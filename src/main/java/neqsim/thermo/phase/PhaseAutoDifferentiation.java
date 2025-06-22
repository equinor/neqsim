package neqsim.thermo.phase;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentSrk;

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

  double Bll = 0.0;
  double All = 0.0;

  /**
   * <p>
   * Constructor for PhasePCSAFT.
   * </p>
   */
  public PhaseAutoDifferentiation() {
    super();
    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
    uEOS = 2;
    wEOS = -1;
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
        new ComponentSrk(componentName, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
      double beta) {
    Bll = calcB(this, temperature, pressure, 1);
    All = calcA(this, temperature, pressure, 1);
    if (type == 0) {
      //Setting up function to be dieeferentiad
      funcToBeDifferentiated = getFunctionStruc();
    }
    if (type > 0) {
      funcToBeDifferentiated = getFunctionStruc();
      for (int i = 0; i < numberOfComponents; i++) {
        componentArray[i].Finit(this, temperature, pressure, totalNumberOfMoles, beta,
            numberOfComponents, type);
      }
    }
    // super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
  }

  public DerivativeStructure getFunctionStruc(){
    int params = 2;
    int order = 2;
    double tRealValue = getTemperature();
    double vRealValue = getVolume();

    double numberOfMoles = getNumberOfMolesInPhase();

    DerivativeStructure tVar = new DerivativeStructure(params, order, 0, tRealValue);
    DerivativeStructure vVar = new DerivativeStructure(params, order, 1, vRealValue);
    //
    DerivativeStructure BdivVstruct = vVar.pow(-1.0).multiply(Bll);
    DerivativeStructure BdivVstruct2 =
        tVar.createConstant(-1.0).multiply((tVar.createConstant(1.0).subtract(BdivVstruct)).log());

    DerivativeStructure Dstruct =
        tVar.createConstant(-1.0 * All / R / getB() / (delta1 - delta2)).divide(tVar);
    DerivativeStructure Dstruct2 =
        tVar.createConstant(1.0).add(tVar.createConstant(delta1 * Bll).divide(vVar));
    DerivativeStructure Dstruct3 =
        tVar.createConstant(1.0).add(tVar.createConstant(delta2 * Bll).divide(vVar));
    DerivativeStructure Dstruct4 = Dstruct.multiply(Dstruct2.divide(Dstruct3));

    DerivativeStructure Ffinal = BdivVstruct2.add(Dstruct4);

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
