#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <math.h>
#include <string.h>
#include "sqlite-amalgamation/sqlite3.h"

#ifdef _WIN32
#define API __declspec(dllexport)
#else
#define API
#endif

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// ==============================================
// CONSTANTS
// ==============================================
#define MIN_RAW 0
#define MAX_RAW 4095
#define MIN_ANGLE -90.0
#define MAX_ANGLE 90.0
#define MIN_ACC -10.0
#define MAX_ACC 10.0
#define OK 0
#define UNDER 1
#define OVER 2

#define DB_STATUS_IDLE 0
#define DB_STATUS_SAVED 1
#define DB_STATUS_ERROR 2

#define L_HAND 80.0
#define OFFSET_X 400.0
#define OFFSET_Y 300.0
#define DB_TIMESTAMP_SIZE 20

// ==============================================
// STRUCTURES
// ==============================================
typedef struct {
    int raw_hand;
    double angle_hand;
    double acc_hand;
    double hand_x;
    double hand_y;
    int err_hand;
} RobotData;

typedef struct {
    int raw_hand;
    double angle_hand;
    double acc_hand;
    double hand_x;
    double hand_y;
    int err_hand;
    int db_status;
    int db_row_id;
    char db_timestamp[DB_TIMESTAMP_SIZE];
} BridgeSnapshot;

// ==============================================
// GLOBAL VARIABLES
// ==============================================
static sqlite3 *db = NULL;
static int db_initialized = 0;
static int rng_seeded = 0;
static double current_x = OFFSET_X;
static double current_y = OFFSET_Y;

// ==============================================
// PROTOTYPES
// ==============================================
static void seed_rng_once(void);
static int ensure_bridge_ready(void);
static void clear_timestamp(char *buffer, size_t size);
static void make_timestamp(char *buffer, size_t size);
static int generer_valeur_brute(void);
static double calibrer(int raw);
static double generer_acceleration(void);
static int detecter_erreur(int raw);
static void calculer_position(double angle, double *x, double *y);
static double calculer_angle(double x, double y);
static int raw_from_angle(double angle);
static int init_db(void);
static int insert_lecture(int raw, double angle, double acc, double x, double y, int err,
                          sqlite3_int64 *row_id, char *timestamp_out, size_t timestamp_size);
static void fill_snapshot(BridgeSnapshot *snapshot, int raw, double angle, double acc, double x, double y, int err);
static void set_db_result(BridgeSnapshot *snapshot, int status, sqlite3_int64 row_id, const char *timestamp);
static BridgeSnapshot snapshot_from_coordinates(double x, double y, double acc);
static BridgeSnapshot snapshot_from_random(void);
static RobotData to_robot_data(BridgeSnapshot snapshot);

int sauvegarder_dans_sqlite(int raw, double angle, double acc, double x, double y, int err);
void afficher_base_donnees(void);
void exporter_csv(void);
void supprimer_toutes_donnees(void);
void fermer_db(void);
void test_bridge_simulation(void);
void test_bridge_manuel(void);
void afficher_menu_test(void);

// ==============================================
// INTERNAL HELPERS
// ==============================================

static void seed_rng_once(void) {
    if (!rng_seeded) {
        srand((unsigned int)time(NULL));
        rng_seeded = 1;
    }
}

static int ensure_bridge_ready(void) {
    seed_rng_once();

    if (!db_initialized) {
        if (init_db() != 0) {
            return -1;
        }
        db_initialized = 1;
    }

    return 0;
}

static void clear_timestamp(char *buffer, size_t size) {
    if (size > 0) {
        memset(buffer, 0, size);
    }
}

static void make_timestamp(char *buffer, size_t size) {
    time_t now = time(NULL);
    struct tm *time_info = localtime(&now);

    clear_timestamp(buffer, size);
    if (time_info != NULL) {
        strftime(buffer, size, "%Y-%m-%d %H:%M:%S", time_info);
    }
}

static int generer_valeur_brute(void) {
    int r = rand() % 100;

    if (r < 80) {
        return MIN_RAW + (rand() % (MAX_RAW - MIN_RAW + 1));
    }
    if (r < 90) {
        return -(rand() % 500) - 1;
    }
    return MAX_RAW + (rand() % 500) + 1;
}

static double calibrer(int raw) {
    if (raw < MIN_RAW || raw > MAX_RAW) {
        return 0.0;
    }

    return MIN_ANGLE + (double)(raw - MIN_RAW) / (MAX_RAW - MIN_RAW) * (MAX_ANGLE - MIN_ANGLE);
}

static double generer_acceleration(void) {
    return MIN_ACC + ((double)rand() / RAND_MAX) * (MAX_ACC - MIN_ACC);
}

static int detecter_erreur(int raw) {
    if (raw < MIN_RAW) {
        return UNDER;
    }
    if (raw > MAX_RAW) {
        return OVER;
    }
    return OK;
}

static void calculer_position(double angle, double *x, double *y) {
    double rad = angle * M_PI / 180.0;
    *x = OFFSET_X + L_HAND * cos(rad);
    *y = OFFSET_Y + L_HAND * sin(rad);
}

static double calculer_angle(double x, double y) {
    double dx = x - OFFSET_X;
    double dy = y - OFFSET_Y;
    double angle = atan2(dy, dx) * 180.0 / M_PI;

    if (angle < MIN_ANGLE) {
        angle = MIN_ANGLE;
    }
    if (angle > MAX_ANGLE) {
        angle = MAX_ANGLE;
    }

    return angle;
}

static int raw_from_angle(double angle) {
    return (int)((angle - MIN_ANGLE) / (MAX_ANGLE - MIN_ANGLE) * (MAX_RAW - MIN_RAW) + MIN_RAW);
}

static void fill_snapshot(BridgeSnapshot *snapshot, int raw, double angle, double acc, double x, double y, int err) {
    snapshot->raw_hand = raw;
    snapshot->angle_hand = angle;
    snapshot->acc_hand = acc;
    snapshot->hand_x = x;
    snapshot->hand_y = y;
    snapshot->err_hand = err;
    snapshot->db_status = DB_STATUS_IDLE;
    snapshot->db_row_id = -1;
    clear_timestamp(snapshot->db_timestamp, sizeof(snapshot->db_timestamp));
}

static void set_db_result(BridgeSnapshot *snapshot, int status, sqlite3_int64 row_id, const char *timestamp) {
    snapshot->db_status = status;
    snapshot->db_row_id = (int)row_id;
    clear_timestamp(snapshot->db_timestamp, sizeof(snapshot->db_timestamp));

    if (timestamp != NULL) {
        strncpy(snapshot->db_timestamp, timestamp, sizeof(snapshot->db_timestamp) - 1);
    }
}

static BridgeSnapshot snapshot_from_coordinates(double x, double y, double acc) {
    BridgeSnapshot snapshot;
    double angle = calculer_angle(x, y);
    int raw = raw_from_angle(angle);
    int err = detecter_erreur(raw);

    fill_snapshot(&snapshot, raw, angle, acc, x, y, err);
    return snapshot;
}

static BridgeSnapshot snapshot_from_random(void) {
    BridgeSnapshot snapshot;
    int raw = generer_valeur_brute();
    double angle = calibrer(raw);
    double acc = generer_acceleration();
    int err = detecter_erreur(raw);
    double x = 0.0;
    double y = 0.0;

    if (err == OK) {
        calculer_position(angle, &x, &y);
    } else {
        x = current_x;
        y = current_y;
        angle = calculer_angle(current_x, current_y);
    }

    fill_snapshot(&snapshot, raw, angle, acc, x, y, err);
    return snapshot;
}

static RobotData to_robot_data(BridgeSnapshot snapshot) {
    RobotData data;

    data.raw_hand = snapshot.raw_hand;
    data.angle_hand = snapshot.angle_hand;
    data.acc_hand = snapshot.acc_hand;
    data.hand_x = snapshot.hand_x;
    data.hand_y = snapshot.hand_y;
    data.err_hand = snapshot.err_hand;

    return data;
}
// ==============================================
// SQLITE
// ==============================================

static int init_db(void) {
    int rc;
    char *err_msg = NULL;
    const char *sql =
        "CREATE TABLE IF NOT EXISTS lectures("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "timestamp TEXT NOT NULL,"
        "raw_hand INTEGER,"
        "angle_hand REAL,"
        "acc_hand REAL,"
        "pos_hand_x REAL,"
        "pos_hand_y REAL,"
        "err_hand INTEGER)";

    if (db != NULL) {
        return 0;
    }

    rc = sqlite3_open("robot.db", &db);
    if (rc != SQLITE_OK) {
        fprintf(stderr, "[DB Error] %s\n", sqlite3_errmsg(db));
        if (db != NULL) {
            sqlite3_close(db);
            db = NULL;
        }
        return -1;
    }

    rc = sqlite3_exec(db, sql, 0, 0, &err_msg);
    if (rc != SQLITE_OK) {
        fprintf(stderr, "[DB Error] %s\n", err_msg);
        sqlite3_free(err_msg);
        sqlite3_close(db);
        db = NULL;
        return -1;
    }

    return 0;
}

static int insert_lecture(int raw, double angle, double acc, double x, double y, int err,
                          sqlite3_int64 *row_id, char *timestamp_out, size_t timestamp_size) {
    sqlite3_stmt *stmt = NULL;
    const char *sql =
        "INSERT INTO lectures "
        "(timestamp, raw_hand, angle_hand, acc_hand, pos_hand_x, pos_hand_y, err_hand) "
        "VALUES (?, ?, ?, ?, ?, ?, ?)";
    char timestamp[DB_TIMESTAMP_SIZE];
    int rc;

    if (ensure_bridge_ready() != 0) {
        return -1;
    }

    make_timestamp(timestamp, sizeof(timestamp));

    rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    if (rc != SQLITE_OK) {
        fprintf(stderr, "[DB Error] %s\n", sqlite3_errmsg(db));
        return -1;
    }

    sqlite3_bind_text(stmt, 1, timestamp, -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 2, raw);
    sqlite3_bind_double(stmt, 3, angle);
    sqlite3_bind_double(stmt, 4, acc);
    sqlite3_bind_double(stmt, 5, x);
    sqlite3_bind_double(stmt, 6, y);
    sqlite3_bind_int(stmt, 7, err);

    rc = sqlite3_step(stmt);
    if (rc != SQLITE_DONE) {
        fprintf(stderr, "[DB Error] %s\n", sqlite3_errmsg(db));
        sqlite3_finalize(stmt);
        return -1;
    }

    if (row_id != NULL) {
        *row_id = sqlite3_last_insert_rowid(db);
    }

    if (timestamp_out != NULL && timestamp_size > 0) {
        clear_timestamp(timestamp_out, timestamp_size);
        strncpy(timestamp_out, timestamp, timestamp_size - 1);
    }

    sqlite3_finalize(stmt);
    return 0;
}

int sauvegarder_dans_sqlite(int raw, double angle, double acc, double x, double y, int err) {
    sqlite3_int64 row_id = -1;
    char timestamp[DB_TIMESTAMP_SIZE];

    if (insert_lecture(raw, angle, acc, x, y, err, &row_id, timestamp, sizeof(timestamp)) != 0) {
        return -1;
    }

    printf("[DB SAVED] ID=%d | Position=(%.0f,%.0f) | Angle=%.1f | Raw=%d | Acc=%.2f | Error=%d\n",
           (int)row_id, x, y, angle, raw, acc, err);

    return (int)row_id;
}

void afficher_base_donnees(void) {
    sqlite3_stmt *stmt = NULL;
    const char *sql =
        "SELECT id, timestamp, raw_hand, angle_hand, acc_hand, pos_hand_x, pos_hand_y, err_hand "
        "FROM lectures ORDER BY id DESC LIMIT 10";
    int rc;

    if (ensure_bridge_ready() != 0) {
        printf("[DB] Database not initialized\n");
        return;
    }

    rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    if (rc != SQLITE_OK) {
        printf("[DB Error] %s\n", sqlite3_errmsg(db));
        return;
    }

    printf("\n");
    printf("+----+---------------------+------+----------+----------+----------+----------+-------+\n");
    printf("| ID | Timestamp           | Raw  | Angle    | Acc      | Pos X    | Pos Y    | Error |\n");
    printf("+----+---------------------+------+----------+----------+----------+----------+-------+\n");

    while (sqlite3_step(stmt) == SQLITE_ROW) {
        int id = sqlite3_column_int(stmt, 0);
        const char *timestamp = (const char *)sqlite3_column_text(stmt, 1);
        int raw = sqlite3_column_int(stmt, 2);
        double angle = sqlite3_column_double(stmt, 3);
        double acc = sqlite3_column_double(stmt, 4);
        double pos_x = sqlite3_column_double(stmt, 5);
        double pos_y = sqlite3_column_double(stmt, 6);
        int err = sqlite3_column_int(stmt, 7);
        const char *err_str = (err == OK) ? "OK" : (err == UNDER) ? "UNDER" : "OVER";

        printf("| %2d | %s | %4d | %8.1f | %8.2f | %8.0f | %8.0f | %5s |\n",
               id, timestamp, raw, angle, acc, pos_x, pos_y, err_str);
    }

    printf("+----+---------------------+------+----------+----------+----------+----------+-------+\n\n");
    sqlite3_finalize(stmt);
}

void exporter_csv(void) {
    sqlite3_stmt *stmt = NULL;
    const char *sql =
        "SELECT id, timestamp, raw_hand, angle_hand, acc_hand, pos_hand_x, pos_hand_y, err_hand "
        "FROM lectures ORDER BY id";
    char filename[100];
    time_t now = time(NULL);
    struct tm *time_info = localtime(&now);
    FILE *file;
    int rc;

    if (ensure_bridge_ready() != 0) {
        printf("[DB] Database not initialized\n");
        return;
    }

    if (time_info == NULL) {
        printf("[ERROR] Cannot create CSV timestamp\n");
        return;
    }

    sprintf(filename, "robot_export_%04d%02d%02d_%02d%02d%02d.csv",
            time_info->tm_year + 1900, time_info->tm_mon + 1, time_info->tm_mday,
            time_info->tm_hour, time_info->tm_min, time_info->tm_sec);

    file = fopen(filename, "w");
    if (file == NULL) {
        printf("[ERROR] Cannot create CSV file\n");
        return;
    }

    fprintf(file, "ID,Timestamp,Raw,Angle(deg),Acceleration,PosX,PosY,Error\n");

    rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    if (rc == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            int err = sqlite3_column_int(stmt, 7);
            const char *err_str = (err == OK) ? "OK" : (err == UNDER) ? "UNDER" : "OVER";

            fprintf(file, "%d,%s,%d,%.2f,%.2f,%.0f,%.0f,%s\n",
                    sqlite3_column_int(stmt, 0),
                    sqlite3_column_text(stmt, 1),
                    sqlite3_column_int(stmt, 2),
                    sqlite3_column_double(stmt, 3),
                    sqlite3_column_double(stmt, 4),
                    sqlite3_column_double(stmt, 5),
                    sqlite3_column_double(stmt, 6),
                    err_str);
        }
        sqlite3_finalize(stmt);
    }

    fclose(file);
    printf("[EXPORT] Data exported to: %s\n", filename);
}

API int clear_all_data_no_prompt(void) {
    char *err_msg = NULL;
    int rc;

    if (ensure_bridge_ready() != 0) {
        return -1;
    }

    rc = sqlite3_exec(db, "DELETE FROM lectures", 0, 0, &err_msg);
    if (rc != SQLITE_OK) {
        printf("[DB Error] %s\n", err_msg);
        sqlite3_free(err_msg);
        return -1;
    }

    sqlite3_exec(db, "DELETE FROM sqlite_sequence WHERE name='lectures'", 0, 0, 0);
    return 0;
}

void supprimer_toutes_donnees(void) {
    char confirmation;

    printf("[WARNING] Delete ALL data from database? (y/N): ");
    scanf(" %c", &confirmation);
    getchar();

    if (confirmation == 'y' || confirmation == 'Y') {
        if (clear_all_data_no_prompt() == 0) {
            printf("[DB] All data deleted\n");
        }
    } else {
        printf("[CANCELLED] Deletion cancelled\n");
    }
}

API int get_record_count(void) {
    sqlite3_stmt *stmt = NULL;
    int count = -1;

    if (ensure_bridge_ready() != 0) {
        return -1;
    }

    if (sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM lectures", -1, &stmt, NULL) == SQLITE_OK) {
        if (sqlite3_step(stmt) == SQLITE_ROW) {
            count = sqlite3_column_int(stmt, 0);
        }
        sqlite3_finalize(stmt);
    }

    return count;
}

void fermer_db(void) {
    if (db != NULL) {
        sqlite3_close(db);
        db = NULL;
    }
    db_initialized = 0;
}