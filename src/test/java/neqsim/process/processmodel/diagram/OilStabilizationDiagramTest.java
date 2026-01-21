package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class demonstrating a complete oil stabilization process with recycle streams.
 * 
 * <p>
 * This test generates a process flow diagram for a 4-stage oil stabilization train with:
 * </p>
 * <ul>
 * <li>HP/MP/LP/Atmospheric separation stages</li>
 * <li>Multi-stage gas compression with intercooling</li>
 * <li>Dew point control with turbo-expander</li>
 * <li>Three recycle streams returning condensate to oil separation</li>
 * </ul>
 * 
 * <p>
 * The process is based on realistic offshore oil/gas processing with integrated condensate
 * recovery.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class OilStabilizationDiagramTest {
  /*
   * Test generating a full oil stabilization process diagram with HYSYS style.**<p>* Creates a
   * comprehensive process including:*</p>*<ul>*<li>HP well stream at 62 bara</li>*<li>LP well
   * stream at 20 bara</li>*<li>4- stage separation train</li>*<li>3- stage gas
   * compression</li>*<li> Dew point control unit</li>*<li>Turbo-expander</li>*<li>3 recycle loops
   * for condensate recovery</li>*</ul>
   */

  @Test
  void testOilStabilizationWithRecycles() {
    // =========================================================================
    // Create multi-component reservoir fluid
    // =========================================================================
    SystemInterface wellFluid = new SystemSrkEos(340.15, 62.0); // 67°C, 62 bara
    wellFluid.addComponent("nitrogen", 1.5);
    wellFluid.addComponent("CO2", 3.0);
    wellFluid.addComponent("methane", 65.0);
    wellFluid.addComponent("ethane", 8.0);
    wellFluid.addComponent("propane", 5.0);
    wellFluid.addComponent("i-butane", 1.5);
    wellFluid.addComponent("n-butane", 2.5);
    wellFluid.addComponent("i-pentane", 1.0);
    wellFluid.addComponent("n-pentane", 1.5);
    wellFluid.addComponent("n-hexane", 2.0);
    wellFluid.addComponent("n-heptane", 4.0);
    wellFluid.addComponent("n-octane", 3.0);
    wellFluid.addComponent("water", 2.0);
    wellFluid.setMixingRule("classic");
    wellFluid.setMultiPhaseCheck(true);

    SystemInterface lpWellFluid = wellFluid.clone();

    // =========================================================================
    // Create Process System
    // =========================================================================
    ProcessSystem operations = new ProcessSystem();
    operations.setName("Oil Stabilization with Recycles");

    // =========================================================================
    // Feed Streams
    // =========================================================================
    Stream wellStreamHP = new Stream("HP Well Stream", wellFluid);
    wellStreamHP.setFlowRate(50000.0, "kg/hr");
    wellStreamHP.setTemperature(67.0, "C");
    wellStreamHP.setPressure(62.0, "bara");
    operations.add(wellStreamHP);

    Stream lpWellStream = new Stream("LP Well Stream", lpWellFluid);
    lpWellStream.setFlowRate(20000.0, "kg/hr");
    lpWellStream.setTemperature(80.0, "C");
    lpWellStream.setPressure(20.0, "bara");
    operations.add(lpWellStream);

    // =========================================================================
    // First Stage Separation (62 bara)
    // =========================================================================
    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("1st Stage Separator", wellStreamHP);
    operations.add(firstStageSeparator);

    ThrottlingValve oilValve1 =
        new ThrottlingValve("Oil Letdown V-101", firstStageSeparator.getOilOutStream());
    oilValve1.setOutletPressure(20.0, "bara");
    operations.add(oilValve1);

    // Recycle stream placeholder for HP condensate return
    Stream oilFirstStageRecycle = wellStreamHP.clone();
    oilFirstStageRecycle.setName("HP Recycle");
    oilFirstStageRecycle.setFlowRate(10.0, "kg/hr"); // Initial small flow
    oilFirstStageRecycle.setPressure(20.0, "bara");
    oilFirstStageRecycle.setTemperature(30.0, "C");
    operations.add(oilFirstStageRecycle);

    Mixer firstStageMixer = new Mixer("1st Stage Mixer");
    firstStageMixer.addStream(oilValve1.getOutletStream());
    firstStageMixer.addStream(oilFirstStageRecycle);
    operations.add(firstStageMixer);

    // =========================================================================
    // Second Stage Separation (20 bara)
    // =========================================================================
    Heater oilHeater1 = new Heater("Oil Heater E-101", firstStageMixer.getOutletStream());
    oilHeater1.setOutTemperature(80.0, "C");
    operations.add(oilHeater1);

    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("2nd Stage Separator", oilHeater1.getOutletStream());
    secondStageSeparator.addStream(lpWellStream);
    operations.add(secondStageSeparator);

    ThrottlingValve oilValve2 =
        new ThrottlingValve("Oil Letdown V-102", secondStageSeparator.getOilOutStream());
    oilValve2.setOutletPressure(7.0, "bara");
    operations.add(oilValve2);

    // Recycle stream placeholder for MP condensate return
    Stream oilSecondStageRecycle = wellStreamHP.clone();
    oilSecondStageRecycle.setName("MP Recycle");
    oilSecondStageRecycle.setFlowRate(10.0, "kg/hr");
    oilSecondStageRecycle.setPressure(7.0, "bara");
    oilSecondStageRecycle.setTemperature(30.0, "C");
    operations.add(oilSecondStageRecycle);

    Mixer secondStageMixer = new Mixer("2nd Stage Mixer");
    secondStageMixer.addStream(oilValve2.getOutletStream());
    secondStageMixer.addStream(oilSecondStageRecycle);
    operations.add(secondStageMixer);

    // =========================================================================
    // Third Stage Separation (7 bara)
    // =========================================================================
    ThreePhaseSeparator thirdStageSeparator =
        new ThreePhaseSeparator("3rd Stage Separator", secondStageMixer.getOutletStream());
    operations.add(thirdStageSeparator);

    ThrottlingValve oilValve3 =
        new ThrottlingValve("Oil Letdown V-103", thirdStageSeparator.getOilOutStream());
    oilValve3.setOutletPressure(3.0, "bara");
    operations.add(oilValve3);

    // Recycle stream placeholder for LP condensate return
    Stream oilThirdStageRecycle = wellStreamHP.clone();
    oilThirdStageRecycle.setName("LP Recycle");
    oilThirdStageRecycle.setFlowRate(10.0, "kg/hr");
    oilThirdStageRecycle.setPressure(3.0, "bara");
    oilThirdStageRecycle.setTemperature(30.0, "C");
    operations.add(oilThirdStageRecycle);

    Mixer thirdStageMixer = new Mixer("3rd Stage Mixer");
    thirdStageMixer.addStream(oilValve3.getOutletStream());
    thirdStageMixer.addStream(oilThirdStageRecycle);
    operations.add(thirdStageMixer);

    // =========================================================================
    // Fourth Stage Separation / Stabilizer (3 bara)
    // =========================================================================
    ThreePhaseSeparator fourthStageSeparator =
        new ThreePhaseSeparator("Stabilizer", thirdStageMixer.getOutletStream());
    operations.add(fourthStageSeparator);

    // =========================================================================
    // First Stage Compression (3 bara → 7 bara)
    // =========================================================================
    Cooler lpGasCooler = new Cooler("LP Gas Cooler E-201", fourthStageSeparator.getGasOutStream());
    lpGasCooler.setOutTemperature(29.0, "C");
    operations.add(lpGasCooler);

    Separator lpScrubber = new Separator("LP Scrubber", lpGasCooler.getOutletStream());
    operations.add(lpScrubber);

    Compressor lpCompressor = new Compressor("LP Compressor K-201", lpScrubber.getGasOutStream());
    lpCompressor.setUsePolytropicCalc(true);
    lpCompressor.setPolytropicEfficiency(0.80);
    lpCompressor.setOutletPressure(7.0, "bara");
    operations.add(lpCompressor);

    // Mix with 3rd stage gas
    Mixer lpGasMixer = new Mixer("LP Gas Mixer");
    lpGasMixer.addStream(lpCompressor.getOutletStream());
    lpGasMixer.addStream(thirdStageSeparator.getGasOutStream());
    operations.add(lpGasMixer);

    // =========================================================================
    // Second Stage Compression (7 bara → 20 bara)
    // =========================================================================
    Cooler mpGasCooler1 = new Cooler("MP Gas Cooler E-202", lpGasMixer.getOutletStream());
    mpGasCooler1.setOutTemperature(29.0, "C");
    operations.add(mpGasCooler1);

    Separator mpScrubber1 = new Separator("MP Scrubber 1", mpGasCooler1.getOutletStream());
    operations.add(mpScrubber1);

    Compressor mpCompressor = new Compressor("MP Compressor K-202", mpScrubber1.getGasOutStream());
    mpCompressor.setUsePolytropicCalc(true);
    mpCompressor.setPolytropicEfficiency(0.80);
    mpCompressor.setOutletPressure(20.0, "bara");
    operations.add(mpCompressor);

    // Mix with 2nd stage gas
    Mixer mpGasMixer = new Mixer("MP Gas Mixer");
    mpGasMixer.addStream(mpCompressor.getOutletStream());
    mpGasMixer.addStream(secondStageSeparator.getGasOutStream());
    operations.add(mpGasMixer);

    // =========================================================================
    // Third Stage Compression (20 bara → 62 bara)
    // =========================================================================
    Cooler hpGasCooler = new Cooler("HP Gas Cooler E-203", mpGasMixer.getOutletStream());
    hpGasCooler.setOutTemperature(29.0, "C");
    operations.add(hpGasCooler);

    Separator hpScrubber = new Separator("HP Scrubber", hpGasCooler.getOutletStream());
    operations.add(hpScrubber);

    Compressor hpCompressor = new Compressor("HP Compressor K-203", hpScrubber.getGasOutStream());
    hpCompressor.setUsePolytropicCalc(true);
    hpCompressor.setPolytropicEfficiency(0.80);
    hpCompressor.setOutletPressure(62.0, "bara");
    operations.add(hpCompressor);

    // Mix with 1st stage gas for export
    Mixer richGasMixer = new Mixer("Rich Gas Mixer");
    richGasMixer.addStream(hpCompressor.getOutletStream());
    richGasMixer.addStream(firstStageSeparator.getGasOutStream());
    operations.add(richGasMixer);

    // =========================================================================
    // Dew Point Control Unit
    // =========================================================================
    Cooler dewPointCooler1 = new Cooler("Dew Point Cooler E-301", richGasMixer.getOutletStream());
    dewPointCooler1.setOutTemperature(29.0, "C");
    operations.add(dewPointCooler1);

    Separator dewPointScrubber1 =
        new Separator("Dew Point Scrubber 1", dewPointCooler1.getOutletStream());
    operations.add(dewPointScrubber1);

    // Chilling for deeper dew point control
    Cooler dewPointCooler2 =
        new Cooler("Dew Point Chiller E-302", dewPointScrubber1.getGasOutStream());
    dewPointCooler2.setOutTemperature(-15.0, "C");
    dewPointCooler2.setOutPressure(60.0, "bara");
    operations.add(dewPointCooler2);

    Separator dewPointScrubber2 =
        new Separator("Dew Point Scrubber 2", dewPointCooler2.getOutletStream());
    operations.add(dewPointScrubber2);

    // =========================================================================
    // Turbo-Expander for sales gas
    // =========================================================================
    Expander turboExpander = new Expander("Turbo-Expander", dewPointScrubber2.getGasOutStream());
    turboExpander.setIsentropicEfficiency(0.80);
    turboExpander.setOutletPressure(50.0, "bara");
    operations.add(turboExpander);

    Separator texScrubber = new Separator("TEX LT Scrubber", turboExpander.getOutletStream());
    operations.add(texScrubber);

    // Sales gas heater for export
    Heater salesGasHeater = new Heater("Sales Gas Heater E-401", texScrubber.getGasOutStream());
    salesGasHeater.setOutTemperature(20.0, "C");
    operations.add(salesGasHeater);

    // =========================================================================
    // Condensate Recovery Mixers
    // =========================================================================
    // HP Condensate mixer (from dew point scrubber 1)
    Mixer hpCondensateMixer = new Mixer("HP Condensate Mixer");
    hpCondensateMixer.addStream(dewPointScrubber1.getLiquidOutStream());
    operations.add(hpCondensateMixer);

    // MP Condensate mixer (from HP scrubber)
    Mixer mpCondensateMixer = new Mixer("MP Condensate Mixer");
    mpCondensateMixer.addStream(hpScrubber.getLiquidOutStream());
    operations.add(mpCondensateMixer);

    // LP Condensate mixer (from LP scrubber and MP scrubber)
    Mixer lpCondensateMixer = new Mixer("LP Condensate Mixer");
    lpCondensateMixer.addStream(lpScrubber.getLiquidOutStream());
    lpCondensateMixer.addStream(mpScrubber1.getLiquidOutStream());
    operations.add(lpCondensateMixer);

    // =========================================================================
    // Recycle Connections
    // =========================================================================
    Recycle hpRecycle = new Recycle("HP Condensate Recycle");
    hpRecycle.addStream(hpCondensateMixer.getOutletStream());
    hpRecycle.setOutletStream(oilFirstStageRecycle);
    hpRecycle.setTolerance(1e-2);
    operations.add(hpRecycle);

    Recycle mpRecycle = new Recycle("MP Condensate Recycle");
    mpRecycle.addStream(mpCondensateMixer.getOutletStream());
    mpRecycle.setOutletStream(oilSecondStageRecycle);
    mpRecycle.setTolerance(1e-2);
    operations.add(mpRecycle);

    Recycle lpRecycle = new Recycle("LP Condensate Recycle");
    lpRecycle.addStream(lpCondensateMixer.getOutletStream());
    lpRecycle.setOutletStream(oilThirdStageRecycle);
    lpRecycle.setTolerance(1e-2);
    operations.add(lpRecycle);

    // =========================================================================
    // Run simulation
    // =========================================================================
    operations.run();

    // =========================================================================
    // Generate Process Flow Diagram
    // =========================================================================
    ProcessDiagramExporter exporter = new ProcessDiagramExporter(operations);

    // Use HYSYS style for a professional look
    exporter.setDiagramStyle(DiagramStyle.HYSYS);
    exporter.setShowLegend(true);
    exporter.setShowStreamValues(true);
    exporter.setHighlightRecycles(true);

    String dotOutput = exporter.toDOT();

    // Verify DOT output was generated
    assertNotNull(dotOutput);
    assertFalse(dotOutput.isEmpty());
  }

  /**
   * Test generating the same process in all diagram styles for comparison.
   */
  @Test
  void testOilStabilizationAllStyles() {
    // Create a simpler but representative oil stabilization process
    SystemInterface wellFluid = new SystemSrkEos(340.15, 60.0);
    wellFluid.addComponent("methane", 70.0);
    wellFluid.addComponent("ethane", 8.0);
    wellFluid.addComponent("propane", 5.0);
    wellFluid.addComponent("n-butane", 3.0);
    wellFluid.addComponent("n-pentane", 2.0);
    wellFluid.addComponent("n-heptane", 6.0);
    wellFluid.addComponent("n-octane", 4.0);
    wellFluid.addComponent("water", 2.0);
    wellFluid.setMixingRule("classic");
    wellFluid.setMultiPhaseCheck(true);

    ProcessSystem process = new ProcessSystem();
    process.setName("Oil Stabilization Compact");

    // Feed
    Stream feed = new Stream("Well Stream", wellFluid);
    feed.setFlowRate(40000.0, "kg/hr");
    feed.setTemperature(60.0, "C");
    feed.setPressure(60.0, "bara");
    process.add(feed);

    // HP Separator
    ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", feed);
    process.add(hpSep);

    // HP letdown valve
    ThrottlingValve hpValve = new ThrottlingValve("HP Letdown", hpSep.getOilOutStream());
    hpValve.setOutletPressure(15.0, "bara");
    process.add(hpValve);

    // Recycle placeholder
    Stream mpRecycleStream = feed.clone();
    mpRecycleStream.setName("MP Recycle Stream");
    mpRecycleStream.setFlowRate(5.0, "kg/hr");
    mpRecycleStream.setPressure(15.0, "bara");
    mpRecycleStream.setTemperature(40.0, "C");
    process.add(mpRecycleStream);

    // Mixer for recycle
    Mixer mpMixer = new Mixer("MP Mixer");
    mpMixer.addStream(hpValve.getOutletStream());
    mpMixer.addStream(mpRecycleStream);
    process.add(mpMixer);

    // Oil heater
    Heater oilHeater = new Heater("Oil Heater", mpMixer.getOutletStream());
    oilHeater.setOutTemperature(75.0, "C");
    process.add(oilHeater);

    // MP Separator
    ThreePhaseSeparator mpSep =
        new ThreePhaseSeparator("MP Separator", oilHeater.getOutletStream());
    process.add(mpSep);

    // LP letdown valve
    ThrottlingValve lpValve = new ThrottlingValve("LP Letdown", mpSep.getOilOutStream());
    lpValve.setOutletPressure(3.0, "bara");
    process.add(lpValve);

    // Stabilizer
    Separator stabilizer = new Separator("Stabilizer", lpValve.getOutletStream());
    process.add(stabilizer);

    // Gas compression train
    Cooler lpCooler = new Cooler("LP Cooler", stabilizer.getGasOutStream());
    lpCooler.setOutTemperature(30.0, "C");
    process.add(lpCooler);

    Separator lpScrubber = new Separator("LP Scrubber", lpCooler.getOutletStream());
    process.add(lpScrubber);

    Compressor lpCompressor = new Compressor("LP Compressor", lpScrubber.getGasOutStream());
    lpCompressor.setOutletPressure(15.0, "bara");
    lpCompressor.setPolytropicEfficiency(0.78);
    process.add(lpCompressor);

    // Mix with MP gas
    Mixer mpGasMixer = new Mixer("MP Gas Mixer");
    mpGasMixer.addStream(lpCompressor.getOutletStream());
    mpGasMixer.addStream(mpSep.getGasOutStream());
    process.add(mpGasMixer);

    Cooler mpCooler = new Cooler("MP Cooler", mpGasMixer.getOutletStream());
    mpCooler.setOutTemperature(30.0, "C");
    process.add(mpCooler);

    Separator mpScrubber = new Separator("MP Scrubber", mpCooler.getOutletStream());
    process.add(mpScrubber);

    Compressor hpCompressor = new Compressor("HP Compressor", mpScrubber.getGasOutStream());
    hpCompressor.setOutletPressure(60.0, "bara");
    hpCompressor.setPolytropicEfficiency(0.78);
    process.add(hpCompressor);

    // Mix with HP gas for export
    Mixer exportMixer = new Mixer("Export Mixer");
    exportMixer.addStream(hpCompressor.getOutletStream());
    exportMixer.addStream(hpSep.getGasOutStream());
    process.add(exportMixer);

    Cooler exportCooler = new Cooler("Export Cooler", exportMixer.getOutletStream());
    exportCooler.setOutTemperature(25.0, "C");
    process.add(exportCooler);

    // Recycle connection
    Mixer condensateMixer = new Mixer("Condensate Mixer");
    condensateMixer.addStream(lpScrubber.getLiquidOutStream());
    condensateMixer.addStream(mpScrubber.getLiquidOutStream());
    process.add(condensateMixer);

    Pump condensatePump = new Pump("Condensate Pump", condensateMixer.getOutletStream());
    condensatePump.setOutletPressure(15.0, "bara");
    process.add(condensatePump);

    Recycle condensateRecycle = new Recycle("Condensate Recycle");
    condensateRecycle.addStream(condensatePump.getOutletStream());
    condensateRecycle.setOutletStream(mpRecycleStream);
    condensateRecycle.setTolerance(1e-2);
    process.add(condensateRecycle);

    // Run simulation
    process.run();

    // Generate diagrams in all styles
    DiagramStyle[] styles = DiagramStyle.values();
    for (DiagramStyle style : styles) {
      ProcessDiagramExporter exporter = new ProcessDiagramExporter(process);
      exporter.setDiagramStyle(style);
      exporter.setShowLegend(true);
      exporter.setShowStreamValues(true);
      exporter.setHighlightRecycles(true);

      String dotOutput = exporter.toDOT();

      // Verify DOT output was generated
      assertNotNull(dotOutput);
      assertFalse(dotOutput.isEmpty());
    }
  }
}
