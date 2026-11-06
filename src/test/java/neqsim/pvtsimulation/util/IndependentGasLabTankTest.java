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
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent synthetic laboratory calibration/holdout and gas-only tank inversion chain.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class IndependentGasLabTankTest {
  /**
   * Reads independently generated observations with all units and inputs fixed in the accompanying scope.
   *
   * @return complete synthetic reference
   * @throws Exception if reference cannot be read
   */
  private JsonObject reference() throws Exception {
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/gas-lab-tank/reference.json"), StandardCharsets.UTF_8)) {
      return new Gson().fromJson(reader, JsonObject.class);
    }
  }

  /**
   * Builds the declared complete SRK configuration, without volume translation or hidden mixture defaults.
   *
   * @param interaction independently identified symmetric interaction
   * @return fresh sample containing one mole
   */
  private SystemInterface sample(double interaction) {
    SystemInterface fluid = new SystemSrkEos(400, 200);
    fluid.addComponent("methane", .9);
    fluid.addComponent("ethane", .1);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule().setBinaryInteractionParameter(0, 1, interaction);
      ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule().setBinaryInteractionParameter(1, 0, interaction);
    }
    return fluid;
  }

  /**
   * Freezes the original and independently laboratory-identified synthetic EOS versions.
   *
   * @param input independent laboratory and reservoir observations
   * @return immutable checkpoints with separate laboratory holdout identity
   */
  private CalibratedPvtModel model(JsonObject input) {
    return new CalibratedPvtModel(sample(0),
        sample(input.getAsJsonObject("calibration").get("identifiedKij").getAsDouble()),
        "synthetic methane/ethane one-mole sample", "original classic SRK kij0", "laboratory-only identified kij",
        "independent Decimal SRK closed-cell pressure/volume calibration at400K; no reservoir fit",
        new String[] {"synthetic laboratory calibration400K"});
  }

  /**
   * Defines the common final actual-volume basis; no oil or invented GOR is supplied.
   *
   * @return fixed surface process
   */
  private PvtSeparatorPath separator() {
    return new PvtSeparatorPath(new double[] {1.01325}, new double[] {288.15},
        "synthetic single-stage actual gas volume at1.01325bara/288.15K");
  }

  /**
   * Compares both checkpoints with the whole independent CCE holdout, preserving closed-cell provenance.
   *
   * @throws Exception if input or complete diagnostic cannot be read/written
   */
  @Test
  void laboratoryIdentificationPrecedesWholeExperimentHoldout() throws Exception {
    JsonObject input = reference();
    CalibratedPvtModel model = model(input);
    byte[] before = SerializationUtils.serialize(model);
    SystemInterface calibration = model.getTunedFluid();
    new ThermodynamicOperations(calibration).TPflash();
    calibration.initProperties();
    assertEquals(1,
        calibration.getCorrectedVolume() / input.getAsJsonObject("calibration").get("molarVolumeM3").getAsDouble(),
        1e-8);
    JsonObject provenance = new Gson().fromJson(model.getProvenance(), JsonObject.class);
    assertEquals("synthetic laboratory calibration400K",
        provenance.getAsJsonArray("calibrationExperiments").get(0).getAsString());
    assertEquals(1, provenance.getAsJsonArray("calibrationExperiments").size());
    assertThrows(IllegalArgumentException.class, () -> model.compareHoldout("synthetic laboratory calibration400K",
        "CCE", 400, new double[] {200, 100}, "relativeVolume", new double[] {1, 2}, new double[] {.01, .01}));
    JsonArray rows = input.getAsJsonArray("laboratoryHoldout");
    double[] pressures = new double[rows.size()];
    for (int i = 0; i < pressures.length; i++) {
      pressures[i] = rows.get(i).getAsJsonObject().get("pressureBara").getAsDouble();
    }
    double originalError = 0;
    List<Map<String, Object>> diagnostics = new ArrayList<Map<String, Object>>();
    Map<String, Object> calibrationRow = new LinkedHashMap<String, Object>();
    calibrationRow.put("role", "calibration");
    calibrationRow.put("temperatureK", 400);
    calibrationRow.put("pressureBara", 200);
    calibrationRow.put("volumeM3", calibration.getCorrectedVolume());
    diagnostics.add(calibrationRow);
    for (boolean tuned : new boolean[] {false, true}) {
      DepletionPvtHistoryGenerator.NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, tuned,
          Path.CCE, "whole CCE holdout420K", 420, pressures, separator());
      assertEquals(tuned, result.isTuned());
      for (int i = 0; i < rows.size(); i++) {
        SystemInterface cell = result.getSnapshots()[i].getRetainedFluid();
        double expected = rows.get(i).getAsJsonObject().get("molarVolumeM3").getAsDouble();
        double relative = Math.abs(cell.getCorrectedVolume() / expected - 1);
        if (tuned) {
          assertTrue(relative <= 1e-8);
        } else {
          originalError = Math.max(originalError, relative);
        }
        assertEquals(1, cell.getNumberOfPhases());
        assertTrue(cell.hasPhaseType("gas"));
        assertEquals(1, cell.getTotalNumberOfMoles(), 1e-10);
        assertEquals(.9, cell.getComponent("methane").getNumberOfmoles(), 1e-10);
        assertEquals(.1, cell.getComponent("ethane").getNumberOfmoles(), 1e-10);
        assertFalse(result.hasWithdrawal(i));
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("tuned", tuned);
        row.put("role", "whole-experiment holdout");
        row.put("temperatureK", 420);
        row.put("pressureBara", cell.getPressure());
        row.put("volumeM3", cell.getCorrectedVolume());
        row.put("relativeVolumeError", relative);
        diagnostics.add(row);
      }
    }
    assertTrue(originalError > 1e-5);
    assertArrayEquals(before, SerializationUtils.serialize(model));
    Files.write(Paths.get("target", "independent-gas-lab.json"),
        new Gson().toJson(diagnostics).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Creates a measured-pressure schedule with independently predicted gas-component withdrawals.
   *
   * @param model frozen laboratory model
   * @param input complete independent observations
   * @param holdoutShift held-out pressure perturbation, bara
   * @return immutable named history
   */
  private CompositionalReservoirHistory history(CalibratedPvtModel model, JsonObject input, double holdoutShift) {
    JsonArray rows = input.getAsJsonArray("rows");
    Step[] steps = new Step[rows.size()];
    for (int i = 0; i < steps.length; i++) {
      JsonObject row = rows.get(i).getAsJsonObject();
      JsonArray injected = row.getAsJsonArray("injectedMoles");
      boolean holdout = i == steps.length - 1;
      steps[i] = Step.phaseWithdrawal(86400, new double[] {100000, 0, 0},
          new double[] {injected.get(1).getAsDouble(), injected.get(0).getAsDouble()},
          row.get("pressureBara").getAsDouble() + (holdout ? holdoutShift : 0), .5, holdout, 4);
    }
    return new CompositionalReservoirHistory(model, "independent synthetic production/injection history",
        new String[] {"ethane", "methane"}, 400, 200, new double[] {1, 0, 0}, steps);
  }

  /**
   * Fits independent pressures and validates withheld pressure, composition and surface volume without EOS retuning.
   *
   * @throws Exception if complete inputs or diagnostics cannot be read/written
   */
  @Test
  void matchesIndependentHistoryAndPreservesTrialRestart() throws Exception {
    JsonObject input = reference();
    CalibratedPvtModel model = model(input);
    byte[] before = SerializationUtils.serialize(model);
    CompositionalReservoirHistory history = history(model, input, 0);
    double expectedVolume = input.get("initialVolumeM3").getAsDouble();
    Result direct = history.replay(expectedVolume);
    Result fit = history.fitVolume(800, 1300).getResult();
    Result shifted = history(model, input, 5).fitVolume(800, 1300).getResult();
    assertEquals(expectedVolume, fit.getInitialVolumeM3(), .001);
    assertEquals(fit.getInitialVolumeM3(), shifted.getInitialVolumeM3(), 0);
    assertEquals(100, shifted.getHoldoutChiSquare(), .001);
    assertTrue(fit.getHoldoutChiSquare() < 1e-8);
    assertThrows(IllegalStateException.class, () -> history.replay(1));
    assertArrayEquals(direct.getPressuresBara(), history.replay(expectedVolume).getPressuresBara(), 0);
    List<Map<String, Object>> diagnostics = new ArrayList<Map<String, Object>>();
    JsonArray rows = input.getAsJsonArray("rows");
    double[][] inventory = direct.getComponentMoles();
    assertEquals(input.getAsJsonArray("initialMoles").get(0).getAsDouble(), inventory[0][1], 1e-4);
    assertEquals(input.getAsJsonArray("initialMoles").get(1).getAsDouble(), inventory[0][0], 1e-4);
    for (int i = 0; i < rows.size(); i++) {
      JsonObject expected = rows.get(i).getAsJsonObject();
      assertEquals(expected.get("pressureBara").getAsDouble(), direct.getPressuresBara()[i + 1], 1e-6);
      assertEquals(expected.get("pressureBara").getAsDouble(), fit.getPressuresBara()[i + 1], 1e-6);
      double[] produced = direct.getProducedComponentMoles()[i];
      for (int component = 0; component < 2; component++) {
        int reversed = 1 - component;
        assertEquals(expected.getAsJsonArray("producedMoles").get(component).getAsDouble(), produced[reversed], 1e-4);
        assertEquals(expected.getAsJsonArray("producedMoles").get(component).getAsDouble(),
            fit.getProducedComponentMoles()[i][reversed], 1e-4);
        assertEquals(expected.getAsJsonArray("retainedMoles").get(component).getAsDouble(), inventory[i + 1][reversed],
            1e-4);
        assertEquals(inventory[i][reversed] + expected.getAsJsonArray("injectedMoles").get(component).getAsDouble()
            - produced[reversed], inventory[i + 1][reversed], 1e-9 * inventory[i][reversed]);
      }
      PvtSeparatorPath.Result surface = separator().separate(direct.getProducedFluid(i));
      assertEquals(1, surface.getVolumesM3()[0] / expected.get("surfaceGasVolumeM3").getAsDouble(), 1e-7);
      assertEquals(0, surface.getVolumesM3()[1], 0);
      assertTrue(Double.isNaN(surface.getGasOilRatio()));
      PvtSeparatorPath.Result fittedSurface = separator().separate(fit.getProducedFluid(i));
      assertEquals(1, fittedSurface.getVolumesM3()[0] / expected.get("surfaceGasVolumeM3").getAsDouble(), 1e-7);
      assertTrue(Double.isNaN(fittedSurface.getGasOilRatio()));
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("pressureBara", direct.getPressuresBara()[i + 1]);
      row.put("producedMolesEthaneMethane", produced);
      row.put("retainedMolesEthaneMethane", inventory[i + 1]);
      row.put("surfaceGasVolumeM3", surface.getVolumesM3()[0]);
      row.put("fittedPressureBara", fit.getPressuresBara()[i + 1]);
      row.put("fittedProducedMolesEthaneMethane", fit.getProducedComponentMoles()[i]);
      row.put("fittedSurfaceGasVolumeM3", fittedSurface.getVolumesM3()[0]);
      row.put("stockTankOilVolumeM3", surface.getVolumesM3()[1]);
      row.put("gasOilRatioStatus", "undefined: no stock-tank oil");
      diagnostics.add(row);
    }
    assertArrayEquals(before, SerializationUtils.serialize(model));
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("fittedVolumeM3", fit.getInitialVolumeM3());
    report.put("initialMolesEthaneMethane", inventory[0]);
    report.put("timesSeconds", direct.getTimesSeconds());
    report.put("modelProvenance", new Gson().fromJson(model.getProvenance(), JsonObject.class));
    report.put("holdoutChiSquare", fit.getHoldoutChiSquare());
    report.put("shiftedHoldoutChiSquare", shifted.getHoldoutChiSquare());
    report.put("rows", diagnostics);
    Files.write(Paths.get("target", "independent-gas-tank.json"),
        new Gson().toJson(report).getBytes(StandardCharsets.UTF_8));
  }
}
