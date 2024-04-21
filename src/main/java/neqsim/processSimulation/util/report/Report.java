package neqsim.processSimulation.util.report;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  ProcessSystem process;

  public Report(ProcessSystem process) {
    this.process = process;
  }

  public void write(String path) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      reports.put(unit.getName(), unit.getReport());
      System.out.println(unit.getName());
    }
    reports.size();
  }

  public static void main(String[] args) {
    // Initializing the HashMap with ten entries
    reports.put("Finance", Arrays.asList(new String[] {"Budget Report", "John Doe", "2023-01-01"},
        new String[] {"Income Statement", "Jane Doe", "2023-02-01"}));
    reports.put("Finance2", Arrays.asList(new String[] {"Budget Report", "John Doe", "2023-01-01"},
        new String[] {"Income Statement", "Jane Doe", "2023-02-01"}));

    // Print out all reports
    for (Map.Entry<String, List<String[]>> entry : reports.entrySet()) {
      System.out.println(entry.getKey() + ": ");
      for (String[] details : entry.getValue()) {
        System.out.println(
            "  Title: " + details[0] + ", Author: " + details[1] + ", Date: " + details[2]);
      }
    }
  }

}
