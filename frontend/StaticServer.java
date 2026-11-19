import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

class StaticServer {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Provide the web distribution directory");
        Path root = Path.of(args[0]).toRealPath();
        if (!Files.isRegularFile(root.resolve("index.html"))) {
            throw new IOException("The web distribution must contain index.html");
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> serve(exchange, root));
        server.start();
        System.out.println("Vent frontend listening on port " + port);
    }

    private static void serve(HttpExchange exchange, Path root) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Path target;
            try {
                String requestPath = exchange.getRequestURI().getPath();
                target = root.resolve(requestPath.substring(1)).normalize();
                if (!target.startsWith(root)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                if (Files.isDirectory(target)) target = target.resolve("index.html");
                target = target.toRealPath();
                if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
            } catch (InvalidPathException | IOException invalidFile) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", mediaType(target));
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            long size = Files.size(target);
            exchange.getResponseHeaders().set("Content-Length", Long.toString(size));
            exchange.sendResponseHeaders(200, method.equals("HEAD") || size == 0 ? -1 : size);
            if (method.equals("GET") && size > 0) Files.copy(target, exchange.getResponseBody());
        } finally {
            exchange.close();
        }
    }

    private static String mediaType(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".wasm")) return "application/wasm";
        if (name.endsWith(".js") || name.endsWith(".mjs")) return "text/javascript";
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".json") || name.endsWith(".map")) return "application/json";
        String detected = Files.probeContentType(file);
        return detected == null ? "application/octet-stream" : detected;
    }
}
