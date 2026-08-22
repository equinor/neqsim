package neqsim.mcp.runners;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.util.annotation.AIExposable;

/**
 * Discovers runtime NeqSim capabilities and invokes bounded static calculations.
 *
 * <p>
 * Discovery is derived from the classes in the running NeqSim artifact, so newly added equipment and calculation
 * methods can be found without adding a domain-specific MCP adapter. Execution is intentionally narrower than
 * discovery: only public static methods with JSON-safe inputs, serializable outputs, calculation-style names, and no
 * unsafe annotation are invoked. Stateful process equipment is reported with the {@code runProcess} route so stream
 * wiring, validation, initialization, and convergence remain owned by {@code JsonProcessBuilder}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class GeneralCapabilityRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
  private static final String ROOT_PACKAGE = "neqsim";
  private static final String ROOT_PACKAGE_PATH = "neqsim";
  private static final int DEFAULT_RESULT_LIMIT = 25;
  private static final int MAX_RESULT_LIMIT = 100;
  private static final int MAX_REQUEST_CHARACTERS = 65536;
  private static final int MAX_ARGUMENT_COUNT = 32;
  private static final int MAX_ARRAY_ELEMENTS = 4096;
  private static final int MAX_STRING_CHARACTERS = 16384;
  private static final int MAX_RESULT_BYTES = 262144;
  private static final long INVOCATION_TIMEOUT_SECONDS = 5L;
  private static final List<String> NON_CALCULATION_PACKAGE_PREFIXES = Collections
      .unmodifiableList(Arrays.asList("neqsim.mcp.", "neqsim.util.agentic.", "neqsim.util.validation."));
  private static final Set<String> QUERY_STOP_WORDS = Collections
      .unmodifiableSet(new HashSet<String>(Arrays.asList("a", "all", "an", "and", "any", "calculate", "calculation",
          "capability", "find", "for", "function", "functionality", "method", "of", "run", "the", "to", "using")));
  private static final List<String> SAFE_METHOD_PREFIXES = Collections
      .unmodifiableList(Arrays.asList("calc", "check", "compute", "convert", "derive", "determine", "estimate",
          "evaluate", "get", "has", "interpolate", "is", "predict", "solve", "validate"));
  private static final List<String> UNSAFE_NAME_FRAGMENTS = Collections.unmodifiableList(
      Arrays.asList("classloader", "connection", "delete", "execute", "file", "import", "load", "network", "open",
          "path", "processbuilder", "read", "resource", "runtime", "save", "socket", "systemproperty", "url", "write"));
  private static volatile List<Capability> cachedCapabilities;

  /** Utility class; do not instantiate. */
  private GeneralCapabilityRunner() {
  }

  /**
   * Runs a capability action from JSON.
   *
   * @param json request with action {@code search} or {@code invoke}
   * @return structured JSON response
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return error("INPUT_ERROR", "Capability request is empty",
          "Provide JSON with action 'search' and query, or action 'invoke' with className, methodName, and arguments.");
    }
    if (json.length() > MAX_REQUEST_CHARACTERS) {
      return error("INPUT_TOO_LARGE", "Capability request exceeds " + MAX_REQUEST_CHARACTERS + " characters",
          "Reduce argument arrays or use a curated runner designed for bulk data.");
    }
    try {
      JsonObject request = JsonParser.parseString(json).getAsJsonObject();
      String action = requiredString(request, "action");
      if ("search".equalsIgnoreCase(action)) {
        return search(requiredString(request, "query"), optionalInt(request, "limit", DEFAULT_RESULT_LIMIT));
      }
      if ("invoke".equalsIgnoreCase(action)) {
        return invoke(request);
      }
      return error("UNKNOWN_ACTION", "Unknown capability action: " + action,
          "Use action 'search' to discover runtime APIs or 'invoke' for a discovered static-json method.");
    } catch (Exception e) {
      return error("INPUT_ERROR", "Invalid capability request: " + message(e),
          "Check the run_capability input schema and provide a valid JSON object.");
    }
  }

  /**
   * Searches the runtime capability index.
   *
   * @param query free-text capability query
   * @param requestedLimit maximum number of matches requested
   * @return structured JSON search response
   */
  public static String search(String query, int requestedLimit) {
    if (query == null || query.trim().isEmpty()) {
      return error("INPUT_ERROR", "Capability query is empty", "Describe a method, property, or equipment function.");
    }
    int limit = Math.max(1, Math.min(MAX_RESULT_LIMIT, requestedLimit));
    List<String> tokens = queryTokens(query);
    List<ScoredCapability> scored = new ArrayList<ScoredCapability>();
    for (Capability capability : capabilities()) {
      int score = capability.score(tokens, query);
      if (score > 0) {
        scored.add(new ScoredCapability(capability, score));
      }
    }
    Collections.sort(scored, new Comparator<ScoredCapability>() {
      @Override
      public int compare(ScoredCapability left, ScoredCapability right) {
        int byScore = Integer.compare(right.score, left.score);
        if (byScore != 0) {
          return byScore;
        }
        return left.capability.signature.compareTo(right.capability.signature);
      }
    });

    JsonArray matches = new JsonArray();
    for (int i = 0; i < scored.size() && i < limit; i++) {
      matches.add(scored.get(i).capability.toJson(scored.get(i).score));
    }
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.addProperty("action", "search");
    result.addProperty("query", query);
    result.addProperty("indexedMethodCount", capabilities().size());
    result.addProperty("matchCount", scored.size());
    result.addProperty("returnedCount", matches.size());
    result.add("matches", matches);
    result.addProperty("contractBasis", "runtime-classpath-reflection");
    result.addProperty("nextStep",
        "Invoke matches labelled static-json with action 'invoke'. Use runProcess for process-json matches; use inspectApi for setup details.");
    return GSON.toJson(result);
  }

  /**
   * Invokes a bounded public static NeqSim method.
   *
   * @param request parsed invocation request
   * @return structured JSON invocation response
   */
  private static String invoke(JsonObject request) {
    long startNanos = System.nanoTime();
    String className = requiredString(request, "className");
    String methodName = requiredString(request, "methodName");
    if (!isSafeClassName(className)) {
      return error("CLASS_NOT_ALLOWED", "Only classes in the neqsim.* namespace can be invoked",
          "Use search first and pass the exact className returned by a static-json match.");
    }
    JsonArray arguments = request.has("arguments") ? request.getAsJsonArray("arguments") : new JsonArray();
    if (arguments.size() > MAX_ARGUMENT_COUNT) {
      return error("INPUT_TOO_LARGE", "Capability invocation exceeds " + MAX_ARGUMENT_COUNT + " arguments",
          "Use a smaller static calculation signature or a curated runner for structured inputs.");
    }
    JsonArray parameterTypes = request.has("parameterTypes") ? request.getAsJsonArray("parameterTypes") : null;
    try {
      Class<?> type = Class.forName(className, false, GeneralCapabilityRunner.class.getClassLoader());
      List<Method> candidates = invocationCandidates(type, methodName, arguments.size(), parameterTypes);
      if (candidates.isEmpty()) {
        return error("METHOD_NOT_EXECUTABLE", "No matching bounded static method was found",
            "Use search to confirm executionMode='static-json'. Stateful equipment and instance methods must use runProcess or a curated runner.");
      }
      if (candidates.size() > 1) {
        return ambiguousMethodError(className, methodName, candidates);
      }
      Method method = candidates.get(0);
      JsonElement value = invokeWithTimeout(method, arguments);
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("action", "invoke");
      result.addProperty("className", className);
      result.addProperty("methodName", methodName);
      result.addProperty("signature", signature(method));
      result.add("result", value);
      result.addProperty("executionMode", "static-json");
      result.addProperty("computationTimeMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
      JsonObject provenance = new JsonObject();
      provenance.addProperty("contractBasis", "runtime-reflection");
      provenance.addProperty("declaredIn", method.getDeclaringClass().getName());
      provenance.addProperty("safetyPolicy", "bounded-static-json-v1");
      provenance.addProperty("timeoutSeconds", INVOCATION_TIMEOUT_SECONDS);
      result.add("provenance", provenance);
      return GSON.toJson(result);
    } catch (ClassNotFoundException e) {
      return error("CLASS_NOT_FOUND", "NeqSim class not found: " + className,
          "Use the exact className returned by capability search.");
    } catch (TimeoutException e) {
      return error("TIMEOUT", "Static calculation exceeded the execution timeout",
          "Use a curated runner or runProcess for long-running and stateful calculations.");
    } catch (Exception e) {
      return error("INVOCATION_ERROR", "Static calculation failed: " + message(e),
          "Check argument values and parameterTypes against the signature returned by search or inspectApi.");
    }
  }

  /**
   * Returns methods matching an invocation request and the bounded execution policy.
   *
   * @param type requested class
   * @param methodName exact public method name
   * @param argumentCount number of JSON arguments
   * @param requestedTypes optional exact parameter type names
   * @return sorted matching methods
   */
  private static List<Method> invocationCandidates(Class<?> type, String methodName, int argumentCount,
      JsonArray requestedTypes) {
    List<Method> candidates = new ArrayList<Method>();
    for (Method method : type.getMethods()) {
      if (!method.getName().equals(methodName) || method.getParameterTypes().length != argumentCount
          || !isExecutable(method) || !matchesRequestedTypes(method.getParameterTypes(), requestedTypes)) {
        continue;
      }
      candidates.add(method);
    }
    Collections.sort(candidates, new Comparator<Method>() {
      @Override
      public int compare(Method left, Method right) {
        return signature(left).compareTo(signature(right));
      }
    });
    return candidates;
  }

  /**
   * Checks optional parameter type selectors against a method signature.
   *
   * @param actualTypes method parameter types
   * @param requestedTypes requested simple or fully qualified names, or {@code null}
   * @return true when no selector was supplied or every type matches
   */
  private static boolean matchesRequestedTypes(Class<?>[] actualTypes, JsonArray requestedTypes) {
    if (requestedTypes == null) {
      return true;
    }
    if (requestedTypes.size() != actualTypes.length) {
      return false;
    }
    for (int i = 0; i < actualTypes.length; i++) {
      String requested = requestedTypes.get(i).getAsString();
      if (!actualTypes[i].getName().equals(requested) && !actualTypes[i].getSimpleName().equals(requested)
          && !readableType(actualTypes[i]).equals(requested)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Converts JSON arguments to a method's supported Java parameter types.
   *
   * @param arguments JSON arguments
   * @param parameterTypes target Java types
   * @return converted argument array
   */
  private static Object[] convertArguments(JsonArray arguments, Class<?>[] parameterTypes) {
    Object[] converted = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      converted[i] = convertArgument(arguments.get(i), parameterTypes[i]);
    }
    return converted;
  }

  /**
   * Converts one JSON value to a supported Java type.
   *
   * @param value JSON value
   * @param targetType target Java class
   * @return converted value
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static Object convertArgument(JsonElement value, Class<?> targetType) {
    if (value == null || value.isJsonNull()) {
      if (targetType.isPrimitive()) {
        throw new IllegalArgumentException("null cannot be passed to primitive " + targetType.getName());
      }
      return null;
    }
    if (targetType == String.class) {
      String text = value.getAsString();
      if (text.length() > MAX_STRING_CHARACTERS) {
        throw new IllegalArgumentException("String argument exceeds " + MAX_STRING_CHARACTERS + " characters");
      }
      return text;
    }
    if (targetType == boolean.class || targetType == Boolean.class) {
      return value.getAsBoolean();
    }
    if (targetType == char.class || targetType == Character.class) {
      String text = value.getAsString();
      if (text.length() != 1) {
        throw new IllegalArgumentException("char arguments must contain exactly one character");
      }
      return Character.valueOf(text.charAt(0));
    }
    if (targetType == byte.class || targetType == Byte.class) {
      return Byte.valueOf(value.getAsByte());
    }
    if (targetType == short.class || targetType == Short.class) {
      return Short.valueOf(value.getAsShort());
    }
    if (targetType == int.class || targetType == Integer.class) {
      return Integer.valueOf(value.getAsInt());
    }
    if (targetType == long.class || targetType == Long.class) {
      return Long.valueOf(value.getAsLong());
    }
    if (targetType == float.class || targetType == Float.class) {
      return Float.valueOf(value.getAsFloat());
    }
    if (targetType == double.class || targetType == Double.class) {
      return Double.valueOf(value.getAsDouble());
    }
    if (targetType.isEnum()) {
      String requested = value.getAsString();
      for (Object constant : targetType.getEnumConstants()) {
        if (((Enum) constant).name().equalsIgnoreCase(requested)) {
          return constant;
        }
      }
      throw new IllegalArgumentException("Unknown " + targetType.getSimpleName() + " value: " + requested);
    }
    if (targetType.isArray()) {
      JsonArray values = value.getAsJsonArray();
      if (values.size() > MAX_ARRAY_ELEMENTS) {
        throw new IllegalArgumentException("Array argument exceeds " + MAX_ARRAY_ELEMENTS + " elements");
      }
      Object array = Array.newInstance(targetType.getComponentType(), values.size());
      for (int i = 0; i < values.size(); i++) {
        Array.set(array, i, convertArgument(values.get(i), targetType.getComponentType()));
      }
      return array;
    }
    throw new IllegalArgumentException("Unsupported JSON parameter type: " + targetType.getName());
  }

  /**
   * Invokes a method on a daemon worker with a fixed timeout.
   *
   * @param method static method to invoke
   * @param arguments JSON arguments to convert inside the timed worker
   * @return bounded JSON method result
   * @throws Exception when invocation fails
   * @throws TimeoutException when the calculation exceeds the timeout
   */
  private static JsonElement invokeWithTimeout(final Method method, final JsonArray arguments)
      throws Exception, TimeoutException {
    ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "neqsim-capability-invocation");
        thread.setDaemon(true);
        return thread;
      }
    });
    Future<JsonElement> future = executor.submit(new Callable<JsonElement>() {
      @Override
      public JsonElement call() throws Exception {
        try {
          Object[] converted = convertArguments(arguments, method.getParameterTypes());
          JsonElement result = toJsonResult(method.invoke(null, converted));
          int resultBytes = GSON.toJson(result).getBytes(StandardCharsets.UTF_8).length;
          if (resultBytes > MAX_RESULT_BYTES) {
            throw new IllegalArgumentException("Result exceeds " + MAX_RESULT_BYTES + " bytes");
          }
          return result;
        } catch (InvocationTargetException e) {
          Throwable cause = e.getCause();
          if (cause instanceof Exception) {
            throw (Exception) cause;
          }
          throw new IllegalStateException(cause == null ? e.getMessage() : cause.getMessage(), cause);
        }
      }
    });
    try {
      return future.get(INVOCATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw new IllegalStateException(cause == null ? e.getMessage() : cause.getMessage(), cause);
    } finally {
      future.cancel(true);
      executor.shutdownNow();
    }
  }

  /**
   * Converts a supported method result to JSON.
   *
   * @param value method result
   * @return JSON representation
   */
  private static JsonElement toJsonResult(Object value) {
    if (value == null) {
      return GSON.toJsonTree(null);
    }
    if (!isSupportedOutputType(value.getClass())) {
      throw new IllegalArgumentException("Unsupported result type: " + value.getClass().getName());
    }
    return GSON.toJsonTree(value);
  }

  /**
   * Builds the runtime capability index once.
   *
   * @return immutable capability list
   */
  private static List<Capability> capabilities() {
    if (cachedCapabilities == null) {
      synchronized (GeneralCapabilityRunner.class) {
        if (cachedCapabilities == null) {
          cachedCapabilities = buildCapabilities();
        }
      }
    }
    return cachedCapabilities;
  }

  /**
   * Scans public methods declared by runtime NeqSim classes.
   *
   * @return immutable sorted capability list
   */
  private static List<Capability> buildCapabilities() {
    List<Capability> result = new ArrayList<Capability>();
    ClassLoader classLoader = GeneralCapabilityRunner.class.getClassLoader();
    for (String className : discoverClassNames(classLoader)) {
      try {
        Class<?> type = Class.forName(className, false, classLoader);
        for (Method method : type.getDeclaredMethods()) {
          if (Modifier.isPublic(method.getModifiers()) && !method.isBridge() && !method.isSynthetic()) {
            result.add(new Capability(type, method));
          }
        }
      } catch (LinkageError | ReflectiveOperationException | SecurityException e) {
        // Optional integrations may not be linkable in every runtime distribution.
      }
    }
    Collections.sort(result, new Comparator<Capability>() {
      @Override
      public int compare(Capability left, Capability right) {
        return left.signature.compareTo(right.signature);
      }
    });
    return Collections.unmodifiableList(result);
  }

  /**
   * Discovers top-level classes below the NeqSim root package.
   *
   * @param classLoader runtime class loader
   * @return sorted unique class names
   */
  private static List<String> discoverClassNames(ClassLoader classLoader) {
    Set<String> classNames = new LinkedHashSet<String>();
    try {
      Enumeration<URL> resources = classLoader.getResources(ROOT_PACKAGE_PATH);
      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        if ("file".equals(resource.getProtocol())) {
          collectDirectoryClassNames(new File(new URI(resource.toString())), ROOT_PACKAGE, classNames);
        } else if ("jar".equals(resource.getProtocol())) {
          collectJarClassNames(resource, classNames);
        }
      }
    } catch (IOException | URISyntaxException e) {
      return Collections.emptyList();
    }
    List<String> sorted = new ArrayList<String>(classNames);
    Collections.sort(sorted);
    return sorted;
  }

  /**
   * Collects top-level classes recursively from an exploded class directory.
   *
   * @param directory current package directory
   * @param packageName package represented by the directory
   * @param classNames destination set
   */
  private static void collectDirectoryClassNames(File directory, String packageName, Set<String> classNames) {
    File[] files = directory.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        collectDirectoryClassNames(file, packageName + "." + file.getName(), classNames);
      } else if (isTopLevelClassFile(file.getName())) {
        classNames.add(packageName + "." + file.getName().substring(0, file.getName().length() - 6));
      }
    }
  }

  /**
   * Collects top-level NeqSim classes from a JAR.
   *
   * @param resource package resource in a JAR
   * @param classNames destination set
   * @throws IOException if the JAR cannot be read
   */
  private static void collectJarClassNames(URL resource, Set<String> classNames) throws IOException {
    JarURLConnection connection = (JarURLConnection) resource.openConnection();
    JarFile jarFile = connection.getJarFile();
    Enumeration<JarEntry> entries = jarFile.entries();
    while (entries.hasMoreElements()) {
      String entryName = entries.nextElement().getName();
      if (entryName.startsWith(ROOT_PACKAGE_PATH + "/") && isTopLevelClassFile(entryName)) {
        classNames.add(entryName.substring(0, entryName.length() - 6).replace('/', '.'));
      }
    }
  }

  /**
   * Checks whether a classpath entry represents a top-level class.
   *
   * @param name file or JAR entry name
   * @return true for top-level class files
   */
  private static boolean isTopLevelClassFile(String name) {
    return name.endsWith(".class") && name.indexOf('$') < 0 && !name.endsWith("package-info.class")
        && !name.endsWith("module-info.class");
  }

  /**
   * Determines whether a method can be invoked by the bounded static policy.
   *
   * @param method method to classify
   * @return true when the method is executable
   */
  private static boolean isExecutable(Method method) {
    if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
        || !isCalculationClass(method.getDeclaringClass()) || method.getReturnType() == void.class
        || !isSupportedOutputType(method.getReturnType())) {
      return false;
    }
    AIExposable annotation = method.getAnnotation(AIExposable.class);
    if (annotation != null && !annotation.safe()) {
      return false;
    }
    String lowerName = method.getName().toLowerCase(Locale.ROOT);
    if (containsAny(lowerName, UNSAFE_NAME_FRAGMENTS)) {
      return false;
    }
    if (annotation == null || !annotation.safe()) {
      if (!startsWithAny(lowerName, SAFE_METHOD_PREFIXES)) {
        return false;
      }
    }
    for (Class<?> parameterType : method.getParameterTypes()) {
      if (!isSupportedInputType(parameterType)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determines whether a Java type can be populated from JSON.
   *
   * @param type parameter type
   * @return true for supported scalar, enum, and bounded array types
   */
  private static boolean isSupportedInputType(Class<?> type) {
    if (type.isArray()) {
      return isSupportedInputType(type.getComponentType());
    }
    return type.isPrimitive() || type == String.class || type == Boolean.class || type == Character.class
        || Number.class.isAssignableFrom(type) || type.isEnum();
  }

  /**
   * Determines whether a Java type can be safely serialized as a bounded result.
   *
   * @param type result type
   * @return true for supported scalar and container types
   */
  private static boolean isSupportedOutputType(Class<?> type) {
    if (type.isArray()) {
      return isSupportedOutputType(type.getComponentType());
    }
    return type.isPrimitive() || type == String.class || type == Boolean.class || type == Character.class
        || Number.class.isAssignableFrom(type) || type.isEnum();
  }

  /**
   * Checks whether a declaring class belongs to a domain calculation package rather than MCP orchestration.
   *
   * @param type declaring class
   * @return true for NeqSim domain classes eligible for bounded static execution
   */
  private static boolean isCalculationClass(Class<?> type) {
    String className = type.getName();
    if (!className.startsWith("neqsim.")) {
      return false;
    }
    for (String prefix : NON_CALCULATION_PACKAGE_PREFIXES) {
      if (className.startsWith(prefix)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks a class name against the invocation namespace policy.
   *
   * @param className fully qualified class name
   * @return true when it is a syntactically valid NeqSim class name
   */
  private static boolean isSafeClassName(String className) {
    return className != null && className.startsWith("neqsim.") && className.matches("[A-Za-z0-9_.$]+");
  }

  /**
   * Tokenizes a free-text query and removes generic search words.
   *
   * @param query free-text query
   * @return normalized meaningful tokens
   */
  private static List<String> queryTokens(String query) {
    List<String> result = new ArrayList<String>();
    for (String token : query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (!token.isEmpty() && !QUERY_STOP_WORDS.contains(token)) {
        result.add(token);
      }
    }
    if (result.isEmpty()) {
      result.add(query.trim().toLowerCase(Locale.ROOT));
    }
    return result;
  }

  /**
   * Checks whether text starts with any supplied prefix.
   *
   * @param text normalized text
   * @param prefixes candidate prefixes
   * @return true when a prefix matches
   */
  private static boolean startsWithAny(String text, List<String> prefixes) {
    for (String prefix : prefixes) {
      if (text.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether text contains any supplied fragment.
   *
   * @param text normalized text
   * @param fragments candidate fragments
   * @return true when a fragment matches
   */
  private static boolean containsAny(String text, List<String> fragments) {
    for (String fragment : fragments) {
      if (text.contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reads a required string property.
   *
   * @param object JSON object
   * @param property property name
   * @return non-empty property value
   */
  private static String requiredString(JsonObject object, String property) {
    if (!object.has(property) || object.get(property).isJsonNull()
        || object.get(property).getAsString().trim().isEmpty()) {
      throw new IllegalArgumentException("Missing required property: " + property);
    }
    return object.get(property).getAsString().trim();
  }

  /**
   * Reads an optional integer property.
   *
   * @param object JSON object
   * @param property property name
   * @param defaultValue value used when the property is absent
   * @return parsed or default value
   */
  private static int optionalInt(JsonObject object, String property, int defaultValue) {
    return object.has(property) ? object.get(property).getAsInt() : defaultValue;
  }

  /**
   * Formats a compact method signature.
   *
   * @param method method to format
   * @return Java-like signature
   */
  private static String signature(Method method) {
    StringBuilder result = new StringBuilder();
    result.append(readableType(method.getReturnType())).append(' ').append(method.getDeclaringClass().getName())
        .append('.').append(method.getName()).append('(');
    for (int i = 0; i < method.getParameterTypes().length; i++) {
      if (i > 0) {
        result.append(", ");
      }
      result.append(readableType(method.getParameterTypes()[i]));
    }
    return result.append(')').toString();
  }

  /**
   * Formats a Java type while retaining array dimensions.
   *
   * @param type type to format
   * @return readable type name
   */
  private static String readableType(Class<?> type) {
    return type.isArray() ? readableType(type.getComponentType()) + "[]" : type.getSimpleName();
  }

  /**
   * Builds an ambiguity response with candidate signatures.
   *
   * @param className requested class
   * @param methodName requested method
   * @param candidates matching overloads
   * @return JSON error response
   */
  private static String ambiguousMethodError(String className, String methodName, List<Method> candidates) {
    JsonObject error = baseError("AMBIGUOUS_METHOD", "Multiple bounded overloads match " + className + "." + methodName,
        "Pass parameterTypes using simple or fully qualified Java type names.");
    JsonArray signatures = new JsonArray();
    for (Method candidate : candidates) {
      signatures.add(signature(candidate));
    }
    error.add("candidates", signatures);
    return GSON.toJson(error);
  }

  /**
   * Builds a structured error response.
   *
   * @param code stable error code
   * @param message error summary
   * @param remediation actionable recovery guidance
   * @return JSON error response
   */
  private static String error(String code, String message, String remediation) {
    return GSON.toJson(baseError(code, message, remediation));
  }

  /**
   * Builds a mutable structured error object.
   *
   * @param code stable error code
   * @param message error summary
   * @param remediation actionable recovery guidance
   * @return JSON error object
   */
  private static JsonObject baseError(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("code", code);
    error.addProperty("message", message);
    error.addProperty("remediation", remediation);
    return error;
  }

  /**
   * Returns the most useful message from a nested reflection exception.
   *
   * @param throwable failure to summarize
   * @return non-null message
   */
  private static String message(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  /** Runtime metadata for one public method. */
  private static final class Capability {
    private final String className;
    private final String simpleClassName;
    private final String methodName;
    private final String signature;
    private final String searchText;
    private final boolean staticMethod;
    private final boolean processEquipment;
    private final boolean executable;

    /**
     * Creates method capability metadata.
     *
     * @param type declaring class
     * @param method public declared method
     */
    private Capability(Class<?> type, Method method) {
      className = type.getName();
      simpleClassName = type.getSimpleName();
      methodName = method.getName();
      signature = GeneralCapabilityRunner.signature(method);
      staticMethod = Modifier.isStatic(method.getModifiers());
      processEquipment = ProcessEquipmentInterface.class.isAssignableFrom(type);
      executable = isExecutable(method);
      StringBuilder searchable = new StringBuilder(className).append(' ').append(simpleClassName).append(' ')
          .append(methodName).append(' ').append(signature);
      AIExposable annotation = method.getAnnotation(AIExposable.class);
      if (annotation != null) {
        searchable.append(' ').append(annotation.description()).append(' ').append(annotation.category());
        for (String tag : annotation.tags()) {
          searchable.append(' ').append(tag);
        }
      }
      searchText = searchable.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Scores this capability against a normalized query.
     *
     * @param tokens meaningful query tokens
     * @param originalQuery original free text
     * @return positive relevance score, or zero for no match
     */
    private int score(List<String> tokens, String originalQuery) {
      int matched = 0;
      int score = 0;
      for (String token : tokens) {
        if (searchText.contains(token)) {
          matched++;
          score += methodName.toLowerCase(Locale.ROOT).contains(token) ? 4 : 2;
        }
      }
      int requiredMatches = Math.max(1, tokens.size() - 1);
      if (matched < requiredMatches) {
        return 0;
      }
      if (searchText.contains(originalQuery.trim().toLowerCase(Locale.ROOT))) {
        score += 5;
      }
      if (executable) {
        score += 2;
      }
      return score;
    }

    /**
     * Converts this capability to its public search representation.
     *
     * @param score relevance score
     * @return JSON capability object
     */
    private JsonObject toJson(int score) {
      JsonObject json = new JsonObject();
      json.addProperty("className", className);
      json.addProperty("classSimpleName", simpleClassName);
      json.addProperty("methodName", methodName);
      json.addProperty("signature", signature);
      json.addProperty("static", staticMethod);
      json.addProperty("executable", executable);
      json.addProperty("score", score);
      if (executable) {
        json.addProperty("executionMode", "static-json");
        json.addProperty("route", "runCapability action=invoke");
      } else if (processEquipment) {
        json.addProperty("executionMode", "process-json");
        json.addProperty("route", "runProcess with equipment type " + simpleClassName);
      } else {
        json.addProperty("executionMode", "inspection-only");
        json.addProperty("route", "inspectApi or a curated runner/plugin");
      }
      json.addProperty("sourcePath", "src/main/java/" + className.replace('.', '/') + ".java");
      return json;
    }
  }

  /** Capability paired with its query relevance score. */
  private static final class ScoredCapability {
    private final Capability capability;
    private final int score;

    /**
     * Creates a scored capability.
     *
     * @param capability capability metadata
     * @param score relevance score
     */
    private ScoredCapability(Capability capability, int score) {
      this.capability = capability;
      this.score = score;
    }
  }
}