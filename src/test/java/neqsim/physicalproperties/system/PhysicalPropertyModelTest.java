package neqsim.physicalproperties.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class PhysicalPropertyModelTest {
  @Test
  void testByName() {
    assertEquals(PhysicalPropertyModel.AMINE, PhysicalPropertyModel.byName("amine"));
    assertEquals(PhysicalPropertyModel.BASIC, PhysicalPropertyModel.byName("basic"));
    assertEquals(PhysicalPropertyModel.CO2WATER, PhysicalPropertyModel.byName("co2water"));
    assertEquals(PhysicalPropertyModel.DEFAULT, PhysicalPropertyModel.byName("default"));
    assertEquals(PhysicalPropertyModel.GLYCOL, PhysicalPropertyModel.byName("glycol"));
    assertEquals(PhysicalPropertyModel.WATER, PhysicalPropertyModel.byName("water"));
  }

  @Test
  void testByValue() {
    assertEquals(PhysicalPropertyModel.AMINE, PhysicalPropertyModel.byValue(3));
    assertEquals(PhysicalPropertyModel.BASIC, PhysicalPropertyModel.byValue(6));
    assertEquals(PhysicalPropertyModel.CO2WATER, PhysicalPropertyModel.byValue(4));
    assertEquals(PhysicalPropertyModel.DEFAULT, PhysicalPropertyModel.byValue(0));
    assertEquals(PhysicalPropertyModel.GLYCOL, PhysicalPropertyModel.byValue(2));
    assertEquals(PhysicalPropertyModel.WATER, PhysicalPropertyModel.byValue(1));
  }
}
