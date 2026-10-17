const responseHeaders = { "Content-Type": "application/json; charset=utf-8" };
let firebaseKeys = null;
let firebaseKeysExpiresAt = 0;

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Authorization, Content-Type",
      "Access-Control-Max-Age": "86400",
    };
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    try {
      if (request.method !== "POST") return json({ error: "Method not allowed" }, 405, cors);
      const url = new URL(request.url);
      if (url.pathname === "/internal/delete-campaign") {
        if (!constantSecret(request.headers.get("X-Media-Worker-Secret"), env.MEDIA_WORKER_SECRET)) return json({error:"Worker authentication required"},403,cors);
        const body=await request.json();
        if (!/^rescuefarm\/campaigns\/[A-Za-z0-9_-]+\/[A-Za-z0-9_-]+\/image-[0-5]-[a-f0-9-]{36}$/.test(body.publicId||"")) return json({error:"Invalid campaign asset"},400,cors);
        return json(await destroyCampaign(body.publicId,env),200,cors);
      }
      if (url.pathname !== "/sign-upload" && url.pathname !== "/delete" && url.pathname !== "/upload-campaign")
        return json({ error: "Not found" }, 404, cors);

      const auth = await authenticate(request, env);
      if (!auth) return json({ error: "Authentication required" }, 401, cors);
      const profile = await readOwnProfile(auth.uid, env, auth.token);
      if (!profile || profile.status !== "ACTIVE") return json({ error: "Active account required" }, 403, cors);
      if (url.pathname === "/upload-campaign") {
        if (profile.role !== "SELLER" || profile.sellerStatus !== "APPROVED") return json({error:"Approved Seller required"},403,cors);
        return json(await uploadCampaign(request,url,auth,env),200,cors);
      }
      const body = await request.json();
      if (body.assetType === "campaign") return json({error:"Campaign images must use the protected upload endpoint"},403,cors);
      const asset = await resolveAsset(body, auth.uid, profile);
      if (!asset) return json({ error: "Invalid asset or insufficient permission" }, 403, cors);
      if ((url.pathname === "/delete") !== (body.assetType === "delete")) return json({error:"Invalid asset action"},400,cors);
      // Legacy Campaigns may reference a Product/avatar image. Preserve that historical asset too.
      const guard=await backend("/media/asset-guard",{publicId:asset.publicId},auth,env,true);
      if (url.pathname === "/delete" && guard.protected) return json({error:"Ảnh đang được lưu trong lịch sử kiểm duyệt Campaign."},403,cors);
      if (url.pathname === "/sign-upload" && guard.protected) { asset.publicId += "-"+crypto.randomUUID(); asset.immutable=true; }

      if (url.pathname === "/sign-upload") {
        const timestamp = Math.floor(Date.now() / 1000);
        const params = {
          asset_folder: asset.assetFolder,
          invalidate: "true",
          overwrite: asset.immutable ? "false" : "true",
          public_id: asset.publicId,
          timestamp: String(timestamp),
        };
        const signature = await sign(params, env.CLOUDINARY_API_SECRET);
        return json({ ...params, signature, api_key: env.CLOUDINARY_API_KEY }, 200, cors);
      }

      const timestamp = Math.floor(Date.now() / 1000);
      const params = { invalidate: "true", public_id: asset.publicId, timestamp: String(timestamp) };
      const signature = await sign(params, env.CLOUDINARY_API_SECRET);
      const form = new URLSearchParams({ ...params, signature, api_key: env.CLOUDINARY_API_KEY });
      const cloudinary = await fetch(
        `https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/destroy`,
        { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: form },
      );
      const result = await cloudinary.json();
      if (!cloudinary.ok || result.result !== "ok" && result.result !== "not found")
        return json({ error: "Cloudinary delete failed", detail: result }, 502, cors);
      return json({ result: result.result, public_id: asset.publicId }, 200, cors);
    } catch (error) {
      return json({ error: error instanceof Error ? error.message : "Request failed" }, 400, cors);
    }
  },
};

async function authenticate(request, env) {
  const match = (request.headers.get("Authorization") || "").match(/^Bearer\s+(.+)$/i);
  if (!match) return null;
  const parts = match[1].split(".");
  if (parts.length !== 3) return null;
  try {
    const header = JSON.parse(decodeBase64Url(parts[0]));
    const claims = JSON.parse(decodeBase64Url(parts[1]));
    if (header.alg !== "RS256" || !header.kid || claims.aud !== env.FIREBASE_PROJECT_ID
        || claims.iss !== `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`
        || !claims.sub || typeof claims.exp !== "number" || claims.exp <= Date.now() / 1000
        || typeof claims.iat !== "number" || claims.iat > Date.now() / 1000 + 60) return null;
    const key = await firebaseSigningKey(header.kid);
    if (!key) {
      firebaseKeys = null;
      const refreshedKey = await firebaseSigningKey(header.kid);
      if (!refreshedKey) return null;
      return await verifyWithKey(refreshedKey, parts, claims, match[1]);
    }
    return await verifyWithKey(key, parts, claims, match[1]);
  } catch (_) { return null; }
}

async function verifyWithKey(key, parts, claims, token) {
  try {
    const publicKey = await crypto.subtle.importKey("jwk", key, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey,
      decodeBase64UrlBytes(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
    if (!valid) return null;
    return { uid: claims.sub, token };
  } catch (_) { return null; }
}

async function firebaseSigningKey(kid) {
  if (!firebaseKeys || Date.now() >= firebaseKeysExpiresAt) {
    const response = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
    if (!response.ok) return null;
    firebaseKeys = (await response.json()).keys || [];
    firebaseKeysExpiresAt = Date.now() + 60 * 60 * 1000;
  }
  return firebaseKeys.find((key) => key.kid === kid && key.kty === "RSA") || null;
}

function decodeBase64Url(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(normalized + "=".repeat((4 - normalized.length % 4) % 4));
  return new TextDecoder().decode(Uint8Array.from(binary, (character) => character.charCodeAt(0)));
}
function decodeBase64UrlBytes(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(normalized + "=".repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

async function resolveAsset(body, uid, profile) {
  const type = String(body.assetType || "");
  if (type === "delete") {
    const publicId = String(body.publicId || "");
    const userPrefixes = [
      `rescuefarm/users/${uid}/`, `rescuefarm/sellers/${uid}/`,
      `rescuefarm/products/${uid}/`, `rescuefarm/posts/${uid}/`, `rescuefarm/reviews/${uid}/`,
    ];
    if (userPrefixes.some((prefix) => publicId.startsWith(prefix)) && /^[A-Za-z0-9_/-]+$/.test(publicId))
      return { publicId };
    if (/^rescuefarm\/(categories|banners)\/[A-Za-z0-9_-]+\/image(?:-[a-f0-9-]{36})?$/.test(publicId)) {
      if (profile.role === "ADMIN") return { publicId };
    }
    return null;
  }
  const resourceId = segment(body.resourceId);
  const slot = Number.isInteger(Number(body.slot)) && Number(body.slot) >= 0 ? Number(body.slot) : null;
  if (!resourceId || slot === null) return null;

  if (type === "avatar" && resourceId === uid)
    return { publicId: `rescuefarm/users/${uid}/avatar`, assetFolder: `rescuefarm_assets/avatars/${uid}` };
  if (type === "shopAvatar" && resourceId === uid && profile.role === "SELLER")
    return { publicId: `rescuefarm/sellers/${uid}/shop-avatar`, assetFolder: `rescuefarm_assets/avatars/${uid}` };
  if (type === "paymentQr" && resourceId === uid && profile.role === "SELLER") {
    if (body.version !== undefined && !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(body.version)) return null;
    return { publicId: `rescuefarm/sellers/${uid}/payment-qr${body.version ? '-' + body.version : ''}`,
      assetFolder: `rescuefarm_assets/payment_qr/${uid}`, immutable: Boolean(body.version) };
  }
  if (type === "sellerProof" && resourceId === uid)
    return { publicId: `rescuefarm/sellers/${uid}/application-proof`, assetFolder: `rescuefarm_assets/seller_applications/${uid}` };
  if (type === "product" && profile.role === "SELLER" && profile.sellerStatus === "APPROVED")
    return { publicId: `rescuefarm/products/${uid}/${resourceId}/image-${slot}`, assetFolder: `rescuefarm_assets/products/${uid}/${resourceId}` };
  if (type === "post" && profile.role === "SELLER" && profile.sellerStatus === "APPROVED")
    return { publicId: `rescuefarm/posts/${uid}/${resourceId}/image-${slot}`, assetFolder: `rescuefarm_assets/posts/${uid}/${resourceId}` };
  if (type === "campaign") return null;
  if (type === "review" && profile.role === "CUSTOMER")
    return { publicId: `rescuefarm/reviews/${uid}/${resourceId}/image-${slot}`, assetFolder: `rescuefarm_assets/reviews/${uid}/${resourceId}` };

  if (type === "category" || type === "banner") {
    if (profile.role !== "ADMIN") return null;
    const collection = type === "category" ? "categories" : "banners";
    return { publicId: `rescuefarm/${collection}/${resourceId}/image`, assetFolder: `rescuefarm_assets/${collection}/${resourceId}` };
  }

  return null;
}

async function readOwnProfile(uid, env, token) {
  if (!token) return "";
  const endpoint = `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${uid}`;
  const response = await fetch(endpoint, { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) return null;
  const document = await response.json();
  const fields = document.fields || {};
  return {
    role: fields.role?.stringValue || "",
    status: fields.status?.stringValue || "",
    sellerStatus: fields.sellerStatus?.stringValue || "",
  };
}

function segment(value) {
  const text = String(value || "").trim();
  return /^[A-Za-z0-9_-]{1,128}$/.test(text) ? text : "";
}

async function sign(params, secret) {
  const canonical = Object.keys(params).sort().map((key) => `${key}=${params[key]}`).join("&") + secret;
  const bytes = new TextEncoder().encode(canonical);
  const digest = await crypto.subtle.digest("SHA-1", bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function json(value, status, cors) {
  return new Response(JSON.stringify(value), { status, headers: { ...responseHeaders, ...cors } });
}

// Campaign media is proxied, never signed for an untrusted client to replace its payload.
function constantSecret(actual,expected) {
  if(typeof expected!=="string"||expected.length<32||typeof actual!=="string"||actual.length!==expected.length) return false;
  let diff=0;for(let i=0;i<expected.length;i++)diff|=actual.charCodeAt(i)^expected.charCodeAt(i);return diff===0;
}
async function backend(path,body,auth,env,internal=false) {
  if(!/^https:\/\//.test(env.MEDIA_BACKEND_URL||"")||!env.MEDIA_WORKER_SECRET||env.MEDIA_WORKER_SECRET.length<32) throw Error("Campaign media backend is not configured");
  const response=await fetch(env.MEDIA_BACKEND_URL.replace(/\/$/,"")+path,{method:"POST",headers:{"Content-Type":"application/json",
    ...(auth?{Authorization:`Bearer ${auth.token}`} : {}),...(internal?{"X-Media-Worker-Secret":env.MEDIA_WORKER_SECRET}:{})},body:JSON.stringify(body)});
  const result=await response.json();if(!response.ok)throw Error(result.error||"Campaign media authorization failed");return result;
}
function imageMime(bytes) {
  if(bytes.length>=3&&bytes[0]===255&&bytes[1]===216&&bytes[2]===255)return "image/jpeg";
  if(bytes.length>=8&&[137,80,78,71,13,10,26,10].every((b,i)=>bytes[i]===b))return "image/png";
  const head=new TextDecoder().decode(bytes.slice(0,12));
  if(head.startsWith("GIF87a")||head.startsWith("GIF89a"))return "image/gif";
  if(head.startsWith("RIFF")&&head.slice(8,12)==="WEBP")return "image/webp";
  return "";
}
async function readCampaignImage(request) {
  const max=10*1024*1024;
  if(Number(request.headers.get("Content-Length"))>max)throw Error("Ảnh phải nhỏ hơn hoặc bằng 10 MB.");
  if(!request.body)throw Error("Thiếu ảnh tải lên.");
  const reader=request.body.getReader(),chunks=[];let size=0;
  try {while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>max){await reader.cancel();throw Error("Ảnh vượt 10 MB.");}chunks.push(value);}}
  finally{reader.releaseLock();}
  const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.byteLength;}
  const mime=imageMime(bytes),declared=(request.headers.get("Content-Type")||"").split(";")[0].toLowerCase().replace("image/jpg","image/jpeg");
  if(!mime||mime!==declared)throw Error("Chỉ hỗ trợ ảnh JPG, PNG, WebP hoặc GIF hợp lệ.");return {bytes,mime};
}
async function uploadCampaign(request,url,auth,env) {
  const resourceId=segment(url.searchParams.get("resourceId")),rawSlot=url.searchParams.get("slot");
  if(!resourceId||!rawSlot||!(/^[0-5]$/).test(rawSlot))throw Error("Campaign slot must be between 0 and 5");
  const {bytes,mime}=await readCampaignImage(request);
  const asset=await backend("/media/campaign/authorize",{resourceId,slot:Number(rawSlot)},auth,env);
  const params={asset_folder:`rescuefarm_assets/campaigns/${auth.uid}/${resourceId}`,public_id:asset.publicId,
    overwrite:"false",timestamp:String(Math.floor(Date.now()/1000))};
  const form=new FormData();for(const [key,value]of Object.entries(params))form.set(key,value);
  form.set("api_key",env.CLOUDINARY_API_KEY);form.set("signature",await sign(params,env.CLOUDINARY_API_SECRET));
  form.set("file",new Blob([bytes],{type:mime}),"campaign-image");
  const response=await fetch(`https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/upload`,{method:"POST",body:form});
  const result=await response.json();
  if(!response.ok||result.public_id!==asset.publicId||!result.secure_url)throw Error("Cloudinary từ chối ảnh Campaign.");
  await backend("/media/campaign/uploaded",{leaseId:asset.leaseId,publicId:asset.publicId,secureUrl:result.secure_url},null,env,true);
  return {secure_url:result.secure_url,public_id:asset.publicId};
}
async function destroyCampaign(publicId,env) {
  const params={invalidate:"true",public_id:publicId,timestamp:String(Math.floor(Date.now()/1000))};
  const form=new URLSearchParams({...params,signature:await sign(params,env.CLOUDINARY_API_SECRET),api_key:env.CLOUDINARY_API_KEY});
  const response=await fetch(`https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/destroy`,{method:"POST",body:form});
  const result=await response.json();if(!response.ok||!["ok","not found"].includes(result.result))throw Error("Campaign cleanup failed");return {result:result.result};
}
export {resolveAsset,imageMime,readCampaignImage,uploadCampaign,constantSecret};
