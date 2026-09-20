package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSAFTVRMie;
import neqsim.thermo.component.ComponentSAFTVRMie;

/** Finite differences of the Mie Helmholtz energy at fixed volume. */
class MieDerivativeConsistencyTest {
  private void close(double expected, double actual, double relative, String label) {
    assertEquals(expected, actual, Math.max(1e-60, Math.abs(expected) * relative), label);
  }

  private PhaseSAFTVRMie mie(double temperature, String[] names, double[] amounts) {
    SystemSAFTVRMie system = new SystemSAFTVRMie(temperature, 30.0);
    for (int i = 0; i < names.length; i++) {
      system.addComponent(names[i], amounts[i]);
    }
    system.setMixingRule("classic");
    system.init(0);
    system.init(2);
    return (PhaseSAFTVRMie) system.getPhase(0);
  }

  private PhaseSAFTVRMie shiftedMie(PhaseSAFTVRMie base, double temperature, double volume) {
    PhaseSAFTVRMie copy = base.clone();
    copy.setTemperature(temperature);
    copy.setMolarVolume(volume);
    for (int i = 0; i < copy.getNumberOfComponents(); i++) {
      ((ComponentSAFTVRMie) copy.getComponent(i)).recalcSAFTDiameter(temperature);
    }
    copy.volInit();
    return copy;
  }

  @Test
  void mieHardChainThermalDerivativesIncludeDiameterAndContactTemperature() {
    for (double temperature : new double[] {300.0, 350.0}) {
      for (PhaseSAFTVRMie p : new PhaseSAFTVRMie[] {mie(temperature, new String[] {"methane"}, new double[] {1}),
          mie(temperature, new String[] {"n-butane"}, new double[] {1}),
          mie(temperature, new String[] {"methane", "n-butane"}, new double[] {0.6, 0.4})}) {
        double originalF = p.getF();
        double originalVolume = p.getMolarVolume();
        double h = 0.1;
        PhaseSAFTVRMie plus = shiftedMie(p, p.getTemperature() + h, p.getMolarVolume());
        PhaseSAFTVRMie minus = shiftedMie(p, p.getTemperature() - h, p.getMolarVolume());
        close((plus.F_HC_SAFT() - minus.F_HC_SAFT()) / (2 * h), p.dF_HC_SAFTdT(), 2e-4, "Mie hard-chain dT");
        close((plus.F_HC_SAFT() - 2 * p.F_HC_SAFT() + minus.F_HC_SAFT()) / (h * h), p.dF_HC_SAFTdTdT(), 2e-2,
            "Mie hard-chain dTT");
        double hv = p.getMolarVolume() * 0.002;
        double ht = 0.7;
        double mixed = (shiftedMie(p, p.getTemperature() + ht, p.getMolarVolume() + hv).F_HC_SAFT()
            - shiftedMie(p, p.getTemperature() + ht, p.getMolarVolume() - hv).F_HC_SAFT()
            - shiftedMie(p, p.getTemperature() - ht, p.getMolarVolume() + hv).F_HC_SAFT()
            + shiftedMie(p, p.getTemperature() - ht, p.getMolarVolume() - hv).F_HC_SAFT())
            / (4 * ht * hv * p.getNumberOfMolesInPhase() * 1e-5);
        close(mixed, p.dF_HC_SAFTdTdV(), 2e-3, "Mie hard-chain dTV");
        close((plus.getdDSAFTdT() - minus.getdDSAFTdT()) / (2 * h), p.getd2DSAFTdTdT(), 1e-4, "Mie diameter dTT");
        close((plus.getF() - minus.getF()) / (2 * h), p.dFdT(), 2e-4, "Mie total dT");
        assertEquals(originalF, p.getF());
        assertEquals(temperature, p.getTemperature());
        assertEquals(originalVolume, p.getMolarVolume());
      }
    }
  }
}
