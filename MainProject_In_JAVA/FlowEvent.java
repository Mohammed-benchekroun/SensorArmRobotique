package sensor;

package roboticam;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class FlowEvent {
    private final String actionName;
    private final List<String> requestLines;
    private final int rawHand;
    private final double angleHand;
    private final double accHand;
    private final double handX;
    private final double handY;
    private final int errHand;
    private final int dbStatus;
    private final int dbRowId;
    private final String dbTimestamp;
    private final String eventTime;

    public FlowEvent(String actionName, List<String> requestLines, RobotApi.BridgeSnapshot snapshot) {
        this.actionName = actionName;
        this.requestLines = Collections.unmodifiableList(new ArrayList<>(requestLines));
        this.rawHand = snapshot.raw_hand;
        this.angleHand = snapshot.angle_hand;
        this.accHand = snapshot.acc_hand;
        this.handX = snapshot.hand_x;
        this.handY = snapshot.hand_y;
        this.errHand = snapshot.err_hand;
        this.dbStatus = snapshot.db_status;
        this.dbRowId = snapshot.db_row_id;
        this.dbTimestamp = snapshot.getTimestamp();
        this.eventTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    public String getActionName() {
        return actionName;
    }

    public List<String> getRequestLines() {
        return requestLines;
    }

    public int getRawHand() {
        return rawHand;
    }

    public double getAngleHand() {
        return angleHand;
    }

    public double getAccHand() {
        return accHand;
    }

    public double getHandX() {
        return handX;
    }

    public double getHandY() {
        return handY;
    }

    public int getErrHand() {
        return errHand;
    }

    public int getDbStatus() {
        return dbStatus;
    }

    public int getDbRowId() {
        return dbRowId;
    }

    public String getDbTimestamp() {
        return dbTimestamp;
    }

    public String getEventTime() {
        return eventTime;
    }

    public boolean isPersisted() {
        return dbStatus == RobotApi.DB_STATUS_SAVED && dbRowId >= 0;
    }

    public String getDbStatusLabel() {
        if (dbStatus == RobotApi.DB_STATUS_SAVED) {
            return "Committed";
        }
        if (dbStatus == RobotApi.DB_STATUS_ERROR) {
            return "Failed";
        }
        return "Idle";
    }

    public String getErrorLabel() {
        if (errHand == RobotApi.OK) {
            return "OK";
        }
        if (errHand == RobotApi.UNDER) {
            return "UNDER";
        }
        return "OVER";
    }

    public List<String> getResultLines() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("Raw: %d", rawHand));
        lines.add(String.format("Angle: %.2f deg", angleHand));
        lines.add(String.format("Acceleration: %.3f", accHand));
        lines.add(String.format("Position: (%.1f, %.1f)", handX, handY));
        lines.add("Error: " + getErrorLabel());
        return lines;
    }

    public List<String> getDbLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Status: " + getDbStatusLabel());
        lines.add("Row ID: " + (dbRowId >= 0 ? dbRowId : "-"));
        lines.add("Timestamp: " + (!dbTimestamp.trim().isEmpty() ? dbTimestamp : "-"));
        return lines;
    }

    public String toLogLine() {
        String id = dbRowId >= 0 ? "#" + dbRowId : "#-";
        return String.format("%s | %s | %s | raw=%d angle=%.1f",
                eventTime, actionName, id, rawHand, angleHand);
    }
}
