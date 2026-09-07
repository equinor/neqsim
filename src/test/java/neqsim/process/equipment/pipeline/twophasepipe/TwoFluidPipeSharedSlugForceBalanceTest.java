package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.SlugForceBalance;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Mechanical and long-horizon qualification of the opt-in slug closure. */
class TwoFluidPipeSharedSlugForceBalanceTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeSharedSlugForceBalanceTest.class);

  private TwoFluidSection section(double holdup, double gasVelocity, double liquidVelocity) {
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, 0.3, 0.0);
    section.setGasHoldup(1.0 - holdup);
    section.setLiquidHoldup(holdup);
    section.setOilHoldup(holdup);
    section.setWaterHoldup(0.0);
    section.setGasDensity(40.0);
    section.setLiquidDensity(750.0);
    section.setOilDensity(750.0);
    section.setGasViscosity(1.2e-5);
    section.setLiquidViscosity(0.002);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(liquidVelocity);
    section.setOilVelocity(liquidVelocity);
    section.setFlowRegime(FlowRegime.SLUG);
    section.setSurfaceTension(0.02);
    section.setRoughness(4.6e-5);
    return section;
  }

  @Test
  void phaseBalancesShareOnePressureGradientWithNonzeroSlip() {
    SlugForceBalance closure = new SlugForceBalance();
    TwoFluidSection section = section(0.5, 4.0, 2.0);
    double holdup = closure.solveHoldup(section, 2.0, 1.0);
    assertEquals(0.5, section.getLiquidHoldup(), 0.0, "root evaluation must not change input state");
    TwoFluidSection equilibrium = section(holdup, 2.0 / (1.0 - holdup), 1.0 / holdup);
    assertTrue(equilibrium.getGasVelocity() > equilibrium.getLiquidVelocity());
    assertEquals(0.0, closure.residual(equilibrium), 1.0e-6);
    SlugForceBalance.Forces force = closure.evaluate(equilibrium);
    assertEquals(closure.pressureGradient(equilibrium),
        force.interfaceForce / (equilibrium.getGasHoldup() * equilibrium.getArea()), 1.0e-6);
    assertTrue(force.interfaceForce * (equilibrium.getGasVelocity() - equilibrium.getLiquidVelocity()) >= 0.0,
        "passive interface exchange must dissipate relative motion");
  }

  @Test
  void wetWallAndInterphaseExchangePreserveMixtureMomentum() {
    TwoFluidSection section = section(0.5, 4.0, 2.0);
    SlugForceBalance closure = new SlugForceBalance();
    SlugForceBalance.Forces force = closure.evaluate(section);
    WallFriction.WallFrictionResult legacy = new WallFriction().calculate(FlowRegime.SLUG, 2.0, 2.0, 40.0, 750.0,
        1.2e-5, 0.002, 0.5, 0.3, 4.6e-5);
    assertEquals(0.0, force.gasWall, 0.0);
    assertEquals(legacy.gasWallForcePerLength + legacy.liquidWallForcePerLength, force.liquidWall, 1.0e-12);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setSharedSlugForceBalanceEnabled(true);
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(false);
    section.setInterfacialShear(force.interfaceForce / section.getDiameter());
    section.setInterfacialWidth(section.getDiameter());
    double[][] source = equations.calcSourceTerms(new TwoFluidSection[] { section });
    assertEquals(-force.interfaceForce, source[0][3], 1.0e-10);
    assertEquals(-force.liquidWall, source[0][3] + source[0][4] + source[0][5], 1.0e-10);
  }

  /** The unchanged 5 km liquid-rich null fixture, with only the new closure selected. */
  private TwoFluidPipe liquidRichPipe(boolean shared, int cells, double cfl) {
    SystemInterface fluid = new SystemSrkEos(323.15, 60.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", 12.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50.0 * 3600.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.30);
    pipe.setNumberOfSections(cells);
    pipe.setCflNumber(cfl);
    pipe.setElevationProfile(new double[cells]);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(4.0, "C");
    assertFalse(pipe.isSharedSlugForceBalanceEnabled());
    pipe.setSharedSlugForceBalanceEnabled(shared);
    pipe.setEnableInterfacialPressure(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.run();
    return pipe;
  }

  @Test
  void absentPhasesReceiveNoForce() {
    SlugForceBalance closure = new SlugForceBalance();
    SlugForceBalance.Forces gasOnly = closure.evaluate(section(0.0, 4.0, 0.0));
    assertTrue(gasOnly.gasWall > 0.0);
    assertEquals(0.0, gasOnly.liquidWall, 0.0);
    assertEquals(0.0, gasOnly.interfaceForce, 0.0);
    SlugForceBalance.Forces liquidOnly = closure.evaluate(section(1.0, 0.0, 2.0));
    assertTrue(liquidOnly.liquidWall > 0.0);
    assertEquals(0.0, liquidOnly.gasWall, 0.0);
    assertEquals(0.0, liquidOnly.interfaceForce, 0.0);
  }

  @Test
  void reverseLiquidWallMotionRemainsDissipative() {
    SlugForceBalance closure = new SlugForceBalance();
    for (double gasVelocity : new double[] { -4.0, 0.0, 4.0 }) {
      for (double liquidVelocity : new double[] { -2.0, 0.0, 2.0 }) {
        SlugForceBalance.Forces force = closure.evaluate(section(0.5, gasVelocity, liquidVelocity));
        assertTrue(force.liquidWall * liquidVelocity >= 0.0);
        assertTrue(force.interfaceForce * (gasVelocity - liquidVelocity) >= 0.0);
      }
    }
  }

  @Test
  void nearbyRatesAndInclinationsRetainMechanicalEquilibrium() {
    SlugForceBalance closure = new SlugForceBalance();
    for (double gasFlux : new double[] { 1.5, 2.0, 2.5 }) {
      for (double liquidFlux : new double[] { 0.8, 1.0, 1.2 }) {
        for (double inclination : new double[] { -0.02, 0.0, 0.02 }) {
          TwoFluidSection section = section(0.5, 4.0, 2.0);
          section.setInclination(inclination);
          double holdup = closure.solveHoldup(section, gasFlux, liquidFlux);
          TwoFluidSection equilibrium = section(holdup, gasFlux / (1.0 - holdup), liquidFlux / holdup);
          equilibrium.setInclination(inclination);
          assertTrue(holdup > 0.0 && holdup < 1.0);
          assertEquals(0.0, closure.residual(equilibrium), 1.0e-6);
        }
      }
    }
    assertThrows(IllegalArgumentException.class, () -> closure.solveHoldup(section(0.5, 4.0, 2.0), Double.NaN, 1.0));
    assertThrows(IllegalArgumentException.class, () -> closure.solveHoldup(section(0.5, 4.0, 2.0), 2.0, 0.0));
  }

  @Test
  void incompatiblePressureConfigurationFailsBeforeAdvancing() {
    TwoFluidPipe pipe = liquidRichPipe(true, 10, 0.5);
    pipe.setEnableCoupledPressureMomentum(false);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(1.0, null));
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(false);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(1.0, null));
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
  }

  @Test
  @Tag("slow")
  void liquidRichInventoryRemainsWithinTwoPercentFor1800Seconds() {
    assertTrue(nullDrift(true, 40, 5.0, 0.5) < 0.02);
  }

  @Test
  @Tag("slow")
  void liquidRichInventoryRemainsQualifiedWithRefinedMeshAndTimeStep() {
    assertTrue(nullDrift(true, 80, 2.5, 0.25) < 0.02);
  }

  @Test
  void refinedSubstepsDoNotLeaveAnUnrepresentableClockRemainder() {
    TwoFluidPipe pipe = liquidRichPipe(true, 80, 0.25);
    for (int step = 0; step < 28; step++) {
      pipe.runTransient(2.5, null);
      assertEquals(2.5, pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 1.0e-12);
    }
    assertEquals(70.0, pipe.getSimulationTime(), 1.0e-8);
  }

  private double nullDrift(boolean shared, int cells, double outerStep, double cfl) {
    TwoFluidPipe pipe = liquidRichPipe(shared, cells, cfl);
    assertTrue(pipe.isSteadyStateConverged());
    assertFalse(pipe.isSteadyStatePressureFloorLimited());
    assertFalse(pipe.isSteadyStateWallClockLimited());
    double initial = pipe.getTotalMassInventory();
    logger.info("Null initial: shared={} cells={} mass={} kg midpointHoldup={} outletPressure={} Pa", shared, cells,
        initial, pipe.getLiquidHoldupProfile()[cells / 2], pipe.getPressureProfile()[cells - 1]);
    double maximumResidual = 0.0;
    for (int step = 0; step < (int) (1800.0 / outerStep); step++) {
      pipe.runTransient(outerStep, UUID.randomUUID());
      maximumResidual = Math.max(maximumResidual,
          Math.abs(pipe.getLastMassBalanceReport().getRelativeResidual(TwoFluidMassBalanceReport.Phase.TOTAL)));
    }
    double drift = Math.abs(pipe.getTotalMassInventory() - initial) / initial;
    logger.info(
        "Null final: shared={} cells={} step={} cfl={} drift={} % maximumResidual={} backflow={} limited={} rejected={}",
        shared, cells, outerStep, cfl, drift * 100.0, maximumResidual, pipe.isTransientOutletBackflowClamped(),
        pipe.isTransientCoupledPressureMomentumCorrectionLimited(),
        pipe.getTransientCoupledPressureMomentumRejectedSubsteps());
    assertEquals(1800.0, pipe.getSimulationTime(), 1.0e-8);
    assertTrue(maximumResidual < 1.0e-10);
    if (shared) {
      assertFalse(pipe.isTransientOutletBackflowClamped());
      assertFalse(pipe.isTransientCoupledPressureMomentumCorrectionLimited());
      assertEquals(0, pipe.getTransientCoupledPressureMomentumRejectedSubsteps());
      assertTrue(drift < 0.02, "1800 s inventory drift=" + drift);
    }
    return drift;
  }
}
