package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/** The molecular beta solve must exclude stripped ions even if their coefficients underflow. */
class TPmultiflashStrippedIonTest {
  @ParameterizedTest
  @ValueSource(doubles = { 0.0, 1.0e-200, 1.0e-300 })
  void strippedIonsCannotPoisonMolecularHessian(double ionCoefficient) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(281.3, 50.0);
    fluid.addComponent("CO2", 0.2);
    fluid.addComponent("water", 0.8);
    fluid.addComponent("Ca++", 0.01);
    fluid.addComponent("Cl-", 0.02);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setBeta(0, 0.5);
    fluid.setBeta(1, 0.5);
    fluid.init(1);
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      for (int component = 0; component < fluid.getPhase(phase).getNumberOfComponents(); component++) {
        ComponentInterface species = fluid.getPhase(phase).getComponent(component);
        boolean ion = species.getIonicCharge() != 0;
        species.setz(ion ? 1.0e-100 : component == 0 ? 0.2 : 0.8);
        species.setx(ion ? 0.01 : component == 0 ? 0.2 : 0.8);
        species.setFugacityCoefficient(ion ? ionCoefficient : 1.0);
      }
    }
    TPmultiflash flash = new TPmultiflash(fluid, false);
    flash.setDoubleArrays();
    flash.calcQ();
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      assertEquals(0.0, flash.dQdbeta[phase][0], 1.0e-14);
      for (int other = 0; other < fluid.getNumberOfPhases(); other++) {
        assertTrue(Double.isFinite(flash.Qmatrix[phase][other]));
      }
    }
    flash.setXY();
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      assertEquals(0.2, fluid.getPhase(phase).getComponent("CO2").getx(), 1.0e-14);
      assertEquals(0.8, fluid.getPhase(phase).getComponent("water").getx(), 1.0e-14);
      assertTrue(fluid.getPhase(phase).getComponent("Ca++").getx() < 1.0e-40);
    }
  }
}
