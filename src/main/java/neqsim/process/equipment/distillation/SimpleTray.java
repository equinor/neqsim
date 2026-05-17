package neqsim.process.equipment.distillation;

import java.util.List;
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
   * When {@code true}, the tray uses reactive flash (Modified RAND, simultaneous
   * chemical + phase
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

  /**
   * Replace all tray inlet streams with the supplied external streams.
   *
   * @param externalStreams external feed streams to keep on the tray
   */
  void resetInputStreams(List<StreamInterface> externalStreams) {
    while (getNumberOfInputStreams() > 0) {
      removeInputStream(getNumberOfInputStreams() - 1);
    }
    for (StreamInterface externalStream : externalStreams) {
      addStream(externalStream);
    }
    invalidateOutStreamCache();
  }

  /** {@inheritDoc} */
  @Override
  public void setHeatInput(double heatinp) {
    this.heatInput = heatinp;
    invalidateOutStreamCache();
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
    invalidateOutStreamCache();
    super.run();
    temperature = mixedStream.getTemperature();
  }

  /**
   * <p>
   * TPflash.
   * </p>
   */
  public void TPflash() {
  }

  /**
   * Enable or disable reactive flash on this tray.
   *
   * @param useReactiveFlash {@code true} to use reactive (chemical + phase)
   *                         equilibrium
   */
  public void setUseReactiveFlash(boolean useReactiveFlash) {
    this.useReactiveFlash = useReactiveFlash;
    invalidateOutStreamCache();
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
      mixedStream.getThermoSystem().initProperties();
    } else {
      try {
        if (useReactiveFlash) {
          testOps.reactivePHflash(enthalpy, 0);
        } else {
          testOps.PHflash(enthalpy, 0);
        }
        mixedStream.getThermoSystem().initProperties();
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
          mixedStream.getThermoSystem().initProperties();
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
   * Invalidate the cached gas and liquid output streams. Call this after
   * modifying the tray's
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
      cachedGasOutStream = createPhaseOutStream("gas");
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
      cachedLiquidOutStream = createLiquidOutStream();
    }
    return cachedLiquidOutStream;
  }

  /**
   * Create the tray gas outlet from the requested phase type and normalize its
   * inventory.
   *
   * @param phaseTypeName phase type name to prefer
   * @return stream containing the selected normalized phase
   */
  private StreamInterface createPhaseOutStream(String phaseTypeName) {
    SystemInterface traySystem = mixedStream.getThermoSystem();
    int phaseIndex = findPhaseIndex(phaseTypeName);
    if (phaseIndex < 0) {
      return createZeroOutStream(traySystem);
    }
    SystemInterface phaseSystem = createPhaseSystemSafely(traySystem, phaseIndex);
    if (phaseSystem == null) {
      return createZeroOutStream(traySystem);
    }
    return new Stream("", phaseSystem);
  }

  /**
   * Create the tray liquid outlet from the liquid or oil phase and normalize its
   * inventory.
   *
   * @return stream containing the selected normalized liquid phase
   */
  private StreamInterface createLiquidOutStream() {
    SystemInterface traySystem = mixedStream.getThermoSystem();
    int phaseIndex = findLiquidPhaseIndex();
    if (phaseIndex < 0) {
      return createZeroOutStream(traySystem);
    }
    SystemInterface phaseSystem = createPhaseSystemSafely(traySystem, phaseIndex);
    if (phaseSystem == null) {
      return createZeroOutStream(traySystem);
    }
    return new Stream("", phaseSystem);
  }

  /**
   * Create a zero-flow outlet stream when the requested phase is absent on the
   * tray.
   *
   * @param traySystem tray thermodynamic system used as a composition template
   * @return stream with zero total moles and zero flow
   */
  private StreamInterface createZeroOutStream(SystemInterface traySystem) {
    SystemInterface zeroSystem = traySystem.clone();
    zeroSystem.setNumberOfPhases(1);
    scalePhaseSystemToNormalizedMoles(zeroSystem, 0.0);
    return new Stream("", zeroSystem);
  }

  /**
   * Create a phase system without allowing invalid phase roots to escape the tray
   * solve.
   *
   * @param traySystem tray thermodynamic system
   * @param phaseIndex phase index to extract
   * @return extracted phase system, or {@code null} if phase extraction fails
   */
  private SystemInterface createPhaseSystemSafely(SystemInterface traySystem, int phaseIndex) {
    try {
      return traySystem.phaseToSystem(phaseIndex);
    } catch (RuntimeException ex) {
      logger.debug("Phase extraction failed for tray {} phase {}", getName(), phaseIndex, ex);
      return null;
    }
  }

  /**
   * Find the phase index with the requested type.
   *
   * @param phaseTypeName phase type name to find
   * @return phase index within the tray system, or {@code -1} when absent
   */
  private int findPhaseIndex(String phaseTypeName) {
    SystemInterface traySystem = mixedStream.getThermoSystem();
    int numberOfPhases = Math.max(1, traySystem.getNumberOfPhases());
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      if (phaseTypeName.equals(traySystem.getPhase(phaseIndex).getPhaseTypeName())) {
        return phaseIndex;
      }
    }
    return -1;
  }

  /**
   * Find the liquid phase index, accepting both liquid and oil phase names.
   *
   * @return liquid phase index within the tray system, or {@code -1} when absent
   */
  private int findLiquidPhaseIndex() {
    SystemInterface traySystem = mixedStream.getThermoSystem();
    int numberOfPhases = Math.max(1, traySystem.getNumberOfPhases());
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      String phaseTypeName = traySystem.getPhase(phaseIndex).getPhaseTypeName();
      if ("liquid".equals(phaseTypeName) || "oil".equals(phaseTypeName)) {
        return phaseIndex;
      }
    }
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      if (!"gas".equals(traySystem.getPhase(phaseIndex).getPhaseTypeName())) {
        return phaseIndex;
      }
    }
    return -1;
  }

  /**
   * Scale a single-phase outlet system to the target number of moles.
   *
   * @param phaseSystem single-phase outlet system to scale
   * @param targetMoles target total moles for the outlet system
   */
  private void scalePhaseSystemToNormalizedMoles(SystemInterface phaseSystem, double targetMoles) {
    double currentMoles = phaseSystem.getTotalNumberOfMoles();
    if (!Double.isFinite(currentMoles) || currentMoles <= 0.0 || !Double.isFinite(targetMoles)) {
      phaseSystem.setTotalNumberOfMoles(0.0);
      return;
    }
    if (targetMoles <= 0.0) {
      for (int phaseIndex = 0; phaseIndex < phaseSystem.getMaxNumberOfPhases(); phaseIndex++) {
        for (int componentIndex = 0; componentIndex < phaseSystem.getPhase(phaseIndex)
            .getNumberOfComponents(); componentIndex++) {
          phaseSystem.getPhase(phaseIndex).getComponent(componentIndex)
              .setNumberOfMolesInPhase(0.0);
          phaseSystem.getPhase(phaseIndex).getComponent(componentIndex).setNumberOfmoles(0.0);
        }
      }
      phaseSystem.setTotalNumberOfMoles(0.0);
      phaseSystem.init(0);
      return;
    }
    double scaleFactor = Math.max(0.0, targetMoles) / currentMoles;
    for (int phaseIndex = 0; phaseIndex < phaseSystem.getMaxNumberOfPhases(); phaseIndex++) {
      for (int componentIndex = 0; componentIndex < phaseSystem.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        double moles = phaseSystem.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase()
            * scaleFactor;
        phaseSystem.getPhase(phaseIndex).getComponent(componentIndex)
            .setNumberOfMolesInPhase(moles);
        phaseSystem.getPhase(phaseIndex).getComponent(componentIndex).setNumberOfmoles(moles);
      }
    }
    phaseSystem.setTotalNumberOfMoles(Math.max(0.0, targetMoles));
    phaseSystem.init(0);
    phaseSystem.init(1);
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
    invalidateOutStreamCache();
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temperature) {
    this.temperature = temperature;
    invalidateOutStreamCache();
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
    int liquidPhaseIndex = findLiquidPhaseIndex();
    if (liquidPhaseIndex >= 0) {
      return getFluid().getPhase(liquidPhaseIndex).getFlowRate(unit);
    }
    return 0.0;
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
