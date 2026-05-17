package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Computes mineral scale saturation indices along a flowline given a T/P profile and water
 * chemistry.
 *
 * <p>
 * In subsea and onshore pipelines, temperature and pressure change along the flowline due to heat
 * loss and friction. This class discretises the pipeline into segments and evaluates scale risk at
 * each location using {@link ScalePredictionCalculator}.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * FlowlineScaleProfile profile = new FlowlineScaleProfile();
 * profile.setWaterChemistry(400, 10, 5, 0, 150, 10, 35000, 2.0, 6.5);
 * profile.setInletConditions(80.0, 200.0);
 * profile.setOutletConditions(20.0, 100.0);
 * profile.setNumberOfSegments(20);
 * profile.calculate();
 * System.out.println(profile.toJson());
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class FlowlineScaleProfile implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // --- Water chemistry (mg/L) ---
  private double calciumMgL = 400.0;
  private double bariumMgL = 10.0;
  private double strontiumMgL = 5.0;
  private double ironMgL = 0.0;
  private double magnesiumMgL = 0.0;
  private double sodiumMgL = 0.0;
  private double bicarbonateMgL = 150.0;
  private double sulphateMgL = 10.0;
  private double totalDissolvedSolids = 35000.0;
  private double co2PartialPressureBar = 2.0;
  private double pH = 6.5;
  private boolean autoPH = true;

  // --- Flowline conditions ---
  private double inletTempC = 80.0;
  private double outletTempC = 20.0;
  private double inletPressBar = 200.0;
  private double outletPressBar = 100.0;
  private int numberOfSegments = 20;

  /** Results at each segment. */
  private List<SegmentResult> results = new ArrayList<SegmentResult>();

  /**
   * Creates a new FlowlineScaleProfile with default parameters.
   */
  public FlowlineScaleProfile() {}

  /**
   * Sets the water chemistry for the flowline.
   *
   * @param caMgL calcium mg/L
   * @param baMgL barium mg/L
   * @param srMgL strontium mg/L
   * @param feMgL iron mg/L
   * @param hco3MgL bicarbonate mg/L
   * @param so4MgL sulphate mg/L
   * @param tdsMgL TDS mg/L
   * @param co2pp CO2 partial pressure bar
   * @param pH pH value
   */
  public void setWaterChemistry(double caMgL, double baMgL, double srMgL, double feMgL,
      double hco3MgL, double so4MgL, double tdsMgL, double co2pp, double pH) {
    this.calciumMgL = caMgL;
    this.bariumMgL = baMgL;
    this.strontiumMgL = srMgL;
    this.ironMgL = feMgL;
    this.bicarbonateMgL = hco3MgL;
    this.sulphateMgL = so4MgL;
    this.totalDissolvedSolids = tdsMgL;
    this.co2PartialPressureBar = co2pp;
    this.pH = pH;
  }

  /**
   * Sets magnesium and sodium concentrations.
   *
   * @param mgMgL magnesium mg/L
   * @param naMgL sodium mg/L
   */
  public void setMgNaConcentrations(double mgMgL, double naMgL) {
    this.magnesiumMgL = mgMgL;
    this.sodiumMgL = naMgL;
  }

  /**
   * Sets the inlet (wellhead) conditions.
   *
   * @param tempC temperature Celsius
   * @param pressBar pressure bara
   */
  public void setInletConditions(double tempC, double pressBar) {
    this.inletTempC = tempC;
    this.inletPressBar = pressBar;
  }

  /**
   * Sets the outlet (host) conditions.
   *
   * @param tempC temperature Celsius
   * @param pressBar pressure bara
   */
  public void setOutletConditions(double tempC, double pressBar) {
    this.outletTempC = tempC;
    this.outletPressBar = pressBar;
  }

  /**
   * Sets the number of segments to discretise the flowline into.
   *
   * @param n number of segments (must be at least 2)
   */
  public void setNumberOfSegments(int n) {
    this.numberOfSegments = Math.max(2, n);
  }

  /**
   * Enables or disables automatic pH estimation.
   *
   * @param auto true to enable auto pH
   */
  public void setAutoPH(boolean auto) {
    this.autoPH = auto;
  }

  /**
   * Runs the scale profile calculation.
   */
  public void calculate() {
    results.clear();

    for (int i = 0; i <= numberOfSegments; i++) {
      double fraction = (double) i / (double) numberOfSegments;
      double tempC = inletTempC + fraction * (outletTempC - inletTempC);
      double pressBar = inletPressBar + fraction * (outletPressBar - inletPressBar);

      ScalePredictionCalculator calc = new ScalePredictionCalculator();
      calc.setCalciumConcentration(calciumMgL);
      calc.setBariumConcentration(bariumMgL);
      calc.setStrontiumConcentration(strontiumMgL);
      calc.setIronConcentration(ironMgL);
      calc.setMagnesiumConcentration(magnesiumMgL);
      calc.setSodiumConcentration(sodiumMgL);
      calc.setBicarbonateConcentration(bicarbonateMgL);
      calc.setSulphateConcentration(sulphateMgL);
      calc.setTotalDissolvedSolids(totalDissolvedSolids);
      calc.setTemperatureCelsius(tempC);
      calc.setPressureBara(pressBar);
      calc.setCO2PartialPressure(co2PartialPressureBar);
      if (autoPH) {
        calc.enableAutoPH();
      }
      calc.calculate();

      SegmentResult sr = new SegmentResult();
      sr.distanceFraction = fraction;
      sr.temperatureC = tempC;
      sr.pressureBar = pressBar;
      sr.siCaCO3 = calc.getCaCO3SaturationIndex();
      sr.siBaSO4 = calc.getBaSO4SaturationIndex();
      sr.siSrSO4 = calc.getSrSO4SaturationIndex();
      sr.siCaSO4 = calc.getCaSO4SaturationIndex();
      sr.siFeCO3 = calc.getFeCO3SaturationIndex();
      results.add(sr);
    }
  }

  /**
   * Returns the calculation results.
   *
   * @return list of segment results
   */
  public List<SegmentResult> getResults() {
    return results;
  }

  /**
   * Returns the maximum SI for a given scale across all segments.
   *
   * @param scaleName one of CaCO3, BaSO4, SrSO4, CaSO4, FeCO3
   * @return maximum SI value
   */
  public double getMaxSI(String scaleName) {
    double maxSI = Double.NEGATIVE_INFINITY;
    for (SegmentResult sr : results) {
      double si = getSIByName(sr, scaleName);
      if (!Double.isNaN(si) && si > maxSI) {
        maxSI = si;
      }
    }
    return maxSI;
  }

  /**
   * Returns the segment result at a given index.
   *
   * @param index segment index (0-based)
   * @return segment result
   */
  public SegmentResult getSegmentResult(int index) {
    return results.get(index);
  }

  /**
   * Returns the SI for a named scale at a segment result.
   *
   * @param sr the segment result
   * @param name scale name
   * @return SI value
   */
  private double getSIByName(SegmentResult sr, String name) {
    if ("CaCO3".equalsIgnoreCase(name)) {
      return sr.siCaCO3;
    }
    if ("BaSO4".equalsIgnoreCase(name)) {
      return sr.siBaSO4;
    }
    if ("SrSO4".equalsIgnoreCase(name)) {
      return sr.siSrSO4;
    }
    if ("CaSO4".equalsIgnoreCase(name)) {
      return sr.siCaSO4;
    }
    if ("FeCO3".equalsIgnoreCase(name)) {
      return sr.siFeCO3;
    }
    return Double.NaN;
  }

  /**
   * Returns a comprehensive JSON report.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();

    Map<String, Object> conditions = new LinkedHashMap<String, Object>();
    conditions.put("inletTempC", inletTempC);
    conditions.put("outletTempC", outletTempC);
    conditions.put("inletPressBar", inletPressBar);
    conditions.put("outletPressBar", outletPressBar);
    conditions.put("numberOfSegments", numberOfSegments);
    report.put("flowlineConditions", conditions);

    Map<String, Object> maxSI = new LinkedHashMap<String, Object>();
    maxSI.put("CaCO3", getMaxSI("CaCO3"));
    maxSI.put("BaSO4", getMaxSI("BaSO4"));
    maxSI.put("SrSO4", getMaxSI("SrSO4"));
    maxSI.put("CaSO4", getMaxSI("CaSO4"));
    maxSI.put("FeCO3", getMaxSI("FeCO3"));
    report.put("maxSaturationIndices", maxSI);

    List<Map<String, Object>> segments = new ArrayList<Map<String, Object>>();
    for (SegmentResult sr : results) {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("distanceFraction", sr.distanceFraction);
      m.put("temperatureC", sr.temperatureC);
      m.put("pressureBar", sr.pressureBar);
      m.put("SI_CaCO3", formatSI(sr.siCaCO3));
      m.put("SI_BaSO4", formatSI(sr.siBaSO4));
      m.put("SI_SrSO4", formatSI(sr.siSrSO4));
      m.put("SI_CaSO4", formatSI(sr.siCaSO4));
      m.put("SI_FeCO3", formatSI(sr.siFeCO3));
      segments.add(m);
    }
    report.put("segmentResults", segments);

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(report);
  }

  /**
   * Formats SI value for JSON.
   *
   * @param si saturation index
   * @return formatted value or "N/A"
   */
  private Object formatSI(double si) {
    return Double.isNaN(si) ? "N/A" : si;
  }

  /**
   * Result at a single pipeline segment.
   */
  public static class SegmentResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Fraction of total distance (0.0 = inlet, 1.0 = outlet). */
    public double distanceFraction;
    /** Temperature at segment in Celsius. */
    public double temperatureC;
    /** Pressure at segment in bara. */
    public double pressureBar;
    /** CaCO3 saturation index. */
    public double siCaCO3;
    /** BaSO4 saturation index. */
    public double siBaSO4;
    /** SrSO4 saturation index. */
    public double siSrSO4;
    /** CaSO4 saturation index. */
    public double siCaSO4;
    /** FeCO3 saturation index. */
    public double siFeCO3;
  }
}
