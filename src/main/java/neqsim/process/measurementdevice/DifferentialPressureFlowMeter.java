package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Shared physics for differential-pressure primary devices following ISO 5167-1 (general principles) and its
 * device-specific parts: ISO 5167-2 (orifice plates), ISO 5167-3 (nozzles), ISO 5167-4 (classical Venturi tubes), ISO
 * 5167-5 (cone meters) and ISO 5167-6 (wedge meters).
 *
 * <p>
 * Every part expands the same ISO 5167-1 general equation
 * </p>
 *
 * <pre>
 * qm = C / sqrt(1 - beta ^ 4) * epsilon * (pi / 4) * d ^ 2 * sqrt(2 * dP * rho1)
 * </pre>
 *
 * <p>
 * with <i>Re,D = 4 qm / (pi mu1 D)</i> and <i>Re,d = Re,D / beta</i>. The parts differ only in how the discharge
 * coefficient <i>C</i> and the expansibility factor <i>epsilon</i> are computed from the geometry, the Reynolds number
 * and the isentropic exponent; that is exactly the split between {@link #calcDischargeCoefficient(double, double)} and
 * {@link #getExpansibilityModel()} that each concrete device implements.
 * </p>
 *
 * <p>
 * The geometry is stored as an upstream pipe internal diameter <i>D</i> and a throat diameter <i>d</i>, with the
 * diameter ratio <i>beta = d / D</i> always recomputed on demand from the two. Devices with no physical throat bore
 * (the ISO 5167-5 cone meter and the ISO 5167-6 wedge meter) derive an equivalent <i>d = D beta</i> from their own
 * geometry (cone diameter, wedge gap height) and store that instead, so this base class only ever needs the one <i>(D,
 * d)</i> representation.
 * </p>
 *
 * <p>
 * The differential pressure is either set explicitly with {@link #setDifferentialPressure(double, String)} or read from
 * a linked {@link DifferentialPressureTransmitter}, which takes precedence when present. The upstream gas density, the
 * isentropic exponent and the dynamic viscosity are all read from the connected stream's gas phase by default (falling
 * back to the mixture when no gas phase is present), and can each be overridden with a sampling-derived value.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public abstract class DifferentialPressureFlowMeter extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<DifferentialPressureFlowMeter.DifferentialPressureFlowMeterState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(DifferentialPressureFlowMeter.class);

  /** Largest pressure-drop ratio dP/p1 for which any ISO 5167 expansibility factor is defined (p2/p1 &gt;= 0.75). */
  public static final double MAX_PRESSURE_DROP_RATIO = 0.25;

  /** Maximum number of Reynolds-number iterations before giving up. */
  private static final int MAX_ITERATIONS = 100;

  /** Relative convergence tolerance of the Reynolds-number iteration. */
  private static final double TOLERANCE = 1.0e-12;

  /** Upstream pipe internal diameter D [m]. */
  private double pipeDiameterMeters = 0.2;

  /** Throat diameter d [m], physical for orifice/nozzle/Venturi devices and derived (D * beta) for cone/wedge. */
  private double throatDiameterMeters = 0.1;

  /** Differential pressure across the device [Pa]. */
  private double differentialPressure = 0.0;

  /** Optional transmitter supplying the differential pressure; takes precedence when set. */
  private DifferentialPressureTransmitter differentialPressureTransmitter = null;

  /** Explicit upstream gas density [kg/m3], NaN when not set. */
  private double gasDensityOverride = Double.NaN;

  /** Explicit isentropic exponent [-], NaN when not set. */
  private double isentropicExponentOverride = Double.NaN;

  /** Explicit dynamic viscosity [kg/(m.sec)], NaN when not set. */
  private double dynamicViscosityOverride = Double.NaN;

  /** Pipe Reynolds number from the most recent dry-gas solve, NaN until first solved. */
  private double lastReynoldsNumberPipe = Double.NaN;

  /**
   * Constructor for DifferentialPressureFlowMeter.
   *
   * @param name device tag, must be non-null
   * @param stream the stream the meter is installed on, must be non-null
   */
  protected DifferentialPressureFlowMeter(String name, StreamInterface stream) {
    super(name, "kg/hr", stream);
  }

  /**
   * Calculates the discharge coefficient C for the given diameter ratio and pipe Reynolds number. Devices whose
   * coefficient does not depend on the Reynolds number (classical Venturi tubes, cone and wedge meters) simply ignore
   * the second argument.
   *
   * @param beta diameter ratio d/D [-]
   * @param reynoldsD pipe Reynolds number Re,D [-]; an initial estimate of 1.0e6 is used for the first iteration
   * @return discharge coefficient C [-]
   */
  protected abstract double calcDischargeCoefficient(double beta, double reynoldsD);

  /**
   * Returns the expansibility factor family used by this device.
   *
   * @return the expansibility model, never null
   */
  protected abstract ExpansibilityModel getExpansibilityModel();

  /**
   * Sets the upstream pipe internal diameter D.
   *
   * @param pipeDiameter pipe internal diameter, must be positive
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   */
  public void setPipeDiameter(double pipeDiameter, String unit) {
    this.pipeDiameterMeters = pipeDiameter * lengthConversionToMeter(unit);
  }

  /**
   * Getter for the upstream pipe internal diameter D.
   *
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   * @return pipe internal diameter in the requested unit
   */
  public double getPipeDiameter(String unit) {
    return pipeDiameterMeters / lengthConversionToMeter(unit);
  }

  /**
   * Sets the throat diameter d directly. Devices with a physical throat bore (orifice, nozzle, classical Venturi)
   * normally use {@link #setGeometry(double, double, String)} instead; devices with no physical throat (cone, wedge)
   * derive this value from their own geometry, passing {@link Double#NaN} to mark the geometry as unset/invalid (see
   * e.g. {@code WedgeFlowMeter.setWedgeRatio(double)}) rather than a positive throat diameter.
   *
   * @param throatDiameter throat diameter, normally positive; {@link Double#NaN} marks unset/invalid geometry
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   */
  public void setThroatDiameter(double throatDiameter, String unit) {
    this.throatDiameterMeters = throatDiameter * lengthConversionToMeter(unit);
  }

  /**
   * Getter for the throat diameter d (physical for orifice/nozzle/Venturi devices, the equivalent D * beta diameter for
   * cone and wedge meters).
   *
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   * @return throat diameter in the requested unit
   */
  public double getThroatDiameter(String unit) {
    return throatDiameterMeters / lengthConversionToMeter(unit);
  }

  /**
   * Sets the pipe and throat diameters together.
   *
   * @param pipeDiameter upstream pipe internal diameter D, must be greater than the throat diameter
   * @param throatDiameter throat diameter d, must be positive
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   */
  public void setGeometry(double pipeDiameter, double throatDiameter, String unit) {
    setPipeDiameter(pipeDiameter, unit);
    setThroatDiameter(throatDiameter, unit);
  }

  /**
   * Returns the diameter ratio beta = d / D, recomputed on demand from the pipe and throat diameters.
   *
   * @return diameter ratio [-], or NaN when the pipe diameter is not positive
   */
  public double getBetaRatio() {
    if (pipeDiameterMeters <= 0.0) {
      return Double.NaN;
    }
    return throatDiameterMeters / pipeDiameterMeters;
  }

  /**
   * Sets the measured differential pressure across the device.
   *
   * @param differentialPressure differential pressure, negative values are treated as no flow
   * @param unit pressure unit, one of "Pa", "kPa", "MPa", "bar", "mbar" or "psi"
   */
  public void setDifferentialPressure(double differentialPressure, String unit) {
    this.differentialPressure = differentialPressure * pressureConversionToPa(unit);
  }

  /**
   * Returns the differential pressure used by the meter. When a differential-pressure transmitter is linked, its
   * reading is returned; otherwise the value set with {@link #setDifferentialPressure(double, String)} is returned.
   *
   * @param unit pressure unit, one of "Pa", "kPa", "MPa", "bar", "mbar" or "psi"
   * @return differential pressure in the requested unit
   */
  public double getDifferentialPressure(String unit) {
    return getDifferentialPressurePa() / pressureConversionToPa(unit);
  }

  /**
   * Links a differential-pressure transmitter that supplies the differential pressure at run time. When set, the
   * transmitter reading takes precedence over any explicitly set value.
   *
   * @param transmitter transmitter to read the differential pressure from, or null to unlink
   */
  public void setDifferentialPressureTransmitter(DifferentialPressureTransmitter transmitter) {
    this.differentialPressureTransmitter = transmitter;
  }

  /**
   * Getter for the linked differential-pressure transmitter.
   *
   * @return the linked transmitter, or null when the differential pressure is set explicitly
   */
  public DifferentialPressureTransmitter getDifferentialPressureTransmitter() {
    return differentialPressureTransmitter;
  }

  /**
   * Overrides the upstream gas density instead of reading it from the connected stream.
   *
   * @param gasDensity upstream gas density, must be positive
   * @param unit density unit, one of "kg/m3" or "g/cm3"
   */
  public void setGasDensity(double gasDensity, String unit) {
    this.gasDensityOverride = gasDensity * densityConversionToKgPerM3(unit);
  }

  /**
   * Overrides the isentropic exponent used in the expansibility factor, for example when it is derived from an off-line
   * gas chromatograph rather than from the flowing stream.
   *
   * @param isentropicExponent isentropic exponent kappa, must be greater than 1
   */
  public void setIsentropicExponent(double isentropicExponent) {
    this.isentropicExponentOverride = isentropicExponent;
  }

  /**
   * Returns the isentropic exponent used in the expansibility calculation. The gas-phase value is used when a gas phase
   * is present, otherwise the mixture value is used.
   *
   * @return isentropic exponent kappa [-]
   */
  public double getIsentropicExponent() {
    if (!Double.isNaN(isentropicExponentOverride) && isentropicExponentOverride > 1.0) {
      return isentropicExponentOverride;
    }
    SystemInterface fluid = stream.getThermoSystem();
    try {
      if (fluid.hasPhaseType("gas")) {
        return fluid.getPhase("gas").getKappa();
      }
    } catch (Exception ex) {
      logger.debug("could not read gas phase kappa for {}, using mixture kappa", getName(), ex);
    }
    return fluid.getKappa();
  }

  /**
   * Overrides the dynamic viscosity used in the Reynolds number instead of reading it from the connected stream. Only
   * relevant for devices whose discharge coefficient depends on the Reynolds number.
   *
   * @param dynamicViscosity dynamic viscosity, must be positive
   * @param unit viscosity unit, one of "kg/msec", "cP" or "Pas"
   */
  public void setDynamicViscosity(double dynamicViscosity, String unit) {
    this.dynamicViscosityOverride = dynamicViscosity * viscosityConversionToKgPerMeterSecond(unit);
  }

  /**
   * Returns the dynamic viscosity used in the Reynolds number. The gas-phase value is used when a gas phase is present,
   * otherwise the mixture value is used.
   *
   * @return dynamic viscosity [kg/(m.sec)]
   */
  protected double getDynamicViscosity() {
    if (!Double.isNaN(dynamicViscosityOverride) && dynamicViscosityOverride > 0.0) {
      return dynamicViscosityOverride;
    }
    SystemInterface fluid = stream.getThermoSystem();
    try {
      if (fluid.hasPhaseType("gas")) {
        return fluid.getPhase("gas").getViscosity("kg/msec");
      }
    } catch (Exception ex) {
      logger.debug("could not read gas phase viscosity for {}, using mixture viscosity", getName(), ex);
    }
    return fluid.getViscosity("kg/msec");
  }

  /**
   * Returns the upstream gas density. These devices measure the gas phase, so the gas-phase density is used when the
   * stream is multiphase; for a single-phase gas stream this equals the mixture density.
   *
   * @return gas density [kg/m3]
   */
  protected double getGasDensity() {
    if (!Double.isNaN(gasDensityOverride) && gasDensityOverride > 0.0) {
      return gasDensityOverride;
    }
    SystemInterface fluid = stream.getThermoSystem();
    try {
      if (fluid.hasPhaseType("gas")) {
        return fluid.getPhase("gas").getDensity("kg/m3");
      }
    } catch (Exception ex) {
      logger.debug("could not read gas phase density for {}, using mixture density", getName(), ex);
    }
    return fluid.getDensity("kg/m3");
  }

  /**
   * Returns the upstream static pressure taken from the connected stream.
   *
   * @return upstream pressure [Pa]
   */
  protected double getUpstreamPressurePa() {
    return stream.getPressure("Pa");
  }

  /**
   * Returns the differential pressure in Pa, preferring a linked transmitter over the explicitly set value.
   *
   * @return differential pressure [Pa]
   */
  protected double getDifferentialPressurePa() {
    if (differentialPressureTransmitter != null) {
      return differentialPressureTransmitter.getMeasuredValue("Pa");
    }
    return differentialPressure;
  }

  /**
   * Returns the expansibility factor for the current stream conditions and differential pressure.
   *
   * @return expansibility factor epsilon [-], or NaN when the inputs are not physically valid
   */
  public double getExpansibilityFactor() {
    return getExpansibilityModel().calculate(getDifferentialPressurePa(), getUpstreamPressurePa(), getBetaRatio(),
        getIsentropicExponent());
  }

  /**
   * Returns the pipe Reynolds number Re,D from the most recently solved dry-gas mass flow rate.
   *
   * @return pipe Reynolds number [-], or NaN before the first solve
   */
  public double getReynoldsNumberPipe() {
    return lastReynoldsNumberPipe;
  }

  /**
   * Records the pipe Reynolds number Re,D of the most recently solved mass flow rate. Subclasses that post-process the
   * dry-gas result (e.g. a wet-gas correction) call this so {@link #getReynoldsNumberPipe()} and
   * {@link #getReynoldsNumberThroat()} reflect the final solved operating point rather than the dry-gas seed value.
   *
   * @param reynoldsNumberPipe pipe Reynolds number [-]
   */
  protected void setReynoldsNumberPipe(double reynoldsNumberPipe) {
    this.lastReynoldsNumberPipe = reynoldsNumberPipe;
  }

  /**
   * Returns the throat Reynolds number Re,d = Re,D / beta.
   *
   * @return throat Reynolds number [-], or NaN before the first solve
   */
  public double getReynoldsNumberThroat() {
    double beta = getBetaRatio();
    if (Double.isNaN(lastReynoldsNumberPipe) || Double.isNaN(beta) || beta <= 0.0) {
      return Double.NaN;
    }
    return lastReynoldsNumberPipe / beta;
  }

  /**
   * Checks whether the current operating point is within the ISO 5167 pressure-drop-ratio window shared by every
   * expansibility factor (p2 / p1 &gt;= 0.75).
   *
   * @return true when the pressure-drop ratio is within range
   */
  public boolean isWithinExpansibilityPressureRatio() {
    double p1 = getUpstreamPressurePa();
    double dp = getDifferentialPressurePa();
    if (p1 <= 0.0 || dp < 0.0) {
      return false;
    }
    return dp / p1 <= MAX_PRESSURE_DROP_RATIO;
  }

  /**
   * Lists the ISO 5167 limits of use that the current operating point violates.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window
   */
  public abstract List<String> getValidityViolations();

  /**
   * Checks whether the current operating point is inside the device's ISO 5167 limits of use.
   *
   * @return true when no limit is violated
   */
  public boolean isWithinValidityRange() {
    return getValidityViolations().isEmpty();
  }

  /**
   * Solves the dry-gas ISO 5167-1 general equation, iterating on the pipe Reynolds number when
   * {@link #calcDischargeCoefficient(double, double)} depends on it. Devices with a Reynolds-number-independent
   * discharge coefficient converge on the first pass. Never throws.
   *
   * @return mass flow rate [kg/sec], 0.0 when the differential pressure is not positive and NaN when the inputs are not
   * physically valid
   */
  protected double getMassFlowRatePerSecond() {
    double dp = getDifferentialPressurePa();
    if (dp <= 0.0) {
      lastReynoldsNumberPipe = Double.NaN;
      return 0.0;
    }
    double p1 = getUpstreamPressurePa();
    double beta = getBetaRatio();
    double density = getGasDensity();
    double mu = getDynamicViscosity();
    if (p1 <= 0.0 || density <= 0.0 || Double.isNaN(beta) || beta <= 0.0 || beta >= 1.0 || mu <= 0.0
        || pipeDiameterMeters <= 0.0) {
      lastReynoldsNumberPipe = Double.NaN;
      return Double.NaN;
    }
    double epsilon = getExpansibilityModel().calculate(dp, p1, beta, getIsentropicExponent());
    if (Double.isNaN(epsilon)) {
      lastReynoldsNumberPipe = Double.NaN;
      return Double.NaN;
    }
    double baseTerm = 1.0 / Math.sqrt(1.0 - Math.pow(beta, 4.0)) * epsilon * Math.PI / 4.0 * throatDiameterMeters
        * throatDiameterMeters * Math.sqrt(2.0 * dp * density);

    double reynoldsD = 1.0e6;
    double flow = baseTerm * calcDischargeCoefficient(beta, reynoldsD);
    if (!Double.isFinite(flow) || flow <= 0.0) {
      logger.warn("{}: non-physical discharge coefficient at the initial Re,D = {} guess", getName(), reynoldsD);
      lastReynoldsNumberPipe = Double.NaN;
      return Double.NaN;
    }
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      double updatedReynoldsD = 4.0 * flow / (Math.PI * mu * pipeDiameterMeters);
      double updatedFlow = baseTerm * calcDischargeCoefficient(beta, updatedReynoldsD);
      if (!Double.isFinite(updatedFlow) || updatedFlow <= 0.0) {
        logger.warn("{}: Reynolds-number iteration produced a non-finite or non-physical discharge coefficient at "
            + "Re,D = {}", getName(), updatedReynoldsD);
        lastReynoldsNumberPipe = Double.NaN;
        return Double.NaN;
      }
      if (Math.abs(updatedFlow - flow) <= TOLERANCE * Math.abs(updatedFlow)) {
        lastReynoldsNumberPipe = updatedReynoldsD;
        return updatedFlow;
      }
      flow = updatedFlow;
      reynoldsD = updatedReynoldsD;
    }
    logger.warn("{}: Reynolds-number iteration did not converge", getName());
    lastReynoldsNumberPipe = reynoldsD;
    return flow;
  }

  /**
   * Returns the mass flow rate derived from the differential pressure.
   *
   * @param unit mass flow unit, one of "kg/sec", "kg/min", "kg/hr", "kg/day" or "tonnes/year"
   * @return mass flow rate in the requested unit, 0.0 when the differential pressure is not positive
   */
  public double getMassFlowRate(String unit) {
    return getMassFlowRatePerSecond() / massFlowConversionToKgPerSecond(unit);
  }

  /**
   * Returns the actual (flowing-condition) volume flow rate. Standard-volume units (e.g. "Sm3/hr") are delegated to
   * {@link #getStandardVolumeFlowRate(String)} since the flowing gas density used here would otherwise be paired with a
   * standard-volume unit, which is dimensionally inconsistent.
   *
   * @param unit volume flow unit, one of "m3/sec", "m3/min", "m3/hr" or a standard volume unit accepted by
   * {@link #getStandardVolumeFlowRate(String)}
   * @return actual (or standard, when a standard-volume unit is requested) volume flow rate in the requested unit
   */
  public double getVolumeFlowRate(String unit) {
    if (isStandardVolumeUnit(unit)) {
      return getStandardVolumeFlowRate(unit);
    }
    double density = getGasDensity();
    if (density <= 0.0) {
      return Double.NaN;
    }
    return getMassFlowRatePerSecond() / density / volumeFlowConversionToM3PerSecond(unit);
  }

  /**
   * Returns the standard-condition volume flow rate, using the standard density (15 degC, 1 atm) of the stream fluid.
   * The gas-phase molar mass is used when a gas phase is present (falling back to the mixture molar mass otherwise), so
   * a wet-gas stream's liquid content does not bias the reported gas standard volume flow.
   *
   * @param unit standard volume flow unit, one of "Sm3/sec", "Sm3/hr", "Sm3/day", "kSm3/hr" or "MSm3/day"
   * @return standard volume flow rate in the requested unit
   */
  public double getStandardVolumeFlowRate(String unit) {
    double standardDensity = getGasStandardDensity();
    if (standardDensity <= 0.0) {
      return Double.NaN;
    }
    return getMassFlowRatePerSecond() / standardDensity / volumeFlowConversionToM3PerSecond(unit);
  }

  /**
   * Returns the standard density (15 degC, 1 atm) used by {@link #getStandardVolumeFlowRate(String)}, preferring the
   * gas-phase molar mass over the overall mixture molar mass when a gas phase is present.
   *
   * @return standard density [kg/Sm3]
   */
  private double getGasStandardDensity() {
    SystemInterface fluid = stream.getThermoSystem();
    try {
      if (fluid.hasPhaseType("gas")) {
        double molarMass = fluid.getPhase("gas").getMolarMass();
        return molarMass * neqsim.thermo.ThermodynamicConstantsInterface.atm
            / neqsim.thermo.ThermodynamicConstantsInterface.R
            / neqsim.thermo.ThermodynamicConstantsInterface.standardStateTemperature;
      }
    } catch (Exception ex) {
      logger.debug("could not read gas phase molar mass for {}, using mixture standard density", getName(), ex);
    }
    return fluid.getDensity("kg/Sm3");
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    double value;
    if (isStandardVolumeUnit(unit)) {
      value = getStandardVolumeFlowRate(unit);
    } else if (isActualVolumeUnit(unit)) {
      value = getVolumeFlowRate(unit);
    } else {
      value = getMassFlowRate(unit);
    }
    return applySignalModifiers(value);
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:differential-pressure-flow:" + transientStateParticipantId;
  }

  /**
   * Reports whether the concrete primary-device subtype has complete extension-state coverage.
   *
   * <p>
   * The default is deliberately fail-closed. Each qualified concrete subtype must override this method and reject its
   * own descendants.
   * </p>
   *
   * @return blocking diagnostic for an unqualified subtype, otherwise {@code null}
   */
  protected String getDifferentialPressureFlowMeterTransientStateCoverageIssue() {
    return "differential-pressure-flow-meter subtype " + getClass().getName()
        + " must provide a snapshot that includes subtype-owned mutable state";
  }

  /**
   * Captures subtype-owned mutable state.
   *
   * @return serializable subtype state, never {@code null} for a qualified subtype
   */
  protected Serializable captureDifferentialPressureFlowMeterExtensionState() {
    return null;
  }

  /**
   * Restores subtype-owned mutable state.
   *
   * @param extensionState state returned by {@link #captureDifferentialPressureFlowMeterExtensionState()}
   */
  protected void restoreDifferentialPressureFlowMeterExtensionState(Serializable extensionState) {
    if (extensionState != null) {
      throw new IllegalArgumentException(
          "Unqualified differential-pressure flow-meter subtype state cannot be restored");
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    String measurementIssue = getMeasurementTransientStateCoverageIssue();
    if (measurementIssue != null) {
      return measurementIssue;
    }
    return getDifferentialPressureFlowMeterTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public DifferentialPressureFlowMeterState captureTransientState() {
    String coverageIssue = getTransientStateCoverageIssue();
    if (coverageIssue != null) {
      throw new IllegalStateException(coverageIssue);
    }
    return new DifferentialPressureFlowMeterState(getTransientStateIdentity(), getClass().getName(), stream,
        pipeDiameterMeters, throatDiameterMeters, differentialPressure, differentialPressureTransmitter,
        gasDensityOverride, isentropicExponentOverride, dynamicViscosityOverride, lastReynoldsNumberPipe,
        captureMeasurementDeviceTransientState(), captureDifferentialPressureFlowMeterExtensionState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(DifferentialPressureFlowMeterState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Differential-pressure flow-meter transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Differential-pressure flow-meter snapshot identity does not match " + getTransientStateIdentity());
    }
    if (!getClass().getName().equals(snapshot.concreteClassName)) {
      throw new IllegalArgumentException(
          "Differential-pressure flow-meter snapshot subtype does not match " + getClass().getName());
    }
    stream = snapshot.stream;
    pipeDiameterMeters = snapshot.pipeDiameterMeters;
    throatDiameterMeters = snapshot.throatDiameterMeters;
    differentialPressure = snapshot.differentialPressure;
    differentialPressureTransmitter = snapshot.differentialPressureTransmitter;
    gasDensityOverride = snapshot.gasDensityOverride;
    isentropicExponentOverride = snapshot.isentropicExponentOverride;
    dynamicViscosityOverride = snapshot.dynamicViscosityOverride;
    lastReynoldsNumberPipe = snapshot.lastReynoldsNumberPipe;
    restoreDifferentialPressureFlowMeterExtensionState(snapshot.extensionState);
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable differential-pressure primary-device rollback point. */
  public static final class DifferentialPressureFlowMeterState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final String concreteClassName;
    private final StreamInterface stream;
    private final double pipeDiameterMeters;
    private final double throatDiameterMeters;
    private final double differentialPressure;
    private final DifferentialPressureTransmitter differentialPressureTransmitter;
    private final double gasDensityOverride;
    private final double isentropicExponentOverride;
    private final double dynamicViscosityOverride;
    private final double lastReynoldsNumberPipe;
    private final MeasurementDeviceTransientState measurementState;
    private final Serializable extensionState;

    private DifferentialPressureFlowMeterState(String stateIdentity, String concreteClassName, StreamInterface stream,
        double pipeDiameterMeters, double throatDiameterMeters, double differentialPressure,
        DifferentialPressureTransmitter differentialPressureTransmitter, double gasDensityOverride,
        double isentropicExponentOverride, double dynamicViscosityOverride, double lastReynoldsNumberPipe,
        MeasurementDeviceTransientState measurementState, Serializable extensionState) {
      this.stateIdentity = stateIdentity;
      this.concreteClassName = concreteClassName;
      this.stream = stream;
      this.pipeDiameterMeters = pipeDiameterMeters;
      this.throatDiameterMeters = throatDiameterMeters;
      this.differentialPressure = differentialPressure;
      this.differentialPressureTransmitter = differentialPressureTransmitter;
      this.gasDensityOverride = gasDensityOverride;
      this.isentropicExponentOverride = isentropicExponentOverride;
      this.dynamicViscosityOverride = dynamicViscosityOverride;
      this.lastReynoldsNumberPipe = lastReynoldsNumberPipe;
      this.measurementState = measurementState;
      this.extensionState = extensionState;
    }
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    logger.info("{}: dP = {} mbar, mass flow = {} kg/hr", getName(), getDifferentialPressure("mbar"),
        getMassFlowRate("kg/hr"));
  }

  /**
   * Creates an empty, mutable list for a subclass's {@link #getValidityViolations()} implementation.
   *
   * @return a new, empty list of violations
   */
  protected static List<String> newViolationList() {
    return new ArrayList<String>();
  }

  /**
   * Returns whether the unit string denotes a standard-condition volume flow.
   *
   * @param unit unit string to classify, or null
   * @return true for standard volume flow units, false when unit is null
   */
  protected static boolean isStandardVolumeUnit(String unit) {
    if (unit == null) {
      return false;
    }
    return unit.startsWith("Sm3") || unit.startsWith("kSm3") || unit.startsWith("MSm3") || unit.startsWith("Sm^3");
  }

  /**
   * Returns whether the unit string denotes an actual (flowing-condition) volume flow.
   *
   * @param unit unit string to classify, or null
   * @return true for actual volume flow units, false when unit is null
   */
  protected static boolean isActualVolumeUnit(String unit) {
    if (unit == null) {
      return false;
    }
    return unit.startsWith("m3") || unit.startsWith("m^3");
  }

  /**
   * Returns the factor converting a length in the given unit to meter.
   *
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   * @return conversion factor to meter
   * @throws java.lang.RuntimeException if the unit is not supported
   */
  protected static double lengthConversionToMeter(String unit) {
    if ("m".equals(unit)) {
      return 1.0;
    } else if ("cm".equals(unit)) {
      return 0.01;
    } else if ("mm".equals(unit)) {
      return 0.001;
    } else if ("in".equals(unit)) {
      return 0.0254;
    }
    throw new RuntimeException("length unit not supported " + unit);
  }

  /**
   * Returns the factor converting a density in the given unit to kg/m3.
   *
   * @param unit density unit, one of "kg/m3" or "g/cm3"
   * @return conversion factor to kg/m3
   * @throws java.lang.RuntimeException if the unit is not supported
   */
  protected static double densityConversionToKgPerM3(String unit) {
    if ("kg/m3".equals(unit)) {
      return 1.0;
    } else if ("g/cm3".equals(unit)) {
      return 1000.0;
    }
    throw new RuntimeException("density unit not supported " + unit);
  }

  /**
   * Returns the factor converting a dynamic viscosity in the given unit to kg/(m.sec).
   *
   * @param unit viscosity unit, one of "kg/msec", "cP" or "Pas"
   * @return conversion factor to kg/(m.sec)
   * @throws java.lang.RuntimeException if the unit is not supported
   */
  protected static double viscosityConversionToKgPerMeterSecond(String unit) {
    if ("kg/msec".equals(unit) || "Pas".equals(unit)) {
      return 1.0;
    } else if ("cP".equals(unit)) {
      return 1.0e-3;
    }
    throw new RuntimeException("viscosity unit not supported " + unit);
  }

  /**
   * Returns the factor converting a pressure in the given unit to Pa.
   *
   * @param unit pressure unit, one of "Pa", "kPa", "MPa", "bar", "mbar" or "psi"
   * @return conversion factor to Pa
   * @throws java.lang.RuntimeException if the unit is not supported
   */
  protected static double pressureConversionToPa(String unit) {
    if ("Pa".equals(unit)) {
      return 1.0;
    } else if ("kPa".equals(unit)) {
      return 1000.0;
    } else if ("MPa".equals(unit)) {
      return 1.0e6;
    } else if ("bar".equals(unit) || "bara".equals(unit)) {
      return 1.0e5;
    } else if ("mbar".equals(unit)) {
      return 100.0;
    } else if ("psi".equals(unit)) {
      return 6894.757293168361;
    }
    throw new RuntimeException("pressure unit not supported " + unit);
  }

  /**
   * Returns the factor converting a mass flow in the given unit to kg/sec.
   *
   * @param unit mass flow unit, one of "kg/sec", "kg/min", "kg/hr", "kg/day" or "tonnes/year"
   * @return conversion factor to kg/sec
   * @throws java.lang.RuntimeException if the unit is not supported
   */
  protected static double massFlowConversionToKgPerSecond(String unit) {
    if ("kg/sec".equals(unit)) {
      return 1.0;
    } else if ("kg/min".equals(unit)) {
      return 1.0 / 60.0;
    } else if ("kg/hr".equals(unit)) {
      return 1.0 / 3600.0;
    } else if ("kg/day".equals(unit)) {
      return 1.0 / (3600.0 * 24.0);
    } else if ("tonnes/year".equals(unit)) {
      return 1000.0 / (3600.0 * 24.0 * 365.0);
    }
    throw new RuntimeException("mass flow unit not supported " + unit);
  }

  /**
   * Returns the factor converting a volume flow in the given unit to m3/sec.
   *
   * @param unit volume flow unit such as "m3/hr", "Sm3/hr", "kSm3/hr" or "MSm3/day"
   * @return conversion factor to m3/sec
   * @throws java.lang.RuntimeException if the unit is not supported
   */
  protected static double volumeFlowConversionToM3PerSecond(String unit) {
    if ("m3/sec".equals(unit) || "Sm3/sec".equals(unit) || "m^3/sec".equals(unit) || "Sm^3/sec".equals(unit)) {
      return 1.0;
    } else if ("kSm3/sec".equals(unit)) {
      return 1000.0;
    } else if ("MSm3/sec".equals(unit)) {
      return 1.0e6;
    } else if ("m3/min".equals(unit) || "Sm3/min".equals(unit) || "m^3/min".equals(unit) || "Sm^3/min".equals(unit)) {
      return 1.0 / 60.0;
    } else if ("kSm3/min".equals(unit)) {
      return 1000.0 / 60.0;
    } else if ("MSm3/min".equals(unit)) {
      return 1.0e6 / 60.0;
    } else if ("m3/hr".equals(unit) || "Sm3/hr".equals(unit) || "m^3/hr".equals(unit) || "Sm^3/hr".equals(unit)) {
      return 1.0 / 3600.0;
    } else if ("m3/day".equals(unit) || "Sm3/day".equals(unit) || "m^3/day".equals(unit) || "Sm^3/day".equals(unit)) {
      return 1.0 / (3600.0 * 24.0);
    } else if ("kSm3/hr".equals(unit)) {
      return 1000.0 / 3600.0;
    } else if ("kSm3/day".equals(unit)) {
      return 1000.0 / (3600.0 * 24.0);
    } else if ("MSm3/day".equals(unit)) {
      return 1.0e6 / (3600.0 * 24.0);
    } else if ("MSm3/hr".equals(unit)) {
      return 1.0e6 / 3600.0;
    }
    throw new RuntimeException("volume flow unit not supported " + unit);
  }
}
