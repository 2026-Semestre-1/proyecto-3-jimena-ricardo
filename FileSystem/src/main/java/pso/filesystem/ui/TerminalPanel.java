/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package pso.filesystem.ui;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
/**
 *
 * @author jimen
 */

public class TerminalPanel extends JPanel {

    private static final Color BG           = new Color(250, 250, 250);
    private static final Color FG_PROMPT    = new Color(39,  110, 60);
    private static final Color FG_CMD       = new Color(30,  30,  30);
    private static final Color FG_SUCCESS   = new Color(22,  130, 90);
    private static final Color FG_ERROR     = new Color(185, 40,  40);
    private static final Color FG_INFO      = new Color(30,  90,  180);
    private static final Color FG_WARNING   = new Color(170, 100, 10);
    private static final Color FG_DIRECTORY = new Color(30,  90,  180);
    private static final Color FG_FILE      = new Color(100, 60,  160);
    private static final Color FG_DIM       = new Color(170, 168, 164);
    private static final Color CARET        = new Color(39,  110, 60);
    private static final Color SCROLLBAR    = new Color(210, 210, 210);

    private final JTextPane textPane;
    private final StyledDocument doc;

    private final SimpleAttributeSet promptStyle    = new SimpleAttributeSet();
    private final SimpleAttributeSet commandStyle   = new SimpleAttributeSet();
    private final SimpleAttributeSet successStyle   = new SimpleAttributeSet();
    private final SimpleAttributeSet errorStyle     = new SimpleAttributeSet();
    private final SimpleAttributeSet infoStyle      = new SimpleAttributeSet();
    private final SimpleAttributeSet warningStyle   = new SimpleAttributeSet();
    private final SimpleAttributeSet directoryStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet fileStyle      = new SimpleAttributeSet();
    private final SimpleAttributeSet dimStyle       = new SimpleAttributeSet();

    private int inputStart = 0;
    private String prompt  = "root@miFS:/# ";

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    private Function<String, List<CommandResult>> commandHandler;

    public TerminalPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);

        textPane = new JTextPane();
        textPane.setBackground(BG);
        textPane.setCaretColor(CARET);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textPane.setForeground(FG_CMD);
        textPane.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        doc = textPane.getStyledDocument();
        initStyles();

        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setBorder(null);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = SCROLLBAR;
                trackColor = BG;
            }
            @Override protected JButton createDecreaseButton(int o) { return zeroBtn(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroBtn(); }
            private JButton zeroBtn() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });

        add(scroll, BorderLayout.CENTER);
        setupKeyListener();

        printDim("# miFileSystem v1.0\n");
        printDim("# Usá 'format <MB>' para crear un disco o 'mount <archivo>' para abrir uno.\n\n");
        printPrompt();
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setCommandHandler(Function<String, List<CommandResult>> handler) {
        this.commandHandler = handler;
    }

    public void printDim(String text) {
        appendStyled(text, dimStyle);
    }

    public void printResult(CommandResult result) {
        appendStyled(result.text() + "\n", styleFor(result.type()));
    }

    public void printResults(List<CommandResult> results) {
        for (CommandResult r : results) printResult(r);
    }

    public void printBlank() {
        appendStyled("\n", dimStyle);
    }

    private void setupKeyListener() {
        textPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int key = e.getKeyCode();

                if (key == KeyEvent.VK_ENTER) {
                    e.consume();
                    String input = getCurrentInput();
                    appendStyled("\n", commandStyle);
                    if (!input.isBlank()) {
                        history.add(0, input.strip());
                        historyIndex = -1;
                        executeCommand(input.strip());
                    } else {
                        printPrompt();
                    }
                    return;
                }

                if (key == KeyEvent.VK_BACK_SPACE) {
                    if (textPane.getCaretPosition() <= inputStart) e.consume();
                    return;
                }

                if (key == KeyEvent.VK_DELETE) {
                    if (textPane.getCaretPosition() < inputStart) e.consume();
                    return;
                }

                if (key == KeyEvent.VK_LEFT) {
                    if (textPane.getCaretPosition() <= inputStart) e.consume();
                    return;
                }

                if (key == KeyEvent.VK_HOME) {
                    e.consume();
                    textPane.setCaretPosition(inputStart);
                    return;
                }

                if (key == KeyEvent.VK_UP) {
                    e.consume();
                    if (!history.isEmpty()) {
                        historyIndex = Math.min(historyIndex + 1, history.size() - 1);
                        replaceCurrentInput(history.get(historyIndex));
                    }
                    return;
                }

                if (key == KeyEvent.VK_DOWN) {
                    e.consume();
                    if (historyIndex > 0) {
                        historyIndex--;
                        replaceCurrentInput(history.get(historyIndex));
                    } else {
                        historyIndex = -1;
                        replaceCurrentInput("");
                    }
                    return;
                }

                if (key == KeyEvent.VK_C && e.isControlDown()) {
                    e.consume();
                    appendStyled("^C\n", dimStyle);
                    historyIndex = -1;
                    printPrompt();
                    return;
                }

                if (key == KeyEvent.VK_L && e.isControlDown()) {
                    e.consume();
                    try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}
                    printPrompt();
                    return;
                }

                if (!e.isActionKey() && !e.isControlDown() && !e.isAltDown()) {
                    if (textPane.getCaretPosition() < inputStart)
                        textPane.setCaretPosition(doc.getLength());
                }
            }
        });
    }

    private void initStyles() {
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        for (SimpleAttributeSet s : new SimpleAttributeSet[]{
                promptStyle, commandStyle, successStyle, errorStyle,
                infoStyle, warningStyle, directoryStyle, fileStyle, dimStyle}) {
            StyleConstants.setFontFamily(s, mono.getFamily());
            StyleConstants.setFontSize(s, mono.getSize());
        }
        StyleConstants.setForeground(promptStyle,    FG_PROMPT);
        StyleConstants.setForeground(commandStyle,   FG_CMD);
        StyleConstants.setForeground(successStyle,   FG_SUCCESS);
        StyleConstants.setForeground(errorStyle,     FG_ERROR);
        StyleConstants.setForeground(infoStyle,      FG_INFO);
        StyleConstants.setForeground(warningStyle,   FG_WARNING);
        StyleConstants.setForeground(directoryStyle, FG_DIRECTORY);
        StyleConstants.setForeground(fileStyle,      FG_FILE);
        StyleConstants.setForeground(dimStyle,       FG_DIM);
    }

    private SimpleAttributeSet styleFor(CommandResult.Type type) {
        return switch (type) {
            case SUCCESS   -> successStyle;
            case ERROR     -> errorStyle;
            case INFO      -> infoStyle;
            case WARNING   -> warningStyle;
            case DIRECTORY -> directoryStyle;
            case FILE      -> fileStyle;
            case DIM       -> dimStyle;
        };
    }

    private void appendStyled(String text, AttributeSet style) {
        try {
            doc.insertString(doc.getLength(), text, style);
            scrollToBottom();
        } catch (BadLocationException ignored) {}
    }

    private void printPrompt() {
        appendStyled(prompt, promptStyle);
        inputStart = doc.getLength();
        textPane.setCaretPosition(inputStart);
    }

    private String getCurrentInput() {
        try {
            int len = doc.getLength() - inputStart;
            return len > 0 ? doc.getText(inputStart, len) : "";
        } catch (BadLocationException e) {
            return "";
        }
    }

    private void replaceCurrentInput(String newText) {
        try {
            int len = doc.getLength() - inputStart;
            if (len > 0) doc.remove(inputStart, len);
            doc.insertString(inputStart, newText, commandStyle);
            textPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void executeCommand(String input) {
        if (commandHandler != null) {
            List<CommandResult> results = commandHandler.apply(input);
            if (results != null && !results.isEmpty()) printResults(results);
        } else {
            printResult(CommandResult.info("(sin handler — comando: " + input + ")"));
        }
        printBlank();
        printPrompt();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> textPane.setCaretPosition(doc.getLength()));
    }
}