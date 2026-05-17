package neqsim.process.equipment.distillation;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * SimpleTray class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SimpleTray extends neqsim.process.equipment.mixer.Mixer implements TrayInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SimpleTray.class);

  double heatInput = 0.0;
  private double temperature = Double.NaN;

  /** Tray operating pressure in bara. Negative means use stream pressure. */
  protected double trayPressure = -1.0;

  /**
   * When {@code true}, the tray uses reactive flash (Modified RAND, simultaneous chemical + phase
   * equilibrium) instead of standard VLE flash. Set via
   * {@link DistillationColumn#setReactive(boolean)}.
   */
  private boolean useReactiveFlash = false;

  /** Cached gas out stream, invalidated when run() is called. */
  private transient StreamInterface cachedGasOutStream = null;
  /** Cached liquid out stream, invalidated when run() is called. */
  private transient StreamInterface cachedLiquidOutStream = null;

  /**
   * <p>
   * Constructor for SimpleTray.
   * </p>
   *
   * @param name name of tray
   */
  public SimpleTray(String name) {
    super(name);
  }

  /**
   * <p>
   * init.
   * </p>
   */
  public void init() {
    int pp = 0;
    if (streams.size() == 3) {
      pp = 1;
    }
    for (int k = pp; k < streams.size(); k++) {
      (streams.get(k).getThermoSystem()).setTemperature(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setHeatInput(double heatinp) {
    this.heatInput = heatinp;
  }

  /**
   * <p>
   * calcMixStreamEnthalpy0.
   * </p>
   *
   * @return a double
   */
  public double calcMixStreamEnthalpy0() {
    double enthalpy = 0;

    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().init(3);
      enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      // System.out.println("total enthalpy k : " + ( ((Stream)
      // streams.get(k)).getThermoSystem()).getEnthalpy());
    }
    // System.out.println("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public double calcMixStreamEnthalpy() {
    double enthalpy = heatInput;
    if (isSetEnergyStream()) {
      enthalpy -= energyStream.getDuty();
    }

    for (int k = 0; k < streams.size(); k++) {
      if (streams.get(k).getFlowRate("kg/hr") > getMinimumFlow()) {
        streams.get(k).getThermoSystem().init(3);
        enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      }
      // System.out.println("total enthalpy k : " + ( ((Stream)
      // streams.get(k)).getThermoSystem()).getEnthalpy());
    }
    // System.out.println("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }

  /**
   * <p>
   * run2.
   * </p>
   */
  public void run2() {
    super.run();
    temperature = mixedStream.getTemperature();
  }

  /**
   * <p>
   * TPflash.
   * </p>
   */
  public void TPflash() {}

  /**
   * Enable or disable reactive flash on this tray.
   *
   * @param useReactiveFlash {@code true} to use reactive (chemical + phase) equilibrium
   */
  public void setUseReactiveFlash(boolean useReactiveFlash) {
    this.useReactiveFlash = useReactiveFlash;
  }

  /**
   * Check whether this tray uses reactive flash.
   *
   * @return {@code true} if reactive flash is enabled
   */
  public boolean isUseReactiveFlash() {
    return useReactiveFlash;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    cachedGasOutStream = null;
    cachedLiquidOutStream = null;
    double enthalpy = 0.0;
    // double flowRate = ((Stream)
    // streams.get(0)).getThermoSystem().getFlowRate("kg/hr");
    // ((Stream) streams.get(0)).getThermoSystem().display();
    boolean changeTo2Phase = false;
    SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
    if (thermoSystem2.doMultiPhaseCheck()) {
      changeTo2Phase = true;
      thermoSystem2.setMultiPhaseCheck(false);
    }
    // System.out.println("total number of moles " +
    // thermoSystem2.getTotalNumberOfMoles());
    if (trayPressure > 0)

    {
      thermoSystem2.setPressure(trayPressure);
    }
    mixedStream.setThermoSystem(thermoSystem2);
    // thermoSystem2.display();
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
    if (streams.size() > 0) {
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().init(0);

      mixStream();
      if (trayPressure > 0) {
        mixedStream.setPressure(trayPressure, "bara");
      }
      enthalpy = calcMixStreamEnthalpy();
      // System.out.println("temp guess " + guessTemperature());
      if (!isSetOutTemperature()) {
        // mixedStream.getThermoSystem().setTemperature(guessTemperature());
      } else {
        mixedStream.setTemperature(getOutTemperature(), "K");
      }
      // System.out.println("filan temp " + mixedStream.getTemperature());
    }
    if (isSetOutTemperature()) {
      if (!Double.isNaN(getOutTemperature())) {
        mixedStream.getThermoSystem().setTemperature(getOutTemperature());
      }
      if (useReactiveFlash) {
        testOps.reactiveTPflash();
      } else {
        testOps.TPflash();
      }
      mixedStream.getThermoSystem().init(2);
    } else {
      try {
        if (useReactiveFlash) {
          testOps.reactivePHflash(enthalpy, 0);
        } else {
          testOps.PHflash(enthalpy, 0);
        }
      } catch (Exception ex) {
        try {
          if (!Double.isNaN(getOutTemperature())) {
            mixedStream.getThermoSystem().setTemperature(getOutTemperature());
          }
          if (useReactiveFlash) {
            testOps.reactiveTPflash();
          } else {
            testOps.TPflash();
          }
        } catch (Exception ex2) {
          logger.warn("TPflash failed in SimpleTray: " + getName(), ex2);
        }
      }
    }

    if (Double.isNaN(mixedStream.getTemperature())) {
      if (!Double.isNaN(getOutTemperature())) {
        mixedStream.setTemperature(getOutTemperature());
      }
    }

    setTemperature(mixedStream.getTemperature());
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);

    if (mixedStream.getFluid().getNumberOfPhases() >= 3) {
      System.out
          .println("error...." + mixedStream.getFluid().getNumberOfPhases() + " phases on tray");
      logger.warn("error...." + mixedStream.getFluid().getNumberOfPhases() + " phases on tray");
    }

    if (changeTo2Phase) {
      thermoSystem2.setMultiPhaseCheck(true);
    }
  }

  /**
   * Invalidate the cached gas and liquid output streams. Call this after modifying the tray's
   * thermo system compositions externally (e.g. Murphree efficiency correction).
   */
  public void invalidateOutStreamCache() {
    cachedGasOutStream = null;
    cachedLiquidOutStream = null;
  }

  /**
   * Set a pre-built gas out stream (e.g. Murphree-corrected) to be returned by
   * {@link #getGasOutStream()} instead of the equilibrium result.
   *
   * @param stream the corrected gas stream
   */
  public void setCachedGasOutStream(StreamInterface stream) {
    this.cachedGasOutStream = stream;
  }

  /**
   * Set a pre-built liquid out stream (e.g. Murphree-corrected) to be returned by
   * {@link #getLiquidOutStream()} instead of the equilibrium result.
   *
   * @param stream the corrected liquid stream
   */
  public void setCachedLiquidOutStream(StreamInterface stream) {
    this.cachedLiquidOutStream = stream;
  }

  /**
   * <p>
   * getGasOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getGasOutStream() {
    if (cachedGasOutStream == null) {
      cachedGasOutStream = new Stream("", mixedStream.getThermoSystem().phaseToSystem(0));
    }
    return cachedGasOutStream;
  }

  /**
   * <p>
   * getLiquidOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getLiquidOutStream() {
    if (cachedLiquidOutStream == null) {
      cachedLiquidOutStream = new Stream("", mixedStream.getThermoSystem().phaseToSystem(1));
    }
    return cachedLiquidOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    return temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pres) {
    trayPressure = pres;
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double guessTemperature() {
    if (Double.isNaN(temperature)) {
      double gtemp = 0;
      for (int k = 0; k < streams.size(); k++) {
        gtemp += streams.get(k).getThermoSystem().getTemperature()
            * streams.get(k).getThermoSystem().getNumberOfMoles()
            / mixedStream.getThermoSystem().getNumberOfMoles();
      }
      // System.out.println("guess temperature " + gtemp);
      return gtemp;
    } else {
      // System.out.println("temperature " + temperature);
      return temperature;
    }
  }

  /**
   * <p>
   * getVaporFlowRate.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getVaporFlowRate(String unit) {
    if (getFluid().hasPhaseType("gas")) {
      return getFluid().getPhase("gas").getFlowRate(unit);
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * getLiquidFlowRate.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getLiquidFlowRate(String unit) {
    if (getFluid().hasPhaseType("aqueous") || getFluid().hasPhaseType("oil")) {
      return getFluid().getPhase(1).getFlowRate(unit);
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * getFeedRate.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getFeedRate(String unit) {
    double feed = 0.0;
    for (int j = 0; j < getNumberOfInputStreams(); j++) {
      feed += getStream(j).getFluid().getFlowRate("kg/hr");
    }
    return feed;
  }

  /**
   * <p>
   * massBalance.
   * </p>
   *
   * Calculates the mass balance by comparing the total mass input and output.
   *
   * @return the difference between mass input and mass output
   */
  public double massBalance() {
    double massInput = 0;
    double massOutput = 0;
    int numberOfInputStreams = getNumberOfInputStreams();
    for (int j = 0; j < numberOfInputStreams; j++) {
      massInput += getStream(j).getFluid().getFlowRate("kg/hr");
    }
    massOutput = getThermoSystem().getFlowRate("kg/hr");
    return massInput - massOutput;
  }
}
