package neqsim.util.agentic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Defines and evaluates standardized engineering benchmark problems for agent performance
 * measurement.
 *
 * <p>
 * Inspired by the Simona dataset used by Tian et al. (2026) for evaluating multi-agent chemical
 * process design workflows, this class provides a curated set of engineering problems with known
 * reference solutions. Each benchmark problem specifies inputs, expected outputs with tolerances,
 * and pass/fail criteria. Agent systems can run the full suite to measure convergence rate,
 * accuracy, and completeness across diverse engineering tasks.
 * </p>
 *
 * <h2>Problem Categories:</h2>
 * <ul>
 * <li><b>THERMO</b> — Pure component and mixture thermodynamic properties</li>
 * <li><b>FLASH</b> — Phase equilibrium calculations (TP, PH, PS flash)</li>
 * <li><b>PROCESS</b> — Process equipment and flowsheet simulations</li>
 * <li><b>PIPELINE</b> — Multiphase pipe flow and pressure drop</li>
 * <li><b>ECONOMICS</b> — Field development NPV and cost estimation</li>
 * <li><b>SAFETY</b> — Depressurization, relief valve sizing, safety envelopes</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * {@code
 * AgentBenchmarkSuite suite = AgentBenchmarkSuite.createStandardSuite();
 * suite.addResult("methane_density_300K_50bar", 34.05);
 *
 * BenchmarkReport report = suite.evaluate();
 * System.out.println("Pass rate: " + report.getPassRate());
 * System.out.println("Failed: " + report.getFailedProblems());
 * String json = report.toJson();
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AgentBenchmarkSuite implements Serializable {
  private static final long serialVersionUID = 1001L;

  /**
   * Category of engineering benchmark problem.
   */
  public enum ProblemCategory {
    /** Pure component and mixture thermodynamic properties. */
    THERMO,
    /** Phase equilibrium calculations (TP, PH, PS flash). */
    FLASH,
    /** Process equipment and flowsheet simulations. */
    PROCESS,
    /** Multiphase pipe flow and pressure drop. */
    PIPELINE,
    /** Field development NPV and cost estimation. */
    ECONOMICS,
    /** Depressurization, relief valve sizing, safety envelopes. */
    SAFETY
  }

  /**
   * Difficulty level of the benchmark problem.
   */
  public enum Difficulty {
    /** Single-step calculation, one component or simple mixture. */
    BASIC,
    /** Multi-step calculation or moderate complexity. */
    INTERMEDIATE,
    /** Complex flowsheet, multi-phase, or multi-discipline integration. */
    ADVANCED
  }

  private final String suiteName;
  private final List<BenchmarkProblem> problems;
  private final Map<String, Double> submittedResults;
  private final Map<String, Boolean> submittedConvergence;

  /**
   * Creates a new benchmark suite with the given name.
   *
   * @param suiteName descriptive name for the benchmark suite
   */
  public AgentBenchmarkSuite(String suiteName) {
    this.suiteName = suiteName;
    this.problems = new ArrayList<>();
    this.submittedResults = new LinkedHashMap<>();
    this.submittedConvergence = new LinkedHashMap<>();
  }

  /**
   * Adds a benchmark problem to the suite.
   *
   * @param problem the benchmark problem to add
   */
  public void addProblem(BenchmarkProblem problem) {
    problems.add(problem);
  }

  /**
   * Submits an agent result for a specific problem.
   *
   * @param problemId the unique identifier of the benchmark problem
   * @param value the computed result value
   */
  public void addResult(String problemId, double value) {
    submittedResults.put(problemId, value);
  }

  /**
   * Records whether a simulation converged for a specific problem.
   *
   * @param problemId the unique identifier of the benchmark problem
   * @param converged true if the simulation converged, false otherwise
   */
  public void addConvergenceResult(String problemId, boolean converged) {
    submittedConvergence.put(problemId, converged);
  }

  /**
   * Evaluates all submitted results against the benchmark reference data.
   *
   * @return a BenchmarkReport with pass/fail verdicts and aggregate metrics
   */
  public BenchmarkReport evaluate() {
    List<ProblemResult> results = new ArrayList<>();
    int passed = 0;
    int failed = 0;
    int notAttempted = 0;

    for (BenchmarkProblem problem : problems) {
      String id = problem.getId();
      if (!submittedResults.containsKey(id) && !submittedConvergence.containsKey(id)) {
        results.add(
            new ProblemResult(problem, Double.NaN, false, "NOT_ATTEMPTED", "No result submitted"));
        notAttempted++;
        continue;
      }

      Boolean converged = submittedConvergence.get(id);
      if (converged != null && !converged) {
        results.add(
            new ProblemResult(problem, Double.NaN, false, "FAILED", "Simulation did not converge"));
        failed++;
        continue;
      }

      if (submittedResults.containsKey(id)) {
        double actual = submittedResults.get(id);
        double expected = problem.getExpectedValue();
        double tolerance = problem.getTolerancePct();
        double deviationPct = expected != 0.0 ? Math.abs((actual - expected) / expected) * 100.0
            : (actual == 0.0 ? 0.0 : 100.0);

        boolean pass = deviationPct <= tolerance;
        String verdict = pass ? "PASS" : "FAIL";
        String detail = String.format("Expected=%.6g, Actual=%.6g, Deviation=%.2f%%, Tol=%.1f%%",
            expected, actual, deviationPct, tolerance);

        results.add(new ProblemResult(problem, actual, pass, verdict, detail));
        if (pass) {
          passed++;
        } else {
          failed++;
        }
      } else {
        // Had convergence=true but no numerical result submitted
        results.add(new ProblemResult(problem, Double.NaN, true, "PASS_CONVERGENCE",
            "Converged, no value checked"));
        passed++;
      }
    }

    return new BenchmarkReport(suiteName, results, passed, failed, notAttempted);
  }

  /**
   * Returns the list of benchmark problems in this suite.
   *
   * @return unmodifiable list of benchmark problems
   */
  public List<BenchmarkProblem> getProblems() {
    return Collections.unmodifiableList(problems);
  }

  /**
   * Returns the name of this benchmark suite.
   *
   * @return the suite name
   */
  public String getSuiteName() {
    return suiteName;
  }

  /**
   * Creates a standard benchmark suite with representative problems across all categories.
   *
   * <p>
   * Reference data sources: NIST Chemistry WebBook, published experimental data, validated
   * simulation results.
   * </p>
   *
   * @return a pre-populated benchmark suite
   */
  public static AgentBenchmarkSuite createStandardSuite() {
    AgentBenchmarkSuite suite = new AgentBenchmarkSuite("NeqSim Standard Benchmark v1.0");

    // THERMO category — pure component properties
    suite.addProblem(new BenchmarkProblem("methane_density_300K_50bar", ProblemCategory.THERMO,
        Difficulty.BASIC, "Methane density at 300 K and 50 bar (SRK EOS)", "kg/m3", 33.5, 2.0,
        "NIST Chemistry WebBook"));

    suite.addProblem(new BenchmarkProblem("water_boiling_1atm", ProblemCategory.THERMO,
        Difficulty.BASIC, "Water normal boiling point at 1.01325 bar", "K", 373.15, 0.5,
        "NIST Chemistry WebBook"));

    suite.addProblem(new BenchmarkProblem("co2_density_310K_100bar", ProblemCategory.THERMO,
        Difficulty.BASIC, "CO2 density at 310 K and 100 bar (SRK EOS)", "kg/m3", 628.6, 5.0,
        "NIST Chemistry WebBook"));

    // FLASH category — phase equilibrium
    suite.addProblem(new BenchmarkProblem("methane_ethane_flash_phase_count", ProblemCategory.FLASH,
        Difficulty.BASIC, "Number of phases for 90/10 methane/ethane at 200 K, 30 bar", "phases",
        1.0, 0.1, "Expected single vapor phase"));

    suite.addProblem(new BenchmarkProblem("natural_gas_dewpoint", ProblemCategory.FLASH,
        Difficulty.INTERMEDIATE, "Cricondentherm for 85/10/5 methane/ethane/propane mixture (SRK)",
        "K", 270.0, 3.0, "SRK EOS phase envelope calculation"));

    // PROCESS category — equipment simulation
    suite.addProblem(
        new BenchmarkProblem("separator_mass_balance", ProblemCategory.PROCESS, Difficulty.BASIC,
            "Mass balance closure for two-phase separator (feed = gas_out + liquid_out)", "%", 0.0,
            0.1, "Conservation of mass"));

    suite.addProblem(new BenchmarkProblem("compressor_polytropic_power", ProblemCategory.PROCESS,
        Difficulty.INTERMEDIATE,
        "Polytropic compressor power for methane from 30 to 100 bar at 100 kg/hr", "kW", 22.0, 10.0,
        "Textbook polytropic compression"));

    // PIPELINE category — pipe flow
    suite.addProblem(new BenchmarkProblem("gas_pipeline_pressure_drop", ProblemCategory.PIPELINE,
        Difficulty.INTERMEDIATE,
        "Pressure drop for 50 km, 20-inch gas pipeline at 100 bar inlet (Beggs-Brill)", "bar", 8.0,
        15.0, "Beggs and Brill correlation"));

    // ECONOMICS category
    suite.addProblem(
        new BenchmarkProblem("simple_npv_10yr", ProblemCategory.ECONOMICS, Difficulty.BASIC,
            "NPV of constant 100 MNOK/yr cash flow over 10 years at 10% discount rate", "MNOK",
            614.5, 1.0, "Textbook DCF calculation"));

    // SAFETY category
    suite.addProblem(new BenchmarkProblem("vessel_blowdown_50pct_time", ProblemCategory.SAFETY,
        Difficulty.INTERMEDIATE,
        "Time to reach 50% of initial pressure during vessel blowdown (100 bar, methane)",
        "seconds", 30.0, 25.0, "Approximate depressurization model"));

    return suite;
  }

  /**
   * Serializes the benchmark suite definition to JSON.
   *
   * @return JSON string representation of the suite
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("suiteName", suiteName);
    map.put("problemCount", problems.size());
    List<Map<String, Object>> problemList = new ArrayList<>();
    for (BenchmarkProblem p : problems) {
      problemList.add(p.toMap());
    }
    map.put("problems", problemList);
    return gson.toJson(map);
  }

  /**
   * A single benchmark problem with expected reference solution.
   */
  public static class BenchmarkProblem implements Serializable {
    private static final long serialVersionUID = 1002L;

    private final String id;
    private final ProblemCategory category;
    private final Difficulty difficulty;
    private final String description;
    private final String unit;
    private final double expectedValue;
    private final double tolerancePct;
    private final String referenceSource;

    /**
     * Creates a new benchmark problem.
     *
     * @param id unique identifier for the problem
     * @param category engineering discipline category
     * @param difficulty difficulty level
     * @param description human-readable description of what to compute
     * @param unit unit of the expected result
     * @param expectedValue reference value from literature or validated simulation
     * @param tolerancePct acceptable deviation in percent
     * @param referenceSource source of the reference value
     */
    public BenchmarkProblem(String id, ProblemCategory category, Difficulty difficulty,
        String description, String unit, double expectedValue, double tolerancePct,
        String referenceSource) {
      this.id = id;
      this.category = category;
      this.difficulty = difficulty;
      this.description = description;
      this.unit = unit;
      this.expectedValue = expectedValue;
      this.tolerancePct = tolerancePct;
      this.referenceSource = referenceSource;
    }

    /**
     * Returns the unique identifier.
     *
     * @return the problem id
     */
    public String getId() {
      return id;
    }

    /**
     * Returns the problem category.
     *
     * @return the category enum value
     */
    public ProblemCategory getCategory() {
      return category;
    }

    /**
     * Returns the difficulty level.
     *
     * @return the difficulty enum value
     */
    public Difficulty getDifficulty() {
      return difficulty;
    }

    /**
     * Returns the human-readable description.
     *
     * @return the problem description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Returns the unit of measurement for the expected result.
     *
     * @return the unit string
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Returns the expected reference value.
     *
     * @return the expected numerical value
     */
    public double getExpectedValue() {
      return expectedValue;
    }

    /**
     * Returns the acceptable tolerance in percent.
     *
     * @return the tolerance percentage
     */
    public double getTolerancePct() {
      return tolerancePct;
    }

    /**
     * Returns the source of the reference data.
     *
     * @return the reference source description
     */
    public String getReferenceSource() {
      return referenceSource;
    }

    /**
     * Converts the problem to a map for JSON serialization.
     *
     * @return map representation of the problem
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", id);
      map.put("category", category.name());
      map.put("difficulty", difficulty.name());
      map.put("description", description);
      map.put("unit", unit);
      map.put("expectedValue", expectedValue);
      map.put("tolerancePct", tolerancePct);
      map.put("referenceSource", referenceSource);
      return map;
    }
  }

  /**
   * Result of evaluating a single benchmark problem.
   */
  public static class ProblemResult implements Serializable {
    private static final long serialVersionUID = 1003L;

    private final BenchmarkProblem problem;
    private final double actualValue;
    private final boolean passed;
    private final String verdict;
    private final String detail;

    /**
     * Creates a new problem result.
     *
     * @param problem the benchmark problem
     * @param actualValue the agent-submitted value
     * @param passed whether the result passed the tolerance check
     * @param verdict short verdict string (PASS, FAIL, NOT_ATTEMPTED, etc.)
     * @param detail detailed explanation of the comparison
     */
    public ProblemResult(BenchmarkProblem problem, double actualValue, boolean passed,
        String verdict, String detail) {
      this.problem = problem;
      this.actualValue = actualValue;
      this.passed = passed;
      this.verdict = verdict;
      this.detail = detail;
    }

    /**
     * Returns the benchmark problem.
     *
     * @return the problem definition
     */
    public BenchmarkProblem getProblem() {
      return problem;
    }

    /**
     * Returns the submitted value.
     *
     * @return the actual computed value
     */
    public double getActualValue() {
      return actualValue;
    }

    /**
     * Returns whether the result passed.
     *
     * @return true if within tolerance
     */
    public boolean isPassed() {
      return passed;
    }

    /**
     * Returns the verdict string.
     *
     * @return the verdict (PASS, FAIL, NOT_ATTEMPTED, etc.)
     */
    public String getVerdict() {
      return verdict;
    }

    /**
     * Returns the detail string.
     *
     * @return detailed comparison information
     */
    public String getDetail() {
      return detail;
    }
  }

  /**
   * Aggregate report for the full benchmark suite evaluation.
   */
  public static class BenchmarkReport implements Serializable {
    private static final long serialVersionUID = 1004L;

    private final String suiteName;
    private final List<ProblemResult> results;
    private final int passed;
    private final int failed;
    private final int notAttempted;

    /**
     * Creates a new benchmark report.
     *
     * @param suiteName the name of the benchmark suite
     * @param results individual problem results
     * @param passed number of passed problems
     * @param failed number of failed problems
     * @param notAttempted number of problems not attempted
     */
    public BenchmarkReport(String suiteName, List<ProblemResult> results, int passed, int failed,
        int notAttempted) {
      this.suiteName = suiteName;
      this.results = new ArrayList<>(results);
      this.passed = passed;
      this.failed = failed;
      this.notAttempted = notAttempted;
    }

    /**
     * Returns the pass rate as a fraction (0.0 to 1.0) of attempted problems.
     *
     * @return the pass rate, or 0.0 if no problems were attempted
     */
    public double getPassRate() {
      int attempted = passed + failed;
      return attempted > 0 ? (double) passed / attempted : 0.0;
    }

    /**
     * Returns the convergence rate as a fraction of total problems that either passed or were
     * attempted.
     *
     * @return the convergence rate
     */
    public double getConvergenceRate() {
      int total = passed + failed + notAttempted;
      return total > 0 ? (double) (passed + failed) / total : 0.0;
    }

    /**
     * Returns the number of passed problems.
     *
     * @return passed count
     */
    public int getPassed() {
      return passed;
    }

    /**
     * Returns the number of failed problems.
     *
     * @return failed count
     */
    public int getFailed() {
      return failed;
    }

    /**
     * Returns the number of not-attempted problems.
     *
     * @return not-attempted count
     */
    public int getNotAttempted() {
      return notAttempted;
    }

    /**
     * Returns only the failed problem results.
     *
     * @return list of failed problem results
     */
    public List<ProblemResult> getFailedProblems() {
      List<ProblemResult> failedList = new ArrayList<>();
      for (ProblemResult r : results) {
        if (!r.isPassed() && !"NOT_ATTEMPTED".equals(r.getVerdict())) {
          failedList.add(r);
        }
      }
      return Collections.unmodifiableList(failedList);
    }

    /**
     * Returns all individual problem results.
     *
     * @return unmodifiable list of all results
     */
    public List<ProblemResult> getResults() {
      return Collections.unmodifiableList(results);
    }

    /**
     * Returns results filtered by problem category.
     *
     * @param category the category to filter by
     * @return list of results in that category
     */
    public List<ProblemResult> getResultsByCategory(ProblemCategory category) {
      List<ProblemResult> filtered = new ArrayList<>();
      for (ProblemResult r : results) {
        if (r.getProblem().getCategory() == category) {
          filtered.add(r);
        }
      }
      return Collections.unmodifiableList(filtered);
    }

    /**
     * Serializes the benchmark report to JSON.
     *
     * @return JSON string representation
     */
    public String toJson() {
      Gson gson =
          new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("suiteName", suiteName);
      map.put("passed", passed);
      map.put("failed", failed);
      map.put("notAttempted", notAttempted);
      map.put("passRate", getPassRate());
      map.put("convergenceRate", getConvergenceRate());

      List<Map<String, Object>> resultList = new ArrayList<>();
      for (ProblemResult r : results) {
        Map<String, Object> rm = new LinkedHashMap<>();
        rm.put("id", r.getProblem().getId());
        rm.put("category", r.getProblem().getCategory().name());
        rm.put("difficulty", r.getProblem().getDifficulty().name());
        rm.put("verdict", r.getVerdict());
        rm.put("detail", r.getDetail());
        if (!Double.isNaN(r.getActualValue())) {
          rm.put("actualValue", r.getActualValue());
        }
        rm.put("expectedValue", r.getProblem().getExpectedValue());
        rm.put("unit", r.getProblem().getUnit());
        resultList.add(rm);
      }
      map.put("results", resultList);
      return gson.toJson(map);
    }
  }
}
