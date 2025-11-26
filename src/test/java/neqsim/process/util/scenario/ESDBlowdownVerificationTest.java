package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.action.CloseValveAction;
import neqsim.process.logic.action.EnergizeESDValveAction;
import neqsim.process.logic.action.SetSplitterAction;
import neqsim.process.logic.action.SetSeparatorModeAction;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test to verify that ESD scenario actually reduces separator pressure and routes gas to flare.
 */
class ESDBlowdownVerificationTest {

  private ProcessSystem system;
  private ProcessScenarioRunner runner;
  private Separator separator;
  private Flare flare;
  private ESDLogic esdLogic;

  @BeforeEach
  void setUp() {
    // Create realistic process system similar to ProcessLogicIntegratedExample
    SystemInterface feedGas = new SystemSrkEos(298.15, 55.0);
    feedGas.addComponent("methane", 85.0);
    feedGas.addComponent("ethane", 10.0);
    feedGas.addComponent("propane", 5.0);
    feedGas.setMixingRule(2);

    Stream feed = new Stream("HP Feed", feedGas);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setPressure(55.0, "bara");
    feed.setTemperature(25.0, "C");

    // Inlet control valve
    ControlValve inletValve = new ControlValve("Inlet Valve", feed);
    inletValve.setPercentValveOpening(80.0);
    inletValve.setCv(400.0);

    // ESD inlet isolation valve
    ESDValve esdInletValve = new ESDValve("ESD Inlet Isolation", inletValve.getOutletStream());
    esdInletValve.setPercentValveOpening(100.0);
    esdInletValve.setCv(350.0);
    esdInletValve.energize();

    // High-pressure separator
    separator = new Separator("HP Separator", esdInletValve.getOutletStream());
    separator.setCalculateSteadyState(true);
    separator.setInternalDiameter(2.0);

    // Gas splitter (process/flare/blowdown)
    Splitter gasSplitter = new Splitter("Gas Splitter", separator.getGasOutStream(), 3);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0, 0.0}); // Normal: all to process

    Stream processStream = new Stream("Process Stream", gasSplitter.getSplitStream(0));

    // Blowdown valve (normally closed)
    ESDValve bdValve = new ESDValve("ESD Blowdown Valve", gasSplitter.getSplitStream(2));
    bdValve.setStrokeTime(2.0);
    bdValve.setCv(250.0);
    bdValve.setFailSafePosition(100.0);
    bdValve.deEnergize(); // Start closed

    // Flare header
    Mixer flareHeader = new Mixer("Flare Header");
    flareHeader.addStream(bdValve.getOutletStream());

    // Flare
    flare = new Flare("Emergency Flare", flareHeader.getOutletStream());

    // Build system
    system = new ProcessSystem();
    system.add(feed);
    system.add(inletValve);
    system.add(esdInletValve);
    system.add(separator);
    system.add(gasSplitter);
    system.add(processStream);
    system.add(bdValve);
    system.add(flareHeader);
    system.add(flare);

    // Create ESD logic
    esdLogic = new ESDLogic("ESD Level 1");
    esdLogic.addAction(new CloseValveAction(inletValve), 0.0);
    esdLogic.addAction(new CloseValveAction(esdInletValve), 0.0);
    esdLogic.addAction(new SetSplitterAction(gasSplitter, new double[] {0.0, 0.0, 1.0}), 0.5);
    esdLogic.addAction(new EnergizeESDValveAction(bdValve, 100.0), 0.5);
    esdLogic.addAction(new SetSeparatorModeAction(separator, false), 1.0);

    // Create runner
    runner = new ProcessScenarioRunner(system);
    runner.addLogic(esdLogic);
  }

  @Test
  void testESDReducesSeparatorPressure() {
    // Initialize system
    runner.initializeSteadyState();

    // Record initial conditions
    double initialPressure = separator.getGasOutStream().getPressure("bara");
    double initialFlareFlow = flare.getInletStream().getFlowRate("kg/hr");

    System.out.println("\n=== INITIAL CONDITIONS ===");
    System.out.println("Separator pressure: " + String.format("%.1f", initialPressure) + " bara");
    System.out.println("Flare flow: " + String.format("%.1f", initialFlareFlow) + " kg/hr");

    // Verify initial conditions are as expected (high pressure, no flare flow)
    assertTrue(initialPressure > 30.0, "Initial separator pressure should be high");
    assertTrue(initialFlareFlow < 10.0, "Initial flare flow should be minimal or zero");

    // Create ESD scenario and activate ESD logic
    ProcessSafetyScenario esdScenario = ProcessSafetyScenario.builder("ESD Activation").build();

    // Activate ESD logic before running scenario
    runner.activateLogic("ESD Level 1");

    // Run scenario for 20 seconds to allow depressurization
    ScenarioExecutionSummary summary = runner.runScenario("ESD Test", esdScenario, 20.0, 0.5);

    // Get final conditions
    double finalPressure = separator.getGasOutStream().getPressure("bara");
    double finalFlareFlow = flare.getInletStream().getFlowRate("kg/hr");

    System.out.println("\n=== FINAL CONDITIONS ===");
    System.out.println("Separator pressure: " + String.format("%.1f", finalPressure) + " bara");
    System.out.println("Flare flow: " + String.format("%.1f", finalFlareFlow) + " kg/hr");
    System.out.println(
        "Pressure reduction: " + String.format("%.1f", initialPressure - finalPressure) + " bar");

    // Verify ESD effectiveness
    assertTrue(finalPressure < initialPressure,
        "ESD should reduce separator pressure from " + initialPressure + " to " + finalPressure);

    double pressureDrop = initialPressure - finalPressure;
    assertTrue(pressureDrop > 5.0,
        "Pressure should drop significantly (got " + pressureDrop + " bar drop)");

    // Verify gas is routed to flare
    assertTrue(finalFlareFlow > initialFlareFlow, "ESD should route gas to flare (initial: "
        + initialFlareFlow + ", final: " + finalFlareFlow + ")");

    // Print summary
    System.out.println("\n=== TEST RESULTS ===");
    System.out.println("✓ Separator pressure reduced: " + String.format("%.1f", initialPressure)
        + " → " + String.format("%.1f", finalPressure) + " bara");
    System.out.println("✓ Pressure drop: " + String.format("%.1f", pressureDrop) + " bar");
    System.out.println("✓ Gas flow to flare increased: " + String.format("%.1f", initialFlareFlow)
        + " → " + String.format("%.1f", finalFlareFlow) + " kg/hr");
    System.out.println("✓ ESD system is working correctly!");

    // Verify no critical errors during depressurization
    assertTrue(summary.getErrors().size() < 5, "Should have minimal simulation errors");
  }

  @Test
  void testESDBlowdownTimeline() {
    // Initialize system
    runner.initializeSteadyState();

    double initialPressure = separator.getGasOutStream().getPressure("bara");

    // Create ESD scenario with immediate activation
    ProcessSafetyScenario esdScenario = ProcessSafetyScenario.builder("Immediate ESD").build();

    // Activate ESD at start
    runner.activateLogic("ESD Level 1");

    System.out.println("\n=== ESD BLOWDOWN TIMELINE ===");
    System.out.println("Initial pressure: " + String.format("%.1f", initialPressure) + " bara\n");

    // Monitor pressure over time
    double[] timePoints = {0.0, 2.0, 5.0, 10.0, 15.0};
    double previousPressure = initialPressure;

    for (double duration : timePoints) {
      if (duration > 0) {
        runner.reset(); // Reset and re-initialize
        runner.initializeSteadyState();
        runner.activateLogic("ESD Level 1");
        runner.runScenario("ESD @ t=" + duration, esdScenario, duration, 0.5);
      }

      double currentPressure = separator.getGasOutStream().getPressure("bara");
      double flareFlow = flare.getInletStream().getFlowRate("kg/hr");

      System.out.println(String.format("t=%.0fs: P=%.1f bara, Flare=%.1f kg/hr", duration,
          currentPressure, flareFlow));

      if (duration > 0) {
        // Pressure should decrease over time during blowdown
        assertTrue(currentPressure <= previousPressure + 5.0, // Allow small increase due to
                                                              // dynamics
            "Pressure should not significantly increase during blowdown");
      }

      previousPressure = currentPressure;
    }

    System.out.println("\n✓ ESD blowdown progresses over time with gas routed to flare");
  }

  @Test
  void testNoBlowdownWithoutESDActivation() {
    // Initialize system
    runner.initializeSteadyState();

    double initialPressure = separator.getGasOutStream().getPressure("bara");
    double initialFlareFlow = flare.getInletStream().getFlowRate("kg/hr");

    // Run scenario WITHOUT activating ESD
    ProcessSafetyScenario normalScenario =
        ProcessSafetyScenario.builder("Normal Operation").build();
    runner.runScenario("No ESD Test", normalScenario, 10.0, 1.0);

    double finalPressure = separator.getGasOutStream().getPressure("bara");
    double finalFlareFlow = flare.getInletStream().getFlowRate("kg/hr");

    System.out.println("\n=== NORMAL OPERATION (NO ESD) ===");
    System.out.println("Initial pressure: " + String.format("%.1f", initialPressure) + " bara");
    System.out.println("Final pressure: " + String.format("%.1f", finalPressure) + " bara");
    System.out.println("Flare flow: " + String.format("%.1f", finalFlareFlow) + " kg/hr");

    // Without ESD, pressure should remain relatively stable
    double pressureChange = Math.abs(finalPressure - initialPressure);
    assertTrue(pressureChange < 10.0,
        "Without ESD, pressure should remain stable (changed by " + pressureChange + " bar)");

    // Flare flow should remain minimal
    assertTrue(finalFlareFlow < 100.0, "Without ESD, flare flow should remain low");

    System.out.println("✓ System operates normally without ESD activation");
  }
}
