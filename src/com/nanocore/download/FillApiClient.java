package com.nanocore.download;

import com.nanocore.patch.JarPatcher;

import java.io.IOException;
import java.nio.file.*;

public class FillApiClient {

    private static final String FILL   = "https://fill.papermc.io/v3/projects";
    private static final String BUNGEE = "https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar";

    public static Path downloadVelocity(Path destDir) throws IOException, InterruptedException {
        System.out.println("[download] Velocity: fetching latest version...");
        Path patchedDest = destDir.resolve("velocity-patched.jar");
        if (GitHubClient.tryDownloadPatched("velocity-patched", patchedDest)) return patchedDest;
        String meta = Downloader.fetch(FILL + "/velocity");
        String version = Downloader.jsonLastArrayString(meta, "versions");
        if (version == null) throw new IOException("Could not determine Velocity version");
        Path original = downloadFillStable("velocity", version, destDir, "velocity-original.jar");
        JarPatcher.patchVelocity(original, patchedDest);
        Files.deleteIfExists(original);
        return patchedDest;
    }

    public static Path downloadPaper(String mcVersion, Path destDir) throws IOException, InterruptedException {
        System.out.println("[download] Paper " + mcVersion + "...");
        Path patchedDest = destDir.resolve("paper-" + mcVersion + "-patched.jar");
        if (GitHubClient.tryDownloadPatched("paper-" + mcVersion + "-patched", patchedDest)) return patchedDest;
        Path original = downloadFillStable("paper", mcVersion, destDir, "paper-" + mcVersion + "-original.jar");
        JarPatcher.patchPaper(mcVersion, original, patchedDest);
        Files.deleteIfExists(original);
        return patchedDest;
    }

    public static Path downloadBungeeCord(Path destDir) throws IOException, InterruptedException {
        System.out.println("[download] BungeeCord...");
        Path patchedDest = destDir.resolve("BungeeCord-patched.jar");
        if (Files.exists(patchedDest) && JarPatcher.isAlreadyPatched(patchedDest)) {
            System.out.println("  Already patched."); return patchedDest;
        }
        if (GitHubClient.tryDownloadPatched("BungeeCord-patched", patchedDest)) return patchedDest;
        Path original = destDir.resolve("BungeeCord-original.jar");
        if (!Files.exists(original)) { destDir.toFile().mkdirs(); Downloader.download(BUNGEE, original); }
        JarPatcher.patchBungeeCord(original, patchedDest);
        Files.deleteIfExists(original);
        return patchedDest;
    }

    private static Path downloadFillStable(String project, String version, Path destDir, String destName)
            throws IOException, InterruptedException {
        Path dest = destDir.resolve(destName);
        if (Files.exists(dest)) { System.out.println("  Already downloaded: " + destName); return dest; }
        String buildsJson = Downloader.fetch(FILL + "/" + project + "/versions/" + version + "/builds");
        String url = null;
        String[] blocks = buildsJson.split("\\},\\{");
        for (int i = blocks.length - 1; i >= 0; i--) {
            if (!"STABLE".equalsIgnoreCase(Downloader.jsonString(blocks[i], "channel"))) continue;
            url = Downloader.jsonString(blocks[i], "url"); if (url != null) break;
        }
        if (url == null) for (int i = blocks.length - 1; i >= 0; i--) {
            url = Downloader.jsonString(blocks[i], "url"); if (url != null) break;
        }
        if (url == null) throw new IOException("No download URL for " + project + " " + version);
        destDir.toFile().mkdirs();
        Downloader.download(url, dest);
        return dest;
    }
}
