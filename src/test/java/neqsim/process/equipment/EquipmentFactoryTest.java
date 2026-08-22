package neqsim.process.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ejector.Ejector;
import neqsim.process.equipment.powergeneration.WindTurbine;
import neqsim.process.equipment.reservoir.ReservoirCVDsim;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.GORfitter;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link EquipmentFactory}.
 */
public class EquipmentFactoryTest extends neqsim.NeqSimTest {
  @Test
  public void createEquipmentFromEnum() {
    ProcessEquipmentInterface equipment = EquipmentFactory.createEquipment("valve1", EquipmentEnum.ThrottlingValve);

    assertInstanceOf(ThrottlingValve.class, equipment);
    assertEquals("valve1", equipment.getName());
  }

  @Test
  public void createEquipmentFromStringAlias() {
    ProcessEquipmentInterface equipment = EquipmentFactory.createEquipment("wt", "windturbine");

    assertInstanceOf(WindTurbine.class, equipment);
    assertEquals("wt", equipment.getName());
  }

  @Test
  public void ejectorRequiresStreams() {
    assertThrows(IllegalArgumentException.class, () -> EquipmentFactory.createEquipment("ej", "ejector"));

    StreamInterface motive = new Stream("motive");
    StreamInterface suction = new Stream("suction");

    Ejector ejector = EquipmentFactory.createEjector("ej", motive, suction);
    assertEquals("ej", ejector.getName());
  }

  @Test
  public void gorfitterRequiresInletStream() {
    assertThrows(IllegalArgumentException.class,
        () -> EquipmentFactory.createEquipment("gor", EquipmentEnum.GORfitter));

    StreamInterface inlet = new Stream("inlet");
    GORfitter fitter = EquipmentFactory.createGORfitter("gor", inlet);
    assertEquals("gor", fitter.getName());
  }

  @Test
  public void reservoirSimRequiresFluid() {
    assertThrows(IllegalArgumentException.class,
        () -> EquipmentFactory.createEquipment("cvd", EquipmentEnum.ReservoirCVDsim));

    SystemInterface fluid = new SystemSrkEos(273.15, 100.0);
    fluid.addComponent("methane", 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    ReservoirCVDsim simulator = EquipmentFactory.createReservoirCVDsim("cvd", fluid);
    assertNotNull(simulator);
    assertEquals("cvd", simulator.getName());
  }

  @Test
  public void createEquipmentByClassNameReflectionFallback() {
    ProcessEquipmentInterface controlValve = EquipmentFactory.createEquipment("cv", "ControlValve");
    assertInstanceOf(neqsim.process.equipment.valve.ControlValve.class, controlValve);
    assertEquals("cv", controlValve.getName());

    ProcessEquipmentInterface airCooler = EquipmentFactory.createEquipment("ac", "AirCooler");
    assertInstanceOf(neqsim.process.equipment.heatexchanger.AirCooler.class, airCooler);
    assertEquals("ac", airCooler.getName());

    ProcessEquipmentInterface orifice = EquipmentFactory.createEquipment("or", "Orifice");
    assertInstanceOf(neqsim.process.equipment.diffpressure.Orifice.class, orifice);
    assertEquals("or", orifice.getName());

    ProcessEquipmentInterface train = EquipmentFactory.createEquipment("ct", "CompressorTrain");
    assertInstanceOf(neqsim.process.equipment.compressor.CompressorTrain.class, train);
    assertEquals("ct", train.getName());
  }

  @Test
  public void createEquipmentFromAutomaticallyDiscoveredPackages() {
    ProcessEquipmentInterface motor = EquipmentFactory.createEquipment("motor", "ElectricMotor");
    assertInstanceOf(neqsim.process.equipment.energy.ElectricMotor.class, motor);

    ProcessEquipmentInterface preparation = EquipmentFactory.createEquipment("preparation", "BioFeedstockPreparation");
    assertInstanceOf(neqsim.process.equipment.solidhandling.BioFeedstockPreparation.class, preparation);

    ProcessEquipmentInterface converter = EquipmentFactory.createEquipment("converter", "ClausCatalyticConverter");
    assertInstanceOf(neqsim.process.equipment.reactor.sulfurrecovery.ClausCatalyticConverter.class, converter);
  }

  @Test
  public void constructibleEquipmentInventoryIncludesAutomaticallyDiscoveredPackages() {
    assertTrue(EquipmentFactory.getConstructibleEquipmentTypes().contains("ElectricMotor"));
    assertTrue(EquipmentFactory.getConstructibleEquipmentTypes().contains("BioFeedstockPreparation"));
    assertTrue(EquipmentFactory.getConstructibleEquipmentTypes().contains("ClausCatalyticConverter"));
  }

  @Test
  public void supportedEquipmentInventoryExcludesContextDependentTypes() {
    assertTrue(EquipmentFactory.supportsEquipmentType("ElectricMotor"));
    assertTrue(EquipmentFactory.supportsEquipmentType("DistillationColumn"));
    assertFalse(EquipmentFactory.supportsEquipmentType("Ejector"));
    assertFalse(EquipmentFactory.supportsEquipmentType("GORfitter"));
    assertFalse(EquipmentFactory.supportsEquipmentType("ReservoirCVDsim"));
    assertTrue(EquipmentFactory.getSupportedEquipmentTypes().contains("ElectricMotor"));
    assertTrue(EquipmentFactory.getSupportedEquipmentTypes().contains("DistillationColumn"));
    assertFalse(EquipmentFactory.getSupportedEquipmentTypes().contains("Ejector"));
  }

  @Test
  public void everyAdvertisedEquipmentTypeIsNameOnlyConstructible() {
    for (String equipmentType : EquipmentFactory.getSupportedEquipmentTypes()) {
      ProcessEquipmentInterface equipment = EquipmentFactory.createEquipment("audit unit", equipmentType);
      assertNotNull(equipment, equipmentType + " was advertised but could not be constructed");
      assertEquals("audit unit", equipment.getName());
    }
  }

  @Test
  public void columnEnumMapsToDistillationColumn() {
    ProcessEquipmentInterface column = EquipmentFactory.createEquipment("col", EquipmentEnum.Column);
    assertInstanceOf(neqsim.process.equipment.distillation.DistillationColumn.class, column);
    assertEquals("col", column.getName());
  }

  @Test
  public void unknownEquipmentTypeThrows() {
    assertThrows(IllegalArgumentException.class, () -> EquipmentFactory.createEquipment("x", "NotARealEquipmentClass"));
  }
}
