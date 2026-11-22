package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidComponentTransport;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Bounded closed thermal-path qualification of conservative phase appearance and evaporation.
 *
 * <p>
 * The uniform methane/n-butane test uses aggressive numerical wall and heating values to shorten execution. They are
 * not equipment design values. Finite relaxation retains positive trace inventories, so this test qualifies actual
 * condensation and evaporation without asserting certified rates or deleting mass at a disappearance threshold.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeConservativeTransitionTest {
  /** Identity shared by accepted intervals. */
  private static final UUID ID = UUID.randomUUID();
  /** Duration of each cooling and heating path in seconds. */
  private static final double DURATION = 0.002;

  /**
   * Compare conservative cooling/reheat on refined meshes with an unforced adiabatic control.
   *
   * @throws Exception if dew-point calculation or diagnostic inventory access fails
   */
  @Test
  void closedRichGasCondensesAndEvaporatesOnRefinedThermalPaths() throws Exception {
    SystemInterface template = fluid();
    SystemInterface dew = template.clone();
    new ThermodynamicOperations(dew).dewPointTemperatureFlash();
    double dewTemperature = dew.getTemperature();
    double[] coarse = transition(template, dewTemperature, 2, 2.0e-5);
    double[] refined = transition(template, dewTemperature, 4, 1.0e-5);
    for (double[] result : new double[][] {coarse, refined}) {
      assertTrue(result[0] < dewTemperature);
      assertTrue(result[1] > dewTemperature + 5.0);
      assertTrue(result[2] > 1.0e-6, "Cooling must actually create oil");
      assertTrue(result[3] > 0.0, "Reheat must actually evaporate oil");
      assertTrue(result[5] < result[4], "Completed reheat must reduce the cooled oil inventory");
      assertTrue(result[5] > 0.0, "Finite relaxation tails remain conserved rather than deleted");
    }
    assertEquals(coarse[0], refined[0], 0.05);
    assertEquals(coarse[1], refined[1], 0.05);
    assertTrue(Math.abs(coarse[2] - refined[2]) / refined[2] < 0.05);
    assertTrue(Math.abs(coarse[5] - refined[5]) / refined[5] < 0.05);
    TwoFluidPipe control = initializedPipe(template, dewTemperature + 0.02, 2);
    control.runTransient(2.0 * DURATION, ID);
    audit(control);
    assertEquals(dewTemperature + 0.02, meanTemperature(control), 1.0e-6);
    assertEquals(0.0, control.getLastMassBalanceReport().getFinalMassKg(TwoFluidMassBalanceReport.Phase.OIL), 1.0e-12,
        "The adiabatic control must remain gas-only");
  }

  /**
   * The chosen energy owner is immutable once the pipe has accepted a transient state.
   *
   * @throws Exception if stationary fixture initialization fails
   */
  @Test
  void changingTheInitializedEnergyOwnerRejectsWithoutLosingItsReport() throws Exception {
    TwoFluidPipe pipe = initializedPipe(fluid(), 340.0, 2);
    pipe.runTransient(1.0e-5, ID);
    TwoFluidConservativeEnergyReport report = pipe.getLastConservativeEnergyReport();
    assertThrows(IllegalStateException.class, () -> pipe.setUseConservativeEnergyRecovery(false));
    assertSame(report, pipe.getLastConservativeEnergyReport());
    assertTrue(pipe.isUseConservativeEnergyRecovery());
  }

  /**
   * Thermal risk flags must also follow conservative adiabatic blowdown cooling.
   *
   * @throws Exception if stationary fixture initialization fails
   */
  @Test
  void adiabaticCoolingUpdatesTheHydrateRiskFlags() throws Exception {
    SystemInterface gas = new SystemSrkEos(300.0, 2.0);
    gas.addComponent("nitrogen", 1.0);
    gas.setMixingRule("classic");
    TwoFluidPipe pipe = initializedPipe(gas, 300.0, 2);
    pipe.setHydrateFormationTemperature(299.99, "K");
    assertFalse(pipe.hasHydrateRisk());
    pipe.openOutlet(1.9, "bara");
    for (int step = 0; step < 20; step++) {
      pipe.runTransient(1.0e-4, ID);
    }
    assertTrue(meanTemperature(pipe) < 299.99);
    assertTrue(pipe.hasHydrateRisk());
    assertTrue(pipe.getHydrateRiskSectionCount() > 0);
  }

  /**
   * Execute a closed uniform cooling/reheat path, auditing every accepted state.
   *
   * @param template synthetic composition
   * @param dewTemperature dew temperature in K
   * @param cells physical cell count
   * @param dt external interval in seconds
   * @return cooled/heated temperatures, condensed/evaporated mass and cooled/final oil inventory
   * @throws Exception if diagnostic inventory access fails
   */
  private double[] transition(SystemInterface template, double dewTemperature, int cells, double dt) throws Exception {
    TwoFluidPipe pipe = initializedPipe(template, dewTemperature + 0.02, cells);
    pipe.setWallProperties(1.0e-5, 100.0, 100.0);
    pipe.setHeatTransferCoefficient(1.0e5);
    pipe.setSurfaceTemperature(dewTemperature - 10.0, "K");
    double[] result = new double[6];
    int steps = (int) Math.round(DURATION / dt);
    for (int step = 0; step < steps; step++) {
      pipe.runTransient(dt, ID);
      audit(pipe);
      result[2] += Math.max(0.0, pipe.getLastMassBalanceReport().getSourceMassKg(TwoFluidMassBalanceReport.Phase.OIL));
    }
    result[0] = meanTemperature(pipe);
    result[4] = pipe.getLastMassBalanceReport().getFinalMassKg(TwoFluidMassBalanceReport.Phase.OIL);
    pipe.setHeatTransferCoefficient(0.0);
    pipe.setDirectElectricalHeatingPowerPerMeter(2.0e6);
    for (int step = 0; step < steps; step++) {
      pipe.runTransient(dt, ID);
      audit(pipe);
      result[3] += Math.max(0.0, -pipe.getLastMassBalanceReport().getSourceMassKg(TwoFluidMassBalanceReport.Phase.OIL));
    }
    result[1] = meanTemperature(pipe);
    result[5] = pipe.getLastMassBalanceReport().getFinalMassKg(TwoFluidMassBalanceReport.Phase.OIL);
    assertEquals(2.0 * DURATION, pipe.getSimulationTime(), 1.0e-12);
    return result;
  }

  /**
   * Construct a stationary closed gas-only conservative pipe.
   *
   * @param template initial composition and pressure
   * @param temperature initial temperature in K
   * @param cells physical cell count
   * @return initialized stationary pipe
   * @throws Exception if accepted cells cannot be accessed
   */
  private TwoFluidPipe initializedPipe(SystemInterface template, double temperature, int cells) throws Exception {
    SystemInterface gas = template.clone();
    gas.setTemperature(temperature);
    Stream inlet = new Stream("transition-feed", gas);
    inlet.setFlowRate(1.0e-10, "kg/sec");
    inlet.run();
    assertEquals(1, inlet.getFluid().getNumberOfPhases());
    assertTrue(inlet.getFluid().hasPhaseType("gas"));
    TwoFluidPipe pipe = new TwoFluidPipe("conservative-transition-" + cells, inlet);
    pipe.setLength(1.0);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(cells);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableSlugTracking(false);
    pipe.setIncludeMassTransfer(true);
    pipe.setMassTransferRelaxationTime(0.01);
    pipe.setIncludeEnergyEquation(true);
    pipe.setEnableJouleThomson(false);
    pipe.setHeatTransferCoefficient(0.0);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.setUseConservativeEnergyRecovery(true);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    for (TwoFluidSection section : (TwoFluidSection[]) field.get(pipe)) {
      section.setGasVelocity(0.0);
      section.setGasMomentumPerLength(0.0);
      section.updateConservativeVariables();
    }
    pipe.closeInlet();
    pipe.closeOutlet();
    return pipe;
  }

  /**
   * Rebuild each actual transported phase with an independent SRK evaluation, without an equilibrium flash.
   *
   * @param pipe accepted closed pipe
   * @throws Exception if component stock access fails
   */
  private void audit(TwoFluidPipe pipe) throws Exception {
    TwoFluidConservativeEnergyReport energy = pipe.getLastConservativeEnergyReport();
    assertTrue(energy.isWithinTolerance(1.0e-6, 1.0e-7));
    assertTrue(energy.getMaxRelativeVolumeResidual() < 1.0e-7);
    assertTrue(energy.getMaxRelativeThermodynamicEnergyResidual() < 1.0e-7);
    assertEquals(0.0, energy.getNetAdvectedEnergyJ(), 1.0e-10);
    TwoFluidComponentConservationReport components = pipe.getLastComponentConservationReport();
    assertTrue(components.isConverged(), components.getMessage());
    TwoFluidMassBalanceReport mass = pipe.getLastMassBalanceReport();
    assertEquals(0.0, mass.getInletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL), 0.0);
    assertEquals(0.0, mass.getOutletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL), 0.0);
    for (TwoFluidMassBalanceReport.Phase phase : TwoFluidMassBalanceReport.Phase.values()) {
      assertEquals(0.0, mass.getResidualKg(phase), 1.0e-10);
    }
    Field field = TwoFluidPipe.class.getDeclaredField("componentTransport");
    field.setAccessible(true);
    TwoFluidComponentTransport transport = (TwoFluidComponentTransport) field.get(pipe);
    double[][][] inventories = transport.getInventoryKg();
    String[] names = transport.getComponentNames();
    SystemInterface reference = pipe.getInletStream().getFluid();
    PhaseType[] types = {PhaseType.GAS, PhaseType.OIL, PhaseType.AQUEOUS};
    TwoFluidSection[] sections = pipe.getSectionSnapshots();
    for (int cell = 0; cell < sections.length; cell++) {
      TwoFluidSection section = sections[cell];
      double[] state = section.getStateVector();
      double volume = 0.0;
      double totalEnergy = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        double phaseMass = 0.0;
        for (double value : inventories[cell][phase]) {
          assertTrue(value >= 0.0);
          phaseMass += value;
        }
        assertEquals(state[phase] * section.getLength(), phaseMass, 1.0e-10);
        if (phaseMass == 0.0) {
          continue;
        }
        SystemInterface forced = new SystemSrkEos(section.getTemperature(), section.getPressure() / 1.0e5);
        for (int component = 0; component < names.length; component++) {
          double molarMass = reference.getPhase(0).getComponent(names[component]).getMolarMass();
          forced.addComponent(names[component], inventories[cell][phase][component] / molarMass);
        }
        forced.setMixingRule("classic");
        forced.setNumberOfPhases(1);
        forced.setMaxNumberOfPhases(1);
        forced.setForcePhaseTypes(true);
        forced.init(0);
        forced.setNumberOfPhases(1);
        forced.setBeta(0, 1.0);
        forced.setPhaseType(0, types[phase]);
        forced.initProperties();
        double density = forced.getPhase(0).getDensity("kg/m3");
        volume += phaseMass / density;
        totalEnergy += phaseMass * (forced.getPhase(0).getEnthalpy("J/kg") - section.getPressure() / density)
            + 0.5 * state[phase + 3] * state[phase + 3] / state[phase] * section.getLength();
      }
      assertEquals(section.getArea() * section.getLength(), volume, 1.0e-9);
      assertEquals(state[6] * section.getLength(), totalEnergy, 1.0e-4);
    }
  }

  /**
   * Construct the public synthetic binary hydrocarbon fluid.
   *
   * @return unflashed SRK composition at 20 bara
   */
  private SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 20.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-butane", 0.2);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Average the uniform physical cell temperatures, allowing roundoff differences.
   *
   * @param pipe initialized pipe
   * @return mean temperature in K
   */
  private double meanTemperature(TwoFluidPipe pipe) {
    double sum = 0.0;
    for (double temperature : pipe.getTemperatureProfile()) {
      sum += temperature;
    }
    return sum / pipe.getTemperatureProfile().length;
  }
}
