import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class BDLTerminal extends JFrame {
    private JTextPane outputArea;
    private JTextField inputField;
    private StyledDocument doc;
    private Style defaultStyle;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    private CommandProcessor commandProcessor;
    private Set<String> keywords;
    private Style keywordStyle;
    private Style stringStyle;
    private Style numberStyle;

    // Regular expressions for command syntax
    private static final Pattern HATCH_PATTERN = Pattern.compile(
        "HATCH\\s+NEW\\s+CHICK\\s+'([^']+)'@'([^']+)'\\s+RECOGNITION\\s+'([^']+)'",
        Pattern.CASE_INSENSITIVE
    );

    public BDLTerminal() throws Exception {
        // Initialize CommandProcessor with UserDatabase
        UserDatabase userDatabase = new UserDatabase();
        commandProcessor = new CommandProcessor(userDatabase);

        // Create initial admin user
        User adminUser = new User(
            "admin",
            "admin",  // Should be changed on first login
            "localhost",
            new String[]{"ADMIN+"},
            false,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "SYSTEM"
        );
        userDatabase.addUser(adminUser);
        commandProcessor.setCurrentUser(adminUser);

        initializeKeywords();
        setupStyles();

        setTitle("BDL Terminal");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        outputArea = new JTextPane();
        outputArea.setEditable(false);
        outputArea.setBackground(Color.BLACK);
        outputArea.setForeground(Color.GREEN);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 14));

        doc = outputArea.getStyledDocument();
        defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

        inputField = new JTextField();
        inputField.setBackground(Color.BLACK);
        inputField.setForeground(Color.GREEN);
        inputField.setFont(new Font("Consolas", Font.PLAIN, 14));
        inputField.setCaretColor(Color.GREEN);

        setLayout(new BorderLayout());
        add(new JScrollPane(outputArea), BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String command = inputField.getText().trim();
                    if (!command.isEmpty()) {
                        commandHistory.add(command);
                        historyIndex = commandHistory.size();
                        appendOutput("> " + command + "\n");

                        // Process command and display result
                        String result = commandProcessor.processCommand(command);
                        appendOutput(result + "\n");

                        inputField.setText("");
                    }
                    SwingUtilities.invokeLater(() -> highlightSyntax());
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (!commandHistory.isEmpty()) {
                        if (historyIndex > 0) historyIndex--;
                        inputField.setText(commandHistory.get(historyIndex));
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (!commandHistory.isEmpty() && historyIndex < commandHistory.size() - 1) {
                        historyIndex++;
                        inputField.setText(commandHistory.get(historyIndex));
                    } else {
                        inputField.setText("");
                        historyIndex = commandHistory.size();
                    }
                } else {
                    SwingUtilities.invokeLater(() -> highlightSyntax());
                }
            }
        });

        // Add window listener for cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });

        appendOutput("BDL Terminal - Ready\nType 'help' for available commands\n");
    }

    private void initializeKeywords() {
        keywords = new HashSet<>(Arrays.asList(
            "HATCH", "DROP", "GRANT", "REVOKE", "CREATE", "ROLE", "CHICK", "NEW",
            "PERMISSION", "TO", "DURATION", "FROM", "PICK", "EGG", "NEST", "LAY",
            "INTO", "UPDATE", "SET", "WHILE", "REMOVE", "DESTROY", "EXPORT", "GRAPH"
        ));
    }

    private void setupStyles() {
        StyleContext styleContext = StyleContext.getDefaultStyleContext();

        defaultStyle = styleContext.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, Color.BLUE);

        keywordStyle = styleContext.addStyle("keyword", null);
        StyleConstants.setForeground(keywordStyle, Color.CYAN);
        StyleConstants.setBold(keywordStyle, true);

        stringStyle = styleContext.addStyle("string", null);
        StyleConstants.setForeground(stringStyle, Color.ORANGE);

        numberStyle = styleContext.addStyle("number", null);
        StyleConstants.setForeground(numberStyle, Color.YELLOW);
    }

    private void highlightSyntax() {
        String text = inputField.getText();
        DefaultStyledDocument doc = new DefaultStyledDocument();
        try {
            // Set default style
            SimpleAttributeSet defaultStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(defaultStyle, Color.GREEN);
            StyleConstants.setFontFamily(defaultStyle, "Consolas");
            doc.insertString(0, text, defaultStyle);

            // Highlight patterns
            highlightPattern(doc, "\\b(HATCH|DROP|GRANT|REVOKE|CREATE|ROLE|PICK|EGG|NEST|LAY|INTO|UPDATE|SET)\\b", keywordStyle);
            highlightPattern(doc, "'[^']*'", stringStyle);
            highlightPattern(doc, "\\b\\d+\\b", numberStyle);
            highlightPattern(doc, "@", stringStyle);

        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        // Preserve caret position
        int caretPos = inputField.getCaretPosition();
        inputField.setDocument(doc);
        inputField.setCaretPosition(Math.min(caretPos, doc.getLength()));
    }

    private void highlightPattern(DefaultStyledDocument doc, String patternStr, Style style) {
        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        try {
            String text = doc.getText(0, doc.getLength());
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                doc.setCharacterAttributes(matcher.start(),
                    matcher.end() - matcher.start(), style, true);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void appendOutput(String text) {
        try {
            SimpleAttributeSet style = new SimpleAttributeSet();

            if (text.startsWith(">")) {
                StyleConstants.setForeground(style, Color.CYAN);
            } else if (text.contains("Error:") || text.contains("FAULTY")) {
                StyleConstants.setForeground(style, Color.RED);
                StyleConstants.setBold(style, true);
            } else if (text.contains("Successfully")) {
                StyleConstants.setForeground(style, new Color(0, 255, 0));
            } else {
                StyleConstants.setForeground(style, Color.GREEN);
            }

            doc.insertString(doc.getLength(), text, style);
            outputArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void cleanup() {
        try {
            if (commandProcessor != null) {
                commandProcessor.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle command input with proper error handling
    private void processInput(String command) {
        if (command.trim().isEmpty()) {
            return;
        }

        if (command.equalsIgnoreCase("help")) {
            displayHelp();
            return;
        }

        try {
            String result = commandProcessor.processCommand(command);
            appendOutput(result + "\n");
        } catch (Exception e) {
            appendOutput("Error: " + e.getMessage() + "\n");
        }
    }

    private void displayHelp() {
        StringBuilder help = new StringBuilder();
        help.append("Available commands:\n");
        help.append("1. HATCH NEW CHICK 'username'@'host' RECOGNITION 'auth_type'\n");
        help.append("2. DROP CHICK 'username' ['reason']\n");
        help.append("3. GRANT CHICK username PERMISSION type TO nest DURATION time\n");
        help.append("4. REVOKE PERMISSION type FROM 'username' DURATION time\n");
        help.append("5. CREATE ROLE 'role_name' hierarchy_number\n");
        help.append("\nFor detailed documentation, please refer to the BDL manual.\n");
        appendOutput(help.toString());
    }


    public static void main(String[] args) {
        // Check for required environment variable first
        if (System.getenv("BDL_MASTER_KEY") == null) {
            System.err.println("Error: Environment variable BDL_MASTER_KEY is not set!");
            System.err.println("\nPlease set the environment variable before running the application:");
            System.err.println("Windows CMD: set BDL_MASTER_KEY=your_secure_key_here");
            System.err.println("Windows PowerShell: $env:BDL_MASTER_KEY = 'your_secure_key_here'");
            System.err.println("Linux/Mac: export BDL_MASTER_KEY=your_secure_key_here");
            System.err.println("\nThe key should be a secure random string used for encryption.");
            System.exit(1);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            BDLTerminal terminal = null;
            try {
                terminal = new BDLTerminal();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            terminal.setVisible(true);
        });
    }
}
