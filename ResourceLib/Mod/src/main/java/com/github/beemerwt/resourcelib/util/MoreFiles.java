package com.github.beemerwt.resourcelib.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MoreFiles {
    private MoreFiles() {}

    public static void unzipOverlay(Path zip, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                Path out = destDir.resolve(e.getName()).normalize();
                // Avoid traversal
                if (!out.startsWith(destDir)) continue;
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void deleteQuietly(Path p) {
        if (p == null) return;
        if (Files.notExists(p)) return;
        try (var stream = Files.walk(p)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                  .forEach(q -> { try { Files.deleteIfExists(q); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
