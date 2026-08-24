package neqsim.mcp.runners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.EquipmentFactory;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Provides focused, version-matched inspection of the public NeqSim Java API.
 *
 * <p>
 * Fully qualified class names are loaded directly from the running NeqSim artifact. Common process classes and JSON
 * equipment aliases are also resolved, so a client can inspect {@code Mixer} or {@code ProcessSystem} without guessing
 * a package. The result includes reflection-derived method signatures plus source and documentation pointers; it does
 * not claim that prose documentation is the runtime contract.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ApiKnowledgeRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final int DEFAULT_METHOD_LIMIT = 100;
  private static final Map<String, String> COMMON_CLASSES = createCommonClasses();

  /**
   * Private constructor for static utility class.
   */
  private ApiKnowledgeRunner() {
  }

  /**
   * Inspects a public NeqSim class or process-equipment alias.
   *
   * @param className fully qualified class name, common class name, or equipment alias
   * @param memberFilter optional case-insensitive substring used to filter method names
   * @return JSON containing the resolved class, constructors, methods, and knowledge pointers
   */
  public static String inspect(String className, String memberFilter) {
    JsonObject result = new JsonObject();
    try {
      Class<?> resolved = resolveClass(className);
      if (resolved == null) {
        return error(className,
            "Class or equipment alias not found. Use a fully qualified neqsim.* class name, a common "
                + "class such as ProcessSystem, or a factory equipment type such as Mixer or Recycle.");
      }

      result.addProperty("status", "success");
      result.addProperty("requested", className);
      result.addProperty("resolvedClass", resolved.getName());
      result.addProperty("package", resolved.getPackage() == null ? "" : resolved.getPackage().getName());
      if (resolved.getSuperclass() != null) {
        result.addProperty("superclass", resolved.getSuperclass().getName());
      }
      result.add("interfaces", typeNames(resolved.getInterfaces()));
      result.add("constructors", constructorSignatures(resolved));

      List<Method> methods = new ArrayList<Method>(Arrays.asList(resolved.getMethods()));
      Collections.sort(methods, new Comparator<Method>() {
        @Override
        public int compare(Method left, Method right) {
          return methodSignature(left).compareTo(methodSignature(right));
        }
      });

      JsonArray methodArray = new JsonArray();
      String filter = memberFilter == null ? "" : memberFilter.trim().toLowerCase(Locale.ROOT);
      int matchingMethodCount = 0;
      for (Method method : methods) {
        if (!Modifier.isPublic(method.getModifiers())
            || (!filter.isEmpty() && !method.getName().toLowerCase(Locale.ROOT).contains(filter))) {
          continue;
        }
        matchingMethodCount++;
        if (methodArray.size() < DEFAULT_METHOD_LIMIT) {
          JsonObject methodJson = new JsonObject();
          methodJson.addProperty("signature", methodSignature(method));
          methodJson.addProperty("declaredIn", method.getDeclaringClass().getName());
          methodArray.add(methodJson);
        }
      }
      result.add("methods", methodArray);
      result.addProperty("matchingMethodCount", matchingMethodCount);
      result.addProperty("methodsTruncated", matchingMethodCount > methodArray.size());
      result.addProperty("sourcePath", sourcePath(resolved));
      result.add("documentation", documentationPointers(resolved));
      result.addProperty("contractBasis", "runtime-reflection");
      result.addProperty("nextStep",
          "Use getSchema/getExample for JSON construction; use this endpoint to verify exact Java classes and methods.");
      return GSON.toJson(result);
    } catch (Exception e) {
      return error(className, "API inspection failed: " + e.getMessage());
    }
  }

  /**
   * Resolves a class from a fully qualified name, common-name map, or equipment factory alias.
   *
   * @param requested requested class name or alias
   * @return resolved class, or {@code null} when no safe match exists
   * @throws ClassNotFoundException if a mapped class is unavailable in the running artifact
   */
  private static Class<?> resolveClass(String requested) throws ClassNotFoundException {
    if (requested == null || requested.trim().isEmpty()) {
      return null;
    }
    String trimmed = requested.trim();
    if (!trimmed.matches("[A-Za-z0-9_.$]+")) {
      return null;
    }
    if (trimmed.startsWith("neqsim.")) {
      return Class.forName(trimmed);
    }
    String common = COMMON_CLASSES.get(trimmed.toLowerCase(Locale.ROOT));
    if (common != null) {
      return Class.forName(common);
    }
    try {
      ProcessEquipmentInterface equipment = EquipmentFactory.createEquipment("api-inspection", trimmed);
      return equipment.getClass();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Creates aliases for common non-equipment process classes.
   *
   * @return immutable lower-case alias-to-class map
   */
  private static Map<String, String> createCommonClasses() {
    Map<String, String> classes = new LinkedHashMap<String, String>();
    classes.put("processsystem", "neqsim.process.processmodel.ProcessSystem");
    classes.put("processmodel", "neqsim.process.processmodel.ProcessModel");
    classes.put("jsonprocessbuilder", "neqsim.process.processmodel.JsonProcessBuilder");
    classes.put("processautomation", "neqsim.process.automation.ProcessAutomation");
    classes.put("systeminterface", "neqsim.thermo.system.SystemInterface");
    classes.put("thermodynamicoperations", "neqsim.thermodynamicoperations.ThermodynamicOperations");
    return Collections.unmodifiableMap(classes);
  }

  /**
   * Formats public constructor signatures.
   *
   * @param type class to inspect
   * @return constructor signature array
   */
  private static JsonArray constructorSignatures(Class<?> type) {
    List<String> signatures = new ArrayList<String>();
    for (Constructor<?> constructor : type.getConstructors()) {
      signatures.add(type.getSimpleName() + "(" + parameterTypes(constructor.getParameterTypes()) + ")");
    }
    Collections.sort(signatures);
    JsonArray array = new JsonArray();
    for (String signature : signatures) {
      array.add(signature);
    }
    return array;
  }

  /**
   * Formats a public method signature.
   *
   * @param method method to format
   * @return compact Java-like method signature
   */
  private static String methodSignature(Method method) {
    return simpleTypeName(method.getReturnType()) + " " + method.getName() + "("
        + parameterTypes(method.getParameterTypes()) + ")";
  }

  /**
   * Formats an array of parameter types.
   *
   * @param parameterTypes parameter classes
   * @return comma-separated simple type names
   */
  private static String parameterTypes(Class<?>[] parameterTypes) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < parameterTypes.length; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(simpleTypeName(parameterTypes[i]));
    }
    return builder.toString();
  }

  /**
   * Returns a readable type name, preserving array dimensions.
   *
   * @param type class to format
   * @return readable type name
   */
  private static String simpleTypeName(Class<?> type) {
    if (type.isArray()) {
      return simpleTypeName(type.getComponentType()) + "[]";
    }
    return type.getSimpleName();
  }

  /**
   * Converts class types to names.
   *
   * @param types classes to convert
   * @return JSON array of fully qualified names
   */
  private static JsonArray typeNames(Class<?>[] types) {
    JsonArray array = new JsonArray();
    for (Class<?> type : types) {
      array.add(type.getName());
    }
    return array;
  }

  /**
   * Derives the repository source path for a class.
   *
   * @param type resolved class
   * @return workspace-relative Java source path
   */
  private static String sourcePath(Class<?> type) {
    String topLevelName = type.getName().split("\\$")[0];
    return "src/main/java/" + topLevelName.replace('.', '/') + ".java";
  }

  /**
   * Returns relevant documentation entry points for a class.
   *
   * @param type resolved class
   * @return JSON array of repository-relative documentation paths
   */
  private static JsonArray documentationPointers(Class<?> type) {
    JsonArray docs = new JsonArray();
    String name = type.getName();
    if (name.contains("process.processmodel") || name.contains("process.equipment")) {
      docs.add("docs/process/json_process_models_and_systems.md");
      docs.add("docs/process/index.md");
    } else if (name.contains("process.automation")) {
      docs.add("docs/process/digital-twin-integration.md");
      docs.add("docs/process/index.md");
    } else if (name.contains("thermo") || name.contains("thermodynamicoperations")) {
      docs.add("docs/thermo/index.md");
    }
    return docs;
  }

  /**
   * Builds a structured error response.
   *
   * @param requested requested class or alias
   * @param remediation remediation guidance
   * @return JSON error response
   */
  private static String error(String requested, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("requested", requested == null ? "" : requested);
    error.addProperty("message", "Unable to resolve NeqSim API target");
    error.addProperty("remediation", remediation);
    return GSON.toJson(error);
  }
}