package neqsim.process.mechanicaldesign.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for coupled impeller diameter, speed, head and inlet flow sizing. */
class CompressorImpellerSizingTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(CompressorImpellerSizingTest.class);

  @Test
  void adjustedFlowCasePreservesTipSpeedIdentity() {
    Compressor compressor = runCompressor(6000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertEquals(Math.PI * design.getImpellerDiameter() / 1000.0 * compressor.getSpeed() / 60.0, design.getTipSpeed(),
        1e-10);
    assertHeadAndFlowIdentity(compressor);
    assertFalse(design.isImpellerSizingFeasible());
    assertTrue(design.getFlowCoefficient() < 0.01);
    assertTrue(design.getImpellerSizingIssues().stream().anyMatch(issue -> issue.contains("flow coefficient")));
    assertFalse(design.validateDesign().isValid());
    assertTrue(design.validateDesign().getIssues().containsAll(design.getImpellerSizingIssues()));
    logger.info("6000 rpm: stages={}, diameter={} mm, tip={} m/s, head={} kJ/kg, phi={}, feasible={}",
        design.getNumberOfStages(), design.getImpellerDiameter(), design.getTipSpeed(),
        compressor.getPolytropicFluidHead(), design.getFlowCoefficient(), design.isImpellerSizingFeasible());
  }

  @Test
  void teachingCaseSatisfiesAllCoupledLimitsAndExportsQualification() {
    Compressor compressor = runCompressor(18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertEquals(8, design.getNumberOfStages());
    assertQualified(compressor);
    JsonObject legacy = JsonParser.parseString(design.toJson()).getAsJsonObject();
    assertTrue(legacy.get("impellerSizingFeasible").getAsBoolean());
    assertEquals(0, legacy.getAsJsonArray("impellerSizingIssues").size());
    assertEquals(design.getFlowCoefficient(), legacy.get("flowCoefficient").getAsDouble(), 1e-12);
    assertEquals(18000.0, legacy.get("impellerSizingSpeedRPM").getAsDouble(), 1e-12);
    assertEquals(compressor.getPolytropicFluidHead(), legacy.get("totalHead").getAsDouble(), 1e-10);
    JsonObject cad = JsonParser.parseString(design.toDesignDataJson()).getAsJsonObject();
    assertTrue(cad.get("impellerSizingFeasible").getAsBoolean());
    assertEquals(0, cad.getAsJsonArray("impellerSizingIssues").size());
    JsonObject basis = cad.getAsJsonObject("designBasis");
    assertEquals("rpm", basis.getAsJsonObject("impellerSizingSpeed").get("unit").getAsString());
    assertEquals("J/kg", basis.getAsJsonObject("headPerStage").get("unit").getAsString());
    double diameterM = cad.getAsJsonObject("geometry").getAsJsonObject("impellerDiameter").get("value").getAsDouble();
    double speed = basis.getAsJsonObject("impellerSizingSpeed").get("value").getAsDouble();
    double tip = basis.getAsJsonObject("impellerTipSpeed").get("value").getAsDouble();
    assertEquals(Math.PI * diameterM * speed / 60.0, tip, 1e-10);
    logger.info("18000 rpm: stages={}, diameter={} mm, tip={} m/s, phi={}, feasible={}", design.getNumberOfStages(),
        design.getImpellerDiameter(), design.getTipSpeed(), design.getFlowCoefficient(),
        design.isImpellerSizingFeasible());
  }

  @Test
  void addingAStageCanSatisfyTheInletFlowLimitWithoutChangingSpeedOrHead() {
    double initialTip = Math.sqrt(30000.0 / 0.5);
    double initialDiameter = initialTip * 60.0 / (Math.PI * 6000.0);
    double flowM3s = 0.009 * initialDiameter * initialDiameter * initialTip;
    Compressor compressor = specifiedHeadCompressor(240.0, flowM3s, 6000.0);
    compressor.getMechanicalDesign().calcDesign();
    assertEquals(9, compressor.getMechanicalDesign().getNumberOfStages());
    assertEquals(6000.0, compressor.getSpeed(), 0.0);
    assertQualified(compressor);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 2000.0, 100000.0 })
  void diameterBoundsAreReportedWithoutClippingAndConcealingHeadErrors(double speed) {
    Compressor compressor = specifiedHeadCompressor(240.0, 1.0, speed);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertFalse(design.isImpellerSizingFeasible());
    assertTrue(design.getImpellerSizingIssues().stream().anyMatch(issue -> issue.contains("diameter")));
    assertTrue(design.getImpellerDiameter() < 100.0 || design.getImpellerDiameter() > 1500.0);
    assertHeadAndFlowIdentity(compressor);
  }

  @Test
  void flowAboveTheUpperLimitIsInfeasible() {
    Compressor compressor = specifiedHeadCompressor(240.0, 10.0, 18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertFalse(design.isImpellerSizingFeasible());
    assertTrue(design.getFlowCoefficient() > 0.15);
    assertHeadAndFlowIdentity(compressor);
  }

  @Test
  void stageLimitAndRepeatedSizingDoNotRetainAFeasibleStatus() {
    Compressor compressor = runCompressor(18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertQualified(compressor);
    design.setMaxStagesPerCasing(7);
    assertFalse(design.isImpellerSizingFeasible());
    design.calcDesign();
    assertTrue(design.getImpellerSizingIssues().stream().anyMatch(issue -> issue.contains("stage count")));
    assertHeadAndFlowIdentity(compressor);
    design.setMaxStagesPerCasing(10);
    compressor.setSpeed(6000.0);
    assertFalse(design.isImpellerSizingFeasible());
    assertTrue(design.getImpellerSizingIssues().stream().anyMatch(issue -> issue.contains("changed after sizing")));
    design.calcDesign();
    assertFalse(design.isImpellerSizingFeasible());
    compressor.setSpeed(18000.0);
    design.calcDesign();
    assertQualified(compressor);
    design.setImpellerDiameter(500.0);
    assertFalse(design.isImpellerSizingFeasible());
    design.calcDesign();
    assertQualified(compressor);
    design.setNumberOfStages(1);
    assertFalse(design.isImpellerSizingFeasible());
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY })
  void invalidSpeedDoesNotReturnAQualifiedDefaultDiameter(double speed) {
    Compressor compressor = runCompressor(18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertQualified(compressor);
    compressor.setSpeed(speed);
    design.calcDesign();
    assertFalse(design.isImpellerSizingFeasible());
    assertTrue(Double.isNaN(design.getImpellerDiameter()));
    assertTrue(design.getImpellerSizingIssues().stream().anyMatch(issue -> issue.contains("finite positive")));
    JsonObject json = JsonParser.parseString(design.toJson()).getAsJsonObject();
    assertFalse(json.get("impellerSizingFeasible").getAsBoolean());
    assertTrue(json.get("impellerDiameter").isJsonNull());
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY })
  void invalidHeadIsUnavailable(double head) {
    Compressor compressor = specifiedHeadCompressor(head, 1.0, 18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertFalse(design.isImpellerSizingFeasible());
    assertTrue(Double.isNaN(design.getTipSpeed()));
  }

  @Test
  void changedAndZeroInletFlowInvalidateSizing() {
    Compressor compressor = runCompressor(18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    assertFalse(design.isImpellerSizingFeasible());
    design.calcDesign();
    assertQualified(compressor);
    compressor.getInletStream().setFlowRate(1.0, "kg/hr");
    assertFalse(design.isImpellerSizingFeasible());
    design.calcDesign();
    assertFalse(design.isImpellerSizingFeasible());
    compressor.getInletStream().setFlowRate(0.0, "kg/hr");
    design.calcDesign();
    assertFalse(design.isImpellerSizingFeasible());
    assertTrue(Double.isNaN(design.getImpellerDiameter()));
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY })
  void invalidSizingClearsDependentResultsAndRecovers(double speed) {
    Compressor compressor = runCompressor(18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertQualified(compressor);
    double diameter = design.getImpellerDiameter();
    double weight = design.getWeightTotal();
    double driverPower = design.getDriverPower();
    compressor.setSpeed(speed);
    design.calcDesign();
    assertUnavailableMechanicalResults(design);
    JsonObject legacy = JsonParser.parseString(design.toJson()).getAsJsonObject();
    for (String field : new String[] { "shaftDiameter", "bearingSpan", "headPerStage", "driverPower",
        "firstCriticalSpeed", "maxContinuousSpeed", "tripSpeed", "rotorWeight", "casingWeight", "bundleWeight",
        "innerDiameter", "outerDiameter", "wallThickness", "tangentLength", "totalWeight", "moduleLength",
        "moduleWidth", "moduleHeight", "casingDesign" }) {
      assertTrue(legacy.get(field).isJsonNull(), field + " must not retain a previous sizing result");
    }
    JsonObject cad = JsonParser.parseString(design.toDesignDataJson()).getAsJsonObject();
    assertEquals("incomplete", cad.get("geometryConsistency").getAsString());
    for (String field : new String[] { "impellerDiameter", "shaftDiameter", "bearingSpan", "innerDiameter",
        "outerDiameter", "wallThickness" }) {
      assertTrue(cad.getAsJsonObject("geometry").getAsJsonObject(field).get("value").isJsonNull(), field);
    }
    compressor.setSpeed(18000.0);
    design.calcDesign();
    assertQualified(compressor);
    assertEquals(diameter, design.getImpellerDiameter(), 1e-10);
    assertEquals(weight, design.getWeightTotal(), 1e-10);
    assertEquals(driverPower, design.getDriverPower(), 1e-10);
  }

  @Test
  void refreshedResponseDropsThePreviousCasingCalculation() {
    Compressor compressor = runCompressor(18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    CompressorMechanicalDesignResponse response = new CompressorMechanicalDesignResponse(design);
    assertFalse(JsonParser.parseString(response.toJson()).getAsJsonObject().get("casingDesign").isJsonNull());
    compressor.setSpeed(0.0);
    design.calcDesign();
    response.populateFromCompressorDesign(design);
    assertTrue(JsonParser.parseString(response.toJson()).getAsJsonObject().get("casingDesign").isJsonNull());
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  void missingEquipmentCannotRetainQualificationOrPreviousGeometry(boolean detached) {
    Compressor compressor = runCompressor(18000.0);
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    design.calcDesign();
    assertQualified(compressor);
    Compressor replacement = new Compressor("uninitialized compressor");
    replacement.setSpeed(18000.0);
    design.setProcessEquipment(detached ? null : replacement);
    assertFalse(design.isImpellerSizingFeasible());
    assertFalse(design.validateDesign().isValid());
    design.calcDesign();
    assertUnavailableMechanicalResults(design);
    assertTrue(Double.isNaN(design.getImpellerDiameter()));
  }

  private void assertUnavailableMechanicalResults(CompressorMechanicalDesign design) {
    assertFalse(design.isImpellerSizingFeasible());
    assertEquals(0, design.getNumberOfStages());
    assertNull(design.getCasingDesignCalculator());
    assertFalse(design.validateDesign().getIssues().toString().contains("Infinity"));
    for (double value : new double[] { design.getShaftDiameter(), design.getBearingSpan(), design.getHeadPerStage(),
        design.getDriverPower(), design.getPower(), design.getFirstCriticalSpeed(), design.getMaxContinuousSpeed(),
        design.getTripSpeed(), design.getRotorWeight(), design.getCasingWeight(), design.getBundleWeight(),
        design.getInnerDiameter(), design.getOuterDiameter(), design.getWallThickness(), design.getTantanLength(),
        design.getWeightTotal(), design.getWeigthVesselShell(), design.getWeigthInternals(), design.getWeightNozzle(),
        design.getWeightPiping(), design.getWeightElectroInstrument(), design.getWeightStructualSteel(),
        design.getModuleLength(), design.getModuleWidth(), design.getModuleHeight() }) {
      assertTrue(Double.isNaN(value), "Unavailable sizing must clear all dependent results, found " + value);
    }
  }

  @Test
  void infeasibilityReachesBothJsonExportsAndTheFeasibilityReport() {
    Compressor compressor = runCompressor(6000.0);
    CompressorDesignFeasibilityReport report = new CompressorDesignFeasibilityReport(compressor);
    report.setGenerateCurves(false);
    report.generateReport();
    assertEquals("NOT_FEASIBLE", report.getVerdict());
    assertTrue(report.getIssues().stream().anyMatch(issue -> "IMPELLER_SIZING".equals(issue.getCategory())
        && issue.getSeverity() == CompressorDesignFeasibilityReport.IssueSeverity.BLOCKER));
    CompressorMechanicalDesign design = report.getMechanicalDesign();
    for (String export : new String[] { design.toJson(), design.toDesignDataJson() }) {
      JsonObject json = JsonParser.parseString(export).getAsJsonObject();
      assertFalse(json.get("impellerSizingFeasible").getAsBoolean());
      assertTrue(json.getAsJsonArray("impellerSizingIssues").size() > 0);
    }
  }

  @Test
  void unavailableImpellerValuesRemainSerializableInTheFeasibilityReport() {
    Compressor compressor = runCompressor(18000.0);
    compressor.setSpeed(Double.NaN);
    CompressorDesignFeasibilityReport report = new CompressorDesignFeasibilityReport(compressor);
    report.setGenerateCurves(false);
    report.generateReport();
    assertEquals("NOT_FEASIBLE", report.getVerdict());
    String json = report.toJson();
    assertFalse(json.contains("NaN"));
    assertFalse(json.contains("Infinity"));
    JsonObject mechanical = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("mechanicalDesign");
    assertFalse(mechanical.get("impellerSizingFeasible").getAsBoolean());
    assertTrue(mechanical.get("impellerDiameter_mm").isJsonNull());
    assertTrue(mechanical.get("inletFlowCoefficient").isJsonNull());
  }

  private void assertQualified(Compressor compressor) {
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    assertTrue(design.isImpellerSizingFeasible(), design.getImpellerSizingIssues().toString());
    assertTrue(design.getImpellerDiameter() >= 100.0 && design.getImpellerDiameter() <= 1500.0);
    assertTrue(design.getTipSpeed() > 0.0 && design.getTipSpeed() <= 350.0);
    assertTrue(design.getHeadPerStage() > 0.0 && design.getHeadPerStage() <= 30.0);
    assertTrue(design.getNumberOfStages() <= design.getMaxStagesPerCasing());
    assertTrue(design.getFlowCoefficient() >= 0.01 && design.getFlowCoefficient() <= 0.15);
    assertHeadAndFlowIdentity(compressor);
  }

  private void assertHeadAndFlowIdentity(Compressor compressor) {
    CompressorMechanicalDesign design = compressor.getMechanicalDesign();
    double diameterM = design.getImpellerDiameter() / 1000.0;
    double tip = Math.PI * diameterM * compressor.getSpeed() / 60.0;
    assertEquals(tip, design.getTipSpeed(), 1e-10);
    double achievedHeadPerStage = 0.5 * tip * tip / 1000.0;
    assertEquals(achievedHeadPerStage, design.getHeadPerStage(), 1e-10);
    assertEquals(compressor.getPolytropicFluidHead(), achievedHeadPerStage * design.getNumberOfStages(), 1e-9);
    double flowM3s = compressor.getInletStream().getFlowRate("m3/hr") / 3600.0;
    assertEquals(flowM3s / (diameterM * diameterM * tip), design.getFlowCoefficient(), 1e-12);
  }

  /** Supply independent head and flow inputs to isolate the preliminary sizing equations. */
  private Compressor specifiedHeadCompressor(final double head, double flowM3s, double speed) {
    SystemInterface fluid = new SystemSrkEos(313.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("inlet", fluid);
    inlet.run();
    inlet.setFlowRate(flowM3s * 3600.0, "m3/hr");
    inlet.run();
    Compressor compressor = new Compressor("specified-head compressor", inlet) {
      private static final long serialVersionUID = 1L;

      @Override
      public double getPolytropicFluidHead() {
        return head;
      }
    };
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    compressor.setOutletPressure(40.0, "bara");
    compressor.setSpeed(speed);
    compressor.run();
    return compressor;
  }

  private Compressor runCompressor(double speed) {
    SystemInterface fluid = new SystemSrkEos(313.15, 20.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-decane", 0.10);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    Separator separator = new Separator("separator", feed);
    separator.setOrientation("horizontal");
    Compressor compressor = new Compressor("compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setPolytropicEfficiency(0.78);
    compressor.setUsePolytropicCalc(true);
    compressor.setSpeed(speed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.run();
    return compressor;
  }
}
