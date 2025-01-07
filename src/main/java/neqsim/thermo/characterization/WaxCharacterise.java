package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * WaxCharacterise class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WaxCharacterise implements java.io.Serializable, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WaxCharacterise.class);

  SystemInterface thermoSystem = null;
  String name = "";
  protected WaxModelInterface model = new PedersenWaxModel();

  /**
   * <p>
   * Constructor for WaxCharacterise.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public WaxCharacterise(SystemInterface system) {
    thermoSystem = system;
  }

  /** {@inheritDoc} */
  @Override
  public WaxCharacterise clone() {
    WaxCharacterise clonedSystem = null;
    try {
      clonedSystem = (WaxCharacterise) super.clone();
      clonedSystem.model = model.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }

  public abstract class WaxBaseModel implements WaxModelInterface {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    double[] parameterWax = new double[3];
    double[] parameterWaxHeatOfFusion = new double[1];
    double[] parameterWaxTriplePointTemperature = new double[1];

    @Override
    public WaxBaseModel clone() {
      WaxBaseModel clonedSystem = null;
      try {
        clonedSystem = (WaxBaseModel) super.clone();
      } catch (Exception ex) {
        logger.error("Cloning failed.", ex);
      }
      return clonedSystem;
    }

    @Override
    public void addTBPWax() {}

    @Override
    public void setWaxParameters(double[] parameters) {
      parameterWax = parameters;
    }

    @Override
    public void setWaxParameter(int i, double parameters) {
      parameterWax[i] = parameters;
    }

    @Override
    public void setParameterWaxHeatOfFusion(int i, double parameters) {
      parameterWaxHeatOfFusion[i] = parameters;
    }

    @Override
    public void setParameterWaxTriplePointTemperature(int i, double parameters) {
      parameterWaxTriplePointTemperature[i] = parameters;
    }

    @Override
    public double[] getWaxParameters() {
      return parameterWax;
    }

    /**
     * @return the parameterWaxHeatOfFusion
     */
    @Override
    public double[] getParameterWaxHeatOfFusion() {
      return parameterWaxHeatOfFusion;
    }

    /**
     * @param parameterWaxHeatOfFusion the parameterWaxHeatOfFusion to set
     */
    @Override
    public void setParameterWaxHeatOfFusion(double[] parameterWaxHeatOfFusion) {
      this.parameterWaxHeatOfFusion = parameterWaxHeatOfFusion;
    }

    /**
     * @return the parameterWaxTriplePointTemperature
     */
    @Override
    public double[] getParameterWaxTriplePointTemperature() {
      return parameterWaxTriplePointTemperature;
    }

    /**
     * @param parameterWaxTriplePointTemperature the parameterWaxTriplePointTemperature to set
     */
    @Override
    public void setParameterWaxTriplePointTemperature(double[] parameterWaxTriplePointTemperature) {
      this.parameterWaxTriplePointTemperature = parameterWaxTriplePointTemperature;
    }
  }

  public class PedersenWaxModel extends WaxBaseModel {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public PedersenWaxModel() {
      parameterWax[0] = 1.074;
      parameterWax[1] = 6.584e-6;
      parameterWax[2] = 0.1915;

      parameterWaxHeatOfFusion[0] = 1.0;
      parameterWaxTriplePointTemperature[0] = 1.0;
    }

    public double calcTriplePointTemperature(int componentNumber) {
      return parameterWaxTriplePointTemperature[0] * (374.5 + (0.02617
          * (thermoSystem.getPhase(0).getComponent(componentNumber).getMolarMass() * 1000.0)
          - 20172.0
              / (thermoSystem.getPhase(0).getComponent(componentNumber).getMolarMass() * 1000.0)));
    }

    public double calcHeatOfFusion(int componentNumber) {
      return getParameterWaxHeatOfFusion()[0] * 0.1426 / 0.238845
          * thermoSystem.getPhase(0).getComponent(componentNumber).getMolarMass() * 1000.0
          * thermoSystem.getPhase(0).getComponent(componentNumber).getTriplePointTemperature();
    }

    public double calcParaffinDensity(int componentNumber) {
      return 0.3915 + 0.0675 * Math
          .log(thermoSystem.getPhase(0).getComponent(componentNumber).getMolarMass() * 1000.0);
    }

    public double calcPCwax(int componentNumber, String normalComponent) {
      return thermoSystem.getPhase(0).getComponent(normalComponent).getPC() * Math.pow(
          calcParaffinDensity(componentNumber)
              / thermoSystem.getPhase(0).getComponent(normalComponent).getNormalLiquidDensity(),
          3.46);
    }

    @Override
    public void addTBPWax() {
      int numberOfCOmponents = thermoSystem.getPhase(0).getNumberOfComponents();
      boolean hasWax = false;
      for (int i = 0; i < numberOfCOmponents; i++) {
        if (thermoSystem.getPhase(0).getComponent(i).getName().startsWith("wax")) {
          hasWax = true;
        }
      }

      for (int i = 0; i < numberOfCOmponents; i++) {
        if (hasWax && thermoSystem.getPhase(0).getComponent(i).getName().startsWith("wax")) {
          double A = parameterWax[0];
          double B = parameterWax[1];
          double C = parameterWax[2];
          String compName = thermoSystem.getPhase(0).getComponent(i).getName().substring(3);

          double densityLocal = calcParaffinDensity(i);

          double molesChange =
              thermoSystem.getPhase(0).getComponent(compName).getNumberOfmoles() * (1.0 - (A
                  + B * thermoSystem.getPhase(0).getComponent(compName).getMolarMass() * 1000.0)
                  * Math
                      .pow((thermoSystem.getPhase(0).getComponent(compName).getNormalLiquidDensity()
                          - densityLocal) / densityLocal, C));

          if (molesChange < 0) {
            molesChange = 0.0;
          }

          thermoSystem.addComponent(compName, -molesChange);
          thermoSystem.addComponent(thermoSystem.getPhase(0).getComponent(i).getName(),
              molesChange);
          for (int k = 0; k < thermoSystem.getNumberOfPhases(); k++) {
            thermoSystem.getPhase(k).getComponent(i).setWaxFormer(true);
            thermoSystem.getPhase(k).getComponent(i).setHeatOfFusion(calcHeatOfFusion(i));
            thermoSystem.getPhase(k).getComponent(i)
                .setTriplePointTemperature(calcTriplePointTemperature(i));
          }
        } else if (!hasWax && (thermoSystem.getPhase(0).getComponent(i).isIsTBPfraction()
            || thermoSystem.getPhase(0).getComponent(i).isIsPlusFraction())) {
          // double A = 1.074, B = 6.584e-4, C = 0.1915;
          double A = parameterWax[0];

          double B = parameterWax[1];
          double C = parameterWax[2];
          double densityLocal = calcParaffinDensity(i);
          double molesChange = thermoSystem.getPhase(0).getComponent(i).getNumberOfmoles()
              * (1.0 - (A + B * thermoSystem.getPhase(0).getComponent(i).getMolarMass() * 1000.0)
                  * Math.pow((thermoSystem.getPhase(0).getComponent(i).getNormalLiquidDensity()
                      - densityLocal) / densityLocal, C));
          // if(molesChange<0) molesChange=0.0;
          // System.out.println("moles change " + molesChange);
          thermoSystem.addComponent(thermoSystem.getPhase(0).getComponent(i).getComponentName(),
              -molesChange);
          thermoSystem.addTBPfraction(
              "wax" + thermoSystem.getPhase(0).getComponent(i).getComponentName(), molesChange,
              thermoSystem.getPhase(0).getComponent(i).getMolarMass(),
              thermoSystem.getPhase(0).getComponent(i).getNormalLiquidDensity());

          int cNumb = thermoSystem.getPhase(0).getNumberOfComponents() - 1;
          double waxPC =
              calcPCwax(cNumb, thermoSystem.getPhase(0).getComponent(i).getComponentName());

          for (int k = 0; k < thermoSystem.getNumberOfPhases(); k++) {
            thermoSystem.getPhase(k).getComponent(cNumb).setWaxFormer(true);
            thermoSystem.getPhase(k).getComponent(cNumb).setHeatOfFusion(calcHeatOfFusion(cNumb));
            thermoSystem.getPhase(k).getComponent(cNumb)
                .setTriplePointTemperature(calcTriplePointTemperature(cNumb));
            thermoSystem.getPhase(k).getComponent(cNumb).setPC(waxPC);
          }
        }
      }
    }

    @Override
    public void removeWax() {
      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        if (thermoSystem.getPhase(0).getComponent(i).getName().startsWith("wax")) {
          String compName = thermoSystem.getPhase(0).getComponent(i).getName().substring(3);
          double moles = thermoSystem.getPhase(0).getComponent(i).getNumberOfmoles();
          thermoSystem.addComponent(thermoSystem.getPhase(0).getComponent(i).getComponentName(),
              -moles);
          thermoSystem.addComponent(compName, moles);
        }
      }
    }
  }

  /**
   * <p>
   * Getter for the field <code>model</code>.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.characterization.WaxModelInterface} object
   */
  public WaxModelInterface getModel(String name) {
    this.name = name;
    if (name.equals("PedersenWax")) {
    }
    return new PedersenWaxModel();
  }

  /**
   * <p>
   * Setter for the field <code>model</code>.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setModel(String name) {
    this.name = name;
    if (name.equals("PedersenWax")) {
      model = new PedersenWaxModel();
    }
    model = new PedersenWaxModel();
  }

  /**
   * <p>
   * Getter for the field <code>model</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.characterization.WaxModelInterface} object
   */
  public WaxModelInterface getModel() {
    return model;
  }

  /**
   * <p>
   * setModelName.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setModelName(String name) {
    this.name = name;
  }
}
