package neqsim.process.equipment.expander;

import java.io.Serializable;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * ExpanderChartKhader is a composition-aware, two-dimensional radial-inflow expander performance map modelled on the
 * Khader (2015) dimensionless-correction approach already used for centrifugal compressors in
 * {@link neqsim.process.equipment.compressor.CompressorChartKhader2015}.
 *
 * <p>
 * Instead of representing expander performance with a single one-dimensional efficiency parabola in the velocity ratio
 * U/C, this class stores a full grid of map data:
 * </p>
 *
 * <ul>
 * <li>One curve for each inlet-guide-vane (IGV) position.</li>
 * <li>Each curve gives the isentropic efficiency and the isentropic stage head drop as a function of the velocity ratio
 * U/C (or equivalently the corrected specific speed).</li>
 * </ul>
 *
 * <p>
 * <b>Composition awareness.</b> The map is digitised once on a reference fluid. The stage head drop is normalised by
 * the square of the reference fluid sound speed to give a dimensionless head coefficient. When the map is evaluated for
 * an arbitrary process fluid the head coefficient is scaled back by the actual fluid sound speed squared:
 * </p>
 *
 * <p>
 * $$ \\psi = \\frac{\\Delta h_s}{c_s^2}, \\qquad \\Delta h_s = \\psi \\, c_{s,\\text{actual}}^2 $$
 *
 * <p>
 * Because the velocity ratio U/C is already dimensionless and Mach-number similar, the efficiency map is reused
 * directly for any fluid. This lets the expander side carry the same fidelity as the Khader compressor map and removes
 * the low-load solver instability caused by the single global parabola.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ExpanderChartKhader implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(ExpanderChartKhader.class);

  /** Reference fluid used to normalise the digitised map. */
  private SystemInterface referenceFluid = null;

  /** Sound speed of the reference fluid in m/s. */
  private double referenceSoundSpeed = Double.NaN;

  /** Impeller outer diameter in m. */
  private double impellerOuterDiameter = 0.424;

  /** IGV positions (fraction of maximum area, 0..1) for each stored curve. */
  private double[] igvPositions = null;

  /** Velocity ratio U/C grid for each IGV position. */
  private double[][] velocityRatio = null;

  /** Isentropic efficiency grid for each IGV position. */
  private double[][] efficiency = null;

  /** Dimensionless head coefficient grid (head/cs^2) for each IGV position. */
  private double[][] headCoefficient = null;

  /** Flag indicating whether map data has been loaded. */
  private boolean mapDefined = false;

  /**
   * Default constructor.
   */
  public ExpanderChartKhader() {
  }

  /**
   * Constructs an ExpanderChartKhader with a reference fluid and impeller diameter.
   *
   * @param referenceFluid the fluid the OEM map was digitised on (may be {@code null} to skip composition correction
   * and use the head directly)
   * @param impellerDiameter the impeller outer diameter in m
   */
  public ExpanderChartKhader(SystemInterface referenceFluid, double impellerDiameter) {
    this.referenceFluid = referenceFluid;
    this.impellerOuterDiameter = impellerDiameter;
  }

  /**
   * Set the digitised expander performance map.
   *
   * <p>
   * The map is supplied as one curve per IGV position. Each curve is an array of velocity ratios with matching
   * isentropic efficiency and isentropic stage head drop (kJ/kg). All inner arrays for a given IGV position must share
   * the same length, but different IGV positions may use a different number of points.
   * </p>
   *
   * @param igvPositions array of IGV positions (fraction of maximum area, 0..1), strictly increasing
   * @param uc 2-D array of velocity ratios U/C, one row per IGV position
   * @param eta 2-D array of isentropic efficiencies (0..1), one row per IGV position
   * @param headDropKjPerKg 2-D array of isentropic stage head drops in kJ/kg, one row per IGV position
   * @throws IllegalArgumentException if the array shapes are inconsistent
   */
  public void setCurves(double[] igvPositions, double[][] uc, double[][] eta, double[][] headDropKjPerKg) {
    if (igvPositions == null || uc == null || eta == null || headDropKjPerKg == null) {
      throw new IllegalArgumentException("ExpanderChartKhader map arrays must not be null");
    }
    if (igvPositions.length != uc.length || uc.length != eta.length || eta.length != headDropKjPerKg.length) {
      throw new IllegalArgumentException(
          "ExpanderChartKhader: igvPositions, uc, eta and headDrop must have matching outer length");
    }
    int nIgv = igvPositions.length;
    this.igvPositions = Arrays.copyOf(igvPositions, nIgv);
    this.velocityRatio = new double[nIgv][];
    this.efficiency = new double[nIgv][];
    this.headCoefficient = new double[nIgv][];

    double csRef = computeReferenceSoundSpeed();

    for (int i = 0; i < nIgv; i++) {
      int m = uc[i].length;
      if (eta[i].length != m || headDropKjPerKg[i].length != m) {
        throw new IllegalArgumentException(
            "ExpanderChartKhader: inner arrays for IGV index " + i + " have inconsistent length");
      }
      // sort each curve by velocity ratio so interpolation is monotone
      double[][] rows = new double[m][3];
      for (int j = 0; j < m; j++) {
        rows[j][0] = uc[i][j];
        rows[j][1] = eta[i][j];
        rows[j][2] = headDropKjPerKg[i][j] * 1000.0 / (csRef * csRef); // J/kg / (m/s)^2
      }
      Arrays.sort(rows, new java.util.Comparator<double[]>() {
        @Override
        public int compare(double[] a, double[] b) {
          return Double.compare(a[0], b[0]);
        }
      });
      this.velocityRatio[i] = new double[m];
      this.efficiency[i] = new double[m];
      this.headCoefficient[i] = new double[m];
      for (int j = 0; j < m; j++) {
        this.velocityRatio[i][j] = rows[j][0];
        this.efficiency[i][j] = rows[j][1];
        this.headCoefficient[i][j] = rows[j][2];
      }
    }
    this.mapDefined = true;
  }

  /**
   * Compute (and cache) the reference fluid sound speed used to normalise the head coefficient.
   *
   * @return the reference sound speed in m/s (1.0 if no reference fluid is set, leaving the head effectively
   * un-normalised)
   */
  private double computeReferenceSoundSpeed() {
    if (referenceFluid == null) {
      referenceSoundSpeed = 1.0;
      return 1.0;
    }
    try {
      SystemInterface clone = referenceFluid.clone();
      ThermodynamicOperations ops = new ThermodynamicOperations(clone);
      ops.TPflash();
      clone.initThermoProperties();
      referenceSoundSpeed = clone.getPhase(0).getSoundSpeed();
    } catch (Exception ex) {
      logger.warn("ExpanderChartKhader could not compute reference sound speed, using 1.0", ex);
      referenceSoundSpeed = 1.0;
    }
    if (!(referenceSoundSpeed > 0.0)) {
      referenceSoundSpeed = 1.0;
    }
    return referenceSoundSpeed;
  }

  /**
   * Returns whether a performance map has been loaded.
   *
   * @return {@code true} if {@link #setCurves(double[], double[][], double[][], double[][])} has been called with valid
   * data
   */
  public boolean isMapDefined() {
    return mapDefined;
  }

  /**
   * Get the isentropic efficiency at a given velocity ratio and IGV position using bilinear interpolation over the map.
   *
   * @param uc velocity ratio U/C
   * @param igv IGV position (fraction of maximum area, 0..1)
   * @return the interpolated isentropic efficiency (0..1)
   */
  public double getEfficiency(double uc, double igv) {
    return interpolate(uc, igv, efficiency);
  }

  /**
   * Get the isentropic stage head drop at a given velocity ratio and IGV position for the supplied process fluid. The
   * dimensionless head coefficient is scaled by the actual fluid sound speed squared to make the result composition
   * aware.
   *
   * @param uc velocity ratio U/C
   * @param igv IGV position (fraction of maximum area, 0..1)
   * @param processFluid the actual process fluid (may be {@code null} to use the reference sound speed)
   * @return the isentropic stage head drop in kJ/kg
   */
  public double getStageHeadDrop(double uc, double igv, SystemInterface processFluid) {
    double coeff = interpolate(uc, igv, headCoefficient);
    double cs = referenceSoundSpeed;
    if (processFluid != null) {
      try {
        double localCs = processFluid.getSoundSpeed();
        if (localCs > 0.0) {
          cs = localCs;
        }
      } catch (Exception ex) {
        logger.debug("ExpanderChartKhader using reference sound speed for head scaling", ex);
      }
    }
    return coeff * cs * cs / 1000.0; // back to kJ/kg
  }

  /**
   * Find the velocity ratio that maximises efficiency at a given IGV position. Useful for control targets and
   * best-efficiency-point tracking.
   *
   * @param igv IGV position (fraction of maximum area, 0..1)
   * @return the velocity ratio U/C at peak efficiency for the nearest stored IGV curve
   */
  public double getOptimumVelocityRatio(double igv) {
    if (!mapDefined) {
      return 0.7;
    }
    int idx = nearestIgvIndex(igv);
    double[] uc = velocityRatio[idx];
    double[] eta = efficiency[idx];
    int best = 0;
    for (int j = 1; j < eta.length; j++) {
      if (eta[j] > eta[best]) {
        best = j;
      }
    }
    return uc[best];
  }

  /**
   * Bilinear interpolation of a stored grid quantity over velocity ratio and IGV position. Linear blending is used
   * between the two bracketing IGV curves and clamped at the edges.
   *
   * @param uc velocity ratio U/C
   * @param igv IGV position (fraction of maximum area)
   * @param grid the per-IGV value grid to interpolate (efficiency or head coefficient)
   * @return the interpolated value
   */
  private double interpolate(double uc, double igv, double[][] grid) {
    if (!mapDefined) {
      // fall back to the historical default parabola so callers always get a value
      return -3.56 * (uc - 1.0) * (uc - 1.0) + 1.0;
    }
    int nIgv = igvPositions.length;
    if (nIgv == 1) {
      return interpolateCurve(uc, velocityRatio[0], grid[0]);
    }
    // clamp below first IGV
    if (igv <= igvPositions[0]) {
      return interpolateCurve(uc, velocityRatio[0], grid[0]);
    }
    // clamp above last IGV
    if (igv >= igvPositions[nIgv - 1]) {
      return interpolateCurve(uc, velocityRatio[nIgv - 1], grid[nIgv - 1]);
    }
    // find bracketing IGV curves
    int lo = 0;
    for (int i = 0; i < nIgv - 1; i++) {
      if (igv >= igvPositions[i] && igv <= igvPositions[i + 1]) {
        lo = i;
        break;
      }
    }
    double v0 = interpolateCurve(uc, velocityRatio[lo], grid[lo]);
    double v1 = interpolateCurve(uc, velocityRatio[lo + 1], grid[lo + 1]);
    double t = (igv - igvPositions[lo]) / (igvPositions[lo + 1] - igvPositions[lo]);
    return v0 + t * (v1 - v0);
  }

  /**
   * Piecewise-linear interpolation of a single curve in velocity ratio, with linear extrapolation outside the data
   * range.
   *
   * @param uc velocity ratio to evaluate
   * @param x sorted velocity-ratio abscissa array
   * @param y matching ordinate array
   * @return the interpolated value
   */
  private double interpolateCurve(double uc, double[] x, double[] y) {
    int n = x.length;
    if (n == 1) {
      return y[0];
    }
    if (uc <= x[0]) {
      double slope = (y[1] - y[0]) / (x[1] - x[0]);
      return y[0] + slope * (uc - x[0]);
    }
    if (uc >= x[n - 1]) {
      double slope = (y[n - 1] - y[n - 2]) / (x[n - 1] - x[n - 2]);
      return y[n - 1] + slope * (uc - x[n - 1]);
    }
    for (int i = 0; i < n - 1; i++) {
      if (uc >= x[i] && uc <= x[i + 1]) {
        double t = (uc - x[i]) / (x[i + 1] - x[i]);
        return y[i] + t * (y[i + 1] - y[i]);
      }
    }
    return y[n - 1];
  }

  /**
   * Find the index of the stored IGV curve closest to the requested IGV position.
   *
   * @param igv IGV position (fraction of maximum area)
   * @return the index of the nearest stored IGV curve
   */
  private int nearestIgvIndex(double igv) {
    int idx = 0;
    double best = Math.abs(igvPositions[0] - igv);
    for (int i = 1; i < igvPositions.length; i++) {
      double d = Math.abs(igvPositions[i] - igv);
      if (d < best) {
        best = d;
        idx = i;
      }
    }
    return idx;
  }

  /**
   * Get the reference fluid sound speed used for head normalisation.
   *
   * @return the reference sound speed in m/s
   */
  public double getReferenceSoundSpeed() {
    return referenceSoundSpeed;
  }

  /**
   * Get the impeller outer diameter.
   *
   * @return the impeller outer diameter in m
   */
  public double getImpellerOuterDiameter() {
    return impellerOuterDiameter;
  }

  /**
   * Set the impeller outer diameter.
   *
   * @param impellerOuterDiameter the impeller outer diameter in m
   */
  public void setImpellerOuterDiameter(double impellerOuterDiameter) {
    this.impellerOuterDiameter = impellerOuterDiameter;
  }

  /**
   * Set the reference fluid used to normalise the digitised map.
   *
   * @param referenceFluid the reference fluid
   */
  public void setReferenceFluid(SystemInterface referenceFluid) {
    this.referenceFluid = referenceFluid;
  }

  /**
   * Get the IGV positions of the stored curves.
   *
   * @return a defensive copy of the IGV position array, or {@code null} if no map is defined
   */
  public double[] getIgvPositions() {
    return igvPositions == null ? null : Arrays.copyOf(igvPositions, igvPositions.length);
  }
}
