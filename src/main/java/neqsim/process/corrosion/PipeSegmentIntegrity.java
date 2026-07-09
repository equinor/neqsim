package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.chemistry.scale.ElectrolyteScaleCalculator;
import neqsim.process.chemistry.scale.ScaleKinetics;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

/**
 * Flow-coupled per-segment integrity screening that combines CO2 corrosion and mineral-scale risk along a pipeline or
 * flowline profile.
 *
 * <p>
 * Most corrosion and scaling screening is evaluated at a single bulk condition. In reality both threats vary strongly
 * along a line as temperature, pressure and velocity change: corrosion peaks where the wall shear and low-pH water
 * combine, while CaCO3 scale peaks where CO2 breaks out and temperature rises. This class walks a supplied set of
 * segments (each with its own temperature, pressure, velocity and diameter) and, for every segment, evaluates:
 * </p>
 * <ul>
 * <li>the {@link NorsokM506CorrosionRate} CO2 corrosion rate (mm/yr) at the local conditions, and</li>
 * <li>the CaCO3 saturation index from an {@link ElectrolyteScaleCalculator} on the shared brine chemistry, optionally
 * converted to a scale growth rate with {@link ScaleKinetics}.</li>
 * </ul>
 *
 * <p>
 * It then reports the worst corrosion segment and the worst scale segment, so an investigation can locate where
 * mitigation (inhibitor, material upgrade, insulation) is most needed. The pH per segment is taken from a robust
 * estimate (supplied brine pH, else a CO2-water correlation via {@link RobustAqueousPH}).
 * </p>
 *
 * <p>
 * This is a screening-level integrator (NORSOK M-506, NACE TM0374, NORSOK M-001 informational), intended for relative
 * ranking of segments rather than absolute mass-balance prediction.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see NorsokM506CorrosionRate
 * @see ElectrolyteScaleCalculator
 * @see ScaleKinetics
 */
public class PipeSegmentIntegrity implements Serializable {

  private static final long serialVersionUID = 1000L;

  /**
   * Per-segment integrity result.
   */
  public static class SegmentResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int index;
    private final double temperatureC;
    private final double pressureBara;
    private final double velocityMs;
    private final double corrosionRateMmYr;
    private final double caco3SI;
    private final double scaleGrowthMmYr;
    private final double pH;

    /**
     * Creates a per-segment result.
     *
     * @param index the segment index
     * @param temperatureC segment temperature in degrees Celsius
     * @param pressureBara segment pressure in bara
     * @param velocityMs segment flow velocity in m/s
     * @param corrosionRateMmYr CO2 corrosion rate in mm/yr
     * @param caco3SI CaCO3 saturation index
     * @param scaleGrowthMmYr scale growth rate in mm/yr
     * @param pH the pH used for the segment
     */
    public SegmentResult(int index, double temperatureC, double pressureBara, double velocityMs,
        double corrosionRateMmYr, double caco3SI, double scaleGrowthMmYr, double pH) {
      this.index = index;
      this.temperatureC = temperatureC;
      this.pressureBara = pressureBara;
      this.velocityMs = velocityMs;
      this.corrosionRateMmYr = corrosionRateMmYr;
      this.caco3SI = caco3SI;
      this.scaleGrowthMmYr = scaleGrowthMmYr;
      this.pH = pH;
    }

    /**
     * Gets the segment index.
     *
     * @return the zero-based segment index
     */
    public int getIndex() {
      return index;
    }

    /**
     * Gets the segment temperature.
     *
     * @return temperature in degrees Celsius
     */
    public double getTemperatureC() {
      return temperatureC;
    }

    /**
     * Gets the segment pressure.
     *
     * @return pressure in bara
     */
    public double getPressureBara() {
      return pressureBara;
    }

    /**
     * Gets the segment flow velocity.
     *
     * @return velocity in m/s
     */
    public double getVelocityMs() {
      return velocityMs;
    }

    /**
     * Gets the CO2 corrosion rate.
     *
     * @return corrosion rate in mm/yr
     */
    public double getCorrosionRateMmYr() {
      return corrosionRateMmYr;
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
     * Gets the scale growth rate.
     *
     * @return scale growth rate in mm/yr
     */
    public double getScaleGrowthMmYr() {
      return scaleGrowthMmYr;
    }

    /**
     * Gets the pH used for the segment.
     *
     * @return the segment pH
     */
    public double getPH() {
      return pH;
    }
  }

  // ─── Inputs ─────────────────────────────────────────────

  private double[] temperatureC = new double[0];
  private double[] pressureBara = new double[0];
  private double[] velocityMs = new double[0];
  private double diameterM = 0.254;
  private double co2MoleFraction = 0.02;
  private double inhibitorEfficiency = 0.0;

  private double calciumMgL = 0.0;
  private double bicarbonateMgL = 0.0;
  private double sulphateMgL = 0.0;
  private double bariumMgL = 0.0;
  private double sodiumMgL = 0.0;
  private double chlorideMgL = 0.0;
  private double brinePH = -1.0;

  // ─── Outputs ────────────────────────────────────────────

  private final List<SegmentResult> segments = new ArrayList<SegmentResult>();
  private int worstCorrosionIndex = -1;
  private int worstScaleIndex = -1;
  private double maxCorrosionRateMmYr = 0.0;
  private double maxScaleSI = Double.NEGATIVE_INFINITY;
  private boolean evaluated = false;

  /**
   * Sets the per-segment temperature, pressure and velocity profiles. The three arrays must be the same length.
   *
   * @param temperatureC segment temperatures in degrees Celsius
   * @param pressureBara segment pressures in bara
   * @param velocityMs segment velocities in m/s
   * @return this for chaining
   */
  public PipeSegmentIntegrity setProfile(double[] temperatureC, double[] pressureBara, double[] velocityMs) {
    if (temperatureC == null || pressureBara == null || velocityMs == null) {
      throw new IllegalArgumentException("profile arrays must not be null");
    }
    if (temperatureC.length != pressureBara.length || temperatureC.length != velocityMs.length) {
      throw new IllegalArgumentException("profile arrays must have the same length");
    }
    this.temperatureC = temperatureC.clone();
    this.pressureBara = pressureBara.clone();
    this.velocityMs = velocityMs.clone();
    this.evaluated = false;
    return this;
  }

  /**
   * Extracts the temperature, pressure, mixture-velocity profile and diameter from a run {@link PipeBeggsAndBrills} so
   * the integrity screening follows the same discretisation as the hydraulic model.
   *
   * <p>
   * The pipe must already have been run so its profile lists are populated. Segment temperatures are converted from
   * Kelvin to degrees Celsius and the mixture superficial velocity is used as the segment velocity. The pipe diameter
   * overrides any value previously set via {@link #setPipeAndGas(double, double)}; the CO2 mole fraction and brine
   * chemistry must still be supplied separately.
   * </p>
   *
   * @param pipe a run Beggs-and-Brills pipeline; must not be null
   * @return this for chaining
   */
  public PipeSegmentIntegrity fromPipe(PipeBeggsAndBrills pipe) {
    if (pipe == null) {
      throw new IllegalArgumentException("pipe must not be null");
    }
    int n = Math.min(pipe.getTemperatureProfileList().size(), pipe.getPressureProfileList().size());
    double[] t = new double[n];
    double[] p = new double[n];
    double[] v = new double[n];
    for (int i = 0; i < n; i++) {
      Double tSeg = pipe.getSegmentTemperature(i);
      Double pSeg = pipe.getSegmentPressure(i);
      Double vSeg = pipe.getSegmentMixtureSuperficialVelocity(i);
      t[i] = (tSeg == null) ? 25.0 : (tSeg.doubleValue() - 273.15);
      p[i] = (pSeg == null) ? 1.0 : pSeg.doubleValue();
      v[i] = (vSeg == null) ? 0.0 : vSeg.doubleValue();
    }
    setProfile(t, p, v);
    if (pipe.getDiameter() > 0.0) {
      this.diameterM = pipe.getDiameter();
    }
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the pipe internal diameter and gas CO2 mole fraction.
   *
   * @param diameterM pipe internal diameter in metres
   * @param co2MoleFraction CO2 mole fraction in the gas phase (0 to 1)
   * @return this for chaining
   */
  public PipeSegmentIntegrity setPipeAndGas(double diameterM, double co2MoleFraction) {
    this.diameterM = Math.max(0.001, diameterM);
    this.co2MoleFraction = Math.max(0.0, Math.min(1.0, co2MoleFraction));
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the chemical inhibitor efficiency applied uniformly along the line.
   *
   * @param efficiency inhibitor efficiency (0 = none, 1 = perfect)
   * @return this for chaining
   */
  public PipeSegmentIntegrity setInhibitorEfficiency(double efficiency) {
    this.inhibitorEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the shared brine chemistry used to evaluate the CaCO3 saturation index at each segment.
   *
   * @param caMgL calcium [mg/L]
   * @param hco3MgL bicarbonate [mg/L]
   * @param so4MgL sulphate [mg/L]
   * @param baMgL barium [mg/L]
   * @param naMgL sodium [mg/L]
   * @param clMgL chloride [mg/L]
   * @return this for chaining
   */
  public PipeSegmentIntegrity setBrineChemistry(double caMgL, double hco3MgL, double so4MgL, double baMgL, double naMgL,
      double clMgL) {
    this.calciumMgL = caMgL;
    this.bicarbonateMgL = hco3MgL;
    this.sulphateMgL = so4MgL;
    this.bariumMgL = baMgL;
    this.sodiumMgL = naMgL;
    this.chlorideMgL = clMgL;
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the brine pH. Set to -1 (the default) to use a CO2-water correlation per segment.
   *
   * @param pH the aqueous pH, or -1 to correlate from local CO2 partial pressure
   * @return this for chaining
   */
  public PipeSegmentIntegrity setBrinePH(double pH) {
    this.brinePH = pH;
    this.evaluated = false;
    return this;
  }

  /**
   * Walks each segment, evaluating the CO2 corrosion rate and CaCO3 scale saturation, and records the worst segments.
   *
   * @return this for chaining
   */
  public PipeSegmentIntegrity evaluate() {
    segments.clear();
    worstCorrosionIndex = -1;
    worstScaleIndex = -1;
    maxCorrosionRateMmYr = 0.0;
    maxScaleSI = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < temperatureC.length; i++) {
      double tC = temperatureC[i];
      double pBara = pressureBara[i];
      double vMs = velocityMs[i];
      double pCO2 = co2MoleFraction * pBara;

      double segmentPH = (brinePH > 0) ? brinePH : RobustAqueousPH.correlationPH(tC, pCO2);

      NorsokM506CorrosionRate corrosion = new NorsokM506CorrosionRate();
      corrosion.setTemperatureCelsius(tC);
      corrosion.setTotalPressureBara(pBara);
      corrosion.setCO2MoleFraction(co2MoleFraction);
      corrosion.setFlowVelocityMs(vMs);
      corrosion.setPipeDiameterM(diameterM);
      corrosion.setInhibitorEfficiency(inhibitorEfficiency);
      corrosion.setActualPH(segmentPH);
      corrosion.setBicarbonateConcentrationMgL(bicarbonateMgL);
      corrosion.calculate();
      double corrosionRate = corrosion.getCorrectedCorrosionRate();

      ElectrolyteScaleCalculator scale = new ElectrolyteScaleCalculator();
      scale.setTemperatureCelsius(tC).setPressureBara(pBara).setPH(segmentPH);
      scale.setCations(calciumMgL, bariumMgL, 0.0, 0.0, sodiumMgL, 0.0, 0.0);
      scale.setAnions(chlorideMgL, sulphateMgL, bicarbonateMgL, 0.0);
      scale.calculate();
      double si = scale.getCaCO3SaturationIndex();

      double growth = new ScaleKinetics().setSaturationIndex(si).evaluate().getEffectiveGrowthRateMmYr();

      segments.add(new SegmentResult(i, tC, pBara, vMs, corrosionRate, si, growth, segmentPH));

      if (corrosionRate > maxCorrosionRateMmYr) {
        maxCorrosionRateMmYr = corrosionRate;
        worstCorrosionIndex = i;
      }
      if (si > maxScaleSI) {
        maxScaleSI = si;
        worstScaleIndex = i;
      }
    }

    evaluated = true;
    return this;
  }

  /**
   * Gets the list of per-segment results.
   *
   * @return the per-segment integrity results
   */
  public List<SegmentResult> getSegments() {
    ensureEvaluated();
    return segments;
  }

  /**
   * Gets the index of the segment with the highest corrosion rate.
   *
   * @return the worst corrosion segment index, or -1 if no segments were evaluated
   */
  public int getWorstCorrosionIndex() {
    ensureEvaluated();
    return worstCorrosionIndex;
  }

  /**
   * Gets the index of the segment with the highest scale saturation index.
   *
   * @return the worst scale segment index, or -1 if no segments were evaluated
   */
  public int getWorstScaleIndex() {
    ensureEvaluated();
    return worstScaleIndex;
  }

  /**
   * Gets the maximum corrosion rate across all segments.
   *
   * @return the maximum corrosion rate in mm/yr
   */
  public double getMaxCorrosionRateMmYr() {
    ensureEvaluated();
    return maxCorrosionRateMmYr;
  }

  /**
   * Gets the maximum CaCO3 saturation index across all segments.
   *
   * @return the maximum CaCO3 saturation index
   */
  public double getMaxScaleSI() {
    ensureEvaluated();
    return maxScaleSI;
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
   * Serialises the per-segment results and worst-case summary to a JSON string.
   *
   * @return a pretty-printed JSON representation
   */
  public String toJson() {
    ensureEvaluated();
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("worstCorrosionIndex", worstCorrosionIndex);
    map.put("maxCorrosionRateMmYr", maxCorrosionRateMmYr);
    map.put("worstScaleIndex", worstScaleIndex);
    map.put("maxScaleSI", maxScaleSI);
    map.put("segments", segments);
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(map);
  }
}
