package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector.OilWaterResult;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression gates for accepted physical time and a stationary three-phase closed volume. */
class TwoFluidPipeDynamicStepContractTest {
  @Test
  void subPicosecondRequestMustNotSilentlyDoNothing() {
    TwoFluidPipe pipe = createPipe();
    pipe.runTransient(1.0e-13, UUID.randomUUID());
    assertEquals(1.0e-13, pipe.getSimulationTime(), 1.0e-27);
    assertEquals(1.0e-13, pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 1.0e-27);
    assertTrue(pipe.getLastMassBalanceReport().getAcceptedSubsteps() > 0);
  }

  @Test
  void aRepresentableClockIncrementMustNotBeLostByForcedSubdivision() {
    TwoFluidPipe pipe = createPipe();
    pipe.runTransient(0.001, UUID.randomUUID());
    double previous = pipe.getSimulationTime();
    double increment = Math.ulp(previous);
    pipe.runTransient(increment, UUID.randomUUID());
    assertEquals(Math.nextUp(previous), pipe.getSimulationTime(), 0.0);
    assertEquals(pipe.getSimulationTime(), pipe.getTime(), 0.0);
    assertEquals(increment, pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 0.0);
  }

  @Test
  void adaptiveMinimumStepMustNotOvershootRequestedInterval() {
    TwoFluidPipe pipe = createPipe();
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.runTransient(1.0e-11, UUID.randomUUID());
    assertEquals(1.0e-11, pipe.getSimulationTime(), 1.0e-25);
    assertEquals(1.0e-11, pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 1.0e-25);
  }

  @Test
  void equipmentAndPipeClocksAdvanceByTheSameAcceptedTime() {
    TwoFluidPipe pipe = createPipe();
    pipe.runTransient(0.001, UUID.randomUUID());
    pipe.runTransient(0.002, UUID.randomUUID());
    assertEquals(0.003, pipe.getTime(), 1.0e-15);
    assertEquals(pipe.getTime(), pipe.getSimulationTime(), 1.0e-15);
    pipe.run();
    assertEquals(0.0, pipe.getTime(), 0.0);
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
  }

  @Test
  void substepBudgetDoesNotReportAnIncompleteIntervalAsSuccessful() {
    TwoFluidPipe pipe = createPipe();
    pipe.setMaximumTransientSubsteps(1);
    assertEquals(1, pipe.getMaximumTransientSubsteps());
    UUID attemptedId = UUID.randomUUID();
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> pipe.runTransient(0.001, attemptedId));
    assertTrue(failure.getMessage().contains("advanced"));
    assertEquals(0.0005, pipe.getSimulationTime(), 1.0e-16);
    assertEquals(pipe.getSimulationTime(), pipe.getTime(), 0.0);
    assertEquals(pipe.getSimulationTime(), pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 0.0);
    assertNotEquals(attemptedId, pipe.getCalculationIdentifier());
    assertThrows(IllegalArgumentException.class, () -> pipe.setMaximumTransientSubsteps(0));
  }

  @Test
  void aLargeRequestedIntervalCannotEnlargeTheAcousticCflStep() throws Exception {
    TwoFluidPipe pipe = createPipe();
    pipe.setMaximumTransientSubsteps(1);
    double stableStep = Double.POSITIVE_INFINITY;
    for (TwoFluidSection section : sections(pipe)) {
      double speed = Math.max(Math.abs(section.getGasVelocity()) + section.getGasSoundSpeed(),
          Math.abs(section.getLiquidVelocity()) + section.getLiquidSoundSpeed());
      stableStep = Math.min(stableStep, 0.5 * section.getLength() / Math.max(1.0, speed));
    }
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(1000.0, UUID.randomUUID()));
    assertTrue(pipe.getSimulationTime() > 0.0);
    assertTrue(pipe.getSimulationTime() <= stableStep * (1.0 + 1.0e-12),
        "The attempt budget must not enlarge a stable step");
  }

  @Test
  void countercurrentOilWaterFlowCannotCancelTheConvectiveCflLimit() throws Exception {
    TwoFluidPipe pipe = createPipe();
    for (TwoFluidSection section : sections(pipe)) {
      section.setGasVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.setOilVelocity(40.0);
      section.setWaterVelocity(-40.0);
    }
    Method convectiveStep = TwoFluidPipe.class.getDeclaredMethod("calcConvectiveTimeStep");
    convectiveStep.setAccessible(true);
    double step = (Double) convectiveStep.invoke(pipe);
    assertTrue(step * 40.0 <= 0.5 * 10.0,
        "Each phase must travel at most half a cell even if the bulk liquid velocity is zero");
  }

  @Test
  void cflRefinementBelowPointOneMustActuallyReduceTheStableStep() throws Exception {
    TwoFluidPipe pipe = createPipe();
    Method stableStep = TwoFluidPipe.class.getDeclaredMethod("calcStableTimeStep");
    stableStep.setAccessible(true);
    pipe.setCflNumber(0.05);
    double coarse = (Double) stableStep.invoke(pipe);
    pipe.setCflNumber(0.025);
    double fine = (Double) stableStep.invoke(pipe);
    assertEquals(0.5 * coarse, fine, 1.0e-16);
    TimeIntegrator integrator = new TimeIntegrator();
    integrator.setCflNumber(0.005);
    assertEquals(0.005, integrator.getCflNumber(), 0.0);
    for (double invalid : new double[] { 0.0, -0.1, 1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
      assertThrows(IllegalArgumentException.class, () -> pipe.setCflNumber(invalid));
      assertThrows(IllegalArgumentException.class, () -> integrator.setCflNumber(invalid));
    }
  }

  @Test
  void rejectedAdaptiveIntervalMustFailAndRestorePressureAndInventory() throws Exception {
    TwoFluidPipe pipe = createPipe();
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setAdaptiveMaxPressure(1.0); // The initialized 60 bara state cannot pass this gate.
    double[] pressure = pipe.getPressureProfile().clone();
    double mass = pipe.getTotalMassInventory();
    UUID attemptedId = UUID.randomUUID();
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.001, attemptedId));
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
    assertEquals(0.0, pipe.getTime(), 0.0);
    assertEquals(mass, pipe.getTotalMassInventory(), 1.0e-12);
    assertArrayEquals(pressure, pipe.getPressureProfile(), 0.0);
    assertNotEquals(attemptedId, pipe.getCalculationIdentifier());
  }

  @Test
  void rejectedTrialPreservesConfiguredOilWaterClosureAndAcceptedDiagnostics() throws Exception {
    TwoFluidPipe pipe = createPipe();
    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setMaximumTransientSubsteps(1);
    pipe.setAdaptiveMaxPressure(1.0);
    TwoFluidSection[] initialSections = sections(pipe);
    OilWaterResult[] acceptedResults = new OilWaterResult[initialSections.length];
    for (int cell = 0; cell < initialSections.length; cell++) {
      TwoFluidSection section = initialSections[cell];
      section.setGasDensity(20.0);
      section.setOilDensity(700.0);
      section.setWaterDensity(1000.0);
      section.setOilViscosity(0.005);
      section.setWaterViscosity(0.001);
      section.setLiquidHoldup(0.8);
      section.setGasHoldup(0.2);
      section.setWaterCut(0.4);
      section.setGasVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.getOilWaterDetector().setCriticalWeber(2.4);
      section.getOilWaterDetector().setInversionConstant(0.7);
      section.updateConservativeVariables();
      section.extractPrimitiveVariables();
      section.updateThreePhaseProperties();
      acceptedResults[cell] = section.getOilWaterResult();
      assertNotNull(acceptedResults[cell]);
    }

    assertThrows(IllegalStateException.class, () -> pipe.runTransient(1e-4, UUID.randomUUID()));
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
    assertEquals(0, pipe.getLastMassBalanceReport().getAcceptedSubsteps());
    TwoFluidSection[] restored = sections(pipe);
    for (int cell = 0; cell < restored.length; cell++) {
      OilWaterResult actual = restored[cell].getOilWaterResult();
      OilWaterResult expected = acceptedResults[cell];
      assertNotNull(actual, "Accepted oil-water diagnostics must survive a rejected trial");
      assertEquals(expected.regime, actual.regime);
      assertEquals(expected.waterWetting, actual.waterWetting);
      assertEquals(expected.waterDropoutRisk, actual.waterDropoutRisk);
      assertEquals(expected.oilContinuous, actual.oilContinuous);
      assertEquals(expected.effectiveViscosity, actual.effectiveViscosity, 0.0);
      assertEquals(expected.inversionWaterFraction, actual.inversionWaterFraction, 0.0);
      assertEquals(expected.criticalDispersionVelocity, actual.criticalDispersionVelocity, 0.0);
      assertEquals(expected.maxDropletDiameter, actual.maxDropletDiameter, 0.0);
      assertEquals(2.4, restored[cell].getOilWaterDetector().getCriticalWeber(), 0.0);
      assertEquals(0.7, restored[cell].getOilWaterDetector().getInversionConstant(), 0.0);
    }
  }

  @Test
  void adaptiveTrialsCannotBorrowInventoryFromAnotherPhaseToRepairNegativeMass() throws Exception {
    for (boolean stiffDrag : new boolean[] { false, true }) {
      for (int phase = 0; phase < 3; phase++) {
        TwoFluidPipe pipe = createPipe();
        pipe.closeInlet();
        pipe.closeOutlet();
        pipe.setEnableAdaptiveTimestepping(true);
        pipe.setEnableStiffBubbleDrag(stiffDrag);
        final int negativePhase = phase;
        final int[] attempts = { 0 };
        TimeIntegrator integrator = new TimeIntegrator(TimeIntegrator.Method.EULER) {
          @Override
          public double[][] step(double[][] state, RHSFunction rhs, double dt) {
            double[][] advanced = super.step(state, rhs, dt);
            if (++attempts[0] == 1) {
              // The old 1e-3 kg/m acceptance allowance hid this deficit by transferring
              // inventory from the other phases during primitive recovery.
              double removedMass = advanced[1][negativePhase] + 1e-7;
              advanced[1][negativePhase] = -1e-7;
              advanced[1][(negativePhase + 1) % 3] += removedMass;
            }
            return advanced;
          }
        };
        Field integratorField = TwoFluidPipe.class.getDeclaredField("timeIntegrator");
        integratorField.setAccessible(true);
        integratorField.set(pipe, integrator);
        pipe.runTransient(1e-4, UUID.randomUUID());
        TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
        assertEquals(report.getAcceptedSubsteps() + 1, attempts[0]);
        assertEquals(1e-4, report.getElapsedTimeSeconds(), 1e-16);
        for (TwoFluidMassBalanceReport.Phase inventory : TwoFluidMassBalanceReport.Phase.values()) {
          assertEquals(0.0, report.getResidualKg(inventory), 1e-12);
        }
      }
    }
  }

  @Test
  void closedUniformThreePhaseMixtureMustPreserveAllValidPhaseDensities() throws Exception {
    for (TimeIntegrator.Method method : new TimeIntegrator.Method[] { TimeIntegrator.Method.RK2,
        TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION }) {
      for (double gasDensity : new double[] { 20.0, 0.02 }) {
        TwoFluidPipe pipe = createPipe();
        pipe.setTimeIntegrationMethod(method);
        pipe.setEnableCoupledPressureMomentum(true);
        pipe.closeInlet();
        pipe.closeOutlet();
        for (TwoFluidSection section : sections(pipe)) {
          section.setPressure(6.0e6);
          section.setGasDensity(gasDensity);
          section.setOilDensity(700.0);
          section.setWaterDensity(1000.0);
          section.setLiquidDensity(812.5);
          section.setGasSoundSpeed(350.0);
          section.setLiquidSoundSpeed(1200.0);
          section.setGasHoldup(0.2);
          section.setWaterCut(0.375);
          section.setLiquidHoldup(0.8);
          section.setGasVelocity(0.0);
          section.setLiquidVelocity(0.0);
          section.setOilVelocity(0.0);
          section.setWaterVelocity(0.0);
          section.updateConservativeVariables();
          section.extractPrimitiveVariables();
        }
        double mass = pipe.getTotalMassInventory();
        pipe.runTransient(1.0e-4, UUID.randomUUID());
        assertTrue(pipe.isCoupledPressureMomentumConverged());
        for (TwoFluidSection section : sections(pipe)) {
          assertEquals(6.0e6, section.getPressure(), 1.0e-3, method + " created a pressure pulse in a resting cell");
          assertEquals(gasDensity, section.getGasDensity(), 1.0e-9);
          assertEquals(700.0, section.getOilDensity(), 1.0e-6);
          assertEquals(1000.0, section.getWaterDensity(), 1.0e-6);
        }
        assertEquals(mass, pipe.getTotalMassInventory(), 1.0e-10);
        assertTrue(
            pipe.getLastMassBalanceReport().isWithinTolerance(TwoFluidMassBalanceReport.Phase.TOTAL, 1.0e-10, 1.0e-12));
      }
    }
  }

  private static TwoFluidSection[] sections(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    return (TwoFluidSection[]) field.get(pipe);
  }

  private static TwoFluidPipe createPipe() {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(0.1, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe("dynamic-contract", feed);
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
    return pipe;
  }
}
