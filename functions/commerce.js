const express = require("express");
const {normalizeUnit, campaignUnit} = require('./quantity-units');
const {enqueueNotification} = require('./notification-outbox');
const crypto = require("node:crypto");
const {createCampaignLifecycle, readAllocations} = require('./campaign-lifecycle');

const text = value => typeof value === "string" ? value.trim() : "";
const hash = value => crypto.createHash("sha256").update(value).digest("hex");
const number = (value, fallback = NaN) => typeof value === "number" && Number.isFinite(value) ? value : fallback;
const time = value => value && typeof value.toMillis === "function" ? value.toMillis() : 0;
const money = value => Math.round(value);
const error = (status, message) => Object.assign(new Error(message), {status});
const identifier = value => /^[A-Za-z0-9_-]{1,128}$/.test(text(value));
const phone = value => text(value).replace(/[\s().-]/g, "").replace(/^\+84/, "0");
const coordinates = (lat, lng) => Number.isFinite(lat) && lat >= -90 && lat <= 90
  && Number.isFinite(lng) && lng >= -180 && lng <= 180 && !(lat === 0 && lng === 0);

function price(product, promotion, quantity, now) {
  const original = number(product.originalPrice), rescue = number(product.rescuePrice);
  if (!(original > 0 && rescue > 0 && rescue <= original)) throw error(409, "Giá sản phẩm chưa hợp lệ.");
  let discount = 0, tier = 0;
  if (promotion && promotion.active === true && time(promotion.startDate) <= now && time(promotion.endDate) > now) {
    if (promotion.type === "PERCENT") discount = rescue * number(promotion.value, 0) / 100;
    if (promotion.type === "FIXED") discount = number(promotion.value, 0);
    for (const [threshold, percent] of Object.entries(promotion.quantityDiscountTiers || {})) {
      if (quantity >= Number(threshold)) tier = Math.max(tier, number(percent, 0));
    }
  }
  const unit = money(Math.max(0, rescue - Math.min(rescue, discount)) * (1 - Math.min(100, tier) / 100));
  return {unit, subtotal: money(unit * quantity), originalSubtotal: money(original * quantity)};
}

function validateSelection(data) {
  if (!Array.isArray(data.items) || !data.items.length || data.items.length > 20) {
    throw error(400, "Chọn từ 1 đến 20 lô hàng để đặt đơn.");
  }
  if (!['DELIVERY', 'PICKUP', 'ON_SITE_RESCUE'].includes(data.fulfillmentType)
      || !['COD', 'BANK_TRANSFER', 'CASH_ON_SITE'].includes(data.paymentMethod)) {
    throw error(400, "Cách nhận hàng hoặc thanh toán không hợp lệ.");
  }
  if (data.paymentMethod !== 'BANK_TRANSFER' && (data.paymentMethod === 'COD') !== (data.fulfillmentType === 'DELIVERY')) {
    throw error(400, "COD chỉ dùng cho giao hàng; tiền mặt dùng khi đến nhận.");
  }
  const batches = new Set(), sellers = new Set();
  const items = data.items.map(item => {
    if (!item || !identifier(item.productId) || !identifier(item.batchId) || !identifier(item.sellerId)
        || !(number(item.quantity) > 0) || item.quantity > 1000000 || batches.has(item.batchId)) {
      throw error(400, "Sản phẩm, lô hoặc số lượng không hợp lệ/trùng lặp.");
    }
    batches.add(item.batchId); sellers.add(item.sellerId);
    return {productId: item.productId, batchId: item.batchId, sellerId: item.sellerId, quantity: item.quantity};
  });
  if (sellers.size !== 1) throw error(400, "Hãy đặt riêng từng cửa hàng.");
  return items;
}

function distanceKm(aLat, aLng, bLat, bLng) {
  const r = Math.PI / 180, dLat = (bLat - aLat) * r, dLng = (bLng - aLng) * r;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * r) * Math.cos(bLat * r) * Math.sin(dLng / 2) ** 2;
  return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
}

function createCommerce({db, admin, authenticate, campaignLifecycle = null}) {
  const router = express.Router();
  const lifecycle = campaignLifecycle || createCampaignLifecycle({db, admin, authenticate});
  const stamp = () => admin.firestore.FieldValue.serverTimestamp();
  const timestamp = millis => admin.firestore.Timestamp.fromMillis(millis);

  // Persistent limits also apply after a Render restart; IPs/phones are stored only as hashes.
  async function limit(req, purpose, maximum, windowMs = 600000, subject = '') {
    const bucket = Math.floor(Date.now() / windowMs);
    const ref = db.collection('requestLimits').doc(hash(`${purpose}|${req.ip}|${subject}|${bucket}`));
    await db.runTransaction(async tx => {
      const doc = await tx.get(ref), count = doc.exists ? number(doc.get('count'), 0) : 0;
      if (count >= maximum) throw error(429, "Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
      tx.set(ref, {count: count + 1, expiresAt: timestamp((bucket + 2) * windowMs)});
    });
  }

  async function quote(read, data, now = Date.now()) {
    const items = validateSelection(data), sellerId = items[0].sellerId;
    const seller = await read(db.collection('users').doc(sellerId));
    if (!seller.exists || seller.get('role') !== 'SELLER' || seller.get('status') !== 'ACTIVE'
        || seller.get('sellerStatus') !== 'APPROVED') throw error(409, "Cửa hàng chưa được duyệt hoặc đã ngừng bán.");
    const rows = [];
    for (const item of items) {
      const p = await read(db.collection('products').doc(item.productId));
      const b = await read(db.collection('productBatches').doc(item.batchId));
      const promo = await read(db.collection('promotions').doc(item.productId));
      if (!p.exists || p.get('sellerId') !== sellerId || p.get('status') !== 'ACTIVE') throw error(409, "Sản phẩm không còn bán.");
      if (!b.exists || b.get('productId') !== p.id || !['AVAILABLE', 'RESERVED_PARTIAL'].includes(b.get('status'))
          || time(b.get('expiryDate')) <= now || number(b.get('availableQuantity'), 0) < item.quantity) {
        throw error(409, "Lô hàng đã hết hạn hoặc không đủ tồn kho.");
      }
      const batch = b.data();
      if (!Number.isFinite(batch.initialQuantity) || !Number.isFinite(batch.reservedQuantity)
          || !Number.isFinite(batch.soldQuantity) || Math.abs(batch.initialQuantity - batch.availableQuantity
            - batch.reservedQuantity - batch.soldQuantity) > 0.000001) throw error(409, "Tồn kho lô hàng chưa nhất quán.");
      rows.push({item, product: p.data(), batch, batchRef: b.ref, price: price(p.data(), promo.exists ? promo.data() : null, item.quantity, now)});
    }
    const campaignIds = [...new Set(rows.map(row => text(row.batch.activeCampaignId)).filter(Boolean))];
    if (campaignIds.length > 1 || (data.fulfillmentType === 'ON_SITE_RESCUE' && !campaignIds.length)) {
      throw error(409, "Nhận tại điểm giải cứu cần các lô thuộc cùng một chiến dịch đang hoạt động.");
    }
    let campaign = null, campaignRef = null, campaignQuantity = 0, allocation = null;
    if (campaignIds.length) {
      campaignRef = db.collection('campaigns').doc(campaignIds[0]);
      const c = await read(campaignRef);
      if (!c.exists || c.get('sellerId') !== sellerId || c.get('status') !== 'ACTIVE'
          || time(c.get('endDate')) <= now || time(c.get('startDate')) > now) throw error(409, "Chiến dịch chưa bắt đầu hoặc đã kết thúc.");
      campaign = {id: c.id, ...c.data()};
      const unit = campaignUnit(rows.filter(row => text(row.batch.activeCampaignId)).map(row => row.product));
      if (campaign.unitStatus !== 'VERIFIED' || normalizeUnit(campaign.quantityUnit) !== unit)
        throw error(409, 'Đơn vị Campaign chưa được xác minh hoặc không khớp; cần đối soát trước khi đặt hàng.');
      allocation = await readAllocations(read, db, c.id, campaign);
      for (const row of rows) if (text(row.batch.activeCampaignId)) {
        const remaining = number((campaign.batchTargets || {})[row.item.batchId], 0)
          - (allocation.reserved[row.item.batchId] || 0) - (allocation.rescued[row.item.batchId] || 0);
        if (remaining + 0.000001 < row.item.quantity) throw error(409, "Số lượng vượt phần phân bổ còn lại của lô trong chiến dịch.");
        campaignQuantity += row.item.quantity;
      }
      if (number(campaign.targetQuantity, 0) - number(campaign.reservedQuantity, 0)
          - number(campaign.rescuedQuantity, 0) + 0.000001 < campaignQuantity) throw error(409, "Chiến dịch không còn đủ sản lượng.");
      if (data.fulfillmentType === 'ON_SITE_RESCUE' && campaign.rescueMode === 'MOBILE_POINT'
          && (campaign.locationSharingEnabled !== true || !coordinates(campaign.currentLatitude, campaign.currentLongitude)
            || now - time(campaign.locationUpdatedAt) > 600000 || time(campaign.locationUpdatedAt) > now)) {
        throw error(409, "Vị trí điểm giải cứu di động cần được cập nhật lại.");
      }
    }
    let profile = null, paymentProfile = null, shippingFee = 0, distance = 0;
    // Store pickup/shipping always comes from the public business profile,
    // never from the seller's personal GPS or caller-supplied origin fields.
    const store = await read(db.collection('sellerPublicProfiles').doc(sellerId));
    if (store.exists) profile = store.data();
    if (data.fulfillmentType === 'DELIVERY' && !profile) {
      throw error(409, "Người bán chưa lưu địa chỉ và GPS cửa hàng trong Hồ sơ người bán.");
    }
    if (data.paymentMethod === 'BANK_TRANSFER') {
      const p = await read(db.collection('sellerPaymentProfiles').doc(sellerId));
      if (!p.exists) throw error(409, "Cửa hàng chưa khai báo thông tin nhận thanh toán.");
      paymentProfile = p.data();
    }
    if (data.fulfillmentType === 'DELIVERY') {
      const c = await read(db.collection('appConfig').doc('shipping'));
      if (!c.exists) throw error(409, "Admin chưa cấu hình biểu phí giao hàng.");
      if (!coordinates(data.latitude, data.longitude))
        throw error(409, "Địa chỉ nhận hàng chưa có GPS. Hãy lấy GPS tại nơi nhận hàng.");
      if (!coordinates(profile.latitude, profile.longitude))
        throw error(409, "Người bán cần lấy GPS cửa hàng và lưu trong Hồ sơ người bán.");
      const config = c.data();
      if (!(number(config.baseFeeVnd) >= 0 && config.baseFeeVnd <= 1000000
          && number(config.perKmFeeVnd) >= 0 && config.perKmFeeVnd <= 1000000
          && number(config.freeDistanceKm) >= 0 && config.freeDistanceKm <= 20040)) throw error(409, "Biểu phí giao hàng chưa hợp lệ.");
      distance = distanceKm(profile.latitude, profile.longitude, data.latitude, data.longitude);
      shippingFee = config.baseFeeVnd + Math.ceil(Math.max(0, distance - config.freeDistanceKm) * config.perKmFeeVnd);
      if (shippingFee > 1000000) throw error(409, "Phí giao hàng vượt giới hạn cho phép.");
    }
    if (data.paymentMethod === 'BANK_TRANSFER' && !['bankCode', 'bankAccountName', 'bankAccountNumber'].every(key => text(paymentProfile[key]))) {
      throw error(409, "Cửa hàng chưa cấu hình đủ tài khoản nhận chuyển khoản.");
    }
    const lines = rows.map(({item, product, price: value}) => ({...item, productName: text(product.name),
      unit: text(product.unit), subtotal: value.subtotal, finalUnitPrice: value.unit, originalSubtotal: value.originalSubtotal}));
    const subtotal = lines.reduce((sum, line) => sum + line.subtotal, 0);
    const originalSubtotal = lines.reduce((sum, line) => sum + line.originalSubtotal, 0);
    return {rows, campaign, campaignRef, campaignQuantity, allocation, profile, paymentProfile, sellerId,
      public: {lines, subtotal, originalSubtotal, totalDiscount: Math.max(0, originalSubtotal - subtotal), shippingFee,
        sellerName: text(seller.get('fullName')), sellerAddress: profile ? text(profile.address) : '',
        sellerLatitude: profile ? number(profile.latitude, 0) : 0,
        sellerLongitude: profile ? number(profile.longitude, 0) : 0,
        distanceKm: distance, total: subtotal + shippingFee, campaignId: campaign ? campaign.id : '',
        campaignTitle: campaign ? text(campaign.title) : '', campaignLocationName: campaign ? text(campaign.locationName) : '',
        rescueMode: campaign ? campaign.rescueMode : '',
        campaignLatitude: campaign ? (campaign.rescueMode === 'MOBILE_POINT' ? campaign.currentLatitude : campaign.latitude) : 0,
        campaignLongitude: campaign ? (campaign.rescueMode === 'MOBILE_POINT' ? campaign.currentLongitude : campaign.longitude) : 0,
        quoteIsEstimate: true}};
  }

  function guestIdentity(data) {
    if (!/^guest_[a-f0-9-]{36}$/i.test(text(data.guestId)) || !/^[a-f0-9]{64}$/i.test(text(data.guestToken))) {
      throw error(400, "Phiên mua hàng Guest không hợp lệ.");
    }
    return {ownerType: 'GUEST', ownerId: data.guestId, tokenHash: hash(data.guestToken)};
  }

  async function checkout(data, identity) {
    const items = validateSelection(data), requestId = text(data.requestId);
    if (!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(requestId)) throw error(400, "Mã yêu cầu đặt hàng không hợp lệ.");
    const receiverPhone = phone(data.receiverPhone), receiverName = text(data.receiverName);
    if (receiverName.length < 2 || receiverName.length > 100 || !/^0\d{8,11}$/.test(receiverPhone)
        || text(data.receiverAddress).length > 500 || text(data.note).length > 1000
        || (data.fulfillmentType === 'DELIVERY' && !text(data.receiverAddress))) throw error(400, "Kiểm tra tên, số điện thoại và địa chỉ nhận hàng.");
    const normalized = {items: [...items].sort((a, b) => a.batchId.localeCompare(b.batchId)), receiverName,
      receiverPhone, receiverAddress: text(data.receiverAddress), latitude: number(data.latitude, 0), longitude: number(data.longitude, 0),
      fulfillmentType: data.fulfillmentType, paymentMethod: data.paymentMethod, note: text(data.note)};
    if (normalized.latitude < -90 || normalized.latitude > 90 || normalized.longitude < -180 || normalized.longitude > 180) throw error(400, "Tọa độ nhận hàng chưa hợp lệ.");
    const fingerprint = hash(JSON.stringify(normalized)), orderRef = db.collection('orders').doc(requestId);
    const receiptRef = db.collection('checkoutReceipts').doc(requestId);
    await lifecycle.reconcileItems(items);
    const result = await db.runTransaction(async tx => {
      const existing = await tx.get(orderRef), receipt = await tx.get(receiptRef);
      if (existing.exists) {
        if (!receipt.exists || existing.get('ownerId') !== identity.ownerId || existing.get('ownerType') !== identity.ownerType
            || receipt.get('fingerprint') !== fingerprint || (identity.ownerType === 'GUEST' && receipt.get('tokenHash') !== identity.tokenHash)) {
          throw error(409, "Mã yêu cầu đã được dùng cho thông tin đặt hàng khác.");
        }
        return {order: existing.data(), replay: true};
      }
      // All reads precede writes, and inventory is re-read inside this transaction.
      if (identity.ownerType === 'CUSTOMER') {
        const account = await tx.get(db.collection('users').doc(identity.ownerId));
        if (!account.exists || account.get('role') !== 'CUSTOMER' || account.get('status') !== 'ACTIVE') throw error(403, "Tài khoản Customer không còn hoạt động.");
      }
      const q = await quote(ref => tx.get(ref), normalized);
      const cartRef = db.collection('customerCarts').doc(identity.ownerId);
      const cart = identity.ownerType === 'CUSTOMER' ? await tx.get(cartRef) : null;
      const codeRef = db.collection('guestOrderCodes').doc(`RF-${requestId.replace(/-/g, '').toUpperCase()}`);
      const code = identity.ownerType === 'GUEST' ? await tx.get(codeRef) : null;
      if (code && code.exists) throw error(409, "Mã đơn bị trùng. Hãy thử lại.");
      if (Number.isFinite(data.expectedSubtotal) && Math.abs(data.expectedSubtotal - q.public.subtotal) > 0.5
          || Number.isFinite(data.expectedShippingFee) && Math.abs(data.expectedShippingFee - q.public.shippingFee) > 0.5) {
        throw error(409, "QUOTE_CHANGED: Giá hoặc phí giao hàng đã thay đổi. Hãy kiểm tra báo giá mới.");
      }
      const paymentStatus = normalized.paymentMethod === 'BANK_TRANSFER' ? 'PENDING' : 'UNPAID';
      const order = {id: requestId, requestId, ownerType: identity.ownerType, ownerId: identity.ownerId,
        orderCode: `RF-${requestId.replace(/-/g, '').toUpperCase()}`, sellerId: q.sellerId, campaignId: q.public.campaignId,
        ...normalized, subtotal: q.public.subtotal, quantityDiscount: 0, shippingFee: q.public.shippingFee,
        totalAmount: q.public.total, paymentStatus, status: 'PENDING', campaignReservedQuantity: q.campaignQuantity,
        batchReservations: Object.fromEntries(items.map(item => [item.batchId, item.quantity])),
        campaignBatchReservations: Object.fromEntries(q.rows.filter(row => text(row.batch.activeCampaignId))
          .map(row => [row.item.batchId, row.item.quantity])),
        inventoryFinalized: false, finalizedStatus: '', createdAt: stamp(), updatedAt: stamp()};
      delete order.items; order.receiverLatitude = normalized.latitude; order.receiverLongitude = normalized.longitude;
      delete order.latitude; delete order.longitude;
      tx.create(orderRef, order);
      tx.create(receiptRef, {ownerId: identity.ownerId, ownerType: identity.ownerType, fingerprint,
        tokenHash: identity.tokenHash || '', createdAt: stamp()});
      const sellerNotificationId = `${requestId}_SELLER_PENDING`;
      tx.create(db.collection('notifications').doc(sellerNotificationId), {
        id: sellerNotificationId, userId: q.sellerId, type: 'ORDER',
        title: `Có đơn hàng mới ${order.orderCode}`,
        body: 'Bạn vừa nhận được đơn hàng mới. Mở RescueFarm để xác nhận.',
        referenceId: requestId, orderStatus: 'PENDING', isRead: false, createdAt: stamp()
      });
      for (const row of q.rows) {
        const item = row.item, batch = row.batch, p = row.product;
        tx.create(orderRef.collection('items').doc(item.batchId), {id: item.batchId, orderId: requestId,
          productId: item.productId, batchId: item.batchId, productNameSnapshot: text(p.name),
          productImageSnapshot: Array.isArray(p.imageUrls) ? text(p.imageUrls[0]) : '', unitSnapshot: normalizeUnit(p.unit),
          originalPriceSnapshot: p.originalPrice, rescuePriceSnapshot: p.rescuePrice, finalPriceSnapshot: row.price.unit,
          quantity: item.quantity, subtotal: row.price.subtotal});
        tx.update(row.batchRef, {availableQuantity: batch.availableQuantity - item.quantity,
          reservedQuantity: batch.reservedQuantity + item.quantity, status: 'RESERVED_PARTIAL',
          inventoryVersion: number(batch.inventoryVersion, 0) + 1, lastReservationOrderId: requestId,
          lastReservationQuantity: item.quantity, everOrdered: true, updatedAt: stamp()});
      }
      if (q.campaign) {
        const reserved = {...q.allocation.reserved};
        for (const [id, quantity] of Object.entries(order.campaignBatchReservations)) reserved[id] = (reserved[id] || 0) + quantity;
        tx.update(q.campaignRef, {reservedQuantity: (q.campaign.reservedQuantity || 0) + q.campaignQuantity,
          reservedByBatch: reserved, rescuedByBatch: q.allocation.rescued, allocationVersion: 1,
          lastReservationOrderId: requestId, lastReservationQuantity: q.campaignQuantity, updatedAt: stamp()});
      }
      const bank = q.paymentProfile || {};
      tx.create(db.collection('payments').doc(requestId), {id: requestId, orderId: requestId,
        method: normalized.paymentMethod, status: paymentStatus, amount: order.totalAmount, referenceCode: '',
        bankCode: normalized.paymentMethod === 'BANK_TRANSFER' ? text(bank.bankCode) : '',
        bankAccountName: normalized.paymentMethod === 'BANK_TRANSFER' ? text(bank.bankAccountName) : '',
        bankAccountNumber: normalized.paymentMethod === 'BANK_TRANSFER' ? text(bank.bankAccountNumber) : '',
        qrUrl: normalized.paymentMethod === 'BANK_TRANSFER' ? text(bank.qrUrl) : '', paidAt: null, createdAt: stamp(), updatedAt: stamp()});
      if (normalized.fulfillmentType === 'DELIVERY') tx.create(db.collection('shipments').doc(requestId), {
        id: requestId, orderId: requestId, carrierName: '', trackingCode: '', status: 'PENDING',
        pickupAddress: q.public.sellerAddress, pickupLatitude: q.public.sellerLatitude,
        pickupLongitude: q.public.sellerLongitude, receiverAddress: normalized.receiverAddress,
        receiverLatitude: normalized.latitude, receiverLongitude: normalized.longitude,
        distanceKm: q.public.distanceKm, shippingFee: q.public.shippingFee, createdAt: stamp(), updatedAt: stamp()});
      if (cart && cart.exists) {
        const quantities = new Map(items.map(item => [`${item.productId}::${item.batchId}`, item.quantity]));
        const remaining = (cart.get('items') || []).flatMap(item => {
          const bought = quantities.get(item.id);
          if (!bought) return [item];
          if (!(number(item.quantity) >= bought)) throw error(409, "QUOTE_CHANGED: Giỏ hàng đã thay đổi.");
          return item.quantity - bought > 0.000001 ? [{...item, quantity: item.quantity - bought}] : [];
        });
        tx.update(cartRef, {items: remaining, updatedAt: stamp()});
      }
      if (identity.ownerType === 'CUSTOMER') tx.create(db.collection('notifications').doc(`${requestId}_PENDING`), {
        id: `${requestId}_PENDING`, userId: identity.ownerId, type: 'ORDER', title: 'Đã tiếp nhận đơn hàng',
        body: 'Đơn hàng đang chờ cửa hàng xác nhận.', referenceId: requestId, orderStatus: 'PENDING',
        isRead: false, createdAt: stamp()});
      if (identity.ownerType === 'GUEST') tx.create(codeRef, {orderId: requestId, phoneHash: hash(receiverPhone), createdAt: stamp()});
      enqueueNotification(tx, db, admin, `order_${requestId}_seller`, {kind: 'DIRECT', resourceType: 'ORDER', resourceId: requestId,
        recipientUid: q.sellerId, decision: 'PENDING', title: 'Có đơn hàng mới', publicMessage: 'Có đơn hàng mới đang chờ cửa hàng xác nhận.'});
      enqueueNotification(tx, db, admin, `order_${requestId}_admins`, {kind: 'ADMINS', resourceType: 'ORDER', resourceId: requestId});
      if (identity.ownerType === 'CUSTOMER') enqueueNotification(tx, db, admin, `order_${requestId}_PENDING`, {
        kind: 'DIRECT', resourceType: 'ORDER', resourceId: requestId, recipientUid: identity.ownerId, decision: 'PENDING', inboxId: `${requestId}_PENDING`});
      return {order, replay: false};
    });
    // Re-read committed server timestamps; never serialize FieldValue sentinels to Android.
    result.order = (await orderRef.get()).data();
    if (identity.ownerType === 'GUEST') result.accessToken = data.guestToken;
    return serialize(result);
  }

  async function details(orderId) {
    const ref = db.collection('orders').doc(orderId);
    const [order, items, payment, shipment] = await Promise.all([ref.get(), ref.collection('items').get(),
      db.collection('payments').doc(orderId).get(), db.collection('shipments').doc(orderId).get()]);
    if (!order.exists || order.get('ownerType') !== 'GUEST') throw error(404, "Không tìm thấy đơn Guest phù hợp.");
    return serialize({order: {...order.data(), items: items.docs.map(item => item.data())},
      payment: payment.exists ? payment.data() : null, shipment: shipment.exists ? shipment.data() : null});
  }

  const route = (path, handler) => router.post(path, async (req, res, next) => {
    try { res.json(await handler(req)); } catch (failure) { next(failure); }
  });
  async function reconciledQuote(data) {
    await lifecycle.reconcileItems(validateSelection(data));
    return (await quote(ref => ref.get(), data)).public;
  }
  route('/quoteCustomerCheckout', async req => { await authenticate(req, ['CUSTOMER']); return reconciledQuote(req.body || {}); });
  route('/quoteGuestCheckout', async req => { await limit(req, 'guestQuote', 60); return reconciledQuote(req.body || {}); });
  route('/checkoutCustomer', async req => {
    const caller = await authenticate(req, ['CUSTOMER']);
    return checkout(req.body || {}, {ownerType: 'CUSTOMER', ownerId: caller.uid});
  });
  route('/checkoutGuest', async req => {
    const data = req.body || {}, identity = guestIdentity(data);
    await limit(req, 'guestCheckout', 10); await limit(req, 'guestSessionCheckout', 10, 600000, identity.ownerId);
    return checkout(data, identity);
  });
  route('/lookupGuestOrder', async req => {
    await limit(req, 'guestLookup', 10);
    const data = req.body || {}, code = text(data.orderCode).toUpperCase(), receiverPhone = phone(data.receiverPhone);
    if (!/^RF-[A-F0-9]{32}$/.test(code) || !/^0\d{8,11}$/.test(receiverPhone)) throw error(404, "Không tìm thấy đơn Guest phù hợp.");
    const entry = await db.collection('guestOrderCodes').doc(code).get();
    if (!entry.exists || entry.get('phoneHash') !== hash(receiverPhone)) throw error(404, "Không tìm thấy đơn Guest phù hợp.");
    const result = await details(entry.get('orderId')), accessToken = crypto.randomBytes(32).toString('hex');
    await db.collection('guestOrderGrants').doc(hash(accessToken)).set({orderId: entry.get('orderId'), expiresAt: timestamp(Date.now() + 86400000)});
    return {...result, accessToken};
  });
  route('/guestOrderDetails', async req => {
    await limit(req, 'guestDetails', 240);
    const data = req.body || {}, orderId = text(data.orderId), token = text(data.accessToken);
    if (!identifier(orderId) || !/^[a-f0-9]{64}$/i.test(token)) throw error(403, "Hãy tra cứu lại bằng mã đơn và số điện thoại.");
    const [receipt, grant] = await Promise.all([db.collection('checkoutReceipts').doc(orderId).get(),
      db.collection('guestOrderGrants').doc(hash(token)).get()]);
    const ownerAccess = receipt.exists && receipt.get('ownerType') === 'GUEST' && receipt.get('tokenHash') === hash(token);
    const lookupAccess = grant.exists && grant.get('orderId') === orderId && time(grant.get('expiresAt')) > Date.now();
    if (!ownerAccess && !lookupAccess) throw error(403, "Hãy tra cứu lại bằng mã đơn và số điện thoại.");
    return details(orderId);
  });
  route('/recordPostView', async req => {
    await limit(req, 'postViews', 120);
    const data = req.body || {};
    if (!identifier(data.postId) || !/^guest_[a-f0-9-]{36}$/i.test(text(data.viewerId))) throw error(400, "Thông tin lượt xem không hợp lệ.");
    const postRef = db.collection('posts').doc(data.postId);
    const viewRef = db.collection('postViewReceipts').doc(hash(`${data.postId}|${data.viewerId}|${Math.floor(Date.now() / 86400000)}`));
    return db.runTransaction(async tx => {
      const post = await tx.get(postRef), receipt = await tx.get(viewRef);
      if (!post.exists || post.get('status') !== 'PUBLISHED') throw error(404, "Bài viết không còn công khai.");
      if (receipt.exists) return {counted: false, viewCount: number(post.get('viewCount'), 0)};
      const viewCount = number(post.get('viewCount'), 0) + 1;
      tx.create(viewRef, {postId: data.postId, createdAt: stamp(), expiresAt: timestamp(Date.now() + 172800000)});
      tx.update(postRef, {viewCount}); return {counted: true, viewCount};
    });
  });
  route('/recordCampaignView', async req => {
    await limit(req, 'campaignViews', 120);
    const data = req.body || {};
    if (!identifier(data.campaignId) || !/^guest_[a-f0-9-]{36}$/i.test(text(data.viewerId)))
      throw error(400, "Thông tin lượt xem chiến dịch không hợp lệ.");
    const campaignRef = db.collection('campaigns').doc(data.campaignId);
    const viewRef = db.collection('campaignViewReceipts').doc(hash(`${data.campaignId}|${data.viewerId}|${Math.floor(Date.now() / 86400000)}`));
    return db.runTransaction(async tx => {
      const campaign = await tx.get(campaignRef), receipt = await tx.get(viewRef);
      if (!campaign.exists || campaign.get('status') !== 'ACTIVE') throw error(404, "Chiến dịch không còn công khai.");
      if (receipt.exists) return {counted: false, viewCount: number(campaign.get('viewCount'), 0)};
      const viewCount = number(campaign.get('viewCount'), 0) + 1;
      tx.create(viewRef, {campaignId: data.campaignId, createdAt: stamp(), expiresAt: timestamp(Date.now() + 172800000)});
      tx.update(campaignRef, {viewCount}); return {counted: true, viewCount};
    });
  });
  return {router, quote, checkout, details};
}

function serialize(value) {
  if (value && typeof value.toMillis === 'function') return value.toMillis();
  if (Array.isArray(value)) return value.map(serialize);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, v]) => [key, serialize(v)]));
  return value;
}

module.exports = {createCommerce, price, validateSelection, phone};
