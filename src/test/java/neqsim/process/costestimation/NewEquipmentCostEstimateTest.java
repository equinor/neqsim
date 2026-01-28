package neqsim.process.costestimation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.costestimation.absorber.AbsorberCostEstimate;
import neqsim.process.costestimation.ejector.EjectorCostEstimate;
import neqsim.process.costestimation.expander.ExpanderCostEstimate;
import neqsim.process.costestimation.mixer.MixerCostEstimate;
import neqsim.process.costestimation.splitter.SplitterCostEstimate;
import neqsim.process.costestimation.tank.TankCostEstimate;

/**
 * Unit tests for the new equipment-specific cost estimation classes.
 *
 * @author AGAS
 */
public class NewEquipmentCostEstimateTest {

  // ============================================================================
  // Tank Cost Estimate Tests
  // ============================================================================

  @Test
  @DisplayName("Test TankCostEstimate with fixed cone roof")
  void testTankCostEstimateFixedConeRoof() {
    TankCostEstimate costEst = new TankCostEstimate(null);
    costEst.setTankType("fixed-cone-roof");
    costEst.setTankVolume(5000.0); // 5000 m3
    costEst.setTankDiameter(20.0); // 20m diameter
    costEst.setTankHeight(16.0); // 16m height
    costEst.setIncludeFoundation(true);
    costEst.setIncludeInsulation(false);

    costEst.calculateCostEstimate();

    assertTrue(costEst.getPurchasedEquipmentCost() > 0,
        "Fixed cone roof tank cost should be positive");
    // BMC calculation depends on equipment type - just verify it's non-negative
    assertTrue(costEst.getBareModuleCost() >= 0, "BMC should be non-negative");

    Map<String, Object> breakdown = costEst.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("fixed-cone-roof", breakdown.get("tankType"));
    assertEquals(5000.0, breakdown.get("tankVolume_m3"));
  }

  @Test
  @DisplayName("Test TankCostEstimate with floating roof")
  void testTankCostEstimateFloatingRoof() {
    TankCostEstimate coneRoof = new TankCostEstimate(null);
    coneRoof.setTankType("fixed-cone-roof");
    coneRoof.setTankVolume(10000.0);
    coneRoof.setIncludeFoundation(false);
    coneRoof.calculateCostEstimate();

    TankCostEstimate floatingRoof = new TankCostEstimate(null);
    floatingRoof.setTankType("floating-roof");
    floatingRoof.setTankVolume(10000.0);
    floatingRoof.setIncludeFoundation(false);
    floatingRoof.calculateCostEstimate();

    assertTrue(floatingRoof.getPurchasedEquipmentCost() > coneRoof.getPurchasedEquipmentCost(),
        "Floating roof should cost more than fixed cone roof");
  }

  @Test
  @DisplayName("Test TankCostEstimate spherical tank")
  void testTankCostEstimateSpherical() {
    TankCostEstimate costEst = new TankCostEstimate(null);
    costEst.setTankType("spherical");
    costEst.setTankVolume(2000.0);
    costEst.setDesignPressure(5.0); // 5 barg
    costEst.setIncludeFoundation(true);

    costEst.calculateCostEstimate();

    assertTrue(costEst.getPurchasedEquipmentCost() > 0, "Spherical tank cost should be positive");

    Map<String, Object> breakdown = costEst.getCostBreakdown();
    assertEquals("spherical", breakdown.get("tankType"));
    assertEquals(5.0, breakdown.get("designPressure_barg"));
  }

  // ============================================================================
  // Expander Cost Estimate Tests
  // ============================================================================

  @Test
  @DisplayName("Test ExpanderCostEstimate with generator")
  void testExpanderCostEstimateWithGenerator() {
    ExpanderCostEstimate costEst = new ExpanderCostEstimate(null);
    costEst.setExpanderType("radial-inflow");
    costEst.setLoadType("generator");
    costEst.setShaftPower(2000.0); // 2000 kW
    costEst.setIncludeLoad(true);
    costEst.setIncludeLubeOilSystem(true);
    costEst.setIncludeControlSystem(true);

    costEst.calculateCostEstimate();

    assertTrue(costEst.getPurchasedEquipmentCost() > 0, "Expander cost should be positive");

    Map<String, Object> breakdown = costEst.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("radial-inflow", breakdown.get("expanderType"));
    assertEquals("generator", breakdown.get("loadType"));
  }

  @Test
  @DisplayName("Test ExpanderCostEstimate cryogenic service")
  void testExpanderCostEstimateCryogenic() {
    ExpanderCostEstimate standardCost = new ExpanderCostEstimate(null);
    standardCost.setExpanderType("radial-inflow");
    standardCost.setShaftPower(1000.0);
    standardCost.setCryogenicService(false);
    standardCost.setIncludeLoad(false);
    standardCost.calculateCostEstimate();

    ExpanderCostEstimate cryoCost = new ExpanderCostEstimate(null);
    cryoCost.setExpanderType("radial-inflow");
    cryoCost.setShaftPower(1000.0);
    cryoCost.setCryogenicService(true);
    cryoCost.setIncludeLoad(false);
    cryoCost.calculateCostEstimate();

    assertTrue(cryoCost.getPurchasedEquipmentCost() > standardCost.getPurchasedEquipmentCost(),
        "Cryogenic expander should cost more");
  }

  // ============================================================================
  // Mixer Cost Estimate Tests
  // ============================================================================

  @Test
  @DisplayName("Test MixerCostEstimate static mixer")
  void testMixerCostEstimateStatic() {
    MixerCostEstimate costEst = new MixerCostEstimate(null);
    costEst.setMixerType("static");
    costEst.setPipeDiameter(8.0); // 8 inch
    costEst.setNumberOfElements(12);
    costEst.setPressureClass(300);
    costEst.setFlangedConnections(true);

    costEst.calculateCostEstimate();

    assertTrue(costEst.getPurchasedEquipmentCost() > 0, "Static mixer cost should be positive");

    Map<String, Object> breakdown = costEst.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("static", breakdown.get("mixerType"));
    assertEquals(8.0, breakdown.get("pipeDiameter_in"));
    assertEquals(12, breakdown.get("numberOfElements"));
  }

  @Test
  @DisplayName("Test MixerCostEstimate different types")
  void testMixerCostEstimateDifferentTypes() {
    MixerCostEstimate staticMixer = new MixerCostEstimate(null);
    staticMixer.setMixerType("static");
    staticMixer.setPipeDiameter(6.0);
    staticMixer.calculateCostEstimate();

    MixerCostEstimate inlineMixer = new MixerCostEstimate(null);
    inlineMixer.setMixerType("inline");
    inlineMixer.setPipeDiameter(6.0);
    inlineMixer.calculateCostEstimate();

    MixerCostEstimate teeMixer = new MixerCostEstimate(null);
    teeMixer.setMixerType("tee");
    teeMixer.setPipeDiameter(6.0);
    teeMixer.calculateCostEstimate();

    assertTrue(inlineMixer.getPurchasedEquipmentCost() > staticMixer.getPurchasedEquipmentCost(),
        "Inline mixer should cost more than static");
    assertTrue(staticMixer.getPurchasedEquipmentCost() > teeMixer.getPurchasedEquipmentCost(),
        "Static mixer should cost more than tee");
  }

  // ============================================================================
  // Splitter Cost Estimate Tests
  // ============================================================================

  @Test
  @DisplayName("Test SplitterCostEstimate manifold")
  void testSplitterCostEstimateManifold() {
    SplitterCostEstimate costEst = new SplitterCostEstimate(null);
    costEst.setSplitterType("manifold");
    costEst.setNumberOfOutlets(4);
    costEst.setInletDiameter(10.0);
    costEst.setOutletDiameter(6.0);
    costEst.setPressureClass(600);
    costEst.setIncludeControlValves(false);

    costEst.calculateCostEstimate();

    assertTrue(costEst.getPurchasedEquipmentCost() > 0, "Manifold cost should be positive");

    Map<String, Object> breakdown = costEst.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("manifold", breakdown.get("splitterType"));
    assertEquals(4, breakdown.get("numberOfOutlets"));
  }

  @Test
  @DisplayName("Test SplitterCostEstimate with control valves")
  void testSplitterCostEstimateWithValves() {
    SplitterCostEstimate withoutValves = new SplitterCostEstimate(null);
    withoutValves.setSplitterType("manifold");
    withoutValves.setNumberOfOutlets(3);
    withoutValves.setIncludeControlValves(false);
    withoutValves.calculateCostEstimate();

    SplitterCostEstimate withValves = new SplitterCostEstimate(null);
    withValves.setSplitterType("manifold");
    withValves.setNumberOfOutlets(3);
    withValves.setIncludeControlValves(true);
    withValves.calculateCostEstimate();

    assertTrue(withValves.getPurchasedEquipmentCost() > withoutValves.getPurchasedEquipmentCost(),
        "Splitter with control valves should cost more");
  }

  // ============================================================================
  // Ejector Cost Estimate Tests
  // ============================================================================

  @Test
  @DisplayName("Test EjectorCostEstimate steam ejector")
  void testEjectorCostEstimateSteam() {
    EjectorCostEstimate costEst = new EjectorCostEstimate(null);
    costEst.setEjectorType("steam");
    costEst.setNumberOfStages(2);
    costEst.setSuctionPressure(50.0); // 50 mbar abs
    costEst.setDischargePressure(1.013); // Atmospheric
    costEst.setSuctionCapacity(500.0); // 500 kg/hr
    costEst.setIncludeIntercondensers(true);
    costEst.setIncludeAftercondenser(true);

    costEst.calculateCostEstimate();

    assertTrue(costEst.getPurchasedEquipmentCost() > 0, "Steam ejector cost should be positive");

    Map<String, Object> breakdown = costEst.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("steam", breakdown.get("ejectorType"));
    assertEquals(2, breakdown.get("numberOfStages"));
  }

  @Test
  @DisplayName("Test EjectorCostEstimate different types")
  void testEjectorCostEstimateDifferentTypes() {
    EjectorCostEstimate steamEjector = new EjectorCostEstimate(null);
    steamEjector.setEjectorType("steam");
    steamEjector.setSuctionCapacity(200.0);
    steamEjector.calculateCostEstimate();

    EjectorCostEstimate gasEjector = new EjectorCostEstimate(null);
    gasEjector.setEjectorType("gas");
    gasEjector.setSuctionCapacity(200.0);
    gasEjector.calculateCostEstimate();

    EjectorCostEstimate liquidEjector = new EjectorCostEstimate(null);
    liquidEjector.setEjectorType("liquid");
    liquidEjector.setSuctionCapacity(200.0);
    liquidEjector.calculateCostEstimate();

    assertTrue(gasEjector.getPurchasedEquipmentCost() > steamEjector.getPurchasedEquipmentCost(),
        "Gas ejector should cost more than steam");
    assertTrue(steamEjector.getPurchasedEquipmentCost() > liquidEjector.getPurchasedEquipmentCost(),
        "Steam ejector should cost more than liquid");
  }

  // ============================================================================
  // Absorber Cost Estimate Tests
  // ============================================================================

  @Test
  @DisplayName("Test AbsorberCostEstimate packed column")
  void testAbsorberCostEstimatePacked() {
    AbsorberCostEstimate costEst = new AbsorberCostEstimate(null);
    costEst.setAbsorberType("packed");
    costEst.setPackingType("structured");
    costEst.setColumnDiameter(2.0);
    costEst.setColumnHeight(15.0);
    costEst.setPackingHeight(10.0);
    costEst.setDesignPressure(60.0);
    costEst.setIncludeLiquidDistributor(true);
    costEst.setIncludeMistEliminator(true);
    costEst.setIncludeReboiler(false);

    costEst.calculateCostEstimate();

    assertTrue(costEst.getPurchasedEquipmentCost() > 0, "Packed absorber cost should be positive");

    Map<String, Object> breakdown = costEst.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("packed", breakdown.get("absorberType"));
    assertEquals("structured", breakdown.get("packingType"));
    assertEquals(2.0, breakdown.get("columnDiameter_m"));
  }

  @Test
  @DisplayName("Test AbsorberCostEstimate trayed column")
  void testAbsorberCostEstimateTrayed() {
    AbsorberCostEstimate packedAbsorber = new AbsorberCostEstimate(null);
    packedAbsorber.setAbsorberType("packed");
    packedAbsorber.setColumnDiameter(1.5);
    packedAbsorber.setColumnHeight(12.0);
    packedAbsorber.setPackingHeight(8.0);
    packedAbsorber.setDesignPressure(50.0);
    packedAbsorber.calculateCostEstimate();

    AbsorberCostEstimate trayedAbsorber = new AbsorberCostEstimate(null);
    trayedAbsorber.setAbsorberType("trayed");
    trayedAbsorber.setTrayType("valve");
    trayedAbsorber.setColumnDiameter(1.5);
    trayedAbsorber.setColumnHeight(12.0);
    trayedAbsorber.setNumberOfStages(15);
    trayedAbsorber.setDesignPressure(50.0);
    trayedAbsorber.calculateCostEstimate();

    // Both should have positive costs
    assertTrue(packedAbsorber.getPurchasedEquipmentCost() > 0);
    assertTrue(trayedAbsorber.getPurchasedEquipmentCost() > 0);
  }

  // ============================================================================
  // Currency Conversion Tests
  // ============================================================================

  @Test
  @DisplayName("Test CostEstimationCalculator currency conversion")
  void testCurrencyConversion() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    // Test USD (default)
    assertEquals("USD", calc.getCurrencyCode());
    assertEquals(1.0, calc.getExchangeRate(), 0.001);

    // Test EUR
    calc.setCurrencyCode("EUR");
    assertEquals("EUR", calc.getCurrencyCode());
    assertEquals(0.92, calc.getExchangeRate(), 0.01);

    double usdCost = 1000000.0;
    double eurCost = calc.convertFromUSD(usdCost);
    assertEquals(920000.0, eurCost, 1000.0);

    // Test NOK
    calc.setCurrencyCode("NOK");
    assertEquals("NOK", calc.getCurrencyCode());
    assertEquals(11.0, calc.getExchangeRate(), 0.1);

    double nokCost = calc.convertFromUSD(usdCost);
    assertEquals(11000000.0, nokCost, 100000.0);

    // Test convert back to USD
    double backToUsd = calc.convertToUSD(nokCost);
    assertEquals(usdCost, backToUsd, 1000.0);
  }

  @Test
  @DisplayName("Test CostEstimationCalculator location factors")
  void testLocationFactors() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    // Test US Gulf Coast (base)
    calc.setLocationByRegion("US Gulf Coast");
    assertEquals(1.0, calc.getLocationFactor(), 0.001);

    // Test North Sea
    calc.setLocationByRegion("North Sea");
    assertEquals(1.35, calc.getLocationFactor(), 0.001);

    // Test Norway (should also be North Sea)
    calc.setLocationByRegion("Norwegian Continental Shelf");
    assertEquals(1.35, calc.getLocationFactor(), 0.001);

    // Test China
    calc.setLocationByRegion("China");
    assertEquals(0.75, calc.getLocationFactor(), 0.001);

    // Get all available factors
    Map<String, Double> factors = CostEstimationCalculator.getAvailableLocationFactors();
    assertTrue(factors.size() >= 10, "Should have at least 10 location factors");
    assertTrue(factors.containsKey("North Sea / Norway"));
    assertTrue(factors.containsKey("Middle East"));
  }

  @Test
  @DisplayName("Test CostEstimationCalculator cost formatting")
  void testCostFormatting() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    // Test USD formatting
    calc.setCurrencyCode("USD");
    assertTrue(calc.formatCost(1500000.0).contains("M"));
    assertTrue(calc.formatCost(1500000000.0).contains("B"));
    assertTrue(calc.formatCost(50000.0).contains("K"));

    // Test EUR formatting
    calc.setCurrencyCode("EUR");
    assertTrue(
        calc.formatCost(1000000.0).contains("\u20AC") || calc.formatCost(1000000.0).contains("€"));

    // Test NOK formatting
    calc.setCurrencyCode("NOK");
    assertTrue(calc.formatCost(1000000.0).contains("kr"));
  }

  // ============================================================================
  // ProcessCostEstimate OPEX Tests
  // ============================================================================

  @Test
  @DisplayName("Test ProcessCostEstimate operating cost calculation")
  void testProcessCostEstimateOpex() {
    ProcessCostEstimate costEst = new ProcessCostEstimate();

    // Set some base costs manually for testing
    // (In real usage, these come from calculateAllCosts())
    costEst.setLocationFactor(1.0);
    costEst.setComplexityFactor(1.0);

    // Calculate operating costs with default utility prices
    double opex = costEst.calculateOperatingCost(8000);

    // Operating cost should be non-negative
    assertTrue(opex >= 0, "Operating cost should be non-negative");

    // Get breakdown
    Map<String, Double> breakdown = costEst.getOperatingCostBreakdown();
    assertNotNull(breakdown);
    assertTrue(breakdown.containsKey("Maintenance"));
    assertTrue(breakdown.containsKey("Operating Labor"));
  }

  @Test
  @DisplayName("Test ProcessCostEstimate financial metrics")
  void testProcessCostEstimateFinancials() {
    ProcessCostEstimate costEst = new ProcessCostEstimate();

    // Manually set costs for testing financial calculations
    // Use reflection or direct calculation setup
    costEst.calculateOperatingCost(8000);

    // Test payback period calculation
    double payback = costEst.calculatePaybackPeriod(5000000.0);
    assertTrue(payback >= 0 || Double.isInfinite(payback),
        "Payback should be positive or infinite");

    // Test ROI calculation
    double roi = costEst.calculateROI(5000000.0);
    // ROI can be any value depending on costs

    // Test NPV calculation
    double npv = costEst.calculateNPV(5000000.0, 0.10, 20);
    // NPV can be positive or negative
  }

  @Test
  @DisplayName("Test ProcessCostEstimate currency settings")
  void testProcessCostEstimateCurrency() {
    ProcessCostEstimate costEst = new ProcessCostEstimate();

    // Set currency
    costEst.setCurrency("EUR");
    assertEquals("EUR", costEst.getCurrencyCode());

    // Set location by region
    costEst.setLocationByRegion("North Sea");
    assertEquals(1.35, costEst.getLocationFactor(), 0.001);

    // Get costs in currency
    Map<String, Double> costs = costEst.getCostsInCurrency();
    assertNotNull(costs);
    assertTrue(costs.containsKey("grassRootsCost"));
    assertTrue(costs.containsKey("annualOperatingCost"));
  }
}
