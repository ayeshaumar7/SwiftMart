import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/* SWIFTMART - Java Swing E-Commerce App
 * ---------------------------------------------------------------
 * MAP OF THIS FILE (top to bottom):
 *  1. Main + login screen ........ program start: choose Shopper / Business Owner
 *  2. Interfaces + Exceptions .... rules (contracts) and our own error types
 *  3. Services ................... exchange-rate API + product image files
 *  4. Product / CartItem / Order . the "business" classes (what a shop is made of)
 *  5. FileManager ................ saves and loads everything in .txt files
 *  6. UI helpers ................. colours (Theme), rounded cards/buttons, confetti, logo
 *  7. StoreManagementFrame ....... the main window (search, cart, checkout, admin tabs)
 *
 * OOP / JAVA CONCEPTS USED (handy for the viva!):
 *  - Encapsulation : Product has private fields + getters (section 4)
 *  - Abstraction   : Product is an abstract class (section 4)
 *  - Inheritance   : GenericProduct extends Product (section 4)
 *  - Polymorphism  : getDiscountPercent() / getAttribute() are overridden
 *  - Interfaces    : Discountable, PriceConverter (section 2)
 *  - Exceptions    : our own StoreException classes + try/catch (sections 2 and 7)
 *  - File handling : FileManager reads/writes text files (section 5)
 *  - Threads       : the currency API call runs in a background Thread (section 7)
 *  - Collections   : ArrayList, TreeSet, Comparator for search + sort (section 7)
 *  - Event handling: button clicks, typing in the search bar, mouse hover (section 7)
 */
public class Main {
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new RoleSelectionDialog().setVisible(true));
    }
}

// ================= 1. LOGIN SCREEN (design unchanged) =================
class RoleSelectionDialog extends JFrame {
    private static final Color BG_COLOR = new Color(255, 240, 245);
    private static final Color BORDER_COLOR = new Color(248, 187, 208);
    private static final Color PRIMARY_COLOR = new Color(216, 27, 96);
    private static final Color ACCENT_COLOR = new Color(233, 30, 99);
    private static final Color TEXT_MUTED = new Color(173, 20, 87);

    public RoleSelectionDialog() {
        setTitle("SwiftMart | Secure Access Portal");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(460, 420);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_COLOR);
        setLayout(new GridBagLayout());

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_COLOR, 2, true), new EmptyBorder(30, 35, 30, 35)));

        JLabel titleLbl = new JLabel("SwiftMart");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 24));
        titleLbl.setForeground(PRIMARY_COLOR);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subLbl = new JLabel("Select your portal access mode ✨");
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subLbl.setForeground(TEXT_MUTED);
        subLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(titleLbl);
        card.add(Box.createVerticalStrut(5));
        card.add(subLbl);
        card.add(Box.createVerticalStrut(25));

        JButton shopperBtn = makeButton("🛍️ Continue as Shopper", PRIMARY_COLOR);
        JButton adminBtn = makeButton("🔐 Business Owner Login", ACCENT_COLOR);
        JButton exitBtn = makeButton("Exit Application", new Color(194, 24, 91));

        shopperBtn.addActionListener(e -> { dispose(); new StoreManagementFrame(false).setVisible(true); });

        // Admin must type the password before the admin portal opens
        adminBtn.addActionListener(e -> {
            JPasswordField passField = new JPasswordField();
            int option = JOptionPane.showConfirmDialog(this, passField, "Enter Admin Password:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option == JOptionPane.OK_OPTION) {
                if ("admin123".equals(new String(passField.getPassword()))) {
                    dispose();
                    new StoreManagementFrame(true).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(this, "Incorrect password (default: admin123).", "Access Denied", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        exitBtn.addActionListener(e -> System.exit(0));

        card.add(shopperBtn);
        card.add(Box.createVerticalStrut(12));
        card.add(adminBtn);
        card.add(Box.createVerticalStrut(12));
        card.add(exitBtn);
        add(card);
    }

    private JButton makeButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setMaximumSize(new Dimension(320, 42));
        btn.setPreferredSize(new Dimension(320, 42));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder());
        return btn;
    }
}

// ================= 2. INTERFACES & EXCEPTIONS =================
// INTERFACE = a promise. Any class that "implements Discountable" MUST provide getDiscountPercent().
interface Discountable { double getDiscountPercent(); }
interface PriceConverter { double getRate(String currency) throws StoreException; }

// CUSTOM EXCEPTIONS: our own error types. InvalidInput and OutOfStock are "children" of StoreException,
// so one catch(StoreException) can handle all of them.
class StoreException extends Exception { StoreException(String m) { super(m); } }
class InvalidInputException extends StoreException { InvalidInputException(String m) { super(m); } }
class OutOfStockException extends StoreException { OutOfStockException(String m) { super(m); } }

// ================= 3. SERVICES =================
// Asks the Groq AI API for a USD -> other currency exchange rate.
// It sends an HTTP request over the internet and reads the number from the reply.
class ExchangeRateService implements PriceConverter {
    private static final String API_KEY = "API KEY ";   // <-- paste your Groq key here

    @Override
    public double getRate(String currency) throws StoreException {
        if ("USD".equalsIgnoreCase(currency)) return 1.0;
        if (API_KEY.contains("PASTE_YOUR_GROQ_KEY_HERE") || API_KEY.trim().isEmpty())
            throw new StoreException("API Key Missing! Replace API_KEY inside ExchangeRateService.");
        try {
            String json = "{\"model\":\"openai/gpt-oss-20b\",\"messages\":["
                    + "{\"role\":\"system\",\"content\":\"You are a financial calculator. Output strictly valid floating point exchange rates with no additional text.\"},"
                    + "{\"role\":\"user\",\"content\":\"What is the exchange rate from 1 USD to " + currency + "? Reply ONLY with the raw number.\"}]}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new StoreException("Groq API Error (" + res.statusCode() + ")");

            // pull the number out of the reply text
            String body = res.body();
            int start = body.indexOf("\"", body.indexOf("\"content\":") + 10) + 1;
            int end = body.indexOf("\"", start);
            return Double.parseDouble(body.substring(start, end).replaceAll("[^0-9.]", ""));
        } catch (IOException | InterruptedException e) {
            throw new StoreException("Network error connecting to Groq API.");
        } catch (RuntimeException e) {
            throw new StoreException("Failed to parse numeric rate from Groq API response.");
        }
    }
}

// Copies product pictures into the 'inventory_images' folder (or makes a placeholder)
class ImageManagerService {
    static String processProductImage(int id, File source) {
        try {
            new File("inventory_images").mkdirs();
            File dest = new File("inventory_images/product_" + id + ".jpg");
            if (source != null && source.exists()) {
                Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else if (!dest.exists()) {
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(300, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(new Color(255, 240, 245));
                g.fillRect(0, 0, 300, 300);
                g.setColor(new Color(216, 27, 96));
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                g.drawString("No Product Image", 80, 150);
                g.dispose();
                javax.imageio.ImageIO.write(img, "jpg", dest);
            }
            return dest.exists() ? dest.getPath() : "";
        } catch (Exception e) {
            return "";
        }
    }
}

// ================= 4. MODELS & ORDERS =================
// ABSTRACT CLASS: a template for products (you cannot write "new Product()").
// ENCAPSULATION: the fields are private; other classes use the getters below.
abstract class Product implements Discountable {
    private final int id;
    private String name, brand, category, imageFilePath, feedback = "No reviews yet.";
    private double priceUsd;
    private int stock;

    Product(int id, String name, String brand, double price, int stock, String category, String img) throws InvalidInputException {
        this.id = id;
        this.name = clean(name, "Product name");
        this.brand = clean(brand, "Brand");
        this.category = clean(category, "Category");
        if (price <= 0) throw new InvalidInputException("Price must be greater than 0.");
        if (stock < 0) throw new InvalidInputException("Stock cannot be negative.");
        this.priceUsd = price;
        this.stock = stock;
        this.imageFilePath = img == null ? "" : img.trim();
    }

    // Commas are removed because our text file uses commas as separators
    private static String clean(String s, String field) throws InvalidInputException {
        if (s == null || s.trim().isEmpty()) throw new InvalidInputException(field + " cannot be empty.");
        return s.trim().replace(",", " ");
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getCategory() { return category; }
    public double getPriceUsd() { return priceUsd; }
    public int getStock() { return stock; }
    public String getImageFilePath() { return imageFilePath; }
    public String getCustomerFeedback() { return feedback; }
    public void setCustomerFeedback(String f) { feedback = f.replace(",", " "); }
    public double getFinalUnitPriceUsd() { return priceUsd * (1 - getDiscountPercent() / 100.0); }
    public abstract String getAttribute();

    public void reduceStock(int qty) throws OutOfStockException {
        if (qty > stock) throw new OutOfStockException("Insufficient stock for product: " + name);
        stock -= qty;
    }

    // One line of text for inventory.txt
    public String toFileFormat() {
        return category + "," + id + "," + name + "," + brand + "," + priceUsd + "," + stock + "," + getAttribute() + "," + imageFilePath + "," + feedback;
    }
}

// INHERITANCE: GenericProduct "extends" Product, so it gets everything Product has.
// POLYMORPHISM: it fills in (overrides) the two abstract methods with its own version.
class GenericProduct extends Product {
    private final String spec;   // e.g. "1 Year Warranty"
    GenericProduct(int id, String n, String b, double p, int s, String cat, String spec, String img) throws InvalidInputException {
        super(id, n, b, p, s, cat, img);
        this.spec = (spec == null || spec.trim().isEmpty()) ? "N/A" : spec.trim().replace(",", " ");
    }
    @Override public double getDiscountPercent() { return 5.0; }
    @Override public String getAttribute() { return spec; }
}

// One cart line = product + quantity
class CartItem {   // (the cart itself is just a List<CartItem> inside the main window)
    private final Product product;
    private int quantity;
    CartItem(Product p, int q) { product = p; quantity = q; }
    Product getProduct() { return product; }
    int getQuantity() { return quantity; }
    void setQuantity(int q) { quantity = q; }
    double getSubtotalUsd() { return product.getFinalUnitPriceUsd() * quantity; }
}

// A finished order: works out the total and prints the receipt
class Order {
    static final String WHOLESALE = "Business to Business (Wholesale)";

    // Wholesale = 15% off, retail with 10+ items = 5% off
    static double multiplier(String type, int totalQty) {
        return WHOLESALE.equals(type) ? 0.85 : (totalQty >= 10 ? 0.95 : 1.0);
    }

    private final String orderId = "ORD-" + System.currentTimeMillis() % 100000;
    private final String client, phone, email, address, type;   // who ordered + where to deliver
    private final List<CartItem> items;
    private final double totalUsd;

    Order(String client, String phone, String email, String address, String type, List<CartItem> cartItems) {
        this.client = client;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.type = type;
        this.items = new ArrayList<>(cartItems);   // copy, so clearing the cart keeps the order intact
        double sum = 0;
        int qty = 0;
        for (CartItem c : items) { sum += c.getSubtotalUsd(); qty += c.getQuantity(); }
        totalUsd = sum * multiplier(type, qty);
    }

    String getOrderId() { return orderId; }
    String getClient() { return client; }
    String getEmail() { return email; }

    String generateReceipt(double rate, String cur) {
        StringBuilder sb = new StringBuilder("====================================================\n");
        sb.append("            SWIFTMART MANAGEMENT RECEIPT            \n");
        sb.append("====================================================\n");
        sb.append("Order Ref   : ").append(orderId).append("\nClient/Co   : ").append(client)
                .append("\nPhone       : ").append(phone).append("\nEmail       : ").append(email)
                .append("\nDeliver To  : ").append(address)
                .append("\nOrder Mode  : ").append(type).append("\nTimestamp   : ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("%-18s %-10s %-8s %-10s%n", "Item", "Brand", "Qty", "Subtotal"));
        for (CartItem i : items)
            sb.append(String.format("%-18s %-10s x%-7d %.2f %s%n", i.getProduct().getName(), i.getProduct().getBrand(),
                    i.getQuantity(), i.getSubtotalUsd() * rate, cur));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("TOTAL PAYABLE: %.2f %s%n", totalUsd * rate, cur));
        return sb.append("====================================================\n\n").toString();
    }
}

// ================= 5. FILE HANDLING =================
// FILE HANDLING: the only class that touches .txt files. Everything is saved here so data survives restarting the app.
class FileManager {
    static final String PRODUCT_FILE = "inventory.txt", CUSTOMER_FILE = "order_audit.txt", SUPPLIER_FILE = "supplier_payouts.txt";

    // Reads inventory.txt. Line format: category,id,name,brand,price,stock,spec,image,feedback
    static List<Product> loadInventory() throws IOException {
        List<Product> list = new ArrayList<>();
        if (!Files.exists(Path.of(PRODUCT_FILE))) return list;
        for (String line : Files.readAllLines(Path.of(PRODUCT_FILE))) {
            if (line.trim().isEmpty()) continue;
            try {
                String[] t = line.split(",");
                GenericProduct p = new GenericProduct(Integer.parseInt(t[1]), t[2], t[3], Double.parseDouble(t[4]),
                        Integer.parseInt(t[5]), t[0], t[6], t.length > 7 ? t[7] : "");
                if (t.length > 8) p.setCustomerFeedback(t[8]);
                list.add(p);
            } catch (Exception e) {
                System.err.println("Corrupted file entry ignored: " + line);   // skip bad lines
            }
        }
        return list;
    }

    static void saveInventory(List<Product> products) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Product p : products) sb.append(p.toFileFormat()).append("\n");
        write(PRODUCT_FILE, sb.toString(), false);
    }

    static void appendOrderAudit(Order o, double rate, String cur) throws IOException {
        write(CUSTOMER_FILE, o.generateReceipt(rate, cur), true);
    }

    static void appendSupplierReceipt(String supplier, Product p, double rate, String cur) throws IOException {
        String s = "====================================================\n"
                + "           SWIFTMART STOCK INWARD RECEIPT           \n"
                + "====================================================\n"
                + "Receipt Ref : SUP-" + System.currentTimeMillis() % 100000 + "\n"
                + "Supplier Co : " + supplier + "\n"
                + "Timestamp   : " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n"
                + "Item Stocked: " + p.getName() + " (" + p.getBrand() + ")\n"
                + "Category    : " + p.getCategory() + "\n"
                + "Qty Inward  : " + p.getStock() + " units\n"
                + String.format("Unit Cost   : %.2f %s%n", p.getPriceUsd() * rate, cur)
                + String.format("PAYABLE DUE : %.2f %s%n", p.getPriceUsd() * p.getStock() * rate, cur)
                + "====================================================\n\n";
        write(SUPPLIER_FILE, s, true);
    }

    static String read(String file, String emptyMsg) throws IOException {
        if (!Files.exists(Path.of(file))) return emptyMsg;
        String s = Files.readString(Path.of(file));
        return s.isEmpty() ? emptyMsg : s;
    }

    static void write(String file, String text, boolean append) throws IOException {
        try (FileWriter fw = new FileWriter(file, append)) { fw.write(text); }
    }
}

// ================= 6. UI HELPERS =================

// One set of colours. We have two: LIGHT (soft pink) and DARK (deep plum).
class Theme {
    final Color bg, card, border, primary, accent, danger, text, muted, input, heroA, heroB, good, warn;
    Theme(int bg, int card, int border, int primary, int accent, int danger, int text, int muted, int input,
          int heroA, int heroB, int good, int warn) {
        this.bg = new Color(bg); this.card = new Color(card); this.border = new Color(border);
        this.primary = new Color(primary); this.accent = new Color(accent); this.danger = new Color(danger);
        this.text = new Color(text); this.muted = new Color(muted); this.input = new Color(input);
        this.heroA = new Color(heroA); this.heroB = new Color(heroB); this.good = new Color(good); this.warn = new Color(warn);
    }
    static final Theme LIGHT = new Theme(0xFFF0F5, 0xFFFFFF, 0xF8BBD0, 0xD81B60, 0xE91E63, 0xC2185B, 0x880E4F, 0xAD1457,
            0xFFFFFF, 0xD81B60, 0x9C27B0, 0x2E7D32, 0xE67E22);
    static final Theme DARK = new Theme(0x18121E, 0x281E30, 0x603858, 0xDB2777, 0xC026D3, 0xBE2850, 0xFAE8F5, 0xCEAADC,
            0x362A40, 0x831843, 0x4A148C, 0x66BB6A, 0xFFA726);
}

// Panel with rounded corners. Two constructors: a bordered card, or a gradient banner.
class RoundedPanel extends JPanel {
    private final Color fill, fill2;
    private Color line;
    private int lineWidth = 1;   // thickness of the outline (we make product cards bolder)
    private final int arc;

    RoundedPanel(LayoutManager lm, Color fill, Color line, int arc) {   // normal card
        super(lm); this.fill = fill; this.fill2 = null; this.line = line; this.arc = arc; setOpaque(false);
    }
    RoundedPanel(LayoutManager lm, Color from, Color to) {              // gradient banner
        super(lm); this.fill = from; this.fill2 = to; this.line = null; this.arc = 24; setOpaque(false);
    }
    void setLine(Color c) { line = c; repaint(); }
    void setLineWidth(int w) { lineWidth = w; repaint(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (fill2 != null) g2.setPaint(new GradientPaint(0, 0, fill, getWidth(), getHeight(), fill2));
        else g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        if (line != null) {   // draw the outline, moved inwards by half its thickness so it is not cut off
            int o = lineWidth / 2;
            g2.setStroke(new BasicStroke(lineWidth));
            g2.setColor(line);
            g2.drawRoundRect(o, o, getWidth() - 1 - 2 * o, getHeight() - 1 - 2 * o, arc, arc);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

// Rounded button that brightens on hover and turns grey when disabled
class RoundedButton extends JButton {
    private final Color base;
    private boolean hover;

    RoundedButton(String text, Color base) {
        super(text);
        this.base = base;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(Color.WHITE);
        setFont(new Font("SansSerif", Font.BOLD, 12));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(9, 16, 9, 16));
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(!isEnabled() ? Color.GRAY : (hover ? base.brighter() : base));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        g2.dispose();
        super.paintComponent(g);
    }
}

// Text box that shows grey hint text while empty (used for the search bar)
class HintTextField extends JTextField {
    private final String hint;
    private final Color hintColor;
    HintTextField(String hint, Color hintColor) { this.hint = hint; this.hintColor = hintColor; }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(hintColor);
            g2.setFont(getFont().deriveFont(Font.ITALIC));
            g2.drawString(hint, getInsets().left, (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2);
            g2.dispose();
        }
    }
}

// 🎉 Confetti: a see-through layer that rains coloured paper for ~4 seconds, then hides itself.
// A Swing Timer calls step() every 25 ms; each step moves every piece a little and repaints.
class ConfettiPanel extends JComponent {
    private static class Piece { double x, y, vx, vy; int w, h; Color color; }   // one piece of paper

    private final List<Piece> pieces = new ArrayList<>();
    private final java.util.Random rnd = new java.util.Random();
    private Timer timer;
    private int ticks;

    void start() {
        Color[] palette = {new Color(0xE91E63), new Color(0xFFC107), new Color(0x00BCD4),
                new Color(0x8BC34A), new Color(0x9C27B0), new Color(0xFF5722)};
        pieces.clear();
        ticks = 0;
        for (int i = 0; i < 180; i++) {   // create 180 random pieces above the top of the window
            Piece p = new Piece();
            p.x = rnd.nextInt(Math.max(1, getWidth()));
            p.y = -rnd.nextInt(500);
            p.vx = rnd.nextDouble() * 4 - 2;     // sideways drift
            p.vy = 2 + rnd.nextDouble() * 5;     // falling speed
            p.w = 6 + rnd.nextInt(8);
            p.h = 4 + rnd.nextInt(6);
            p.color = palette[rnd.nextInt(palette.length)];
            pieces.add(p);
        }
        setVisible(true);
        if (timer != null) timer.stop();
        timer = new Timer(25, e -> step());
        timer.start();
    }

    private void step() {
        ticks++;
        for (Piece p : pieces) { p.x += p.vx; p.y += p.vy; }
        if (ticks > 160) { timer.stop(); setVisible(false); }   // 160 x 25 ms = 4 seconds
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        for (Piece p : pieces) {
            g.setColor(p.color);
            g.fillRect((int) p.x, (int) p.y, p.w, p.h);
        }
    }
}

// 🛍️ Our SwiftMart logo, drawn with code (no image file needed): pink-purple circle + white shopping bag.
class LogoIcon implements Icon {
    private final int size;
    LogoIcon(int size) { this.size = size; }
    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        g2.setPaint(new GradientPaint(0, 0, new Color(0xD81B60), size, size, new Color(0x9C27B0)));
        g2.fillOval(0, 0, size, size);                                   // background circle
        g2.setColor(Color.WHITE);
        int bw = size * 5 / 12, bh = size * 4 / 10, bx = (size - bw) / 2, by = size * 11 / 24;
        g2.fillRoundRect(bx, by, bw, bh, size / 8, size / 8);            // bag body
        g2.setStroke(new BasicStroke(Math.max(2f, size / 16f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(bx + bw / 4, by - bh / 2, bw / 2, bh, 0, 180);        // bag handle
        g2.setColor(new Color(0xD81B60));
        g2.fillOval(size / 2 - size / 16, by + bh / 2 - size / 16, size / 8, size / 8);   // little pink dot
        g2.dispose();
    }
}

// ================= MAIN WINDOW (shopper + admin) =================
// The main window. isAdmin decides what the user sees: shoppers get 1 tab, the business owner gets 4.
// Everything on screen is built in buildUI(), which runs again when the theme changes.
class StoreManagementFrame extends JFrame {
    private static final String ALL = "All Categories";
    private static boolean darkMode = false;                 // static: remembered when switching roles
    private static final List<String> uiKeys = new ArrayList<>();   // UIManager keys we changed (to reset later)

    private Theme t;                                          // colours currently in use
    private final List<Product> inventory = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private final PriceConverter priceService = new ExchangeRateService();
    private final boolean isAdmin;
    private String currentCurrency = "USD";
    private double currentRate = 1.0;

    // search / filter state
    private String searchText = "", selectedCategory = ALL;
    private int sortMode = 0;
    private boolean updatingFilters = false;

    // components we need to reach later
    private JTabbedPane tabs;
    private JPanel gridPanel;
    private HintTextField searchField;
    private JComboBox<String> categoryFilter, sortCombo;
    private JLabel countLabel, statusLabel, imageNameLabel;
    private RoundedButton cartButton;
    private JTextArea supplierLog, customerLog;
    private File pickedImage;
    private final ConfettiPanel confetti = new ConfettiPanel();   // the confetti layer (lives on the window's glass pane)
    private final DefaultTableModel cartModel = new DefaultTableModel(new String[]{"Item", "Brand", "Qty", "Unit Price", "Subtotal"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    public StoreManagementFrame(boolean isAdmin) {
        this.isAdmin = isAdmin;
        setTitle("SwiftMart 🌸 | " + (isAdmin ? "Admin Portal" : "Shopper Marketplace"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1350, 880);
        setLocationRelativeTo(null);

        // Window icon = our logo (drawn into a small image)
        java.awt.image.BufferedImage logo = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D lg = logo.createGraphics();
        new LogoIcon(64).paintIcon(null, lg, 0, 0);
        lg.dispose();
        setIconImage(logo);

        setGlassPane(confetti);   // the glass pane floats above everything, perfect for confetti
        loadInitialData();
        buildUI();
    }

    // ---------- Builds (or rebuilds after the theme toggle) the whole screen ----------
    private void buildUI() {
        t = darkMode ? Theme.DARK : Theme.LIGHT;
        applyUiDefaults();
        int keep = tabs == null ? 0 : tabs.getSelectedIndex();   // stay on the same tab

        Container root = getContentPane();
        root.removeAll();
        root.setBackground(t.bg);
        root.setLayout(new BorderLayout());
        root.add(buildHeader(), BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        tabs.setBackground(t.bg);
        tabs.setForeground(t.text);
        tabs.addTab("  Marketplace Catalog 🛍️  ", buildCatalog());
        if (isAdmin) {   // only the business owner sees these 3 tabs
            tabs.addTab("  Supplier Intake & Admin  ", buildIntake());
            tabs.addTab("  Supplier Inward Receipts  ", buildLog(true));
            tabs.addTab("  Customer Sales Receipts  ", buildLog(false));
        }
        tabs.setSelectedIndex(Math.min(keep, tabs.getTabCount() - 1));
        tabs.addChangeListener(e -> reloadLogs());
        root.add(tabs, BorderLayout.CENTER);

        refreshGrid();
        updateCartBadge();
        reloadLogs();
        root.revalidate();
        root.repaint();
    }

    // Colours Java's built-in pieces (pop-ups, drop-downs, tabs) with our theme
    private void applyUiDefaults() {
        ui(t.bg, "Panel.background", "OptionPane.background", "TabbedPane.background", "TabbedPane.contentAreaColor", "ScrollPane.background");
        ui(t.text, "Label.foreground", "OptionPane.messageForeground", "TextField.foreground", "TextField.caretForeground",
                "ComboBox.foreground", "List.foreground", "TabbedPane.foreground", "TextArea.foreground");
        ui(t.input, "TextField.background", "ComboBox.background", "List.background");
        ui(t.card, "TabbedPane.selected", "Viewport.background", "TextArea.background");
        ui(t.primary, "ComboBox.selectionBackground", "List.selectionBackground", "TabbedPane.focus");
        ui(Color.WHITE, "ComboBox.selectionForeground", "List.selectionForeground");
        ui(t.border, "TabbedPane.light", "TabbedPane.highlight", "TabbedPane.shadow", "TabbedPane.darkShadow", "ScrollBar.thumb");
    }

    private void ui(Color c, String... keys) {
        for (String k : keys) { UIManager.put(k, c); if (!uiKeys.contains(k)) uiKeys.add(k); }
    }

    // ---------- Header: title, currency, cart button, theme toggle, switch role ----------
    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(t.card);
        bar.setBorder(new CompoundBorder(new LineBorder(t.border, 1), new EmptyBorder(10, 20, 10, 20)));

        JLabel title = lbl(isAdmin ? "SwiftMart 👑  —  [Admin Management]" : "SwiftMart 🛍️  —  [Shopper Portal]", 18, Font.BOLD, t.primary);

        title.setIcon(new LogoIcon(36));   // our logo next to the store name
        title.setIconTextGap(12);

        statusLabel = lbl("Currency: " + currentCurrency, 12, Font.BOLD, t.text);
        JComboBox<String> currCombo = new JComboBox<>(new String[]{"USD", "PKR", "EUR", "GBP", "AED", "INR"});
        styleCombo(currCombo);
        currCombo.setSelectedItem(currentCurrency);
        currCombo.addActionListener(e -> changeCurrency((String) currCombo.getSelectedItem(), currCombo));

        // The cart button opens the cart popup (replaces the old right-hand panel)
        cartButton = new RoundedButton("🛒 Cart (0)", t.accent);
        cartButton.addActionListener(e -> openCartDialog());

        RoundedButton themeBtn = new RoundedButton(darkMode ? "☀️ Light Mode" : "🌙 Dark Mode", darkMode ? new Color(0xFF8F00) : new Color(0x4A148C));
        themeBtn.addActionListener(e -> { darkMode = !darkMode; buildUI(); });

        RoundedButton switchBtn = new RoundedButton("Switch Role", t.primary);
        switchBtn.addActionListener(e -> {
            dispose();
            for (String k : uiKeys) UIManager.put(k, null);   // login screen keeps its original look
            new RoleSelectionDialog().setVisible(true);
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(statusLabel);
        right.add(currCombo);
        right.add(cartButton);
        right.add(themeBtn);
        right.add(switchBtn);
        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ---------- Catalog tab: banner + SEARCH BAR + product grid ----------
    private JPanel buildCatalog() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(t.bg);
        panel.setBorder(new EmptyBorder(15, 18, 15, 18));

        // Gradient banner
        RoundedPanel hero = new RoundedPanel(new BorderLayout(), t.heroA, t.heroB);
        hero.setBorder(new EmptyBorder(18, 26, 18, 26));
        hero.add(lbl(isAdmin ? "Welcome back, Business Owner 👑" : "Discover products you'll love ✨", 24, Font.BOLD, Color.WHITE));

        // Search bar: [🔍 text box] [category] [sort] [Reset]
        searchField = new HintTextField("Search by product name, brand, category or spec...", t.muted);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 15));
        searchField.setBackground(t.input);
        searchField.setForeground(t.text);
        searchField.setCaretColor(t.text);
        searchField.setBorder(new CompoundBorder(new LineBorder(t.border, 1, true), new EmptyBorder(8, 12, 8, 12)));
        searchField.setText(searchText);
        // Fires on EVERY keystroke -> the grid updates live
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { searchChanged(); }
            public void removeUpdate(DocumentEvent e) { searchChanged(); }
            public void changedUpdate(DocumentEvent e) { searchChanged(); }
        });

        categoryFilter = new JComboBox<>();
        styleCombo(categoryFilter);
        categoryFilter.addActionListener(e -> {
            if (updatingFilters) return;
            selectedCategory = (String) categoryFilter.getSelectedItem();
            refreshGrid();
        });
        refreshCategoryFilter();

        sortCombo = new JComboBox<>(new String[]{"Sort: Default", "Price: Low → High", "Price: High → Low", "Name: A → Z", "Stock: High → Low"});
        styleCombo(sortCombo);
        sortCombo.setSelectedIndex(sortMode);
        sortCombo.addActionListener(e -> { sortMode = sortCombo.getSelectedIndex(); refreshGrid(); });

        RoundedButton reset = new RoundedButton("✕ Reset", t.danger);
        reset.addActionListener(e -> { searchField.setText(""); categoryFilter.setSelectedIndex(0); sortCombo.setSelectedIndex(0); });

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filters.setOpaque(false);
        filters.add(categoryFilter);
        filters.add(sortCombo);
        filters.add(reset);

        RoundedPanel searchCard = new RoundedPanel(new BorderLayout(12, 0), t.card, t.border, 22);
        searchCard.setBorder(new EmptyBorder(10, 16, 10, 16));
        searchCard.add(lbl("🔍", 18, Font.PLAIN, t.primary), BorderLayout.WEST);
        searchCard.add(searchField, BorderLayout.CENTER);
        searchCard.add(filters, BorderLayout.EAST);

        countLabel = lbl(" ", 12, Font.BOLD, t.muted);
        JPanel searchArea = new JPanel(new BorderLayout(0, 4));
        searchArea.setOpaque(false);
        searchArea.add(searchCard, BorderLayout.CENTER);
        searchArea.add(countLabel, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(hero, BorderLayout.NORTH);
        top.add(searchArea, BorderLayout.CENTER);

        // Product grid (4 cards per row, full width). Wrapper keeps cards at natural height.
        gridPanel = new JPanel();
        gridPanel.setBackground(t.bg);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(t.bg);
        wrapper.add(gridPanel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(t.bg);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void searchChanged() { searchText = searchField.getText(); refreshGrid(); }

    // Category drop-down = "All Categories" + every category that really exists
    private void refreshCategoryFilter() {
        if (categoryFilter == null) return;
        updatingFilters = true;   // ignore events while we refill
        categoryFilter.removeAllItems();
        categoryFilter.addItem(ALL);
        Set<String> cats = new TreeSet<>();
        for (Product p : inventory) cats.add(p.getCategory());
        for (String c : cats) categoryFilter.addItem(c);
        categoryFilter.setSelectedItem(selectedCategory);
        selectedCategory = (String) categoryFilter.getSelectedItem();
        updatingFilters = false;
    }

    // THE SEARCH LOGIC: keep products matching the text + category, then sort
    private List<Product> filteredProducts() {
        String q = searchText.trim().toLowerCase();
        List<Product> out = new ArrayList<>();
        for (Product p : inventory) {
            boolean catOk = selectedCategory.equals(ALL) || p.getCategory().equalsIgnoreCase(selectedCategory);
            boolean textOk = q.isEmpty() || (p.getName() + " " + p.getBrand() + " " + p.getCategory() + " " + p.getAttribute()).toLowerCase().contains(q);
            if (catOk && textOk) out.add(p);
        }
        switch (sortMode) {
            case 1: out.sort(Comparator.comparingDouble(Product::getPriceUsd)); break;
            case 2: out.sort(Comparator.comparingDouble(Product::getPriceUsd).reversed()); break;
            case 3: out.sort(Comparator.comparing((Product p) -> p.getName().toLowerCase())); break;
            case 4: out.sort(Comparator.comparingInt(Product::getStock).reversed()); break;
            default: break;
        }
        return out;
    }

    // Redraws the product grid (called after every search / change)
    private void refreshGrid() {
        if (gridPanel == null) return;
        gridPanel.removeAll();
        List<Product> shown = filteredProducts();
        if (countLabel != null) countLabel.setText("Showing " + shown.size() + " of " + inventory.size() + " products");

        if (shown.isEmpty()) {   // nothing matched
            gridPanel.setLayout(new GridLayout(1, 1));
            JLabel none = lbl("😕  No products found. Try a different search or press Reset.", 16, Font.BOLD, t.muted);
            none.setHorizontalAlignment(JLabel.CENTER);
            none.setBorder(new EmptyBorder(60, 0, 60, 0));
            gridPanel.add(none);
        } else {
            gridPanel.setLayout(new GridLayout(0, 4, 16, 16));
            for (Product p : shown) gridPanel.add(productCard(p));
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    // One product card: picture, details, stock badge and buttons
    private JPanel productCard(Product p) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(6, 8), t.card, t.border, 22);
        card.setLineWidth(3);   // BOLD card outline
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.addMouseListener(new MouseAdapter() {   // pink border on hover
            public void mouseEntered(MouseEvent e) { card.setLine(t.primary); }
            public void mouseExited(MouseEvent e) { if (!card.contains(e.getPoint())) card.setLine(t.border); }
        });

        JLabel img = new JLabel("[No Image]", JLabel.CENTER);
        img.setForeground(t.muted);
        img.setPreferredSize(new Dimension(160, 160));
        img.setBorder(new CompoundBorder(new LineBorder(t.border, 3, true), new EmptyBorder(4, 4, 4, 4)));   // BOLD image border
        if (new File(p.getImageFilePath()).isFile()) {
            img.setText(null);
            img.setIcon(new ImageIcon(new ImageIcon(p.getImageFilePath()).getImage().getScaledInstance(140, 140, Image.SCALE_SMOOTH)));
        }
        card.add(img, BorderLayout.NORTH);

        int s = p.getStock();   // green = plenty, orange = low, red = sold out
        JLabel stock = lbl(s == 0 ? "● Sold out" : (s <= 5 ? "● Only " + s + " left!" : "● In stock (" + s + ")"), 11, Font.BOLD,
                s == 0 ? t.danger : (s <= 5 ? t.warn : t.good));
        String review = p.getCustomerFeedback();
        if (review.length() > 55) review = review.substring(0, 55) + "...";

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.add(lbl(p.getName(), 15, Font.BOLD, t.text));
        info.add(lbl(p.getBrand() + "  •  " + p.getCategory(), 11, Font.PLAIN, t.muted));
        info.add(lbl("✔ " + p.getAttribute(), 11, Font.PLAIN, t.muted));
        info.add(Box.createVerticalStrut(6));
        info.add(lbl(String.format("%.2f %s", p.getPriceUsd() * currentRate, currentCurrency), 17, Font.BOLD, t.primary));
        info.add(lbl(String.format("🏷️ %.0f%% off in cart: %.2f %s", p.getDiscountPercent(), p.getFinalUnitPriceUsd() * currentRate, currentCurrency), 11, Font.PLAIN, t.good));
        info.add(stock);
        info.add(lbl("<html><i>⭐ " + review.replace("<", "&lt;") + "</i></html>", 11, Font.PLAIN, t.muted));
        card.add(info, BorderLayout.CENTER);

        RoundedButton addBtn = new RoundedButton("Add to Cart 🛒", t.accent);
        addBtn.setEnabled(s > 0);   // disabled when sold out
        addBtn.addActionListener(e -> {
            String q = JOptionPane.showInputDialog(this, "Enter quantity for " + p.getName() + ":", "1");
            if (q == null || q.trim().isEmpty()) return;
            try { addToCart(p, Integer.parseInt(q.trim())); } catch (NumberFormatException ex) { showError(this, "Invalid quantity number."); }
        });

        RoundedButton reviewBtn = new RoundedButton("Review ⭐", t.primary);
        reviewBtn.addActionListener(e -> {
            String text = JOptionPane.showInputDialog(this, "Write your review/rating for " + p.getName() + ":");
            if (text == null || text.trim().isEmpty()) return;
            p.setCustomerFeedback(text);
            try {
                FileManager.saveInventory(inventory);
                refreshGrid();
                JOptionPane.showMessageDialog(this, "Thank you for your feedback!");
            } catch (IOException ex) { showError(this, "Failed to save feedback."); }
        });

        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setOpaque(false);
        row.add(addBtn);
        row.add(reviewBtn);
        JPanel bottom = new JPanel(new GridLayout(0, 1, 6, 6));
        bottom.setOpaque(false);
        bottom.add(row);

        if (isAdmin) {   // only the admin can delete products
            RoundedButton del = new RoundedButton("🗑 Remove Product", t.danger);
            del.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(this, "Remove '" + p.getName() + "' from store?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                inventory.removeIf(x -> x.getId() == p.getId());
                cart.removeIf(c -> c.getProduct().getId() == p.getId());
                try { FileManager.saveInventory(inventory); } catch (IOException ex) { showError(this, "Failed to save changes."); }
                refreshCategoryFilter();
                refreshGrid();
                updateCartBadge();
            });
            bottom.add(del);
        }
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    // ---------- Cart logic ----------
    // Steps: 1) quantity must be positive  2) if the product is already in the cart, add to it  3) never go above the stock
    private void addToCart(Product p, int qty) {
        if (qty <= 0) { showError(this, "Quantity must be at least 1."); return; }
        for (CartItem item : cart) {
            if (item.getProduct().getId() == p.getId()) {   // already in cart -> increase quantity
                if (item.getQuantity() + qty > p.getStock()) { showError(this, "Total quantity in cart exceeds stock."); return; }
                item.setQuantity(item.getQuantity() + qty);
                updateCartBadge();
                return;
            }
        }
        if (qty > p.getStock()) { showError(this, "Requested quantity exceeds available stock (" + p.getStock() + ")."); return; }
        cart.add(new CartItem(p, qty));
        updateCartBadge();
    }

    private void updateCartBadge() {
        if (cartButton == null) return;
        int n = 0;
        for (CartItem i : cart) n += i.getQuantity();
        cartButton.setText("🛒 Cart (" + n + ")");
    }

    // Final amount in the chosen currency (same discount rules as Order)
    private double cartTotal(String orderType) {
        double usd = 0;
        int qty = 0;
        for (CartItem i : cart) { usd += i.getSubtotalUsd(); qty += i.getQuantity(); }
        return usd * Order.multiplier(orderType, qty) * currentRate;
    }

    // Cart popup: table of items + checkout form
    private void openCartDialog() {
        JDialog dialog = new JDialog(this, "Your Shopping Cart 🛒", true);
        dialog.setSize(820, 800);
        dialog.setLocationRelativeTo(this);
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(t.bg);
        content.setBorder(new EmptyBorder(16, 16, 16, 16));
        dialog.setContentPane(content);
        content.add(lbl("🛒 Your Shopping Cart", 20, Font.BOLD, t.primary), BorderLayout.NORTH);

        JTable table = new JTable(cartModel);
        table.setRowHeight(30);
        table.setBackground(t.card);
        table.setForeground(t.text);
        table.setSelectionBackground(t.primary);
        table.setSelectionForeground(Color.WHITE);
        table.getTableHeader().setBackground(t.card);
        table.getTableHeader().setForeground(t.primary);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new LineBorder(t.border, 1, true));
        sp.getViewport().setBackground(t.card);
        content.add(sp, BorderLayout.CENTER);

        // Delivery details the customer must fill in before paying (like real e-commerce sites)
        JTextField nameField = textField(), phoneField = textField(), emailField = textField(), addressField = textField();
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Business to Customer (Retail)", Order.WHOLESALE});
        styleCombo(typeCombo);
        JLabel totalLabel = lbl(" ", 18, Font.BOLD, t.primary);

        // refills the table rows and the total text
        Runnable refresh = () -> {
            cartModel.setRowCount(0);
            for (CartItem i : cart) cartModel.addRow(new Object[]{i.getProduct().getName(), i.getProduct().getBrand(), i.getQuantity(),
                    String.format("%.2f %s", i.getProduct().getFinalUnitPriceUsd() * currentRate, currentCurrency),
                    String.format("%.2f %s", i.getSubtotalUsd() * currentRate, currentCurrency)});
            totalLabel.setText(String.format("Total Payable: %.2f %s", cartTotal((String) typeCombo.getSelectedItem()), currentCurrency));
        };
        typeCombo.addActionListener(e -> refresh.run());

        RoundedButton removeBtn = new RoundedButton("Remove Item", t.danger);
        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { showError(dialog, "Select an item in the table first."); return; }
            cart.remove(row);
            updateCartBadge();
            refresh.run();
        });
        RoundedButton clearBtn = new RoundedButton("Clear Cart", t.muted);
        clearBtn.addActionListener(e -> { cart.clear(); updateCartBadge(); refresh.run(); });
        RoundedButton payBtn = new RoundedButton("Complete Checkout ✨", t.accent);
        payBtn.addActionListener(e -> {
            Order done = checkout(dialog, nameField.getText().trim(), phoneField.getText().trim(), emailField.getText().trim(),
                    addressField.getText().trim(), (String) typeCombo.getSelectedItem());
            if (done != null) { dialog.dispose(); celebrate(done); }   // order OK -> close cart, then confetti + thank you
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        btns.add(removeBtn);
        btns.add(clearBtn);
        btns.add(payBtn);
        // Layout: 2 columns of boxes on top, then the address, the total and the buttons
        JPanel details = new JPanel(new GridLayout(0, 2, 12, 8));
        details.setOpaque(false);
        details.add(fieldBox("Full Name / Company", nameField));
        details.add(fieldBox("Phone Number", phoneField));
        details.add(fieldBox("Email Address", emailField));
        details.add(fieldBox("Order Type", typeCombo));
        JPanel lower = new JPanel(new BorderLayout(0, 8));
        lower.setOpaque(false);
        lower.add(fieldBox("Delivery Address (house, street, city)", addressField), BorderLayout.NORTH);
        lower.add(totalLabel, BorderLayout.CENTER);
        lower.add(btns, BorderLayout.SOUTH);
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.setOpaque(false);
        form.add(details, BorderLayout.NORTH);
        form.add(lower, BorderLayout.CENTER);
        content.add(form, BorderLayout.SOUTH);

        refresh.run();
        dialog.setVisible(true);   // code pauses here until the popup closes
    }

    // Completes the order. Returns the finished Order, or null if something was wrong.
    private Order checkout(Component parent, String client, String phone, String email, String address, String type) {
        try {
            // VALIDATION: each wrong entry throws our own exception, shown to the user in the catch block below
            if (cart.isEmpty()) throw new InvalidInputException("Cart is empty.");
            if (client.isEmpty()) throw new InvalidInputException("Provide Customer or Corporate Name.");
            if (!phone.matches("\\+?[0-9 \\-]{7,15}")) throw new InvalidInputException("Enter a valid phone number (7-15 digits).");
            if (!email.matches("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")) throw new InvalidInputException("Enter a valid email address, e.g. name@example.com");
            if (address.length() < 5) throw new InvalidInputException("Enter the full delivery address.");
            for (CartItem i : cart)   // check ALL stock first...
                if (i.getQuantity() > i.getProduct().getStock()) throw new OutOfStockException("Stock depleted for item: " + i.getProduct().getName());
            for (CartItem i : cart) i.getProduct().reduceStock(i.getQuantity());   // ...then reduce it

            Order order = new Order(client, phone, email, address, type, cart);
            FileManager.appendOrderAudit(order, currentRate, currentCurrency);
            FileManager.saveInventory(inventory);

            cart.clear();
            updateCartBadge();
            refreshGrid();   // shows the new, lower stock
            reloadLogs();
            return order;
        } catch (StoreException | IOException ex) {
            showError(parent, ex.getMessage());
            return null;
        }
    }

    // 🎉 After a successful order: confetti rains over the window + a "Thank you" pop-up appears
    private void celebrate(Order order) {
        confetti.start();   // the confetti keeps animating (Timer) while the pop-up is open
        JOptionPane.showMessageDialog(this,
                "🎉 Thank you for ordering from SwiftMart, " + order.getClient() + "!\n\n"
                        + "Order ID: " + order.getOrderId() + "\n"
                        + "A confirmation will be sent to " + order.getEmail() + "\n\n"
                        + "=== LIVE ORDER TRACKING STATUS ===\n• Current Status: Order Pending 🕒\n• Estimated Delivery: 2 Days\n"
                        + "• Tracking: Courier assigned & preparing items for dispatch 🛵",
                "Thank You! 🎉", JOptionPane.INFORMATION_MESSAGE);
    }

    // Small helper: a label on top with the input box below it
    private JPanel fieldBox(String label, JComponent field) {
        JPanel box = new JPanel(new BorderLayout(0, 3));
        box.setOpaque(false);
        box.add(lbl(label, 12, Font.BOLD, t.text), BorderLayout.NORTH);
        box.add(field, BorderLayout.CENTER);
        return box;
    }

    // ---------- Admin tab: add new products (supplier stock intake) ----------
    private JPanel buildIntake() {
        JPanel card = new RoundedPanel(new BorderLayout(10, 10), t.card, t.border, 22);
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.add(lbl("Supplier Stock Intake & Product Image Upload", 15, Font.BOLD, t.primary), BorderLayout.NORTH);

        JComboBox<String> catCombo = new JComboBox<>(new String[]{"Electronics", "Clothing", "Groceries", "Furniture", "Candies", "Stationery", "Toys"});
        catCombo.setEditable(true);   // pick one OR type a new category
        styleCombo(catCombo);
        JTextField supplierF = textField(), nameF = textField(), brandF = textField(), priceF = textField(), stockF = textField(), specF = textField();

        RoundedButton browse = new RoundedButton("Browse Image...", t.primary);
        imageNameLabel = lbl("No image selected", 12, Font.PLAIN, t.text);
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "png", "jpeg"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pickedImage = fc.getSelectedFile();
                imageNameLabel.setText(pickedImage.getName());
            }
        });
        JPanel imgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        imgRow.setOpaque(false);
        imgRow.add(browse);
        imgRow.add(imageNameLabel);

        JPanel grid = new JPanel(new GridLayout(8, 2, 10, 10));
        grid.setOpaque(false);
        Object[][] rows = {{"Supplier Co:", supplierF}, {"Category:", catCombo}, {"Product Name:", nameF}, {"Brand:", brandF},
                {"Unit Price:", priceF}, {"Stock Quantity:", stockF}, {"Spec (Warranty/Size):", specF}, {"Product Picture:", imgRow}};
        for (Object[] r : rows) { grid.add(lbl((String) r[0], 12, Font.BOLD, t.text)); grid.add((Component) r[1]); }

        RoundedButton addBtn = new RoundedButton("Register Stock & Publish", t.accent);
        addBtn.addActionListener(e -> {
            try {   // exceptions stop bad input from crashing the app
                String supplier = supplierF.getText().trim();
                if (supplier.isEmpty()) throw new InvalidInputException("Supplier name is required.");
                String cat = (String) catCombo.getEditor().getItem();
                if (cat == null || cat.trim().isEmpty()) throw new InvalidInputException("Category cannot be empty.");

                int id = nextId();
                Product product = new GenericProduct(id, nameF.getText(), brandF.getText(), Double.parseDouble(priceF.getText().trim()),
                        Integer.parseInt(stockF.getText().trim()), cat.trim(), specF.getText().trim(),
                        ImageManagerService.processProductImage(id, pickedImage));
                inventory.add(product);
                FileManager.saveInventory(inventory);
                FileManager.appendSupplierReceipt(supplier, product, currentRate, currentCurrency);

                refreshCategoryFilter();
                refreshGrid();
                reloadLogs();
                for (JTextField f : new JTextField[]{supplierF, nameF, brandF, priceF, stockF, specF}) f.setText("");
                pickedImage = null;
                imageNameLabel.setText("No image selected");
                JOptionPane.showMessageDialog(this, "Product published successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                showError(this, "Price and Stock must be valid numbers.");
            } catch (InvalidInputException ex) {
                showError(this, ex.getMessage());
            } catch (IOException ex) {
                showError(this, "File save failed: " + ex.getMessage());
            }
        });
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnBar.setOpaque(false);
        btnBar.add(addBtn);
        card.add(grid, BorderLayout.CENTER);
        card.add(btnBar, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(t.bg);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.add(card, BorderLayout.CENTER);
        return panel;
    }

    // ---------- Admin tabs: supplier receipts (true) / customer receipts (false) ----------
    private JPanel buildLog(boolean supplier) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBackground(t.card);
        area.setForeground(t.text);
        area.setBorder(new EmptyBorder(8, 8, 8, 8));
        if (supplier) supplierLog = area; else customerLog = area;

        RoundedButton reload = new RoundedButton("Reload", t.primary);
        reload.addActionListener(e -> reloadLogs());
        RoundedButton clear = new RoundedButton("Clear History", t.danger);
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Clear all history?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try {
                FileManager.write(supplier ? FileManager.SUPPLIER_FILE : FileManager.CUSTOMER_FILE, "", false);
                reloadLogs();
            } catch (IOException ex) { showError(this, "Failed to clear receipts."); }
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bar.setOpaque(false);
        bar.add(reload);
        bar.add(clear);
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(new LineBorder(t.border, 1, true));
        sp.getViewport().setBackground(t.card);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(t.bg);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.add(sp, BorderLayout.CENTER);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    // Reloads the receipt text when the admin opens tab 2 or 3
    private void reloadLogs() {
        if (!isAdmin || tabs == null) return;
        try {
            int i = tabs.getSelectedIndex();
            if (i == 2 && supplierLog != null) supplierLog.setText(FileManager.read(FileManager.SUPPLIER_FILE, "No supplier intake receipts recorded in system."));
            if (i == 3 && customerLog != null) customerLog.setText(FileManager.read(FileManager.CUSTOMER_FILE, "No customer order logs recorded in system."));
        } catch (IOException e) { showError(this, "Failed to load audit logs."); }
    }

    // ---------- Data + currency ----------
    private void loadInitialData() {
        try {
            inventory.addAll(FileManager.loadInventory());
            if (inventory.isEmpty()) {   // first run: add 3 sample products
                inventory.add(new GenericProduct(101, "Workstation", "Dell", 1100.00, 12, "Electronics", "1 Year Warranty", ImageManagerService.processProductImage(101, null)));
                inventory.add(new GenericProduct(102, "Executive Suit", "Armani", 350.00, 15, "Clothing", "Size: L", ImageManagerService.processProductImage(102, null)));
                inventory.add(new GenericProduct(103, "Organic Coffee", "Nestle", 18.00, 99, "Groceries", "Exp: 2027", ImageManagerService.processProductImage(103, null)));
                FileManager.saveInventory(inventory);
            }
        } catch (Exception e) {
            System.err.println("Failed to load inventory data: " + e.getMessage());
        }
    }

    private int nextId() {   // highest id + 1
        int max = 100;
        for (Product p : inventory) max = Math.max(max, p.getId());
        return max + 1;
    }

    // The API call runs in a background Thread so the window never freezes
    private void changeCurrency(String cur, JComboBox<String> combo) {
        if (cur.equals(currentCurrency)) return;
        statusLabel.setText("Converting...");
        combo.setEnabled(false);
        new Thread(() -> {
            try {
                double rate = priceService.getRate(cur);
                SwingUtilities.invokeLater(() -> {   // UI changes must happen on the UI thread
                    currentCurrency = cur;
                    currentRate = rate;
                    statusLabel.setText("Currency: " + cur);
                    refreshGrid();
                    combo.setEnabled(true);
                });
            } catch (StoreException ex) {
                SwingUtilities.invokeLater(() -> {
                    showError(this, ex.getMessage());
                    statusLabel.setText("Currency: " + currentCurrency);
                    combo.setSelectedItem(currentCurrency);
                    combo.setEnabled(true);
                });
            }
        }).start();
    }

    // ---------- Small helpers ----------
    private JLabel lbl(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private JTextField textField() {
        JTextField tf = new JTextField();
        tf.setBackground(t.input);
        tf.setForeground(t.text);
        tf.setCaretColor(t.text);
        tf.setBorder(new CompoundBorder(new LineBorder(t.border, 1, true), new EmptyBorder(5, 8, 5, 8)));
        return tf;
    }

    private void styleCombo(JComboBox<?> c) {
        c.setBackground(t.input);
        c.setForeground(t.text);
        c.setFont(new Font("SansSerif", Font.PLAIN, 12));
    }

    private void showError(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}