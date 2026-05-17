package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

/**
 * Combines a {@link ScalePredictionCalculator} with one or more {@link ScaleInhibitorPerformance}
 * models to estimate the residual scaling risk after chemical treatment.
 *
 * <p>
 * The inhibitor reduces the effective supersaturation seen by the system. The residual saturation
 * index after treatment is approximated as:
 * </p>
 *
 * <pre>
 * {@code
 * SI_inhibited = SI_uninhibited + log10(1 - efficiency)
 * }
 * </pre>
 *
 * <p>
 * If efficiency = 0 the residual SI equals the uninhibited SI; if efficiency = 1 the residual SI
 * approaches negative infinity (no scale). The model assumes the inhibitor acts on nucleation /
 * growth kinetics, not on thermodynamic equilibrium — so this is a screening-level risk metric.
 * </p>
 *
 * <p>
 * Pattern: build with the prediction calculator, register inhibitors per scale type, call
 * {@link #evaluate()}, then read residual SIs and the verdict via {@link #toMap()}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ScaleControlAssessor implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final ScalePredictionCalculator predictor;
  private final Map<ScaleInhibitorPerformance.ScaleType, ScaleInhibitorPerformance> inhibitors =
      new LinkedHashMap<ScaleInhibitorPerformance.ScaleType, ScaleInhibitorPerformance>();
  private final Map<ScaleInhibitorPerformance.ScaleType, Double> residualSI =
      new LinkedHashMap<ScaleInhibitorPerformance.ScaleType, Double>();
  private boolean evaluated = false;

  /**
   * Wraps an existing scale prediction calculator.
   *
   * @param predictor the calculator (must be calculate()-ready or already calculated)
   */
  public ScaleControlAssessor(ScalePredictionCalculator predictor) {
    this.predictor = predictor;
  }

  /**
   * Convenience factory: builds a ScalePredictionCalculator pre-loaded with operating conditions
   * and aqueous-phase concentrations from the given stream, then wraps it in a fresh
   * ScaleControlAssessor.
   *
   * @param stream produced fluid stream
   * @return assessor with predictor initialised from the stream
   */
  public static ScaleControlAssessor fromStream(
      neqsim.process.equipment.stream.StreamInterface stream) {
    neqsim.process.chemistry.util.StreamChemistryAdapter ad =
        new neqsim.process.chemistry.util.StreamChemistryAdapter(stream);
    ScalePredictionCalculator p = new ScalePredictionCalculator();
    p.setTemperatureCelsius(ad.getTemperatureCelsius());
    p.setPressureBara(ad.getPressureBara());
    p.setCalciumConcentration(ad.getCalciumMgL());
    p.setBariumConcentration(ad.getBariumMgL());
    p.setIronConcentration(ad.getIronMgL());
    p.setSodiumConcentration(ad.getSodiumMgL());
    p.setBicarbonateConcentration(ad.getBicarbonateMgL());
    p.setSulphateConcentration(ad.getSulphateMgL());
    p.setTotalDissolvedSolids(ad.getTdsMgL());
    p.setCO2PartialPressure(ad.getPartialPressureBara("CO2"));
    return new ScaleControlAssessor(p);
  }

  /**
   * Registers an inhibitor for a specific scale type.
   *
   * @param scaleType target scale
   * @param inhibitor configured performance model
   */
  public void addInhibitor(ScaleInhibitorPerformance.ScaleType scaleType,
      ScaleInhibitorPerformance inhibitor) {
    inhibitors.put(scaleType, inhibitor);
  }

  /**
   * Runs the predictor (if needed), evaluates each inhibitor, and computes residual SI per scale.
   */
  public void evaluate() {
    predictor.calculate();
    residualSI.clear();
    Map<ScaleInhibitorPerformance.ScaleType, Double> uninh =
        new LinkedHashMap<ScaleInhibitorPerformance.ScaleType, Double>();
    uninh.put(ScaleInhibitorPerformance.ScaleType.CACO3, predictor.getCaCO3SaturationIndex());
    uninh.put(ScaleInhibitorPerformance.ScaleType.BASO4, predictor.getBaSO4SaturationIndex());
    uninh.put(ScaleInhibitorPerformance.ScaleType.SRSO4, predictor.getSrSO4SaturationIndex());
    uninh.put(ScaleInhibitorPerformance.ScaleType.CASO4, predictor.getCaSO4SaturationIndex());
    uninh.put(ScaleInhibitorPerformance.ScaleType.FECO3, predictor.getFeCO3SaturationIndex());

    for (Map.Entry<ScaleInhibitorPerformance.ScaleType, Double> entry : uninh.entrySet()) {
      double si = entry.getValue();
      if (Double.isNaN(si)) {
        residualSI.put(entry.getKey(), Double.NaN);
        continue;
      }
      ScaleInhibitorPerformance perf = inhibitors.get(entry.getKey());
      if (perf == null) {
        residualSI.put(entry.getKey(), si);
        continue;
      }
      if (!perf.isEvaluated()) {
        perf.evaluate();
      }
      double eff = Math.min(0.999, Math.max(0.0, perf.getEfficiency()));
      double residual = si + Math.log10(Math.max(1.0e-6, 1.0 - eff));
      residualSI.put(entry.getKey(), residual);
    }
    evaluated = true;
  }

  /**
   * Returns the residual SI for the supplied scale.
   *
   * @param scaleType scale type
   * @return residual SI (NaN if not applicable)
   */
  public double getResidualSI(ScaleInhibitorPerformance.ScaleType scaleType) {
    Double v = residualSI.get(scaleType);
    return v == null ? Double.NaN : v.doubleValue();
  }

  /**
   * Returns the worst residual SI among all evaluated scales.
   *
   * @return worst SI (most positive)
   */
  public double getWorstResidualSI() {
    double worst = Double.NEGATIVE_INFINITY;
    for (Double v : residualSI.values()) {
      if (v != null && !Double.isNaN(v) && v.doubleValue() > worst) {
        worst = v.doubleValue();
      }
    }
    return worst == Double.NEGATIVE_INFINITY ? Double.NaN : worst;
  }

  /**
   * Returns whether the worst residual SI is below the supplied scaling threshold.
   *
   * @param threshold scaling SI threshold (e.g. 0.5)
   * @return true if all scales are controlled
   */
  public boolean isControlled(double threshold) {
    double worst = getWorstResidualSI();
    return !Double.isNaN(worst) && worst < threshold;
  }

  /**
   * Returns whether evaluate() has been run.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns a structured map for JSON serialisation.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    Map<String, Object> uninhibited = new LinkedHashMap<String, Object>();
    uninhibited.put("CACO3", predictor.getCaCO3SaturationIndex());
    uninhibited.put("BASO4", predictor.getBaSO4SaturationIndex());
    uninhibited.put("SRSO4", predictor.getSrSO4SaturationIndex());
    uninhibited.put("CASO4", predictor.getCaSO4SaturationIndex());
    uninhibited.put("FECO3", predictor.getFeCO3SaturationIndex());
    map.put("uninhibitedSI", uninhibited);
    Map<String, Object> residual = new LinkedHashMap<String, Object>();
    for (Map.Entry<ScaleInhibitorPerformance.ScaleType, Double> entry : residualSI.entrySet()) {
      residual.put(entry.getKey().name(), entry.getValue());
    }
    map.put("residualSI", residual);
    Map<String, Object> inh = new LinkedHashMap<String, Object>();
    for (Map.Entry<ScaleInhibitorPerformance.ScaleType, ScaleInhibitorPerformance> entry : inhibitors
        .entrySet()) {
      inh.put(entry.getKey().name(), entry.getValue().toMap());
    }
    map.put("inhibitors", inh);
    map.put("worstResidualSI", getWorstResidualSI());
    map.put("controlledAt0p5", isControlled(0.5));
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns the industry standards applied by the scale-control assessor.
   *
   * @return list of standards (each as an ordered map)
   */
  public java.util.List<java.util.Map<String, Object>> getStandardsApplied() {
    return neqsim.process.chemistry.util.StandardsRegistry
        .toMapList(neqsim.process.chemistry.util.StandardsRegistry.NACE_TM0374);
  }
}
