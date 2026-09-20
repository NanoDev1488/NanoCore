package com.nanocore.update;

import com.nanocore.NanoCoreMain;
import com.nanocore.download.Downloader;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * AutoUpdater — проверяет GitHub Releases и обновляет NanoCore.jar.
 *
 * Процесс:
 *   1. GET https://api.github.com/repos/NanoDev1488/NanoCore/releases/latest
 *   2. Сравниваем tag_name с NanoCoreMain.VERSION
 *   3. Если версия новее — скачиваем NanoCore.jar из assets
 *   4. Сохраняем как NanoCore-new.jar рядом с текущим
 *   5. При следующем запуске (или сразу) заменяем текущий jar
 */
public class AutoUpdater {

    private static final String REPO    = "NanoDev1488/NanoCore";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String UA      = "NanoCore/" + NanoCoreMain.VERSION;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nanocore-updater");
            t.setDaemon(true);
            return t;
        });

    public void startPeriodicCheck(int intervalHours) {
        scheduler.scheduleAtFixedRate(this::checkAndUpdate,
            0, intervalHours, TimeUnit.HOURS);
    }

    public void checkAndUpdate() {
        System.out.println("[update] Проверяю обновления...");
        try {
            String json       = Downloader.fetch(API_URL);
            String latestTag  = Downloader.jsonString(json, "tag_name");
            if (latestTag == null) { System.out.println("[update] Не удалось определить версию."); return; }

            String latestVer  = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
            String currentVer = NanoCoreMain.VERSION;

            System.out.println("[update] Текущая: v" + currentVer + " | Последняя: " + latestTag);

            if (!isNewer(latestVer, currentVer)) {
                System.out.println("[update] ✅ Установлена последняя версия.");
                return;
            }

            System.out.println("[update] ⬆ Доступно обновление " + latestTag + " — скачиваю...");
            downloadUpdate(json);

        } catch (Exception e) {
            System.out.println("[update] Ошибка проверки: " + e.getMessage());
        }
    }

    private void downloadUpdate(String releasesJson) throws IOException, InterruptedException {
        // Ищем NanoCore.jar в assets
        String assetUrl = null;
        String[] parts = releasesJson.split("\"browser_download_url\":");
        for (int i = 1; i < parts.length; i++) {
            String url = Downloader.jsonString("{\"u\":" + parts[i], "u");
            if (url != null && url.endsWith("NanoCore.jar") && !url.contains("bundle")) {
                assetUrl = url; break;
            }
        }
        if (assetUrl == null) { System.out.println("[update] NanoCore.jar не найден в релизе."); return; }

        Path runningJar = NanoCoreMain.getRunningJar();
        if (runningJar == null) { System.out.println("[update] Не определить текущий jar."); return; }

        Path newJar = runningJar.resolveSibling("NanoCore-update.jar");
        Downloader.download(assetUrl, newJar);

        // Создаём скрипт замены который выполнится после остановки JVM
        writeRestartScript(runningJar, newJar);

        System.out.println("[update] ✅ Обновление скачано: " + newJar.getFileName());
        System.out.println("[update] Перезапусти NanoCore чтобы применить обновление.");
        System.out.println("[update] Или запусти: bash nanocore-update.sh");
    }

    /** Пишет shell-скрипт для атомарной замены jar после остановки */
    private void writeRestartScript(Path current, Path newJar) throws IOException {
        Path script = current.resolveSibling("nanocore-update.sh");
        Files.writeString(script,
            "#!/bin/bash\n" +
            "echo 'Применяю обновление NanoCore...'\n" +
            "sleep 2\n" +
            "mv \"" + newJar.toAbsolutePath() + "\" \"" + current.toAbsolutePath() + "\"\n" +
            "echo 'Обновление применено. Запускаю NanoCore...'\n" +
            "java -jar \"" + current.toAbsolutePath() + "\" &\n" +
            "rm -- \"$0\"\n"
        );
        script.toFile().setExecutable(true);
        System.out.println("[update] Скрипт замены: " + script.getFileName());
    }

    /** Сравнивает семантические версии X.Y.Z */
    private boolean isNewer(String candidate, String current) {
        try {
            int[] c = parse(candidate), r = parse(current);
            for (int i = 0; i < 3; i++) {
                if (c[i] > r[i]) return true;
                if (c[i] < r[i]) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private int[] parse(String ver) {
        String[] p = ver.split("\\.");
        int[] r = {0,0,0};
        for (int i = 0; i < Math.min(p.length, 3); i++)
            r[i] = Integer.parseInt(p[i].replaceAll("[^0-9]",""));
        return r;
    }
}
