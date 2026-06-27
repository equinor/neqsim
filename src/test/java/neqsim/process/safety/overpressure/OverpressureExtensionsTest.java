package neqsim.process.safety.overpressure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for the TR3001 overpressure-protection extensions: fire-case relief, liquid and two-phase relief sizing,
 * the TR3001 compliance checker and the relief disposal network.
 *
 * @author ESOL
 * @version 1.0
 */
public class OverpressureExtensionsTest {

  /**
   * Builds a lean natural-gas fluid for relief-property evaluation.
   *
   * @param pressureBara the pressure in bara
   * @param temperatureK the temperature in K
   * @return a lean-gas fluid
   */
  private SystemInterface leanGas(double pressureBara, double temperatureK) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Fire heat input scales with wetted area and the relief rate equals Q/latent heat. */
  @Test
  public void fireCaseReliefScalesWithWettedArea() {
    ReliefScenario small = new FireCaseRelief().setName("Fire small").setWettedAreaM2(20.0).setHasDrainage(true)
        .setHasFireFighting(true).setLatentHeatJPerKg(350000.0).calculate();
    ReliefScenario large = new FireCaseRelief().setName("Fire large").setWettedAreaM2(80.0).setHasDrainage(true)
        .setHasFireFighting(true).setLatentHeatJPerKg(350000.0).calculate();
    assertEquals(ReliefCause.FIRE, small.getCause());
    assertEquals(ReliefPhase.VAPOUR, small.getPhase());
    assertTrue(small.getReliefRateKgPerS() > 0.0);
    assertTrue(large.getReliefRateKgPerS() > small.getReliefRateKgPerS());
  }

  /** Inadequate drainage gives a higher heat input (34500 vs 21000 BTU/hr/ft^2 basis). */
  @Test
  public void fireCaseNoDrainageGivesHigherRate() {
    ReliefScenario drained = new FireCaseRelief().setWettedAreaM2(50.0).setHasDrainage(true).setHasFireFighting(true)
        .setLatentHeatJPerKg(350000.0).calculate();
    ReliefScenario undrained = new FireCaseRelief().setWettedAreaM2(50.0).setHasDrainage(false)
        .setHasFireFighting(false).setLatentHeatJPerKg(350000.0).calculate();
    assertTrue(undrained.getReliefRateKgPerS() > drained.getReliefRateKgPerS());
  }

  /** Fire-case wetted area can be derived from vessel geometry and uses fluid vapour properties. */
  @Test
  public void fireCaseDerivesAreaFromGeometryAndFluid() {
    ReliefScenario scenario = new FireCaseRelief().setName("Fire geom").setVesselDiameterM(2.0).setWettedHeightM(3.0)
        .setLatentHeatJPerKg(350000.0).setReliefPressureBara(60.0).setReliefTemperatureC(120.0)
        .setFluid(leanGas(60.0, 393.15)).calculate();
    assertTrue(scenario.getReliefRateKgPerS() > 0.0);
    assertTrue(scenario.getMolarMassKgPerMol() > 0.0);
    assertTrue(scenario.getSpecificHeatRatio() > 0.0);
  }

  /** A governing liquid relief case is sized with the API 520 liquid method. */
  @Test
  public void studySizesLiquidGoverningCase() {
    ProtectedItem item = new ProtectedItem("V-400", 30.0).setReliefSetPressureBara(30.0).setBackPressureBara(1.2);
    ReliefScenario liquid = new ReliefScenario.Builder("Liquid blocked outlet", ReliefCause.BLOCKED_OUTLET)
        .phase(ReliefPhase.LIQUID).reliefRateKgPerS(15.0).reliefTemperatureK(310.0).densityKgPerM3(720.0)
        .viscosityPaS(8.0e-4).build();
    OverpressureProtectionStudy study = new OverpressureProtectionStudy(item).addScenario(liquid);
    OverpressureStudyResult result = study.evaluate();
    assertEquals(ReliefPhase.LIQUID, result.getGoverningScenario().getPhase());
    assertTrue(result.getRequiredAreaIn2() > 0.0);
    assertNotNull(result.getRecommendedOrifice());
    assertTrue(result.getSelectedAreaIn2() >= result.getRequiredAreaIn2());
  }

  /** A governing two-phase relief case is sized with the omega method. */
  @Test
  public void studySizesTwoPhaseGoverningCase() {
    ProtectedItem item = new ProtectedItem("V-500", 40.0).setReliefSetPressureBara(40.0).setBackPressureBara(1.5);
    ReliefScenario twoPhase = new ReliefScenario.Builder("Two-phase fire", ReliefCause.FIRE)
        .phase(ReliefPhase.TWO_PHASE).reliefRateKgPerS(20.0).reliefTemperatureK(360.0).gasMassFraction(0.3)
        .gasDensityKgPerM3(25.0).liquidDensityKgPerM3(600.0).latentHeatJPerKg(300000.0)
        .liquidHeatCapacityJPerKgK(2400.0).build();
    OverpressureProtectionStudy study = new OverpressureProtectionStudy(item).addScenario(twoPhase);
    OverpressureStudyResult result = study.evaluate();
    assertEquals(ReliefPhase.TWO_PHASE, result.getGoverningScenario().getPhase());
    assertTrue(result.getRequiredAreaIn2() > 0.0);
    assertNotNull(result.getRecommendedOrifice());
  }

  /** A two-phase case missing omega inputs is not sized and raises a warning. */
  @Test
  public void twoPhaseMissingInputsRaisesWarning() {
    ProtectedItem item = new ProtectedItem("V-510", 40.0).setReliefSetPressureBara(40.0).setBackPressureBara(1.5);
    ReliefScenario twoPhase = new ReliefScenario.Builder("Two-phase incomplete", ReliefCause.FIRE)
        .phase(ReliefPhase.TWO_PHASE).reliefRateKgPerS(20.0).reliefTemperatureK(360.0).build();
    OverpressureProtectionStudy study = new OverpressureProtectionStudy(item).addScenario(twoPhase);
    OverpressureStudyResult result = study.evaluate();
    assertFalse(result.getWarnings().isEmpty());
  }

  /** The compliance checker passes a well-formed vapour study. */
  @Test
  public void complianceCheckerPassesAdequateStudy() {
    ProtectedItem item = new ProtectedItem("V-600", 100.0).setReliefSetPressureBara(100.0).setBackPressureBara(1.5);
    ReliefScenario big = new ReliefScenario.Builder("Blocked outlet", ReliefCause.BLOCKED_OUTLET)
        .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(12.0).reliefTemperatureK(320.0).molarMassKgPerMol(0.019)
        .compressibility(0.95).specificHeatRatio(1.28).build();
    OverpressureStudyResult result = new OverpressureProtectionStudy(item).addScenario(big).evaluate();

    TR3001ComplianceChecker checker = new TR3001ComplianceChecker();
    List<ComplianceFinding> findings = checker.check(result);
    assertFalse(findings.isEmpty());
    assertTrue(checker.isCompliant(findings));
    boolean hasCapacityFinding = false;
    for (ComplianceFinding finding : findings) {
      if (finding.getTitle().contains("capacity")) {
        hasCapacityFinding = true;
        assertEquals(ComplianceStatus.PASS, finding.getStatus());
      }
    }
    assertTrue(hasCapacityFinding);
  }

  /** A fire governing case that is not dynamically determined triggers a needs-review finding (SR-26565). */
  @Test
  public void complianceCheckerFlagsSteadyStateFireCase() {
    ProtectedItem item = new ProtectedItem("V-700", 60.0).setReliefSetPressureBara(60.0).setBackPressureBara(1.2);
    ReliefScenario fire = new ReliefScenario.Builder("Fire", ReliefCause.FIRE).phase(ReliefPhase.VAPOUR)
        .reliefRateKgPerS(8.0).reliefTemperatureK(420.0).molarMassKgPerMol(0.020).compressibility(0.92)
        .specificHeatRatio(1.25).dynamicallyDetermined(false).build();
    OverpressureStudyResult result = new OverpressureProtectionStudy(item).addScenario(fire).evaluate();

    List<ComplianceFinding> findings = new TR3001ComplianceChecker().check(result);
    boolean foundDynamic = false;
    for (ComplianceFinding finding : findings) {
      if (finding.getRequirementId().contains("SR-26565")) {
        foundDynamic = true;
        assertEquals(ComplianceStatus.NEEDS_REVIEW, finding.getStatus());
      }
    }
    assertTrue(foundDynamic);
  }

  /** The disposal network sums simultaneous loads and ignores non-simultaneous ones for the total. */
  @Test
  public void disposalNetworkSumsSimultaneousLoads() {
    ProtectedItem itemA = new ProtectedItem("V-A", 100.0).setReliefSetPressureBara(100.0).setBackPressureBara(1.5);
    OverpressureStudyResult resultA = new OverpressureProtectionStudy(itemA).addScenario(
        new ReliefScenario.Builder("Fire A", ReliefCause.FIRE).phase(ReliefPhase.VAPOUR).reliefRateKgPerS(10.0)
            .reliefTemperatureK(420.0).molarMassKgPerMol(0.020).compressibility(0.92).specificHeatRatio(1.25).build())
        .evaluate();
    ProtectedItem itemB = new ProtectedItem("V-B", 100.0).setReliefSetPressureBara(100.0).setBackPressureBara(1.5);
    OverpressureStudyResult resultB = new OverpressureProtectionStudy(itemB).addScenario(
        new ReliefScenario.Builder("Fire B", ReliefCause.FIRE).phase(ReliefPhase.VAPOUR).reliefRateKgPerS(6.0)
            .reliefTemperatureK(420.0).molarMassKgPerMol(0.020).compressibility(0.92).specificHeatRatio(1.25).build())
        .evaluate();

    ReliefDisposalResult disposal = new ReliefDisposalNetwork("Fire zone 1").addRelief(resultA, true)
        .addRelief(resultB, true).calculate();
    assertEquals(16.0, disposal.getTotalSimultaneousKgPerS(), 1.0e-9);
    assertEquals(10.0, disposal.getPeakSingleKgPerS(), 1.0e-9);
    assertEquals("V-A", disposal.getGoverningContributor());
    assertEquals(2, disposal.getContributions().size());
    assertEquals(16.0 * 3600.0, disposal.getTotalSimultaneousKgPerHr(), 1.0e-6);
  }

  /** Non-simultaneous loads are excluded from the simultaneous total but still set the peak single. */
  @Test
  public void disposalNetworkExcludesNonSimultaneous() {
    ReliefDisposalResult disposal = new ReliefDisposalNetwork("Mixed")
        .addContribution(new ReliefLoadContribution("V-1", ReliefCause.BLOCKED_OUTLET, ReliefPhase.VAPOUR, 5.0, true))
        .addContribution(new ReliefLoadContribution("V-2", ReliefCause.TUBE_RUPTURE, ReliefPhase.VAPOUR, 9.0, false))
        .calculate();
    assertEquals(5.0, disposal.getTotalSimultaneousKgPerS(), 1.0e-9);
    assertEquals(9.0, disposal.getPeakSingleKgPerS(), 1.0e-9);
    assertEquals("V-2", disposal.getGoverningContributor());
  }

  /** The study, disposal and compliance results serialize to non-empty JSON for results.json reporting. */
  @Test
  public void resultsSerializeToJson() {
    ProtectedItem item = new ProtectedItem("V-800", 100.0).setReliefSetPressureBara(100.0).setBackPressureBara(1.5);
    ReliefScenario scenario = new ReliefScenario.Builder("Blocked outlet", ReliefCause.BLOCKED_OUTLET)
        .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(12.0).reliefTemperatureK(320.0).molarMassKgPerMol(0.019)
        .compressibility(0.95).specificHeatRatio(1.28).build();
    OverpressureStudyResult result = new OverpressureProtectionStudy(item).addScenario(scenario).evaluate();

    String studyJson = result.toJson();
    assertNotNull(studyJson);
    assertTrue(studyJson.contains("V-800"));
    assertTrue(studyJson.contains("governingScenario"));

    ReliefDisposalResult disposal = new ReliefDisposalNetwork("Zone").addRelief(result, true).calculate();
    String disposalJson = disposal.toJson();
    assertNotNull(disposalJson);
    assertTrue(disposalJson.contains("totalSimultaneousKgPerS"));

    List<ComplianceFinding> findings = new TR3001ComplianceChecker().check(result);
    String findingsJson = TR3001ComplianceChecker.findingsToJson(findings);
    assertNotNull(findingsJson);
    assertTrue(findingsJson.contains("PASS") || findingsJson.contains("status"));
  }
}
