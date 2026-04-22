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
