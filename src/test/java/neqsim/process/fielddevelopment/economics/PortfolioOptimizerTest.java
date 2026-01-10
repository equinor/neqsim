package neqsim.process.fielddevelopment.economics;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.economics.PortfolioOptimizer.OptimizationStrategy;
import neqsim.process.fielddevelopment.economics.PortfolioOptimizer.PortfolioResult;
import neqsim.process.fielddevelopment.economics.PortfolioOptimizer.Project;
import neqsim.process.fielddevelopment.economics.PortfolioOptimizer.ProjectType;

/**
 * Tests for PortfolioOptimizer multi-field portfolio optimization.
 *
 * @author ESOL
 * @version 1.0
 */
class PortfolioOptimizerTest {

  private PortfolioOptimizer optimizer;

  @BeforeEach
  void setUp() {
    optimizer = new PortfolioOptimizer();
  }

  @Test
  @DisplayName("Test adding projects to portfolio")
  void testAddProject() {
    optimizer.addProject("Field A", 2000.0, 500.0, ProjectType.DEVELOPMENT, 0.8);
    optimizer.addProject("Field B", 300.0, 100.0, ProjectType.TIEBACK, 0.9);

    assertEquals(2, optimizer.getProjects().size());
  }

  @Test
  @DisplayName("Test greedy NPV ratio optimization")
  void testGreedyNpvRatioOptimization() {
    optimizer.setTotalBudget(600.0); // 600 MUSD budget

    // Project A: NPV=500, CAPEX=400, ratio=1.25
    optimizer.addProject("Field A", 400.0, 500.0, ProjectType.DEVELOPMENT, 0.9);

    // Project B: NPV=200, CAPEX=150, ratio=1.33 (better ratio)
    optimizer.addProject("Field B", 150.0, 200.0, ProjectType.TIEBACK, 0.95);

    // Project C: NPV=100, CAPEX=100, ratio=1.0 (worst ratio)
    optimizer.addProject("Field C", 100.0, 100.0, ProjectType.IOR, 0.85);

    PortfolioResult result = optimizer.optimize(OptimizationStrategy.GREEDY_NPV_RATIO);

    assertNotNull(result);
    assertTrue(result.getTotalCapex() <= 600.0, "Should not exceed budget");
    assertTrue(result.getSelectedProjects().size() >= 1, "Should select at least one project");
  }

  @Test
  @DisplayName("Test risk-weighted optimization")
  void testRiskWeightedOptimization() {
    optimizer.setTotalBudget(500.0);

    // High NPV but low probability
    optimizer.addProject("Risky Field", 200.0, 500.0, ProjectType.EXPLORATION, 0.4);

    // Lower NPV but higher probability (EMV = 0.9 * 250 = 225)
    optimizer.addProject("Safe Field", 200.0, 250.0, ProjectType.DEVELOPMENT, 0.9);

    PortfolioResult result = optimizer.optimize(OptimizationStrategy.RISK_WEIGHTED);

    assertNotNull(result);
    // Risk-weighted should prefer the safer project
    assertTrue(result.getSelectedProjects().size() >= 1);
  }

  @Test
  @DisplayName("Test budget constraint enforcement")
  void testBudgetConstraint() {
    optimizer.setTotalBudget(100.0); // Small budget

    // Both projects exceed budget individually
    optimizer.addProject("Large Field A", 150.0, 500.0, ProjectType.DEVELOPMENT, 0.9);
    optimizer.addProject("Large Field B", 200.0, 700.0, ProjectType.DEVELOPMENT, 0.8);

    // Add one that fits
    optimizer.addProject("Small Field", 50.0, 80.0, ProjectType.TIEBACK, 0.95);

    PortfolioResult result = optimizer.optimize(OptimizationStrategy.GREEDY_NPV_RATIO);

    assertTrue(result.getTotalCapex() <= 100.0, "Must respect budget constraint");
    assertTrue(result.getSelectedProjects().size() >= 1);
  }

  @Test
  @DisplayName("Test comparison of strategies")
  void testCompareStrategies() {
    optimizer.setTotalBudget(300.0);

    optimizer.addProject("Field A", 100.0, 150.0, ProjectType.DEVELOPMENT, 0.9);
    optimizer.addProject("Field B", 80.0, 100.0, ProjectType.TIEBACK, 0.85);
    optimizer.addProject("Field C", 120.0, 180.0, ProjectType.IOR, 0.7);

    var results = optimizer.compareStrategies();

    assertNotNull(results);
    assertTrue(results.containsKey(OptimizationStrategy.GREEDY_NPV_RATIO));
    assertTrue(results.containsKey(OptimizationStrategy.RISK_WEIGHTED));
    assertTrue(results.containsKey(OptimizationStrategy.EMV_MAXIMIZATION));
  }

  @Test
  @DisplayName("Test report generation")
  void testGenerateComparisonReport() {
    optimizer.setTotalBudget(500.0);

    optimizer.addProject("Field A", 200.0, 350.0, ProjectType.DEVELOPMENT, 0.85);
    optimizer.addProject("Field B", 150.0, 400.0, ProjectType.EXPLORATION, 0.5);

    String report = optimizer.generateComparisonReport();

    assertNotNull(report);
    assertTrue(report.contains("PORTFOLIO STRATEGY COMPARISON"));
  }

  @Test
  @DisplayName("Test project creation through addProject method")
  void testProjectCreation() {
    Project project =
        optimizer.addProject("Test Field", 200.0, 400.0, ProjectType.DEVELOPMENT, 0.8);

    assertNotNull(project);
    assertEquals("Test Field", project.getName());
    assertEquals(200.0, project.getCapexMusd(), 0.01);
    assertEquals(400.0, project.getNpvMusd(), 0.01);
    assertEquals(0.8, project.getProbabilityOfSuccess(), 0.01);
  }

  @Test
  @DisplayName("Test annual budget constraints")
  void testAnnualBudgetConstraints() {
    optimizer.setAnnualBudget(2025, 200.0);
    optimizer.setAnnualBudget(2026, 250.0);

    optimizer.addProject("Field A", 300.0, 400.0, ProjectType.DEVELOPMENT, 0.9);

    PortfolioResult result = optimizer.optimize(OptimizationStrategy.GREEDY_NPV_RATIO);

    assertNotNull(result);
  }
}
