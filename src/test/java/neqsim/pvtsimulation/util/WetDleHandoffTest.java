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
import com.google.gson.JsonObject;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Native wet differential liberation: remove gas and retain both liquid inventories.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WetDleHandoffTest {
  /**
   * Builds a declared synthetic unshifted SRK sample, without a measured calibration claim.
   *
   * @param methane methane mole fraction
   * @return immutable original/tuned checkpoints
   */
  private CalibratedPvtModel model(double methane) {
    SystemInterface fluid = new SystemSrkEos(350, 200);
    fluid.addComponent("methane", methane);
    fluid.addComponent("n-decane", .8 - methane);
    fluid.addComponent("water", .2);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    SystemInterface shifted = fluid.clone();
    shifted.useVolumeCorrection(true);
    return new CalibratedPvtModel(fluid, shifted, "synthetic wet DLE", "unshifted SRK", "shifted SRK",
        "declared synthetic checkpoints; no measured fit", new String[] {"wet-DLE-calibration"});
  }

  /**
   * Supplies an immutable explicit surface train.
   *
   * @return fixed separator and stock-tank basis
   */
  private PvtSeparatorPath separator() {
    return new PvtSeparatorPath(new double[] {10, 1.01325}, new double[] {303.15, 298.15},
        "synthetic 25 C, 101.325 kPa absolute final basis");
  }

  /**
   * Checks independent three-phase equilibria followed by complete gas removal, including vaporized water.
   *
   * @throws Exception if the independent reference cannot be read
   */
  @Test
  void independentWetLiberation() throws Exception {
    CalibratedPvtModel model = model(.6);
    DepletionPvtHistoryGenerator.NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, false, Path.DLE,
        "whole synthetic wet DLE holdout", 350, new double[] {200, 100, 50}, separator());
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/native-wet-dle/reference.json"),
        StandardCharsets.UTF_8)) {
      JsonArray references = new Gson().fromJson(reader, JsonArray.class);
      double[] cumulative = new double[3];
      for (int row = 0; row < 3; row++) {
        JsonObject reference = references.get(row).getAsJsonObject();
        DepletionSnapshot snapshot = result.getSnapshots()[row];
        SystemInterface equilibrium = snapshot.getEquilibriumFluid();
        SystemInterface retained = snapshot.getRetainedFluid();
        assertEquals(3, equilibrium.getNumberOfPhases());
        String[] types = {"gas", "oil", "aqueous"};
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(reference.getAsJsonArray("phaseMoles").get(phase).getAsDouble(),
              equilibrium.getPhase(types[phase]).getNumberOfMolesInPhase(), 1e-8);
          for (int component = 0; component < 3; component++) {
            assertEquals(
                reference.getAsJsonArray("phaseCompositions").get(phase).getAsJsonArray().get(component).getAsDouble(),
                equilibrium.getPhase(types[phase]).getComponent(component).getx(), 1e-8);
          }
        }
        assertFalse(retained.hasPhaseType("gas"));
        assertTrue(retained.hasPhaseType("oil"));
        assertTrue(retained.hasPhaseType("aqueous"));
        double volume = reference.get("equilibriumVolumeM3").getAsDouble();
        assertEquals(volume, equilibrium.getCorrectedVolume(), volume * 1e-8);
        volume = reference.get("retainedVolumeM3").getAsDouble();
        assertEquals(volume, retained.getCorrectedVolume(), volume * 1e-8);
        double[][] retainedProducts = result.getRetainedProducts(row).getComponentMoles();
        double[][] gasProducts = result.getWithdrawalProducts(row).getComponentMoles();
        for (int component = 0; component < 3; component++) {
          double removed = snapshot.getProducedComponentMoles()[component];
          double left = retained.getComponent(component).getNumberOfmoles();
          assertEquals(reference.getAsJsonArray("withdrawnMoles").get(component).getAsDouble(), removed, 1e-8);
          assertEquals(reference.getAsJsonArray("retainedMoles").get(component).getAsDouble(), left, 1e-8);
          assertEquals(equilibrium.getPhase("gas").getComponent(component).getNumberOfMolesInPhase(), removed, 1e-10);
          assertEquals(left,
              retainedProducts[0][component] + retainedProducts[1][component] + retainedProducts[2][component], 1e-8);
          assertEquals(removed, gasProducts[0][component] + gasProducts[1][component] + gasProducts[2][component],
              1e-8);
          cumulative[component] += removed;
          assertEquals(new double[] {.6, .2, .2}[component], cumulative[component] + left, 1e-10);
        }
        assertTrue(snapshot.getProducedComponentMoles()[2] > 0);
      }
    }
    assertArrayEquals(new double[] {.6, .2, .2},
        new double[] {model.getOriginalFluid().getComponent(0).getNumberOfmoles(),
            model.getOriginalFluid().getComponent(1).getNumberOfmoles(),
            model.getOriginalFluid().getComponent(2).getNumberOfmoles()},
        1e-12);
  }

  /** Wet constant-volume withdrawal remains separately unqualified. */
  @Test
  void wetCvdRemainsUnsupported() {
    assertThrows(IllegalArgumentException.class, () -> DepletionPvtHistoryGenerator.replayNative(model(.6), false,
        Path.CVD, "unsupported wet CVD", 350, new double[] {200, 100, 50}, separator()));
  }

  /** Two liquid phases without gas retain their inventories and do not trigger a saturation scan. */
  @Test
  void noGasTwoLiquidLimitAndReplayOwnership() {
    for (boolean tuned : new boolean[] {false, true}) {
      CalibratedPvtModel model = model(0);
      DepletionPvtHistoryGenerator.NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, tuned,
          Path.DLE, "two-liquid no-gas holdout", 350, new double[] {200, 100, 50}, separator());
      for (int row = 0; row < 3; row++) {
        assertFalse(result.hasWithdrawal(row));
        assertArrayEquals(new double[3], result.getSnapshots()[row].getProducedComponentMoles(), 0);
        assertTrue(result.getSnapshots()[row].getRetainedFluid().hasPhaseType("aqueous"));
        assertEquals(.2, result.getSnapshots()[row].getRetainedFluid().getComponent("water").getNumberOfmoles(), 1e-12);
      }
    }
    SystemInterface fluid = model(.6).getOriginalFluid();
    byte[] before = SerializationUtils.serialize(fluid);
    DifferentialLiberation simulation = new DifferentialLiberation(fluid);
    simulation.setTemperature(350, "K");
    simulation.setPressures(new double[] {200, 100, 50});
    DepletionSnapshot[] first = simulation.runNativeLiberation();
    DepletionSnapshot[] second = simulation.runNativeLiberation();
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
    for (int row = 0; row < 3; row++) {
      assertArrayEquals(first[row].getProducedComponentMoles(), second[row].getProducedComponentMoles(), 0);
    }
    first[0] = null;
    assertTrue(simulation.getDepletionSnapshots()[0] != null);
  }

  /** Invalid schedules and absent oil fail without publishing a partial experiment. */
  @Test
  void invalidPathsAndOilAbsence() {
    DifferentialLiberation simulation = new DifferentialLiberation(model(.6).getOriginalFluid());
    for (double[] pressures : new double[][] {new double[0], {200, 201}, {200, Double.NaN}, {200, 0}}) {
      simulation.setPressures(pressures);
      assertThrows(IllegalArgumentException.class, () -> simulation.runNativeLiberation());
      assertEquals(0, simulation.getDepletionSnapshots().length);
    }
    assertThrows(IllegalArgumentException.class, () -> DepletionPvtHistoryGenerator.replayNative(model(.8), false,
        Path.DLE, "oil absent", 350, new double[] {200, 100, 50}, separator()));
  }

  /** Gas appearance and the actual sampling pressures are part of a differential experiment. */
  @Test
  void gasAppearanceAndProtocolSampling() {
    double[] finalMethane = new double[2];
    double[][] schedules = {{600, 200, 100, 50}, {600, 400, 300, 200, 150, 100, 75, 50}};
    for (int experiment = 0; experiment < schedules.length; experiment++) {
      DepletionPvtHistoryGenerator.NativeResult result = DepletionPvtHistoryGenerator.replayNative(model(.6), false,
          Path.DLE, "distinct sampling protocol " + experiment, 350, schedules[experiment], separator());
      assertFalse(result.hasWithdrawal(0));
      assertTrue(result.hasWithdrawal(schedules[experiment].length - 1));
      double[] cumulative = new double[3];
      for (DepletionSnapshot row : result.getSnapshots()) {
        for (int component = 0; component < 3; component++) {
          cumulative[component] += row.getProducedComponentMoles()[component];
          assertEquals(new double[] {.6, .2, .2}[component],
              cumulative[component] + row.getRetainedFluid().getComponent(component).getNumberOfmoles(), 1e-10);
        }
      }
      finalMethane[experiment] = result.getSnapshots()[schedules[experiment].length - 1].getRetainedFluid()
          .getComponent("methane").getNumberOfmoles();
    }
    assertTrue(Math.abs(finalMethane[0] - finalMethane[1]) > 1e-8);
  }
}
