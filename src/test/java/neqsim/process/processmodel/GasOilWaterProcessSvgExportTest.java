package neqsim.process.processmodel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.diagram.DiagramDetailLevel;
import neqsim.process.processmodel.diagram.ProcessDiagramExporter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class that generates a Graphviz SVG diagram for a gas-oil-water process.
 *
 * <p>
 * This test creates a realistic oil and gas production process with valves, pumps, and compressors
 * to visualize the process flow diagram functionality.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class GasOilWaterProcessSvgExportTest extends neqsim.NeqSimTest {

  /**
   * Creates a well fluid with typical oil and gas components plus water.
   *
   * @return a configured SystemInterface with gas, oil, and water components
   */
  private SystemInterface createWellFluid() {
    SystemInterface fluid = new SystemSrkEos(338.15, 50.0);
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 2.5);
    fluid.addComponent("methane", 65.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("i-butane", 1.5);
    fluid.addComponent("n-butane", 2.5);
    fluid.addComponent("i-pentane", 1.0);
    fluid.addComponent("n-pentane", 1.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addComponent("n-heptane", 5.0);
    fluid.addComponent("n-octane", 4.0);
    fluid.addComponent("water", 2.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Creates a gas-oil-water process with valves, pumps, and compressors, then exports it to SVG.
   *
   * <p>
   * The process includes:
   * <ul>
   * <li>Well stream feed</li>
   * <li>First stage inlet heater</li>
   * <li>Three-phase HP separator</li>
   * <li>Oil throttling valve to MP</li>
   * <li>MP three-phase separator</li>
   * <li>Oil throttling valve to LP</li>
   * <li>LP three-phase separator</li>
   * <li>Gas compression train (3 stages with coolers)</li>
   * <li>Oil export pump</li>
   * <li>Water disposal pump</li>
   * </ul>
   * </p>
   *
   * @throws IOException if SVG export fails
   */
  @Test
  public void generateGasOilWaterProcessSvg() throws IOException {
    ProcessSystem process = new ProcessSystem("Gas-Oil-Water Production Facility");

    // ===== FEED SECTION =====
    Stream wellStream = new Stream("Well Stream", createWellFluid());
    wellStream.setTemperature(65.0, "C");
    wellStream.setPressure(50.0, "bara");
    wellStream.setFlowRate(150000.0, "kg/hr");

    Heater inletHeater = new Heater("Inlet Heater", wellStream);
    inletHeater.setOutTemperature(70.0, "C");

    // ===== HP SEPARATION (50 bara) =====
    ThreePhaseSeparator hpSeparator =
        new ThreePhaseSeparator("HP 3-Phase Separator", inletHeater.getOutStream());

    // HP Gas to compression
    Cooler hpGasCooler = new Cooler("HP Gas Cooler", hpSeparator.getGasOutStream());
    hpGasCooler.setOutTemperature(40.0, "C");

    Compressor stage1Compressor =
        new Compressor("1st Stage Compressor", hpGasCooler.getOutStream());
    stage1Compressor.setOutletPressure(80.0, "bara");
    stage1Compressor.setIsentropicEfficiency(0.75);

    Cooler stage1Aftercooler = new Cooler("1st Stage Aftercooler", stage1Compressor.getOutStream());
    stage1Aftercooler.setOutTemperature(40.0, "C");

    Compressor stage2Compressor =
        new Compressor("2nd Stage Compressor", stage1Aftercooler.getOutStream());
    stage2Compressor.setOutletPressure(120.0, "bara");
    stage2Compressor.setIsentropicEfficiency(0.75);

    Cooler stage2Aftercooler = new Cooler("2nd Stage Aftercooler", stage2Compressor.getOutStream());
    stage2Aftercooler.setOutTemperature(40.0, "C");

    Stream exportGas = new Stream("Export Gas", stage2Aftercooler.getOutStream());

    // HP Oil to MP via valve
    ThrottlingValve hpToMpValve = new ThrottlingValve("HP-MP Valve", hpSeparator.getOilOutStream());
    hpToMpValve.setOutletPressure(15.0, "bara");

    // HP Water to disposal
    Pump hpWaterPump = new Pump("HP Water Pump", hpSeparator.getWaterOutStream());
    hpWaterPump.setOutletPressure(5.0, "bara");

    Stream hpWaterDisposal = new Stream("HP Water Disposal", hpWaterPump.getOutStream());

    // ===== MP SEPARATION (15 bara) =====
    ThreePhaseSeparator mpSeparator =
        new ThreePhaseSeparator("MP 3-Phase Separator", hpToMpValve.getOutStream());

    // MP Gas - vent or flare (simplified)
    Stream mpGasVent = new Stream("MP Gas Vent", mpSeparator.getGasOutStream());

    // MP Oil to LP via valve
    ThrottlingValve mpToLpValve = new ThrottlingValve("MP-LP Valve", mpSeparator.getOilOutStream());
    mpToLpValve.setOutletPressure(2.0, "bara");

    // MP Water combined with HP water
    Pump mpWaterPump = new Pump("MP Water Pump", mpSeparator.getWaterOutStream());
    mpWaterPump.setOutletPressure(5.0, "bara");

    Stream mpWaterDisposal = new Stream("MP Water Disposal", mpWaterPump.getOutStream());

    // ===== LP SEPARATION (2 bara) =====
    ThreePhaseSeparator lpSeparator =
        new ThreePhaseSeparator("LP 3-Phase Separator", mpToLpValve.getOutStream());

    // LP Gas - vent or flare (simplified)
    Stream lpGasVent = new Stream("LP Gas Vent", lpSeparator.getGasOutStream());

    // LP Oil to export pump
    Pump oilExportPump = new Pump("Oil Export Pump", lpSeparator.getOilOutStream());
    oilExportPump.setOutletPressure(10.0, "bara");

    Cooler oilExportCooler = new Cooler("Oil Export Cooler", oilExportPump.getOutStream());
    oilExportCooler.setOutTemperature(45.0, "C");

    Stream exportOil = new Stream("Export Oil", oilExportCooler.getOutStream());

    // LP Water
    Pump lpWaterPump = new Pump("LP Water Pump", lpSeparator.getWaterOutStream());
    lpWaterPump.setOutletPressure(5.0, "bara");

    Stream lpWaterDisposal = new Stream("LP Water Disposal", lpWaterPump.getOutStream());

    // ===== ADD ALL EQUIPMENT TO PROCESS =====
    // Feed section
    process.add(wellStream);
    process.add(inletHeater);

    // HP separation
    process.add(hpSeparator);
    process.add(hpGasCooler);
    process.add(stage1Compressor);
    process.add(stage1Aftercooler);
    process.add(stage2Compressor);
    process.add(stage2Aftercooler);
    process.add(exportGas);
    process.add(hpToMpValve);
    process.add(hpWaterPump);
    process.add(hpWaterDisposal);

    // MP separation
    process.add(mpSeparator);
    process.add(mpGasVent);
    process.add(mpToLpValve);
    process.add(mpWaterPump);
    process.add(mpWaterDisposal);

    // LP separation
    process.add(lpSeparator);
    process.add(lpGasVent);
    process.add(oilExportPump);
    process.add(oilExportCooler);
    process.add(exportOil);
    process.add(lpWaterPump);
    process.add(lpWaterDisposal);

    // Run the process to calculate all streams
    process.run();

    // ===== EXPORT TO DOT AND SVG =====
    // Define output paths
    Path outputDir = Paths.get("output");
    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir);
    }

    // Export DOT file (can be viewed with any Graphviz viewer)
    Path dotFile = outputDir.resolve("gas-oil-water-process.dot");
    String dotContent = process.toDOT(DiagramDetailLevel.ENGINEERING);
    Files.write(dotFile, dotContent.getBytes());
    System.out.println("DOT file exported to: " + dotFile.toAbsolutePath());

    // Export SVG file (requires Graphviz 'dot' command to be installed)
    Path svgFile = outputDir.resolve("gas-oil-water-process.svg");
    try {
      ProcessDiagramExporter exporter = new ProcessDiagramExporter(process);
      exporter.setTitle("Gas-Oil-Water Production Facility");
      exporter.setDetailLevel(DiagramDetailLevel.ENGINEERING);
      exporter.setShowStreamValues(true);
      exporter.exportSVG(svgFile);
      System.out.println("SVG file exported to: " + svgFile.toAbsolutePath());
    } catch (IOException e) {
      System.out.println("SVG export requires Graphviz to be installed.");
      System.out.println("Install Graphviz: https://graphviz.org/download/");
      System.out.println("On Windows: choco install graphviz");
      System.out.println("On macOS: brew install graphviz");
      System.out.println("On Linux: apt-get install graphviz");
      System.out.println("\nYou can manually convert the DOT file using:");
      System.out
          .println("  dot -Tsvg " + dotFile.toAbsolutePath() + " -o " + svgFile.toAbsolutePath());
      throw e;
    }

    // Verify files were created
    assert Files.exists(dotFile) : "DOT file was not created";
    System.out.println("\n=== Process Summary ===");
    System.out.println("Export Gas pressure: " + exportGas.getPressure("bara") + " bara");
    System.out.println("Export Oil pressure: " + exportOil.getPressure("bara") + " bara");
  }
}
