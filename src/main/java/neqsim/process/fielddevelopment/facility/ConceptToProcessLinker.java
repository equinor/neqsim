package neqsim.process.fielddevelopment.facility;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Links field development concepts to detailed process models.
 *
 * <p>
 * Bridges the gap between high-level field development concepts (DG0-DG2) and detailed process
 * simulation models (DG3+). Auto-generates process flowsheets based on concept parameters.
 * </p>
 *
 * <h2>Capabilities</h2>
 * <ul>
 * <li>Generate separator train from concept oil rate and GOR</li>
 * <li>Size compression from gas rate and export pressure</li>
 * <li>Configure water treatment from water cut forecast</li>
 * <li>Create complete process system from concept</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create concept
 * FieldConcept concept = FieldConcept.oilDevelopment("Field A", 150.0, 8, 5000);
 * 
 * // Link to process
 * ConceptToProcessLinker linker = new ConceptToProcessLinker();
 * ProcessSystem process = linker.generateProcessSystem(concept, FidelityLevel.SCREENING);
 * 
 * // Run simulation
 * process.run();
 * 
 * // Get results
 * double powerMW = linker.getTotalPowerMW(process);
 * double heatingMW = linker.getTotalHeatingMW(process);
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ConceptToProcessLinker implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(ConceptToProcessLinker.class);

  /**
   * Fidelity level for process model generation.
   */
  public enum FidelityLevel {
    /** Quick screening - simplified models. */
    SCREENING,
    /** Concept selection - basic equipment. */
    CONCEPT,
    /** Pre-FEED - detailed equipment. */
    PRE_FEED,
    /** FEED - full detailed models. */
    FEED
  }

  /**
   * Process template type.
   */
  public enum ProcessTemplate {
    /** Oil processing with gas export. */
    OIL_PROCESSING,
    /** Gas processing with condensate stabilization. */
    GAS_PROCESSING,
    /** Gas condensate with NGL extraction. */
    GAS_CONDENSATE,
    /** Heavy oil with heating. */
    HEAVY_OIL,
    /** Subsea tieback processing. */
    TIEBACK_PROCESSING
  }

  // ============================================================================
  // DESIGN PARAMETERS
  // ============================================================================

  /** HP separator pressure (bara). */
  private double hpSeparatorPressureBar = 50.0;

  /** LP separator pressure (bara). */
  private double lpSeparatorPressureBar = 5.0;

  /** Export gas pressure (bara). */
  private double exportGasPressureBar = 150.0;

  /** Inlet temperature (C). */
  private double inletTemperatureC = 60.0;

  /** Export oil temperature (C). */
  private double exportOilTemperatureC = 40.0;

  /** Compression efficiency. */
  private double compressionEfficiency = 0.75;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new linker with default parameters.
   */
  public ConceptToProcessLinker() {
    // Default constructor
  }

  // ============================================================================
  // PROCESS GENERATION
  // ============================================================================

  /**
   * Generate a complete process system from a field concept.
   *
   * @param concept the field concept
   * @param fidelity fidelity level
   * @return generated process system
   */
  public ProcessSystem generateProcessSystem(FieldConcept concept, FidelityLevel fidelity) {
    logger.info("Generating process system for {} at {} fidelity", concept.getName(), fidelity);

    ProcessSystem process = new ProcessSystem();
    process.setName(concept.getName() + " Process");

    // Determine template from concept
    ProcessTemplate template = determineTemplate(concept);

    // Create inlet stream
    Stream inlet = createInletStream(concept);
    process.add(inlet);

    // Generate based on template and fidelity
    switch (template) {
      case OIL_PROCESSING:
        generateOilProcessing(process, inlet, concept, fidelity);
        break;
      case GAS_PROCESSING:
        generateGasProcessing(process, inlet, concept, fidelity);
        break;
      case GAS_CONDENSATE:
        generateGasCondensateProcessing(process, inlet, concept, fidelity);
        break;
      default:
        generateOilProcessing(process, inlet, concept, fidelity);
    }

    return process;
  }

  /**
   * Determine process template from concept.
   *
   * @param concept the field concept to analyze
   * @return the appropriate process template
   */
  private ProcessTemplate determineTemplate(FieldConcept concept) {
    if (concept.getReservoir() != null) {
      switch (concept.getReservoir().getFluidType()) {
        case LEAN_GAS:
        case RICH_GAS:
          return ProcessTemplate.GAS_PROCESSING;
        case GAS_CONDENSATE:
          return ProcessTemplate.GAS_CONDENSATE;
        case HEAVY_OIL:
          return ProcessTemplate.HEAVY_OIL;
        default:
          return ProcessTemplate.OIL_PROCESSING;
      }
    }
    return ProcessTemplate.OIL_PROCESSING;
  }

  /**
   * Create inlet stream from concept parameters.
   *
   * @param concept the field concept containing reservoir and production data
   * @return a configured Stream with representative fluid composition
   */
  private Stream createInletStream(FieldConcept concept) {
    // Create representative fluid
    SystemInterface fluid = new SystemSrkEos(273.15 + inletTemperatureC, hpSeparatorPressureBar);

    // Add components based on concept
    if (concept.getReservoir() != null) {
      double gor = concept.getReservoir().getGor();
      double gasFraction = gor / (gor + 159.0); // Approximate gas fraction

      fluid.addComponent("methane", gasFraction * 0.8);
      fluid.addComponent("ethane", gasFraction * 0.1);
      fluid.addComponent("propane", gasFraction * 0.05);
      fluid.addComponent("nC10", (1 - gasFraction) * 0.8);
      fluid.addComponent("nC16", (1 - gasFraction) * 0.15);
      fluid.addComponent("water", 0.05);
    } else {
      // Default composition
      fluid.addComponent("methane", 0.3);
      fluid.addComponent("nC10", 0.6);
      fluid.addComponent("water", 0.1);
    }

    fluid.setMixingRule("classic");

    Stream inlet = new Stream("Inlet", fluid);

    // Set flow rate from concept
    if (concept.getWells() != null) {
      double totalRate =
          concept.getWells().getProducerCount() * concept.getWells().getRatePerWell();
      inlet.setFlowRate(totalRate, "kg/hr");
    } else {
      inlet.setFlowRate(100000.0, "kg/hr");
    }

    return inlet;
  }

  /**
   * Generate oil processing train.
   *
   * @param process the process system to add equipment to
   * @param inlet the inlet stream
   * @param concept the field concept with production parameters
   * @param fidelity the model fidelity level
   */
  private void generateOilProcessing(ProcessSystem process, Stream inlet, FieldConcept concept,
      FidelityLevel fidelity) {
    // HP Separator
    ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP-Separator", inlet);
    process.add(hpSep);

    if (fidelity == FidelityLevel.SCREENING) {
      // Minimal model - just HP separator
      return;
    }

    // LP Separator
    StreamInterface oilFromHp = hpSep.getOilOutStream();
    neqsim.process.equipment.valve.ThrottlingValve toLP =
        new neqsim.process.equipment.valve.ThrottlingValve("To-LP", oilFromHp);
    toLP.setOutletPressure(lpSeparatorPressureBar);
    process.add(toLP);

    ThreePhaseSeparator lpSep = new ThreePhaseSeparator("LP-Separator", toLP.getOutletStream());
    process.add(lpSep);

    // HP Compression
    StreamInterface hpGas = hpSep.getGasOutStream();
    Cooler hpScrubCooler = new Cooler("HP-Scrub-Cooler", hpGas);
    hpScrubCooler.setOutTemperature(273.15 + 30.0);
    process.add(hpScrubCooler);

    Compressor exportCompressor =
        new Compressor("Export-Compressor", hpScrubCooler.getOutletStream());
    exportCompressor.setOutletPressure(exportGasPressureBar);
    exportCompressor.setIsentropicEfficiency(compressionEfficiency);
    process.add(exportCompressor);

    Cooler exportCooler = new Cooler("Export-Cooler", exportCompressor.getOutletStream());
    exportCooler.setOutTemperature(273.15 + 40.0);
    process.add(exportCooler);

    if (fidelity.ordinal() >= FidelityLevel.PRE_FEED.ordinal()) {
      // Add LP compression
      StreamInterface lpGas = lpSep.getGasOutStream();
      Compressor lpCompressor = new Compressor("LP-Compressor", lpGas);
      lpCompressor.setOutletPressure(hpSeparatorPressureBar);
      lpCompressor.setIsentropicEfficiency(compressionEfficiency);
      process.add(lpCompressor);

      // Oil export pump
      StreamInterface oilExport = lpSep.getOilOutStream();
      Pump oilPump = new Pump("Oil-Export-Pump", oilExport);
      oilPump.setOutletPressure(30.0); // Export pressure
      process.add(oilPump);
    }
  }

  /**
   * Generate gas processing train.
   *
   * @param process the process system to add equipment to
   * @param inlet the inlet stream
   * @param concept the field concept with production parameters
   * @param fidelity the model fidelity level
   */
  private void generateGasProcessing(ProcessSystem process, StreamInterface inlet,
      FieldConcept concept, FidelityLevel fidelity) {
    // Inlet separator (slug catcher)
    ThreePhaseSeparator slugCatcher = new ThreePhaseSeparator("Slug-Catcher", inlet);
    process.add(slugCatcher);

    // Gas compression
    StreamInterface gas = slugCatcher.getGasOutStream();

    Cooler inletCooler = new Cooler("Inlet-Cooler", gas);
    inletCooler.setOutTemperature(273.15 + 25.0);
    process.add(inletCooler);

    // Dehydration represented as cooler (simplified)
    Cooler dehydration = new Cooler("Dehydration", inletCooler.getOutletStream());
    dehydration.setOutTemperature(273.15 + 20.0);
    process.add(dehydration);

    // Export compression
    Compressor exportComp = new Compressor("Export-Compressor", dehydration.getOutletStream());
    exportComp.setOutletPressure(exportGasPressureBar);
    exportComp.setIsentropicEfficiency(compressionEfficiency);
    process.add(exportComp);

    Cooler exportCooler = new Cooler("Export-Cooler", exportComp.getOutletStream());
    exportCooler.setOutTemperature(273.15 + 40.0);
    process.add(exportCooler);
  }

  /**
   * Generate gas condensate processing train.
   *
   * @param process the process system to add equipment to
   * @param inlet the inlet stream
   * @param concept the field concept with production parameters
   * @param fidelity the model fidelity level
   */
  private void generateGasCondensateProcessing(ProcessSystem process, Stream inlet,
      FieldConcept concept, FidelityLevel fidelity) {
    // HP Separator
    ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP-Separator", inlet);
    process.add(hpSep);

    // Condensate stabilization
    StreamInterface condensate = hpSep.getOilOutStream();
    Heater stabHeater = new Heater("Stabilizer-Heater", condensate);
    stabHeater.setOutTemperature(273.15 + 80.0);
    process.add(stabHeater);

    neqsim.process.equipment.valve.ThrottlingValve stabValve =
        new neqsim.process.equipment.valve.ThrottlingValve("Stab-Valve",
            stabHeater.getOutletStream());
    stabValve.setOutletPressure(lpSeparatorPressureBar);
    process.add(stabValve);

    ThreePhaseSeparator stabilizer =
        new ThreePhaseSeparator("Stabilizer", stabValve.getOutletStream());
    process.add(stabilizer);

    // Gas processing similar to gas field
    generateGasProcessing(process, hpSep.getGasOutStream(), concept, fidelity);
  }

  // ============================================================================
  // UTILITY CALCULATION METHODS
  // ============================================================================

  /**
   * Calculate total power consumption from a process system.
   *
   * @param process the process system
   * @return total power in MW
   */
  public double getTotalPowerMW(ProcessSystem process) {
    double totalPower = 0.0;

    for (int i = 0; i < process.size(); i++) {
      Object unit = process.getUnitOperations().get(i);
      if (unit instanceof Compressor) {
        totalPower += Math.abs(((Compressor) unit).getPower("MW"));
      } else if (unit instanceof Pump) {
        totalPower += Math.abs(((Pump) unit).getPower("MW"));
      }
    }

    return totalPower;
  }

  /**
   * Calculate total heating duty from a process system.
   *
   * @param process the process system
   * @return total heating in MW
   */
  public double getTotalHeatingMW(ProcessSystem process) {
    double totalHeating = 0.0;

    for (int i = 0; i < process.size(); i++) {
      Object unit = process.getUnitOperations().get(i);
      if (unit instanceof Heater) {
        double duty = ((Heater) unit).getDuty();
        if (duty > 0) {
          totalHeating += duty / 1e6; // Convert to MW
        }
      }
    }

    return totalHeating;
  }

  /**
   * Calculate total cooling duty from a process system.
   *
   * @param process the process system
   * @return total cooling in MW
   */
  public double getTotalCoolingMW(ProcessSystem process) {
    double totalCooling = 0.0;

    for (int i = 0; i < process.size(); i++) {
      Object unit = process.getUnitOperations().get(i);
      if (unit instanceof Cooler) {
        double duty = ((Cooler) unit).getDuty();
        totalCooling += Math.abs(duty) / 1e6; // Convert to MW
      }
    }

    return totalCooling;
  }

  /**
   * Generate a summary of process utilities.
   *
   * @param process the process system
   * @return formatted summary string
   */
  public String getUtilitySummary(ProcessSystem process) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== UTILITY SUMMARY ===\n\n");

    double power = getTotalPowerMW(process);
    double heating = getTotalHeatingMW(process);
    double cooling = getTotalCoolingMW(process);

    sb.append(String.format("Power consumption:  %.2f MW\n", power));
    sb.append(String.format("Heating duty:       %.2f MW\n", heating));
    sb.append(String.format("Cooling duty:       %.2f MW\n", cooling));

    // Estimate CO2 emissions (50 kg CO2/GJ for gas turbine)
    double co2Tonnes = power * 24 * 0.05 * 3.6; // tonnes/day
    sb.append(String.format("Est. CO2 emissions: %.1f tonnes/day\n", co2Tonnes));

    return sb.toString();
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /**
   * Set HP separator pressure.
   *
   * @param pressure pressure in bara
   */
  public void setHpSeparatorPressure(double pressure) {
    this.hpSeparatorPressureBar = pressure;
  }

  /**
   * Set LP separator pressure.
   *
   * @param pressure pressure in bara
   */
  public void setLpSeparatorPressure(double pressure) {
    this.lpSeparatorPressureBar = pressure;
  }

  /**
   * Set export gas pressure.
   *
   * @param pressure pressure in bara
   */
  public void setExportGasPressure(double pressure) {
    this.exportGasPressureBar = pressure;
  }

  /**
   * Set inlet temperature.
   *
   * @param temperatureC temperature in Celsius
   */
  public void setInletTemperature(double temperatureC) {
    this.inletTemperatureC = temperatureC;
  }

  /**
   * Set compression efficiency.
   *
   * @param efficiency efficiency (0-1)
   */
  public void setCompressionEfficiency(double efficiency) {
    this.compressionEfficiency = efficiency;
  }
}
