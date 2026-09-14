package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the material-tagged environment rules in {@link ChemicalInteractionRule}.
 *
 * <p>
 * A material rule whose threshold is keyed on a variable the assessor does not carry (flow velocity, dissolved oxygen,
 * water cut) previously matched unconditionally, so every carbon-steel study reported it. These tests pin the corrected
 * behaviour: unconditional and temperature-keyed material rules still match, unsupported specs do not.
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemicalInteractionRuleEnvironmentTest {

  /**
   * Finds the first loaded rule whose environment threshold spec matches the supplied text.
   *
   * @param spec the threshold specification to look for, e.g. {@code velocity>5mps}
   * @return the matching rule, or null when the bundled rule set does not contain it
   */
  private static ChemicalInteractionRule ruleWithSpec(String spec) {
    List<ChemicalInteractionRule> rules = ChemicalInteractionRule.loadDefaultRules();
    for (ChemicalInteractionRule rule : rules) {
      if ("material_carbon_steel".equals(rule.getChemical2Type()) && spec.equals(rule.getChemical2Ingredient())) {
        return rule;
      }
    }
    return null;
  }

  @Test
  public void velocityKeyedMaterialRuleDoesNotMatchWithoutAVelocity() {
    ChemicalInteractionRule rule = ruleWithSpec("velocity>5mps");
    assertNotNull(rule, "bundled rule set should contain the NORSOK M-506 velocity rule");
    assertTrue(rule.isEnvironmentRule());
    assertFalse(rule.environmentMatches(25.0, 2000.0, 5.0, 300.0, "carbon_steel"),
        "a velocity-keyed rule must not fire when no velocity is supplied");
  }

  @Test
  public void oxygenKeyedMaterialRuleDoesNotMatchWithoutAnOxygenLevel() {
    ChemicalInteractionRule rule = ruleWithSpec("O2<10ppb");
    assertNotNull(rule, "bundled rule set should contain the residual-oxygen rule");
    assertFalse(rule.environmentMatches(25.0, 2000.0, 5.0, 300.0, "carbon_steel"),
        "an oxygen-keyed rule must not fire when no oxygen level is supplied");
  }

  @Test
  public void unconditionalMaterialRuleStillMatches() {
    ChemicalInteractionRule rule = ruleWithSpec("*");
    assertNotNull(rule, "bundled rule set should contain an unconditional carbon-steel rule");
    assertTrue(rule.environmentMatches(25.0, 0.0, 0.0, 0.0, "carbon_steel"),
        "an unconditional material rule must still apply");
    assertFalse(rule.environmentMatches(25.0, 0.0, 0.0, 0.0, "316L"),
        "a carbon-steel rule must not apply to a different material");
  }

  @Test
  public void temperatureKeyedMaterialRuleStillGatesOnTemperature() {
    ChemicalInteractionRule rule = ruleWithSpec("T>60C");
    assertNotNull(rule, "bundled rule set should contain a temperature-gated carbon-steel rule");
    assertTrue(rule.environmentMatches(80.0, 0.0, 0.0, 0.0, "carbon_steel"));
    assertFalse(rule.environmentMatches(40.0, 0.0, 0.0, 0.0, "carbon_steel"));
  }

  @Test
  public void ambientChemicalCabinetDoesNotReportAVelocityDrivenCorrosionIssue() {
    ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
    assessor.addChemical(ProductionChemical.corrosionInhibitor("CI-flowline", 30.0));
    assessor.setTemperatureCelsius(25.0);
    assessor.setMaterial("carbon_steel");
    assessor.evaluate();
    for (java.util.Map<String, Object> issue : assessor.getIssues()) {
      Object mechanism = issue.get("mechanism");
      assertFalse(mechanism != null && String.valueOf(mechanism).contains("above 5 m/s"),
          "the velocity-keyed NORSOK M-506 rule must not be reported for a static chemical cabinet");
    }
  }
}
