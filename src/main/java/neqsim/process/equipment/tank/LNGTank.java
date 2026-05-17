package neqsim.process.equipment.tank;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * LNG storage tank model with boil-off gas (BOG) generation.
 *
 * <p>
 * Models an LNG storage tank (membrane or Moss sphere type) with heat ingress
 * through insulation,
 * generating a boil-off gas (BOG) stream. The BOG rate depends on tank
 * geometry, insulation
 * properties, ambient temperature, and LNG composition.
 * </p>
 *
 * <p>
 * The model calculates the steady-state boil-off rate as:
 * </p>
 *
 * <pre>
 *   Q_ingress = U * A * (T_ambient - T_LNG)
 *   BOG_rate  = Q_ingress / latent_heat_of_vaporisation
 * </pre>
 *
 * <p>
 * Typical boil-off rates are 0.05 - 0.15 %/day of total LNG volume for modern
 * insulated tanks.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGTank extends Tank {
  private static final long serialVersionUID = 1003;
  private static final Logger logger = LogManager.getLogger(LNGTank.class);

  /**
   * Insulation type enumeration.
   */
  public enum InsulationType {
    /** Membrane tank (GTT Mark III or NO96). */
    MEMBRANE,
    /** Moss spherical tank. */
    MOSS,
    /** SPB prismatic tank. */
    PRISMATIC
  }

  /** Tank insulation type. */
  private InsulationType insulationType = InsulationType.MEMBRANE;

  /** Overall heat transfer coefficient through insulation (W/m2/K). */
  private double overallHeatTransferCoeff = 0.04;

  /** Tank outer surface area (m2). */
  private double tankSurfaceArea = 10000.0;

  /** Ambient temperature (K). */
  private double ambientTemperature = 273.15 + 25.0;

  /** LNG storage temperature (K). Default -162 C. */
  private double lngTemperature = 273.15 - 162.0;

  /** LNG storage pressure (bara). */
  private double storagePressure = 1.1;

  /** Calculated heat ingress (W). */
  private double heatIngress = 0.0;

  /** Calculated boil-off rate (%/day of LNG mass). */
  private double boilOffRatePctPerDay = 0.0;

  /** Calculated BOG mass flow rate (kg/hr). */
  private double bogMassFlowRate = 0.0;

  /** Total LNG inventory (kg). */
  private double lngInventory = 50000000.0;

  /** BOG outlet stream. */
  private StreamInterface bogStream;

  /** LNG liquid outlet stream. */
  private StreamInterface lngProductStream;

  /** Latent heat of vaporisation of LNG (J/kg). */
  private double latentHeat = 510000.0;

  /**
   * Constructor for LNGTank.
   *
   * @param name name of the LNG tank
   */
  public LNGTank(String name) {
    super(name);
  }

  /**
   * Constructor for LNGTank with an inlet LNG stream.
   *
   * @param name        name of the LNG tank
   * @param inletStream inlet LNG stream
   */
  public LNGTank(String name, StreamInterface inletStream) {
    super(name);
    addStream(inletStream);
  }

  /**
   * Set the insulation type.
   *
   * @param type insulation type (MEMBRANE, MOSS, or PRISMATIC)
   */
  public void setInsulationType(InsulationType type) {
    this.insulationType = type;
    switch (type) {
      case MEMBRANE:
        overallHeatTransferCoeff = 0.04;
        break;
      case MOSS:
        overallHeatTransferCoeff = 0.05;
        break;
      case PRISMATIC:
        overallHeatTransferCoeff = 0.045;
        break;
      default:
        overallHeatTransferCoeff = 0.04;
        break;
    }
  }

  /**
   * Get the insulation type.
   *
   * @return insulation type
   */
  public InsulationType getInsulationType() {
    return insulationType;
  }

  /**
   * Set the overall heat transfer coefficient through insulation.
   *
   * @param u heat transfer coefficient (W/m2/K)
   */
  public void setOverallHeatTransferCoefficient(double u) {
    this.overallHeatTransferCoeff = u;
  }

  /**
   * Get the overall heat transfer coefficient.
   *
   * @return heat transfer coefficient (W/m2/K)
   */
  public double getOverallHeatTransferCoefficient() {
    return overallHeatTransferCoeff;
  }

  /**
   * Set the ambient temperature surrounding the tank.
   *
   * @param temperature ambient temperature value
   * @param unit        temperature unit ("K", "C")
   */
  public void setAmbientTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.ambientTemperature = temperature + 273.15;
    } else {
      this.ambientTemperature = temperature;
    }
  }

  /**
   * Get the ambient temperature.
   *
   * @return ambient temperature (K)
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Set the tank outer surface area.
   *
   * @param area surface area (m2)
   */
  public void setTankSurfaceArea(double area) {
    this.tankSurfaceArea = area;
  }

  /**
   * Get the tank outer surface area.
   *
   * @return surface area (m2)
   */
  public double getTankSurfaceArea() {
    return tankSurfaceArea;
  }

  /**
   * Set the total LNG inventory in the tank.
   *
   * @param inventory LNG mass (kg)
   */
  public void setLNGInventory(double inventory) {
    this.lngInventory = inventory;
  }

  /**
   * Get the total LNG inventory.
   *
   * @return LNG mass (kg)
   */
  public double getLNGInventory() {
    return lngInventory;
  }

  /**
   * Set the LNG storage pressure.
   *
   * @param pressure storage pressure (bara)
   */
  public void setStoragePressure(double pressure) {
    this.storagePressure = pressure;
  }

  /**
   * Get the LNG storage pressure.
   *
   * @return storage pressure (bara)
   */
  public double getStoragePressure() {
    return storagePressure;
  }

  /**
   * Get the heat ingress into the tank.
   *
   * @return heat ingress (W)
   */
  public double getHeatIngress() {
    return heatIngress;
  }

  /**
   * Get the boil-off rate as a percentage of LNG mass per day.
   *
   * @return boil-off rate (%/day)
   */
  public double getBoilOffRatePctPerDay() {
    return boilOffRatePctPerDay;
  }

  /**
   * Get the BOG mass flow rate.
   *
   * @return BOG mass flow rate (kg/hr)
   */
  public double getBOGMassFlowRate() {
    return bogMassFlowRate;
  }

  /**
   * Get the BOG outlet stream.
   *
   * @return BOG stream
   */
  public StreamInterface getBOGStream() {
    return bogStream;
  }

  /**
   * Get the LNG product (liquid) outlet stream.
   *
   * @return LNG product stream
   */
  public StreamInterface getLNGProductStream() {
    return lngProductStream;
  }

  /** {@inheritDoc} */
  @Override
  public java.util.List<StreamInterface> getOutletStreams() {
    java.util.List<StreamInterface> out = new java.util.ArrayList<>();
    if (bogStream != null) {
      out.add(bogStream);
    }
    if (lngProductStream != null) {
      out.add(lngProductStream);
    }
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Run the parent Tank logic first
    super.run(id);

    // Get the inlet fluid from the mixer
    SystemInterface inletFluid = inletStreamMixer.getOutletStream().getThermoSystem();
    if (inletFluid == null) {
      logger.warn("LNG Tank '" + getName() + "': no inlet fluid available");
      setCalculationIdentifier(id);
      return;
    }
    lngTemperature = inletFluid.getTemperature("K");

    // Calculate heat ingress: Q = U * A * (T_amb - T_LNG)
    heatIngress = overallHeatTransferCoeff * tankSurfaceArea * (ambientTemperature - lngTemperature);
    if (heatIngress < 0) {
      heatIngress = 0.0;
    }

    // Calculate latent heat from thermodynamic system
    try {
      SystemInterface flashSystem = inletFluid.clone();
      flashSystem.setPressure(storagePressure);
      flashSystem.setTemperature(lngTemperature);
      ThermodynamicOperations ops = new ThermodynamicOperations(flashSystem);
      ops.TPflash();
      flashSystem.initProperties();

      if (flashSystem.getNumberOfPhases() > 1 && flashSystem.hasPhaseType("gas")
          && flashSystem.hasPhaseType("oil")) {
        double hGas = flashSystem.getPhase("gas").getEnthalpy()
            / flashSystem.getPhase("gas").getNumberOfMolesInPhase()
            / flashSystem.getPhase("gas").getMolarMass("kg/mol");
        double hLiq = flashSystem.getPhase("oil").getEnthalpy()
            / flashSystem.getPhase("oil").getNumberOfMolesInPhase()
            / flashSystem.getPhase("oil").getMolarMass("kg/mol");
        double calcLatentHeat = Math.abs(hGas - hLiq);
        if (calcLatentHeat > 100.0) {
          latentHeat = calcLatentHeat;
        }
      }
    } catch (Exception ex) {
      logger.warn("Failed to calculate latent heat, using default: " + latentHeat, ex);
    }

    // Calculate BOG rates
    if (latentHeat > 0) {
      bogMassFlowRate = heatIngress / latentHeat * 3600.0; // W / (J/kg) * 3600 = kg/hr
    }
    if (lngInventory > 0) {
      boilOffRatePctPerDay = bogMassFlowRate * 24.0 / lngInventory * 100.0;
    }

    // Create BOG and LNG product streams from TP flash at storage conditions
    try {
      SystemInterface bogSystem = inletFluid.clone();
      bogSystem.setPressure(storagePressure);
      bogSystem.setTemperature(lngTemperature);
      ThermodynamicOperations bogOps = new ThermodynamicOperations(bogSystem);
      bogOps.TPflash();

      if (bogSystem.hasPhaseType("gas")) {
        SystemInterface gasPhase = bogSystem.phaseToSystem("gas");
        bogStream = new Stream(getName() + "_BOG", gasPhase);
        bogStream.setFlowRate(bogMassFlowRate, "kg/hr");
        bogStream.run(id);
      }

      if (bogSystem.hasPhaseType("oil")) {
        SystemInterface liqPhase = bogSystem.phaseToSystem("oil");
        lngProductStream = new Stream(getName() + "_LNG", liqPhase);
        lngProductStream.run(id);
      }
    } catch (Exception ex) {
      logger.warn("Failed to create BOG/LNG streams", ex);
    }

    logger.info(String.format("LNG Tank '%s': Q_ingress=%.1f kW, BOG=%.1f kg/hr (%.4f %%/day)",
        getName(), heatIngress / 1000.0, bogMassFlowRate, boilOffRatePctPerDay));

    setCalculationIdentifier(id);
  }
}
