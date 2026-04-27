double result = calibrer(MIN_RAW);
TEST_ASSERT_DOUBLE_EQ(MIN_ANGLE, result, 0.001); 
double result = calibrer(MAX_RAW);
TEST_ASSERT_DOUBLE_EQ(MAX_ANGLE, result, 0.001);