package neqsim.thermo.system;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseLeachmanEos;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSolidHelmholtzEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.solid.ParaHydrogenSolidHelmholtzEquation;
import neqsim.thermo.util.solid.SolidHelmholtzState;

/**
 * This class defines a thermodynamic system using the LeachmanEos equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemLeachmanEos extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private final String hydrogenComponentName;

  /**
   * Constructor for SystemLeachmanEos.
   */
  public SystemLeachmanEos() {
    this(298.15, 1.0, "hydrogen", false);
  }

  /**
   * Constructor for SystemLeachmanEos.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemLeachmanEos(double T, double P) {
    this(T, P, "hydrogen", false);
  }

  /**
   * Constructor for SystemLeachmanEos.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemLeachmanEos(double T, double P, boolean checkForSolids) {
    this(T, P, "hydrogen", checkForSolids);
  }

  /**
   * Constructor for a specified Leachman hydrogen spin isomer.
   *
   * <p>
   * The thesis Helmholtz solid model is available for {@code para-hydrogen}. Normal and ortho hydrogen retain the
   * established empirical pure-solid phase when solid checking is requested.
   * </p>
   *
   * @param T temperature in K
   * @param P pressure in bara
   * @param hydrogenComponentName component name: hydrogen, para-hydrogen, or ortho-hydrogen
   * @param checkForSolids whether to configure a solid phase
   */
  public SystemLeachmanEos(double T, double P, String hydrogenComponentName, boolean checkForSolids) {
    super(T, P, checkForSolids);
    this.hydrogenComponentName = validateHydrogenComponentName(hydrogenComponentName);
    modelName = "Leachman-EOS";

    if (solidPhaseCheck) {
      setNumberOfPhases(5);
    }

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseLeachmanEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      if ("para-hydrogen".equals(this.hydrogenComponentName)) {
        phaseArray[numberOfPhases - 1] = new PhaseSolidHelmholtzEos(createCalibratedParaHydrogenSolidEquation());
      } else {
        phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      }
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      if (phaseArray[numberOfPhases - 1] instanceof PhasePureComponentSolid) {
        phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
      }
    }

    // What could set hydratecheck? Will never be true
    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
    this.useVolumeCorrection(false);
    addComponent(this.hydrogenComponentName, 1.0);
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i].getPhysicalProperties().setViscosityModel("Muzny");
      phaseArray[i].getPhysicalProperties().setConductivityModel("PFCT");
    }
    commonInitialization();
  }

  /**
   * Validate and normalize the requested hydrogen component name.
   *
   * @param componentName hydrogen component name or database alias
   * @return normalized supported component name
   * @throws IllegalArgumentException if the component is not a supported hydrogen spin isomer
   */
  private static String validateHydrogenComponentName(String componentName) {
    String normalizedName = ComponentInterface.getComponentNameFromAlias(componentName);
    if (!"hydrogen".equals(normalizedName) && !"para-hydrogen".equals(normalizedName)
        && !"ortho-hydrogen".equals(normalizedName)) {
      throw new IllegalArgumentException("SystemLeachmanEos supports hydrogen, para-hydrogen, or ortho-hydrogen.");
    }
    return normalizedName;
  }

  /**
   * Calibrate the thesis solid reference to para-Leachman liquid at the hydrogen triple point.
   *
   * @return calibrated para-hydrogen solid Helmholtz equation
   */
  private static ParaHydrogenSolidHelmholtzEquation createCalibratedParaHydrogenSolidEquation() {
    ParaHydrogenSolidHelmholtzEquation rawEquation = new ParaHydrogenSolidHelmholtzEquation();
    SolidHelmholtzState rawSolidState = rawEquation.evaluate(
        ParaHydrogenSolidHelmholtzEquation.TRIPLE_POINT_TEMPERATURE,
        ParaHydrogenSolidHelmholtzEquation.TRIPLE_POINT_PRESSURE);

    PhaseLeachmanEos paraLiquid = new PhaseLeachmanEos();
    paraLiquid.setTemperature(ParaHydrogenSolidHelmholtzEquation.TRIPLE_POINT_TEMPERATURE);
    paraLiquid.setPressure(ParaHydrogenSolidHelmholtzEquation.TRIPLE_POINT_PRESSURE);
    paraLiquid.addComponent("para-hydrogen", 1.0, 1.0, 0);
    paraLiquid.init(1.0, 1, 2, PhaseType.LIQUID, 1.0);

    double fluidGibbsEnergy = paraLiquid.getGibbsEnergy();
    double fluidEntropy = paraLiquid.getEntropy();
    double gibbsShift = fluidGibbsEnergy - rawSolidState.getGibbsEnergy();
    double entropyShift = fluidEntropy - rawSolidState.getEntropy()
        - ParaHydrogenSolidHelmholtzEquation.TRIPLE_POINT_ENTHALPY_OF_FUSION
            / ParaHydrogenSolidHelmholtzEquation.TRIPLE_POINT_TEMPERATURE;
    return new ParaHydrogenSolidHelmholtzEquation(gibbsShift, entropyShift);
  }

  /** {@inheritDoc} */
  @Override
  public SystemLeachmanEos clone() {
    SystemLeachmanEos clonedSystem = null;
    try {
      clonedSystem = (SystemLeachmanEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }

  /**
   * commonInitialization.
   */
  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(false);
    setImplementedPressureDeriativesofFugacity(false);
    setImplementedTemperatureDeriativesofFugacity(false);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double moles) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);
    if (!hydrogenComponentName.equals(componentName)) {
      throw new IllegalArgumentException("SystemLeachmanEos supports only " + hydrogenComponentName + ".");
    }
    super.addComponent(componentName, moles);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(ComponentInterface inComponent) {
    String name = ComponentInterface.getComponentNameFromAlias(inComponent.getComponentName());
    if (!hydrogenComponentName.equals(name)) {
      throw new IllegalArgumentException("SystemLeachmanEos supports only " + hydrogenComponentName + ".");
    }
    super.addComponent(inComponent);
  }
}
