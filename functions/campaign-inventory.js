// Dashboard projection needs only batches referenced by active campaigns, not
// every historical batch or every product in the database.
async function campaignInventory(db, campaigns) {
  async function read(collection, ids) {
    const rows = [];
    for (let offset = 0; offset < ids.length; offset += 20) {
      rows.push(...await Promise.all(ids.slice(offset, offset + 20).map(id => db.collection(collection).doc(id).get())));
    }
    return rows.filter(row => row.exists);
  }
  const valid = id => typeof id === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(id);
  const ids = [...new Set(campaigns.flatMap(row => Object.keys(row.get('batchTargets') || {})))].filter(valid);
  const batches = await read('productBatches', ids);
  const productIds = [...new Set(batches.map(row => row.get('productId')))].filter(valid);
  const products = await read('products', productIds);
  const active = new Set(products.filter(row => row.get('status') === 'ACTIVE').map(row => row.id));
  return batches.filter(row => active.has(row.get('productId'))).map(row => ({...row.data(), id: row.id}));
}
module.exports = {campaignInventory};
