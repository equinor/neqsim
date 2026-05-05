package neqsim.process.fielddevelopment.concept;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.facility.FacilityConfig;
import neqsim.process.fielddevelopment.screening.LifecycleEmissionsProfile;

/**
 * Standardized field-development case template for concept comparison.
 *
 * <p>
 * The template bundles the concept definition, generated facility configuration, CAPEX breakdown,
 * production profile, emissions, schedule, and screening economics into one comparable object.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class DevelopmentCaseTemplate implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final String caseType;
  private final FieldConcept concept;
  private final FacilityConfig facilityConfig;
  private final Map<String, Double> capexBreakdownMusd;
  private final Map<Integer, Double> productionProfile;
  private final double annualOpexMusd;
  private final double powerMw;
  private final double annualEmissionsTonnes;
  private final int firstProductionYear;
  private final int developmentDurationMonths;
  private final CashFlowEngine.CashFlowResult economics;
  private final String assumptionsSummary;
  private final DevelopmentCaseUncertainty uncertainty;
  private final LifecycleEmissionsProfile lifecycleEmissionsProfile;

  /**
   * Creates a development case template.
   *
   * @param name case name
   * @param caseType case type label
   * @param concept field concept
   * @param facilityConfig facility configuration or null if not generated
   * @param capexBreakdownMusd CAPEX breakdown in MUSD
   * @param productionProfile annual production profile in Sm3/year for gas or bbl/year for oil
   * @param annualOpexMusd annual OPEX in MUSD/year
   * @param powerMw estimated power demand in MW
   * @param annualEmissionsTonnes annual emissions in tonnes CO2e/year
   * @param firstProductionYear first production year
   * @param developmentDurationMonths development duration in months
   * @param economics cash-flow result
   * @param assumptionsSummary concise assumptions summary
   */
  public DevelopmentCaseTemplate(String name, String caseType, FieldConcept concept,
      FacilityConfig facilityConfig, Map<String, Double> capexBreakdownMusd,
      Map<Integer, Double> productionProfile, double annualOpexMusd, double powerMw,
      double annualEmissionsTonnes, int firstProductionYear, int developmentDurationMonths,
      CashFlowEngine.CashFlowResult economics, String assumptionsSummary) {
    this(name, caseType, concept, facilityConfig, capexBreakdownMusd, productionProfile,
        annualOpexMusd, powerMw, annualEmissionsTonnes, firstProductionYear,
        developmentDurationMonths, economics, assumptionsSummary,
        DevelopmentCaseUncertainty.empty(), LifecycleEmissionsProfile.empty());
  }

  /**
   * Creates a development case template with uncertainty and lifecycle emissions.
   *
   * @param name case name
   * @param caseType case type label
   * @param concept field concept
   * @param facilityConfig facility configuration or null if not generated
   * @param capexBreakdownMusd CAPEX breakdown in MUSD
   * @param productionProfile annual production profile in Sm3/year for gas or bbl/year for oil
   * @param annualOpexMusd annual OPEX in MUSD/year
   * @param powerMw estimated power demand in MW
   * @param annualEmissionsTonnes annual emissions in tonnes CO2e/year
   * @param firstProductionYear first production year
   * @param developmentDurationMonths development duration in months
   * @param economics cash-flow result
   * @param assumptionsSummary concise assumptions summary
   * @param uncertainty probabilistic assumption bundle
   * @param lifecycleEmissionsProfile lifecycle emissions time series
   */
  public DevelopmentCaseTemplate(String name, String caseType, FieldConcept concept,
      FacilityConfig facilityConfig, Map<String, Double> capexBreakdownMusd,
      Map<Integer, Double> productionProfile, double annualOpexMusd, double powerMw,
      double annualEmissionsTonnes, int firstProductionYear, int developmentDurationMonths,
      CashFlowEngine.CashFlowResult economics, String assumptionsSummary,
      DevelopmentCaseUncertainty uncertainty, LifecycleEmissionsProfile lifecycleEmissionsProfile) {
    this.name = name;
    this.caseType = caseType;
    this.concept = concept;
    this.facilityConfig = facilityConfig;
    this.capexBreakdownMusd = new LinkedHashMap<String, Double>(capexBreakdownMusd);
    this.productionProfile = new LinkedHashMap<Integer, Double>(productionProfile);
    this.annualOpexMusd = annualOpexMusd;
    this.powerMw = powerMw;
    this.annualEmissionsTonnes = annualEmissionsTonnes;
    this.firstProductionYear = firstProductionYear;
    this.developmentDurationMonths = developmentDurationMonths;
    this.economics = economics;
    this.assumptionsSummary = assumptionsSummary;
    this.uncertainty = uncertainty == null ? DevelopmentCaseUncertainty.empty() : uncertainty;
    this.lifecycleEmissionsProfile =
        lifecycleEmissionsProfile == null ? LifecycleEmissionsProfile.empty()
            : lifecycleEmissionsProfile;
  }

  /**
   * Gets the case name.
   *
   * @return case name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the case name.
   *
   * @return case name
   */
  public String getCaseName() {
    return name;
  }

  /**
   * Gets the case type label.
   *
   * @return case type
   */
  public String getCaseType() {
    return caseType;
  }

  /**
   * Gets the field concept.
   *
   * @return field concept
   */
  public FieldConcept getConcept() {
    return concept;
  }

  /**
   * Gets the generated facility configuration.
   *
   * @return facility configuration or null
   */
  public FacilityConfig getFacilityConfig() {
    return facilityConfig;
  }

  /**
   * Gets the CAPEX breakdown.
   *
   * @return defensive copy of CAPEX breakdown in MUSD
   */
  public Map<String, Double> getCapexBreakdownMusd() {
    return new LinkedHashMap<String, Double>(capexBreakdownMusd);
  }

  /**
   * Gets the annual production profile.
   *
   * @return defensive copy of annual production profile
   */
  public Map<Integer, Double> getProductionProfile() {
    return new LinkedHashMap<Integer, Double>(productionProfile);
  }

  /**
   * Gets the total CAPEX.
   *
   * @return total CAPEX in MUSD
   */
  public double getTotalCapexMusd() {
    double total = 0.0;
    for (Double value : capexBreakdownMusd.values()) {
      total += value;
    }
    return total;
  }

  /**
   * Gets the annual OPEX.
   *
   * @return annual OPEX in MUSD/year
   */
  public double getAnnualOpexMusd() {
    return annualOpexMusd;
  }

  /**
   * Gets the estimated power demand.
   *
   * @return power demand in MW
   */
  public double getPowerMw() {
    return powerMw;
  }

  /**
   * Gets annual emissions.
   *
   * @return annual emissions in tonnes CO2e/year
   */
  public double getAnnualEmissionsTonnes() {
    return annualEmissionsTonnes;
  }

  /**
   * Gets the first production year.
   *
   * @return first production year
   */
  public int getFirstProductionYear() {
    return firstProductionYear;
  }

  /**
   * Gets the development duration.
   *
   * @return development duration in months
   */
  public int getDevelopmentDurationMonths() {
    return developmentDurationMonths;
  }

  /**
   * Gets the screening economics.
   *
   * @return cash-flow result
   */
  public CashFlowEngine.CashFlowResult getEconomics() {
    return economics;
  }

  /**
   * Gets the assumptions summary.
   *
   * @return assumptions summary
   */
  public String getAssumptionsSummary() {
    return assumptionsSummary;
  }

  /**
   * Gets the probabilistic assumptions attached to this template.
   *
   * @return uncertainty bundle
   */
  public DevelopmentCaseUncertainty getUncertainty() {
    return uncertainty;
  }

  /**
   * Gets the lifecycle emissions profile.
   *
   * @return lifecycle emissions profile
   */
  public LifecycleEmissionsProfile getLifecycleEmissionsProfile() {
    return lifecycleEmissionsProfile;
  }

  /**
   * Gets a one-line summary for comparison tables.
   *
   * @return formatted summary
   */
  public String getSummary() {
    double npv = economics != null ? economics.getNpv() : Double.NaN;
    double irr = economics != null ? economics.getIrr() : Double.NaN;
    return String.format(
        "%s [%s]: CAPEX %.0f MUSD, OPEX %.1f MUSD/y, power %.1f MW, " + "NPV %.0f MUSD, IRR %.1f%%",
        name, caseType, getTotalCapexMusd(), annualOpexMusd, powerMw, npv, irr * 100.0);
  }

  @Override
  public String toString() {
    return getSummary();
  }
}
