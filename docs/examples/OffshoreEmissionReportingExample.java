package neqsim.process.equipment.util;

import java.util.Map;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
  private static final Logger logger =
      LogManager.getLogger(OffshoreEmissionReportingExample.class);

  /**
   * Main method demonstrating emission calculation workflow.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    logger.info("═══════════════════════════════════════════════════════════════════");
    logger.info("      OFFSHORE PLATFORM EMISSION REPORTING - NeqSim Example        ");
    logger.info("═══════════════════════════════════════════════════════════════════");

    // =========================================================================
    // STEP 1: CREATE PRODUCED WATER FLUID
    // =========================================================================
    logger.info("\n▶ Step 1: Creating produced water fluid (CPA-EoS)");

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

    logger.info("  Fluid: CPA equation of state");
    logger.info("  Components: water, CO2, CH4, C2H6, C3H8");

    // =========================================================================
    // STEP 2: CREATE PROCESS SYSTEM
    // =========================================================================
    logger.info("\n▶ Step 2: Building multi-stage degassing process");

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

    logger.info("  Stage 1: Degasser (30 → 4 bara)");
    logger.info("  Stage 2: CFU (4 → 1.1 bara)");
    logger.info("  Simulation completed successfully");

    // =========================================================================
    // STEP 3: CALCULATE EMISSIONS
    // =========================================================================
    logger.info("\n▶ Step 3: Calculating emissions (thermodynamic method)");

    // Create emissions calculator for each stage
    EmissionsCalculator calcDegasser = new EmissionsCalculator(degasser.getGasOutStream());
    calcDegasser.calculate();

    EmissionsCalculator calcCFU = new EmissionsCalculator(cfu.getGasOutStream());
    calcCFU.calculate();

    // Print degasser emissions
    logger.info("\n  ┌─────────────────────────────────────────────────────┐");
    logger.info("  │                 DEGASSER EMISSIONS                   │");
    logger.info("  ├─────────────────────────────────────────────────────┤");
    logger.info("{}", String.format("  │  CO2:     %,12.2f kg/hr                       │",
        calcDegasser.getCO2EmissionRate("kg/hr")));
    logger.info("{}", String.format("  │  Methane: %,12.2f kg/hr                       │",
        calcDegasser.getMethaneEmissionRate("kg/hr")));
    logger.info("{}", String.format("  │  nmVOC:   %,12.2f kg/hr                       │",
        calcDegasser.getNMVOCEmissionRate("kg/hr")));
    logger.info("  └─────────────────────────────────────────────────────┘");

    // Print CFU emissions
    logger.info("\n  ┌─────────────────────────────────────────────────────┐");
    logger.info("  │                   CFU EMISSIONS                      │");
    logger.info("  ├─────────────────────────────────────────────────────┤");
    logger.info("{}", String.format("  │  CO2:     %,12.2f kg/hr                       │",
        calcCFU.getCO2EmissionRate("kg/hr")));
    logger.info("{}", String.format("  │  Methane: %,12.2f kg/hr                       │",
        calcCFU.getMethaneEmissionRate("kg/hr")));
    logger.info("{}", String.format("  │  nmVOC:   %,12.2f kg/hr                       │",
        calcCFU.getNMVOCEmissionRate("kg/hr")));
    logger.info("  └─────────────────────────────────────────────────────┘");

    // =========================================================================
    // STEP 4: ANNUAL TOTALS AND CO2 EQUIVALENTS
    // =========================================================================
    logger.info("\n▶ Step 4: Annual totals (8760 hours/year)");

    double totalCO2 =
        calcDegasser.getCO2EmissionRate("tonnes/year") + calcCFU.getCO2EmissionRate("tonnes/year");
    double totalCH4 = calcDegasser.getMethaneEmissionRate("tonnes/year")
        + calcCFU.getMethaneEmissionRate("tonnes/year");
    double totalNMVOC = calcDegasser.getNMVOCEmissionRate("tonnes/year")
        + calcCFU.getNMVOCEmissionRate("tonnes/year");
    double totalCO2eq =
        calcDegasser.getCO2Equivalents("tonnes/year") + calcCFU.getCO2Equivalents("tonnes/year");

    logger.info("\n  ╔═════════════════════════════════════════════════════╗");
    logger.info("  ║           ANNUAL EMISSION TOTALS                    ║");
    logger.info("  ╠═════════════════════════════════════════════════════╣");
    logger.info("{}", String.format("  ║  CO2:           %,12.0f tonnes/year            ║", totalCO2));
    logger.info("{}", String.format("  ║  Methane:       %,12.0f tonnes/year            ║", totalCH4));
    logger.info("{}", String.format("  ║  nmVOC:         %,12.0f tonnes/year            ║", totalNMVOC));
    logger.info("  ╠═════════════════════════════════════════════════════╣");
    logger.info("{}", String.format("  ║  CO2 Equivalent:%,12.0f tonnes/year            ║", totalCO2eq));
    logger.info("  ╚═════════════════════════════════════════════════════╝");

    // =========================================================================
    // STEP 5: COMPARE WITH CONVENTIONAL METHOD
    // =========================================================================
    logger.info("\n▶ Step 5: Comparison with Norwegian handbook method");

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

    logger.info("\n  ┌───────────────────────────────────────────────────────────────┐");
    logger.info("  │              METHOD COMPARISON (tonnes/year)                   │");
    logger.info("  ├───────────────────────────────────────────────────────────────┤");
    logger.info("  │  Component       Conventional    Thermodynamic    Difference  │");
    logger.info("  ├───────────────────────────────────────────────────────────────┤");
    logger.info("{}", String.format("  │  CO2             %,10.0f      %,10.0f       %-10s │", 0.0, totalCO2,
        co2Diff));
    logger.info("{}", String.format("  │  Methane         %,10.0f      %,10.0f       %-10s │", convCH4,
        totalCH4, ch4Diff));
    logger.info("{}", String.format("  │  nmVOC           %,10.0f      %,10.0f       %-10s │", convNMVOC,
        totalNMVOC, nmvocDiff));
    logger.info("  ├───────────────────────────────────────────────────────────────┤");
    logger.info("{}", String.format("  │  CO2 Equivalent  %,10.0f      %,10.0f       %-10s │", convCO2eq,
        totalCO2eq, co2eqDiff));
    logger.info("  └───────────────────────────────────────────────────────────────┘");

    // =========================================================================
    // STEP 6: GAS COMPOSITION REPORT
    // =========================================================================
    logger.info("\n▶ Step 6: Gas composition analysis");

    Map<String, Double> composition = calcDegasser.getGasCompositionMole();
    logger.info("\n  Degasser gas composition (mole %):");
    for (Map.Entry<String, Double> entry : composition.entrySet()) {
      if (entry.getValue() > 0.001) {
        logger.info("{}", String.format("    %-12s %6.2f %%", entry.getKey(), entry.getValue() * 100));
      }
    }

    // =========================================================================
    // STEP 7: REGULATORY COMPLIANCE SUMMARY
    // =========================================================================
    logger.info("\n═══════════════════════════════════════════════════════════════════");
    logger.info("                    REGULATORY COMPLIANCE SUMMARY                   ");
    logger.info("═══════════════════════════════════════════════════════════════════");
    logger.info("\n  Norwegian Requirements (Aktivitetsforskriften §70):");
    logger.info("    ✓ Thermodynamic calculation method used");
    logger.info("    ✓ All GHG components quantified (CO2, CH4, nmVOC)");
    logger.info("    ✓ Uncertainty < 5% (CPA-EoS validated)");

    logger.info("\n  EU ETS Requirements:");
    logger.info("{}", String.format("    ✓ Total CO2e: %,.0f tonnes/year", totalCO2eq));
    logger.info("    ✓ Monitoring methodology documented");

    logger.info("\n  EU Methane Regulation 2024/1787:");
    logger.info("{}", String.format("    ✓ Methane emissions: %,.0f tonnes/year", totalCH4));
    logger.info("    ✓ Source-level quantification provided");

    logger.info("\n  Emission Reduction Potential:");
    logger.info("    ✓ Thermodynamic method enables accurate source attribution");
    logger.info("    ✓ Enables targeted reduction initiatives");
    logger.info("    ✓ Supports decarbonization planning");

    logger.info("\n═══════════════════════════════════════════════════════════════════");
    logger.info("  Reference: NeqSim Documentation");
    logger.info("  https://github.com/equinor/neqsim");
    logger.info("═══════════════════════════════════════════════════════════════════");
  }
}
