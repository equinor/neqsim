package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the asphaltene characterization fixes: shared component detection, mass-based
 * asphaltene-rich test, parameters applied to all phases, Pedersen correlations, smooth interaction parameters, the
 * gamma split and the SARA resin with asphaltene-resin cross-association.
 */
class AsphalteneCharacterizationFixesTest {

  @Test
  void componentNamesAreRecognisedConsistently() {
    assertTrue(AsphalteneComponents.isAsphaltene("asphaltene"));
    assertTrue(AsphalteneComponents.isAsphaltene("Asphaltene_PC"));
    assertTrue(AsphalteneComponents.isAsphaltene("Asphaltene_3_PC"));
    assertTrue(AsphalteneComponents.isAsphaltene("Asph_2_PC"));
    assertFalse(AsphalteneComponents.isAsphaltene("nC10"));
    assertFalse(AsphalteneComponents.isAsphaltene((String) null));
  }

  @Test
  void allPhasesReturnsEveryAllocatedPhaseOnce() {
    SystemInterface fluid = new SystemSrkEos(373.15, 50.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-heptane", 0.5);
    fluid.setMixingRule("classic");
    List<PhaseInterface> phases = AsphalteneComponents.allPhases(fluid);
    assertTrue(phases.size() >= 2, "Both allocated phases must be returned");
    for (int i = 0; i < phases.size(); i++) {
      for (int j = i + 1; j < phases.size(); j++) {
        assertFalse(phases.get(i) == phases.get(j));
      }
    }
  }

  @Test
  void pedersenCriticalTemperatureUsesDensity() {
    PedersenAsphalteneCharacterization light = new PedersenAsphalteneCharacterization(1000.0, 1.05, 0.05);
    PedersenAsphalteneCharacterization dense = new PedersenAsphalteneCharacterization(1000.0, 1.20, 0.05);
    light.characterize();
    dense.characterize();
    // Tc = 913.222 rho + ..., so 0.15 g/cm3 adds about 137 K (unless a floor or cap applies).
    assertEquals(913.222 * 0.15, dense.getCriticalTemperature() - light.getCriticalTemperature(), 1e-6);
    double rho = 1.10;
    double m = 1000.0;
    PedersenAsphalteneCharacterization c = new PedersenAsphalteneCharacterization(m, rho, 0.05);
    c.characterize();
    double expectedPc = 1.01325 * Math.exp(1.28155 + 1.26838 * Math.pow(rho, 0.25) + 1.67106e2 / m - 8.10164e3 / (m * m));
    assertEquals(Math.max(5.0, Math.min(50.0, expectedPc)), c.getCriticalPressure(), 1e-9);
  }

  @Test
  void kijScaleIsSmoothAndMatchesFormerBins() {
    assertEquals(1.5, PedersenAsphalteneCharacterization.kijScaleFactor(16.0), 1e-12);
    double previous = Double.POSITIVE_INFINITY;
    for (double m = 16.0; m < 600.0; m += 1.0) {
      double f = PedersenAsphalteneCharacterization.kijScaleFactor(m);
      assertTrue(f <= previous, "Scale must fall monotonically with molar mass");
      assertTrue(previous - f < 0.01 || previous == Double.POSITIVE_INFINITY, "No jumps");
      previous = f;
    }
    assertEquals(1.2, PedersenAsphalteneCharacterization.kijScaleFactor(75.0), 0.02);
    assertTrue(PedersenAsphalteneCharacterization.kijScaleFactor(500.0) < 0.6);
  }

  @Test
  void kijIsAppliedToAllPhasesAndAllSplitComponents() {
    SystemInterface fluid = new SystemSrkEos(373.15, 200.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-heptane", 0.45);
    PedersenAsphalteneCharacterization pedersen = new PedersenAsphalteneCharacterization();
    pedersen.setAsphalteneMW(1000.0);
    pedersen.addDistributedAsphaltene(fluid, 0.05, 3);
    fluid.setMixingRule("classic");
    pedersen.applyAsphalteneKij(fluid, 0.08);
    List<Integer> asphaltenes = AsphalteneComponents.indices(fluid);
    assertEquals(3, asphaltenes.size());
    double expectedMethane = 0.08 * PedersenAsphalteneCharacterization.kijScaleFactor(16.043);
    for (PhaseInterface phase : AsphalteneComponents.allPhases(fluid)) {
      for (int a : asphaltenes) {
        double kij = ((PhaseEosInterface) phase).getEosMixingRule().getBinaryInteractionParameter(a, 0);
        assertEquals(expectedMethane, kij, 1e-3);
      }
      assertEquals(0.0, ((PhaseEosInterface) phase).getEosMixingRule().getBinaryInteractionParameter(asphaltenes.get(0),
          asphaltenes.get(1)), 0.0, "Asphaltene-asphaltene pairs stay zero");
    }
  }

  @Test
  void gammaSplitConservesMolesAndMass() {
    double[][] split = PedersenAsphalteneCharacterization.gammaSplit(1200.0, 600.0, 2.0, 5);
    double moles = 0.0;
    double mean = 0.0;
    for (int i = 0; i < 5; i++) {
      moles += split[1][i];
      mean += split[1][i] * split[0][i];
      assertTrue(split[0][i] > 600.0);
    }
    assertEquals(1.0, moles, 1e-9);
    assertEquals(1200.0, mean, 1e-6);
    assertThrows(IllegalArgumentException.class, () -> PedersenAsphalteneCharacterization.gammaSplit(500, 600, 2, 3));
  }

  @Test
  void userMolecularWeightIsNotOverwrittenAndC7plusIsRequired() {
    AsphalteneCharacterization sara = new AsphalteneCharacterization(0.5, 0.3, 0.15, 0.05);
    sara.setMwAsphaltene(1500.0);
    sara.setC7plusProperties(300.0, 900.0);
    assertEquals(1500.0, sara.estimateAsphalteneMolecularWeight(), 0.0);
    AsphalteneCharacterization noC7 = new AsphalteneCharacterization(0.5, 0.3, 0.15, 0.05);
    SystemInterface fluid = new SystemSrkCPAstatoil(350.0, 100.0);
    fluid.addComponent("n-heptane", 1.0);
    assertThrows(IllegalStateException.class, () -> noC7.addAsphalteneComponents(fluid, 0.5));
    assertThrows(IllegalArgumentException.class, () -> new AsphalteneCharacterization(0.6, 0.3, 0.15, -0.05));
  }

  @Test
  void resinIsAddedAndCrossAssociatesWithAsphaltene() {
    AsphalteneCharacterization sara = new AsphalteneCharacterization(0.5, 0.3, 0.15, 0.05);
    sara.setC7plusProperties(300.0, 900.0);
    assertEquals(0.005, sara.getAsphalteneAssociationVolume(), 0.0, "Default matches the component database");

    SystemInterface fluid = new SystemSrkCPAstatoil(350.0, 150.0);
    fluid.addComponent("methane", 0.4);
    fluid.addComponent("n-heptane", 0.3);
    fluid.addComponent("nC10", 0.2);
    sara.addAsphalteneComponents(fluid, 0.1);
    String resin = AsphalteneCharacterization.findResinName(fluid);
    assertNotNull(resin, "Resin pseudo-component must be added");
    int r = fluid.getPhase(0).getComponent(resin).getComponentNumber();
    for (PhaseInterface phase : AsphalteneComponents.allPhases(fluid)) {
      assertEquals(2, phase.getComponent(r).getNumberOfAssociationSites());
      assertTrue(phase.getComponent(r) instanceof ComponentSrkCPA);
    }
    fluid.setMixingRule(10);
    assertTrue(sara.applyCrossAssociation(fluid));

    double withCross = asphalteneFugacityCoefficient(fluid);
    sara.setResinAsphalteneAssociationEnergy(0.0);
    sara.applyCrossAssociation(fluid); // explicit 0 energy: no asphaltene-resin bonding
    double combiningRule = asphalteneFugacityCoefficient(fluid);
    assertTrue(Double.isFinite(withCross) && Double.isFinite(combiningRule));
    assertNotEquals(withCross, combiningRule, "The explicit cross-association energy must change the asphaltene");
  }

  /**
   * Flashes a copy and returns ln(phi) of asphaltene in the first phase.
   *
   * @param fluid fluid
   * @return ln fugacity coefficient
   */
  private static double asphalteneFugacityCoefficient(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(1);
    return fluid.getPhase(0).getComponent("asphaltene").getLogFugacityCoefficient();
  }
}
