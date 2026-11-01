package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent dry CVD gas-only extensivity and pressure-grid qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CvdGasOnlyTailTest {
  /**
   * Runs a fresh dry reference sample, without EOS fitting.
   *
   * @param scale initial sample amount in mol
   * @param pressures decreasing absolute pressure schedule in bara
   * @return completed native CVD evidence
   */
  private ConstantVolumeDepletion run(double scale, double[] pressures) {
    return run(scale, pressures, true);
  }

  /**
   * Runs a fresh sample with the selected existing saturation-reference mode.
   *
   * @param scale initial sample amount in mol
   * @param pressures decreasing absolute pressure schedule in bara
   * @param refine whether the checked upper-dew reference is enabled
   * @return completed depletion calculation
   */
  private ConstantVolumeDepletion run(double scale, double[] pressures, boolean refine) {
    SystemInterface fluid = new SystemSrkEos(275, 500);
    fluid.addComponent("methane", 0.95 * scale);
    fluid.addComponent("n-butane", 0.05 * scale);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
    cvd.setTemperature(275, "K");
    cvd.setPressures(pressures);
    cvd.setRecordDepletionSnapshots(true);
    cvd.setRefineSaturationConditions(refine);
    cvd.runCalc();
    return cvd;
  }

  /**
   * Compares all states and withdrawals to independent cubic-root and algebraic predictions.
   *
   * @throws Exception if the committed independent reference cannot be read
   */
  @Test
  void matchesIndependentTailAtThreeSampleScales() throws Exception {
    JsonArray reference;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/native-cvd-tail/reference.json"),
        StandardCharsets.UTF_8)) {
      reference = new Gson().fromJson(reader, JsonArray.class).get(0).getAsJsonObject().getAsJsonArray("rows");
    }
    for (double scale : new double[] {0.001, 1, 1000}) {
      ConstantVolumeDepletion cvd = run(scale, new double[] {500, 200, 100, 50, 20, 5, 2});
      assertEquals(74.95956303780969, cvd.getSaturationPressure(), 1e-5);
      DepletionSnapshot[] rows = cvd.getDepletionSnapshots();
      double[] cumulative = new double[2];
      for (int row = 0; row < rows.length; row++) {
        JsonObject expected = reference.get(row).getAsJsonObject();
        SystemInterface cell = rows[row].getRetainedFluid();
        double volume = scale * expected.get("volumeM3").getAsDouble();
        assertEquals(volume, cell.getCorrectedVolume(), volume * 1e-6);
        for (int i = 0; i < 2; i++) {
          double retained = cell.getComponent(i).getNumberOfmoles();
          double withdrawn = rows[row].getProducedComponentMoles()[i];
          assertEquals(scale * expected.getAsJsonArray("retainedMoles").get(i).getAsDouble(), retained, scale * 1e-6);
          assertEquals(scale * expected.getAsJsonArray("withdrawnMoles").get(i).getAsDouble(), withdrawn, scale * 1e-6);
          cumulative[i] += withdrawn;
          double initial = scale * (i == 0 ? 0.95 : 0.05);
          assertEquals(initial, retained + cumulative[i], 1e-10 * Math.max(1, initial));
          if (row < 3) {
            assertEquals(0, withdrawn, 0);
          }
        }
        if (row >= 4) {
          assertFalse(cell.hasPhaseType("oil"));
          assertEquals(1, cell.getNumberOfPhases());
          assertTrue(rows[row].getProducedComponentMoles()[0] > 0);
        }
      }
    }
  }

  /** Gas-only substeps preserve the same final inventory and gas composition. */
  @Test
  void refinesGasOnlyPressureStepsWithoutInventingOil() {
    DepletionSnapshot[] coarse = run(1, new double[] {500, 200, 100, 50, 20, 5, 2}).getDepletionSnapshots();
    DepletionSnapshot[] fine = run(1, new double[] {500, 200, 100, 50, 20, 10, 5, 3, 2}).getDepletionSnapshots();
    SystemInterface a = coarse[coarse.length - 1].getRetainedFluid();
    SystemInterface b = fine[fine.length - 1].getRetainedFluid();
    assertEquals(a.getCorrectedVolume(), b.getCorrectedVolume(), a.getCorrectedVolume() * 1e-8);
    assertEquals(a.getTotalNumberOfMoles(), b.getTotalNumberOfMoles(), 1e-10);
    assertArrayEquals(a.getMolarComposition(), b.getMolarComposition(), 1e-10);
  }

  /** The direct default route restores its own scan-reference volume through gas-only steps. */
  @Test
  void legacyReferenceAlsoRestoresTailWithoutPreBoundaryWithdrawal() {
    DepletionSnapshot[] rows = run(1, new double[] {500, 200, 100, 50, 20, 5, 2}, false).getDepletionSnapshots();
    double volume = rows[3].getRetainedFluid().getCorrectedVolume();
    for (int row = 0; row < 3; row++) {
      assertArrayEquals(new double[] {0, 0}, rows[row].getProducedComponentMoles(), 0);
    }
    for (int row = 4; row < rows.length; row++) {
      assertEquals(volume, rows[row].getRetainedFluid().getCorrectedVolume(), volume * 1e-8);
      assertTrue(rows[row].getProducedComponentMoles()[0] > 0);
      assertFalse(rows[row].getRetainedFluid().hasPhaseType("oil"));
    }
  }
}
