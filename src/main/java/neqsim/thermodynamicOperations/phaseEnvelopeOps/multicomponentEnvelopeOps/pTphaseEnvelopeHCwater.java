/*
 * pTphaseEnvelopeHCwater.java
 *
 * Created on 14. oktober 2000, 21:59 Updated on May 2019, by Nefeli
 */

package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * pTphaseEnvelope class.
 * </p>
 *
 * @author asmun
 * @version $Id: $Id
 */
public class pTphaseEnvelopeHCwater extends pTphaseEnvelope {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(pTphaseEnvelopeHCwater.class);

  /**
   * <p>
   * Constructor for pTphaseEnvelopeHCwater.
   * </p>
   */
  public pTphaseEnvelopeHCwater() {}

  /**
   * <p>
   * Constructor for pTphaseEnvelopeHCwater.
   * </p>
   * Method follow algorithm in : Lindeloff, N. and M.L. Michelsen, Phase envelope calculations for
   * hydrocarbon-water mixtures. SPE Journal, 2003. 8(03): p. 298-303.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param name a {@link java.lang.String} object
   * @param phaseFraction a double
   * @param lowPres a double
   * @param bubfirst a boolean
   */
  public pTphaseEnvelopeHCwater(SystemInterface system, String name, double phaseFraction, double lowPres,
      boolean bubfirst) {
    super(system, name, phaseFraction, lowPres, bubfirst);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // envelope contruction method comes here.....

    /**
     * Steps in algorithm (figure names references original paper of Lindeloff and Michelsen):
     * 
     * 
     * Tracing the dewline segments that separate the single-phase region from the two-phase region
     * (Lines I and II in Fig. 1).
     * 
     * Determining the three-phase points on the dewline. These are the points where the overall
     * composition is at equilibrium with two incipient phases.
     * 
     * Tracing the phase boundaries that separate the two-phase region from the three-phase region
     * (Lines III and IV in Fig. 1). These phase boundaries emerge from the three-phase points or
     * exist as isolated lines.
     * 
     */


    // Step 1Tracing the dewline segments that separate the single-phase region from the
    // two-phase region (Lines I and II in Fig. 1).
    //
    // setting pressure to 1.0 atm
    system.setPressure(50.0, "atm");
    // set temperature to 0C
    system.setTemperature(0.0, "C");
    // initializing fluid and calculaton K-values accirding to Wilson
    system.init(0);
    // Create a thermodynamic operation object for the fluid
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    // Calculate the dew point temprature of the fluid
    try {
      ops.dewPointTemperatureFlash(false);
    } catch (Exception e) {
      logger.error(e.toString());
    }
    double dewPointTemperature = system.getTemperature("C");
    String dewPhaseType = system.getPhase(1).getPhaseTypeName();
    double dewPointPressure = system.getPressure("bara");


    //at the moment we use the HC phase envelope method in the super class
    // super.run();
  }

}
