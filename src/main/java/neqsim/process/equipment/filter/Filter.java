package neqsim.process.equipment.filter;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.filter.FilterMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;

/**
 * General filter unit operation for particulate, coalescing, strainer, and granular-media service.
 *
 * <p>
 * The default remains a fixed pressure-drop, two-port filter for backward compatibility. Optional models add
 * flow-dependent clean pressure drop, Ergun packed-media pressure drop, beta-ratio filtration efficiency, dynamic dirt
 * loading, breakthrough, backwash/regeneration, and differential-pressure bypass behavior.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Filter extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for this class. */
  private static final Logger logger = LogManager.getLogger(Filter.class);

  private double deltaP = 0.01;
  private double Cv = 0.0;
  private double cleanDeltaP = Double.NaN;
  private double calculatedCleanDeltaP = 0.01;
  private double unrestrictedDeltaP = 0.01;
  private FilterType filterType = FilterType.CARTRIDGE;
  private FilterPressureDropModel pressureDropModel = FilterPressureDropModel.FIXED;
  private FilterPressureDropCurve pressureDropCurve = new FilterPressureDropCurve();
  private double referenceFlowRateM3Hr = Double.NaN;
  private double flowExponent = 1.0;
  private double mediaArea = 1.0;
  private double mediaBedDepth = 1.0;
  private double mediaParticleDiameter = 0.001;
  private double mediaVoidFraction = 0.40;
  private int numberOfElements = 1;
  private double holdupVolume = 0.0;
  private double holdupResidenceTime = 0.0;
  private double solidsLoading = 0.0;
  private double solidsLoadingRate = 0.0;
  private double calculatedCapturedRate = 0.0;
  private double loadingCapacity = Double.POSITIVE_INFINITY;
  private double pressureDropIncreaseAtCapacity = 0.0;
  private double breakthroughStartFraction = 0.8;
  private double breakthroughFraction = 0.0;
  private FilterPerformanceCurve performanceCurve = new FilterPerformanceCurve();
  private double nominalRemovalEfficiency = 0.0;
  private double particleSizeMicrometre = 10.0;
  private double inletParticleConcentrationMgKg = 0.0;
  private double outletParticleConcentrationMgKg = 0.0;
  private boolean concentrationLoadingModelEnabled = false;
  private boolean bypassEnabled = false;
  private double bypassCrackingDeltaP = Double.POSITIVE_INFINITY;
  private double bypassFraction = 0.0;
  private double terminalDeltaP = 5.0;
  private double elementCollapsePressure = 10.0;
  private boolean elementIntegrityVerified = false;
  private double backwashRemovalRate = 0.0;
  private double regenerationRemovalRate = 0.0;
  private boolean backwashActive = false;
  private boolean regenerationActive = false;
  private FilterMechanicalDesign mechanicalDesign;

  /**
   * Restores defaults for fields added after the original serialized filter representation.
   *
   * @param input serialized object input
   * @throws IOException if the serialized form cannot be read
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    if (filterType == null) {
      filterType = FilterType.CARTRIDGE;
    }
    if (pressureDropModel == null) {
      pressureDropModel = FilterPressureDropModel.FIXED;
    }
    if (pressureDropCurve == null) {
      pressureDropCurve = new FilterPressureDropCurve();
    }
    if (performanceCurve == null) {
      performanceCurve = new FilterPerformanceCurve();
    }
    if (flowExponent <= 0.0) {
      flowExponent = 1.0;
    }
    if (mediaArea <= 0.0) {
      mediaArea = 1.0;
    }
    if (mediaBedDepth <= 0.0) {
      mediaBedDepth = 1.0;
    }
    if (mediaParticleDiameter <= 0.0) {
      mediaParticleDiameter = 0.001;
    }
    if (mediaVoidFraction <= 0.0 || mediaVoidFraction >= 1.0) {
      mediaVoidFraction = 0.40;
    }
    if (numberOfElements < 1) {
      numberOfElements = 1;
    }
    if (particleSizeMicrometre <= 0.0) {
      particleSizeMicrometre = 10.0;
    }
    if (terminalDeltaP <= 0.0) {
      terminalDeltaP = 5.0;
    }
    if (elementCollapsePressure <= 0.0) {
      elementCollapsePressure = 10.0;
    }
    if (bypassCrackingDeltaP <= 0.0) {
      bypassCrackingDeltaP = Double.POSITIVE_INFINITY;
    }
    if (calculatedCleanDeltaP <= 0.0 && deltaP > 0.0) {
      calculatedCleanDeltaP = deltaP;
    }
    if (unrestrictedDeltaP <= 0.0 && deltaP > 0.0) {
      unrestrictedDeltaP = deltaP;
    }
  }

  /**
   * Constructor for Filter.
   *
   * @param name name of filter
   */
  public Filter(String name) {
    super(name);
    initMechanicalDesign();
  }

  /**
   * Constructor for Filter.
   *
   * @param name name of filter
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Filter(String name, StreamInterface inStream) {
    super(name, inStream);
    initMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new FilterMechanicalDesign(this);
    if (inStream != null) {
      mechanicalDesign.setMaxOperationPressure(inStream.getPressure("bara"));
      mechanicalDesign.setMaxOperationTemperature(inStream.getTemperature("K"));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inStream.getThermoSystem().clone();
    system.initProperties();
    updateHydraulicState(system);
    updateParticleCapturePerformance();

    double inletPressure = inStream.getPressure("bara");
    double appliedDeltaP = Math.min(Math.max(0.0, getDeltaP()), Math.max(0.0, inletPressure - 1.0e-6));
    if (appliedDeltaP + 1.0e-12 < getDeltaP()) {
      logger.warn("Filter {} calculated pressure drop {} bar exceeds inlet pressure {} bara; outlet pressure was "
          + "limited to a small positive value", getName(), getDeltaP(), inletPressure);
      deltaP = appliedDeltaP;
    }
    if (appliedDeltaP > 1e-10) {
      system.setPressure(inletPressure - appliedDeltaP, "bara");
      ThermodynamicOperations testOps = new ThermodynamicOperations(system);
      testOps.TPflash();
    }
    system.initProperties();
    outStream.setThermoSystem(system);
    double massFlow = Math.abs(inStream.getFlowRate("kg/hr"));
    Cv = massFlow > 1.0e-20 ? Math.sqrt(Math.max(0.0, deltaP)) / massFlow : 0.0;
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      setCalculationIdentifier(id);
      return;
    }

    initialiseDynamicState();
    run(id);

    double step = Math.max(0.0, dt);
    if (step > 0.0) {
      updateHoldupResidenceTime();
      updateDynamicLoading(step);
      updateBreakthroughFraction();
      updateDynamicPressureDrop();
      run(id);
    }
    increaseTime(dt);
    setCalculationIdentifier(id);
  }

  /**
   * Getter for the field <code>deltaP</code>.
   *
   * @return a double
   */
  public double getDeltaP() {
    return deltaP;
  }

  /**
   * Setter for the field <code>deltaP</code>.
   *
   * @param deltaP a double
   */
  public void setDeltaP(double deltaP) {
    this.deltaP = Math.max(0.0, deltaP);
    this.cleanDeltaP = this.deltaP;
    this.calculatedCleanDeltaP = this.deltaP;
    this.unrestrictedDeltaP = this.deltaP;
    if (outStream != null && inStream != null) {
      this.outStream.setPressure(this.inStream.getPressure("bara") - this.deltaP, "bara");
    }
  }

  /**
   * Setter for the field <code>deltaP</code>.
   *
   * @param deltaP a double
   * @param unit a {@link java.lang.String} object
   */
  public void setDeltaP(double deltaP, String unit) {
    double pressureDropBar = Math.max(0.0, deltaP) * new PressureUnit(1.0, unit).getConversionFactor(unit);
    setDeltaP(pressureDropBar);
  }

  /**
   * Returns the clean pressure-drop datum configured with {@link #setDeltaP(double)}.
   *
   * @return reference clean pressure drop in bar
   */
  public double getCleanDeltaP() {
    initialiseDynamicState();
    return cleanDeltaP;
  }

  /**
   * Returns the last calculated clean pressure drop before cake/loading and bypass effects.
   *
   * @return calculated clean pressure drop in bar
   */
  public double getCalculatedCleanDeltaP() {
    return calculatedCleanDeltaP;
  }

  /**
   * Returns the calculated differential pressure before a configured bypass valve limits it.
   *
   * @return unrestricted pressure drop in bar
   */
  public double getUnrestrictedDeltaP() {
    return unrestrictedDeltaP;
  }

  /**
   * Configures a common filter construction and applies its preliminary hydraulic defaults.
   *
   * <p>
   * Project and vendor data should replace these defaults where available.
   * </p>
   *
   * @param filterType filter construction
   */
  public void setFilterServiceType(FilterType filterType) {
    if (filterType == null) {
      throw new IllegalArgumentException("Filter type cannot be null");
    }
    this.filterType = filterType;
    this.pressureDropModel = filterType.getDefaultPressureDropModel();
    this.flowExponent = filterType.getDefaultFlowExponent();
  }

  /** @return configured filter construction */
  public FilterType getFilterServiceType() {
    return filterType;
  }

  /**
   * Selects the clean-filter differential-pressure model.
   *
   * @param pressureDropModel hydraulic model
   */
  public void setPressureDropModel(FilterPressureDropModel pressureDropModel) {
    if (pressureDropModel == null) {
      throw new IllegalArgumentException("Pressure-drop model cannot be null");
    }
    this.pressureDropModel = pressureDropModel;
  }

  /** @return selected clean-filter pressure-drop model */
  public FilterPressureDropModel getPressureDropModel() {
    return pressureDropModel;
  }

  /**
   * Sets the reference actual volumetric flow for {@link FilterPressureDropModel#FLOW_SCALED}.
   *
   * @param referenceFlowRateM3Hr actual volumetric flow in m3/hr
   */
  public void setReferenceFlowRate(double referenceFlowRateM3Hr) {
    if (!Double.isFinite(referenceFlowRateM3Hr) || referenceFlowRateM3Hr <= 0.0) {
      throw new IllegalArgumentException("Reference flow rate must be finite and positive");
    }
    this.referenceFlowRateM3Hr = referenceFlowRateM3Hr;
  }

  /** @return reference actual volumetric flow in m3/hr, or NaN when not configured */
  public double getReferenceFlowRate() {
    return referenceFlowRateM3Hr;
  }

  /**
   * Sets the exponent used to scale differential pressure with actual volumetric flow.
   *
   * @param flowExponent positive exponent, commonly one for viscous media and two for inertial devices
   */
  public void setFlowExponent(double flowExponent) {
    if (!Double.isFinite(flowExponent) || flowExponent <= 0.0) {
      throw new IllegalArgumentException("Flow exponent must be finite and positive");
    }
    this.flowExponent = flowExponent;
  }

  /** @return pressure-drop flow exponent */
  public double getFlowExponent() {
    return flowExponent;
  }

  /**
   * Sets a clean pressure-drop test curve. Selecting a curve also selects the tabulated model.
   *
   * @param pressureDropCurve pressure-drop versus actual-flow curve
   */
  public void setPressureDropCurve(FilterPressureDropCurve pressureDropCurve) {
    if (pressureDropCurve == null) {
      throw new IllegalArgumentException("Pressure-drop curve cannot be null");
    }
    this.pressureDropCurve = pressureDropCurve;
    this.pressureDropModel = FilterPressureDropModel.TABULATED;
  }

  /** @return configured pressure-drop curve */
  public FilterPressureDropCurve getPressureDropCurve() {
    return pressureDropCurve;
  }

  /**
   * Configures packed-media geometry for the Ergun pressure-drop model.
   *
   * @param areaM2 media cross-sectional area in m2
   * @param bedDepthM media depth in m
   * @param particleDiameterM representative media particle diameter in m
   * @param voidFraction bed void fraction from zero to one
   */
  public void setMediaGeometry(double areaM2, double bedDepthM, double particleDiameterM, double voidFraction) {
    if (!Double.isFinite(areaM2) || areaM2 <= 0.0 || !Double.isFinite(bedDepthM) || bedDepthM <= 0.0
        || !Double.isFinite(particleDiameterM) || particleDiameterM <= 0.0 || !Double.isFinite(voidFraction)
        || voidFraction <= 0.0 || voidFraction >= 1.0) {
      throw new IllegalArgumentException("Media geometry must be finite and positive with void fraction between zero and one");
    }
    this.mediaArea = areaM2;
    this.mediaBedDepth = bedDepthM;
    this.mediaParticleDiameter = particleDiameterM;
    this.mediaVoidFraction = voidFraction;
  }

  /** @return packed-media cross-sectional area in m2 */
  public double getMediaArea() {
    return mediaArea;
  }

  /** @return packed-media bed depth in m */
  public double getMediaBedDepth() {
    return mediaBedDepth;
  }

  /** @return representative packed-media particle diameter in m */
  public double getMediaParticleDiameter() {
    return mediaParticleDiameter;
  }

  /** @return packed-media void fraction */
  public double getMediaVoidFraction() {
    return mediaVoidFraction;
  }

  /**
   * Sets the installed number of parallel filter elements.
   *
   * @param numberOfElements positive element count
   */
  public void setNumberOfElements(int numberOfElements) {
    if (numberOfElements < 1) {
      throw new IllegalArgumentException("Number of filter elements must be at least one");
    }
    this.numberOfElements = numberOfElements;
  }

  /** @return installed number of parallel filter elements */
  public int getNumberOfElements() {
    return numberOfElements;
  }

  /**
   * Sets a particle-size dependent beta-ratio performance curve.
   *
   * @param performanceCurve laboratory or supplier curve
   */
  public void setPerformanceCurve(FilterPerformanceCurve performanceCurve) {
    if (performanceCurve == null) {
      throw new IllegalArgumentException("Performance curve cannot be null");
    }
    this.performanceCurve = performanceCurve;
  }

  /** @return configured beta-ratio performance curve */
  public FilterPerformanceCurve getPerformanceCurve() {
    return performanceCurve;
  }

  /**
   * Sets a single beta ratio as an alternative to a particle-size curve.
   *
   * @param betaRatio upstream count divided by downstream count, at least one
   */
  public void setBetaRatio(double betaRatio) {
    if (!Double.isFinite(betaRatio) || betaRatio < 1.0) {
      throw new IllegalArgumentException("Beta ratio must be finite and greater than or equal to one");
    }
    nominalRemovalEfficiency = 1.0 - 1.0 / betaRatio;
    performanceCurve = new FilterPerformanceCurve();
  }

  /**
   * Sets nominal fractional particle or droplet removal.
   *
   * @param efficiency removal efficiency from zero to one
   */
  public void setNominalRemovalEfficiency(double efficiency) {
    if (!Double.isFinite(efficiency) || efficiency < 0.0 || efficiency > 1.0) {
      throw new IllegalArgumentException("Removal efficiency must be between zero and one");
    }
    this.nominalRemovalEfficiency = efficiency;
    performanceCurve = new FilterPerformanceCurve();
  }

  /** @return nominal efficiency before loading breakthrough or bypass */
  public double getNominalRemovalEfficiency() {
    if (performanceCurve.size() > 0) {
      return performanceCurve.getRemovalEfficiency(particleSizeMicrometre);
    }
    return nominalRemovalEfficiency;
  }

  /**
   * Sets the representative particle or droplet size evaluated against the beta-ratio curve.
   *
   * @param particleSizeMicrometre size in micrometres
   */
  public void setParticleSize(double particleSizeMicrometre) {
    if (!Double.isFinite(particleSizeMicrometre) || particleSizeMicrometre <= 0.0) {
      throw new IllegalArgumentException("Particle size must be finite and positive");
    }
    this.particleSizeMicrometre = particleSizeMicrometre;
  }

  /** @return representative particle or droplet size in micrometres */
  public double getParticleSize() {
    return particleSizeMicrometre;
  }

  /**
   * Enables concentration-based loading from an inlet particle or droplet concentration.
   *
   * @param concentrationMgKg inlet concentration in mg/kg of process fluid
   */
  public void setInletParticleConcentration(double concentrationMgKg) {
    if (!Double.isFinite(concentrationMgKg) || concentrationMgKg < 0.0) {
      throw new IllegalArgumentException("Particle concentration must be finite and non-negative");
    }
    this.inletParticleConcentrationMgKg = concentrationMgKg;
    this.concentrationLoadingModelEnabled = true;
  }

  /** @return inlet particle or droplet concentration in mg/kg */
  public double getInletParticleConcentration() {
    return inletParticleConcentrationMgKg;
  }

  /** @return calculated outlet particle or droplet concentration in mg/kg */
  public double getOutletParticleConcentration() {
    return outletParticleConcentrationMgKg;
  }

  /**
   * Returns removal efficiency after capacity breakthrough and bypass flow.
   *
   * @return current removal efficiency from zero to one
   */
  public double getCurrentRemovalEfficiency() {
    double retainedFraction = (1.0 - breakthroughFraction) * (1.0 - bypassFraction);
    return Math.max(0.0, Math.min(1.0, getNominalRemovalEfficiency() * retainedFraction));
  }

  /** @return calculated captured particle or droplet mass rate in kg/hr */
  public double getCalculatedCapturedRate() {
    return calculatedCapturedRate;
  }

  /**
   * Enables a differential-pressure bypass and sets its cracking pressure.
   *
   * @param crackingDeltaPBar cracking differential pressure in bar
   */
  public void setBypassCrackingDeltaP(double crackingDeltaPBar) {
    if (!Double.isFinite(crackingDeltaPBar) || crackingDeltaPBar <= 0.0) {
      throw new IllegalArgumentException("Bypass cracking pressure must be finite and positive");
    }
    bypassCrackingDeltaP = crackingDeltaPBar;
    bypassEnabled = true;
  }

  /** Disables differential-pressure bypass flow. */
  public void disableBypass() {
    bypassEnabled = false;
    bypassFraction = 0.0;
  }

  /** @return true when a differential-pressure bypass is configured */
  public boolean isBypassEnabled() {
    return bypassEnabled;
  }

  /** @return current estimated bypass flow fraction */
  public double getBypassFraction() {
    return bypassFraction;
  }

  /**
   * Sets the normal element replacement or backwash differential pressure.
   *
   * @param terminalDeltaP terminal differential pressure in bar
   */
  public void setTerminalDeltaP(double terminalDeltaP) {
    if (!Double.isFinite(terminalDeltaP) || terminalDeltaP <= 0.0) {
      throw new IllegalArgumentException("Terminal differential pressure must be finite and positive");
    }
    this.terminalDeltaP = terminalDeltaP;
  }

  /** @return terminal differential pressure in bar */
  public double getTerminalDeltaP() {
    return terminalDeltaP;
  }

  /** @return current terminal differential-pressure utilization */
  public double getDifferentialPressureUtilization() {
    return terminalDeltaP > 0.0 ? unrestrictedDeltaP / terminalDeltaP : Double.POSITIVE_INFINITY;
  }

  /** @return true when the terminal differential pressure or dirt capacity has been reached */
  public boolean isReplacementRequired() {
    return getDifferentialPressureUtilization() >= 1.0 || getLoadingFraction() >= 1.0;
  }

  /**
   * Sets the element collapse or burst differential-pressure rating.
   *
   * @param pressureBar rated differential pressure in bar
   */
  public void setElementCollapsePressure(double pressureBar) {
    if (!Double.isFinite(pressureBar) || pressureBar <= 0.0) {
      throw new IllegalArgumentException("Element collapse pressure must be finite and positive");
    }
    elementCollapsePressure = pressureBar;
  }

  /** @return rated element collapse or burst differential pressure in bar */
  public double getElementCollapsePressure() {
    return elementCollapsePressure;
  }

  /**
   * Records whether filter-element fabrication integrity has been verified by the project or supplier.
   *
   * @param verified true when verified
   */
  public void setElementIntegrityVerified(boolean verified) {
    elementIntegrityVerified = verified;
  }

  /** @return whether element fabrication integrity has been recorded as verified */
  public boolean isElementIntegrityVerified() {
    return elementIntegrityVerified;
  }

  /**
   * Sets the filter holdup volume used to report dynamic residence time.
   *
   * @param volume volume in m3, where negative values are clamped to zero
   */
  public void setHoldupVolume(double volume) {
    this.holdupVolume = Math.max(0.0, volume);
  }

  /**
   * Returns the configured filter holdup volume.
   *
   * @return holdup volume in m3
   */
  public double getHoldupVolume() {
    return holdupVolume;
  }

  /**
   * Returns the last calculated residence time from holdup volume and inlet flow.
   *
   * @return residence time in seconds
   */
  public double getHoldupResidenceTime() {
    return holdupResidenceTime;
  }

  /**
   * Sets a generic solids or contaminant capture rate for dynamic filter loading.
   *
   * @param rate capture rate in kg/hr, where negative values are clamped to zero
   */
  public void setSolidsLoadingRate(double rate) {
    this.solidsLoadingRate = Math.max(0.0, rate);
    this.concentrationLoadingModelEnabled = false;
  }

  /**
   * Returns the generic solids or contaminant capture rate.
   *
   * @return capture rate in kg/hr
   */
  public double getSolidsLoadingRate() {
    return getCapturedSolidsRate();
  }

  /**
   * Sets the accumulated solids or contaminant loading state.
   *
   * @param loading loading in kg, where negative values are clamped to zero
   */
  public void setSolidsLoading(double loading) {
    this.solidsLoading = Math.max(0.0, loading);
    updateBreakthroughFraction();
    updateDynamicPressureDrop();
  }

  /**
   * Returns the accumulated solids or contaminant loading.
   *
   * @return accumulated loading in kg
   */
  public double getSolidsLoading() {
    return solidsLoading;
  }

  /**
   * Sets the loading capacity where breakthrough reaches 100%.
   *
   * @param capacity capacity in kg, where non-positive values disable the finite capacity limit
   */
  public void setLoadingCapacity(double capacity) {
    this.loadingCapacity = capacity > 0.0 ? capacity : Double.POSITIVE_INFINITY;
    updateBreakthroughFraction();
    updateDynamicPressureDrop();
  }

  /**
   * Returns the loading capacity.
   *
   * @return loading capacity in kg, or positive infinity when no finite capacity is configured
   */
  public double getLoadingCapacity() {
    return loadingCapacity;
  }

  /**
   * Returns the fraction of the finite loading capacity that has been used.
   *
   * @return loading fraction, or zero when no finite capacity is configured
   */
  public double getLoadingFraction() {
    if (Double.isInfinite(loadingCapacity) || loadingCapacity <= 0.0) {
      return 0.0;
    }
    return solidsLoading / loadingCapacity;
  }

  /**
   * Sets the additional pressure drop at one loading-capacity equivalent.
   *
   * @param deltaPAtCapacity pressure-drop increase in bar at 100% loading
   */
  public void setPressureDropIncreaseAtCapacity(double deltaPAtCapacity) {
    this.pressureDropIncreaseAtCapacity = Math.max(0.0, deltaPAtCapacity);
    updateDynamicPressureDrop();
  }

  /**
   * Returns the additional pressure drop at one loading-capacity equivalent.
   *
   * @return pressure-drop increase in bar at 100% loading
   */
  public double getPressureDropIncreaseAtCapacity() {
    return pressureDropIncreaseAtCapacity;
  }

  /**
   * Sets the loading fraction where breakthrough starts.
   *
   * @param fraction loading fraction from 0 to less than 1
   */
  public void setBreakthroughStartFraction(double fraction) {
    this.breakthroughStartFraction = Math.max(0.0, Math.min(0.999999, fraction));
    updateBreakthroughFraction();
  }

  /**
   * Returns the loading fraction where breakthrough starts.
   *
   * @return breakthrough start fraction
   */
  public double getBreakthroughStartFraction() {
    return breakthroughStartFraction;
  }

  /**
   * Returns the current breakthrough fraction.
   *
   * @return breakthrough fraction from 0 to 1
   */
  public double getBreakthroughFraction() {
    return breakthroughFraction;
  }

  /**
   * Sets the backwash loading removal rate.
   *
   * @param rate removal rate in kg/hr, where negative values are clamped to zero
   */
  public void setBackwashRemovalRate(double rate) {
    this.backwashRemovalRate = Math.max(0.0, rate);
  }

  /**
   * Returns the backwash loading removal rate.
   *
   * @return removal rate in kg/hr
   */
  public double getBackwashRemovalRate() {
    return backwashRemovalRate;
  }

  /**
   * Sets the regeneration loading removal rate.
   *
   * @param rate removal rate in kg/hr, where negative values are clamped to zero
   */
  public void setRegenerationRemovalRate(double rate) {
    this.regenerationRemovalRate = Math.max(0.0, rate);
  }

  /**
   * Returns the regeneration loading removal rate.
   *
   * @return removal rate in kg/hr
   */
  public double getRegenerationRemovalRate() {
    return regenerationRemovalRate;
  }

  /**
   * Starts a backwash operation that removes accumulated loading during transient steps.
   */
  public void startBackwash() {
    this.backwashActive = true;
  }

  /**
   * Stops the active backwash operation.
   */
  public void stopBackwash() {
    this.backwashActive = false;
  }

  /**
   * Returns whether backwash is active.
   *
   * @return true when backwash is active
   */
  public boolean isBackwashActive() {
    return backwashActive;
  }

  /**
   * Starts a regeneration operation that removes accumulated loading during transient steps.
   */
  public void startRegeneration() {
    this.regenerationActive = true;
  }

  /**
   * Stops the active regeneration operation.
   */
  public void stopRegeneration() {
    this.regenerationActive = false;
  }

  /**
   * Returns whether regeneration is active.
   *
   * @return true when regeneration is active
   */
  public boolean isRegenerationActive() {
    return regenerationActive;
  }

  /**
   * Resets dynamic loading, breakthrough, and active backwash/regeneration state.
   */
  public void resetDynamicState() {
    this.solidsLoading = 0.0;
    this.breakthroughFraction = 0.0;
    this.backwashActive = false;
    this.regenerationActive = false;
    initialiseDynamicState();
    updateDynamicPressureDrop();
  }

  /**
   * Returns the captured solids or contaminant rate used by the transient loading model.
   *
   * @return captured loading rate in kg/hr
   */
  protected double getCapturedSolidsRate() {
    return concentrationLoadingModelEnabled ? calculatedCapturedRate : solidsLoadingRate;
  }

  /**
   * Calculates clean and loaded differential pressure, including bypass flow when configured.
   *
   * @param system initialized inlet thermodynamic system
   */
  protected void updateHydraulicState(SystemInterface system) {
    initialiseDynamicState();
    calculatedCleanDeltaP = calculateCleanPressureDrop(system);
    unrestrictedDeltaP = calculatedCleanDeltaP
        + pressureDropIncreaseAtCapacity * Math.max(0.0, getLoadingFraction());
    bypassFraction = 0.0;
    deltaP = unrestrictedDeltaP;
    if (bypassEnabled && unrestrictedDeltaP > bypassCrackingDeltaP) {
      double filteredFlowFraction = Math.sqrt(bypassCrackingDeltaP / unrestrictedDeltaP);
      bypassFraction = Math.max(0.0, Math.min(1.0, 1.0 - filteredFlowFraction));
      deltaP = bypassCrackingDeltaP;
    }
  }

  /**
   * Calculates the clean differential pressure for the selected hydraulic model.
   *
   * @param system initialized inlet thermodynamic system
   * @return clean differential pressure in bar
   */
  private double calculateCleanPressureDrop(SystemInterface system) {
    double actualFlowM3Hr = Math.max(0.0, inStream.getFlowRate("m3/hr"));
    switch (pressureDropModel) {
      case FLOW_SCALED:
        if (!Double.isFinite(referenceFlowRateM3Hr) || referenceFlowRateM3Hr <= 0.0) {
          referenceFlowRateM3Hr = actualFlowM3Hr;
        }
        if (referenceFlowRateM3Hr <= 0.0) {
          return 0.0;
        }
        return cleanDeltaP * Math.pow(actualFlowM3Hr / referenceFlowRateM3Hr, flowExponent);
      case TABULATED:
        if (pressureDropCurve.size() == 0) {
          return cleanDeltaP;
        }
        return pressureDropCurve.getPressureDrop(actualFlowM3Hr);
      case ERGUN:
        return calculateErgunPressureDrop(system, actualFlowM3Hr);
      case FIXED:
      default:
        return cleanDeltaP;
    }
  }

  /**
   * Calculates packed-media pressure drop with the Ergun equation.
   *
   * @param system initialized inlet thermodynamic system
   * @param actualFlowM3Hr actual volumetric flow in m3/hr
   * @return pressure drop in bar
   */
  private double calculateErgunPressureDrop(SystemInterface system, double actualFlowM3Hr) {
    if (actualFlowM3Hr <= 0.0) {
      return 0.0;
    }
    double density = system.getDensity("kg/m3");
    double viscosity = system.getViscosity("kg/msec");
    if (!Double.isFinite(density) || density <= 0.0 || !Double.isFinite(viscosity) || viscosity <= 0.0) {
      return cleanDeltaP;
    }
    double superficialVelocity = actualFlowM3Hr / 3600.0 / mediaArea;
    double solidFraction = 1.0 - mediaVoidFraction;
    double voidCube = Math.pow(mediaVoidFraction, 3.0);
    double viscousTerm = 150.0 * viscosity * solidFraction * solidFraction
        / (voidCube * mediaParticleDiameter * mediaParticleDiameter) * superficialVelocity;
    double inertialTerm = 1.75 * density * solidFraction / (voidCube * mediaParticleDiameter)
        * superficialVelocity * superficialVelocity;
    return Math.max(0.0, mediaBedDepth * (viscousTerm + inertialTerm) / 1.0e5);
  }

  /**
   * Updates concentration and captured-mass outputs for the current efficiency.
   */
  protected void updateParticleCapturePerformance() {
    if (!concentrationLoadingModelEnabled) {
      calculatedCapturedRate = 0.0;
      outletParticleConcentrationMgKg = 0.0;
      return;
    }
    double efficiency = getCurrentRemovalEfficiency();
    outletParticleConcentrationMgKg = inletParticleConcentrationMgKg * (1.0 - efficiency);
    double massFlowKgHr = Math.max(0.0, inStream.getFlowRate("kg/hr"));
    calculatedCapturedRate = massFlowKgHr * inletParticleConcentrationMgKg * efficiency / 1.0e6;
  }

  /**
   * Initializes dynamic state defaults from the current steady-state pressure drop.
   */
  private void initialiseDynamicState() {
    if (Double.isNaN(cleanDeltaP)) {
      cleanDeltaP = deltaP;
    }
  }

  /**
   * Updates the residence time estimate from holdup volume and inlet volumetric flow.
   */
  private void updateHoldupResidenceTime() {
    if (holdupVolume <= 0.0 || inStream == null) {
      holdupResidenceTime = 0.0;
      return;
    }
    double volumetricFlowM3s = Math.max(0.0, inStream.getFlowRate("m3/hr") / 3600.0);
    holdupResidenceTime = volumetricFlowM3s > 0.0 ? holdupVolume / volumetricFlowM3s : Double.POSITIVE_INFINITY;
  }

  /**
   * Integrates loading accumulation and active backwash or regeneration removal.
   *
   * @param dt timestep in seconds
   */
  private void updateDynamicLoading(double dt) {
    double captured = Math.max(0.0, getCapturedSolidsRate()) * dt / 3600.0;
    double removed = 0.0;
    if (backwashActive) {
      removed += backwashRemovalRate * dt / 3600.0;
    }
    if (regenerationActive) {
      removed += regenerationRemovalRate * dt / 3600.0;
    }
    solidsLoading = Math.max(0.0, solidsLoading + captured - removed);
  }

  /**
   * Updates the breakthrough fraction from the current loading fraction.
   */
  private void updateBreakthroughFraction() {
    double loadingFraction = getLoadingFraction();
    if (loadingFraction <= breakthroughStartFraction) {
      breakthroughFraction = 0.0;
      return;
    }
    breakthroughFraction = Math.min(1.0,
        (loadingFraction - breakthroughStartFraction) / (1.0 - breakthroughStartFraction));
  }

  /**
   * Updates the effective dynamic pressure drop from clean pressure drop and loading.
   */
  private void updateDynamicPressureDrop() {
    initialiseDynamicState();
    unrestrictedDeltaP = calculatedCleanDeltaP
        + pressureDropIncreaseAtCapacity * Math.max(0.0, getLoadingFraction());
    deltaP = unrestrictedDeltaP;
    if (bypassEnabled && unrestrictedDeltaP > bypassCrackingDeltaP) {
      bypassFraction = Math.max(0.0,
          Math.min(1.0, 1.0 - Math.sqrt(bypassCrackingDeltaP / unrestrictedDeltaP)));
      deltaP = bypassCrackingDeltaP;
    } else {
      bypassFraction = 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refTEGabsorberloc) {
    double deltaP = inStream.getPressure("bara") - outStream.getPressure("bara");
    double massFlow = Math.abs(inStream.getFlowRate("kg/hr"));
    Cv = massFlow > 1.0e-20 ? Math.sqrt(Math.max(0.0, deltaP)) / massFlow : 0.0;
  }

  /**
   * getCvFactor.
   *
   * @return a double
   */
  public double getCvFactor() {
    return Cv;
  }

  /**
   * setCvFactor.
   *
   * @param pressureCoef a double
   */
  public void setCvFactor(double pressureCoef) {
    this.Cv = pressureCoef;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new neqsim.process.util.monitor.FilterResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(neqsim.process.util.report.ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == neqsim.process.util.report.ReportConfig.DetailLevel.HIDE) {
      return null;
    }
    neqsim.process.util.monitor.FilterResponse res = new neqsim.process.util.monitor.FilterResponse(this);
    res.applyConfig(cfg);
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }
}
