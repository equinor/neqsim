package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;

class EnergyNetworkDynamicsTest {

  @Test
  void testShaftInertiaIntegratesPowerImbalanceAndTripCoastdown() {
    MechanicalShaft shaft = new MechanicalShaft("dynamic shaft");
    shaft.setMomentOfInertia(10.0);
    shaft.setSpeed(1000.0);
    shaft.setGeneratedPower("driver", 120000.0);
    shaft.setConsumedPower("load", 100000.0);

    double acceleratedSpeed = shaft.advanceTransient(1.0);

    assertTrue(acceleratedSpeed > 1000.0);
    shaft.setTripped(true);
    double coastedSpeed = shaft.advanceTransient(1.0);
    assertTrue(coastedSpeed < acceleratedSpeed);
    shaft.setSpeed(0.0);
    assertTrue(Double.isNaN(shaft.getQuality().getShaftSpeed()));
  }

  @Test
  void testBatteryBalanceAllocationUpdatesStateOfCharge() {
    EnergyBus bus = new EnergyBus("battery bus", EnergyType.ELECTRICAL);
    EnergyPort load = new EnergyPort("power", EnergyType.ELECTRICAL, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);
    load.setOwnerName("load");
    load.connect(bus);
    load.setRequestedPower(100.0);

    BatteryStorage battery = new BatteryStorage("battery", 1000.0);
    battery.setStateOfCharge(500.0);
    battery.enableAutomaticBalancing(100.0, 100.0, 10);
    battery.connectEnergyStream(BatteryStorage.ELECTRICAL_PORT, bus, EnergyPortMode.BALANCE);

    bus.solveBalance();
    battery.runTransient(3600.0);

    assertEquals(500.0 - 100.0 / 0.95, battery.getStateOfCharge(), 1.0e-9);
    assertEquals(100.0, battery.getCurrentPower(), 1.0e-9);

    double stateBeforeTrip = battery.getStateOfCharge();
    battery.setTripped(true);
    assertEquals(100.0, bus.solveBalance().getUnmetDemand(), 1.0e-9);
    battery.runTransient(3600.0);
    assertEquals(0.0, battery.getCurrentPower(), 1.0e-9);
    assertEquals(stateBeforeTrip, battery.getStateOfCharge(), 1.0e-9);
  }

  @Test
  void testBatteryReconcilesRampLimitedBalancePower() {
    EnergyBus bus = new EnergyBus("ramp-limited battery bus", EnergyType.ELECTRICAL);
    EnergyPort load = new EnergyPort("power", EnergyType.ELECTRICAL, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);
    load.setOwnerName("load");
    load.connect(bus);
    load.setRequestedPower(100.0);

    BatteryStorage battery = new BatteryStorage("battery", 1000.0);
    battery.setStateOfCharge(500.0);
    battery.enableAutomaticBalancing(100.0, 100.0, 10);
    battery.setPowerRampRate(10.0);
    battery.connectEnergyStream(BatteryStorage.ELECTRICAL_PORT, bus, EnergyPortMode.BALANCE);

    bus.solveBalance();
    battery.runTransient(1.0);

    assertEquals(10.0, battery.getCurrentPower(), 1.0e-9);
    assertEquals(10.0, bus.getAllocation(battery.getEnergyPort(BatteryStorage.ELECTRICAL_PORT).getParticipantId()),
        1.0e-9);
    assertEquals(-10.0, bus.getAllocation(load.getParticipantId()), 1.0e-9);
    assertEquals(10.0, bus.getLastReport().getServedDemand(), 1.0e-9);
    assertEquals(90.0, bus.getLastReport().getUnmetDemand(), 1.0e-9);
    assertEquals(10.0, bus.getLastReport().getBalancingGeneration(), 1.0e-9);
  }

  @Test
  void testBatteryRejectsNonFinitePowerLimits() {
    BatteryStorage battery = new BatteryStorage("battery", 1000.0);

    assertThrows(IllegalArgumentException.class, () -> battery.setPowerLimits(Double.POSITIVE_INFINITY, 100.0));
    assertThrows(IllegalArgumentException.class, () -> battery.setPowerLimits(100.0, Double.POSITIVE_INFINITY));
  }
}
