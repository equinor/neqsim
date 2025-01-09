package neqsim.process.equipment.pump;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PumpChartTest {
  @Test
  void testSetHeadUnit() {
    PumpChart pc = new PumpChart();
    String origUnit = pc.getHeadUnit();
    Assertions.assertEquals("meter", origUnit);
    String newUnit = "kJ/kg";
    pc.setHeadUnit(newUnit);
    Assertions.assertEquals(newUnit, pc.getHeadUnit());
    pc.setHeadUnit(origUnit);
    Assertions.assertEquals(origUnit, pc.getHeadUnit());

    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      pc.setHeadUnit("doesNotExist");
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: PumpChart:setHeadUnit - Input headUnit does not support value doesNotExist",
        thrown.getMessage());
  }
}
