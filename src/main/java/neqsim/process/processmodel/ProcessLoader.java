package neqsim.process.processmodel;

import java.io.File;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * The ProcessLoader class is responsible for loading process configurations from a YAML file and
 * initializing the process system with the specified units and their properties.
 *
 * @author esol
 */
public class ProcessLoader {
  /**
   * Loads a process from a YAML file or YAML string and initializes the process system.
   *
   * @param yamlFile the YAML file containing the process configuration (nullable if yamlString is
   *        used)
   * @param yamlString the YAML string containing the process configuration (nullable if yamlFile is
   *        used)
   * @param process the process system to initialize
   * @throws java.lang.Exception if loading or parsing fails
   */
  public static void loadProcessFromYaml(File yamlFile, String yamlString, ProcessSystem process)
      throws Exception {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Map<String, List<Map<String, Object>>> data;
    if (yamlFile != null) {
      data = mapper.readValue(yamlFile, Map.class);
    } else if (yamlString != null) {
      data = mapper.readValue(yamlString, Map.class);
    } else {
      throw new IllegalArgumentException("Either yamlFile or yamlString must be provided");
    }
    List<Map<String, Object>> steps = data.get("process");
    for (Map<String, Object> step : steps) {
      String unitType = (String) step.get("unit");
      Map<String, Object> properties = (Map<String, Object>) step.get("properties");
      ProcessEquipmentInterface unit = process.addUnit(unitType);
      if (properties != null) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
          setProperty(unit, entry.getKey(), entry.getValue());
        }
      }
    }
  }

  /**
   * Loads a process from a YAML file and initializes the process system.
   *
   * @param yamlFile the YAML file containing the process configuration
   * @param process the process system to initialize
   * @throws java.lang.Exception if loading or parsing fails
   */
  public static void loadProcessFromYaml(File yamlFile, ProcessSystem process) throws Exception {
    loadProcessFromYaml(yamlFile, null, process);
  }

  /**
   * Loads a process from a YAML string and initializes the process system.
   *
   * @param yamlString the YAML string containing the process configuration
   * @param process the process system to initialize
   * @throws java.lang.Exception if loading or parsing fails
   */
  public static void loadProcessFromYaml(String yamlString, ProcessSystem process)
      throws Exception {
    loadProcessFromYaml(null, yamlString, process);
  }

  private static void setProperty(ProcessEquipmentInterface unit, String property, Object value) {
    try {
      String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);

      if (value instanceof List) {
        List<?> list = (List<?>) value;
        java.lang.reflect.Method method =
            unit.getClass().getMethod(setterName, double.class, String.class);
        method.invoke(unit, ((Number) list.get(0)).doubleValue(), list.get(1));
      } else if (value instanceof Map) {
        if (property.equalsIgnoreCase("fluid")) {
          // Assume user gives Map<String, Double> for fluid components
          SystemInterface fluid = new SystemSrkEos();
          Map<String, Double> components = (Map<String, Double>) value;
          for (Map.Entry<String, Double> comp : components.entrySet()) {
            fluid.addComponent(comp.getKey(), comp.getValue());
          }
          java.lang.reflect.Method method =
              unit.getClass().getMethod("setFluid", SystemInterface.class);
          method.invoke(unit, fluid);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
