import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new RoleSelectionDialog().setVisible(true));
    }
}

// ==========================================
// 0. SOBER & PROFESSIONAL LOGIN DIALOG
// ==========================================
class RoleSelectionDialog extends JFrame {
    private static final Color BG_COLOR = new Color(255, 240, 245);       // Soft Lavender Blush
    private static final Color CARD_BG = Color.WHITE;                     // Pure Crisp White
    private static final Color BORDER_COLOR = new Color(248, 187, 208);   // Delicate Rose Border
    private static final Color PRIMARY_COLOR = new Color(216, 27, 96);    // Deep Raspberry Pink
    private static final Color ACCENT_COLOR = new Color(233, 30, 99);     // Vibrant Magenta/Hot Pink
    private static final Color TEXT_MUTED = new Color(173, 20, 87);       // Muted Plum Text

    public RoleSelectionDialog() {
        setTitle("SwiftMart | Secure Access Portal");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(460, 420);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_COLOR);
        setLayout(new GridBagLayout());

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 2, true),
                new EmptyBorder(30, 35, 30, 35)
        ));

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

        JButton shopperBtn = createProfessionalButton("🛍️ Continue as Shopper", PRIMARY_COLOR);
        JButton adminBtn = createProfessionalButton("🔐 Business Owner Login", ACCENT_COLOR);
        JButton exitBtn = createProfessionalButton("Exit Application", new Color(194, 24, 91));

        shopperBtn.addActionListener(e -> {
            dispose();
            new StoreManagementFrame(false).setVisible(true);
        });

        // EXCEPTION HANDLING / Password Authentication
        adminBtn.addActionListener(e -> {
            JPasswordField passField = new JPasswordField();
            int option = JOptionPane.showConfirmDialog(this, passField, "Enter Admin Password:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option == JOptionPane.OK_OPTION) {
                String pass = new String(passField.getPassword());
                if ("admin123".equals(pass)) {
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

    private JButton createProfessionalButton(String text, Color bgColor) {
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

// ==========================================
// 1. INTERFACES & CUSTOM EXCEPTION CLASSES
// ==========================================
interface Discountable {
    double getDiscountPercent();
}

interface PriceConverter {
    double getRate(String currency) throws StoreException;
}

// Custom Exception classes for handling store errors safely
class StoreException extends Exception {
    StoreException(String msg) { super(msg); }
}
class InvalidInputException extends StoreException {
    InvalidInputException(String msg) { super(msg); }
}
class OutOfStockException extends StoreException {
    OutOfStockException(String msg) { super(msg); }
}

// ==========================================
// 2. API & IMAGE FILE MANAGEMENT SERVICES
// ==========================================
class ExchangeRateService implements PriceConverter {
    private static final String API_KEY = "API KEY ";

    @Override
    public double getRate(String currency) throws StoreException {
        if ("USD".equalsIgnoreCase(currency)) return 1.0;

        if (API_KEY.contains("PASTE_YOUR_GROQ_KEY_HERE") || API_KEY.trim().isEmpty()) {
            throw new StoreException("API Key Missing! Replace API_KEY inside ExchangeRateService.");
        }

        // EXCEPTION HANDLING for network connection timeouts
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            String jsonPayload = "{\n" +
                    "  \"model\": \"openai/gpt-oss-20b\",\n" +
                    "  \"messages\": [\n" +
                    "    {\n" +
                    "      \"role\": \"system\",\n" +
                    "      \"content\": \"You are a financial calculator. Output strictly valid floating point exchange rates with no additional markdown, text, or explanations.\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"role\": \"user\",\n" +
                    "      \"content\": \"What is the exchange rate from 1 USD to " + currency + "? Reply ONLY with the raw number.\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new StoreException("Groq API Error (" + res.statusCode() + ")");
            }

            return parseGroqResponse(res.body());
        } catch (IOException | InterruptedException e) {
            throw new StoreException("Network error connecting to Groq API.");
        }
    }

    private double parseGroqResponse(String json) throws StoreException {
        try {
            int contentIdx = json.indexOf("\"content\":");
            if (contentIdx < 0) throw new StoreException("Unexpected Groq response structure.");
            int start = json.indexOf("\"", contentIdx + 10) + 1;
            int end = json.indexOf("\"", start);
            String rawText = json.substring(start, end).replaceAll("[^0-9.]", "").trim();
            return Double.parseDouble(rawText);
        } catch (Exception e) {
            throw new StoreException("Failed to parse numeric rate from Groq API response.");
        }
    }
}

// FILE HANDLING: Processes product images on disk
class ImageManagerService {
    private static final String IMAGE_DIR = "inventory_images";

    public static String processProductImage(int productId, File sourceFile) {
        try {
            File dir = new File(IMAGE_DIR);
            if (!dir.exists()) dir.mkdirs();

            String destPath = IMAGE_DIR + "/product_" + productId + ".jpg";
            File destFile = new File(destPath);

            if (sourceFile != null && sourceFile.exists()) {
                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return destPath;
            } else if (!destFile.exists()) {
                createDefaultPlaceholder(destFile);
            }
            return destFile.exists() ? destPath : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void createDefaultPlaceholder(File file) {
        try {
            int width = 300, height = 300;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setColor(new Color(255, 240, 245));
            g2d.fillRect(0, 0, width, height);
            g2d.setColor(new Color(216, 27, 96));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2d.drawString("No Product Image", 80, 150);
            g2d.dispose();
            javax.imageio.ImageIO.write(img, "jpg", file);
        } catch (Exception ignored) { }
    }
}

// ==========================================
// 3. OOP DOMAIN MODELS
// ==========================================
abstract class Product implements Discountable {
    private final int id;
    private String name;
    private String brand;
    private double priceUsd;
    private int stock;
    private String customCategory;
    private String imageFilePath;
    private String customerFeedback = "No reviews yet.";

    Product(int id, String name, String brand, double priceUsd, int stock, String customCategory, String imageFilePath) throws InvalidInputException {
        this.id = id;
        setName(name);
        setBrand(brand);
        setPriceUsd(priceUsd);
        setStock(stock);
        setCustomCategory(customCategory);
        this.imageFilePath = imageFilePath == null ? "" : imageFilePath.trim();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public double getPriceUsd() { return priceUsd; }
    public int getStock() { return stock; }
    public String getImageFilePath() { return imageFilePath; }
    public void setImageFilePath(String path) { this.imageFilePath = path == null ? "" : path.trim(); }
    public String getCustomerFeedback() { return customerFeedback; }
    public void setCustomerFeedback(String feedback) { this.customerFeedback = feedback; }

    public void setName(String n) throws InvalidInputException {
        if (n == null || n.trim().isEmpty()) throw new InvalidInputException("Product name cannot be empty.");
        this.name = n.trim().replace(",", " ");
    }
    public void setBrand(String b) throws InvalidInputException {
        if (b == null || b.trim().isEmpty()) throw new InvalidInputException("Brand cannot be empty.");
        this.brand = b.trim().replace(",", " ");
    }
    public void setPriceUsd(double p) throws InvalidInputException {
        if (p <= 0) throw new InvalidInputException("Price must be greater than 0.");
        this.priceUsd = p;
    }
    public void setStock(int s) throws InvalidInputException {
        if (s < 0) throw new InvalidInputException("Stock cannot be negative.");
        this.stock = s;
    }
    public void setCustomCategory(String c) throws InvalidInputException {
        if (c == null || c.trim().isEmpty()) throw new InvalidInputException("Category cannot be empty.");
        this.customCategory = c.trim().replace(",", " ");
    }
    public void reduceStock(int qty) throws OutOfStockException {
        if (qty > stock) throw new OutOfStockException("Insufficient stock for product: " + name);
        this.stock -= qty;
    }

    public double getFinalUnitPriceUsd() {
        return priceUsd - (priceUsd * getDiscountPercent() / 100.0);
    }

    public String getCategory() { return customCategory; }
    public abstract String getAttribute();

    // FILE HANDLING: Formats product data to text line for saving
    public String toFileFormat() {
        return getCategory() + "," + id + "," + name + "," + brand + "," + priceUsd + "," + stock + "," + getAttribute() + "," + imageFilePath + "," + customerFeedback.replace(",", " ");
    }
}

class GenericProduct extends Product {
    private final String specAttribute;

    GenericProduct(int id, String n, String b, double p, int s, String category, String specAttribute, String imageFilePath) throws InvalidInputException {
        super(id, n, b, p, s, category, imageFilePath);
        this.specAttribute = (specAttribute == null || specAttribute.trim().isEmpty()) ? "N/A" : specAttribute.trim().replace(",", " ");
    }

    @Override public double getDiscountPercent() { return 5.0; }
    @Override public String getAttribute() { return specAttribute; }
}

// ==========================================
// 4. BUSINESS LOGIC & ORDER MANAGEMENT
// ==========================================
class CartItem {
    private final Product product;
    private int quantity;

    CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int q) { this.quantity = q; }
    public double getSubtotalUsd() { return product.getFinalUnitPriceUsd() * quantity; }
}

class Order {
    private final String orderId;
    private final String clientName;
    private final String orderType;
    private final List<CartItem> items;
    private final double totalUsd;

    Order(String clientName, String orderType, List<CartItem> items) {
        this.orderId = "ORD-" + System.currentTimeMillis() % 100000;
        this.clientName = clientName;
        this.orderType = orderType;
        this.items = new ArrayList<>(items);

        double baseTotal = 0;
        int totalQuantity = 0;
        for (CartItem c : items) {
            baseTotal += c.getSubtotalUsd();
            totalQuantity += c.getQuantity();
        }

        double multiplier = 1.0;
        if ("Business to Business (Wholesale)".equals(orderType)) {
            multiplier = 0.85;
        } else if (totalQuantity >= 10) {
            multiplier = 0.95;
        }
        this.totalUsd = baseTotal * multiplier;
    }

    public String getOrderId() { return orderId; }

    public String generateReceipt(double conversionRate, String currency) {
        StringBuilder sb = new StringBuilder();
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sb.append("====================================================\n");
        sb.append("            SWIFTMART MANAGEMENT RECEIPT            \n");
        sb.append("====================================================\n");
        sb.append("Order Ref   : ").append(orderId).append("\n");
        sb.append("Client/Co   : ").append(clientName).append("\n");
        sb.append("Order Mode  : ").append(orderType).append("\n");
        sb.append("Timestamp   : ").append(date).append("\n");
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("%-18s %-10s %-8s %-10s%n", "Item", "Brand", "Qty", "Subtotal"));
        sb.append("----------------------------------------------------\n");
        for (CartItem item : items) {
            sb.append(String.format("%-18s %-10s x%-7d %.2f %s%n",
                    item.getProduct().getName(),
                    item.getProduct().getBrand(),
                    item.getQuantity(),
                    item.getSubtotalUsd() * conversionRate,
                    currency));
        }
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("TOTAL PAYABLE: %.2f %s%n", totalUsd * conversionRate, currency));
        sb.append("====================================================\n\n");
        return sb.toString();
    }
}

// ==========================================
// 5. FILE PERSISTENCE & LOG MANAGEMENT
// ==========================================
class FileManager {
    private static final String PRODUCT_FILE = "inventory.txt";
    private static final String CUSTOMER_RECEIPT_FILE = "order_audit.txt";
    private static final String SUPPLIER_RECEIPT_FILE = "supplier_payouts.txt";

    // FILE HANDLING: Reads inventory text from hard drive
    static List<Product> loadInventory() throws IOException {
        List<Product> list = new ArrayList<>();
        File file = new File(PRODUCT_FILE);
        if (!file.exists()) return list;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    String[] token = line.split(",");
                    String cat = token[0];
                    int id = Integer.parseInt(token[1]);
                    String name = token[2];
                    String brand = token[3];
                    double price = Double.parseDouble(token[4]);
                    int stock = Integer.parseInt(token[5]);
                    String attr = token[6];
                    String imgPath = token.length > 7 ? token[7] : "";
                    String feedback = token.length > 8 ? token[8] : "No reviews yet.";

                    GenericProduct p = new GenericProduct(id, name, brand, price, stock, cat, attr, imgPath);
                    p.setCustomerFeedback(feedback);
                    list.add(p);
                } catch (Exception e) {
                    System.err.println("Corrupted file entry ignored: " + line);
                }
            }
        }
        return list;
    }

    // FILE HANDLING: Writes inventory items to 'inventory.txt'
    static void saveInventory(List<Product> products) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(PRODUCT_FILE))) {
            for (Product p : products) pw.println(p.toFileFormat());
        }
    }

    // FILE HANDLING: Appends order receipt text to 'order_audit.txt'
    static void appendOrderAudit(Order order, double rate, String currency) throws IOException {
        try (FileWriter fw = new FileWriter(CUSTOMER_RECEIPT_FILE, true)) {
            fw.write(order.generateReceipt(rate, currency));
        }
    }

    // FILE HANDLING: Appends supplier receipt to 'supplier_payouts.txt'
    static void appendSupplierReceipt(String supplier, Product product, double rate, String currency) throws IOException {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        double totalPayoutUsd = product.getPriceUsd() * product.getStock();

        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("           SWIFTMART STOCK INWARD RECEIPT           \n");
        sb.append("====================================================\n");
        sb.append("Receipt Ref : SUP-").append(System.currentTimeMillis() % 100000).append("\n");
        sb.append("Supplier Co : ").append(supplier).append("\n");
        sb.append("Timestamp   : ").append(date).append("\n");
        sb.append("----------------------------------------------------\n");
        sb.append("Item Stocked: ").append(product.getName()).append(" (").append(product.getBrand()).append(")\n");
        sb.append("Category    : ").append(product.getCategory()).append("\n");
        sb.append("Qty Inward  : ").append(product.getStock()).append(" units\n");
        sb.append(String.format("Unit Cost   : %.2f %s%n", product.getPriceUsd() * rate, currency));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("PAYABLE DUE : %.2f %s%n", totalPayoutUsd * rate, currency));
        sb.append("====================================================\n\n");

        try (FileWriter fw = new FileWriter(SUPPLIER_RECEIPT_FILE, true)) {
            fw.write(sb.toString());
        }
    }

    static String readCustomerReceipts() throws IOException {
        return readTextFile(CUSTOMER_RECEIPT_FILE, "No customer order logs recorded in system.");
    }

    static String readSupplierReceipts() throws IOException {
        return readTextFile(SUPPLIER_RECEIPT_FILE, "No supplier intake receipts recorded in system.");
    }

    static void clearCustomerReceipts() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(CUSTOMER_RECEIPT_FILE))) {
            pw.print("");
        }
    }

    static void clearSupplierReceipts() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(SUPPLIER_RECEIPT_FILE))) {
            pw.print("");
        }
    }

    private static String readTextFile(String filename, String defaultMsg) throws IOException {
        File file = new File(filename);
        if (!file.exists()) return defaultMsg;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.length() == 0 ? defaultMsg : sb.toString();
    }
}

// ==========================================
// 6. CHIC PINK & AESTHETIC DASHBOARD
// ==========================================
class StoreManagementFrame extends JFrame {

    // Aesthetic Pink Theme Colors 🌸
    private static final Color BG_COLOR = new Color(255, 240, 245);          // Lavender Blush Background
    private static final Color CARD_BG = Color.WHITE;                         // Clean White Card
    private static final Color BORDER_COLOR = new Color(248, 187, 208);       // Rose Border
    private static final Color HEADER_BG = Color.WHITE;                       // White Header
    private static final Color PRIMARY_COLOR = new Color(216, 27, 96);        // Raspberry Pink Primary
    private static final Color ACCENT_COLOR = new Color(233, 30, 99);         // Hot Pink Accent
    private static final Color TEXT_DARK = new Color(136, 14, 79);            // Deep Plum Text
    private static final Color TEXT_MUTED = new Color(173, 20, 87);           // Muted Plum Text

    private final List<Product> inventory = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private final PriceConverter priceService = new ExchangeRateService();

    private final boolean isAdmin;

    private String currentCurrency = "USD";
    private double currentRate = 1.0;

    private JPanel catalogGridPanel;
    private DefaultTableModel cartModel;
    private JTable cartTable;
    private JLabel totalLabel, statusLabel, selectedImageLabel;
    private JTextArea customerAuditArea, supplierAuditArea;
    private JTextField clientNameField;
    private JComboBox<String> orderTypeCombo;
    private File uploadedImageFile = null;
    private JTabbedPane tabs;

    public StoreManagementFrame(boolean isAdmin) {
        this.isAdmin = isAdmin;
        setTitle("SwiftMart 🌸 | " + (isAdmin ? "Admin Portal" : "Shopper Marketplace"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1300, 850);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_COLOR);

        loadInitialData();

        setLayout(new BorderLayout());
        add(buildHeaderBar(), BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        tabs.setBackground(BG_COLOR);
        tabs.setForeground(TEXT_DARK);

        tabs.addTab("  Marketplace Catalog 🛍️  ", buildPOSPanel());

        if (isAdmin) {
            tabs.addTab("  Supplier Intake & Admin  ", buildInventoryAdminPanel());
            tabs.addTab("  Supplier Inward Receipts  ", buildSupplierAuditPanel());
            tabs.addTab("  Customer Sales Receipts  ", buildCustomerAuditPanel());
        }

        tabs.addChangeListener(e -> refreshAllTabs(tabs.getSelectedIndex()));

        add(tabs, BorderLayout.CENTER);
        refreshCatalogGrid();
        refreshAllTabs(0);
    }

    private JPanel buildHeaderBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(HEADER_BG);
        bar.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 20, 10, 20)
        ));

        String roleTitle = isAdmin ? "SwiftMart 👑  —  [Admin Management]" : "SwiftMart 🛍️  —  [Shopper Portal]";
        JLabel title = new JLabel(roleTitle);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(PRIMARY_COLOR);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        statusLabel = new JLabel("Currency: USD");
        statusLabel.setForeground(TEXT_DARK);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        JComboBox<String> currCombo = new JComboBox<>(new String[]{"USD", "PKR", "EUR", "GBP", "AED", "INR"});
        currCombo.setBackground(Color.WHITE);
        currCombo.setForeground(TEXT_DARK);
        currCombo.addActionListener(e -> changeCurrency((String) currCombo.getSelectedItem(), currCombo));

        JButton switchRoleBtn = new JButton("Switch Role");
        switchRoleBtn.setBackground(PRIMARY_COLOR);
        switchRoleBtn.setForeground(Color.WHITE);
        switchRoleBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        switchRoleBtn.setFocusPainted(false);
        switchRoleBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        switchRoleBtn.addActionListener(e -> {
            dispose();
            new RoleSelectionDialog().setVisible(true);
        });

        right.add(statusLabel);
        right.add(currCombo);
        right.add(switchRoleBtn);

        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildPOSPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 15, 15));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel left = createCardPanel(isAdmin ? "Catalog Management (Admin Delete Enabled)" : "Store Product Catalog");
        catalogGridPanel = new JPanel(new GridLayout(0, 2, 12, 12));
        catalogGridPanel.setBackground(CARD_BG);
        JScrollPane catalogScroll = new JScrollPane(catalogGridPanel);
        catalogScroll.getVerticalScrollBar().setUnitIncrement(16);
        left.add(catalogScroll, BorderLayout.CENTER);

        JPanel right = createCardPanel("Active Order Terminal 💳");
        cartModel = new DefaultTableModel(new String[]{"Item", "Brand", "Qty", "Unit Price", "Subtotal"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        cartTable = new JTable(cartModel);
        cartTable.setRowHeight(25);
        cartTable.getTableHeader().setBackground(HEADER_BG);
        cartTable.getTableHeader().setForeground(PRIMARY_COLOR);
        cartTable.setBackground(CARD_BG);
        cartTable.setForeground(TEXT_DARK);

        JPanel checkoutForm = new JPanel(new GridLayout(5, 1, 6, 6));
        checkoutForm.setOpaque(false);

        clientNameField = createStyledTextField();
        clientNameField.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COLOR), "Customer / Corporate Name", 0, 0, null, TEXT_DARK));

        orderTypeCombo = new JComboBox<>(new String[]{"Business to Customer (Retail)", "Business to Business (Wholesale)"});
        orderTypeCombo.setBackground(Color.WHITE);
        orderTypeCombo.setForeground(TEXT_DARK);
        orderTypeCombo.addActionListener(e -> refreshCartTable());

        totalLabel = new JLabel("Total Payable: 0.00 USD");
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        totalLabel.setForeground(PRIMARY_COLOR);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);
        JButton removeBtn = createStyledButton("Remove Item", new Color(194, 24, 91));
        JButton processBtn = createStyledButton("Complete Checkout ✨", ACCENT_COLOR);

        removeBtn.addActionListener(e -> {
            int row = cartTable.getSelectedRow();
            if (row >= 0) { cart.remove(row); refreshCartTable(); }
        });

        processBtn.addActionListener(e -> processCheckout());

        btnRow.add(removeBtn);
        btnRow.add(processBtn);

        checkoutForm.add(clientNameField);
        checkoutForm.add(orderTypeCombo);
        checkoutForm.add(totalLabel);
        checkoutForm.add(btnRow);

        right.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        right.add(checkoutForm, BorderLayout.SOUTH);

        panel.add(left);
        panel.add(right);
        return panel;
    }

    private JPanel buildInventoryAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel formCard = createCardPanel("Supplier Stock Intake & Product Image Upload");
        JPanel grid = new JPanel(new GridLayout(8, 2, 10, 10));
        grid.setOpaque(false);

        String[] defaultCategories = {"Electronics", "Clothing", "Groceries", "Furniture", "Candies", "Stationery", "Toys"};
        JComboBox<String> categoryCombo = new JComboBox<>(defaultCategories);
        categoryCombo.setEditable(true);
        categoryCombo.setBackground(Color.WHITE);
        categoryCombo.setForeground(TEXT_DARK);

        JTextField supplierF = createStyledTextField();
        JTextField nameF = createStyledTextField();
        JTextField brandF = createStyledTextField();
        JTextField priceF = createStyledTextField();
        JTextField stockF = createStyledTextField();
        JTextField extraF = createStyledTextField();

        JButton uploadImgBtn = createStyledButton("Browse Image...", PRIMARY_COLOR);
        selectedImageLabel = new JLabel("No image selected");
        selectedImageLabel.setForeground(TEXT_DARK);

        uploadImgBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "png", "jpeg"));
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                uploadedImageFile = fileChooser.getSelectedFile();
                selectedImageLabel.setText(uploadedImageFile.getName());
            }
        });

        JButton addBtn = createStyledButton("Register Stock & Publish", ACCENT_COLOR);

        // EXCEPTION HANDLING: Prevents crashes when bad numbers are typed
        addBtn.addActionListener(e -> {
            try {
                String supplier = supplierF.getText().trim();
                if (supplier.isEmpty()) throw new InvalidInputException("Supplier name is required.");

                String category = (String) categoryCombo.getEditor().getItem();
                if (category == null || category.trim().isEmpty()) {
                    throw new InvalidInputException("Category cannot be empty.");
                }

                int id = getNextId();
                String name = nameF.getText();
                String brand = brandF.getText();
                double p = Double.parseDouble(priceF.getText().trim());
                int s = Integer.parseInt(stockF.getText().trim());
                String extra = extraF.getText().trim();

                String localImagePath = ImageManagerService.processProductImage(id, uploadedImageFile);
                Product product = new GenericProduct(id, name, brand, p, s, category.trim(), extra, localImagePath);

                inventory.add(product);
                FileManager.saveInventory(inventory); // FILE HANDLING: Saves stock to file
                FileManager.appendSupplierReceipt(supplier, product, currentRate, currentCurrency); // FILE HANDLING

                refreshCatalogGrid();
                refreshAllTabs(tabs.getSelectedIndex());

                supplierF.setText(""); nameF.setText(""); brandF.setText(""); priceF.setText(""); stockF.setText(""); extraF.setText("");
                uploadedImageFile = null;
                selectedImageLabel.setText("No image selected");

                JOptionPane.showMessageDialog(this, "Product published successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                showError("Price and Stock must be valid numbers.");
            } catch (InvalidInputException ex) {
                showError(ex.getMessage());
            } catch (IOException ex) {
                showError("File save failed: " + ex.getMessage());
            }
        });

        grid.add(createLabel("Supplier Co:")); grid.add(supplierF);
        grid.add(createLabel("Category:")); grid.add(categoryCombo);
        grid.add(createLabel("Product Name:")); grid.add(nameF);
        grid.add(createLabel("Brand:")); grid.add(brandF);
        grid.add(createLabel("Unit Price:")); grid.add(priceF);
        grid.add(createLabel("Stock Quantity:")); grid.add(stockF);
        grid.add(createLabel("Spec (Warranty/Size):")); grid.add(extraF);
        grid.add(createLabel("Product Picture:"));

        JPanel imgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        imgRow.setOpaque(false);
        imgRow.add(uploadImgBtn);
        imgRow.add(selectedImageLabel);
        grid.add(imgRow);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnBar.setOpaque(false);
        btnBar.add(addBtn);

        formCard.add(grid, BorderLayout.CENTER);
        formCard.add(btnBar, BorderLayout.SOUTH);

        panel.add(formCard, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSupplierAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        supplierAuditArea = createConsoleArea();

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setOpaque(false);

        JButton refresh = createStyledButton("Reload Ledger", PRIMARY_COLOR);
        refresh.addActionListener(e -> refreshAllTabs(tabs.getSelectedIndex()));

        JButton clearBtn = createStyledButton("Clear History", new Color(194, 24, 91));
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Clear all supplier receipts?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    FileManager.clearSupplierReceipts();
                    refreshAllTabs(tabs.getSelectedIndex());
                } catch (IOException ex) {
                    showError("Failed to clear receipts.");
                }
            }
        });

        actionPanel.add(refresh);
        actionPanel.add(clearBtn);

        panel.add(new JScrollPane(supplierAuditArea), BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildCustomerAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        customerAuditArea = createConsoleArea();

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setOpaque(false);

        JButton refresh = createStyledButton("Reload Sales Ledger", PRIMARY_COLOR);
        refresh.addActionListener(e -> refreshAllTabs(tabs.getSelectedIndex()));

        JButton clearBtn = createStyledButton("Clear Sales History", new Color(194, 24, 91));
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Clear all sales history?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    FileManager.clearCustomerReceipts();
                    refreshAllTabs(tabs.getSelectedIndex());
                } catch (IOException ex) {
                    showError("Failed to clear receipts.");
                }
            }
        });

        actionPanel.add(refresh);
        actionPanel.add(clearBtn);

        panel.add(new JScrollPane(customerAuditArea), BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void addToCart(Product p, int qty) {
        if (qty > p.getStock()) {
            showError("Requested quantity exceeds available stock (" + p.getStock() + ").");
            return;
        }

        for (CartItem item : cart) {
            if (item.getProduct().getId() == p.getId()) {
                int newQty = item.getQuantity() + qty;
                if (newQty > p.getStock()) {
                    showError("Total quantity in cart exceeds stock.");
                    return;
                }
                item.setQuantity(newQty);
                refreshCartTable();
                return;
            }
        }

        cart.add(new CartItem(p, qty));
        refreshCartTable();
    }

    // Process Checkout with ORDER PENDING Status Notification
    private void processCheckout() {
        try {
            String client = clientNameField.getText().trim();
            if (client.isEmpty()) throw new InvalidInputException("Provide Customer or Corporate Name.");
            if (cart.isEmpty()) throw new InvalidInputException("Cart is empty.");

            for (CartItem item : cart) {
                if (item.getQuantity() > item.getProduct().getStock()) {
                    throw new OutOfStockException("Stock depleted for item: " + item.getProduct().getName());
                }
            }

            for (CartItem item : cart) {
                item.getProduct().reduceStock(item.getQuantity());
            }

            String type = (String) orderTypeCombo.getSelectedItem();
            Order order = new Order(client, type, cart);

            FileManager.appendOrderAudit(order, currentRate, currentCurrency); // FILE HANDLING
            FileManager.saveInventory(inventory); // FILE HANDLING

            // ORDER PENDING TRACKER STATUS
            JOptionPane.showMessageDialog(this,
                    "Transaction completed successfully! ✨\nOrder ID: " + order.getOrderId() + "\n\n" +
                            "=== LIVE ORDER TRACKING STATUS ===\n" +
                            "• Current Status: Order Pending 🕒\n" +
                            "• Estimated Delivery: 2 Days\n" +
                            "• Tracking: Courier assigned & preparing items for dispatch 🛵",
                    "Order Status Tracker", JOptionPane.INFORMATION_MESSAGE);

            cart.clear();
            clientNameField.setText("");
            refreshCartTable();
            refreshCatalogGrid();
            if (isAdmin) refreshAllTabs(tabs.getSelectedIndex());

        } catch (StoreException | IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void loadInitialData() {
        try {
            inventory.addAll(FileManager.loadInventory()); // FILE HANDLING: Loading
            if (inventory.isEmpty()) {
                inventory.add(new GenericProduct(101, "Workstation", "Dell", 1100.00, 12, "Electronics", "1 Year Warranty", ImageManagerService.processProductImage(101, null)));
                inventory.add(new GenericProduct(102, "Executive Suit", "Armani", 350.00, 15, "Clothing", "Size: L", ImageManagerService.processProductImage(102, null)));
                inventory.add(new GenericProduct(103, "Organic Coffee", "Nestle", 18.00, 99, "Groceries", "Exp: 2027", ImageManagerService.processProductImage(103, null)));
                FileManager.saveInventory(inventory); // FILE HANDLING: Saving
            }
        } catch (Exception e) {
            System.err.println("Failed to load inventory data: " + e.getMessage());
        }
    }

    private int getNextId() {
        int max = 100;
        for (Product p : inventory) {
            if (p.getId() > max) max = p.getId();
        }
        return max + 1;
    }

    private void changeCurrency(String newCurr, JComboBox<String> combo) {
        if (newCurr.equals(currentCurrency)) return;
        statusLabel.setText("Converting...");
        combo.setEnabled(false);
        new Thread(() -> {
            try {
                double rate = priceService.getRate(newCurr);
                SwingUtilities.invokeLater(() -> {
                    currentCurrency = newCurr;
                    currentRate = rate;
                    statusLabel.setText("Currency: " + currentCurrency);
                    refreshCartTable();
                    refreshCatalogGrid();
                    combo.setEnabled(true);
                });
            } catch (StoreException ex) {
                SwingUtilities.invokeLater(() -> {
                    showError(ex.getMessage());
                    combo.setSelectedItem(currentCurrency);
                    combo.setEnabled(true);
                });
            }
        }).start();
    }

    // Refresh Catalog with CUSTOMER FEEDBACK Button
    private void refreshCatalogGrid() {
        catalogGridPanel.removeAll();
        for (Product p : inventory) {
            JPanel card = new JPanel(new BorderLayout(5, 5));
            card.setBackground(CARD_BG);
            card.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR, 1, true),
                    new EmptyBorder(10, 10, 10, 10)
            ));

            JLabel imgLbl = new JLabel();
            imgLbl.setHorizontalAlignment(JLabel.CENTER);
            if (!p.getImageFilePath().isEmpty() && new File(p.getImageFilePath()).exists()) {
                ImageIcon icon = new ImageIcon(p.getImageFilePath());
                Image scaled = icon.getImage().getScaledInstance(140, 140, Image.SCALE_SMOOTH);
                imgLbl.setIcon(new ImageIcon(scaled));
            } else {
                imgLbl.setText("[No Image]");
                imgLbl.setForeground(TEXT_DARK);
            }
            card.add(imgLbl, BorderLayout.NORTH);

            JPanel infoPanel = new JPanel(new GridLayout(5, 1, 2, 2));
            infoPanel.setOpaque(false);

            JLabel nameLbl = new JLabel(p.getName() + " (" + p.getBrand() + ")");
            nameLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
            nameLbl.setForeground(TEXT_DARK);

            JLabel catLbl = new JLabel("Category: " + p.getCategory());
            catLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
            catLbl.setForeground(TEXT_MUTED);

            double convertedPrice = p.getPriceUsd() * currentRate;
            JLabel priceLbl = new JLabel(String.format("Price: %.2f %s", convertedPrice, currentCurrency));
            priceLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
            priceLbl.setForeground(PRIMARY_COLOR);

            JLabel stockLbl = new JLabel("Stock Available: " + p.getStock());
            stockLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
            stockLbl.setForeground(TEXT_DARK);

            JLabel reviewLbl = new JLabel("<html><font color='#AD1457'>Reviews: " + p.getCustomerFeedback() + "</font></html>");
            reviewLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));

            infoPanel.add(nameLbl);
            infoPanel.add(catLbl);
            infoPanel.add(priceLbl);
            infoPanel.add(stockLbl);
            infoPanel.add(reviewLbl);
            card.add(infoPanel, BorderLayout.CENTER);

            JPanel cardBottom = new JPanel(new GridLayout(isAdmin ? 3 : 2, 1, 4, 4));
            cardBottom.setOpaque(false);

            JButton addBtn = createStyledButton("Add to Cart 🛒", ACCENT_COLOR);
            addBtn.addActionListener(e -> {
                String qtyStr = JOptionPane.showInputDialog(this, "Enter quantity for " + p.getName() + ":", "1");
                if (qtyStr != null && !qtyStr.trim().isEmpty()) {
                    try {
                        int qty = Integer.parseInt(qtyStr.trim());
                        addToCart(p, qty);
                    } catch (Exception ex) {
                        showError("Invalid quantity number.");
                    }
                }
            });
            cardBottom.add(addBtn);

            // ADDED: Customer Feedback / Review Button
            JButton reviewBtn = createStyledButton("Add Review ⭐", PRIMARY_COLOR);
            reviewBtn.addActionListener(e -> {
                String review = JOptionPane.showInputDialog(this, "Write your review/rating for " + p.getName() + ":");
                if (review != null && !review.trim().isEmpty()) {
                    p.setCustomerFeedback(review);
                    try {
                        FileManager.saveInventory(inventory); // FILE HANDLING: Persist review
                        refreshCatalogGrid();
                        JOptionPane.showMessageDialog(this, "Thank you for your feedback!");
                    } catch (IOException ex) {
                        showError("Failed to save feedback.");
                    }
                }
            });
            cardBottom.add(reviewBtn);

            if (isAdmin) {
                JButton deleteProductBtn = createStyledButton("Remove Product", new Color(194, 24, 91));
                deleteProductBtn.addActionListener(e -> {
                    int confirm = JOptionPane.showConfirmDialog(this, "Remove '" + p.getName() + "' from store?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        inventory.removeIf(prod -> prod.getId() == p.getId());
                        try {
                            FileManager.saveInventory(inventory); // FILE HANDLING
                            refreshCatalogGrid();
                            JOptionPane.showMessageDialog(this, "Product removed successfully.");
                        } catch (IOException ex) {
                            showError("Failed to save changes.");
                        }
                    }
                });
                cardBottom.add(deleteProductBtn);
            }

            card.add(cardBottom, BorderLayout.SOUTH);
            catalogGridPanel.add(card);
        }
        catalogGridPanel.revalidate();
        catalogGridPanel.repaint();
    }

    private void refreshCartTable() {
        cartModel.setRowCount(0);
        double total = 0;
        int totalQty = 0;
        for (CartItem item : cart) {
            double sub = item.getSubtotalUsd() * currentRate;
            total += sub;
            totalQty += item.getQuantity();
            cartModel.addRow(new Object[]{
                    item.getProduct().getName(),
                    item.getProduct().getBrand(),
                    item.getQuantity(),
                    String.format("%.2f %s", item.getProduct().getFinalUnitPriceUsd() * currentRate, currentCurrency),
                    String.format("%.2f %s", sub, currentCurrency)
            });
        }

        double multiplier = 1.0;
        String type = orderTypeCombo != null ? (String) orderTypeCombo.getSelectedItem() : "";
        if ("Business to Business (Wholesale)".equals(type)) {
            multiplier = 0.85;
        } else if (totalQty >= 10) {
            multiplier = 0.95;
        }
        total *= multiplier;

        if (totalLabel != null) {
            totalLabel.setText(String.format("Total Payable: %.2f %s", total, currentCurrency));
        }
    }

    private void refreshAllTabs(int tabIndex) {
        if (!isAdmin) return;
        try {
            if (tabIndex == 2) {
                supplierAuditArea.setText(FileManager.readSupplierReceipts());
                supplierAuditArea.setCaretPosition(0);
            } else if (tabIndex == 3) {
                customerAuditArea.setText(FileManager.readCustomerReceipts());
                customerAuditArea.setCaretPosition(0);
            }
        } catch (IOException e) {
            showError("Failed to load audit logs.");
        }
    }

    private JPanel createCardPanel(String title) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(12, 12, 12, 12)
        ));
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        lbl.setForeground(PRIMARY_COLOR);
        card.add(lbl, BorderLayout.NORTH);
        return card;
    }

    private JTextField createStyledTextField() {
        JTextField tf = new JTextField();
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_DARK);
        tf.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(4, 6, 4, 6)
        ));
        return tf;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        return btn;
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_DARK);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        return l;
    }

    private JTextArea createConsoleArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBackground(CARD_BG);
        area.setForeground(TEXT_DARK);
        area.setBorder(new EmptyBorder(8, 8, 8, 8));
        return area;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}