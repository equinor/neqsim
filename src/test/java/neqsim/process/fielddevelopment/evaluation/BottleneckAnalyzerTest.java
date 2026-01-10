package neqsim.process.fielddevelopment.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.evaluation.BottleneckAnalyzer.BottleneckResult;
import neqsim.process.fielddevelopment.evaluation.BottleneckAnalyzer.DebottleneckOption;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for BottleneckAnalyzer.
 *
 * @author ESOL
 * @version 1.0
 */
public class BottleneckAnalyzerTest {
  private ProcessSystem facility;
  private BottleneckAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    facility = new ProcessSystem();

    SystemInterface fluid = new SystemSrkEos(288.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("nC10", 0.15);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50000, "kg/hr");

    Separator separator = new Separator("V-100", feed);

    Compressor compressor = new Compressor("K-100", separator.getGasOutStream());
    compressor.setOutletPressure(100.0);

    facility.add(feed);
    facility.add(separator);
    facility.add(compressor);
    facility.run();

    analyzer = new BottleneckAnalyzer(facility);
  }

  @Test
  void testIdentifyBottlenecks() {
    List<BottleneckResult> bottlenecks = analyzer.identifyBottlenecks();
    assertNotNull(bottlenecks, "Bottleneck list should not be null");
    assertFalse(bottlenecks.isEmpty(), "Should identify some equipment");
  }

  @Test
  void testBottlenecksSortedByUtilization() {
    List<BottleneckResult> bottlenecks = analyzer.identifyBottlenecks();
    if (bottlenecks.size() > 1) {
      for (int i = 1; i < bottlenecks.size(); i++) {
        assertTrue(bottlenecks.get(i - 1).getUtilization() >= bottlenecks.get(i).getUtilization(),
            "Bottlenecks should be sorted by utilization (descending)");
      }
    }
  }

  @Test
  void testUtilizationThreshold() {
    analyzer.setUtilizationThreshold(0.70);
    List<BottleneckResult> active = analyzer.getActiveBottlenecks();
    for (BottleneckResult result : active) {
      assertTrue(result.getUtilization() >= 0.70, "Active bottlenecks should exceed threshold");
    }
  }

  @Test
  void testDebottleneckOptions() {
    List<BottleneckResult> bottlenecks = analyzer.identifyBottlenecks();
    for (BottleneckResult bn : bottlenecks) {
      List<DebottleneckOption> options =
          analyzer.evaluateDebottleneckOptions(bn.getEquipmentName(), 0.20);
      assertNotNull(options, "Options list should not be null");
    }
  }

  @Test
  void testReportGeneration() {
    String report = analyzer.generateReport();
    assertNotNull(report, "Report should not be null");
    assertTrue(report.contains("Bottleneck"), "Report should contain 'Bottleneck'");
  }

  @Test
  void testEmptySystem() {
    ProcessSystem emptySystem = new ProcessSystem();
    BottleneckAnalyzer emptyAnalyzer = new BottleneckAnalyzer(emptySystem);
    List<BottleneckResult> results = emptyAnalyzer.identifyBottlenecks();
    assertNotNull(results, "Results should not be null for empty system");
    assertTrue(results.isEmpty(), "Empty system should have no bottlenecks");
  }

  @Test
  void testNullSystemThrowsException() {
    try {
      new BottleneckAnalyzer(null);
      assertTrue(false, "Should throw exception for null facility");
    } catch (IllegalArgumentException e) {
      assertTrue(true, "Correctly threw exception");
    }
  }
}
