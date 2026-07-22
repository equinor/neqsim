package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Screening-level model of solid deposit (fouling) accumulation on a centrifugal compressor impeller and its effect on
 * aerodynamic performance.
 *
 * <p>
 * The model bridges deposit-generating flow-assurance calculations (elemental sulfur drop-out, salt carry-over, mineral
 * scale, corrosion products, wax) and compressor performance. Any number of deposit {@link DepositMechanism mechanisms}
 * can be accumulated as a <em>mass</em> (kg) and the combined deposit is mapped to a fractional flow-area blockage and
 * hence to a polytropic efficiency multiplier and a polytropic head multiplier.
 * </p>
 *
 * <h2>Physical screening model</h2>
 * <ol>
 * <li>Deposit volume: {@code V = sum(mass_i / density_i)} over all mechanisms.</li>
 * <li>Film thickness on the foulable (wetted) impeller/inlet area: {@code t = V / wettedArea}.</li>
 * <li>Flow-area blockage: {@code b = min(t / passageHalfHeight, maxBlockage)}.</li>
 * <li>Efficiency multiplier: {@code m_eff = max(1 - kEff * b, minEff)} — area reduction, added surface roughness and
 * incidence losses compound (default {@code kEff = 1.6}).</li>
 * <li>Head multiplier: {@code m_head = max((1 - b)^headExponent, minHead)} — throughput/area effect (default exponent
 * 2).</li>
 * </ol>
 *
 * <p>
 * The relations are screening-level. Coefficients ({@code kEff}, {@code headExponent}) are exposed so the model can be
 * calibrated to measured degradation (for example a root-cause-analysis clean / partial / fully-degraded set of
 * points).
 * </p>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * CompressorDeposit dep = CompressorDeposit.fromCompressor(compressor);
 * dep.addDeposit(DepositMechanism.SULFUR_S8, 1.2); // kg from a sulfur drop-out study
 * dep.addDeposit(DepositMechanism.SALT_NACL, 0.4); // kg from a salt carry-over study
 * compressor.setDepositModel(dep); // degradation now applied in run()
 * compressor.run();
 * double effLoss = 1.0 - dep.getEfficiencyMultiplier();
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorDeposit implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Accumulated deposit mass per mechanism [kg]. */
  private final Map<DepositMechanism, Double> depositMass = new EnumMap<>(DepositMechanism.class);

  /** Foulable (wetted) impeller / inlet area available for deposition [m2]. */
  private double wettedArea = Double.NaN;

  /** Characteristic flow-passage half-height [m] used for the blockage estimate. */
  private double passageHalfHeight = Double.NaN;

  /** Efficiency-blockage coefficient (area + roughness + incidence losses). */
  private double efficiencyBlockageCoefficient = 1.6;

  /** Head-blockage exponent. */
  private double headBlockageExponent = 2.0;

  /** Maximum allowed flow-area blockage fraction. */
  private double maxBlockageFraction = 0.95;

  /** Lower clamp on the efficiency multiplier. */
  private double minEfficiencyMultiplier = 0.02;

  /** Lower clamp on the head multiplier. */
  private double minHeadMultiplier = 0.05;

  /**
   * Default constructor. Impeller geometry must be set via {@link #setWettedArea(double)} and
   * {@link #setPassageHalfHeight(double)}, or with {@link #sizeImpellerFromInletFlow(double, double)} /
   * {@link #fromCompressor(Compressor)} before performance multipliers are meaningful.
   */
  public CompressorDeposit() {
  }

  /**
   * Constructor with explicit foulable geometry.
   *
   * @param wettedArea foulable impeller / inlet area in m2
   * @param passageHalfHeight characteristic flow-passage half-height in m
   */
  public CompressorDeposit(double wettedArea, double passageHalfHeight) {
    this.wettedArea = wettedArea;
    this.passageHalfHeight = passageHalfHeight;
  }

  // ==========================================================================
  // Deposit inventory
  // ==========================================================================

  /**
   * Add (accumulate) deposit mass for a mechanism.
   *
   * @param mechanism deposit mechanism
   * @param massKg deposit mass to add in kg (negative values are ignored)
   */
  public void addDeposit(DepositMechanism mechanism, double massKg) {
    if (mechanism == null || massKg <= 0.0 || Double.isNaN(massKg)) {
      return;
    }
    double current = depositMass.containsKey(mechanism) ? depositMass.get(mechanism) : 0.0;
    depositMass.put(mechanism, current + massKg);
  }

  /**
   * Accumulate deposit over a time interval given a deposition rate.
   *
   * @param mechanism deposit mechanism
   * @param rateKgPerHour deposition rate in kg/hr (for example from a sulfur or scale study)
   * @param hours duration in hours
   */
  public void accumulate(DepositMechanism mechanism, double rateKgPerHour, double hours) {
    if (rateKgPerHour <= 0.0 || hours <= 0.0) {
      return;
    }
    addDeposit(mechanism, rateKgPerHour * hours);
  }

  /**
   * Accumulate deposit from a {@link DepositSource} over a time interval. The source computes the deposition rate from
   * the process thermodynamics (solid precipitation, salt from evaporating entrained water, etc.), so the deposit
   * amount is obtained as part of the process calculation.
   *
   * @param source deposit source that computes a rate from a process stream
   * @param hours duration in hours
   * @return the deposit mass added in kg
   */
  public double accumulate(DepositSource source, double hours) {
    if (source == null || hours <= 0.0) {
      return 0.0;
    }
    double rateKgPerHour = source.getDepositRate("kg/hr");
    double mass = Math.max(0.0, rateKgPerHour * hours);
    addDeposit(source.getMechanism(), mass);
    return mass;
  }

  /**
   * Remove a fraction of all deposits (models a compressor wash / cleaning).
   *
   * @param fraction fraction of deposit to remove (0 = none, 1 = fully clean)
   */
  public void removeFraction(double fraction) {
    double f = Math.max(0.0, Math.min(1.0, fraction));
    for (Map.Entry<DepositMechanism, Double> e : depositMass.entrySet()) {
      e.setValue(e.getValue() * (1.0 - f));
    }
  }

  /** Remove all deposits (fully clean). */
  public void clear() {
    depositMass.clear();
  }

  /**
   * Remove a mass of a single deposit mechanism (models dissolution by a wash fluid).
   *
   * @param mechanism deposit mechanism
   * @param massKg mass to remove in kg (clamped to the available deposit)
   * @return the mass actually removed in kg
   */
  public double removeDeposit(DepositMechanism mechanism, double massKg) {
    if (mechanism == null || massKg <= 0.0) {
      return 0.0;
    }
    double current = depositMass.containsKey(mechanism) ? depositMass.get(mechanism) : 0.0;
    double removed = Math.min(current, massKg);
    depositMass.put(mechanism, current - removed);
    return removed;
  }

  /**
   * Get the accumulated deposit mass for a single mechanism.
   *
   * @param mechanism deposit mechanism
   * @return deposit mass in kg (0 if none)
   */
  public double getDepositMass(DepositMechanism mechanism) {
    return depositMass.containsKey(mechanism) ? depositMass.get(mechanism) : 0.0;
  }

  /**
   * Get the total accumulated deposit mass over all mechanisms.
   *
   * @return total deposit mass in kg
   */
  public double getTotalDepositMass() {
    double sum = 0.0;
    for (double m : depositMass.values()) {
      sum += m;
    }
    return sum;
  }

  /**
   * Get the total deposit volume, summing mass/density over all mechanisms.
   *
   * @return total deposit volume in m3
   */
  public double getDepositVolume() {
    double vol = 0.0;
    for (Map.Entry<DepositMechanism, Double> e : depositMass.entrySet()) {
      vol += e.getValue() / e.getKey().getDensity();
    }
    return vol;
  }

  // ==========================================================================
  // Geometry
  // ==========================================================================

  /**
   * Size the foulable impeller geometry from the inlet volumetric flow (screening).
   *
   * <p>
   * The impeller eye area is estimated as {@code Q / eyeAxialVelocity}; the foulable (wetted) area of the first stage
   * is taken as six times the eye area (hub, shroud and blade surfaces) and the characteristic passage half-height as
   * {@code eyeDiameter / 12}.
   * </p>
   *
   * @param inletVolumeFlow inlet actual volumetric flow in m3/s
   * @param eyeAxialVelocity impeller-eye axial velocity in m/s (typical 40-50 m/s)
   */
  public void sizeImpellerFromInletFlow(double inletVolumeFlow, double eyeAxialVelocity) {
    if (inletVolumeFlow <= 0.0 || eyeAxialVelocity <= 0.0) {
      return;
    }
    double eyeArea = inletVolumeFlow / eyeAxialVelocity;
    double eyeDiameter = Math.sqrt(4.0 * eyeArea / Math.PI);
    this.wettedArea = 6.0 * eyeArea;
    this.passageHalfHeight = eyeDiameter / 12.0;
  }

  /**
   * Build a deposit model with foulable geometry derived from a compressor's inlet operating point.
   *
   * @param compressor compressor whose inlet stream is used to size the impeller
   * @return a new {@link CompressorDeposit} with geometry populated (empty deposit inventory)
   */
  public static CompressorDeposit fromCompressor(Compressor compressor) {
    CompressorDeposit dep = new CompressorDeposit();
    if (compressor != null && compressor.getInletStream() != null) {
      double q = compressor.getInletStream().getFlowRate("m3/sec");
      dep.sizeImpellerFromInletFlow(q, 45.0);
    }
    return dep;
  }

  /**
   * Get the foulable impeller / inlet area.
   *
   * @return wetted area in m2
   */
  public double getWettedArea() {
    return wettedArea;
  }

  /**
   * Set the foulable impeller / inlet area.
   *
   * @param wettedArea wetted area in m2
   */
  public void setWettedArea(double wettedArea) {
    this.wettedArea = wettedArea;
  }

  /**
   * Get the characteristic flow-passage half-height.
   *
   * @return passage half-height in m
   */
  public double getPassageHalfHeight() {
    return passageHalfHeight;
  }

  /**
   * Set the characteristic flow-passage half-height.
   *
   * @param passageHalfHeight passage half-height in m
   */
  public void setPassageHalfHeight(double passageHalfHeight) {
    this.passageHalfHeight = passageHalfHeight;
  }

  // ==========================================================================
  // Performance mapping
  // ==========================================================================

  /**
   * Deposit film thickness on the foulable area.
   *
   * @return film thickness in m (0 if geometry is not set)
   */
  public double getFilmThickness() {
    if (Double.isNaN(wettedArea) || wettedArea <= 0.0) {
      return 0.0;
    }
    return getDepositVolume() / wettedArea;
  }

  /**
   * Fractional flow-area blockage caused by the accumulated deposit.
   *
   * @return blockage fraction between 0 and {@code maxBlockageFraction}
   */
  public double getAreaBlockageFraction() {
    if (Double.isNaN(passageHalfHeight) || passageHalfHeight <= 0.0) {
      return 0.0;
    }
    return Math.min(getFilmThickness() / passageHalfHeight, maxBlockageFraction);
  }

  /**
   * Polytropic efficiency multiplier due to the accumulated deposit.
   *
   * @return efficiency multiplier between {@code minEfficiencyMultiplier} and 1.0
   */
  public double getEfficiencyMultiplier() {
    double b = getAreaBlockageFraction();
    return Math.max(1.0 - efficiencyBlockageCoefficient * b, minEfficiencyMultiplier);
  }

  /**
   * Polytropic head multiplier due to the accumulated deposit.
   *
   * @return head multiplier between {@code minHeadMultiplier} and 1.0
   */
  public double getHeadMultiplier() {
    double b = getAreaBlockageFraction();
    return Math.max(Math.pow(1.0 - b, headBlockageExponent), minHeadMultiplier);
  }

  /**
   * Invert the efficiency model: find the total deposit mass of a single mechanism that yields a target efficiency
   * multiplier, given the current geometry.
   *
   * <p>
   * Useful for calibration ("how much deposit is consistent with a measured efficiency drop?"). The current deposit
   * inventory is not modified.
   * </p>
   *
   * @param mechanism deposit mechanism to solve for
   * @param targetEfficiencyMultiplier target efficiency multiplier (0-1)
   * @return deposit mass in kg that yields the target multiplier (0 if geometry unset)
   */
  public double depositMassForEfficiencyMultiplier(DepositMechanism mechanism, double targetEfficiencyMultiplier) {
    if (mechanism == null || Double.isNaN(wettedArea) || Double.isNaN(passageHalfHeight) || wettedArea <= 0.0
        || passageHalfHeight <= 0.0) {
      return 0.0;
    }
    // Blockage that yields the target multiplier: 1 - kEff*b = target
    double targetBlockage = (1.0 - targetEfficiencyMultiplier) / efficiencyBlockageCoefficient;
    targetBlockage = Math.max(0.0, Math.min(targetBlockage, maxBlockageFraction));
    double thickness = targetBlockage * passageHalfHeight;
    double volume = thickness * wettedArea;
    return volume * mechanism.getDensity();
  }

  // ==========================================================================
  // Calibration coefficients
  // ==========================================================================

  /**
   * Get the efficiency-blockage coefficient.
   *
   * @return coefficient (default 1.6)
   */
  public double getEfficiencyBlockageCoefficient() {
    return efficiencyBlockageCoefficient;
  }

  /**
   * Set the efficiency-blockage coefficient used to calibrate the efficiency loss.
   *
   * @param coefficient efficiency-blockage coefficient (must be positive)
   */
  public void setEfficiencyBlockageCoefficient(double coefficient) {
    if (coefficient > 0.0) {
      this.efficiencyBlockageCoefficient = coefficient;
    }
  }

  /**
   * Get the head-blockage exponent.
   *
   * @return exponent (default 2.0)
   */
  public double getHeadBlockageExponent() {
    return headBlockageExponent;
  }

  /**
   * Set the head-blockage exponent used to calibrate the head loss.
   *
   * @param exponent head-blockage exponent (must be positive)
   */
  public void setHeadBlockageExponent(double exponent) {
    if (exponent > 0.0) {
      this.headBlockageExponent = exponent;
    }
  }

  /**
   * Get the maximum allowed flow-area blockage fraction.
   *
   * @return maximum blockage fraction
   */
  public double getMaxBlockageFraction() {
    return maxBlockageFraction;
  }

  /**
   * Set the maximum allowed flow-area blockage fraction.
   *
   * @param maxBlockageFraction maximum blockage fraction (0-1)
   */
  public void setMaxBlockageFraction(double maxBlockageFraction) {
    this.maxBlockageFraction = Math.max(0.0, Math.min(1.0, maxBlockageFraction));
  }

  // ==========================================================================
  // Reporting
  // ==========================================================================

  /**
   * Produce a compact JSON summary of the deposit inventory and its performance effect.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"deposits_kg\":{");
    boolean first = true;
    for (Map.Entry<DepositMechanism, Double> e : depositMass.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      sb.append("\"").append(e.getKey().name()).append("\":").append(e.getValue());
      first = false;
    }
    sb.append("},");
    sb.append("\"total_deposit_kg\":").append(getTotalDepositMass()).append(",");
    sb.append("\"deposit_volume_m3\":").append(getDepositVolume()).append(",");
    sb.append("\"film_thickness_mm\":").append(getFilmThickness() * 1000.0).append(",");
    sb.append("\"area_blockage_fraction\":").append(getAreaBlockageFraction()).append(",");
    sb.append("\"efficiency_multiplier\":").append(getEfficiencyMultiplier()).append(",");
    sb.append("\"head_multiplier\":").append(getHeadMultiplier());
    sb.append("}");
    return sb.toString();
  }
}
