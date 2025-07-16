package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Assertions;
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
    SystemInterface system = new SystemSrkEos(398.15, 1.0);
    system.addComponent("hydrogen", 1.0);
    system.addComponent("oxygen", 1.0);
    system.addComponent("water", 0.0);
    system.setMixingRule(2);
    system.init(0);

    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);

    // Create GibbsReactor
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(10000); // User-configurable iteration count
    reactor.setConvergenceTolerance(1e-4); // Convergence tolerance

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    Assertions.assertEquals(outletSystem.getComponent("hydrogen").getz(), 1e-5, 0.01);
    Assertions.assertEquals(outletSystem.getComponent("oxygen").getz(), 0.33, 0.01);
    Assertions.assertEquals(outletSystem.getComponent("water").getz(), 0.66, 0.01);
    Assertions.assertEquals(outletSystem.getFlowRate("mole/sec"), 1.5, 0.0001);
  }

  /**
   * Test fugacity coefficient derivatives from GibbsReactor outlet composition.
   */
  @Test
  public void testFugacityCoefficientDerivatives() {
    // Create a system with hydrogen, oxygen, and water at 1 bar and 25°C
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    system.addComponent("hydrogen", 1.0);
    system.addComponent("oxygen", 1.0);
    system.addComponent("water", 1.0);
    system.setMixingRule(2);
    system.init(0);

    Stream inletStream = new Stream("Inlet Stream", system);
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-4);
    reactor.run();

    // Test fugacity coefficient derivatives for each component pair in vapor phase (phase 0)
    double dphiH2_dH2 = reactor.getFugacityDerivative("hydrogen", "hydrogen", 0);
    double dphiH2_dO2 = reactor.getFugacityDerivative("hydrogen", "oxygen", 0);
    double dphiH2_dH2O = reactor.getFugacityDerivative("hydrogen", "water", 0);

    double dphiO2_dH2 = reactor.getFugacityDerivative("oxygen", "hydrogen", 0);
    double dphiO2_dO2 = reactor.getFugacityDerivative("oxygen", "oxygen", 0);
    double dphiO2_dH2O = reactor.getFugacityDerivative("oxygen", "water", 0);

    double dphiH2O_dH2 = reactor.getFugacityDerivative("water", "hydrogen", 0);
    double dphiH2O_dO2 = reactor.getFugacityDerivative("water", "oxygen", 0);
    double dphiH2O_dH2O = reactor.getFugacityDerivative("water", "water", 0);

    // Assert that the derivatives are finite (not NaN or infinite)
    Assertions.assertTrue(Double.isFinite(dphiH2_dH2), "dphiH2/dnH2 should be finite");
    Assertions.assertTrue(Double.isFinite(dphiH2_dO2), "dphiH2/dnO2 should be finite");
    Assertions.assertTrue(Double.isFinite(dphiH2_dH2O), "dphiH2/dnH2O should be finite");
    Assertions.assertTrue(Double.isFinite(dphiO2_dH2), "dphiO2/dnH2 should be finite");
    Assertions.assertTrue(Double.isFinite(dphiO2_dO2), "dphiO2/dnO2 should be finite");
    Assertions.assertTrue(Double.isFinite(dphiO2_dH2O), "dphiO2/dnH2O should be finite");
    Assertions.assertTrue(Double.isFinite(dphiH2O_dH2), "dphiH2O/dnH2 should be finite");
    Assertions.assertTrue(Double.isFinite(dphiH2O_dO2), "dphiH2O/dnO2 should be finite");
    Assertions.assertTrue(Double.isFinite(dphiH2O_dH2O), "dphiH2O/dnH2O should be finite");
  }

  /**
   * Test fugacity coefficients from GibbsReactor outlet composition.
   */
  @Test
  public void testFugacityCoefficients() {
    // Create a system with hydrogen, oxygen, and water at 1 bar and 15°C
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("hydrogen", 1.0);
    system.addComponent("oxygen", 1.0);
    system.addComponent("water", 1.0);
    system.setMixingRule(2);
    system.init(0);

    Stream inletStream = new Stream("Inlet Stream", system);
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-4);
    reactor.run();

    // Test fugacity coefficients for each component in vapor phase (phase 0)
    double phiH2 = reactor.getFugacityCoefficient("hydrogen", 0);
    double phiO2 = reactor.getFugacityCoefficient("oxygen", 0);
    double phiH2O = reactor.getFugacityCoefficient("water", 0);


    // Test fugacity coefficients for each component in vapor phase (phase 0)
    double phiH22 = reactor.getFugacityCoefficient("hydrogen", 1);
    double phiO22 = reactor.getFugacityCoefficient("oxygen", 1);
    double phiH2O2 = reactor.getFugacityCoefficient("water", 1);

  }
}
