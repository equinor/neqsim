package neqsim.process.engineering.verticalslice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.designcase.EngineeringDesignCase;

/** Controlled acceptance policy for the inlet-separator, compression, cooling and export vertical slice. */
public final class InletCompressionExportSlicePolicy implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String policyId;
  private final String revision;
  private final String separatorTag;
  private final String compressorTag;
  private final String coolerTag;
  private final String exportLineTag;
  private final String recycleValveTag;
  private final String recycleUnitTag;
  private final String pressureControlValveTag;
  private final String levelControlValveTag;
  private final String reliefValveTag;
  private final String blowdownValveTag;
  private final String suctionEsdValveTag;
  private final String dischargeEsdValveTag;
  private final String flareConnectionTag;
  private final Set<EngineeringDesignCase.Type> requiredCaseTypes;
  private final Set<String> requiredDynamicScenarioIds;
  private final Set<String> requiredStandards;
  private final List<String> evidenceReferences;
  private final boolean coupledReliefBlowdownFlareRequired;

  private InletCompressionExportSlicePolicy(Builder builder) {
    policyId = requireText(builder.policyId, "policyId");
    revision = requireText(builder.revision, "revision");
    separatorTag = requireText(builder.separatorTag, "separatorTag");
    compressorTag = requireText(builder.compressorTag, "compressorTag");
    coolerTag = requireText(builder.coolerTag, "coolerTag");
    exportLineTag = requireText(builder.exportLineTag, "exportLineTag");
    recycleValveTag = requireText(builder.recycleValveTag, "recycleValveTag");
    recycleUnitTag = requireText(builder.recycleUnitTag, "recycleUnitTag");
    pressureControlValveTag = requireText(builder.pressureControlValveTag, "pressureControlValveTag");
    levelControlValveTag = requireText(builder.levelControlValveTag, "levelControlValveTag");
    reliefValveTag = requireText(builder.reliefValveTag, "reliefValveTag");
    blowdownValveTag = requireText(builder.blowdownValveTag, "blowdownValveTag");
    suctionEsdValveTag = requireText(builder.suctionEsdValveTag, "suctionEsdValveTag");
    dischargeEsdValveTag = requireText(builder.dischargeEsdValveTag, "dischargeEsdValveTag");
    flareConnectionTag = requireText(builder.flareConnectionTag, "flareConnectionTag");
    requiredCaseTypes = Collections.unmodifiableSet(EnumSet.copyOf(builder.requiredCaseTypes));
    requiredDynamicScenarioIds = Collections
        .unmodifiableSet(new LinkedHashSet<String>(builder.requiredDynamicScenarioIds));
    requiredStandards = Collections.unmodifiableSet(new LinkedHashSet<String>(builder.requiredStandards));
    evidenceReferences = Collections.unmodifiableList(new ArrayList<String>(builder.evidenceReferences));
    coupledReliefBlowdownFlareRequired = builder.coupledReliefBlowdownFlareRequired;
  }

  public static Builder builder(String policyId, String revision) {
    return new Builder(policyId, revision);
  }

  public String getPolicyId() {
    return policyId;
  }

  public String getRevision() {
    return revision;
  }

  public String getSeparatorTag() {
    return separatorTag;
  }

  public String getCompressorTag() {
    return compressorTag;
  }

  public String getCoolerTag() {
    return coolerTag;
  }

  public String getExportLineTag() {
    return exportLineTag;
  }

  public String getRecycleValveTag() {
    return recycleValveTag;
  }

  public String getRecycleUnitTag() {
    return recycleUnitTag;
  }

  public String getPressureControlValveTag() {
    return pressureControlValveTag;
  }

  public String getLevelControlValveTag() {
    return levelControlValveTag;
  }

  public String getReliefValveTag() {
    return reliefValveTag;
  }

  public String getBlowdownValveTag() {
    return blowdownValveTag;
  }

  public String getSuctionEsdValveTag() {
    return suctionEsdValveTag;
  }

  public String getDischargeEsdValveTag() {
    return dischargeEsdValveTag;
  }

  public String getFlareConnectionTag() {
    return flareConnectionTag;
  }

  public Set<EngineeringDesignCase.Type> getRequiredCaseTypes() {
    return requiredCaseTypes;
  }

  public Set<String> getRequiredDynamicScenarioIds() {
    return requiredDynamicScenarioIds;
  }

  public Set<String> getRequiredStandards() {
    return requiredStandards;
  }

  public List<String> getEvidenceReferences() {
    return evidenceReferences;
  }

  public boolean isCoupledReliefBlowdownFlareRequired() {
    return coupledReliefBlowdownFlareRequired;
  }

  /** @return complete controlled policy definition used for execution fingerprinting and audit */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("policyId", policyId);
    result.put("revision", revision);
    result.put("separatorTag", separatorTag);
    result.put("compressorTag", compressorTag);
    result.put("coolerTag", coolerTag);
    result.put("exportLineTag", exportLineTag);
    result.put("recycleValveTag", recycleValveTag);
    result.put("recycleUnitTag", recycleUnitTag);
    result.put("pressureControlValveTag", pressureControlValveTag);
    result.put("levelControlValveTag", levelControlValveTag);
    result.put("reliefValveTag", reliefValveTag);
    result.put("blowdownValveTag", blowdownValveTag);
    result.put("suctionEsdValveTag", suctionEsdValveTag);
    result.put("dischargeEsdValveTag", dischargeEsdValveTag);
    result.put("flareConnectionTag", flareConnectionTag);
    List<String> caseTypes = new ArrayList<String>();
    for (EngineeringDesignCase.Type type : requiredCaseTypes) {
      caseTypes.add(type.name());
    }
    result.put("requiredCaseTypes", caseTypes);
    result.put("requiredDynamicScenarioIds", new ArrayList<String>(requiredDynamicScenarioIds));
    result.put("requiredStandards", new ArrayList<String>(requiredStandards));
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    result.put("coupledReliefBlowdownFlareRequired", Boolean.valueOf(coupledReliefBlowdownFlareRequired));
    return result;
  }

  /** Builder with the complete production vertical-slice case and standards basis enabled by default. */
  public static final class Builder {
    private final String policyId;
    private final String revision;
    private String separatorTag;
    private String compressorTag;
    private String coolerTag;
    private String exportLineTag;
    private String recycleValveTag;
    private String recycleUnitTag;
    private String pressureControlValveTag;
    private String levelControlValveTag;
    private String reliefValveTag;
    private String blowdownValveTag;
    private String suctionEsdValveTag;
    private String dischargeEsdValveTag;
    private String flareConnectionTag;
    private final Set<EngineeringDesignCase.Type> requiredCaseTypes = EnumSet.of(EngineeringDesignCase.Type.NORMAL,
        EngineeringDesignCase.Type.MINIMUM_TURNDOWN, EngineeringDesignCase.Type.MAXIMUM_PRODUCTION,
        EngineeringDesignCase.Type.STARTUP, EngineeringDesignCase.Type.SHUTDOWN,
        EngineeringDesignCase.Type.EQUIPMENT_TRIP, EngineeringDesignCase.Type.SETTLE_OUT,
        EngineeringDesignCase.Type.BLOCKED_OUTLET, EngineeringDesignCase.Type.FIRE,
        EngineeringDesignCase.Type.BLOWDOWN);
    private final Set<String> requiredDynamicScenarioIds = new LinkedHashSet<String>();
    private final Set<String> requiredStandards = new LinkedHashSet<String>();
    private final List<String> evidenceReferences = new ArrayList<String>();
    private boolean coupledReliefBlowdownFlareRequired = true;

    private Builder(String policyId, String revision) {
      this.policyId = policyId;
      this.revision = revision;
      Collections.addAll(requiredStandards, "DEXPI", "IEC 61511", "API 520", "API 521", "API 617", "API 670",
          "ANSI/ISA-5.1", "NORSOK P-002", "NORSOK I-001", "NORSOK I-002", "NORSOK M-001");
    }

    public Builder processTags(String separator, String compressor, String cooler, String exportLine) {
      separatorTag = separator;
      compressorTag = compressor;
      coolerTag = cooler;
      exportLineTag = exportLine;
      return this;
    }

    public Builder controlTags(String recycleValve, String recycleUnit, String pressureControlValve,
        String levelControlValve) {
      recycleValveTag = recycleValve;
      recycleUnitTag = recycleUnit;
      pressureControlValveTag = pressureControlValve;
      levelControlValveTag = levelControlValve;
      return this;
    }

    public Builder safetyTags(String reliefValve, String blowdownValve, String suctionEsdValve,
        String dischargeEsdValve, String flareConnection) {
      reliefValveTag = reliefValve;
      blowdownValveTag = blowdownValve;
      suctionEsdValveTag = suctionEsdValve;
      dischargeEsdValveTag = dischargeEsdValve;
      flareConnectionTag = flareConnection;
      return this;
    }

    public Builder addRequiredDynamicScenario(String scenarioId) {
      requiredDynamicScenarioIds.add(requireText(scenarioId, "scenarioId"));
      return this;
    }

    public Builder addEvidenceReference(String reference) {
      String normalized = requireText(reference, "evidenceReference");
      if (!evidenceReferences.contains(normalized)) {
        evidenceReferences.add(normalized);
      }
      return this;
    }

    public Builder coupledReliefBlowdownFlareRequired(boolean value) {
      coupledReliefBlowdownFlareRequired = value;
      return this;
    }

    public InletCompressionExportSlicePolicy build() {
      if (requiredDynamicScenarioIds.isEmpty()) {
        throw new IllegalStateException("At least one controlled dynamic scenario identifier is required");
      }
      if (evidenceReferences.isEmpty()) {
        throw new IllegalStateException("Controlled HAZOP, map, cause-and-effect or design evidence is required");
      }
      return new InletCompressionExportSlicePolicy(this);
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
