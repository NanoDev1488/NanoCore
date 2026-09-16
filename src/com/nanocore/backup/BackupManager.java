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

public class BackupManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final ServerManager manager;
    private final NotifyService notify;
    private final Path backupDir;
    private final int keepCount, intervalHours;
    private final List<String> targetServers, includeFolders;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nanocore-backup"); t.setDaemon(true); return t;
        });

    public BackupManager(ServerManager manager, NotifyService notify, Path baseDir) {
        this.manager = manager; this.notify = notify;
        Properties p = loadProps(baseDir.resolve("backup.properties"), baseDir);
        this.backupDir     = Path.of(p.getProperty("backup_dir", baseDir.resolve("backups").toString()));
        this.keepCount     = Integer.parseInt(p.getProperty("backup_keep", "10"));
        this.intervalHours = Integer.parseInt(p.getProperty("backup_interval_hours", "6"));
        this.targetServers = Arrays.asList(p.getProperty("backup_servers","all").split(","));
        this.includeFolders = Arrays.asList(p.getProperty("backup_include","world,world_nether,world_the_end").split(","));
    }

    public void startScheduled() {
        System.out.printf("[backup] Auto-backup every %dh, keep %d copies%n", intervalHours, keepCount);
        scheduler.scheduleAtFixedRate(this::runAll, intervalHours, intervalHours, TimeUnit.HOURS);
    }

    public void runAll() {
        for (String id : manager.ids()) {
            if (!targetServers.contains("all") && !targetServers.contains(id)) continue;
            runFor(id);
        }
        pruneOld();
    }

    public void runFor(String serverId) {
        var inst = manager.getInstance(serverId);
        if (inst == null) { System.out.println("[backup] Server not found: " + serverId); return; }
        Path workdir = inst.getConfig().workdir;
        Path zipFile = backupDir.resolve(serverId + "_" + LocalDateTime.now().format(FMT) + ".zip");
        zipFile.getParent().toFile().mkdirs();
        System.out.println("[backup] Backing up " + serverId + " → " + zipFile.getFileName());
        if (inst.isRunning()) {
            inst.sendCommand("save-all");
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        }
        try (ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            zout.setLevel(6);
            for (String folder : includeFolders) {
                Path src = workdir.resolve(folder.trim());
                if (!Files.isDirectory(src)) continue;
                zipDir(src, folder.trim() + "/", zout);
            }
        } catch (IOException e) { System.out.println("[backup] ❌ Error: " + e.getMessage()); return; }
        long mb = zipFile.toFile().length() / 1024 / 1024;
        System.out.printf("[backup] ✅ %s → %s (%d MB)%n", serverId, zipFile.getFileName(), mb);
        notify.backupDone(zipFile.getFileName().toString(), mb);
    }

    private void zipDir(Path dir, String prefix, ZipOutputStream zout) throws IOException {
        try (var s = Files.walk(dir)) {
            for (Path p : s.toList()) {
                if (Files.isDirectory(p)) continue;
                String arc = prefix + dir.relativize(p).toString().replace('\\','/');
                zout.putNextEntry(new ZipEntry(arc));
                Files.copy(p, zout);
                zout.closeEntry();
            }
        }
    }

    private void pruneOld() {
        try (var list = Files.list(backupDir)) {
            List<Path> files = list.filter(p -> p.toString().endsWith(".zip"))
                .sorted(Comparator.comparingLong(p -> {
                    try { return Files.getLastModifiedTime(p).toMillis(); }
                    catch (IOException e) { return 0L; }
                })).toList();
            for (int i = 0; i < files.size() - keepCount; i++) {
                Files.deleteIfExists(files.get(i));
                System.out.println("[backup] Pruned: " + files.get(i).getFileName());
            }
        } catch (IOException ignored) {}
    }

    private Properties loadProps(Path file, Path base) {
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            catch (IOException ignored) {}
        } else {
            try { Files.writeString(file,
                "backup_dir=" + base.resolve("backups") + "\nbackup_interval_hours=6\n" +
                "backup_keep=10\nbackup_servers=all\nbackup_include=world,world_nether,world_the_end\n");
            } catch (IOException ignored) {}
        }
        return p;
    }
}
