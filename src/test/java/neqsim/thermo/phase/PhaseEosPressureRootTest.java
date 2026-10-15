package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Pressure residual regressions for stationary-volume false convergence.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PhaseEosPressureRootTest {
  /** A gas-start volume solve formerly stopped at 43.48 bara despite a requested 81 bara. */
  @Test
  void stationaryVolumeMustSatisfyTheIndependentPrPressureEquation() {
    for (double temperature : new double[] {193.75, 194.0, 194.25}) {
      for (double pressure : new double[] {78.5, 81.0, 82.0}) {
        for (double scale : new double[] {1e-6, 1.0, 1e6}) {
          SystemInterface fluid = fluid(temperature, pressure, scale, false);
          for (int phase = 0; phase < 2; phase++) {
            for (int initialization : new int[] {1, 3, 1}) {
              fluid.init(initialization, phase);
              PhaseEos eos = (PhaseEos) fluid.getPhase(phase);
              double volume = eos.getMolarVolume();
              double a = eos.getA() / Math.pow(eos.getNumberOfMolesInPhase(), 2.0);
              double b = eos.getB() / eos.getNumberOfMolesInPhase();
              double independent = ThermodynamicConstantsInterface.R * temperature / (volume - b)
                  - a / (volume * (volume + b) + b * (volume - b));
              assertEquals(pressure, independent, 1e-8 * pressure,
                  "A small volume step cannot establish pressure convergence");
              assertEquals(pressure, eos.calcPressure(), 1e-8 * pressure);
              assertTrue(Double.isFinite(eos.calcPressuredV()) && eos.calcPressuredV() < 0.0);
            }
          }
        }
      }
    }
  }

  /** A cubic fallback must not silently discard an additional pressure term supplied by a subclass. */
  @Test
  void incompatibleAnalyticalFallbackIsRejected() {
    SystemInterface fluid = fluid(194.0, 81.0, 1.0, true);
    RuntimeException rejected = assertThrows(RuntimeException.class, () -> fluid.init(1, 0));
    assertTrue(rejected.toString().contains("does not satisfy the phase pressure equation"));
  }

  /**
   * Creates the independently checked binary cubic fixture.
   *
   * @param temperature temperature in kelvin
   * @param pressure pressure in bar absolute
   * @param scale overall mole scale
   * @param offsetPressure whether to install the synthetic non-cubic pressure contribution
   * @return initialized binary fluid
   */
  private SystemInterface fluid(double temperature, double pressure, double scale, boolean offsetPressure) {
    SystemInterface fluid = new SystemPrEos(temperature, pressure);
    if (offsetPressure) {
      fluid.setPhase(new OffsetPressurePhase(), 0);
      fluid.setPhase(new OffsetPressurePhase(), 1);
      fluid.setTemperature(temperature);
      fluid.setPressure(pressure);
    }
    fluid.addComponent("methane", 0.9897248773388901 * scale);
    fluid.addComponent("n-heptane", (1.0 - 0.9897248773388901) * scale);
    fluid.setMixingRule("classic");
    for (int phase = 0; phase < 2; phase++) {
      ((EosMixingRulesInterface) fluid.getPhase(phase).getMixingRule()).setBinaryInteractionParameter(0, 1, 0.05);
    }
    fluid.init(0);
    fluid.setBeta(0.5);
    return fluid;
  }

  /** A synthetic 1 bara pressure term exercises virtual-EOS acceptance of an analytical proposal. */
  private static class OffsetPressurePhase extends PhasePrEos {
    private static final long serialVersionUID = 1000L;

    /** {@inheritDoc} */
    @Override
    public double dFdV() {
      return super.dFdV() - 1.0 / (R * getTemperature());
    }
  }
}
