package neqsim.process.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the self-healing automation diagnostics including fuzzy matching, auto-correction,
 * physical bounds validation, and operation tracking.
 */
class AutomationDiagnosticsTest {

  private AutomationDiagnostics diagnostics;

  @BeforeEach
  void setUp() {
    diagnostics = new AutomationDiagnostics();
  }

  @Test
  void testExactMatchReturnsFirst() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep", "Compressor");
    List<String> closest = diagnostics.findClosestNames("HP Sep", valid, 3);
    assertFalse(closest.isEmpty());
    assertEquals("HP Sep", closest.get(0));
  }

  @Test
  void testCaseInsensitiveFuzzyMatch() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep", "Compressor");
    List<String> closest = diagnostics.findClosestNames("hp sep", valid, 3);
    assertFalse(closest.isEmpty());
    assertEquals("HP Sep", closest.get(0));
  }

  @Test
  void testSubstringMatch() {
    List<String> valid = Arrays.asList("HP Separator", "LP Separator", "Gas Compressor");
    List<String> closest = diagnostics.findClosestNames("HP Sep", valid, 3);
    assertFalse(closest.isEmpty());
    assertEquals("HP Separator", closest.get(0));
  }

  @Test
  void testAutoCorrectCaseInsensitive() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep", "Compressor");
    String corrected = diagnostics.autoCorrectName("hp sep", valid);
    assertEquals("HP Sep", corrected);
  }

  @Test
  void testAutoCorrectWhitespaceNormalization() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep");
    String corrected = diagnostics.autoCorrectName("HP  Sep", valid);
    assertEquals("HP Sep", corrected);
  }

  @Test
  void testAutoCorrectSubstring() {
    List<String> valid = Arrays.asList("HP Separator", "Gas Compressor");
    // "HP Sep" is contained in "HP Separator" — single match
    String corrected = diagnostics.autoCorrectName("HP Sep", valid);
    assertEquals("HP Separator", corrected);
  }

  @Test
  void testAutoCorrectCloseEditDistance() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep");
    // "HP Sap" is 1 edit from "HP Sep"
    String corrected = diagnostics.autoCorrectName("HP Sap", valid);
    assertEquals("HP Sep", corrected);
  }

  @Test
  void testAutoCorrectNoMatch() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep");
    String corrected = diagnostics.autoCorrectName("XYZ Widget", valid);
    assertNull(corrected);
  }

  @Test
  void testAutoCorrectLearnedCorrection() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep");
    // Simulate a previous failure + correction
    diagnostics.recordFailure("get", "hp separator",
        AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND, "HP Sep");

    // Now looking up "hp separator" should return the learned correction
    String corrected = diagnostics.autoCorrectName("hp separator", valid);
    assertEquals("HP Sep", corrected);
  }

  @Test
  void testDiagnoseUnitNotFoundWithSuggestions() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep", "Compressor");
    AutomationDiagnostics.DiagnosticResult result =
        diagnostics.diagnoseUnitNotFound("HP Separator", valid);

    assertEquals(AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND, result.getCategory());
    assertEquals("HP Separator", result.getOriginalInput());
    assertFalse(result.getSuggestions().isEmpty());
    assertNotNull(result.getRemediation());
    assertTrue(result.getRemediation().length() > 0);
  }

  @Test
  void testDiagnoseUnitNotFoundWithAutoCorrection() {
    List<String> valid = Arrays.asList("HP Sep", "LP Sep", "Compressor");
    AutomationDiagnostics.DiagnosticResult result =
        diagnostics.diagnoseUnitNotFound("hp sep", valid);

    assertTrue(result.hasAutoCorrection());
    assertEquals("HP Sep", result.getAutoCorrection());
    assertTrue(result.getRemediation().contains("Auto-corrected"));
  }

  @Test
  void testPhysicalBoundsTemperature() {
    // Reasonable temperature
    AutomationDiagnostics.DiagnosticResult result =
        diagnostics.validatePhysicalBounds("temperature", 25.0, "C");
    assertNull(result, "25 C should be in bounds");

    // Out of bounds temperature
    result = diagnostics.validatePhysicalBounds("temperature", -300.0, "C");
    assertNotNull(result, "-300 C is below absolute zero");
    assertEquals(AutomationDiagnostics.ErrorCategory.VALUE_OUT_OF_BOUNDS, result.getCategory());
  }

  @Test
  void testPhysicalBoundsPressure() {
    // Reasonable pressure
    AutomationDiagnostics.DiagnosticResult result =
        diagnostics.validatePhysicalBounds("pressure", 60.0, "bara");
    assertNull(result, "60 bara should be in bounds");

    // Negative pressure
    result = diagnostics.validatePhysicalBounds("outletPressure", -10.0, "bara");
    assertNotNull(result, "negative pressure should be out of bounds");
  }

  @Test
  void testPhysicalBoundsUnusualWarning() {
    // Unusual but not impossible temperature (soft bounds)
    AutomationDiagnostics.DiagnosticResult result =
        diagnostics.validatePhysicalBounds("temperature", 2000.0, "C");
    assertNotNull(result, "2000 C should trigger soft warning");
    assertTrue(result.getContext().containsKey("severity"));
    assertEquals("WARNING", result.getContext().get("severity"));
  }

  @Test
  void testOperationTracking() {
    assertEquals(0, diagnostics.getOperationCount());
    assertEquals(1.0, diagnostics.getSuccessRate(), 0.01);

    diagnostics.recordSuccess("get", "HP Sep.temperature");
    diagnostics.recordSuccess("get", "HP Sep.pressure");
    diagnostics.recordFailure("get", "HP separator.temp",
        AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND, "HP Sep.temperature");

    assertEquals(3, diagnostics.getOperationCount());
    assertEquals(2.0 / 3.0, diagnostics.getSuccessRate(), 0.01);

    java.util.Map<String, Integer> errors = diagnostics.getErrorCategoryCounts();
    assertEquals(1, errors.get("UNIT_NOT_FOUND").intValue());
  }

  @Test
  void testLearnedCorrectionsAccumulate() {
    diagnostics.recordFailure("get", "hp sep",
        AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND, "HP Sep");
    diagnostics.recordFailure("get", "compresser",
        AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND, "Compressor");

    java.util.Map<String, String> learned = diagnostics.getLearnedCorrections();
    assertEquals(2, learned.size());
    assertEquals("HP Sep", learned.get("hp sep"));
    assertEquals("Compressor", learned.get("compresser"));
  }

  @Test
  void testLearningReport() {
    diagnostics.recordSuccess("get", "HP Sep.temperature");
    diagnostics.recordFailure("get", "hp separator.temp",
        AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND, "HP Sep.temperature");

    String report = diagnostics.getLearningReport();
    assertNotNull(report);
    assertTrue(report.contains("totalOperations"));
    assertTrue(report.contains("successRate"));
    assertTrue(report.contains("learnedCorrections"));
  }

  @Test
  void testDiagnosticResultToJson() {
    AutomationDiagnostics.DiagnosticResult result = diagnostics.diagnoseUnitNotFound(
        "HP Separator", Arrays.asList("HP Sep", "LP Sep"));

    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("UNIT_NOT_FOUND"));
    assertTrue(json.contains("HP Separator"));
    assertTrue(json.contains("suggestions"));
  }

  @Test
  void testEditDistance() {
    assertEquals(0, AutomationDiagnostics.editDistance("abc", "abc"));
    assertEquals(1, AutomationDiagnostics.editDistance("abc", "ab"));
    assertEquals(1, AutomationDiagnostics.editDistance("abc", "abd"));
    assertEquals(3, AutomationDiagnostics.editDistance("abc", "xyz"));
  }

  @Test
  void testReset() {
    diagnostics.recordSuccess("get", "HP Sep.temp");
    diagnostics.recordFailure("get", "bad", AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND,
        "good");
    assertEquals(2, diagnostics.getOperationCount());
    assertFalse(diagnostics.getLearnedCorrections().isEmpty());

    diagnostics.reset();
    assertEquals(0, diagnostics.getOperationCount());
    assertTrue(diagnostics.getLearnedCorrections().isEmpty());
  }

  @Test
  void testProcessAutomationSafeGetWithGoodAddress() {
    ProcessSystem process = buildTestProcess();
    process.run();
    ProcessAutomation auto = process.getAutomation();

    String result = auto.getVariableValueSafe("HP Sep.temperature", "C");
    assertNotNull(result);
    assertTrue(result.contains("success"));
    assertTrue(result.contains("value"));
  }

  @Test
  void testProcessAutomationSafeGetWithBadUnitName() {
    ProcessSystem process = buildTestProcess();
    process.run();
    ProcessAutomation auto = process.getAutomation();

    String result = auto.getVariableValueSafe("hp sep.temperature", "C");
    assertNotNull(result);
    // Should either auto-correct or return diagnostic
    assertTrue(result.contains("auto_corrected") || result.contains("UNIT_NOT_FOUND")
        || result.contains("success"));
  }

  @Test
  void testProcessAutomationFindUnitFuzzy() {
    ProcessSystem process = buildTestProcess();
    process.run();
    ProcessAutomation auto = process.getAutomation();

    // Exact name should work
    double temp = auto.getVariableValue("HP Sep.temperature", "C");
    assertTrue(!Double.isNaN(temp));

    // Case-insensitive should now also work via fuzzy matching in findUnit
    double temp2 = auto.getVariableValue("hp sep.temperature", "C");
    assertEquals(temp, temp2, 0.01);
  }

  @Test
  void testProcessAutomationSafeSetWithBoundsWarning() {
    ProcessSystem process = buildTestProcess();
    process.run();
    ProcessAutomation auto = process.getAutomation();

    // Set a reasonable value
    String result = auto.setVariableValueSafe("Compressor.outletPressure", 100.0, "bara");
    assertNotNull(result);
    assertTrue(result.contains("success") || result.contains("auto_corrected"));
  }

  @Test
  void testDiagnosticsAccessibleFromAutomation() {
    ProcessSystem process = buildTestProcess();
    ProcessAutomation auto = process.getAutomation();
    assertNotNull(auto.getDiagnostics());
  }

  /**
   * Builds a simple test process with a feed, separator, and compressor.
   */
  private ProcessSystem buildTestProcess() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed Gas", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    Separator sep = new Separator("HP Sep", feed);
    Compressor comp = new Compressor("Compressor", sep.getGasOutStream());
    comp.setOutletPressure(100.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(comp);
    return process;
  }
}
