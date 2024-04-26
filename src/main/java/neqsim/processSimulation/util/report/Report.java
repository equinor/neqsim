package neqsim.processSimulation.util.report;

import java.util.HashMap;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processSystem.ProcessModule;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;
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
  private HashMap<String, List<String[]>> reports = new HashMap<>();
  private HashMap<String, String> json_reports = new HashMap<>();
  Gson gson = new Gson();
  ProcessSystem process = null;
  ProcessEquipmentBaseClass processEquipment = null;

  public Report(ProcessSystem process) {
    this.process = process;
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

  public String json() {
    if (process != null) {
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        json_reports.put(unit.getName(), unit.toJson());
      }
    }
    if (processEquipment != null) {
      json_reports.put(processEquipment.getName(), processEquipment.toJson());
    }
    return new GsonBuilder().setPrettyPrinting().create().toJson(json_reports);
  }

}
