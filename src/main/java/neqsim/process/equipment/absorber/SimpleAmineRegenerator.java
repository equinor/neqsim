package neqsim.process.equipment.absorber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.amines.AmineHeatOfAbsorption;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Simple amine regenerator (stripper) model for solvent regeneration.
 *
 * <p>
 * Models the thermal regeneration of a rich amine solvent that has picked up acid gas (CO2 and H2S) in an absorber.
 * This is the counterpart of {@link SimpleAmineAbsorber}: it takes a rich amine inlet stream and produces a lean amine
 * outlet stream (returned to the absorber) and an acid gas overhead stream (CO2/H2S sent to compression or vent).
 * </p>
 *
 * <p>
 * The model uses a robust lumped acid-gas mass balance rather than a rigorous rate-based stripping calculation. The
 * amount of acid gas stripped is governed by a target lean loading and a regeneration efficiency. The reboiler duty is
 * estimated from three physically meaningful contributions:
 * </p>
 * <ul>
 * <li><b>Heat of desorption</b> of CO2 from the amine (from {@link AmineHeatOfAbsorption});</li>
 * <li><b>Sensible heat</b> to raise the rich amine from its inlet temperature to the reboiler temperature;</li>
 * <li><b>Stripping steam</b> latent heat needed to generate the stripping vapour.</li>
 * </ul>
 *
 * <h2>Design Parameters</h2>
 * <ul>
 * <li><b>Lean loading target:</b> residual mol acid gas per mol amine after regeneration (typically 0.01-0.20)</li>
 * <li><b>Reboiler temperature:</b> typically 110-125 degC for MDEA, limited by amine degradation</li>
 * <li><b>Regeneration efficiency:</b> fraction of the strippable acid gas actually removed</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SimpleAmineRegenerator extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SimpleAmineRegenerator.class);

  /** Molar mass of CO2 in kg/mol. */
  private static final double CO2_MOLAR_MASS_KG_PER_MOL = 0.04401;

  /** Latent heat of vaporization of water in kJ/mol (approximate, near reboiler conditions). */
  private static final double WATER_LATENT_HEAT_KJ_PER_MOL = 40.7;

  /** Representative specific heat capacity of aqueous amine solution in kJ/(kg K). */
  private static final double AMINE_CP_KJ_PER_KG_K = 3.8;

  // ============================================================================
  // STREAM REFERENCES
  // ============================================================================

  /** Rich amine inlet stream (acid-gas loaded solvent from the absorber). */
  private StreamInterface richAmineInStream;

  /** Lean amine outlet stream (regenerated solvent returned to the absorber). */
  private StreamInterface leanAmineOutStream;

  /** Acid gas overhead outlet stream (stripped CO2/H2S). */
  private StreamInterface acidGasOutStream;

  // ============================================================================
  // DESIGN PARAMETERS
  // ============================================================================

  /** Amine type: "MDEA", "DEA", "MEA", or "AMDEA". */
  private String amineType = "MDEA";

  /** Amine weight percent concentration in the solvent (typically 30-50 wt%). */
  private double amineConcentrationWtPct = 50.0;

  /** Target lean amine loading after regeneration (mol acid gas / mol amine). */
  private double leanLoadingTarget = 0.01;

  /** Regeneration efficiency (0.0-1.0): fraction of strippable acid gas removed. */
  private double regenerationEfficiency = 0.95;

  /** Reboiler temperature (degrees C). Limited by amine thermal degradation. */
  private double reboilerTemperatureC = 120.0;

  /** Overhead condenser temperature (degrees C) for the acid gas stream. */
  private double condenserTemperatureC = 50.0;

  /** Moles of stripping steam generated per mole of CO2 stripped. */
  private double strippingSteamMolRatio = 1.5;

  /** Moles of water vapour carried overhead per mole of CO2 stripped. */
  private double overheadWaterMolRatio = 0.10;

  // ============================================================================
  // CALCULATED RESULTS
  // ============================================================================

  /** Calculated rich amine loading (mol acid gas / mol amine). */
  private double richLoading = 0.0;

  /** Calculated lean amine loading after regeneration (mol acid gas / mol amine). */
  private double leanLoading = 0.0;

  /** Moles of CO2 stripped (system-mole basis). */
  private double strippedCO2Moles = 0.0;

  /** Reboiler duty contribution from heat of desorption (kW). */
  private double desorptionDutyKW = 0.0;

  /** Reboiler duty contribution from sensible heating (kW). */
  private double sensibleDutyKW = 0.0;

  /** Reboiler duty contribution from stripping steam (kW). */
  private double strippingDutyKW = 0.0;

  /** Total reboiler duty (kW). */
  private double reboilerDutyKW = 0.0;

  /** Specific reboiler duty (MJ per kg CO2 stripped). */
  private double specificReboilerDutyMJperKgCO2 = 0.0;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a simple amine regenerator.
   *
   * @param name equipment name
   */
  public SimpleAmineRegenerator(String name) {
    super(name);
  }

  /**
   * Creates a simple amine regenerator with a rich amine feed stream.
   *
   * @param name equipment name
   * @param richAmineStream rich amine inlet stream
   */
  public SimpleAmineRegenerator(String name, StreamInterface richAmineStream) {
    super(name);
    setRichAmineInStream(richAmineStream);
  }

  // ============================================================================
  // STREAM CONFIGURATION
  // ============================================================================

  /**
   * Sets the rich amine inlet stream.
   *
   * <p>
   * The lean amine and acid gas outlet streams are created as clones of the rich amine input.
   * </p>
   *
   * @param stream rich amine stream loaded with acid gas
   */
  public void setRichAmineInStream(StreamInterface stream) {
    this.richAmineInStream = stream;
    this.leanAmineOutStream = stream.clone();
    this.leanAmineOutStream.setName(getName() + " lean amine out");
    this.acidGasOutStream = stream.clone();
    this.acidGasOutStream.setName(getName() + " acid gas out");
  }

  /**
   * Gets the rich amine inlet stream.
   *
   * @return rich amine inlet stream
   */
  public StreamInterface getRichAmineInStream() {
    return richAmineInStream;
  }

  /**
   * Gets the lean amine outlet stream.
   *
   * @return lean amine outlet stream
   */
  public StreamInterface getLeanAmineOutStream() {
    return leanAmineOutStream;
  }

  /**
   * Gets the acid gas overhead outlet stream.
   *
   * @return acid gas outlet stream
   */
  public StreamInterface getAcidGasOutStream() {
    return acidGasOutStream;
  }

  // ============================================================================
  // DESIGN PARAMETERS
  // ============================================================================

  /**
   * Sets the amine type.
   *
   * @param type amine type identifier ("MDEA", "DEA", "MEA", "AMDEA")
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
   * Sets the amine weight percent concentration in the solvent.
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
   * Sets the target lean amine loading after regeneration.
   *
   * @param loading mol acid gas per mol amine in the regenerated solvent
   */
  public void setLeanLoadingTarget(double loading) {
    this.leanLoadingTarget = Math.max(0.0, loading);
  }

  /**
   * Gets the target lean amine loading.
   *
   * @return mol acid gas per mol amine in the regenerated solvent
   */
  public double getLeanLoadingTarget() {
    return leanLoadingTarget;
  }

  /**
   * Sets the regeneration efficiency.
   *
   * @param efficiency fraction of strippable acid gas removed (0.0-1.0)
   */
  public void setRegenerationEfficiency(double efficiency) {
    this.regenerationEfficiency = Math.min(1.0, Math.max(0.0, efficiency));
  }

  /**
   * Gets the regeneration efficiency.
   *
   * @return regeneration efficiency (0.0-1.0)
   */
  public double getRegenerationEfficiency() {
    return regenerationEfficiency;
  }

  /**
   * Sets the reboiler temperature.
   *
   * @param temperatureC reboiler temperature in degrees Celsius
   */
  public void setReboilerTemperatureC(double temperatureC) {
    this.reboilerTemperatureC = temperatureC;
  }

  /**
   * Gets the reboiler temperature.
   *
   * @return reboiler temperature in degrees Celsius
   */
  public double getReboilerTemperatureC() {
    return reboilerTemperatureC;
  }

  /**
   * Sets the overhead condenser temperature for the acid gas stream.
   *
   * @param temperatureC condenser temperature in degrees Celsius
   */
  public void setCondenserTemperatureC(double temperatureC) {
    this.condenserTemperatureC = temperatureC;
  }

  /**
   * Gets the overhead condenser temperature.
   *
   * @return condenser temperature in degrees Celsius
   */
  public double getCondenserTemperatureC() {
    return condenserTemperatureC;
  }

  /**
   * Sets the stripping steam to CO2 molar ratio.
   *
   * @param ratio moles of stripping steam per mole of CO2 stripped
   */
  public void setStrippingSteamMolRatio(double ratio) {
    this.strippingSteamMolRatio = Math.max(0.0, ratio);
  }

  /**
   * Gets the stripping steam to CO2 molar ratio.
   *
   * @return moles of stripping steam per mole of CO2 stripped
   */
  public double getStrippingSteamMolRatio() {
    return strippingSteamMolRatio;
  }

  // ============================================================================
  // CALCULATED RESULTS
  // ============================================================================

  /**
   * Gets the calculated rich amine loading.
   *
   * @return rich amine loading (mol acid gas / mol amine)
   */
  public double getRichLoading() {
    return richLoading;
  }

  /**
   * Gets the calculated lean amine loading after regeneration.
   *
   * @return lean amine loading (mol acid gas / mol amine)
   */
  public double getLeanLoading() {
    return leanLoading;
  }

  /**
   * Gets the reboiler duty contribution from heat of desorption.
   *
   * @return desorption duty in kW
   */
  public double getDesorptionDutyKW() {
    return desorptionDutyKW;
  }

  /**
   * Gets the reboiler duty contribution from sensible heating.
   *
   * @return sensible duty in kW
   */
  public double getSensibleDutyKW() {
    return sensibleDutyKW;
  }

  /**
   * Gets the reboiler duty contribution from stripping steam.
   *
   * @return stripping duty in kW
   */
  public double getStrippingDutyKW() {
    return strippingDutyKW;
  }

  /**
   * Gets the total reboiler duty.
   *
   * @return reboiler duty in kW
   */
  public double getReboilerDutyKW() {
    return reboilerDutyKW;
  }

  /**
   * Gets the specific reboiler duty per unit mass of CO2 stripped.
   *
   * @return specific reboiler duty in MJ per kg CO2
   */
  public double getSpecificReboilerDutyMJperKgCO2() {
    return specificReboilerDutyMJperKgCO2;
  }

  // ============================================================================
  // INTERNAL HELPERS
  // ============================================================================

  /**
   * Counts the total moles of molecular amine present in the system.
   *
   * @param system thermodynamic system to inspect
   * @return total moles of amine (MDEA, DEA, MEA, AMDEA, Piperazine)
   */
  private double countAmineMoles(SystemInterface system) {
    String[] amineComps = { "MDEA", "DEA", "MEA", "AMDEA", "Piperazine" };
    double total = 0.0;
    for (int i = 0; i < amineComps.length; i++) {
      if (system.hasComponent(amineComps[i])) {
        total += system.getPhase(0).getComponent(amineComps[i]).getNumberOfmoles();
      }
    }
    return total;
  }

  /**
   * Maps the amine type string to a heat-of-absorption amine type.
   *
   * @return matching {@link AmineHeatOfAbsorption.AmineType}, defaulting to MDEA
   */
  private AmineHeatOfAbsorption.AmineType mapHeatType() {
    if (amineType == null) {
      return AmineHeatOfAbsorption.AmineType.MDEA;
    }
    String upper = amineType.trim().toUpperCase();
    if (upper.startsWith("MEA")) {
      return AmineHeatOfAbsorption.AmineType.MEA;
    }
    if (upper.startsWith("DEA")) {
      return AmineHeatOfAbsorption.AmineType.DEA;
    }
    if (upper.startsWith("AMDEA")) {
      return AmineHeatOfAbsorption.AmineType.AMDEA;
    }
    return AmineHeatOfAbsorption.AmineType.MDEA;
  }

  /**
   * Calculates the reboiler duty from the desorption, sensible and stripping contributions.
   *
   * @param co2StripMolesSystemBasis moles of CO2 stripped on the system-mole basis
   */
  private void calcReboilerDuty(double co2StripMolesSystemBasis) {
    desorptionDutyKW = 0.0;
    sensibleDutyKW = 0.0;
    strippingDutyKW = 0.0;
    reboilerDutyKW = 0.0;
    specificReboilerDutyMJperKgCO2 = 0.0;

    if (richAmineInStream == null || co2StripMolesSystemBasis <= 0.0) {
      return;
    }

    SystemInterface richSystem = richAmineInStream.getThermoSystem();
    double totalSystemMoles = richSystem.getTotalNumberOfMoles();
    if (totalSystemMoles <= 0.0) {
      return;
    }

    // Convert the system-mole stripped quantity to a molar rate (mol/s) using the stream flow rate.
    double molarFlowMolPerSec = richAmineInStream.getFlowRate("mole/sec");
    double co2StripMolPerSec = co2StripMolesSystemBasis * (molarFlowMolPerSec / totalSystemMoles);
    if (co2StripMolPerSec <= 0.0) {
      return;
    }

    // 1) Heat of desorption (endothermic on the reboiler side).
    AmineHeatOfAbsorption hoa = new AmineHeatOfAbsorption(mapHeatType(), amineConcentrationWtPct / 100.0,
        Math.max(leanLoadingTarget, richLoading), reboilerTemperatureC + 273.15);
    double heatAbsKJperMol = Math.abs(hoa.calcHeatOfAbsorptionCO2());
    desorptionDutyKW = heatAbsKJperMol * co2StripMolPerSec;

    // 2) Sensible heat to raise the rich amine to the reboiler temperature.
    double massFlowKgPerSec = richAmineInStream.getFlowRate("kg/sec");
    double inletTemperatureK = richSystem.getTemperature();
    double deltaTK = (reboilerTemperatureC + 273.15) - inletTemperatureK;
    if (deltaTK > 0.0 && massFlowKgPerSec > 0.0) {
      sensibleDutyKW = massFlowKgPerSec * AMINE_CP_KJ_PER_KG_K * deltaTK;
    }

    // 3) Stripping steam latent heat.
    strippingDutyKW = strippingSteamMolRatio * WATER_LATENT_HEAT_KJ_PER_MOL * co2StripMolPerSec;

    reboilerDutyKW = desorptionDutyKW + sensibleDutyKW + strippingDutyKW;

    double co2MassRateKgPerSec = co2StripMolPerSec * CO2_MOLAR_MASS_KG_PER_MOL;
    if (co2MassRateKgPerSec > 0.0) {
      // kW (kJ/s) divided by kg/s gives kJ/kg; divide by 1000 for MJ/kg.
      specificReboilerDutyMJperKgCO2 = reboilerDutyKW / co2MassRateKgPerSec / 1000.0;
    }
  }

  // ============================================================================
  // STREAM INTROSPECTION
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> inlets = new ArrayList<>();
    if (richAmineInStream != null) {
      inlets.add(richAmineInStream);
    }
    return Collections.unmodifiableList(inlets);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<>();
    if (leanAmineOutStream != null) {
      outlets.add(leanAmineOutStream);
    }
    if (acidGasOutStream != null) {
      outlets.add(acidGasOutStream);
    }
    return Collections.unmodifiableList(outlets);
  }

  // ============================================================================
  // RUN
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (richAmineInStream == null) {
      setCalculationIdentifier(id);
      return;
    }

    SystemInterface richSystem = richAmineInStream.getThermoSystem().clone();
    richSystem.init(0);

    double amineMoles = countAmineMoles(richSystem);
    double co2Moles = richSystem.hasComponent("CO2") ? richSystem.getPhase(0).getComponent("CO2").getNumberOfmoles()
        : 0.0;
    double h2sMoles = richSystem.hasComponent("H2S") ? richSystem.getPhase(0).getComponent("H2S").getNumberOfmoles()
        : 0.0;

    double co2ToStrip = 0.0;
    if (amineMoles > 0.0 && co2Moles > 0.0) {
      double targetCO2Moles = leanLoadingTarget * amineMoles;
      co2ToStrip = Math.max(0.0, co2Moles - targetCO2Moles) * regenerationEfficiency;
    }
    double h2sToStrip = h2sMoles * regenerationEfficiency;

    strippedCO2Moles = co2ToStrip;
    richLoading = amineMoles > 0.0 ? co2Moles / amineMoles : 0.0;
    leanLoading = amineMoles > 0.0 ? (co2Moles - co2ToStrip) / amineMoles : 0.0;

    // --- Lean amine outlet: rich solvent with acid gas removed, heated to reboiler temperature ---
    SystemInterface leanSystem = richSystem.clone();
    if (leanSystem.hasComponent("CO2") && co2ToStrip > 0.0) {
      leanSystem.addComponent("CO2", -co2ToStrip);
    }
    if (leanSystem.hasComponent("H2S") && h2sToStrip > 0.0) {
      leanSystem.addComponent("H2S", -h2sToStrip);
    }
    leanSystem.setTemperature(reboilerTemperatureC + 273.15);
    leanSystem.init(0);
    ThermodynamicOperations leanOps = new ThermodynamicOperations(leanSystem);
    try {
      leanOps.TPflash();
      leanSystem.initProperties();
    } catch (Exception ex) {
      logger.error("Flash failed for lean amine in regenerator", ex);
    }
    leanAmineOutStream.setThermoSystem(leanSystem);
    leanAmineOutStream.setCalculationIdentifier(id);

    // --- Acid gas overhead: stripped CO2/H2S plus some water vapour ---
    SystemInterface acidSystem = richAmineInStream.getThermoSystem().clone();
    acidSystem.setEmptyFluid();
    if (acidSystem.hasComponent("CO2") && co2ToStrip > 0.0) {
      acidSystem.addComponent("CO2", co2ToStrip);
    }
    if (acidSystem.hasComponent("H2S") && h2sToStrip > 0.0) {
      acidSystem.addComponent("H2S", h2sToStrip);
    }
    if (acidSystem.hasComponent("water") && co2ToStrip > 0.0) {
      acidSystem.addComponent("water", co2ToStrip * overheadWaterMolRatio);
    }
    acidSystem.setTemperature(condenserTemperatureC + 273.15);
    acidSystem.init(0);
    ThermodynamicOperations acidOps = new ThermodynamicOperations(acidSystem);
    try {
      acidOps.TPflash();
      acidSystem.initProperties();
    } catch (Exception ex) {
      logger.error("Flash failed for acid gas in regenerator", ex);
    }
    acidGasOutStream.setThermoSystem(acidSystem);
    acidGasOutStream.setCalculationIdentifier(id);

    // --- Reboiler duty estimate ---
    calcReboilerDuty(co2ToStrip);

    setCalculationIdentifier(id);
  }

  /**
   * Gets a summary of the regenerator design and performance.
   *
   * @return design summary string
   */
  public String getDesignSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Amine Regenerator Design Summary\n");
    sb.append("=================================\n");
    sb.append(String.format("Amine type: %s at %.1f wt%%\n", amineType, amineConcentrationWtPct));
    sb.append(String.format("Reboiler temperature: %.1f degC\n", reboilerTemperatureC));
    sb.append(String.format("Rich amine loading: %.3f mol/mol\n", richLoading));
    sb.append(String.format("Lean amine loading: %.3f mol/mol\n", leanLoading));
    sb.append(String.format("Regeneration efficiency: %.1f%%\n", regenerationEfficiency * 100.0));
    sb.append(String.format("Reboiler duty: %.1f kW\n", reboilerDutyKW));
    sb.append(String.format("Specific reboiler duty: %.2f MJ/kg CO2\n", specificReboilerDutyMJperKgCO2));
    return sb.toString();
  }
}
