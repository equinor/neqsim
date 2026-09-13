package neqsim.process.equipment.heatexchanger;

import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.characterization.SarirAtmosphericAssay;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Source-bounded crude heating train for the public Sarir atmospheric-column feed.
 *
 * <p>
 * The case preserves the published crude rate, atmospheric-column feed temperature, and absolute pressure from
 * {@link SarirAtmosphericReference}. The source does not document a refinery preheat train, furnace pressure loss,
 * efficiency, fuel, or emission factors. Callers must therefore provide those engineering choices explicitly through
 * {@link HeatingInputs}. This is an executable screening case, not a reconstruction of the Sarir heater system.
 * </p>
 *
 * <p>
 * The configured train contains a sensible {@link Heater} followed by a {@link FiredHeater}. It deliberately excludes
 * heat-exchanger topology, utility streams, fouling, radiant/convection sections, combustion chemistry, and plant
 * calibration.
 * </p>
 */
public final class SarirAtmosphericCrudeHeatingCase {
  private static final double ABSOLUTE_TEMPERATURE_TOLERANCE_KELVIN = 1.0e-7;
  private static final double ABSOLUTE_PRESSURE_TOLERANCE_BARA = 1.0e-10;
  private static final double RELATIVE_FLOW_TOLERANCE = 1.0e-10;
  private static final double RELATIVE_IDENTITY_TOLERANCE = 1.0e-10;

  private final Stream crudeInletStream;
  private final Heater preheater;
  private final FiredHeater furnace;
  private final HeatingInputs heatingInputs;

  private SarirAtmosphericCrudeHeatingCase(Stream crudeInletStream, Heater preheater, FiredHeater furnace,
      HeatingInputs heatingInputs) {
    this.crudeInletStream = crudeInletStream;
    this.preheater = preheater;
    this.furnace = furnace;
    this.heatingInputs = heatingInputs;
  }

  /**
   * Create a configured but unsolved crude heating train.
   *
   * @param name non-blank case name
   * @param cutSpecificGravity specific gravity for each of the 18 source-derived cuts
   * @param cutMolarMassKgPerMol molar mass for each cut, in kg/mol
   * @param heatingInputs explicit source-unreported heating, pressure-loss, fuel, and emission inputs
   * @return configured heating train
   * @throws NullPointerException if {@code heatingInputs} is {@code null}
   * @throws IllegalArgumentException if the name, profiles, or heating inputs are invalid
   */
  public static SarirAtmosphericCrudeHeatingCase create(String name, double[] cutSpecificGravity,
      double[] cutMolarMassKgPerMol, HeatingInputs heatingInputs) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Case name must be non-blank");
    }
    Objects.requireNonNull(heatingInputs, "heatingInputs");

    double columnFeedTemperatureKelvin = getPublishedColumnFeedTemperatureKelvin();
    if (!(heatingInputs.getPreheatTemperatureKelvin() < columnFeedTemperatureKelvin)) {
      throw new IllegalArgumentException("Preheat temperature must be below the published column-feed temperature");
    }

    double columnFeedPressureBara = getPublishedColumnFeedPressureBara();
    double furnaceInletPressureBara = columnFeedPressureBara + heatingInputs.getFurnacePressureLossBara();
    double crudeInletPressureBara = furnaceInletPressureBara + heatingInputs.getPreheaterPressureLossBara();

    SystemInterface crude = new SystemSrkEos(heatingInputs.getCrudeInletTemperatureKelvin(), crudeInletPressureBara);
    OilAssayCharacterisation assay =
        SarirAtmosphericAssay.create(crude, cutSpecificGravity, cutMolarMassKgPerMol);
    assay.apply();
    crude.setMixingRule("classic");

    Stream crudeInlet = new Stream(name + " crude inlet", crude);
    crudeInlet.setFlowRate(SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(), "kg/hr");
    crudeInlet.setTemperature(heatingInputs.getCrudeInletTemperatureKelvin(), "K");
    crudeInlet.setPressure(crudeInletPressureBara, "bara");

    Heater configuredPreheater = new Heater(name + " preheater", crudeInlet);
    configuredPreheater.setOutletTemperature(heatingInputs.getPreheatTemperatureKelvin(), "K");
    configuredPreheater.setOutletPressure(furnaceInletPressureBara, "bara");

    FiredHeater configuredFurnace = new FiredHeater(name + " furnace", configuredPreheater.getOutletStream());
    configuredFurnace.setOutletTemperature(columnFeedTemperatureKelvin, "K");
    configuredFurnace.setOutletPressure(columnFeedPressureBara, "bara");
    configuredFurnace.setThermalEfficiency(heatingInputs.getThermalEfficiency());
    configuredFurnace.setFuelLHV(heatingInputs.getFuelLowerHeatingValueJPerKg());
    configuredFurnace.setFuelCO2Factor(heatingInputs.getFuelCO2FactorKgPerKg());
    configuredFurnace.setNoxFactor(heatingInputs.getNoxFactorKgPerGJ());
    configuredFurnace.setStackTemperature(heatingInputs.getStackTemperatureKelvin());

    return new SarirAtmosphericCrudeHeatingCase(crudeInlet, configuredPreheater, configuredFurnace, heatingInputs);
  }

  /**
   * Run the inlet, preheater, and furnace under one calculation identifier and verify the heating contract.
   *
   * @param id calculation identifier
   * @throws NullPointerException if {@code id} is {@code null}
   * @throws IllegalStateException if a solved boundary, balance, or fuel identity is invalid
   */
  public void run(UUID id) {
    Objects.requireNonNull(id, "id");
    crudeInletStream.run(id);
    preheater.run(id);
    furnace.run(id);
    validateSolvedTrain();
  }

  /** @return mutable characterized crude inlet stream */
  public Stream getCrudeInletStream() {
    return crudeInletStream;
  }

  /** @return mutable sensible preheater */
  public Heater getPreheater() {
    return preheater;
  }

  /** @return mutable fired heater */
  public FiredHeater getFurnace() {
    return furnace;
  }

  /** @return mutable furnace outlet at the atmospheric-column feed boundary after a successful run */
  public StreamInterface getColumnFeedStream() {
    return furnace.getOutletStream();
  }

  /** @return immutable source-unreported inputs used to configure this case */
  public HeatingInputs getHeatingInputs() {
    return heatingInputs;
  }

  private void validateSolvedTrain() {
    double sourceFlowKgPerHour = SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour();
    double preheaterFlowKgPerHour = preheater.getOutletStream().getFlowRate("kg/hr");
    double columnFeedFlowKgPerHour = getColumnFeedStream().getFlowRate("kg/hr");
    requireClose(preheaterFlowKgPerHour, sourceFlowKgPerHour, RELATIVE_FLOW_TOLERANCE,
        "Preheater mass-flow closure");
    requireClose(columnFeedFlowKgPerHour, sourceFlowKgPerHour, RELATIVE_FLOW_TOLERANCE,
        "Furnace mass-flow closure");
    requireAbsoluteClose(preheater.getOutletStream().getTemperature("K"), heatingInputs.getPreheatTemperatureKelvin(),
        ABSOLUTE_TEMPERATURE_TOLERANCE_KELVIN, "Preheater outlet temperature");
    requireAbsoluteClose(preheater.getOutletStream().getPressure("bara"),
        getPublishedColumnFeedPressureBara() + heatingInputs.getFurnacePressureLossBara(),
        ABSOLUTE_PRESSURE_TOLERANCE_BARA, "Preheater outlet pressure");
    requireAbsoluteClose(getColumnFeedStream().getTemperature("K"), getPublishedColumnFeedTemperatureKelvin(),
        ABSOLUTE_TEMPERATURE_TOLERANCE_KELVIN, "Column-feed temperature");
    requireAbsoluteClose(getColumnFeedStream().getPressure("bara"), getPublishedColumnFeedPressureBara(),
        ABSOLUTE_PRESSURE_TOLERANCE_BARA, "Column-feed pressure");

    double preheaterDutyW = preheater.getDuty("W");
    double absorbedDutyW = furnace.getAbsorbedDuty("W");
    double firedDutyW = furnace.getFiredDuty("W");
    double stackLossW = furnace.getStackLoss("W");
    requireFinitePositive(preheaterDutyW, "Preheater duty");
    requireFinitePositive(absorbedDutyW, "Furnace absorbed duty");
    requireFinitePositive(firedDutyW, "Furnace fired duty");
    requireFiniteNonNegative(stackLossW, "Furnace stack loss");
    requireClose(firedDutyW, absorbedDutyW / heatingInputs.getThermalEfficiency(), RELATIVE_IDENTITY_TOLERANCE,
        "Fired-duty efficiency identity");
    requireClose(stackLossW, firedDutyW - absorbedDutyW, RELATIVE_IDENTITY_TOLERANCE,
        "Stack-loss identity");

    double fuelKgPerHour = furnace.getFuelConsumption("kg/hr");
    double co2KgPerHour = furnace.getCO2Emissions("kg/hr");
    double noxKgPerHour = furnace.getNOxEmissions("kg/hr");
    requireFinitePositive(fuelKgPerHour, "Fuel consumption");
    requireFiniteNonNegative(co2KgPerHour, "CO2 emissions");
    requireFiniteNonNegative(noxKgPerHour, "NOx emissions");
    requireClose(fuelKgPerHour, firedDutyW / heatingInputs.getFuelLowerHeatingValueJPerKg() * 3600.0,
        RELATIVE_IDENTITY_TOLERANCE, "Fuel-LHV identity");
    requireClose(co2KgPerHour, fuelKgPerHour * heatingInputs.getFuelCO2FactorKgPerKg(),
        RELATIVE_IDENTITY_TOLERANCE, "CO2-factor identity");
    requireClose(noxKgPerHour, firedDutyW / 1.0e9 * heatingInputs.getNoxFactorKgPerGJ() * 3600.0,
        RELATIVE_IDENTITY_TOLERANCE, "NOx-factor identity");
  }

  private static double getPublishedColumnFeedTemperatureKelvin() {
    return SarirAtmosphericReference.getColumnFeedTemperatureCelsius() + 273.15;
  }

  private static double getPublishedColumnFeedPressureBara() {
    return SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0;
  }

  private static void requireFinitePositive(double value, String label) {
    if (!Double.isFinite(value) || !(value > 0.0)) {
      throw new IllegalStateException(label + " must be finite and positive");
    }
  }

  private static void requireFiniteNonNegative(double value, String label) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalStateException(label + " must be finite and non-negative");
    }
  }

  private static void requireClose(double value, double expected, double relativeTolerance, String label) {
    if (!Double.isFinite(value) || !Double.isFinite(expected)
        || Math.abs(value - expected) > relativeTolerance * Math.max(1.0, Math.abs(expected))) {
      throw new IllegalStateException(label + " is outside the qualified tolerance");
    }
  }

  private static void requireAbsoluteClose(double value, double expected, double absoluteTolerance, String label) {
    if (!Double.isFinite(value) || Math.abs(value - expected) > absoluteTolerance) {
      throw new IllegalStateException(label + " is outside the qualified tolerance");
    }
  }

  /** Explicit source-unreported crude-heating engineering inputs. */
  public static final class HeatingInputs {
    private final double crudeInletTemperatureKelvin;
    private final double preheatTemperatureKelvin;
    private final double preheaterPressureLossBara;
    private final double furnacePressureLossBara;
    private final double thermalEfficiency;
    private final double fuelLowerHeatingValueJPerKg;
    private final double fuelCO2FactorKgPerKg;
    private final double noxFactorKgPerGJ;
    private final double stackTemperatureKelvin;

    /**
     * Create validated source-unreported heating inputs.
     *
     * @param crudeInletTemperatureKelvin crude temperature before sensible preheat, in kelvin
     * @param preheatTemperatureKelvin crude temperature between preheater and furnace, in kelvin
     * @param preheaterPressureLossBara absolute pressure loss across the preheater, in bar
     * @param furnacePressureLossBara absolute pressure loss across the furnace, in bar
     * @param thermalEfficiency fired-heater thermal efficiency in (0, 1]
     * @param fuelLowerHeatingValueJPerKg fuel lower heating value in J/kg
     * @param fuelCO2FactorKgPerKg kg CO2 per kg fuel
     * @param noxFactorKgPerGJ kg NOx per GJ fired duty
     * @param stackTemperatureKelvin reported stack temperature in kelvin
     */
    public HeatingInputs(double crudeInletTemperatureKelvin, double preheatTemperatureKelvin,
        double preheaterPressureLossBara, double furnacePressureLossBara, double thermalEfficiency,
        double fuelLowerHeatingValueJPerKg, double fuelCO2FactorKgPerKg, double noxFactorKgPerGJ,
        double stackTemperatureKelvin) {
      requireInputFinitePositive(crudeInletTemperatureKelvin, "Crude inlet temperature");
      requireInputFinitePositive(preheatTemperatureKelvin, "Preheat temperature");
      requireInputFiniteNonNegative(preheaterPressureLossBara, "Preheater pressure loss");
      requireInputFiniteNonNegative(furnacePressureLossBara, "Furnace pressure loss");
      if (!Double.isFinite(thermalEfficiency) || !(thermalEfficiency > 0.0) || thermalEfficiency > 1.0) {
        throw new IllegalArgumentException("Thermal efficiency must be finite and in (0, 1]");
      }
      requireInputFinitePositive(fuelLowerHeatingValueJPerKg, "Fuel lower heating value");
      requireInputFiniteNonNegative(fuelCO2FactorKgPerKg, "Fuel CO2 factor");
      requireInputFiniteNonNegative(noxFactorKgPerGJ, "NOx factor");
      requireInputFinitePositive(stackTemperatureKelvin, "Stack temperature");
      if (!(crudeInletTemperatureKelvin < preheatTemperatureKelvin)) {
        throw new IllegalArgumentException("Crude inlet temperature must be below the preheat temperature");
      }

      this.crudeInletTemperatureKelvin = crudeInletTemperatureKelvin;
      this.preheatTemperatureKelvin = preheatTemperatureKelvin;
      this.preheaterPressureLossBara = preheaterPressureLossBara;
      this.furnacePressureLossBara = furnacePressureLossBara;
      this.thermalEfficiency = thermalEfficiency;
      this.fuelLowerHeatingValueJPerKg = fuelLowerHeatingValueJPerKg;
      this.fuelCO2FactorKgPerKg = fuelCO2FactorKgPerKg;
      this.noxFactorKgPerGJ = noxFactorKgPerGJ;
      this.stackTemperatureKelvin = stackTemperatureKelvin;
    }

    /** @return crude inlet temperature in kelvin */
    public double getCrudeInletTemperatureKelvin() {
      return crudeInletTemperatureKelvin;
    }

    /** @return preheater outlet temperature in kelvin */
    public double getPreheatTemperatureKelvin() {
      return preheatTemperatureKelvin;
    }

    /** @return preheater pressure loss in bar */
    public double getPreheaterPressureLossBara() {
      return preheaterPressureLossBara;
    }

    /** @return furnace pressure loss in bar */
    public double getFurnacePressureLossBara() {
      return furnacePressureLossBara;
    }

    /** @return fired-heater thermal efficiency */
    public double getThermalEfficiency() {
      return thermalEfficiency;
    }

    /** @return fuel lower heating value in J/kg */
    public double getFuelLowerHeatingValueJPerKg() {
      return fuelLowerHeatingValueJPerKg;
    }

    /** @return fuel CO2 factor in kg/kg */
    public double getFuelCO2FactorKgPerKg() {
      return fuelCO2FactorKgPerKg;
    }

    /** @return NOx factor in kg/GJ */
    public double getNoxFactorKgPerGJ() {
      return noxFactorKgPerGJ;
    }

    /** @return stack temperature in kelvin */
    public double getStackTemperatureKelvin() {
      return stackTemperatureKelvin;
    }

    /** @return total explicit preheater-plus-furnace pressure loss in bar */
    public double getTotalPressureLossBara() {
      return preheaterPressureLossBara + furnacePressureLossBara;
    }

    /** @return derived crude inlet pressure required to meet the published column-feed pressure, in bar absolute */
    public double getCrudeInletPressureBara() {
      return getPublishedColumnFeedPressureBara() + getTotalPressureLossBara();
    }

    private static void requireInputFinitePositive(double value, String label) {
      if (!Double.isFinite(value) || !(value > 0.0)) {
        throw new IllegalArgumentException(label + " must be finite and positive");
      }
    }

    private static void requireInputFiniteNonNegative(double value, String label) {
      if (!Double.isFinite(value) || value < 0.0) {
        throw new IllegalArgumentException(label + " must be finite and non-negative");
      }
    }
  }
}
