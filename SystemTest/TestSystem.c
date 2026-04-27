double result = calibrer(MIN_RAW);
TEST_ASSERT_DOUBLE_EQ(MIN_ANGLE, result, 0.001); 
double result = calibrer(MAX_RAW);
TEST_ASSERT_DOUBLE_EQ(MAX_ANGLE, result, 0.001);
int mid_raw = (MIN_RAW + MAX_RAW) / 2;
double result = calibrer(mid_raw);
TEST_ASSERT_DOUBLE_EQ(0.0, result, 0.5);
double result_below = calibrer(MIN_RAW - 100);
double result_above = calibrer(MAX_RAW + 100);
TEST_ASSERT_DOUBLE_EQ(0.0, result_below, 0.001);
TEST_ASSERT_DOUBLE_EQ(0.0, result_above, 0.001);
double x, y;
calculer_position(0.0, &x, &y);
TEST_ASSERT_DOUBLE_EQ(OFFSET_X + L_HAND, x, 0.001);
TEST_ASSERT_DOUBLE_EQ(OFFSET_Y, y, 0.001);
double x, y;
calculer_position(90.0, &x, &y);
TEST_ASSERT_DOUBLE_EQ(OFFSET_X, x, 0.001);
TEST_ASSERT_DOUBLE_EQ(OFFSET_Y + L_HAND, y, 0.001);
double angle = calculer_angle(OFFSET_X + L_HAND, OFFSET_Y);
TEST_ASSERT_DOUBLE_EQ(0.0, angle, 0.001);
double test_angles[] = { -180, -135, -90, -45, 0, 45, 90, 135, 180 };
for (int i = 0; i < 9; i++) {
    double original_angle = test_angles[i];
    double x, y;
    calculer_position(original_angle, &x, &y);
    double computed_angle = calculer_angle(x, y);

    // Special case: -180 and 180 are equivalent
    if (fabs(original_angle) == 180.0 && fabs(computed_angle) == 180.0) {
        continue;
    }
    TEST_ASSERT_DOUBLE_EQ(original_angle, computed_angle, 0.01);
}
int test_raws[] = { 0, 500, 1000, 2048, 3000, 3500, 4095 };
for (int i = 0; i < 7; i++) {
    int original_raw = test_raws[i];
    double angle = calibrer(original_raw);
    int computed_raw = raw_from_angle(angle);
    TEST_ASSERT_INT_EQ(original_raw, computed_raw);
}
int result = detecter_erreur(MIN_RAW - 1);
TEST_ASSERT_INT_EQ(UNDER, result);