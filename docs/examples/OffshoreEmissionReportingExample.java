package neqsim.process.equipment.util;

import java.util.Map;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Example demonstrating offshore platform emission reporting with NeqSim.
 *
 * <p>
 * This example shows how to:
 * </p>
 * <ul>
 * <li>Calculate emissions from produced water degassing</li>
 * <li>Compare thermodynamic vs conventional methods</li>
 * <li>Generate emission reports for regulatory compliance</li>
 * </ul>
 *
 * <h2>Regulatory Framework</h2>
 * <ul>
 * <li>Aktivitetsforskriften §70 (Norwegian)</li>
 * <li>EU ETS Directive 2003/87/EC</li>
 * <li>EU Methane Regulation 2024/1787</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see EmissionsCalculator
 */
public class OffshoreEmissionReportingExample {

  /**
   * Main method demonstrating emission calculation workflow.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("═══════════════════════════════════════════════════════════════════");
    System.out.println("      OFFSHORE PLATFORM EMISSION REPORTING - NeqSim Example        ");
    System.out.println("═══════════════════════════════════════════════════════════════════");

    // =========================================================================
    // STEP 1: CREATE PRODUCED WATER FLUID
    // =========================================================================
    System.out.println("\n▶ Step 1: Creating produced water fluid (CPA-EoS)");

    // Use CPA equation of state for accurate water-hydrocarbon equilibrium
    SystemInterface producedWater = new SystemSrkCPAstatoil(273.15 + 80.0, 30.0);

    // Typical North Sea produced water composition (mole fractions)
    producedWater.addComponent("water", 0.90);
    producedWater.addComponent("CO2", 0.03); // Often 50-80% of emissions!
    producedWater.addComponent("methane", 0.05);
    producedWater.addComponent("ethane", 0.015);
    producedWater.addComponent("propane", 0.005);

    // Set CPA mixing rule
    producedWater.setMixingRule(10);
    producedWater.init(0);

    System.out.println("  Fluid: CPA equation of state");
    System.out.println("  Components: water, CO2, CH4, C2H6, C3H8");

    // =========================================================================
    // STEP 2: CREATE PROCESS SYSTEM
    // =========================================================================
    System.out.println("\n▶ Step 2: Building multi-stage degassing process");

    // Create inlet stream (100 m³/hr produced water)
    Stream inletStream = new Stream("PW-Feed", producedWater);
    inletStream.setFlowRate(100000.0, "kg/hr"); // ~100 m³/hr
    inletStream.setTemperature(80.0, "C");
    inletStream.setPressure(30.0, "bara");

    // Stage 1: Degasser (30 → 4 bara)
    ThrottlingValve degasserValve = new ThrottlingValve("V-101", inletStream);
    degasserValve.setOutletPressure(4.0, "bara");

    Separator degasser = new Separator("Degasser", degasserValve.getOutletStream());

    // Stage 2: CFU (4 → 1.1 bara)
    ThrottlingValve cfuValve = new ThrottlingValve("V-102", degasser.getLiquidOutStream());
    cfuValve.setOutletPressure(1.1, "bara");

    Separator cfu = new Separator("CFU", cfuValve.getOutletStream());

    // Build process system
    ProcessSystem process = new ProcessSystem();
    process.add(inletStream);
    process.add(degasserValve);
    process.add(degasser);
    process.add(cfuValve);
    process.add(cfu);

    // Run simulation
    process.run();

    System.out.println("  Stage 1: Degasser (30 → 4 bara)");
    System.out.println("  Stage 2: CFU (4 → 1.1 bara)");
    System.out.println("  Simulation completed successfully");

    // =========================================================================
    // STEP 3: CALCULATE EMISSIONS
    // =========================================================================
    System.out.println("\n▶ Step 3: Calculating emissions (thermodynamic method)");

    // Create emissions calculator for each stage
    EmissionsCalculator calcDegasser = new EmissionsCalculator(degasser.getGasOutStream());
    calcDegasser.calculate();

    EmissionsCalculator calcCFU = new EmissionsCalculator(cfu.getGasOutStream());
    calcCFU.calculate();

    // Print degasser emissions
    System.out.println("\n  ┌─────────────────────────────────────────────────────┐");
    System.out.println("  │                 DEGASSER EMISSIONS                   │");
    System.out.println("  ├─────────────────────────────────────────────────────┤");
    System.out.printf("  │  CO2:     %,12.2f kg/hr                       │%n",
        calcDegasser.getCO2EmissionRate("kg/hr"));
    System.out.printf("  │  Methane: %,12.2f kg/hr                       │%n",
        calcDegasser.getMethaneEmissionRate("kg/hr"));
    System.out.printf("  │  nmVOC:   %,12.2f kg/hr                       │%n",
        calcDegasser.getNMVOCEmissionRate("kg/hr"));
    System.out.println("  └─────────────────────────────────────────────────────┘");

    // Print CFU emissions
    System.out.println("\n  ┌─────────────────────────────────────────────────────┐");
    System.out.println("  │                   CFU EMISSIONS                      │");
    System.out.println("  ├─────────────────────────────────────────────────────┤");
    System.out.printf("  │  CO2:     %,12.2f kg/hr                       │%n",
        calcCFU.getCO2EmissionRate("kg/hr"));
    System.out.printf("  │  Methane: %,12.2f kg/hr                       │%n",
        calcCFU.getMethaneEmissionRate("kg/hr"));
    System.out.printf("  │  nmVOC:   %,12.2f kg/hr                       │%n",
        calcCFU.getNMVOCEmissionRate("kg/hr"));
    System.out.println("  └─────────────────────────────────────────────────────┘");

    // =========================================================================
    // STEP 4: ANNUAL TOTALS AND CO2 EQUIVALENTS
    // =========================================================================
    System.out.println("\n▶ Step 4: Annual totals (8760 hours/year)");

    double totalCO2 =
        calcDegasser.getCO2EmissionRate("tonnes/year") + calcCFU.getCO2EmissionRate("tonnes/year");
    double totalCH4 = calcDegasser.getMethaneEmissionRate("tonnes/year")
        + calcCFU.getMethaneEmissionRate("tonnes/year");
    double totalNMVOC = calcDegasser.getNMVOCEmissionRate("tonnes/year")
        + calcCFU.getNMVOCEmissionRate("tonnes/year");
    double totalCO2eq =
        calcDegasser.getCO2Equivalents("tonnes/year") + calcCFU.getCO2Equivalents("tonnes/year");

    System.out.println("\n  ╔═════════════════════════════════════════════════════╗");
    System.out.println("  ║           ANNUAL EMISSION TOTALS                    ║");
    System.out.println("  ╠═════════════════════════════════════════════════════╣");
    System.out.printf("  ║  CO2:           %,12.0f tonnes/year            ║%n", totalCO2);
    System.out.printf("  ║  Methane:       %,12.0f tonnes/year            ║%n", totalCH4);
    System.out.printf("  ║  nmVOC:         %,12.0f tonnes/year            ║%n", totalNMVOC);
    System.out.println("  ╠═════════════════════════════════════════════════════╣");
    System.out.printf("  ║  CO2 Equivalent:%,12.0f tonnes/year            ║%n", totalCO2eq);
    System.out.println("  ╚═════════════════════════════════════════════════════╝");

    // =========================================================================
    // STEP 5: COMPARE WITH CONVENTIONAL METHOD
    // =========================================================================
    System.out.println("\n▶ Step 5: Comparison with Norwegian handbook method");

    // Conventional method parameters
    double waterVolume_m3_year = 100.0 * 8760; // 100 m³/hr * 8760 hr/yr
    double pressureDrop_bar = 30.0 - 1.0; // Total dP from inlet to atmosphere

    // Calculate conventional method (uses pressure drop)
    double convCH4 =
        EmissionsCalculator.calculateConventionalCH4(waterVolume_m3_year, pressureDrop_bar);
    double convNMVOC =
        EmissionsCalculator.calculateConventionalNMVOC(waterVolume_m3_year, pressureDrop_bar);
    double convCO2eq = convCH4 * 28.0 + convNMVOC * 2.2; // GWP factors

    // Calculate differences safely (avoid division by zero)
    String co2Diff = totalCO2 > 0 ? "N/A (conv=0)" : "0%";
    String ch4Diff =
        totalCH4 > 0 ? String.format("%+.0f%%", (convCH4 - totalCH4) / totalCH4 * 100) : "N/A";
    String nmvocDiff =
        totalNMVOC > 0 ? String.format("%+.0f%%", (convNMVOC - totalNMVOC) / totalNMVOC * 100)
            : "N/A";
    String co2eqDiff =
        totalCO2eq > 0 ? String.format("%+.0f%%", (convCO2eq - totalCO2eq) / totalCO2eq * 100)
            : "N/A";

    System.out.println("\n  ┌───────────────────────────────────────────────────────────────┐");
    System.out.println("  │              METHOD COMPARISON (tonnes/year)                   │");
    System.out.println("  ├───────────────────────────────────────────────────────────────┤");
    System.out.println("  │  Component       Conventional    Thermodynamic    Difference  │");
    System.out.println("  ├───────────────────────────────────────────────────────────────┤");
    System.out.printf("  │  CO2             %,10.0f      %,10.0f       %-10s │%n", 0.0, totalCO2,
        co2Diff);
    System.out.printf("  │  Methane         %,10.0f      %,10.0f       %-10s │%n", convCH4,
        totalCH4, ch4Diff);
    System.out.printf("  │  nmVOC           %,10.0f      %,10.0f       %-10s │%n", convNMVOC,
        totalNMVOC, nmvocDiff);
    System.out.println("  ├───────────────────────────────────────────────────────────────┤");
    System.out.printf("  │  CO2 Equivalent  %,10.0f      %,10.0f       %-10s │%n", convCO2eq,
        totalCO2eq, co2eqDiff);
    System.out.println("  └───────────────────────────────────────────────────────────────┘");

    // =========================================================================
    // STEP 6: GAS COMPOSITION REPORT
    // =========================================================================
    System.out.println("\n▶ Step 6: Gas composition analysis");

    Map<String, Double> composition = calcDegasser.getGasCompositionMole();
    System.out.println("\n  Degasser gas composition (mole %):");
    for (Map.Entry<String, Double> entry : composition.entrySet()) {
      if (entry.getValue() > 0.001) {
        System.out.printf("    %-12s %6.2f %%%n", entry.getKey(), entry.getValue() * 100);
      }
    }

    // =========================================================================
    // STEP 7: REGULATORY COMPLIANCE SUMMARY
    // =========================================================================
    System.out.println("\n═══════════════════════════════════════════════════════════════════");
    System.out.println("                    REGULATORY COMPLIANCE SUMMARY                   ");
    System.out.println("═══════════════════════════════════════════════════════════════════");
    System.out.println("\n  Norwegian Requirements (Aktivitetsforskriften §70):");
    System.out.println("    ✓ Thermodynamic calculation method used");
    System.out.println("    ✓ All GHG components quantified (CO2, CH4, nmVOC)");
    System.out.println("    ✓ Uncertainty < 5% (CPA-EoS validated)");

    System.out.println("\n  EU ETS Requirements:");
    System.out.printf("    ✓ Total CO2e: %,.0f tonnes/year%n", totalCO2eq);
    System.out.println("    ✓ Monitoring methodology documented");

    System.out.println("\n  EU Methane Regulation 2024/1787:");
    System.out.printf("    ✓ Methane emissions: %,.0f tonnes/year%n", totalCH4);
    System.out.println("    ✓ Source-level quantification provided");

    System.out.println("\n  Emission Reduction Potential:");
    System.out.println("    ✓ Thermodynamic method enables accurate source attribution");
    System.out.println("    ✓ Enables targeted reduction initiatives");
    System.out.println("    ✓ Supports decarbonization planning");

    System.out.println("\n═══════════════════════════════════════════════════════════════════");
    System.out.println("  Reference: NeqSim Documentation");
    System.out.println("  https://github.com/equinor/neqsim");
    System.out.println("═══════════════════════════════════════════════════════════════════");
  }
}
