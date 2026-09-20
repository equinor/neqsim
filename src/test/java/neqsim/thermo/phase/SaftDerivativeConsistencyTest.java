package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemPCSAFTa;

/** Differential identities evaluated at fixed volume, independently of the derivative code. */
class SaftDerivativeConsistencyTest {
  private PhasePCSAFT pcsaft(boolean associating, boolean mixture) {
    SystemInterface system = associating ? new SystemPCSAFTa(350.0, 30.0) : new SystemPCSAFT(350.0, 30.0);
    system.addComponent("methane", mixture ? 0.6 : 1.0);
    if (mixture) {
      system.addComponent("n-butane", 0.4);
    }
    system.setMixingRule("classic");
    system.init(0);
    system.init(2);
    return (PhasePCSAFT) system.getPhase(0);
  }

  private PhasePCSAFT shifted(PhasePCSAFT base, double temperature, double volume) {
    PhasePCSAFT copy = base.clone();
    copy.setTemperature(temperature);
    copy.setMolarVolume(volume);
    for (int i = 0; i < copy.getNumberOfComponents(); i++) {
      copy.getComponent(i).init(temperature, copy.getPressure(), copy.getNumberOfMolesInPhase(), 1.0, 1);
    }
    copy.volInit();
    return copy;
  }

  private void close(double expected, double actual, double relative, String label) {
    assertEquals(expected, actual, Math.max(1e-60, Math.abs(expected) * relative), label);
  }

  @Test
  void pcsaftDiameterAndHelmholtzTemperatureDerivatives() {
    for (boolean associating : new boolean[] {false, true}) {
      for (boolean mixture : new boolean[] {false, true}) {
        PhasePCSAFT p = pcsaft(associating, mixture);
        double h = 0.02;
        PhasePCSAFT plus = shifted(p, p.getTemperature() + h, p.getMolarVolume());
        PhasePCSAFT minus = shifted(p, p.getTemperature() - h, p.getMolarVolume());
        close((plus.getDSAFT() - minus.getDSAFT()) / (2 * h), p.getdDSAFTdT(), 1e-6, "diameter moment dT");
        close((plus.getdDSAFTdT() - minus.getdDSAFTdT()) / (2 * h), p.getd2DSAFTdTdT(), 1e-5, "diameter moment dTT");
        close((plus.F_HC_SAFT() - minus.F_HC_SAFT()) / (2 * h), p.dF_HC_SAFTdT(), 1e-5, "HC dT");
        close((plus.F_DISP1_SAFT() - minus.F_DISP1_SAFT()) / (2 * h), p.dF_DISP1_SAFTdT(), 1e-5, "D1 dT");
        close((plus.getF2dispZHC() - minus.getF2dispZHC()) / (2 * h), p.calcdF2dispZHCdT(), 1e-5,
            "ZHC dT " + associating + " " + mixture);
        close((plus.F_DISP2_SAFT() - minus.F_DISP2_SAFT()) / (2 * h), p.dF_DISP2_SAFTdT(), 1e-5,
            "D2 dT " + p.getClass().getSimpleName() + " mixture=" + mixture);
        close((plus.dFdT() - minus.dFdT()) / (2 * h), p.dFdTdT(), 1e-5, "Helmholtz dTT " + associating);
        close((plus.dFdV() - minus.dFdV()) / (2 * h), p.dFdTdV(), 1e-5, "Helmholtz dTV " + associating);
        close((plus.getF() - minus.getF()) / (2 * h), p.dFdT(), 1e-5,
            "Helmholtz dT associating=" + associating + " mixture=" + mixture);
      }
    }
  }

  @Test
  void pcsaftVolumeDerivativesMatchHelmholtzEnergy() {
    for (boolean associating : new boolean[] {false, true}) {
      PhasePCSAFT p = pcsaft(associating, true);
      double h = p.getMolarVolume() * 1e-4;
      PhasePCSAFT plus = shifted(p, p.getTemperature(), p.getMolarVolume() + h);
      PhasePCSAFT minus = shifted(p, p.getTemperature(), p.getMolarVolume() - h);
      double totalVolumeStep = h * p.getNumberOfMolesInPhase();
      close((plus.getF() - minus.getF()) / (2 * totalVolumeStep), p.dFdV(), 1e-6, "Helmholtz dV");
      close((plus.dFdV() - minus.dFdV()) / (2 * totalVolumeStep), p.dFdVdV(), 1e-6, "Helmholtz dVV");
    }
  }

  @Test
  void associatingPcsaftHardChainVolumeCurvature() {
    PhasePCSAFT p = pcsaft(true, true);
    double h = p.getMolarVolume() * 1e-4;
    PhasePCSAFT plus = shifted(p, p.getTemperature(), p.getMolarVolume() + h);
    PhasePCSAFT minus = shifted(p, p.getTemperature(), p.getMolarVolume() - h);
    double deltaVolumeSI = h * p.getNumberOfMolesInPhase() * 1e-5;
    close((plus.dF_HC_SAFTdV() - minus.dF_HC_SAFTdV()) / (2 * deltaVolumeSI), p.dF_HC_SAFTdVdV(), 1e-6,
        "hard-chain dVV");
    close((plus.dF_HC_SAFTdVdV() - minus.dF_HC_SAFTdVdV()) / (2 * deltaVolumeSI), p.dF_HC_SAFTdVdVdV(), 1e-6,
        "hard-chain dVVV");
  }

}
