import neqsim.process.equipment.*;
import neqsim.process.processmodel.*;
import neqsim.thermo.system.*;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test to demonstrate ProcessModel report with sub process names and units
 */
public class test_process_model_report {
  public static void main(String[] args) {
    // Create first process system
    ProcessSystem process1 = new ProcessSystem("compressor process");
    
    // Create a simple compressor setup
    SystemInterface gas1 = new SystemSrkEos(216.0, 30.0);
    gas1.addComponent("methane", 0.8);
    gas1.addComponent("ethane", 0.2);
    gas1.setMixingRule("classic");
    gas1.createDatabase(true);
    
    Stream feed1 = new Stream("HP well stream", gas1);
    feed1.setFlowRate(100.0, "kg/hr");
    feed1.setTemperature(80.0, "C");
    feed1.setPressure(50.0, "bara");
    
    process1.add(feed1);
    
    Compressor compressor1 = new Compressor("Compressor1", feed1);
    compressor1.setOutletPressure(100.0, "bara");
    process1.add(compressor1);
    
    // Create second process system
    ProcessSystem process2 = new ProcessSystem("cooler process");
    
    Stream feed2 = (Stream) compressor1.getOutletStream().clone();
    feed2.setName("Compressor outlet");
    
    process2.add(feed2);
    
    WaterCooler cooler = new WaterCooler("Cooler1", feed2);
    cooler.setOutletTemperature(40.0, "C");
    process2.add(cooler);
    
    // Create ProcessModel and add both processes
    ProcessModel model = new ProcessModel();
    model.add("compressor process", process1);
    model.add("cooler process", process2);
    
    // Run the model
    try {
      model.run();
    } catch (Exception ex) {
      System.err.println("Error running model: " + ex.getMessage());
    }
    
    // Get and display the report with enhanced process names and units
    System.out.println("=== ENHANCED PROCESS MODEL REPORT ===");
    System.out.println("Shows process names and unit types for each subprocess");
    System.out.println("");
    
    String report = model.getReport_json();
    System.out.println(report);
    
    System.out.println("");
    System.out.println("=== REPORT STRUCTURE ===");
    System.out.println("The report now includes:");
    System.out.println("- Process name (e.g., 'compressor process')");
    System.out.println("- Unit count per process");
    System.out.println("- Unit type (e.g., 'Stream', 'Compressor', 'WaterCooler')");
    System.out.println("- Unit-specific data with full properties");
  }
}
