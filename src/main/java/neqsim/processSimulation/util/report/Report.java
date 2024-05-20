package neqsim.processSimulation.util.report;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processSystem.ProcessModule;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Report class.
 * </p>
 * 
 * @author even
 * @version $Id: $Id
 */
public class Report {
  ProcessSystem process = null;
  ProcessEquipmentBaseClass processEquipment = null;
  SystemInterface fluid = null;

  public Report(ProcessSystem process) {
    this.process = process;
  }

  public Report(SystemInterface fluid) {
    this.fluid = fluid;
  }

  public Report(ProcessEquipmentBaseClass processEquipmentBaseClass) {
    processEquipment = processEquipmentBaseClass;
  }

  public Report(ProcessModule processModule) {
    // TODO Auto-generated constructor stub
  }

  public Report(ProcessModuleBaseClass processModuleBaseClass) {
    // TODO Auto-generated constructor stub
  }

  public String generateJsonReport() {
    Map<String, String> json_reports = new HashMap<>();

    if (process != null) {
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        json_reports.put(unit.getName(), unit.toJson());
      }
    }
    if (processEquipment != null) {
      json_reports.put(processEquipment.getName(), processEquipment.toJson());
    }
    if (fluid != null) {
      json_reports.put(fluid.getFluidName(), fluid.toJson());
    }

    // Create a JsonObject to hold the parsed nested JSON objects
    JsonObject finalJsonObject = new JsonObject();

    // Iterate through the entries of the json_reports map
    for (Map.Entry<String, String> entry : json_reports.entrySet()) {
      // Parse each value as a separate JSON object using the static parseString method
      JsonObject nestedJsonObject = JsonParser.parseString(entry.getValue()).getAsJsonObject();
      // Update the final JsonObject with the parsed JSON object
      finalJsonObject.add(entry.getKey(), nestedJsonObject);
    }

    // Convert the final JsonObject to a JSON string with pretty printing
    Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
    return prettyGson.toJson(finalJsonObject);
  }
}
