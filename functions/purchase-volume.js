// Thresholds are VND after existing product promotions; physical units are never added together.
const POLICY = Object.freeze({version: 1, seller: [{minimum: 200000, percent: 3}, {minimum: 500000, percent: 5}],
  platform: [{minimum: 500000, percent: 2}, {minimum: 1000000, percent: 4}]});
function tier(amount, tiers) { return tiers.filter(row => amount >= row.minimum).at(-1)?.percent || 0; }
function allocate(total, weights) {
  const sum = weights.reduce((a, b) => a + b, 0);
  if (!sum || !total) return weights.map(() => 0);
  const exact = weights.map(value => total * value / sum), values = exact.map(Math.floor);
  const indices = weights.map((_, i) => i).sort((a, b) => (exact[b] - values[b]) - (exact[a] - values[a]) || a - b);
  let left = total - values.reduce((a, b) => a + b, 0);
  for (const i of indices) if (left-- > 0) values[i]++;
  return values;
}
function applyVolume(groups) {
  const rows = groups.map(group => {
    const subtotal = group.subtotal, sellerPercent = tier(subtotal, POLICY.seller);
    const sellerDiscount = Math.round(subtotal * sellerPercent / 100);
    return {...group, sellerPercent, sellerDiscount, eligibleSubtotal: subtotal - sellerDiscount};
  });
  const eligible = rows.reduce((sum, row) => sum + row.eligibleSubtotal, 0);
  const sellerCount = new Set(rows.filter(row => row.eligibleSubtotal > 0).map(row => row.sellerId)).size;
  const platformPercent = sellerCount >= 2 ? tier(eligible, POLICY.platform) : 0;
  const platformDiscount = Math.round(eligible * platformPercent / 100);
  const allocations = allocate(platformDiscount, rows.map(row => row.eligibleSubtotal));
  return {policy: POLICY, sellerCount, eligibleSubtotal: eligible, platformPercent, platformDiscount,
    groups: rows.map((row, i) => ({...row, platformPercent, platformDiscount: allocations[i],
      quantityDiscount: row.sellerDiscount + allocations[i], netSubtotal: row.eligibleSubtotal - allocations[i]}))};
}
module.exports = {POLICY, allocate, applyVolume};
