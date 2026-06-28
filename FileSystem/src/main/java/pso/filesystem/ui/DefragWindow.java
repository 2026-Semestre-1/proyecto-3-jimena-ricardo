/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package pso.filesystem.ui;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
/**
 *
 * @author jimen
 */

public class DefragWindow extends JFrame {

    private static final Color BG           = Color.WHITE;
    private static final Color PANEL_BG     = new Color(248, 248, 248);
    private static final Color BORDER_COLOR = new Color(220, 220, 220);
    private static final Color TEXT_PRIMARY = new Color(30,  30,  30);
    private static final Color TEXT_MUTED   = new Color(120, 118, 114);
    private static final Color TEXT_DIM     = new Color(180, 178, 174);
    private static final Color ACCENT       = new Color(29,  158, 117);
    private static final Color COLOR_FREE   = new Color(230, 230, 230);
    private static final Color COLOR_META   = new Color(83,  74,  183);
    private static final Color COLOR_DATA   = new Color(15,  110, 75);
    private static final Color COLOR_MOVING = new Color(239, 159, 39);
    private static final Color COLOR_DONE   = new Color(29,  158, 117);

    private static final int BLOCK_PX  = 14;
    private static final int BLOCK_GAP = 2;
    private static final int GRID_PAD  = 16;

    // Estado del disco
    private int[] blockTypes;
    private int   totalBlocks;

    // Estado de la animación
    private int  animFromBlock = -1;
    private int  animToBlock   = -1;
    private volatile boolean running = false;
    private Thread defragThread;

    private final GridPanel  gridPanel;
    private final JLabel     statusLabel;
    private final JLabel     progressLabel;
    private final JProgressBar progressBar;
    private final JButton    startButton;
    private final JButton    closeButton;

    // Callback que ejecuta la desfragmentación real en el disco
    private Runnable defragTask;

    public DefragWindow(int[] initialBlockTypes, int totalBlocks) {
        super("Desfragmentador de disco");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setMinimumSize(new Dimension(650, 420));
        setLocationRelativeTo(null);
        setBackground(BG);

        this.totalBlocks = totalBlocks;
        this.blockTypes  = initialBlockTypes.clone();

        gridPanel     = new GridPanel();
        statusLabel   = makeLabel("Listo para desfragmentar.", 12, Font.PLAIN, TEXT_MUTED);
        progressLabel = makeLabel("", 11, Font.PLAIN, TEXT_DIM);
        progressBar   = new JProgressBar(0, 100);
        startButton   = new JButton("Iniciar desfragmentación");
        closeButton   = new JButton("Cerrar");

        styleProgressBar();
        styleButtons();

        JPanel header = buildHeader();
        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel footer = buildFooter();

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(BG);
        content.add(header, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { stopDefrag(); }
        });
    }


    public void setDefragTask(Runnable task) {
        this.defragTask = task;
    }

    /** Llamar desde afuera para animar el movimiento de un bloque. */
    public void animateMove(int fromBlock, int toBlock) throws InterruptedException {
        animFromBlock = fromBlock;
        animToBlock   = toBlock;
        blockTypes[fromBlock] = 3; // moving (amarillo)
        SwingUtilities.invokeLater(gridPanel::repaint);
        Thread.sleep(30);

        blockTypes[toBlock]   = blockTypes[fromBlock];
        blockTypes[fromBlock] = 0; // free
        animFromBlock = -1;
        animToBlock   = -1;
        SwingUtilities.invokeLater(gridPanel::repaint);
        Thread.sleep(20);
    }

    public void setBlockType(int blockIndex, int type) {
        if (blockIndex >= 0 && blockIndex < totalBlocks) {
            blockTypes[blockIndex] = type;
            SwingUtilities.invokeLater(gridPanel::repaint);
        }
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    public void setProgress(int percent, String detail) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            progressLabel.setText(detail);
        });
    }

    public void onDefragComplete(int movedBlocks) {
        SwingUtilities.invokeLater(() -> {
            running = false;
            startButton.setEnabled(false);
            startButton.setText("Completado ✓");
            setStatus("Desfragmentación completada. Bloques movidos: " + movedBlocks);
            progressBar.setValue(100);
            gridPanel.repaint();
        });
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
            new EmptyBorder(14, 18, 12, 18)
        ));

        JLabel title = makeLabel("Desfragmentador", 17, Font.BOLD, TEXT_PRIMARY);
        JLabel desc  = makeLabel("Mueve los bloques de datos para eliminar fragmentación y mejorar el rendimiento.", 12, Font.PLAIN, TEXT_MUTED);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        legend.setOpaque(false);
        legend.add(legendChip(COLOR_FREE,   "Libre"));
        legend.add(legendChip(COLOR_META,   "Sistema"));
        legend.add(legendChip(COLOR_DATA,   "Datos"));
        legend.add(legendChip(COLOR_MOVING, "Moviendo"));
        legend.add(legendChip(COLOR_DONE,   "Compactado"));

        panel.add(title,  BorderLayout.NORTH);
        panel.add(desc,   BorderLayout.CENTER);
        panel.add(legend, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
            new EmptyBorder(10, 18, 12, 18)
        ));

        JPanel statusRow = new JPanel(new BorderLayout(8, 0));
        statusRow.setOpaque(false);
        statusRow.add(statusLabel,   BorderLayout.CENTER);
        statusRow.add(progressLabel, BorderLayout.EAST);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.add(closeButton);
        btnRow.add(startButton);

        panel.add(progressBar, BorderLayout.NORTH);
        panel.add(statusRow,   BorderLayout.CENTER);
        panel.add(btnRow,      BorderLayout.SOUTH);
        return panel;
    }

    private void styleProgressBar() {
        progressBar.setForeground(ACCENT);
        progressBar.setBackground(new Color(220, 220, 220));
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 5));
        progressBar.setValue(0);
    }

    private void styleButtons() {
        startButton.setBackground(ACCENT);
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setBorderPainted(false);
        startButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startButton.addActionListener(e -> startDefrag());

        closeButton.setBackground(PANEL_BG);
        closeButton.setForeground(TEXT_MUTED);
        closeButton.setFocusPainted(false);
        closeButton.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        closeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> { stopDefrag(); dispose(); });
    }

    private void startDefrag() {
        if (running) return;
        if (defragTask == null) {
            setStatus("No hay tarea de desfragmentación configurada.");
            return;
        }
        running = true;
        startButton.setEnabled(false);
        startButton.setText("Desfragmentando...");
        setStatus("Analizando disco...");
        defragThread = new Thread(defragTask);
        defragThread.setDaemon(true);
        defragThread.start();
    }

    private void stopDefrag() {
        running = false;
        if (defragThread != null) defragThread.interrupt();
    }

    private class GridPanel extends JPanel {
        GridPanel() {
            setBackground(BG);
        }

        @Override public Dimension getPreferredSize() {
            int cols = computeCols();
            int rows = (int) Math.ceil((double) totalBlocks / cols);
            return new Dimension(
                GRID_PAD * 2 + cols * (BLOCK_PX + BLOCK_GAP),
                Math.max(GRID_PAD * 2 + rows * (BLOCK_PX + BLOCK_GAP), 200)
            );
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cols = computeCols();
            for (int i = 0; i < totalBlocks; i++) {
                int col = i % cols, row = i / cols;
                int x = GRID_PAD + col * (BLOCK_PX + BLOCK_GAP);
                int y = GRID_PAD + row * (BLOCK_PX + BLOCK_GAP);
                Color fill = colorFor(i);
                g2.setColor(fill);
                g2.fillRoundRect(x, y, BLOCK_PX, BLOCK_PX, 2, 2);
                g2.setColor(fill.darker());
                g2.setStroke(new BasicStroke(0.5f));
                g2.drawRoundRect(x, y, BLOCK_PX - 1, BLOCK_PX - 1, 2, 2);
            }
        }

        private Color colorFor(int i) {
            if (i == animFromBlock) return COLOR_MOVING;
            if (i == animToBlock)   return COLOR_MOVING;
            int t = (blockTypes != null && i < blockTypes.length) ? blockTypes[i] : 0;
            return switch (t) {
                case 0  -> COLOR_FREE;
                case 1, 2, 3, 4 -> COLOR_META;
                case 5, 6, 7    -> COLOR_DATA;
                case 99         -> COLOR_DONE;
                default         -> COLOR_FREE;
            };
        }

        private int computeCols() {
            return Math.max(1, (Math.max(getWidth(), 400) - GRID_PAD * 2) / (BLOCK_PX + BLOCK_GAP));
        }
    }

    private JPanel legendChip(Color color, String label) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        item.setOpaque(false);
        JPanel sw = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(0, 1, 10, 10, 2, 2);
            }
        };
        sw.setPreferredSize(new Dimension(10, 12));
        sw.setOpaque(false);
        item.add(sw);
        item.add(makeLabel(label, 11, Font.PLAIN, TEXT_MUTED));
        return item;
    }

    private static JLabel makeLabel(String text, int size, int style, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font(Font.SANS_SERIF, style, size));
        lbl.setForeground(color);
        return lbl;
    }
}