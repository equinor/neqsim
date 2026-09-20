package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemAmmoniaEos;

/** Pressure and its volume slope must describe the same reference EOS state. */
class AmmoniaPressureDerivativeTest {
  @Test
  void gasAndLiquidDerivativesMatchIndependentVolumePerturbations() {
    for (PhaseType type : new PhaseType[] {PhaseType.GAS, PhaseType.LIQUID}) {
      SystemAmmoniaEos system = new SystemAmmoniaEos(293.15, type == PhaseType.GAS ? 5.0 : 10.0);
      system.setNumberOfPhases(1);
      system.setMaxNumberOfPhases(1);
      system.setForcePhaseTypes(true);
      system.init(0);
      system.setPhaseType(0, type);
      system.init(3);
      PhaseAmmoniaEos p = (PhaseAmmoniaEos) system.getPhase(0);
      assertEquals(system.getPressure(), p.calcPressure(), 1e-5);
      double volume = p.getMolarVolume();
      double step = volume * 1e-5;
      double derivative = p.calcPressuredV();
      p.setMolarVolume(volume + step);
      double plus = p.calcPressure();
      p.setMolarVolume(volume - step);
      double minus = p.calcPressure();
      p.setMolarVolume(volume);
      double numerical = (plus - minus) / (2 * step * p.getNumberOfMolesInPhase());
      assertTrue(derivative < 0);
      assertEquals(numerical, derivative, Math.abs(numerical) * 1e-6);
    }
  }
}
