package neqsim.process.equipment.reactor;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Basic test without complex dependencies.
 */
public class SimpleGibbsTest {
  public static void main(String[] args) {
    System.out.println("=== Simple GibbsReactor Test ===");
    
    try {
      // Create a simple system
      SystemInterface system = new SystemSrkEos(288.15, 1.0); // 15°C, 1 bar
      
      // Add components
      system.addComponent("hydrogen", 1.0);
      system.addComponent("oxygen", 1.0);  
      system.addComponent("water", 0.0);
      
      system.setMixingRule(1);
      system.init(0);
      
      // Create inlet stream
      Stream inletStream = new Stream("Inlet", system);
      
      // Test basic reactor creation
      GibbsReactor reactor = new GibbsReactor("TestReactor", inletStream);
      
      System.out.println("GibbsReactor created successfully!");
      System.out.println("Method: " + reactor.getMethod());
      System.out.println("Use all database species: " + reactor.getUseAllDatabaseSpecies());
      
      // Test the run method
      reactor.run();
      
      System.out.println("GibbsReactor run completed successfully!");
      
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
