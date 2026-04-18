package neqsim.util.nucleation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link MulticomponentNucleation}.
 *
 * @author esol
 * @version 1.0
 */
@Disabled("Long-running test - disabled for CI")
class MulticomponentNucleationTest {

  private SystemInterface system;

  @BeforeEach
  void setUp() {
    // Create a gas-condensate system that will have two phases at low T
    system = new SystemSrkEos(253.15, 30.0); // -20 C, 30 bara
    system.addComponent("methane", 0.80);
    system.addComponent("ethane", 0.07);
    system.addComponent("propane", 0.05);
    system.addComponent("n-butane", 0.03);
    system.addComponent("n-pentane", 0.02);
    system.addComponent("n-hexane", 0.02);
    system.addComponent("n-heptane", 0.01);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
  }

  @Test
  void testConstructor() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    assertNotNull(mcn);
    assertFalse(mcn.isCalculated());
    assertEquals(MulticomponentNucleation.NucleationMode.PSEUDOCOMPONENT, mcn.getMode());
  }

  @Test
  void testPseudocomponentMode() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.setMode(MulticomponentNucleation.NucleationMode.PSEUDOCOMPONENT);
    mcn.setResidenceTime(1.0);
    mcn.calculate();

    assertTrue(mcn.isCalculated());
    // Should find at least some condensable components (the heavier ones)
    assertTrue(mcn.getNumberOfCondensableComponents() > 0,
        "Should identify condensable components");
    assertNotNull(mcn.getDominantComponent());
    assertFalse(mcn.getDominantComponent().isEmpty());
    assertTrue(mcn.getEffectiveMW() > 0.0, "Effective MW should be positive");
    assertTrue(mcn.getEffectiveDensity() > 0.0, "Effective density should be positive");
    assertTrue(mcn.getEffectiveSurfaceTension() > 0.0,
        "Effective surface tension should be positive");
  }

  @Test
  void testIndependentMode() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.setMode(MulticomponentNucleation.NucleationMode.INDEPENDENT);
    mcn.setResidenceTime(1.0);
    mcn.calculate();

    assertTrue(mcn.isCalculated());
    int numCond = mcn.getNumberOfCondensableComponents();
    assertTrue(numCond > 0, "Should identify condensable components");

    List<Double> rates = mcn.getComponentNucleationRates();
    assertEquals(numCond, rates.size());
  }

  @Test
  void testCondensableComponentIdentification() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.calculate();

    List<String> names = mcn.getCondensableComponentNames();
    assertNotNull(names);
    assertTrue(names.size() > 0, "Should identify condensable components");

    // Heavier hydrocarbons should be condensable at -20 C, 30 bara
    List<Double> supersats = mcn.getComponentSupersaturations();
    assertEquals(names.size(), supersats.size());
    for (double s : supersats) {
      assertTrue(s > 0.0, "Supersaturation ratio should be positive");
    }
  }

  @Test
  void testCondensationFractions() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.calculate();

    List<Double> fractions = mcn.getComponentCondensationFractions();
    double sum = 0.0;
    for (double f : fractions) {
      assertTrue(f >= 0.0 && f <= 1.0, "Fraction should be between 0 and 1");
      sum += f;
    }
    if (!fractions.isEmpty()) {
      assertEquals(1.0, sum, 0.01, "Condensation fractions should sum to 1.0");
    }
  }

  @Test
  void testHeterogeneousNucleation() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.setHeterogeneous(true, 60.0);
    mcn.setResidenceTime(1.0);
    mcn.calculate();

    assertTrue(mcn.isCalculated());

    // Compare with homogeneous — heterogeneous should give higher rates
    MulticomponentNucleation mcnHom = new MulticomponentNucleation(system);
    mcnHom.setResidenceTime(1.0);
    mcnHom.calculate();

    // Heterogeneous reduces barrier so rate should be >= homogeneous
    assertTrue(mcn.getTotalNucleationRate() >= mcnHom.getTotalNucleationRate(),
        "Heterogeneous rate should be >= homogeneous rate");
  }

  @Test
  void testToJson() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.setResidenceTime(1.0);
    mcn.calculate();

    String json = mcn.toJson();
    assertNotNull(json);
    assertTrue(json.contains("PSEUDOCOMPONENT"));
    assertTrue(json.contains("condensableComponents"));
    assertTrue(json.contains("totalNucleationRate"));
  }

  @Test
  void testToMap() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.setMode(MulticomponentNucleation.NucleationMode.INDEPENDENT);
    mcn.setResidenceTime(1.0);
    mcn.calculate();

    Map<String, Object> map = mcn.toMap();
    assertNotNull(map);
    assertEquals("INDEPENDENT", map.get("mode"));
    assertNotNull(map.get("results"));
    assertNotNull(map.get("condensableComponents"));
  }

  @Test
  void testSinglePhaseSystemNoNucleation() {
    // High temperature, moderate pressure — single phase
    SystemInterface singlePhase = new SystemSrkEos(353.15, 50.0); // 80 C, 50 bara
    singlePhase.addComponent("methane", 0.90);
    singlePhase.addComponent("ethane", 0.10);
    singlePhase.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(singlePhase);
    ops.TPflash();
    singlePhase.initProperties();

    MulticomponentNucleation mcn = new MulticomponentNucleation(singlePhase);
    mcn.calculate();

    assertTrue(mcn.isCalculated());
    // With only light components and high T, no significant condensation expected
    assertEquals(0.0, mcn.getTotalNucleationRate(), 1e-10,
        "Single-phase system should have zero nucleation rate");
  }

  @Test
  void testCondensableThreshold() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.setCondensableThreshold(0.5); // Very high threshold
    mcn.calculate();

    // With very high threshold, only major liquid components should qualify
    int countHigh = mcn.getNumberOfCondensableComponents();

    MulticomponentNucleation mcn2 = new MulticomponentNucleation(system);
    mcn2.setCondensableThreshold(1e-10); // Very low threshold
    mcn2.calculate();

    int countLow = mcn2.getNumberOfCondensableComponents();

    assertTrue(countLow >= countHigh,
        "Lower threshold should find at least as many condensable components");
  }

  @Test
  void testPseudocomponentCNTAccessible() {
    MulticomponentNucleation mcn = new MulticomponentNucleation(system);
    mcn.setMode(MulticomponentNucleation.NucleationMode.PSEUDOCOMPONENT);
    mcn.calculate();

    if (mcn.getNumberOfCondensableComponents() > 0) {
      ClassicalNucleationTheory cnt = mcn.getPseudocomponentCNT();
      assertNotNull(cnt, "Pseudocomponent CNT should be available after calculation");
    }
  }
}
