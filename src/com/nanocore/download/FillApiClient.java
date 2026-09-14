package com.nanocore.download;

import com.nanocore.patch.JarPatcher;

import java.io.IOException;
import java.nio.file.*;

public class FillApiClient {

    private static final String FILL   = "https://fill.papermc.io/v3/projects";
    private static final String BUNGEE = "https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar";

    // ------------------------------------------------------------------
    // Velocity
    // ------------------------------------------------------------------

    public static Path downloadVelocity(Path destDir) throws IOException, InterruptedException {
        System.out.println("[download] Velocity: fetching latest version...");
        Path patchedDest = destDir.resolve("velocity-patched.jar");
        if (Files.exists(patchedDest) && JarPatcher.isAlreadyPatched(patchedDest)) {
            System.out.println("  Already patched."); return patchedDest;
        }
        if (GitHubClient.tryDownloadPatched("velocity-patched", patchedDest)) return patchedDest;

        String meta = Downloader.fetch(FILL + "/velocity");
        // Fill v3 returns {"versions": {"4.x": ["4.1.1", "4.1.0", ...]}}
        // — nested object, NOT a plain array. We find the first array inside it.
        String version = firstVersionFromNestedMap(meta);
        if (version == null) throw new IOException("Could not determine Velocity version from: " + meta.substring(0, Math.min(200, meta.length())));

        System.out.println("  Latest Velocity version: " + version);
        Path original = downloadFillStable("velocity", version, destDir, "velocity-original.jar");
        JarPatcher.patchVelocity(original, patchedDest);
        Files.deleteIfExists(original);
        return patchedDest;
    }

    // ------------------------------------------------------------------
    // Paper
    // ------------------------------------------------------------------

    public static Path downloadPaper(String mcVersion, Path destDir) throws IOException, InterruptedException {
        System.out.println("[download] Paper " + mcVersion + "...");
        // Paper is downloaded as a paperclip launcher — the real server classes
        // are extracted on first run. We can't binary-patch the launcher.
        // Instead we rename it simply and write NanoCore config alongside it
        // to disable bStats, RCON, etc. via Paper's own config flags.
        Path dest = destDir.resolve("paper-" + mcVersion + "-nanocore.jar");
        if (Files.exists(dest)) { System.out.println("  Already downloaded."); return dest; }

        if (GitHubClient.tryDownloadPatched("paper-" + mcVersion + "-nanocore", dest)) return dest;

        Path original = downloadFillStable("paper", mcVersion, destDir, "paper-" + mcVersion + "-original.jar");
        Files.move(original, dest, StandardCopyOption.REPLACE_EXISTING);
        writePaperConfig(destDir);
        System.out.println("  ✅ Paper " + mcVersion + " ready (config-patched)");
        return dest;
    }

    // ------------------------------------------------------------------
    // BungeeCord
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Config-based Paper "patching" (disables bStats, RCON, etc. via yml)
    // ------------------------------------------------------------------

    private static void writePaperConfig(Path serverDir) throws IOException {
        serverDir.toFile().mkdirs();

        // paper-global.yml — disables bStats, RCON, unnecessary features
        Path configDir = serverDir.resolve("config");
        configDir.toFile().mkdirs();
        Files.writeString(configDir.resolve("paper-global.yml"),
            "# NanoCore — auto-generated config\n" +
            "timings:\n" +
            "  enabled: false\n" +
            "  verbose: false\n" +
            "  server-name-privacy: false\n" +
            "  history-interval: -1\n" +
            "  history-length: 1\n" +
            "  url: ''\n" +
            "proxies:\n" +
            "  velocity:\n" +
            "    enabled: true\n" +
            "    online-mode: true\n" +
            "    secret: 'nanocore-secret'\n" +
            "console:\n" +
            "  enable-brigadier-highlighting: false\n" +
            "  enable-brigadier-completions: false\n"
        );

        // server.properties — disable RCON, enable-command-block false, etc.
        Path props = serverDir.resolve("server.properties");
        if (!Files.exists(props)) {
            Files.writeString(props,
                "enable-rcon=false\n" +
                "enable-query=false\n" +
                "online-mode=false\n" +
                "enforce-secure-profile=false\n" +
                "log-ips=false\n"
            );
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Parses Fill v3 nested versions map: {"versions": {"4.x": ["4.1.1", ...]}}
     * Returns the first (= newest) version string from the first group.
     */
    static String firstVersionFromNestedMap(String json) {
        // Find "versions":
        int vIdx = json.indexOf("\"versions\":");
        if (vIdx < 0) return null;
        // Skip past the outer { of the group map
        int firstBracket = json.indexOf("[", vIdx);
        if (firstBracket < 0) return null;
        int firstBracketEnd = json.indexOf("]", firstBracket);
        if (firstBracketEnd < 0) return null;
        String arr = json.substring(firstBracket + 1, firstBracketEnd).trim();
        if (arr.isEmpty()) return null;
        // First element is the newest version
        String first = arr.split(",")[0].trim().replace("\"", "");
        return first.isEmpty() ? null : first;
    }

    private static Path downloadFillStable(String project, String version,
                                            Path destDir, String destName)
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
