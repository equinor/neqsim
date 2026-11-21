package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.SafetyFunctionDesign;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.depressurization.DepressurizationSimulator;
import neqsim.process.safety.risk.sis.nog070.Nog070SifType;
import neqsim.process.safety.risk.sis.nog070.Nog070SilCatalogue;
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Runs physics-based screening safety studies on the safeguards of a synthesized P&amp;ID and writes the results back
 * onto the P&amp;ID elements.
 *
 * <p>
 * The studies use the converged process simulation as the physical basis:
 * </p>
 * <ul>
 * <li>pressure envelope: proposed design pressure and temperature of each protected unit from its operating point;</li>
 * <li>relief (API 520/521): blocked outlet, external fire (wetted area, adequate drainage) and gas blow-by through a
 * failed-open upstream liquid control valve (IEC 60534 choked gas flow); the governing case sets the relieving rate
 * and the API orifice;</li>
 * <li>blowdown (API 521): vapour-withdrawal depressurization of the vessel inventory, with the smallest orifice that
 * reaches the lower of 6.9 barg and half the design pressure within 15 minutes, the peak flare rate and the minimum
 * fluid temperature;</li>
 * <li>flare load: simultaneous blowdown of all sized segments compared with the governing relief rate;</li>
 * <li>trip set points: placed midway between the operating value and the design or relief limit, so both margins are
 * visible;</li>
 * <li>safety integrity: NOG 070 minimum SIL per function and an IEC 61511 PFDavg screening of the drawn sensor, logic
 * solver and final elements, upgrading the architecture step by step until the target is met.</li>
 * </ul>
 *
 * <p>
 * Results only replace placeholders (values containing {@code REQUIRED} or {@code HOLD}); project inputs are never
 * overwritten. Every touched element keeps {@code REVIEW_REQUIRED} status and gets a {@link #EVIDENCE_PREFIX}
 * reference. The default {@link Policy} values are documented screening assumptions, not certified reliability data
 * or approved design rules; supply project values through the policy. A HAZOP, LOPA and SRS remain required.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PidSafetyStudies {
  /** Evidence prefix on every element updated by the studies. */
  public static final String EVIDENCE_PREFIX = "NEQSIM-SAFETY-SCREENING-STUDY";
  /** Attribute holding the evidence reference. */
  public static final String REFERENCE = "safetyStudyReference";
  /** Attribute holding the compact drawing annotation. */
  public static final String ANNOTATION = "drawingAnnotation";
  /** Attribute holding the HAZOP evidence sentence. */
  public static final String HAZOP_EVIDENCE = "hazopEvidence";
  /** Attribute holding study findings for an element. */
  public static final String FINDINGS = "safetyStudyFindings";
  private static final Logger logger = LogManager.getLogger(PidSafetyStudies.class);
  private static final double ATM = 1.01325;
  private static final double FIFTEEN_MINUTES = 900.0;

  /** Utility class. */
  private PidSafetyStudies() {
  }

  /**
   * Screening assumptions used by the studies. Every value can be replaced with a project value.
   */
  public static final class Policy implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name = "NEQSIM_SCREENING_DEFAULTS";
    private double designPressureFraction = 0.10;
    private double designPressureMinimumMarginBar = 3.5;
    private double designTemperatureMarginC = 30.0;
    private double flareBackPressureBara = 2.0;
    private double blowdownDischargeCoefficient = 0.85;
    private double normalLiquidLevelFraction = 0.5;
    private double fireEnvironmentFactor = 1.0;
    private double levelHighHighPercent = 85.0;
    private double levelLowLowPercent = 15.0;
    private double lowFlowFraction = 0.5;
    private double lowPressureFraction = 0.5;
    private double valvePressureRecoveryFactorXt = 0.72;
    private double carbonSteelMdmtC = -29.0;
    private double proofTestIntervalHours = 8760.0;
    private double sensorDangerousRate = 1.0e-6;
    private double sensorDiagnosticCoverage = 0.6;
    private double logicDangerousRate = 5.0e-7;
    private double logicDiagnosticCoverage = 0.9;
    private double finalElementDangerousRate = 3.0e-6;
    private double partialStrokeIntervalHours = 730.0;
    private double partialStrokeCoverage = 0.6;
    private double betaFactor = 0.1;
    private double meanRepairTimeHours = 8.0;
    private final Map<String, Double> segmentVolumes = new LinkedHashMap<String, Double>();

    /**
     * Returns the default screening policy.
     *
     * @return policy with documented screening defaults
     */
    public static Policy screening() {
      return new Policy();
    }

    /**
     * Sets the design pressure rule: the larger of operating gauge pressure times (1 + fraction) and operating plus a
     * minimum margin.
     *
     * @param fraction relative margin, for example 0.10
     * @param minimumMarginBar minimum absolute margin in bar
     * @return this policy
     */
    public Policy designPressure(double fraction, double minimumMarginBar) {
      name = "PROJECT_POLICY";
      designPressureFraction = fraction;
      designPressureMinimumMarginBar = minimumMarginBar;
      return this;
    }

    /**
     * Sets the flare system back pressure seen by relief and blowdown devices.
     *
     * @param bara back pressure in bara
     * @return this policy
     */
    public Policy flareBackPressure(double bara) {
      name = "PROJECT_POLICY";
      flareBackPressureBara = bara;
      return this;
    }

    /**
     * Declares the gas volume of a segment without vessel geometry in the model, such as a compressor casing and its
     * piping, so its blowdown can be sized.
     *
     * @param equipmentTag equipment owning the blowdown valve
     * @param volumeM3 segment volume in m3
     * @return this policy
     */
    public Policy segmentVolume(String equipmentTag, double volumeM3) {
      segmentVolumes.put(equipmentTag, Double.valueOf(volumeM3));
      return this;
    }

    /**
     * Sets the screening reliability data for the safety integrity calculation.
     *
     * @param sensorRate sensor dangerous failure rate per hour
     * @param logicRate logic solver dangerous failure rate per hour
     * @param finalElementRate final element (valve, actuator, solenoid) dangerous failure rate per hour
     * @param proofTestHours proof test interval in hours
     * @return this policy
     */
    public Policy reliability(double sensorRate, double logicRate, double finalElementRate, double proofTestHours) {
      name = "PROJECT_POLICY";
      sensorDangerousRate = sensorRate;
      logicDangerousRate = logicRate;
      finalElementDangerousRate = finalElementRate;
      proofTestIntervalHours = proofTestHours;
      return this;
    }

    /**
     * Returns the policy name: {@code NEQSIM_SCREENING_DEFAULTS} until a value is changed.
     *
     * @return policy name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the policy as a map for the study report.
     *
     * @return named policy values
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("designPressure", String.format(Locale.ROOT,
          "max(operating x %.2f, operating + %.1f bar) gauge", 1.0 + designPressureFraction,
          designPressureMinimumMarginBar));
      map.put("designTemperatureMarginC", Double.valueOf(designTemperatureMarginC));
      map.put("psvSetPressure", "equal to the proposed design pressure (single valve)");
      map.put("flareBackPressureBara", Double.valueOf(flareBackPressureBara));
      map.put("fireCase", "API 521 Q = 43200 F A^0.82 W, F = " + fireEnvironmentFactor
          + ", wetted area at normal liquid level");
      map.put("normalLiquidLevelFraction", Double.valueOf(normalLiquidLevelFraction));
      map.put("blowdownCriterion", "lower of 6.9 barg and 50 % of design pressure within 15 min (API 521)");
      map.put("blowdownDischargeCoefficient", Double.valueOf(blowdownDischargeCoefficient));
      map.put("gasBlowbyValve", "IEC 60534 choked gas flow, xT = " + valvePressureRecoveryFactorXt
          + ", rated Cv from the model valve");
      map.put("tripSetPoints", "midway between the operating value and the design or relief limit; level HH "
          + levelHighHighPercent + " %, LL " + levelLowLowPercent + " % of range; low flow " + lowFlowFraction
          + " x normal; low pressure " + lowPressureFraction + " x operating gauge");
      map.put("silTarget", "NOG 070 minimum SIL for the function type");
      map.put("reliabilityData", String.format(Locale.ROOT,
          "SCREENING_NOT_CERTIFIED: sensor lambdaD %.1e/h DC %.0f %%, logic %.1e/h DC %.0f %%, final element %.1e/h,"
              + " proof test %.0f h, beta %.2f, MTTR %.0f h, partial stroke %.0f h coverage %.0f %%",
          sensorDangerousRate, sensorDiagnosticCoverage * 100, logicDangerousRate, logicDiagnosticCoverage * 100,
          finalElementDangerousRate, proofTestIntervalHours, betaFactor, meanRepairTimeHours,
          partialStrokeIntervalHours, partialStrokeCoverage * 100));
      map.put("materialMinimumDesignMetalTemperatureC", Double.valueOf(carbonSteelMdmtC));
      map.put("segmentVolumesM3", new LinkedHashMap<String, Double>(segmentVolumes));
      return map;
    }
  }

  /**
   * Result of one study run: what was calculated, what was written to the P&amp;ID and what is still open.
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Policy policy;
    private final List<String> updatedTags = new ArrayList<String>();
    private final List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();

    /**
     * Creates an empty result.
     *
     * @param policy policy used
     */
    Result(Policy policy) {
      this.policy = policy;
    }

    /**
     * Returns the tags of the P&amp;ID elements that received study results.
     *
     * @return updated tags
     */
    public List<String> getUpdatedTags() {
      return updatedTags;
    }

    /**
     * Returns the open findings of the study.
     *
     * @return findings with code, subject and message
     */
    public List<Map<String, Object>> getFindings() {
      return findings;
    }

    /**
     * Returns the policy used.
     *
     * @return policy
     */
    public Policy getPolicy() {
      return policy;
    }

    /**
     * Records a finding.
     *
     * @param code finding code
     * @param subject tag or equipment
     * @param message explanation
     */
    void finding(String code, String subject, String message) {
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("code", code);
      row.put("subject", subject);
      row.put("message", message);
      findings.add(row);
    }
  }

  /**
   * Runs all screening studies and writes the results onto the P&amp;ID elements.
   *
   * <p>
   * Call after {@code PidStandardConnectionProposals.complete(...)} and before {@code PidSimulationBindings.from(...)}.
   * </p>
   *
   * @param model P&amp;ID proposal to update
   * @param process converged process the proposal was synthesized from
   * @param policy screening assumptions
   * @return what was calculated and what is still open
   */
  public static Result run(PidDesignModel model, ProcessSystem process, Policy policy) {
    if (model == null || process == null || policy == null) {
      throw new IllegalArgumentException("model, process and policy are required");
    }
    if (!process.solved()) {
      throw new IllegalStateException("Run and converge the process before the safety studies");
    }
    Result result = new Result(policy);
    Map<String, ProcessEquipmentInterface> units = new LinkedHashMap<String, ProcessEquipmentInterface>();
    Map<StreamInterface, ProcessEquipmentInterface> producers =
        new IdentityHashMap<StreamInterface, ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      units.put(unit.getName(), unit);
      for (StreamInterface outlet : unit.getOutletStreams()) {
        producers.put(outlet, unit);
      }
    }
    Map<String, Double> psvSetBarg = new LinkedHashMap<String, Double>();
    for (PidElement element : model.getElements()) {
      if (element.getType() == PidElementType.SAFETY_RELIEF_VALVE) {
        ProcessEquipmentInterface unit = units.get(element.getEquipmentTag());
        if (unit != null) {
          double set = reliefStudy(model, element, unit, producers, policy, result);
          if (!Double.isNaN(set)) {
            psvSetBarg.put(unit.getName(), Double.valueOf(set));
          }
        }
      }
    }
    for (PidElement element : model.getElements()) {
      if (element.getType() == PidElementType.BLOWDOWN_VALVE) {
        ProcessEquipmentInterface unit = units.get(element.getEquipmentTag());
        if (unit != null) {
          blowdownStudy(element, unit, policy, result);
        }
      }
    }
    flareLoad(model, result);
    Map<String, PidElement> byId = new LinkedHashMap<String, PidElement>();
    for (PidElement element : model.getElements()) {
      byId.put(element.getId(), element);
    }
    for (PidElement element : model.getElements()) {
      if (element.getType() == PidElementType.TRIP) {
        ProcessEquipmentInterface unit = units.get(element.getEquipmentTag());
        if (unit != null) {
          tripSetPoint(element, unit, psvSetBarg.get(unit.getName()), policy, result);
          integrity(element, byId, policy, result);
        }
      }
    }
    for (Map<String, Object> finding : result.findings) {
      for (PidElement element : model.getElements()) {
        if (element.getTag().equals(finding.get("subject"))) {
          Object previous = element.getAttributes().get(FINDINGS);
          String code = String.valueOf(finding.get("code"));
          element.attribute(FINDINGS, previous == null || String.valueOf(previous).contains(code)
              ? (previous == null ? code : String.valueOf(previous)) : previous + "; " + code);
        }
      }
    }
    return result;
  }

  /**
   * Builds the package report from the study results stored on the P&amp;ID elements.
   *
   * @param model P&amp;ID proposal that went through {@link #run(PidDesignModel, ProcessSystem, Policy)}
   * @param result run result with policy and findings, or null when only the stored attributes are available
   * @return report map, empty when no element carries study results
   */
  public static Map<String, Object> report(PidDesignModel model, Result result) {
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> relief = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> blowdown = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> functions = new ArrayList<Map<String, Object>>();
    double simultaneous = 0.0;
    double governingRelief = 0.0;
    for (PidElement element : model.getElements()) {
      Object reference = element.getAttributes().get(REFERENCE);
      boolean open = element.getAttributes().containsKey(FINDINGS);
      if ((reference == null || !String.valueOf(reference).startsWith(EVIDENCE_PREFIX)) && !open) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("tag", element.getTag());
      row.put("equipmentTag", element.getEquipmentTag());
      for (Map.Entry<String, Object> entry : element.getAttributes().entrySet()) {
        if (entry.getKey().startsWith("study") || entry.getKey().equals("setPoint") || entry.getKey().equals("silTarget")
            || entry.getKey().equals("voting") || entry.getKey().equals("orificeSizing")
            || entry.getKey().equals(FINDINGS)) {
          row.put(entry.getKey(), entry.getValue());
        }
      }
      if (element.getType() == PidElementType.SAFETY_RELIEF_VALVE) {
        relief.add(row);
        governingRelief = Math.max(governingRelief, number(element, "studyRelievingRateKgPerH"));
      } else if (element.getType() == PidElementType.BLOWDOWN_VALVE) {
        blowdown.add(row);
        simultaneous += number(element, "studyPeakBlowdownRateKgPerH");
      } else {
        functions.add(row);
      }
    }
    if (relief.isEmpty() && blowdown.isEmpty() && functions.isEmpty()) {
      return report;
    }
    report.put("schemaVersion", "neqsim_pid_safety_studies.v1");
    report.put("projectId", model.getProjectId());
    report.put("studyStatus", "SCREENING_REVIEW_REQUIRED");
    report.put("evidencePrefix", EVIDENCE_PREFIX);
    report.put("standards", java.util.Arrays.asList("API 520 Part I", "API 521:2020", "IEC 60534-2-1",
        "IEC 61511:2016", "NOG 070", "ISO 10418:2019"));
    if (result != null) {
      report.put("policy", result.getPolicy().toMap());
    } else {
      Object reference = null;
      for (PidElement element : model.getElements()) {
        reference = reference == null ? element.getAttributes().get(REFERENCE) : reference;
      }
      report.put("policy", reference != null && String.valueOf(reference).contains("NEQSIM_SCREENING_DEFAULTS")
          ? Policy.screening().toMap() : "PROJECT_POLICY: values recorded with the study run");
    }
    report.put("reliefDevices", relief);
    report.put("blowdownValves", blowdown);
    Map<String, Object> flare = new LinkedHashMap<String, Object>();
    flare.put("simultaneousBlowdownPeakKgPerH", Double.valueOf(Math.round(simultaneous)));
    flare.put("governingSingleReliefKgPerH", Double.valueOf(Math.round(governingRelief)));
    flare.put("flareDesignLoadKgPerH", Double.valueOf(Math.round(Math.max(simultaneous, governingRelief))));
    flare.put("governingFlareCase", simultaneous >= governingRelief ? "SIMULTANEOUS_BLOWDOWN" : "SINGLE_RELIEF");
    flare.put("basis", "Global blowdown of every sized segment at once, compared with the largest single relief case;"
        + " flare network hydraulics and radiation are not included");
    report.put("flareLoad", flare);
    report.put("safetyFunctions", functions);
    List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
    if (result != null) {
      findings.addAll(result.getFindings());
    } else {
      for (PidElement element : model.getElements()) {
        Object codes = element.getAttributes().get(FINDINGS);
        if (codes != null && element.getAttributes().containsKey(REFERENCE)
            || codes != null && element.getType() == PidElementType.BLOWDOWN_VALVE) {
          for (String code : String.valueOf(codes).split("; ")) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("code", code);
            row.put("subject", element.getTag());
            findings.add(row);
          }
        }
      }
    }
    report.put("findings", findings);
    report.put("approvalStatus", "REVIEW_REQUIRED");
    report.put("limitations", java.util.Arrays.asList(
        "Screening values: confirm design pressure, relief scenarios and set points in the HAZOP and relief study",
        "SIL targets are NOG 070 minimums; a LOPA may require more",
        "Reliability data are screening values, not certified data",
        "Two-phase relief, flare network back pressure and ESD valve closure times are not calculated"));
    return report;
  }

  /**
   * Returns the report as pretty-printed JSON.
   *
   * @param model studied P&amp;ID proposal
   * @param result run result, or null
   * @return JSON text, or an empty string when no element carries study results
   */
  public static String reportJson(PidDesignModel model, Result result) {
    Map<String, Object> report = report(model, result);
    return report.isEmpty() ? "" : new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
        .create().toJson(report);
  }

  /**
   * Sizes the relief valve of one protected unit and writes the results.
   *
   * @param model studied proposal, used to note gas blow-by on the upstream low-low level trip
   * @param psv relief valve element
   * @param unit protected unit
   * @param producers stream producers
   * @param policy screening assumptions
   * @param result run result
   * @return proposed set pressure in barg, or NaN when the study could not run
   */
  private static double reliefStudy(PidDesignModel model, PidElement psv, ProcessEquipmentInterface unit,
      Map<StreamInterface, ProcessEquipmentInterface> producers, Policy policy, Result result) {
    if (unit.getInletStreams().isEmpty()) {
      return Double.NaN;
    }
    double operatingBarg = maxPressureBara(unit) - ATM;
    double designBarg = designPressureBarg(operatingBarg, policy);
    double setBara = designBarg + ATM;
    double backBara = policy.flareBackPressureBara;
    StringBuilder cases = new StringBuilder();
    String governing = "";
    double governingRate = 0.0;
    double governingOverpressure = 0.10;
    double governingTemperatureK = unit.getInletStreams().get(0).getTemperature();
    // Blocked outlet: all vapour entering at relieving conditions must be relieved.
    double relievingBara = setBara * 1.10;
    try {
      SystemInterface inlet = mixedInlet(unit);
      inlet.setPressure(relievingBara, "bara");
      new ThermodynamicOperations(inlet).TPflash();
      inlet.initProperties();
      double vapourRate = vapourMassRateKgPerH(inlet, unit);
      if (vapourRate > 0.0) {
        governing = "BLOCKED_OUTLET";
        governingRate = vapourRate;
        cases.append(String.format(Locale.ROOT, "BLOCKED_OUTLET %.0f kg/h", vapourRate));
      }
    } catch (RuntimeException ex) {
      result.finding("RELIEF_CASE_FAILED", psv.getTag(), "Blocked-outlet flash failed: " + ex.getMessage());
    }
    // External fire on the wetted area of a vessel with liquid inventory.
    if (unit instanceof Separator) {
      Separator separator = (Separator) unit;
      double diameter = separator.getInternalDiameter();
      double length = separator.getSeparatorLength();
      if (diameter > 0.0 && length > 0.0) {
        double fireRelievingBara = setBara * 1.21;
        double latent = latentHeatJPerKg(separator, fireRelievingBara);
        if (latent > 0.0) {
          double wetted = wettedAreaM2(diameter, length, policy.normalLiquidLevelFraction,
              "vertical".equalsIgnoreCase(String.valueOf(separator.getOrientation())));
          double heatW = 43200.0 * policy.fireEnvironmentFactor * Math.pow(wetted, 0.82);
          double fireRate = heatW / Math.max(latent, 115000.0) * 3600.0;
          appendCase(cases, String.format(Locale.ROOT, "FIRE %.0f kg/h (wetted %.1f m2, %.0f kW)", fireRate,
              wetted, heatW / 1000.0));
          if (fireRate > governingRate) {
            governing = "FIRE";
            governingRate = fireRate;
            governingOverpressure = 0.21;
          }
        } else {
          result.finding("FIRE_CASE_SUPERCRITICAL", psv.getTag(), String.format(Locale.ROOT,
              "The liquid has no distinct latent heat at %.1f bara; use the API 521 supercritical fire method",
              fireRelievingBara));
        }
      } else {
        result.finding("FIRE_CASE_GEOMETRY_MISSING", psv.getTag(), "Vessel geometry is required for the fire case");
      }
    }
    // Gas blow-by from a higher-pressure vessel through a failed-open liquid valve.
    for (ThrottlingValve valve : upstreamLiquidValves(unit, producers)) {
      double blowby = gasBlowbyKgPerH(valve, producers, relievingBara, policy);
      if (blowby > 0.0) {
        if (valve.getPercentValveOpening() >= 99.0) {
          result.finding("BLOWBY_VALVE_CV_ASSUMED", psv.getTag(), valve.getName()
              + " runs fully open in the model, so its rated Cv is taken as the operating Cv; use the datasheet Cv");
        }
        ProcessEquipmentInterface source = producers.get(valve.getInletStreams().get(0));
        for (PidElement trip : model.getElementsForEquipment(source.getName())) {
          if (String.valueOf(trip.getAttributes().get("proposalPurpose")).contains("LEVEL-LL")) {
            trip.attribute(HAZOP_EVIDENCE, String.format(Locale.ROOT,
                "Loss of level in %s gives gas blow-by of %.0f kg/h through %s to %s, relieved by %s", source.getName(),
                blowby, valve.getName(), unit.getName(), psv.getTag()));
          }
        }
        appendCase(cases, String.format(Locale.ROOT, "GAS_BLOWBY via %s %.0f kg/h", valve.getName(), blowby));
        if (blowby > governingRate) {
          governing = "GAS_BLOWBY via " + valve.getName();
          governingRate = blowby;
          governingOverpressure = 0.10;
        }
      }
    }
    if (governingRate <= 0.0) {
      result.finding("RELIEF_CASE_NOT_QUANTIFIED", psv.getTag(), "No relief case could be quantified");
      return Double.NaN;
    }
    String orifice;
    double requiredMm2;
    double relievingTemperatureC;
    try {
      SystemInterface gas = reliefGas(unit, setBara * (1.0 + governingOverpressure));
      relievingTemperatureC = gas.getTemperature() - 273.15;
      ReliefValveSizing.PSVSizingResult sizing = ReliefValveSizing.calculateRequiredArea(governingRate / 3600.0,
          setBara * 1.0e5, governingOverpressure, backBara * 1.0e5, gas.getTemperature(), gas.getMolarMass(),
          gas.getZ(), Math.max(1.01, gas.getGamma2()), false, false);
      requiredMm2 = sizing.getRequiredArea() * 1.0e6;
      double largest = ReliefValveSizing.STANDARD_ORIFICE_AREAS_IN2[ReliefValveSizing.STANDARD_ORIFICE_AREAS_IN2.length
          - 1];
      if (sizing.getRequiredAreaIn2() > largest) {
        int count = (int) Math.ceil(sizing.getRequiredAreaIn2() / largest);
        orifice = count + " x T";
        result.finding("RELIEF_MULTIPLE_VALVES", psv.getTag(),
            "Required area exceeds a T orifice; " + count + " valves in parallel are needed");
      } else {
        orifice = sizing.getRecommendedOrifice();
      }
    } catch (RuntimeException ex) {
      result.finding("RELIEF_SIZING_FAILED", psv.getTag(), ex.getMessage());
      return Double.NaN;
    }
    psv.attribute(REFERENCE,
        reference(policy, EVIDENCE_PREFIX + ": API 520/521 relief sizing on the simulated operating point"));
    psv.attribute("studyProtectedDesignPressureBarg", round1(designBarg));
    psv.attribute("studySetPressureBarg", round1(designBarg));
    psv.attribute("studyGoverningCase", governing);
    psv.attribute("studyReliefCases", cases.toString());
    psv.attribute("studyRelievingRateKgPerH", Double.valueOf(Math.round(governingRate)));
    psv.attribute("studyOverpressurePercent", Double.valueOf(governingOverpressure * 100.0));
    psv.attribute("studyRelievingTemperatureC", round1(relievingTemperatureC));
    psv.attribute("studyRequiredAreaMm2", round1(requiredMm2));
    psv.attribute("studyOrifice", orifice);
    psv.attribute("studyBackPressureBara", round1(backBara));
    psv.attribute(ANNOTATION, String.format(Locale.ROOT, "SET %.1f barg / %s", designBarg, orifice));
    psv.attribute(HAZOP_EVIDENCE, String.format(Locale.ROOT,
        "%s set %.1f barg (design pressure %.1f barg), governing case %s %.0f kg/h, orifice %s", psv.getTag(),
        designBarg, designBarg, governing, governingRate, orifice));
    result.updatedTags.add(psv.getTag());
    return designBarg;
  }

  /**
   * Sizes the blowdown orifice of one segment by dynamic depressurization and writes the results.
   *
   * @param bdv blowdown valve element
   * @param unit segment owner
   * @param policy screening assumptions
   * @param result run result
   */
  private static void blowdownStudy(PidElement bdv, ProcessEquipmentInterface unit, Policy policy, Result result) {
    if (!placeholder(bdv.getAttributes().get("orificeSizing"))) {
      return;
    }
    double volume;
    SystemInterface inventory;
    String volumeBasis;
    try {
      if (unit instanceof Separator && ((Separator) unit).getInternalDiameter() > 0.0) {
        Separator separator = (Separator) unit;
        double diameter = separator.getInternalDiameter();
        volume = Math.PI / 4.0 * diameter * diameter * separator.getSeparatorLength();
        volumeBasis = String.format(Locale.ROOT, "vessel %.2f m x %.2f m from the process model", diameter,
            separator.getSeparatorLength());
        inventory = vesselInventory(separator, volume, policy.normalLiquidLevelFraction);
      } else if (policy.segmentVolumes.containsKey(unit.getName())) {
        volume = policy.segmentVolumes.get(unit.getName()).doubleValue();
        volumeBasis = String.format(Locale.ROOT, "declared segment volume %.2f m3", volume);
        StreamInterface outlet = unit.getOutletStreams().isEmpty() ? unit.getInletStreams().get(0)
            : unit.getOutletStreams().get(0);
        inventory = outlet.getThermoSystem().clone();
      } else {
        result.finding("BLOWDOWN_SEGMENT_VOLUME_REQUIRED", bdv.getTag(),
            "Declare the segment volume of " + unit.getName() + " (Policy.segmentVolume) to size this blowdown valve");
        return;
      }
    } catch (RuntimeException ex) {
      result.finding("BLOWDOWN_INVENTORY_FAILED", bdv.getTag(), ex.getMessage());
      return;
    }
    double operatingBarg = maxPressureBara(unit) - ATM;
    double designBarg = designPressureBarg(operatingBarg, policy);
    double targetBara = Math.min(6.9, 0.5 * designBarg) + ATM;
    if (inventory.getPressure() <= targetBara) {
      result.finding("BLOWDOWN_NOT_REQUIRED_BY_CRITERION", bdv.getTag(),
          "Operating pressure is already below the API 521 blowdown target");
      return;
    }
    double low = 2.0;
    double high = 150.0;
    DepressurizationSimulator.DepressurizationResult best = null;
    double bestDiameter = Double.NaN;
    DepressurizationSimulator.DepressurizationResult largest = simulate(inventory, volume, high, targetBara, policy);
    if (largest == null || timeToTarget(largest, targetBara) > FIFTEEN_MINUTES) {
      result.finding("BLOWDOWN_TARGET_NOT_REACHED", bdv.getTag(),
          "A 150 mm orifice does not reach the target within 15 minutes; split the segment or review the criterion");
      return;
    }
    best = largest;
    bestDiameter = high;
    for (int iteration = 0; iteration < 8; iteration++) {
      double mid = 0.5 * (low + high);
      DepressurizationSimulator.DepressurizationResult trial = simulate(inventory, volume, mid, targetBara, policy);
      if (trial != null && timeToTarget(trial, targetBara) <= FIFTEEN_MINUTES) {
        high = mid;
        best = trial;
        bestDiameter = mid;
      } else {
        low = mid;
      }
    }
    double diameterMm = Math.ceil(bestDiameter);
    double peak = 0.0;
    for (Double flow : best.massFlowKgPerS) {
      peak = Math.max(peak, flow.doubleValue());
    }
    double minimumC = best.minFluidTemperatureK - 273.15;
    double seconds = timeToTarget(best, targetBara);
    bdv.attribute(REFERENCE,
        reference(policy, EVIDENCE_PREFIX + ": API 521 dynamic depressurization of the simulated inventory"));
    bdv.attribute("orificeSizing", String.format(Locale.ROOT, "%.0f mm restriction orifice (API 521, %.1f barg in %.0f s)",
        diameterMm, targetBara - ATM, seconds));
    bdv.attribute("studyOrificeDiameterMm", Double.valueOf(diameterMm));
    bdv.attribute("studyInventoryBasis", volumeBasis);
    bdv.attribute("studyInitialPressureBarg", round1(inventory.getPressure() - ATM));
    bdv.attribute("studyTargetPressureBarg", round1(targetBara - ATM));
    bdv.attribute("studyTimeToTargetS", Double.valueOf(Math.round(seconds)));
    bdv.attribute("studyPeakBlowdownRateKgPerH", Double.valueOf(Math.round(peak * 3600.0)));
    bdv.attribute("studyMinimumFluidTemperatureC", round1(minimumC));
    String mdmt = minimumC < policy.carbonSteelMdmtC ? "BELOW_CARBON_STEEL_MDMT" : "ABOVE_CARBON_STEEL_MDMT";
    bdv.attribute("studyMinimumTemperatureCheck", mdmt);
    if (minimumC < policy.carbonSteelMdmtC) {
      result.finding("BLOWDOWN_LOW_TEMPERATURE", bdv.getTag(), String.format(Locale.ROOT,
          "Fluid reaches %.1f C during blowdown (wall heat capacity not modelled), below the %.0f C carbon steel"
              + " limit; check wall and tail-pipe temperatures and review material selection",
          minimumC, policy.carbonSteelMdmtC));
    }
    bdv.attribute(ANNOTATION, String.format(Locale.ROOT, "RO %.0f mm", diameterMm));
    bdv.attribute(HAZOP_EVIDENCE, String.format(Locale.ROOT,
        "%s with %.0f mm orifice depressurizes %s to %.1f barg in %.0f s, peak %.0f kg/h to flare, minimum %.1f C",
        bdv.getTag(), diameterMm, unit.getName(), targetBara - ATM, seconds, peak * 3600.0, minimumC));
    result.updatedTags.add(bdv.getTag());
  }

  /**
   * Records an open finding when no blowdown or relief load reached the flare summary.
   *
   * @param model studied proposal
   * @param result run result
   */
  private static void flareLoad(PidDesignModel model, Result result) {
    boolean any = false;
    for (PidElement element : model.getElements()) {
      any |= element.getAttributes().containsKey("studyPeakBlowdownRateKgPerH")
          || element.getAttributes().containsKey("studyRelievingRateKgPerH");
    }
    if (!any) {
      result.finding("FLARE_LOAD_NOT_QUANTIFIED", model.getProjectId(),
          "No relief or blowdown load was quantified for the flare system");
    }
  }

  /**
   * Proposes the trip set point of one shutdown function.
   *
   * @param trip trip element
   * @param unit monitored unit
   * @param psvSetBarg relief set pressure of the unit, or null
   * @param policy screening assumptions
   * @param result run result
   */
  private static void tripSetPoint(PidElement trip, ProcessEquipmentInterface unit, Double psvSetBarg, Policy policy,
      Result result) {
    if (!placeholder(trip.getAttributes().get("setPoint"))) {
      return;
    }
    String purpose = String.valueOf(trip.getAttributes().get("proposalPurpose"));
    String setPoint = null;
    String basis = null;
    String display = null;
    if (purpose.contains("P-HH") || purpose.contains("PRESSURE-HH")) {
      double operating = (purpose.contains("DISCHARGE") ? outletPressureBara(unit) : maxPressureBara(unit)) - ATM;
      double limit = psvSetBarg != null ? psvSetBarg.doubleValue() : designPressureBarg(operating, policy);
      double set = 0.5 * (operating + limit);
      setPoint = String.format(Locale.ROOT, "%.1f barg", set);
      basis = String.format(Locale.ROOT, "midway between operating %.1f barg and %s %.1f barg", operating,
          psvSetBarg != null ? "PSV set" : "design pressure", limit);
      display = String.format(Locale.ROOT, "%.1f barg", set);
    } else if (purpose.contains("P-LL") || purpose.contains("LOW-SUCTION")) {
      if (purpose.contains("LOW-SUCTION")) {
        return;
      }
      double operating = inletPressureBara(unit) - ATM;
      double set = policy.lowPressureFraction * operating;
      setPoint = String.format(Locale.ROOT, "%.1f barg", set);
      basis = String.format(Locale.ROOT, "%.0f %% of operating %.1f barg", policy.lowPressureFraction * 100,
          operating);
      display = setPoint;
    } else if (purpose.contains("T-HH")) {
      double operating = unit.getOutletStreams().isEmpty() ? unit.getInletStreams().get(0).getTemperature("C")
          : unit.getOutletStreams().get(0).getTemperature("C");
      double design = operating + policy.designTemperatureMarginC;
      double set = 0.5 * (operating + design);
      setPoint = String.format(Locale.ROOT, "%.0f C", set);
      basis = String.format(Locale.ROOT, "midway between operating %.1f C and design %.1f C", operating, design);
      display = setPoint;
    } else if (purpose.contains("LOW-FLOW")) {
      double normal = unit.getInletStreams().get(0).getFlowRate("kg/hr");
      double set = policy.lowFlowFraction * normal;
      setPoint = String.format(Locale.ROOT, "%.0f kg/h", set);
      basis = String.format(Locale.ROOT, "%.0f %% of normal %.0f kg/h", policy.lowFlowFraction * 100, normal);
      display = setPoint;
    } else if (purpose.contains("LEVEL-HH")) {
      setPoint = String.format(Locale.ROOT, "%.0f %% of range", policy.levelHighHighPercent);
      basis = "screening level HH; confirm against vessel internals and the inlet device";
      display = String.format(Locale.ROOT, "%.0f %%", policy.levelHighHighPercent);
    } else if (purpose.contains("LEVEL-LL")) {
      setPoint = String.format(Locale.ROOT, "%.0f %% of range", policy.levelLowLowPercent);
      basis = "screening level LL; confirm the liquid seal against gas blow-by";
      display = String.format(Locale.ROOT, "%.0f %%", policy.levelLowLowPercent);
    }
    if (setPoint == null) {
      return;
    }
    trip.attribute("setPoint", setPoint);
    trip.attribute("studySetPointBasis", basis);
    trip.attribute(REFERENCE, reference(policy, EVIDENCE_PREFIX + ": trip set point from the simulated operating point"));
    trip.attribute(ANNOTATION, display);
    Object earlier = trip.getAttributes().get(HAZOP_EVIDENCE);
    String sentence = trip.getTag() + " trips at " + setPoint + " (" + basis + ")";
    trip.attribute(HAZOP_EVIDENCE, earlier == null ? sentence : earlier + "; " + sentence);
    if (!result.updatedTags.contains(trip.getTag())) {
      result.updatedTags.add(trip.getTag());
    }
  }

  /**
   * Sets the NOG 070 SIL target and verifies the drawn architecture by PFDavg screening.
   *
   * @param trip trip element
   * @param byId elements by identifier
   * @param policy screening assumptions
   * @param result run result
   */
  private static void integrity(PidElement trip, Map<String, PidElement> byId, Policy policy, Result result) {
    if (!placeholder(trip.getAttributes().get("silTarget"))) {
      return;
    }
    String purpose = String.valueOf(trip.getAttributes().get("proposalPurpose"));
    Nog070SifType type = purpose.contains("LEVEL-HH") ? Nog070SifType.PSD_HIGH_LEVEL
        : purpose.contains("LEVEL-LL") ? Nog070SifType.PSD_LOW_LOW_LEVEL : Nog070SifType.PSD_PROCESS_SEGMENT;
    int target = Nog070SilCatalogue.getMinimumSil(type);
    trip.attribute("silTarget", "SIL " + target + " (NOG 070 minimum: " + type.name() + ")");
    trip.attribute(REFERENCE, reference(policy, EVIDENCE_PREFIX + ": NOG 070 SIL target and IEC 61511 PFDavg screening"));
    if (!result.updatedTags.contains(trip.getTag())) {
      result.updatedTags.add(trip.getTag());
    }
    List<PidElement> finals = finalElements(trip, byId);
    String existing = trip.getAttributes().get(ANNOTATION) == null ? "" : trip.getAttributes().get(ANNOTATION) + " ";
    if (finals.isEmpty()) {
      trip.attribute("studySilVerification", "FINAL_ELEMENT_REQUIRED");
      trip.attribute(ANNOTATION, existing + "SIL " + target);
      result.finding("SIF_FINAL_ELEMENT_REQUIRED", trip.getTag(),
          "No final element is assigned, so SIL " + target + " cannot be verified");
      return;
    }
    String[] steps = {"1oo1 sensor, 1oo1 logic, 1oo1 final element", "1oo1 sensor, 1oo1 logic, 1oo1 final element"
        + " with partial-stroke testing", "1oo2 sensors, 1oo1 logic, 1oo1 final element with partial-stroke testing",
        "1oo2 sensors, 1oo1 logic, 1oo2 final elements (second valve in series) with partial-stroke testing"};
    double pfd = Double.NaN;
    int achieved = 0;
    int step = 0;
    for (; step < steps.length; step++) {
      SafetyFunctionDesign design = new SafetyFunctionDesign(trip.getTag(), trip.getTag() + "-SIF", target);
      int sensors = step >= 2 ? 2 : 1;
      design.addSubsystem(new SafetyFunctionDesign.Subsystem("sensor", SafetyFunctionDesign.SubsystemType.SENSOR, 1,
          sensors, policy.sensorDangerousRate, policy.sensorDiagnosticCoverage, policy.proofTestIntervalHours,
          policy.meanRepairTimeHours, policy.betaFactor));
      design.addSubsystem(new SafetyFunctionDesign.Subsystem("logic", SafetyFunctionDesign.SubsystemType.LOGIC_SOLVER,
          1, 1, policy.logicDangerousRate, policy.logicDiagnosticCoverage, policy.proofTestIntervalHours,
          policy.meanRepairTimeHours, policy.betaFactor));
      for (PidElement valve : finals) {
        SafetyFunctionDesign.Subsystem finalElement = new SafetyFunctionDesign.Subsystem(valve.getTag(),
            SafetyFunctionDesign.SubsystemType.FINAL_ELEMENT, 1, step >= 3 ? 2 : 1, policy.finalElementDangerousRate,
            0.0, policy.proofTestIntervalHours, policy.meanRepairTimeHours, policy.betaFactor);
        if (step >= 1) {
          finalElement.setPartialStrokeTesting(policy.partialStrokeIntervalHours, policy.partialStrokeCoverage);
        }
        design.addSubsystem(finalElement);
      }
      pfd = design.calculatePfdAverage();
      achieved = design.getAchievedSil();
      if (achieved >= target) {
        break;
      }
    }
    boolean met = achieved >= target;
    int used = Math.min(step, steps.length - 1);
    List<String> valveTags = new ArrayList<String>();
    for (PidElement valve : finals) {
      valveTags.add(valve.getTag());
    }
    trip.attribute("voting", steps[used] + " per isolation point (" + String.join(", ", valveTags) + ")");
    trip.attribute("studyPfdAvg", Double.valueOf(Double.parseDouble(String.format(Locale.ROOT, "%.2e", pfd))));
    trip.attribute("studyAchievedSil", Integer.valueOf(achieved));
    trip.attribute("studySilVerification", met ? "TARGET_MET_SCREENING" : "TARGET_NOT_MET");
    trip.attribute(ANNOTATION, existing + "SIL " + target);
    String evidence = String.format(Locale.ROOT, "SIL %d target (NOG 070), PFDavg %.1e with %s", target, pfd,
        steps[used]);
    Object previous = trip.getAttributes().get(HAZOP_EVIDENCE);
    trip.attribute(HAZOP_EVIDENCE, previous == null ? trip.getTag() + " " + evidence : previous + "; " + evidence);
    if (!met) {
      result.finding("SIF_TARGET_NOT_MET", trip.getTag(), String.format(Locale.ROOT,
          "SIL %d not reached (PFDavg %.1e) even with redundant sensors and final elements; review the function",
          target, pfd));
    } else if (used == 3) {
      result.finding("SIF_REDUNDANT_FINAL_ELEMENT", trip.getTag(),
          "SIL " + target + " needs a second final element in series; it is not drawn yet");
    }
  }

  /**
   * Returns the shutdown valves that must act for a trip, following logic functions to their valves. Inlet isolation
   * valves are preferred when present; blowdown valves belong to a separate function.
   *
   * @param trip trip element
   * @param byId elements by identifier
   * @return required final elements
   */
  private static List<PidElement> finalElements(PidElement trip, Map<String, PidElement> byId) {
    List<PidElement> valves = new ArrayList<PidElement>();
    Set<String> visited = new LinkedHashSet<String>();
    List<String> queue = new ArrayList<String>(trip.getConnectedElementIds());
    while (!queue.isEmpty()) {
      String id = queue.remove(0);
      if (!visited.add(id)) {
        continue;
      }
      PidElement element = byId.get(id);
      if (element == null) {
        continue;
      }
      if (element.getType() == PidElementType.SHUTDOWN_VALVE) {
        valves.add(element);
      } else if (element.getType() == PidElementType.TRIP || element.getType() == PidElementType.SAFETY_FUNCTION) {
        queue.addAll(element.getConnectedElementIds());
      }
    }
    List<PidElement> inlet = new ArrayList<PidElement>();
    for (PidElement valve : valves) {
      if (String.valueOf(valve.getAttributes().get("proposalPurpose")).contains("INLET")) {
        inlet.add(valve);
      }
    }
    return inlet.isEmpty() ? valves : inlet;
  }

  /**
   * Runs one depressurization trial.
   *
   * @param inventory vessel inventory at operating conditions
   * @param volume vessel volume in m3
   * @param diameterMm orifice diameter in mm
   * @param targetBara target pressure in bara
   * @param policy screening assumptions
   * @return trial result, or null when the simulation fails
   */
  private static DepressurizationSimulator.DepressurizationResult simulate(SystemInterface inventory, double volume,
      double diameterMm, double targetBara, Policy policy) {
    try {
      DepressurizationSimulator simulator = new DepressurizationSimulator(inventory.clone(), volume,
          diameterMm / 1000.0, policy.blowdownDischargeCoefficient, policy.flareBackPressureBara * 1.0e5);
      simulator.setWithdrawalMode(DepressurizationSimulator.WithdrawalMode.VAPOUR);
      // Keep each step to about 2 % of the inventory: large orifices on small volumes need short steps.
      SystemInterface start = inventory.clone();
      start.initProperties();
      double mass = start.getDensity("kg/m3") * volume;
      double area = Math.PI / 4.0 * Math.pow(diameterMm / 1000.0, 2);
      double initialFlow = policy.blowdownDischargeCoefficient * area * start.getPressure() * 1.0e5
          * Math.sqrt(start.getMolarMass() / (8.314 * start.getTemperature())) * 0.67;
      double step = initialFlow > 0.0 ? 0.02 * mass / initialFlow : 5.0;
      simulator.setTimeStep(Math.max(0.05, Math.min(5.0, step)));
      simulator.setMaxTime(FIFTEEN_MINUTES + 5.0);
      simulator.setStopPressure(targetBara * 1.0e5 * 0.98);
      return simulator.run();
    } catch (RuntimeException ex) {
      logger.debug("Depressurization trial failed for {} mm: {}", Double.valueOf(diameterMm), ex.getMessage());
      return null;
    }
  }

  /**
   * Returns the first time the pressure reaches the target.
   *
   * @param trial depressurization result
   * @param targetBara target pressure in bara
   * @return seconds, or infinity when not reached
   */
  private static double timeToTarget(DepressurizationSimulator.DepressurizationResult trial, double targetBara) {
    for (int i = 0; i < trial.pressureBara.size(); i++) {
      if (trial.pressureBara.get(i).doubleValue() <= targetBara) {
        return trial.time.get(i).doubleValue();
      }
    }
    return Double.POSITIVE_INFINITY;
  }

  /**
   * Builds the gas and liquid inventory of a separator at its normal liquid level.
   *
   * @param separator separator
   * @param volume vessel volume in m3
   * @param liquidFraction liquid volume fraction
   * @return combined inventory at operating conditions
   */
  private static SystemInterface vesselInventory(Separator separator, double volume, double liquidFraction) {
    SystemInterface gas = separator.getGasOutStream().getThermoSystem().clone();
    gas.setTotalFlowRate(gas.getDensity("kg/m3") * volume * (1.0 - liquidFraction), "kg/sec");
    new ThermodynamicOperations(gas).TPflash();
    gas.initProperties();
    if (separator.getLiquidOutStream() == null || separator.getLiquidOutStream().getFlowRate("kg/hr") <= 1.0e-6) {
      return gas;
    }
    SystemInterface liquid = separator.getLiquidOutStream().getThermoSystem().clone();
    liquid.setTotalFlowRate(liquid.getDensity("kg/m3") * volume * liquidFraction, "kg/sec");
    gas.addFluid(liquid);
    new ThermodynamicOperations(gas).TPflash();
    gas.initProperties();
    return gas;
  }

  /**
   * Mixes the inlet streams of a unit.
   *
   * @param unit unit
   * @return mixed inlet fluid at the first inlet temperature
   */
  private static SystemInterface mixedInlet(ProcessEquipmentInterface unit) {
    SystemInterface mixed = null;
    for (StreamInterface inlet : unit.getInletStreams()) {
      SystemInterface fluid = inlet.getThermoSystem().clone();
      if (mixed == null) {
        mixed = fluid;
      } else {
        mixed.addFluid(fluid);
      }
    }
    return mixed;
  }

  /**
   * Returns the vapour mass rate of a flashed inlet, scaled to the unit's inlet mass flow.
   *
   * @param flashed flashed inlet at relieving conditions
   * @param unit unit
   * @return vapour mass rate in kg/h
   */
  private static double vapourMassRateKgPerH(SystemInterface flashed, ProcessEquipmentInterface unit) {
    double total = 0.0;
    for (StreamInterface inlet : unit.getInletStreams()) {
      total += inlet.getFlowRate("kg/hr");
    }
    if (!flashed.hasPhaseType("gas")) {
      return 0.0;
    }
    double vapourFraction = flashed.getPhase("gas").getMass() / flashed.getMass("kg");
    return total * vapourFraction;
  }

  /**
   * Returns the relieving gas at relieving pressure.
   *
   * @param unit protected unit
   * @param relievingBara relieving pressure in bara
   * @return gas system at relieving conditions
   */
  private static SystemInterface reliefGas(ProcessEquipmentInterface unit, double relievingBara) {
    SystemInterface gas = unit instanceof Separator ? ((Separator) unit).getGasOutStream().getThermoSystem().clone()
        : mixedInlet(unit);
    gas.setPressure(relievingBara, "bara");
    new ThermodynamicOperations(gas).TPflash();
    gas.initProperties();
    return gas;
  }

  /**
   * Returns the latent heat of the separator liquid at the fire relieving pressure.
   *
   * @param separator separator
   * @param relievingBara relieving pressure in bara
   * @return latent heat in J/kg, or 0 when the liquid is supercritical or absent
   */
  private static double latentHeatJPerKg(Separator separator, double relievingBara) {
    try {
      if (separator.getLiquidOutStream() == null || separator.getLiquidOutStream().getFlowRate("kg/hr") <= 1.0e-6) {
        return 0.0;
      }
      SystemInterface liquid = separator.getLiquidOutStream().getThermoSystem().clone();
      liquid.setPressure(relievingBara, "bara");
      new ThermodynamicOperations(liquid).bubblePointTemperatureFlash();
      liquid.setTemperature(liquid.getTemperature() + 1.0);
      new ThermodynamicOperations(liquid).TPflash();
      liquid.initProperties();
      if (!liquid.hasPhaseType("gas") || liquid.getNumberOfPhases() < 2) {
        return 0.0;
      }
      double gasH = liquid.getPhase("gas").getEnthalpy("J/kg");
      double liquidH = liquid.getPhase(1).getEnthalpy("J/kg");
      double latent = gasH - liquidH;
      return latent > 0.0 ? latent : 0.0;
    } catch (Exception ex) {
      logger.debug("Latent heat calculation failed for {}: {}", separator.getName(), ex.getMessage());
      return 0.0;
    }
  }

  /**
   * Returns the API 521 wetted area of a vessel at the given liquid level.
   *
   * @param diameter inside diameter in m
   * @param length tan-tan length in m
   * @param level liquid level fraction of the diameter (horizontal) or length (vertical)
   * @param vertical true for a vertical vessel
   * @return wetted area in m2
   */
  static double wettedAreaM2(double diameter, double length, double level, boolean vertical) {
    if (vertical) {
      return Math.PI * diameter * Math.min(7.6, level * length) + Math.PI * diameter * diameter / 4.0;
    }
    double theta = 2.0 * Math.acos(1.0 - 2.0 * level);
    double circumferenceFraction = theta / (2.0 * Math.PI);
    double segment = diameter * diameter / 8.0 * (theta - Math.sin(theta));
    return Math.PI * diameter * length * circumferenceFraction + 2.0 * segment;
  }

  /**
   * Finds liquid control valves upstream of a unit that draw from a higher-pressure vessel.
   *
   * @param unit downstream unit
   * @param producers stream producers
   * @return valves that can pass gas on loss of liquid level
   */
  private static List<ThrottlingValve> upstreamLiquidValves(ProcessEquipmentInterface unit,
      Map<StreamInterface, ProcessEquipmentInterface> producers) {
    List<ThrottlingValve> valves = new ArrayList<ThrottlingValve>();
    List<StreamInterface> queue = new ArrayList<StreamInterface>(unit.getInletStreams());
    Set<ProcessEquipmentInterface> seen = java.util.Collections
        .newSetFromMap(new IdentityHashMap<ProcessEquipmentInterface, Boolean>());
    while (!queue.isEmpty()) {
      ProcessEquipmentInterface producer = producers.get(queue.remove(0));
      if (producer == null || !seen.add(producer)) {
        continue;
      }
      if (producer instanceof ThrottlingValve) {
        ProcessEquipmentInterface source = producers.get(producer.getInletStreams().get(0));
        if (source instanceof Separator && source.getInletStreams().size() > 0
            && ((Separator) source).getLiquidOutStream() == producer.getInletStreams().get(0)) {
          valves.add((ThrottlingValve) producer);
        }
      } else if (producer instanceof Mixer || producer.getClass() == Heater.class) {
        queue.addAll(producer.getInletStreams());
      }
    }
    return valves;
  }

  /**
   * Returns the choked gas flow through a fully open liquid valve when the upstream vessel loses its level.
   *
   * @param valve liquid control valve
   * @param producers stream producers
   * @param downstreamBara downstream relieving pressure in bara
   * @param policy screening assumptions
   * @return gas blow-by in kg/h, or 0 when it cannot be calculated
   */
  private static double gasBlowbyKgPerH(ThrottlingValve valve, Map<StreamInterface, ProcessEquipmentInterface> producers,
      double downstreamBara, Policy policy) {
    try {
      Separator source = (Separator) producers.get(valve.getInletStreams().get(0));
      double opening = Math.max(1.0, valve.getPercentValveOpening()) / 100.0;
      double ratedCv = valve.getCv() / opening;
      if (!(ratedCv > 0.0)) {
        return 0.0;
      }
      SystemInterface gas = source.getGasOutStream().getThermoSystem().clone();
      gas.initProperties();
      double p1 = gas.getPressure();
      if (p1 <= downstreamBara) {
        return 0.0;
      }
      double rho = gas.getDensity("kg/m3");
      double fGamma = Math.max(1.01, gas.getGamma2()) / 1.40;
      double x = Math.min((p1 - downstreamBara) / p1, fGamma * policy.valvePressureRecoveryFactorXt);
      double y = Math.max(2.0 / 3.0, 1.0 - x / (3.0 * fGamma * policy.valvePressureRecoveryFactorXt));
      return 31.6 * ratedCv * y * Math.sqrt(x * p1 * rho);
    } catch (RuntimeException ex) {
      logger.debug("Gas blow-by calculation failed for {}: {}", valve.getName(), ex.getMessage());
      return 0.0;
    }
  }

  /**
   * Returns the proposed design pressure for an operating pressure.
   *
   * @param operatingBarg operating pressure in barg
   * @param policy screening assumptions
   * @return design pressure in barg
   */
  private static double designPressureBarg(double operatingBarg, Policy policy) {
    return Math.max(operatingBarg * (1.0 + policy.designPressureFraction),
        operatingBarg + policy.designPressureMinimumMarginBar);
  }

  /**
   * Returns the highest stream pressure at a unit.
   *
   * @param unit unit
   * @return pressure in bara
   */
  private static double maxPressureBara(ProcessEquipmentInterface unit) {
    double pressure = 0.0;
    for (StreamInterface stream : unit.getInletStreams()) {
      pressure = Math.max(pressure, stream.getPressure("bara"));
    }
    for (StreamInterface stream : unit.getOutletStreams()) {
      pressure = Math.max(pressure, stream.getPressure("bara"));
    }
    return pressure;
  }

  /**
   * Returns the outlet pressure of a unit, or its highest pressure when it has no outlet.
   *
   * @param unit unit
   * @return pressure in bara
   */
  private static double outletPressureBara(ProcessEquipmentInterface unit) {
    return unit instanceof Compressor ? ((Compressor) unit).getOutletStream().getPressure("bara")
        : maxPressureBara(unit);
  }

  /**
   * Returns the inlet pressure of a unit.
   *
   * @param unit unit
   * @return pressure in bara
   */
  private static double inletPressureBara(ProcessEquipmentInterface unit) {
    return unit.getInletStreams().isEmpty() ? maxPressureBara(unit) : unit.getInletStreams().get(0).getPressure("bara");
  }

  /**
   * Returns true for an empty or placeholder attribute value.
   *
   * @param value attribute value
   * @return true when the value still awaits input
   */
  private static boolean placeholder(Object value) {
    if (value == null) {
      return false;
    }
    String text = String.valueOf(value);
    return text.contains("REQUIRED") || text.equals("HOLD") || text.contains("_INPUT");
  }

  /**
   * Adds the policy name to an evidence reference.
   *
   * @param policy policy used
   * @param text evidence text
   * @return evidence reference
   */
  private static String reference(Policy policy, String text) {
    return text + " [policy " + policy.name + "]";
  }

  /**
   * Appends one case to a case list.
   *
   * @param cases case list
   * @param text case text
   */
  private static void appendCase(StringBuilder cases, String text) {
    if (cases.length() > 0) {
      cases.append("; ");
    }
    cases.append(text);
  }

  /**
   * Rounds to one decimal.
   *
   * @param value value
   * @return rounded value
   */
  private static Double round1(double value) {
    return Double.valueOf(Math.round(value * 10.0) / 10.0);
  }

  /**
   * Returns a numeric attribute, or zero.
   *
   * @param element element
   * @param name attribute name
   * @return value or zero
   */
  private static double number(PidElement element, String name) {
    Object value = element.getAttributes().get(name);
    return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
  }
}
