package neqsim.process.processmodel.diagram;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class demonstrating HYSYS-style diagram generation.
 *
 * <p>
 * Creates process flow diagrams that mimic the visual style of AspenTech HYSYS, including:
 * </p>
 * <ul>
 * <li>Blue-themed equipment with consistent styling</li>
 * <li>Blue material stream lines</li>
 * <li>Clean professional appearance</li>
 * <li>No background zone clustering</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class HysysStyleDiagramTest {
  private ProcessSystem processSystem;

  /**
   * Sets up a simple gas processing train for testing.
   */
  @BeforeEach
  public void setUp() {
    // Create a simple natural gas processing system
    SystemInterface gasComposition = new SystemSrkEos(280.0, 50.0);
    gasComposition.addComponent("methane", 0.85);
    gasComposition.addComponent("ethane", 0.08);
    gasComposition.addComponent("propane", 0.04);
    gasComposition.addComponent("n-butane", 0.02);
    gasComposition.addComponent("water", 0.01);
    gasComposition.setMixingRule("classic");

    // Create the process system
    processSystem = new ProcessSystem();
    processSystem.setName("Natural Gas Processing");

    // Feed stream
    Stream feedGas = new Stream("Feed Gas", gasComposition);
    feedGas.setFlowRate(100.0, "MSm3/day");
    feedGas.setTemperature(280.0, "K");
    feedGas.setPressure(50.0, "bara");
    processSystem.add(feedGas);

    // Inlet separator
    ThreePhaseSeparator inletSeparator = new ThreePhaseSeparator("Inlet Separator", feedGas);
    processSystem.add(inletSeparator);

    // Gas compression
    Compressor compressor =
        new Compressor("1st Stage Compressor", inletSeparator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    processSystem.add(compressor);

    // Aftercooler
    Cooler aftercooler = new Cooler("Aftercooler", compressor.getOutletStream());
    aftercooler.setOutTemperature(303.0, "K");
    processSystem.add(aftercooler);

    // HP Separator
    Separator hpSeparator = new Separator("HP Separator", aftercooler.getOutletStream());
    processSystem.add(hpSeparator);

    // Sales gas heater
    Heater salesHeater = new Heater("Sales Gas Heater", hpSeparator.getGasOutStream());
    salesHeater.setOutTemperature(313.0, "K");
    processSystem.add(salesHeater);

    // JT valve for condensate stabilization
    ThrottlingValve jtValve = new ThrottlingValve("JT Valve", hpSeparator.getLiquidOutStream());
    jtValve.setOutletPressure(10.0, "bara");
    processSystem.add(jtValve);

    // Stabilizer column
    DistillationColumn stabilizer = new DistillationColumn("Stabilizer", 10, true, true);
    stabilizer.addFeedStream(jtValve.getOutletStream(), 5);
    processSystem.add(stabilizer);

    // Condensate pump
    StreamInterface stabBottoms = stabilizer.getLiquidOutStream();
    Pump condensatePump = new Pump("Condensate Pump", stabBottoms);
    condensatePump.setOutletPressure(30.0, "bara");
    processSystem.add(condensatePump);

    // Water handling from inlet separator
    ThrottlingValve waterValve =
        new ThrottlingValve("Water Control Valve", inletSeparator.getWaterOutStream());
    waterValve.setOutletPressure(5.0, "bara");
    processSystem.add(waterValve);
  }

  /**
   * Tests generating a HYSYS-style diagram.
   *
   * @throws IOException if file writing fails
   */
  @Test
  public void testHysysStyleDiagram() throws IOException {
    // Create exporter with HYSYS style
    ProcessDiagramExporter exporter = new ProcessDiagramExporter(processSystem);
    exporter.setTitle("Natural Gas Processing - HYSYS Style").setDiagramStyle(DiagramStyle.HYSYS)
        .setDetailLevel(DiagramDetailLevel.ENGINEERING).setShowStreamValues(true);

    // Generate DOT output
    String dotContent = exporter.toDOT();

    // Write to output file
    String outputDir = "output";
    Files.createDirectories(Paths.get(outputDir));
    Files.write(Paths.get(outputDir, "hysys-style-process.dot"), dotContent.getBytes());

    System.out.println("HYSYS-style DOT file generated: output/hysys-style-process.dot");
    System.out.println("\nTo convert to SVG, run:");
    System.out
        .println("  dot -Tsvg output/hysys-style-process.dot -o output/hysys-style-process.svg");

    // Verify content
    org.junit.jupiter.api.Assertions.assertTrue(dotContent.contains("digraph"),
        "Should contain digraph");
    org.junit.jupiter.api.Assertions.assertTrue(dotContent.contains("HYSYS style"),
        "Should contain HYSYS style comment");
    org.junit.jupiter.api.Assertions.assertTrue(dotContent.contains("#0066CC"),
        "Should contain HYSYS blue stream color");
  }

  /**
   * Tests generating a PRO/II style diagram.
   *
   * @throws IOException if file writing fails
   */
  @Test
  public void testProIIStyleDiagram() throws IOException {
    // Create exporter with PRO/II style
    ProcessDiagramExporter exporter = new ProcessDiagramExporter(processSystem);
    exporter.setTitle("Natural Gas Processing - PRO/II Style").setDiagramStyle(DiagramStyle.PROII)
        .setDetailLevel(DiagramDetailLevel.ENGINEERING);

    // Generate DOT output
    String dotContent = exporter.toDOT();

    // Write to output file
    String outputDir = "output";
    Files.createDirectories(Paths.get(outputDir));
    Files.write(Paths.get(outputDir, "proii-style-process.dot"), dotContent.getBytes());

    System.out.println("PRO/II-style DOT file generated: output/proii-style-process.dot");

    // Verify content
    org.junit.jupiter.api.Assertions.assertTrue(dotContent.contains("digraph"),
        "Should contain digraph");
    org.junit.jupiter.api.Assertions.assertTrue(dotContent.contains("PRO/II style"),
        "Should contain PRO/II style comment");
    org.junit.jupiter.api.Assertions.assertTrue(dotContent.contains("#F5F5F5"),
        "Should contain PRO/II gray background");
  }

  /**
   * Tests generating an Aspen Plus style diagram.
   *
   * @throws IOException if file writing fails
   */
  @Test
  public void testAspenPlusStyleDiagram() throws IOException {
    // Create exporter with Aspen Plus style
    ProcessDiagramExporter exporter = new ProcessDiagramExporter(processSystem);
    exporter.setTitle("Natural Gas Processing - Aspen Plus Style")
        .setDiagramStyle(DiagramStyle.ASPEN_PLUS).setDetailLevel(DiagramDetailLevel.ENGINEERING);

    // Generate DOT output
    String dotContent = exporter.toDOT();

    // Write to output file
    String outputDir = "output";
    Files.createDirectories(Paths.get(outputDir));
    Files.write(Paths.get(outputDir, "aspen-style-process.dot"), dotContent.getBytes());

    System.out.println("Aspen Plus-style DOT file generated: output/aspen-style-process.dot");

    // Verify content
    org.junit.jupiter.api.Assertions.assertTrue(dotContent.contains("digraph"),
        "Should contain digraph");
    org.junit.jupiter.api.Assertions.assertTrue(dotContent.contains("Aspen Plus style"),
        "Should contain Aspen Plus style comment");
  }

  /**
   * Tests comparing all diagram styles side by side.
   *
   * @throws IOException if file writing fails
   */
  @Test
  public void testCompareAllStyles() throws IOException {
    String outputDir = "output";
    Files.createDirectories(Paths.get(outputDir));

    for (DiagramStyle style : DiagramStyle.values()) {
      ProcessDiagramExporter exporter = new ProcessDiagramExporter(processSystem);
      exporter.setTitle("Natural Gas Processing - " + style.getDisplayName()).setDiagramStyle(style)
          .setDetailLevel(DiagramDetailLevel.ENGINEERING);

      String dotContent = exporter.toDOT();
      String filename = style.name().toLowerCase() + "-style.dot";
      Files.write(Paths.get(outputDir, filename), dotContent.getBytes());

      System.out.println("Generated: output/" + filename);
    }

    System.out.println("\nGenerated diagrams for all styles. Convert with:");
    System.out.println("  for %f in (output\\*-style.dot) do dot -Tsvg %f -o %~dpnf.svg");
  }
}
