package neqsim.thermo.characterization;

import java.util.HashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * WaxCharacterise class.
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
   * Constructor for WaxCharacterise.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public WaxCharacterise(SystemInterface system) {
    thermoSystem = system;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if a required part of the clone cannot be copied
   */
  @Override
  public WaxCharacterise clone() {
    WaxCharacterise clonedSystem = null;
    try {
      clonedSystem = (WaxCharacterise) super.clone();
      clonedSystem.model = model.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
      throw new IllegalStateException("Unable to complete clone of WaxCharacterise", ex);
    }
    return clonedSystem;
  }

  public abstract class WaxBaseModel implements WaxModelInterface {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    double[] parameterWax = new double[3];
    double[] parameterWaxHeatOfFusion = new double[1];
    double[] parameterWaxTriplePointTemperature = new double[1];

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if a required part of the clone cannot be copied
     */
    @Override
    public WaxBaseModel clone() {
      WaxBaseModel clonedSystem = null;
      try {
        clonedSystem = (WaxBaseModel) super.clone();
      } catch (Exception ex) {
        logger.error("Cloning failed.", ex);
        throw new IllegalStateException("Unable to complete clone of WaxBaseModel", ex);
      }
      return clonedSystem;
    }

    /** {@inheritDoc} */
    @Override
    public void addTBPWax() {
    }

    /** {@inheritDoc} */
    @Override
    public void setWaxParameters(double[] parameters) {
      parameterWax = parameters;
    }

    /** {@inheritDoc} */
    @Override
    public void setWaxParameter(int i, double parameters) {
      parameterWax[i] = parameters;
    }

    /** {@inheritDoc} */
    @Override
    public void setParameterWaxHeatOfFusion(int i, double parameters) {
      parameterWaxHeatOfFusion[i] = parameters;
    }

    /** {@inheritDoc} */
    @Override
    public void setParameterWaxTriplePointTemperature(int i, double parameters) {
      parameterWaxTriplePointTemperature[i] = parameters;
    }

    /** {@inheritDoc} */
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
    /** Triple-point temperature in K that the component database uses when it has no measured value. */
    public static final double DATABASE_DEFAULT_TRIPLE_POINT_TEMPERATURE = 273.0;
    /** Heat of fusion in J/mol that the component database uses when it has no measured value. */
    public static final double DATABASE_DEFAULT_HEAT_OF_FUSION = 6000.0;
    /** Names of named wax formers whose fusion properties this model assigned from its correlations. */
    private HashSet<String> correlatedNamedWaxFormers = new HashSet<String>();

    public PedersenWaxModel() {
      parameterWax[0] = 1.074;
      parameterWax[1] = 6.584e-6;
      parameterWax[2] = 0.1915;

      parameterWaxHeatOfFusion[0] = 1.0;
      parameterWaxTriplePointTemperature[0] = 1.0;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if a required part of the clone cannot be copied
     */
    @Override
    public PedersenWaxModel clone() {
      PedersenWaxModel clonedModel = (PedersenWaxModel) super.clone();
      clonedModel.correlatedNamedWaxFormers = new HashSet<String>(correlatedNamedWaxFormers);
      return clonedModel;
    }

    public double calcTriplePointTemperature(int componentNumber) {
      return parameterWaxTriplePointTemperature[0]
          * (374.5 + (0.02617 * (thermoSystem.getPhase(0).getComponent(componentNumber).getMolarMass() * 1000.0)
              - 20172.0 / (thermoSystem.getPhase(0).getComponent(componentNumber).getMolarMass() * 1000.0)));
    }

    public double calcHeatOfFusion(int componentNumber) {
      return getParameterWaxHeatOfFusion()[0] * 0.1426 / 0.238845
          * thermoSystem.getPhase(0).getComponent(componentNumber).getMolarMass() * 1000.0
          * thermoSystem.getPhase(0).getComponent(componentNumber).getTriplePointTemperature();
    }

    public double calcParaffinDensity(int componentNumber) {
      return 0.3915 + 0.0675 * Math.log(thermoSystem.getPhase(0).getComponent(componentNumber).getMolarMass() * 1000.0);
    }

    public double calcPCwax(int componentNumber, String normalComponent) {
      return thermoSystem.getPhase(0).getComponent(normalComponent).getPC()
          * Math.pow(calcParaffinDensity(componentNumber)
              / thermoSystem.getPhase(0).getComponent(normalComponent).getNormalLiquidDensity(), 3.46);
    }

    /**
     * Pedersen wax-forming mole fraction of a TBP or plus fraction.
     *
     * <p>
     * {@code z_wax / z_total = 1 - (A + B M)((rho - rhoP) / rhoP)^C}, with M in g/mol, rho the normal liquid density in
     * g/cm3 and rhoP the n-paraffin density {@code 0.3915 + 0.0675 ln M}. A fraction that is not denser than the
     * n-paraffin of the same molar mass is treated as entirely wax forming (the base of the power would otherwise be
     * negative and the result NaN). The result is bounded to [0, 1] so that no split creates negative moles.
     * </p>
     *
     * @param componentNumber index of the TBP, plus or wax pseudo component in phase 0
     * @return wax-forming fraction of the total moles of that fraction, in [0, 1]
     * @throws IllegalArgumentException if molar mass, density or model parameters are not finite and positive
     */
    public double calcWaxFormerFraction(int componentNumber) {
      ComponentInterface component = thermoSystem.getPhase(0).getComponent(componentNumber);
      double molarMass = component.getMolarMass() * 1000.0;
      double density = component.getNormalLiquidDensity();
      double paraffinDensity = calcParaffinDensity(componentNumber);
      if (!Double.isFinite(molarMass) || molarMass <= 0.0 || !Double.isFinite(density) || density <= 0.0
          || !Double.isFinite(paraffinDensity) || paraffinDensity <= 0.0) {
        throw new IllegalArgumentException("Wax split requires positive molar mass and density for "
            + component.getComponentName() + ": M=" + molarMass + " g/mol, rho=" + density + " g/cm3");
      }
      if (density <= paraffinDensity) {
        return 1.0;
      }
      double nonWaxFraction = (parameterWax[0] + parameterWax[1] * molarMass)
          * Math.pow((density - paraffinDensity) / paraffinDensity, parameterWax[2]);
      if (!Double.isFinite(nonWaxFraction)) {
        throw new IllegalArgumentException("Invalid Pedersen wax split parameters A=" + parameterWax[0] + ", B="
            + parameterWax[1] + ", C=" + parameterWax[2] + " for " + component.getComponentName());
      }
      return Math.max(0.0, Math.min(1.0, 1.0 - nonWaxFraction));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * TBP and plus fractions are split into a non-wax part and a wax-forming {@code wax}-prefixed pseudo component using
     * {@link #calcWaxFormerFraction(int)}. The split is idempotent: when a wax pseudo component already exists, the
     * total moles of the pair are re-split with the current parameters, so repeated calls (for example during parameter
     * tuning) do not move more and more of the fraction into wax. A TBP fraction added after an earlier split is split
     * on the next call. Named components flagged as wax formers in the component database (the n-paraffins) are wax
     * formers as a whole and are not split. They keep their database melting temperature and heat of fusion; where the
     * database holds only its placeholder default (273.0 K and 6000 J/mol, currently nC15 and nC17 to nC24) they
     * receive the Pedersen correlations of this model instead (#5210).
     * </p>
     */
    @Override
    public void addTBPWax() {
      addNamedWaxFormerProperties();
      PhaseInterface phase0 = thermoSystem.getPhase(0);
      int numberOfComponents = phase0.getNumberOfComponents();
      for (int i = 0; i < numberOfComponents; i++) {
        ComponentInterface component = phase0.getComponent(i);
        String name = component.getComponentName();
        if (name.startsWith("wax")) {
          String parentName = name.substring(3);
          if (!phase0.hasComponent(parentName)) {
            continue;
          }
          double waxMoles = component.getNumberOfmoles();
          double totalMoles = phase0.getComponent(parentName).getNumberOfmoles() + waxMoles;
          double molesChange = totalMoles * calcWaxFormerFraction(i) - waxMoles;
          thermoSystem.addComponent(parentName, -molesChange);
          thermoSystem.addComponent(name, molesChange);
          for (int k = 0; k < thermoSystem.getNumberOfPhases(); k++) {
            thermoSystem.getPhase(k).getComponent(i).setWaxFormer(true);
            thermoSystem.getPhase(k).getComponent(i).setHeatOfFusion(calcHeatOfFusion(i));
            thermoSystem.getPhase(k).getComponent(i).setTriplePointTemperature(calcTriplePointTemperature(i));
          }
        } else if ((component.isIsTBPfraction() || component.isIsPlusFraction())
            && !phase0.hasComponent("wax" + name)) {
          double molesChange = component.getNumberOfmoles() * calcWaxFormerFraction(i);
          thermoSystem.addComponent(name, -molesChange);
          thermoSystem.addTBPfraction("wax" + name, molesChange, component.getMolarMass(),
              component.getNormalLiquidDensity());

          int cNumb = thermoSystem.getPhase(0).getNumberOfComponents() - 1;
          double waxPC = calcPCwax(cNumb, name);

          for (int k = 0; k < thermoSystem.getNumberOfPhases(); k++) {
            thermoSystem.getPhase(k).getComponent(cNumb).setWaxFormer(true);
            thermoSystem.getPhase(k).getComponent(cNumb).setHeatOfFusion(calcHeatOfFusion(cNumb));
            thermoSystem.getPhase(k).getComponent(cNumb).setTriplePointTemperature(calcTriplePointTemperature(cNumb));
            thermoSystem.getPhase(k).getComponent(cNumb).setPC(waxPC);
          }
        }
      }
    }

    /**
     * Assigns the Pedersen melting (triple-point) temperature and heat of fusion to named wax formers that have no
     * fusion data in the component database.
     *
     * <p>
     * A named wax former is a database component with the wax-former flag set that is neither a TBP nor a plus fraction
     * and is not a {@code wax}-prefixed pseudo component created by this model. Database fusion data are kept where
     * they exist. A component whose database values are the placeholder default
     * ({@value #DATABASE_DEFAULT_TRIPLE_POINT_TEMPERATURE} K and {@value #DATABASE_DEFAULT_HEAT_OF_FUSION} J/mol) gets
     * the correlated values, and keeps getting them on later calls so that the tuning multipliers
     * {@code parameterWaxTriplePointTemperature} and {@code parameterWaxHeatOfFusion} apply to it. The values are
     * written to the component in every allocated phase, including an already created wax phase, so the result does not
     * depend on whether {@code addSolidComplexPhase("wax")} was called before or after {@link #addTBPWax()}.
     * </p>
     */
    private void addNamedWaxFormerProperties() {
      PhaseInterface[] phases = thermoSystem.getPhases();
      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        ComponentInterface component = thermoSystem.getPhase(0).getComponent(i);
        if (!component.isWaxFormer() || component.isIsTBPfraction() || component.isIsPlusFraction()
            || component.getName().startsWith("wax")) {
          continue;
        }
        boolean placeholderData = component.getTriplePointTemperature() == DATABASE_DEFAULT_TRIPLE_POINT_TEMPERATURE
            && component.getHeatOfFusion() == DATABASE_DEFAULT_HEAT_OF_FUSION;
        if (!placeholderData && !correlatedNamedWaxFormers.contains(component.getName())) {
          continue;
        }
        correlatedNamedWaxFormers.add(component.getName());
        double triplePointTemperature = calcTriplePointTemperature(i);
        double heatOfFusion = getParameterWaxHeatOfFusion()[0] * 0.1426 / 0.238845 * component.getMolarMass() * 1000.0
            * triplePointTemperature;
        for (PhaseInterface phase : phases) {
          if (phase == null || phase.getNumberOfComponents() <= i) {
            continue;
          }
          phase.getComponent(i).setTriplePointTemperature(triplePointTemperature);
          phase.getComponent(i).setHeatOfFusion(heatOfFusion);
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    public void removeWax() {
      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        if (thermoSystem.getPhase(0).getComponent(i).getName().startsWith("wax")) {
          String compName = thermoSystem.getPhase(0).getComponent(i).getName().substring(3);
          double moles = thermoSystem.getPhase(0).getComponent(i).getNumberOfmoles();
          thermoSystem.addComponent(thermoSystem.getPhase(0).getComponent(i).getComponentName(), -moles);
          thermoSystem.addComponent(compName, moles);
        }
      }
    }
  }

  /**
   * Getter for the field <code>model</code>.
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.characterization.WaxModelInterface} object
   */
  public WaxModelInterface getModel(String name) {
    // PedersenWax is the only implemented model; every name returns it.
    this.name = name;
    return new PedersenWaxModel();
  }

  /**
   * Setter for the field <code>model</code>.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setModel(String name) {
    // PedersenWax is the only implemented model; every name selects it.
    this.name = name;
    model = new PedersenWaxModel();
  }

  /**
   * Getter for the field <code>model</code>.
   *
   * @return a {@link neqsim.thermo.characterization.WaxModelInterface} object
   */
  public WaxModelInterface getModel() {
    return model;
  }

  /**
   * setModelName.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setModelName(String name) {
    this.name = name;
  }
}
