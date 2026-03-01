package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.subsea.SubseaWell;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for WellMechanicalDesign, WellDesignCalculator, and WellCostEstimator.
 *
 * @author ESOL
 */
public class WellMechanicalDesignTest {

  private SubseaWell well;
  private SystemInterface fluid;
  private Stream stream;

  @BeforeEach
  public void setUp() {
    fluid = new SystemSrkEos(273.15 + 80.0, 200.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("nC4", 0.03);
    fluid.addComponent("CO2", 0.02);
    fluid.setMixingRule("classic");

    stream = new Stream("reservoir stream", fluid);
    stream.setFlowRate(50000.0, "kg/hr");
    stream.setTemperature(80.0, "C");
    stream.setPressure(200.0, "bara");

    well = new SubseaWell("Test Well", stream);
    well.setWellType(SubseaWell.WellType.OIL_PRODUCER);
    well.setCompletionType(SubseaWell.CompletionType.CASED_PERFORATED);
    well.setRigType(SubseaWell.RigType.SEMI_SUBMERSIBLE);
    well.setMeasuredDepth(3800.0);
    well.setTrueVerticalDepth(3200.0);
    well.setWaterDepth(350.0);
    well.setMaxWellheadPressure(345.0);
    well.setReservoirPressure(400.0);
    well.setReservoirTemperature(100.0);
    well.setMaxBottomholeTemperature(120.0);

    well.setConductorOD(30.0);
    well.setConductorDepth(100.0);
    well.setSurfaceCasingOD(20.0);
    well.setSurfaceCasingDepth(800.0);
    well.setIntermediateCasingOD(13.375);
    well.setIntermediateCasingDepth(2500.0);
    well.setProductionCasingOD(9.625);
    well.setProductionCasingDepth(3800.0);
    well.setTubingOD(5.5);
    well.setTubingWeight(23.0);
    well.setTubingGrade("L80");

    well.setDrillingDays(45.0);
    well.setCompletionDays(25.0);
    well.setRigDayRate(540000.0);
    well.setHasDHSV(true);
    well.setPrimaryBarrierElements(3);
    well.setSecondaryBarrierElements(3);
  }

  @Test
  public void testInitMechanicalDesign() {
    well.initMechanicalDesign();
    assertNotNull(well.getMechanicalDesign());
    assertTrue(well.getMechanicalDesign() instanceof WellMechanicalDesign);
  }

  @Test
  public void testCalcDesign() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
    design.calcDesign();

    // Casing wall thicknesses must be positive
    assertTrue(design.getProductionCasingWallThickness() > 0,
        "Production casing wall thickness should be positive");
    assertTrue(design.getTotalCasingWeight() > 0, "Total casing weight should be positive");
    assertTrue(design.getTotalTubingWeight() > 0, "Total tubing weight should be positive");
    assertTrue(design.getTotalCementVolume() > 0, "Total cement volume should be positive");
  }

  @Test
  public void testDesignFactors() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
    design.calcDesign();

    // Design factors must meet minimum requirements
    assertTrue(design.getProductionCasingBurstDF() >= 1.10,
        "Production casing burst DF must be >= 1.10 (NORSOK D-010)");
    assertTrue(design.getProductionCasingCollapseDF() >= 1.00,
        "Production casing collapse DF must be >= 1.00");
    assertTrue(design.getProductionCasingTensionDF() >= 1.60,
        "Production casing tension DF must be >= 1.60");
  }

  @Test
  public void testWellBarrierVerification() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
    design.calcDesign();

    // With proper barriers configured, verification should pass
    assertTrue(design.isBarrierVerificationPassed(),
        "Barrier verification should pass with proper configuration");
    assertNotNull(design.getBarrierNotes());
    assertTrue(design.getBarrierNotes().size() > 0, "Should have barrier verification notes");
  }

  @Test
  public void testBarrierVerificationFails() {
    well.setPrimaryBarrierElements(1);
    well.setSecondaryBarrierElements(1);
    well.setHasDHSV(false);

    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
    design.calcDesign();

    assertTrue(!design.isBarrierVerificationPassed(),
        "Barrier verification should fail with insufficient barriers");
  }

  @Test
  public void testCostEstimate() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
    design.calculateCostEstimate();

    assertTrue(design.getTotalCostUSD() > 0, "Total cost should be positive");
    assertTrue(design.getDrillingCostUSD() > 0, "Drilling cost should be positive");
    assertTrue(design.getCompletionCostUSD() > 0, "Completion cost should be positive");
    assertTrue(design.getWellheadCostUSD() > 0, "Wellhead cost should be positive");
    assertTrue(design.getLoggingCostUSD() > 0, "Logging cost should be positive");

    // Drilling should be the largest component
    assertTrue(design.getDrillingCostUSD() > design.getWellheadCostUSD(),
        "Drilling cost should be larger than wellhead cost");
  }

  @Test
  public void testCostBreakdown() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
    design.calculateCostEstimate();

    Map<String, Object> breakdown = design.getCostBreakdown();
    assertNotNull(breakdown);
    assertTrue(breakdown.containsKey("totalCost"));
    assertTrue(breakdown.containsKey("drilling"));
    assertTrue(breakdown.containsKey("completion"));
    assertTrue(breakdown.containsKey("evaluation"));
    assertTrue(breakdown.containsKey("contingencyPct"));
  }

  @Test
  public void testBillOfMaterials() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();

    List<Map<String, Object>> bom = design.generateBillOfMaterials();
    assertNotNull(bom);
    assertTrue(bom.size() >= 5,
        "BOM should have at least 5 items (casings, tubing, wellhead, DHSV, cement)");

    // Check BOM item structure
    Map<String, Object> firstItem = bom.get(0);
    assertTrue(firstItem.containsKey("name"));
    assertTrue(firstItem.containsKey("description"));
    assertTrue(firstItem.containsKey("category"));
    assertTrue(firstItem.containsKey("quantity"));
    assertTrue(firstItem.containsKey("unit"));
  }

  @Test
  public void testToJson() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
    design.calcDesign();
    design.calculateCostEstimate();

    String json = design.toJson();
    assertNotNull(json);
    assertTrue(json.contains("SubseaWell"));
    assertTrue(json.contains("designResults"));
    assertTrue(json.contains("casingProgram"));
    assertTrue(json.contains("costEstimation"));
    assertTrue(json.contains("barrierVerification"));
  }

  @Test
  public void testToMap() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
    design.calcDesign();
    design.calculateCostEstimate();

    Map<String, Object> map = design.toMap();
    assertNotNull(map);
    assertEquals("SubseaWell", map.get("equipmentType"));
    assertEquals("OIL_PRODUCER", map.get("wellType"));
    assertTrue(map.containsKey("geometry"));
    assertTrue(map.containsKey("casingDesign"));
    assertTrue(map.containsKey("weights"));
  }

  // ============ WellDesignCalculator Tests ============

  @Test
  public void testDesignCalculatorDirectly() {
    WellDesignCalculator calc = new WellDesignCalculator();
    calc.setMeasuredDepth(3800.0);
    calc.setTrueVerticalDepth(3200.0);
    calc.setWaterDepth(350.0);
    calc.setMaxWellheadPressure(345.0);
    calc.setReservoirPressure(400.0);
    calc.setReservoirTemperature(100.0);
    calc.setMaxBottomholeTemperature(120.0);

    calc.setConductorCasing(30.0, 100.0);
    calc.setSurfaceCasing(20.0, 800.0);
    calc.setIntermediateCasing(13.375, 2500.0);
    calc.setProductionCasing(9.625, 3800.0);
    calc.setTubing(5.5, 23.0, "L80");

    calc.calculateCasingDesign();
    calc.calculateTubingDesign();
    calc.calculateWeights();
    calc.calculateCementVolumes();

    assertTrue(calc.getProductionCasingWallThickness() > 5.0,
        "Wall thickness should be realistic (> 5mm)");
    assertTrue(calc.getProductionCasingWallThickness() < 30.0,
        "Wall thickness should be realistic (< 30mm)");
    assertTrue(calc.getTotalCasingWeight() > 10.0,
        "Casing weight should be > 10 tonnes for a 3800m well");
    assertTrue(calc.getTotalCementVolume() > 0, "Cement volume should be positive");
    assertTrue(calc.getTotalCuttingsVolume() > 0, "Cuttings volume should be positive");
  }

  @Test
  public void testCalculatorToMap() {
    WellDesignCalculator calc = new WellDesignCalculator();
    calc.setProductionCasing(9.625, 3800.0);
    calc.setMaxWellheadPressure(345.0);
    calc.setReservoirPressure(400.0);
    calc.calculateCasingDesign();

    Map<String, Object> map = calc.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("casingDesign"));
    assertTrue(map.containsKey("tubingDesign"));
    assertTrue(map.containsKey("weights"));
  }

  // ============ WellCostEstimator Tests ============

  @Test
  public void testCostEstimatorOilProducer() {
    WellCostEstimator estimator = new WellCostEstimator();
    estimator.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", 3800.0,
        350.0, 45.0, 25.0, 0.0, true, 4);

    assertTrue(estimator.getTotalCost() > 0, "Total cost should be positive");
    assertTrue(estimator.getDrillingCost() > 0, "Drilling cost should be positive");
    assertTrue(estimator.getCompletionCost() > 0, "Completion cost should be positive");
    assertTrue(estimator.getWellheadCost() > 0, "Wellhead cost should be positive");
  }

  @Test
  public void testCostEstimatorWaterInjector() {
    WellCostEstimator estimator = new WellCostEstimator();
    estimator.calculateWellCost("WATER_INJECTOR", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", 3500.0,
        350.0, 35.0, 15.0, 0.0, true, 4);

    assertTrue(estimator.getTotalCost() > 0, "Total cost should be positive");
  }

  @Test
  public void testProducerCostsMoreThanInjector() {
    WellCostEstimator producerEst = new WellCostEstimator();
    producerEst.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", 3800.0,
        350.0, 45.0, 25.0, 0.0, true, 4);

    WellCostEstimator injectorEst = new WellCostEstimator();
    injectorEst.calculateWellCost("WATER_INJECTOR", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", 3800.0,
        350.0, 35.0, 15.0, 0.0, true, 4);

    assertTrue(producerEst.getTotalCost() > injectorEst.getTotalCost(),
        "Oil producer should cost more than water injector");
  }

  @Test
  public void testRegionalCostVariation() {
    WellCostEstimator norwayEst = new WellCostEstimator(SubseaCostEstimator.Region.NORWAY);
    norwayEst.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", 3800.0,
        350.0, 45.0, 25.0, 0.0, true, 4);

    WellCostEstimator gomEst = new WellCostEstimator(SubseaCostEstimator.Region.GOM);
    gomEst.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", 3800.0, 350.0,
        45.0, 25.0, 0.0, true, 4);

    assertTrue(norwayEst.getTotalCost() > gomEst.getTotalCost(),
        "Norway wells should cost more than GOM wells");
  }

  @Test
  public void testCostEstimatorToJson() {
    WellCostEstimator estimator = new WellCostEstimator();
    estimator.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", 3800.0,
        350.0, 45.0, 25.0, 0.0, true, 4);

    String json = estimator.toJson();
    assertNotNull(json);
    assertTrue(json.contains("totalCost"));
    assertTrue(json.contains("drilling"));
    assertTrue(json.contains("completion"));
  }

  @Test
  public void testCompletionTypeFactor() {
    WellCostEstimator simpleEst = new WellCostEstimator();
    simpleEst.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "OPEN_HOLE", 3800.0, 350.0,
        45.0, 25.0, 0.0, true, 4);

    WellCostEstimator complexEst = new WellCostEstimator();
    complexEst.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "MULTI_ZONE", 3800.0, 350.0,
        45.0, 25.0, 0.0, true, 4);

    assertTrue(complexEst.getCompletionCost() > simpleEst.getCompletionCost(),
        "Multi-zone completion should cost more than open hole");
  }

  // ============ Integration Test ============

  @Test
  public void testFullDesignAndCostWorkflow() {
    well.initMechanicalDesign();
    WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();

    // Run design
    design.calcDesign();

    // Run cost
    design.calculateCostEstimate();

    // Verify everything works together
    assertTrue(design.getProductionCasingWallThickness() > 0);
    assertTrue(design.getTotalCostUSD() > 0);
    assertTrue(design.isBarrierVerificationPassed());

    // Full JSON report
    String json = design.toJson();
    assertNotNull(json);
    assertTrue(json.length() > 100, "JSON report should be comprehensive");
  }

  @Test
  public void testIsProducer() {
    assertTrue(well.isProducer(), "OIL_PRODUCER should be a producer");

    well.setWellType(SubseaWell.WellType.WATER_INJECTOR);
    assertTrue(well.isInjector(), "WATER_INJECTOR should be an injector");
    assertTrue(!well.isProducer(), "WATER_INJECTOR should not be a producer");
  }
}
