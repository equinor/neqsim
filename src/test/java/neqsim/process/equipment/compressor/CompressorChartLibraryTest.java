package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link CompressorChartLibrary}, {@link CompressorChartMetadata} and the {@link Compressor} multi-chart
 * selection API.
 */
public class CompressorChartLibraryTest {

  private CompressorChartInterface makeChart(double headScale) {
    CompressorChart chart = new CompressorChart();
    double[] chartConditions = { 18.0, 298.15, 50.0, 0.9 };
    double[] speeds = { 10000.0, 11000.0 };
    double[][] flows = { { 1000.0, 2000.0, 3000.0 }, { 1100.0, 2100.0, 3100.0 } };
    double[][] heads = { { 100.0 * headScale, 90.0 * headScale, 80.0 * headScale },
        { 110.0 * headScale, 100.0 * headScale, 90.0 * headScale } };
    double[][] effs = { { 75.0, 80.0, 78.0 }, { 76.0, 81.0, 79.0 } };
    chart.setCurves(chartConditions, speeds, flows, heads, effs);
    chart.setHeadUnit("kJ/kg");
    chart.setUseCompressorChart(true);
    return chart;
  }

  @Test
  public void testAddSelectAndSwitch() {
    CompressorChartLibrary lib = new CompressorChartLibrary("BCL 405/B bundle");
    CompressorChartMetadata metaDesign = new CompressorChartMetadata("BCL 405/B", "pipeline gas export", "27-KA01",
        "8300199-CA-001 sheet 14", CompressorChartMetadata.CurveType.EXPECTED)
        .setReferenceConditions(19.34, 303.15, 52.76, 0.877);

    lib.add("export-design", makeChart(1.0), metaDesign);
    lib.add("export-tested", makeChart(1.05));

    assertEquals(2, lib.size());
    assertTrue(lib.contains("export-design"));
    assertEquals("export-design", lib.getSelectedName(), "first added chart is selected by default");

    List<String> names = lib.getNames();
    assertEquals(2, names.size());
    assertEquals("export-design", names.get(0));

    // metadata retained
    CompressorChartMetadata m = lib.getMetadata("export-design");
    assertNotNull(m);
    assertEquals("BCL 405/B", m.getCasingModel());
    assertEquals(CompressorChartMetadata.CurveType.EXPECTED, m.getCurveType());
    assertEquals(19.34, m.getMolecularWeight(), 1e-9);

    // switch selection
    CompressorChartInterface tested = lib.select("export-tested");
    assertEquals("export-tested", lib.getSelectedName());
    assertEquals(tested, lib.getSelected());

    // unknown name throws
    assertThrows(IllegalArgumentException.class, () -> lib.select("does-not-exist"));

    // remove updates selection
    lib.remove("export-tested");
    assertEquals(1, lib.size());
    assertEquals("export-design", lib.getSelectedName());
  }

  @Test
  public void testValidation() {
    CompressorChartLibrary lib = new CompressorChartLibrary();
    assertThrows(IllegalArgumentException.class, () -> lib.add("", makeChart(1.0)));
    assertThrows(IllegalArgumentException.class, () -> lib.add("x", null));
    assertNull(lib.getSelected());
    assertEquals(0, lib.size());
  }

  @Test
  public void testJsonRoundTrip() {
    CompressorChartLibrary lib = new CompressorChartLibrary("bundle");
    lib.add("a", makeChart(1.0), new CompressorChartMetadata("BCL 505", "1st recompr", "23-KA01", "8300199-CA-001",
        CompressorChartMetadata.CurveType.EXPECTED));
    lib.add("b", makeChart(1.2));
    lib.select("b");

    String json = lib.toJson();
    assertTrue(json.contains("\"a\""));
    assertTrue(json.contains("BCL 505"));

    CompressorChartLibrary restored = CompressorChartLibrary.fromJson(json);
    assertEquals(2, restored.size());
    assertEquals("b", restored.getSelectedName());
    assertTrue(restored.contains("a"));
    assertNotNull(restored.getMetadata("a"));
    assertEquals("BCL 505", restored.getMetadata("a").getCasingModel());

    // reconstructed chart reproduces polytropic head
    double original = lib.get("a").getPolytropicHead(2000.0, 10000.0);
    double roundtrip = restored.get("a").getPolytropicHead(2000.0, 10000.0);
    assertEquals(original, roundtrip, 1e-6);
  }

  @Test
  public void testDescribeCatalog() {
    CompressorChartLibrary lib = new CompressorChartLibrary("bundle");
    lib.add("a", makeChart(1.0));
    String catalog = lib.describe();
    assertTrue(catalog.contains("\"count\": 1"));
    assertTrue(catalog.contains("\"name\": \"a\""));
  }

  @Test
  public void testCompressorSelectChart() {
    SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(10000.0, "kg/hr");
    inlet.setTemperature(25.0, "C");
    inlet.setPressure(50.0, "bara");
    inlet.run();

    Compressor comp = new Compressor("test compressor");
    comp.setInletStream(inlet);
    comp.setOutletPressure(100.0, "bara");
    comp.setSpeed(10500);

    CompressorChartInterface design = makeChart(1.0);
    CompressorChartInterface tested = makeChart(1.1);
    comp.addChart("design", design, new CompressorChartMetadata("BCL 405/B", "export", "27-KA01", "8300199-CA-001",
        CompressorChartMetadata.CurveType.EXPECTED));
    comp.addChart("tested", tested);

    assertEquals(2, comp.getAvailableCharts().size());
    assertEquals("design", comp.getSelectedChartName(), "first added chart selected by default");

    // switch active chart in one call
    comp.selectChart("tested");
    assertEquals("tested", comp.getSelectedChartName());
    assertEquals(tested, comp.getCompressorChart());
    assertTrue(comp.getCompressorChart().isUseCompressorChart());

    comp.selectChart("design");
    assertEquals(design, comp.getCompressorChart());

    assertThrows(IllegalArgumentException.class, () -> comp.selectChart("missing"));
  }
}
