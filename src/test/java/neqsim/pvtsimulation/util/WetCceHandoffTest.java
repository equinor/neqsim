package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.util.DepletionPvtHistoryGenerator.NativeResult;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Closed-mass wet CCE qualification without wet withdrawal or black-oil extrapolation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WetCceHandoffTest {
  /**
   * Freezes an explicitly synthetic wet sample and a separate volume-corrected checkpoint.
   *
   * @param methane methane amount in mol
   * @param decane decane amount in mol
   * @return immutable declared checkpoints
   */
  private CalibratedPvtModel model(double methane, double decane) {
    SystemInterface original = new SystemSrkEos(350, 300);
    if (methane > 0) {
      original.addComponent("methane", methane);
    }
    if (decane > 0) {
      original.addComponent("n-decane", decane);
    }
    original.addComponent("water", 1 - methane - decane);
    original.setMixingRule("classic");
    original.setMultiPhaseCheck(true);
    original.useVolumeCorrection(false);
    original.init(0);
    SystemInterface tuned = SerializationUtils.clone(original);
    tuned.useVolumeCorrection(true);
    return new CalibratedPvtModel(original, tuned, "synthetic wet sample", "unshifted SRK", "shifted SRK",
        "declared synthetic variants; no measured fit", new String[] {"wet-calibration"});
  }

  /**
   * Defines a fixed separator train with explicit aqueous products.
   *
   * @return immutable surface basis
   */
  private PvtSeparatorPath separator() {
    return new PvtSeparatorPath(new double[] {10, 1.01325}, new double[] {303.15, 298.15},
        "synthetic final basis 25 C / 101.325 kPa absolute");
  }

  /** Wet CCE preserves every named component and both checkpoints through cell and separator. */
  @Test
  void wetClosedMassPreservesAqueousProductsAndImmutableProvenance() {
    CalibratedPvtModel model = model(.5, 0);
    for (boolean tuned : new boolean[] {false, true}) {
      NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, tuned, Path.CCE, "whole wet CCE holdout",
          350, new double[] {300, 150, 50}, separator());
      assertEquals(tuned, result.isTuned());
      assertEquals("whole wet CCE holdout", result.getPathReference());
      for (int row = 0; row < result.getSnapshots().length; row++) {
        DepletionSnapshot snapshot = result.getSnapshots()[row];
        SystemInterface cell = snapshot.getRetainedFluid();
        assertTrue(cell.hasPhaseType("aqueous"));
        assertTrue(cell.hasPhaseType("gas"));
        assertArrayEquals(new double[] {0, 0}, snapshot.getProducedComponentMoles(), 0);
        assertFalse(result.hasWithdrawal(row));
        double[][] products = result.getRetainedProducts(row).getComponentMoles();
        for (int component = 0; component < 2; component++) {
          assertEquals(.5, cell.getComponent(component).getNumberOfmoles(), 1e-10);
          assertEquals(.5, products[0][component] + products[1][component] + products[2][component], 1e-8);
        }
        assertTrue(result.getRetainedProducts(row).getVolumesM3()[2] > 0);
        cell.addComponent("water", 1);
        assertEquals(1, snapshot.getRetainedFluid().getTotalNumberOfMoles(), 1e-12);
      }
      NativeResult restored = SerializationUtils.clone(result);
      assertEquals(result.getModel().getProvenance(), restored.getModel().getProvenance());
      assertEquals(300, restored.getSnapshots()[0].getRetainedFluid().getPressure(), 0);
    }
  }

  /**
   * Checks independently solved wet binary SRK volumes, phase amounts and compositions.
   *
   * @throws Exception if committed reference data cannot be read
   */
  @Test
  void independentWetBinaryReference() throws Exception {
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/native-wet-cce/reference.json"),
        StandardCharsets.UTF_8)) {
      JsonArray records = new Gson().fromJson(reader, JsonArray.class);
      assertEquals(9, records.size());
      for (JsonElement element : records) {
        JsonObject ref = element.getAsJsonObject();
        NativeResult result = DepletionPvtHistoryGenerator.replayNative(model(.5, 0), false, Path.CCE,
            "independent wet binary synthetic reference", ref.get("temperatureK").getAsDouble(),
            new double[] {300, 150, 50}, separator());
        int row = ref.get("pressureBara").getAsInt() == 300 ? 0 : ref.get("pressureBara").getAsInt() == 150 ? 1 : 2;
        SystemInterface cell = result.getSnapshots()[row].getRetainedFluid();
        double volume = ref.get("volumeM3").getAsDouble();
        assertEquals(volume, cell.getCorrectedVolume(), volume * 1e-8);
        assertEquals(ref.get("gasMoleFraction").getAsDouble(), cell.getPhase("gas").getBeta(), 1e-8);
        for (int i = 0; i < 2; i++) {
          assertEquals(ref.getAsJsonArray("gasComposition").get(i).getAsDouble(),
              cell.getPhase("gas").getComponent(i).getx(), 1e-8);
          assertEquals(ref.getAsJsonArray("liquidComposition").get(i).getAsDouble(),
              cell.getPhase("aqueous").getComponent(i).getx(), 1e-8);
        }
      }
    }
  }

  /** Wet constant-volume withdrawal remains an explicit unsupported domain. */
  @Test
  void doesNotEnableWetCvd() {
    for (Path path : new Path[] {Path.CVD}) {
      assertThrows(IllegalArgumentException.class, () -> DepletionPvtHistoryGenerator.replayNative(model(.5, 0), false,
          path, "unsupported wet withdrawal", 350, new double[] {300, 150, 50}, separator()));
    }
  }

  /** Water may be absent as a cell phase while remaining a conserved component and surface product. */
  @Test
  void singlePhaseWetGasLimit() {
    NativeResult result = DepletionPvtHistoryGenerator.replayNative(model(.5, 0), false, Path.CCE,
        "supercritical wet closed cell", 700, new double[] {300, 150, 50}, separator());
    for (DepletionSnapshot row : result.getSnapshots()) {
      assertEquals(1, row.getRetainedFluid().getNumberOfPhases());
      assertEquals(.5, row.getRetainedFluid().getComponent("water").getNumberOfmoles(), 1e-10);
      assertArrayEquals(new double[] {0, 0}, row.getProducedComponentMoles(), 0);
    }
  }

  /** Pure aqueous cell volumes satisfy the independently bisected SRK pressure equation. */
  @Test
  void independentPureWaterLimit() {
    CalibratedPvtModel model = model(0, 0);
    SystemInterface f = model.getOriginalFluid();
    double tc = f.getComponent(0).getTC();
    double pc = f.getComponent(0).getPC() * 1e5;
    double omega = f.getComponent(0).getAcentricFactor();
    double r = 8.3144621;
    double c = Math.cbrt(2) - 1;
    double b = c / 3 * r * tc / pc;
    double m = .48 + 1.574 * omega - .176 * omega * omega;
    double a = r * r * tc * tc / (9 * c * pc) * Math.pow(1 + m * (1 - Math.sqrt(350 / tc)), 2);
    NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, false, Path.CCE, "pure water closed cell",
        350, new double[] {300, 150, 50}, separator());
    for (DepletionSnapshot row : result.getSnapshots()) {
      SystemInterface cell = row.getRetainedFluid();
      double low = b * (1 + 1e-12);
      double high = 2 * b;
      for (int i = 0; i < 150; i++) {
        double v = (low + high) / 2;
        double pressure = r * 350 / (v - b) - a / (v * (v + b));
        if (pressure > cell.getPressure() * 1e5) {
          low = v;
        } else {
          high = v;
        }
      }
      double volume = (low + high) / 2;
      assertEquals(volume, cell.getCorrectedVolume(), volume * 1e-8);
      assertTrue(cell.hasPhaseType("aqueous"));
      assertEquals(1, cell.getTotalNumberOfMoles(), 1e-12);
      assertArrayEquals(new double[] {0}, row.getProducedComponentMoles(), 0);
    }
  }

  /**
   * Three-phase volumes, compositions and fractions agree with six independent fugacity equations and material balance.
   *
   * @throws Exception if reference data cannot be read
   */
  @Test
  void independentThreePhaseClosedCell() throws Exception {
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/native-wet-cce/ternary-reference.json"),
        StandardCharsets.UTF_8)) {
      JsonArray records = new Gson().fromJson(reader, JsonArray.class);
      assertEquals(3, records.size());
      NativeResult result = DepletionPvtHistoryGenerator.replayNative(model(.6, .2), false, Path.CCE,
          "independent three-phase wet closed cell", 350, new double[] {200, 100, 50}, separator());
      for (int row = 0; row < 3; row++) {
        JsonObject ref = records.get(row).getAsJsonObject();
        SystemInterface cell = result.getSnapshots()[row].getRetainedFluid();
        assertEquals(3, cell.getNumberOfPhases());
        double volume = ref.get("volumeM3").getAsDouble();
        assertEquals(volume, cell.getCorrectedVolume(), volume * 1e-8);
        String[] types = {"gas", "oil", "aqueous"};
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(ref.getAsJsonArray("phaseFractions").get(phase).getAsDouble(),
              cell.getPhase(types[phase]).getBeta(), 1e-8);
          for (int component = 0; component < 3; component++) {
            assertEquals(
                ref.getAsJsonArray("phaseCompositions").get(phase).getAsJsonArray().get(component).getAsDouble(),
                cell.getPhase(types[phase]).getComponent(component).getx(), 1e-8);
          }
        }
        double[][] products = result.getRetainedProducts(row).getComponentMoles();
        for (int component = 0; component < 3; component++) {
          assertEquals(new double[] {.6, .2, .2}[component],
              products[0][component] + products[1][component] + products[2][component], 1e-8);
        }
        assertArrayEquals(new double[3], result.getSnapshots()[row].getProducedComponentMoles(), 0);
      }
    }
  }

  /** Intermediate pressure rows do not change the final closed-cell equilibrium or its inventory. */
  @Test
  void pressureGridRefinementPreservesFinalClosedCell() {
    for (double decane : new double[] {0, .2}) {
      CalibratedPvtModel model = model(decane == 0 ? .5 : .6, decane);
      NativeResult coarse = DepletionPvtHistoryGenerator.replayNative(model, false, Path.CCE,
          "coarse complete wet experiment", 350, new double[] {300, 150, 50}, separator());
      NativeResult fine = DepletionPvtHistoryGenerator.replayNative(model, false, Path.CCE,
          "refined complete wet experiment", 350, new double[] {300, 250, 200, 150, 100, 75, 50}, separator());
      SystemInterface a = coarse.getSnapshots()[2].getRetainedFluid();
      SystemInterface b = fine.getSnapshots()[6].getRetainedFluid();
      assertEquals(a.getCorrectedVolume(), b.getCorrectedVolume(), a.getCorrectedVolume() * 1e-8);
      assertEquals(a.getNumberOfPhases(), b.getNumberOfPhases());
      for (int component = 0; component < a.getNumberOfComponents(); component++) {
        assertEquals(a.getComponent(component).getNumberOfmoles(), b.getComponent(component).getNumberOfmoles(), 1e-10);
      }
      for (int phase = 0; phase < a.getNumberOfPhases(); phase++) {
        String type = a.getPhase(phase).getType().getDesc();
        assertTrue(b.hasPhaseType(type));
        for (int component = 0; component < a.getNumberOfComponents(); component++) {
          assertEquals(a.getPhase(type).getComponent(component).getx(), b.getPhase(type).getComponent(component).getx(),
              1e-8);
        }
      }
    }
  }
}
