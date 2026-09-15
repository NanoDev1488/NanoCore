package com.nanocore.download;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Создаёт и распаковывает portable NanoCore-bundle.jar.
 *
 * bundle = NanoCore.jar + все серверные jar'ы внутри под префиксом bundled/
 *
 * Создание:  java -jar NanoCore.jar bundle
 *            → NanoCore-bundle.jar (~200MB, всё в одном файле)
 *
 * Запуск:    java -jar NanoCore-bundle.jar
 *            → при первом запуске автоматически распаковывает серверы → стартует
 */
public class BundleManager {

    private static final String BUNDLE_PREFIX = "bundled/";
    private static final String BUNDLE_MARKER = "META-INF/NANOCORE_BUNDLE.txt";

    // ------------------------------------------------------------------
    // Создание bundle
    // ------------------------------------------------------------------

    public static Path createBundle(Path nanoCoreJar, Path baseDir, Path outputJar)
            throws IOException {
        System.out.println("[bundle] Создаю " + outputJar.getFileName() + "...");

        List<Path> serverJars = collectServerJars(baseDir);
        if (serverJars.isEmpty()) {
            System.out.println("❌ Серверных jar'ов нет в " + baseDir);
            System.out.println("   Сначала запусти: java -jar NanoCore.jar download");
            return null;
        }

        long totalMB = serverJars.stream().mapToLong(p -> p.toFile().length()).sum() / 1024 / 1024;
        System.out.println("  Включаем " + serverJars.size() + " серверных jar'ов (~" + totalMB + " MB)");

        Path tmp = outputJar.resolveSibling(outputJar.getFileName() + ".tmp");

        try (ZipInputStream  zin  = new ZipInputStream(new BufferedInputStream(Files.newInputStream(nanoCoreJar)));
             ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {

            zout.setLevel(1);

            // 1. Копируем весь NanoCore.jar (без старых bundled/ записей)
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().startsWith(BUNDLE_PREFIX)
                 || entry.getName().equals(BUNDLE_MARKER)) {
                    zin.closeEntry(); continue;
                }
                ZipEntry out = cloneEntry(entry);
                zout.putNextEntry(out);
                zin.transferTo(zout);
                zout.closeEntry();
                zin.closeEntry();
            }

            // 2. Маркер bundle
            zout.putNextEntry(new ZipEntry(BUNDLE_MARKER));
            zout.write(("NanoCore Bundle v1\nServers: " + serverJars.size()
                + "\nDate: " + new Date() + "\n").getBytes());
            zout.closeEntry();

            // 3. Серверные jar'ы как STORED (уже сжаты)
            for (Path jar : serverJars) {
                String arcName = BUNDLE_PREFIX + baseDir.relativize(jar).toString().replace('\\', '/');
                System.out.println("  + " + arcName + " (" + jar.toFile().length()/1024/1024 + " MB)");
                addStoredEntry(zout, jar, arcName);
            }
        }

        Files.move(tmp, outputJar, StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("%n✅ Bundle: %s (%.1f MB)%n",
            outputJar, outputJar.toFile().length() / 1024.0 / 1024.0);
        System.out.println("  Запуск: java -jar " + outputJar.getFileName());
        return outputJar;
    }

    // ------------------------------------------------------------------
    // Авто-распаковка при запуске bundle-jar
    // ------------------------------------------------------------------

    /**
     * Если запущен bundle-jar и серверов ещё нет — распаковывает bundled/ → baseDir.
     * Вызывается из main() автоматически. Прозрачно для пользователя.
     */
    public static boolean extractIfNeeded(Path runningJar, Path baseDir) throws IOException {
        if (!isBundleJar(runningJar)) return false;
        if (hasServers(baseDir)) return false;

        System.out.println("[bundle] Первый запуск — распаковываю серверные jar'ы...");
        int extracted = 0;

        try (ZipInputStream zin = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(runningJar)))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (!entry.getName().startsWith(BUNDLE_PREFIX) || entry.isDirectory()) {
                    zin.closeEntry(); continue;
                }
                String rel = entry.getName().substring(BUNDLE_PREFIX.length());
                Path dest = baseDir.resolve(rel);
                dest.getParent().toFile().mkdirs();
                System.out.println("  → " + rel);
                Files.copy(zin, dest, StandardCopyOption.REPLACE_EXISTING);
                zin.closeEntry();
                extracted++;
            }
        }

        System.out.println("✅ Распаковано " + extracted + " файлов → " + baseDir);
        return extracted > 0;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static boolean isBundleJar(Path jar) {
        if (jar == null || !Files.exists(jar)) return false;
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null)
                if (e.getName().equals(BUNDLE_MARKER)) return true;
        } catch (IOException ignored) {}
        return false;
    }

    private static boolean hasServers(Path baseDir) {
        for (String sub : new String[]{"proxy", "servers"}) {
            try (var s = Files.walk(baseDir.resolve(sub), 2)) {
                if (s.anyMatch(p -> p.toString().endsWith(".jar"))) return true;
            } catch (IOException ignored) {}
        }
        return false;
    }

    private static List<Path> collectServerJars(Path baseDir) throws IOException {
        List<Path> jars = new ArrayList<>();
        for (String sub : new String[]{"proxy", "servers"}) {
            Path dir = baseDir.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (var s = Files.walk(dir)) {
                s.filter(p -> p.toString().endsWith(".jar")).forEach(jars::add);
            }
        }
        jars.sort(Comparator.naturalOrder());
        return jars;
    }

    private static void addStoredEntry(ZipOutputStream zout, Path file, String arcName)
            throws IOException {
        byte[] data = Files.readAllBytes(file);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(arcName);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        zout.putNextEntry(entry);
        zout.write(data);
        zout.closeEntry();
    }

    private static ZipEntry cloneEntry(ZipEntry src) {
        ZipEntry dst = new ZipEntry(src.getName());
        dst.setTime(src.getTime());
        if (src.getMethod() == ZipEntry.STORED && src.getSize() >= 0) {
            dst.setMethod(ZipEntry.STORED);
            dst.setSize(src.getSize());
            dst.setCompressedSize(src.getCompressedSize());
            dst.setCrc(src.getCrc());
        }
        return dst;
    }
}
