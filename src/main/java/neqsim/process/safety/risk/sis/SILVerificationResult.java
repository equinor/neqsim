package neqsim.process.safety.risk.sis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * SIL Verification Result.
 *
 * <p>
 * Contains the results of Safety Integrity Level (SIL) verification for a Safety Instrumented
 * Function (SIF) per IEC 61508/61511. Includes PFD calculation, architecture analysis, diagnostic
 * coverage, and systematic capability assessment.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SILVerificationResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** SIF being verified. */
  private SafetyInstrumentedFunction sif;

  /** Claimed SIL. */
  private int claimedSIL;

  /** Achieved SIL based on PFD. */
  private int achievedSIL;

  /** Whether SIL target is achieved. */
  private boolean silAchieved;

  /** Calculated PFD average. */
  private double pfdAverage;

  /** PFD upper bound. */
  private double pfdUpper;

  /** Diagnostic coverage. */
  private double diagnosticCoverage;

  /** Hardware fault tolerance. */
  private int hardwareFaultTolerance;

  /** Systematic capability claim. */
  private int systematicCapability;

  /** Verification issues found. */
  private List<VerificationIssue> issues;

  /** Component contributions. */
  private List<ComponentContribution> componentContributions;

  /**
   * Verification issue found during analysis.
   */
  public static class VerificationIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    private IssueSeverity severity;
    private IssueCategory category;
    private String description;
    private String recommendation;

    public enum IssueSeverity {
      WARNING, ERROR, INFO
    }

    public enum IssueCategory {
      PFD_EXCEEDED, ARCHITECTURE, DIAGNOSTIC_COVERAGE, PROOF_TEST_INTERVAL, COMMON_CAUSE, SYSTEMATIC
    }

    public VerificationIssue(IssueSeverity severity, IssueCategory category, String description,
        String recommendation) {
      this.severity = severity;
      this.category = category;
      this.description = description;
      this.recommendation = recommendation;
    }

    public IssueSeverity getSeverity() {
      return severity;
    }

    public IssueCategory getCategory() {
      return category;
    }

    public String getDescription() {
      return description;
    }

    public String getRecommendation() {
      return recommendation;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("severity", severity.name());
      map.put("category", category.name());
      map.put("description", description);
      map.put("recommendation", recommendation);
      return map;
    }
  }

  /**
   * Component contribution to overall PFD.
   */
  public static class ComponentContribution implements Serializable {
    private static final long serialVersionUID = 1L;

    private String componentName;
    private String componentType;
    private double failureRate;
    private double pfdContribution;
    private double percentOfTotal;

    public ComponentContribution(String name, String type, double failureRate, double pfd,
        double percent) {
      this.componentName = name;
      this.componentType = type;
      this.failureRate = failureRate;
      this.pfdContribution = pfd;
      this.percentOfTotal = percent;
    }

    public String getComponentName() {
      return componentName;
    }

    public String getComponentType() {
      return componentType;
    }

    public double getFailureRate() {
      return failureRate;
    }

    public double getPfdContribution() {
      return pfdContribution;
    }

    public double getPercentOfTotal() {
      return percentOfTotal;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("componentName", componentName);
      map.put("componentType", componentType);
      map.put("failureRate", failureRate);
      map.put("pfdContribution", pfdContribution);
      map.put("percentOfTotal", percentOfTotal);
      return map;
    }
  }

  /**
   * Creates SIL verification result.
   *
   * @param sif Safety Instrumented Function being verified
   */
  public SILVerificationResult(SafetyInstrumentedFunction sif) {
    this.sif = sif;
    this.claimedSIL = sif.getSil();
    this.issues = new ArrayList<>();
    this.componentContributions = new ArrayList<>();
    performVerification();
  }

  private void performVerification() {
    // Calculate PFD
    this.pfdAverage = sif.getPfdAvg();
    this.pfdUpper = pfdAverage * 1.5; // Conservative estimate

    // Determine achieved SIL
    this.achievedSIL = SafetyInstrumentedFunction.getRequiredSil(pfdAverage);

    // Check if target met
    this.silAchieved = achievedSIL >= claimedSIL;

    // Analyze architecture for fault tolerance
    analyzeArchitecture();

    // Check for issues
    checkForIssues();
  }

  private void analyzeArchitecture() {
    String arch = sif.getArchitecture();
    switch (arch) {
      case "1oo1":
        hardwareFaultTolerance = 0;
        break;
      case "1oo2":
        hardwareFaultTolerance = 1;
        break;
      case "2oo2":
        hardwareFaultTolerance = 0;
        break;
      case "2oo3":
        hardwareFaultTolerance = 1;
        break;
      case "1oo3":
        hardwareFaultTolerance = 2;
        break;
      default:
        hardwareFaultTolerance = 0;
    }

    // Systematic capability based on claimed SIL (simplified)
    this.systematicCapability = claimedSIL;

    // Default diagnostic coverage based on architecture
    this.diagnosticCoverage = arch.contains("oo2") || arch.contains("oo3") ? 0.6 : 0.5;
  }

  private void checkForIssues() {
    // Check PFD vs target
    double targetPFD = SafetyInstrumentedFunction.getMaxPfdForSil(claimedSIL);
    if (pfdAverage > targetPFD) {
      issues.add(new VerificationIssue(VerificationIssue.IssueSeverity.ERROR,
          VerificationIssue.IssueCategory.PFD_EXCEEDED,
          String.format("Calculated PFD (%.2e) exceeds SIL %d target (%.2e)", pfdAverage,
              claimedSIL, targetPFD),
          "Consider upgrading architecture, improving component reliability, or reducing proof test interval"));
    }

    // Check architecture constraints per IEC 61511
    if (claimedSIL == 4 && hardwareFaultTolerance < 2) {
      issues.add(new VerificationIssue(VerificationIssue.IssueSeverity.ERROR,
          VerificationIssue.IssueCategory.ARCHITECTURE,
          "SIL 4 requires minimum hardware fault tolerance of 2",
          "Upgrade to 1oo3 or higher redundancy architecture"));
    } else if (claimedSIL == 3 && hardwareFaultTolerance < 1) {
      issues.add(new VerificationIssue(VerificationIssue.IssueSeverity.WARNING,
          VerificationIssue.IssueCategory.ARCHITECTURE,
          "SIL 3 typically requires minimum hardware fault tolerance of 1",
          "Consider upgrading to redundant architecture or demonstrating additional measures"));
    }

    // Check proof test interval
    if (sif.getProofTestIntervalYears() > 5) {
      issues.add(new VerificationIssue(VerificationIssue.IssueSeverity.WARNING,
          VerificationIssue.IssueCategory.PROOF_TEST_INTERVAL,
          "Proof test interval exceeds 5 years",
          "Consider more frequent proof testing to maintain PFD requirements"));
    }

    // Check diagnostic coverage for higher SILs
    if (claimedSIL >= 3 && diagnosticCoverage < 0.9) {
      issues.add(new VerificationIssue(VerificationIssue.IssueSeverity.WARNING,
          VerificationIssue.IssueCategory.DIAGNOSTIC_COVERAGE,
          String.format("Diagnostic coverage (%.0f%%) may be insufficient for SIL %d",
              diagnosticCoverage * 100, claimedSIL),
          "Implement enhanced diagnostics or online testing capabilities"));
    }
  }

  /**
   * Adds component contribution to PFD.
   *
   * @param name component name
   * @param type component type (sensor, logic, final element)
   * @param failureRate dangerous failure rate (per hour)
   * @param pfd PFD contribution
   * @param percent percentage of total PFD
   */
  public void addComponentContribution(String name, String type, double failureRate, double pfd,
      double percent) {
    componentContributions.add(new ComponentContribution(name, type, failureRate, pfd, percent));
  }

  // Getters

  public SafetyInstrumentedFunction getSif() {
    return sif;
  }

  public int getClaimedSIL() {
    return claimedSIL;
  }

  public int getAchievedSIL() {
    return achievedSIL;
  }

  public boolean isSilAchieved() {
    return silAchieved;
  }

  public double getPfdAverage() {
    return pfdAverage;
  }

  public double getPfdUpper() {
    return pfdUpper;
  }

  public double getDiagnosticCoverage() {
    return diagnosticCoverage;
  }

  public void setDiagnosticCoverage(double coverage) {
    this.diagnosticCoverage = coverage;
  }

  public int getHardwareFaultTolerance() {
    return hardwareFaultTolerance;
  }

  public int getSystematicCapability() {
    return systematicCapability;
  }

  public List<VerificationIssue> getIssues() {
    return new ArrayList<>(issues);
  }

  public List<VerificationIssue> getErrors() {
    List<VerificationIssue> errors = new ArrayList<>();
    for (VerificationIssue issue : issues) {
      if (issue.getSeverity() == VerificationIssue.IssueSeverity.ERROR) {
        errors.add(issue);
      }
    }
    return errors;
  }

  public List<VerificationIssue> getWarnings() {
    List<VerificationIssue> warnings = new ArrayList<>();
    for (VerificationIssue issue : issues) {
      if (issue.getSeverity() == VerificationIssue.IssueSeverity.WARNING) {
        warnings.add(issue);
      }
    }
    return warnings;
  }

  public List<ComponentContribution> getComponentContributions() {
    return new ArrayList<>(componentContributions);
  }

  public boolean hasErrors() {
    for (VerificationIssue issue : issues) {
      if (issue.getSeverity() == VerificationIssue.IssueSeverity.ERROR) {
        return true;
      }
    }
    return false;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();

    // SIF info
    Map<String, Object> sifInfo = new HashMap<>();
    sifInfo.put("name", sif.getName());
    sifInfo.put("category", sif.getCategory().name());
    sifInfo.put("architecture", sif.getArchitecture());
    sifInfo.put("proofTestIntervalYears", sif.getProofTestIntervalYears());
    map.put("sif", sifInfo);

    // SIL verification
    Map<String, Object> silVerification = new HashMap<>();
    silVerification.put("claimedSIL", claimedSIL);
    silVerification.put("achievedSIL", achievedSIL);
    silVerification.put("silAchieved", silAchieved);
    silVerification.put("pfdAverage", pfdAverage);
    silVerification.put("pfdUpper", pfdUpper);
    silVerification.put("targetPFD", SafetyInstrumentedFunction.getMaxPfdForSil(claimedSIL));
    map.put("silVerification", silVerification);

    // Architecture assessment
    Map<String, Object> architecture = new HashMap<>();
    architecture.put("hardwareFaultTolerance", hardwareFaultTolerance);
    architecture.put("systematicCapability", systematicCapability);
    architecture.put("diagnosticCoverage", diagnosticCoverage);
    map.put("architectureAssessment", architecture);

    // Component contributions
    if (!componentContributions.isEmpty()) {
      List<Map<String, Object>> contributions = new ArrayList<>();
      for (ComponentContribution cc : componentContributions) {
        contributions.add(cc.toMap());
      }
      map.put("componentContributions", contributions);
    }

    // Issues
    List<Map<String, Object>> issueList = new ArrayList<>();
    for (VerificationIssue issue : issues) {
      issueList.add(issue.toMap());
    }
    map.put("issues", issueList);

    // Summary
    Map<String, Object> summary = new HashMap<>();
    summary.put("errorCount", getErrors().size());
    summary.put("warningCount", getWarnings().size());
    summary.put("verificationPassed", !hasErrors());
    map.put("summary", summary);

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Generates report string.
   *
   * @return verification report
   */
  public String toReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("SIL Verification Report\n");
    sb.append("═".repeat(60)).append("\n\n");

    sb.append("SIF: ").append(sif.getName()).append("\n");
    sb.append("Category: ").append(sif.getCategory()).append("\n");
    sb.append("Architecture: ").append(sif.getArchitecture()).append("\n\n");

    sb.append("SIL Assessment:\n");
    sb.append("─".repeat(40)).append("\n");
    sb.append(String.format("  Claimed SIL:    %d%n", claimedSIL));
    sb.append(String.format("  Achieved SIL:   %d%n", achievedSIL));
    sb.append(String.format("  Target PFD:     %.2e%n",
        SafetyInstrumentedFunction.getMaxPfdForSil(claimedSIL)));
    sb.append(String.format("  Calculated PFD: %.2e%n", pfdAverage));
    sb.append(String.format("  Status:         %s%n", silAchieved ? "✓ ACHIEVED" : "✗ NOT MET"));
    sb.append("\n");

    sb.append("Architecture Assessment:\n");
    sb.append("─".repeat(40)).append("\n");
    sb.append(String.format("  Hardware Fault Tolerance: %d%n", hardwareFaultTolerance));
    sb.append(String.format("  Systematic Capability:    SC %d%n", systematicCapability));
    sb.append(String.format("  Diagnostic Coverage:      %.0f%%%n", diagnosticCoverage * 100));
    sb.append("\n");

    if (!issues.isEmpty()) {
      sb.append("Issues Found:\n");
      sb.append("─".repeat(40)).append("\n");
      for (VerificationIssue issue : issues) {
        sb.append(String.format("  [%s] %s%n", issue.getSeverity(), issue.getDescription()));
        sb.append(String.format("    → %s%n", issue.getRecommendation()));
      }
    } else {
      sb.append("No issues found.\n");
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("SILVerificationResult[%s: SIL %d claimed, SIL %d achieved, PFD=%.2e, %s]",
        sif.getName(), claimedSIL, achievedSIL, pfdAverage, silAchieved ? "PASSED" : "FAILED");
  }
}
