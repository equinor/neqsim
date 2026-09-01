package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.DynamicCapability;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;

class EnergyConverterTest {

  @Test
  void testMotorConvertsAllocatedElectricalPowerToShaftWork() {
    EnergyBus electricalBus = new EnergyBus("electrical", EnergyType.ELECTRICAL);
    MechanicalShaft shaft = new MechanicalShaft("shaft");
    EnergyPort grid = port("grid", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED,
        electricalBus);
    EnergyPort load = port("load", EnergyType.SHAFT_WORK, EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION,
        shaft);
    ElectricMotor motor = new ElectricMotor("motor", 0.95);
    motor.connectEnergyStream(EnergyConverter.INPUT_PORT, electricalBus, EnergyPortMode.SPECIFICATION);
    motor.connectEnergyStream(EnergyConverter.OUTPUT_PORT, shaft, EnergyPortMode.CALCULATED);
    motor.setRequestedInputPower(100000.0);
    load.setRequestedPower(95000.0);
    grid.setDuty(100000.0);

    electricalBus.solveBalance();
    motor.run();
    shaft.solveBalance();

    assertEquals(100000.0, motor.getInputPower(), 1.0e-9);
    assertEquals(95000.0, motor.getOutputPower(), 1.0e-9);
    assertEquals(5000.0, motor.getHeatLoss(), 1.0e-9);
    assertEquals(-95000.0, shaft.getAllocation(load.getParticipantId()), 1.0e-9);
    assertEquals(5000.0, shaft.getLastReport().getConversionLoss(), 1.0e-9);
  }

  @Test
  void testConverterTransientRampAndTrip() {
    EnergyBus input = new EnergyBus("input", EnergyType.ELECTRICAL);
    EnergyBus output = new EnergyBus("output", EnergyType.ELECTRICAL);
    EnergyPort source = port("source", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED,
        input);
    Inverter inverter = new Inverter("inverter");
    inverter.connectEnergyStream(EnergyConverter.INPUT_PORT, input, EnergyPortMode.SPECIFICATION);
    inverter.connectEnergyStream(EnergyConverter.OUTPUT_PORT, output, EnergyPortMode.CALCULATED);
    inverter.setRequestedInputPower(1000.0);
    inverter.setRampRate(100.0);
    source.setDuty(1000.0);
    input.solveBalance();

    inverter.runTransient(2.0);

    assertEquals(200.0, inverter.getOutputPower(), 1.0e-12);
    inverter.setTripped(true);
    inverter.runTransient(2.0);
    assertEquals(0.0, inverter.getOutputPower(), 1.0e-12);
    assertTrue(inverter.isTripped());
  }

  @Test
  void transientRefinementRecomputesFromPhysicalStepStartWithoutDoubleAdvance() {
    EnergyBus input = new EnergyBus("input", EnergyType.ELECTRICAL);
    EnergyBus output = new EnergyBus("output", EnergyType.ELECTRICAL);
    EnergyPort source = port("source", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED,
        input);
    Inverter inverter = new Inverter("inverter");
    inverter.connectEnergyStream(EnergyConverter.INPUT_PORT, input, EnergyPortMode.SPECIFICATION);
    inverter.connectEnergyStream(EnergyConverter.OUTPUT_PORT, output, EnergyPortMode.CALCULATED);
    inverter.setRequestedInputPower(1000.0);
    inverter.setRampRate(100.0);
    source.setDuty(1000.0);
    input.solveBalance();

    UUID physicalStepA = UUID.randomUUID();
    UUID physicalStepB = UUID.randomUUID();

    inverter.runTransient(2.0, physicalStepA);
    assertEquals(200.0, inverter.getOutputPower(), 1.0e-12);
    assertEquals(2.0, inverter.getTime(), 0.0);
    assertEquals(physicalStepA, inverter.getCalculationIdentifier());

    inverter.runTransient(2.0, physicalStepA);
    assertEquals(200.0, inverter.getOutputPower(), 1.0e-12);
    assertEquals(2.0, inverter.getTime(), 0.0);
    assertEquals(physicalStepA, inverter.getCalculationIdentifier());

    inverter.runTransient(2.0, physicalStepB);
    assertEquals(400.0, inverter.getOutputPower(), 1.0e-12);
    assertEquals(4.0, inverter.getTime(), 0.0);
    assertEquals(physicalStepB, inverter.getCalculationIdentifier());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, inverter.getDynamicCapability());
  }

  private static EnergyPort port(String owner, EnergyType type, EnergyPortDirection direction, EnergyPortMode mode,
      EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", type, direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }
}
