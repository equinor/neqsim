package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashAccelerationTest {
  @Test
  void testUnsafeDemExtrapolationFallsBackToSuccessiveSubstitution() {
    SystemInterface system = new SystemPrEos(300.0, 50.0);
    system.addComponent("methane", 0.8);
    system.addComponent("n-heptane", 0.2);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(false);
    new ThermodynamicOperations(system).TPflash();

    TPflash flash = new TPflash(system);
    double methaneKBefore = system.getPhase(0).getComponent(0).getK();
    double heptaneKBefore = system.getPhase(0).getComponent(1).getK();
    double betaBefore = system.getBeta();

    flash.lnK[0] = Math.log(methaneKBefore);
    flash.lnK[1] = Math.log(heptaneKBefore);
    flash.oldDeltalnK[0] = 0.99;
    flash.oldoldDeltalnK[0] = 1.0;
    flash.deltalnK[0] = 0.1;

    flash.accselerateSucsSubs();

    double methaneKAfter = system.getPhase(0).getComponent(0).getK();
    double heptaneKAfter = system.getPhase(0).getComponent(1).getK();
    assertTrue(Double.isFinite(methaneKAfter));
    assertTrue(Double.isFinite(heptaneKAfter));
    assertEquals(methaneKBefore, methaneKAfter, 1.0e-10);
    assertEquals(heptaneKBefore, heptaneKAfter, 1.0e-10);
    assertEquals(betaBefore, system.getBeta(), 1.0e-10);
  }

  @Test
  void testBoundedDemExtrapolationAboveUnitEigenvalueIsPreserved() {
    SystemInterface system = new SystemPrEos(300.0, 50.0);
    system.addComponent("methane", 0.8);
    system.addComponent("n-heptane", 0.2);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(false);
    new ThermodynamicOperations(system).TPflash();

    TPflash flash = new TPflash(system);
    double methaneKBefore = system.getPhase(0).getComponent(0).getK();
    flash.lnK[0] = Math.log(methaneKBefore);
    flash.lnK[1] = Math.log(system.getPhase(0).getComponent(1).getK());
    flash.oldDeltalnK[0] = 1.04;
    flash.oldoldDeltalnK[0] = 1.0;
    flash.deltalnK[0] = 0.01;

    flash.accselerateSucsSubs();

    double expectedLogKStep = 1.04 / (1.0 - 1.04) * 0.01;
    assertEquals(Math.exp(expectedLogKStep), system.getPhase(0).getComponent(0).getK() / methaneKBefore, 1.0e-10);
  }

  @Test
  void testAccelerationWorkspaceDoesNotRetainPreviousResult() {
    SystemInterface initialSystem = new SystemPrEos(300.0, 50.0);
    initialSystem.addComponent("methane", 0.8);
    initialSystem.addComponent("n-heptane", 0.2);
    initialSystem.setMixingRule("classic");
    initialSystem.setMultiPhaseCheck(false);
    new ThermodynamicOperations(initialSystem).TPflash();

    TPflash flash = new TPflash(initialSystem.clone());
    prepareBoundedAcceleration(flash);
    flash.accselerateSucsSubs();
    double expectedBeta = flash.system.getBeta();
    double expectedMethaneK = flash.system.getPhase(0).getComponent(0).getK();
    double expectedHeptaneK = flash.system.getPhase(0).getComponent(1).getK();

    flash.system = initialSystem.clone();
    prepareBoundedAcceleration(flash);
    flash.accselerateSucsSubs();

    assertEquals(expectedBeta, flash.system.getBeta(), 0.0);
    assertEquals(expectedMethaneK, flash.system.getPhase(0).getComponent(0).getK(), 0.0);
    assertEquals(expectedHeptaneK, flash.system.getPhase(0).getComponent(1).getK(), 0.0);
  }

  private void prepareBoundedAcceleration(TPflash flash) {
    for (int componentIndex = 0; componentIndex < flash.system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      flash.lnK[componentIndex] = Math.log(flash.system.getPhase(0).getComponent(componentIndex).getK());
      flash.oldDeltalnK[componentIndex] = 0.8;
      flash.oldoldDeltalnK[componentIndex] = 1.0;
      flash.deltalnK[componentIndex] = 1.0e-4 * (componentIndex + 1.0);
    }
  }
}
