package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class EnergyBusDispatchStrategyTest {

  @Test
  void testDefaultPriorityDispatchRemainsUnchanged() {
    EnergyBus bus = new EnergyBus("priority grid", EnergyType.ELECTRICAL);
    EnergyPort prioritySource = port("priority source", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort cheapSource = port("cheap source", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort load = port("load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    prioritySource.setPriority(10);
    cheapSource.setPriority(20);
    prioritySource.setEnergyPricePerMWh(100.0);
    cheapSource.setEnergyPricePerMWh(10.0);
    prioritySource.setDuty(100.0e3);
    cheapSource.setDuty(100.0e3);
    load.setRequestedPower(100.0e3);

    EnergyNetworkReport report = bus.solveBalance();

    assertEquals(EnergyDispatchStrategy.PRIORITY_PROPORTIONAL, bus.getDispatchStrategy());
    assertEquals(100.0e3, bus.getAllocation(prioritySource.getParticipantId()), 1.0e-9);
    assertEquals(0.0, bus.getAllocation(cheapSource.getParticipantId()), 1.0e-9);
    assertEquals(10.0, report.getOperatingCostPerHour(), 1.0e-12);
    assertEquals(100.0e3, report.getCurtailedSupply(), 1.0e-9);
  }

  @Test
  void testMinimumCostDispatchSelectsLowestMarginalPrice() {
    EnergyBus bus = twoSourceBus("cost grid");
    EnergyPort expensive = bus.getRegisteredPorts().values().stream()
        .filter(port -> "expensive.power".equals(port.getParticipantName())).findFirst().get();
    EnergyPort cheap = bus.getRegisteredPorts().values().stream()
        .filter(port -> "cheap.power".equals(port.getParticipantName())).findFirst().get();
    bus.setDispatchStrategy(EnergyDispatchStrategy.MINIMUM_COST);

    EnergyNetworkReport report = bus.solveBalance();

    assertEquals(0.0, bus.getAllocation(expensive.getParticipantId()), 1.0e-9);
    assertEquals(100.0e3, bus.getAllocation(cheap.getParticipantId()), 1.0e-9);
    assertEquals(1.0, report.getOperatingCostPerHour(), 1.0e-12);
    assertEquals(100.0e3, report.getCurtailedSupply(), 1.0e-9);
  }

  @Test
  void testMinimumEmissionsDispatchSelectsLowestEmissionFactor() {
    EnergyBus bus = new EnergyBus("emissions grid", EnergyType.ELECTRICAL);
    EnergyPort highCarbon = port("high carbon", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort lowCarbon = port("low carbon", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort load = port("load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    highCarbon.setEmissionFactorKgPerMWh(500.0);
    lowCarbon.setEmissionFactorKgPerMWh(20.0);
    highCarbon.setDuty(100.0e3);
    lowCarbon.setDuty(100.0e3);
    load.setRequestedPower(100.0e3);
    bus.setDispatchStrategy(EnergyDispatchStrategy.MINIMUM_EMISSIONS);

    EnergyNetworkReport report = bus.solveBalance();

    assertEquals(0.0, bus.getAllocation(highCarbon.getParticipantId()), 1.0e-9);
    assertEquals(100.0e3, bus.getAllocation(lowCarbon.getParticipantId()), 1.0e-9);
    assertEquals(2.0, report.getCo2EmissionRate(), 1.0e-12);
  }

  @Test
  void testEqualMeritAndPrioritySourcesShareProportionally() {
    EnergyBus bus = new EnergyBus("equal merit grid", EnergyType.ELECTRICAL);
    EnergyPort large = port("large", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort small = port("small", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort load = port("load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    large.setEnergyPricePerMWh(25.0);
    small.setEnergyPricePerMWh(25.0);
    large.setDuty(100.0e3);
    small.setDuty(50.0e3);
    load.setRequestedPower(75.0e3);
    bus.setDispatchStrategy(EnergyDispatchStrategy.MINIMUM_COST);

    bus.solveBalance();

    assertEquals(50.0e3, bus.getAllocation(large.getParticipantId()), 1.0e-9);
    assertEquals(25.0e3, bus.getAllocation(small.getParticipantId()), 1.0e-9);
  }

  @Test
  void testDemandPriorityIsIndependentOfGenerationStrategy() {
    EnergyBus bus = new EnergyBus("load priority grid", EnergyType.ELECTRICAL);
    EnergyPort source = port("source", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort essential = port("essential", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    EnergyPort flexible = port("flexible", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    source.setDuty(100.0e3);
    essential.setPriority(10);
    flexible.setPriority(20);
    essential.setRequestedPower(80.0e3);
    flexible.setRequestedPower(80.0e3);
    bus.setDispatchStrategy(EnergyDispatchStrategy.MINIMUM_COST);

    EnergyNetworkReport report = bus.solveBalance();

    assertEquals(-80.0e3, bus.getAllocation(essential.getParticipantId()), 1.0e-9);
    assertEquals(-20.0e3, bus.getAllocation(flexible.getParticipantId()), 1.0e-9);
    assertEquals(60.0e3, report.getUnmetDemand(), 1.0e-9);
  }

  @Test
  void testBalancingGenerationRemainsReserveUnderMeritOrder() {
    EnergyBus bus = new EnergyBus("reserve grid", EnergyType.ELECTRICAL);
    EnergyPort normal = port("normal source", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort reserve = port("reserve", EnergyPortDirection.BIDIRECTIONAL, EnergyPortMode.BALANCE, bus);
    EnergyPort load = port("load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    normal.setEnergyPricePerMWh(100.0);
    reserve.setEnergyPricePerMWh(1.0);
    reserve.setBalanceLimits(100.0e3, 0.0);
    normal.setDuty(100.0e3);
    load.setRequestedPower(100.0e3);
    bus.setDispatchStrategy(EnergyDispatchStrategy.MINIMUM_COST);

    EnergyNetworkReport report = bus.solveBalance();

    assertEquals(100.0e3, bus.getAllocation(normal.getParticipantId()), 1.0e-9);
    assertEquals(0.0, bus.getAllocation(reserve.getParticipantId()), 1.0e-9);
    assertEquals(0.0, report.getBalancingGeneration(), 1.0e-9);
  }

  @Test
  void testChangingStrategyInvalidatesSolutionAndNullIsRejected() {
    EnergyBus bus = twoSourceBus("strategy grid");
    bus.solveBalance();
    assertTrue(bus.hasSolution());

    bus.setDispatchStrategy(EnergyDispatchStrategy.MINIMUM_COST);

    assertFalse(bus.hasSolution());
    assertThrows(IllegalArgumentException.class, () -> bus.setDispatchStrategy(null));
  }

  private static EnergyBus twoSourceBus(String name) {
    EnergyBus bus = new EnergyBus(name, EnergyType.ELECTRICAL);
    EnergyPort expensive = port("expensive", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort cheap = port("cheap", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort load = port("load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    expensive.setPriority(10);
    cheap.setPriority(20);
    expensive.setEnergyPricePerMWh(100.0);
    cheap.setEnergyPricePerMWh(10.0);
    expensive.setDuty(100.0e3);
    cheap.setDuty(100.0e3);
    load.setRequestedPower(100.0e3);
    return bus;
  }

  private static EnergyPort port(String owner, EnergyPortDirection direction, EnergyPortMode mode, EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", EnergyType.ELECTRICAL, direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }
}
