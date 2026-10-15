package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkCPA;

/**
 * Independent finite-difference validation of the CPA third volume derivative.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PhaseSrkCPAVolumeDerivativeTest {
  /** Tests equal and unequal association sites, several temperatures, and extensive amount scaling. */
  @Test
  void thirdDerivativeContractsEachAssociationSiteIndependently() {
    for (double temperature : new double[] {280, 313.15, 373.15}) {
      for (boolean mixedSolvent : new boolean[] {false, true}) {
        for (double scale : new double[] {1, 7}) {
          SystemSrkCPA fluid = new SystemSrkCPA(temperature, 5);
          fluid.addComponent("water", 55.508 * scale);
          if (mixedSolvent) {
            fluid.addComponent("MEG", 2 * scale);
          }
          fluid.setMixingRule(10);
          fluid.init(0);
          fluid.init(3);
          PhaseSrkCPA phase = (PhaseSrkCPA) fluid.getPhase(1);
          double step = phase.getTotalVolume() * 2e-5;
          PhaseSrkCPA high = atVolume(phase, phase.getTotalVolume() + step);
          PhaseSrkCPA low = atVolume(phase, phase.getTotalVolume() - step);
          double numeric = (high.dFdVdV() - low.dFdVdV()) / (2 * step);
          assertEquals(numeric, phase.dFdVdVdV(), 2e-8 * Math.max(1, Math.abs(numeric)));
          double cpaNumeric = (high.dFCPAdVdV() - low.dFCPAdVdV()) / (2 * step);
          assertEquals(cpaNumeric, phase.dFCPAdVdVdV(), 2e-8 * Math.max(1, Math.abs(cpaNumeric)));
        }
      }
    }
  }

  /**
   * Re-equilibrates association sites at a prescribed total volume.
   *
   * @param phase original initialized phase
   * @param volume perturbed total volume
   * @return an independently initialized phase at the specified volume
   */
  private static PhaseSrkCPA atVolume(PhaseSrkCPA phase, double volume) {
    PhaseSrkCPA copy = phase.clone();
    copy.setTotalVolume(volume);
    copy.setMolarVolume(volume / copy.getNumberOfMolesInPhase());
    copy.setConstantPhaseVolume(true);
    copy.calcMolarVolume(false);
    copy.init(copy.getNumberOfMolesInPhase(), copy.getNumberOfComponents(), 3, phase.getType(), 1);
    return copy;
  }
}
