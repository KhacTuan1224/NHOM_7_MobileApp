const normalizeUnit = value => {
  const unit = String(value || '').normalize('NFC').trim().toLowerCase().replace(/\s+/g, ' ');
  return ['kilogram', 'kilograms', 'kilôgam', 'ký'].includes(unit) ? 'kg' : unit;
};
function campaignUnit(products) {
  const units = [...new Set(products.map(product => normalizeUnit(product.unit)))];
  if (units.length !== 1 || !units[0] || units[0].length > 32)
    throw Object.assign(new Error('Các lô trong Campaign phải có cùng một đơn vị tính; không tự quy đổi bó/bao thành kg.'), {status: 409});
  return units[0];
}
function kilograms(campaign) {
  return campaign.unitStatus === 'VERIFIED' && normalizeUnit(campaign.quantityUnit) === 'kg';
}
module.exports = {normalizeUnit, campaignUnit, kilograms};
