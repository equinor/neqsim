package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;

class CommittedEnergyGeneratorTest {

  @Test
  void testMinimumDownTimeBlocksStartThenStartupPenaltyIsRecorded() {
    CommittedEnergyGenerator unit = configuredUnit();
    unit.initializeCommitment(false, 300.0, 0.0);
    unit.setRequestedPower(8.0e6);

    unit.runTransient(300.0, UUID.randomUUID());
    assertFalse(unit.isCommitted());
    assertTrue(unit.getLastStepResult().isStartBlocked());
    assertEquals(0.0, unit.getCurrentPower(), 1.0e-12);

    unit.runTransient(1.0, UUID.randomUUID());
    assertTrue(unit.isCommitted());
    assertTrue(unit.getLastStepResult().isStarted());
    assertEquals(1, unit.getStartupCount());
    assertEquals(50000.0, unit.getCumulativeStartupCost(), 1.0e-12);
    assertEquals(1000.0, unit.getCumulativeStartupEmissionsKg(), 1.0e-12);
  }

  @Test
  void testRampAndMinimumStableLoad() {
    CommittedEnergyGenerator unit = configuredUnit();
    unit.initializeCommitment(false, 601.0, 0.0);
    unit.setRequestedPower(8.0e6);

    unit.runTransient(10.0, UUID.randomUUID());
    assertEquals(1.0e6, unit.getCurrentPower(), 1.0e-9);
    unit.runTransient(10.0, UUID.randomUUID());
    assertEquals(2.0e6, unit.getCurrentPower(), 1.0e-9);

    unit.setRequestedPower(0.5e6);
    unit.runTransient(10.0, UUID.randomUUID());
    assertEquals(2.0e6, unit.getCurrentPower(), 1.0e-9);
  }

  @Test
  void testMinimumUpTimeBlocksShutdown() {
    CommittedEnergyGenerator unit = configuredUnit();
    unit.initializeCommitment(true, 100.0, 4.0e6);
    unit.setRequestedPower(0.0);

    unit.runTransient(100.0, UUID.randomUUID());
    assertTrue(unit.isCommitted());
    assertTrue(unit.getLastStepResult().isStopBlocked());
    assertTrue(unit.getCurrentPower() >= 2.0e6);

    unit.runTransient(700.0, UUID.randomUUID());
    unit.runTransient(1.0, UUID.randomUUID());
    assertFalse(unit.isCommitted());
    assertTrue(unit.getLastStepResult().isStopped());
  }

  @Test
  void testGenerationIsPublishedToBus() {
    CommittedEnergyGenerator unit = configuredUnit();
    EnergyBus grid = new EnergyBus("grid", EnergyType.ELECTRICAL);
    unit.connectEnergyStream(CommittedEnergyGenerator.OUTPUT_PORT, grid, EnergyPortMode.CALCULATED);
    unit.initializeCommitment(false, 601.0, 0.0);
    unit.setRequestedPower(5.0e6);

    unit.runTransient(100.0, UUID.randomUUID());
    grid.solveBalance();

    assertEquals(unit.getCurrentPower(),
        grid.getContribution(unit.getEnergyPort(CommittedEnergyGenerator.OUTPUT_PORT).getParticipantId()), 1.0e-9);
  }

  @Test
  void testInvalidConfigurationAndInitialization() {
    CommittedEnergyGenerator unit = new CommittedEnergyGenerator("unit", EnergyType.ELECTRICAL);
    assertFalse(unit.validateSetup().isValid());
    assertThrows(IllegalArgumentException.class, () -> unit.setPowerLimits(2.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> unit.setRampRates(0.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> unit.setRequestedPower(-1.0));
    unit.setPowerLimits(2.0e6, 10.0e6);
    assertThrows(IllegalArgumentException.class, () -> unit.initializeCommitment(false, 0.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> unit.runTransient(Double.NaN, UUID.randomUUID()));
  }

  private static CommittedEnergyGenerator configuredUnit() {
    CommittedEnergyGenerator unit = new CommittedEnergyGenerator("gas turbine", EnergyType.ELECTRICAL);
    unit.setPowerLimits(2.0e6, 10.0e6);
    unit.setRampRates(0.1e6, 0.2e6);
    unit.setMinimumUpDownTimes(900.0, 600.0);
    unit.setStartupPenalty(50000.0, 1000.0);
    return unit;
  }
}
