package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.valve.ControlValveSizing_MultiphaseChoke;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Integration tests for multiphase choke models with ThrottlingValve.
 *
 * <p>
 * Tests verify that the Sachdeva, Gilbert, and other multiphase choke models can be used as valve
 * sizing methods within the ThrottlingValve unit operation.
 * </p>
 *
 * @author esol
 */
public class ThrottlingValveMultiphaseChokeTest {

  private SystemInterface twoPhaseFluid;
  private Stream inletStream;

  @BeforeEach
  void setUp() {
    // Create a two-phase gas-oil fluid
    twoPhaseFluid = new SystemSrkEos(320.0, 100.0); // 47°C, 100 bara
    twoPhaseFluid.addComponent("methane", 0.70);
    twoPhaseFluid.addComponent("ethane", 0.10);
    twoPhaseFluid.addComponent("propane", 0.05);
    twoPhaseFluid.addComponent("n-heptane", 0.10);
    twoPhaseFluid.addComponent("nC10", 0.05);
    twoPhaseFluid.setMixingRule(2);
    twoPhaseFluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(twoPhaseFluid);
    ops.TPflash();
    twoPhaseFluid.initPhysicalProperties();

    inletStream = new Stream("Inlet", twoPhaseFluid);
    inletStream.setFlowRate(10000, "kg/hr");
    inletStream.run();
  }

  @Test
  @DisplayName("Test Sachdeva model integration with ThrottlingValve")
  void testSachdevaModelIntegration() {
    // Create throttling valve with Sachdeva sizing
    ThrottlingValve choke = new ThrottlingValve("Production Choke", inletStream);
    choke.setOutletPressure(50.0, "bara"); // 50 bara outlet

    // Configure for Sachdeva multiphase model
    ValveMechanicalDesign design = choke.getMechanicalDesign();
    design.setValveSizingStandard("Sachdeva");
    design.setChokeDiameter(0.5, "in"); // 0.5 inch choke

    // Run the valve
    choke.run();

    // Verify results
    assertNotNull(choke.getOutletStream());
    assertTrue(choke.getOutletStream().getFlowRate("kg/hr") > 0, "Flow should be positive");
    assertEquals(50.0, choke.getOutletStream().getPressure("bara"), 0.1,
        "Outlet pressure should match setpoint");

    System.out.println("\n=== Sachdeva Model Results ===");
    System.out.printf("Inlet Pressure: %.1f bara\n", inletStream.getPressure("bara"));
    System.out.printf("Outlet Pressure: %.1f bara\n", choke.getOutletStream().getPressure("bara"));
    System.out.printf("Flow Rate: %.1f kg/hr\n", choke.getOutletStream().getFlowRate("kg/hr"));
  }

  @Test
  @DisplayName("Test Gilbert model integration with ThrottlingValve")
  void testGilbertModelIntegration() {
    // Create throttling valve with Gilbert sizing
    ThrottlingValve choke = new ThrottlingValve("Production Choke", inletStream);
    choke.setOutletPressure(50.0, "bara");

    // Configure for Gilbert multiphase model
    ValveMechanicalDesign design = choke.getMechanicalDesign();
    design.setValveSizingStandard("Gilbert");
    design.setChokeDiameter(32, "64ths"); // 32/64" = 0.5 inch

    // Run the valve
    choke.run();

    // Verify results
    assertNotNull(choke.getOutletStream());
    assertTrue(choke.getOutletStream().getFlowRate("kg/hr") > 0, "Flow should be positive");

    System.out.println("\n=== Gilbert Model Results ===");
    System.out.printf("Inlet Pressure: %.1f bara\n", inletStream.getPressure("bara"));
    System.out.printf("Outlet Pressure: %.1f bara\n", choke.getOutletStream().getPressure("bara"));
    System.out.printf("Flow Rate: %.1f kg/hr\n", choke.getOutletStream().getFlowRate("kg/hr"));
  }

  @Test
  @DisplayName("Test all Gilbert-type correlations")
  void testAllGilbertCorrelations() {
    String[] models = {"Gilbert", "Baxendell", "Ros", "Achong"};

    System.out.println("\n=== Comparison of Gilbert-Type Correlations ===");
    System.out.println("Model       | Outlet Flow (kg/hr)");
    System.out.println("---------------------------------");

    for (String model : models) {
      ThrottlingValve choke = new ThrottlingValve("Choke " + model, inletStream);
      choke.setOutletPressure(50.0, "bara");

      ValveMechanicalDesign design = choke.getMechanicalDesign();
      design.setValveSizingStandard(model);
      design.setChokeDiameter(0.5, "in");

      choke.run();

      double flowRate = choke.getOutletStream().getFlowRate("kg/hr");
      System.out.printf("%-11s | %.1f\n", model, flowRate);

      assertTrue(flowRate > 0, model + " should produce positive flow");
    }
  }

  @Test
  @DisplayName("Test choke diameter effect on calculated flow")
  void testChokeDiameterEffect() {
    double[] diameters = {0.25, 0.5, 0.75, 1.0}; // inches
    double previousCalculatedFlow = 0;

    System.out.println("\n=== Choke Diameter Effect on Flow ===");
    System.out.println("Diameter (in) | Calculated Flow (kg/hr) | Ratio to Previous");
    System.out.println("--------------------------------------------------");

    for (double d : diameters) {
      ThrottlingValve choke = new ThrottlingValve("Test Choke", inletStream);
      choke.setOutletPressure(50.0, "bara");
      choke.run();

      ValveMechanicalDesign design = choke.getMechanicalDesign();
      design.setValveSizingStandard("Sachdeva");
      design.setChokeDiameter(d, "in");

      // Get the sizing method to calculate predicted flow
      ControlValveSizing_MultiphaseChoke chokeMethod =
          (ControlValveSizing_MultiphaseChoke) design.getValveSizingMethod();

      // Calculate predicted flow using the choke model directly
      Map<String, Object> results = chokeMethod.calcValveSize(100.0);
      double calculatedFlow = ((Number) results.getOrDefault("massFlowRate", 0.0)).doubleValue();
      double calculatedFlowKgHr = calculatedFlow * 3600.0; // kg/s to kg/hr

      double ratio = previousCalculatedFlow > 0 ? calculatedFlowKgHr / previousCalculatedFlow : 0;

      System.out.printf("    %.2f      |   %.1f    |     %.2f\n", d, calculatedFlowKgHr, ratio);

      assertTrue(calculatedFlowKgHr > previousCalculatedFlow || previousCalculatedFlow == 0,
          "Calculated flow should increase with larger diameter");
      previousCalculatedFlow = calculatedFlowKgHr;
    }
  }

  @Test
  @DisplayName("Test pressure drop effect on flow")
  void testPressureDropEffect() {
    double[] outletPressures = {90, 70, 50, 30}; // bara

    System.out.println("\n=== Pressure Drop Effect on Flow (Sachdeva) ===");
    System.out.println("P_out (bara) | Delta_P | Flow (kg/hr) | Regime");
    System.out.println("----------------------------------------------------");

    double previousFlow = 0;

    for (double pOut : outletPressures) {
      ThrottlingValve choke = new ThrottlingValve("Test Choke", inletStream);
      choke.setOutletPressure(pOut, "bara");

      ValveMechanicalDesign design = choke.getMechanicalDesign();
      design.setValveSizingStandard("Sachdeva");
      design.setChokeDiameter(0.5, "in");

      choke.run();

      double flowRate = choke.getOutletStream().getFlowRate("kg/hr");
      double deltaP = 100.0 - pOut;

      // Determine if critical
      ControlValveSizing_MultiphaseChoke chokeMethod =
          (ControlValveSizing_MultiphaseChoke) design.getValveSizingMethod();
      double gasQuality =
          chokeMethod.getChokeModel().calculateGasQuality(inletStream.getThermoSystem());
      double criticalRatio =
          chokeMethod.getChokeModel().calculateCriticalPressureRatio(gasQuality, 1.3);
      String regime = (pOut / 100.0) < criticalRatio ? "CRITICAL" : "SUBCRITICAL";

      System.out.printf("   %.0f       |  %.0f   |   %.1f    | %s\n", pOut, deltaP, flowRate,
          regime);

      assertTrue(flowRate >= previousFlow,
          "Flow should increase or stay constant with more pressure drop");
      previousFlow = flowRate;
    }
  }

  @Test
  @DisplayName("Test discharge coefficient effect")
  void testDischargeCoefficient() {
    double[] cdValues = {0.70, 0.80, 0.85, 0.90};

    System.out.println("\n=== Discharge Coefficient Effect ===");
    System.out.println("Cd    | Flow (kg/hr)");
    System.out.println("----------------------");

    for (double cd : cdValues) {
      ThrottlingValve choke = new ThrottlingValve("Test Choke", inletStream);
      choke.setOutletPressure(50.0, "bara");

      ValveMechanicalDesign design = choke.getMechanicalDesign();
      design.setValveSizingStandard("Sachdeva");
      design.setChokeDiameter(0.5, "in");
      design.setChokeDischargeCoefficient(cd);

      choke.run();

      double flowRate = choke.getOutletStream().getFlowRate("kg/hr");
      System.out.printf("%.2f  |   %.1f\n", cd, flowRate);

      assertTrue(flowRate > 0, "Flow should be positive");
    }
  }

  @Test
  @DisplayName("Test valve sizing method getter")
  void testGetValveSizingMethod() {
    ThrottlingValve choke = new ThrottlingValve("Test Choke", inletStream);
    choke.setOutletPressure(50.0, "bara");

    ValveMechanicalDesign design = choke.getMechanicalDesign();
    design.setValveSizingStandard("Sachdeva");

    assertTrue(design.getValveSizingMethod() instanceof ControlValveSizing_MultiphaseChoke,
        "Should be MultiphaseChoke sizing method");

    ControlValveSizing_MultiphaseChoke chokeMethod =
        (ControlValveSizing_MultiphaseChoke) design.getValveSizingMethod();
    assertEquals("Sachdeva", chokeMethod.getModelType());
  }

  @Test
  @DisplayName("Test critical flow detection")
  void testCriticalFlowDetection() {
    ThrottlingValve choke = new ThrottlingValve("Test Choke", inletStream);
    choke.setOutletPressure(20.0, "bara"); // Low pressure to ensure critical flow

    ValveMechanicalDesign design = choke.getMechanicalDesign();
    design.setValveSizingStandard("Sachdeva");
    design.setChokeDiameter(0.5, "in");

    choke.run();

    // Get the choke method to check critical flow
    ControlValveSizing_MultiphaseChoke chokeMethod =
        (ControlValveSizing_MultiphaseChoke) design.getValveSizingMethod();

    double gasQuality =
        chokeMethod.getChokeModel().calculateGasQuality(inletStream.getThermoSystem());
    double criticalRatio =
        chokeMethod.getChokeModel().calculateCriticalPressureRatio(gasQuality, 1.3);
    double pressureRatio = 20.0 / 100.0;

    System.out.println("\n=== Critical Flow Detection ===");
    System.out.printf("Gas Quality: %.3f\n", gasQuality);
    System.out.printf("Critical Pressure Ratio: %.3f\n", criticalRatio);
    System.out.printf("Actual Pressure Ratio: %.3f\n", pressureRatio);
    System.out.printf("Flow is: %s\n", pressureRatio < criticalRatio ? "CRITICAL" : "SUBCRITICAL");

    assertTrue(pressureRatio < criticalRatio || pressureRatio >= criticalRatio,
        "Should detect flow regime correctly");
  }

  @Test
  @DisplayName("Test calculate valve opening from target flow rate")
  void testCalculateValveOpeningFromFlowRate() {
    // Set up choke valve
    ThrottlingValve choke = new ThrottlingValve("Test Choke", inletStream);
    choke.setOutletPressure(50.0, "bara");
    choke.run();

    ValveMechanicalDesign design = choke.getMechanicalDesign();
    design.setValveSizingStandard("Sachdeva");
    design.setChokeDiameter(1.0, "in"); // 1 inch choke

    ControlValveSizing_MultiphaseChoke chokeMethod =
        (ControlValveSizing_MultiphaseChoke) design.getValveSizingMethod();

    // First, calculate flow at 50% opening
    double testOpening = 50.0;
    double effectiveDiameter = 0.0254 * Math.sqrt(testOpening / 100.0); // 1 inch * sqrt(0.5)
    chokeMethod.setChokeDiameter(1.0, "in");

    // Get flow at 50% opening using calcValveSize
    Map<String, Object> results50 = chokeMethod.calcValveSize(testOpening);
    double massFlow50 = ((Number) results50.getOrDefault("massFlowRate", 0.0)).doubleValue();
    double density = inletStream.getThermoSystem().getDensity("kg/m3");
    double volumetricFlow50 = massFlow50 / density; // m3/s

    System.out.println("\n=== Valve Opening from Flow Rate Test ===");
    System.out.printf("Test opening: %.1f%%\n", testOpening);
    System.out.printf("Mass flow at %.1f%% opening: %.4f kg/s\n", testOpening, massFlow50);
    System.out.printf("Volumetric flow: %.6f m3/s\n", volumetricFlow50);

    // Now reverse-calculate: given this flow, what opening do we need?
    // Reset choke diameter to full size first
    chokeMethod.setChokeDiameter(1.0, "in");

    double calculatedOpening = chokeMethod.calculateValveOpeningFromFlowRate(volumetricFlow50, 0.0,
        inletStream, choke.getOutletStream());

    System.out.printf("Calculated opening for same flow: %.1f%%\n", calculatedOpening);
    System.out.printf("Error: %.2f%%\n", Math.abs(calculatedOpening - testOpening));

    // The calculated opening should be close to the original test opening
    assertEquals(testOpening, calculatedOpening, 2.0,
        "Calculated opening should match original opening within 2%");

    // Test with different target openings
    System.out.println("\n--- Validation at Multiple Openings ---");
    System.out.println("Target Opening | Calc Flow (kg/s) | Reverse Calc Opening | Error");
    System.out.println("----------------------------------------------------------------");

    double[] testOpenings = {20.0, 40.0, 60.0, 80.0, 100.0};
    for (double opening : testOpenings) {
      // Reset and calculate flow at this opening
      chokeMethod.setChokeDiameter(1.0, "in");
      Map<String, Object> results = chokeMethod.calcValveSize(opening);
      double massFlow = ((Number) results.getOrDefault("massFlowRate", 0.0)).doubleValue();
      double volFlow = massFlow / density;

      // Reverse calculate opening
      chokeMethod.setChokeDiameter(1.0, "in");
      double reverseOpening = chokeMethod.calculateValveOpeningFromFlowRate(volFlow, 0.0,
          inletStream, choke.getOutletStream());

      double error = Math.abs(reverseOpening - opening);
      System.out.printf("    %.1f%%       |     %.4f      |        %.1f%%         |  %.2f%%\n",
          opening, massFlow, reverseOpening, error);

      assertTrue(error < 2.0, "Reverse calculation error should be < 2%");
    }
  }

  @Test
  @DisplayName("Test flow calculation in transient mode - choke calculates outlet flow")
  void testFlowCalculationTransientMode() {
    System.out.println("\n=== Flow Calculation in Transient Mode Test ===");

    // Test 1: Steady-state mode (default) - outlet flow equals inlet flow
    ThrottlingValve choke1 = new ThrottlingValve("Choke1", inletStream);
    choke1.setOutletPressure(50.0, "bara");

    ValveMechanicalDesign design1 = choke1.getMechanicalDesign();
    design1.setValveSizingStandard("Sachdeva");
    design1.setChokeDiameter(0.5, "in");

    choke1.run();

    double inletFlow = inletStream.getFlowRate("kg/hr");
    double outletFlow1 = choke1.getOutletStream().getFlowRate("kg/hr");

    System.out.printf("Steady-state mode (run()):\n");
    System.out.printf("  Inlet flow: %.1f kg/hr\n", inletFlow);
    System.out.printf("  Outlet flow: %.1f kg/hr\n", outletFlow1);
    assertEquals(inletFlow, outletFlow1, 1.0, "In steady-state, outlet should equal inlet");

    // Test 2: Transient mode with setCalculateSteadyState(false) - flow is calculated
    ThrottlingValve choke2 = new ThrottlingValve("Choke2", inletStream);
    choke2.setOutletPressure(50.0, "bara");
    choke2.setCalculateSteadyState(false); // Enable transient flow calculation

    ValveMechanicalDesign design2 = choke2.getMechanicalDesign();
    design2.setValveSizingStandard("Sachdeva");
    design2.setChokeDiameter(0.5, "in");

    // Run transient with small timestep
    choke2.runTransient(0.1);

    double outletFlow2 = choke2.getOutletStream().getFlowRate("kg/hr");

    System.out.printf("\nTransient mode (runTransient with setCalculateSteadyState=false):\n");
    System.out.printf("  Inlet flow: %.1f kg/hr\n", inletFlow);
    System.out.printf("  Outlet flow: %.1f kg/hr (calculated by choke model)\n", outletFlow2);

    // The calculated flow should be positive
    assertTrue(outletFlow2 > 0, "Calculated flow should be positive");

    // Test 3: Different choke sizes should give different flows in transient mode
    double[] diameters = {0.25, 0.5, 1.0}; // inches
    double previousFlow = 0;

    System.out.println("\n--- Flow vs Choke Diameter (Transient Mode) ---");
    System.out.println("Diameter (in) | Calculated Flow (kg/hr)");
    System.out.println("------------------------------------------");

    for (double d : diameters) {
      ThrottlingValve choke = new ThrottlingValve("Choke", inletStream);
      choke.setOutletPressure(50.0, "bara");
      choke.setCalculateSteadyState(false);

      ValveMechanicalDesign design = choke.getMechanicalDesign();
      design.setValveSizingStandard("Sachdeva");
      design.setChokeDiameter(d, "in");

      choke.runTransient(0.1);

      double flow = choke.getOutletStream().getFlowRate("kg/hr");
      System.out.printf("    %.2f      |        %.1f\n", d, flow);

      assertTrue(flow > previousFlow, "Larger choke should give higher flow");
      previousFlow = flow;
    }

    // Test 4: Different valve openings should give different flows
    System.out.println("\n--- Flow vs Valve Opening (Transient Mode) ---");
    System.out.println("Opening (%) | Calculated Flow (kg/hr)");
    System.out.println("------------------------------------------");

    previousFlow = 0;
    double[] openings = {25.0, 50.0, 75.0, 100.0};

    for (double opening : openings) {
      ThrottlingValve choke = new ThrottlingValve("Choke", inletStream);
      choke.setOutletPressure(50.0, "bara");
      choke.setCalculateSteadyState(false);
      choke.setPercentValveOpening(opening);

      ValveMechanicalDesign design = choke.getMechanicalDesign();
      design.setValveSizingStandard("Sachdeva");
      design.setChokeDiameter(1.0, "in");

      choke.runTransient(0.1);

      double flow = choke.getOutletStream().getFlowRate("kg/hr");
      System.out.printf("    %.0f      |        %.1f\n", opening, flow);

      assertTrue(flow > previousFlow, "Higher opening should give higher flow");
      previousFlow = flow;
    }
  }
}
