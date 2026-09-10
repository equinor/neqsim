package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Ensures legacy process-format methods return clearly labelled diagnostics, never deck input. */
class ProcessScreeningDiagnosticExportTest {
  @TempDir
  Path tempDir;

  @Test
  void multiScenarioDiagnosticsRetainResultAxesUnitsAndFeasibility() throws IOException {
    MultiScenarioVFPGenerator generator = generatedScreening();
    String diagnostic = generator.toDiagnosticString();
    assertDiagnosticOnly(diagnostic);
    assertTrue(diagnostic.contains("rate [kg/hr]"));
    List<String[]> rows = dataRows(diagnostic);
    assertEquals(4, rows.size());
    for (int i = 0; i < rows.size(); i++) {
      String[] row = rows.get(i);
      assertEquals(6, row.length);
      assertEquals(i < 2 ? 10.0 : 20.0, Double.parseDouble(row[0]), 1e-10);
      assertEquals(i % 2 == 0 ? 10.0 : 40.0, Double.parseDouble(row[1]), 1e-10);
      assertEquals(0.0, Double.parseDouble(row[2]), 1e-10);
      assertEquals(80.0, Double.parseDouble(row[3]), 1e-10);
      if (i % 2 == 0) {
        assertEquals("true", row[5]);
        assertEquals(10.0, Double.parseDouble(row[4]), 0.2);
      } else {
        assertEquals("false", row[5]);
        assertTrue(Double.isNaN(Double.parseDouble(row[4])));
      }
    }
    // Formatting uses the saved result axes, not mutable settings for the next run.
    generator.setFlowRates(new double[] { 999.0 });
    generator.setOutletPressures(new double[] { 999.0 });
    generator.setWaterCuts(new double[] { 0.9 });
    generator.setGORs(new double[] { 999.0 });
    generator.setFlowRateUnit("Sm3/s");
    assertEquals(diagnostic, generator.toDiagnosticString());
    assertEquals(diagnostic, generator.toVFPEXPString(17));
    Path file = tempDir.resolve("legacy-screening.txt");
    generator.exportVFPEXP(file, 17);
    assertEquals(diagnostic, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    generator.exportVFPEXP(file.toString(), 23);
    assertEquals(diagnostic, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
  }

  @Test
  void missingMultiScenarioResultsFailBeforeOpeningTheFile() throws IOException {
    MultiScenarioVFPGenerator generator = new MultiScenarioVFPGenerator(ProcessSystem::new, "feed", "outlet");
    assertThrows(IllegalStateException.class, generator::toDiagnosticString);
    assertThrows(IllegalStateException.class, () -> generator.toVFPEXPString(1));
    Path file = tempDir.resolve("existing.txt");
    byte[] original = "existing results\n".getBytes(StandardCharsets.UTF_8);
    Files.write(file, original);
    assertThrows(IllegalStateException.class, () -> generator.exportVFPEXP(file, 1));
    assertArrayEquals(original, Files.readAllBytes(file));
  }

  @Test
  void pressureBoundaryDiagnosticsPreserveEveryCoordinateAndUnavailableValue() {
    PressureBoundaryOptimizer.LiftCurveTable table = new PressureBoundaryOptimizer.LiftCurveTable("capacity",
        new double[] { 40.0, 50.0 }, new double[] { 20.0, 30.0 },
        new double[][] { { 400.25, Double.NaN }, { 350.5, 500.75 } },
        new double[][] { { 10.25, Double.NaN }, { 20.5, 30.75 } },
        new String[][] { { "compressor", "unavailable" }, { "pipe", "valve" } }, "bara", "kg/hr");
    String diagnostic = table.toDiagnosticTable();
    assertDiagnosticOnly(diagnostic);
    assertEquals(diagnostic, table.toEclipseFormat());
    assertTrue(diagnostic.contains("maximum flow [kg/hr]"));
    List<String[]> rows = dataRows(diagnostic);
    assertEquals(4, rows.size());
    assertArrayEquals(new String[] { "40.0", "20.0", "400.25", "10.25", "compressor", "true" }, rows.get(0));
    assertArrayEquals(new String[] { "40.0", "30.0", "NaN", "NaN", "unavailable", "false" }, rows.get(1));
    assertArrayEquals(new String[] { "50.0", "20.0", "350.5", "20.5", "pipe", "true" }, rows.get(2));
    assertArrayEquals(new String[] { "50.0", "30.0", "500.75", "30.75", "valve", "true" }, rows.get(3));
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      assertEquals(diagnostic, table.toDiagnosticTable());
    } finally {
      Locale.setDefault(previous);
    }
  }

  private static void assertDiagnosticOnly(String text) {
    assertTrue(text.contains("diagnostic text only"));
    for (String misleading : new String[] { "VFPPROD", "VFPEXP", "BHP", "THP", "1*" }) {
      assertFalse(text.contains(misleading), "Diagnostic output must not contain " + misleading);
    }
  }

  private static List<String[]> dataRows(String diagnostic) {
    List<String[]> rows = new ArrayList<>();
    for (String line : diagnostic.split("\n")) {
      if (!line.startsWith("#") && !line.trim().isEmpty()) {
        rows.add(line.split("\t", -1));
      }
    }
    rows.remove(0);
    return rows;
  }

  private static MultiScenarioVFPGenerator generatedScreening() {
    SystemSrkEos reference = new SystemSrkEos(288.15, 1.01325);
    reference.addComponent("methane", 0.5);
    reference.addComponent("n-heptane", 0.5);
    reference.setMixingRule("classic");
    reference.setMultiPhaseCheck(true);
    FluidMagicInput input = FluidMagicInput.fromFluid(reference);
    input.separateToStandardConditions();
    MultiScenarioVFPGenerator generator = new MultiScenarioVFPGenerator(() -> {
      ProcessSystem process = new ProcessSystem();
      process.add(new Stream("feed", reference.clone()));
      return process;
    }, "feed", "feed");
    generator.setFlashGenerator(new RecombinationFlashGenerator(input));
    generator.setFlowRateUnit("kg/hr");
    generator.setFlowRates(new double[] { 10.0, 20.0 });
    generator.setOutletPressures(new double[] { 10.0, 40.0 });
    generator.setWaterCuts(new double[] { 0.0 });
    generator.setGORs(new double[] { 80.0 });
    generator.setMinInletPressure(5.0);
    generator.setMaxInletPressure(30.0);
    generator.setPressureTolerance(0.1);
    generator.setEnableParallel(false);
    generator.generateVFPTable();
    return generator;
  }
}
