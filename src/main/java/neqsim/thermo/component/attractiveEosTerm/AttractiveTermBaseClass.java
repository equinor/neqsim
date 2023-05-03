/*
 * AttractiveTermBaseClass.java
 *
 * Created on 13. mai 2001, 21:58
 */

package neqsim.thermo.component.attractiveEosTerm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermBaseClass class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermBaseClass implements AttractiveTermInterface {
  private static final long serialVersionUID = 1000;

  private ComponentEosInterface component = null;
  protected double m;
  protected double[] parameters = new double[3];
  protected double[] parametersSolid = new double[3];

  static Logger logger = LogManager.getLogger(AttractiveTermBaseClass.class);

  /**
   * <p>
   * Constructor for AttractiveTermBaseClass.
   * </p>
   */
  public AttractiveTermBaseClass() {}

  /**
   * <p>
   * Constructor for AttractiveTermBaseClass.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermBaseClass(ComponentEosInterface component) {
    this.setComponent(component);
  }

  /** {@inheritDoc} */
  @Override
  public void setm(double val) {
    this.m = val;
    logger.info("does not solve for accentric when new m is set... in AccentricBase class");
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermBaseClass clone() {
    AttractiveTermBaseClass attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermBaseClass) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    // atSystem.out.println("m " + m);ractiveTerm.parameters = (double[])
    // parameters.clone();
    // System.arraycopy(parameters,0, attractiveTerm.parameters, 0,
    // parameters.length);
    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {}

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    return getComponent().geta();
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void setParameters(int i, double val) {
    parameters[i] = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getParameters(int i) {
    return parameters[i];
  }

  /**
   * Get component.
   *
   * @return ComponentEosInterface.
   */
  ComponentEosInterface getComponent() {
    return component;
  }

  /**
   * Set Component.
   *
   * @param component input components.
   */
  void setComponent(ComponentEosInterface component) {
    this.component = component;
  }
}
