package neqsim.process.operations.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link OperationalEnvelopeEvaluator}.
 *
 * @author ESOL
 * @version 1.0
 */
class OperationalEnvelopeEvaluatorTest {

  /**
   * Verifies that capacity constraints are converted into ranked margins and suggestions.
   */
  @Test
  void evaluateRanksCapacityMarginsAndSuggestsMitigation() {
    ProcessSystem process = createProcess(78.0);

    OperationalEnvelopeReport report = OperationalEnvelopeEvaluator.evaluate(process);

    assertTrue(report.getMargins().size() > 0, report.toJson());
    assertTrue(report.getWarningOrWorseCount() > 0, report.toJson());
    assertFalse(report.getMitigationSuggestions().isEmpty(), report.toJson());
    assertTrue(report.toJsonObject().has("rankedMargins"));
  }

  /**
   * Verifies that decreasing margin history generates a trip prediction.
   */
  @Test
  void evaluatePredictsTripFromDecreasingMarginHistory() {
    ProcessSystem process = createProcess(72.0);
    Map<String, MarginTrendTracker> history = new LinkedHashMap<String, MarginTrendTracker>();
    MarginTrendTracker tracker = new MarginTrendTracker("Outlet Valve.valveOpening")
        .addSample(0.0, 35.0).addSample(60.0, 25.0).addSample(120.0, 15.0);
    history.put(tracker.getMarginKey(), tracker);

    OperationalEnvelopeReport report = OperationalEnvelopeEvaluator.evaluate(process, history,
        1800.0, true);

    assertFalse(report.getTripPredictions().isEmpty(), report.toJson());
    assertEquals("Outlet Valve", report.getTripPredictions().get(0).getEquipmentName());
  }

  /**
   * Creates a simple separator and outlet valve process.
   *
   * @param valveOpening valve opening in percent
   * @return initialized process system
   */
  private ProcessSystem createProcess(double valveOpening) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setPressure(70.0, "bara");
    feed.setTemperature(25.0, "C");

    Separator separator = new Separator("Separator", feed);
    ThrottlingValve valve = new ThrottlingValve("Outlet Valve", separator.getGasOutStream());
    valve.setOutletPressure(50.0);
    valve.setPercentValveOpening(valveOpening);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(valve);
    process.run();
    return process;
  }
}