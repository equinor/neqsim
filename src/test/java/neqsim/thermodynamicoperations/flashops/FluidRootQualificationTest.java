package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Numerical qualification of fluid-root selection and stability trial compositions.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class FluidRootQualificationTest {
  /** A root request must follow the lower-Gibbs solution even when both roots satisfy pressure. */
  @Test
  void ionicSplitSelectsTheLowerGibbsGasRootAcrossFeedScales() {
    double referenceEnergy = Double.NaN;
    for (double scale : new double[] {0.01, 1.0, 100.0}) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(282.814777132, 29.01);
      fluid.addComponent("CO2", 10.0 * scale);
      fluid.addComponent("water", scale / 0.01801528);
      double salt = scale * 14.97 / (100.0 - 14.97) / 0.0745513;
      fluid.addComponent("K+", salt);
      fluid.addComponent("Cl-", salt);
      fluid.setMixingRule(10);
      fluid.setMultiPhaseCheck(true);
      double[] inventory = FluidEquilibriumChecks.inventory(fluid);
      for (int repeat = 0; repeat < 2; repeat++) {
        new ThermodynamicOperations(fluid).TPflash();
        FluidEquilibriumChecks.validate(fluid, inventory);
        int gas = fluid.getPhaseNumberOfPhase(PhaseType.GAS);
        assertTrue(gas >= 0);
        assertEquals(PhaseType.GAS, fluid.getPhaseType(gas));
        PhaseInterface phase = fluid.getPhase(gas);
        PhaseInterface alternate = phase.clone();
        alternate.init(fluid.getTotalNumberOfMoles(), fluid.getNumberOfComponents(), 1, PhaseType.LIQUID,
            fluid.getBeta(gas));
        FluidEquilibriumChecks.validatePressureRoot(alternate);
        double rootDifference = 0.0;
        for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
          double x = phase.getComponent(component).getx();
          if (x > 0.0) {
            alternate.getComponent(component).fugcoef(alternate);
            rootDifference += x * (alternate.getComponent(component).getLogFugacityCoefficient()
                - phase.getComponent(component).getLogFugacityCoefficient());
          }
        }
        assertTrue(rootDifference > 0.1, "The returned gas root must be the lower-Gibbs branch");
        double energy = fluid.getGibbsEnergy() / fluid.getTotalNumberOfMoles();
        if (Double.isFinite(referenceEnergy)) {
          assertEquals(referenceEnergy, energy, 1e-6, "Root selection is independent of amount scale and history");
        } else {
          referenceEnergy = energy;
        }
      }
    }
  }

  /** Wilson weights belong to the stability objective; every EOS call still requires mole fractions. */
  @Test
  void enhancedNeutralStabilityEvaluatesNormalizedCompositions() {
    SystemInterface fluid = new SystemPrEos(300.0, 10.0);
    fluid.setPhase(new CompositionCheckingPhase(), 0);
    fluid.setPhase(new CompositionCheckingPhase(), 1);
    fluid.setTemperature(300.0);
    fluid.setPressure(10.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-butane", 0.2);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(1);
    fluid.setNumberOfPhases(1);
    fluid.setBeta(0, 1.0);
    fluid.init(1);
    CompositionCheckingPhase.maximumError = 0.0;
    new TPmultiflash(fluid, false).stabilityAnalysisEnhanced();
    assertTrue(CompositionCheckingPhase.maximumError <= 1e-12,
        "A stability trial must normalize Wilson weights before evaluating the EOS");
  }

  /** Records the physical composition presented to the actual PR initialization. */
  private static class CompositionCheckingPhase extends PhasePrEos {
    private static final long serialVersionUID = 1000L;
    private static double maximumError;

    /** {@inheritDoc} */
    @Override
    public void init(double moles, int components, int level, PhaseType type, double beta) {
      if (level > 0) {
        double sum = 0.0;
        for (int component = 0; component < getNumberOfComponents(); component++) {
          sum += getComponent(component).getx();
        }
        maximumError = Math.max(maximumError, Math.abs(sum - 1.0));
      }
      super.init(moles, components, level, type, beta);
    }
  }
}
