package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.measurementdevice.PushButton;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example demonstrating Emergency Shutdown (ESD) system with Blowdown Valve and Push Button.
 * 
 * <p>
 * This example shows:
 * <ul>
 * <li>High-pressure separator with gas outlet</li>
 * <li>Splitter directing gas to process or blowdown</li>
 * <li>Blowdown valve (normally closed) activated by ESD push button</li>
 * <li>Orifice controlling blowdown flow rate</li>
 * <li>Flare system receiving and combusting blowdown gas</li>
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

    // ESD inlet valve (normally open, closes on ESD)
    ThrottlingValve esdInletValve = new ThrottlingValve("ESD-XV-101", feedStream);
    esdInletValve.setPercentValveOpening(100.0); // Initially fully open
    esdInletValve.setCv(500.0); // Large Cv for minimal pressure drop
    esdInletValve.setOutletPressure(50.0);

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
    System.out.println("ESD inlet valve: ESD-XV-101 (normally open)");
    System.out.println("Blowdown valve: BD-101 (normally closed)");
    System.out.println("ESD push button: ESD-PB-101");
    System.out.println("BD orifice: D=0.4m, d=0.05m, Cd=0.61 (beta=0.125, restrictive)");
    System.out.println("Flare header: 1.5 bara");
    System.out.println();

    // Run initial steady state - normal operation
    System.out.println("═══ NORMAL OPERATION ═══");
    feedStream.run();
    esdInletValve.run();
    separatorInlet.run();
    separator.run();
    separatorGasOut.run();
    gasSplitter.run();
    processStream.run();
    blowdownStream.run();
    bdValve.run();

    System.out.printf("Inlet valve opening: %.1f%%%n", esdInletValve.getPercentValveOpening());
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
    System.out.println(">>> ESD INLET VALVE CLOSES <<<");
    esdButton.push();

    // Close ESD inlet valve on ESD signal
    esdInletValve.setPercentValveOpening(0.0);

    // Redirect flow to blowdown
    gasSplitter.setSplitFactors(new double[] {0.0, 1.0});

    // Now switch separator to dynamic/transient mode
    separator.setCalculateSteadyState(false);

    System.out.printf("ESD button state: %s%n", esdButton.isPushed() ? "PUSHED" : "NOT PUSHED");
    System.out.printf("ESD inlet valve opening: %.1f%%%n", esdInletValve.getPercentValveOpening());
    System.out.printf("BD valve activated: %s%n", bdValve.isActivated() ? "YES" : "NO");
    System.out.println();

    // Simulate blowdown over time with pressure monitoring
    System.out.println("═══ BLOWDOWN SIMULATION WITH PRESSURE MONITORING ═══");
    System.out.println(
        "Time (s) | Sep Press (bara) | BD Opening (%) | BD Flow (kg/hr) | Inlet Valve (%) | Flare Heat (MW) | P_bd (bara) | ΔP (bar) | ρ (kg/m³)");
    System.out.println(
        "---------|------------------|----------------|-----------------|-----------------|-----------------|-------------|----------|----------");

    double timeStep = 1.0;
    double totalTime = 20.0;

    // Track pressure for verification
    double initialPressure = separator.getGasOutStream().getPressure("bara");
    double minPressure = initialPressure;
    double maxPressure = initialPressure;
    double pressureAtEnd = initialPressure;

    for (double time = 0.0; time <= totalTime; time += timeStep) {
      // Control feed flow based on inlet valve position
      if (esdInletValve.getPercentValveOpening() < 1.0) {
        // Inlet valve closed - minimal purge flow to avoid numerical issues
        separatorInlet.getThermoSystem().setTotalFlowRate(0.1, "kg/hr");
      } else {
        // Inlet valve open - run normally
        feedStream.run();
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

      // Get diagnostic info
      double bdInletPress = bdValveOutlet.getPressure("bara");
      double sepOutPress = separatorGasOut.getPressure("bara");
      double blowdownStreamPress = blowdownStream.getPressure("bara");
      double orificeInletPress = bdOrifice.getInletStream().getPressure("bara");
      double orificeOutletPress = toFlare.getPressure("bara");
      double deltaP = orificeInletPress - orificeOutletPress;
      double density = bdValveOutlet.getFluid().getDensity("kg/m3");

      System.out.printf(
          "%8.1f | %16.2f | %14.1f | %15.1f | %15.1f | %15.2f | BDS=%.2f VOut=%.2f OrIn=%.2f OrOut=%.2f ΔP=%.2f%n",
          time, currentPressure, bdValve.getPercentValveOpening(), toFlare.getFlowRate("kg/hr"),
          esdInletValve.getPercentValveOpening(), flare.getHeatDuty("MW"), blowdownStreamPress,
          bdInletPress, orificeInletPress, orificeOutletPress, deltaP);
    }

    System.out.println();
    System.out.println("═══ BLOWDOWN SUMMARY ═══");
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
    System.out.println(bdValve.toString());
    System.out.println();

    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║              ESD BLOWDOWN EXAMPLE COMPLETED                    ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }
}
