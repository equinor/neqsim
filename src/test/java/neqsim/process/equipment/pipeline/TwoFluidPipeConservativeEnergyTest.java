package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemIdealGas;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent first-law regressions for opt-in conservative transient energy recovery.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeConservativeEnergyTest {
  /** Identifier shared by the accepted physical intervals. */
  private static final UUID CALCULATION_ID = UUID.randomUUID();

  /**
   * Reproduce issue 4595 without treating its nonuniform cells as a well-mixed vessel.
   */
  @Test
  void idealNitrogenBlowdownRecoversCoolingAndTheStoredEnergy() {
    Fixture fixture = createPipe("conservative-blowdown", true, 300.0, 2.0, 2);
    fixture.pipe.closeInlet();
    fixture.pipe.openOutlet(1.9, "bara");
    double initialMass = fixture.pipe.getTotalMassInventory();
    double inletMass = 0.0;
    double outletMass = 0.0;
    assertPublishedStateCloses(fixture.pipe);

    for (int step = 0; step < 20; step++) {
      fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
      assertPublishedStateCloses(fixture.pipe);
      TwoFluidMassBalanceReport mass = fixture.pipe.getLastMassBalanceReport();
      inletMass += mass.getInletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL);
      outletMass += mass.getOutletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL);
      assertEquals(0.0, mass.getResidualKg(TwoFluidMassBalanceReport.Phase.TOTAL), 1.0e-12);
      assertNotNull(fixture.pipe.getLastComponentConservationReport());
      assertTrue(fixture.pipe.getLastComponentConservationReport().isConverged());
    }

    assertEquals(0.002, fixture.pipe.getSimulationTime(), 1.0e-12);
    assertEquals(0.0, inletMass, 0.0);
    assertTrue(outletMass > 0.0);
    assertEquals(initialMass - outletMass, fixture.pipe.getTotalMassInventory(), 1.0e-12);
    assertTrue(massWeightedTemperature(fixture.pipe) < 299.9,
        "Adiabatic enthalpy outflow must cool ideal nitrogen even when its Joule-Thomson coefficient is zero");
    assertFalse(fixture.pipe.isTransientOutletBackflowClamped());
    assertFalse(fixture.pipe.isTransientCoupledPressureMomentumFailureDetected());
  }

  /**
   * Preserve the existing default while demonstrating that the issue reproducer distinguishes the paths.
   */
  @Test
  void legacyModeRemainsTheDefaultAndRetainsItsHistoricalBlowdownResponse() {
    Fixture fixture = createPipe("legacy-blowdown", false, 300.0, 2.0, 2);
    assertFalse(fixture.pipe.isUseConservativeEnergyRecovery());
    fixture.pipe.closeInlet();
    fixture.pipe.openOutlet(1.9, "bara");
    for (int step = 0; step < 20; step++) {
      fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
    }
    assertArrayEquals(new double[] {300.0, 300.0}, fixture.pipe.getTemperatureProfile(), 1.0e-9);
    assertTrue(Math.abs(storedEnergy(fixture.pipe) - independentIdealGasEnergy(fixture.pipe)) > 1.0,
        "This control must retain the reported unrecovered-energy diagnostic");
  }

  /**
   * The calorically perfect filling limit is accurate here because temperature rises by less than 0.1 K.
   *
   * @throws Exception when the stationary reference cells cannot be accessed
   */
  @Test
  void idealGasFillingMatchesTheRigidVesselFirstLaw() throws Exception {
    Fixture fixture = createPipe("conservative-filling", true, 300.0, 2.0, 2);
    makeStationary(fixture.pipe);
    fixture.pipe.closeOutlet();
    double initialMass = fixture.pipe.getTotalMassInventory();
    double initialEnergy = storedEnergy(fixture.pipe);
    double gamma = fixture.fluid.getCp("J/kgK") / fixture.fluid.getCv("J/kgK");
    double inletEnthalpy = fixture.inlet.getFluid().getEnthalpy("J/kg");
    double inletMass = 0.0;
    double outletMass = 0.0;

    for (int step = 0; step < 20; step++) {
      fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
      assertPublishedStateCloses(fixture.pipe);
      TwoFluidMassBalanceReport mass = fixture.pipe.getLastMassBalanceReport();
      inletMass += mass.getInletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL);
      outletMass += mass.getOutletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL);
    }

    double finalMass = fixture.pipe.getTotalMassInventory();
    assertTrue(inletMass > 0.0);
    assertEquals(0.0, outletMass, 0.0);
    assertEquals(initialMass + inletMass, finalMass, 1.0e-12);
    assertEquals(initialEnergy + inletMass * inletEnthalpy, storedEnergy(fixture.pipe), 1.0e-5,
        "A rigid adiabatic cell gains inlet enthalpy rather than inlet internal energy");
    double perfectGasTemperature = (initialMass * 300.0 + gamma * 300.0 * inletMass) / finalMass;
    assertEquals(perfectGasTemperature, massWeightedTemperature(fixture.pipe), 1.0e-4);
    assertTrue(massWeightedTemperature(fixture.pipe) > 300.0);
  }

  /**
   * A stationary closed adiabatic state must be a fixed point of both conservative and primitive states.
   *
   * @throws Exception when the stationary reference cells cannot be accessed
   */
  @Test
  void stationaryClosedAdiabaticStateIsInvariant() throws Exception {
    Fixture fixture = createPipe("conservative-closed", true, 300.0, 2.0, 2);
    makeStationary(fixture.pipe);
    fixture.pipe.closeInlet();
    fixture.pipe.closeOutlet();
    double initialEnergy = storedEnergy(fixture.pipe);
    double initialMass = fixture.pipe.getTotalMassInventory();
    double[] initialPressure = fixture.pipe.getPressureProfile();
    double[] initialTemperature = fixture.pipe.getTemperatureProfile();

    for (int step = 0; step < 3; step++) {
      fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
      assertPublishedStateCloses(fixture.pipe);
    }

    assertEquals(initialEnergy, storedEnergy(fixture.pipe), 1.0e-7);
    assertEquals(initialMass, fixture.pipe.getTotalMassInventory(), 1.0e-13);
    assertArrayEquals(initialTemperature, fixture.pipe.getTemperatureProfile(), 1.0e-7);
    assertArrayEquals(initialPressure, fixture.pipe.getPressureProfile(), 1.0e-3);
  }

  /**
   * A rigid sealed cell must store wall heat in internal energy once and raise its EOS pressure.
   *
   * @throws Exception when the stationary reference cells cannot be accessed
   */
  @Test
  void closedIsochoricHeatingStoresWallHeatOnce() throws Exception {
    Fixture fixture = createPipe("conservative-heating", true, 300.0, 2.0, 2);
    makeStationary(fixture.pipe);
    fixture.pipe.closeInlet();
    fixture.pipe.closeOutlet();
    fixture.pipe.setSurfaceTemperature(310.0, "K");
    fixture.pipe.setHeatTransferCoefficient(10.0);
    fixture.pipe.setWallProperties(1.0e-4, 100.0, 100.0);
    double initialEnergy = storedEnergy(fixture.pipe);
    double initialMass = fixture.pipe.getTotalMassInventory();
    double wallHeatToFluid = 0.0;

    for (int step = 0; step < 5; step++) {
      fixture.pipe.runTransient(1.0e-3, CALCULATION_ID);
      assertPublishedStateCloses(fixture.pipe);
      assertNotNull(fixture.pipe.getLastConservativeEnergyReport());
      wallHeatToFluid += fixture.pipe.getLastConservativeEnergyReport().getWallHeatToFluidJ();
      assertEquals(0.0, fixture.pipe.getLastConservativeEnergyReport().getResidualJ(), 1.0e-5);
    }

    assertEquals(initialMass, fixture.pipe.getTotalMassInventory(), 1.0e-12);
    assertTrue(massWeightedTemperature(fixture.pipe) > 300.0);
    assertTrue(storedEnergy(fixture.pipe) > initialEnergy);
    assertEquals(wallHeatToFluid, storedEnergy(fixture.pipe) - initialEnergy, 1.0e-5,
        "The sealed fluid stores exactly the heat transferred from the wall, without another temperature correction");
    for (TwoFluidSection section : fixture.pipe.getSectionSnapshots()) {
      assertEquals(section.getTemperature() / 300.0, section.getPressure() / 2.0e5, 1.0e-7,
          "Rigid ideal-gas heating raises pressure in proportion to absolute temperature");
    }
  }

  /**
   * Nearby P/T conditions must recover thermodynamic energy without point-specific tuning.
   */
  @Test
  void nearbyBlowdownConditionsRecoverVolumeAndEnergy() {
    double[][] conditions = {{295.0, 1.95}, {305.0, 2.05}};
    for (double[] condition : conditions) {
      Fixture fixture = createPipe("perturbed-blowdown", true, condition[0], condition[1], 2);
      fixture.pipe.closeInlet();
      fixture.pipe.openOutlet(condition[1] * 0.95, "bara");
      for (int step = 0; step < 5; step++) {
        fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
        assertPublishedStateCloses(fixture.pipe);
      }
      assertTrue(massWeightedTemperature(fixture.pipe) < condition[0]);
    }
  }

  /**
   * Serialization must preserve the selected owner and independent transient state.
   */
  @Test
  void serializedCopyKeepsTheConservativeOwnerAndProducesTheSameNextState() {
    Fixture fixture = createPipe("conservative-copy", true, 300.0, 2.0, 2);
    fixture.pipe.closeInlet();
    fixture.pipe.openOutlet(1.9, "bara");
    fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
    TwoFluidPipe copied = (TwoFluidPipe) fixture.pipe.copy();
    assertTrue(copied.isUseConservativeEnergyRecovery());
    fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
    copied.runTransient(1.0e-4, CALCULATION_ID);
    assertArrayEquals(fixture.pipe.getTemperatureProfile(), copied.getTemperatureProfile(), 1.0e-10);
    assertArrayEquals(fixture.pipe.getPressureProfile(), copied.getPressureProfile(), 1.0e-7);
    assertEquals(storedEnergy(fixture.pipe), storedEnergy(copied), 1.0e-8);
    assertPublishedStateCloses(copied);
  }

  /**
   * An unsupported integrator must fail before changing a successfully accepted interval.
   */
  @Test
  void unsupportedIntegratorFailsWithoutAdvancingState() {
    Fixture fixture = createPipe("conservative-unsupported", true, 300.0, 2.0, 2);
    double initialEnergy = storedEnergy(fixture.pipe);
    double initialMass = fixture.pipe.getTotalMassInventory();
    double[] temperature = fixture.pipe.getTemperatureProfile();
    double[] pressure = fixture.pipe.getPressureProfile();
    assertThrows(IllegalStateException.class, () -> {
      fixture.pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK4);
      fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
    });
    assertEquals(0.0, fixture.pipe.getSimulationTime(), 0.0);
    assertEquals(initialEnergy, storedEnergy(fixture.pipe), 0.0);
    assertEquals(initialMass, fixture.pipe.getTotalMassInventory(), 0.0);
    assertArrayEquals(temperature, fixture.pipe.getTemperatureProfile(), 0.0);
    assertArrayEquals(pressure, fixture.pipe.getPressureProfile(), 0.0);
  }

  /** A converged legacy steady state can transfer energy ownership once without changing accepted fields. */
  @Test
  void convergedSteadyStateCanHandOffToConservativeTransientOwnership() {
    Fixture fixture = createPipe("steady-handoff", false, 300.0, 2.0, 2);
    double[] pressure = fixture.pipe.getPressureProfile();
    double[] temperature = fixture.pipe.getTemperatureProfile();
    double[] holdup = fixture.pipe.getLiquidHoldupProfile();
    double[] gasVelocity = fixture.pipe.getGasVelocityProfile();
    double[] liquidVelocity = fixture.pipe.getLiquidVelocityProfile();
    double energy = storedEnergy(fixture.pipe);

    fixture.pipe.prepareConservativeEnergyTransientFromSteadyState();

    assertTrue(fixture.pipe.isUseConservativeEnergyRecovery());
    assertTrue(fixture.pipe.isComponentTransportEnabled());
    assertTrue(fixture.pipe.isCoupledPressureMomentumEnabled());
    assertEquals(TimeIntegrator.Method.EULER, fixture.pipe.getTimeIntegrationMethod());
    assertArrayEquals(pressure, fixture.pipe.getPressureProfile(), 0.0);
    assertArrayEquals(temperature, fixture.pipe.getTemperatureProfile(), 0.0);
    assertArrayEquals(holdup, fixture.pipe.getLiquidHoldupProfile(), 0.0);
    assertArrayEquals(gasVelocity, fixture.pipe.getGasVelocityProfile(), 0.0);
    assertArrayEquals(liquidVelocity, fixture.pipe.getLiquidVelocityProfile(), 0.0);
    assertEquals(energy, storedEnergy(fixture.pipe), 0.0);

    fixture.pipe.prepareConservativeEnergyTransientFromSteadyState();
    fixture.pipe.closeInlet();
    fixture.pipe.openOutlet(1.9, "bara");
    fixture.pipe.runTransient(1.0e-4, CALCULATION_ID);
    assertNotNull(fixture.pipe.getLastConservativeEnergyReport());
    assertNotNull(fixture.pipe.getLastComponentConservationReport());
    assertThrows(IllegalStateException.class, fixture.pipe::prepareConservativeEnergyTransientFromSteadyState);
  }

  /** Reject post-steady ownership transfer before a converged steady state exists. */
  @Test
  void conservativeHandoffRejectsUninitializedPipe() {
    SystemIdealGas fluid = nitrogen(300.0, 2.0);
    Stream inlet = new Stream("uninitialized-handoff-inlet", fluid);
    TwoFluidPipe pipe = new TwoFluidPipe("uninitialized-handoff", inlet);
    assertThrows(IllegalStateException.class, pipe::prepareConservativeEnergyTransientFromSteadyState);
  }

  /** An unsupported post-steady model fails before installing component or energy ownership. */
  @Test
  void conservativeHandoffRejectsUnsupportedModelWithoutPartialPreparation() {
    Fixture fixture = createPipe("unsupported-handoff", false, 300.0, 2.0, 2);
    double[] pressure = fixture.pipe.getPressureProfile();
    double energy = storedEnergy(fixture.pipe);
    fixture.pipe.setUseMultilayerThermalModel(true);

    assertThrows(IllegalStateException.class, fixture.pipe::prepareConservativeEnergyTransientFromSteadyState);

    assertFalse(fixture.pipe.isUseConservativeEnergyRecovery());
    assertFalse(fixture.pipe.isComponentTransportEnabled());
    assertArrayEquals(pressure, fixture.pipe.getPressureProfile(), 0.0);
    assertEquals(energy, storedEnergy(fixture.pipe), 0.0);
  }

  /**
   * Create the short ideal-nitrogen diagnostic from issue 4595.
   *
   * @param name descriptive fixture name
   * @param conservative whether to recover conservative energy
   * @param temperature initial temperature in K
   * @param pressure initial absolute pressure in bar
   * @param cells finite-volume cell count
   * @return initialized fixture
   */
  private Fixture createPipe(String name, boolean conservative, double temperature, double pressure, int cells) {
    SystemIdealGas fluid = nitrogen(temperature, pressure);
    Stream inlet = new Stream(name + "-inlet", fluid);
    inlet.setFlowRate(0.001, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe(name, inlet);
    pipe.setLength(1.0);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(cells);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableSlugTracking(false);
    pipe.setIncludeMassTransfer(false);
    pipe.setIncludeEnergyEquation(true);
    pipe.setEnableJouleThomson(false);
    pipe.setHeatTransferCoefficient(0.0);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    if (conservative) {
      pipe.setUseConservativeEnergyRecovery(true);
    }
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged(), "Fixture initialization must converge");
    return new Fixture(fluid, inlet, pipe);
  }

  /**
   * Initialize an independent ideal nitrogen EOS at a reported thermodynamic point.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return initialized ideal-gas system
   */
  private SystemIdealGas nitrogen(double temperature, double pressure) {
    SystemIdealGas fluid = new SystemIdealGas(temperature, pressure);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Compare U6 with an EOS reconstruction based only on the published primitive state and phase inventory.
   *
   * @param pipe accepted pipe state
   */
  private void assertPublishedStateCloses(TwoFluidPipe pipe) {
    assertEquals(storedEnergy(pipe), independentIdealGasEnergy(pipe), 1.0e-5,
        "Published temperature and pressure must reconstruct conservative total energy");
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      SystemIdealGas local = nitrogen(section.getTemperature(), section.getPressure() / 1.0e5);
      assertEquals(1.0, section.getGasMassPerLength() / local.getDensity("kg/m3") / section.getArea(), 1.0e-7,
          "The independently evaluated EOS gas must occupy exactly the rigid cell volume");
      assertTrue(section.getTemperature() > 0.0);
      assertEquals(section.getGasMomentumPerLength(), section.getGasMassPerLength() * section.getGasVelocity(), 1.0e-9);
    }
  }

  /**
   * Sum integrated conservative energy over the physical cells.
   *
   * @param pipe initialized pipe
   * @return total internal plus phase kinetic energy in J
   */
  private double storedEnergy(TwoFluidPipe pipe) {
    double total = 0.0;
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      total += section.getEnergyPerLength() * section.getLength();
    }
    return total;
  }

  /**
   * Independently rebuild ideal-gas total energy using the same EOS caloric reference.
   *
   * @param pipe initialized ideal-gas pipe
   * @return independently reconstructed energy in J
   */
  private double independentIdealGasEnergy(TwoFluidPipe pipe) {
    double total = 0.0;
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      SystemIdealGas local = nitrogen(section.getTemperature(), section.getPressure() / 1.0e5);
      double gasMass = section.getGasMassPerLength();
      double kinetic = 0.5 * gasMass * section.getGasVelocity() * section.getGasVelocity();
      total += (gasMass * local.getEnthalpy("J/kg") - section.getPressure() * section.getArea() + kinetic)
          * section.getLength();
    }
    return total;
  }

  /**
   * Calculate the physically mass-weighted mean ideal-gas temperature.
   *
   * @param pipe initialized ideal-gas pipe
   * @return inventory-weighted temperature in K
   */
  private double massWeightedTemperature(TwoFluidPipe pipe) {
    double massTemperature = 0.0;
    double mass = 0.0;
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      double cellMass = section.getGasMassPerLength() * section.getLength();
      massTemperature += cellMass * section.getTemperature();
      mass += cellMass;
    }
    return massTemperature / mass;
  }

  /**
   * Remove initial flow kinetic energy consistently for an exact closed stationary reference.
   *
   * @param pipe initialized pipe
   * @throws Exception when reflection cannot access the accepted physical cells
   */
  private void makeStationary(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    for (TwoFluidSection section : (TwoFluidSection[]) field.get(pipe)) {
      section.setGasVelocity(0.0);
      section.setGasMomentumPerLength(0.0);
      section.updateConservativeVariables();
    }
  }

  /**
   * Initialized ideal-gas fixture.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Fixture {
    /** EOS used to obtain reference heat capacities. */
    private final SystemIdealGas fluid;
    /** Named inlet retained for input enthalpy checks. */
    private final Stream inlet;
    /** Pipe under test. */
    private final TwoFluidPipe pipe;

    /**
     * Retain the initialized model objects.
     *
     * @param fluid initialized ideal nitrogen
     * @param inlet connected inlet stream
     * @param pipe initialized conservative pipe
     */
    private Fixture(SystemIdealGas fluid, Stream inlet, TwoFluidPipe pipe) {
      this.fluid = fluid;
      this.inlet = inlet;
      this.pipe = pipe;
    }
  }
}
