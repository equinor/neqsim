package neqsim.process.equipment.reactor;

import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for GibbsReactor.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GibbsReactorTest {

  /**
   * Test GibbsReactor with hydrogen, oxygen, and water system.
   */
  @Test
  public void testGibbsReactorHydrogenOxygenWater() {
    // Create a system with hydrogen, oxygen, and water at 1 bar and 15°C
    SystemInterface system = new SystemSrkEos(298.15, 1.0); // 25°C = 298.15 K, 1 bar
    
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
    
    // Set to use only system components for this test
    reactor.setUseAllDatabaseSpecies(false);
    
    // Run the reactor
    reactor.run();
    
    // Print initial Lagrangian multipliers (should be zeros)
    System.out.println("\n=== Initial Lagrangian Multipliers (zeros) ===");
    double[] lambdaValues = reactor.getLagrangianMultipliers();
    String[] elementNames = reactor.getElementNames();
    for (int i = 0; i < lambdaValues.length; i++) {
      System.out.println("  λ[" + elementNames[i] + "] = " + lambdaValues[i]);
    }
    
    // Set all Lagrange multipliers to 1.0 for testing
    System.out.println("\n=== Setting all Lagrange multipliers to 1.0 ===");
    for (int i = 0; i < elementNames.length; i++) {
      reactor.setLagrangeMultiplier(i, 1.0);
    }
    
    // Print updated multipliers
    lambdaValues = reactor.getLagrangianMultipliers();
    for (int i = 0; i < lambdaValues.length; i++) {
      System.out.println("  λ[" + elementNames[i] + "] = " + lambdaValues[i]);
    }
    
    System.out.println("\n=== Detailed Lagrange Multiplier Contributions ===");
    Map<String, Map<String, Double>> detailedContributions = reactor.getLagrangeMultiplierContributions();
    
    // Print contributions for system components
    String[] systemComponents = {"hydrogen", "oxygen", "water"};
    for (String compName : systemComponents) {
      Map<String, Double> contributions = detailedContributions.get(compName);
      if (contributions != null) {
        System.out.println("\n" + compName.toUpperCase() + ":");
        for (int i = 0; i < elementNames.length; i++) {
          String element = elementNames[i];
          Double contribution = contributions.get(element);
          if (contribution != null && Math.abs(contribution) > 1e-10) {
            System.out.printf("    %s: %8.4f%n", element, contribution);
          }
        }
        Double total = contributions.get("TOTAL");
        if (total != null) {
          System.out.printf("    TOTAL: %8.4f%n", total);
        }
      }
    }
    
    // Test setting specific multipliers
    System.out.println("\n=== Testing Specific Lagrange Multiplier Values ===");
    reactor.setLagrangeMultiplier("O", 2.0);  // Oxygen
    reactor.setLagrangeMultiplier("H", 0.5);  // Hydrogen
    
    System.out.println("Set λ[O] = 2.0, λ[H] = 0.5");
    
    // Print updated contributions
    detailedContributions = reactor.getLagrangeMultiplierContributions();
    for (String compName : systemComponents) {
      Map<String, Double> contributions = detailedContributions.get(compName);
      if (contributions != null) {
        System.out.println("\n" + compName.toUpperCase() + " (updated):");
        for (int i = 0; i < elementNames.length; i++) {
          String element = elementNames[i];
          Double contribution = contributions.get(element);
          if (contribution != null && Math.abs(contribution) > 1e-10) {
            System.out.printf("    %s: %8.4f%n", element, contribution);
          }
        }
        Double total = contributions.get("TOTAL");
        if (total != null) {
          System.out.printf("    TOTAL: %8.4f%n", total);
        }
      }
    }
    
    // Get outlet stream
    Stream outletStream = (Stream) reactor.getOutletStream();
    
    // Print basic results
    System.out.println("\n=== GibbsReactor Test Results ===");
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
  }
  
  /**
   * Test GibbsReactor with only system components.
   */
  @Test
  public void testGibbsReactorSystemComponentsOnly() {
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
    
    // Set to use only system components
    reactor.setUseAllDatabaseSpecies(false);
    
    // Run the reactor
    reactor.run();
    
    // Get outlet stream
    Stream outletStream = (Stream) reactor.getOutletStream();
    
    // Print Lagrangian multipliers
    System.out.println("\n=== Lagrangian Multipliers (System Components Only) ===");
    double[] lambdaValues = reactor.getLagrangianMultipliers();
    String[] elementNames = reactor.getElementNames();
    for (int i = 0; i < lambdaValues.length; i++) {
      System.out.println("  λ[" + elementNames[i] + "] = " + lambdaValues[i]);
    }
    
    System.out.println("\n=== Lagrangian Contributions by Component (System Components Only) ===");
    reactor.getLagrangeContributions().forEach((component, contribution) -> {
      System.out.println("  " + component + ": " + contribution);
    });
    
    // Print results
    System.out.println("\n=== GibbsReactor Test Results (System Components Only) ===");
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
  }
}
