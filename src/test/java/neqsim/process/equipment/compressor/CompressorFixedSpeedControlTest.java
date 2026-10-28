package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link CompressorFixedSpeedControl} and {@link CompressorShaft#evaluateFixedSpeedControl()}: a
 * constant-speed compressor that meets a discharge pressure by recycle, needs throttling, or has a head deficit.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorFixedSpeedControlTest {
  private Compressor member;
  private CompressorShaft shaft;
  private double processFlowM3h;
  private double deliveredBara;
  private double powerKW;

  /**
   * Build a charted compressor running at its fixed design speed.
   */
  @BeforeEach
  void setUp() {
    SystemSrkEos gas = new SystemSrkEos(273.15 + 30.0, 10.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");
    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(6000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    member = new Compressor("body", feed);
    member.setOutletPressure(20.0);
    member.setUsePolytropicCalc(true);
    member.setPolytropicEfficiency(0.75);
    member.run();
    member.generateCompressorChart("normal curves", 5);
    member.getCompressorChart().setUseCompressorChart(true);

    shaft = new CompressorShaft("fixed speed shaft");
    shaft.addCompressor(member);
    shaft.runAtFixedSpeed(member.getSpeed(), null);

    processFlowM3h = member.getInletStream().getFlowRate("m3/hr");
    deliveredBara = member.getOutletStream().getPressure("bara");
    powerKW = member.getPower("kW");
  }

  /**
   * A requirement equal to the pressure the chart delivers at the process flow needs no recycle and no throttling.
   */
  @Test
  void requirementEqualToChartPressureIsOnCurve() {
    FixedSpeedControlResult r = CompressorFixedSpeedControl.evaluate(member, deliveredBara);
    assertEquals(FixedSpeedControlResult.Status.ON_CURVE, r.getStatus(), r.getMessage());
    assertTrue(r.isFeasible());
    assertEquals(0.0, r.getRecycleFraction(), 1e-9);
    assertEquals(0.0, r.getThrottlePressureDropBar(), 1e-9);
    assertEquals(powerKW, r.getShaftPowerKW(), 0.01 * powerKW);
  }

  /**
   * Surplus head is met by recycle: the through-flow is higher than the process flow, and a fresh run of the member at
   * that through-flow gives the required discharge pressure. The shaft power at that point is higher than at the
   * process flow.
   */
  @Test
  void surplusHeadIsMetByRecycleOnTheChart() {
    double required = 0.95 * deliveredBara;
    FixedSpeedControlResult r = CompressorFixedSpeedControl.evaluate(member, required);
    assertEquals(FixedSpeedControlResult.Status.RECYCLE_CONTROLLED, r.getStatus(), r.getMessage());
    assertTrue(r.getThroughFlowM3PerHr() > processFlowM3h);
    assertTrue(r.getRecycleFraction() > 0.0 && r.getRecycleFraction() < 1.0);
    assertEquals(1.0 - processFlowM3h / r.getThroughFlowM3PerHr(), r.getRecycleFraction(), 1e-9);
    assertTrue(r.getShaftPowerKW() > powerKW, "power at the recycle point includes the recycled gas");

    Compressor check = member.copy();
    double scale = r.getThroughFlowM3PerHr() / processFlowM3h;
    check.getInletStream().setFlowRate(6000.0 * scale, "kg/hr");
    check.getInletStream().run();
    check.run();
    assertEquals(required, check.getOutletStream().getPressure("bara"), 0.005 * required);
    assertEquals(check.getPower("kW"), r.getShaftPowerKW(), 0.01 * r.getShaftPowerKW());
  }

  /**
   * Less required pressure gives more recycle and more power.
   */
  @Test
  void lowerRequirementNeedsMoreRecycle() {
    FixedSpeedControlResult a = CompressorFixedSpeedControl.evaluate(member, 0.97 * deliveredBara);
    FixedSpeedControlResult b = CompressorFixedSpeedControl.evaluate(member, 0.92 * deliveredBara);
    assertTrue(a.isFeasible() && b.isFeasible());
    assertTrue(b.getRecycleFraction() > a.getRecycleFraction());
    assertTrue(b.getShaftPowerKW() >= a.getShaftPowerKW());
  }

  /**
   * A requirement above the chart head at the fixed speed is a head deficit and reports the deliverable pressure.
   */
  @Test
  void requirementAboveChartHeadIsHeadDeficit() {
    FixedSpeedControlResult r = CompressorFixedSpeedControl.evaluate(member, 1.4 * deliveredBara);
    assertEquals(FixedSpeedControlResult.Status.HEAD_DEFICIT, r.getStatus(), r.getMessage());
    assertFalse(r.isFeasible());
    assertEquals(deliveredBara, r.getDeliverableDischargeBara(), 0.01 * deliveredBara);
  }

  /**
   * A requirement far below what the chart makes even at the stonewall flow cannot be met by recycle and needs
   * throttling.
   */
  @Test
  void requirementFarBelowChartHeadNeedsThrottling() {
    FixedSpeedControlResult r = CompressorFixedSpeedControl.evaluate(member, 10.3);
    assertEquals(FixedSpeedControlResult.Status.THROTTLE_REQUIRED, r.getStatus(), r.getMessage());
    assertTrue(r.isFeasible());
    assertTrue(r.getThrottlePressureDropBar() > 0.0);
    assertEquals(deliveredBara - 10.3, r.getThrottlePressureDropBar(), 0.02 * deliveredBara);
  }

  /**
   * The evaluation works on a copy: the member and its streams keep their state.
   */
  @Test
  void evaluationDoesNotChangeTheMember() {
    double flowBefore = member.getInletStream().getFlowRate("kg/hr");
    CompressorFixedSpeedControl.evaluate(member, 0.9 * deliveredBara);
    assertEquals(flowBefore, member.getInletStream().getFlowRate("kg/hr"), 1e-9);
    assertEquals(deliveredBara, member.getOutletStream().getPressure("bara"), 1e-9);
    assertEquals(powerKW, member.getPower("kW"), 1e-9);
  }

  /**
   * The shaft evaluates every member with a required pressure and reports idle and unset members as not evaluated.
   */
  @Test
  void shaftEvaluatesMembersWithRequiredPressure() {
    List<FixedSpeedControlResult> none = shaft.evaluateFixedSpeedControl();
    assertEquals(1, none.size());
    assertEquals(FixedSpeedControlResult.Status.NOT_EVALUATED, none.get(0).getStatus());

    shaft.setMemberRequiredDischargePressure(member, 0.9 * deliveredBara, "bara");
    List<FixedSpeedControlResult> results = shaft.evaluateFixedSpeedControl();
    assertEquals(FixedSpeedControlResult.Status.RECYCLE_CONTROLLED, results.get(0).getStatus(),
        results.get(0).getMessage());
    assertEquals(results.get(0).getShaftPowerKW(), shaft.getFixedSpeedControlledPower("kW"), 1e-6);

    shaft.setMemberActive(member, false);
    assertEquals(FixedSpeedControlResult.Status.NOT_EVALUATED, shaft.evaluateFixedSpeedControl().get(0).getStatus());
  }

  /**
   * A member without a chart in use cannot be evaluated; invalid arguments are rejected.
   */
  @Test
  void memberWithoutChartAndBadArguments() {
    SystemSrkEos gas = new SystemSrkEos(273.15 + 30.0, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    Stream feed = new Stream("f", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    Compressor plain = new Compressor("plain", feed);
    plain.setOutletPressure(20.0);
    plain.run();
    assertEquals(FixedSpeedControlResult.Status.NOT_EVALUATED,
        CompressorFixedSpeedControl.evaluate(plain, 20.0).getStatus());
    assertThrows(IllegalArgumentException.class, () -> CompressorFixedSpeedControl.evaluate(null, 20.0));
    assertThrows(IllegalArgumentException.class, () -> CompressorFixedSpeedControl.evaluate(member, 0.0));
  }

  /**
   * A lighter gas at the same actual flow needs more head for the same pressure, which the fixed-speed chart cannot
   * give: the member that was on its curve with the heavier gas has a head deficit with the lighter gas (the flare-gas
   * swing of a recompression train).
   */
  @Test
  void lighterGasAtTheSameFlowGivesHeadDeficitAtFixedSpeed() {
    Compressor light = member.copy();
    light.getInletStream().getThermoSystem().setMolarComposition(new double[] {1.0, 0.0});
    light.getInletStream().run();
    double q = light.getInletStream().getFlowRate("m3/hr");
    light.getInletStream().setFlowRate(6000.0 * processFlowM3h / q, "kg/hr");
    light.getInletStream().run();
    light.run();

    FixedSpeedControlResult heavy = CompressorFixedSpeedControl.evaluate(member, 0.97 * deliveredBara);
    FixedSpeedControlResult lighter = CompressorFixedSpeedControl.evaluate(light, 0.97 * deliveredBara);
    assertTrue(heavy.isFeasible(), heavy.getMessage());
    assertEquals(FixedSpeedControlResult.Status.HEAD_DEFICIT, lighter.getStatus(), lighter.getMessage());
    assertTrue(lighter.getDeliverableDischargeBara() < 0.97 * deliveredBara);
  }

  /**
   * The JSON report carries the status and the controlled operating point.
   */
  @Test
  void jsonReportCarriesStatusAndPower() {
    FixedSpeedControlResult r = CompressorFixedSpeedControl.evaluate(member, 0.95 * deliveredBara);
    String json = r.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"status\":\"RECYCLE_CONTROLLED\""), json);
    assertTrue(json.contains("\"shaftPowerKW\":"), json);
    assertTrue(json.contains("\"recycleFraction\":"), json);
  }
}
