package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemAmmoniaEos;

/** Independent Gao 2020 ammonia Gibbs anchors and the Helmholtz energy identity. */
class AmmoniaGibbsEnergyTest {
  // CoolProp 7.2.0 Gao 2020 ammonia H and S at forced gas/liquid states.
  // https://github.com/CoolProp/CoolProp/blob/v7.2.0/dev/fluids/Ammonia.json
  private static final double[][] STATES = {{293.15, 5.0, -2772.744082140227}, {400.0, 50.0, -7657.521275151397},
      {293.15, 10.0, -1559.9864738338374}, {280.0, 10.0, -1178.3334017660281}};

  private static PhaseInterface initialize(SystemAmmoniaEos system, int index) {
    system.setTemperature(STATES[index][0]);
    system.setPressure(STATES[index][1]);
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setForcePhaseTypes(true);
    system.setPhaseType(0, index < 2 ? PhaseType.GAS : PhaseType.LIQUID);
    system.init(3);
    return system.getPhase(0);
  }

  private static void assertGibbs(PhaseInterface phase, int index) {
    double molarGibbs = phase.getGibbsEnergy() / phase.getNumberOfMolesInPhase();
    double molarEnthalpy = phase.getEnthalpy("J/mol");
    double molarEntropy = phase.getEntropy("J/molK");
    assertEquals(STATES[index][2], molarGibbs, 0.06, "CoolProp reference state " + index);
    assertEquals(molarEnthalpy - STATES[index][0] * molarEntropy, molarGibbs, 1.0e-8,
        "G = H - T*S at state " + index);
  }

  @Test
  void gasAndLiquidReferencesSurviveStateChangesAndRepeatInitialization() {
    SystemAmmoniaEos system = new SystemAmmoniaEos(STATES[0][0], STATES[0][1]);
    for (int index = 0; index < STATES.length; index++) {
      PhaseInterface phase = initialize(system, index);
      assertGibbs(phase, index);
      double firstGibbs = phase.getGibbsEnergy();
      assertGibbs(initialize(system, index), index);
      assertEquals(firstGibbs, system.getPhase(0).getGibbsEnergy(), 1.0e-8);
    }
    assertGibbs(initialize(system, 0), 0);
  }

  @Test
  void clonedAndSerializedSystemsRetainIndependentGibbsState() {
    SystemAmmoniaEos original = new SystemAmmoniaEos(STATES[0][0], STATES[0][1]);
    assertGibbs(initialize(original, 0), 0);

    SystemAmmoniaEos cloned = original.clone();
    assertGibbs(initialize(cloned, 2), 2);
    assertGibbs(original.getPhase(0), 0);
    assertNotEquals(original.getPhase(0).getGibbsEnergy(), cloned.getPhase(0).getGibbsEnergy());

    SystemAmmoniaEos restored = SerializationUtils.clone(original);
    assertGibbs(initialize(restored, 3), 3);
    assertGibbs(initialize(restored, 0), 0);
    assertGibbs(original.getPhase(0), 0);
  }
}
