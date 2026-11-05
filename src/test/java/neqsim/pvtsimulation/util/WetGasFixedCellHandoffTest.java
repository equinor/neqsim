package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent synthetic gas/aqueous transition and immutable handoff qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WetGasFixedCellHandoffTest {
  /**
   * Constructs the declared classic unshifted SRK binary without tuning.
   *
   * @param temperature temperature, K
   * @param water initial water mole fraction
   * @param scale initial sample moles
   * @return fresh unflashed sample
   */
  private SystemInterface sample(double temperature, double water, double scale) {
    SystemInterface fluid = new SystemSrkEos(temperature, 200);
    fluid.addComponent("methane", (1 - water) * scale);
    fluid.addComponent("water", water * scale);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    return fluid;
  }

  /**
   * Checks each separator's named retained/withdrawn component totals.
   *
   * @param result completed separator products
   * @param expected independently predicted component totals
   */
  private void products(PvtSeparatorPath.Result result, double[] expected) {
    double[][] phases = result.getComponentMoles();
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], phases[0][i] + phases[1][i] + phases[2][i], 1e-8);
    }
    assertEquals(0, result.getVolumesM3()[1], 0);
  }

  /**
   * Compares all27 paths and both immutable checkpoint choices against the independent SRK reference.
   *
   * @throws Exception if complete source/diagnostic files cannot be accessed
   */
  @Test
  void independentWetGasMatrixPreservesEveryDeclaredPath() throws Exception {
    JsonArray cases;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/wet-gas-fixed-cell/reference.json"),
        StandardCharsets.UTF_8)) {
      cases = new Gson().fromJson(reader, JsonArray.class);
    }
    assertEquals(27, cases.size());
    List<Map<String, Object>> diagnostics = new ArrayList<Map<String, Object>>();
    List<String> failures = new ArrayList<String>();
    for (int index = 0; index < cases.size(); index++) {
      JsonObject expected = cases.get(index).getAsJsonObject();
      for (boolean selected : new boolean[] {false, true}) {
        Map<String, Object> diagnostic = new LinkedHashMap<String, Object>();
        diagnostic.put("case", index);
        diagnostic.put("tuned", selected);
        diagnostics.add(diagnostic);
        try {
          compare(expected, selected, diagnostic);
          diagnostic.put("status", "pass");
        } catch (AssertionError | RuntimeException ex) {
          String error = index + "/" + selected + ": " + ex.toString();
          failures.add(error);
          diagnostic.put("status", "fail");
          diagnostic.put("error", error);
        }
      }
    }
    Files.write(Paths.get("target", "wet-gas-fixed-cell-metrics.json"),
        new Gson().toJson(diagnostics).getBytes(StandardCharsets.UTF_8));
    assertTrue(failures.isEmpty(), failures.toString());
  }

  /**
   * Audits a full independently predicted path without absent-phase property access.
   *
   * @param expected complete independent reference path
   * @param selected tuned checkpoint selection
   * @param metrics diagnostic metrics retained even when a comparison fails
   */
  private void compare(JsonObject expected, boolean selected, Map<String, Object> metrics) {
    double temperature = expected.get("temperatureK").getAsDouble();
    double water = expected.get("waterMoleFraction").getAsDouble();
    double scale = expected.get("sampleMoles").getAsDouble();
    SystemInterface input = sample(temperature, water, scale);
    CalibratedPvtModel model = new CalibratedPvtModel(input, SerializationUtils.clone(input),
        "synthetic methane/water reference", "original SRK/classic", "separate SRK consistency checkpoint",
        "independent numerical control; not measured calibration", new String[] {"synthetic-reference"});
    byte[] original = SerializationUtils.serialize(model.getOriginalFluid());
    byte[] tuned = SerializationUtils.serialize(model.getTunedFluid());
    JsonArray rows = expected.getAsJsonArray("rows");
    assertEquals(10, rows.size());
    metrics.put("temperatureK", temperature);
    metrics.put("waterMoleFraction", water);
    metrics.put("sampleMoles", scale);
    List<Map<String, Object>> completed = new ArrayList<Map<String, Object>>();
    metrics.put("rows", completed);
    double[] pressures = new double[rows.size()];
    for (int i = 0; i < pressures.length; i++) {
      pressures[i] = rows.get(i).getAsJsonObject().get("pressureBara").getAsDouble();
    }
    PvtSeparatorPath separator = new PvtSeparatorPath(new double[] {1.01325}, new double[] {298.15},
        "synthetic one-stage separator; actual products1.01325bara298.15K");
    DepletionPvtHistoryGenerator.NativeResult result = DepletionPvtHistoryGenerator.replayNativeWetGasFixedCell(model,
        selected, "synthetic gas/aqueous initial-occupied-volume path; no wet upper-dew inference", temperature,
        pressures, separator);
    assertTrue(result.hasFixedCellReference());
    assertTrue(result.getFixedCellReference().allowsWetGasPhaseChanges());
    assertFalse(result.getFixedCellReference().allowsDryPhaseChanges());
    assertEquals(selected, result.isTuned());
    double target = expected.get("targetVolumeM3").getAsDouble();
    assertEquals(1, result.getFixedCellReference().getReferenceVolumeM3() / target, 1e-8);
    double[] cumulative = new double[2];
    double maxAmount = 0;
    double maxVolume = 0;
    double maxInternalVolume = 0;
    double maxComposition = 0;
    double maxClosure = 0;
    for (int row = 0; row < rows.size(); row++) {
      JsonObject reference = rows.get(row).getAsJsonObject();
      DepletionSnapshot snapshot = result.getSnapshots()[row];
      SystemInterface cell = snapshot.getRetainedFluid();
      String[] names = new Gson().fromJson(reference.get("phaseNames"), String[].class);
      assertEquals(names.length, cell.getNumberOfPhases());
      double[] removed = snapshot.getProducedComponentMoles();
      double[] retained = new double[2];
      double[] expectedRemoved = new double[2];
      for (int component = 0; component < 2; component++) {
        retained[component] = reference.getAsJsonArray("retainedMoles").get(component).getAsDouble();
        expectedRemoved[component] = reference.getAsJsonArray("withdrawnMoles").get(component).getAsDouble();
        maxAmount = Math.max(maxAmount,
            Math.abs(retained[component] - cell.getComponent(component).getNumberOfmoles()));
        maxAmount = Math.max(maxAmount, Math.abs(expectedRemoved[component] - removed[component]));
        cumulative[component] += removed[component];
        double initial = (component == 0 ? 1 - water : water) * scale;
        maxClosure = Math.max(maxClosure,
            Math.abs(initial - cell.getComponent(component).getNumberOfmoles() - cumulative[component]));
        assertEquals(initial, cell.getComponent(component).getNumberOfmoles() + cumulative[component],
            1e-10 * Math.max(1, initial));
        if (row == 0) {
          assertEquals(0, removed[component], 0);
        }
      }
      double[] phaseMoles = new double[names.length];
      double[][] phaseCompositions = new double[names.length][2];
      for (int phase = 0; phase < names.length; phase++) {
        assertTrue(cell.hasPhaseType(names[phase]));
        phaseMoles[phase] = cell.getPhase(names[phase]).getNumberOfMolesInPhase();
        maxAmount = Math.max(maxAmount, Math.abs(cell.getPhase(names[phase]).getNumberOfMolesInPhase()
            - reference.getAsJsonArray("phaseMoles").get(phase).getAsDouble()));
        for (int component = 0; component < 2; component++) {
          phaseCompositions[phase][component] = cell.getPhase(names[phase]).getComponent(component).getx();
          maxComposition = Math.max(maxComposition, Math.abs(phaseCompositions[phase][component] - reference
              .getAsJsonArray("phaseCompositions").get(phase).getAsJsonArray().get(component).getAsDouble()));
          assertEquals(
              reference.getAsJsonArray("phaseCompositions").get(phase).getAsJsonArray().get(component).getAsDouble(),
              cell.getPhase(names[phase]).getComponent(component).getx(), 1e-8);
        }
      }
      maxVolume = Math.max(maxVolume, Math.abs(cell.getCorrectedVolume() / target - 1));
      maxInternalVolume = Math.max(maxInternalVolume,
          Math.abs(cell.getCorrectedVolume() / result.getFixedCellReference().getReferenceVolumeM3() - 1));
      Map<String, Object> actual = new LinkedHashMap<String, Object>();
      actual.put("pressureBara", cell.getPressure());
      actual.put("phaseNames", names);
      actual.put("phaseMoles", phaseMoles);
      actual.put("phaseCompositions", phaseCompositions);
      actual.put("retainedMoles",
          new double[] {cell.getComponent(0).getNumberOfmoles(), cell.getComponent(1).getNumberOfmoles()});
      actual.put("withdrawnMoles", removed);
      actual.put("occupiedVolumeM3", cell.getCorrectedVolume());
      actual.put("retainedSeparatorComponentMoles", result.getRetainedProducts(row).getComponentMoles());
      actual.put("retainedSeparatorVolumesM3", result.getRetainedProducts(row).getVolumesM3());
      if (row > 0) {
        actual.put("withdrawnSeparatorComponentMoles", result.getWithdrawalProducts(row).getComponentMoles());
        actual.put("withdrawnSeparatorVolumesM3", result.getWithdrawalProducts(row).getVolumesM3());
      }
      completed.add(actual);
      products(result.getRetainedProducts(row), retained);
      assertEquals(row > 0, result.hasWithdrawal(row));
      if (row > 0) {
        products(result.getWithdrawalProducts(row), expectedRemoved);
      }
    }
    metrics.put("maxAmountErrorMol", maxAmount);
    metrics.put("maxRelativeVolumeError", maxVolume);
    metrics.put("maxInternalVolumeResidual", maxInternalVolume);
    metrics.put("maxCompositionError", maxComposition);
    metrics.put("maxComponentClosureMol", maxClosure);
    assertTrue(maxInternalVolume <= 1e-12);
    assertTrue(maxAmount <= 1e-8);
    assertTrue(maxVolume <= 1e-8);
    assertArrayEquals(original, SerializationUtils.serialize(model.getOriginalFluid()));
    assertArrayEquals(tuned, SerializationUtils.serialize(model.getTunedFluid()));
  }

  /** Rejects oil and missing gas transactionally while preserving the existing fixed-family contract. */
  @Test
  void rejectsOilAndMissingGasWithoutMutatingCaller() {
    SystemInterface wet = sample(440, .1, 1);
    byte[] before = SerializationUtils.serialize(wet);
    assertThrows(IllegalStateException.class,
        () -> new ConstantVolumeDepletion(wet).runFixedCellDepletion(440, new double[] {200, 150, 100, 70, 50}));
    assertArrayEquals(before, SerializationUtils.serialize(wet));
    SystemInterface water = sample(350, 1, 1);
    byte[] waterBefore = SerializationUtils.serialize(water);
    assertThrows(IllegalStateException.class,
        () -> new ConstantVolumeDepletion(water).runWetGasFixedCellDepletion(350, new double[] {200, 100}));
    assertArrayEquals(waterBefore, SerializationUtils.serialize(water));
    SystemInterface oil = sample(350, .2, 1);
    oil.addComponent("n-decane", .2);
    oil.init(0);
    byte[] oilBefore = SerializationUtils.serialize(oil);
    assertThrows(IllegalStateException.class,
        () -> new ConstantVolumeDepletion(oil).runWetGasFixedCellDepletion(350, new double[] {200, 100}));
    assertArrayEquals(oilBefore, SerializationUtils.serialize(oil));
  }

  /** Registered zero water has the same pure-gas limit without invented aqueous properties. */
  @Test
  void registeredZeroWaterRetainsPureGasLimit() {
    for (double temperature : new double[] {440, 450, 460}) {
      for (double scale : new double[] {.001, 1, 1000}) {
        SystemInterface binary = sample(temperature, 0, scale);
        SystemInterface methane = new SystemSrkEos(temperature, 200);
        methane.addComponent("methane", scale);
        methane.setMixingRule("classic");
        methane.useVolumeCorrection(false);
        methane.setMultiPhaseCheck(true);
        methane.init(0);
        double[] pressures = {200, 150, 100, 70, 50, 30, 20, 10, 5, 2};
        ConstantVolumeDepletion.FixedCellResult actual = new ConstantVolumeDepletion(binary)
            .runWetGasFixedCellDepletion(temperature, pressures);
        ConstantVolumeDepletion.FixedCellResult expected = new ConstantVolumeDepletion(methane)
            .runWetGasFixedCellDepletion(temperature, pressures);
        assertEquals(1, actual.getReferenceVolumeM3() / expected.getReferenceVolumeM3(), 1e-10);
        for (int row = 0; row < pressures.length; row++) {
          SystemInterface cell = actual.getSnapshots()[row].getRetainedFluid();
          assertEquals(1, cell.getNumberOfPhases());
          assertTrue(cell.hasPhaseType("gas"));
          assertEquals(0, cell.getComponent("water").getNumberOfmoles(), 0);
          assertEquals(0, actual.getSnapshots()[row].getProducedComponentMoles()[1], 0);
          assertEquals(expected.getSnapshots()[row].getRetainedFluid().getTotalNumberOfMoles(),
              cell.getTotalNumberOfMoles(), 1e-10 * Math.max(1, scale));
        }
      }
    }
  }

  /** Distinct translated checkpoints and serialized protocol evidence remain separately selected. */
  @Test
  void distinctCheckpointVolumesAndSerializedProtocolRemainImmutable() {
    SystemInterface original = sample(450, .08, 1);
    SystemInterface translated = SerializationUtils.clone(original);
    translated.useVolumeCorrection(true);
    CalibratedPvtModel model = new CalibratedPvtModel(original, translated, "synthetic same-sample control",
        "unshifted original", "independently declared translated consistency checkpoint",
        "synthetic volume-basis selection, not measured tuning", new String[] {"numerical-control"});
    byte[] before = SerializationUtils.serialize(model);
    PvtSeparatorPath separator = new PvtSeparatorPath(new double[] {1.01325}, new double[] {298.15},
        "same actual product-volume basis");
    double[] pressures = {200, 100, 50, 20};
    DepletionPvtHistoryGenerator.NativeResult first = DepletionPvtHistoryGenerator.replayNativeWetGasFixedCell(model,
        false, "unshifted wet-gas fixed cell", 450, pressures, separator);
    DepletionPvtHistoryGenerator.NativeResult second = DepletionPvtHistoryGenerator.replayNativeWetGasFixedCell(model,
        true, "translated wet-gas fixed cell", 450, pressures, separator);
    assertTrue(Math.abs(first.getFixedCellReference().getReferenceVolumeM3()
        - second.getFixedCellReference().getReferenceVolumeM3()) > 1e-12);
    DepletionPvtHistoryGenerator.NativeResult restored = SerializationUtils.clone(second);
    assertTrue(restored.isTuned());
    assertTrue(restored.getFixedCellReference().allowsWetGasPhaseChanges());
    assertFalse(restored.getFixedCellReference().allowsDryPhaseChanges());
    assertArrayEquals(second.getSnapshots()[3].getProducedComponentMoles(),
        restored.getSnapshots()[3].getProducedComponentMoles(), 0);
    assertArrayEquals(before, SerializationUtils.serialize(model));
    DepletionSnapshot[] changed = restored.getSnapshots();
    changed[0] = null;
    assertTrue(restored.getSnapshots()[0] != null);
    assertThrows(IllegalArgumentException.class,
        () -> new ConstantVolumeDepletion(original).runWetGasFixedCellDepletion(450, new double[] {200, 200}));
    assertArrayEquals(before, SerializationUtils.serialize(model));
  }

}
