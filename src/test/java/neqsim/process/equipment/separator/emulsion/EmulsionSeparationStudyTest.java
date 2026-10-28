package neqsim.process.equipment.separator.emulsion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Checks optional-study ownership, kinetic provenance and conserved dispersed-water accounting.
 *
 * @author ESOL
 * @version 1.0
 */
class EmulsionSeparationStudyTest {
  /** Roundoff in the requested step count must not create a zero-duration final step. */
  @Test
  void handlesDecimalDurationAtAnExactNumberOfSteps() {
    EmulsionSeparationStudy.Result result = new EmulsionSeparationStudy(1.0, 0.01, 800.0, 1000.0)
        .calibratedKinetics(1.35e-3, 9500.0).run(1.0e-5, 0.07, 0.01);
    assertEquals(0.07, result.getProfile().get(result.getProfile().size() - 1).getTimeS(), 0.0);
    assertEquals(8, result.getProfile().size());
    assertEquals(result.getIncomingWaterVolumeM3(),
        result.getAccumulatedWaterVolumeM3() + result.getCoalescedWaterVolumeM3(), 1e-20);
  }

  /** Execute the exact self-contained example in the study guide. */
  @Test
  void executesDocumentedEstimatedStudy() {
    EmulsionSeparationStudy study = new EmulsionSeparationStudy(1.0, 0.01, 800.0, 1000.0).guessKinetics(1.35e-3, 60.0);
    EmulsionSeparationStudy.Result result = study.run(1.0e-5, 600.0, 1.0);
    double heightM = result.getFinalLayerHeightM();
    double criticalFlowM3PerS = result.getCriticalWaterFlowM3PerS();

    assertTrue(Double.isFinite(heightM));
    assertTrue(heightM > 0.0);
    assertTrue(criticalFlowM3PerS > 0.0);
    assertEquals(EmulsionSeparationStudy.KineticsProvenance.ESTIMATED_UNCALIBRATED, result.getKineticsProvenance());
    assertEquals(9, result.getScenarios().size());
    assertEquals(601, result.getProfile().size());
    assertEquals(600.0, result.getProfile().get(600).getTimeS(), 0.0);
  }

  /** Account for incoming water, retained droplets and coalesced drainage independently of kinetics. */
  @Test
  void closesWaterLedgerIncludingInitialInventoryAndRemainderTimeStep() {
    EmulsionSeparationStudy study = new EmulsionSeparationStudy(0.4, 0.01, 800.0, 1000.0).calibratedKinetics(1.35e-3,
        9500.0);
    EmulsionSeparationStudy.Result result = study.run(0.001, 2.0e-5, 10.5, 2.0);
    assertEquals(0.0004, result.getInitialWaterVolumeM3(), 1e-18);
    assertEquals(2.1e-4, result.getIncomingWaterVolumeM3(), 1e-18);
    assertEquals(result.getInitialWaterVolumeM3() + result.getIncomingWaterVolumeM3(),
        result.getAccumulatedWaterVolumeM3() + result.getCoalescedWaterVolumeM3(), 1e-16);
    assertTrue(result.getAccumulatedWaterVolumeM3() >= 0.0);
    assertTrue(result.getCoalescedWaterVolumeM3() >= 0.0);
    assertEquals(10.5, result.getProfile().get(result.getProfile().size() - 1).getTimeS(), 0.0);
  }

  /** Keep supplied calibrated kinetics separate from automatically generated guesses. */
  @Test
  void suppliedCalibrationHasNoGuessedReplacementScenarios() {
    EmulsionSeparationStudy.Result result = new EmulsionSeparationStudy(1.0, 0.01, 800.0, 1000.0)
        .calibratedKinetics(1.35e-3, 9500.0).run(1.0e-5, 60.0, 1.0);
    assertEquals(EmulsionSeparationStudy.KineticsProvenance.CALIBRATED_INPUT, result.getKineticsProvenance());
    assertEquals(1, result.getScenarios().size());
    assertEquals(9500.0, result.getScenarios().get(0).getOmegaPerM2Second(), 0.0);
    assertTrue(Double.isNaN(result.getScenarios().get(0).getContactTimeS()));
    assertTrue(result.getWarnings().stream().anyMatch(text -> text.contains("calibration source")));
  }

  /** Fully inhibited coalescence must retain all incoming water and declare no finite steady layer. */
  @Test
  void inhibitedCoalescenceAccumulatesConservatively() {
    EmulsionSeparationStudy.Result result = new EmulsionSeparationStudy(2.0, 0.01, 800.0, 1000.0)
        .calibratedKinetics(1.35e-3, 0.0).run(1.0e-5, 120.0, 2.0);
    assertEquals(0.0, result.getCriticalWaterFlowM3PerS(), 0.0);
    assertEquals(0.0012, result.getAccumulatedWaterVolumeM3(), 1e-14);
    assertEquals(0.0, result.getCoalescedWaterVolumeM3(), 1e-14);
    assertEquals(Double.POSITIVE_INFINITY, result.getSteadyLayerHeightM());
  }

  /** Return fresh results for repeated calls and leave the original immutable configuration intact. */
  @Test
  void repeatedStudiesAreIndependentAndGuessChangesAreExplicit() {
    EmulsionSeparationStudy original = new EmulsionSeparationStudy(1.0, 0.01, 800.0, 1000.0);
    EmulsionSeparationStudy changed = original.guessKinetics(1.35e-3, 180.0);
    EmulsionSeparationStudy.Result first = original.run(1.0e-5, 60.0, 1.0);
    EmulsionSeparationStudy.Result slower = changed.run(1.0e-5, 60.0, 1.0);
    EmulsionSeparationStudy.Result repeated = original.run(1.0e-5, 60.0, 1.0);
    assertEquals(first.getFinalLayerHeightM(), repeated.getFinalLayerHeightM(), 0.0);
    assertEquals(first.getAccumulatedWaterVolumeM3(), repeated.getAccumulatedWaterVolumeM3(), 0.0);
    assertTrue(slower.getFinalLayerHeightM() > first.getFinalLayerHeightM());
    assertEquals(first.getCriticalWaterFlowM3PerS() / 3.0, slower.getCriticalWaterFlowM3PerS(), 1e-18);
  }

  /** Expose a sensitivity envelope as declared scenarios rather than an unsupported probability. */
  @Test
  void guessedSensitivityCasesChangeCapacityAndLayerHeight() {
    EmulsionSeparationStudy.Result result = new EmulsionSeparationStudy(1.0, 0.01, 800.0, 1000.0).run(1.0e-5, 60.0,
        1.0);
    double minimumHeight = Double.POSITIVE_INFINITY;
    double maximumHeight = 0.0;
    double minimumCriticalFlow = Double.POSITIVE_INFINITY;
    double maximumCriticalFlow = 0.0;
    for (EmulsionSeparationStudy.Scenario scenario : result.getScenarios()) {
      minimumHeight = Math.min(minimumHeight, scenario.getFinalLayerHeightM());
      maximumHeight = Math.max(maximumHeight, scenario.getFinalLayerHeightM());
      minimumCriticalFlow = Math.min(minimumCriticalFlow, scenario.getCriticalWaterFlowM3PerS());
      maximumCriticalFlow = Math.max(maximumCriticalFlow, scenario.getCriticalWaterFlowM3PerS());
    }
    assertTrue(minimumHeight < maximumHeight);
    assertTrue(minimumHeight <= result.getFinalLayerHeightM());
    assertTrue(maximumHeight >= result.getFinalLayerHeightM());
    assertEquals(81.0, maximumCriticalFlow / minimumCriticalFlow, 1e-10);
    assertTrue(result.getWarnings().stream().anyMatch(text -> text.contains("not a probability")));
  }

  /** Source separator thermodynamics, levels and product streams must not be mutated by the study. */
  @Test
  void separatorAdapterOwnsPropertiesAndDoesNotChangeSourceState() {
    ThreePhaseSeparator separator = calculatedSeparator();
    SystemInterface source = separator.getThermoSystem();
    double pressure = source.getPressure();
    double temperature = source.getTemperature();
    double moles = source.getTotalNumberOfMoles();
    int phaseCount = source.getNumberOfPhases();
    double oilLevel = separator.getOilLevel();
    double waterLevel = separator.getWaterLevel();
    double gasFlow = separator.getGasOutStream().getFlowRate("kg/hr");
    double oilFlow = separator.getOilOutStream().getFlowRate("kg/hr");
    double waterFlow = separator.getWaterOutStream().getFlowRate("kg/hr");
    EmulsionSeparationStudy study = new EmulsionSeparationStudy(separator, 1.0).calibratedKinetics(1.35e-3, 9500.0);
    EmulsionSeparationStudy.Result before = study.run(1e-5, 60.0, 1.0);
    assertEquals(pressure, source.getPressure(), 0.0);
    assertEquals(temperature, source.getTemperature(), 0.0);
    assertEquals(moles, source.getTotalNumberOfMoles(), 0.0);
    assertEquals(phaseCount, source.getNumberOfPhases());
    assertEquals(oilLevel, separator.getOilLevel(), 0.0);
    assertEquals(waterLevel, separator.getWaterLevel(), 0.0);
    assertEquals(gasFlow, separator.getGasOutStream().getFlowRate("kg/hr"), 0.0);
    assertEquals(oilFlow, separator.getOilOutStream().getFlowRate("kg/hr"), 0.0);
    assertEquals(waterFlow, separator.getWaterOutStream().getFlowRate("kg/hr"), 0.0);

    source.setTemperature(temperature + 10.0);
    EmulsionSeparationStudy.Result after = study.run(1e-5, 60.0, 1.0);
    assertEquals(before.getFinalLayerHeightM(), after.getFinalLayerHeightM(), 0.0);
  }

  /** Result collections must not allow a caller to change past predictions. */
  @Test
  void resultCollectionsAreImmutableAndZeroDurationRetainsInitialState() {
    EmulsionSeparationStudy.Result result = new EmulsionSeparationStudy(1.0, 0.01, 800.0, 1000.0).run(0.0, 0.0, 1.0);
    assertEquals(1, result.getProfile().size());
    assertEquals(0.0, result.getFinalLayerHeightM(), 0.0);
    assertEquals(0.0, result.getIncomingWaterVolumeM3(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> result.getProfile().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.getWarnings().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.getScenarios().clear());
  }

  /** Reject invalid physical inputs and bound unreasonable numerical requests before integration. */
  @Test
  void rejectsInvalidOrUnboundedRequests() {
    assertThrows(IllegalArgumentException.class, () -> new EmulsionSeparationStudy(Double.NaN, 0.01, 800.0, 1000.0));
    assertThrows(IllegalArgumentException.class, () -> new EmulsionSeparationStudy(1.0, 0.0, 800.0, 1000.0));
    assertThrows(IllegalArgumentException.class, () -> new EmulsionSeparationStudy(1.0, 0.01, 1000.0, 800.0));
    assertThrows(IllegalArgumentException.class, () -> new EmulsionSeparationStudy((ThreePhaseSeparator) null, 1.0));
    EmulsionSeparationStudy study = new EmulsionSeparationStudy(1.0, 0.01, 800.0, 1000.0);
    assertThrows(IllegalArgumentException.class, () -> study.guessKinetics(0.0, 60.0));
    assertThrows(IllegalArgumentException.class, () -> study.guessKinetics(1e200, 60.0));
    assertThrows(IllegalArgumentException.class, () -> study.calibratedKinetics(1e-3, -1.0));
    assertThrows(IllegalArgumentException.class, () -> study.run(-1.0, 60.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> study.run(1e-5, 60.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> study.run(1e-5, 100001.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> study.run(Double.MAX_VALUE, 60.0, 1.0));
  }

  /**
   * Build an independent three-phase calculation for the adapter-ownership check.
   *
   * @return calculated gas/oil/water separator
   */
  private ThreePhaseSeparator calculatedSeparator() {
    SystemInterface fluid = new SystemSrkCPAstatoil(315.15, 10.0);
    fluid.addComponent("methane", 0.72);
    fluid.addComponent("n-heptane", 0.14);
    fluid.addComponent("water", 0.40);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("study feed", fluid);
    feed.setFlowRate(720.0, "kg/hr");
    feed.run();
    ThreePhaseSeparator separator = new ThreePhaseSeparator("study separator", feed);
    separator.run();
    assertTrue(separator.getThermoSystem().hasPhaseType("oil"));
    assertTrue(separator.getThermoSystem().hasPhaseType("aqueous"));
    return separator;
  }
}
