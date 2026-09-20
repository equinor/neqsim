package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.system.SystemPitzer;

/** Regressions for cancellation in the ionic activity path near infinite dilution. */
class PitzerDiluteActivityTest extends neqsim.NeqSimTest {
  @Test
  void betaContributionsRetainDiluteLimitsInLegacyAndCommonIonPaths() {
    for (boolean commonIon : new boolean[] {false, true}) {
      for (boolean beta2 : new boolean[] {false, true}) {
        for (double molality : new double[] {1.0e-18, 1.0e-14, 1.0e-10, 1.0e-5, 0.1}) {
          assertBinaryContribution(molality, beta2, commonIon);
        }
      }
    }
  }

  private static void assertBinaryContribution(double molality, boolean beta2, boolean commonIon) {
    SystemPitzer system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.508);
    system.addComponent(beta2 ? "Mg++" : "Na+", molality);
    system.addComponent(beta2 ? "SO4--" : "Cl-", molality);
    system.init(0);
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    for (int ion = 1; ion <= 2; ion++) {
      phase.getComponent(ion)
          .setNumberOfMolesInPhase(molality * phase.getSolventWeight() / phase.getComponent(ion).getx());
    }
    phase.setBinaryParameters(1, 2, 0.0, 0.0, 0.0);
    phase.setBeta2(1, 2, 0.0);
    phase.markManualParameterDatasetLoaded();
    if (commonIon) {
      phase.enablePhreeqcCommonIonTerms();
    }
    double baseline = logGamma(phase);
    // Amplify only the selected parameter so a wrong dilute limit remains observable
    // in gamma. These artificial coefficients are a numerical probe, not a salt fit.
    if (beta2) {
      phase.setBeta2(1, 2, 1.0 / molality);
    } else {
      phase.setBinaryParameters(1, 2, 0.0, 1.0 / molality, 0.0);
    }
    double x = (beta2 ? phase.getPitzerAlpha2(1, 2) : 2.0) * Math.sqrt(phase.getIonicStrength());
    // Independent integral: 2*g(x) + (x/2)*g'(x) = integral_0^1 (4*t-x*t*t)*exp(-x*t) dt.
    double expected = 0.0;
    int intervals = 4096;
    for (int i = 0; i <= intervals; i++) {
      double t = (double) i / intervals;
      double weight = i == 0 || i == intervals ? 1.0 : (i % 2 == 0 ? 2.0 : 4.0);
      expected += weight * (4.0 * t - x * t * t) * Math.exp(-x * t);
    }
    expected /= 3.0 * intervals;
    assertEquals(expected, logGamma(phase) - baseline, 3.0e-12,
        "molality=" + molality + ", beta2=" + beta2 + ", commonIon=" + commonIon);
  }

  private static double logGamma(PhasePitzer phase) {
    ComponentGePitzer ion = (ComponentGePitzer) phase.getComponent(1);
    return Math.log(ion.getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(),
        phase.getType()));
  }
}
