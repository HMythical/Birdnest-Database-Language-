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

    public BDLTerminal() {
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

        appendOutput("BDL Terminal - Ready\n");
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

    private void appendOutput(String text) {
        try {
            // Apply syntax highlighting to output
            if (text.startsWith(">")) {
                // Command echo gets blue color
                SimpleAttributeSet commandStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(commandStyle, Color.BLUE);
                doc.insertString(doc.getLength(), text, commandStyle);
            } else if (text.contains("Error:") || text.contains("FAULTYPERMISSION")) {
                // Errors in red
                SimpleAttributeSet errorStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(errorStyle, Color.RED);
                doc.insertString(doc.getLength(), text, errorStyle);
            } else if (text.contains("Successfully")) {
                // Success messages in bright green
                SimpleAttributeSet successStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(successStyle, new Color(0, 255, 0));
                doc.insertString(doc.getLength(), text, successStyle);
            } else {
                // Response gets default green color
                SimpleAttributeSet style = new SimpleAttributeSet();
                StyleConstants.setForeground(style, Color.GREEN);
                doc.insertString(doc.getLength(), text, style);
            }
            outputArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void highlightSyntax() {
        String text = inputField.getText();
        DefaultStyledDocument doc = new DefaultStyledDocument();
        try {
            // Set default white color for input
            SimpleAttributeSet defaultInputStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(defaultInputStyle, Color.WHITE);
            doc.insertString(0, text, defaultInputStyle);

            // Split by spaces but preserve strings in single quotes
            List<String> tokens = new ArrayList<>();
            StringBuilder currentToken = new StringBuilder();
            boolean inQuotes = false;

            for (char c : text.toCharArray()) {
                if (c == '\'') {
                    inQuotes = !inQuotes;
                    currentToken.append(c);
                } else if (c == ' ' && !inQuotes) {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken = new StringBuilder();
                    }
                } else {
                    currentToken.append(c);
                }
            }
            if (currentToken.length() > 0) {
                tokens.add(currentToken.toString());
            }

            // Highlight tokens
            int pos = 0;
            for (String token : tokens) {
                pos = text.indexOf(token, pos);
                if (pos == -1) continue;

                if (keywords.contains(token.toUpperCase())) {
                    doc.setCharacterAttributes(pos, token.length(), keywordStyle, true);
                } else if (token.matches("'.*'")) {
                    doc.setCharacterAttributes(pos, token.length(), stringStyle, true);
                } else if (token.matches("\\d+")) {
                    doc.setCharacterAttributes(pos, token.length(), numberStyle, true);
                } else if (token.contains("@")) {
                    doc.setCharacterAttributes(pos, token.length(), stringStyle, true);
                } else {
                    doc.setCharacterAttributes(pos, token.length(), defaultInputStyle, true);
                }
                pos += token.length();
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        inputField.setDocument(doc);
        inputField.setCaretPosition(text.length());
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BDLTerminal terminal = new BDLTerminal();
            terminal.setVisible(true);
        });
    }
}