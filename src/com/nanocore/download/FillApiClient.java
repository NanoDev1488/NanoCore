package com.nanocore.download;

import com.nanocore.patch.JarPatcher;

import java.io.IOException;
import java.nio.file.*;

public class FillApiClient {

    private static final String FILL   = "https://fill.papermc.io/v3/projects";
    private static final String BUNGEE = "https://ci.md-5.net/job/BungeeCord/"
                                       + "lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar";

    // ------------------------------------------------------------------
    // Velocity
    // ------------------------------------------------------------------

    public static Path downloadVelocity(Path destDir) throws IOException, InterruptedException {
        System.out.println("[download] Velocity: получаю последнюю версию...");

        Path patchedDest = destDir.resolve("velocity-patched.jar");

        // 1. Пробуем скачать уже патченный с нашего GitHub
        if (GitHubClient.tryDownloadPatched("velocity-patched", patchedDest))
            return patchedDest;

        // 2. Скачиваем с PaperMC и патчим локально
        String meta    = Downloader.fetch(FILL + "/velocity");
        String version = Downloader.jsonLastArrayString(meta, "versions");
        if (version == null) throw new IOException("Не удалось определить версию Velocity");

        Path original = downloadFillStable("velocity", version, destDir, "velocity-original.jar");
        JarPatcher.patchVelocity(original, patchedDest);

        // Удаляем оригинал — нам нужен только патченный
        Files.deleteIfExists(original);
        return patchedDest;
    }

    // ------------------------------------------------------------------
    // Paper
    // ------------------------------------------------------------------

    public static Path downloadPaper(String mcVersion, Path destDir)
            throws IOException, InterruptedException {
        System.out.println("[download] Paper " + mcVersion + "...");

        Path patchedDest = destDir.resolve("paper-" + mcVersion + "-patched.jar");

        // 1. GitHub
        if (GitHubClient.tryDownloadPatched("paper-" + mcVersion + "-patched", patchedDest))
            return patchedDest;

        // 2. PaperMC + локальный патч
        Path original = downloadFillStable("paper", mcVersion, destDir,
                                           "paper-" + mcVersion + "-original.jar");
        JarPatcher.patchPaper(mcVersion, original, patchedDest);
        Files.deleteIfExists(original);
        return patchedDest;
    }

    // ------------------------------------------------------------------
    // BungeeCord
    // ------------------------------------------------------------------

    public static Path downloadBungeeCord(Path destDir) throws IOException, InterruptedException {
        System.out.println("[download] BungeeCord...");

        Path patchedDest = destDir.resolve("BungeeCord-patched.jar");

        if (Files.exists(patchedDest) && JarPatcher.isAlreadyPatched(patchedDest)) {
            System.out.println("  ⏭  BungeeCord уже патчен, пропускаю.");
            return patchedDest;
        }

        // 1. GitHub
        if (GitHubClient.tryDownloadPatched("BungeeCord-patched", patchedDest))
            return patchedDest;

        // 2. Jenkins + патч
        Path original = destDir.resolve("BungeeCord-original.jar");
        if (!Files.exists(original)) {
            destDir.toFile().mkdirs();
            Downloader.download(BUNGEE, original);
        }
        JarPatcher.patchBungeeCord(original, patchedDest);
        Files.deleteIfExists(original);
        return patchedDest;
    }

    // ------------------------------------------------------------------
    // Fill v3 helpers
    // ------------------------------------------------------------------

    private static Path downloadFillStable(String project, String version,
                                           Path destDir, String destName)
            throws IOException, InterruptedException {
        Path dest = destDir.resolve(destName);
        if (Files.exists(dest)) {
            System.out.println("  ⏭  " + destName + " уже скачан.");
            return dest;
        }

        String buildsJson = Downloader.fetch(
            FILL + "/" + project + "/versions/" + version + "/builds");

        String url = null;
        String[] blocks = buildsJson.split("\\},\\{");
        for (int i = blocks.length - 1; i >= 0; i--) {
            if (!"STABLE".equalsIgnoreCase(Downloader.jsonString(blocks[i], "channel"))) continue;
            url = Downloader.jsonString(blocks[i], "url");
            if (url != null) break;
        }
        if (url == null) {
            for (int i = blocks.length - 1; i >= 0; i--) {
                url = Downloader.jsonString(blocks[i], "url");
                if (url != null) break;
            }
        }
        if (url == null) throw new IOException("Нет ссылки для " + project + " " + version);

        destDir.toFile().mkdirs();
        Downloader.download(url, dest);
        return dest;
    }
}
