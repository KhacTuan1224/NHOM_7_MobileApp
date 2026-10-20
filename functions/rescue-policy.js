const TYPES = ['MYSTERY_BOX', 'GROUP_BUY', 'SUBSCRIPTION', 'BULK', 'DONATION', 'FLASH'];
const DAY = 86400000;
const fail = (status, message) => Object.assign(new Error(message), {status});
const text = value => typeof value === 'string' ? value.trim() : '';
const id = value => typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);
const time = value => value && typeof value.toMillis === 'function' ? value.toMillis() : Number(value) || 0;
const round = value => Math.round(value * 1e6) / 1e6;
function number(value, min, max, label, integer = false) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max || integer && !Number.isInteger(value))
    throw fail(400, `${label} không hợp lệ.`);
  return value;
}
function string(value, min, max, label) {
  value = text(value);
  if (value.length < min || value.length > max) throw fail(400, `${label} không hợp lệ.`);
  return value;
}
function coordinates(latitude, longitude) {
  number(latitude, -90, 90, 'Vĩ độ'); number(longitude, -180, 180, 'Kinh độ');
  if (latitude === 0 && longitude === 0) throw fail(400, 'Hãy chọn vị trí nhận hàng thực tế.');
  return {latitude, longitude};
}
function distanceKm(a, b) {
  const rad = d => d * Math.PI / 180, lat = rad(b.latitude - a.latitude), lon = rad(b.longitude - a.longitude);
  const h = Math.sin(lat / 2) ** 2 + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(lon / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
}
function validateOffer(body, now) {
  if (!id(body.requestId) || !TYPES.includes(body.type)) throw fail(400, 'Mã yêu cầu hoặc phương thức giải cứu không hợp lệ.');
  const totalUnits = number(body.totalUnits, 1, 100000, 'Số suất', true);
  const unitPrice = number(body.unitPrice, 0, 1e9, 'Giá mỗi suất', true);
  const originalUnitPrice = number(body.originalUnitPrice, 1, 1e9, 'Giá trị gốc', true);
  if (unitPrice > originalUnitPrice || (body.type === 'DONATION') !== (unitPrice === 0))
    throw fail(400, 'Chỉ gói quyên góp có giá 0 đồng; giá giải cứu không vượt giá trị gốc.');
  const closesAt = number(body.closesAt, now + 60000, now + 30 * DAY, 'Hạn đăng ký');
  const pickupStartAt = number(body.pickupStartAt, now, now + 30 * DAY, 'Bắt đầu nhận');
  const pickupEndAt = number(body.pickupEndAt, Math.max(pickupStartAt + 60000, closesAt), now + 31 * DAY, 'Kết thúc nhận');
  if (body.type === 'GROUP_BUY' && pickupStartAt < closesAt)
    throw fail(400, 'Nhóm mua chung chỉ nhận hàng sau hạn gom nhóm.');
  if (body.type === 'FLASH' && closesAt - now > 2 * 3600000)
    throw fail(400, 'Flash sale giờ chót kéo dài tối đa 2 giờ.');
  if (!Array.isArray(body.components) || !body.components.length || body.components.length > 12)
    throw fail(400, 'Chọn từ 1 đến 12 lô hàng cho mỗi gói.');
  const seen = new Set();
  const components = body.components.map(component => {
    if (!id(component.batchId) || seen.has(component.batchId)) throw fail(400, 'Lô hàng không hợp lệ hoặc bị trùng.');
    seen.add(component.batchId);
    const quantityPerUnit = number(component.quantityPerUnit, 0.000001, 1e6, 'Lượng nông sản mỗi suất');
    if (Math.abs(quantityPerUnit - round(quantityPerUnit)) > 1e-9) throw fail(400, 'Số lượng hỗ trợ tối đa 6 chữ số thập phân.');
    if (quantityPerUnit * totalUnits > 1e6) throw fail(400, 'Tổng lượng mỗi lô trong một gói không vượt 1.000.000 đơn vị.');
    return {batchId: component.batchId, quantityPerUnit};
  });
  const minPeople = body.type === 'GROUP_BUY' ? number(body.minPeople, 2, 50, 'Số người tối thiểu', true) : 1;
  const minUnits = body.type === 'GROUP_BUY' ? number(body.minUnits, minPeople, totalUnits, 'Số suất tối thiểu', true) : 1;
  return {type: body.type, title: string(body.title, 3, 120, 'Tên gói'), description: string(body.description, 10, 2000, 'Mô tả'),
    categoryLabel: string(body.categoryLabel, 2, 100, 'Phân loại'), contentsDisclosure: string(body.contentsDisclosure, 3, 1000, 'Thông tin thành phần / dị ứng'),
    unitLabel: string(body.unitLabel, 1, 24, 'Đơn vị suất'), pickupAddress: string(body.pickupAddress, 5, 300, 'Địa chỉ nhận'),
    ...coordinates(body.latitude, body.longitude), totalUnits, unitPrice, originalUnitPrice, closesAt, pickupStartAt, pickupEndAt,
    minPeople, minUnits, maxUnitsPerClaim: number(body.maxUnitsPerClaim ?? totalUnits, 1, totalUnits, 'Giới hạn mỗi khách', true),
    planKey: body.type === 'SUBSCRIPTION' ? string(body.planKey, 2, 64, 'Mã gói định kỳ') : '', components};
}
function assertBatch(batch, product, sellerId, now, pickupEndAt) {
  if (!batch || !product || product.sellerId !== sellerId || product.status !== 'ACTIVE'
      || !['AVAILABLE', 'RESERVED_PARTIAL'].includes(batch.status) || text(batch.activeCampaignId)
      || time(batch.harvestDate) > now || time(batch.expiryDate) <= pickupEndAt)
    throw fail(409, 'Lô phải còn bán được, thuộc cửa hàng, chưa gắn chiến dịch và còn hạn sau giờ nhận.');
  for (const key of ['initialQuantity', 'availableQuantity', 'reservedQuantity', 'soldQuantity'])
    if (!Number.isFinite(batch[key]) || batch[key] < 0) throw fail(409, 'Tồn kho chưa nhất quán.');
  if (Math.abs(batch.initialQuantity - batch.availableQuantity - batch.reservedQuantity - batch.soldQuantity) > 1e-6)
    throw fail(409, 'Tồn kho chưa nhất quán.');
}
function groupReady(offer) {
  return offer.type !== 'GROUP_BUY' || offer.groupSucceeded === true
    || offer.participantCount >= offer.minPeople && offer.claimedUnits >= offer.minUnits;
}
function claimState(offer, claim) {
  if (['COMPLETED', 'CANCELLED'].includes(claim.status)) return claim.status;
  if (['CANCELLED', 'FAILED', 'EXPIRED'].includes(offer.status)) return 'CANCELLED';
  if (!groupReady(offer)) return 'PLEDGED';
  return claim.status;
}
module.exports = {TYPES, DAY, fail, text, id, time, round, number, string, coordinates, distanceKm,
  validateOffer, assertBatch, groupReady, claimState};
