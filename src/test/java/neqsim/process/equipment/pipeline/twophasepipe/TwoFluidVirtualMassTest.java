package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;

/** Tests for the stage-pure virtual-mass momentum coupling. */
class TwoFluidVirtualMassTest {

  @Test
  void identicalRhsEvaluationsAreBitwiseDeterministic() {
    TwoFluidConservationEquations equations = enabledEquations(0.5);
    TwoFluidSection[] sections = { createSection(0.0, 0.5), createSection(10.0, 0.5) };

    double[][] first = equations.calcRHS(sections, 10.0);
    double[][] second = equations.calcRHS(sections, 10.0);

    assertMatrixEquals(first, second, 0.0);

    final double[][] initialState = { createSection(0.0, 0.5).getStateVector() };
    TimeIntegrator.RHSFunction rhs = (state, time) -> {
      TwoFluidSection stageSection = createSection(0.0, 0.5);
      stageSection.setStateVector(state[0]);
      stageSection.extractPrimitiveVariables();
      double[][] rates = accelerationRates(stageSection, 100.0, -2.0);
      equations.applyVirtualMassCoupling(new TwoFluidSection[] { stageSection }, rates);
      return rates;
    };
    for (TimeIntegrator.Method method : TimeIntegrator.Method.values()) {
      TimeIntegrator integrator = new TimeIntegrator(method);
      double[][] integratedFirst = integrator.step(copy(initialState), rhs, 1.0e-4);
      double[][] integratedSecond = integrator.step(copy(initialState), rhs, 1.0e-4);
      assertMatrixEquals(integratedFirst, integratedSecond, 0.0);
      for (double value : integratedFirst[0]) {
        assertTrue(Double.isFinite(value), method + " returned a non-finite stage result");
      }
    }
  }

  @Test
  void matchesClosedFormTwoBodySolution() {
    TwoFluidConservationEquations equations = enabledEquations(0.5);
    TwoFluidSection section = createSection(0.0, 0.5);
    double[][] rates = new double[1][TwoFluidConservationEquations.NUM_EQUATIONS];
    rates[0][TwoFluidConservationEquations.IDX_GAS_MOMENTUM] = 8.0;
    rates[0][TwoFluidConservationEquations.IDX_OIL_MOMENTUM] = -2.0;

    equations.applyVirtualMassCoupling(new TwoFluidSection[] { section }, rates);

    assertEquals(0.5116279069767442, rates[0][TwoFluidConservationEquations.IDX_GAS_MOMENTUM], 1.0e-12);
    assertEquals(5.488372093023256, rates[0][TwoFluidConservationEquations.IDX_OIL_MOMENTUM], 1.0e-12);
    double gasAcceleration = rates[0][TwoFluidConservationEquations.IDX_GAS_MOMENTUM] / section.getGasMassPerLength();
    double liquidAcceleration = rates[0][TwoFluidConservationEquations.IDX_OIL_MOMENTUM]
        / section.getOilMassPerLength();
    assertEquals(4.767245737257357, gasAcceleration - liquidAcceleration, 1.0e-11);
  }

  @Test
  void preservesMixtureMomentumAndPartitionsLiquidByMass() {
    TwoFluidConservationEquations equations = enabledEquations(0.5);
    TwoFluidSection section = createThreePhaseSection();
    double[][] rates = new double[1][TwoFluidConservationEquations.NUM_EQUATIONS];
    rates[0][TwoFluidConservationEquations.IDX_GAS_MOMENTUM] = 8.0;
    rates[0][TwoFluidConservationEquations.IDX_OIL_MOMENTUM] = -1.0;
    rates[0][TwoFluidConservationEquations.IDX_WATER_MOMENTUM] = -1.0;
    double momentumBefore = totalMomentumRate(rates[0]);

    equations.applyVirtualMassCoupling(new TwoFluidSection[] { section }, rates);

    assertEquals(momentumBefore, totalMomentumRate(rates[0]), 0.0);
    double oilCorrection = rates[0][TwoFluidConservationEquations.IDX_OIL_MOMENTUM] + 1.0;
    double waterCorrection = rates[0][TwoFluidConservationEquations.IDX_WATER_MOMENTUM] + 1.0;
    assertEquals(section.getOilMassPerLength() / section.getWaterMassPerLength(), oilCorrection / waterCorrection,
        1.0e-12);
  }

  @Test
  void disabledAndZeroCoefficientPathsAreExactNoOps() {
    TwoFluidSection section = createSection(0.0, 0.5);
    double[][] reference = new double[1][TwoFluidConservationEquations.NUM_EQUATIONS];
    reference[0][TwoFluidConservationEquations.IDX_GAS_MOMENTUM] = 8.0;
    reference[0][TwoFluidConservationEquations.IDX_OIL_MOMENTUM] = -2.0;

    double[][] disabled = copy(reference);
    new TwoFluidConservationEquations().applyVirtualMassCoupling(new TwoFluidSection[] { section }, disabled);
    assertMatrixEquals(reference, disabled, 0.0);

    double[][] zeroCoefficient = copy(reference);
    TwoFluidConservationEquations equations = enabledEquations(0.0);
    equations.applyVirtualMassCoupling(new TwoFluidSection[] { section }, zeroCoefficient);
    assertMatrixEquals(reference, zeroCoefficient, 0.0);
  }

  @Test
  void couplingIsFiniteAcrossHoldupsAndCoefficientsAndSkipsAbsentLiquid() {
    double[] gasHoldups = { 1.0e-8, 0.1, 0.5, 0.9, 1.0 - 1.0e-8 };
    double[] coefficients = { 0.3, 0.5, 0.7 };
    for (double gasHoldup : gasHoldups) {
      double previousRelativeAcceleration = Double.POSITIVE_INFINITY;
      for (double coefficient : coefficients) {
        TwoFluidSection section = createSection(0.0, gasHoldup);
        double[][] rates = accelerationRates(section, 100.0, -2.0);
        enabledEquations(coefficient).applyVirtualMassCoupling(new TwoFluidSection[] { section }, rates);
        double relativeAcceleration = relativeAcceleration(section, rates[0]);
        assertTrue(Double.isFinite(relativeAcceleration));
        assertTrue(Math.abs(relativeAcceleration) < previousRelativeAcceleration);
        previousRelativeAcceleration = Math.abs(relativeAcceleration);
      }
    }

    TwoFluidSection gasOnly = createSection(0.0, 1.0);
    double[][] gasOnlyRates = accelerationRates(gasOnly, 100.0, 0.0);
    double[][] gasOnlyReference = copy(gasOnlyRates);
    enabledEquations(0.5).applyVirtualMassCoupling(new TwoFluidSection[] { gasOnly }, gasOnlyRates);
    assertMatrixEquals(gasOnlyReference, gasOnlyRates, 0.0);
  }

  @Test
  void serializationRoundTripRetainsStagePureConfiguration() throws Exception {
    TwoFluidConservationEquations original = enabledEquations(0.7);
    original.setTimestep(0.2);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }

    TwoFluidConservationEquations restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (TwoFluidConservationEquations) input.readObject();
    }

    assertTrue(restored.isVirtualMassForceEnabled());
    assertEquals(0.7, restored.getVirtualMassCoefficient(), 0.0);
    assertEquals(0.2, restored.getTimestep(), 0.0);
    TwoFluidSection section = createSection(0.0, 0.5);
    double[][] originalRates = accelerationRates(section, 100.0, -2.0);
    double[][] restoredRates = copy(originalRates);
    original.applyVirtualMassCoupling(new TwoFluidSection[] { section }, originalRates);
    restored.applyVirtualMassCoupling(new TwoFluidSection[] { section }, restoredRates);
    assertMatrixEquals(originalRates, restoredRates, 0.0);
  }

  private TwoFluidConservationEquations enabledEquations(double coefficient) {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableVirtualMassForce(true);
    equations.setVirtualMassCoefficient(coefficient);
    return equations;
  }

  private TwoFluidSection createSection(double position, double gasHoldup) {
    TwoFluidSection section = new TwoFluidSection(position, 10.0, 0.1, 0.0);
    double liquidHoldup = 1.0 - gasHoldup;
    section.setPressure(50.0e5);
    section.setTemperature(300.0);
    section.setGasDensity(20.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(800.0);
    section.setGasViscosity(1.5e-5);
    section.setOilViscosity(5.0e-3);
    section.setWaterViscosity(1.0e-3);
    section.setLiquidViscosity(5.0e-3);
    section.setGasSoundSpeed(300.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setGasEnthalpy(1.0e5);
    section.setLiquidEnthalpy(5.0e4);
    section.setSurfaceTension(0.025);
    section.setGasHoldup(gasHoldup);
    section.setLiquidHoldup(liquidHoldup);
    section.setOilHoldup(liquidHoldup);
    section.setWaterHoldup(0.0);
    section.setWaterCut(0.0);
    section.setOilFractionInLiquid(1.0);
    section.setGasVelocity(2.0);
    section.setLiquidVelocity(0.5);
    section.setOilVelocity(0.5);
    section.setWaterVelocity(0.5);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }

  private TwoFluidSection createThreePhaseSection() {
    TwoFluidSection section = createSection(0.0, 0.5);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(900.0);
    section.setLiquidHoldup(0.5);
    section.setOilHoldup(0.25);
    section.setWaterHoldup(0.25);
    section.setWaterCut(0.5);
    section.setOilFractionInLiquid(0.5);
    section.setOilVelocity(0.6);
    section.setWaterVelocity(0.4);
    section.updateConservativeVariables();
    section.updateWaterOilConservativeVariables();
    return section;
  }

  private double[][] accelerationRates(TwoFluidSection section, double gasAcceleration, double liquidAcceleration) {
    double[][] rates = new double[1][TwoFluidConservationEquations.NUM_EQUATIONS];
    rates[0][TwoFluidConservationEquations.IDX_GAS_MOMENTUM] = gasAcceleration * section.getGasMassPerLength();
    rates[0][TwoFluidConservationEquations.IDX_OIL_MOMENTUM] = liquidAcceleration * section.getOilMassPerLength();
    rates[0][TwoFluidConservationEquations.IDX_WATER_MOMENTUM] = liquidAcceleration * section.getWaterMassPerLength();
    return rates;
  }

  private double relativeAcceleration(TwoFluidSection section, double[] rates) {
    double gasAcceleration = rates[TwoFluidConservationEquations.IDX_GAS_MOMENTUM] / section.getGasMassPerLength();
    double liquidMass = section.getOilMassPerLength() + section.getWaterMassPerLength();
    double liquidAcceleration = (rates[TwoFluidConservationEquations.IDX_OIL_MOMENTUM]
        + rates[TwoFluidConservationEquations.IDX_WATER_MOMENTUM]) / liquidMass;
    return gasAcceleration - liquidAcceleration;
  }

  private double totalMomentumRate(double[] rates) {
    return rates[TwoFluidConservationEquations.IDX_GAS_MOMENTUM] + rates[TwoFluidConservationEquations.IDX_OIL_MOMENTUM]
        + rates[TwoFluidConservationEquations.IDX_WATER_MOMENTUM];
  }

  private double[][] copy(double[][] values) {
    double[][] result = new double[values.length][];
    for (int i = 0; i < values.length; i++) {
      result[i] = values[i].clone();
    }
    return result;
  }

  private void assertMatrixEquals(double[][] expected, double[][] actual, double tolerance) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      assertArrayEquals(expected[i], actual[i], tolerance);
    }
  }
}
