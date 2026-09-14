package com.nanocore.patch;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class JarPatcher {

    private final PatchManifest manifest;

    public JarPatcher(PatchManifest manifest) { this.manifest = manifest; }

    public int patch(Path inputJar, Path outputJar) throws IOException {
        outputJar.getParent().toFile().mkdirs();
        Path tmp = outputJar.resolveSibling(outputJar.getFileName() + ".tmp");
        int removed = 0, kept = 0;
        List<String> removedEntries = new ArrayList<>();

        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(inputJar)));
             ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            zout.setLevel(Deflater.BEST_SPEED);
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (manifest.shouldRemove(name)) {
                    removedEntries.add(name); removed++; zin.closeEntry(); continue;
                }
                ZipEntry outEntry = new ZipEntry(name);
                outEntry.setTime(entry.getTime());
                if (entry.getMethod() == ZipEntry.STORED && entry.getSize() >= 0) {
                    outEntry.setMethod(ZipEntry.STORED);
                    outEntry.setSize(entry.getSize());
                    outEntry.setCompressedSize(entry.getCompressedSize());
                    outEntry.setCrc(entry.getCrc());
                }
                zout.putNextEntry(outEntry); zin.transferTo(zout); zout.closeEntry(); zin.closeEntry(); kept++;
            }
            ZipEntry report = new ZipEntry("META-INF/NANOCORE_PATCH.txt");
            zout.putNextEntry(report);
            String txt = "NanoCore Patch\nServer: " + manifest.serverType
                + "\nRemoved: " + removed + "\nKept: " + kept
                + "\nhttps://github.com/NanoDev1488/NanoCore\n";
            zout.write(txt.getBytes()); zout.closeEntry();
        }
        Files.move(tmp, outputJar, StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("  [patch] %s: removed %d entries, kept %d%n", manifest.serverType, removed, kept);
        return removed;
    }

    public static int patchVelocity(Path input, Path output) throws IOException {
        System.out.println("\n[patch] Patching Velocity...");
        PatchManifest m = PatchManifest.forVelocity(); m.printSummary();
        return new JarPatcher(m).patch(input, output);
    }

    public static int patchPaper(String mcVersion, Path input, Path output) throws IOException {
        System.out.println("\n[patch] Patching Paper " + mcVersion + "...");
        PatchManifest m = PatchManifest.forPaper(mcVersion); m.printSummary();
        return new JarPatcher(m).patch(input, output);
    }

    public static int patchBungeeCord(Path input, Path output) throws IOException {
        System.out.println("\n[patch] Patching BungeeCord...");
        PatchManifest m = PatchManifest.forBungeeCord(); m.printSummary();
        return new JarPatcher(m).patch(input, output);
    }

    public static boolean isAlreadyPatched(Path jar) {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null)
                if (e.getName().equals("META-INF/NANOCORE_PATCH.txt")) return true;
        } catch (IOException ignored) {}
        return false;
    }
}
