const {merchants} = require('../sepay');
try {
  const entries = Object.entries(merchants());
  if (!entries.length) { console.error('Chưa bật seller nào trong SEPAY_MERCHANTS_JSON.'); process.exitCode = 1; }
  else for (const [id, config] of entries)
    console.log(`${id}: bank ${config.bankBin}, account ending ${config.accountNumber.slice(-4)}, auth ${config.authMode}`);
} catch (error) { console.error(error.message); process.exitCode = 1; }
