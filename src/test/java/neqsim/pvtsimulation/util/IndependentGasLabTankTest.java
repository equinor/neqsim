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

  /**
   * Qualifies explicit gas-flow refinement against an independent continuous well-mixed balance, including
   * constant-inventory, injection-only, withdrawal-only and zero-flow limits.
   *
   * @throws Exception if the independent reference or complete diagnostics cannot be read or written
   */
  @Test
  void convergesToContinuousGasBalanceAndPreservesZeroFlowLimits() throws Exception {
    JsonObject input;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/gas-lab-tank/continuous-reference.json"),
        StandardCharsets.UTF_8)) {
      input = new Gson().fromJson(reader, JsonObject.class);
    }
    CalibratedPvtModel model = model(reference());
    byte[] before = SerializationUtils.serialize(model);
    List<Map<String, Object>> diagnostics = new ArrayList<Map<String, Object>>();
    for (int scenario = 0; scenario < input.getAsJsonArray("cases").size(); scenario++) {
      JsonObject testCase = input.getAsJsonArray("cases").get(scenario).getAsJsonObject();
      String name = testCase.get("name").getAsString();
      boolean mixing = scenario < 2;
      double previousError = Double.NaN;
      JsonArray schedule = testCase.getAsJsonArray("schedule");
      JsonArray refinements = testCase.getAsJsonArray("refinements");
      for (int refinement = 0; refinement < refinements.size(); refinement++) {
        JsonObject expected = refinements.get(refinement).getAsJsonObject();
        int subdivisions = expected.get("substeps").getAsInt();
        Step[] steps = new Step[schedule.size()];
        for (int row = 0; row < steps.length; row++) {
          JsonObject interval = schedule.get(row).getAsJsonObject();
          JsonArray injection = interval.getAsJsonArray("injectedMoles");
          steps[row] = Step.phaseWithdrawal(86400, new double[] {interval.get("producedMoles").getAsDouble(), 0, 0},
              new double[] {injection.get(1).getAsDouble(), injection.get(0).getAsDouble()}, Double.NaN, Double.NaN,
              false, subdivisions);
        }
        CompositionalReservoirHistory history = new CompositionalReservoirHistory(model,
            "independent continuous gas balance: " + name, new String[] {"ethane", "methane"}, 400, 200,
            new double[] {1, 0, 0}, steps);
        long start = System.nanoTime();
        Result result = history.replay(input.get("initialVolumeM3").getAsDouble());
        double seconds = (System.nanoTime() - start) / 1e9;
        double[][] inventory = result.getComponentMoles();
        double[][] production = result.getProducedComponentMoles();
        double componentError = 0;
        double finiteComponentError = 0;
        double finitePressureError = 0;
        double continuousPressureError = 0;
        double surfaceRelativeError = 0;
        double closure = 0;
        List<Map<String, Object>> rowDiagnostics = new ArrayList<Map<String, Object>>();
        for (int row = 0; row < steps.length; row++) {
          JsonObject finite = expected.getAsJsonArray("rows").get(row).getAsJsonObject();
          JsonObject exact = testCase.getAsJsonArray("continuous").get(row).getAsJsonObject();
          JsonObject interval = schedule.get(row).getAsJsonObject();
          for (int component = 0; component < 2; component++) {
            int reversed = 1 - component;
            double actual = inventory[row + 1][reversed];
            double finiteAmount = finite.getAsJsonArray("retainedMoles").get(component).getAsDouble();
            double exactAmount = exact.getAsJsonArray("retainedMoles").get(component).getAsDouble();
            assertEquals(finiteAmount, actual, 1e-4);
            assertEquals(finite.getAsJsonArray("producedMoles").get(component).getAsDouble(), production[row][reversed],
                1e-4);
            finiteComponentError = Math.max(finiteComponentError, Math.abs(actual - finiteAmount));
            componentError = Math.max(componentError, Math.abs(actual - exactAmount));
            double balance = inventory[row][reversed]
                + interval.getAsJsonArray("injectedMoles").get(component).getAsDouble() - production[row][reversed];
            assertEquals(balance, actual, 1e-9 * inventory[row][reversed]);
            closure = Math.max(closure, Math.abs(balance - actual));
          }
          double actualPressure = result.getPressuresBara()[row + 1];
          assertEquals(finite.get("pressureBara").getAsDouble(), actualPressure, 1e-6);
          finitePressureError = Math.max(finitePressureError,
              Math.abs(actualPressure - finite.get("pressureBara").getAsDouble()));
          continuousPressureError = Math.max(continuousPressureError,
              Math.abs(actualPressure - exact.get("pressureBara").getAsDouble()));
          Map<String, Object> rowReport = new LinkedHashMap<String, Object>();
          rowReport.put("pressureBara", actualPressure);
          rowReport.put("retainedMolesEthaneMethane", inventory[row + 1]);
          rowReport.put("producedMolesEthaneMethane", production[row]);
          if (interval.get("producedMoles").getAsDouble() > 0) {
            PvtSeparatorPath.Result surface = separator().separate(result.getProducedFluid(row));
            double relative = Math.abs(surface.getVolumesM3()[0] / finite.get("surfaceGasVolumeM3").getAsDouble() - 1);
            assertTrue(relative <= 1e-7);
            assertEquals(0, surface.getVolumesM3()[1], 0);
            assertTrue(Double.isNaN(surface.getGasOilRatio()));
            surfaceRelativeError = Math.max(surfaceRelativeError, relative);
            rowReport.put("surfaceGasVolumeM3", surface.getVolumesM3()[0]);
            rowReport.put("gasOilRatioStatus", "undefined: no stock-tank oil");
          } else {
            final int zeroProductionRow = row;
            assertThrows(IllegalStateException.class, () -> result.getProducedFluid(zeroProductionRow));
            rowReport.put("producedFluidStatus", "absent: zero production");
          }
          if (scenario == 1) {
            assertEquals(inventory[0][0] + inventory[0][1], inventory[row + 1][0] + inventory[row + 1][1], 1e-4);
          }
          rowDiagnostics.add(rowReport);
        }
        if (mixing) {
          if (refinement > 0) {
            assertTrue(componentError / previousError >= .45 && componentError / previousError <= .55,
                name + " first-order refinement at " + subdivisions);
          }
          if (subdivisions == 64) {
            assertTrue(componentError > 1, "64 substeps do not meet the frozen one-mole target");
          }
          if (subdivisions == 1024) {
            assertTrue(componentError <= 1, name + " one-mole continuous-limit target");
          }
        } else {
          assertTrue(componentError <= 1e-4);
          assertTrue(continuousPressureError <= 1e-6);
        }
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("case", name);
        report.put("substeps", subdivisions);
        report.put("seconds", seconds);
        report.put("maxContinuousComponentErrorMoles", componentError);
        report.put("maxContinuousPressureErrorBara", continuousPressureError);
        report.put("maxFiniteComponentErrorMoles", finiteComponentError);
        report.put("maxFinitePressureErrorBara", finitePressureError);
        report.put("maxFiniteSurfaceRelativeError", surfaceRelativeError);
        report.put("maxComponentClosureMoles", closure);
        if (mixing && refinement > 0) {
          report.put("refinementErrorRatio", componentError / previousError);
        }
        report.put("rows", rowDiagnostics);
        diagnostics.add(report);
        previousError = componentError;
      }
    }
    assertArrayEquals(before, SerializationUtils.serialize(model));
    Files.write(Paths.get("target", "independent-gas-continuous.json"),
        new Gson().toJson(diagnostics).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Checks noisy pressure-sigma weighting, scale invariance, held-out exclusion and bound diagnostics against
   * independent physical-volume minimization, without EOS retuning.
   *
   * @throws Exception if the independent reference or complete diagnostics cannot be read or written
   */
  @Test
  void matchesIndependentUnequalSigmaAndBoundedInversion() throws Exception {
    JsonObject input;
    try (InputStreamReader reader = new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/gas-lab-tank/weighted-reference.json"),
        StandardCharsets.UTF_8)) {
      input = new Gson().fromJson(reader, JsonObject.class);
    }
    CalibratedPvtModel model = model(reference());
    byte[] before = SerializationUtils.serialize(model);
    List<Map<String, Object>> diagnostics = new ArrayList<Map<String, Object>>();
    List<Result> results = new ArrayList<Result>();
    for (int scenario = 0; scenario < input.getAsJsonArray("cases").size(); scenario++) {
      JsonObject testCase = input.getAsJsonArray("cases").get(scenario).getAsJsonObject();
      JsonArray rows = testCase.getAsJsonArray("rows");
      Step[] steps = new Step[rows.size()];
      for (int row = 0; row < rows.size(); row++) {
        JsonArray injection = rows.get(row).getAsJsonObject().getAsJsonArray("injectedMoles");
        steps[row] = Step.phaseWithdrawal(86400, new double[] {100000, 0, 0},
            new double[] {injection.get(1).getAsDouble(), injection.get(0).getAsDouble()},
            testCase.getAsJsonArray("observedPressuresBara").get(row).getAsDouble(),
            testCase.getAsJsonArray("sigmasBara").get(row).getAsDouble(), row == rows.size() - 1, 4);
      }
      CompositionalReservoirHistory history = new CompositionalReservoirHistory(model,
          "independent noisy-pressure inversion: " + testCase.get("name").getAsString(),
          new String[] {"ethane", "methane"}, 400, 200, new double[] {1, 0, 0}, steps);
      long start = System.nanoTime();
      CompositionalReservoirHistory.Fit fit = history.fitVolume(testCase.get("lowerVolumeM3").getAsDouble(),
          testCase.get("upperVolumeM3").getAsDouble());
      double seconds = (System.nanoTime() - start) / 1e9;
      Result result = fit.getResult();
      results.add(result);
      assertEquals(testCase.get("fittedVolumeM3").getAsDouble(), result.getInitialVolumeM3(), .001);
      assertEquals(testCase.get("calibrationChiSquare").getAsDouble(), result.getCalibrationChiSquare(), 1e-6);
      assertEquals(testCase.get("boundLimited").getAsBoolean(), fit.isAtBound());
      if (fit.isAtBound()) {
        assertEquals(testCase.get("upperVolumeM3").getAsDouble(), result.getInitialVolumeM3(), 0);
      }
      assertArrayEquals(result.getPressuresBara(), history.replay(result.getInitialVolumeM3()).getPressuresBara(), 0);
      double[] residuals = result.getStandardizedResiduals();
      double chiSquare = 0;
      List<Map<String, Object>> rowReports = new ArrayList<Map<String, Object>>();
      for (int row = 0; row < rows.size(); row++) {
        JsonObject expected = rows.get(row).getAsJsonObject();
        assertEquals(expected.get("pressureBara").getAsDouble(), result.getPressuresBara()[row + 1], 1e-6);
        assertEquals(testCase.getAsJsonArray("standardizedResiduals").get(row).getAsDouble(), residuals[row], 1e-5);
        if (row < rows.size() - 1) {
          chiSquare += residuals[row] * residuals[row];
        }
        double[] produced = result.getProducedComponentMoles()[row];
        for (int component = 0; component < 2; component++) {
          assertEquals(expected.getAsJsonArray("producedMoles").get(component).getAsDouble(), produced[1 - component],
              1e-4);
        }
        PvtSeparatorPath.Result surface = separator().separate(result.getProducedFluid(row));
        assertEquals(1, surface.getVolumesM3()[0] / expected.get("surfaceGasVolumeM3").getAsDouble(), 1e-7);
        assertEquals(0, surface.getVolumesM3()[1], 0);
        assertTrue(Double.isNaN(surface.getGasOilRatio()));
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("pressureBara", result.getPressuresBara()[row + 1]);
        report.put("standardizedResidual", residuals[row]);
        report.put("producedMolesEthaneMethane", produced);
        report.put("surfaceGasVolumeM3", surface.getVolumesM3()[0]);
        rowReports.add(report);
      }
      assertEquals(chiSquare, result.getCalibrationChiSquare(), 1e-10);
      assertEquals(residuals[3] * residuals[3], result.getHoldoutChiSquare(), 1e-10);
      Map<String, Object> report = new LinkedHashMap<String, Object>();
      report.put("case", testCase.get("name").getAsString());
      report.put("seconds", seconds);
      report.put("fittedVolumeM3", result.getInitialVolumeM3());
      report.put("calibrationChiSquare", result.getCalibrationChiSquare());
      report.put("holdoutChiSquare", result.getHoldoutChiSquare());
      report.put("boundLimited", fit.isAtBound());
      report.put("rows", rowReports);
      diagnostics.add(report);
    }
    assertTrue(Math.abs(results.get(0).getInitialVolumeM3() - results.get(1).getInitialVolumeM3()) > 1);
    assertEquals(results.get(0).getInitialVolumeM3(), results.get(2).getInitialVolumeM3(), .001);
    assertEquals(results.get(0).getCalibrationChiSquare() / 9, results.get(2).getCalibrationChiSquare(), 1e-6);
    assertEquals(results.get(0).getInitialVolumeM3(), results.get(3).getInitialVolumeM3(), 0);
    assertEquals(results.get(0).getCalibrationChiSquare(), results.get(3).getCalibrationChiSquare(), 0);
    assertArrayEquals(before, SerializationUtils.serialize(model));
    Files.write(Paths.get("target", "independent-gas-weighted.json"),
        new Gson().toJson(diagnostics).getBytes(StandardCharsets.UTF_8));
  }
}
