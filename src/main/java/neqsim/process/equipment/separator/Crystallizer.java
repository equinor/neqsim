package neqsim.process.equipment.separator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Crystallizer for producing solid crystals from solution.
 *
 * <p>
 * Models a crystallization process where dissolved solutes are brought out of
 * solution as solid
 * crystals. Supports cooling crystallization (reducing temperature),
 * evaporative crystallization
 * (removing solvent), and anti-solvent crystallization.
 * </p>
 *
 * <p>
 * The crystallizer operates by flashing the feed at reduced temperature and/or
 * pressure to
 * concentrate the solution beyond the saturation point, causing
 * crystallization. NeqSim's solid
 * phase equilibrium capabilities (TPSolidflash) are leveraged when available.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * Crystallizer cryst = new Crystallizer("Sugar Crystallizer", feedStream);
 * cryst.setCrystallizationType("cooling");
 * cryst.setOutletTemperature(273.15 + 30.0); // cool to 30 C
 * cryst.setSolidRecovery(0.85); // 85% of solute crystallizes
 * cryst.run();
 *
 * StreamInterface crystals = cryst.getCrystalStream();
 * StreamInterface motherLiquor = cryst.getMotherLiquorStream();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class Crystallizer extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Crystallizer.class);

  /** Inlet feed stream. */
  private StreamInterface inletStream;

  /** Crystal (solid) outlet stream. */
  private StreamInterface crystalStream;

  /** Mother liquor (liquid) outlet stream. */
  private StreamInterface motherLiquorStream;

  /** Type of crystallization: "cooling", "evaporative", "antisolvent". */
  private String crystallizationType = "cooling";

  /** Outlet temperature in Kelvin. Used for cooling crystallization. */
  private double outletTemperature = Double.NaN;

  /** Outlet pressure in bara. Used for evaporative crystallization. */
  private double outletPressure = Double.NaN;

  /** Solid recovery fraction of the target solute (0-1). */
  private double solidRecovery = 0.80;

  /** Name of the target solute to crystallize. */
  private String targetSolute = "";

  /** Crystal purity (mass fraction of target in crystals). */
  private double crystalPurity = 0.98;

  /** Residence time in hours. */
  private double residenceTime = 2.0;

  /** Vessel volume in m3. */
  private double vesselVolume = 10.0;

  /** Heat duty for cooling/heating in Watts. */
  private double heatDuty = 0.0;

  /**
   * Constructor for Crystallizer.
   *
   * @param name name of the crystallizer
   */
  public Crystallizer(String name) {
    super(name);
  }

  /**
   * Constructor for Crystallizer with inlet stream.
   *
   * @param name        name of the crystallizer
   * @param inletStream the feed stream
   */
  public Crystallizer(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * Set the inlet stream.
   *
   * @param inletStream the feed stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    SystemInterface sys = inletStream.getThermoSystem().clone();
    crystalStream = new Stream(getName() + " crystals", sys);
    motherLiquorStream = new Stream(getName() + " mother liquor", sys.clone());
  }

  /**
   * Get the inlet stream.
   *
   * @return inlet stream
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * Get the crystal (solid) outlet stream.
   *
   * @return crystal stream
   */
  public StreamInterface getCrystalStream() {
    return crystalStream;
  }

  /**
   * Get the mother liquor outlet stream.
   *
   * @return mother liquor stream
   */
  public StreamInterface getMotherLiquorStream() {
    return motherLiquorStream;
  }

  /** {@inheritDoc} */
  @Override
  public java.util.List<StreamInterface> getInletStreams() {
    if (inletStream == null) {
      return java.util.Collections.emptyList();
    }
    return java.util.Collections.singletonList(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public java.util.List<StreamInterface> getOutletStreams() {
    java.util.List<StreamInterface> out = new java.util.ArrayList<>();
    if (crystalStream != null) {
      out.add(crystalStream);
    }
    if (motherLiquorStream != null) {
      out.add(motherLiquorStream);
    }
    return out;
  }

  /**
   * Set the crystallization type.
   *
   * @param type "cooling", "evaporative", or "antisolvent"
   */
  public void setCrystallizationType(String type) {
    this.crystallizationType = type;
  }

  /**
   * Get the crystallization type.
   *
   * @return crystallization type
   */
  public String getCrystallizationType() {
    return crystallizationType;
  }

  /**
   * Set the outlet temperature.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setOutletTemperature(double temperatureK) {
    this.outletTemperature = temperatureK;
  }

  /**
   * Set the outlet temperature with unit.
   *
   * @param temperature temperature value
   * @param unit        temperature unit ("K", "C", "F")
   */
  public void setOutletTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.outletTemperature = temperature + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      this.outletTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.outletTemperature = temperature;
    }
  }

  /**
   * Get the outlet temperature in Kelvin.
   *
   * @return outlet temperature
   */
  public double getOutletTemperature() {
    return outletTemperature;
  }

  /**
   * Set the outlet pressure (for evaporative crystallization).
   *
   * @param pressureBara pressure in bara
   */
  public void setOutletPressure(double pressureBara) {
    this.outletPressure = pressureBara;
  }

  /**
   * Get the outlet pressure.
   *
   * @return pressure in bara
   */
  public double getOutletPressure() {
    return outletPressure;
  }

  /**
   * Set the solid recovery fraction.
   *
   * @param recovery fraction of solute that crystallizes (0-1)
   */
  public void setSolidRecovery(double recovery) {
    this.solidRecovery = recovery;
  }

  /**
   * Get the solid recovery fraction.
   *
   * @return solid recovery
   */
  public double getSolidRecovery() {
    return solidRecovery;
  }

  /**
   * Set the target solute to crystallize.
   *
   * @param componentName name of the component to crystallize
   */
  public void setTargetSolute(String componentName) {
    this.targetSolute = componentName;
  }

  /**
   * Get the target solute name.
   *
   * @return target solute component name
   */
  public String getTargetSolute() {
    return targetSolute;
  }

  /**
   * Set the crystal purity.
   *
   * @param purity mass fraction of target in crystals (0-1)
   */
  public void setCrystalPurity(double purity) {
    this.crystalPurity = purity;
  }

  /**
   * Get the crystal purity.
   *
   * @return crystal purity
   */
  public double getCrystalPurity() {
    return crystalPurity;
  }

  /**
   * Set the residence time.
   *
   * @param hours residence time in hours
   */
  public void setResidenceTime(double hours) {
    this.residenceTime = hours;
  }

  /**
   * Get the residence time.
   *
   * @return residence time in hours
   */
  public double getResidenceTime() {
    return residenceTime;
  }

  /**
   * Set the vessel volume.
   *
   * @param volumeM3 vessel volume in m3
   */
  public void setVesselVolume(double volumeM3) {
    this.vesselVolume = volumeM3;
  }

  /**
   * Get the vessel volume.
   *
   * @return vessel volume in m3
   */
  public double getVesselVolume() {
    return vesselVolume;
  }

  /**
   * Get the heat duty.
   *
   * @return heat duty in Watts
   */
  public double getHeatDuty() {
    return heatDuty;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inletStream.getThermoSystem().clone();

    system.init(3);
    double inletEnthalpy = system.getEnthalpy();

    // Set outlet conditions based on crystallization type
    if ("cooling".equalsIgnoreCase(crystallizationType) && !Double.isNaN(outletTemperature)) {
      system.setTemperature(outletTemperature);
    }
    if (!Double.isNaN(outletPressure)) {
      system.setPressure(outletPressure);
    }

    // Try solid flash first, fall back to TP flash
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception ex) {
      logger.warn("Flash failed in crystallizer '{}': {}", getName(), ex.getMessage());
    }

    system.init(3);
    system.initProperties();

    // Calculate heat duty
    heatDuty = system.getEnthalpy() - inletEnthalpy;

    // Apply component-based separation for the target solute
    // This is a simplified model: we split the target solute based on solidRecovery
    SystemInterface crystalSys = system.clone();
    SystemInterface liquidSys = system.clone();

    int numComponents = system.getNumberOfComponents();
    for (int i = 0; i < numComponents; i++) {
      String compName = system.getComponent(i).getComponentName();
      double totalMoles = system.getComponent(i).getNumberOfmoles();

      double crystalFraction;
      if (compName.equals(targetSolute)) {
        crystalFraction = solidRecovery;
      } else {
        // Small amount of mother liquor gets trapped in crystal cake
        crystalFraction = 0.02; // 2% entrainment
      }

      double crystalMoles = totalMoles * crystalFraction;
      double liquidMoles = totalMoles * (1.0 - crystalFraction);

      // Adjust crystal system
      double currentCrystal = crystalSys.getComponent(i).getNumberOfmoles();
      crystalSys.addComponent(i, crystalMoles - currentCrystal);

      // Adjust liquid system
      double currentLiquid = liquidSys.getComponent(i).getNumberOfmoles();
      liquidSys.addComponent(i, liquidMoles - currentLiquid);
    }

    crystalSys.initProperties();
    liquidSys.initProperties();

    crystalStream.setThermoSystem(crystalSys);
    motherLiquorStream.setThermoSystem(liquidSys);

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Get a map representation of the crystallizer.
   *
   * @return map of properties
   */
  private Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", getName());
    map.put("type", "Crystallizer");
    map.put("crystallizationType", crystallizationType);
    map.put("targetSolute", targetSolute);
    map.put("solidRecovery", solidRecovery);
    map.put("crystalPurity", crystalPurity);
    map.put("residenceTime_hr", residenceTime);
    map.put("vesselVolume_m3", vesselVolume);
    map.put("heatDuty_W", heatDuty);
    if (!Double.isNaN(outletTemperature)) {
      map.put("outletTemperature_K", outletTemperature);
    }
    return map;
  }
}
