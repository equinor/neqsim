package neqsim.process.util.heatintegration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for PinchAnalyzer.
 */
public class PinchAnalyzerTest {

  @Test
  public void testManualStreamsAnalysis() {
    ProcessSystem process = new ProcessSystem();
    PinchAnalyzer analyzer = new PinchAnalyzer(process);

    // Add hot streams (need cooling)
    analyzer.addHotStream("Hot1", 473.15, 323.15, 150000.0); // 200C -> 50C, 150 kW
    analyzer.addHotStream("Hot2", 423.15, 303.15, 120000.0); // 150C -> 30C, 120 kW

    // Add cold streams (need heating)
    analyzer.addColdStream("Cold1", 293.15, 393.15, 100000.0); // 20C -> 120C, 100 kW
    analyzer.addColdStream("Cold2", 313.15, 443.15, 130000.0); // 40C -> 170C, 130 kW

    analyzer.setMinApproachTemperature(10.0);
    analyzer.analyze();

    // Should have calculated pinch point
    assertFalse(Double.isNaN(analyzer.getPinchTemperature()));

    // Min utility duties should be non-negative
    assertTrue(analyzer.getMinHotUtilityDuty() >= 0.0);
    assertTrue(analyzer.getMinColdUtilityDuty() >= 0.0);

    // Should suggest matches
    assertFalse(analyzer.getMatches().isEmpty());

    // Energy recovery should be between 0 and 1
    double recovery = analyzer.getEnergyRecoveryFraction();
    assertTrue(recovery >= 0.0 && recovery <= 1.0);
  }

  @Test
  public void testCompositeCurves() {
    ProcessSystem process = new ProcessSystem();
    PinchAnalyzer analyzer = new PinchAnalyzer(process);

    analyzer.addHotStream("Hot1", 400.0, 300.0, 50000.0);
    analyzer.addColdStream("Cold1", 280.0, 380.0, 50000.0);

    analyzer.analyze();

    assertFalse(analyzer.getHotCompositeCurve().isEmpty());
    assertFalse(analyzer.getColdCompositeCurve().isEmpty());
    assertFalse(analyzer.getGrandCompositeCurve().isEmpty());
  }

  @Test
  public void testAutoExtractFromProcess() {
    SystemInterface gas = new SystemSrkEos(273.15 + 80.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");

    Cooler cooler = new Cooler("cooler", feed);
    cooler.setOutTemperature(273.15 + 30.0);

    SystemInterface cold = new SystemSrkEos(273.15 + 20.0, 50.0);
    cold.addComponent("methane", 0.9);
    cold.addComponent("ethane", 0.1);
    cold.setMixingRule("classic");

    Stream coldFeed = new Stream("cold feed", cold);
    coldFeed.setFlowRate(8000.0, "kg/hr");

    Heater heater = new Heater("heater", coldFeed);
    heater.setOutTemperature(273.15 + 60.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cooler);
    process.add(coldFeed);
    process.add(heater);
    process.run();

    PinchAnalyzer analyzer = new PinchAnalyzer(process);
    analyzer.setMinApproachTemperature(5.0);
    analyzer.analyze();

    // Should have found both hot and cold streams
    assertEquals(1, analyzer.getHotStreams().size());
    assertEquals(1, analyzer.getColdStreams().size());

    assertTrue(analyzer.getTotalRecoverableEnergy() >= 0.0);
  }

  @Test
  public void testJsonOutput() {
    ProcessSystem process = new ProcessSystem();
    PinchAnalyzer analyzer = new PinchAnalyzer(process);

    analyzer.addHotStream("Hot1", 400.0, 300.0, 50000.0);
    analyzer.addColdStream("Cold1", 280.0, 380.0, 40000.0);
    analyzer.analyze();

    String json = analyzer.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Pinch Analysis"));
    assertTrue(json.contains("hotStreams"));
    assertTrue(json.contains("coldStreams"));
    assertTrue(json.contains("heatExchangerMatches"));
  }

  @Test
  public void testEmptyProcess() {
    ProcessSystem process = new ProcessSystem();
    PinchAnalyzer analyzer = new PinchAnalyzer(process);
    analyzer.analyze();

    assertTrue(Double.isNaN(analyzer.getPinchTemperature()));
    assertEquals(0.0, analyzer.getMinHotUtilityDuty(), 1e-6);
    assertTrue(analyzer.getMatches().isEmpty());
  }
}
