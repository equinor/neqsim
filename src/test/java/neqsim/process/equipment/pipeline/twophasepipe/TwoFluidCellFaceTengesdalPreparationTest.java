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
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Separate Tengesdal 2002 test 3 preparation with explicit finite-volume face elevations.
 *
 * <p>
 * The historical section-indexed fixture and its recorded failures remain in
 * {@link TwoFluidUnsplitTengesdalPreparationTest}. These cases use the same fluid, facility lengths and flow rates, but
 * correct the terrain/source integral and steady midpoint pressure placement. A cell crossing the riser base carries
 * its average signed slope; no claim of resolved local bend geometry, experimental amplitude or period is made.
 * </p>
 */
class TwoFluidCellFaceTengesdalPreparationTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidCellFaceTengesdalPreparationTest.class);

  @ParameterizedTest
  @CsvSource({ "16,0.1", "16,0.05", "24,0.05" })
  void shortFaceTerrainRiserHandoffPreservesPhysicalRiseAndConservativePreparation(int count, double maximumStep) {
    verifyPreparation(count, maximumStep, 0.1);
  }

  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.face-terrain.qualification", matches = "true")
  @ParameterizedTest
  @CsvSource({ "16,0.1", "16,0.05", "24,0.05" })
  void fiveSecondFaceTerrainRiserQualificationMustCompleteTheWholeInterval(int count, double maximumStep) {
    verifyPreparation(count, maximumStep, 5.0);
  }

  private static void verifyPreparation(int count, double maximumStep, double duration) {
    TwoFluidPipe pipe = createPipe(count);
    TwoFluidSection[] initial = pipe.getSectionSnapshots();
    double physicalRise = 19.81 * Math.sin(Math.toRadians(-3.0)) + 14.94;
    double representedRise = 0.0;
    for (TwoFluidSection section : initial) {
      representedRise += section.getLength() * Math.sin(section.getInclination());
    }
    assertEquals(physicalRise, representedRise, 1.0e-12);
    assertEquals(1.01325e5, pipe.getOutletStream().getFluid().getPressure("Pa"), 1.0e-8);
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
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(0.0, interval.getMassResidualKg()[phase] / Math.max(1.0, initialMass[phase]), 1.0e-8);
    }
    TwoFluidSection[] unchanged = pipe.getSectionSnapshots();
    for (int cell = 0; cell < count; cell++) {
      assertArrayEquals(initial[cell].getStateVector(), unchanged[cell].getStateVector(), 0.0);
      assertEquals(initial[cell].getPressure(), unchanged[cell].getPressure(), 0.0);
      assertEquals(initial[cell].getElevation(), unchanged[cell].getElevation(), 0.0);
    }
    logger.info(
        "Face-terrain Tengesdal handoff: cells={}, maximumStep={}, duration={}, rise={}, substeps={}, "
            + "rejects={}, evaluations={}, residual={}",
        count, maximumStep, duration, representedRise, interval.getSubsteps().size(), interval.getRejectedAttempts(),
        interval.getModelEvaluations(), interval.getMaximumScaledResidual());
  }

  /** Keep a distinct factory so diagnostic replays cannot silently replace historical terrain measurements. */
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
    Stream inlet = new Stream("Tengesdal 2002 test 3 face-terrain feed", fluid);
    inlet.setFlowRate(gasFlow + oilFlow, "kg/sec");
    inlet.run();
    double length = 19.81 + 14.94;
    double angle = Math.toRadians(-3.0);
    double[] elevations = new double[count + 1];
    for (int face = 0; face <= count; face++) {
      double position = length * face / count;
      elevations[face] = position <= 19.81 ? position * Math.sin(angle) : 19.81 * Math.sin(angle) + position - 19.81;
    }
    TwoFluidPipe pipe = new TwoFluidPipe("Face-terrain unsplit Tengesdal handoff", inlet);
    pipe.setLength(length);
    pipe.setDiameter(diameter);
    pipe.setRoughness(1.5e-6);
    pipe.setNumberOfSections(count);
    pipe.setCellFaceElevationProfile(elevations);
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
    pipe.setUnsplitPressureInterpolationEnabled(true);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }
}
