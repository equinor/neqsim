package neqsim.process.equipment.reservoir;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test for the complete production system from reservoir to oil stabilization.
 */
public class WellToOilStabilizationExampleTest {
  private static final Logger logger = LogManager.getLogger(WellToOilStabilizationExampleTest.class);

  /** Helper method for Java 8 compatibility (String.repeat was added in Java 11). */
  private static String repeat(String str, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(str);
    }
    return sb.toString();
  }

  @Test
  void testWellToOilStabilization() {
    logger.info(repeat("=", 70));
    logger.info("NeqSim Well-to-Oil-Stabilization Production System Example");
    logger.info(repeat("=", 70));

    // =========================================================================
    // STEP 1: Define Reservoir Fluid
    // =========================================================================
    logger.info("\n1. Creating reservoir fluid...");

    // Typical black oil composition (simplified for demo)
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
    reservoirFluid.setMixingRule(2); // Numeric mixing rule for compatibility
    reservoirFluid.setMultiPhaseCheck(true);

    logger.info("   - Black oil with 11 components (including water)");
    logger.info("   - Reservoir conditions: 100°C, 250 bara");

    // =========================================================================
    // STEP 2: Create Reservoir (Material Balance Tank)
    // =========================================================================
    logger.info("\n2. Setting up reservoir...");

    SimpleReservoir reservoir = new SimpleReservoir("Main Reservoir");
    reservoir.setReservoirFluid(reservoirFluid, 1e6, 10.0, 10.0);

    logger.info("   - Reservoir pressure: " + reservoir.getReservoirFluid().getPressure("bara") + " bara");

    // =========================================================================
    // STEP 3: Create Integrated Well System (IPR + VLP)
    // =========================================================================
    logger.info("\n3. Creating integrated well system (IPR + VLP)...");

    // Create reservoir stream at reservoir conditions
    Stream reservoirStream = new Stream("Reservoir Stream", reservoir.getReservoirFluid());
    reservoirStream.setFlowRate(5000.0, "Sm3/day");
    reservoirStream.setTemperature(100.0, "C");
    reservoirStream.setPressure(250.0, "bara");

    // Create WellSystem with integrated IPR and VLP (now optimized for speed)
    WellSystem well = new WellSystem("Producer-1", reservoirStream);

    // Configure IPR model (Vogel for solution gas drive)
    well.setIPRModel(WellSystem.IPRModel.VOGEL);
    well.setVogelParameters(8000.0, 180.0, 250.0); // qMax, testPwf, Pr

    // Configure VLP (tubing)
    well.setTubingLength(2500.0, "m");
    well.setTubingDiameter(4.0, "in");
    well.setTubingRoughness(2.5e-5);
    well.setInclination(85.0);
    well.setBottomHoleTemperature(100.0, "C");
    well.setWellheadTemperature(65.0, "C");
    well.setWellheadPressure(80.0, "bara");

    logger.info("   - IPR Model: Vogel (solution gas drive)");
    logger.info("   - VLP: Simplified correlation, 2500m tubing, 4-inch");
    logger.info("   - Target WHP: 80 bara");

    // =========================================================================
    // STEP 4: Flowline to Platform
    // =========================================================================
    logger.info("\n4. Creating flowline to platform...");

    PipeBeggsAndBrills flowline = new PipeBeggsAndBrills("Flowline to Platform");
    flowline.setInletStream(well.getOutletStream());
    flowline.setLength(5000.0);
    flowline.setDiameter(0.2);
    flowline.setPipeWallRoughness(5.0e-5);
    flowline.setAngle(0.0);
    flowline.setNumberOfIncrements(5); // Reduced for faster testing

    logger.info("   - Length: 5 km, Diameter: 8-inch");

    // =========================================================================
    // STEP 5: Inlet Choke Valve
    // =========================================================================
    logger.info("\n5. Creating inlet choke valve...");

    ThrottlingValve inletChoke = new ThrottlingValve("Inlet Choke", flowline.getOutletStream());
    inletChoke.setOutletPressure(35.0, "bara");

    logger.info("   - Outlet pressure: 35 bara");

    // =========================================================================
    // STEP 6: Oil Stabilization Train (3-Stage Separation)
    // =========================================================================
    logger.info("\n6. Creating oil stabilization train...");

    // HP Separator
    Heater preHeater1 = new Heater("Pre-Heater HP", inletChoke.getOutletStream());
    preHeater1.setOutTemperature(70.0, "C");

    Separator hpSeparator = new Separator("HP Separator");
    hpSeparator.setInletStream(preHeater1.getOutletStream());

    logger.info("   - HP Separator: 35 bara, 70°C");

    // MP Separator
    ThrottlingValve mpValve = new ThrottlingValve("MP Let-down Valve", hpSeparator.getLiquidOutStream());
    mpValve.setOutletPressure(10.0, "bara");

    Heater preHeater2 = new Heater("Pre-Heater MP", mpValve.getOutletStream());
    preHeater2.setOutTemperature(65.0, "C");

    Separator mpSeparator = new Separator("MP Separator");
    mpSeparator.setInletStream(preHeater2.getOutletStream());

    logger.info("   - MP Separator: 10 bara, 65°C");

    // LP Separator (Stabilizer)
    ThrottlingValve lpValve = new ThrottlingValve("LP Let-down Valve", mpSeparator.getLiquidOutStream());
    lpValve.setOutletPressure(2.0, "bara");

    Heater stabilizer = new Heater("Stabilizer Heater", lpValve.getOutletStream());
    stabilizer.setOutTemperature(80.0, "C");

    Separator lpSeparator = new Separator("LP Separator (Stabilizer)");
    lpSeparator.setInletStream(stabilizer.getOutletStream());

    logger.info("   - LP Separator: 2 bara, 80°C");

    // =========================================================================
    // STEP 7: Gas Compression (Recovery)
    // =========================================================================
    logger.info("\n7. Creating gas compression system...");

    Compressor lpCompressor = new Compressor("LP Compressor", lpSeparator.getGasOutStream());
    lpCompressor.setOutletPressure(10.0, "bara");
    lpCompressor.setPolytropicEfficiency(0.75);

    Cooler lpCooler = new Cooler("LP Compressor Aftercooler", lpCompressor.getOutletStream());
    lpCooler.setOutTemperature(40.0, "C");

    Mixer mpMixer = new Mixer("MP Gas Mixer");
    mpMixer.addStream(mpSeparator.getGasOutStream());
    mpMixer.addStream(lpCooler.getOutletStream());

    Compressor mpCompressor = new Compressor("MP Compressor", mpMixer.getOutletStream());
    mpCompressor.setOutletPressure(35.0, "bara");
    mpCompressor.setPolytropicEfficiency(0.75);

    Cooler mpCooler = new Cooler("MP Compressor Aftercooler", mpCompressor.getOutletStream());
    mpCooler.setOutTemperature(40.0, "C");

    Mixer hpMixer = new Mixer("HP Gas Mixer");
    hpMixer.addStream(hpSeparator.getGasOutStream());
    hpMixer.addStream(mpCooler.getOutletStream());

    Compressor exportCompressor = new Compressor("Export Compressor", hpMixer.getOutletStream());
    exportCompressor.setOutletPressure(120.0, "bara");
    exportCompressor.setPolytropicEfficiency(0.78);

    Cooler exportCooler = new Cooler("Export Cooler", exportCompressor.getOutletStream());
    exportCooler.setOutTemperature(30.0, "C");

    logger.info("   - 3-stage compression: LP→MP→HP→Export");
    logger.info("   - Export pressure: 120 bara");

    // =========================================================================
    // STEP 8: Build and Run Process System
    // =========================================================================
    logger.info("\n8. Building process system...");

    ProcessSystem process = new ProcessSystem();

    // Production and flowline
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

    logger.info("   - Total equipment: " + process.size() + " units");
    logger.info("\n9. Running simulation...");
    process.run();

    // =========================================================================
    // STEP 9: Results Summary
    // =========================================================================
    logger.info("\n" + repeat("=", 70));
    logger.info("SIMULATION RESULTS");
    logger.info(repeat("=", 70));

    // Wellhead (from WellSystem with IPR+VLP)
    logger.info("\n--- WELLHEAD (IPR+VLP Solution) ---");
    logger.info(String.format("   Wellhead Pressure:     %.1f bara", well.getOutletStream().getPressure("bara")));
    logger.info(String.format("   Wellhead Temperature:  %.1f °C", well.getOutletStream().getTemperature("C")));
    logger.info(String.format("   Operating Flow Rate:   %.1f Sm³/day", well.getOperatingFlowRate("Sm3/day")));
    logger.info(String.format("   Bottom Hole Pressure:  %.1f bara", well.getBottomHolePressure("bara")));
    System.out.println(String.format("   Drawdown:              %.1f bar", well.getDrawdown("bar")));

    // Flowline
    logger.info("\n--- FLOWLINE ---");
    logger.info(String.format("   Inlet Pressure:        %.1f bara", flowline.getInletStream().getPressure("bara")));
    logger.info(String.format("   Outlet Pressure:       %.1f bara", flowline.getOutletStream().getPressure("bara")));
    logger.info(String.format("   Pressure Drop:         %.1f bar",
	flowline.getInletStream().getPressure("bara") - flowline.getOutletStream().getPressure("bara")));

    // Choke
    logger.info("\n--- INLET CHOKE ---");
    logger.info(String.format("   Inlet Pressure:        %.1f bara", inletChoke.getInletStream().getPressure("bara")));
    logger.info(String.format("   Outlet Pressure:       %.1f bara", inletChoke.getOutletStream().getPressure("bara")));

    // Separation
    logger.info("\n--- SEPARATION TRAIN ---");
    logger.info(
	String.format("   HP Sep Gas:            %.1f Sm³/day", hpSeparator.getGasOutStream().getFlowRate("Sm3/day")));
    logger.info(
	String.format("   MP Sep Gas:            %.1f Sm³/day", mpSeparator.getGasOutStream().getFlowRate("Sm3/day")));
    logger.info(
	String.format("   LP Sep Gas:            %.1f Sm³/day", lpSeparator.getGasOutStream().getFlowRate("Sm3/day")));

    // Products
    logger.info("\n--- PRODUCT STREAMS ---");
    logger.info(String.format("   Stabilized Oil Rate:   %.1f Sm³/day",
	lpSeparator.getLiquidOutStream().getFlowRate("Sm3/day")));
    logger.info(
	String.format("   Stabilized Oil Pressure: %.1f bara", lpSeparator.getLiquidOutStream().getPressure("bara")));
    logger.info(
	String.format("   Export Gas Rate:       %.1f Sm³/day", exportCooler.getOutletStream().getFlowRate("Sm3/day")));
    logger
	.info(String.format("   Export Gas Pressure:   %.1f bara", exportCooler.getOutletStream().getPressure("bara")));

    // Compression power
    logger.info("\n--- COMPRESSION POWER ---");
    double totalPower = lpCompressor.getPower("kW") + mpCompressor.getPower("kW") + exportCompressor.getPower("kW");
    System.out.println(String.format("   LP Compressor:         %.0f kW", lpCompressor.getPower("kW")));
    System.out.println(String.format("   MP Compressor:         %.0f kW", mpCompressor.getPower("kW")));
    logger.info(String.format("   Export Compressor:     %.0f kW", exportCompressor.getPower("kW")));
    logger.info(String.format("   Total Power:           %.0f kW", totalPower));

    logger.info("\n" + repeat("=", 70));
    logger.info("Simulation completed successfully!");
    logger.info(repeat("=", 70));
  }
}
