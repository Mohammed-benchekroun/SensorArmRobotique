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
int result = detecter_erreur(MAX_RAW + 1);
TEST_ASSERT_INT_EQ(OVER, result);
int result = detecter_erreur(2048);
TEST_ASSERT_INT_EQ(OK, result);
double x = OFFSET_X + L_HAND - 0.1;
double y = OFFSET_Y;
int result = detecter_erreur_position(x, y);
TEST_ASSERT_INT_EQ(OK, result);
double x = OFFSET_X + L_HAND + 1.0;
double y = OFFSET_Y;
int result = detecter_erreur_position(x, y);
TEST_ASSERT_INT_EQ(OVER, result);
fermer_db();
int result = init_db();
TEST_ASSERT_INT_EQ(0, result);
sqlite3_int64 row_id = -1;
char timestamp[DB_TIMESTAMP_SIZE];
int result = insert_lecture(2048, 0.0, 0.5, OFFSET_X + 40, OFFSET_Y, OK, &row_id, timestamp, DB_TIMESTAMP_SIZE);
TEST_ASSERT_INT_EQ(0, result);
TEST_ASSERT(row_id > 0);
TEST_ASSERT(strlen(timestamp) > 0);
int count_before = get_record_count();
insert_lecture(1024, 45.0, 0.3, OFFSET_X + 30, OFFSET_Y + 30, OK, NULL, NULL, 0);
int count_after = get_record_count();
TEST_ASSERT_INT_EQ(count_before + 1, count_after);
insert_lecture(500, 10.0, 0.1, OFFSET_X + 10, OFFSET_Y, OK, NULL, NULL, 0);
int count_before = get_record_count();
clear_all_data_no_prompt();
int count_after = get_record_count();
TEST_ASSERT(count_before > 0);
TEST_ASSERT_INT_EQ(0, count_after);
double test_x = OFFSET_X + 40;
double test_y = OFFSET_Y + 40;
double expected_angle = calculer_angle(test_x, test_y);
BridgeSnapshot snapshot = snapshot_from_coordinates(test_x, test_y, 0.5);
TEST_ASSERT_DOUBLE_EQ(expected_angle, snapshot.angle_hand, 0.001);
TEST_ASSERT_DOUBLE_EQ(test_x, snapshot.hand_x, 0.001);
TEST_ASSERT_DOUBLE_EQ(test_y, snapshot.hand_y, 0.001);
clear_all_data_no_prompt();
int count_before = get_record_count();
BridgeSnapshot snapshot = set_coordinates_snapshot(OFFSET_X + 50, OFFSET_Y);
int count_after = get_record_count();
TEST_ASSERT_INT_EQ(count_before + 1, count_after);
TEST_ASSERT_INT_EQ(DB_STATUS_SAVED, snapshot.db_status);
TEST_ASSERT(snapshot.db_row_id > 0);
set_coordinates_snapshot(OFFSET_X, OFFSET_Y);
move_point_snapshot(10, 20);
move_point_snapshot(5, -10);
double x2 = get_current_x();
double y2 = get_current_y();
TEST_ASSERT_DOUBLE_EQ(OFFSET_X + 15, x2, 0.001);
TEST_ASSERT_DOUBLE_EQ(OFFSET_Y + 10, y2, 0.001);
set_coordinates_snapshot(OFFSET_X, OFFSET_Y);
double old_x = get_current_x();
double old_y = get_current_y();
BridgeSnapshot snapshot = generate_random_snapshot();
if (snapshot.err_hand == OK) {
    TEST_ASSERT(fabs(get_current_x() - old_x) > 0.001 ||
        fabs(get_current_y() - old_y) > 0.001);
}
