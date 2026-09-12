package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Same maintained riser fixtures with the inclined film constraint enabled only after the steady initialization. */
class TwoFluidInclinedFilmTengesdalPreparationTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidInclinedFilmTengesdalPreparationTest.class);

  @ParameterizedTest
  @CsvSource({ "16,0.1,false", "16,0.05,false", "24,0.05,false", "16,0.1,true", "16,0.05,true", "24,0.05,true" })
  void shortFilmConstrainedRiserPreparationRemainsConservative(int cells, double maximumStep, boolean faceTerrain) {
    verify(cells, maximumStep, faceTerrain, 0.1);
  }

  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.film-bridging.qualification", matches = "true")
  @ParameterizedTest
  @CsvSource({ "16,0.1,false", "16,0.05,false", "24,0.05,false", "16,0.1,true", "16,0.05,true", "24,0.05,true" })
  void fiveSecondFilmConstrainedRiserQualificationMustCompleteTheWholeInterval(int cells, double maximumStep,
      boolean faceTerrain) {
    verify(cells, maximumStep, faceTerrain, 5.0);
  }

  private static void verify(int count, double maximumStep, boolean faceTerrain, double duration) {
    TwoFluidPipe pipe = faceTerrain ? TwoFluidCellFaceTengesdalPreparationTest.createPipe(count)
        : TwoFluidUnsplitTengesdalPreparationTest.createPipe(count);
    // Preserve the historical accepted steady state. Only the copied transient closure gains the film constraint.
    pipe.setUseInclinedFilmBridgingCriterion(true);
    pipe.setUnsplitPressureInterpolationEnabled(true);
    TwoFluidSection[] initial = pipe.getSectionSnapshots();
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    PreparedInterval interval = pipe.prepareUnsplitTransient(duration, maximumStep, solver,
        pipe.createUnsplitDensityModel());
    assertEquals(duration, interval.getEndTimeSeconds(), 0.0);
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
    assertEquals(0.0, pipe.getTime(), 0.0);
    assertTrue(interval.getMaximumScaledResidual() <= 1.0e-8);
    double[] initialMass = interval.getSubsteps().get(0).getInitialMassKg();
    assertTrue(initialMass[0] > 0.0 && initialMass[1] > 0.0);
    assertEquals(0.0, initialMass[2], 0.0);
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(0.0, interval.getMassResidualKg()[phase] / Math.max(1.0, initialMass[phase]), 1.0e-8);
    }
    for (PreparedStep step : interval.getSubsteps()) {
      assertEquals(step.getTimeStepSeconds(), step.getPressureInterpolationTimeScale(), 0.0);
      assertTrue(step.getMaximumScaledResidual() <= 1.0e-8);
      for (TwoFluidSection cell : step.getEndpointSections()) {
        assertTrue(Double.isFinite(cell.getPressure()) && cell.getPressure() > 0.0);
        assertEquals(1.0, cell.getGasHoldup() + cell.getOilHoldup() + cell.getWaterHoldup(), 1.0e-8);
        assertEquals(0.0, cell.getWaterMassPerLength(), 0.0);
        assertTrue(Double.isFinite(cell.getGasVelocity()) && Double.isFinite(cell.getOilVelocity()));
      }
    }
    TwoFluidSection[] unchanged = pipe.getSectionSnapshots();
    for (int cell = 0; cell < count; cell++) {
      assertArrayEquals(initial[cell].getStateVector(), unchanged[cell].getStateVector(), 0.0);
      assertEquals(initial[cell].getPressure(), unchanged[cell].getPressure(), 0.0);
    }
    logger.info(
        "Film-constrained Tengesdal preparation: faceTerrain={}, cells={}, maximumStep={}, duration={}, "
            + "substeps={}, rejects={}, residual={}",
        faceTerrain, count, maximumStep, duration, interval.getSubsteps().size(), interval.getRejectedAttempts(),
        interval.getMaximumScaledResidual());
  }
}
