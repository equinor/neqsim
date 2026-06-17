package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ChemicalCompatibilityAssessor} and the underlying rule engine.
 */
public class ChemicalCompatibilityAssessorTest {

  @Test
  public void compatibleSetReturnsCompatibleVerdict() {
    ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
    assessor.addChemical(ProductionChemical.thermodynamicHydrateInhibitor("MEG-90", 0.0));
    assessor.setTemperatureCelsius(40.0);
    assessor.evaluate();
    assertEquals(ChemicalCompatibilityAssessor.Verdict.COMPATIBLE, assessor.getVerdict());
  }

  @Test
  public void cationicCIAndAnionicSITriggerHighSeverity() {
    ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
    assessor.addChemical(ProductionChemical.corrosionInhibitor("CI-A", 50.0));
    assessor.addChemical(ProductionChemical.scaleInhibitor("SI-B", 20.0));
    assessor.setTemperatureCelsius(60.0);
    assessor.evaluate();
    assertEquals(ChemicalCompatibilityAssessor.Verdict.INCOMPATIBLE, assessor.getVerdict());
    assertFalse(assessor.getIssues().isEmpty());
  }

  @Test
  public void temperatureExceedsStabilityRangeFlagged() {
    ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
    ProductionChemical scavenger = ProductionChemical.h2sScavenger("SCV-1", 100.0);
    assessor.addChemical(scavenger);
    assessor.setTemperatureCelsius(95.0);
    assessor.evaluate();
    Boolean stable = assessor.getThermalStability().get("SCV-1");
    assertNotNull(stable);
    assertFalse(stable.booleanValue());
  }

  @Test
  public void highCalciumWaterFlagsPhosphonateInhibitor() {
    ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
    assessor.addChemical(ProductionChemical.scaleInhibitor("SI-Phos", 20.0));
    assessor.setCalciumMgL(3000.0);
    assessor.evaluate();
    assertTrue(assessor.getIssues().size() > 0);
  }

  @Test
  public void jsonOutputIncludesVerdictAndIssues() {
    ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
    assessor.addChemical(ProductionChemical.corrosionInhibitor("CI-A", 50.0));
    assessor.addChemical(ProductionChemical.scaleInhibitor("SI-B", 20.0));
    assessor.evaluate();
    String json = assessor.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"verdict\""));
    assertTrue(json.contains("\"issues\""));
    assertTrue(json.contains("\"interactionMatrix\""));
  }

  @Test
  public void rulesLoadFromDefaultResource() {
    java.util.List<ChemicalInteractionRule> rules = ChemicalInteractionRule.loadDefaultRules();
    assertNotNull(rules);
    assertTrue(rules.size() >= 20, "Expected at least 20 rules; got " + rules.size());
  }
}
