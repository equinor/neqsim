/*
 * pTphaseEnvelopeHCwater.java
 *
 * Created on 14. oktober 2000, 21:59 Updated on May 2019, by Nefeli
 */

package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

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
   //to be implemented.....

   //envelope contruction method comes here.....

    //at the moment we use the HC phase envelope method in the super class
   super.run();
  }

}
