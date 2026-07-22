package neqsim.process.chemistry.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ScaleRemediationAdvisor} — deposit-to-dissolver recommendation used in RCA proposed solutions.
 */
public class ScaleRemediationAdvisorTest {

  @Test
  void testKnowledgeBaseLoads() {
    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    assertFalse(advisor.getAllOptions().isEmpty(), "Remediation knowledge base must load from the classpath");
  }

  @Test
  void testAliasResolution() {
    assertEquals("CaCO3", ScaleRemediationAdvisor.canonicalKey("calcite"));
    assertEquals("BaSO4", ScaleRemediationAdvisor.canonicalKey("Barite"));
    assertEquals("FeS", ScaleRemediationAdvisor.canonicalKey("mackinawite"));
    assertEquals("CaSO4", ScaleRemediationAdvisor.canonicalKey("gypsum"));
    assertEquals("Dithiazine", ScaleRemediationAdvisor.canonicalKey("scavenger solids"));
    assertEquals("UNKNOWN", ScaleRemediationAdvisor.canonicalKey(null));
    assertEquals("UNKNOWN", ScaleRemediationAdvisor.canonicalKey("  "));
  }

  @Test
  void testCarbonateRecommendationIsAcid() {
    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    List<ScaleRemediationAdvisor.RemediationOption> options = advisor.recommendFor("calcite");
    assertFalse(options.isEmpty());
    // Highest-effectiveness option first.
    ScaleRemediationAdvisor.RemediationOption best = options.get(0);
    assertEquals("CaCO3", best.getScaleType());
    assertEquals("HIGH", best.getEffectiveness());
    assertTrue(
        best.getDissolver().toLowerCase().contains("hcl") || best.getDissolver().toLowerCase().contains("hydrochloric"),
        "Best carbonate remedy should be acid-based");
  }

  @Test
  void testOptionsOrderedByEffectiveness() {
    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    List<ScaleRemediationAdvisor.RemediationOption> options = advisor.recommendFor("CaCO3");
    for (int i = 1; i < options.size(); i++) {
      assertTrue(options.get(i - 1).effectivenessRank() >= options.get(i).effectivenessRank(),
          "Options must be ordered by descending effectiveness");
    }
  }

  @Test
  void testSulfateScaleIsNotAcid() {
    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    List<ScaleRemediationAdvisor.RemediationOption> options = advisor.recommendFor("barite");
    assertFalse(options.isEmpty());
    for (ScaleRemediationAdvisor.RemediationOption option : options) {
      assertEquals("BaSO4", option.getScaleType());
      assertTrue(option.getDissolver().toLowerCase().contains("chelant"),
          "Barite is acid-insoluble; remedy must be a chelant");
    }
  }

  @Test
  void testDithiazineRemediationMentionsPhControl() {
    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    List<ScaleRemediationAdvisor.RemediationOption> options = advisor.recommendFor("dithiazine");
    assertFalse(options.isEmpty());
    boolean phMentioned = false;
    for (ScaleRemediationAdvisor.RemediationOption option : options) {
      if (option.getCautions().toLowerCase().contains("ph")) {
        phMentioned = true;
      }
    }
    assertTrue(phMentioned, "Dithiazine remedy must flag restoring pH control to prevent recurrence");
  }

  @Test
  void testUnknownFallback() {
    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    List<ScaleRemediationAdvisor.RemediationOption> options = advisor.recommendFor("some-mystery-deposit");
    assertFalse(options.isEmpty());
    assertEquals("UNKNOWN", options.get(0).getScaleType());
  }

  @Test
  void testMultiMineralRecommendation() {
    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    Map<String, List<ScaleRemediationAdvisor.RemediationOption>> result = advisor
        .recommendForMinerals(Arrays.asList("calcite", "barite", "mackinawite"));
    assertTrue(result.containsKey("CaCO3"));
    assertTrue(result.containsKey("BaSO4"));
    assertTrue(result.containsKey("FeS"));
  }

  @Test
  void testJsonOutput() {
    ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
    String json = advisor.toJson("CaCO3");
    assertTrue(json.contains("CaCO3"));
    assertTrue(json.contains("options"));
    assertTrue(json.contains("disclaimer"));
  }
}
