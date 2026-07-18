package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry and fail-closed service assessment for exact engineering method versions. */
public final class EngineeringMethodQualificationRegistry implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Overall qualification verdict for one method execution context. */
  public enum Status {
    QUALIFIED_FOR_SERVICE, UNREGISTERED_METHOD, INCOMPLETE_QUALIFICATION, MISSING_INDEPENDENT_BENCHMARK,
    USE_NOT_QUALIFIED, INSUFFICIENT_SERVICE_CONTEXT, OUTSIDE_QUALIFIED_ENVELOPE
  }

  /** Immutable registry assessment suitable for evidence packages and readiness gates. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String methodKey;
    private final EngineeringMethodServiceContext serviceContext;
    private final Status status;
    private final boolean executionPermitted;
    private final List<String> findings;

    Result(String methodKey, EngineeringMethodServiceContext serviceContext, Status status, boolean executionPermitted,
        List<String> findings) {
      this.methodKey = methodKey;
      this.serviceContext = serviceContext;
      this.status = status;
      this.executionPermitted = executionPermitted;
      this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
    }

    public String getMethodKey() {
      return methodKey;
    }

    public Status getStatus() {
      return status;
    }

    public boolean isQualifiedForService() {
      return status == Status.QUALIFIED_FOR_SERVICE;
    }

    /** @return true when calculation may run; this does not mean its result is qualified */
    public boolean isExecutionPermitted() {
      return executionPermitted;
    }

    public List<String> getFindings() {
      return findings;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "engineering_method_service_assessment.v1");
      result.put("methodKey", methodKey);
      result.put("serviceContext", serviceContext == null ? null : serviceContext.toMap());
      result.put("status", status.name());
      result.put("qualifiedForService", Boolean.valueOf(isQualifiedForService()));
      result.put("executionPermitted", Boolean.valueOf(executionPermitted));
      result.put("findings", new ArrayList<String>(findings));
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }

  private final String id;
  private final String revision;
  private final Map<String, EngineeringMethodQualification> qualifications = new LinkedHashMap<String, EngineeringMethodQualification>();

  public EngineeringMethodQualificationRegistry(String id, String revision) {
    this.id = text(id, "id");
    this.revision = text(revision, "revision");
  }

  public EngineeringMethodQualificationRegistry register(EngineeringMethodQualification qualification) {
    if (qualification == null) {
      throw new IllegalArgumentException("qualification must not be null");
    }
    String key = qualification.getMethodKey();
    if (qualifications.containsKey(key)) {
      throw new IllegalArgumentException("Duplicate method qualification " + key);
    }
    qualifications.put(key, qualification);
    return this;
  }

  /**
   * Evaluates one exact method version against qualification completeness, benchmark evidence, use and service range.
   */
  public Result assess(String methodKey, EngineeringMethodServiceContext context,
      EngineeringBenchmarkSuite.Report benchmarkReport) {
    String key = text(methodKey, "methodKey");
    List<String> findings = new ArrayList<String>();
    EngineeringMethodQualification qualification = qualifications.get(key);
    if (qualification == null) {
      findings.add("No qualification is registered for the exact method version");
      return new Result(key, context, Status.UNREGISTERED_METHOD, false, findings);
    }
    if (!qualification.isIndustrialQualificationComplete()) {
      findings.add("Qualification lacks a complete structured envelope, intended use, acceptance criteria or approval");
      return new Result(key, context, Status.INCOMPLETE_QUALIFICATION, false, findings);
    }
    if (benchmarkReport == null || !benchmarkReport.getQualifyingMethods().contains(key)) {
      findings.add("No passing independently reviewed non-regression benchmark covers the exact method version");
      return new Result(key, context, Status.MISSING_INDEPENDENT_BENCHMARK, false, findings);
    }
    if (context == null) {
      findings.add("A controlled service context is required");
      return new Result(key, null, Status.INSUFFICIENT_SERVICE_CONTEXT, false, findings);
    }
    if (!qualification.getQualifiedUses().contains(context.getIntendedUse())) {
      findings.add("Intended use " + context.getIntendedUse() + " is not included in the qualification");
      return new Result(key, context, Status.USE_NOT_QUALIFIED, true, findings);
    }
    EngineeringMethodApplicabilityEnvelope.Assessment applicability = qualification.getApplicabilityEnvelope()
        .assess(context);
    findings.addAll(applicability.getFindings());
    if (!applicability.isContextComplete()) {
      return new Result(key, context, Status.INSUFFICIENT_SERVICE_CONTEXT, false, findings);
    }
    if (!applicability.isWithinEnvelope()) {
      return new Result(key, context, Status.OUTSIDE_QUALIFIED_ENVELOPE, applicability.isExecutionPermitted(),
          findings);
    }
    findings.add("Exact method version is benchmarked and qualified for the supplied service and intended use");
    return new Result(key, context, Status.QUALIFIED_FOR_SERVICE, true, findings);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "engineering_method_qualification_registry.v1");
    result.put("registryId", id);
    result.put("revision", revision);
    List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
    for (EngineeringMethodQualification qualification : qualifications.values()) {
      records.add(qualification.toMap());
    }
    result.put("qualifications", records);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
