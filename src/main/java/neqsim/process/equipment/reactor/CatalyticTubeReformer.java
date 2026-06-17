package neqsim.process.equipment.reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Catalytic tube-side steam methane reformer model for hydrogen production.
 *
 * <p>
 * The model uses a constrained Gibbs equilibrium reactor at a specified tube outlet temperature and
 * overlays engineering checks for pressure drop, tube-wall temperature, heat flux, steam-to-carbon
 * ratio, and nickel-catalyst deactivation. It is intended as a design-envelope model for SMR
 * flowsheets before detailed furnace CFD, burner layout, and vendor tube-rating calculations.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class CatalyticTubeReformer extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default reforming temperature in Kelvin. */
  private double reformingTemperatureK = 1123.15;

  /** User-specified or estimated tube-side pressure drop in bar. */
  private double pressureDropBar = 1.0;

  /** Effective heated tube length in metres. */
  private double tubeLengthM = 10.0;

  /** Tube inside diameter in metres. */
  private double tubeInnerDiameterM = 0.10;

  /** Number of parallel reformer tubes. */
  private int numberOfTubes = 100;

  /** Overall heat-transfer coefficient from tube wall to gas in W/(m2 K). */
  private double overallHeatTransferCoefficient = 120.0;

  /** Maximum permitted tube-wall temperature in Kelvin. */
  private double maxTubeWallTemperatureK = 1173.15;

  /** Catalyst bed used for activity and pressure-drop screening. */
  private CatalystBed catalystBed = new CatalystBed();

  /** Catalyst deactivation model applied before each run. */
  private CatalystDeactivationKinetics deactivationKinetics = new CatalystDeactivationKinetics(
      CatalystDeactivationKinetics.CatalystFamily.NICKEL_REFORMING);

  /** Heat duty required by the tube-side process in W. */
  private double heatDutyW = 0.0;

  /** Methane conversion from inlet to outlet. */
  private double methaneConversion = 0.0;

  /** Steam-to-carbon molar ratio in the inlet feed. */
  private double steamToCarbonRatio = Double.NaN;

  /** Tube-wall temperature estimate in Kelvin. */
  private double tubeWallTemperatureK = Double.NaN;

  /** Average heat flux over heated tube surface in kW/m2. */
  private double heatFluxKWPerM2 = 0.0;

  /** Dry syngas lower heating value in MJ/Nm3. */
  private double drySyngasLhvMjPerNm3 = 0.0;

  /** Internal Gibbs reactor reused between runs. */
  private transient GibbsReactor equilibriumReactor;

  /**
   * Creates a catalytic tube reformer.
   *
   * @param name equipment name
   */
  public CatalyticTubeReformer(String name) {
    super(name);
  }

  /**
   * Creates a catalytic tube reformer with an inlet stream.
   *
   * @param name equipment name
   * @param inletStream reformer feed stream containing methane and steam
   */
  public CatalyticTubeReformer(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the target tube outlet temperature.
   *
   * @param temperatureK reforming temperature in Kelvin
   */
  public void setReformingTemperature(double temperatureK) {
    validatePositive(temperatureK, "temperatureK");
    this.reformingTemperatureK = temperatureK;
  }

  /**
   * Sets the target tube outlet temperature with unit conversion.
   *
   * @param temperature temperature value
   * @param unit temperature unit, either K or C
   */
  public void setReformingTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      setReformingTemperature(temperature + 273.15);
    } else {
      setReformingTemperature(temperature);
    }
  }

  /**
   * Gets the target tube outlet temperature.
   *
   * @return reforming temperature in Kelvin
   */
  public double getReformingTemperature() {
    return reformingTemperatureK;
  }

  /**
   * Sets tube geometry.
   *
   * @param lengthM heated tube length in metres
   * @param innerDiameterM tube inside diameter in metres
   * @param tubes number of parallel tubes
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
   * @param pressureDropBar pressure drop in bar, non-negative
   */
  public void setPressureDrop(double pressureDropBar) {
    if (!Double.isFinite(pressureDropBar) || pressureDropBar < 0.0) {
      throw new IllegalArgumentException("pressureDropBar must be finite and non-negative");
    }
    this.pressureDropBar = pressureDropBar;
  }

  /**
   * Gets tube-side pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getPressureDrop() {
    return pressureDropBar;
  }

  /**
   * Sets the overall heat-transfer coefficient.
   *
   * @param coefficient heat-transfer coefficient in W/(m2 K)
   */
  public void setOverallHeatTransferCoefficient(double coefficient) {
    validatePositive(coefficient, "coefficient");
    this.overallHeatTransferCoefficient = coefficient;
  }

  /**
   * Sets the maximum tube-wall temperature limit.
   *
   * @param temperatureK maximum tube-wall temperature in Kelvin
   */
  public void setMaxTubeWallTemperature(double temperatureK) {
    validatePositive(temperatureK, "temperatureK");
    this.maxTubeWallTemperatureK = temperatureK;
  }

  /**
   * Sets the catalyst bed used for activity screening.
   *
   * @param catalystBed catalyst bed, not null
   */
  public void setCatalystBed(CatalystBed catalystBed) {
    if (catalystBed == null) {
      throw new IllegalArgumentException("catalystBed cannot be null");
    }
    this.catalystBed = catalystBed;
  }

  /**
   * Gets the catalyst bed.
   *
   * @return catalyst bed
   */
  public CatalystBed getCatalystBed() {
    return catalystBed;
  }

  /**
   * Sets the catalyst deactivation model.
   *
   * @param kinetics deactivation model, or null to disable activity updates
   */
  public void setDeactivationKinetics(CatalystDeactivationKinetics kinetics) {
    this.deactivationKinetics = kinetics;
  }

  /**
   * Gets the heat duty.
   *
   * @return heat duty in W, positive when heat is required
   */
  public double getHeatDuty() {
    return heatDutyW;
  }

  /**
   * Gets the heat duty in a selected unit.
   *
   * @param unit unit string, W, kW, or MW
   * @return heat duty in requested unit
   */
  public double getHeatDuty(String unit) {
    if (unit == null) {
      return heatDutyW;
    }
    String normalized = unit.trim().toLowerCase();
    if ("mw".equals(normalized)) {
      return heatDutyW / 1.0e6;
    } else if ("kw".equals(normalized)) {
      return heatDutyW / 1.0e3;
    }
    return heatDutyW;
  }

  /**
   * Gets methane conversion.
   *
   * @return methane conversion fraction
   */
  public double getMethaneConversion() {
    return methaneConversion;
  }

  /**
   * Gets the inlet steam-to-carbon ratio.
   *
   * @return steam-to-carbon ratio
   */
  public double getSteamToCarbonRatio() {
    return steamToCarbonRatio;
  }

  /**
   * Gets the tube-wall temperature estimate.
   *
   * @return tube-wall temperature in Kelvin
   */
  public double getTubeWallTemperature() {
    return tubeWallTemperatureK;
  }

  /**
   * Gets the average heat flux.
   *
   * @return heat flux in kW/m2
   */
  public double getHeatFluxKWPerM2() {
    return heatFluxKWPerM2;
  }

  /**
   * Gets dry syngas lower heating value.
   *
   * @return dry syngas LHV in MJ/Nm3
   */
  public double getDrySyngasLhvMjPerNm3() {
    return drySyngasLhvMjPerNm3;
  }

  /**
   * Checks whether the tube-wall temperature is within the configured design limit.
   *
   * @return true when tube-wall temperature is not above the limit
   */
  public boolean isTubeWallTemperatureAcceptable() {
    return !Double.isNaN(tubeWallTemperatureK) && tubeWallTemperatureK <= maxTubeWallTemperatureK;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface inletBasis = getInletStream().getThermoSystem().clone();
    inletBasis.init(3);
    double inletEnthalpyJ = inletBasis.getEnthalpy("J");

    SystemInterface reactorFeedSystem = inletBasis.clone();
    HydrogenProductionUtils.ensureSyngasComponents(reactorFeedSystem);
    reactorFeedSystem.setTemperature(reformingTemperatureK);
    reactorFeedSystem.setPressure(Math.max(1.0e-6, inletBasis.getPressure() - pressureDropBar));
    reactorFeedSystem.init(3);

    steamToCarbonRatio = HydrogenProductionUtils.calculateSteamToCarbonRatio(reactorFeedSystem);
    if (deactivationKinetics != null) {
      deactivationKinetics.setTemperature(reformingTemperatureK)
          .setSteamToCarbonRatio(Double.isNaN(steamToCarbonRatio) ? 0.0 : steamToCarbonRatio);
      deactivationKinetics.applyTo(catalystBed);
    }

    Stream reactorFeed = new Stream(getName() + " equilibrium feed", reactorFeedSystem);
    reactorFeed.run(id);

    equilibriumReactor = HydrogenProductionUtils.createSyngasGibbsReactor(
        getName() + " Gibbs equilibrium", reactorFeed, GibbsReactor.EnergyMode.ISOTHERMAL);
    equilibriumReactor.run(id);

    SystemInterface outletSystem = equilibriumReactor.getOutletStream().getThermoSystem().clone();
    outletSystem.init(3);
    double outletEnthalpyJ = outletSystem.getEnthalpy("J");
    heatDutyW = Math.max(0.0, outletEnthalpyJ - inletEnthalpyJ);
    methaneConversion =
        HydrogenProductionUtils.calculateMethaneConversion(inletBasis, outletSystem);
    drySyngasLhvMjPerNm3 = HydrogenProductionUtils.estimateDrySyngasLhvMjPerNm3(outletSystem);
    estimateTubeThermalState();

    outStream.setThermoSystem(outletSystem);
    outStream.run(id);
    setCalculationIdentifier(id);
  }

  /**
   * Builds a map of reformer results.
   *
   * @return ordered result map
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("reformingTemperatureK", reformingTemperatureK);
    results.put("pressureDropBar", pressureDropBar);
    results.put("methaneConversion", methaneConversion);
    results.put("steamToCarbonRatio", steamToCarbonRatio);
    results.put("heatDutyKW", heatDutyW / 1.0e3);
    results.put("heatFluxKWPerM2", heatFluxKWPerM2);
    results.put("tubeWallTemperatureK", tubeWallTemperatureK);
    results.put("tubeWallTemperatureAcceptable", isTubeWallTemperatureAcceptable());
    results.put("catalystActivity", catalystBed.getActivityFactor());
    results.put("drySyngasLhvMjPerNm3", drySyngasLhvMjPerNm3);
    if (outStream != null && outStream.getThermoSystem() != null) {
      results.put("syngasComposition_molFrac",
          HydrogenProductionUtils.extractSyngasComposition(outStream.getThermoSystem()));
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
   * Estimates heat flux and tube-wall temperature from duty and geometry.
   */
  private void estimateTubeThermalState() {
    double heatedArea = Math.PI * tubeInnerDiameterM * tubeLengthM * numberOfTubes;
    if (heatedArea <= 0.0) {
      heatFluxKWPerM2 = 0.0;
      tubeWallTemperatureK = reformingTemperatureK;
      return;
    }
    heatFluxKWPerM2 = heatDutyW / heatedArea / 1.0e3;
    double deltaT = heatDutyW / Math.max(1.0e-12, overallHeatTransferCoefficient * heatedArea);
    tubeWallTemperatureK = reformingTemperatureK + Math.max(0.0, deltaT);
  }

  /**
   * Validates that a numeric value is positive and finite.
   *
   * @param value value to check
   * @param name parameter name used in the exception
   */
  private void validatePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and greater than zero");
    }
  }
}
