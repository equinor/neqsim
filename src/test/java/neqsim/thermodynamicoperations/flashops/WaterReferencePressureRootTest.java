package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseWaterIAPWS;
import neqsim.thermo.util.steam.Iapws_if97;

/**
 * Pressure-root qualification for the legacy IF97 adapter's SI total-volume convention.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class WaterReferencePressureRootTest {
  /** Independent reference v(P) differences verify the reciprocal total-volume pressure slope. */
  @Test
  void pressureSlopeUsesTotalVolumeAcrossTemperaturesPressuresAndAmounts() {
    double[][] states = {{300, 1}, {300, 100}, {400, 1}, {500, 10}, {500, 100}};
    for (double[] state : states) {
      double normalizedSlope = Double.NaN;
      for (double moles : new double[] {1e-6, 1, 1e6}) {
        PhaseWaterIAPWS phase = phase(state[0], state[1], moles);
        double volume = phase.getTotalVolume();
        double molarVolume = phase.getMolarVolume();
        assertEquals(state[1], phase.calcPressure(), 1e-8 * Math.max(1, state[1]));
        double pressureMPa = state[1] / 10;
        double stepMPa = Math.max(1e-5, pressureMPa * 1e-4);
        double dVdP = moles * phase.getMolarMass()
            * (Iapws_if97.v_pt(pressureMPa + stepMPa, state[0]) - Iapws_if97.v_pt(pressureMPa - stepMPa, state[0]))
            / (20 * stepMPa);
        double slope = phase.calcPressuredV();
        assertTrue(Double.isFinite(slope) && slope < 0);
        assertEquals(1 / dVdP, slope, 2e-5 * Math.abs(slope));
        assertEquals(molarVolume, phase.getMolarVolume(), 0, "Derivative evaluation must restore volume");
        assertEquals(volume, phase.getTotalVolume(), 0);
        if (Double.isFinite(normalizedSlope)) {
          assertEquals(normalizedSlope, moles * slope, 1e-10 * Math.abs(normalizedSlope));
        }
        normalizedSlope = moles * slope;
        FluidEquilibriumChecks.validatePressureRoot(phase);
        phase.setMolarVolume(molarVolume * (1 + 1e-7));
        assertThrows(IllegalStateException.class, () -> FluidEquilibriumChecks.validatePressureRoot(phase),
            "A corrupted volume must be rejected independently of the overall mole amount");
      }
    }
  }

  /**
   * Initializes one pure reference-EOS phase without an amount-dependent system default.
   *
   * @param temperature temperature in kelvin
   * @param pressure pressure in bar
   * @param moles total phase amount in mol
   * @return initialized reference phase
   */
  private static PhaseWaterIAPWS phase(double temperature, double pressure, double moles) {
    PhaseWaterIAPWS phase = new PhaseWaterIAPWS();
    phase.addComponent("water", moles, moles, 0);
    phase.setTemperature(temperature);
    phase.setPressure(pressure);
    phase.init(moles, 1, 0, PhaseType.LIQUID, 1);
    phase.init(moles, 1, 1, PhaseType.LIQUID, 1);
    return phase;
  }
}
