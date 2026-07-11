package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Simultaneous multi-mineral solid-precipitation equilibrium solver for oilfield brines.
 *
 * <p>
 * Unlike the decoupled per-mineral mass estimate in {@link ScaleMassCalculator} (which applies the {@code 1 - 10^(-SI)}
 * approximation to each salt independently), this class solves the coupled solid/liquid equilibrium where several
 * minerals compete for shared ions. For example barite (BaSO4), celestite (SrSO4) and anhydrite (CaSO4) all draw on the
 * same sulphate pool, and calcite (CaCO3) and siderite (FeCO3) share the carbonate pool; calcite and anhydrite share
 * calcium. The solver precipitates (and, where required, re-dissolves) every mineral until the Gibbs complementarity
 * conditions are met:
 * </p>
 * <ul>
 * <li>each mineral with solid present has saturation index SI = 0 (activity product = Ksp), and</li>
 * <li>each mineral without solid has SI &le; 0 (undersaturated).</li>
 * </ul>
 *
 * <p>
 * This mirrors the behaviour of dedicated scale-prediction tools (OLI ScaleChem, ScaleSoftPitzer, Multiscale) at a
 * screening level: the output is the actual precipitated mass per mineral and the residual (equilibrated) brine
 * composition, not just a saturation index.
 * </p>
 *
 * <p>
 * The thermodynamics (solubility products, ion pairing, Debye-Hückel A parameter) are taken directly from a configured
 * {@link ScalePredictionCalculator}, so the starting saturation state is identical to that class. The activity model
 * can optionally be upgraded from the Davies equation (valid to ionic strength I &asymp; 0.5 mol/kg) to an extended
 * Debye-Hückel "B-dot" (Helgeson) model that is usable to I &asymp; 3 mol/kg, which matters for high-salinity formation
 * waters.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * ScalePredictionCalculator predictor = new ScalePredictionCalculator();
 * predictor.setTemperatureCelsius(80.0);
 * predictor.setPressureBara(150.0);
 * predictor.setBariumConcentration(300.0);
 * predictor.setStrontiumConcentration(500.0);
 * predictor.setSulphateConcentration(2000.0);
 * predictor.setCalciumConcentration(2000.0);
 * predictor.setBicarbonateConcentration(500.0);
 * predictor.setTotalDissolvedSolids(120000.0);
 * predictor.enableAutoPH();
 *
 * MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(predictor);
 * eq.setActivityModel(MultiMineralScaleEquilibrium.ActivityModel.BDOT);
 * eq.solve();
 * double baso4 = eq.getPrecipitatedMassMgPerL("BaSO4");
 * double total = eq.getTotalScaleMassMgPerL();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class MultiMineralScaleEquilibrium implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Activity-coefficient model used for the coupled equilibrium. */
  public enum ActivityModel {
    /** Davies equation (valid to I &asymp; 0.5 mol/kg). */
    DAVIES,
    /** Extended Debye-Hückel "B-dot" (Helgeson) model (usable to I &asymp; 3 mol/kg). */
    BDOT
  }

  // Ion index layout for the free-concentration vector.
  private static final int CA = 0;
  private static final int BA = 1;
  private static final int SR = 2;
  private static final int FE = 3;
  private static final int SO4 = 4;
  private static final int CO3 = 5;

  // Molar masses (g/mol).
  private static final double MW_BASO4 = 233.39;
  private static final double MW_SRSO4 = 183.68;
  private static final double MW_CASO4 = 136.14;
  private static final double MW_CACO3 = 100.09;
  private static final double MW_FECO3 = 115.85;

  /** Numerical floor for free-ion molarity. */
  private static final double EPS = 1.0e-18;

  /** Debye-Hückel B parameter (Å⁻¹·(kg/mol)^0.5), weakly temperature dependent (~25 °C value). */
  private static final double DH_B = 0.3283;

  private final ScalePredictionCalculator predictor;
  private ActivityModel activityModel = ActivityModel.DAVIES;

  /** Effective ion-size parameter å (Å) for the B-dot model. */
  private double bdotIonSize = 5.0;

  /** Helgeson b-dot term (kg/mol), ~0.041 for NaCl-dominated brine at 25 °C. */
  private double bdotB = 0.041;

  private double waterVolumeLitres = 1.0;
  private int maxIterations = 200000;
  private double relaxation = 0.5;
  private double convergenceThreshold = 1.0e-15;

  private boolean solved = false;
  private double divalentGamma = Double.NaN;
  private final Map<String, MineralResult> results = new LinkedHashMap<String, MineralResult>();
  private final Map<String, Double> residualFreeIons = new LinkedHashMap<String, Double>();

  /**
   * Creates a solver bound to a configured {@link ScalePredictionCalculator}.
   *
   * @param predictor the configured scale prediction calculator (temperature, pressure and ion concentrations already
   * set)
   */
  public MultiMineralScaleEquilibrium(ScalePredictionCalculator predictor) {
    this.predictor = predictor;
  }

  /**
   * Sets the activity-coefficient model.
   *
   * @param model the activity model
   * @return this instance for chaining
   */
  public MultiMineralScaleEquilibrium setActivityModel(ActivityModel model) {
    this.activityModel = model;
    this.solved = false;
    return this;
  }

  /**
   * Sets the effective ion-size parameter and b-dot term for the B-dot activity model.
   *
   * @param ionSizeAngstrom effective ion-size parameter å in Ångström
   * @param bDot Helgeson b-dot term in kg/mol
   * @return this instance for chaining
   */
  public MultiMineralScaleEquilibrium setBdotParameters(double ionSizeAngstrom, double bDot) {
    this.bdotIonSize = ionSizeAngstrom;
    this.bdotB = bDot;
    this.solved = false;
    return this;
  }

  /**
   * Sets the water volume used to report absolute precipitated mass in grams.
   *
   * @param litres water volume in litres
   * @return this instance for chaining
   */
  public MultiMineralScaleEquilibrium setWaterVolume(double litres) {
    this.waterVolumeLitres = litres;
    return this;
  }

  /**
   * Sets solver controls.
   *
   * @param maxIter maximum number of iterations
   * @param relax under-relaxation factor in (0, 1]
   * @param convThreshold precipitation-step convergence threshold in mol/L
   * @return this instance for chaining
   */
  public MultiMineralScaleEquilibrium setSolverControls(int maxIter, double relax, double convThreshold) {
    this.maxIterations = maxIter;
    this.relaxation = relax;
    this.convergenceThreshold = convThreshold;
    this.solved = false;
    return this;
  }

  /**
   * Computes the divalent-ion activity coefficient for the selected model.
   *
   * @param ionicStrength ionic strength in mol/L
   * @return activity coefficient of a divalent ion (charge 2)
   */
  private double divalentActivityCoefficient(double ionicStrength) {
    if (activityModel == ActivityModel.DAVIES) {
      return predictor.getDivalentActivityCoefficient();
    }
    // Extended Debye-Hückel "B-dot" (Helgeson): log10 γ = -A z² √I/(1+å B √I) + ḃ I
    double a = predictor.getDebyeHuckelAParameter();
    double sqrtI = Math.sqrt(Math.max(ionicStrength, 0.0));
    double z2 = 4.0;
    double logGamma = -a * z2 * sqrtI / (1.0 + bdotIonSize * DH_B * sqrtI) + bdotB * ionicStrength;
    return Math.pow(10.0, logGamma);
  }

  /**
   * Solves the coupled multi-mineral solid/liquid equilibrium.
   *
   * @return this instance for chaining
   */
  public MultiMineralScaleEquilibrium solve() {
    predictor.calculate();
    results.clear();
    residualFreeIons.clear();

    double ionicStrength = predictor.getIonicStrengthMolPerL();
    double gamma2 = divalentActivityCoefficient(ionicStrength);
    divalentGamma = gamma2;
    double gamma2sq = gamma2 * gamma2;

    // Free ion vector (mol/L). Ca, SO4, CO3 use ion-pairing-corrected free values; Ba, Sr, Fe use
    // total analytical values (they are not ion-paired in the predictor).
    double[] free = new double[6];
    free[CA] = Math.max(predictor.getFreeCalciumMolPerL(), 0.0);
    free[BA] = Math.max(predictor.getTotalBariumMolPerL(), 0.0);
    free[SR] = Math.max(predictor.getTotalStrontiumMolPerL(), 0.0);
    free[FE] = Math.max(predictor.getTotalIronMolPerL(), 0.0);
    free[SO4] = Math.max(predictor.getFreeSulphateMolPerL(), 0.0);
    free[CO3] = Math.max(predictor.getFreeCarbonateMolPerL(), 0.0);

    // Mineral definitions: {cation index, anion index, Ksp, molar mass, name, formula}.
    List<Mineral> minerals = new ArrayList<Mineral>();
    addMineral(minerals, BA, SO4, predictor.getKspBarite(), MW_BASO4, "BaSO4", "barite", free);
    addMineral(minerals, SR, SO4, predictor.getKspCelestite(), MW_SRSO4, "SrSO4", "celestite", free);
    addMineral(minerals, CA, SO4, predictor.getKspAnhydrite(), MW_CASO4, "CaSO4", "anhydrite", free);
    addMineral(minerals, CA, CO3, predictor.getKspCalcite(), MW_CACO3, "CaCO3", "calcite", free);
    addMineral(minerals, FE, CO3, predictor.getKspSiderite(), MW_FECO3, "FeCO3", "siderite", free);

    // Record initial (pre-precipitation) saturation indices.
    for (Mineral m : minerals) {
      m.initialSI = saturationIndex(free[m.cation], free[m.anion], gamma2sq, m.ksp);
    }

    // Coupled precipitation / redissolution by greedy coordinate descent: at every iteration the
    // single most-violating mineral (largest precipitation OR redissolution extent) is advanced by a
    // relaxed step. Picking one mineral at a time and allowing under-saturated solids to redissolve
    // makes the scheme converge to the true Gibbs complementarity solution even for stiff shared-ion
    // competition (e.g. barite, celestite and anhydrite drawing on one sulphate pool), which a
    // simultaneous Gauss-Seidel sweep can stall on.
    double convThreshold = convergenceThreshold;
    for (int iter = 0; iter < maxIterations; iter++) {
      Mineral best = null;
      double bestAbs = 0.0;
      double bestDxi = 0.0;

      for (Mineral m : minerals) {
        double cat = free[m.cation];
        double an = free[m.anion];

        // Equilibrium extent ξ for (cat-ξ)(an-ξ) = Ksp/γ² (signed: ξ<0 means redissolution).
        double kPrime = m.ksp / gamma2sq;
        double b = cat + an;
        double c = cat * an - kPrime;
        double disc = b * b - 4.0 * c;
        if (disc < 0.0) {
          disc = 0.0;
        }
        double xiEq = 0.5 * (b - Math.sqrt(disc));

        double dxi;
        if (xiEq > 0.0) {
          // Precipitation, bounded by the limiting ion.
          dxi = xiEq;
          double cap = Math.min(cat, an) - EPS;
          if (dxi > cap) {
            dxi = Math.max(cap, 0.0);
          }
        } else if (xiEq < 0.0 && m.precipitatedMolL > EPS) {
          // Redissolution, bounded by the solid present.
          dxi = xiEq;
          if (dxi < -m.precipitatedMolL) {
            dxi = -m.precipitatedMolL;
          }
        } else {
          dxi = 0.0;
        }

        if (Math.abs(dxi) > bestAbs) {
          bestAbs = Math.abs(dxi);
          bestDxi = dxi;
          best = m;
        }
      }

      if (best == null || bestAbs < convThreshold) {
        break;
      }

      double step = relaxation * bestDxi;
      free[best.cation] -= step;
      free[best.anion] -= step;
      best.precipitatedMolL += step;
      if (best.precipitatedMolL < 0.0) {
        best.precipitatedMolL = 0.0;
      }
      if (free[best.cation] < 0.0) {
        free[best.cation] = 0.0;
      }
      if (free[best.anion] < 0.0) {
        free[best.anion] = 0.0;
      }
    }

    // Store results.
    for (Mineral m : minerals) {
      double massMgL = m.precipitatedMolL * m.molarMass * 1000.0;
      double finalSI = saturationIndex(free[m.cation], free[m.anion], gamma2sq, m.ksp);
      MineralResult r = new MineralResult();
      r.name = m.name;
      r.formula = m.formula;
      r.precipitatedMolPerL = m.precipitatedMolL;
      r.precipitatedMassMgPerL = massMgL;
      r.precipitatedMassGrams = massMgL / 1000.0 * waterVolumeLitres;
      r.initialSI = m.initialSI;
      r.finalSI = finalSI;
      r.limitingIonExhausted = (free[m.cation] <= 10.0 * EPS || free[m.anion] <= 10.0 * EPS)
          && m.precipitatedMolL > EPS;
      results.put(m.name, r);
    }

    residualFreeIons.put("Ca++", free[CA]);
    residualFreeIons.put("Ba++", free[BA]);
    residualFreeIons.put("Sr++", free[SR]);
    residualFreeIons.put("Fe++", free[FE]);
    residualFreeIons.put("SO4--", free[SO4]);
    residualFreeIons.put("CO3--", free[CO3]);

    solved = true;
    return this;
  }

  /**
   * Registers a mineral if both its cation and anion are initially present.
   *
   * @param minerals target list
   * @param cation cation ion index
   * @param anion anion ion index
   * @param ksp solubility product
   * @param molarMass molar mass g/mol
   * @param name short mineral name
   * @param formula common mineral name
   * @param free free-ion vector
   */
  private void addMineral(List<Mineral> minerals, int cation, int anion, double ksp, double molarMass, String name,
      String formula, double[] free) {
    if (free[cation] <= EPS || free[anion] <= EPS || !(ksp > 0.0) || Double.isNaN(ksp)) {
      return;
    }
    Mineral m = new Mineral();
    m.cation = cation;
    m.anion = anion;
    m.ksp = ksp;
    m.molarMass = molarMass;
    m.name = name;
    m.formula = formula;
    minerals.add(m);
  }

  /**
   * Computes the saturation index SI = log10(γ² · cCat · cAn / Ksp).
   *
   * @param cCat free cation molarity
   * @param cAn free anion molarity
   * @param gamma2sq squared divalent activity coefficient
   * @param ksp solubility product
   * @return saturation index
   */
  private double saturationIndex(double cCat, double cAn, double gamma2sq, double ksp) {
    double iap = gamma2sq * Math.max(cCat, EPS) * Math.max(cAn, EPS);
    if (iap <= 0.0 || ksp <= 0.0) {
      return Double.NaN;
    }
    return Math.log10(iap / ksp);
  }

  /**
   * Returns the precipitated mass of a mineral in mg per litre of water.
   *
   * @param mineralName mineral name (e.g. "BaSO4")
   * @return precipitated mass in mg/L, or 0.0 if the mineral did not precipitate
   */
  public double getPrecipitatedMassMgPerL(String mineralName) {
    ensureSolved();
    MineralResult r = results.get(mineralName);
    return r == null ? 0.0 : r.precipitatedMassMgPerL;
  }

  /**
   * Returns the precipitated amount of a mineral in mol per litre of water.
   *
   * @param mineralName mineral name (e.g. "BaSO4")
   * @return precipitated amount in mol/L
   */
  public double getPrecipitatedMolPerL(String mineralName) {
    ensureSolved();
    MineralResult r = results.get(mineralName);
    return r == null ? 0.0 : r.precipitatedMolPerL;
  }

  /**
   * Returns the total precipitated scale mass across all minerals in mg per litre.
   *
   * @return total scale mass in mg/L
   */
  public double getTotalScaleMassMgPerL() {
    ensureSolved();
    double sum = 0.0;
    for (MineralResult r : results.values()) {
      sum += r.precipitatedMassMgPerL;
    }
    return sum;
  }

  /**
   * Returns the residual (equilibrated) free-ion concentration in mol/L.
   *
   * @param ion ion label (Ca++, Ba++, Sr++, Fe++, SO4--, CO3--)
   * @return residual free concentration in mol/L, or NaN if unknown
   */
  public double getResidualFreeIonMolPerL(String ion) {
    ensureSolved();
    Double v = residualFreeIons.get(ion);
    return v == null ? Double.NaN : v.doubleValue();
  }

  /**
   * Returns the per-mineral result map.
   *
   * @return map of mineral name to {@link MineralResult}
   */
  public Map<String, MineralResult> getResults() {
    ensureSolved();
    return results;
  }

  /**
   * Returns whether the equilibrium has been solved.
   *
   * @return true if {@link #solve()} completed
   */
  public boolean isSolved() {
    return solved;
  }

  /**
   * Returns the divalent-ion activity coefficient used in the last solve.
   *
   * @return divalent activity coefficient, or NaN if not solved
   */
  public double getDivalentActivityCoefficientUsed() {
    ensureSolved();
    return divalentGamma;
  }

  /**
   * Ensures the equilibrium has been solved, solving lazily if required.
   */
  private void ensureSolved() {
    if (!solved) {
      solve();
    }
  }

  /**
   * Returns a JSON report of the coupled equilibrium result.
   *
   * @return JSON string
   */
  public String toJson() {
    ensureSolved();
    Map<String, Object> out = new LinkedHashMap<String, Object>();

    Map<String, Object> conditions = new LinkedHashMap<String, Object>();
    conditions.put("temperature_C", predictor.getTemperatureKelvin() - 273.15);
    conditions.put("ionicStrength_molL", predictor.getIonicStrengthMolPerL());
    conditions.put("activityModel", activityModel.name());
    conditions.put("divalentActivityCoefficient", divalentGamma);
    conditions.put("waterVolume_L", waterVolumeLitres);
    out.put("conditions", conditions);

    Map<String, Object> mineralMap = new LinkedHashMap<String, Object>();
    for (MineralResult r : results.values()) {
      Map<String, Object> e = new LinkedHashMap<String, Object>();
      e.put("formula", r.formula);
      e.put("initialSI", r.initialSI);
      e.put("finalSI", r.finalSI);
      e.put("precipitated_molL", r.precipitatedMolPerL);
      e.put("precipitated_mgL", r.precipitatedMassMgPerL);
      e.put("precipitated_g", r.precipitatedMassGrams);
      e.put("limitingIonExhausted", r.limitingIonExhausted);
      mineralMap.put(r.name, e);
    }
    out.put("minerals", mineralMap);
    out.put("totalScaleMass_mgL", getTotalScaleMassMgPerL());
    out.put("residualFreeIons_molL", residualFreeIons);

    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(out);
  }

  /**
   * Internal mineral bookkeeping.
   */
  private static final class Mineral implements Serializable {
    private static final long serialVersionUID = 1000L;
    int cation;
    int anion;
    double ksp;
    double molarMass;
    String name;
    String formula;
    double precipitatedMolL = 0.0;
    double initialSI = Double.NaN;
  }

  /**
   * Per-mineral precipitation result.
   */
  public static final class MineralResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String name;
    private String formula;
    private double precipitatedMolPerL;
    private double precipitatedMassMgPerL;
    private double precipitatedMassGrams;
    private double initialSI;
    private double finalSI;
    private boolean limitingIonExhausted;

    /**
     * Returns the mineral name.
     *
     * @return mineral name (e.g. "BaSO4")
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the common mineral name.
     *
     * @return common name (e.g. "barite")
     */
    public String getFormula() {
      return formula;
    }

    /**
     * Returns the precipitated amount in mol per litre.
     *
     * @return precipitated amount in mol/L
     */
    public double getPrecipitatedMolPerL() {
      return precipitatedMolPerL;
    }

    /**
     * Returns the precipitated mass in mg per litre.
     *
     * @return precipitated mass in mg/L
     */
    public double getPrecipitatedMassMgPerL() {
      return precipitatedMassMgPerL;
    }

    /**
     * Returns the precipitated mass in grams for the configured water volume.
     *
     * @return precipitated mass in grams
     */
    public double getPrecipitatedMassGrams() {
      return precipitatedMassGrams;
    }

    /**
     * Returns the saturation index before precipitation.
     *
     * @return initial saturation index
     */
    public double getInitialSI() {
      return initialSI;
    }

    /**
     * Returns the saturation index at equilibrium (should be &asymp; 0 when solid is present, or &le; 0 when the
     * mineral did not precipitate).
     *
     * @return final saturation index
     */
    public double getFinalSI() {
      return finalSI;
    }

    /**
     * Returns whether a limiting ion was effectively exhausted by precipitation.
     *
     * @return true if a shared ion was depleted
     */
    public boolean isLimitingIonExhausted() {
      return limitingIonExhausted;
    }
  }
}
