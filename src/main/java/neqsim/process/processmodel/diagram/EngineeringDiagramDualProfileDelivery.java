package neqsim.process.processmodel.diagram;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Publishes coordinated PFD and review-required P&amp;ID proposal deliveries from one canonical {@link ProcessSystem}.
 *
 * <p>
 * Both views are generated from the same process object and must retain the same canonical source-graph fingerprint.
 * The P&amp;ID view is not a complete control, piping, or safety design. Its accompanying native DEXPI 2.0 Process file
 * is a PFD/BFD information-model companion only; it is not represented as a DEXPI Plant or Proteus P&amp;ID exchange.
 * </p>
 *
 * <p>
 * This opt-in facade does not claim ISO 10628, ISO 14617, ISA-5.1, DEXPI interoperability, discipline approval, or
 * fitness for construction.
 * </p>
 */
public final class EngineeringDiagramDualProfileDelivery {
  private static final String SCHEMA_VERSION = "neqsim_engineering_diagram_dual_profile_delivery.v1";
  private static final String MANIFEST_FILE = "dual-profile-manifest.json";

  private EngineeringDiagramDualProfileDelivery() {
  }

  /** Immutable coordinated delivery request. */
  public static final class Request {
    private final String plantId;
    private final String revision;
    private final String pfdDrawingNumber;
    private final String pidDrawingNumber;
    private final String title;
    private final String operatingCaseId;
    private final NativeEngineeringDiagramRenderer.SheetFormat sheetFormat;
    private final NativeEngineeringDiagramRenderer.RoutingMode routingMode;
    private final EngineeringDiagramDesignationRegister designationRegister;
    private final EngineeringDiagramLayoutRegister layoutRegister;
    private final EngineeringDiagramConventionRegister conventionRegister;

    private Request(Builder builder) {
      plantId = requireText(builder.plantId, "plantId");
      revision = requireText(builder.revision, "revision");
      pfdDrawingNumber = requireText(builder.pfdDrawingNumber, "pfdDrawingNumber");
      pidDrawingNumber = requireText(builder.pidDrawingNumber, "pidDrawingNumber");
      title = requireText(builder.title, "title");
      operatingCaseId = optionalText(builder.operatingCaseId);
      sheetFormat = requireNonNull(builder.sheetFormat, "sheetFormat");
      routingMode = requireNonNull(builder.routingMode, "routingMode");
      designationRegister = requireNonNull(builder.designationRegister, "designationRegister");
      layoutRegister = requireNonNull(builder.layoutRegister, "layoutRegister");
      conventionRegister = requireNonNull(builder.conventionRegister, "conventionRegister");
      if (pfdDrawingNumber.equals(pidDrawingNumber)) {
        throw new IllegalArgumentException("PFD and P&ID drawing numbers must be distinct");
      }
    }

    /**
     * Starts a request for coordinated PFD and P&amp;ID proposal deliveries.
     *
     * @param plantId persistent plant identity
     * @param revision controlled source-model revision
     * @param pfdDrawingNumber controlled PFD drawing number
     * @param pidDrawingNumber controlled P&amp;ID drawing number
     * @param title common drawing-set title
     * @return request builder
     */
    public static Builder builder(String plantId, String revision, String pfdDrawingNumber, String pidDrawingNumber,
        String title) {
      return new Builder(plantId, revision, pfdDrawingNumber, pidDrawingNumber, title);
    }

    /** Mutable builder for a coordinated request. */
    public static final class Builder {
      private final String plantId;
      private final String revision;
      private final String pfdDrawingNumber;
      private final String pidDrawingNumber;
      private final String title;
      private String operatingCaseId;
      private NativeEngineeringDiagramRenderer.SheetFormat sheetFormat = NativeEngineeringDiagramRenderer.SheetFormat.A3_LANDSCAPE;
      private NativeEngineeringDiagramRenderer.RoutingMode routingMode = NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL;
      private EngineeringDiagramDesignationRegister designationRegister = new EngineeringDiagramDesignationRegister();
      private EngineeringDiagramLayoutRegister layoutRegister = new EngineeringDiagramLayoutRegister();
      private EngineeringDiagramConventionRegister conventionRegister = new EngineeringDiagramConventionRegister();

      private Builder(String plantId, String revision, String pfdDrawingNumber, String pidDrawingNumber, String title) {
        this.plantId = plantId;
        this.revision = revision;
        this.pfdDrawingNumber = pfdDrawingNumber;
        this.pidDrawingNumber = pidDrawingNumber;
        this.title = title;
      }

      /** @param value successfully-run operating-case identity @return this builder */
      public Builder operatingCaseId(String value) {
        operatingCaseId = requireText(value, "operatingCaseId");
        return this;
      }

      /** @param value common sheet format @return this builder */
      public Builder sheetFormat(NativeEngineeringDiagramRenderer.SheetFormat value) {
        sheetFormat = requireNonNull(value, "sheetFormat");
        return this;
      }

      /** @param value common routing mode @return this builder */
      public Builder routingMode(NativeEngineeringDiagramRenderer.RoutingMode value) {
        routingMode = requireNonNull(value, "routingMode");
        return this;
      }

      /** @param value common designation evidence @return this builder */
      public Builder designationRegister(EngineeringDiagramDesignationRegister value) {
        designationRegister = requireNonNull(value, "designationRegister");
        return this;
      }

      /** @param value common manual layout evidence @return this builder */
      public Builder layoutRegister(EngineeringDiagramLayoutRegister value) {
        layoutRegister = requireNonNull(value, "layoutRegister");
        return this;
      }

      /** @param value common project symbol conventions @return this builder */
      public Builder conventionRegister(EngineeringDiagramConventionRegister value) {
        conventionRegister = requireNonNull(value, "conventionRegister");
        return this;
      }

      /** @return validated immutable request */
      public Request build() {
        return new Request(this);
      }
    }
  }

  /** Immutable coordinated-delivery evidence. */
  public static final class Report {
    private final Path directory;
    private final EngineeringDiagramDelivery.Report pfd;
    private final EngineeringDiagramDelivery.Report pid;
    private final String fingerprint;

    private Report(Path directory, EngineeringDiagramDelivery.Report pfd, EngineeringDiagramDelivery.Report pid) {
      this.directory = directory;
      this.pfd = pfd;
      this.pid = pid;
      fingerprint = sha256(new GsonBuilder().create().toJson(toMapWithoutFingerprint()));
    }

    /** @return published bundle directory */
    public Path getDirectory() {
      return directory;
    }

    /** @return PFD delivery evidence */
    public EngineeringDiagramDelivery.Report getPfd() {
      return pfd;
    }

    /** @return review-required P&amp;ID proposal delivery evidence */
    public EngineeringDiagramDelivery.Report getPid() {
      return pid;
    }

    /** @return deterministic manifest fingerprint */
    public String getFingerprint() {
      return fingerprint;
    }

    /** @return whether both deliveries passed and retained one canonical source graph */
    public boolean isComplete() {
      return pfd.isComplete() && pid.isComplete()
          && pfd.getDocumentSet().getSourceGraphFingerprint().equals(pid.getDocumentSet().getSourceGraphFingerprint());
    }

    /** @return deterministic machine-readable evidence */
    public Map<String, Object> toMap() {
      Map<String, Object> result = toMapWithoutFingerprint();
      result.put("fingerprint", fingerprint);
      return result;
    }

    /** @return pretty-printed deterministic manifest JSON */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }

    private Map<String, Object> toMapWithoutFingerprint() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", SCHEMA_VERSION);
      result.put("plantId", pfd.getDocumentSet().getPlantId());
      result.put("revision", pfd.getDocumentSet().getRevision());
      result.put("sourceGraphFingerprint", pfd.getDocumentSet().getSourceGraphFingerprint());
      result.put("pfdContentProfile", ContentProfile.PFD.name());
      result.put("pidContentProfile", ContentProfile.PID.name());
      result.put("pfdDexpiInformationModel", "DEXPI_2_0_PROCESS");
      result.put("pidDexpiInformationModel", "PROCESS_PFD_BFD_COMPANION_ONLY");
      result.put("pidApprovalStatus", "REVIEW_REQUIRED");
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("iso10628ConformanceClaimed", Boolean.FALSE);
      result.put("sameCanonicalPlant", Boolean.valueOf(isComplete()));
      result.put("pfdDelivery", pfd.toMap());
      result.put("pidDelivery", pid.toMap());
      return result;
    }
  }

  /**
   * Publishes coordinated PFD and P&amp;ID proposal deliveries.
   *
   * @param processSystem successfully configured process model
   * @param directory new destination directory
   * @param request controlled coordinated request
   * @return coordinated delivery report
   * @throws IOException when either bounded delivery or final manifest publication fails
   */
  public static Report deliver(ProcessSystem processSystem, Path directory, Request request) throws IOException {
    if (processSystem == null || directory == null || request == null) {
      throw new IllegalArgumentException("processSystem, directory, and request must not be null");
    }
    Path target = directory.toAbsolutePath().normalize();
    if (Files.exists(target)) {
      throw new IllegalArgumentException("delivery directory must not already exist: " + target);
    }
    Files.createDirectories(target);
    try {
      EngineeringDiagramDelivery.Report pfd = EngineeringDiagramDelivery.deliver(processSystem, target.resolve("pfd"),
          deliveryRequest(request, ContentProfile.PFD, request.pfdDrawingNumber));
      EngineeringDiagramDelivery.Report pid = EngineeringDiagramDelivery.deliver(processSystem, target.resolve("pid"),
          deliveryRequest(request, ContentProfile.PID, request.pidDrawingNumber));
      Report report = new Report(target, pfd, pid);
      if (!report.isComplete()) {
        throw new IOException("PFD and P&ID deliveries do not retain one complete canonical plant");
      }
      Files.write(target.resolve(MANIFEST_FILE), report.toJson().getBytes(StandardCharsets.UTF_8));
      return report;
    } catch (IOException ex) {
      deleteRecursively(target);
      throw ex;
    } catch (RuntimeException ex) {
      deleteRecursively(target);
      throw ex;
    }
  }

  private static EngineeringDiagramDelivery.Request deliveryRequest(Request request, ContentProfile profile,
      String drawingNumber) {
    EngineeringDiagramDelivery.Request.Builder builder = EngineeringDiagramDelivery.Request
        .builder(request.plantId, request.revision, drawingNumber,
            request.title + (profile == ContentProfile.PFD ? " — PFD" : " — P&ID proposal"), profile)
        .sheetFormat(request.sheetFormat).routingMode(request.routingMode)
        .designationRegister(request.designationRegister).layoutRegister(request.layoutRegister)
        .conventionRegister(request.conventionRegister);
    if (!request.operatingCaseId.isEmpty()) {
      builder.operatingCaseId(request.operatingCaseId);
    }
    return builder.build();
  }

  private static void deleteRecursively(Path path) {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try {
      if (Files.isDirectory(path)) {
        DirectoryStream<Path> children = Files.newDirectoryStream(path);
        try {
          for (Path child : children) {
            deleteRecursively(child);
          }
        } finally {
          children.close();
        }
      }
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup must not hide the original publication failure.
    }
  }

  private static String requireText(String value, String name) {
    String result = optionalText(value);
    if (result.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be null or blank");
    }
    return result;
  }

  private static String optionalText(String value) {
    return value == null ? "" : value.trim();
  }

  private static <T> T requireNonNull(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    return value;
  }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : digest) {
        result.append(String.format("%02x", item & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", ex);
    }
  }
}
