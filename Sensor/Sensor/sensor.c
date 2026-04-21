#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include "sqlite-amalgamation/sqlite3.h"

// External declarations
extern int db_initialized;
extern double current_x;
extern double current_y;

// Constants
#define OFFSET_X 400.0
#define OFFSET_Y 300.0
#define DB_TIMESTAMP_SIZE 20

// Structure definitions
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

// Function prototypes from other files
extern int initialize_bridge(void);
extern void close_bridge(void);
extern BridgeSnapshot generate_random_snapshot(void);
extern BridgeSnapshot set_coordinates_snapshot(double x, double y);
extern BridgeSnapshot move_point_snapshot(double dx, double dy);
extern void afficher_base_donnees(void);
extern void exporter_csv(void);
extern void supprimer_toutes_donnees(void);

// ==============================================
// TEST FUNCTIONS
// ==============================================

void test_bridge_simulation(void) {
    int i;

    printf("\n");
    printf("========================================\n");
    printf("  TEST 1: SIMULATION MODE (Bridge C)\n");
    printf("  C -> SQLite -> (pret pour Java)\n");
    printf("========================================\n\n");

    for (i = 0; i < 5; i++) {
        BridgeSnapshot snapshot = generate_random_snapshot();

        printf("--- Iteration %d ---\n", i + 1);
        printf("Result: Angle=%.1f, Position=(%.0f,%.0f), Raw=%d, Acc=%.2f, Error=%d, DB=%d, Row=%d\n",
            snapshot.angle_hand, snapshot.hand_x, snapshot.hand_y, snapshot.raw_hand,
            snapshot.acc_hand, snapshot.err_hand, snapshot.db_status, snapshot.db_row_id);
    }
}

void test_bridge_manuel(void) {
    char commande;

    printf("\n");
    printf("========================================\n");
    printf("  TEST 2: MANUAL MODE (Bridge C)\n");
    printf("  C -> SQLite -> (pret pour Java)\n");
    printf("========================================\n");
    printf("Commands:\n");
    printf("  Z: Up    S: Down    Q: Left    D: Right\n");
    printf("  R: Reset  V: View DB  0: Exit\n\n");

    (void)set_coordinates_snapshot(OFFSET_X, OFFSET_Y);

    while (1) {
        printf("\rPosition: (%.0f, %.0f) | Command: ", current_x, current_y);

        commande = getchar();
        getchar();

        if (commande == '0') {
            break;
        }

        switch (commande) {
        case 'z':
        case 'Z':
            (void)move_point_snapshot(0, 10);
            break;
        case 's':
        case 'S':
            (void)move_point_snapshot(0, -10);
            break;
        case 'q':
        case 'Q':
            (void)move_point_snapshot(-10, 0);
            break;
        case 'd':
        case 'D':
            (void)move_point_snapshot(10, 0);
            break;
        case 'r':
        case 'R':
            (void)set_coordinates_snapshot(OFFSET_X, OFFSET_Y);
            break;
        case 'v':
        case 'V':
            afficher_base_donnees();
            break;
        default:
            printf("\nInvalid command! (Z/S/Q/D/R/V/0)\n");
        }
    }
}

void afficher_menu_test(void) {
    printf("\n");
    printf("========================================\n");
    printf("     C BRIDGE - SQLite + Java Ready\n");
    printf("========================================\n");
    printf("  1. Test Simulation Mode (5 lectures)\n");
    printf("  2. Test Manual Mode (Interactive)\n");
    printf("  3. View Database (Last 10 records)\n");
    printf("  4. Export to CSV\n");
    printf("  5. Delete All Data\n");
    printf("  0. Exit\n");
    printf("========================================\n");
    printf("Your choice: ");
}

// ==============================================
// MAIN FUNCTION - FOR CONSOLE TESTING
// ==============================================

int main(void) {
    int choix;

    printf("\n");
    printf("========================================\n");
    printf("     C BRIDGE - SQLite DATABASE + JAVA  \n");
    printf("========================================\n");
    printf("Role of C:\n");
    printf(" 1. Generate data (angle, raw, acceleration)\n");
    printf(" 2. Save to SQLite database (robot.db)\n");
    printf(" 3. Send to Java for display (via JNA)\n");
    printf("\nThis MAIN is for testing the C bridge\n");
    printf("In production, Java will call the exported snapshot APIs\n");
    printf("========================================\n");

    if (initialize_bridge() != 0) {
        printf("[ERROR] Database initialization failed\n");
        return 1;
    }

    do {
        afficher_menu_test();
        scanf("%d", &choix);
        getchar();

        switch (choix) {
        case 1:
            test_bridge_simulation();
            break;
        case 2:
            test_bridge_manuel();
            break;
        case 3:
            afficher_base_donnees();
            break;
        case 4:
            exporter_csv();
            break;
        case 5:
            supprimer_toutes_donnees();
            break;
        case 0:
            printf("\n[INFO] Goodbye!\n");
            break;
        default:
            printf("[ERROR] Invalid choice!\n");
        }
    } while (choix != 0);

    close_bridge();
    return 0;
}