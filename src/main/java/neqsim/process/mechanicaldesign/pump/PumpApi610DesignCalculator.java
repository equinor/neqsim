package neqsim.process.mechanicaldesign.pump;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Value-only calculator for API 610 pump design screening.
 *
 * <p>
 * The calculator evaluates process and purchaser data that can be checked without detailed vendor geometry. It does not
 * certify API 610 conformity. Final casing, rotor, bearing, nozzle-load, seal, baseplate and test compliance must be
 * verified against the purchased standard, project specification and vendor documentation.
 * </p>
 *
 * <p>
 * Unknown vendor inputs are represented by {@link Double#NaN} and produce {@link CheckStatus#NOT_EVALUATED}, never a
 * silent pass.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PumpApi610DesignCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Standard gravitational acceleration [m/s2]. */
  private static final double GRAVITY = 9.80665;

  /** Exact API 610 centrifugal-pump construction types. */
  public enum Api610PumpType {
    /** Type is not yet selected by purchaser or vendor. */
    UNSPECIFIED,
    /** Foot-mounted overhung pump. */
    OH1,
    /** Centreline-mounted overhung pump. */
    OH2,
    /** Vertical in-line pump with separate bearing bracket. */
    OH3,
    /** Rigidly coupled vertical in-line pump. */
    OH4,
    /** Close-coupled vertical in-line pump. */
    OH5,
    /** High-speed integrally geared overhung pump. */
    OH6,
    /** Axially split one- or two-stage between-bearings pump. */
    BB1,
    /** Radially split one- or two-stage between-bearings pump. */
    BB2,
    /** Axially split multistage between-bearings pump. */
    BB3,
    /** Radially split single-casing multistage pump. */
    BB4,
    /** Radially split double-casing multistage pump. */
    BB5,
    /** Wet-pit diffuser vertical pump. */
    VS1,
    /** Wet-pit volute vertical pump. */
    VS2,
    /** Wet-pit axial-flow vertical pump. */
    VS3,
    /** Vertical sump pump with line shaft. */
    VS4,
    /** Vertical cantilever sump pump. */
    VS5,
    /** Double-casing diffuser vertical pump. */
    VS6,
    /** Double-casing volute vertical pump. */
    VS7
  }

  /** Provenance of a design input. */
  public enum DataSource {
    /** Value obtained from the current process simulation. */
    PROCESS_MODEL,
    /** Value explicitly supplied by the purchaser or project. */
    PURCHASER_INPUT,
    /** Value obtained from a manufacturer performance curve. */
    VENDOR_CURVE,
    /** Value produced by a preliminary screening correlation or assumption. */
    SCREENING_ESTIMATE,
    /** Required data are not available. */
    NOT_AVAILABLE
  }

  /** Result of one screening check. */
  public enum CheckStatus {
    /** Requirement is met for the supplied data. */
    PASS,
    /** Point remains operable but needs engineering review. */
    WARNING,
    /** Supplied data do not meet the configured screening criterion. */
    FAIL,
    /** Required purchaser or vendor data were not supplied. */
    NOT_EVALUATED
  }

  /** Overall screening result. */
  public enum AssessmentStatus {
    /** All evaluated checks pass and there are no data gaps. */
    PASS,
    /** No evaluated check fails, but warnings or data gaps remain. */
    PASS_WITH_WARNINGS,
    /** At least one evaluated check fails. */
    FAIL,
    /** No check could be evaluated. */
    NOT_EVALUATED
  }

  /** Bearing rolling-element type for the ISO 281 basic rating-life exponent. */
  public enum BearingType {
    /** Ball bearing, life exponent p = 3. */
    BALL,
    /** Roller bearing, life exponent p = 10/3. */
    ROLLER,
    /** Sleeve or hydrodynamic bearing; ISO 281 L10 is not applicable. */
    SLEEVE
  }

  /** One auditable screening check. */
  public static class Check implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String requirement;
    private final CheckStatus status;
    private final double actualValue;
    private final double limitValue;
    private final String unit;
    private final String message;

    Check(String id, String requirement, CheckStatus status, double actualValue, double limitValue, String unit,
        String message) {
      this.id = id;
      this.requirement = requirement;
      this.status = status;
      this.actualValue = actualValue;
      this.limitValue = limitValue;
      this.unit = unit;
      this.message = message;
    }

    public String getId() {
      return id;
    }

    public String getRequirement() {
      return requirement;
    }

    public CheckStatus getStatus() {
      return status;
    }

    public double getActualValue() {
      return actualValue;
    }

    public double getLimitValue() {
      return limitValue;
    }

    public String getUnit() {
      return unit;
    }

    public String getMessage() {
      return message;
    }
  }

  // Identification and provenance.
  private String standardEdition = "13th";
  private String assessmentBasis = "API 610 screening; vendor and purchaser verification required";
  private Api610PumpType pumpType = Api610PumpType.UNSPECIFIED;
  private DataSource pumpTypeSource = DataSource.NOT_AVAILABLE;
  private DataSource dutyPointSource = DataSource.PROCESS_MODEL;
  private DataSource bepSource = DataSource.NOT_AVAILABLE;
  private DataSource npshrSource = DataSource.NOT_AVAILABLE;
  private DataSource shutoffHeadSource = DataSource.NOT_AVAILABLE;

  // Duty and vendor inputs.
  private double operatingFlowM3h = Double.NaN;
  private double ratedHeadM = Double.NaN;
  private double ratedSpeedRpm = Double.NaN;
  private double fluidDensityKgM3 = Double.NaN;
  private double absorbedPowerKw = Double.NaN;
  private double bepFlowM3h = Double.NaN;
  private double bepHeadM = Double.NaN;
  private double npshAvailableM = Double.NaN;
  private double npshRequiredM = Double.NaN;
  private double maximumSuctionPressureBara = Double.NaN;
  private double furnishedMawpBara = Double.NaN;
  private double shutoffHeadM = Double.NaN;
  private double hydrostaticTestPressureBara = Double.NaN;

  // Configurable screening criteria.
  private double porLowFraction = 0.70;
  private double porHighFraction = 1.20;
  private double ratedPointLowFraction = 0.80;
  private double ratedPointHighFraction = 1.10;
  private double aorLowFraction = 0.60;
  private double aorHighFraction = 1.30;
  private double npshMarginFactor = 1.15;
  private double minimumNpshMarginM = 1.0;
  private double assumedShutoffHeadFactor = 1.10;
  private double minimumHeadRiseToShutoffFraction = 0.10;
  private double hydrostaticTestPressureFactor = 1.50;
  private double driverMarginFactor = 1.10;
  private double minimumBearingLifeHours = 25000.0;
  private double maximumShaftDeflectionMm = 0.05;
  private double minimumCriticalSpeedRatio = 1.20;
  private double maximumNozzleLoadUtilization = 1.0;
  private double maximumVibrationVelocityMmS = 3.0;

  // Optional mechanical evidence.
  private BearingType bearingType = BearingType.BALL;
  private double bearingDynamicLoadRatingKn = Double.NaN;
  private double bearingEquivalentDynamicLoadKn = Double.NaN;
  private double shaftDeflectionMm = Double.NaN;
  private double firstCriticalSpeedRpm = Double.NaN;
  private double nozzleLoadUtilization = Double.NaN;
  private double vibrationVelocityMmS = Double.NaN;
  private boolean seallessPump;

  // Candidate driver ratings and calculated results.
  private double[] driverCandidateRatingsKw = new double[] { 0.75, 1.1, 1.5, 2.2, 3.0, 4.0, 5.5, 7.5, 11.0, 15.0, 18.5,
      22.0, 30.0, 37.0, 45.0, 55.0, 75.0, 90.0, 110.0, 132.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0,
      1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0 };
  private double operatingFlowRatio = Double.NaN;
  private String operatingRegion = "NOT_EVALUATED";
  private double npshMarginM = Double.NaN;
  private double npshMarginRatio = Double.NaN;
  private double governingShutoffHeadM = Double.NaN;
  private double requiredCasingPressureBara = Double.NaN;
  private double preliminaryHydrostaticTestPressureBara = Double.NaN;
  private double headRiseToShutoffFraction = Double.NaN;
  private double requiredDriverPowerKw = Double.NaN;
  private double selectedDriverPowerKw = Double.NaN;
  private double bearingL10LifeHours = Double.NaN;
  private double criticalSpeedRatio = Double.NaN;
  private AssessmentStatus assessmentStatus = AssessmentStatus.NOT_EVALUATED;
  private final List<Check> checks = new ArrayList<Check>();

  /**
   * Copy all input data and screening criteria without copying calculated results.
   *
   * <p>
   * The engineering-workflow adapter uses this method to execute the legacy mutable calculator
   * without mutating caller-owned state.
   * </p>
   *
   * @return independent calculator configuration
   */
  public PumpApi610DesignCalculator copyConfiguration() {
    PumpApi610DesignCalculator copy = new PumpApi610DesignCalculator();
    copy.standardEdition = standardEdition;
    copy.assessmentBasis = assessmentBasis;
    copy.pumpType = pumpType;
    copy.pumpTypeSource = pumpTypeSource;
    copy.dutyPointSource = dutyPointSource;
    copy.bepSource = bepSource;
    copy.npshrSource = npshrSource;
    copy.shutoffHeadSource = shutoffHeadSource;
    copy.operatingFlowM3h = operatingFlowM3h;
    copy.ratedHeadM = ratedHeadM;
    copy.ratedSpeedRpm = ratedSpeedRpm;
    copy.fluidDensityKgM3 = fluidDensityKgM3;
    copy.absorbedPowerKw = absorbedPowerKw;
    copy.bepFlowM3h = bepFlowM3h;
    copy.bepHeadM = bepHeadM;
    copy.npshAvailableM = npshAvailableM;
    copy.npshRequiredM = npshRequiredM;
    copy.maximumSuctionPressureBara = maximumSuctionPressureBara;
    copy.furnishedMawpBara = furnishedMawpBara;
    copy.shutoffHeadM = shutoffHeadM;
    copy.hydrostaticTestPressureBara = hydrostaticTestPressureBara;
    copy.porLowFraction = porLowFraction;
    copy.porHighFraction = porHighFraction;
    copy.ratedPointLowFraction = ratedPointLowFraction;
    copy.ratedPointHighFraction = ratedPointHighFraction;
    copy.aorLowFraction = aorLowFraction;
    copy.aorHighFraction = aorHighFraction;
    copy.npshMarginFactor = npshMarginFactor;
    copy.minimumNpshMarginM = minimumNpshMarginM;
    copy.assumedShutoffHeadFactor = assumedShutoffHeadFactor;
    copy.minimumHeadRiseToShutoffFraction = minimumHeadRiseToShutoffFraction;
    copy.hydrostaticTestPressureFactor = hydrostaticTestPressureFactor;
    copy.driverMarginFactor = driverMarginFactor;
    copy.minimumBearingLifeHours = minimumBearingLifeHours;
    copy.maximumShaftDeflectionMm = maximumShaftDeflectionMm;
    copy.minimumCriticalSpeedRatio = minimumCriticalSpeedRatio;
    copy.maximumNozzleLoadUtilization = maximumNozzleLoadUtilization;
    copy.maximumVibrationVelocityMmS = maximumVibrationVelocityMmS;
    copy.bearingType = bearingType;
    copy.bearingDynamicLoadRatingKn = bearingDynamicLoadRatingKn;
    copy.bearingEquivalentDynamicLoadKn = bearingEquivalentDynamicLoadKn;
    copy.shaftDeflectionMm = shaftDeflectionMm;
    copy.firstCriticalSpeedRpm = firstCriticalSpeedRpm;
    copy.nozzleLoadUtilization = nozzleLoadUtilization;
    copy.vibrationVelocityMmS = vibrationVelocityMmS;
    copy.seallessPump = seallessPump;
    copy.driverCandidateRatingsKw = driverCandidateRatingsKw.clone();
    return copy;
  }

  /** Evaluates all configured checks and replaces any previous result. */
  public void calculate() {
    checks.clear();
    evaluateOperatingRegion();
    evaluateNpsh();
    evaluatePressureCasing();
    evaluateHydrostaticTest();
    evaluateHeadCurve();
    evaluateDriver();
    evaluateBearingLife();
    evaluateOptionalMechanicalEvidence();
    evaluateScope();
    updateAssessmentStatus();
  }

  private void evaluateOperatingRegion() {
    if (!isPositiveFinite(operatingFlowM3h) || !isPositiveFinite(bepFlowM3h)) {
      operatingFlowRatio = Double.NaN;
      operatingRegion = "NOT_EVALUATED";
      addNotEvaluated("operating-region", "Rated flow shall be evaluated relative to vendor BEP",
          "Vendor BEP flow is not available");
      return;
    }
    operatingFlowRatio = operatingFlowM3h / bepFlowM3h;
    if (operatingFlowRatio >= ratedPointLowFraction && operatingFlowRatio <= ratedPointHighFraction) {
      operatingRegion = "POR";
      addCheck("operating-region", "Rated flow within configured rated-point region", CheckStatus.PASS,
          operatingFlowRatio, ratedPointHighFraction, "Q/Qbep", "Rated point is within the configured rated region");
    } else if (operatingFlowRatio >= porLowFraction && operatingFlowRatio <= porHighFraction) {
      operatingRegion = "POR";
      addCheck("operating-region", "Rated flow within configured rated-point region", CheckStatus.WARNING,
          operatingFlowRatio,
          operatingFlowRatio < ratedPointLowFraction ? ratedPointLowFraction : ratedPointHighFraction, "Q/Qbep",
          "Duty is inside POR but outside the configured rated-point region");
    } else if (operatingFlowRatio >= aorLowFraction && operatingFlowRatio <= aorHighFraction) {
      operatingRegion = "AOR";
      addCheck("operating-region", "Rated flow within configured allowable operating region", CheckStatus.WARNING,
          operatingFlowRatio, operatingFlowRatio < porLowFraction ? porLowFraction : porHighFraction, "Q/Qbep",
          "Rated point is outside POR but remains inside AOR");
    } else {
      operatingRegion = "OUTSIDE_AOR";
      addCheck("operating-region", "Rated flow within configured allowable operating region", CheckStatus.FAIL,
          operatingFlowRatio, operatingFlowRatio < aorLowFraction ? aorLowFraction : aorHighFraction, "Q/Qbep",
          "Rated point is outside the configured allowable operating region");
    }
  }

  private void evaluateNpsh() {
    if (!isFinite(npshAvailableM) || !isPositiveFinite(npshRequiredM)) {
      npshMarginM = Double.NaN;
      npshMarginRatio = Double.NaN;
      addNotEvaluated("npsh-margin", "NPSHa shall exceed NPSHr by the configured head and ratio margins",
          "Reliable NPSHa or vendor NPSHr is not available");
      return;
    }
    npshMarginM = npshAvailableM - npshRequiredM;
    npshMarginRatio = npshAvailableM / npshRequiredM;
    boolean ratioPass = npshMarginRatio >= npshMarginFactor;
    boolean headPass = npshMarginM >= minimumNpshMarginM;
    CheckStatus status = ratioPass && headPass ? CheckStatus.PASS : CheckStatus.FAIL;
    addCheck("npsh-margin", "NPSHa shall exceed NPSHr by configured head and ratio margins", status, npshMarginM,
        minimumNpshMarginM, "m",
        "NPSH margin ratio=" + format(npshMarginRatio) + ", required ratio=" + format(npshMarginFactor));
  }

  private void evaluatePressureCasing() {
    if (!isPositiveFinite(maximumSuctionPressureBara) || !isPositiveFinite(ratedHeadM)
        || !isPositiveFinite(fluidDensityKgM3)) {
      governingShutoffHeadM = Double.NaN;
      requiredCasingPressureBara = Double.NaN;
      preliminaryHydrostaticTestPressureBara = Double.NaN;
      addNotEvaluated("pressure-casing", "Pressure casing MAWP shall cover maximum suction plus shutoff head",
          "Maximum suction pressure, rated head or liquid density is not available");
      return;
    }
    if (isPositiveFinite(shutoffHeadM)) {
      governingShutoffHeadM = shutoffHeadM;
    } else {
      governingShutoffHeadM = ratedHeadM * assumedShutoffHeadFactor;
      shutoffHeadSource = DataSource.SCREENING_ESTIMATE;
    }
    requiredCasingPressureBara = maximumSuctionPressureBara
        + fluidDensityKgM3 * GRAVITY * governingShutoffHeadM / 1.0e5;
    double hydrotestBasis = isPositiveFinite(furnishedMawpBara) ? furnishedMawpBara : requiredCasingPressureBara;
    preliminaryHydrostaticTestPressureBara = hydrotestBasis * hydrostaticTestPressureFactor;
    if (!isPositiveFinite(furnishedMawpBara)) {
      addNotEvaluated("pressure-casing", "Furnished casing MAWP shall cover the required casing pressure",
          "Required pressure calculated; furnished casing MAWP is not available");
      return;
    }
    CheckStatus status = furnishedMawpBara >= requiredCasingPressureBara ? CheckStatus.PASS : CheckStatus.FAIL;
    addCheck("pressure-casing", "Furnished casing MAWP shall cover maximum suction plus shutoff head", status,
        furnishedMawpBara, requiredCasingPressureBara, "bara",
        "Required casing pressure uses shutoff-head source " + shutoffHeadSource);
  }

  private void evaluateHeadCurve() {
    if (shutoffHeadSource != DataSource.VENDOR_CURVE && shutoffHeadSource != DataSource.PURCHASER_INPUT) {
      headRiseToShutoffFraction = Double.NaN;
      addNotEvaluated("head-rise", "Head rise from rated flow to shutoff shall meet the configured minimum",
          "A vendor or purchaser shutoff head is not available");
      return;
    }
    if (!isPositiveFinite(shutoffHeadM) || !isPositiveFinite(ratedHeadM)) {
      headRiseToShutoffFraction = Double.NaN;
      addNotEvaluated("head-rise", "Head rise from rated flow to shutoff shall meet the configured minimum",
          "Rated or shutoff head is not available");
      return;
    }
    headRiseToShutoffFraction = shutoffHeadM / ratedHeadM - 1.0;
    CheckStatus status = headRiseToShutoffFraction >= minimumHeadRiseToShutoffFraction ? CheckStatus.PASS
        : CheckStatus.FAIL;
    addCheck("head-rise", "Head rise from rated flow to shutoff shall meet the configured minimum", status,
        headRiseToShutoffFraction, minimumHeadRiseToShutoffFraction, "fraction",
        "Endpoint screening does not replace verification of a continuously rising H-Q curve");
  }

  private void evaluateHydrostaticTest() {
    if (!isPositiveFinite(preliminaryHydrostaticTestPressureBara)) {
      addNotEvaluated("hydrostatic-test", "Casing hydrostatic test pressure shall meet the configured pressure factor",
          "Casing pressure basis is not available");
      return;
    }
    if (!isPositiveFinite(hydrostaticTestPressureBara)) {
      addNotEvaluated("hydrostatic-test", "Casing hydrostatic test pressure shall meet the configured pressure factor",
          "Required test pressure calculated; vendor test pressure is not available");
      return;
    }
    CheckStatus status = hydrostaticTestPressureBara >= preliminaryHydrostaticTestPressureBara ? CheckStatus.PASS
        : CheckStatus.FAIL;
    addCheck("hydrostatic-test", "Casing hydrostatic test pressure shall meet the configured pressure factor", status,
        hydrostaticTestPressureBara, preliminaryHydrostaticTestPressureBara, "bara", "Vendor test-pressure evidence");
  }

  private void evaluateDriver() {
    if (!isPositiveFinite(absorbedPowerKw)) {
      requiredDriverPowerKw = Double.NaN;
      selectedDriverPowerKw = Double.NaN;
      addNotEvaluated("driver-rating", "Driver rating shall cover absorbed power with configured margin",
          "Maximum absorbed power is not available");
      return;
    }
    requiredDriverPowerKw = absorbedPowerKw * driverMarginFactor;
    selectedDriverPowerKw = selectDriverRating(requiredDriverPowerKw, driverCandidateRatingsKw);
    if (!isPositiveFinite(selectedDriverPowerKw)) {
      addCheck("driver-rating", "Driver rating shall cover absorbed power with configured margin", CheckStatus.FAIL,
          requiredDriverPowerKw, maximum(driverCandidateRatingsKw), "kW",
          "No configured driver candidate covers the required power");
      return;
    }
    addCheck("driver-rating", "Driver rating shall cover absorbed power with configured margin", CheckStatus.PASS,
        selectedDriverPowerKw, requiredDriverPowerKw, "kW", "Selected next configured driver rating");
  }

  private void evaluateBearingLife() {
    if (bearingType == BearingType.SLEEVE) {
      bearingL10LifeHours = Double.NaN;
      addNotEvaluated("bearing-life", "Bearing life shall be verified for the governing radial and axial loads",
          "ISO 281 rolling-element L10 calculation is not applicable to sleeve bearings");
      return;
    }
    bearingL10LifeHours = calculateBearingL10LifeHours(bearingDynamicLoadRatingKn, bearingEquivalentDynamicLoadKn,
        ratedSpeedRpm, bearingType);
    if (!isPositiveFinite(bearingL10LifeHours)) {
      addNotEvaluated("bearing-life", "Bearing L10 life shall meet the configured minimum",
          "Bearing dynamic rating, equivalent load or rated speed is not available");
      return;
    }
    CheckStatus status = bearingL10LifeHours >= minimumBearingLifeHours ? CheckStatus.PASS : CheckStatus.FAIL;
    addCheck("bearing-life", "Bearing L10 life shall meet the configured minimum", status, bearingL10LifeHours,
        minimumBearingLifeHours, "h", "ISO 281 basic rating-life screening calculation");
  }

  private void evaluateOptionalMechanicalEvidence() {
    evaluateMaximum("shaft-deflection", "Shaft deflection at the seal shall not exceed the configured limit",
        shaftDeflectionMm, maximumShaftDeflectionMm, "mm", "Vendor shaft analysis is not available");

    if (isPositiveFinite(firstCriticalSpeedRpm) && isPositiveFinite(ratedSpeedRpm)) {
      criticalSpeedRatio = firstCriticalSpeedRpm / ratedSpeedRpm;
      CheckStatus status = criticalSpeedRatio >= minimumCriticalSpeedRatio ? CheckStatus.PASS : CheckStatus.FAIL;
      addCheck("critical-speed", "First critical speed shall meet the configured separation ratio", status,
          criticalSpeedRatio, minimumCriticalSpeedRatio, "ratio", "Dry critical-speed screening");
    } else {
      criticalSpeedRatio = Double.NaN;
      addNotEvaluated("critical-speed", "Critical-speed separation shall be verified",
          "Vendor rotor-dynamic analysis is not available");
    }

    evaluateMaximum("nozzle-loads", "Combined nozzle-load utilization shall not exceed unity", nozzleLoadUtilization,
        maximumNozzleLoadUtilization, "fraction", "Nozzle loads or allowables are not available");
    evaluateMaximum("vibration", "Vibration velocity shall not exceed the configured limit", vibrationVelocityMmS,
        maximumVibrationVelocityMmS, "mm/s", "Vendor or measured vibration data are not available");
  }

  private void evaluateScope() {
    if (pumpType == Api610PumpType.UNSPECIFIED) {
      addNotEvaluated("pump-type", "Exact API 610 construction type shall be selected",
          "Only a preliminary pump family recommendation is available");
    } else if (pumpTypeSource == DataSource.SCREENING_ESTIMATE) {
      addCheck("pump-type", "Exact API 610 construction type shall be selected", CheckStatus.WARNING, Double.NaN,
          Double.NaN, "", "Preliminary recommended construction type " + pumpType + " requires purchaser selection");
    } else {
      addCheck("pump-type", "Exact API 610 construction type shall be selected", CheckStatus.PASS, Double.NaN,
          Double.NaN, "", "Selected construction type " + pumpType);
    }
    if (seallessPump) {
      addCheck("standard-scope", "Sealless pump construction requires the applicable sealless-pump standard",
          CheckStatus.WARNING, Double.NaN, Double.NaN, "",
          "Magnetic-drive and canned-motor pumps are outside API 610 scope");
    }
  }

  private void evaluateMaximum(String id, String requirement, double actual, double limit, String unit,
      String missingMessage) {
    if (!isFinite(actual)) {
      addNotEvaluated(id, requirement, missingMessage);
      return;
    }
    CheckStatus status = actual <= limit ? CheckStatus.PASS : CheckStatus.FAIL;
    addCheck(id, requirement, status, actual, limit, unit,
        status == CheckStatus.PASS ? "Criterion met" : "Criterion exceeded");
  }

  private void updateAssessmentStatus() {
    if (checks.isEmpty()) {
      assessmentStatus = AssessmentStatus.NOT_EVALUATED;
      return;
    }
    boolean evaluated = false;
    boolean warningOrGap = false;
    for (Check check : checks) {
      if (check.getStatus() == CheckStatus.FAIL) {
        assessmentStatus = AssessmentStatus.FAIL;
        return;
      }
      if (check.getStatus() == CheckStatus.WARNING || check.getStatus() == CheckStatus.NOT_EVALUATED) {
        warningOrGap = true;
      } else {
        evaluated = true;
      }
    }
    if (!evaluated) {
      assessmentStatus = AssessmentStatus.NOT_EVALUATED;
    } else if (warningOrGap) {
      assessmentStatus = AssessmentStatus.PASS_WITH_WARNINGS;
    } else {
      assessmentStatus = AssessmentStatus.PASS;
    }
  }

  private void addNotEvaluated(String id, String requirement, String message) {
    addCheck(id, requirement, CheckStatus.NOT_EVALUATED, Double.NaN, Double.NaN, "", message);
  }

  private void addCheck(String id, String requirement, CheckStatus status, double actual, double limit, String unit,
      String message) {
    checks.add(new Check(id, requirement, status, actual, limit, unit, message));
  }

  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  private static boolean isPositiveFinite(double value) {
    return isFinite(value) && value > 0.0;
  }

  private static String format(double value) {
    return isFinite(value) ? String.format("%.3f", value) : "not available";
  }

  private static double maximum(double[] values) {
    double result = Double.NaN;
    if (values == null) {
      return result;
    }
    for (double value : values) {
      if (isFinite(value) && (Double.isNaN(result) || value > result)) {
        result = value;
      }
    }
    return result;
  }

  /**
   * Selects the smallest configured driver rating that covers the required power.
   *
   * @param requiredPowerKw required driver power in kW
   * @param candidatesKw available ratings in kW; ordering is not significant
   * @return selected rating in kW, or NaN if none is suitable
   */
  public static double selectDriverRating(double requiredPowerKw, double[] candidatesKw) {
    if (!isPositiveFinite(requiredPowerKw) || candidatesKw == null) {
      return Double.NaN;
    }
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : candidatesKw) {
      if (isPositiveFinite(candidate) && candidate >= requiredPowerKw && candidate < selected) {
        selected = candidate;
      }
    }
    return Double.isInfinite(selected) ? Double.NaN : selected;
  }

  /**
   * Calculates ISO 281 basic rating life for a rolling-element bearing.
   *
   * @param dynamicLoadRatingKn basic dynamic load rating C in kN
   * @param equivalentDynamicLoadKn equivalent dynamic bearing load P in kN
   * @param speedRpm bearing speed in rpm
   * @param type rolling-element bearing type
   * @return L10 life in hours, or NaN for incomplete or inapplicable input
   */
  public static double calculateBearingL10LifeHours(double dynamicLoadRatingKn, double equivalentDynamicLoadKn,
      double speedRpm, BearingType type) {
    if (!isPositiveFinite(dynamicLoadRatingKn) || !isPositiveFinite(equivalentDynamicLoadKn)
        || !isPositiveFinite(speedRpm) || type == null || type == BearingType.SLEEVE) {
      return Double.NaN;
    }
    double exponent = type == BearingType.BALL ? 3.0 : 10.0 / 3.0;
    double lifeMillionRevolutions = Math.pow(dynamicLoadRatingKn / equivalentDynamicLoadKn, exponent);
    return lifeMillionRevolutions * 1.0e6 / (60.0 * speedRpm);
  }

  public String getStandardEdition() {
    return standardEdition;
  }

  public void setStandardEdition(String standardEdition) {
    this.standardEdition = standardEdition;
  }

  public String getAssessmentBasis() {
    return assessmentBasis;
  }

  public Api610PumpType getPumpType() {
    return pumpType;
  }

  public void setPumpType(Api610PumpType pumpType) {
    setPumpType(pumpType, DataSource.PURCHASER_INPUT);
  }

  public void setPumpType(Api610PumpType pumpType, DataSource source) {
    this.pumpType = pumpType == null ? Api610PumpType.UNSPECIFIED : pumpType;
    this.pumpTypeSource = this.pumpType == Api610PumpType.UNSPECIFIED ? DataSource.NOT_AVAILABLE
        : source == null ? DataSource.NOT_AVAILABLE : source;
  }

  public DataSource getPumpTypeSource() {
    return pumpTypeSource;
  }

  public DataSource getDutyPointSource() {
    return dutyPointSource;
  }

  public void setDutyPointSource(DataSource dutyPointSource) {
    this.dutyPointSource = dutyPointSource;
  }

  public DataSource getBepSource() {
    return bepSource;
  }

  public void setBepSource(DataSource bepSource) {
    this.bepSource = bepSource;
  }

  public DataSource getNpshrSource() {
    return npshrSource;
  }

  public void setNpshrSource(DataSource npshrSource) {
    this.npshrSource = npshrSource;
  }

  public DataSource getShutoffHeadSource() {
    return shutoffHeadSource;
  }

  public void setShutoffHeadSource(DataSource shutoffHeadSource) {
    this.shutoffHeadSource = shutoffHeadSource;
  }

  public void setDutyPoint(double flowM3h, double headM, double speedRpm, double densityKgM3, double absorbedPowerKw) {
    this.operatingFlowM3h = flowM3h;
    this.ratedHeadM = headM;
    this.ratedSpeedRpm = speedRpm;
    this.fluidDensityKgM3 = densityKgM3;
    this.absorbedPowerKw = absorbedPowerKw;
  }

  public void setBepPoint(double flowM3h, double headM, DataSource source) {
    this.bepFlowM3h = flowM3h;
    this.bepHeadM = headM;
    this.bepSource = source == null ? DataSource.NOT_AVAILABLE : source;
  }

  public void setNpsh(double availableM, double requiredM, DataSource requiredSource) {
    this.npshAvailableM = availableM;
    this.npshRequiredM = requiredM;
    this.npshrSource = requiredSource == null ? DataSource.NOT_AVAILABLE : requiredSource;
  }

  public void setPressureBasis(double maximumSuctionPressureBara, double furnishedMawpBara, double shutoffHeadM,
      DataSource shutoffSource) {
    this.maximumSuctionPressureBara = maximumSuctionPressureBara;
    this.furnishedMawpBara = furnishedMawpBara;
    this.shutoffHeadM = shutoffHeadM;
    this.shutoffHeadSource = shutoffSource == null ? DataSource.NOT_AVAILABLE : shutoffSource;
  }

  public void setHydrostaticTestPressureBara(double hydrostaticTestPressureBara) {
    this.hydrostaticTestPressureBara = hydrostaticTestPressureBara;
  }

  public void setOperatingRegions(double porLow, double porHigh, double aorLow, double aorHigh) {
    this.porLowFraction = porLow;
    this.porHighFraction = porHigh;
    this.aorLowFraction = aorLow;
    this.aorHighFraction = aorHigh;
  }

  public void setRatedPointRegion(double ratedLow, double ratedHigh) {
    this.ratedPointLowFraction = ratedLow;
    this.ratedPointHighFraction = ratedHigh;
  }

  public void setNpshCriteria(double marginFactor, double minimumMarginM) {
    this.npshMarginFactor = marginFactor;
    this.minimumNpshMarginM = minimumMarginM;
  }

  public void setPressureCriteria(double assumedShutoffHeadFactor, double hydrostaticTestPressureFactor) {
    this.assumedShutoffHeadFactor = assumedShutoffHeadFactor;
    this.hydrostaticTestPressureFactor = hydrostaticTestPressureFactor;
  }

  public void setMinimumHeadRiseToShutoffFraction(double minimumHeadRiseToShutoffFraction) {
    this.minimumHeadRiseToShutoffFraction = minimumHeadRiseToShutoffFraction;
  }

  public void setDriverCriteria(double marginFactor, double[] candidateRatingsKw) {
    this.driverMarginFactor = marginFactor;
    if (candidateRatingsKw != null) {
      this.driverCandidateRatingsKw = candidateRatingsKw.clone();
    }
  }

  public void setBearingData(BearingType type, double dynamicLoadRatingKn, double equivalentDynamicLoadKn) {
    this.bearingType = type == null ? BearingType.BALL : type;
    this.bearingDynamicLoadRatingKn = dynamicLoadRatingKn;
    this.bearingEquivalentDynamicLoadKn = equivalentDynamicLoadKn;
  }

  public void setMechanicalEvidence(double shaftDeflectionMm, double firstCriticalSpeedRpm,
      double nozzleLoadUtilization, double vibrationVelocityMmS) {
    this.shaftDeflectionMm = shaftDeflectionMm;
    this.firstCriticalSpeedRpm = firstCriticalSpeedRpm;
    this.nozzleLoadUtilization = nozzleLoadUtilization;
    this.vibrationVelocityMmS = vibrationVelocityMmS;
  }

  public void setMechanicalCriteria(double minimumBearingLifeHours, double maximumShaftDeflectionMm,
      double minimumCriticalSpeedRatio, double maximumNozzleLoadUtilization, double maximumVibrationVelocityMmS) {
    this.minimumBearingLifeHours = minimumBearingLifeHours;
    this.maximumShaftDeflectionMm = maximumShaftDeflectionMm;
    this.minimumCriticalSpeedRatio = minimumCriticalSpeedRatio;
    this.maximumNozzleLoadUtilization = maximumNozzleLoadUtilization;
    this.maximumVibrationVelocityMmS = maximumVibrationVelocityMmS;
  }

  public void setSeallessPump(boolean seallessPump) {
    this.seallessPump = seallessPump;
  }

  public double getOperatingFlowM3h() {
    return operatingFlowM3h;
  }

  public double getRatedHeadM() {
    return ratedHeadM;
  }

  public double getRatedSpeedRpm() {
    return ratedSpeedRpm;
  }

  public double getFluidDensityKgM3() {
    return fluidDensityKgM3;
  }

  public double getAbsorbedPowerKw() {
    return absorbedPowerKw;
  }

  public double getBepFlowM3h() {
    return bepFlowM3h;
  }

  public double getBepHeadM() {
    return bepHeadM;
  }

  public double getNpshAvailableM() {
    return npshAvailableM;
  }

  public double getNpshRequiredM() {
    return npshRequiredM;
  }

  public double getFurnishedMawpBara() {
    return furnishedMawpBara;
  }

  public double getOperatingFlowRatio() {
    return operatingFlowRatio;
  }

  public String getOperatingRegion() {
    return operatingRegion;
  }

  public double getNpshMarginM() {
    return npshMarginM;
  }

  public double getNpshMarginRatio() {
    return npshMarginRatio;
  }

  public double getGoverningShutoffHeadM() {
    return governingShutoffHeadM;
  }

  public double getRequiredCasingPressureBara() {
    return requiredCasingPressureBara;
  }

  public double getPreliminaryHydrostaticTestPressureBara() {
    return preliminaryHydrostaticTestPressureBara;
  }

  public double getHydrostaticTestPressureBara() {
    return hydrostaticTestPressureBara;
  }

  public double getHeadRiseToShutoffFraction() {
    return headRiseToShutoffFraction;
  }

  public double getRequiredDriverPowerKw() {
    return requiredDriverPowerKw;
  }

  public double getSelectedDriverPowerKw() {
    return selectedDriverPowerKw;
  }

  public double getBearingL10LifeHours() {
    return bearingL10LifeHours;
  }

  public double getCriticalSpeedRatio() {
    return criticalSpeedRatio;
  }

  public AssessmentStatus getAssessmentStatus() {
    return assessmentStatus;
  }

  public List<Check> getChecks() {
    return Collections.unmodifiableList(checks);
  }

  public Check getCheck(String id) {
    for (Check check : checks) {
      if (check.getId().equals(id)) {
        return check;
      }
    }
    return null;
  }
}
