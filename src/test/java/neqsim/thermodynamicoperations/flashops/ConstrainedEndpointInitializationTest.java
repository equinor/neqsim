package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAMM;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies that post-solve initialization retains a qualified constrained equilibrium.
 *
 * <p>
 * This is a numerical endpoint test. It does not qualify the MM model's salt-rich liquid split against experimental
 * phase behavior, or assume that the first material phase contains the salt.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ConstrainedEndpointInitializationTest {
  /** Checks the frozen inventory and molecular residual after repeated association initialization. */
  @Test
  void initializationRetainsConservedMolecularEquilibrium() {
    SystemInterface fluid = new SystemElectrolyteCPAMM(298.15, 1.0);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Na+", 1.0);
    fluid.addComponent("Cl-", 1.0);
    fluid.setMixingRule(10);
    double[] inventory = FluidEquilibriumChecks.inventory(fluid);

    new ThermodynamicOperations(fluid).TPflash();
    for (int iteration = 0; iteration < 5; iteration++) {
      fluid.init(iteration % 2 == 0 ? 1 : 3);
      FluidEquilibriumChecks.validate(fluid, inventory);
    }
  }

  /** Keeps the same ionic boiling roots across neutral beta bounds, feed scales and caloric initialization. */
  @Test
  void boilingBrineRetainsConstrainedAqueousHostAtNeutralBetaBound() {
    for (double temperature : new double[] {395.65, 409.4}) {
      double referenceGasBeta = Double.NaN;
      for (double scale : new double[] {0.01, 1.0, 100.0}) {
        SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, 1.0);
        fluid.addComponent("water", 55.508 * scale);
        fluid.addComponent("Na+", scale);
        fluid.addComponent("Cl-", scale);
        fluid.setMixingRule(10);
        fluid.setMultiPhaseCheck(true);
        double[] inventory = FluidEquilibriumChecks.inventory(fluid);
        for (int repeat = 0; repeat < 2; repeat++) {
          new ThermodynamicOperations(fluid).TPflash();
          for (int level : new int[] {1, 3, 1}) {
            fluid.init(level);
            FluidEquilibriumChecks.validate(fluid, inventory);
            assertEquals(2, fluid.getNumberOfPhases(), "Boiling retains gas and the material ionic host");
            assertTrue(fluid.hasPhaseType(PhaseType.GAS));
            assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
          }
          double gasBeta = fluid.getBeta(fluid.getPhaseNumberOfPhase("gas"));
          if (Double.isFinite(referenceGasBeta)) {
            assertEquals(referenceGasBeta, gasBeta, 1.0e-8, "Feed scale and repeated flashes retain the same roots");
          } else {
            referenceGasBeta = gasBeta;
          }
        }
      }
    }
  }

  /**
   * Verifies that salt selects the lower-energy liquid host independently of component registration and warm starts.
   */
  @Test
  void ionicHostIsIndependentOfRegistrationOrderAndWarmStart() {
    for (double temperature : new double[] {292.15, 293.15, 294.15}) {
      double referenceEnergy = Double.NaN;
      for (boolean reversed : new boolean[] {false, true}) {
        SystemInterface fluid = new SystemSrkEos(temperature, 1.0);
        fluid.addComponent(reversed ? "water" : "ethanol", 49.0);
        fluid.addComponent(reversed ? "ethanol" : "water", 49.0);
        fluid.addComponent("NaCl", 2.0);
        fluid.setMixingRule("classic");
        fluid.setMultiPhaseCheck(true);
        double[] inventory = FluidEquilibriumChecks.inventory(fluid);
        ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
        for (int repeat = 0; repeat < 2; repeat++) {
          operations.TPflash();
          FluidEquilibriumChecks.validate(fluid, inventory);
          int host = FlashPhaseConstraints.capture(fluid).getAqueousPhase();
          assertTrue(
              fluid.getPhase(host).getComponent("water").getx() > fluid.getPhase(host).getComponent("ethanol").getx(),
              "The lower-energy salt host is water-rich");
          double energy = fluid.getGibbsEnergy();
          if (Double.isFinite(referenceEnergy)) {
            assertEquals(referenceEnergy, energy, 1.0e-4, "Registration order and repeats preserve the minimum");
          } else {
            referenceEnergy = energy;
          }
        }
      }
    }
  }
}
