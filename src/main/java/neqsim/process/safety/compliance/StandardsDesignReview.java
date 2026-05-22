package neqsim.process.safety.compliance;

import java.io.Serializable;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.mechanicaldesign.pipeline.NorsokP002LineSizingValidator;
import neqsim.process.mechanicaldesign.pipeline.NorsokP002LineSizingValidator.LineSizingResult;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.conformity.ConformityReport;
import neqsim.process.mechanicaldesign.separator.conformity.ConformityResult;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.compliance.StandardsComplianceReport.Status;

/**
 * Standards-aware process design review for NeqSim process systems.
 *
 * <p>
 * The review currently orchestrates checks that have direct NeqSim model support: TR1965 gas
 * scrubber conformity and NORSOK P-002 line-sizing screening. The output is a
 * {@link StandardsComplianceReport}, so agents can combine calculated evidence with document
 * evidence from standards or technical-requirement reviews.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class StandardsDesignReview implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** P-002 validator used for pipeline checks. */
  private final NorsokP002LineSizingValidator lineSizingValidator;

  /**
   * Creates a design review using default validators.
   */
  public StandardsDesignReview() {
    this(new NorsokP002LineSizingValidator());
  }

  /**
   * Creates a design review with an injected line-sizing validator.
   *
   * @param lineSizingValidator validator for NORSOK P-002 line sizing
   */
  public StandardsDesignReview(NorsokP002LineSizingValidator lineSizingValidator) {
    this.lineSizingValidator = lineSizingValidator == null ? new NorsokP002LineSizingValidator()
        : lineSizingValidator;
  }

  /**
   * Reviews a process system and returns a populated compliance report.
   *
   * @param process process system to review
   * @return standards compliance report with calculated evidence
   * @throws IllegalArgumentException if {@code process} is null
   */
  public StandardsComplianceReport review(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    StandardsComplianceReport report = new StandardsComplianceReport(process.getName())
        .loadSTS0131().loadTR1965().loadNORSOKP002().loadTR2237();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof GasScrubber) {
        reviewGasScrubber((GasScrubber) unit, report);
      }
      if (unit instanceof PipeLineInterface) {
        reviewPipeline((PipeLineInterface) unit, report);
      }
    }
    return report;
  }

  /**
   * Reviews one gas scrubber against TR1965.
   *
   * @param scrubber gas scrubber to review
   * @param report report receiving the review requirements
   */
  private void reviewGasScrubber(GasScrubber scrubber, StandardsComplianceReport report) {
    if (scrubber.getMechanicalDesign() == null) {
      scrubber.initMechanicalDesign();
    }
    GasScrubberMechanicalDesign design = scrubber.getMechanicalDesign();
    design.setConformityRules("TR1965");
    ConformityReport conformityReport = design.checkConformity();
    for (ConformityResult result : conformityReport.getResults()) {
      String clause = scrubber.getName() + ":" + result.getCheckName();
      report.addRequirement("TR1965", clause, result.getDescription());
      report.setStatus("TR1965", clause, mapConformityStatus(result.getStatus()),
          createConformityEvidence(result));
    }
  }

  /**
   * Reviews one pipeline against NORSOK P-002.
   *
   * @param pipe pipeline to review
   * @param report report receiving the review requirements
   */
  private void reviewPipeline(PipeLineInterface pipe, StandardsComplianceReport report) {
    LineSizingResult result = lineSizingValidator.validate(pipe);
    String clause = ((ProcessEquipmentInterface) pipe).getName() + ":line-sizing";
    report.addRequirement("NORSOK P-002", clause,
        "Line sizing velocity, pressure gradient, and erosional velocity screening");
    report.setStatus("NORSOK P-002", clause,
        result.isAcceptable() ? Status.COMPLIANT : Status.NON_COMPLIANT, result.toJson());
  }

  /**
   * Maps a conformity status to a compliance report status.
   *
   * @param status conformity status
   * @return compliance report status
   */
  private Status mapConformityStatus(ConformityResult.Status status) {
    if (status == ConformityResult.Status.PASS) {
      return Status.COMPLIANT;
    }
    if (status == ConformityResult.Status.WARNING) {
      return Status.PARTIAL;
    }
    if (status == ConformityResult.Status.FAIL) {
      return Status.NON_COMPLIANT;
    }
    return Status.NOT_APPLICABLE;
  }

  /**
   * Builds a compact evidence string for a conformity result.
   *
   * @param result conformity result
   * @return evidence string
   */
  private String createConformityEvidence(ConformityResult result) {
    if (result.getStatus() == ConformityResult.Status.NOT_APPLICABLE) {
      return result.getDescription();
    }
    return result.getDescription() + "; actual=" + result.getActualValue() + " " + result.getUnit()
        + ", limit=" + result.getLimitValue() + " " + result.getUnit();
  }
}