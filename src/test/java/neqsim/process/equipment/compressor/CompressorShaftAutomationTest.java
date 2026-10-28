package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.automation.SimulationVariable;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;
import neqsim.process.equipment.energy.ElectricMotor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.CompressorShaftCalculator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the shaft recycle application, the VFD speed band and the automation addresses of a
 * {@link CompressorShaftCalculator}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorShaftAutomationTest {

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

  /** Charted two-body train: body 1 from 10 to 20 bara, body 2 from 20 to 40 bara. */
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
   * @return the shaft
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
   * The required recycle fraction goes into the built-in anti-surge and the recycle valve; repeated calls give the same
   * state, and a member that no longer needs recycle is released again.
   */
  @Test
  void applyRequiredRecycleIsIdempotentAndReleases() {
    buildTrain();
    CompressorShaft s = shaft();
    ThrottlingValve asv = new ThrottlingValve("ASV stage 3", c1.getOutletStream());
    s.setAntiSurgeValve(c1, asv);
    ThrottlingValve asv2 = new ThrottlingValve("ASV stage 4", c2.getOutletStream());
    s.setAntiSurgeController(c2, new AntiSurgeController("ASC stage 4", c2, asv2));
    c1.setSurgeControlMargin(2.0);
    s.runAtFixedSpeed(design, null);
    double need = s.getOperatingReport().getMember("stage 3").getRequiredRecycleFraction();
    assertTrue(need > 0.05 && need < 0.99, "need " + need);
    assertFalse(c1.getAntiSurge().isActive());

    Map<String, Double> first = s.applyRequiredRecycle();
    assertEquals(need, first.get("stage 3").doubleValue(), 1.0e-12);
    assertTrue(c1.getAntiSurge().isActive());
    assertEquals(3.0, c1.getAntiSurge().getSurgeControlFactor(), 1.0e-12);
    double opening = Math.min(100.0, 100.0 * need / 0.5);
    assertEquals(opening, asv.getPercentValveOpening(), 1.0e-9);
    assertEquals(c1.getAntiSurge().getValvePosition(), opening / 100.0, 1.0e-12);
    double second = s.applyRequiredRecycle().get("stage 3").doubleValue();
    assertEquals(need, second, 1.0e-12);
    assertEquals(opening, asv.getPercentValveOpening(), 1.0e-9);

    s.runAtFixedSpeed(design, null);
    assertEquals(need, s.applyRequiredRecycle().get("stage 3").doubleValue(), 1.0e-6);
    assertEquals(first.get("stage 4").doubleValue() > 0.0, asv2.getPercentValveOpening() > 0.0);

    s.setMemberActive(c1, false);
    assertEquals(0.0, s.applyRequiredRecycle().get("stage 3").doubleValue(), 0.0);
    assertFalse(c1.getAntiSurge().isActive());
    assertEquals(0.0, asv.getPercentValveOpening(), 0.0);
    s.setMemberActive(c1, true);
    s.applyRequiredRecycle();
    assertTrue(c1.getAntiSurge().isActive());
    c1.setSurgeControlMargin(0.0);
    s.runAtFixedSpeed(design, null);
    assertEquals(0.0, s.applyRequiredRecycle().get("stage 3").doubleValue(), 0.0);
    assertFalse(c1.getAntiSurge().isActive());
    assertEquals(0.0, c1.getAntiSurge().getCurrentSurgeFraction(), 0.0);
  }

  /**
   * An anti-surge the user switched on stays on when no recycle is needed.
   */
  @Test
  void userActivatedAntiSurgeStaysActive() {
    buildTrain();
    CompressorShaft s = shaft();
    c1.getAntiSurge().setActive(true);
    c1.setSurgeControlMargin(0.0);
    s.runAtFixedSpeed(design, null);
    assertEquals(0.0, s.applyRequiredRecycle().get("stage 3").doubleValue(), 0.0);
    assertTrue(c1.getAntiSurge().isActive());
  }

  /**
   * The speed band is taken from the VFD data of the motor: minimum and maximum speed ratio times the rated speed. An
   * explicit speed-bound call overrides it, and the motor supplies the available power at the common speed.
   */
  @Test
  void vfdSpeedBandAndPowerFromMotor() {
    buildTrain();
    CompressorShaft s = new CompressorShaft("VFD shaft");
    s.addCompressor(c1);
    ElectricMotorDriver drive = new ElectricMotorDriver(5000.0, 3600.0, 0.96);
    drive.setHasVFD(true);
    drive.setMinSpeedRatio(0.6);
    drive.setMaxSpeedRatio(1.05);
    ElectricMotor motor = new ElectricMotor("M-1", 0.96);
    motor.setPerformanceModel(drive);
    s.setMotorDriverUnit(motor);
    assertEquals(2160.0, s.getMinSpeed(), 1.0e-9);
    assertEquals(3780.0, s.getMaxSpeed(), 1.0e-9);
    assertTrue(s.isSpeedBandFromDriver());
    s.setSpeed(3600.0);
    assertEquals(5000.0, s.getAvailableShaftPower("kW"), 1.0);
    s.setSpeed(3000.0);
    assertEquals(5000.0 * 3000.0 / 3600.0, s.getAvailableShaftPower("kW"), 1.0);

    drive.setMaxSpeedRatio(1.1);
    s.refreshSpeedBandFromDriver();
    assertEquals(3960.0, s.getMaxSpeed(), 1.0e-9);
    drive.setCellBypassDerating(6, 1);
    drive.setMaxSpeedRatio(1.1);
    s.refreshSpeedBandFromDriver();
    assertEquals(3960.0 * 5.0 / 6.0, s.getMaxSpeed(), 1.0);

    s.setSpeedBounds(1000.0, 2000.0);
    assertFalse(s.isSpeedBandFromDriver());
    drive.setMaxSpeedRatio(1.2);
    s.refreshSpeedBandFromDriver();
    assertEquals(2000.0, s.getMaxSpeed(), 0.0);
    s.setMotorDriverUnit(null);
    assertEquals(null, s.getDriverCurve());
  }

  /**
   * A fixed-speed motor gives a narrow band around the rated speed.
   */
  @Test
  void fixedSpeedMotorGivesNarrowBand() {
    buildTrain();
    CompressorShaft s = new CompressorShaft("fixed shaft");
    s.addCompressor(c1);
    s.setDriverCurve(new ElectricMotorDriver(2000.0, 3000.0, 0.95));
    assertEquals(2940.0, s.getMinSpeed(), 1.0e-9);
    assertEquals(3000.0, s.getMaxSpeed(), 1.0e-9);
  }

  /**
   * The calculator publishes shaft, driver and member variables to ProcessAutomation with guarded setters.
   */
  @Test
  void shaftAddressesThroughProcessAutomation() {
    buildTrain();
    CompressorShaft s = shaft();
    s.setMaxShaftPower(5.0, "MW");
    CompressorShaftCalculator calc = new CompressorShaftCalculator("HT shaft calc", s, c2, 40.0, "bara");
    calc.setSpeedBounds(design * 0.92, design * 1.08);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(c1);
    process.add(c2);
    process.add(calc);
    process.run();
    ProcessAutomation auto = process.getAutomation();
    assertTrue(auto.getUnitList().contains("HT shaft calc"));
    List<SimulationVariable> vars = auto.getVariableList("HT shaft calc");
    boolean member = false;
    boolean writableActive = false;
    for (SimulationVariable v : vars) {
      member |= v.getAddress().equals("HT shaft calc.member.stage 3.recycleFraction");
      writableActive |= v.getAddress().equals("HT shaft calc.member.stage 4.active") && v.isWritable();
    }
    assertTrue(member && writableActive);
    assertTrue(auto.isWritableAddress("HT shaft calc.ambientTemperature"));
    assertFalse(auto.isWritableAddress("HT shaft calc.power"));

    assertEquals(s.getTotalPower() / 1.0e3, auto.getVariableValue("HT shaft calc.power", "kW"), 1.0e-6);
    assertEquals(c2.getPower() / 1.0e3, auto.getVariableValue("HT shaft calc.member.stage 4.power", "kW"), 1.0e-6);
    assertEquals(s.getMemberSpeed(c1), auto.getVariableValue("HT shaft calc.member.stage 3.speed", "rpm"), 1.0e-6);
    assertEquals(s.getPowerUtilization(), auto.getVariableValue("HT shaft calc.powerUtilization", ""), 1.0e-12);
    assertEquals(5.0, auto.getVariableValue("HT shaft calc.availablePower", "MW"), 1.0e-9);
    assertTrue(auto.getVariableValueSafe("HT shaft calc.member.stage 4.surgeMargin", "").contains("value"));

    assertTrue(auto.setVariableValueSafe("HT shaft calc.ambientTemperature", 30.0, "C").contains("success"));
    assertEquals(30.0, s.getAmbientTemperature("C"), 1.0e-9);
    assertFalse(auto.setVariableValueSafe("HT shaft calc.ambientTemperature", 300.0, "C").contains("success"));
    assertTrue(auto.setVariableValueSafe("HT shaft calc.maxShaftPower", 4.0, "MW").contains("success"));
    assertEquals(4.0, s.getAvailableShaftPower("MW"), 1.0e-9);

    assertFalse(auto.setVariableValueSafe("HT shaft calc.member.stage 4.active", 0.5, "").contains("success"));
    assertTrue(s.isMemberActive(c2));
    assertTrue(auto.setVariableValueSafe("HT shaft calc.member.stage 4.active", 0.0, "").contains("success"));
    assertFalse(s.isMemberActive(c2));
    assertEquals(0.0, auto.getVariableValue("HT shaft calc.member.stage 4.active", ""), 0.0);
    auto.setVariableValue("HT shaft calc.member.stage 4.active", 1.0, "");
    assertTrue(s.isMemberActive(c2));

    assertFalse(auto.setVariableValueSafe("HT shaft calc.speed", design * 2.0, "rpm").contains("success"));
    assertTrue(auto.setVariableValueSafe("HT shaft calc.speed", design, "rpm").contains("success"));
    assertEquals(design, s.getSpeed(), 1.0e-9);
    assertFalse(auto.setVariableValueSafe("HT shaft calc.member.stage 4.gearRatio", -1.0, "").contains("success"));
    assertTrue(auto.setVariableValueSafe("HT shaft calc.member.stage 4.gearRatio", 1.0, "").contains("success"));

    c1.setSurgeControlMargin(2.0);
    process.run();
    assertTrue(auto.getVariableValue("HT shaft calc.recycleFractionMax", "") > 0.0);
    assertTrue(auto.setVariableValueSafe("HT shaft calc.applyRecycle", 1.0, "").contains("success"));
    assertTrue(c1.getAntiSurge().isActive());
    assertFalse(auto.setVariableValueSafe("HT shaft calc.applyRecycle", 2.0, "").contains("success"));
    assertNotNull(auto.getVariableValueSafe("HT shaft calc.unknown", ""));
  }

  /**
   * The calculator applies the gear ratio of a member and takes the speed band from a driver on the shaft.
   */
  @Test
  void calculatorFollowsGearAndDriverBand() {
    buildTrain();
    CompressorShaft s = new CompressorShaft("geared");
    s.addCompressor(c1);
    s.addCompressor(c2, 1.1);
    ElectricMotorDriver drive = new ElectricMotorDriver(9000.0, design * 1.0, 0.96);
    drive.setHasVFD(true);
    drive.setMinSpeedRatio(0.9);
    drive.setMaxSpeedRatio(1.1);
    s.setDriverCurve(drive);
    CompressorShaftCalculator calc = new CompressorShaftCalculator("geared calc", s, c2, 40.0, "bara");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(c1);
    process.add(c2);
    process.add(calc);
    process.run();
    assertEquals(0.9 * design, calc.getMinSpeed(), 1.0e-6);
    assertEquals(1.1 * design, calc.getMaxSpeed(), 1.0e-6);
    assertEquals(1.1 * c1.getSpeed(), c2.getSpeed(), 1.0e-6);
    assertTrue(s.getSpeed() >= 0.9 * design - 1.0e-6 && s.getSpeed() <= 1.1 * design + 1.0e-6);
  }
}
