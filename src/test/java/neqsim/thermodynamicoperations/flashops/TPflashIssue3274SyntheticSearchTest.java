package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashIssue3274SyntheticSearchTest {
  @Test
  void balancedGasAqueousEndpointRemainsEligibleForCompositionRelaxation() {
    SystemInterface endpoint = new SystemSrkEos(260.0, 100.0);
    endpoint.addComponent("CO2", 0.543865141103918);
    endpoint.addComponent("methane", 0.2937712952303271);
    endpoint.addComponent("ethane", 0.07010605470616459);
    endpoint.addComponent("water", 0.09225750895959021);
    endpoint.setMixingRule("classic");
    endpoint.setMultiPhaseCheck(true);
    new ThermodynamicOperations(endpoint).TPflash();
    endpoint.init(3);

    assertEquals(2, endpoint.getNumberOfPhases());
    assertTrue(endpoint.hasPhaseType(PhaseType.GAS));
    assertTrue(endpoint.hasPhaseType(PhaseType.AQUEOUS));

    TPflash flash = new TPflash(endpoint, false);
    assertFalse(flash.shouldSkipCompositionRelaxedWaterRichCandidate(true, false, true, false, 1.0e-12),
        "a feasible GAS+AQUEOUS endpoint still needs a cold composition-relaxed candidate");
    assertTrue(flash.shouldSkipCompositionRelaxedWaterRichCandidate(true, false, false, false, 1.0e-12),
        "other feasible aqueous endpoints should retain the fast path");
  }
}
