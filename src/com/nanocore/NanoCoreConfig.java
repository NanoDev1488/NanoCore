package com.nanocore;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Читает NanoCore/nanocore.properties — главный конфиг.
 *
 * Пример файла (создаётся автоматически при первом запуске):
 *
 *   # AdminHub — временный мини-сервер для администраторов
 *   adminhub_enabled=true
 *   adminhub_port=25599
 *   adminhub_permission=nanocore.adminhub
 *
 *   # Веб-панель
 *   web_port=8080
 *   web_user=
 *   web_pass=
 *
 *   # Paper versions to manage (comma-separated)
 *   paper_versions=1.21.4,1.21.11
 */
public class NanoCoreConfig {

    public final boolean adminHubEnabled;
    public final int     adminHubPort;
    public final String  adminHubPermission;
    public final String  webUser;
    public final String  webPass;
    public final int     webPort;
    public final String[] paperVersions;

    private NanoCoreConfig(Properties p) {
        this.adminHubEnabled    = !"false".equalsIgnoreCase(p.getProperty("adminhub_enabled", "true"));
        this.adminHubPort       = parseInt(p, "adminhub_port", 25599);
        this.adminHubPermission = p.getProperty("adminhub_permission", "nanocore.adminhub");
        this.webUser            = p.getProperty("web_user", "");
        this.webPass            = p.getProperty("web_pass", "");
        this.webPort            = parseInt(p, "web_port", 8080);
        this.paperVersions      = p.getProperty("paper_versions", "1.21.4,1.21.11").split(",");
    }

    public static NanoCoreConfig load(Path baseDir) {
        Path file = baseDir.resolve("nanocore.properties");
        Properties p = new Properties();

        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            catch (IOException ignored) {}
        } else {
            // Создаём шаблон
            try {
                Files.writeString(file, """
                    # NanoCore Configuration
                    # ========================

                    # AdminHub — мини-сервер для администраторов
                    # Команда /admServerHub в игре (Velocity)
                    adminhub_enabled=true
                    adminhub_port=25599
                    adminhub_permission=nanocore.adminhub

                    # Веб-панель мониторинга (http://IP:web_port/admin/monitor)
                    web_port=8080
                    # web_user=admin
                    # web_pass=changeme

                    # Версии Paper для управления
                    paper_versions=1.21.4,1.21.11
                    """);
                System.out.println("[config] Created: " + file);
            } catch (IOException ignored) {}
        }

        NanoCoreConfig cfg = new NanoCoreConfig(p);
        System.out.println("[config] AdminHub: " + (cfg.adminHubEnabled ? "enabled" : "disabled"));
        return cfg;
    }

    private static int parseInt(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
}
