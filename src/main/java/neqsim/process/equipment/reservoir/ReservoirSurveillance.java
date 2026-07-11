package neqsim.process.equipment.reservoir;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GasMaterialBalance;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.OilMaterialBalance;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.VanEverdingenHurstAquifer;
import neqsim.pvtsimulation.util.DeclineCurveAnalysis;
import neqsim.pvtsimulation.util.ZFactorCorrelations;

/**
 * Reservoir-surveillance analyser for a {@link SimpleReservoir}.
 *
 * <p>
 * Ties the analytical ("inverse") material-balance, decline-curve and aquifer-influx engines to the pressure and
 * cumulative-production history recorded by a forward {@link SimpleReservoir} tank model. Together the forward physics
 * simulation and the analytical surveillance form a complete reservoir production toolkit: the tank model generates the
 * pressure/production history and the surveillance analyser regresses reserves (OGIP/OOIP), diagnoses drive mechanism,
 * quantifies aquifer support and fits decline curves for forecasting.
 * </p>
 *
 * <p>
 * The gas material balance and aquifer calculations operate directly on the recorded history. The oil material balance
 * additionally requires tabulated black-oil PVT (formation volume factors and solution gas-oil ratios) aligned with the
 * history, which the caller supplies.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ReservoirSurveillance implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** The reservoir whose history is analysed. */
  private final SimpleReservoir reservoir;

  /** Aquifer permeability (m^2). */
  private double aquiferPermeability = 0.0;
  /** Aquifer porosity (fraction). */
  private double aquiferPorosity = 0.0;
  /** Aquifer water viscosity (Pa*s). */
  private double aquiferViscosity = 0.0;
  /** Aquifer total compressibility (1/bar). */
  private double aquiferCompressibility = 0.0;
  /** Aquifer thickness (m). */
  private double aquiferThickness = 0.0;
  /** Aquifer/reservoir equivalent radius (m). */
  private double aquiferRadius = 0.0;
  /** Aquifer encroachment angle (degrees). */
  private double aquiferAngle = 360.0;
  /** Aquifer dimensionless outer radius (infinite when non-positive). */
  private double aquiferReD = Double.POSITIVE_INFINITY;
  /** Flag indicating an aquifer has been attached. */
  private boolean aquiferAttached = false;

  /**
   * Constructor.
   *
   * @param reservoir the reservoir to analyse
   */
  public ReservoirSurveillance(SimpleReservoir reservoir) {
    this.reservoir = reservoir;
  }

  // ============================================================
  // Gas material balance
  // ============================================================

  /**
   * Regress original gas in place from the recorded P/Z history (volumetric depletion drive).
   *
   * <p>
   * Z-factors are evaluated with the Sutton pseudo-critical / Hall-Yarborough correlation at the reservoir temperature
   * and gas gravity.
   * </p>
   *
   * @return the gas material balance regression result
   */
  public GasMaterialBalance.Result gasMaterialBalance() {
    double[] p = reservoir.getPressureHistory();
    double[] gp = reservoir.getGasProductionHistory();
    double tK = reservoir.getReservoirTemperature("K");
    double sg = reservoir.getGasGravity();
    return GasMaterialBalance.fitVolumetric(p, gp, tK, sg);
  }

  /**
   * Regress original gas in place from the recorded P/Z history.
   *
   * @param unit "Sm3" or "GSm3"
   * @return OGIP in the requested unit
   */
  public double estimateOGIP(String unit) {
    double ogip = gasMaterialBalance().getOgip();
    if ("GSm3".equals(unit)) {
      return ogip / 1.0e9;
    }
    return ogip;
  }

  /**
   * Compute the Cole plot ($F/E_g$ versus cumulative gas) for aquifer diagnosis.
   *
   * @return two-row array: row 0 = cumulative gas (Sm3), row 1 = F/Eg
   */
  public double[][] getColePlot() {
    double[] p = reservoir.getPressureHistory();
    double[] gp = reservoir.getGasProductionHistory();
    double tK = reservoir.getReservoirTemperature("K");
    double sg = reservoir.getGasGravity();
    double[] z = zFactors(p, tK, sg);
    return GasMaterialBalance.colePlot(p, z, gp, tK);
  }

  /**
   * Regress original gas in place from the recorded history using a supplied cumulative water influx (Havlena-Odeh).
   *
   * @param we cumulative water influx aligned with the recorded history (reservoir m3)
   * @return the gas material balance regression result
   */
  public GasMaterialBalance.Result gasMaterialBalanceWithAquifer(double[] we) {
    double[] p = reservoir.getPressureHistory();
    double[] gp = reservoir.getGasProductionHistory();
    double tK = reservoir.getReservoirTemperature("K");
    double sg = reservoir.getGasGravity();
    double[] z = zFactors(p, tK, sg);
    return GasMaterialBalance.fitHavlenaOdeh(p, z, gp, we, tK);
  }

  // ============================================================
  // Oil material balance (caller supplies tabulated PVT)
  // ============================================================

  /**
   * Regress original oil in place for a depletion-drive reservoir using the recorded production history and supplied
   * black-oil PVT.
   *
   * <p>
   * The reservoir withdrawal $F$ and oil expansion $E_o$ are built from the recorded cumulative oil ($N_p$), cumulative
   * gas ($G_p$, giving the producing ratio $R_p = G_p/N_p$) and cumulative water ($W_p$) together with the supplied
   * formation volume factors and solution gas-oil ratios. Index 0 supplies the initial PVT values.
   * </p>
   *
   * @param bo oil formation volume factor at each history point (rvol/svol)
   * @param rs solution gas-oil ratio at each history point (svol gas / svol oil)
   * @param bg gas formation volume factor at each history point (rvol/svol gas)
   * @param bw water formation volume factor (rvol/svol)
   * @return the oil material balance regression result
   */
  public OilMaterialBalance.Result estimateOOIPdepletion(double[] bo, double[] rs, double[] bg, double bw) {
    double[][] fEo = buildFandEo(bo, rs, bg, bw);
    return OilMaterialBalance.fitDepletionDrive(fEo[0], fEo[1]);
  }

  /**
   * Regress original oil in place for a water-drive reservoir using the recorded production history, supplied black-oil
   * PVT and a supplied cumulative water influx.
   *
   * @param bo oil formation volume factor at each history point (rvol/svol)
   * @param rs solution gas-oil ratio at each history point (svol gas / svol oil)
   * @param bg gas formation volume factor at each history point (rvol/svol gas)
   * @param bw water formation volume factor (rvol/svol)
   * @param we cumulative water influx aligned with the recorded history (reservoir m3)
   * @return the oil material balance regression result
   */
  public OilMaterialBalance.Result estimateOOIPwaterDrive(double[] bo, double[] rs, double[] bg, double bw,
      double[] we) {
    double[][] fEo = buildFandEo(bo, rs, bg, bw);
    return OilMaterialBalance.fitWaterDrive(fEo[0], fEo[1], we, bw);
  }

  /**
   * Build the reservoir withdrawal $F$ and oil expansion $E_o$ arrays (excluding the initial point) from the recorded
   * history and supplied PVT.
   *
   * @param bo oil FVF at each history point (rvol/svol)
   * @param rs solution GOR at each history point (svol gas / svol oil)
   * @param bg gas FVF at each history point (rvol/svol gas)
   * @param bw water FVF (rvol/svol)
   * @return two-row array {F, Eo} for history indices 1..n-1
   */
  private double[][] buildFandEo(double[] bo, double[] rs, double[] bg, double bw) {
    double[] np = reservoir.getOilProductionHistory();
    double[] gp = reservoir.getGasProductionHistory();
    double[] wp = reservoir.getWaterProductionHistory();
    int n = np.length;
    if (bo == null || rs == null || bg == null || bo.length != n || rs.length != n || bg.length != n) {
      throw new IllegalArgumentException("PVT arrays must match the recorded history length (" + n + ")");
    }
    if (n < 3) {
      throw new IllegalArgumentException("at least three recorded history points are required");
    }
    double boi = bo[0];
    double rsi = rs[0];
    double[] f = new double[n - 1];
    double[] eo = new double[n - 1];
    for (int i = 1; i < n; i++) {
      double rp = np[i] > 1.0e-30 ? gp[i] / np[i] : rsi;
      f[i - 1] = OilMaterialBalance.withdrawalF(np[i], rp, bo[i], rs[i], bg[i], wp[i], bw);
      eo[i - 1] = OilMaterialBalance.eo(bo[i], boi, rsi, rs[i], bg[i]);
    }
    return new double[][] { f, eo };
  }

  // ============================================================
  // Decline curve analysis
  // ============================================================

  /**
   * Fit Arps decline parameters to the recorded gas-rate history.
   *
   * @return map with keys "qi" (Sm3/day), "di" (1/day), "b" and "rSquared"
   */
  public Map<String, Double> fitGasDecline() {
    return fitDecline(reservoir.getGasProductionHistory());
  }

  /**
   * Fit Arps decline parameters to the recorded oil-rate history.
   *
   * @return map with keys "qi" (Sm3/day), "di" (1/day), "b" and "rSquared"
   */
  public Map<String, Double> fitOilDecline() {
    return fitDecline(reservoir.getOilProductionHistory());
  }

  /**
   * Estimated ultimate recovery from a fitted decline curve.
   *
   * @param fit the decline fit map (keys "qi", "di", "b")
   * @param economicLimitPerDay economic-limit rate (Sm3/day)
   * @return EUR (Sm3)
   */
  public double eur(Map<String, Double> fit, double economicLimitPerDay) {
    return DeclineCurveAnalysis.eurFromFit(fit, economicLimitPerDay);
  }

  /**
   * Convert a cumulative-production history to instantaneous rates and fit Arps parameters.
   *
   * @param cumulative cumulative production history (Sm3), aligned with the reservoir time history
   * @return map with keys "qi", "di", "b" and "rSquared"
   */
  private Map<String, Double> fitDecline(double[] cumulative) {
    double[] tSec = reservoir.getTimeHistory();
    int n = tSec.length;
    if (n < 4) {
      throw new IllegalArgumentException("at least four recorded history points are required to fit a decline curve");
    }
    // Midpoint times (days) and average rates (Sm3/day) from cumulative differences.
    double[] tDay = new double[n - 1];
    double[] rate = new double[n - 1];
    for (int i = 1; i < n; i++) {
      double dtDay = (tSec[i] - tSec[i - 1]) / 86400.0;
      tDay[i - 1] = 0.5 * (tSec[i] + tSec[i - 1]) / 86400.0;
      rate[i - 1] = dtDay > 1.0e-12 ? (cumulative[i] - cumulative[i - 1]) / dtDay : 0.0;
    }
    return DeclineCurveAnalysis.fitArps(tDay, rate);
  }

  // ============================================================
  // Aquifer support (Van Everdingen-Hurst / Carter-Tracy)
  // ============================================================

  /**
   * Attach a radial aquifer for water-influx calculation.
   *
   * @param permeability aquifer permeability (m^2)
   * @param porosity aquifer porosity (fraction)
   * @param viscosity aquifer water viscosity (Pa*s)
   * @param totalCompressibility total aquifer compressibility (1/bar)
   * @param thickness aquifer thickness (m)
   * @param radius aquifer/reservoir equivalent inner radius (m)
   * @param encroachmentAngleDeg encroachment angle (degrees, 0-360)
   * @param reD dimensionless outer radius (use {@code Double.POSITIVE_INFINITY} for an infinite aquifer)
   */
  public void attachAquifer(double permeability, double porosity, double viscosity, double totalCompressibility,
      double thickness, double radius, double encroachmentAngleDeg, double reD) {
    this.aquiferPermeability = permeability;
    this.aquiferPorosity = porosity;
    this.aquiferViscosity = viscosity;
    this.aquiferCompressibility = totalCompressibility;
    this.aquiferThickness = thickness;
    this.aquiferRadius = radius;
    this.aquiferAngle = encroachmentAngleDeg;
    this.aquiferReD = reD;
    this.aquiferAttached = true;
  }

  /**
   * Compute the cumulative water-influx history for the attached aquifer via the Carter-Tracy method.
   *
   * @return cumulative water influx (reservoir m3) aligned with the recorded history
   */
  public double[] computeWaterInflux() {
    if (!aquiferAttached) {
      throw new IllegalStateException("no aquifer attached; call attachAquifer(...) first");
    }
    double[] tSec = reservoir.getTimeHistory();
    double[] p = reservoir.getPressureHistory();
    int n = tSec.length;
    double pi = p[0];
    double[] tD = new double[n];
    double[] deltaP = new double[n];
    double ctPerPa = aquiferCompressibility / 1.0e5;
    for (int i = 0; i < n; i++) {
      tD[i] = VanEverdingenHurstAquifer.dimensionlessTime(aquiferPermeability, tSec[i], aquiferPorosity,
          aquiferViscosity, ctPerPa, aquiferRadius);
      deltaP[i] = pi - p[i];
    }
    double u = VanEverdingenHurstAquifer.aquiferConstant(aquiferPorosity, aquiferCompressibility, aquiferThickness,
        aquiferRadius, aquiferAngle);
    return VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(tD, deltaP, u, aquiferReD);
  }

  // ============================================================
  // Summary
  // ============================================================

  /**
   * Generate a surveillance summary comparing regressed reserves against the reservoir's initial in-place volumes.
   *
   * @return map of key surveillance metrics
   */
  public Map<String, Double> summary() {
    Map<String, Double> out = new LinkedHashMap<String, Double>();
    out.put("numberOfHistoryPoints", (double) reservoir.getTimeHistory().length);
    try {
      GasMaterialBalance.Result gas = gasMaterialBalance();
      out.put("estimatedOGIP_GSm3", gas.getOgip() / 1.0e9);
      out.put("ogipRSquared", gas.getRSquared());
      out.put("actualOGIP_GSm3", reservoir.getOGIP("GSm3"));
    } catch (RuntimeException ex) {
      out.put("estimatedOGIP_GSm3", Double.NaN);
    }
    return out;
  }

  /**
   * Sutton/Hall-Yarborough Z-factors at each pressure point.
   *
   * @param p pressures (bara)
   * @param tK reservoir temperature (K)
   * @param sg gas specific gravity (air = 1.0)
   * @return Z-factor array
   */
  private static double[] zFactors(double[] p, double tK, double sg) {
    double[] z = new double[p.length];
    for (int i = 0; i < p.length; i++) {
      z[i] = ZFactorCorrelations.zFactorSutton(p[i], tK, sg);
    }
    return z;
  }
}
