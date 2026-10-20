package com.stevenris.reader;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class BookStore {
    private final File root;
    BookStore(Context context) {
        root = new File(context.getFilesDir(), "books");
        root.mkdirs();
    }
    private File folder(int id) { return new File(root, Integer.toString(id)); }
    private File metaFile(int id) { return new File(folder(id), "meta.json"); }
    private File pageFile(int id, int page) { return new File(folder(id), page + ".txt"); }

    synchronized void saveMeta(int id, String title, int total) throws Exception {
        folder(id).mkdirs();
        JSONObject meta = new JSONObject();
        meta.put("id", id); meta.put("title", title); meta.put("total", total);
        write(metaFile(id), meta.toString());
    }
    synchronized JSONObject meta(int id) throws Exception {
        return new JSONObject(read(metaFile(id)));
    }
    synchronized List<JSONObject> books() {
        List<JSONObject> result = new ArrayList<>();
        File[] dirs = root.listFiles();
        if (dirs != null) for (File dir : dirs) {
            try { result.add(meta(Integer.parseInt(dir.getName()))); }
            catch (Exception ignored) { }
        }
        result.sort((a, b) -> a.optString("title").compareToIgnoreCase(b.optString("title")));
        return result;
    }
    synchronized boolean hasPage(int id, int page) {
        return pageFile(id, page).isFile() && pageFile(id, page).length() > 0;
    }
    synchronized int downloaded(int id, int total) {
        int result = 0;
        for (int p = 1; p <= total; p++) if (hasPage(id, p)) result++;
        return result;
    }
    synchronized void savePage(int id, int page, String text) throws Exception {
        folder(id).mkdirs();
        write(pageFile(id, page), text);
    }
    synchronized String page(int id, int page) throws Exception { return read(pageFile(id, page)); }
    synchronized void delete(int id) {
        File[] files = folder(id).listFiles();
        if (files != null) for (File file : files) file.delete();
        folder(id).delete();
    }
    private static String read(File file) throws IOException {
        try (InputStream stream = new AtomicFile(file).openRead();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                if (out.size() + n > 4 * 1024 * 1024) throw new IOException("Слишком большой файл");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static void write(File file, String text) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream stream = atomic.startWrite();
        try {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(stream);
        } catch (IOException e) {
            atomic.failWrite(stream);
            throw e;
        }
    }
}
