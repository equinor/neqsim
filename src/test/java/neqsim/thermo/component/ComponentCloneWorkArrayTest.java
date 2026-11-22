package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression checks for component work arrays that remain live after cloning.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ComponentCloneWorkArrayTest {
  /**
   * Every EOS work array keeps its full capacity and independent values in both directions.
   *
   * @param pengRobinson whether to use PR instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void eosCloneOwnsAllDerivativeArrays(boolean pengRobinson) {
    ComponentEos source = (ComponentEos) gas(pengRobinson).getComponent(0);
    double[][] original = {source.Aij, source.Bij, source.dAdndn, source.dBdndn};
    for (double[] values : original) {
      values[0] = 7.0;
      values[values.length - 1] = 11.0;
    }
    ComponentEos copy = source.clone();
    double[][] copied = {copy.Aij, copy.Bij, copy.dAdndn, copy.dBdndn};
    for (int array = 0; array < original.length; array++) {
      assertNotSame(original[array], copied[array]);
      assertArrayEquals(original[array], copied[array], 0.0);
      copied[array][0] = 9.0;
      original[array][original[array].length - 1] = 13.0;
      assertEquals(7.0, original[array][0], 0.0);
      assertEquals(11.0, copied[array][copied[array].length - 1], 0.0);
    }
  }

  /**
   * Initializing a warmer cloned fluid must not change the source Helmholtz composition curvature.
   *
   * @param pengRobinson whether to use PR instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void evaluatingClonePreservesOriginalCurvature(boolean pengRobinson) {
    SystemInterface source = gas(pengRobinson);
    PhaseInterface phase = source.getPhase(0);
    ComponentEos component = (ComponentEos) phase.getComponent(0);
    double original = component.dFdNdN(1, phase, 2, source.getTemperature(), source.getPressure());
    assertTrue(Double.isFinite(original));
    SystemInterface copy = source.clone();
    copy.setTemperature(420.0);
    copy.init(3);
    double after = component.dFdNdN(1, phase, 2, source.getTemperature(), source.getPressure());
    assertEquals(original, after, 0.0, "Evaluating only the clone changed the source curvature");
  }

  /** Ordinary CPA association composition rows must not alias after a component clone. */
  @Test
  void cpaCloneOwnsNestedAssociationDerivatives() {
    SystemInterface fluid = new SystemSrkCPA(298.15, 1.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(1);
    fluid.setForceSinglePhase(PhaseType.AQUEOUS);
    fluid.init(3);
    ComponentSrkCPA source = (ComponentSrkCPA) fluid.getComponent(0);
    ComponentSrkCPA copy = source.clone();
    assertTrue(source.getXsitedni().length > 0);
    for (int site = 0; site < source.getXsitedni().length; site++) {
      assertNotSame(source.getXsitedni()[site], copy.getXsitedni()[site]);
      assertArrayEquals(source.getXsitedni()[site], copy.getXsitedni()[site], 0.0);
    }
    double original = source.getXsitedni(0, 0);
    copy.setXsitedni(0, 0, original + 1.0);
    assertEquals(original, source.getXsitedni(0, 0), 0.0);
    source.setXsitedni(0, 0, original - 1.0);
    assertEquals(original + 1.0, copy.getXsitedni(0, 0), 0.0);
  }

  /**
   * Builds an initialized gas with nonzero mixture derivatives.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @return single-phase fluid with composition derivatives initialized
   */
  private static SystemInterface gas(boolean pengRobinson) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(300.0, 30.0) : new SystemSrkEos(300.0, 30.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(3);
    return fluid;
  }
}
