package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;

/** Regression coverage for GERG-2008 phase-root selection and fugacity synchronization. */
class PhaseGERG2008EosFugacityTest {

  /** Verify that an incipient liquid keeps its liquid root and current component fugacities. */
  @Test
  void incipientLiquidUsesLiquidDensityAndCurrentFugacities() {
    SystemInterface fluid = new SystemGERG2008Eos(190.26696845096356, 1.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.025);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("nitrogen", 0.005);
    fluid.init(0);
    fluid.setNumberOfPhases(2);
    fluid.setBeta(0, 1.0 - 1.0e-15);
    fluid.setBeta(1, 1.0e-15);

    double incipientTotal = 0.0;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      fluid.getPhase(0).getComponent(i).setx(fluid.getPhase(0).getComponent(i).getz());
      double liquidFraction = fluid.getPhase(1).getComponent(i).getz() / fluid.getPhase(0).getComponent(i).getK();
      fluid.getPhase(1).getComponent(i).setx(liquidFraction);
      incipientTotal += liquidFraction;
    }
    for (int i = 0; i < fluid.getPhase(1).getNumberOfComponents(); i++) {
      fluid.getPhase(1).getComponent(i).setx(fluid.getPhase(1).getComponent(i).getx() / incipientTotal);
    }
    fluid.init(1);

    assertTrue(fluid.getPhase(1).getDensity() > 300.0, "incipient liquid must use the high-density GERG root");
    assertTrue(fluid.getPhase(1).getDensity() > 10.0 * fluid.getPhase(0).getDensity(),
        "liquid and vapor density roots must remain distinct");
    PhaseGERG2008Eos liquidPhase = (PhaseGERG2008Eos) fluid.getPhase(1);
    for (int i = 0; i < liquidPhase.getNumberOfComponents(); i++) {
      double storedLogPhi = liquidPhase.getComponent(i).getLogFugacityCoefficient();
      double calculatedLogPhi = liquidPhase.getLogFugacityCoefficient(i);
      assertTrue(Double.isFinite(storedLogPhi), "stored GERG fugacity coefficient must be finite");
      assertEquals(calculatedLogPhi, storedLogPhi, 1.0e-5,
          "component fugacity must be refreshed after liquid phase classification");
    }
  }
}
