package neqsim.process.util.mercury;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.mercury.MercuryPhaseEquilibrium;

/**
 * Mercury in one process stream: total flow, distribution between the fluid phases and separate mercury phase.
 *
 * <p>
 * The values are calculated from the stream fluid with {@link MercuryPhaseEquilibrium} at the temperature and pressure
 * of the stream. A stream that holds more mercury than its gas, oil and aqueous phases dissolve carries the rest as
 * free mercury, liquid above 234.321 K and solid below. Free mercury below 0.1 % of the mercury in the stream is not
 * reported.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class MercuryStreamResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Status of a stream with a mercury distribution. */
  public static final String STATUS_OK = "ok";

  /** Status of a stream without flow. */
  public static final String STATUS_NO_FLOW = "no flow";

  /** Status of a stream without mercury. */
  public static final String STATUS_NO_MERCURY = "no mercury";

  /** Start of the status of a stream where the mercury distribution could not be calculated. */
  public static final String STATUS_FAILED = "failed";

  /** Mass flow in kg/sec below which a stream is treated as empty. */
  private static final double MINIMUM_MASS_FLOW = 1.0e-12;

  /**
   * Fraction of the mercury in a stream below which free mercury is not reported. A stream that leaves a separator
   * saturated with mercury shows free mercury of up to a few times 1e-4 of its mercury when it is flashed again, from
   * the tolerance of the flash calculations.
   */
  private static final double FREE_MERCURY_TOLERANCE = 1.0e-3;

  /** Unique name of the stream in the report. */
  private final String label;

  /** Name of the stream. */
  private final String streamName;

  /** Name of the unit operation that the stream leaves, empty for a feed stream. */
  private final String sourceUnit;

  /** True when no unit operation in the process produces the stream. */
  private final boolean feed;

  /** True when no unit operation in the process uses the stream. */
  private final boolean product;

  /** Status of the calculation. */
  private String status = STATUS_OK;

  /** Temperature in kelvin. */
  private double temperature = Double.NaN;

  /** Pressure in bara. */
  private double pressure = Double.NaN;

  /** Total mass flow in kg/sec. */
  private double massFlow = 0.0;

  /** Molar mass of mercury in kg/mol. */
  private double mercuryMolarMass = 0.20059;

  /** Total mercury flow in mole/sec. */
  private double mercuryMoles = 0.0;

  /** Flow of free mercury in mole/sec. */
  private double freeMercuryMoles = 0.0;

  /** State of the free mercury: none, liquid or solid. */
  private String freeMercuryState = "none";

  /** Fugacity of mercury with all mercury dissolved divided by the fugacity of pure condensed mercury. */
  private double saturationRatio = 0.0;

  /** Type of each fluid phase. */
  private String[] phaseTypes = new String[0];

  /** Molar mass of each fluid phase in kg/mol. */
  private double[] phaseMolarMasses = new double[0];

  /** Mole fraction of mercury in each fluid phase. */
  private double[] mercuryMoleFractions = new double[0];

  /** Flow of dissolved mercury in each fluid phase in mole/sec. */
  private double[] phaseMercuryMoles = new double[0];

  /**
   * Calculate the mercury distribution of a stream.
   *
   * @param label unique name of the stream in the report
   * @param sourceUnit name of the unit operation that the stream leaves, empty for a feed stream
   * @param feed true when no unit operation in the process produces the stream
   * @param product true when no unit operation in the process uses the stream
   * @param stream calculated process stream
   */
  MercuryStreamResult(String label, String sourceUnit, boolean feed, boolean product, StreamInterface stream) {
    this.label = label;
    this.streamName = stream.getName();
    this.sourceUnit = sourceUnit;
    this.feed = feed;
    this.product = product;
    try {
      calculate(stream);
    } catch (RuntimeException ex) {
      status = STATUS_FAILED + ": " + ex.getMessage();
    }
  }

  /**
   * Read the stream and calculate the mercury distribution.
   *
   * @param stream calculated process stream
   */
  private void calculate(StreamInterface stream) {
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      status = STATUS_NO_FLOW;
      return;
    }
    temperature = fluid.getTemperature();
    pressure = fluid.getPressure();
    massFlow = stream.getFlowRate("kg/sec");
    if (!(massFlow > MINIMUM_MASS_FLOW)) {
      massFlow = 0.0;
      status = STATUS_NO_FLOW;
      return;
    }
    if (!fluid.getPhase(0).hasComponent(MercuryPhaseEquilibrium.MERCURY)) {
      status = STATUS_NO_MERCURY;
      return;
    }
    mercuryMolarMass = fluid.getPhase(0).getComponent(MercuryPhaseEquilibrium.MERCURY).getMolarMass();
    mercuryMoles = fluid.getPhase(0).getComponent(MercuryPhaseEquilibrium.MERCURY).getNumberOfmoles();
    if (!(mercuryMoles > 0.0)) {
      mercuryMoles = 0.0;
      status = STATUS_NO_MERCURY;
      return;
    }
    MercuryPhaseEquilibrium equilibrium = new MercuryPhaseEquilibrium(fluid);
    equilibrium.run();
    SystemInterface phases = equilibrium.getFluid();
    int numberOfPhases = phases.getNumberOfPhases();
    phaseTypes = new String[numberOfPhases];
    phaseMolarMasses = new double[numberOfPhases];
    mercuryMoleFractions = new double[numberOfPhases];
    phaseMercuryMoles = new double[numberOfPhases];
    for (int p = 0; p < numberOfPhases; p++) {
      PhaseInterface phase = phases.getPhase(p);
      phaseTypes[p] = phase.getType().getDesc();
      phaseMolarMasses[p] = phase.getMolarMass();
      mercuryMoleFractions[p] = phase.getComponent(MercuryPhaseEquilibrium.MERCURY).getx();
      phaseMercuryMoles[p] = phase.getComponent(MercuryPhaseEquilibrium.MERCURY).getNumberOfMolesInPhase();
    }
    saturationRatio = equilibrium.getSaturationRatio();
    freeMercuryMoles = equilibrium.getFreeMercuryMoles();
    if (freeMercuryMoles > FREE_MERCURY_TOLERANCE * mercuryMoles) {
      freeMercuryState = equilibrium.getFreeMercuryState();
    } else {
      freeMercuryMoles = 0.0;
    }
  }

  /**
   * Get the unique name of the stream in the report.
   *
   * @return the stream name, with the name of the unit operation it leaves in front when several streams share the name
   */
  public String getLabel() {
    return label;
  }

  /**
   * Get the name of the stream.
   *
   * @return stream name as given in the process
   */
  public String getStreamName() {
    return streamName;
  }

  /**
   * Get the unit operation that the stream leaves.
   *
   * @return name of the unit operation, empty for a feed stream
   */
  public String getSourceUnit() {
    return sourceUnit;
  }

  /**
   * Check whether the stream is a feed to the process.
   *
   * @return true when no unit operation in the process produces the stream
   */
  public boolean isFeed() {
    return feed;
  }

  /**
   * Check whether the stream is a product of the process.
   *
   * @return true when no unit operation in the process uses the stream
   */
  public boolean isProduct() {
    return product;
  }

  /**
   * Get the status of the calculation.
   *
   * @return {@link #STATUS_OK}, {@link #STATUS_NO_FLOW}, {@link #STATUS_NO_MERCURY} or a text starting with
   * {@link #STATUS_FAILED}
   */
  public String getStatus() {
    return status;
  }

  /**
   * Get the temperature of the stream.
   *
   * @return temperature in kelvin, NaN for a stream without fluid
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Get the pressure of the stream.
   *
   * @return pressure in bara, NaN for a stream without fluid
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Get the total mass flow of the stream.
   *
   * @return mass flow in kg/hr
   */
  public double getMassFlowRate() {
    return massFlow * 3600.0;
  }

  /**
   * Get the total flow of mercury in the stream, dissolved and free.
   *
   * @param unit "mole/sec", "mole/hr", "kg/sec", "kg/min", "kg/hr", "kg/day", "kg/yr", "g/sec", "g/hr", "g/day" or
   * "g/yr"
   * @return flow of mercury
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getMercuryFlowRate(String unit) {
    return MercuryPhaseEquilibrium.convertMercuryFlow(mercuryMoles, mercuryMolarMass, unit);
  }

  /**
   * Get the flow of free mercury carried by the stream.
   *
   * @param unit flow unit, see {@link #getMercuryFlowRate(String)}
   * @return flow of mercury that the fluid phases do not dissolve
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getFreeMercuryFlowRate(String unit) {
    return MercuryPhaseEquilibrium.convertMercuryFlow(freeMercuryMoles, mercuryMolarMass, unit);
  }

  /**
   * Get the flow of mercury dissolved in the fluid phases of the stream.
   *
   * @param unit flow unit, see {@link #getMercuryFlowRate(String)}
   * @return flow of dissolved mercury
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getDissolvedMercuryFlowRate(String unit) {
    return MercuryPhaseEquilibrium.convertMercuryFlow(mercuryMoles - freeMercuryMoles, mercuryMolarMass, unit);
  }

  /**
   * Check whether the stream carries free mercury.
   *
   * @return true when the stream holds more mercury than its fluid phases dissolve
   */
  public boolean hasFreeMercury() {
    return freeMercuryMoles > 0.0;
  }

  /**
   * Get the state of the free mercury.
   *
   * @return "none", "liquid" or "solid"
   */
  public String getFreeMercuryState() {
    return freeMercuryState;
  }

  /**
   * Get the degree of mercury saturation of the stream.
   *
   * @return fugacity of mercury with all mercury dissolved divided by the fugacity of pure condensed mercury; above one
   * means free mercury, zero for a stream without mercury
   */
  public double getSaturationRatio() {
    return saturationRatio;
  }

  /**
   * Get the number of fluid phases in the stream.
   *
   * @return number of gas, oil and aqueous phases; zero when no mercury distribution was calculated
   */
  public int getNumberOfPhases() {
    return phaseTypes.length;
  }

  /**
   * Get the type of a fluid phase.
   *
   * @param phase phase number from zero
   * @return "gas", "oil", "aqueous" or "liquid"
   */
  public String getPhaseType(int phase) {
    return phaseTypes[phase];
  }

  /**
   * Find a fluid phase by type.
   *
   * @param phaseType "gas", "oil", "aqueous" or "liquid"
   * @return phase number from zero, or -1 when the stream has no such phase
   */
  public int getPhaseNumber(String phaseType) {
    for (int p = 0; p < phaseTypes.length; p++) {
      if (phaseTypes[p].equalsIgnoreCase(phaseType)) {
        return p;
      }
    }
    return -1;
  }

  /**
   * Get the mole fraction of mercury dissolved in a fluid phase.
   *
   * @param phase phase number from zero
   * @return mole fraction of mercury
   */
  public double getMercuryMoleFraction(int phase) {
    return mercuryMoleFractions[phase];
  }

  /**
   * Get the flow of mercury dissolved in a fluid phase.
   *
   * @param phase phase number from zero
   * @param unit flow unit, see {@link #getMercuryFlowRate(String)}
   * @return flow of dissolved mercury in the phase
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getPhaseMercuryFlowRate(int phase, String unit) {
    return MercuryPhaseEquilibrium.convertMercuryFlow(phaseMercuryMoles[phase], mercuryMolarMass, unit);
  }

  /**
   * Get the concentration of mercury dissolved in a fluid phase.
   *
   * @param phase phase number from zero
   * @param unit "ug/Sm3" (per standard cubic metre of ideal gas at 15 C and 1.01325 bara), "ug/Nm3" (0 C and 1.01325
   * bara), "ug/kg" (per kilogram of the phase) or "ppb" (mole fraction times 1e9)
   * @return mercury concentration
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getMercuryConcentration(int phase, String unit) {
    return MercuryPhaseEquilibrium.convertMercuryConcentration(mercuryMoleFractions[phase], mercuryMolarMass,
        phaseMolarMasses[phase], unit);
  }

  /**
   * Get the concentration of mercury dissolved in the fluid phase of a given type.
   *
   * @param phaseType "gas", "oil", "aqueous" or "liquid"
   * @param unit concentration unit, see {@link #getMercuryConcentration(int, String)}
   * @return mercury concentration, NaN when the stream has no such phase
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getMercuryConcentration(String phaseType, String unit) {
    int phase = getPhaseNumber(phaseType);
    return phase < 0 ? Double.NaN : getMercuryConcentration(phase, unit);
  }

  /**
   * Get the result as a map for reporting.
   *
   * @return ordered map with flows in g/day, gas concentrations in ug/Sm3 and liquid concentrations in ug/kg
   */
  Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("stream", label);
    map.put("sourceUnit", sourceUnit);
    map.put("feed", feed);
    map.put("product", product);
    map.put("status", status);
    map.put("temperature_C", temperature - 273.15);
    map.put("pressure_bara", pressure);
    map.put("massFlow_kg_hr", getMassFlowRate());
    map.put("mercuryFlow_g_day", getMercuryFlowRate("g/day"));
    map.put("saturationRatio", saturationRatio);
    map.put("freeMercuryState", freeMercuryState);
    map.put("freeMercuryFlow_g_day", getFreeMercuryFlowRate("g/day"));
    Map<String, Object> phases = new LinkedHashMap<String, Object>();
    for (int p = 0; p < phaseTypes.length; p++) {
      Map<String, Object> phase = new LinkedHashMap<String, Object>();
      phase.put("mercuryMoleFraction", mercuryMoleFractions[p]);
      phase.put("mercuryFlow_g_day", getPhaseMercuryFlowRate(p, "g/day"));
      if ("gas".equals(phaseTypes[p])) {
        phase.put("mercury_ug_Sm3", getMercuryConcentration(p, "ug/Sm3"));
      } else {
        phase.put("mercury_ug_kg", getMercuryConcentration(p, "ug/kg"));
      }
      phases.put(phaseTypes[p], phase);
    }
    map.put("phases", phases);
    return map;
  }
}
