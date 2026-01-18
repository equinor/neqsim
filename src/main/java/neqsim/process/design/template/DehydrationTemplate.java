package neqsim.process.design.template;

import neqsim.process.design.ProcessBasis;
import neqsim.process.design.ProcessTemplate;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Template for creating TEG (Triethylene Glycol) gas dehydration systems.
 *
 * <p>
 * This template creates a standard TEG dehydration unit consisting of an absorber column, glycol
 * regeneration system, and associated equipment. The design follows industry standards for natural
 * gas dehydration.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>TEG absorber with configurable number of theoretical stages</li>
 * <li>Rich glycol flash drum for hydrocarbon recovery</li>
 * <li>Glycol-glycol heat exchanger for heat recovery</li>
 * <li>Regeneration still with configurable reboiler temperature</li>
 * <li>Lean glycol pump and cooler</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * ProcessBasis basis = new ProcessBasis();
 * basis.setFeedFluid(wetGasFluid);
 * basis.setParameter("tegCirculationRate", 5.0); // m3/hr
 * basis.setParameter("reboilerTemperature", 204.0); // °C
 * basis.setParameter("numberOfStages", 4);
 * 
 * DehydrationTemplate template = new DehydrationTemplate();
 * ProcessSystem dehy = template.create(basis);
 * dehy.run();
 * }</pre>
 *
 * <h2>Water Content Targets</h2>
 * <ul>
 * <li>Pipeline specification: 7 lb/MMscf</li>
 * <li>Cryogenic processing: &lt; 1 ppm</li>
 * <li>LNG feed: &lt; 0.1 ppm</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DehydrationTemplate implements ProcessTemplate {

  /** Default number of theoretical stages in absorber. */
  private static final int DEFAULT_ABSORBER_STAGES = 4;

  /** Default reboiler temperature in Celsius. */
  private static final double DEFAULT_REBOILER_TEMP_C = 204.0;

  /** Default lean TEG purity (mass fraction). */
  private static final double DEFAULT_LEAN_TEG_PURITY = 0.99;

  /** Default TEG circulation rate in m3/hr per MMscfd of gas. */
  private static final double DEFAULT_TEG_RATE_PER_MMSCFD = 3.0;

  /** Default lean glycol temperature in Celsius. */
  private static final double DEFAULT_LEAN_GLYCOL_TEMP_C = 45.0;

  /**
   * Creates a new DehydrationTemplate.
   */
  public DehydrationTemplate() {
    // Default constructor
  }

  /** {@inheritDoc} */
  @Override
  public ProcessSystem create(ProcessBasis basis) {
    ProcessSystem process = new ProcessSystem();

    // Get parameters from basis
    SystemInterface feedFluid = basis.getFeedFluid();
    if (feedFluid == null) {
      throw new IllegalArgumentException("ProcessBasis must have a feed fluid defined");
    }

    double feedPressure = basis.getFeedPressure();
    if (Double.isNaN(feedPressure) || feedPressure <= 0) {
      feedPressure = 70.0; // Default 70 bara
    }

    double feedTemperature = basis.getFeedTemperature();
    if (Double.isNaN(feedTemperature) || feedTemperature <= 0) {
      feedTemperature = 30.0 + 273.15; // Default 30°C
    }

    int absorberStages = (int) basis.getParameter("numberOfStages", DEFAULT_ABSORBER_STAGES);
    double reboilerTemp = basis.getParameter("reboilerTemperature", DEFAULT_REBOILER_TEMP_C);
    double leanGlycolTemp = basis.getParameter("leanGlycolTemperature", DEFAULT_LEAN_GLYCOL_TEMP_C);
    double tegCirculationRate = basis.getParameter("tegCirculationRate", 0);

    // Get flow rate
    double gasFlowRate = basis.getFeedFlowRate();
    if (gasFlowRate <= 0) {
      gasFlowRate = 10000.0; // Default 10000 kg/hr
    }

    // Calculate TEG rate if not specified
    if (tegCirculationRate <= 0) {
      // Estimate based on gas flow rate - typically 3 gal TEG per lb water removed
      tegCirculationRate = gasFlowRate * 0.001 * DEFAULT_TEG_RATE_PER_MMSCFD;
    }

    // Create wet gas feed stream
    SystemInterface wetGas = feedFluid.clone();
    wetGas.setPressure(feedPressure, "bara");
    wetGas.setTemperature(feedTemperature, "K");
    Stream wetGasFeed = new Stream("Wet Gas Feed", wetGas);
    wetGasFeed.setFlowRate(gasFlowRate, "kg/hr");
    process.add(wetGasFeed);

    // Create lean TEG stream
    SystemInterface leanTEG = createTEGFluid(DEFAULT_LEAN_TEG_PURITY, feedPressure);
    leanTEG.setTemperature(leanGlycolTemp + 273.15, "K");
    Stream leanTEGFeed = new Stream("Lean TEG", leanTEG);
    leanTEGFeed.setFlowRate(tegCirculationRate * 1120.0, "kg/hr"); // TEG density ~1120 kg/m3
    process.add(leanTEGFeed);

    // TEG Absorber
    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG Absorber");
    absorber.addGasInStream(wetGasFeed);
    absorber.addSolventInStream(leanTEGFeed);
    absorber.setNumberOfStages(absorberStages);
    process.add(absorber);

    // Rich glycol flash drum
    ThrottlingValve richGlycolValve =
        new ThrottlingValve("Rich Glycol Valve", absorber.getSolventOutStream());
    richGlycolValve.setOutletPressure(5.0); // Flash at 5 bara
    process.add(richGlycolValve);

    Separator flashDrum =
        new Separator("Rich Glycol Flash Drum", richGlycolValve.getOutletStream());
    process.add(flashDrum);

    // Rich glycol heater (simulates heat from lean/rich exchanger)
    // Using separate Heater/Cooler instead of HeatExchanger for template simplicity
    Heater richGlycolHeater = new Heater("Rich Glycol Heater", flashDrum.getLiquidOutStream());
    richGlycolHeater.setOutTemperature(reboilerTemp - 20.0 + 273.15); // Preheat to near reboiler
                                                                      // temp
    process.add(richGlycolHeater);

    // Regeneration still (simplified as heater + separator)
    Heater reboiler = new Heater("Regeneration Reboiler", richGlycolHeater.getOutletStream());
    reboiler.setOutTemperature(reboilerTemp + 273.15);
    process.add(reboiler);

    Separator regenerator = new Separator("Regeneration Still", reboiler.getOutletStream());
    process.add(regenerator);

    // Lean glycol pump
    Pump glycolPump = new Pump("Lean Glycol Pump", regenerator.getLiquidOutStream());
    glycolPump.setOutletPressure(feedPressure + 2.0); // Slight overpressure
    process.add(glycolPump);

    // Lean glycol cooler
    neqsim.process.equipment.heatexchanger.Cooler glycolCooler =
        new neqsim.process.equipment.heatexchanger.Cooler("Lean Glycol Cooler",
            glycolPump.getOutletStream());
    glycolCooler.setOutTemperature(leanGlycolTemp + 273.15);
    process.add(glycolCooler);

    // TEG makeup mixer (for glycol losses)
    // In actual operation, makeup stream would be added here

    return process;
  }

  /**
   * Creates a TEG fluid for the dehydration system.
   *
   * @param purity TEG mass fraction (typically 0.98-0.995)
   * @param pressure system pressure in bara
   * @return TEG fluid system
   */
  private SystemInterface createTEGFluid(double purity, double pressure) {
    // Use SRK for templates - CPA requires careful initialization and compatible fluids
    // For production use with rigorous TEG thermodynamics, use SystemSrkCPAstatoil
    SystemInterface tegFluid = new neqsim.thermo.system.SystemSrkEos(273.15 + 45.0, pressure);
    tegFluid.addComponent("TEG", purity);
    tegFluid.addComponent("water", 1.0 - purity);
    tegFluid.setMixingRule("classic");
    tegFluid.setMultiPhaseCheck(true);
    return tegFluid;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isApplicable(SystemInterface fluid) {
    if (fluid == null) {
      return false;
    }

    try {
      // Check if fluid contains water
      boolean hasWater = false;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        String name = fluid.getComponent(i).getName().toLowerCase();
        if (name.equals("water") || name.equals("h2o")) {
          hasWater = true;
          break;
        }
      }

      if (!hasWater) {
        return false;
      }

      // Check if predominantly gas
      SystemInterface testFluid = fluid.clone();
      testFluid.setTemperature(30.0 + 273.15, "K");
      testFluid.setPressure(70.0, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(testFluid);
      ops.TPflash();

      return testFluid.hasPhaseType("gas");
    } catch (Exception e) {
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String[] getRequiredEquipmentTypes() {
    return new String[] {"SimpleTEGAbsorber", "Separator", "Heater", "Pump", "Cooler",
        "ThrottlingValve"};
  }

  /** {@inheritDoc} */
  @Override
  public String[] getExpectedOutputs() {
    return new String[] {"Dry Gas - Dehydrated gas meeting pipeline specification",
        "Flash Gas - Hydrocarbon-rich gas from flash drum",
        "Water - Produced water from regeneration still", "TEG Losses - Estimated glycol losses"};
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "TEG Gas Dehydration";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "TEG (Triethylene Glycol) gas dehydration system with absorber, "
        + "flash drum, lean/rich heat exchanger, and regeneration still. "
        + "Suitable for pipeline-spec gas dehydration and cryogenic pre-treatment.";
  }

  /**
   * Calculates required TEG circulation rate for target water content.
   *
   * @param gasFlowRate gas flow rate in MMscfd
   * @param inletWaterContent inlet water content in lb/MMscf
   * @param outletWaterContent target outlet water content in lb/MMscf
   * @return required TEG circulation rate in gal/hr
   */
  public static double calculateTEGRate(double gasFlowRate, double inletWaterContent,
      double outletWaterContent) {
    double waterRemoved = (inletWaterContent - outletWaterContent) * gasFlowRate; // lb/day
    double tegRateGalPerDay = waterRemoved * 3.0; // 3 gal TEG per lb water (typical)
    return tegRateGalPerDay / 24.0; // Convert to gal/hr
  }

  /**
   * Estimates equilibrium water content above TEG at given conditions.
   *
   * @param tegPurity TEG mass fraction
   * @param temperature temperature in Celsius
   * @param pressure pressure in bara
   * @return equilibrium water content in lb/MMscf
   */
  public static double estimateEquilibriumWater(double tegPurity, double temperature,
      double pressure) {
    // McKetta-Wehe correlation (simplified)
    // Water content decreases with TEG purity and pressure, increases with temperature
    double dewPoint = -12.5 * Math.log10(1.0 - tegPurity) + 0.7 * temperature - 20.0;
    double waterContent = Math.pow(10, 0.0297 * dewPoint + 0.445) / pressure;
    return Math.max(0.1, waterContent);
  }
}
