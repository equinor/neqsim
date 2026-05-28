package neqsim.process.equipment.reactor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Fired steam-methane-reformer furnace with burner-to-tube heat coupling.
 *
 * <p>
 * This unit couples an existing {@link FurnaceBurner} flue-gas equilibrium calculation to a
 * {@link CatalyticTubeReformer}. The coupling is a screening heat-balance layer: available radiant
 * heat from burner fuel is compared with tube-side reforming duty, and the tube reformer can be
 * rerun at a reduced effective outlet temperature if fuel firing is insufficient. Detailed burner
 * geometry, view factors, and tube-row radiation are intentionally left for future high-fidelity
 * models.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ReformerFurnace extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Process feed to the reformer tubes. */
  private StreamInterface processInletStream;

  /** Fuel stream to the furnace burner. */
  private StreamInterface fuelInletStream;

  /** Combustion air stream to the furnace burner. */
  private StreamInterface airInletStream;

  /** Syngas outlet stream from the reformer tubes. */
  private StreamInterface syngasOutStream;

  /** Flue-gas outlet stream from the burner model. */
  private StreamInterface flueGasOutStream;

  /** Target tube outlet temperature in Kelvin. */
  private double targetReformingTemperatureK = 1123.15;

  /** Tube-side pressure drop in bar. */
  private double tubePressureDropBar = 1.0;

  /** Effective heated tube length in metres. */
  private double tubeLengthM = 10.0;

  /** Tube inside diameter in metres. */
  private double tubeInnerDiameterM = 0.10;

  /** Number of reformer tubes. */
  private int numberOfTubes = 100;

  /** Tube-side heat-transfer coefficient in W/(m2 K). */
  private double tubeHeatTransferCoefficient = 120.0;

  /** Maximum tube-wall temperature in Kelvin. */
  private double maxTubeWallTemperatureK = 1173.15;

  /** Fraction of burner heat released as useful radiant tube heat. */
  private double radiantEfficiency = 0.55;

  /** Overall furnace heat efficiency after stack and casing losses. */
  private double furnaceEfficiency = 0.90;

  /** Excess air fraction for the burner. */
  private double excessAirFraction = 0.15;

  /** Whether to rerun tubes at lower temperature if firing is insufficient. */
  private boolean enforceHeatBalance = true;

  /** Internal tube reformer from latest run. */
  private transient CatalyticTubeReformer tubeReformer;

  /** Internal burner from latest run. */
  private transient FurnaceBurner burner;

  /** Available useful radiant heat in kW. */
  private double availableRadiantHeatKW = 0.0;

  /** Tube-side process heat demand in kW. */
  private double tubeHeatDemandKW = 0.0;

  /** Available heat divided by tube-side heat demand. */
  private double heatBalanceRatio = Double.NaN;

  /** Effective tube outlet temperature used after heat-balance coupling. */
  private double effectiveReformingTemperatureK = Double.NaN;

  /**
   * Creates a reformer furnace.
   *
   * @param name equipment name
   */
  public ReformerFurnace(String name) {
    super(name);
  }

  /**
   * Creates a reformer furnace with a process inlet stream.
   *
   * @param name equipment name
   * @param processInletStream tube-side feed stream
   */
  public ReformerFurnace(String name, StreamInterface processInletStream) {
    this(name);
    setProcessInletStream(processInletStream);
  }

  /**
   * Sets the tube-side process inlet.
   *
   * @param stream process feed stream
   */
  public void setProcessInletStream(StreamInterface stream) {
    this.processInletStream = stream;
    if (stream != null) {
      this.syngasOutStream = stream.clone(getName() + " syngas");
    }
  }

  /**
   * Sets the furnace fuel inlet.
   *
   * @param stream fuel stream
   */
  public void setFuelInletStream(StreamInterface stream) {
    this.fuelInletStream = stream;
    if (stream != null) {
      this.flueGasOutStream = stream.clone(getName() + " flue gas");
    }
  }

  /**
   * Sets the combustion air inlet.
   *
   * @param stream air stream
   */
  public void setAirInletStream(StreamInterface stream) {
    this.airInletStream = stream;
  }

  /**
   * Sets target tube outlet temperature.
   *
   * @param temperatureK target temperature in Kelvin
   */
  public void setTargetReformingTemperature(double temperatureK) {
    validatePositive(temperatureK, "temperatureK");
    this.targetReformingTemperatureK = temperatureK;
  }

  /**
   * Sets tube geometry.
   *
   * @param lengthM heated tube length in metres
   * @param innerDiameterM tube inside diameter in metres
   * @param tubes number of tubes
   */
  public void setTubeGeometry(double lengthM, double innerDiameterM, int tubes) {
    validatePositive(lengthM, "lengthM");
    validatePositive(innerDiameterM, "innerDiameterM");
    if (tubes < 1) {
      throw new IllegalArgumentException("tubes must be greater than zero");
    }
    this.tubeLengthM = lengthM;
    this.tubeInnerDiameterM = innerDiameterM;
    this.numberOfTubes = tubes;
  }

  /**
   * Sets tube-side pressure drop.
   *
   * @param pressureDropBar pressure drop in bar
   */
  public void setTubePressureDrop(double pressureDropBar) {
    if (!Double.isFinite(pressureDropBar) || pressureDropBar < 0.0) {
      throw new IllegalArgumentException("pressureDropBar must be finite and non-negative");
    }
    this.tubePressureDropBar = pressureDropBar;
  }

  /**
   * Sets the tube-side heat-transfer coefficient.
   *
   * @param coefficient heat-transfer coefficient in W/(m2 K)
   */
  public void setTubeHeatTransferCoefficient(double coefficient) {
    validatePositive(coefficient, "coefficient");
    this.tubeHeatTransferCoefficient = coefficient;
  }

  /**
   * Sets maximum tube-wall temperature.
   *
   * @param temperatureK maximum tube-wall temperature in Kelvin
   */
  public void setMaxTubeWallTemperature(double temperatureK) {
    validatePositive(temperatureK, "temperatureK");
    this.maxTubeWallTemperatureK = temperatureK;
  }

  /**
   * Sets radiant and overall furnace efficiencies.
   *
   * @param radiantEfficiency fraction of burner heat reaching tubes as radiant heat
   * @param furnaceEfficiency overall furnace efficiency
   */
  public void setFurnaceEfficiencies(double radiantEfficiency, double furnaceEfficiency) {
    this.radiantEfficiency = HydrogenProductionUtils.clamp(radiantEfficiency, 0.0, 1.0);
    this.furnaceEfficiency = HydrogenProductionUtils.clamp(furnaceEfficiency, 0.0, 1.0);
  }

  /**
   * Sets burner excess air fraction.
   *
   * @param fraction excess air fraction
   */
  public void setExcessAirFraction(double fraction) {
    if (!Double.isFinite(fraction) || fraction < 0.0) {
      throw new IllegalArgumentException("fraction must be finite and non-negative");
    }
    this.excessAirFraction = fraction;
  }

  /**
   * Enables or disables temperature reduction when firing is insufficient.
   *
   * @param enforce true to enforce heat balance
   */
  public void setEnforceHeatBalance(boolean enforce) {
    this.enforceHeatBalance = enforce;
  }

  /**
   * Gets the syngas outlet stream.
   *
   * @return syngas outlet stream
   */
  public StreamInterface getSyngasOutStream() {
    return syngasOutStream;
  }

  /**
   * Gets the flue-gas outlet stream.
   *
   * @return flue-gas outlet stream
   */
  public StreamInterface getFlueGasOutStream() {
    return flueGasOutStream;
  }

  /**
   * Gets the internal tube reformer.
   *
   * @return tube reformer from the latest run
   */
  public CatalyticTubeReformer getTubeReformer() {
    return tubeReformer;
  }

  /**
   * Gets available useful radiant heat.
   *
   * @return useful radiant heat in kW
   */
  public double getAvailableRadiantHeatKW() {
    return availableRadiantHeatKW;
  }

  /**
   * Gets tube-side heat demand.
   *
   * @return heat demand in kW
   */
  public double getTubeHeatDemandKW() {
    return tubeHeatDemandKW;
  }

  /**
   * Gets heat-balance ratio.
   *
   * @return available heat divided by demanded heat
   */
  public double getHeatBalanceRatio() {
    return heatBalanceRatio;
  }

  /**
   * Gets effective reforming temperature.
   *
   * @return effective reforming temperature in Kelvin
   */
  public double getEffectiveReformingTemperature() {
    return effectiveReformingTemperatureK;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    validateStreams();
    burner = new FurnaceBurner(getName() + " burner");
    burner.setFuelInlet(fuelInletStream);
    burner.setAirInlet(airInletStream);
    burner.setExcessAirFraction(excessAirFraction);
    burner.run(id);
    flueGasOutStream.setThermoSystem(burner.getOutletStream().getThermoSystem().clone());
    flueGasOutStream.run(id);
    availableRadiantHeatKW = burner.getHeatReleasekW() * radiantEfficiency * furnaceEfficiency;

    tubeReformer = createTubeReformer(targetReformingTemperatureK);
    tubeReformer.run(id);
    tubeHeatDemandKW = tubeReformer.getHeatDuty("kW");
    heatBalanceRatio = calculateHeatBalanceRatio();
    effectiveReformingTemperatureK = targetReformingTemperatureK;

    if (enforceHeatBalance && heatBalanceRatio < 1.0 && tubeHeatDemandKW > 0.0) {
      double inletTemperature = processInletStream.getTemperature("K");
      double fraction = HydrogenProductionUtils.clamp(heatBalanceRatio, 0.20, 1.0);
      effectiveReformingTemperatureK =
          inletTemperature + (targetReformingTemperatureK - inletTemperature) * fraction;
      tubeReformer = createTubeReformer(effectiveReformingTemperatureK);
      tubeReformer.run(id);
      tubeHeatDemandKW = tubeReformer.getHeatDuty("kW");
      heatBalanceRatio = calculateHeatBalanceRatio();
    }

    syngasOutStream.setThermoSystem(tubeReformer.getOutletStream().getThermoSystem().clone());
    syngasOutStream.run(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    if (processInletStream == null || fuelInletStream == null || airInletStream == null
        || syngasOutStream == null || flueGasOutStream == null) {
      return Double.NaN;
    }
    double inletMass = processInletStream.getFlowRate(unit) + fuelInletStream.getFlowRate(unit)
        + airInletStream.getFlowRate(unit);
    double outletMass = syngasOutStream.getFlowRate(unit) + flueGasOutStream.getFlowRate(unit);
    return outletMass - inletMass;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    if (processInletStream == null || fuelInletStream == null || airInletStream == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(processInletStream, fuelInletStream, airInletStream);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    if (syngasOutStream == null || flueGasOutStream == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(syngasOutStream, flueGasOutStream);
  }

  /**
   * Builds a map of reformer-furnace results.
   *
   * @return ordered result map
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("targetReformingTemperatureK", targetReformingTemperatureK);
    results.put("effectiveReformingTemperatureK", effectiveReformingTemperatureK);
    results.put("availableRadiantHeatKW", availableRadiantHeatKW);
    results.put("tubeHeatDemandKW", tubeHeatDemandKW);
    results.put("heatBalanceRatio", heatBalanceRatio);
    if (tubeReformer != null) {
      results.put("methaneConversion", tubeReformer.getMethaneConversion());
      results.put("tubeWallTemperatureK", tubeReformer.getTubeWallTemperature());
      results.put("heatFluxKWPerM2", tubeReformer.getHeatFluxKWPerM2());
      results.put("tubeWallTemperatureAcceptable", tubeReformer.isTubeWallTemperatureAcceptable());
    }
    if (burner != null) {
      results.put("flameTemperatureK", burner.getFlameTemperature());
      results.put("burnerHeatReleaseKW", burner.getHeatReleasekW());
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }

  /**
   * Creates and configures the internal tube reformer.
   *
   * @param reformingTemperatureK target reforming temperature in Kelvin
   * @return configured tube reformer
   */
  private CatalyticTubeReformer createTubeReformer(double reformingTemperatureK) {
    CatalyticTubeReformer reformer =
        new CatalyticTubeReformer(getName() + " tube reformer", processInletStream);
    reformer.setReformingTemperature(reformingTemperatureK);
    reformer.setPressureDrop(tubePressureDropBar);
    reformer.setTubeGeometry(tubeLengthM, tubeInnerDiameterM, numberOfTubes);
    reformer.setOverallHeatTransferCoefficient(tubeHeatTransferCoefficient);
    reformer.setMaxTubeWallTemperature(maxTubeWallTemperatureK);
    return reformer;
  }

  /**
   * Calculates available heat divided by demanded heat.
   *
   * @return heat-balance ratio
   */
  private double calculateHeatBalanceRatio() {
    if (tubeHeatDemandKW <= 1.0e-12) {
      return Double.POSITIVE_INFINITY;
    }
    return availableRadiantHeatKW / tubeHeatDemandKW;
  }

  /**
   * Validates that required streams are connected.
   */
  private void validateStreams() {
    if (processInletStream == null || fuelInletStream == null || airInletStream == null) {
      throw new IllegalStateException("process, fuel, and air inlet streams must be connected");
    }
  }

  /**
   * Validates positive finite inputs.
   *
   * @param value value to validate
   * @param name parameter name used in exception text
   */
  private void validatePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and greater than zero");
    }
  }
}
