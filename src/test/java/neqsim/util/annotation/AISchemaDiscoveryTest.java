package neqsim.util.annotation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the AISchemaDiscovery class.
 */
class AISchemaDiscoveryTest {

  private AISchemaDiscovery discovery;

  @BeforeEach
  void setUp() {
    discovery = new AISchemaDiscovery();
  }

  // Sample class with AIExposable annotations for testing
  static class TestThermo {
    @AIExposable(description = "Add a chemical component", category = "composition",
        example = "addComponent(\"methane\", 0.9)", priority = 100, safe = false,
        tags = {"fluid", "setup"})
    public void addComponent(
        @AIParameter(name = "name", description = "Component name",
            options = {"methane", "ethane", "propane"}) String name,
        @AIParameter(name = "moles", description = "Mole fraction", minValue = 0.0,
            maxValue = 1.0) double moles) {}

    @AIExposable(description = "Get temperature", category = "properties",
        example = "getTemperature()", priority = 80, safe = true)
    public double getTemperature() {
      return 298.15;
    }

    public void nonExposedMethod() {}
  }

  @Test
  @DisplayName("Should discover annotated methods")
  void testDiscoverAnnotatedMethods() {
    List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverMethods(TestThermo.class);

    assertEquals(2, methods.size(), "Should find 2 annotated methods");

    // Should be sorted by priority (highest first)
    assertEquals("addComponent", methods.get(0).getMethodName());
    assertEquals("getTemperature", methods.get(1).getMethodName());
  }

  @Test
  @DisplayName("Should extract method description")
  void testExtractDescription() {
    List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverMethods(TestThermo.class);

    AISchemaDiscovery.MethodSchema addComponent = methods.stream()
        .filter(m -> m.getMethodName().equals("addComponent")).findFirst().orElse(null);

    assertNotNull(addComponent);
    assertEquals("Add a chemical component", addComponent.getDescription());
    assertEquals("composition", addComponent.getCategory());
    assertFalse(addComponent.isSafe());
  }

  @Test
  @DisplayName("Should extract parameter annotations")
  void testExtractParameters() {
    List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverMethods(TestThermo.class);

    AISchemaDiscovery.MethodSchema addComponent = methods.stream()
        .filter(m -> m.getMethodName().equals("addComponent")).findFirst().orElse(null);

    assertNotNull(addComponent);
    assertEquals(2, addComponent.getParameters().size());

    AISchemaDiscovery.ParameterSchema nameParam = addComponent.getParameters().get(0);
    assertEquals("name", nameParam.getName());
    assertEquals("Component name",
        nameParam.toPromptText().split(":")[1].trim().split("\\[")[0].trim());
  }

  @Test
  @DisplayName("Should generate prompt text")
  void testGeneratePromptText() {
    List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverMethods(TestThermo.class);

    String prompt = discovery.generateMethodPrompt(methods);

    assertNotNull(prompt);
    assertTrue(prompt.contains("# Available NeqSim Methods"));
    assertTrue(prompt.contains("## composition"));
    assertTrue(prompt.contains("## properties"));
    assertTrue(prompt.contains("addComponent"));
    assertTrue(prompt.contains("getTemperature"));
  }

  @Test
  @DisplayName("Should discover common methods without annotations")
  void testDiscoverCommonMethods() {
    // Test with a real NeqSim class
    try {
      Class<?> streamClass = Class.forName("neqsim.process.equipment.stream.Stream");
      List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverCommonMethods(streamClass);

      // Should find common methods like run, setFlowRate, getFlowRate
      assertTrue(methods.stream().anyMatch(m -> m.getMethodName().equals("run")),
          "Should find run method");
      assertTrue(methods.stream().anyMatch(m -> m.getMethodName().equals("setFlowRate")),
          "Should find setFlowRate method");
    } catch (ClassNotFoundException e) {
      // Skip test if class not on classpath
      System.out.println("Skipping test - Stream class not on classpath");
    }
  }

  @Test
  @DisplayName("Should get quick-start prompt")
  void testGetQuickStartPrompt() {
    String prompt = discovery.getQuickStartPrompt();

    assertNotNull(prompt);
    assertTrue(prompt.contains("# NeqSim Quick Start for AI Agents"));
    assertTrue(prompt.contains("SystemSrkEos"));
    assertTrue(prompt.contains("addComponent"));
    assertTrue(prompt.contains("TPflash"));
    assertTrue(prompt.contains("ProcessSystem"));
  }

  @Test
  @DisplayName("MethodSchema toPromptText should be well formatted")
  void testMethodSchemaToPromptText() {
    List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverMethods(TestThermo.class);

    AISchemaDiscovery.MethodSchema getTemp = methods.stream()
        .filter(m -> m.getMethodName().equals("getTemperature")).findFirst().orElse(null);

    assertNotNull(getTemp);
    String prompt = getTemp.toPromptText();

    assertTrue(prompt.contains("### getTemperature"));
    assertTrue(prompt.contains("**Class:** TestThermo"));
    assertTrue(prompt.contains("**Category:** properties"));
    assertTrue(prompt.contains("**Returns:** double"));
    assertTrue(prompt.contains("**Safe:** Yes"));
  }

  @Test
  @DisplayName("Should not discover non-annotated methods")
  void testNonAnnotatedMethodsIgnored() {
    List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverMethods(TestThermo.class);

    boolean hasNonExposed =
        methods.stream().anyMatch(m -> m.getMethodName().equals("nonExposedMethod"));

    assertFalse(hasNonExposed, "Should not find non-annotated method");
  }

  @Test
  @DisplayName("Should sort by priority")
  void testSortByPriority() {
    List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverMethods(TestThermo.class);

    // Higher priority should come first
    assertEquals(100, methods.get(0).getPriority());
    assertEquals(80, methods.get(1).getPriority());
  }

  @Test
  @DisplayName("Should handle class with no annotations")
  void testClassWithNoAnnotations() {
    List<AISchemaDiscovery.MethodSchema> methods = discovery.discoverMethods(String.class);

    assertTrue(methods.isEmpty(), "Should return empty list for class with no annotations");
  }

  @Test
  @DisplayName("Parameter schema should handle min/max values")
  void testParameterMinMax() {
    AISchemaDiscovery.ParameterSchema param = new AISchemaDiscovery.ParameterSchema("pressure",
        "double", "System pressure", "bar", 0.0, 1000.0, "1.0", true, new String[0]);

    String text = param.toPromptText();
    assertTrue(text.contains("Range: [0.0, 1000.0]"));
    assertTrue(text.contains("[bar]"));
  }

  @Test
  @DisplayName("Should discover core APIs")
  void testDiscoverCoreAPIs() {
    Map<String, List<AISchemaDiscovery.MethodSchema>> apis = discovery.discoverCoreAPIs();

    // Should find at least some core classes
    assertNotNull(apis);
    // The actual content depends on which classes are on the classpath
  }
}
