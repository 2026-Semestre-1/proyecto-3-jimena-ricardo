/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package pso.ui;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
/**
 *
 * @author jimen
 */

public class DiskViewPanel extends JPanel {

    public static final int TYPE_FREE      = 0;
    public static final int TYPE_META      = 1;
    public static final int TYPE_BITMAP    = 2;
    public static final int TYPE_INODE     = 3;
    public static final int TYPE_USERTABLE = 4;
    public static final int TYPE_INDEX     = 5;
    public static final int TYPE_DIRECTORY = 6;
    public static final int TYPE_DATA      = 7;

    private static final Color BG           = Color.WHITE;
    private static final Color PANEL_BG     = new Color(248, 248, 248);
    private static final Color BORDER_COLOR = new Color(220, 220, 220);
    private static final Color TEXT_PRIMARY = new Color(30,  30,  30);
    private static final Color TEXT_MUTED   = new Color(120, 118, 114);
    private static final Color TEXT_DIM     = new Color(180, 178, 174);
    private static final Color ACCENT       = new Color(29, 158, 117);

    private static final Color[] BLOCK_FILL = {
        new Color(30,  30,  30),
        new Color(83,  74, 183),
        new Color(57, 109,  17),
        new Color(24,  95, 165),
        new Color(153, 60,  29),
        new Color(186, 117, 23),
        new Color(24,  94, 134),
        new Color(15, 110,  75),
    };

    private static final Color[] BLOCK_BORDER_COLOR = {
        new Color(50,  50,  48),
        new Color(127,119,221),
        new Color(99, 153, 34),
        new Color(55, 138,221),
        new Color(216, 90, 48),
        new Color(239,159, 39),
        new Color(55, 138,200),
        new Color(29, 158,117),
    };

    private static final Color[] BLOCK_HOVER = {
        new Color(55,  55,  52),
        new Color(111,103,206),
        new Color(80, 130, 27),
        new Color(37, 110,186),
        new Color(187, 74, 38),
        new Color(208,138, 20),
        new Color(37, 118,175),
        new Color(20, 138, 94),
    };

    private static final String[] TYPE_NAMES = {
        "Libre",
        "Metadatos (Boot/Super)",
        "Bitmap de espacio libre",
        "Tabla de inodos",
        "Tabla de usuarios/grupos",
        "IndexBlock",
        "Directorio",
        "Datos de archivo",
    };

    private int[]    blockTypes;
    private int[]    blockInodes;
    private String[] blockOwners;
    private int      totalBlocks = 0;
    private long     totalBytes  = 0;
    private long     usedBytes   = 0;
    private String   diskName    = "—";

    private static final int BLOCK_PX  = 14;
    private static final int BLOCK_GAP = 2;
    private static final int GRID_PAD  = 16;

    private int hoveredBlock  = -1;
    private int selectedBlock = -1;

    private final HeaderPanel  headerPanel;
    private final GridPanel    gridPanel;
    private final DetailPanel  detailPanel;
    private final LegendPanel  legendPanel;

    public DiskViewPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);

        headerPanel = new HeaderPanel();
        gridPanel   = new GridPanel();
        detailPanel = new DetailPanel();
        legendPanel = new LegendPanel();

        JScrollPane gridScroll = new JScrollPane(gridPanel);
        gridScroll.setBorder(null);
        gridScroll.setBackground(BG);
        gridScroll.getViewport().setBackground(BG);
        gridScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        styleScrollbar(gridScroll);

        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setBackground(BG);
        center.add(gridScroll, BorderLayout.CENTER);
        center.add(detailPanel, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);
        add(center,      BorderLayout.CENTER);
        add(legendPanel, BorderLayout.SOUTH);

        showEmpty();
    }

    public void loadDisk(int[] types, int[] inodes, String[] owners,
                         long totalBytes, long usedBytes, String diskName) {
        this.blockTypes  = types.clone();
        this.blockInodes = inodes != null ? inodes.clone() : new int[types.length];
        this.blockOwners = owners != null ? owners.clone() : new String[types.length];
        this.totalBlocks = types.length;
        this.totalBytes  = totalBytes;
        this.usedBytes   = usedBytes;
        this.diskName    = diskName != null ? diskName : "—";
        this.selectedBlock = -1;
        this.hoveredBlock  = -1;
        headerPanel.update();
        gridPanel.revalidate();
        gridPanel.repaint();
        detailPanel.clear();
    }

    public void refresh(int[] types, int[] inodes, String[] owners,
                        long totalBytes, long usedBytes, String diskName) {
        loadDisk(types, inodes, owners, totalBytes, usedBytes, diskName);
    }

    public void showEmpty() {
        this.totalBlocks = 0;
        this.diskName    = "sin montar";
        this.totalBytes  = 0;
        this.usedBytes   = 0;
        headerPanel.update();
        gridPanel.repaint();
        detailPanel.clear();
    }

    private class HeaderPanel extends JPanel {
        private final JLabel nameLabel  = makeLabel("—", 18, Font.BOLD,  TEXT_PRIMARY);
        private final JLabel totalLabel = makeLabel("—", 12, Font.PLAIN, TEXT_MUTED);
        private final JLabel usedLabel  = makeLabel("—", 12, Font.PLAIN, TEXT_MUTED);
        private final JLabel freeLabel  = makeLabel("—", 12, Font.PLAIN, TEXT_MUTED);
        private final UsageBar usageBar = new UsageBar();

        HeaderPanel() {
            setBackground(PANEL_BG);
            setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(14, 18, 14, 18)
            ));
            setLayout(new BorderLayout(0, 8));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(nameLabel, BorderLayout.WEST);

            JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
            stats.setOpaque(false);
            stats.add(statChip("Total", totalLabel));
            stats.add(statChip("Usado", usedLabel));
            stats.add(statChip("Libre", freeLabel));
            top.add(stats, BorderLayout.EAST);

            add(top,      BorderLayout.NORTH);
            add(usageBar, BorderLayout.SOUTH);
            update();
        }

        void update() {
            nameLabel.setText(diskName);
            totalLabel.setText(formatBytes(totalBytes));
            freeLabel.setText(formatBytes(totalBytes - usedBytes));
            usedLabel.setText(formatBytes(usedBytes));
            usageBar.setRatio(totalBytes > 0 ? (double) usedBytes / totalBytes : 0);
            repaint();
        }

        private JPanel statChip(String label, JLabel valueLabel) {
            JPanel chip = new JPanel();
            chip.setLayout(new BoxLayout(chip, BoxLayout.Y_AXIS));
            chip.setOpaque(false);
            JLabel lbl = makeLabel(label, 10, Font.PLAIN, TEXT_DIM);
            lbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
            valueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            chip.add(lbl);
            chip.add(valueLabel);
            return chip;
        }
    }

    private class UsageBar extends JPanel {
        private double ratio = 0;

        UsageBar() {
            setPreferredSize(new Dimension(0, 6));
            setOpaque(false);
        }

        void setRatio(double r) { this.ratio = r; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(BORDER_COLOR);
            g2.fillRoundRect(0, 0, w, h, h, h);
            int filled = (int) (w * ratio);
            if (filled > 0) {
                Color c = ratio > 0.85 ? new Color(226, 75, 74)
                        : ratio > 0.65 ? new Color(239, 159, 39)
                        : ACCENT;
                g2.setColor(c);
                g2.fillRoundRect(0, 0, filled, h, h, h);
            }
        }
    }

    private class GridPanel extends JPanel {
        GridPanel() {
            setBackground(BG);
            setOpaque(true);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int idx = blockAt(e.getX(), e.getY());
                    if (idx >= 0 && idx < totalBlocks) {
                        selectedBlock = idx;
                        detailPanel.showBlock(idx);
                        repaint();
                    }
                }
                @Override public void mouseExited(MouseEvent e) {
                    hoveredBlock = -1;
                    repaint();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int idx = blockAt(e.getX(), e.getY());
                    if (idx != hoveredBlock) {
                        hoveredBlock = idx;
                        setCursor(idx >= 0 && idx < totalBlocks
                            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            : Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            if (totalBlocks == 0) return new Dimension(400, 200);
            int cols = computeCols();
            int rows = (int) Math.ceil((double) totalBlocks / cols);
            int w = GRID_PAD * 2 + cols * (BLOCK_PX + BLOCK_GAP) - BLOCK_GAP;
            int h = GRID_PAD * 2 + rows * (BLOCK_PX + BLOCK_GAP) - BLOCK_GAP;
            return new Dimension(w, Math.max(h, 200));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (totalBlocks == 0) {
                g2.setColor(TEXT_DIM);
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                String msg = "Sin disco montado — usá 'format <MB>' para crear uno.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2,
                              (getHeight() + fm.getAscent()) / 2);
                return;
            }

            int cols = computeCols();
            for (int i = 0; i < totalBlocks; i++) {
                int col = i % cols;
                int row = i / cols;
                int x   = GRID_PAD + col * (BLOCK_PX + BLOCK_GAP);
                int y   = GRID_PAD + row * (BLOCK_PX + BLOCK_GAP);
                int t   = (blockTypes != null && i < blockTypes.length) ? blockTypes[i] : 0;

                g2.setColor(i == hoveredBlock ? BLOCK_HOVER[t] : BLOCK_FILL[t]);
                g2.fillRoundRect(x, y, BLOCK_PX, BLOCK_PX, 2, 2);

                if (i == selectedBlock) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1.2f));
                } else {
                    g2.setColor(BLOCK_BORDER_COLOR[t]);
                    g2.setStroke(new BasicStroke(0.5f));
                }
                g2.drawRoundRect(x, y, BLOCK_PX - 1, BLOCK_PX - 1, 2, 2);
            }
        }

        private int computeCols() {
            int available = Math.max(getWidth() - GRID_PAD * 2, BLOCK_PX + BLOCK_GAP);
            return Math.max(1, available / (BLOCK_PX + BLOCK_GAP));
        }

        private int blockAt(int mx, int my) {
            if (totalBlocks == 0) return -1;
            int cols = computeCols();
            int col  = (mx - GRID_PAD) / (BLOCK_PX + BLOCK_GAP);
            int row  = (my - GRID_PAD) / (BLOCK_PX + BLOCK_GAP);
            if (col < 0 || col >= cols || row < 0) return -1;
            int bx = GRID_PAD + col * (BLOCK_PX + BLOCK_GAP);
            int by = GRID_PAD + row * (BLOCK_PX + BLOCK_GAP);
            if (mx < bx || mx >= bx + BLOCK_PX || my < by || my >= by + BLOCK_PX) return -1;
            int idx = row * cols + col;
            return idx < totalBlocks ? idx : -1;
        }
    }

    private class DetailPanel extends JPanel {
        private final JLabel titleLabel = makeLabel("—", 13, Font.BOLD,  TEXT_PRIMARY);
        private final JLabel typeLabel  = makeLabel("—", 12, Font.PLAIN, TEXT_MUTED);
        private final JLabel inodeLabel = makeLabel("—", 12, Font.PLAIN, TEXT_MUTED);
        private final JLabel ownerLabel = makeLabel("—", 12, Font.PLAIN, TEXT_MUTED);
        private final JPanel swatch     = new JPanel();

        DetailPanel() {
            setPreferredSize(new Dimension(200, 0));
            setBackground(PANEL_BG);
            setBorder(new CompoundBorder(
                new MatteBorder(0, 1, 0, 0, BORDER_COLOR),
                new EmptyBorder(16, 14, 16, 14)
            ));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            swatch.setPreferredSize(new Dimension(180, 4));
            swatch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
            swatch.setBackground(TEXT_DIM);
            swatch.setOpaque(true);
            swatch.setBorder(null);

            JLabel hint = makeLabel("Haz clic en un bloque", 11, Font.PLAIN, TEXT_DIM);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);

            add(swatch);
            add(Box.createVerticalStrut(10));
            add(titleLabel);
            add(Box.createVerticalStrut(4));
            add(typeLabel);
            add(Box.createVerticalStrut(12));
            add(fieldRow("Inodo", inodeLabel));
            add(Box.createVerticalStrut(6));
            add(fieldRow("Dueño", ownerLabel));
            add(Box.createVerticalGlue());
            add(hint);

            for (JComponent c : new JComponent[]{titleLabel, typeLabel, inodeLabel, ownerLabel})
                c.setAlignmentX(Component.LEFT_ALIGNMENT);

            clear();
        }

        void showBlock(int idx) {
            int t     = blockTypes  != null && idx < blockTypes.length  ? blockTypes[idx]  : 0;
            int inode = blockInodes != null && idx < blockInodes.length ? blockInodes[idx] : -1;
            String owner = blockOwners != null && idx < blockOwners.length && blockOwners[idx] != null
                           ? blockOwners[idx] : "—";
            swatch.setBackground(BLOCK_FILL[t]);
            titleLabel.setText("Bloque " + idx);
            typeLabel.setText(TYPE_NAMES[t]);
            inodeLabel.setText(inode > 0 ? String.valueOf(inode) : "—");
            ownerLabel.setText(owner);
            repaint();
        }

        void clear() {
            swatch.setBackground(TEXT_DIM);
            titleLabel.setText("Sin selección");
            typeLabel.setText("Haz clic en un bloque");
            inodeLabel.setText("—");
            ownerLabel.setText("—");
            repaint();
        }

        private JPanel fieldRow(String label, JLabel valueLabel) {
            JPanel row = new JPanel(new BorderLayout(0, 2));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(makeLabel(label, 10, Font.PLAIN, TEXT_DIM), BorderLayout.NORTH);
            row.add(valueLabel, BorderLayout.CENTER);
            return row;
        }
    }

    private class LegendPanel extends JPanel {
        LegendPanel() {
            setBackground(PANEL_BG);
            setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(8, 16, 8, 16)
            ));
            setLayout(new FlowLayout(FlowLayout.LEFT, 14, 0));
            for (int i = 0; i < TYPE_NAMES.length; i++)
                add(legendItem(BLOCK_FILL[i], BLOCK_BORDER_COLOR[i], TYPE_NAMES[i]));
        }

        private JPanel legendItem(Color fill, Color border, String label) {
            JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            item.setOpaque(false);
            JPanel sw = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 1, 10, 10, 2, 2);
                    g2.setColor(border);
                    g2.setStroke(new BasicStroke(0.5f));
                    g2.drawRoundRect(0, 1, 9, 9, 2, 2);
                }
            };
            sw.setPreferredSize(new Dimension(10, 12));
            sw.setOpaque(false);
            item.add(sw);
            item.add(makeLabel(label, 11, Font.PLAIN, TEXT_MUTED));
            return item;
        }
    }

    private static JLabel makeLabel(String text, int size, int style, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font(Font.SANS_SERIF, style, size));
        lbl.setForeground(color);
        return lbl;
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0)          return "0 B";
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static void styleScrollbar(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(55, 55, 52);
                trackColor = BG;
            }
            @Override protected JButton createDecreaseButton(int o) { return zero(); }
            @Override protected JButton createIncreaseButton(int o) { return zero(); }
            private JButton zero() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
    }
}