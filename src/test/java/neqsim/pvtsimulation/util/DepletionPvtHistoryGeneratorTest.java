package neqsim.pvtsimulation.util;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.blackoil.BlackOilPVTTable.Record;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.util.DepletionPvtHistoryGenerator.Result;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Native-path, separator-conservation and approximation-gate qualification using synthetic dry samples.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DepletionPvtHistoryGeneratorTest {
  private static final double TEMPERATURE = 323.15;
  private static final double[] PRESSURES = {200.0, 140.0, 80.0};

  /**
   * Defines a synthetic dry hydrocarbon sample that separates into gas and oil over the test grid.
   *
   * @return initialized original sample
   */
  private SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(TEMPERATURE, 300.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-decane", 0.15);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Freezes separate original/tuned checkpoints with explicit synthetic calibration provenance.
   *
   * @return accepted model fixture
   */
  private CalibratedPvtModel model() {
    return new CalibratedPvtModel(fluid(), fluid(), "synthetic dry hydrocarbon sample", "original SRK/classic",
        "accepted synthetic SRK/classic", "synthetic CCE and separator calibration", new String[] {"CCE-1", "SEP-1"});
  }

  /**
   * Defines a separate pure-water reference with an explicit CPA mixing rule.
   *
   * @return initialized reference water model
   */
  private SystemInterface water() {
    SystemInterface water = new SystemSrkCPAstatoil(TEMPERATURE, 200.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    water.init(0);
    return water;
  }

  /**
   * Runs the documented dry-sample protocol with two separator stages and a declared synthetic tolerance.
   *
   * @param path CVD or DLE
   * @param tolerance engineering approximation acceptance bound
   * @return accepted path evidence
   */
  private Result generate(Path path, double tolerance) {
    return DepletionPvtHistoryGenerator.generate(model(), path, "synthetic complete depletion run", TEMPERATURE,
        PRESSURES, new double[] {10.0, 1.01325}, new double[] {303.15, 288.15}, water(),
        "synthetic independently specified pure-water CPA model", tolerance,
        "synthetic conversion test bound; not a field acceptance recommendation");
  }

  /** Checks CVD snapshots, changed retained composition and agreement with the unchanged solver outputs. */
  @Test
  void cvdRecordsActualCompositionPathAndPreservesLegacyResults() {
    Result result = generate(Path.CVD, 0.1);
    ConstantVolumeDepletion independent = new ConstantVolumeDepletion(fluid());
    independent.setTemperature(TEMPERATURE, "K");
    independent.setPressures(PRESSURES);
    independent.runCalc();
    Assertions.assertEquals(0, independent.getDepletionSnapshots().length);
    DepletionSnapshot[] snapshots = result.getSnapshots();
    double initialDecane = snapshots[0].getRetainedFluid().getComponent("n-decane").getz();
    double finalDecane = snapshots[2].getRetainedFluid().getComponent("n-decane").getz();
    Assertions.assertTrue(finalDecane > initialDecane);
    for (int row = 0; row < PRESSURES.length; row++) {
      SystemInterface state = snapshots[row].getEquilibriumFluid();
      SystemInterface retained = snapshots[row].getRetainedFluid();
      Assertions.assertArrayEquals(state.getMolarComposition(), retained.getMolarComposition(), 0.0);
      Assertions.assertEquals(state.getTotalNumberOfMoles(), retained.getTotalNumberOfMoles(), 0.0);
      state.initProperties();
      double fraction = state.getPhase("oil").getCorrectedVolume() / state.getCorrectedVolume() * 100.0;
      Assertions.assertEquals(independent.getLiquidRelativeVolume()[row], fraction, 1.0e-7);
      Assertions.assertEquals(PRESSURES[row], state.getPressure(), 1.0e-12);
    }
    Assertions.assertEquals(independent.getThermoSystem().getComponent("n-decane").getz(), finalDecane, 1.0e-12);
    assertComponentClosure(snapshots);
    Assertions.assertTrue(result.getMaximumRelativeSurfaceClosureError() < result.getAcceptanceTolerance());
    Assertions.assertEquals(Path.CVD, result.getHistory().getPath());
    Assertions.assertTrue(result.getHistory().getPathReference().contains(result.getWaterReference()));
  }

  /** Checks DLE equilibrium-before-removal snapshots and native component conservation. */
  @Test
  void dlePreservesPreRemovalPhaseStatesAndIndependentLiquidDensity() {
    Result result = generate(Path.DLE, 0.1);
    DifferentialLiberation independent = new DifferentialLiberation(fluid());
    independent.setTemperature(TEMPERATURE, "K");
    independent.setPressures(PRESSURES);
    independent.runCalc();
    Assertions.assertEquals(0, independent.getDepletionSnapshots().length);
    DepletionSnapshot[] snapshots = result.getSnapshots();
    for (int row = 0; row < snapshots.length; row++) {
      SystemInterface equilibrium = snapshots[row].getEquilibriumFluid();
      equilibrium.initProperties();
      Assertions.assertTrue(equilibrium.hasPhaseType("gas"));
      Assertions.assertTrue(equilibrium.hasPhaseType("oil"));
      SystemInterface retained = snapshots[row].getRetainedFluid();
      Assertions.assertFalse(retained.hasPhaseType("gas"));
      Assertions.assertArrayEquals(equilibrium.getPhase("oil").getMolarComposition(),
          retained.getPhase("oil").getMolarComposition(), 0.0);
      Assertions.assertEquals(equilibrium.getPhase("oil").getNumberOfMolesInPhase(), retained.getTotalNumberOfMoles(),
          1.0e-12);
      Assertions.assertEquals(independent.getOilDensity()[row], equilibrium.getPhase("oil").getDensity("kg/m3"),
          1.0e-8);
      Assertions.assertEquals(PRESSURES[row], equilibrium.getPressure(), 1.0e-12);
    }
    assertComponentClosure(snapshots);
    Assertions.assertEquals(Path.DLE, result.getHistory().getPath());
  }

  /**
   * Independently checks native sample equals retained plus every withdrawn component amount.
   *
   * @param snapshots immutable recorded solver steps
   */
  private void assertComponentClosure(DepletionSnapshot[] snapshots) {
    SystemInterface initial = fluid();
    double[] produced = new double[initial.getNumberOfComponents()];
    for (DepletionSnapshot snapshot : snapshots) {
      double[] interval = snapshot.getProducedComponentMoles();
      SystemInterface retained = snapshot.getRetainedFluid();
      for (int i = 0; i < produced.length; i++) {
        produced[i] += interval[i];
        Assertions.assertEquals(initial.getComponent(i).getNumberOfmoles(),
            produced[i] + retained.getComponent(i).getNumberOfmoles(), 1.0e-9);
      }
    }
  }

  /** Checks one-stage limiting interpretation against independent direct phase flashes and water density. */
  @Test
  void oneStagePropertiesAgreeWithIndependentPhaseAndWaterFlashes() {
    Result result = DepletionPvtHistoryGenerator.generate(model(), Path.CVD, "one-stage reference", TEMPERATURE,
        PRESSURES, new double[] {1.01325}, new double[] {288.15}, water(), "pure-water CPA reference", 0.1,
        "synthetic direct-flash comparison tolerance");
    Record[] records = result.getHistory().getRecords();
    for (int row = 0; row < records.length; row++) {
      SystemInterface state = result.getSnapshots()[row].getEquilibriumFluid();
      state.initProperties();
      SystemInterface oil = SerializationUtils.deserialize(SerializationUtils.serialize(state));
      SystemInterface gas = SerializationUtils.deserialize(SerializationUtils.serialize(state));
      oil = oil.phaseToSystem("oil");
      gas = gas.phaseToSystem("gas");
      flashStandard(oil);
      flashStandard(gas);
      Assertions.assertEquals(oil.getPhase("gas").getCorrectedVolume() / oil.getPhase("oil").getCorrectedVolume(),
          records[row].Rs, 1.0e-9);
      Assertions.assertEquals(gas.getPhase("oil").getCorrectedVolume() / gas.getPhase("gas").getCorrectedVolume(),
          records[row].Rv, 1.0e-12);
      Assertions.assertEquals(state.getPhase("oil").getCorrectedVolume() / oil.getPhase("oil").getCorrectedVolume(),
          records[row].Bo, 1.0e-10);
      Assertions.assertEquals(state.getPhase("gas").getCorrectedVolume() / gas.getPhase("gas").getCorrectedVolume(),
          records[row].Bg, 1.0e-12);
      SystemInterface water = water();
      water.setPressure(PRESSURES[row]);
      new ThermodynamicOperations(water).TPflash();
      water.initProperties();
      double reservoirDensity = water.getDensity("kg/m3");
      flashStandard(water);
      Assertions.assertEquals(water.getDensity("kg/m3") / reservoirDensity, records[row].Bw, 1.0e-12);
    }
    Assertions.assertNotEquals(generate(Path.CVD, 0.1).getHistory().getRecords()[1].Rs, records[1].Rs, 1.0e-3);
  }

  /**
   * Independently flashes an extracted phase to the common stock-tank basis.
   *
   * @param fluid mutable isolated phase
   */
  private void flashStandard(SystemInterface fluid) {
    fluid.setPressure(1.01325);
    fluid.setTemperature(288.15);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
  }

  /** Checks failed approximation acceptance, unsupported paths and absent phases are rejected explicitly. */
  @Test
  void rejectsUnsupportedStatesAndUnacceptableApproximation() {
    IllegalStateException approximation = Assertions.assertThrows(IllegalStateException.class,
        () -> generate(Path.CVD, 1.0e-15));
    Assertions.assertTrue(approximation.getMessage().contains("surface-component closure error"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> generate(Path.CONSTANT_COMPOSITION, 0.1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DepletionPvtHistoryGenerator.generate(model(), Path.CVD, "invalid grid", TEMPERATURE,
            new double[] {80.0, 140.0, 200.0}, new double[] {1.01325}, new double[] {288.15}, water(), "water", 0.1,
            "acceptance"));
    SystemInterface wet = fluid();
    wet.addComponent("water", 0.01);
    wet.setMixingRule("classic");
    wet.init(0);
    CalibratedPvtModel wetModel = new CalibratedPvtModel(wet, wet, "wet synthetic", "original", "tuned",
        "laboratory fixture", new String[] {"wet-CCE"});
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DepletionPvtHistoryGenerator.generate(wetModel, Path.CVD, "unsupported wet path", TEMPERATURE, PRESSURES,
            new double[] {1.01325}, new double[] {288.15}, water(), "water", 0.1, "acceptance"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DepletionPvtHistoryGenerator.generate(model(), Path.CVD, "invalid Bw reference", TEMPERATURE, PRESSURES,
            new double[] {1.01325}, new double[] {288.15}, fluid(), "not water", 0.1, "acceptance"));
    Assertions.assertThrows(IllegalStateException.class,
        () -> DepletionPvtHistoryGenerator.generate(model(), Path.CVD, "above-saturation grid", TEMPERATURE,
            new double[] {900.0, 800.0, 700.0}, new double[] {1.01325}, new double[] {288.15}, water(), "water", 0.1,
            "acceptance"));
  }

  /** Checks deep ownership, serialization and the complete documented call. */
  @Test
  void preservesFrozenModelsAndOwnsProtocolEvidence() {
    CalibratedPvtModel model = model();
    String provenance = model.getProvenance();
    double[] originalComposition = model.getOriginalFluid().getMolarComposition();
    double[] tunedComposition = model.getTunedFluid().getMolarComposition();
    SystemInterface water = water();
    double waterPressure = water.getPressure();
    double[] pressures = PRESSURES.clone();
    Result result = DepletionPvtHistoryGenerator.generate(model, Path.CVD, "documented synthetic path", TEMPERATURE,
        pressures, new double[] {10.0, 1.01325}, new double[] {303.15, 288.15}, water, "separately accepted water EOS",
        0.1, "synthetic approximation acceptance");
    pressures[0] = Double.NaN;
    result.getRelativeSurfaceClosureResiduals()[1][0] = Double.NaN;
    result.getCumulativeSurfaceProductionSm3()[1][0] = Double.NaN;
    result.getSnapshots()[0].getRetainedFluid().setPressure(1.0);
    result.getSnapshots()[0].getProducedComponentMoles()[0] = Double.NaN;
    Assertions.assertEquals(provenance, model.getProvenance());
    Assertions.assertEquals(waterPressure, water.getPressure());
    Assertions.assertArrayEquals(originalComposition, model.getOriginalFluid().getMolarComposition(), 0.0);
    Assertions.assertArrayEquals(tunedComposition, model.getTunedFluid().getMolarComposition(), 0.0);
    result.getWaterFluid().setPressure(1.0);
    Assertions.assertEquals(waterPressure, result.getWaterFluid().getPressure());
    Assertions.assertEquals(200.0, result.getSnapshots()[0].getRetainedFluid().getPressure(), 1.0e-12);
    Assertions.assertTrue(Double.isFinite(result.getRelativeSurfaceClosureResiduals()[1][0]));
    Assertions.assertTrue(Double.isFinite(result.getCumulativeSurfaceProductionSm3()[1][0]));
    Result restored = SerializationUtils.deserialize(SerializationUtils.serialize(result));
    Assertions.assertEquals(result.getMaximumRelativeSurfaceClosureError(),
        restored.getMaximumRelativeSurfaceClosureError());
    Assertions.assertEquals(provenance, restored.getHistory().getModel().getProvenance());
    Assertions.assertEquals(0.0, restored.getCumulativeSurfaceProductionSm3()[0][0]);
    Assertions.assertEquals(0.0, restored.getCumulativeSurfaceProductionSm3()[0][1]);
  }
}
