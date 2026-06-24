package neqsim.process.safety.rupture;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Standards and evidence validator for pipe fire-rupture study handoffs.
 *
 * <p>
 * The validator does not certify compliance. It records whether the evidence needed for an API 521 / ISO 23251 / NORSOK
 * S-001 and piping-specification review is present before a result is treated as design grade.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PipeFireRuptureStandardsValidator implements Serializable {
  private static final long serialVersionUID = 1L;

  private final List<String> standardsApplied;

  /** Creates a validator with the default pipe-fire standards basis. */
  public PipeFireRuptureStandardsValidator() {
    this.standardsApplied = Collections.unmodifiableList(
	new ArrayList<String>(Arrays.asList("API 521 / ISO 23251 depressurization and fire exposure basis",
	    "NORSOK S-001 process safety and emergency depressurization review basis",
	    "Piping class, pipe-size, and material basis",
	    "Material certificate / MDS high-temperature material property basis",
	    "CCPS/TNO source-term handoff basis for post-rupture consequences")));
  }

  /**
   * Gets standards applied by the validator.
   *
   * @return immutable standards list
   */
  public List<String> getStandardsApplied() {
    return standardsApplied;
  }

  /**
   * Validates a data source and optional calculation result.
   *
   * @param dataSource data source to validate
   * @param result calculation result, or null if the calculation did not run
   * @return readiness result with standards findings
   */
  public SafetyStudyReadiness validate(PipeFireRuptureDataSource dataSource, PipeFireRuptureResult result) {
    SafetyStudyReadiness.Builder readiness = SafetyStudyReadiness.builder();
    for (String standard : standardsApplied) {
      readiness.addInfo("standard", standard,
	  "Confirm applicability and acceptance criteria in the formal study basis.");
    }
    if (dataSource == null) {
      readiness.addBlocker("data_source", "No data source was supplied to the standards validator.",
	  "Build a PipeFireRuptureDataSource before running standards checks.");
      return readiness.build();
    }
    readiness.merge(dataSource.readiness());
    if (!dataSource.isPipingSpecificationRowsReviewed()) {
      readiness.addWarning("piping_specification", "Piping specification rows are not reviewed and joined.",
	  "Verify the applicable piping-specification row for the nominal pipe size.");
    }
    if (!dataSource.isMaterialCertificateReviewed()) {
      readiness.addWarning("material", "Material certificate or governing MDS is not reviewed.",
	  "Confirm the selected NeqSim material curve against controlled material data.");
    }
    if (!dataSource.isBlowdownProfileVerified()) {
      readiness.addWarning("API521", "Pressure profile is not marked verified against depressurization basis.",
	  "Run governed blowdown/depressurization and attach the pressure profile evidence.");
    }
    if (!dataSource.isPidTopologyVerified()) {
      readiness.addWarning("NORSOK_S001", "Isolation boundary and blowdown path topology are not verified.",
	  "Trace valves, nozzles, relief/blowdown connections, and battery limits from source drawing evidence.");
    }
    if (result == null) {
      readiness.addBlocker("calculation", "Pipe fire-rupture calculation result is missing.",
	  "Resolve blocker findings and run PipeFireRuptureStudy.");
    } else if (result.isRupturePredicted()) {
      readiness.addInfo("source_term", "Rupture is predicted and a source-term handoff should be reviewed.",
	  "Feed rupture pressure and release estimate into consequence/dispersion assessment.");
    } else {
      readiness.addInfo("source_term", "No rupture was predicted within the simulated exposure duration.",
	  "Confirm maximum exposure time, PFP demand duration, and acceptance criteria.");
    }
    return readiness.build();
  }
}
