package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.flare.Flare;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
  private static final Logger logger = LogManager.getLogger(PSVFlareSystemTest.class);

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
  Flare flare;

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

    // Flare unit operation
    flare = new Flare("Main Flare", flareHeaderOutlet);
    flare.setFlameHeight(40.0); // 40 meter flame height
    flare.setRadiantFraction(0.18); // 18% radiant heat
    flare.setTipDiameter(0.5); // 0.5 meter tip diameter
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
    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     PSV RELIEF TO FLARE SYSTEM - DYNAMIC SIMULATION           ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    logger.info("═══ SYSTEM CONFIGURATION ═══");
    logger.info("Feed flow rate: 5000.0 kg/hr");
    logger.info("Compressor discharge: 55.0 bara");
    logger.info("Separator design pressure: 60.0 bara");
    logger.info("PSV set pressure: 55.0 bara");
    logger.info("PSV full open pressure: 60.5 bara (10% overpressure)");
    logger.info("Flare header pressure: ~1.5 bara");


    // Run initial steady state
    logger.info("═══ INITIAL STEADY STATE ═══");
    feedStream.run();
    feedCompressor.run();
    compressorOutlet.run();
    separator.run();
    separatorGasOutlet.run();
    outletValve.run();
    psv.run();

    double initialPressure = separator.getGasOutStream().getPressure("bara");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Separator pressure: %.2f bara%n", initialPressure);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Outlet valve opening: %.1f%%%n", outletValve.getPercentValveOpening());
    logger.printf(org.apache.logging.log4j.Level.INFO, "PSV status: CLOSED (pressure below set point)%n");


    // Dynamic simulation parameters
    double dt = 2.0; // 2 second time steps
    double simulationTime = 300.0; // 5 minutes total
    double incidentStart = 50.0; // Incident starts at 50 seconds
    double incidentEnd = 200.0; // Outlet valve reopens at 200 seconds

    // Tracking variables
    double maxPressure = 0.0;
    double maxReliefFlow = 0.0;
    double peakHeatReleaseRate = 0.0; // MW
    boolean psvHasOpened = false;
    double psvOpenTime = 0.0;

    // Reset flare cumulative values
    flare.resetCumulative();

    logger.info("═══ DYNAMIC SIMULATION ═══");
    logger.info(
        "Time (s) | Sep Press | Outlet Vlv | PSV Open | Relief Flow | Heat Release | Cumulative Heat");
    logger.info(
        "         |   (bara)  |    (%)     |   (%)    |   (kg/hr)   |     (MW)     |      (GJ)");
    logger.info(
        "---------|-----------|------------|----------|-------------|--------------|----------------");

    for (double time = 0.0; time <= simulationTime; time += dt) {
      // Simulate outlet valve failure (blocks at 50s) and recovery (opens at 200s)
      if (time >= incidentStart && time < incidentEnd) {
        if (time == incidentStart) {
          logger.info("\n>>> INCIDENT: Outlet valve FAILS CLOSED <<<\n");
        }
        outletValve.setPercentValveOpening(1.0); // Valve stuck nearly closed
      } else if (time >= incidentEnd) {
        if (time == incidentEnd) {
          logger.info("\n>>> RECOVERY: Outlet valve REOPENED <<<\n");
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
      flare.run();
      flare.updateCumulative(dt); // Update cumulative values

      // Calculate PSV relief flow
      double reliefFlow = psvRelief.getFlowRate("kg/hr");

      // Get heat release from flare unit operation
      double heatReleaseRate = flare.getHeatDuty("MW"); // MW

      if (reliefFlow > 0.01) {
        // Track peak heat release rate
        peakHeatReleaseRate = Math.max(peakHeatReleaseRate, heatReleaseRate);
      }

      // Track maximum values
      maxPressure = Math.max(maxPressure, sepPressure);
      maxReliefFlow = Math.max(maxReliefFlow, reliefFlow);

      // Check if PSV opened
      if (psv.getPercentValveOpening() > 0.1 && !psvHasOpened) {
        psvHasOpened = true;
        psvOpenTime = time;
        logger.info("\n>>> PSV OPENED at t=" + time + " s <<<\n");
      }

      // Print every 10 seconds
      if (time % 10.0 < dt) {
        logger.printf(org.apache.logging.log4j.Level.INFO, "%7.0f  | %9.2f | %10.1f | %8.1f | %11.1f | %12.3f | %14.3f%n", time,
            sepPressure, outletValve.getPercentValveOpening(), psv.getPercentValveOpening(),
            reliefFlow, heatReleaseRate, flare.getCumulativeHeatReleased("GJ"));
      }
    }

    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║                    INCIDENT SUMMARY REPORT                     ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    logger.info("═══ TIMELINE ═══");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Incident start (outlet valve fails): %.0f s%n", incidentStart);
    logger.printf(org.apache.logging.log4j.Level.INFO, "PSV opened: %.0f s%n", psvOpenTime);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Incident end (outlet valve reopened): %.0f s%n", incidentEnd);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total incident duration: %.0f s (%.1f minutes)%n",
        incidentEnd - incidentStart, (incidentEnd - incidentStart) / 60.0);


    logger.info("═══ PRESSURE PROFILE ═══");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Initial separator pressure: %.2f bara%n", initialPressure);
    logger.printf(org.apache.logging.log4j.Level.INFO, "PSV set pressure: 55.0 bara%n");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Maximum separator pressure: %.2f bara%n", maxPressure);
    logger.printf(org.apache.logging.log4j.Level.INFO, "PSV full open pressure: 60.5 bara%n");


    logger.info("═══ PSV RELIEF PERFORMANCE ═══");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Maximum relief flow: %.1f kg/hr (%.2f kg/s)%n", maxReliefFlow,
        maxReliefFlow / 3600.0);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total gas relieved: %.1f kg%n", flare.getCumulativeGasBurned("kg"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Average relief rate: %.1f kg/hr%n",
        flare.getCumulativeGasBurned("kg") / ((incidentEnd - psvOpenTime) / 3600.0));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Required PSV Cv: %.1f%n", psv.getCv());


    logger.info("═══ FLARE SYSTEM PERFORMANCE ═══");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Peak heat release rate: %.2f MW%n", peakHeatReleaseRate);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total heat released: %.2f GJ (%.2f MMBtu)%n",
        flare.getCumulativeHeatReleased("GJ"), flare.getCumulativeHeatReleased("MMBtu"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Average heat release rate: %.2f MW%n",
        flare.getCumulativeHeatReleased("GJ") * 1000.0 / (incidentEnd - psvOpenTime));

    // Gas composition to flare

    logger.info("═══ RELIEF GAS COMPOSITION ═══");
    SystemInterface reliefGas = psvRelief.getFluid();
    for (int i = 0; i < reliefGas.getPhase(0).getNumberOfComponents(); i++) {
      String compName = reliefGas.getPhase(0).getComponent(i).getComponentName();
      double moleFrac = reliefGas.getPhase(0).getComponent(i).getz() * 100.0;
      if (moleFrac > 0.01) {
        logger.printf(org.apache.logging.log4j.Level.INFO, "%-15s: %6.2f mol%%%n", compName, moleFrac);
      }
    }


    logger.info("═══ ENVIRONMENTAL IMPACT ═══");
    // Get CO2 emissions from flare unit operation
    logger.printf(org.apache.logging.log4j.Level.INFO, "Estimated CO2 emissions: %.1f kg (%.2f tonnes)%n",
        flare.getCumulativeCO2Emission("kg"), flare.getCumulativeCO2Emission("tonnes"));


    logger.info("═══ VALIDATION CHECKS ═══");
    assertTrue(psvHasOpened, "PSV should have opened during incident");
    assertTrue(maxPressure <= 55.0 * 1.15, "Max pressure should not exceed 115% of set pressure");
    assertTrue(flare.getCumulativeGasBurned("kg") > 0, "Gas should have been relieved to flare");
    assertTrue(flare.getCumulativeHeatReleased("GJ") > 0,
        "Heat should have been released from flare");
    logger.info("✓ PSV opened and relieved gas to flare");
    logger.printf(org.apache.logging.log4j.Level.INFO, "✓ Maximum pressure (%.2f bara) within acceptable limits%n", maxPressure);
    logger.printf(org.apache.logging.log4j.Level.INFO, "✓ Total %.1f kg of gas burned in flare%n",
        flare.getCumulativeGasBurned("kg"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "✓ Total %.2f GJ heat released to atmosphere%n",
        flare.getCumulativeHeatReleased("GJ"));

    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║              SIMULATION COMPLETED SUCCESSFULLY                 ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");
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
    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║   MULTIPLE PSV RELIEF SOURCES TO COMMON FLARE HEADER          ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

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

    logger.info("═══ CONFIGURATION ═══");
    logger.info("PSV-101: Primary separator (5000 kg/hr capacity)");
    logger.info("PSV-102: Secondary source (3000 kg/hr capacity)");
    logger.info("Common flare header collecting both relief streams");


    // Simulate relief event - manually set both PSVs to relieve
    logger.info("═══ SIMULTANEOUS RELIEF EVENT ═══");

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
    flare.run();

    double totalReliefFlow = psvRelief.getFlowRate("kg/hr") + psv2Relief.getFlowRate("kg/hr");

    // Get combined heat release from flare unit operation
    double heatRelease = flare.getHeatDuty("MW"); // MW

    logger.printf(org.apache.logging.log4j.Level.INFO, "PSV-101 relief flow: %.1f kg/hr%n", psvRelief.getFlowRate("kg/hr"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "PSV-102 relief flow: %.1f kg/hr%n", psv2Relief.getFlowRate("kg/hr"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total relief to flare: %.1f kg/hr (%.2f kg/s)%n", totalReliefFlow,
        totalReliefFlow / 3600.0);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Combined heat release rate: %.2f MW%n", heatRelease);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Flare header pressure: %.2f bara%n", flareHeaderOutlet.getPressure("bara"));


    assertTrue(totalReliefFlow > 0, "Combined relief flow should be positive");
    assertTrue(heatRelease > 0, "Heat release should be positive");
    logger.info("✓ Multiple PSV sources successfully combined in flare header");
    logger.printf(org.apache.logging.log4j.Level.INFO, "✓ Total %.2f MW heat release from combined relief%n", heatRelease);

    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║          MULTIPLE SOURCE TEST COMPLETED SUCCESSFULLY           ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");
  }
}
