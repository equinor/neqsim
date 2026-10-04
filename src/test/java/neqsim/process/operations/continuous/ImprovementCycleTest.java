package neqsim.process.operations.continuous;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the living-task classes: drift monitor, baseline comparator and improvement cycle.
 *
 * @author ESOL
 * @version 1.0
 */
public class ImprovementCycleTest {

  @Test
  void driftMonitorIgnoresNoiseAndDetectsStep() {
    Random random = new Random(1);
    ModelDriftMonitor monitor = new ModelDriftMonitor();
    for (int i = 0; i < 200; i++) {
      assertFalse(monitor.update("x", 10.0 + random.nextGaussian()).get("newAlarm").getAsBoolean());
    }
    boolean detected = false;
    for (int i = 0; i < 5; i++) {
      detected |= monitor.update("x", 14.0 + random.nextGaussian()).get("newAlarm").getAsBoolean();
    }
    assertTrue(detected);
    assertTrue(monitor.getState("x").isAlarm());
    monitor.reset("x");
    assertFalse(monitor.getState("x").isAlarm());
  }

  @Test
  void engineeringFloorSuppressesIrrelevantShift() {
    Random random = new Random(2);
    ModelDriftMonitor monitor = new ModelDriftMonitor().setMinSigma("x", 10.0);
    for (int i = 0; i < 30; i++) {
      monitor.update("x", 10.0 + random.nextGaussian());
    }
    for (int i = 0; i < 20; i++) {
      assertFalse(monitor.update("x", 14.0 + random.nextGaussian()).get("alarm").getAsBoolean());
    }
    assertThrows(IllegalArgumentException.class, () -> monitor.setLambda(0.0));
  }

  @Test
  void comparatorFiresOnCrossingOnly() {
    Map<String, Double> baseline = new HashMap<String, Double>();
    baseline.put("eta", 0.80);
    baseline.put("p", 30.0);
    BaselineComparator comparator = new BaselineComparator(baseline).addStepTrigger("p", 2.0).addCriterion("eta",
        "< 0.785");
    Map<String, Double> now = new HashMap<String, Double>();
    now.put("eta", 0.79);
    now.put("p", 29.5);
    assertEquals(0, comparator.compare(now).getAsJsonArray("triggers").size());
    now.put("eta", 0.78);
    now.put("p", 27.0);
    JsonArray triggers = comparator.compare(now).getAsJsonArray("triggers");
    assertEquals(2, triggers.size());
    assertEquals(0, comparator.compare(now).getAsJsonArray("triggers").size());
    assertThrows(IllegalArgumentException.class, () -> comparator.addCriterion("eta", "about 0.8"));
  }

  @Test
  void improvementCycleRunsModelAndRaisesTriggers() {
    SystemSrkEos gas = new SystemSrkEos(273.15 + 30.0, 30.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");
    Compressor compressor = new Compressor("comp", feed);
    compressor.setOutletPressure(90.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.run();

    ImprovementCycle cycle = new ImprovementCycle(process.getAutomation());
    cycle.addKpi("comp.power", "kW");
    double basePower = compressor.getPower("kW");
    Map<String, Double> baseline = Collections.singletonMap("comp.power", basePower);
    cycle.setComparator(new BaselineComparator(baseline).addStepTrigger("comp.power", 0.05 * basePower));
    cycle.setDriftMonitor(new ModelDriftMonitor().setWarmup(5, 1).setMinSigma("comp.power", 1.0));

    for (int i = 0; i < 5; i++) {
      JsonObject result = JsonParser.parseString(cycle.runCycle(null, null)).getAsJsonObject();
      assertTrue(result.get("feasible").getAsBoolean());
      assertEquals(0, result.getAsJsonArray("triggers").size());
    }
    Map<String, Double> lowerSuction = Collections.singletonMap("feed.pressure", 24.0);
    JsonObject result = JsonParser.parseString(cycle.runCycle(lowerSuction, "bara")).getAsJsonObject();
    assertTrue(result.get("feasible").getAsBoolean(), result.toString());
    String triggers = result.getAsJsonArray("triggers").toString();
    assertTrue(triggers.contains("kpi_step:comp.power"), triggers);
    assertTrue(triggers.contains("drift:comp.power"), triggers);
    assertTrue(result.getAsJsonObject("kpis").get("comp.power").getAsDouble() > basePower);
  }
}
