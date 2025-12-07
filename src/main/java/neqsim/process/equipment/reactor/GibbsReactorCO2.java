package neqsim.process.equipment.reactor;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * A specialized Gibbs reactor for CO2/acid gas equilibrium calculations.
 *
 * <p>
 * This two-port equipment encapsulates the reaction sequence commonly used for modeling acid gas
 * systems containing CO2, H2S, SO2, and NOx compounds. The reactor automatically selects the
 * appropriate reaction pathway based on inlet stream composition:
 * </p>
 *
 * <ul>
 * <li><b>NO2 + H2S present:</b> Single reactor handles both species</li>
 * <li><b>Oxygen present:</b> Two-stage reaction (H2S oxidation followed by SO2 processing)</li>
 * <li><b>Otherwise:</b> Single reactor with SO2 as inert</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * Stream feed = new Stream("feed", thermoSystem);
 * GibbsReactorCO2 reactor = new GibbsReactorCO2("acid gas reactor", feed);
 * reactor.run();
 * Stream outlet = reactor.getOutletStream();
 * </pre>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class GibbsReactorCO2 extends TwoPortEquipment {

  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(GibbsReactorCO2.class);

  /** Threshold in ppm for considering a component present in the mixture. */
  private static final double PPM_THRESHOLD = 0.01;

  /** Default damping factor for composition updates during iteration. */
  private static final double DEFAULT_DAMPING = 0.01;

  /** Default maximum number of iterations for convergence. */
  private static final int DEFAULT_MAX_ITERATIONS = 15000;

  /** Default convergence tolerance for the reactor. */
  private static final double DEFAULT_TOLERANCE = 1e-3;

  /** Default inert components for CO2/acid gas systems. */
  private static final String[] DEFAULT_INERT_COMPONENTS =
      {"CO", "COS", "CO2", "ammonia", "hydrogen", "N2O3", "nitrogen", "N2H4", "N2O"};

  /**
   * Creates a new GibbsReactorCO2 with the specified name.
   *
   * @param name the equipment name
   */
  public GibbsReactorCO2(String name) {
    super(name);
  }

  /**
   * Creates a new GibbsReactorCO2 with the specified name and inlet stream.
   *
   * @param name the equipment name
   * @param inlet the inlet stream
   */
  public GibbsReactorCO2(String name, StreamInterface inlet) {
    super(name, inlet);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    StreamInterface inlet = getInletStream();
    if (inlet == null) {
      logger.warn("Cannot run GibbsReactorCO2 '{}': inlet stream is null", getName());
      return;
    }

    SystemInterface outletSystem = computeEquilibrium(inlet);

    if (outletSystem != null) {
      getOutletStream().setThermoSystem(outletSystem);
    }
    getOutletStream().run();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  /**
   * Computes the chemical equilibrium based on inlet composition.
   *
   * @param inlet the inlet stream
   * @return the equilibrated thermo system, or null if calculation fails
   */
  private SystemInterface computeEquilibrium(StreamInterface inlet) {
    double no2ppm = getComponentPpm(inlet, "NO2");
    double h2sppm = getComponentPpm(inlet, "H2S");
    double oxyppm = getComponentPpm(inlet, "oxygen");

    try {
      if (hasSignificantConcentration(no2ppm) && hasSignificantConcentration(h2sppm)) {
        return runSingleReactor(inlet);
      } else if (hasSignificantConcentration(oxyppm)) {
        return runTwoStageOxidation(inlet, no2ppm, h2sppm);
      } else {
        return runSingleReactorWithSO2Inert(inlet);
      }
    } catch (Exception e) {
      logger.error("Equilibrium calculation failed for reactor '{}': {}", getName(),
          e.getMessage());
      return null;
    }
  }

  /**
   * Runs a single-stage reactor for systems with both NO2 and H2S.
   *
   * @param inlet the inlet stream
   * @return the outlet thermo system
   */
  private SystemInterface runSingleReactor(StreamInterface inlet) {
    GibbsReactor reactor = createConfiguredReactor("Primary Reactor", inlet);
    reactor.run();
    return reactor.getOutletStream().getThermoSystem();
  }

  /**
   * Runs a single-stage reactor with SO2 set as inert.
   *
   * @param inlet the inlet stream
   * @return the outlet thermo system
   */
  private SystemInterface runSingleReactorWithSO2Inert(StreamInterface inlet) {
    GibbsReactor reactor = createConfiguredReactor("Primary Reactor", inlet);
    reactor.setComponentAsInert("SO2");
    reactor.run();
    return reactor.getOutletStream().getThermoSystem();
  }

  /**
   * Runs a two-stage oxidation sequence for systems containing oxygen.
   *
   * <p>
   * Stage 1: H2S oxidation reactions Stage 2: SO2 processing based on remaining oxygen
   * </p>
   *
   * @param inlet the inlet stream
   * @param no2ppm NO2 concentration in ppm
   * @param h2sppm H2S concentration in ppm (unused but kept for API consistency)
   * @return the outlet thermo system
   */
  private SystemInterface runTwoStageOxidation(StreamInterface inlet, double no2ppm,
      double h2sppm) {
    // Stage 1: H2S oxidation
    GibbsReactor h2sReactor = createH2SReactor(inlet, no2ppm);
    h2sReactor.run();

    // Stage 2: SO2 processing
    GibbsReactor so2Reactor = createSO2Reactor(h2sReactor.getOutletStream());
    so2Reactor.run();

    return so2Reactor.getOutletStream().getThermoSystem();
  }

  /**
   * Creates and configures a reactor for H2S oxidation (first stage).
   *
   * @param inlet the inlet stream
   * @param no2ppm NO2 concentration in ppm
   * @return configured H2S reactor
   */
  private GibbsReactor createH2SReactor(StreamInterface inlet, double no2ppm) {
    GibbsReactor reactor = createConfiguredReactor("H2S Oxidation Reactor", inlet);

    // Additional inerts for H2S oxidation stage
    reactor.setComponentAsInert("sulfuric acid");
    reactor.setComponentAsInert("NH4HSO4");
    reactor.setComponentAsInert("SO3");

    // SO2 is inert if no significant NO2 present
    if (!hasSignificantConcentration(no2ppm)) {
      reactor.setComponentAsInert("SO2");
    }

    // If SO2 is present, H2S reactions are suppressed
    double so2ppm = getComponentPpm(inlet, "SO2");
    if (hasSignificantConcentration(so2ppm)) {
      reactor.setComponentAsInert("H2S");
    }

    return reactor;
  }

  /**
   * Creates and configures a reactor for SO2 processing (second stage).
   *
   * @param h2sOutlet the outlet stream from the H2S reactor
   * @return configured SO2 reactor
   */
  private GibbsReactor createSO2Reactor(StreamInterface h2sOutlet) {
    GibbsReactor reactor = createConfiguredReactor("SO2 Processing Reactor", h2sOutlet);

    // Configure based on remaining oxygen content
    double outletOxygenFraction = getComponentMoleFraction(h2sOutlet, "oxygen");

    if (outletOxygenFraction > PPM_THRESHOLD) {
      reactor.setComponentAsInert("SO2");
    } else {
      reactor.setComponentAsInert("oxygen");
      reactor.setComponentAsInert("SO2");
    }

    return reactor;
  }

  /**
   * Creates a GibbsReactor with standard configuration for acid gas systems.
   *
   * @param name the reactor name
   * @param inlet the inlet stream
   * @return configured reactor with default settings and inert components
   */
  private GibbsReactor createConfiguredReactor(String name, StreamInterface inlet) {
    GibbsReactor reactor = new GibbsReactor(name, inlet);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(DEFAULT_DAMPING);
    reactor.setMaxIterations(DEFAULT_MAX_ITERATIONS);
    reactor.setConvergenceTolerance(DEFAULT_TOLERANCE);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    setDefaultInertComponents(reactor);
    return reactor;
  }

  /**
   * Sets the default list of inert components on a reactor.
   *
   * @param reactor the reactor to configure
   */
  private void setDefaultInertComponents(GibbsReactor reactor) {
    for (String component : DEFAULT_INERT_COMPONENTS) {
      reactor.setComponentAsInert(component);
    }
  }

  /**
   * Gets the concentration of a component in ppm (parts per million).
   *
   * @param stream the stream to query
   * @param componentName the component name
   * @return concentration in ppm, or 0.0 if component not found
   */
  private double getComponentPpm(StreamInterface stream, String componentName) {
    return getComponentMoleFraction(stream, componentName) * 1e6;
  }

  /**
   * Gets the mole fraction of a component in a stream.
   *
   * @param stream the stream to query
   * @param componentName the component name
   * @return mole fraction, or 0.0 if component not found
   */
  private double getComponentMoleFraction(StreamInterface stream, String componentName) {
    try {
      return stream.getThermoSystem().getComponent(componentName).getz();
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Checks if a concentration exceeds the significance threshold.
   *
   * @param ppm concentration in ppm
   * @return true if concentration is above threshold
   */
  private boolean hasSignificantConcentration(double ppm) {
    return ppm > PPM_THRESHOLD;
  }
}
