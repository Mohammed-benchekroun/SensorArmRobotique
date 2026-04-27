double result = calibrer(MIN_RAW);
TEST_ASSERT_DOUBLE_EQ(MIN_ANGLE, result, 0.001); 
double result = calibrer(MAX_RAW);
TEST_ASSERT_DOUBLE_EQ(MAX_ANGLE, result, 0.001);
int mid_raw = (MIN_RAW + MAX_RAW) / 2;
double result = calibrer(mid_raw);
TEST_ASSERT_DOUBLE_EQ(0.0, result, 0.5);