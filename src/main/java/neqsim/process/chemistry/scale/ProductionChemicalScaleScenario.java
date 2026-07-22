package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.chemistry.ProductionChemical;
import neqsim.pvtsimulation.flowassurance.MultiMineralScaleEquilibrium;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

/**
 * Treatment-aware mineral-scale scenario for production-chemical root-cause analysis.
 *
 * <p>
 * The scenario evaluates the same brine before and after chemical dosing. pH adjusters are applied as acid/base
 * equivalents to a closed carbonate alkalinity balance; H2S scavengers consume dissolved sulphide according to active
 * chemistry; scale inhibitors are reported as kinetic controls and deliberately do not change thermodynamic saturation
 * index. This separation prevents a common but physically incorrect RCA assumption that threshold inhibitors lower
 * mineral saturation.
 * </p>
 *
 * <p>
 * Supported pH-adjuster actives are NaOH, sodium carbonate/soda ash, MDEA and MEA. Supported scavenger actives are
 * MEA-triazine, MMA-triazine, iron and glyoxal. Unrecognised chemicals remain in the audit trail and generate a warning
 * instead of receiving an invented thermodynamic effect.
 * </p>
 *
 * <p>
 * Concentrations are aqueous mg/L. Chemical dosage is treated as mg neat product/L, appropriate when ppm and mg/L are
 * interchangeable at screening density. The carbonate calculation is a closed aqueous-contact scenario: gas exchange,
 * organic-acid buffering and mineral dissolution during dosing require a coupled process/electrolyte flash.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProductionChemicalScaleScenario implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private static final double MW_H2S = 34.08;
  private static final double MW_HCO3 = 61.016;
  private static final double WATER_KW_25C = 1.0e-14;

  private double temperatureC = 60.0;
  private double pressureBara = 1.01325;
  private double pH = 6.5;
  private double calciumMgL;
  private double bariumMgL;
  private double strontiumMgL;
  private double ironMgL;
  private double magnesiumMgL;
  private double sodiumMgL;
  private double sulphateMgL;
  private double bicarbonateMgL;
  private double tdsMgL;
  private double dissolvedH2SMgL;
  private double co2PartialPressureBar;
  private MultiMineralScaleEquilibrium.ActivityModel activityModel = MultiMineralScaleEquilibrium.ActivityModel.DAVIES;
  private double pitzerIonicStrengthMolal = Double.NaN;

  private final List<ProductionChemical> chemicals = new ArrayList<ProductionChemical>();
  private final Map<String, String> warnings = new LinkedHashMap<String, String>();
  private final Map<String, Double> baselineSI = new LinkedHashMap<String, Double>();
  private final Map<String, Double> treatedSI = new LinkedHashMap<String, Double>();
  private boolean evaluated;
  private double treatedPH = Double.NaN;
  private double treatedBicarbonateMgL = Double.NaN;
  private double residualDissolvedH2SMgL = Double.NaN;
  private double baseDoseEquivalentsMolPerL;
  private double scavengerCapacityH2SMgL;
  private boolean scaleInhibitorPresent;

  /** Adds a production chemical to the treatment scenario. */
  public ProductionChemicalScaleScenario addChemical(ProductionChemical chemical) {
    if (chemical == null) {
      throw new IllegalArgumentException("chemical cannot be null");
    }
    chemicals.add(chemical);
    evaluated = false;
    return this;
  }

  /** Sets temperature in degrees Celsius. */
  public ProductionChemicalScaleScenario setTemperatureCelsius(double value) {
    temperatureC = value;
    evaluated = false;
    return this;
  }

  /** Sets pressure in bara. */
  public ProductionChemicalScaleScenario setPressureBara(double value) {
    pressureBara = value;
    evaluated = false;
    return this;
  }

  /** Sets the untreated aqueous pH. */
  public ProductionChemicalScaleScenario setPH(double value) {
    pH = value;
    evaluated = false;
    return this;
  }

  /** Sets calcium in mg/L. */
  public ProductionChemicalScaleScenario setCalciumMgL(double value) {
    calciumMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets barium in mg/L. */
  public ProductionChemicalScaleScenario setBariumMgL(double value) {
    bariumMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets strontium in mg/L. */
  public ProductionChemicalScaleScenario setStrontiumMgL(double value) {
    strontiumMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets dissolved ferrous iron in mg/L. */
  public ProductionChemicalScaleScenario setIronMgL(double value) {
    ironMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets magnesium in mg/L. */
  public ProductionChemicalScaleScenario setMagnesiumMgL(double value) {
    magnesiumMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets sodium in mg/L. */
  public ProductionChemicalScaleScenario setSodiumMgL(double value) {
    sodiumMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets sulphate in mg/L. */
  public ProductionChemicalScaleScenario setSulphateMgL(double value) {
    sulphateMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets bicarbonate in mg/L as HCO3-. */
  public ProductionChemicalScaleScenario setBicarbonateMgL(double value) {
    bicarbonateMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets total dissolved solids in mg/L. */
  public ProductionChemicalScaleScenario setTotalDissolvedSolidsMgL(double value) {
    tdsMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets total dissolved H2S in mg/L as H2S. */
  public ProductionChemicalScaleScenario setDissolvedH2SMgL(double value) {
    dissolvedH2SMgL = value;
    evaluated = false;
    return this;
  }

  /** Sets CO2 partial pressure in bar for reporting and the scale predictor. */
  public ProductionChemicalScaleScenario setCO2PartialPressureBar(double value) {
    co2PartialPressureBar = value;
    evaluated = false;
    return this;
  }

  /** Sets the activity model used for baseline and treated mineral equilibrium. */
  public ProductionChemicalScaleScenario setActivityModel(MultiMineralScaleEquilibrium.ActivityModel value) {
    activityModel = value;
    evaluated = false;
    return this;
  }

  /** Overrides Pitzer ionic strength in mol/kg water for both equilibrium cases. */
  public ProductionChemicalScaleScenario setPitzerIonicStrengthMolal(double value) {
    if (value < 0.0 || Double.isNaN(value)) {
      throw new IllegalArgumentException("Pitzer ionic strength must be non-negative");
    }
    pitzerIonicStrengthMolal = value;
    evaluated = false;
    return this;
  }

  /** Evaluates chemical consumption, carbonate alkalinity and before/after scale equilibrium. */
  public void evaluate() {
    warnings.clear();
    baselineSI.clear();
    treatedSI.clear();
    baseDoseEquivalentsMolPerL = 0.0;
    scavengerCapacityH2SMgL = 0.0;
    scaleInhibitorPresent = false;

    for (ProductionChemical chemical : chemicals) {
      applyChemical(chemical);
    }

    CarbonateState treatedCarbonate = applyBaseDose();
    treatedPH = treatedCarbonate.pH;
    treatedBicarbonateMgL = treatedCarbonate.bicarbonateMolL * MW_HCO3 * 1000.0;
    residualDissolvedH2SMgL = Math.max(0.0, dissolvedH2SMgL - scavengerCapacityH2SMgL);

    MultiMineralScaleEquilibrium baseline = createEquilibrium(pH, bicarbonateMgL);
    MultiMineralScaleEquilibrium treated = createEquilibrium(treatedPH, treatedBicarbonateMgL);
    copyInitialSI(baseline, baselineSI);
    copyInitialSI(treated, treatedSI);
    evaluated = true;

    if (scaleInhibitorPresent) {
      warnings.put("inhibitor_is_kinetic",
          "Scale inhibitor does not change thermodynamic SI; evaluate MIC and growth control separately with "
              + "ScaleControlAssessor");
    }
    double deltaCalcite = getSaturationIndexChange("CaCO3");
    if (!Double.isNaN(deltaCalcite) && deltaCalcite > 0.25) {
      warnings.put("chemical_induced_calcite_risk",
          "Base-equivalent dosing increased calcite SI by " + String.format(Locale.ROOT, "%.2f", deltaCalcite));
    }
    if (scavengerCapacityH2SMgL > 0.0 && residualDissolvedH2SMgL > 0.0) {
      warnings.put("h2s_scavenger_under_capacity",
          "Calculated dissolved-H2S load exceeds the practical active scavenger capacity");
    }
  }

  private void applyChemical(ProductionChemical chemical) {
    double activeMgL = Math.max(0.0, chemical.getDosagePpm()) * Math.max(0.0, chemical.getActiveWtPct()) / 100.0;
    switch (chemical.getType()) {
    case ACID:
    case PH_ADJUSTER:
      double equivalents = chemical.getAlkalinityCapacityMolEqPerKgActive() * activeMgL * 1.0e-6;
      if (Double.isNaN(chemical.getAlkalinityCapacityMolEqPerKgActive())) {
        equivalents = baseEquivalentsMolPerL(chemical.getActiveIngredient(), activeMgL);
      }
      if (Double.isNaN(equivalents)) {
        warnings.put("unsupported_ph_adjuster_" + chemical.getName(),
            "No acid/base-equivalent model for active ingredient " + chemical.getActiveIngredient());
      } else {
        baseDoseEquivalentsMolPerL += equivalents;
      }
      break;
    case H2S_SCAVENGER:
      double capacity = chemical.getH2SCapacityKgPerKgActive() * activeMgL;
      if (Double.isNaN(chemical.getH2SCapacityKgPerKgActive())) {
        capacity = scavengerCapacityMgL(chemical.getActiveIngredient(), activeMgL);
      }
      if (Double.isNaN(capacity)) {
        warnings.put("unsupported_h2s_scavenger_" + chemical.getName(),
            "No H2S stoichiometric model for active ingredient " + chemical.getActiveIngredient());
      } else {
        scavengerCapacityH2SMgL += capacity;
        if (chemical.getActiveIngredient().toLowerCase(Locale.ROOT).contains("triazine")) {
          warnings.put("triazine_spent_product",
              "Check dithiazine/amorphous spent-product deposition; scavenger removal is not mineral equilibrium");
        }
      }
      break;
    case SCALE_INHIBITOR:
      scaleInhibitorPresent = true;
      break;
    default:
      warnings.put("no_equilibrium_effect_" + chemical.getName(),
          "Chemical retained in audit trail; no supported stoichiometric effect on this aqueous scale scenario");
      break;
    }
  }

  private CarbonateState applyBaseDose() {
    if (bicarbonateMgL <= 0.0 || baseDoseEquivalentsMolPerL == 0.0) {
      return new CarbonateState(pH, Math.max(0.0, bicarbonateMgL) / (MW_HCO3 * 1000.0));
    }
    double temperatureK = temperatureC + 273.15;
    double k1 = carbonateK1(temperatureK);
    double k2 = carbonateK2(temperatureK);
    double initialHco3 = bicarbonateMgL / (MW_HCO3 * 1000.0);
    double initialAlpha1 = carbonateFractions(pH, k1, k2)[1];
    if (initialAlpha1 < 1.0e-12) {
      warnings.put("carbonate_balance_ill_conditioned",
          "Initial pH/HCO3 combination cannot define total inorganic carbon");
      return new CarbonateState(pH, initialHco3);
    }
    double totalCarbon = initialHco3 / initialAlpha1;
    double targetAlkalinity = alkalinity(totalCarbon, pH, k1, k2) + baseDoseEquivalentsMolPerL;

    double low = 2.0;
    double high = 14.0;
    for (int i = 0; i < 100; i++) {
      double mid = 0.5 * (low + high);
      if (alkalinity(totalCarbon, mid, k1, k2) < targetAlkalinity) {
        low = mid;
      } else {
        high = mid;
      }
    }
    double newPH = 0.5 * (low + high);
    if (newPH > 13.99) {
      warnings.put("ph_solver_upper_bound", "Dose exceeds the closed-carbonate model pH 14 search bound");
    }
    double newHco3 = totalCarbon * carbonateFractions(newPH, k1, k2)[1];
    return new CarbonateState(newPH, newHco3);
  }

  private MultiMineralScaleEquilibrium createEquilibrium(double scenarioPH, double scenarioBicarbonateMgL) {
    ScalePredictionCalculator predictor = new ScalePredictionCalculator();
    predictor.setTemperatureCelsius(temperatureC);
    predictor.setPressureBara(pressureBara);
    predictor.setPH(scenarioPH);
    predictor.setCalciumConcentration(calciumMgL);
    predictor.setBariumConcentration(bariumMgL);
    predictor.setStrontiumConcentration(strontiumMgL);
    predictor.setIronConcentration(ironMgL);
    predictor.setMagnesiumConcentration(magnesiumMgL);
    predictor.setSodiumConcentration(sodiumMgL);
    predictor.setSulphateConcentration(sulphateMgL);
    predictor.setBicarbonateConcentration(scenarioBicarbonateMgL);
    predictor.setTotalDissolvedSolids(tdsMgL);
    predictor.setCO2PartialPressure(co2PartialPressureBar);
    MultiMineralScaleEquilibrium equilibrium = new MultiMineralScaleEquilibrium(predictor)
        .setActivityModel(activityModel);
    if (activityModel == MultiMineralScaleEquilibrium.ActivityModel.PITZER_BINARY
        && !Double.isNaN(pitzerIonicStrengthMolal)) {
      equilibrium.setPitzerIonicStrengthMolal(pitzerIonicStrengthMolal);
    }
    return equilibrium.solve();
  }

  private static void copyInitialSI(MultiMineralScaleEquilibrium equilibrium, Map<String, Double> destination) {
    for (Map.Entry<String, MultiMineralScaleEquilibrium.MineralResult> entry : equilibrium.getResults().entrySet()) {
      destination.put(entry.getKey(), entry.getValue().getInitialSI());
    }
  }

  private static double baseEquivalentsMolPerL(String active, double activeMgL) {
    String key = active == null ? "" : active.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
    double activeGramsPerL = activeMgL / 1000.0;
    if (key.contains("naoh") || key.contains("sodiumhydroxide") || key.contains("caustic")) {
      return activeGramsPerL / 39.997;
    }
    if (key.contains("na2co3") || key.contains("sodiumcarbonate") || key.contains("sodaash")) {
      return 2.0 * activeGramsPerL / 105.9888;
    }
    if (key.contains("mdea") || key.contains("methyldiethanolamine")) {
      return activeGramsPerL / 119.163;
    }
    if (key.equals("mea") || key.contains("monoethanolamine")) {
      return activeGramsPerL / 61.084;
    }
    if (key.contains("hcl") || key.contains("hydrochloricacid")) {
      return -activeGramsPerL / 36.461;
    }
    return Double.NaN;
  }

  private static double scavengerCapacityMgL(String active, double activeMgL) {
    String key = active == null ? "" : active.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
    double molarMass;
    double molH2SPerMolActive;
    if (key.contains("meatriazine")) {
      molarMass = 189.26;
      molH2SPerMolActive = 1.0;
    } else if (key.contains("mmatriazine")) {
      molarMass = 129.21;
      molH2SPerMolActive = 1.0;
    } else if (key.contains("iron") || key.equals("fe")) {
      molarMass = 55.845;
      molH2SPerMolActive = 1.0;
    } else if (key.contains("glyoxal")) {
      molarMass = 58.04;
      molH2SPerMolActive = 1.0 / 1.5;
    } else {
      return Double.NaN;
    }
    return activeMgL / molarMass * molH2SPerMolActive * MW_H2S;
  }

  private static double carbonateK1(double temperatureK) {
    double logK1 = -356.3094 - 0.06091964 * temperatureK + 21834.37 / temperatureK + 126.8339 * Math.log10(temperatureK)
        - 1684915.0 / (temperatureK * temperatureK);
    return Math.pow(10.0, logK1);
  }

  private static double carbonateK2(double temperatureK) {
    double logK2 = -107.8871 - 0.03252849 * temperatureK + 5151.79 / temperatureK + 38.92561 * Math.log10(temperatureK)
        - 563713.9 / (temperatureK * temperatureK);
    return Math.pow(10.0, logK2);
  }

  private static double[] carbonateFractions(double valuePH, double k1, double k2) {
    double h = Math.pow(10.0, -valuePH);
    double denominator = h * h + k1 * h + k1 * k2;
    return new double[] { h * h / denominator, k1 * h / denominator, k1 * k2 / denominator };
  }

  private static double alkalinity(double totalCarbon, double valuePH, double k1, double k2) {
    double[] alpha = carbonateFractions(valuePH, k1, k2);
    double h = Math.pow(10.0, -valuePH);
    double oh = WATER_KW_25C / h;
    return totalCarbon * (alpha[1] + 2.0 * alpha[2]) + oh - h;
  }

  /** Returns the treated pH. */
  public double getTreatedPH() {
    ensureEvaluated();
    return treatedPH;
  }

  /** Returns residual dissolved H2S in mg/L after scavenger capacity is consumed. */
  public double getResidualDissolvedH2SMgL() {
    ensureEvaluated();
    return residualDissolvedH2SMgL;
  }

  /** Returns baseline saturation index for a mineral formula, or NaN when its ions are absent. */
  public double getBaselineSaturationIndex(String mineral) {
    ensureEvaluated();
    Double value = baselineSI.get(mineral);
    return value == null ? Double.NaN : value.doubleValue();
  }

  /** Returns treated saturation index for a mineral formula, or NaN when its ions are absent. */
  public double getTreatedSaturationIndex(String mineral) {
    ensureEvaluated();
    Double value = treatedSI.get(mineral);
    return value == null ? Double.NaN : value.doubleValue();
  }

  /** Returns treated minus baseline saturation index. */
  public double getSaturationIndexChange(String mineral) {
    double baseline = getBaselineSaturationIndex(mineral);
    double treated = getTreatedSaturationIndex(mineral);
    return Double.isNaN(baseline) || Double.isNaN(treated) ? Double.NaN : treated - baseline;
  }

  /** Returns whether evaluate has completed. */
  public boolean isEvaluated() {
    return evaluated;
  }

  /** Returns a copy of model warnings and RCA evidence flags. */
  public Map<String, String> getWarnings() {
    ensureEvaluated();
    return new LinkedHashMap<String, String>(warnings);
  }

  /** Returns a structured scenario report. */
  public Map<String, Object> toMap() {
    ensureEvaluated();
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    Map<String, Object> conditions = new LinkedHashMap<String, Object>();
    conditions.put("temperatureC", temperatureC);
    conditions.put("pressureBara", pressureBara);
    conditions.put("activityModel", activityModel.name());
    map.put("conditions", conditions);
    Map<String, Object> chemistry = new LinkedHashMap<String, Object>();
    chemistry.put("baselinePH", pH);
    chemistry.put("treatedPH", treatedPH);
    chemistry.put("baselineBicarbonateMgL", bicarbonateMgL);
    chemistry.put("treatedBicarbonateMgL", treatedBicarbonateMgL);
    chemistry.put("baseDoseEquivalentsMolL", baseDoseEquivalentsMolPerL);
    chemistry.put("baselineDissolvedH2SMgL", dissolvedH2SMgL);
    chemistry.put("scavengerCapacityH2SMgL", scavengerCapacityH2SMgL);
    chemistry.put("residualDissolvedH2SMgL", residualDissolvedH2SMgL);
    map.put("treatedChemistry", chemistry);
    map.put("baselineSaturationIndex", new LinkedHashMap<String, Double>(baselineSI));
    map.put("treatedSaturationIndex", new LinkedHashMap<String, Double>(treatedSI));
    Map<String, Double> changes = new LinkedHashMap<String, Double>();
    for (String mineral : baselineSI.keySet()) {
      changes.put(mineral, getSaturationIndexChange(mineral));
    }
    map.put("saturationIndexChange", changes);
    List<Map<String, Object>> chemicalMaps = new ArrayList<Map<String, Object>>();
    for (ProductionChemical chemical : chemicals) {
      chemicalMaps.add(chemical.toMap());
    }
    map.put("chemicals", chemicalMaps);
    map.put("scaleInhibitorChangesThermodynamicSI", false);
    map.put("warnings", new LinkedHashMap<String, String>(warnings));
    map.put("modelScope",
        "Closed aqueous carbonate balance; no gas re-equilibration, organic-acid buffer or vendor reaction "
            + "by-products");
    return map;
  }

  /** Returns the scenario report as JSON. */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(toMap());
  }

  private void ensureEvaluated() {
    if (!evaluated) {
      evaluate();
    }
  }

  private static final class CarbonateState implements Serializable {
    private static final long serialVersionUID = 1000L;
    final double pH;
    final double bicarbonateMolL;

    CarbonateState(double pH, double bicarbonateMolL) {
      this.pH = pH;
      this.bicarbonateMolL = bicarbonateMolL;
    }
  }
}
