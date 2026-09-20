package com.nanocore.download;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Создаёт и распаковывает portable NanoCore-bundle.jar.
 *
 * bundle = NanoCore.jar + все серверные jar'ы внутри под prefixом bundled/
 *
 * Как работает:
 *   bundle  → читает текущий NanoCore.jar, добавляет внутрь все jar'ы из NanoCore/,
 *             пишет NanoCore-bundle.jar. Итог: один файл на все случаи жизни.
 *
 *   extract → при запуске bundle-jar'а: если NanoCore/ пустая (нет серверов),
 *             распаковывает bundled/ обратно в NanoCore/. Прозрачно для пользователя.
 */
public class BundleManager {

    private static final String BUNDLE_PREFIX  = "bundled/";
    private static final String BUNDLE_MARKER  = "META-INF/NANOCORE_BUNDLE.txt";

    // ------------------------------------------------------------------
    // Создание bundle-jar
    // ------------------------------------------------------------------

    /**
     * Создаёт NanoCore-bundle.jar в той же папке что и запущенный jar.
     * Внутри: весь NanoCore.jar + все серверные jar'ы под bundled/
     */
    public static Path createBundle(Path nanoCoreJar, Path baseDir, Path outputJar)
            throws IOException {
        System.out.println("[bundle] Создаю " + outputJar.getFileName() + "...");

        List<Path> serverJars = collectServerJars(baseDir);
        if (serverJars.isEmpty()) {
            System.out.println("❌ Нет серверных jar'ов в " + baseDir + ". Сначала запусти: download");
            return null;
        }

        long totalBytes = nanoCoreJar.toFile().length()
            + serverJars.stream().mapToLong(p -> p.toFile().length()).sum();
        System.out.printf("  Размер входных данных: %.1f MB%n", totalBytes / 1024.0 / 1024.0);

        Path tmp = outputJar.resolveSibling(outputJar.getFileName() + ".tmp");

        try (ZipInputStream  zin  = new ZipInputStream(new BufferedInputStream(Files.newInputStream(nanoCoreJar)));
             ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {

            zout.setLevel(1); // быстрее, jar'ы уже сжаты

            // 1. Копируем весь NanoCore.jar
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().startsWith(BUNDLE_PREFIX)
                 || entry.getName().equals(BUNDLE_MARKER)) {
                    zin.closeEntry(); continue; // пересоздадим заново
                }
                ZipEntry out = new ZipEntry(entry.getName());
                out.setTime(entry.getTime());
                // STORED → сохраняем без перепаковки
                if (entry.getMethod() == ZipEntry.STORED && entry.getSize() >= 0) {
                    out.setMethod(ZipEntry.STORED);
                    out.setSize(entry.getSize());
                    out.setCompressedSize(entry.getCompressedSize());
                    out.setCrc(entry.getCrc());
                }
                zout.putNextEntry(out);
                zin.transferTo(zout);
                zout.closeEntry();
                zin.closeEntry();
            }

            // 2. Добавляем маркер bundle
            zout.putNextEntry(new ZipEntry(BUNDLE_MARKER));
            String manifest = "NanoCore Bundle\nServers: " + serverJars.size()
                + "\nDate: " + new java.util.Date() + "\n";
            zout.write(manifest.getBytes());
            zout.closeEntry();

            // 3. Добавляем все серверные jar'ы как STORED внутри bundled/
            for (Path jar : serverJars) {
                String arcName = BUNDLE_PREFIX + baseDir.relativize(jar).toString().replace('\\', '/');
                System.out.println("  + " + arcName + "  (" + jar.toFile().length()/1024/1024 + " MB)");
                addStoredEntry(zout, jar, arcName);
            }
        }

        Files.move(tmp, outputJar, StandardCopyOption.REPLACE_EXISTING);
        double sizeMB = outputJar.toFile().length() / 1024.0 / 1024.0;
        System.out.printf("%n✅ Bundle готов: %s (%.1f MB)%n", outputJar, sizeMB);
        System.out.println("  Запуск: java -jar " + outputJar.getFileName());
        return outputJar;
    }

    // ------------------------------------------------------------------
    // Распаковка при запуске bundle
    // ------------------------------------------------------------------

    /**
     * Если запущен bundle-jar (содержит BUNDLE_MARKER) и серверов ещё нет —
     * распаковывает bundled/ → baseDir. Вызывается из main() до discover().
     */
    public static boolean extractIfNeeded(Path runningJar, Path baseDir) throws IOException {
        if (!isBundleJar(runningJar)) return false;

        // Проверяем есть ли уже хоть один серверный jar
        if (hasServers(baseDir)) {
            System.out.println("[bundle] Серверы уже распакованы.");
            return false;
        }

        System.out.println("[bundle] Первый запуск bundle — распаковываю серверные jar'ы...");
        int extracted = 0;

        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(runningJar)))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (!entry.getName().startsWith(BUNDLE_PREFIX) || entry.isDirectory()) {
                    zin.closeEntry(); continue;
                }
                // bundled/proxy/velocity-patched.jar → baseDir/proxy/velocity-patched.jar
                String rel = entry.getName().substring(BUNDLE_PREFIX.length());
                Path dest = baseDir.resolve(rel);
                dest.getParent().toFile().mkdirs();
                System.out.println("  → " + rel);
                Files.copy(zin, dest, StandardCopyOption.REPLACE_EXISTING);
                zin.closeEntry();
                extracted++;
            }
        }

        System.out.println("✅ Распаковано: " + extracted + " файлов → " + baseDir);
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

    private static void addStoredEntry(ZipOutputStream zout, Path file, String arcName) throws IOException {
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
}
