package neqsim.thermodynamicoperations.propertygenerator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.steam.BrineSalinityCorrection;
import neqsim.thermo.util.steam.LiquidWaterReference;

/**
 * Tests of the OLGA table generation options: explicit and envelope-refined grids, standard-condition insertion,
 * monotonic absent-phase fill, produced water, salinity, reference water equations and the quality report.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OlgaTableGenerationOptionsTest {
  /**
   * Gas condensate with a stock-tank oil.
   *
   * @return unflashed feed
   */
  private static SystemInterface condensate() {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.04);
    fluid.addComponent("nC10", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * CPA gas condensate with free water, water last.
   *
   * @return unflashed feed
   */
  private static SystemInterface wetCondensate() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 60.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-heptane", 0.04);
    fluid.addComponent("nC10", 0.03);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Read the first numeric value following a header keyword.
   *
   * @param text table text
   * @param keyword header keyword
   * @return value
   */
  private static double scalar(String text, String keyword) {
    String marker = keyword + " = ";
    int start = text.indexOf(marker);
    assertTrue(start >= 0, "Missing " + keyword);
    return Double.parseDouble(text.substring(start + marker.length()).split("[,\\s]")[0]);
  }

  /**
   * Find a column by keyword.
   *
   * @param keywords column keywords
   * @param keyword wanted keyword
   * @return column index
   */
  private static int column(String[] keywords, String keyword) {
    for (int k = 0; k < keywords.length; k++) {
      if (keyword.equals(keywords[k])) {
        return k;
      }
    }
    throw new IllegalArgumentException(keyword);
  }

  /**
   * Write a three-phase table and read it back.
   *
   * @param generator completed generator
   * @param directory output directory
   * @return table text
   * @throws IOException if reading fails
   */
  private static String table(OLGApropertyTableGeneratorWaterKeywordFormat generator, Path directory)
      throws IOException {
    Path target = directory.resolve("table.tab");
    generator.writeOLGAinpFile(target.toString());
    return new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
  }

  /** IF97 liquid water against IAPWS values at 25 C, 1 bara, and internal derivative consistency. */
  @Test
  void liquidWaterReferenceMatchesIapws() {
    LiquidWaterReference water = LiquidWaterReference.at(298.15, 1.0);
    assertEquals(997.05, water.getDensity(), 0.05);
    assertEquals(4181.3, water.getHeatCapacityCp(), 2.0);
    assertEquals(890.0e-6, water.getViscosity(), 1.0e-6);
    assertEquals(0.6065, water.getThermalConductivity(), 0.002);
    assertFalse(water.isMetastable());

    LiquidWaterReference state = LiquidWaterReference.at(353.15, 150.0);
    double dp = 0.1;
    double dt = 0.01;
    double drhodp = (LiquidWaterReference.at(353.15, 150.0 + dp).getDensity()
        - LiquidWaterReference.at(353.15, 150.0 - dp).getDensity()) / (2.0 * dp * 1.0e5);
    double drhodt = (LiquidWaterReference.at(353.15 + dt, 150.0).getDensity()
        - LiquidWaterReference.at(353.15 - dt, 150.0).getDensity()) / (2.0 * dt);
    double dhdt = (LiquidWaterReference.at(353.15 + dt, 150.0).getEnthalpy()
        - LiquidWaterReference.at(353.15 - dt, 150.0).getEnthalpy()) / (2.0 * dt);
    assertEquals(drhodp, state.getDensityPressureDerivative(), 1.0e-3 * Math.abs(drhodp));
    assertEquals(drhodt, state.getDensityTemperatureDerivative(), 1.0e-3 * Math.abs(drhodt));
    assertEquals(dhdt, state.getHeatCapacityCp(), 1.0e-3 * dhdt);

    assertTrue(LiquidWaterReference.at(263.15, 50.0).isMetastable());
    assertTrue(LiquidWaterReference.at(393.15, 1.0).isMetastable());
    assertThrows(IllegalArgumentException.class, () -> LiquidWaterReference.at(700.0, 50.0));
  }

  /** Batzle-Wang ratios are neutral without salt and give seawater-like corrections at 35 g/l. */
  @Test
  void brineCorrectionRatios() {
    assertEquals(1.0, BrineSalinityCorrection.densityRatio(323.15, 100.0, 0.0), 1.0e-12);
    assertEquals(1.0, BrineSalinityCorrection.viscosityRatio(323.15, 0.0), 1.0e-12);
    double salinity = BrineSalinityCorrection.massFractionFromConcentration(35000.0, 288.15, 1.01325);
    assertEquals(0.0343, salinity, 3.0e-4);
    double ratio = BrineSalinityCorrection.densityRatio(298.15, 1.01325, salinity);
    assertTrue(ratio > 1.02 && ratio < 1.03, "density ratio " + ratio);
    double viscosityRatio = BrineSalinityCorrection.viscosityRatio(298.15, salinity);
    assertTrue(viscosityRatio > 1.03 && viscosityRatio < 1.12, "viscosity ratio " + viscosityRatio);
    assertThrows(IllegalArgumentException.class, () -> BrineSalinityCorrection.densityRatio(300.0, 10.0, 0.5));
  }

  /** Envelope refinement places gridFactor/(gridFactor+1) of the points inside the envelope. */
  @Test
  void refinedAxisConcentratesPointsInsideEnvelope() {
    double[] axis = OlgaTableGrid.refinedAxis(1.0, 200.0, 20, 1.0, 100.0, 5.0);
    assertEquals(20, axis.length);
    assertEquals(1.0, axis[0], 0.0);
    assertEquals(200.0, axis[19], 0.0);
    int inside = 0;
    for (int i = 0; i < axis.length; i++) {
      if (i > 0) {
        assertTrue(axis[i] > axis[i - 1]);
      }
      if (axis[i] <= 100.0) {
        inside++;
      }
    }
    assertEquals(17, inside);
    assertEquals(null, OlgaTableGrid.refinedAxis(1.0, 200.0, 20, 1.0, 250.0, 5.0));
  }

  /** The standard value is inserted without changing the point count of a range axis. */
  @Test
  void standardValueIsInsertedIntoGrid() {
    OlgaTableGrid grid = new OlgaTableGrid();
    grid.setRange(1.0, 101.0, 5);
    double[] axis = grid.build(false, 5.0, Double.NaN, Double.NaN, true, 1.01325);
    assertEquals(5, axis.length);
    assertEquals(1.01325, axis[1], 0.0);
    assertEquals(101.0, axis[4], 0.0);

    grid.setValues(new double[] {50.0, 0.5, 10.0, 10.0});
    assertArrayEquals(new double[] {0.5, 1.01325, 10.0, 50.0}, grid.build(false, 5.0, 1.0, 40.0, true, 1.01325), 0.0);
    assertArrayEquals(new double[] {0.5, 10.0, 50.0}, grid.build(true, 5.0, 1.0, 40.0, false, 1.01325), 0.0);
  }

  /**
   * Explicit grids with inserted standard conditions, envelope refinement and a loadable quality report.
   *
   * @param directory temporary output directory
   * @throws IOException if writing fails
   */
  @Test
  void twoPhaseGridOptionsAndQualityReport(@TempDir Path directory) throws IOException {
    OLGApropertyTableGeneratorKeywordFormat generator = new OLGApropertyTableGeneratorKeywordFormat(condensate());
    generator.setPressures(new double[] {120.0, 20.0, 60.0});
    generator.setTemperatures(new double[] {273.15, 323.15});
    generator.setInsertStandardConditions(true);
    generator.run();
    assertArrayEquals(new double[] {20.0, 60.0, 120.0}, generator.pressures, 1.0e-12);
    assertArrayEquals(new double[] {273.15, 288.15, 323.15}, generator.temperatures, 1.0e-12);

    OLGApropertyTableGeneratorKeywordFormat refined = new OLGApropertyTableGeneratorKeywordFormat(condensate());
    refined.setPressureRange(5.0, 400.0, 8);
    refined.setTemperatureRange(253.15, 553.15, 8);
    refined.setEnvelopeRefinement(true, 5.0);
    refined.run();
    double[] uniform = OlgaTableGrid.uniform(5.0, 400.0, 8);
    assertFalse(java.util.Arrays.equals(uniform, refined.pressures), "pressure axis was not refined");
    OlgaTableQualityReport report = refined.getQualityReport();
    assertTrue(report.isLoadable(), report.toJson());
    for (String keyword : new String[] {"ROG", "HG", "HHL", "SEG", "SEHL"}) {
      assertEquals(0, report.getColumn(keyword).monotonicityViolationsExtrapolated, keyword);
    }
    // Retrograde condensate liquid density falls with pressure at existing nodes; that is reported, not corrected.
    assertTrue(report.getColumn("ROHL").monotonicityViolationsExisting > 0);
    Path csv = directory.resolve("qc.csv");
    report.writeCsv(csv.toString());
    assertTrue(Files.readAllLines(csv, StandardCharsets.UTF_8).size() > 1);
    assertTrue(report.toJson().contains("\"loadable\": true"));
  }

  /** Absent-phase enthalpy follows the phase heat capacity instead of being held flat. */
  @Test
  void monotonicFillUsesPhaseGradients() {
    OLGApropertyTableGeneratorKeywordFormat corrected = new OLGApropertyTableGeneratorKeywordFormat(condensate());
    corrected.setPressureRange(5.0, 60.0, 3);
    corrected.setTemperatureRange(273.15, 573.15, 6);
    corrected.run();

    int enthalpy = column(corrected.namesKeyword, "HHL");
    int checked = 0;
    for (int i = 0; i < corrected.pressures.length; i++) {
      for (int j = 1; j < corrected.temperatures.length; j++) {
        if (corrected.columnPresent[enthalpy][i][j] || corrected.columnPresent[enthalpy][i][j - 1]) {
          continue;
        }
        double slope = (corrected.props[enthalpy][i][j] - corrected.props[enthalpy][i][j - 1])
            / (corrected.temperatures[j] - corrected.temperatures[j - 1]);
        assertTrue(slope > 500.0 && slope < 10000.0, "absent-phase enthalpy slope " + slope);
        checked++;
      }
    }
    assertTrue(checked > 0, "grid must contain an absent-liquid region");

    OLGApropertyTableGeneratorKeywordFormat legacy = new OLGApropertyTableGeneratorKeywordFormat(condensate());
    legacy.setPressureRange(5.0, 60.0, 3);
    legacy.setTemperatureRange(273.15, 573.15, 6);
    legacy.setMonotonicityCorrection(false);
    legacy.run();
    assertTrue(legacy.getQualityReport().isLoadable());
  }

  /**
   * Reference water equations replace the aqueous columns at pure-water nodes.
   *
   * @param directory temporary output directory
   * @throws IOException if writing fails
   */
  @Test
  void referenceWaterProperties(@TempDir Path directory) throws IOException {
    OLGApropertyTableGeneratorWaterKeywordFormat generator = new OLGApropertyTableGeneratorWaterKeywordFormat(
        wetCondensate());
    generator.setPressureRange(20.0, 100.0, 2);
    generator.setTemperatureRange(283.15, 333.15, 3);
    generator.setUseReferenceWaterProperties(true);
    generator.run();
    assertEquals(6, generator.getReferenceWaterNodeCount());
    assertEquals(0, generator.getReferenceWaterSkippedNodeCount());
    LiquidWaterReference water = LiquidWaterReference.at(333.15, 100.0);
    int density = column(generator.namesKeyword, "ROWT");
    int viscosity = column(generator.namesKeyword, "VISWT");
    int enthalpy = column(generator.namesKeyword, "HWT");
    assertEquals(water.getDensity(), generator.props[density][1][2], 1.0e-9);
    assertEquals(water.getViscosity(), generator.props[viscosity][1][2], 1.0e-12);
    double enthalpyRise = generator.props[enthalpy][1][2] - generator.props[enthalpy][1][0];
    double referenceRise = water.getEnthalpy() - LiquidWaterReference.at(283.15, 100.0).getEnthalpy();
    assertEquals(referenceRise, enthalpyRise, 1.0e-6 * referenceRise);
    String text = table(generator, directory);
    assertEquals(LiquidWaterReference.at(288.15, 1.01325).getDensity(), scalar(text, "STDWATDENSITY"), 1.0e-9);
  }

  /**
   * Salinity scales density, its derivatives and viscosity by the brine ratios.
   *
   * @param directory temporary output directory
   * @throws IOException if writing fails
   */
  @Test
  void salinityCorrectsWaterColumns(@TempDir Path directory) throws IOException {
    OLGApropertyTableGeneratorWaterKeywordFormat fresh = new OLGApropertyTableGeneratorWaterKeywordFormat(
        wetCondensate());
    fresh.setPressureRange(20.0, 100.0, 2);
    fresh.setTemperatureRange(283.15, 333.15, 2);
    fresh.run();
    String freshText = table(fresh, directory);
    OLGApropertyTableGeneratorWaterKeywordFormat brine = new OLGApropertyTableGeneratorWaterKeywordFormat(
        wetCondensate());
    brine.setPressureRange(20.0, 100.0, 2);
    brine.setTemperatureRange(283.15, 333.15, 2);
    brine.setWaterSalinity(3.5, "wt%");
    brine.run();
    double ratio = BrineSalinityCorrection.densityRatio(333.15, 100.0, 0.035);
    int density = column(brine.namesKeyword, "ROWT");
    int viscosity = column(brine.namesKeyword, "VISWT");
    assertEquals(fresh.props[density][1][1] * ratio, brine.props[density][1][1], 1.0e-9 * brine.props[density][1][1]);
    assertEquals(fresh.props[viscosity][1][1] * BrineSalinityCorrection.viscosityRatio(333.15, 0.035),
        brine.props[viscosity][1][1], 1.0e-12);
    assertTrue(scalar(table(brine, directory), "STDWATDENSITY") > scalar(freshText, "STDWATDENSITY") * 1.02);
    assertThrows(IllegalArgumentException.class, () -> brine.setWaterSalinity(1.0, "g/l"));
  }

  /**
   * Produced water by water cut, mol% and wt%, leaving the source feed unchanged.
   *
   * @param directory temporary output directory
   * @throws IOException if writing fails
   */
  @Test
  void producedWaterSpecifications(@TempDir Path directory) throws IOException {
    SystemInterface feed = new SystemSrkCPAstatoil(298.15, 60.0);
    feed.addComponent("methane", 0.70);
    feed.addComponent("propane", 0.05);
    feed.addComponent("n-heptane", 0.10);
    feed.addComponent("nC10", 0.15);
    feed.setMixingRule(10);
    feed.setMultiPhaseCheck(true);

    OLGApropertyTableGeneratorWaterKeywordFormat byCut = new OLGApropertyTableGeneratorWaterKeywordFormat(feed);
    byCut.setPressureRange(20.0, 40.0, 2);
    byCut.setTemperatureRange(293.15, 313.15, 2);
    byCut.setProducedWater(30.0, "watercut");
    byCut.run();
    assertEquals(0.30, scalar(table(byCut, directory), "WC"), 2.0e-4);
    assertFalse(feed.hasComponent("water"));

    OLGApropertyTableGeneratorWaterKeywordFormat byMol = new OLGApropertyTableGeneratorWaterKeywordFormat(feed);
    byMol.setPressureRange(20.0, 40.0, 2);
    byMol.setTemperatureRange(293.15, 313.15, 2);
    byMol.setProducedWater(5.0, "mol%");
    byMol.run();
    assertEquals(0.05, byMol.getTableFluid().getPhase(0).getComponent("water").getz(), 1.0e-12);

    SystemInterface withWaterFeed = wetCondensate();
    OLGApropertyTableGeneratorWaterKeywordFormat rejected = new OLGApropertyTableGeneratorWaterKeywordFormat(
        withWaterFeed);
    rejected.setPressureRange(20.0, 40.0, 2);
    rejected.setTemperatureRange(293.15, 313.15, 2);
    rejected.setProducedWater(10.0, "wt%");
    assertThrows(IllegalArgumentException.class, rejected::run);
    assertThrows(IllegalArgumentException.class, () -> rejected.setProducedWater(10.0, "vol%"));
  }

  /** The table fluid is a copy of the source feed when no produced water is specified. */
  @Test
  void tableFluidIsSourceWithoutProducedWater() {
    SystemInterface feed = wetCondensate();
    // The generator works on a private copy of the feed, so initialise the caller's feed to read its composition.
    feed.init(0);
    OLGApropertyTableGeneratorWaterKeywordFormat generator = new OLGApropertyTableGeneratorWaterKeywordFormat(feed);
    generator.setPressureRange(20.0, 40.0, 2);
    generator.setTemperatureRange(293.15, 313.15, 2);
    generator.run();
    SystemInterface tableFluid = generator.getTableFluid();
    assertEquals(feed.getNumberOfComponents(), tableFluid.getNumberOfComponents());
    assertEquals(feed.getPhase(0).getComponent("water").getz(), tableFluid.getPhase(0).getComponent("water").getz(),
        1.0e-12);
  }

  /** The table critical point comes from the water-free composition. */
  @Test
  void criticalPointIsWaterFree() {
    SystemInterface wet = wetCondensate();
    SystemInterface dry = wet.clone();
    dry.removeComponent("water");
    OlgaEnvelopeInfo wetInfo = OlgaEnvelopeInfo.of(wet);
    OlgaEnvelopeInfo dryInfo = OlgaEnvelopeInfo.of(dry);
    assertTrue(Double.isFinite(dryInfo.cricondenthermTemperature));
    assertEquals(dryInfo.cricondenthermTemperature, wetInfo.cricondenthermTemperature, 1.0e-6);
    assertEquals(dryInfo.cricondenbarPressure, wetInfo.cricondenbarPressure, 1.0e-6);
  }

  /**
   * The wellstream example in docs/integration/olga_pvt_table_generation.md, on a coarser grid.
   *
   * @param directory temporary output directory
   * @throws IOException if writing fails
   */
  @Test
  void documentedWellstreamExample(@TempDir Path directory) throws IOException {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 40.0, 60.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-heptane", 0.05);
    fluid.addComponent("nC10", 0.05);
    fluid.addComponent("water", 0.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    OLGApropertyTableGeneratorWaterKeywordFormat generator = new OLGApropertyTableGeneratorWaterKeywordFormat(fluid);
    generator.setFluidLabel("WELLSTREAM");
    generator.setPressureRange(5.0, 250.0, 8);
    generator.setTemperatureRange(253.15, 393.15, 7);
    generator.setEnvelopeRefinement(true, 5.0);
    generator.setInsertStandardConditions(true);
    generator.setProducedWater(30.0, "watercut");
    generator.setWaterSalinity(35000.0, "mg/l");
    generator.setUseReferenceWaterProperties(true);
    generator.run();
    Path table = directory.resolve("wellstream.tab");
    Path reportFile = directory.resolve("wellstream_table_report.md");
    Path csv = directory.resolve("wellstream_table_qc.csv");
    generator.writeOLGAinpFile(table.toString());
    generator.writeTableReport(reportFile.toString());
    generator.getQualityReport().writeCsv(csv.toString());

    String text = new String(Files.readAllBytes(table), StandardCharsets.UTF_8);
    assertEquals(0.30, scalar(text, "WC"), 2.0e-4);
    assertEquals(56, text.split("PVTTABLE POINT").length - 1);
    assertTrue(generator.getReferenceWaterNodeCount() > 0);
    assertTrue(generator.getQualityReport().isLoadable(), generator.getQualityReport().toJson());
    String report = new String(Files.readAllBytes(reportFile), StandardCharsets.UTF_8);
    assertTrue(report.contains("# OLGA PVT table report: WELLSTREAM"));
    assertTrue(report.contains("+ standard"));
    assertTrue(report.contains("IAPWS reference equations"));
    assertTrue(report.contains("Batzle and Wang"));
    assertTrue(report.contains("Loadable by OLGA (all values finite"));
    assertTrue(report.contains("**yes**"));
  }
}
