package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.costestimation.CostEstimateResult;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.fielddevelopment.FieldDevelopmentCostEstimator.ConceptType;
import neqsim.process.util.fielddevelopment.FieldDevelopmentCostEstimator.FidelityLevel;
import neqsim.process.util.fielddevelopment.FieldDevelopmentCostEstimator.FieldDevelopmentCostReport;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for field-development CAPEX integration.
 *
 * @author esol
 * @version 1.0
 */
class FieldDevelopmentCostEstimatorTest {

  /**
   * Builds a simple field-development topsides process.
   *
   * @return process system
   */
  private ProcessSystem buildFacility() {
    SystemInterface gas = new SystemSrkEos(288.15, 55.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();
    process.setName("Field Development Topsides");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(25000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(55.0, "bara");
    process.add(feed);

    Separator inletSeparator = new Separator("Inlet Separator", feed);
    process.add(inletSeparator);

    Compressor compressor = new Compressor("Export Compressor", inletSeparator.getGasOutStream());
    compressor.setOutletPressure(120.0);
    process.add(compressor);

    Cooler cooler = new Cooler("Aftercooler", compressor.getOutletStream());
    cooler.setOutTemperature(35.0, "C");
    process.add(cooler);

    ThrottlingValve valve = new ThrottlingValve("Export Valve", cooler.getOutletStream());
    valve.setOutletPressure(100.0);
    process.add(valve);

    process.run();
    return process;
  }

  /**
   * Verifies that field-development reports expose detailed topsides CAPEX.
   */
  @Test
  void testDevelopmentReportIncludesTopsidesDetailedEstimate() {
    FieldDevelopmentCostEstimator estimator = new FieldDevelopmentCostEstimator(buildFacility());
    estimator.setFidelityLevel(FidelityLevel.PRE_FEED);
    estimator.setConceptType(ConceptType.FPSO);
    estimator.setLocationFactor(1.25);

    FieldDevelopmentCostReport report = estimator.estimateDevelopmentCosts();
    CostEstimateResult topsides = report.getTopsidesDetailedEstimateResult();

    assertNotNull(topsides, "Detailed topsides estimate should be available");
    assertTrue(topsides.getProjectCostSummary().get("totalTopsidesCapex") > 0.0,
        "Detailed topsides CAPEX should be positive");
    assertTrue(report.getFacilitiesCapex() == topsides.getProjectCostSummary().get("totalTopsidesCapex"),
        "Facilities CAPEX should be driven by detailed topsides total");
    assertTrue(report.toJson().contains("topsidesDetailedEstimateResult"),
        "JSON should expose detailed topsides estimate");
    assertTrue(report.toJson().contains("totalTopsidesCapex"), "JSON should expose total topsides CAPEX");
  }

  /**
   * Verifies that field-development summaries use the detailed topsides physical and cost basis.
   */
  @Test
  void testDevelopmentReportReconcilesTopsidesPhysicalAndCostBasis() {
    FieldDevelopmentCostEstimator estimator = new FieldDevelopmentCostEstimator(buildFacility());
    estimator.setFidelityLevel(FidelityLevel.PRE_FEED);
    estimator.setConceptType(ConceptType.FPSO);
    estimator.setLocationFactor(1.25);

    FieldDevelopmentCostReport report = estimator.estimateDevelopmentCosts();
    CostEstimateResult topsides = report.getTopsidesDetailedEstimateResult();

    assertEquals(topsides.getWeightBasis().get("totalEstimatedDryWeight"), report.getTotalWeight(),
        topsides.getWeightBasis().get("totalEstimatedDryWeight") * 1.0e-10,
        "Report total weight should use the detailed topsides dry-weight basis");
    assertTrue(report.getCostByCategory().containsKey("topsides.processEquipmentModules"),
        "Top-level category breakdown should expose detailed topsides cost categories");
    for (String category : report.getCostByCategory().keySet()) {
      assertTrue(!category.startsWith("topsides.module."),
          "Top-level category breakdown should not expose additive topsides module detail lines");
    }
    assertTrue(!report.getCostByCategory().containsKey("topsides.directFieldCost"),
        "Top-level category breakdown should not expose topsides subtotal rows");
    assertTrue(report.getEquipmentCostByCategory().containsKey("Separator"),
        "Equipment-only category breakdown should remain available separately");
    assertTrue(report.toJson().contains("equipmentCostByCategory"),
        "JSON should keep equipment-only category breakdown explicit");
  }

  /**
   * Verifies that concept comparison keeps subsea well-count and depth assumptions.
   */
  @Test
  void testCompareConceptCostsPropagatesWellParameters() {
    FieldDevelopmentCostEstimator directEstimator = new FieldDevelopmentCostEstimator(buildFacility());
    directEstimator.setConceptType(ConceptType.SUBSEA_TIEBACK);
    directEstimator.setSubseaParameters(25.0, 350.0);
    directEstimator.setWellParameters(4, 1, 4200.0);
    FieldDevelopmentCostReport directReport = directEstimator.estimateDevelopmentCosts();

    FieldDevelopmentCostEstimator comparisonEstimator = new FieldDevelopmentCostEstimator(buildFacility());
    comparisonEstimator.setConceptType(ConceptType.SUBSEA_TIEBACK);
    comparisonEstimator.setSubseaParameters(25.0, 350.0);
    comparisonEstimator.setWellParameters(4, 1, 4200.0);
    FieldDevelopmentCostReport comparisonReport = comparisonEstimator
        .compareConceptCosts(Collections.singletonList(buildFacility())).get(0);

    assertEquals(directReport.getSubseaCapex(), comparisonReport.getSubseaCapex(),
        directReport.getSubseaCapex() * 1.0e-10, "Concept comparison should preserve well parameters in subsea CAPEX");
  }
}
