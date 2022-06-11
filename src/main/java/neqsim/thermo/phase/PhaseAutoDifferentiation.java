package neqsim.thermo.phase;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentPCSAFT;

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
  DerivativeStructure funcToBeDifferentiated = null;
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
      funcToBeDifferentiated = getFunctionStruc();
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

    DerivativeStructure tVar = new DerivativeStructure(params, order, 0, tRealValue);
    DerivativeStructure vVar = new DerivativeStructure(params, order, 1, vRealValue);

    DerivativeStructure tTimesV = tVar.add(vVar.multiply(3.0));
    return tTimesV;
  }


  /** {@inheritDoc} */
  @Override
  public double getF() {
    return funcToBeDifferentiated.getValue();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
  
    return funcToBeDifferentiated.getPartialDerivative(0, 1);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
     return 1.0;
  }

}
