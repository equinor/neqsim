package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.tank.Tank;

/** Tests vessel-backed level measurement compatibility. */
class LevelTransmitterTest {

  @Test
  void readsSeparatorLevelThroughExistingApi() {
    Separator separator = new Separator("20-VA-001");
    LevelTransmitter transmitter = new LevelTransmitter("LT-2001", separator);

    assertSame(separator, transmitter.getSeparator());
    assertSame(separator, transmitter.getVessel());
    assertEquals(separator.getLiquidLevel(), transmitter.getMeasuredValue(""), 1.0e-12);
  }

  @Test
  void readsTankLevelThroughGenericVesselApi() {
    Tank tank = new Tank("20-TK-001");
    LevelTransmitter transmitter = new LevelTransmitter("LT-2002", tank);

    assertSame(tank, transmitter.getTank());
    assertSame(tank, transmitter.getVessel());
    assertEquals(tank.getLiquidLevel(), transmitter.getMeasuredValue(""), 1.0e-12);
  }

  @Test
  void rejectsUnsupportedEquipmentAndUnits() {
    Tank tank = new Tank("20-TK-002");
    LevelTransmitter transmitter = new LevelTransmitter(tank);

    assertThrows(IllegalArgumentException.class,
        () -> new LevelTransmitter("LT-INVALID", (ProcessEquipmentInterface) null));
    assertThrows(RuntimeException.class, () -> transmitter.getMeasuredValue("%"));
  }
}
