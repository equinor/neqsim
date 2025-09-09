package neqsim.thermo.phase;

// import org.ejml.data.DenseMatrix64F;

import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.component.ComponentSrkCPAs;

/**
 * <p>
 * PhaseSrkCPAs class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseSrkCPAs extends PhaseSrkCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhaseSrkCPAs.
   * </p>
   */
  public PhaseSrkCPAs() {}

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPAs clone() {
    PhaseSrkCPAs clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkCPAs) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSrkCPAs(name, moles, molesInPhase, compNumber, this);
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i] instanceof ComponentSrkCPA) {
        ((ComponentSrkCPA) componentArray[i]).resizeXsitedni(numberOfComponents);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calc_g() {
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double g = 1.0 / (1.0 - x);
    // System.out.println("g " + g);
    return g;
  }

  /** {@inheritDoc} */
  @Override
  public double calc_lngV() {
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double gv = (x / getTotalVolume()) / (1.0 - x);
    return -gv;
  }

  /** {@inheritDoc} */
  @Override
  public double calc_lngVV() {
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double xV = -1.9 / 4.0 * getB() / (getTotalVolume() * getTotalVolume());
    double u = 1.0 - x;

    double val = -x / (getTotalVolume() * getTotalVolume() * u) + xV / (getTotalVolume() * u)
        - x / (getTotalVolume() * u * u) * (-1.0) * xV;
    return -val;

    // double gvv
    // =0.225625/Math.pow(1.0-0.475*getB()/getTotalVolume(),2.0)*Math.pow(getB(),2.0)/(Math.pow(getTotalVolume(),4.0))+0.95/(1.0-0.475*getB()/getTotalVolume())*getB()/(Math.pow(getTotalVolume(),3.0));
    // System.out.println("val2 " + gvv);
    // return gvv;
  }

  /** {@inheritDoc} */
  @Override
  public double calc_lngVVV() {
    double totVol = getTotalVolume();
    double totVol4 = totVol * totVol * totVol * totVol;
    double totVol5 = totVol4 * totVol;

    double temp1 = 1.0 - 0.475 * getB() / getTotalVolume();
    double gvv =
        -0.21434375 / (temp1 * temp1 * temp1) * getB() * getB() * getB() / (totVol5 * totVol)
            - 0.135375E1 / (temp1 * temp1) * getB() * getB() / (totVol5)
            - 0.285E1 / (temp1) * getB() / (totVol4);
    return gvv;
  }
}
