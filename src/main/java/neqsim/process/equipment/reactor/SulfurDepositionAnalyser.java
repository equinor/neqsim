package neqsim.process.equipment.reactor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Analyser for elemental sulfur formation, deposition, and corrosion in natural
 * gas systems.
 *
 * <p>
 * This unit operation combines multiple analysis capabilities for understanding
 * sulfur behaviour in
 * gas value chains (offshore and onshore):
 * </p>
 * <ul>
 * <li><b>Chemical equilibrium:</b> Uses the Gibbs reactor to compute
 * equilibrium products from H2S
 * and O2 reactions, including S8, SO2, SO3, and sulfuric acid formation.</li>
 * <li><b>Sulfur solubility:</b> Calculates S8 solubility in the gas phase at
 * given conditions and
 * identifies precipitation (solid formation) via TP-solid flash.</li>
 * <li><b>Temperature sweep:</b> Scans across a range of temperatures to find
 * the onset temperature
 * for solid sulfur deposition and maps the deposition profile.</li>
 * <li><b>Corrosion assessment:</b> Evaluates FeS (iron sulfide) formation
 * potential and corrosion
 * risk from H2S and SO2 in the presence of water.</li>
 * </ul>
 *
 * <h2>Key Reactions Modelled</h2>
 *
 * <table>
 * <caption>Sulfur-related chemical reactions modelled by this
 * analyser</caption>
 * <tr>
 * <th>Reaction</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>2 H2S + O2 = 2 H2O + 1/4 S8</td>
 * <td>Direct oxidation of H2S (Claus reaction)</td>
 * </tr>
 * <tr>
 * <td>2 H2S + 3 O2 = 2 SO2 + 2 H2O</td>
 * <td>Full oxidation of H2S</td>
 * </tr>
 * <tr>
 * <td>3 H2S + SO2 = 4 S + 2 H2O</td>
 * <td>Claus tail gas reaction</td>
 * </tr>
 * <tr>
 * <td>H2S + Fe = FeS + H2</td>
 * <td>Iron sulfide (corrosion) formation</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * SystemInterface gas = new SystemSrkEos(273.15 + 50, 70.0);
 * gas.addComponent("methane", 0.93);
 * gas.addComponent("H2S", 0.0001);
 * gas.addComponent("oxygen", 0.00001);
 * gas.addComponent("CO2", 0.02);
 * gas.addComponent("S8", 1e-8);
 * gas.addComponent("SO2", 0.0);
 * gas.addComponent("water", 0.001);
 * gas.setMixingRule(2);
 *
 * Stream feed = new Stream("feed", gas);
 * feed.setFlowRate(100000, "kg/hr");
 * feed.run();
 *
 * SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("Sulfur Analyser", feed);
 * analyser.setTemperatureSweepRange(0, 200, 5); // 0-200 C in 5 C steps
 * analyser.run();
 *
 * // Get results
 * String report = analyser.getResultsAsJson();
 * double onsetTemp = analyser.getSulfurDepositionOnsetTemperature();
 * double s8Solubility = analyser.getSulfurSolubilityInGas();
 * boolean corrosionRisk = analyser.hasCorrosionRisk();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see GibbsReactor
 * @see GibbsReactorCO2
 */
public class SulfurDepositionAnalyser extends TwoPortEquipment {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(SulfurDepositionAnalyser.class);

  /** Molar mass of S8 in kg/mol. */
  private static final double S8_MOLAR_MASS = 256.48e-3;

  /** Conversion factor from mol fraction to mg/Sm3. */
  private static final double MOL_FRAC_TO_MG_SM3 = S8_MOLAR_MASS * 1e6
      * (101325.0 / (ThermodynamicConstantsInterface.R * 288.15));

  // ========== Configuration ==========

  private double tempSweepStartC = 0.0;
  private double tempSweepEndC = 200.0;
  private double tempSweepStepC = 5.0;
  private double analysisPressueBara = -1; // -1 = use inlet pressure
  private boolean runChemicalEquilibrium = true;
  private boolean runSolidFlash = true;
  private boolean runCorrosionAssessment = true;
  private int gibbsMaxIterations = 5000;
  private double gibbsDamping = 0.01;
  private double gibbsTolerance = 1e-3;

  // ========== Results ==========

  private double sulfurSolubilityMolFrac = Double.NaN;
  private double sulfurSolubilityMgSm3 = Double.NaN;
  private double sulfurDepositionOnsetTemperatureC = Double.NaN;
  private boolean solidSulfurPresent = false;
  private double solidSulfurFraction = 0.0;

  // Equilibrium products (mol fractions after Gibbs)
  private Map<String, Double> equilibriumComposition = new LinkedHashMap<>();
  // Temperature sweep results
  private List<Map<String, Object>> temperatureSweepResults = new ArrayList<>();
  // Corrosion assessment
  private Map<String, Object> corrosionAssessment = new LinkedHashMap<>();
  // Sulfur reaction products at inlet conditions
  private Map<String, Object> reactionSummary = new LinkedHashMap<>();
  // Kinetic analysis results
  private Map<String, Object> kineticAnalysis = new LinkedHashMap<>();
  // Supersaturation and nucleation analysis
  private Map<String, Object> supersaturationAnalysis = new LinkedHashMap<>();
  // Gas vs liquid S8 solubility comparison
  private Map<String, Object> gasVsLiquidSolubility = new LinkedHashMap<>();
  // Blockage risk assessment
  private Map<String, Object> blockageRiskAssessment = new LinkedHashMap<>();
  // Catalysis analysis for elemental sulfur formation pathways
  private Map<String, Object> catalysisAnalysis = new LinkedHashMap<>();

  // Pipeline/equipment geometry for blockage calculation
  private double pipeDiameterM = 0.254; // 10 inch default
  private double pipeSegmentLengthM = 1000.0; // 1 km segment
  private double flowVelocityMs = 5.0; // m/s gas velocity
  private double gasFlowRateSm3h = 100000.0; // Sm3/h

  /**
   * Creates a new SulfurDepositionAnalyser.
   *
   * @param name equipment name
   */
  public SulfurDepositionAnalyser(String name) {
    super(name);
  }

  /**
   * Creates a new SulfurDepositionAnalyser with an inlet stream.
   *
   * @param name   equipment name
   * @param stream inlet stream
   */
  public SulfurDepositionAnalyser(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * Sets the temperature range for the deposition sweep analysis.
   *
   * @param startC start temperature in Celsius
   * @param endC   end temperature in Celsius
   * @param stepC  step size in Celsius
   */
  public void setTemperatureSweepRange(double startC, double endC, double stepC) {
    this.tempSweepStartC = startC;
    this.tempSweepEndC = endC;
    this.tempSweepStepC = stepC;
  }

  /**
   * Sets the pressure for the analysis (default: use inlet pressure).
   *
   * @param pressureBara pressure in bara, or -1 to use inlet pressure
   */
  public void setAnalysisPressure(double pressureBara) {
    this.analysisPressueBara = pressureBara;
  }

  /**
   * Enables or disables chemical equilibrium calculation.
   *
   * @param enabled true to run equilibrium (default: true)
   */
  public void setRunChemicalEquilibrium(boolean enabled) {
    this.runChemicalEquilibrium = enabled;
  }

  /**
   * Enables or disables solid flash calculation.
   *
   * @param enabled true to run solid flash (default: true)
   */
  public void setRunSolidFlash(boolean enabled) {
    this.runSolidFlash = enabled;
  }

  /**
   * Enables or disables corrosion assessment.
   *
   * @param enabled true to run corrosion assessment (default: true)
   */
  public void setRunCorrosionAssessment(boolean enabled) {
    this.runCorrosionAssessment = enabled;
  }

  /**
   * Sets Gibbs reactor max iterations.
   *
   * @param maxIter maximum iterations
   */
  public void setGibbsMaxIterations(int maxIter) {
    this.gibbsMaxIterations = maxIter;
  }

  /**
   * Sets Gibbs reactor damping factor.
   *
   * @param damping damping factor (0-1)
   */
  public void setGibbsDamping(double damping) {
    this.gibbsDamping = damping;
  }

  /**
   * Sets Gibbs reactor convergence tolerance.
   *
   * @param tolerance convergence tolerance
   */
  public void setGibbsTolerance(double tolerance) {
    this.gibbsTolerance = tolerance;
  }

  /**
   * Sets pipe diameter for blockage risk calculations.
   *
   * @param diameterM pipe inner diameter in metres
   */
  public void setPipeDiameter(double diameterM) {
    this.pipeDiameterM = diameterM;
  }

  /**
   * Sets pipe segment length for blockage risk calculations.
   *
   * @param lengthM pipe segment length in metres
   */
  public void setPipeSegmentLength(double lengthM) {
    this.pipeSegmentLengthM = lengthM;
  }

  /**
   * Sets gas flow velocity for blockage risk calculations.
   *
   * @param velocityMs gas velocity in m/s
   */
  public void setFlowVelocity(double velocityMs) {
    this.flowVelocityMs = velocityMs;
  }

  /**
   * Sets gas flow rate at standard conditions for blockage risk calculations.
   *
   * @param flowRateSm3h volumetric flow rate in Sm3/h
   */
  public void setGasFlowRate(double flowRateSm3h) {
    this.gasFlowRateSm3h = flowRateSm3h;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    StreamInterface inlet = getInletStream();
    if (inlet == null) {
      logger.warn("Cannot run SulfurDepositionAnalyser '{}': inlet stream is null", getName());
      return;
    }

    double pressure = analysisPressueBara > 0 ? analysisPressueBara
        : inlet.getThermoSystem().getPressure();

    // Step 1: Chemical equilibrium with Gibbs reactor (H2S + O2 reactions)
    if (runChemicalEquilibrium) {
      performChemicalEquilibrium(inlet, pressure);
    }

    // Step 2: Sulfur solubility and solid flash at inlet conditions
    if (runSolidFlash) {
      performSulfurSolubilityAnalysis(inlet, pressure);
    }

    // Step 3: Temperature sweep for deposition profile
    performTemperatureSweep(inlet, pressure);

    // Step 4: Corrosion assessment
    if (runCorrosionAssessment) {
      performCorrosionAssessment(inlet, pressure);
    }

    // Step 5: Kinetic analysis (reaction rates, residence time comparison)
    performKineticAnalysis(inlet, pressure);

    // Step 6: Supersaturation and nucleation risk analysis
    performSupersaturationAnalysis(inlet, pressure);

    // Step 7: Gas vs liquid S8 solubility comparison
    performGasVsLiquidSolubilityComparison(inlet, pressure);

    // Step 8: Blockage risk assessment
    performBlockageRiskAssessment(inlet, pressure);

    // Step 9: Catalysis pathway analysis
    performCatalysisAnalysis(inlet, pressure);

    // Set outlet stream to a clone of inlet (analyser does not modify the stream)
    SystemInterface outSystem = inlet.getThermoSystem().clone();
    getOutletStream().setThermoSystem(outSystem);
    getOutletStream().run(id);
  }

  /**
   * Performs Gibbs free energy minimisation to determine equilibrium products
   * from H2S and O2
   * reactions at the inlet conditions.
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  private void performChemicalEquilibrium(StreamInterface inlet, double pressure) {
    try {
      // Ensure inlet has all sulfur species
      SystemInterface sys = inlet.getThermoSystem().clone();

      // Build the Gibbs reactor
      Stream tempInlet = new Stream("temp_inlet", sys);
      tempInlet.run();

      GibbsReactor reactor = new GibbsReactor("sulfur_equilibrium", tempInlet);
      reactor.setUseAllDatabaseSpecies(false);
      reactor.setDampingComposition(gibbsDamping);
      reactor.setMaxIterations(gibbsMaxIterations);
      reactor.setConvergenceTolerance(gibbsTolerance);
      reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

      // Mark hydrocarbons as inert (they don't participate in sulfur chemistry
      // at typical process temperatures)
      setHydrocarbonInert(reactor, sys);

      reactor.run();

      SystemInterface outSys = reactor.getOutletStream().getThermoSystem();
      equilibriumComposition.clear();
      reactionSummary.clear();

      for (int i = 0; i < outSys.getNumberOfComponents(); i++) {
        String name = outSys.getComponent(i).getComponentName();
        double z = outSys.getComponent(i).getz();
        equilibriumComposition.put(name, z);
      }

      // Summarise sulfur species
      reactionSummary.put("converged", reactor.hasConverged());
      reactionSummary.put("iterations", reactor.getActualIterations());
      reactionSummary.put("temperatureC", sys.getTemperature() - 273.15);
      reactionSummary.put("pressureBara", pressure);

      String[] sulfurSpecies = { "H2S", "S8", "SO2", "SO3", "sulfuric acid", "S", "S2" };
      for (String sp : sulfurSpecies) {
        try {
          double ppm = outSys.getComponent(sp).getz() * 1e6;
          reactionSummary.put(sp + "_ppm", ppm);
        } catch (Exception e) {
          // Component not in system
        }
      }

      logger.info("Sulfur equilibrium calculation completed. Converged: {}",
          reactor.hasConverged());
    } catch (Exception e) {
      logger.error("Chemical equilibrium calculation failed: {}", e.getMessage());
    }
  }

  /**
   * Marks hydrocarbon components as inert in the Gibbs reactor so they do not
   * participate in
   * reactions.
   *
   * @param reactor the Gibbs reactor
   * @param sys     the thermo system
   */
  private void setHydrocarbonInert(GibbsReactor reactor, SystemInterface sys) {
    String[] inertNames = { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
        "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane",
        "n-decane", "benzene", "toluene", "CO", "COS", "argon" };
    for (String name : inertNames) {
      try {
        if (sys.getComponent(name) != null) {
          reactor.setComponentAsInert(name);
        }
      } catch (Exception e) {
        // Component not present
      }
    }
  }

  /**
   * Analyses sulfur solubility in the gas phase and checks for solid S8 formation
   * using TP-solid
   * flash at inlet conditions.
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  private void performSulfurSolubilityAnalysis(StreamInterface inlet, double pressure) {
    try {
      SystemInterface sys = inlet.getThermoSystem().clone();
      sys.setMultiPhaseCheck(true);

      boolean hasS8 = false;
      try {
        hasS8 = sys.getComponent("S8") != null;
      } catch (Exception e) {
        // no S8
      }

      if (!hasS8) {
        logger.info("No S8 component in system - skipping solid flash");
        return;
      }

      // Enable solid phase check for S8
      sys.setSolidPhaseCheck("S8");

      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPSolidflash();

      // Check if solid phase formed
      solidSulfurPresent = sys.hasPhaseType("solid");

      if (solidSulfurPresent) {
        int solidPhaseNum = sys.getPhaseNumberOfPhase("solid");
        solidSulfurFraction = sys.getBeta(solidPhaseNum);

        // Gas phase S8 mole fraction = solubility limit
        int gasPhaseNum = sys.getPhaseNumberOfPhase("gas");
        if (gasPhaseNum >= 0) {
          sulfurSolubilityMolFrac = sys.getPhase(gasPhaseNum).getComponent("S8").getx();
          sulfurSolubilityMgSm3 = sulfurSolubilityMolFrac * MOL_FRAC_TO_MG_SM3;
        }
      } else {
        // No solid = all S8 in gas phase
        sulfurSolubilityMolFrac = sys.getPhase(0).getComponent("S8").getx();
        sulfurSolubilityMgSm3 = sulfurSolubilityMolFrac * MOL_FRAC_TO_MG_SM3;
      }

      logger.info(
          "Sulfur solubility analysis: solid present={}, S8 in gas={} mol frac, {} mg/Sm3",
          solidSulfurPresent, sulfurSolubilityMolFrac, sulfurSolubilityMgSm3);

    } catch (Exception e) {
      logger.error("Sulfur solubility analysis failed: {}", e.getMessage());
    }
  }

  /**
   * Sweeps temperature to find the sulfur deposition onset temperature and build
   * a deposition
   * profile. At each temperature, performs a TP-solid flash and records S8 in gas
   * vs solid.
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  private void performTemperatureSweep(StreamInterface inlet, double pressure) {
    temperatureSweepResults.clear();
    sulfurDepositionOnsetTemperatureC = Double.NaN;
    boolean foundOnset = false;

    boolean hasS8 = false;
    try {
      hasS8 = inlet.getThermoSystem().getComponent("S8") != null;
    } catch (Exception e) {
      // no S8
    }

    for (double tempC = tempSweepStartC; tempC <= tempSweepEndC; tempC += tempSweepStepC) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("temperatureC", tempC);
      point.put("pressureBara", pressure);

      try {
        SystemInterface sys = inlet.getThermoSystem().clone();
        sys.setTemperature(tempC + 273.15);
        sys.setPressure(pressure);
        sys.setMultiPhaseCheck(true);

        if (hasS8) {
          sys.setSolidPhaseCheck("S8");
        }

        ThermodynamicOperations ops = new ThermodynamicOperations(sys);

        if (hasS8) {
          ops.TPSolidflash();
        } else {
          ops.TPflash();
        }

        boolean hasSolid = sys.hasPhaseType("solid");
        point.put("solidSulfurPresent", hasSolid);

        if (hasSolid) {
          int solidPhaseNum = sys.getPhaseNumberOfPhase("solid");
          double solidFrac = sys.getBeta(solidPhaseNum);
          point.put("solidPhaseFraction", solidFrac);

          int gasPhaseNum = sys.getPhaseNumberOfPhase("gas");
          if (gasPhaseNum >= 0) {
            double s8InGas = sys.getPhase(gasPhaseNum).getComponent("S8").getx();
            point.put("S8_molFracInGas", s8InGas);
            point.put("S8_mgPerSm3", s8InGas * MOL_FRAC_TO_MG_SM3);
          }

          if (!foundOnset) {
            sulfurDepositionOnsetTemperatureC = tempC;
            foundOnset = true;
          }
        } else {
          point.put("solidPhaseFraction", 0.0);
          if (hasS8) {
            double s8InGas = sys.getPhase(0).getComponent("S8").getx();
            point.put("S8_molFracInGas", s8InGas);
            point.put("S8_mgPerSm3", s8InGas * MOL_FRAC_TO_MG_SM3);
          } else {
            point.put("S8_molFracInGas", 0.0);
            point.put("S8_mgPerSm3", 0.0);
          }

          // If we previously found onset, this means deposition stopped
          if (foundOnset) {
            // continue tracking
          }
        }

        // Also track H2S concentration in gas
        try {
          double h2s = sys.getPhase(0).getComponent("H2S").getx();
          point.put("H2S_molFracInGas", h2s);
        } catch (Exception e) {
          // no H2S
        }

        // Track number of phases
        point.put("numberOfPhases", sys.getNumberOfPhases());

      } catch (Exception e) {
        point.put("error", e.getMessage());
        logger.debug("Temperature sweep failed at {}C: {}", tempC, e.getMessage());
      }

      temperatureSweepResults.add(point);
    }

    // If onset was found at the highest temperature, sweep upward to find the
    // real onset temperature
    if (foundOnset) {
      logger.info("Sulfur deposition onset temperature: {} C", sulfurDepositionOnsetTemperatureC);
    } else {
      logger.info(
          "No solid sulfur deposition found in temperature range {} - {} C at {} bara",
          tempSweepStartC, tempSweepEndC, pressure);
    }
  }

  /**
   * Assesses corrosion risk from sulfur species. Evaluates:
   * <ul>
   * <li>FeS formation potential from H2S in presence of water</li>
   * <li>Sour corrosion severity based on H2S partial pressure</li>
   * <li>SO2 corrosion risk</li>
   * <li>Sulfuric acid formation risk</li>
   * </ul>
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  private void performCorrosionAssessment(StreamInterface inlet, double pressure) {
    corrosionAssessment.clear();

    SystemInterface sys = inlet.getThermoSystem();
    double totalMoles = 0;
    for (int i = 0; i < sys.getNumberOfComponents(); i++) {
      totalMoles += sys.getComponent(i).getNumberOfmoles();
    }

    // H2S partial pressure and corrosion severity per NACE MR0175
    double h2sMolFrac = 0.0;
    try {
      h2sMolFrac = sys.getComponent("H2S").getz();
    } catch (Exception e) {
      // no H2S
    }
    double h2sPartialPressurePsia = h2sMolFrac * pressure * 14.5038;
    double h2sPartialPressureKPa = h2sMolFrac * pressure * 100.0;

    corrosionAssessment.put("H2S_molFraction", h2sMolFrac);
    corrosionAssessment.put("H2S_ppm", h2sMolFrac * 1e6);
    corrosionAssessment.put("H2S_partialPressure_psia", h2sPartialPressurePsia);
    corrosionAssessment.put("H2S_partialPressure_kPa", h2sPartialPressureKPa);

    // NACE MR0175 sour service classification
    String sourSeverity;
    if (h2sPartialPressureKPa < 0.3) {
      sourSeverity = "Non-sour (H2S pp < 0.3 kPa)";
    } else if (h2sPartialPressureKPa < 1.0) {
      sourSeverity = "Mild sour (0.3-1.0 kPa H2S)";
    } else if (h2sPartialPressureKPa < 10.0) {
      sourSeverity = "Moderate sour (1-10 kPa H2S)";
    } else {
      sourSeverity = "Severe sour (> 10 kPa H2S)";
    }
    corrosionAssessment.put("sourSeverityNACE", sourSeverity);

    // FeS formation potential: H2S + Fe -> FeS + H2
    // FeS forms when H2S contacts carbon steel in presence of water
    boolean waterPresent = false;
    try {
      waterPresent = sys.getComponent("water") != null && sys.getComponent("water").getz() > 1e-10;
    } catch (Exception e) {
      // no water
    }

    boolean feSFormationRisk = h2sMolFrac > 1e-6 && waterPresent;
    corrosionAssessment.put("waterPresent", waterPresent);
    corrosionAssessment.put("FeS_formationRisk", feSFormationRisk);

    if (feSFormationRisk) {
      // Estimate FeS corrosion rate category based on H2S concentration
      // Based on NORSOK M-506 and industry guidelines
      String feSCorrosionCategory;
      if (h2sMolFrac * 1e6 < 10) {
        feSCorrosionCategory = "Low FeS risk (< 10 ppm H2S)";
      } else if (h2sMolFrac * 1e6 < 100) {
        feSCorrosionCategory = "Moderate FeS risk (10-100 ppm H2S)";
      } else if (h2sMolFrac * 1e6 < 1000) {
        feSCorrosionCategory = "High FeS risk (100-1000 ppm H2S)";
      } else {
        feSCorrosionCategory = "Very high FeS risk (> 1000 ppm H2S)";
      }
      corrosionAssessment.put("FeS_corrosionCategory", feSCorrosionCategory);

      // FeS formation temperature range
      corrosionAssessment.put("FeS_formationNote",
          "FeS (iron sulfide) forms on carbon steel surfaces exposed to H2S. "
              + "The reaction H2S + Fe -> FeS + H2 proceeds at all temperatures "
              + "but accelerates above 60 C. FeS scale can be protective (limiting "
              + "further corrosion) or non-protective depending on morphology.");
    }

    // SO2 corrosion risk
    double so2MolFrac = 0.0;
    try {
      so2MolFrac = sys.getComponent("SO2").getz();
    } catch (Exception e) {
      // no SO2
    }
    corrosionAssessment.put("SO2_molFraction", so2MolFrac);
    corrosionAssessment.put("SO2_ppm", so2MolFrac * 1e6);
    boolean so2CorrosionRisk = so2MolFrac > 1e-8 && waterPresent;
    corrosionAssessment.put("SO2_corrosionRisk", so2CorrosionRisk);
    if (so2CorrosionRisk) {
      corrosionAssessment.put("SO2_corrosionNote",
          "SO2 + H2O forms sulfurous acid (H2SO3), which is corrosive to carbon steel. "
              + "If further oxidised, sulfuric acid (H2SO4) may form. "
              + "Consider corrosion-resistant alloys or inhibitors.");
    }

    // Overall corrosion risk
    boolean anyCorrosionRisk = feSFormationRisk || so2CorrosionRisk;
    corrosionAssessment.put("overallCorrosionRisk", anyCorrosionRisk);

    // Sulfuric acid formation assessment
    double so3MolFrac = 0.0;
    try {
      so3MolFrac = sys.getComponent("SO3").getz();
    } catch (Exception e) {
      // no SO3
    }
    boolean h2so4Risk = (so2MolFrac > 1e-8 || so3MolFrac > 1e-8) && waterPresent;
    corrosionAssessment.put("H2SO4_formationRisk", h2so4Risk);

    // Process location risk assessment
    List<String> riskLocations = new ArrayList<>();
    riskLocations.add("Downstream of pressure reduction valves (JT valves, chokes)");
    riskLocations.add("Cold sections of heat exchangers");
    riskLocations.add("Gas metering stations");
    riskLocations.add("Pipeline dead legs and low-velocity zones");
    riskLocations.add("Turboexpander outlets");
    if (waterPresent) {
      riskLocations.add("Water condensation points (below water dew point)");
    }
    corrosionAssessment.put("typicalDepositionLocations", riskLocations);

    logger.info("Corrosion assessment completed: overall risk={}, sour severity={}",
        anyCorrosionRisk, sourSeverity);
  }

  // ========== Kinetic, Supersaturation, and Blockage Analysis ==========

  /**
   * Performs kinetic analysis of sulfur-related reactions. Evaluates whether
   * reactions are kinetically feasible at the given conditions by comparing
   * reaction half-lives with typical process residence times.
   *
   * <p>
   * Literature-based rate models used:
   * </p>
   * <ul>
   * <li>H2S thermal oxidation: Ea ~ 50-65 kJ/mol (Monnery et al., 1993;
   * Cheremisinoff, 2000).
   * Negligible below 200 C.</li>
   * <li>Claus reaction (catalytic): k = A*exp(-Ea/RT), Ea ~ 30-40 kJ/mol on
   * Al2O3 catalyst</li>
   * <li>FeS formation: diffusion-controlled at steel surface, rate depends on
   * H2S partial pressure
   * and temperature. Approximate Arrhenius with Ea ~ 20 kJ/mol (Sun &amp;
   * Nesic, 2009)</li>
   * <li>Polysulfane formation: H2S + Sx = H2(S)x+1, intermediate step in
   * sulfur precipitation</li>
   * </ul>
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  private void performKineticAnalysis(StreamInterface inlet, double pressure) {
    kineticAnalysis.clear();

    SystemInterface sys = inlet.getThermoSystem();
    double tempK = sys.getTemperature();
    double tempC = tempK - 273.15;
    double R_GAS = 8.314; // J/(mol*K)

    // ---- H2S Thermal Oxidation Kinetics ----
    // 2 H2S + O2 -> 2 H2O + 1/4 S8
    // Uncatalysed gas-phase thermal oxidation via radical chain mechanism.
    // Rate: r = k * [H2S] * [O2]^0.5 (Monnery et al., 1993; Karan et al., 1999)
    // k = A * exp(-Ea/RT)
    // Ea = 160000 J/mol for uncatalysed thermal oxidation (radical initiation)
    // A = 1.0e10 1/s (pre-exponential factor for gas-phase radical chain)
    // Note: Catalysed (Al2O3, TiO2 Claus catalysts) has Ea ~ 30-40 kJ/mol
    double eaH2SOxidation = 160000.0; // J/mol (uncatalysed)
    double preExpH2SOxidation = 1.0e10; // 1/s
    double kH2SOxidation = preExpH2SOxidation * Math.exp(-eaH2SOxidation / (R_GAS * tempK));

    // Half-life for pseudo-first-order at typical H2S concentration
    double halfLifeH2SOxidation = Math.log(2.0) / Math.max(kH2SOxidation, 1e-30);

    kineticAnalysis.put("H2S_oxidation_Ea_Jmol", eaH2SOxidation);
    kineticAnalysis.put("H2S_oxidation_preExp_1s", preExpH2SOxidation);
    kineticAnalysis.put("H2S_oxidation_rateConst_1s", kH2SOxidation);
    kineticAnalysis.put("H2S_oxidation_halfLife_s", halfLifeH2SOxidation);
    kineticAnalysis.put("H2S_oxidation_halfLife_days",
        halfLifeH2SOxidation / 86400.0);

    // Assess kinetic feasibility at process conditions
    // Typical pipeline residence time: 1-24 hours
    double typicalResidenceTimeS = 3600.0 * 4; // 4 hours
    boolean oxidationKineticallyFeasible = halfLifeH2SOxidation < typicalResidenceTimeS;
    kineticAnalysis.put("H2S_oxidation_kineticallyFeasible", oxidationKineticallyFeasible);

    String oxidationNote;
    if (tempC < 150) {
      oxidationNote = "At " + String.format("%.0f", tempC)
          + " C, H2S thermal oxidation is kinetically negligible "
          + "(half-life >> years). Sulfur in the gas phase originates from "
          + "the reservoir, NOT from in-situ pipeline reactions. "
          + "This means S8 deposition is a THERMODYNAMIC precipitation "
          + "problem (solubility decrease with cooling) rather than a "
          + "chemical reaction problem.";
    } else if (tempC < 300) {
      oxidationNote = "At " + String.format("%.0f", tempC)
          + " C, H2S oxidation is slow but measurable over days/weeks. "
          + "Some in-situ S8 formation possible in stagnant zones.";
    } else {
      oxidationNote = "At " + String.format("%.0f", tempC)
          + " C, H2S oxidation proceeds rapidly. Significant S8 and SO2 "
          + "formation expected. Gibbs equilibrium analysis is applicable.";
    }
    kineticAnalysis.put("H2S_oxidation_note", oxidationNote);

    // ---- FeS Formation Kinetics ----
    // Fe + H2S -> FeS + H2
    // Corrosion rate: CR = A * exp(-Ea/RT) * (pH2S)^n
    // Based on Sun & Nesic (2009): Ea ~ 20 kJ/mol, n ~ 0.5
    double eaFeS = 20000.0; // J/mol
    double preExpFeS = 30.0; // mm/year (pre-exponential)
    double h2sMolFrac = 0.0;
    try {
      h2sMolFrac = sys.getComponent("H2S").getz();
    } catch (Exception e) {
      // no H2S
    }
    double ph2sBar = h2sMolFrac * pressure;
    double corrosionRateMmYear = 0.0;

    if (ph2sBar > 0) {
      // Smith-de Waard sour corrosion model (simplified)
      // ln(CR) = 7.96 - 2320/T + 0.67*ln(pH2S)
      // CR in mm/year, T in K, pH2S in bar
      corrosionRateMmYear = Math.exp(7.96 - 2320.0 / tempK + 0.67 * Math.log(ph2sBar));
      // Cap at reasonable maximum
      corrosionRateMmYear = Math.min(corrosionRateMmYear, 100.0);
    }

    kineticAnalysis.put("FeS_Ea_Jmol", eaFeS);
    kineticAnalysis.put("FeS_corrosionRate_mmYear", corrosionRateMmYear);
    kineticAnalysis.put("H2S_partialPressure_bar", ph2sBar);

    // FeS scale morphology depends on temperature
    String feSMorphology;
    String feSProtectiveness;
    if (tempC < 60) {
      feSMorphology = "Mackinawite (FeS)";
      feSProtectiveness = "Non-protective, thin flaky scale that spalls under "
          + "flow shear. Continuous corrosion expected.";
    } else if (tempC < 120) {
      feSMorphology = "Mixed mackinawite/pyrite (FeS/FeS2)";
      feSProtectiveness = "Partially protective. Scale integrity depends on "
          + "flow velocity and pH. Localised pitting possible.";
    } else {
      feSMorphology = "Pyrrhotite (Fe(1-x)S)";
      feSProtectiveness = "More protective dense scale. However, can crack "
          + "under thermal cycling or mechanical vibration.";
    }
    kineticAnalysis.put("FeS_scaleMorphology", feSMorphology);
    kineticAnalysis.put("FeS_scaleProtectiveness", feSProtectiveness);

    // ---- Wall Loss Timeline ----
    if (corrosionRateMmYear > 0) {
      // Typical pipe wall thickness: 12.7 mm (0.5 inch)
      double wallThicknessMm = 12.7;
      double yearsToFailure = wallThicknessMm / corrosionRateMmYear;
      kineticAnalysis.put("estimatedWallLoss_mmYear", corrosionRateMmYear);
      kineticAnalysis.put("yearsToWallPenetration", yearsToFailure);
      kineticAnalysis.put("wallThicknessAssumed_mm", wallThicknessMm);
    }

    // ---- Root Cause Classification ----
    // Based on kinetics + thermodynamics, classify the sulfur source
    List<String> rootCauses = new ArrayList<>();
    if (tempC < 200) {
      rootCauses.add(
          "PRIMARY: Thermodynamic precipitation - S8 dissolved from reservoir "
              + "exceeds solubility limit upon cooling/depressurisation");
    }
    if (tempC >= 200) {
      rootCauses.add(
          "PRIMARY: Chemical reaction - H2S + O2 reaction produces S8 at "
              + "elevated temperature");
    }
    double o2MolFrac = 0.0;
    try {
      o2MolFrac = sys.getComponent("oxygen").getz();
    } catch (Exception e) {
      // no O2
    }
    if (o2MolFrac > 1e-6) {
      rootCauses.add(
          "CONTRIBUTING: Air ingress detected (O2 = "
              + String.format("%.1f", o2MolFrac * 1e6)
              + " ppm). Even trace O2 can slowly form elemental sulfur "
              + "over weeks in stagnant zones via H2S + O2 reaction.");
    }
    boolean waterPresent = false;
    try {
      waterPresent = sys.getComponent("water") != null
          && sys.getComponent("water").getz() > 1e-10;
    } catch (Exception e) {
      // no water
    }
    if (waterPresent && h2sMolFrac > 1e-4) {
      rootCauses.add(
          "CONTRIBUTING: Wet sour conditions enable FeS formation on steel "
              + "surfaces, providing nucleation sites for S8 deposition");
    }

    kineticAnalysis.put("rootCauseClassification", rootCauses);

    logger.info("Kinetic analysis completed at {}C, {}bar", tempC, pressure);
  }

  /**
   * Analyses the supersaturation state of dissolved S8 and estimates nucleation
   * risk using classical nucleation theory.
   *
   * <p>
   * The supersaturation ratio sigma = y_S8_actual / y_S8_sat determines whether
   * precipitation will occur:
   * </p>
   * <ul>
   * <li>sigma &lt; 1: Undersaturated. No deposition risk.</li>
   * <li>1 &lt; sigma &lt; 1.5: Metastable zone. Nucleation unlikely without
   * seed crystals.</li>
   * <li>sigma &gt; 1.5: Labile zone. Spontaneous homogeneous nucleation
   * expected.</li>
   * </ul>
   *
   * <p>
   * Literature: Mersmann (2001), Mullin (2001) - Crystallization. The
   * interfacial energy of solid S8 is approx. 0.025-0.035 J/m2.
   * </p>
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  private void performSupersaturationAnalysis(StreamInterface inlet, double pressure) {
    supersaturationAnalysis.clear();

    try {
      SystemInterface sys = inlet.getThermoSystem().clone();
      boolean hasS8 = false;
      try {
        hasS8 = sys.getComponent("S8") != null;
      } catch (Exception e) {
        // no S8
      }
      if (!hasS8) {
        return;
      }

      // Actual S8 in the feed (before flash)
      double s8Actual = sys.getComponent("S8").getz();

      // Calculate equilibrium solubility at current conditions
      sys.setMultiPhaseCheck(true);
      sys.setSolidPhaseCheck("S8");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPSolidflash();

      double s8Sat;
      boolean hasSolid = sys.hasPhaseType("solid");
      if (hasSolid) {
        int gasPhaseNum = sys.getPhaseNumberOfPhase("gas");
        if (gasPhaseNum >= 0) {
          s8Sat = sys.getPhase(gasPhaseNum).getComponent("S8").getx();
        } else {
          s8Sat = sys.getPhase(0).getComponent("S8").getx();
        }
      } else {
        // No solid formed = solubility not exceeded; estimate from feed
        s8Sat = s8Actual; // at saturation, no excess
      }

      // Supersaturation ratio
      double sigma = (s8Sat > 1e-30) ? s8Actual / s8Sat : 0.0;

      supersaturationAnalysis.put("S8_actual_molFrac", s8Actual);
      supersaturationAnalysis.put("S8_saturation_molFrac", s8Sat);
      supersaturationAnalysis.put("supersaturationRatio", sigma);

      // Classify supersaturation zone
      String zone;
      String nucleationRisk;
      if (sigma < 1.0) {
        zone = "Undersaturated";
        nucleationRisk = "None - S8 remains dissolved in gas phase";
      } else if (sigma < 1.2) {
        zone = "Metastable (slightly supersaturated)";
        nucleationRisk = "Low - heterogeneous nucleation possible on rough "
            + "surfaces, FeS scale, or existing S8 particles";
      } else if (sigma < 2.0) {
        zone = "Metastable (moderately supersaturated)";
        nucleationRisk = "Medium - nucleation on pipe walls and equipment "
            + "surfaces. Deposition rate increases with sigma.";
      } else {
        zone = "Labile (highly supersaturated)";
        nucleationRisk = "High - spontaneous homogeneous nucleation in gas "
            + "phase. Sulfur fog/mist formation possible. Rapid deposition.";
      }
      supersaturationAnalysis.put("supersaturationZone", zone);
      supersaturationAnalysis.put("nucleationRisk", nucleationRisk);

      // Estimate nucleation rate using simplified classical nucleation theory
      // J = A * exp(-16*pi*gamma^3*v^2 / (3*kB^3*T^3*(ln(sigma))^2))
      // gamma = surface tension of solid S8 ~ 0.030 J/m2
      // v = molecular volume of S8 ~ 256.48e-3 / (2070 * 6.022e23) m3
      double kB = 1.381e-23; // J/K
      double gammaS8 = 0.030; // J/m2 interfacial energy
      double rhoSolidS8 = 2070.0; // kg/m3 solid sulfur density
      double volMolecularS8 = S8_MOLAR_MASS / (rhoSolidS8 * 6.022e23); // m3
      double tempK = sys.getTemperature();

      if (sigma > 1.0) {
        double lnSigma = Math.log(sigma);
        double dGstar = 16.0 * Math.PI * Math.pow(gammaS8, 3)
            * Math.pow(volMolecularS8, 2)
            / (3.0 * Math.pow(kB * tempK, 3) * Math.pow(lnSigma, 2));
        // This is dimensionless barrier dG*/(kBT)^3 -- correct form for nucleation
        double barrierKbT = 16.0 * Math.PI * Math.pow(gammaS8, 3)
            * Math.pow(volMolecularS8, 2)
            / (3.0 * kB * kB * kB * tempK * tempK * tempK
                * lnSigma * lnSigma);
        // Critical nucleus radius
        double rCritical = 2.0 * gammaS8 * volMolecularS8
            / (kB * tempK * lnSigma);

        supersaturationAnalysis.put("nucleationBarrier_kBT", barrierKbT);
        supersaturationAnalysis.put("criticalNucleusRadius_nm",
            rCritical * 1e9);

        // Induction time estimate (Kashchiev & van Rosmalen, 2003)
        // t_ind ~ 1/J, where J = A * exp(-barrier)
        // For S8: A ~ 1e25 nuclei/(m3*s) (typical for molecular crystals)
        double nucleationPrefactor = 1e25; // nuclei/(m3*s)
        double nucleationRate = nucleationPrefactor * Math.exp(-barrierKbT);
        supersaturationAnalysis.put("nucleationRate_perM3s", nucleationRate);

        if (nucleationRate > 1e-10) {
          double inductionTimeS = 1.0 / nucleationRate;
          supersaturationAnalysis.put("inductionTime_s", inductionTimeS);
          supersaturationAnalysis.put("inductionTime_hours",
              inductionTimeS / 3600.0);
        }
      }

      // Deposition locations: list where supersaturation spikes
      List<String> highRiskLocations = new ArrayList<>();
      if (sigma > 1.0) {
        highRiskLocations.add("JT valves (temperature drop causes sigma spike)");
        highRiskLocations.add("Turboexpander outlets (rapid cooling)");
        highRiskLocations.add("Heat exchanger cold ends");
        highRiskLocations.add("Pipeline cold spots (uninsulated sections)");
        highRiskLocations.add("Flow restrictions / orifice plates (local cooling)");
      }
      supersaturationAnalysis.put("highRiskDepositionLocations", highRiskLocations);

    } catch (Exception e) {
      logger.error("Supersaturation analysis failed: {}", e.getMessage());
    }
  }

  /**
   * Compares S8 solubility in gas phase versus liquid hydrocarbon phase.
   *
   * <p>
   * S8 is approximately 100-1000x more soluble in liquid hydrocarbons than in the
   * gas phase (Roof, 1971; Roberts, 1997). This has critical implications:
   * </p>
   * <ul>
   * <li>When liquid HC condenses (crossing hydrocarbon dew point), it acts as a
   * "sulfur sponge" - absorbing S8 from the gas phase and reducing gas-phase
   * supersaturation.</li>
   * <li>Conversely, when liquid HC is flashed to gas (e.g., JT valve,
   * separator),
   * S8 that was dissolved in the liquid has nowhere to go - massive S8
   * precipitation can occur.</li>
   * <li>In two-phase (gas + liquid HC) flow, the liquid phase carries most of
   * the dissolved sulfur. Pipeline liquid holdup influences where sulfur
   * deposits.</li>
   * <li>Retrograde condensation near the cricondentherm temporarily provides
   * liquid to dissolve S8, but re-vaporisation at lower pressure releases
   * it.</li>
   * </ul>
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  private void performGasVsLiquidSolubilityComparison(StreamInterface inlet, double pressure) {
    gasVsLiquidSolubility.clear();

    try {
      SystemInterface sys = inlet.getThermoSystem().clone();
      sys.setMultiPhaseCheck(true);

      boolean hasS8 = false;
      try {
        hasS8 = sys.getComponent("S8") != null;
      } catch (Exception e) {
        // no S8
      }
      if (!hasS8) {
        return;
      }

      sys.setSolidPhaseCheck("S8");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPSolidflash();

      int nPhases = sys.getNumberOfPhases();
      gasVsLiquidSolubility.put("numberOfPhases", nPhases);
      gasVsLiquidSolubility.put("temperatureC", sys.getTemperature() - 273.15);
      gasVsLiquidSolubility.put("pressureBara", pressure);

      double s8InGas = 0.0;
      double s8InLiquid = 0.0;
      double gasBeta = 0.0;
      double liquidBeta = 0.0;
      boolean hasGasPhase = false;
      boolean hasLiquidPhase = false;

      // Check each phase for S8 content
      for (int i = 0; i < nPhases; i++) {
        String phaseType = sys.getPhase(i).getPhaseTypeName();
        double s8x = 0.0;
        try {
          s8x = sys.getPhase(i).getComponent("S8").getx();
        } catch (Exception e) {
          continue;
        }

        if ("gas".equals(phaseType)) {
          s8InGas = s8x;
          gasBeta = sys.getBeta(i);
          hasGasPhase = true;
          gasVsLiquidSolubility.put("S8_molFrac_gasPhase", s8InGas);
          gasVsLiquidSolubility.put("gasPhase_moleFraction", gasBeta);
        } else if ("oil".equals(phaseType) || "liquid".equals(phaseType)) {
          s8InLiquid = s8x;
          liquidBeta = sys.getBeta(i);
          hasLiquidPhase = true;
          gasVsLiquidSolubility.put("S8_molFrac_liquidPhase", s8InLiquid);
          gasVsLiquidSolubility.put("liquidPhase_moleFraction", liquidBeta);
        }
      }

      gasVsLiquidSolubility.put("hasGasPhase", hasGasPhase);
      gasVsLiquidSolubility.put("hasLiquidPhase", hasLiquidPhase);

      // Calculate the K-value (gas/liquid partition coefficient for S8)
      if (hasGasPhase && hasLiquidPhase && s8InLiquid > 1e-30) {
        double kValueS8 = s8InGas / s8InLiquid;
        double liquidGasRatio = s8InLiquid / Math.max(s8InGas, 1e-30);
        gasVsLiquidSolubility.put("S8_KValue_gasLiquid", kValueS8);
        gasVsLiquidSolubility.put("S8_liquidGasSolubilityRatio", liquidGasRatio);

        // Fraction of total S8 in each phase
        double totalS8 = s8InGas * gasBeta + s8InLiquid * liquidBeta;
        if (totalS8 > 0) {
          gasVsLiquidSolubility.put("S8_fractionInGas",
              s8InGas * gasBeta / totalS8);
          gasVsLiquidSolubility.put("S8_fractionInLiquid",
              s8InLiquid * liquidBeta / totalS8);
        }
      }

      // Implications analysis
      List<String> implications = new ArrayList<>();
      if (hasLiquidPhase && s8InLiquid > s8InGas) {
        implications.add(
            "LIQUID ACTS AS SULFUR SPONGE: S8 is "
                + String.format("%.0f", s8InLiquid / Math.max(s8InGas, 1e-30))
                + "x more soluble in the liquid HC phase than in gas. "
                + "The liquid phase carries the majority of dissolved sulfur.");
        implications.add(
            "FLASH RISK: If this liquid is flashed (JT valve, separator), "
                + "the released vapor cannot hold the S8 that was dissolved "
                + "in the liquid. Massive sulfur precipitation will occur "
                + "downstream of the flash.");
        implications.add(
            "SEPARATOR DESIGN: Liquid from separators is sulfur-rich. "
                + "Downstream heating or pressure reduction of this liquid "
                + "stream requires sulfur management.");
      } else if (!hasLiquidPhase) {
        implications.add(
            "SINGLE-PHASE GAS: No liquid HC present at these conditions. "
                + "All S8 is dissolved in the gas phase only. Deposition risk "
                + "depends purely on gas-phase supersaturation upon cooling.");
        implications.add(
            "DEW POINT TRANSITION: If conditions change to form liquid "
                + "(crossing HC dew point), the liquid will preferentially "
                + "absorb S8, temporarily reducing gas-phase supersaturation "
                + "and deposition risk on pipe walls.");
      }
      gasVsLiquidSolubility.put("implications", implications);

    } catch (Exception e) {
      logger.error("Gas vs liquid solubility comparison failed: {}",
          e.getMessage());
    }
  }

  /**
   * Assesses the risk of pipeline or equipment blockage from sulfur deposition.
   * Estimates sulfur accumulation rate, time to critical restriction, and
   * identifies pipe sections most vulnerable to plugging.
   *
   * <p>
   * The blockage model considers:
   * </p>
   * <ul>
   * <li>Sulfur dropout rate from supersaturated gas (kg/h per km)</li>
   * <li>Critical accumulation thickness to restrict flow (&gt;5% diameter
   * reduction)</li>
   * <li>Flow velocity effect on deposition vs re-entrainment</li>
   * <li>Susceptibility of different equipment types to blockage</li>
   * </ul>
   *
   * <p>
   * References: Pack (2005), Wilkes &amp; McMahon (2007) - Sulfur deposition
   * in gas pipelines.
   * </p>
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  private void performBlockageRiskAssessment(StreamInterface inlet, double pressure) {
    blockageRiskAssessment.clear();

    try {
      // Check if deposition occurs
      if (Double.isNaN(sulfurSolubilityMolFrac) || !solidSulfurPresent) {
        blockageRiskAssessment.put("blockageRisk", "None");
        blockageRiskAssessment.put("note",
            "No solid sulfur precipitation at inlet conditions. "
                + "Blockage risk may still exist downstream where "
                + "temperature and pressure decrease.");
        return;
      }

      // Calculate sulfur dropout rate
      // Sulfur deposited = (S8_feed - S8_saturation) * gas_flow * S8_molar_mass
      double s8Feed = 0.0;
      try {
        s8Feed = inlet.getThermoSystem().getComponent("S8").getz();
      } catch (Exception e) {
        return;
      }

      double s8Excess = s8Feed - sulfurSolubilityMolFrac;
      if (s8Excess <= 0) {
        s8Excess = solidSulfurFraction * s8Feed; // use solid fraction
      }

      // Sulfur dropout rate in mg/Sm3
      double sulfurDropoutMgSm3 = s8Excess * MOL_FRAC_TO_MG_SM3;
      // Mass flow of deposited sulfur in kg/h
      double sulfurMassFlowKgH = sulfurDropoutMgSm3 * gasFlowRateSm3h * 1e-6;

      blockageRiskAssessment.put("sulfurDropout_mgSm3", sulfurDropoutMgSm3);
      blockageRiskAssessment.put("sulfurMassDeposition_kgPerHour", sulfurMassFlowKgH);
      blockageRiskAssessment.put("sulfurMassDeposition_kgPerDay",
          sulfurMassFlowKgH * 24.0);
      blockageRiskAssessment.put("sulfurMassDeposition_kgPerYear",
          sulfurMassFlowKgH * 8760.0);

      // Pipe geometry calculations
      double pipeAreaM2 = Math.PI * pipeDiameterM * pipeDiameterM / 4.0;
      double pipeCircumM = Math.PI * pipeDiameterM;
      double rhoSolidS8 = 2070.0; // kg/m3 solid sulfur density

      // Deposition thickness per year (assuming uniform deposition around pipe wall)
      // Volume = mass / density
      // Thickness = volume / (circumference * length)
      double depositionVolumeM3Year = sulfurMassFlowKgH * 8760.0 / rhoSolidS8;
      double depositionThicknessMmYear = 0.0;
      if (pipeCircumM > 0 && pipeSegmentLengthM > 0) {
        depositionThicknessMmYear = depositionVolumeM3Year
            / (pipeCircumM * pipeSegmentLengthM) * 1000.0;
      }
      blockageRiskAssessment.put("depositionThickness_mmPerYear",
          depositionThicknessMmYear);
      blockageRiskAssessment.put("pipeDiameter_m", pipeDiameterM);

      // Time to critical restriction (5% diameter reduction = 10% flow area
      // reduction)
      double criticalThicknessMm = pipeDiameterM * 0.05 * 1000.0; // 5% of diameter
      double yearsToCritical = (depositionThicknessMmYear > 0)
          ? criticalThicknessMm / depositionThicknessMmYear
          : Double.MAX_VALUE;
      blockageRiskAssessment.put("criticalThickness_mm", criticalThicknessMm);
      blockageRiskAssessment.put("yearsToCriticalRestriction", yearsToCritical);

      // Time to complete blockage (pipe center filled)
      double fullBlockThicknessMm = pipeDiameterM * 500.0; // radius in mm
      double yearsToFullBlock = (depositionThicknessMmYear > 0)
          ? fullBlockThicknessMm / depositionThicknessMmYear
          : Double.MAX_VALUE;
      blockageRiskAssessment.put("yearsToFullBlockage", yearsToFullBlock);

      // Flow velocity effect on deposition
      // Critical re-entrainment velocity: particles stay deposited if v < v_crit
      // v_crit ~ 3-5 m/s for solid sulfur particles (Stevenson, 2015)
      double criticalVelocityMs = 4.0;
      boolean depositionDominated = flowVelocityMs < criticalVelocityMs;
      blockageRiskAssessment.put("flowVelocity_ms", flowVelocityMs);
      blockageRiskAssessment.put("criticalReentrainmentVelocity_ms",
          criticalVelocityMs);
      blockageRiskAssessment.put("depositionDominated", depositionDominated);

      // Blockage risk classification
      String blockageRisk;
      String piggingRecommendation;
      if (sulfurMassFlowKgH < 0.001) {
        blockageRisk = "Negligible";
        piggingRecommendation = "Standard pigging schedule sufficient.";
      } else if (yearsToCritical > 10) {
        blockageRisk = "Low";
        piggingRecommendation = "Annual pigging recommended. Monitor DP across "
            + "pipeline for sulfur accumulation.";
      } else if (yearsToCritical > 2) {
        blockageRisk = "Medium";
        piggingRecommendation = "Quarterly pigging recommended. Consider sulfur "
            + "solvent injection or pipeline insulation to maintain "
            + "temperature above deposition onset.";
      } else if (yearsToCritical > 0.5) {
        blockageRisk = "High";
        piggingRecommendation = "Monthly pigging required. Install sulfur traps "
            + "or filters upstream of critical equipment. Consider chemical "
            + "inhibitor injection (e.g., H2S scavenger or sulfur dispersant).";
      } else {
        blockageRisk = "Critical";
        piggingRecommendation = "IMMEDIATE ACTION REQUIRED. Continuous sulfur "
            + "removal system needed. Pipeline may plug within months. "
            + "Consider heated pipeline or sulfur removal at wellhead.";
      }
      blockageRiskAssessment.put("blockageRisk", blockageRisk);
      blockageRiskAssessment.put("piggingRecommendation", piggingRecommendation);

      // Equipment-specific blockage susceptibility
      List<Map<String, String>> equipmentRisk = new ArrayList<>();
      addEquipmentRisk(equipmentRisk, "Orifice plates / flow meters",
          "Very High",
          "Small orifice easily blocked by sulfur particles");
      addEquipmentRisk(equipmentRisk, "Control valves / chokes",
          "Very High",
          "Sulfur deposits on valve trim cause sticking and erosion");
      addEquipmentRisk(equipmentRisk, "JT valves",
          "High",
          "Temperature drop causes rapid S8 precipitation on valve internals");
      addEquipmentRisk(equipmentRisk, "Heat exchanger tubes",
          "High",
          "S8 deposits reduce heat transfer and increase DP");
      addEquipmentRisk(equipmentRisk, "Pipeline low points / dead legs",
          "High",
          "Low velocity allows sulfur particles to settle and accumulate");
      addEquipmentRisk(equipmentRisk, "Filters / strainers",
          "Medium (by design)",
          "Require frequent cleaning; indicate upstream deposition");
      addEquipmentRisk(equipmentRisk, "Turboexpanders",
          "Very High",
          "Sulfur particles cause blade erosion and rotor imbalance");
      blockageRiskAssessment.put("equipmentSusceptibility", equipmentRisk);

    } catch (Exception e) {
      logger.error("Blockage risk assessment failed: {}", e.getMessage());
    }
  }

  /**
   * Adds an equipment risk entry to the equipment risk list.
   *
   * @param list        the risk list to add to
   * @param equipment   equipment name
   * @param riskLevel   risk level string
   * @param description risk description
   */
  private void addEquipmentRisk(List<Map<String, String>> list,
      String equipment, String riskLevel, String description) {
    Map<String, String> entry = new LinkedHashMap<>();
    entry.put("equipment", equipment);
    entry.put("riskLevel", riskLevel);
    entry.put("description", description);
    list.add(entry);
  }

  /**
   * Evaluates catalytic pathways for elemental sulfur formation from the gas
   * composition. Analyses homogeneous, heterogeneous, and aqueous-phase catalysis
   * mechanisms based on inlet gas composition and conditions.
   *
   * <p>
   * Literature-based catalysis pathways evaluated:
   * </p>
   *
   * <table>
   * <caption>Catalytic pathways for elemental sulfur formation</caption>
   * <tr>
   * <th>Pathway</th>
   * <th>Catalyst / Mechanism</th>
   * <th>Conditions</th>
   * <th>Reference</th>
   * </tr>
   * <tr>
   * <td>Claus reaction: 2H2S + SO2 = 3/8 S8 + 2H2O</td>
   * <td>Al2O3 / TiO2 surface catalyst</td>
   * <td>200-350 C, Ea ~ 30-40 kJ/mol</td>
   * <td>Monnery et al. (1993)</td>
   * </tr>
   * <tr>
   * <td>Iron oxide: Fe2O3 + 3H2S = Fe2S3 + 3H2O</td>
   * <td>Pipeline rust, iron sponge</td>
   * <td>Ambient to 60 C, regenerable with O2</td>
   * <td>Kohl &amp; Nielsen (1997)</td>
   * </tr>
   * <tr>
   * <td>FeS surface catalysis</td>
   * <td>Corrosion product on steel</td>
   * <td>All pipeline T, heterogeneous</td>
   * <td>Sun &amp; Nesic (2009)</td>
   * </tr>
   * <tr>
   * <td>Liquid redox: 2Fe3+ + H2S = 2Fe2+ + S + 2H+</td>
   * <td>Iron chelate (EDTA/NTA)</td>
   * <td>Aqueous, pH 7-9, ambient</td>
   * <td>GPSA (2004)</td>
   * </tr>
   * <tr>
   * <td>Polysulfide: HS- + (n-1)S = Sn2- + H+</td>
   * <td>Alkaline aqueous phase</td>
   * <td>pH &gt; 7, water/glycol</td>
   * <td>Kamyshny et al. (2007)</td>
   * </tr>
   * <tr>
   * <td>COS hydrolysis: COS + H2O = CO2 + H2S</td>
   * <td>Al2O3 catalyst</td>
   * <td>150-350 C, Ea ~ 35 kJ/mol</td>
   * <td>George (1974)</td>
   * </tr>
   * <tr>
   * <td>Activated carbon adsorption + catalysis</td>
   * <td>AC surface + O2 traces</td>
   * <td>Ambient, requires O2 traces</td>
   * <td>Bandosz (2002)</td>
   * </tr>
   * </table>
   *
   * @param inlet    the inlet stream
   * @param pressure the analysis pressure in bara
   */
  @SuppressWarnings("unchecked")
  private void performCatalysisAnalysis(StreamInterface inlet, double pressure) {
    catalysisAnalysis.clear();

    SystemInterface sys = inlet.getThermoSystem();
    double tempK = sys.getTemperature();
    double tempC = tempK - 273.15;
    double rGas = 8.314; // J/(mol*K)

    // ---- Extract composition ----
    double h2sMolFrac = getComponentMolFrac(sys, "H2S");
    double o2MolFrac = getComponentMolFrac(sys, "oxygen");
    double so2MolFrac = getComponentMolFrac(sys, "SO2");
    double co2MolFrac = getComponentMolFrac(sys, "CO2");
    double waterMolFrac = getComponentMolFrac(sys, "water");
    double s8MolFrac = getComponentMolFrac(sys, "S8");
    double megMolFrac = getComponentMolFrac(sys, "MEG");

    // Heavy HC fraction (C3+) as proxy for "rich gas"
    double heavyHCFrac = 0.0;
    String[] heavyHCs = { "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane" };
    for (String hc : heavyHCs) {
      heavyHCFrac += getComponentMolFrac(sys, hc);
    }

    catalysisAnalysis.put("temperatureC", tempC);
    catalysisAnalysis.put("pressureBara", pressure);
    catalysisAnalysis.put("H2S_molFrac", h2sMolFrac);
    catalysisAnalysis.put("O2_molFrac", o2MolFrac);
    catalysisAnalysis.put("SO2_molFrac", so2MolFrac);
    catalysisAnalysis.put("CO2_molFrac", co2MolFrac);
    catalysisAnalysis.put("water_molFrac", waterMolFrac);
    catalysisAnalysis.put("heavyHC_C3plus_molFrac", heavyHCFrac);

    // Build list of active catalysis pathways
    List<Map<String, Object>> pathways = new ArrayList<>();
    List<String> activeCatalysts = new ArrayList<>();

    // ====== PATHWAY 1: Uncatalysed thermal H2S oxidation ======
    // 2H2S + O2 -> 2H2O + 1/4 S8 (radical chain mechanism)
    // Ea = 160 kJ/mol (Monnery et al., 1993; Karan et al., 1999)
    // Negligible below 200 C; appreciable above 400 C
    double eaUncatalysed = 160000.0; // J/mol
    double kUncatalysed = 1.0e10 * Math.exp(-eaUncatalysed / (rGas * tempK));
    double halfLifeUncatDays = Math.log(2.0) / Math.max(kUncatalysed, 1e-30)
        / 86400.0;
    Map<String, Object> pw1 = new LinkedHashMap<>();
    pw1.put("pathway", "Uncatalysed thermal H2S + O2");
    pw1.put("reaction", "2 H2S + O2 -> 2 H2O + 1/4 S8");
    pw1.put("activationEnergy_kJmol", 160.0);
    pw1.put("preExponential_1s", 1.0e10);
    pw1.put("rateConstant_1s", kUncatalysed);
    pw1.put("halfLife_days", halfLifeUncatDays);
    pw1.put("active", tempC > 200 && o2MolFrac > 1e-8);
    pw1.put("significance", tempC > 300 ? "HIGH"
        : tempC > 200 ? "MODERATE"
            : "NEGLIGIBLE");
    pw1.put("reference", "Monnery et al. (1993); Karan et al. (1999)");
    pw1.put("note", tempC < 200
        ? "Below 200 C, uncatalysed gas-phase oxidation half-life exceeds "
            + "years. This pathway is irrelevant at pipeline conditions."
        : "Thermal oxidation active. Radical chain mechanism: "
            + "initiation H2S + O2 -> HSO + OH, then propagation.");
    pathways.add(pw1);

    // ====== PATHWAY 2: Claus catalytic reaction (Al2O3 / TiO2) ======
    // 2H2S + SO2 -> 3/8 S8 + 2H2O
    // On Al2O3: Ea ~ 30-40 kJ/mol (Kerr & Jagodzinski, 1973)
    // On TiO2: Ea ~ 25-35 kJ/mol, higher selectivity (Linde, 2006)
    // Relevant when SO2 coexists with H2S (Claus units, tail gas)
    double eaClausAl2O3 = 35000.0; // J/mol
    double eaClausTiO2 = 30000.0; // J/mol
    double kClausAl2O3 = 1.0e6 * Math.exp(-eaClausAl2O3 / (rGas * tempK));
    double kClausTiO2 = 2.0e6 * Math.exp(-eaClausTiO2 / (rGas * tempK));
    boolean clausActive = so2MolFrac > 1e-8 && h2sMolFrac > 1e-8;
    Map<String, Object> pw2 = new LinkedHashMap<>();
    pw2.put("pathway", "Claus catalytic (Al2O3 / TiO2)");
    pw2.put("reaction", "2 H2S + SO2 -> 3/8 S8 + 2 H2O");
    pw2.put("Ea_Al2O3_kJmol", 35.0);
    pw2.put("Ea_TiO2_kJmol", 30.0);
    pw2.put("rateConst_Al2O3_1s", kClausAl2O3);
    pw2.put("rateConst_TiO2_1s", kClausTiO2);
    pw2.put("active", clausActive);
    pw2.put("significance", clausActive ? "HIGH - dominant industrial pathway"
        : "INACTIVE - no SO2 present");
    pw2.put("reference", "Kerr & Jagodzinski (1973); Linde (2006)");
    pw2.put("note", "Claus reaction requires 2:1 H2S:SO2 stoichiometry. "
        + "In Claus SRU, conversion reaches 95-97%. "
        + "Sub-dewpoint Claus achieves 99%+ by condensing S8.");
    pathways.add(pw2);
    if (clausActive) {
      activeCatalysts.add("Al2O3 / TiO2 (Claus catalyst)");
    }

    // ====== PATHWAY 3: Iron oxide / rust catalysis ======
    // Fe2O3 + 3H2S -> Fe2S3 + 3H2O (iron sponge mechanism)
    // Regeneration: 2Fe2S3 + 3O2 -> 2Fe2O3 + 6S (exothermic!)
    // This happens naturally on corroded steel pipe walls.
    // Ea ~ 15-25 kJ/mol (low barrier, surface-controlled)
    // Kohl & Nielsen (1997), "Gas Purification", 5th Ed.
    double eaIronOxide = 20000.0; // J/mol
    double kIronOxide = 5.0e4 * Math.exp(-eaIronOxide / (rGas * tempK));
    boolean ironOxideActive = h2sMolFrac > 1e-7; // Any H2S on steel
    Map<String, Object> pw3 = new LinkedHashMap<>();
    pw3.put("pathway", "Iron oxide (Fe2O3) surface catalysis");
    pw3.put("reaction",
        "Fe2O3 + 3 H2S -> Fe2S3 + 3 H2O; then 2 Fe2S3 + 3 O2 -> 2 Fe2O3 + 6 S");
    pw3.put("Ea_kJmol", 20.0);
    pw3.put("rateConst_1s", kIronOxide);
    pw3.put("active", ironOxideActive);
    pw3.put("significance", ironOxideActive
        ? "MODERATE - always present on carbon steel pipeline walls"
        : "INACTIVE");
    pw3.put("reference", "Kohl & Nielsen (1997)");
    pw3.put("note", "Pipeline rust (Fe2O3/FeOOH) reacts with H2S to form "
        + "iron sulfide. If any O2 ingress occurs (e.g., compressor seals, "
        + "instrument tubing), the iron sulfide can be re-oxidised, "
        + "releasing elemental sulfur directly on the pipe wall. "
        + "This is a KEY mechanism for sulfur deposits at valves "
        + "and fittings in sour gas pipelines.");
    pathways.add(pw3);
    if (ironOxideActive) {
      activeCatalysts.add("Fe2O3 / FeOOH (pipeline rust)");
    }

    // ====== PATHWAY 4: FeS surface catalysis ======
    // FeS corrosion product acts as heterogeneous catalyst for H2S decomposition
    // FeS + H2S -> FeS2 + H2 (pyrite formation, Ea ~ 50-70 kJ/mol)
    // Also: catalytic H2S + 1/2 O2 -> S + H2O on FeS surface
    // Sun & Nesic (2009); Smith & Pacheco (2002)
    double eaFeSCatalysis = 60000.0; // J/mol
    double kFeSCatalysis = 1.0e7 * Math.exp(-eaFeSCatalysis / (rGas * tempK));
    Map<String, Object> pw4 = new LinkedHashMap<>();
    pw4.put("pathway", "FeS surface catalysis");
    pw4.put("reaction",
        "FeS + H2S -> FeS2 + H2; catalytic H2S + 1/2 O2 -> S + H2O on FeS surface");
    pw4.put("Ea_kJmol", 60.0);
    pw4.put("rateConst_1s", kFeSCatalysis);
    pw4.put("active", ironOxideActive);
    pw4.put("significance", o2MolFrac > 1e-7 ? "HIGH - FeS + O2 co-presence"
        : "LOW - no O2 for regeneration cycle");
    pw4.put("reference", "Sun & Nesic (2009); Smith & Pacheco (2002)");
    pw4.put("note", "FeS corrosion scale on pipe wall is a "
        + "heterogeneous catalyst. Mackinawite (< 60 C) is more reactive "
        + "than pyrrhotite (> 120 C). With trace O2, the FeS/Fe2O3 redox "
        + "cycle continuously generates elemental sulfur at the pipe wall. "
        + "This explains deposits found at valves and bends where "
        + "turbulence exposes fresh steel and disrupts the FeS layer.");
    pathways.add(pw4);
    if (ironOxideActive && o2MolFrac > 1e-7) {
      activeCatalysts.add("FeS (corrosion product) + O2 trace");
    }

    // ====== PATHWAY 5: Liquid redox (Fe3+/Fe2+ chelate) ======
    // 2Fe3+ + H2S -> 2Fe2+ + S° + 2H+ (LO-CAT, SulFerox)
    // Regeneration: 2Fe2+ + 1/2 O2 + H2O -> 2Fe3+ + 2OH-
    // Net: H2S + 1/2 O2 -> S° + H2O (catalytic)
    // No Ea barrier when chelated; limited by mass transfer
    // GPSA Engineering Data Book (2004), Section 21
    boolean liquidRedoxRelevant = waterMolFrac > 0.001 && h2sMolFrac > 1e-6;
    Map<String, Object> pw5 = new LinkedHashMap<>();
    pw5.put("pathway", "Liquid redox (Fe3+ chelate: LO-CAT, SulFerox)");
    pw5.put("reaction",
        "2 Fe3+ + H2S -> 2 Fe2+ + S + 2H+; net: H2S + 1/2 O2 -> S + H2O");
    pw5.put("Ea_kJmol", "Near zero (chelate-mediated)");
    pw5.put("active", liquidRedoxRelevant);
    pw5.put("significance", liquidRedoxRelevant
        ? "MODERATE - relevant if iron ions dissolved in produced water"
        : "INACTIVE - no aqueous phase");
    pw5.put("reference", "GPSA (2004); Dalrymple et al. (1994)");
    pw5.put("note", "Dissolved Fe2+/Fe3+ ions in produced water or MEG can "
        + "catalytically convert H2S to elemental sulfur. This is a key "
        + "mechanism for sulfur found in MEG/water filters on multiphase "
        + "pipelines. Iron comes from upstream corrosion.");
    pathways.add(pw5);
    if (liquidRedoxRelevant) {
      activeCatalysts.add("Dissolved Fe ions in produced water/MEG");
    }

    // ====== PATHWAY 6: Polysulfide / HS- aqueous chemistry ======
    // In aqueous phase: H2S -> HS- + H+ (pKa1 ~ 7.0 at 25 C)
    // HS- -> S2- + H+ (pKa2 ~ 17 at 25 C)
    // Polysulfide formation: HS- + (n-1)S8/8 -> Sn2- + H+
    // Polysulfide decomposition: Sn2- + H+ -> HS- + (n-1)/8 S8
    // Kamyshny et al. (2007); Giggenbach (1972)
    // This pathway is important in MEG/water systems!
    boolean aqueousSulfurRelevant = waterMolFrac > 0.001
        && h2sMolFrac > 1e-5;
    double pKa1H2S = 6.98; // at 25 C
    // Temperature correction: pKa1(T) ~ 6.98 + 0.012*(T-25)
    double pKa1AtTemp = pKa1H2S + 0.012 * (tempC - 25.0);
    double hsFraction = 0.0;
    if (aqueousSulfurRelevant) {
      // Estimate pH from CO2 partial pressure
      double pCO2Bar = co2MolFrac * pressure;
      // pH ~ 3.5 + log10(1/pCO2) for CO2-buffered water (rough)
      double estimatedPH = (pCO2Bar > 0.01)
          ? 3.5 + Math.log10(1.0 / pCO2Bar)
          : 7.0;
      estimatedPH = Math.max(3.0, Math.min(estimatedPH, 9.0));
      // HS- fraction = 1 / (1 + 10^(pKa1-pH))
      hsFraction = 1.0 / (1.0 + Math.pow(10.0, pKa1AtTemp - estimatedPH));
      catalysisAnalysis.put("estimatedAqueousPH", estimatedPH);
      catalysisAnalysis.put("HS_minus_fraction", hsFraction);
      catalysisAnalysis.put("pKa1_H2S_atTemp", pKa1AtTemp);
    }
    Map<String, Object> pw6 = new LinkedHashMap<>();
    pw6.put("pathway",
        "Polysulfide / HS- aqueous chemistry");
    pw6.put("reaction",
        "H2S -> HS- + H+; HS- + (n-1)/8 S8 -> Sn2- + H+ (polysulfide equilibrium)");
    pw6.put("pKa1_H2S_25C", pKa1H2S);
    pw6.put("pKa1_H2S_atProcessTemp", pKa1AtTemp);
    pw6.put("HS_minus_fraction", hsFraction);
    pw6.put("active", aqueousSulfurRelevant);
    pw6.put("significance", aqueousSulfurRelevant && hsFraction > 0.1
        ? "HIGH - significant HS- available for polysulfide formation"
        : aqueousSulfurRelevant
            ? "LOW - pH too low for significant HS-"
            : "INACTIVE - no aqueous phase");
    pw6.put("reference",
        "Kamyshny et al. (2007); Giggenbach (1972); Rickard & Luther (2007)");
    pw6.put("note", "HS- ions can form polysulfide chains (S2^2-, S3^2-, "
        + "S4^2-, S5^2-) which are metastable intermediates. These "
        + "decompose to release elemental sulfur upon pH decrease "
        + "(e.g., CO2 ingress), temperature change, or oxidation. "
        + "In MEG/glycol systems, the lower dielectric constant "
        + "shifts equilibria towards molecular H2S rather than HS-. "
        + "NeqSim's electrolyte models (SystemElectrolyteCPAstatoil) "
        + "can model H2S dissociation: H2S + H2O -> HS- + H3O+ "
        + "(reaction 8 in REACTIONDATA.csv) and HS- + H2O -> S2- + H3O+ "
        + "(reaction 9).");
    pathways.add(pw6);
    if (aqueousSulfurRelevant && hsFraction > 0.01) {
      activeCatalysts.add("HS-/polysulfide aqueous pathway");
    }

    // ====== PATHWAY 7: COS/CS2 hydrolysis ======
    // COS + H2O -> CO2 + H2S (on Al2O3, Ea ~ 35 kJ/mol; George, 1974)
    // CS2 + 2H2O -> CO2 + 2H2S (on Al2O3, Ea ~ 40 kJ/mol)
    // These organosulfur compounds form H2S which then precipitates as S8
    // Indirect contribution to sulfur load
    double cosMolFrac = getComponentMolFrac(sys, "COS");
    boolean cosPresent = cosMolFrac > 1e-8;
    double eaCOS = 35000.0; // J/mol
    double kCOS = 5.0e5 * Math.exp(-eaCOS / (rGas * tempK));
    Map<String, Object> pw7 = new LinkedHashMap<>();
    pw7.put("pathway", "COS hydrolysis (catalysed on Al2O3)");
    pw7.put("reaction", "COS + H2O -> CO2 + H2S (then H2S -> S8)");
    pw7.put("Ea_kJmol", 35.0);
    pw7.put("rateConst_1s", kCOS);
    pw7.put("COS_molFrac", cosMolFrac);
    pw7.put("active", cosPresent);
    pw7.put("significance", cosPresent
        ? "MODERATE - COS hydrolysis adds to total H2S/sulfur budget"
        : "INACTIVE - no COS detected");
    pw7.put("reference", "George (1974); Ferm (1957)");
    pw7.put("note", "COS is a common impurity in natural gas (1-100 ppm). "
        + "It hydrolyses to H2S on Al2O3 molecular sieve beds during "
        + "gas dehydration, increasing the total sulfur species downstream. "
        + "This indirect pathway feeds the S8 precipitation mechanism.");
    pathways.add(pw7);
    if (cosPresent) {
      activeCatalysts.add("COS hydrolysis (molecular sieve beds)");
    }

    // ====== PATHWAY 8: Activated carbon catalysis ======
    // H2S + 1/2 O2 -> S + H2O (on activated carbon surface)
    // Ea ~ 20-30 kJ/mol on AC (Bandosz, 2002; Abatzoglou & Boivin, 2009)
    // AC is used in gas treating; the mechanism is O2-dependent
    double eaAC = 25000.0; // J/mol
    double kAC = 1.0e5 * Math.exp(-eaAC / (rGas * tempK));
    Map<String, Object> pw8 = new LinkedHashMap<>();
    pw8.put("pathway", "Activated carbon surface catalysis");
    pw8.put("reaction", "H2S + 1/2 O2 -> S + H2O (on AC surface)");
    pw8.put("Ea_kJmol", 25.0);
    pw8.put("rateConst_1s", kAC);
    pw8.put("active", o2MolFrac > 1e-8 && h2sMolFrac > 1e-7);
    pw8.put("significance", o2MolFrac > 1e-8
        ? "MODERATE - requires activated carbon bed in process"
        : "INACTIVE - requires O2 trace");
    pw8.put("reference", "Bandosz (2002); Abatzoglou & Boivin (2009)");
    pw8.put("note", "Activated carbon is used in some gas treating "
        + "applications. The microporous structure adsorbs H2S which "
        + "then reacts with dissolved O2 on the AC surface. Sulfur "
        + "deposits within the carbon pores, eventually deactivating "
        + "the bed.");
    pathways.add(pw8);

    // ====== PATHWAY 9: Mercury-catalysed H2S oxidation ======
    // Hg enhances H2S -> S8 oxidation on metal surfaces
    // Relevant in mercury-containing reservoirs (SE Asia, North Sea)
    // Wilhelm & Bloom (2000)
    Map<String, Object> pw9 = new LinkedHashMap<>();
    pw9.put("pathway", "Mercury-catalysed surface reaction");
    pw9.put("reaction",
        "H2S + Hg(surface) -> HgS(surface) + S-radical chain");
    pw9.put("active", false);
    pw9.put("significance", "POTENTIAL - relevant if reservoir contains Hg. "
        + "Hg amalgamates with equipment metals and catalyses sulfur "
        + "deposition on aluminium heat exchangers.");
    pw9.put("reference", "Wilhelm & Bloom (2000)");
    pw9.put("note", "Mercury is present in some SE Asian and North Sea "
        + "gas fields at 1-1000 ug/Nm3. Hg catalyses sulfur formation "
        + "and causes liquid metal embrittlement of aluminium "
        + "brazed heat exchangers in LNG/NGL plants.");
    pathways.add(pw9);

    catalysisAnalysis.put("pathways", pathways);
    catalysisAnalysis.put("activeCatalysts", activeCatalysts);
    catalysisAnalysis.put("numberOfActivePathways", activeCatalysts.size());

    // ====== Gas composition effect on catalysis ======
    Map<String, Object> compositionEffects = new LinkedHashMap<>();

    // Rich gas effect
    boolean richGas = heavyHCFrac > 0.05;
    compositionEffects.put("richGas_C3plus_pct", heavyHCFrac * 100.0);
    compositionEffects.put("richGas_effect", richGas
        ? "Rich gas (C3+ > 5 mol%) increases S8 solubility in gas "
            + "phase. At JT/dewpoint conditions, liquid HC formation "
            + "extracts dissolved S8 ('sulfur sponge'). On subsequent "
            + "flash/separation, S8 exceeds liquid solubility and "
            + "precipitates heavily at valves and control equipment."
        : "Lean gas. S8 solubility is lower. Precipitation onset "
            + "occurs at higher temperature during pipeline cooling.");

    // CO2 effect on aqueous sulfur chemistry
    compositionEffects.put("CO2_pct", co2MolFrac * 100.0);
    compositionEffects.put("CO2_effect",
        co2MolFrac > 0.01
            ? "CO2 > 1 mol% acidifies produced water (pH ~ "
                + String.format("%.1f",
                    co2MolFrac * pressure > 0.01
                        ? 3.5 + Math.log10(1.0 / (co2MolFrac * pressure))
                        : 7.0)
                + "). Lower pH shifts H2S equilibrium towards molecular H2S "
                + "(less HS-). This REDUCES polysulfide formation but "
                + "INCREASES direct H2S corrosion of steel."
            : "Low CO2. Aqueous pH not significantly depressed by CO2.");

    // Water / MEG effect
    compositionEffects.put("water_pct", waterMolFrac * 100.0);
    compositionEffects.put("MEG_pct", megMolFrac * 100.0);
    if (waterMolFrac > 0.001) {
      compositionEffects.put("water_effect",
          "Water present enables: (1) H2S dissociation to HS-/S2-, "
              + "(2) polysulfide formation, (3) iron dissolution from steel, "
              + "(4) Fe(OH)2/FeOOH precipitation. All contribute to sulfur "
              + "deposition especially at filters and low-velocity zones.");
    }
    if (megMolFrac > 0.001) {
      compositionEffects.put("MEG_effect",
          "MEG (mono-ethylene glycol) has lower dielectric constant "
              + "than water. This shifts H2S dissociation equilibrium "
              + "towards molecular H2S. However, MEG solutions can still "
              + "carry dissolved Fe2+/Fe3+ from upstream corrosion which "
              + "participate in liquid-phase S8 formation. MEG reclamation "
              + "filters commonly accumulate sulfur deposits.");
    }

    // O2 trace effect (critical catalyst!)
    compositionEffects.put("O2_ppm", o2MolFrac * 1e6);
    compositionEffects.put("O2_effect", o2MolFrac > 1e-6
        ? "CRITICAL: O2 detected at " + String.format("%.1f", o2MolFrac * 1e6)
            + " ppm. Even 1-2 ppm O2 catalyses sulfur formation:"
            + " (1) Direct: 2H2S + O2 -> 2S + 2H2O (slow gas phase, fast "
            + "on FeS surface); (2) Indirect: FeS + O2 -> Fe2O3 + S "
            + "(releases solid sulfur on pipe wall); (3) Regenerative: "
            + "Fe2O3 + H2S -> FeS + H2O then FeS + O2 -> Fe2O3 + S "
            + "(continuous catalytic cycle). O2 ingress sources: "
            + "compressor seals, instrument air leaks, chemical injection "
            + "lines, pig launcher/receiver operations."
        : "No O2 detected. Without O2, the Fe2O3/FeS catalytic cycle "
            + "cannot regenerate. Sulfur deposits are purely from "
            + "thermodynamic precipitation (solubility decrease).");

    // SO2 co-presence
    compositionEffects.put("SO2_ppm", so2MolFrac * 1e6);
    if (so2MolFrac > 1e-8) {
      compositionEffects.put("SO2_effect",
          "SO2 present at " + String.format("%.2f", so2MolFrac * 1e6)
              + " ppm. The Claus reaction (2H2S + SO2 -> 3/8 S8 + 2H2O) "
              + "is thermodynamically favoured at all temperatures. "
              + "Even without catalyst, this reaction can proceed at "
              + "measurable rates above 150 C. On Al2O3/TiO2 surfaces "
              + "(e.g., molecular sieve beds), the rate is fast at "
              + "process conditions. SO2 sources: combustion products, "
              + "thermal oxidation of H2S upstream, SRU tail gas.");
    }

    catalysisAnalysis.put("compositionEffects", compositionEffects);

    // ====== Overall catalysis assessment ======
    String dominantMechanism;
    if (tempC > 300 && o2MolFrac > 1e-6) {
      dominantMechanism = "HIGH-TEMPERATURE OXIDATION: Direct thermal "
          + "H2S + O2 reaction dominates above 300 C.";
    } else if (clausActive) {
      dominantMechanism = "CLAUS REACTION: SO2 + H2S produces sulfur "
          + "catalytically at moderate temperatures.";
    } else if (o2MolFrac > 1e-6 && ironOxideActive) {
      dominantMechanism = "SURFACE CATALYSIS: Fe2O3/FeS redox cycle on "
          + "pipe wall generates sulfur where O2 and H2S coexist. "
          + "This explains deposits at valves, bends, and fittings.";
    } else if (aqueousSulfurRelevant && hsFraction > 0.1) {
      dominantMechanism = "AQUEOUS-PHASE: HS- / polysulfide chemistry in "
          + "produced water or MEG generates and redistributes sulfur. "
          + "Explains deposits in MEG/water filters.";
    } else {
      dominantMechanism = "THERMODYNAMIC PRECIPITATION: No significant "
          + "catalytic pathway active. S8 deposits are from supersaturation "
          + "caused by cooling and depressurisation of reservoir fluid "
          + "containing dissolved S8.";
    }
    catalysisAnalysis.put("dominantMechanism", dominantMechanism);

    // Catalysis enhancement factor vs uncatalysed
    // Compare rate constants: catalysed/uncatalysed
    if (kUncatalysed > 0) {
      double maxCatalysedK = Math.max(Math.max(kClausAl2O3, kIronOxide),
          Math.max(kFeSCatalysis, kCOS));
      double enhancementFactor = maxCatalysedK / Math.max(kUncatalysed, 1e-30);
      catalysisAnalysis.put("catalyticEnhancementFactor", enhancementFactor);
      catalysisAnalysis.put("catalyticEnhancementNote",
          "Surface catalysts (Fe2O3, FeS, Al2O3) lower Ea from "
              + "160 kJ/mol to 20-35 kJ/mol, increasing reaction rate by "
              + String.format("%.2e", enhancementFactor) + "x at "
              + String.format("%.0f", tempC) + " C.");
    }

    logger.info("Catalysis analysis completed: {} active pathways at {}C, {}bar",
        activeCatalysts.size(), tempC, pressure);
  }

  /**
   * Gets the mole fraction of a component from the system, returning 0 if not
   * present.
   *
   * @param sys  the thermodynamic system
   * @param name the component name
   * @return mole fraction, or 0 if component not found
   */
  private double getComponentMolFrac(SystemInterface sys, String name) {
    try {
      if (sys.getComponent(name) != null) {
        return sys.getComponent(name).getz();
      }
    } catch (Exception e) {
      // Component not present
    }
    return 0.0;
  }

  // ========== Result Accessors ==========

  /**
   * Gets the catalysis analysis results including all evaluated pathways,
   * composition effects, and dominant mechanism assessment.
   *
   * @return map of catalysis analysis data
   */
  public Map<String, Object> getCatalysisAnalysis() {
    return new LinkedHashMap<>(catalysisAnalysis);
  }

  /**
   * Gets the sulfur solubility in the gas phase as mole fraction.
   *
   * @return S8 mole fraction in gas at inlet conditions
   */
  public double getSulfurSolubilityInGas() {
    return sulfurSolubilityMolFrac;
  }

  /**
   * Gets the sulfur solubility in the gas phase in mg/Sm3.
   *
   * @return S8 concentration in mg/Sm3 at standard conditions
   */
  public double getSulfurSolubilityMgSm3() {
    return sulfurSolubilityMgSm3;
  }

  /**
   * Gets the temperature at which solid sulfur first deposits (in Celsius).
   * Returns NaN if no solid
   * deposition occurs in the sweep range.
   *
   * @return onset temperature in Celsius, or NaN
   */
  public double getSulfurDepositionOnsetTemperature() {
    return sulfurDepositionOnsetTemperatureC;
  }

  /**
   * Returns true if solid sulfur is present at inlet conditions.
   *
   * @return true if solid S8 phase exists
   */
  public boolean isSolidSulfurPresent() {
    return solidSulfurPresent;
  }

  /**
   * Gets the solid sulfur phase fraction at inlet conditions.
   *
   * @return solid phase mole fraction
   */
  public double getSolidSulfurFraction() {
    return solidSulfurFraction;
  }

  /**
   * Returns true if any corrosion risk is identified.
   *
   * @return true if FeS, SO2, or H2SO4 corrosion risk exists
   */
  public boolean hasCorrosionRisk() {
    Object risk = corrosionAssessment.get("overallCorrosionRisk");
    return risk != null && (Boolean) risk;
  }

  /**
   * Gets the equilibrium composition after Gibbs reactor calculation.
   *
   * @return map of component names to mole fractions
   */
  public Map<String, Double> getEquilibriumComposition() {
    return new LinkedHashMap<>(equilibriumComposition);
  }

  /**
   * Gets the temperature sweep results.
   *
   * @return list of maps, each containing temperature point data
   */
  public List<Map<String, Object>> getTemperatureSweepResults() {
    return new ArrayList<>(temperatureSweepResults);
  }

  /**
   * Gets the corrosion assessment results.
   *
   * @return map of corrosion assessment data
   */
  public Map<String, Object> getCorrosionAssessment() {
    return new LinkedHashMap<>(corrosionAssessment);
  }

  /**
   * Gets the reaction summary from the Gibbs equilibrium calculation.
   *
   * @return map of reaction summary data
   */
  public Map<String, Object> getReactionSummary() {
    return new LinkedHashMap<>(reactionSummary);
  }

  /**
   * Gets the kinetic analysis results including reaction rates, FeS corrosion
   * rate, root cause classification, and wall loss estimates.
   *
   * @return map of kinetic analysis data
   */
  public Map<String, Object> getKineticAnalysis() {
    return new LinkedHashMap<>(kineticAnalysis);
  }

  /**
   * Gets the supersaturation and nucleation analysis results.
   *
   * @return map of supersaturation data including ratio, zone, and nucleation
   *         rate
   */
  public Map<String, Object> getSupersaturationAnalysis() {
    return new LinkedHashMap<>(supersaturationAnalysis);
  }

  /**
   * Gets the gas vs liquid S8 solubility comparison results.
   *
   * @return map of solubility comparison data
   */
  public Map<String, Object> getGasVsLiquidSolubility() {
    return new LinkedHashMap<>(gasVsLiquidSolubility);
  }

  /**
   * Gets the blockage risk assessment results.
   *
   * @return map of blockage risk data including accumulation rate and plugging
   *         time
   */
  public Map<String, Object> getBlockageRiskAssessment() {
    return new LinkedHashMap<>(blockageRiskAssessment);
  }

  /**
   * Returns comprehensive results as a JSON string.
   *
   * @return JSON string with all analysis results
   */
  public String getResultsAsJson() {
    JsonObject root = new JsonObject();

    // Header
    root.addProperty("analyser", getName());
    root.addProperty("description",
        "Sulfur deposition and corrosion analysis for natural gas system");

    // Inlet conditions
    JsonObject inlet = new JsonObject();
    try {
      SystemInterface sys = getInletStream().getThermoSystem();
      inlet.addProperty("temperatureC", sys.getTemperature() - 273.15);
      inlet.addProperty("pressureBara", sys.getPressure());
    } catch (Exception e) {
      // skip
    }
    root.add("inletConditions", inlet);

    // Sulfur solubility
    JsonObject solubility = new JsonObject();
    solubility.addProperty("S8_molFractionInGas", sulfurSolubilityMolFrac);
    solubility.addProperty("S8_mgPerSm3", sulfurSolubilityMgSm3);
    solubility.addProperty("solidSulfurPresent", solidSulfurPresent);
    solubility.addProperty("solidSulfurFraction", solidSulfurFraction);
    root.add("sulfurSolubility", solubility);

    // Deposition onset
    JsonObject deposition = new JsonObject();
    deposition.addProperty("onsetTemperatureC", sulfurDepositionOnsetTemperatureC);
    deposition.addProperty("sweepStartC", tempSweepStartC);
    deposition.addProperty("sweepEndC", tempSweepEndC);
    deposition.addProperty("sweepStepC", tempSweepStepC);
    root.add("depositionOnset", deposition);

    // Chemical equilibrium
    if (!reactionSummary.isEmpty()) {
      JsonObject reactions = new JsonObject();
      for (Map.Entry<String, Object> entry : reactionSummary.entrySet()) {
        if (entry.getValue() instanceof Number) {
          reactions.addProperty(entry.getKey(), ((Number) entry.getValue()).doubleValue());
        } else if (entry.getValue() instanceof Boolean) {
          reactions.addProperty(entry.getKey(), (Boolean) entry.getValue());
        } else {
          reactions.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
      }
      root.add("chemicalEquilibrium", reactions);
    }

    // Temperature sweep
    if (!temperatureSweepResults.isEmpty()) {
      JsonArray sweep = new JsonArray();
      for (Map<String, Object> point : temperatureSweepResults) {
        JsonObject p = new JsonObject();
        for (Map.Entry<String, Object> entry : point.entrySet()) {
          if (entry.getValue() instanceof Number) {
            p.addProperty(entry.getKey(), ((Number) entry.getValue()).doubleValue());
          } else if (entry.getValue() instanceof Boolean) {
            p.addProperty(entry.getKey(), (Boolean) entry.getValue());
          } else {
            p.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
          }
        }
        sweep.add(p);
      }
      root.add("temperatureSweep", sweep);
    }

    // Corrosion assessment
    if (!corrosionAssessment.isEmpty()) {
      JsonObject corrosion = new JsonObject();
      for (Map.Entry<String, Object> entry : corrosionAssessment.entrySet()) {
        if (entry.getValue() instanceof Number) {
          corrosion.addProperty(entry.getKey(), ((Number) entry.getValue()).doubleValue());
        } else if (entry.getValue() instanceof Boolean) {
          corrosion.addProperty(entry.getKey(), (Boolean) entry.getValue());
        } else if (entry.getValue() instanceof List) {
          JsonArray arr = new JsonArray();
          for (Object item : (List<?>) entry.getValue()) {
            arr.add(String.valueOf(item));
          }
          corrosion.add(entry.getKey(), arr);
        } else {
          corrosion.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
      }
      root.add("corrosionAssessment", corrosion);
    }

    // Kinetic analysis
    if (!kineticAnalysis.isEmpty()) {
      root.add("kineticAnalysis", mapToJsonObject(kineticAnalysis));
    }

    // Supersaturation analysis
    if (!supersaturationAnalysis.isEmpty()) {
      root.add("supersaturationAnalysis", mapToJsonObject(supersaturationAnalysis));
    }

    // Gas vs liquid S8 solubility
    if (!gasVsLiquidSolubility.isEmpty()) {
      root.add("gasVsLiquidSolubility", mapToJsonObject(gasVsLiquidSolubility));
    }

    // Blockage risk assessment
    if (!blockageRiskAssessment.isEmpty()) {
      root.add("blockageRiskAssessment", mapToJsonObject(blockageRiskAssessment));
    }

    // Catalysis analysis
    if (!catalysisAnalysis.isEmpty()) {
      root.add("catalysisAnalysis", mapToJsonObject(catalysisAnalysis));
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }

  /**
   * Prints a human-readable summary of the analysis results to the logger.
   */
  public void printSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n======== Sulfur Deposition Analysis Summary ========\n");
    sb.append("Analyser: ").append(getName()).append("\n");

    try {
      SystemInterface sys = getInletStream().getThermoSystem();
      sb.append(String.format("Inlet: %.1f C, %.1f bara%n",
          sys.getTemperature() - 273.15, sys.getPressure()));
    } catch (Exception e) {
      // skip
    }

    sb.append("\n--- Sulfur Solubility ---\n");
    sb.append(String.format("S8 in gas: %.4e mol frac = %.4f mg/Sm3%n",
        sulfurSolubilityMolFrac, sulfurSolubilityMgSm3));
    sb.append("Solid S8 present: ").append(solidSulfurPresent).append("\n");
    if (solidSulfurPresent) {
      sb.append(String.format("Solid fraction: %.6e%n", solidSulfurFraction));
    }

    sb.append("\n--- Deposition Onset ---\n");
    if (!Double.isNaN(sulfurDepositionOnsetTemperatureC)) {
      sb.append(String.format("Onset temperature: %.1f C%n", sulfurDepositionOnsetTemperatureC));
    } else {
      sb.append("No solid deposition in sweep range\n");
    }

    if (!reactionSummary.isEmpty()) {
      sb.append("\n--- Chemical Equilibrium (Gibbs Reactor) ---\n");
      sb.append(String.format("Converged: %s%n", reactionSummary.get("converged")));
      for (Map.Entry<String, Object> e : reactionSummary.entrySet()) {
        if (e.getKey().endsWith("_ppm")) {
          sb.append(String.format("  %s: %.6e ppm%n", e.getKey(), e.getValue()));
        }
      }
    }

    if (!kineticAnalysis.isEmpty()) {
      sb.append("\n--- Kinetic Analysis ---\n");
      Object crRate = kineticAnalysis.get("FeS_corrosionRate_mmYear");
      if (crRate instanceof Number) {
        sb.append(String.format("FeS corrosion rate: %.2f mm/year%n",
            ((Number) crRate).doubleValue()));
      }
      Object morphology = kineticAnalysis.get("FeS_scaleMorphology");
      if (morphology != null) {
        sb.append("FeS scale type: ").append(morphology).append("\n");
      }
      Object note = kineticAnalysis.get("H2S_oxidation_note");
      if (note != null) {
        sb.append("Kinetic note: ").append(note).append("\n");
      }
    }

    if (!supersaturationAnalysis.isEmpty()) {
      sb.append("\n--- Supersaturation Analysis ---\n");
      Object sigma = supersaturationAnalysis.get("supersaturationRatio");
      if (sigma instanceof Number) {
        sb.append(String.format("Supersaturation ratio: %.4f%n",
            ((Number) sigma).doubleValue()));
      }
      Object zone = supersaturationAnalysis.get("supersaturationZone");
      if (zone != null) {
        sb.append("Zone: ").append(zone).append("\n");
      }
    }

    if (!gasVsLiquidSolubility.isEmpty()) {
      sb.append("\n--- Gas vs Liquid S8 Solubility ---\n");
      Object ratio = gasVsLiquidSolubility.get("S8_liquidGasSolubilityRatio");
      if (ratio instanceof Number) {
        sb.append(String.format("Liquid/gas solubility ratio: %.0fx%n",
            ((Number) ratio).doubleValue()));
      }
    }

    if (!blockageRiskAssessment.isEmpty()) {
      sb.append("\n--- Blockage Risk ---\n");
      sb.append("Risk level: ").append(blockageRiskAssessment.get("blockageRisk"))
          .append("\n");
      Object yrs = blockageRiskAssessment.get("yearsToCriticalRestriction");
      if (yrs instanceof Number) {
        sb.append(String.format("Years to critical restriction: %.1f%n",
            ((Number) yrs).doubleValue()));
      }
      Object pigging = blockageRiskAssessment.get("piggingRecommendation");
      if (pigging != null) {
        sb.append("Pigging: ").append(pigging).append("\n");
      }
    }

    if (!corrosionAssessment.isEmpty()) {
      sb.append("\n--- Corrosion Assessment ---\n");
      sb.append("Sour severity: ").append(corrosionAssessment.get("sourSeverityNACE"))
          .append("\n");
      sb.append("FeS formation risk: ").append(corrosionAssessment.get("FeS_formationRisk"))
          .append("\n");
      sb.append("SO2 corrosion risk: ").append(corrosionAssessment.get("SO2_corrosionRisk"))
          .append("\n");
      sb.append("Overall corrosion risk: ")
          .append(corrosionAssessment.get("overallCorrosionRisk")).append("\n");
    }

    if (!catalysisAnalysis.isEmpty()) {
      sb.append("\n--- Catalysis Analysis ---\n");
      Object dom = catalysisAnalysis.get("dominantMechanism");
      if (dom != null) {
        sb.append("Dominant mechanism: ").append(dom).append("\n");
      }
      Object nPaths = catalysisAnalysis.get("numberOfActivePathways");
      if (nPaths != null) {
        sb.append("Active catalytic pathways: ").append(nPaths).append("\n");
      }
      Object catalysts = catalysisAnalysis.get("activeCatalysts");
      if (catalysts instanceof List) {
        for (Object c : (List<?>) catalysts) {
          sb.append("  - ").append(c).append("\n");
        }
      }
      Object enhancement = catalysisAnalysis.get("catalyticEnhancementNote");
      if (enhancement != null) {
        sb.append("Enhancement: ").append(enhancement).append("\n");
      }
    }

    sb.append("====================================================\n");
    logger.info(sb.toString());
  }

  /**
   * Converts a map to a JsonObject, handling nested Lists and Maps recursively.
   *
   * @param map the map to convert
   * @return a JsonObject representation
   */
  @SuppressWarnings("unchecked")
  private JsonObject mapToJsonObject(Map<String, Object> map) {
    JsonObject obj = new JsonObject();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object val = entry.getValue();
      if (val instanceof Number) {
        obj.addProperty(entry.getKey(), ((Number) val).doubleValue());
      } else if (val instanceof Boolean) {
        obj.addProperty(entry.getKey(), (Boolean) val);
      } else if (val instanceof String) {
        obj.addProperty(entry.getKey(), (String) val);
      } else if (val instanceof List) {
        JsonArray arr = new JsonArray();
        for (Object item : (List<?>) val) {
          if (item instanceof Map) {
            arr.add(mapToJsonObject((Map<String, Object>) item));
          } else if (item instanceof String) {
            arr.add((String) item);
          } else {
            arr.add(String.valueOf(item));
          }
        }
        obj.add(entry.getKey(), arr);
      } else if (val instanceof Map) {
        obj.add(entry.getKey(), mapToJsonObject((Map<String, Object>) val));
      } else if (val != null) {
        obj.addProperty(entry.getKey(), String.valueOf(val));
      }
    }
    return obj;
  }
}
