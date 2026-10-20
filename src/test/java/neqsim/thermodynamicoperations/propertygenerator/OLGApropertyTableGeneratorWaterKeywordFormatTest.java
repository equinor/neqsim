package neqsim.thermodynamicoperations.propertygenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for the three-phase OLGA keyword definitions in issue 4478.
 *
 * @author NeqSim
 * @version 1.0
 */
class OLGApropertyTableGeneratorWaterKeywordFormatTest {
  /**
   * Reproduce the reported CPA gas condensate with free water.
   *
   * @return unflashed feed
   */
  private SystemInterface wetCondensate() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("n-heptane", 0.01);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Generate a small asymmetric table including the reported state.
   *
   * @param fluid feed to clone
   * @return completed generator
   */
  private OLGApropertyTableGeneratorWaterKeywordFormat generate(SystemInterface fluid) {
    OLGApropertyTableGeneratorWaterKeywordFormat generator = new OLGApropertyTableGeneratorWaterKeywordFormat(
        fluid.clone());
    generator.setPressureRange(60.0, 70.0, 2);
    generator.setTemperatureRange(273.15, 293.15, 3);
    generator.run();
    return generator;
  }

  /**
   * Read a generated table.
   *
   * @param generator completed generator
   * @param directory temporary output directory
   * @return file content
   * @throws IOException if reading fails
   */
  private String table(OLGApropertyTableGeneratorWaterKeywordFormat generator, Path directory) throws IOException {
    Path target = directory.resolve("fluid.tab");
    generator.writeOLGAinpFile(target.toString());
    return new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
  }

  /**
   * Read the first numeric value following a keyword.
   *
   * @param text generated table
   * @param keyword scalar header keyword
   * @return scalar value
   */
  private double scalar(String text, String keyword) {
    String marker = keyword + " = ";
    int start = text.indexOf(marker);
    assertTrue(start >= 0, "Missing " + keyword);
    String value = text.substring(start + marker.length()).split("[,\\s]")[0];
    return Double.parseDouble(value);
  }

  /**
   * RS must exclude free water, including in the written POINT row.
   *
   * @param directory temporary output directory
   * @throws IOException if reading fails
   */
  @Test
  void wetCondensateRsExcludesAqueousMass(@TempDir Path directory) throws IOException {
    SystemInterface fluid = wetCondensate();
    SystemInterface reference = fluid.clone();
    reference.setTemperature(273.15);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    double gasMass = reference.getPhaseOfType("gas").getMass();
    double oilMass = reference.getPhaseOfType("oil").getMass();
    assertTrue(reference.hasPhaseType("aqueous"));
    double expected = gasMass / (gasMass + oilMass);
    assertEquals(0.922261, expected, 2.0e-5);
    OLGApropertyTableGeneratorWaterKeywordFormat generator = generate(fluid);
    assertEquals(expected, generator.props[9][0][0], 1.0e-8);
    String text = table(generator, directory);
    String row = text.substring(text.indexOf("PVTTABLE POINT = ("));
    String[] values = row.substring(row.indexOf('(') + 1, row.indexOf(')')).split(",");
    assertEquals(expected, Double.parseDouble(values[11]), 1.0e-8);
    assertFalse(text.contains("BUBBLEPRESSURES"));
    assertFalse(text.contains("BUBBLETEMPERATURES"));
  }

  /**
   * Check standard ratios against an independent mass/density volume calculation.
   *
   * @param directory temporary output directory
   * @throws IOException if reading fails
   */
  @Test
  void standardRatiosIncludeWater(@TempDir Path directory) throws IOException {
    SystemInterface fluid = wetCondensate();
    fluid.addComponent("nC10", 0.10);
    fluid.setMixingRule(10);
    SystemInterface reference = fluid.clone();
    reference.setPressure(1.01325);
    reference.setTemperature(288.15);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    PhaseInterface gas = reference.getPhaseOfType("gas");
    PhaseInterface oil = reference.getPhaseOfType("oil");
    PhaseInterface water = reference.getPhaseOfType("aqueous");
    double gasVolume = gas.getMass() / gas.getDensity("kg/m3");
    double oilVolume = oil.getMass() / oil.getDensity("kg/m3");
    double waterVolume = water.getMass() / water.getDensity("kg/m3");
    String text = table(generate(fluid), directory);
    assertEquals(gasVolume / oilVolume, scalar(text, "GOR"), 1.0e-5);
    assertEquals(gasVolume / (oilVolume + waterVolume), scalar(text, "GLR"), 1.0e-5);
    assertEquals(waterVolume / (oilVolume + waterVolume), scalar(text, "WC"), 1.0e-8);
    assertTrue(scalar(text, "GLR") < scalar(text, "GOR"));
  }

  /**
   * Gas with condensed water must never be interpreted as oil.
   *
   * @param directory temporary output directory
   * @throws IOException if reading fails
   */
  @Test
  void gasWaterHasUnitRsAndWaterCut(@TempDir Path directory) throws IOException {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 60.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    OLGApropertyTableGeneratorWaterKeywordFormat generator = generate(fluid);
    assertEquals(1.0, generator.props[9][0][0], 0.0);
    String text = table(generator, directory);
    assertEquals(1.0, scalar(text, "WC"), 0.0);
    assertEquals(-999.0, scalar(text, "GOR"), 0.0);
    assertTrue(scalar(text, "GLR") > 0.0);
  }

  /**
   * A dry gas must use the no-liquid sentinel, with zero water cut.
   *
   * @param directory temporary output directory
   * @throws IOException if reading fails
   */
  @Test
  void dryGasHasNoLiquidSentinels(@TempDir Path directory) throws IOException {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    String text = table(generate(fluid), directory);
    assertEquals(-999.0, scalar(text, "GLR"), 0.0);
    assertEquals(-999.0, scalar(text, "GOR"), 0.0);
    assertEquals(0.0, scalar(text, "WC"), 0.0);
  }

  /**
   * Without gas, both an oil-only and a water-only grid must have zero RS.
   */
  @Test
  void noGasHasZeroRs() {
    for (String name : new String[] {"nC10", "water"}) {
      SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
      fluid.addComponent(name, 0.9);
      fluid.addComponent("n-heptane", 0.1);
      fluid.setMixingRule("classic");
      fluid.setMultiPhaseCheck(true);
      assertEquals(0.0, generate(fluid).props[9][0][0], 0.0);
    }
  }

  /**
   * Pure-component saturation must remain available and pair each pressure with its temperature.
   *
   * @param directory temporary output directory
   * @throws IOException if reading fails
   */
  @Test
  void pureComponentCurveDropsSupercriticalPoints(@TempDir Path directory) throws IOException {
    SystemInterface fluid = new SystemSrkEos(280.0, 20.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule("classic");
    OLGApropertyTableGeneratorWaterKeywordFormat generator = generate(fluid);
    generator.setTemperatureRange(280.0, 320.0, 3);
    generator.run();
    String text = table(generator, directory);
    String pMarker = "BUBBLEPRESSURES = (";
    String tMarker = "BUBBLETEMPERATURES = (";
    assertTrue(text.contains(pMarker));
    int pStart = text.indexOf(pMarker) + pMarker.length();
    int tStart = text.indexOf(tMarker) + tMarker.length();
    String[] pressures = text.substring(pStart, text.indexOf(')', pStart)).split(",");
    String[] temperatures = text.substring(tStart, text.indexOf(')', tStart)).split(",");
    assertEquals(2, pressures.length);
    assertEquals(pressures.length, temperatures.length);
    for (int i = 0; i < pressures.length; i++) {
      double pressure = Double.parseDouble(pressures[i]);
      double temperature = Double.parseDouble(temperatures[i]);
      assertTrue(pressure > 1.0e6 && pressure < 7.4e6);
      assertEquals(280.0 + 20.0 * i - 273.15, temperature, 1.0e-10);
    }
    assertFalse(text.contains("NaN"));
    assertFalse(text.contains("Infinity"));
  }

  /**
   * Bubble pressures calculated outside the property grid must retain their own matching temperatures.
   *
   * @param directory temporary output directory
   * @throws IOException if reading fails
   */
  @Test
  void customBubbleTemperaturesStayPairedAndDoNotMutateFluid(@TempDir Path directory) throws IOException {
    SystemInterface fluid = new SystemSrkEos(280.0, 20.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule("classic");
    OLGApropertyTableGeneratorWaterKeywordFormat generator = generate(fluid);
    double pressure = generator.thermoSystem.getPressure();
    double temperature = generator.thermoSystem.getTemperature();
    double[] saturation = generator.calcBubP(new double[] {270.0, 290.0, 310.0});
    assertTrue(saturation[0] > 0.0);
    assertTrue(saturation[1] > 0.0);
    assertTrue(Double.isNaN(saturation[2]));
    assertEquals(pressure, generator.thermoSystem.getPressure(), 0.0);
    assertEquals(temperature, generator.thermoSystem.getTemperature(), 0.0);
    String text = table(generator, directory);
    String marker = "BUBBLETEMPERATURES = (";
    int start = text.indexOf(marker) + marker.length();
    String[] temperatures = text.substring(start, text.indexOf(')', start)).split(",");
    assertEquals(2, temperatures.length);
    assertEquals(-3.15, Double.parseDouble(temperatures[0]), 1.0e-10);
    assertEquals(16.85, Double.parseDouble(temperatures[1]), 1.0e-10);
  }

  /**
   * A required pure-component curve must never be silently replaced by an empty or invalid one.
   *
   * @param directory temporary output directory
   */
  @Test
  void pureComponentWithoutSaturationFailsBeforeWriting(@TempDir Path directory) {
    SystemInterface fluid = new SystemSrkEos(320.0, 60.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule("classic");
    OLGApropertyTableGeneratorWaterKeywordFormat generator = new OLGApropertyTableGeneratorWaterKeywordFormat(fluid);
    generator.setPressureRange(60.0, 70.0, 2);
    generator.setTemperatureRange(320.0, 340.0, 2);
    generator.run();
    Path target = directory.resolve("invalid.tab");
    assertThrows(IllegalStateException.class, () -> generator.writeOLGAinpFile(target.toString()));
    assertFalse(Files.exists(target));
  }

  /**
   * Exercise the complete grid from issue 4478 and verify every exported property is finite.
   *
   * @param directory temporary output directory
   * @throws IOException if reading fails
   */
  @Test
  void fullReportedGridHasFiniteProperties(@TempDir Path directory) throws IOException {
    OLGApropertyTableGeneratorWaterKeywordFormat generator = new OLGApropertyTableGeneratorWaterKeywordFormat(
        wetCondensate());
    generator.setFluidLabel("TEST");
    generator.setPressureRange(10.0, 120.0, 12);
    generator.setTemperatureRange(253.15, 313.15, 7);
    generator.run();
    assertEquals(0.922261, generator.props[9][5][2], 2.0e-5);
    for (int k = 0; k < generator.props.length; k++) {
      for (int i = 0; i < 12; i++) {
        for (int j = 0; j < 7; j++) {
          assertTrue(Double.isFinite(generator.props[k][i][j]));
          if (k < 3) {
            assertTrue(generator.props[k][i][j] > 0.0);
          }
        }
      }
    }
    String text = table(generator, directory);
    assertEquals(84, text.split("PVTTABLE POINT = ").length - 1);
    assertFalse(text.contains("BUBBLEPRESSURES"));
    assertFalse(text.contains("BUBBLETEMPERATURES"));
    assertTrue(scalar(text, "GLR") > 0.0);
    assertTrue(scalar(text, "WC") > 0.0);
  }
}
