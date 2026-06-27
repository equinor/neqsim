package neqsim.process.safety.overpressure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for the {@link neqsim.process.safety.overpressure} relief-scenario engine, cause calculators and
 * acceptance checker (TR3001 / API STD 521).
 *
 * @author ESOL
 * @version 1.0
 */
public class OverpressureProtectionStudyTest {

  /**
   * Builds a lean natural-gas fluid for relief-property evaluation.
   *
   * @param pressureBara the pressure in bara
   * @param temperatureK the temperature in K
   * @return a flashed lean-gas fluid
   */
  private SystemInterface leanGas(double pressureBara, double temperatureK) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Choked nozzle flow must equal the critical-flow value and be independent of back pressure. */
  @Test
  public void nozzleChokedFlowIsBackPressureIndependent() {
    double cd = 0.85;
    double area = 1.0e-4;
    double p1 = 50.0e5;
    double t1 = 300.0;
    double mm = 0.018;
    double k = 1.3;
    double lowBack = NozzleFlow.gasMassRateKgPerS(cd, area, p1, 1.0e5, t1, mm, k);
    double higherBack = NozzleFlow.gasMassRateKgPerS(cd, area, p1, 10.0e5, t1, mm, k);
    assertTrue(NozzleFlow.isChoked(p1, 1.0e5, k));
    assertTrue(NozzleFlow.isChoked(p1, 10.0e5, k));
    assertEquals(lowBack, higherBack, 1.0e-9);
    assertTrue(lowBack > 0.0);
  }

  /** Sub-critical nozzle flow must fall below the choked value as back pressure rises. */
  @Test
  public void nozzleSubcriticalFlowBelowChoked() {
    double cd = 0.85;
    double area = 1.0e-4;
    double p1 = 5.0e5;
    double t1 = 300.0;
    double mm = 0.018;
    double k = 1.3;
    double chokedRef = NozzleFlow.gasMassRateKgPerS(cd, area, p1, 1.0e5, t1, mm, k);
    double subcritical = NozzleFlow.gasMassRateKgPerS(cd, area, p1, 4.5e5, t1, mm, k);
    assertFalse(NozzleFlow.isChoked(p1, 4.5e5, k));
    assertTrue(subcritical < chokedRef);
    assertTrue(subcritical > 0.0);
  }

  /** Blocked outlet relief rate equals the full inflow (TR3001 SR-26460). */
  @Test
  public void blockedOutletEqualsFullInflow() {
    ReliefScenario scenario = new BlockedOutletRelief().setName("BO").setInflowRateKgPerHr(36000.0)
        .setReliefPressureBara(50.0).setReliefTemperatureC(20.0).setFluid(leanGas(50.0, 293.15)).calculate();
    assertEquals(ReliefCause.BLOCKED_OUTLET, scenario.getCause());
    assertEquals(10.0, scenario.getReliefRateKgPerS(), 1.0e-9);
    assertEquals(ReliefPhase.VAPOUR, scenario.getPhase());
    assertTrue(scenario.getMolarMassKgPerMol() > 0.0);
  }

  /** Check-valve leakage uses the default 1% leak area and produces a positive vapour rate. */
  @Test
  public void checkValveLeakageProducesPositiveRate() {
    ReliefScenario scenario = new CheckValveLeakRelief().setName("CV leak").setNominalDiameterInch(6.0)
        .setUpstreamPressureBara(120.0).setUpstreamTemperatureC(40.0).setDownstreamPressureBara(20.0)
        .setSpecificHeatRatio(1.28).setMolarMassKgPerMol(0.019).calculate();
    assertEquals(ReliefCause.CHECK_VALVE_LEAKAGE, scenario.getCause());
    assertTrue(scenario.getReliefRateKgPerS() > 0.0);
    assertEquals(ReliefPhase.VAPOUR, scenario.getPhase());
  }

  /** Control-valve failure produces a positive vapour rate from a Cv input. */
  @Test
  public void controlValveFailureProducesPositiveRate() {
    ReliefScenario scenario = new ControlValveFailureRelief().setName("CV fail").setCv(150.0)
        .setUpstreamPressureBara(80.0).setUpstreamTemperatureC(30.0).setDownstreamPressureBara(20.0)
        .setSpecificHeatRatio(1.30).setMolarMassKgPerMol(0.018).calculate();
    assertEquals(ReliefCause.CONTROL_VALVE_FAILURE, scenario.getCause());
    assertTrue(scenario.getReliefRateKgPerS() > 0.0);
  }

  /** A full-bore single-tube rupture presents two open ends (TR3001 SR-26616). */
  @Test
  public void tubeRuptureUsesTwoOpenEnds() {
    ControlValveFailureRelief ignore = new ControlValveFailureRelief();
    assertNotNull(ignore);
    ReliefScenario oneEnd = new TubeRuptureRelief().setTubeInnerDiameterMm(16.0).setNumberOfOpenEnds(1)
        .setHighPressureBara(120.0).setHighTemperatureC(60.0).setLowPressureBara(20.0).setSpecificHeatRatio(1.3)
        .setMolarMassKgPerMol(0.019).calculate();
    ReliefScenario twoEnds = new TubeRuptureRelief().setTubeInnerDiameterMm(16.0).setNumberOfOpenEnds(2)
        .setHighPressureBara(120.0).setHighTemperatureC(60.0).setLowPressureBara(20.0).setSpecificHeatRatio(1.3)
        .setMolarMassKgPerMol(0.019).calculate();
    assertEquals(ReliefCause.TUBE_RUPTURE, twoEnds.getCause());
    assertEquals(2.0 * oneEnd.getReliefRateKgPerS(), twoEnds.getReliefRateKgPerS(), 1.0e-9);
  }

  /** Accumulation factors follow ASME VIII Div 1 / TR3001 section 2. */
  @Test
  public void accumulationFactorsMatchStandard() {
    OverpressureAcceptanceChecker checker = new OverpressureAcceptanceChecker();
    assertEquals(1.10, checker.accumulationFactor(ReliefCause.BLOCKED_OUTLET, false), 1.0e-12);
    assertEquals(1.16, checker.accumulationFactor(ReliefCause.BLOCKED_OUTLET, true), 1.0e-12);
    assertEquals(1.21, checker.accumulationFactor(ReliefCause.FIRE, false), 1.0e-12);
    assertEquals(1.21, checker.accumulationFactor(ReliefCause.FIRE, true), 1.0e-12);
  }

  /** The study selects the largest credible scenario, sizes a PSV and checks acceptance. */
  @Test
  public void studySelectsGoverningCaseAndSizes() {
    ProtectedItem item = new ProtectedItem("V-100", 100.0).setReliefSetPressureBara(100.0).setBackPressureBara(1.5);

    ReliefScenario small = new ReliefScenario.Builder("Small", ReliefCause.CHECK_VALVE_LEAKAGE)
        .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(2.0).reliefTemperatureK(310.0).molarMassKgPerMol(0.019)
        .compressibility(0.95).specificHeatRatio(1.28).build();
    ReliefScenario big = new ReliefScenario.Builder("Big", ReliefCause.BLOCKED_OUTLET).phase(ReliefPhase.VAPOUR)
        .reliefRateKgPerS(12.0).reliefTemperatureK(320.0).molarMassKgPerMol(0.019).compressibility(0.95)
        .specificHeatRatio(1.28).build();

    OverpressureProtectionStudy study = new OverpressureProtectionStudy(item).addScenario(small).addScenario(big);
    OverpressureStudyResult result = study.evaluate();

    assertNotNull(result.getGoverningScenario());
    assertEquals("Big", result.getGoverningScenario().getName());
    assertTrue(result.getRequiredAreaIn2() > 0.0);
    assertNotNull(result.getRecommendedOrifice());
    assertTrue(result.isCapacityAdequate());
    assertNotNull(result.getAcceptance());
    assertTrue(result.getAcceptance().isAccepted());
  }

  /** A non-credible scenario is excluded from governing-case selection (double-jeopardy filter). */
  @Test
  public void nonCredibleScenarioIsExcluded() {
    ProtectedItem item = new ProtectedItem("V-200", 50.0);
    ReliefScenario credible = new ReliefScenario.Builder("Credible", ReliefCause.BLOCKED_OUTLET).reliefRateKgPerS(5.0)
        .molarMassKgPerMol(0.019).specificHeatRatio(1.3).reliefTemperatureK(300.0).build();
    ReliefScenario excluded = new ReliefScenario.Builder("Excluded", ReliefCause.TUBE_RUPTURE).reliefRateKgPerS(50.0)
        .molarMassKgPerMol(0.019).specificHeatRatio(1.3).reliefTemperatureK(300.0).credible(false).build();

    OverpressureProtectionStudy study = new OverpressureProtectionStudy(item).addScenario(credible)
        .addScenario(excluded);
    assertEquals("Credible", study.governingScenario().getName());
  }

  /** Fire is the governing contingency and uses the 121% accumulation limit. */
  @Test
  public void fireCaseUsesFireAccumulationLimit() {
    ProtectedItem item = new ProtectedItem("V-300", 60.0).setReliefSetPressureBara(60.0).setBackPressureBara(1.2);
    ReliefScenario fire = new ReliefScenario.Builder("Fire", ReliefCause.FIRE).phase(ReliefPhase.VAPOUR)
        .reliefRateKgPerS(8.0).reliefTemperatureK(420.0).molarMassKgPerMol(0.020).compressibility(0.92)
        .specificHeatRatio(1.25).build();
    OverpressureProtectionStudy study = new OverpressureProtectionStudy(item).addScenario(fire);
    OverpressureStudyResult result = study.evaluate();
    assertEquals(ReliefCause.FIRE, result.getGoverningScenario().getCause());
    assertEquals(60.0 * 1.21, result.getAcceptance().getAllowableAccumulatedPressureBara(), 1.0e-9);
  }
}
