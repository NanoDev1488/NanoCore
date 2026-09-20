package com.nanocore.patch;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;
import java.util.*;

/**
 * Патчер jar-файлов.
 *
 * Принцип работы:
 *   1. Читает оригинальный jar как ZipInputStream
 *   2. Для каждой записи проверяет PatchManifest.shouldRemove()
 *   3. Пропущенные записи — не копируются (удалены)
 *   4. Остальные записи копируются без изменений в новый jar
 *   5. Записывает NANOCORE_PATCH.txt с отчётом о патче
 *
 * Никаких внешних библиотек — только java.util.zip + java.util.jar.
 */
public class JarPatcher {

    private final PatchManifest manifest;

    public JarPatcher(PatchManifest manifest) {
        this.manifest = manifest;
    }

    /**
     * Патчит inputJar -> outputJar.
     * @return Количество удалённых записей.
     */
    public int patch(Path inputJar, Path outputJar) throws IOException {
        outputJar.getParent().toFile().mkdirs();
        Path tmp = outputJar.resolveSibling(outputJar.getFileName() + ".tmp");

        int removed = 0;
        int kept    = 0;
        List<String> removedEntries = new ArrayList<>();

        try (ZipInputStream  zin  = new ZipInputStream(
                    new BufferedInputStream(Files.newInputStream(inputJar)));
             ZipOutputStream zout = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmp)))) {

            zout.setLevel(Deflater.BEST_SPEED);

            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();

                if (manifest.shouldRemove(name)) {
                    removedEntries.add(name);
                    removed++;
                    zin.closeEntry();
                    continue;
                }

                // Копируем без изменений
                ZipEntry outEntry = new ZipEntry(name);
                outEntry.setTime(entry.getTime());
                if (entry.getMethod() == ZipEntry.STORED && entry.getSize() >= 0) {
                    outEntry.setMethod(ZipEntry.STORED);
                    outEntry.setSize(entry.getSize());
                    outEntry.setCompressedSize(entry.getCompressedSize());
                    outEntry.setCrc(entry.getCrc());
                }
                zout.putNextEntry(outEntry);
                zin.transferTo(zout);
                zout.closeEntry();
                zin.closeEntry();
                kept++;
            }

            // Добавляем отчёт о патче прямо в jar
            writePatchReport(zout, removedEntries, kept);
        }

        // Атомарная замена
        Files.move(tmp, outputJar, StandardCopyOption.REPLACE_EXISTING);

        System.out.printf("  [patch] %s: убрано %d записей, оставлено %d%n",
            manifest.serverType, removed, kept);
        printRemovedSummary(removedEntries);

        return removed;
    }

    private void writePatchReport(ZipOutputStream zout, List<String> removed, int kept)
            throws IOException {
        ZipEntry report = new ZipEntry("META-INF/NANOCORE_PATCH.txt");
        zout.putNextEntry(report);

        StringBuilder sb = new StringBuilder();
        sb.append("NanoCore Patch Report\n");
        sb.append("======================\n");
        sb.append("Server  : ").append(manifest.serverType).append("\n");
        sb.append("Desc    : ").append(manifest.description).append("\n");
        sb.append("Removed : ").append(removed.size()).append(" entries\n");
        sb.append("Kept    : ").append(kept).append(" entries\n");
        sb.append("GitHub  : https://github.com/NanoDev1488/NanoCore\n\n");
        sb.append("Removed entries:\n");
        for (String e : removed) sb.append("  - ").append(e).append("\n");

        zout.write(sb.toString().getBytes());
        zout.closeEntry();
    }

    private void printRemovedSummary(List<String> removed) {
        if (removed.isEmpty()) return;
        // Группируем по пакету для краткости
        Map<String, Integer> byPkg = new LinkedHashMap<>();
        for (String e : removed) {
            String pkg = e.contains("/")
                ? e.substring(0, e.lastIndexOf('/'))
                : "(root)";
            byPkg.merge(pkg, 1, Integer::sum);
        }
        System.out.println("  Удалённые пакеты:");
        byPkg.forEach((pkg, cnt) ->
            System.out.printf("    %-60s (%d классов)%n", pkg, cnt));
    }

    // ------------------------------------------------------------------
    // Статические фабрики под каждый тип сервера
    // ------------------------------------------------------------------

    public static int patchVelocity(Path input, Path output) throws IOException {
        System.out.println("\n[patch] Патчу Velocity...");
        PatchManifest m = PatchManifest.forVelocity();
        m.printSummary();
        return new JarPatcher(m).patch(input, output);
    }

    public static int patchPaper(String mcVersion, Path input, Path output) throws IOException {
        System.out.println("\n[patch] Патчу Paper " + mcVersion + "...");
        PatchManifest m = PatchManifest.forPaper(mcVersion);
        m.printSummary();
        return new JarPatcher(m).patch(input, output);
    }

    public static int patchBungeeCord(Path input, Path output) throws IOException {
        System.out.println("\n[patch] Патчу BungeeCord...");
        PatchManifest m = PatchManifest.forBungeeCord();
        m.printSummary();
        return new JarPatcher(m).patch(input, output);
    }

    /** Проверяет, был ли jar уже патчен нами. */
    public static boolean isAlreadyPatched(Path jar) {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals("META-INF/NANOCORE_PATCH.txt")) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }
}
