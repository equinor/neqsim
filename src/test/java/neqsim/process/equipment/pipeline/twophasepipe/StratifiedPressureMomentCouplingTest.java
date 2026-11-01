package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedPressureMoments;

/** Conservative operator qualification for circular-stratified hydrostatic pressure moments. */
class StratifiedPressureMomentCouplingTest {
  private static final double GRAVITY = 9.81;
  private static final double DIAMETER = 1.0148;
  private static final double LENGTH = 10.0;
  private static final double GAS_DENSITY = 10.0;
  private static final double OIL_DENSITY = 800.0;
  private static final double WATER_DENSITY = 1000.0;

  /** Uniform flow receives the analytical face traction and matching donor-volume pressure work. */
  @Test
  void analyticalTractionAndSignedPressureWorkShareTheMassDonor() {
    for (double velocity : new double[] {-2.0, 2.0}) {
      TwoFluidSection[] cells = {section(0, DIAMETER, 0.4, 0.6, 0.0, velocity, velocity),
          section(1, DIAMETER, 0.4, 0.6, 0.0, velocity, velocity)};
      TwoFluidSection outletDonor = cells[1].clone();
      outletDonor.setGasDensity(2.5 * GAS_DENSITY);
      outletDonor.setOilDensity(0.75 * OIL_DENSITY);
      outletDonor.setLiquidDensity(0.75 * OIL_DENSITY);
      outletDonor.updateConservativeVariables();
      TwoFluidConservationEquations baseline = equations(false, false);
      baseline.setClosedBoundaries(false, false);
      baseline.setAllowOutletPhaseBackflow(true);
      baseline.setOutletBoundaryState(outletDonor);
      baseline.calcRHS(copy(cells), LENGTH);
      double[] baselineEnergy = baseline.getLastEnergyFaceFluxes();

      TwoFluidConservationEquations coupled = equations(true, false);
      coupled.setClosedBoundaries(false, false);
      coupled.setAllowOutletPhaseBackflow(true);
      coupled.setOutletBoundaryState(outletDonor);
      coupled.calcRHS(copy(cells), LENGTH);
      double[][] forces = coupled.getLastStratifiedHydrostaticPressureForces();
      double depth = new GeometryCalculator().calculateFromHoldup(0.6, DIAMETER).liquidLevel;
      StratifiedPressureMoments moments = new GeometryCalculator().calculateStratifiedPressureMoments(depth, DIAMETER);
      double expectedGasForce = GAS_DENSITY * GRAVITY * moments.getGasMoment();
      double expectedOilForce = OIL_DENSITY * GRAVITY * moments.getLiquidMoment();

      assertEquals(3, forces.length);
      for (double[] face : forces) {
        assertEquals(expectedGasForce, face[0], 1.0e-10);
        assertEquals(expectedOilForce, face[1], 1.0e-9);
        assertEquals(0.0, face[2], 0.0);
      }
      double expectedWork = velocity * (expectedGasForce + expectedOilForce);
      for (int face = 0; face < forces.length; face++) {
        assertEquals(expectedWork, coupled.getLastEnergyFaceFluxes()[face] - baselineEnergy[face], 1.0e-7);
      }
      if (velocity > 0.0) {
        for (double[] rate : coupled.calcRHS(copy(cells), LENGTH)) {
          assertEquals(0.0, rate[TwoFluidConservationEquations.IDX_ENERGY], 2.0e-7);
        }
      }
    }
  }

  /** Internal pressure traction cancels pairwise and leaves only the two declared boundary forces. */
  @Test
  void closedDomainMomentumLedgerIsConservativeOnNonuniformHoldup() {
    TwoFluidSection[] cells = {section(0, DIAMETER, 0.8, 0.2, 0.0, 0.0, 0.0),
        section(1, DIAMETER, 0.5, 0.5, 0.0, 0.0, 0.0), section(2, DIAMETER, 0.2, 0.8, 0.0, 0.0, 0.0)};
    TwoFluidConservationEquations equations = equations(true, false);
    double[][] rates = equations.calcRHS(cells, LENGTH);
    double[][] forces = equations.getLastStratifiedHydrostaticPressureForces();

    for (int phase = 0; phase < 3; phase++) {
      double integratedRate = 0.0;
      for (int cell = 0; cell < cells.length; cell++) {
        integratedRate += rates[cell][TwoFluidConservationEquations.IDX_GAS_MOMENTUM + phase] * cells[cell].getLength();
        assertEquals(0.0, rates[cell][phase], 0.0);
      }
      assertEquals(forces[0][phase] - forces[forces.length - 1][phase], integratedRate, 1.0e-8);
    }
    for (double[] rate : rates) {
      assertEquals(0.0, rate[TwoFluidConservationEquations.IDX_ENERGY], 0.0);
    }
  }

  /** Exact single-phase limits retain the present phase moment and never seed an absent phase. */
  @Test
  void exactPhaseLimitsAndVerticalOrientationAreResolvedWithoutSeeds() {
    double area = Math.PI * DIAMETER * DIAMETER / 4.0;
    double wholeMoment = 0.5 * DIAMETER * area;
    double[][] cases = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
    double[] expected = {-GAS_DENSITY * GRAVITY * wholeMoment, OIL_DENSITY * GRAVITY * wholeMoment,
        WATER_DENSITY * GRAVITY * wholeMoment};
    for (int present = 0; present < cases.length; present++) {
      TwoFluidSection cell = section(0, DIAMETER, cases[present][0], cases[present][1], cases[present][2], 0.0, 0.0);
      TwoFluidConservationEquations equations = equations(true, false);
      equations.calcRHS(new TwoFluidSection[] {cell}, LENGTH);
      double[][] forces = equations.getLastStratifiedHydrostaticPressureForces();
      for (double[] face : forces) {
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(phase == present ? expected[phase] : 0.0, face[phase], 1.0e-9);
        }
      }
    }

    TwoFluidSection vertical = section(0, DIAMETER, 0.4, 0.6, 0.0, 0.0, 0.0);
    vertical.setInclination(Math.PI / 2.0);
    TwoFluidConservationEquations equations = equations(true, false);
    equations.calcRHS(new TwoFluidSection[] {vertical}, LENGTH);
    for (double[] face : equations.getLastStratifiedHydrostaticPressureForces()) {
      assertArrayEquals(new double[3], face, 1.0e-11);
    }
  }

  /** One-interface ownership rejects unsupported liquid layering and discontinuous face geometry. */
  @Test
  void unsupportedInterfacesAndDiameterStepsFailClosed() {
    TwoFluidSection threePhase = section(0, DIAMETER, 0.4, 0.3, 0.3, 0.0, 0.0);
    IllegalStateException layering = assertThrows(IllegalStateException.class,
        () -> equations(true, true).calcRHS(new TwoFluidSection[] {threePhase}, LENGTH));
    assertTrue(layering.getMessage().contains("simultaneous oil and water"));

    TwoFluidSection left = section(0, DIAMETER, 0.4, 0.6, 0.0, 0.0, 0.0);
    TwoFluidSection right = section(1, 0.9 * DIAMETER, 0.4, 0.6, 0.0, 0.0, 0.0);
    IllegalStateException diameter = assertThrows(IllegalStateException.class,
        () -> equations(true, false).calcRHS(new TwoFluidSection[] {left, right}, LENGTH));
    assertTrue(diameter.getMessage().contains("constant face diameter"));
  }

  /** Trial evaluations restore the last accepted force ledger, including after rejection. */
  @Test
  void transactionalEvaluationRestoresAcceptedForceLedger() {
    TwoFluidConservationEquations equations = equations(true, false);
    TwoFluidSection[] accepted = {section(0, DIAMETER, 0.4, 0.6, 0.0, 0.0, 0.0),
        section(1, DIAMETER, 0.4, 0.6, 0.0, 0.0, 0.0)};
    equations.calcRHS(accepted, LENGTH);
    double[][] acceptedForces = equations.getLastStratifiedHydrostaticPressureForces();

    TwoFluidSection[] trial = {section(0, DIAMETER, 0.7, 0.3, 0.0, 0.0, 0.0),
        section(1, DIAMETER, 0.7, 0.3, 0.0, 0.0, 0.0)};
    TwoFluidConservationEquations.TransactionalEvaluation trialEvaluation = equations.evaluateTransactional(trial,
        LENGTH);
    assertNotEquals(acceptedForces[0][0], trialEvaluation.getStratifiedHydrostaticPressureForces()[0][0]);
    assertMatrixEquals(acceptedForces, equations.getLastStratifiedHydrostaticPressureForces(), 0.0);

    TwoFluidSection invalid = section(0, DIAMETER, 0.4, 0.3, 0.3, 0.0, 0.0);
    assertThrows(IllegalStateException.class,
        () -> equations.calcRHSTransactional(new TwoFluidSection[] {invalid}, LENGTH));
    assertMatrixEquals(acceptedForces, equations.getLastStratifiedHydrostaticPressureForces(), 0.0);
  }

  /** Default-off execution is bitwise stable and the production API exposes only explicit opt-in state. */
  @Test
  void defaultOffIsBitwiseStableAndPubliclyControllable() {
    TwoFluidSection[] cells = {section(0, DIAMETER, 0.4, 0.6, 0.0, 1.0, 0.2),
        section(1, DIAMETER, 0.3, 0.7, 0.0, 0.5, 0.1)};
    TwoFluidConservationEquations equations = equations(false, false);
    double[][] baseline = equations.calcRHS(copy(cells), LENGTH);
    assertEquals(0, equations.getLastStratifiedHydrostaticPressureForces().length);
    equations.setStratifiedHydrostaticPressureMomentsEnabled(true);
    equations.calcRHS(copy(cells), LENGTH);
    equations.setStratifiedHydrostaticPressureMomentsEnabled(false);
    assertMatrixEquals(baseline, equations.calcRHS(copy(cells), LENGTH), 0.0);
    assertEquals(0, equations.getLastStratifiedHydrostaticPressureForces().length);

    TwoFluidPipe pipe = new TwoFluidPipe("stratified pressure API");
    assertFalse(pipe.isStratifiedHydrostaticPressureMomentsEnabled());
    pipe.setStratifiedHydrostaticPressureMomentsEnabled(true);
    assertTrue(pipe.isStratifiedHydrostaticPressureMomentsEnabled());
    TwoFluidPipe restored = SerializationUtils.clone(pipe);
    assertTrue(restored.isStratifiedHydrostaticPressureMomentsEnabled());
    pipe.setStratifiedHydrostaticPressureMomentsEnabled(false);
    assertFalse(pipe.isStratifiedHydrostaticPressureMomentsEnabled());
    assertEquals(0, pipe.getLastStratifiedHydrostaticPressureForces().length);
  }

  private static TwoFluidConservationEquations equations(boolean pressureMoments, boolean waterOilSlip) {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(true);
    equations.setIncludeMassTransfer(false);
    equations.setConsistentPhasePressureEnabled(true);
    equations.setEnableWaterOilSlip(waterOilSlip);
    equations.setClosedBoundaries(true, true);
    equations.setStratifiedHydrostaticPressureMomentsEnabled(pressureMoments);
    return equations;
  }

  private static TwoFluidSection section(int index, double diameter, double gasHoldup, double oilHoldup,
      double waterHoldup, double gasVelocity, double liquidVelocity) {
    TwoFluidSection section = new TwoFluidSection((index + 0.5) * LENGTH, LENGTH, diameter, 0.0);
    section.setPressure(1.5e7);
    section.setGasHoldup(gasHoldup);
    section.setWaterCut(waterHoldup == 0.0 ? 0.0 : waterHoldup / (oilHoldup + waterHoldup));
    section.setLiquidHoldup(oilHoldup + waterHoldup);
    section.setOilHoldup(oilHoldup);
    section.setWaterHoldup(waterHoldup);
    section.setGasDensity(GAS_DENSITY);
    section.setOilDensity(OIL_DENSITY);
    section.setWaterDensity(WATER_DENSITY);
    section.setLiquidDensity(waterHoldup > 0.0 && oilHoldup == 0.0 ? WATER_DENSITY : OIL_DENSITY);
    section.setGasSoundSpeed(340.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setGasEnthalpy(100000.0);
    section.setLiquidEnthalpy(50000.0);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(liquidVelocity);
    section.setOilVelocity(liquidVelocity);
    section.setWaterVelocity(liquidVelocity);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }

  private static TwoFluidSection[] copy(TwoFluidSection[] cells) {
    TwoFluidSection[] copy = new TwoFluidSection[cells.length];
    for (int cell = 0; cell < cells.length; cell++) {
      copy[cell] = cells[cell].clone();
    }
    return copy;
  }

  private static void assertMatrixEquals(double[][] expected, double[][] actual, double tolerance) {
    assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], tolerance);
    }
  }
}
