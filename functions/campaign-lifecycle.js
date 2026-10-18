const express = require('express');
const crypto = require('node:crypto');
const {orderStatusLabel} = require('./order-status-labels');
const {campaignUnit, normalizeUnit} = require('./quantity-units');
const {enqueueNotification} = require('./notification-outbox');
const EPSILON = 0.000001;
const fail = (status, message) => Object.assign(new Error(message), {status});
const text = value => typeof value === 'string' ? value.trim() : '';
const millis = value => value && typeof value.toMillis === 'function' ? value.toMillis()
  : (value instanceof Date ? value.getTime() : (typeof value === 'number' ? value : 0));
const sum = values => Object.values(values).reduce((total, quantity) => total + quantity, 0);
const validId = value => /^[A-Za-z0-9_-]{1,128}$/.test(text(value));
const terminalOrders = new Set(['DELIVERED', 'CANCELLED']);

function quantityMap(raw, positive = false) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw fail(409, 'Dữ liệu phân bổ lô chưa hợp lệ.');
  const result = {};
  for (const [id, quantity] of Object.entries(raw)) {
    if (!validId(id) || !Number.isFinite(quantity) || quantity < 0 || (positive && quantity <= 0))
      throw fail(409, 'Số lượng phân bổ lô chưa hợp lệ.');
    result[id] = quantity;
  }
  return result;
}

function orderAllocations(order, targets) {
  if (!text(order.campaignId) || !(order.campaignReservedQuantity > 0)) return {};
  const source = quantityMap(order.campaignBatchReservations || order.batchReservations || {});
  const allocations = Object.fromEntries(Object.entries(source).filter(([id]) => Object.hasOwn(targets, id)));
  if (Math.abs(sum(allocations) - order.campaignReservedQuantity) > EPSILON)
    throw fail(409, 'Phân bổ đơn cũ chưa khớp; cần đối soát trước khi cập nhật Campaign.');
  return allocations;
}

function validateProgress(campaign, reserved, rescued) {
  const targets = quantityMap(campaign.batchTargets, true);
  for (const id of new Set([...Object.keys(reserved), ...Object.keys(rescued)])) {
    if (!Object.hasOwn(targets, id) || (reserved[id] || 0) + (rescued[id] || 0) > targets[id] + EPSILON)
      throw fail(409, 'Phân bổ lô vượt mục tiêu; cần đối soát dữ liệu Campaign.');
  }
  if (Math.abs(sum(reserved) - (campaign.reservedQuantity || 0)) > EPSILON
      || Math.abs(sum(rescued) - (campaign.rescuedQuantity || 0)) > EPSILON)
    throw fail(409, 'Tổng phân bổ Campaign chưa khớp; cần đối soát dữ liệu.');
  return {reserved, rescued};
}

// Legacy counters are reconstructed only from authoritative orders, never guessed across batches.
async function readAllocations(read, db, campaignId, campaign) {
  if (campaign.allocationVersion === 1)
    return validateProgress(campaign, quantityMap(campaign.reservedByBatch || {}), quantityMap(campaign.rescuedByBatch || {}));
  const reserved = {}, rescued = {};
  if ((campaign.reservedQuantity || 0) || (campaign.rescuedQuantity || 0)) {
    const orders = await read(db.collection('orders').where('campaignId', '==', campaignId));
    for (const doc of orders.docs) {
      const order = doc.data(), allocations = orderAllocations(order, campaign.batchTargets);
      const destination = order.status === 'DELIVERED' ? rescued : !terminalOrders.has(order.status) ? reserved : null;
      if (destination) for (const [id, quantity] of Object.entries(allocations))
        destination[id] = (destination[id] || 0) + quantity;
    }
  }
  return validateProgress(campaign, reserved, rescued);
}

function isLive(campaign, now) {
  return campaign && campaign.status === 'ACTIVE' && millis(campaign.endDate) > now
    && (campaign.rescuedQuantity || 0) < campaign.targetQuantity - EPSILON;
}

function nextClosedStatus(campaign, now) {
  if (campaign.status !== 'ACTIVE') return campaign.status;
  if ((campaign.rescuedQuantity || 0) >= campaign.targetQuantity - EPSILON) return 'COMPLETED';
  if (millis(campaign.endDate) <= now) return 'EXPIRED';
  return 'ACTIVE';
}

function createCampaignLifecycle({db, admin, authenticate}) {
  const router = express.Router();
  const stamp = () => admin.firestore.FieldValue.serverTimestamp();
  const allocationFields = progress => ({reservedByBatch: progress.reserved, rescuedByBatch: progress.rescued, allocationVersion: 1});
  const readBatches = async (tx, campaign) => {
    const targets = quantityMap(campaign.batchTargets, true);
    if (Object.keys(targets).length > 200) throw fail(409, 'Campaign cũ vượt giới hạn lô được hỗ trợ.');
    return Promise.all(Object.keys(targets).map(id => tx.get(db.collection('productBatches').doc(id))));
  };
  const unlink = (tx, id, batches) => {
    for (const batch of batches) if (batch.exists && batch.get('activeCampaignId') === id)
      tx.update(batch.ref, {activeCampaignId: null, inventoryVersion: (batch.get('inventoryVersion') || 0) + 1, updatedAt: stamp()});
  };
  async function verifyUnit(tx, id, campaign, batches) {
    const products = await Promise.all(batches.map(b => b.exists ? tx.get(db.collection('products').doc(b.get('productId'))) : null));
    if (products.some(p => !p || !p.exists)) throw fail(409, 'Thiếu sản phẩm để xác minh đơn vị Campaign.');
    const unit = campaignUnit(products.map(p => p.data()));
    if (campaign.quantityUnit && normalizeUnit(campaign.quantityUnit) !== unit) throw fail(409, 'Đơn vị Campaign đã thay đổi; cần đối soát.');
    if (campaign.unitStatus !== 'VERIFIED' && ((campaign.reservedQuantity || 0) + (campaign.rescuedQuantity || 0) > 0)) {
      const orders = await tx.get(db.collection('orders').where('campaignId', '==', id));
      if (orders.empty) throw fail(409, 'Thiếu đơn lịch sử để xác minh đơn vị.');
      for (const order of orders.docs) {
        if (order.get('status') === 'CANCELLED') continue;
        const items = await tx.get(order.ref.collection('items'));
        const relevant = items.docs.filter(item => Object.hasOwn(campaign.batchTargets, item.get('batchId')));
        if (!relevant.length || relevant.some(item => normalizeUnit(item.get('unitSnapshot')) !== unit))
          throw fail(409, 'Đơn vị snapshot đơn cũ không khớp; cần đối soát Campaign.');
      }
    }
    return unit;
  }

  async function reconcileCampaign(id) {
    return db.runTransaction(async tx => {
      const ref = db.collection('campaigns').doc(id), snap = await tx.get(ref);
      if (!snap.exists) return {missing: true};
      const campaign = snap.data(), next = nextClosedStatus(campaign, Date.now());
      const batches = await readBatches(tx, campaign);
      let unitFields = {};
      if (campaign.unitStatus !== 'VERIFIED') {
        try { unitFields = {quantityUnit: await verifyUnit(tx, id, campaign, batches), unitStatus: 'VERIFIED'}; }
        catch (error) { if (error.status !== 409) throw error; unitFields = {unitStatus: 'REVIEW_REQUIRED', unitReviewReason: error.message}; }
      }
      if (next === 'ACTIVE') {
        let progressFields = {};
        if (campaign.allocationVersion !== 1) {
          const progress = await readAllocations(ref => tx.get(ref), db, id, campaign);
          progressFields = allocationFields(progress);
        }
        if (Object.keys(unitFields).length || Object.keys(progressFields).length) tx.update(ref, {...unitFields, ...progressFields});
        return {status: next};
      }
      // Closing a campaign never releases stock held by orders. Those orders retain their campaignId.
      unlink(tx, id, batches);
      if (Object.keys(unitFields).length) tx.update(ref, unitFields);
      if (next !== campaign.status) tx.update(ref, {status: next, locationSharingEnabled: false,
        ...(next === 'COMPLETED' ? {completedAt: stamp()} : {expiredAt: stamp()}), updatedAt: stamp()});
      return {status: next};
    });
  }

  async function reconcileItems(items) {
    const ids = [...new Set(items.map(item => text(item.batchId)).filter(validId))];
    const batches = await Promise.all(ids.map(id => db.collection('productBatches').doc(id).get()));
    const campaigns = [...new Set(batches.filter(b => b.exists).map(b => text(b.get('activeCampaignId'))).filter(Boolean))];
    for (const id of campaigns) {
      const result = await reconcileCampaign(id);
      if (result.missing) for (const batch of batches.filter(b => b.exists && b.get('activeCampaignId') === id)) {
        await db.runTransaction(async tx => {
          const current = await tx.get(batch.ref), campaign = await tx.get(db.collection('campaigns').doc(id));
          if (!campaign.exists && current.exists && current.get('activeCampaignId') === id)
            tx.update(batch.ref, {activeCampaignId: null, inventoryVersion: (current.get('inventoryVersion') || 0) + 1, updatedAt: stamp()});
        });
      }
    }
  }

  async function moderate(caller, data) {
    if (caller.role !== 'ADMIN') throw fail(403, 'Chỉ Admin được duyệt Campaign.');
    const id = text(data.campaignId), target = text(data.targetStatus), reason = text(data.reason);
    if (!validId(id) || !['ACTIVE', 'REJECTED', 'STOPPED'].includes(target) || reason.length > 1000)
      throw fail(400, 'Dữ liệu duyệt Campaign không hợp lệ.');
    if (target !== 'ACTIVE' && reason.length < 3) throw fail(400, 'Cần nhập lý do ít nhất 3 ký tự.');
    return db.runTransaction(async tx => {
      const ref = db.collection('campaigns').doc(id), snap = await tx.get(ref);
      if (!snap.exists) throw fail(404, 'Không tìm thấy Campaign.');
      const campaign = snap.data();
      if (campaign.status !== target && !((campaign.status === 'PENDING_APPROVAL' && ['ACTIVE', 'REJECTED'].includes(target))
          || (campaign.status === 'ACTIVE' && target === 'STOPPED'))) throw fail(409, 'Campaign đã đổi trạng thái. Hãy tải lại.');
      const batches = await readBatches(tx, campaign);
      if (target === 'ACTIVE') {
        if (campaign.status === target) return {ok: true, replay: true};
        const now = Date.now(), targets = quantityMap(campaign.batchTargets, true);
        if (!batches.length || batches.length > 8 || !Number.isFinite(campaign.targetQuantity)
            || Math.abs(sum(targets) - campaign.targetQuantity) > EPSILON
            || !(millis(campaign.startDate) > 0 && millis(campaign.endDate) > millis(campaign.startDate))
            || millis(campaign.endDate) <= now) throw fail(409, 'Mục tiêu hoặc thời gian Campaign không còn hợp lệ.');
        const seller = await tx.get(db.collection('users').doc(campaign.sellerId));
        if (!seller.exists || seller.get('role') !== 'SELLER' || seller.get('status') !== 'ACTIVE'
            || seller.get('sellerStatus') !== 'APPROVED') throw fail(409, 'Seller không còn đủ điều kiện kinh doanh.');
        if ((campaign.reservedQuantity || 0) !== 0 || (campaign.rescuedQuantity || 0) !== 0)
          throw fail(409, 'Campaign chờ duyệt đã có số liệu giao dịch bất thường; cần đối soát.');
        const products = await Promise.all(batches.map(batch => {
          if (!batch.exists || !validId(batch.get('productId'))) throw fail(409, 'Một lô hàng không còn tồn tại.');
          return tx.get(db.collection('products').doc(batch.get('productId')));
        }));
        const conflicts = await Promise.all(batches.map(batch => {
          const linked = text(batch.get('activeCampaignId'));
          return linked && linked !== id ? tx.get(db.collection('campaigns').doc(linked)) : null;
        }));
        if (products.some(product => !product.exists)) throw fail(409, 'Một sản phẩm không còn tồn tại.');
        const quantityUnit = campaignUnit(products.map(product => product.data()));
        if (campaign.quantityUnit && normalizeUnit(campaign.quantityUnit) !== quantityUnit)
          throw fail(409, 'Đơn vị khai báo của Campaign không khớp đơn vị các lô. Hãy sửa bản gửi duyệt.');
        let earliestExpiry = Infinity;
        batches.forEach((batch, index) => {
          const b = batch.data(), product = products[index];
          if (!product.exists || product.get('sellerId') !== campaign.sellerId || product.get('status') !== 'ACTIVE')
            throw fail(409, 'Sản phẩm sai chủ sở hữu hoặc không còn hoạt động.');
          if (!['AVAILABLE', 'RESERVED_PARTIAL'].includes(b.status) || millis(b.expiryDate) <= now
              || !Number.isFinite(b.availableQuantity) || b.availableQuantity + EPSILON < targets[batch.id]
              || ![b.initialQuantity, b.reservedQuantity, b.soldQuantity].every(value => Number.isFinite(value) && value >= 0)
              || Math.abs(b.initialQuantity - b.availableQuantity - b.reservedQuantity - b.soldQuantity) > EPSILON)
            throw fail(409, 'Lô hàng đã hết hạn, thiếu tồn hoặc số liệu tồn kho chưa nhất quán.');
          if (conflicts[index] && conflicts[index].exists && isLive(conflicts[index].data(), now))
            throw fail(409, 'Lô hàng đang thuộc Campaign khác đang hoạt động.');
          earliestExpiry = Math.min(earliestExpiry, millis(b.expiryDate));
        });
        const days = value => (value - now) / 86400000;
        const urgencyLevel = days(earliestExpiry) <= 2 || days(millis(campaign.endDate)) <= 1 ? 'CRITICAL'
          : days(earliestExpiry) <= 5 || days(millis(campaign.endDate)) <= 3 ? 'HIGH' : 'NORMAL';
        await require('./campaign-media-policy').assertApprovalMedia(tx, db, id, campaign);
        require('./campaign-media-policy').freezeCampaignAssets(tx, db, campaign);
        tx.update(ref, {status: 'ACTIVE', moderatedBy: caller.uid, moderatedAt: stamp(), moderationReason: reason,
          reservedByBatch: {}, rescuedByBatch: {}, allocationVersion: 1, quantityUnit, unitStatus: 'VERIFIED', urgencyLevel, updatedAt: stamp()});
        for (const batch of batches) tx.update(batch.ref, {activeCampaignId: id,
          inventoryVersion: (batch.get('inventoryVersion') || 0) + 1, updatedAt: stamp()});
        enqueueNotification(tx, db, admin, 'campaign_active_' + id, {kind: 'CAMPAIGN', resourceType: 'CAMPAIGN', resourceId: id,
          title: 'Chiến dịch giải cứu đang hoạt động', publicMessage: text(campaign.title)});
      } else {
        unlink(tx, id, batches);
        if (campaign.status === target) return {ok: true, replay: true};
        tx.update(ref, {status: target, moderatedBy: caller.uid, moderatedAt: stamp(), moderationReason: reason,
          locationSharingEnabled: false, updatedAt: stamp()});
      }
      const audit = db.collection('adminAuditLogs').doc();
      tx.create(audit, {adminId: caller.uid, resourceType: 'CAMPAIGNS', resourceId: id,
        fromState: campaign.status, toState: target, reason, createdAt: stamp()});
      return {ok: true, replay: false};
    });
  }

  async function transitionOrder(caller, data) {
    if (!['SELLER', 'ADMIN', 'CUSTOMER', 'GUEST'].includes(caller.role)) throw fail(403, 'Bạn không có quyền xử lý đơn.');
    const id = text(data.orderId), target = text(data.targetStatus);
    if (!validId(id) || !['CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'SHIPPING', 'DELIVERED', 'CANCELLED'].includes(target))
      throw fail(400, 'Đơn hoặc trạng thái không hợp lệ.');
    return db.runTransaction(async tx => {
      const orderRef = db.collection('orders').doc(id), snap = await tx.get(orderRef);
      if (!snap.exists) throw fail(404, 'Không tìm thấy đơn.');
      const order = snap.data();
      if (caller.role === 'SELLER') {
        const seller = await tx.get(db.collection('users').doc(caller.uid));
        if (order.sellerId !== caller.uid || !seller.exists || seller.get('status') !== 'ACTIVE'
            || seller.get('sellerStatus') !== 'APPROVED') throw fail(403, 'Seller không có quyền xử lý đơn này.');
      }
      let buyer = caller.role === 'CUSTOMER' && order.ownerType === 'CUSTOMER' && order.ownerId === caller.uid;
      if (caller.role === 'GUEST') {
        const token = text(caller.accessToken);
        if (/^[a-f0-9]{64}$/i.test(token) && order.ownerType === 'GUEST') {
          const tokenHash = crypto.createHash('sha256').update(token).digest('hex');
          const [receipt, grant] = await Promise.all([
            tx.get(db.collection('checkoutReceipts').doc(id)),
            tx.get(db.collection('guestOrderGrants').doc(tokenHash))
          ]);
          const ownerAccess = receipt.exists && receipt.get('ownerType') === 'GUEST'
            && receipt.get('ownerId') === order.ownerId && receipt.get('tokenHash') === tokenHash;
          const lookupAccess = grant.exists && grant.get('orderId') === id
            && (millis(grant.get('expiresAt')) > Date.now());
          buyer = Boolean(ownerAccess || lookupAccess);
        }
      }
      if ((target === 'DELIVERED' && !buyer) || (target !== 'DELIVERED' && ['CUSTOMER', 'GUEST'].includes(caller.role)))
        throw fail(403, 'Chỉ người mua của đơn hàng được xác nhận đã nhận hàng.');
      if (order.status === target) return {ok: true, replay: true};
      if (order.paymentMethod === 'BANK_TRANSFER' && order.paymentStatus !== 'PAID'
          && !['CANCELLED', 'DELIVERED'].includes(target))
        throw fail(409, 'Cần nhập mã giao dịch và xác nhận đã nhận tiền trước khi xử lý đơn chuyển khoản.');
      const allowed = {PENDING: ['CONFIRMED', 'CANCELLED'], CONFIRMED: ['PREPARING', 'CANCELLED'],
        PREPARING: ['CANCELLED', order.fulfillmentType === 'DELIVERY' ? 'SHIPPING' : order.fulfillmentType === 'PICKUP' ? 'READY_FOR_PICKUP' : 'DELIVERED'],
        READY_FOR_PICKUP: ['DELIVERED', 'CANCELLED'], SHIPPING: ['DELIVERED']};
      if (order.inventoryFinalized || !(allowed[order.status] || []).includes(target)) throw fail(409, 'Chuyển trạng thái đơn không hợp lệ.');
      if (target === 'CANCELLED' && order.paymentStatus === 'PAID') throw fail(409, 'Đơn đã thanh toán cần quy trình hoàn tiền.');
      if (target === 'DELIVERED' && order.paymentMethod === 'BANK_TRANSFER' && order.paymentStatus !== 'PAID')
        throw fail(409, 'Cần xác nhận chuyển khoản trước khi hoàn tất.');
      const paymentRef = db.collection('payments').doc(id), payment = await tx.get(paymentRef);
      const shipmentRef = db.collection('shipments').doc(id);
      const shipment = order.fulfillmentType === 'DELIVERY' ? await tx.get(shipmentRef) : null;
      if (!payment.exists || shipment && !shipment.exists) throw fail(409, 'Thiếu dữ liệu thanh toán/vận chuyển.');
      if (payment.get('status') !== order.paymentStatus) throw fail(409, 'Trạng thái thanh toán chưa nhất quán; cần đối soát trước khi xử lý.');
      const terminal = terminalOrders.has(target), reservations = quantityMap(order.batchReservations || {}, true);
      const batches = terminal ? await Promise.all(Object.keys(reservations).map(batch => tx.get(db.collection('productBatches').doc(batch)))) : [];
      const campaignRef = text(order.campaignId) ? db.collection('campaigns').doc(order.campaignId) : null;
      const campaignSnap = terminal && campaignRef ? await tx.get(campaignRef) : null;
      if (terminal && order.campaignReservedQuantity > 0 && (!campaignSnap || !campaignSnap.exists))
        throw fail(409, 'Không tìm thấy Campaign lịch sử của đơn.');
      let campaignUpdate = null, closedBatches = [];
      if (terminal && campaignSnap && campaignSnap.exists && order.campaignReservedQuantity > 0) {
        const campaign = campaignSnap.data();
        const progress = await readAllocations(ref => tx.get(ref), db, campaignSnap.id, campaign);
        for (const [batch, quantity] of Object.entries(orderAllocations(order, campaign.batchTargets))) {
          if ((progress.reserved[batch] || 0) + EPSILON < quantity) throw fail(409, 'Phân bổ Campaign của đơn không còn nhất quán.');
          progress.reserved[batch] = Math.max(0, progress.reserved[batch] - quantity);
          if (target === 'DELIVERED') progress.rescued[batch] = (progress.rescued[batch] || 0) + quantity;
        }
        campaignUpdate = {...allocationFields(progress), reservedQuantity: sum(progress.reserved), rescuedQuantity: sum(progress.rescued),
          lastLifecycleOrderId: id, lastLifecycleAction: target, lastLifecycleQuantity: order.campaignReservedQuantity, updatedAt: stamp()};
        const next = nextClosedStatus({...campaign, ...campaignUpdate}, Date.now());
        if (next !== 'ACTIVE') {
          closedBatches = await readBatches(tx, campaign);
          campaignUpdate.status = next;
          campaignUpdate.locationSharingEnabled = false;
          if (next === 'COMPLETED' && campaign.status !== 'COMPLETED') campaignUpdate.completedAt = stamp();
          if (next === 'EXPIRED' && campaign.status !== 'EXPIRED') campaignUpdate.expiredAt = stamp();
        }
      }
      // No more reads below this point. Inventory and per-batch allocations commit together.
      const unlinkIds = new Set(closedBatches.filter(b => b.exists && b.get('activeCampaignId') === order.campaignId).map(b => b.id));
      for (const batch of batches) {
        if (!batch.exists) throw fail(409, 'Không tìm thấy lô hàng của đơn.');
        const b = batch.data(), quantity = reservations[batch.id];
        if (![b.initialQuantity, b.availableQuantity, b.reservedQuantity, b.soldQuantity].every(Number.isFinite)
            || b.reservedQuantity + EPSILON < quantity
            || Math.abs(b.initialQuantity - b.availableQuantity - b.reservedQuantity - b.soldQuantity) > EPSILON)
          throw fail(409, 'Tồn kho của đơn không còn nhất quán.');
        const available = b.availableQuantity + (target === 'CANCELLED' ? quantity : 0), reserved = Math.max(0, b.reservedQuantity - quantity);
        tx.update(batch.ref, {availableQuantity: available, reservedQuantity: reserved,
          soldQuantity: b.soldQuantity + (target === 'DELIVERED' ? quantity : 0),
          status: b.status === 'ARCHIVED' ? 'ARCHIVED' : millis(b.expiryDate) <= Date.now() ? 'EXPIRED' : reserved > EPSILON ? 'RESERVED_PARTIAL' : available <= EPSILON ? 'SOLD_OUT' : 'AVAILABLE',
          ...(unlinkIds.has(batch.id) ? {activeCampaignId: null} : {}),
          inventoryVersion: (b.inventoryVersion || 0) + 1, lastLifecycleOrderId: id, lastLifecycleAction: target,
          lastLifecycleQuantity: quantity, updatedAt: stamp()});
        unlinkIds.delete(batch.id);
      }
      for (const batch of closedBatches) if (unlinkIds.has(batch.id)) tx.update(batch.ref,
        {activeCampaignId: null, inventoryVersion: (batch.get('inventoryVersion') || 0) + 1, updatedAt: stamp()});
      if (campaignUpdate) tx.update(campaignRef, campaignUpdate);
      const paymentStatus = target === 'DELIVERED' && order.paymentMethod !== 'BANK_TRANSFER' ? 'PAID'
        : target === 'CANCELLED' ? 'FAILED' : order.paymentStatus;
      tx.update(orderRef, {status: target, paymentStatus, updatedAt: stamp(),
        ...(terminal ? {inventoryFinalized: true, finalizedStatus: target} : {}),
        ...(target === 'DELIVERED' ? {deliveredAt: stamp(), receivedAt: stamp(), receivedBy: caller.uid || order.ownerId} : {})});
      if (terminal) tx.update(paymentRef, {status: paymentStatus, updatedAt: stamp(),
        ...(paymentStatus === 'PAID' && payment.get('status') !== 'PAID' ? {paidAt: stamp()} : {})});
      if (shipment) tx.update(shipmentRef, {status: ({PREPARING: 'READY', SHIPPING: 'SHIPPING', DELIVERED: 'DELIVERED', CANCELLED: 'CANCELLED'})[target] || 'PENDING', updatedAt: stamp()});
      const recipients = [...new Set([...(order.ownerType === 'CUSTOMER' ? [order.ownerId] : []),
        ...(buyer ? [order.sellerId] : [])])];
      for (const recipient of recipients) {
        const inboxId = `${id}_${target}${recipient === order.sellerId ? '_seller' : ''}`;
        const publicMessage = `Trạng thái: ${orderStatusLabel(target, order.paymentMethod)}`;
        tx.set(db.collection('notifications').doc(inboxId), {id: inboxId, userId: recipient, type: 'ORDER',
          referenceId: id, orderStatus: target, title: 'Cập nhật đơn hàng', body: publicMessage,
          isRead: false, createdAt: stamp()});
        enqueueNotification(tx, db, admin, `order_${inboxId}`, {kind: 'DIRECT', resourceType: 'ORDER',
          resourceId: id, recipientUid: recipient, decision: target, publicMessage, inboxId});
      }
      return {ok: true, replay: false};
    });
  }

  async function confirmBankTransfer(caller, data) {
    if (caller.role !== 'SELLER') throw fail(403, 'Chỉ cửa hàng của đơn được xác nhận nhận tiền.');
    const id = text(data.orderId), reference = text(data.referenceCode);
    if (!validId(id) || reference.length < 3 || reference.length > 120)
      throw fail(400, 'Nhập mã giao dịch chuyển khoản từ 3 đến 120 ký tự.');
    return db.runTransaction(async tx => {
      const orderRef = db.collection('orders').doc(id), paymentRef = db.collection('payments').doc(id);
      const orderSnap = await tx.get(orderRef), payment = await tx.get(paymentRef);
      const seller = await tx.get(db.collection('users').doc(caller.uid));
      if (!orderSnap.exists || !payment.exists) throw fail(404, 'Không tìm thấy đơn hoặc thanh toán.');
      const order = orderSnap.data();
      if (order.sellerId !== caller.uid || !seller.exists || seller.get('status') !== 'ACTIVE'
          || seller.get('sellerStatus') !== 'APPROVED') throw fail(403, 'Bạn không có quyền xác nhận đơn này.');
      if (payment.get('verificationProvider') === 'SEPAY')
        throw fail(409, 'Đơn dùng SePay: hệ thống tự xác minh khi ngân hàng báo tiền về.');
      if (order.paymentMethod !== 'BANK_TRANSFER' || payment.get('method') !== 'BANK_TRANSFER'
          || order.status === 'CANCELLED') throw fail(409, 'Đơn không thể xác nhận chuyển khoản.');
      if (order.paymentStatus === 'PAID' && payment.get('status') === 'PAID') {
        if (payment.get('referenceCode') !== reference) throw fail(409, 'Đơn đã được xác nhận bằng mã giao dịch khác.');
        return {ok: true, replay: true};
      }
      if (order.paymentStatus !== 'PENDING' || payment.get('status') !== 'PENDING'
          || !['PENDING', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'SHIPPING'].includes(order.status))
        throw fail(409, 'Trạng thái đơn hoặc thanh toán không cho phép xác nhận.');
      tx.update(paymentRef, {status: 'PAID', referenceCode: reference, paidAt: stamp(), updatedAt: stamp()});
      tx.update(orderRef, {paymentStatus: 'PAID', status: order.status === 'PENDING' ? 'CONFIRMED' : order.status,
        ...(order.status === 'PENDING' ? {confirmedAt: stamp()} : {}), updatedAt: stamp()});
      if (order.ownerType === 'CUSTOMER') {
        const inboxId = `${id}_BANK_CONFIRMED`;
        const publicMessage = 'Cửa hàng đã nhận tiền và xác nhận đơn hàng.';
        tx.set(db.collection('notifications').doc(inboxId), {id: inboxId, userId: order.ownerId, type: 'ORDER',
          referenceId: id, orderStatus: order.status === 'PENDING' ? 'CONFIRMED' : order.status,
          title: 'Đã nhận tiền và xác nhận đơn', body: publicMessage, isRead: false, createdAt: stamp()});
        enqueueNotification(tx, db, admin, `order_${inboxId}`, {kind: 'DIRECT', resourceType: 'ORDER',
          resourceId: id, recipientUid: order.ownerId, publicMessage, inboxId});
      }
      return {ok: true, replay: false};
    });
  }

  // Also repairs legacy pending/closed links. Safe to repeat and to run while orders are in flight.
  async function reconcileAll() {
    let cursor = null, repaired = 0;
    while (true) {
      let query = db.collection('campaigns').orderBy(admin.firestore.FieldPath.documentId()).limit(100);
      if (cursor) query = query.startAfter(cursor);
      const page = await query.get();
      if (page.empty) break;
      for (const doc of page.docs) {
        try { await reconcileCampaign(doc.id); repaired++; }
        catch (error) { console.error('Campaign reconciliation requires attention:', doc.id, error.message); }
      }
      cursor = page.docs[page.docs.length - 1];
    }
    return {checked: repaired};
  }

  async function reconcileExpired() {
    const page = await db.collection('campaigns').where('status', '==', 'ACTIVE')
      .where('endDate', '<=', admin.firestore.Timestamp.fromMillis(Date.now())).limit(100).get();
    for (const doc of page.docs) {
      try { await reconcileCampaign(doc.id); }
      catch (error) { console.error('Expired Campaign requires attention:', doc.id, error.message); }
    }
    return {checked: page.size};
  }

  router.post('/campaigns/moderate', async (req, res, next) => {
    try { res.json(await moderate(await authenticate(req, ['ADMIN']), req.body || {})); } catch (error) { next(error); }
  });
  router.post('/orders/transition', async (req, res, next) => {
    try { res.json(await transitionOrder(await authenticate(req, ['SELLER', 'ADMIN', 'CUSTOMER']), req.body || {})); } catch (error) { next(error); }
  });
  router.post('/orders/confirm-bank-transfer', async (req, res, next) => {
    try { res.json(await confirmBankTransfer(await authenticate(req, ['SELLER']), req.body || {})); }
    catch (error) { next(error); }
  });
  router.post('/orders/confirm-guest-receipt', async (req, res, next) => {
    try { res.json(await transitionOrder({role: 'GUEST', accessToken: text(req.body && req.body.accessToken)},
      {orderId: req.body && req.body.orderId, targetStatus: 'DELIVERED'})); } catch (error) { next(error); }
  });
  return {router, moderate, confirmBankTransfer, transitionOrder, reconcileItems, reconcileAll, reconcileExpired, reconcileCampaign};
}

module.exports = {createCampaignLifecycle, readAllocations, orderAllocations, validateProgress, quantityMap, sum, isLive, nextClosedStatus};
