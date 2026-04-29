package roboticam;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import java.nio.charset.StandardCharsets;

public interface RobotApi extends Library {
    int DB_STATUS_IDLE = 0;
    int DB_STATUS_SAVED = 1;
    int DB_STATUS_ERROR = 2;

    int OK = 0;
    int UNDER = 1;
    int OVER = 2;

    RobotApi INSTANCE = Native.load("robot", RobotApi.class);

    @Structure.FieldOrder({
        "raw_hand",
        "angle_hand",
        "acc_hand",
        "hand_x",
        "hand_y",
        "err_hand",
        "db_status",
        "db_row_id",
        "db_timestamp"
    })
    class BridgeSnapshot extends Structure implements Structure.ByValue {
        public int raw_hand;
        public double angle_hand;
        public double acc_hand;
        public double hand_x;
        public double hand_y;
        public int err_hand;
        public int db_status;
        public int db_row_id;
        public byte[] db_timestamp = new byte[20];

        public String getTimestamp() {
            int length = 0;
            while (length < db_timestamp.length && db_timestamp[length] != 0) {
                length++;
            }
            return new String(db_timestamp, 0, length, StandardCharsets.UTF_8);
        }
    }

    @Structure.FieldOrder({"raw_hand", "angle_hand", "acc_hand", "hand_x", "hand_y", "err_hand"})
    class RobotData extends Structure implements Structure.ByValue {
        public int raw_hand;
        public double angle_hand;
        public double acc_hand;
        public double hand_x;
        public double hand_y;
        public int err_hand;
    }

    int initialize_bridge();
    void close_bridge();
    BridgeSnapshot get_current_snapshot();
    BridgeSnapshot set_coordinates_snapshot(double x, double y);
    BridgeSnapshot move_point_snapshot(double dx, double dy);
    BridgeSnapshot generate_random_snapshot();
    int get_record_count();
    int clear_all_data_no_prompt();

    RobotData get_current_data();
    void set_coordinates(double x, double y);
    void move_point(double dx, double dy);
    double get_current_x();
    double get_current_y();
    RobotData get_random_data();

    class DoubleByReference extends com.sun.jna.ptr.ByReference {
        public DoubleByReference() {
            super(8);
        }

        public DoubleByReference(double value) {
            super(8);
            setValue(value);
        }

        public void setValue(double value) {
            getPointer().setDouble(0, value);
        }

        public double getValue() {
            return getPointer().getDouble(0);
        }
    }
}
