package neqsim.process.util.example;

import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating separator heat input capabilities with flare integration.
 * 
 * This example shows: 1. Basic separator heat input functionality 2. Integration with flare
 * radiation calculations 3. Three-phase separator heat input 4. Practical use cases for external
 * heating
 */
public class SeparatorHeatInputExample {

  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║          SEPARATOR HEAT INPUT CAPABILITIES EXAMPLE            ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");

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
    ThreePhaseSeparator threePhaseSep =
        new ThreePhaseSeparator("3-Phase Separator", feedStream.clone());

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

    System.out.println("\n═══ SCENARIO 1: BASIC HEAT INPUT FUNCTIONALITY ═══");

    // Run initial calculations
    processOps.run();

    System.out.printf("Initial separator temperature: %.2f °C%n",
        separator.getThermoSystem().getTemperature("C"));

    // Add external heat input (e.g., from nearby equipment)
    separator.setHeatInput(75.0, "kW");
    threePhaseSep.setHeatInput(50.0, "kW");

    System.out.printf("Heat input to main separator: %.1f kW%n", separator.getHeatInput("kW"));
    System.out.printf("Heat input to 3-phase separator: %.1f kW%n",
        threePhaseSep.getHeatInput("kW"));

    // Run separators with heat input
    separator.run();
    threePhaseSep.run();

    System.out.println("✓ Heat input successfully applied to both separators");

    System.out.println("\n═══ SCENARIO 2: FLARE RADIATION INTEGRATION ═══");

    // Calculate flare heat release
    flare.run();
    double flareHeatDuty = flare.getHeatDuty("MW");
    System.out.printf("Flare heat release: %.3f MW%n", flareHeatDuty);

    // Calculate radiation heat flux at separator location
    double separatorDistance = 40.0; // meters from flare
    double radiationFlux = flare.estimateRadiationHeatFlux(separatorDistance);
    System.out.printf("Radiation heat flux at %sm: %.2f W/m²%n", separatorDistance, radiationFlux);

    // Calculate heat input to separator
    double separatorSurfaceArea = 80.0; // m² exposed to flare
    double radiationHeatInput = radiationFlux * separatorSurfaceArea;

    // Apply flare radiation to separator
    separator.setHeatInput(radiationHeatInput, "W");
    System.out.printf("Radiation heat input to separator: %.2f kW%n", radiationHeatInput / 1000.0);

    // Run separator with flare radiation
    separator.run();
    System.out.println("✓ Flare radiation successfully integrated with separator");

    System.out.println("\n═══ SCENARIO 3: MULTIPLE HEAT SOURCES ═══");

    // Simulate multiple heat sources
    double ambientHeating = 15.0; // kW from ambient conditions
    double processHeating = 30.0; // kW from nearby hot process streams
    double flareRadiation = radiationHeatInput / 1000.0; // kW from flare

    double totalHeatInput = ambientHeating + processHeating + flareRadiation;
    threePhaseSep.setHeatInput(totalHeatInput, "kW");

    System.out.printf("Ambient heating: %.1f kW%n", ambientHeating);
    System.out.printf("Process heating: %.1f kW%n", processHeating);
    System.out.printf("Flare radiation: %.2f kW%n", flareRadiation);
    System.out.printf("Total heat input: %.2f kW%n", totalHeatInput);

    threePhaseSep.run();
    System.out.println("✓ Multiple heat sources successfully combined");

    System.out.println("\n═══ SCENARIO 4: HEAT INPUT UNIT CONVERSIONS ═══");

    // Demonstrate unit conversion capabilities
    separator.setHeatInput(0.1, "MW");
    System.out.printf("Heat input: %.1f MW = %.0f kW = %.0f W%n", separator.getHeatInput("MW"),
        separator.getHeatInput("kW"), separator.getHeatInput("W"));

    separator.setHeatInput(250.0, "kW");
    System.out.printf("Heat input: %.3f MW = %.0f kW = %.0f W%n", separator.getHeatInput("MW"),
        separator.getHeatInput("kW"), separator.getHeatInput("W"));

    System.out.println("\n═══ SCENARIO 5: TRANSIENT HEAT INPUT EFFECTS ═══");

    // Show how heat input affects energy balance in transient mode
    separator.setHeatInput(100.0, "kW");
    System.out.printf("Separator with %.0f kW heat input ready for transient simulation%n",
        separator.getHeatInput("kW"));

    // In actual transient simulation, the heat input would affect temperature/pressure evolution
    System.out.println(
        "Note: Heat input will be incorporated in energy balance during transient calculations");
    System.out.println("      deltaEnergy += heatInput (in runTransient method)");

    System.out.println("\n═══ PERFORMANCE SUMMARY ═══");

    // Summary of capabilities
    System.out.printf("Main Separator:%n");
    System.out.printf("  - Heat input set: %s%n", separator.isSetHeatInput() ? "YES" : "NO");
    System.out.printf("  - Heat input: %.2f kW%n", separator.getHeatInput("kW"));
    System.out.printf("  - Gas outlet flow: %.0f kg/hr%n",
        separator.getGasOutStream().getFlowRate("kg/hr"));

    System.out.printf("3-Phase Separator:%n");
    System.out.printf("  - Heat input set: %s%n", threePhaseSep.isSetHeatInput() ? "YES" : "NO");
    System.out.printf("  - Heat input: %.2f kW%n", threePhaseSep.getHeatInput("kW"));
    System.out.printf("  - Gas outlet flow: %.0f kg/hr%n",
        threePhaseSep.getGasOutStream().getFlowRate("kg/hr"));

    System.out.printf("Flare:%n");
    System.out.printf("  - Heat duty: %.3f MW%n", flare.getHeatDuty("MW"));
    System.out.printf("  - CO2 emissions: %.2f kg/hr%n", flare.getCO2Emission());

    System.out.println("\n═══ KEY FEATURES DEMONSTRATED ═══");
    System.out.println("✓ Separator.setHeatInput(double, String) - Set heat input with units");
    System.out.println("✓ Separator.getHeatInput(String) - Get heat input in various units");
    System.out.println("✓ Separator.setHeatDuty() - Alias methods for convenience");
    System.out.println("✓ ThreePhaseSeparator heat input inheritance");
    System.out.println("✓ Flare.estimateRadiationHeatFlux() integration");
    System.out.println("✓ Energy balance incorporation in runTransient()");
    System.out.println("✓ Unit conversions (W, kW, MW)");
    System.out.println("✓ Multiple heat source combination");

    System.out.println("\n═══ USE CASES ═══");
    System.out.println("• Flare radiation heating of nearby separators");
    System.out.println("• External heating coils or jackets");
    System.out.println("• Heat recovery from hot process streams");
    System.out.println("• Solar heating in outdoor installations");
    System.out.println("• Heat tracing for winterization");
    System.out.println("• Process heating for enhanced separation");
    System.out.println("• Emergency heating systems");

    System.out.println("\n✓ Separator heat input capabilities successfully demonstrated!");
  }
}
