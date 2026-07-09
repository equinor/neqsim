package neqsim.process.equipment.separator;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.OilLevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.WaterLevelTransmitter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Tests for {@link ThreePhaseGasScrubber} and vertical-orientation behaviour of {@link ThreePhaseSeparator}, covering
 * steady-state three-phase separation and dynamic control of both liquid levels with independent oil and aqueous outlet
 * valves.
 *
 * @author ESOL
 */
@Tag("slow")
class ThreePhaseGasScrubberTest {

  /**
   * Builds a gas + oil + water three-phase fluid.
   *
   * @return a three-phase thermodynamic system
   */
  private static SystemInterface makeThreePhaseFluid() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 50.0, 15.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addComponent("n-heptane", 15.0);
    fluid.addComponent("water", 50.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Builds a vertical three-phase scrubber (D = 1.5 m, L = 4.0 m) on a 5000 kg/hr feed and runs the initial
   * steady-state solution.
   *
   * @param name equipment name
   * @return a solved {@link ThreePhaseGasScrubber}
   */
  private static ThreePhaseGasScrubber makeScrubber(String name) {
    Stream feed = new Stream("feed", makeThreePhaseFluid());
    feed.setTemperature(50.0, "C");
    feed.setPressure(15.0, "bara");
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    ThreePhaseGasScrubber scrubber = new ThreePhaseGasScrubber(name, feed);
    scrubber.setInternalDiameter(1.5);
    scrubber.setSeparatorLength(4.0);
    scrubber.setLiquidLevel(0.25); // start at 25 % (1.0 m) to leave head- and drain-room
    scrubber.run();
    return scrubber;
  }

  /**
   * Advances a scrubber dynamically for a number of fixed-size steps with constant valve positions, asserting physical
   * bounds each step, then returns the final [waterLevel, oilLevel].
   *
   * @param scrubber the scrubber to advance
   * @param waterFraction aqueous outlet valve position (0..1)
   * @param oilFraction oil outlet valve position (0..1)
   * @param steps number of transient steps
   * @param dt time step [s]
   * @return two-element array {waterLevel, oilLevel} in metres
   */
  private static double[] advance(ThreePhaseGasScrubber scrubber, double waterFraction, double oilFraction, int steps,
      double dt) {
    scrubber.setCalculateSteadyState(false);
    scrubber.setWaterOutletFlowFraction(waterFraction);
    scrubber.setOilOutletFlowFraction(oilFraction);
    scrubber.setGasOutletFlowFraction(1.0);
    UUID id = UUID.randomUUID();
    for (int i = 0; i < steps; i++) {
      scrubber.runTransient(dt, id);
      Assertions.assertTrue(Double.isFinite(scrubber.getWaterLevel()), "water level finite at step " + i);
      Assertions.assertTrue(Double.isFinite(scrubber.getOilLevel()), "oil level finite at step " + i);
      Assertions.assertTrue(scrubber.getWaterLevel() >= 0.0, "water level non-negative at step " + i);
      Assertions.assertTrue(scrubber.getOilLevel() >= scrubber.getWaterLevel() - 1e-6,
          "oil level >= water level at step " + i);
      Assertions.assertTrue(scrubber.getOilLevel() <= scrubber.getMaxLiquidHeight() + 1e-6,
          "oil level within vessel at step " + i);
    }
    return new double[] { scrubber.getWaterLevel(), scrubber.getOilLevel() };
  }

  /**
   * A three-phase gas scrubber must default to vertical orientation and its maximum liquid height must equal the
   * tan-tan length (not the diameter).
   */
  @Test
  void testDefaultsToVerticalOrientation() {
    ThreePhaseGasScrubber scrubber = makeScrubber("KO drum");
    Assertions.assertEquals("vertical", scrubber.getOrientation());
    Assertions.assertEquals(4.0, scrubber.getMaxLiquidHeight(), 1e-9);
  }

  /**
   * Steady-state run of a vertical three-phase scrubber must produce three non-trivial product streams (gas, oil,
   * water) and close the overall mass balance.
   */
  @Test
  void testSteadyStateThreeProducts() {
    ThreePhaseGasScrubber scrubber = makeScrubber("KO drum");

    double gas = scrubber.getGasOutStream().getFlowRate("kg/hr");
    double oil = scrubber.getOilOutStream().getFlowRate("kg/hr");
    double water = scrubber.getWaterOutStream().getFlowRate("kg/hr");

    Assertions.assertTrue(gas > 1.0, "gas product should be non-trivial");
    Assertions.assertTrue(oil > 1.0, "oil product should be non-trivial");
    Assertions.assertTrue(water > 1.0, "water product should be non-trivial");

    // Overall mass balance (out - in) should be ~0.
    Assertions.assertEquals(0.0, scrubber.getMassBalance("kg/hr"), 1.0);
  }

  /**
   * A plain {@link ThreePhaseSeparator} switched to vertical orientation must produce the same three products and a
   * finite water/oil level after a transient step.
   */
  @Test
  void testThreePhaseSeparatorVerticalOrientation() {
    Stream feed = new Stream("feed", makeThreePhaseFluid());
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("V-100", feed);
    sep.setInternalDiameter(1.5);
    sep.setSeparatorLength(4.0);
    sep.setOrientation("vertical");
    sep.run();

    Assertions.assertTrue(sep.getGasOutStream().getFlowRate("kg/hr") > 1.0);
    Assertions.assertTrue(sep.getOilOutStream().getFlowRate("kg/hr") > 1.0);
    Assertions.assertTrue(sep.getWaterOutStream().getFlowRate("kg/hr") > 1.0);

    // A single transient step should populate finite, ordered liquid levels.
    sep.setCalculateSteadyState(false);
    sep.runTransient(10.0, UUID.randomUUID());
    Assertions.assertTrue(Double.isFinite(sep.getWaterLevel()));
    Assertions.assertTrue(Double.isFinite(sep.getOilLevel()));
    Assertions.assertTrue(sep.getWaterLevel() >= 0.0);
    Assertions.assertTrue(sep.getOilLevel() >= sep.getWaterLevel() - 1e-9,
        "oil level (water+oil) must be >= water level");
  }

  /**
   * Closing the aqueous outlet valve must make the water level accumulate above the level reached with the valve fully
   * open — i.e. the aqueous outlet valve regulates the water level.
   */
  @Test
  void testAqueousOutletValveRaisesWaterLevel() {
    double[] open = advance(makeScrubber("open"), 1.0, 1.0, 40, 10.0);
    double[] throttled = advance(makeScrubber("throttled"), 0.0, 1.0, 40, 10.0);

    Assertions.assertTrue(throttled[0] > open[0] + 1e-3,
        "closing the water valve should raise the water level (throttled=" + throttled[0] + " m, open=" + open[0]
            + " m)");
  }

  /**
   * Closing the oil outlet valve must make the total liquid (oil) level accumulate above the level reached with the
   * valve fully open — i.e. the oil outlet valve regulates the total liquid level.
   */
  @Test
  void testOilOutletValveRaisesTotalLevel() {
    double[] open = advance(makeScrubber("open"), 1.0, 1.0, 40, 10.0);
    double[] throttled = advance(makeScrubber("throttled"), 1.0, 0.0, 40, 10.0);

    Assertions.assertTrue(throttled[1] > open[1] + 1e-3,
        "closing the oil valve should raise the total liquid level (throttled=" + throttled[1] + " m, open=" + open[1]
            + " m)");
  }

  /**
   * Closed-loop dual-level control: two independent reverse-acting PI loops driving the aqueous and oil outlet valves
   * from the water- and oil-level transmitters must keep both liquid levels finite, ordered and bounded inside the
   * vertical vessel throughout a feed disturbance, and hold them in a stable band around their setpoints.
   */
  @Test
  void testDynamicDualLevelControlStable() {
    ThreePhaseGasScrubber scrubber = makeScrubber("KO drum");

    WaterLevelTransmitter waterLT = new WaterLevelTransmitter("LT-water", scrubber);
    OilLevelTransmitter oilLT = new OilLevelTransmitter("LT-oil", scrubber);

    // Transmitter ranges must reflect the vertical height, not the diameter.
    Assertions.assertEquals(4.0, waterLT.getMaximumValue(), 1e-9);
    Assertions.assertEquals(4.0, oilLT.getMaximumValue(), 1e-9);

    scrubber.setCalculateSteadyState(false);

    final double waterLevelSP = 0.6; // m
    final double oilLevelSP = 1.4; // m (total liquid level, above the 1.0 m start so it can accumulate)
    double kp = 60.0;
    double ti = 300.0;
    double waterIntegral = 0.0;
    double oilIntegral = 0.0;

    double dt = 10.0;
    int steps = 100;
    UUID id = UUID.randomUUID();

    for (int i = 0; i < steps; i++) {
      double waterErr = waterLevelSP - waterLT.getMeasuredValue("m");
      double oilErr = oilLevelSP - oilLT.getMeasuredValue("m");
      waterIntegral += waterErr * dt;
      oilIntegral += oilErr * dt;

      // Reverse acting: level above setpoint opens the outlet valve.
      double waterOpen = 100.0 - (kp * waterErr + kp / ti * waterIntegral);
      double oilOpen = 100.0 - (kp * oilErr + kp / ti * oilIntegral);
      waterOpen = Math.max(0.0, Math.min(100.0, waterOpen));
      oilOpen = Math.max(0.0, Math.min(100.0, oilOpen));

      scrubber.setWaterOutletFlowFraction(waterOpen / 100.0);
      scrubber.setOilOutletFlowFraction(oilOpen / 100.0);

      if (i == 40) {
        scrubber.getFeedStream().setFlowRate(6000.0, "kg/hr");
        scrubber.getFeedStream().run();
      }

      scrubber.runTransient(dt, id);

      Assertions.assertTrue(Double.isFinite(scrubber.getWaterLevel()), "water level finite at step " + i);
      Assertions.assertTrue(Double.isFinite(scrubber.getOilLevel()), "oil level finite at step " + i);
      Assertions.assertTrue(scrubber.getWaterLevel() >= 0.0, "water level non-negative at step " + i);
      Assertions.assertTrue(scrubber.getOilLevel() >= scrubber.getWaterLevel() - 1e-6,
          "oil level >= water level at step " + i);
      Assertions.assertTrue(scrubber.getOilLevel() <= scrubber.getMaxLiquidHeight() + 1e-6,
          "oil level within vessel at step " + i);
    }

    // Both loops should hold the levels in a stable band around their setpoints.
    Assertions.assertEquals(waterLevelSP, scrubber.getWaterLevel(), 0.5, "water level controlled near setpoint");
    Assertions.assertEquals(oilLevelSP, scrubber.getOilLevel(), 0.6, "oil level controlled near setpoint");
  }

  /**
   * Sizes the three outlet control valves with margin: the nominal (100 %-open) outlet flow of each product stream is
   * scaled up by {@code factor} so that the steady-state operating point sits near {@code 1/factor} valve opening. This
   * gives every controller headroom to open <i>above</i> the design flow and therefore reject inlet disturbances that
   * increase throughput (a valve limited to the exact design flow could only reject turndown).
   *
   * @param scrubber the scrubber whose outlet streams are scaled
   * @param factor valve sizing margin (e.g. 2.0 for a design point at 50 % open)
   */
  private static void sizeControlValves(ThreePhaseGasScrubber scrubber, double factor) {
    scaleNominalFlow(scrubber.getGasOutStream(), factor);
    scaleNominalFlow(scrubber.getOilOutStream(), factor);
    scaleNominalFlow(scrubber.getWaterOutStream(), factor);
  }

  /**
   * Scales the nominal flow of an outlet stream by a factor (used to give a control valve sizing margin).
   *
   * @param stream the outlet stream
   * @param factor multiplier
   */
  private static void scaleNominalFlow(neqsim.process.equipment.stream.StreamInterface stream, double factor) {
    double flow = stream.getFlowRate("kg/hr");
    if (flow > 1.0e-9) {
      stream.setFlowRate(flow * factor, "kg/hr");
      stream.run();
    }
  }

  /**
   * Full three-element dynamic control of a vertical {@link ThreePhaseGasScrubber}: two liquid-level loops (water and
   * oil outlet valves) plus a vessel-pressure loop (gas outlet valve). Levels are measured with dedicated level
   * transmitters and pressure with a {@link PressureTransmitter}; each valve is driven by a direct-acting PI controller
   * with anti-windup. A +30 % inlet surge followed by a turndown to 80 % is injected, and the controllers must reject
   * both disturbances — returning both liquid levels and the pressure to their setpoints — while keeping the vessel
   * state finite, ordered and bounded throughout.
   */
  @Test
  void testInletDisturbanceRejection() {
    SystemInterface fluid = makeThreePhaseFluid();
    Stream feed = new Stream("feed", fluid);
    feed.setTemperature(50.0, "C");
    feed.setPressure(15.0, "bara");
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    ThreePhaseGasScrubber scrubber = new ThreePhaseGasScrubber("KO drum", feed);
    scrubber.setInternalDiameter(1.5);
    scrubber.setSeparatorLength(4.0);
    scrubber.setLiquidLevel(0.2); // start at 20 % (0.8 m)
    scrubber.run();

    // Size the three outlet control valves with 2x margin -> design point near 50 % open.
    sizeControlValves(scrubber, 2.0);

    WaterLevelTransmitter waterLT = new WaterLevelTransmitter("LT-water", scrubber);
    OilLevelTransmitter oilLT = new OilLevelTransmitter("LT-oil", scrubber);
    PressureTransmitter pt = new PressureTransmitter("PT-gas", scrubber.getGasOutStream());
    pt.setUnit("bara");

    final double waterSP = 0.40; // m
    final double oilSP = 0.90; // m (total liquid level)
    final double pressureSP = 15.0; // bara

    // Direct-acting PI controllers (valve opens when its measurement is above setpoint).
    final double bias = 50.0; // % opening at the design operating point
    final double levelKp = 120.0;
    final double levelTi = 200.0;
    final double presKp = 10.0;
    final double presTi = 100.0;
    double waterI = 0.0;
    double oilI = 0.0;
    double presI = 0.0;

    scrubber.setCalculateSteadyState(false);

    double dt = 3.0;
    int steps = 420;
    int surgeStep = 150; // +30 % inlet surge
    int turndownStep = 290; // turndown to 80 %
    UUID id = UUID.randomUUID();

    for (int i = 0; i < steps; i++) {
      if (i == surgeStep) {
        feed.setFlowRate(6500.0, "kg/hr");
        feed.run();
      }
      if (i == turndownStep) {
        feed.setFlowRate(4000.0, "kg/hr");
        feed.run();
      }

      // Step the three controllers (seed at 50 % on the first step, then use previous output).
      double waterErr = waterLT.getMeasuredValue("m") - waterSP;
      double oilErr = oilLT.getMeasuredValue("m") - oilSP;
      double presErr = pt.getMeasuredValue("bara") - pressureSP;

      // PI with conditional anti-windup: undo the integral step if the output saturates.
      waterI += waterErr * dt;
      double waterRaw = bias + levelKp * waterErr + levelKp / levelTi * waterI;
      double waterOpen = Math.max(0.0, Math.min(100.0, waterRaw));
      if (waterOpen != waterRaw) {
        waterI -= waterErr * dt;
      }

      oilI += oilErr * dt;
      double oilRaw = bias + levelKp * oilErr + levelKp / levelTi * oilI;
      double oilOpen = Math.max(0.0, Math.min(100.0, oilRaw));
      if (oilOpen != oilRaw) {
        oilI -= oilErr * dt;
      }

      presI += presErr * dt;
      double presRaw = bias + presKp * presErr + presKp / presTi * presI;
      double presOpen = Math.max(0.0, Math.min(100.0, presRaw));
      if (presOpen != presRaw) {
        presI -= presErr * dt;
      }

      scrubber.setWaterOutletFlowFraction(waterOpen / 100.0);
      scrubber.setOilOutletFlowFraction(oilOpen / 100.0);
      scrubber.setGasOutletFlowFraction(presOpen / 100.0);

      scrubber.runTransient(dt, id);

      Assertions.assertTrue(Double.isFinite(scrubber.getWaterLevel()), "water level finite at step " + i);
      Assertions.assertTrue(Double.isFinite(scrubber.getOilLevel()), "oil level finite at step " + i);
      Assertions.assertTrue(Double.isFinite(scrubber.getThermoSystem().getPressure("bara")),
          "pressure finite at step " + i);
      Assertions.assertTrue(scrubber.getWaterLevel() >= 0.0, "water level non-negative at step " + i);
      Assertions.assertTrue(scrubber.getOilLevel() >= scrubber.getWaterLevel() - 1e-6,
          "oil level >= water level at step " + i);
      Assertions.assertTrue(scrubber.getOilLevel() <= scrubber.getMaxLiquidHeight() + 1e-6,
          "oil level within vessel at step " + i);
      Assertions.assertTrue(scrubber.getThermoSystem().getPressure("bara") > 0.0, "pressure positive at step " + i);
    }

    // After both disturbances, all three loops must have returned close to their setpoints.
    Assertions.assertEquals(waterSP, scrubber.getWaterLevel(), 0.15, "water level returned to setpoint");
    Assertions.assertEquals(oilSP, scrubber.getOilLevel(), 0.2, "oil level returned to setpoint");
    Assertions.assertEquals(pressureSP, scrubber.getThermoSystem().getPressure("bara"), 1.5,
        "pressure returned to setpoint");
  }
}
