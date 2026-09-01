package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.process.equipment.stream.UtilityLevel;

class EnergyNetworkProfessionalHardeningTest {

  @Test
  void testTransientConverterOutputIsLimitedByAvailableInput() {
    EnergyBus inputBus = new EnergyBus("input bus", EnergyType.ELECTRICAL);
    EnergyBus outputBus = new EnergyBus("output bus", EnergyType.ELECTRICAL);
    EnergyPort source = port("source", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED,
        inputBus);

    Inverter inverter = new Inverter("inverter");
    inverter.setEfficiency(0.95);
    inverter.setIdleLoss(10.0);
    inverter.setRampRate(10.0);
    inverter.connectEnergyStream(EnergyConverter.INPUT_PORT, inputBus, EnergyPortMode.SPECIFICATION);
    inverter.connectEnergyStream(EnergyConverter.OUTPUT_PORT, outputBus, EnergyPortMode.CALCULATED);
    inverter.setRequestedInputPower(1000.0);

    source.setDuty(1000.0);
    inputBus.solveBalance();
    inverter.run(UUID.randomUUID());
    assertEquals(940.5, inverter.getOutputPower(), 1.0e-12);

    source.setDuty(100.0);
    inputBus.solveBalance();
    inverter.runTransient(1.0, UUID.randomUUID());

    assertEquals(100.0, inverter.getInputPower(), 1.0e-12);
    assertEquals(85.5, inverter.getOutputPower(), 1.0e-12);
    assertEquals(14.5, inverter.getHeatLoss(), 1.0e-12);
    assertEquals(inverter.getInputPower(), inverter.getOutputPower() + inverter.getHeatLoss(), 1.0e-12);
    assertThrows(IllegalArgumentException.class, () -> inverter.runTransient(-1.0, UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> inverter.runTransient(Double.NaN, UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class,
        () -> inverter.runTransient(Double.POSITIVE_INFINITY, UUID.randomUUID()));
  }

  @Test
  void testInverterQualityUpdatePreservesExistingMetadata() {
    EnergyBus outputBus = new EnergyBus("quality bus", EnergyType.ELECTRICAL);
    outputBus.getQuality().setTemperature(320.0);
    outputBus.getQuality().setPressure(2.0e5);

    Inverter inverter = new Inverter("quality inverter");
    inverter.connectEnergyStream(EnergyConverter.OUTPUT_PORT, outputBus, EnergyPortMode.CALCULATED);
    inverter.setOutputElectricalQuality(690.0, 50.0);

    assertEquals(690.0, outputBus.getQuality().getVoltage(), 1.0e-12);
    assertEquals(50.0, outputBus.getQuality().getFrequency(), 1.0e-12);
    assertEquals(320.0, outputBus.getQuality().getTemperature(), 1.0e-12);
    assertEquals(2.0e5, outputBus.getQuality().getPressure(), 1.0e-12);
  }

  @Test
  void testInverterRejectsInvalidSetpointsWithoutChangingConfiguredQuality() {
    EnergyBus outputBus = new EnergyBus("validated quality bus", EnergyType.ELECTRICAL);
    Inverter inverter = new Inverter("validated inverter");
    inverter.connectEnergyStream(EnergyConverter.OUTPUT_PORT, outputBus, EnergyPortMode.CALCULATED);
    inverter.setOutputElectricalQuality(690.0, 50.0);

    assertThrows(IllegalArgumentException.class, () -> inverter.setOutputElectricalQuality(Double.NaN, 50.0));
    assertThrows(IllegalArgumentException.class, () -> inverter.setOutputElectricalQuality(690.0, 0.0));
    assertEquals(690.0, inverter.getOutputVoltage(), 1.0e-12);
    assertEquals(50.0, inverter.getOutputFrequency(), 1.0e-12);
    assertEquals(690.0, outputBus.getQuality().getVoltage(), 1.0e-12);
    assertEquals(50.0, outputBus.getQuality().getFrequency(), 1.0e-12);
  }

  @Test
  void testInverterPublishesQualityWhenConfiguredBeforeConnection() {
    Inverter inverter = new Inverter("late-connected inverter");
    inverter.setOutputElectricalQuality(400.0, 60.0);

    EnergyBus outputBus = new EnergyBus("late output", EnergyType.ELECTRICAL);
    outputBus.getQuality().setTemperature(315.0);
    inverter.connectEnergyStream(EnergyConverter.OUTPUT_PORT, outputBus, EnergyPortMode.CALCULATED);
    inverter.run(UUID.randomUUID());

    assertEquals(400.0, outputBus.getQuality().getVoltage(), 1.0e-12);
    assertEquals(60.0, outputBus.getQuality().getFrequency(), 1.0e-12);
    assertEquals(315.0, outputBus.getQuality().getTemperature(), 1.0e-12);
  }

  @Test
  void testMotorDriveTrainDetectsMotorOutputRewiring() {
    ElectricMotor motor = new ElectricMotor("motor");
    Compressor compressor = new Compressor("compressor");
    EnergyBus electricalBus = new EnergyBus("electrical bus", EnergyType.ELECTRICAL);
    MechanicalShaft shaft = new MechanicalShaft("configured shaft");
    MotorDriveTrain driveTrain = new MotorDriveTrain(motor, compressor, electricalBus, shaft);

    assertTrue(driveTrain.validateSetup().isValid());

    MechanicalShaft wrongShaft = new MechanicalShaft("wrong shaft");
    motor.connectEnergyStream(EnergyConverter.OUTPUT_PORT, wrongShaft, EnergyPortMode.CALCULATED);

    assertFalse(driveTrain.validateSetup().isValid());
    assertTrue(driveTrain.validateSetup().getReport().contains("Motor is not connected to the configured shaft"));
  }

  @Test
  void testMotorAssistedDriveTrainDetectsAssistMotorOutputRewiring() {
    Expander expander = new Expander("expander");
    Compressor compressor = new Compressor("compressor");
    ElectricMotor assistMotor = new ElectricMotor("assist motor");
    EnergyBus electricalBus = new EnergyBus("assist electrical bus", EnergyType.ELECTRICAL);
    MechanicalShaft shaft = new MechanicalShaft("common shaft");
    MotorAssistedDriveTrain driveTrain = new MotorAssistedDriveTrain(expander, compressor, assistMotor, electricalBus,
        shaft);

    assertTrue(driveTrain.validateSetup().isValid());

    MechanicalShaft wrongShaft = new MechanicalShaft("wrong assist shaft");
    assistMotor.connectEnergyStream(EnergyConverter.OUTPUT_PORT, wrongShaft, EnergyPortMode.CALCULATED);

    assertFalse(driveTrain.validateSetup().isValid());
    assertTrue(driveTrain.validateSetup().getReport().contains("Assist motor is not connected to the common shaft"));
  }

  @Test
  void testUtilityEnergyBusRequiresSpecifiedGrade() {
    assertThrows(IllegalArgumentException.class, () -> new UtilityEnergyBus("null utility", null));
    assertThrows(IllegalArgumentException.class,
        () -> new UtilityEnergyBus("unspecified utility", UtilityLevel.UNSPECIFIED));

    UtilityEnergyBus steam = new UtilityEnergyBus("LP steam", UtilityLevel.LOW_PRESSURE_STEAM, 425.0, 383.0);
    assertEquals(UtilityLevel.LOW_PRESSURE_STEAM, steam.getUtilityLevel());
    assertEquals(425.0, steam.getSupplyTemperature(), 1.0e-12);
    assertEquals(383.0, steam.getReturnTemperature(), 1.0e-12);
  }

  private static EnergyPort port(String owner, EnergyType type, EnergyPortDirection direction, EnergyPortMode mode,
      EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", type, direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }
}
