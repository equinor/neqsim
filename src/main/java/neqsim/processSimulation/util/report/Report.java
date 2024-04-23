package neqsim.processSimulation.util.report;

import java.util.HashMap;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;

/**
 * <p>
 * Report class.
 * </p>
 * 
 * @author even
 * @version $Id: $Id
 */
public class Report {
  public static HashMap<String, List<String[]>> reports = new HashMap<>();
  public static HashMap<String, String> json_reports = new HashMap<>();
  Gson gson = new Gson();
  ProcessSystem process;
  ProcessEquipmentBaseClass processEquipment;

  public Report(ProcessSystem process) {
    this.process = process;
  }

  public Report(ProcessEquipmentBaseClass processEquipmentBaseClass) {
    processEquipment = processEquipmentBaseClass;
  }

  public String json() {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      json_reports.put(unit.getName(), unit.toJson());
    }
    return new GsonBuilder().setPrettyPrinting().create().toJson(json_reports);
  }

}
