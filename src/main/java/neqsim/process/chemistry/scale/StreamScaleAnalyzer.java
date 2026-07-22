package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.chemistry.util.StreamChemistryAdapter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.pvtsimulation.flowassurance.MultiMineralScaleEquilibrium;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

/**
 * Links the coupled multi-mineral scale equilibrium into a NeqSim process flowsheet.
 *
 * <p>
 * Given a process {@link StreamInterface} with an aqueous (produced-water) phase, this analyser extracts the aqueous
 * ion chemistry and operating conditions, runs the coupled {@link MultiMineralScaleEquilibrium} (barite, celestite,
 * anhydrite, calcite, siderite competing for shared sulphate / carbonate / calcium pools), and converts the per-litre
 * precipitated amounts into an absolute scaling <b>mass rate</b> (kg/day) using the stream's aqueous water throughput.
 * That mass rate is the quantity that matters for process operations, deposition-rate estimates and root cause
 * analysis.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * StreamScaleAnalyzer analyzer = StreamScaleAnalyzer.fromStream(producedWaterStream);
 * analyzer.analyze();
 * double bariteKgPerDay = analyzer.getScaleRateKgPerDay("BaSO4");
 * double totalKgPerDay = analyzer.getTotalScaleRateKgPerDay();
 * String dominant = analyzer.getDominantScale();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class StreamScaleAnalyzer implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final ScalePredictionCalculator predictor;
  private final MultiMineralScaleEquilibrium equilibrium;
  private double waterFlowLitrePerDay = 0.0;
  private boolean analyzed = false;

  /**
   * Wraps an existing, configured scale-prediction calculator.
   *
   * @param predictor a scale prediction calculator (conditions and ion concentrations already set)
   */
  public StreamScaleAnalyzer(ScalePredictionCalculator predictor) {
    this.predictor = predictor;
    this.equilibrium = new MultiMineralScaleEquilibrium(predictor);
  }

  /**
   * Builds an analyser from a flowsheet stream, extracting the aqueous ion chemistry, operating conditions and
   * produced-water throughput.
   *
   * @param stream a flowsheet stream that has been run (ideally an electrolyte-CPA fluid with an aqueous phase)
   * @return a configured analyser
   */
  public static StreamScaleAnalyzer fromStream(StreamInterface stream) {
    StreamChemistryAdapter ad = new StreamChemistryAdapter(stream);
    ScalePredictionCalculator p = new ScalePredictionCalculator();
    p.setTemperatureCelsius(ad.getTemperatureCelsius());
    p.setPressureBara(ad.getPressureBara());
    p.setCalciumConcentration(ad.getCalciumMgL());
    p.setBariumConcentration(ad.getBariumMgL());
    p.setStrontiumConcentration(ad.getStrontiumMgL());
    p.setIronConcentration(ad.getIronMgL());
    p.setMagnesiumConcentration(ad.getMagnesiumMgL());
    p.setSodiumConcentration(ad.getSodiumMgL());
    p.setBicarbonateConcentration(ad.getBicarbonateMgL());
    p.setSulphateConcentration(ad.getSulphateMgL());
    p.setTotalDissolvedSolids(ad.getTdsMgL());
    p.setCO2PartialPressure(ad.getPartialPressureBara("CO2"));
    p.enableAutoPH();
    StreamScaleAnalyzer analyzer = new StreamScaleAnalyzer(p);
    analyzer.waterFlowLitrePerDay = ad.getAqueousWaterFlowLitrePerDay();
    return analyzer;
  }

  /**
   * Selects the activity-coefficient model for the coupled equilibrium.
   *
   * @param model the activity model (Davies or B-dot)
   * @return this for chaining
   */
  public StreamScaleAnalyzer setActivityModel(MultiMineralScaleEquilibrium.ActivityModel model) {
    equilibrium.setActivityModel(model);
    analyzed = false;
    return this;
  }

  /**
   * Overrides the aqueous water throughput used to convert mg/L to a kg/day scaling rate.
   *
   * @param litrePerDay produced-water flow in litres per day
   * @return this for chaining
   */
  public StreamScaleAnalyzer setWaterFlowLitrePerDay(double litrePerDay) {
    this.waterFlowLitrePerDay = litrePerDay;
    return this;
  }

  /**
   * Runs the coupled scale equilibrium.
   *
   * @return this for chaining
   */
  public StreamScaleAnalyzer analyze() {
    equilibrium.setWaterVolume(1.0).solve();
    analyzed = true;
    return this;
  }

  /**
   * Ensures the analysis has run.
   */
  private void ensureAnalyzed() {
    if (!analyzed) {
      analyze();
    }
  }

  /**
   * Returns the scaling mass rate of a mineral in kg per day.
   *
   * @param mineralName mineral name (e.g. "BaSO4")
   * @return scaling rate in kg/day (0 if the mineral did not precipitate or water flow is unknown)
   */
  public double getScaleRateKgPerDay(String mineralName) {
    ensureAnalyzed();
    return equilibrium.getPrecipitatedMassMgPerL(mineralName) * waterFlowLitrePerDay / 1.0e6;
  }

  /**
   * Returns the total scaling mass rate across all minerals in kg per day.
   *
   * @return total scaling rate in kg/day
   */
  public double getTotalScaleRateKgPerDay() {
    ensureAnalyzed();
    return equilibrium.getTotalScaleMassMgPerL() * waterFlowLitrePerDay / 1.0e6;
  }

  /**
   * Returns the total precipitated scale concentration in mg per litre of water.
   *
   * @return total precipitated mass in mg/L
   */
  public double getTotalScaleMassMgPerL() {
    ensureAnalyzed();
    return equilibrium.getTotalScaleMassMgPerL();
  }

  /**
   * Returns the mineral that precipitates in the greatest mass, or {@code "none"} if no scale forms.
   *
   * @return dominant scale mineral name
   */
  public String getDominantScale() {
    ensureAnalyzed();
    String dominant = "none";
    double best = 0.0;
    for (Map.Entry<String, MultiMineralScaleEquilibrium.MineralResult> e : equilibrium.getResults().entrySet()) {
      double m = e.getValue().getPrecipitatedMassMgPerL();
      if (m > best) {
        best = m;
        dominant = e.getKey();
      }
    }
    return dominant;
  }

  /**
   * Returns whether any mineral scale precipitates.
   *
   * @return true if total precipitated mass is positive
   */
  public boolean hasScaling() {
    ensureAnalyzed();
    return getTotalScaleMassMgPerL() > 0.0;
  }

  /**
   * Returns the underlying coupled equilibrium solver.
   *
   * @return the equilibrium solver
   */
  public MultiMineralScaleEquilibrium getEquilibrium() {
    ensureAnalyzed();
    return equilibrium;
  }

  /**
   * Returns the underlying scale prediction calculator.
   *
   * @return the predictor
   */
  public ScalePredictionCalculator getPredictor() {
    return predictor;
  }

  /**
   * Returns the aqueous water flow used for the kg/day conversion.
   *
   * @return produced-water flow in litres per day
   */
  public double getWaterFlowLitrePerDay() {
    return waterFlowLitrePerDay;
  }

  /**
   * Returns a structured result map for JSON serialisation.
   *
   * @return ordered map of the scale analysis
   */
  public Map<String, Object> toMap() {
    ensureAnalyzed();
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("waterFlow_LPerDay", waterFlowLitrePerDay);
    map.put("totalScaleMass_mgL", getTotalScaleMassMgPerL());
    map.put("totalScaleRate_kgPerDay", getTotalScaleRateKgPerDay());
    map.put("dominantScale", getDominantScale());

    Map<String, Object> minerals = new LinkedHashMap<String, Object>();
    List<String> atRisk = new ArrayList<String>();
    for (Map.Entry<String, MultiMineralScaleEquilibrium.MineralResult> e : equilibrium.getResults().entrySet()) {
      MultiMineralScaleEquilibrium.MineralResult r = e.getValue();
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("initialSI", r.getInitialSI());
      m.put("finalSI", r.getFinalSI());
      m.put("precipitated_mgL", r.getPrecipitatedMassMgPerL());
      m.put("rate_kgPerDay", r.getPrecipitatedMassMgPerL() * waterFlowLitrePerDay / 1.0e6);
      minerals.put(e.getKey(), m);
      if (r.getPrecipitatedMassMgPerL() > 0.0) {
        atRisk.add(e.getKey());
      }
    }
    map.put("minerals", minerals);
    map.put("scalesAtRisk", atRisk);
    return map;
  }

  /**
   * Returns a JSON report of the stream scale analysis.
   *
   * @return JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(toMap());
  }
}
