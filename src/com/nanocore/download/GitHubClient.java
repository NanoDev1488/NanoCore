package com.nanocore.download;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.*;

/**
 * Клиент для GitHub Releases репозитория NanoDev1488/NanoCore.
 *
 * Логика скачивания:
 *   1. Сначала пробуем скачать уже патченный jar с GitHub Releases
 *      (GitHub Actions автоматически собирает патченные версии)
 *   2. Если не вышло (нет релиза, нет сети до GitHub) — скачиваем
 *      оригинал с PaperMC и патчим локально.
 */
public class GitHubClient {

    private static final String REPO  = "NanoDev1488/NanoCore";
    private static final String API    = "https://api.github.com/repos/" + REPO;
    private static final String RAW    = "https://github.com/" + REPO + "/releases/download";

    /**
     * Пробует скачать последний патченный jar для указанного сервера.
     * @param assetPrefix  например "velocity-patched" или "paper-1.21.4-patched"
     * @param dest         куда сохранить
     * @return true если скачалось с GitHub
     */
    public static boolean tryDownloadPatched(String assetPrefix, Path dest)
            throws InterruptedException {
        try {
            System.out.println("  [github] Ищу патченный jar: " + assetPrefix + " ...");
            String releasesJson = Downloader.fetch(API + "/releases/latest");

            // Ищем нужный asset в JSON
            Pattern p = Pattern.compile(
                "\"browser_download_url\":\\s*\"([^\"]*" +
                Pattern.quote(assetPrefix) + "[^\"]*\\.jar)\"");
            Matcher m = p.matcher(releasesJson);

            if (!m.find()) {
                System.out.println("  [github] Патченный jar не найден в последнем релизе.");
                return false;
            }

            String url = m.group(1);
            System.out.println("  [github] Найден: " + url);
            Downloader.download(url, dest);
            System.out.println("  ✅ Патченный jar скачан с GitHub Releases.");
            return true;

        } catch (IOException e) {
            System.out.println("  [github] Недоступен: " + e.getMessage());
            return false;
        }
    }

    /**
     * Возвращает список тегов релизов (для диагностики).
     */
    public static void printLatestRelease() {
        try {
            String json = Downloader.fetch(API + "/releases/latest");
            String tag  = Downloader.jsonString(json, "tag_name");
            String name = Downloader.jsonString(json, "name");
            System.out.println("  Последний релиз: " + tag + " — " + name);
            System.out.println("  https://github.com/" + REPO + "/releases/tag/" + tag);
        } catch (Exception e) {
            System.out.println("  [github] Не удалось получить релизы: " + e.getMessage());
        }
    }
}
