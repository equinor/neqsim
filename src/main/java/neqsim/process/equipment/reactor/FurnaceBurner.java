package neqsim.process.equipment.reactor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.FurnaceBurnerResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;

/**
 * FurnaceBurner mixes a fuel gas stream with combustion air and evaluates combustion using the
 * {@link GibbsReactor}. The unit supports adiabatic and cooled designs, returning the reacted
 * outlet stream together with emission estimates and heat release.
 */
public class FurnaceBurner extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1L;

  private StreamInterface fuelInlet;
  private StreamInterface airInlet;
  private StreamInterface outletStream;
  private GibbsReactor reactor;

  /** Burner design options controlling how the energy balance is handled. */
  public enum BurnerDesign {
    /** Fully adiabatic combustion (default). */
    ADIABATIC,
    /** Combustion with heat loss to surroundings represented by a cooling factor. */
    COOLED
  }

  private BurnerDesign burnerDesign = BurnerDesign.ADIABATIC;
  private double surroundingsTemperatureK = Double.NaN;
  private double coolingFactor = 0.0;

  private double excessAirFraction = 0.0;
  private double airFuelRatioMass = Double.NaN;
  private double flameTemperature = Double.NaN;
  private double heatReleasekW = Double.NaN;
  private final Map<String, Double> emissionRatesKgPerHr = new HashMap<>();

  /**
   * Create a furnace burner with the provided name.
   *
   * @param name unit name
   */
  public FurnaceBurner(String name) {
    super(name);
  }

  /**
   * Set the natural gas (fuel) inlet stream.
   *
   * @param stream fuel stream
   */
  public void setFuelInlet(StreamInterface stream) {
    this.fuelInlet = stream;
  }

  /**
   * Set the combustion air inlet stream.
   *
   * @param stream air stream
   */
  public void setAirInlet(StreamInterface stream) {
    this.airInlet = stream;
  }

  /**
   * Set the burner design option.
   *
   * @param design burner design
   */
  public void setBurnerDesign(BurnerDesign design) {
    this.burnerDesign = design == null ? BurnerDesign.ADIABATIC : design;
  }

  /**
   * Provide a cooling factor (0-1) that pulls the flame temperature towards the surroundings. A
   * value of 0.0 keeps adiabatic operation, while 1.0 forces the products to the surroundings
   * temperature.
   *
   * @param factor cooling factor between 0 and 1
   */
  public void setCoolingFactor(double factor) {
    this.coolingFactor = Math.max(0.0, Math.min(1.0, factor));
  }

  /**
   * Specify the surroundings (ambient) temperature used when applying cooling.
   *
   * @param temperatureK surroundings temperature in kelvin
   */
  public void setSurroundingsTemperature(double temperatureK) {
    this.surroundingsTemperatureK = temperatureK;
  }

  /**
   * Specify excess air as a fraction of stoichiometric air (0.1 = 10% excess).
   *
   * @param fraction fraction of excess air
   */
  public void setExcessAirFraction(double fraction) {
    this.excessAirFraction = Math.max(0.0, fraction);
  }

  /**
   * Set the air-fuel ratio on a mass basis. When set, the air flow rate will be calculated from the
   * fuel flow rate and this ratio during execution.
   *
   * @param ratio air-fuel ratio (mass air / mass fuel)
   */
  public void setAirFuelRatioMass(double ratio) {
    this.airFuelRatioMass = ratio;
  }

  /**
   * Get the air-fuel ratio on a mass basis.
   *
   * @return air-fuel ratio (mass air / mass fuel)
   */
  public double getAirFuelRatioMass() {
    return airFuelRatioMass;
  }

  /**
   * Get the natural gas (fuel) inlet stream.
   *
   * @return fuel stream
   */
  public StreamInterface getFuelInlet() {
    return fuelInlet;
  }

  /**
   * Get the combustion air inlet stream.
   *
   * @return air stream
   */
  public StreamInterface getAirInlet() {
    return airInlet;
  }

  /**
   * Get the reacted outlet stream from the furnace burner.
   *
   * @return reacted outlet stream
   */
  public StreamInterface getOutletStream() {
    return outletStream;
  }

  /**
   * Get the calculated flame temperature (K) after considering the burner design.
   *
   * @return flame temperature in kelvin
   */
  public double getFlameTemperature() {
    return flameTemperature;
  }

  /**
   * Get the heat released by combustion (kW, positive for exothermic reactions).
   *
   * @return heat release in kW
   */
  public double getHeatReleasekW() {
    return heatReleasekW;
  }

  /**
   * Get a map of emission rates (kg/hr) for common combustion species.
   *
   * @return emission rate map
   */
  public Map<String, Double> getEmissionRatesKgPerHr() {
    return new HashMap<>(emissionRatesKgPerHr);
  }

  @Override
  public void run(UUID id) {
    if (fuelInlet == null || airInlet == null) {
      throw new IllegalStateException("Fuel and air streams must be specified before running");
    }

    // Combine the fuel and air streams, scaling air for requested excess
    SystemInterface fuelSystem = fuelInlet.getThermoSystem().clone();
    SystemInterface airSystem = airInlet.getThermoSystem().clone();

    // If air-fuel ratio is set, calculate air flow rate from fuel flow rate
    if (!Double.isNaN(airFuelRatioMass)) {
      double fuelMassRate = fuelSystem.getFlowRate("kg/sec");
      double requiredAirMassRate = fuelMassRate * airFuelRatioMass;
      airSystem.setTotalFlowRate(requiredAirMassRate, "kg/sec");
      airSystem.init(3);
    }
    if (excessAirFraction > 0.0) {
      double scaledAirFlow = airSystem.getFlowRate("mole/sec") * (1.0 + excessAirFraction);
      airSystem.setTotalFlowRate(scaledAirFlow, "mole/sec");
      airSystem.init(3);
    }

    fuelSystem.addFluid(airSystem);
    fuelSystem.createDatabase(true);
    String[] tracked =
        {"CO2", "CO", "NO", "NO2", "SO2", "SO3", "H2S", "oxygen", "water", "nitrogen"};
    for (String compName : tracked) {
      if (!fuelSystem.hasComponent(compName)) {
        fuelSystem.addComponent(compName, 0.0, "mole/sec");
      }
    }
    fuelSystem.init(3);

    SystemInterface inletBasis = fuelSystem.clone();
    inletBasis.init(3);
    double inletEnthalpyJ = inletBasis.getEnthalpy("J");

    Stream mixedStream = new Stream(getName() + " mixture", fuelSystem);
    mixedStream.run(id);

    // First perform an adiabatic equilibrium to establish baseline combustion
    if (reactor == null) {
      reactor = new GibbsReactor(getName() + " Gibbs reactor", mixedStream);
    } else {
      reactor.setInletStream(mixedStream);
    }
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(1e-4);
    
  
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
    reactor.run(id);

    outletStream = reactor.getOutletStream();
    outletStream.run(id);

    SystemInterface outletSystem = outletStream.getThermoSystem();
    flameTemperature = outletSystem.getTemperature();
    heatReleasekW = Math.abs(reactor.getEnthalpyOfReactions());

    boolean applyCooling = burnerDesign == BurnerDesign.COOLED || coolingFactor > 0.0;
    if (applyCooling) {
      double ambientTemp = Double.isNaN(surroundingsTemperatureK) ? airSystem.getTemperature()
          : surroundingsTemperatureK;
      double cooledTemperature =
          ambientTemp + (flameTemperature - ambientTemp) * (1.0 - coolingFactor);

      SystemInterface cooledFeed = mixedStream.getThermoSystem().clone();
      cooledFeed.setTemperature(cooledTemperature);
      cooledFeed.init(3);

      Stream cooledStream = new Stream(getName() + " cooled mixture", cooledFeed);
      cooledStream.run(id);

      GibbsReactor cooledReactor =
          new GibbsReactor(getName() + " cooled Gibbs reactor", cooledStream);
      cooledReactor.setUseAllDatabaseSpecies(false);
      cooledReactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
      cooledReactor.run(id);

      outletStream = cooledReactor.getOutletStream();
      outletStream.run(id);
      outletSystem = outletStream.getThermoSystem();

      flameTemperature = outletSystem.getTemperature();

      double outletEnthalpyJ = outletSystem.getEnthalpy("J");
      heatReleasekW = Math.abs(inletEnthalpyJ - outletEnthalpyJ) / 1000.0;
    }

    emissionRatesKgPerHr.clear();
    for (String compName : tracked) {
      if (outletSystem.hasComponent(compName)) {
        double rate = outletSystem.getComponent(compName).getFlowRate("kg/sec") * 3600.0;
        emissionRatesKgPerHr.put(compName, rate);
      }
    }
    if (emissionRatesKgPerHr.containsKey("NO") || emissionRatesKgPerHr.containsKey("NO2")) {
      double nox = emissionRatesKgPerHr.getOrDefault("NO", 0.0)
          + emissionRatesKgPerHr.getOrDefault("NO2", 0.0);
      emissionRatesKgPerHr.put("NOx", nox);
    }

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    if (fuelInlet == null || airInlet == null || outletStream == null) {
      return Double.NaN;
    }

    double inletMass = fuelInlet.getFlowRate(unit) + airInlet.getFlowRate(unit);
    return outletStream.getFlowRate(unit) - inletMass;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new FurnaceBurnerResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    FurnaceBurnerResponse res = new FurnaceBurnerResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
  }
}
