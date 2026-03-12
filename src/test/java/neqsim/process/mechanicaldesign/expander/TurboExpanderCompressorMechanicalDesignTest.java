package neqsim.process.mechanicaldesign.expander;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.expander.TurboExpanderCompressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Integration tests for TurboExpanderCompressorMechanicalDesign.
 *
 * <p>
 * Tests the full workflow: process simulation via TurboExpanderCompressor, mechanical design via
 * calcDesign(), off-design evaluation, and multi-scenario reporting. Validates that the process
 * model and mechanical design work together correctly and that the design can be assessed for
 * fitness for future operations.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class TurboExpanderCompressorMechanicalDesignTest {

  private static TurboExpanderCompressor turboExpander;
  private static TurboExpanderCompressorMechanicalDesign mechDesign;

  /**
   * Set up a realistic turbo-expander-compressor process model, run it to convergence, then
   * initialize and run mechanical design.
   */
  @BeforeAll
  static void setUp() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 42.0, 10.00);
    feedGas.addComponent("nitrogen", 0.006);
    feedGas.addComponent("CO2", 0.014);
    feedGas.addComponent("methane", 0.862);
    feedGas.addComponent("ethane", 0.08);
    feedGas.addComponent("propane", 0.03);
    feedGas.addComponent("i-butane", 0.0024);
    feedGas.addComponent("n-butane", 0.004);
    feedGas.addComponent("n-hexane", 0.0015);
    feedGas.setMixingRule(2);
    feedGas.init(0);

    Stream feedStream = new Stream("dry feed gas", feedGas);
    feedStream.setFlowRate(456000.0, "kg/hr");
    feedStream.setTemperature(-23.0, "C");
    feedStream.setPressure(60.95, "bara");
    feedStream.run();

    turboExpander = new TurboExpanderCompressor("TEX-001", feedStream);

    turboExpander.setUCcurve(
        new double[] {0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821,
            0.9552460269880922, 1.0},
        new double[] {0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364,
            0.9943181818181818, 1.0});
    turboExpander.setQNEfficiencycurve(new double[] {0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6},
        new double[] {0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6});
    turboExpander.setQNHeadcurve(new double[] {0.5, 0.8, 1.0, 1.2, 1.4, 1.6},
        new double[] {1.1, 1.05, 1.0, 0.9, 0.7, 0.4});

    turboExpander.setImpellerDiameter(0.424);
    turboExpander.setDesignSpeed(6850.0);
    turboExpander.setExpanderDesignIsentropicEfficiency(0.88);
    turboExpander.setDesignUC(0.7);
    turboExpander.setDesignQn(0.03328);
    turboExpander.setExpanderOutPressure(42.0);
    turboExpander.setCompressorDesignPolytropicEfficiency(0.81);
    turboExpander.setCompressorDesignPolytropicHead(20.47);
    turboExpander.setMaximumIGVArea(1.637e4);

    // Run with separator and heater loop (realistic configuration)
    turboExpander.run();

    Separator separator = new Separator("separator", turboExpander.getExpanderOutletStream());
    separator.run();

    Heater heater = new Heater("heater", separator.getGasOutStream());
    heater.setOutTemperature(17.0, "C");
    heater.run();

    turboExpander.setCompressorFeedStream(heater.getOutStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feedStream);
    process.add(turboExpander);
    process.add(separator);
    process.add(heater);
    process.run();
    process.run();
    process.run();

    // Initialize and run mechanical design
    turboExpander.initTECMechanicalDesign();
    mechDesign = turboExpander.getTECMechanicalDesign();
    mechDesign.calcDesign();
  }

  // ============================================================================
  // 1. Process model → mechanical design data flow
  // ============================================================================

  @Test
  void testMechanicalDesignNotNull() {
    assertNotNull(mechDesign, "Mechanical design should be initialized");
    assertNotNull(turboExpander.getTECMechanicalDesign(),
        "getTECMechanicalDesign should return same instance");
  }

  @Test
  void testDesignExtractsProcessConditions() {
    // calcDesign should have extracted operating conditions from the converged process model
    assertTrue(mechDesign.getExpanderDesignInletPressure() > 50.0,
        "Expander inlet pressure should be > 50 bara");
    assertTrue(mechDesign.getExpanderPowerKW() > 100.0,
        "Expander power should be significant (> 100 kW)");
    assertTrue(mechDesign.getOperatingSpeed() > 5000.0, "Operating speed should be > 5000 rpm");
  }

  @Test
  void testExpanderWheelSized() {
    double wheelDiam = mechDesign.getExpanderWheelDiameter();
    assertTrue(wheelDiam > 100.0 && wheelDiam < 1500.0,
        "Expander wheel diameter should be 100-1500 mm, got " + wheelDiam);

    double tipSpeed = mechDesign.getExpanderTipSpeed();
    assertTrue(tipSpeed > 0 && tipSpeed <= 450.0,
        "Expander tip speed should be <= 450 m/s per API 617, got " + tipSpeed);
  }

  @Test
  void testCompressorImpellerSized() {
    double impDiam = mechDesign.getCompressorImpellerDiameter();
    assertTrue(impDiam > 100.0 && impDiam < 1500.0,
        "Compressor impeller diameter should be 100-1500 mm, got " + impDiam);

    double tipSpeed = mechDesign.getCompressorTipSpeed();
    assertTrue(tipSpeed > 0 && tipSpeed <= 350.0,
        "Compressor tip speed should be <= 350 m/s, got " + tipSpeed);
  }

  @Test
  void testShaftSizedFromCombinedTorque() {
    double shaftDiam = mechDesign.getShaftDiameter();
    assertTrue(shaftDiam > 30.0, "Shaft diameter should be > 30 mm");
    assertTrue(shaftDiam < 300.0, "Shaft diameter should be reasonable (< 300 mm)");

    double designTorque = mechDesign.getDesignTorqueNm();
    assertTrue(designTorque > 0, "Design torque should be positive");
  }

  @Test
  void testRotorDynamicsCalculated() {
    double firstCrit = mechDesign.getFirstCriticalSpeed();
    assertTrue(firstCrit > 0, "First critical speed should be positive");

    double bearingSpan = mechDesign.getBearingSpan();
    assertTrue(bearingSpan > 0, "Bearing span should be positive");

    double tripSpeed = mechDesign.getTripSpeed();
    assertTrue(tripSpeed > mechDesign.getMaxContinuousSpeed(),
        "Trip speed should exceed max continuous speed");
  }

  @Test
  void testCasingDesignPressuresIncludeMargin() {
    double expInP = mechDesign.getExpanderDesignInletPressure();
    double expCasingP = mechDesign.getExpanderCasingDesignPressure();
    assertTrue(expCasingP > expInP, "Casing design pressure must exceed operating pressure");
    assertEquals(expInP * 1.10, expCasingP, 0.1, "10% design margin expected");
  }

  @Test
  void testSurgeStonewallEstimated() {
    double surgeFlow = mechDesign.getDesignSurgeFlowM3hr();
    double stonewallFlow = mechDesign.getDesignStonewallFlowM3hr();
    assertTrue(surgeFlow > 0, "Surge flow estimate should be positive");
    assertTrue(stonewallFlow > surgeFlow, "Stonewall > surge flow expected");
  }

  @Test
  void testWeightsCalculated() {
    double totalWeight = mechDesign.getWeightTotal();
    assertTrue(totalWeight > 100.0, "Total weight should be substantial (> 100 kg)");
  }

  // ============================================================================
  // 2. Off-design evaluation at current operating conditions
  // ============================================================================

  @Test
  void testDesignPointEvaluationPasses() {
    // Evaluate the design at the same conditions it was sized for — must pass
    double expInP = mechDesign.getExpanderDesignInletPressure();
    double expOutP = mechDesign.getExpanderDesignOutletPressure();
    double compInP = mechDesign.getCompressorDesignSuctionPressure();
    double compOutP = mechDesign.getCompressorDesignDischargePressure();

    DesignEvaluationResult result = mechDesign.evaluateDesignAtConditions(expInP, expOutP, -23.0,
        compInP, compOutP, 17.0, 456000.0, 400000.0, 17.5, 17.5);

    assertTrue(result.isAcceptable(), "Design should be acceptable at design-point conditions. "
        + "Failures: " + result.getFailures());
    assertTrue(result.getFailures().isEmpty(),
        "No failures expected at design point, got: " + result.getFailures());
  }

  @Test
  void testEvaluationWithFluidObjects() {
    // Evaluate using full fluid objects for more accurate thermodynamic calculation
    neqsim.thermo.system.SystemInterface expFluid =
        turboExpander.getExpanderFeedStream().getThermoSystem().clone();
    neqsim.thermo.system.SystemInterface compFluid =
        turboExpander.getCompressorFeedStream().getThermoSystem().clone();

    DesignEvaluationResult result =
        mechDesign.evaluateDesignWithFluid(expFluid, 42.0, compFluid, 50.0);

    assertNotNull(result, "Result should not be null");
    assertTrue(result.getMargins().size() >= 13, "Should have at least 13 margin checks");
  }

  @Test
  void testOverpressureDetected() {
    // Push expander inlet pressure beyond casing design → should fail
    double casingLimit = mechDesign.getExpanderCasingDesignPressure();
    double overPressure = casingLimit * 1.1; // 10% above casing limit

    DesignEvaluationResult result = mechDesign.evaluateDesignAtConditions(overPressure, 42.0, -23.0,
        42.0, 55.0, 17.0, 456000.0, 400000.0, 17.5, 17.5);

    assertFalse(result.isAcceptable(), "Design should fail at overpressure");
    assertTrue(result.getExpanderCasingPressureMargin() < 0,
        "Expander casing pressure margin should be negative");
  }

  // ============================================================================
  // 3. Auxiliary system evaluations
  // ============================================================================

  @Test
  void testShearPinEvaluationFromGeometry() {
    // Save existing pin state to restore after test
    int savedPins = mechDesign.getNumberOfShearPins();
    double savedPinDia = mechDesign.getShearPinDiameterMm();
    double savedPinStrength = mechDesign.getShearPinShearStrengthMPa();
    double savedPinRadius = mechDesign.getShearPinRadialPositionMm();

    try {
      // Configure shear pin parameters — sized for ~6000 kW total at ~6850 rpm
      // Design torque ~ 8350 Nm; pins must handle 1.5x that ~ 12500 Nm
      mechDesign.setNumberOfShearPins(4);
      mechDesign.setShearPinDiameterMm(16.0);
      mechDesign.setShearPinShearStrengthMPa(310.0); // AISI 316
      mechDesign.setShearPinRadialPositionMm(100.0); // 100 mm from shaft centre

      DesignEvaluationResult result = mechDesign.evaluateDesignAtConditions(60.95, 42.0, -23.0,
          42.0, 50.0, 17.0, 456000.0, 400000.0, 17.5, 17.5);

      // With proper sizing the pins should survive normal torque
      assertTrue(result.getShearPinTorqueMargin() > 0,
          "Shear pin torque margin should be positive under normal operation");
    } finally {
      // Restore state for other tests
      mechDesign.setNumberOfShearPins(savedPins);
      mechDesign.setShearPinDiameterMm(savedPinDia);
      mechDesign.setShearPinShearStrengthMPa(savedPinStrength);
      mechDesign.setShearPinRadialPositionMm(savedPinRadius);
    }
  }

  @Test
  void testSealGasDpCheck() {
    // Save state
    double savedSupplyP = mechDesign.getSealGasSupplyPressureBara();
    double savedReqDp = mechDesign.getSealGasRequiredDpBar();

    try {
      // Set seal gas supply at 5 bar above max process pressure
      double maxProcess = Math.max(60.95, 50.0); // expander inlet or comp discharge
      mechDesign.setSealGasSupplyPressureBara(maxProcess + 5.0);
      mechDesign.setSealGasRequiredDpBar(1.5);

      DesignEvaluationResult result = mechDesign.evaluateDesignAtConditions(60.95, 42.0, -23.0,
          42.0, 50.0, 17.0, 456000.0, 400000.0, 17.5, 17.5);

      assertTrue(result.getSealGasDpMargin() > 0,
          "Seal gas DP should be adequate with 5 bar margin");

      // Now simulate insufficient seal gas
      mechDesign.setSealGasSupplyPressureBara(maxProcess + 0.5);
      DesignEvaluationResult result2 = mechDesign.evaluateDesignAtConditions(60.95, 42.0, -23.0,
          42.0, 50.0, 17.0, 456000.0, 400000.0, 17.5, 17.5);

      assertTrue(result2.getSealGasDpMargin() < 0,
          "Seal gas DP margin should be negative with insufficient supply");
    } finally {
      // Restore state for other tests
      mechDesign.setSealGasSupplyPressureBara(savedSupplyP);
      mechDesign.setSealGasRequiredDpBar(savedReqDp);
    }
  }

  @Test
  void testThrustBalanceDetails() {
    DesignEvaluationResult result = mechDesign.evaluateDesignAtConditions(60.95, 42.0, -23.0, 42.0,
        50.0, 17.0, 456000.0, 400000.0, 17.5, 17.5);

    // Thrust components should be calculated
    assertTrue(result.getExpanderAxialThrustN() > 0, "Expander thrust should be positive");
    assertTrue(result.getCompressorAxialThrustN() > 0, "Compressor thrust should be positive");
    // Net thrust is the difference
    double expected = result.getExpanderAxialThrustN() - result.getCompressorAxialThrustN();
    assertEquals(expected, result.getNetAxialThrustN(), 1.0,
        "Net thrust should equal expander - compressor");
  }

  // ============================================================================
  // 4. Multi-scenario evaluation for future operations fitness
  // ============================================================================

  @Test
  void testMultipleScenarioEvaluation() {
    List<String> names =
        Arrays.asList("Design Case", "Low Flow (Turndown)", "High Pressure (Future)");

    List<Map<String, Double>> scenarios = new ArrayList<Map<String, Double>>();

    // Design case
    Map<String, Double> design = new LinkedHashMap<String, Double>();
    design.put("expInletP", 60.95);
    design.put("expOutletP", 42.0);
    design.put("expInletT", -23.0);
    design.put("compInletP", 42.0);
    design.put("compDischargeP", 50.0);
    design.put("compInletT", 17.0);
    design.put("expMassFlow", 456000.0);
    design.put("compMassFlow", 400000.0);
    design.put("expMolarMass", 17.5);
    design.put("compMolarMass", 17.5);
    scenarios.add(design);

    // Low flow (turndown)
    Map<String, Double> turndown = new LinkedHashMap<String, Double>();
    turndown.put("expInletP", 55.0);
    turndown.put("expOutletP", 42.0);
    turndown.put("expInletT", -20.0);
    turndown.put("compInletP", 42.0);
    turndown.put("compDischargeP", 48.0);
    turndown.put("compInletT", 17.0);
    turndown.put("expMassFlow", 300000.0);
    turndown.put("compMassFlow", 270000.0);
    turndown.put("expMolarMass", 17.5);
    turndown.put("compMolarMass", 17.5);
    scenarios.add(turndown);

    // High pressure (future reservoir conditions)
    Map<String, Double> future = new LinkedHashMap<String, Double>();
    future.put("expInletP", 65.0);
    future.put("expOutletP", 42.0);
    future.put("expInletT", -18.0);
    future.put("compInletP", 42.0);
    future.put("compDischargeP", 55.0);
    future.put("compInletT", 20.0);
    future.put("expMassFlow", 500000.0);
    future.put("compMassFlow", 450000.0);
    future.put("expMolarMass", 18.0);
    future.put("compMolarMass", 18.0);
    scenarios.add(future);

    List<DesignEvaluationResult> results = mechDesign.evaluateMultipleScenarios(names, scenarios);

    assertEquals(3, results.size(), "Should have 3 results");
    assertEquals("Design Case", results.get(0).getScenarioName());
    assertEquals("Low Flow (Turndown)", results.get(1).getScenarioName());
    assertEquals("High Pressure (Future)", results.get(2).getScenarioName());

    // Design case should pass
    assertTrue(results.get(0).isAcceptable(),
        "Design case should be acceptable. Failures: " + results.get(0).getFailures());

    // Each result should have all 13 margins
    for (DesignEvaluationResult r : results) {
      assertTrue(r.getMargins().size() >= 10, "Each result should have at least 10 margins");
    }
  }

  // ============================================================================
  // 5. JSON reporting
  // ============================================================================

  @Test
  void testToJsonContainsAllSections() {
    String json = mechDesign.toJson();
    assertNotNull(json, "toJson should return non-null");
    assertTrue(json.contains("expander"), "JSON should have expander section");
    assertTrue(json.contains("compressor"), "JSON should have compressor section");
    assertTrue(json.contains("shaft"), "JSON should have shaft section");
    assertTrue(json.contains("shearPins"), "JSON should have shearPins section");
    assertTrue(json.contains("thrustBalance"), "JSON should have thrustBalance section");
    assertTrue(json.contains("sealGasSystem"), "JSON should have sealGasSystem section");
    assertTrue(json.contains("oilSealSystem"), "JSON should have oilSealSystem section");
    assertTrue(json.contains("antiSurgeSystem"), "JSON should have antiSurgeSystem section");
    assertTrue(json.contains("module"), "JSON should have module section");
  }

  @Test
  void testEvaluationReportToJson() {
    List<String> names = Arrays.asList("Design", "Turndown");

    List<Map<String, Double>> scenarios = new ArrayList<Map<String, Double>>();

    Map<String, Double> design = new LinkedHashMap<String, Double>();
    design.put("expInletP", 60.95);
    design.put("expOutletP", 42.0);
    design.put("expInletT", -23.0);
    design.put("compInletP", 42.0);
    design.put("compDischargeP", 50.0);
    design.put("compInletT", 17.0);
    design.put("expMassFlow", 456000.0);
    design.put("compMassFlow", 400000.0);
    design.put("expMolarMass", 17.5);
    design.put("compMolarMass", 17.5);
    scenarios.add(design);

    Map<String, Double> turndown = new LinkedHashMap<String, Double>();
    turndown.put("expInletP", 50.0);
    turndown.put("expOutletP", 42.0);
    turndown.put("expInletT", -20.0);
    turndown.put("compInletP", 42.0);
    turndown.put("compDischargeP", 48.0);
    turndown.put("compInletT", 17.0);
    turndown.put("expMassFlow", 300000.0);
    turndown.put("compMassFlow", 270000.0);
    turndown.put("expMolarMass", 17.5);
    turndown.put("compMolarMass", 17.5);
    scenarios.add(turndown);

    String report = mechDesign.evaluationReportToJson(names, scenarios);
    assertNotNull(report);
    assertTrue(report.contains("designBasis"), "Report should have designBasis");
    assertTrue(report.contains("ratedConditions"), "Report should have ratedConditions");
    assertTrue(report.contains("scenarioEvaluations"), "Report should have scenarioEvaluations");
    assertTrue(report.contains("summary"), "Report should have summary");
    assertTrue(report.contains("overallVerdict"), "Report should have verdict");
    assertTrue(report.contains("thrustBalance"), "Report should have thrust balance per scenario");
  }

  // ============================================================================
  // 6. Datasheet loading workflow
  // ============================================================================

  @Test
  void testDatasheetLoadingAndEvaluation() {
    // Simulate loading vendor datasheet values and running evaluation
    TurboExpanderCompressorMechanicalDesign design = turboExpander.getTECMechanicalDesign();

    try {
      // Load from vendor datasheet
      design.setFromDatasheet(424.0, // expander wheel [mm]
          350.0, // compressor impeller [mm]
          6850.0, // rated speed [rpm]
          7193.0, // MCS [rpm]
          7553.0, // trip [rpm]
          9500.0, // 1st critical [rpm]
          38000.0, // 2nd critical [rpm]
          120.0, // shaft diameter [mm] — sized for 6000 kW total at 6850 rpm
          900.0, // bearing span [mm]
          67.0, // exp casing design P [bara]
          100.0, // exp casing design T [°C]
          60.0, // comp casing design P [bara]
          80.0 // comp casing design T [°C]
      );

      design.setRatedConditions(60.95, 42.0, -23.0, -41.0, // expander
          42.0, 50.0, 17.0, 35.0 // compressor
      );

      design.setRatedPerformance(3000.0, // expander power [kW]
          2990.0, // compressor power [kW]
          25.0, // enthalpy drop [kJ/kg]
          20.47, // polytropic head [kJ/kg]
          8000.0, // surge flow [m3/hr]
          15000.0 // stonewall flow [m3/hr]
      );

      // Evaluate at design conditions
      DesignEvaluationResult result = design.evaluateDesignAtConditions(60.95, 42.0, -23.0, 42.0,
          50.0, 17.0, 456000.0, 400000.0, 17.5, 17.5);

      assertTrue(result.isAcceptable(),
          "Should pass at datasheet rated conditions. Failures: " + result.getFailures());

      // Evaluate at challenging future conditions
      DesignEvaluationResult futureResult = design.evaluateDesignAtConditions(65.0, 42.0, -18.0,
          42.0, 58.0, 20.0, 500000.0, 450000.0, 18.0, 18.0);

      // The future scenario with comp discharge at 58 bara (limit 60 bara) should still pass
      // but might show warnings
      assertNotNull(futureResult.getMargins());
    } finally {
      // Restore design state from process model so other tests are unaffected
      design.calcDesign();
    }
  }

  // ============================================================================
  // 7. Consistency of process model and design results
  // ============================================================================

  @Test
  void testDesignSpeedMatchesProcessModel() {
    double processSpeed = turboExpander.getSpeed();
    double designSpeed = mechDesign.getOperatingSpeed();
    assertEquals(processSpeed, designSpeed, 1.0,
        "Design operating speed should match the process model converged speed");
  }

  @Test
  void testDesignPowerMatchesProcessModel() {
    double processPowerExp = turboExpander.getPowerExpander("kW");
    double designPowerExp = mechDesign.getExpanderPowerKW();
    assertEquals(processPowerExp, designPowerExp, 1.0,
        "Design expander power should match process model power");
  }

  @Test
  void testDesignPressuresMatchProcessModel() {
    double processExpInP = turboExpander.getExpanderFeedStream().getPressure("bara");
    double designExpInP = mechDesign.getExpanderDesignInletPressure();
    assertEquals(processExpInP, designExpInP, 0.1,
        "Design inlet pressure should match process model");
  }

  // ============================================================================
  // 8. UC curve uses fitted parameters
  // ============================================================================

  @Test
  void testUCcurveUsesFittedParameters() {
    // The setUp called setUCcurve with data, so ucCurveA should be non-zero
    double ucCurveA = turboExpander.getUcCurveA();
    assertTrue(ucCurveA != 0.0, "ucCurveA should be fitted from data, not zero");

    // At vertex (uc = 1.0), fitted curve should give efficiency = ucCurveK = 1.0
    double effAtVertex = turboExpander.getEfficiencyFromUC(1.0);
    assertEquals(1.0, effAtVertex, 0.001, "Efficiency at UC=1.0 should be ~1.0 (vertex)");

    // Off-design should use fitted coefficient, not hardcoded -3.56
    double effAtUc08 = turboExpander.getEfficiencyFromUC(0.8);
    double expectedFitted = ucCurveA * (0.8 - 1.0) * (0.8 - 1.0) + 1.0;
    assertEquals(expectedFitted, effAtUc08, 0.001,
        "Off-design efficiency should use fitted ucCurveA, not hardcoded -3.56");
  }

  // ============================================================================
  // 9. EOS-accurate evaluation with fluid objects
  // ============================================================================

  @Test
  void testEvaluateDesignWithFluid() {
    neqsim.thermo.system.SystemInterface expFluid =
        turboExpander.getExpanderFeedStream().getThermoSystem().clone();
    neqsim.thermo.system.SystemInterface compFluid =
        turboExpander.getCompressorFeedStream().getThermoSystem().clone();

    DesignEvaluationResult result =
        mechDesign.evaluateDesignWithFluid(expFluid, 42.0, compFluid, 50.0);

    assertNotNull(result, "Fluid-based evaluation should return a result");
    assertTrue(result.getMargins().size() >= 10, "Should have at least 10 margins computed");

    // Shaft stress from EOS flash should be more accurate (positive margin at design)
    assertTrue(result.getShaftStressMargin() > 0,
        "Shaft stress margin from EOS evaluation should be positive at design conditions");
  }
}
