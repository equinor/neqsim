package neqsim.process.safety.depressurization;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.rupture.PidTopologyEvidence;
import neqsim.process.safety.rupture.SafetyEvidenceReference;
import neqsim.process.safety.rupture.SafetyStudyReadiness;
import neqsim.thermo.system.SystemInterface;

/**
 * Governed input package for dynamic blowdown, PSV sizing, and flare-load studies.
 *
 * <p>
 * A data source binds NeqSim transient depressurization inputs to source diagrams, line/equipment
 * lists, piping specifications, PSV, fire, and flare evidence that produced them. Agents should
 * assemble this object before running the dynamic study runner so calculation readiness and
 * provenance remain attached to the result.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class DynamicBlowdownFlareStudyDataSource implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String studyId;
  private final List<BlowdownSource> sources;
  private final PidTopologyEvidence pidTopologyEvidence;
  private final LineEquipmentListEvidence lineEquipmentListEvidence;
  private final List<SafetyEvidenceReference> sourceDocumentEvidence;
  private final List<SafetyEvidenceReference> pipingSpecificationEvidence;
  private final List<SafetyEvidenceReference> processEvidence;
  private final List<SafetyEvidenceReference> reliefValveEvidence;
  private final List<SafetyEvidenceReference> flareEvidence;
  private final List<SafetyEvidenceReference> fireScenarioEvidence;
  private final List<String> assumptions;
  private final List<String> gaps;
  private final double headerDiameterM;
  private final double headerPressureBara;
  private final double headerTemperatureK;
  private final double headerMolarMassKgPerMol;
  private final double headerGamma;
  private final double maxAllowableHeaderMach;
  private final double flareTipDiameterM;
  private final double flareFlameHeightM;
  private final double flareRadiantFraction;
  private final double flareDesignHeatDutyW;
  private final double flareDesignMassFlowKgPerS;
  private final double flareDesignMolarFlowMolePerS;
  private final boolean sourceDiagramsReviewed;
  private final boolean pidTopologyVerified;
  private final boolean lineEquipmentListsReviewed;
  private final boolean pipingSpecificationRowsReviewed;
  private final boolean vesselInventoryReviewed;
  private final boolean valveSizingBasisReviewed;
  private final boolean psvBasisReviewed;
  private final boolean flareSystemBasisReviewed;
  private final boolean fireCaseReviewed;
  private final boolean standardsReviewed;
  private final boolean humanReviewRequired;

  /**
   * Creates a data source.
   *
   * @param builder populated builder
   */
  private DynamicBlowdownFlareStudyDataSource(Builder builder) {
    this.studyId = clean(builder.studyId);
    this.sources = Collections.unmodifiableList(new ArrayList<BlowdownSource>(builder.sources));
    this.pidTopologyEvidence = builder.pidTopologyEvidence;
    this.lineEquipmentListEvidence = builder.lineEquipmentListEvidence;
    this.sourceDocumentEvidence = immutableEvidence(builder.sourceDocumentEvidence);
    this.pipingSpecificationEvidence = immutableEvidence(builder.pipingSpecificationEvidence);
    this.processEvidence = immutableEvidence(builder.processEvidence);
    this.reliefValveEvidence = immutableEvidence(builder.reliefValveEvidence);
    this.flareEvidence = immutableEvidence(builder.flareEvidence);
    this.fireScenarioEvidence = immutableEvidence(builder.fireScenarioEvidence);
    this.assumptions = immutableText(builder.assumptions);
    this.gaps = immutableText(builder.gaps);
    this.headerDiameterM = builder.headerDiameterM;
    this.headerPressureBara = builder.headerPressureBara;
    this.headerTemperatureK = builder.headerTemperatureK;
    this.headerMolarMassKgPerMol = builder.headerMolarMassKgPerMol;
    this.headerGamma = builder.headerGamma;
    this.maxAllowableHeaderMach = builder.maxAllowableHeaderMach;
    this.flareTipDiameterM = builder.flareTipDiameterM;
    this.flareFlameHeightM = builder.flareFlameHeightM;
    this.flareRadiantFraction = builder.flareRadiantFraction;
    this.flareDesignHeatDutyW = builder.flareDesignHeatDutyW;
    this.flareDesignMassFlowKgPerS = builder.flareDesignMassFlowKgPerS;
    this.flareDesignMolarFlowMolePerS = builder.flareDesignMolarFlowMolePerS;
    this.sourceDiagramsReviewed = builder.sourceDiagramsReviewed;
    this.pidTopologyVerified = builder.pidTopologyVerified;
    this.lineEquipmentListsReviewed = builder.lineEquipmentListsReviewed;
    this.pipingSpecificationRowsReviewed = builder.pipingSpecificationRowsReviewed;
    this.vesselInventoryReviewed = builder.vesselInventoryReviewed;
    this.valveSizingBasisReviewed = builder.valveSizingBasisReviewed;
    this.psvBasisReviewed = builder.psvBasisReviewed;
    this.flareSystemBasisReviewed = builder.flareSystemBasisReviewed;
    this.fireCaseReviewed = builder.fireCaseReviewed;
    this.standardsReviewed = builder.standardsReviewed;
    this.humanReviewRequired = builder.humanReviewRequired;
  }

  /**
   * Creates a data-source builder.
   *
   * @param studyId study id
   * @return data-source builder
   */
  public static Builder builder(String studyId) {
    return new Builder(studyId);
  }

  /**
   * Gets study id.
   *
   * @return study id
   */
  public String getStudyId() {
    return studyId;
  }

  /**
   * Gets blowdown sources.
   *
   * @return immutable source list
   */
  public List<BlowdownSource> getSources() {
    return sources;
  }

  /**
   * Gets P&amp;ID topology evidence.
   *
   * @return topology evidence or null
   */
  public PidTopologyEvidence getPidTopologyEvidence() {
    return pidTopologyEvidence;
  }

  /**
   * Gets line/equipment-list evidence.
   *
   * @return line/equipment-list evidence or null
   */
  public LineEquipmentListEvidence getLineEquipmentListEvidence() {
    return lineEquipmentListEvidence;
  }

  /**
   * Gets all evidence references.
   *
   * @return immutable evidence list
   */
  public List<SafetyEvidenceReference> getAllEvidenceReferences() {
    List<SafetyEvidenceReference> references = new ArrayList<SafetyEvidenceReference>();
    references.addAll(sourceDocumentEvidence);
    references.addAll(pipingSpecificationEvidence);
    references.addAll(processEvidence);
    references.addAll(reliefValveEvidence);
    references.addAll(flareEvidence);
    references.addAll(fireScenarioEvidence);
    for (BlowdownSource source : sources) {
      references.addAll(source.getEvidenceReferences());
    }
    return Collections.unmodifiableList(references);
  }

  /**
   * Creates the calculation readiness verdict.
   *
   * @return readiness result
   */
  public SafetyStudyReadiness readiness() {
    SafetyStudyReadiness.Builder readiness = SafetyStudyReadiness.builder();
    for (SafetyEvidenceReference reference : getAllEvidenceReferences()) {
      readiness.addEvidenceReference(reference);
    }
    if (pidTopologyEvidence != null) {
      readiness.merge(pidTopologyEvidence.readiness());
    }
    if (lineEquipmentListEvidence != null) {
      readiness.merge(lineEquipmentListEvidence.readiness());
    }
    if (sources.isEmpty()) {
      readiness.addBlocker("blowdown_sources",
          "No protected equipment or blowdown source is defined.",
          "Build BlowdownSource entries from reviewed equipment list, inventory, BDV/orifice, and fluid evidence.");
    }
    for (BlowdownSource source : sources) {
      source.addReadinessFindings(readiness);
    }
    if (!sourceDiagramsReviewed) {
      readiness.addWarning("source_diagrams",
          "Detailed source diagram evidence has not been marked reviewed.",
          "Read the relevant drawing pages and record the reviewed equipment, line, PSV, BDV, and flare-path tags.");
    }
    if (!pidTopologyVerified
        && (pidTopologyEvidence == null || !pidTopologyEvidence.isSimulationReady())) {
      readiness.addWarning("pid_topology",
          "P&ID topology is not verified for the blowdown/flare boundary.",
          "Trace protected equipment, BDVs, PSVs, flare header tie-ins, isolation valves, and battery limits.");
    }
    if (!lineEquipmentListsReviewed
        && (lineEquipmentListEvidence == null || !lineEquipmentListEvidence.isSimulationReady())) {
      readiness.addWarning("line_equipment_list",
          "Line and equipment lists are not reviewed for model construction.",
          "Review line-list and equipment-list rows before treating the dynamic model as design grade.");
    }
    if (!pipingSpecificationRowsReviewed) {
      readiness.addWarning("piping_specification",
          "Piping and valve specification rows are not reviewed and joined.",
          "Resolve the applicable piping/valve specification revision and map pipe, valve, and material rows before design-grade use.");
    }
    if (!vesselInventoryReviewed) {
      readiness.addWarning("inventory", "Protected-equipment inventory is not marked reviewed.",
          "Confirm vessel/line volumes, operating fill, fluid basis, and connected inventory.");
    }
    if (!valveSizingBasisReviewed) {
      readiness.addWarning("bdv", "BDV/orifice sizing basis is not marked reviewed.",
          "Confirm BDV equivalent diameter/Cd/Cv, opening philosophy, and flare-backpressure assumptions.");
    }
    if (!psvBasisReviewed) {
      readiness.addWarning("psv", "PSV sizing basis is not marked reviewed.",
          "Confirm PSV set pressure, overpressure basis, backpressure correction, and rupture-disk/bellows status.");
    }
    if (!flareSystemBasisReviewed) {
      readiness.addWarning("flare", "Flare system geometry/capacity basis is not marked reviewed.",
          "Confirm header diameter/pressure, flare-tip size, capacity, radiation model, and disposal-system limits.");
    }
    if (!fireCaseReviewed) {
      readiness.addWarning("fire_case", "Fire-case heat input basis is not marked reviewed.",
          "Confirm wetted area, drainage/firefighting status, PFP, and API 521/ISO 23251 fire assumptions.");
    }
    if (!standardsReviewed) {
      readiness.addWarning("standards", "Standards basis has not been marked reviewed.",
          "Review API 520/521, ISO 23251, NORSOK S-001/P-002, and applicable piping-design requirements.");
    }
    for (String gap : gaps) {
      readiness.addWarning("gap", gap,
          "Close or explicitly accept this gap before design-grade use.");
    }
    if (humanReviewRequired) {
      readiness.addInfo("human_review",
          "Qualified engineering review is required for final acceptance.",
          "Use the runner output as an auditable handoff into formal process-safety verification.");
    }
    return readiness.build();
  }

  /**
   * Gets header diameter.
   *
   * @return header internal diameter in m, or NaN if not configured
   */
  public double getHeaderDiameterM() {
    return headerDiameterM;
  }

  /**
   * Gets header pressure.
   *
   * @return header pressure in bara
   */
  public double getHeaderPressureBara() {
    return headerPressureBara;
  }

  /**
   * Gets header temperature.
   *
   * @return header temperature in K
   */
  public double getHeaderTemperatureK() {
    return headerTemperatureK;
  }

  /**
   * Gets header molar mass.
   *
   * @return header molar mass in kg/mol
   */
  public double getHeaderMolarMassKgPerMol() {
    return headerMolarMassKgPerMol;
  }

  /**
   * Gets header isentropic exponent.
   *
   * @return gamma value
   */
  public double getHeaderGamma() {
    return headerGamma;
  }

  /**
   * Gets maximum allowable header Mach number.
   *
   * @return Mach limit
   */
  public double getMaxAllowableHeaderMach() {
    return maxAllowableHeaderMach;
  }

  /**
   * Gets flare tip diameter.
   *
   * @return flare tip diameter in m
   */
  public double getFlareTipDiameterM() {
    return flareTipDiameterM;
  }

  /**
   * Gets flare flame height.
   *
   * @return flame height in m
   */
  public double getFlareFlameHeightM() {
    return flareFlameHeightM;
  }

  /**
   * Gets flare radiant fraction.
   *
   * @return radiant fraction
   */
  public double getFlareRadiantFraction() {
    return flareRadiantFraction;
  }

  /**
   * Gets flare design heat-duty capacity.
   *
   * @return design heat duty in W, or NaN if not configured
   */
  public double getFlareDesignHeatDutyW() {
    return flareDesignHeatDutyW;
  }

  /**
   * Gets flare design mass-flow capacity.
   *
   * @return design mass flow in kg/s, or NaN if not configured
   */
  public double getFlareDesignMassFlowKgPerS() {
    return flareDesignMassFlowKgPerS;
  }

  /**
   * Gets flare design molar-flow capacity.
   *
   * @return design molar flow in mole/s, or NaN if not configured
   */
  public double getFlareDesignMolarFlowMolePerS() {
    return flareDesignMolarFlowMolePerS;
  }

  /**
   * Checks if standards evidence has been reviewed.
   *
   * @return true if standards reviewed
   */
  public boolean isStandardsReviewed() {
    return standardsReviewed;
  }

  /**
   * Converts data source to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "dynamic_blowdown_flare_data_source.v1");
    map.put("studyId", studyId);
    List<Map<String, Object>> sourceMaps = new ArrayList<Map<String, Object>>();
    for (BlowdownSource source : sources) {
      sourceMaps.add(source.toMap());
    }
    map.put("sources", sourceMaps);
    map.put("pidTopologyEvidence",
        pidTopologyEvidence == null ? null : pidTopologyEvidence.toMap());
    map.put("lineEquipmentListEvidence",
        lineEquipmentListEvidence == null ? null : lineEquipmentListEvidence.toMap());
    map.put("sourceDocumentEvidence", evidenceMaps(sourceDocumentEvidence));
    map.put("pipingSpecificationEvidence", evidenceMaps(pipingSpecificationEvidence));
    map.put("processEvidence", evidenceMaps(processEvidence));
    map.put("reliefValveEvidence", evidenceMaps(reliefValveEvidence));
    map.put("flareEvidence", evidenceMaps(flareEvidence));
    map.put("fireScenarioEvidence", evidenceMaps(fireScenarioEvidence));
    map.put("assumptions", assumptions);
    map.put("gaps", gaps);
    map.put("flareHeader", headerMap());
    map.put("flareSystem", flareMap());
    map.put("sourceDiagramsReviewed", Boolean.valueOf(sourceDiagramsReviewed));
    map.put("pidTopologyVerified", Boolean.valueOf(pidTopologyVerified));
    map.put("lineEquipmentListsReviewed", Boolean.valueOf(lineEquipmentListsReviewed));
    map.put("pipingSpecificationRowsReviewed", Boolean.valueOf(pipingSpecificationRowsReviewed));
    map.put("vesselInventoryReviewed", Boolean.valueOf(vesselInventoryReviewed));
    map.put("valveSizingBasisReviewed", Boolean.valueOf(valveSizingBasisReviewed));
    map.put("psvBasisReviewed", Boolean.valueOf(psvBasisReviewed));
    map.put("flareSystemBasisReviewed", Boolean.valueOf(flareSystemBasisReviewed));
    map.put("fireCaseReviewed", Boolean.valueOf(fireCaseReviewed));
    map.put("standardsReviewed", Boolean.valueOf(standardsReviewed));
    map.put("humanReviewRequired", Boolean.valueOf(humanReviewRequired));
    map.put("readiness", readiness().toMap());
    return map;
  }

  /**
   * Converts data source to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toMap());
  }

  /**
   * Builds a header map.
   *
   * @return header map
   */
  private Map<String, Object> headerMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("diameterM", finiteOrNull(headerDiameterM));
    map.put("pressureBara", Double.valueOf(headerPressureBara));
    map.put("temperatureK", Double.valueOf(headerTemperatureK));
    map.put("molarMassKgPerMol", Double.valueOf(headerMolarMassKgPerMol));
    map.put("gamma", Double.valueOf(headerGamma));
    map.put("maxAllowableMach", Double.valueOf(maxAllowableHeaderMach));
    return map;
  }

  /**
   * Builds a flare-system map.
   *
   * @return flare-system map
   */
  private Map<String, Object> flareMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("tipDiameterM", Double.valueOf(flareTipDiameterM));
    map.put("flameHeightM", Double.valueOf(flareFlameHeightM));
    map.put("radiantFraction", Double.valueOf(flareRadiantFraction));
    map.put("designHeatDutyW", finiteOrNull(flareDesignHeatDutyW));
    map.put("designMassFlowKgPerS", finiteOrNull(flareDesignMassFlowKgPerS));
    map.put("designMolarFlowMolePerS", finiteOrNull(flareDesignMolarFlowMolePerS));
    return map;
  }

  /**
   * Converts evidence references to maps.
   *
   * @param references references to convert
   * @return map list
   */
  private static List<Map<String, Object>> evidenceMaps(List<SafetyEvidenceReference> references) {
    List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
    for (SafetyEvidenceReference reference : references) {
      maps.add(reference.toMap());
    }
    return maps;
  }

  /**
   * Copies evidence references.
   *
   * @param references references to copy
   * @return immutable copy
   */
  private static List<SafetyEvidenceReference> immutableEvidence(
      List<SafetyEvidenceReference> references) {
    return Collections.unmodifiableList(new ArrayList<SafetyEvidenceReference>(references));
  }

  /**
   * Copies text values.
   *
   * @param values values to copy
   * @return immutable copy
   */
  private static List<String> immutableText(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  /**
   * Normalizes nullable text.
   *
   * @param value text value
   * @return trimmed text or empty string
   */
  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Converts finite values to boxed values and non-finite values to null.
   *
   * @param value numeric value
   * @return boxed value or null
   */
  private static Object finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }

  /** One protected equipment source connected to blowdown or relief disposal. */
  public static final class BlowdownSource implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sourceId;
    private final String equipmentTag;
    private final SystemInterface fluid;
    private final double vesselVolumeM3;
    private final double orificeDiameterM;
    private final double dischargeCoefficient;
    private final double backPressureBara;
    private final double stopPressureBara;
    private final double fireHeatInputW;
    private final double wettedAreaM2;
    private final boolean adequateDrainage;
    private final boolean fireFightingAvailable;
    private final double wallMassKg;
    private final double wallAreaM2;
    private final double wallSpecificHeatJPerKgK;
    private final double wallHeatTransferCoeffWPerM2K;
    private final double psvSetPressureBara;
    private final double psvOverpressureFraction;
    private final boolean balancedBellowsPsv;
    private final boolean ruptureDiskInstalled;
    private final List<SafetyEvidenceReference> evidenceReferences;

    /**
     * Creates a blowdown source.
     *
     * @param builder populated builder
     */
    private BlowdownSource(SourceBuilder builder) {
      this.sourceId = clean(builder.sourceId);
      this.equipmentTag = clean(builder.equipmentTag);
      this.fluid = builder.fluid;
      this.vesselVolumeM3 = builder.vesselVolumeM3;
      this.orificeDiameterM = builder.orificeDiameterM;
      this.dischargeCoefficient = builder.dischargeCoefficient;
      this.backPressureBara = builder.backPressureBara;
      this.stopPressureBara = builder.stopPressureBara;
      this.fireHeatInputW = builder.fireHeatInputW;
      this.wettedAreaM2 = builder.wettedAreaM2;
      this.adequateDrainage = builder.adequateDrainage;
      this.fireFightingAvailable = builder.fireFightingAvailable;
      this.wallMassKg = builder.wallMassKg;
      this.wallAreaM2 = builder.wallAreaM2;
      this.wallSpecificHeatJPerKgK = builder.wallSpecificHeatJPerKgK;
      this.wallHeatTransferCoeffWPerM2K = builder.wallHeatTransferCoeffWPerM2K;
      this.psvSetPressureBara = builder.psvSetPressureBara;
      this.psvOverpressureFraction = builder.psvOverpressureFraction;
      this.balancedBellowsPsv = builder.balancedBellowsPsv;
      this.ruptureDiskInstalled = builder.ruptureDiskInstalled;
      this.evidenceReferences = immutableEvidence(builder.evidenceReferences);
    }

    /**
     * Creates a source builder.
     *
     * @param sourceId source id
     * @param fluid initial source fluid at operating pressure and temperature
     * @return source builder
     */
    public static SourceBuilder builder(String sourceId, SystemInterface fluid) {
      return new SourceBuilder(sourceId, fluid);
    }

    /**
     * Gets source id.
     *
     * @return source id
     */
    public String getSourceId() {
      return sourceId;
    }

    /**
     * Gets equipment tag.
     *
     * @return equipment tag
     */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /**
     * Gets fluid.
     *
     * @return fluid or null
     */
    public SystemInterface getFluid() {
      return fluid;
    }

    /**
     * Gets vessel volume.
     *
     * @return vessel volume in m3
     */
    public double getVesselVolumeM3() {
      return vesselVolumeM3;
    }

    /**
     * Gets blowdown orifice diameter.
     *
     * @return orifice diameter in m
     */
    public double getOrificeDiameterM() {
      return orificeDiameterM;
    }

    /**
     * Gets discharge coefficient.
     *
     * @return discharge coefficient
     */
    public double getDischargeCoefficient() {
      return dischargeCoefficient;
    }

    /**
     * Gets backpressure.
     *
     * @return backpressure in bara
     */
    public double getBackPressureBara() {
      return backPressureBara;
    }

    /**
     * Gets stop pressure.
     *
     * @return stop pressure in bara
     */
    public double getStopPressureBara() {
      return stopPressureBara;
    }

    /**
     * Gets configured or API 521 calculated fire heat input.
     *
     * @return fire heat input in W, or 0 if not configured
     */
    public double getEffectiveFireHeatInputW() {
      if (Double.isFinite(fireHeatInputW) && fireHeatInputW >= 0.0) {
        return fireHeatInputW;
      }
      if (Double.isFinite(wettedAreaM2) && wettedAreaM2 > 0.0) {
        return neqsim.process.util.fire.ReliefValveSizing.calculateAPI521FireHeatInput(wettedAreaM2,
            adequateDrainage, fireFightingAvailable);
      }
      return 0.0;
    }

    /**
     * Checks if wall model inputs are configured.
     *
     * @return true if wall model can be enabled
     */
    public boolean hasWallModel() {
      return wallMassKg > 0.0 && wallAreaM2 > 0.0 && wallSpecificHeatJPerKgK > 0.0
          && wallHeatTransferCoeffWPerM2K > 0.0;
    }

    /**
     * Gets wall mass.
     *
     * @return wall mass in kg
     */
    public double getWallMassKg() {
      return wallMassKg;
    }

    /**
     * Gets wall area.
     *
     * @return wall area in m2
     */
    public double getWallAreaM2() {
      return wallAreaM2;
    }

    /**
     * Gets wall specific heat.
     *
     * @return specific heat in J/kg/K
     */
    public double getWallSpecificHeatJPerKgK() {
      return wallSpecificHeatJPerKgK;
    }

    /**
     * Gets wall heat-transfer coefficient.
     *
     * @return heat-transfer coefficient in W/m2/K
     */
    public double getWallHeatTransferCoeffWPerM2K() {
      return wallHeatTransferCoeffWPerM2K;
    }

    /**
     * Checks if PSV sizing inputs are available.
     *
     * @return true if PSV sizing can be calculated
     */
    public boolean hasPsvSizingBasis() {
      return Double.isFinite(psvSetPressureBara) && psvSetPressureBara > 0.0;
    }

    /**
     * Gets PSV set pressure.
     *
     * @return PSV set pressure in bara, or NaN if not configured
     */
    public double getPsvSetPressureBara() {
      return psvSetPressureBara;
    }

    /**
     * Gets PSV overpressure fraction.
     *
     * @return overpressure fraction
     */
    public double getPsvOverpressureFraction() {
      return psvOverpressureFraction;
    }

    /**
     * Checks if PSV is balanced bellows.
     *
     * @return true if balanced bellows
     */
    public boolean isBalancedBellowsPsv() {
      return balancedBellowsPsv;
    }

    /**
     * Checks if rupture disk is installed upstream of PSV.
     *
     * @return true if rupture disk is installed
     */
    public boolean isRuptureDiskInstalled() {
      return ruptureDiskInstalled;
    }

    /**
     * Gets source evidence references.
     *
     * @return immutable references
     */
    public List<SafetyEvidenceReference> getEvidenceReferences() {
      return evidenceReferences;
    }

    /**
     * Adds source-specific readiness findings.
     *
     * @param readiness readiness builder
     */
    private void addReadinessFindings(SafetyStudyReadiness.Builder readiness) {
      String category = "source:" + sourceId;
      if (fluid == null) {
        readiness.addBlocker(category, "Initial fluid is missing.",
            "Build a NeqSim fluid from reviewed composition and operating state evidence.");
      }
      if (vesselVolumeM3 <= 0.0 || !Double.isFinite(vesselVolumeM3)) {
        readiness.addBlocker(category,
            "Vessel or protected inventory volume is missing or invalid.",
            "Provide internal volume from equipment list, datasheet, or governed inventory calculation.");
      }
      if (orificeDiameterM <= 0.0 || !Double.isFinite(orificeDiameterM)) {
        readiness.addBlocker(category, "BDV/orifice equivalent diameter is missing or invalid.",
            "Provide BDV/orifice diameter, area, or equivalent Cv/Cd basis.");
      }
      if (dischargeCoefficient <= 0.0 || !Double.isFinite(dischargeCoefficient)) {
        readiness.addBlocker(category, "Discharge coefficient is missing or invalid.",
            "Provide a governed Cd value or valve/orifice sizing basis.");
      }
      if (backPressureBara <= 0.0 || !Double.isFinite(backPressureBara)) {
        readiness.addBlocker(category, "Flare/disposal backpressure is missing or invalid.",
            "Provide flare header or disposal-system pressure basis.");
      }
      if (!hasPsvSizingBasis()) {
        readiness.addWarning(category,
            "PSV set pressure is not configured; PSV orifice sizing will be skipped.",
            "Provide PSV set pressure and correction basis to include API 520/521 PSV sizing.");
      }
      if (!Double.isFinite(fireHeatInputW)
          && !(Double.isFinite(wettedAreaM2) && wettedAreaM2 > 0.0)) {
        readiness.addWarning(category,
            "Fire heat input is not configured; adiabatic blowdown will be used.",
            "Provide API 521 wetted area/drainage/firefighting evidence or a reviewed fire heat input.");
      }
      if (!hasWallModel()) {
        readiness.addInfo(category, "Wall thermal model is not fully configured.",
            "Add wall mass, area, specific heat, and heat-transfer coefficient when MDMT or wall temperature matters.");
      }
    }

    /**
     * Converts source to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("sourceId", sourceId);
      map.put("equipmentTag", equipmentTag);
      map.put("fluid", fluidSummary(fluid));
      map.put("vesselVolumeM3", finiteOrNull(vesselVolumeM3));
      map.put("orificeDiameterM", finiteOrNull(orificeDiameterM));
      map.put("dischargeCoefficient", finiteOrNull(dischargeCoefficient));
      map.put("backPressureBara", finiteOrNull(backPressureBara));
      map.put("stopPressureBara", finiteOrNull(stopPressureBara));
      map.put("fireHeatInputW", finiteOrNull(fireHeatInputW));
      map.put("wettedAreaM2", finiteOrNull(wettedAreaM2));
      map.put("adequateDrainage", Boolean.valueOf(adequateDrainage));
      map.put("fireFightingAvailable", Boolean.valueOf(fireFightingAvailable));
      map.put("effectiveFireHeatInputW", Double.valueOf(getEffectiveFireHeatInputW()));
      map.put("wallModelEnabled", Boolean.valueOf(hasWallModel()));
      map.put("wallMassKg", finiteOrNull(wallMassKg));
      map.put("wallAreaM2", finiteOrNull(wallAreaM2));
      map.put("wallSpecificHeatJPerKgK", finiteOrNull(wallSpecificHeatJPerKgK));
      map.put("wallHeatTransferCoeffWPerM2K", finiteOrNull(wallHeatTransferCoeffWPerM2K));
      map.put("psvSetPressureBara", finiteOrNull(psvSetPressureBara));
      map.put("psvOverpressureFraction", Double.valueOf(psvOverpressureFraction));
      map.put("balancedBellowsPsv", Boolean.valueOf(balancedBellowsPsv));
      map.put("ruptureDiskInstalled", Boolean.valueOf(ruptureDiskInstalled));
      map.put("evidenceReferences", evidenceMaps(evidenceReferences));
      return map;
    }

    /**
     * Builds a compact fluid summary.
     *
     * @param fluid fluid to summarize
     * @return fluid summary map or null
     */
    private static Map<String, Object> fluidSummary(SystemInterface fluid) {
      if (fluid == null) {
        return null;
      }
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("temperatureK", Double.valueOf(fluid.getTemperature()));
      map.put("pressureBara", Double.valueOf(fluid.getPressure()));
      map.put("molarMassKgPerMol", Double.valueOf(fluid.getMolarMass()));
      map.put("componentCount", Integer.valueOf(fluid.getNumberOfComponents()));
      return map;
    }
  }

  /** Builder for {@link BlowdownSource}. */
  public static final class SourceBuilder {
    private final String sourceId;
    private final SystemInterface fluid;
    private String equipmentTag = "";
    private double vesselVolumeM3 = Double.NaN;
    private double orificeDiameterM = Double.NaN;
    private double dischargeCoefficient = 0.72;
    private double backPressureBara = 1.5;
    private double stopPressureBara = 1.5;
    private double fireHeatInputW = Double.NaN;
    private double wettedAreaM2 = Double.NaN;
    private boolean adequateDrainage = true;
    private boolean fireFightingAvailable = true;
    private double wallMassKg = Double.NaN;
    private double wallAreaM2 = Double.NaN;
    private double wallSpecificHeatJPerKgK = 470.0;
    private double wallHeatTransferCoeffWPerM2K = 50.0;
    private double psvSetPressureBara = Double.NaN;
    private double psvOverpressureFraction = 0.21;
    private boolean balancedBellowsPsv;
    private boolean ruptureDiskInstalled;
    private final List<SafetyEvidenceReference> evidenceReferences =
        new ArrayList<SafetyEvidenceReference>();

    /**
     * Creates a builder.
     *
     * @param sourceId source id
     * @param fluid initial source fluid
     */
    private SourceBuilder(String sourceId, SystemInterface fluid) {
      this.sourceId = sourceId;
      this.fluid = fluid;
    }

    /**
     * Sets equipment tag.
     *
     * @param equipmentTag equipment tag
     * @return this builder
     */
    public SourceBuilder equipmentTag(String equipmentTag) {
      this.equipmentTag = equipmentTag;
      return this;
    }

    /**
     * Sets vessel volume.
     *
     * @param vesselVolumeM3 vessel volume in m3
     * @return this builder
     */
    public SourceBuilder vesselVolumeM3(double vesselVolumeM3) {
      this.vesselVolumeM3 = vesselVolumeM3;
      return this;
    }

    /**
     * Sets BDV/orifice equivalent diameter.
     *
     * @param orificeDiameterM orifice diameter in m
     * @return this builder
     */
    public SourceBuilder orificeDiameterM(double orificeDiameterM) {
      this.orificeDiameterM = orificeDiameterM;
      return this;
    }

    /**
     * Sets discharge coefficient.
     *
     * @param dischargeCoefficient discharge coefficient
     * @return this builder
     */
    public SourceBuilder dischargeCoefficient(double dischargeCoefficient) {
      this.dischargeCoefficient = dischargeCoefficient;
      return this;
    }

    /**
     * Sets flare/disposal backpressure.
     *
     * @param backPressureBara backpressure in bara
     * @return this builder
     */
    public SourceBuilder backPressureBara(double backPressureBara) {
      this.backPressureBara = backPressureBara;
      return this;
    }

    /**
     * Sets stop pressure.
     *
     * @param stopPressureBara stop pressure in bara
     * @return this builder
     */
    public SourceBuilder stopPressureBara(double stopPressureBara) {
      this.stopPressureBara = stopPressureBara;
      return this;
    }

    /**
     * Sets direct fire heat input.
     *
     * @param fireHeatInputW fire heat input in W
     * @return this builder
     */
    public SourceBuilder fireHeatInputW(double fireHeatInputW) {
      this.fireHeatInputW = fireHeatInputW;
      return this;
    }

    /**
     * Sets API 521 fire-case wetted-area basis.
     *
     * @param wettedAreaM2 wetted area in m2
     * @param adequateDrainage true when adequate drainage exists
     * @param fireFightingAvailable true when firefighting systems are available
     * @return this builder
     */
    public SourceBuilder api521FireCase(double wettedAreaM2, boolean adequateDrainage,
        boolean fireFightingAvailable) {
      this.wettedAreaM2 = wettedAreaM2;
      this.adequateDrainage = adequateDrainage;
      this.fireFightingAvailable = fireFightingAvailable;
      return this;
    }

    /**
     * Sets wall thermal model.
     *
     * @param wallMassKg wall mass in kg
     * @param wallAreaM2 wall area in m2
     * @param wallSpecificHeatJPerKgK wall specific heat in J/kg/K
     * @param wallHeatTransferCoeffWPerM2K heat-transfer coefficient in W/m2/K
     * @return this builder
     */
    public SourceBuilder wallModel(double wallMassKg, double wallAreaM2,
        double wallSpecificHeatJPerKgK, double wallHeatTransferCoeffWPerM2K) {
      this.wallMassKg = wallMassKg;
      this.wallAreaM2 = wallAreaM2;
      this.wallSpecificHeatJPerKgK = wallSpecificHeatJPerKgK;
      this.wallHeatTransferCoeffWPerM2K = wallHeatTransferCoeffWPerM2K;
      return this;
    }

    /**
     * Sets PSV sizing basis.
     *
     * @param setPressureBara PSV set pressure in bara
     * @param overpressureFraction overpressure fraction
     * @param balancedBellows true for balanced-bellows PSV
     * @param ruptureDisk true when rupture disk is installed upstream
     * @return this builder
     */
    public SourceBuilder psvBasis(double setPressureBara, double overpressureFraction,
        boolean balancedBellows, boolean ruptureDisk) {
      this.psvSetPressureBara = setPressureBara;
      this.psvOverpressureFraction = overpressureFraction;
      this.balancedBellowsPsv = balancedBellows;
      this.ruptureDiskInstalled = ruptureDisk;
      return this;
    }

    /**
     * Adds evidence reference.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public SourceBuilder evidenceReference(SafetyEvidenceReference reference) {
      if (reference != null) {
        evidenceReferences.add(reference);
      }
      return this;
    }

    /**
     * Builds a source.
     *
     * @return blowdown source
     */
    public BlowdownSource build() {
      if (clean(sourceId).isEmpty()) {
        throw new IllegalArgumentException("sourceId must not be empty");
      }
      return new BlowdownSource(this);
    }
  }

  /** Builder for {@link DynamicBlowdownFlareStudyDataSource}. */
  public static final class Builder {
    private final String studyId;
    private final List<BlowdownSource> sources = new ArrayList<BlowdownSource>();
    private PidTopologyEvidence pidTopologyEvidence;
    private LineEquipmentListEvidence lineEquipmentListEvidence;
    private final List<SafetyEvidenceReference> sourceDocumentEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> pipingSpecificationEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> processEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> reliefValveEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> flareEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> fireScenarioEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<String> assumptions = new ArrayList<String>();
    private final List<String> gaps = new ArrayList<String>();
    private double headerDiameterM = Double.NaN;
    private double headerPressureBara = 1.5;
    private double headerTemperatureK = 288.15;
    private double headerMolarMassKgPerMol = 0.020;
    private double headerGamma = 1.30;
    private double maxAllowableHeaderMach = 0.70;
    private double flareTipDiameterM = 0.8;
    private double flareFlameHeightM = 50.0;
    private double flareRadiantFraction = 0.20;
    private double flareDesignHeatDutyW = Double.NaN;
    private double flareDesignMassFlowKgPerS = Double.NaN;
    private double flareDesignMolarFlowMolePerS = Double.NaN;
    private boolean sourceDiagramsReviewed;
    private boolean pidTopologyVerified;
    private boolean lineEquipmentListsReviewed;
    private boolean pipingSpecificationRowsReviewed;
    private boolean vesselInventoryReviewed;
    private boolean valveSizingBasisReviewed;
    private boolean psvBasisReviewed;
    private boolean flareSystemBasisReviewed;
    private boolean fireCaseReviewed;
    private boolean standardsReviewed;
    private boolean humanReviewRequired = true;

    /**
     * Creates a builder.
     *
     * @param studyId study id
     */
    private Builder(String studyId) {
      this.studyId = studyId;
    }

    /**
     * Adds a blowdown source.
     *
     * @param source source to add
     * @return this builder
     */
    public Builder addSource(BlowdownSource source) {
      if (source != null) {
        sources.add(source);
      }
      return this;
    }

    /**
     * Sets topology evidence.
     *
     * @param pidTopologyEvidence topology evidence
     * @return this builder
     */
    public Builder pidTopologyEvidence(PidTopologyEvidence pidTopologyEvidence) {
      this.pidTopologyEvidence = pidTopologyEvidence;
      return this;
    }

    /**
     * Sets line/equipment-list evidence.
     *
     * @param evidence line/equipment-list evidence
     * @return this builder
     */
    public Builder lineEquipmentListEvidence(LineEquipmentListEvidence evidence) {
      this.lineEquipmentListEvidence = evidence;
      return this;
    }

    /**
     * Adds source-document evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addSourceDocumentEvidence(SafetyEvidenceReference reference) {
      addEvidence(sourceDocumentEvidence, reference);
      return this;
    }

    /**
     * Adds piping-specification evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addPipingSpecificationEvidence(SafetyEvidenceReference reference) {
      addEvidence(pipingSpecificationEvidence, reference);
      return this;
    }

    /**
     * Adds process evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addProcessEvidence(SafetyEvidenceReference reference) {
      addEvidence(processEvidence, reference);
      return this;
    }

    /**
     * Adds relief-valve evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addReliefValveEvidence(SafetyEvidenceReference reference) {
      addEvidence(reliefValveEvidence, reference);
      return this;
    }

    /**
     * Adds flare evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addFlareEvidence(SafetyEvidenceReference reference) {
      addEvidence(flareEvidence, reference);
      return this;
    }

    /**
     * Adds fire-scenario evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addFireScenarioEvidence(SafetyEvidenceReference reference) {
      addEvidence(fireScenarioEvidence, reference);
      return this;
    }

    /**
     * Adds an assumption.
     *
     * @param assumption assumption text
     * @return this builder
     */
    public Builder addAssumption(String assumption) {
      addText(assumptions, assumption);
      return this;
    }

    /**
     * Adds a gap.
     *
     * @param gap gap text
     * @return this builder
     */
    public Builder addGap(String gap) {
      addText(gaps, gap);
      return this;
    }

    /**
     * Sets flare header basis.
     *
     * @param diameterM header internal diameter in m
     * @param pressureBara header pressure in bara
     * @param temperatureK header temperature in K
     * @param molarMassKgPerMol header molar mass in kg/mol
     * @param gamma isentropic exponent
     * @return this builder
     */
    public Builder flareHeader(double diameterM, double pressureBara, double temperatureK,
        double molarMassKgPerMol, double gamma) {
      this.headerDiameterM = diameterM;
      this.headerPressureBara = pressureBara;
      this.headerTemperatureK = temperatureK;
      this.headerMolarMassKgPerMol = molarMassKgPerMol;
      this.headerGamma = gamma;
      return this;
    }

    /**
     * Sets maximum allowable header Mach number.
     *
     * @param maxAllowableHeaderMach Mach limit
     * @return this builder
     */
    public Builder maxAllowableHeaderMach(double maxAllowableHeaderMach) {
      this.maxAllowableHeaderMach = maxAllowableHeaderMach;
      return this;
    }

    /**
     * Sets flare geometry.
     *
     * @param tipDiameterM flare tip diameter in m
     * @param flameHeightM effective flame height in m
     * @param radiantFraction radiant fraction
     * @return this builder
     */
    public Builder flareGeometry(double tipDiameterM, double flameHeightM, double radiantFraction) {
      this.flareTipDiameterM = tipDiameterM;
      this.flareFlameHeightM = flameHeightM;
      this.flareRadiantFraction = radiantFraction;
      return this;
    }

    /**
     * Sets flare design capacities.
     *
     * @param heatDutyW heat duty capacity in W
     * @param massFlowKgPerS mass-flow capacity in kg/s
     * @param molarFlowMolePerS molar-flow capacity in mole/s
     * @return this builder
     */
    public Builder flareDesignCapacity(double heatDutyW, double massFlowKgPerS,
        double molarFlowMolePerS) {
      this.flareDesignHeatDutyW = heatDutyW;
      this.flareDesignMassFlowKgPerS = massFlowKgPerS;
      this.flareDesignMolarFlowMolePerS = molarFlowMolePerS;
      return this;
    }

    /**
     * Marks source diagram review state.
     *
     * @param reviewed true if reviewed
     * @return this builder
     */
    public Builder sourceDiagramsReviewed(boolean reviewed) {
      this.sourceDiagramsReviewed = reviewed;
      return this;
    }

    /**
     * Marks topology review state.
     *
     * @param verified true if verified
     * @return this builder
     */
    public Builder pidTopologyVerified(boolean verified) {
      this.pidTopologyVerified = verified;
      return this;
    }

    /**
     * Marks line/equipment-list review state.
     *
     * @param reviewed true if reviewed
     * @return this builder
     */
    public Builder lineEquipmentListsReviewed(boolean reviewed) {
      this.lineEquipmentListsReviewed = reviewed;
      return this;
    }

    /**
     * Marks piping-specification row review state.
     *
     * @param reviewed true if reviewed and joined
     * @return this builder
     */
    public Builder pipingSpecificationRowsReviewed(boolean reviewed) {
      this.pipingSpecificationRowsReviewed = reviewed;
      return this;
    }

    /**
     * Marks vessel inventory review state.
     *
     * @param reviewed true if reviewed
     * @return this builder
     */
    public Builder vesselInventoryReviewed(boolean reviewed) {
      this.vesselInventoryReviewed = reviewed;
      return this;
    }

    /**
     * Marks BDV/valve sizing review state.
     *
     * @param reviewed true if reviewed
     * @return this builder
     */
    public Builder valveSizingBasisReviewed(boolean reviewed) {
      this.valveSizingBasisReviewed = reviewed;
      return this;
    }

    /**
     * Marks PSV review state.
     *
     * @param reviewed true if reviewed
     * @return this builder
     */
    public Builder psvBasisReviewed(boolean reviewed) {
      this.psvBasisReviewed = reviewed;
      return this;
    }

    /**
     * Marks flare-system review state.
     *
     * @param reviewed true if reviewed
     * @return this builder
     */
    public Builder flareSystemBasisReviewed(boolean reviewed) {
      this.flareSystemBasisReviewed = reviewed;
      return this;
    }

    /**
     * Marks fire-case review state.
     *
     * @param reviewed true if reviewed
     * @return this builder
     */
    public Builder fireCaseReviewed(boolean reviewed) {
      this.fireCaseReviewed = reviewed;
      return this;
    }

    /**
     * Marks standards review state.
     *
     * @param reviewed true if reviewed
     * @return this builder
     */
    public Builder standardsReviewed(boolean reviewed) {
      this.standardsReviewed = reviewed;
      return this;
    }

    /**
     * Sets human-review requirement.
     *
     * @param required true if human review is required
     * @return this builder
     */
    public Builder humanReviewRequired(boolean required) {
      this.humanReviewRequired = required;
      return this;
    }

    /**
     * Builds data source.
     *
     * @return dynamic blowdown/flare data source
     */
    public DynamicBlowdownFlareStudyDataSource build() {
      if (clean(studyId).isEmpty()) {
        throw new IllegalArgumentException("studyId must not be empty");
      }
      return new DynamicBlowdownFlareStudyDataSource(this);
    }

    /**
     * Adds evidence when not null.
     *
     * @param target target list
     * @param reference evidence reference
     */
    private static void addEvidence(List<SafetyEvidenceReference> target,
        SafetyEvidenceReference reference) {
      if (reference != null) {
        target.add(reference);
      }
    }

    /**
     * Adds non-empty text.
     *
     * @param target target list
     * @param value text value
     */
    private static void addText(List<String> target, String value) {
      if (!clean(value).isEmpty()) {
        target.add(clean(value));
      }
    }
  }
}
