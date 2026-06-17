package neqsim.process.equipment.splitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Biogas upgrader for producing biomethane from raw biogas.
 *
 * <p>
 * Removes CO2, H2S, and other impurities from raw biogas to produce pipeline-quality biomethane
 * suitable for grid injection or vehicle fuel. Internally uses component-selective splitting to
 * model the separation, with technology-specific removal efficiencies and energy consumption.
 * </p>
 *
 * <h2>Upgrading Technologies</h2>
 *
 * <table>
 * <caption>Technology comparison for biogas upgrading</caption>
 * <tr>
 * <th>Technology</th>
 * <th>CO2 Removal</th>
 * <th>CH4 Recovery</th>
 * <th>Energy (kWh/Nm3)</th>
 * </tr>
 * <tr>
 * <td>WATER_SCRUBBING</td>
 * <td>96-98%</td>
 * <td>98%</td>
 * <td>0.25-0.30</td>
 * </tr>
 * <tr>
 * <td>AMINE_SCRUBBING</td>
 * <td>99%</td>
 * <td>99.9%</td>
 * <td>0.12-0.15</td>
 * </tr>
 * <tr>
 * <td>MEMBRANE</td>
 * <td>96-98%</td>
 * <td>99.5%</td>
 * <td>0.20-0.30</td>
 * </tr>
 * <tr>
 * <td>PSA</td>
 * <td>97-98%</td>
 * <td>98%</td>
 * <td>0.20-0.25</td>
 * </tr>
 * </table>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * BiogasUpgrader upgrader = new BiogasUpgrader("BGU-1", rawBiogasStream);
 * upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.AMINE_SCRUBBING);
 * upgrader.run();
 *
 * StreamInterface biomethane = upgrader.getBiomethaneOutStream();
 * StreamInterface offgas = upgrader.getOffgasOutStream();
 * double methaneContent = upgrader.getBiomethaneMethanePercent();
 * double wobbe = upgrader.getWobbeIndex();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BiogasUpgrader extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1003L;
  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(BiogasUpgrader.class);

  /**
   * Upgrading technology enumeration.
   */
  public enum UpgradingTechnology {
    /** Pressurised water scrubbing. */
    WATER_SCRUBBING(0.97, 0.980, 0.95, 0.28),
    /** Chemical absorption with amines (e.g. MEA, MDEA). */
    AMINE_SCRUBBING(0.99, 0.999, 0.99, 0.14),
    /** Polymeric membrane separation. */
    MEMBRANE(0.97, 0.995, 0.97, 0.25),
    /** Pressure swing adsorption. */
    PSA(0.975, 0.980, 0.95, 0.22);

    /** CO2 removal efficiency (fraction removed from biogas). */
    private final double co2RemovalEfficiency;
    /** Methane recovery (fraction of inlet CH4 in biomethane). */
    private final double methaneRecovery;
    /** H2S removal efficiency. */
    private final double h2sRemovalEfficiency;
    /** Specific energy consumption in kWh per Nm3 raw biogas. */
    private final double specificEnergyKWhPerNm3;

    /**
     * Creates a technology enum constant.
     *
     * @param co2Removal CO2 removal efficiency (0-1)
     * @param ch4Recovery methane recovery (0-1)
     * @param h2sRemoval H2S removal efficiency (0-1)
     * @param energy specific energy in kWh/Nm3 raw biogas
     */
    UpgradingTechnology(double co2Removal, double ch4Recovery, double h2sRemoval, double energy) {
      this.co2RemovalEfficiency = co2Removal;
      this.methaneRecovery = ch4Recovery;
      this.h2sRemovalEfficiency = h2sRemoval;
      this.specificEnergyKWhPerNm3 = energy;
    }

    /**
     * Returns the default CO2 removal efficiency.
     *
     * @return CO2 removal efficiency (0-1)
     */
    public double getCo2RemovalEfficiency() {
      return co2RemovalEfficiency;
    }

    /**
     * Returns the default methane recovery.
     *
     * @return methane recovery (0-1)
     */
    public double getMethaneRecovery() {
      return methaneRecovery;
    }

    /**
     * Returns the default H2S removal efficiency.
     *
     * @return H2S removal efficiency (0-1)
     */
    public double getH2sRemovalEfficiency() {
      return h2sRemovalEfficiency;
    }

    /**
     * Returns the specific energy consumption.
     *
     * @return energy in kWh per Nm3 raw biogas
     */
    public double getSpecificEnergyKWhPerNm3() {
      return specificEnergyKWhPerNm3;
    }
  }

  // ── Configuration ──
  /** Inlet raw biogas stream. */
  private StreamInterface inletStream;
  /** Upgrading technology. */
  private UpgradingTechnology technology = UpgradingTechnology.WATER_SCRUBBING;
  /** CO2 removal efficiency override (NaN = use technology default). */
  private double co2RemovalEfficiency = Double.NaN;
  /** Methane recovery override (NaN = use technology default). */
  private double methaneRecovery = Double.NaN;
  /** H2S removal efficiency override (NaN = use technology default). */
  private double h2sRemovalEfficiency = Double.NaN;
  /** Specific energy override in kWh/Nm3 (NaN = use technology default). */
  private double specificEnergyKWhPerNm3 = Double.NaN;
  /** Target outlet pressure for biomethane in bara. */
  private double outletPressureBara = Double.NaN;

  // ── Output streams ──
  /** Upgraded biomethane (methane-rich) outlet stream. */
  private StreamInterface biomethaneOutStream;
  /** Off-gas (CO2-rich) outlet stream. */
  private StreamInterface offgasOutStream;

  // ── Results ──
  /** Methane content in biomethane product (vol%). */
  private double biomethaneMethanePercent = Double.NaN;
  /** CO2 content in biomethane product (vol%). */
  private double biomethaneCO2Percent = Double.NaN;
  /** Wobbe index of biomethane product in MJ/Nm3. */
  private double wobbeIndex = Double.NaN;
  /** Energy consumption in kW. */
  private double energyConsumptionKW = Double.NaN;
  /** Methane slip to off-gas as percent of inlet methane. */
  private double methaneSlipPercent = Double.NaN;
  /** Raw biogas flow rate in Nm3/hr. */
  private double rawBiogasFlowNm3PerHr = Double.NaN;
  /** Biomethane product flow rate in Nm3/hr. */
  private double biomethaneFlowNm3PerHr = Double.NaN;
  /** Whether the upgrader has been run. */
  private boolean hasRun = false;

  /**
   * Creates a biogas upgrader with the given name.
   *
   * @param name equipment name
   */
  public BiogasUpgrader(String name) {
    super(name);
  }

  /**
   * Creates a biogas upgrader with the given name and inlet stream.
   *
   * @param name equipment name
   * @param inletStream the raw biogas inlet stream
   */
  public BiogasUpgrader(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * Sets the inlet raw biogas stream.
   *
   * @param stream the inlet stream
   */
  public void setInletStream(StreamInterface stream) {
    this.inletStream = stream;
  }

  /**
   * Gets the inlet stream.
   *
   * @return inlet stream
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * Sets the upgrading technology.
   *
   * @param tech the upgrading technology
   */
  public void setTechnology(UpgradingTechnology tech) {
    this.technology = tech;
  }

  /**
   * Gets the upgrading technology.
   *
   * @return upgrading technology
   */
  public UpgradingTechnology getTechnology() {
    return technology;
  }

  /**
   * Sets the CO2 removal efficiency override.
   *
   * @param efficiency CO2 removal efficiency (0-1)
   */
  public void setCO2RemovalEfficiency(double efficiency) {
    this.co2RemovalEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Sets the methane recovery override.
   *
   * @param recovery methane recovery (0-1)
   */
  public void setMethaneRecovery(double recovery) {
    this.methaneRecovery = Math.max(0.0, Math.min(1.0, recovery));
  }

  /**
   * Sets the H2S removal efficiency override.
   *
   * @param efficiency H2S removal efficiency (0-1)
   */
  public void setH2SRemovalEfficiency(double efficiency) {
    this.h2sRemovalEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Sets the specific energy consumption override in kWh per Nm3 raw biogas.
   *
   * @param energy specific energy in kWh/Nm3
   */
  public void setSpecificEnergy(double energy) {
    this.specificEnergyKWhPerNm3 = Math.max(0.0, energy);
  }

  /**
   * Sets the target outlet pressure for the biomethane stream in bara.
   *
   * @param pressureBara outlet pressure in bara
   */
  public void setOutletPressure(double pressureBara) {
    this.outletPressureBara = pressureBara;
  }

  /**
   * Returns the upgraded biomethane outlet stream.
   *
   * @return biomethane outlet stream, or null if not yet run
   */
  public StreamInterface getBiomethaneOutStream() {
    return biomethaneOutStream;
  }

  /**
   * Returns the off-gas (CO2-rich) outlet stream.
   *
   * @return off-gas outlet stream, or null if not yet run
   */
  public StreamInterface getOffgasOutStream() {
    return offgasOutStream;
  }

  /**
   * Returns the methane content of the biomethane product as volume percent.
   *
   * @return methane content in vol%
   */
  public double getBiomethaneMethanePercent() {
    return biomethaneMethanePercent;
  }

  /**
   * Returns the CO2 content of the biomethane product as volume percent.
   *
   * @return CO2 content in vol%
   */
  public double getBiomethaneCO2Percent() {
    return biomethaneCO2Percent;
  }

  /**
   * Returns the Wobbe index of the biomethane product in MJ/Nm3.
   *
   * @return Wobbe index
   */
  public double getWobbeIndex() {
    return wobbeIndex;
  }

  /**
   * Returns the energy consumption of the upgrading process in kW.
   *
   * @return energy consumption in kW
   */
  public double getEnergyConsumptionKW() {
    return energyConsumptionKW;
  }

  /**
   * Returns the methane slip to the off-gas as a percentage of inlet methane.
   *
   * @return methane slip in percent
   */
  public double getMethaneSlipPercent() {
    return methaneSlipPercent;
  }

  /**
   * Returns the raw biogas volumetric flow rate in Nm3/hr.
   *
   * @return raw biogas flow rate
   */
  public double getRawBiogasFlowNm3PerHr() {
    return rawBiogasFlowNm3PerHr;
  }

  /**
   * Returns the biomethane product volumetric flow rate in Nm3/hr.
   *
   * @return biomethane flow rate
   */
  public double getBiomethaneFlowNm3PerHr() {
    return biomethaneFlowNm3PerHr;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    if (inletStream != null) {
      return Collections.singletonList(inletStream);
    }
    return Collections.emptyList();
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<StreamInterface>();
    if (biomethaneOutStream != null) {
      outlets.add(biomethaneOutStream);
    }
    if (offgasOutStream != null) {
      outlets.add(offgasOutStream);
    }
    return Collections.unmodifiableList(outlets);
  }

  /**
   * Runs the biogas upgrader simulation.
   *
   * <p>
   * Applies technology-specific removal efficiencies to selectively split inlet biogas components
   * into a methane-rich biomethane stream and a CO2-rich off-gas stream. Both outlet streams are
   * flashed at the outlet conditions.
   * </p>
   *
   * @param id UUID for this run
   */
  @Override
  public void run(UUID id) {
    if (inletStream == null) {
      throw new IllegalStateException("Inlet stream must be set before running");
    }

    SystemInterface inletFluid = inletStream.getThermoSystem();
    if (inletFluid == null) {
      throw new IllegalStateException("Inlet stream has no thermodynamic system");
    }

    // ── Step 1: Resolve technology parameters ──
    double effCO2Removal = Double.isNaN(co2RemovalEfficiency) ? technology.getCo2RemovalEfficiency()
        : co2RemovalEfficiency;
    double effCH4Recovery =
        Double.isNaN(methaneRecovery) ? technology.getMethaneRecovery() : methaneRecovery;
    double effH2SRemoval = Double.isNaN(h2sRemovalEfficiency) ? technology.getH2sRemovalEfficiency()
        : h2sRemovalEfficiency;
    double effEnergy =
        Double.isNaN(specificEnergyKWhPerNm3) ? technology.getSpecificEnergyKWhPerNm3()
            : specificEnergyKWhPerNm3;

    // ── Step 2: Calculate raw biogas flow ──
    double totalMolesPerHr = inletFluid.getFlowRate("mole/hr");
    rawBiogasFlowNm3PerHr = totalMolesPerHr * 22.414 / 1000.0;

    // ── Step 3: Build split factors for biomethane stream ──
    // splitFactor[i] = fraction of component i that goes to biomethane (stream 0)
    int numComp = inletFluid.getNumberOfComponents();
    double[] splitFactors = new double[numComp];
    for (int i = 0; i < numComp; i++) {
      String compName = inletFluid.getComponent(i).getComponentName();
      if ("CO2".equalsIgnoreCase(compName)) {
        // CO2: most removed to off-gas, small fraction remains in biomethane
        splitFactors[i] = 1.0 - effCO2Removal;
      } else if ("methane".equalsIgnoreCase(compName)) {
        // Methane: high recovery to biomethane
        splitFactors[i] = effCH4Recovery;
      } else if ("H2S".equalsIgnoreCase(compName)) {
        // H2S: removed with CO2
        splitFactors[i] = 1.0 - effH2SRemoval;
      } else if ("water".equalsIgnoreCase(compName)) {
        // Water: mostly removed during upgrading (dehydration)
        splitFactors[i] = 0.05;
      } else if ("nitrogen".equalsIgnoreCase(compName)) {
        // N2: partially removed depending on technology
        splitFactors[i] = technology == UpgradingTechnology.PSA ? 0.20 : 0.85;
      } else if ("oxygen".equalsIgnoreCase(compName)) {
        // O2: partially removed
        splitFactors[i] = 0.50;
      } else if ("hydrogen".equalsIgnoreCase(compName)) {
        // H2: passes through most technologies
        splitFactors[i] = 0.90;
      } else {
        // Other trace components: assume they follow methane
        splitFactors[i] = effCH4Recovery;
      }
    }

    // ── Step 4: Create biomethane stream (split stream 0) ──
    double outPressure =
        Double.isNaN(outletPressureBara) ? inletFluid.getPressure() : outletPressureBara;
    double outTemp = inletFluid.getTemperature();

    SystemInterface biomethaneFluid = inletFluid.clone();
    biomethaneFluid.setEmptyFluid();
    double totalBiomethaneMoles = 0.0;
    for (int k = 0; k < numComp; k++) {
      double moles = inletFluid.getComponent(k).getNumberOfmoles() * splitFactors[k];
      biomethaneFluid.addComponent(k, moles);
      totalBiomethaneMoles += moles;
    }

    if (totalBiomethaneMoles > 0.0) {
      biomethaneFluid.setPressure(outPressure);
      biomethaneFluid.setTemperature(outTemp);
      biomethaneFluid.init(0);
      ThermodynamicOperations biomethaneOps = new ThermodynamicOperations(biomethaneFluid);
      biomethaneOps.TPflash();
    }
    biomethaneOutStream = new Stream(getName() + " biomethane", biomethaneFluid);

    // ── Step 5: Create off-gas stream (split stream 1) ──
    SystemInterface offgasFluid = inletFluid.clone();
    offgasFluid.setEmptyFluid();
    double totalOffgasMoles = 0.0;
    for (int k = 0; k < numComp; k++) {
      double moles = inletFluid.getComponent(k).getNumberOfmoles() * (1.0 - splitFactors[k]);
      offgasFluid.addComponent(k, moles);
      totalOffgasMoles += moles;
    }

    if (totalOffgasMoles > 0.0) {
      offgasFluid.setPressure(ThermodynamicConstantsInterface.referencePressure);
      offgasFluid.setTemperature(outTemp);
      offgasFluid.init(0);
      ThermodynamicOperations offgasOps = new ThermodynamicOperations(offgasFluid);
      offgasOps.TPflash();
    }
    offgasOutStream = new Stream(getName() + " offgas", offgasFluid);

    // ── Step 6: Calculate quality metrics ──
    calculateQualityMetrics(biomethaneFluid, effCH4Recovery, effEnergy);

    hasRun = true;
    setCalculationIdentifier(id);
  }

  /**
   * Calculates biomethane quality metrics after the split.
   *
   * @param biomethaneFluid the biomethane fluid system
   * @param ch4Recovery the methane recovery used
   * @param energyPerNm3 specific energy consumption in kWh/Nm3
   */
  private void calculateQualityMetrics(SystemInterface biomethaneFluid, double ch4Recovery,
      double energyPerNm3) {
    // Methane and CO2 content in biomethane
    biomethaneMethanePercent = getMoleFractionPercent(biomethaneFluid, "methane");
    biomethaneCO2Percent = getMoleFractionPercent(biomethaneFluid, "CO2");

    // Methane slip
    methaneSlipPercent = (1.0 - ch4Recovery) * 100.0;

    // Biomethane flow rate
    double biomethaneMolesPerHr = biomethaneFluid.getFlowRate("mole/hr");
    biomethaneFlowNm3PerHr = biomethaneMolesPerHr * 22.414 / 1000.0;

    // Energy consumption
    energyConsumptionKW = rawBiogasFlowNm3PerHr * energyPerNm3;

    // Wobbe index: W = HHV / sqrt(specific gravity)
    // Approximate HHV from composition: CH4=39.82 MJ/Nm3, H2=12.75 MJ/Nm3
    double ch4Frac = biomethaneMethanePercent / 100.0;
    double h2Frac = getMoleFractionPercent(biomethaneFluid, "hydrogen") / 100.0;
    double co2Frac = biomethaneCO2Percent / 100.0;
    double n2Frac = getMoleFractionPercent(biomethaneFluid, "nitrogen") / 100.0;

    double hhv = ch4Frac * 39.82 + h2Frac * 12.75; // MJ/Nm3

    // Specific gravity relative to air (MW_mix / MW_air)
    double mwMix = ch4Frac * 16.04 + co2Frac * 44.01 + n2Frac * 28.01 + h2Frac * 2.016
        + (1.0 - ch4Frac - co2Frac - n2Frac - h2Frac) * 18.015;
    double sg = mwMix / 28.97;
    wobbeIndex = sg > 0 ? hhv / Math.sqrt(sg) : 0.0;
  }

  /**
   * Returns the mole fraction of a component as a percentage.
   *
   * @param fluid the fluid system
   * @param componentName component name
   * @return mole fraction in percent (0-100)
   */
  private double getMoleFractionPercent(SystemInterface fluid, String componentName) {
    try {
      if (fluid != null && fluid.hasComponent(componentName)) {
        return fluid.getPhase(0).getComponent(componentName).getz() * 100.0;
      }
    } catch (Exception e) {
      logger.debug("Could not get mole fraction for {}: {}", componentName, e.getMessage());
    }
    return 0.0;
  }

  /**
   * Returns a map of key results from the upgrader simulation.
   *
   * @return map of result name to value
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("technology", technology.name());
    results.put("rawBiogasFlow_Nm3PerHr", rawBiogasFlowNm3PerHr);
    results.put("biomethaneFlow_Nm3PerHr", biomethaneFlowNm3PerHr);
    results.put("biomethaneMethane_volPercent", biomethaneMethanePercent);
    results.put("biomethaneCO2_volPercent", biomethaneCO2Percent);
    results.put("wobbeIndex_MJperNm3", wobbeIndex);
    results.put("methaneSlip_percent", methaneSlipPercent);
    results.put("energyConsumption_kW", energyConsumptionKW);

    // Check grid injection quality (typical European spec)
    boolean gridQuality = biomethaneMethanePercent >= 95.0 && biomethaneCO2Percent <= 2.5;
    results.put("meetsGridInjectionSpec", gridQuality);

    return results;
  }

  /**
   * Returns a JSON string with the upgrader results.
   *
   * @return JSON results string
   */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!hasRun) {
      return "BiogasUpgrader '" + getName() + "' (not yet run)";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("BiogasUpgrader '").append(getName()).append("'\n");
    sb.append(String.format("  Technology: %s%n", technology));
    sb.append(String.format("  Raw biogas: %.1f Nm3/hr%n", rawBiogasFlowNm3PerHr));
    sb.append(String.format("  Biomethane: %.1f Nm3/hr (%.1f%% CH4)%n", biomethaneFlowNm3PerHr,
        biomethaneMethanePercent));
    sb.append(String.format("  CO2 in product: %.2f%%%n", biomethaneCO2Percent));
    sb.append(String.format("  Wobbe index: %.1f MJ/Nm3%n", wobbeIndex));
    sb.append(String.format("  Methane slip: %.2f%%%n", methaneSlipPercent));
    sb.append(String.format("  Energy: %.1f kW%n", energyConsumptionKW));
    return sb.toString();
  }
}
