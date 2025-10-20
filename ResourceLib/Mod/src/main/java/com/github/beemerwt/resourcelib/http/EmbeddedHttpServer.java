package com.github.beemerwt.resourcelib.http;

import com.github.beemerwt.resourcelib.config.ResourceLibConfig;
import com.github.beemerwt.resourcelib.util.Hashes;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EmbeddedHttpServer {
    private final ResourceLibConfig config;
    private volatile HttpServer server;
    private final Map<String, Path> files = new ConcurrentHashMap<>();

    public EmbeddedHttpServer(ResourceLibConfig config) {
        this.config = config;
    }

    public synchronized void start() throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String raw = exchange.getRequestURI().getPath(); // "/Name.zip"
                if (raw == null || raw.equals("/") || raw.isBlank()) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                String name = raw.startsWith("/") ? raw.substring(1) : raw;
                name = URLDecoder.decode(name, StandardCharsets.UTF_8);

                // Only plain filenames, no traversal
                if (name.contains("/") || name.contains("\\") || name.contains("..")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                Path target = files.get(name);
                if (target == null || !Files.isRegularFile(target)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                String etag = "\"" + Hashes.sha1Hex(target) + "\"";
                String inm = exchange.getRequestHeaders().getFirst("If-None-Match");
                if (etag.equals(inm)) {
                    exchange.sendResponseHeaders(304, -1);
                    return;
                }

                byte[] body = Files.readAllBytes(target);
                Headers h = exchange.getResponseHeaders();
                h.add("Content-Type", contentTypeFor(target));
                h.add("Content-Length", String.valueOf(body.length));
                h.add("ETag", etag);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            }
        });
        server.start();
    }

    /** Map a file to be downloadable as http://host:port/<filename> */
    public void register(Path file) {
        files.put(file.getFileName().toString(), file.normalize());
    }

    /** http://host:port/<filename> (filename is URL-encoded) */
    public String publicUrlFor(String fileName) {
        String enc = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        return "http://" + config.bindAddress() + ":" + config.port() + "/" + enc;
    }

    public void stop() {
        if (server != null) server.stop(1);
        files.clear();
        server = null;
    }

    private static String contentTypeFor(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".zip") || n.endsWith(".mcpack")) return "application/zip";
        return "application/octet-stream";
    }
}
