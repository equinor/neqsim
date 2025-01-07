package neqsim.process.util.report;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessModuleBaseClass;
import neqsim.process.processmodel.ProcessSystem;
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
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Report.class);
  ProcessSystem process = null;
  ProcessEquipmentBaseClass processEquipment = null;
  SystemInterface fluid = null;

  /**
   * <p>
   * Constructor for Report.
   * </p>
   *
   * @param process a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public Report(ProcessSystem process) {
    this.process = process;
  }

  /**
   * <p>
   * Constructor for Report.
   * </p>
   *
   * @param fluid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Report(SystemInterface fluid) {
    this.fluid = fluid;
  }

  /**
   * <p>
   * Constructor for Report.
   * </p>
   *
   * @param processEquipmentBaseClass a {@link neqsim.process.equipment.ProcessEquipmentBaseClass}
   *        object
   */
  public Report(ProcessEquipmentBaseClass processEquipmentBaseClass) {
    processEquipment = processEquipmentBaseClass;
  }

  /**
   * <p>
   * Constructor for Report.
   * </p>
   *
   * @param processModule a {@link neqsim.process.processmodel.ProcessModule} object
   */
  public Report(ProcessModule processModule) {
    // TODO Auto-generated constructor stub
  }

  /**
   * <p>
   * Constructor for Report.
   * </p>
   *
   * @param processModuleBaseClass a {@link neqsim.process.processmodel.ProcessModuleBaseClass}
   *        object
   */
  public Report(ProcessModuleBaseClass processModuleBaseClass) {
    // TODO Auto-generated constructor stub
  }

  /**
   * <p>
   * generateJsonReport.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
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
      try {
        String s = entry.getValue();
        if (s == null) {
          // Not necessary to log that an entry is null
          continue;
        }
        JsonObject nestedJsonObject = JsonParser.parseString(s).getAsJsonObject();

        // Update the final JsonObject with the parsed JSON object
        finalJsonObject.add(entry.getKey(), nestedJsonObject);
      } catch (Exception ex) {
        logger.error(ex.getMessage());
      }
    }

    // Convert the final JsonObject to a JSON string with pretty printing
    Gson prettyGson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return prettyGson.toJson(finalJsonObject);
  }
}
