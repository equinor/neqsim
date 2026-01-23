package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;

/**
 * Tests for
 * {@link ProcessSystem#replaceObject(String, neqsim.process.equipment.ProcessEquipmentBaseClass)}.
 */
public class ProcessSystemReplaceObjectTest extends neqsim.NeqSimTest {
  @Test
  public void replaceExistingUnitKeepsPositionAndName() {
    ProcessSystem system = new ProcessSystem();
    system.add(new Separator("first"));
    system.add(new Cooler("second"));

    Pump replacement = new Pump("replacement");

    system.replaceObject("second", replacement);

    assertSame(replacement, system.getUnitOperations().get(1));
    assertEquals("second", replacement.getName(),
        "Replacement should adopt the original unit name");
    assertSame(replacement, system.getUnit("second"));
    assertNotNull(system.getUnit("first"));
  }

  @Test
  public void replaceNonExistingUnitThrows() {
    ProcessSystem system = new ProcessSystem();
    system.add(new Separator("first"));

    Pump replacement = new Pump("replacement");

    assertThrows(IllegalArgumentException.class,
        () -> system.replaceObject("unknown", replacement));
  }
}
