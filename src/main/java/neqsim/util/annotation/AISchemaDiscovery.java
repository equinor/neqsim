package neqsim.util.annotation;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovers and documents AI-exposed NeqSim functionality.
 * 
 * <p>
 * This class uses reflection to find methods marked with {@link AIExposable} and generates
 * structured documentation that AI agents can parse.
 * </p>
 * 
 * <h2>Usage:</h2>
 * 
 * <pre>
 * {@code
 * AISchemaDiscovery discovery = new AISchemaDiscovery();
 * 
 * // Discover methods in a class
 * List<MethodSchema> methods = discovery.discoverMethods(SystemSrkEos.class);
 * 
 * // Generate prompt for AI
 * String prompt = discovery.generateMethodPrompt(methods);
 * 
 * // Get all core NeqSim APIs
 * Map<String, List<MethodSchema>> coreAPIs = discovery.discoverCoreAPIs();
 * }
 * </pre>
 * 
 * @author NeqSim
 * @version 1.0
 */
public class AISchemaDiscovery implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Schema for a discovered method.
   */
  public static class MethodSchema implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String className;
    private final String methodName;
    private final String description;
    private final String category;
    private final String example;
    private final int priority;
    private final boolean safe;
    private final String[] tags;
    private final List<ParameterSchema> parameters;
    private final String returnType;

    /**
     * Constructor.
     */
    public MethodSchema(String className, String methodName, String description, String category,
        String example, int priority, boolean safe, String[] tags, List<ParameterSchema> parameters,
        String returnType) {
      this.className = className;
      this.methodName = methodName;
      this.description = description;
      this.category = category;
      this.example = example;
      this.priority = priority;
      this.safe = safe;
      this.tags = tags;
      this.parameters = parameters;
      this.returnType = returnType;
    }

    public String getClassName() {
      return className;
    }

    public String getMethodName() {
      return methodName;
    }

    public String getDescription() {
      return description;
    }

    public String getCategory() {
      return category;
    }

    public String getExample() {
      return example;
    }

    public int getPriority() {
      return priority;
    }

    public boolean isSafe() {
      return safe;
    }

    public String[] getTags() {
      return tags;
    }

    public List<ParameterSchema> getParameters() {
      return parameters;
    }

    public String getReturnType() {
      return returnType;
    }

    /**
     * Convert to structured text for AI consumption.
     */
    public String toPromptText() {
      StringBuilder sb = new StringBuilder();
      sb.append("### ").append(methodName).append("\n");
      sb.append("**Class:** ").append(className).append("\n");
      sb.append("**Category:** ").append(category).append("\n");
      if (!description.isEmpty()) {
        sb.append("**Description:** ").append(description).append("\n");
      }
      sb.append("**Returns:** ").append(returnType).append("\n");
      sb.append("**Safe:** ").append(safe ? "Yes" : "No (modifies state)").append("\n");

      if (!parameters.isEmpty()) {
        sb.append("**Parameters:**\n");
        for (ParameterSchema param : parameters) {
          sb.append("  - ").append(param.toPromptText()).append("\n");
        }
      }

      if (!example.isEmpty()) {
        sb.append("**Example:** `").append(example).append("`\n");
      }

      return sb.toString();
    }
  }

  /**
   * Schema for a method parameter.
   */
  public static class ParameterSchema implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String name;
    private final String type;
    private final String description;
    private final String unit;
    private final double minValue;
    private final double maxValue;
    private final String defaultValue;
    private final boolean required;
    private final String[] options;

    /**
     * Constructor.
     */
    public ParameterSchema(String name, String type, String description, String unit,
        double minValue, double maxValue, String defaultValue, boolean required, String[] options) {
      this.name = name;
      this.type = type;
      this.description = description;
      this.unit = unit;
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.defaultValue = defaultValue;
      this.required = required;
      this.options = options;
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    /**
     * Convert to structured text for AI consumption.
     */
    public String toPromptText() {
      StringBuilder sb = new StringBuilder();
      sb.append("`").append(name).append("` (").append(type).append(")");
      if (!description.isEmpty()) {
        sb.append(": ").append(description);
      }
      if (!unit.isEmpty()) {
        sb.append(" [").append(unit).append("]");
      }
      if (minValue != Double.NEGATIVE_INFINITY || maxValue != Double.POSITIVE_INFINITY) {
        sb.append(" Range: [").append(minValue).append(", ").append(maxValue).append("]");
      }
      if (!required) {
        sb.append(" (optional");
        if (!defaultValue.isEmpty()) {
          sb.append(", default: ").append(defaultValue);
        }
        sb.append(")");
      }
      return sb.toString();
    }
  }

  /**
   * Discover all AI-exposed methods in a class.
   * 
   * @param clazz class to scan
   * @return list of method schemas
   */
  public List<MethodSchema> discoverMethods(Class<?> clazz) {
    List<MethodSchema> schemas = new ArrayList<>();

    for (Method method : clazz.getMethods()) {
      AIExposable annotation = method.getAnnotation(AIExposable.class);
      if (annotation != null) {
        schemas.add(createMethodSchema(clazz, method, annotation));
      }
    }

    // Sort by priority (highest first)
    schemas.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    return schemas;
  }

  /**
   * Discover common NeqSim methods even without annotations.
   * 
   * <p>
   * This provides a basic discovery of frequently-used methods for AI consumption.
   * </p>
   * 
   * @param clazz class to scan
   * @return list of common method schemas
   */
  public List<MethodSchema> discoverCommonMethods(Class<?> clazz) {
    List<MethodSchema> schemas = new ArrayList<>();

    // Common thermodynamic methods
    List<String> commonMethods = Arrays.asList("addComponent", "setTemperature", "setPressure",
        "setMixingRule", "init", "run", "getTemperature", "getPressure", "getDensity",
        "getEnthalpy", "getEntropy", "getZ", "getBeta", "getViscosity", "getThermalConductivity",
        "getMolarMass", "setFlowRate", "getFlowRate", "getFluid", "getThermoSystem", "clone");

    for (Method method : clazz.getMethods()) {
      if (commonMethods.contains(method.getName())) {
        schemas.add(createMethodSchemaFromMethod(clazz, method));
      }
    }

    return schemas;
  }

  /**
   * Create MethodSchema from annotation.
   */
  private MethodSchema createMethodSchema(Class<?> clazz, Method method, AIExposable annotation) {
    List<ParameterSchema> params = new ArrayList<>();
    for (Parameter param : method.getParameters()) {
      AIParameter paramAnnotation = param.getAnnotation(AIParameter.class);
      if (paramAnnotation != null) {
        params.add(new ParameterSchema(
            paramAnnotation.name().isEmpty() ? param.getName() : paramAnnotation.name(),
            param.getType().getSimpleName(), paramAnnotation.description(), paramAnnotation.unit(),
            paramAnnotation.minValue(), paramAnnotation.maxValue(), paramAnnotation.defaultValue(),
            paramAnnotation.required(), paramAnnotation.options()));
      } else {
        params.add(new ParameterSchema(param.getName(), param.getType().getSimpleName(), "", "",
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "", true, new String[0]));
      }
    }

    return new MethodSchema(clazz.getSimpleName(), method.getName(), annotation.description(),
        annotation.category(), annotation.example(), annotation.priority(), annotation.safe(),
        annotation.tags(), params, method.getReturnType().getSimpleName());
  }

  /**
   * Create MethodSchema from method without annotation.
   */
  private MethodSchema createMethodSchemaFromMethod(Class<?> clazz, Method method) {
    List<ParameterSchema> params = new ArrayList<>();
    for (Parameter param : method.getParameters()) {
      params.add(new ParameterSchema(param.getName(), param.getType().getSimpleName(), "", "",
          Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "", true, new String[0]));
    }

    String description = generateDescriptionFromName(method.getName());

    return new MethodSchema(clazz.getSimpleName(), method.getName(), description, "general", "", 50,
        !method.getName().startsWith("set"), new String[0], params,
        method.getReturnType().getSimpleName());
  }

  /**
   * Generate a basic description from method name.
   */
  private String generateDescriptionFromName(String methodName) {
    if (methodName.startsWith("get")) {
      return "Get the " + camelToSpaces(methodName.substring(3));
    } else if (methodName.startsWith("set")) {
      return "Set the " + camelToSpaces(methodName.substring(3));
    } else if (methodName.startsWith("add")) {
      return "Add " + camelToSpaces(methodName.substring(3));
    } else if (methodName.startsWith("is") || methodName.startsWith("has")) {
      return "Check if " + camelToSpaces(methodName);
    }
    return camelToSpaces(methodName);
  }

  /**
   * Convert camelCase to spaces.
   */
  private String camelToSpaces(String camel) {
    return camel.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
  }

  /**
   * Generate a prompt containing discovered methods.
   * 
   * @param methods list of method schemas
   * @return formatted prompt text
   */
  public String generateMethodPrompt(List<MethodSchema> methods) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Available NeqSim Methods\n\n");

    // Group by category
    Map<String, List<MethodSchema>> byCategory =
        methods.stream().collect(Collectors.groupingBy(MethodSchema::getCategory));

    for (Map.Entry<String, List<MethodSchema>> entry : byCategory.entrySet()) {
      sb.append("## ").append(entry.getKey()).append("\n\n");
      for (MethodSchema method : entry.getValue()) {
        sb.append(method.toPromptText()).append("\n");
      }
    }

    return sb.toString();
  }

  /**
   * Discover core NeqSim APIs by scanning common classes.
   * 
   * @return map of class name to method schemas
   */
  public Map<String, List<MethodSchema>> discoverCoreAPIs() {
    Map<String, List<MethodSchema>> apis = new HashMap<>();

    // Core thermodynamic classes
    try {
      apis.put("SystemSrkEos",
          discoverCommonMethods(Class.forName("neqsim.thermo.system.SystemSrkEos")));
    } catch (ClassNotFoundException e) {
      // Ignore if class not found
    }

    try {
      apis.put("Stream",
          discoverCommonMethods(Class.forName("neqsim.process.equipment.stream.Stream")));
    } catch (ClassNotFoundException e) {
      // Ignore
    }

    try {
      apis.put("Separator",
          discoverCommonMethods(Class.forName("neqsim.process.equipment.separator.Separator")));
    } catch (ClassNotFoundException e) {
      // Ignore
    }

    try {
      apis.put("ProcessSystem",
          discoverCommonMethods(Class.forName("neqsim.process.processmodel.ProcessSystem")));
    } catch (ClassNotFoundException e) {
      // Ignore
    }

    return apis;
  }

  /**
   * Get a quick-start prompt for AI agents.
   * 
   * @return formatted quick-start prompt
   */
  public String getQuickStartPrompt() {
    StringBuilder sb = new StringBuilder();
    sb.append("# NeqSim Quick Start for AI Agents\n\n");
    sb.append("## Creating a Fluid\n");
    sb.append("```java\n");
    sb.append("SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0); // T(K), P(bar)\n");
    sb.append("fluid.addComponent(\"methane\", 0.9);    // component, mole fraction\n");
    sb.append("fluid.addComponent(\"ethane\", 0.1);\n");
    sb.append("fluid.setMixingRule(\"classic\");        // or setMixingRule(2) for CPA\n");
    sb.append("```\n\n");
    sb.append("## Running a Flash Calculation\n");
    sb.append("```java\n");
    sb.append("ThermodynamicOperations ops = new ThermodynamicOperations(fluid);\n");
    sb.append("ops.TPflash();  // Calculate phase equilibrium\n");
    sb.append("fluid.init(3);  // Initialize properties\n");
    sb.append("```\n\n");
    sb.append("## Getting Properties\n");
    sb.append("```java\n");
    sb.append("double density = fluid.getDensity(\"kg/m3\");\n");
    sb.append("double viscosity = fluid.getViscosity(\"cP\");\n");
    sb.append("double Z = fluid.getZ();  // Compressibility factor\n");
    sb.append("```\n\n");
    sb.append("## Process Simulation\n");
    sb.append("```java\n");
    sb.append("Stream feed = new Stream(\"feed\", fluid);\n");
    sb.append("feed.setFlowRate(1000, \"kg/hr\");\n");
    sb.append("Separator sep = new Separator(\"sep\", feed);\n\n");
    sb.append("ProcessSystem process = new ProcessSystem();\n");
    sb.append("process.add(feed);\n");
    sb.append("process.add(sep);\n");
    sb.append("process.run();\n\n");
    sb.append("Stream gasOut = sep.getGasOutStream();\n");
    sb.append("Stream liqOut = sep.getLiquidOutStream();\n");
    sb.append("```\n\n");
    sb.append("## Validation (AI-Friendly)\n");
    sb.append("```java\n");
    sb.append("ValidationResult result = SimulationValidator.validate(fluid);\n");
    sb.append("if (!result.isValid()) {\n");
    sb.append("    System.out.println(result.getReport()); // Contains fix hints\n");
    sb.append("}\n");
    sb.append("```\n");
    return sb.toString();
  }
}
