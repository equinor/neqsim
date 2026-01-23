package neqsim.process.util.sensitivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.sensitivity.ProcessSensitivityAnalyzer.VariableSpec;
import neqsim.process.util.uncertainty.SensitivityMatrix;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ProcessSensitivityAnalyzer.
 */
class ProcessSensitivityAnalyzerTest {
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    testFluid = new SystemSrkEos(298.0, 50.0);
    testFluid.addComponent("methane", 0.8);
    testFluid.addComponent("ethane", 0.15);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");
  }

  @Nested
  @DisplayName("Fluent API Tests")
  class FluentApiTests {
    @DisplayName("Can add inputs and outputs")
    void testAddInputsOutputs() {
      ProcessSystem process = new ProcessSystem();
      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

      analyzer.withInput("feed", "temperature").withInput("feed", "pressure")
          .withOutput("product", "temperature").withOutput("product", "flowRate");

      // No exception thrown means success
      assertNotNull(analyzer);
    }

    @Test
    @DisplayName("Can add inputs with units")
    void testAddInputsWithUnits() {
      ProcessSystem process = new ProcessSystem();
      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

      analyzer.withInput("feed", "temperature", "C").withInput("feed", "flowRate", "kg/hr")
          .withOutput("product", "temperature", "K");

      assertNotNull(analyzer);
    }

    @Test
    @DisplayName("Can reset analyzer")
    void testReset() {
      ProcessSystem process = new ProcessSystem();
      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

      analyzer.withInput("feed", "temperature").withOutput("product", "temperature");

      ProcessSensitivityAnalyzer result = analyzer.reset();

      assertSame(analyzer, result); // Fluent API returns same instance
    }

    @Test
    @DisplayName("Can configure perturbation settings")
    void testPerturbationSettings() {
      ProcessSystem process = new ProcessSystem();
      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

      analyzer.withPerturbation(0.01).withCentralDifferences(true);

      assertNotNull(analyzer);
    }
  }

  @Nested
  @DisplayName("VariableSpec Tests")
  class VariableSpecTests {
    @Test
    @DisplayName("VariableSpec stores equipment and property names")
    void testVariableSpecBasic() {
      VariableSpec spec = new VariableSpec("heater1", "temperature");

      assertEquals("heater1", spec.getEquipmentName());
      assertEquals("temperature", spec.getPropertyName());
      assertNull(spec.getUnit());
      assertEquals("heater1.temperature", spec.getFullName());
    }

    @Test
    @DisplayName("VariableSpec stores unit")
    void testVariableSpecWithUnit() {
      VariableSpec spec = new VariableSpec("separator", "pressure", "bara");

      assertEquals("separator", spec.getEquipmentName());
      assertEquals("pressure", spec.getPropertyName());
      assertEquals("bara", spec.getUnit());
      assertEquals("separator.pressure", spec.getFullName());
    }

    @Test
    @DisplayName("VariableSpec toString returns full name")
    void testVariableSpecToString() {
      VariableSpec spec = new VariableSpec("valve", "flowRate");
      assertEquals("valve.flowRate", spec.toString());
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {
    @Test
    @DisplayName("Throws exception if no inputs specified")
    void testNoInputsThrowsException() {
      ProcessSystem process = new ProcessSystem();
      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

      analyzer.withOutput("product", "temperature");

      assertThrows(IllegalStateException.class, () -> analyzer.compute());
    }

    @Test
    @DisplayName("Throws exception if no outputs specified")
    void testNoOutputsThrowsException() {
      ProcessSystem process = new ProcessSystem();
      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

      analyzer.withInput("feed", "temperature");

      assertThrows(IllegalStateException.class, () -> analyzer.compute());
    }
  }

  @Nested
  @DisplayName("Simple Process Sensitivity Tests")
  class SimpleProcessTests {
    @Test
    @DisplayName("Computes sensitivity for stream")
    void testStreamSensitivity() {
      // Create a simple process: feed stream only
      ProcessSystem process = new ProcessSystem();

      Stream feed = new Stream("feed", testFluid.clone());
      feed.setFlowRate(100.0, "kg/hr");
      feed.setTemperature(25.0, "C");
      feed.setPressure(10.0, "bara");

      process.add(feed);
      process.run();

      // Analyze sensitivity of stream outputs to its inputs
      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);
      analyzer.withInput("feed", "temperature").withOutput("feed", "temperature");

      SensitivityMatrix matrix = analyzer.compute();

      assertNotNull(matrix);

      // The output temperature should be directly equal to input (sensitivity = 1)
      double sensitivity = matrix.getSensitivity("feed.temperature", "feed.temperature");
      assertEquals(1.0, sensitivity, 0.1,
          "Expected sensitivity ~1.0 for same variable, got: " + sensitivity);
    }

    @Test
    @DisplayName("Computes sensitivity for separator")
    void testSeparatorSensitivity() {
      // Create a simple process: feed -> separator
      ProcessSystem process = new ProcessSystem();

      SystemInterface twoPhase = new SystemSrkEos(298.0, 50.0);
      twoPhase.addComponent("methane", 0.9);
      twoPhase.addComponent("n-heptane", 0.1);
      twoPhase.setMixingRule("classic");

      Stream feed = new Stream("feed", twoPhase);
      feed.setFlowRate(1000.0, "kg/hr");
      feed.setTemperature(25.0, "C");
      feed.setPressure(10.0, "bara");

      Separator separator = new Separator("separator", feed);

      process.add(feed);
      process.add(separator);
      process.run();

      // Analyze sensitivity
      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);
      analyzer.withInput("feed", "temperature").withInput("feed", "pressure")
          .withOutput("feed", "temperature").withOutput("feed", "pressure");

      SensitivityMatrix matrix = analyzer.compute();

      assertNotNull(matrix);

      // Check that we get reasonable sensitivities
      // Temperature output should be sensitive to temperature input
      double dT_dT = matrix.getSensitivity("feed.temperature", "feed.temperature");
      assertEquals(1.0, dT_dT, 0.1, "Expected temperature self-sensitivity ~1.0, got: " + dT_dT);

      // Pressure output should be sensitive to pressure input
      double dP_dP = matrix.getSensitivity("feed.pressure", "feed.pressure");
      assertEquals(1.0, dP_dP, 0.1, "Expected pressure self-sensitivity ~1.0, got: " + dP_dP);
    }

    @Test
    @DisplayName("Central differences gives similar results to forward")
    void testCentralDifferences() {
      ProcessSystem process = new ProcessSystem();

      Stream feed = new Stream("feed", testFluid.clone());
      feed.setFlowRate(100.0, "kg/hr");
      feed.setTemperature(25.0, "C");
      feed.setPressure(10.0, "bara");

      process.add(feed);
      process.run();

      // Forward differences
      ProcessSensitivityAnalyzer forwardAnalyzer = new ProcessSensitivityAnalyzer(process);
      forwardAnalyzer.withInput("feed", "flowRate").withOutput("feed", "temperature")
          .withCentralDifferences(false);

      // Central differences
      ProcessSensitivityAnalyzer centralAnalyzer = new ProcessSensitivityAnalyzer(process);
      centralAnalyzer.withInput("feed", "flowRate").withOutput("feed", "temperature")
          .withCentralDifferences(true);

      SensitivityMatrix forwardMatrix = forwardAnalyzer.compute();
      SensitivityMatrix centralMatrix = centralAnalyzer.compute();

      // Both should give similar results
      double forwardSens = forwardMatrix.getSensitivity("feed.temperature", "feed.flowRate");
      double centralSens = centralMatrix.getSensitivity("feed.temperature", "feed.flowRate");

      // For a linear relationship (or in this case, no relationship), they should be close
      assertEquals(forwardSens, centralSens, 0.01,
          "Forward and central should give similar results");
    }
  }

  @Nested
  @DisplayName("Report Generation Tests")
  class ReportTests {
    @Test
    @DisplayName("Generates readable report")
    void testReportGeneration() {
      ProcessSystem process = new ProcessSystem();

      Stream feed = new Stream("feed", testFluid.clone());
      feed.setFlowRate(100.0, "kg/hr");
      feed.setTemperature(25.0, "C");
      feed.setPressure(10.0, "bara");

      Separator separator = new Separator("separator", feed);

      process.add(feed);
      process.add(separator);
      process.run();

      ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);
      analyzer.withInput("feed", "temperature").withInput("feed", "pressure").withOutput("feed",
          "temperature");

      SensitivityMatrix matrix = analyzer.compute();
      String report = analyzer.generateReport(matrix);

      assertNotNull(report);
      assertTrue(report.contains("Process Sensitivity Analysis Report"));
      assertTrue(report.contains("feed.temperature"));
      assertTrue(report.contains("Most Influential Inputs"));
    }
  }
}
