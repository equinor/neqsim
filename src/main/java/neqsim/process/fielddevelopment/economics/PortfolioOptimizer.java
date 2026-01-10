package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Multi-field portfolio optimizer for capital-constrained investment decisions.
 *
 * <p>
 * Optimizes project selection across multiple fields subject to annual capital budget constraints.
 * Uses value-based ranking with optional risk weighting to maximize portfolio NPV while respecting
 * budget limits for each year.
 * </p>
 *
 * <h2>Optimization Approaches</h2>
 * <ul>
 * <li><b>Greedy NPV/CAPEX</b>: Rank by NPV/investment ratio, select until budget exhausted</li>
 * <li><b>Risk-Weighted</b>: Adjust ranking by probability of success</li>
 * <li><b>Balanced</b>: Ensure mix of project types (development, IOR, exploration)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * PortfolioOptimizer optimizer = new PortfolioOptimizer();
 * 
 * // Add projects
 * optimizer.addProject("Field A", 500.0, 850.0, ProjectType.DEVELOPMENT, 0.85);
 * optimizer.addProject("Field B", 300.0, 420.0, ProjectType.DEVELOPMENT, 0.90);
 * optimizer.addProject("Field C IOR", 100.0, 180.0, ProjectType.IOR, 0.95);
 * optimizer.addProject("Exploration X", 150.0, 600.0, ProjectType.EXPLORATION, 0.30);
 * 
 * // Set annual budget constraints
 * optimizer.setAnnualBudget(2025, 400.0);
 * optimizer.setAnnualBudget(2026, 450.0);
 * optimizer.setAnnualBudget(2027, 350.0);
 * 
 * // Optimize
 * PortfolioResult result = optimizer.optimize(OptimizationStrategy.GREEDY_NPV_RATIO);
 * 
 * // Results
 * System.out.println("Selected projects: " + result.getSelectedProjects());
 * System.out.println("Portfolio NPV: " + result.getTotalNpv() + " MUSD");
 * System.out.println("Capital efficiency: " + result.getCapitalEfficiency());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PortfolioOptimizer implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(PortfolioOptimizer.class);

  /**
   * Project type classification.
   */
  public enum ProjectType {
    /** New field development. */
    DEVELOPMENT,
    /** Improved oil recovery / infill. */
    IOR,
    /** Exploration / appraisal. */
    EXPLORATION,
    /** Tie-back to existing infrastructure. */
    TIEBACK,
    /** Infrastructure sharing. */
    INFRASTRUCTURE
  }

  /**
   * Optimization strategy.
   */
  public enum OptimizationStrategy {
    /** Greedy selection by NPV/CAPEX ratio. */
    GREEDY_NPV_RATIO,
    /** Greedy selection by absolute NPV. */
    GREEDY_ABSOLUTE_NPV,
    /** Risk-weighted NPV ranking. */
    RISK_WEIGHTED,
    /** Balanced portfolio across project types. */
    BALANCED,
    /** Maximize expected monetary value. */
    EMV_MAXIMIZATION
  }

  /**
   * Single project in the portfolio.
   */
  public static class Project implements Serializable {
    private static final long serialVersionUID = 1001L;

    private String name;
    private double capexMusd;
    private double npvMusd;
    private ProjectType type;
    private double probabilityOfSuccess;
    private int startYear;
    private Map<Integer, Double> capexProfile;
    private boolean mandatory;
    private List<String> dependencies;

    /**
     * Creates a new project.
     *
     * @param name project name
     * @param capexMusd total CAPEX in MUSD
     * @param npvMusd NPV in MUSD
     * @param type project type
     * @param probabilityOfSuccess probability (0-1)
     */
    public Project(String name, double capexMusd, double npvMusd, ProjectType type,
        double probabilityOfSuccess) {
      this.name = name;
      this.capexMusd = capexMusd;
      this.npvMusd = npvMusd;
      this.type = type;
      this.probabilityOfSuccess = probabilityOfSuccess;
      this.startYear = 2025;
      this.capexProfile = new HashMap<Integer, Double>();
      this.mandatory = false;
      this.dependencies = new ArrayList<String>();
    }

    /** Get project name. */
    public String getName() {
      return name;
    }

    /** Get total CAPEX. */
    public double getCapexMusd() {
      return capexMusd;
    }

    /** Get NPV. */
    public double getNpvMusd() {
      return npvMusd;
    }

    /** Get project type. */
    public ProjectType getType() {
      return type;
    }

    /** Get probability of success. */
    public double getProbabilityOfSuccess() {
      return probabilityOfSuccess;
    }

    /** Get start year. */
    public int getStartYear() {
      return startYear;
    }

    /** Set start year. */
    public void setStartYear(int year) {
      this.startYear = year;
    }

    /** Set CAPEX profile by year. */
    public void setCapexProfile(Map<Integer, Double> profile) {
      this.capexProfile = profile;
    }

    /** Get CAPEX profile. */
    public Map<Integer, Double> getCapexProfile() {
      return capexProfile;
    }

    /** Get CAPEX for specific year. */
    public double getCapexForYear(int year) {
      if (capexProfile.isEmpty()) {
        // Default: spread evenly over 2 years starting from startYear
        if (year == startYear || year == startYear + 1) {
          return capexMusd / 2.0;
        }
        return 0.0;
      }
      Double value = capexProfile.get(Integer.valueOf(year));
      return value != null ? value.doubleValue() : 0.0;
    }

    /** Check if mandatory. */
    public boolean isMandatory() {
      return mandatory;
    }

    /** Set mandatory flag. */
    public void setMandatory(boolean mandatory) {
      this.mandatory = mandatory;
    }

    /** Get dependencies. */
    public List<String> getDependencies() {
      return dependencies;
    }

    /** Add dependency. */
    public void addDependency(String projectName) {
      dependencies.add(projectName);
    }

    /** Calculate NPV/CAPEX ratio. */
    public double getNpvCapexRatio() {
      return capexMusd > 0 ? npvMusd / capexMusd : 0.0;
    }

    /** Calculate expected monetary value. */
    public double getEmv() {
      return npvMusd * probabilityOfSuccess;
    }

    /** Calculate risk-weighted NPV ratio. */
    public double getRiskWeightedRatio() {
      return getNpvCapexRatio() * probabilityOfSuccess;
    }
  }

  /**
   * Portfolio optimization result.
   */
  public static class PortfolioResult implements Serializable {
    private static final long serialVersionUID = 1002L;

    private List<Project> selectedProjects;
    private List<Project> deferredProjects;
    private double totalNpv;
    private double totalCapex;
    private double totalEmv;
    private Map<Integer, Double> annualCapexUsed;
    private Map<Integer, Double> annualBudgetRemaining;
    private OptimizationStrategy strategy;

    /**
     * Creates a new result.
     */
    public PortfolioResult() {
      this.selectedProjects = new ArrayList<Project>();
      this.deferredProjects = new ArrayList<Project>();
      this.annualCapexUsed = new HashMap<Integer, Double>();
      this.annualBudgetRemaining = new HashMap<Integer, Double>();
    }

    /** Get selected projects. */
    public List<Project> getSelectedProjects() {
      return selectedProjects;
    }

    /** Get deferred projects. */
    public List<Project> getDeferredProjects() {
      return deferredProjects;
    }

    /** Get total NPV. */
    public double getTotalNpv() {
      return totalNpv;
    }

    /** Set total NPV. */
    public void setTotalNpv(double npv) {
      this.totalNpv = npv;
    }

    /** Get total CAPEX. */
    public double getTotalCapex() {
      return totalCapex;
    }

    /** Set total CAPEX. */
    public void setTotalCapex(double capex) {
      this.totalCapex = capex;
    }

    /** Get total EMV. */
    public double getTotalEmv() {
      return totalEmv;
    }

    /** Set total EMV. */
    public void setTotalEmv(double emv) {
      this.totalEmv = emv;
    }

    /** Get capital efficiency (NPV/CAPEX). */
    public double getCapitalEfficiency() {
      return totalCapex > 0 ? totalNpv / totalCapex : 0.0;
    }

    /** Get annual CAPEX used. */
    public Map<Integer, Double> getAnnualCapexUsed() {
      return annualCapexUsed;
    }

    /** Get annual budget remaining. */
    public Map<Integer, Double> getAnnualBudgetRemaining() {
      return annualBudgetRemaining;
    }

    /** Get strategy used. */
    public OptimizationStrategy getStrategy() {
      return strategy;
    }

    /** Set strategy. */
    public void setStrategy(OptimizationStrategy strategy) {
      this.strategy = strategy;
    }

    /** Get project count. */
    public int getProjectCount() {
      return selectedProjects.size();
    }

    /**
     * Generate summary report.
     *
     * @return formatted report string
     */
    public String generateReport() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== PORTFOLIO OPTIMIZATION RESULT ===\n\n");
      sb.append("Strategy: ").append(strategy).append("\n\n");

      sb.append("SELECTED PROJECTS:\n");
      sb.append(String.format("%-20s %10s %10s %10s %8s%n", "Project", "CAPEX", "NPV", "NPV/CAPEX",
          "PoS"));
      sb.append(
          "--------------------------------------------------------------------------------\n");

      for (Project p : selectedProjects) {
        sb.append(
            String.format("%-20s %10.1f %10.1f %10.2f %8.0f%%%n", p.getName(), p.getCapexMusd(),
                p.getNpvMusd(), p.getNpvCapexRatio(), p.getProbabilityOfSuccess() * 100));
      }

      sb.append(
          "--------------------------------------------------------------------------------\n");
      sb.append(String.format("%-20s %10.1f %10.1f %10.2f%n", "TOTAL", totalCapex, totalNpv,
          getCapitalEfficiency()));

      if (!deferredProjects.isEmpty()) {
        sb.append("\nDEFERRED PROJECTS:\n");
        for (Project p : deferredProjects) {
          sb.append(String.format("  - %s (CAPEX: %.1f, NPV: %.1f)%n", p.getName(),
              p.getCapexMusd(), p.getNpvMusd()));
        }
      }

      sb.append("\nANNUAL BUDGET UTILIZATION:\n");
      for (Map.Entry<Integer, Double> entry : annualCapexUsed.entrySet()) {
        Double remaining = annualBudgetRemaining.get(entry.getKey());
        double budget =
            entry.getValue().doubleValue() + (remaining != null ? remaining.doubleValue() : 0.0);
        double utilization = budget > 0 ? entry.getValue().doubleValue() / budget * 100 : 0;
        sb.append(String.format("  %d: %.1f / %.1f MUSD (%.0f%% utilized)%n", entry.getKey(),
            entry.getValue(), budget, utilization));
      }

      return sb.toString();
    }
  }

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  /** List of candidate projects. */
  private List<Project> projects;

  /** Annual budget constraints (year to budget in MUSD). */
  private Map<Integer, Double> annualBudgets;

  /** Total budget constraint (across all years). */
  private double totalBudgetMusd;

  /** Minimum allocation per project type. */
  private Map<ProjectType, Double> minAllocationByType;

  /** Maximum allocation per project type. */
  private Map<ProjectType, Double> maxAllocationByType;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new portfolio optimizer.
   */
  public PortfolioOptimizer() {
    this.projects = new ArrayList<Project>();
    this.annualBudgets = new HashMap<Integer, Double>();
    this.totalBudgetMusd = Double.MAX_VALUE;
    this.minAllocationByType = new HashMap<ProjectType, Double>();
    this.maxAllocationByType = new HashMap<ProjectType, Double>();
  }

  // ============================================================================
  // PROJECT MANAGEMENT
  // ============================================================================

  /**
   * Add a project to the portfolio candidates.
   *
   * @param name project name
   * @param capexMusd total CAPEX in MUSD
   * @param npvMusd NPV in MUSD
   * @param type project type
   * @param probabilityOfSuccess probability of success (0-1)
   * @return the created project for further configuration
   */
  public Project addProject(String name, double capexMusd, double npvMusd, ProjectType type,
      double probabilityOfSuccess) {
    Project p = new Project(name, capexMusd, npvMusd, type, probabilityOfSuccess);
    projects.add(p);
    return p;
  }

  /**
   * Add a project with detailed CAPEX profile.
   *
   * @param name project name
   * @param npvMusd NPV in MUSD
   * @param type project type
   * @param probabilityOfSuccess probability of success
   * @param capexProfile yearly CAPEX profile
   * @return the created project
   */
  public Project addProject(String name, double npvMusd, ProjectType type,
      double probabilityOfSuccess, Map<Integer, Double> capexProfile) {
    double totalCapex = 0.0;
    for (Double v : capexProfile.values()) {
      totalCapex += v.doubleValue();
    }
    Project p = new Project(name, totalCapex, npvMusd, type, probabilityOfSuccess);
    p.setCapexProfile(capexProfile);
    projects.add(p);
    return p;
  }

  /**
   * Add a project directly.
   *
   * @param project the project to add
   */
  public void addProject(Project project) {
    projects.add(project);
  }

  /**
   * Get all candidate projects.
   *
   * @return list of projects
   */
  public List<Project> getProjects() {
    return projects;
  }

  /**
   * Clear all projects.
   */
  public void clearProjects() {
    projects.clear();
  }

  // ============================================================================
  // BUDGET CONSTRAINTS
  // ============================================================================

  /**
   * Set annual budget constraint.
   *
   * @param year the year
   * @param budgetMusd budget in MUSD
   */
  public void setAnnualBudget(int year, double budgetMusd) {
    annualBudgets.put(Integer.valueOf(year), Double.valueOf(budgetMusd));
  }

  /**
   * Set total budget constraint across all years.
   *
   * @param totalMusd total budget in MUSD
   */
  public void setTotalBudget(double totalMusd) {
    this.totalBudgetMusd = totalMusd;
  }

  /**
   * Set minimum allocation for a project type.
   *
   * @param type project type
   * @param minMusd minimum CAPEX allocation
   */
  public void setMinAllocation(ProjectType type, double minMusd) {
    minAllocationByType.put(type, Double.valueOf(minMusd));
  }

  /**
   * Set maximum allocation for a project type.
   *
   * @param type project type
   * @param maxMusd maximum CAPEX allocation
   */
  public void setMaxAllocation(ProjectType type, double maxMusd) {
    maxAllocationByType.put(type, Double.valueOf(maxMusd));
  }

  // ============================================================================
  // OPTIMIZATION
  // ============================================================================

  /**
   * Optimize the portfolio using the specified strategy.
   *
   * @param strategy optimization strategy
   * @return portfolio result
   */
  public PortfolioResult optimize(OptimizationStrategy strategy) {
    logger.info("Starting portfolio optimization with strategy: {}", strategy);

    switch (strategy) {
      case GREEDY_NPV_RATIO:
        return optimizeGreedyNpvRatio();
      case GREEDY_ABSOLUTE_NPV:
        return optimizeGreedyAbsoluteNpv();
      case RISK_WEIGHTED:
        return optimizeRiskWeighted();
      case EMV_MAXIMIZATION:
        return optimizeEmv();
      case BALANCED:
        return optimizeBalanced();
      default:
        return optimizeGreedyNpvRatio();
    }
  }

  /**
   * Optimize using greedy NPV/CAPEX ratio selection.
   */
  private PortfolioResult optimizeGreedyNpvRatio() {
    List<Project> sorted = new ArrayList<Project>(projects);
    Collections.sort(sorted, new Comparator<Project>() {
      @Override
      public int compare(Project a, Project b) {
        return Double.compare(b.getNpvCapexRatio(), a.getNpvCapexRatio());
      }
    });
    return selectProjects(sorted, OptimizationStrategy.GREEDY_NPV_RATIO);
  }

  /**
   * Optimize using greedy absolute NPV selection.
   */
  private PortfolioResult optimizeGreedyAbsoluteNpv() {
    List<Project> sorted = new ArrayList<Project>(projects);
    Collections.sort(sorted, new Comparator<Project>() {
      @Override
      public int compare(Project a, Project b) {
        return Double.compare(b.getNpvMusd(), a.getNpvMusd());
      }
    });
    return selectProjects(sorted, OptimizationStrategy.GREEDY_ABSOLUTE_NPV);
  }

  /**
   * Optimize using risk-weighted NPV ratio.
   */
  private PortfolioResult optimizeRiskWeighted() {
    List<Project> sorted = new ArrayList<Project>(projects);
    Collections.sort(sorted, new Comparator<Project>() {
      @Override
      public int compare(Project a, Project b) {
        return Double.compare(b.getRiskWeightedRatio(), a.getRiskWeightedRatio());
      }
    });
    return selectProjects(sorted, OptimizationStrategy.RISK_WEIGHTED);
  }

  /**
   * Optimize using expected monetary value.
   */
  private PortfolioResult optimizeEmv() {
    List<Project> sorted = new ArrayList<Project>(projects);
    Collections.sort(sorted, new Comparator<Project>() {
      @Override
      public int compare(Project a, Project b) {
        return Double.compare(b.getEmv(), a.getEmv());
      }
    });
    return selectProjects(sorted, OptimizationStrategy.EMV_MAXIMIZATION);
  }

  /**
   * Optimize with balanced allocation across project types.
   */
  private PortfolioResult optimizeBalanced() {
    // Group projects by type
    Map<ProjectType, List<Project>> byType = new HashMap<ProjectType, List<Project>>();
    for (Project p : projects) {
      List<Project> list = byType.get(p.getType());
      if (list == null) {
        list = new ArrayList<Project>();
        byType.put(p.getType(), list);
      }
      list.add(p);
    }

    // Sort each group by NPV/CAPEX ratio
    for (List<Project> list : byType.values()) {
      Collections.sort(list, new Comparator<Project>() {
        @Override
        public int compare(Project a, Project b) {
          return Double.compare(b.getNpvCapexRatio(), a.getNpvCapexRatio());
        }
      });
    }

    // Interleave selection from each type
    List<Project> balanced = new ArrayList<Project>();
    boolean added = true;
    int index = 0;
    while (added) {
      added = false;
      for (ProjectType type : ProjectType.values()) {
        List<Project> list = byType.get(type);
        if (list != null && index < list.size()) {
          balanced.add(list.get(index));
          added = true;
        }
      }
      index++;
    }

    return selectProjects(balanced, OptimizationStrategy.BALANCED);
  }

  /**
   * Select projects respecting budget constraints.
   */
  private PortfolioResult selectProjects(List<Project> ranked, OptimizationStrategy strategy) {
    PortfolioResult result = new PortfolioResult();
    result.setStrategy(strategy);

    // Initialize budget tracking
    Map<Integer, Double> remainingBudget = new HashMap<Integer, Double>(annualBudgets);
    double remainingTotal = totalBudgetMusd;

    // First add mandatory projects
    for (Project p : ranked) {
      if (p.isMandatory()) {
        if (canAfford(p, remainingBudget, remainingTotal)) {
          result.getSelectedProjects().add(p);
          remainingTotal -= p.getCapexMusd();
          deductCapex(p, remainingBudget);
        }
      }
    }

    // Then add by ranking
    for (Project p : ranked) {
      if (p.isMandatory()) {
        continue; // Already added
      }
      if (p.getNpvMusd() <= 0) {
        result.getDeferredProjects().add(p);
        continue; // Skip negative NPV projects
      }

      // Check dependencies
      if (!dependenciesSatisfied(p, result.getSelectedProjects())) {
        result.getDeferredProjects().add(p);
        continue;
      }

      if (canAfford(p, remainingBudget, remainingTotal)) {
        result.getSelectedProjects().add(p);
        remainingTotal -= p.getCapexMusd();
        deductCapex(p, remainingBudget);
      } else {
        result.getDeferredProjects().add(p);
      }
    }

    // Calculate totals
    double totalNpv = 0.0;
    double totalCapex = 0.0;
    double totalEmv = 0.0;
    for (Project p : result.getSelectedProjects()) {
      totalNpv += p.getNpvMusd();
      totalCapex += p.getCapexMusd();
      totalEmv += p.getEmv();
    }
    result.setTotalNpv(totalNpv);
    result.setTotalCapex(totalCapex);
    result.setTotalEmv(totalEmv);

    // Calculate budget utilization
    for (Map.Entry<Integer, Double> entry : annualBudgets.entrySet()) {
      double budget = entry.getValue().doubleValue();
      Double remaining = remainingBudget.get(entry.getKey());
      double used = budget - (remaining != null ? remaining.doubleValue() : 0.0);
      result.getAnnualCapexUsed().put(entry.getKey(), Double.valueOf(used));
      result.getAnnualBudgetRemaining().put(entry.getKey(), remaining);
    }

    logger.info("Portfolio optimization complete: {} projects selected, NPV = {} MUSD",
        result.getProjectCount(), result.getTotalNpv());

    return result;
  }

  /**
   * Check if a project can be afforded within budget constraints.
   */
  private boolean canAfford(Project p, Map<Integer, Double> remainingBudget,
      double remainingTotal) {
    if (p.getCapexMusd() > remainingTotal) {
      return false;
    }

    // Check annual constraints
    for (Map.Entry<Integer, Double> entry : annualBudgets.entrySet()) {
      int year = entry.getKey().intValue();
      double projectCapex = p.getCapexForYear(year);
      Double remaining = remainingBudget.get(entry.getKey());
      if (remaining != null && projectCapex > remaining.doubleValue()) {
        return false;
      }
    }

    return true;
  }

  /**
   * Deduct project CAPEX from remaining budgets.
   */
  private void deductCapex(Project p, Map<Integer, Double> remainingBudget) {
    for (Map.Entry<Integer, Double> entry : annualBudgets.entrySet()) {
      int year = entry.getKey().intValue();
      double projectCapex = p.getCapexForYear(year);
      if (projectCapex > 0) {
        Double remaining = remainingBudget.get(entry.getKey());
        if (remaining != null) {
          remainingBudget.put(entry.getKey(),
              Double.valueOf(remaining.doubleValue() - projectCapex));
        }
      }
    }
  }

  /**
   * Check if all dependencies are satisfied.
   */
  private boolean dependenciesSatisfied(Project p, List<Project> selected) {
    for (String dep : p.getDependencies()) {
      boolean found = false;
      for (Project s : selected) {
        if (s.getName().equals(dep)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  // ============================================================================
  // ANALYSIS METHODS
  // ============================================================================

  /**
   * Compare multiple optimization strategies.
   *
   * @return map of strategy to result
   */
  public Map<OptimizationStrategy, PortfolioResult> compareStrategies() {
    Map<OptimizationStrategy, PortfolioResult> results =
        new HashMap<OptimizationStrategy, PortfolioResult>();
    for (OptimizationStrategy strategy : OptimizationStrategy.values()) {
      results.put(strategy, optimize(strategy));
    }
    return results;
  }

  /**
   * Generate comparison report for all strategies.
   *
   * @return formatted comparison report
   */
  public String generateComparisonReport() {
    Map<OptimizationStrategy, PortfolioResult> results = compareStrategies();

    StringBuilder sb = new StringBuilder();
    sb.append("=== PORTFOLIO STRATEGY COMPARISON ===\n\n");
    sb.append(String.format("%-25s %10s %10s %10s %8s%n", "Strategy", "Projects", "CAPEX", "NPV",
        "Efficiency"));
    sb.append("------------------------------------------------------------------------\n");

    for (Map.Entry<OptimizationStrategy, PortfolioResult> entry : results.entrySet()) {
      PortfolioResult r = entry.getValue();
      sb.append(String.format("%-25s %10d %10.1f %10.1f %8.2f%n", entry.getKey(),
          r.getProjectCount(), r.getTotalCapex(), r.getTotalNpv(), r.getCapitalEfficiency()));
    }

    return sb.toString();
  }
}
