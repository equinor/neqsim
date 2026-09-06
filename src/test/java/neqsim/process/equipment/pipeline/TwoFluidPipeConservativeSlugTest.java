package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** End-to-end accepted-step contracts for the conservative tracking mode. */
class TwoFluidPipeConservativeSlugTest {
  @Test
  void thinBodyAtACellFaceCannotDrainMoreLiquidThanTheCellContains() throws Exception {
    TwoFluidPipe pipe = pipe(1.0);
    SlugBubbleUnit slug = seed(pipe);
    slug.tailPosition = 9.9001;
    slug.frontPosition = 10.0001;
    slug.slugLength = slug.frontPosition - slug.tailPosition;
    for (TwoFluidSection cell : sections(pipe)) {
      cell.setGasHoldup(0.999);
      cell.setLiquidHoldup(0.001);
      cell.setWaterHoldup(0.001);
      cell.setGasVelocity(1.0);
      cell.setLiquidVelocity(1.0);
      cell.setWaterVelocity(1.0);
      cell.updateConservativeVariables();
    }
    Field equationsField = TwoFluidPipe.class.getDeclaredField("equations");
    equationsField.setAccessible(true);
    TwoFluidConservationEquations equations = (TwoFluidConservationEquations) equationsField.get(pipe);
    equations.setConservativeSlugs(pipe.getLagrangianSlugTracker().getSlugs());
    Method timeStep = TwoFluidPipe.class.getDeclaredMethod("calcConvectiveTimeStep");
    timeStep.setAccessible(true);
    double dt = (Double) timeStep.invoke(pipe);
    double[][] derivative = equations.calcRHS(sections(pipe), 10.0);
    // The velocity-only CFL exceeds the drainage time for this hundred-fold
    // face/cell holdup contrast. The accepted predictor must stay positive.
    assertTrue(dt < 0.2, "Subcell phase availability must restrict the material-velocity CFL");
    assertTrue(derivative[0][2] < 0.0, "The test must actually drain water from the first cell");
    for (int cell = 0; cell < sections(pipe).length; cell++) {
      double[] state = sections(pipe)[cell].getStateVector();
      for (int phase = 0; phase < 3; phase++) {
        assertTrue(state[phase] + dt * derivative[cell][phase] >= 0.0,
            "Reconstructed outgoing flux depleted phase " + phase + " in cell " + cell);
      }
    }
  }

  @Test
  void coupledTrackedSlugPreservesEveryPhaseInAClosedPipe() throws Exception {
    for (double waterCut : new double[] { 0.0, 0.4, 1.0 }) {
      TwoFluidPipe pipe = pipe(waterCut);
      SlugBubbleUnit slug = seed(pipe);
      pipe.runTransient(1e-3, UUID.randomUUID());
      assertEquals(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN, pipe.getSlugTrackingMode());
      assertTrue(pipe.getLagrangianSlugTracker().isConservativeFilmCouplingEnabled());
      assertTrue(pipe.isCoupledPressureMomentumConverged());
      assertEquals(pipe.getSimulationTime(), slug.age, 1e-14);
      assertEquals(1e-3, pipe.getSimulationTime(), 1e-14);
      for (TwoFluidMassBalanceReport.Phase phase : TwoFluidMassBalanceReport.Phase.values()) {
        assertEquals(0.0, pipe.getLastMassBalanceReport().getResidualKg(phase), 1e-10);
      }
      for (TwoFluidSection section : sections(pipe)) {
        for (int phase = 0; phase < 3; phase++) {
          assertTrue(section.getStateVector()[phase] >= 0.0);
        }
      }
    }
  }

  @Test
  void rejectedHydrodynamicTrialCannotAdvanceTrackedGeometry() throws Exception {
    TwoFluidPipe pipe = pipe(0.4);
    SlugBubbleUnit slug = seed(pipe);
    double initialFront = slug.frontPosition;
    final int[] attempts = { 0 };
    TimeIntegrator integrator = new TimeIntegrator(TimeIntegrator.Method.RK2) {
      @Override
      public double[][] step(double[][] state, RHSFunction rhs, double dt) {
        if (attempts[0] == 1) {
          assertEquals(initialFront, slug.frontPosition, 0.0, "Rejected trial moved the marker");
          assertEquals(0.0, slug.age, 0.0, "Rejected trial advanced the marker clock");
        }
        double[][] result = super.step(state, rhs, dt);
        if (++attempts[0] == 1) {
          result[1][0] = Double.NaN;
        }
        return result;
      }
    };
    Field field = TwoFluidPipe.class.getDeclaredField("timeIntegrator");
    field.setAccessible(true);
    field.set(pipe, integrator);
    pipe.runTransient(1e-4, UUID.randomUUID());
    assertEquals(pipe.getLastMassBalanceReport().getAcceptedSubsteps() + 1, attempts[0]);
    assertEquals(pipe.getSimulationTime(), slug.age, 1e-15);
    assertEquals(1e-4, slug.age, 1e-15);
  }

  private static SlugBubbleUnit seed(TwoFluidPipe pipe) throws Exception {
    SlugCharacteristics characteristics = new SlugCharacteristics();
    characteristics.tailPosition = 5.0;
    characteristics.frontPosition = 21.0;
    characteristics.length = 16.0;
    characteristics.holdup = 0.9;
    characteristics.volume = 16.0 * sections(pipe)[0].getArea() * 0.9;
    SlugBubbleUnit slug = pipe.getLagrangianSlugTracker().initializeTerrainSlug(characteristics, sections(pipe));
    assertNotNull(slug);
    return slug;
  }

  private static TwoFluidPipe pipe(double waterCut) throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(0.1, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe("conservative-slug", feed);
    pipe.setLength(40.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(4);
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN);
    pipe.getLagrangianSlugTracker().setEnableInletSlugGeneration(false);
    pipe.getLagrangianSlugTracker().setEnableWakeEffects(false);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK2);
    pipe.closeInlet();
    pipe.closeOutlet();
    for (TwoFluidSection section : sections(pipe)) {
      section.setGasDensity(20.0);
      section.setOilDensity(800.0);
      section.setWaterDensity(1000.0);
      section.setLiquidDensity(800.0 * (1.0 - waterCut) + 1000.0 * waterCut);
      section.setGasHoldup(0.6);
      section.setLiquidHoldup(0.4);
      section.setWaterCut(waterCut);
      section.setOilHoldup(0.4 * (1.0 - waterCut));
      section.setWaterHoldup(0.4 * waterCut);
      section.setGasVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.setOilVelocity(0.0);
      section.setWaterVelocity(0.0);
      section.setGasEnthalpy(1e6);
      section.setLiquidEnthalpy(1e6);
      section.updateConservativeVariables();
    }
    return pipe;
  }

  private static TwoFluidSection[] sections(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    return (TwoFluidSection[]) field.get(pipe);
  }
}
