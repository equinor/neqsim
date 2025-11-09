package neqsim.process.util.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard for comparing KPIs across multiple scenarios.
 * 
 * <p>
 * Provides visual comparison of scenario performance metrics, rankings, and recommendations for
 * process safety system optimization.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class KPIDashboard {
  private final Map<String, ScenarioKPI> scenarios = new HashMap<>();
  private final List<String> scenarioOrder = new ArrayList<>();

  /**
   * Adds a scenario with its KPIs to the dashboard.
   *
   * @param scenarioName name of the scenario
   * @param kpi KPI metrics for the scenario
   */
  public void addScenario(String scenarioName, ScenarioKPI kpi) {
    scenarios.put(scenarioName, kpi);
    if (!scenarioOrder.contains(scenarioName)) {
      scenarioOrder.add(scenarioName);
    }
  }

  /**
   * Prints a comprehensive comparison dashboard to console.
   */
  public void printDashboard() {
    if (scenarios.isEmpty()) {
      System.out.println("No scenarios to display.");
      return;
    }

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║                 SCENARIO KPI DASHBOARD                         ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    printSafetyMetrics();
    printProcessMetrics();
    printEnvironmentalMetrics();
    printScoreComparison();
    printRecommendations();
  }

  /**
   * Prints safety performance metrics comparison.
   */
  private void printSafetyMetrics() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│                    SAFETY PERFORMANCE                           │");
    System.out.println("├─────────────────────┬───────────────────────────────────────────┤");

    // Header
    System.out.print("│ Metric              │");
    for (String scenario : scenarioOrder) {
      System.out.printf(" %-12s │", truncate(scenario, 12));
    }
    System.out.println();
    System.out.println("├─────────────────────┼───────────────────────────────────────────┤");

    // Peak Pressure
    System.out.print("│ Peak Pressure (bara)│");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getPeakPressure() > 0) {
        System.out.printf(" %10.2f   │", kpi.getPeakPressure());
      } else {
        System.out.print("     N/A      │");
      }
    }
    System.out.println();

    // ESD Activation Time
    System.out.print("│ ESD Time (s)        │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getTimeToESDActivation() >= 0) {
        System.out.printf(" %10.2f   │", kpi.getTimeToESDActivation());
      } else {
        System.out.print("  Not Active  │");
      }
    }
    System.out.println();

    // Safety Margin
    System.out.print("│ Safety Margin (bara)│");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getSafetyMarginToMAWP() > 0) {
        System.out.printf(" %10.2f   │", kpi.getSafetyMarginToMAWP());
      } else {
        System.out.print("     N/A      │");
      }
    }
    System.out.println();

    // HIPPS Status
    System.out.print("│ HIPPS Tripped       │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      System.out.printf("     %-7s  │", kpi.isHippsTripped() ? "YES" : "NO");
    }
    System.out.println();

    // PSV Status
    System.out.print("│ PSV Activated       │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      System.out.printf("     %-7s  │", kpi.isPsvActivated() ? "YES" : "NO");
    }
    System.out.println();

    System.out.println("└─────────────────────┴───────────────────────────────────────────┘\n");
  }

  /**
   * Prints process performance metrics comparison.
   */
  private void printProcessMetrics() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│                   PROCESS PERFORMANCE                           │");
    System.out.println("├─────────────────────┬───────────────────────────────────────────┤");

    // Header
    System.out.print("│ Metric              │");
    for (String scenario : scenarioOrder) {
      System.out.printf(" %-12s │", truncate(scenario, 12));
    }
    System.out.println();
    System.out.println("├─────────────────────┼───────────────────────────────────────────┤");

    // Production Loss
    System.out.print("│ Production Loss (kg)│");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getProductionLoss() > 0) {
        System.out.printf(" %10.2f   │", kpi.getProductionLoss());
      } else {
        System.out.print("      0.00    │");
      }
    }
    System.out.println();

    // Recovery Time
    System.out.print("│ Recovery Time (s)   │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getRecoveryTime() > 0) {
        System.out.printf(" %10.2f   │", kpi.getRecoveryTime());
      } else {
        System.out.print("     N/A      │");
      }
    }
    System.out.println();

    // Avg Flow Rate
    System.out.print("│ Avg Flow (kg/hr)    │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getAverageFlowRate() > 0) {
        System.out.printf(" %10.2f   │", kpi.getAverageFlowRate());
      } else {
        System.out.print("     N/A      │");
      }
    }
    System.out.println();

    // Simulation Duration
    System.out.print("│ Duration (s)        │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      System.out.printf(" %10.2f   │", kpi.getSimulationDuration());
    }
    System.out.println();

    System.out.println("└─────────────────────┴───────────────────────────────────────────┘\n");
  }

  /**
   * Prints environmental impact metrics comparison.
   */
  private void printEnvironmentalMetrics() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│                  ENVIRONMENTAL IMPACT                           │");
    System.out.println("├─────────────────────┬───────────────────────────────────────────┤");

    // Header
    System.out.print("│ Metric              │");
    for (String scenario : scenarioOrder) {
      System.out.printf(" %-12s │", truncate(scenario, 12));
    }
    System.out.println();
    System.out.println("├─────────────────────┼───────────────────────────────────────────┤");

    // Flare Gas Volume
    System.out.print("│ Flare Gas (Nm³)     │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getFlareGasVolume() > 0) {
        System.out.printf(" %10.2f   │", kpi.getFlareGasVolume());
      } else {
        System.out.print("      0.00    │");
      }
    }
    System.out.println();

    // CO2 Emissions
    System.out.print("│ CO₂ Emissions (kg)  │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getCo2Emissions() > 0) {
        System.out.printf(" %10.2f   │", kpi.getCo2Emissions());
      } else {
        System.out.print("      0.00    │");
      }
    }
    System.out.println();

    // Flaring Duration
    System.out.print("│ Flaring Time (s)    │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getFlaringDuration() > 0) {
        System.out.printf(" %10.2f   │", kpi.getFlaringDuration());
      } else {
        System.out.print("      0.00    │");
      }
    }
    System.out.println();

    // Vented Mass
    System.out.print("│ Vented Mass (kg)    │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      if (kpi.getVentedMass() > 0) {
        System.out.printf(" %10.2f   │", kpi.getVentedMass());
      } else {
        System.out.print("      0.00    │");
      }
    }
    System.out.println();

    System.out.println("└─────────────────────┴───────────────────────────────────────────┘\n");
  }

  /**
   * Prints comparative scores for all scenarios.
   */
  private void printScoreComparison() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│                    PERFORMANCE SCORES                           │");
    System.out.println("│                     (0-100, Higher is Better)                   │");
    System.out.println("├─────────────────────┬───────────────────────────────────────────┤");

    // Header
    System.out.print("│ Score Type          │");
    for (String scenario : scenarioOrder) {
      System.out.printf(" %-12s │", truncate(scenario, 12));
    }
    System.out.println();
    System.out.println("├─────────────────────┼───────────────────────────────────────────┤");

    // Safety Score
    System.out.print("│ Safety Score        │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      double score = kpi.calculateSafetyScore();
      System.out.printf("   %5.1f %s   │", score, getScoreIndicator(score));
    }
    System.out.println();

    // Environmental Score
    System.out.print("│ Environmental Score │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      double score = kpi.calculateEnvironmentalScore();
      System.out.printf("   %5.1f %s   │", score, getScoreIndicator(score));
    }
    System.out.println();

    // Process Score
    System.out.print("│ Process Score       │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      double score = kpi.calculateProcessScore();
      System.out.printf("   %5.1f %s   │", score, getScoreIndicator(score));
    }
    System.out.println();

    System.out.println("├─────────────────────┼───────────────────────────────────────────┤");

    // Overall Score
    System.out.print("│ OVERALL SCORE       │");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      double score = kpi.calculateOverallScore();
      System.out.printf("   %5.1f %s   │", score, getScoreIndicator(score));
    }
    System.out.println();

    System.out.println("└─────────────────────┴───────────────────────────────────────────┘\n");
  }

  /**
   * Prints recommendations based on KPI analysis.
   */
  private void printRecommendations() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│                      RECOMMENDATIONS                            │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");

    // Find best and worst performing scenarios
    String bestSafety = findBestScenario(ScenarioKPI::calculateSafetyScore);
    String bestEnvironmental = findBestScenario(ScenarioKPI::calculateEnvironmentalScore);
    String bestProcess = findBestScenario(ScenarioKPI::calculateProcessScore);
    String bestOverall = findBestScenario(ScenarioKPI::calculateOverallScore);

    System.out.println("  ✓ Best Safety Performance:       " + bestSafety);
    System.out.println("  ✓ Best Environmental Performance: " + bestEnvironmental);
    System.out.println("  ✓ Best Process Performance:       " + bestProcess);
    System.out.println("  ★ Best Overall Performance:       " + bestOverall);
    System.out.println();

    // Identify scenarios needing attention
    System.out.println("  Areas for Improvement:");
    for (String scenario : scenarioOrder) {
      ScenarioKPI kpi = scenarios.get(scenario);
      List<String> issues = new ArrayList<>();

      if (kpi.calculateSafetyScore() < 70.0) {
        issues.add("Safety score below 70");
      }
      if (kpi.isPsvActivated()) {
        issues.add("PSV activated (consider HIPPS tuning)");
      }
      if (kpi.getFlareGasVolume() > 100.0) {
        issues.add("High flare emissions");
      }
      if (kpi.getErrorCount() > 10) {
        issues.add("Many simulation errors");
      }

      if (!issues.isEmpty()) {
        System.out.println("  ⚠ " + scenario + ":");
        for (String issue : issues) {
          System.out.println("      - " + issue);
        }
      }
    }

    // General recommendations
    System.out.println("\n  General Recommendations:");

    // Check if any scenarios had HIPPS trip
    boolean anyHippsTrip = scenarios.values().stream().anyMatch(ScenarioKPI::isHippsTripped);
    if (anyHippsTrip) {
      System.out.println("  1. Review HIPPS trip setpoint - trips detected in scenarios");
    }

    // Check for high flaring
    double totalFlaring =
        scenarios.values().stream().mapToDouble(ScenarioKPI::getFlareGasVolume).sum();
    if (totalFlaring > 200.0) {
      System.out.println("  2. Consider optimizing ESD sequences to minimize flaring");
    }

    // Check for PSV activations
    boolean anyPsvActivation = scenarios.values().stream().anyMatch(ScenarioKPI::isPsvActivated);
    if (anyPsvActivation) {
      System.out.println("  3. PSV activations detected - review upstream protection layers");
    }

    System.out.println();
  }

  /**
   * Finds the best performing scenario based on a scoring function.
   */
  private String findBestScenario(java.util.function.Function<ScenarioKPI, Double> scoreFunction) {
    String best = scenarioOrder.get(0);
    double bestScore = scoreFunction.apply(scenarios.get(best));

    for (String scenario : scenarioOrder) {
      double score = scoreFunction.apply(scenarios.get(scenario));
      if (score > bestScore) {
        bestScore = score;
        best = scenario;
      }
    }

    return best + String.format(" (%.1f)", bestScore);
  }

  /**
   * Gets a visual indicator for a score.
   */
  private String getScoreIndicator(double score) {
    if (score >= 90.0)
      return "★";
    else if (score >= 75.0)
      return "✓";
    else if (score >= 60.0)
      return "○";
    else
      return "⚠";
  }

  /**
   * Truncates a string to specified length.
   */
  private String truncate(String str, int length) {
    if (str.length() <= length) {
      return str;
    }
    return str.substring(0, length - 2) + "..";
  }

  /**
   * Gets the number of scenarios in the dashboard.
   */
  public int getScenarioCount() {
    return scenarios.size();
  }

  /**
   * Clears all scenarios from the dashboard.
   */
  public void clear() {
    scenarios.clear();
    scenarioOrder.clear();
  }
}
