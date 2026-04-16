package neqsim.process.equipment.watertreatment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.watertreatment.HydrocycloneMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * De-oiling hydrocyclone for produced water treatment.
 *
 * <p>
 * Models a multi-liner de-oiling hydrocyclone package used on offshore platforms for primary
 * produced water treatment. The implementation is based on the equilibrium-orbit theory (Bradley
 * 1965, Svarovsky 1984) combined with the Stokes-law centrifugal settling model. It covers:
 * </p>
 * <ul>
 * <li><b>Physics-based d50 prediction</b> from fluid properties and liner geometry</li>
 * <li><b>Rosin-Rammler grade efficiency curve</b> integrated over a log-normal droplet size
 * distribution</li>
 * <li><b>Multi-liner sizing</b> with standard liner diameters (35, 45, 60 mm)</li>
 * <li><b>PDR (Pressure Drop Ratio)</b> model for reject stream control</li>
 * <li><b>Turndown behaviour</b> with min/max flow per liner and efficiency degradation</li>
 * <li><b>OSPAR/NCS compliance checking</b> against 30 mg/L monthly weighted average</li>
 * <li><b>Capacity utilization</b> using the Separator constraint framework</li>
 * </ul>
 *
 * <h2>Governing Equations</h2>
 *
 * <p>
 * The cut size d50 for a cylindrical cyclone is derived from the centrifugal-Stokes balance:
 * </p>
 *
 * <pre>
 * d50 = sqrt(C * mu * Q / (deltaRho * D^2))
 * </pre>
 *
 * <p>
 * which simplifies for a standard deoiling liner to a correlation with flow, viscosity, and density
 * difference. The grade efficiency for droplet diameter d is:
 * </p>
 *
 * <pre>
 * eta(d) = 1 - exp(-0.693 * (d / d50)^n)
 * </pre>
 *
 * <p>
 * where n is the sharpness-of-cut index (typically 2-4, default 3). The overall efficiency is
 * obtained by integrating eta(d) over the droplet size distribution.
 * </p>
 *
 * <h2>Design Standards</h2>
 * <table>
 * <caption>Applicable standards for hydrocyclone design</caption>
 * <tr><th>Standard</th><th>Scope</th></tr>
 * <tr><td>NORSOK P-001</td><td>Process design, produced water treatment</td></tr>
 * <tr><td>OLF 052 (NOROG)</td><td>Produced water management NCS</td></tr>
 * <tr><td>OSPAR Rec 2001/1</td><td>30 mg/L OIW discharge limit</td></tr>
 * </table>
 *
 * <h2>Typical Operating Ranges</h2>
 * <table>
 * <caption>Operating ranges for oil-water hydrocyclones</caption>
 * <tr><th>Parameter</th><th>Range</th><th>Unit</th></tr>
 * <tr><td>d50 cut size</td><td>8-15</td><td>microns</td></tr>
 * <tr><td>Reject ratio (PDR-controlled)</td><td>1-3</td><td>% of feed</td></tr>
 * <tr><td>Pressure drop (inlet to overflow)</td><td>1-3</td><td>bar</td></tr>
 * <tr><td>Differential pressure (inlet-overflow)</td><td>2-6</td><td>bar</td></tr>
 * <tr><td>Turndown ratio</td><td>2.5:1 to 4:1</td><td>-</td></tr>
 * <tr><td>PDR</td><td>1.4-2.0</td><td>-</td></tr>
 * <tr><td>Oil removal (bulk)</td><td>90-98</td><td>%</td></tr>
 * </table>
 *
 * @author ESOL
 * @version 2.0
 */
public class Hydrocyclone extends Separator {
  private static final long serialVersionUID = 1001L;

  // ---------------------------------------------------------------------------
  // CONSTANTS
  // ---------------------------------------------------------------------------

  /** OSPAR / NCS monthly average OIW discharge limit (mg/L). */
  public static final double OSPAR_OIW_LIMIT_MGL = 30.0;

  /** Minimum recommended differential pressure for oil hydrocyclone (bar). */
  public static final double MIN_DESIGN_DP_BAR = 2.0;

  /** Recommended differential pressure for high efficiency (bar). */
  public static final double RECOMMENDED_DP_BAR = 5.0;

  // ---------------------------------------------------------------------------
  // LINER GEOMETRY
  // ---------------------------------------------------------------------------

  /** Standard liner cone diameters in mm. */
  public static final double[] STANDARD_LINER_SIZES_MM = {35.0, 45.0, 60.0};

  /** Liner cone diameter (mm). Default 35 mm is most common for oil-water. */
  private double linerDiameterMm = 35.0;

  /** Effective separation length of one liner (m). Derived from liner diameter. */
  private double linerEffectiveLengthM = 0.50;

  /** Number of active liners. */
  private int numberOfLiners = 1;

  /** Number of spare (blanked-off) liners. */
  private int numberOfSpareLiners = 0;

  /** Liners per vessel (pressure housing). Typical 4-12. */
  private int linersPerVessel = 6;

  // ---------------------------------------------------------------------------
  // OPERATING PARAMETERS
  // ---------------------------------------------------------------------------

  /** d50 cut size in microns (user-set or calculated). */
  private double d50Microns = 12.0;

  /** Whether d50 was explicitly set by user (vs calculated from conditions). */
  private boolean d50UserSet = false;

  /** Rosin-Rammler sharpness index n. Typical 2-4. */
  private double sharpnessIndex = 3.0;

  /** Reject ratio - fraction of feed going to reject (oil-rich) outlet. */
  private double rejectRatio = 0.02;

  /** Pressure drop across cyclone, inlet to clean water overflow (bar). */
  private double pressureDrop = 2.0;

  /** Pressure Drop Ratio: PDR = (P_in - P_reject) / (P_in - P_overflow). */
  private double pdr = 1.8;

  /** Overall oil removal efficiency (0-1). Calculated or user-set. */
  private double oilRemovalEfficiency = 0.95;

  /** Whether efficiency was explicitly set by user (vs calculated). */
  private boolean efficiencyUserSet = false;

  // ---------------------------------------------------------------------------
  // FLOW PARAMETERS
  // ---------------------------------------------------------------------------

  /** Design flow per liner (m3/h). Set from liner diameter lookup. */
  private double designFlowPerLinerM3h = 5.0;

  /** Minimum flow per liner (m3/h). Below this, centrifugal force is too low. */
  private double minFlowPerLinerM3h = 2.0;

  /** Maximum flow per liner (m3/h). Above this, droplet re-entrainment occurs. */
  private double maxFlowPerLinerM3h = 7.5;

  // ---------------------------------------------------------------------------
  // OIL-IN-WATER TRACKING
  // ---------------------------------------------------------------------------

  /** Inlet oil concentration (mg/L). */
  private double inletOilMgL = 1000.0;

  /** Outlet oil concentration (mg/L). Calculated. */
  private double outletOilMgL = 50.0;

  // ---------------------------------------------------------------------------
  // DROPLET SIZE DISTRIBUTION PARAMETERS (log-normal)
  // ---------------------------------------------------------------------------

  /** Median droplet diameter (volume-weighted) in microns. */
  private double dv50Microns = 30.0;

  /** Geometric standard deviation of droplet size distribution. */
  private double gsd = 2.5;

  // ---------------------------------------------------------------------------
  // FLUID PROPERTIES (used for d50 calculation)
  // ---------------------------------------------------------------------------

  /** Water dynamic viscosity (Pa.s). Default 20 degC. */
  private double waterViscosityPas = 1.0e-3;

  /** Oil density (kg/m3). */
  private double oilDensityKgm3 = 850.0;

  /** Water density (kg/m3). */
  private double waterDensityKgm3 = 1025.0;

  // ---------------------------------------------------------------------------
  // CALCULATED RESULTS
  // ---------------------------------------------------------------------------

  /** Calculated feed flow rate (m3/h). */
  private double feedFlowM3h = 0.0;

  /** Calculated flow per liner (m3/h). */
  private double flowPerLinerM3h = 0.0;

  /** Calculated capacity utilization (0-1+). */
  private double capacityUtilization = 0.0;

  /** Calculated d50 from Stokes model. */
  private double calculatedD50 = 12.0;

  /** Calculated overall efficiency from DSD integration. */
  private double calculatedEfficiency = 0.0;

  /** Number of vessels (calculated from liners). */
  private int numberOfVessels = 1;

  /** Hydrocyclone-specific mechanical design instance. */
  private HydrocycloneMechanicalDesign hydrocycloneMechanicalDesign;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a hydrocyclone with default parameters.
   *
   * @param name equipment name/tag
   */
  public Hydrocyclone(String name) {
    super(name);
    setOrientation("vertical");
    initLinerDefaults();
  }

  /**
   * Creates a hydrocyclone with an inlet stream.
   *
   * @param name equipment name/tag
   * @param inletStream produced water stream containing dispersed oil
   */
  public Hydrocyclone(String name, StreamInterface inletStream) {
    super(name, inletStream);
    setOrientation("vertical");
    initLinerDefaults();
  }

  /**
   * Sets liner geometry defaults based on current liner diameter.
   */
  private void initLinerDefaults() {
    updateLinerCapacityFromDiameter();
  }

  // ============================================================================
  // MECHANICAL DESIGN
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public HydrocycloneMechanicalDesign getMechanicalDesign() {
    return hydrocycloneMechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    hydrocycloneMechanicalDesign = new HydrocycloneMechanicalDesign(this);
  }

  // ============================================================================
  // LINER CONFIGURATION
  // ============================================================================

  /**
   * Sets the liner cone diameter from standard sizes: 35, 45, or 60 mm.
   *
   * <p>
   * The liner diameter determines the design, minimum, and maximum flow per liner. Smaller liners
   * (35 mm) give finer cut sizes and are preferred for deoiling. Larger liners (60 mm) handle
   * higher flow per liner and are used where coarser separation is acceptable.
   * </p>
   *
   * @param diameterMm liner cone diameter in mm (35, 45, or 60)
   */
  public void setLinerDiameterMm(double diameterMm) {
    this.linerDiameterMm = diameterMm;
    updateLinerCapacityFromDiameter();
  }

  /**
   * Gets the liner cone diameter.
   *
   * @return diameter in mm
   */
  public double getLinerDiameterMm() {
    return linerDiameterMm;
  }

  /**
   * Updates flow-per-liner capacity based on liner diameter.
   *
   * <p>
   * Flow capacity scales approximately with D^2 (cross-sectional area). The reference is a 35 mm
   * liner at 5 m3/h design flow. These values are based on typical vendor data for deoiling
   * hydrocyclones (Vortoil, CDS Natco).
   * </p>
   */
  private void updateLinerCapacityFromDiameter() {
    double scaleFactor = Math.pow(linerDiameterMm / 35.0, 2.0);
    designFlowPerLinerM3h = 5.0 * scaleFactor;
    minFlowPerLinerM3h = 2.0 * scaleFactor;
    maxFlowPerLinerM3h = 7.5 * scaleFactor;
    linerEffectiveLengthM = 0.50 * (linerDiameterMm / 35.0);
  }

  /**
   * Sets the number of active liners.
   *
   * @param liners number of active liners
   */
  public void setNumberOfLiners(int liners) {
    this.numberOfLiners = Math.max(1, liners);
  }

  /**
   * Gets the number of active liners.
   *
   * @return number of active liners
   */
  public int getNumberOfLiners() {
    return numberOfLiners;
  }

  /**
   * Sets the number of spare (blanked-off) liners for turndown/redundancy.
   *
   * @param spares number of spare liners
   */
  public void setNumberOfSpareLiners(int spares) {
    this.numberOfSpareLiners = Math.max(0, spares);
  }

  /**
   * Gets the number of spare liners.
   *
   * @return spare liners
   */
  public int getNumberOfSpareLiners() {
    return numberOfSpareLiners;
  }

  /**
   * Sets the number of liners per vessel (pressure housing).
   *
   * @param linersPerVessel liners per vessel (typically 4-12)
   */
  public void setLinersPerVessel(int linersPerVessel) {
    this.linersPerVessel = Math.max(1, linersPerVessel);
  }

  /**
   * Gets the number of liners per vessel.
   *
   * @return liners per vessel
   */
  public int getLinersPerVessel() {
    return linersPerVessel;
  }

  // ============================================================================
  // OPERATING PARAMETERS
  // ============================================================================

  /**
   * Sets the d50 cut size. When set explicitly, the Stokes calculation is bypassed.
   *
   * @param d50 cut size in microns
   */
  public void setD50Microns(double d50) {
    this.d50Microns = d50;
    this.d50UserSet = true;
  }

  /**
   * Gets the d50 cut size (user-set or calculated).
   *
   * @return d50 in microns
   */
  public double getD50Microns() {
    return d50Microns;
  }

  /**
   * Sets the Rosin-Rammler sharpness index.
   *
   * @param n sharpness index (typical 2-4)
   */
  public void setSharpnessIndex(double n) {
    this.sharpnessIndex = Math.max(1.0, Math.min(6.0, n));
  }

  /**
   * Gets the sharpness index.
   *
   * @return sharpness index
   */
  public double getSharpnessIndex() {
    return sharpnessIndex;
  }

  /**
   * Sets the reject ratio (fraction of feed going to oil-rich reject stream).
   *
   * @param ratio reject/feed ratio (0.005-0.10 typical)
   */
  public void setRejectRatio(double ratio) {
    this.rejectRatio = Math.min(0.10, Math.max(0.005, ratio));
  }

  /**
   * Gets the reject ratio.
   *
   * @return reject ratio
   */
  public double getRejectRatio() {
    return rejectRatio;
  }

  /**
   * Sets the pressure drop from inlet to clean water overflow.
   *
   * @param dp pressure drop in bar
   */
  public void setPressureDrop(double dp) {
    this.pressureDrop = Math.max(0.0, dp);
    super.setPressureDrop(dp);
  }

  /**
   * Gets the pressure drop (inlet to overflow).
   *
   * @return pressure drop in bar
   */
  public double getPressureDropBar() {
    return pressureDrop;
  }

  /**
   * Sets pressure drop in bar. Alias for setPressureDrop kept for backward compatibility.
   *
   * @param dp pressure drop in bar
   */
  public void setPressureDropBar(double dp) {
    setPressureDrop(dp);
  }

  /**
   * Sets the Pressure Drop Ratio. PDR = (P_inlet - P_reject) / (P_inlet - P_overflow).
   *
   * <p>
   * PDR controls the reject split. Higher PDR drives more flow to reject, increasing oil removal
   * but also water loss. Typical 1.4-2.0.
   * </p>
   *
   * @param pdr pressure drop ratio (typically 1.4-2.0)
   */
  public void setPDR(double pdr) {
    this.pdr = Math.max(1.0, Math.min(5.0, pdr));
  }

  /**
   * Gets the Pressure Drop Ratio.
   *
   * @return PDR
   */
  public double getPDR() {
    return pdr;
  }

  /**
   * Sets the overall oil removal efficiency directly. When set, the DSD integration is bypassed.
   *
   * @param efficiency efficiency (0.0-1.0)
   */
  public void setOilRemovalEfficiency(double efficiency) {
    this.oilRemovalEfficiency = Math.min(1.0, Math.max(0.0, efficiency));
    this.efficiencyUserSet = true;
  }

  /**
   * Gets the oil removal efficiency (user-set or calculated).
   *
   * @return efficiency (0.0-1.0)
   */
  public double getOilRemovalEfficiency() {
    return oilRemovalEfficiency;
  }

  /**
   * Sets the inlet oil-in-water concentration.
   *
   * @param oilMgL oil concentration in mg/L
   */
  public void setInletOilConcentration(double oilMgL) {
    this.inletOilMgL = Math.max(0.0, oilMgL);
  }

  /**
   * Gets the inlet oil concentration.
   *
   * @return oil concentration in mg/L
   */
  public double getInletOilConcentration() {
    return inletOilMgL;
  }

  // ============================================================================
  // DROPLET SIZE DISTRIBUTION
  // ============================================================================

  /**
   * Sets the volume-weighted median droplet diameter of the inlet DSD.
   *
   * <p>
   * Typical values for produced water after a separator are 20-50 microns. Lower values (10-20)
   * indicate heavy/viscous oil or shear from choke valves.
   * </p>
   *
   * @param dv50 volume median diameter in microns
   */
  public void setDv50Microns(double dv50) {
    this.dv50Microns = Math.max(1.0, dv50);
  }

  /**
   * Gets the volume median droplet diameter.
   *
   * @return dv50 in microns
   */
  public double getDv50Microns() {
    return dv50Microns;
  }

  /**
   * Sets the geometric standard deviation of the log-normal DSD.
   *
   * <p>
   * Typical values 2.0-3.5. Larger GSD means a wider DSD with more fine droplets.
   * </p>
   *
   * @param gsd geometric standard deviation
   */
  public void setGeometricStdDev(double gsd) {
    this.gsd = Math.max(1.1, Math.min(5.0, gsd));
  }

  /**
   * Gets the geometric standard deviation.
   *
   * @return GSD
   */
  public double getGeometricStdDev() {
    return gsd;
  }

  // ============================================================================
  // FLUID PROPERTY OVERRIDES (for manual specification)
  // ============================================================================

  /**
   * Sets the water dynamic viscosity.
   *
   * @param viscosityPas viscosity in Pa.s (0.001 = water at 20 degC)
   */
  public void setWaterViscosity(double viscosityPas) {
    this.waterViscosityPas = viscosityPas;
  }

  /**
   * Sets the oil density.
   *
   * @param densityKgm3 oil density in kg/m3
   */
  public void setOilDensity(double densityKgm3) {
    this.oilDensityKgm3 = densityKgm3;
  }

  /**
   * Sets the water density.
   *
   * @param densityKgm3 water density in kg/m3
   */
  public void setWaterDensity(double densityKgm3) {
    this.waterDensityKgm3 = densityKgm3;
  }

  // ============================================================================
  // SIZING CALCULATIONS
  // ============================================================================

  /**
   * Calculates the required number of liners for a given total water flow rate.
   *
   * <p>
   * Uses the design flow per liner and rounds up to the nearest integer.
   * </p>
   *
   * @param totalFlowM3h total water flow rate in m3/h
   * @return required number of active liners
   */
  public int calcNumberOfLiners(double totalFlowM3h) {
    int required = (int) Math.ceil(totalFlowM3h / designFlowPerLinerM3h);
    this.numberOfLiners = Math.max(1, required);
    this.numberOfVessels = calcNumberOfVessels();
    return this.numberOfLiners;
  }

  /**
   * Auto-sizes the hydrocyclone for the current inlet stream flow rate.
   *
   * <p>
   * Determines the number of liners, vessels, and updates capacity utilization. Should be called
   * after the inlet stream has been configured and run.
   * </p>
   */
  public void autoSize() {
    if (getInletStreams() != null && !getInletStreams().isEmpty()) {
      StreamInterface inlet = getInletStreams().get(0);
      if (inlet != null && inlet.getFluid() != null) {
        SystemInterface fluid = inlet.getFluid();
        try {
          double volM3s = fluid.getVolume("m3");
          feedFlowM3h = volM3s * 3600.0;
        } catch (Exception ex) {
          // Use default
        }
      }
    }

    if (feedFlowM3h > 0.0) {
      calcNumberOfLiners(feedFlowM3h);
      flowPerLinerM3h = feedFlowM3h / numberOfLiners;
      capacityUtilization = flowPerLinerM3h / designFlowPerLinerM3h;
    }
  }

  /**
   * Calculates the number of vessels needed.
   *
   * @return number of vessels
   */
  public int calcNumberOfVessels() {
    int totalLiners = numberOfLiners + numberOfSpareLiners;
    this.numberOfVessels = (int) Math.ceil((double) totalLiners / linersPerVessel);
    return this.numberOfVessels;
  }

  /**
   * Gets the number of vessels (pressure housings).
   *
   * @return number of vessels
   */
  public int getNumberOfVessels() {
    return numberOfVessels;
  }

  /**
   * Gets the total package capacity at design flow.
   *
   * @return maximum design capacity in m3/h
   */
  public double getMaxDesignCapacityM3h() {
    return numberOfLiners * designFlowPerLinerM3h;
  }

  /**
   * Gets the minimum operatable flow rate (all liners at minimum flow).
   *
   * @return minimum total flow in m3/h
   */
  public double getMinOperatingFlowM3h() {
    return numberOfLiners * minFlowPerLinerM3h;
  }

  /**
   * Gets the maximum operatable flow rate (all liners at maximum flow).
   *
   * @return maximum total flow in m3/h
   */
  public double getMaxOperatingFlowM3h() {
    return numberOfLiners * maxFlowPerLinerM3h;
  }

  /**
   * Gets the turndown ratio based on liner operating range.
   *
   * @return turndown ratio (max/min for a single liner)
   */
  public double getTurndownRatio() {
    if (minFlowPerLinerM3h <= 0) {
      return 1.0;
    }
    return maxFlowPerLinerM3h / minFlowPerLinerM3h;
  }

  /**
   * Gets the design flow per liner.
   *
   * @return design flow in m3/h
   */
  public double getDesignFlowPerLinerM3h() {
    return designFlowPerLinerM3h;
  }

  /**
   * Gets the minimum flow per liner.
   *
   * @return minimum flow in m3/h
   */
  public double getMinFlowPerLinerM3h() {
    return minFlowPerLinerM3h;
  }

  /**
   * Gets the maximum flow per liner.
   *
   * @return maximum flow in m3/h
   */
  public double getMaxFlowPerLinerM3h() {
    return maxFlowPerLinerM3h;
  }

  /**
   * Gets the current flow per liner (after run or auto-size).
   *
   * @return current flow per liner in m3/h
   */
  public double getFlowPerLinerM3h() {
    return flowPerLinerM3h;
  }

  // ============================================================================
  // PHYSICS-BASED d50 CALCULATION
  // ============================================================================

  /**
   * Calculates the d50 cut size from the centrifugal Stokes settling model.
   *
   * <p>
   * The cut size depends on the flow rate per liner, water viscosity, density difference between oil
   * and water, and liner geometry. The simplified correlation is calibrated so that a 35 mm liner at
   * 5 m3/h with water at 20 degC and deltaRho 175 kg/m3 gives d50 of approximately 12 microns.
   * </p>
   *
   * @return calculated d50 in microns
   */
  public double calcD50FromConditions() {
    double deltaRho = waterDensityKgm3 - oilDensityKgm3;
    if (deltaRho <= 0.0) {
      calculatedD50 = 999.0;
      return calculatedD50;
    }

    double qPerLiner = flowPerLinerM3h > 0.0 ? flowPerLinerM3h : designFlowPerLinerM3h;
    double qM3s = qPerLiner / 3600.0;
    double dLiner = linerDiameterMm / 1000.0;

    // Empirical constant calibrated to vendor data:
    // 35 mm liner, 5 m3/h, mu=1e-3 Pa.s, deltaRho=175 kg/m3 -> d50 ~12 microns
    double cEmpirical = 2.2e-5;
    double d50Sq = cEmpirical * waterViscosityPas * qM3s / (deltaRho * dLiner * dLiner);

    calculatedD50 = Math.sqrt(d50Sq) * 1.0e6; // m -> microns
    return calculatedD50;
  }

  // ============================================================================
  // GRADE EFFICIENCY CURVE
  // ============================================================================

  /**
   * Calculates removal efficiency for a specific droplet diameter using the Rosin-Rammler model.
   *
   * @param dropletSizeMicrons droplet diameter in microns
   * @return removal efficiency (0.0-1.0)
   */
  public double getEfficiencyForDropletSize(double dropletSizeMicrons) {
    double ratio = dropletSizeMicrons / d50Microns;
    return 1.0 - Math.exp(-0.693 * Math.pow(ratio, sharpnessIndex));
  }

  /**
   * Integrates the grade efficiency curve over a log-normal droplet size distribution to calculate
   * the overall separation efficiency.
   *
   * <p>
   * Uses 50-point numerical quadrature over the log-normal distribution from 0.5 micron to 5x the
   * median diameter.
   * </p>
   *
   * @return overall efficiency (0.0-1.0) weighted by volume
   */
  public double calcEfficiencyFromDSD() {
    int nPoints = 50;
    double logDv50 = Math.log(dv50Microns);
    double logSigma = Math.log(gsd);

    double dMin = 0.5;
    double dMax = dv50Microns * 5.0;
    double logDMin = Math.log(dMin);
    double logDMax = Math.log(dMax);
    double dLogD = (logDMax - logDMin) / (nPoints - 1);

    double numerator = 0.0;
    double denominator = 0.0;

    for (int i = 0; i < nPoints; i++) {
      double logD = logDMin + i * dLogD;
      double d = Math.exp(logD);

      double exponent = -0.5 * Math.pow((logD - logDv50) / logSigma, 2.0);
      double pdf = Math.exp(exponent);

      double eta = getEfficiencyForDropletSize(d);

      numerator += eta * pdf;
      denominator += pdf;
    }

    if (denominator > 0.0) {
      calculatedEfficiency = numerator / denominator;
    } else {
      calculatedEfficiency = oilRemovalEfficiency;
    }

    return calculatedEfficiency;
  }

  // ============================================================================
  // PDR MODEL
  // ============================================================================

  /**
   * Estimates the reject ratio from the Pressure Drop Ratio.
   *
   * <p>
   * The reject ratio is approximately RR = 0.01 * PDR^1.5 for standard deoiling hydrocyclones.
   * Gives RR approximately 1.5% at PDR 1.4 and approximately 2.5% at PDR 2.0.
   * </p>
   *
   * @return estimated reject ratio
   */
  public double calcRejectRatioFromPDR() {
    return 0.01 * Math.pow(pdr, 1.5);
  }

  /**
   * Estimates the efficiency correction factor based on PDR.
   *
   * <p>
   * Efficiency is optimum at PDR around 1.8. Below 1.3, short-circuiting degrades performance.
   * Above 2.5, excessive water loss without much efficiency gain.
   * </p>
   *
   * @return PDR correction factor (multiply with base efficiency)
   */
  public double getPDREfficiencyFactor() {
    if (pdr < 1.2) {
      return 0.70;
    } else if (pdr < 1.4) {
      return 0.70 + 0.25 * (pdr - 1.2) / 0.2;
    } else if (pdr <= 2.2) {
      return 0.95 + 0.05 * (pdr - 1.4) / 0.8;
    } else {
      return 1.0;
    }
  }

  // ============================================================================
  // DESIGN VALIDATION
  // ============================================================================

  /**
   * Checks whether the available differential pressure meets the minimum design requirement.
   *
   * @return true if pressure drop meets minimum (2 bar)
   */
  public boolean isDifferentialPressureAdequate() {
    return pressureDrop >= MIN_DESIGN_DP_BAR;
  }

  /**
   * Checks if the hydrocyclone meets OSPAR 30 mg/L discharge limit.
   *
   * @return true if outlet OIW is at or below 30 mg/L
   */
  public boolean isOSPARCompliant() {
    return outletOilMgL <= OSPAR_OIW_LIMIT_MGL;
  }

  /**
   * Checks if each liner is operating within its valid flow range.
   *
   * @return true if within turndown/turnup limits
   */
  public boolean isWithinOperatingRange() {
    return flowPerLinerM3h >= minFlowPerLinerM3h && flowPerLinerM3h <= maxFlowPerLinerM3h;
  }

  /**
   * Calculates the required inlet pressure given downstream back-pressures.
   *
   * @param waterOutletPressureBar water outlet back-pressure (bar)
   * @param rejectValveDPBar pressure drop across reject control valve (bar)
   * @param rejectLineDPBar pressure drop in reject piping (bar)
   * @param heightDifferenceDPBar static head difference to reject destination (bar)
   * @return required inlet pressure (bar)
   */
  public double calcRequiredInletPressure(double waterOutletPressureBar, double rejectValveDPBar,
      double rejectLineDPBar, double heightDifferenceDPBar) {
    return waterOutletPressureBar + pressureDrop + rejectValveDPBar + rejectLineDPBar
        + heightDifferenceDPBar;
  }

  /**
   * Estimates oil removal efficiency based on available differential pressure and water
   * temperature.
   *
   * <p>
   * Higher dP improves efficiency. Lower temperature (higher viscosity) reduces efficiency. Base
   * efficiency is at 20 degC and 5 bar dP.
   * </p>
   *
   * @param availableDPBar available differential pressure (bar)
   * @param waterTemperatureC water temperature (degrees Celsius)
   * @return estimated oil removal efficiency (0.0-1.0)
   */
  public double estimateEfficiencyFromConditions(double availableDPBar, double waterTemperatureC) {
    double baseEfficiency = 0.95;

    double dpFactor = 1.0;
    if (availableDPBar < RECOMMENDED_DP_BAR) {
      dpFactor = 0.7 + 0.3 * (availableDPBar / RECOMMENDED_DP_BAR);
    }
    dpFactor = Math.min(1.0, dpFactor);

    double tempFactor = 1.0;
    if (waterTemperatureC < 20.0) {
      tempFactor = 0.85 + 0.15 * (waterTemperatureC / 20.0);
    } else if (waterTemperatureC > 50.0) {
      tempFactor = 1.0;
    }
    tempFactor = Math.max(0.5, Math.min(1.0, tempFactor));

    return baseEfficiency * dpFactor * tempFactor;
  }

  // ============================================================================
  // CAPACITY UTILIZATION
  // ============================================================================

  /**
   * Gets the capacity utilization as the ratio of actual flow per liner to design flow.
   *
   * <p>
   * Values below 0.4 indicate under-utilisation (poor separation). Values above 1.5 indicate
   * significant overload (droplet re-entrainment). Optimum is 0.7-1.2.
   * </p>
   *
   * @return capacity utilization (0.0 = empty, 1.0 = design flow)
   */
  public double getHydrocycloneCapacityUtilization() {
    if (designFlowPerLinerM3h <= 0.0) {
      return 0.0;
    }
    return flowPerLinerM3h / designFlowPerLinerM3h;
  }

  /**
   * Checks if the hydrocyclone package is overloaded (flow per liner exceeds maximum).
   *
   * @return true if overloaded
   */
  public boolean isOverloaded() {
    return flowPerLinerM3h > maxFlowPerLinerM3h;
  }

  // ============================================================================
  // RUN
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    super.run(id);

    extractFluidProperties();
    calcFeedFlow();

    if (numberOfLiners > 0 && feedFlowM3h > 0.0) {
      flowPerLinerM3h = feedFlowM3h / numberOfLiners;
    }

    if (!d50UserSet) {
      calcD50FromConditions();
      d50Microns = calculatedD50;
    }

    if (!efficiencyUserSet) {
      double dsdEfficiency = calcEfficiencyFromDSD();
      double pdrFactor = getPDREfficiencyFactor();
      oilRemovalEfficiency = Math.min(1.0, dsdEfficiency * pdrFactor);
    }

    rejectRatio = calcRejectRatioFromPDR();
    outletOilMgL = inletOilMgL * (1.0 - oilRemovalEfficiency);
    capacityUtilization = getHydrocycloneCapacityUtilization();
    numberOfVessels = calcNumberOfVessels();

    setCalculationIdentifier(id);
  }

  /**
   * Extracts fluid properties from the inlet stream for d50 and efficiency calculations.
   */
  private void extractFluidProperties() {
    if (getInletStreams() == null || getInletStreams().isEmpty()) {
      return;
    }
    StreamInterface inlet = getInletStreams().get(0);
    if (inlet == null || inlet.getFluid() == null) {
      return;
    }
    SystemInterface fluid = inlet.getFluid();

    try {
      if (fluid.hasPhaseType("aqueous")) {
        waterDensityKgm3 = fluid.getPhase("aqueous").getDensity("kg/m3");
        waterViscosityPas = fluid.getPhase("aqueous").getViscosity("kg/msec");
        if (waterViscosityPas <= 0.0) {
          waterViscosityPas = 1.0e-3;
        }
      }
      if (fluid.hasPhaseType("oil")) {
        oilDensityKgm3 = fluid.getPhase("oil").getDensity("kg/m3");
      }
    } catch (Exception ex) {
      // Use defaults if property extraction fails
    }
  }

  /**
   * Calculates the total feed flow rate from the inlet stream.
   */
  private void calcFeedFlow() {
    if (getInletStreams() == null || getInletStreams().isEmpty()) {
      return;
    }
    StreamInterface inlet = getInletStreams().get(0);
    if (inlet == null || inlet.getFluid() == null) {
      return;
    }
    SystemInterface fluid = inlet.getFluid();

    try {
      double volM3s = fluid.getVolume("m3");
      feedFlowM3h = volM3s * 3600.0;
    } catch (Exception ex) {
      // Keep previous value
    }
  }

  // ============================================================================
  // RESULTS
  // ============================================================================

  /**
   * Gets the outlet oil-in-water concentration.
   *
   * @return OIW in mg/L
   */
  public double getOutletOilMgL() {
    return outletOilMgL;
  }

  /**
   * Gets the feed flow rate.
   *
   * @return feed flow in m3/h
   */
  public double getFeedFlowM3h() {
    return feedFlowM3h;
  }

  /**
   * Gets the calculated d50 (from Stokes model).
   *
   * @return calculated d50 in microns
   */
  public double getCalculatedD50() {
    return calculatedD50;
  }

  /**
   * Gets the calculated efficiency from DSD integration.
   *
   * @return calculated efficiency (0.0-1.0)
   */
  public double getCalculatedEfficiency() {
    return calculatedEfficiency;
  }

  /**
   * Gets the oil mass removed per hour.
   *
   * @return removed oil in kg/h
   */
  public double getRemovedOilKgPerHour() {
    double removedConcentration = inletOilMgL - outletOilMgL;
    double waterVolumeM3h = feedFlowM3h * (1.0 - rejectRatio);
    return removedConcentration * waterVolumeM3h * 1000.0 * 1e-6;
  }

  /**
   * Gets the reject stream flow rate.
   *
   * @return reject flow in m3/h
   */
  public double getRejectFlowM3h() {
    return feedFlowM3h * rejectRatio;
  }

  /**
   * Gets the clean water (overflow) flow rate.
   *
   * @return overflow flow in m3/h
   */
  public double getOverflowFlowM3h() {
    return feedFlowM3h * (1.0 - rejectRatio);
  }

  // ============================================================================
  // SIZING REPORT
  // ============================================================================

  /**
   * Gets a comprehensive design and sizing summary.
   *
   * @return human-readable design report
   */
  public String getDesignValidationSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Hydrocyclone Design & Sizing Report ===\n");
    sb.append(String.format("Equipment tag: %s\n\n", getName()));

    sb.append("--- Liner Configuration ---\n");
    sb.append(String.format("Liner diameter:       %.0f mm\n", linerDiameterMm));
    sb.append(String.format("Active liners:        %d\n", numberOfLiners));
    sb.append(String.format("Spare liners:         %d\n", numberOfSpareLiners));
    sb.append(String.format("Total liners:         %d\n", numberOfLiners + numberOfSpareLiners));
    sb.append(String.format("Liners per vessel:    %d\n", linersPerVessel));
    sb.append(String.format("Number of vessels:    %d\n", numberOfVessels));
    sb.append('\n');

    sb.append("--- Flow Capacity ---\n");
    sb.append(String.format("Design flow/liner:    %.1f m3/h\n", designFlowPerLinerM3h));
    sb.append(String.format("Min flow/liner:       %.1f m3/h\n", minFlowPerLinerM3h));
    sb.append(String.format("Max flow/liner:       %.1f m3/h\n", maxFlowPerLinerM3h));
    sb.append(String.format("Turndown ratio:       %.1f:1\n", getTurndownRatio()));
    sb.append(String.format("Total design cap:     %.1f m3/h\n", getMaxDesignCapacityM3h()));
    sb.append(String.format("Min operating flow:   %.1f m3/h\n", getMinOperatingFlowM3h()));
    sb.append(String.format("Max operating flow:   %.1f m3/h\n", getMaxOperatingFlowM3h()));
    sb.append('\n');

    sb.append("--- Operating Point ---\n");
    sb.append(String.format("Feed flow:            %.1f m3/h\n", feedFlowM3h));
    sb.append(String.format("Flow per liner:       %.1f m3/h\n", flowPerLinerM3h));
    sb.append(String.format("Capacity utilization: %.0f%%\n", capacityUtilization * 100.0));
    sb.append(String.format("Within range:         %s\n",
        isWithinOperatingRange() ? "OK" : "OUT OF RANGE"));
    sb.append(String.format("Overloaded:           %s\n", isOverloaded() ? "YES" : "No"));
    sb.append('\n');

    sb.append("--- Separation Performance ---\n");
    sb.append(String.format("d50 cut size:         %.1f microns%s\n", d50Microns,
        d50UserSet ? " (user-set)" : " (calculated)"));
    if (!d50UserSet) {
      sb.append(String.format("  Calc d50 (Stokes):  %.1f microns\n", calculatedD50));
    }
    sb.append(String.format("Sharpness index n:    %.1f\n", sharpnessIndex));
    sb.append(String.format("Oil removal eff:      %.1f%%%s\n", oilRemovalEfficiency * 100.0,
        efficiencyUserSet ? " (user-set)" : " (calculated)"));
    if (!efficiencyUserSet) {
      sb.append(String.format("  DSD efficiency:     %.1f%%\n", calculatedEfficiency * 100.0));
      sb.append(String.format("  PDR eff. factor:    %.3f\n", getPDREfficiencyFactor()));
    }
    sb.append('\n');

    sb.append("--- Pressure & PDR ---\n");
    sb.append(String.format("Pressure drop:        %.1f bar (min: %.1f, recommended: %.1f)\n",
        pressureDrop, MIN_DESIGN_DP_BAR, RECOMMENDED_DP_BAR));
    sb.append(String.format("dP adequate:          %s\n",
        isDifferentialPressureAdequate() ? "OK" : "INSUFFICIENT"));
    sb.append(String.format("PDR:                  %.2f\n", pdr));
    sb.append(String.format("Reject ratio:         %.1f%%\n", rejectRatio * 100.0));
    sb.append('\n');

    sb.append("--- OIW Performance ---\n");
    sb.append(String.format("Inlet OIW:            %.0f mg/L\n", inletOilMgL));
    sb.append(String.format("Outlet OIW:           %.0f mg/L\n", outletOilMgL));
    sb.append(String.format("OSPAR compliant:      %s (limit: %.0f mg/L)\n",
        isOSPARCompliant() ? "YES" : "NO", OSPAR_OIW_LIMIT_MGL));
    sb.append('\n');

    sb.append("--- Fluid Properties ---\n");
    sb.append(String.format("Water viscosity:      %.4f mPa.s\n", waterViscosityPas * 1000.0));
    sb.append(String.format("Water density:        %.0f kg/m3\n", waterDensityKgm3));
    sb.append(String.format("Oil density:          %.0f kg/m3\n", oilDensityKgm3));
    sb.append(
        String.format("Density difference:   %.0f kg/m3\n", waterDensityKgm3 - oilDensityKgm3));
    sb.append('\n');

    sb.append("--- DSD Parameters ---\n");
    sb.append(String.format("Volume median dv50:   %.1f microns\n", dv50Microns));
    sb.append(String.format("Geometric std dev:    %.1f\n", gsd));

    return sb.toString();
  }

  /**
   * Returns a map of sizing results for programmatic access.
   *
   * @return map of sizing parameter names to values
   */
  public Map<String, Object> getSizingResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("linerDiameterMm", linerDiameterMm);
    results.put("activeLiners", numberOfLiners);
    results.put("spareLiners", numberOfSpareLiners);
    results.put("totalLiners", numberOfLiners + numberOfSpareLiners);
    results.put("linersPerVessel", linersPerVessel);
    results.put("numberOfVessels", numberOfVessels);
    results.put("designFlowPerLinerM3h", designFlowPerLinerM3h);
    results.put("minFlowPerLinerM3h", minFlowPerLinerM3h);
    results.put("maxFlowPerLinerM3h", maxFlowPerLinerM3h);
    results.put("turndownRatio", getTurndownRatio());
    results.put("totalDesignCapacityM3h", getMaxDesignCapacityM3h());
    results.put("feedFlowM3h", feedFlowM3h);
    results.put("flowPerLinerM3h", flowPerLinerM3h);
    results.put("capacityUtilization", capacityUtilization);
    results.put("d50Microns", d50Microns);
    results.put("oilRemovalEfficiency", oilRemovalEfficiency);
    results.put("pressureDropBar", pressureDrop);
    results.put("pdr", pdr);
    results.put("rejectRatio", rejectRatio);
    results.put("inletOilMgL", inletOilMgL);
    results.put("outletOilMgL", outletOilMgL);
    results.put("osparCompliant", isOSPARCompliant());
    results.put("withinOperatingRange", isWithinOperatingRange());
    results.put("overflowFlowM3h", getOverflowFlowM3h());
    results.put("rejectFlowM3h", getRejectFlowM3h());
    return results;
  }
}

