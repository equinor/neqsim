package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Review regressions for rejected fixed steps and a collapsed stable time step. */
class TwoFluidPipeFinalReviewTest {
  @Test
  void fixedSteppingMustRejectInvalidPredictorsWithoutRepairingPhaseInventory() throws Exception {
    for (int invalidVariable = 0; invalidVariable < 4; invalidVariable++) {
      final int variable = invalidVariable;
      TwoFluidPipe pipe = createPipe();
      double[][] initial = states(pipe);
      TimeIntegrator integrator = new TimeIntegrator(TimeIntegrator.Method.EULER) {
        @Override
        public double[][] step(double[][] state, RHSFunction rhs, double dt) {
          double[][] result = super.step(state, rhs, dt);
          result[1][variable] = variable < 3 ? -1.0e-7 : Double.NaN;
          return result;
        }
      };
      injectIntegrator(pipe, integrator);
      UUID attemptedId = UUID.randomUUID();
      IllegalStateException failure = assertThrows(IllegalStateException.class,
          () -> pipe.runTransient(0.001, attemptedId), "Invalid raw variable " + variable + " was accepted");
      assertTrue(failure.getMessage().contains("invalid conservative value"));
      assertTrue(failure.getMessage().contains("lastRejectedCell=1"));
      assertTrue(failure.getMessage().contains("lastRejectedVariable=" + variable));
      assertTrue(failure.getMessage().contains("lastRejectedPreviousValue=" + initial[1][variable]));
      assertEquals(0.0, pipe.getTime(), 0.0);
      assertEquals(0.0, pipe.getSimulationTime(), 0.0);
      assertEquals(0.0, integrator.getCurrentTime(), 0.0);
      assertEquals(0, pipe.getLastMassBalanceReport().getAcceptedSubsteps());
      assertNotEquals(attemptedId, pipe.getCalculationIdentifier());
      double[][] restored = states(pipe);
      for (int cell = 0; cell < initial.length; cell++) {
        assertArrayEquals(initial[cell], restored[cell], 0.0);
      }
      for (TwoFluidMassBalanceReport.Phase phase : TwoFluidMassBalanceReport.Phase.values()) {
        assertEquals(0.0, pipe.getLastMassBalanceReport().getResidualKg(phase), 1.0e-12);
      }
    }
  }

  @Test
  void invalidFixedStepMustRetainOnlyThePreviouslyAcceptedSubstep() throws Exception {
    TwoFluidPipe pipe = createPipe();
    final int[] attempts = { 0 };
    TimeIntegrator integrator = new TimeIntegrator(TimeIntegrator.Method.EULER) {
      @Override
      public double[][] step(double[][] state, RHSFunction rhs, double dt) {
        double[][] result = super.step(state, rhs, dt);
        if (++attempts[0] == 2) {
          result[1][2] = -1.0e-7;
        }
        return result;
      }
    };
    injectIntegrator(pipe, integrator);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.001, UUID.randomUUID()));
    assertEquals(2, attempts[0]);
    assertEquals(1, pipe.getLastMassBalanceReport().getAcceptedSubsteps());
    assertEquals(0.0005, pipe.getSimulationTime(), 1.0e-16);
    assertEquals(pipe.getSimulationTime(), pipe.getTime(), 0.0);
    assertEquals(pipe.getSimulationTime(), integrator.getCurrentTime(), 0.0);
    assertEquals(pipe.getSimulationTime(), pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 0.0);
    for (TwoFluidMassBalanceReport.Phase phase : TwoFluidMassBalanceReport.Phase.values()) {
      assertEquals(0.0, pipe.getLastMassBalanceReport().getResidualKg(phase), 1.0e-12);
    }
  }

  @Test
  void clockResolutionFailureMustNotBeReportedAsExhaustedSubstepBudget() throws Exception {
    TwoFluidPipe pipe = createPipe();
    pipe.runTransient(0.001, UUID.randomUUID());
    double acceptedTime = pipe.getSimulationTime();
    pipe.setMaximumTransientSubsteps(20);
    pipe.setCflNumber(1.0e-20);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> pipe.runTransient(0.001, UUID.randomUUID()));
    assertTrue(failure.getMessage().contains("time step cannot advance the simulation clock"), failure.getMessage());
    assertTrue(failure.getMessage().contains("after 1 substep attempts"), failure.getMessage());
    assertEquals(acceptedTime, pipe.getSimulationTime(), 0.0);
    assertEquals(acceptedTime, pipe.getTime(), 0.0);
    assertEquals(0, pipe.getLastMassBalanceReport().getAcceptedSubsteps());
    assertEquals(0.0, pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 0.0);
  }

  private static void injectIntegrator(TwoFluidPipe pipe, TimeIntegrator integrator) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("timeIntegrator");
    field.setAccessible(true);
    field.set(pipe, integrator);
  }

  private static double[][] states(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    TwoFluidSection[] sections = (TwoFluidSection[]) field.get(pipe);
    double[][] states = new double[sections.length][];
    for (int cell = 0; cell < sections.length; cell++) {
      states[cell] = sections[cell].getStateVector().clone();
    }
    return states;
  }

  private static TwoFluidPipe createPipe() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("review-feed", fluid);
    feed.setFlowRate(0.1, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe("review-pipe", feed);
    pipe.setLength(40.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(4);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(false);
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    pipe.closeInlet();
    pipe.closeOutlet();
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    for (TwoFluidSection section : (TwoFluidSection[]) field.get(pipe)) {
      section.setGasVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.setOilVelocity(0.0);
      section.setWaterVelocity(0.0);
      section.updateConservativeVariables();
    }
    return pipe;
  }
}
