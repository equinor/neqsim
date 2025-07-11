package neqsim.process.equipment.reactor;

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
   * Test GibbsReactor with only system components.
   */
  @Test
  public void testGibbsReactorSystemComponentsOnly() {
    // Create a system with hydrogen, oxygen, and water at 1 bar and 15°C
    SystemInterface system = new SystemSrkEos(288.15, 1.0); 
    
    // Add 1 mole of hydrogen
    system.addComponent("hydrogen", 1.0);
    
    // Add 1 mole of oxygen
    system.addComponent("oxygen", 1.0);
    
    // Add 0 moles of water (will be formed in reaction)
    system.addComponent("water", 0.0);
    
    // Set the system to vapor phase
    system.setMixingRule(2);
    system.init(0);
    
    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);
    
    // Create GibbsReactor
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    
    // Set to use only system components
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setMaxIterations(10000); // User-configurable iteration count
    reactor.setConvergenceTolerance(1e-4); // Convergence tolerance
    // Run the reactor
    reactor.run();

    // Get outlet stream
    Stream outletStream = (Stream) reactor.getOutletStream();

    
    System.out.println("\nOutlet conditions:");
    SystemInterface outletSystem = outletStream.getThermoSystem();
    System.out.println("  Temperature: " + outletSystem.getTemperature() + " K");
    System.out.println("  Pressure: " + outletSystem.getPressure() + " bar");
    System.out.println("  Hydrogen: " + outletSystem.getComponent("hydrogen").getNumberOfMolesInPhase() + " mol");
    System.out.println("  Oxygen: " + outletSystem.getComponent("oxygen").getNumberOfMolesInPhase() + " mol");
    System.out.println("  Water: " + outletSystem.getComponent("water").getNumberOfMolesInPhase() + " mol");
  }
}
