package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemVanLaarActivitySRK;

/** Numerical-robustness tests for the direct EOS-GE K-value iteration. */
class TPflashGammaPhiNumericalRobustnessTest {
  /** Direct EOS-GE system that emits one non-finite vapour coefficient. */
  private static final class OneNonFiniteCoefficientSystem extends SystemVanLaarActivitySRK {
    private static final long serialVersionUID = 1000;
    private boolean emittedNonFiniteCoefficient;

    OneNonFiniteCoefficientSystem(double temperature, double pressure) {
      super(temperature, pressure);
    }

    @Override
    public double getGammaPhiVapourFugacityCoefficient(ComponentInterface component, PhaseInterface vapourPhase) {
      if (!emittedNonFiniteCoefficient) {
        emittedNonFiniteCoefficient = true;
        return Double.NaN;
      }
      return super.getGammaPhiVapourFugacityCoefficient(component, vapourPhase);
    }
  }

  /** TPflash view that exposes the package-level successive-substitution deviation to this regression. */
  private static final class InspectableTPflash extends TPflash {
    private static final long serialVersionUID = 1000;

    InspectableTPflash(SystemInterface system) {
      super(system);
    }

    double getDeviationForTest() {
      return deviation;
    }
  }

  @Test
  void nonFiniteGammaPhiTargetFallsBackWithoutPoisoningDeviation() {
    OneNonFiniteCoefficientSystem system = new OneNonFiniteCoefficientSystem(273.15, 1.0);
    system.addComponent("CO2", 10.0);
    system.addComponent("water", 0.70);
    system.addComponent("nitric acid", 0.15);
    system.addComponent("sulfuric acid", 0.15);
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);

    InspectableTPflash flash = new InspectableTPflash(system);
    system.prepareGammaPhiFlash();
    flash.sucsSubsDirectGammaPhi();

    assertTrue(Double.isFinite(flash.getDeviationForTest()));
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      for (int componentIndex = 0; componentIndex < system.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        double kValue = system.getPhase(phaseIndex).getComponent(componentIndex).getK();
        assertTrue(Double.isFinite(kValue) && kValue > 0.0);
      }
    }
  }

  @Test
  void nonFinitePreviousKIsRepairedWithoutPoisoningDeviation() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15, 1.0);
    system.addComponent("CO2", 10.0);
    system.addComponent("water", 0.70);
    system.addComponent("nitric acid", 0.15);
    system.addComponent("sulfuric acid", 0.15);
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    system.prepareGammaPhiFlash();
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      system.getPhase(phaseIndex).getComponent("water").setK(Double.NaN);
    }

    InspectableTPflash flash = new InspectableTPflash(system);
    flash.sucsSubsDirectGammaPhi();

    assertTrue(Double.isFinite(flash.getDeviationForTest()));
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      double repairedK = system.getPhase(phaseIndex).getComponent("water").getK();
      assertTrue(Double.isFinite(repairedK) && repairedK > 0.0);
    }
  }
}
