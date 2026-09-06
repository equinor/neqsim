package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Forward/inverse consistency for downhill, flat, and uphill liquid pipelines. */
class PipeInletPressureRegressionTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @ValueSource(doubles = { -5.0, 0.0, 5.0 })
  void inverseRecoversForwardPressureAndRejectsUnconvergedTrials(double angle) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("water feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("downhill line", feed);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.setAngle(angle);
    pipe.setNumberOfIncrements(10);
    pipe.run();
    double arrivalPressure = pipe.getOutletStream().getPressure("bara");
    if (angle < 0.0) {
      assertTrue(arrivalPressure > 35.0, "The downhill liquid head must exceed friction losses");
    }

    feed.setPressure(45.0, "bara");
    pipe.setOutletPressure(arrivalPressure, "bara");
    pipe.setCalculationMode(PipeBeggsAndBrills.CalculationMode.CALCULATE_INLET_PRESSURE);
    pipe.run();

    assertEquals(30.0, pipe.getSolvedInletPressure(), 0.01,
        "The inverse must recover the known forward inlet, even below the arrival pressure");
    assertEquals(arrivalPressure, pipe.getOutletStream().getPressure("bara"), 0.01);

    feed.setPressure(45.0, "bara");
    pipe.setMaxFlowIterations(1);
    assertThrows(IllegalStateException.class, () -> pipe.run(),
        "An exhausted iteration budget must not publish the last trial as a solved pressure");
    assertTrue(Double.isNaN(pipe.getSolvedInletPressure()));
    assertEquals(45.0, feed.getPressure("bara"), 1.0e-12);
  }
}
