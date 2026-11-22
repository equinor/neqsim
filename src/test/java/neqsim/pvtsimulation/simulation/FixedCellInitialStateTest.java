package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Qualifies reuse of the already accepted initial fixed-cell state without another flash.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FixedCellInitialStateTest {
  /** Canonical names; the third component is registered with exactly zero inventory. */
  private static final String[] NAMES = {"methane", "n-butane", "n-heptane"};
  /** Complete component insertion permutations. */
  private static final int[][] ORDERS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

  /**
   * Constructs a fresh declared unshifted SRK sample.
   *
   * @param temperature temperature, K
   * @param pressure initial absolute pressure, bara
   * @param scale extensive amount multiplier
   * @param order component insertion order
   * @return new sample, never flashed by this builder
   */
  private SystemInterface sample(double temperature, double pressure, double scale, int[] order) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    double[] amounts = {.95 * scale, .05 * scale, 0};
    for (int index : order) {
      fluid.addComponent(NAMES[index], amounts[index]);
    }
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    return fluid;
  }

  /**
   * Reuses the frozen independent binary SRK reference with an exactly zero additional component.
   *
   * @throws Exception if the public synthetic reference cannot be read
   */
  @Test
  void registeredZeroMatchesIndependentBinaryReference() throws Exception {
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/fixed-cell-transitions/reference.json"),
        StandardCharsets.UTF_8)) {
      JsonArray cases = JsonParser.parseReader(reader).getAsJsonArray();
      for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
        JsonObject reference = cases.get(caseIndex).getAsJsonObject();
        double temperature = reference.get("temperatureK").getAsDouble();
        double methane = reference.get("methaneFraction").getAsDouble();
        JsonArray expectedRows = reference.getAsJsonArray("rows");
        double[] pressures = new double[expectedRows.size()];
        for (int row = 0; row < pressures.length; row++) {
          pressures[row] = expectedRows.get(row).getAsJsonObject().get("pressureBara").getAsDouble();
        }
        for (double scale : new double[] {.001, 1, 1000}) {
          SystemInterface fluid = new SystemSrkEos(temperature, pressures[0]);
          fluid.addComponent("n-heptane", 0);
          fluid.addComponent("methane", methane * scale);
          fluid.addComponent("n-butane", (1 - methane) * scale);
          fluid.setMixingRule("classic");
          fluid.useVolumeCorrection(false);
          fluid.setMultiPhaseCheck(true);
          fluid.init(0);
          byte[] before = SerializationUtils.serialize(fluid);
          ConstantVolumeDepletion.FixedCellResult actual = new ConstantVolumeDepletion(fluid)
              .runDryPhaseChangingFixedCellDepletion(temperature, pressures);
          for (int row = 0; row < pressures.length; row++) {
            DepletionSnapshot snapshot = actual.getSnapshots()[row];
            SystemInterface cell = snapshot.getRetainedFluid();
            JsonObject expected = expectedRows.get(row).getAsJsonObject();
            assertEquals(0, cell.getComponent(0).getNumberOfmoles(), 0);
            assertEquals(0, snapshot.getProducedComponentMoles()[0], 0);
            assertEquals(1, cell.getCorrectedVolume() / actual.getReferenceVolumeM3(), 1e-12);
            for (int component = 0; component < 2; component++) {
              assertEquals(scale * expected.getAsJsonArray("retainedMoles").get(component).getAsDouble(),
                  cell.getComponent(component + 1).getNumberOfmoles(), 1e-8);
              assertEquals(scale * expected.getAsJsonArray("withdrawnMoles").get(component).getAsDouble(),
                  snapshot.getProducedComponentMoles()[component + 1], 1e-8);
            }
          }
          assertArrayEquals(before, SerializationUtils.serialize(fluid));
        }
      }
    }
  }

  /** All54 zero-registered paths retain their zero and agree with the named two-component controls. */
  @Test
  void qualifiedInitialStateRetainsZeroInventoryAcrossNearbyPaths() {
    for (int nearby = -1; nearby <= 1; nearby++) {
      double temperature = 275 + 2 * nearby;
      double pressure = 100 + 2 * nearby;
      double[] pressures = {pressure, 60, 30, 10, 2};
      for (double scale : new double[] {.001, 1, 1000}) {
        SystemInterface binary = sample(temperature, pressure, scale, new int[] {0, 1});
        ConstantVolumeDepletion.FixedCellResult expected = new ConstantVolumeDepletion(binary)
            .runDryPhaseChangingFixedCellDepletion(temperature, pressures);
        for (int[] order : ORDERS) {
          SystemInterface fluid = sample(temperature, pressure, scale, order);
          byte[] before = SerializationUtils.serialize(fluid);
          ConstantVolumeDepletion.FixedCellResult actual = new ConstantVolumeDepletion(fluid)
              .runDryPhaseChangingFixedCellDepletion(temperature, pressures);
          double[] cumulative = new double[3];
          DepletionSnapshot[] rows = actual.getSnapshots();
          assertEquals(actual.getReferenceVolumeM3(), rows[0].getRetainedFluid().getCorrectedVolume(), 0);
          for (int row = 0; row < rows.length; row++) {
            SystemInterface cell = rows[row].getRetainedFluid();
            SystemInterface control = expected.getSnapshots()[row].getRetainedFluid();
            assertEquals(1, cell.getCorrectedVolume() / actual.getReferenceVolumeM3(), 1e-12);
            for (int named = 0; named < NAMES.length; named++) {
              int position = Arrays.asList(cell.getComponentNames()).indexOf(NAMES[named]);
              double removed = rows[row].getProducedComponentMoles()[position];
              assertTrue(removed >= 0);
              cumulative[named] += removed;
              double initial = named == 0 ? .95 * scale : named == 1 ? .05 * scale : 0;
              double retained = cell.getComponent(position).getNumberOfmoles();
              assertEquals(initial, retained + cumulative[named], 1e-10 * Math.max(1, initial));
              if (named == 2) {
                assertEquals(0, retained, 0);
                assertEquals(0, removed, 0);
              } else {
                assertEquals(control.getComponent(named).getNumberOfmoles(), retained, 1e-10 * Math.max(1, initial));
              }
            }
          }
          assertArrayEquals(before, SerializationUtils.serialize(fluid));
        }
      }
    }
  }
}
