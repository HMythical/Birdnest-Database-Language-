import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class BDLTerminal extends JFrame {
    private JTextPane outputArea;
    private JTextField inputField;
    private StyledDocument doc;
    private Style keywordStyle;
    private Style defaultStyle;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String version = "1.0.0";

    // Simple in-memory database
    private Map<String, Map<String, Object>> nests = new HashMap<>();

    public BDLTerminal() {
        setTitle("BirdNest Database Language Terminal");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Setup styled text pane
        outputArea = new JTextPane();
        outputArea.setEditable(false);
        outputArea.setBackground(Color.BLACK);
        outputArea.setForeground(Color.GREEN);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 14));

        doc = outputArea.getStyledDocument();
        defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        keywordStyle = doc.addStyle("Keyword", defaultStyle);
        StyleConstants.setForeground(keywordStyle, Color.CYAN);
        StyleConstants.setBold(keywordStyle, true);

        inputField = new JTextField();
        inputField.setBackground(Color.BLACK);
        inputField.setForeground(Color.GREEN);
        inputField.setFont(new Font("Consolas", Font.PLAIN, 14));
        inputField.setCaretColor(Color.GREEN);

        // Layout
        setLayout(new BorderLayout());
        add(new JScrollPane(outputArea), BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);

        // Input handling with history
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String command = inputField.getText().trim();
                    if (!command.isEmpty()) {
                        commandHistory.add(command);
                        historyIndex = commandHistory.size();
                        processCommand(command);
                        inputField.setText("");
                    }
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
                }
            }
        });

        // Welcome message
        appendOutput("Welcome to BirdNest Database Language Terminal\nCurrent version: " + version + "\n");
    }

    private void appendOutput(String text) {
        try {
            doc.insertString(doc.getLength(), text, defaultStyle);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void appendKeyword(String text) {
        try {
            doc.insertString(doc.getLength(), text, keywordStyle);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    // Highlight BDL keywords in output
    private void highlightKeywords(String command) {
        String[] keywords = {
                "PICK", "EGG", "FROM", "FIND", "NEST", "WITH", "HAS", "SORT", "BY", "ASCO", "DESCO", "LIMIT",
                "LAY", "INTO", "EGGS", "UPDATE", "SET", "WHILE", "REMOVE", "CREATE", "NEW", "CHANGE", "ADD",
                "DROP", "MODIFY", "DESTROY", "EXPORT", "TO", "FILETYPE", "GRAPH", "TYPE", "GROUPS", "INDEX",
                "ON", "DELETE", "JOIN", "INNER", "CHICK", "RECOGNITION", "GRANT", "PERMISSION", "DURATION", "REVOKE"
        };

        String[] tokens = command.split("\\s+");
        for (String token : tokens) {
            boolean isKeyword = false;
            for (String kw : keywords) {
                if (token.equalsIgnoreCase(kw)) {
                    appendKeyword(token + " ");
                    isKeyword = true;
                    break;
                }
            }
            if (!isKeyword) {
                appendOutput(token + " ");
            }
        }
        appendOutput("\n");
    }

    private void processCommand(String command) {
        // Echo command with highlighting
        appendOutput("> ");
        highlightKeywords(command);

        String[] tokens = command.trim().split("\\s+");
        if (tokens.length == 0) return;

        try {
            switch (tokens[0].toUpperCase()) {
                case "PICK":
                    handlePickCommand(tokens);
                    break;
                case "LAY":
                    handleLayCommand(tokens);
                    break;
                case "CREATE":
                    if (tokens.length > 1 && tokens[1].equalsIgnoreCase("NEW")) {
                        if (tokens[2].equalsIgnoreCase("NEST")) {
                            handleCreateNest(tokens);
                        } else if (tokens[2].equalsIgnoreCase("CHICK")) {
                            handleCreateChick(tokens);
                        }
                    }
                    break;
                case "VERSION":
                    appendOutput("BDL Terminal Version: " + version + "\n");
                    break;
                default:
                    appendOutput("Error: Unrecognized command.\n");
            }
        } catch (Exception e) {
            appendOutput("Error: " + e.getMessage() + "\n");
        }
    }

    // Command implementations
    private void handlePickCommand(String[] tokens) {
        // Simplified PICK EGG implementation
        if (tokens.length < 4 || !tokens[1].equalsIgnoreCase("EGG") || !tokens[3].equalsIgnoreCase("FROM")) {
            throw new IllegalArgumentException("Invalid PICK EGG syntax. Use: PICK EGG [eggs] FROM [nest]");
        }
        String nestName = tokens[4];
        if (!nests.containsKey(nestName)) {
            throw new IllegalArgumentException("Nest not found: " + nestName);
        }
        appendOutput("Displaying eggs from nest: " + nestName + "\n");
        // Actual data retrieval would go here
    }

    private void handleLayCommand(String[] tokens) {
        // Simplified LAY EGG implementation
        if (tokens.length < 4 || !tokens[1].equalsIgnoreCase("EGG") || !tokens[2].equalsIgnoreCase("INTO")) {
            throw new IllegalArgumentException("Invalid LAY EGG syntax. Use: LAY EGG INTO [nest] EGGS (...)");
        }
        String nestName = tokens[3];
        if (!nests.containsKey(nestName)) {
            throw new IllegalArgumentException("Nest not found: " + nestName);
        }
        appendOutput("New eggs added to nest: " + nestName + "\n");
    }

    private void handleCreateNest(String[] tokens) {
        if (tokens.length < 4) {
            throw new IllegalArgumentException("Invalid CREATE NEW NEST syntax");
        }
        String nestName = tokens[3];
        nests.put(nestName, new HashMap<>());
        appendOutput("Created new nest: " + nestName + "\n");
    }

    private void handleCreateChick(String[] tokens) {
        if (tokens.length < 6 || !tokens[4].equalsIgnoreCase("RECOGNITION")) {
            throw new IllegalArgumentException("Invalid CREATE NEW CHICK syntax");
        }
        String userName = tokens[3].replaceAll("'", "");
        String authType = tokens[5].replaceAll("'", "");
        appendOutput("Created new chick: " + userName + " with auth: " + authType + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BDLTerminal terminal = new BDLTerminal();
            terminal.setVisible(true);
        });
    }
}