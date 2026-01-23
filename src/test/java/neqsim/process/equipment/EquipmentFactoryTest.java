package neqsim.process.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    ProcessEquipmentInterface equipment =
        EquipmentFactory.createEquipment("valve1", EquipmentEnum.ThrottlingValve);

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
    assertThrows(IllegalArgumentException.class,
        () -> EquipmentFactory.createEquipment("ej", "ejector"));

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
}
