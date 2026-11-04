package com.skb.aiplayer;

import android.graphics.Bitmap;
import android.util.Base64;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One-shot screenshot analysis. No autonomous control or background uploads. */
public final class GeminiVision {
    public interface Callback { void done(String result); }
    private GeminiVision() {}
    public static void analyze(WebView web, String key, Callback callback) {
        if (key == null || key.trim().isEmpty()) { callback.done("Enter a Gemini API key first."); return; }
        if (web.getWidth() < 1 || web.getHeight() < 1) { callback.done("WebView is not visible."); return; }
        // WebView.draw may omit hardware-protected streaming video frames.
        int w = Math.min(960, web.getWidth());
        int h = Math.max(1, Math.round(web.getHeight() * (w / (float) web.getWidth())));
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.scale(w / (float)web.getWidth(), h / (float)web.getHeight());
        web.draw(canvas);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 65, bytes);
        bitmap.recycle();
        String encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
        new Thread(() -> {
            HttpURLConnection connection = null;
            String message;
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
                connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("x-goog-api-key", key);
                JSONObject image = new JSONObject().put("inline_data",
                    new JSONObject().put("mime_type","image/jpeg").put("data",encoded));
                JSONObject prompt = new JSONObject().put("text",
                    "Describe only what is actually visible in this game screenshot. Identify player, menu, controls, obstacles and objective if visible. Say when video content is blank or unavailable. Do not claim to have controlled the game. Keep under 150 words.");
                JSONArray parts = new JSONArray().put(prompt).put(image);
                JSONObject body = new JSONObject().put("contents",
                    new JSONArray().put(new JSONObject().put("parts",parts)));
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) { out.write(payload); }
                int status = connection.getResponseCode();
                InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                if (stream != null) {
                    try (InputStream in = stream) {
                        byte[] buf = new byte[4096]; int n;
                        while ((n = in.read(buf)) != -1 && response.size() < 100000) response.write(buf,0,n);
                    }
                }
                JSONObject json = new JSONObject(response.toString("UTF-8"));
                if (status >= 400) {
                    String detail = json.optJSONObject("error") == null ? "HTTP " + status
                        : json.getJSONObject("error").optString("message","HTTP " + status);
                    message = "Gemini error: " + detail;
                } else {
                    JSONArray candidates = json.optJSONArray("candidates");
                    JSONObject responseContent = candidates != null && candidates.length()>0
                        ? candidates.getJSONObject(0).optJSONObject("content") : null;
                    JSONArray output = responseContent != null ? responseContent.optJSONArray("parts") : null;
                    StringBuilder answer = new StringBuilder();
                    if (output != null) for (int i=0;i<output.length();i++)
                        answer.append(output.getJSONObject(i).optString("text"));
                    message = answer.length()>0 ? answer.toString() : "Gemini returned no text.";
                }
            } catch (Exception e) {
                message = "Analysis failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally { if (connection != null) connection.disconnect(); }
            final String result = message;
            web.post(() -> callback.done(result));
        }).start();
    }
}
