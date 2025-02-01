package neqsim.process.mechanicaldesign.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PipeDesignTest {
  @Test
  void testErosionalVelocity() {

  }

  @Test
  void testGaugeFromThickness() {

  }

  @Test
  void testNearestPipe() {
    PipeDesign pipedesing = new PipeDesign();
    Double Do = Double.valueOf(0.25);
    Double Di = null;// 0.25 - 2 * 0.00635;
    Double thickness = null;// 0.00635;
    String schedule = "40"; // "40";
    Double NPS = null;// 0.5;
    double[] nearestPipe = pipedesing.nearestPipe(Do, Di, NPS, schedule);

    // NPS
    Assertions.assertEquals(nearestPipe[0], 10.0);

    // ID
    Assertions.assertEquals(nearestPipe[1], 0.25446, 0.00001);

    // OD
    Assertions.assertEquals(nearestPipe[2], 0.273, 0.00001);

    // wall thickness
    Assertions.assertEquals(nearestPipe[3], 0.009269999999999, 0.0000001);

  }

  @Test
  void testNearestPipe2() {
    PipeDesign pipedesing = new PipeDesign();
    Double Do = Double.valueOf(0.5);
    Double Di = null;
    Double thickness = null;
    String schedule = "40";
    Double NPS = null;
    double[] nearestPipe = pipedesing.nearestPipe(Do, Di, NPS, schedule);

    // NPS
    Assertions.assertEquals(nearestPipe[0], 20.0);

    // ID
    Assertions.assertEquals(nearestPipe[1], 0.4778199999999, 0.00001);

    // OD
    Assertions.assertEquals(nearestPipe[2], 0.508, 0.00001);

    // wall thickness
    Assertions.assertEquals(nearestPipe[3], 0.01509, 0.0000001);

  }

  @Test
  void testThicknessFromGauge() {

  }
}
