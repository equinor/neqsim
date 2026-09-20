package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemPCSAFT;

/** PC-SAFT roots must close pressure inside the hard-sphere domain without history dependence. */
class PcsaftVolumeDomainTest {
  private SystemPCSAFT fluid(double pressure) {
    SystemPCSAFT system = new SystemPCSAFT(300.0, pressure);
    system.addComponent("n-butane", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    system.init(2);
    return system;
  }

  @Test
  void freshAndPressureCycledRootsArePhysical() {
    SystemPCSAFT fresh = fluid(50.0);
    SystemPCSAFT walked = fluid(100.0);
    walked.setPressure(50.0);
    walked.init(2);
    for (int i = 0; i < fresh.getNumberOfPhases(); i++) {
      PhasePCSAFT phase = (PhasePCSAFT) fresh.getPhase(i);
      assertTrue(phase.getNSAFT() > 0 && phase.getNSAFT() < 1, "packing fraction " + phase.getNSAFT());
      assertEquals(50.0, phase.calcPressure(), 1e-6);
      assertEquals(walked.getPhase(i).getZ(), phase.getZ(), 1e-8);
      assertTrue(Double.isFinite(phase.getComponent(0).getLogFugacityCoefficient()));
      assertTrue(phase.getComponent(0).getFugacityCoefficient() > 0);
    }
  }

  @Test
  void nearbyStatesAlsoClosePressure() {
    for (double pressure : new double[] {20, 45, 55, 100}) {
      SystemPCSAFT system = fluid(pressure);
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        PhasePCSAFT phase = (PhasePCSAFT) system.getPhase(i);
        assertTrue(phase.getNSAFT() > 0 && phase.getNSAFT() < 1);
        assertEquals(pressure, phase.calcPressure(), 1e-6);
      }
    }
  }
}
