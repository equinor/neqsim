package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.blackoil.BlackOilPVTTable.Record;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Synthetic protocol replay, checkpoint ownership and measured-history handoff checks.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CalibratedPvtModelTest {
  /**
   * Creates a synthetic sample with an independently specified CO2/hydrocarbon interaction.
   *
   * @param interaction CO2/heavy-component BIP
   * @return normalized model
   */
  private SystemInterface fluid(double interaction) {
    SystemInterface fluid = new SystemSrkEos(323.15, 300.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-decane", 0.11);
    fluid.addComponent("CO2", 0.04);
    fluid.setMixingRule("classic");
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule().setBinaryInteractionParameter(3, 4, interaction);
      ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule().setBinaryInteractionParameter(4, 3, interaction);
    }
    fluid.init(0);
    return fluid;
  }

  /**
   * Freezes original and separately accepted synthetic laboratory models.
   *
   * @return immutable checkpoint bundle
   */
  private CalibratedPvtModel model() {
    return new CalibratedPvtModel(fluid(0.0), fluid(0.15), "synthetic sample v1", "original v1", "tuned v2",
        "synthetic calibration report; NeqSim test runtime", new String[] {"calibration-CVD"});
  }

  /**
   * Parses an evidence document.
   *
   * @param value JSON evidence
   * @return parsed object
   */
  private JsonObject json(String value) {
    return JsonParser.parseString(value).getAsJsonObject();
  }

  /** Snapshots own all nested EOS state and survive serialization without exposing mutable model references. */
  @Test
  void immutableCheckpointsRetainBothModelsAndProvenance() {
    SystemInterface original = fluid(0.0);
    SystemInterface tuned = fluid(0.15);
    String originalCapture = PVTFluidStudy.capture(original);
    String tunedCapture = PVTFluidStudy.capture(tuned);
    String[] ids = {"calibration-CVD"};
    CalibratedPvtModel bundle = new CalibratedPvtModel(original, tuned, "sample v1", "original v1", "tuned v2",
        "lab protocol v1", ids);
    ids[0] = "changed";
    original.setTemperature(500.0);
    tuned.setPressure(50.0);
    SystemInterface exposed = bundle.getTunedFluid();
    ((PhaseEosInterface) exposed.getPhase(0)).getEosMixingRule().setBinaryInteractionParameter(3, 4, 0.9);
    CalibratedPvtModel restored = SerializationUtils.clone(bundle);
    assertEquals(originalCapture, PVTFluidStudy.capture(restored.getOriginalFluid()));
    assertEquals(tunedCapture, PVTFluidStudy.capture(restored.getTunedFluid()));
    assertEquals("calibration-CVD",
        json(bundle.getProvenance()).getAsJsonArray("calibrationExperiments").get(0).getAsString());
    assertNotEquals(originalCapture, tunedCapture);
    assertThrows(IllegalArgumentException.class, () -> bundle.compareHoldout(" calibration-CVD ", "CVD", 323.15,
        new double[] {300, 150, 50}, "gasZ", new double[] {1, 1, 1}, new double[] {0.01, 0.01, 0.01}));
  }

  /** Full depletion schedules agree with separately executed laboratory-solver references. */
  @Test
  void completeCceCvdAndDleHoldoutsRetainOriginalAndTunedResiduals() {
    double[] pressures = {300, 150, 50, 20};
    double[] sigma = {0.01, 0.01, 0.01, 0.01};
    ConstantMassExpansion cce = new ConstantMassExpansion(fluid(0.15));
    cce.setTemperature(323.15, "K");
    cce.setPressures(pressures.clone());
    cce.runCalc();
    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid(0.15));
    cvd.setTemperature(323.15, "K");
    cvd.setPressures(pressures.clone());
    cvd.runCalc();
    DifferentialLiberation dle = new DifferentialLiberation(fluid(0.15));
    dle.setTemperature(323.15, "K");
    dle.setPressures(pressures.clone());
    dle.runCalc();
    String[] kinds = {"CCE", "CVD", "DLE"};
    String[] properties = {"relativeVolume", "liquidRelativeVolume", "boM3PerSm3"};
    double[][] observations = {cce.getRelativeVolume(), cvd.getLiquidRelativeVolume(), dle.getBo()};
    boolean originalDiffers = false;
    CalibratedPvtModel bundle = model();
    String before = PVTFluidStudy.capture(bundle.getTunedFluid());
    for (int experiment = 0; experiment < kinds.length; experiment++) {
      JsonObject result = json(bundle.compareHoldout("holdout-" + kinds[experiment], kinds[experiment], 323.15,
          pressures, properties[experiment], observations[experiment], sigma));
      JsonArray rows = result.getAsJsonArray("rows");
      assertEquals(pressures.length, rows.size());
      for (int i = 0; i < rows.size(); i++) {
        JsonObject row = rows.get(i).getAsJsonObject();
        assertEquals(observations[experiment][i], row.get("tunedPrediction").getAsDouble(), 1e-10);
        assertEquals(0.0, row.get("tunedResidual").getAsDouble(), 1e-10);
        assertEquals(row.get("originalResidual").getAsDouble() / sigma[i],
            row.get("originalStandardizedResidual").getAsDouble(), 1e-10);
        originalDiffers |= Math.abs(row.get("originalResidual").getAsDouble()) > 1e-7;
      }
    }
    assertTrue(originalDiffers, "A changed EOS must remain visible in the untuned residuals");
    assertEquals(before, PVTFluidStudy.capture(bundle.getTunedFluid()));
  }

  /** Separator replay preserves every stage and the common standard basis. */
  @Test
  void heldoutSeparatorMatchesIndependentMultiStageCalculation() {
    double[] pressures = {50, 10, 1.01325};
    double[] temperatures = {303.15, 293.15, 288.15};
    MultiStageSeparatorTest reference = new MultiStageSeparatorTest(fluid(0.15));
    reference.setReservoirConditions(300.0, 50.0);
    for (int i = 0; i < pressures.length; i++) {
      reference.addSeparatorStage(pressures[i], temperatures[i] - 273.15);
    }
    reference.run();
    JsonObject result = json(model().compareSeparatorHoldout("holdout-separator", 323.15, 300.0, pressures,
        temperatures, "totalGORSm3PerSm3", reference.getTotalGOR(), 1.0));
    assertEquals(0.0, result.getAsJsonArray("rows").get(0).getAsJsonObject().get("tunedResidual").getAsDouble(), 1e-10);
    assertEquals(3, result.getAsJsonArray("separatorStages").size());
    temperatures[2] = 298.15;
    assertThrows(IllegalArgumentException.class, () -> model().compareSeparatorHoldout("holdout-separator", 323.15,
        300.0, pressures, temperatures, "totalGORSm3PerSm3", 100, 1));
  }

  /** Invalid measurements/protocols cannot be presented as independent residual evidence. */
  @Test
  void rejectsIncompleteNonfiniteAndUnsupportedHoldouts() {
    CalibratedPvtModel bundle = model();
    double[] pressures = {300, 150, 50};
    assertThrows(IllegalArgumentException.class, () -> bundle.compareHoldout("holdout", "DLE", 323.15, pressures,
        "boM3PerSm3", new double[] {1}, new double[] {1}));
    assertThrows(IllegalArgumentException.class, () -> bundle.compareHoldout("holdout", "DLE", 323.15, pressures,
        "boM3PerSm3", new double[] {1, 1, 1}, new double[] {1, 0, 1}));
    assertThrows(IllegalArgumentException.class, () -> bundle.compareHoldout("holdout", "DLE", 323.15, pressures,
        "boM3PerSm3", new double[] {1, Double.NaN, 1}, new double[] {1, 1, 1}));
    assertThrows(IllegalArgumentException.class, () -> bundle.compareHoldout("holdout", "TP", 323.15, pressures,
        "boM3PerSm3", new double[] {1, 1, 1}, new double[] {1, 1, 1}));
    assertThrows(IllegalArgumentException.class, () -> bundle.compareHoldout("holdout", "DLE", 323.15,
        new double[] {300, 50, 150}, "boM3PerSm3", new double[] {1, 1, 1}, new double[] {1, 1, 1}));
    assertThrows(IllegalArgumentException.class, () -> new CalibratedPvtModel(fluid(0), fluid(0), "sample", "v1", "v2",
        "lab", new String[] {"duplicate", " duplicate "}));
  }

  /** Pressure matching, standard basis and path identity gate the existing generalized inverse calculation. */
  @Test
  void alignedSyntheticHistoryRecoversKnownGasInventoryAndRejectsFalsePaths() {
    Record[] records = {new Record(300, 0, 1, 0, 0.004, 0, 0, 1, 0), new Record(200, 0, 1, 0, 0.006, 0, 0, 1, 0),
        new Record(100, 0, 1, 0, 0.01, 0, 0, 1, 0)};
    double[] pressure = {300, 200, 100};
    double[] zero = {0, 0, 0};
    double inventory = 1e9;
    double[] gas = {0, inventory * (1 - 0.004 / 0.006), inventory * (1 - 0.004 / 0.01)};
    MaterialBalancePvtHistory history = new MaterialBalancePvtHistory(model(), MaterialBalancePvtHistory.Path.CVD,
        "synthetic independently evaluated gas path v1", 323.15, new double[] {50, 1.01325},
        new double[] {303.15, 288.15}, records);
    records[1] = records[0];
    assertEquals(inventory, history.materialBalance(pressure, zero, gas, zero, zero, zero).fitInitiallyGas().getOgip(),
        1e-5);
    MaterialBalancePvtHistory restored = SerializationUtils.clone(history);
    restored.getRecords()[1] = null;
    restored.getSeparatorTemperaturesK()[1] = 500;
    assertEquals(288.15, restored.getSeparatorTemperaturesK()[1]);
    assertEquals(200, restored.getRecords()[1].p);
    assertEquals(MaterialBalancePvtHistory.Path.CVD, restored.getPath());
    pressure[1] = 190;
    assertThrows(IllegalArgumentException.class, () -> history.materialBalance(pressure, zero, gas, zero, zero, zero));
    pressure[1] = 200;
    gas[0] = 1;
    assertThrows(IllegalArgumentException.class, () -> history.materialBalance(pressure, zero, gas, zero, zero, zero));
    gas[0] = 0;
    MaterialBalancePvtHistory cce = new MaterialBalancePvtHistory(model(),
        MaterialBalancePvtHistory.Path.CONSTANT_COMPOSITION, "independent flashes", 323.15, new double[] {1.01325},
        new double[] {288.15}, restored.getRecords());
    assertThrows(IllegalArgumentException.class, () -> cce.materialBalance(pressure, zero, gas, zero, zero, zero));
    assertThrows(IllegalArgumentException.class,
        () -> history.materialBalance(pressure, new double[1], gas, zero, zero, zero));
  }

  /** Invalid path evidence and numeric rows cannot enter an inverse history calculation. */
  @Test
  void rejectsInvalidPathRecordsAndHistoryValues() {
    Record[] records = {new Record(300, 0, 1, 0, 0.004, 0, 0, 1, 0), new Record(200, 0, 1, 0, 0.006, 0, 0, 1, 0),
        new Record(100, 0, 1, 0, 0.01, 0, 0, 1, 0)};
    CalibratedPvtModel bundle = model();
    double[] standardPressure = {1.01325};
    double[] standardTemperature = {288.15};
    MaterialBalancePvtHistory history = new MaterialBalancePvtHistory(bundle, MaterialBalancePvtHistory.Path.DLE,
        "synthetic path reference", 323.15, standardPressure, standardTemperature, records);
    double[] pressure = {300, 200, 100};
    double[] zero = {0, 0, 0};
    assertThrows(IllegalArgumentException.class,
        () -> history.materialBalance(pressure, zero, new double[] {0, Double.NaN, 1}, zero, zero, zero));
    assertThrows(IllegalArgumentException.class,
        () -> history.materialBalance(pressure, zero, zero, zero, new double[] {1, 1, 1}, zero));
    records[1] = records[0];
    assertThrows(IllegalArgumentException.class, () -> new MaterialBalancePvtHistory(bundle,
        MaterialBalancePvtHistory.Path.CVD, "path", 323.15, standardPressure, standardTemperature, records));
    records[1] = new Record(200, 100, 1, 0, 0.006, 0, 0.01, 1, 0);
    assertThrows(IllegalArgumentException.class, () -> new MaterialBalancePvtHistory(bundle,
        MaterialBalancePvtHistory.Path.DLE, "path", 323.15, standardPressure, standardTemperature, records));
    assertThrows(IllegalArgumentException.class, () -> bundle.compareSeparatorHoldout("holdout", 323.15, 20,
        new double[] {50, 1.01325}, new double[] {303.15, 288.15}, "boM3PerSm3", 1.2, 0.01));
  }

}
