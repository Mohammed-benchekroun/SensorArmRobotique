// Jira Task: [Ticket SCRUM-29] - Test C module independently ( Unit Testing)
// https://usmba-team-j15yjeh5.atlassian.net/browse/SCRUM-29 
#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <math.h>
#include <time.h>
#include <string.h>
#include "unity.h"

// Définir TEST_MODE pour éviter le main de arm-robot.c
#define TEST_MODE 1

// Inclure le fichier à tester
#include "robot.c"

void setUp(void) {}
void tearDown(void) {}

// ==============================================
// TEST 1: generer_valeur_brute()
// ==============================================
void test_generer_valeur_brute(void) {
    printf("\n[Test 1] generer_valeur_brute()\n");

    int val = generer_valeur_brute();
    TEST_ASSERT_TRUE(val >= -500 && val <= 4595);

    int dans_limites = 0;
    for (int i = 0; i < 100; i++) {
        int v = generer_valeur_brute();
        if (v >= 0 && v <= 4095) {
            dans_limites++;
        }
        else if (v < 0) {
            TEST_ASSERT_TRUE(v >= -500);
        }
        else if (v > 4095) {
            TEST_ASSERT_TRUE(v <= 4595);
        }
    }
    TEST_ASSERT_TRUE(dans_limites >= 60 && dans_limites <= 95);
    printf("  Valeurs normales: %d/100\n", dans_limites);
}

// ==============================================
// TEST 2: calibrer()
// ==============================================
void test_calibrer(void) {
    printf("\n[Test 2] calibrer()\n");

    TEST_ASSERT_FLOAT_WITHIN(0.01, -180.0, calibrer(0));
    TEST_ASSERT_FLOAT_WITHIN(0.01, 180.0, calibrer(4095));
    TEST_ASSERT_FLOAT_WITHIN(1.0, 0.0, calibrer(2048));
    TEST_ASSERT_FLOAT_WITHIN(0.01, 0.0, calibrer(-100));
    TEST_ASSERT_FLOAT_WITHIN(0.01, 0.0, calibrer(5000));

    double angle = calibrer(1024);
    TEST_ASSERT_TRUE(angle > -180.0 && angle < 180.0);

    printf("  calibrer(0)=%.1f, calibrer(4095)=%.1f\n",
        calibrer(0), calibrer(4095));
}

// ==============================================
// TEST 3: generer_acceleration()
// ==============================================
void test_generer_acceleration(void) {
    printf("\n[Test 3] generer_acceleration()\n");

    for (int i = 0; i < 100; i++) {
        double acc = generer_acceleration();
        TEST_ASSERT_TRUE(acc >= -10.0 && acc <= 10.0);
    }
    printf("  100 valeurs dans [-10, 10]\n");
}

// ==============================================
// TEST 4: detecter_erreur()
// ==============================================
void test_detecter_erreur(void) {
    printf("\n[Test 4] detecter_erreur()\n");

    TEST_ASSERT_EQUAL_INT(0, detecter_erreur(0));
    TEST_ASSERT_EQUAL_INT(0, detecter_erreur(2048));
    TEST_ASSERT_EQUAL_INT(0, detecter_erreur(4095));
    TEST_ASSERT_EQUAL_INT(1, detecter_erreur(-1));
    TEST_ASSERT_EQUAL_INT(1, detecter_erreur(-500));
    TEST_ASSERT_EQUAL_INT(2, detecter_erreur(4096));
    TEST_ASSERT_EQUAL_INT(2, detecter_erreur(5000));

    printf("  OK=%d, UNDER=%d, OVER=%d\n",
        detecter_erreur(2048), detecter_erreur(-1), detecter_erreur(5000));
}

// ==============================================
// TEST 5: calculer_position()
// ==============================================
void test_calculer_position(void) {
    printf("\n[Test 5] calculer_position()\n");

    double x, y;

    calculer_position(0, &x, &y);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 480.0, x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 300.0, y);

    calculer_position(90, &x, &y);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 400.0, x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 380.0, y);

    calculer_position(-90, &x, &y);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 400.0, x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 220.0, y);

    calculer_position(180, &x, &y);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 320.0, x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 300.0, y);

    calculer_position(45, &x, &y);
    double expected_x = 400.0 + 80.0 * cos(45 * M_PI / 180.0);
    double expected_y = 300.0 + 80.0 * sin(45 * M_PI / 180.0);
    TEST_ASSERT_FLOAT_WITHIN(0.01, expected_x, x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, expected_y, y);

    printf("  Angle 0° → (%.0f,%.0f), Angle 180° → (%.0f,%.0f)\n", 480.0, 300.0, 320.0, 300.0);
}

// ==============================================
// TEST 6: calculer_angle()
// ==============================================
void test_calculer_angle(void) {
    printf("\n[Test 6] calculer_angle()\n");

    double angle = calculer_angle(480, 300);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 0.0, angle);

    angle = calculer_angle(400, 380);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 90.0, angle);

    angle = calculer_angle(400, 220);
    TEST_ASSERT_FLOAT_WITHIN(0.01, -90.0, angle);

    angle = calculer_angle(320, 300);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 180.0, angle);

    double px = 400 + 80 * cos(45 * M_PI / 180.0);
    double py = 300 + 80 * sin(45 * M_PI / 180.0);
    angle = calculer_angle(px, py);
    TEST_ASSERT_FLOAT_WITHIN(0.1, 45.0, angle);

    printf("  (480,300) → angle=%.1f°, (320,300) → angle=%.1f°\n",
        calculer_angle(480, 300), calculer_angle(320, 300));
}

// ==============================================
// TEST 7: raw_from_angle() - CORRIGE (accepte 2047 ou 2048)
// ==============================================
void test_raw_from_angle(void) {
    printf("\n[Test 7] raw_from_angle()\n");

    TEST_ASSERT_EQUAL_INT(0, raw_from_angle(-180.0));

    // Accepter 2047 ou 2048 à cause de l'arrondi
    int raw_zero = raw_from_angle(0.0);
    TEST_ASSERT_TRUE(raw_zero == 2047 || raw_zero == 2048);

    TEST_ASSERT_EQUAL_INT(4095, raw_from_angle(180.0));
    TEST_ASSERT_EQUAL_INT(1024, raw_from_angle(-90.0));
    TEST_ASSERT_EQUAL_INT(3072, raw_from_angle(90.0));

    printf("  angle 0° → raw=%d (attendu 2047 ou 2048)\n", raw_zero);
}

// ==============================================
// TEST 8: detecter_erreur_position() - CORRIGE (attend OVER=2)
// ==============================================
void test_detecter_erreur_position(void) {
    printf("\n[Test 8] detecter_erreur_position()\n");

    // Positions valides (sur le cercle)
    TEST_ASSERT_EQUAL_INT(0, detecter_erreur_position(480, 300));
    TEST_ASSERT_EQUAL_INT(0, detecter_erreur_position(400, 380));
    TEST_ASSERT_EQUAL_INT(0, detecter_erreur_position(400, 220));
    TEST_ASSERT_EQUAL_INT(0, detecter_erreur_position(456, 356));

    // Positions invalides (en dehors du cercle) retournent OVER (2)
    TEST_ASSERT_EQUAL_INT(2, detecter_erreur_position(500, 300));
    TEST_ASSERT_EQUAL_INT(2, detecter_erreur_position(400, 500));
    TEST_ASSERT_EQUAL_INT(2, detecter_erreur_position(300, 300));
    TEST_ASSERT_EQUAL_INT(2, detecter_erreur_position(400, 200));

    printf("  Position valide → err=0 (OK), invalide → err=2 (OVER)\n");
}

// ==============================================
// TEST 9: RobotData structure
// ==============================================
void test_robot_data_structure(void) {
    printf("\n[Test 9] Structure RobotData\n");

    RobotData data;
    data.raw_hand = 2048;
    data.angle_hand = 0.0;
    data.acc_hand = 5.5;
    data.hand_x = 480.0;
    data.hand_y = 300.0;
    data.err_hand = 0;

    TEST_ASSERT_EQUAL_INT(2048, data.raw_hand);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 0.0, data.angle_hand);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 5.5, data.acc_hand);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 480.0, data.hand_x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 300.0, data.hand_y);
    TEST_ASSERT_EQUAL_INT(0, data.err_hand);

    printf("  Structure RobotData fonctionne\n");
}

// ==============================================
// TEST 10: get_current_x() et get_current_y()
// ==============================================
void test_get_current_position(void) {
    printf("\n[Test 10] get_current_x() et get_current_y()\n");

    current_x = 450;
    current_y = 350;

    TEST_ASSERT_FLOAT_WITHIN(0.01, 450.0, get_current_x());
    TEST_ASSERT_FLOAT_WITHIN(0.01, 350.0, get_current_y());

    printf("  Position courante: (%.0f, %.0f)\n", get_current_x(), get_current_y());
}

// ==============================================
// TEST 11: set_coordinates() et move_point()
// ==============================================
void test_set_and_move_coordinates(void) {
    printf("\n[Test 11] set_coordinates() et move_point()\n");

    set_coordinates(500, 400);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 500.0, current_x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 400.0, current_y);

    move_point(10, -20);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 510.0, current_x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, 380.0, current_y);

    set_coordinates(OFFSET_X, OFFSET_Y);
    TEST_ASSERT_FLOAT_WITHIN(0.01, OFFSET_X, current_x);
    TEST_ASSERT_FLOAT_WITHIN(0.01, OFFSET_Y, current_y);

    printf("  Deplacement: set(500,400) puis move(10,-20) → (510,380)\n");
}

// ==============================================
// MAIN DES TESTS
// ==============================================
int main(void) {
    printf("\n========================================\n");
    printf("       TESTS UNITAIRES - ROBOT ARM\n");
    printf("========================================\n");

    srand(42);

    UNITY_BEGIN();

    RUN_TEST(test_generer_valeur_brute);
    RUN_TEST(test_calibrer);
    RUN_TEST(test_generer_acceleration);
    RUN_TEST(test_detecter_erreur);
    RUN_TEST(test_calculer_position);
    RUN_TEST(test_calculer_angle);
    RUN_TEST(test_raw_from_angle);
    RUN_TEST(test_detecter_erreur_position);
    RUN_TEST(test_robot_data_structure);
    RUN_TEST(test_get_current_position);
    RUN_TEST(test_set_and_move_coordinates);

    printf("\n========================================\n");
    printf("          FIN DES TESTS\n");
    printf("========================================\n");

    fermer_db();

    return UNITY_END();
}