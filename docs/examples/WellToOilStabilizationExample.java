package neqsim.process.equipment.reservoir.examples;

import org.apache.commons.lang3.StringUtils;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.TubingPerformance;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Complete Production System Example: Wellhead to Oil Stabilization.
 *
 * <p>
 * This example demonstrates a complete oil/gas production system from wellhead to stabilized oil,
 * using NeqSim's process simulation capabilities. The system includes:
 * </p>
 *
 * <pre>
 * ┌───────────────┐    ┌──────────────┐    ┌─────────────────┐
 * │   Wellhead    │───►│   Flowline   │───►│  Inlet Choke    │
 * │   Stream      │    │(Beggs-Brill) │    │    Valve        │
 * └───────────────┘    └──────────────┘    └─────────────────┘
 *                                                   │
 *         ┌─────────────────────────────────────────┘
 *         ▼
 * ┌───────────────┐    ┌───────────────┐    ┌───────────────┐
 * │ HP Separator  │───►│ MP Separator  │───►│ LP Separator  │
 * │   (35 bara)   │    │   (10 bara)   │    │   (2 bara)    │
 * └───────────────┘    └───────────────┘    └───────────────┘
 *         │                    │                    │
 *         │           Gas      │           Gas      │
 *         ▼ Gas      Compression              ▼
 *    ┌─────────────────────────────────────────────────────┐
 *    │              Gas Compression Train                  │
 *    │     LP Comp → MP Comp → Export Compressor → 120 bara│
 *    └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Components Demonstrated</h2>
 * <ul>
 * <li>SimpleReservoir for reservoir fluid</li>
 * <li>Stream for wellhead production</li>
 * <li>PipeBeggsAndBrills for flowline</li>
 * <li>ThrottlingValve for inlet choke</li>
 * <li>Three-stage oil stabilization train</li>
 * <li>Multi-stage gas compression</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class WellToOilStabilizationExample {

  /**
   * Run the complete production system example.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println(StringUtils.repeat("=", 70));
    System.out.println("NeqSim Well-to-Oil-Stabilization Production System Example");
    System.out.println(StringUtils.repeat("=", 70));

    // =========================================================================
    // STEP 1: Define Reservoir Fluid
    // =========================================================================
    System.out.println("\n1. Creating reservoir fluid...");

    // Typical black oil composition (simplified)
    SystemInterface reservoirFluid = new SystemSrkEos(373.15, 250.0); // 100°C, 250 bara
    reservoirFluid.addComponent("water", 3.0);
    reservoirFluid.addComponent("nitrogen", 0.5);
    reservoirFluid.addComponent("CO2", 2.0);
    reservoirFluid.addComponent("methane", 62.0);
    reservoirFluid.addComponent("ethane", 7.0);
    reservoirFluid.addComponent("propane", 5.0);
    reservoirFluid.addComponent("i-butane", 1.5);
    reservoirFluid.addComponent("n-butane", 2.5);
    reservoirFluid.addComponent("n-pentane", 2.5);
    reservoirFluid.addComponent("n-hexane", 4.0);
    reservoirFluid.addComponent("n-heptane", 10.0);
    reservoirFluid.setMixingRule(2); // Numeric mixing rule
    reservoirFluid.setMultiPhaseCheck(true);

    System.out.println("   - Black oil with 11 components (including water)");
    System.out.println("   - Reservoir conditions: 100°C, 250 bara");

    // =========================================================================
    // STEP 2: Create Reservoir (Material Balance Tank)
    // =========================================================================
    System.out.println("\n2. Setting up reservoir...");

    SimpleReservoir reservoir = new SimpleReservoir("Main Reservoir");
    reservoir.setReservoirFluid(reservoirFluid, 1e6, 10.0, 10.0);

    System.out.println(
        "   - Reservoir pressure: " + reservoir.getReservoirFluid().getPressure("bara") + " bara");

    // =========================================================================
    // STEP 3: Create Integrated Well System (IPR + VLP)
    // =========================================================================
    System.out.println("\n3. Creating integrated well system (IPR + VLP)...");

    // Create reservoir stream at reservoir conditions
    Stream reservoirStream = new Stream("Reservoir Stream", reservoir.getReservoirFluid());
    reservoirStream.setFlowRate(5000.0, "Sm3/day"); // Initial estimate
    reservoirStream.setTemperature(100.0, "C");
    reservoirStream.setPressure(250.0, "bara");

    // Create WellSystem with integrated IPR and VLP
    WellSystem well = new WellSystem("Producer-1", reservoirStream);

    // Configure IPR model (Vogel for solution gas drive)
    well.setIPRModel(WellSystem.IPRModel.VOGEL);
    well.setVogelParameters(8000.0, 180.0, 250.0); // qTest, pwfTest, pRes

    // Configure VLP (tubing)
    well.setTubingLength(2500.0, "m");
    well.setTubingDiameter(4.0, "in");
    well.setTubingRoughness(2.5e-5);
    well.setInclination(85.0);
    well.setPressureDropCorrelation(TubingPerformance.PressureDropCorrelation.BEGGS_BRILL);
    well.setTemperatureModel(TubingPerformance.TemperatureModel.LINEAR_GRADIENT);
    well.setBottomHoleTemperature(100.0, "C");
    well.setWellheadTemperature(65.0, "C");

    // Set wellhead pressure constraint
    well.setWellheadPressure(80.0, "bara");

    System.out.println("   - IPR Model: Vogel (solution gas drive)");
    System.out.println("   - VLP: Optimized hydrostatic+friction solver, 2500m tubing, 4-inch");
    System.out.println("   - Target WHP: 80 bara");

    // =========================================================================
    // STEP 4: Flowline to Platform
    // =========================================================================
    System.out.println("\n4. Creating flowline to platform...");

    PipeBeggsAndBrills flowline = new PipeBeggsAndBrills("Flowline to Platform");
    flowline.setInletStream(well.getOutletStream());
    flowline.setLength(5000.0);
    flowline.setDiameter(0.2);
    flowline.setPipeWallRoughness(5.0e-5);
    flowline.setAngle(0.0);
    flowline.setNumberOfIncrements(20);

    System.out.println("   - Length: 5 km, Diameter: 8-inch");

    // =========================================================================
    // STEP 5: Inlet Choke Valve
    // =========================================================================
    System.out.println("\n5. Creating inlet choke valve...");

    ThrottlingValve inletChoke = new ThrottlingValve("Inlet Choke", flowline.getOutletStream());
    inletChoke.setOutletPressure(35.0, "bara");

    System.out.println("   - Outlet pressure: 35 bara");

    // =========================================================================
    // STEP 6: Oil Stabilization Train (3-Stage Separation)
    // =========================================================================
    System.out.println("\n6. Creating oil stabilization train...");

    // --- First Stage: HP Separator ---
    Heater preHeater1 = new Heater("Pre-Heater HP", inletChoke.getOutletStream());
    preHeater1.setOutTemperature(70.0, "C");

    Separator hpSeparator = new Separator("HP Separator");
    hpSeparator.setInletStream(preHeater1.getOutletStream());

    System.out.println("   - HP Separator: 35 bara, 70°C");

    // --- Second Stage: MP Separator ---
    ThrottlingValve mpValve =
        new ThrottlingValve("MP Let-down Valve", hpSeparator.getLiquidOutStream());
    mpValve.setOutletPressure(10.0, "bara");

    Heater preHeater2 = new Heater("Pre-Heater MP", mpValve.getOutletStream());
    preHeater2.setOutTemperature(65.0, "C");

    Separator mpSeparator = new Separator("MP Separator");
    mpSeparator.setInletStream(preHeater2.getOutletStream());

    System.out.println("   - MP Separator: 10 bara, 65°C");

    // --- Third Stage: LP Separator (Stabilizer) ---
    ThrottlingValve lpValve =
        new ThrottlingValve("LP Let-down Valve", mpSeparator.getLiquidOutStream());
    lpValve.setOutletPressure(2.0, "bara");

    Heater stabilizer = new Heater("Stabilizer Heater", lpValve.getOutletStream());
    stabilizer.setOutTemperature(80.0, "C");

    Separator lpSeparator = new Separator("LP Separator (Stabilizer)");
    lpSeparator.setInletStream(stabilizer.getOutletStream());

    System.out.println("   - LP Separator: 2 bara, 80°C");

    // =========================================================================
    // STEP 7: Gas Compression (Recovery)
    // =========================================================================
    System.out.println("\n7. Creating gas compression system...");

    // Compress LP gas to MP pressure
    Compressor lpCompressor = new Compressor("LP Compressor", lpSeparator.getGasOutStream());
    lpCompressor.setOutletPressure(10.0, "bara");
    lpCompressor.setPolytropicEfficiency(0.75);

    Cooler lpCooler = new Cooler("LP Compressor Aftercooler", lpCompressor.getOutletStream());
    lpCooler.setOutTemperature(40.0, "C");

    // Mix LP compressed gas with MP gas
    Mixer mpMixer = new Mixer("MP Gas Mixer");
    mpMixer.addStream(mpSeparator.getGasOutStream());
    mpMixer.addStream(lpCooler.getOutletStream());

    // Compress to HP pressure
    Compressor mpCompressor = new Compressor("MP Compressor", mpMixer.getOutletStream());
    mpCompressor.setOutletPressure(35.0, "bara");
    mpCompressor.setPolytropicEfficiency(0.75);

    Cooler mpCooler = new Cooler("MP Compressor Aftercooler", mpCompressor.getOutletStream());
    mpCooler.setOutTemperature(40.0, "C");

    // Mix with HP gas for export
    Mixer hpMixer = new Mixer("HP Gas Mixer");
    hpMixer.addStream(hpSeparator.getGasOutStream());
    hpMixer.addStream(mpCooler.getOutletStream());

    // Final compression for export
    Compressor exportCompressor = new Compressor("Export Compressor", hpMixer.getOutletStream());
    exportCompressor.setOutletPressure(120.0, "bara");
    exportCompressor.setPolytropicEfficiency(0.78);

    Cooler exportCooler = new Cooler("Export Cooler", exportCompressor.getOutletStream());
    exportCooler.setOutTemperature(30.0, "C");

    System.out.println("   - 3-stage compression: LP→MP→HP→Export");
    System.out.println("   - Export pressure: 120 bara");

    // =========================================================================
    // STEP 8: Build and Run Process System
    // =========================================================================
    System.out.println("\n8. Building process system...");

    ProcessSystem process = new ProcessSystem();

    // Well system (solves IPR+VLP internally, produces outlet stream)
    process.add(well);
    process.add(flowline);
    process.add(inletChoke);

    // Separation train
    process.add(preHeater1);
    process.add(hpSeparator);
    process.add(mpValve);
    process.add(preHeater2);
    process.add(mpSeparator);
    process.add(lpValve);
    process.add(stabilizer);
    process.add(lpSeparator);

    // Gas compression
    process.add(lpCompressor);
    process.add(lpCooler);
    process.add(mpMixer);
    process.add(mpCompressor);
    process.add(mpCooler);
    process.add(hpMixer);
    process.add(exportCompressor);
    process.add(exportCooler);

    System.out.println("   - Total equipment: " + process.size() + " units");
    System.out.println("\n9. Running simulation...");
    process.run();

    // =========================================================================
    // STEP 9: Results Summary
    // =========================================================================
    System.out.println("\n" + StringUtils.repeat("=", 70));
    System.out.println("SIMULATION RESULTS");
    System.out.println(StringUtils.repeat("=", 70));

    // Well Performance (IPR + VLP results)
    System.out.println("\n--- WELL PERFORMANCE (IPR + VLP) ---");
    System.out.println(
        String.format("   Reservoir Pressure:    %.1f bara", well.getReservoirPressure("bara")));
    System.out.println(
        String.format("   Bottom-hole Pressure:  %.1f bara", well.getBottomHolePressure("bara")));
    System.out.println(
        String.format("   Wellhead Pressure:     %.1f bara", well.getWellheadPressure("bara")));
    System.out
        .println(String.format("   Drawdown:              %.1f bar", well.getDrawdown("bara")));
    System.out.println(String.format("   Operating Flow Rate:   %.1f Sm³/day",
        well.getOperatingFlowRate("Sm3/day")));

    // Flowline
    System.out.println("\n--- FLOWLINE ---");
    System.out.println(String.format("   Inlet Pressure:        %.1f bara",
        flowline.getInletStream().getPressure("bara")));
    System.out.println(String.format("   Outlet Pressure:       %.1f bara",
        flowline.getOutletStream().getPressure("bara")));
    System.out.println(String.format("   Pressure Drop:         %.1f bar",
        flowline.getInletStream().getPressure("bara")
            - flowline.getOutletStream().getPressure("bara")));

    // Choke
    System.out.println("\n--- INLET CHOKE ---");
    System.out.println(String.format("   Inlet Pressure:        %.1f bara",
        inletChoke.getInletStream().getPressure("bara")));
    System.out.println(String.format("   Outlet Pressure:       %.1f bara",
        inletChoke.getOutletStream().getPressure("bara")));

    // Separation
    System.out.println("\n--- SEPARATION TRAIN ---");
    System.out.println(String.format("   HP Sep Gas:            %.1f Sm³/day",
        hpSeparator.getGasOutStream().getFlowRate("Sm3/day")));
    System.out.println(String.format("   MP Sep Gas:            %.1f Sm³/day",
        mpSeparator.getGasOutStream().getFlowRate("Sm3/day")));
    System.out.println(String.format("   LP Sep Gas:            %.1f Sm³/day",
        lpSeparator.getGasOutStream().getFlowRate("Sm3/day")));

    // Products
    System.out.println("\n--- PRODUCT STREAMS ---");
    System.out.println(String.format("   Stabilized Oil Rate:   %.1f Sm³/day",
        lpSeparator.getLiquidOutStream().getFlowRate("Sm3/day")));
    System.out.println(String.format("   Stabilized Oil Pressure: %.1f bara",
        lpSeparator.getLiquidOutStream().getPressure("bara")));
    System.out.println(String.format("   Export Gas Rate:       %.1f Sm³/day",
        exportCooler.getOutletStream().getFlowRate("Sm3/day")));
    System.out.println(String.format("   Export Gas Pressure:   %.1f bara",
        exportCooler.getOutletStream().getPressure("bara")));

    // Compression power
    System.out.println("\n--- COMPRESSION POWER ---");
    double totalPower =
        lpCompressor.getPower("kW") + mpCompressor.getPower("kW") + exportCompressor.getPower("kW");
    System.out
        .println(String.format("   LP Compressor:         %.0f kW", lpCompressor.getPower("kW")));
    System.out
        .println(String.format("   MP Compressor:         %.0f kW", mpCompressor.getPower("kW")));
    System.out.println(
        String.format("   Export Compressor:     %.0f kW", exportCompressor.getPower("kW")));
    System.out.println(String.format("   Total Power:           %.0f kW", totalPower));

    System.out.println("\n" + StringUtils.repeat("=", 70));
    System.out.println("Simulation completed successfully!");
    System.out.println(StringUtils.repeat("=", 70));
  }
}
