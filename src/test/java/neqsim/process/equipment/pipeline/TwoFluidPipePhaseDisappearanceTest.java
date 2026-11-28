package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Complete revaporisation of retrograde liquid in a blowdown, shut-in and pressure-driven refill cycle.
 *
 * <p>
 * Equilibrium relaxation removes a phase whose equilibrium target is zero geometrically. Before the disappearance was
 * completed explicitly, the oil inventory decayed to a subnormal mass, its volume fraction underflowed and the
 * conservative endpoint recovery stopped the simulation several hundred relaxation times after the liquid had
 * physically gone. While the trace remained in the annular regime neither phase carried any wall friction.
 * </p>
 */
@Tag("slow")
class TwoFluidPipePhaseDisappearanceTest {
  private static final int CELLS = 6;
  private static final double DT = 0.5;

  /**
   * Blow a rich-gas line down into the two-phase region, shut it in, refill it above the phase envelope and hold it
   * until the former failure time has passed.
   */
  @Test
  void revaporisedLiquidDisappearsExactlyAndTheRefilledLineKeepsRunning() {
    TwoFluidPipe pipe = createPipe();
    double initialMass = pipe.getTotalMassInventory();
    double boundaryMass = 0.0;
    double maximumOil = 0.0;
    double worstResidual = 0.0;
    double maximumRefillGasSpeed = 0.0;

    int blowdownSteps = (int) Math.round(60.0 / DT);
    for (int step = 1; step <= blowdownSteps; step++) {
      pipe.setOutletPressure(130.0 - 50.0 * step / blowdownSteps, "bara");
      pipe.runTransient(DT, null);
      boundaryMass += acceptedBoundaryMass(pipe);
      worstResidual = Math.max(worstResidual, worstResidual(pipe));
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
    }
    assertTrue(maximumOil > 1000.0, "The blowdown must condense liquid, got " + maximumOil + " kg");

    pipe.setGasOnlyOutletTakeoff(false);
    pipe.closeOutlet();
    for (int step = 0; step < (int) Math.round(20.0 / DT); step++) {
      pipe.runTransient(DT, null);
      boundaryMass += acceptedBoundaryMass(pipe);
      worstResidual = Math.max(worstResidual, worstResidual(pipe));
    }

    double refillStartPressure = pipe.getInletFacePressure("Pa");
    pipe.setInletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setInletBoundaryFluid(pipe.getInletStream().getFluid());
    int refillSteps = (int) Math.round(60.0 / DT);
    for (int step = 0; step < refillSteps; step++) {
      pipe.setInletPressure(refillStartPressure + (200.0e5 - refillStartPressure) * (step + 1) / refillSteps);
      pipe.runTransient(DT, null);
      boundaryMass += acceptedBoundaryMass(pipe);
      worstResidual = Math.max(worstResidual, worstResidual(pipe));
      for (double velocity : pipe.getGasVelocityProfile()) {
        maximumRefillGasSpeed = Math.max(maximumRefillGasSpeed, Math.abs(velocity));
      }
    }
    // Without wall friction on the trace-liquid cells the inlet gas reached 190 m/s.
    assertTrue(maximumRefillGasSpeed < 100.0, "Refill gas speed " + maximumRefillGasSpeed + " m/s");

    pipe.closeInlet();
    // The unfixed model stopped about 290 s into this hold.
    for (int step = 0; step < (int) Math.round(320.0 / DT); step++) {
      pipe.runTransient(DT, null);
      boundaryMass += acceptedBoundaryMass(pipe);
      worstResidual = Math.max(worstResidual, worstResidual(pipe));
    }

    assertEquals(0.0, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL), 0.0);
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      assertEquals(0.0, section.getOilMassPerLength(), 0.0);
      assertEquals(0.0, section.getOilMomentumPerLength(), 0.0);
      assertEquals(FlowRegime.SINGLE_PHASE_GAS, section.getFlowRegime());
    }
    assertTrue(worstResidual < 1.0e-10, "Worst phase mass residual " + worstResidual);
    assertEquals(initialMass + boundaryMass, pipe.getTotalMassInventory(), 1.0e-9 * initialMass);
    assertTrue(pipe.getLastComponentConservationReport().isConverged(),
        pipe.getLastComponentConservationReport().getMessage());
  }

  private static double acceptedBoundaryMass(TwoFluidPipe pipe) {
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    return report.getInletMassKg(Phase.TOTAL) - report.getOutletMassKg(Phase.TOTAL);
  }

  private static double worstResidual(TwoFluidPipe pipe) {
    double worst = 0.0;
    for (Phase phase : Phase.values()) {
      worst = Math.max(worst, pipe.getLastMassBalanceReport().getRelativeResidual(phase));
    }
    return worst;
  }

  private static TwoFluidPipe createPipe() {
    SystemInterface fluid = new SystemSrkEos(278.15, 130.0);
    fluid.addComponent("methane", 0.916);
    fluid.addComponent("ethane", 0.050);
    fluid.addComponent("propane", 0.020);
    fluid.addComponent("n-butane", 0.007);
    fluid.addComponent("n-pentane", 0.003);
    fluid.addComponent("n-heptane", 0.004);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("feed", fluid);
    inlet.setFlowRate(0.001, "kg/sec");
    inlet.setTemperature(5.0, "C");
    inlet.setPressure(130.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("phase disappearance", inlet);
    pipe.setLength(5000.0);
    pipe.setDiameter(1.0128);
    pipe.setRoughness(45.0e-6);
    pipe.setNumberOfSections(CELLS);
    double[] faces = new double[CELLS + 1];
    for (int face = 0; face <= CELLS; face++) {
      faces[face] = -300.0 + 250.0 * face / CELLS;
    }
    pipe.setCellFaceElevationProfile(faces);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setCflNumber(0.3);
    pipe.setMaximumTransientSubsteps(500);
    pipe.setComponentTransportEnabled(true);
    pipe.setStoreComponentConservationHistory(false);
    pipe.setIncludeMassTransfer(true);
    pipe.setMassTransferRelaxationTime(0.6);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableJouleThomson(false);
    pipe.setHeatTransferCoefficient(0.0);
    pipe.setSurfaceTemperature(278.15, "K");
    pipe.setOutletPressure(130.0, "bara");
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    double[] wallTemperature = new double[CELLS];
    Arrays.fill(wallTemperature, 278.15);
    pipe.setInitialWallTemperatureProfile(wallTemperature, "K");
    pipe.run();
    pipe.prepareConservativeEnergyTransientFromSteadyState();
    pipe.closeInlet();
    pipe.setWallProperties(0.027, 7850.0, 500.0);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(278.15, "K");
    pipe.setGasOnlyOutletTakeoff(true);
    return pipe;
  }
}
