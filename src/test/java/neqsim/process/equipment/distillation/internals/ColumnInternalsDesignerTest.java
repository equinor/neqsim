package neqsim.process.equipment.distillation.internals;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for {@link ColumnInternalsDesigner} with a real converged
 * {@link DistillationColumn}.
 */
@Tag("slow")
public class ColumnInternalsDesignerTest {

  /**
   * Tests tray internals sizing for a depropanizer column. Creates a column, converges it, then
   * runs the internals designer.
   */
  @Test
  public void testSieveTrayDesignForDepropanizer() {
    // Build a simple C3/nC4 column
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.0);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("DePropanizer", 5, true, true);
    column.addFeedStream(feed, 3);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.setMaxNumberOfIterations(50);
    column.run();

    // Now size the internals
    ColumnInternalsDesigner designer = new ColumnInternalsDesigner(column);
    designer.setInternalsType("sieve");
    designer.setTraySpacing(0.6);
    designer.setDesignFloodFraction(0.80);
    designer.calculate();

    // Required diameter should be reasonable for 5000 kg/hr
    double diam = designer.getRequiredDiameter();
    assertTrue(diam > 0.2, "Diameter too small: " + diam);
    assertTrue(diam < 5.0, "Diameter too large: " + diam);

    // Should have results for all trays
    assertTrue(designer.getTrayResults().size() > 0, "Should have per-tray results");

    // Total pressure drop should be positive
    assertTrue(designer.getTotalPressureDrop() > 0, "Total DP positive");

    // Max flooding should be reasonable
    assertTrue(designer.getMaxPercentFlood() > 0, "Max flood positive");

    // Average efficiency
    assertTrue(designer.getAverageTrayEfficiency() > 0.2, "Efficiency too low");
    assertTrue(designer.getAverageTrayEfficiency() < 1.0, "Efficiency too high");

    // JSON report should be parseable
    String json = designer.toJson();
    assertNotNull(json);
    assertTrue(json.contains("columnName"));
    assertTrue(json.contains("trayProfile"));
    assertTrue(json.contains("overallResults"));
    assertTrue(json.contains("percentFlood"));
  }

  /**
   * Tests the convenience method on DistillationColumn directly.
   */
  @Test
  public void testCalcColumnInternalsConvenience() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.0);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(3000.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("DeProp", 3, true, true);
    column.addFeedStream(feed, 2);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.run();

    // Use the convenience method
    ColumnInternalsDesigner designer = column.calcColumnInternals("valve");
    assertNotNull(designer);
    assertTrue(designer.getRequiredDiameter() > 0, "Should size diameter");
    assertTrue(designer.getTrayResults().size() > 0, "Should have tray results");

    // Default method
    ColumnInternalsDesigner designer2 = column.calcColumnInternals();
    assertNotNull(designer2);
    assertTrue(designer2.getRequiredDiameter() > 0, "Default sieve should work");
  }

  /**
   * Tests packed column evaluation.
   */
  @Test
  public void testPackedColumnDesign() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.0);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(3000.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("PackedCol", 3, true, true);
    column.addFeedStream(feed, 2);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.run();

    ColumnInternalsDesigner designer = new ColumnInternalsDesigner(column);
    designer.setInternalsType("packed");
    designer.setPackingPreset("Pall-Ring-50");
    designer.setPackedHeight(5.0);
    designer.setDesignFloodFraction(0.70);
    designer.calculate();

    assertTrue(designer.getRequiredDiameter() > 0.1, "Packed column diameter should be positive");
    assertNotNull(designer.getPackingResult(), "Should have packing result");
    assertTrue(designer.getTotalPressureDrop() > 0, "Pressure drop should be positive");

    String json = designer.toJson();
    assertNotNull(json);
    assertTrue(json.contains("packingDesign"));
    assertTrue(json.contains("HETP_m"));
  }

  /**
   * Tests structured packing design.
   */
  @Test
  public void testStructuredPackingDesign() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.0);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(3000.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("StructPacked", 3, true, true);
    column.addFeedStream(feed, 2);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.run();

    ColumnInternalsDesigner designer = new ColumnInternalsDesigner(column);
    designer.setInternalsType("packed");
    designer.setStructuredPacking(true);
    designer.setPackingPreset("Mellapak-250Y");
    designer.setPackedHeight(4.0);
    designer.setDesignFloodFraction(0.70);
    designer.calculate();

    assertTrue(designer.getRequiredDiameter() > 0.1);
    assertNotNull(designer.getPackingResult());
    assertTrue(designer.getPackingResult().getHETP() > 0, "HETP should be positive");
    assertTrue(designer.getPackingResult().getNumberOfTheoreticalStages() > 0,
        "Stages should be positive");
  }
}
