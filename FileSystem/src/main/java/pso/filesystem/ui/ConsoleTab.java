/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package pso.filesystem.ui;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
/**
 *
 * @author jimen
 */


public class ConsoleTab extends JPanel {

    private static final Color SIDEBAR_BG     = new Color(245, 245, 245);
    private static final Color SIDEBAR_BORDER = new Color(220, 220, 220);
    private static final Color ITEM_ACTIVE_BG = new Color(225, 225, 225);
    private static final Color ITEM_FG        = new Color(120, 118, 114);
    private static final Color ITEM_ACTIVE_FG = new Color(30,  30,  30);
    private static final Color DOT_ONLINE     = new Color(39,  110, 60);
    private static final Color DOT_OFFLINE    = new Color(200, 200, 200);
    private static final Color ADD_FG         = new Color(150, 148, 144);
    private static final Color ADD_BORDER     = new Color(200, 200, 200);
    private static final Color SECTION_FG     = new Color(170, 168, 164);

    private final List<SessionEntry> sessions = new ArrayList<>();
    private int activeIndex  = -1;
    private int nextSessionId = 1;

    private final JPanel    sessionListPanel;
    private final JPanel    terminalArea;
    private final CardLayout cardLayout;

    private Function<String, List<CommandResult>> commandHandler;

    public ConsoleTab() {
        setLayout(new BorderLayout());

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(165, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SIDEBAR_BORDER));

        JLabel sectionLabel = new JLabel("CONSOLAS");
        sectionLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        sectionLabel.setForeground(SECTION_FG);
        sectionLabel.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        sidebar.add(sectionLabel, BorderLayout.NORTH);

        sessionListPanel = new JPanel();
        sessionListPanel.setLayout(new BoxLayout(sessionListPanel, BoxLayout.Y_AXIS));
        sessionListPanel.setBackground(SIDEBAR_BG);
        sessionListPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JScrollPane sideScroll = new JScrollPane(sessionListPanel);
        sideScroll.setBorder(null);
        sideScroll.setBackground(SIDEBAR_BG);
        sideScroll.getViewport().setBackground(SIDEBAR_BG);
        sideScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebar.add(sideScroll, BorderLayout.CENTER);

        JButton addBtn = makeAddButton();
        JPanel addWrapper = new JPanel(new BorderLayout());
        addWrapper.setBackground(SIDEBAR_BG);
        addWrapper.setBorder(BorderFactory.createEmptyBorder(6, 8, 10, 8));
        addWrapper.add(addBtn);
        sidebar.add(addWrapper, BorderLayout.SOUTH);

        cardLayout  = new CardLayout();
        terminalArea = new JPanel(cardLayout);
        terminalArea.setBackground(Color.WHITE);

        add(sidebar,      BorderLayout.WEST);
        add(terminalArea, BorderLayout.CENTER);

        addSession("root");
    }

    public void setCommandHandler(Function<String, List<CommandResult>> handler) {
        this.commandHandler = handler;
        for (SessionEntry s : sessions)
            s.terminal.setCommandHandler(handler);
    }

    public void setActivePrompt(String prompt) {
        if (activeIndex >= 0)
            sessions.get(activeIndex).terminal.setPrompt(prompt);
    }

    public void printToActive(CommandResult result) {
        if (activeIndex >= 0)
            sessions.get(activeIndex).terminal.printResult(result);
    }

    private void addSession(String username) {
        int id      = nextSessionId++;
        String key  = "session-" + id;
        String label = username + "@miFS";

        TerminalPanel terminal = new TerminalPanel();
        terminal.setPrompt(username + "@miFS:/# ");
        if (commandHandler != null)
            terminal.setCommandHandler(commandHandler);

        sessions.add(new SessionEntry(id, label, key, terminal, true));
        terminalArea.add(terminal, key);
        rebuildSidebar();
        switchTo(sessions.size() - 1);
    }

    private void switchTo(int index) {
        if (index < 0 || index >= sessions.size()) return;
        activeIndex = index;
        cardLayout.show(terminalArea, sessions.get(index).cardKey);
        rebuildSidebar();
    }

    private void rebuildSidebar() {
        sessionListPanel.removeAll();
        for (int i = 0; i < sessions.size(); i++) {
            final int idx = i;
            sessionListPanel.add(makeSessionItem(sessions.get(i), i == activeIndex, () -> switchTo(idx)));
            sessionListPanel.add(Box.createVerticalStrut(2));
        }
        sessionListPanel.revalidate();
        sessionListPanel.repaint();
    }

    private JPanel makeSessionItem(SessionEntry entry, boolean active, Runnable onClick) {
        JPanel item = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(Graphics g) {
                if (active) {
                    g.setColor(ITEM_ACTIVE_BG);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                }
                super.paintComponent(g);
            }
        };
        item.setOpaque(false);
        item.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel dot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(entry.online ? DOT_ONLINE : DOT_OFFLINE);
                g2.fillOval(0, 2, 7, 7);
            }
        };
        dot.setPreferredSize(new Dimension(7, 12));
        dot.setOpaque(false);

        JLabel label = new JLabel(entry.label);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        label.setForeground(active ? ITEM_ACTIVE_FG : ITEM_FG);

        item.add(dot,   BorderLayout.WEST);
        item.add(label, BorderLayout.CENTER);

        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
            @Override public void mouseEntered(MouseEvent e) {
                if (!active) { item.setOpaque(true); item.setBackground(new Color(235, 235, 235)); }
            }
            @Override public void mouseExited(MouseEvent e) {
                item.setOpaque(false); item.repaint();
            }
        });

        return item;
    }

    private JButton makeAddButton() {
        JButton btn = new JButton("+ Nueva consola") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ADD_BORDER);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{4, 3}, 0));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        btn.setForeground(ADD_FG);
        btn.setBackground(SIDEBAR_BG);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        btn.addActionListener(e -> {
            String user = JOptionPane.showInputDialog(this,
                "Nombre de usuario:", "Nueva consola", JOptionPane.PLAIN_MESSAGE);
            if (user != null && !user.isBlank())
                addSession(user.strip());
        });
        return btn;
    }

    private static class SessionEntry {
        final int id;
        final String label;
        final String cardKey;
        final TerminalPanel terminal;
        boolean online;

        SessionEntry(int id, String label, String cardKey, TerminalPanel terminal, boolean online) {
            this.id = id; this.label = label; this.cardKey = cardKey;
            this.terminal = terminal; this.online = online;
        }
    }
}