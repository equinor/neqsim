package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the PackedColumn equipment class.
 */
@Tag("slow")
public class PackedColumnTest {

  @Test
  public void testBasicAbsorber() {
    // Simple methane/ethane gas absorption with a heavy liquid
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");

    Stream gasIn = new Stream("gas feed", gas);
    gasIn.setFlowRate(50000.0, "kg/hr");
    gasIn.setTemperature(30.0, "C");
    gasIn.setPressure(50.0, "bara");

    SystemInterface liquid = new SystemSrkEos(273.15 + 25.0, 50.0);
    liquid.addComponent("nC10", 0.99);
    liquid.addComponent("propane", 0.01);
    liquid.setMixingRule("classic");

    Stream liqIn = new Stream("solvent", liquid);
    liqIn.setFlowRate(10000.0, "kg/hr");
    liqIn.setTemperature(25.0, "C");
    liqIn.setPressure(50.0, "bara");

    PackedColumn column = new PackedColumn("Absorber", gasIn);
    column.setPackedHeight(6.0);
    column.setPackingType("Pall-Ring-50");
    column.setStructuredPacking(false);
    column.setDesignFloodFraction(0.70);
    column.addSolventStream(liqIn);

    ProcessSystem process = new ProcessSystem();
    process.add(gasIn);
    process.add(liqIn);
    process.add(column);
    process.run();

    // Check that column converged
    assertTrue(column.getGasOutStream() != null, "Gas out should exist");
    assertTrue(column.getLiquidOutStream() != null, "Liquid out should exist");
  }

  @Test
  public void testPackedColumnSettersAndGetters() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 30.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");

    Stream gasIn = new Stream("gas", gas);
    gasIn.setFlowRate(20000.0, "kg/hr");

    PackedColumn column = new PackedColumn("Test Column", gasIn);

    column.setPackedHeight(8.0);
    assertEquals(8.0, column.getPackedHeight(), 0.01);

    column.setPackingType("Mellapak-250Y");
    assertEquals("Mellapak-250Y", column.getPackingType());

    column.setStructuredPacking(true);
    assertTrue(column.isStructuredPacking());

    column.setDesignFloodFraction(0.75);
    assertEquals(0.75, column.getDesignFloodFraction(), 0.01);

    column.setColumnDiameter(1.5);
  }

  @Test
  public void testPackedColumnWithCondenserReboiler() {
    // Distillation-type packed column
    SystemInterface feed = new SystemSrkEos(273.15 + 60.0, 10.0);
    feed.addComponent("methane", 0.40);
    feed.addComponent("ethane", 0.30);
    feed.addComponent("propane", 0.20);
    feed.addComponent("n-butane", 0.10);
    feed.setMixingRule("classic");

    Stream feedStream = new Stream("feed", feed);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setTemperature(60.0, "C");
    feedStream.setPressure(10.0, "bara");

    PackedColumn column =
        new PackedColumn("Packed Distillation", 5.0, "Pall-Ring-25", true, true);
    column.addFeedStream(feedStream, 5);
    column.setPackedHeight(5.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feedStream);
    process.add(column);
    process.run();

    assertTrue(column.getGasOutStream() != null, "Gas out should exist");
    assertTrue(column.getLiquidOutStream() != null, "Liquid out should exist");
  }

  @Test
  public void testJsonReport() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 30.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");

    Stream gasIn = new Stream("gas", gas);
    gasIn.setFlowRate(20000.0, "kg/hr");
    gasIn.setPressure(30.0, "bara");

    SystemInterface liq = new SystemSrkEos(273.15 + 25.0, 30.0);
    liq.addComponent("nC10", 1.0);
    liq.setMixingRule("classic");

    Stream liqIn = new Stream("solvent", liq);
    liqIn.setFlowRate(5000.0, "kg/hr");
    liqIn.setPressure(30.0, "bara");

    PackedColumn column = new PackedColumn("JSON Test", gasIn);
    column.setPackedHeight(4.0);
    column.setPackingType("Pall-Ring-38");
    column.addSolventStream(liqIn);

    ProcessSystem process = new ProcessSystem();
    process.add(gasIn);
    process.add(liqIn);
    process.add(column);
    process.run();

    String json = column.toJson();
    assertTrue(json.contains("PackedColumn"), "JSON should contain PackedColumn type");
    assertTrue(json.contains("packingConfiguration"), "JSON should contain packing config");
    assertTrue(json.contains("hydraulicResults"), "JSON should contain hydraulic results");
    assertTrue(json.contains("columnPerformance"), "JSON should contain performance");
  }
}
