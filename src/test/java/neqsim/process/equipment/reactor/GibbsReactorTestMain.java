package neqsim.process.equipment.reactor;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Simple test class to verify GibbsReactor functionality.
 *
 * @author Even Solbraa
 */
public class GibbsReactorTestMain {
  public static void main(String[] args) {
    try {
      System.out.println("Testing GibbsReactor implementation...");
      
      // Create a system with hydrogen, oxygen, and water at 1 bar and 15°C
      SystemInterface system = new SystemSrkEos(288.15, 1.0); // 15°C = 288.15 K, 1 bar
      
      // Add 1 mole of hydrogen
      system.addComponent("hydrogen", 1.0);
      
      // Add 1 mole of oxygen
      system.addComponent("oxygen", 1.0);
      
      // Add 0 moles of water (will be formed in reaction)
      system.addComponent("water", 0.0);
      
      // Set the system to vapor phase
      system.setMixingRule(1);
      system.init(0);
      
      // Create inlet stream
      Stream inletStream = new Stream("Inlet Stream", system);
      
      // Create GibbsReactor
      GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
      
      // Set method (default is DirectGibbsMinimization)
      reactor.setMethod("DirectGibbsMinimization");
      
      // Set to use all database species
      reactor.setUseAllDatabaseSpecies(true);
      
      // Run the reactor
      reactor.run();
      
      // Get outlet stream
      Stream outletStream = (Stream) reactor.getOutletStream();
      
      // Print results
      System.out.println("=== GibbsReactor Test Results ===");
      System.out.println("Inlet conditions:");
      System.out.println("  Temperature: " + system.getTemperature() + " K");
      System.out.println("  Pressure: " + system.getPressure() + " bar");
      System.out.println("  Hydrogen: " + system.getComponent("hydrogen").getNumberOfMolesInPhase() + " mol");
      System.out.println("  Oxygen: " + system.getComponent("oxygen").getNumberOfMolesInPhase() + " mol");
      System.out.println("  Water: " + system.getComponent("water").getNumberOfMolesInPhase() + " mol");
      
      System.out.println("\nOutlet conditions:");
      SystemInterface outletSystem = outletStream.getThermoSystem();
      System.out.println("  Temperature: " + outletSystem.getTemperature() + " K");
      System.out.println("  Pressure: " + outletSystem.getPressure() + " bar");
      System.out.println("  Hydrogen: " + outletSystem.getComponent("hydrogen").getNumberOfMolesInPhase() + " mol");
      System.out.println("  Oxygen: " + outletSystem.getComponent("oxygen").getNumberOfMolesInPhase() + " mol");
      System.out.println("  Water: " + outletSystem.getComponent("water").getNumberOfMolesInPhase() + " mol");
      
      System.out.println("\nTest completed successfully!");
      
    } catch (Exception e) {
      System.err.println("Error in GibbsReactor test: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
