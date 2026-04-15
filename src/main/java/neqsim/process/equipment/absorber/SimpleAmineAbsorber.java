package neqsim.process.equipment.absorber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Simple amine absorber model for acid gas removal (CO2 and H2S).
 *
 * <p>
 * Models the gas sweetening process using amine solvents (MDEA, DEA, MEA, or blends). The model
 * calculates acid gas removal based on:
 * </p>
 * <ul>
 * <li>Acid gas loading (mole acid gas per mole amine)</li>
 * <li>Approach to equilibrium (typically 70% of equilibrium loading)</li>
 * <li>Solvent circulation rate</li>
 * <li>Number of theoretical stages</li>
 * </ul>
 *
 * <h2>Design Parameters</h2>
 * <ul>
 * <li><b>Foaming margin:</b> Applied as capacity derating on gas flow (default 20%)</li>
 * <li><b>Packing height:</b> Maximum 5-6 m per section for fixed installations</li>
 * <li><b>Amine temperature margin:</b> Lean amine temperature at least 6 degC above gas feed</li>
 * <li><b>Gas carry-under:</b> Default 0.03 Am3 gas per Am3 amine</li>
 * <li><b>Demister K-factor:</b> Maximum 0.08 m/s for wire mesh at outlet</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SimpleAmineAbsorber extends SimpleAbsorber {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SimpleAmineAbsorber.class);

  // ============================================================================
  // STREAM REFERENCES
  // ============================================================================

  /** Sour gas inlet stream. */
  private StreamInterface sourGasInStream;

  /** Lean amine inlet stream. */
  private StreamInterface leanAmineInStream;

  /** Sweet gas outlet stream. */
  private StreamInterface sweetGasOutStream;

  /** Rich amine outlet stream. */
  private StreamInterface richAmineOutStream;

  // ============================================================================
  // DESIGN PARAMETERS
  // ============================================================================

  /** Amine type: "MDEA", "DEA", "MEA", or blend. */
  private String amineType = "MDEA";

  /** Amine weight percent concentration in the lean solvent (typically 30-50 wt%). */
  private double amineConcentrationWtPct = 50.0;

  /** Target CO2 content in sweet gas (mol fraction). */
  private double targetCO2MolFraction = 0.02;

  /** Target H2S content in sweet gas (ppmv). If zero, H2S removal is not modelled. */
  private double targetH2SPpmv = 4.0;

  /** Overall CO2 removal efficiency (0.0-1.0). */
  private double co2RemovalEfficiency = 0.90;

  /** Overall H2S removal efficiency (0.0-1.0). */
  private double h2sRemovalEfficiency = 0.99;

  /** Foaming design margin as fraction (0.0-1.0). Applied as capacity derating. */
  private double foamingDesignMargin = 0.20;

  /** Maximum packing height per section in metres. */
  private double maxPackingHeightPerSection = 5.5;

  /** Maximum lean amine temperature in the reboiler (degrees C). Above this, amine degrades. */
  private double maxReboilerTemperatureC = 130.0;

  /** Gas carry-under: Am3 gas per Am3 amine downstream the contactor. */
  private double gasCarryUnder = 0.03;

  /** Demister K-factor limit for wire mesh at gas outlet (m/s). */
  private double maxDemisterKFactor = 0.08;

  /** Required temperature margin: lean amine T above gas feed T (degC). */
  private double amineTemperatureMarginC = 6.0;

  // ============================================================================
  // CALCULATED RESULTS
  // ============================================================================

  /** Calculated acid gas loading (mol acid gas / mol amine). */
  private double richAmineLoading = 0.0;

  /** Lean amine acid gas loading (mol acid gas / mol amine). */
  private double leanAmineLoading = 0.01;

  /** Approach to equilibrium fraction (typically 0.70). */
  private double approachToEquilibrium = 0.70;

  /** Required solvent circulation rate (m3/h). */
  private double requiredCirculationRate = 0.0;

  /** Calculated number of theoretical stages. */
  private double calculatedTheoreticalStages = 0.0;

  /** Effective packing height required (m). */
  private double requiredPackingHeight = 0.0;

  /** Number of packing sections needed. */
  private int numberOfPackingSections = 1;

  /** Heat of absorption (kW). */
  private double heatOfAbsorption = 0.0;

  /** Amine loss rate (kg/MSm3 gas treated). */
  private double amineLossRate = 0.0;

  /** Calculated demister K-factor at gas outlet (m/s). */
  private double calculatedDemisterKFactor = 0.0;

  /** Whether the amine temperature is above gas feed by the required margin. */
  private boolean amineTemperatureAdequate = false;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a simple amine absorber.
   *
   * @param name equipment name
   */
  public SimpleAmineAbsorber(String name) {
    super(name);
    setNumberOfStages(10);
    setStageEfficiency(0.25);
  }

  /**
   * Creates a simple amine absorber with a sour gas feed stream.
   *
   * @param name equipment name
   * @param sourGasStream sour gas inlet stream
   */
  public SimpleAmineAbsorber(String name, StreamInterface sourGasStream) {
    super(name);
    setSourGasInStream(sourGasStream);
    setNumberOfStages(10);
    setStageEfficiency(0.25);
  }

  // ============================================================================
  // STREAM CONFIGURATION
  // ============================================================================

  /**
   * Sets the sour gas inlet stream.
   *
   * <p>
   * The sweet gas outlet stream is created as a clone of the sour gas input.
   * </p>
   *
   * @param stream sour gas stream containing CO2/H2S
   */
  public void setSourGasInStream(StreamInterface stream) {
    this.sourGasInStream = stream;
    this.sweetGasOutStream = stream.clone();
    this.sweetGasOutStream.setName(getName() + " sweet gas out");
  }

  /**
   * Gets the sour gas inlet stream.
   *
   * @return sour gas inlet stream
   */
  public StreamInterface getSourGasInStream() {
    return sourGasInStream;
  }

  /**
   * Sets the lean amine inlet stream.
   *
   * <p>
   * The rich amine outlet stream is created as a clone of the lean amine input.
   * </p>
   *
   * @param stream lean amine stream
   */
  public void setLeanAmineInStream(StreamInterface stream) {
    this.leanAmineInStream = stream;
    this.richAmineOutStream = stream.clone();
    this.richAmineOutStream.setName(getName() + " rich amine out");
  }

  /**
   * Gets the lean amine inlet stream.
   *
   * @return lean amine inlet stream
   */
  public StreamInterface getLeanAmineInStream() {
    return leanAmineInStream;
  }

  /**
   * Gets the sweet gas outlet stream.
   *
   * @return sweet gas outlet stream
   */
  public StreamInterface getSweetGasOutStream() {
    return sweetGasOutStream;
  }

  /**
   * Gets the rich amine outlet stream.
   *
   * @return rich amine outlet stream
   */
  public StreamInterface getRichAmineOutStream() {
    return richAmineOutStream;
  }

  // ============================================================================
  // AMINE PROPERTIES
  // ============================================================================

  /**
   * Sets the amine type.
   *
   * @param type amine type identifier ("MDEA", "DEA", "MEA")
   */
  public void setAmineType(String type) {
    this.amineType = type;
  }

  /**
   * Gets the amine type.
   *
   * @return amine type string
   */
  public String getAmineType() {
    return amineType;
  }

  /**
   * Sets the amine weight percent concentration in lean solvent.
   *
   * @param wtPct weight percent (typically 30-50 for MDEA)
   */
  public void setAmineConcentrationWtPct(double wtPct) {
    this.amineConcentrationWtPct = Math.min(60.0, Math.max(10.0, wtPct));
  }

  /**
   * Gets the amine weight percent concentration.
   *
   * @return amine concentration in wt%
   */
  public double getAmineConcentrationWtPct() {
    return amineConcentrationWtPct;
  }

  /**
   * Sets the CO2 removal efficiency.
   *
   * @param efficiency removal efficiency (0.0-1.0)
   */
  public void setCO2RemovalEfficiency(double efficiency) {
    this.co2RemovalEfficiency = Math.min(1.0, Math.max(0.0, efficiency));
  }

  /**
   * Gets the CO2 removal efficiency.
   *
   * @return removal efficiency (0.0-1.0)
   */
  public double getCO2RemovalEfficiency() {
    return co2RemovalEfficiency;
  }

  /**
   * Sets the H2S removal efficiency.
   *
   * @param efficiency removal efficiency (0.0-1.0)
   */
  public void setH2SRemovalEfficiency(double efficiency) {
    this.h2sRemovalEfficiency = Math.min(1.0, Math.max(0.0, efficiency));
  }

  /**
   * Gets the H2S removal efficiency.
   *
   * @return removal efficiency (0.0-1.0)
   */
  public double getH2SRemovalEfficiency() {
    return h2sRemovalEfficiency;
  }

  // ============================================================================
  // DESIGN PARAMETERS
  // ============================================================================

  /**
   * Sets the foaming design margin.
   *
   * @param margin foaming margin as fraction (default 0.20 = 20%)
   */
  public void setFoamingDesignMargin(double margin) {
    this.foamingDesignMargin = Math.min(0.50, Math.max(0.0, margin));
  }

  /**
   * Gets the foaming design margin.
   *
   * @return foaming margin as fraction
   */
  public double getFoamingDesignMargin() {
    return foamingDesignMargin;
  }

  /**
   * Sets the maximum packing height per section.
   *
   * @param heightM maximum height in metres
   */
  public void setMaxPackingHeightPerSection(double heightM) {
    this.maxPackingHeightPerSection = heightM;
  }

  /**
   * Gets the maximum packing height per section.
   *
   * @return maximum height in metres
   */
  public double getMaxPackingHeightPerSection() {
    return maxPackingHeightPerSection;
  }

  /**
   * Sets the approach to equilibrium fraction for acid gas loading.
   *
   * <p>
   * Experience demonstrates that the loading of acid gas is limited to around 70% of the
   * equilibrium loading in the liquid phase.
   * </p>
   *
   * @param fraction approach fraction (typically 0.70)
   */
  public void setApproachToEquilibrium(double fraction) {
    this.approachToEquilibrium = Math.min(1.0, Math.max(0.1, fraction));
  }

  /**
   * Gets the approach to equilibrium fraction.
   *
   * @return approach fraction
   */
  public double getApproachToEquilibrium() {
    return approachToEquilibrium;
  }

  /**
   * Sets the lean amine loading (residual acid gas in lean amine).
   *
   * @param loading mol acid gas per mol amine in lean solvent
   */
  public void setLeanAmineLoading(double loading) {
    this.leanAmineLoading = Math.max(0.0, loading);
  }

  /**
   * Gets the lean amine loading.
   *
   * @return mol acid gas per mol amine in lean solvent
   */
  public double getLeanAmineLoading() {
    return leanAmineLoading;
  }

  /**
   * Sets the required lean amine to gas feed temperature margin.
   *
   * @param marginC margin in degrees Celsius (typically 6 for HC service)
   */
  public void setAmineTemperatureMarginC(double marginC) {
    this.amineTemperatureMarginC = marginC;
  }

  /**
   * Gets the required lean amine to gas feed temperature margin.
   *
   * @return margin in degrees Celsius
   */
  public double getAmineTemperatureMarginC() {
    return amineTemperatureMarginC;
  }

  // ============================================================================
  // CALCULATIONS
  // ============================================================================

  /**
   * Calculates the acid gas loading on the rich amine.
   *
   * <p>
   * The acid gas pick-up is described as mole acid gas per mole amine. The practical loading is
   * limited by the approach to equilibrium, typically 70% of the thermodynamic equilibrium value.
   * </p>
   *
   * @param equilibriumLoading equilibrium loading (mol acid gas / mol amine)
   * @return practical rich amine loading
   */
  public double calcRichAmineLoading(double equilibriumLoading) {
    richAmineLoading =
        leanAmineLoading + (equilibriumLoading - leanAmineLoading) * approachToEquilibrium;
    return richAmineLoading;
  }

  /**
   * Gets the calculated rich amine acid gas loading.
   *
   * @return acid gas loading (mol acid gas / mol amine)
   */
  public double getRichAmineLoading() {
    return richAmineLoading;
  }

  /**
   * Calculates the minimum required amine circulation rate.
   *
   * <p>
   * Based on the amount of acid gas to be removed and the net loading capacity of the amine:
   * </p>
   *
   * <pre>
   * Q_amine =
   *     F_gas * y_acidgas * efficiency / (rho_amine * x_amine * (loading_rich - loading_lean))
   * </pre>
   *
   * @param acidGasMolFlowMolPerSec molar flow of acid gas to remove (mol/s)
   * @param amineDensityKgPerM3 lean amine solution density (kg/m3)
   * @param amineMolarMassKgPerMol amine molar mass (kg/mol)
   * @return required circulation rate in m3/h
   */
  public double calcRequiredCirculationRate(double acidGasMolFlowMolPerSec,
      double amineDensityKgPerM3, double amineMolarMassKgPerMol) {
    double netLoading = richAmineLoading - leanAmineLoading;
    if (netLoading <= 0.0 || amineDensityKgPerM3 <= 0.0 || amineMolarMassKgPerMol <= 0.0) {
      return 0.0;
    }
    double amineWeightFraction = amineConcentrationWtPct / 100.0;
    double amineMolPerM3 = amineDensityKgPerM3 * amineWeightFraction / amineMolarMassKgPerMol;
    double acidGasToRemoveMolPerH = acidGasMolFlowMolPerSec * 3600.0;
    requiredCirculationRate = acidGasToRemoveMolPerH / (amineMolPerM3 * netLoading);
    return requiredCirculationRate;
  }

  /**
   * Gets the required amine circulation rate.
   *
   * @return circulation rate in m3/h
   */
  public double getRequiredCirculationRate() {
    return requiredCirculationRate;
  }

  /**
   * Calculates the required packing height and number of sections.
   *
   * <p>
   * The packing height is estimated from the number of transfer units and the height of a transfer
   * unit (HTU). Multiple sections with liquid redistribution are used when the height exceeds the
   * maximum per section (typically 5-6 m for fixed installations, 3-5 m for floating).
   * </p>
   *
   * @param htuM height of a transfer unit in metres (typically 0.5-1.5 m)
   * @param ntu number of transfer units
   */
  public void calcPackingHeight(double htuM, double ntu) {
    setHTU(htuM);
    setNTU(ntu);
    requiredPackingHeight = htuM * ntu;
    numberOfPackingSections = (int) Math.ceil(requiredPackingHeight / maxPackingHeightPerSection);
    if (numberOfPackingSections < 1) {
      numberOfPackingSections = 1;
    }
  }

  /**
   * Gets the required total packing height.
   *
   * @return packing height in metres
   */
  public double getRequiredPackingHeight() {
    return requiredPackingHeight;
  }

  /**
   * Gets the number of packing sections needed (each with redistribution).
   *
   * @return number of sections
   */
  public int getNumberOfPackingSections() {
    return numberOfPackingSections;
  }

  /**
   * Calculates the demister K-factor for wire mesh at the gas outlet.
   *
   * <pre>
   * K = Vs * sqrt(rho_gas / (rho_liquid - rho_gas))
   * </pre>
   *
   * @param gasVelocityMs superficial gas velocity through the demister (m/s)
   * @param gasDensityKgM3 gas density (kg/m3)
   * @param liquidDensityKgM3 amine liquid density (kg/m3)
   * @return K-factor in m/s
   */
  public double calcDemisterKFactor(double gasVelocityMs, double gasDensityKgM3,
      double liquidDensityKgM3) {
    if (liquidDensityKgM3 <= gasDensityKgM3) {
      return 0.0;
    }
    calculatedDemisterKFactor =
        gasVelocityMs * Math.sqrt(gasDensityKgM3 / (liquidDensityKgM3 - gasDensityKgM3));
    return calculatedDemisterKFactor;
  }

  /**
   * Gets the maximum allowable demister K-factor.
   *
   * @return max K-factor in m/s
   */
  public double getMaxDemisterKFactor() {
    return maxDemisterKFactor;
  }

  /**
   * Checks if the demister K-factor is within the design limit.
   *
   * @return true if demister is adequately sized
   */
  public boolean isDemisterWithinLimit() {
    return calculatedDemisterKFactor <= maxDemisterKFactor;
  }

  /**
   * Gets the calculated demister K-factor.
   *
   * @return K-factor in m/s
   */
  public double getCalculatedDemisterKFactor() {
    return calculatedDemisterKFactor;
  }

  /**
   * Gets the gas carry-under design value.
   *
   * @return gas carry-under in Am3 gas per Am3 amine
   */
  public double getGasCarryUnder() {
    return gasCarryUnder;
  }

  /**
   * Sets the gas carry-under design value.
   *
   * @param carryUnder gas carry-under in Am3 gas per Am3 amine
   */
  public void setGasCarryUnder(double carryUnder) {
    this.gasCarryUnder = carryUnder;
  }

  /**
   * Checks if the amine temperature has sufficient margin above the gas feed temperature to prevent
   * hydrocarbon condensation in the contactor.
   *
   * @param gasFeedTemperatureC gas feed temperature in degrees Celsius
   * @param amineInletTemperatureC lean amine inlet temperature in degrees Celsius
   * @return true if the margin is sufficient
   */
  public boolean checkAmineTemperatureMargin(double gasFeedTemperatureC,
      double amineInletTemperatureC) {
    amineTemperatureAdequate =
        (amineInletTemperatureC - gasFeedTemperatureC) >= amineTemperatureMarginC;
    return amineTemperatureAdequate;
  }

  /**
   * Gets whether the amine inlet temperature is adequate.
   *
   * @return true if temperature margin has been verified as adequate
   */
  public boolean isAmineTemperatureAdequate() {
    return amineTemperatureAdequate;
  }

  /**
   * Calculates the effective gas capacity with foaming derating.
   *
   * @param designGasFlowM3s design gas flow rate in m3/s
   * @return effective capacity in m3/s after foaming derating
   */
  public double getEffectiveGasCapacityWithFoamingMargin(double designGasFlowM3s) {
    return designGasFlowM3s * (1.0 + foamingDesignMargin);
  }

  // ============================================================================
  // STREAM INTROSPECTION
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> inlets = new ArrayList<>();
    if (sourGasInStream != null) {
      inlets.add(sourGasInStream);
    }
    if (leanAmineInStream != null) {
      inlets.add(leanAmineInStream);
    }
    return Collections.unmodifiableList(inlets);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<>();
    if (sweetGasOutStream != null) {
      outlets.add(sweetGasOutStream);
    }
    if (richAmineOutStream != null) {
      outlets.add(richAmineOutStream);
    }
    return Collections.unmodifiableList(outlets);
  }

  // ============================================================================
  // RUN
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (sourGasInStream == null) {
      setCalculationIdentifier(id);
      return;
    }

    // Clone the sour gas feed as the basis for the sweet gas output
    SystemInterface sweetGasSystem = sourGasInStream.getThermoSystem().clone();

    // Apply acid gas removal by reducing CO2/H2S moles
    if (sweetGasSystem.hasComponent("CO2")) {
      double co2Moles = sweetGasSystem.getPhase(0).getComponent("CO2").getNumberOfmoles();
      double co2ToRemove = co2Moles * co2RemovalEfficiency;
      sweetGasSystem.addComponent("CO2", -co2ToRemove);
    }
    if (sweetGasSystem.hasComponent("H2S")) {
      double h2sMoles = sweetGasSystem.getPhase(0).getComponent("H2S").getNumberOfmoles();
      double h2sToRemove = h2sMoles * h2sRemovalEfficiency;
      sweetGasSystem.addComponent("H2S", -h2sToRemove);
    }

    sweetGasSystem.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(sweetGasSystem);
    try {
      ops.TPflash();
      sweetGasSystem.init(2);
    } catch (Exception ex) {
      logger.error("Flash failed in amine absorber run", ex);
    }

    sweetGasOutStream.setThermoSystem(sweetGasSystem);
    sweetGasOutStream.setCalculationIdentifier(id);

    // Build rich amine system if lean amine stream is available
    if (leanAmineInStream != null && richAmineOutStream != null) {
      SystemInterface richAmineSystem = leanAmineInStream.getThermoSystem().clone();
      // Acid gas picked up by amine appears in the rich amine
      if (sourGasInStream.getThermoSystem().hasComponent("CO2")) {
        double co2Moles =
            sourGasInStream.getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfmoles();
        double co2Removed = co2Moles * co2RemovalEfficiency;
        if (richAmineSystem.hasComponent("CO2")) {
          richAmineSystem.addComponent("CO2", co2Removed);
        }
      }
      if (sourGasInStream.getThermoSystem().hasComponent("H2S")) {
        double h2sMoles =
            sourGasInStream.getThermoSystem().getPhase(0).getComponent("H2S").getNumberOfmoles();
        double h2sRemoved = h2sMoles * h2sRemovalEfficiency;
        if (richAmineSystem.hasComponent("H2S")) {
          richAmineSystem.addComponent("H2S", h2sRemoved);
        }
      }
      richAmineSystem.init(0);
      ThermodynamicOperations richOps = new ThermodynamicOperations(richAmineSystem);
      try {
        richOps.TPflash();
        richAmineSystem.init(2);
      } catch (Exception ex) {
        logger.error("Flash failed for rich amine in amine absorber", ex);
      }
      richAmineOutStream.setThermoSystem(richAmineSystem);
      richAmineOutStream.setCalculationIdentifier(id);
    }

    setCalculationIdentifier(id);
  }

  // ============================================================================
  // DESIGN VALIDATION
  // ============================================================================

  /**
   * Validates the amine absorber design against industry best practices.
   *
   * @return map of check names to pass/fail results
   */
  public Map<String, DesignCheck> validateDesign() {
    Map<String, DesignCheck> checks = new LinkedHashMap<>();

    // Check 1: Foaming margin applied
    checks.put("foaming_margin",
        new DesignCheck("Foaming design margin", foamingDesignMargin >= 0.10, String.format(
            "%.0f%% margin applied (minimum 10%% recommended)", foamingDesignMargin * 100.0)));

    // Check 2: Packing section height
    if (requiredPackingHeight > 0) {
      double heightPerSection = requiredPackingHeight / numberOfPackingSections;
      checks.put("packing_height",
          new DesignCheck("Packing height per section",
              heightPerSection <= maxPackingHeightPerSection,
              String.format("%.1f m per section (max %.1f m, %d sections)", heightPerSection,
                  maxPackingHeightPerSection, numberOfPackingSections)));
    }

    // Check 3: Approach to equilibrium
    checks.put("approach_to_equilibrium",
        new DesignCheck("Approach to equilibrium loading", approachToEquilibrium <= 0.80,
            String.format("%.0f%% of equilibrium (max 70-80%% recommended)",
                approachToEquilibrium * 100.0)));

    // Check 4: Demister K-factor
    if (calculatedDemisterKFactor > 0) {
      checks.put("demister_kfactor", new DesignCheck("Demister K-factor", isDemisterWithinLimit(),
          String.format("%.4f m/s (max %.3f m/s)", calculatedDemisterKFactor, maxDemisterKFactor)));
    }

    // Check 5: Amine temperature margin
    checks.put("amine_temperature",
        new DesignCheck("Amine temperature margin", amineTemperatureAdequate,
            String.format("Required margin: %.1f degC above gas feed", amineTemperatureMarginC)));

    return checks;
  }

  /**
   * Gets a summary of the absorber design.
   *
   * @return design summary string
   */
  public String getDesignSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Amine Absorber Design Summary\n");
    sb.append("==============================\n");
    sb.append(String.format("Amine type: %s at %.1f wt%%\n", amineType, amineConcentrationWtPct));
    sb.append(String.format("CO2 removal efficiency: %.1f%%\n", co2RemovalEfficiency * 100.0));
    sb.append(String.format("H2S removal efficiency: %.1f%%\n", h2sRemovalEfficiency * 100.0));
    sb.append(String.format("Foaming margin: %.0f%%\n", foamingDesignMargin * 100.0));
    sb.append(String.format("Rich amine loading: %.3f mol/mol\n", richAmineLoading));
    sb.append(String.format("Lean amine loading: %.3f mol/mol\n", leanAmineLoading));
    sb.append(String.format("Approach to equilibrium: %.0f%%\n", approachToEquilibrium * 100.0));
    sb.append(String.format("Required circulation rate: %.1f m3/h\n", requiredCirculationRate));
    sb.append(String.format("Required packing height: %.1f m in %d sections\n",
        requiredPackingHeight, numberOfPackingSections));
    sb.append(String.format("Gas carry-under: %.3f Am3/Am3\n", gasCarryUnder));
    sb.append(String.format("Demister K-factor: %.4f m/s (max %.3f)\n", calculatedDemisterKFactor,
        maxDemisterKFactor));
    return sb.toString();
  }

  // ============================================================================
  // INNER CLASS: DesignCheck
  // ============================================================================

  /**
   * Result of a single design check.
   */
  public static class DesignCheck implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Check name. */
    private final String name;
    /** Whether the check passed. */
    private final boolean passed;
    /** Descriptive detail. */
    private final String detail;

    /**
     * Creates a design check result.
     *
     * @param name check name
     * @param passed whether it passed
     * @param detail description
     */
    public DesignCheck(String name, boolean passed, String detail) {
      this.name = name;
      this.passed = passed;
      this.detail = detail;
    }

    /**
     * Gets the check name.
     *
     * @return check name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets whether the check passed.
     *
     * @return true if passed
     */
    public boolean isPassed() {
      return passed;
    }

    /**
     * Gets the detail description.
     *
     * @return detail string
     */
    public String getDetail() {
      return detail;
    }
  }
}
