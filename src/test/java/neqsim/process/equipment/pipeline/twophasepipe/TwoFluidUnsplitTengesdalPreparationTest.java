package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Actual frozen-phase EOS handoff for the public Tengesdal 2002 large-facility test 3 fixture.
 *
 * <p>
 * The short handoff checks conservative preparation and isolation. The explicit opt-in five-second gate retains known
 * negative evidence and must complete before the longer slug characterization sequence is attempted. Neither test
 * qualifies experimental amplitude or period; no prepared state is published to the pipe.
 * </p>
 */
class TwoFluidUnsplitTengesdalPreparationTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidUnsplitTengesdalPreparationTest.class);

  @ParameterizedTest
  @CsvSource({ "16,0.1", "16,0.05", "24,0.05" })
  void shortRiserHandoffPreparesConservativelyWithoutPublishing(int cells, double maximumStep) {
    verifyPreparation(cells, maximumStep, 0.1);
  }

  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.qualification", matches = "true")
  @ParameterizedTest
  @CsvSource({ "16,0.1", "16,0.05", "24,0.05" })
  void fiveSecondRiserQualificationMustCompleteTheWholeInterval(int cells, double maximumStep) {
    verifyPreparation(cells, maximumStep, 5.0);
  }

  private static void verifyPreparation(int count, double maximumStep, double duration) {
    TwoFluidPipe pipe = createPipe(count);
    TwoFluidSection[] initial = pipe.getSectionSnapshots();
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    PreparedInterval interval = pipe.prepareUnsplitTransient(duration, maximumStep, solver,
        pipe.createUnsplitDensityModel());
    assertEquals(duration, interval.getEndTimeSeconds(), 0.0);
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
    assertEquals(0.0, pipe.getTime(), 0.0);
    assertTrue(interval.getMaximumScaledResidual() <= 1.0e-8);
    double maximumVelocity = 0.0;
    double maximumPressureDeparture = 0.0;
    double minimumStep = maximumStep;
    double[] initialMass = interval.getSubsteps().get(0).getInitialMassKg();
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(0.0, interval.getMassResidualKg()[phase] / Math.max(1.0, initialMass[phase]), 1.0e-8);
    }
    assertTrue(initialMass[0] > 0.0 && initialMass[1] > 0.0);
    assertEquals(0.0, initialMass[2], 0.0);
    for (PreparedStep step : interval.getSubsteps()) {
      minimumStep = Math.min(minimumStep, step.getTimeStepSeconds());
      assertTrue(step.getMaximumScaledResidual() <= 1.0e-8);
      TwoFluidSection[] candidate = step.getEndpointSections();
      for (int cell = 0; cell < count; cell++) {
        maximumVelocity = Math.max(maximumVelocity,
            Math.max(Math.abs(candidate[cell].getGasVelocity()), Math.abs(candidate[cell].getOilVelocity())));
        maximumPressureDeparture = Math.max(maximumPressureDeparture,
            Math.abs(candidate[cell].getPressure() - initial[cell].getPressure()));
        assertTrue(Double.isFinite(candidate[cell].getPressure()) && candidate[cell].getPressure() > 0.0);
        assertEquals(1.0,
            candidate[cell].getGasHoldup() + candidate[cell].getOilHoldup() + candidate[cell].getWaterHoldup(), 1.0e-8);
      }
    }
    assertTrue(Double.isFinite(maximumVelocity));
    TwoFluidSection[] unchanged = pipe.getSectionSnapshots();
    for (int cell = 0; cell < count; cell++) {
      assertArrayEquals(initial[cell].getStateVector(), unchanged[cell].getStateVector(), 0.0);
      assertEquals(initial[cell].getPressure(), unchanged[cell].getPressure(), 0.0);
    }
    logger.info(
        "Unsplit Tengesdal handoff: cells={}, maximumStep={}, duration={}, substeps={}, rejects={}, "
            + "evaluations={}, minimumStep={}, maximumSpeed={}, maximumPressureDeparture={}, scaledResidual={}",
        count, maximumStep, duration, interval.getSubsteps().size(), interval.getRejectedAttempts(),
        interval.getModelEvaluations(), minimumStep, maximumVelocity, maximumPressureDeparture,
        interval.getMaximumScaledResidual());
  }

  private static TwoFluidPipe createPipe(int count) {
    double diameter = 0.0762;
    double area = Math.PI * diameter * diameter / 4.0;
    double oilFlow = 0.50 * area * 856.0;
    double gasFlow = area * 1.204;
    SystemInterface fluid = new SystemSrkEos(298.15, 2.3);
    fluid.addComponent("nitrogen", gasFlow / 0.0280134);
    fluid.addTBPfraction("Crystex", oilFlow / 0.220, 0.220, 0.856);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("Tengesdal 2002 test 3 unsplit feed", fluid);
    inlet.setFlowRate(gasFlow + oilFlow, "kg/sec");
    inlet.run();
    double length = 19.81 + 14.94;
    double angle = Math.toRadians(-3.0);
    double[] elevation = new double[count];
    for (int cell = 0; cell < count; cell++) {
      double position = length * cell / (count - 1.0);
      elevation[cell] = position <= 19.81 ? position * Math.sin(angle) : 19.81 * Math.sin(angle) + position - 19.81;
    }
    TwoFluidPipe pipe = new TwoFluidPipe("Unsplit Tengesdal handoff", inlet);
    pipe.setLength(length);
    pipe.setDiameter(diameter);
    pipe.setRoughness(1.5e-6);
    pipe.setNumberOfSections(count);
    pipe.setElevationProfile(elevation);
    pipe.setOutletPressure(1.01325, "bara");
    pipe.setInletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setIncludeMassTransfer(false);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setAllowOutletPhaseBackflow(true);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }
}
