package neqsim.process.fielddevelopment.workflow;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator;
import neqsim.process.fielddevelopment.economics.SensitivityAnalyzer;
import neqsim.process.fielddevelopment.evaluation.ConceptEvaluator;
import neqsim.process.fielddevelopment.evaluation.ConceptKPIs;
import neqsim.process.fielddevelopment.facility.FacilityBuilder;
import neqsim.process.fielddevelopment.facility.FacilityConfig;
import neqsim.process.fielddevelopment.screening.EconomicsEstimator;
import neqsim.process.fielddevelopment.screening.FlowAssuranceScreener;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Unified field development workflow orchestrator.
 *
 * <p>
 * This class provides a single entry point for running field development studies at different
 * fidelity levels, from early screening through detailed design. It integrates:
 * </p>
 * <ul>
 * <li>PVT characterization and EOS tuning</li>
 * <li>Reservoir modeling (material balance)</li>
 * <li>Well modeling (IPR/VLP nodal analysis)</li>
 * <li>Process simulation</li>
 * <li>Economics and decision support</li>
 * </ul>
 *
 * <h2>Fidelity Levels</h2>
 * <ul>
 * <li><b>SCREENING</b> - Analog-based, ±50% accuracy, minutes to run</li>
 * <li><b>CONCEPTUAL</b> - EOS fluid, IPR/VLP, ±30% accuracy, hours to run</li>
 * <li><b>DETAILED</b> - Tuned EOS, full process sim, Monte Carlo, ±20% accuracy</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Quick screening study
 * FieldDevelopmentWorkflow workflow = new FieldDevelopmentWorkflow("Satellite Discovery");
 * workflow.setFidelityLevel(FidelityLevel.SCREENING);
 * workflow.setConcept(FieldConcept.quickGasTieback("Discovery", 200, 0.02, 25));
 * workflow.setCountryCode("NO");
 * 
 * WorkflowResult result = workflow.run();
 * System.out.println(result.getSummary());
 * 
 * // Progress to conceptual with EOS fluid
 * workflow.setFidelityLevel(FidelityLevel.CONCEPTUAL);
 * workflow.setFluid(tunedFluid);
 * result = workflow.run();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see FieldConcept
 * @see ConceptEvaluator
 * @see CashFlowEngine
 */
public class FieldDevelopmentWorkflow implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Fidelity level for the workflow.
   */
  public enum FidelityLevel {
    /** Screening level: analog-based, ±50% accuracy. */
    SCREENING,
    /** Conceptual level: EOS fluid, IPR/VLP, ±30% accuracy. */
    CONCEPTUAL,
    /** Detailed level: tuned EOS, full process, Monte Carlo, ±20% accuracy. */
    DETAILED
  }

  /**
   * Study phase in field development lifecycle.
   */
  public enum StudyPhase {
    /** Discovery and appraisal phase. */
    DISCOVERY,
    /** Feasibility study (DG1). */
    FEASIBILITY,
    /** Concept selection (DG2). */
    CONCEPT_SELECT,
    /** FEED / detailed design (DG3/DG4). */
    FEED,
    /** Operations phase. */
    OPERATIONS
  }

  // Configuration
  private String projectName;
  private FidelityLevel fidelityLevel = FidelityLevel.SCREENING;
  private StudyPhase studyPhase = StudyPhase.FEASIBILITY;
  private String countryCode = "NO";
  private double discountRate = 0.08;

  // Inputs
  private FieldConcept concept;
  private SystemInterface fluid;
  private SimpleReservoir reservoir;
  private List<WellSystem> wells = new ArrayList<>();
  private ProcessSystem processSystem;
  private FacilityConfig facilityConfig;

  // Production parameters
  private int firstProductionYear = 2027;
  private int fieldLifeYears = 25;
  private double plateauYears = 5;
  private double declineRate = 0.12;

  // Prices
  private double oilPrice = 75.0; // USD/bbl
  private double gasPrice = 0.30; // USD/Sm3
  private double gasTariff = 0.02; // USD/Sm3

  // Results cache
  private WorkflowResult lastResult;

  /**
   * Creates a new field development workflow.
   *
   * @param projectName name of the project/field
   */
  public FieldDevelopmentWorkflow(String projectName) {
    this.projectName = projectName;
  }

  /**
   * Creates a workflow with concept.
   *
   * @param projectName name of the project/field
   * @param concept field concept definition
   */
  public FieldDevelopmentWorkflow(String projectName, FieldConcept concept) {
    this.projectName = projectName;
    this.concept = concept;
  }

  // ============================================================================
  // CONFIGURATION METHODS
  // ============================================================================

  /**
   * Sets the fidelity level.
   *
   * @param level fidelity level
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setFidelityLevel(FidelityLevel level) {
    this.fidelityLevel = level;
    return this;
  }

  /**
   * Sets the study phase.
   *
   * @param phase study phase
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setStudyPhase(StudyPhase phase) {
    this.studyPhase = phase;
    // Auto-adjust fidelity based on phase
    switch (phase) {
      case DISCOVERY:
      case FEASIBILITY:
        if (fidelityLevel == FidelityLevel.DETAILED) {
          fidelityLevel = FidelityLevel.SCREENING;
        }
        break;
      case CONCEPT_SELECT:
        if (fidelityLevel == FidelityLevel.SCREENING) {
          fidelityLevel = FidelityLevel.CONCEPTUAL;
        }
        break;
      case FEED:
      case OPERATIONS:
        fidelityLevel = FidelityLevel.DETAILED;
        break;
    }
    return this;
  }

  /**
   * Sets the country code for tax calculations.
   *
   * @param countryCode ISO country code (e.g., "NO", "UK", "BR")
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setCountryCode(String countryCode) {
    this.countryCode = countryCode;
    return this;
  }

  /**
   * Sets the discount rate for NPV calculations.
   *
   * @param rate discount rate (0-1)
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setDiscountRate(double rate) {
    this.discountRate = rate;
    return this;
  }

  /**
   * Sets the field concept.
   *
   * @param concept field concept definition
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setConcept(FieldConcept concept) {
    this.concept = concept;
    return this;
  }

  /**
   * Sets the reservoir fluid.
   *
   * @param fluid thermodynamic system representing the fluid
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setFluid(SystemInterface fluid) {
    this.fluid = fluid;
    return this;
  }

  /**
   * Sets the reservoir model.
   *
   * @param reservoir simple reservoir for material balance
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setReservoir(SimpleReservoir reservoir) {
    this.reservoir = reservoir;
    return this;
  }

  /**
   * Adds a well model.
   *
   * @param well integrated well system (IPR+VLP)
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow addWell(WellSystem well) {
    this.wells.add(well);
    return this;
  }

  /**
   * Sets the process system.
   *
   * @param process process simulation model
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setProcessSystem(ProcessSystem process) {
    this.processSystem = process;
    return this;
  }

  /**
   * Sets the facility configuration.
   *
   * @param config facility configuration
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setFacilityConfig(FacilityConfig config) {
    this.facilityConfig = config;
    return this;
  }

  /**
   * Sets production timing parameters.
   *
   * @param firstYear first production year
   * @param fieldLife total field life in years
   * @param plateau plateau duration in years
   * @param decline annual decline rate after plateau
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setProductionTiming(int firstYear, int fieldLife, double plateau,
      double decline) {
    this.firstProductionYear = firstYear;
    this.fieldLifeYears = fieldLife;
    this.plateauYears = plateau;
    this.declineRate = decline;
    return this;
  }

  /**
   * Sets commodity prices.
   *
   * @param oil oil price in USD/bbl
   * @param gas gas price in USD/Sm3
   * @param tariff gas transport tariff in USD/Sm3
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setPrices(double oil, double gas, double tariff) {
    this.oilPrice = oil;
    this.gasPrice = gas;
    this.gasTariff = tariff;
    return this;
  }

  // ============================================================================
  // EXECUTION METHODS
  // ============================================================================

  /**
   * Runs the workflow at the configured fidelity level.
   *
   * @return workflow result with all outputs
   */
  public WorkflowResult run() {
    validateInputs();

    switch (fidelityLevel) {
      case SCREENING:
        lastResult = runScreening();
        break;
      case CONCEPTUAL:
        lastResult = runConceptual();
        break;
      case DETAILED:
        lastResult = runDetailed();
        break;
      default:
        lastResult = runScreening();
    }

    return lastResult;
  }

  /**
   * Validates that required inputs are present.
   */
  private void validateInputs() {
    if (concept == null) {
      throw new IllegalStateException("Field concept must be set before running workflow");
    }
  }

  /**
   * Runs screening-level analysis (±50% accuracy).
   */
  private WorkflowResult runScreening() {
    WorkflowResult result = new WorkflowResult(projectName, fidelityLevel);

    // 1. Flow assurance screening
    FlowAssuranceScreener faScreener = new FlowAssuranceScreener();
    result.flowAssuranceResult = faScreener.quickScreen(concept);

    // 2. Economics estimation
    EconomicsEstimator estimator = new EconomicsEstimator(countryCode);
    result.economicsReport = estimator.quickEstimate(concept);

    // 3. Generate production profile (Arps decline)
    ProductionProfileGenerator gen = new ProductionProfileGenerator();
    double peakRate = concept.getWells().getRatePerWellSm3d();
    int wellCount = concept.getWells().getProducerCount();
    double totalPeakRate = peakRate * wellCount;

    result.gasProfile = gen.generateFullProfile(totalPeakRate, 1, (int) plateauYears, declineRate,
        ProductionProfileGenerator.DeclineType.EXPONENTIAL, firstProductionYear, fieldLifeYears);

    // 4. Cash flow analysis
    CashFlowEngine engine = new CashFlowEngine(countryCode);
    engine.setCapex(result.economicsReport.getTotalCapexMUSD(), firstProductionYear - 2);
    engine.addCapex(result.economicsReport.getTotalCapexMUSD() * 0.3, firstProductionYear - 1);
    engine.setOpexPercentOfCapex(0.04);
    engine.setOilPrice(oilPrice);
    engine.setGasPrice(gasPrice);
    engine.setGasTariff(gasTariff);

    // Set production profile
    for (Map.Entry<Integer, Double> entry : result.gasProfile.entrySet()) {
      engine.addAnnualProduction(entry.getKey(), 0, entry.getValue() * 365, 0);
    }

    result.cashFlowResult = engine.calculate(discountRate);

    // 5. Calculate KPIs
    result.npv = result.cashFlowResult.getNpv();
    result.irr = result.cashFlowResult.getIrr();
    result.paybackYears = result.cashFlowResult.getPaybackYears();
    result.breakevenGasPrice = engine.calculateBreakevenGasPrice(discountRate);

    return result;
  }

  /**
   * Runs conceptual-level analysis (±30% accuracy).
   */
  private WorkflowResult runConceptual() {
    // Start with screening
    WorkflowResult result = runScreening();
    result.fidelityLevel = FidelityLevel.CONCEPTUAL;

    // 2. If fluid provided, use EOS-based calculations
    if (fluid != null) {
      // Enhanced flow assurance with actual fluid - use default operating conditions
      FlowAssuranceScreener faScreener = new FlowAssuranceScreener();
      double minTemp = concept.isSubseaTieback() ? 4.0 : 15.0;
      double pressure =
          concept.getWells() != null ? concept.getWells().getTubeheadPressure() : 80.0;
      result.flowAssuranceResult = faScreener.screen(concept, minTemp, pressure);
    }

    // 3. If wells provided, use actual well performance
    if (!wells.isEmpty()) {
      double totalRate = 0;
      for (WellSystem well : wells) {
        well.run();
        totalRate += well.getOperatingFlowRate("Sm3/day");
      }
      // Recalculate production profile with actual well rates
      ProductionProfileGenerator gen = new ProductionProfileGenerator();
      result.gasProfile = gen.generateFullProfile(totalRate, 1, (int) plateauYears, declineRate,
          ProductionProfileGenerator.DeclineType.EXPONENTIAL, firstProductionYear, fieldLifeYears);
    }

    // 4. If facility config provided, use detailed costs
    if (facilityConfig != null) {
      EconomicsEstimator estimator = new EconomicsEstimator(countryCode);
      result.economicsReport = estimator.estimate(concept, facilityConfig);
    }

    // 5. Full concept evaluation
    ConceptEvaluator evaluator = new ConceptEvaluator();
    if (facilityConfig != null) {
      result.conceptKPIs = evaluator.evaluate(concept, facilityConfig);
    } else {
      result.conceptKPIs = evaluator.evaluate(concept);
    }

    // 6. Recalculate economics with updated data
    CashFlowEngine engine = new CashFlowEngine(countryCode);
    engine.setCapex(result.economicsReport.getTotalCapexMUSD(), firstProductionYear - 2);
    engine.addCapex(result.economicsReport.getTotalCapexMUSD() * 0.3, firstProductionYear - 1);
    engine.setOpexPercentOfCapex(0.04);
    engine.setOilPrice(oilPrice);
    engine.setGasPrice(gasPrice);
    engine.setGasTariff(gasTariff);

    for (Map.Entry<Integer, Double> entry : result.gasProfile.entrySet()) {
      engine.addAnnualProduction(entry.getKey(), 0, entry.getValue() * 365, 0);
    }

    result.cashFlowResult = engine.calculate(discountRate);
    result.npv = result.cashFlowResult.getNpv();
    result.irr = result.cashFlowResult.getIrr();
    result.paybackYears = result.cashFlowResult.getPaybackYears();

    return result;
  }

  /**
   * Runs detailed-level analysis (±20% accuracy).
   */
  private WorkflowResult runDetailed() {
    // Start with conceptual
    WorkflowResult result = runConceptual();
    result.fidelityLevel = FidelityLevel.DETAILED;

    // 1. If process system provided, run full simulation
    if (processSystem != null) {
      processSystem.run();
      // Extract actual rates and power consumption
      // result.processResults = extractProcessResults(processSystem);
    }

    // 2. If reservoir provided, run depletion
    if (reservoir != null) {
      // Generate time-varying production profile from reservoir model
      result.gasProfile = runReservoirDepletion(reservoir, wells);
    }

    // 3. Monte Carlo analysis
    CashFlowEngine engine = new CashFlowEngine(countryCode);
    engine.setCapex(result.economicsReport.getTotalCapexMUSD(), firstProductionYear - 2);
    engine.addCapex(result.economicsReport.getTotalCapexMUSD() * 0.3, firstProductionYear - 1);
    engine.setOpexPercentOfCapex(0.04);
    engine.setOilPrice(oilPrice);
    engine.setGasPrice(gasPrice);
    engine.setGasTariff(gasTariff);

    for (Map.Entry<Integer, Double> entry : result.gasProfile.entrySet()) {
      engine.addAnnualProduction(entry.getKey(), 0, entry.getValue() * 365, 0);
    }

    result.cashFlowResult = engine.calculate(discountRate);

    SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, discountRate);
    analyzer.setGasPriceDistribution(gasPrice * 0.7, gasPrice * 1.3);
    analyzer.setCapexDistribution(result.economicsReport.getTotalCapexMUSD() * 0.8,
        result.economicsReport.getTotalCapexMUSD() * 1.2);
    analyzer.setProductionFactorDistribution(0.8, 1.2);

    result.monteCarloResult = analyzer.monteCarloAnalysis(5000);
    result.tornadoResult = analyzer.tornadoAnalysis(0.20);

    result.npv = result.cashFlowResult.getNpv();
    result.npvP10 = result.monteCarloResult.getNpvP10();
    result.npvP50 = result.monteCarloResult.getNpvP50();
    result.npvP90 = result.monteCarloResult.getNpvP90();
    result.irr = result.cashFlowResult.getIrr();
    result.paybackYears = result.cashFlowResult.getPaybackYears();

    return result;
  }

  /**
   * Runs reservoir depletion with wells to generate production profile.
   */
  private Map<Integer, Double> runReservoirDepletion(SimpleReservoir res,
      List<WellSystem> wellList) {
    Map<Integer, Double> profile = new HashMap<>();

    // Simple implementation - can be enhanced
    for (int year = firstProductionYear; year < firstProductionYear + fieldLifeYears; year++) {
      double totalRate = 0;

      // Run each well at current reservoir pressure
      for (WellSystem well : wellList) {
        well.run();
        totalRate += well.getOperatingFlowRate("Sm3/day");
      }

      profile.put(year, totalRate);

      // Deplete reservoir for one year
      // res.runDepletion(365.0);
    }

    return profile;
  }

  // ============================================================================
  // CONVENIENCE METHODS
  // ============================================================================

  /**
   * Creates a quick screening workflow for a gas tieback.
   *
   * @param name project name
   * @param giipGSm3 gas initially in place (GSm3)
   * @param tiebackKm tieback distance (km)
   * @param wellCount number of wells
   * @param ratePerWellMSm3d rate per well (MSm3/d)
   * @param countryCode tax jurisdiction
   * @return configured workflow
   */
  public static FieldDevelopmentWorkflow quickGasTieback(String name, double giipGSm3,
      double tiebackKm, int wellCount, double ratePerWellMSm3d, String countryCode) {
    FieldConcept concept = FieldConcept.gasTieback(name, tiebackKm, wellCount, ratePerWellMSm3d);
    return new FieldDevelopmentWorkflow(name, concept).setCountryCode(countryCode)
        .setFidelityLevel(FidelityLevel.SCREENING);
  }

  /**
   * Creates a quick screening workflow for oil development.
   *
   * @param name project name
   * @param stoiipMMbbl stock tank oil initially in place (MMbbl)
   * @param wellCount number of wells
   * @param ratePerWellBopd rate per well (bopd)
   * @param countryCode tax jurisdiction
   * @return configured workflow
   */
  public static FieldDevelopmentWorkflow quickOilDevelopment(String name, double stoiipMMbbl,
      int wellCount, double ratePerWellBopd, String countryCode) {
    FieldConcept concept = FieldConcept.oilDevelopment(name, wellCount, ratePerWellBopd, 0.1);
    return new FieldDevelopmentWorkflow(name, concept).setCountryCode(countryCode)
        .setFidelityLevel(FidelityLevel.SCREENING);
  }

  /**
   * Generates a comparison report for multiple concepts.
   *
   * @param workflows list of workflows to compare
   * @return markdown comparison table
   */
  public static String generateComparisonReport(List<FieldDevelopmentWorkflow> workflows) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Field Development Concept Comparison\n\n");
    sb.append("| Concept | NPV (MUSD) | IRR (%) | Payback (yr) | CAPEX (MUSD) | Risk |\n");
    sb.append("|---------|------------|---------|--------------|--------------|------|\n");

    for (FieldDevelopmentWorkflow wf : workflows) {
      WorkflowResult r = wf.run();
      String risk =
          r.flowAssuranceResult != null ? r.flowAssuranceResult.getOverallResult().toString()
              : "N/A";
      sb.append(String.format("| %s | %.0f | %.1f | %.1f | %.0f | %s |\n", wf.projectName, r.npv,
          r.irr * 100, r.paybackYears, r.economicsReport.getTotalCapexMUSD(), risk));
    }

    return sb.toString();
  }

  // ============================================================================
  // GETTERS
  // ============================================================================

  /**
   * Gets the project name.
   *
   * @return project name
   */
  public String getProjectName() {
    return projectName;
  }

  /**
   * Gets the last result.
   *
   * @return last workflow result, or null if not run
   */
  public WorkflowResult getLastResult() {
    return lastResult;
  }

  /**
   * Gets the fidelity level.
   *
   * @return fidelity level
   */
  public FidelityLevel getFidelityLevel() {
    return fidelityLevel;
  }

  /**
   * Gets the study phase.
   *
   * @return study phase
   */
  public StudyPhase getStudyPhase() {
    return studyPhase;
  }
}
