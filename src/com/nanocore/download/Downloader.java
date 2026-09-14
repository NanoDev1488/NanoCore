package com.nanocore.download;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

public class Downloader {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private static final String UA = "NanoCore/1.0 (https://github.com/NanoDev1488/NanoCore)";

    public static void download(String url, Path dest) throws IOException, InterruptedException {
        dest.getParent().toFile().mkdirs();
        System.out.print("  Downloading " + dest.getFileName() + " ... ");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).GET().build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        long total = resp.headers().firstValueAsLong("content-length").orElse(-1);
        long written = 0;
        try (InputStream in = resp.body();
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(dest))) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n); written += n;
                if (total > 0)
                    System.out.print("\r  Downloading " + dest.getFileName()
                        + " ... " + (written * 100 / total) + "%   ");
            }
        }
        System.out.println("\r  \u2705 " + dest.getFileName() + " (" + (written / 1024 / 1024) + " MB)       ");
    }

    public static String fetch(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        return resp.body();
    }

    public static String jsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle); if (i < 0) return null;
        i += needle.length(); int j = json.indexOf("\"", i);
        return j < 0 ? null : json.substring(i, j);
    }

    public static String jsonLastArrayString(String json, String key) {
        String needle = "\"" + key + "\":[";
        int i = json.indexOf(needle); if (i < 0) return null;
        int end = json.indexOf("]", i); if (end < 0) return null;
        String arr = json.substring(i + needle.length(), end);
        String[] parts = arr.split(",");
        return parts[parts.length - 1].trim().replaceAll("\"", "");
    }
}
