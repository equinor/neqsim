package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link SpreadsheetBlock}.
 */
class SpreadsheetBlockTest {

  @Test
  void testConstantAndFormulaCells() {
    SpreadsheetBlock sheet = new SpreadsheetBlock("test-sheet");
    sheet.addConstantCell("A", 10.0);
    sheet.addConstantCell("B", 20.0);
    sheet.addFormulaCell("sum", cells -> cells.get("A") + cells.get("B"));
    sheet.addFormulaCell("product", cells -> cells.get("A") * cells.get("B"));

    sheet.run();

    assertEquals(10.0, sheet.getCellValue("A"), 1e-10);
    assertEquals(20.0, sheet.getCellValue("B"), 1e-10);
    assertEquals(30.0, sheet.getCellValue("sum"), 1e-10);
    assertEquals(200.0, sheet.getCellValue("product"), 1e-10);
  }

  @Test
  void testImportFromStream() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    SpreadsheetBlock sheet = new SpreadsheetBlock("stream-sheet");
    sheet.addStreamImportCell("pressure", feed, s -> s.getThermoSystem().getPressure("bara"));
    sheet.addStreamImportCell("temperature_K", feed, s -> s.getThermoSystem().getTemperature());
    sheet.addFormulaCell("temperature_C", cells -> cells.get("temperature_K") - 273.15);

    sheet.run();

    assertEquals(50.0, sheet.getCellValue("pressure"), 0.1);
    assertEquals(25.0, sheet.getCellValue("temperature_C"), 0.1);
  }

  @Test
  void testChainedFormulas() {
    SpreadsheetBlock sheet = new SpreadsheetBlock("chained");
    sheet.addConstantCell("x", 5.0);
    sheet.addFormulaCell("x_squared", cells -> cells.get("x") * cells.get("x"));
    sheet.addFormulaCell("x_cubed", cells -> cells.get("x_squared") * cells.get("x"));

    sheet.run();

    assertEquals(25.0, sheet.getCellValue("x_squared"), 1e-10);
    assertEquals(125.0, sheet.getCellValue("x_cubed"), 1e-10);
  }

  @Test
  void testGetAllCellValues() {
    SpreadsheetBlock sheet = new SpreadsheetBlock("all-cells");
    sheet.addConstantCell("A", 1.0);
    sheet.addConstantCell("B", 2.0);
    sheet.run();

    Map<String, Double> all = sheet.getAllCellValues();
    assertEquals(2, all.size());
    assertTrue(all.containsKey("A"));
    assertTrue(all.containsKey("B"));
  }

  @Test
  void testGetCellNames() {
    SpreadsheetBlock sheet = new SpreadsheetBlock("names");
    sheet.addConstantCell("alpha", 1.0);
    sheet.addConstantCell("beta", 2.0);
    sheet.addFormulaCell("gamma", cells -> cells.get("alpha") + cells.get("beta"));

    assertEquals(3, sheet.getCellNames().size());
    assertEquals("alpha", sheet.getCellNames().get(0));
    assertEquals("beta", sheet.getCellNames().get(1));
    assertEquals("gamma", sheet.getCellNames().get(2));
  }

  @Test
  void testExportCell() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Stream target = new Stream("target", fluid.clone());
    target.setFlowRate(500.0, "kg/hr");
    target.run();

    SpreadsheetBlock sheet = new SpreadsheetBlock("export-test");
    sheet.addConstantCell("newPressure", 30.0);
    sheet.addExportCell("newPressure", target, (eq, val) -> ((Stream) eq).setPressure(val, "bara"));

    sheet.run();

    assertEquals(30.0, target.getPressure(), 0.1);
  }

  @Test
  void testInProcessSystem() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    SpreadsheetBlock sheet = new SpreadsheetBlock("inline-calc");
    sheet.addStreamImportCell("P_bara", feed, s -> s.getThermoSystem().getPressure("bara"));
    sheet.addConstantCell("margin", 1.1);
    sheet.addFormulaCell("designP", cells -> cells.get("P_bara") * cells.get("margin"));

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sheet);
    process.run();

    assertEquals(55.0, sheet.getCellValue("designP"), 0.5);
  }

  @Test
  void testNaNOnMissingCell() {
    SpreadsheetBlock sheet = new SpreadsheetBlock("nan-test");
    double value = sheet.getCellValue("nonexistent");
    assertTrue(Double.isNaN(value));
  }
}
