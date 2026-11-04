package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion.FixedCellResult;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.util.DepletionPvtHistoryGenerator.NativeResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent dry binary fixed-cell phase-appearance, disappearance and handoff qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DryFixedCellPhaseTransitionTest {
  /**
   * Constructs the declared synthetic unshifted SRK sample without fitting.
   *
   * @param temperature common temperature, K
   * @param methane initial methane mole fraction
   * @param scale total initial moles
   * @return initialized sample
   */
  private SystemInterface fluid(double temperature, double methane, double scale) {
    SystemInterface fluid = new SystemSrkEos(temperature, 100);
    fluid.addComponent("methane", methane * scale);
    fluid.addComponent("n-butane", (1 - methane) * scale);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    return fluid;
  }

  /**
   * Freezes separately labeled checkpoints for numerical consistency, not measured calibration.
   *
   * @param fluid sample to freeze
   * @return immutable original/tuned model
   */
  private CalibratedPvtModel model(SystemInterface fluid) {
    return new CalibratedPvtModel(fluid, SerializationUtils.clone(fluid), "synthetic dry transition sample",
        "original unshifted SRK/classic", "separate identical consistency checkpoint; not an experimental tune",
        "synthetic laboratory control; no reservoir fitting", new String[] {"whole-experiment-control"});
  }

  /**
   * Declares the unchanged separator and final actual product-volume basis.
   *
   * @return immutable two-stage surface process
   */
  private PvtSeparatorPath separator() {
    return new PvtSeparatorPath(new double[] {1.5, 1.01325}, new double[] {303.15, 298.15},
        "synthetic equilibrium separator; final actual volumes at1.01325bara298.15K");
  }

  /**
   * Reads named total component inventories from a native cell.
   *
   * @param fluid native cell
   * @return component amounts, mol
   */
  private double[] inventory(SystemInterface fluid) {
    double[] amounts = new double[fluid.getNumberOfComponents()];
    for (int i = 0; i < amounts.length; i++) {
      amounts[i] = fluid.getComponent(i).getNumberOfmoles();
    }
    return amounts;
  }

  /**
   * Converts reference values to a primitive array.
   *
   * @param values independent numeric array
   * @param scale extensive multiplier
   * @return scaled values
   */
  private double[] numbers(JsonArray values, double scale) {
    double[] result = new double[values.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = values.get(i).getAsDouble() * scale;
    }
    return result;
  }

  /**
   * Computes the largest absolute component error while asserting the frozen bound.
   *
   * @param actual candidate component values
   * @param expected independent values
   * @param bound frozen absolute tolerance
   * @return largest error
   */
  private double compare(double[] actual, double[] expected, double bound) {
    assertArrayEquals(expected, actual, bound);
    double largest = 0;
    for (int i = 0; i < actual.length; i++) {
      largest = Math.max(largest, Math.abs(actual[i] - expected[i]));
    }
    return largest;
  }

  /**
   * Verifies all present surface products preserve every named component.
   *
   * @param products separator output on its declared actual basis
   * @param expected incoming component moles
   */
  private void products(PvtSeparatorPath.Result products, double[] expected) {
    double[][] amounts = products.getComponentMoles();
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], amounts[0][i] + amounts[1][i] + amounts[2][i], 1e-8);
    }
  }

  /**
   * Tests 27 independently predicted paths at three scales and both checkpoint selections.
   *
   * @throws Exception if reference or diagnostic output cannot be read or written
   */
  @Test
  void matchesIndependentTransitionsAndSeparatorInventories() throws Exception {
    JsonArray reference;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/fixed-cell-transitions/reference.json"),
        StandardCharsets.UTF_8)) {
      reference = new Gson().fromJson(reader, JsonArray.class);
    }
    int states = 0;
    int appearances = 0;
    int disappearances = 0;
    double amountError = 0;
    double compositionError = 0;
    double volumeError = 0;
    long started = System.nanoTime();
    for (int index = 0; index < reference.size(); index++) {
      JsonObject test = reference.get(index).getAsJsonObject();
      double temperature = test.get("temperatureK").getAsDouble();
      double methane = test.get("methaneFraction").getAsDouble();
      JsonArray rows = test.getAsJsonArray("rows");
      double[] pressures = new double[rows.size()];
      for (int row = 0; row < rows.size(); row++) {
        pressures[row] = rows.get(row).getAsJsonObject().get("pressureBara").getAsDouble();
      }
      for (double scale : new double[] {.001, 1, 1000}) {
        SystemInterface input = fluid(temperature, methane, scale);
        byte[] before = SerializationUtils.serialize(input);
        CalibratedPvtModel model = model(input);
        PvtSeparatorPath separator = separator();
        for (boolean tuned : new boolean[] {false, true}) {
          String identity = "synthetic whole dry fixed-cell experiment; independent reference case " + index;
          NativeResult result = DepletionPvtHistoryGenerator.replayNativeDryPhaseChangingFixedCell(model, tuned,
              identity, temperature, pressures, separator);
          assertSame(model, result.getModel());
          assertEquals(tuned, result.isTuned());
          assertEquals(identity, result.getPathReference());
          assertSame(separator, result.getSeparatorPath());
          assertTrue(result.getFixedCellReference().allowsDryPhaseChanges());
          assertEquals(pressures[0], result.getFixedCellReference().getReferencePressureBara(), 0);
          DepletionSnapshot[] snapshots = result.getSnapshots();
          boolean previousOil = snapshots[0].getRetainedFluid().hasPhaseType("oil");
          double[] cumulative = new double[2];
          for (int row = 0; row < snapshots.length; row++) {
            states++;
            JsonObject expected = rows.get(row).getAsJsonObject();
            SystemInterface cell = snapshots[row].getRetainedFluid();
            boolean oil = cell.hasPhaseType("oil");
            if (row > 0 && oil != previousOil) {
              if (oil) {
                appearances++;
              } else {
                disappearances++;
              }
            }
            previousOil = oil;
            assertTrue(cell.hasPhaseType("gas"));
            JsonArray phases = expected.getAsJsonArray("phases");
            assertEquals(phases.size(), cell.getNumberOfPhases());
            for (int phase = 0; phase < phases.size(); phase++) {
              JsonObject prediction = phases.get(phase).getAsJsonObject();
              String type = prediction.get("type").getAsString();
              assertTrue(cell.hasPhaseType(type));
              amountError = Math.max(amountError, Math
                  .abs(cell.getPhase(type).getNumberOfMolesInPhase() - scale * prediction.get("moles").getAsDouble()));
              assertEquals(scale * prediction.get("moles").getAsDouble(), cell.getPhase(type).getNumberOfMolesInPhase(),
                  1e-8);
              compositionError = Math.max(compositionError,
                  compare(cell.getPhase(type).getMolarComposition(), numbers(prediction.getAsJsonArray("x"), 1), 1e-8));
            }
            double[] retained = numbers(expected.getAsJsonArray("retainedMoles"), scale);
            double[] withdrawn = snapshots[row].getProducedComponentMoles();
            double[] actual = inventory(cell);
            amountError = Math.max(amountError, compare(actual, retained, 1e-8));
            amountError = Math.max(amountError,
                compare(withdrawn, numbers(expected.getAsJsonArray("withdrawnMoles"), scale), 1e-8));
            double relative = Math
                .abs(cell.getCorrectedVolume() / (scale * expected.get("volumeM3").getAsDouble()) - 1);
            volumeError = Math.max(volumeError, relative);
            assertTrue(relative <= 1e-8);
            for (int i = 0; i < 2; i++) {
              cumulative[i] += withdrawn[i];
              double initial = scale * (i == 0 ? methane : 1 - methane);
              assertEquals(initial, actual[i] + cumulative[i], 1e-10 * Math.max(1, initial));
            }
            products(result.getRetainedProducts(row), actual);
            if (row == 0) {
              assertArrayEquals(new double[2], withdrawn, 0);
              assertFalse(result.hasWithdrawal(row));
            } else {
              assertTrue(result.hasWithdrawal(row));
              products(result.getWithdrawalProducts(row), withdrawn);
            }
          }
        }
        assertArrayEquals(before, SerializationUtils.serialize(input));
      }
    }
    assertEquals(1458, states);
    assertTrue(appearances > 0);
    assertTrue(disappearances > 0);
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("stateExecutions", states);
    metrics.put("oilAppearances", appearances);
    metrics.put("oilDisappearances", disappearances);
    metrics.put("maxAmountErrorMol", amountError);
    metrics.put("maxCompositionError", compositionError);
    metrics.put("maxRelativeVolumeError", volumeError);
    metrics.put("elapsedSeconds", (System.nanoTime() - started) / 1e9);
    Files.write(Paths.get("target/dry-fixed-cell-transition-metrics.json"),
        new Gson().toJson(metrics).getBytes(StandardCharsets.UTF_8));
  }

  /** The named opt-in is reproducible and keeps the unchanged-family default contract intact. */
  @Test
  void restartsIdenticallyAndPreservesProtocolIdentity() {
    SystemInterface input = fluid(275, .95, 1);
    byte[] before = SerializationUtils.serialize(input);
    ConstantVolumeDepletion simulation = new ConstantVolumeDepletion(input);
    double[] pressures = {100, 60, 50, 30, 20, 5, 2};
    FixedCellResult first = simulation.runDryPhaseChangingFixedCellDepletion(275, pressures);
    FixedCellResult repeated = simulation.runDryPhaseChangingFixedCellDepletion(275, pressures);
    assertTrue(first.allowsDryPhaseChanges());
    assertEquals(0, simulation.getDepletionSnapshots().length);
    for (int row = 0; row < pressures.length; row++) {
      assertArrayEquals(first.getSnapshots()[row].getProducedComponentMoles(),
          repeated.getSnapshots()[row].getProducedComponentMoles(), 0);
    }
    assertThrows(IllegalStateException.class, () -> simulation.runFixedCellDepletion(275, pressures));
    assertFalse(simulation.runFixedCellDepletion(275, new double[] {100, 99}).allowsDryPhaseChanges());
    assertThrows(IllegalArgumentException.class,
        () -> simulation.runDryPhaseChangingFixedCellDepletion(275, new double[] {100, 60, 61}));
    assertArrayEquals(before, SerializationUtils.serialize(input));
    FixedCellResult serialized = SerializationUtils.clone(first);
    assertTrue(serialized.allowsDryPhaseChanges());
    assertEquals(first.getReferenceVolumeM3(), serialized.getReferenceVolumeM3(), 0);
    DepletionSnapshot[] exposed = first.getSnapshots();
    exposed[0] = null;
    assertTrue(first.getSnapshots()[0] != null);
  }

  /** Gas-only tail refinement changes no final inventory or composition and creates no oil. */
  @Test
  void gasOnlyTailHasAnIndependentPressureGridLimit() {
    ConstantVolumeDepletion simulation = new ConstantVolumeDepletion(fluid(275, .95, 1));
    FixedCellResult coarse = simulation.runDryPhaseChangingFixedCellDepletion(275,
        new double[] {100, 60, 50, 30, 20, 5, 2});
    FixedCellResult refined = simulation.runDryPhaseChangingFixedCellDepletion(275,
        new double[] {100, 60, 50, 30, 20, 10, 5, 3, 2});
    SystemInterface a = coarse.getSnapshots()[6].getRetainedFluid();
    SystemInterface b = refined.getSnapshots()[8].getRetainedFluid();
    assertFalse(a.hasPhaseType("oil"));
    assertFalse(b.hasPhaseType("oil"));
    assertArrayEquals(inventory(a), inventory(b), 1e-10);
    assertArrayEquals(a.getMolarComposition(), b.getMolarComposition(), 1e-10);
  }

  /** Wet inputs and absent-gas states remain explicit failures without changing the supplied sample. */
  @Test
  void unsupportedDomainsRemainTransactional() {
    SystemInterface wet = fluid(275, .95, 1);
    wet.addComponent("water", .01);
    wet.setMixingRule("classic");
    wet.init(0);
    byte[] before = SerializationUtils.serialize(wet);
    assertThrows(IllegalArgumentException.class,
        () -> new ConstantVolumeDepletion(wet).runDryPhaseChangingFixedCellDepletion(275, new double[] {100, 50, 2}));
    assertThrows(IllegalArgumentException.class,
        () -> DepletionPvtHistoryGenerator.replayNativeDryPhaseChangingFixedCell(model(wet), false,
            "unsupported wet phase-changing experiment", 275, new double[] {100, 50, 2}, separator()));
    assertArrayEquals(before, SerializationUtils.serialize(wet));
    SystemInterface oil = fluid(275, .01, 1);
    byte[] oilBefore = SerializationUtils.serialize(oil);
    assertThrows(IllegalStateException.class,
        () -> new ConstantVolumeDepletion(oil).runDryPhaseChangingFixedCellDepletion(275, new double[] {100, 50, 2}));
    assertArrayEquals(oilBefore, SerializationUtils.serialize(oil));
  }

  /**
   * Compiles and executes the exact complete guide example using Java 8 source syntax.
   *
   * @throws Exception if the marked guide source cannot be compiled or executed
   */
  @Test
  void literalGuideExampleCompilesAndRuns() throws Exception {
    String guide = new String(Files.readAllBytes(Paths.get("docs/pvtsimulation/depletion_pvt_generation.md")),
        StandardCharsets.UTF_8);
    String marker = "<!-- dry-fixed-cell-example -->";
    assertEquals(guide.indexOf(marker), guide.lastIndexOf(marker));
    assertTrue(guide.indexOf(marker) >= 0);
    Matcher fence = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL)
        .matcher(guide.substring(guide.indexOf(marker) + marker.length()));
    assertTrue(fence.find());
    String source = fence.group(1);
    java.nio.file.Path directory = Files.createTempDirectory("dry-fixed-cell-guide-");
    java.nio.file.Path file = directory.resolve("DryFixedCellGuideExample.java");
    Files.write(file, source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null, "A JDK is required for the literal documentation example");
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      assertTrue(
          compiler.getTask(diagnostics, manager, null,
              Arrays.asList("-source", "8", "-target", "8", "-classpath", System.getProperty("java.class.path"), "-d",
                  directory.toString()),
              null, manager.getJavaFileObjects(file.toFile())).call(),
          diagnostics.toString());
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.toUri().toURL()},
        getClass().getClassLoader())) {
      NativeResult result = (NativeResult) loader.loadClass("DryFixedCellGuideExample").getMethod("run").invoke(null);
      assertTrue(result.getFixedCellReference().allowsDryPhaseChanges());
      assertEquals(7, result.getSnapshots().length);
      assertFalse(result.getSnapshots()[0].getRetainedFluid().hasPhaseType("oil"));
      assertTrue(result.getSnapshots()[1].getRetainedFluid().hasPhaseType("oil"));
      assertTrue(result.getSnapshots()[2].getRetainedFluid().hasPhaseType("oil"));
      assertFalse(result.getSnapshots()[3].getRetainedFluid().hasPhaseType("oil"));
      assertFalse(result.hasWithdrawal(0));
      assertTrue(result.hasWithdrawal(6));
    }
  }

  /**
   * Approaches the independently solved noncritical liquid-disappearance boundary from both sides.
   *
   * @throws Exception if the independent limit reference cannot be read
   */
  @Test
  void liquidAmountTendsToZeroWithoutAbsentPhaseExtrapolation() throws Exception {
    JsonArray cases;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/fixed-cell-transitions/limit-reference.json"),
        StandardCharsets.UTF_8)) {
      cases = new Gson().fromJson(reader, JsonArray.class);
    }
    assertEquals(6, cases.size());
    double previousOilMoles = Double.POSITIVE_INFINITY;
    for (int index = 0; index < cases.size(); index++) {
      JsonObject test = cases.get(index).getAsJsonObject();
      JsonObject expected = test.getAsJsonArray("rows").get(1).getAsJsonObject();
      double pressure = expected.get("pressureBara").getAsDouble();
      FixedCellResult result = new ConstantVolumeDepletion(fluid(275, .95, 1))
          .runDryPhaseChangingFixedCellDepletion(275, new double[] {60, pressure});
      DepletionSnapshot row = result.getSnapshots()[1];
      SystemInterface cell = row.getRetainedFluid();
      assertTrue(Math.abs(cell.getCorrectedVolume() / expected.get("volumeM3").getAsDouble() - 1) <= 1e-8);
      compare(inventory(cell), numbers(expected.getAsJsonArray("retainedMoles"), 1), 1e-8);
      compare(row.getProducedComponentMoles(), numbers(expected.getAsJsonArray("withdrawnMoles"), 1), 1e-8);
      JsonArray phases = expected.getAsJsonArray("phases");
      assertEquals(phases.size(), cell.getNumberOfPhases());
      for (int phase = 0; phase < phases.size(); phase++) {
        JsonObject prediction = phases.get(phase).getAsJsonObject();
        String type = prediction.get("type").getAsString();
        assertEquals(prediction.get("moles").getAsDouble(), cell.getPhase(type).getNumberOfMolesInPhase(), 1e-8);
        compare(cell.getPhase(type).getMolarComposition(), numbers(prediction.getAsJsonArray("x"), 1), 1e-8);
      }
      if (test.get("offsetBara").getAsDouble() < 0) {
        assertFalse(cell.hasPhaseType("oil"));
        assertArrayEquals(new double[] {.95, .05}, cell.getMolarComposition(), 1e-10);
      } else {
        assertTrue(cell.hasPhaseType("oil"));
        double oilMoles = cell.getPhase("oil").getNumberOfMolesInPhase();
        assertTrue(oilMoles > 0 && oilMoles < previousOilMoles);
        previousOilMoles = oilMoles;
      }
    }
    assertTrue(previousOilMoles < 1e-5);
  }

}
