package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Consumer checks of CPA candidate isolation in equilibrium release inventories.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ReleaseInventoryCpaIsolationTest extends neqsim.NeqSimTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);

  /** Checks accepted CPA gas and aqueous withdrawal at nearby temperatures. */
  @Test
  void acceptedWithdrawalConservesCpaInventory() {
    for (double temperature : new double[] {298.15, 308.15}) {
      for (PhaseType phase : new PhaseType[] {PhaseType.GAS, PhaseType.AQUEOUS}) {
        SystemInterface source = fluid(temperature);
        double[] original = signature(source);
        ReleaseInventory vessel = new ReleaseInventory("cpa", source, 0.02, 0.001, 0.62, 101325.0,
            new ControlledRelease(), 0.02, phase);
        vessel.runTransient(0.04, UUID.randomUUID());
        assertArrayEquals(original, signature(source), 0.0);
        ReleaseInventory.Balance balance = vessel.getBalance();
        assertEquals(0.00004, balance.getReleasedMassKg(), 1e-14);
        for (String name : balance.getInitialComponentMassKg().keySet()) {
          double initial = balance.getInitialComponentMassKg().get(name);
          assertEquals(initial,
              balance.getRemainingComponentMassKg().get(name) + balance.getReleasedComponentMassKg().get(name),
              initial * 1e-10);
        }
        assertEquals(balance.getInitialEnergyJ(), balance.getInternalEnergyJ() + balance.getReleasedEnergyJ(),
            Math.abs(balance.getInitialEnergyJ()) * 1e-7);
        assertEquals(0.02, vessel.getFluid().getVolume("m3"), 2e-9);
        double[] committed = signature(vessel.getFluid());
        SystemInterface exported = vessel.getFluid();
        exported.setTemperature(320.0);
        exported.setPressure(40.0);
        new ThermodynamicOperations(exported).TPflash();
        exported.init(3);
        assertArrayEquals(committed, signature(vessel.getFluid()), 0.0);
        assertArrayEquals(original, signature(source), 0.0);
      }
    }
  }

  /** Checks rollback after a completed volume/energy trial and repeatability after rejection. */
  @Test
  void rejectedSecondSubstepLeavesCpaStateUnchanged() {
    for (double temperature : new double[] {298.15, 308.15}) {
      for (PhaseType phase : new PhaseType[] {PhaseType.GAS, PhaseType.AQUEOUS}) {
        SystemInterface source = fluid(temperature);
        double[] original = signature(source);
        ControlledRelease boundary = new ControlledRelease();
        ReleaseInventory vessel = new ReleaseInventory("cpa", source, 0.02, 0.001, 0.62, 101325.0, boundary, 0.02,
            phase);
        double[] before = signature(vessel.getFluid());
        UUID beforeId = vessel.getCalculationIdentifier();
        boundary.rejectAtCall = 2;
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> vessel.runTransient(0.04, UUID.randomUUID()));
        assertEquals("CONTROLLED_SECOND_SUBSTEP_REJECTION", failure.getMessage());
        assertEquals(2, boundary.calls);
        assertEquals(0.0, vessel.getTime(), 0.0);
        assertEquals(beforeId, vessel.getCalculationIdentifier());
        assertEquals(0.0, vessel.getBalance().getReleasedMassKg(), 0.0);
        assertEquals(0.0, vessel.getBalance().getReleasedEnergyJ(), 0.0);
        assertEquals(0, vessel.getLastVolumeEnergySolves());
        assertArrayEquals(before, signature(vessel.getFluid()), 0.0);
        assertArrayEquals(original, signature(source), 0.0);
        boundary.rejectAtCall = -1;
        vessel.runTransient(0.04, UUID.randomUUID());
        ReleaseInventory control = new ReleaseInventory("control", fluid(temperature), 0.02, 0.001, 0.62, 101325.0,
            new ControlledRelease(), 0.02, phase);
        control.runTransient(0.04, UUID.randomUUID());
        assertArrayEquals(signature(control.getFluid()), signature(vessel.getFluid()), 0.0);
      }
    }
  }

  /**
   * Exercises successful native transient sessions through both public process containers.
   *
   * @throws Exception if a schema fixture cannot be retained
   */
  @Test
  void nativeSessionsRetainCpaPhaseAndSchemaEvidence() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      for (PhaseType phase : new PhaseType[] {PhaseType.GAS, PhaseType.AQUEOUS}) {
        ReleaseInventory vessel = new ReleaseInventory("cpa", fluid(298.15), 0.02, 0.001, 0.62, 101325.0,
            new ControlledRelease(), 0.02, phase);
        ProcessSystem process = new ProcessSystem();
        process.add(vessel);
        SourceTermSession session;
        if (useModel) {
          ProcessModel model = new ProcessModel();
          model.add("area", process);
          session = new SourceTermSession("cpa-inventory", model, CLOCK);
          session.addInventorySource("opening", "area", "cpa");
        } else {
          session = new SourceTermSession("cpa-inventory", process, CLOCK);
          session.addInventorySource("opening", "cpa");
        }
        assertEquals(SourceTermFrame.Status.VALID, session.runSteadyState().get(0).getStatus());
        SourceTermFrame frame = session.step(0.04).get(0);
        assertEquals(SourceTermFrame.Status.VALID, frame.getStatus(), frame.toJson());
        SourceTermFrame.verifyEnvelope(frame.toJson());
        assertEquals(0.00004, vessel.getBalance().getReleasedMassKg(), 1e-14);
        assertEquals(0.04, vessel.getTime(), 0.0);
        assertEquals(phase, vessel.getWithdrawalPhaseType());
        write("source-term-contract-fixtures", "cpa-inventory-" + phase + "-" + useModel + ".json", frame.toJson());
      }
    }
  }

  /**
   * Retains nearby-state refinement and conservation receipts for the controlled flux.
   *
   * @throws Exception if the numerical receipt cannot be written
   */
  @Test
  void cpaWithdrawalRefinesWithoutChangingFluxOrConservationGates() throws Exception {
    StringBuilder csv = new StringBuilder("initial_temperature_K,withdrawal_phase,max_substep_s,time_s,"
        + "pressure_Pa,temperature_K,released_mass_kg,energy_residual_J,volume_residual_m3\n");
    for (double temperature : new double[] {298.15, 308.15}) {
      for (PhaseType phase : new PhaseType[] {PhaseType.GAS, PhaseType.AQUEOUS}) {
        double[] pressures = new double[3];
        for (int refinement = 0; refinement < 3; refinement++) {
          double step = 0.4 / Math.pow(2.0, refinement);
          ReleaseInventory vessel = new ReleaseInventory("cpa", fluid(temperature), 0.02, 0.001, 0.62, 101325.0,
              new ControlledRelease(), step, phase);
          vessel.runTransient(2.0, UUID.randomUUID());
          ReleaseInventory.Balance b = vessel.getBalance();
          SystemInterface remaining = vessel.getFluid();
          pressures[refinement] = remaining.getPressure() * 1e5;
          assertEquals(0.002, b.getReleasedMassKg(), 1e-14);
          for (String name : b.getInitialComponentMassKg().keySet()) {
            double initial = b.getInitialComponentMassKg().get(name);
            assertEquals(initial, b.getRemainingComponentMassKg().get(name) + b.getReleasedComponentMassKg().get(name),
                initial * 1e-10);
          }
          double energyResidual = b.getInternalEnergyJ() + b.getReleasedEnergyJ() - b.getInitialEnergyJ();
          double volumeResidual = remaining.getVolume("m3") - 0.02;
          assertEquals(0.0, energyResidual, Math.abs(b.getInitialEnergyJ()) * 1e-7);
          assertEquals(0.0, volumeResidual, 2e-9);
          csv.append(temperature).append(',').append(phase).append(',').append(step).append(",2.0,")
              .append(pressures[refinement]).append(',').append(remaining.getTemperature()).append(',')
              .append(b.getReleasedMassKg()).append(',').append(energyResidual).append(',').append(volumeResidual)
              .append('\n');
        }
        write("source-term-benchmarks", "cpa-inventory-refinement.csv", csv.toString());
        double coarse = Math.abs(pressures[1] - pressures[0]);
        double fine = Math.abs(pressures[2] - pressures[1]);
        assertTrue(coarse > 0.0 && fine < coarse * 0.75,
            "CPA pressure refinement: T=" + temperature + ", phase=" + phase + ", coarse=" + coarse + ", fine=" + fine);
      }
    }
    write("source-term-benchmarks", "cpa-inventory-refinement.csv", csv.toString());
  }

  /**
   * Retains actual Java results in directories collected by the source-term workflow.
   *
   * @param directory target subdirectory
   * @param name fixture filename
   * @param content serialized result
   * @throws Exception if output cannot be written
   */
  private static void write(String directory, String name, String content) throws Exception {
    Path parent = Paths.get("target", directory);
    Files.createDirectories(parent);
    Files.write(parent.resolve(name), content.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Creates an initialized methane/water CPA feed with active association sites.
   *
   * @param temperature temperature in K
   * @return gas/aqueous equilibrium feed at 60 bara
   */
  private static SystemInterface fluid(double temperature) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temperature, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    return fluid;
  }

  /**
   * Captures already initialized public state without refreshing caches.
   *
   * @param fluid state to inspect
   * @return scalar state and caloric derivative signature
   */
  private static double[] signature(SystemInterface fluid) {
    List<Double> values = new ArrayList<Double>();
    for (double value : new double[] {fluid.getTemperature(), fluid.getPressure(), fluid.getTotalNumberOfMoles(),
        fluid.getVolume("m3"), fluid.getInternalEnergy("J"), fluid.getEnthalpy("J"), fluid.getCp(),
        fluid.getNumberOfPhases()}) {
      values.add(value);
    }
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      values.add(fluid.getPhase(phase).getBeta());
      values.add(fluid.getPhase(phase).getCp());
      values.add((double) fluid.getPhase(phase).getType().ordinal());
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        values.add(fluid.getPhase(phase).getComponent(component).getx());
        values.add(fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase());
        values.add(fluid.getPhase(phase).getComponent(component).getFugacityCoefficient());
        values.add(fluid.getPhase(phase).getComponent(component).getdfugdt());
        values.add(fluid.getPhase(phase).getComponent(component).getdfugdp());
      }
    }
    double[] result = new double[values.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = values.get(i);
    }
    return result;
  }

  /**
   * Fixed boundary flux isolates inventory bookkeeping from discharge-model qualification.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private static final class ControlledRelease implements ReleaseFlowModel {
    private static final long serialVersionUID = 1L;
    private int calls;
    private int rejectAtCall = -1;

    /** @return test-only model identity */
    @Override
    public String getModelId() {
      return "test-cpa-inventory-flux";
    }

    /**
     * Prescribes a 0.001 kg/s boundary with unchanged stagnation enthalpy.
     *
     * @param request selected phase boundary
     * @return controlled conservative boundary result
     */
    @Override
    public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
      if (++calls == rejectAtCall) {
        throw new IllegalStateException("CONTROLLED_SECOND_SUBSTEP_REJECTION");
      }
      SystemInterface fluid = request.getFluid();
      fluid.init(3);
      ReleaseState state = ReleaseState.fromFluid(fluid, 0.0);
      Map<ReleaseFlowResult.Station, ReleaseState> stations = new EnumMap<ReleaseFlowResult.Station, ReleaseState>(
          ReleaseFlowResult.Station.class);
      for (ReleaseFlowResult.Station station : ReleaseFlowResult.Station.values()) {
        stations.put(station, state);
      }
      return ReleaseFlowResult.success(this, 0.001, false, stations,
          Collections.<ReleaseFlowResult.Diagnostic>emptyList(), null, false);
    }
  }
}
