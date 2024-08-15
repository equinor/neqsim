package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StateOfMatterTest {
  @Test
  void testFromPhaseType() {
    Assertions.assertEquals(StateOfMatter.GAS, StateOfMatter.fromPhaseType(PhaseType.GAS));
    Assertions.assertEquals(StateOfMatter.LIQUID, StateOfMatter.fromPhaseType(PhaseType.LIQUID));
    Assertions.assertEquals(StateOfMatter.LIQUID, StateOfMatter.fromPhaseType(PhaseType.OIL));
    Assertions.assertEquals(StateOfMatter.LIQUID, StateOfMatter.fromPhaseType(PhaseType.AQUEOUS));
    Assertions.assertEquals(StateOfMatter.SOLID, StateOfMatter.fromPhaseType(PhaseType.SOLID));
    Assertions.assertEquals(StateOfMatter.SOLID,
        StateOfMatter.fromPhaseType(PhaseType.SOLIDCOMPLEX));
    Assertions.assertEquals(StateOfMatter.SOLID, StateOfMatter.fromPhaseType(PhaseType.HYDRATE));
    Assertions.assertEquals(StateOfMatter.SOLID, StateOfMatter.fromPhaseType(PhaseType.WAX));
  }

  @Test
  void testIsGas() {
    Assertions.assertTrue(StateOfMatter.isGas(PhaseType.GAS));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.LIQUID));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.OIL));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.AQUEOUS));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.SOLID));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.SOLIDCOMPLEX));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.HYDRATE));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.WAX));
  }

  @Test
  void testIsLiquid() {
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.GAS));
    Assertions.assertTrue(StateOfMatter.isLiquid(PhaseType.LIQUID));
    Assertions.assertTrue(StateOfMatter.isLiquid(PhaseType.OIL));
    Assertions.assertTrue(StateOfMatter.isLiquid(PhaseType.AQUEOUS));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.SOLID));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.SOLIDCOMPLEX));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.HYDRATE));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.WAX));
  }

  @Test
  void testIsSolid() {
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.GAS));
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.LIQUID));
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.OIL));
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.AQUEOUS));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.SOLID));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.SOLIDCOMPLEX));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.HYDRATE));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.WAX));
  }
}
