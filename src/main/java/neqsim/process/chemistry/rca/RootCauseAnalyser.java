package neqsim.process.chemistry.rca;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import neqsim.process.chemistry.ChemicalCompatibilityAssessor;
import neqsim.process.chemistry.ProductionChemical;

/**
 * Root cause analyser for chemical-related incidents in well, flow assurance and process systems.
 *
 * <p>
 * Given a set of {@link Symptom}s plus contextual data (chemical inventory, water chemistry,
 * temperature, pressure, material), the analyser ranks plausible root causes by combining:
 * </p>
 * <ul>
 * <li>Direct symptom-to-cause mapping (deposit found ⇒ scale / wax / asphaltene candidates).</li>
 * <li>Chemical compatibility issues from a {@link ChemicalCompatibilityAssessor}.</li>
 * <li>Process condition flags (high temperature, high shear, oxygen ingress, low pH).</li>
 * </ul>
 *
 * <p>
 * Each candidate gets a score 0..1 and a tag (PRIMARY / CONTRIBUTING / POSSIBLE / RULED_OUT). The
 * top-ranked candidate is the primary recommendation; others are listed for follow-up
 * investigation.
 * </p>
 *
 * <p>
 * The analyser is deliberately rule-based and explainable — every candidate carries an evidence
 * narrative that justifies its score. This is appropriate for engineering-review workflows where
 * black-box ML output is hard to defend.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class RootCauseAnalyser implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ─── Inputs ─────────────────────────────────────────────

  private final List<Symptom> symptoms = new ArrayList<Symptom>();
  private final List<ProductionChemical> chemicals = new ArrayList<ProductionChemical>();
  private ChemicalCompatibilityAssessor compatibilityAssessor;
  private double temperatureC = 60.0;
  private double pressureBara = 50.0;
  private double pH = 6.5;
  private double calciumMgL = 0.0;
  private double ironMgL = 0.0;
  private double oxygenPpb = 0.0;
  private double h2sPartialPressureBar = 0.0;
  private double co2PartialPressureBar = 0.0;
  private double wallShearStressPa = 0.0;
  private String material = "carbon_steel";

  // ─── Outputs ────────────────────────────────────────────

  private final List<RootCauseCandidate> candidates = new ArrayList<RootCauseCandidate>();
  private final List<String> dataGaps = new ArrayList<String>();
  private final Map<String, Double> bayesianPosteriors = new LinkedHashMap<String, Double>();
  private boolean evaluated = false;

  // ─── Setters ────────────────────────────────────────────

  /**
   * Adds an observed symptom.
   *
   * @param symptom symptom
   * @return this for chaining
   */
  public RootCauseAnalyser addSymptom(Symptom symptom) {
    symptoms.add(symptom);
    return this;
  }

  /**
   * Adds a production chemical to context.
   *
   * @param chemical chemical
   * @return this for chaining
   */
  public RootCauseAnalyser addChemical(ProductionChemical chemical) {
    chemicals.add(chemical);
    return this;
  }

  /**
   * Sets the compatibility assessor (already configured with chemicals).
   *
   * @param assessor compatibility assessor
   */
  public void setCompatibilityAssessor(ChemicalCompatibilityAssessor assessor) {
    this.compatibilityAssessor = assessor;
  }

  /**
   * Sets process temperature.
   *
   * @param tC temperature in Celsius
   */
  public void setTemperatureCelsius(double tC) {
    this.temperatureC = tC;
  }

  /**
   * Sets process pressure.
   *
   * @param bara pressure in bara
   */
  public void setPressureBara(double bara) {
    this.pressureBara = bara;
  }

  /**
   * Sets pH.
   *
   * @param pH pH
   */
  public void setPH(double pH) {
    this.pH = pH;
  }

  /**
   * Sets calcium concentration.
   *
   * @param mgL Ca in mg/L
   */
  public void setCalciumMgL(double mgL) {
    this.calciumMgL = mgL;
  }

  /**
   * Sets iron concentration.
   *
   * @param mgL Fe in mg/L
   */
  public void setIronMgL(double mgL) {
    this.ironMgL = mgL;
  }

  /**
   * Sets oxygen content.
   *
   * @param ppb O2 in ppb
   */
  public void setOxygenPpb(double ppb) {
    this.oxygenPpb = ppb;
  }

  /**
   * Sets H2S partial pressure.
   *
   * @param bar H2S pp in bar
   */
  public void setH2SPartialPressureBar(double bar) {
    this.h2sPartialPressureBar = bar;
  }

  /**
   * Sets CO2 partial pressure.
   *
   * @param bar CO2 pp in bar
   */
  public void setCO2PartialPressureBar(double bar) {
    this.co2PartialPressureBar = bar;
  }

  /**
   * Sets wall shear stress.
   *
   * @param pa shear in Pa
   */
  public void setWallShearStressPa(double pa) {
    this.wallShearStressPa = pa;
  }

  /**
   * Sets material identifier.
   *
   * @param material e.g. "carbon_steel", "13Cr", "316L"
   */
  public void setMaterial(String material) {
    this.material = material;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Runs the analysis. Populates the ranked candidate list.
   */
  public void analyse() {
    candidates.clear();
    dataGaps.clear();

    // Pass 1: symptom-driven cause hypotheses
    for (Symptom s : symptoms) {
      switch (s.getCategory()) {
        case DEPOSIT:
          analyseDeposit(s);
          break;
        case CORROSION:
          analyseCorrosion(s);
          break;
        case EMULSION:
          analyseEmulsion(s);
          break;
        case PH_EXCURSION:
          analysePH(s);
          break;
        case FLOW_RESTRICTION:
          analyseFlowRestriction(s);
          break;
        case H2S_BREAKTHROUGH:
          analyseH2SBreakthrough(s);
          break;
        case OFF_SPEC:
          analyseOffSpec(s);
          break;
        default:
          break;
      }
    }

    // Pass 2: chemical compatibility
    if (compatibilityAssessor != null) {
      if (!compatibilityAssessor.isEvaluated()) {
        compatibilityAssessor.evaluate();
      }
      if (compatibilityAssessor
          .getVerdict() == ChemicalCompatibilityAssessor.Verdict.INCOMPATIBLE) {
        candidates.add(new RootCauseCandidate("CHEMICAL_INCOMPATIBILITY",
            "Incompatible chemical combination injected upstream", 0.85,
            "ChemicalCompatibilityAssessor reports INCOMPATIBLE: "
                + compatibilityAssessor.getIssues(),
            "Reformulate chemical injection program; segregate injection points"));
      } else if (compatibilityAssessor
          .getVerdict() == ChemicalCompatibilityAssessor.Verdict.CAUTION) {
        candidates.add(new RootCauseCandidate("CHEMICAL_CAUTION",
            "Chemical combination flagged with caution", 0.40,
            "ChemicalCompatibilityAssessor reports CAUTION: " + compatibilityAssessor.getIssues(),
            "Verify compatibility with bench testing; review operating conditions"));
      }
    } else if (chemicals.size() >= 2) {
      dataGaps.add(
          "Multiple chemicals declared but no ChemicalCompatibilityAssessor configured — compatibility not verified");
    }

    // Pass 3: deduplicate by code (keep highest score)
    Map<String, RootCauseCandidate> dedup = new LinkedHashMap<String, RootCauseCandidate>();
    for (RootCauseCandidate c : candidates) {
      RootCauseCandidate prev = dedup.get(c.getCode());
      if (prev == null || c.getScore() > prev.getScore()) {
        dedup.put(c.getCode(), c);
      }
    }
    candidates.clear();
    candidates.addAll(dedup.values());

    // Sort descending by score
    Collections.sort(candidates, new Comparator<RootCauseCandidate>() {
      @Override
      public int compare(RootCauseCandidate a, RootCauseCandidate b) {
        return Double.compare(b.getScore(), a.getScore());
      }
    });

    // Re-tag: highest score = PRIMARY (if any candidates)
    if (!candidates.isEmpty()) {
      candidates.get(0).setTag(RootCauseCandidate.Tag.PRIMARY);
      for (int i = 1; i < candidates.size(); i++) {
        RootCauseCandidate c = candidates.get(i);
        if (c.getScore() >= 0.4) {
          c.setTag(RootCauseCandidate.Tag.CONTRIBUTING);
        } else if (c.getScore() >= 0.15) {
          c.setTag(RootCauseCandidate.Tag.POSSIBLE);
        } else {
          c.setTag(RootCauseCandidate.Tag.RULED_OUT);
        }
      }
    }

    evaluated = true;
  }

  // ─── Symptom analysers ──────────────────────────────────

  /**
   * Analyses a deposit symptom.
   *
   * @param s symptom
   */
  private void analyseDeposit(Symptom s) {
    String desc = nullSafe(s.getDescription()).toLowerCase();
    // Carbonate / sulphate scale
    if (desc.contains("scale") || desc.contains("carbonate") || desc.contains("calcite")
        || desc.contains("barite") || desc.contains("white") || desc.contains("crystal")) {
      double score = 0.55 + 0.25 * (calciumMgL > 1000.0 ? 1.0 : calciumMgL / 1000.0);
      candidates.add(new RootCauseCandidate("MINERAL_SCALE",
          "Mineral scale precipitation (carbonate / sulphate)", Math.min(0.95, score),
          "Deposit symptom + Ca = " + calciumMgL + " mg/L; verify with XRD",
          "Run ScalePredictionCalculator; raise scale inhibitor dose; verify SI > 0"));
    }
    if (desc.contains("wax") || desc.contains("paraffin")) {
      candidates.add(new RootCauseCandidate("WAX_DEPOSITION", "Paraffin wax deposition below WAT",
          0.75, "Deposit symptom matches wax morphology; check vs. WAT",
          "Run WaxPrecipitationModel; apply wax inhibitor or insulation"));
    }
    if (desc.contains("asphalt") || desc.contains("black") || desc.contains("tar")) {
      candidates
          .add(new RootCauseCandidate("ASPHALTENE", "Asphaltene precipitation near onset pressure",
              0.70, "Deposit symptom matches asphaltene appearance",
              "Run asphaltene stability check; apply dispersant; manage pressure"));
    }
    if (desc.contains("hydrate") || desc.contains("ice") || desc.contains("plug")) {
      candidates.add(new RootCauseCandidate("HYDRATE_PLUG", "Hydrate formation",
          temperatureC < 25.0 ? 0.80 : 0.45,
          "Deposit symptom + T = " + temperatureC + " C below typical hydrate envelope",
          "Verify with hydrate model; inject MEG / MeOH or apply LDHI"));
    }
    if (desc.contains("fes") || desc.contains("iron sulph") || desc.contains("black powder")) {
      candidates.add(new RootCauseCandidate("FES_DEPOSITION",
          "Iron sulphide deposition (sour service)", h2sPartialPressureBar > 0.1 ? 0.85 : 0.40,
          "Deposit symptom + H2S pp = " + h2sPartialPressureBar + " bar",
          "Check H2S scavenger performance; apply FeS dispersant; review material"));
    }
  }

  /**
   * Analyses a corrosion symptom.
   *
   * @param s symptom
   */
  private void analyseCorrosion(Symptom s) {
    double cr = s.getMeasurement("corrosionRateMmYr");
    boolean fastCr = !Double.isNaN(cr) && cr > 0.1;

    if (co2PartialPressureBar > 0.5 && material.toLowerCase().contains("carbon")) {
      double score = 0.55 + 0.10 * Math.log10(Math.max(1.0, co2PartialPressureBar));
      if (fastCr) {
        score += 0.20;
      }
      candidates.add(new RootCauseCandidate("CO2_CORROSION",
          "Sweet (CO2) corrosion of carbon steel", Math.min(0.95, score),
          "CO2 pp = " + co2PartialPressureBar + " bar, material " + material
              + (fastCr ? ", measured CR " + cr + " mm/yr" : ""),
          "Run NORSOK M-506 model; verify CI dose; consider CRA upgrade"));
    }
    if (h2sPartialPressureBar > 0.05) {
      candidates
          .add(new RootCauseCandidate("SOUR_CORROSION", "Sour (H2S) corrosion / cracking risk",
              0.70, "H2S pp = " + h2sPartialPressureBar + " bar",
              "Check NACE MR0175 material limits; verify SSC immunity; apply scavenger"));
    }
    if (oxygenPpb > 50.0) {
      double score = 0.60 + 0.20 * Math.min(1.0, oxygenPpb / 1000.0);
      if (fastCr) {
        score += 0.15;
      }
      candidates.add(new RootCauseCandidate("OXYGEN_CORROSION", "Oxygen-driven pitting corrosion",
          Math.min(0.95, score),
          "O2 = " + oxygenPpb + " ppb (above 50 ppb threshold)"
              + (fastCr ? ", measured CR " + cr + " mm/yr" : ""),
          "Eliminate O2 ingress; apply oxygen scavenger; review degassing"));
    }
    if (pH < 5.0) {
      candidates.add(new RootCauseCandidate("ACID_CORROSION", "Acid corrosion (low pH excursion)",
          0.65, "pH = " + pH + " below 5.0",
          "Investigate acid breakthrough or H2S/CO2 spike; apply pyridine-based CI"));
    }
    if (wallShearStressPa > 150.0) {
      candidates
          .add(new RootCauseCandidate("EROSION_CORROSION", "Erosion-corrosion (high wall shear)",
              0.55, "Wall shear = " + wallShearStressPa + " Pa above 150 Pa threshold",
              "Reduce velocity; verify CI persistency under shear; consider CRA"));
    }
    if (chemicals.size() > 0) {
      // chemical-induced corrosion check
      for (ProductionChemical c : chemicals) {
        if (c.getType() == ProductionChemical.ChemicalType.ACID) {
          candidates.add(new RootCauseCandidate("ACID_INDUCED_CORROSION",
              "Acid stimulation corrosion (acid in inventory)", 0.50,
              "Acid declared: " + c.getName(), "Verify acid CI dose; check returns chemistry"));
        }
      }
    }
  }

  /**
   * Analyses an emulsion symptom.
   *
   * @param s symptom
   */
  private void analyseEmulsion(Symptom s) {
    candidates.add(new RootCauseCandidate("EMULSION_FORMATION",
        "Stable oil-water emulsion at separator", 0.50,
        "Emulsion symptom reported; review demulsifier dose and chemistry",
        "Verify demulsifier compatibility; bench-test demulsifier dose; check for surfactant interference"));
    boolean hasCorrInhibitor = false;
    boolean hasDemulsifier = false;
    for (ProductionChemical c : chemicals) {
      if (c.getType() == ProductionChemical.ChemicalType.CORROSION_INHIBITOR) {
        hasCorrInhibitor = true;
      }
      if (c.getType() == ProductionChemical.ChemicalType.DEMULSIFIER) {
        hasDemulsifier = true;
      }
    }
    if (hasCorrInhibitor && hasDemulsifier) {
      candidates.add(new RootCauseCandidate("CI_DEMULSIFIER_CONFLICT",
          "Corrosion inhibitor stabilising emulsion despite demulsifier", 0.65,
          "CI and demulsifier both present — quaternary CI commonly antagonises demulsifier",
          "Switch to demulsifier-friendly CI chemistry; bench-test combined performance"));
    }
  }

  /**
   * Analyses a pH excursion symptom.
   *
   * @param s symptom
   */
  private void analysePH(Symptom s) {
    if (pH < 5.0) {
      candidates.add(
          new RootCauseCandidate("ACID_BREAKTHROUGH", "Residual acid breakthrough or organic acid",
              0.60, "pH = " + pH + "; review well returns and organic acid load",
              "Check returns chemistry; neutralise; verify pH controller"));
    }
    if (pH > 9.0) {
      candidates
          .add(new RootCauseCandidate("CAUSTIC_OVERSHOOT", "Caustic overdose or amine carryover",
              0.55, "pH = " + pH + " above 9; review caustic / amine injection",
              "Reduce caustic dose; check amine slip from gas treating"));
    }
  }

  /**
   * Analyses a flow restriction symptom.
   *
   * @param s symptom
   */
  private void analyseFlowRestriction(Symptom s) {
    candidates.add(new RootCauseCandidate("FLOW_RESTRICTION_GENERIC",
        "Flow restriction — likely deposit or hydrate", 0.30,
        "Flow restriction reported; correlate with deposit symptoms",
        "Inspect for solids; verify hydrate/wax/scale risk"));
  }

  /**
   * Analyses an H2S breakthrough symptom.
   *
   * @param s symptom
   */
  private void analyseH2SBreakthrough(Symptom s) {
    candidates.add(new RootCauseCandidate("SCAVENGER_BREAKTHROUGH",
        "H2S scavenger inventory depleted or under-dosed", 0.75,
        "H2S detected above sales target; verify scavenger residence time and dose",
        "Run H2SScavengerPerformance; increase dose or extend contactor; consider amine treating"));
  }

  /**
   * Analyses an off-spec symptom.
   *
   * @param s symptom
   */
  private void analyseOffSpec(Symptom s) {
    candidates.add(new RootCauseCandidate("OFF_SPEC_GENERIC",
        "Off-spec product — likely separator or treating issue", 0.30,
        "Off-spec reported; correlate with emulsion / dehydration / sweetening performance",
        "Review upstream separator and treating performance; check chemical doses"));
  }

  /**
   * Returns a string safely (non-null).
   *
   * @param s string
   * @return non-null string
   */
  private static String nullSafe(String s) {
    return s == null ? "" : s;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the candidate list (sorted, highest score first).
   *
   * @return list
   */
  public List<RootCauseCandidate> getCandidates() {
    return new ArrayList<RootCauseCandidate>(candidates);
  }

  /**
   * Returns the primary candidate (or null).
   *
   * @return primary or null
   */
  public RootCauseCandidate getPrimary() {
    if (candidates.isEmpty()) {
      return null;
    }
    return candidates.get(0);
  }

  /**
   * Returns identified data gaps.
   *
   * @return list of gap descriptions
   */
  public List<String> getDataGaps() {
    return new ArrayList<String>(dataGaps);
  }

  /**
   * Returns whether analyse() has been run.
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
    Map<String, Object> ctx = new LinkedHashMap<String, Object>();
    ctx.put("temperatureC", temperatureC);
    ctx.put("pressureBara", pressureBara);
    ctx.put("pH", pH);
    ctx.put("calciumMgL", calciumMgL);
    ctx.put("ironMgL", ironMgL);
    ctx.put("oxygenPpb", oxygenPpb);
    ctx.put("h2sPartialPressureBar", h2sPartialPressureBar);
    ctx.put("co2PartialPressureBar", co2PartialPressureBar);
    ctx.put("wallShearStressPa", wallShearStressPa);
    ctx.put("material", material);
    map.put("context", ctx);
    List<Map<String, Object>> sList = new ArrayList<Map<String, Object>>();
    for (Symptom s : symptoms) {
      sList.add(s.toMap());
    }
    map.put("symptoms", sList);
    List<Map<String, Object>> cList = new ArrayList<Map<String, Object>>();
    for (RootCauseCandidate c : candidates) {
      cList.add(c.toMap());
    }
    map.put("candidates", cList);
    map.put("primary", getPrimary() != null ? getPrimary().toMap() : null);
    map.put("dataGaps", dataGaps);
    if (!bayesianPosteriors.isEmpty()) {
      map.put("bayesianPosteriors", new LinkedHashMap<String, Double>(bayesianPosteriors));
    }
    return map;
  }

  /**
   * Adds a piece of independent evidence and updates the Bayesian posterior over root-cause
   * candidates by code, using prior &prop; existing posterior (or
   * {@link RootCauseCandidate#getScore()} on first call) and
   * {@code posterior_i &prop; prior_i &middot; likelihood_i}. Posteriors are normalised to sum to
   * 1.0 across all known candidate codes. Codes not in {@code likelihoods} are treated with
   * likelihood 1.0 (no information).
   *
   * <p>
   * This complements the deterministic rule-based ranking with a probabilistic update suitable for
   * sparse, noisy field evidence (lab assays, sensor diagnostics, operator observations).
   *
   * @param likelihoods map of candidate code to likelihood P(evidence | code), values &gt;= 0
   */
  public void addEvidence(Map<String, Double> likelihoods) {
    if (likelihoods == null || likelihoods.isEmpty()) {
      return;
    }
    if (bayesianPosteriors.isEmpty()) {
      // Initialise from existing candidate scores; if none, use uniform prior over evidence keys.
      if (candidates.isEmpty()) {
        double uniform = 1.0 / likelihoods.size();
        for (String code : likelihoods.keySet()) {
          bayesianPosteriors.put(code, uniform);
        }
      } else {
        for (RootCauseCandidate c : candidates) {
          bayesianPosteriors.put(c.getCode(), Math.max(c.getScore(), 1.0e-6));
        }
        // Ensure all evidence codes are represented (with a small prior)
        for (String code : likelihoods.keySet()) {
          if (!bayesianPosteriors.containsKey(code)) {
            bayesianPosteriors.put(code, 1.0e-3);
          }
        }
      }
    } else {
      // Add any new candidate codes from the evidence with a small prior
      for (String code : likelihoods.keySet()) {
        if (!bayesianPosteriors.containsKey(code)) {
          bayesianPosteriors.put(code, 1.0e-3);
        }
      }
    }
    // Multiply by likelihood (default 1.0 for codes without supplied evidence)
    double sum = 0.0;
    for (Map.Entry<String, Double> e : bayesianPosteriors.entrySet()) {
      double l = likelihoods.containsKey(e.getKey()) ? likelihoods.get(e.getKey()) : 1.0;
      double v = Math.max(0.0, e.getValue() * l);
      e.setValue(v);
      sum += v;
    }
    if (sum > 0.0) {
      for (Map.Entry<String, Double> e : bayesianPosteriors.entrySet()) {
        e.setValue(e.getValue() / sum);
      }
    }
  }

  /**
   * Convenience overload: adds a single piece of evidence for one candidate code.
   *
   * @param code candidate code (e.g. "CO2_CORROSION")
   * @param likelihood P(evidence | this code) &gt;= 0
   */
  public void addEvidence(String code, double likelihood) {
    Map<String, Double> m = new LinkedHashMap<String, Double>();
    m.put(code, likelihood);
    addEvidence(m);
  }

  /**
   * Returns the current Bayesian posterior over candidate codes (defensive copy).
   *
   * @return code -&gt; posterior probability (sums to 1.0 if at least one update has been applied)
   */
  public Map<String, Double> getBayesianPosteriors() {
    return new LinkedHashMap<String, Double>(bayesianPosteriors);
  }

  /**
   * Returns the result as pretty-printed JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    return gson.toJson(toMap());
  }
}
