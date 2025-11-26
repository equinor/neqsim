package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.measurementdevice.PushButton;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example demonstrating Emergency Shutdown (ESD) system with Control Valve, ESD Valve, Blowdown
 * Valve and Push Button.
 * 
 * <p>
 * This example shows:
 * <ul>
 * <li>Inlet control valve (throttling valve) for flow control</li>
 * <li>ESD inlet valve (normally open, fail-closed) for process isolation</li>
 * <li>High-pressure separator with gas outlet</li>
 * <li>Splitter directing gas to process or blowdown</li>
 * <li>Blowdown valve (normally closed) activated by ESD push button</li>
 * <li>Orifice controlling blowdown flow rate</li>
 * <li>Flare system receiving and combusting blowdown gas</li>
 * <li>Coordinated ESD valve closure and blowdown valve opening</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ESDBlowdownSystemExample {

  /**
   * Main method to run the ESD blowdown system example.
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     EMERGENCY SHUTDOWN (ESD) BLOWDOWN SYSTEM EXAMPLE          ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Create separator feed gas
    SystemInterface separatorGas = new SystemSrkEos(298.15, 50.0);
    separatorGas.addComponent("nitrogen", 1.0);
    separatorGas.addComponent("methane", 85.0);
    separatorGas.addComponent("ethane", 10.0);
    separatorGas.addComponent("propane", 3.0);
    separatorGas.addComponent("n-butane", 1.0);
    separatorGas.setMixingRule(2);

    Stream feedStream = new Stream("Feed", separatorGas);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setPressure(50.0, "bara");
    feedStream.setTemperature(25.0, "C");

    // Inlet control valve for flow control
    ControlValve controlValve = new ControlValve("FCV-101", feedStream);
    controlValve.setPercentValveOpening(50.0); // 50% opening for flow control
    controlValve.setCv(300.0);
    controlValve.setOutletPressure(48.0); // Slight pressure drop

    Stream afterControlValve = new Stream("After Control Valve", controlValve.getOutletStream());

    // ESD inlet valve (normally open, fail-closed safety valve)
    ESDValve esdInletValve = new ESDValve("ESD-XV-101", afterControlValve);
    esdInletValve.setStrokeTime(5.0); // 5 seconds to close
    esdInletValve.setCv(500.0); // Large Cv for minimal pressure drop
    esdInletValve.energize(); // Energized during normal operation

    Stream separatorInlet = new Stream("Separator Inlet", esdInletValve.getOutletStream());

    // Separator
    Separator separator = new Separator("HP Separator", separatorInlet);
    // Start in steady-state mode for initialization
    separator.setCalculateSteadyState(true);

    // Separator gas outlet
    Stream separatorGasOut = new Stream("Sep Gas Out", separator.getGasOutStream());

    // Splitter to divide gas between process and blowdown
    Splitter gasSplitter = new Splitter("Gas Splitter", separatorGasOut, 2);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0}); // Initially all to process

    Stream processStream = new Stream("To Process", gasSplitter.getSplitStream(0));
    Stream blowdownStream = new Stream("To Blowdown", gasSplitter.getSplitStream(1));

    // Blowdown valve (normally closed)
    BlowdownValve bdValve = new BlowdownValve("BD-101", blowdownStream);
    bdValve.setOpeningTime(5.0); // 5 seconds to fully open
    bdValve.setCv(200.0);

    // ESD push button linked to blowdown valve
    PushButton esdButton = new PushButton("ESD-PB-101", bdValve);

    Stream bdValveOutlet = new Stream("BD Valve Outlet", bdValve.getOutletStream());

    // Blowdown orifice to control flow rate (restrictive for blowdown control)
    // Connect directly to blowdown stream to get actual separator pressure
    Orifice bdOrifice = new Orifice("BD Orifice", 0.4, 0.05, 50.0, 1.5, 0.61);
    bdOrifice.setInletStream(blowdownStream);

    Stream toFlare = new Stream("To Flare", bdOrifice.getOutletStream());

    // Flare header
    Mixer flareHeader = new Mixer("Flare Header");
    flareHeader.addStream(toFlare);

    Stream flareHeaderOutlet = new Stream("Flare Header Outlet", flareHeader.getOutletStream());

    // Flare
    Flare flare = new Flare("Blowdown Flare", flareHeaderOutlet);
    flare.setFlameHeight(50.0);
    flare.setRadiantFraction(0.20);
    flare.setTipDiameter(0.8);

    System.out.println("═══ SYSTEM CONFIGURATION ═══");
    System.out.println("Separator: HP Separator at 50 bara");
    System.out.println("Gas flow rate: 10000 kg/hr");
    System.out.println("Inlet control valve: FCV-101 (50% opening for flow control)");
    System.out.println("  - Type: ControlValve with Cv=300");
    System.out.println("ESD inlet valve: ESD-XV-101 (normally open, fail-closed)");
    System.out.println("  - Type: ESDValve with 5s stroke time");
    System.out.println("  - Fail-safe: Closed (spring-return actuator)");
    System.out.println("Blowdown valve: BD-101 (normally closed)");
    System.out.println("ESD push button: ESD-PB-101");
    System.out.println("BD orifice: D=0.4m, d=0.05m, Cd=0.61 (beta=0.125, restrictive)");
    System.out.println("Flare header: 1.5 bara");
    System.out.println();

    // Run initial steady state - normal operation
    System.out.println("═══ NORMAL OPERATION ═══");
    feedStream.run();
    controlValve.run();
    afterControlValve.run();
    esdInletValve.run();
    separatorInlet.run();
    separator.run();
    separatorGasOut.run();
    gasSplitter.run();
    processStream.run();
    blowdownStream.run();
    bdValve.run();

    System.out.printf("Control valve: %.1f%% open, outlet P=%.2f bara%n",
        controlValve.getPercentValveOpening(), afterControlValve.getPressure("bara"));
    System.out.printf("ESD inlet valve: %s, %.1f%% open%n",
        esdInletValve.isEnergized() ? "ENERGIZED" : "DE-ENERGIZED",
        esdInletValve.getPercentValveOpening());
    System.out.printf("Separator inlet flow: %.1f kg/hr%n", separatorInlet.getFlowRate("kg/hr"));
    System.out.printf("Process flow: %.1f kg/hr%n", processStream.getFlowRate("kg/hr"));
    System.out.printf("Blowdown flow: %.1f kg/hr%n", blowdownStream.getFlowRate("kg/hr"));
    System.out.printf("BD valve state: %s%n",
        bdValve.isActivated() ? "ACTIVATED" : "NOT ACTIVATED");
    System.out.printf("ESD button state: %s%n", esdButton.isPushed() ? "PUSHED" : "NOT PUSHED");
    System.out.println();

    // Simulate ESD activation - operator pushes button
    System.out.println("═══ EMERGENCY SHUTDOWN ACTIVATED ═══");
    System.out.println(">>> OPERATOR PUSHES ESD BUTTON <<<");
    System.out.println(">>> DE-ENERGIZING ESD INLET VALVE (FAIL-SAFE CLOSURE) <<<");
    System.out.println(">>> ACTIVATING BLOWDOWN VALVE <<<");
    esdButton.push();

    // De-energize ESD inlet valve - triggers fail-safe closure
    esdInletValve.trip();

    // Redirect flow to blowdown
    gasSplitter.setSplitFactors(new double[] {0.0, 1.0});

    // Now switch separator to dynamic/transient mode
    separator.setCalculateSteadyState(false);

    System.out.printf("ESD button state: %s%n", esdButton.isPushed() ? "PUSHED" : "NOT PUSHED");
    System.out.printf("ESD inlet valve: %s%n", esdInletValve.isClosing() ? "CLOSING" : "CLOSED");
    System.out.printf("ESD inlet valve opening: %.1f%%%n", esdInletValve.getPercentValveOpening());
    System.out.printf("BD valve activated: %s%n", bdValve.isActivated() ? "YES" : "NO");
    System.out.println();

    // Simulate blowdown over time with pressure monitoring
    System.out.println("═══ BLOWDOWN SIMULATION WITH COORDINATED VALVE OPERATION ═══");
    System.out.println(
        "Time (s) | Sep Press (bara) | ESD Valve (%) | BD Opening (%) | BD Flow (kg/hr) | Flare Heat (MW)");
    System.out.println(
        "---------|------------------|---------------|----------------|-----------------|----------------");

    double timeStep = 1.0;
    double totalTime = 20.0;

    // Track pressure for verification
    double initialPressure = separator.getGasOutStream().getPressure("bara");
    double minPressure = initialPressure;
    double maxPressure = initialPressure;
    double pressureAtEnd = initialPressure;

    for (double time = 0.0; time <= totalTime; time += timeStep) {
      // Run ESD valve transient (progressive closure)
      esdInletValve.runTransient(timeStep, java.util.UUID.randomUUID());

      // Control feed flow based on inlet valve position
      if (esdInletValve.getPercentValveOpening() < 1.0) {
        // Inlet valve closed - minimal purge flow to avoid numerical issues
        separatorInlet.getThermoSystem().setTotalFlowRate(0.1, "kg/hr");
      } else {
        // Inlet valve open - run normally
        feedStream.run();
        controlValve.run();
        afterControlValve.run();
        esdInletValve.run();
        separatorInlet.run();
      }

      // Run equipment with transient simulation for separator (after ESD)
      if (separator.getCalculateSteadyState()) {
        separator.run();
      } else {
        separator.runTransient(timeStep, java.util.UUID.randomUUID());
      }
      separatorGasOut.run();
      gasSplitter.run();
      blowdownStream.run();
      bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      bdValveOutlet.run();
      bdOrifice.runTransient(timeStep, java.util.UUID.randomUUID());
      toFlare.run();
      flareHeader.run();
      flareHeaderOutlet.run();
      flare.run();
      flare.updateCumulative(timeStep);

      // Monitor separator pressure
      double currentPressure = separator.getGasOutStream().getPressure("bara");
      minPressure = Math.min(minPressure, currentPressure);
      maxPressure = Math.max(maxPressure, currentPressure);
      if (time == totalTime) {
        pressureAtEnd = currentPressure;
      }

      System.out.printf("%8.1f | %16.2f | %13.1f | %14.1f | %15.1f | %15.2f%n", time,
          currentPressure, esdInletValve.getPercentValveOpening(), bdValve.getPercentValveOpening(),
          toFlare.getFlowRate("kg/hr"), flare.getHeatDuty("MW"));
    }

    System.out.println();
    System.out.println("═══ BLOWDOWN SUMMARY ═══");
    System.out.printf("ESD inlet valve: %s (%.1f%% open)%n",
        esdInletValve.hasTripCompleted() ? "TRIP COMPLETED" : "CLOSING",
        esdInletValve.getPercentValveOpening());
    System.out.printf("BD valve final opening: %.1f%%%n", bdValve.getPercentValveOpening());
    System.out.printf("Total gas blown down: %.1f kg%n", flare.getCumulativeGasBurned("kg"));
    System.out.printf("Total heat released: %.2f GJ%n", flare.getCumulativeHeatReleased("GJ"));
    System.out.printf("Total CO2 emissions: %.1f kg%n", flare.getCumulativeCO2Emission("kg"));
    System.out.println();

    System.out.println("═══ PRESSURE RELIEF VERIFICATION ═══");
    System.out.printf("Initial separator pressure: %.2f bara%n", initialPressure);
    System.out.printf("Minimum pressure during blowdown: %.2f bara%n", minPressure);
    System.out.printf("Maximum pressure during blowdown: %.2f bara%n", maxPressure);
    System.out.printf("Final separator pressure: %.2f bara%n", pressureAtEnd);
    System.out.printf("Pressure drop: %.2f bar (%.1f%% reduction)%n",
        initialPressure - pressureAtEnd,
        100.0 * (initialPressure - pressureAtEnd) / initialPressure);

    // Verification checks
    if (esdInletValve.hasTripCompleted() && esdInletValve.getPercentValveOpening() < 1.0) {
      System.out.println("✓ ESD inlet valve successfully closed (fail-safe operation)");
    } else {
      System.out.println("✗ WARNING: ESD inlet valve closure not completed");
    }

    if (pressureAtEnd < initialPressure) {
      System.out.println("✓ Pressure successfully reduced via blowdown valve");
    } else {
      System.out.println("✗ WARNING: Pressure not reduced - check blowdown system");
    }

    if (bdValve.isActivated() && bdValve.getPercentValveOpening() > 90.0) {
      System.out.println("✓ Blowdown valve fully opened as expected");
    } else {
      System.out.println("✗ WARNING: Blowdown valve not fully open");
    }

    if (flare.getCumulativeGasBurned("kg") > 0) {
      System.out.println("✓ Gas successfully routed to flare");
    } else {
      System.out.println("✗ WARNING: No gas flow to flare detected");
    }
    System.out.println();

    System.out.println("═══ SYSTEM STATUS ═══");
    System.out.println(esdButton.toString());
    System.out.println(controlValve.toString());
    System.out.println(esdInletValve.toString());
    System.out.println(bdValve.toString());
    System.out.println();

    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║              ESD BLOWDOWN EXAMPLE COMPLETED                    ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }
}
