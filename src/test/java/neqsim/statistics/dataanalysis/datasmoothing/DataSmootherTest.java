package neqsim.statistics.dataanalysis.datasmoothing;

import org.junit.jupiter.api.Test;
import Jama.Matrix;

public class DataSmootherTest {

  @Test
  void testgetSmoothedNumbers(String[] args) {
    double[] numbers = {10, 11, 12, 13, 14, 15, 15.5, 15, 19, 14, 14, 13, 12, 12, 11, 10, 9, 8};
    DataSmoother test = new DataSmoother(numbers, 3, 3, 0, 4);
    Matrix data = new Matrix(test.getSmoothedNumbers(), 1);
    data.print(10, 2);
    test.runSmoothing();
    data = new Matrix(test.getSmoothedNumbers(), 1);
    data.print(10, 2);
  }
}
