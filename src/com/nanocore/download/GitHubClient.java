package com.nanocore.download;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.*;

public class GitHubClient {

    private static final String REPO = "NanoDev1488/NanoCore";
    private static final String API  = "https://api.github.com/repos/" + REPO;

    public static boolean tryDownloadPatched(String assetPrefix, Path dest) throws InterruptedException {
        try {
            System.out.println("  [github] Looking for patched jar: " + assetPrefix + " ...");
            String releasesJson = Downloader.fetch(API + "/releases/latest");
            Pattern p = Pattern.compile(
                "\"browser_download_url\":\\s*\"([^\"]*" + Pattern.quote(assetPrefix) + "[^\"]*\\.jar)\"");
            Matcher m = p.matcher(releasesJson);
            if (!m.find()) {
                System.out.println("  [github] Not found in latest release, will patch locally.");
                return false;
            }
            String url = m.group(1);
            System.out.println("  [github] Found: " + url);
            Downloader.download(url, dest);
            System.out.println("  \u2705 Downloaded pre-patched jar from GitHub Releases.");
            return true;
        } catch (IOException e) {
            System.out.println("  [github] Unavailable: " + e.getMessage());
            return false;
        }
    }
}
