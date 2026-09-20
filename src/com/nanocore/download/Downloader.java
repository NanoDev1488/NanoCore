package com.nanocore.download;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/** Лёгкий HTTP-клиент на java.net.http — без внешних библиотек. */
public class Downloader {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private static final String UA = "NanoCore/1.0 (https://github.com/nanocore-project)";

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Скачивает URL в файл, показывает прогресс. */
    public static void download(String url, Path dest) throws IOException, InterruptedException {
        dest.getParent().toFile().mkdirs();
        System.out.print("  Скачиваю " + dest.getFileName() + " ... ");

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", UA)
            .GET().build();

        HttpResponse<InputStream> resp =
            HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() != 200)
            throw new IOException("HTTP " + resp.statusCode() + " для " + url);

        long total = resp.headers().firstValueAsLong("content-length").orElse(-1);
        long written = 0;

        try (InputStream in = resp.body();
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(dest))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                written += n;
                if (total > 0) {
                    int pct = (int)(written * 100 / total);
                    System.out.print("\r  Скачиваю " + dest.getFileName()
                        + " ... " + pct + "% (" + (written/1024/1024) + " МБ)    ");
                }
            }
        }
        System.out.println("\r  ✅ " + dest.getFileName()
            + " (" + (written / 1024 / 1024) + " МБ)                  ");
    }

    /** Скачивает URL и возвращает тело как строку (для API-запросов). */
    public static String fetch(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", UA)
            .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("HTTP " + resp.statusCode() + " для " + url);
        return resp.body();
    }

    // ------------------------------------------------------------------
    // Tiny JSON extractor (без внешних зависимостей)
    // ------------------------------------------------------------------

    /** Извлекает строковое значение поля из JSON: "key":"value" */
    public static String jsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i += needle.length();
        int j = json.indexOf("\"", i);
        return j < 0 ? null : json.substring(i, j);
    }

    /** Извлекает числовое значение поля: "key":123 */
    public static String jsonNumber(String json, String key) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i += needle.length();
        int j = i;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '.')) j++;
        return json.substring(i, j);
    }

    /** Последний элемент JSON-массива строк: [...,"last"] */
    public static String jsonLastArrayString(String json, String key) {
        String needle = "\"" + key + "\":[";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int end = json.indexOf("]", i);
        if (end < 0) return null;
        String arr = json.substring(i + needle.length(), end);
        // Берём последнее "..." в массиве
        String[] parts = arr.split(",");
        String last = parts[parts.length - 1].trim();
        return last.replaceAll("\"", "");
    }
}
