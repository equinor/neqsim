package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemUMRCPAEoS;

/** Regression coverage for association-site initialization from the overall composition. */
class CPAAssociationInitializationTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @ValueSource(strings = { "srk", "statoil", "electrolyte", "umr" })
  void reinitializationRestoresAssociatingComponentsFromOverallMoles(String model) {
    SystemInterface fluid = createFluid(model);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("water", 10.0);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(1);
    double reference = fluid.getPhase(0).getComponent("water").getFugacityCoefficient();
    assertTrue(Double.isFinite(reference));

    // init(0) replaces phase mole fractions with the feed composition. Its site topology must
    // therefore not depend on the preceding flash having depleted an associating component.
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface target = fluid.getPhase(phase);
      target.getComponent("water").setx(0.0);
      target.init(fluid.getTotalNumberOfMoles(), fluid.getNumberOfComponents(), 0, target.getType(),
          fluid.getBeta(phase));
    }
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      int originalSites = fluid.getPhase(phase).getComponent("water").getOrginalNumberOfAssociationSites();
      assertTrue(originalSites > 0);
      assertEquals(originalSites, fluid.getPhase(phase).getComponent("water").getNumberOfAssociationSites(),
          model + " water must retain its association sites after composition initialization");
    }
    fluid.init(1);
    assertEquals(reference, fluid.getPhase(0).getComponent("water").getFugacityCoefficient(), 1.0e-10);
  }

  @ParameterizedTest
  @ValueSource(strings = { "srk", "statoil", "electrolyte", "umr" })
  void pureWaterActivityIsUnityWithFreshReferencePhase(String model) {
    SystemInterface fluid = createFluid(model);
    fluid.addComponent("water", 10.0);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(1);
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      assertEquals(1.0, fluid.getPhase(phase).getActivityCoefficient(0), 1.0e-8,
          model + " pure-water reference must have the same association contribution as the fluid");
    }
  }

  private SystemInterface createFluid(String model) {
    if ("srk".equals(model)) {
      return new SystemSrkCPA(298.15, 20.0);
    } else if ("electrolyte".equals(model)) {
      return new SystemElectrolyteCPAstatoil(298.15, 20.0);
    } else if ("umr".equals(model)) {
      return new SystemUMRCPAEoS(298.15, 20.0);
    } else {
      return new SystemSrkCPAstatoil(298.15, 20.0);
    }
  }
}
