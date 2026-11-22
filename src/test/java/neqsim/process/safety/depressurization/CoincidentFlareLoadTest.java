package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource.BlowdownSource;
import neqsim.process.safety.depressurization.FlareDisposalCaseResult.CapacityStatus;
import neqsim.process.safety.overpressure.BlockedOutletRelief;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression cases for event-relative PSV and BDV load coincidence. */
class CoincidentFlareLoadTest {
  private final SystemInterface gas = gas();
  private final OverpressureProtectionStudy relief = new OverpressureProtectionStudy(
      new ProtectedItem("V1", 80.0).setReliefSetPressureBara(75.0).setBackPressureBara(1.5))
      .addScenario(scenario("blocked outlet", 2.0)).addScenario(scenario("other contingency", 4.0));

  /** A PSV begins partway through the first BDV interval, which was accepted before the 5 s sample. */
  @Test
  void midIntervalOverlapUsesAcceptedInventoryAndSelectedScenario() {
    CoupledReliefBlowdownFlareInput.Builder input = builder(2.01);
    input.addReliefScenario(relief, "blocked outlet", "process upset", 3.0, 5.0, gas).scheduleBlowdown("V1",
        "process upset", 1.0, 11.0);
    CoupledReliefBlowdownFlareResult result = run(input);
    FlareDisposalCaseResult event = result.getDisposalCases().get(0);
    double bdv = firstAcceptedRate(result);
    assertTrue(bdv > 0.01);
    assertEquals(2.0 + bdv, event.getPeakMassFlowKgPerS(), 1e-9);
    assertEquals(3.0, event.getPeakTimeSeconds());
    assertEquals(2.0, event.getContributionsKgPerS().get("PSV:V1"));
    assertEquals(bdv, event.getContributionsKgPerS().get("BDV:V1"), 1e-9);
    assertEquals("COINCIDENT_RELIEF_AND_BLOWDOWN", result.getGoverningBasis());
    assertEquals(CapacityStatus.EXCEEDED, event.getCapacityChecks().get("massFlowKgPerS"));
    assertFalse(result.isCapacityAcceptable());
  }

  /** Simultaneous closing and opening boundaries must never double count a release. */
  @Test
  void touchingWindowsAreDisjoint() {
    CoupledReliefBlowdownFlareResult result = run(builder(500.0)
        .addReliefScenario(relief, "blocked outlet", "trip", 0.0, 3.0, gas).scheduleBlowdown("V1", "trip", 3.0, 4.0));
    assertEquals(Math.max(2.0, firstAcceptedRate(result)), result.getGoverningMassFlowKgPerS(), 1e-9);
    assertEquals(1, result.getDisposalCases().get(0).getContributionsKgPerS().size());
    assertEquals(CapacityStatus.WITHIN_LIMIT, result.getDisposalCases().get(0).getCapacityStatus());
  }

  /** Mutually exclusive events must remain distinct, including a steady overload that the BDV alone passes. */
  @Test
  void steadyOnlyOverloadIsCheckedInItsOwnEvent() {
    CoupledReliefBlowdownFlareResult result = run(builder(3.9).addReliefStudy(relief, "blocked export", gas)
        .scheduleBlowdown("V1", "independent ESD", 0.0, null));
    assertEquals(2, result.getDisposalCases().size());
    assertEquals("blocked export", result.getGoverningEvent());
    assertEquals(4.0, result.getGoverningMassFlowKgPerS());
    assertEquals(CapacityStatus.EXCEEDED, result.getDisposalCases().get(0).getCapacityStatus());
    assertEquals(CapacityStatus.WITHIN_LIMIT, result.getDisposalCases().get(1).getCapacityStatus());
  }

  /** Even a small positive disposal rate needs capacity evidence, including a gas combustion basis. */
  @Test
  void missingMassLimitAndCombustionBasisRemainUnevaluated() {
    CoupledReliefBlowdownFlareResult result = run(builder(Double.NaN).addReliefStudy(relief, "isolated relief"));
    FlareDisposalCaseResult event = result.getDisposalCases().get(0);
    assertEquals(CapacityStatus.NOT_EVALUATED, event.getCapacityChecks().get("massFlowKgPerS"));
    assertEquals(CapacityStatus.NOT_EVALUATED, event.getCapacityChecks().get("heatDutyW"));
    assertEquals(CapacityStatus.WITHIN_LIMIT, event.getCapacityChecks().get("molarFlowMolPerS"));
    assertFalse(result.isCapacityAcceptable());
    assertTrue(result.toJson().contains("NOT_EVALUATED"));
  }

  /** Pure constituent limits bound a 1 kg/s fractionating discharge, including ambiguous capacities. */
  @Test
  void fractionatingDischargeReportsPhysicalBounds() {
    SystemInterface fluid = gas();
    fluid.addComponent("ethane", 1.0);
    BlowdownSource source = BlowdownSource.builder("wet gas", fluid).build();
    FlareDischargeTimeline event = new FlareDischargeTimeline();
    event.addBlowdown(source, null, acceptedInterval(true, 2.0, 10.0, 8.0));
    DynamicBlowdownFlareStudyDataSource configuration = DynamicBlowdownFlareStudyDataSource.builder("limits")
        .addSource(source).flareDesignCapacity(55e6, 2.0, 40.0).build();
    FlareDisposalCaseResult result = event.evaluate("trip", configuration);
    assertEquals(1.0, result.getPeakMassFlowKgPerS());
    assertEquals(0.0, result.getPeakLowerBounds().get("heatDutyW"));
    assertTrue(result.getPeakUpperBounds().get("heatDutyW") > 49e6);
    assertTrue(result.getPeakUpperBounds().get("heatDutyW") < 51e6);
    assertEquals(CapacityStatus.WITHIN_LIMIT, result.getCapacityChecks().get("heatDutyW"));
    assertEquals(CapacityStatus.NOT_EVALUATED, result.getCapacityChecks().get("molarFlowMolPerS"));
    assertEquals(0.0, result.getPeakLowerBounds().get("molarFlowMolPerS"));
    assertEquals(1.0 / 0.01604, result.getPeakUpperBounds().get("molarFlowMolPerS"), 0.03);
    assertNull(result.getPeakValues().get("heatDutyW"));
    DynamicBlowdownFlareStudyDataSource lowCapacity = DynamicBlowdownFlareStudyDataSource.builder("overload")
        .addSource(source).flareDesignCapacity(40e6, 2.0, 30.0).build();
    assertEquals(CapacityStatus.NOT_EVALUATED,
        event.evaluate("trip", lowCapacity).getCapacityChecks().get("heatDutyW"));
  }

  /** A gross wet release can exceed a gas-tip rating while its flashed vapour passes. */
  @Test
  void wetReleaseOverTipLimitNeedsKnockoutAssessment() {
    SystemInterface wet = new SystemSrkEos(300.0, 2.0);
    wet.addComponent("methane", 0.65);
    wet.addComponent("n-decane", 0.35);
    wet.init(0);
    double rate = wet.getMolarMass();
    BlowdownSource source = BlowdownSource.builder("wet", wet).build();
    FlareDischargeTimeline event = new FlareDischargeTimeline();
    event.addBlowdown(source, null, acceptedInterval(false, 1.0, 1.0, 1.0 - rate));
    DynamicBlowdownFlareStudyDataSource config = DynamicBlowdownFlareStudyDataSource.builder("wet").addSource(source)
        .flareHeader(0.3, 2.0, 300.0, 0.02, 1.3).flareDesignCapacity(1.633e6, 0.04, 0.9).build();
    FlareDisposalCaseResult result = event.evaluate("wet", config);
    assertEquals(rate, result.getPeakMassFlowKgPerS(), 1e-12);
    assertEquals(CapacityStatus.NOT_EVALUATED, result.getCapacityChecks().get("heatDutyW"));
    assertEquals(CapacityStatus.NOT_EVALUATED, result.getCapacityChecks().get("molarFlowMolPerS"));
    assertEquals(CapacityStatus.NOT_EVALUATED, result.getCapacityChecks().get("massFlowKgPerS"));
    assertEquals(CapacityStatus.WITHIN_LIMIT, result.getCapacityChecks().get("headerMach"));
  }

  /** A typo must never silently convert a requested coincident discharge to a standalone case. */
  @Test
  void rejectsInvalidEventDefinitions() {
    assertThrows(IllegalArgumentException.class,
        () -> builder(50.0).scheduleBlowdown("absent", "trip", 0.0, 1.0).build());
    assertThrows(IllegalArgumentException.class, () -> builder(50.0).scheduleBlowdown("V1", "trip", 2.0, 2.0));
    assertThrows(IllegalArgumentException.class,
        () -> builder(50.0).addReliefScenario(relief, "typo", "trip", 0.0, 1.0, gas));
  }

  private static Map<String, Object> acceptedInterval(boolean fractionating, double duration, double startMass,
      double endMass) {
    Map<String, List<Double>> profile = new LinkedHashMap<>();
    profile.put("timeS", Arrays.asList(0.0, duration));
    profile.put("massKg", Arrays.asList(startMass, endMass));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("vapourWithdrawalUsed", fractionating);
    result.put("profile", profile);
    return result;
  }

  private double firstAcceptedRate(CoupledReliefBlowdownFlareResult result) {
    Map<?, ?> sources = (Map<?, ?>) result.getDynamicHandoff().getResult().get("sourceResults");
    Map<?, ?> profile = (Map<?, ?>) ((Map<?, ?>) sources.get("V1")).get("profile");
    List<?> mass = (List<?>) profile.get("massKg");
    List<?> time = (List<?>) profile.get("timeS");
    return (((Number) mass.get(0)).doubleValue() - ((Number) mass.get(1)).doubleValue())
        / (((Number) time.get(1)).doubleValue() - ((Number) time.get(0)).doubleValue());
  }

  private SystemInterface gas() {
    SystemInterface fluid = new SystemSrkEos(313.15, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private ReliefScenario scenario(String name, double rate) {
    return new BlockedOutletRelief().setName(name).setInflowRateKgPerS(rate).setReliefPressureBara(75.0)
        .setReliefTemperatureC(30.0).setFluid(gas).calculate();
  }

  private CoupledReliefBlowdownFlareInput.Builder builder(double maxMass) {
    BlowdownSource bdv = BlowdownSource.builder("V1", gas).equipmentTag("V1").vesselVolumeM3(2.0)
        .orificeDiameterM(0.012).dischargeCoefficient(0.72).backPressureBara(1.5).stopPressureBara(1.5)
        .fireHeatInputW(0.0).psvBasis(75.0, 0.21, false, false).build();
    DynamicBlowdownFlareStudyDataSource study = DynamicBlowdownFlareStudyDataSource.builder("dynamic").addSource(bdv)
        .flareHeader(0.30, 1.5, 288.15, 0.020, 1.30).flareGeometry(0.5, 35.0, 0.20)
        .flareDesignCapacity(5.0e8, maxMass, 30000.0).build();
    return CoupledReliefBlowdownFlareInput.builder("coupled").dynamicStudy(study).scenarioSelectionReviewed(true);
  }

  private CoupledReliefBlowdownFlareResult run(CoupledReliefBlowdownFlareInput.Builder input) {
    return new CoupledReliefBlowdownFlareCalculation(
        DynamicBlowdownFlareStudyRunner.builder().timeStepSeconds(5.0).maxTimeSeconds(15.0).build())
        .calculate(input.build(), null).getValue();
  }
}
