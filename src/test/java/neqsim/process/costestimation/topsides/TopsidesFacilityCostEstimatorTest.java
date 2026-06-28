package neqsim.process.costestimation.topsides;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.costestimation.CostEstimateBasis;
import neqsim.process.costestimation.CostEstimateResult;
import neqsim.process.costestimation.EstimateClass;
import neqsim.process.costestimation.topsides.TopsidesFacilityCostEstimator.FacilityType;
import neqsim.process.costestimation.topsides.TopsidesFacilityCostEstimator.ProjectContext;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for topsides facility cost estimation.
 *
 * @author esol
 * @version 1.0
 */
class TopsidesFacilityCostEstimatorTest {

  /**
   * Builds a small topsides gas process for cost-estimator tests.
   *
   * @return process system with separation, compression, cooling, and pressure control
   */
  private ProcessSystem buildTopsidesProcess() {
    SystemInterface gas = new SystemSrkEos(288.15, 55.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();
    process.setName("Topside Gas Train");

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

    Cooler afterCooler = new Cooler("Aftercooler", compressor.getOutletStream());
    afterCooler.setOutTemperature(35.0, "C");
    process.add(afterCooler);

    ThrottlingValve exportValve = new ThrottlingValve("Export Control Valve", afterCooler.getOutletStream());
    exportValve.setOutletPressure(100.0);
    process.add(exportValve);

    process.run();
    return process;
  }

  /**
   * Verifies that a fixed-platform estimate includes facility bulk and project cost stacks.
   */
  @Test
  void testFixedPlatformEstimateIncludesBulkAndProjectStack() {
    CostEstimateBasis basis = new CostEstimateBasis().setEstimateClass(EstimateClass.CLASS_3).setLocationFactor(1.35)
        .setLocationBasis("North Sea");
    TopsidesFacilityCostEstimator estimator = new TopsidesFacilityCostEstimator(buildTopsidesProcess())
        .setFacilityType(FacilityType.FIXED_PLATFORM).setProjectContext(ProjectContext.GREENFIELD)
        .setEstimateBasis(basis);

    CostEstimateResult result = estimator.estimate();
    Map<String, Double> capitalCosts = result.getCapitalCosts();
    Map<String, Double> projectCosts = result.getProjectCosts();

    assertTrue(capitalCosts.get("processEquipmentModules") > 0.0, "Process module cost should be positive");
    assertTrue(capitalCosts.get("structuralSteel") > 0.0, "Structural steel cost should be positive");
    assertTrue(capitalCosts.get("pipingBulk") > 0.0, "Piping bulk cost should be positive");
    assertTrue(projectCosts.get("totalTopsidesCapex") > capitalCosts.get("directFieldCost"),
        "Total topsides CAPEX should include project costs beyond direct field cost");
    assertTrue(result.getMaterialTakeOff().size() >= 4, "Result should include equipment and bulk MTO lines");
    assertTrue(result.toJson().contains("module.separationAndTreatmentModules"),
        "JSON should include module-level cost lines");
  }

  /**
   * Verifies that brownfield estimates carry a scope quality flag.
   */
  @Test
  void testBrownfieldEstimateAddsCongestionQualityFlag() {
    TopsidesFacilityCostEstimator estimator = new TopsidesFacilityCostEstimator(buildTopsidesProcess())
        .setFacilityType(FacilityType.BROWNFIELD_TIE_IN).setProjectContext(ProjectContext.HOST_TIE_IN);

    CostEstimateResult result = estimator.estimate();
    Map<String, Object> resultMap = result.toMap();
    Object flags = resultMap.get("qualityFlags");

    assertNotNull(flags, "Quality flags should be present");
    assertTrue(flags instanceof List<?>, "Quality flags should be a list");
    assertTrue(flags.toString().contains("Brownfield"), "Brownfield estimate should flag brownfield assumptions");
  }
}
