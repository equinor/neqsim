package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;

class ElectricMotorPerformanceIntegrationTest {

  @Test
  void testPartLoadEfficiencyIsUsedForInverseElectricalSizing() {
    ElectricMotorDriver performance = new ElectricMotorDriver(1000.0, 3000.0, 0.96);
    ElectricMotor motor = new ElectricMotor("part-load motor");
    motor.setPerformanceModel(performance);
    motor.setOperatingSpeed(3000.0);

    double expectedEfficiency = 0.96 * 0.85;
    double requiredInput = motor.getRequiredInputPowerForOutput(250.0e3);

    assertEquals(expectedEfficiency, motor.getEfficiencyAtOutputPower(250.0e3), 1.0e-12);
    assertEquals(250.0e3 / expectedEfficiency, requiredInput, 1.0e-9);
  }

  @Test
  void testMotorDriveTrainUsesPerformanceModelForRequestedInput() {
    ElectricMotorDriver performance = new ElectricMotorDriver(1000.0, 3000.0, 0.96);
    ElectricMotor motor = new ElectricMotor("drive motor");
    motor.setPerformanceModel(performance);

    Compressor compressor = new Compressor("compressor");
    EnergyBus electricalBus = new EnergyBus("electrical bus", EnergyType.ELECTRICAL);
    MechanicalShaft shaft = new MechanicalShaft("compressor shaft");
    shaft.setSpeed(3000.0);
    MotorDriveTrain driveTrain = new MotorDriveTrain(motor, compressor, electricalBus, shaft);

    driveTrain.setRequestedShaftPower(250.0e3);

    assertEquals(motor.getRequiredInputPowerForOutput(250.0e3),
        motor.getEnergyPort(EnergyConverter.INPUT_PORT).getRequestedPower(), 1.0e-9);
    assertEquals(250.0e3, compressor.getEnergyPort("shaftPower").getRequestedPower(), 1.0e-9);
  }

  @Test
  void testAmbientDeratingCapsRealizedMotorOutputAndPreservesConservation() {
    ElectricMotorDriver performance = new ElectricMotorDriver(1000.0, 3000.0, 0.96);
    performance.setAmbientTemperature(50.0);

    EnergyBus electricalBus = new EnergyBus("derated electrical bus", EnergyType.ELECTRICAL);
    MechanicalShaft shaft = new MechanicalShaft("derated motor shaft");
    shaft.setSpeed(3000.0);
    EnergyPort source = port("grid", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, electricalBus);

    ElectricMotor motor = new ElectricMotor("derated motor");
    motor.setPerformanceModel(performance);
    motor.connectEnergyStream(EnergyConverter.INPUT_PORT, electricalBus, EnergyPortMode.SPECIFICATION);
    motor.connectEnergyStream(EnergyConverter.OUTPUT_PORT, shaft, EnergyPortMode.CALCULATED);
    motor.setRequestedInputPower(2.0e6);
    source.setDuty(2.0e6);

    electricalBus.solveBalance();
    motor.run(UUID.randomUUID());

    assertEquals(800.0e3, motor.getAvailableShaftPower(), 1.0e-9);
    assertEquals(800.0e3, motor.getOutputPower(), 1.0e-6);
    assertEquals(motor.getRequiredInputPowerForOutput(800.0e3), motor.getInputPower(), 1.0e-6);
    assertTrue(motor.getInputPower() < 2.0e6);
    assertEquals(motor.getInputPower(), motor.getOutputPower() + motor.getHeatLoss(), 1.0e-9);
    assertEquals(motor.getOutputPower() / motor.getInputPower(), motor.getOperatingEfficiency(), 1.0e-12);
  }

  @Test
  void testAltitudeDeratingIsAppliedAtStandardAmbientTemperature() {
    ElectricMotorDriver performance = new ElectricMotorDriver(1000.0, 3000.0, 0.96);
    performance.setAmbientTemperature(15.0);
    performance.setAltitude(2000.0);

    ElectricMotor motor = new ElectricMotor("high-altitude motor");
    motor.setPerformanceModel(performance);
    motor.setOperatingSpeed(3000.0);

    assertEquals(900.0e3, motor.getAvailableShaftPower(), 1.0e-9);
    assertThrows(IllegalArgumentException.class, () -> motor.getRequiredInputPowerForOutput(900.1e3));
  }

  @Test
  void testFixedSpeedViolationBecomesValidWhenVfdRangeIsEnabled() {
    ElectricMotorDriver performance = new ElectricMotorDriver(1000.0, 3000.0, 0.96);
    EnergyBus electricalBus = new EnergyBus("vfd electrical bus", EnergyType.ELECTRICAL);
    MechanicalShaft shaft = new MechanicalShaft("variable speed shaft");
    shaft.setSpeed(1500.0);

    ElectricMotor motor = new ElectricMotor("VFD motor");
    motor.setPerformanceModel(performance);
    motor.connectEnergyStream(EnergyConverter.INPUT_PORT, electricalBus, EnergyPortMode.SPECIFICATION);
    motor.connectEnergyStream(EnergyConverter.OUTPUT_PORT, shaft, EnergyPortMode.CALCULATED);

    assertFalse(motor.validateSetup().isValid());
    assertEquals(0.0, motor.getAvailableShaftPower(), 1.0e-12);

    performance.setMinSpeedRatio(0.3);
    performance.setMaxSpeedRatio(1.2);
    performance.setHasVFD(true);

    assertTrue(motor.validateSetup().isValid());
    assertEquals(500.0e3, motor.getAvailableShaftPower(), 1.0e-9);
  }

  @Test
  void testRequestedOutputAboveDeratedCapabilityIsRejected() {
    ElectricMotorDriver performance = new ElectricMotorDriver(1000.0, 3000.0, 0.96);
    performance.setAmbientTemperature(50.0);
    ElectricMotor motor = new ElectricMotor("capacity-limited motor");
    motor.setPerformanceModel(performance);
    motor.setOperatingSpeed(3000.0);

    assertThrows(IllegalArgumentException.class, () -> motor.getRequiredInputPowerForOutput(800.1e3));
  }

  @Test
  void testClearingPerformanceModelRestoresConstantEfficiency() {
    ElectricMotor motor = new ElectricMotor("fallback motor", 0.95);
    motor.setPerformanceModel(new ElectricMotorDriver(1000.0, 3000.0, 0.96));
    motor.clearPerformanceModel();

    assertFalse(motor.hasPerformanceModel());
    assertEquals(100.0e3 / 0.95, motor.getRequiredInputPowerForOutput(100.0e3), 1.0e-9);
  }

  private static EnergyPort port(String owner, EnergyPortDirection direction, EnergyPortMode mode, EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", EnergyType.ELECTRICAL, direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }
}
