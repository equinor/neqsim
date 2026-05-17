package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Screens two water sources for compatibility by evaluating mineral scale risk at different mixing
 * ratios.
 *
 * <p>
 * Water compatibility is critical in oilfield operations when formation water meets injection water
 * (e.g., seawater). This class mixes the two waters at varying ratios and evaluates the scale risk
 * at each ratio using the {@link ScalePredictionCalculator}.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * WaterCompatibilityScreener screener = new WaterCompatibilityScreener();
 * screener.setFormationWater(400, 10, 5, 0, 150, 10, 35000, 80, 100, 2.0, 6.5);
 * screener.setInjectionWater(20, 0, 0, 0, 100, 2700, 35000, 80, 100, 0.5, 7.0);
 * screener.setMixingRatios(new double[] {0, 10, 20, 30, 50, 70, 90, 100});
 * screener.calculate();
 * System.out.println(screener.toJson());
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class WaterCompatibilityScreener implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // --- Formation water properties (mg/L unless noted) ---
  private double fwCalcium = 400.0;
  private double fwBarium = 10.0;
  private double fwStrontium = 5.0;
  private double fwIron = 0.0;
  private double fwMagnesium = 0.0;
  private double fwSodium = 0.0;
  private double fwBicarbonate = 150.0;
  private double fwSulphate = 10.0;
  private double fwTDS = 35000.0;
  private double fwTempC = 80.0;
  private double fwPressBar = 100.0;
  private double fwCO2pp = 2.0;
  private double fwPH = 6.5;

  // --- Injection water properties (mg/L unless noted) ---
  private double iwCalcium = 20.0;
  private double iwBarium = 0.0;
  private double iwStrontium = 0.0;
  private double iwIron = 0.0;
  private double iwMagnesium = 0.0;
  private double iwSodium = 0.0;
  private double iwBicarbonate = 100.0;
  private double iwSulphate = 2700.0;
  private double iwTDS = 35000.0;
  private double iwTempC = 80.0;
  private double iwPressBar = 100.0;
  private double iwCO2pp = 0.5;
  private double iwPH = 7.0;

  /** Mixing ratios as percentage of injection water (0-100). */
  private double[] mixingRatios = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

  /** Results at each mixing ratio. */
  private List<MixingResult> results = new ArrayList<MixingResult>();

  /** Worst-case mixing ratio. */
  private double worstCaseRatio = -1;

  /** Worst-case scale type. */
  private String worstCaseScale = "";

  /** Worst-case SI value. */
  private double worstCaseSI = Double.NEGATIVE_INFINITY;

  /**
   * Creates a new WaterCompatibilityScreener with default parameters.
   */
  public WaterCompatibilityScreener() {}

  /**
   * Sets the formation water composition.
   *
   * @param caMgL calcium mg/L
   * @param baMgL barium mg/L
   * @param srMgL strontium mg/L
   * @param feMgL iron mg/L
   * @param hco3MgL bicarbonate mg/L
   * @param so4MgL sulphate mg/L
   * @param tdsMgL TDS mg/L
   * @param tempC temperature Celsius
   * @param pressBar pressure bara
   * @param co2pp CO2 partial pressure bar
   * @param pH pH
   */
  public void setFormationWater(double caMgL, double baMgL, double srMgL, double feMgL,
      double hco3MgL, double so4MgL, double tdsMgL, double tempC, double pressBar, double co2pp,
      double pH) {
    this.fwCalcium = caMgL;
    this.fwBarium = baMgL;
    this.fwStrontium = srMgL;
    this.fwIron = feMgL;
    this.fwBicarbonate = hco3MgL;
    this.fwSulphate = so4MgL;
    this.fwTDS = tdsMgL;
    this.fwTempC = tempC;
    this.fwPressBar = pressBar;
    this.fwCO2pp = co2pp;
    this.fwPH = pH;
  }

  /**
   * Sets the injection water composition.
   *
   * @param caMgL calcium mg/L
   * @param baMgL barium mg/L
   * @param srMgL strontium mg/L
   * @param feMgL iron mg/L
   * @param hco3MgL bicarbonate mg/L
   * @param so4MgL sulphate mg/L
   * @param tdsMgL TDS mg/L
   * @param tempC temperature Celsius
   * @param pressBar pressure bara
   * @param co2pp CO2 partial pressure bar
   * @param pH pH
   */
  public void setInjectionWater(double caMgL, double baMgL, double srMgL, double feMgL,
      double hco3MgL, double so4MgL, double tdsMgL, double tempC, double pressBar, double co2pp,
      double pH) {
    this.iwCalcium = caMgL;
    this.iwBarium = baMgL;
    this.iwStrontium = srMgL;
    this.iwIron = feMgL;
    this.iwBicarbonate = hco3MgL;
    this.iwSulphate = so4MgL;
    this.iwTDS = tdsMgL;
    this.iwTempC = tempC;
    this.iwPressBar = pressBar;
    this.iwCO2pp = co2pp;
    this.iwPH = pH;
  }

  /**
   * Sets the magnesium and sodium concentrations for formation water.
   *
   * @param fwMgMgL formation water magnesium mg/L
   * @param fwNaMgL formation water sodium mg/L
   */
  public void setFormationWaterMgNa(double fwMgMgL, double fwNaMgL) {
    this.fwMagnesium = fwMgMgL;
    this.fwSodium = fwNaMgL;
  }

  /**
   * Sets the magnesium and sodium concentrations for injection water.
   *
   * @param iwMgMgL injection water magnesium mg/L
   * @param iwNaMgL injection water sodium mg/L
   */
  public void setInjectionWaterMgNa(double iwMgMgL, double iwNaMgL) {
    this.iwMagnesium = iwMgMgL;
    this.iwSodium = iwNaMgL;
  }

  /**
   * Sets the mixing ratios to evaluate.
   *
   * @param ratios array of injection water percentages (0-100)
   */
  public void setMixingRatios(double[] ratios) {
    this.mixingRatios = ratios;
  }

  /**
   * Runs the compatibility screen at all mixing ratios.
   */
  public void calculate() {
    results.clear();
    worstCaseRatio = -1;
    worstCaseScale = "";
    worstCaseSI = Double.NEGATIVE_INFINITY;

    for (double ratio : mixingRatios) {
      double frac = ratio / 100.0;

      // Linear mixing of concentrations
      double caMix = fwCalcium * (1 - frac) + iwCalcium * frac;
      double baMix = fwBarium * (1 - frac) + iwBarium * frac;
      double srMix = fwStrontium * (1 - frac) + iwStrontium * frac;
      double feMix = fwIron * (1 - frac) + iwIron * frac;
      double mgMix = fwMagnesium * (1 - frac) + iwMagnesium * frac;
      double naMix = fwSodium * (1 - frac) + iwSodium * frac;
      double hco3Mix = fwBicarbonate * (1 - frac) + iwBicarbonate * frac;
      double so4Mix = fwSulphate * (1 - frac) + iwSulphate * frac;
      double tdsMix = fwTDS * (1 - frac) + iwTDS * frac;
      double tempMix = fwTempC * (1 - frac) + iwTempC * frac;
      double pressMix = fwPressBar * (1 - frac) + iwPressBar * frac;
      double co2Mix = fwCO2pp * (1 - frac) + iwCO2pp * frac;

      ScalePredictionCalculator calc = new ScalePredictionCalculator();
      calc.setCalciumConcentration(caMix);
      calc.setBariumConcentration(baMix);
      calc.setStrontiumConcentration(srMix);
      calc.setIronConcentration(feMix);
      calc.setMagnesiumConcentration(mgMix);
      calc.setSodiumConcentration(naMix);
      calc.setBicarbonateConcentration(hco3Mix);
      calc.setSulphateConcentration(so4Mix);
      calc.setTotalDissolvedSolids(tdsMix);
      calc.setTemperatureCelsius(tempMix);
      calc.setPressureBara(pressMix);
      calc.setCO2PartialPressure(co2Mix);
      calc.enableAutoPH();
      calc.calculate();

      MixingResult mr = new MixingResult();
      mr.injectionWaterPct = ratio;
      mr.siCaCO3 = calc.getCaCO3SaturationIndex();
      mr.siBaSO4 = calc.getBaSO4SaturationIndex();
      mr.siSrSO4 = calc.getSrSO4SaturationIndex();
      mr.siCaSO4 = calc.getCaSO4SaturationIndex();
      mr.siFeCO3 = calc.getFeCO3SaturationIndex();
      results.add(mr);

      // Track worst case
      checkWorstCase(mr.siCaCO3, ratio, "CaCO3");
      checkWorstCase(mr.siBaSO4, ratio, "BaSO4");
      checkWorstCase(mr.siSrSO4, ratio, "SrSO4");
      checkWorstCase(mr.siCaSO4, ratio, "CaSO4");
      checkWorstCase(mr.siFeCO3, ratio, "FeCO3");
    }
  }

  /**
   * Checks if a given SI is the worst case seen so far.
   *
   * @param si saturation index
   * @param ratio mixing ratio
   * @param scaleName scale type name
   */
  private void checkWorstCase(double si, double ratio, String scaleName) {
    if (!Double.isNaN(si) && si > worstCaseSI) {
      worstCaseSI = si;
      worstCaseRatio = ratio;
      worstCaseScale = scaleName;
    }
  }

  /**
   * Returns the worst-case mixing ratio (% injection water).
   *
   * @return mixing ratio percentage
   */
  public double getWorstCaseRatio() {
    return worstCaseRatio;
  }

  /**
   * Returns the worst-case scale type.
   *
   * @return scale mineral name
   */
  public String getWorstCaseScale() {
    return worstCaseScale;
  }

  /**
   * Returns the worst-case saturation index.
   *
   * @return SI value
   */
  public double getWorstCaseSI() {
    return worstCaseSI;
  }

  /**
   * Returns the list of mixing results.
   *
   * @return list of MixingResult
   */
  public List<MixingResult> getResults() {
    return results;
  }

  /**
   * Returns a comprehensive JSON report.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();

    Map<String, Object> worstCase = new LinkedHashMap<String, Object>();
    worstCase.put("injectionWaterPct", worstCaseRatio);
    worstCase.put("scaleType", worstCaseScale);
    worstCase.put("saturationIndex", worstCaseSI);
    report.put("worstCase", worstCase);

    List<Map<String, Object>> mixResults = new ArrayList<Map<String, Object>>();
    for (MixingResult mr : results) {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("injectionWaterPct", mr.injectionWaterPct);
      m.put("SI_CaCO3", formatSI(mr.siCaCO3));
      m.put("SI_BaSO4", formatSI(mr.siBaSO4));
      m.put("SI_SrSO4", formatSI(mr.siSrSO4));
      m.put("SI_CaSO4", formatSI(mr.siCaSO4));
      m.put("SI_FeCO3", formatSI(mr.siFeCO3));
      mixResults.add(m);
    }
    report.put("mixingResults", mixResults);

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
   * Result at a single mixing ratio.
   */
  public static class MixingResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Injection water percentage. */
    public double injectionWaterPct;
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
