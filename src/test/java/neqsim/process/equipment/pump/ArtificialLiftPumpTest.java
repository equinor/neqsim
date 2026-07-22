package neqsim.process.equipment.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for the artificial-lift pumps {@link JetPump} (hydraulic jet pump) and {@link SuckerRodPump} (beam pump).
 *
 * @author NeqSim
 * @version 1.0
 */
class ArtificialLiftPumpTest {

  /**
   * Builds a simple single-phase liquid feed stream at the given pressure.
   *
   * @param pressureBara stream pressure in bara
   * @return a run liquid stream
   */
  private Stream liquidFeed(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(323.15, pressureBara);
    fluid.addComponent("n-heptane", 1.0);
    fluid.setMixingRule("classic");
    Stream s = new Stream("feed", fluid);
    s.setFlowRate(5000.0, "kg/hr");
    s.setTemperature(50.0, "C");
    s.setPressure(pressureBara, "bara");
    s.run();
    return s;
  }

  /**
   * A jet pump must boost the suction pressure to a discharge pressure that lies between the suction and power-fluid
   * pressures, and report a non-negative head ratio and efficiency.
   */
  @Test
  void testJetPumpDischargeBetweenSuctionAndPower() {
    Stream feed = liquidFeed(50.0);
    JetPump pump = new JetPump("JP-1", feed);
    pump.setAreaRatio(0.25);
    pump.setPowerFluidPressure(220.0);
    pump.setOperatingFlowRatio(0.8);
    pump.run(null);
    double pd = pump.getDischargePressure();
    assertTrue(pd >= 50.0, "discharge must be at least suction pressure");
    assertTrue(pd <= 220.0, "discharge must not exceed power-fluid pressure");
    assertTrue(pump.getHeadRatio() >= 0.0, "head ratio must be non-negative");
    assertTrue(pump.getEfficiency() >= 0.0, "efficiency must be non-negative");
    assertEquals(pd, pump.getOutletStream().getPressure("bara"), 1.0e-6);
  }

  /**
   * The jet-pump head ratio must fall as the operating flow ratio rises (more entrained flow per unit power flow yields
   * a smaller pressure lift).
   */
  @Test
  void testJetPumpHeadRatioDecreasesWithFlowRatio() {
    JetPump pump = new JetPump("JP-2");
    pump.setAreaRatio(0.30);
    double hLow = pump.headRatioAt(0.5);
    double hHigh = pump.headRatioAt(2.0);
    assertTrue(hLow > hHigh, "head ratio must decrease with flow ratio");
  }

  /**
   * A sucker-rod pump's actual displacement must equal theoretical displacement scaled by the volumetric efficiency,
   * and scale linearly with strokes per minute.
   */
  @Test
  void testSuckerRodPumpDisplacement() {
    SuckerRodPump pump = new SuckerRodPump("SR-1");
    pump.setPlungerDiameter(0.0381);
    pump.setStrokeLength(1.5);
    pump.setStrokesPerMinute(8.0);
    pump.setVolumetricEfficiency(0.80);
    double theo = pump.getTheoreticalDisplacement("m3/day");
    double actual = pump.getActualDisplacement("m3/day");
    assertTrue(theo > 0.0);
    assertEquals(theo * 0.80, actual, 1.0e-9);

    pump.setStrokesPerMinute(16.0);
    assertEquals(2.0 * theo, pump.getTheoreticalDisplacement("m3/day"), 1.0e-6);
  }

  /**
   * Running a sucker-rod pump must compute a positive polished-rod load and boost the stream to the configured
   * discharge pressure.
   */
  @Test
  void testSuckerRodPumpRun() {
    Stream feed = liquidFeed(20.0);
    SuckerRodPump pump = new SuckerRodPump("SR-2", feed);
    pump.setPumpDepth(1500.0);
    pump.setFluidDensity(900.0);
    pump.setDischargePressure(60.0);
    pump.run(null);
    assertTrue(pump.getPolishedRodLoad() > 0.0, "polished-rod load must be positive");
    assertEquals(60.0, pump.getOutletStream().getPressure("bara"), 1.0e-6);
  }
}
