package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Mixing-induced scale evaluation for two incompatible brines (for example seawater injection breaking through into
 * formation water).
 *
 * <p>
 * Sulphate scales (BaSO4, SrSO4, CaSO4) are the classic incompatibility problem: sulphate-rich seawater mixed with a
 * barium/strontium-rich formation water precipitates sulphate scale even though neither brine is scaling on its own.
 * The worst supersaturation frequently occurs at an intermediate mixing ratio, not at either end member.
 * </p>
 *
 * <p>
 * This evaluator linearly blends the two brine ion analyses across a set of mixing fractions (fraction of brine A in
 * the mixture), evaluates an activity-corrected {@link ElectrolyteScaleCalculator} at each blend, and reports the
 * saturation index of each mineral versus mixing fraction plus the worst-case fraction and mineral. Common-ion effects
 * are captured because the shared ions (SO4, Ba, Sr, Ca) are mixed on a mass-concentration basis before the saturation
 * calculation.
 * </p>
 *
 * <p>
 * Standards informational: NACE TM0374, Oddo-Tomson, NORSOK M-001.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see ElectrolyteScaleCalculator
 */
public class BrineMixingScaleEvaluator implements Serializable {

  private static final long serialVersionUID = 1000L;

  /**
   * Immutable ion analysis of a brine (all concentrations in mg/L).
   */
  public static class BrineComposition implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double calciumMgL;
    private final double bariumMgL;
    private final double strontiumMgL;
    private final double magnesiumMgL;
    private final double sodiumMgL;
    private final double potassiumMgL;
    private final double chlorideMgL;
    private final double sulphateMgL;
    private final double bicarbonateMgL;

    /**
     * Creates a brine composition from ion concentrations in mg/L.
     *
     * @param calciumMgL calcium concentration [mg/L]
     * @param bariumMgL barium concentration [mg/L]
     * @param strontiumMgL strontium concentration [mg/L]
     * @param magnesiumMgL magnesium concentration [mg/L]
     * @param sodiumMgL sodium concentration [mg/L]
     * @param potassiumMgL potassium concentration [mg/L]
     * @param chlorideMgL chloride concentration [mg/L]
     * @param sulphateMgL sulphate concentration [mg/L]
     * @param bicarbonateMgL bicarbonate concentration [mg/L]
     */
    public BrineComposition(double calciumMgL, double bariumMgL, double strontiumMgL, double magnesiumMgL,
        double sodiumMgL, double potassiumMgL, double chlorideMgL, double sulphateMgL, double bicarbonateMgL) {
      this.calciumMgL = calciumMgL;
      this.bariumMgL = bariumMgL;
      this.strontiumMgL = strontiumMgL;
      this.magnesiumMgL = magnesiumMgL;
      this.sodiumMgL = sodiumMgL;
      this.potassiumMgL = potassiumMgL;
      this.chlorideMgL = chlorideMgL;
      this.sulphateMgL = sulphateMgL;
      this.bicarbonateMgL = bicarbonateMgL;
    }
  }

  /**
   * Result of the mineral saturation at one mixing fraction.
   */
  public static class MixPoint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double fractionA;
    private final double caco3SI;
    private final double baso4SI;
    private final double caso4SI;
    private final double srso4SI;

    /**
     * Creates a mixing-fraction result point.
     *
     * @param fractionA fraction of brine A in the mixture (0 to 1)
     * @param caco3SI CaCO3 saturation index
     * @param baso4SI BaSO4 saturation index
     * @param caso4SI CaSO4 saturation index
     * @param srso4SI SrSO4 saturation index
     */
    public MixPoint(double fractionA, double caco3SI, double baso4SI, double caso4SI, double srso4SI) {
      this.fractionA = fractionA;
      this.caco3SI = caco3SI;
      this.baso4SI = baso4SI;
      this.caso4SI = caso4SI;
      this.srso4SI = srso4SI;
    }

    /**
     * Gets the fraction of brine A at this point.
     *
     * @return fraction of brine A (0 to 1)
     */
    public double getFractionA() {
      return fractionA;
    }

    /**
     * Gets the CaCO3 saturation index.
     *
     * @return CaCO3 saturation index
     */
    public double getCaCO3SI() {
      return caco3SI;
    }

    /**
     * Gets the BaSO4 saturation index.
     *
     * @return BaSO4 saturation index
     */
    public double getBaSO4SI() {
      return baso4SI;
    }

    /**
     * Gets the CaSO4 saturation index.
     *
     * @return CaSO4 saturation index
     */
    public double getCaSO4SI() {
      return caso4SI;
    }

    /**
     * Gets the SrSO4 saturation index.
     *
     * @return SrSO4 saturation index
     */
    public double getSrSO4SI() {
      return srso4SI;
    }
  }

  private final BrineComposition brineA;
  private final BrineComposition brineB;
  private double temperatureC = 25.0;
  private double pressureBara = 1.0;
  private double pH = 6.5;
  private int steps = 11;

  private final List<MixPoint> points = new ArrayList<MixPoint>();
  private double worstFractionA = Double.NaN;
  private String worstMineral = "NONE";
  private double worstSI = Double.NEGATIVE_INFINITY;
  private boolean evaluated = false;

  /**
   * Constructs an evaluator for two brines.
   *
   * @param brineA the first brine end member (for example formation water); must not be null
   * @param brineB the second brine end member (for example injection seawater); must not be null
   */
  public BrineMixingScaleEvaluator(BrineComposition brineA, BrineComposition brineB) {
    if (brineA == null || brineB == null) {
      throw new IllegalArgumentException("both brine compositions must be non-null");
    }
    this.brineA = brineA;
    this.brineB = brineB;
  }

  /**
   * Sets the temperature and pressure at which the mixture is evaluated.
   *
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBara pressure in bara
   * @return this for chaining
   */
  public BrineMixingScaleEvaluator setConditions(double temperatureC, double pressureBara) {
    this.temperatureC = temperatureC;
    this.pressureBara = pressureBara;
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the pH of the mixed brine.
   *
   * @param pH pH of the aqueous phase
   * @return this for chaining
   */
  public BrineMixingScaleEvaluator setPH(double pH) {
    this.pH = pH;
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the number of mixing fractions evaluated between the two end members (minimum 2).
   *
   * @param steps number of mixing points
   * @return this for chaining
   */
  public BrineMixingScaleEvaluator setSteps(int steps) {
    this.steps = Math.max(2, steps);
    this.evaluated = false;
    return this;
  }

  /**
   * Evaluates the mineral saturation across the mixing range and records the worst-case point.
   *
   * @return this for chaining
   */
  public BrineMixingScaleEvaluator evaluate() {
    points.clear();
    worstSI = Double.NEGATIVE_INFINITY;
    worstMineral = "NONE";
    worstFractionA = Double.NaN;

    for (int i = 0; i < steps; i++) {
      double fA = (double) i / (steps - 1);
      double fB = 1.0 - fA;

      double ca = blend(brineA.calciumMgL, brineB.calciumMgL, fA, fB);
      double ba = blend(brineA.bariumMgL, brineB.bariumMgL, fA, fB);
      double sr = blend(brineA.strontiumMgL, brineB.strontiumMgL, fA, fB);
      double mg = blend(brineA.magnesiumMgL, brineB.magnesiumMgL, fA, fB);
      double na = blend(brineA.sodiumMgL, brineB.sodiumMgL, fA, fB);
      double k = blend(brineA.potassiumMgL, brineB.potassiumMgL, fA, fB);
      double cl = blend(brineA.chlorideMgL, brineB.chlorideMgL, fA, fB);
      double so4 = blend(brineA.sulphateMgL, brineB.sulphateMgL, fA, fB);
      double hco3 = blend(brineA.bicarbonateMgL, brineB.bicarbonateMgL, fA, fB);

      ElectrolyteScaleCalculator calc = new ElectrolyteScaleCalculator();
      calc.setTemperatureCelsius(temperatureC).setPressureBara(pressureBara).setPH(pH);
      calc.setCations(ca, ba, sr, mg, na, k, 0.0);
      calc.setAnions(cl, so4, hco3, 0.0);
      calc.calculate();

      MixPoint point = new MixPoint(fA, calc.getCaCO3SaturationIndex(), calc.getBaSO4SaturationIndex(),
          calc.getCaSO4SaturationIndex(), calc.getSrSO4SaturationIndex());
      points.add(point);

      updateWorst(fA, "CaCO3", point.caco3SI);
      updateWorst(fA, "BaSO4", point.baso4SI);
      updateWorst(fA, "CaSO4", point.caso4SI);
      updateWorst(fA, "SrSO4", point.srso4SI);
    }

    evaluated = true;
    return this;
  }

  /**
   * Blends two concentrations by mixing fractions.
   *
   * @param a concentration in brine A
   * @param b concentration in brine B
   * @param fA fraction of brine A
   * @param fB fraction of brine B
   * @return the blended concentration
   */
  private double blend(double a, double b, double fA, double fB) {
    return a * fA + b * fB;
  }

  /**
   * Updates the worst-case record if the supplied saturation index is the highest seen so far.
   *
   * @param fractionA the mixing fraction of brine A at this point
   * @param mineral the mineral name
   * @param si the saturation index
   */
  private void updateWorst(double fractionA, String mineral, double si) {
    if (si > worstSI) {
      worstSI = si;
      worstMineral = mineral;
      worstFractionA = fractionA;
    }
  }

  /**
   * Gets the list of evaluated mixing points.
   *
   * @return the mixing-fraction result points
   */
  public List<MixPoint> getPoints() {
    ensureEvaluated();
    return points;
  }

  /**
   * Gets the mixing fraction of brine A at which the worst-case supersaturation occurs.
   *
   * @return worst-case fraction of brine A (0 to 1)
   */
  public double getWorstFractionA() {
    ensureEvaluated();
    return worstFractionA;
  }

  /**
   * Gets the mineral with the highest saturation index across the mixing range.
   *
   * @return the worst-case mineral name
   */
  public String getWorstMineral() {
    ensureEvaluated();
    return worstMineral;
  }

  /**
   * Gets the highest saturation index across the mixing range.
   *
   * @return the worst-case saturation index
   */
  public double getWorstSI() {
    ensureEvaluated();
    return worstSI;
  }

  /**
   * Ensures {@link #evaluate()} has been called before returning a result.
   */
  private void ensureEvaluated() {
    if (!evaluated) {
      evaluate();
    }
  }

  /**
   * Serialises the mixing sweep and worst-case summary to a JSON string.
   *
   * @return a pretty-printed JSON representation
   */
  public String toJson() {
    ensureEvaluated();
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("temperatureC", temperatureC);
    map.put("pressureBara", pressureBara);
    map.put("pH", pH);
    map.put("worstFractionA", worstFractionA);
    map.put("worstMineral", worstMineral);
    map.put("worstSI", worstSI);
    map.put("points", points);
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(map);
  }
}
