package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidComponentTransport;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemIdealGas;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Bounded qualification of conservative thermal recovery without claiming phase-transition validation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeConservativeQualificationTest {
  /** Identifier shared by completed physical intervals. */
  private static final UUID CALCULATION_ID = UUID.randomUUID();

  /**
   * Compare the same rigid ideal-gas filling event on refined space/time grids against its independent first law.
   *
   * @throws Exception if the stationary reference cells cannot be accessed
   */
  @Test
  void nitrogenFillingHasTheSameAnalyticalLimitOnRefinedMeshesAndTimeSteps() throws Exception {
    double[] temperatures = new double[3];
    double horizon = 0.002;
    for (int level = 0; level < 3; level++) {
      int cells = 2 << level;
      double dt = 1.0e-4 / (1 << level);
      SystemIdealGas fluid = new SystemIdealGas(300.0, 2.0);
      fluid.addComponent("nitrogen", 1.0);
      fluid.setMixingRule("classic");
      TwoFluidPipe pipe = pipe("refined-filling-" + cells, fluid, cells, 0.001);
      makeStationary(pipe);
      pipe.closeOutlet();
      double initialMass = pipe.getTotalMassInventory();
      double gamma = pipe.getInletStream().getFluid().getCp("J/kgK") / pipe.getInletStream().getFluid().getCv("J/kgK");
      double inletMass = 0.0;
      int steps = (int) Math.round(horizon / dt);
      for (int step = 0; step < steps; step++) {
        pipe.runTransient(dt, CALCULATION_ID);
        assertAuditCloses(pipe);
        inletMass += pipe.getLastMassBalanceReport().getInletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL);
      }
      double finalMass = pipe.getTotalMassInventory();
      assertEquals(initialMass + inletMass, finalMass, 1.0e-11);
      assertEquals(horizon, pipe.getSimulationTime(), 1.0e-12);
      double expected = 300.0 * (initialMass + gamma * inletMass) / finalMass;
      temperatures[level] = massWeightedTemperature(pipe);
      assertTrue(temperatures[level] > 300.0);
      assertEquals(expected, temperatures[level], 1.0e-4,
          "The integrated filling first law holds on each mesh; cells need not share a temperature");
    }
    assertEquals(temperatures[0], temperatures[1], 1.0e-4);
    assertEquals(temperatures[1], temperatures[2], 1.0e-4);
  }

  /**
   * Transport a composition front while independently rebuilding each gas/oil/water EOS state from named stocks.
   *
   * @throws Exception if the accepted component inventories cannot be accessed
   */
  @Test
  void richGasOilWaterRetainsIndependentCompositionsAndClosesItsFirstLaw() throws Exception {
    TwoFluidPipe pipe = pipe("three-phase-conservative", richFluid(false), 2, 0.02);
    assertTrue(pipe.getInletStream().getFluid().hasPhaseType("gas"));
    assertTrue(pipe.getInletStream().getFluid().hasPhaseType("oil"));
    assertTrue(pipe.getInletStream().getFluid().hasPhaseType("aqueous"));
    double beforeMethane = pipe.getComponentMassFractionProfile(Phase.GAS, "methane")[0];
    SystemInterface changed = richFluid(true);
    changed.setTotalFlowRate(0.02, "kg/sec");
    pipe.getInletStream().setThermoSystem(changed);
    pipe.getInletStream().run();

    for (int step = 0; step < 5; step++) {
      pipe.runTransient(2.0e-5, CALCULATION_ID);
      assertAuditCloses(pipe);
      assertIndependentThreePhaseState(pipe);
      TwoFluidComponentConservationReport report = pipe.getLastComponentConservationReport();
      assertEquals(0.0, report.getInterphaseLatentHeatEnergyJ(), 1.0e-12);
      assertEquals(0.0, report.getMaximumInterphaseTransferResidualKg(), 1.0e-12);
    }
    double afterMethane = pipe.getComponentMassFractionProfile(Phase.GAS, "methane")[0];
    assertTrue(Math.abs(afterMethane - beforeMethane) > 1.0e-12,
        "The changed feed must actually enter the transported gas composition");
    double[] gasMethane = pipe.getComponentMassFractionProfile(Phase.GAS, "methane");
    double[] oilMethane = pipe.getComponentMassFractionProfile(Phase.OIL, "methane");
    double[] aqueousWater = pipe.getComponentMassFractionProfile(Phase.WATER, "water");
    for (int cell = 0; cell < gasMethane.length; cell++) {
      assertTrue(Math.abs(gasMethane[cell] - oilMethane[cell]) > 0.1,
          "Independent gas and liquid stocks must not be replaced by a common bulk composition");
      assertTrue(aqueousWater[cell] > 0.9);
    }
  }

  /**
   * Direct electrical heating adds exactly its configured energy to a closed adiabatic fluid.
   *
   * @throws Exception if the stationary reference cells cannot be accessed
   */
  @Test
  void closedAdiabaticElectricalHeatingAddsTheConfiguredJoulesOnce() throws Exception {
    SystemIdealGas fluid = new SystemIdealGas(300.0, 2.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    TwoFluidPipe pipe = pipe("closed-deh-conservative", fluid, 2, 0.001);
    makeStationary(pipe);
    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setDirectElectricalHeatingPowerPerMeter(25.0);
    double initialEnergy = storedEnergy(pipe);
    double initialMass = pipe.getTotalMassInventory();
    double suppliedHeat = 0.0;
    for (int step = 0; step < 5; step++) {
      pipe.runTransient(1.0e-3, CALCULATION_ID);
      assertAuditCloses(pipe);
      suppliedHeat += pipe.getLastConservativeEnergyReport().getWallHeatToFluidJ();
    }
    double expectedHeat = 25.0 * 1.0 * 0.005;
    assertEquals(expectedHeat, suppliedHeat, 1.0e-7);
    assertEquals(expectedHeat, storedEnergy(pipe) - initialEnergy, 1.0e-7);
    assertEquals(initialMass, pipe.getTotalMassInventory(), 1.0e-12);
    assertTrue(massWeightedTemperature(pipe) > 300.0);
  }

  /**
   * An unrecoverable conservative energy cannot publish any part of its attempted interval.
   *
   * @throws Exception if the diagnostic cell cannot be accessed
   */
  @Test
  void injectedEnergyOutsideTheEosTemperatureRangeRollsBackEveryPublishedObject() throws Exception {
    SystemIdealGas fluid = new SystemIdealGas(300.0, 2.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    TwoFluidPipe pipe = pipe("rejected-energy-conservative", fluid, 2, 0.001);
    pipe.closeInlet();
    pipe.openOutlet(1.9, "bara");
    pipe.runTransient(1.0e-4, CALCULATION_ID);
    TwoFluidConservativeEnergyReport acceptedReport = pipe.getLastConservativeEnergyReport();
    double acceptedTime = pipe.getSimulationTime();
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    TwoFluidSection[] sections = (TwoFluidSection[]) field.get(pipe);
    sections[0].setEnergyPerLength(-1.0e12);
    byte[] beforeAttempt = SerializationUtils.serialize(pipe);

    assertThrows(IllegalStateException.class, () -> pipe.runTransient(1.0e-4, CALCULATION_ID));

    assertArrayEquals(beforeAttempt, SerializationUtils.serialize(pipe),
        "Failure must preserve cells, named stocks, clocks, connected streams and accepted ledgers");
    assertEquals(acceptedTime, pipe.getSimulationTime(), 0.0);
    assertEquals(acceptedReport.getFinalEnergyJ(), pipe.getLastConservativeEnergyReport().getFinalEnergyJ(), 0.0);
  }

  /**
   * Configure a short conservative pipe with no ambient heat, phase transfer or slug model.
   *
   * @param name fixture identifier
   * @param fluid thermodynamic inlet definition
   * @param cells finite-volume cell count
   * @param massFlow inlet mass rate in kg/s
   * @return converged initialized pipe
   */
  private TwoFluidPipe pipe(String name, SystemInterface fluid, int cells, double massFlow) {
    Stream inlet = new Stream(name + "-feed", fluid);
    inlet.setFlowRate(massFlow, "kg/sec");
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
    pipe.setUseConservativeEnergyRecovery(true);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());
    return pipe;
  }

  /**
   * Create public synthetic three-phase SRK fluid definitions with the same component slate.
   *
   * @param changed whether to make the feed richer in heavy hydrocarbon
   * @return unflashed synthetic fluid
   */
  private SystemInterface richFluid(boolean changed) {
    SystemInterface fluid = new SystemSrkEos(323.15, 60.0);
    fluid.addComponent("methane", changed ? 45.0 : 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", changed ? 27.0 : 12.0);
    fluid.addComponent("water", 25.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Audit all accepted interval ledgers and the independently checked recovery residuals.
   *
   * @param pipe accepted transient pipe
   */
  private void assertAuditCloses(TwoFluidPipe pipe) {
    TwoFluidConservativeEnergyReport energy = pipe.getLastConservativeEnergyReport();
    assertNotNull(energy);
    assertTrue(energy.isWithinTolerance(1.0e-6, 1.0e-7), "First-law residual is " + energy.getResidualJ());
    assertTrue(energy.getMaxRelativeVolumeResidual() < 1.0e-7);
    assertTrue(energy.getMaxRelativeThermodynamicEnergyResidual() < 1.0e-7);
    double[] inletPhaseEnergy = energy.getInletPhaseAdvectedEnergyJ();
    double[] outletPhaseEnergy = energy.getOutletPhaseAdvectedEnergyJ();
    assertEquals(energy.getInletAdvectedEnergyJ(), inletPhaseEnergy[0] + inletPhaseEnergy[1] + inletPhaseEnergy[2],
        1.0e-10);
    assertEquals(energy.getOutletAdvectedEnergyJ(), outletPhaseEnergy[0] + outletPhaseEnergy[1] + outletPhaseEnergy[2],
        1.0e-10);
    TwoFluidComponentConservationReport components = pipe.getLastComponentConservationReport();
    assertTrue(components.isConverged(), components.getMessage());
    for (int component = 0; component < components.getComponentNames().length; component++) {
      double inletPhaseMass = components
          .getInletBoundaryMassKg(TwoFluidComponentConservationReport.Phase.GAS)[component]
          + components.getInletBoundaryMassKg(TwoFluidComponentConservationReport.Phase.OIL)[component]
          + components.getInletBoundaryMassKg(TwoFluidComponentConservationReport.Phase.WATER)[component];
      double outletPhaseMass = components
          .getOutletBoundaryMassKg(TwoFluidComponentConservationReport.Phase.GAS)[component]
          + components.getOutletBoundaryMassKg(TwoFluidComponentConservationReport.Phase.OIL)[component]
          + components.getOutletBoundaryMassKg(TwoFluidComponentConservationReport.Phase.WATER)[component];
      assertEquals(components.getInletBoundaryMassKg()[component], inletPhaseMass, 1.0e-12);
      assertEquals(components.getOutletBoundaryMassKg()[component], outletPhaseMass, 1.0e-12);
    }
    for (TwoFluidMassBalanceReport.Phase phase : TwoFluidMassBalanceReport.Phase.values()) {
      assertEquals(0.0, pipe.getLastMassBalanceReport().getResidualKg(phase), 1.0e-10);
    }
  }

  /**
   * Rebuild SRK phases independently from transported component masses without an equilibrium flash.
   *
   * @param pipe accepted three-phase pipe
   * @throws Exception if the named component inventories cannot be accessed
   */
  private void assertIndependentThreePhaseState(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("componentTransport");
    field.setAccessible(true);
    TwoFluidComponentTransport transport = (TwoFluidComponentTransport) field.get(pipe);
    double[][][] inventories = transport.getInventoryKg();
    String[] names = transport.getComponentNames();
    SystemInterface reference = pipe.getInletStream().getFluid();
    PhaseType[] phaseTypes = {PhaseType.GAS, PhaseType.OIL, PhaseType.AQUEOUS};
    TwoFluidSection[] sections = pipe.getSectionSnapshots();
    for (int cell = 0; cell < sections.length; cell++) {
      TwoFluidSection section = sections[cell];
      double[] state = section.getStateVector();
      double volume = 0.0;
      double energy = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        assertTrue(state[phase] > 0.0, "This fixed-phase qualification requires all three phases to remain present");
        SystemInterface forced = new SystemSrkEos(section.getTemperature(), section.getPressure() / 1.0e5);
        double phaseMass = 0.0;
        for (int component = 0; component < names.length; component++) {
          double componentMass = inventories[cell][phase][component];
          assertTrue(componentMass >= 0.0);
          phaseMass += componentMass;
          double molarMass = reference.getPhase(0).getComponent(names[component]).getMolarMass();
          forced.addComponent(names[component], componentMass / molarMass);
        }
        assertEquals(state[phase] * section.getLength(), phaseMass, 1.0e-10);
        forced.setMixingRule("classic");
        forced.setNumberOfPhases(1);
        forced.setMaxNumberOfPhases(1);
        forced.setForcePhaseTypes(true);
        forced.init(0);
        forced.setNumberOfPhases(1);
        forced.setBeta(0, 1.0);
        forced.setPhaseType(0, phaseTypes[phase]);
        forced.initProperties();
        double density = forced.getPhase(0).getDensity("kg/m3");
        double enthalpy = forced.getPhase(0).getEnthalpy("J/kg");
        volume += phaseMass / density;
        energy += phaseMass * (enthalpy - section.getPressure() / density)
            + 0.5 * state[phase + 3] * state[phase + 3] / state[phase] * section.getLength();
      }
      assertEquals(section.getArea() * section.getLength(), volume, 1.0e-9);
      assertEquals(state[6] * section.getLength(), energy, 1.0e-4);
    }
  }

  /**
   * Remove initialization kinetic energy consistently for an exact stationary reference.
   *
   * @param pipe initialized ideal-gas pipe
   * @throws Exception if the accepted section array cannot be accessed
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
   * Sum integrated conservative total energy.
   *
   * @param pipe initialized pipe
   * @return total energy in J
   */
  private double storedEnergy(TwoFluidPipe pipe) {
    double energy = 0.0;
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      energy += section.getEnergyPerLength() * section.getLength();
    }
    return energy;
  }

  /**
   * Evaluate the mass-weighted ideal-gas temperature for comparison with a global first-law limit.
   *
   * @param pipe initialized ideal-gas pipe
   * @return mass-weighted temperature in K
   */
  private double massWeightedTemperature(TwoFluidPipe pipe) {
    double mass = 0.0;
    double weighted = 0.0;
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      double cellMass = section.getGasMassPerLength() * section.getLength();
      mass += cellMass;
      weighted += cellMass * section.getTemperature();
    }
    return weighted / mass;
  }
}
