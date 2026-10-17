package com.rescuefarm.service.media;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rescuefarm.BuildConfig;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cloudinary signed uploads; the API secret is held only by the signing Worker. */
public final class CloudinaryMediaStorage implements MediaStorage {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private final ContentResolver resolver;

    public CloudinaryMediaStorage(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
    }

    @Override public CompletableFuture<String> uploadImage(String localUri) {
        return uploadImage(localUri, "misc", "unassigned", 0);
    }

    @Override public CompletableFuture<String> uploadImage(String localUri, String folder) {
        return uploadImage(localUri, folder, "unassigned", 0);
    }

    public CompletableFuture<String> uploadImage(String localUri, String assetType, String resourceId, int slot) {
        return CompletableFuture.supplyAsync(() -> {
            requireConfiguration();
            HttpURLConnection connection = null;
            String boundary = "----RescueFarm" + UUID.randomUUID();
            try {
                Uri uri = Uri.parse(localUri);
                String mimeType = resolver.getType(uri);
                if (!isSupportedImage(mimeType)) throw new IllegalArgumentException("Chỉ hỗ trợ ảnh JPG, PNG, WebP hoặc GIF.");
                try (AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(uri, "r")) {
                    if (descriptor != null && descriptor.getLength() > MAX_IMAGE_BYTES)
                        throw new IllegalArgumentException("Ảnh phải nhỏ hơn 10 MB.");
                }
                if ("campaign".equals(assetType)) return uploadCampaign(uri, mimeType, resourceId, slot);
                JSONObject signed = requestWorker("/sign-upload", new JSONObject()
                        .put("assetType", assetType).put("resourceId", resourceId).put("slot", Math.max(0, slot)));
                connection = (HttpURLConnection) new URL("https://api.cloudinary.com/v1_1/"
                        + BuildConfig.CLOUDINARY_CLOUD_NAME.trim() + "/image/upload").openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(20000); connection.setReadTimeout(60000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = connection.getOutputStream()) {
                    writeField(out, boundary, "api_key", signed.getString("api_key"));
                    writeField(out, boundary, "asset_folder", signed.getString("asset_folder"));
                    writeField(out, boundary, "timestamp", signed.getString("timestamp"));
                    writeField(out, boundary, "public_id", signed.getString("public_id"));
                    writeField(out, boundary, "overwrite", signed.getString("overwrite"));
                    writeField(out, boundary, "invalidate", signed.getString("invalidate"));
                    writeField(out, boundary, "signature", signed.getString("signature"));
                    writeFile(out, boundary, uri, mimeType);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                String body = readAll(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
                JSONObject json = new JSONObject(body);
                if (status < 200 || status >= 300) {
                    JSONObject error = json.optJSONObject("error");
                    throw new IllegalStateException("Cloudinary từ chối ảnh (" + status + "): "
                            + (error == null ? body : error.optString("message", body)));
                }
                String secureUrl = json.optString("secure_url", "");
                String publicId = json.optString("public_id", "");
                if (secureUrl.isEmpty() || publicId.isEmpty()) throw new IllegalStateException("Cloudinary thiếu secure_url hoặc public_id.");
                CloudinaryAssetIds.rememberUpload(secureUrl, publicId);
                return secureUrl;
            } catch (Exception error) {
                if (error instanceof RuntimeException) throw (RuntimeException) error;
                throw new IllegalStateException("Không thể tải ảnh lên Cloudinary: " + error.getMessage(), error);
            } finally { if (connection != null) connection.disconnect(); }
        }, EXECUTOR);
    }

    private String uploadCampaign(Uri uri, String mimeType, String resourceId, int slot) throws Exception {
        if(slot<0||slot>=6) throw new IllegalArgumentException("Mỗi Campaign tối đa 6 vị trí ảnh.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=resolver.openInputStream(uri)) {
            if(input==null) throw new IllegalStateException("Không đọc được ảnh.");
            byte[] buffer=new byte[16384]; int size;
            while((size=input.read(buffer))!=-1) {if(bytes.size()+size>MAX_IMAGE_BYTES)throw new IllegalArgumentException("Ảnh vượt 10 MB.");bytes.write(buffer,0,size);}
        }
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser(); if(user==null)throw new IllegalStateException("Cần đăng nhập người bán.");
        String token=Tasks.await(user.getIdToken(false)).getToken();
        HttpURLConnection connection=(HttpURLConnection)new URL(BuildConfig.CLOUDINARY_SIGNING_URL.trim()+"/upload-campaign?resourceId="
                +Uri.encode(resourceId)+"&slot="+slot).openConnection();
        try {
            connection.setRequestMethod("POST");connection.setConnectTimeout(20000);connection.setReadTimeout(120000);connection.setDoOutput(true);
            connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Content-Type",mimeType);
            connection.setFixedLengthStreamingMode(bytes.size());try(OutputStream output=connection.getOutputStream()){bytes.writeTo(output);}
            int status=connection.getResponseCode();JSONObject result=new JSONObject(readAll(status>=200&&status<300?connection.getInputStream():connection.getErrorStream()));
            if(status<200||status>=300)throw new IllegalStateException(result.optString("error","Không tải được ảnh Campaign."));
            String url=result.getString("secure_url"),publicId=result.getString("public_id");CloudinaryAssetIds.rememberUpload(url,publicId);return url;
        }finally{connection.disconnect();}
    }

    @Override public CompletableFuture<Void> deleteImage(String remoteUrl) {
        return deleteImageFromUrl(remoteUrl);
    }

    public static CompletableFuture<Void> deleteImageFromUrl(String remoteUrl) {
        return CompletableFuture.runAsync(() -> {
            if (BuildConfig.CLOUDINARY_CLOUD_NAME.trim().isEmpty()
                    || BuildConfig.CLOUDINARY_SIGNING_URL.trim().isEmpty())
                throw new IllegalStateException("Thiếu CLOUDINARY_CLOUD_NAME hoặc CLOUDINARY_SIGNING_URL trong Gradle properties.");
            String publicId = CloudinaryAssetIds.fromSecureUrl(remoteUrl);
            if (publicId.isEmpty()) throw new IllegalArgumentException("URL không chứa Cloudinary public ID hợp lệ.");
            try {
                requestWorker("/delete", new JSONObject().put("assetType", "delete").put("publicId", publicId));
            } catch (Exception error) {
                if (error instanceof RuntimeException) throw (RuntimeException) error;
                throw new IllegalStateException("Không thể xóa ảnh Cloudinary: " + error.getMessage(), error);
            }
        }, EXECUTOR);
    }

    private static JSONObject requestWorker(String path, JSONObject payload) throws Exception {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new IllegalStateException("Vui lòng đăng nhập lại trước khi quản lý ảnh.");
        String token = Tasks.await(user.getIdToken(false)).getToken();
        HttpURLConnection connection = (HttpURLConnection) new URL(
                BuildConfig.CLOUDINARY_SIGNING_URL.trim() + path).openConnection();
        try {
            connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            String body = readAll(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
            JSONObject json = new JSONObject(body);
            if (status < 200 || status >= 300)
                throw new IllegalStateException(json.optString("error", "Signing service failed")
                        + (json.has("detail") ? ": " + json.opt("detail") : ""));
            return json;
        } finally { connection.disconnect(); }
    }

    private static void requireConfiguration() {
        if (BuildConfig.CLOUDINARY_CLOUD_NAME.trim().isEmpty()
                || BuildConfig.CLOUDINARY_SIGNING_URL.trim().isEmpty())
            throw new IllegalStateException("Thiếu CLOUDINARY_CLOUD_NAME hoặc CLOUDINARY_SIGNING_URL trong Gradle properties.");
    }
    private static void writeField(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    private void writeFile(OutputStream out, String boundary, Uri uri, String mimeType) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"image." + extension(mimeType)
                + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Không đọc được ảnh đã chọn.");
            byte[] buffer = new byte[16384]; int read; while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    private static String extension(String mime) { if ("image/png".equalsIgnoreCase(mime)) return "png"; if ("image/webp".equalsIgnoreCase(mime)) return "webp"; if ("image/gif".equalsIgnoreCase(mime)) return "gif"; return "jpg"; }
    private static boolean isSupportedImage(String mime) { return "image/jpeg".equalsIgnoreCase(mime) || "image/jpg".equalsIgnoreCase(mime) || "image/png".equalsIgnoreCase(mime) || "image/webp".equalsIgnoreCase(mime) || "image/gif".equalsIgnoreCase(mime); }
    private static String readAll(InputStream stream) throws Exception { if (stream == null) return "{}"; try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int count; while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count); return out.toString("UTF-8"); } }
}
