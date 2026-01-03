package neqsim.process.processmodel.diagram;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.DexpiXmlReader;
import neqsim.process.processmodel.dexpi.DexpiXmlReaderException;
import neqsim.process.processmodel.dexpi.DexpiXmlWriter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Bridge between DEXPI XML data exchange and PFD visualization.
 *
 * <p>
 * This class provides integration utilities for:
 * </p>
 * <ul>
 * <li>Creating diagram exporters optimized for DEXPI-imported processes</li>
 * <li>Importing DEXPI XML files and generating PFD diagrams</li>
 * <li>Exporting ProcessSystem to DEXPI XML with embedded layout coordinates</li>
 * <li>Round-trip: DEXPI → NeqSim simulation → PFD → DEXPI with preserved context</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * // Import DEXPI and create diagram
 * ProcessDiagramExporter exporter =
 *     DexpiDiagramBridge.importAndCreateExporter(Paths.get("plant.xml"), templateStream);
 * exporter.exportAsDOT(Paths.get("diagram.dot"));
 *
 * // Create exporter optimized for DEXPI content
 * ProcessDiagramExporter exporter = DexpiDiagramBridge.createExporter(system);
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 * @see DexpiXmlReader
 * @see DexpiXmlWriter
 * @see ProcessDiagramExporter
 */
public final class DexpiDiagramBridge implements Serializable {
  private static final long serialVersionUID = 1000L;

  private DexpiDiagramBridge() {
    // Utility class
  }

  /**
   * Creates a diagram exporter optimized for DEXPI-imported processes.
   *
   * <p>
   * The exporter is configured with:
   * </p>
   * <ul>
   * <li>DEXPI metadata display enabled (line numbers, fluid codes)</li>
   * <li>Detailed labels for P&amp;ID cross-referencing</li>
   * <li>Legend enabled for phase identification</li>
   * </ul>
   *
   * @param processSystem the process system (typically imported from DEXPI)
   * @return configured diagram exporter
   */
  public static ProcessDiagramExporter createExporter(ProcessSystem processSystem) {
    return new ProcessDiagramExporter(processSystem).setShowDexpiMetadata(true)
        .setDetailLevel(DiagramDetailLevel.STANDARD).setShowLegend(true);
  }

  /**
   * Creates a diagram exporter with full detail for DEXPI-imported processes.
   *
   * <p>
   * Includes all available information: operating conditions, flow rates, and DEXPI metadata.
   * </p>
   *
   * @param processSystem the process system
   * @return configured diagram exporter with detailed labels
   */
  public static ProcessDiagramExporter createDetailedExporter(ProcessSystem processSystem) {
    return new ProcessDiagramExporter(processSystem).setShowDexpiMetadata(true)
        .setDetailLevel(DiagramDetailLevel.DETAILED).setShowLegend(true).setShowStreamValues(true);
  }

  /**
   * Imports a DEXPI XML file and creates a diagram exporter.
   *
   * <p>
   * Uses a default methane/ethane fluid template for stream thermodynamics.
   * </p>
   *
   * @param dexpiXmlFile path to the DEXPI XML file
   * @return diagram exporter for the imported process
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the XML is invalid
   */
  public static ProcessDiagramExporter importAndCreateExporter(Path dexpiXmlFile)
      throws IOException, DexpiXmlReaderException {
    Stream template = createDefaultTemplate();
    ProcessSystem system = DexpiXmlReader.read(dexpiXmlFile.toFile(), template);
    return createExporter(system);
  }

  /**
   * Imports a DEXPI XML file with a custom template stream and creates a diagram exporter.
   *
   * @param dexpiXmlFile path to the DEXPI XML file
   * @param templateStream stream template for thermodynamic properties
   * @return diagram exporter for the imported process
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the XML is invalid
   */
  public static ProcessDiagramExporter importAndCreateExporter(Path dexpiXmlFile,
      Stream templateStream) throws IOException, DexpiXmlReaderException {
    ProcessSystem system = DexpiXmlReader.read(dexpiXmlFile.toFile(), templateStream);
    return createExporter(system);
  }

  /**
   * Imports a DEXPI XML file into a ProcessSystem.
   *
   * <p>
   * Convenience wrapper around {@link DexpiXmlReader#read(File, Stream)} with a default template.
   * </p>
   *
   * @param dexpiXmlFile path to the DEXPI XML file
   * @return the imported process system
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the XML is invalid
   */
  public static ProcessSystem importDexpi(Path dexpiXmlFile)
      throws IOException, DexpiXmlReaderException {
    Stream template = createDefaultTemplate();
    return DexpiXmlReader.read(dexpiXmlFile.toFile(), template);
  }

  /**
   * Imports a DEXPI XML file into a ProcessSystem with a custom template.
   *
   * @param dexpiXmlFile path to the DEXPI XML file
   * @param templateStream stream template for thermodynamic properties
   * @return the imported process system
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the XML is invalid
   */
  public static ProcessSystem importDexpi(Path dexpiXmlFile, Stream templateStream)
      throws IOException, DexpiXmlReaderException {
    return DexpiXmlReader.read(dexpiXmlFile.toFile(), templateStream);
  }

  /**
   * Exports a ProcessSystem to DEXPI XML format.
   *
   * <p>
   * The exported XML preserves DEXPI metadata (tag names, line numbers, fluid codes) and includes
   * operating conditions (pressure, temperature, flow) as generic attributes.
   * </p>
   *
   * @param processSystem the process system to export
   * @param outputFile the output file path
   * @throws IOException if the file cannot be written
   */
  public static void exportToDexpi(ProcessSystem processSystem, Path outputFile)
      throws IOException {
    DexpiXmlWriter.write(processSystem, outputFile.toFile());
  }

  /**
   * Performs a complete round-trip: import DEXPI, generate diagram, export back to DEXPI.
   *
   * <p>
   * This is useful for:
   * </p>
   * <ul>
   * <li>Validating DEXPI import/export fidelity</li>
   * <li>Generating diagrams from existing P&amp;ID data</li>
   * <li>Enriching DEXPI files with simulation results</li>
   * </ul>
   *
   * @param inputDexpi path to input DEXPI XML file
   * @param outputDot path for output DOT diagram file
   * @param outputDexpi path for re-exported DEXPI XML file
   * @return the processed system
   * @throws IOException if file operations fail
   * @throws DexpiXmlReaderException if the input XML is invalid
   */
  public static ProcessSystem roundTrip(Path inputDexpi, Path outputDot, Path outputDexpi)
      throws IOException, DexpiXmlReaderException {
    // Import
    ProcessSystem system = importDexpi(inputDexpi);

    // Run simulation to populate thermodynamic state
    system.run();

    // Generate diagram
    ProcessDiagramExporter exporter = createExporter(system);
    exporter.exportAsDOT(outputDot);

    // Export back to DEXPI with enriched data
    exportToDexpi(system, outputDexpi);

    return system;
  }

  /**
   * Creates a default template stream with methane/ethane composition.
   *
   * @return default stream template
   */
  private static Stream createDefaultTemplate() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream template = new Stream("template", fluid);
    template.setFlowRate(1.0, "MSm3/day");
    template.setPressure(50.0, "bara");
    template.setTemperature(30.0, "C");
    return template;
  }
}
