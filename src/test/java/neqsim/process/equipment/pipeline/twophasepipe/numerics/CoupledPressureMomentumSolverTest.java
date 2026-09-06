package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoupledPressureMomentumSolverTest {
  @Test
  void feasibleIncomingCorrectionSuppliesThroughFlowInEitherDirection() {
    for (boolean reverse : new boolean[] { false, true }) {
      double[][] state = new double[3][7];
      double[][] density = { filled(3, 1.0), filled(3, 800.0), filled(3, 1000.0) };
      for (int cell = 0; cell < 3; cell++) {
        state[cell][0] = 1.0;
      }
      int donor = reverse ? 2 : 0;
      int receiver = reverse ? 0 : 2;
      state[donor][0] = 100.0;
      density[0][donor] = 100.0;
      double[] correction = reverse ? new double[] { 0.0, 2.0, 4.0 } : new double[] { 4.0, 2.0, 0.0 };
      double[] initialMass = totalPhaseMass(state);
      CoupledPressureMomentumSolver.applyConservativeMassFluxCorrection(state, 1.0, correction,
          phaseAreas(state, density), filled(3, 1.0), filled(3, 1.0), density, false);

      assertEquals(98.0, state[donor][0], 1e-12);
      assertEquals(1.0, state[1][0], 1e-12,
          "A feasible incoming 2 kg transfer must supply the simultaneous 2 kg outgoing transfer");
      assertEquals(3.0, state[receiver][0], 1e-12);
      assertNonnegativePhaseMasses(state);
      assertArrayEquals(initialMass, totalPhaseMass(state), 1e-12);
    }
  }

  @Test
  void reducedIncomingCorrectionPropagatesWithoutOverdrawingDownstreamCells() {
    for (boolean reverse : new boolean[] { false, true }) {
      double[][] state = new double[6][7];
      double[][] density = { filled(6, 0.1), filled(6, 800.0), filled(6, 1000.0) };
      double[] correction = new double[6];
      double[] lengths = { 1.0, 2.0, 3.0, 4.0, 5.0, 6.0 };
      for (int cell = 0; cell < 6; cell++) {
        state[cell][0] = 0.1;
        correction[cell] = reverse ? 100.0 * cell : 100.0 * (5 - cell);
      }
      double[] initialMass = weightedPhaseMass(state, lengths);
      CoupledPressureMomentumSolver.applyConservativeMassFluxCorrection(state, 1.0, correction,
          phaseAreas(state, density), filled(6, 1.0), lengths, density, false);

      assertNonnegativePhaseMasses(state);
      assertArrayEquals(initialMass, weightedPhaseMass(state, lengths), 1e-12,
          "Only feasible incoming mass may fund downstream corrections");
      int receiver = reverse ? 0 : 5;
      assertEquals(initialMass[0], state[receiver][0] * lengths[receiver], 1e-12);
    }
  }

  @Test
  void volumeClosedPredictorStillEnforcesChangedOutletPressure() {
    for (boolean checkerboard : new boolean[] { false, true }) {
      for (CoupledPressureMomentumSolver.GasDensityModel model : CoupledPressureMomentumSolver.GasDensityModel
          .values()) {
        CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
        solver.setCheckerboardCorrectionEnabled(checkerboard);
        solver.setGasDensityModel(model);
        double[][] state = uniformState();
        double[] lengths = filled(4, 10.0);
        double[] initialMass = weightedPhaseMass(state, lengths);
        CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.01, filled(4, 5e6), filled(4, 1.0),
            lengths, filled(4, 10.0), filled(4, 800.0), filled(4, 1000.0), filled(4, 300.0), filled(4, 1200.0),
            filled(4, 1200.0), 4.9e6, true);

        assertTrue(result.isConverged(),
            "Changed outlet must converge for " + model + ", checkerboard=" + checkerboard);
        assertTrue(result.getIterations() > 0, "A volume-closed predictor still needs the boundary correction");
        assertEquals(4.9e6, result.getPressure()[3], 4.9e6 * solver.getRelativeVolumeTolerance());
        double[] finalMass = weightedPhaseMass(result.getState(), lengths);
        double[] outletMass = result.getOutletBoundaryMassCorrectionKg();
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(initialMass[phase], finalMass[phase] + outletMass[phase], 1e-9,
              "Changed pressure must retain the exact phase transfer ledger");
        }
      }
    }
  }

  @Test
  void unresolvedOutletPressureCannotBeReportedAsConverged() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    solver.setMaximumIterations(1);
    CoupledPressureMomentumSolver.Result result = solver.correct(uniformState(), 0.01, filled(4, 5e6), filled(4, 1.0),
        filled(4, 10.0), filled(4, 10.0), filled(4, 800.0), filled(4, 1000.0), filled(4, 300.0), filled(4, 1200.0),
        filled(4, 1200.0), 4.9e6, true);
    assertFalse(result.isConverged(), "Volume closure alone cannot certify an unmet Dirichlet boundary");
  }

  @Test
  void uniformStateIsInvariant() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    double[][] state = uniformState();
    double[][] original = copy(state);
    double[] pressure = filled(4, 5.0e6);
    double[] area = filled(4, 1.0);
    double[] length = filled(4, 10.0);
    double[] gasDensity = filled(4, 10.0);
    double[] oilDensity = filled(4, 800.0);
    double[] waterDensity = filled(4, 1000.0);
    double[] gasSoundSpeed = filled(4, 300.0);
    double[] liquidSoundSpeed = filled(4, 1200.0);

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, pressure, area, length, gasDensity,
        oilDensity, waterDensity, gasSoundSpeed, liquidSoundSpeed, liquidSoundSpeed, 5.0e6, true);

    assertTrue(result.isConverged());
    assertEquals(0, result.getIterations());
    assertEquals(1.0, result.getMinimumMassFluxCorrectionScale(), 0.0,
        "An invariant state has no limited correction to report");
    for (int cell = 0; cell < state.length; cell++) {
      assertArrayEquals(original[cell], result.getState()[cell], 0.0);
    }
    assertArrayEquals(pressure, result.getPressure(), 0.0);
  }

  @Test
  void correctionReducesVolumeResidualAndConservesEachPhase() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    solver.setMaximumIterations(20);
    double[][] state = uniformState();
    state[1][0] -= 0.5;
    state[2][0] += 0.5;
    double[] initialPhaseMass = totalPhaseMass(state);
    double[] pressure = filled(4, 5.0e6);
    double[] area = filled(4, 1.0);
    double[] length = filled(4, 10.0);
    double[] gasDensity = filled(4, 10.0);
    double[] oilDensity = filled(4, 800.0);
    double[] waterDensity = filled(4, 1000.0);
    double[] gasSoundSpeed = filled(4, 300.0);
    double[] liquidSoundSpeed = filled(4, 1200.0);
    double initialResidual = 0.05;

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, pressure, area, length, gasDensity,
        oilDensity, waterDensity, gasSoundSpeed, liquidSoundSpeed, liquidSoundSpeed, 5.0e6, false);

    assertTrue(result.isConverged());
    assertTrue(result.getMaximumRelativeVolumeResidual() < initialResidual * 1.0e-4);
    assertArrayEquals(initialPhaseMass, totalPhaseMass(result.getState()), 1.0e-10);
    assertTrue(Math.abs(result.getState()[1][3]) > 0.0);
    assertTrue(Math.abs(result.getState()[2][3]) > 0.0);
  }

  @Test
  void fixedPressureBoundaryCorrectionIsReportedAsOutletMass() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    double[][] state = uniformState();
    state[state.length - 1][0] += 0.5;
    double[] initialPhaseMass = totalPhaseMass(state);
    double[] pressure = filled(4, 5.0e6);
    double[] area = filled(4, 1.0);
    double[] length = filled(4, 10.0);
    double[] gasDensity = filled(4, 10.0);
    double[] oilDensity = filled(4, 800.0);
    double[] waterDensity = filled(4, 1000.0);
    double[] gasSoundSpeed = filled(4, 300.0);
    double[] liquidSoundSpeed = filled(4, 1200.0);

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, pressure, area, length, gasDensity,
        oilDensity, waterDensity, gasSoundSpeed, liquidSoundSpeed, liquidSoundSpeed, 5.0e6, true);

    assertTrue(result.isConverged());
    assertEquals(5.0e6, result.getPressure()[3], 0.0);
    double[] finalPhaseMass = totalPhaseMass(result.getState());
    double[] outletCorrection = result.getOutletBoundaryMassCorrectionKg();
    for (int phase = 0; phase < initialPhaseMass.length; phase++) {
      assertEquals(10.0 * (finalPhaseMass[phase] - initialPhaseMass[phase]), -outletCorrection[phase], 1.0e-10);
    }
    assertTrue(outletCorrection[0] + outletCorrection[1] + outletCorrection[2] > 0.0);
  }

  /** A centered interface must not extract gas from a gas-free donor in either correction direction. */
  @Test
  void pressureCorrectionCannotExportAnAbsentDonorPhase() {
    for (int donor = 0; donor < 2; donor++) {
      double[][] state = { { 4.0, 480.0, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 4.0, 480.0, 0.0, 0.0, 0.0, 0.0, 1e6 } };
      state[donor][0] = 0.0;
      state[donor][1] = 800.08;
      double[] length = { 2.0, 3.0 };
      double[] initialMass = weightedPhaseMass(state, length);
      double[] correction = { 0.0, 0.0 };
      correction[donor] = 1000.0;
      double[][] density = { filled(2, 10.0), filled(2, 800.0), filled(2, 1000.0) };
      double[] outlet = CoupledPressureMomentumSolver.applyConservativeMassFluxCorrection(state, 0.01, correction,
          phaseAreas(state, density), filled(2, 1.0), length, density, false);
      assertEquals(0.0, state[donor][0], 0.0);
      assertArrayEquals(initialMass, weightedPhaseMass(state, length), 1e-12);
      assertArrayEquals(new double[3], outlet, 0.0);
      assertNonnegativePhaseMasses(state);
    }
  }

  /** A donor with two outgoing faces has one inventory budget, also on nonuniform cells. */
  @Test
  void simultaneousOutgoingCorrectionsCannotOverdrawPhaseInventory() {
    for (double sign : new double[] { -1.0, 1.0 }) {
      double[][] state = { { 1.0, 720.0, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 1e-6, 799.99992, 0.0, 0.0, 0.0, 0.0, 1e6 },
          { 1.0, 720.0, 0.0, 0.0, 0.0, 0.0, 1e6 } };
      double[] length = { 1.0, 2.0, 3.0 };
      double[] initialMass = weightedPhaseMass(state, length);
      double[][] density = { filled(3, 10.0), filled(3, 800.0), filled(3, 1000.0) };
      CoupledPressureMomentumSolver.applyConservativeMassFluxCorrection(state, 0.1,
          new double[] { 0.0, sign * 1e5, 0.0 }, phaseAreas(state, density), filled(3, 1.0), length, density, false);
      assertNonnegativePhaseMasses(state);
      assertArrayEquals(initialMass, weightedPhaseMass(state, length), 1e-10);
      for (double[] cell : state) {
        assertEquals(0.0, cell[2], 0.0);
      }
    }
  }

  /** The nonlinear solve can close volume while preserving phase mass at a gas-disappearance interface. */
  @Test
  void gasFreeOverfilledCellConvergesWithoutCrossPhaseMassRepair() {
    for (int donor = 0; donor < 2; donor++) {
      for (boolean outletFixed : new boolean[] { false, true }) {
        CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
        double[][] state = { { 4.0, 480.0, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 4.0, 480.0, 0.0, 0.0, 0.0, 0.0, 1e6 } };
        state[donor][0] = 0.0;
        state[donor][1] = 800.08;
        double[] length = filled(2, 1.0);
        double[] initialMass = weightedPhaseMass(state, length);
        CoupledPressureMomentumSolver.Result result = solver.correct(state, 1e-5, filled(2, 5e6), filled(2, 1.0),
            length, filled(2, 10.0), filled(2, 800.0), filled(2, 1000.0), filled(2, 300.0), filled(2, 1200.0),
            filled(2, 1200.0), 5e6, outletFixed);
        assertTrue(result.isConverged(), "Volume closure must converge at donor=" + donor + ", fixed=" + outletFixed);
        assertTrue(result.getMaximumRelativeVolumeResidual() <= solver.getRelativeVolumeTolerance());
        assertNonnegativePhaseMasses(result.getState());
        double[] finalMass = weightedPhaseMass(result.getState(), length);
        double[] outlet = result.getOutletBoundaryMassCorrectionKg();
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(initialMass[phase], finalMass[phase] + outlet[phase], 1e-10);
        }
      }
    }
  }

  /**
   * A dry donor has no gas mobility, while an empty receiver can accept upstream gas. The density contrast also
   * requires the pressure Jacobian to use each cell's own density.
   */
  @Test
  void donorMobilityClosesVolumeAcrossGasDisappearanceAndDensityContrast() {
    for (int gasFreeCell = 0; gasFreeCell < 2; gasFreeCell++) {
      for (double volumeError : new double[] { -0.03, 0.03 }) {
        for (double timeStep : new double[] { 0.001, 0.01 }) {
          CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
          double[][] state = { { 2.1, 256.8, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 2.1, 256.8, 0.0, 0.0, 0.0, 0.0, 1e6 } };
          state[gasFreeCell][0] = 0.0;
          state[gasFreeCell][1] = 856.0 * (1.0 + volumeError);
          double[] gasDensity = filled(2, 3.0);
          gasDensity[gasFreeCell] = 0.04;
          double[] lengths = filled(2, 1.0);
          double[] initialMass = weightedPhaseMass(state, lengths);

          CoupledPressureMomentumSolver.Result result = solver.correct(state, timeStep, filled(2, 5e6), filled(2, 1.0),
              lengths, gasDensity, filled(2, 856.0), filled(2, 1000.0), filled(2, 300.0), filled(2, 1200.0),
              filled(2, 1200.0), 5e6, false);

          assertTrue(result.isConverged(), "Volume closure failed for gas-free cell=" + gasFreeCell + ", volume error="
              + volumeError + ", dt=" + timeStep + ", residual=" + result.getMaximumRelativeVolumeResidual());
          assertTrue(result.getMaximumRelativeVolumeResidual() <= solver.getRelativeVolumeTolerance());
          assertTrue(result.getIterations() <= solver.getMaximumIterations());
          assertNonnegativePhaseMasses(result.getState());
          assertArrayEquals(initialMass, weightedPhaseMass(result.getState(), lengths), 1e-10);
          assertArrayEquals(new double[3], result.getOutletBoundaryMassCorrectionKg(), 0.0);
          if (volumeError < 0.0) {
            assertTrue(result.getState()[gasFreeCell][0] > 0.0,
                "Gas must be able to enter a gas-free receiver from a gas-containing donor");
          } else {
            assertEquals(0.0, result.getState()[gasFreeCell][0], 0.0,
                "A gas-free donor must not export gas to close its volume residual");
          }
        }
      }
    }
  }

  /** Independent cell pressure caps must not reverse a Newton direction and cycle its active donors. */
  @Test
  void boundedNewtonDirectionConvergesWithoutCyclingFaceDonors() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    double[][] state = { { 0.0053312, 533.12, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 0.0, 804.8, 0.0, 0.0, 0.0, 0.0, 1e6 },
        { 67.599, 218.4, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 0.50688, 430.08, 0.0, 0.0, 0.0, 0.0, 1e6 } };
    double[] lengths = filled(4, 1.0);
    double[] initialMass = weightedPhaseMass(state, lengths);

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.0018,
        new double[] { 140000.0, 7600000.0, 136000.0, 1340000.0 }, filled(4, 1.0), lengths,
        new double[] { 0.017, 10.0, 87.0, 1.2 }, filled(4, 800.0), filled(4, 1000.0), filled(4, 300.0),
        filled(4, 1200.0), filled(4, 1200.0), 1e5, false);

    assertTrue(result.isConverged(), "Bounded Newton solve stalled at residual "
        + result.getMaximumRelativeVolumeResidual() + " after " + result.getIterations() + " iterations");
    assertTrue(result.getMaximumRelativeVolumeResidual() <= solver.getRelativeVolumeTolerance());
    assertTrue(result.getIterations() <= solver.getMaximumIterations());
    assertTrue(result.isPressureCorrectionLimited());
    assertNonnegativePhaseMasses(result.getState());
    assertArrayEquals(initialMass, weightedPhaseMass(result.getState(), lengths), 1e-10);
    assertArrayEquals(new double[3], result.getOutletBoundaryMassCorrectionKg(), 0.0);
  }

  /** A positive subatmospheric acoustic state must not be constrained by an atmospheric interior-pressure floor. */
  @Test
  void subatmosphericPressureClosesVolumeWithoutAtmosphericFloor() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    double[][] state = { { 0.99, 0.0, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 0.99, 0.0, 0.0, 0.0, 0.0, 0.0, 1e6 } };
    double[] lengths = filled(2, 1.0);

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, filled(2, 80000.0), filled(2, 1.0),
        lengths, filled(2, 1.0), filled(2, 800.0), filled(2, 1000.0), filled(2, 300.0), filled(2, 1200.0),
        filled(2, 1200.0), 80000.0, false);

    assertTrue(result.isConverged(),
        "A positive subatmospheric state must close its volume: residual=" + result.getMaximumRelativeVolumeResidual());
    assertArrayEquals(weightedPhaseMass(state, lengths), weightedPhaseMass(result.getState(), lengths), 1e-12);
    for (int cell = 0; cell < state.length; cell++) {
      // The uniform sealed column has zero flux. Its exact affine acoustic closure
      // is rho = mass / area and p = p_initial + c^2 * (rho - rho_initial).
      assertEquals(0.99, result.getGasDensity()[cell], 1e-7);
      assertEquals(79100.0, result.getPressure()[cell], 0.01);
      assertEquals(1.0 + (result.getPressure()[cell] - 80000.0) / 90000.0, result.getGasDensity()[cell], 1e-12);
      assertTrue(result.getPressure()[cell] > 0.0);
    }
    assertArrayEquals(new double[3], result.getOutletBoundaryMassCorrectionKg(), 0.0);
  }

  /** A pressure floor cannot close a sealed underfilled volume by applying an unreported density decrease. */
  @Test
  void pressureFloorUsesActualPressureChangeAndReportsInfeasibleVolume() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    solver.setMinimumPressure(1e5);
    double[][] state = { { 0.99, 0.0, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 0.99, 0.0, 0.0, 0.0, 0.0, 0.0, 1e6 } };
    double[][] original = copy(state);

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, filled(2, 1e5), filled(2, 1.0),
        filled(2, 1.0), filled(2, 1.0), filled(2, 800.0), filled(2, 1000.0), filled(2, 300.0), filled(2, 1200.0),
        filled(2, 1200.0), 1e5, false);

    for (int cell = 0; cell < state.length; cell++) {
      assertEquals(1e5, result.getPressure()[cell], 0.0);
      double actualPressureChange = result.getPressure()[cell] - 1e5;
      assertEquals(1.0 + actualPressureChange / (300.0 * 300.0), result.getGasDensity()[cell], 1e-12,
          "Density must respond to the actual returned pressure change");
      assertArrayEquals(original[cell], result.getState()[cell], 0.0,
          "A zero pressure increment must not change mass or momentum");
    }
    assertTrue(!result.isConverged(), "The sealed volume cannot close under the declared pressure floor");
    assertTrue(result.isPressureCorrectionLimited());
    assertEquals(0.01, result.getMaximumRelativeVolumeResidual(), 1e-12);
    assertArrayEquals(new double[3], result.getOutletBoundaryMassCorrectionKg(), 0.0);
  }

  /** The pressure lower bound is an explicit positive numerical setting, separate from an outlet boundary. */
  @Test
  void minimumPressureConfigurationRequiresPositiveFiniteValues() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    assertEquals(1.0, solver.getMinimumPressure(), 0.0);
    solver.setMinimumPressure(90000.0);
    assertEquals(90000.0, solver.getMinimumPressure(), 0.0);
    for (double invalid : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
      assertThrows(IllegalArgumentException.class, () -> solver.setMinimumPressure(invalid));
    }
  }

  /** A large volume deficit must not trigger an independent density repair in the acoustic EOS update. */
  @Test
  void densityPositivityDampingPreservesTheActualAcousticPressureResponse() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    double[][] state = { { 0.001, 0.0, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 0.001, 0.0, 0.0, 0.0, 0.0, 0.0, 1e6 } };
    double[] lengths = filled(2, 1.0);

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, filled(2, 5e6), filled(2, 1.0), lengths,
        filled(2, 0.01), filled(2, 800.0), filled(2, 1000.0), filled(2, 300.0), filled(2, 1200.0), filled(2, 1200.0),
        5e6, false);

    assertTrue(result.isConverged(),
        "Density-guarded correction failed at residual " + result.getMaximumRelativeVolumeResidual());
    assertTrue(result.isPressureCorrectionLimited());
    assertNonnegativePhaseMasses(result.getState());
    assertArrayEquals(weightedPhaseMass(state, lengths), weightedPhaseMass(result.getState(), lengths), 1e-12);
    for (int cell = 0; cell < state.length; cell++) {
      double actualPressureChange = result.getPressure()[cell] - 5e6;
      assertEquals(0.01 + actualPressureChange / (300.0 * 300.0), result.getGasDensity()[cell], 1e-12);
      assertEquals(0.001, result.getGasDensity()[cell], 1e-9);
      assertTrue(result.getGasDensity()[cell] > 0.0);
    }
  }

  /** An absent gas phase's density placeholder must not restrict a valid liquid-only pressure correction. */
  @Test
  void absentPhaseDensityDoesNotConstrainSinglePhaseLiquidCorrection() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    double[][] state = { { 0.0, 792.0, 0.0, 0.0, 0.0, 0.0, 1e6 }, { 0.0, 792.0, 0.0, 0.0, 0.0, 0.0, 1e6 } };
    double[] lengths = filled(2, 1.0);

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, filled(2, 5e7), filled(2, 1.0), lengths,
        filled(2, 1e-6), filled(2, 800.0), filled(2, 1000.0), filled(2, 300.0), filled(2, 1200.0), filled(2, 1200.0),
        5e7, false);

    assertTrue(result.isConverged(), "An absent phase density must not block liquid volume closure");
    assertArrayEquals(weightedPhaseMass(state, lengths), weightedPhaseMass(result.getState(), lengths), 1e-12);
    for (int cell = 0; cell < state.length; cell++) {
      assertEquals(1e-6, result.getGasDensity()[cell], 0.0);
      assertEquals(0.0, result.getState()[cell][0], 0.0);
      double actualPressureChange = result.getPressure()[cell] - 5e7;
      assertEquals(800.0 + actualPressureChange / (1200.0 * 1200.0), result.getOilDensity()[cell], 1e-10);
      assertTrue(actualPressureChange < -1e6);
    }
  }

  /** A sealed gas column has an independent finite-amplitude pressure-volume relation. */
  @Test
  void polytropicGasClosesFiniteVolumeDeficitWithExactPressureDensityRelation() {
    CoupledPressureMomentumSolver solver = polytropicSolver();
    double referencePressure = 2e5;
    double referenceDensity = 2.0;
    double exponent = 1.4;
    double soundSpeed = Math.sqrt(exponent * referencePressure / referenceDensity);
    double[][] state = sealedGasState(0.8 * referenceDensity);

    CoupledPressureMomentumSolver.Result result = correctSealedGas(solver, state, filled(2, referencePressure),
        filled(2, referenceDensity), filled(2, soundSpeed));

    assertTrue(result.isConverged(),
        "Finite-amplitude gas correction residual=" + result.getMaximumRelativeVolumeResidual());
    assertArrayEquals(totalPhaseMass(state), totalPhaseMass(result.getState()), 1e-12);
    assertArrayEquals(new double[3], result.getOutletBoundaryMassCorrectionKg(), 0.0);
    for (int cell = 0; cell < state.length; cell++) {
      double actualPressure = result.getPressure()[cell];
      double actualDensity = result.getGasDensity()[cell];
      assertEquals(referencePressure * Math.pow(0.8, exponent), actualPressure, 1e-4);
      assertEquals(1.6, actualDensity, 1e-9);
      assertEquals(referenceDensity * Math.pow(actualPressure / referencePressure, 1.0 / exponent), actualDensity,
          1e-12);
      double correctedSoundSpeed = result.getGasSoundSpeed()[cell];
      assertEquals(exponent, correctedSoundSpeed * correctedSoundSpeed * actualDensity / actualPressure, 1e-12);
      assertEquals(800.0, result.getOilDensity()[cell], 0.0);
      assertEquals(1000.0, result.getWaterDensity()[cell], 0.0);
    }
  }

  /** The finite-amplitude closure must retain the supplied local sound speed in its infinitesimal limit. */
  @Test
  void polytropicGasRecoversTheAcousticSmallPerturbationLimit() {
    CoupledPressureMomentumSolver solver = polytropicSolver();
    double referencePressure = 2e5;
    double referenceDensity = 2.0;
    double exponent = 1.4;
    double soundSpeed = Math.sqrt(exponent * referencePressure / referenceDensity);
    double fractionRemoved = 1e-4;
    double[][] state = sealedGasState(referenceDensity * (1.0 - fractionRemoved));

    CoupledPressureMomentumSolver.Result result = correctSealedGas(solver, state, filled(2, referencePressure),
        filled(2, referenceDensity), filled(2, soundSpeed));

    assertTrue(result.isConverged());
    double expectedPressure = referencePressure * Math.pow(1.0 - fractionRemoved, exponent);
    double acousticPressure = referencePressure - soundSpeed * soundSpeed * referenceDensity * fractionRemoved;
    for (double actualPressure : result.getPressure()) {
      assertEquals(expectedPressure, actualPressure, 1e-4);
      assertEquals(acousticPressure, actualPressure, 0.001,
          "The second-order difference from the local acoustic response is below 0.001 Pa");
    }
    assertArrayEquals(totalPhaseMass(state), totalPhaseMass(result.getState()), 1e-12);
  }

  /** Carrying the returned sound speed makes successive corrections follow the same barotropic curve. */
  @Test
  void polytropicGasContinuationPreservesTheOriginalExponent() {
    CoupledPressureMomentumSolver solver = polytropicSolver();
    double referencePressure = 2e5;
    double referenceDensity = 2.0;
    double exponent = 1.4;
    double soundSpeed = Math.sqrt(exponent * referencePressure / referenceDensity);
    double[][] firstState = sealedGasState(0.9 * referenceDensity);
    CoupledPressureMomentumSolver.Result first = correctSealedGas(solver, firstState, filled(2, referencePressure),
        filled(2, referenceDensity), filled(2, soundSpeed));
    assertTrue(first.isConverged());
    double[][] secondState = first.getState();
    for (double[] cell : secondState) {
      cell[0] *= 0.9;
    }

    CoupledPressureMomentumSolver.Result second = correctSealedGas(solver, secondState, first.getPressure(),
        first.getGasDensity(), first.getGasSoundSpeed());

    assertTrue(second.isConverged());
    assertArrayEquals(totalPhaseMass(firstState), totalPhaseMass(first.getState()), 1e-12);
    assertArrayEquals(totalPhaseMass(secondState), totalPhaseMass(second.getState()), 1e-12);
    for (int cell = 0; cell < secondState.length; cell++) {
      assertEquals(referencePressure * Math.pow(0.81, exponent), second.getPressure()[cell], 1e-4);
      assertEquals(referenceDensity * 0.81, second.getGasDensity()[cell], 1e-9);
      double correctedSoundSpeed = second.getGasSoundSpeed()[cell];
      assertEquals(exponent,
          correctedSoundSpeed * correctedSoundSpeed * second.getGasDensity()[cell] / second.getPressure()[cell], 1e-12);
    }
  }

  /** An infeasible pressure bound must preserve the nonlinear EOS and report the remaining volume deficit. */
  @Test
  void polytropicGasPressureBoundReportsInfeasibleVolumeWithoutIndependentDensityRepair() {
    CoupledPressureMomentumSolver solver = polytropicSolver();
    solver.setMinimumPressure(190000.0);
    double referencePressure = 2e5;
    double referenceDensity = 2.0;
    double exponent = 1.4;
    double soundSpeed = Math.sqrt(exponent * referencePressure / referenceDensity);
    double[][] state = sealedGasState(0.8 * referenceDensity);

    CoupledPressureMomentumSolver.Result result = correctSealedGas(solver, state, filled(2, referencePressure),
        filled(2, referenceDensity), filled(2, soundSpeed));

    assertTrue(!result.isConverged());
    assertTrue(result.isPressureCorrectionLimited());
    assertTrue(result.getMaximumRelativeVolumeResidual() > solver.getRelativeVolumeTolerance());
    assertArrayEquals(totalPhaseMass(state), totalPhaseMass(result.getState()), 1e-12);
    for (int cell = 0; cell < state.length; cell++) {
      assertTrue(result.getPressure()[cell] >= solver.getMinimumPressure());
      assertEquals(solver.getMinimumPressure(), result.getPressure()[cell], 0.001);
      assertTrue(result.getGasDensity()[cell] > 0.0);
      assertEquals(referenceDensity * Math.pow(result.getPressure()[cell] / referencePressure, 1.0 / exponent),
          result.getGasDensity()[cell], 1e-12);
    }
  }

  /** Every cell mass change must be explained by the exact shared correction-face transfer ledger. */
  @Test
  void correctionFaceLedgerExplainsEveryCellPhaseMassChange() {
    for (boolean interpolationEnabled : new boolean[] { false, true }) {
      for (boolean outletFixed : new boolean[] { false, true }) {
        CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
        solver.setCheckerboardCorrectionEnabled(interpolationEnabled);
        double[][] state = uniformState();
        state[1][0] -= 0.2;
        state[2][0] += 0.2;
        double[] lengths = { 1.0, 2.0, 3.0, 4.0 };

        CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.001, filled(4, 5e6), filled(4, 1.0),
            lengths, filled(4, 10.0), filled(4, 800.0), filled(4, 1000.0), filled(4, 300.0), filled(4, 1200.0),
            filled(4, 1200.0), 5e6, outletFixed);

        assertTrue(result.isConverged(), "Correction ledger fixture failed with interpolation=" + interpolationEnabled
            + ", outletFixed=" + outletFixed + ", residual=" + result.getMaximumRelativeVolumeResidual());
        double[][] transfers = result.getPhaseMassCorrectionsKg();
        assertEquals(state.length + 1, transfers.length);
        for (int cell = 0; cell < state.length; cell++) {
          for (int phase = 0; phase < 3; phase++) {
            assertEquals((result.getState()[cell][phase] - state[cell][phase]) * lengths[cell],
                transfers[cell][phase] - transfers[cell + 1][phase], 1e-10);
          }
        }
        assertArrayEquals(new double[3], transfers[0], 0.0);
        assertArrayEquals(result.getOutletBoundaryMassCorrectionKg(), transfers[state.length], 0.0);
        transfers[1][0] = Double.NaN;
        assertTrue(Double.isFinite(result.getPhaseMassCorrectionsKg()[1][0]));
      }
    }
  }

  /** @return a polytropic solver with a tighter tolerance for analytical comparisons */
  private static CoupledPressureMomentumSolver polytropicSolver() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    assertEquals(CoupledPressureMomentumSolver.GasDensityModel.AFFINE, solver.getGasDensityModel());
    assertThrows(IllegalArgumentException.class, () -> solver.setGasDensityModel(null));
    solver.setGasDensityModel(CoupledPressureMomentumSolver.GasDensityModel.POLYTROPIC);
    solver.setRelativeVolumeTolerance(1e-10);
    return solver;
  }

  /**
   * @param mass gas mass per unit length in kg/m
   * @return two identical gas-only sealed cells
   */
  private static double[][] sealedGasState(double mass) {
    return new double[][] { { mass, 0.0, 0.0, 0.0, 0.0, 0.0, 1e6 }, { mass, 0.0, 0.0, 0.0, 0.0, 0.0, 1e6 } };
  }

  /**
   * Apply a pressure correction to a two-cell uniform sealed column with unit cell area and length.
   *
   * @param solver configured pressure solver
   * @param state conservative cell state
   * @param pressure initial cell pressure in Pa
   * @param density initial gas density in kg/m3
   * @param soundSpeed initial gas sound speed in m/s
   * @return correction result
   */
  private static CoupledPressureMomentumSolver.Result correctSealedGas(CoupledPressureMomentumSolver solver,
      double[][] state, double[] pressure, double[] density, double[] soundSpeed) {
    return solver.correct(state, 1e-5, pressure, filled(2, 1.0), filled(2, 1.0), density, filled(2, 800.0),
        filled(2, 1000.0), soundSpeed, filled(2, 1200.0), filled(2, 1200.0), pressure[0], false);
  }

  /**
   * Calculate occupied phase areas for a correction-stage fixture.
   *
   * @param state conservative cell state
   * @param density phase/cell densities
   * @return phase/cell occupied areas
   */
  private static double[][] phaseAreas(double[][] state, double[][] density) {
    double[][] result = new double[3][state.length];
    for (int phase = 0; phase < 3; phase++) {
      for (int cell = 0; cell < state.length; cell++) {
        result[phase][cell] = state[cell][phase] / density[phase][cell];
      }
    }
    return result;
  }

  /**
   * Integrate phase inventories over nonuniform cell lengths.
   *
   * @param state conservative cell state
   * @param lengths cell lengths in meters
   * @return gas, oil and water inventories in kg
   */
  private static double[] weightedPhaseMass(double[][] state, double[] lengths) {
    double[] result = new double[3];
    for (int cell = 0; cell < state.length; cell++) {
      for (int phase = 0; phase < 3; phase++) {
        result[phase] += state[cell][phase] * lengths[cell];
      }
    }
    return result;
  }

  /**
   * Assert that the correction alone maintains finite, nonnegative phase inventories.
   *
   * @param state corrected conservative cell state
   */
  private static void assertNonnegativePhaseMasses(double[][] state) {
    for (double[] cell : state) {
      for (int phase = 0; phase < 3; phase++) {
        assertTrue(Double.isFinite(cell[phase]));
        assertTrue(cell[phase] >= 0.0, "Correction made phase " + phase + " mass negative: " + cell[phase]);
      }
    }
  }

  private static double[][] uniformState() {
    double[][] state = new double[4][7];
    for (int cell = 0; cell < state.length; cell++) {
      state[cell][0] = 4.0;
      state[cell][1] = 480.0;
      state[cell][2] = 0.0;
      state[cell][3] = 4.0;
      state[cell][4] = 480.0;
      state[cell][5] = 0.0;
      state[cell][6] = 1.0e6;
    }
    return state;
  }

  private static double[] totalPhaseMass(double[][] state) {
    double[] result = new double[3];
    for (double[] cell : state) {
      for (int phase = 0; phase < result.length; phase++) {
        result[phase] += cell[phase];
      }
    }
    return result;
  }

  private static double[] filled(int size, double value) {
    double[] result = new double[size];
    for (int index = 0; index < size; index++) {
      result[index] = value;
    }
    return result;
  }

  private static double[][] copy(double[][] state) {
    double[][] result = new double[state.length][];
    for (int row = 0; row < state.length; row++) {
      result[row] = state[row].clone();
    }
    return result;
  }
}
