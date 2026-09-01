package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;

class CoupledProcessEnergyDimensionalStateTest {

  @SuppressWarnings("unchecked")
  @Test
  void testEconomicAndEmissionRatesDoNotEnterPowerResidual() throws Exception {
    EnergyBus bus = new EnergyBus("electrical bus", EnergyType.ELECTRICAL);
    EnergyPort producer = port("producer", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyPort consumer = port("consumer", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    producer.setDuty(1.0e6);
    consumer.setRequestedPower(1.0e6);
    producer.setEnergyPricePerMWh(10.0);
    producer.setEmissionFactorKgPerMWh(100.0);
    bus.solveBalance();

    Method capture = CoupledProcessEnergySolver.class.getDeclaredMethod("captureEnergyState", java.util.List.class);
    capture.setAccessible(true);
    Map<String, Double> before = (Map<String, Double>) capture.invoke(null, Collections.singletonList(bus));

    producer.setEnergyPricePerMWh(1000.0);
    producer.setEmissionFactorKgPerMWh(5000.0);
    bus.solveBalance();
    Map<String, Double> after = (Map<String, Double>) capture.invoke(null, Collections.singletonList(bus));

    Method residual = CoupledProcessEnergySolver.class.getDeclaredMethod("maximumAbsoluteChange", Map.class, Map.class);
    residual.setAccessible(true);
    double powerResidual = ((Double) residual.invoke(null, before, after)).doubleValue();

    assertEquals(0.0, powerResidual, 1.0e-12);
  }

  private static EnergyPort port(String owner, EnergyPortDirection direction, EnergyPortMode mode, EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", EnergyType.ELECTRICAL, direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }
}
