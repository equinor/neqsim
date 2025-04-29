package neqsim.process.processmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.thermo.system.SystemInterface;
import java.io.File;
import java.util.List;
import java.util.Map;
import neqsim.thermo.system.SystemSrkEos;

public class ProcessLoader {

  public static void loadProcessFromYaml(File yamlFile, ProcessSystem process) throws Exception {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    Map<String, List<Map<String, Object>>> data = mapper.readValue(yamlFile, Map.class);
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

  private static void setProperty(ProcessEquipmentInterface unit, String property, Object value) {
    try {
      String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);

      if (value instanceof List) {
        List<?> list = (List<?>) value;
        java.lang.reflect.Method method =
            unit.getClass().getMethod(setterName, double.class, String.class);
        method.invoke(unit, ((Number) list.get(0)).doubleValue(), (String) list.get(1));
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
