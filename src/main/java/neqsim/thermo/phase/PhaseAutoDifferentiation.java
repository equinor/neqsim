package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentPCSAFT;
import org.apache.commons.math4.analysis.differentiation.*;

/**
 * <p>
 * PhasePCSAFT class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseAutoDifferentiation extends PhaseEos {
  private static final long serialVersionUID = 1000;

  static Logger logger = LogManager.getLogger(PhaseAutoDifferentiation.class);

  /**
   * <p>
   * Constructor for PhasePCSAFT.
   * </p>
   */
  public PhaseAutoDifferentiation() {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public PhaseAutoDifferentiation clone() {
    PhaseAutoDifferentiation clonedPhase = null;
    try {
      clonedPhase = (PhaseAutoDifferentiation) super.clone();
    } catch (Exception e) {
      logger.error("Cloning failed.", e);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addcomponent(String componentName, double moles, double molesInPhase,
      int compNumber) {
    super.addcomponent(molesInPhase);
    componentArray[compNumber] =
        new ComponentPCSAFT(componentName, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
      double beta) {

    if (type == 0) {
      //Setting up function to be dieeferentiad
      DerivativeStructure funcToBeDifferentiated = getFunctionStruc();
    }
    if (type > 0) {
      for (int i = 0; i < numberOfComponents; i++) {
        componentArray[i].Finit(this, temperature, pressure, totalNumberOfMoles, beta,
            numberOfComponents, type);
      }
    }
    super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
  }

  public DerivativeStructure getFunctionStruc(){
    int params = 2;
    int order = 2;
    double tRealValue = getTemperature();
    double vRealValue = getVolume();
    return new DerivativeStrcture(params, order, 0, tRealValue);
  }


  /** {@inheritDoc} */
  @Override
  public double getF() {
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
  
   return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
     return 1.0;
  }

}
