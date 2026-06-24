package neqsim.process.controllerdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the dynamic anti-surge controller (P3).
 */
public class AntiSurgeControllerTest {

  /**
   * Build a simple gas stream for wiring test equipment.
   *
   * @return a runnable gas stream
   */
  private Stream gasStream() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule(2);
    Stream s = new Stream("gas", gas);
    s.setFlowRate(100000.0, "kg/hr");
    s.setTemperature(30.0, "C");
    s.setPressure(50.0, "bara");
    s.run();
    return s;
  }

  @Test
  void testRecycleValveOpensWhenMarginLow() {
    Stream feed = gasStream();
    // stub compressor exposing a controllable surge margin
    final double[] marginHolder = new double[] { 0.02 };
    Compressor comp = new Compressor("comp", feed) {
      private static final long serialVersionUID = 1L;

      @Override
      public double getDistanceToSurge() {
        return marginHolder[0];
      }
    };
    ThrottlingValve recycle = new ThrottlingValve("recycle", feed);
    recycle.setPercentValveOpening(0.0);

    AntiSurgeController controller = new AntiSurgeController("ASC", comp, recycle);
    controller.setSurgeMarginSetPoint(0.10);
    controller.setProportionalGain(300.0);
    controller.setIntegralTime(10.0);

    // margin (0.02) is below the set point (0.10): valve should open over time
    for (int i = 0; i < 20; i++) {
      controller.runTransient(0.0, 1.0);
    }
    double openLow = controller.getValveOpening();
    Assertions.assertTrue(openLow > 5.0, "Recycle valve should open when surge margin is low");
    Assertions.assertEquals(openLow, recycle.getPercentValveOpening(), 1e-9);

    // restore a healthy margin: controller should close the valve back down
    controller.reset();
    marginHolder[0] = 0.30;
    for (int i = 0; i < 20; i++) {
      controller.runTransient(0.0, 1.0);
    }
    double openHigh = controller.getValveOpening();
    Assertions.assertEquals(0.0, openHigh, 1e-6, "Recycle valve should stay closed when margin is healthy");
  }

  @Test
  void testClampAndNoCompressorHold() {
    Stream feed = gasStream();
    ThrottlingValve recycle = new ThrottlingValve("recycle", feed);
    AntiSurgeController controller = new AntiSurgeController("ASC");
    controller.setRecycleValve(recycle);
    controller.setOpeningRange(0.0, 60.0);
    // no compressor configured: output held, no exception
    controller.runTransient(0.0, 1.0);
    Assertions.assertTrue(controller.getValveOpening() <= 60.0);

    final double[] marginHolder = new double[] { -0.5 };
    Compressor comp = new Compressor("comp", feed) {
      private static final long serialVersionUID = 1L;

      @Override
      public double getDistanceToSurge() {
        return marginHolder[0];
      }
    };
    controller.setCompressor(comp);
    controller.setProportionalGain(1000.0);
    for (int i = 0; i < 30; i++) {
      controller.runTransient(0.0, 1.0);
    }
    // deeply in surge: output saturates at the configured maximum
    Assertions.assertEquals(60.0, controller.getValveOpening(), 1e-6);
  }
}
