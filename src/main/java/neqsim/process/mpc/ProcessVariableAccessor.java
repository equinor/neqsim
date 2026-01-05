package neqsim.process.mpc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Accessor for reading and writing process variables by path.
 *
 * <p>
 * Provides a unified interface for accessing any variable in a NeqSim ProcessSystem using a simple
 * path notation.
 * </p>
 *
 * <p>
 * Path formats supported:
 * </p>
 * <ul>
 * <li>Simple: "UnitName.propertyName" (e.g., "Feed.flowRate")</li>
 * <li>Nested: "UnitName.subObject.propertyName" (e.g., "Separator.gasOutStream.flowRate")</li>
 * <li>Indexed: "UnitName.propertyName[index]" (e.g., "Feed.composition[0]")</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessVariableAccessor {

  /** The process system to access. */
  private ProcessSystem process;

  /**
   * Constructor.
   *
   * @param process the process system to access
   */
  public ProcessVariableAccessor(ProcessSystem process) {
    this.process = process;
  }

  /** Default units for common properties that require unit parameters. */
  private static final Map<String, String> DEFAULT_UNITS = new HashMap<>();
  static {
    DEFAULT_UNITS.put("flowrate", "kg/hr");
    DEFAULT_UNITS.put("pressure", "bara");
    DEFAULT_UNITS.put("temperature", "K");
    DEFAULT_UNITS.put("enthalpy", "J/mol");
    DEFAULT_UNITS.put("entropy", "J/molK");
    DEFAULT_UNITS.put("density", "kg/m3");
    DEFAULT_UNITS.put("viscosity", "kg/msec");
    DEFAULT_UNITS.put("molarflow", "mol/sec");
    DEFAULT_UNITS.put("volumeflow", "m3/hr");
  }

  /**
   * Get a variable value by path.
   *
   * @param path variable path (e.g., "Feed.flowRate", "Separator.gasOutStream.temperature")
   * @return variable value
   */
  public double getValue(String path) {
    try {
      String[] parts = parsePath(path);
      if (parts.length < 2) {
        throw new IllegalArgumentException("Invalid path format: " + path);
      }

      Object target = resolveTarget(parts, parts.length - 1);
      String propertyName = parts[parts.length - 1];

      // Handle indexed access
      int arrayIndex = -1;
      if (propertyName.contains("[")) {
        int bracketStart = propertyName.indexOf('[');
        int bracketEnd = propertyName.indexOf(']');
        arrayIndex = Integer.parseInt(propertyName.substring(bracketStart + 1, bracketEnd));
        propertyName = propertyName.substring(0, bracketStart);
      }

      String getterName = "get" + capitalize(propertyName);

      // First try getter without parameters
      Method getter = findMethodNoParams(target.getClass(), getterName);
      if (getter != null) {
        Object result = getter.invoke(target);
        return extractNumericValue(result, arrayIndex, path);
      }

      // Try getter with String parameter (unit) using default unit
      Method unitGetter = findMethod(target.getClass(), getterName, String.class);
      if (unitGetter != null) {
        String defaultUnit = getDefaultUnit(propertyName);
        Object result = unitGetter.invoke(target, defaultUnit);
        return extractNumericValue(result, arrayIndex, path);
      }

      throw new IllegalArgumentException("Cannot find getter for: " + propertyName);

    } catch (Exception e) {
      throw new RuntimeException("Failed to get value for path: " + path, e);
    }
  }

  /**
   * Extract numeric value from method result.
   */
  private double extractNumericValue(Object result, int arrayIndex, String path) {
    if (result instanceof Double) {
      return (Double) result;
    } else if (result instanceof double[]) {
      if (arrayIndex >= 0) {
        return ((double[]) result)[arrayIndex];
      }
      throw new IllegalArgumentException("Array access requires index: " + path);
    } else if (result instanceof Number) {
      return ((Number) result).doubleValue();
    }
    throw new IllegalArgumentException("Property does not return a numeric value: " + path);
  }

  /**
   * Get default unit for a property name.
   */
  private String getDefaultUnit(String propertyName) {
    String lowerName = propertyName.toLowerCase();
    for (Map.Entry<String, String> entry : DEFAULT_UNITS.entrySet()) {
      if (lowerName.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return "";
  }

  /**
   * Find a method with no parameters.
   *
   * @param clazz the class to search for the method
   * @param name the method name to find
   * @return the Method object if found, null otherwise
   */
  private Method findMethodNoParams(Class<?> clazz, String name) {
    try {
      Method m = clazz.getMethod(name);
      return m;
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  /**
   * Get a variable value with unit conversion.
   *
   * @param path variable path
   * @param unit desired unit
   * @return variable value in specified unit
   */
  public double getValue(String path, String unit) {
    try {
      String[] parts = parsePath(path);
      if (parts.length < 2) {
        throw new IllegalArgumentException("Invalid path format: " + path);
      }

      Object target = resolveTarget(parts, parts.length - 1);
      String propertyName = parts[parts.length - 1];
      String getterName = "get" + capitalize(propertyName);

      // Try getter with String parameter (unit)
      Method unitGetter = findMethod(target.getClass(), getterName, String.class);
      if (unitGetter != null) {
        Object result = unitGetter.invoke(target, unit);
        if (result instanceof Number) {
          return ((Number) result).doubleValue();
        }
      }

      // Fall back to getter without unit
      return getValue(path);

    } catch (Exception e) {
      throw new RuntimeException("Failed to get value for path: " + path + " with unit: " + unit,
          e);
    }
  }

  /**
   * Set a variable value by path.
   *
   * @param path variable path
   * @param value value to set
   */
  public void setValue(String path, double value) {
    try {
      String[] parts = parsePath(path);
      if (parts.length < 2) {
        throw new IllegalArgumentException("Invalid path format: " + path);
      }

      Object target = resolveTarget(parts, parts.length - 1);
      String propertyName = parts[parts.length - 1];

      // Handle indexed access
      int arrayIndex = -1;
      if (propertyName.contains("[")) {
        int bracketStart = propertyName.indexOf('[');
        int bracketEnd = propertyName.indexOf(']');
        arrayIndex = Integer.parseInt(propertyName.substring(bracketStart + 1, bracketEnd));
        propertyName = propertyName.substring(0, bracketStart);
      }

      // Find setter method
      String setterName = "set" + capitalize(propertyName);

      // First try setter with just double parameter
      Method setter = findMethod(target.getClass(), setterName, double.class);
      if (setter != null) {
        setter.invoke(target, value);
        return;
      }

      // Try Double
      setter = findMethod(target.getClass(), setterName, Double.class);
      if (setter != null) {
        setter.invoke(target, value);
        return;
      }

      // Try setter with (double, String) parameter using default unit
      setter = findMethod(target.getClass(), setterName, double.class, String.class);
      if (setter != null) {
        String defaultUnit = getDefaultUnit(propertyName);
        setter.invoke(target, value, defaultUnit);
        return;
      }

      throw new IllegalArgumentException("Cannot find setter for: " + propertyName);

    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to set value for path: " + path, e);
    }
  }

  /**
   * Set a variable value with unit specification.
   *
   * @param path variable path
   * @param value value to set
   * @param unit unit of the value
   */
  public void setValue(String path, double value, String unit) {
    try {
      String[] parts = parsePath(path);
      if (parts.length < 2) {
        throw new IllegalArgumentException("Invalid path format: " + path);
      }

      Object target = resolveTarget(parts, parts.length - 1);
      String propertyName = parts[parts.length - 1];
      String setterName = "set" + capitalize(propertyName);

      // Try to find setter with unit parameter
      Method setter = findMethod(target.getClass(), setterName, double.class, String.class);

      if (setter != null) {
        setter.invoke(target, value, unit);
      } else {
        // Fall back to setter without unit
        setValue(path, value);
      }

    } catch (Exception e) {
      throw new RuntimeException("Failed to set value for path: " + path + " with unit: " + unit,
          e);
    }
  }

  /**
   * Check if a variable path is valid.
   *
   * @param path variable path
   * @return true if path can be resolved
   */
  public boolean isValidPath(String path) {
    try {
      getValue(path);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Parse a path into components.
   *
   * @param path the dot-separated path string to parse
   * @return array of path components
   */
  private String[] parsePath(String path) {
    return path.split("\\.");
  }

  /**
   * Resolve the target object for a path.
   *
   * @param parts array of path components
   * @param depth number of path components to navigate (excluding the property)
   * @return the resolved target object
   */
  private Object resolveTarget(String[] parts, int depth) {
    // First part is always the unit name
    String unitName = parts[0];
    Object current = process.getUnit(unitName);

    if (current == null) {
      throw new IllegalArgumentException("Unit not found: " + unitName);
    }

    // Navigate through intermediate parts
    for (int i = 1; i < depth; i++) {
      String part = parts[i];

      // Handle indexed access in intermediate parts
      if (part.contains("[")) {
        int bracketStart = part.indexOf('[');
        int bracketEnd = part.indexOf(']');
        int index = Integer.parseInt(part.substring(bracketStart + 1, bracketEnd));
        part = part.substring(0, bracketStart);
      }

      // Try getter
      String getterName = "get" + capitalize(part);
      Method getter = findMethodNoParams(current.getClass(), getterName);

      if (getter == null) {
        // Try direct method
        getter = findMethodNoParams(current.getClass(), part);
      }

      if (getter == null) {
        throw new IllegalArgumentException("Cannot resolve path part: " + part);
      }

      try {
        current = getter.invoke(current);
      } catch (Exception e) {
        throw new RuntimeException("Failed to navigate path at: " + part, e);
      }
    }

    return current;
  }

  /**
   * Find a method by name and parameter types.
   *
   * @param clazz the class to search for the method
   * @param name the method name to find
   * @param paramTypes the parameter types of the method
   * @return the Method object if found, null otherwise
   */
  private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
    try {
      return clazz.getMethod(name, paramTypes);
    } catch (NoSuchMethodException e) {
      // Try superclasses and interfaces
      for (Method m : clazz.getMethods()) {
        if (m.getName().equalsIgnoreCase(name)) {
          if (paramTypes.length == 0 || matchesParams(m, paramTypes)) {
            return m;
          }
        }
      }
      return null;
    }
  }

  /**
   * Check if method parameters match.
   */
  private boolean matchesParams(Method method, Class<?>[] paramTypes) {
    Class<?>[] methodParams = method.getParameterTypes();
    if (methodParams.length != paramTypes.length) {
      return false;
    }
    for (int i = 0; i < paramTypes.length; i++) {
      if (!methodParams[i].isAssignableFrom(paramTypes[i])
          && !isBoxedEquivalent(methodParams[i], paramTypes[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check if types are boxed equivalents.
   */
  private boolean isBoxedEquivalent(Class<?> a, Class<?> b) {
    return (a == double.class && b == Double.class) || (a == Double.class && b == double.class)
        || (a == int.class && b == Integer.class) || (a == Integer.class && b == int.class);
  }

  /**
   * Capitalize first letter.
   */
  private String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
