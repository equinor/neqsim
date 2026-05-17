package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.chemistry.util.StandardsRegistry;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

/**
 * Integrates scale-deposition risk along a {@link PipeBeggsAndBrills} segmented pipeline by
 * evaluating an {@link ScalePredictionCalculator} at each segment temperature and pressure, then
 * estimating cumulative deposit thickness on the wall and time-to-blockage.
 *
 * <p>
 * This is a screening-level model (NACE TM0374, NORSOK M-001 informational) intended for relative
 * ranking of pipeline segments rather than absolute mass-balance prediction.
 *
 * <p>
 * The deposition driving force per segment is taken as
 * {@code max(0, exp(SI*ln10) - 1) * baseRate_kg_m2_yr}, where {@code SI} is the CaCO3 saturation
 * index (other phases optionally added by client) and {@code baseRate_kg_m2_yr} defaults to a
 * field-typical 0.5 kg/(m^2.yr). This keeps the model tunable without requiring kinetic data the
 * user usually does not have at screening stage.
 *
 * @author ESOL
 * @version 1.0
 */
public class ScaleDepositionAccumulator implements Serializable {

  private static final long serialVersionUID = 1000L;

  private final transient PipeBeggsAndBrills pipe;
  private double calciumMgL = 0.0;
  private double bicarbonateMgL = 0.0;
  private double sulphateMgL = 0.0;
  private double bariumMgL = 0.0;
  private double sodiumMgL = 0.0;
  private double tdsMgL = 0.0;
  private double pH = 6.0;
  private double co2PartialPressureBar = 0.0;
  private double scaleDensityKgM3 = 2700.0;
  private double baseRateKgM2Yr = 0.5;
  private double inhibitorEfficiency = 0.0;
  private double serviceYears = 1.0;

  private final List<Double> segmentSI = new ArrayList<Double>();
  private final List<Double> segmentDepositionMassKg = new ArrayList<Double>();
  private final List<Double> segmentThicknessMm = new ArrayList<Double>();
  private double totalDepositionMassKg = 0.0;
  private double maxThicknessMm = 0.0;
  private double timeToBlockageYears = Double.POSITIVE_INFINITY;
  private boolean evaluated = false;

  /**
   * Constructs the accumulator bound to a Beggs-and-Brills pipeline.
   *
   * @param pipe the segmented pipeline (must already have been run)
   */
  public ScaleDepositionAccumulator(PipeBeggsAndBrills pipe) {
    this.pipe = pipe;
  }

  /**
   * Sets aqueous-phase chemistry used to evaluate the saturation index per segment.
   *
   * @param caMgL calcium concentration [mg/L]
   * @param hcoMgL bicarbonate concentration [mg/L]
   * @param soMgL sulphate concentration [mg/L]
   * @param baMgL barium concentration [mg/L]
   * @param naMgL sodium concentration [mg/L]
   * @param tdsMgL total dissolved solids [mg/L]
   * @return this for chaining
   */
  public ScaleDepositionAccumulator setBrineChemistry(double caMgL, double hcoMgL, double soMgL,
      double baMgL, double naMgL, double tdsMgL) {
    this.calciumMgL = caMgL;
    this.bicarbonateMgL = hcoMgL;
    this.sulphateMgL = soMgL;
    this.bariumMgL = baMgL;
    this.sodiumMgL = naMgL;
    this.tdsMgL = tdsMgL;
    return this;
  }

  /**
   * Sets pH and CO2 partial pressure used by the scale calculator.
   *
   * @param pH pH of the aqueous phase
   * @param pco2Bar CO2 partial pressure [bar]
   * @return this for chaining
   */
  public ScaleDepositionAccumulator setpHAndCo2(double pH, double pco2Bar) {
    this.pH = pH;
    this.co2PartialPressureBar = pco2Bar;
    return this;
  }

  /**
   * Sets the inhibitor efficiency applied uniformly along the pipeline (0..1).
   *
   * @param efficiency fractional efficiency reducing deposition rate
   * @return this for chaining
   */
  public ScaleDepositionAccumulator setInhibitorEfficiency(double efficiency) {
    this.inhibitorEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
    return this;
  }

  /**
   * Sets the service exposure time used to scale deposition mass and thickness.
   *
   * @param years service time in years
   * @return this for chaining
   */
  public ScaleDepositionAccumulator setServiceYears(double years) {
    this.serviceYears = Math.max(0.0, years);
    return this;
  }

  /**
   * Sets the assumed scale density (default 2700 kg/m^3 for CaCO3).
   *
   * @param density kg/m^3
   * @return this for chaining
   */
  public ScaleDepositionAccumulator setScaleDensity(double density) {
    this.scaleDensityKgM3 = density;
    return this;
  }

  /**
   * Sets the base deposition rate at SI = 1.0 (default 0.5 kg/m^2/yr).
   *
   * @param rate kg/(m^2.yr)
   * @return this for chaining
   */
  public ScaleDepositionAccumulator setBaseRate(double rate) {
    this.baseRateKgM2Yr = rate;
    return this;
  }

  /**
   * Walks each Beggs-and-Brills segment, evaluates the CaCO3 saturation index, and integrates
   * deposit mass and wall thickness. Sets timeToBlockage as the years until the maximum-thickness
   * segment occludes 50% of the pipe cross-section.
   *
   * @return this for chaining
   */
  public ScaleDepositionAccumulator evaluate() {
    segmentSI.clear();
    segmentDepositionMassKg.clear();
    segmentThicknessMm.clear();
    totalDepositionMassKg = 0.0;
    maxThicknessMm = 0.0;
    int n = pipe.getNumberOfIncrements();
    double diameter = pipe.getDiameter();
    double length = pipe.getLength();
    if (n <= 0 || diameter <= 0.0 || length <= 0.0) {
      evaluated = true;
      return this;
    }
    int profileSize =
        Math.min(pipe.getTemperatureProfileList().size(), pipe.getPressureProfileList().size());
    if (profileSize <= 0) {
      evaluated = true;
      return this;
    }
    double segmentLength = length / profileSize;
    double segmentArea = Math.PI * diameter * segmentLength;
    for (int i = 0; i < profileSize; i++) {
      Double tSeg = pipe.getSegmentTemperature(i);
      Double pSeg = pipe.getSegmentPressure(i);
      double tC = tSeg == null ? 25.0 : (tSeg.doubleValue() - 273.15);
      double pBara = pSeg == null ? 1.0 : pSeg.doubleValue();
      ScalePredictionCalculator sc = new ScalePredictionCalculator();
      sc.setTemperatureCelsius(tC);
      sc.setPressureBara(pBara);
      sc.setCO2PartialPressure(co2PartialPressureBar);
      sc.setCalciumConcentration(calciumMgL);
      sc.setBicarbonateConcentration(bicarbonateMgL);
      sc.setSulphateConcentration(sulphateMgL);
      sc.setBariumConcentration(bariumMgL);
      sc.setSodiumConcentration(sodiumMgL);
      sc.setTotalDissolvedSolids(tdsMgL);
      sc.setPH(pH);
      sc.calculate();
      double si = sc.getCaCO3SaturationIndex();
      segmentSI.add(si);
      double drivingForce = Math.max(0.0, Math.exp(si * Math.log(10.0)) - 1.0);
      double rateKgM2Yr = baseRateKgM2Yr * drivingForce * (1.0 - inhibitorEfficiency);
      double massKg = rateKgM2Yr * segmentArea * serviceYears;
      segmentDepositionMassKg.add(massKg);
      totalDepositionMassKg += massKg;
      // Convert mass to thickness assuming uniform deposition on inner wall
      double volumeM3 = massKg / scaleDensityKgM3;
      double thicknessM = volumeM3 / (Math.PI * diameter * segmentLength);
      double thicknessMm = thicknessM * 1000.0;
      segmentThicknessMm.add(thicknessMm);
      if (thicknessMm > maxThicknessMm) {
        maxThicknessMm = thicknessMm;
      }
    }
    // Time to blockage: 50% of inner radius blocked at the worst segment (linear extrapolation)
    double radiusMm = (diameter / 2.0) * 1000.0;
    double thresholdMm = radiusMm * 0.5;
    if (maxThicknessMm > 0.0 && serviceYears > 0.0) {
      timeToBlockageYears = thresholdMm * serviceYears / maxThicknessMm;
    } else {
      timeToBlockageYears = Double.POSITIVE_INFINITY;
    }
    evaluated = true;
    return this;
  }

  /**
   * Returns the per-segment CaCO3 saturation index list.
   *
   * @return list of SI values, one per pipe segment
   */
  public List<Double> getSegmentSaturationIndex() {
    return new ArrayList<Double>(segmentSI);
  }

  /**
   * Returns the per-segment cumulative deposition mass [kg].
   *
   * @return list of mass values
   */
  public List<Double> getSegmentDepositionMassKg() {
    return new ArrayList<Double>(segmentDepositionMassKg);
  }

  /**
   * Returns the per-segment scale thickness [mm].
   *
   * @return list of thicknesses
   */
  public List<Double> getSegmentThicknessMm() {
    return new ArrayList<Double>(segmentThicknessMm);
  }

  /**
   * Returns the integrated total deposition mass over all segments [kg].
   *
   * @return total mass
   */
  public double getTotalDepositionMassKg() {
    return totalDepositionMassKg;
  }

  /**
   * Returns the maximum segment thickness [mm].
   *
   * @return max thickness
   */
  public double getMaxThicknessMm() {
    return maxThicknessMm;
  }

  /**
   * Returns the projected time until the worst segment occludes half the pipe cross-section
   * [years]. Positive infinity when no deposition is expected.
   *
   * @return years to 50% occlusion
   */
  public double getTimeToBlockageYears() {
    return timeToBlockageYears;
  }

  /**
   * Returns true once {@link #evaluate()} has been invoked.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns the standards used by this accumulator.
   *
   * @return list of standard reference maps
   */
  public List<Map<String, Object>> getStandardsApplied() {
    return StandardsRegistry.toMapList(StandardsRegistry.NACE_TM0374,
        StandardsRegistry.NORSOK_M001);
  }

  /**
   * Returns a structured map representation suitable for JSON serialisation.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("totalDepositionMassKg", totalDepositionMassKg);
    map.put("maxThicknessMm", maxThicknessMm);
    map.put("timeToBlockageYears", timeToBlockageYears);
    map.put("serviceYears", serviceYears);
    map.put("inhibitorEfficiency", inhibitorEfficiency);
    map.put("segmentSaturationIndex", new ArrayList<Double>(segmentSI));
    map.put("segmentDepositionMassKg", new ArrayList<Double>(segmentDepositionMassKg));
    map.put("segmentThicknessMm", new ArrayList<Double>(segmentThicknessMm));
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns a JSON representation of the accumulator state.
   *
   * @return pretty-printed JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls()
        .serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
