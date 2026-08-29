package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Verifies the public TP-flash algorithm usage example against the current API. */
class TPflashAlgorithmDocumentationTest {

  @Test
  void testPhaseTypedHydrocarbonUsageExample() {
    SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
    system.addComponent("methane", 0.7);
    system.addComponent("ethane", 0.2);
    system.addComponent("propane", 0.1);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();

    double betaTotal = 0.0;
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      double phaseFraction = system.getBeta(phaseNumber);
      assertTrue(Double.isFinite(phaseFraction));
      assertTrue(phaseFraction >= 0.0);
      assertTrue(phaseFraction <= 1.0);
      betaTotal += phaseFraction;
    }

    assertEquals(1.0, betaTotal, 1.0e-12);
    assertTrue(system.hasPhaseType(PhaseType.GAS));

    int gasPhaseNumber = system.getPhaseNumberOfPhase(PhaseType.GAS);
    double vaporFraction = system.getBeta(gasPhaseNumber);
    assertTrue(vaporFraction >= 0.0);
    assertTrue(vaporFraction <= 1.0);
    assertEquals(298.15, system.getTemperature(), 1.0e-10);
    assertEquals(10.0, system.getPressure(), 1.0e-10);
  }
}
