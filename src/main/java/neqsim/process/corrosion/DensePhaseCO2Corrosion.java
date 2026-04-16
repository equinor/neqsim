package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.thermo.system.SystemInterface;

/**
 * Dense-phase CO2 corrosion assessment for CCS transport pipelines.
 *
 * <p>
 * Evaluates corrosion risk in dense-phase CO2 transport systems where impurities (H2O, O2, SO2,
 * NOx, H2S) significantly affect phase behavior and corrosion mechanisms. In dry dense-phase CO2,
 * corrosion is negligible — but free water formation from impurity interactions can cause severe
 * attack.
 * </p>
 *
 * <h2>Standards</h2>
 *
 * <table>
 * <caption>Standards for dense-phase CO2 corrosion</caption>
 * <tr>
 * <th>Standard</th>
 * <th>Scope</th>
 * </tr>
 * <tr>
 * <td>DNV-RP-J202</td>
 * <td>Design and operation of CO2 pipelines</td>
 * </tr>
 * <tr>
 * <td>ISO 27913</td>
 * <td>CO2 capture, transportation, and geological storage — pipelines</td>
 * </tr>
 * <tr>
 * <td>DNV-RP-F104</td>
 * <td>Design of carbon steel pipelines for CCS</td>
 * </tr>
 * <tr>
 * <td>ASME B31.4</td>
 * <td>Pipeline transportation — supercritical CO2</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 */
public class DensePhaseCO2Corrosion implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1008L;

  // ─── Input Parameters ───────────────────────────────────

  /** Temperature in Celsius. */
  private double temperatureC = 25.0;

  /** Pressure in bara. */
  private double pressureBara = 100.0;

  /** CO2 purity in mol%. */
  private double co2PurityMolPct = 99.0;

  /** Water content in ppmv. */
  private double waterContentPpmv = 0.0;

  /** O2 content in ppmv. */
  private double o2ContentPpmv = 0.0;

  /** SO2 content in ppmv. */
  private double so2ContentPpmv = 0.0;

  /** NOx content in ppmv. */
  private double noxContentPpmv = 0.0;

  /** H2S content in ppmv. */
  private double h2sContentPpmv = 0.0;

  /** H2 content in mol%. */
  private double h2ContentMolPct = 0.0;

  /** N2 content in mol%. */
  private double n2ContentMolPct = 0.0;

  /** Ar content in mol%. */
  private double arContentMolPct = 0.0;

  /** Pipeline material. */
  private String materialType = "Carbon steel X65";

  /** NeqSim fluid for property calculations. */
  private transient SystemInterface fluid = null;

  // ─── Results ────────────────────────────────────────────

  /** Whether free water can form. */
  private boolean freeWaterRisk = false;

  /** Water solubility limit in dense CO2 at conditions (ppmv). */
  private double waterSolubilityLimitPpmv = 0.0;

  /** Safety margin above water solubility (ppmv). */
  private double waterMarginPpmv = 0.0;

  /** Corrosion risk level. */
  private String riskLevel = "";

  /** Estimated corrosion rate if free water forms (mm/yr). */
  private double wetCorrosionRateMmYr = 0.0;

  /** Whether impurity levels meet typical CCS pipeline specs. */
  private boolean meetsImpuritySpecs = true;

  /** Impurity specification issues. */
  private List<String> impurityIssues = new ArrayList<String>();

  /** Overall recommendation. */
  private String recommendation = "";

  /** Phase state of CO2 at conditions. */
  private String co2PhaseState = "";

  /** Notes. */
  private List<String> notes = new ArrayList<String>();

  /** Evaluated flag. */
  private boolean evaluated = false;

  /**
   * Default constructor.
   */
  public DensePhaseCO2Corrosion() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets temperature.
   *
   * @param tempC temperature in Celsius
   */
  public void setTemperatureC(double tempC) {
    this.temperatureC = tempC;
  }

  /**
   * Sets pressure.
   *
   * @param bara pressure in bara
   */
  public void setPressureBara(double bara) {
    this.pressureBara = bara;
  }

  /**
   * Sets CO2 purity.
   *
   * @param molPct CO2 purity in mol%
   */
  public void setCo2PurityMolPct(double molPct) {
    this.co2PurityMolPct = molPct;
  }

  /**
   * Sets water content.
   *
   * @param ppmv water in ppmv
   */
  public void setWaterContentPpmv(double ppmv) {
    this.waterContentPpmv = ppmv;
  }

  /**
   * Sets O2 content.
   *
   * @param ppmv O2 in ppmv
   */
  public void setO2ContentPpmv(double ppmv) {
    this.o2ContentPpmv = ppmv;
  }

  /**
   * Sets SO2 content.
   *
   * @param ppmv SO2 in ppmv
   */
  public void setSo2ContentPpmv(double ppmv) {
    this.so2ContentPpmv = ppmv;
  }

  /**
   * Sets NOx content.
   *
   * @param ppmv NOx in ppmv
   */
  public void setNoxContentPpmv(double ppmv) {
    this.noxContentPpmv = ppmv;
  }

  /**
   * Sets H2S content.
   *
   * @param ppmv H2S in ppmv
   */
  public void setH2sContentPpmv(double ppmv) {
    this.h2sContentPpmv = ppmv;
  }

  /**
   * Sets H2 content.
   *
   * @param molPct H2 in mol%
   */
  public void setH2ContentMolPct(double molPct) {
    this.h2ContentMolPct = molPct;
  }

  /**
   * Sets N2 content.
   *
   * @param molPct N2 in mol%
   */
  public void setN2ContentMolPct(double molPct) {
    this.n2ContentMolPct = molPct;
  }

  /**
   * Sets Ar content.
   *
   * @param molPct Ar in mol%
   */
  public void setArContentMolPct(double molPct) {
    this.arContentMolPct = molPct;
  }

  /**
   * Sets material type.
   *
   * @param type material type string
   */
  public void setMaterialType(String type) {
    this.materialType = type;
  }

  /**
   * Sets NeqSim fluid for water solubility calculations.
   *
   * @param system the NeqSim fluid system
   */
  public void setFluid(SystemInterface system) {
    this.fluid = system;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Performs dense-phase CO2 corrosion assessment.
   */
  public void evaluate() {
    notes.clear();
    impurityIssues.clear();

    determineCO2Phase();
    estimateWaterSolubility();
    checkImpuritySpecs();
    assessCorrosionRisk();
    generateRecommendation();

    evaluated = true;
  }

  /**
   * Determines CO2 phase state.
   */
  private void determineCO2Phase() {
    // CO2 critical point: 31.1°C, 73.8 bara
    if (pressureBara > 73.8 && temperatureC > 31.1) {
      co2PhaseState = "Supercritical";
    } else if (pressureBara > 73.8 && temperatureC < 31.1) {
      co2PhaseState = "Dense liquid";
    } else if (pressureBara > 51.8 && temperatureC < 31.1) {
      co2PhaseState = "Liquid";
    } else {
      co2PhaseState = "Gas";
    }
    notes.add("CO2 phase at " + temperatureC + "°C, " + pressureBara + " bara: " + co2PhaseState);
  }

  /**
   * Estimates water solubility limit in dense CO2.
   */
  private void estimateWaterSolubility() {
    // Water solubility in dense/supercritical CO2 (empirical approximation)
    // Pure CO2: roughly 2000-4000 ppmv at dense phase conditions
    // Impurities reduce solubility (SO2, NO2 create acids that enhance water condensation)
    double baseSolubility;
    if (co2PhaseState.equals("Gas")) {
      baseSolubility = 20000.0; // Gas-phase can hold more water
    } else if (co2PhaseState.equals("Supercritical")) {
      baseSolubility = 3000.0 + 50.0 * (temperatureC - 31.1);
    } else {
      baseSolubility = 2000.0 + 30.0 * (temperatureC - 0.0);
    }

    // Impurity reduction factor
    double impurityReduction = 1.0;
    if (so2ContentPpmv > 0) {
      impurityReduction -= 0.1 * Math.min(so2ContentPpmv / 100.0, 0.3);
    }
    if (noxContentPpmv > 0) {
      impurityReduction -= 0.05 * Math.min(noxContentPpmv / 100.0, 0.2);
    }
    if (h2sContentPpmv > 0) {
      impurityReduction -= 0.05 * Math.min(h2sContentPpmv / 100.0, 0.2);
    }
    if (impurityReduction < 0.5) {
      impurityReduction = 0.5;
    }

    waterSolubilityLimitPpmv = baseSolubility * impurityReduction;
    waterMarginPpmv = waterSolubilityLimitPpmv - waterContentPpmv;
    freeWaterRisk = waterMarginPpmv <= 0;

    if (freeWaterRisk) {
      notes.add("CRITICAL: Water content exceeds estimated solubility — free water expected.");
    } else if (waterMarginPpmv < 500) {
      notes.add("WARNING: Water content within 500 ppmv of solubility limit.");
    }
  }

  /**
   * Checks impurity levels against typical CCS pipeline specifications.
   */
  private void checkImpuritySpecs() {
    // DNV-RP-J202 / ISO 27913 typical limits
    meetsImpuritySpecs = true;

    if (waterContentPpmv > 500) {
      impurityIssues.add("H2O: " + waterContentPpmv + " ppmv exceeds 500 ppmv limit");
      meetsImpuritySpecs = false;
    }
    if (o2ContentPpmv > 100) {
      impurityIssues.add(
          "O2: " + o2ContentPpmv + " ppmv exceeds 100 ppmv limit (10 ppmv" + " preferred for CS)");
      meetsImpuritySpecs = false;
    } else if (o2ContentPpmv > 10) {
      impurityIssues.add("O2: " + o2ContentPpmv + " ppmv — acceptable with dehydration but"
          + " above 10 ppmv preferred limit");
    }
    if (so2ContentPpmv > 100) {
      impurityIssues.add("SO2: " + so2ContentPpmv + " ppmv exceeds 100 ppmv limit");
      meetsImpuritySpecs = false;
    }
    if (noxContentPpmv > 100) {
      impurityIssues.add("NOx: " + noxContentPpmv + " ppmv exceeds 100 ppmv limit");
      meetsImpuritySpecs = false;
    }
    if (h2sContentPpmv > 200) {
      impurityIssues.add("H2S: " + h2sContentPpmv + " ppmv exceeds 200 ppmv limit");
      meetsImpuritySpecs = false;
    }
    if (h2ContentMolPct > 2.0) {
      impurityIssues.add("H2: " + h2ContentMolPct + " mol% may affect dense phase properties");
    }
    if (n2ContentMolPct + arContentMolPct > 4.0) {
      impurityIssues.add("N2+Ar: " + (n2ContentMolPct + arContentMolPct)
          + " mol% — may affect critical point and two-phase region");
    }
  }

  /**
   * Assesses overall corrosion risk.
   */
  private void assessCorrosionRisk() {
    if (!freeWaterRisk && meetsImpuritySpecs) {
      riskLevel = "Low";
      wetCorrosionRateMmYr = 0.001; // Negligible in dry CO2
      notes.add("Dry dense-phase CO2 — corrosion risk negligible for carbon steel.");
    } else if (freeWaterRisk) {
      // CO2 corrosion rate when free water present (de Waard-Milliams type)
      double pCO2 = pressureBara * co2PurityMolPct / 100.0;
      // Simplified de Waard-Milliams: log(Vcor) = 5.8 - 1710/(T+273) + 0.67 * log(pCO2)
      double tK = temperatureC + 273.15;
      double logRate = 5.8 - 1710.0 / tK + 0.67 * Math.log10(pCO2);
      wetCorrosionRateMmYr = Math.pow(10, logRate);

      // SO2 and O2 synergistic effects
      if (so2ContentPpmv > 50 && o2ContentPpmv > 50) {
        wetCorrosionRateMmYr *= 2.0;
        notes.add("SO2 + O2 synergy increases corrosion from sulfuric acid formation.");
      }

      if (wetCorrosionRateMmYr > 10.0) {
        riskLevel = "Very High";
      } else if (wetCorrosionRateMmYr > 1.0) {
        riskLevel = "High";
      } else {
        riskLevel = "Medium";
      }
    } else {
      riskLevel = "Medium";
      wetCorrosionRateMmYr = 0.05;
      notes.add("Impurity specs exceeded but no free water — localized corrosion risk.");
    }
  }

  /**
   * Generates overall recommendation.
   */
  private void generateRecommendation() {
    StringBuilder sb = new StringBuilder();

    if (riskLevel.equals("Low")) {
      sb.append("Carbon steel pipeline acceptable. ");
      sb.append("Maintain dehydration to keep water below ");
      sb.append(String.format("%.0f", waterSolubilityLimitPpmv * 0.6));
      sb.append(" ppmv (60% of solubility limit).");
    } else if (freeWaterRisk) {
      sb.append("CRITICAL: Must dehydrate CO2 stream to below ");
      sb.append(String.format("%.0f", waterSolubilityLimitPpmv * 0.6));
      sb.append(" ppmv. ");
      sb.append("If dehydration not possible, use CRA pipeline (316L minimum). ");
      sb.append("Estimated wet corrosion rate: ");
      sb.append(String.format("%.1f", wetCorrosionRateMmYr));
      sb.append(" mm/yr = unacceptable for CS.");
    } else {
      sb.append("Address impurity exceedances: ");
      for (String issue : impurityIssues) {
        sb.append(issue).append("; ");
      }
    }

    recommendation = sb.toString();
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Checks if free water can form.
   *
   * @return true if free water risk exists
   */
  public boolean isFreeWaterRisk() {
    return freeWaterRisk;
  }

  /**
   * Gets water solubility limit.
   *
   * @return limit in ppmv
   */
  public double getWaterSolubilityLimitPpmv() {
    return waterSolubilityLimitPpmv;
  }

  /**
   * Gets water margin.
   *
   * @return margin in ppmv (negative = exceeded)
   */
  public double getWaterMarginPpmv() {
    return waterMarginPpmv;
  }

  /**
   * Gets corrosion risk level.
   *
   * @return risk level string
   */
  public String getRiskLevel() {
    return riskLevel;
  }

  /**
   * Gets wet corrosion rate.
   *
   * @return rate in mm/yr
   */
  public double getWetCorrosionRateMmYr() {
    return wetCorrosionRateMmYr;
  }

  /**
   * Checks if impurity specs are met.
   *
   * @return true if all within limits
   */
  public boolean isMeetsImpuritySpecs() {
    return meetsImpuritySpecs;
  }

  /**
   * Gets impurity issues.
   *
   * @return list of issues
   */
  public List<String> getImpurityIssues() {
    return impurityIssues;
  }

  /**
   * Gets recommendation.
   *
   * @return recommendation string
   */
  public String getRecommendation() {
    return recommendation;
  }

  /**
   * Gets CO2 phase state.
   *
   * @return phase state string
   */
  public String getCo2PhaseState() {
    return co2PhaseState;
  }

  /**
   * Gets notes.
   *
   * @return list of notes
   */
  public List<String> getNotes() {
    return notes;
  }

  /**
   * Converts results to a map.
   *
   * @return ordered map of results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("standard", "DNV-RP-J202 / ISO 27913");
    map.put("temperature_C", temperatureC);
    map.put("pressure_bara", pressureBara);
    map.put("co2Purity_molPct", co2PurityMolPct);
    map.put("co2PhaseState", co2PhaseState);
    map.put("materialType", materialType);

    Map<String, Object> impurities = new LinkedHashMap<String, Object>();
    impurities.put("H2O_ppmv", waterContentPpmv);
    impurities.put("O2_ppmv", o2ContentPpmv);
    impurities.put("SO2_ppmv", so2ContentPpmv);
    impurities.put("NOx_ppmv", noxContentPpmv);
    impurities.put("H2S_ppmv", h2sContentPpmv);
    impurities.put("H2_molPct", h2ContentMolPct);
    impurities.put("N2_molPct", n2ContentMolPct);
    impurities.put("Ar_molPct", arContentMolPct);
    map.put("impurities", impurities);

    map.put("waterSolubilityLimit_ppmv", Math.round(waterSolubilityLimitPpmv * 10.0) / 10.0);
    map.put("waterMargin_ppmv", Math.round(waterMarginPpmv * 10.0) / 10.0);
    map.put("freeWaterRisk", freeWaterRisk);
    map.put("meetsImpuritySpecs", meetsImpuritySpecs);
    map.put("impurityIssues", impurityIssues);
    map.put("wetCorrosionRate_mmyr", Math.round(wetCorrosionRateMmYr * 1000.0) / 1000.0);
    map.put("riskLevel", riskLevel);
    map.put("recommendation", recommendation);
    map.put("notes", notes);
    return map;
  }

  /**
   * Converts results to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
