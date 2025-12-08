package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.equipment.pipeline.twophasepipe.ThermodynamicCoupling.ThermoProperties;

/**
 * Pre-computed flash property table for fast interpolation.
 *
 * <p>
 * Stores thermodynamic properties on a P-T grid for rapid lookup during transient simulations.
 * Avoids expensive flash calculations at each time step by using bilinear interpolation.
 * </p>
 *
 * <h2>Usage</h2>
 * 
 * <pre>
 * FlashTable table = new FlashTable();
 * table.build(referenceFluid, 1e5, 100e5, 50, 250.0, 400.0, 40);
 * thermoCoupling.setFlashTable(table);
 * </pre>
 *
 * <h2>Accuracy Considerations</h2>
 * <ul>
 * <li>Grid resolution affects interpolation accuracy near phase boundaries</li>
 * <li>Use finer grids near critical point or phase envelope</li>
 * <li>Consider adaptive refinement for high-accuracy applications</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FlashTable implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Pressure grid points (Pa). */
  private double[] pressures;

  /** Temperature grid points (K). */
  private double[] temperatures;

  /** Number of pressure points. */
  private int nP;

  /** Number of temperature points. */
  private int nT;

  /** Minimum pressure (Pa). */
  private double pMin;

  /** Maximum pressure (Pa). */
  private double pMax;

  /** Minimum temperature (K). */
  private double tMin;

  /** Maximum temperature (K). */
  private double tMax;

  /** Pressure step size. */
  private double dP;

  /** Temperature step size. */
  private double dT;

  // Property tables [pressure index][temperature index]
  private double[][] gasDensity;
  private double[][] liquidDensity;
  private double[][] gasViscosity;
  private double[][] liquidViscosity;
  private double[][] gasEnthalpy;
  private double[][] liquidEnthalpy;
  private double[][] gasSoundSpeed;
  private double[][] liquidSoundSpeed;
  private double[][] surfaceTension;
  private double[][] gasVaporFraction;
  private double[][] gasCp;
  private double[][] liquidCp;
  private double[][] gasCompressibility;
  private double[][] liquidCompressibility;

  /** Flag indicating table is built. */
  private boolean isBuilt = false;

  /**
   * Default constructor.
   */
  public FlashTable() {}

  /**
   * Build the flash table from a reference fluid.
   *
   * @param referenceFluid Fluid system to use for flash calculations
   * @param pressureMin Minimum pressure (Pa)
   * @param pressureMax Maximum pressure (Pa)
   * @param numPressurePoints Number of pressure grid points
   * @param temperatureMin Minimum temperature (K)
   * @param temperatureMax Maximum temperature (K)
   * @param numTemperaturePoints Number of temperature grid points
   */
  public void build(SystemInterface referenceFluid, double pressureMin, double pressureMax,
      int numPressurePoints, double temperatureMin, double temperatureMax,
      int numTemperaturePoints) {

    this.pMin = pressureMin;
    this.pMax = pressureMax;
    this.tMin = temperatureMin;
    this.tMax = temperatureMax;
    this.nP = numPressurePoints;
    this.nT = numTemperaturePoints;

    // Calculate grid spacing
    this.dP = (pMax - pMin) / (nP - 1);
    this.dT = (tMax - tMin) / (nT - 1);

    // Initialize arrays
    pressures = new double[nP];
    temperatures = new double[nT];

    for (int i = 0; i < nP; i++) {
      pressures[i] = pMin + i * dP;
    }
    for (int j = 0; j < nT; j++) {
      temperatures[j] = tMin + j * dT;
    }

    // Initialize property tables
    gasDensity = new double[nP][nT];
    liquidDensity = new double[nP][nT];
    gasViscosity = new double[nP][nT];
    liquidViscosity = new double[nP][nT];
    gasEnthalpy = new double[nP][nT];
    liquidEnthalpy = new double[nP][nT];
    gasSoundSpeed = new double[nP][nT];
    liquidSoundSpeed = new double[nP][nT];
    surfaceTension = new double[nP][nT];
    gasVaporFraction = new double[nP][nT];
    gasCp = new double[nP][nT];
    liquidCp = new double[nP][nT];
    gasCompressibility = new double[nP][nT];
    liquidCompressibility = new double[nP][nT];

    // Create thermodynamic coupling for flash calculations
    ThermodynamicCoupling coupling = new ThermodynamicCoupling(referenceFluid);

    // Fill the table
    for (int i = 0; i < nP; i++) {
      for (int j = 0; j < nT; j++) {
        ThermoProperties props = coupling.flashPT(pressures[i], temperatures[j]);

        gasDensity[i][j] = props.gasDensity;
        liquidDensity[i][j] = props.liquidDensity;
        gasViscosity[i][j] = props.gasViscosity;
        liquidViscosity[i][j] = props.liquidViscosity;
        gasEnthalpy[i][j] = props.gasEnthalpy;
        liquidEnthalpy[i][j] = props.liquidEnthalpy;
        gasSoundSpeed[i][j] = props.gasSoundSpeed;
        liquidSoundSpeed[i][j] = props.liquidSoundSpeed;
        surfaceTension[i][j] = props.surfaceTension;
        gasVaporFraction[i][j] = props.gasVaporFraction;
        gasCp[i][j] = props.gasCp;
        liquidCp[i][j] = props.liquidCp;
        gasCompressibility[i][j] = props.gasCompressibility;
        liquidCompressibility[i][j] = props.liquidCompressibility;
      }
    }

    isBuilt = true;
  }

  /**
   * Interpolate properties at given P-T conditions.
   *
   * @param pressure Pressure (Pa)
   * @param temperature Temperature (K)
   * @return Interpolated thermodynamic properties
   */
  public ThermoProperties interpolate(double pressure, double temperature) {
    ThermoProperties props = new ThermoProperties();

    if (!isBuilt) {
      props.converged = false;
      props.errorMessage = "Flash table not built";
      return props;
    }

    // Clamp to valid range
    double p = Math.max(pMin, Math.min(pMax, pressure));
    double t = Math.max(tMin, Math.min(tMax, temperature));

    // Find grid indices
    int iP = (int) ((p - pMin) / dP);
    int iT = (int) ((t - tMin) / dT);

    // Clamp indices
    iP = Math.max(0, Math.min(nP - 2, iP));
    iT = Math.max(0, Math.min(nT - 2, iT));

    // Calculate interpolation weights
    double wP = (p - pressures[iP]) / dP;
    double wT = (t - temperatures[iT]) / dT;

    // Clamp weights
    wP = Math.max(0.0, Math.min(1.0, wP));
    wT = Math.max(0.0, Math.min(1.0, wT));

    // Bilinear interpolation
    props.gasDensity = bilinearInterp(gasDensity, iP, iT, wP, wT);
    props.liquidDensity = bilinearInterp(liquidDensity, iP, iT, wP, wT);
    props.gasViscosity = bilinearInterp(gasViscosity, iP, iT, wP, wT);
    props.liquidViscosity = bilinearInterp(liquidViscosity, iP, iT, wP, wT);
    props.gasEnthalpy = bilinearInterp(gasEnthalpy, iP, iT, wP, wT);
    props.liquidEnthalpy = bilinearInterp(liquidEnthalpy, iP, iT, wP, wT);
    props.gasSoundSpeed = bilinearInterp(gasSoundSpeed, iP, iT, wP, wT);
    props.liquidSoundSpeed = bilinearInterp(liquidSoundSpeed, iP, iT, wP, wT);
    props.surfaceTension = bilinearInterp(surfaceTension, iP, iT, wP, wT);
    props.gasVaporFraction = bilinearInterp(gasVaporFraction, iP, iT, wP, wT);
    props.gasCp = bilinearInterp(gasCp, iP, iT, wP, wT);
    props.liquidCp = bilinearInterp(liquidCp, iP, iT, wP, wT);
    props.gasCompressibility = bilinearInterp(gasCompressibility, iP, iT, wP, wT);
    props.liquidCompressibility = bilinearInterp(liquidCompressibility, iP, iT, wP, wT);

    props.liquidFraction = 1.0 - props.gasVaporFraction;
    props.converged = true;

    return props;
  }

  /**
   * Bilinear interpolation helper.
   */
  private double bilinearInterp(double[][] table, int iP, int iT, double wP, double wT) {
    double v00 = table[iP][iT];
    double v10 = table[iP + 1][iT];
    double v01 = table[iP][iT + 1];
    double v11 = table[iP + 1][iT + 1];

    double v0 = v00 * (1 - wP) + v10 * wP;
    double v1 = v01 * (1 - wP) + v11 * wP;

    return v0 * (1 - wT) + v1 * wT;
  }

  /**
   * Get a specific property value using bilinear interpolation.
   *
   * @param property Property name
   * @param pressure Pressure (Pa)
   * @param temperature Temperature (K)
   * @return Interpolated property value
   */
  public double getProperty(String property, double pressure, double temperature) {
    ThermoProperties props = interpolate(pressure, temperature);

    switch (property.toLowerCase()) {
      case "gasdensity":
        return props.gasDensity;
      case "liquiddensity":
        return props.liquidDensity;
      case "gasviscosity":
        return props.gasViscosity;
      case "liquidviscosity":
        return props.liquidViscosity;
      case "gasenthalpy":
        return props.gasEnthalpy;
      case "liquidenthalpy":
        return props.liquidEnthalpy;
      case "gassoundspeed":
        return props.gasSoundSpeed;
      case "liquidsoundspeed":
        return props.liquidSoundSpeed;
      case "surfacetension":
        return props.surfaceTension;
      case "gasvaporfraction":
        return props.gasVaporFraction;
      default:
        return Double.NaN;
    }
  }

  /**
   * Check if table is built.
   *
   * @return True if table has been built
   */
  public boolean isBuilt() {
    return isBuilt;
  }

  /**
   * Get minimum pressure of table range.
   *
   * @return Minimum pressure (Pa)
   */
  public double getMinPressure() {
    return pMin;
  }

  /**
   * Get maximum pressure of table range.
   *
   * @return Maximum pressure (Pa)
   */
  public double getMaxPressure() {
    return pMax;
  }

  /**
   * Get minimum temperature of table range.
   *
   * @return Minimum temperature (K)
   */
  public double getMinTemperature() {
    return tMin;
  }

  /**
   * Get maximum temperature of table range.
   *
   * @return Maximum temperature (K)
   */
  public double getMaxTemperature() {
    return tMax;
  }

  /**
   * Get number of pressure points.
   *
   * @return Number of pressure grid points
   */
  public int getNumPressurePoints() {
    return nP;
  }

  /**
   * Get number of temperature points.
   *
   * @return Number of temperature grid points
   */
  public int getNumTemperaturePoints() {
    return nT;
  }

  /**
   * Get pressure grid points.
   *
   * @return Array of pressure values (Pa)
   */
  public double[] getPressures() {
    return pressures != null ? pressures.clone() : null;
  }

  /**
   * Get temperature grid points.
   *
   * @return Array of temperature values (K)
   */
  public double[] getTemperatures() {
    return temperatures != null ? temperatures.clone() : null;
  }

  /**
   * Get total number of grid points.
   *
   * @return Total grid points (nP * nT)
   */
  public int getTotalGridPoints() {
    return nP * nT;
  }

  /**
   * Estimate memory usage of the table in bytes.
   *
   * @return Approximate memory usage in bytes
   */
  public long estimateMemoryUsage() {
    // 14 property tables * nP * nT * 8 bytes per double
    return 14L * nP * nT * 8;
  }

  /**
   * Clear the table to free memory.
   */
  public void clear() {
    pressures = null;
    temperatures = null;
    gasDensity = null;
    liquidDensity = null;
    gasViscosity = null;
    liquidViscosity = null;
    gasEnthalpy = null;
    liquidEnthalpy = null;
    gasSoundSpeed = null;
    liquidSoundSpeed = null;
    surfaceTension = null;
    gasVaporFraction = null;
    gasCp = null;
    liquidCp = null;
    gasCompressibility = null;
    liquidCompressibility = null;
    isBuilt = false;
  }
}
