package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStreamReader;
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
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Explicit-reference numerical, provenance, transactional and separator qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FixedCellDepletionHandoffTest {
  /**
   * Builds the declared unshifted synthetic EOS sample without fitting.
   *
   * @param names native component names
   * @param amounts component molar amounts
   * @param temperature common temperature, K
   * @return initialized original sample
   */
  private SystemInterface fluid(String[] names, double[] amounts, double temperature) {
    SystemInterface fluid = new SystemSrkEos(temperature, 200);
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], amounts[i]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    return fluid;
  }

  /**
   * Freezes separate labeled checkpoints without reservoir-volume fitting.
   *
   * @param fluid synthetic sample
   * @return independent model versions with experiment provenance
   */
  private CalibratedPvtModel model(SystemInterface fluid) {
    return new CalibratedPvtModel(fluid, SerializationUtils.clone(fluid), "synthetic fixed-cell sample",
        "original SRK/classic", "separately labeled SRK/classic consistency checkpoint",
        "synthetic numerical calibration control; not measured accuracy", new String[] {"laboratory-control"});
  }

  /**
   * Defines the fixed surface and product-volume basis used throughout each experiment.
   *
   * @return immutable two-stage separator process
   */
  private PvtSeparatorPath separator() {
    return new PvtSeparatorPath(new double[] {10, 1.01325}, new double[] {303.15, 298.15},
        "synthetic equilibrium separator; final1.01325bara298.15K actual product volumes");
  }

  /**
   * Reads a primitive array from the independently generated numeric reference.
   *
   * @param values JSON numeric array
   * @return complete primitive array
   */
  private double[] numbers(JsonArray values) {
    double[] result = new double[values.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = values.get(i).getAsDouble();
    }
    return result;
  }

  /**
   * Checks all phase-product component amounts against the supplied inventory.
   *
   * @param products separated native products
   * @param expected independently conserved component inventory, mol
   */
  private void products(PvtSeparatorPath.Result products, double[] expected) {
    double[][] phases = products.getComponentMoles();
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], phases[0][i] + phases[1][i] + phases[2][i], 1e-8);
    }
  }

  /**
   * Compares72complete independent paths, both checkpoint selections and all fixed separator products.
   *
   * @throws Exception if reference or diagnostic output cannot be read or written
   */
  @Test
  void independentMatrixPreservesBothVersionsAndNamedInventories() throws Exception {
    JsonArray cases;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/fixed-cell/reference.json"), StandardCharsets.UTF_8)) {
      cases = new Gson().fromJson(reader, JsonArray.class);
    }
    int states = 0;
    double maxVolume = 0, maxAmount = 0, maxComposition = 0, maxClosure = 0;
    long start = System.nanoTime();
    for (int c = 0; c < cases.size(); c++) {
      JsonObject entry = cases.get(c).getAsJsonObject();
      double temperature = entry.get("temperatureK").getAsDouble();
      double amount = entry.get("sampleMoles").getAsDouble();
      double[] initial = numbers(entry.getAsJsonArray("feed"));
      String[] names = new Gson().fromJson(entry.get("componentNames"), String[].class);
      String[] phases = new Gson().fromJson(entry.get("phaseNames"), String[].class);
      for (int i = 0; i < initial.length; i++) {
        initial[i] *= amount;
      }
      SystemInterface input = fluid(names, initial, temperature);
      CalibratedPvtModel checkpoints = model(input);
      byte[] original = SerializationUtils.serialize(checkpoints.getOriginalFluid());
      byte[] tuned = SerializationUtils.serialize(checkpoints.getTunedFluid());
      for (boolean selected : new boolean[] {false, true}) {
        NativeResult result = DepletionPvtHistoryGenerator.replayNativeFixedCell(checkpoints, selected,
            "synthetic path " + c + "; initial occupied EOS cell; full experiment; no measured accuracy claim",
            temperature, new double[] {200, 100, 50}, separator());
        assertSame(checkpoints, result.getModel());
        assertEquals(selected, result.isTuned());
        assertEquals(Path.CVD, result.getPath());
        assertTrue(result.hasFixedCellReference());
        assertEquals(temperature, result.getFixedCellReference().getTemperatureK(), 0);
        assertEquals(200, result.getFixedCellReference().getReferencePressureBara(), 0);
        double target = entry.get("targetVolumeM3").getAsDouble();
        double[] cumulative = new double[initial.length];
        DepletionSnapshot[] snapshots = result.getSnapshots();
        assertEquals(3, snapshots.length);
        for (int row = 0; row < snapshots.length; row++) {
          JsonObject expected = entry.getAsJsonArray("rows").get(row).getAsJsonObject();
          SystemInterface cell = snapshots[row].getRetainedFluid();
          assertArrayEquals(input.getComponentNames(), snapshots[row].getComponents());
          assertEquals(temperature, cell.getTemperature(), 0);
          assertEquals(expected.get("pressureBara").getAsDouble(), cell.getPressure(), 0);
          assertEquals(phases.length, cell.getNumberOfPhases());
          double volumeError = Math.abs(cell.getCorrectedVolume() / target - 1);
          maxVolume = Math.max(maxVolume, volumeError);
          assertTrue(volumeError <= 1e-8);
          assertEquals(target, result.getFixedCellReference().getReferenceVolumeM3(), target * 1e-8);
          double[] withdrawn = snapshots[row].getProducedComponentMoles();
          double[] referenceWithdrawn = numbers(expected.getAsJsonArray("withdrawnMoles"));
          double[] retained = numbers(expected.getAsJsonArray("retainedMoles"));
          for (int i = 0; i < initial.length; i++) {
            double current = cell.getComponent(i).getNumberOfmoles();
            double error = Math.max(Math.abs(current - retained[i]), Math.abs(withdrawn[i] - referenceWithdrawn[i]));
            maxAmount = Math.max(maxAmount, error);
            assertTrue(error <= 1e-8);
            cumulative[i] += withdrawn[i];
            double closure = Math.abs(initial[i] - current - cumulative[i]);
            maxClosure = Math.max(maxClosure, closure);
            assertTrue(closure <= 1e-10);
            if (row == 0) {
              assertEquals(0, withdrawn[i], 0);
            }
          }
          for (int p = 0; p < phases.length; p++) {
            assertTrue(cell.hasPhaseType(phases[p]));
            double phaseAmount = cell.getPhase(phases[p]).getNumberOfMolesInPhase();
            double error = Math.abs(phaseAmount - expected.getAsJsonArray("phaseMoles").get(p).getAsDouble());
            maxAmount = Math.max(maxAmount, error);
            assertTrue(error <= 1e-8);
            double[] composition = numbers(expected.getAsJsonArray("phaseCompositions").get(p).getAsJsonArray());
            for (int i = 0; i < initial.length; i++) {
              double difference = Math.abs(cell.getPhase(phases[p]).getComponent(i).getx() - composition[i]);
              maxComposition = Math.max(maxComposition, difference);
              assertTrue(difference <= 1e-8);
            }
          }
          products(result.getRetainedProducts(row), retained);
          assertEquals(row > 0, result.hasWithdrawal(row));
          if (row > 0) {
            products(result.getWithdrawalProducts(row), referenceWithdrawn);
          }
          states++;
        }
        assertArrayEquals(original, SerializationUtils.serialize(checkpoints.getOriginalFluid()));
        assertArrayEquals(tuned, SerializationUtils.serialize(checkpoints.getTunedFluid()));
      }
    }
    assertEquals(72, cases.size());
    assertEquals(432, states);
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("uniqueIndependentStates", states / 2);
    metrics.put("checkpointSelectedStateExecutions", states);
    metrics.put("maximumRelativeVolumeError", maxVolume);
    metrics.put("maximumAbsoluteAmountErrorMol", maxAmount);
    metrics.put("maximumAbsoluteCompositionError", maxComposition);
    metrics.put("maximumComponentClosureMol", maxClosure);
    metrics.put("secondsIncludingSeparatorProducts", (System.nanoTime() - start) / 1e9);
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/fixed-cell-metrics.json"),
        new Gson().toJson(metrics).getBytes(StandardCharsets.UTF_8));
  }

  /** Checks primitive pressure inputs, absent gas, repeated execution and defensive immutable result boundaries. */
  @Test
  void transactionalPureGasLimitsAndInvalidInputs() {
    SystemInterface input = fluid(new String[] {"methane"}, new double[] {1}, 350);
    ConstantVolumeDepletion simulation = new ConstantVolumeDepletion(input);
    byte[] before = SerializationUtils.serialize(input);
    FixedCellResult first = simulation.runFixedCellDepletion(350, new double[] {200, 100, 50});
    FixedCellResult second = simulation.runFixedCellDepletion(350, new double[] {200, 100, 50});
    assertArrayEquals(before, SerializationUtils.serialize(input));
    assertEquals(0, simulation.getDepletionSnapshots().length);
    for (int row = 0; row < 3; row++) {
      assertEquals(first.getReferenceVolumeM3(), first.getSnapshots()[row].getRetainedFluid().getCorrectedVolume(),
          first.getReferenceVolumeM3() * 1e-8);
      assertArrayEquals(first.getSnapshots()[row].getProducedComponentMoles(),
          second.getSnapshots()[row].getProducedComponentMoles(), 0);
    }
    DepletionSnapshot[] altered = first.getSnapshots();
    altered[0] = null;
    assertTrue(first.getSnapshots()[0] != null);
    SystemInterface changed = first.getSnapshots()[0].getRetainedFluid();
    changed.addComponent("methane", 10);
    assertEquals(1, first.getSnapshots()[0].getRetainedFluid().getTotalNumberOfMoles(), 0);
    assertThrows(IllegalArgumentException.class, () -> simulation.runFixedCellDepletion(350, new double[] {200, 200}));
    assertThrows(IllegalArgumentException.class,
        () -> simulation.runFixedCellDepletion(Double.NaN, new double[] {200, 100}));
    assertThrows(IllegalArgumentException.class, () -> simulation.runFixedCellDepletion(350, new double[] {200, -1}));
    SystemInterface water = fluid(new String[] {"water"}, new double[] {1}, 350);
    byte[] waterBefore = SerializationUtils.serialize(water);
    assertThrows(IllegalStateException.class,
        () -> new ConstantVolumeDepletion(water).runFixedCellDepletion(350, new double[] {200, 100}));
    assertArrayEquals(waterBefore, SerializationUtils.serialize(water));
  }

  /** A late phase-family failure leaves input and previous independent results intact. */
  @Test
  void latePhaseFamilyFailurePublishesNoPartialTrial() {
    SystemInterface input = fluid(new String[] {"methane", "n-butane"}, new double[] {.95, .05}, 275);
    ConstantVolumeDepletion simulation = new ConstantVolumeDepletion(input);
    byte[] before = SerializationUtils.serialize(input);
    FixedCellResult completed = simulation.runFixedCellDepletion(275, new double[] {50, 49});
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> simulation.runFixedCellDepletion(275, new double[] {50, 49, 20, 5}));
    assertTrue(failure.getMessage().contains("phase-family"));
    assertArrayEquals(before, SerializationUtils.serialize(input));
    assertEquals(0, simulation.getDepletionSnapshots().length);
    FixedCellResult repeated = simulation.runFixedCellDepletion(275, new double[] {50, 49});
    assertArrayEquals(completed.getSnapshots()[1].getProducedComponentMoles(),
        repeated.getSnapshots()[1].getProducedComponentMoles(), 0);
  }

  /** Distinct declared EOS volume bases prove checkpoint selection without any reservoir-volume fitting. */
  @Test
  void checkpointSelectionRetainsDistinctIndependentlyDeclaredVolumeBases() {
    SystemInterface original = fluid(new String[] {"methane", "n-decane", "water"}, new double[] {.6, .2, .2}, 350);
    SystemInterface tuned = SerializationUtils.clone(original);
    tuned.useVolumeCorrection(true);
    CalibratedPvtModel checkpoints = new CalibratedPvtModel(original, tuned, "same synthetic sample",
        "unshifted original", "independently declared translated control", "synthetic model-selection control",
        new String[] {"laboratory-control"});
    byte[] originalBefore = SerializationUtils.serialize(checkpoints.getOriginalFluid());
    byte[] tunedBefore = SerializationUtils.serialize(checkpoints.getTunedFluid());
    NativeResult first = DepletionPvtHistoryGenerator.replayNativeFixedCell(checkpoints, false, "unshifted experiment",
        350, new double[] {200, 100, 50}, separator());
    NativeResult second = DepletionPvtHistoryGenerator.replayNativeFixedCell(checkpoints, true, "translated experiment",
        350, new double[] {200, 100, 50}, separator());
    assertTrue(Math.abs(first.getFixedCellReference().getReferenceVolumeM3()
        - second.getFixedCellReference().getReferenceVolumeM3()) > 1e-12);
    assertArrayEquals(originalBefore, SerializationUtils.serialize(checkpoints.getOriginalFluid()));
    assertArrayEquals(tunedBefore, SerializationUtils.serialize(checkpoints.getTunedFluid()));
    assertTrue(checkpoints.getProvenance().contains("independently declared translated control"));
    NativeResult restored = SerializationUtils.clone(second);
    assertTrue(restored.hasFixedCellReference());
    assertTrue(restored.isTuned());
    assertEquals(second.getFixedCellReference().getReferenceVolumeM3(),
        restored.getFixedCellReference().getReferenceVolumeM3(), 0);
    assertArrayEquals(second.getSnapshots()[2].getProducedComponentMoles(),
        restored.getSnapshots()[2].getProducedComponentMoles(), 0);
  }

  /** Keeps the existing wet upper-dew rejection and distinguishes legacy results from explicit reference results. */
  @Test
  void explicitReferenceNeverSilentlyReplacesLaboratoryUpperDew() {
    SystemInterface wet = fluid(new String[] {"methane", "n-decane", "water"}, new double[] {.6, .2, .2}, 350);
    CalibratedPvtModel model = model(wet);
    assertThrows(IllegalArgumentException.class, () -> DepletionPvtHistoryGenerator.replayNative(model, false, Path.CVD,
        "saturation-referenced laboratory protocol", 350, new double[] {200, 100, 50}, separator()));
    NativeResult cce = DepletionPvtHistoryGenerator.replayNative(model, false, Path.CCE, "closed mass", 350,
        new double[] {200, 100, 50}, separator());
    assertFalse(cce.hasFixedCellReference());
    assertThrows(IllegalStateException.class, cce::getFixedCellReference);
  }
}
