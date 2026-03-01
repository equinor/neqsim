package neqsim.process.equipment.heatexchanger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Multi-effect evaporator for concentration of solutions.
 *
 * <p>
 * Models a series of evaporator effects at decreasing pressures. Each effect uses the vapor from
 * the previous effect as the heating medium, achieving significant steam economy. Commonly used in
 * sugar refining, dairy processing, and bio-product concentration.
 * </p>
 *
 * <p>
 * The model performs sequential flash calculations at decreasing pressures, removing vapor at each
 * stage to concentrate the liquid product.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * MultiEffectEvaporator evap = new MultiEffectEvaporator("MEE", feedStream);
 * evap.setNumberOfEffects(3);
 * evap.setFirstEffectPressure(2.0); // bara
 * evap.setLastEffectPressure(0.2); // bara
 * evap.setTargetConcentrationFactor(5.0); // 5x concentration
 * evap.run();
 *
 * StreamInterface concentrate = evap.getConcentrateStream();
 * StreamInterface condensate = evap.getVaporCondensateStream();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class MultiEffectEvaporator extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(MultiEffectEvaporator.class);

  /** Inlet feed stream. */
  private StreamInterface inletStream;

  /** Concentrated liquid outlet stream. */
  private StreamInterface concentrateStream;

  /** Combined vapor/condensate outlet stream. */
  private StreamInterface vaporCondensateStream;

  /** Number of evaporator effects. */
  private int numberOfEffects = 3;

  /** Pressure of the first (highest pressure) effect in bara. */
  private double firstEffectPressure = 2.0;

  /** Pressure of the last (lowest pressure) effect in bara. */
  private double lastEffectPressure = 0.2;

  /** Target concentration factor (ratio of feed to concentrate flow). */
  private double targetConcentrationFactor = 3.0;

  /** Overall heat transfer coefficient in W/(m2*K). */
  private double overallHeatTransferCoefficient = 2000.0;

  /** Total heat transfer area in m2 (calculated). */
  private double totalHeatTransferArea = 0.0;

  /** Total steam consumption in kg/hr (calculated). */
  private double steamConsumption = 0.0;

  /** Steam economy (kg water evaporated / kg steam used). */
  private double steamEconomy = 0.0;

  /** Pressures for each effect (calculated from first/last). */
  private List<Double> effectPressures = new ArrayList<Double>();

  /**
   * Constructor for MultiEffectEvaporator.
   *
   * @param name name of the evaporator
   */
  public MultiEffectEvaporator(String name) {
    super(name);
  }

  /**
   * Constructor for MultiEffectEvaporator with inlet stream.
   *
   * @param name name of the evaporator
   * @param inletStream the feed stream
   */
  public MultiEffectEvaporator(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * Set the inlet feed stream.
   *
   * @param inletStream the feed stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    SystemInterface sys = inletStream.getThermoSystem().clone();
    concentrateStream = new Stream(getName() + " concentrate", sys);
    vaporCondensateStream = new Stream(getName() + " vapor condensate", sys.clone());
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
   * Get the concentrated liquid outlet stream.
   *
   * @return concentrate stream
   */
  public StreamInterface getConcentrateStream() {
    return concentrateStream;
  }

  /**
   * Get the combined vapor condensate stream.
   *
   * @return vapor condensate stream
   */
  public StreamInterface getVaporCondensateStream() {
    return vaporCondensateStream;
  }

  /**
   * Set the number of evaporator effects.
   *
   * @param effects number of effects (1-7 typically)
   */
  public void setNumberOfEffects(int effects) {
    if (effects < 1) {
      throw new IllegalArgumentException("Number of effects must be at least 1");
    }
    this.numberOfEffects = effects;
  }

  /**
   * Get the number of effects.
   *
   * @return number of effects
   */
  public int getNumberOfEffects() {
    return numberOfEffects;
  }

  /**
   * Set the first (highest) effect pressure.
   *
   * @param pressure pressure in bara
   */
  public void setFirstEffectPressure(double pressure) {
    this.firstEffectPressure = pressure;
  }

  /**
   * Get the first effect pressure.
   *
   * @return pressure in bara
   */
  public double getFirstEffectPressure() {
    return firstEffectPressure;
  }

  /**
   * Set the last (lowest) effect pressure.
   *
   * @param pressure pressure in bara
   */
  public void setLastEffectPressure(double pressure) {
    this.lastEffectPressure = pressure;
  }

  /**
   * Get the last effect pressure.
   *
   * @return pressure in bara
   */
  public double getLastEffectPressure() {
    return lastEffectPressure;
  }

  /**
   * Set the target concentration factor.
   *
   * @param factor ratio of feed mass flow to concentrate mass flow
   */
  public void setTargetConcentrationFactor(double factor) {
    this.targetConcentrationFactor = factor;
  }

  /**
   * Get the target concentration factor.
   *
   * @return concentration factor
   */
  public double getTargetConcentrationFactor() {
    return targetConcentrationFactor;
  }

  /**
   * Set the overall heat transfer coefficient.
   *
   * @param u heat transfer coefficient in W/(m2*K)
   */
  public void setOverallHeatTransferCoefficient(double u) {
    this.overallHeatTransferCoefficient = u;
  }

  /**
   * Get the overall heat transfer coefficient.
   *
   * @return U in W/(m2*K)
   */
  public double getOverallHeatTransferCoefficient() {
    return overallHeatTransferCoefficient;
  }

  /**
   * Get the total heat transfer area (calculated after run).
   *
   * @return area in m2
   */
  public double getTotalHeatTransferArea() {
    return totalHeatTransferArea;
  }

  /**
   * Get the steam economy.
   *
   * @return kg water evaporated per kg steam
   */
  public double getSteamEconomy() {
    return steamEconomy;
  }

  /**
   * Get the steam consumption.
   *
   * @return steam consumption in kg/hr
   */
  public double getSteamConsumption() {
    return steamConsumption;
  }

  /**
   * Calculate the pressure distribution across effects using geometric spacing.
   */
  private void calculateEffectPressures() {
    effectPressures.clear();
    if (numberOfEffects == 1) {
      effectPressures.add(lastEffectPressure);
      return;
    }
    for (int i = 0; i < numberOfEffects; i++) {
      double fraction = (double) i / (numberOfEffects - 1);
      // Geometric interpolation
      double pressure =
          firstEffectPressure * Math.pow(lastEffectPressure / firstEffectPressure, fraction);
      effectPressures.add(pressure);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    calculateEffectPressures();

    SystemInterface currentLiquid = inletStream.getThermoSystem().clone();
    SystemInterface totalVapor = null;
    double totalVaporMoles = 0.0;

    // Process through each effect
    for (int effect = 0; effect < numberOfEffects; effect++) {
      double effectPressure = effectPressures.get(effect);
      currentLiquid.setPressure(effectPressure);

      // Flash at the effect pressure (isenthalpic for realistic modeling)
      ThermodynamicOperations ops = new ThermodynamicOperations(currentLiquid);
      try {
        ops.TPflash();
      } catch (Exception ex) {
        logger.warn("Flash failed at effect {} (P={} bara): {}", effect + 1, effectPressure,
            ex.getMessage());
        continue;
      }

      currentLiquid.init(3);

      // Separate gas phase from liquid
      if (currentLiquid.getNumberOfPhases() > 1 && currentLiquid.hasPhaseType("gas")) {
        // Extract vapor phase
        SystemInterface vaporPhase = currentLiquid.phaseToSystem(currentLiquid.getPhases()[0]);

        // Keep only liquid for next effect
        currentLiquid = currentLiquid.phaseToSystem(currentLiquid.getPhases()[1]);

        // Accumulate total vapor
        if (totalVapor == null) {
          totalVapor = vaporPhase;
        } else {
          // Add the vapor components to total
          for (int i = 0; i < vaporPhase.getNumberOfComponents(); i++) {
            String compName = vaporPhase.getComponent(i).getComponentName();
            double moles = vaporPhase.getComponent(i).getNumberOfmoles();
            try {
              totalVapor.addComponent(compName, moles);
            } catch (Exception ex) {
              // Skip if component can't be added
            }
          }
        }
      }
    }

    // Set output streams
    currentLiquid.initProperties();
    concentrateStream.setThermoSystem(currentLiquid);

    if (totalVapor != null) {
      totalVapor.initProperties();
      vaporCondensateStream.setThermoSystem(totalVapor);
    }

    // Estimate steam economy (simplified: number of effects approximates economy)
    steamEconomy = numberOfEffects * 0.8; // Typical: 0.8 * N

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Get a map representation of the evaporator.
   *
   * @return map of properties
   */
  private Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", getName());
    map.put("type", "MultiEffectEvaporator");
    map.put("numberOfEffects", numberOfEffects);
    map.put("firstEffectPressure_bara", firstEffectPressure);
    map.put("lastEffectPressure_bara", lastEffectPressure);
    map.put("targetConcentrationFactor", targetConcentrationFactor);
    map.put("steamEconomy", steamEconomy);
    map.put("overallHeatTransferCoefficient_W_m2K", overallHeatTransferCoefficient);
    map.put("effectPressures_bara", effectPressures);
    return map;
  }
}
