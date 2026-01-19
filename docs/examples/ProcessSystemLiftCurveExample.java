package neqsim.process.fielddevelopment.reservoir;

import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating how to generate Eclipse VFP lift curves for a complete oil and gas
 * separation facility using ProcessSystemLiftCurveGenerator.
 *
 * <p>
 * This example shows:
 * </p>
 * <ul>
 * <li>Setting up a typical three-stage separation process</li>
 * <li>Configuring the lift curve generator with VFP parameters</li>
 * <li>Generating VFP tables for Eclipse reservoir simulation</li>
 * <li>Exporting to Eclipse VFPPROD format and CSV</li>
 * </ul>
 *
 * <h2>Process Configuration</h2>
 * 
 * <pre>
 *                                    [Export Gas]
 *                                         ↑
 *                                   [Compressor]
 *                                         ↑
 *                                   [Gas Mixer]
 *                                    ↗   ↑   ↖
 *                                   /    |    \
 * [Well Stream] → [HP Sep] → [MP Sep] → [LP Sep] → [Stable Oil]
 *                 (35 bar)   (10 bar)   (2.5 bar)
 * </pre>
 *
 * @author ESOL
 * @see ProcessSystemLiftCurveGenerator
 */
public class ProcessSystemLiftCurveExample {

  /**
   * Main entry point for the example.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("=================================================");
    System.out.println("ProcessSystemLiftCurveGenerator Example");
    System.out.println("=================================================\n");

    try {
      runExample();
    } catch (Exception e) {
      System.err.println("Error running example: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Run the complete example.
   */
  public static void runExample() throws Exception {
    // Create base fluid representing typical North Sea oil/gas
    SystemInterface baseFluid = createNorthSeaFluid();

    // Build the separation process
    ProcessSystem separationProcess = createThreeStageSeparation(baseFluid);

    // Run initial simulation
    System.out.println("Running initial process simulation...");
    separationProcess.run();
    printProcessResults(separationProcess);

    // Create lift curve generator
    System.out.println("\n-------------------------------------------------");
    System.out.println("Creating Lift Curve Generator...");
    System.out.println("-------------------------------------------------");

    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    // Configure stream names matching the process
    generator.setInletStreamName("well stream");
    generator.setExportGasStreamName("export gas");
    generator.setExportOilStreamName("stable oil");

    // Set process description for documentation
    generator.setProcessDescription("Three-Stage Offshore Separation - North Sea Platform");

    // Configure VFP table parameters
    configureVfpParameters(generator);

    // Generate VFP table
    System.out.println("\n-------------------------------------------------");
    System.out.println("Generating VFP Table...");
    System.out.println("-------------------------------------------------");

    ProcessSystemLiftCurveGenerator.VfpTableData vfp = generator.generateVfpTable(1, "PLATFORM-A");

    // Print summary
    printVfpSummary(vfp);

    // Export to Eclipse format
    String eclipseKeywords = generator.getEclipseKeywords();
    System.out.println("\n-------------------------------------------------");
    System.out.println("Eclipse VFPPROD Keywords (first 2000 chars):");
    System.out.println("-------------------------------------------------");
    System.out.println(eclipseKeywords.substring(0, Math.min(2000, eclipseKeywords.length())));
    if (eclipseKeywords.length() > 2000) {
      System.out.println("... [truncated]");
    }

    // Export to CSV
    System.out.println("\n-------------------------------------------------");
    System.out.println("CSV Export (first 20 lines):");
    System.out.println("-------------------------------------------------");
    String csv = generator.exportToCsv();
    String[] csvLines = csv.split("\n");
    for (int i = 0; i < Math.min(20, csvLines.length); i++) {
      System.out.println(csvLines[i]);
    }

    // Export to JSON (summary)
    System.out.println("\n-------------------------------------------------");
    System.out.println("JSON Export available via generator.toJson()");
    System.out.println("-------------------------------------------------");

    // Optional: Export to file
    // generator.exportToFile("vfp_platform_a.inc");
    // System.out.println("Exported to vfp_platform_a.inc");

    System.out.println("\n=================================================");
    System.out.println("Example Complete!");
    System.out.println("=================================================");
  }

  /**
   * Create a typical North Sea fluid composition.
   */
  private static SystemInterface createNorthSeaFluid() {
    SystemInterface fluid = new SystemSrkEos(330.0, 35.0);

    // Typical North Sea oil/gas composition
    fluid.addComponent("nitrogen", 0.6);
    fluid.addComponent("CO2", 1.8);
    fluid.addComponent("methane", 62.0);
    fluid.addComponent("ethane", 9.0);
    fluid.addComponent("propane", 6.0);
    fluid.addComponent("i-butane", 1.2);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("i-pentane", 1.3);
    fluid.addComponent("n-pentane", 2.0);
    fluid.addComponent("n-hexane", 3.0);
    fluid.addComponent("n-heptane", 4.5);
    fluid.addComponent("n-octane", 3.0);
    fluid.addComponent("nC10", 2.6);

    fluid.setMixingRule("classic");

    return fluid;
  }

  /**
   * Create three-stage separation process typical for offshore platforms.
   */
  private static ProcessSystem createThreeStageSeparation(SystemInterface baseFluid) {
    ProcessSystem process = new ProcessSystem("Offshore Separation Train");

    // ================================================================
    // INLET SECTION
    // ================================================================
    SystemInterface inlet = baseFluid.clone();
    inlet.setTemperature(330.0);
    inlet.setPressure(35.0);

    Stream wellStream = new Stream("well stream", inlet);
    wellStream.setFlowRate(120000.0, "kg/hr"); // ~12,000 bbl/day equivalent
    wellStream.setTemperature(57.0, "C");
    wellStream.setPressure(35.0, "bara");
    process.add(wellStream);

    // ================================================================
    // HP SEPARATION (35 bara)
    // ================================================================
    ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", wellStream);
    process.add(hpSep);

    // ================================================================
    // MP SEPARATION (10 bara)
    // ================================================================
    ThrottlingValve hpToMpValve = new ThrottlingValve("HP to MP Valve", hpSep.getOilOutStream());
    hpToMpValve.setOutletPressure(10.0);
    process.add(hpToMpValve);

    ThreePhaseSeparator mpSep =
        new ThreePhaseSeparator("MP Separator", hpToMpValve.getOutletStream());
    process.add(mpSep);

    // ================================================================
    // LP SEPARATION (2.5 bara)
    // ================================================================
    ThrottlingValve mpToLpValve = new ThrottlingValve("MP to LP Valve", mpSep.getOilOutStream());
    mpToLpValve.setOutletPressure(2.5);
    process.add(mpToLpValve);

    ThreePhaseSeparator lpSep =
        new ThreePhaseSeparator("LP Separator", mpToLpValve.getOutletStream());
    process.add(lpSep);

    // Export stable oil
    Stream stableOil = new Stream("stable oil", lpSep.getOilOutStream());
    process.add(stableOil);

    // ================================================================
    // GAS COMPRESSION SECTION
    // ================================================================

    // LP gas compressor (2.5 → 10 bar)
    Compressor lpCompressor = new Compressor("LP Compressor", lpSep.getGasOutStream());
    lpCompressor.setOutletPressure(10.0);
    lpCompressor.setPolytropicEfficiency(0.75);
    process.add(lpCompressor);

    Cooler lpCooler = new Cooler("LP Cooler", lpCompressor.getOutletStream());
    lpCooler.setOutTemperature(313.15); // 40°C
    process.add(lpCooler);

    // Mixer for MP and compressed LP gas
    Mixer mpGasMixer = new Mixer("MP Gas Mixer");
    mpGasMixer.addStream(mpSep.getGasOutStream());
    mpGasMixer.addStream(lpCooler.getOutletStream());
    process.add(mpGasMixer);

    // MP gas compressor (10 → 35 bar)
    Compressor mpCompressor = new Compressor("MP Compressor", mpGasMixer.getOutletStream());
    mpCompressor.setOutletPressure(35.0);
    mpCompressor.setPolytropicEfficiency(0.75);
    process.add(mpCompressor);

    Cooler mpCooler = new Cooler("MP Cooler", mpCompressor.getOutletStream());
    mpCooler.setOutTemperature(313.15); // 40°C
    process.add(mpCooler);

    // Mixer for HP and compressed MP gas
    Mixer hpGasMixer = new Mixer("HP Gas Mixer");
    hpGasMixer.addStream(hpSep.getGasOutStream());
    hpGasMixer.addStream(mpCooler.getOutletStream());
    process.add(hpGasMixer);

    // Export compressor (35 → 70 bar)
    Compressor exportCompressor = new Compressor("Export Compressor", hpGasMixer.getOutletStream());
    exportCompressor.setOutletPressure(70.0);
    exportCompressor.setPolytropicEfficiency(0.78);
    process.add(exportCompressor);

    Cooler exportCooler = new Cooler("Export Cooler", exportCompressor.getOutletStream());
    exportCooler.setOutTemperature(303.15); // 30°C
    process.add(exportCooler);

    // Export gas stream
    Stream exportGas = new Stream("export gas", exportCooler.getOutletStream());
    process.add(exportGas);

    return process;
  }

  /**
   * Configure VFP table parameters.
   */
  private static void configureVfpParameters(ProcessSystemLiftCurveGenerator generator) {
    // Flow rate range (oil equivalent, Sm3/day)
    generator.setFlowRateRange(500.0, 8000.0, 8);

    // Tubing head pressure range (bara)
    generator.setThpRange(20.0, 60.0, 5);

    // Water cut range (fraction)
    generator.setWaterCutRange(0.0, 0.6, 4);

    // Gas-oil ratio range (Sm3/Sm3)
    generator.setGorRange(100.0, 400.0, 4);

    // No artificial lift
    generator.setAlmRange(0.0, 0.0, 1);

    // Set flow rate type
    generator.setFlowRateType(ProcessSystemLiftCurveGenerator.FlowRateType.LIQUID);

    // Set datum depth (well depth for hydrostatic calculation)
    generator.setDatumDepth(2500.0); // meters

    // Set inlet temperature
    generator.setInletTemperature(57.0, "C");

    // Set export pressure requirement
    generator.setExportPressure(70.0);

    System.out.println("VFP Parameters configured:");
    System.out.println("  Flow rates:  500-8000 Sm3/d (8 points)");
    System.out.println("  THP:         20-60 bara (5 points)");
    System.out.println("  Water cut:   0-60% (4 points)");
    System.out.println("  GOR:         100-400 Sm3/Sm3 (4 points)");
    System.out.println("  Total points: " + (8 * 5 * 4 * 4) + " simulations");
  }

  /**
   * Print initial process simulation results.
   */
  private static void printProcessResults(ProcessSystem process) {
    System.out.println("\nInitial Process Simulation Results:");
    System.out.println("-----------------------------------");

    Stream exportGas = (Stream) process.getUnit("export gas");
    Stream stableOil = (Stream) process.getUnit("stable oil");

    if (exportGas != null && exportGas.getFluid() != null) {
      System.out.printf("Export Gas: %.1f kg/hr at %.1f bara, %.1f°C%n",
          exportGas.getFluid().getFlowRate("kg/hr"), exportGas.getPressure("bara"),
          exportGas.getTemperature("C"));
    }

    if (stableOil != null && stableOil.getFluid() != null) {
      System.out.printf("Stable Oil: %.1f kg/hr at %.1f bara, %.1f°C%n",
          stableOil.getFluid().getFlowRate("kg/hr"), stableOil.getPressure("bara"),
          stableOil.getTemperature("C"));
    }

    // Print compressor power
    double totalPower = 0;
    String[] compressors = {"LP Compressor", "MP Compressor", "Export Compressor"};
    for (String name : compressors) {
      Compressor comp = (Compressor) process.getUnit(name);
      if (comp != null) {
        double power = comp.getPower() / 1000.0; // kW
        System.out.printf("%s: %.1f kW%n", name, power);
        totalPower += power;
      }
    }
    System.out.printf("Total Compression Power: %.1f kW%n", totalPower);
  }

  /**
   * Print VFP table summary.
   */
  private static void printVfpSummary(ProcessSystemLiftCurveGenerator.VfpTableData vfp) {
    System.out.println("\nVFP Table Summary:");
    System.out.println("------------------");
    System.out.printf("Table Number: %d%n", vfp.getTableNumber());
    System.out.printf("Well Name: %s%n", vfp.getWellName());
    System.out.printf("Datum Depth: %.1f m%n", vfp.getDatumDepth());
    System.out.printf("Flow Rate Type: %s%n", vfp.getFlowRateType());
    System.out.printf("Flow Rates: %d values (%.0f - %.0f Sm3/d)%n", vfp.getFlowRates().length,
        vfp.getFlowRates()[0], vfp.getFlowRates()[vfp.getFlowRates().length - 1]);
    System.out.printf("THP Values: %d values (%.0f - %.0f bara)%n", vfp.getThpValues().length,
        vfp.getThpValues()[0], vfp.getThpValues()[vfp.getThpValues().length - 1]);
    System.out.printf("WCT Values: %d values (%.2f - %.2f)%n", vfp.getWctValues().length,
        vfp.getWctValues()[0], vfp.getWctValues()[vfp.getWctValues().length - 1]);
    System.out.printf("GOR Values: %d values (%.0f - %.0f Sm3/Sm3)%n", vfp.getGorValues().length,
        vfp.getGorValues()[0], vfp.getGorValues()[vfp.getGorValues().length - 1]);

    // Sample BHP values
    System.out.println("\nSample BHP Values (bara):");
    System.out.println("Flow(Sm3/d)  THP(bara)  WCT    GOR    BHP(bara)");
    System.out.println("----------------------------------------------");

    double[] flowRates = vfp.getFlowRates();
    double[] thpValues = vfp.getThpValues();
    double[] wctValues = vfp.getWctValues();
    double[] gorValues = vfp.getGorValues();
    double[][][][][] bhpValues = vfp.getBhpValues();

    // Print corners of the table
    int[] flowIdx = {0, flowRates.length - 1};
    int[] thpIdx = {0, thpValues.length - 1};

    for (int iF : flowIdx) {
      for (int iT : thpIdx) {
        System.out.printf("%10.0f  %9.1f  %.2f  %.0f    %.1f%n", flowRates[iF], thpValues[iT],
            wctValues[0], gorValues[0], bhpValues[iF][iT][0][0][0]);
      }
    }
  }
}
