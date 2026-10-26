package neqsim.process.equipment.pipeline.twophasepipe;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemIdealGas;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.system.SystemSrkCPAs;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Fixed-phase volume/internal-energy recovery regressions, including independent liquid compositions.
 *
 * @author NeqSim
 * @version 1.0
 */
class ConservativeThermodynamicRecoveryTest {
  /**
   * Construct one homogeneous phase without equilibrium redistribution.
   *
   * @param fluid fluid with components already added
   * @param type forced phase branch
   * @return initialized homogeneous state
   */
  private SystemInterface homogeneous(SystemInterface fluid, PhaseType type) {
    fluid.setMixingRule("classic");
    fluid.setNumberOfPhases(1);
    fluid.setMaxNumberOfPhases(1);
    fluid.setForcePhaseTypes(true);
    fluid.init(0);
    fluid.setNumberOfPhases(1);
    fluid.setBeta(0, 1.0);
    fluid.setPhaseType(0, type);
    fluid.initProperties();
    return fluid;
  }

  /**
   * Construct nitrogen at 300 K and 2 bara.
   *
   * @return homogeneous ideal gas
   */
  private SystemInterface nitrogen() {
    SystemInterface gas = new SystemIdealGas(300.0, 2.0);
    gas.addComponent("nitrogen", 1.0);
    return homogeneous(gas, PhaseType.GAS);
  }

  /**
   * Build an exact conservative cell from specified independent phase states and occupied fractions.
   *
   * @param fluids three homogeneous phase slots
   * @param fractions occupied volume fractions
   * @param velocities independent phase speeds in m/s
   * @return conservative cell at the common phase pressure/temperature
   */
  private TwoFluidSection cell(SystemInterface[] fluids, double[] fractions, double[] velocities) {
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 0.1, 0.0);
    double[] state = new double[7];
    double pressure = 0.0;
    double temperature = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      if (fluids[phase] != null) {
        pressure = fluids[phase].getPressure() * 1.0e5;
        temperature = fluids[phase].getTemperature();
        double density = fluids[phase].getPhase(0).getDensity("kg/m3");
        state[phase] = density * fractions[phase] * section.getArea();
        state[phase + 3] = state[phase] * velocities[phase];
        state[6] += state[phase]
            * (fluids[phase].getPhase(0).getEnthalpy("J/kg") + 0.5 * velocities[phase] * velocities[phase]);
      }
    }
    state[6] -= pressure * section.getArea();
    section.setPressure(pressure);
    section.setTemperature(temperature);
    section.setStateVector(state);
    return section;
  }

  /** Recover the ideal-gas P/T state while retaining independent kinetic energy and exact conserved variables. */
  @Test
  void recoversIdealGasVolumeAndEnergyWithoutMutatingInputs() {
    SystemInterface gas = nitrogen();
    SystemInterface[] phases = {gas, null, null};
    TwoFluidSection section = cell(phases, new double[] {1.0, 0.0, 0.0}, new double[] {125.0, 0.0, 0.0});
    double[] originalState = section.getStateVector();
    section.setPressure(1.8e5);
    section.setTemperature(290.0);
    ConservativeThermodynamicRecovery.Result result = ConservativeThermodynamicRecovery.recover(section, phases);
    Assertions.assertEquals(2.0e5, result.getPressurePa(), 1.0e-3);
    Assertions.assertEquals(300.0, result.getTemperatureK(), 1.0e-6);
    Assertions.assertEquals(290.0, section.getTemperature(), 0.0);
    Assertions.assertEquals(1.8e5, section.getPressure(), 0.0);
    Assertions.assertEquals(300.0, gas.getTemperature(), 0.0);
    Assertions.assertEquals(2.0, gas.getPressure(), 0.0);
    result.applyTo(section);
    Assertions.assertArrayEquals(originalState, section.getStateVector(), 0.0);
    Assertions.assertEquals(125.0, section.getGasVelocity(), 1.0e-12);
    Assertions.assertEquals(1.0, section.getGasHoldup(), 1.0e-9);
    Assertions.assertTrue(Math.abs(result.getRelativeVolumeResidual()) < 1.0e-9);
    Assertions.assertTrue(Math.abs(result.getEnergyResidualJPerMetre()) < 1.0e-5);
    double reconstructed = section.getGasMassPerLength()
        * (section.getGasEnthalpy() + 0.5 * section.getGasVelocity() * section.getGasVelocity())
        - section.getPressure() * section.getArea();
    Assertions.assertEquals(section.getEnergyPerLength(), reconstructed, 1.0e-5);
  }

  /** Common cubic and association EOS models recover a nearby methane state without changing U6 or momentum. */
  @Test
  void recoversMethaneWithSrkPrAndAssociationModels() {
    SystemInterface[] models = {new SystemSrkEos(300.0, 50.0), new SystemPrEos(300.0, 50.0),
        new SystemSrkCPA(300.0, 50.0), new SystemSrkCPAs(300.0, 50.0)};
    for (SystemInterface model : models) {
      model.addComponent("methane", 1.0);
      SystemInterface gas = homogeneous(model, PhaseType.GAS);
      SystemInterface[] phases = {gas, null, null};
      TwoFluidSection section = cell(phases, new double[] {1.0, 0.0, 0.0}, new double[] {17.0, 0.0, 0.0});
      double[] state = section.getStateVector();
      section.setPressure(47.0e5);
      section.setTemperature(295.0);
      ConservativeThermodynamicRecovery.Result result = ConservativeThermodynamicRecovery.recover(section, phases);
      String modelName = model.getClass().getSimpleName();
      Assertions.assertEquals(50.0e5, result.getPressurePa(), 0.1, modelName);
      Assertions.assertEquals(300.0, result.getTemperatureK(), 1.0e-5, modelName);
      result.applyTo(section);
      Assertions.assertArrayEquals(state, section.getStateVector(), 0.0, modelName);
      Assertions.assertEquals(17.0, section.getGasVelocity(), 1.0e-12, modelName);
      Assertions.assertEquals(section.getArea(), section.getGasMassPerLength() / section.getGasDensity(),
          section.getArea() * 1.0e-9, modelName);
      double reconstructed = section.getGasMassPerLength()
          * (section.getGasEnthalpy() + 0.5 * section.getGasVelocity() * section.getGasVelocity())
          - section.getPressure() * section.getArea();
      Assertions.assertEquals(state[6], reconstructed, Math.max(1.0, Math.abs(state[6])) * 2.0e-9, modelName);
      Assertions.assertEquals(1, result.getPhaseState(0).getNumberOfPhases(), modelName);
      Assertions.assertEquals(1.0, result.getPhaseState(0).getPhase(0).getComponent("methane").getx(), 1.0e-14,
          modelName);
      Assertions.assertEquals(300.0, gas.getTemperature(), 0.0, modelName);
      Assertions.assertEquals(50.0, gas.getPressure(), 0.0, modelName);
    }
  }

  /** Open-cell enthalpy removal must cool ideal gas even though the starting temperatures are spatially uniform. */
  @Test
  void enthalpyRemovalCoolsAndEnthalpyFillingHeatsIdealGas() {
    SystemInterface gas = nitrogen();
    SystemInterface[] phases = {gas, null, null};
    for (double massRatio : new double[] {0.99, 1.01}) {
      TwoFluidSection section = cell(phases, new double[] {1.0, 0.0, 0.0}, new double[3]);
      double[] state = section.getStateVector();
      double originalMass = state[0];
      state[0] *= massRatio;
      state[6] += (state[0] - originalMass) * gas.getPhase(0).getEnthalpy("J/kg");
      section.setStateVector(state);
      ConservativeThermodynamicRecovery.Result result = ConservativeThermodynamicRecovery.recover(section, phases);
      double gamma = gas.getCp("J/kgK") / gas.getCv("J/kgK");
      double constantHeatCapacityLimit = 300.0 * (1.0 + gamma * (massRatio - 1.0)) / massRatio;
      Assertions.assertEquals(constantHeatCapacityLimit, result.getTemperatureK(), 0.002);
      Assertions.assertTrue((result.getTemperatureK() - 300.0) * (massRatio - 1.0) > 0.0);
      result.applyTo(section);
      Assertions.assertArrayEquals(state, section.getStateVector(), 0.0);
    }
  }

  /** Fixed-volume heat input changes temperature/pressure while leaving all inventories untouched. */
  @Test
  void closedIsochoricHeatingUsesStoredEnergyAndNeverRestoresAnEmptyPhase() {
    SystemInterface gas = nitrogen();
    SystemInterface[] phases = {gas, null, null};
    TwoFluidSection section = cell(phases, new double[] {1.0, 0.0, 0.0}, new double[3]);
    double[] state = section.getStateVector();
    double heatInput = state[0] * gas.getCv("J/kgK") * 5.0;
    state[6] += heatInput;
    section.setStateVector(state);
    ConservativeThermodynamicRecovery.Result result = ConservativeThermodynamicRecovery.recover(section,
        new SystemInterface[] {gas, gas, gas});
    Assertions.assertEquals(305.0, result.getTemperatureK(), 0.01);
    Assertions.assertEquals(2.0e5 * result.getTemperatureK() / 300.0, result.getPressurePa(), 0.001);
    Assertions.assertNull(result.getPhaseState(1));
    Assertions.assertNull(result.getPhaseState(2));
    result.applyTo(section);
    Assertions.assertArrayEquals(state, section.getStateVector(), 0.0);
    Assertions.assertEquals(0.0, section.getOilMassPerLength(), 0.0);
    Assertions.assertEquals(0.0, section.getWaterMassPerLength(), 0.0);
  }

  /** Independent gas, oil and aqueous compositions are retained across a coupled P/T perturbation. */
  @Test
  void recoversThreePhaseFixedCompositionStateWithSlip() {
    SystemInterface gas = new SystemSrkEos(320.0, 50.0);
    gas.addComponent("methane", 0.99);
    gas.addComponent("n-heptane", 0.01);
    SystemInterface oil = new SystemSrkEos(320.0, 50.0);
    oil.addComponent("methane", 0.05);
    oil.addComponent("n-heptane", 0.95);
    SystemInterface water = new SystemSrkEos(320.0, 50.0);
    water.addComponent("methane", 0.001);
    water.addComponent("water", 0.999);
    SystemInterface[] phases = {homogeneous(gas, PhaseType.GAS), homogeneous(oil, PhaseType.LIQUID),
        homogeneous(water, PhaseType.AQUEOUS)};
    double[] fractions = {0.7, 0.2, 0.1};
    double[] velocities = {3.0, 1.0, -1.0};
    TwoFluidSection section = cell(phases, fractions, velocities);
    double[] state = section.getStateVector();
    section.setPressure(47.0e5);
    section.setTemperature(315.0);
    ConservativeThermodynamicRecovery.Result result = ConservativeThermodynamicRecovery.recover(section, phases);
    Assertions.assertEquals(50.0e5, result.getPressurePa(), 0.1);
    Assertions.assertEquals(320.0, result.getTemperatureK(), 1.0e-5);
    for (int phase = 0; phase < 3; phase++) {
      Assertions.assertArrayEquals(phases[phase].getMolarComposition(),
          result.getPhaseState(phase).getMolarComposition(), 1.0e-14);
      Assertions.assertEquals(1, result.getPhaseState(phase).getNumberOfPhases());
    }
    result.applyTo(section);
    Assertions.assertArrayEquals(state, section.getStateVector(), 0.0);
    Assertions.assertEquals(fractions[0], section.getGasHoldup(), 1.0e-8);
    Assertions.assertEquals(fractions[1], section.getOilHoldup(), 1.0e-8);
    Assertions.assertEquals(fractions[2], section.getWaterHoldup(), 1.0e-8);
    Assertions.assertEquals(velocities[1], section.getOilVelocity(), 1.0e-12);
    Assertions.assertEquals(velocities[2], section.getWaterVelocity(), 1.0e-12);
  }

  /** Inadmissible energy recovery leaves both the section and caller-owned phase state untouched. */
  @Test
  void failedRecoveryAndChangedStateApplicationAreTransactional() {
    SystemInterface gas = nitrogen();
    SystemInterface[] phases = {gas, null, null};
    TwoFluidSection section = cell(phases, new double[] {1.0, 0.0, 0.0}, new double[3]);
    ConservativeThermodynamicRecovery.Result result = ConservativeThermodynamicRecovery.recover(section, phases);
    double[] state = section.getStateVector();
    state[6] -= 1.0e8;
    section.setStateVector(state);
    Assertions.assertThrows(IllegalArgumentException.class, () -> result.applyTo(section));
    Assertions.assertThrows(IllegalStateException.class,
        () -> ConservativeThermodynamicRecovery.recover(section, phases));
    Assertions.assertArrayEquals(state, section.getStateVector(), 0.0);
    Assertions.assertEquals(300.0, section.getTemperature(), 0.0);
    Assertions.assertEquals(2.0e5, section.getPressure(), 0.0);
    Assertions.assertEquals(300.0, gas.getTemperature(), 0.0);
    Assertions.assertEquals(2.0, gas.getPressure(), 0.0);
  }

  /** Missing phase states, nonfinite inputs and momentum without mass must be rejected rather than repaired. */
  @Test
  void rejectsInvalidConservativeStateAndMissingPhaseComposition() {
    SystemInterface[] phases = {nitrogen(), null, null};
    TwoFluidSection section = cell(phases, new double[] {1.0, 0.0, 0.0}, new double[3]);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ConservativeThermodynamicRecovery.recover(section, new SystemInterface[3]));
    double[] state = section.getStateVector();
    state[4] = 1.0e-15;
    section.setStateVector(state);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ConservativeThermodynamicRecovery.recover(section, phases));
    state[4] = 0.0;
    state[6] = Double.NaN;
    section.setStateVector(state);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ConservativeThermodynamicRecovery.recover(section, phases));
  }
}
