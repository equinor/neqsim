package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for MultiphaseGibbsReactor.
 *
 * @author Sviatoslav Eroshkin
 * @version 1.0
 */
public class MultiphaseGibbsReactorTest {


  /**
   * Test CO2-H2O system with multiphase reactor using SystemInterface directly.
   *
   * <p>
   * Tests equilibrium between CO2 (1E6 moles) and H2O (100 moles) at 25°C and 10 bar. This
   * represents a vapor-liquid equilibrium case where CO2 partitions between gas and aqueous phases.
   * Prints fugacities for each component in each phase to verify equilibrium.
   * </p>
   */
  @Test
  public void testCO2H2OEquilibrium() {
    // Create system with CO2 and H2O at 25°C and 10 bar
    SystemInterface system = new SystemSrkEos(273.15 + 25.0, 10.0);
    system.addComponent("CO2", 1e6);
    system.addComponent("water", 100000);
    system.setMixingRule("classic");

    // Create MultiphaseGibbsReactor using system directly (new constructor)
    MultiphaseGibbsReactor reactor = new MultiphaseGibbsReactor("MP Gibbs", system);
    reactor.setNumberOfPhases(2);
    reactor.setPhaseModel(0, "SRK"); // Gas phase
    reactor.setPhaseModel(1, "SRK"); // Liquid phase
    reactor.setMaxIterations(100000);
    reactor.setDynamicDamping(true); // Disable dynamic damping
    reactor.setDampingFactor(1e-4); // Start with higher damping - will auto-adjust
    reactor.setDebugMode(false); // Turn on verbose printing (Jacobian, iterations, etc.)
    reactor.setIncludeFugacityDerivatives(false); // Set d(ln φ_i)/dn_j = 0
    // Run the reactor
    reactor.run();

    // Print fugacities for each component in each phase
    System.out.println("\n=== Phase Outputs ===");
    for (int phaseNum = 0; phaseNum < reactor.getNumberOfPhases(); phaseNum++) {
      SystemInterface phaseOut = reactor.getPhaseOut(phaseNum);
      if (phaseOut != null) {
        System.out.println("\nPhase " + phaseNum + " (" + reactor.getPhaseModel(phaseNum) + "):");
        phaseOut.init(3); // Initialize to calculate fugacities
        double pressure = phaseOut.getPressure();
        System.out.println("Fugacities:");
        // phaseOut is a single-phase SystemInterface
        // Get properties from the phase object (index 0)
        for (int comp = 0; comp < phaseOut.getNumberOfComponents(); comp++) {
          String compName = phaseOut.getComponent(comp).getName();
          double fugacityCoeff = phaseOut.getPhase(0).getComponent(comp).getFugacityCoefficient();
          double moleFraction = phaseOut.getPhase(0).getComponent(comp).getx();
          double fugacity = fugacityCoeff * moleFraction * pressure;
          System.out.println("  " + compName + " pressure: " + pressure + " bar, fugacity = " + fugacity + " bar, phi = "
              + fugacityCoeff + ", x = " + moleFraction);
        }
      }
    }

  }

}
