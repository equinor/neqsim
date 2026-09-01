package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.reactor.digestion.ModelFidelity;

/**
 * Transparent techno-economic and carbon-intensity comparator for biological-process concepts.
 *
 * <p>
 * The evaluator consumes annualized outputs from process models instead of embedding a particular flowsheet. It keeps
 * physical closure, evidence, fidelity, economics, and emissions visible in every result so a favorable economic rank
 * cannot silently hide an unqualified process basis.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class BioprocessConceptEvaluator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Available result-ordering metrics. */
  public enum RankingMetric {
    /** Highest net present value first. */
    NET_PRESENT_VALUE,
    /** Lowest levelized product cost first. */
    LEVELIZED_PRODUCT_COST,
    /** Lowest carbon intensity first. */
    CARBON_INTENSITY
  }

  /**
   * Input basis for one concept.
   */
  public static class ConceptCase implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Concept name. */
    private final String name;
    /** Annual product quantity. */
    private double annualProduct;
    /** Product unit. */
    private String productUnit = "unit";
    /** Product energy content in MWh per product unit. */
    private double energyMWhPerUnit;
    /** Product price in currency per product unit. */
    private double productPricePerUnit;
    /** Installed capital expenditure. */
    private double capitalExpenditure;
    /** Annual operating expenditure. */
    private double annualOperatingExpenditure;
    /** Annual greenhouse-gas emissions in tCO2-equivalent. */
    private double annualEmissionsTCO2Equivalent;
    /** Process mass-closure fraction. */
    private double massClosureFraction = Double.NaN;
    /** Process carbon-closure fraction. */
    private double carbonClosureFraction = Double.NaN;
    /** Model fidelity. */
    private ModelFidelity modelFidelity = ModelFidelity.SCREENING;
    /** Evidence reference. */
    private String evidenceReference = "";

    /**
     * Creates a named concept basis.
     *
     * @param name concept name
     */
    public ConceptCase(String name) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Concept name must be provided");
      }
      this.name = name;
    }

    /**
     * Sets annual product output and its unit.
     *
     * @param quantity annual product quantity
     * @param unit product unit
     * @return this concept basis
     */
    public ConceptCase setAnnualProduct(double quantity, String unit) {
      annualProduct = quantity;
      productUnit = unit;
      return this;
    }

    /**
     * Sets product energy content.
     *
     * @param valueMWhPerUnit energy content in MWh per product unit
     * @return this concept basis
     */
    public ConceptCase setEnergyMWhPerUnit(double valueMWhPerUnit) {
      energyMWhPerUnit = valueMWhPerUnit;
      return this;
    }

    /**
     * Sets product price.
     *
     * @param pricePerUnit price per product unit
     * @return this concept basis
     */
    public ConceptCase setProductPricePerUnit(double pricePerUnit) {
      productPricePerUnit = pricePerUnit;
      return this;
    }

    /**
     * Sets capital and annual operating expenditure on a consistent currency basis.
     *
     * @param capital capital expenditure
     * @param annualOperating annual operating expenditure
     * @return this concept basis
     */
    public ConceptCase setCosts(double capital, double annualOperating) {
      capitalExpenditure = capital;
      annualOperatingExpenditure = annualOperating;
      return this;
    }

    /**
     * Sets annual greenhouse-gas emissions.
     *
     * @param emissionsTCO2Equivalent emissions in tCO2-equivalent per year
     * @return this concept basis
     */
    public ConceptCase setAnnualEmissionsTCO2Equivalent(double emissionsTCO2Equivalent) {
      annualEmissionsTCO2Equivalent = emissionsTCO2Equivalent;
      return this;
    }

    /**
     * Sets mass and carbon closure reported by the process simulation.
     *
     * @param massClosure mass-closure fraction
     * @param carbonClosure carbon-closure fraction
     * @return this concept basis
     */
    public ConceptCase setClosure(double massClosure, double carbonClosure) {
      massClosureFraction = massClosure;
      carbonClosureFraction = carbonClosure;
      return this;
    }

    /**
     * Sets the model qualification basis.
     *
     * @param fidelity model fidelity
     * @param evidence evidence or data-set reference
     * @return this concept basis
     */
    public ConceptCase setQualification(ModelFidelity fidelity, String evidence) {
      if (fidelity == null) {
        throw new IllegalArgumentException("Model fidelity must be provided");
      }
      modelFidelity = fidelity;
      evidenceReference = evidence == null ? "" : evidence;
      return this;
    }

    /**
     * Validates the concept input.
     */
    private void validate() {
      if (annualProduct <= 0.0 || energyMWhPerUnit <= 0.0) {
        throw new IllegalArgumentException("Annual product and energy content must be positive for " + name);
      }
      if (productPricePerUnit < 0.0 || capitalExpenditure < 0.0 || annualOperatingExpenditure < 0.0) {
        throw new IllegalArgumentException("Price and costs cannot be negative for " + name);
      }
      if (productUnit == null || productUnit.trim().isEmpty()) {
        throw new IllegalArgumentException("Product unit must be provided for " + name);
      }
    }
  }

  /**
   * Immutable calculated result for one concept.
   */
  public static class ConceptResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Concept name. */
    private final String name;
    /** Product unit. */
    private final String productUnit;
    /** Net present value. */
    private final double netPresentValue;
    /** Levelized product cost. */
    private final double levelizedProductCost;
    /** Carbon intensity in kgCO2-equivalent/MWh. */
    private final double carbonIntensityKgCO2EquivalentPerMWh;
    /** Annual net cash flow. */
    private final double annualNetCashFlow;
    /** Model fidelity. */
    private final ModelFidelity modelFidelity;
    /** Evidence reference. */
    private final String evidenceReference;
    /** Whether closure meets the configured tolerance. */
    private final boolean closureQualified;
    /** Result warnings. */
    private final List<String> warnings;

    /**
     * Creates a calculated concept result.
     *
     * @param concept concept basis
     * @param npv net present value
     * @param levelizedCost levelized product cost
     * @param carbonIntensity carbon intensity
     * @param netCashFlow annual net cash flow
     * @param qualified true when closure is qualified
     * @param resultWarnings result warnings
     */
    private ConceptResult(ConceptCase concept, double npv, double levelizedCost, double carbonIntensity,
        double netCashFlow, boolean qualified, List<String> resultWarnings) {
      name = concept.name;
      productUnit = concept.productUnit;
      netPresentValue = npv;
      levelizedProductCost = levelizedCost;
      carbonIntensityKgCO2EquivalentPerMWh = carbonIntensity;
      annualNetCashFlow = netCashFlow;
      modelFidelity = concept.modelFidelity;
      evidenceReference = concept.evidenceReference;
      closureQualified = qualified;
      warnings = Collections.unmodifiableList(new ArrayList<String>(resultWarnings));
    }

    /**
     * Returns a report-ready result map.
     *
     * @return result map
     */
    public Map<String, Object> toMap() {
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("name", name);
      values.put("productUnit", productUnit);
      values.put("netPresentValue", netPresentValue);
      values.put("annualNetCashFlow", annualNetCashFlow);
      values.put("levelizedProductCost", levelizedProductCost);
      values.put("carbonIntensity_kgCO2EquivalentPerMWh", carbonIntensityKgCO2EquivalentPerMWh);
      values.put("modelFidelity", modelFidelity.name());
      values.put("evidenceReference", evidenceReference);
      values.put("closureQualified", closureQualified);
      values.put("warnings", warnings);
      return values;
    }

    /** @return concept name */
    public String getName() {
      return name;
    }

    /** @return net present value */
    public double getNetPresentValue() {
      return netPresentValue;
    }

    /** @return levelized product cost */
    public double getLevelizedProductCost() {
      return levelizedProductCost;
    }

    /** @return carbon intensity in kgCO2-equivalent/MWh */
    public double getCarbonIntensityKgCO2EquivalentPerMWh() {
      return carbonIntensityKgCO2EquivalentPerMWh;
    }

    /** @return true when process closure is within tolerance */
    public boolean isClosureQualified() {
      return closureQualified;
    }

    /** @return immutable warning list */
    public List<String> getWarnings() {
      return warnings;
    }
  }

  /** Concept inputs. */
  private final List<ConceptCase> concepts = new ArrayList<ConceptCase>();
  /** Calculated results. */
  private final List<ConceptResult> results = new ArrayList<ConceptResult>();
  /** Real annual discount rate. */
  private double discountRate = 0.10;
  /** Project life in years. */
  private int projectLifeYears = 20;
  /** Permitted absolute deviation of closure from unity. */
  private double closureTolerance = 0.001;

  /**
   * Sets the economic evaluation basis.
   *
   * @param annualDiscountRate annual discount rate as a fraction
   * @param lifeYears project life in years
   */
  public void setEconomicBasis(double annualDiscountRate, int lifeYears) {
    if (annualDiscountRate < 0.0 || lifeYears <= 0) {
      throw new IllegalArgumentException("Discount rate cannot be negative and project life must be positive");
    }
    discountRate = annualDiscountRate;
    projectLifeYears = lifeYears;
  }

  /**
   * Sets the absolute mass and carbon closure tolerance.
   *
   * @param tolerance closure tolerance as a fraction
   */
  public void setClosureTolerance(double tolerance) {
    if (tolerance < 0.0) {
      throw new IllegalArgumentException("Closure tolerance cannot be negative");
    }
    closureTolerance = tolerance;
  }

  /**
   * Adds a concept case.
   *
   * @param concept concept input
   */
  public void addConcept(ConceptCase concept) {
    if (concept == null) {
      throw new IllegalArgumentException("Concept must be provided");
    }
    concept.validate();
    concepts.add(concept);
  }

  /**
   * Calculates economic, environmental, and qualification metrics for all concepts.
   */
  public void evaluate() {
    results.clear();
    double capitalRecoveryFactor = calculateCapitalRecoveryFactor();
    double presentValueFactor = calculatePresentValueFactor();
    for (ConceptCase concept : concepts) {
      concept.validate();
      double annualRevenue = concept.annualProduct * concept.productPricePerUnit;
      double annualNetCash = annualRevenue - concept.annualOperatingExpenditure;
      double netPresentValue = -concept.capitalExpenditure + annualNetCash * presentValueFactor;
      double levelizedCost = (concept.capitalExpenditure * capitalRecoveryFactor + concept.annualOperatingExpenditure)
          / concept.annualProduct;
      double annualEnergyMWh = concept.annualProduct * concept.energyMWhPerUnit;
      double carbonIntensity = concept.annualEmissionsTCO2Equivalent * 1000.0 / annualEnergyMWh;

      List<String> warnings = new ArrayList<String>();
      boolean closureQualified = isClosureQualified(concept.massClosureFraction)
          && isClosureQualified(concept.carbonClosureFraction);
      if (!closureQualified) {
        warnings.add("Mass and carbon closure must be supplied and remain within the configured tolerance");
      }
      if (concept.modelFidelity == ModelFidelity.SCREENING) {
        warnings.add("Screening-fidelity result requires calibration before investment or design decisions");
      }
      if (concept.evidenceReference.trim().isEmpty()) {
        warnings.add("No evidence or data-set reference has been recorded");
      }
      results.add(new ConceptResult(concept, netPresentValue, levelizedCost, carbonIntensity, annualNetCash,
          closureQualified, warnings));
    }
  }

  /**
   * Returns calculated results ordered by the selected metric.
   *
   * @param metric ranking metric
   * @return immutable ranked results
   */
  public List<ConceptResult> getRankedResults(final RankingMetric metric) {
    if (metric == null) {
      throw new IllegalArgumentException("Ranking metric must be provided");
    }
    evaluate();
    List<ConceptResult> ranked = new ArrayList<ConceptResult>(results);
    Collections.sort(ranked, new Comparator<ConceptResult>() {
      @Override
      public int compare(ConceptResult first, ConceptResult second) {
        if (metric == RankingMetric.NET_PRESENT_VALUE) {
          return Double.compare(second.netPresentValue, first.netPresentValue);
        }
        if (metric == RankingMetric.CARBON_INTENSITY) {
          return Double.compare(first.carbonIntensityKgCO2EquivalentPerMWh,
              second.carbonIntensityKgCO2EquivalentPerMWh);
        }
        return Double.compare(first.levelizedProductCost, second.levelizedProductCost);
      }
    });
    return Collections.unmodifiableList(ranked);
  }

  /**
   * Returns true when a supplied closure value is finite and within tolerance.
   *
   * @param closure closure fraction
   * @return qualification flag
   */
  private boolean isClosureQualified(double closure) {
    return !Double.isNaN(closure) && Math.abs(closure - 1.0) <= closureTolerance;
  }

  /**
   * Calculates the capital recovery factor.
   *
   * @return capital recovery factor
   */
  private double calculateCapitalRecoveryFactor() {
    if (discountRate == 0.0) {
      return 1.0 / projectLifeYears;
    }
    double growth = Math.pow(1.0 + discountRate, projectLifeYears);
    return discountRate * growth / (growth - 1.0);
  }

  /**
   * Calculates the present-value factor for a uniform annual cash flow.
   *
   * @return present-value factor
   */
  private double calculatePresentValueFactor() {
    if (discountRate == 0.0) {
      return projectLifeYears;
    }
    return (1.0 - Math.pow(1.0 + discountRate, -projectLifeYears)) / discountRate;
  }
}
