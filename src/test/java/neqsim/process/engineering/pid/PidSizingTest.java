package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.mechanicaldesign.pipeline.NorsokP002LineSizingValidator;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks rotating equipment and heat-transfer sizing at a solved plant operating point.
 * 
 * @author NeqSim development team
 * @version 1.0
 */
class PidSizingTest extends NeqSimTest {
  /**
   * Verifies native owners, units and positive design values for each equipment family.
   * 
   * @param directory output directory
   * @throws Exception if native export or inspection fails
   */
  @Test
  void writesDesignPropertiesOnTheCorrectOwners(@TempDir Path directory) throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(313.15, 20.0);
    fluid.addComponent("methane", .80);
    fluid.addComponent("ethane", .05);
    fluid.addComponent("propane", .05);
    fluid.addComponent("n-decane", .10);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-F-001", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    Separator separator = new Separator("20-V-001", feed);
    Compressor compressor = new Compressor("20-K-001", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setPolytropicEfficiency(.78);
    compressor.setUsePolytropicCalc(true);
    compressor.setSpeed(18000);
    Pump pump = new Pump("20-P-001", separator.getLiquidOutStream());
    pump.setOutletPressure(80.0, "bara");
    HeatExchanger exchanger = new HeatExchanger("20-E-001", compressor.getOutletStream(), pump.getOutletStream());
    exchanger.setUAvalue(1000);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(pump);
    process.add(exchanger);
    process.run();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Heat recovery", process, "2.0.1").build()
        .setPidSizingSchedule("40", new NorsokP002LineSizingValidator().setMaximumGasVelocityMPerS(15));
    PidSizing sizes = new PidSizing(project);
    assertTrue(sizes.getEquipmentHolds().isEmpty(), sizes.getEquipmentHolds().toString());
    Path file = directory.resolve("heat-recovery.xml");
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-18T00:00:00Z", "NeqSim", "NeqSim", "test")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_NAME, "Heat recovery").build();
    assertTrue(
        DexpiEngineeringExporter.exportDexpi201(project, file, metadata, false, sizes).isSchemaAndProfileConformant());
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    Document doc = factory.newDocumentBuilder().parse(file.toFile());
    assertQuantity(doc, "20-E-001", "", PidSizing.Dimension.AREA, sizes);
    assertQuantity(doc, "20-P-001", "", PidSizing.Dimension.FLOW, sizes);
    assertQuantity(doc, "20-P-001", "", PidSizing.Dimension.HEAD, sizes);
    assertQuantity(doc, "20-K-001", "/Components[@property='Impellers']/Object", PidSizing.Dimension.IMPELLER, sizes);
  }

  /**
   * Verifies the dimensional unit and value at a tagged equipment port or owned component.
   * 
   * @param doc actual native output
   * @param tag equipment tag
   * @param component relative component path
   * @param dimension measured dimension
   * @param sizes independent sizing run
   * @throws Exception if the XPath cannot be evaluated
   */
  private void assertQuantity(Document doc, String tag, String component, PidSizing.Dimension dimension,
      PidSizing sizes) throws Exception {
    XPath xpath = XPathFactory.newInstance().newXPath();
    String quantity = "//Object[Data[@property='TagName']/String='" + tag + "']" + component + "/Data[@property='"
        + dimension.getProperty() + "']/AggregatedDataValue";
    assertEquals(dimension.getUnit(), xpath.evaluate(quantity + "/Data[@property='Unit']/DataReference/@data", doc));
    double value = Double.parseDouble(xpath.evaluate(quantity + "/Data[@property='Value']/Double", doc));
    assertTrue(value > 0);
    assertEquals(sizes.dimensions(tag).get(dimension), value, 1e-9);
  }
}
