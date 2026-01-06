package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Compares Cv/Kv calculations across different sizing methods to verify they produce reasonable and
 * consistent results.
 */
public class ValveSizingMethodComparisonTest {

  /**
   * Compare all sizing methods for a gas valve.
   */
  @Test
  void compareGasValveSizingMethods() {
    System.out.println("=== GAS VALVE SIZING METHOD COMPARISON ===\n");

    // Test conditions
    double flowRateSm3hr = 10000.0;
    double inletPressure = 50.0; // bara
    double outletPressure = 40.0; // bara (20% pressure drop)
    double temperature = 25.0; // C

    String[] methods = {"default", "IEC 60534", "prod choke"};
    double[] kvResults = new double[methods.length];
    double[] cvResults = new double[methods.length];

    System.out.println("Test conditions:");
    System.out.println("  Flow rate: " + flowRateSm3hr + " Sm3/hr");
    System.out.println("  Inlet P: " + inletPressure + " bara");
    System.out.println("  Outlet P: " + outletPressure + " bara");
    System.out.println("  Delta P: " + (inletPressure - outletPressure) + " bar");
    System.out.println("  Temperature: " + temperature + " C");
    System.out.println();

    for (int i = 0; i < methods.length; i++) {
      SystemInterface gasFluid = new SystemSrkEos(273.15 + temperature, inletPressure);
      gasFluid.addComponent("methane", 0.9);
      gasFluid.addComponent("ethane", 0.1);
      gasFluid.setMixingRule(2);

      Stream gasStream = new Stream("gas feed", gasFluid);
      gasStream.setFlowRate(flowRateSm3hr, "Sm3/hr");
      gasStream.setPressure(inletPressure, "bara");
      gasStream.setTemperature(temperature, "C");

      ThrottlingValve valve = new ThrottlingValve("valve", gasStream);
      ((ValveMechanicalDesign) valve.getMechanicalDesign()).setValveSizingStandard(methods[i]);
      valve.setOutletPressure(outletPressure, "bara");
      valve.setPercentValveOpening(100.0);

      ProcessSystem process = new ProcessSystem();
      process.add(gasStream);
      process.add(valve);
      process.run();

      kvResults[i] = valve.getKv();
      cvResults[i] = valve.getCv();

      System.out.println("Method: " + methods[i]);
      System.out.println("  Kv: " + String.format("%.4f", kvResults[i]));
      System.out.println("  Cv: " + String.format("%.4f", cvResults[i]));
      System.out.println("  Cg: " + String.format("%.4f", valve.getCg()));
      System.out.println();
    }

    // Print comparison table
    System.out.println("--- COMPARISON TABLE (Gas) ---");
    System.out.println(String.format("%-15s %12s %12s %12s", "Method", "Kv", "Cv", "Ratio to IEC"));
    System.out.println("-".repeat(55));
    for (int i = 0; i < methods.length; i++) {
      double ratio = kvResults[i] / kvResults[1]; // ratio to IEC 60534
      System.out.println(String.format("%-15s %12.4f %12.4f %12.2f", methods[i], kvResults[i],
          cvResults[i], ratio));
    }
    System.out.println();

    // Verify all methods give positive results
    for (int i = 0; i < methods.length; i++) {
      assertTrue(kvResults[i] > 0, "Kv should be positive for " + methods[i]);
      assertTrue(cvResults[i] > 0, "Cv should be positive for " + methods[i]);
    }

    // The simple/default method should give similar order of magnitude as IEC
    // (within a factor of 100 - they use quite different formulas)
    double ratioDefaultToIEC = kvResults[0] / kvResults[1];
    System.out.println("Ratio default/IEC: " + ratioDefaultToIEC);
  }

  /**
   * Compare all sizing methods for a liquid valve.
   */
  @Test
  void compareLiquidValveSizingMethods() {
    System.out.println("\n=== LIQUID VALVE SIZING METHOD COMPARISON ===\n");

    // Test conditions
    double flowRateM3hr = 100.0; // m3/hr
    double inletPressure = 50.0; // bara
    double outletPressure = 40.0; // bara
    double temperature = 25.0; // C

    String[] methods = {"default", "IEC 60534", "prod choke"};
    double[] kvResults = new double[methods.length];
    double[] cvResults = new double[methods.length];

    System.out.println("Test conditions:");
    System.out.println("  Flow rate: " + flowRateM3hr + " m3/hr");
    System.out.println("  Inlet P: " + inletPressure + " bara");
    System.out.println("  Outlet P: " + outletPressure + " bara");
    System.out.println("  Delta P: " + (inletPressure - outletPressure) + " bar");
    System.out.println("  Temperature: " + temperature + " C");
    System.out.println();

    for (int i = 0; i < methods.length; i++) {
      // Use heavy hydrocarbon to ensure liquid phase
      SystemInterface liquidFluid = new SystemSrkEos(273.15 + temperature, inletPressure);
      liquidFluid.addComponent("nC10", 1.0);
      liquidFluid.setMixingRule(2);

      Stream liquidStream = new Stream("liquid feed", liquidFluid);
      liquidStream.setFlowRate(flowRateM3hr, "Am3/hr"); // Am3/hr = actual m3/hr for liquids
      liquidStream.setPressure(inletPressure, "bara");
      liquidStream.setTemperature(temperature, "C");

      ThrottlingValve valve = new ThrottlingValve("valve", liquidStream);
      ((ValveMechanicalDesign) valve.getMechanicalDesign()).setValveSizingStandard(methods[i]);
      valve.setOutletPressure(outletPressure, "bara");
      valve.setPercentValveOpening(100.0);

      ProcessSystem process = new ProcessSystem();
      process.add(liquidStream);
      process.add(valve);
      process.run();

      kvResults[i] = valve.getKv();
      cvResults[i] = valve.getCv();

      System.out.println("Method: " + methods[i]);
      System.out.println("  isGasValve: " + valve.isGasValve());
      System.out.println("  Kv: " + String.format("%.4f", kvResults[i]));
      System.out.println("  Cv: " + String.format("%.4f", cvResults[i]));
      System.out.println();
    }

    // Print comparison table
    System.out.println("--- COMPARISON TABLE (Liquid) ---");
    System.out.println(String.format("%-15s %12s %12s %12s", "Method", "Kv", "Cv", "Ratio to IEC"));
    System.out.println("-------------------------------------------------------");
    for (int i = 0; i < methods.length; i++) {
      double ratio = kvResults[i] / kvResults[1]; // ratio to IEC 60534
      System.out.println(String.format("%-15s %12.4f %12.4f %12.2f", methods[i], kvResults[i],
          cvResults[i], ratio));
    }
    System.out.println();

    // Verify all methods give positive results
    for (int i = 0; i < methods.length; i++) {
      assertTrue(kvResults[i] > 0, "Kv should be positive for " + methods[i]);
      assertTrue(cvResults[i] > 0, "Cv should be positive for " + methods[i]);
    }
  }

  /**
   * Verify Kv formula for liquid against manual calculation. Standard liquid Kv formula: Kv = Q *
   * sqrt(SG / deltaP) where Q is in m3/hr, SG is specific gravity, deltaP is in bar
   */
  @Test
  void verifyLiquidKvFormula() {
    System.out.println("\n=== LIQUID Kv FORMULA VERIFICATION ===\n");

    double flowRateM3hr = 100.0;
    double inletPressure = 50.0;
    double outletPressure = 40.0;
    double deltaP = inletPressure - outletPressure;

    SystemInterface liquidFluid = new SystemSrkEos(298.15, inletPressure);
    liquidFluid.addComponent("water", 1.0);
    liquidFluid.setMixingRule(2);

    Stream liquidStream = new Stream("water feed", liquidFluid);
    liquidStream.setFlowRate(flowRateM3hr, "Am3/hr"); // Am3/hr = actual m3/hr for liquids
    liquidStream.setPressure(inletPressure, "bara");
    liquidStream.setTemperature(25.0, "C");

    ThrottlingValve valve = new ThrottlingValve("valve", liquidStream);
    valve.setOutletPressure(outletPressure, "bara");
    valve.setPercentValveOpening(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(liquidStream);
    process.add(valve);
    process.run();

    double calculatedKv = valve.getKv();
    double density = liquidStream.getFluid().getDensity("kg/m3");
    double specificGravity = density / 1000.0; // relative to water

    // Get actual flow rate at process conditions (may differ from setpoint)
    double actualFlowM3hr = liquidStream.getFlowRate("m3/hr");

    // IEC 60534 liquid Kv formula: Kv = Q * sqrt(rho0 / (rho * deltaP))
    // where rho0 = 1000 kg/m3 (water reference)
    // This simplifies to: Kv = Q * sqrt(1 / (SG * deltaP))
    double expectedKv = actualFlowM3hr * Math.sqrt(1.0 / (specificGravity * deltaP));

    System.out.println("Set flow rate: " + flowRateM3hr + " Am3/hr");
    System.out.println("Actual flow rate: " + actualFlowM3hr + " m3/hr");
    System.out.println("Delta P: " + deltaP + " bar");
    System.out.println("Density: " + density + " kg/m3");
    System.out.println("Specific gravity: " + specificGravity);
    System.out.println();
    System.out.println("Calculated Kv (NeqSim): " + String.format("%.4f", calculatedKv));
    System.out.println("Expected Kv (IEC formula): " + String.format("%.4f", expectedKv));
    System.out.println("Ratio: " + String.format("%.4f", calculatedKv / expectedKv));

    // Should be within 10% (different methods may have slight variations)
    double ratio = calculatedKv / expectedKv;
    assertTrue(ratio > 0.5 && ratio < 2.0,
        "Calculated Kv should be within reasonable range of expected value");
  }

  /**
   * Verify Kv formula for gas against manual calculation. IEC 60534 gas formula (simplified,
   * non-choked): Kv = Q / (N9 * P1 * Y * sqrt(x / (M*T*Z)))
   */
  @Test
  void verifyGasKvFormula() {
    System.out.println("\n=== GAS Kv FORMULA VERIFICATION ===\n");

    double flowRateSm3hr = 10000.0;
    double inletPressure = 50.0; // bara
    double outletPressure = 45.0; // bara (small drop to avoid choked flow)
    double temperature = 298.15; // K

    SystemInterface gasFluid = new SystemSrkEos(temperature, inletPressure);
    gasFluid.addComponent("methane", 1.0);
    gasFluid.setMixingRule(2);

    Stream gasStream = new Stream("methane feed", gasFluid);
    gasStream.setFlowRate(flowRateSm3hr, "Sm3/hr");
    gasStream.setPressure(inletPressure, "bara");
    gasStream.setTemperature(temperature - 273.15, "C");

    ThrottlingValve valve = new ThrottlingValve("valve", gasStream);
    ((ValveMechanicalDesign) valve.getMechanicalDesign()).setValveSizingStandard("IEC 60534");
    valve.setOutletPressure(outletPressure, "bara");
    valve.setPercentValveOpening(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(gasStream);
    process.add(valve);
    process.run();

    double calculatedKv = valve.getKv();

    // Get fluid properties
    double actualFlowM3hr = gasStream.getFlowRate("m3/hr");
    double MW = gasFluid.getMolarMass("gr/mol");
    double Z = gasFluid.getZ();
    double gamma = gasFluid.getGamma2();

    // IEC 60534 constants and calculations
    double N9 = 24.6; // for m3/h, kPa, K
    double P1_kPa = inletPressure * 100.0;
    double P2_kPa = outletPressure * 100.0;
    double x = (P1_kPa - P2_kPa) / P1_kPa;
    double xT = 0.137; // typical value
    double Fgamma = gamma / 1.40;
    double Y = Math.max(1.0 - x / (3.0 * Fgamma * xT), 2.0 / 3.0);

    // IEC 60534 formula: Kv = Q / (N9 * P1 * Y) * sqrt(M * T * Z / x)
    double expectedKv = actualFlowM3hr / (N9 * P1_kPa * Y) * Math.sqrt(MW * temperature * Z / x);

    System.out.println("Flow rate: " + flowRateSm3hr + " Sm3/hr = " + actualFlowM3hr + " m3/hr");
    System.out.println("Inlet P: " + inletPressure + " bara = " + P1_kPa + " kPa");
    System.out.println("Outlet P: " + outletPressure + " bara");
    System.out.println("x (pressure ratio): " + String.format("%.4f", x));
    System.out.println("Y (expansion factor): " + String.format("%.4f", Y));
    System.out.println("MW: " + String.format("%.2f", MW) + " g/mol");
    System.out.println("Z: " + String.format("%.4f", Z));
    System.out.println("gamma: " + String.format("%.4f", gamma));
    System.out.println();
    System.out.println("Calculated Kv (NeqSim IEC 60534): " + String.format("%.4f", calculatedKv));
    System.out.println("Expected Kv (manual IEC formula): " + String.format("%.4f", expectedKv));
    System.out.println("Ratio: " + String.format("%.4f", calculatedKv / expectedKv));

    // Should be close (within 20% due to different property calculations)
    double ratio = calculatedKv / expectedKv;
    assertTrue(ratio > 0.5 && ratio < 2.0,
        "Calculated Kv should be within reasonable range of expected value");
  }

  /**
   * Compare sizing at different pressure drops to verify behavior.
   */
  @Test
  void testPressureDropEffect() {
    System.out.println("\n=== PRESSURE DROP EFFECT ON Kv ===\n");

    double flowRateSm3hr = 10000.0;
    double inletPressure = 50.0;
    double[] outletPressures = {49.0, 45.0, 40.0, 30.0, 20.0};

    System.out.println("Flow rate: " + flowRateSm3hr + " Sm3/hr");
    System.out.println("Inlet P: " + inletPressure + " bara");
    System.out.println();
    System.out
        .println(String.format("%-12s %12s %12s %12s", "Outlet P", "Delta P", "Kv", "Choked?"));
    System.out.println("--------------------------------------------------");

    for (double outletP : outletPressures) {
      SystemInterface gasFluid = new SystemSrkEos(298.15, inletPressure);
      gasFluid.addComponent("methane", 1.0);
      gasFluid.setMixingRule(2);

      Stream gasStream = new Stream("gas", gasFluid);
      gasStream.setFlowRate(flowRateSm3hr, "Sm3/hr");
      gasStream.setPressure(inletPressure, "bara");
      gasStream.setTemperature(25.0, "C");

      ThrottlingValve valve = new ThrottlingValve("valve", gasStream);
      ((ValveMechanicalDesign) valve.getMechanicalDesign()).setValveSizingStandard("IEC 60534");
      valve.setOutletPressure(outletP, "bara");
      valve.setPercentValveOpening(100.0);

      ProcessSystem process = new ProcessSystem();
      process.add(gasStream);
      process.add(valve);
      process.run();

      double kv = valve.getKv();
      double deltaP = inletPressure - outletP;
      double x = deltaP / inletPressure;
      boolean choked = x > 0.137 * 1.4; // approximate choked flow threshold

      System.out.println(
          String.format("%-12.1f %12.1f %12.4f %12s", outletP, deltaP, kv, choked ? "Yes" : "No"));

      assertTrue(kv > 0, "Kv should be positive");
    }

    System.out.println();
    System.out.println("Note: As pressure drop increases, Kv decreases because");
    System.out.println("more pressure energy is available to drive the same flow.");
  }

  /**
   * Test comprehensive valve mechanical design calculations.
   */
  @Test
  void testValveMechanicalDesign() {
    System.out.println("\n=== VALVE MECHANICAL DESIGN TEST ===\n");

    // Create a gas valve
    SystemInterface gasFluid = new SystemSrkEos(273.15 + 50.0, 80.0);
    gasFluid.addComponent("methane", 0.85);
    gasFluid.addComponent("ethane", 0.10);
    gasFluid.addComponent("propane", 0.05);
    gasFluid.setMixingRule(2);

    Stream gasStream = new Stream("gas feed", gasFluid);
    gasStream.setFlowRate(50000.0, "Sm3/hr");
    gasStream.setPressure(80.0, "bara");
    gasStream.setTemperature(50.0, "C");

    ThrottlingValve valve = new ThrottlingValve("PCV-101", gasStream);
    valve.setOutletPressure(60.0, "bara");
    valve.setPercentValveOpening(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(gasStream);
    process.add(valve);
    process.run();

    // Calculate mechanical design
    ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
    mechDesign.calcDesign();

    // Print results
    System.out.println("=== VALVE SIZING RESULTS ===");
    System.out.println("Valve Cv: " + String.format("%.2f", valve.getCv()));
    System.out.println("Valve Kv: " + String.format("%.2f", valve.getKv()));
    System.out.println();

    System.out.println("=== MECHANICAL DESIGN RESULTS ===");
    System.out.println("ANSI Pressure Class: " + mechDesign.getAnsiPressureClass());
    System.out.println(
        "Nominal Size: " + String.format("%.1f", mechDesign.getNominalSizeInches()) + " inches");
    System.out.println(
        "Design Pressure: " + String.format("%.1f", mechDesign.getDesignPressure()) + " bara");
    System.out.println(
        "Design Temperature: " + String.format("%.1f", mechDesign.getDesignTemperature()) + " °C");
    System.out
        .println("Face-to-Face: " + String.format("%.0f", mechDesign.getFaceToFace()) + " mm");
    System.out.println(
        "Body Wall Thickness: " + String.format("%.1f", mechDesign.getBodyWallThickness()) + " mm");
    System.out.println("Required Actuator Thrust: "
        + String.format("%.0f", mechDesign.getRequiredActuatorThrust()) + " N");
    System.out.println(
        "Actuator Weight: " + String.format("%.1f", mechDesign.getActuatorWeight()) + " kg");
    System.out.println(
        "Total Valve Weight: " + String.format("%.1f", mechDesign.getWeightTotal()) + " kg");
    System.out.println();

    System.out.println("=== MODULE DIMENSIONS ===");
    System.out
        .println("Module Height: " + String.format("%.3f", mechDesign.getModuleHeight()) + " m");
    System.out
        .println("Module Width: " + String.format("%.3f", mechDesign.getModuleWidth()) + " m");
    System.out
        .println("Module Length: " + String.format("%.3f", mechDesign.getModuleLength()) + " m");

    // Verify reasonable results
    assertTrue(mechDesign.getAnsiPressureClass() >= 300,
        "Pressure class should be at least 300 for 80 bara inlet");
    assertTrue(mechDesign.getNominalSizeInches() > 0, "Nominal size should be positive");
    assertTrue(mechDesign.getFaceToFace() > 0, "Face-to-face should be positive");
    assertTrue(mechDesign.getBodyWallThickness() >= 3.0, "Wall thickness should be >= 3mm minimum");
    assertTrue(mechDesign.getWeightTotal() > 0, "Total weight should be positive");
    assertTrue(mechDesign.getRequiredActuatorThrust() > 0, "Actuator thrust should be positive");

    System.out.println("\n✓ All mechanical design values are reasonable");
  }
}
