import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.event.*;
import java.util.List;

/* SWIFTMART - Java backend + HTML/CSS/JS frontend
 * OOP Used: Abstraction (Product), Inheritance (GenericProduct), Polymorphism (getDiscountPercent),
 * Interface (Discountable), Encapsulation (private fields), Custom exceptions (StoreException),
 * File Handling (inventory.txt, order_audit.txt, individual receipts, reviews.txt, CSV import/export),
 * Collections (List, Map). */

public class Main {
    interface Discountable { double getDiscountPercent(); }

    static class StoreException extends Exception {
        final String field;
        StoreException(String field, String msg) { super(msg); this.field = field; }
    }

    static abstract class Product implements Discountable {
        private final int id;
        private String name, brand, category, sizes, colors, image;
        private double price;
        private int stock;

        Product(int id, String cat, String name, String brand, String price, String stock, String sizes, String colors, String image) throws StoreException {
            this.id = id;
            this.category = req("cat", cat);
            this.name = req("name", name);
            this.brand = req("brand", brand);
            try { this.price = Double.parseDouble(price.trim()); if (this.price <= 0) throw new NumberFormatException(); }
            catch (RuntimeException e) { throw new StoreException("price", "Price must be a valid number above 0."); }
            try { this.stock = Integer.parseInt(stock.trim()); if (this.stock < 0) throw new NumberFormatException(); }
            catch (RuntimeException e) { throw new StoreException("stock", "Stock must be a whole number, 0 or more."); }
            this.sizes = opt(sizes);
            this.colors = opt(colors);
            this.image = opt(image);
        }

        Product(int id, String cat, String name, String brand, String price, String stock, String sizes, String colors) throws StoreException {
            this(id, cat, name, brand, price, stock, sizes, colors, "-");
        }

        private static String req(String field, String s) throws StoreException {
            if (s == null || s.trim().isEmpty()) throw new StoreException(field, "This field cannot be empty.");
            return s.trim().replace("|", " ");
        }
        private static String opt(String s) { return s == null || s.trim().isEmpty() ? "-" : s.trim().replace("|", " "); }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getBrand() { return brand; }
        public String getCategory() { return category; }
        public double getPrice() { return price; }
        public int getStock() { return stock; }
        public String getSizes() { return sizes; }
        public String getColors() { return colors; }
        public String getImage() { return (image == null || image.equals("-")) ? "" : image; }
        public void setImage(String img) { this.image = opt(img); }
        public double getFinalPrice() { return price * (1 - getDiscountPercent() / 100.0); }

        public void reduceStock(int q) throws StoreException {
            if (q > stock) throw new StoreException("cart", "Not enough stock for " + name + " (Only " + stock + " left).");
            stock -= q;
        }
        public void setStock(int s) { if (s >= 0) this.stock = s; }
        public void setPrice(double p) { if (p > 0) this.price = p; }

        String toLine() { return id + "|" + category + "|" + name + "|" + brand + "|" + price + "|" + stock + "|" + sizes + "|" + colors + "|" + image; }
        String json() {
            return String.format(Locale.US, "{\"id\":%d,\"cat\":\"%s\",\"name\":\"%s\",\"brand\":\"%s\",\"price\":%.2f,\"final\":%.2f,\"disc\":%.0f,\"stock\":%d,\"sizes\":%s,\"colors\":%s,\"img\":\"%s\"}",
                    id, Main.esc(category), Main.esc(name), Main.esc(brand), price, getFinalPrice(), getDiscountPercent(), stock, arr(sizes), arr(colors), Main.esc(getImage()));
        }
        private static String arr(String s) {
            if (s.equals("-")) return "[]";
            StringBuilder b = new StringBuilder("[");
            for (String x : s.split(",")) b.append(b.length() > 1 ? "," : "").append("\"").append(Main.esc(x.trim())).append("\"");
            return b.append("]").toString();
        }
    }

    static class GenericProduct extends Product {
        GenericProduct(int id, String c, String n, String b, String p, String s, String sz, String col, String img) throws StoreException { super(id, c, n, b, p, s, sz, col, img); }
        GenericProduct(int id, String c, String n, String b, String p, String s, String sz, String col) throws StoreException { super(id, c, n, b, p, s, sz, col, "-"); }
        @Override public double getDiscountPercent() { return 5.0; } // 5% store discount
    }
    static final Path WEB = resolveWebDir();
    static final Path DIR = resolveDataDir();
    static final Path INV = DIR.resolve("inventory.txt");
    static final Path ORD = DIR.resolve("order_audit.txt");
    static final Path RCP = DIR.resolve("receipts");
    static final Path REV = DIR.resolve("reviews.txt");
    static final Path RCL = DIR.resolve("review_claims.txt"); // "ORD-xxxx:productId" -> one review per order line
    static final String JSON = "application/json; charset=utf-8";
    static final List<Product> inv = new ArrayList<>();
    static final List<String> revList = new ArrayList<>();

    private static Path resolveWebDir() {
        String prop = System.getProperty("web");
        if (prop != null && !prop.trim().isEmpty()) {
            return Paths.get(prop).toAbsolutePath().normalize();
        }
        // Look for index.html in: working dir, ./web, the folder of the compiled classes, and their parents
        List<Path> starts = new ArrayList<>();
        starts.add(Paths.get("").toAbsolutePath().normalize());
        try {
            Path code = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            starts.add(Files.isDirectory(code) ? code : code.getParent());
        } catch (Exception ignored) { }
        for (Path st : starts) {
            for (Path d = st; d != null; d = d.getParent()) {
                if (Files.exists(d.resolve("index.html"))) return d;
                if (Files.exists(d.resolve("web").resolve("index.html"))) return d.resolve("web");
            }
        }
        return starts.get(0);
    }

    private static Path resolveDataDir() {
        String prop = System.getProperty("data");
        if (prop != null && !prop.trim().isEmpty()) {
            return Paths.get(prop).toAbsolutePath().normalize();
        }
        Path local = Paths.get("data").toAbsolutePath().normalize();
        try {
            Files.createDirectories(local);
            return local;
        } catch (Exception e) {
            return Paths.get(System.getProperty("user.home"), "SwiftMart");
        }
    }

    static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    static String readStr(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    static void writeStr(Path p, String s, OpenOption... opts) throws IOException {
        Files.write(p, s.getBytes(StandardCharsets.UTF_8), opts);
    }

    static String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // 15 Distinct Products for Each of the 9 Categories (135 Total Products)
    static final String[] SEED = {
            // --- 1. Electronics (15 Products) ---
            "Electronics|Wireless Headphones|Sony|129|35|-|Black,White,Silver",
            "Electronics|Gaming Laptop|Asus|1299|12|-|Black,Grey",
            "Electronics|Smartphone Pro|Samsung|899|20|-|Black,Blue,Silver",
            "Electronics|Bluetooth Speaker|JBL|79|45|-|Black,Red,Blue",
            "Electronics|4K Action Camera|GoPro|249|18|-|Black",
            "Electronics|Noise Cancelling Earbuds|Apple|199|30|-|White,Black",
            "Electronics|Ultra Tablet Pro|Apple|799|16|-|Grey,Silver",
            "Electronics|Mechanical Gaming Keyboard|Razer|119|28|-|Black,Silver",
            "Electronics|Wireless Ergonomic Mouse|Logitech|69|50|-|Grey,Black",
            "Electronics|Fast Power Bank 20000mAh|Anker|49|65|-|Black,White",
            "Electronics|Portable SSD 1TB|SanDisk|109|40|-|Black,Grey",
            "Electronics|Mirrorless 4K Camera|Fujifilm|849|10|-|Silver,Black",
            "Electronics|Smart OLED Fitness Band|Xiaomi|39|75|-|Black,Blue",
            "Electronics|Compact Studio Soundbar|Sonos|279|15|-|Black,White",
            "Electronics|Smart Home Assistant Hub|Google|89|35|-|White,Charcoal",

            // --- 2. Clothing (15 Products) ---
            "Clothing|Classic Hoodie|Nike|59|60|S,M,L,XL|Black,Grey,White",
            "Clothing|Slim Fit Jeans|Levis|69|55|28,30,32,34,36|Blue,Black",
            "Clothing|Executive Suit|Armani|350|20|M,L,XL|Black,Navy",
            "Clothing|Cotton T-Shirt|Uniqlo|19|120|S,M,L,XL|White,Black,Grey",
            "Clothing|Denim Jacket|Zara|89|30|S,M,L|Blue,Black",
            "Clothing|Oversized Fleece Sweatshirt|Champion|65|45|S,M,L,XL|Grey,Black,Navy",
            "Clothing|Tailored Chino Trousers|Dockers|55|40|30,32,34,36|Khaki,Black,Navy",
            "Clothing|Classic Wool Trench Coat|Burberry|420|12|M,L,XL|Beige,Black",
            "Clothing|Flight Bomber Jacket|Alpha Industries|135|25|S,M,L,XL|Olive,Black",
            "Clothing|Casual Oxford Shirt|Ralph Lauren|85|35|S,M,L,XL|White,Blue",
            "Clothing|Merino Wool Turtleneck|Uniqlo|49|50|S,M,L,XL|Black,Charcoal,Navy",
            "Clothing|Linen Summer Button-Down|Mango|45|38|S,M,L|White,Beige,Olive",
            "Clothing|Utility Cargo Pants|Carhartt|79|42|30,32,34,36|Black,Olive",
            "Clothing|Athletic Track Jacket|Adidas|70|48|S,M,L,XL|Black,White",
            "Clothing|Quilted Puffer Vest|The North Face|110|28|M,L,XL|Black,Navy",

            // --- 3. Shoes (15 Products) ---
            "Shoes|Running Sneakers|Adidas|110|40|40,41,42,43,44|White,Black,Grey",
            "Shoes|Leather Boots|Timberland|160|25|40,41,42,43|Brown,Black",
            "Shoes|Classic Loafers|Clarks|95|30|40,41,42,43|Black,Brown",
            "Shoes|Air Basketball High-Tops|Nike|140|28|41,42,43,44,45|White,Red,Black",
            "Shoes|Canvas Slip-On Sneakers|Vans|55|60|39,40,41,42,43|Black,White",
            "Shoes|Oxford Formal Dress Shoes|Cole Haan|150|22|40,41,42,43|Black,Brown",
            "Shoes|Trail Hiking Mountain Boots|Salomon|175|18|41,42,43|Grey,Black",
            "Shoes|Chelsea Suede Boots|Dr Martens|165|20|40,41,42,43|Black,Beige",
            "Shoes|Minimalist Leather Tennis Shoes|Common Projects|190|15|40,41,42,43|White,Black",
            "Shoes|Suede Derby Shoes|Clarks|105|24|40,41,42,43|Beige,Navy,Brown",
            "Shoes|Lightweight Training Runners|Under Armour|85|45|40,41,42,43,44|Black,Grey",
            "Shoes|Double Monk Strap Shoes|Magnanni|220|14|41,42,43|Brown,Black",
            "Shoes|Retro Suede Skate Sneakers|New Balance|95|38|40,41,42,43,44|Grey,Navy",
            "Shoes|Summer Canvas Espadrilles|Castaner|65|30|40,41,42,43|Navy,Beige",
            "Shoes|Slip-On Leather Driving Shoes|Tod's|185|16|41,42,43|Brown,Navy",

            // --- 4. Bags (15 Products) ---
            "Bags|Leather Backpack|Herschel|85|35|-|Black,Brown",
            "Bags|Travel Duffel|Samsonite|120|22|-|Black,Navy",
            "Bags|Minimalist Crossbody Bag|Bellroy|75|40|-|Black,Grey,Olive",
            "Bags|Waterproof Commuter Backpack|Rains|95|30|-|Black,Charcoal",
            "Bags|Waxed Canvas Messenger Bag|Filson|145|18|-|Beige,Olive",
            "Bags|Rolling Carry-On Luggage|Away|225|15|-|Black,Grey,Navy",
            "Bags|Gym Sports Duffle with Shoe Pocket|Nike|55|50|-|Black,Grey",
            "Bags|Vintage Leather Satchel|Fossil|135|20|-|Brown,Black",
            "Bags|Everyday Heavyweight Tote Bag|Carhartt|45|60|-|Brown,Black",
            "Bags|Tactical Molle Daypack|5.11|110|25|-|Black,Beige",
            "Bags|Slim RFID Leather Cardholder|Ridge|70|55|-|Grey,Black",
            "Bags|Camera Shoulder Bag|Peak Design|130|18|-|Charcoal,Black",
            "Bags|Weekender Overnighter Bag|Everlane|98|24|-|Black,Navy",
            "Bags|Urban Hip Belt Pack|Patagonia|42|45|-|Black,Blue",
            "Bags|Expandable 40L Travel Pack|Osprey|160|16|-|Black,Olive",

            // --- 5. Watches (15 Products) ---
            "Watches|Minimal Watch|Casio|75|45|-|Black,Silver,Gold",
            "Watches|Chronograph|Fossil|180|22|-|Black,Brown",
            "Watches|Automatic Aviator Pilot Watch|Seiko|210|18|-|Black,Silver",
            "Watches|Diver 200m Automatic|Citizen|240|16|-|Blue,Black,Silver",
            "Watches|Skeleton Mechanical Timepiece|Fossil|220|14|-|Grey,Silver",
            "Watches|Vintage Retro Digital Watch|Casio|45|80|-|Gold,Silver,Black",
            "Watches|Solar Powered Field Watch|Timex|95|35|-|Olive,Black",
            "Watches|Bauhaus Minimalist Dress Watch|Junghans|310|12|-|White,Black",
            "Watches|Ceramic Bezel Sports Watch|Tissot|340|10|-|Black,Blue",
            "Watches|Titanium Lightweight Watch|Skagen|145|25|-|Grey,Silver",
            "Watches|Stainless Steel Bracelet Watch|Armani Exchange|160|28|-|Silver,Black",
            "Watches|World Time GMT Watch|Seiko|275|15|-|Blue,Red,Black",
            "Watches|Smart Hybrid Watch|Withings|195|20|-|Black,White",
            "Watches|Luxury Gold Plated Slim Watch|Bulova|250|14|-|Gold",
            "Watches|Military Tactical Watch|Luminox|290|12|-|Black",

            // --- 6. Groceries (15 Products) ---
            "Groceries|Organic Coffee|Nestle|18|120|-|-",
            "Groceries|Dark Chocolate Box|Lindt|14|90|-|-",
            "Groceries|Green Tea Pack|Lipton|9|110|-|-",
            "Groceries|Raw Mountain Honey 500g|Langnese|16|70|-|-",
            "Groceries|Extra Virgin Olive Oil 1L|Monini|22|60|-|-",
            "Groceries|Whole Grain Toasted Granola|Kelloggs|11|85|-|-",
            "Groceries|Creamy Almond Butter 400g|Pip and Nut|13|65|-|-",
            "Groceries|Himalayan Pink Salt Grinder|Drogheria|8|100|-|-",
            "Groceries|Organic Ceremonial Matcha Tea|Ippodo|28|45|-|-",
            "Groceries|Organic Tricolor Quinoa 1kg|Nature Crop|12|75|-|-",
            "Groceries|Sun-Dried Medjool Dates 500g|Bateel|19|50|-|-",
            "Groceries|Artisan Bronze Cut Pasta|De Cecco|7|130|-|-",
            "Groceries|Dark Roast Espresso Whole Beans|Lavazza|24|60|-|-",
            "Groceries|Pure Maple Syrup Grade A 250ml|Kirkland|15|55|-|-",
            "Groceries|Natural Organic Chia Seeds 500g|Sevenhills|10|90|-|-",

            // --- 7. Stationery (15 Products) ---
            "Stationery|Premium Notebook|Moleskine|16|95|-|Black,Red",
            "Stationery|Fountain Pen|Parker|45|40|-|Black,Blue",
            "Stationery|Brass Heavyweight Mechanical Pencil|Kaweco|38|45|-|Gold,Black",
            "Stationery|Hardcover Watercolor Sketchbook|Leuchtturm|22|60|-|Black,Navy,Olive",
            "Stationery|Archival Calligraphy Ink Bottle|Winsor and Newton|14|50|-|Black,Brown",
            "Stationery|Fine Gel Pens 0.5mm 10-Pack|Muji|18|110|-|Black,Blue",
            "Stationery|Handcrafted Leather Journal Cover|Roterfaden|65|25|-|Brown,Black",
            "Stationery|Solid Aluminium Precision Ruler Set|Midori|24|55|-|Silver,Black",
            "Stationery|Eco Vegan Leather Desk Mat|Orbitkey|42|35|-|Black,Grey",
            "Stationery|Pastel Minimalist Sticky Notes Pack|Post-it|9|140|-|-",
            "Stationery|Solid Brass Bookmark Clip Set|Midori|15|70|-|Gold",
            "Stationery|Executive Matte Letter Opener|Philippi|28|30|-|Silver,Black",
            "Stationery|Expandable Accordion Document Folder|Kokuyo|19|50|-|Grey,White",
            "Stationery|Micro Fine Liner Artist Pens 6-Pack|Sakura Pigma|17|80|-|Black",
            "Stationery|Solid Walnut Wood Desk Pen Stand|Grovemade|35|40|-|Brown,Beige",

            // --- 8. Toys (15 Products) ---
            "Toys|Building Blocks Set|Lego|59|50|-|-",
            "Toys|RC Racing Car|Hot Wheels|39|45|-|Red,Blue",
            "Toys|Mini Folding Camera Drone|DJI|299|20|-|Grey,White",
            "Toys|Tournament Handcrafted Wooden Chess|Staunton|75|25|-|Brown,White",
            "Toys|3D Magnetic Tiles 100-Piece Kit|Magna-Tiles|65|35|-|-",
            "Toys|Diecast 1:18 Model Supercar|Bburago|48|30|-|Red,Black",
            "Toys|Classic Soft Plush Bear 40cm|Steiff|45|40|-|Brown,White",
            "Toys|Monocular Compound Science Microscope|National Geographic|85|22|-|Black,White",
            "Toys|Magnetic Competition Speed Cube|GAN|29|70|-|-",
            "Toys|Programmable Educational Robot|Makeblock|115|18|-|Blue,Black",
            "Toys|Classic Wooden Railway Train Set|Brio|54|32|-|Brown",
            "Toys|Deluxe Artist Easel & Acrylics Set|Crayola|34|45|-|-",
            "Toys|Gyroscope RC Indoor Helicopter|Syma|32|50|-|Red,Blue",
            "Toys|1000-Piece Panoramic Art Jigsaw|Ravensburger|22|60|-|-",
            "Toys|Collectible Anime Action Figure|Bandai|58|35|-|-",

            // --- 9. Furniture (15 Products) ---
            "Furniture|Ergonomic Chair|Herman Miller|420|15|-|Black,Grey",
            "Furniture|Standing Desk|IKEA|310|18|-|White,Black",
            "Furniture|Table Lamp|Philips|35|60|-|White,Black",
            "Furniture|Solid Oak Minimalist Coffee Table|MUJI|180|14|-|Brown,Black",
            "Furniture|Velvet Accent Lounge Armchair|West Elm|260|12|-|Grey,Charcoal,Navy",
            "Furniture|5-Tier Modular Wall Bookshelf|IKEA|125|22|-|Black,White",
            "Furniture|Bedside Dual Drawer Nightstand|Target|75|30|-|Brown,White",
            "Furniture|Floating Wall Shelves 3-Piece|Castlery|48|45|-|Brown,Black",
            "Furniture|Modern Arc Steel Floor Lamp|Flos|145|20|-|Black,Gold",
            "Furniture|Contemporary 3-Seater Fabric Sofa|Article|550|8|-|Charcoal,Grey",
            "Furniture|Solid Hardwood Dining Table|Crate and Barrel|480|10|-|Brown",
            "Furniture|Top-Grain Leather Swivel Recliner|Stressless|390|9|-|Black,Brown",
            "Furniture|Ceramic Indoor Planter with Stand|Kanso|39|50|-|White,Black",
            "Furniture|Adjustable Modern Swivel Bar Stool|HAY|89|28|-|Black,White",
            "Furniture|Solid Ash Wood Coat Hat Rack|Design Within Reach|68|32|-|Brown,Black"
    };

    static final String[] SEED_REVIEWS = {
            "101|Hamza R.|5|Exceptional studio sound quality, deep bass, and comfortable earcups.|2026-10-15",
            "101|Ayesha K.|5|Battery lasts over 30 hours on a single charge. Very impressed.|2026-10-16",
            "102|Usman M.|5|Blazing fast performance for rendering and gaming. Sleek chassis.|2026-10-14",
            "106|Bilal S.|5|Extremely soft cotton fleece and perfect tailored fit.|2026-10-17",
            "111|Zain A.|5|Featherlight running sneakers with responsive cushioning.|2026-10-18",
            "116|Danyal H.|5|Timeless minimalist dial and robust stainless steel construction.|2026-10-19"
    };

    public static void main(String[] a) throws Exception {
        Files.createDirectories(DIR);
        Files.createDirectories(RCP);
        load();
        HttpServer s;
        try { s = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0); }
        catch (BindException e) { s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        s.createContext("/", Main::route);
        s.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        s.start();
        String url = "http://localhost:" + s.getAddress().getPort();
        System.out.println("SwiftMart running at " + url);
        if (a.length == 0 || !a[0].equals("--no-browser")) open(url);

        // Native Java Swing Desktop Admin & Control Panel with Embedded HTML
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> launchSwingAdminGUI(url));
        }

        Thread.currentThread().join();
    }

    static void open(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        // 1. Try standard Desktop browse (opens user's default browser on any OS)
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) { }

        // 2. Platform-specific default browser openers
        try {
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
                return;
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
                return;
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", url).start();
                return;
            }
        } catch (Exception ignored) { }

        // 3. Fallback Windows explorer shell invocation
        try {
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", url).start();
                return;
            }
        } catch (Exception ignored) { }

        System.out.println("Please open " + url + " in your browser.");
    }

    static void load() throws IOException {
        inv.clear();
        if (Files.exists(INV)) {
            for (String l : Files.readAllLines(INV, StandardCharsets.UTF_8)) {
                if (l.trim().isEmpty()) continue;
                try {
                    String[] t = l.split("\\|", -1);
                    String img = t.length > 8 ? t[8] : "-";
                    inv.add(new GenericProduct(Integer.parseInt(t[0]), t[1], t[2], t[3], t[4], t[5], t[6], t[7], img));
                } catch (Exception e) { System.err.println("Skipped bad line: " + l); }
            }
        }
        // If inventory is empty or has fewer than the full 135 seed catalog items, reload full catalog
        if (inv.size() < SEED.length) {
            inv.clear();
            int id = 101;
            for (String s : SEED) {
                String[] t = s.split("\\|");
                try { inv.add(new GenericProduct(id++, t[0], t[1], t[2], t[3], t[4], t[5], t[6], "-")); } catch (StoreException ignored) { }
            }
            save();
        }

        if (Files.exists(REV)) {
            for (String l : Files.readAllLines(REV, StandardCharsets.UTF_8)) {
                if (!l.trim().isEmpty()) revList.add(l.trim());
            }
        }
        if (revList.isEmpty()) {
            for (String r : SEED_REVIEWS) revList.add(r);
            saveReviews();
        }
    }

    static synchronized void save() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Product p : inv) sb.append(p.toLine()).append("\n");
        writeStr(INV, sb.toString());
    }

    static synchronized void saveReviews() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : revList) sb.append(r).append("\n");
        writeStr(REV, sb.toString());
    }

    static Product find(int id) { for (Product p : inv) if (p.getId() == id) return p; return null; }

    // ---------- Web Routing ----------
    static void route(HttpExchange x) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(x.getRequestMethod())) {
                x.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                x.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                x.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Admin");
                x.sendResponseHeaders(204, -1);
                return;
            }

            String p = x.getRequestURI().getPath();
            String body = new String(readAll(x.getRequestBody()), StandardCharsets.UTF_8);
            Map<String, String> f = form(body);
            if (!p.startsWith("/api/")) { file(x, p); return; }

            // Admin endpoints require authentication header
            if (p.startsWith("/api/admin") && !"admin123".equals(x.getRequestHeaders().getFirst("X-Admin"))) {
                send(x, 401, JSON, "{\"error\":\"Unauthorized. Incorrect admin credentials.\"}"); return;
            }

            switch (p) {
                case "/api/ping": send(x, 200, JSON, "{}"); break;
                case "/api/bye": send(x, 200, JSON, "{}"); break;
                case "/api/products": send(x, 200, JSON, list()); break;
                case "/api/geocode": send(x, 200, JSON, geocode(queryParam(x, "q"), queryParam(x, "vb"), "1".equals(queryParam(x, "bounded")))); break;
                case "/api/checkout": send(x, 200, JSON, checkout(f)); break;
                case "/api/receipt": downloadReceipt(x); break;
                case "/api/groq": proxyGroq(x, body); break;

                // Reviews API
                case "/api/reviews": handleReviews(x, f); break;

                // Admin API & File Handling
                case "/api/admin/add": send(x, 200, JSON, add(f)); break;
                case "/api/admin/delete": send(x, 200, JSON, remove(Integer.parseInt(g(f, "id")))); break;
                case "/api/admin/update-image": send(x, 200, JSON, updateImage(f)); break;
                case "/api/admin/delete-review": send(x, 200, JSON, deleteReview(f)); break;
                case "/api/admin/orders": send(x, 200, "text/plain; charset=utf-8", Files.exists(ORD) ? readStr(ORD) : "No orders yet."); break;
                case "/api/admin/download-audit": downloadAudit(x); break;
                case "/api/admin/export-stock": exportStock(x); break;
                case "/api/admin/import-stock": importStockHandler(x, body); break;
                case "/api/admin/receipts": listReceipts(x); break;

                default: send(x, 404, JSON, "{\"error\":\"Endpoint not found.\"}");
            }
        } catch (StoreException e) {
            send(x, 400, JSON, "{\"field\":\"" + esc(e.field) + "\",\"error\":\"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            send(x, 500, JSON, "{\"error\":\"Server error: " + esc(String.valueOf(e.getMessage())) + "\"}");
        }
    }

    static synchronized String list() {
        StringBuilder b = new StringBuilder("[");
        for (Product p : inv) b.append(b.length() > 1 ? "," : "").append(p.json());
        return b.append("]").toString();
    }

    static synchronized String add(Map<String, String> f) throws StoreException, IOException {
        int id = 101;
        for (Product p : inv) id = Math.max(id, p.getId() + 1);
        String img = g(f, "image");
        inv.add(new GenericProduct(id, g(f, "cat"), g(f, "name"), g(f, "brand"), g(f, "price"), g(f, "stock"), g(f, "sizes"), g(f, "colors"), img));
        save();
        return "{\"ok\":true,\"id\":" + id + "}";
    }

    static synchronized String remove(int id) throws IOException {
        inv.removeIf(p -> p.getId() == id);
        save();
        return "{\"ok\":true}";
    }

    // Admin option to change a product's image
    static synchronized String updateImage(Map<String, String> f) throws StoreException, IOException {
        int id = Integer.parseInt(g(f, "id"));
        String newImg = g(f, "image");
        Product p = find(id);
        if (p == null) throw new StoreException("image", "Product not found.");
        p.setImage(newImg);
        save();
        return "{\"ok\":true,\"id\":" + id + ",\"img\":\"" + esc(p.getImage()) + "\"}";
    }

    // Admin option to remove a customer review
    static synchronized String deleteReview(Map<String, String> f) throws StoreException, IOException {
        String idStr = g(f, "id");
        String name = g(f, "name");
        String date = g(f, "date");
        String comment = g(f, "comment");

        int targetId = 0;
        try { targetId = Integer.parseInt(idStr); } catch (Exception ignored) {}

        boolean removed = false;
        for (int i = 0; i < revList.size(); i++) {
            String r = revList.get(i);
            String[] t = r.split("\\|", 5);
            if (t.length >= 5) {
                int curId = 0;
                try { curId = Integer.parseInt(t[0].trim()); } catch (Exception ignored) {}
                if (curId == targetId && t[1].trim().equalsIgnoreCase(name.trim()) && t[4].trim().equals(date.trim())) {
                    revList.remove(i);
                    removed = true;
                    break;
                }
            }
        }
        if (!removed && !comment.isEmpty()) {
            for (int i = 0; i < revList.size(); i++) {
                if (revList.get(i).contains(comment)) {
                    revList.remove(i);
                    removed = true;
                    break;
                }
            }
        }
        if (!removed && targetId > 0) {
            for (int i = 0; i < revList.size(); i++) {
                if (revList.get(i).startsWith(targetId + "|")) {
                    revList.remove(i);
                    removed = true;
                    break;
                }
            }
        }
        if (removed) {
            saveReviews();
            return "{\"ok\":true}";
        }
        throw new StoreException("review", "Review not found or already deleted.");
    }
    // Delivery locations (Pakistan only): Country -> City -> Areas. Keep in sync with ADDRESS_DATA in app.js.
    static final Map<String, Map<String, List<String>>> ADDRESS_DATA = new LinkedHashMap<>();
    static final Map<String, String> PHONE_RE = new LinkedHashMap<>();
    // City centers used to verify that the map pin is near the selected city
    static final Map<String, double[]> CITY_CENTERS = new HashMap<>();
    static final double MAX_PIN_KM = 60.0;
    static {
        Map<String, List<String>> pk = new LinkedHashMap<>();
        pk.put("Karachi", Arrays.asList("Clifton", "DHA", "Gulshan-e-Iqbal", "Gulistan-e-Johar", "North Nazimabad", "Nazimabad", "Saddar", "PECHS", "Bahadurabad", "Korangi", "Malir", "Federal B Area"));
        pk.put("Lahore", Arrays.asList("Gulberg", "DHA", "Johar Town", "Model Town", "Bahria Town", "Iqbal Town", "Cantt", "Faisal Town", "Wapda Town"));
        pk.put("Islamabad", Arrays.asList("F-6", "F-7", "F-8", "F-10", "F-11", "G-9", "G-10", "G-11", "I-8", "E-11", "Blue Area", "DHA", "Bahria Town"));
        pk.put("Rawalpindi", Arrays.asList("Saddar", "Satellite Town", "Bahria Town", "Chaklala", "Westridge", "Commercial Market"));
        pk.put("Faisalabad", Arrays.asList("Peoples Colony", "D Ground", "Madina Town", "Samanabad", "Gulberg"));
        pk.put("Peshawar", Arrays.asList("University Town", "Hayatabad", "Saddar", "Cantt"));
        pk.put("Multan", Arrays.asList("Cantt", "Gulgasht Colony", "Shah Rukn-e-Alam Colony"));
        pk.put("Quetta", Arrays.asList("Satellite Town", "Jinnah Town", "Cantt"));
        ADDRESS_DATA.put("Pakistan", pk);
        PHONE_RE.put("Pakistan", "^(\\+92|0)?3[0-9]{9}$");
        CITY_CENTERS.put("Karachi", new double[]{24.8607, 67.0011});
        CITY_CENTERS.put("Lahore", new double[]{31.5204, 74.3587});
        CITY_CENTERS.put("Islamabad", new double[]{33.6844, 73.0479});
        CITY_CENTERS.put("Rawalpindi", new double[]{33.5651, 73.0169});
        CITY_CENTERS.put("Faisalabad", new double[]{31.4504, 73.135});
        CITY_CENTERS.put("Peshawar", new double[]{34.0151, 71.5249});
        CITY_CENTERS.put("Multan", new double[]{30.1978, 71.4697});
        CITY_CENTERS.put("Quetta", new double[]{30.1798, 66.975});
    }

    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.asin(Math.sqrt(a));
    }
    // ---------- Checkout with Strict Validations & Receipt File Creation ----------
    static synchronized String checkout(Map<String, String> f) throws StoreException, IOException {
        String n = g(f, "name"), ph = g(f, "phone"), em = g(f, "email"), ad = g(f, "address"), items = g(f, "items");

        // 1. Name: only alphabets and spaces allowed, 3-50 chars
        if (!n.matches("^[a-zA-Z\\s]{3,50}$")) {
            throw new StoreException("name", "Name must contain only alphabets and spaces (no numbers or symbols).");
        }

        // 2. Phone: must match the mobile format of the selected country
        String cleanPhone = ph.replaceAll("[\\s\\-]", "");
        String phoneCountry = ad.contains(",") ? ad.substring(ad.lastIndexOf(',') + 1).trim() : "";
        String phoneRe = PHONE_RE.get(phoneCountry);
        if (phoneRe != null && !cleanPhone.matches(phoneRe)) {
            throw new StoreException("phone", "Enter a valid " + phoneCountry + " mobile number.");
        }

        // 3. Email validation (Gmail only)
        if (!em.matches("(?i)^[\\w.+-]+@gmail\\.com$")) {
            throw new StoreException("email", "Email must be a valid @gmail.com address.");
        }

        // 4. Address validation (house, area, city, country - must come from the fixed lists)
        String[] parts = ad.split(",", -1);
        if (parts.length != 4) {
            throw new StoreException("address", "Select country, city and area, and enter your house no.");
        }
        String houseNo = parts[0].trim(), areaName = parts[1].trim(), cityName = parts[2].trim(), countryName = parts[3].trim();
        Map<String, List<String>> cityMap = ADDRESS_DATA.get(countryName);
        if (cityMap == null || !cityMap.containsKey(cityName) || !cityMap.get(cityName).contains(areaName)) {
            throw new StoreException("address", "Please select a valid country, city and area from the list.");
        }
        if (houseNo.length() < 3 || !houseNo.matches(".*\\d.*") || !houseNo.matches("^[A-Za-z0-9\\s#./-]+$")) {
            throw new StoreException("address", "House No must include a number (e.g. House 14 Street 5).");
        }

        // 4b. Delivery pin: must be a valid coordinate inside Pakistan and near the selected city
        double pinLat, pinLng;
        try {
            pinLat = Double.parseDouble(g(f, "lat"));
            pinLng = Double.parseDouble(g(f, "lng"));
        } catch (NumberFormatException e) {
            throw new StoreException("pin", "Please drop a pin on the map to mark your exact delivery location.");
        }
        if (pinLat < 23.5 || pinLat > 37.2 || pinLng < 60.8 || pinLng > 77.9) {
            throw new StoreException("pin", "The pin must be inside Pakistan.");
        }
        double[] cityCenter = CITY_CENTERS.get(cityName);
        if (cityCenter == null || distanceKm(pinLat, pinLng, cityCenter[0], cityCenter[1]) > MAX_PIN_KM) {
            throw new StoreException("pin", "Your pin is too far from " + cityName + ". Move the pin or change the city.");
        }

        // 4c. Landmark / delivery note (optional, max 120 characters, no line breaks)
        String note = g(f, "note").replaceAll("[\\r\\n|\\p{Cntrl}]", " ").trim();
        if (note.length() > 120) {
            throw new StoreException("note", "Delivery note must be 120 characters or fewer.");
        }

        // 5. Cart items validation
        if (items.isEmpty()) {
            throw new StoreException("cart", "Your cart is empty.");
        }

        List<Object[]> lines = new ArrayList<>();
        Map<Integer, Integer> need = new HashMap<>();
        double total = 0;
        for (String it : items.split(";")) {
            if (it.trim().isEmpty()) continue;
            String[] t = it.split(":", 4);
            Product p = find(Integer.parseInt(t[0]));
            int q = Integer.parseInt(t[1]);
            if (p == null || q < 1) throw new StoreException("cart", "An item in your cart is no longer available.");
            need.merge(p.getId(), q, Integer::sum);
            if (need.get(p.getId()) > p.getStock()) throw new StoreException("cart", "Not enough stock for " + p.getName() + ".");
            lines.add(new Object[]{p, q, t.length > 2 ? t[2] : "-", t.length > 3 ? t[3] : "-"});
            total += p.getFinalPrice() * q;
        }

        for (Object[] l : lines) ((Product) l[0]).reduceStock((Integer) l[1]);

        // 8-digit random ID (hard to guess) and never reuse an existing receipt file
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        String id;
        do { id = "ORD-" + (10000000 + rnd.nextInt(90000000)); } while (Files.exists(RCP.resolve(id + ".txt")));
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder r = new StringBuilder();
        r.append("============================================================\n");
        r.append("                SWIFTMART OFFICIAL RECEIPT                  \n");
        r.append("============================================================\n");
        r.append("Order ID    : ").append(id).append("\n");
        r.append("Date & Time : ").append(dateStr).append("\n");
        r.append("Customer    : ").append(n).append("\n");
        r.append("Phone       : ").append(ph).append("\n");
        r.append("Email       : ").append(em).append("\n");
        r.append("Address     : ").append(ad).append("\n");
        r.append("Payment     : Cash on Delivery (pay the rider when the order arrives)\n");
        r.append("Landmark    : ").append(note.isEmpty() ? "-" : note).append("\n");
        r.append("Map Pin     : ").append(String.format(Locale.US, "%.6f, %.6f", pinLat, pinLng)).append("\n");
        r.append("Map Link    : ").append(String.format(Locale.US, "https://www.google.com/maps?q=%.6f,%.6f", pinLat, pinLng)).append("\n");
        r.append("------------------------------------------------------------\n");
        r.append(String.format(Locale.US, "%-24s %-6s %-8s %-8s %10s%n", "Item", "Qty", "Size", "Color", "Subtotal"));
        r.append("------------------------------------------------------------\n");
        for (Object[] l : lines) {
            Product p = (Product) l[0];
            int q = (Integer) l[1];
            String sz = (String) l[2];
            String col = (String) l[3];
            double sub = p.getFinalPrice() * q;
            r.append(String.format(Locale.US, "%-24s x%-5d %-8s %-8s $%9.2f%n", p.getName(), q, sz, col, sub));
        }
        r.append("------------------------------------------------------------\n");
        r.append(String.format(Locale.US, "TOTAL DUE ON DELIVERY:                          $%9.2f%n", total));
        r.append("============================================================\n");
        r.append("    Thank you for shopping with SwiftMart! Keep this receipt. \n");
        r.append("============================================================\n\n");

        Files.createDirectories(RCP);
        Path rcFile = RCP.resolve(id + ".txt");
        writeStr(rcFile, r.toString());
        writeStr(ORD, r.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        save();

        return String.format(Locale.US, "{\"ok\":true,\"id\":\"%s\",\"total\":\"%.2f\",\"date\":\"%s\"}", id, total, dateStr);
    }

    static void downloadReceipt(HttpExchange x) throws IOException {
        String q = x.getRequestURI().getQuery();
        String ordId = "";
        if (q != null) {
            for (String pair : q.split("&")) {
                String[] kv = pair.split("=");
                if (kv[0].equals("id") && kv.length > 1) ordId = dec(kv[1]);
            }
        }
        Path rf = RCP.resolve(ordId + ".txt").normalize();
        if (!ordId.isEmpty() && rf.startsWith(RCP) && Files.exists(rf)) {
            x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"Receipt-" + ordId + ".txt\"");
            send(x, 200, "text/plain; charset=utf-8", readStr(rf));
        } else {
            send(x, 404, "text/plain", "Receipt file not found.");
        }
    }

    // Proxy endpoint for Groq AI to guarantee connectivity across all environments
    static void proxyGroq(HttpExchange x, String body) throws IOException {
        if (!"POST".equalsIgnoreCase(x.getRequestMethod())) {
            send(x, 405, "text/plain", "Method Not Allowed");
            return;
        }
        String auth = x.getRequestHeaders().getFirst("Authorization");
        if (auth == null || auth.trim().isEmpty() || auth.trim().equals("Bearer")) {
            auth = "Bearer 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";
        }
        URL u = new URL("https://api.groq.com/openai/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", auth);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(25000);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        byte[] resp = is != null ? readAll(is) : new byte[0];
        send(x, code, JSON, resp);
    }

    // ---- Review authenticity helpers ----
    static String normName(String s) { return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }

    static boolean reviewClaimed(String claim) throws IOException {
        if (!Files.exists(RCL)) return false;
        for (String l : Files.readAllLines(RCL, StandardCharsets.UTF_8)) if (l.trim().equals(claim)) return true;
        return false;
    }

    /** Confirms that orderId exists, was placed by `name`, and contains product `p`. Returns the buyer's name from the receipt. */
    static String verifyReviewer(String orderId, String name, Product p) throws StoreException, IOException {
        if (orderId.isEmpty()) {
            throw new StoreException("orderId", "Order ID required. Only customers who bought this product can review it.");
        }
        if (!orderId.matches("^ORD-\\d{1,12}$")) {
            throw new StoreException("orderId", "Invalid Order ID format.");
        }
        Path rf = RCP.resolve(orderId + ".txt").normalize();
        if (!rf.startsWith(RCP) || !Files.exists(rf)) {
            throw new StoreException("orderId", "No order found with this Order ID.");
        }
        String receipt = readStr(rf);

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^Customer\\s*:\\s*(.+?)\\s*$").matcher(receipt);
        String buyer = m.find() ? m.group(1) : "";
        if (buyer.isEmpty() || !normName(buyer).equals(normName(name))) {
            throw new StoreException("name", "This name does not match the customer on that order. Only the person who ordered can review.");
        }

        // Match the product's line in the receipt's item table (not just any text that contains the name)
        boolean hasItem = java.util.regex.Pattern
                .compile("(?m)^" + java.util.regex.Pattern.quote(p.getName()) + "\\s+x\\d+")
                .matcher(receipt).find();
        if (!hasItem) {
            throw new StoreException("review", "This order does not contain this product.");
        }
        return buyer;
    }

    static void handleReviews(HttpExchange x, Map<String, String> f) throws IOException, StoreException {
        if ("POST".equalsIgnoreCase(x.getRequestMethod())) {
            String idStr = g(f, "id");
            String name = g(f, "name");
            String starsStr = g(f, "stars");
            String comment = g(f, "comment");

            if (idStr.isEmpty()) throw new StoreException("review", "Product ID required.");
            if (!name.matches("^[a-zA-Z\\s]{2,40}$")) throw new StoreException("name", "Name must contain only alphabets.");
            int stars;
            try {
                stars = Integer.parseInt(starsStr);
                if (stars < 1 || stars > 5) throw new NumberFormatException();
            } catch (Exception e) {
                throw new StoreException("stars", "Rating must be between 1 and 5 stars.");
            }
            if (comment.length() < 3) throw new StoreException("comment", "Please write at least 3 characters.");

            // Verified purchaser validation (enforced on the server, so it cannot be bypassed from the browser).
            // The Order ID must exist, belong to the SAME customer name, and contain this product.
            int prodIdVal;
            try { prodIdVal = Integer.parseInt(idStr); }
            catch (NumberFormatException e) { throw new StoreException("review", "Invalid product ID."); }
            Product targetP = find(prodIdVal);
            if (targetP == null) throw new StoreException("review", "Product not found.");

            String orderId = g(f, "orderId").toUpperCase(Locale.ROOT);
            String buyerName = verifyReviewer(orderId, name, targetP);

            synchronized (Main.class) {
                String claim = orderId + ":" + targetP.getId();
                if (reviewClaimed(claim)) {
                    throw new StoreException("review", "You have already reviewed this product for this order.");
                }
                String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                // Store the name exactly as it appears on the order, not whatever was typed
                String entry = targetP.getId() + "|" + esc(buyerName) + "|" + stars + "|" + esc(comment).replace("|", " ") + "|" + date;
                revList.add(0, entry);
                saveReviews();
                writeStr(RCL, claim + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            send(x, 200, JSON, "{\"ok\":true}");
        } else {
            String q = x.getRequestURI().getQuery();
            int prodId = 0;
            if (q != null && q.contains("id=")) {
                for (String pair : q.split("&")) {
                    String[] kv = pair.split("=");
                    if (kv[0].equals("id") && kv.length > 1) prodId = Integer.parseInt(kv[1]);
                }
            }
            StringBuilder b = new StringBuilder("[");
            for (String r : revList) {
                String[] t = r.split("\\|", 5);
                if (t.length >= 5) {
                    int pId = Integer.parseInt(t[0].trim());
                    if (prodId == 0 || pId == prodId) {
                        b.append(b.length() > 1 ? "," : "");
                        b.append(String.format(Locale.US, "{\"productId\":%d,\"name\":\"%s\",\"stars\":%s,\"comment\":\"%s\",\"date\":\"%s\"}",
                                pId, esc(t[1]), t[2], esc(t[3]), esc(t[4])));
                    }
                }
            }
            b.append("]");
            send(x, 200, JSON, b.toString());
        }
    }

    static synchronized void exportStock(HttpExchange x) throws IOException {
        StringBuilder csv = new StringBuilder("ID,Category,Name,Brand,OriginalPrice,DiscountedPrice,Stock,Sizes,Colors,Image\n");
        for (Product p : inv) {
            csv.append(String.format(Locale.US, "%d,\"%s\",\"%s\",\"%s\",%.2f,%.2f,%d,\"%s\",\"%s\",\"%s\"\n",
                    p.getId(), p.getCategory(), p.getName(), p.getBrand(), p.getPrice(), p.getFinalPrice(), p.getStock(), p.getSizes(), p.getColors(), p.getImage()));
        }
        x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"swiftmart_stock_export.csv\"");
        send(x, 200, "text/csv; charset=utf-8", csv.toString());
    }

    static synchronized void importStockHandler(HttpExchange x, String body) throws IOException {
        int count = 0;
        String[] lines = body.split("\r?\n");
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty() || l.toLowerCase().startsWith("id,") || l.toLowerCase().startsWith("id|")) continue;
            String[] t = l.contains("|") ? l.split("\\|", -1) : parseCsvLine(l);
            if (t.length >= 6) {
                try {
                    int id = Integer.parseInt(t[0].trim());
                    String cat = t[1].trim();
                    String name = t[2].trim();
                    String brand = t[3].trim();
                    String price = t[4].trim();
                    String stock = t[5].trim();
                    String sizes = t.length > 6 ? t[6].trim() : "-";
                    String colors = t.length > 7 ? t[7].trim() : "-";
                    String img = t.length > 8 ? t[8].trim() : "-";

                    Product existing = find(id);
                    if (existing != null) {
                        inv.remove(existing);
                    }
                    inv.add(new GenericProduct(id, cat, name, brand, price, stock, sizes, colors, img));
                    count++;
                } catch (Exception ignored) { }
            }
        }
        save();
        send(x, 200, JSON, "{\"ok\":true,\"imported\":" + count + "}");
    }

    static String[] parseCsvLine(String line) {
        List<String> list = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) {
                list.add(cur.toString().trim());
                cur = new StringBuilder();
            } else {
                cur.append(c);
            }
        }
        list.add(cur.toString().trim());
        return list.toArray(new String[0]);
    }

    static void downloadAudit(HttpExchange x) throws IOException {
        if (Files.exists(ORD)) {
            x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"order_audit.txt\"");
            send(x, 200, "text/plain; charset=utf-8", readStr(ORD));
        } else {
            send(x, 404, "text/plain", "No audit log file exists yet.");
        }
    }

    static void listReceipts(HttpExchange x) throws IOException {
        List<String> list = new ArrayList<>();
        if (Files.exists(RCP)) {
            try (Stream<Path> stream = Files.list(RCP)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString().replace(".txt", ""))
                        .sorted(Comparator.reverseOrder())
                        .forEach(id -> list.add("\"" + id + "\""));
            }
        }
        send(x, 200, JSON, "[" + String.join(",", list) + "]");
    }

    // ---------- Map place search (proxy to OpenStreetMap Nominatim) ----------
    // Done on the server so we can send the User-Agent Nominatim requires,
    // keep to its limit of 1 request per second, and cache repeated searches.
    static final Map<String, String> GEO_CACHE = new LinkedHashMap<>();
    static final Object GEO_LOCK = new Object();
    static long lastGeoCall = 0;

    static String queryParam(HttpExchange x, String key) {
        String qs = x.getRequestURI().getRawQuery();
        if (qs == null) return "";
        for (String kv : qs.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(key)) return dec(kv.substring(i + 1));
        }
        return "";
    }

    // vb = "minLng,maxLat,maxLng,minLat" (the area to search in). bounded = only return places inside that box.
    static String geocode(String q, String vb, boolean bounded) throws IOException {
        q = q.trim();
        if (q.length() < 3) return "[]";
        if (q.length() > 120) q = q.substring(0, 120);
        if (!vb.matches("-?\\d+(\\.\\d+)?(,-?\\d+(\\.\\d+)?){3}")) { vb = ""; bounded = false; }
        String key = q.toLowerCase() + "|" + vb + "|" + bounded;
        synchronized (GEO_LOCK) {
            String hit = GEO_CACHE.get(key);
            if (hit != null) return hit;
            long wait = 1100 - (System.currentTimeMillis() - lastGeoCall);
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            lastGeoCall = System.currentTimeMillis();
            String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=pk&accept-language=en&q="
                    + URLEncoder.encode(q, "UTF-8");
            if (!vb.isEmpty()) url += "&viewbox=" + vb + (bounded ? "&bounded=1" : "");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "SwiftMart-StudentProject/1.0");
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) throw new IOException("Place search returned HTTP " + c.getResponseCode());
            String raw = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
            if (GEO_CACHE.size() >= 200) GEO_CACHE.remove(GEO_CACHE.keySet().iterator().next());
            GEO_CACHE.put(key, raw);
            return raw;
        }
    }

    // ---------- Helpers & Static File Serving ----------
    static String g(Map<String, String> f, String k) { return f.getOrDefault(k, "").trim(); }
    static Map<String, String> form(String body) {
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(dec(kv.substring(0, i)), dec(kv.substring(i + 1)));
        }
        return m;
    }
    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }

    static void file(HttpExchange x, String p) throws IOException {
        String sub = p.equals("/") ? "index.html" : (p.startsWith("/") ? p.substring(1) : p);
        if (sub.equals("admin") || sub.equals("admin/")) sub = "admin.html";
        if (sub.equals("login") || sub.equals("login/")) sub = "login.html";

        byte[] bytes = null;
        Path f = WEB.resolve(sub).normalize();
        if (f.startsWith(WEB) && Files.isRegularFile(f)) {
            bytes = Files.readAllBytes(f);
        } else {
            InputStream in = Main.class.getResourceAsStream("/" + sub);
            if (in != null) {
                try { bytes = readAll(in); } finally { in.close(); }
            }
        }
        // Embedded HTML fallbacks directly from Main.java
        if (bytes == null) {
            if (sub.equalsIgnoreCase("index.html")) {
                bytes = INDEX_HTML_FALLBACK.getBytes(StandardCharsets.UTF_8);
            } else if (sub.equalsIgnoreCase("admin.html")) {
                bytes = ADMIN_HTML_FALLBACK.getBytes(StandardCharsets.UTF_8);
            } else if (sub.equalsIgnoreCase("login.html")) {
                bytes = LOGIN_HTML_FALLBACK.getBytes(StandardCharsets.UTF_8);
            }
        }
        if (bytes == null) { send(x, 404, "text/plain", "Not found: " + sub); return; }

        String n = sub.toLowerCase();
        String t = n.endsWith(".html") ? "text/html; charset=utf-8" :
                n.endsWith(".css") ? "text/css; charset=utf-8" :
                        n.endsWith(".js") ? "text/javascript; charset=utf-8" :
                                n.endsWith(".svg") ? "image/svg+xml" :
                                        n.endsWith(".json") ? "application/json; charset=utf-8" : "application/octet-stream";
        send(x, 200, t, bytes);
    }

    static void send(HttpExchange x, int c, String t, String s) throws IOException { send(x, c, t, s.getBytes(StandardCharsets.UTF_8)); }
    static void send(HttpExchange x, int c, String t, byte[] b) throws IOException {
        x.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        x.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        x.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Admin");
        x.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        x.getResponseHeaders().set("Pragma", "no-cache");
        x.getResponseHeaders().set("Expires", "0");
        x.getResponseHeaders().set("Content-Type", t);
        x.sendResponseHeaders(c, b.length);
        try (OutputStream o = x.getResponseBody()) { o.write(b); }
    }

    // =========================================================================
    // EMBEDDED HTML SYSTEM & FALLBACK TEMPLATES IN MAIN.JAVA
    // =========================================================================

    static final String INDEX_HTML_FALLBACK = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>SwiftMart</title>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>body{font-family:sans-serif;margin:0;padding:24px;background:#fff;color:#000}.bar{display:flex;justify-content:space-between;align-items:center;padding-bottom:16px;border-bottom:2px solid #000}select{padding:6px 12px;font-weight:bold}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:20px;margin-top:24px}.card{border:1.5px solid #ddd;border-radius:12px;padding:16px;box-shadow:0 2px 6px rgba(0,0,0,0.05)}</style></head>"
            + "<body><div class='bar'><h2 style='margin:0;letter-spacing:2px'>SWIFTMART</h2><div><select id='curr' onchange='chg(this.value)'><option value='PKR' selected>PKR (Rs.)</option><option value='USD'>USD ($)</option><option value='GBP'>GBP (£)</option><option value='EUR'>EUR (€)</option></select> <a href='/admin' style='margin-left:14px;font-weight:bold;color:#000'>Staff Portal &rarr;</a></div></div>"
            + "<div id='catalog' class='grid'></div>"
            + "<script>let rate=278.5;let sym='Rs. ';function chg(c){if(c==='USD'){rate=1;sym='$';}else if(c==='GBP'){rate=0.79;sym='£';}else if(c==='EUR'){rate=0.92;sym='€';}else{rate=278.5;sym='Rs. ';}else{rate=1;sym='$';}render();}let prods=[];fetch('/api/products').then(r=>r.json()).then(d=>{prods=d;render();});function render(){document.getElementById('catalog').innerHTML=prods.map(p=>`<div class='card'><b>${p.name}</b><br><small style='color:#666'>${p.cat} - ${p.brand}</small><br><br><b style='font-size:18px'>${sym}${Math.round(p.final*rate).toLocaleString()}</b> <small style='color:green;font-weight:bold'>(5% instant discount)</small><br><small style='color:#777'>Stock: ${p.stock} units</small></div>`).join('');}</script></body></html>";

    static final String ADMIN_HTML_FALLBACK = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>SwiftMart Staff Portal</title>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>body{font-family:sans-serif;margin:0;padding:24px;background:#fff;color:#000}.bar{display:flex;justify-content:space-between;align-items:center;padding-bottom:16px;border-bottom:2px solid #000}table{width:100%;border-collapse:collapse;margin-top:20px}th,td{border:1px solid #ddd;padding:10px;text-align:left}th{background:#f5f5f5}</style></head>"
            + "<body><div class='bar'><h2 style='margin:0;letter-spacing:1px'>SWIFTMART STAFF PORTAL</h2><a href='/' style='color:#000;font-weight:bold'>&larr; Back to Storefront</a></div>"
            + "<div id='content'><h3>Inventory &amp; Reviews Overview</h3><p>Loading catalog data...</p></div>"
            + "<script>fetch('/api/products').then(r=>r.json()).then(d=>{document.getElementById('content').innerHTML='<table><thead><tr><th>ID</th><th>Name</th><th>Category</th><th>Brand</th><th>Price</th><th>Stock</th></tr></thead><tbody>'+d.map(p=>`<tr><td>#${p.id}</td><td><b>${p.name}</b></td><td>${p.cat}</td><td>${p.brand}</td><td>$${p.final.toFixed(2)}</td><td>${p.stock}</td></tr>`).join('')+'</tbody></table>';});</script></body></html>";

    static final String LOGIN_HTML_FALLBACK = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>SwiftMart Login</title>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;background:#fafafa;color:#000}.box{background:#fff;border:1.5px solid #ddd;border-radius:16px;padding:36px;max-width:380px;width:100%;box-shadow:0 4px 12px rgba(0,0,0,0.06)}input,button{width:100%;padding:12px;margin-top:12px;border-radius:8px;box-sizing:border-box}button{background:#000;color:#fff;font-weight:bold;cursor:pointer;border:none}</style></head>"
            + "<body><div class='box'><h2 style='margin-top:0'>SwiftMart Staff Portal</h2><p style='color:#666;font-size:13px'>Administrative Password: <code>admin123</code></p><input type='password' id='pwd' placeholder='Enter admin password'><button onclick=\"if(document.getElementById('pwd').value==='admin123'){sessionStorage.setItem('swiftmart_admin','admin123');location.href='/admin';}else{alert('Invalid password');}\">Sign In</button></div></body></html>";

    // =========================================================================
    // NATIVE JAVA SWING DESKTOP CONTROL PANEL IMPLEMENTATION
    // =========================================================================

    static void launchSwingAdminGUI(String serverUrl) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) { }

        JFrame frame = new JFrame("SwiftMart - Native Desktop Administration & Control Panel");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1080, 720);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();

        // --- TAB 1: System Overview & Live Server Dashboard (Swing HTML) ---
        JPanel pnlDash = new JPanel(new BorderLayout());
        JEditorPane dashPane = new JEditorPane();
        dashPane.setContentType("text/html");
        dashPane.setEditable(false);
        dashPane.setText(buildDashboardHtml(serverUrl));
        JScrollPane dashScroll = new JScrollPane(dashPane);
        pnlDash.add(dashScroll, BorderLayout.CENTER);

        JPanel pnlDashBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btnOpenStore = new JButton("Open Storefront in Browser");
        JButton btnOpenAdmin = new JButton("Open Web Staff Portal");
        JButton btnOpenReceipts = new JButton("Open Receipts Directory");
        JButton btnRefreshDash = new JButton("Refresh System Stats");

        btnOpenStore.addActionListener(e -> open(serverUrl));
        btnOpenAdmin.addActionListener(e -> open(serverUrl + "/admin"));
        btnOpenReceipts.addActionListener(e -> openFolder(RCP));
        btnRefreshDash.addActionListener(e -> dashPane.setText(buildDashboardHtml(serverUrl)));

        pnlDashBtns.add(btnRefreshDash);
        pnlDashBtns.add(btnOpenReceipts);
        pnlDashBtns.add(btnOpenAdmin);
        pnlDashBtns.add(btnOpenStore);
        pnlDash.add(pnlDashBtns, BorderLayout.SOUTH);
        tabs.addTab("System Dashboard", pnlDash);

        // --- TAB 2: Inventory Management (Swing JTable) ---
        JPanel pnlInv = new JPanel(new BorderLayout(5, 5));
        pnlInv.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[] invCols = {"ID", "Category", "Name", "Brand", "Price ($)", "Final ($)", "Stock", "Sizes", "Colors"};
        DefaultTableModel invModel = new DefaultTableModel(invCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        populateInvModel(invModel);

        JTable invTable = new JTable(invModel);
        invTable.setRowHeight(24);
        invTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> invSorter = new TableRowSorter<>(invModel);
        invTable.setRowSorter(invSorter);

        JPanel pnlInvTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        pnlInvTop.add(new JLabel("Filter Inventory:"));
        JTextField txtInvFilter = new JTextField(20);
        txtInvFilter.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                String text = txtInvFilter.getText().trim();
                if (text.isEmpty()) invSorter.setRowFilter(null);
                else invSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
            }
        });
        pnlInvTop.add(txtInvFilter);
        pnlInv.add(pnlInvTop, BorderLayout.NORTH);
        pnlInv.add(new JScrollPane(invTable), BorderLayout.CENTER);

        JPanel pnlInvBot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnRestock = new JButton("Restock (+25 Units)");
        JButton btnChangeImg = new JButton("Change Image URL");
        JButton btnDelProd = new JButton("Delete Product");
        JButton btnReloadInv = new JButton("Reload Table");

        btnRestock.addActionListener(e -> {
            int row = invTable.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a product row first."); return; }
            int id = (Integer) invTable.getValueAt(row, 0);
            Product p = find(id);
            if (p != null) {
                String input = JOptionPane.showInputDialog(frame, "Add stock units for \"" + p.getName() + "\":", "25");
                if (input != null && !input.trim().isEmpty()) {
                    try {
                        int addUnits = Integer.parseInt(input.trim());
                        p.setStock(p.getStock() + addUnits);
                        save();
                        populateInvModel(invModel);
                        dashPane.setText(buildDashboardHtml(serverUrl));
                        JOptionPane.showMessageDialog(frame, "Stock updated to " + p.getStock());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(frame, "Invalid number: " + ex.getMessage());
                    }
                }
            }
        });

        btnChangeImg.addActionListener(e -> {
            int row = invTable.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a product row first."); return; }
            int id = (Integer) invTable.getValueAt(row, 0);
            Product p = find(id);
            if (p != null) {
                String newUrl = JOptionPane.showInputDialog(frame, "Enter new Image URL for \"" + p.getName() + "\":", p.getImage());
                if (newUrl != null) {
                    try {
                        p.setImage(newUrl.trim());
                        save();
                        JOptionPane.showMessageDialog(frame, "Product image updated successfully.");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage());
                    }
                }
            }
        });

        btnDelProd.addActionListener(e -> {
            int row = invTable.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a product row first."); return; }
            int id = (Integer) invTable.getValueAt(row, 0);
            int conf = JOptionPane.showConfirmDialog(frame, "Delete product #" + id + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (conf == JOptionPane.YES_OPTION) {
                try {
                    remove(id);
                    populateInvModel(invModel);
                    dashPane.setText(buildDashboardHtml(serverUrl));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Delete error: " + ex.getMessage());
                }
            }
        });

        btnReloadInv.addActionListener(e -> populateInvModel(invModel));

        pnlInvBot.add(btnReloadInv);
        pnlInvBot.add(btnChangeImg);
        pnlInvBot.add(btnRestock);
        pnlInvBot.add(btnDelProd);
        pnlInv.add(pnlInvBot, BorderLayout.SOUTH);
        tabs.addTab("Inventory Management", pnlInv);

        // --- TAB 3: Customer Reviews Management (Swing JTable) ---
        JPanel pnlRev = new JPanel(new BorderLayout(5, 5));
        pnlRev.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[] revCols = {"Product ID", "Item Name", "Customer", "Rating", "Review Comment", "Date"};
        DefaultTableModel revModel = new DefaultTableModel(revCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        populateRevModel(revModel);

        JTable revTable = new JTable(revModel);
        revTable.setRowHeight(24);
        revTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> revSorter = new TableRowSorter<>(revModel);
        revTable.setRowSorter(revSorter);

        JPanel pnlRevTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        pnlRevTop.add(new JLabel("Filter Reviews:"));
        JTextField txtRevFilter = new JTextField(20);
        txtRevFilter.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                String text = txtRevFilter.getText().trim();
                if (text.isEmpty()) revSorter.setRowFilter(null);
                else revSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
            }
        });
        pnlRevTop.add(txtRevFilter);
        pnlRev.add(pnlRevTop, BorderLayout.NORTH);
        pnlRev.add(new JScrollPane(revTable), BorderLayout.CENTER);

        JPanel pnlRevBot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnDelRev = new JButton("Delete Selected Review");
        JButton btnAddRev = new JButton("Add Sample Review");
        JButton btnReloadRev = new JButton("Reload Reviews");

        btnDelRev.addActionListener(e -> {
            int row = revTable.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a review row first."); return; }
            int pId = (Integer) revTable.getValueAt(row, 0);
            String cust = (String) revTable.getValueAt(row, 2);
            String comm = (String) revTable.getValueAt(row, 4);
            int conf = JOptionPane.showConfirmDialog(frame, "Delete review by \"" + cust + "\" for Product #" + pId + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (conf == JOptionPane.YES_OPTION) {
                try {
                    Map<String, String> m = new HashMap<>();
                    m.put("id", String.valueOf(pId));
                    m.put("name", cust);
                    m.put("comment", comm);
                    deleteReview(m);
                    populateRevModel(revModel);
                    dashPane.setText(buildDashboardHtml(serverUrl));
                    JOptionPane.showMessageDialog(frame, "Review removed.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage());
                }
            }
        });

        btnAddRev.addActionListener(e -> {
            String pIdStr = JOptionPane.showInputDialog(frame, "Enter Product ID (e.g. 101):", "101");
            if (pIdStr == null || pIdStr.trim().isEmpty()) return;
            String name = JOptionPane.showInputDialog(frame, "Enter Customer Name:", "Ali Khan");
            if (name == null || name.trim().isEmpty()) return;
            String comment = JOptionPane.showInputDialog(frame, "Enter Review Comment:", "Great product quality and fast delivery!");
            if (comment == null || comment.trim().isEmpty()) return;
            String stars = JOptionPane.showInputDialog(frame, "Enter Rating (1-5):", "5");
            if (stars == null || stars.trim().isEmpty()) return;

            try {
                int id = Integer.parseInt(pIdStr.trim());
                int st = Math.max(1, Math.min(5, Integer.parseInt(stars.trim())));
                String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String entry = id + "|" + esc(name) + "|" + st + "|" + esc(comment).replace("|", " ") + "|" + date;
                revList.add(0, entry);
                saveReviews();
                populateRevModel(revModel);
                dashPane.setText(buildDashboardHtml(serverUrl));
                JOptionPane.showMessageDialog(frame, "Sample review added successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage());
            }
        });

        btnReloadRev.addActionListener(e -> populateRevModel(revModel));

        pnlRevBot.add(btnReloadRev);
        pnlRevBot.add(btnAddRev);
        pnlRevBot.add(btnDelRev);
        pnlRev.add(pnlRevBot, BorderLayout.SOUTH);
        tabs.addTab("Customer Reviews", pnlRev);

        // --- TAB 4: Embedded HTML Storefront Showcase (Swing JEditorPane) ---
        JPanel pnlStoreShowcase = new JPanel(new BorderLayout());
        JEditorPane storeShowcasePane = new JEditorPane();
        storeShowcasePane.setContentType("text/html");
        storeShowcasePane.setEditable(false);
        storeShowcasePane.setText(buildStorefrontHtml());
        pnlStoreShowcase.add(new JScrollPane(storeShowcasePane), BorderLayout.CENTER);
        tabs.addTab("HTML Storefront Showcase", pnlStoreShowcase);

        // --- TAB 5: Order Audit Log (Swing JTextArea) ---
        JPanel pnlAudit = new JPanel(new BorderLayout(5, 5));
        pnlAudit.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JTextArea auditArea = new JTextArea();
        auditArea.setEditable(false);
        auditArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        loadAuditArea(auditArea);
        pnlAudit.add(new JScrollPane(auditArea), BorderLayout.CENTER);

        JPanel pnlAuditBot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnReloadAudit = new JButton("Reload Audit Log");
        JButton btnOpenAuditFile = new JButton("Open Audit File in Editor");
        btnReloadAudit.addActionListener(e -> loadAuditArea(auditArea));
        btnOpenAuditFile.addActionListener(e -> openFile(ORD));
        pnlAuditBot.add(btnReloadAudit);
        pnlAuditBot.add(btnOpenAuditFile);
        pnlAudit.add(pnlAuditBot, BorderLayout.SOUTH);
        tabs.addTab("Order Audit Ledger", pnlAudit);

        frame.getContentPane().add(tabs);
        frame.setVisible(true);
    }

    static void populateInvModel(DefaultTableModel model) {
        model.setRowCount(0);
        for (Product p : inv) {
            model.addRow(new Object[]{
                    p.getId(), p.getCategory(), p.getName(), p.getBrand(),
                    String.format(Locale.US, "%.2f", p.getPrice()),
                    String.format(Locale.US, "%.2f", p.getFinalPrice()),
                    p.getStock(), p.getSizes(), p.getColors()
            });
        }
    }

    static void populateRevModel(DefaultTableModel model) {
        model.setRowCount(0);
        for (String r : revList) {
            String[] t = r.split("\\|", 5);
            if (t.length >= 5) {
                int pId = 0;
                try { pId = Integer.parseInt(t[0].trim()); } catch (Exception ignored) {}
                Product p = find(pId);
                String pName = p != null ? p.getName() : "Product #" + pId;
                int stars = 5;
                try { stars = Integer.parseInt(t[2].trim()); } catch (Exception ignored) {}
                String starsStr = "";
                for (int s = 0; s < stars; s++) starsStr += "\u2605";
                for (int s = stars; s < 5; s++) starsStr += "\u2606";
                model.addRow(new Object[]{pId, pName, t[1], starsStr + " (" + stars + "/5)", t[3], t[4]});
            }
        }
    }

    static void loadAuditArea(JTextArea area) {
        try {
            if (Files.exists(ORD)) {
                area.setText(readStr(ORD));
            } else {
                area.setText("No orders recorded yet. Complete a checkout transaction on the storefront to generate audit entries.");
            }
        } catch (Exception e) {
            area.setText("Error loading audit log: " + e.getMessage());
        }
    }

    static void openFolder(Path p) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(p.toFile());
                return;
            }
        } catch (Exception ignored) { }
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer.exe", p.toAbsolutePath().toString()).start();
            }
        } catch (Exception ignored) { }
    }

    static void openFile(Path p) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(p.toFile());
                return;
            }
        } catch (Exception ignored) { }
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("notepad.exe", p.toAbsolutePath().toString()).start();
            }
        } catch (Exception ignored) { }
    }

    static String buildDashboardHtml(String serverUrl) {
        int totalProducts = inv.size();
        int totalStock = 0;
        double totalVal = 0;
        for (Product p : inv) {
            totalStock += p.getStock();
            totalVal += p.getFinalPrice() * p.getStock();
        }
        int totalReviews = revList.size();
        double avgRating = 5.0;
        if (!revList.isEmpty()) {
            double sum = 0;
            for (String r : revList) {
                String[] t = r.split("\\|", 5);
                if (t.length >= 3) {
                    try { sum += Double.parseDouble(t[2].trim()); } catch (Exception ignored) { sum += 5; }
                }
            }
            avgRating = sum / totalReviews;
        }

        StringBuilder h = new StringBuilder();
        h.append("<html><body style='font-family:sans-serif;background-color:#ffffff;color:#111111;padding:15px;'>");
        h.append("<table width='100%' cellpadding='0' cellspacing='0' style='border-bottom:2px solid #000000;margin-bottom:15px;'>");
        h.append("<tr>");
        h.append("<td><h1 style='margin:0;font-size:22px;letter-spacing:1px;'>SWIFTMART DESKTOP CONTROL PANEL</h1>");
        h.append("<span style='font-size:12px;color:#555555;'>Pure Monochrome Luxury E-Commerce Engine &copy; 2026</span></td>");
        h.append("<td align='right'><span style='background-color:#000000;color:#ffffff;padding:5px 12px;font-size:12px;font-weight:bold;'>SERVER ONLINE &bull; PORT ").append(serverUrl.replaceAll(".*:", "")).append("</span></td>");
        h.append("</tr></table>");

        h.append("<table width='100%' cellpadding='10' cellspacing='8' style='margin-bottom:15px;'><tr>");
        h.append("<td bgcolor='#f7f7f7' align='center' style='border:1px solid #e2e2e2;'><div style='font-size:11px;color:#666;font-weight:bold;'>TOTAL CATALOG ITEMS</div><div style='font-size:24px;font-weight:bold;margin-top:4px;'>").append(totalProducts).append("</div></td>");
        h.append("<td bgcolor='#f7f7f7' align='center' style='border:1px solid #e2e2e2;'><div style='font-size:11px;color:#666;font-weight:bold;'>TOTAL UNITS IN STOCK</div><div style='font-size:24px;font-weight:bold;margin-top:4px;'>").append(totalStock).append("</div></td>");
        h.append("<td bgcolor='#f7f7f7' align='center' style='border:1px solid #e2e2e2;'><div style='font-size:11px;color:#666;font-weight:bold;'>CATALOG VALUATION</div><div style='font-size:24px;font-weight:bold;margin-top:4px;'>$").append(String.format(Locale.US, "%,.2f", totalVal)).append("</div></td>");
        h.append("<td bgcolor='#f7f7f7' align='center' style='border:1px solid #e2e2e2;'><div style='font-size:11px;color:#666;font-weight:bold;'>CUSTOMER REVIEWS</div><div style='font-size:24px;font-weight:bold;margin-top:4px;'>").append(totalReviews).append(" <span style='font-size:16px;color:#e67e22;'>(").append(String.format(Locale.US, "%.1f", avgRating)).append("★)</span></div></td>");
        h.append("</tr></table>");

        h.append("<table width='100%' cellpadding='8' cellspacing='0' style='border:1px solid #dddddd;background-color:#fafafa;margin-bottom:15px;'>");
        h.append("<tr><td colspan='2' bgcolor='#eeeeee' style='font-weight:bold;font-size:13px;border-bottom:1px solid #dddddd;'>SYSTEM ARCHITECTURE &amp; ENVIRONMENT</td></tr>");
        h.append("<tr><td width='30%' style='border-bottom:1px solid #eeeeee;'><b>Local HTTP Server</b></td><td style='border-bottom:1px solid #eeeeee;'><a href='").append(serverUrl).append("'>").append(serverUrl).append("</a></td></tr>");
        h.append("<tr><td style='border-bottom:1px solid #eeeeee;'><b>Web Admin Endpoint</b></td><td style='border-bottom:1px solid #eeeeee;'><a href='").append(serverUrl).append("/admin'>").append(serverUrl).append("/admin</a></td></tr>");
        h.append("<tr><td style='border-bottom:1px solid #eeeeee;'><b>Data Directory</b></td><td style='border-bottom:1px solid #eeeeee;'><code>").append(DIR.toAbsolutePath()).append("</code></td></tr>");
        h.append("<tr><td style='border-bottom:1px solid #eeeeee;'><b>Operating System</b></td><td style='border-bottom:1px solid #eeeeee;'>").append(System.getProperty("os.name")).append(" (").append(System.getProperty("os.arch")).append(")</td></tr>");
        h.append("<tr><td style='border-bottom:1px solid #eeeeee;'><b>Java SE Runtime</b></td><td style='border-bottom:1px solid #eeeeee;'>Java ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor")).append(")</td></tr>");
        h.append("<tr><td><b>Multi-Currency Engine</b></td><td>Active &bull; PKR (Rs.) [Default], USD ($), GBP (&pound;), EUR (&euro;) &bull; Groq AI Model: openai/gpt-oss-20b</td></tr>");
        h.append("</table>");

        h.append("<div style='background-color:#ffffff;border:1px solid #dddddd;padding:12px;'>");
        h.append("<h3 style='margin:0 0 8px 0;font-size:14px;'>Recent Activity &amp; System Integration Notes:</h3>");
        h.append("<ul style='margin:0;padding-left:20px;font-size:12.5px;color:#444444;'>");
        h.append("<li>Customer purchases generate immutable order receipts in <code>data/receipts/</code>.</li>");
        h.append("<li>Customer reviews submitted on the storefront popup are logged to <code>data/reviews.txt</code>.</li>");
        h.append("<li>Live currency conversion supports PKR default exchange rates out-of-the-box or via Groq AI.</li>");
        h.append("<li>You can manage products and moderate customer reviews directly using the tabs above.</li>");
        h.append("</ul></div>");

        h.append("</body></html>");
        return h.toString();
    }

    static String buildStorefrontHtml() {
        StringBuilder h = new StringBuilder();
        h.append("<html><body style='font-family:sans-serif;background-color:#fcfcfc;color:#111111;padding:15px;'>");
        h.append("<table width='100%' cellpadding='8' cellspacing='0' bgcolor='#000000' style='color:#ffffff;margin-bottom:15px;'>");
        h.append("<tr>");
        h.append("<td><b style='font-size:18px;letter-spacing:2px;'>SWIFTMART STOREFRONT SHOWCASE</b></td>");
        h.append("<td align='right' style='font-size:12px;'>Currencies: PKR (Rs.) [Default] &bull; USD ($) &bull; GBP &bull; EUR</td>");
        h.append("</tr></table>");

        h.append("<table width='100%' cellpadding='12' cellspacing='8' style='margin-bottom:15px;'><tr>");
        int count = 0;
        for (Product p : inv) {
            if (count >= 6) break;
            h.append("<td width='33%' bgcolor='#ffffff' style='border:1px solid #e0e0e0;padding:12px;'>");
            h.append("<div style='font-size:10px;color:#888888;font-weight:bold;text-transform:uppercase;'>").append(p.getCategory()).append(" &bull; ").append(p.getBrand()).append("</div>");
            h.append("<div style='font-size:15px;font-weight:bold;margin:4px 0;'>").append(p.getName()).append("</div>");
            h.append("<div style='font-size:12px;color:#555;'>Stock: <b>").append(p.getStock()).append("</b> units</div>");
            h.append("<div style='margin-top:8px;'>");
            h.append("<span style='font-size:16px;font-weight:bold;color:#000;'>$").append(String.format(Locale.US, "%.2f", p.getFinalPrice())).append("</span> ");
            h.append("<span style='font-size:11px;color:#888;text-decoration:line-through;'>$").append(String.format(Locale.US, "%.2f", p.getPrice())).append("</span> ");
            h.append("<span style='font-size:11px;color:#27ae60;font-weight:bold;'>5% OFF</span>");
            h.append("</div>");
            h.append("<div style='font-size:11px;color:#888;margin-top:4px;'>PKR: Rs. ").append(String.format(Locale.US, "%,.0f", p.getFinalPrice() * 278.5)).append("</div>");
            h.append("</td>");
            count++;
            if (count % 3 == 0 && count < 6) h.append("</tr><tr>");
        }
        h.append("</tr></table>");

        h.append("<table width='100%' cellpadding='10' cellspacing='0' bgcolor='#f0f0f0' style='border:1px solid #cccccc;margin-top:10px;'>");
        h.append("<tr><td>");
        h.append("<b style='font-size:14px;'>Recent Verified Customer Reviews:</b><br><br>");
        int revCount = 0;
        for (String r : revList) {
            if (revCount >= 4) break;
            String[] t = r.split("\\|", 5);
            if (t.length >= 5) {
                h.append("<div style='margin-bottom:8px;font-size:12.5px;padding-bottom:6px;border-bottom:1px dashed #cccccc;'>");
                h.append("<b>").append(t[1]).append("</b> (Product #").append(t[0]).append(") &mdash; <span style='color:#e67e22;'>★ ").append(t[2]).append("/5</span>: ");
                h.append("<i>\"").append(t[3]).append("\"</i> <span style='color:#888;font-size:11px;'>[").append(t[4]).append("]</span>");
                h.append("</div>");
                revCount++;
            }
        }
        h.append("</td></tr></table>");

        h.append("</body></html>");
        return h.toString();
    }

}