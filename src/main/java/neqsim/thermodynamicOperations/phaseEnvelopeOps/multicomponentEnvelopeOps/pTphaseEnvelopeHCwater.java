/*
 * pTphaseEnvelopeHCwater.java
 *
 * Created on 14. oktober 2000, 21:59 Updated on May 2019, by Nefeli
 */

package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import java.util.ArrayList;
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
  ThermodynamicOperations ops = null;

  class PhaseEnevelopePoint {

    double temperature;
    double pressure;
    String dewPointType;

    public PhaseEnevelopePoint(SystemInterface fluid) {
      this.temperature = fluid.getTemperature();
      this.pressure = fluid.getPressure();

      if (fluid.getNumberOfPhases() > 1 && fluid.getPhase(1).getPhaseTypeName().equals("aqueous")
          && fluid.getPhase(0).getPhaseTypeName().equals("gas")) {
        this.dewPointType = "2 phase gas-aqueous dew point";
      } else if (fluid.getNumberOfPhases() > 1
          && fluid.getPhase(1).getPhaseTypeName().equals("aqueous")
          && fluid.getPhase(0).getPhaseTypeName().equals("oil")) {
        this.dewPointType = "2 phase oil-aqueous dew point";
      } else if (fluid.getNumberOfPhases() > 1 && fluid.getPhase(1).getPhaseTypeName().equals("oil")
          && fluid.getPhase(0).getPhaseTypeName().equals("gas")) {
        this.dewPointType = "2 phase gas-oil dew point";
      }
    }
  }

  ArrayList<PhaseEnevelopePoint> data = new ArrayList<PhaseEnevelopePoint>();

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
  public pTphaseEnvelopeHCwater(SystemInterface system, String name, double phaseFraction,
      double lowPres, boolean bubfirst) {
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


    // Step 1.
    // Tracing the dewline segments that separate the single-phase region from the
    // two-phase region (Lines I and II in Fig. 1).
    //
    system.setTemperature(0.0, "C");
    system.setPressure(lowPres, "atm");
    // initializing fluid and calculaton K-values according to Wilson
    system.init(0);

    // Create a thermodynamic operation object for the fluid
    ops = new ThermodynamicOperations(system);

    // Initialize with threee steps along dew point line
    startTraceDewPointLine();

    double dewPointPressure = system.getPressure("bara");
    // at the moment we use the HC phase envelope method in the super class
    // super.run();
  }

  private void startTraceDewPointLine() {
    double highDewPointTemperature = 0.0;
    system.setPressure(lowPres);

    try {
      ops.dewPointTemperatureFlash(false);
    } catch (Exception e) {
      logger.error(e.toString());
    }

    




    for (int i = 0; i < 3; i++) {
      // setting pressure to 1.0 atm
      system.setPressure(lowPres + 1.0 * i, "atm");
      // Calculate the dew point temprature of the fluid
      try {
        ops.dewPointTemperatureFlash(false);
      } catch (Exception e) {
        logger.error(e.toString());
      }
      if (checkForThreePhases()) {
        break;
      }
      data.add(new PhaseEnevelopePoint(system));
    }
  }

  private boolean checkForThreePhases() {
    try {
      ops.TPflash();
    } catch (Exception e) {
      logger.error(e.toString());
    }
    if (system.getNumberOfPhases() > 2) {
      return true;
    } else {
      return false;
    }
  }

}
