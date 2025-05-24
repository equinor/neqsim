package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class SafeSplineSurgeCurveTest {

  @Test
  public void testSurgeCurve10250() {
    double[] flow10250 = {9758.49, 9578.11, 9397.9, 9248.64, 9006.93, 8749.97, 8508.5, 8179.81,
        7799.81, 7111.75, 6480.26, 6007.91, 5607.45};

    double[] head10250 = {112.65, 121.13, 127.56, 132.13, 137.29, 140.73, 142.98, 144.76, 146.14,
        148.05, 148.83, 149.54, 150.0};

    // Initialize curve
    SafeSplineSurgeCurve curve = new SafeSplineSurgeCurve(flow10250, head10250);

    // Should be active
    assertTrue(curve.isActive());

    // Check interpolation within range
    double midFlow = 8000.0;
    double interpolatedHead = curve.getSurgeHead(midFlow);
    assertTrue(interpolatedHead > 0.0);

    // Check extrapolation below minimum
    double lowFlow = 5000.0;
    double extrapolatedHeadLow = curve.getSurgeHead(lowFlow);
    assertTrue(extrapolatedHeadLow >= 0.0);

    // Check extrapolation above maximum
    double highFlow = 10000.0;
    double extrapolatedHeadHigh = curve.getSurgeHead(highFlow);
    assertTrue(extrapolatedHeadHigh >= 0.0);

    // Sanity check: increasing flow should not increase head (head drops with more flow)
    assertTrue(curve.getSurgeHead(6000.0) > curve.getSurgeHead(9500.0));

    double surgeflow = curve.getSurgeFlow(130.0); // Test flow to head conversion
    assertEquals(9321.84315857, surgeflow, 1.0);

    surgeflow = curve.getSurgeFlow(190.0); // Test flow to head conversion
    assertEquals(0.0, surgeflow, 1.0);

    surgeflow = curve.getSurgeFlow(10.0); // Test flow to head conversion
    assertEquals(11941.98139, surgeflow, 1.0);

    surgeflow = curve.getSurgeFlow(130.0); // Test flow to head conversion
    assertEquals(9321.84315, surgeflow, 1.0);

    surgeflow = curve.getSurgeHead(9321.84315); // Test flow to head conversion
    assertEquals(130.0, surgeflow, 1.0);
  }
}
