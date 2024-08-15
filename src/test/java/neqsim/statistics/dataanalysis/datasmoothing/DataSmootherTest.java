package neqsim.statistics.dataanalysis.datasmoothing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DataSmootherTest {
  @Test
  void testgetSmoothedNumbers() {
    double[] numbers = {10, 11, 12, 13, 14, 15, 15.5, 15, 19, 14, 14, 13, 12, 12, 11, 10, 9, 8};
    DataSmoother test = new DataSmoother(numbers, 3, 3, 0, 4);
    Assertions.assertArrayEquals(numbers, test.getSmoothedNumbers());

    double[] expected = {10.000000, 11.000000, 12.000000, 12.567099567099579, 13.567099567099582,
        14.556277056277072, 15.305194805194823, 15.389610389610409, 16.66233766233768,
        17.149350649350666, 13.350649350649375, 13.627705627705646, 12.432900432900452,
        11.89177489177491, 11.541125541125552, 10.000000, 9.000000, 8.000000};
    test.runSmoothing();
    Assertions.assertArrayEquals(expected, test.getSmoothedNumbers());
  }
}
