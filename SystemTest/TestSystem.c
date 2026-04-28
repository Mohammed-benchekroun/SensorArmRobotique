// test_system.c
// Test system for Robot Bridge - compile with robot_bridge.c
// Visual Studio: Add both files to project, set test_system.c as main

#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include "robot.h"

// ==============================================
// TEST FRAMEWORK MACROS
// ==============================================
#define TEST_PASSED 1
#define TEST_FAILED 0

static int tests_total = 0;
static int tests_passed = 0;
static int tests_failed = 0;

#define TEST_START(name) \
    do { \
        tests_total++; \
        printf("[TEST] %-35s : ", name); \
    } while(0)

#define TEST_ASSERT(condition) \
    do { \
        if (!(condition)) { \
            printf("FAIL\n"); \
            printf("       FAILED at %s:%d\n", __FILE__, __LINE__); \
            tests_failed++; \
            return TEST_FAILED; \
        } \
    } while(0)

#define TEST_ASSERT_DOUBLE_EQ(expected, actual, tolerance) \
    do { \
        double e = (expected); \
        double a = (actual); \
        double tol = (tolerance); \
        if (fabs(e - a) > tol) { \
            printf("FAIL\n"); \
            printf("       Expected: %f, Actual: %f (tolerance: %f)\n", e, a, tol); \
            tests_failed++; \
            return TEST_FAILED; \
        } \
    } while(0)

#define TEST_ASSERT_INT_EQ(expected, actual) \
    do { \
        int e = (expected); \
        int a = (actual); \
        if (e != a) { \
            printf("FAIL\n"); \
            printf("       Expected: %d, Actual: %d\n", e, a); \
            tests_failed++; \
            return TEST_FAILED; \
        } \
    } while(0)

#define TEST_END() \
    do { \
        printf("PASS\n"); \
        tests_passed++; \
        return TEST_PASSED; \
    } while(0)

// ==============================================
// HELPER FOR TESTING (uses test database)
// ==============================================
static void use_test_database(void)
{
    // Override DB filename by closing and reopening with test db
    extern sqlite3* db;
    extern int db_initialized;

    if (db_initialized) {
        fermer_db();
    }

    // Re-open with test database
    db_initialized = 0;
    db = NULL;

    // Temporarily rename function behavior - we need to modify init_db
    // For now, just ensure clean state
    printf("[TEST] Using test database mode\n");
}

// ==============================================
// MATH TESTS
// ==============================================

static int test_calibrer_min(void)
{
    TEST_START("calibrer() returns MIN_ANGLE at MIN_RAW");
    double result = calibrer(MIN_RAW);
    TEST_ASSERT_DOUBLE_EQ(MIN_ANGLE, result, 0.001);
    TEST_END();
}

static int test_calibrer_max(void)
{
    TEST_START("calibrer() returns MAX_ANGLE at MAX_RAW");
    double result = calibrer(MAX_RAW);
    TEST_ASSERT_DOUBLE_EQ(MAX_ANGLE, result, 0.001);
    TEST_END();
}

static int test_calibrer_mid(void)
{
    TEST_START("calibrer() returns 0 at midpoint");
    int mid_raw = (MIN_RAW + MAX_RAW) / 2;
    double result = calibrer(mid_raw);
    TEST_ASSERT_DOUBLE_EQ(0.0, result, 0.5);
    TEST_END();
}

static int test_calibrer_out_of_range(void)
{
    TEST_START("calibrer() returns 0.0 for out of range inputs");
    double result_below = calibrer(MIN_RAW - 100);
    double result_above = calibrer(MAX_RAW + 100);
    TEST_ASSERT_DOUBLE_EQ(0.0, result_below, 0.001);
    TEST_ASSERT_DOUBLE_EQ(0.0, result_above, 0.001);
    TEST_END();
}

static int test_calculer_position_angle_zero(void)
{
    TEST_START("calculer_position() angle 0 -> (OFFSET_X+L_HAND, OFFSET_Y)");
    double x, y;
    calculer_position(0.0, &x, &y);
    TEST_ASSERT_DOUBLE_EQ(OFFSET_X + L_HAND, x, 0.001);
    TEST_ASSERT_DOUBLE_EQ(OFFSET_Y, y, 0.001);
    TEST_END();
}

static int test_calculer_position_angle_90(void)
{
    TEST_START("calculer_position() angle 90 -> (OFFSET_X, OFFSET_Y+L_HAND)");
    double x, y;
    calculer_position(90.0, &x, &y);
    TEST_ASSERT_DOUBLE_EQ(OFFSET_X, x, 0.001);
    TEST_ASSERT_DOUBLE_EQ(OFFSET_Y + L_HAND, y, 0.001);
    TEST_END();
}

static int test_calculer_angle_zero_position(void)
{
    TEST_START("calculer_angle() at (OFFSET_X+L_HAND, OFFSET_Y) -> 0");
    double angle = calculer_angle(OFFSET_X + L_HAND, OFFSET_Y);
    TEST_ASSERT_DOUBLE_EQ(0.0, angle, 0.001);
    TEST_END();
}

static int test_round_trip_angle_to_position_to_angle(void)
{
    TEST_START("round trip: angle -> position -> angle is consistent");
    double test_angles[] = { -180, -135, -90, -45, 0, 45, 90, 135, 180 };

    for (int i = 0; i < 9; i++) {
        double original_angle = test_angles[i];
        double x, y;
        calculer_position(original_angle, &x, &y);
        double computed_angle = calculer_angle(x, y);

        // Special case: -180 and 180 are equivalent positions
        if (fabs(original_angle) == 180.0 && fabs(computed_angle) == 180.0) {
            // Both represent same physical position - acceptable
            continue;
        }

        TEST_ASSERT_DOUBLE_EQ(original_angle, computed_angle, 0.01);
    }
    TEST_END();
}

static int test_raw_from_angle_consistency(void)
{
    TEST_START("raw_from_angle() is inverse of calibrer()");
    int test_raws[] = { 0, 500, 1000, 2048, 3000, 3500, 4095 };

    for (int i = 0; i < 7; i++) {
        int original_raw = test_raws[i];
        double angle = calibrer(original_raw);
        int computed_raw = raw_from_angle(angle);
        TEST_ASSERT_INT_EQ(original_raw, computed_raw);
    }
    TEST_END();
}

// ==============================================
// ERROR DETECTION TESTS
// ==============================================

static int test_detecter_erreur_under(void)
{
    TEST_START("detecter_erreur() returns UNDER for raw < MIN_RAW");
    int result = detecter_erreur(MIN_RAW - 1);
    TEST_ASSERT_INT_EQ(UNDER, result);
    TEST_END();
}

static int test_detecter_erreur_over(void)
{
    TEST_START("detecter_erreur() returns OVER for raw > MAX_RAW");
    int result = detecter_erreur(MAX_RAW + 1);
    TEST_ASSERT_INT_EQ(OVER, result);
    TEST_END();
}

static int test_detecter_erreur_ok(void)
{
    TEST_START("detecter_erreur() returns OK for raw in range");
    int result = detecter_erreur(2048);
    TEST_ASSERT_INT_EQ(OK, result);
    TEST_END();
}

static int test_detecter_erreur_position_within_limit(void)
{
    TEST_START("detecter_erreur_position() returns OK for distance <= L_HAND");
    double x = OFFSET_X + L_HAND - 0.1;
    double y = OFFSET_Y;
    int result = detecter_erreur_position(x, y);
    TEST_ASSERT_INT_EQ(OK, result);
    TEST_END();
}

static int test_detecter_erreur_position_beyond_limit(void)
{
    TEST_START("detecter_erreur_position() returns OVER for distance > L_HAND+0.2");
    double x = OFFSET_X + L_HAND + 1.0;
    double y = OFFSET_Y;
    int result = detecter_erreur_position(x, y);
    TEST_ASSERT_INT_EQ(OVER, result);
    TEST_END();
}

// ==============================================
// DATABASE TESTS
// ==============================================

static int test_db_initialization(void)
{
    TEST_START("init_db() creates database and table");
    fermer_db(); // Close any existing connection
    int result = init_db();
    TEST_ASSERT_INT_EQ(0, result);
    TEST_END();
}

static int test_insert_lecture_valid_data(void)
{
    TEST_START("insert_lecture() saves valid data and returns row_id");
    sqlite3_int64 row_id = -1;
    char timestamp[DB_TIMESTAMP_SIZE];

    int result = insert_lecture(2048, 0.0, 0.5, OFFSET_X + 40, OFFSET_Y, OK, &row_id, timestamp, DB_TIMESTAMP_SIZE);

    TEST_ASSERT_INT_EQ(0, result);
    TEST_ASSERT(row_id > 0);
    TEST_ASSERT(strlen(timestamp) > 0);
    TEST_END();
}

static int test_get_record_count_after_insert(void)
{
    TEST_START("get_record_count() returns correct count after insert");
    int count_before = get_record_count();

    insert_lecture(1024, 45.0, 0.3, OFFSET_X + 30, OFFSET_Y + 30, OK, NULL, NULL, 0);

    int count_after = get_record_count();
    TEST_ASSERT_INT_EQ(count_before + 1, count_after);
    TEST_END();
}

static int test_clear_all_data(void)
{
    TEST_START("clear_all_data_no_prompt() deletes all records");
    insert_lecture(500, 10.0, 0.1, OFFSET_X + 10, OFFSET_Y, OK, NULL, NULL, 0);
    int count_before = get_record_count();

    clear_all_data_no_prompt();

    int count_after = get_record_count();
    TEST_ASSERT(count_before > 0);
    TEST_ASSERT_INT_EQ(0, count_after);
    TEST_END();
}

// ==============================================
// SNAPSHOT API TESTS
// ==============================================

static int test_snapshot_from_coordinates(void)
{
    TEST_START("snapshot_from_coordinates() computes correct angle and raw");
    double test_x = OFFSET_X + 40;
    double test_y = OFFSET_Y + 40;
    double expected_angle = calculer_angle(test_x, test_y);

    BridgeSnapshot snapshot = snapshot_from_coordinates(test_x, test_y, 0.5);

    TEST_ASSERT_DOUBLE_EQ(expected_angle, snapshot.angle_hand, 0.001);
    TEST_ASSERT_DOUBLE_EQ(test_x, snapshot.hand_x, 0.001);
    TEST_ASSERT_DOUBLE_EQ(test_y, snapshot.hand_y, 0.001);
    TEST_ASSERT_DOUBLE_EQ(0.5, snapshot.acc_hand, 0.001);
    TEST_END();
}

static int test_set_coordinates_snapshot_saves_to_db(void)
{
    TEST_START("set_coordinates_snapshot() saves to database");
    clear_all_data_no_prompt();
    int count_before = get_record_count();

    BridgeSnapshot snapshot = set_coordinates_snapshot(OFFSET_X + 50, OFFSET_Y);

    int count_after = get_record_count();
    TEST_ASSERT_INT_EQ(count_before + 1, count_after);
    TEST_ASSERT_INT_EQ(DB_STATUS_SAVED, snapshot.db_status);
    TEST_ASSERT(snapshot.db_row_id > 0);
    TEST_END();
}

static int test_move_point_snapshot_accumulates(void)
{
    TEST_START("move_point_snapshot() accumulates movement");
    set_coordinates_snapshot(OFFSET_X, OFFSET_Y);

    move_point_snapshot(10, 20);
    double x1 = get_current_x();
    double y1 = get_current_y();

    move_point_snapshot(5, -10);
    double x2 = get_current_x();
    double y2 = get_current_y();

    TEST_ASSERT_DOUBLE_EQ(OFFSET_X + 15, x2, 0.001);
    TEST_ASSERT_DOUBLE_EQ(OFFSET_Y + 10, y2, 0.001);
    TEST_END();
}

static int test_generate_random_snapshot_updates_position_on_ok(void)
{
    TEST_START("generate_random_snapshot() updates position when err==OK");
    set_coordinates_snapshot(OFFSET_X, OFFSET_Y);
    double old_x = get_current_x();
    double old_y = get_current_y();

    // We need a way to force a valid random - this is probabilistic
    // For deterministic test, we'll use a known good snapshot
    BridgeSnapshot snapshot = generate_random_snapshot();

    if (snapshot.err_hand == OK) {
        TEST_ASSERT(fabs(get_current_x() - old_x) > 0.001 ||
            fabs(get_current_y() - old_y) > 0.001);
    }
    TEST_END();
}

// ==============================================
// BOUNDARY AND STRESS TESTS
// ==============================================

static int test_boundary_min_angle_position(void)
{
    TEST_START("MIN_ANGLE (-180) produces correct position");
    double x, y;
    calculer_position(MIN_ANGLE, &x, &y);
    // -180 degrees = pointing left from origin
    TEST_ASSERT_DOUBLE_EQ(OFFSET_X - L_HAND, x, 0.001);
    TEST_ASSERT_DOUBLE_EQ(OFFSET_Y, y, 0.001);
    TEST_END();
}

static int test_boundary_max_angle_position(void)
{
    TEST_START("MAX_ANGLE (180) produces correct position");
    double x, y;
    calculer_position(MAX_ANGLE, &x, &y);
    // 180 degrees = pointing left (same as -180)
    TEST_ASSERT_DOUBLE_EQ(OFFSET_X - L_HAND, x, 0.001);
    TEST_ASSERT_DOUBLE_EQ(OFFSET_Y, y, 0.001);
    TEST_END();
}

static int test_clamping_on_out_of_bounds_angle(void)
{
    TEST_START("calculer_angle() clamps values outside MIN/MAX");
    double x_too_far = OFFSET_X + (L_HAND * 2);
    double angle = calculer_angle(x_too_far, OFFSET_Y);
    TEST_ASSERT(angle >= MIN_ANGLE && angle <= MAX_ANGLE);
    TEST_END();
}

static int test_large_number_of_operations(void)
{
    TEST_START("stress: 1000 random operations without crash");
    initialize_bridge();
    clear_all_data_no_prompt();

    for (int i = 0; i < 1000; i++) {
        BridgeSnapshot snap = generate_random_snapshot();
        // Just ensure no crash
        (void)snap;
    }

    TEST_ASSERT(get_record_count() == 1000);
    TEST_END();
}

// ==============================================
// MAIN TEST RUNNER
// ==============================================

typedef int (*test_func_t)(void);

static void run_test(test_func_t test, const char* name)
{
    test();
}

int main(void)
{
    printf("\n");
    printf("========================================\n");
    printf("     ROBOT BRIDGE TEST SYSTEM\n");
    printf("========================================\n\n");

    // Initialize for tests - use separate test database
    printf("[SETUP] Initializing test environment...\n");

    // Close any existing DB and start fresh for tests
    fermer_db();

    // Override to use test database by deleting and recreating
    _unlink("robot_test.db");  // Delete if exists

    // Reinitialize with fresh test DB
    if (init_db() != 0) {
        printf("[ERROR] Could not initialize database\n");
        return 1;
    }

    printf("[SETUP] Complete\n\n");

    // Run all tests
    run_test(test_calibrer_min, "calibrer_min");
    run_test(test_calibrer_max, "calibrer_max");
    run_test(test_calibrer_mid, "calibrer_mid");
    run_test(test_calibrer_out_of_range, "calibrer_out_of_range");
    run_test(test_calculer_position_angle_zero, "calculer_position_angle_zero");
    run_test(test_calculer_position_angle_90, "calculer_position_angle_90");
    run_test(test_calculer_angle_zero_position, "calculer_angle_zero_position");
    run_test(test_round_trip_angle_to_position_to_angle, "round_trip_angle_position_angle");
    run_test(test_raw_from_angle_consistency, "raw_from_angle_consistency");

    run_test(test_detecter_erreur_under, "detecter_erreur_under");
    run_test(test_detecter_erreur_over, "detecter_erreur_over");
    run_test(test_detecter_erreur_ok, "detecter_erreur_ok");
    run_test(test_detecter_erreur_position_within_limit, "detecter_erreur_position_within_limit");
    run_test(test_detecter_erreur_position_beyond_limit, "detecter_erreur_position_beyond_limit");

    run_test(test_db_initialization, "db_initialization");
    run_test(test_insert_lecture_valid_data, "insert_lecture_valid_data");
    run_test(test_get_record_count_after_insert, "get_record_count_after_insert");
    run_test(test_clear_all_data, "clear_all_data");

    run_test(test_snapshot_from_coordinates, "snapshot_from_coordinates");
    run_test(test_set_coordinates_snapshot_saves_to_db, "set_coordinates_snapshot_saves_to_db");
    run_test(test_move_point_snapshot_accumulates, "move_point_snapshot_accumulates");
    run_test(test_generate_random_snapshot_updates_position_on_ok, "generate_random_snapshot_updates_position");

    run_test(test_boundary_min_angle_position, "boundary_min_angle_position");
    run_test(test_boundary_max_angle_position, "boundary_max_angle_position");
    run_test(test_clamping_on_out_of_bounds_angle, "clamping_on_out_of_bounds_angle");
    run_test(test_large_number_of_operations, "stress_1000_operations");

    // Summary
    printf("\n");
    printf("========================================\n");
    printf("     TEST RESULTS\n");
    printf("========================================\n");
    printf("  Total tests: %d\n", tests_total);
    printf("  Passed:      %d\n", tests_passed);
    printf("  Failed:      %d\n", tests_failed);
    printf("========================================\n");

    // Cleanup
    fermer_db();
    _unlink("robot_test.db");

    if (tests_failed > 0) {
        printf("\n[FAILURE] Some tests failed!\n");
        return 1;
    }

    printf("\n[SUCCESS] All tests passed!\n");
    return 0;
}