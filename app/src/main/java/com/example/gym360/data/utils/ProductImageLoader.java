package com.example.gym360.data.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mini-loader sin dependencias. Luego deberíamos usar Coil o Glide. */
public final class ProductImageLoader {
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };

    private ProductImageLoader() {
    }

    public static void show(ImageView view, String url) {
        view.setTag(url);
        view.setImageResource(android.R.drawable.ic_menu_gallery);
        if (url == null || !url.startsWith("https://"))
            return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        IO.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setInstanceFollowRedirects(false);
                if (conn.getResponseCode() != 200)
                    return;
                ByteArrayOutputStream sink = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        if (sink.size() + n > 3 * 1024 * 1024)
                            return;
                        sink.write(chunk, 0, n);
                    }
                }
                byte[] data = sink.toByteArray();
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                int max = Math.max(opts.outWidth, opts.outHeight), sample = 1;
                while (max / sample > 900)
                    sample *= 2;
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                if (bitmap != null) {
                    CACHE.put(url, bitmap);
                    MAIN.post(() -> {
                        if (url.equals(view.getTag()))
                            view.setImageBitmap(bitmap);
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        });
    }
}
