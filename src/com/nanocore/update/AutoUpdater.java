package com.nanocore.update;

import com.nanocore.NanoCoreMain;
import com.nanocore.download.Downloader;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class AutoUpdater {

    private static final String API = "https://api.github.com/repos/NanoDev1488/NanoCore/releases/latest";

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nanocore-updater"); t.setDaemon(true); return t;
        });

    public void startPeriodicCheck(int hours) {
        scheduler.scheduleAtFixedRate(this::checkAndUpdate, 0, hours, TimeUnit.HOURS);
    }

    public void checkAndUpdate() {
        System.out.println("[update] Checking for updates...");
        try {
            String json      = Downloader.fetch(API);
            String latestTag = Downloader.jsonString(json, "tag_name");
            if (latestTag == null) { System.out.println("[update] Could not determine latest version."); return; }
            String latest  = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
            String current = NanoCoreMain.VERSION;
            System.out.println("[update] Current: v" + current + " | Latest: " + latestTag);
            if (!isNewer(latest, current)) { System.out.println("[update] ✅ Already up to date."); return; }
            System.out.println("[update] ⬆ New version available — downloading...");
            downloadUpdate(json);
        } catch (Exception e) {
            System.out.println("[update] Check failed: " + e.getMessage());
        }
    }

    private void downloadUpdate(String json) throws IOException, InterruptedException {
        String assetUrl = null;
        String[] parts = json.split("\"browser_download_url\":");
        for (int i = 1; i < parts.length; i++) {
            String url = Downloader.jsonString("{\"u\":" + parts[i], "u");
            if (url != null && url.endsWith("NanoCore.jar") && !url.contains("bundle")) {
                assetUrl = url; break;
            }
        }
        if (assetUrl == null) { System.out.println("[update] NanoCore.jar not found in release."); return; }

        Path jar = NanoCoreMain.getRunningJar();
        if (jar == null) { System.out.println("[update] Cannot determine running jar."); return; }

        Path newJar = jar.resolveSibling("NanoCore-update.jar");
        Downloader.download(assetUrl, newJar);

        Path script = jar.resolveSibling("nanocore-update.sh");
        Files.writeString(script,
            "#!/bin/bash\necho 'Applying NanoCore update...'\nsleep 2\n" +
            "mv \"" + newJar.toAbsolutePath() + "\" \"" + jar.toAbsolutePath() + "\"\n" +
            "echo 'Done. Starting NanoCore...'\n" +
            "java -jar \"" + jar.toAbsolutePath() + "\" &\nrm -- \"$0\"\n");
        script.toFile().setExecutable(true);
        System.out.println("[update] ✅ Update ready. Run: bash nanocore-update.sh");
    }

    private boolean isNewer(String candidate, String current) {
        try {
            int[] c = parse(candidate), r = parse(current);
            for (int i = 0; i < 3; i++) { if (c[i] > r[i]) return true; if (c[i] < r[i]) return false; }
        } catch (Exception ignored) {}
        return false;
    }

    private int[] parse(String v) {
        String[] p = v.split("\\."); int[] r = {0,0,0};
        for (int i = 0; i < Math.min(p.length,3); i++) r[i] = Integer.parseInt(p[i].replaceAll("[^0-9]",""));
        return r;
    }
}
