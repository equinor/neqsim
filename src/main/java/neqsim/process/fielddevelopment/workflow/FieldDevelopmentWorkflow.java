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
import neqsim.process.fielddevelopment.screening.EmissionsTracker;
import neqsim.process.fielddevelopment.screening.FlowAssuranceScreener;
import neqsim.process.fielddevelopment.subsea.SubseaProductionSystem;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.TiebackAnalyzer;
import neqsim.process.fielddevelopment.tieback.TiebackReport;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.mechanicaldesign.FieldDevelopmentDesignOrchestrator;
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Unified field development workflow orchestrator.
 *
 * <p>
 * This class provides a single entry point for running field development studies at different
 * fidelity levels, from early screening through detailed design. The workflow is designed to
 * support education and industry applications aligned with academic programs such as NTNU's
 * <b>TPG4230 - Underground reservoirs fluid production and injection</b> course.
 * </p>
 *
 * <h2>TPG4230 Course Topic Coverage</h2>
 * <p>
 * This framework addresses key topics from the course:
 * </p>
 * <ul>
 * <li><b>Field Lifecycle Management</b> - Discovery through operations with progressive
 * refinement</li>
 * <li><b>PVT Characterization</b> - EOS selection and tuning to laboratory data</li>
 * <li><b>Reservoir Material Balance</b> - Tank models with production/injection tracking</li>
 * <li><b>Well Performance (IPR/VLP)</b> - Inflow performance and vertical lift modeling</li>
 * <li><b>Production Network Optimization</b> - Multi-well gathering system equilibrium</li>
 * <li><b>Economic Evaluation</b> - NPV, IRR with country-specific fiscal terms</li>
 * <li><b>Flow Assurance Screening</b> - Hydrates, wax, corrosion risk assessment</li>
 * </ul>
 *
 * <h2>Integration Architecture</h2>
 * <p>
 * The workflow integrates multiple NeqSim subsystems:
 * </p>
 * <ul>
 * <li>PVT characterization and EOS tuning ({@code thermo.system}, {@code pvtsimulation})</li>
 * <li>Reservoir modeling with material balance ({@code process.equipment.reservoir})</li>
 * <li>Well modeling with IPR/VLP nodal analysis ({@code WellSystem}, {@code WellFlow})</li>
 * <li>Process simulation ({@code ProcessSystem})</li>
 * <li>Economics with tax models ({@code CashFlowEngine}, {@code TaxModel})</li>
 * <li>Flow assurance screening ({@code FlowAssuranceScreener})</li>
 * </ul>
 *
 * <h2>Fidelity Levels</h2>
 * <ul>
 * <li><b>SCREENING</b> - Analog-based correlations, ±50% accuracy, suitable for portfolio
 * screening</li>
 * <li><b>CONCEPTUAL</b> - EOS fluid, IPR/VLP models, ±30% accuracy, suitable for concept
 * selection</li>
 * <li><b>DETAILED</b> - Tuned EOS, full process simulation, Monte Carlo economics, ±20%
 * accuracy</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Quick screening study for gas tieback
 * FieldDevelopmentWorkflow workflow =
 *     FieldDevelopmentWorkflow.quickGasTieback("Satellite Discovery", 50.0, 25.0, 4, 2.0, "NO");
 * WorkflowResult result = workflow.run();
 * System.out.println(result.getSummary());
 * 
 * // Progress to conceptual with EOS-tuned fluid
 * workflow.setFidelityLevel(FidelityLevel.CONCEPTUAL);
 * workflow.setFluid(tunedFluid); // From PVT regression
 * result = workflow.run();
 * 
 * // Full detailed study with Monte Carlo
 * workflow.setFidelityLevel(FidelityLevel.DETAILED);
 * workflow.setMonteCarloIterations(1000);
 * result = workflow.run();
 * System.out.println("NPV P50: " + result.getNpvP50() + " MUSD");
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see FieldConcept
 * @see ConceptEvaluator
 * @see CashFlowEngine
 * @see neqsim.process.fielddevelopment.network.NetworkSolver
 * @see neqsim.process.fielddevelopment.reservoir.InjectionStrategy
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

  // Subsea configuration
  private SubseaProductionSystem subseaSystem;
  private List<HostFacility> potentialHosts = new ArrayList<>();
  private TiebackAnalyzer tiebackAnalyzer;
  private boolean runSubseaAnalysis = true;
  private double waterDepthM = 350.0;
  private double tiebackDistanceKm = 25.0;
  private SubseaProductionSystem.SubseaArchitecture subseaArchitecture =
      SubseaProductionSystem.SubseaArchitecture.MANIFOLD_CLUSTER;

  // Production parameters
  private int firstProductionYear = 2027;
  private int fieldLifeYears = 25;
  private double plateauYears = 5;
  private double declineRate = 0.12;

  // Prices
  private double oilPrice = 75.0; // USD/bbl
  private double gasPrice = 0.30; // USD/Sm3
  private double gasTariff = 0.02; // USD/Sm3

  // Mechanical design and emissions
  private boolean runMechanicalDesign = true;
  private boolean calculateEmissions = true;
  private String powerSupplyType = "GAS_TURBINE"; // GAS_TURBINE, POWER_FROM_SHORE, COMBINED_CYCLE
  private double gridEmissionFactor = 0.05; // kg CO2/kWh (Nordic grid default)
  private String designStandard = "Equinor"; // Company design standard
  private int monteCarloIterations = 5000;

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
   * Enables or disables mechanical design calculations.
   *
   * @param enabled true to run mechanical design
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setRunMechanicalDesign(boolean enabled) {
    this.runMechanicalDesign = enabled;
    return this;
  }

  /**
   * Enables or disables CO2 emissions calculations.
   *
   * @param enabled true to calculate emissions
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setCalculateEmissions(boolean enabled) {
    this.calculateEmissions = enabled;
    return this;
  }

  /**
   * Sets the power supply type for emissions calculations.
   *
   * @param type power supply type: GAS_TURBINE, POWER_FROM_SHORE, COMBINED_CYCLE, DIESEL
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setPowerSupplyType(String type) {
    this.powerSupplyType = type;
    return this;
  }

  /**
   * Sets the grid emission factor for power-from-shore.
   *
   * @param factor emission factor in kg CO2/kWh (e.g., 0.05 for Nordic, 0.4 for UK)
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setGridEmissionFactor(double factor) {
    this.gridEmissionFactor = factor;
    return this;
  }

  /**
   * Sets the design standard for mechanical design.
   *
   * @param standard company design standard (e.g., "Equinor", "Shell", "BP")
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setDesignStandard(String standard) {
    this.designStandard = standard;
    return this;
  }

  /**
   * Sets the number of Monte Carlo iterations for uncertainty analysis.
   *
   * @param iterations number of iterations (typically 1000-10000)
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setMonteCarloIterations(int iterations) {
    this.monteCarloIterations = iterations;
    return this;
  }

  // ============================================================================
  // SUBSEA CONFIGURATION METHODS
  // ============================================================================

  /**
   * Sets the subsea production system.
   *
   * @param subsea configured subsea system
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setSubseaSystem(SubseaProductionSystem subsea) {
    this.subseaSystem = subsea;
    return this;
  }

  /**
   * Adds a potential host facility for tieback analysis.
   *
   * @param host host facility
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow addHostFacility(HostFacility host) {
    this.potentialHosts.add(host);
    return this;
  }

  /**
   * Sets the tieback analyzer for subsea economics.
   *
   * @param analyzer tieback analyzer
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setTiebackAnalyzer(TiebackAnalyzer analyzer) {
    this.tiebackAnalyzer = analyzer;
    return this;
  }

  /**
   * Enables or disables subsea analysis.
   *
   * @param enabled true to run subsea analysis
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setRunSubseaAnalysis(boolean enabled) {
    this.runSubseaAnalysis = enabled;
    return this;
  }

  /**
   * Sets water depth for subsea development.
   *
   * @param depthM water depth in meters
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setWaterDepthM(double depthM) {
    this.waterDepthM = depthM;
    return this;
  }

  /**
   * Sets tieback distance for subsea development.
   *
   * @param distanceKm tieback distance in kilometers
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setTiebackDistanceKm(double distanceKm) {
    this.tiebackDistanceKm = distanceKm;
    return this;
  }

  /**
   * Sets subsea architecture type.
   *
   * @param arch subsea architecture (DIRECT_TIEBACK, MANIFOLD_CLUSTER, etc.)
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow setSubseaArchitecture(
      SubseaProductionSystem.SubseaArchitecture arch) {
    this.subseaArchitecture = arch;
    return this;
  }

  /**
   * Configures subsea parameters from concept.
   *
   * <p>
   * Extracts subsea-relevant parameters from the field concept and sets up subsea system
   * automatically.
   * </p>
   *
   * @return this for chaining
   */
  public FieldDevelopmentWorkflow configureSubseaFromConcept() {
    if (concept == null) {
      throw new IllegalStateException("Concept must be set before configuring subsea");
    }

    // Extract tieback distance from infrastructure
    if (concept.getInfrastructure() != null) {
      this.tiebackDistanceKm = concept.getInfrastructure().getTiebackLength();
    }

    // Auto-configure if this is a subsea tieback
    if (concept.isSubseaTieback() && subseaSystem == null) {
      subseaSystem = new SubseaProductionSystem(projectName + " Subsea");
      subseaSystem.setArchitecture(subseaArchitecture).setWaterDepthM(waterDepthM)
          .setTiebackDistanceKm(tiebackDistanceKm)
          .setWellCount(concept.getWells() != null ? concept.getWells().getProducerCount() : 2);

      if (fluid != null) {
        subseaSystem.setReservoirFluid(fluid);
      }
    }

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
   *
   * @return workflow result containing screening analysis outputs
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
   *
   * @return workflow result containing conceptual analysis outputs
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

    // 7. Subsea analysis if applicable
    if (runSubseaAnalysis && concept.isSubseaTieback()) {
      runSubseaAnalysis(result);
    }

    return result;
  }

  /**
   * Runs detailed-level analysis (±20% accuracy).
   */
  private WorkflowResult runDetailed() {
    // Start with conceptual
    WorkflowResult result = runConceptual();
    result.fidelityLevel = FidelityLevel.DETAILED;

    // 1. If subsea system provided, run detailed subsea simulation
    if (subseaSystem != null && runSubseaAnalysis) {
      if (fluid != null && subseaSystem.getWells().isEmpty()) {
        subseaSystem.setReservoirFluid(fluid);
        subseaSystem.build();
      }
      subseaSystem.run();

      // Extract subsea results
      SubseaProductionSystem.SubseaSystemResult subseaResult = subseaSystem.getResult();
      result.subseaSystemResult = subseaResult;

      // Update CAPEX with detailed subsea costs
      if (result.economicsReport != null) {
        double additionalSubseaCapex = subseaResult.getTotalSubseaCapexMusd();
        // Subsea CAPEX already included in concept estimate, but update with detailed values
        result.subseaCapexMusd = additionalSubseaCapex;
      }

      result.arrivalPressureBara = subseaResult.getArrivalPressureBara();
      result.arrivalTemperatureC = subseaResult.getArrivalTemperatureC();
    }

    // 2. If process system provided, run full simulation with mechanical design
    if (processSystem != null) {
      processSystem.run();

      // 1a. Extract power consumption from process equipment
      result.totalPowerMW = calculateTotalPowerMW(processSystem);
      result.powerBreakdownMW = calculatePowerBreakdown(processSystem);

      // 1b. Run mechanical design if enabled
      if (runMechanicalDesign) {
        SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(processSystem);
        sysMecDesign.setCompanySpecificDesignStandards(designStandard);
        sysMecDesign.runDesignCalculation();
        result.mechanicalDesign = sysMecDesign;
        result.totalEquipmentWeightTonnes = sysMecDesign.getTotalWeight() / 1000.0;
        result.totalFootprintM2 = sysMecDesign.getTotalPlotSpace();
      }

      // 1c. Calculate CO2 emissions if enabled
      if (calculateEmissions) {
        calculateProcessEmissions(result, processSystem);
      }
    } else if (calculateEmissions && concept != null) {
      // Use concept-level emissions estimation if no process system
      calculateConceptEmissions(result);
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

    result.monteCarloResult = analyzer.monteCarloAnalysis(monteCarloIterations);
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
   * Runs subsea analysis and tieback evaluation.
   *
   * @param result workflow result to populate
   */
  private void runSubseaAnalysis(WorkflowResult result) {
    // Initialize tieback analyzer if not set
    if (tiebackAnalyzer == null) {
      tiebackAnalyzer = new TiebackAnalyzer();
    }

    // Run tieback analysis if hosts are configured
    if (!potentialHosts.isEmpty()) {
      TiebackReport report = tiebackAnalyzer.analyze(concept, potentialHosts);
      result.tiebackReport = report;

      if (report.getBestFeasibleOption() != null) {
        result.selectedTiebackOption = report.getBestFeasibleOption();
        result.subseaCapexMusd = report.getBestFeasibleOption().getTotalCapexMusd();
        result.arrivalPressureBara = report.getBestFeasibleOption().getArrivalPressureBara();
      }
    }

    // If subsea system provided, use it for hydraulic analysis
    if (subseaSystem != null) {
      if (fluid != null) {
        subseaSystem.setReservoirFluid(fluid);
      }

      // Configure from concept if not already done
      if (concept != null && concept.getWells() != null) {
        subseaSystem.setWellCount(concept.getWells().getProducerCount());
        subseaSystem.setRatePerWell(concept.getWells().getRatePerWellSm3d());
      }

      subseaSystem.setWaterDepthM(waterDepthM);
      subseaSystem.setTiebackDistanceKm(tiebackDistanceKm);
      subseaSystem.setArchitecture(subseaArchitecture);

      // Build and run if fluid is available
      if (fluid != null) {
        try {
          subseaSystem.build();
          subseaSystem.run();

          SubseaProductionSystem.SubseaSystemResult subseaResult = subseaSystem.getResult();
          result.subseaSystemResult = subseaResult;

          // Update arrival conditions
          result.arrivalPressureBara = subseaResult.getArrivalPressureBara();
          result.arrivalTemperatureC = subseaResult.getArrivalTemperatureC();

          // Update CAPEX if not already set from tieback analysis
          if (result.subseaCapexMusd <= 0) {
            result.subseaCapexMusd = subseaResult.getTotalSubseaCapexMusd();
          }
        } catch (Exception e) {
          // Subsea simulation failed, use tieback estimates only
          result.subseaSimulationError = e.getMessage();
        }
      }
    } else if (concept.isSubseaTieback()) {
      // Auto-create subsea system from concept for cost estimation
      SubseaProductionSystem autoSubsea = new SubseaProductionSystem(projectName + " Auto Subsea");
      autoSubsea.setArchitecture(subseaArchitecture).setWaterDepthM(waterDepthM)
          .setTiebackDistanceKm(tiebackDistanceKm)
          .setWellCount(concept.getWells() != null ? concept.getWells().getProducerCount() : 2);

      // Get cost estimate (without running hydraulics)
      result.subseaCapexMusd = estimateSubseaCapex(autoSubsea);
    }
  }

  /**
   * Estimates subsea CAPEX from system configuration.
   *
   * @param subsea subsea production system
   * @return estimated CAPEX in MUSD
   */
  private double estimateSubseaCapex(SubseaProductionSystem subsea) {
    double subseaTreeCost = 25.0; // MUSD per tree
    double manifoldCost = 35.0; // MUSD base
    double pipelineCostPerKm = 2.5; // MUSD/km base
    double umbilicalCostPerKm = 1.0; // MUSD/km

    int wells = subsea.getWellCount();
    double distance = subsea.getTiebackDistanceKm();
    double depth = subsea.getWaterDepthM();

    // Adjust for water depth
    double depthFactor = 1.0;
    if (depth > 500) {
      depthFactor = 1.0 + (depth - 500) / 1000.0;
    }

    double treeCost = wells * subseaTreeCost;
    double manifold = manifoldCost * depthFactor;
    double pipeline = distance * pipelineCostPerKm * depthFactor;
    double umbilical = distance * 1.05 * umbilicalCostPerKm;
    double controls = wells * 3.0 + 5.0;

    return treeCost + manifold + pipeline + umbilical + controls;
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

  // ============================================================================
  // PROCESS DESIGN & EMISSIONS HELPER METHODS
  // ============================================================================

  /**
   * Calculates total power consumption from process system.
   *
   * @param process the process system
   * @return total power in MW
   */
  private double calculateTotalPowerMW(ProcessSystem process) {
    double totalPower = 0.0;
    for (ProcessEquipmentInterface equip : process.getUnitOperations()) {
      if (equip instanceof Compressor) {
        totalPower += ((Compressor) equip).getPower("MW");
      } else if (equip instanceof Pump) {
        totalPower += ((Pump) equip).getPower("MW");
      }
    }
    return totalPower;
  }

  /**
   * Calculates power breakdown by equipment type.
   *
   * @param process the process system
   * @return map of equipment type to power (MW)
   */
  private Map<String, Double> calculatePowerBreakdown(ProcessSystem process) {
    Map<String, Double> breakdown = new HashMap<>();
    double compressionPower = 0.0;
    double pumpingPower = 0.0;

    for (ProcessEquipmentInterface equip : process.getUnitOperations()) {
      if (equip instanceof Compressor) {
        compressionPower += ((Compressor) equip).getPower("MW");
      } else if (equip instanceof Pump) {
        pumpingPower += ((Pump) equip).getPower("MW");
      }
    }

    breakdown.put("Compression", compressionPower);
    breakdown.put("Pumping", pumpingPower);
    return breakdown;
  }

  /**
   * Calculates CO2 emissions from process system.
   *
   * @param result the workflow result to populate
   * @param process the process system
   */
  private void calculateProcessEmissions(WorkflowResult result, ProcessSystem process) {
    result.powerSupplyType = powerSupplyType;
    result.gridEmissionFactor = gridEmissionFactor;

    // Get emission factor based on power supply type
    double emissionFactor = getEmissionFactorKgPerMWh(powerSupplyType);

    // Calculate annual emissions from power consumption
    // Assume 8000 hours/year operation
    double operatingHours = 8000.0;
    double annualPowerMWh = result.totalPowerMW * operatingHours;
    double annualCO2eKg = annualPowerMWh * emissionFactor;

    result.annualCO2eKtonnes = annualCO2eKg / 1e6; // Convert kg to ktonnes

    // Calculate CO2 intensity (kg/boe)
    // Use gas production from profile if available
    if (result.gasProfile != null && !result.gasProfile.isEmpty()) {
      double peakRateSm3d =
          result.gasProfile.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
      double annualGasSm3 = peakRateSm3d * 365;
      double annualBoe = annualGasSm3 / 163.0; // 163 Sm3 gas = 1 boe
      if (annualBoe > 0) {
        result.co2IntensityKgPerBoe = annualCO2eKg / annualBoe;
      }
    }

    // Build emission breakdown
    result.emissionBreakdown = new HashMap<>();
    if (result.powerBreakdownMW != null) {
      for (Map.Entry<String, Double> entry : result.powerBreakdownMW.entrySet()) {
        double categoryEmission = entry.getValue() * operatingHours * emissionFactor / 1e6;
        result.emissionBreakdown.put(entry.getKey(), categoryEmission);
      }
    }
  }

  /**
   * Calculates CO2 emissions from concept-level estimation.
   *
   * @param result the workflow result to populate
   */
  private void calculateConceptEmissions(WorkflowResult result) {
    result.powerSupplyType = powerSupplyType;
    result.gridEmissionFactor = gridEmissionFactor;

    // Use EmissionsTracker for concept-level estimation
    EmissionsTracker tracker = new EmissionsTracker();
    EmissionsTracker.EmissionsReport emReport = tracker.estimate(concept, facilityConfig);

    result.annualCO2eKtonnes = emReport.getTotalEmissionsTonnesPerYear() / 1000.0;
    result.co2IntensityKgPerBoe = emReport.getIntensityKgCO2PerBoe();
    result.totalPowerMW = emReport.getTotalPowerMW();
  }

  /**
   * Gets emission factor based on power supply type.
   *
   * @param supplyType power supply type
   * @return kg CO2 per MWh
   */
  private double getEmissionFactorKgPerMWh(String supplyType) {
    if (supplyType == null) {
      return 500.0; // Default gas turbine
    }
    switch (supplyType.toUpperCase()) {
      case "POWER_FROM_SHORE":
        return gridEmissionFactor * 1000.0; // Convert from kg/kWh to kg/MWh
      case "GAS_TURBINE":
        return 500.0;
      case "COMBINED_CYCLE":
        return 350.0;
      case "DIESEL":
        return 600.0;
      default:
        return 500.0;
    }
  }
}
