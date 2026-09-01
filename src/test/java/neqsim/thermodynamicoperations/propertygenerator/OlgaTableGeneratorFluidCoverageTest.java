package neqsim.thermodynamicoperations.propertygenerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Fluid-coverage regression tests for the OLGA PVT table generators.
 *
 * <p>
 * The generators used to index phases by array position, assuming a gas phase at 0, an oil phase at 1 and an aqueous
 * phase at 2. A flash only returns the phases that exist, so every single-phase node wrote a zero density for the
 * missing phase and OLGA refused the file outright with <em>OIL DENSITY IS ZERO AT ...</em>. Dry gas, dead oil and
 * dense-phase CO2 tables were therefore unusable, and the three-phase generator threw on a fluid with no water
 * component at all.
 * </p>
 *
 * <p>
 * These tests pin the properties OLGA actually requires: every density column strictly positive, every value finite,
 * and the right PHASE keyword. They are deliberately cheap - a coarse grid is enough, because the defect was structural
 * rather than numerical.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class OlgaTableGeneratorFluidCoverageTest {

  private static final double MIN_PRESSURE_BARA = 5.0;
  private static final double MAX_PRESSURE_BARA = 120.0;
  private static final int PRESSURE_STEPS = 5;
  private static final double MIN_TEMPERATURE_K = 273.15 + 5.0;
  private static final double MAX_TEMPERATURE_K = 273.15 + 70.0;
  private static final int TEMPERATURE_STEPS = 4;

  /** Density columns of the two-phase table, which OLGA rejects when any entry is zero. */
  private static final String[] TWO_PHASE_DENSITY_COLUMNS = { "ROG", "ROHL" };
  /** Density columns of the three-phase table. */
  private static final String[] THREE_PHASE_DENSITY_COLUMNS = { "ROG", "ROHL", "ROWT" };

  /**
   * Build a single-phase dry gas.
   *
   * @return a NeqSim fluid
   */
  private SystemInterface dryGas() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 60.0);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Build a two-phase gas condensate.
   *
   * @return a NeqSim fluid
   */
  private SystemInterface gasCondensate() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Build a single-phase liquid with no light ends.
   *
   * @return a NeqSim fluid
   */
  private SystemInterface deadOil() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 20.0);
    fluid.addComponent("nC10", 0.5);
    fluid.addComponent("nC16", 0.5);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Build a gas, condensate and water fluid.
   *
   * @return a NeqSim fluid
   */
  private SystemInterface threePhase() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 60.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-heptane", 0.12);
    fluid.addComponent("nC10", 0.08);
    fluid.addComponent("water", 0.12);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Build a gas and water fluid with no hydrocarbon liquid.
   *
   * @return a NeqSim fluid
   */
  private SystemInterface gasWater() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 60.0);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("water", 0.08);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Generate a two-phase table.
   *
   * @param fluid fluid to tabulate
   * @param target output file
   */
  private void writeTwoPhaseTable(SystemInterface fluid, Path target) {
    OLGApropertyTableGeneratorKeywordFormat generator = new OLGApropertyTableGeneratorKeywordFormat(fluid);
    generator.setPressureRange(MIN_PRESSURE_BARA, MAX_PRESSURE_BARA, PRESSURE_STEPS);
    generator.setTemperatureRange(MIN_TEMPERATURE_K, MAX_TEMPERATURE_K, TEMPERATURE_STEPS);
    generator.run();
    generator.writeOLGAinpFile(target.toString());
  }

  /**
   * Generate a three-phase table.
   *
   * @param fluid fluid to tabulate
   * @param target output file
   */
  private void writeThreePhaseTable(SystemInterface fluid, Path target) {
    OLGApropertyTableGeneratorWaterKeywordFormat generator = new OLGApropertyTableGeneratorWaterKeywordFormat(fluid);
    generator.setPressureRange(MIN_PRESSURE_BARA, MAX_PRESSURE_BARA, PRESSURE_STEPS);
    generator.setTemperatureRange(MIN_TEMPERATURE_K, MAX_TEMPERATURE_K, TEMPERATURE_STEPS);
    generator.run();
    generator.writeOLGAinpFile(target.toString());
  }

  /**
   * Read a written table.
   *
   * @param target table file
   * @return file content
   * @throws IOException if the file cannot be read
   */
  private String read(Path target) throws IOException {
    assertTrue(Files.exists(target), "The generator wrote no file to " + target);
    return new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
  }

  /**
   * Check the invariants OLGA enforces on any table.
   *
   * @param table table content
   * @param expectedPhase expected PHASE keyword value
   * @param densityColumns density column keywords that must stay positive
   */
  private void assertLoadableByOlga(String table, String expectedPhase, String[] densityColumns) {
    assertTrue(table.contains("PHASE = " + expectedPhase),
        "Expected PHASE = " + expectedPhase + " in the table header");
    assertFalse(table.contains("NaN"), "A NaN in the table makes OLGA fail to parse it");
    assertFalse(table.contains("Infinity"), "An infinite value makes OLGA fail to parse it");

    List<String> columns = columnOrder(table);
    for (String column : densityColumns) {
      int index = columns.indexOf(column);
      assertTrue(index >= 0, "Column " + column + " is missing from the table");
      for (double value : columnValues(table, index)) {
        assertTrue(value > 0.0, "OLGA rejects a table with a zero or negative " + column + ", found " + value);
      }
    }
  }

  /**
   * Read the COLUMNS declaration.
   *
   * @param table table content
   * @return column keywords in file order
   */
  private List<String> columnOrder(String table) {
    List<String> columns = new ArrayList<String>();
    for (String line : table.split("\\R")) {
      if (line.startsWith("COLUMNS")) {
        String inside = line.substring(line.indexOf('(') + 1, line.lastIndexOf(')'));
        for (String name : inside.split(",")) {
          columns.add(name.trim());
        }
        break;
      }
    }
    assertFalse(columns.isEmpty(), "The table has no COLUMNS declaration");
    return columns;
  }

  /**
   * Read one column from every PVTTABLE POINT row.
   *
   * @param table table content
   * @param index zero-based column index
   * @return column values
   */
  private List<Double> columnValues(String table, int index) {
    List<Double> values = new ArrayList<Double>();
    for (String line : table.split("\\R")) {
      if (!line.startsWith("PVTTABLE POINT")) {
        continue;
      }
      String inside = line.substring(line.indexOf('(') + 1, line.lastIndexOf(')'));
      String[] parts = inside.split(",");
      if (index < parts.length) {
        String valueText = parts[index].trim();
        try {
          values.add(Double.valueOf(valueText));
        } catch (NumberFormatException e) {
          fail("Invalid numeric value '" + valueText + "' in PVTTABLE POINT row: " + line, e);
        }
      }
    }
    assertFalse(values.isEmpty(), "The table has no PVTTABLE POINT rows");
    return values;
  }

  /**
   * A single-phase dry gas must still produce a loadable two-phase table.
   *
   * @param tempDir JUnit temporary directory
   * @throws IOException if the table cannot be read
   */
  @Test
  void testDryGasTableHasNoZeroDensities(@TempDir Path tempDir) throws IOException {
    Path target = tempDir.resolve("dry_gas.tab");
    writeTwoPhaseTable(dryGas(), target);
    assertLoadableByOlga(read(target), "TWO", TWO_PHASE_DENSITY_COLUMNS);
  }

  /**
   * A gas condensate is the case that always worked and must keep working.
   *
   * @param tempDir JUnit temporary directory
   * @throws IOException if the table cannot be read
   */
  @Test
  void testGasCondensateTableHasNoZeroDensities(@TempDir Path tempDir) throws IOException {
    Path target = tempDir.resolve("gas_condensate.tab");
    writeTwoPhaseTable(gasCondensate(), target);
    assertLoadableByOlga(read(target), "TWO", TWO_PHASE_DENSITY_COLUMNS);
  }

  /**
   * A dead oil has no gas anywhere on the grid, so the gas columns must be extrapolated.
   *
   * @param tempDir JUnit temporary directory
   * @throws IOException if the table cannot be read
   */
  @Test
  void testDeadOilTableHasNoZeroDensities(@TempDir Path tempDir) throws IOException {
    Path target = tempDir.resolve("dead_oil.tab");
    writeTwoPhaseTable(deadOil(), target);
    assertLoadableByOlga(read(target), "TWO", TWO_PHASE_DENSITY_COLUMNS);
  }

  /**
   * A gas, condensate and water fluid must produce a loadable three-phase table.
   *
   * @param tempDir JUnit temporary directory
   * @throws IOException if the table cannot be read
   */
  @Test
  void testThreePhaseTableHasNoZeroDensities(@TempDir Path tempDir) throws IOException {
    Path target = tempDir.resolve("three_phase.tab");
    writeThreePhaseTable(threePhase(), target);
    assertLoadableByOlga(read(target), "THREE", THREE_PHASE_DENSITY_COLUMNS);
  }

  /**
   * A gas and water fluid has no oil phase, which used to shift the water columns onto the oil slot.
   *
   * @param tempDir JUnit temporary directory
   * @throws IOException if the table cannot be read
   */
  @Test
  void testGasWaterTableHasNoZeroDensities(@TempDir Path tempDir) throws IOException {
    Path target = tempDir.resolve("gas_water.tab");
    writeThreePhaseTable(gasWater(), target);
    assertLoadableByOlga(read(target), "THREE", THREE_PHASE_DENSITY_COLUMNS);
  }

  /**
   * The three-phase generator must tolerate a fluid with no water component at all.
   *
   * @param tempDir JUnit temporary directory
   * @throws IOException if the table cannot be read
   */
  @Test
  void testThreePhaseGeneratorAcceptsDryFluid(@TempDir Path tempDir) throws IOException {
    Path target = tempDir.resolve("dry_gas_three_phase.tab");
    writeThreePhaseTable(dryGas(), target);
    assertLoadableByOlga(read(target), "THREE", THREE_PHASE_DENSITY_COLUMNS);
  }

  /**
   * The fluid label has to be settable because the OLGA case refers to it by name.
   *
   * @param tempDir JUnit temporary directory
   * @throws IOException if the table cannot be read
   */
  @Test
  void testFluidLabelIsWrittenToTable(@TempDir Path tempDir) throws IOException {
    Path target = tempDir.resolve("labelled.tab");
    OLGApropertyTableGeneratorKeywordFormat generator = new OLGApropertyTableGeneratorKeywordFormat(gasCondensate());
    generator.setFluidLabel("EXPORTGAS");
    generator.setPressureRange(MIN_PRESSURE_BARA, MAX_PRESSURE_BARA, PRESSURE_STEPS);
    generator.setTemperatureRange(MIN_TEMPERATURE_K, MAX_TEMPERATURE_K, TEMPERATURE_STEPS);
    generator.run();
    generator.writeOLGAinpFile(target.toString());

    String table = read(target);
    assertTrue(table.contains("LABEL = \"EXPORTGAS\""), "The configured fluid label must reach the table");
    assertFalse(table.contains("\"Equation\""), "The EOS key must name the actual equation of state");
  }
}
