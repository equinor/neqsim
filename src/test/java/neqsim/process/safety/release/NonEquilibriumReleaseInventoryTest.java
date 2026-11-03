package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.DynamicCapability;
import neqsim.process.dynamics.DynamicCapabilityReport;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Conservation, lifecycle, refinement and process-container tests for finite-rate phase inventories. */
class NonEquilibriumReleaseInventoryTest extends neqsim.NeqSimTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);
  private static final double VOLUME_M3 = 0.02;
  private static final double VESSEL_DIAMETER_M = 0.20;
  private static final double VESSEL_LENGTH_M = VOLUME_M3 / (Math.PI * VESSEL_DIAMETER_M * VESSEL_DIAMETER_M / 4.0);

  /**
   * Creates an equilibrium gas/condensate starting point.
   *
   * @return initialized SRK two-phase fluid
   */
  private static SystemInterface twoPhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(250.0, 10.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.OIL) || fluid.hasPhaseType(PhaseType.LIQUID));
    return fluid;
  }

  /**
   * Creates an upper gas-opening geometry with a live interface.
   *
   * @return vertical cylindrical geometry
   */
  private static ReleaseInventoryPhaseGeometry geometry() {
    return new ReleaseInventoryPhaseGeometry(ReleaseInventoryPhaseGeometry.Orientation.VERTICAL, VESSEL_DIAMETER_M,
        VESSEL_LENGTH_M, 0.95 * VESSEL_LENGTH_M, 1.0e-4);
  }

  /**
   * Creates prescribed component-film coefficients.
   *
   * @param coefficientMS common component coefficient in m/s
   * @return immutable transfer closure
   */
  private static ReleaseInventoryInterphaseTransfer transfer(double coefficientMS) {
    Map<String, Double> coefficients = new TreeMap<String, Double>();
    coefficients.put("methane", coefficientMS);
    coefficients.put("n-butane", coefficientMS);
    return new ReleaseInventoryInterphaseTransfer(coefficients, 0.25, "synthetic-film-coefficients:v1");
  }

  /**
   * Creates a deterministic non-screening flow model for inventory integration tests.
   *
   * @param massFlowRateKgS fixed release rate in kg/s
   * @return test-only release model
   */
  private static ReleaseFlowModel fixedRelease(final double massFlowRateKgS) {
    return new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public String getModelId() {
        return "test-fixed-non-equilibrium-release";
      }

      /** {@inheritDoc} */
      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        SystemInterface fluid = request.getFluid();
        fluid.init(3);
        ReleaseState state = ReleaseState.fromFluid(fluid, 0.0);
        Map<ReleaseFlowResult.Station, ReleaseState> stations = new EnumMap<ReleaseFlowResult.Station, ReleaseState>(
            ReleaseFlowResult.Station.class);
        stations.put(ReleaseFlowResult.Station.UPSTREAM_STAGNATION, state);
        stations.put(ReleaseFlowResult.Station.THROAT_CRITICAL, state);
        stations.put(ReleaseFlowResult.Station.ORIFICE_EXIT, state);
        stations.put(ReleaseFlowResult.Station.AMBIENT_EXPANDED, state);
        return ReleaseFlowResult.success(this, massFlowRateKgS, false, stations,
            Collections.singletonList(new ReleaseFlowResult.Diagnostic("CONTROLLED_TEST_MODEL",
                "Fixed release isolates retained-phase conservation and refinement")),
            null, false);
      }
    };
  }

  /**
   * Creates the test inventory.
   *
   * @param maxSubstepS explicit maximum substep in s
   * @param coefficientMS prescribed component-film coefficient in m/s
   * @return configured inventory
   */
  private static NonEquilibriumReleaseInventory inventory(double maxSubstepS, double coefficientMS) {
    return new NonEquilibriumReleaseInventory("finite-rate-inventory", twoPhaseFluid(), VOLUME_M3, 0.005, 0.7, 100000.0,
        fixedRelease(2.0e-4), maxSubstepS, PhaseType.GAS, geometry(), transfer(coefficientMS));
  }

  /**
   * Verifies complete component, mass, volume and energy accounting.
   *
   * @param inventory tested inventory
   */
  private static void balanced(NonEquilibriumReleaseInventory inventory) {
    NonEquilibriumReleaseInventory.Balance balance = inventory.getBalance();
    double released = 0.0;
    for (String name : balance.getInitialComponentMassKg().keySet()) {
      double initial = balance.getInitialComponentMassKg().get(name);
      assertEquals(initial,
          balance.getRemainingComponentMassKg().get(name) + balance.getReleasedComponentMassKg().get(name),
          Math.max(1.0e-12, initial * 1.0e-8));
      assertEquals(balance.getRemainingComponentMassKg().get(name),
          balance.getGasComponentMassKg().get(name) + balance.getLiquidComponentMassKg().get(name),
          Math.max(1.0e-12, initial * 1.0e-10));
      released += balance.getReleasedComponentMassKg().get(name);
    }
    assertEquals(released, balance.getReleasedMassKg(), 1.0e-12);
    assertEquals(balance.getInitialEnergyJ(),
        balance.getInternalEnergyJ() + balance.getReleasedEnergyJ() - balance.getHeatTransferEnergyJ(),
        Math.max(1.0, Math.abs(balance.getInitialEnergyJ())) * 1.0e-7);
    assertEquals(balance.getVolumeM3(),
        inventory.getGasPhase().getVolume("m3") + inventory.getLiquidPhase().getVolume("m3"),
        balance.getVolumeM3() * 1.0e-8);
  }

  @Test
  void transferConfigurationIsImmutableAndFailClosed() {
    Map<String, Double> coefficients = new TreeMap<String, Double>();
    coefficients.put("methane", 1.0e-3);
    ReleaseInventoryInterphaseTransfer closure = new ReleaseInventoryInterphaseTransfer(coefficients, 0.25,
        "public-synthetic-input:v1");
    coefficients.put("methane", 99.0);
    assertEquals(1.0e-3, closure.getCoefficientsMS().get("methane"), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> closure.getCoefficientsMS().put("n-butane", 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> new ReleaseInventoryInterphaseTransfer(Collections.singletonMap("methane", -1.0), 0.25, "source"));
    assertThrows(IllegalArgumentException.class,
        () -> new ReleaseInventoryInterphaseTransfer(Collections.singletonMap("methane", 1.0), 0.75, "source"));
    assertThrows(IllegalArgumentException.class,
        () -> new ReleaseInventoryInterphaseTransfer(Collections.singletonMap("methane", 1.0), 0.25, " "));
    ReleaseInventoryInterphaseTransfer unknownComponent = new ReleaseInventoryInterphaseTransfer(
        Collections.singletonMap("component-typo", 1.0e-3), 0.25, "synthetic-typo:v1");
    assertThrows(IllegalArgumentException.class,
        () -> new NonEquilibriumReleaseInventory("unknown-component", twoPhaseFluid(), VOLUME_M3, 0.005, 0.7, 100000.0,
            fixedRelease(2.0e-4), 0.02, PhaseType.GAS, geometry(), unknownComponent));
  }

  @Test
  void transferMovesBothDirectionsAndLimitsEachDonor() {
    NonEquilibriumReleaseInventory vessel = inventory(0.02, 1.0e-3);
    SystemInterface gas = vessel.getGasPhase();
    SystemInterface liquid = vessel.getLiquidPhase();
    gas.setMolarComposition(new double[] {0.99, 0.01});
    liquid.setMolarComposition(new double[] {0.01, 0.99});
    gas.initProperties();
    liquid.initProperties();
    Map<String, Double> coefficients = new TreeMap<String, Double>();
    coefficients.put("methane", 1.0e6);
    coefficients.put("n-butane", 1.0e6);
    ReleaseInventoryInterphaseTransfer closure = new ReleaseInventoryInterphaseTransfer(coefficients, 0.25,
        "donor-limit-control:v1");
    NonEquilibriumReleaseInventory.Balance balance = vessel.getBalance();

    ReleaseInventoryInterphaseTransfer.Step step = closure.calculate(gas, liquid, balance.getGasComponentMassKg(),
        balance.getLiquidComponentMassKg(), 1.0, 1.0);

    assertTrue(step.getDonorLimitedComponents() > 0);
    for (Map.Entry<String, Double> entry : step.getGasDirectedComponentMassKg().entrySet()) {
      double donor = entry.getValue() >= 0.0 ? balance.getLiquidComponentMassKg().get(entry.getKey())
          : balance.getGasComponentMassKg().get(entry.getKey());
      assertTrue(Math.abs(entry.getValue()) <= 0.25 * donor + 1.0e-15);
    }
  }

  @Test
  void releaseRetainsPhaseComponentsAndClosesLatentEnergyEffect() {
    NonEquilibriumReleaseInventory vessel = inventory(0.02, 2.0e-3);
    Map<String, Double> initialGas = vessel.getBalance().getGasComponentMassKg();

    vessel.runTransient(0.20, UUID.randomUUID());

    NonEquilibriumReleaseInventory.Balance balance = vessel.getBalance();
    assertEquals(4.0e-5, balance.getReleasedMassKg(), 1.0e-13);
    assertTrue(balance.getInterphaseTransferredMassKg() > 0.0);
    assertNotEquals(initialGas, balance.getGasComponentMassKg());
    assertTrue(Double.isFinite(balance.getInterphaseLatentEnergyJ()));
    assertEquals("PRESCRIBED_FUGACITY_FILM_V1", vessel.getReleaseProvenance().get("inventoryInterphaseTransferModel"));
    assertEquals("COMMON_PHASE_TEMPERATURE", vessel.getReleaseProvenance().get("inventoryThermalBoundary"));
    assertEquals("PHASE_DISAPPEARANCE_FAILS_CLOSED", vessel.getReleaseProvenance().get("inventoryPhaseLifecycle"));
    balanced(vessel);
  }

  @Test
  void bothProcessContainersEmitDeterministicFiniteRateFrames() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      NonEquilibriumReleaseInventory vessel = inventory(0.02, 1.0e-3);
      ProcessSystem process = new ProcessSystem();
      process.add(vessel);
      assertEquals(DynamicCapability.DYNAMIC_LUMPED, vessel.getDynamicCapability());
      SourceTermSession session;
      DynamicCapabilityReport report;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("release-area", process);
        report = DynamicCapabilityReport.from(model);
        session = new SourceTermSession("finite-rate-study", model, CLOCK);
        session.addInventorySource("opening", "release-area", vessel.getName());
      } else {
        report = DynamicCapabilityReport.from(process);
        session = new SourceTermSession("finite-rate-study", process, CLOCK);
        session.addInventorySource("opening", vessel.getName());
      }
      assertEquals(Integer.valueOf(1), report.getCapabilityCounts().get(DynamicCapability.DYNAMIC_LUMPED));
      assertTrue(report.isFullyAudited(), report.toJson());
      assertEquals(1, report.getUnverifiedActivationElements().size());
      assertEquals(SourceTermFrame.Status.VALID, session.runSteadyState().get(0).getStatus());
      SourceTermFrame frame = session.step(0.10).get(0);
      assertEquals(SourceTermFrame.Status.VALID, frame.getStatus(), frame.toJson());
      assertTrue(frame.toJson().contains("COUPLED_RIGID_THERMAL_EQUILIBRIUM_FINITE_RATE_PHASE_INVENTORY"));
      assertTrue(frame.toJson().contains("inventoryGasMassKg"));
      assertEquals(frame.toJson(), frame.toJson());
      assertEquals(0.10, vessel.getTime(), 0.0);
      balanced(vessel);
      vessel.setReleaseEnabled(false);
      assertEquals(DynamicCapability.DYNAMIC_LUMPED, vessel.getDynamicCapability());
    }
  }

  @Test
  void repeatedIdentityAndFailedStepRollbackAtomically() {
    NonEquilibriumReleaseInventory vessel = inventory(0.02, 1.0e-3);
    UUID id = UUID.randomUUID();
    vessel.runTransient(0.10, id);
    NonEquilibriumReleaseInventory.Balance accepted = vessel.getBalance();
    vessel.runTransient(0.10, id);
    assertEquals(accepted.getReleasedMassKg(), vessel.getBalance().getReleasedMassKg(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> vessel.runTransient(0.05, id));
    assertEquals(accepted.getReleasedMassKg(), vessel.getBalance().getReleasedMassKg(), 0.0);

    ReleaseFlowModel failAfterCandidate = new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;
      private int calls;

      /** {@inheritDoc} */
      @Override
      public String getModelId() {
        return "test-fail-after-candidate";
      }

      /** {@inheritDoc} */
      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        if (++calls > 1) {
          throw new IllegalStateException("synthetic second-substep failure");
        }
        SystemInterface fluid = request.getFluid();
        fluid.init(3);
        ReleaseState releaseState = ReleaseState.fromFluid(fluid, 0.0);
        Map<ReleaseFlowResult.Station, ReleaseState> stations = new EnumMap<ReleaseFlowResult.Station, ReleaseState>(
            ReleaseFlowResult.Station.class);
        for (ReleaseFlowResult.Station station : ReleaseFlowResult.Station.values()) {
          stations.put(station, releaseState);
        }
        return ReleaseFlowResult.success(this, 2.0e-4, false, stations,
            Collections.singletonList(new ReleaseFlowResult.Diagnostic("CONTROLLED_TEST_MODEL", "atomic rollback")),
            null, false);
      }
    };
    NonEquilibriumReleaseInventory failing = new NonEquilibriumReleaseInventory("failing", twoPhaseFluid(), VOLUME_M3,
        0.005, 0.7, 100000.0, failAfterCandidate, 0.02, PhaseType.GAS, geometry(), transfer(1.0e-3));
    NonEquilibriumReleaseInventory.Balance before = failing.getBalance();
    assertThrows(IllegalStateException.class, () -> failing.runTransient(0.10, UUID.randomUUID()));
    assertEquals(before.getReleasedMassKg(), failing.getBalance().getReleasedMassKg(), 0.0);
    assertEquals(before.getInternalEnergyJ(), failing.getBalance().getInternalEnergyJ(), 0.0);
    assertEquals(before.getGasComponentMassKg(), failing.getBalance().getGasComponentMassKg());
  }

  @Test
  void timestepRefinementConvergesPressureAndPhasePartition() throws Exception {
    double[] steps = {0.04, 0.02, 0.01, 0.005};
    double[] pressure = new double[steps.length];
    double[] gasButane = new double[steps.length];
    StringBuilder csv = new StringBuilder("max_substep_s,pressure_pa,gas_n_butane_kg,energy_residual_j\n");
    for (int i = 0; i < steps.length; i++) {
      NonEquilibriumReleaseInventory vessel = inventory(steps[i], 2.0e-3);
      vessel.runTransient(0.20, UUID.randomUUID());
      pressure[i] = vessel.getGasPhase().getPressure() * 1.0e5;
      gasButane[i] = vessel.getBalance().getGasComponentMassKg().get("n-butane");
      NonEquilibriumReleaseInventory.Balance balance = vessel.getBalance();
      double residual = balance.getInternalEnergyJ() + balance.getReleasedEnergyJ() - balance.getHeatTransferEnergyJ()
          - balance.getInitialEnergyJ();
      csv.append(steps[i]).append(',').append(pressure[i]).append(',').append(gasButane[i]).append(',').append(residual)
          .append('\n');
      balanced(vessel);
    }
    double coarsePressure = Math.abs(pressure[0] - pressure[1]);
    double finePressure = Math.abs(pressure[2] - pressure[3]);
    double coarsePartition = Math.abs(gasButane[0] - gasButane[1]);
    double finePartition = Math.abs(gasButane[2] - gasButane[3]);
    assertTrue(coarsePressure > 0.0);
    assertTrue(finePressure < coarsePressure);
    assertTrue(coarsePartition > 0.0);
    assertTrue(finePartition < coarsePartition);
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("non-equilibrium-release-inventory-refinement.csv"),
        csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void isolatedEquilibriumTransferDoesNotInventMassOrEnergy() {
    NonEquilibriumReleaseInventory vessel = inventory(0.02, 2.0e-3);
    vessel.setReleaseEnabled(false);
    NonEquilibriumReleaseInventory.Balance before = vessel.getBalance();
    vessel.runTransient(0.10, UUID.randomUUID());
    NonEquilibriumReleaseInventory.Balance after = vessel.getBalance();
    assertEquals(0.0, after.getReleasedMassKg(), 0.0);
    for (String name : before.getInitialComponentMassKg().keySet()) {
      assertEquals(before.getInitialComponentMassKg().get(name), after.getRemainingComponentMassKg().get(name),
          Math.max(1.0e-14, before.getInitialComponentMassKg().get(name) * 1.0e-12));
    }
    assertEquals(before.getInitialEnergyJ(), after.getInternalEnergyJ(),
        Math.max(1.0, Math.abs(before.getInitialEnergyJ())) * 1.0e-7);
    assertFalse(vessel.isReleaseEnabled());
    balanced(vessel);
  }
}
