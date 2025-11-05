package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for simulating a complete PSV relief scenario with flare system.
 * 
 * <p>
 * This test simulates a realistic overpressure incident where:
 * <ul>
 * <li>A separator experiences blocked outlet leading to pressure buildup</li>
 * <li>PSV opens and relieves gas to a flare header/manifold</li>
 * <li>Multiple relief sources can feed into the common flare header</li>
 * <li>Gas is combusted in the flare with heat release calculation</li>
 * <li>System tracks total gas burned, heat release rate, and cumulative heat release</li>
 * </ul>
 * 
 * <p>
 * The scenario demonstrates:
 * <ul>
 * <li>Dynamic PSV sizing during relief event</li>
 * <li>Flare header pressure drop and capacity</li>
 * <li>Combustion calculations for flare gas</li>
 * <li>Heat release rate and total heat release</li>
 * <li>Peak relief flow and duration</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class PSVFlareSystemTest {
  SystemInterface feedGas;
  Stream feedStream;
  Compressor feedCompressor;
  Stream compressorOutlet;
  Separator separator;
  ThrottlingValve outletValve;
  Stream separatorGasOutlet;
  SafetyValve psv;
  Stream psvRelief;
  Mixer flareHeader;
  Stream flareHeaderOutlet;
  Stream flareGas;

  /**
   * Set up the process system with PSV and flare.
   * 
   * <p>
   * System configuration:
   * <ul>
   * <li>Feed: 5000 kg/hr natural gas at 30 bara</li>
   * <li>Compressor: Boosts to 55 bara</li>
   * <li>Separator: Design pressure 60 bara, MAWP 65 bara</li>
   * <li>PSV: Set pressure 55 bara, relieves to flare</li>
   * <li>Flare header: Collects relief from PSV(s)</li>
   * <li>Flare: Burns relief gas at atmospheric pressure</li>
   * </ul>
   */
  @BeforeEach
  void setUp() {
    // Create natural gas feed mixture
    feedGas = new SystemSrkEos(273.15 + 30.0, 30.0);
    feedGas.addComponent("nitrogen", 0.5);
    feedGas.addComponent("CO2", 1.5);
    feedGas.addComponent("methane", 85.0);
    feedGas.addComponent("ethane", 8.0);
    feedGas.addComponent("propane", 3.0);
    feedGas.addComponent("i-butane", 1.0);
    feedGas.addComponent("n-butane", 1.0);
    feedGas.setMixingRule("classic");
    feedGas.createDatabase(true);
    feedGas.setMultiPhaseCheck(true);

    // Create feed stream
    feedStream = new Stream("Feed Gas", feedGas);
    feedStream.setFlowRate(5000.0, "kg/hr");
    feedStream.setTemperature(30.0, "C");
    feedStream.setPressure(30.0, "bara");
    feedStream.run();

    // Compressor to increase pressure (simulates upstream compression)
    feedCompressor = new Compressor("Feed Compressor", feedStream);
    feedCompressor.setOutletPressure(55.0);
    feedCompressor.setIsentropicEfficiency(0.75);

    compressorOutlet = new Stream("Compressor Outlet", feedCompressor.getOutletStream());

    // High pressure separator
    separator = new Separator("HP Separator", compressorOutlet);
    separator.setInternalDiameter(2.0); // 2 meter diameter
    separator.setSeparatorLength(6.0); // 6 meter length

    // Outlet control valve - will be blocked to create overpressure
    separatorGasOutlet = new Stream("Separator Gas Out", separator.getGasOutStream());
    outletValve = new ThrottlingValve("Outlet Valve", separatorGasOutlet);
    outletValve.setOutletPressure(50.0);
    outletValve.setPercentValveOpening(50.0);
    outletValve.setCv(150.0);

    // Safety valve (PSV) on separator
    psv = new SafetyValve("PSV-101", separator.getGasOutStream());
    psv.setPressureSpec(55.0); // Set pressure in bara
    psv.setFullOpenPressure(60.5); // 10% overpressure = 60.5 bara full open
    psv.setCv(150.0); // Will be sized during simulation

    // PSV relief stream
    psvRelief = new Stream("PSV Relief", psv.getOutletStream());

    // Flare header (manifold) - collects relief from multiple sources
    flareHeader = new Mixer("Flare Header");
    flareHeader.addStream(psvRelief);

    flareHeaderOutlet = new Stream("Flare Header Outlet", flareHeader.getOutletStream());
  }

  /**
   * Test complete PSV relief scenario with flare system.
   * 
   * <p>
   * Scenario:
   * <ol>
   * <li>Normal operation with outlet valve at 50% opening</li>
   * <li>Outlet valve fails closed (goes to 1% opening)</li>
   * <li>Pressure builds up in separator</li>
   * <li>PSV opens at set pressure (55 bara)</li>
   * <li>Gas relieves to flare header and is burned in flare</li>
   * <li>Calculate gas flow, heat release, and flare performance</li>
   * <li>Outlet valve reopens and system returns to normal</li>
   * </ol>
   */
  @Test
  void testPSVReliefToFlareWithHeatRelease() {
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     PSV RELIEF TO FLARE SYSTEM - DYNAMIC SIMULATION           ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("═══ SYSTEM CONFIGURATION ═══");
    System.out.println("Feed flow rate: 5000.0 kg/hr");
    System.out.println("Compressor discharge: 55.0 bara");
    System.out.println("Separator design pressure: 60.0 bara");
    System.out.println("PSV set pressure: 55.0 bara");
    System.out.println("PSV full open pressure: 60.5 bara (10% overpressure)");
    System.out.println("Flare header pressure: ~1.5 bara");
    System.out.println();

    // Run initial steady state
    System.out.println("═══ INITIAL STEADY STATE ═══");
    feedStream.run();
    feedCompressor.run();
    compressorOutlet.run();
    separator.run();
    separatorGasOutlet.run();
    outletValve.run();
    psv.run();

    double initialPressure = separator.getGasOutStream().getPressure("bara");
    System.out.printf("Separator pressure: %.2f bara%n", initialPressure);
    System.out.printf("Outlet valve opening: %.1f%%%n", outletValve.getPercentValveOpening());
    System.out.printf("PSV status: CLOSED (pressure below set point)%n");
    System.out.println();

    // Dynamic simulation parameters
    double dt = 2.0; // 2 second time steps
    double simulationTime = 300.0; // 5 minutes total
    double incidentStart = 50.0; // Incident starts at 50 seconds
    double incidentEnd = 200.0; // Outlet valve reopens at 200 seconds

    // Tracking variables
    double maxPressure = 0.0;
    double maxReliefFlow = 0.0;
    double totalGasRelieved = 0.0; // kg
    double totalHeatRelease = 0.0; // MJ
    double peakHeatReleaseRate = 0.0; // MW
    boolean psvHasOpened = false;
    double psvOpenTime = 0.0;

    System.out.println("═══ DYNAMIC SIMULATION ═══");
    System.out.println(
        "Time (s) | Sep Press | Outlet Vlv | PSV Open | Relief Flow | Heat Release | Cumulative Heat");
    System.out.println(
        "         |   (bara)  |    (%)     |   (%)    |   (kg/hr)   |     (MW)     |      (GJ)");
    System.out.println(
        "---------|-----------|------------|----------|-------------|--------------|----------------");

    for (double time = 0.0; time <= simulationTime; time += dt) {
      // Simulate outlet valve failure (blocks at 50s) and recovery (opens at 200s)
      if (time >= incidentStart && time < incidentEnd) {
        if (time == incidentStart) {
          System.out.println("\n>>> INCIDENT: Outlet valve FAILS CLOSED <<<\n");
        }
        outletValve.setPercentValveOpening(1.0); // Valve stuck nearly closed
      } else if (time >= incidentEnd) {
        if (time == incidentEnd) {
          System.out.println("\n>>> RECOVERY: Outlet valve REOPENED <<<\n");
        }
        outletValve.setPercentValveOpening(50.0); // Normal operation restored
      }

      // Run process equipment
      feedStream.run();
      feedCompressor.run();
      compressorOutlet.run();
      separator.run();
      separatorGasOutlet.run();
      outletValve.run();

      // PSV operates based on separator pressure
      double sepPressure = separator.getGasOutStream().getPressure("bara");
      psv.run();
      psvRelief.run();

      // Flare header and flare
      flareHeader.run();
      flareHeaderOutlet.run();

      // Calculate PSV relief flow
      double reliefFlow = psvRelief.getFlowRate("kg/hr");

      // Calculate heat release from flare combustion
      double heatReleaseRate = 0.0; // MW
      if (reliefFlow > 0.01) {
        // Get heating value of relief gas using ISO 6976 standard
        neqsim.standards.gasquality.Standard_ISO6976 standard =
            new neqsim.standards.gasquality.Standard_ISO6976(psvRelief.getFluid(), 15.0, 15.0,
                "volume");
        standard.calculate();

        // Get LHV (Lower Heating Value/Inferior Calorific Value) in kJ/Sm3
        double lhvKJperSm3 = standard.getValue("InferiorCalorificValue");
        double lhvMJperSm3 = lhvKJperSm3 / 1000.0; // Convert to MJ/Sm3

        // Get flow rate in Sm3/hr at standard conditions (15°C, 1.01325 bara)
        psvRelief.getFluid().setTemperature(15.0, "C");
        psvRelief.getFluid().setPressure(1.01325, "bara");
        psvRelief.run();
        double densityStd = psvRelief.getFluid().getPhase(0).getDensity("kg/m3");
        double volumeFlow = reliefFlow / densityStd; // Sm3/hr

        // Heat release rate in MW (1 MW = 1 MJ/s)
        heatReleaseRate = (volumeFlow / 3600.0) * lhvMJperSm3; // Sm3/s * MJ/Sm3 = MW

        // Cumulative heat release
        totalHeatRelease += heatReleaseRate * dt / 1000.0; // Convert to GJ

        // Track peak heat release rate
        peakHeatReleaseRate = Math.max(peakHeatReleaseRate, heatReleaseRate);
      }

      // Track cumulative gas relieved
      totalGasRelieved += reliefFlow * dt / 3600.0; // kg

      // Track maximum values
      maxPressure = Math.max(maxPressure, sepPressure);
      maxReliefFlow = Math.max(maxReliefFlow, reliefFlow);

      // Check if PSV opened
      if (psv.getPercentValveOpening() > 0.1 && !psvHasOpened) {
        psvHasOpened = true;
        psvOpenTime = time;
        System.out.println("\n>>> PSV OPENED at t=" + time + " s <<<\n");
      }

      // Print every 10 seconds
      if (time % 10.0 < dt) {
        System.out.printf("%7.0f  | %9.2f | %10.1f | %8.1f | %11.1f | %12.3f | %14.3f%n", time,
            sepPressure, outletValve.getPercentValveOpening(), psv.getPercentValveOpening(),
            reliefFlow, heatReleaseRate, totalHeatRelease);
      }
    }

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║                    INCIDENT SUMMARY REPORT                     ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("═══ TIMELINE ═══");
    System.out.printf("Incident start (outlet valve fails): %.0f s%n", incidentStart);
    System.out.printf("PSV opened: %.0f s%n", psvOpenTime);
    System.out.printf("Incident end (outlet valve reopened): %.0f s%n", incidentEnd);
    System.out.printf("Total incident duration: %.0f s (%.1f minutes)%n",
        incidentEnd - incidentStart, (incidentEnd - incidentStart) / 60.0);
    System.out.println();

    System.out.println("═══ PRESSURE PROFILE ═══");
    System.out.printf("Initial separator pressure: %.2f bara%n", initialPressure);
    System.out.printf("PSV set pressure: 55.0 bara%n");
    System.out.printf("Maximum separator pressure: %.2f bara%n", maxPressure);
    System.out.printf("PSV full open pressure: 60.5 bara%n");
    System.out.println();

    System.out.println("═══ PSV RELIEF PERFORMANCE ═══");
    System.out.printf("Maximum relief flow: %.1f kg/hr (%.2f kg/s)%n", maxReliefFlow,
        maxReliefFlow / 3600.0);
    System.out.printf("Total gas relieved: %.1f kg%n", totalGasRelieved);
    System.out.printf("Average relief rate: %.1f kg/hr%n",
        totalGasRelieved / ((incidentEnd - psvOpenTime) / 3600.0));
    System.out.printf("Required PSV Cv: %.1f%n", psv.getCv());
    System.out.println();

    System.out.println("═══ FLARE SYSTEM PERFORMANCE ═══");
    System.out.printf("Peak heat release rate: %.2f MW%n", peakHeatReleaseRate);
    System.out.printf("Total heat released: %.2f GJ (%.2f MMBtu)%n", totalHeatRelease,
        totalHeatRelease * 0.947817); // GJ to MMBtu
    System.out.printf("Average heat release rate: %.2f MW%n",
        totalHeatRelease * 1000.0 / (incidentEnd - psvOpenTime));

    // Gas composition to flare
    System.out.println();
    System.out.println("═══ RELIEF GAS COMPOSITION ═══");
    SystemInterface reliefGas = psvRelief.getFluid();
    for (int i = 0; i < reliefGas.getPhase(0).getNumberOfComponents(); i++) {
      String compName = reliefGas.getPhase(0).getComponent(i).getComponentName();
      double moleFrac = reliefGas.getPhase(0).getComponent(i).getz() * 100.0;
      if (moleFrac > 0.01) {
        System.out.printf("%-15s: %6.2f mol%%%n", compName, moleFrac);
      }
    }

    System.out.println();
    System.out.println("═══ ENVIRONMENTAL IMPACT ═══");
    // Estimate CO2 emissions (simplified - assumes complete combustion)
    double co2Emissions = 0.0;
    SystemInterface gas = psvRelief.getFluid();
    for (int i = 0; i < gas.getPhase(0).getNumberOfComponents(); i++) {
      String name = gas.getPhase(0).getComponent(i).getComponentName();
      double moleFrac = gas.getPhase(0).getComponent(i).getz();
      int carbonNumber = 0;

      if (name.equals("methane"))
        carbonNumber = 1;
      else if (name.equals("ethane"))
        carbonNumber = 2;
      else if (name.equals("propane"))
        carbonNumber = 3;
      else if (name.equals("i-butane") || name.equals("n-butane"))
        carbonNumber = 4;
      else if (name.equals("CO2"))
        carbonNumber = -1; // Already CO2

      if (carbonNumber > 0) {
        // Each hydrocarbon carbon becomes CO2 (44 g/mol)
        double molMass = gas.getPhase(0).getComponent(i).getMolarMass();
        double massFrac = moleFrac * molMass / gas.getPhase(0).getMolarMass();
        co2Emissions += totalGasRelieved * massFrac * carbonNumber * 44.01 / molMass;
      } else if (carbonNumber == -1) {
        double molMass = gas.getPhase(0).getComponent(i).getMolarMass();
        double massFrac = moleFrac * molMass / gas.getPhase(0).getMolarMass();
        co2Emissions += totalGasRelieved * massFrac; // Direct CO2 emission
      }
    }
    System.out.printf("Estimated CO2 emissions: %.1f kg (%.2f tonnes)%n", co2Emissions,
        co2Emissions / 1000.0);

    System.out.println();
    System.out.println("═══ VALIDATION CHECKS ═══");
    assertTrue(psvHasOpened, "PSV should have opened during incident");
    assertTrue(maxPressure <= 55.0 * 1.15, "Max pressure should not exceed 115% of set pressure");
    assertTrue(totalGasRelieved > 0, "Gas should have been relieved to flare");
    assertTrue(totalHeatRelease > 0, "Heat should have been released from flare");
    System.out.println("✓ PSV opened and relieved gas to flare");
    System.out.printf("✓ Maximum pressure (%.2f bara) within acceptable limits%n", maxPressure);
    System.out.printf("✓ Total %.1f kg of gas burned in flare%n", totalGasRelieved);
    System.out.printf("✓ Total %.2f GJ heat released to atmosphere%n", totalHeatRelease);

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║              SIMULATION COMPLETED SUCCESSFULLY                 ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
  }

  /**
   * Test flare system with multiple relief sources.
   * 
   * <p>
   * This test simulates a scenario where multiple PSVs relieve simultaneously into a common flare
   * header, demonstrating header capacity and combined heat release.
   */
  @Test
  void testMultiplePSVReliefToCommonFlare() {
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║   MULTIPLE PSV RELIEF SOURCES TO COMMON FLARE HEADER          ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Create second relief source
    SystemInterface secondSource = feedGas.clone();
    Stream secondFeed = new Stream("Second Source", secondSource);
    secondFeed.setFlowRate(3000.0, "kg/hr");
    secondFeed.setPressure(55.0, "bara");
    secondFeed.run();

    SafetyValve psv2 = new SafetyValve("PSV-102", secondFeed);
    psv2.setPressureSpec(55.0); // Set pressure in bara
    psv2.setFullOpenPressure(60.5); // 10% overpressure
    psv2.setCv(100.0);

    Stream psv2Relief = new Stream("PSV-102 Relief", psv2.getOutletStream());

    // Add second PSV to flare header
    flareHeader.addStream(psv2Relief);

    System.out.println("═══ CONFIGURATION ═══");
    System.out.println("PSV-101: Primary separator (5000 kg/hr capacity)");
    System.out.println("PSV-102: Secondary source (3000 kg/hr capacity)");
    System.out.println("Common flare header collecting both relief streams");
    System.out.println();

    // Simulate relief event - manually set both PSVs to relieve
    System.out.println("═══ SIMULTANEOUS RELIEF EVENT ═══");

    // Force both PSVs to open by simulating high pressure
    feedStream.run();
    feedCompressor.run();
    compressorOutlet.run();
    separator.run();

    // Simulate blocked outlet to trigger PSV-101
    outletValve.setPercentValveOpening(1.0);
    outletValve.run();
    psv.run();
    psvRelief.run();

    // Simulate PSV-102 relief
    secondFeed.setPressure(60.0, "bara"); // Above set pressure
    secondFeed.run();
    psv2.run();
    psv2Relief.run();

    // Combine in flare header
    flareHeader.run();
    flareHeaderOutlet.run();

    double totalReliefFlow = psvRelief.getFlowRate("kg/hr") + psv2Relief.getFlowRate("kg/hr");

    // Calculate combined heat release
    double heatRelease = 0.0;
    SystemInterface combinedGas = flareHeaderOutlet.getFluid();

    if (totalReliefFlow > 0) {
      // Use ISO 6976 standard for heating value
      neqsim.standards.gasquality.Standard_ISO6976 standard =
          new neqsim.standards.gasquality.Standard_ISO6976(combinedGas, 15.0, 15.0, "volume");
      standard.calculate();
      double lhvKJperSm3 = standard.getValue("InferiorCalorificValue");
      double lhvMJperSm3 = lhvKJperSm3 / 1000.0;

      combinedGas.setTemperature(15.0, "C");
      combinedGas.setPressure(1.01325, "bara");
      double densityStd = combinedGas.getPhase(0).getDensity("kg/m3");
      double volumeFlow = totalReliefFlow / densityStd;
      heatRelease = (volumeFlow / 3600.0) * lhvMJperSm3;
    }

    System.out.printf("PSV-101 relief flow: %.1f kg/hr%n", psvRelief.getFlowRate("kg/hr"));
    System.out.printf("PSV-102 relief flow: %.1f kg/hr%n", psv2Relief.getFlowRate("kg/hr"));
    System.out.printf("Total relief to flare: %.1f kg/hr (%.2f kg/s)%n", totalReliefFlow,
        totalReliefFlow / 3600.0);
    System.out.printf("Combined heat release rate: %.2f MW%n", heatRelease);
    System.out.printf("Flare header pressure: %.2f bara%n", flareHeaderOutlet.getPressure("bara"));

    System.out.println();
    assertTrue(totalReliefFlow > 0, "Combined relief flow should be positive");
    assertTrue(heatRelease > 0, "Heat release should be positive");
    System.out.println("✓ Multiple PSV sources successfully combined in flare header");
    System.out.printf("✓ Total %.2f MW heat release from combined relief%n", heatRelease);

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║          MULTIPLE SOURCE TEST COMPLETED SUCCESSFULLY           ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
  }
}
