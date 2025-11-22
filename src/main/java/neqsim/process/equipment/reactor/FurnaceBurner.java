package neqsim.process.equipment.reactor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * FurnaceBurner mixes a fuel gas stream with combustion air and evaluates adiabatic combustion
 * using the {@link GibbsReactor}. The unit returns the reacted outlet stream together with simple
 * emission estimates and heat release.
 */
public class FurnaceBurner extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1L;

  private StreamInterface fuelInlet;
  private StreamInterface airInlet;
  private StreamInterface outletStream;
  private GibbsReactor reactor;

  private double excessAirFraction = 0.0;
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
   * Specify excess air as a fraction of stoichiometric air (0.1 = 10% excess).
   *
   * @param fraction fraction of excess air
   */
  public void setExcessAirFraction(double fraction) {
    this.excessAirFraction = Math.max(0.0, fraction);
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
   * Get the calculated adiabatic flame temperature (K).
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
    if (excessAirFraction > 0.0) {
      double scaledAirFlow = airSystem.getFlowRate("mole/sec") * (1.0 + excessAirFraction);
      airSystem.setTotalFlowRate(scaledAirFlow, "mole/sec");
      airSystem.init(3);
    }

    fuelSystem.addFluid(airSystem);
    fuelSystem.createDatabase(true);
    String[] tracked = {"CO2", "CO", "NO", "NO2", "oxygen", "water", "nitrogen"};
    for (String compName : tracked) {
      if (!fuelSystem.hasComponent(compName)) {
        fuelSystem.addComponent(compName, 0.0, "mole/sec");
      }
    }
    fuelSystem.init(3);

    Stream mixedStream = new Stream(getName() + " mixture", fuelSystem);
    mixedStream.run(id);

    if (reactor == null) {
      reactor = new GibbsReactor(getName() + " Gibbs reactor", mixedStream);
    } else {
      reactor.setInletStream(mixedStream);
    }
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
    reactor.run(id);

    outletStream = reactor.getOutletStream();
    outletStream.run(id);

    SystemInterface outletSystem = outletStream.getThermoSystem();
    flameTemperature = outletSystem.getTemperature();
    heatReleasekW = -reactor.getEnthalpyOfReactions();

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
}
