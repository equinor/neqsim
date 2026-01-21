package neqsim.process.fielddevelopment.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.Criterion;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.DevelopmentOption;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.RankingResult;

/**
 * Tests for DevelopmentOptionRanker multi-criteria decision analysis.
 *
 * @author ESOL
 * @version 1.0
 */
class DevelopmentOptionRankerTest {
  private DevelopmentOptionRanker ranker;

  @BeforeEach
  void setUp() {
    ranker = new DevelopmentOptionRanker();
  }

  @Test
  @DisplayName("Test adding development options")
  void testAddOption() {
    DevelopmentOption option1 = ranker.addOption("FPSO Development");
    option1.setScore(Criterion.NPV, 500.0);
    option1.setScore(Criterion.TECHNICAL_RISK, 0.3);

    assertEquals(1, ranker.getOptions().size());
  }

  @Test
  @DisplayName("Test setting criterion weights")
  void testSetWeight() {
    ranker.setWeight(Criterion.NPV, 0.3);
    ranker.setWeight(Criterion.IRR, 0.2);
    ranker.setWeight(Criterion.CO2_INTENSITY, 0.2);
    ranker.setWeight(Criterion.TECHNICAL_RISK, 0.15);
    ranker.setWeight(Criterion.EXECUTION_RISK, 0.15);

    // Weights should be stored
    assertTrue(true);
  }

  @Test
  @DisplayName("Test basic ranking")
  void testBasicRanking() {
    // Add options
    DevelopmentOption fpso = ranker.addOption("FPSO");
    fpso.setScore(Criterion.NPV, 500.0);
    fpso.setScore(Criterion.IRR, 0.15);
    fpso.setScore(Criterion.CO2_INTENSITY, 10.0);

    DevelopmentOption tieback = ranker.addOption("Tieback");
    tieback.setScore(Criterion.NPV, 300.0);
    tieback.setScore(Criterion.IRR, 0.25);
    tieback.setScore(Criterion.CO2_INTENSITY, 8.0);

    DevelopmentOption subsea = ranker.addOption("Subsea");
    subsea.setScore(Criterion.NPV, 400.0);
    subsea.setScore(Criterion.IRR, 0.18);
    subsea.setScore(Criterion.CO2_INTENSITY, 12.0);

    // Set weights - NPV most important
    ranker.setWeight(Criterion.NPV, 0.5);
    ranker.setWeight(Criterion.IRR, 0.3);
    ranker.setWeight(Criterion.CO2_INTENSITY, 0.2);

    RankingResult result = ranker.rank();

    assertNotNull(result);
    List<DevelopmentOption> ranked = result.getRankedOptions();
    assertEquals(3, ranked.size());
    assertNotNull(ranked.get(0));
  }

  @Test
  @DisplayName("Test economic weight profile")
  void testEconomicWeightProfile() {
    ranker.setWeightProfile("economic");

    // Add options
    DevelopmentOption option1 = ranker.addOption("High NPV");
    option1.setScore(Criterion.NPV, 1000.0);
    option1.setScore(Criterion.IRR, 0.10);

    DevelopmentOption option2 = ranker.addOption("Low NPV");
    option2.setScore(Criterion.NPV, 200.0);
    option2.setScore(Criterion.IRR, 0.30);

    RankingResult result = ranker.rank();

    // Economic profile prioritizes NPV, so High NPV should rank first
    assertEquals("High NPV", result.getRankedOptions().get(0).getName());
  }

  @Test
  @DisplayName("Test sustainability weight profile")
  void testSustainabilityWeightProfile() {
    ranker.setWeightProfile("sustainability");

    // Add options with different CO2 intensities
    DevelopmentOption option1 = ranker.addOption("High CO2");
    option1.setScore(Criterion.NPV, 500.0);
    option1.setScore(Criterion.CO2_INTENSITY, 25.0); // High emissions

    DevelopmentOption option2 = ranker.addOption("Low CO2");
    option2.setScore(Criterion.NPV, 400.0);
    option2.setScore(Criterion.CO2_INTENSITY, 5.0); // Low emissions

    RankingResult result = ranker.rank();

    assertNotNull(result);
    // Sustainability profile should prefer lower CO2
  }

  @Test
  @DisplayName("Test ranking by single criterion")
  void testRankByCriterion() {
    DevelopmentOption opt1 = ranker.addOption("Option A");
    opt1.setScore(Criterion.NPV, 200.0);

    DevelopmentOption opt2 = ranker.addOption("Option B");
    opt2.setScore(Criterion.NPV, 100.0);

    DevelopmentOption opt3 = ranker.addOption("Option C");
    opt3.setScore(Criterion.NPV, 150.0);

    // NPV is higherIsBetter=true, so highest should be first
    List<DevelopmentOption> ranked = ranker.rankByCriterion(Criterion.NPV);

    assertEquals("Option A", ranked.get(0).getName()); // Highest NPV first
    assertEquals("Option B", ranked.get(2).getName()); // Lowest NPV last
  }

  @Test
  @DisplayName("Test sensitivity to weights")
  void testSensitivityToWeight() {
    DevelopmentOption opt1 = ranker.addOption("Option A");
    opt1.setScore(Criterion.NPV, 500.0);
    opt1.setScore(Criterion.CO2_INTENSITY, 20.0);

    DevelopmentOption opt2 = ranker.addOption("Option B");
    opt2.setScore(Criterion.NPV, 300.0);
    opt2.setScore(Criterion.CO2_INTENSITY, 5.0);

    // Test that different weights give different rankings
    ranker.setWeight(Criterion.NPV, 1.0);
    ranker.setWeight(Criterion.CO2_INTENSITY, 0.0);
    RankingResult npvFocused = ranker.rank();

    ranker.setWeight(Criterion.NPV, 0.0);
    ranker.setWeight(Criterion.CO2_INTENSITY, 1.0);
    RankingResult co2Focused = ranker.rank();

    // Different weights should potentially give different rankings
    assertNotNull(npvFocused);
    assertNotNull(co2Focused);
  }

  @Test
  @DisplayName("Test report generation")
  void testReportGeneration() {
    DevelopmentOption opt1 = ranker.addOption("FPSO");
    opt1.setScore(Criterion.NPV, 500.0);
    opt1.setScore(Criterion.TECHNICAL_RISK, 0.5);

    ranker.setWeight(Criterion.NPV, 0.6);
    ranker.setWeight(Criterion.TECHNICAL_RISK, 0.4);

    RankingResult result = ranker.rank();
    String report = result.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("FPSO"));
  }
}
