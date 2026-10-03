package neqsim.process.equipment.reactor;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.validation.ValidationResult;

/**
 * Gas-fired hot-oil heater with independently switched burners and a common air and fuel supply.
 *
 * <p>
 * The optional detailed-chemistry backend solves each lit burner's fixed-volume stirred zone, conserves enthalpy while
 * mixing all products with explicitly uncaptured air, and integrates finite-rate burnout through the common chamber.
 * Fuel is redistributed between lit burners in proportion to caller weights, preserving total supplied fuel. Air
 * capture is an independent caller-specified fraction of total air for each lit burner; remaining air joins the common
 * zone. Switching a burner does not impose an undocumented port-leak model.
 *
 * <p>
 * Tube heat absorption, refractory/shell loss, stack sensible energy and residual chemical energy are distinct.
 * Geometry and effective heat-transfer properties are caller inputs; they do not constitute a resolved flame, CFD or
 * certified industrial emissions model. The steady refractory calculation excludes wall thermal storage.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class MultiBurnerFiredHeater extends FiniteRateCombustionReactor {
  private static final long serialVersionUID = 1L;
  private StreamInterface fuelInlet;
  private StreamInterface airInlet;
  private StreamInterface hotOilInlet;
  private StreamInterface hotOilOutlet;
  private final boolean[] burnerEnabled;
  private final double[] fuelWeights;
  private final double[] airCaptureFractions;
  private final double[] burnerVolumes;
  private double chamberDiameterM = Double.NaN;
  private double chamberLengthM = Double.NaN;
  private double tubeAreaM2 = Double.NaN;
  private double tubeConvectionWPerM2K = Double.NaN;
  private double tubeEffectiveEmissivity = Double.NaN;
  private double primaryHeatingAreaFraction = Double.NaN;
  private double refractoryThicknessM = Double.NaN;
  private double refractoryConductivityWPerMK = Double.NaN;
  private double refractoryEmissivity = Double.NaN;
  private double refractoryConvectionWPerM2K = Double.NaN;
  private double ambientTemperatureK = 298.15;
  private double heatSinkTemperatureK = Double.NaN;
  private double ignitionTemperatureK = 2000.0;

  /**
   * Create a multi-burner heater; all burner states are initially on, but their volumes and air capture need setting.
   *
   * @param name equipment name
   * @param fuel fuel supply stream
   * @param air common combustion-air stream
   * @param numberOfBurners installed burner count
   */
  public MultiBurnerFiredHeater(String name, StreamInterface fuel, StreamInterface air, int numberOfBurners) {
    super(name, fuel);
    if (air == null || numberOfBurners < 1) {
      throw new IllegalArgumentException("An air stream and at least one burner are required");
    }
    fuelInlet = fuel;
    airInlet = air;
    burnerEnabled = new boolean[numberOfBurners];
    fuelWeights = new double[numberOfBurners];
    airCaptureFractions = new double[numberOfBurners];
    burnerVolumes = new double[numberOfBurners];
    for (int i = 0; i < numberOfBurners; i++) {
      burnerEnabled[i] = true;
      fuelWeights[i] = 1.0;
      burnerVolumes[i] = Double.NaN;
    }
    // Parent scalar-age validation is satisfied; this unit requests fixed physical volumes instead.
    setResidenceTime(1.0);
  }

  /**
   * Configure one installed burner. Fractions describe capture from the common total-air stream.
   *
   * @param burner zero-based burner index
   * @param enabled whether the burner is lit
   * @param fuelWeight positive relative share of total fuel among lit burners
   * @param airCaptureFraction fraction of total air routed to this burner when lit, from zero through one
   * @param volumeM3 positive reactive stirred-zone volume [m3]
   */
  public void configureBurner(int burner, boolean enabled, double fuelWeight, double airCaptureFraction,
      double volumeM3) {
    index(burner);
    positive(fuelWeight, "Fuel weight");
    fraction(airCaptureFraction, "Air capture fraction");
    positive(volumeM3, "Burner zone volume");
    burnerEnabled[burner] = enabled;
    fuelWeights[burner] = fuelWeight;
    airCaptureFractions[burner] = airCaptureFraction;
    burnerVolumes[burner] = volumeM3;
  }

  /**
   * Switch one burner while retaining its configured fuel weight, air routing and zone volume.
   *
   * @param burner zero-based burner index
   * @param enabled true for on, false for off
   */
  public void setBurnerEnabled(int burner, boolean enabled) {
    index(burner);
    burnerEnabled[burner] = enabled;
  }

  /**
   * Set cylindrical chamber dimensions, separately from any chimney dimensions.
   *
   * @param diameterM clear internal chamber diameter [m]
   * @param lengthM effective chamber axial length [m]
   */
  public void setChamberGeometry(double diameterM, double lengthM) {
    positive(diameterM, "Chamber diameter");
    positive(lengthM, "Chamber length");
    chamberDiameterM = diameterM;
    chamberLengthM = lengthM;
  }

  /**
   * Set effective tube heat-transfer inputs. Radiation uses a caller-defined combined gas/view-factor emissivity.
   *
   * @param areaM2 effective total tube surface [m2]
   * @param convectionWPerM2K effective gas-to-tube convection coefficient [W/(m2 K)]
   * @param effectiveEmissivity effective grey gas-to-tube emissivity, zero through one
   * @param primaryAreaFraction fraction of area assigned to the burner stirred zones, zero through one
   * @param sinkTemperatureK prescribed effective oil-side tube sink temperature [K]
   */
  public void setTubeHeatTransfer(double areaM2, double convectionWPerM2K, double effectiveEmissivity,
      double primaryAreaFraction, double sinkTemperatureK) {
    positive(areaM2, "Tube area");
    nonnegative(convectionWPerM2K, "Tube convection coefficient");
    fraction(effectiveEmissivity, "Tube effective emissivity");
    fraction(primaryAreaFraction, "Primary area fraction");
    positive(sinkTemperatureK, "Heat sink temperature");
    tubeAreaM2 = areaM2;
    tubeConvectionWPerM2K = convectionWPerM2K;
    tubeEffectiveEmissivity = effectiveEmissivity;
    primaryHeatingAreaFraction = primaryAreaFraction;
    heatSinkTemperatureK = sinkTemperatureK;
  }

  /**
   * Set a steady refractory conduction model with gas-side convection and grey radiation.
   *
   * @param thicknessM brick thickness [m]
   * @param conductivityWPerMK brick thermal conductivity [W/(m K)]
   * @param effectiveEmissivity effective gas-to-refractory emissivity, zero through one
   * @param convectionWPerM2K gas-to-refractory convection coefficient [W/(m2 K)]
   * @param surroundingsK prescribed cold-side ambient temperature [K]
   */
  public void setRefractory(double thicknessM, double conductivityWPerMK, double effectiveEmissivity,
      double convectionWPerM2K, double surroundingsK) {
    positive(thicknessM, "Refractory thickness");
    positive(conductivityWPerMK, "Refractory conductivity");
    fraction(effectiveEmissivity, "Refractory effective emissivity");
    nonnegative(convectionWPerM2K, "Refractory convection coefficient");
    positive(surroundingsK, "Ambient temperature");
    refractoryThicknessM = thicknessM;
    refractoryConductivityWPerMK = conductivityWPerMK;
    refractoryEmissivity = effectiveEmissivity;
    refractoryConvectionWPerM2K = convectionWPerM2K;
    ambientTemperatureK = surroundingsK;
  }

  /**
   * Set the hot PSR startup guess without changing the cold reactant inlet.
   *
   * @param kelvin initial burner-zone temperature [K]
   */
  @Override
  public void setIgnitionTemperature(double kelvin) {
    positive(kelvin, "Ignition temperature");
    ignitionTemperatureK = kelvin;
  }

  /**
   * Attach the circulating hot-oil stream. Tube sink temperature remains an explicit caller-selected effective value.
   *
   * @param oil hot-oil supply stream
   */
  public void setHotOilInlet(StreamInterface oil) {
    if (oil == null) {
      throw new IllegalArgumentException("Hot oil stream must not be null");
    }
    hotOilInlet = oil;
    hotOilOutlet = oil.clone(getName() + " hot oil outlet");
  }

  /**
   * Get the hot-oil outlet after the latest successful heater calculation.
   *
   * @return hot-oil outlet stream, or null when no hot-oil stream is connected
   */
  public StreamInterface getHotOilOutlet() {
    if (hotOilInlet != null && hasExecutionBeenAttempted() && !isOutletProjectionValid()) {
      throw new IllegalStateException("Hot-oil outlet is not current; run the heater successfully first");
    }
    return hotOilOutlet;
  }

  /**
   * Get the fuel chemical lower-heating-value input.
   *
   * @return firing duty [MW], distinct from absorbed useful heat
   */
  public double getFiringDutyMW() {
    return resultNumber("fuelChemicalPowerW") / 1.0e6;
  }

  /**
   * Get mechanism-predicted useful heat absorbed by the oil tube sink.
   *
   * @return useful heat [MW]
   */
  public double getUsefulHeatMW() {
    return resultNumber("usefulHeatToOilW") / 1.0e6;
  }

  /**
   * Get refractory/shell heat loss to the prescribed ambient boundary.
   *
   * @return shell heat loss [MW]
   */
  public double getShellHeatLossMW() {
    return resultNumber("shellHeatLossW") / 1.0e6;
  }

  /**
   * Get stack sensible energy relative to the documented mechanism reference temperature.
   *
   * @return stack sensible heat [MW]
   */
  public double getStackSensibleHeatMW() {
    return resultNumber("stackSensibleHeatW") / 1.0e6;
  }

  /**
   * Get chemical energy retained in incomplete-combustion products and unburned fuel.
   *
   * @return residual chemical energy [MW]
   */
  public double getResidualChemicalPowerMW() {
    return resultNumber("residualChemicalPowerW") / 1.0e6;
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = super.validateSetup();
    double captured = 0.0;
    double volumes = 0.0;
    int active = 0;
    for (int i = 0; i < burnerEnabled.length; i++) {
      if (!Double.isFinite(burnerVolumes[i]) || burnerVolumes[i] <= 0.0) {
        result.addError("burners", "Burner zone volume is unset", "Configure every installed burner");
      }
      if (burnerEnabled[i]) {
        active++;
        captured += airCaptureFractions[i];
        volumes += burnerVolumes[i];
      }
    }
    double grossVolume = Math.PI * chamberDiameterM * chamberDiameterM * chamberLengthM / 4.0;
    if (active == 0 || captured > 1.0 + 1.0e-12 || captured <= 0.0) {
      result.addError("burners", "No lit burner or invalid total captured air", "Enable burners and set air capture");
    }
    if (!Double.isFinite(grossVolume) || grossVolume <= volumes) {
      result.addError("geometry", "Chamber volume must exceed active burner-zone volume",
          "Set separate chamber diameter/length and realistic reactive volumes");
    }
    if (!Double.isFinite(tubeAreaM2) || !Double.isFinite(refractoryThicknessM)) {
      result.addError("heat transfer", "Tube or refractory model is unset",
          "Call setTubeHeatTransfer and setRefractory");
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    invalidateOutletProjection();
    Mixer mixer = new Mixer(getName() + " reactant mixer");
    mixer.addStream(fuelInlet);
    mixer.addStream(airInlet);
    mixer.run(id);
    setInletStream(mixer.getOutletStream());
    super.run(id);
    if (hotOilInlet != null) {
      try {
        StreamInterface calculated = neqsim.process.util.combustion.HotOilHeatBalance.apply(hotOilInlet,
            resultNumber("usefulHeatToOilW"), getName() + " hot oil outlet", id);
        hotOilOutlet.setThermoSystem(calculated.getThermoSystem());
        hotOilOutlet.setCalculationIdentifier(id);
      } catch (RuntimeException ex) {
        invalidateOutletProjection();
        throw ex;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> streams = new ArrayList<>();
    streams.add(fuelInlet);
    streams.add(airInlet);
    if (hotOilInlet != null) {
      streams.add(hotOilInlet);
    }
    return streams;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> streams = new ArrayList<>();
    streams.add(getOutletStream());
    if (hotOilOutlet != null) {
      streams.add(getHotOilOutlet());
    }
    return streams;
  }

  /**
   * Build a fixed-volume multi-zone heater request without changing the total fuel or air supply.
   *
   * @param inlet mixed inlet stream for the parent conservation contract
   * @return versioned heater request
   */
  @Override
  protected JsonObject createRequest(SystemInterface inlet) {
    JsonObject request = super.createRequest(inlet);
    request.addProperty("reactorModel", "MULTI_BURNER_FIRED_HEATER");
    request.add("fuelMolarFlows", flows(fuelInlet));
    request.add("airMolarFlows", flows(airInlet));
    request.addProperty("fuelTemperatureK", fuelInlet.getTemperature());
    request.addProperty("airTemperatureK", airInlet.getTemperature());
    JsonArray burners = new JsonArray();
    double activeVolume = 0.0;
    for (int i = 0; i < burnerEnabled.length; i++) {
      JsonObject burner = new JsonObject();
      burner.addProperty("id", i);
      burner.addProperty("enabled", burnerEnabled[i]);
      burner.addProperty("fuelWeight", fuelWeights[i]);
      burner.addProperty("airCaptureFraction", airCaptureFractions[i]);
      burner.addProperty("volumeM3", burnerVolumes[i]);
      burners.add(burner);
      if (burnerEnabled[i]) {
        activeVolume += burnerVolumes[i];
      }
    }
    request.add("burners", burners);
    request.addProperty("ignitionTemperatureK", ignitionTemperatureK);
    request.addProperty("chamberDiameterM", chamberDiameterM);
    request.addProperty("chamberLengthM", chamberLengthM);
    request.addProperty("commonReactiveVolumeM3",
        Math.PI * chamberDiameterM * chamberDiameterM * chamberLengthM / 4.0 - activeVolume);
    request.addProperty("tubeAreaM2", tubeAreaM2);
    request.addProperty("tubeConvectionWPerM2K", tubeConvectionWPerM2K);
    request.addProperty("tubeEffectiveEmissivity", tubeEffectiveEmissivity);
    request.addProperty("primaryHeatingAreaFraction", primaryHeatingAreaFraction);
    request.addProperty("heatSinkTemperatureK", heatSinkTemperatureK);
    request.addProperty("refractoryAreaM2",
        Math.PI * chamberDiameterM * chamberLengthM + Math.PI * chamberDiameterM * chamberDiameterM / 2.0);
    request.addProperty("refractoryThicknessM", refractoryThicknessM);
    request.addProperty("refractoryConductivityWPerMK", refractoryConductivityWPerMK);
    request.addProperty("refractoryEffectiveEmissivity", refractoryEmissivity);
    request.addProperty("refractoryConvectionWPerM2K", refractoryConvectionWPerM2K);
    request.addProperty("ambientTemperatureK", ambientTemperatureK);
    return request;
  }

  /**
   * Extract component flows without modifying a supply stream.
   *
   * @param stream supply stream
   * @return component molar flows [mol/s]
   */
  private static JsonObject flows(StreamInterface stream) {
    JsonObject result = new JsonObject();
    SystemInterface fluid = stream.getThermoSystem();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      result.addProperty(fluid.getComponent(i).getComponentName(), fluid.getComponent(i).getFlowRate("mole/sec"));
    }
    return result;
  }

  /**
   * Read a finite numeric result after successful execution.
   *
   * @param key detailed result field
   * @return field value
   */
  private double resultNumber(String key) {
    if (!isOutletProjectionValid()) {
      throw new IllegalStateException("No current accepted heater result");
    }
    JsonObject result = JsonParser.parseString(getKineticsResultJson()).getAsJsonObject();
    return result.get(key).getAsDouble();
  }

  /**
   * Validate burner index.
   *
   * @param burner zero-based index
   */
  private void index(int burner) {
    if (burner < 0 || burner >= burnerEnabled.length) {
      throw new IllegalArgumentException("Burner index is outside the installed burner range");
    }
  }

  /**
   * Validate positive input.
   *
   * @param value input value
   * @param name input name
   */
  private static void positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Validate nonnegative input.
   *
   * @param value input value
   * @param name input name
   */
  private static void nonnegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }

  /**
   * Validate a unit fraction.
   *
   * @param value input value
   * @param name input name
   */
  private static void fraction(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be in [0, 1]");
    }
  }
}
