package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Conservation, convergence, lifecycle and executable integration examples for trapped gas releases. */
class ReleaseInventoryTest extends neqsim.NeqSimTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC);

  private static SystemInterface gas(double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(300.0, pressureBar);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static ReleaseInventory inventory(double pressureBar, double step) {
    return new ReleaseInventory("inventory", gas(pressureBar), 1.0, 0.01, 0.7, 10000.0, new IdealGasReleaseModel(),
        step);
  }

  private static void balanced(ReleaseInventory inventory) {
    ReleaseInventory.Balance b = inventory.getBalance();
    double released = 0.0;
    for (String name : b.getInitialComponentMassKg().keySet()) {
      double initial = b.getInitialComponentMassKg().get(name);
      assertEquals(initial, b.getRemainingComponentMassKg().get(name) + b.getReleasedComponentMassKg().get(name),
          initial * 1e-10);
      released += b.getReleasedComponentMassKg().get(name);
    }
    assertEquals(released, b.getReleasedMassKg(), 1e-12);
    assertEquals(b.getInitialEnergyJ(), b.getInternalEnergyJ() + b.getReleasedEnergyJ(),
        Math.max(1.0, Math.abs(b.getInitialEnergyJ())) * 1e-7);
    assertEquals(b.getVolumeM3(), inventory.getFluid().getVolume("m3"), b.getVolumeM3() * 1e-7);
  }

  @Test
  void withdrawsMixtureComponentsAndEnthalpyWithoutMutatingCaller() {
    SystemInterface initial = gas(5.0);
    initial.addComponent("methane", 0.2);
    initial.setMixingRule("classic");
    double originalMoles = initial.getTotalNumberOfMoles();
    ReleaseInventory vessel = new ReleaseInventory("inventory", initial, 0.5, 0.008, 0.62, 101325.0,
        new HomogeneousEquilibriumReleaseModel(), 0.1);
    ReleaseInventory.Balance frozen = vessel.getBalance();
    double initialTemperature = vessel.getFluid().getTemperature();
    double initialRate = vessel.getReleaseModel().calculate(vessel.getReleaseRequest()).getMassFlowRateKgS();
    vessel.runTransient(0.5, UUID.randomUUID());
    assertTrue(vessel.getBalance().getReleasedMassKg() > 0.0);
    assertTrue(vessel.getFluid().getTemperature() < initialTemperature);
    assertTrue(vessel.getFluid().getPressure() < 5.0);
    assertTrue(vessel.getReleaseModel().calculate(vessel.getReleaseRequest()).getMassFlowRateKgS() < initialRate);
    assertEquals(originalMoles, initial.getTotalNumberOfMoles(), 0.0);
    assertEquals(5.0, initial.getPressure(), 0.0);
    assertEquals(300.0, initial.getTemperature(), 0.0);
    assertEquals(0.0, frozen.getReleasedMassKg(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> frozen.getInitialComponentMassKg().clear());
    SystemInterface copy = vessel.getFluid();
    copy.setTemperature(500.0);
    assertTrue(vessel.getFluid().getTemperature() < 300.0);
    balanced(vessel);
  }

  @Test
  void energyWithdrawalUsesStagnationEnthalpyIncludingFlowWork() {
    ReleaseInventory vessel = inventory(2.0, 0.1);
    SystemInterface initial = vessel.getFluid();
    double rate = vessel.getReleaseModel().calculate(vessel.getReleaseRequest()).getMassFlowRateKgS();
    double expectedEnergyLoss = rate * 0.1 * initial.getEnthalpy("J/kg");
    vessel.runTransient(0.1, UUID.randomUUID());
    assertEquals(rate * 0.1, vessel.getBalance().getReleasedMassKg(), 1e-14);
    assertEquals(expectedEnergyLoss, vessel.getBalance().getReleasedEnergyJ(), 1e-10);
    assertTrue(Math.abs(expectedEnergyLoss - rate * 0.1 * initial.getInternalEnergy("J/kg")) > 1.0);
    balanced(vessel);
  }

  @Test
  void refinementConvergesAndMatchesIndependentAdiabaticGasLimit() throws Exception {
    StringBuilder csv = new StringBuilder("initialPressure_Pa,maxSubstep_s,time_s,pressure_Pa,mass_kg,temperature_K,"
        + "releasedMass_kg,energyResidual_J,analyticalMass_kg,analyticalPressure_Pa\n");
    for (double initialPressure : new double[] {2.0, 2.2}) {
      double[] pressures = new double[3];
      for (int i = 0; i < 3; i++) {
        double dt = 0.2 / Math.pow(2.0, i);
        ReleaseInventory vessel = inventory(initialPressure, dt);
        SystemInterface fluid = vessel.getFluid();
        double gamma = fluid.getGamma();
        double mass0 = fluid.getMass("kg");
        double temperature0 = fluid.getTemperature();
        double pressure0 = fluid.getPressure() * 1e5;
        // Independent closed-form constant-gamma rigid adiabatic choked blowdown solution.
        double rSpecific = 8.31446261815324 / fluid.getMolarMass();
        double rate0 = 0.7 * Math.PI * 0.01 * 0.01 / 4.0 * pressure0 * Math.sqrt(gamma / (rSpecific * temperature0))
            * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
        double fraction = Math.pow(1.0 + (gamma - 1.0) * rate0 * 10.0 / (2.0 * mass0), -2.0 / (gamma - 1.0));
        vessel.runTransient(10.0, UUID.randomUUID());
        SystemInterface finalFluid = vessel.getFluid();
        pressures[i] = finalFluid.getPressure() * 1e5;
        assertEquals(mass0 * fraction, finalFluid.getMass("kg"), mass0 * 0.003);
        assertEquals(pressure0 * Math.pow(fraction, gamma), pressures[i], pressure0 * 0.003);
        assertEquals(temperature0 * Math.pow(fraction, gamma - 1.0), finalFluid.getTemperature(), temperature0 * 0.003);
        balanced(vessel);
        ReleaseInventory.Balance b = vessel.getBalance();
        csv.append(pressure0).append(',').append(dt).append(",10.0,").append(pressures[i]).append(',')
            .append(finalFluid.getMass("kg")).append(',').append(finalFluid.getTemperature()).append(',')
            .append(b.getReleasedMassKg()).append(',')
            .append(b.getInternalEnergyJ() + b.getReleasedEnergyJ() - b.getInitialEnergyJ()).append(',')
            .append(mass0 * fraction).append(',').append(pressure0 * Math.pow(fraction, gamma)).append('\n');
      }
      double coarse = Math.abs(pressures[0] - pressures[1]);
      double fine = Math.abs(pressures[1] - pressures[2]);
      assertTrue(coarse > 0.0 && fine < coarse * 0.6, "Expected first-order timestep convergence");
    }
    write("source-term-benchmarks", "inventory-convergence.csv", csv.toString());
  }

  @Test
  void documentationExampleAndPhysicalIsolationWorkForBothContainers() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      ReleaseInventory vessel = inventory(2.0, 0.1);
      ProcessSystem process = new ProcessSystem();
      process.add(vessel);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("gas-area", process);
        session = new SourceTermSession("inventory-study", model, CLOCK);
        session.addInventorySource("opening", "gas-area", "inventory");
      } else {
        session = new SourceTermSession("inventory-study", process, CLOCK);
        session.addInventorySource("opening", "inventory");
      }
      SourceTermFrame first = session.runSteadyState().get(0);
      assertEquals(SourceTermFrame.Status.VALID, first.getStatus(), first.toJson());
      assertEquals(0.0, vessel.getBalance().getReleasedMassKg(), 0.0);
      SourceTermFrame next = session.step(0.5).get(0);
      assertEquals(SourceTermFrame.Status.VALID, next.getStatus(), next.toJson());
      assertEquals(0.5, next.getSimulationTimeS(), 0.0);
      assertEquals(0.5, vessel.getTime(), 0.0);
      assertEquals(process.getCalculationIdentifier(), vessel.getCalculationIdentifier());
      JsonObject frame = JsonParser.parseString(next.toJson()).getAsJsonObject();
      JsonObject provenance = frame.getAsJsonObject("provenance");
      assertEquals("COUPLED_RIGID_ADIABATIC_GAS_INVENTORY", provenance.get("releaseBasis").getAsString());
      assertEquals(vessel.getBalance().getReleasedMassKg(), provenance.get("cumulativeReleasedMassKg").getAsDouble(),
          0.0);
      assertEquals(vessel.getFluid().getPressure() * 1e5, frame.getAsJsonObject("source").getAsJsonObject("stations")
          .getAsJsonObject("UPSTREAM_STAGNATION").getAsJsonObject("pressure").get("value").getAsDouble(), 1e-6);
      assertEquals(next.toJson(), next.toJson());
      write("source-term-contract-fixtures", "inventory-" + useModel + ".json", next.toJson());
      vessel.setReleaseEnabled(false);
      double released = vessel.getBalance().getReleasedMassKg();
      SourceTermFrame closed = session.step(0.5).get(0);
      assertEquals(SourceTermFrame.Status.DISABLED, closed.getStatus(), closed.toJson());
      assertTrue(closed.toJson().contains("INVENTORY_OPENING_CLOSED"));
      assertEquals(released, vessel.getBalance().getReleasedMassKg(), 0.0);
      write("source-term-contract-fixtures", "inventory-closed-" + useModel + ".json", closed.toJson());
      vessel.setReleaseEnabled(true);
      assertEquals(SourceTermFrame.Status.VALID, session.step(0.5).get(0).getStatus());
      assertTrue(vessel.getBalance().getReleasedMassKg() > released);
      balanced(vessel);
    }
  }

  @Test
  void repeatedNativeEvaluationDoesNotWithdrawTwice() {
    ReleaseInventory vessel = inventory(2.0, 0.1);
    ProcessSystem process = new ProcessSystem();
    process.add(vessel);
    process.setIntegrationMethod(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT);
    UUID id = UUID.randomUUID();
    process.runTransient(0.5, id);
    double released = vessel.getBalance().getReleasedMassKg();
    assertEquals(0.5, vessel.getTime(), 0.0);
    vessel.runTransient(0.5, id);
    assertEquals(released, vessel.getBalance().getReleasedMassKg(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> vessel.runTransient(0.25, id));
  }

  @Test
  void noForwardPressureDifferenceLeavesInventoryUnchanged() {
    ReleaseInventory vessel = new ReleaseInventory("inventory", gas(1.0), 1.0, 0.01, 0.7, 110000.0,
        new IdealGasReleaseModel(), 0.1);
    ReleaseInventory.Balance before = vessel.getBalance();
    vessel.runTransient(10.0, UUID.randomUUID());
    assertEquals(0.0, vessel.getBalance().getReleasedMassKg(), 0.0);
    assertEquals(before.getInternalEnergyJ(), vessel.getBalance().getInternalEnergyJ(), 0.0);
    assertEquals(10.0, vessel.getTime(), 0.0);
    balanced(vessel);
  }

  @Test
  void subcriticalNearbyBackpressuresPreserveBalances() {
    for (double backPressurePa : new double[] {120000.0, 180000.0}) {
      ReleaseInventory vessel = new ReleaseInventory("inventory", gas(2.0), 1.0, 0.01, 0.7, backPressurePa,
          new IdealGasReleaseModel(), 0.1);
      ReleaseFlowResult initial = vessel.getReleaseModel().calculate(vessel.getReleaseRequest());
      assertFalse(initial.isChoked());
      vessel.runTransient(0.5, UUID.randomUUID());
      assertTrue(vessel.getBalance().getReleasedMassKg() > 0.0);
      assertTrue(vessel.getPressure() < 2.0);
      assertTrue(vessel.getPressure() * 1e5 > backPressurePa);
      balanced(vessel);
    }
  }

  @Test
  void rejectsLiquidAndScreeningRatherThanInventingADepletionModel() {
    SystemInterface liquid = new SystemSrkEos(230.0, 10.0);
    liquid.addComponent("propane", 1.0);
    liquid.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new ReleaseInventory("liquid", liquid, 1.0, 0.01, 0.7, 101325.0,
        new HomogeneousEquilibriumReleaseModel(), 0.1));
    ReleaseInventory screening = new ReleaseInventory("screen", gas(2.0), 1.0, 0.01, 0.7, 101325.0,
        new LegacyScreeningReleaseModel(), 0.1);
    assertTrue(assertThrows(IllegalStateException.class, () -> screening.runTransient(0.1, UUID.randomUUID()))
        .getMessage().contains("SCREENING_UNSUPPORTED"));
    assertEquals(0.0, screening.getTime(), 0.0);
    assertEquals(0.0, screening.getBalance().getReleasedMassKg(), 0.0);
  }

  @Test
  void modelFailureAfterSubstepRollsBackInventoryAndProducesFailClosedSession() throws Exception {
    ReleaseFlowModel failing = new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;
      private int calls;

      @Override
      public String getModelId() {
        return "ideal-gas-isentropic-orifice";
      }

      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        if (++calls >= 4) {
          return ReleaseFlowResult.failure(this, false, "INJECTED_FAILURE", "mid-step regression");
        }
        return new IdealGasReleaseModel().calculate(request);
      }
    };
    ReleaseInventory vessel = new ReleaseInventory("inventory", gas(2.0), 1.0, 0.01, 0.7, 10000.0, failing, 0.1);
    ProcessSystem process = new ProcessSystem();
    process.add(vessel);
    SourceTermSession session = new SourceTermSession("failed-inventory", process, CLOCK);
    session.addInventorySource("opening", "inventory");
    assertEquals(SourceTermFrame.Status.VALID, session.runSteadyState().get(0).getStatus());
    UUID beforeId = vessel.getCalculationIdentifier();
    Map<String, Double> before = vessel.getBalance().getRemainingComponentMassKg();
    SourceTermFrame failed = session.step(0.5).get(0);
    assertEquals(SourceTermFrame.Status.INVALID, failed.getStatus());
    assertFalse(JsonParser.parseString(failed.toJson()).getAsJsonObject().has("source"));
    assertEquals(before, vessel.getBalance().getRemainingComponentMassKg());
    assertEquals(0.0, vessel.getTime(), 0.0);
    assertEquals(beforeId, vessel.getCalculationIdentifier());
    assertTrue(session.isFaulted());
    assertThrows(IllegalStateException.class, () -> session.step(0.1));
    write("source-term-contract-fixtures", "inventory-failed.json", failed.toJson());
  }

  @Test
  void invalidStepAndPressureCrossingCannotPartiallyDeplete() {
    ReleaseInventory vessel = new ReleaseInventory("inventory", gas(1.01), 0.001, 0.01, 0.7, 100000.0,
        new IdealGasReleaseModel(), 1.0);
    assertThrows(IllegalArgumentException.class, () -> vessel.runTransient(Double.NaN, UUID.randomUUID()));
    assertTrue(assertThrows(IllegalStateException.class, () -> vessel.runTransient(1.0, UUID.randomUUID())).getMessage()
        .contains("RECEIVING_PRESSURE_CROSSED"));
    assertEquals(0.0, vessel.getBalance().getReleasedMassKg(), 0.0);
    assertEquals(0.0, vessel.getTime(), 0.0);
  }

  @Test
  void exportDisableDoesNotDisablePhysicsAndRegistrationDoesNotDuplicateWithdrawal() {
    ReleaseInventory vessel = inventory(2.0, 0.1);
    ProcessSystem process = new ProcessSystem();
    process.add(vessel);
    SourceTermSession session = new SourceTermSession("inventory-study", process, CLOCK);
    session.addInventorySource("opening", "inventory");
    assertThrows(IllegalArgumentException.class, () -> session.addInventorySource("duplicate", "inventory"));
    session.runSteadyState();
    session.setEnabled("opening", false);
    assertEquals(SourceTermFrame.Status.DISABLED, session.step(0.1).get(0).getStatus());
    assertTrue(vessel.getBalance().getReleasedMassKg() > 0.0);
    session.setEnabled("opening", true);
    UUID oldId = process.getCalculationIdentifier();
    vessel.setReleaseEnabled(false);
    assertEquals(SourceTermFrame.Status.STALE,
        session.capture(Collections.singletonMap(SourceTermSession.SINGLE_AREA, oldId)).get(0).getStatus());
    assertThrows(UnsupportedOperationException.class, () -> vessel.setTemperature(310.0));
    assertThrows(UnsupportedOperationException.class, () -> vessel.setPressure(2.5));
  }

  @Test
  void mismatchedModelStateFailsBeforeWithdrawal() {
    ReleaseFlowModel mismatched = new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      @Override
      public String getModelId() {
        return "ideal-gas-isentropic-orifice";
      }

      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        SystemInterface wrong = request.getFluid();
        wrong.setTemperature(wrong.getTemperature() + 20.0);
        return new IdealGasReleaseModel().calculate(new ReleaseFlowRequest(wrong, request.getDiameterM(),
            request.getDischargeCoefficient(), request.getBackPressurePa()));
      }
    };
    ReleaseInventory vessel = new ReleaseInventory("inventory", gas(2.0), 1.0, 0.01, 0.7, 10000.0, mismatched, 0.1);
    assertTrue(assertThrows(IllegalStateException.class, () -> vessel.runTransient(0.1, UUID.randomUUID())).getMessage()
        .contains("RELEASE_STATE_MISMATCH"));
    assertEquals(0.0, vessel.getBalance().getReleasedMassKg(), 0.0);
  }

  @Test
  void copyPreservesIndependentInventoryAndAccounting() {
    ReleaseInventory vessel = inventory(2.0, 0.1);
    vessel.runTransient(0.2, UUID.randomUUID());
    ReleaseInventory copy = (ReleaseInventory) vessel.copy();
    double originalMass = vessel.getFluid().getMass("kg");
    copy.runTransient(0.2, UUID.randomUUID());
    assertEquals(originalMass, vessel.getFluid().getMass("kg"), 0.0);
    assertTrue(copy.getFluid().getMass("kg") < originalMass);
    balanced(copy);
  }

  private static void write(String directoryName, String filename, String value) throws Exception {
    Path directory = Paths.get("target", directoryName);
    Files.createDirectories(directory);
    Files.write(directory.resolve(filename), value.getBytes(StandardCharsets.UTF_8));
  }
}
