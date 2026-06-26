package neqsim.process.equipment.filter;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Filter class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Filter extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double deltaP = 0.01;
  private double Cv = 0.0;
  private double cleanDeltaP = Double.NaN;
  private double holdupVolume = 0.0;
  private double holdupResidenceTime = 0.0;
  private double solidsLoading = 0.0;
  private double solidsLoadingRate = 0.0;
  private double loadingCapacity = Double.POSITIVE_INFINITY;
  private double pressureDropIncreaseAtCapacity = 0.0;
  private double breakthroughStartFraction = 0.8;
  private double breakthroughFraction = 0.0;
  private double backwashRemovalRate = 0.0;
  private double regenerationRemovalRate = 0.0;
  private boolean backwashActive = false;
  private boolean regenerationActive = false;

  /**
   * Constructor for Filter.
   *
   * @param name name of filter
   */
  public Filter(String name) {
    super(name);
  }

  /**
   * Constructor for Filter.
   *
   * @param name name of filter
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Filter(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inStream.getThermoSystem().clone();
    if (Math.abs(getDeltaP()) > 1e-10) {
      system.setPressure(inStream.getPressure() - getDeltaP());
      ThermodynamicOperations testOps = new ThermodynamicOperations(system);
      testOps.TPflash();
    }
    system.initProperties();
    outStream.setThermoSystem(system);
    Cv = Math.sqrt(deltaP) / inStream.getFlowRate("kg/hr");
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
    this.deltaP = deltaP;
    this.cleanDeltaP = deltaP;
    this.outStream.setPressure(this.inStream.getPressure() - deltaP);
  }

  /**
   * Setter for the field <code>deltaP</code>.
   *
   * @param deltaP a double
   * @param unit a {@link java.lang.String} object
   */
  public void setDeltaP(double deltaP, String unit) {
    this.deltaP = deltaP;
    this.cleanDeltaP = deltaP;
    this.outStream.setPressure(this.inStream.getPressure(unit) - deltaP, unit);
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
  }

  /**
   * Returns the generic solids or contaminant capture rate.
   *
   * @return capture rate in kg/hr
   */
  public double getSolidsLoadingRate() {
    return solidsLoadingRate;
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
    return solidsLoadingRate;
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
    deltaP = cleanDeltaP + pressureDropIncreaseAtCapacity * Math.max(0.0, getLoadingFraction());
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refTEGabsorberloc) {
    double deltaP = inStream.getPressure("bara") - outStream.getPressure("bara");
    Cv = Math.sqrt(deltaP) / inStream.getFlowRate("kg/hr");
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
