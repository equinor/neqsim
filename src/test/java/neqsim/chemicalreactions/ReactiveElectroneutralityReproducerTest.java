package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/** Regression for the charge row used by reactive electrolyte equilibrium. */
class ReactiveElectroneutralityReproducerTest extends neqsim.NeqSimTest {
  /** Pitzer and electrolyte-EOS chemistry target zero charge rather than preserving a charged iterate. */
  @Test
  void chargeConstraintTargetsElectroneutralityForBothModelFamilies() {
    assertChargeTarget(createPitzer());
    assertChargeTarget(createElectrolyteCpa());
  }

  private static void assertChargeTarget(SystemInterface system) {
    double[] conservedQuantities = system.getChemicalReactionOperations().calcBVector();
    assertEquals(0.0, conservedQuantities[conservedQuantities.length - 1], 0.0);
  }

  private static SystemInterface createPitzer() {
    SystemPitzer system = new SystemPitzer(313.15, 50.0);
    addFeed(system);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");
    return system;
  }

  private static SystemInterface createElectrolyteCpa() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(303.15, 14.0);
    addFeed(system);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    return system;
  }

  private static void addFeed(SystemInterface system) {
    system.addComponent("water", 55.508);
    system.addComponent("CO2", 0.1);
  }
}
