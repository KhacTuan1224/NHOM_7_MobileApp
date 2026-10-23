const {test} = require('node:test');
const assert = require('node:assert/strict');
const {campaignInventory} = require('./campaign-inventory');
test('dashboard reads each referenced batch/product once and never scans unrelated inventory', async () => {
  const reads = [], values = {'productBatches/a':{productId:'p'}, 'productBatches/b':{productId:'p'},
    'productBatches/hidden':{productId:'h'}, 'products/p':{status:'ACTIVE'}, 'products/h':{status:'HIDDEN'}};
  const db = {collection: c => ({doc: id => ({get: async () => {
    const path = c+'/'+id; reads.push(path); const value = values[path];
    return {id, exists:!!value, get:key=>value?.[key], data:()=>value};
  }})})};
  const rows = await campaignInventory(db, [{get:()=>({a:1,b:1,missing:1,'bad/path':1})}, {get:()=>({a:1,hidden:1})}]);
  assert.deepEqual(rows.map(row=>row.id), ['a','b']);
  assert.deepEqual(reads.sort(), ['productBatches/a','productBatches/b','productBatches/hidden','productBatches/missing','products/h','products/p']);
});
test('empty campaign list performs no inventory reads', async () => {
  assert.deepEqual(await campaignInventory({collection:()=>{throw Error('unexpected scan');}}, []), []);
});
