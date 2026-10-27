package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineCatalog;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineUnit;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the common-shaft extensions of {@link CompressorShaft}: gear ratios, idle members, ambient site-rated
 * driver, control targets, operating report, driver-limited speed and throughput.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorShaftGroupTest {

  private Stream feed;
  private Compressor c1;
  private Compressor c2;
  private double design;

  /**
   * Build a run gas stream.
   *
   * @param name stream name
   * @param pressure pressure in bara
   * @return run stream
   */
  private static Stream gas(String name, double pressure) {
    SystemSrkEos g = new SystemSrkEos(273.15 + 30.0, pressure);
    g.addComponent("methane", 0.90);
    g.addComponent("ethane", 0.10);
    g.setMixingRule("classic");
    Stream s = new Stream(name, g);
    s.setFlowRate(20000.0, "kg/hr");
    s.setTemperature(30.0, "C");
    s.setPressure(pressure, "bara");
    s.run();
    return s;
  }

  /**
   * Charted two-body train: body 1 from 10 to 20 bara, body 2 from 20 to 40 bara, both with generated charts.
   */
  private void buildTrain() {
    feed = gas("feed", 10.0);
    c1 = new Compressor("stage 3", feed);
    c1.setOutletPressure(20.0);
    c1.setUsePolytropicCalc(true);
    c1.setPolytropicEfficiency(0.78);
    c1.run();
    c1.generateCompressorChart("normal curves", 5);
    c1.getCompressorChart().setUseCompressorChart(true);
    c2 = new Compressor("stage 4", c1.getOutletStream());
    c2.setOutletPressure(40.0);
    c2.setUsePolytropicCalc(true);
    c2.setPolytropicEfficiency(0.78);
    c2.run();
    c2.generateCompressorChart("normal curves", 5);
    c2.getCompressorChart().setUseCompressorChart(true);
    design = c1.getSpeed();
  }

  /**
   * Build a shaft with both bodies and speed bounds around the design speed.
   *
   * @return shaft
   */
  private CompressorShaft shaft() {
    CompressorShaft s = new CompressorShaft("HT shaft");
    s.addCompressor(c1);
    s.addCompressor(c2);
    s.setSpeedBounds(design * 0.92, design * 1.08);
    s.setPressureTolerance(0.05);
    s.setMaxIterations(40);
    return s;
  }

  /**
   * A gear ratio sets the member speed to ratio times the common speed.
   */
  @Test
  void gearRatioScalesMemberSpeed() {
    buildTrain();
    CompressorShaft s = new CompressorShaft("geared");
    s.addCompressor(c1);
    s.addCompressor(c2, 1.25);
    s.setSpeed(design);
    assertEquals(design, c1.getSpeed(), 1e-6);
    assertEquals(1.25 * design, c2.getSpeed(), 1e-6);
    assertEquals(1.25, s.getGearRatio(c2), 0.0);
    assertEquals(1.25 * design, s.getMemberSpeed(c2), 1e-6);
    assertThrows(IllegalArgumentException.class, () -> s.setGearRatio(c1, 0.0));
    assertThrows(IllegalArgumentException.class, () -> s.setGearRatio(new Compressor("x"), 1.0));
  }

  /**
   * An idle member leaves the power sum and the report flags it; a shaft with no active member is idle.
   */
  @Test
  void idleMemberAndIdleShaft() {
    buildTrain();
    CompressorShaft s = shaft();
    s.runAtFixedSpeed(design, null);
    double both = s.getTotalPower();
    assertEquals(c1.getPower() + c2.getPower(), both, 1.0);
    s.setMemberActive(c2, false);
    assertFalse(s.isMemberActive(c2));
    assertTrue(c2.isLockedInactive());
    assertEquals(c1.getPower(), s.getTotalPower(), 1.0);
    assertEquals(1, s.getActiveCompressors().size());
    CompressorShaftReport rep = s.getOperatingReport();
    assertTrue(rep.getMember("stage 4").getFlags().contains("idle"));
    assertEquals(0.0, rep.getMember("stage 4").getPowerW(), 0.0);
    s.setMemberActive(c1, false);
    assertFalse(s.isTurning());
    assertEquals(0.0, s.solveSpeed(c2, 40.0, "bara", null), 0.0);
    assertEquals(CompressorShaft.SolveStatus.SHAFT_IDLE, s.getLastSolveResult().getStatus());
    assertTrue(s.isFeasible());
    s.setMemberActive(c1, true);
    s.setMemberActive(c2, true);
    assertFalse(c2.isLockedInactive());
    assertTrue(s.isTurning());
  }

  /**
   * The controlling member sets the speed from its suction pressure; the other member follows.
   */
  @Test
  void suctionPressureTargetSetsSpeed() {
    buildTrain();
    CompressorShaft s = shaft();
    s.runAtFixedSpeed(design, null);
    double p0 = c2.getInletStream().getPressure("bara");
    double target = p0 + 0.4;
    s.solveSpeedToTarget(c2, CompressorShaft.ControlTarget.SUCTION_PRESSURE, target, "bara", null);
    assertEquals(target, c2.getInletStream().getPressure("bara"), 0.1);
    assertTrue(s.getSpeed() > design);
    assertEquals(s.getSpeed(), c1.getSpeed(), 1e-6);
  }

  /**
   * A target outside the range reachable with the speed bounds is flagged and saturates.
   */
  @Test
  void flowTargetOutOfRange() {
    buildTrain();
    CompressorShaft s = shaft();
    s.runAtFixedSpeed(design, null);
    double q0 = c2.getInletStream().getFlowRate("m3/hr");
    s.solveSpeedToTarget(c2, CompressorShaft.ControlTarget.INLET_FLOW, q0 * 3.0, "m3/hr", null);
    assertEquals(CompressorShaft.SolveStatus.TARGET_OUT_OF_RANGE, s.getLastSolveResult().getStatus());
    assertFalse(s.isFeasible());
  }

  /**
   * The operating report lists the members with margins and serialises to JSON.
   */
  @Test
  void operatingReportShowsMarginsAndJson() {
    buildTrain();
    CompressorShaft s = shaft();
    s.setMemberRequiredDischargePressure(c2, 50.0, "bara");
    s.setMemberRequiredDischargePressure(c1, 10.0, "bara");
    s.setMaxShaftPower(1.0, "MW");
    s.runAtFixedSpeed(design, null);
    CompressorShaftReport rep = s.getOperatingReport();
    assertEquals(2, rep.getMembers().size());
    CompressorShaftReport.MemberReport m2 = rep.getMember("stage 4");
    assertNotNull(m2);
    assertTrue(m2.getPowerW() > 0.0);
    assertTrue(m2.getDischargeMarginBar() < 0.0);
    assertTrue(m2.getFlags().contains("speed too low for the discharge need"));
    assertTrue(rep.getMember("stage 3").getFlags().contains("excess head (throttle or recycle)"));
    assertEquals(c1.getPower() + c2.getPower(), rep.getTotalPowerW(), 1.0);
    assertEquals(rep.getRequiredDriverPowerW() / 1.0e6, rep.getPowerUtilisation(), 1.0e-9);
    assertEquals(rep.isOverPower(), rep.getPowerUtilisation() > 1.0);
    String json = rep.toJson();
    assertTrue(json.contains("\"members\":[") && json.contains("\"stage 4\""));
  }

  /**
   * A site-rated gas turbine unit limits the shaft by ambient temperature and reports fuel after the solve.
   */
  @Test
  void gasTurbineUnitDriverFollowsAmbient() {
    buildTrain();
    CompressorShaft s = shaft();
    Stream fuel = gas("fuel", 30.0);
    fuel.setFlowRate(10.0, "kg/hr");
    fuel.run();
    GasTurbineUnit gt = new GasTurbineUnit("KA GT", fuel, GasTurbineCatalog.get("LM2500"));
    s.setGasTurbineUnitDriver(gt);
    s.setAmbientTemperature(5.0, "C");
    double cold = s.getAvailableShaftPower("W");
    s.setAmbientTemperature(30.0, "C");
    double hot = s.getAvailableShaftPower("W");
    assertTrue(cold > hot && hot > 0.0);
    assertEquals(gt.getAvailablePowerAtAmbientC(30.0), hot, 1.0);
    assertEquals(30.0, s.getAmbientTemperature("C"), 1e-9);
    s.runAtFixedSpeed(design, null);
    s.updateDriverOperatingPoint();
    CompressorShaftReport rep = s.getOperatingReport();
    assertTrue(rep.getDriverFuelMassFlowKgPerHr() > 0.0);
    assertTrue(rep.getAvailablePowerW() == hot);
    assertThrows(IllegalArgumentException.class, () -> s.setAmbientTemperature(1.0, "F"));
  }

  /**
   * An electric supply limit caps the shaft power and combines with other limits by the minimum.
   */
  @Test
  void electricSupplyLimitCapsShaft() {
    buildTrain();
    CompressorShaft s = shaft();
    s.setElectricSupplyLimit(5.0, "MW", 0.95);
    assertEquals(4.75, s.getAvailableShaftPower("MW"), 1e-9);
    s.setMaxShaftPower(3.0, "MW");
    assertEquals(3.0, s.getAvailableShaftPower("MW"), 1e-9);
    s.setMaxShaftPower(9.0, "MW");
    assertEquals(4.75, s.getAvailableShaftPower("MW"), 1e-9);
    assertThrows(IllegalArgumentException.class, () -> s.setElectricSupplyLimit(1.0, "MW", 1.5));
  }

  /**
   * The highest speed within the driver power is found where required power meets the available power.
   */
  @Test
  void maxSpeedWithinPowerLimit() {
    buildTrain();
    CompressorShaft s = shaft();
    s.runAtFixedSpeed(design, null);
    double atDesign = s.getTotalPower();
    s.setMaxShaftPower(0.85 * atDesign, "W");
    double n = s.findMaxSpeedWithinPowerLimit(null);
    assertTrue(n < design && n > 0.92 * design);
    assertEquals(0.85 * atDesign, s.getRequiredDriverPower("W"), 0.02 * atDesign);
    assertTrue(s.getPowerUtilization() <= 1.0 + 1e-6);
    s.setMaxShaftPower(5.0 * atDesign, "W");
    assertEquals(design * 1.08, s.findMaxSpeedWithinPowerLimit(null), 1e-6);
  }

  /**
   * Members that live in different ProcessSystems share one speed; the callback runs both systems in sequence.
   */
  @Test
  void membersInSeparateProcessSystems() {
    buildTrain();
    final Stream feedB = gas("feed B", 20.0);
    final Compressor stageB = new Compressor("stage 4 (other platform)", feedB);
    stageB.setOutletPressure(40.0);
    stageB.setUsePolytropicCalc(true);
    stageB.setPolytropicEfficiency(0.78);
    stageB.run();
    stageB.generateCompressorChart("normal curves", 5);
    stageB.getCompressorChart().setUseCompressorChart(true);
    final neqsim.process.processmodel.ProcessSystem platformA = new neqsim.process.processmodel.ProcessSystem();
    platformA.add(feed);
    platformA.add(c1);
    final neqsim.process.processmodel.ProcessSystem platformB = new neqsim.process.processmodel.ProcessSystem();
    platformB.add(feedB);
    platformB.add(stageB);
    Runnable both = new Runnable() {
      @Override
      public void run() {
        platformA.run();
        feedB.setThermoSystem(c1.getOutletStream().getThermoSystem().clone());
        platformB.run();
      }
    };
    CompressorShaft s = new CompressorShaft("OSA+OSD shaft");
    s.addCompressor(c1);
    s.addCompressor(stageB);
    s.setSpeedBounds(design * 0.92, design * 1.08);
    s.setPressureTolerance(0.05);
    s.runAtFixedSpeed(design, both);
    double target = stageB.getOutletStream().getPressure("bara") + 0.5;
    s.solveSpeed(stageB, target, "bara", both);
    assertEquals(target, stageB.getOutletStream().getPressure("bara"), 0.2);
    assertEquals(s.getSpeed(), c1.getSpeed(), 1e-6);
    assertEquals(s.getSpeed(), stageB.getSpeed(), 1e-6);
    assertTrue(s.getSpeed() > design);
  }

  /**
   * A stage can be decoupled from the shaft (own driver in a later year).
   */
  @Test
  void removeMemberDecouplesStage() {
    buildTrain();
    CompressorShaft s = shaft();
    s.setMemberActive(c1, false);
    s.removeCompressor(c1);
    assertFalse(c1.isLockedInactive());
    assertEquals(1, s.getCompressors().size());
    assertThrows(IllegalArgumentException.class, () -> s.setGearRatio(c1, 1.0));
    s.runAtFixedSpeed(design, null);
    assertEquals(c2.getPower(), s.getTotalPower(), 1.0);
  }

  /**
   * The throughput search finds the flow at which the driver power binds.
   */
  @Test
  void driverLimitedThroughput() {
    buildTrain();
    CompressorShaft s = shaft();
    s.runAtFixedSpeed(design, null);
    double p0 = c2.getOutletStream().getPressure("bara");
    double powerAtDesign = s.getTotalPower();
    s.setMaxShaftPower(1.1 * powerAtDesign, "W");
    DriverLimitedThroughputSolver solver = new DriverLimitedThroughputSolver(s, c2, p0, "bara", flow -> {
      feed.setFlowRate(flow, "kg/hr");
      feed.run();
    }, null);
    DriverLimitedThroughputSolver.Result r = solver.solve(18000.0, 24000.0);
    assertTrue(r.getMaxThroughput() >= 18000.0 && r.getMaxThroughput() <= 24000.0, r.toJson());
    assertTrue(r.getRequiredPowerW() <= 1.1 * powerAtDesign * 1.001, r.toJson());
    assertTrue(r.getEvaluations() > 2, r.toJson());
    assertTrue(r.getMaxThroughput() < 24000.0 && r.getBinding() != DriverLimitedThroughputSolver.Binding.NONE,
        r.toJson());
    assertNotNull(r.getBinding());
    assertTrue(r.toJson().contains("maxThroughput"));
  }
}
