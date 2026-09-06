package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for sizing a valve at an opening different from its current position. */
class ValveAutoSizeOpeningRegressionTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @CsvSource({ "100.0, 50.0, methane, linear", "30.0, 70.0, methane, linear", "70.0, 30.0, methane, linear",
      "100.0, 50.0, methane, equal percentage", "100.0, 50.0, water, linear", "30.0, 70.0, water, equal percentage" })
  void requestedDesignOpeningPreservesDesignFlow(double initialOpening, double designOpening, String component,
      String characteristic) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent(component, 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    ThrottlingValve valve = new ThrottlingValve("sized valve", feed);
    valve.setOutletPressure(40.0);
    valve.setPercentValveOpening(initialOpening);
    valve.getMechanicalDesign().setValveCharacterization(characteristic);

    valve.autoSize(1.0, designOpening);
    assertEquals(designOpening, valve.getPercentValveOpening(), 1.0e-12);
    valve.setCalculateSteadyState(false);
    valve.runTransient(1.0, UUID.randomUUID());

    assertEquals(10000.0, valve.getOutletStream().getFlowRate("kg/hr"), 1.0,
        "The requested opening must carry the design flow after sizing");
  }

  @ParameterizedTest
  @CsvSource({ "NaN, 50.0", "0.0, 50.0", "1.0, NaN", "1.0, 0.0", "1.0, 101.0" })
  void invalidDesignIsRejectedBeforeChangingTheValve(double safetyFactor, double opening) {
    ThrottlingValve valve = new ThrottlingValve("unconnected valve");
    assertThrows(IllegalArgumentException.class, () -> valve.autoSize(safetyFactor, opening));
  }
}
