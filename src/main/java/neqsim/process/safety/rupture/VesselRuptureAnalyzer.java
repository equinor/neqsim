package neqsim.process.safety.rupture;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.util.fire.VesselRuptureCalculator;

/**
 * Thin-wall vessel rupture analyzer that tracks the von Mises wall stress against the temperature-derated allowable
 * tensile strength of the material throughout a fire or blowdown transient.
 *
 * <p>
 * At each time step the internal pressure and wall (metal) temperature give a von Mises equivalent stress (via
 * {@link VesselRuptureCalculator#vonMisesStress(double, double, double)}) which is compared with the allowable rupture
 * stress obtained by derating the ambient tensile strength with the {@link MaterialStrengthCurve}. Rupture is predicted
 * at the first crossover where the stress reaches the allowable strength; the crossover time is found by linear
 * interpolation of the margin between the bracketing steps. This is the time-marching equivalent of the Birk/Moodie
 * fire-engulfment validation cases and complements the strain-rate pipe rupture variant already in this package.
 * </p>
 *
 * <p>
 * <b>References:</b> Birk, A.M. and Cunningham, M.H. (1996), J. Loss Prev. Process Ind. 9(3); Moodie et al. (1988), J.
 * Hazard. Mater. 20; API 521 §4.3.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class VesselRuptureAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double innerRadiusM;
  private final double wallThicknessM;
  private final MaterialStrengthCurve material;
  private double tensileStrengthFactor = 1.0;

  /**
   * Creates a vessel rupture analyzer.
   *
   * @param innerRadiusM vessel inner radius in m; must be positive
   * @param wallThicknessM wall thickness in m; must be positive
   * @param material temperature-dependent material strength curve; must not be null
   * @throws IllegalArgumentException if any argument is invalid
   */
  public VesselRuptureAnalyzer(double innerRadiusM, double wallThicknessM, MaterialStrengthCurve material) {
    if (innerRadiusM <= 0.0 || wallThicknessM <= 0.0) {
      throw new IllegalArgumentException("innerRadiusM and wallThicknessM must be positive");
    }
    if (material == null) {
      throw new IllegalArgumentException("material must not be null");
    }
    this.innerRadiusM = innerRadiusM;
    this.wallThicknessM = wallThicknessM;
    this.material = material;
  }

  /**
   * Sets the tensile-strength utilization factor applied when computing the allowable rupture stress.
   *
   * @param tensileStrengthFactor fraction of the derated tensile strength taken as allowable (0 to 1]; must be positive
   * and at most one
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if {@code tensileStrengthFactor} is out of range
   */
  public VesselRuptureAnalyzer setTensileStrengthFactor(double tensileStrengthFactor) {
    if (tensileStrengthFactor <= 0.0 || tensileStrengthFactor > 1.0) {
      throw new IllegalArgumentException("tensileStrengthFactor must be in (0, 1]");
    }
    this.tensileStrengthFactor = tensileStrengthFactor;
    return this;
  }

  /**
   * Computes the von Mises wall stress for a single state.
   *
   * @param internalPressurePa internal pressure in Pa absolute; must be non-negative
   * @return von Mises stress in Pa
   */
  public double wallStressPa(double internalPressurePa) {
    return VesselRuptureCalculator.vonMisesStress(internalPressurePa, innerRadiusM, wallThicknessM);
  }

  /**
   * Computes the allowable rupture stress for a single metal temperature.
   *
   * @param metalTemperatureK wall (metal) temperature in K; must be positive
   * @return allowable rupture stress in Pa
   */
  public double allowableStressPa(double metalTemperatureK) {
    return material.allowableRuptureStressAt(metalTemperatureK, tensileStrengthFactor);
  }

  /**
   * Analyzes a transient history and determines whether and when the wall ruptures.
   *
   * @param timeS time stamps in s; strictly increasing, at least one element
   * @param internalPressurePa internal pressure history in Pa absolute, same length as {@code timeS}
   * @param metalTemperatureK wall (metal) temperature history in K, same length as {@code timeS}
   * @return a {@link VesselRuptureResult} with the per-step stress/strength history and the rupture verdict
   * @throws IllegalArgumentException if the arrays are null, empty or of differing length
   */
  public VesselRuptureResult analyze(double[] timeS, double[] internalPressurePa, double[] metalTemperatureK) {
    if (timeS == null || internalPressurePa == null || metalTemperatureK == null) {
      throw new IllegalArgumentException("input arrays must not be null");
    }
    if (timeS.length == 0 || timeS.length != internalPressurePa.length || timeS.length != metalTemperatureK.length) {
      throw new IllegalArgumentException("input arrays must be non-empty and of equal length");
    }
    VesselRuptureResult result = new VesselRuptureResult();
    double previousMargin = Double.NaN;
    double previousTime = Double.NaN;
    for (int i = 0; i < timeS.length; i++) {
      double stress = wallStressPa(internalPressurePa[i]);
      double allowable = allowableStressPa(metalTemperatureK[i]);
      double margin = allowable - stress;
      result.timeS.add(timeS[i]);
      result.vonMisesStressPa.add(stress);
      result.allowableStressPa.add(allowable);
      result.marginPa.add(margin);
      if (margin < result.minMarginPa) {
        result.minMarginPa = margin;
        result.minMarginTimeS = timeS[i];
      }
      if (!result.ruptured && margin <= 0.0) {
        result.ruptured = true;
        if (!Double.isNaN(previousMargin) && previousMargin > 0.0 && margin != previousMargin) {
          double frac = previousMargin / (previousMargin - margin);
          result.ruptureTimeS = previousTime + frac * (timeS[i] - previousTime);
        } else {
          result.ruptureTimeS = timeS[i];
        }
        result.ruptureStressPa = stress;
        result.allowableAtRupturePa = allowable;
        result.ruptureMetalTemperatureK = metalTemperatureK[i];
      }
      previousMargin = margin;
      previousTime = timeS[i];
    }
    return result;
  }

  /**
   * Gets the material strength curve used by this analyzer.
   *
   * @return the material strength curve
   */
  public MaterialStrengthCurve getMaterial() {
    return material;
  }

  /**
   * Immutable-style container of vessel rupture analysis output.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class VesselRuptureResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Time stamps of the analyzed history in s. */
    public final List<Double> timeS = new ArrayList<Double>();
    /** Von Mises wall stress at each step in Pa. */
    public final List<Double> vonMisesStressPa = new ArrayList<Double>();
    /** Allowable rupture stress at each step in Pa. */
    public final List<Double> allowableStressPa = new ArrayList<Double>();
    /** Margin (allowable minus stress) at each step in Pa. */
    public final List<Double> marginPa = new ArrayList<Double>();
    /** Whether rupture was predicted during the history. */
    public boolean ruptured = false;
    /** Interpolated rupture time in s (NaN if no rupture). */
    public double ruptureTimeS = Double.NaN;
    /** Von Mises stress at the rupture step in Pa (NaN if no rupture). */
    public double ruptureStressPa = Double.NaN;
    /** Allowable stress at the rupture step in Pa (NaN if no rupture). */
    public double allowableAtRupturePa = Double.NaN;
    /** Wall (metal) temperature at the rupture step in K (NaN if no rupture). */
    public double ruptureMetalTemperatureK = Double.NaN;
    /** Minimum stress margin reached over the history in Pa. */
    public double minMarginPa = Double.POSITIVE_INFINITY;
    /** Time at which the minimum margin occurred in s. */
    public double minMarginTimeS = Double.NaN;
  }
}
