package sensor;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

public class FlowPanel extends JPanel {

    /* ── Dark theme colours ──────────────────────────────────── */
    private static final Color BG_DARK         = new Color(18, 18, 26);
    private static final Color LIST_BG         = new Color(24, 26, 38);
    private static final Color LIST_FG         = new Color(200, 210, 228);
    private static final Color LIST_ALT        = new Color(28, 30, 44);
    private static final Color BORDER_SUBTLE   = new Color(52, 56, 78);
    private static final Color ACCENT_PURPLE   = new Color(142, 108, 228);
    private static final Color TEXT_SECONDARY   = new Color(150, 158, 180);

    private final FlowCanvas canvas;
    private final DefaultListModel<String> eventModel;

    public FlowPanel() {
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);

        canvas = new FlowCanvas();
        eventModel = new DefaultListModel<>();

        JList<String> eventList = new JList<>(eventModel);
        eventList.setFont(new Font("Cascadia Code", Font.PLAIN, 11));
        eventList.setBackground(LIST_BG);
        eventList.setForeground(LIST_FG);
        eventList.setSelectionBackground(new Color(66, 133, 244, 60));
        eventList.setSelectionForeground(Color.WHITE);
        eventList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (!isSelected) {
                    c.setBackground(index % 2 == 0 ? LIST_BG : LIST_ALT);
                }
                setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(eventList);
        scrollPane.setPreferredSize(new Dimension(320, 160));
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        scrollPane.getViewport().setBackground(LIST_BG);

        // Wrap scrollpane in a titled section
        JPanel listSection = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(26, 27, 38));
                g2d.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
                g2d.dispose();
            }
        };
        listSection.setOpaque(false);
        listSection.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // Title label painted manually
        JPanel titleRow = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                // Accent bar
                g2d.setColor(ACCENT_PURPLE);
                g2d.fill(new RoundRectangle2D.Double(0, 4, 3, 14, 3, 3));
                g2d.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2d.setColor(ACCENT_PURPLE);
                g2d.drawString("Recent Flow Events", 10, 16);
                g2d.dispose();
            }
        };
        titleRow.setOpaque(false);
        titleRow.setPreferredSize(new Dimension(0, 24));

        listSection.add(titleRow, BorderLayout.NORTH);
        listSection.add(scrollPane, BorderLayout.CENTER);

        add(canvas, BorderLayout.CENTER);
        add(listSection, BorderLayout.SOUTH);
    }

    public void showEvent(FlowEvent event) {
        canvas.showEvent(event);
        eventModel.add(0, event.toLogLine());
        while (eventModel.size() > 10) {
            eventModel.remove(eventModel.size() - 1);
        }
    }

    private static class FlowCanvas extends JPanel {

        /* ── Colours ─────────────────────────────────────────── */
        private static final Color CANVAS_TOP     = new Color(14, 18, 28);
        private static final Color CANVAS_BOTTOM  = new Color(24, 32, 48);
        private static final Color NODE_FILL      = new Color(255, 255, 255, 12);
        private static final Color NODE_IDLE      = new Color(60, 68, 95);
        private static final Color NODE_ACTIVE    = new Color(66, 155, 244);
        private static final Color NODE_SUCCESS   = new Color(52, 199, 136);
        private static final Color NODE_ERROR     = new Color(234, 84, 76);
        private static final Color CONNECTOR_BG   = new Color(255, 255, 255, 40);
        private static final Color PACKET_COLOR   = new Color(245, 166, 55);
        private static final Color PACKET_RING    = new Color(255, 255, 255, 120);
        private static final Color CARD_FILL      = new Color(255, 255, 255, 16);
        private static final Color CARD_BORDER    = new Color(255, 255, 255, 28);
        private static final Color TEXT_WHITE     = new Color(255, 255, 255, 230);
        private static final Color TEXT_DIM       = new Color(200, 210, 228);
        private static final Color TITLE_COLOR    = new Color(142, 108, 228);

        private final Timer timer;
        private FlowEvent currentEvent;
        private float progress = 1f;

        FlowCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(420, 700));
            timer = new Timer(30, null);
            timer.addActionListener(e -> {
                progress += 0.035f;
                if (progress >= 1f) {
                    progress = 1f;
                    timer.stop();
                }
                repaint();
            });
        }

        void showEvent(FlowEvent event) {
            this.currentEvent = event;
            this.progress = 0f;
            timer.restart();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // Background
            g2.setPaint(new GradientPaint(0, 0, CANVAS_TOP, 0, height, CANVAS_BOTTOM));
            g2.fill(new RoundRectangle2D.Double(0, 0, width, height, 24, 24));

            // Inner glow
            g2.setColor(new Color(255, 255, 255, 6));
            g2.draw(new RoundRectangle2D.Double(1, 1, width - 3, height - 3, 22, 22));

            // Title
            g2.setColor(TITLE_COLOR);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
            g2.drawString("Runtime Flow", 22, 32);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.setColor(new Color(150, 158, 180));
            g2.drawString("Live visualization of GUI → C Bridge → SQLite", 22, 50);

            int nodeWidth = width - 100;
            int nodeX = 50;
            int guiY = 76;
            int cY = 172;
            int dbY = 268;

            drawConnector(g2, width / 2, guiY + 56, cY, progress, 0f, 0.5f);
            drawConnector(g2, width / 2, cY + 56, dbY, progress, 0.5f, 1f);

            drawNode(g2, nodeX, guiY, nodeWidth, 56, "Java GUI", "Buttons, graph, table, flow view",
                    stageColor(progress, 0), "01");
            drawNode(g2, nodeX, cY, nodeWidth, 56, "C Bridge", "JNA call, math, snapshot, SQLite insert",
                    stageColor(progress, 1), "02");
            drawNode(g2, nodeX, dbY, nodeWidth, 56, "SQLite DB",
                    currentEvent != null ? currentEvent.getDbStatusLabel() : "Idle",
                    stageColor(progress, 2), "03");

            if (currentEvent != null) {
                drawCard(g2, 20, 352, width - 40, 88, "Request Payload", currentEvent.getRequestLines(), new Color(66, 133, 244));
                drawCard(g2, 20, 452, width - 40, 108, "Bridge Snapshot", currentEvent.getResultLines(), new Color(52, 199, 136));
                drawCard(g2, 20, 572, width - 40, 86, "DB Result", currentEvent.getDbLines(), new Color(142, 108, 228));
            }

            g2.dispose();
        }

        private Color stageColor(float animationProgress, int stageIndex) {
            if (currentEvent == null) {
                return NODE_IDLE;
            }

            if (stageIndex == 2 && animationProgress >= 1f) {
                return currentEvent.getDbStatus() == RobotApi.DB_STATUS_ERROR ? NODE_ERROR : NODE_SUCCESS;
            }
            if (stageIndex == 0 && animationProgress < 0.34f) {
                return NODE_ACTIVE;
            }
            if (stageIndex == 1 && animationProgress >= 0.34f && animationProgress < 0.68f) {
                return NODE_ACTIVE;
            }
            if (stageIndex == 2 && animationProgress >= 0.68f) {
                return NODE_ACTIVE;
            }
            return NODE_IDLE;
        }

        private void drawConnector(Graphics2D g2, int centerX, int startY, int endNodeY, float animationProgress,
                                   float startProgress, float endProgress) {
            int endY = endNodeY;

            // Background line
            g2.setColor(CONNECTOR_BG);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(centerX, startY, centerX, endY);

            if (currentEvent == null) {
                return;
            }

            float localProgress = (animationProgress - startProgress) / (endProgress - startProgress);
            if (localProgress < 0f) {
                return;
            }
            if (localProgress > 1f) {
                localProgress = 1f;
            }

            int packetY = startY + Math.round((endY - startY) * localProgress);

            // Packet glow
            g2.setColor(new Color(245, 166, 55, 40));
            g2.fill(new Ellipse2D.Double(centerX - 14, packetY - 14, 28, 28));
            // Packet dot
            g2.setColor(PACKET_COLOR);
            g2.fill(new Ellipse2D.Double(centerX - 7, packetY - 7, 14, 14));
            g2.setColor(PACKET_RING);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Double(centerX - 7, packetY - 7, 14, 14));
        }

        private void drawNode(Graphics2D g2, int x, int y, int width, int height,
                              String title, String subtitle, Color accent, String badge) {
            RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, width, height, 18, 18);
            g2.setColor(NODE_FILL);
            g2.fill(shape);
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(shape);

            // Accent bar left
            g2.fill(new RoundRectangle2D.Double(x + 1, y + 10, 3, height - 20, 3, 3));

            // Badge
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
            g2.drawString(badge, x + width - 28, y + 18);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.setColor(TEXT_WHITE);
            g2.drawString(title, x + 16, y + 24);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.setColor(TEXT_DIM);
            g2.drawString(subtitle, x + 16, y + 42);
        }

        private void drawCard(Graphics2D g2, int x, int y, int width, int height,
                              String title, java.util.List<String> lines, Color accent) {
            RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, width, height, 16, 16);
            g2.setColor(CARD_FILL);
            g2.fill(shape);
            g2.setColor(CARD_BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(shape);

            // Accent bar
            g2.setColor(accent);
            g2.fill(new RoundRectangle2D.Double(x + 1, y + 8, 3, 16, 3, 3));

            g2.setColor(accent);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.drawString(title, x + 14, y + 22);

            g2.setFont(new Font("Cascadia Code", Font.PLAIN, 11));
            g2.setColor(TEXT_DIM);
            int lineY = y + 42;
            for (String line : lines) {
                g2.drawString(line, x + 14, lineY);
                lineY += 17;
                if (lineY > y + height - 8) {
                    break;
                }
            }
        }
    }
}
