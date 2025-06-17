package automatic.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.util.unit.NeqSimUnitSet;
import neqsim.util.unit.Units;

class UnitsActivationTest {
  @Test
  void testSetNeqSimUnits() {
    NeqSimUnitSet.setNeqSimUnits("SI");
    assertEquals("Pa", Units.getSymbol("pressure"));

    NeqSimUnitSet.setNeqSimUnits("field");
    assertEquals("psia", Units.getSymbol("pressure"));

    NeqSimUnitSet.setNeqSimUnits("default");
    assertEquals("bara", Units.getSymbol("pressure"));

    NeqSimUnitSet.setNeqSimUnits("invalid");
    assertEquals("bara", Units.getSymbol("pressure"));
  }
}
