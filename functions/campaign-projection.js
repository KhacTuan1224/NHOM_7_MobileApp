// Read-time projection: identical day thresholds and remaining quantity contract to Android.
const millis = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;
function projectCampaign(campaign, batches, now) {
  const remaining = Math.max(0, (campaign.targetQuantity || 0) - (campaign.reservedQuantity || 0) - (campaign.rescuedQuantity || 0));
  const end = millis(campaign.endDate), start = millis(campaign.startDate);
  const active = campaign.status === 'ACTIVE' && end > now && start <= now && remaining > 0;
  let earliest = Infinity;
  for (const batch of batches || []) if (Object.hasOwn(campaign.batchTargets || {}, batch.id)
      && ['AVAILABLE', 'RESERVED_PARTIAL'].includes(batch.status) && batch.availableQuantity > .000001 && millis(batch.expiryDate) > now)
    earliest = Math.min(earliest, millis(batch.expiryDate));
  const batchDays = Number.isFinite(earliest) ? Math.trunc((earliest - now) / 86400000) : Infinity;
  const campaignDays = Math.trunc((end - now) / 86400000);
  const ratio = campaign.targetQuantity > 0 ? remaining / campaign.targetQuantity : 0;
  const urgencyLevel = batchDays <= 2 || (campaignDays <= 1 && ratio > .30) ? 'CRITICAL'
    : batchDays <= 5 || campaignDays <= 3 ? 'HIGH' : 'NORMAL';
  return {...campaign, active, urgencyLevel, remainingQuantity: remaining};
}
module.exports = {projectCampaign};
