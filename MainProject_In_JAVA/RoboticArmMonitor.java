package roboticam;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

public class RoboticArmMonitor extends JFrame {
    /* ── Colour palette ──────────────────────────────────────── */
    private static final Color BG_DARK       = new Color(18, 18, 26);
    private static final Color BG_PANEL      = new Color(26, 27, 38);
    private static final Color BG_CARD       = new Color(32, 34, 48);
    private static final Color BG_INPUT      = new Color(38, 40, 56);
    private static final Color BORDER_SUBTLE = new Color(52, 56, 78);
    private static final Color TEXT_PRIMARY   = new Color(230, 234, 245);
    private static final Color TEXT_SECONDARY = new Color(150, 158, 180);
    private static final Color TEXT_MUTED     = new Color(100, 108, 130);
    private static final Color ACCENT_BLUE    = new Color(66, 133, 244);
    private static final Color ACCENT_GREEN   = new Color(52, 199, 136);
    private static final Color ACCENT_ORANGE  = new Color(245, 166, 55);
    private static final Color ACCENT_RED     = new Color(234, 84, 76);
    private static final Color ACCENT_PURPLE  = new Color(142, 108, 228);
    private static final Color ACCENT_TEAL    = new Color(64, 190, 210);
    private static final Color TABLE_ROW_ALT  = new Color(28, 30, 44);
    private static final Color TABLE_HEADER   = new Color(38, 42, 62);

    /* ── Fonts ───────────────────────────────────────────────── */
    private static final Font FONT_TITLE     = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_BODY      = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_LABEL     = new Font("Segoe UI Semibold", Font.PLAIN, 12);
    private static final Font FONT_BUTTON    = new Font("Segoe UI Semibold", Font.PLAIN, 12);
    private static final Font FONT_INPUT     = new Font("Cascadia Code", Font.PLAIN, 13);
    private static final Font FONT_TABLE     = new Font("Cascadia Code", Font.PLAIN, 12);
    private static final Font FONT_SMALL     = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_STATUS    = new Font("Segoe UI", Font.BOLD, 13);

    /* ── Members ─────────────────────────────────────────────── */
    private final JTextField xInput;
    private final JTextField yInput;
    private final JTextField moveDistInput;
    private final JLabel statusLabel;
    private final RobotArmPanel robotArmPanel;
    private final DefaultTableModel tableModel;
    private final Timer randomTimer;
    private final RobotBridgeService bridgeService;

    public RoboticArmMonitor() {
        try {
            bridgeService = new RobotBridgeService();
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to load robot.dll or JNA runtime.", error);
        }

        setTitle("Robotic Arm Monitor");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);

        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int initialWidth = screenBounds.width;
        int initialHeight = screenBounds.height;
        int minimumWidth = Math.min(1180, screenBounds.width);
        int minimumHeight = Math.min(720, screenBounds.height);
        setSize(initialWidth, initialHeight);
        setMinimumSize(new Dimension(minimumWidth, minimumHeight));
        setLocation(
                screenBounds.x + ((screenBounds.width - initialWidth) / 2),
                screenBounds.y + ((screenBounds.height - initialHeight) / 2)
        );

        xInput = createStyledInput("400", 7);
        yInput = createStyledInput("300", 7);
        moveDistInput = createStyledInput("10", 5);
        statusLabel = new JLabel("Waiting for bridge...");
        statusLabel.setFont(FONT_STATUS);
        statusLabel.setForeground(ACCENT_TEAL);
        robotArmPanel = new RobotArmPanel();

        tableModel = new DefaultTableModel(new String[]{
                "DB ID", "DB Timestamp", "Action", "Raw", "Angle", "Acc", "Pos X", "Pos Y", "Error"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        randomTimer = new Timer(700, event -> submit(bridgeService.generateRandom()));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                randomTimer.stop();
                bridgeService.shutdown();
                dispose();
            }
        });

        JPanel leftPanel = buildLeftPanel();

        setLayout(new BorderLayout());
        add(leftPanel, BorderLayout.CENTER);

        submit(bridgeService.initialize());
    }

    /* ══════════════════════════════════════════════════════════ */
    /*  LEFT PANEL                                              */
    /* ══════════════════════════════════════════════════════════ */
    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.add(createControlPanel(), BorderLayout.NORTH);

        panel.add(robotArmPanel, BorderLayout.CENTER);

        return panel;
    }

    /* ══════════════════════════════════════════════════════════ */
    /*  CONTROL PANEL                                           */
    /* ══════════════════════════════════════════════════════════ */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 6));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        /* ── Row 1 : Manual Coordinates ───────────────── */
        /* ── Row 2 : Random Mode ──────────────────────── */
        JPanel row2 = createCardRow();
        row2.setBorder(createSectionBorder("Random Mode", ACCENT_ORANGE));
        JButton startRandomButton = createStyledButton("Start Random", ACCENT_GREEN);
        JButton stopRandomButton = createStyledButton("Stop Random", ACCENT_RED);
        row2.add(startRandomButton);
        row2.add(stopRandomButton);

        /* ── Row 3 : Step Controls ────────────────────── */
        JPanel row3 = createCardRow();
        row3.setBorder(createSectionBorder("Step Controls", ACCENT_TEAL));
        row3.add(createFieldLabel("Distance"));
        row3.add(moveDistInput);
        JButton leftButton = createStyledButton("← Left", ACCENT_BLUE);
        JButton upButton = createStyledButton("↑ Up", ACCENT_BLUE);
        JButton downButton = createStyledButton("↓ Down", ACCENT_BLUE);
        JButton rightButton = createStyledButton("→ Right", ACCENT_BLUE);
        JButton resetButton = createStyledButton("Reset", ACCENT_ORANGE);
        row3.add(leftButton);
        row3.add(upButton);
        row3.add(downButton);
        row3.add(rightButton);
        row3.add(resetButton);

        /* ── Row 4 : Session Status ───────────────────── */
        /* ── Actions ──────────────────────────────────── */
        startRandomButton.addActionListener(event -> randomTimer.start());
        stopRandomButton.addActionListener(event -> randomTimer.stop());

        leftButton.addActionListener(event -> moveWithDirection("Move Left", -getDistance(), 0));
        upButton.addActionListener(event -> moveWithDirection("Move Up", 0, getDistance()));
        downButton.addActionListener(event -> moveWithDirection("Move Down", 0, -getDistance()));
        rightButton.addActionListener(event -> moveWithDirection("Move Right", getDistance(), 0));
        resetButton.addActionListener(event -> {
            randomTimer.stop();
            submit(bridgeService.setCoordinates(400, 300));
        });
        panel.add(row2);
        panel.add(row3);
        return panel;
    }

    /* ══════════════════════════════════════════════════════════ */
    /*  STYLED UI FACTORY METHODS                               */
    /* ══════════════════════════════════════════════════════════ */

    /** Returns a dark‑themed card row with rounded corners. */
    private JPanel createCardRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 7)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
            }
        };
        row.setOpaque(false);
        return row;
    }

    /** Returns a compact label styled for input fields. */
    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_LABEL);
        label.setForeground(TEXT_SECONDARY);
        return label;
    }

    /** Returns a dark‑themed text field with rounded appearance. */
    private JTextField createStyledInput(String defaultText, int columns) {
        JTextField field = new JTextField(defaultText, columns) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_INPUT);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setFont(FONT_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(ACCENT_BLUE);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER_SUBTLE, 1, 10),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        return field;
    }

    /** Returns a premium pill‑shaped button with hover & press effects. */
    private JButton createStyledButton(String text, Color accent) {
        JButton button = new JButton(text) {
            private boolean hovered = false;
            private boolean pressed = false;

            {
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFont(FONT_BUTTON);
                setForeground(Color.WHITE);
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override
                    public void mouseExited(MouseEvent e) { hovered = false; pressed = false; repaint(); }
                    @Override
                    public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                    @Override
                    public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor = accent;
                if (pressed) {
                    bgColor = darker(accent, 0.78f);
                } else if (hovered) {
                    bgColor = brighter(accent, 1.18f);
                }

                // Subtle shadow
                g2.setColor(new Color(0, 0, 0, 40));
                g2.fill(new RoundRectangle2D.Double(1, 2, getWidth() - 2, getHeight() - 2, 12, 12));

                // Main pill
                g2.setPaint(new GradientPaint(0, 0, brighter(bgColor, 1.08f), 0, getHeight(), bgColor));
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 2, 12, 12));

                // Top highlight
                g2.setColor(new Color(255, 255, 255, hovered ? 38 : 22));
                g2.fill(new RoundRectangle2D.Double(1, 1, getWidth() - 3, getHeight() / 2.0, 12, 12));

                // Text
                FontMetrics fm = g2.getFontMetrics(getFont());
                g2.setFont(getFont());
                g2.setColor(getForeground());
                int textWidth = fm.stringWidth(getText());
                int textX = (getWidth() - textWidth) / 2;
                int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), textX, textY);

                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(getFont());
                int w = fm.stringWidth(getText()) + 28;
                int h = fm.getHeight() + 14;
                return new Dimension(w, h);
            }
        };
        return button;
    }

    /** Custom titled border with accent colour. */
    private Border createSectionBorder(String title, Color accent) {
        return BorderFactory.createCompoundBorder(
                new AbstractBorder() {
                    @Override
                    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(BORDER_SUBTLE);
                        g2.draw(new RoundRectangle2D.Double(x, y, width - 1, height - 1, 16, 16));

                        // Accent bar on the left edge
                        g2.setColor(accent);
                        g2.fill(new RoundRectangle2D.Double(x, y + 6, 4, 22, 4, 4));

                        // Title text
                        g2.setFont(FONT_SMALL);
                        g2.setColor(accent);
                        g2.drawString(title, x + 12, y + 16);
                        g2.dispose();
                    }

                    @Override
                    public Insets getBorderInsets(Component c) {
                        return new Insets(22, 10, 6, 10);
                    }
                },
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        );
    }

    /** Creates the styled JTable with dark theme and alternating rows. */
    private JTable createStyledTable() {
        JTable table = new JTable(tableModel);
        table.setFont(FONT_TABLE);
        table.setRowHeight(26);
        table.setBackground(BG_CARD);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_SUBTLE);
        table.setSelectionBackground(ACCENT_BLUE.darker());
        table.setSelectionForeground(Color.WHITE);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        // Alternating row colours
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                            boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? BG_CARD : TABLE_ROW_ALT);
                    c.setForeground(TEXT_PRIMARY);
                }
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return c;
            }
        });

        // Header styling
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 12));
        header.setBackground(TABLE_HEADER);
        header.setForeground(TEXT_SECONDARY);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_PURPLE));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                            boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                label.setHorizontalAlignment(SwingConstants.LEFT);
                label.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 12));
                label.setBackground(TABLE_HEADER);
                label.setForeground(TEXT_SECONDARY);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_PURPLE),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
                return label;
            }
        });

        return table;
    }

    /* ══════════════════════════════════════════════════════════ */
    /*  HELPERS                                                 */
    /* ══════════════════════════════════════════════════════════ */

    private void moveWithDirection(String direction, double dx, double dy) {
        randomTimer.stop();
        submit(bridgeService.movePoint(direction, dx, dy));
    }

    private double getDistance() {
        try {
            return Double.parseDouble(moveDistInput.getText());
        } catch (NumberFormatException error) {
            return 10.0;
        }
    }

    private void toggleVisualState(JButton button) {
        boolean nextState = !robotArmPanel.isOverlayVisible();
        robotArmPanel.setOverlayVisible(nextState);
        button.setText(nextState ? "Hide Visual State" : "Show Visual State");
    }

    private void submit(CompletableFuture<FlowEvent> future) {
        statusLabel.setText("Waiting for native response...");
        statusLabel.setForeground(ACCENT_ORANGE);
        future.whenComplete((event, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                statusLabel.setText("Last action failed");
                statusLabel.setForeground(ACCENT_RED);
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                showError(cause.getMessage() != null ? cause.getMessage() : "Native action failed.");
                return;
            }
            applyEvent(event);
        }));
    }

    private void applyEvent(FlowEvent event) {
        xInput.setText(String.format("%.2f", event.getHandX()));
        yInput.setText(String.format("%.2f", event.getHandY()));
        robotArmPanel.setEvent(event);
        if (isRequestedOutOfReach(event)) {
            statusLabel.setText(String.format("%s  |  Reached max", event.getActionName()));
            statusLabel.setForeground(ACCENT_ORANGE);
        } else if (event.getErrHand() != RobotApi.OK) {
            statusLabel.setText(String.format("%s  |  %s", event.getActionName(), event.getErrorLabel()));
            statusLabel.setForeground(ACCENT_ORANGE);
        } else {
            statusLabel.setText(String.format("%s  |  DB %s", event.getActionName(), event.getDbStatusLabel()));
            statusLabel.setForeground(ACCENT_GREEN);
        }

        if (event.isPersisted()) {
            tableModel.insertRow(0, new Object[]{
                    event.getDbRowId(),
                    event.getDbTimestamp(),
                    event.getActionName(),
                    event.getRawHand(),
                    String.format("%.2f", event.getAngleHand()),
                    String.format("%.3f", event.getAccHand()),
                    String.format("%.1f", event.getHandX()),
                    String.format("%.1f", event.getHandY()),
                    event.getErrorLabel()
            });
            while (tableModel.getRowCount() > 50) {
                tableModel.removeRow(tableModel.getRowCount() - 1);
            }
        }
    }

    private boolean isRequestedOutOfReach(FlowEvent event) {
        if (event.getErrHand() != RobotApi.OK) {
            return true;
        }
        return Math.hypot(event.getHandX() - 400.0, event.getHandY() - 300.0) > 80.2;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /* ── Colour Utilities ────────────────────────────────────── */
    private static Color brighter(Color c, float factor) {
        return new Color(
                Math.min(255, (int) (c.getRed() * factor)),
                Math.min(255, (int) (c.getGreen() * factor)),
                Math.min(255, (int) (c.getBlue() * factor)),
                c.getAlpha()
        );
    }

    private static Color darker(Color c, float factor) {
        return new Color(
                Math.max(0, (int) (c.getRed() * factor)),
                Math.max(0, (int) (c.getGreen() * factor)),
                Math.max(0, (int) (c.getBlue() * factor)),
                c.getAlpha()
        );
    }

    /* ── Rounded line border ─────────────────────────────────── */
    private static class RoundedLineBorder extends AbstractBorder {
        private final Color color;
        private final int thickness;
        private final int arc;

        RoundedLineBorder(Color color, int thickness, int arc) {
            this.color = color;
            this.thickness = thickness;
            this.arc = arc;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Double(x, y, w - 1, h - 1, arc, arc));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 2, thickness + 4, thickness + 2, thickness + 4);
        }
    }

    /* ══════════════════════════════════════════════════════════ */
    /*  ENTRY POINT                                             */
    /* ══════════════════════════════════════════════════════════ */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new RoboticArmMonitor().setVisible(true);
            } catch (Throwable error) {
                JOptionPane.showMessageDialog(null,
                        "Unable to start the monitor.\n" + error.getMessage(),
                        "Startup Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
