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
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the OLGA hydrate curve export.
 *
 * <p>
 * OLGA does not compute hydrate thermodynamics; it interpolates a tabulated equilibrium curve. The syntax it accepts
 * was established against the OLGA 2025.1 rules engine:
 * </p>
 *
 * <pre>
 * HYDRATECURVE LABEL = "HYD", PRESSURE = (...) bara, TEMPERATURE = (...) C
 * </pre>
 *
 * <p>
 * with the flowpath referring to the curve by label through {@code HYDRATECHECK HYDRATECURVE="HYD"}. These tests pin
 * the format and the physical shape of the curve.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class OLGAhydrateCurveGeneratorTest {

  /**
   * Build a wet gas that forms hydrates.
   *
   * @return a NeqSim fluid with free water
   */
  private SystemInterface wetGas() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 60.0);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("water", 0.06);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Build the generator over a modest pressure range.
   *
   * @return a generator that has not yet been run
   */
  private OLGAhydrateCurveGenerator generator() {
    OLGAhydrateCurveGenerator generator = new OLGAhydrateCurveGenerator(wetGas());
    generator.setPressureRange(20.0, 150.0, 6);
    return generator;
  }

  /** The hydrate curve must rise with pressure and stay in a physical range. */
  @Test
  void testCurveIsPhysical() {
    OLGAhydrateCurveGenerator generator = generator();
    generator.run();

    double[] pressures = generator.getCurvePressures();
    double[] temperatures = generator.getCurveTemperatures();
    assertEquals(pressures.length, temperatures.length, "Every pressure needs a temperature");
    assertTrue(pressures.length >= 4, "Most of the requested points should converge, got " + pressures.length);

    for (int i = 0; i < temperatures.length; i++) {
      assertTrue(temperatures[i] > -30.0 && temperatures[i] < 35.0,
          "Hydrate temperature " + temperatures[i] + " C at " + pressures[i] + " bara is not physical");
    }
    for (int i = 1; i < temperatures.length; i++) {
      assertTrue(temperatures[i] > temperatures[i - 1], "The hydrate curve must rise with pressure, but " + pressures[i]
          + " bara gave " + temperatures[i] + " C after " + temperatures[i - 1] + " C");
    }
  }

  /** A fluid without water cannot form hydrates and must say so. */
  @Test
  void testDryFluidIsRejected() {
    SystemInterface dry = new SystemSrkEos(273.15 + 20.0, 60.0);
    dry.addComponent("methane", 0.95);
    dry.addComponent("ethane", 0.05);
    dry.setMixingRule("classic");

    OLGAhydrateCurveGenerator generator = new OLGAhydrateCurveGenerator(dry);
    generator.setPressureRange(20.0, 150.0, 4);
    IllegalStateException thrown = assertThrows(IllegalStateException.class, generator::run);
    assertTrue(thrown.getMessage().contains("water"), "The message must point at the missing water component");
  }

  /** An unusable pressure range must be rejected up front. */
  @Test
  void testInvalidPressureRangeIsRejected() {
    OLGAhydrateCurveGenerator generator = new OLGAhydrateCurveGenerator(wetGas());
    assertThrows(IllegalArgumentException.class, () -> generator.setPressureRange(20.0, 150.0, 1));
    assertThrows(IllegalArgumentException.class, () -> generator.setPressureRange(0.0, 150.0, 5));
    assertThrows(IllegalArgumentException.class, () -> generator.setPressureRange(150.0, 20.0, 5));
  }

  /** Running is required before the curve can be written. */
  @Test
  void testWriteBeforeRunIsRejected(@TempDir Path tempDir) {
    OLGAhydrateCurveGenerator generator = generator();
    Path target = tempDir.resolve("curve.inp");
    assertThrows(Exception.class, () -> generator.writeOLGAinpFile(target.toString()));
  }

  /**
   * The written block must match the syntax the OLGA rules engine accepts.
   *
   * @param tempDir JUnit temporary directory
   * @throws Exception if the file cannot be written or read
   */
  @Test
  void testKeywordBlockMatchesOlgaSyntax(@TempDir Path tempDir) throws Exception {
    OLGAhydrateCurveGenerator generator = generator();
    generator.setCurveLabel("LINNORM_HYD");
    generator.run();

    Path target = tempDir.resolve("curve.inp");
    generator.writeOLGAinpFile(target.toString());
    String block = read(target);

    assertTrue(block.contains("HYDRATECURVE LABEL = \"LINNORM_HYD\""), "Curve label must be written");
    assertTrue(block.contains("PRESSURE = ("), "PRESSURE key is mandatory");
    assertTrue(block.contains(") bara"), "OLGA needs the pressure unit");
    assertTrue(block.contains("TEMPERATURE = ("), "TEMPERATURE key is mandatory");
    assertTrue(block.contains(") C"), "OLGA needs the temperature unit");
    assertFalse(block.contains("NaN"), "A NaN would move the hydrate boundary silently");
    assertFalse(block.matches("(?s).*\\dE[-+]?\\d.*"), "Values must be plain fixed point, not exponent notation");

    assertEquals(" HYDRATECHECK HYDRATECURVE=\"LINNORM_HYD\"", generator.getHydrateCheckKeyword(),
        "The flowpath line must refer to the curve by its label");
  }

  /** The generator must not disturb the caller's fluid. */
  @Test
  void testCallerFluidIsUntouched() {
    SystemInterface fluid = wetGas();
    double pressureBefore = fluid.getPressure();
    double temperatureBefore = fluid.getTemperature();

    OLGAhydrateCurveGenerator generator = new OLGAhydrateCurveGenerator(fluid);
    generator.setPressureRange(20.0, 150.0, 5);
    generator.run();

    assertEquals(pressureBefore, fluid.getPressure(), 1.0e-9, "The caller's pressure must not change");
    assertEquals(temperatureBefore, fluid.getTemperature(), 1.0e-9, "The caller's temperature must not change");
  }

  /**
   * Read a written file.
   *
   * @param target file to read
   * @return file content
   * @throws IOException if the file cannot be read
   */
  private String read(Path target) throws IOException {
    assertTrue(Files.exists(target), "The generator wrote no file to " + target);
    return new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
  }
}
