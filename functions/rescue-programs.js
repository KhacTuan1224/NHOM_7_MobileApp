const express = require('express');
const crypto = require('node:crypto');
const P = require('./rescue-policy');
const {enqueueNotification, digest} = require('./notification-outbox');
const {fail, text, id, time, round, number, DAY} = P;

/** Stock-backed pickup programs. Money is collected in person at pickup. */
function createRescuePrograms({db, admin, authenticate, now = Date.now}) {
  const router = express.Router();
  const stamp = () => admin.firestore.FieldValue.serverTimestamp();
  const ts = value => admin.firestore.Timestamp.fromMillis(value);
  const ref = (collection, key) => {
    if (!id(key)) throw fail(400, 'Mã dữ liệu không hợp lệ.');
    return db.collection(collection).doc(key);
  };
  const serial = value => value && typeof value.toMillis === 'function' ? value.toMillis()
    : Array.isArray(value) ? value.map(serial) : value && typeof value === 'object'
      ? Object.fromEntries(Object.entries(value).map(([k, v]) => [k, serial(v)])) : value;
  const data = snapshot => snapshot.exists ? {...snapshot.data(), id: snapshot.id} : null;
  function route(path, roles, action) {
    router.post('/rescue/' + path, async (req, res, next) => {
      try {
        const caller = roles ? await authenticate(req, roles) : null;
        res.json({ok: true, ...serial(await action(req.body || {}, caller, req))});
      } catch (error) { next(error); }
    });
  }
  async function account(tx, caller, roles) {
    const user = data(await tx.get(ref('users', caller.uid)));
    if (!user || user.status !== 'ACTIVE' || !roles.includes(user.role)
        || user.role === 'SELLER' && user.sellerStatus !== 'APPROVED') throw fail(403, 'Tài khoản không có quyền thực hiện.');
    return user;
  }
  async function stock(tx, offer) {
    const rows = [];
    for (const component of offer.components) {
      const snapshot = await tx.get(ref('productBatches', component.batchId));
      if (!snapshot.exists) throw fail(409, 'Không tìm thấy lô hàng đã giữ.');
      rows.push({component, snapshot, batch: snapshot.data(), product: data(await tx.get(ref('products', snapshot.get('productId'))))});
    }
    return rows;
  }
  function moveStock(tx, rows, units, operation) {
    if (units <= 0) return;
    for (const {component, snapshot, batch} of rows) {
      const quantity = round(component.quantityPerUnit * units);
      let available = batch.availableQuantity, reserved = batch.reservedQuantity, sold = batch.soldQuantity;
      if (operation === 'RESERVE') { available = round(available - quantity); reserved = round(reserved + quantity); }
      else { reserved = round(reserved - quantity); if (operation === 'COMPLETE') sold = round(sold + quantity); else available = round(available + quantity); }
      if (![available, reserved, sold].every(Number.isFinite) || Math.min(available, reserved, sold) < 0
          || Math.abs(batch.initialQuantity - available - reserved - sold) > 1e-6) throw fail(409, 'Tồn kho thay đổi hoặc chưa nhất quán.');
      const status = batch.status === 'ARCHIVED' ? 'ARCHIVED' : time(batch.expiryDate) <= now() ? 'EXPIRED'
        : reserved > 0 ? 'RESERVED_PARTIAL' : available <= 0 ? 'SOLD_OUT' : 'AVAILABLE';
      tx.update(snapshot.ref, {availableQuantity: available, reservedQuantity: reserved, soldQuantity: sold, status,
        inventoryVersion: (batch.inventoryVersion || 0) + 1, everOrdered: true, updatedAt: stamp()});
    }
  }
  async function assertLive(tx, offer) {
    const seller = data(await tx.get(ref('users', offer.sellerId)));
    if (!seller || seller.role !== 'SELLER' || seller.status !== 'ACTIVE' || seller.sellerStatus !== 'APPROVED')
      throw fail(409, 'Cửa hàng hiện không nhận đăng ký.');
    const rows = await stock(tx, offer);
    for (const row of rows) {
      if (!row.product || row.product.status !== 'ACTIVE' || row.product.sellerId !== offer.sellerId
          || time(row.batch.expiryDate) <= now() || ['ARCHIVED', 'EXPIRED'].includes(row.batch.status))
        throw fail(409, 'Nông sản trong gói hiện không còn khả dụng.');
    }
    return rows;
  }
  function notify(tx, key, uid, offerId, title, message) {
    enqueueNotification(tx, db, admin, 'rescue_' + digest(key), {kind: 'DIRECT', resourceType: 'RESCUE_OFFER',
      resourceId: offerId, recipientUid: uid, title, publicMessage: message});
  }
  const publicKeys = ['id', 'sellerId', 'sellerName', 'type', 'title', 'description', 'categoryLabel', 'contentsDisclosure',
    'unitLabel', 'unitPrice', 'originalUnitPrice', 'closesAt', 'pickupStartAt', 'pickupEndAt', 'pickupAddress', 'latitude', 'longitude',
    'totalUnits', 'availableUnits', 'claimedUnits', 'completedUnits',
    'maxUnitsPerClaim', 'status', 'createdAt'];
  function publicOffer(offer) {
    return Object.fromEntries(publicKeys.filter(key => offer[key] !== undefined).map(key => [key, offer[key]]));
  }
  async function page(collection, body, field, value) {
    let query = db.collection(collection);
    if (field) query = query.where(field, '==', value);
    query = query.orderBy(admin.firestore.FieldPath.documentId()).limit(30);
    if (body.cursor) { if (!id(body.cursor)) throw fail(400, 'Trang không hợp lệ.'); query = query.startAfter(body.cursor); }
    const snapshot = await query.get();
    return {docs: snapshot.docs, cursor: snapshot.docs.length ? snapshot.docs.at(-1).id : '', hasMore: snapshot.size === 30};
  }

  route('create', ['SELLER'], async (body, caller) => {
    const offerRef = ref('rescueOffers', body.requestId);
    return db.runTransaction(async tx => {
      const seller = await account(tx, caller, ['SELLER']);
      const existing = data(await tx.get(offerRef));
      const input = P.validateOffer(body, existing ? existing.validationAt || time(existing.createdAt) : now());
      const fingerprint = digest(JSON.stringify(input));
      if (existing) {
        // Compare the supported fields so removal of a retired method's metadata
        // does not break replay of an already-created, unchanged offer.
        const previousInput = P.validateOffer({...existing, requestId: existing.id}, existing.validationAt || time(existing.createdAt));
        if (existing.sellerId !== caller.uid || digest(JSON.stringify(previousInput)) !== fingerprint)
          throw fail(409, 'Mã yêu cầu đã được dùng cho gói khác.');
        return {offer: existing, replay: true};
      }
      const offer = {...input, sellerId: caller.uid, components: input.components};
      const rows = await stock(tx, offer);
      for (const row of rows) P.assertBatch(row.batch, row.product, caller.uid, now(), input.pickupEndAt);
      const originalValue = Math.round(rows.reduce((sum, row) => sum + row.product.originalPrice * row.component.quantityPerUnit, 0));
      if (!Number.isFinite(originalValue) || input.originalUnitPrice > originalValue)
        throw fail(400, 'Giá trị gốc mỗi suất không được vượt tổng giá gốc của các thành phần.');
      const saved = {...offer, components: rows.map(row => ({...row.component, productNameSnapshot: text(row.product.name),
        unitSnapshot: text(row.product.unit), expiryDate: time(row.batch.expiryDate)})),
        id: offerRef.id, fingerprint, sellerName: text(seller.shopName) || text(seller.fullName),
        availableUnits: input.totalUnits, claimedUnits: 0, completedUnits: 0, releasedUnits: 0, claimCount: 0,
        status: 'OPEN', validationAt: now(), createdAt: stamp(), updatedAt: stamp(), nextActionAt: ts(input.closesAt)};
      moveStock(tx, rows, input.totalUnits, 'RESERVE');
      tx.create(offerRef, saved);
      return {offer: saved};
    });
  });

  route('catalog', null, async body => {
    if (body.type && !P.TYPES.includes(body.type)) throw fail(400, 'Phương thức không hợp lệ.');
    let point = null;
    if (body.nearby) { point = P.coordinates(body.latitude, body.longitude); number(body.radiusKm ?? 5, 0.1, 20, 'Bán kính'); }
    const result = await page('rescueOffers', body, 'status', 'OPEN'), items = [];
    const sellers = new Map();
    for (const doc of result.docs) {
      const offer = data(doc);
      if (!P.TYPES.includes(offer.type) || offer.closesAt <= now() || offer.availableUnits <= 0 || (offer.claimCount || 0) >= 100 || body.type && offer.type !== body.type) continue;
      if (!sellers.has(offer.sellerId)) sellers.set(offer.sellerId, data(await ref('users', offer.sellerId).get()));
      const seller = sellers.get(offer.sellerId);
      if (!seller || seller.role !== 'SELLER' || seller.status !== 'ACTIVE' || seller.sellerStatus !== 'APPROVED') continue;
      const distanceKm = point ? P.distanceKm(point, offer) : null;
      if (point && distanceKm > (body.radiusKm ?? 5)) continue;
      items.push({...publicOffer(offer), ...(point ? {distanceKm} : {})});
    }
    if (point) items.sort((a, b) => a.distanceKm - b.distanceKm);
    return {items, cursor: result.cursor, hasMore: result.hasMore};
  });
  route('mine', ['SELLER', 'CUSTOMER', 'ADMIN'], async (body, caller) => {
    if (caller.role === 'SELLER') {
      const result = await page('rescueOffers', body, 'sellerId', caller.uid);
      return {items: result.docs.map(doc => data(doc)).filter(offer => P.TYPES.includes(offer.type)), cursor: result.cursor, hasMore: result.hasMore};
    }
    const result = await page('rescueClaims', body, 'customerId', caller.uid), items = [];
    for (const doc of result.docs) {
      const claim = data(doc), offer = data(await ref('rescueOffers', claim.offerId).get());
      if (offer && P.TYPES.includes(offer.type)) items.push({...claim, status: P.claimState(offer, claim), offer: publicOffer(offer)});
    }
    return {items, cursor: result.cursor, hasMore: result.hasMore};
  });
  route('detail', null, async (body, unused, req) => {
    const offer = data(await ref('rescueOffers', body.offerId).get());
    if (!offer || !P.TYPES.includes(offer.type)) throw fail(404, 'Không tìm thấy gói giải cứu.');
    const caller = req.get('authorization') ? await authenticate(req, ['CUSTOMER', 'SELLER', 'ADMIN']) : null;
    let claims = [];
    if (caller && (caller.role === 'CUSTOMER' || caller.uid === offer.sellerId)) {
      let claimQuery = db.collection('rescueClaims').where('offerId', '==', offer.id);
      if (caller.role === 'CUSTOMER') {
        claimQuery = claimQuery.where('customerId', '==', caller.uid);
      }
      claims = (await claimQuery.limit(100).get()).docs.map(doc => ({...data(doc), status: P.claimState(offer, data(doc))}));
    }
    const impact = data(await ref('rescueImpact', offer.sellerId).get());
    const receivedContents = [];
    if (caller?.role === 'CUSTOMER' && claims.some(c => c.status === 'COMPLETED')) {
      for (const component of offer.components) {
        const batch = await ref('productBatches', component.batchId).get();
        if (batch.exists) {
          const product = await ref('products', batch.get('productId')).get();
          if (product.exists) receivedContents.push({productName: component.productNameSnapshot || product.get('name'),
            unit: component.unitSnapshot || product.get('unit'), quantityPerUnit: component.quantityPerUnit});
        }
      }
    }
    return {offer: caller?.uid === offer.sellerId ? offer : publicOffer(offer), claims, impact, receivedContents};
  });
  route('batches', ['SELLER'], async (body, caller) => {
    // New picker: page both products and their batches, and explain exclusions.
    // Keep the original response contract for previously installed clients.
    if (body.includeUnavailable === true) {
      let cursor = null;
      if (body.cursor) {
        try {
          if (typeof body.cursor !== 'string' || body.cursor.length > 600) throw Error();
          cursor = JSON.parse(Buffer.from(body.cursor, 'base64url').toString('utf8'));
          if (!id(cursor.productId) || cursor.batchId !== '' && !id(cursor.batchId)) throw Error();
        } catch { throw fail(400, 'Trang lô hàng không hợp lệ.'); }
      }
      let products = db.collection('products').where('sellerId', '==', caller.uid)
        .orderBy(admin.firestore.FieldPath.documentId()).limit(30);
      if (cursor) products = cursor.batchId ? products.startAt(cursor.productId) : products.startAfter(cursor.productId);
      const result = await products.get(), items = [];
      const encode = (productId, batchId = '') => Buffer.from(JSON.stringify({productId, batchId})).toString('base64url');
      for (const product of result.docs) {
        let query = db.collection('productBatches').where('productId', '==', product.id)
          .orderBy(admin.firestore.FieldPath.documentId()).limit(100 - items.length);
        if (cursor?.productId === product.id && cursor.batchId) query = query.startAfter(cursor.batchId);
        const batches = await query.get();
        for (const batch of batches.docs) {
          let unavailableReason = '';
          if (product.get('status') !== 'ACTIVE') unavailableReason = 'Sản phẩm không ở trạng thái Đang bán.';
          else if (text(batch.get('activeCampaignId'))) unavailableReason = 'Lô đang gắn với chiến dịch, không thể dùng đồng thời cho gói giải cứu.';
          else if (time(batch.get('expiryDate')) <= now()) unavailableReason = 'Lô đã hết hạn sử dụng.';
          else if (time(batch.get('harvestDate')) > now()) unavailableReason = 'Chưa đến ngày thu hoạch.';
          else if (!['AVAILABLE', 'RESERVED_PARTIAL'].includes(batch.get('status'))) unavailableReason = 'Trạng thái lô không cho phép bán.';
          else if (!(batch.get('availableQuantity') > 0)) unavailableReason = 'Lô không còn số lượng khả dụng.';
          items.push({batchId: batch.id, productName: product.get('name'), unit: product.get('unit'),
            availableQuantity: batch.get('availableQuantity'), harvestDate: batch.get('harvestDate'),
            expiryDate: batch.get('expiryDate'), selectable: !unavailableReason, unavailableReason});
        }
        if (items.length === 100) return {items, cursor: encode(product.id, batches.docs.at(-1).id), hasMore: true};
      }
      return {items, cursor: result.empty ? '' : encode(result.docs.at(-1).id), hasMore: result.size === 30};
    }
    const result = await page('products', body, 'sellerId', caller.uid), items = [];
    for (const productDoc of result.docs) {
      if (productDoc.get('status') !== 'ACTIVE') continue;
      const batches = await db.collection('productBatches').where('productId', '==', productDoc.id).limit(100).get();
      for (const batch of batches.docs) if (!text(batch.get('activeCampaignId')) && batch.get('availableQuantity') > 0
          && ['AVAILABLE', 'RESERVED_PARTIAL'].includes(batch.get('status')) && time(batch.get('expiryDate')) > now()) {
        items.push({batchId: batch.id, productName: productDoc.get('name'), unit: productDoc.get('unit'),
          availableQuantity: batch.get('availableQuantity'), expiryDate: batch.get('expiryDate')});
      }
    }
    return {items, cursor: result.cursor, hasMore: result.hasMore};
  });

  async function join(body, caller) {
    const offerRef = ref('rescueOffers', body.offerId), claimRef = ref('rescueClaims', digest(body.offerId + '|' + caller.uid));
    return db.runTransaction(async tx => {
      const buyer = await account(tx, caller, ['CUSTOMER']);
      const offer = data(await tx.get(offerRef));
      const previous = data(await tx.get(claimRef));
      if (!offer || !P.TYPES.includes(offer.type)) throw fail(404, 'Không tìm thấy gói giải cứu.');
      if (previous) {
        if (previous.units !== body.units)
          throw fail(409, 'Bạn đã đăng ký gói này với lựa chọn khác.');
        return {claim: {...previous, status: P.claimState(offer, previous)}, replay: true};
      }
      if (offer.status !== 'OPEN' || offer.closesAt <= now()) throw fail(409, 'Gói đã hết hạn đăng ký.');
      const units = number(body.units, 1, offer.maxUnitsPerClaim, 'Số suất', true);
      if (offer.availableUnits < units) throw fail(409, 'Không còn đủ suất.');
      if (offer.sellerId === caller.uid) throw fail(403, 'Không thể tự đăng ký.');
      await assertLive(tx, offer);
      const claimedUnits = offer.claimedUnits + units;
      if ((offer.claimCount || 0) >= 100) throw fail(409, 'Gói đã đạt giới hạn 100 đăng ký.');
      const claim = {id: claimRef.id, offerId: offer.id, customerId: caller.uid, sellerId: offer.sellerId,
        customerName: text(buyer.fullName), units, unitPrice: offer.unitPrice, totalAmount: offer.unitPrice * units,
        status: 'RESERVED', paymentMethod: 'CASH_ON_PICKUP',
        paymentStatus: 'UNPAID', createdAt: stamp(), updatedAt: stamp()};
      tx.create(claimRef, claim);
      tx.update(offerRef, {availableUnits: offer.availableUnits - units, claimedUnits,
        claimCount: (offer.claimCount || 0) + 1, updatedAt: stamp()});
      notify(tx, claimRef.id + ':joined', caller.uid, offer.id, 'Đã đăng ký giải cứu',
        'Cửa hàng sẽ chuẩn bị nông sản để bạn nhận tại điểm.');
      notify(tx, claimRef.id + ':seller', offer.sellerId, offer.id, 'Có đăng ký giải cứu mới', 'Mở khu Giải cứu mở rộng để chuẩn bị hàng.');
      return {claim: {...claim, status: P.claimState(offer, claim)}};
    });
  }
  route('join', ['CUSTOMER'], (body, caller) => join(body, caller));

  route('claims', ['SELLER', 'CUSTOMER'], async (body, caller) => {
    const offer = data(await ref('rescueOffers', body.offerId).get());
    if (!offer || !P.TYPES.includes(offer.type)) throw fail(404, 'Không tìm thấy gói.');
    if (caller.role === 'SELLER' && offer.sellerId !== caller.uid) throw fail(403, 'Gói không thuộc cửa hàng.');
    let query = db.collection('rescueClaims').where('offerId', '==', offer.id);
    if (caller.role === 'CUSTOMER') query = query.where('customerId', '==', caller.uid);
    const claims = await query.limit(100).get();
    return {offer: publicOffer(offer), items: claims.docs.map(doc => ({...data(doc), status: P.claimState(offer, data(doc))}))};
  });

  route('claims/action', ['CUSTOMER', 'SELLER'], async (body, caller) => db.runTransaction(async tx => {
    await account(tx, caller, ['CUSTOMER', 'SELLER']);
    const claimRef = ref('rescueClaims', body.claimId), claim = data(await tx.get(claimRef));
    if (!claim || !(caller.role === 'CUSTOMER' && claim.customerId === caller.uid || caller.role === 'SELLER' && claim.sellerId === caller.uid))
      throw fail(403, 'Không có quyền xử lý đăng ký.');
    const offerRef = ref('rescueOffers', claim.offerId), offer = data(await tx.get(offerRef));
    if (!offer || !P.TYPES.includes(offer.type)) throw fail(404, 'Gói không còn tồn tại.');
    const effective = P.claimState(offer, claim);
    if (body.action === 'COMPLETE' && claim.status === 'COMPLETED' || body.action === 'CANCEL' && effective === 'CANCELLED') return {replay: true};
    if (['COMPLETED', 'CANCELLED'].includes(effective)) throw fail(409, 'Đăng ký đã kết thúc.');
    if (body.action === 'READY') {
      if (caller.role !== 'SELLER' || now() < offer.pickupStartAt || now() >= offer.pickupEndAt)
        throw fail(409, 'Chỉ cửa hàng chuẩn bị khi đã đủ điều kiện và trong giờ nhận.');
      await assertLive(tx, offer);
      tx.update(claimRef, {status: 'READY', updatedAt: stamp()});
      notify(tx, claim.id + ':ready', claim.customerId, offer.id, 'Nông sản đã sẵn sàng', 'Đến điểm nhận trong khung giờ của gói. Chỉ xác nhận đã nhận sau khi nhận hàng thực tế.');
    } else if (body.action === 'COMPLETE') {
      if (caller.role !== 'CUSTOMER' || claim.status !== 'READY'
          || now() < offer.pickupStartAt || now() >= offer.pickupEndAt) throw fail(409, 'Khách chỉ xác nhận khi cửa hàng đã chuẩn bị và còn trong giờ nhận.');
      if (body.confirmReceived !== true || claim.totalAmount > 0 && body.confirmPaid !== true)
        throw fail(400, 'Cần xác nhận đã nhận hàng và thanh toán tại điểm.');
      const rows = await assertLive(tx, offer);
      const statsRef = ref('rescueImpact', offer.sellerId), stats = data(await tx.get(statsRef)) || {};
      moveStock(tx, rows, claim.units, 'COMPLETE');
      tx.update(claimRef, {status: 'COMPLETED', paymentStatus: 'BUYER_CONFIRMED_CASH',
        receivedAt: stamp(), updatedAt: stamp()});
      tx.update(offerRef, {claimedUnits: offer.claimedUnits - claim.units, completedUnits: offer.completedUnits + claim.units, updatedAt: stamp()});
      const quantities = {...(stats.quantityByUnit || {})};
      for (const row of rows) {
        const unit = text(row.component.unitSnapshot || row.product.unit), quantity = round(row.component.quantityPerUnit * claim.units);
        quantities[unit] = round((quantities[unit] || 0) + quantity);
      }
      tx.set(statsRef, {sellerId: offer.sellerId, completedPickups: (stats.completedPickups || 0) + 1,
        quantityByUnit: quantities, updatedAt: stamp()});
      notify(tx, claim.id + ':completed', offer.sellerId, offer.id, 'Khách đã xác nhận nhận nông sản', 'Lượt giải cứu đã được ghi nhận vào thống kê Giải cứu mở rộng.');
    } else if (body.action === 'CANCEL') {
      if (caller.role !== 'CUSTOMER')
        throw fail(409, 'Chỉ khách sở hữu có thể hủy đăng ký.');
      const release = offer.status !== 'OPEN' || now() >= offer.closesAt;
      const rows = release ? await stock(tx, offer) : [];
      if (release) moveStock(tx, rows, claim.units, 'RELEASE');
      tx.update(claimRef, {status: 'CANCELLED', updatedAt: stamp()});
      tx.update(offerRef, {claimedUnits: offer.claimedUnits - claim.units,
        availableUnits: offer.availableUnits + (release ? 0 : claim.units),
        releasedUnits: offer.releasedUnits + (release ? claim.units : 0), updatedAt: stamp()});
    } else throw fail(400, 'Thao tác không hợp lệ.');
    return {};
  }));

  async function settle(offerId, caller = null) {
    return db.runTransaction(async tx => {
      if (caller) await account(tx, caller, ['SELLER', 'ADMIN']);
      const offerRef = ref('rescueOffers', offerId), offer = data(await tx.get(offerRef));
      if (!offer) throw fail(404, 'Không tìm thấy gói.');
      if (caller && caller.role !== 'ADMIN' && offer.sellerId !== caller.uid) throw fail(403, 'Không có quyền dừng gói.');
      if (!['OPEN', 'LOCKED'].includes(offer.status)) return {replay: true};
      if (!caller && now() < offer.closesAt) return {};
      const terminal = !!caller || !P.TYPES.includes(offer.type) || now() >= offer.pickupEndAt;
      const units = terminal ? offer.totalUnits - offer.completedUnits - offer.releasedUnits : offer.availableUnits;
      const rows = await stock(tx, offer);
      const claims = terminal ? await tx.get(db.collection('rescueClaims').where('offerId', '==', offer.id).limit(100)) : null;
      moveStock(tx, rows, units, 'RELEASE');
      const status = caller || !P.TYPES.includes(offer.type) ? 'CANCELLED' : terminal ? 'EXPIRED' : 'LOCKED';
      tx.update(offerRef, {status, availableUnits: 0, claimedUnits: terminal ? 0 : offer.claimedUnits,
        releasedUnits: offer.releasedUnits + units, nextActionAt: ts(terminal ? now() : offer.pickupEndAt), updatedAt: stamp()});
      if (claims) for (const doc of claims.docs) if (!['COMPLETED', 'CANCELLED'].includes(doc.get('status'))) {
        tx.update(doc.ref, {status: 'CANCELLED', cancellationReason: status, updatedAt: stamp()});
        notify(tx, doc.id + ':closed', doc.get('customerId'), offer.id,
          'Đăng ký giải cứu đã kết thúc',
          'Đã trả tồn kho chưa nhận. Không có khoản thanh toán trước để hoàn tiền.');
      }
      return {status};
    });
  }
  route('close', ['SELLER', 'ADMIN'], (body, caller) => settle(body.offerId, caller));

  route('nearby/preferences', ['CUSTOMER'], async (body, caller) => {
    const preferenceRef = ref('rescueNearbyPreferences', caller.uid);
    if (body.enabled === false) { await preferenceRef.delete(); return {}; }
    const point = P.coordinates(body.latitude, body.longitude), radiusKm = number(body.radiusKm ?? 5, 0.1, 5, 'Bán kính');
    await preferenceRef.set({customerId: caller.uid, enabled: true, ...point, radiusKm,
      expiresAt: ts(now() + DAY), updatedAt: stamp(), nextRunAt: ts(now())});
    return {expiresAt: now() + DAY};
  });
  route('impact', null, async body => {
    const stats = data(await ref('rescueImpact', body.sellerId).get());
    return {impact: stats || {completedPickups: 0, quantityByUnit: {}}};
  });

  let running = false;
  async function reconcile() {
    if (running) return {busy: true}; running = true;
    try {
      const due = await db.collection('rescueOffers').where('status', 'in', ['OPEN', 'LOCKED'])
        .where('nextActionAt', '<=', ts(now())).orderBy('nextActionAt').limit(40).get();
      for (const doc of due.docs) await settle(doc.id);
      const preferences = await db.collection('rescueNearbyPreferences').where('nextRunAt', '<=', ts(now())).orderBy('nextRunAt').limit(40).get();
      const flash = preferences.empty ? [] : (await db.collection('rescueOffers').where('status', '==', 'OPEN')
        .where('type', '==', 'FLASH').orderBy('closesAt').limit(100).get()).docs.map(data).filter(o => o.closesAt > now() && o.availableUnits > 0);
      for (const doc of preferences.docs) {
        const preference = data(doc);
        if (time(preference.expiresAt) <= now()) {
          await db.runTransaction(async tx => {
            const current = await tx.get(doc.ref);
            if (current.exists && time(current.get('expiresAt')) <= now()) tx.delete(doc.ref);
          });
          continue;
        }
        for (const offer of flash) if (P.distanceKm(preference, offer) <= preference.radiusKm) {
          const key = 'rescue_' + digest('nearby|' + doc.id + '|' + offer.id);
          await db.runTransaction(async tx => {
            const [event, current, seller, liveOffer] = await Promise.all([tx.get(ref('notificationOutbox', key)), tx.get(doc.ref),
              tx.get(ref('users', offer.sellerId)), tx.get(ref('rescueOffers', offer.id))]);
            if (event.exists || !current.exists || !current.get('enabled') || time(current.get('expiresAt')) <= now() || !liveOffer.exists
                || liveOffer.get('status') !== 'OPEN' || liveOffer.get('closesAt') <= now() || liveOffer.get('availableUnits') <= 0
                || seller.get('status') !== 'ACTIVE' || seller.get('sellerStatus') !== 'APPROVED'
                || P.distanceKm(current.data(), liveOffer.data()) > current.get('radiusKm')) return;
            enqueueNotification(tx, db, admin, key, {kind: 'RESCUE_NEARBY', resourceType: 'RESCUE_OFFER', resourceId: offer.id,
              recipientUid: doc.id, title: 'Giải cứu giờ chót gần bạn', publicMessage: `${offer.title} · còn suất nhận tại điểm, mở để xem giờ và giá.`});
          });
        }
        await db.runTransaction(async tx => {
          const current = await tx.get(doc.ref);
          if (current.exists && time(current.get('updatedAt')) === time(preference.updatedAt))
            tx.update(doc.ref, {nextRunAt: ts(now() + 5 * 60000)});
        });
      }
      return {settled: due.size, nearby: preferences.size};
    } finally { running = false; }
  }
  return {router, reconcile, settle, join};
}
module.exports = {createRescuePrograms};
