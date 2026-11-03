package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Independent upper-dew and native CVD reference qualification without fitted EOS inputs.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class RefinedCvdHandoffTest {
  /**
   * Creates the declared unshifted binary SRK reference fluid.
   *
   * @param moles sample amount in mol
   * @param pressure initial pressure in bara
   * @return fresh original sample
   */
  private SystemInterface fluid(double moles, double pressure) {
    SystemInterface fluid = new SystemSrkEos(373.15, pressure);
    fluid.addComponent("methane", 0.95 * moles);
    fluid.addComponent("n-decane", 0.05 * moles);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    return fluid;
  }

  /**
   * Runs the public opt-in CVD path, preserving legacy default behavior separately.
   *
   * @param moles sample amount in mol
   * @param initialPressure initial pressure in bara
   * @param refine whether upper-dew refinement is enabled
   * @return completed depletion calculation
   */
  private ConstantVolumeDepletion simulation(double moles, double initialPressure, boolean refine) {
    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid(moles, initialPressure));
    cvd.setTemperature(373.15, "K");
    cvd.setPressures(new double[] {500, 300, 200, 100});
    cvd.setRecordDepletionSnapshots(true);
    cvd.setRefineSaturationConditions(refine);
    cvd.runCalc();
    return cvd;
  }

  /** The unchanged scan fails the independent pressure criterion; refinement closes the upper root. */
  @Test
  void refinesUpperBoundaryWithoutChangingLegacyDefault() {
    double independentDew = 359.2450710350704;
    ConstantVolumeDepletion legacy = simulation(1, 500, false);
    assertEquals(359.2417392730713, legacy.getSaturationPressure(), 1e-9);
    assertTrue(Math.abs(legacy.getSaturationPressure() - independentDew) > 1e-5);
    for (double start : new double[] {300, 400, 500}) {
      ConstantVolumeDepletion refined = simulation(1, start, true);
      assertEquals(independentDew, refined.getSaturationPressure(), 1e-5);
      assertTrue(refined.getSaturationPressure() > 351 && refined.getSaturationPressure() < 361);
    }
  }

  /**
   * Sample scaling retains independently predicted volumes, inventories and protocol withdrawals.
   *
   * @throws Exception if the committed independent reference cannot be read
   */
  @Test
  void matchesIndependentAlgebraicDepletionAtThreeSampleScales() throws Exception {
    JsonArray reference;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/native-depletion/equilibrium-reference.json"),
        StandardCharsets.UTF_8)) {
      reference = new Gson().fromJson(reader, JsonArray.class).get(1).getAsJsonObject().getAsJsonArray("rows");
    }
    for (double scale : new double[] {0.001, 1, 1000}) {
      DepletionSnapshot[] snapshots = simulation(scale, 500, true).getDepletionSnapshots();
      double[] cumulative = new double[2];
      for (int row = 0; row < snapshots.length; row++) {
        JsonObject expected = reference.get(row).getAsJsonObject();
        SystemInterface cell = snapshots[row].getRetainedFluid();
        double expectedVolume = scale * expected.get("volumeM3").getAsDouble();
        assertEquals(expectedVolume, cell.getCorrectedVolume(), expectedVolume * 1e-6);
        for (int i = 0; i < 2; i++) {
          double retained = cell.getComponent(i).getNumberOfmoles();
          double withdrawn = snapshots[row].getProducedComponentMoles()[i];
          assertEquals(scale * expected.getAsJsonArray("retainedMoles").get(i).getAsDouble(), retained, scale * 1e-6);
          assertEquals(scale * expected.getAsJsonArray("withdrawnMoles").get(i).getAsDouble(), withdrawn, scale * 1e-6);
          cumulative[i] += withdrawn;
          double initial = scale * (i == 0 ? 0.95 : 0.05);
          assertEquals(initial, retained + cumulative[i], 1e-10 * Math.max(1, initial));
        }
      }
    }
  }

  /** Pure supercritical gas has no bounded dew bracket and must not silently use 1000 bara. */
  @Test
  void rejectsMissingTwoPhaseBracket() {
    SystemInterface fluid = new SystemSrkEos(373.15, 500);
    fluid.addComponent("methane", 1);
    fluid.setMixingRule("classic");
    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
    cvd.setRefineSaturationConditions(true);
    cvd.setPressures(new double[] {500, 300});
    assertThrows(IllegalStateException.class, cvd::runCalc);
  }

  /** Wet experiments require a distinct, qualified protocol rather than a dry dew refinement. */
  @Test
  void rejectsWetRefinement() {
    SystemInterface fluid = fluid(1, 500);
    fluid.addComponent("water", 0.01);
    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
    cvd.setRefineSaturationConditions(true);
    assertThrows(IllegalArgumentException.class, cvd::runCalc);
  }
}
