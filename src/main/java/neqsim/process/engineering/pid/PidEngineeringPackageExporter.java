package neqsim.process.engineering.pid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;

/** Exports DEXPI plus the governed P&amp;ID proposal and completeness sidecars as one package. */
public final class PidEngineeringPackageExporter {
  private PidEngineeringPackageExporter() {
  }

  /** Result of a governed P&amp;ID package export. */
  public static final class ExportResult {
    private final DexpiEngineeringExporter.ExportResult engineeringPackage;
    private final Path designModelFile;
    private final Path completenessReportFile;

    ExportResult(DexpiEngineeringExporter.ExportResult engineeringPackage, Path designModelFile,
        Path completenessReportFile) {
      this.engineeringPackage = engineeringPackage;
      this.designModelFile = designModelFile;
      this.completenessReportFile = completenessReportFile;
    }

    public DexpiEngineeringExporter.ExportResult getEngineeringPackage() {
      return engineeringPackage;
    }

    public Path getDesignModelFile() {
      return designModelFile;
    }

    public Path getCompletenessReportFile() {
      return completenessReportFile;
    }
  }

  public static ExportResult export(EngineeringProject project, PidDesignModel model, Path outputDirectory)
      throws IOException {
    if (project == null || model == null || outputDirectory == null) {
      throw new IllegalArgumentException("project, model and outputDirectory must not be null");
    }
    if (!project.getProjectId().equals(model.getProjectId())) {
      throw new IllegalArgumentException("project and P&ID design model identities do not match");
    }
    DexpiEngineeringExporter.ExportResult engineering = DexpiEngineeringExporter.export(project, outputDirectory);
    Path modelFile = outputDirectory.resolve("pid-design-model.json");
    Files.write(modelFile, new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(model.toMap()).getBytes(StandardCharsets.UTF_8));
    PidCompletenessReport report = PidCompletenessValidator.validate(model);
    Path completenessFile = outputDirectory.resolve("pid-completeness-report.json");
    Files.write(completenessFile, report.toJson().getBytes(StandardCharsets.UTF_8));
    PidDexpiMaterializer.materialize(model, engineering.getDexpiFile());
    PidDexpiMaterializer.materialize(model, engineering.getPyDexpiFile());
    DexpiEngineeringExporter.refreshPackageManifest(outputDirectory);
    return new ExportResult(engineering, modelFile, completenessFile);
  }
}
