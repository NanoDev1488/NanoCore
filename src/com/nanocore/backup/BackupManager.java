package com.nanocore.backup;

import com.nanocore.core.ServerManager;
import com.nanocore.notify.NotifyService;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/**
 * BackupManager — резервное копирование миров.
 *
 * Настройка: NanoCore/backup.properties
 *   backup_dir=NanoCore/backups
 *   backup_interval_hours=6
 *   backup_keep=10          (сколько последних бэкапов хранить)
 *   backup_servers=all      (all | velocity,paper-1.21.4 | ...)
 *   backup_include=world,world_nether,world_the_end
 */
public class BackupManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final ServerManager manager;
    private final NotifyService notify;
    private final Path backupDir;
    private final int keepCount;
    private final int intervalHours;
    private final List<String> targetServers;
    private final List<String> includeFolders;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nanocore-backup");
            t.setDaemon(true);
            return t;
        });

    public BackupManager(ServerManager manager, NotifyService notify, Path baseDir) {
        this.manager = manager;
        this.notify  = notify;

        Properties props = loadProps(baseDir.resolve("backup.properties"), baseDir);
        this.backupDir     = Path.of(props.getProperty("backup_dir",
            baseDir.resolve("backups").toString()));
        this.keepCount     = Integer.parseInt(props.getProperty("backup_keep", "10"));
        this.intervalHours = Integer.parseInt(props.getProperty("backup_interval_hours", "6"));
        this.targetServers = Arrays.asList(
            props.getProperty("backup_servers", "all").split(","));
        this.includeFolders = Arrays.asList(
            props.getProperty("backup_include", "world,world_nether,world_the_end").split(","));
    }

    public void startScheduled() {
        System.out.printf("[backup] Авто-бэкап каждые %dч, хранить %d копий%n",
            intervalHours, keepCount);
        scheduler.scheduleAtFixedRate(this::runAll,
            intervalHours, intervalHours, TimeUnit.HOURS);
    }

    /** Бэкап всех (или выбранных) серверов прямо сейчас */
    public void runAll() {
        for (String id : manager.ids()) {
            if (!targetServers.contains("all") && !targetServers.contains(id)) continue;
            runFor(id);
        }
        pruneOld();
    }

    /** Бэкап конкретного сервера */
    public void runFor(String serverId) {
        var inst = manager.getInstance(serverId);
        if (inst == null) { System.out.println("[backup] Сервер не найден: " + serverId); return; }

        Path workdir = inst.getConfig().workdir;
        String timestamp = LocalDateTime.now().format(FMT);
        Path zipFile = backupDir.resolve(serverId + "_" + timestamp + ".zip");
        zipFile.getParent().toFile().mkdirs();

        System.out.println("[backup] Начинаю бэкап " + serverId + " → " + zipFile.getFileName());

        // Шлём save-all перед бэкапом
        if (inst.isRunning()) {
            inst.sendCommand("save-all");
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        }

        long totalBytes = 0;
        try (ZipOutputStream zout = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            zout.setLevel(6);

            for (String folder : includeFolders) {
                Path src = workdir.resolve(folder.trim());
                if (!Files.isDirectory(src)) continue;
                totalBytes += zipDir(src, folder.trim() + "/", zout);
            }
        } catch (IOException e) {
            System.out.println("[backup] ❌ Ошибка: " + e.getMessage());
            return;
        }

        long sizeMB = zipFile.toFile().length() / 1024 / 1024;
        System.out.printf("[backup] ✅ %s → %s (%d MB)%n",
            serverId, zipFile.getFileName(), sizeMB);
        notify.backupDone(zipFile.getFileName().toString(), sizeMB);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long zipDir(Path dir, String prefix, ZipOutputStream zout) throws IOException {
        long total = 0;
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.toList()) {
                if (Files.isDirectory(p)) continue;
                String arcName = prefix + dir.relativize(p).toString().replace('\\', '/');
                zout.putNextEntry(new ZipEntry(arcName));
                total += Files.copy(p, zout);
                zout.closeEntry();
            }
        }
        return total;
    }

    private void pruneOld() {
        try (var list = Files.list(backupDir)) {
            List<Path> files = list
                .filter(p -> p.toString().endsWith(".zip"))
                .sorted(Comparator.comparingLong(p -> {
                    try { return Files.getLastModifiedTime(p).toMillis(); }
                    catch (IOException e) { return 0L; }
                }))
                .toList();

            if (files.size() > keepCount) {
                for (int i = 0; i < files.size() - keepCount; i++) {
                    Files.deleteIfExists(files.get(i));
                    System.out.println("[backup] Удалён старый бэкап: " + files.get(i).getFileName());
                }
            }
        } catch (IOException ignored) {}
    }

    private Properties loadProps(Path file, Path baseDir) {
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            catch (IOException ignored) {}
        } else {
            try {
                Files.writeString(file,
                    "backup_dir=" + baseDir.resolve("backups") + "\n" +
                    "backup_interval_hours=6\n" +
                    "backup_keep=10\n" +
                    "backup_servers=all\n" +
                    "backup_include=world,world_nether,world_the_end\n");
            } catch (IOException ignored) {}
        }
        return p;
    }
}
