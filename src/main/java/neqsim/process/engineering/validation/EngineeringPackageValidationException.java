package neqsim.process.engineering.validation;

import java.io.IOException;
import java.nio.file.Path;

/** Raised when a compiler package fails structural or semantic validation. */
public final class EngineeringPackageValidationException extends IOException {
  private static final long serialVersionUID = 1000L;
  private final Path validationReportFile;
  private final EngineeringPackageValidationReport validationReport;

  public EngineeringPackageValidationException(Path validationReportFile,
      EngineeringPackageValidationReport validationReport) {
    super("Engineering package validation failed with " + validationReport.getErrorCount() + " error(s); see "
        + validationReportFile + summarize(validationReport));
    this.validationReportFile = validationReportFile;
    this.validationReport = validationReport;
  }

  public Path getValidationReportFile() {
    return validationReportFile;
  }

  public EngineeringPackageValidationReport getValidationReport() {
    return validationReport;
  }

  private static String summarize(EngineeringPackageValidationReport report) {
    for (EngineeringPackageValidationReport.Finding finding : report.getFindings()) {
      if (finding.getSeverity() == EngineeringPackageValidationReport.Severity.ERROR) {
        return "; first error: " + finding.getCode() + " in " + finding.getArtifact() + finding.getPath() + " - "
            + finding.getMessage();
      }
    }
    return "";
  }
}
