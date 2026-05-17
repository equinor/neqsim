package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import java.util.List;

/**
 * Tests for DebottleneckAnalyzer.
 */
public class DebottleneckAnalyzerTest {

  @Test
  public void testAnalyzeProcess() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 60.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");

    Separator sep = new Separator("HP Separator", feed);

    Cooler cooler = new Cooler("Gas Cooler", sep.getGasOutStream());
    cooler.setOutTemperature(273.15 + 25.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(cooler);
    process.run();

    DebottleneckAnalyzer analyzer = new DebottleneckAnalyzer(process);
    analyzer.setWarningThreshold(0.85);
    analyzer.setCriticalThreshold(0.95);
    analyzer.analyze();

    // Should complete without error
    double util = analyzer.getOverallUtilization();
    assertTrue(util >= 0.0);

    // Should have analyzed some equipment
    List<DebottleneckAnalyzer.EquipmentStatus> ranked = analyzer.getRankedEquipment();
    assertNotNull(ranked);
  }

  @Test
  public void testJsonOutput() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 60.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");

    Separator sep = new Separator("Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    DebottleneckAnalyzer analyzer = new DebottleneckAnalyzer(process);
    analyzer.analyze();

    String json = analyzer.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Debottleneck Analysis"));
    assertTrue(json.contains("equipment"));
    assertTrue(json.contains("overallUtilization"));
  }

  @Test
  public void testConstrainedEquipmentFiltering() {
    ProcessSystem process = new ProcessSystem();
    DebottleneckAnalyzer analyzer = new DebottleneckAnalyzer(process);
    analyzer.setWarningThreshold(0.90);
    analyzer.analyze();

    List<DebottleneckAnalyzer.EquipmentStatus> constrained = analyzer.getConstrainedEquipment();
    assertNotNull(constrained);
    assertEquals(0, analyzer.getOverloadedCount());
  }
}
