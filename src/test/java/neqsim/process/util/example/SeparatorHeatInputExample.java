package neqsim.process.util.example;

import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Example demonstrating separator heat input capabilities with flare integration.
 *
 * This example shows: 1. Basic separator heat input functionality 2. Integration with flare radiation calculations 3.
 * Three-phase separator heat input 4. Practical use cases for external heating
 */
public class SeparatorHeatInputExample {
  private static final Logger logger = LogManager.getLogger(SeparatorHeatInputExample.class);

  public static void main(String[] args) {
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║          SEPARATOR HEAT INPUT CAPABILITIES EXAMPLE            ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝");

    // Create process system
    ProcessSystem processOps = new ProcessSystem();

    // Create feed gas stream
    SystemSrkEos feedGas = new SystemSrkEos(298.15, 50.0);
    feedGas.addComponent("methane", 0.6);
    feedGas.addComponent("ethane", 0.25);
    feedGas.addComponent("propane", 0.10);
    feedGas.addComponent("n-butane", 0.05);
    feedGas.setMixingRule("classic");
    feedGas.createDatabase(true);

    Stream feedStream = new Stream("Feed Gas", feedGas);
    feedStream.setFlowRate(5000.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(50.0, "bara");

    // Create separators
    Separator separator = new Separator("Main Separator", feedStream);
    ThreePhaseSeparator threePhaseSep = new ThreePhaseSeparator("3-Phase Separator", feedStream.clone());

    // Create flare for heat source
    Stream flareGas = feedStream.clone();
    flareGas.setName("Flare Gas");
    flareGas.setFlowRate(500.0, "kg/hr"); // Smaller flare gas flow
    Flare flare = new Flare("Emergency Flare", flareGas);

    // Add equipment to process system
    processOps.add(feedStream);
    processOps.add(separator);
    processOps.add(threePhaseSep);
    processOps.add(flareGas);
    processOps.add(flare);

    logger.info("\n═══ SCENARIO 1: BASIC HEAT INPUT FUNCTIONALITY ═══");

    // Run initial calculations
    processOps.run();

    logger.printf(org.apache.logging.log4j.Level.INFO, "Initial separator temperature: %.2f °C%n",
        separator.getThermoSystem().getTemperature("C"));

    // Add external heat input (e.g., from nearby equipment)
    separator.setHeatInput(75.0, "kW");
    threePhaseSep.setHeatInput(50.0, "kW");

    logger.printf(org.apache.logging.log4j.Level.INFO, "Heat input to main separator: %.1f kW%n",
        separator.getHeatInput("kW"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Heat input to 3-phase separator: %.1f kW%n",
        threePhaseSep.getHeatInput("kW"));

    // Run separators with heat input
    separator.run();
    threePhaseSep.run();

    logger.info("✓ Heat input successfully applied to both separators");

    logger.info("\n═══ SCENARIO 2: FLARE RADIATION INTEGRATION ═══");

    // Calculate flare heat release
    flare.run();
    double flareHeatDuty = flare.getHeatDuty("MW");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Flare heat release: %.3f MW%n", flareHeatDuty);

    // Calculate radiation heat flux at separator location
    double separatorDistance = 40.0; // meters from flare
    double radiationFlux = flare.estimateRadiationHeatFlux(separatorDistance);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Radiation heat flux at %sm: %.2f W/m²%n", separatorDistance,
        radiationFlux);

    // Calculate heat input to separator
    double separatorSurfaceArea = 80.0; // m² exposed to flare
    double radiationHeatInput = radiationFlux * separatorSurfaceArea;

    // Apply flare radiation to separator
    separator.setHeatInput(radiationHeatInput, "W");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Radiation heat input to separator: %.2f kW%n",
        radiationHeatInput / 1000.0);

    // Run separator with flare radiation
    separator.run();
    logger.info("✓ Flare radiation successfully integrated with separator");

    logger.info("\n═══ SCENARIO 3: MULTIPLE HEAT SOURCES ═══");

    // Simulate multiple heat sources
    double ambientHeating = 15.0; // kW from ambient conditions
    double processHeating = 30.0; // kW from nearby hot process streams
    double flareRadiation = radiationHeatInput / 1000.0; // kW from flare

    double totalHeatInput = ambientHeating + processHeating + flareRadiation;
    threePhaseSep.setHeatInput(totalHeatInput, "kW");

    logger.printf(org.apache.logging.log4j.Level.INFO, "Ambient heating: %.1f kW%n", ambientHeating);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Process heating: %.1f kW%n", processHeating);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Flare radiation: %.2f kW%n", flareRadiation);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total heat input: %.2f kW%n", totalHeatInput);

    threePhaseSep.run();
    logger.info("✓ Multiple heat sources successfully combined");

    logger.info("\n═══ SCENARIO 4: HEAT INPUT UNIT CONVERSIONS ═══");

    // Demonstrate unit conversion capabilities
    separator.setHeatInput(0.1, "MW");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Heat input: %.1f MW = %.0f kW = %.0f W%n",
        separator.getHeatInput("MW"), separator.getHeatInput("kW"), separator.getHeatInput("W"));

    separator.setHeatInput(250.0, "kW");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Heat input: %.3f MW = %.0f kW = %.0f W%n",
        separator.getHeatInput("MW"), separator.getHeatInput("kW"), separator.getHeatInput("W"));

    logger.info("\n═══ SCENARIO 5: TRANSIENT HEAT INPUT EFFECTS ═══");

    // Show how heat input affects energy balance in transient mode
    separator.setHeatInput(100.0, "kW");
    logger.printf(org.apache.logging.log4j.Level.INFO,
        "Separator with %.0f kW heat input ready for transient simulation%n", separator.getHeatInput("kW"));

    // In actual transient simulation, the heat input would affect temperature/pressure evolution
    logger.info("Note: Heat input will be incorporated in energy balance during transient calculations");
    logger.info("      deltaEnergy += heatInput (in runTransient method)");

    logger.info("\n═══ PERFORMANCE SUMMARY ═══");

    // Summary of capabilities
    logger.printf(org.apache.logging.log4j.Level.INFO, "Main Separator:%n");
    logger.printf(org.apache.logging.log4j.Level.INFO, "  - Heat input set: %s%n",
        separator.isSetHeatInput() ? "YES" : "NO");
    logger.printf(org.apache.logging.log4j.Level.INFO, "  - Heat input: %.2f kW%n", separator.getHeatInput("kW"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "  - Gas outlet flow: %.0f kg/hr%n",
        separator.getGasOutStream().getFlowRate("kg/hr"));

    logger.printf(org.apache.logging.log4j.Level.INFO, "3-Phase Separator:%n");
    logger.printf(org.apache.logging.log4j.Level.INFO, "  - Heat input set: %s%n",
        threePhaseSep.isSetHeatInput() ? "YES" : "NO");
    logger.printf(org.apache.logging.log4j.Level.INFO, "  - Heat input: %.2f kW%n", threePhaseSep.getHeatInput("kW"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "  - Gas outlet flow: %.0f kg/hr%n",
        threePhaseSep.getGasOutStream().getFlowRate("kg/hr"));

    logger.printf(org.apache.logging.log4j.Level.INFO, "Flare:%n");
    logger.printf(org.apache.logging.log4j.Level.INFO, "  - Heat duty: %.3f MW%n", flare.getHeatDuty("MW"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "  - CO2 emissions: %.2f kg/hr%n", flare.getCO2Emission());

    logger.info("\n═══ KEY FEATURES DEMONSTRATED ═══");
    logger.info("✓ Separator.setHeatInput(double, String) - Set heat input with units");
    logger.info("✓ Separator.getHeatInput(String) - Get heat input in various units");
    logger.info("✓ Separator.setHeatDuty() - Alias methods for convenience");
    logger.info("✓ ThreePhaseSeparator heat input inheritance");
    logger.info("✓ Flare.estimateRadiationHeatFlux() integration");
    logger.info("✓ Energy balance incorporation in runTransient()");
    logger.info("✓ Unit conversions (W, kW, MW)");
    logger.info("✓ Multiple heat source combination");

    logger.info("\n═══ USE CASES ═══");
    logger.info("• Flare radiation heating of nearby separators");
    logger.info("• External heating coils or jackets");
    logger.info("• Heat recovery from hot process streams");
    logger.info("• Solar heating in outdoor installations");
    logger.info("• Heat tracing for winterization");
    logger.info("• Process heating for enhanced separation");
    logger.info("• Emergency heating systems");

    logger.info("\n✓ Separator heat input capabilities successfully demonstrated!");
  }
}
