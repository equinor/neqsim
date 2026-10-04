package neqsim.process.util.combustion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Native oil-side duty and inlet-preservation checks, separate from chemical mechanism qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HotOilHeatBalanceTest {
  /** Verify physical oil heating, independent enthalpy closure and unchanged inlet state. */
  @Test
  void closesOilDutyWithoutMutatingInlet() {
    Stream oil = inlet();
    oil.getThermoSystem().init(2);
    double originalEnthalpy = oil.getThermoSystem().getEnthalpy();
    HotOilHeatBalance.Result result = HotOilHeatBalance.applyWithClosure(oil, 20.0e6, "heated oil", UUID.randomUUID());
    StreamInterface outlet = result.getOutlet();
    assertTrue(outlet.getTemperature() > 560.0 && outlet.getTemperature() < 563.0);
    assertEquals(20.0, outlet.getPressure(), 1.0e-12);
    assertEquals(100.0, outlet.getFlowRate("kg/sec"), 1.0e-9);
    assertEquals(20.0e6, outlet.getThermoSystem().getEnthalpy() - originalEnthalpy, 1.0);
    assertTrue(result.getRelativeDutyResidual() < 1.0e-7);
    assertEquals(500.0, oil.getTemperature(), 1.0e-12);
    assertEquals(originalEnthalpy, oil.getThermoSystem().getEnthalpy(), 1.0e-9);
  }

  /** Verify zero heat preserves the temperature without an unnecessary energy specification. */
  @Test
  void preservesZeroDutyState() {
    HotOilHeatBalance.Result result = HotOilHeatBalance.applyWithClosure(inlet(), 0.0, "unheated oil",
        UUID.randomUUID());
    assertEquals(500.0, result.getOutlet().getTemperature(), 1.0e-12);
    assertEquals(0.0, result.getAbsorbedDutyW(), 1.0e-9);
  }

  /** Reject undefined duties and chemical systems before they become utility outlets. */
  @Test
  void rejectsInvalidDutyAndReactiveOil() {
    Stream oil = inlet();
    assertThrows(IllegalArgumentException.class,
        () -> HotOilHeatBalance.apply(oil, Double.NaN, "invalid", UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class,
        () -> HotOilHeatBalance.apply(oil, -1.0, "invalid", UUID.randomUUID()));
    oil.getThermoSystem().isChemicalSystem(true);
    assertThrows(IllegalArgumentException.class,
        () -> HotOilHeatBalance.apply(oil, 1.0e6, "reactive oil", UUID.randomUUID()));
  }

  /**
   * Create a reproducible nonreactive hydrocarbon surrogate for the oil utility.
   *
   * @return flashed 100 kg/s n-decane stream at 500 K and 20 bara
   */
  private static Stream inlet() {
    SystemSrkEos fluid = new SystemSrkEos(500.0, 20.0);
    fluid.addComponent("n-decane", 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    fluid.setTotalFlowRate(100.0, "kg/sec");
    Stream oil = new Stream("oil inlet", fluid);
    oil.run(UUID.randomUUID());
    return oil;
  }
}
