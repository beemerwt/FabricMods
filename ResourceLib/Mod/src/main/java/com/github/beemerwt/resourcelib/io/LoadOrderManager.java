package com.github.beemerwt.resourcelib.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A dead-simple YAML-ish file:
 *
 * key:
 *   - FileA.zip
 *   - FileB.zip
 *
 * Stored at the provided path. Unknown names are appended after known ones in natural order.
 */
public record LoadOrderManager(Path path) {

    public List<File> resolveOrder(String key, List<File> registered) throws IOException {
        Map<String, List<String>> data = read();
        List<String> names = registered.stream().map(File::getName).toList();

        List<String> order = new ArrayList<>(data.getOrDefault(key, List.of()));
        // Keep only entries that still exist
        order.removeIf(n -> !names.contains(n));
        // Append new names that were not in the file
        for (String n : names) {
            if (!order.contains(n)) order.add(n);
        }
        data.put(key, new ArrayList<>(order));
        write(data);

        // Now map back to files by this order
        Map<String, File> byName = new LinkedHashMap<>();
        for (File f : registered) byName.put(f.getName(), f);

        List<File> result = new ArrayList<>();
        for (String n : order) {
            File f = byName.get(n);
            if (f != null) result.add(f);
        }
        return result;
    }

    private Map<String, List<String>> read() throws IOException {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (Files.notExists(path)) return out;

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String current = null;
            String line;
            while ((line = br.readLine()) != null) {
                line = line.stripTrailing();
                if (line.isBlank()) continue;
                if (!line.startsWith("  - ") && line.endsWith(":")) {
                    current = line.substring(0, line.length() - 1).trim();
                    out.putIfAbsent(current, new ArrayList<>());
                } else if (line.startsWith("  - ") && current != null) {
                    out.get(current).add(line.substring(4).trim());
                }
            }
        }
        return out;
    }

    private void write(Map<String, List<String>> data) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter bw = Files.newBufferedWriter(path)) {
            for (Map.Entry<String, List<String>> e : data.entrySet()) {
                bw.write(e.getKey());
                bw.write(":\n");
                for (String v : e.getValue()) {
                    bw.write("  - ");
                    bw.write(v);
                    bw.write("\n");
                }
            }
        }
    }
}
