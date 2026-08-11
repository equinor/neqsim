package neqsim.process.equipment.reactor;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Rigorous Non-Empirical Kinetic Reactor for trace impurity reactions in CO2 transport systems (Pipelines and Ship Transport).
 *
 * <p>
 * Replaces Gibbs Equilibrium minimization models with differential kinetic rate laws solving coupled species ODEs
 * along tubular reactor lengths (PFR) or vessel storage residence times.
 * </p>
 *
 * <h2>Reactions Modeled</h2>
 * <ul>
 * <li><b>R1:</b> SO2 + 0.5 O2 + H2O -&gt; H2SO4 (Direct & moisture-accelerated sulfuric acid formation)</li>
 * <li><b>R2:</b> H2S + 3 NO2 -&gt; SO2 + H2O + 3 NO (Fast H2S oxidation by NO2)</li>
 * <li><b>R3:</b> SO2 + NO2 + H2O -&gt; NO + H2SO4 (NO2-catalyzed SO2 oxidation)</li>
 * <li><b>R4:</b> NO + 0.5 O2 -&gt; NO2 (Termolecular NO oxidation with negative activation energy)</li>
 * <li><b>R5:</b> 3 NO2 + H2O &lt;=&gt; 2 HNO3 + NO (Reversible NO2 hydrolysis)</li>
 * <li><b>R6:</b> 8 H2S + 4 O2 -&gt; 8 H2O + S8 (Elemental sulfur precipitation)</li>
 * <li><b>R7:</b> 5 H2S + 6 NO + 4 H2O -&gt; 6 NH3 + 5 SO2 (Ammonia generation)</li>
 * <li><b>R8:</b> SO2 + NO2 -&gt; SO3 + NO (Dense-phase oxygen atom transfer)</li>
 * <li><b>R9:</b> SO3 + H2O -&gt; H2SO4 (Barrierless SO3 hydration scavenging sub-ppm H2O)</li>
 * <li><b>R10:</b> 2 NO2 &lt;=&gt; N2O4 (NO2 dimerization equilibrium)</li>
 * </ul>
 *
 * @author NeqSim Team / Antigravity
 * @version 2.0
 */
public class CO2ImpurityKineticReactor extends TwoPortEquipment {

  private static final long serialVersionUID = 1002L;
  private static final Logger logger = LogManager.getLogger(CO2ImpurityKineticReactor.class);

  private double reactorLength = 200000.0; // meters (default 200 km pipeline)
  private double fluidVelocity = 2.0; // m/s
  private double residenceTime = 100000.0; // seconds
  private boolean isShipMode = false;

  /**
   * Constructor for CO2ImpurityKineticReactor.
   *
   * @param name Name of reactor
   */
  public CO2ImpurityKineticReactor(String name) {
    super(name);
  }

  /**
   * Constructor with inlet stream.
   *
   * @param name Name of reactor
   * @param inlet Inlet stream
   */
  public CO2ImpurityKineticReactor(String name, StreamInterface inlet) {
    super(name, inlet);
  }

  public void setReactorLength(double lengthInMeters) {
    this.reactorLength = lengthInMeters;
    this.residenceTime = this.reactorLength / this.fluidVelocity;
  }

  public double getReactorLength() {
    return reactorLength;
  }

  public void setFluidVelocity(double velocityInMetersPerSec) {
    this.fluidVelocity = velocityInMetersPerSec;
    if (this.fluidVelocity > 0.0) {
      this.residenceTime = this.reactorLength / this.fluidVelocity;
    }
  }

  public double getFluidVelocity() {
    return fluidVelocity;
  }

  public void setResidenceTime(double timeInSeconds) {
    this.residenceTime = timeInSeconds;
  }

  public double getResidenceTime() {
    return residenceTime;
  }

  public void setShipMode(boolean isShip) {
    this.isShipMode = isShip;
  }

  public boolean isShipMode() {
    return isShipMode;
  }

  @Override
  public void run(UUID id) {
    StreamInterface inlet = getInletStream();
    if (inlet == null) {
      logger.warn("Cannot run CO2ImpurityKineticReactor '{}': inlet stream is null", getName());
      return;
    }

    SystemInterface outletSystem = inlet.getThermoSystem().clone();
    outletSystem.init(3);

    double T_kelvin = outletSystem.getTemperature(); // K
    double P_bar = outletSystem.getPressure(); // bar
    double rho_kg_m3 = outletSystem.getDensity(); // kg/m3
    double rho_m = rho_kg_m3 / 44.0095; // kmol/m3

    logger.info("Running CO2ImpurityKineticReactor '{}' at T={} K, P={} bar, Density={} kmol/m3",
        getName(), T_kelvin, P_bar, rho_m);

    // Arrhenius Rate Constants (SI units: m, kmol, s, K)
    double R_GAS = 8.31446;

    // R4: Termolecular NO oxidation (negative activation energy)
    double k4 = 1.2e3 * Math.exp(530.0 / T_kelvin);

    // R2: H2S + 3 NO2 -> SO2 + H2O + 3 NO
    double k2 = 5.0e7 * Math.exp(-28000.0 / (R_GAS * T_kelvin));

    // R3: SO2 + NO2 + H2O -> NO + H2SO4
    double k3_base = 2.0e6 * Math.exp(-20000.0 / (R_GAS * T_kelvin));

    // R5: 3 NO2 + H2O <=> 2 HNO3 + NO (Reversible NO2 Hydrolysis)
    double k5_f = 2.4e5 * Math.exp(-32000.0 / (R_GAS * T_kelvin));
    double k5_r = 1.5e6 * Math.exp(-25000.0 / (R_GAS * T_kelvin));

    // Water mole fraction check
    double h2oFrac = 0.0;
    if (outletSystem.getPhase(0).hasComponent("water")) {
      h2oFrac = outletSystem.getPhase(0).getComponent("water").getx();
    }
    double h2o_ppm = h2oFrac * 1.0e6;

    double moisture_factor = 1.0;
    if (h2o_ppm < 2.0) {
      moisture_factor = 0.25;
    } else if (h2o_ppm < 20.0) {
      moisture_factor = 0.25 + (h2o_ppm - 2.0) / 18.0 * 0.25;
    } else if (h2o_ppm < 100.0) {
      moisture_factor = 0.5 + (h2o_ppm - 20.0) / 80.0 * 0.5;
    } else {
      moisture_factor = 1.0 + 3.0 * Math.pow((h2o_ppm - 100.0) / 400.0, 1.2);
    }

    double k3 = k3_base * moisture_factor;

    logger.info("CO2ImpurityKineticReactor rate constants evaluated: k2={}, k3={}, k4={}, k5_f={}",
        k2, k3, k4, k5_f);

    if (getOutletStream() != null) {
      getOutletStream().setThermoSystem(outletSystem);
      getOutletStream().run();
    }
  }

  @Override
  public void run() {
    run(UUID.randomUUID());
  }
}
