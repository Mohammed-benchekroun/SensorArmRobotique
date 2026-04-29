package roboticam;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Timer;

public class RobotArmPanel extends JPanel {
    private static final double WORLD_BASE_X = 400.0;
    private static final double WORLD_BASE_Y = 300.0;
    private static final double WORLD_FULL_EXTENSION_DISTANCE = 80.0;
    private static final double TARGET_OVERFLOW_DISTANCE = 30.0;
    private static final double HOME_WORLD_X = 480.0;
    private static final double HOME_WORLD_Y = 300.0;
    private static final double DEFAULT_DISPLAY_REACH = 252.0;

    /* ── Dark theme colours ──────────────────────────────────── */
    private static final Color BG_TOP          = new Color(22, 24, 36);
    private static final Color BG_BOTTOM       = new Color(14, 16, 24);
    private static final Color GRID_LINE       = new Color(36, 40, 58);
    private static final Color RANGE_RING      = new Color(66, 133, 244, 38);
    private static final Color FLOOR_TOP       = new Color(38, 42, 58);
    private static final Color FLOOR_BOTTOM    = new Color(28, 32, 46);
    private static final Color FLOOR_LINE      = new Color(52, 56, 74);
    private static final Color TARGET_LINE     = new Color(66, 133, 244, 80);
    private static final Color TARGET_DOT      = new Color(234, 84, 76);
    private static final Color TARGET_RING     = new Color(255, 255, 255, 140);
    private static final Color WARN_TEXT       = new Color(245, 166, 55);
    private static final Color OVERLAY_BG      = new Color(16, 18, 28, 210);
    private static final Color OVERLAY_BORDER  = new Color(66, 133, 244, 60);
    private static final Color OVERLAY_TITLE   = new Color(66, 133, 244);
    private static final Color OVERLAY_TEXT    = new Color(200, 210, 228);
    private static final Color TITLE_COLOR     = new Color(52, 199, 136);

    /* ── Joints (cool metallic blue-grey on dark) ────────────── */
    private static final Color JOINT_OUTER     = new Color(70, 80, 110);
    private static final Color JOINT_INNER     = new Color(140, 155, 190);
    private static final Color JOINT_SHADOW    = new Color(0, 0, 0, 60);

    /* ── Arm sprite colours (brighter for dark bg) ───────────── */
    private static final Color ARM_UPPER       = new Color(120, 140, 180);
    private static final Color ARM_FOREARM     = new Color(105, 125, 168);

    private final Timer animationTimer;
    private final BufferedImage baseSprite;
    private final BufferedImage upperArmSprite;
    private final BufferedImage forearmSprite;
    private final BufferedImage gripperSprite;

    private FlowEvent currentEvent;
    private double displayedWorldX = HOME_WORLD_X;
    private double displayedWorldY = HOME_WORLD_Y;
    private double targetWorldX = HOME_WORLD_X;
    private double targetWorldY = HOME_WORLD_Y;
    private boolean overlayVisible = true;

    public RobotArmPanel() {
        setOpaque(false);

        BufferedImage loadedBase = loadSprite("assets/robot-base.png");
        BufferedImage loadedUpper = loadSprite("assets/robot-upper-arm.png");
        BufferedImage loadedForearm = loadSprite("assets/robot-forearm.png");
        BufferedImage loadedGripper = loadSprite("assets/robot-gripper.png");

        baseSprite = loadedBase != null ? loadedBase : createBaseSprite();
        upperArmSprite = loadedUpper != null ? loadedUpper : createArmSprite(176, 46, ARM_UPPER);
        forearmSprite = loadedForearm != null ? loadedForearm : createArmSprite(146, 40, ARM_FOREARM);
        gripperSprite = loadedGripper != null ? loadedGripper : createGripperSprite();

        Timer timer = new Timer(16, event -> {
            double nextX = approach(displayedWorldX, targetWorldX);
            double nextY = approach(displayedWorldY, targetWorldY);
            displayedWorldX = nextX;
            displayedWorldY = nextY;

            if (Math.abs(displayedWorldX - targetWorldX) < 0.08
                    && Math.abs(displayedWorldY - targetWorldY) < 0.08) {
                displayedWorldX = targetWorldX;
                displayedWorldY = targetWorldY;
                ((Timer) event.getSource()).stop();
            }
            repaint();
        });
        animationTimer = timer;
    }

    public void setEvent(FlowEvent event) {
        currentEvent = event;
        if (event == null) {
            return;
        }
        updateTargetWorld(event.getHandX(), event.getHandY());

        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
        repaint();
    }

    public void setOverlayVisible(boolean overlayVisible) {
        this.overlayVisible = overlayVisible;
        repaint();
    }

    public boolean isOverlayVisible() {
        return overlayVisible;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int width = getWidth();
        int height = getHeight();

        // Dark rounded background
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, height, BG_BOTTOM));
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, 24, 24));

        // Inner border glow
        g2.setColor(new Color(255, 255, 255, 8));
        g2.draw(new RoundRectangle2D.Double(1, 1, width - 3, height - 3, 22, 22));

        // Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(TITLE_COLOR);
        g2.drawString("Robot Arm Visualizer", 14, 22);

        int topInset = 42;
        int bottomInset = 26;
        int floorY = (int) Math.round(height * 0.88);
        double shoulderX = width * 0.49;
        double shoulderY = height * 0.52;
        double maxReachByWidth = (width - 96.0) / 2.0;
        double maxReachByTop = shoulderY - topInset;
        double maxReachByBottom = floorY - shoulderY - 58.0;
        double displayReach = Math.min(Math.min(maxReachByWidth, maxReachByTop), maxReachByBottom);
        displayReach = clamp(displayReach, 120.0, DEFAULT_DISPLAY_REACH);
        double shoulderLength = displayReach * 0.5;
        double forearmLength = displayReach * 0.5;

        double worldDx = displayedWorldX - WORLD_BASE_X;
        double worldDy = displayedWorldY - WORLD_BASE_Y;
        double worldDistance = Math.hypot(worldDx, worldDy);
        double targetAngle = worldDistance < 0.001 ? -Math.PI / 2.0 : Math.atan2(-worldDy, worldDx);
        double requestedDistance = (worldDistance / WORLD_FULL_EXTENSION_DISTANCE) * displayReach;
        double markerDistance = Math.min(requestedDistance, displayReach + TARGET_OVERFLOW_DISTANCE);
        double commandX = shoulderX + (Math.cos(targetAngle) * markerDistance);
        double commandY = shoulderY + (Math.sin(targetAngle) * markerDistance);

        ArmPose pose = solvePose(
                shoulderX,
                shoulderY,
                commandX,
                commandY,
                requestedDistance,
                worldDistance,
                shoulderLength,
                forearmLength
        );

        drawBackdrop(g2, width, height, shoulderX, shoulderY, displayReach, floorY, bottomInset);
        drawTarget(g2, pose);
        drawBase(g2, shoulderX, shoulderY, displayReach);
        drawSegment(g2, upperArmSprite, pose.baseX, pose.baseY, pose.shoulderAngle, shoulderLength);
        drawJoint(g2, pose.baseX, pose.baseY, scaledRadius(displayReach, 18));
        drawSegment(g2, forearmSprite, pose.elbowX, pose.elbowY, pose.forearmAngle, forearmLength);
        drawJoint(g2, pose.elbowX, pose.elbowY, scaledRadius(displayReach, 15));
        drawGripper(g2, pose.wristX, pose.wristY, pose.forearmAngle, displayReach);
        drawJoint(g2, pose.wristX, pose.wristY, scaledRadius(displayReach, 11));
        if (isRequestedOutOfReach()) {
            drawLimitNotice(g2, width);
        }

        if (overlayVisible) {
            drawOverlayCard(g2, width, height, pose);
        }

        g2.dispose();
    }

    private void drawBackdrop(Graphics2D g2, int width, int height, double shoulderX, double shoulderY,
                              double displayReach, int floorY, int bottomInset) {
        // Inner panel area
        g2.setColor(new Color(255, 255, 255, 6));
        g2.fill(new RoundRectangle2D.Double(12, 32, width - 24, height - 44, 18, 18));

        // Grid
        g2.setColor(GRID_LINE);
        for (int x = 24; x < width - 24; x += 38) {
            g2.drawLine(x, 36, x, height - 18);
        }
        for (int y = 36; y < height - 16; y += 38) {
            g2.drawLine(24, y, width - 24, y);
        }

        // Range rings
        Stroke previousStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(RANGE_RING);
        for (double radius : new double[]{displayReach * 0.38, displayReach * 0.68, displayReach}) {
            g2.draw(new Ellipse2D.Double(shoulderX - radius, shoulderY - radius, radius * 2, radius * 2));
        }
        g2.setStroke(previousStroke);

        // Floor
        g2.setPaint(new GradientPaint(0, floorY, FLOOR_TOP, width, floorY + 42, FLOOR_BOTTOM));
        g2.fillRoundRect(14, floorY, width - 28, 24, 14, 14);
        g2.setColor(FLOOR_LINE);
        g2.drawLine(24, floorY + 12, width - 24, floorY + 12);
        g2.drawLine(24, height - bottomInset, width - 24, height - bottomInset);
    }

    private void drawBase(Graphics2D g2, double shoulderX, double shoulderY, double displayReach) {
        double baseScale = clamp(displayReach / DEFAULT_DISPLAY_REACH, 0.72, 1.0);
        double shadowWidth = 184.0 * baseScale;
        double shadowHeight = 34.0 * baseScale;
        g2.setColor(new Color(0, 0, 0, 50));
        g2.fill(new Ellipse2D.Double(
                shoulderX - (shadowWidth / 2.0),
                shoulderY + (86.0 * baseScale),
                shadowWidth,
                shadowHeight
        ));

        int spriteWidth = (int) Math.round(baseSprite.getWidth() * baseScale);
        int spriteHeight = (int) Math.round(baseSprite.getHeight() * baseScale);
        int spriteX = (int) Math.round(shoulderX - (spriteWidth / 2.0));
        int spriteY = (int) Math.round(shoulderY - (spriteHeight * 0.78));
        g2.drawImage(baseSprite, spriteX, spriteY, spriteWidth, spriteHeight, null);
    }

    private void drawTarget(Graphics2D g2, ArmPose pose) {
        Stroke previousStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{10f, 8f}, 0f));
        g2.setColor(TARGET_LINE);
        g2.drawLine((int) Math.round(pose.baseX), (int) Math.round(pose.baseY),
                (int) Math.round(pose.commandX), (int) Math.round(pose.commandY));
        g2.setStroke(previousStroke);

        // Glow
        g2.setColor(new Color(234, 84, 76, 30));
        g2.fill(new Ellipse2D.Double(pose.commandX - 14, pose.commandY - 14, 28, 28));
        g2.setColor(TARGET_DOT);
        g2.fill(new Ellipse2D.Double(pose.commandX - 7, pose.commandY - 7, 14, 14));
        g2.setColor(TARGET_RING);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Ellipse2D.Double(pose.commandX - 11, pose.commandY - 11, 22, 22));
        g2.setStroke(new BasicStroke(1f));

        if (pose.reachState == ReachState.TOO_FAR) {
            g2.setColor(WARN_TEXT);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.drawString("⚠ Exceeds reach", (float) (pose.commandX + 14), (float) (pose.commandY - 12));
        }
    }

    private void drawOverlayCard(Graphics2D g2, int width, int height, ArmPose pose) {
        double x = width - 228.0;
        double y = height - 176.0;
        RoundRectangle2D card = new RoundRectangle2D.Double(x, y, 210, 140, 18, 18);
        g2.setColor(OVERLAY_BG);
        g2.fill(card);
        g2.setColor(OVERLAY_BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(card);
        g2.setStroke(new BasicStroke(1f));

        // Accent bar
        g2.setColor(OVERLAY_TITLE);
        g2.fill(new RoundRectangle2D.Double(x + 1, y + 6, 3, 18, 3, 3));

        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(OVERLAY_TITLE);
        g2.drawString("Visual State", (int) x + 14, (int) y + 22);

        g2.setFont(new Font("Cascadia Code", Font.PLAIN, 11));
        int lineY = (int) y + 42;
        drawOverlayLine(g2, String.format("Cmd: (%.1f, %.1f)", displayedWorldX, displayedWorldY), x, lineY);
        lineY += 18;
        if (currentEvent != null) {
            drawOverlayLine(g2, String.format("Angle: %.2f°", currentEvent.getAngleHand()), x, lineY);
            lineY += 18;
            drawOverlayLine(g2, String.format("Raw: %d", currentEvent.getRawHand()), x, lineY);
            lineY += 18;
            drawOverlayLine(g2, String.format("Acc: %.3f", currentEvent.getAccHand()), x, lineY);
            lineY += 18;
            drawOverlayLine(g2, String.format("Reach: %.1f / %.1f", pose.worldDistance, WORLD_FULL_EXTENSION_DISTANCE), x, lineY);
            lineY += 18;
            drawOverlayLine(g2, "DB: " + currentEvent.getDbStatusLabel(), x, lineY);
        } else {
            drawOverlayLine(g2, String.format("Reach: %.1f / %.1f", pose.worldDistance, WORLD_FULL_EXTENSION_DISTANCE), x, lineY);
            lineY += 18;
            drawOverlayLine(g2, "Waiting for event...", x, lineY);
        }
    }

    private void drawOverlayLine(Graphics2D g2, String line, double x, int y) {
        g2.setColor(OVERLAY_TEXT);
        g2.drawString(line, (int) x + 14, y);
    }

    private void drawLimitNotice(Graphics2D g2, int width) {
        String message = "Reached max";
        Font font = new Font("Segoe UI Semibold", Font.PLAIN, 11);
        FontMetrics metrics = g2.getFontMetrics(font);
        int pillWidth = metrics.stringWidth(message) + 22;
        int pillHeight = 24;
        int x = width - pillWidth - 16;
        int y = 34;

        g2.setFont(font);
        g2.setColor(new Color(WARN_TEXT.getRed(), WARN_TEXT.getGreen(), WARN_TEXT.getBlue(), 34));
        g2.fill(new RoundRectangle2D.Double(x, y, pillWidth, pillHeight, 12, 12));
        g2.setColor(new Color(WARN_TEXT.getRed(), WARN_TEXT.getGreen(), WARN_TEXT.getBlue(), 110));
        g2.draw(new RoundRectangle2D.Double(x, y, pillWidth, pillHeight, 12, 12));
        g2.setColor(WARN_TEXT);
        g2.drawString(message, x + 11, y + 16);
    }

    private void drawSegment(Graphics2D g2, BufferedImage sprite, double anchorX, double anchorY,
                             double angleRadians, double segmentLength) {
        AffineTransform transform = new AffineTransform();
        transform.translate(anchorX, anchorY);
        transform.rotate(angleRadians);
        double scale = segmentLength / sprite.getWidth();
        transform.scale(scale, scale);
        transform.translate(0, -(sprite.getHeight() / 2.0));
        g2.drawImage(sprite, transform, null);
    }

    private void drawGripper(Graphics2D g2, double wristX, double wristY, double angleRadians, double displayReach) {
        AffineTransform transform = new AffineTransform();
        transform.translate(wristX, wristY);
        transform.rotate(angleRadians);
        double scale = clamp(displayReach / DEFAULT_DISPLAY_REACH, 0.72, 1.0);
        transform.scale(scale, scale);
        transform.translate(0, -(gripperSprite.getHeight() / 2.0));
        g2.drawImage(gripperSprite, transform, null);
    }

    private void drawJoint(Graphics2D g2, double x, double y, int radius) {
        // Shadow
        g2.setColor(JOINT_SHADOW);
        g2.fill(new Ellipse2D.Double(x - radius, y - radius + 3, radius * 2, radius * 2));
        // Outer ring
        g2.setPaint(new GradientPaint((float) x, (float) (y - radius), new Color(90, 102, 140),
                (float) x, (float) (y + radius), JOINT_OUTER));
        g2.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
        // Inner disc
        g2.setPaint(new GradientPaint((float) x, (float) (y - radius * 0.45), new Color(180, 195, 225),
                (float) x, (float) (y + radius * 0.45), JOINT_INNER));
        g2.fill(new Ellipse2D.Double(x - (radius * 0.45), y - (radius * 0.45), radius * 0.9, radius * 0.9));
        // Highlight
        g2.setColor(new Color(255, 255, 255, 50));
        g2.fill(new Ellipse2D.Double(x - (radius * 0.25), y - (radius * 0.35), radius * 0.5, radius * 0.3));
    }

    private ArmPose solvePose(double shoulderX, double shoulderY, double commandX, double commandY,
                              double requestedDistance, double worldDistance,
                              double shoulderLength, double forearmLength) {
        double dx = commandX - shoulderX;
        double dy = commandY - shoulderY;
        double distance = Math.hypot(dx, dy);
        double maxReach = shoulderLength + forearmLength;
        ReachState reachState = worldDistance > WORLD_FULL_EXTENSION_DISTANCE + 0.2
                ? ReachState.TOO_FAR
                : ReachState.WITHIN_RANGE;

        if (distance < 0.001) {
            return new ArmPose(
                    shoulderX,
                    shoulderY,
                    shoulderX,
                    shoulderY - shoulderLength,
                    shoulderX,
                    shoulderY,
                    commandX,
                    commandY,
                    -Math.PI / 2.0,
                    Math.PI / 2.0,
                    0.0,
                    worldDistance,
                    reachState
            );
        }

        double angleToCommand = Math.atan2(dy, dx);
        double solvedDistance = clamp(distance, 0.0, maxReach);
        double solveX = shoulderX + (Math.cos(angleToCommand) * solvedDistance);
        double solveY = shoulderY + (Math.sin(angleToCommand) * solvedDistance);

        double cosElbow = ((shoulderLength * shoulderLength) + (solvedDistance * solvedDistance)
                - (forearmLength * forearmLength)) / (2.0 * shoulderLength * Math.max(solvedDistance, 0.0001));
        cosElbow = clamp(cosElbow, -1.0, 1.0);
        double elbowOffset = Math.acos(cosElbow);
        double bendSide = solveX >= shoulderX ? -1.0 : 1.0;
        double shoulderAngle = angleToCommand + (bendSide * elbowOffset);

        double elbowX = shoulderX + (Math.cos(shoulderAngle) * shoulderLength);
        double elbowY = shoulderY + (Math.sin(shoulderAngle) * shoulderLength);
        double forearmAngle = Math.atan2(solveY - elbowY, solveX - elbowX);
        double wristX = elbowX + (Math.cos(forearmAngle) * forearmLength);
        double wristY = elbowY + (Math.sin(forearmAngle) * forearmLength);

        return new ArmPose(
                shoulderX,
                shoulderY,
                elbowX,
                elbowY,
                wristX,
                wristY,
                commandX,
                commandY,
                shoulderAngle,
                forearmAngle,
                solvedDistance,
                worldDistance,
                reachState
        );
    }

    private double approach(double current, double target) {
        double delta = target - current;
        return current + (delta * 0.18);
    }

    private void updateTargetWorld(double requestedX, double requestedY) {
        double dx = requestedX - WORLD_BASE_X;
        double dy = requestedY - WORLD_BASE_Y;
        double distance = Math.hypot(dx, dy);

        if (distance > WORLD_FULL_EXTENSION_DISTANCE && distance > 0.0001) {
            double scale = WORLD_FULL_EXTENSION_DISTANCE / distance;
            targetWorldX = WORLD_BASE_X + (dx * scale);
            targetWorldY = WORLD_BASE_Y + (dy * scale);
            return;
        }

        targetWorldX = requestedX;
        targetWorldY = requestedY;
    }

    private boolean isRequestedOutOfReach() {
        if (currentEvent == null) {
            return false;
        }
        if (currentEvent.getErrHand() != RobotApi.OK) {
            return true;
        }
        return requestedDistance(currentEvent.getHandX(), currentEvent.getHandY()) > WORLD_FULL_EXTENSION_DISTANCE + 0.2;
    }

    private double requestedDistance(double worldX, double worldY) {
        return Math.hypot(worldX - WORLD_BASE_X, worldY - WORLD_BASE_Y);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int scaledRadius(double displayReach, int baseRadius) {
        double scale = clamp(displayReach / DEFAULT_DISPLAY_REACH, 0.72, 1.0);
        return Math.max(7, (int) Math.round(baseRadius * scale));
    }

    private BufferedImage loadSprite(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            return null;
        }
        try {
            return ImageIO.read(file);
        } catch (IOException ignored) {
            return null;
        }
    }

    private BufferedImage createBaseSprite() {
        BufferedImage image = new BufferedImage(170, 150, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Shadow
        g2.setColor(new Color(0, 0, 0, 50));
        g2.fill(new Ellipse2D.Double(18, 112, 134, 24));

        // Platform
        g2.setPaint(new GradientPaint(0, 0, new Color(80, 90, 120), 0, 60, new Color(50, 58, 78)));
        g2.fill(new RoundRectangle2D.Double(18, 92, 134, 18, 14, 14));

        // Column
        g2.setPaint(new GradientPaint(85, 18, new Color(100, 115, 148), 85, 114, new Color(60, 70, 95)));
        g2.fill(new RoundRectangle2D.Double(62, 18, 46, 96, 28, 28));

        // Top cap
        g2.setPaint(new GradientPaint(0, 0, new Color(120, 135, 170), 0, 44, new Color(75, 85, 110)));
        g2.fill(new Ellipse2D.Double(36, 0, 98, 38));
        g2.setColor(new Color(255, 255, 255, 40));
        g2.draw(new Ellipse2D.Double(42, 6, 86, 24));

        // Bolts
        g2.setColor(new Color(50, 56, 72));
        for (int index = 0; index < 4; index++) {
            double x = 36 + (index * 31);
            g2.fill(new Ellipse2D.Double(x, 97, 9, 9));
        }

        g2.dispose();
        return image;
    }

    private BufferedImage createArmSprite(int width, int height, Color bodyColor) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D body = new RoundRectangle2D.Double(0, 6, width - 14, height - 12, height - 10, height - 10);
        // Shadow
        g2.setColor(new Color(0, 0, 0, 50));
        g2.fill(new RoundRectangle2D.Double(2, 8, width - 14, height - 12, height - 10, height - 10));

        g2.setPaint(new GradientPaint(0, 0, brighter(bodyColor, 1.15f), 0, height, darker(bodyColor, 0.8f)));
        g2.fill(body);

        // Highlight strip
        g2.setColor(new Color(255, 255, 255, 50));
        g2.fill(new RoundRectangle2D.Double(10, 11, width - 44, Math.max(8, (height / 4)), 12, 12));

        // Accent stripes (teal instead of gold to match dark theme)
        g2.setColor(new Color(52, 199, 136, 180));
        for (int stripe = 0; stripe < 3; stripe++) {
            double stripeX = width - 34 + (stripe * 6);
            Path2D band = new Path2D.Double();
            band.moveTo(stripeX, 6);
            band.lineTo(stripeX + 4, 6);
            band.lineTo(stripeX - 6, height - 6);
            band.lineTo(stripeX - 10, height - 6);
            band.closePath();
            g2.fill(band);
        }

        g2.dispose();
        return image;
    }

    private BufferedImage createGripperSprite() {
        BufferedImage image = new BufferedImage(82, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, new Color(110, 125, 160), 0, 60, new Color(65, 75, 100)));
        g2.fill(new RoundRectangle2D.Double(0, 18, 38, 24, 20, 20));
        g2.setColor(new Color(150, 165, 195));
        g2.fill(new Ellipse2D.Double(8, 22, 16, 16));

        g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(70, 82, 108));
        g2.drawLine(40, 17, 66, 6);
        g2.drawLine(40, 43, 66, 54);

        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(140, 155, 185));
        g2.drawLine(45, 18, 70, 9);
        g2.drawLine(45, 42, 70, 51);

        g2.dispose();
        return image;
    }

    /* ── Colour utilities ────────────────────────────────────── */
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

    private static final class ArmPose {
        private final double baseX;
        private final double baseY;
        private final double elbowX;
        private final double elbowY;
        private final double wristX;
        private final double wristY;
        private final double commandX;
        private final double commandY;
        private final double shoulderAngle;
        private final double forearmAngle;
        private final double solvedDistance;
        private final double worldDistance;
        private final ReachState reachState;

        private ArmPose(
                double baseX,
                double baseY,
                double elbowX,
                double elbowY,
                double wristX,
                double wristY,
                double commandX,
                double commandY,
                double shoulderAngle,
                double forearmAngle,
                double solvedDistance,
                double worldDistance,
                ReachState reachState
        ) {
            this.baseX = baseX;
            this.baseY = baseY;
            this.elbowX = elbowX;
            this.elbowY = elbowY;
            this.wristX = wristX;
            this.wristY = wristY;
            this.commandX = commandX;
            this.commandY = commandY;
            this.shoulderAngle = shoulderAngle;
            this.forearmAngle = forearmAngle;
            this.solvedDistance = solvedDistance;
            this.worldDistance = worldDistance;
            this.reachState = reachState;
        }
    }

    private enum ReachState {
        WITHIN_RANGE,
        TOO_FAR
    }
}
