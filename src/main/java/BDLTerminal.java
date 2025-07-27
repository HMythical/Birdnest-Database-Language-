import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.util.ArrayList;
import java.util.List;

public class BDLTerminal extends JFrame {
    private JTextPane outputArea;
    private JTextField inputField;
    private StyledDocument doc;
    private Style defaultStyle;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    public BDLTerminal() {
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

        appendOutput("BDL Terminal - Ready\n");
    }

    private void appendOutput(String text) {
        try {
            doc.insertString(doc.getLength(), text, defaultStyle);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BDLTerminal terminal = new BDLTerminal();
            terminal.setVisible(true);
        });
    }
}