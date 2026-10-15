package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.netlib.util.doubleW;
import neqsim.thermo.phase.PhaseEOSCGEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.util.gerg.EOSCG;

/**
 * Pressure qualification uses the actual EOS-CG model despite inheritance from the GERG phase.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class EOSCGPressureRootTest {
  /**
   * Creates gas and dense-liquid EOS-CG fixtures, including a species absent from GERG.
   *
   * @param fixture state index
   * @param amount total moles
   * @return initialized EOS-CG phase
   */
  private static PhaseEOSCGEos phase(int fixture, double amount) {
    double[][] states = {{320.0, 2.0}, {305.0, 80.0}, {298.15, 50.0}, {298.15, 50.0}};
    SystemEOSCGEos fluid = new SystemEOSCGEos(states[fixture][0], states[fixture][1]);
    if (fixture == 0) {
      fluid.addComponent("CO2", amount);
    } else if (fixture == 1) {
      fluid.addComponent("methane", 0.55 * amount);
      fluid.addComponent("CO2", 0.4 * amount);
      fluid.addComponent("nitrogen", 0.05 * amount);
    } else if (fixture == 2) {
      fluid.addComponent("CO2", 0.95 * amount);
      fluid.addComponent("SO2", 0.05 * amount);
    } else {
      fluid.addComponent("SO2", amount);
    }
    fluid.init(0);
    fluid.setNumberOfPhases(1);
    fluid.setBeta(0, 1.0);
    fluid.setPhaseType(0, fixture >= 2 ? PhaseType.LIQUID : PhaseType.GAS);
    fluid.init(3);
    return (PhaseEOSCGEos) fluid.getPhase(0);
  }

  /**
   * Maps fixture components independently to the native EOS-CG ordering.
   *
   * @param phase initialized EOS-CG phase
   * @return native mole fractions
   */
  private static double[] composition(PhaseEOSCGEos phase) {
    double[] result = new double[29];
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      String component = phase.getComponent(i).getComponentName();
      int index = "methane".equals(component) ? 1 : "nitrogen".equals(component) ? 2 : "CO2".equals(component) ? 3 : 22;
      result[index] = phase.getComponent(i).getx();
    }
    return result;
  }

  /**
   * Evaluates pressure directly in a separate native EOS-CG model at an imposed total volume.
   *
   * @param nativeModel independent native model
   * @param phase phase supplying temperature, amounts and composition
   * @param volume total internal volume
   * @return pressure in bar
   */
  private static double pressure(EOSCG nativeModel, PhaseEOSCGEos phase, double volume) {
    doubleW pressure = new doubleW(0.0);
    doubleW z = new doubleW(0.0);
    nativeModel.pressure(phase.getTemperature(), 100.0 * phase.getNumberOfMolesInPhase() / volume, composition(phase),
        pressure, z);
    return pressure.val / 100.0;
  }

  /**
   * Evaluates n*alphaR directly in a separate native EOS-CG model.
   *
   * @param nativeModel independent native model
   * @param phase phase supplying temperature, amounts and composition
   * @param volume total internal volume
   * @return dimensionless extensive residual Helmholtz energy
   */
  private static double residual(EOSCG nativeModel, PhaseEOSCGEos phase, double volume) {
    doubleW[][] alpha = new doubleW[4][4];
    for (int i = 0; i < alpha.length; i++) {
      for (int j = 0; j < alpha[i].length; j++) {
        alpha[i][j] = new doubleW(0.0);
      }
    }
    nativeModel.alphar(2, 3, phase.getTemperature(), 100.0 * phase.getNumberOfMolesInPhase() / volume,
        composition(phase), alpha);
    return phase.getNumberOfMolesInPhase() * alpha[0][0].val;
  }

  /** Native pressure and independent total-volume differences must agree at three extensive scales. */
  @Test
  void nativeEOSCGPressureSlopeAndResidualCurvatureMatchAcrossInventoryScales() {
    EOSCG nativeModel = new EOSCG();
    nativeModel.setup();
    for (int fixture = 0; fixture < 4; fixture++) {
      for (double amount : new double[] {1e-6, 1.0, 1e6}) {
        PhaseEOSCGEos phase = phase(fixture, amount);
        double volume = phase.getTotalVolume();
        double expectedPressure = pressure(nativeModel, phase, volume);
        assertEquals(phase.getPressure(), expectedPressure, Math.max(1e-7, phase.getPressure() * 1e-8));
        assertEquals(expectedPressure, phase.calcPressure(), Math.abs(expectedPressure) * 1e-12);
        FluidEquilibriumChecks.validatePressureRoot(phase);
        for (double scale : new double[] {1.0, 1.01}) {
          phase.setMolarVolume(phase.getMolarVolume() * scale);
          volume = phase.getTotalVolume();
          assertEquals(pressure(nativeModel, phase, volume), phase.calcPressure(), Math.abs(expectedPressure) * 1e-12);
          double step = volume * 1e-5;
          double finiteSlope = (pressure(nativeModel, phase, volume + step)
              - pressure(nativeModel, phase, volume - step)) / (2.0 * step);
          assertTrue(phase.calcPressuredV() < 0.0);
          assertEquals(finiteSlope, phase.calcPressuredV(), Math.abs(finiteSlope) * 3e-7);
          step = volume * 2e-4;
          double finiteCurvature = (residual(nativeModel, phase, volume + step)
              - 2.0 * residual(nativeModel, phase, volume) + residual(nativeModel, phase, volume - step))
              / (step * step);
          assertEquals(finiteCurvature, phase.dFdVdV(), Math.abs(finiteCurvature) * 5e-6);
        }
      }
    }
  }

  /** A changed stored volume is rejected even when GERG omits a material EOS-CG-only species. */
  @Test
  void pressureGateRejectsCorruptedEOSCGVolumesAcrossInventoryScales() {
    for (int fixture = 0; fixture < 4; fixture++) {
      for (double amount : new double[] {1e-6, 1.0, 1e6}) {
        PhaseEOSCGEos phase = phase(fixture, amount);
        phase.setMolarVolume(phase.getMolarVolume() * 1.1);
        assertThrows(IllegalStateException.class, () -> FluidEquilibriumChecks.validatePressureRoot(phase));
      }
    }
  }
}
