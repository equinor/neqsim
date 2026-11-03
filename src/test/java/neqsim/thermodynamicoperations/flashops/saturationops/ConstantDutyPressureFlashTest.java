package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification tests for {@link ConstantDutyPressureFlash}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ConstantDutyPressureFlashTest {
  /**
   * Verifies convergence to the independently calculated upper dew-pressure root from warm starts.
   */
  @Test
  void closesUpperDewRootFromWarmStarts() {
    double expectedPressure = 359.2450710350704;
    double[] startPressures = {300.0, 359.2417392730713, 400.0, 500.0};

    for (double startPressure : startPressures) {
      SystemInterface fluid = new SystemSrkEos(373.15, startPressure);
      fluid.addComponent("methane", 0.95);
      fluid.addComponent("n-decane", 0.05);
      fluid.setMixingRule("classic");
      fluid.useVolumeCorrection(false);
      fluid.init(0);

      fluid.setPressure(300.0);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.setPressure(startPressure);
      fluid.setNumberOfPhases(2);
      fluid.setBeta(0, 1.0 - 1e-10);
      fluid.setBeta(1, 1e-10);

      new ConstantDutyPressureFlash(fluid).run();
      fluid.init(1);

      assertEquals(expectedPressure, fluid.getPressure(), 1e-5);
      assertTrue(maxLogFugacityResidual(fluid) <= 1e-8);
      assertTrue(compositionDistance(fluid) > 1e-8);
    }
  }

  /**
   * Calculates the maximum component log-fugacity difference between two phases.
   *
   * @param fluid two-phase thermodynamic system
   * @return maximum absolute log-fugacity difference
   */
  private static double maxLogFugacityResidual(SystemInterface fluid) {
    double residual = 0.0;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      double phase0Fugacity = fluid.getPhase(0).getComponent(i).getx()
          * fluid.getPhase(0).getComponent(i).getFugacityCoefficient();
      double phase1Fugacity = fluid.getPhase(1).getComponent(i).getx()
          * fluid.getPhase(1).getComponent(i).getFugacityCoefficient();
      residual = Math.max(residual, Math.abs(Math.log(phase0Fugacity) - Math.log(phase1Fugacity)));
    }
    return residual;
  }

  /**
   * Calculates the L1 composition distance between two phases.
   *
   * @param fluid two-phase thermodynamic system
   * @return L1 composition distance
   */
  private static double compositionDistance(SystemInterface fluid) {
    double distance = 0.0;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      distance += Math.abs(fluid.getPhase(0).getComponent(i).getx() - fluid.getPhase(1).getComponent(i).getx());
    }
    return distance;
  }
}
