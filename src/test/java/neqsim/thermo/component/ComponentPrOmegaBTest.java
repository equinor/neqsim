package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Persistent PR covolume coefficient regressions for issue 4548.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ComponentPrOmegaBTest {
  /** Verifies the analytical coefficient, input validation, reset and unchanged default. */
  @Test
  void coefficientValidationAndReset() {
    ComponentPR component = new ComponentPR("methane", 1.0, 1.0, 0);
    double original = 0.077803333 * component.R * component.getTC() / component.getPC();
    assertEquals(original, component.calcb(), 0.0);
    assertFalse(component.hasOmegaBOverride());
    assertTrue(Double.isNaN(component.getOmegaBOverride()));
    for (double invalid : new double[] {0.0, -0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> component.setOmegaB(invalid));
    }
    component.setOmegaB(0.081);
    assertEquals(0.081 * component.R * component.getTC() / component.getPC(), component.calcb(), 0.0);
    component.init(350.0, 100.0, 1.0, 1.0, 0);
    assertEquals(component.calcb(), component.getb(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> component.setOmegaB(0.0));
    assertEquals(0.081, component.getOmegaBOverride(), 0.0);
    component.clearOmegaBOverride();
    component.init(350.0, 100.0, 1.0, 1.0, 0);
    assertEquals(original, component.getb(), 0.0);
  }

  /** Verifies BNS constructor defaults and explicit rejection of association-model retuning. */
  @Test
  void specializedComponentsKeepTheirOwnContracts() {
    ComponentBNS bns = new ComponentBNS("methane", 1.0, 1.0, 0, 190.6, 46.0, 0.01604, 0.01, 0.45, 0.084, 0.0);
    double baseline = bns.calcb();
    assertEquals(0.084, bns.getOmegaB(), 0.0);
    bns.setOmegaB(0.082);
    assertEquals(0.082 * bns.R * bns.getTC() / bns.getPC(), bns.calcb(), 0.0);
    bns.clearOmegaBOverride();
    assertEquals(baseline, bns.calcb(), 0.0);
    ComponentUMRCPA cpa = new ComponentUMRCPA("water", 1.0, 1.0, 0);
    double original = cpa.calcb();
    assertThrows(UnsupportedOperationException.class, () -> cpa.setOmegaB(0.08));
    assertEquals(original, cpa.calcb(), 0.0);
  }

  /**
   * Verifies repeated initialization, actual flash, independent clone and Java serialization persistence.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void flashCloneAndSerialization() throws Exception {
    SystemInterface fluid = oil();
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      ((ComponentPR) fluid.getPhase(phase).getComponent("methane")).setOmegaB(0.081);
    }
    for (int repeat = 0; repeat < 3; repeat++) {
      fluid.init(0);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.initProperties();
      verifyFluidCoefficient(fluid, 0.081);
    }
    SystemInterface clone = fluid.clone();
    verifyFluidCoefficient(clone, 0.081);
    ((ComponentPR) clone.getPhase(0).getComponent("methane")).clearOmegaBOverride();
    verifyFluidCoefficient(fluid, 0.081);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(fluid);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      SystemInterface restored = (SystemInterface) in.readObject();
      new ThermodynamicOperations(restored).TPflash();
      verifyFluidCoefficient(restored, 0.081);
    }
  }

  /**
   * Builds the synthetic methane/decane assay used for initialization and serialization checks.
   *
   * @return initialized PR fluid
   */
  private SystemInterface oil() {
    SystemInterface fluid = new SystemPrEos(350.0, 100.0);
    fluid.addComponent("methane", 0.4);
    fluid.addComponent("n-decane", 0.6);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Checks the independently calculated coefficient on every allocated phase.
   *
   * @param fluid fluid under test
   * @param omegaB expected dimensionless coefficient
   */
  private void verifyFluidCoefficient(SystemInterface fluid, double omegaB) {
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      ComponentPR component = (ComponentPR) fluid.getPhase(phase).getComponent("methane");
      assertEquals(omegaB, component.getOmegaBOverride(), 0.0);
      assertEquals(omegaB * component.R * component.getTC() / component.getPC(), component.getb(), 1e-14);
    }
  }
}
