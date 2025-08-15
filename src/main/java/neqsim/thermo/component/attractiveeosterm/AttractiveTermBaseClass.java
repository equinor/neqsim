/*
 * AttractiveTermBaseClass.java
 *
 * Created on 13. mai 2001, 21:58
 */

package neqsim.thermo.component.attractiveeosterm;

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
public abstract class AttractiveTermBaseClass implements AttractiveTermInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AttractiveTermBaseClass.class);

  private ComponentEosInterface component = null;
  protected double m;
  protected double[] parameters = new double[3];
  protected double[] parametersSolid = new double[3];

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
  public double getm() {
    return this.m;
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

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AttractiveTermBaseClass other = (AttractiveTermBaseClass) obj;
    if (Double.compare(m, other.m) != 0) {
      return false;
    }
    if (!java.util.Arrays.equals(parameters, other.parameters)) {
      return false;
    }
    if (!java.util.Arrays.equals(parametersSolid, other.parametersSolid)) {
      return false;
    }
    if (component == null) {
      if (other.component != null) {
        return false;
      }
    } /*
       * else if (!component.equals(other.component)) { // Typically equality is checked from
       * component so this will infinitely recursive return false; }
       */
    return true;
  }
}
