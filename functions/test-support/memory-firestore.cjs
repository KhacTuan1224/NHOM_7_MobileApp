// Deterministic transaction harness. The separate emulator suite verifies real Firestore/Rules semantics.
const crypto = require('node:crypto');
class Timestamp {
  constructor(value, extraNanos = 0) { this.value = value; this.extraNanos = extraNanos; this.seconds = Math.floor(value / 1000); this.nanoseconds = (value % 1000) * 1000000 + extraNanos; }
  toMillis() { return this.value; }
}
function copy(value) {
  if (value instanceof Timestamp) return new Timestamp(value.value, value.extraNanos);
  if (Array.isArray(value)) return value.map(copy);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, copy(v)]));
  return value;
}
class Reference {
  constructor(db, path) { this.db = db; this.path = path; this.id = path.split('/').pop(); }
  collection(name) { return new Query(this.db, `${this.path}/${name}`); }
  async get() { return this.db.snapshot(this.path); }
  async delete() { this.db.documents.delete(this.path); }
}
class Query {
  constructor(db, path, filters = [], limit = Infinity, cursor = null, order = []) {
    Object.assign(this, {db, path, filters, maximum: limit, cursor, order});
  }
  doc(id = crypto.randomUUID()) { return new Reference(this.db, `${this.path}/${id}`); }
  where(field, operation, value) { return new Query(this.db, this.path, [...this.filters, [field, operation, value]], this.maximum, this.cursor, this.order); }
  orderBy(field, direction = 'asc') { return new Query(this.db, this.path, this.filters, this.maximum, this.cursor, [...this.order, [field, direction]]); }
  startAfter(...values) { return new Query(this.db, this.path, this.filters, this.maximum, values, this.order); }
  limit(maximum) { return new Query(this.db, this.path, this.filters, maximum, this.cursor, this.order); }
  async get() {
    const depth = this.path.split('/').length + 1;
    let docs = [...this.db.documents.keys()].filter(path => path.startsWith(this.path + '/') && path.split('/').length === depth)
      .map(path => this.db.snapshot(path));
    docs = docs.filter(doc => this.filters.every(([field, op, value]) => {
      const v = doc.get(field), a = v instanceof Timestamp ? v.value : v, b = value instanceof Timestamp ? value.value : value;
      if (op === '==') return a === b;
      if (op === '<=') return a <= b;
      if (op === '>=') return a >= b;
      if (op === 'in') return b.includes(a);
      throw Error('Unsupported test query: ' + op);
    }));
    const orders = this.order.length ? this.order : [['__name__', 'asc']];
    const value = (doc, key) => key === '__name__' ? doc.id : doc.get(key);
    const compare = (left, right) => {
      if (left instanceof Timestamp && right instanceof Timestamp) return left.seconds - right.seconds || left.nanoseconds - right.nanoseconds;
      const a = left instanceof Timestamp ? left.value : left, b = right instanceof Timestamp ? right.value : right;
      return a < b ? -1 : a > b ? 1 : 0;
    };
    const compareDoc = (a, b) => {
      for (const [field, direction] of orders) { const result = compare(value(a, field), value(b, field)); if (result) return result * (direction === 'desc' ? -1 : 1); }
      return a.id.localeCompare(b.id);
    };
    docs.sort(compareDoc);
    if (this.cursor) {
      const first = this.cursor[0];
      if (first && first.ref) docs = docs.filter(doc => compareDoc(doc, first) > 0);
      else docs = docs.filter(doc => {
        for (let i = 0; i < orders.length; i++) { const result = compare(value(doc, orders[i][0]), this.cursor[i]); if (result) return result * (orders[i][1] === 'desc' ? -1 : 1) > 0; }
        return false;
      });
    }
    docs = docs.slice(0, this.maximum);
    return {docs, empty: !docs.length, size: docs.length};
  }
}
class MemoryFirestore {
  constructor(seed = {}) { this.documents = new Map(Object.entries(seed).map(([k, v]) => [k, copy(v)])); this.tail = Promise.resolve(); }
  collection(path) { return new Query(this, path); }
  snapshot(path) {
    const value = copy(this.documents.get(path));
    return {id: path.split('/').pop(), ref: new Reference(this, path), exists: value !== undefined,
      data: () => copy(value), get: field => value && copy(value[field])};
  }
  async runTransaction(work) {
    const result = this.tail.then(async () => {
      const writes = [];
      const tx = {
        get: async reference => { if (writes.length) throw Error('Read after transaction write'); return reference.get(); },
        update: (ref, data) => writes.push(['update', ref.path, copy(data)]),
        set: (ref, data) => writes.push(['set', ref.path, copy(data)]),
        create: (ref, data) => writes.push(['create', ref.path, copy(data)])
      };
      const value = await work(tx), next = new Map(this.documents);
      for (const [kind, path, data] of writes) {
        if (kind === 'create' && next.has(path)) throw Error('Document already exists');
        if (kind === 'update' && !next.has(path)) throw Error('Document does not exist');
        next.set(path, kind === 'update' ? {...next.get(path), ...data} : data);
      }
      this.documents = next;
      return value;
    });
    this.tail = result.catch(() => {});
    return result;
  }
}
const admin = {firestore: {FieldValue: {serverTimestamp: () => new Timestamp(Date.now())},
  Timestamp: {fromMillis: value => new Timestamp(value)}, FieldPath: {documentId: () => '__name__'}}};
module.exports = {MemoryFirestore, Timestamp, admin};
