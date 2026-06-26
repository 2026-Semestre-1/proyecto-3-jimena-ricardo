/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package pso.filesystem.ui;

import javax.swing.*;
import java.awt.*;
/**
 *
 * @author jimen
 */
public class MainFrame extends JFrame {

    private static final Color TAB_BG     = new Color(240, 240, 240);
    private static final Color TAB_FG     = new Color(120, 118, 114);
    private static final Color TAB_SEL_FG = new Color(30,  30,  30);
    private static final Color TAB_SEL_BG = Color.WHITE;
    private static final Color BORDER     = new Color(210, 210, 210);

    private final JTabbedPane tabbedPane;
    private final DiskViewPanel diskViewPanel;

    public MainFrame() {
        super("miFileSystem");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        diskViewPanel = new DiskViewPanel();

        tabbedPane = new JTabbedPane(JTabbedPane.TOP) {
            @Override public void updateUI() {
                super.updateUI();
                setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
                    @Override protected void installDefaults() {
                        super.installDefaults();
                        tabAreaInsets = new Insets(6, 8, 0, 0);
                        selectedTabPadInsets = new Insets(0, 0, 0, 0);
                        contentBorderInsets = new Insets(0, 0, 0, 0);
                    }
                    @Override protected void paintTabBackground(Graphics g, int tp, int idx,
                            int x, int y, int w, int h, boolean sel) {
                        g.setColor(sel ? TAB_SEL_BG : TAB_BG);
                        g.fillRect(x, y, w, h);
                    }
                    @Override protected void paintTabBorder(Graphics g, int tp, int idx,
                            int x, int y, int w, int h, boolean sel) {
                        g.setColor(sel ? BORDER : TAB_BG);
                        g.drawRect(x, y, w - 1, h);
                    }
                    @Override protected void paintContentBorder(Graphics g, int tp, int idx) {}
                    protected void paintFocusIndicator(Graphics g, int tp,
                            Rectangle[] rects, int idx, Rectangle clip, boolean sel) {}
                });
            }
        };
        tabbedPane.setBackground(TAB_BG);
        tabbedPane.setForeground(TAB_FG);
        tabbedPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));

        ConsoleTab consoleTab = new ConsoleTab();
        tabbedPane.addTab("Terminal", consoleTab);
        tabbedPane.addTab("Visualizar disco", diskViewPanel);

        tabbedPane.setForegroundAt(0, TAB_FG);
        tabbedPane.setForegroundAt(1, TAB_FG);
        tabbedPane.addChangeListener(e -> {
            int sel = tabbedPane.getSelectedIndex();
            for (int i = 0; i < tabbedPane.getTabCount(); i++)
                tabbedPane.setForegroundAt(i, i == sel ? TAB_SEL_FG : TAB_FG);
        });
        tabbedPane.setSelectedIndex(1);

        setContentPane(tabbedPane);
    }

    public void setDiskData(int[] types, int[] inodes, String[] owners,
                            long totalBytes, long usedBytes, String diskName) {
        diskViewPanel.loadDisk(types, inodes, owners, totalBytes, usedBytes, diskName);
    }

    public void refreshDisk(int[] types, int[] inodes, String[] owners,
                            long totalBytes, long usedBytes, String diskName) {
        diskViewPanel.refresh(types, inodes, owners, totalBytes, usedBytes, diskName);
    }

    public DiskViewPanel getDiskViewPanel() {
        return diskViewPanel;
    }

    private JPanel buildConsolePlaceholder() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(13, 13, 13));
        JLabel lbl = new JLabel("Terminal — próximamente");
        lbl.setForeground(new Color(60, 59, 57));
        lbl.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(lbl, BorderLayout.CENTER);
        return p;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();

            int N = 640;
            int[] types  = new int[N];
            int[] inodes = new int[N];
            String[] owners = new String[N];

            types[0] = DiskViewPanel.TYPE_META;
            types[1] = DiskViewPanel.TYPE_META;
            for (int i = 2;  i <= 5;  i++) types[i] = DiskViewPanel.TYPE_BITMAP;
            for (int i = 6;  i <= 30; i++) types[i] = DiskViewPanel.TYPE_INODE;
            for (int i = 31; i <= 36; i++) types[i] = DiskViewPanel.TYPE_USERTABLE;

            int[] pattern = {
                DiskViewPanel.TYPE_FREE, DiskViewPanel.TYPE_FREE,
                DiskViewPanel.TYPE_FREE, DiskViewPanel.TYPE_INDEX,
                DiskViewPanel.TYPE_DIRECTORY, DiskViewPanel.TYPE_DATA,
                DiskViewPanel.TYPE_DATA, DiskViewPanel.TYPE_FREE
            };
            String[] namePool = {"notas.txt", "docs/", "script.sh", "home/", "README.md"};
            for (int i = 37; i < N; i++) {
                int t = pattern[i % pattern.length];
                types[i] = t;
                if (t != DiskViewPanel.TYPE_FREE) {
                    inodes[i] = (i % 20) + 1;
                    owners[i] = namePool[i % namePool.length];
                }
            }

            long total = (long) N * 1024;
            long used  = 0;
            for (int t : types) if (t != DiskViewPanel.TYPE_FREE) used += 1024;

            frame.setDiskData(types, inodes, owners, total, used, "miFS");
            frame.setVisible(true);
        });
    }
}