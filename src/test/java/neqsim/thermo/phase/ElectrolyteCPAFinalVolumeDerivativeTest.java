package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks composition derivatives after an electrolyte CPA volume solve has finished.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPAFinalVolumeDerivativeTest {
  /** Checks the existing methane/brine fixture with explicitly initialized, nonzero derivatives. */
  @Test
  void initializedFlashSatisfiesGibbsDuhem() {
    SystemInterface fluid = flashedFluid();
    fluid.init(3);
    assertCompositionIdentities(fluid);
  }

  /** Checks detached and repeated initialization without changing the converged phase volumes. */
  @Test
  void detachedRepeatedInitializationPreservesCompositionIdentities() {
    SystemInterface fluid = SerializationUtils.clone(flashedFluid());
    fluid.init(3);
    double[] volumes = new double[fluid.getNumberOfPhases()];
    for (int phase = 0; phase < volumes.length; phase++) {
      volumes[phase] = fluid.getPhase(phase).getTotalVolume();
    }
    fluid.init(3);
    for (int phase = 0; phase < volumes.length; phase++) {
      assertEquals(volumes[phase], fluid.getPhase(phase).getTotalVolume(), 1e-12);
    }
    assertCompositionIdentities(fluid);
  }

  /** Fugacity values use final-volume association caches at every property initialization level. */
  @Test
  void propertyAndDerivativeInitializationGiveTheSameFugacities() {
    SystemInterface fluid = flashedFluid();
    fluid.init(3);
    double[][] logarithms = new double[fluid.getNumberOfPhases()][fluid.getNumberOfComponents()];
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        logarithms[phase][component] = fluid.getPhase(phase).getComponent(component).getLogFugacityCoefficient();
      }
    }
    for (int level : new int[] {1, 3, 1}) {
      fluid.init(level);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
          assertEquals(logarithms[phase][component],
              fluid.getPhase(phase).getComponent(component).getLogFugacityCoefficient(), 1e-12,
              "Final-volume fugacity at initialization level " + level);
        }
      }
    }
  }

  /**
   * Creates the existing two-phase methane/water/NaCl regression fixture.
   *
   * @return flashed fluid before composition-derivative initialization
   */
  private static SystemInterface flashedFluid() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 10.01325);
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("Na+", 0.001);
    fluid.addComponent("Cl-", 0.001);
    fluid.setMixingRule(10);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(2, fluid.getNumberOfPhases());
    return fluid;
  }

  /**
   * Checks each Gibbs-Duhem column independently and rejects uninitialized zero derivative arrays.
   *
   * @param fluid initialized fluid to inspect
   */
  private static void assertCompositionIdentities(SystemInterface fluid) {
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      double largestContribution = 0.0;
      for (int column = 0; column < phase.getNumberOfComponents(); column++) {
        double residual = 0.0;
        for (int row = 0; row < phase.getNumberOfComponents(); row++) {
          ComponentInterface component = phase.getComponent(row);
          double derivative = component.getdfugdn(column);
          assertTrue(Double.isFinite(derivative));
          double contribution = component.getNumberOfMolesInPhase() * derivative;
          residual += contribution;
          largestContribution = Math.max(largestContribution, Math.abs(contribution));
        }
        assertEquals(0.0, residual, 1e-10, "Gibbs-Duhem residual in phase " + phaseIndex + ", column " + column);
      }
      assertTrue(largestContribution > 1e-6, "Composition derivatives must be initialized in each phase");
    }
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    checker.setMaxError(1e-10);
    assertTrue(checker.checkFugacityCoefficientsDn());
    assertTrue(checker.checkFugacityCoefficientsDn2());
  }
}
