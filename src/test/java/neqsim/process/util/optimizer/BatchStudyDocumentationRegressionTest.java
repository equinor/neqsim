package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.BatchStudy.BatchStudyResult;
import neqsim.process.util.optimizer.BatchStudy.CaseResult;
import neqsim.process.util.optimizer.BatchStudy.Objective;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies the public batch-study paths used in the production optimization guide. */
class BatchStudyDocumentationRegressionTest {
  private ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(320.0, "K");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    return process;
  }

  @Test
  void ranksMaximizationAndMixedDirectionParetoUsingTheDeclaredDirections() {
    BatchStudyResult result = BatchStudy.builder(process())
        .vary("feed.flowRate", new double[] { 1000.0, 2000.0, 3000.0 })
        .addObjective("flow", Objective.MAXIMIZE, proc -> ((Stream) proc.getUnit("feed")).getFlowRate("kg/hr"))
        .addObjective("cost", Objective.MINIMIZE, proc -> ((Stream) proc.getUnit("feed")).getFlowRate("kg/hr") * 0.1)
        .parallelism(2).build().run();
    assertEquals(3, result.getSuccessCount());
    assertEquals(3000.0, result.getBestCase("flow").objectiveValues.get("flow"), 1e-8);
    assertEquals(100.0, result.getBestCase("cost").objectiveValues.get("cost"), 1e-8);
    List<CaseResult> front = result.getParetoFront("cost", "flow");
    assertEquals(3, front.size(), "Each higher flow costs more, so all three are nondominated");
  }

  @Test
  void preservesCelsiusParameterUnitsAndTheBaseCase() {
    ProcessSystem base = process();
    base.run();
    double original = ((Heater) base.getUnit("heater")).getOutletStream().getTemperature("K");
    BatchStudyResult result = BatchStudy.builder(base).vary("heater.outletTemperature", new double[] { 40.0, 60.0 })
        .addObjective("temperatureK", Objective.MINIMIZE,
            proc -> ((Heater) proc.getUnit("heater")).getOutletStream().getTemperature("K"))
        .parallelism(1).build().run();
    assertEquals(2, result.getSuccessCount());
    assertEquals(313.15, result.getBestCase("temperatureK").objectiveValues.get("temperatureK"), 1e-6);
    assertEquals(original, ((Heater) base.getUnit("heater")).getOutletStream().getTemperature("K"), 1e-8);
  }

  @Test
  void unsupportedPathsFailCasesAndSequentialStopOnFailureStops() {
    for (String path : new String[] { "pressure", "missing.flowRate", "heater.unknown", "feed.duty" }) {
      BatchStudyResult result = BatchStudy.builder(process()).vary(path, new double[] { 1.0, 2.0 }).parallelism(1)
          .stopOnFailure(true).build().run();
      assertEquals(0, result.getSuccessCount(), path);
      assertEquals(1, result.getFailureCount(), path);
      assertEquals(1, result.getAllResults().size(), path);
      assertNotNull(result.getAllResults().get(0).errorMessage);
    }
  }

  @Test
  void invalidObjectiveDoesNotBecomeASuccessfulOrParetoCase() {
    BatchStudyResult result = BatchStudy.builder(process()).vary("feed.flowRate", new double[] { 1000.0, 2000.0 })
        .addObjective("invalid", Objective.MINIMIZE, proc -> Double.NaN).parallelism(1).build().run();
    assertEquals(0, result.getSuccessCount());
    assertEquals(2, result.getFailureCount());
    assertTrue(result.getParetoFront("invalid", "invalid").isEmpty());
  }

  @Test
  void validatesRangeAndRetainsExplicitSingleCase() {
    assertThrows(IllegalArgumentException.class,
        () -> BatchStudy.builder(process()).vary("feed.flowRate", 1000.0, 2000.0, 1));
    assertThrows(IllegalArgumentException.class,
        () -> BatchStudy.builder(process()).vary("feed.flowRate", new double[] { Double.NaN }));
    assertThrows(IllegalArgumentException.class, () -> BatchStudy.builder(process()).parallelism(0));
    BatchStudyResult result = BatchStudy.builder(process()).vary("feed.flowRate", new double[] { 1000.0 })
        .parallelism(1).build().run();
    assertEquals(1, result.getSuccessCount());
  }

  @Test
  void exportsRuntimeWithoutReflectiveAccessToJavaTimeInternals() {
    BatchStudyResult result = BatchStudy.builder(process()).vary("feed.flowRate", new double[] { 1000.0 })
        .parallelism(1).build().run();
    com.google.gson.JsonObject json = JsonParser.parseString(result.toJson()).getAsJsonObject();
    assertEquals(1, json.get("successCount").getAsInt());
    assertTrue(json.get("startTime").getAsString().endsWith("Z"));
    assertTrue(json.getAsJsonArray("results").get(0).getAsJsonObject().get("runtime").getAsString().startsWith("PT"));
  }
}
