import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DhevEvRentalServer {
    private static final List<Vehicle> vehicles = new ArrayList<>();
    private static final List<Booking> bookings = new ArrayList<>();
    private static final Path PUBLIC_DIR = Path.of("public").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        seedData();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/login", DhevEvRentalServer::handleLogin);
        server.createContext("/api/vehicles", DhevEvRentalServer::handleVehicles);
        server.createContext("/api/bookings/cancel", DhevEvRentalServer::handleCancelBooking);
        server.createContext("/api/bookings", DhevEvRentalServer::handleBookings);
        server.createContext("/", DhevEvRentalServer::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("DHEV EV Rental Java server running at http://localhost:8080");
    }

    private static void seedData() {
        vehicles.add(new Vehicle("ev-001", "Tata Nexon EV Max", "SUV", "Coimbatore", 453, 88, 5, 2400, "Available", "12 Jul 2026", "https://images.unsplash.com/photo-1619767886558-efdc259cde1a?auto=format&fit=crop&w=900&q=80"));
        vehicles.add(new Vehicle("ev-002", "MG ZS EV", "Premium SUV", "Chennai", 461, 76, 5, 2800, "Available", "18 Jul 2026", "https://images.unsplash.com/photo-1617788138017-80ad40651399?auto=format&fit=crop&w=900&q=80"));
        vehicles.add(new Vehicle("ev-003", "Ather 450X", "Scooter", "Bengaluru", 150, 94, 2, 850, "Available", "09 Jul 2026", "https://images.unsplash.com/photo-1617654112368-307921291f42?auto=format&fit=crop&w=900&q=80"));
        vehicles.add(new Vehicle("ev-004", "Hyundai Kona Electric", "Crossover", "Erode", 452, 52, 5, 2600, "Service Soon", "05 Jul 2026", "https://images.unsplash.com/photo-1533473359331-0135ef1b58bf?auto=format&fit=crop&w=900&q=80"));
        bookings.add(new Booking("INV-2601", "Arun Kumar", "ev-002", "MG ZS EV", "Chennai", "2026-07-08", "2026-07-10", 5600, "Razorpay", "Paid"));
    }

    private static void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, jsonMessage("Method not allowed"));
            return;
        }

        String body = readBody(exchange);
        String userId = jsonValue(body, "userId");
        String password = jsonValue(body, "password");

        if ("dhevrentalev".equals(userId) && "1234".equals(password)) {
            send(exchange, 200, "{\"ok\":true,\"role\":\"Admin\"}");
        } else {
            send(exchange, 401, jsonMessage("Use user ID dhevrentalev and password 1234."));
        }
    }

    private static void handleVehicles(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            send(exchange, 200, vehiclesJson());
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            Vehicle vehicle = new Vehicle(
                "ev-" + UUID.randomUUID().toString().substring(0, 8),
                jsonValue(body, "name"),
                jsonValue(body, "type"),
                jsonValue(body, "location"),
                jsonInt(body, "range", 300),
                100,
                5,
                jsonInt(body, "price", 2000),
                "Available",
                "Schedule pending",
                "https://images.unsplash.com/photo-1593941707882-a5bba14938c7?auto=format&fit=crop&w=900&q=80"
            );
            vehicles.add(vehicle);
            send(exchange, 201, vehicle.toJson());
            return;
        }

        send(exchange, 405, jsonMessage("Method not allowed"));
    }

    private static void handleBookings(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            send(exchange, 200, bookingsJson());
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            String vehicleId = jsonValue(body, "vehicleId");
            String pickup = jsonValue(body, "pickup");
            String drop = jsonValue(body, "drop");
            Optional<Vehicle> vehicle = vehicles.stream().filter(item -> item.id.equals(vehicleId)).findFirst();

            if (vehicle.isEmpty()) {
                send(exchange, 404, jsonMessage("Vehicle not found"));
                return;
            }

            boolean blocked = bookings.stream().anyMatch(booking ->
                booking.vehicleId.equals(vehicleId) &&
                !"Cancelled".equals(booking.status) &&
                overlaps(pickup, drop, booking.pickup, booking.drop)
            );

            if (blocked) {
                send(exchange, 409, jsonMessage("This EV is already booked for those dates."));
                return;
            }

            Vehicle selected = vehicle.get();
            String payment = jsonValue(body, "payment");
            int days = rentalDays(pickup, drop);
            int subtotal = selected.price * days;
            int amount = subtotal - Math.round(subtotal * discountRate(jsonValue(body, "coupon")));

            Booking booking = new Booking(
                "INV-" + (2600 + (int) (Math.random() * 7000)),
                defaultText(jsonValue(body, "customer"), "Guest Customer"),
                selected.id,
                selected.name,
                selected.location,
                pickup,
                drop,
                amount,
                defaultText(payment, "Razorpay"),
                "Pay at pickup".equals(payment) ? "Pending" : "Paid"
            );
            bookings.add(booking);
            send(exchange, 201, booking.toJson());
            return;
        }

        send(exchange, 405, jsonMessage("Method not allowed"));
    }

    private static void handleCancelBooking(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, jsonMessage("Method not allowed"));
            return;
        }

        String id = jsonValue(readBody(exchange), "id");
        Optional<Booking> booking = bookings.stream().filter(item -> item.id.equals(id)).findFirst();
        if (booking.isEmpty()) {
            send(exchange, 404, jsonMessage("Booking not found"));
            return;
        }

        booking.get().status = "Cancelled";
        send(exchange, 200, booking.get().toJson());
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        Path file = PUBLIC_DIR.resolve(requestPath.substring(1)).normalize();
        if (!file.startsWith(PUBLIC_DIR) || !Files.exists(file) || Files.isDirectory(file)) {
            send(exchange, 404, "Not found", "text/plain");
            return;
        }

        String type = contentType(file);
        byte[] data = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private static boolean overlaps(String startA, String endA, String startB, String endB) {
        return startA.compareTo(endB) < 0 && startB.compareTo(endA) < 0;
    }

    private static int rentalDays(String pickup, String drop) {
        try {
            java.time.LocalDate start = java.time.LocalDate.parse(pickup);
            java.time.LocalDate end = java.time.LocalDate.parse(drop);
            return Math.max((int) java.time.temporal.ChronoUnit.DAYS.between(start, end), 1);
        } catch (Exception error) {
            return 1;
        }
    }

    private static float discountRate(String coupon) {
        if ("GREEN10".equalsIgnoreCase(coupon)) return 0.10f;
        if ("EV20".equalsIgnoreCase(coupon)) return 0.20f;
        return 0f;
    }

    private static String vehiclesJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < vehicles.size(); i++) {
            if (i > 0) json.append(",");
            json.append(vehicles.get(i).toJson());
        }
        return json.append("]").toString();
    }

    private static String bookingsJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < bookings.size(); i++) {
            if (i > 0) json.append(",");
            json.append(bookings.get(i).toJson());
        }
        return json.append("]").toString();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String jsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    private static int jsonInt(String json, String key, int fallback) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static String jsonMessage(String message) {
        return "{\"message\":\"" + escape(message) + "\"}";
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, body, "application/json");
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        return "application/octet-stream";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    static class Vehicle {
        String id;
        String name;
        String type;
        String location;
        int range;
        int battery;
        int seats;
        int price;
        String status;
        String maintenance;
        String image;

        Vehicle(String id, String name, String type, String location, int range, int battery, int seats, int price, String status, String maintenance, String image) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.location = location;
            this.range = range;
            this.battery = battery;
            this.seats = seats;
            this.price = price;
            this.status = status;
            this.maintenance = maintenance;
            this.image = image;
        }

        String toJson() {
            return "{" +
                "\"id\":\"" + escape(id) + "\"," +
                "\"name\":\"" + escape(name) + "\"," +
                "\"type\":\"" + escape(type) + "\"," +
                "\"location\":\"" + escape(location) + "\"," +
                "\"range\":" + range + "," +
                "\"battery\":" + battery + "," +
                "\"seats\":" + seats + "," +
                "\"price\":" + price + "," +
                "\"status\":\"" + escape(status) + "\"," +
                "\"maintenance\":\"" + escape(maintenance) + "\"," +
                "\"image\":\"" + escape(image) + "\"" +
                "}";
        }
    }

    static class Booking {
        String id;
        String customer;
        String vehicleId;
        String vehicleName;
        String location;
        String pickup;
        String drop;
        int amount;
        String payment;
        String status;

        Booking(String id, String customer, String vehicleId, String vehicleName, String location, String pickup, String drop, int amount, String payment, String status) {
            this.id = id;
            this.customer = customer;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.location = location;
            this.pickup = pickup;
            this.drop = drop;
            this.amount = amount;
            this.payment = payment;
            this.status = status;
        }

        String toJson() {
            return "{" +
                "\"id\":\"" + escape(id) + "\"," +
                "\"customer\":\"" + escape(customer) + "\"," +
                "\"vehicleId\":\"" + escape(vehicleId) + "\"," +
                "\"vehicleName\":\"" + escape(vehicleName) + "\"," +
                "\"location\":\"" + escape(location) + "\"," +
                "\"pickup\":\"" + escape(pickup) + "\"," +
                "\"drop\":\"" + escape(drop) + "\"," +
                "\"amount\":" + amount + "," +
                "\"payment\":\"" + escape(payment) + "\"," +
                "\"status\":\"" + escape(status) + "\"" +
                "}";
        }
    }
}
