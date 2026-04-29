package roboticam;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

public class GraphPanel extends JPanel {
    private static final double WORLD_WIDTH = 800.0;
    private static final double WORLD_HEIGHT = 600.0;
    private static final double BASE_X = 400.0;
    private static final double BASE_Y = 300.0;
    private static final double MAX_REACH = 80.0;
    private static final int GRID_STEP = 50;

    /* -- Dark theme colours ------------------------------------ */
    private static final Color BG_TOP          = new Color(22, 24, 36);
    private static final Color BG_BOTTOM       = new Color(16, 18, 28);
    private static final Color GRID_LINE       = new Color(40, 44, 62);
    private static final Color AXIS_LINE       = new Color(66, 133, 244, 90);
    private static final Color BASE_DOT        = new Color(66, 133, 244);
    private static final Color LINE_COLOR      = new Color(66, 155, 244, 180);
    private static final Color HAND_DOT        = new Color(234, 84, 76);
    private static final Color HAND_RING       = new Color(255, 255, 255, 120);
    private static final Color LABEL_PRIMARY   = new Color(230, 234, 245);
    private static final Color LABEL_SECONDARY = new Color(150, 158, 180);
    private static final Color TITLE_COLOR     = new Color(66, 133, 244);

    private FlowEvent currentEvent;

    public GraphPanel() {
        setOpaque(false);
    }

    public void setEvent(FlowEvent event) {
        this.currentEvent = event;
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

        // Background with rounded corners
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, height, BG_BOTTOM));
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, 24, 24));

        // Subtle inner glow
        g2.setColor(new Color(255, 255, 255, 8));
        g2.draw(new RoundRectangle2D.Double(1, 1, width - 3, height - 3, 22, 22));

        // Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(TITLE_COLOR);
        g2.drawString("Coordinate Graph", 14, 22);

        // Grid lines
        g2.setStroke(new BasicStroke(1f));
        for (int x = 0; x <= WORLD_WIDTH; x += GRID_STEP) {
            int screenX = worldToScreenX(x, width);
            g2.setColor(x == BASE_X ? AXIS_LINE : GRID_LINE);
            g2.drawLine(screenX, 32, screenX, height - 8);
        }
        for (int y = 0; y <= WORLD_HEIGHT; y += GRID_STEP) {
            int screenY = worldToScreenY(y, height);
            g2.setColor(y == BASE_Y ? AXIS_LINE : GRID_LINE);
            g2.drawLine(8, screenY, width - 8, screenY);
        }

        int baseScreenX = worldToScreenX(BASE_X, width);
        int baseScreenY = worldToScreenY(BASE_Y, height);

        // Base dot with glow
        g2.setColor(new Color(66, 133, 244, 40));
        g2.fill(new Ellipse2D.Double(baseScreenX - 16, baseScreenY - 16, 32, 32));
        g2.setColor(BASE_DOT);
        g2.fill(new Ellipse2D.Double(baseScreenX - 7, baseScreenY - 7, 14, 14));
        g2.setColor(new Color(255, 255, 255, 80));
        g2.fill(new Ellipse2D.Double(baseScreenX - 3, baseScreenY - 5, 6, 4));
        g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 11));
        g2.setColor(LABEL_SECONDARY);
        g2.drawString("Base", baseScreenX + 12, baseScreenY - 10);

        if (currentEvent != null) {
            int handScreenX = worldToScreenX(currentEvent.getHandX(), width);
            int handScreenY = worldToScreenY(currentEvent.getHandY(), height);

            // Connection line with glow
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(66, 155, 244, 50));
            g2.drawLine(baseScreenX, baseScreenY, handScreenX, handScreenY);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(LINE_COLOR);
            g2.drawLine(baseScreenX, baseScreenY, handScreenX, handScreenY);

            // Hand dot with glow
            g2.setColor(new Color(234, 84, 76, 40));
            g2.fill(new Ellipse2D.Double(handScreenX - 18, handScreenY - 18, 36, 36));
            g2.setColor(HAND_DOT);
            g2.fill(new Ellipse2D.Double(handScreenX - 9, handScreenY - 9, 18, 18));
            g2.setColor(HAND_RING);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Double(handScreenX - 9, handScreenY - 9, 18, 18));
            g2.setColor(new Color(255, 255, 255, 90));
            g2.fill(new Ellipse2D.Double(handScreenX - 4, handScreenY - 6, 7, 5));

            // Labels
            g2.setColor(LABEL_PRIMARY);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.drawString(String.format("Hand (%.1f, %.1f)", currentEvent.getHandX(), currentEvent.getHandY()),
                    handScreenX + 14, handScreenY - 10);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            if (isRequestedOutOfReach(currentEvent)) {
                g2.setColor(new Color(245, 166, 55));
                g2.drawString(String.format("Reached max | Reach %.1f / %.1f",
                        distanceFromBase(currentEvent.getHandX(), currentEvent.getHandY()), MAX_REACH),
                        handScreenX + 14, handScreenY + 8);
            } else {
                g2.setColor(LABEL_SECONDARY);
                g2.drawString(String.format("Angle %.2fÂ° | Raw %d | %s",
                        currentEvent.getAngleHand(), currentEvent.getRawHand(), currentEvent.getErrorLabel()),
                        handScreenX + 14, handScreenY + 8);
            }
        }

        g2.dispose();
    }

    private int worldToScreenX(double x, int width) {
        return (int) Math.round((x / WORLD_WIDTH) * width);
    }

    private int worldToScreenY(double y, int height) {
        return (int) Math.round(height - ((y / WORLD_HEIGHT) * height));
    }

    private double distanceFromBase(double x, double y) {
        return Math.hypot(x - BASE_X, y - BASE_Y);
    }

    private boolean isRequestedOutOfReach(FlowEvent event) {
        if (event.getErrHand() != RobotApi.OK) {
            return true;
        }
        return distanceFromBase(event.getHandX(), event.getHandY()) > MAX_REACH + 0.2;
    }
}
