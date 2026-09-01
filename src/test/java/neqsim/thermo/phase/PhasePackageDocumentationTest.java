package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Executable coverage for the public calls in the phase-package documentation.
 */
class PhasePackageDocumentationTest {
  @Test
  void phaseInspectionExampleUsesCurrentInterfaces() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    assertTrue(fluid.getNumberOfPhases() >= 1);

    double moleFractionSum = 0.0;
    double volumeFractionSum = 0.0;
    double massFractionSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      assertNotNull(phase.getType());

      moleFractionSum += phase.getBeta();
      volumeFractionSum += fluid.getVolumeFraction(phaseIndex);
      massFractionSum += fluid.getWtFraction(phaseIndex);

      assertTrue(Double.isFinite(phase.getDensity("kg/m3")));
      assertTrue(Double.isFinite(phase.getZ()));
      assertTrue(Double.isFinite(phase.getViscosity("cP")));
      assertTrue(Double.isFinite(phase.getThermalConductivity("W/mK")));
    }

    assertEquals(1.0, moleFractionSum, 1.0e-10);
    assertEquals(1.0, volumeFractionSum, 1.0e-10);
    assertEquals(1.0, massFractionSum, 1.0e-10);

    if (fluid.hasPhaseType(PhaseType.GAS)) {
      PhaseInterface gas = fluid.getPhase(PhaseType.GAS);
      assertTrue(gas.getDensity("kg/m3") > 0.0);
    }
  }
}
