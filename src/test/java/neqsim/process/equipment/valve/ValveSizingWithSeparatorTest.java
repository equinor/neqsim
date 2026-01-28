package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests to verify that Cv/Kv is calculated correctly for valves connected to separator outlets.
 * This ensures proper sizing for both gas and liquid valves in typical process configurations.
 */
public class ValveSizingWithSeparatorTest {
  /**
   * Test that gas and liquid valves connected to separator outlets get correctly calculated Cv/Kv*
   * values based on their respective flow rates and pressure drops.
   */

  @Test
  void testGasAndLiquidValveCvCalculation() {
    // Create a two-phase feed (gas + liquid)
    SystemInterface feedFluid = new SystemSrkEos(298.15, 50.0);
    feedFluid.addComponent("methane", 0.8); // Gas component
    feedFluid.addComponent("ethane", 0.1);
    feedFluid.addComponent("propane", 0.05);
    feedFluid.addComponent("nC10", 0.05); // Liquid component
    feedFluid.setMixingRule(2);

    Stream feedStream = new Stream("feed", feedFluid);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setPressure(50.0, "bara");
    feedStream.setTemperature(25.0, "C");

    // Create separator
    Separator separator = new Separator("test separator", feedStream);

    // Create gas outlet valve at 50% opening
    ThrottlingValve gasValve = new ThrottlingValve("gas valve", separator.getGasOutStream());
    gasValve.setOutletPressure(40.0, "bara");
    gasValve.setPercentValveOpening(50.0);

    // Create liquid outlet valve at 50% opening
    ThrottlingValve liquidValve =
        new ThrottlingValve("liquid valve", separator.getLiquidOutStream());
    liquidValve.setOutletPressure(40.0, "bara");
    liquidValve.setPercentValveOpening(50.0);

    // Build and run process
    ProcessSystem process = new ProcessSystem();
    process.add(feedStream);
    process.add(separator);
    process.add(gasValve);
    process.add(liquidValve);
    process.run();

    // Get the calculated Kv values
    double gasKv = gasValve.getKv();
    double liquidKv = liquidValve.getKv();
    double gasCv = gasValve.getCv();
    double liquidCv = liquidValve.getCv();

    // Print values for debugging
    System.out.println("=== Valve Sizing Results ===");
    System.out.println("Gas valve:");
    System.out.println("  Flow rate: " + gasValve.getInletStream().getFlowRate("kg/hr") + " kg/hr");
    System.out
        .println("  Flow rate: " + gasValve.getInletStream().getFlowRate("Sm3/hr") + " Sm3/hr");
    System.out.println("  Inlet P: " + gasValve.getInletStream().getPressure("bara") + " bara");
    System.out.println("  Outlet P: " + gasValve.getOutletStream().getPressure("bara") + " bara");
    System.out.println("  Kv: " + gasKv);
    System.out.println("  Cv: " + gasCv);
    System.out.println("  isGasValve: " + gasValve.isGasValve());

    System.out.println("Liquid valve:");
    System.out
        .println("  Flow rate: " + liquidValve.getInletStream().getFlowRate("kg/hr") + " kg/hr");
    System.out
        .println("  Flow rate: " + liquidValve.getInletStream().getFlowRate("m3/hr") + " m3/hr");
    System.out.println("  Inlet P: " + liquidValve.getInletStream().getPressure("bara") + " bara");
    System.out
        .println("  Outlet P: " + liquidValve.getOutletStream().getPressure("bara") + " bara");
    System.out.println("  Kv: " + liquidKv);
    System.out.println("  Cv: " + liquidCv);
    System.out.println("  isGasValve: " + liquidValve.isGasValve());

    // Verify basic sanity checks
    assertTrue(gasKv > 0, "Gas valve Kv should be positive");
    assertTrue(liquidKv > 0, "Liquid valve Kv should be positive");
    assertTrue(gasCv > 0, "Gas valve Cv should be positive");
    assertTrue(liquidCv > 0, "Liquid valve Cv should be positive");

    // Verify Cv = Kv * 1.156 (conversion factor)
    assertEquals(gasKv * 1.156, gasCv, 0.01, "Gas Cv should equal Kv * 1.156");
    assertEquals(liquidKv * 1.156, liquidCv, 0.01, "Liquid Cv should equal Kv * 1.156");

    // Verify gas valve is detected as gas and liquid valve is detected as liquid
    assertTrue(gasValve.isGasValve(), "Gas valve should be detected as gas valve");
    assertTrue(!liquidValve.isGasValve(), "Liquid valve should be detected as liquid valve");

    // Verify the Kv calculation is consistent:
    // If we use the calculated Kv at 100% opening, we should get roughly the same flow
    // Since valves were sized at 50%, the Kv at 100% should give 2x the flow for linear
    // characteristic

    // Store the Kv values (these are at 100% equivalent)
    double gasKvAt100 = gasKv;
    double liquidKvAt100 = liquidKv;

    // Now verify by running transient - the flow should remain the same
    gasValve.setCalculateSteadyState(false);
    liquidValve.setCalculateSteadyState(false);

    double gasFlowBefore = gasValve.getOutletStream().getFlowRate("kg/hr");
    double liquidFlowBefore = liquidValve.getOutletStream().getFlowRate("kg/hr");

    gasValve.runTransient(0.1);
    liquidValve.runTransient(0.1);

    double gasFlowAfter = gasValve.getOutletStream().getFlowRate("kg/hr");
    double liquidFlowAfter = liquidValve.getOutletStream().getFlowRate("kg/hr");

    // Flow should remain approximately the same since conditions haven't changed
    assertEquals(gasFlowBefore, gasFlowAfter, gasFlowBefore * 0.01,
        "Gas flow should remain stable after transient run");
    assertEquals(liquidFlowBefore, liquidFlowAfter, liquidFlowBefore * 0.01,
        "Liquid flow should remain stable after transient run");
  }

  /**
   * Test that valve Kv scales correctly with valve opening percentage using linear characteristic.
   */
  @Test
  void testKvScalingWithOpening() {
    // Create a simple gas stream
    SystemInterface gasFluid = new SystemSrkEos(298.15, 50.0);
    gasFluid.addComponent("methane", 1.0);
    gasFluid.setMixingRule(2);

    Stream gasStream = new Stream("gas feed", gasFluid);
    gasStream.setFlowRate(5000.0, "Sm3/hr");
    gasStream.setPressure(50.0, "bara");
    gasStream.setTemperature(25.0, "C");

    // Create valve at 100% opening
    ThrottlingValve valve100 = new ThrottlingValve("valve 100%", gasStream);
    valve100.setOutletPressure(40.0, "bara");
    valve100.setPercentValveOpening(100.0);

    ProcessSystem process1 = new ProcessSystem();
    process1.add(gasStream);
    process1.add(valve100);
    process1.run();

    double kvAt100 = valve100.getKv();
    System.out.println("\n=== Kv Scaling Test ===");
    System.out.println("Kv at 100% opening: " + kvAt100);

    // Create identical setup at 50% opening
    SystemInterface gasFluid2 = new SystemSrkEos(298.15, 50.0);
    gasFluid2.addComponent("methane", 1.0);
    gasFluid2.setMixingRule(2);

    Stream gasStream2 = new Stream("gas feed 2", gasFluid2);
    gasStream2.setFlowRate(5000.0, "Sm3/hr");
    gasStream2.setPressure(50.0, "bara");
    gasStream2.setTemperature(25.0, "C");

    ThrottlingValve valve50 = new ThrottlingValve("valve 50%", gasStream2);
    valve50.setOutletPressure(40.0, "bara");
    valve50.setPercentValveOpening(50.0);

    ProcessSystem process2 = new ProcessSystem();
    process2.add(gasStream2);
    process2.add(valve50);
    process2.run();

    double kvAt50 = valve50.getKv();
    System.out.println("Kv at 50% opening: " + kvAt50);

    // For linear characteristic: Kv(50%) should be 2x Kv(100%)
    // Because Kv represents the valve coefficient at 100% opening
    // When sized at 50%, the 100%-equivalent Kv must be larger
    assertEquals(kvAt100 * 2.0, kvAt50, kvAt100 * 0.05,
        "Kv sized at 50% opening should be 2x larger than Kv sized at 100%");
  }

  /**
   * Test recalculation of Kv when conditions change.
   */
  @Test
  void testKvRecalculation() {
    SystemInterface gasFluid = new SystemSrkEos(298.15, 50.0);
    gasFluid.addComponent("methane", 1.0);
    gasFluid.setMixingRule(2);

    Stream gasStream = new Stream("gas feed", gasFluid);
    gasStream.setFlowRate(5000.0, "Sm3/hr");
    gasStream.setPressure(50.0, "bara");
    gasStream.setTemperature(25.0, "C");

    ThrottlingValve valve = new ThrottlingValve("test valve", gasStream);
    valve.setOutletPressure(40.0, "bara");
    valve.setPercentValveOpening(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(gasStream);
    process.add(valve);
    process.run();

    double kvInitial = valve.getKv();
    System.out.println("\n=== Kv Recalculation Test ===");
    System.out.println("Initial Kv: " + kvInitial);

    // Change flow rate
    gasStream.setFlowRate(10000.0, "Sm3/hr");

    // Run again - Kv should NOT change (already set)
    process.run();
    double kvAfterFlowChange = valve.getKv();
    System.out.println("Kv after flow change (no recalc): " + kvAfterFlowChange);
    assertEquals(kvInitial, kvAfterFlowChange, 0.001,
        "Kv should not change without explicit recalculation");

    // Force recalculation
    valve.setValveKvSet(false);
    process.run();
    double kvRecalculated = valve.getKv();
    System.out.println("Kv after forced recalculation: " + kvRecalculated);

    // Kv should be approximately 2x now (double the flow)
    assertEquals(kvInitial * 2.0, kvRecalculated, kvInitial * 0.1,
        "Recalculated Kv should be ~2x for double flow rate");
  }

  /**
   * Verify flow calculation using the stored Kv matches actual flow in transient mode.
   */
  @Test
  void testTransientFlowMatchesSteadyState() {
    // Create two-phase feed
    SystemInterface feedFluid = new SystemSrkEos(298.15, 30.0);
    feedFluid.addComponent("methane", 0.7);
    feedFluid.addComponent("propane", 0.2);
    feedFluid.addComponent("nC10", 0.1);
    feedFluid.setMixingRule(2);

    Stream feedStream = new Stream("feed", feedFluid);
    feedStream.setFlowRate(5000.0, "kg/hr");
    feedStream.setPressure(30.0, "bara");
    feedStream.setTemperature(40.0, "C");

    Separator separator = new Separator("separator", feedStream);

    ThrottlingValve gasValve = new ThrottlingValve("gas valve", separator.getGasOutStream());
    gasValve.setOutletPressure(20.0, "bara");
    gasValve.setPercentValveOpening(75.0);

    ThrottlingValve liquidValve =
        new ThrottlingValve("liquid valve", separator.getLiquidOutStream());
    liquidValve.setOutletPressure(20.0, "bara");
    liquidValve.setPercentValveOpening(75.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feedStream);
    process.add(separator);
    process.add(gasValve);
    process.add(liquidValve);

    // Run steady state
    process.run();

    double gasFlowSteady = gasValve.getOutletStream().getFlowRate("kg/hr");
    double liquidFlowSteady = liquidValve.getOutletStream().getFlowRate("kg/hr");
    double gasKv = gasValve.getKv();
    double liquidKv = liquidValve.getKv();

    System.out.println("\n=== Steady vs Transient Flow Test ===");
    System.out.println("Steady state gas flow: " + gasFlowSteady + " kg/hr");
    System.out.println("Steady state liquid flow: " + liquidFlowSteady + " kg/hr");
    System.out.println("Gas Kv: " + gasKv);
    System.out.println("Liquid Kv: " + liquidKv);

    // Switch to transient mode
    gasValve.setCalculateSteadyState(false);
    liquidValve.setCalculateSteadyState(false);
    separator.setCalculateSteadyState(false);

    // Run a few transient steps
    process.setTimeStep(0.1);
    for (int i = 0; i < 5; i++) {
      process.runTransient();
    }

    double gasFlowTransient = gasValve.getOutletStream().getFlowRate("kg/hr");
    double liquidFlowTransient = liquidValve.getOutletStream().getFlowRate("kg/hr");

    System.out.println("Transient gas flow: " + gasFlowTransient + " kg/hr");
    System.out.println("Transient liquid flow: " + liquidFlowTransient + " kg/hr");

    // Flows should be similar (within 5%) since conditions haven't changed significantly
    assertEquals(gasFlowSteady, gasFlowTransient, gasFlowSteady * 0.05,
        "Transient gas flow should match steady state");
    assertEquals(liquidFlowSteady, liquidFlowTransient, liquidFlowSteady * 0.05,
        "Transient liquid flow should match steady state");
  }

  /**
   * Test IEC 60534 standard for gas valve sizing.
   */
  @Test
  void testIEC60534GasValveSizing() {
    SystemInterface gasFluid = new SystemSrkEos(298.15, 50.0);
    gasFluid.addComponent("methane", 0.9);
    gasFluid.addComponent("ethane", 0.1);
    gasFluid.setMixingRule(2);

    Stream gasStream = new Stream("gas feed", gasFluid);
    gasStream.setFlowRate(10000.0, "Sm3/hr");
    gasStream.setPressure(50.0, "bara");
    gasStream.setTemperature(25.0, "C");

    ThrottlingValve valve = new ThrottlingValve("IEC valve", gasStream);
    ((ValveMechanicalDesign) valve.getMechanicalDesign()).setValveSizingStandard("IEC 60534");
    valve.setOutletPressure(45.0, "bara"); // 10% pressure drop
    valve.setPercentValveOpening(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(gasStream);
    process.add(valve);
    process.run();

    double kv = valve.getKv();
    double cv = valve.getCv();

    System.out.println("\n=== IEC 60534 Gas Valve Test ===");
    System.out.println("Flow: " + gasStream.getFlowRate("Sm3/hr") + " Sm3/hr");
    System.out.println("Inlet P: " + valve.getInletStream().getPressure("bara") + " bara");
    System.out.println("Outlet P: " + valve.getOutletStream().getPressure("bara") + " bara");
    System.out.println("Kv (IEC 60534): " + kv);
    System.out.println("Cv (IEC 60534): " + cv);
    System.out.println("Cg: " + valve.getCg());

    assertTrue(kv > 0, "Kv should be positive");
    assertTrue(cv > 0, "Cv should be positive");

    // Verify the valve is detected as gas valve
    assertTrue(valve.isGasValve(), "Should be detected as gas valve");
  }
}
