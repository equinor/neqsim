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
    Assertions.assertEquals(StateOfMatter.LIQUID,
        StateOfMatter.fromPhaseType(PhaseType.LIQUID_ASPHALTENE));
    Assertions.assertEquals(StateOfMatter.SOLID, StateOfMatter.fromPhaseType(PhaseType.SOLID));
    Assertions.assertEquals(StateOfMatter.SOLID,
        StateOfMatter.fromPhaseType(PhaseType.SOLIDCOMPLEX));
    Assertions.assertEquals(StateOfMatter.SOLID, StateOfMatter.fromPhaseType(PhaseType.HYDRATE));
    Assertions.assertEquals(StateOfMatter.SOLID, StateOfMatter.fromPhaseType(PhaseType.WAX));
    Assertions.assertEquals(StateOfMatter.SOLID, StateOfMatter.fromPhaseType(PhaseType.ASPHALTENE));
  }

  @Test
  void testIsGas() {
    Assertions.assertTrue(StateOfMatter.isGas(PhaseType.GAS));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.LIQUID));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.OIL));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.AQUEOUS));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.LIQUID_ASPHALTENE));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.SOLID));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.SOLIDCOMPLEX));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.HYDRATE));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.WAX));
    Assertions.assertFalse(StateOfMatter.isGas(PhaseType.ASPHALTENE));
  }

  @Test
  void testIsLiquid() {
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.GAS));
    Assertions.assertTrue(StateOfMatter.isLiquid(PhaseType.LIQUID));
    Assertions.assertTrue(StateOfMatter.isLiquid(PhaseType.OIL));
    Assertions.assertTrue(StateOfMatter.isLiquid(PhaseType.AQUEOUS));
    Assertions.assertTrue(StateOfMatter.isLiquid(PhaseType.LIQUID_ASPHALTENE));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.SOLID));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.SOLIDCOMPLEX));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.HYDRATE));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.WAX));
    Assertions.assertFalse(StateOfMatter.isLiquid(PhaseType.ASPHALTENE));
  }

  @Test
  void testIsSolid() {
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.GAS));
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.LIQUID));
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.OIL));
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.AQUEOUS));
    Assertions.assertFalse(StateOfMatter.isSolid(PhaseType.LIQUID_ASPHALTENE));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.SOLID));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.SOLIDCOMPLEX));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.HYDRATE));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.WAX));
    Assertions.assertTrue(StateOfMatter.isSolid(PhaseType.ASPHALTENE));
  }

  @Test
  void testIsAsphaltene() {
    // Test ASPHALTENE solid phase type
    Assertions.assertFalse(PhaseType.GAS == PhaseType.ASPHALTENE);
    Assertions.assertFalse(PhaseType.OIL == PhaseType.ASPHALTENE);
    Assertions.assertEquals("asphaltene", PhaseType.ASPHALTENE.getDesc());
    Assertions.assertEquals(PhaseType.ASPHALTENE, PhaseType.byDesc("asphaltene"));

    // Test LIQUID_ASPHALTENE (Pedersen's liquid-liquid approach)
    Assertions.assertEquals("asphaltene liquid", PhaseType.LIQUID_ASPHALTENE.getDesc());
    Assertions.assertEquals(PhaseType.LIQUID_ASPHALTENE, PhaseType.byDesc("asphaltene liquid"));

    // Test isAsphaltene helper method
    Assertions.assertTrue(StateOfMatter.isAsphaltene(PhaseType.ASPHALTENE));
    Assertions.assertTrue(StateOfMatter.isAsphaltene(PhaseType.LIQUID_ASPHALTENE));
    Assertions.assertFalse(StateOfMatter.isAsphaltene(PhaseType.GAS));
    Assertions.assertFalse(StateOfMatter.isAsphaltene(PhaseType.OIL));
    Assertions.assertFalse(StateOfMatter.isAsphaltene(PhaseType.LIQUID));
    Assertions.assertFalse(StateOfMatter.isAsphaltene(PhaseType.SOLID));
  }
}
