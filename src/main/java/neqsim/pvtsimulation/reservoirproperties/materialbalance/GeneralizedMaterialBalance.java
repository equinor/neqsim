package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.blackoil.BlackOilPVTTable.Record;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;

/**
 * Generalized two-component reservoir material balance including vaporized oil.
 *
 * <p>
 * Implements the conservation basis of Walsh (1995), doi:10.2118/95-01-07. Unlike a conventional black-oil balance,
 * both solution gas Rs and vaporized oil Rv are retained. Observations can come from measured history or a forward tank
 * simulation. PVT must be independently calibrated and evaluated on the appropriate depletion/separator path. This
 * class neither tunes an EOS nor infers aquifer influx from pressure alone.
 * </p>
 *
 * <p>
 * All surface quantities use Sm3 on ONE fixed separator/stock-tank basis; formation volume factors use reservoir
 * m3/Sm3. Net cumulative production means produced minus injected, from the initial state. Aquifer influx is already in
 * reservoir m3 and must not be multiplied by Bw again. Injection is valid only when its component amounts are
 * represented by the same two surface pseudo-components and the PVT path remains representative. Strong composition
 * changes require full compositional simulation.
 * </p>
 *
 * <p>
 * Fits minimize unweighted reservoir-volume residuals, not pressure errors. Scaled QR least squares and an SVD
 * condition-number gate reject non-identifiable fits. Results are hydrocarbon volumes in place, not recoverable
 * reserves or confidence intervals. Near-critical Rs*Rv approaching one is explicitly rejected.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class GeneralizedMaterialBalance implements Serializable {
  private static final long serialVersionUID = 1L;
  /** Smallest permitted determinant of the surface-component split. */
  private static final double MIN_SPLIT_DETERMINANT = 1.0e-10;
  /** Largest permitted condition number after column scaling. */
  private static final double MAX_CONDITION_NUMBER = 1.0e8;
  /** Initial PVT on the common separator basis. */
  private final Record initialPvt;
  /** Rows {oil expansion, gas expansion, net withdrawal minus influx, net oil, net gas}. */
  private final List<double[]> observations = new ArrayList<double[]>();

  /**
   * Creates a history anchored to the original reservoir state.
   *
   * @param initialPvt initial PVT; pressure in bara, FVF in reservoir m3/Sm3
   */
  public GeneralizedMaterialBalance(Record initialPvt) {
    coefficients(initialPvt);
    this.initialPvt = initialPvt;
  }

  /**
   * Adds an aligned historical pressure/PVT/cumulative-production observation.
   *
   * <p>
   * The optional rock/connate-water support is specified as a dimensionless expansion fraction of INITIAL hydrocarbon
   * reservoir volume. In the linear small-compressibility approximation it is (cf + Swi*cw)/(1-Swi)*(pi-p), with
   * compressibilities in 1/bar. Use zero for a rigid tank with constant water volume. The fraction can be negative
   * under repressurization; influx can be negative for efflux.
   * </p>
   *
   * @param pvt PVT at measured average reservoir pressure on the calibrated path
   * @param netOilProduced cumulative stock-tank oil produced minus injected (Sm3)
   * @param netGasProduced cumulative standard gas produced minus injected (Sm3)
   * @param netWaterProduced cumulative standard water produced minus injected (Sm3)
   * @param waterInflux cumulative aquifer influx (reservoir m3), excluding injected water
   * @param expansionFraction rock/connate-water expansion per initial hydrocarbon volume
   */
  public void addObservation(Record pvt, double netOilProduced, double netGasProduced, double netWaterProduced,
      double waterInflux, double expansionFraction) {
    requireFinite(netOilProduced, "net oil production");
    requireFinite(netGasProduced, "net gas production");
    requireFinite(netWaterProduced, "net water production");
    requireFinite(waterInflux, "water influx");
    requireFinite(expansionFraction, "expansion fraction");
    double[] c = coefficients(pvt);
    double eo = c[0] + initialPvt.Rs * c[1] - initialPvt.Bo + initialPvt.Bo * expansionFraction;
    double eg = initialPvt.Rv * c[0] + c[1] - initialPvt.Bg + initialPvt.Bg * expansionFraction;
    double withdrawal = c[0] * netOilProduced + c[1] * netGasProduced + pvt.Bw * netWaterProduced - waterInflux;
    requireFinite(eo, "oil expansion");
    requireFinite(eg, "gas expansion");
    requireFinite(withdrawal, "withdrawal");
    observations.add(new double[] {eo, eg, withdrawal, netOilProduced, netGasProduced});
  }

  /**
   * Fits a reservoir with no initial free gas; dissolved gas is still included.
   *
   * @return original oil and gas in place and volume-balance residuals
   */
  public Result fitInitiallyOil() {
    return fit(0, 0.0);
  }

  /**
   * Fits a reservoir with no initial free oil; oil carried in gas is still included.
   *
   * @return original oil and gas in place and volume-balance residuals
   */
  public Result fitInitiallyGas() {
    return fit(1, 0.0);
  }

  /**
   * Fits with a specified initial gas-cap/oil-zone reservoir-volume ratio.
   *
   * @param gasCapRatio initial free gas reservoir volume divided by free oil reservoir volume
   * @return original oil and gas in place and volume-balance residuals
   */
  public Result fitKnownGasCap(double gasCapRatio) {
    requireFinite(gasCapRatio, "gas-cap ratio");
    if (gasCapRatio < 0.0) {
      throw new IllegalArgumentException("gas-cap ratio must be non-negative");
    }
    double ratio = gasCapRatio * initialPvt.Bo / initialPvt.Bg;
    requireFinite(ratio, "initial free-gas/free-oil surface ratio");
    return fit(0, ratio);
  }

  /**
   * Fits both initial free oil and free gas when their expansion histories are identifiable.
   *
   * @return original oil and gas in place and volume-balance residuals
   */
  public Result fitOilAndGas() {
    return fit(2, 0.0);
  }

  /**
   * Runs a column-scaled fit without normal equations or an inverse.
   *
   * @param mode 0 for oil/known gas cap, 1 for gas, 2 for both
   * @param gasPerOil initial free-gas/free-oil surface ratio in mode 0
   * @return fit result
   */
  private Result fit(int mode, double gasPerOil) {
    int columns = mode == 2 ? 2 : 1;
    int count = observations.size();
    if (count < Math.max(2, columns)) {
      throw new IllegalArgumentException("at least two non-initial observations are required");
    }
    double[][] matrix = new double[count][columns];
    double[] response = new double[count];
    double[] scale = new double[columns];
    double responseScale = 0.0;
    for (int i = 0; i < count; i++) {
      double[] row = observations.get(i);
      matrix[i][0] = mode == 1 ? row[1] : row[0] + gasPerOil * row[1];
      if (mode == 2) {
        matrix[i][1] = row[1];
      }
      for (int j = 0; j < columns; j++) {
        requireFinite(matrix[i][j], "expansion predictor");
        scale[j] = Math.max(scale[j], Math.abs(matrix[i][j]));
      }
      response[i] = row[2];
      responseScale = Math.max(responseScale, Math.abs(response[i]));
    }
    if (responseScale == 0.0) {
      throw new IllegalArgumentException("history has no net withdrawal signal");
    }
    for (int j = 0; j < columns; j++) {
      if (scale[j] == 0.0) {
        throw new IllegalArgumentException("history has no expansion signal for a fitted phase");
      }
      for (int i = 0; i < count; i++) {
        matrix[i][j] /= scale[j];
      }
    }
    for (int i = 0; i < count; i++) {
      response[i] /= responseScale;
    }
    EjmlLinearAlgebra algebra = new EjmlLinearAlgebra();
    double condition = algebra.conditionNumber(matrix);
    if (!Double.isFinite(condition) || condition > MAX_CONDITION_NUMBER) {
      throw new IllegalArgumentException("history cannot identify independent oil/gas volumes; scaled condition="
          + condition + "; constrain the gas cap or supply additional data");
    }
    double[] fitted = algebra.solveLeastSquares(matrix, response);
    double oil = mode == 1 ? 0.0 : fitted[0] * responseScale / scale[0];
    double gas = mode == 1 ? fitted[0] * responseScale / scale[0]
        : mode == 2 ? fitted[1] * responseScale / scale[1] : gasPerOil * oil;
    requireFinite(oil, "fitted oil volume");
    requireFinite(gas, "fitted gas volume");
    if (oil < 0.0 || gas < 0.0 || oil + gas == 0.0) {
      throw new IllegalArgumentException("unphysical fitted volumes; check history, PVT and drive assumptions");
    }
    double totalOil = oil + initialPvt.Rv * gas;
    double totalGas = gas + initialPvt.Rs * oil;
    requireFinite(totalOil, "total OOIP");
    requireFinite(totalGas, "total OGIP");
    double[] residuals = new double[count];
    for (int i = 0; i < count; i++) {
      double[] row = observations.get(i);
      if (row[3] > totalOil + 1.0e-10 * Math.max(1.0, totalOil)
          || row[4] > totalGas + 1.0e-10 * Math.max(1.0, totalGas)) {
        throw new IllegalArgumentException("net production exceeds fitted component inventory at observation " + i);
      }
      residuals[i] = row[2] - oil * row[0] - gas * row[1];
      requireFinite(residuals[i], "balance residual");
    }
    return new Result(oil, gas, initialPvt, residuals, condition);
  }

  /**
   * Maps total surface oil/gas components to their combined reservoir volume.
   *
   * @param pvt PVT on the common surface basis
   * @return effective oil and gas reservoir-volume coefficients
   */
  private static double[] coefficients(Record pvt) {
    if (pvt == null || !Double.isFinite(pvt.p) || pvt.p <= 0.0 || !Double.isFinite(pvt.Bo) || pvt.Bo <= 0.0
        || !Double.isFinite(pvt.Bg) || pvt.Bg <= 0.0 || !Double.isFinite(pvt.Bw) || pvt.Bw <= 0.0
        || !Double.isFinite(pvt.Rs) || pvt.Rs < 0.0 || !Double.isFinite(pvt.Rv) || pvt.Rv < 0.0) {
      throw new IllegalArgumentException("PVT needs positive finite pressure/FVF and non-negative finite Rs/Rv");
    }
    double determinant = 1.0 - pvt.Rs * pvt.Rv;
    if (determinant <= MIN_SPLIT_DETERMINANT) {
      throw new IllegalArgumentException("Rs*Rv must be below 1 by more than 1e-10; split is near critical or invalid");
    }
    double a = (pvt.Bo - pvt.Rs * pvt.Bg) / determinant;
    double b = (pvt.Bg - pvt.Rv * pvt.Bo) / determinant;
    requireFinite(a, "effective oil volume factor");
    requireFinite(b, "effective gas volume factor");
    return new double[] {a, b};
  }

  /**
   * Rejects missing/non-finite numeric input or intermediate results.
   *
   * @param value value to check
   * @param name quantity name for the error
   */
  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /**
   * Immutable fit result; total in-place volumes include dissolved gas and vaporized oil.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double freeOil;
    private final double freeGas;
    private final double ooip;
    private final double ogip;
    private final double gasCapRatio;
    private final double[] residuals;
    private final double conditionNumber;

    /**
     * Constructs the result on the initial separator basis.
     *
     * @param oil initial free-oil surface component (Sm3)
     * @param gas initial free-gas surface component (Sm3)
     * @param initial initial PVT
     * @param residuals volume residuals (reservoir m3)
     * @param condition scaled design-matrix condition number
     */
    private Result(double oil, double gas, Record initial, double[] residuals, double condition) {
      this.freeOil = oil;
      this.freeGas = gas;
      this.ooip = oil + initial.Rv * gas;
      this.ogip = gas + initial.Rs * oil;
      this.gasCapRatio = oil > 0.0 ? gas * initial.Bg / (oil * initial.Bo) : Double.POSITIVE_INFINITY;
      this.residuals = residuals.clone();
      this.conditionNumber = condition;
      requireFinite(ooip, "total OOIP");
      requireFinite(ogip, "total OGIP");
    }

    /**
     * Returns the original free-oil component.
     *
     * @return initial free-oil surface component (Sm3), excluding oil in the gas phase
     */
    public double getInitialFreeOil() {
      return freeOil;
    }

    /**
     * Returns the original free-gas component.
     *
     * @return initial free-gas surface component (Sm3), excluding dissolved gas
     */
    public double getInitialFreeGas() {
      return freeGas;
    }

    /**
     * Returns the total oil inventory on the common stock-tank basis.
     *
     * @return total original stock-tank oil in place (Sm3), including vaporized oil
     */
    public double getOoip() {
      return ooip;
    }

    /**
     * Returns the total gas inventory on the common standard basis.
     *
     * @return total original standard gas in place (Sm3), including dissolved gas
     */
    public double getOgip() {
      return ogip;
    }

    /**
     * Returns the fitted or specified initial gas-cap size.
     *
     * @return initial free-gas/free-oil reservoir-volume ratio, infinity for initially gas only
     */
    public double getGasCapRatio() {
      return gasCapRatio;
    }

    /**
     * Returns one residual per observation in insertion order.
     *
     * @return defensive copy of observed-minus-fitted balance residuals (reservoir m3)
     */
    public double[] getResiduals() {
      return residuals.clone();
    }

    /**
     * Returns the unweighted volume-balance misfit.
     *
     * @return root-mean-square volume residual (reservoir m3), not a pressure residual
     */
    public double getRmsResidual() {
      double norm = 0.0;
      for (double residual : residuals) {
        norm = Math.hypot(norm, residual);
      }
      return norm / Math.sqrt(residuals.length);
    }

    /**
     * Returns the numerical identifiability diagnostic.
     *
     * @return condition number of the column-scaled expansion predictors
     */
    public double getConditionNumber() {
      return conditionNumber;
    }
  }
}
