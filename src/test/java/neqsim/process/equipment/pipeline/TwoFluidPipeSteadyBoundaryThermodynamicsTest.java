package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Explicit pressure boundaries must participate in the steady thermodynamic and momentum solve. */
class TwoFluidPipeSteadyBoundaryThermodynamicsTest {
  private static final double FEED_PRESSURE_BARA = 70.0;
  private static final double OUTLET_PRESSURE_BARA = 50.0;

  @Test
  void gasDensityMatchesReportedPressureWithAnExplicitOutlet() throws Exception {
    TwoFluidPipe pipe = createPipe(false, FEED_PRESSURE_BARA, 0.5);
    pipe.setOutletPressure(OUTLET_PRESSURE_BARA, "bara");
    pipe.run();

    assertSteadyThermodynamics(pipe);
    assertEquals(OUTLET_PRESSURE_BARA * 1.0e5, pipe.getPressureProfile()[pipe.getPressureProfile().length - 1], 1.0e-6);
    assertEquals(FEED_PRESSURE_BARA, pipe.getInletStream().getPressure("bara"), 0.0,
        "Solving the pipe must not mutate its feed pressure");
  }

  @Test
  void allThreePhaseDensitiesMatchTheirReportedBoundaryPressure() throws Exception {
    TwoFluidPipe pipe = createPipe(true, FEED_PRESSURE_BARA, 0.3);
    pipe.setOutletPressure(OUTLET_PRESSURE_BARA, "bara");
    pipe.run();

    assertSteadyThermodynamics(pipe);
    for (TwoFluidSection section : sections(pipe)) {
      assertTrue(section.getGasHoldup() > 0.0);
      assertTrue(section.getOilHoldup() > 0.0);
      assertTrue(section.getWaterHoldup() > 0.0);
    }
    assertEquals(FEED_PRESSURE_BARA, pipe.getInletStream().getPressure("bara"), 0.0);
  }

  @Test
  void explicitInletPressureIsIncludedInTheThermodynamicSolve() throws Exception {
    TwoFluidPipe pipe = createPipe(false, FEED_PRESSURE_BARA, 0.5);
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setInletPressure(90.0, "bara");
    pipe.run();

    assertSteadyThermodynamics(pipe);
    assertEquals(90.0e5, pipe.getPressureProfile()[0], 1.0e-6);
    assertEquals(FEED_PRESSURE_BARA, pipe.getInletStream().getPressure("bara"), 0.0);
  }

  @Test
  void fixedOutletGasProfileDoesNotDependOnTheFeedPressureInitialGuess() throws Exception {
    TwoFluidPipe reference = createPipe(false, 70.0, 20.0);
    TwoFluidPipe differentGuess = createPipe(false, 90.0, 20.0);
    for (TwoFluidPipe pipe : new TwoFluidPipe[] { reference, differentGuess }) {
      pipe.setLength(10000.0);
      pipe.setNumberOfSections(40);
      pipe.setElevationProfile(new double[41]);
      pipe.setOutletPressure(OUTLET_PRESSURE_BARA, "bara");
      pipe.run();
      assertTrue(pipe.isSteadyStateConverged());
    }

    double[] expected = reference.getPressureProfile();
    double[] actual = differentGuess.getPressureProfile();
    assertTrue(expected[0] - expected[expected.length - 1] > 0.5e5,
        "The reference must have enough pressure drop to resolve pressure-density feedback");
    for (int cell = 0; cell < expected.length; cell++) {
      assertEquals(expected[cell], actual[cell], 1000.0,
          "Fixed mass flow, temperature, composition and outlet pressure must determine the profile at cell " + cell);
    }
    assertSteadyThermodynamics(reference);
    assertSteadyThermodynamics(differentGuess);
  }

  @Test
  void anUnchangedGasBoundaryDoesNotCreateAnArtificialThermodynamicTransient() {
    TwoFluidPipe pipe = createPipe(false, FEED_PRESSURE_BARA, 0.05);
    pipe.setOutletPressure(OUTLET_PRESSURE_BARA, "bara");
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.run();

    assertTrue(pipe.isSteadyStateConverged());
    double initialMass = pipe.getTotalMassInventory();
    double[] initialPressure = pipe.getPressureProfile().clone();
    for (int step = 0; step < 10; step++) {
      pipe.runTransient(0.01, null);
      assertTrue(pipe.isCoupledPressureMomentumConverged());
      assertTrue(
          pipe.getLastMassBalanceReport().isWithinTolerance(TwoFluidMassBalanceReport.Phase.TOTAL, 1.0e-8, 1.0e-8));
    }

    assertEquals(initialMass, pipe.getTotalMassInventory(), initialMass * 1.0e-3,
        "Unchanged boundaries must not expel inventory flashed at an inconsistent initial pressure");
    double[] finalPressure = pipe.getPressureProfile();
    for (int cell = 0; cell < initialPressure.length; cell++) {
      assertEquals(initialPressure[cell], finalPressure[cell], 1000.0,
          "No imposed change justifies a pressure impulse at cell " + cell);
    }
    assertFalse(pipe.isTransientOutletBackflowClamped());
  }

  private static TwoFluidPipe createPipe(boolean threePhase, double feedPressureBara, double massFlowKgPerSecond) {
    SystemInterface fluid = new SystemSrkEos(298.15, feedPressureBara);
    fluid.addComponent("methane", 1.0);
    if (threePhase) {
      fluid.addComponent("n-decane", 1.0);
      fluid.addComponent("water", 1.0);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("Fixed-pressure boundary feed", fluid);
    feed.setFlowRate(massFlowKgPerSecond, "kg/sec");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("Pressure and EOS consistency", feed);
    pipe.setLength(100.0);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(8);
    pipe.setElevationProfile(new double[9]);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableJouleThomson(false);
    pipe.setEnableTerrainTracking(false);
    return pipe;
  }

  private static void assertSteadyThermodynamics(TwoFluidPipe pipe) throws Exception {
    assertTrue(pipe.isSteadyStateConverged(),
        "The stationary profile must converge before handoff; iterations=" + pipe.getSteadyStateIterationsUsed()
            + ", pressure floor=" + pipe.isSteadyStatePressureFloorLimited() + ", wall-clock limit="
            + pipe.isSteadyStateWallClockLimited());
    assertFalse(pipe.isSteadyStatePressureFloorLimited());
    TwoFluidSection[] cells = sections(pipe);
    for (int cell = 0; cell < cells.length; cell++) {
      TwoFluidSection section = cells[cell];
      SystemInterface reference = pipe.getInletStream().getFluid().clone();
      reference.setPressure(section.getPressure(), "Pa");
      reference.setTemperature(section.getTemperature(), "K");
      new ThermodynamicOperations(reference).TPflash();
      reference.initPhysicalProperties();
      if (reference.hasPhaseType("gas")) {
        double expected = reference.getPhase("gas").getDensity("kg/m3");
        assertEquals(expected, section.getGasDensity(), expected * 1.0e-6,
            "Gas EOS density does not match reported pressure in cell " + cell);
      }
      if (reference.hasPhaseType("oil")) {
        double expected = reference.getPhase("oil").getDensity("kg/m3");
        assertEquals(expected, section.getOilDensity(), expected * 1.0e-6,
            "Oil EOS density does not match reported pressure in cell " + cell);
      }
      if (reference.hasPhaseType("aqueous")) {
        double expected = reference.getPhase("aqueous").getDensity("kg/m3");
        assertEquals(expected, section.getWaterDensity(), expected * 1.0e-6,
            "Water EOS density does not match reported pressure in cell " + cell);
      }
    }
  }

  private static TwoFluidSection[] sections(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    return (TwoFluidSection[]) field.get(pipe);
  }
}
