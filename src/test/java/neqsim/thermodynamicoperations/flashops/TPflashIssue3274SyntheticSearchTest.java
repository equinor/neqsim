package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;

class TPflashIssue3274SyntheticSearchTest {
  @Test
  void aqueousRollbackRequiresUnchangedPhaseTopology() {
    assertTrue(TPflash.hasSameTwoPhaseTopology(PhaseType.GAS, PhaseType.AQUEOUS, PhaseType.GAS, PhaseType.AQUEOUS));
    assertTrue(TPflash.hasSameTwoPhaseTopology(PhaseType.AQUEOUS, PhaseType.GAS, PhaseType.GAS, PhaseType.AQUEOUS),
        "phase ordering must not affect rollback eligibility");
    assertFalse(TPflash.hasSameTwoPhaseTopology(PhaseType.OIL, PhaseType.AQUEOUS, PhaseType.GAS, PhaseType.AQUEOUS),
        "a GAS-to-OIL root transition must not be overwritten by the saved GAS+AQUEOUS state");
  }
}
