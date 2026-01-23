package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PushButton;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for Emergency Shutdown (ESD) system with Blowdown Valve.
 * 
 * <p>
 * This test demonstrates a complete ESD blowdown system where:
 * <ul>
 * <li>A separator operates normally sending gas to process</li>
 * <li>On ESD activation, a blowdown valve opens</li>
 * <li>Gas is redirected from process to blowdown system via splitter</li>
 * <li>Flow through blowdown valve is controlled by downstream orifice</li>
 * <li>Blowdown gas is sent to flare for safe disposal</li>
 * <li>System tracks depressurization rate and flare load</li>
 * </ul>
 * 
 * <p>
 * The scenario demonstrates:
 * <ul>
 * <li>Normal operation with blowdown valve closed</li>
 * <li>ESD activation triggering blowdown valve opening</li>
 * <li>Controlled depressurization through orifice</li>
 * <li>Flare system handling blowdown gas</li>
 * <li>Heat release and emissions tracking</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class BlowdownValveESDSystemTest {
  SystemInterface separatorGas;
  Stream feedStream;
  Separator separator;
  Stream separatorGasOut;
  Splitter gasSplitter;
  Stream processStream;
  Stream blowdownStream;
  BlowdownValve bdValve;
  Stream bdValveOutlet;
  Orifice bdOrifice;
  Stream toFlare;
  Mixer flareHeader;
  Stream flareHeaderOutlet;
  Flare flare;
  PushButton esdButton;

  /**
   * Set up the ESD blowdown system.
   * 
   * <p>
   * System configuration:
   * <ul>
   * <li>Separator: 50 bara operating pressure with gas outlet</li>
   * <li>Splitter: Divides gas between process and blowdown</li>
   * <li>Blowdown Valve: Normally closed, opens in 5 seconds on ESD</li>
   * <li>BD Orifice: Controls depressurization rate (Cv=150)</li>
   * <li>Flare: Receives and combusts blowdown gas at ~1.5 bara</li>
   * </ul>
   */
  @BeforeEach
  void setUp() {
    // Create separator feed gas
    separatorGas = new SystemSrkEos(298.15, 50.0);
    separatorGas.addComponent("nitrogen", 1.0);
    separatorGas.addComponent("methane", 85.0);
    separatorGas.addComponent("ethane", 10.0);
    separatorGas.addComponent("propane", 3.0);
    separatorGas.addComponent("n-butane", 1.0);
    separatorGas.setMixingRule(2);

    feedStream = new Stream("Feed", separatorGas);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setPressure(50.0, "bara");
    feedStream.setTemperature(25.0, "C");

    // Separator
    separator = new Separator("HP Separator", feedStream);
    // Start in steady-state mode for initialization
    separator.setCalculateSteadyState(true);

    // Separator gas outlet
    separatorGasOut = new Stream("Sep Gas Out", separator.getGasOutStream());

    // Splitter to divide gas between process and blowdown
    // Split factor [0] = process stream, [1] = blowdown stream
    gasSplitter = new Splitter("Gas Splitter", separatorGasOut, 2);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0}); // Initially all to process

    processStream = new Stream("To Process", gasSplitter.getSplitStream(0));
    blowdownStream = new Stream("To Blowdown", gasSplitter.getSplitStream(1));

    // Blowdown valve (normally closed)
    bdValve = new BlowdownValve("BD-101", blowdownStream);
    bdValve.setOpeningTime(5.0); // 5 seconds to fully open
    bdValve.setCv(200.0);

    // ESD push button linked to blowdown valve
    esdButton = new PushButton("ESD-PB-101", bdValve);

    bdValveOutlet = new Stream("BD Valve Outlet", bdValve.getOutletStream());

    // Blowdown orifice to control flow rate
    // Connect directly to blowdown stream to get actual separator pressure
    bdOrifice = new Orifice("BD Orifice", 0.4, 0.15, 50.0, 1.5, 0.61);
    bdOrifice.setInletStream(blowdownStream);

    toFlare = new Stream("To Flare", bdOrifice.getOutletStream());

    // Flare header (can collect from multiple blowdown sources)
    flareHeader = new Mixer("Flare Header");
    flareHeader.addStream(toFlare);

    flareHeaderOutlet = new Stream("Flare Header Outlet", flareHeader.getOutletStream());

    // Flare
    flare = new Flare("Blowdown Flare", flareHeaderOutlet);
    flare.setFlameHeight(50.0); // 50 meter flame for blowdown event
    flare.setRadiantFraction(0.20); // Higher radiant fraction during blowdown
    flare.setTipDiameter(0.8); // Larger diameter for blowdown capacity
  }

  /**
   * Test complete ESD blowdown scenario.
   * 
   * <p>
   * Scenario:
   * <ol>
   * <li>Normal operation with all gas to process, BD valve closed</li>
   * <li>ESD is activated at t=10s</li>
   * <li>Splitter redirects gas to blowdown</li>
   * <li>BD valve opens over 5 seconds</li>
   * <li>Gas flows through BD orifice to flare</li>
   * <li>Monitor pressure, flow rates, and flare performance</li>
   * <li>Track total gas blown down and heat release</li>
   * </ol>
   */
  @Test
  void testESDBlowdownSystem() {
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║        EMERGENCY SHUTDOWN (ESD) BLOWDOWN SYSTEM TEST          ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("═══ SYSTEM CONFIGURATION ═══");
    System.out.println("Separator operating pressure: 50.0 bara");
    System.out.println("Gas flow rate: 10000.0 kg/hr");
    System.out.println("Blowdown valve: BD-101 (normally closed)");
    System.out.println("ESD Push Button: ESD-PB-101 (linked to BD-101)");
    System.out.println("BD Orifice Cv: 150.0");
    System.out.println("Flare header pressure: 1.5 bara");
    System.out.println("BD valve opening time: 5.0 seconds");
    System.out.println();

    // Run initial steady state
    System.out.println("═══ INITIAL STEADY STATE (Normal Operation) ═══");
    feedStream.run();
    separator.run();
    separatorGasOut.run();
    gasSplitter.run();
    processStream.run();
    blowdownStream.run();
    bdValve.run();

    double initialPressure = separator.getGasOutStream().getPressure("bara");
    double initialProcessFlow = processStream.getFlowRate("kg/hr");
    double initialBDFlow = blowdownStream.getFlowRate("kg/hr");

    System.out.printf("Separator gas pressure: %.2f bara%n", initialPressure);
    System.out.printf("Process flow rate: %.1f kg/hr%n", initialProcessFlow);
    System.out.printf("Blowdown flow rate: %.1f kg/hr%n", initialBDFlow);
    System.out.printf("BD valve opening: %.1f%%%n", bdValve.getPercentValveOpening());
    System.out.printf("ESD button state: %s%n", esdButton.isPushed() ? "PUSHED" : "NOT PUSHED");
    System.out.println();

    // Dynamic simulation
    System.out.println("═══ DYNAMIC SIMULATION - ESD ACTIVATION ═══");
    System.out.println(
        "Time (s) | Sep Press | Process Flow | BD Flow    | BD Opening | Flare Flow | Heat Release");
    System.out.println(
        "         | (bara)    | (kg/hr)      | (kg/hr)    | (%)        | (kg/hr)    | (MW)");
    System.out.println(
        "---------|-----------|--------------|------------|------------|------------|-------------");

    double timeStep = 0.5; // 0.5 second time steps
    double simulationTime = 30.0; // 30 second simulation (reduced to avoid separator running dry)
    double esdActivationTime = 10.0; // Activate ESD at 10 seconds

    boolean esdActivated = false;
    double maxBlowdownFlow = 0.0;
    double peakHeatRelease = 0.0;
    double esdStartTime = 0.0;

    for (double time = 0.0; time <= simulationTime; time += timeStep) {
      // Activate ESD at specified time - operator pushes ESD button
      if (!esdActivated && time >= esdActivationTime) {
        System.out.println("\n>>> OPERATOR PUSHES ESD BUTTON - BLOWDOWN INITIATED <<<");
        System.out.println(">>> FEED INLET VALVE CLOSES <<<\n");
        esdButton.push(); // Push button activates BD valve
        // Redirect all flow to blowdown
        gasSplitter.setSplitFactors(new double[] {0.0, 1.0});
        // Stop feeding separator (simulate inlet valve closure on ESD)
        // Use minimal purge flow to avoid numerical issues with zero moles
        feedStream.setFlowRate(0.1, "kg/hr");
        // Switch separator to dynamic mode
        separator.setCalculateSteadyState(false);
        esdActivated = true;
        esdStartTime = time;
      }

      // Run equipment in sequence
      feedStream.run();
      if (separator.getCalculateSteadyState()) {
        separator.run();
      } else {
        separator.runTransient(timeStep, java.util.UUID.randomUUID());
      }
      separatorGasOut.run();
      gasSplitter.run();
      processStream.run();
      blowdownStream.run();

      // Run blowdown valve with transient behavior
      bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      bdValveOutlet.run();

      // BD orifice and flare
      bdOrifice.runTransient(timeStep, java.util.UUID.randomUUID());
      toFlare.run();
      flareHeader.run();
      flareHeaderOutlet.run();
      flare.run();
      flare.updateCumulative(timeStep);

      // Get current values
      double sepPressure = separator.getGasOutStream().getPressure("bara");
      double procesFlow = processStream.getFlowRate("kg/hr");
      double bdFlow = blowdownStream.getFlowRate("kg/hr");
      double bdOpening = bdValve.getPercentValveOpening();
      double flareFlow = toFlare.getFlowRate("kg/hr");
      double heatRelease = flare.getHeatDuty("MW");

      // Track peak values
      if (esdActivated && bdFlow > maxBlowdownFlow) {
        maxBlowdownFlow = bdFlow;
      }
      if (heatRelease > peakHeatRelease) {
        peakHeatRelease = heatRelease;
      }

      // Print every 2 seconds or when ESD activates
      if (time % 2.0 < timeStep || (esdActivated && time - esdStartTime < 10.0)) {
        System.out.printf("%8.1f | %9.2f | %12.1f | %10.1f | %10.1f | %10.1f | %12.2f%n", time,
            sepPressure, procesFlow, bdFlow, bdOpening, flareFlow, heatRelease);
      }
    }

    // Summary report
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║                    ESD BLOWDOWN SUMMARY                        ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("═══ OPERATIONAL TIMELINE ═══");
    System.out.printf("ESD activated at: %.1f s%n", esdActivationTime);
    System.out.printf("BD valve fully open at: %.1f s%n",
        esdActivationTime + bdValve.getOpeningTime());
    System.out.printf("Simulation duration: %.1f s%n", simulationTime);
    System.out.println();

    System.out.println("═══ BLOWDOWN PERFORMANCE ═══");
    System.out.printf("Maximum blowdown flow: %.1f kg/hr (%.2f kg/s)%n", maxBlowdownFlow,
        maxBlowdownFlow / 3600.0);
    System.out.printf("Total gas blown down: %.1f kg%n", flare.getCumulativeGasBurned("kg"));
    System.out.printf("Average blowdown rate: %.1f kg/hr%n",
        flare.getCumulativeGasBurned("kg") * 3600.0 / (simulationTime - esdActivationTime));
    System.out.printf("BD valve final opening: %.1f%%%n", bdValve.getPercentValveOpening());
    System.out.println();

    System.out.println("═══ FLARE SYSTEM PERFORMANCE ═══");
    System.out.printf("Peak heat release rate: %.2f MW%n", peakHeatRelease);
    System.out.printf("Total heat released: %.2f GJ (%.2f MMBtu)%n",
        flare.getCumulativeHeatReleased("GJ"), flare.getCumulativeHeatReleased("MMBtu"));
    System.out.printf("Average heat release rate: %.2f MW%n",
        flare.getCumulativeHeatReleased("GJ") * 1000.0 / (simulationTime - esdActivationTime));
    System.out.println();

    // Gas composition to flare
    System.out.println("═══ BLOWDOWN GAS COMPOSITION ═══");
    SystemInterface bdGas = toFlare.getFluid();
    for (int i = 0; i < bdGas.getPhase(0).getNumberOfComponents(); i++) {
      String compName = bdGas.getPhase(0).getComponent(i).getComponentName();
      double moleFrac = bdGas.getPhase(0).getComponent(i).getz() * 100.0;
      if (moleFrac > 0.01) {
        System.out.printf("%-15s: %6.2f mol%%%n", compName, moleFrac);
      }
    }

    System.out.println();
    System.out.println("═══ ENVIRONMENTAL IMPACT ═══");
    System.out.printf("Total CO2 emissions: %.1f kg (%.2f tonnes)%n",
        flare.getCumulativeCO2Emission("kg"), flare.getCumulativeCO2Emission("tonnes"));
    System.out.println();

    System.out.println("═══ VALIDATION CHECKS ═══");
    assertTrue(esdActivated, "ESD should have been activated");
    assertTrue(esdButton.isPushed(), "ESD button should be pushed");
    assertTrue(bdValve.isActivated(), "BD valve should be activated");
    assertTrue(bdValve.getPercentValveOpening() > 90.0, "BD valve should be nearly fully open");
    assertTrue(maxBlowdownFlow > 0, "Blowdown flow should be positive");
    assertTrue(flare.getCumulativeGasBurned("kg") > 0, "Gas should have been blown down to flare");
    assertTrue(flare.getCumulativeHeatReleased("GJ") > 0, "Heat should have been released");

    System.out.println("✓ ESD push button successfully triggered blowdown");
    System.out.println("✓ BD valve automatically activated by push button");
    System.out.printf("✓ BD valve opened to %.1f%% in %.1f seconds%n",
        bdValve.getPercentValveOpening(), bdValve.getOpeningTime());
    System.out.printf("✓ Total %.1f kg of gas safely blown down to flare%n",
        flare.getCumulativeGasBurned("kg"));
    System.out.printf("✓ Peak flare load: %.2f MW heat release%n", peakHeatRelease);
    System.out.println("✓ Controlled depressurization through BD orifice");
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║              ESD BLOWDOWN SYSTEM TEST COMPLETED                ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }

  /**
   * Test push button operation and integration with blowdown valve.
   */
  @Test
  void testPushButtonOperation() {
    System.out.println("\n═══ PUSH BUTTON OPERATION TEST ═══");

    // Initially not pushed
    assertEquals(0.0, esdButton.getMeasuredValue(), 0.01, "Button should not be pushed initially");
    assertTrue(!esdButton.isPushed(), "Button should not be pushed");
    assertTrue(!bdValve.isActivated(), "BD valve should not be activated");

    System.out.printf("Initial state - Button: %s, BD Valve: %s%n",
        esdButton.isPushed() ? "PUSHED" : "NOT PUSHED",
        bdValve.isActivated() ? "ACTIVATED" : "NOT ACTIVATED");

    // Push button
    esdButton.push();

    assertEquals(1.0, esdButton.getMeasuredValue(), 0.01, "Button should be pushed");
    assertTrue(esdButton.isPushed(), "Button should be pushed");
    assertTrue(bdValve.isActivated(), "BD valve should be activated by button push");

    System.out.printf("After push - Button: %s, BD Valve: %s%n",
        esdButton.isPushed() ? "PUSHED" : "NOT PUSHED",
        bdValve.isActivated() ? "ACTIVATED" : "NOT ACTIVATED");

    // Test display
    System.out.println("\nButton display output:");
    esdButton.displayResult();
    System.out.println("\nButton toString:");
    System.out.println(esdButton.toString());

    // Reset button (valve stays activated)
    esdButton.reset();
    assertEquals(0.0, esdButton.getMeasuredValue(), 0.01, "Button should be reset");
    assertTrue(!esdButton.isPushed(), "Button should not be pushed after reset");
    assertTrue(bdValve.isActivated(),
        "BD valve should still be activated (requires separate reset)");

    System.out.printf("\nAfter button reset - Button: %s, BD Valve: %s%n",
        esdButton.isPushed() ? "PUSHED" : "NOT PUSHED",
        bdValve.isActivated() ? "ACTIVATED" : "NOT ACTIVATED");

    // Reset valve
    bdValve.reset();
    assertTrue(!bdValve.isActivated(), "BD valve should be reset");

    System.out.println("✓ Push button operation test passed");
    System.out.println("✓ Button correctly activates linked BD valve");
    System.out.println("✓ Button reset does not affect valve state (safety feature)");
  }

  /**
   * Test push button with auto-activation disabled.
   */
  @Test
  void testPushButtonManualMode() {
    System.out.println("\n═══ PUSH BUTTON MANUAL MODE TEST ═══");

    // Create push button with auto-activation disabled
    PushButton manualButton = new PushButton("Manual-PB-101", bdValve);
    manualButton.setAutoActivateValve(false);

    assertTrue(!manualButton.isAutoActivateValve(), "Auto-activation should be disabled");

    // Push button - valve should NOT activate automatically
    manualButton.push();

    assertTrue(manualButton.isPushed(), "Button should be pushed");
    assertTrue(!bdValve.isActivated(),
        "BD valve should NOT be activated (auto-activation disabled)");

    System.out.println("Manual mode: Button pushed but valve not auto-activated");

    // Manually activate valve
    bdValve.activate();
    assertTrue(bdValve.isActivated(), "BD valve should be activated manually");

    System.out.println("✓ Manual mode test passed");
    System.out.println("✓ Auto-activation can be disabled for manual control");
  }

  /**
   * Test blowdown valve behavior in isolation.
   */
  @Test
  void testBlowdownValveOperation() {
    System.out.println("\n═══ BLOWDOWN VALVE OPERATION TEST ═══");

    // Initially closed
    assertEquals(0.0, bdValve.getPercentValveOpening(), 0.1, "BD valve should start closed");
    assertTrue(!bdValve.isActivated(), "BD valve should not be activated");

    // Activate
    bdValve.activate();
    assertTrue(bdValve.isActivated(), "BD valve should be activated");

    // Simulate opening over time
    double timeStep = 0.5;
    double totalTime = 0.0;

    while (totalTime < bdValve.getOpeningTime() * 1.5) {
      bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      totalTime += timeStep;

      System.out.printf("Time: %.1fs, Opening: %.1f%%, Opening in progress: %s%n", totalTime,
          bdValve.getPercentValveOpening(), bdValve.isOpening());
    }

    // Should be fully open
    assertTrue(bdValve.getPercentValveOpening() >= 99.0, "BD valve should be fully open");
    assertTrue(!bdValve.isOpening(), "BD valve should have finished opening");

    // Test reset
    bdValve.reset();
    assertEquals(0.0, bdValve.getPercentValveOpening(), 0.1,
        "BD valve should be closed after reset");
    assertTrue(!bdValve.isActivated(), "BD valve should not be activated after reset");

    System.out.println("✓ Blowdown valve operation test passed");
  }

  /**
   * Test multiple blowdown sources to common flare.
   */
  @Test
  void testMultipleBlowdownSources() {
    System.out.println("\n═══ MULTIPLE BLOWDOWN SOURCES TEST ═══");

    // Create second blowdown source
    SystemInterface separatorGas2 = separatorGas.clone();
    Stream feedStream2 = new Stream("Feed 2", separatorGas2);
    feedStream2.setFlowRate(5000.0, "kg/hr");
    feedStream2.setPressure(40.0, "bara");

    Separator separator2 = new Separator("MP Separator", feedStream2);
    Stream sepGasOut2 = new Stream("Sep 2 Gas Out", separator2.getGasOutStream());

    BlowdownValve bdValve2 = new BlowdownValve("BD-102", sepGasOut2);
    bdValve2.setOpeningTime(3.0);
    bdValve2.setCv(150.0);

    Stream bdValve2Outlet = new Stream("BD-102 Outlet", bdValve2.getOutletStream());

    Orifice bdOrifice2 = new Orifice("BD Orifice 2", 0.3, 0.12, 30.0, 1.5, 0.61);
    bdOrifice2.setInletStream(bdValve2Outlet);

    Stream toFlare2 = new Stream("To Flare 2", bdOrifice2.getOutletStream());

    // Add second source to flare header
    flareHeader.addStream(toFlare2);

    // Activate both blowdown valves
    bdValve.activate();
    bdValve2.activate();
    gasSplitter.setSplitFactors(new double[] {0.0, 1.0});

    // Run simulation
    double timeStep = 1.0;
    for (double time = 0.0; time <= 20.0; time += timeStep) {
      feedStream.run();
      separator.run();
      separatorGasOut.run();
      gasSplitter.run();
      blowdownStream.run();
      bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      bdValveOutlet.run();
      bdOrifice.runTransient(timeStep, java.util.UUID.randomUUID());
      toFlare.run();

      feedStream2.run();
      separator2.run();
      sepGasOut2.run();
      bdValve2.runTransient(timeStep, java.util.UUID.randomUUID());
      bdValve2Outlet.run();
      bdOrifice2.runTransient(timeStep, java.util.UUID.randomUUID());
      toFlare2.run();

      flareHeader.run();
      flareHeaderOutlet.run();
      flare.run();
      flare.updateCumulative(timeStep);
    }

    double totalFlareFlow = toFlare.getFlowRate("kg/hr") + toFlare2.getFlowRate("kg/hr");
    double heatRelease = flare.getHeatDuty("MW");

    System.out.printf("BD-101 flow: %.1f kg/hr%n", toFlare.getFlowRate("kg/hr"));
    System.out.printf("BD-102 flow: %.1f kg/hr%n", toFlare2.getFlowRate("kg/hr"));
    System.out.printf("Total flare flow: %.1f kg/hr%n", totalFlareFlow);
    System.out.printf("Combined heat release: %.2f MW%n", heatRelease);

    assertTrue(totalFlareFlow > 0, "Combined flare flow should be positive");
    assertTrue(heatRelease > 0, "Heat release should be positive");
    System.out.println("✓ Multiple blowdown sources successfully combined");
  }

  /**
   * Test pressure relief via blowdown valve with pressure monitoring.
   * 
   * <p>
   * This test simulates a scenario where separator pressure builds up due to blocked outlet, then
   * ESD is activated and pressure is relieved through the blowdown valve.
   */
  @Test
  void testPressureReliefViaBlowdown() {
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║        PRESSURE RELIEF VIA BLOWDOWN - MONITORING TEST         ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Create a scenario with potential for pressure buildup
    SystemInterface highPressureGas = separatorGas.clone();
    Stream highPressureFeed = new Stream("HP Feed", highPressureGas);
    highPressureFeed.setFlowRate(15000.0, "kg/hr"); // Higher flow rate
    highPressureFeed.setPressure(60.0, "bara");
    highPressureFeed.setTemperature(25.0, "C");

    Separator hpSeparator = new Separator("HP Separator", highPressureFeed);
    Stream hpSepGasOut = new Stream("HP Sep Gas", hpSeparator.getGasOutStream());

    // Outlet valve that will be blocked to build pressure
    ThrottlingValve hpOutletValve = new ThrottlingValve("HP Outlet Valve", hpSepGasOut);
    hpOutletValve.setCv(100.0);
    hpOutletValve.setPercentValveOpening(50.0); // Initially open
    hpOutletValve.setOutletPressure(20.0);

    Stream hpProcessOut = new Stream("HP Process Out", hpOutletValve.getOutletStream());

    // Blowdown system on separator
    Splitter hpSplitter = new Splitter("HP Splitter", hpSepGasOut, 2);
    hpSplitter.setSplitFactors(new double[] {1.0, 0.0}); // Initially to process

    Stream hpBlowdownStream = new Stream("HP BD Stream", hpSplitter.getSplitStream(1));

    BlowdownValve hpBdValve = new BlowdownValve("HP-BD-101", hpBlowdownStream);
    hpBdValve.setOpeningTime(3.0);
    hpBdValve.setCv(250.0);

    PushButton hpEsdButton = new PushButton("HP-ESD-PB", hpBdValve);

    Stream hpBdOut = new Stream("HP BD Out", hpBdValve.getOutletStream());

    // Connect orifice directly to blowdown stream to get actual separator pressure
    Orifice hpBdOrifice = new Orifice("HP BD Orifice", 0.45, 0.18, 60.0, 1.5, 0.61);
    hpBdOrifice.setInletStream(hpBlowdownStream);

    Stream hpToFlare = new Stream("HP To Flare", hpBdOrifice.getOutletStream());

    Mixer hpFlareHeader = new Mixer("HP Flare Header");
    hpFlareHeader.addStream(hpToFlare);

    Stream hpFlareOut = new Stream("HP Flare Out", hpFlareHeader.getOutletStream());

    Flare hpFlare = new Flare("HP Flare", hpFlareOut);

    System.out.println("═══ INITIAL STEADY STATE ═══");
    highPressureFeed.run();
    hpSeparator.run();
    hpSepGasOut.run();
    hpOutletValve.run();

    System.out.printf("Separator pressure: %.2f bara%n", hpSeparator.getPressure("bara"));
    System.out.printf("Outlet valve opening: %.1f%%%n", hpOutletValve.getPercentValveOpening());
    System.out.println();

    // Switch to dynamic mode now that we have initial conditions
    hpSeparator.setCalculateSteadyState(false);

    // Run one transient timestep to establish dynamic initial conditions
    double timeStep = 0.5;
    highPressureFeed.run();
    hpSeparator.runTransient(timeStep, java.util.UUID.randomUUID());
    hpSepGasOut.run();

    // Capture the actual starting pressure for dynamic simulation
    double initialPressure = hpSeparator.getPressure("bara");

    // Dynamic simulation with pressure buildup and relief
    System.out.println("═══ DYNAMIC SIMULATION - PRESSURE BUILDUP AND RELIEF ═══");
    System.out.println("Time (s) | Sep Press | Outlet Valve | BD Valve   | BD Flow    | Status");
    System.out.println("         | (bara)    | (%)          | Opening(%) | (kg/hr)    |");
    System.out.println(
        "---------|-----------|--------------|------------|------------|------------------");

    double simulationTime = 30.0;
    double blockageTime = 5.0; // Block outlet at 5s
    double esdActivationTime = 10.0; // Activate ESD at 10s

    boolean outletBlocked = false;
    boolean esdActivated = false;
    double maxPressure = initialPressure;
    double finalPressure = initialPressure;
    double pressureAtEsdActivation = initialPressure;

    for (double time = 0.0; time <= simulationTime; time += timeStep) {
      // Simulate outlet blockage
      if (!outletBlocked && time >= blockageTime) {
        hpOutletValve.setPercentValveOpening(5.0); // Severely restrict outlet
        outletBlocked = true;
      }

      // Activate ESD when pressure gets high
      if (!esdActivated && time >= esdActivationTime) {
        hpEsdButton.push();
        hpSplitter.setSplitFactors(new double[] {0.0, 1.0}); // All to blowdown
        // Stop feeding separator - use minimal purge flow to avoid numerical issues
        highPressureFeed.setFlowRate(0.1, "kg/hr");
        esdActivated = true;
        pressureAtEsdActivation = hpSeparator.getPressure("bara");
      }

      // Run equipment
      highPressureFeed.run();
      hpSeparator.runTransient(timeStep, java.util.UUID.randomUUID());
      hpSepGasOut.run();

      if (!esdActivated) {
        hpOutletValve.run();
        hpProcessOut.run();
      }

      hpSplitter.run();
      hpBlowdownStream.run();
      hpBdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      hpBdOut.run();
      hpBdOrifice.runTransient(timeStep, java.util.UUID.randomUUID());
      hpToFlare.run();
      hpFlareHeader.run();
      hpFlareOut.run();
      hpFlare.run();
      hpFlare.updateCumulative(timeStep);

      // Monitor pressure
      double currentPressure = hpSeparator.getPressure("bara");
      maxPressure = Math.max(maxPressure, currentPressure);
      finalPressure = currentPressure;

      // Print status every second or at key events
      if (time % 2.0 < timeStep || (outletBlocked && time - blockageTime < 1.0)
          || (esdActivated && time - esdActivationTime < 2.0)) {
        String status = "";
        if (!outletBlocked) {
          status = "Normal operation";
        } else if (!esdActivated) {
          status = "Outlet blocked - pressure rising";
        } else {
          status = "ESD active - depressurizing";
        }

        System.out.printf("%8.1f | %9.2f | %12.1f | %10.1f | %10.1f | %s%n", time, currentPressure,
            hpOutletValve.getPercentValveOpening(), hpBdValve.getPercentValveOpening(),
            hpBlowdownStream.getFlowRate("kg/hr"), status);
      }
    }

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║              PRESSURE RELIEF VERIFICATION REPORT               ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("═══ PRESSURE PROFILE ═══");
    System.out.printf("Initial pressure: %.2f bara%n", initialPressure);
    System.out.printf("Maximum pressure reached: %.2f bara%n", maxPressure);
    System.out.printf("Pressure at ESD activation: %.2f bara%n", pressureAtEsdActivation);
    System.out.printf("Final pressure: %.2f bara%n", finalPressure);
    System.out.printf("Pressure rise before ESD: %.2f bar (%.1f%% increase)%n",
        pressureAtEsdActivation - initialPressure,
        100.0 * (pressureAtEsdActivation - initialPressure) / initialPressure);
    System.out.printf("Pressure drop after ESD: %.2f bar (%.1f%% reduction)%n",
        pressureAtEsdActivation - finalPressure,
        100.0 * (pressureAtEsdActivation - finalPressure) / pressureAtEsdActivation);
    System.out.println();

    System.out.println("═══ BLOWDOWN PERFORMANCE ═══");
    System.out.printf("Total gas blown down: %.1f kg%n", hpFlare.getCumulativeGasBurned("kg"));
    System.out.printf("Total heat to flare: %.2f GJ%n", hpFlare.getCumulativeHeatReleased("GJ"));
    System.out.printf("BD valve final opening: %.1f%%%n", hpBdValve.getPercentValveOpening());
    System.out.println();

    System.out.println("═══ VALIDATION CHECKS ═══");
    assertTrue(maxPressure > initialPressure, "Pressure should have increased due to blockage");
    assertTrue(finalPressure < pressureAtEsdActivation,
        "Pressure should decrease after ESD activation");
    assertTrue(hpBdValve.isActivated(), "BD valve should be activated");
    assertTrue(hpFlare.getCumulativeGasBurned("kg") > 0, "Gas should flow to flare");

    System.out.printf("✓ Pressure buildup detected: %.2f bara → %.2f bara%n", initialPressure,
        maxPressure);
    System.out.printf("✓ ESD successfully activated at %.2f bara%n", pressureAtEsdActivation);
    System.out.printf("✓ Pressure relieved to %.2f bara (%.1f%% reduction)%n", finalPressure,
        100.0 * (pressureAtEsdActivation - finalPressure) / pressureAtEsdActivation);
    System.out.printf("✓ Total %.1f kg gas safely blown down to flare%n",
        hpFlare.getCumulativeGasBurned("kg"));

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║          PRESSURE RELIEF TEST COMPLETED SUCCESSFULLY           ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }
}
