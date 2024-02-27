package neqsim.thermo.system;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.chemicalReactions.ChemicalReactionOperations;
import neqsim.physicalProperties.interfaceProperties.InterfaceProperties;
import neqsim.physicalProperties.interfaceProperties.InterphasePropertiesInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.characterization.Characterise;
import neqsim.thermo.characterization.WaxCharacterise;
import neqsim.thermo.characterization.WaxModelInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSolid;
import neqsim.thermo.phase.PhaseSolidComplex;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseWax;
import neqsim.util.database.NeqSimDataBase;
import neqsim.util.exception.InvalidInputException;

/**
 * This is the base class of the System classes.
 */
public abstract class SystemThermo implements SystemInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(SystemThermo.class);

  // Class variables
  private static final int MAX_PHASES = 6;

  // Object metadata
  protected String fluidInfo = "No Information Available";
  protected String fluidName = "DefaultName";
  protected String modelName = "Default";

  // Initialization
  boolean isInitialized = false;
  protected boolean numericDerivatives = false;
  protected int initType = 3;

  private boolean implementedTemperatureDeriativesofFugacity = true;
  private boolean implementedPressureDeriativesofFugacity = true;
  private boolean implementedCompositionDeriativesofFugacity = true;
  protected double criticalTemperature = 0;
  protected String[][] resultTable = null;

  protected boolean allowPhaseShift = true;

  private boolean useTVasIndependentVariables = false;
  protected double criticalPressure = 0;
  private double totalNumberOfMoles = 0;

  // TODO: componentNameTag is not working yet, a kind of alias-postfix for
  // Components from this
  // system that will be passed on to other systems. used to find originator of
  // specific components
  // or
  public String componentNameTag = "";
  protected neqsim.thermo.characterization.WaxCharacterise waxCharacterisation = null;
  protected int a;

  private ArrayList<String> componentNames = new ArrayList<String>();
  // todo: replace numberOfComponents with length of componentNames.
  protected int numberOfComponents = 0;

  // protected ArrayList<String> resultArray1 = new ArrayList<String>();
  protected String[] CapeOpenProperties11 = {"molecularWeight", "speedOfSound",
      "jouleThomsonCoefficient", "internalEnergy", "internalEnergy.Dtemperature", "gibbsEnergy",
      "helmholtzEnergy", "fugacityCoefficient", "logFugacityCoefficient",
      "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
      "logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dmoles", "enthalpy.Dtemperature",
      "enthalpy.Dpressure", "entropy", "entropy.Dtemperature", "entropy.Dpressure",
      "entropy.Dmoles", "heatCapacityCp", "heatCapacityCv", "density", "density.Dtemperature",
      "density.Dpressure", "density.Dmoles", "volume", "volume.Dpressure", "volume.Dtemperature",
      "molecularWeight.Dtemperature", "molecularWeight.Dpressure", "molecularWeight.Dmoles",
      "compressibilityFactor"};
  protected String[] CapeOpenProperties10 = {"molecularWeight", "speedOfSound",
      "jouleThomsonCoefficient", "energy", "energy.Dtemperature", "gibbsFreeEnergy",
      "helmholtzFreeEnergy", "fugacityCoefficient", "logFugacityCoefficient",
      "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
      "logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dmoles", "enthalpy.Dtemperature",
      "enthalpy.Dpressure", "entropy", "entropy.Dtemperature", "entropy.Dpressure",
      "entropy.Dmoles", "heatCapacity", "heatCapacityCv", "density", "density.Dtemperature",
      "density.Dpressure", "density.Dmoles", "volume", "volume.Dpressure", "volume.Dtemperature",
      "molecularWeight.Dtemperature", "molecularWeight.Dpressure", "molecularWeight.Dmoles",
      "compressibilityFactor"};
  protected int attractiveTermNumber = 0;

  /** Number of phases in use/existing. */
  protected int numberOfPhases = 2;
  /** Maximum allowed number of phases . */
  public int maxNumberOfPhases = 2;
  /**
   * Array of indexes to phaseArray keeping track of the creation order of the phases where 0 is the
   * first created phase and the lowest number is the phase created last.
   */
  protected int[] phaseIndex;
  /**
   * Array containing all phases of System. NB! Phases are reorered according to density, use
   * phaseIndex to keep track of the creation order.
   */
  protected PhaseInterface[] phaseArray = new PhaseInterface[MAX_PHASES];
  // PhaseType of phases belonging to system.
  protected PhaseType[] phaseType = new PhaseType[MAX_PHASES];
  // Fraction of moles_in_phase / moles_in_system.
  protected double[] beta = new double[MAX_PHASES];

  protected ChemicalReactionOperations chemicalReactionOperations = null;
  private int mixingRule = 1;
  protected boolean chemicalSystem = false;

  protected boolean solidPhaseCheck = false;
  protected boolean multiPhaseCheck = false;
  protected boolean hydrateCheck = false;

  protected boolean checkStability = true;
  public neqsim.thermo.characterization.Characterise characterization = null;
  protected neqsim.standards.StandardInterface standard = null;
  protected InterphasePropertiesInterface interfaceProp = null;
  private boolean multiphaseWaxCheck = false;
  private boolean forcePhaseTypes = false;

  /**
   * <p>
   * Constructor for SystemThermo.
   * </p>
   */
  public SystemThermo() {
    characterization = new Characterise(this);
    interfaceProp = new InterfaceProperties(this);

    reInitPhaseInformation();
  }

  /**
   * <p>
   * Constructor for SystemThermo.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemThermo(double T, double P) {
    this();
    if (T < 0.0) {
      neqsim.util.exception.InvalidInputException ex =
          new neqsim.util.exception.InvalidInputException(this.getClass().getSimpleName(),
              "SystemThermo", "T", "is negative");
      throw new RuntimeException(ex);
    }

    if (P < 0.0) {
      neqsim.util.exception.InvalidInputException ex =
          new neqsim.util.exception.InvalidInputException(this.getClass().getSimpleName(),
              "SystemThermo", "P", "is negative");
      throw new RuntimeException(ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfComponents() {
    return getComponentNames().length;
  }

  /** {@inheritDoc} */
  @Override
  public void clearAll() {
    setTotalNumberOfMoles(0);
    phaseType[0] = PhaseType.byValue(1);
    phaseType[1] = PhaseType.byValue(0);
    numberOfComponents = 0;
    setNumberOfPhases(2);
    beta[0] = 1.0;
    beta[1] = 1.0;
    beta[2] = 1.0;
    beta[3] = 1.0;
    beta[4] = 1.0;
    beta[5] = 1.0;
    chemicalSystem = false;

    double oldTemp = phaseArray[0].getTemperature();
    double oldPres = phaseArray[0].getPressure();

    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      try {
        phaseArray[i] = phaseArray[i].getClass().getDeclaredConstructor().newInstance();
      } catch (Exception ex) {
        logger.error("err ", ex);
      }
      phaseArray[i].setTemperature(oldTemp);
      phaseArray[i].setPressure(oldPres);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void resetCharacterisation() {
    int numberOfLumpedComps = characterization.getLumpingModel().getNumberOfLumpedComponents();
    characterization = new Characterise(this);
    characterization.getLumpingModel().setNumberOfLumpedComponents(numberOfLumpedComps);
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface addFluid(SystemInterface addSystem) {
    boolean addedNewComponent = false;
    int index = -1;
    for (int i = 0; i < addSystem.getPhase(0).getNumberOfComponents(); i++) {
      if (!getPhase(0).hasComponent(addSystem.getPhase(0).getComponent(i).getComponentName())) {
        index = -1;
        addedNewComponent = true;
      } else {
        index = getPhase(0).getComponent(addSystem.getPhase(0).getComponent(i).getComponentName())
            .getComponentNumber();
      }

      if (index != -1) {
        addComponent(index, addSystem.getPhase(0).getComponent(i).getNumberOfmoles());
      } else if (addSystem.getPhase(0).getComponent(i).isIsTBPfraction()) {
        addTBPfraction(
            addSystem.getPhase(0).getComponent(i).getComponentName().replaceFirst("_PC", ""),
            addSystem.getPhase(0).getComponent(i).getNumberOfmoles(),
            addSystem.getPhase(0).getComponent(i).getMolarMass(),
            addSystem.getPhase(0).getComponent(i).getNormalLiquidDensity());
      } else {
        if (addSystem.getPhase(0).getComponent(i).isIsTBPfraction()) {
          addTBPfraction(
              addSystem.getPhase(0).getComponent(i).getComponentName().replaceFirst("_PC", ""),
              addSystem.getPhase(0).getComponent(i).getNumberOfmoles(),
              addSystem.getPhase(0).getComponent(i).getMolarMass(),
              addSystem.getPhase(0).getComponent(i).getNormalLiquidDensity());
        } else {
          addComponent(addSystem.getComponent(i));
        }
      }
    }
    if (addedNewComponent) {
      createDatabase(true);
      setMixingRule(getMixingRule());
      init(0);
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface addFluid(SystemInterface addSystem, int phase) {
    for (int i = 0; i < addSystem.getPhase(0).getNumberOfComponents(); i++) {
      addComponent(addSystem.getPhase(0).getComponent(i).getComponentName(),
          addSystem.getPhase(0).getComponent(i).getNumberOfmoles(), phase);
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public void addPhase() {
    /*
     * if (maxNumberOfPhases < 6 && !hydrateCheck) { ArrayList phaseList = new ArrayList(0); for
     * (int i = 0; i < numberOfPhases; i++) { phaseList.add(phaseArray[i]); } // add the new phase
     * phaseList.add(phaseArray[0].clone()); beta[phaseList.size() - 1] = 1.0e-8; // beta[1] -=
     * beta[1]/1.0e5;
     *
     * PhaseInterface[] phaseArray2 = new PhaseInterface[numberOfPhases + 1];
     *
     * for (int i = 0; i < numberOfPhases + 1; i++) { phaseArray2[i] = (PhaseInterface)
     * phaseList.get(i); }
     *
     * phaseArray = phaseArray2;
     *
     * System.out.println("number of phases " + numberOfPhases); if (maxNumberOfPhases <
     * numberOfPhases) { maxNumberOfPhases = numberOfPhases; } }
     */
    numberOfPhases++;
  }

  /**
   * <p>
   * addSolidPhase.
   * </p>
   */
  public void addSolidPhase() {
    if (!multiPhaseCheck) {
      setMultiPhaseCheck(true);
    }
    phaseArray[3] = new PhasePureComponentSolid();
    phaseArray[3].setTemperature(phaseArray[0].getTemperature());
    phaseArray[3].setPressure(phaseArray[0].getPressure());
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction()) {
        phaseArray[3].addComponent("default", getPhase(0).getComponent(i).getNumberOfmoles(),
            getPhase(0).getComponent(i).getNumberOfmoles(), i);
        phaseArray[3].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
        phaseArray[3].getComponent(i).setIsPlusFraction(true);
      } else {
        phaseArray[3].addComponent(getPhase(0).getComponent(i).getName(),
            getPhase(0).getComponent(i).getNumberOfmoles(),
            getPhase(0).getComponent(i).getNumberOfmoles(), i);
      }
    }
    ((PhaseSolid) phaseArray[3]).setSolidRefFluidPhase(phaseArray[0]);

    if (getMaxNumberOfPhases() < 4) {
      setMaxNumberOfPhases(4);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addSolidComplexPhase(String type) {
    if (!multiPhaseCheck) {
      setMultiPhaseCheck(true);
    }
    addHydratePhase();
    if (type.equalsIgnoreCase("wax")) {
      phaseArray[5] = new PhaseWax();
    } else {
      phaseArray[5] = new PhaseSolidComplex();
    }

    phaseArray[5].setTemperature(phaseArray[0].getTemperature());
    phaseArray[5].setPressure(phaseArray[0].getPressure());
    phaseArray[5].setType(PhaseType.WAX);
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction()) {
        phaseArray[5].addComponent(getPhase(0).getComponent(i).getName(),
            getPhase(0).getComponent(i).getNumberOfmoles(),
            getPhase(0).getComponent(i).getNumberOfmoles(), i);
        phaseArray[5].getComponent(i).setIsPlusFraction(true);
      } else {
        phaseArray[5].addComponent(getPhase(0).getComponent(i).getName(),
            getPhase(0).getComponent(i).getNumberOfmoles(),
            getPhase(0).getComponent(i).getNumberOfmoles(), i);
      }
    }
    ((PhaseSolid) phaseArray[5]).setSolidRefFluidPhase(phaseArray[0]);
    setNumberOfPhases(6);
  }

  /**
   * <p>
   * addHydratePhase.
   * </p>
   */
  public void addHydratePhase() {
    if (!multiPhaseCheck) {
      setMultiPhaseCheck(true);
    }

    if (!hasSolidPhase()) {
      phaseArray[3] = new PhasePureComponentSolid();
      phaseArray[3].setTemperature(phaseArray[0].getTemperature());
      phaseArray[3].setPressure(phaseArray[0].getPressure());
      phaseArray[3].setType(PhaseType.SOLID);
      for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
        if (getPhase(0).getComponent(i).isIsTBPfraction()) {
          phaseArray[3].addComponent("default", getPhase(0).getComponent(i).getNumberOfmoles(),
              getPhase(0).getComponent(i).getNumberOfmoles(), i);
          phaseArray[3].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
          phaseArray[3].getComponent(i).setIsTBPfraction(true);
        } else {
          phaseArray[3].addComponent(getPhase(0).getComponent(i).getName(),
              getPhase(0).getComponent(i).getNumberOfmoles(),
              getPhase(0).getComponent(i).getNumberOfmoles(), i);
        }
      }
      ((PhaseSolid) phaseArray[3]).setSolidRefFluidPhase(phaseArray[0]);
    }

    phaseArray[4] = new PhaseHydrate(getModelName());
    phaseArray[4].setTemperature(phaseArray[0].getTemperature());
    phaseArray[4].setPressure(phaseArray[0].getPressure());
    phaseArray[4].setType(PhaseType.HYDRATE);
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction()) {
        phaseArray[4].addComponent("default", getPhase(0).getComponent(i).getNumberOfmoles(),
            getPhase(0).getComponent(i).getNumberOfmoles(), i);
        phaseArray[4].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
        phaseArray[4].getComponent(i).setIsTBPfraction(true);
      } else {
        phaseArray[4].addComponent(getPhase(0).getComponent(i).getName(),
            getPhase(0).getComponent(i).getNumberOfmoles(),
            getPhase(0).getComponent(i).getNumberOfmoles(), i);
      }
    }
    ((PhaseHydrate) phaseArray[4]).setSolidRefFluidPhase(phaseArray[0]);

    setNumberOfPhases(5);
  }

  /**
   * <p>
   * addHydratePhase2.
   * </p>
   */
  public void addHydratePhase2() {
    if (!multiPhaseCheck) {
      setMultiPhaseCheck(true);
    }
    phaseArray[3] = new PhaseHydrate();
    phaseArray[3].setTemperature(phaseArray[0].getTemperature());
    phaseArray[3].setPressure(phaseArray[0].getPressure());
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction()) {
        phaseArray[3].addComponent("default", getPhase(0).getComponent(i).getNumberOfmoles(),
            getPhase(0).getComponent(i).getNumberOfmoles(), i);
        phaseArray[3].getComponent("default")
            .setComponentName(getPhase(0).getComponent(i).getName());
      } else {
        phaseArray[3].addComponent(getPhase(0).getComponent(i).getName(),
            getPhase(0).getComponent(i).getNumberOfmoles(),
            getPhase(0).getComponent(i).getNumberOfmoles(), i);
      }
    }
    setNumberOfPhases(4);
  }

  /** {@inheritDoc} */
  @Override
  public void setAllComponentsInPhase(int phase) {
    for (int k = 0; k < numberOfPhases; k++) {
      for (int i = 0; i < numberOfComponents; i++) {
        if (phase != k) {
          // System.out.println("moles of comp: " + i + " " +
          // phaseArray[k].getComponents()[i].getNumberOfMolesInPhase());
          phaseArray[phase].addMoles(i,
              (phaseArray[k].getComponents()[i].getNumberOfMolesInPhase() * (1.0 - 0.01)));
          phaseArray[k].addMoles(i,
              -(phaseArray[k].getComponents()[i].getNumberOfMolesInPhase() * (1.0 - 0.01)));
          phaseArray[k].getComponents()[i]
              .setx(phaseArray[k].getComponents()[i].getNumberOfMolesInPhase()
                  / phaseArray[k].getNumberOfMolesInPhase());
          // System.out.println("moles of comp after: " + i + " " +
          // phaseArray[k].getComponents()[i].getNumberOfMolesInPhase());
        }
      }
    }
    initBeta();
    init(1);
  }

  /** {@inheritDoc} */
  @Override
  public void removePhase(int specPhase) {
    setTotalNumberOfMoles(getTotalNumberOfMoles() - getPhase(specPhase).getNumberOfMolesInPhase());

    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        getPhase(j).getComponents()[i]
            .setNumberOfmoles(getPhase(j).getComponents()[i].getNumberOfmoles()
                - getPhase(specPhase).getComponents()[i].getNumberOfMolesInPhase());
      }
    }

    ArrayList<PhaseInterface> phaseList = new ArrayList<PhaseInterface>(0);
    for (int i = 0; i < numberOfPhases; i++) {
      if (specPhase != i) {
        phaseList.add(phaseArray[phaseIndex[i]]);
      }
    }

    // phaseArray = new PhaseInterface[numberOfPhases - 1];
    for (int i = 0; i < numberOfPhases - 1; i++) {
      // phaseArray[i] = (PhaseInterface) phaseList.get(i);
      if (i >= specPhase) {
        phaseIndex[i] = phaseIndex[i + 1];
        phaseType[i] = phaseType[i + 1];
      }
    }
    numberOfPhases--;
  }

  /** {@inheritDoc} */
  @Override
  public void removePhaseKeepTotalComposition(int specPhase) {
    ArrayList<PhaseInterface> phaseList = new ArrayList<PhaseInterface>(0);
    for (int i = 0; i < numberOfPhases; i++) {
      if (specPhase != i) {
        phaseList.add(phaseArray[phaseIndex[i]]);
      }
    }

    // phaseArray = new PhaseInterface[numberOfPhases - 1];
    for (int i = 0; i < numberOfPhases - 1; i++) {
      // phaseArray[i] = (PhaseInterface) phaseList.get(i);
      if (i >= specPhase) {
        phaseIndex[i] = phaseIndex[i + 1];
        phaseType[i] = phaseType[i + 1];
      }
    }
    numberOfPhases--;
  }

  /** {@inheritDoc} */
  @Override
  public void replacePhase(int repPhase, PhaseInterface newPhase) {
    for (int i = 0; i < 2; i++) {
      phaseArray[i] = newPhase.clone();
    }
    setTotalNumberOfMoles(newPhase.getNumberOfMolesInPhase());
  }

  /** {@inheritDoc} */
  @Override
  public SystemThermo clone() {
    SystemThermo clonedSystem = null;
    try {
      clonedSystem = (SystemThermo) super.clone();
      // clonedSystem.chemicalReactionOperations = (ChemicalReactionOperations)
      // chemicalReactionOperations.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    clonedSystem.beta = beta.clone();
    clonedSystem.attractiveTermNumber = attractiveTermNumber;
    clonedSystem.phaseType = phaseType.clone();
    clonedSystem.phaseIndex = phaseIndex.clone();

    clonedSystem.componentNames = new ArrayList<String>(componentNames);
    if (interfaceProp != null) {
      // clonedSystem.interfaceProp = (InterphasePropertiesInterface)
      // interfaceProp.clone();
    }
    clonedSystem.characterization = characterization.clone();
    if (clonedSystem.waxCharacterisation != null) {
      clonedSystem.waxCharacterisation = waxCharacterisation.clone();
    }

    System.arraycopy(this.beta, 0, clonedSystem.beta, 0, beta.length);
    System.arraycopy(this.phaseType, 0, clonedSystem.phaseType, 0, phaseType.length);
    System.arraycopy(this.phaseIndex, 0, clonedSystem.phaseIndex, 0, phaseIndex.length);

    clonedSystem.phaseArray = phaseArray.clone();
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      clonedSystem.phaseArray[i] = phaseArray[i].clone();
    }
    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getEmptySystemClone() {
    int phaseNumber = 0;

    SystemInterface newSystem = this.clone();

    for (int j = 0; j < getMaxNumberOfPhases(); j++) {
      phaseNumber = j;
      for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
        newSystem.getPhase(j).getComponents()[i].setNumberOfmoles(
            getPhase(phaseNumber).getComponents()[i].getNumberOfMolesInPhase() / 1.0e30);
        newSystem.getPhase(j).getComponents()[i].setNumberOfMolesInPhase(
            getPhase(phaseNumber).getComponents()[i].getNumberOfMolesInPhase() / 1.0e30);
      }
    }

    newSystem.setTotalNumberOfMoles(getPhase(phaseNumber).getNumberOfMolesInPhase() / 1.0e30);

    newSystem.init(0);
    // newSystem.init(1);
    return newSystem;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface phaseToSystem(PhaseInterface newPhase) {
    // TODO: other phaseToSystem functions returns clones.
    for (int i = 0; i < newPhase.getNumberOfComponents(); i++) {
      newPhase.getComponents()[i]
          .setNumberOfmoles(newPhase.getComponents()[i].getNumberOfMolesInPhase());
    }

    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      phaseArray[i] = newPhase.clone();
    }

    setTotalNumberOfMoles(newPhase.getNumberOfMolesInPhase());
    this.init(0);
    setNumberOfPhases(1);
    setPhaseType(0, newPhase.getType());
    initBeta();
    init_x_y();
    this.init(1);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface phaseToSystem(String phaseName) {
    try {
      for (int j = 0; j < getMaxNumberOfPhases(); j++) {
        if (this.getPhase(j).getPhaseTypeName().equals(phaseName)) {
          return phaseToSystem(j);
        }
      }
    } catch (Exception ex) {
      logger.error("error....." + fluidName + " has no phase .... " + phaseName
          + " ..... returning phase number 0");
    }
    return phaseToSystem(0);
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface phaseToSystem(int phaseNumber) {
    SystemInterface newSystem = this.clone();

    for (int j = 0; j < getMaxNumberOfPhases(); j++) {
      for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
        newSystem.getPhase(j).getComponent(i)
            .setNumberOfmoles(getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase());
        newSystem.getPhase(j).getComponent(i).setNumberOfMolesInPhase(
            getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase());
      }
    }

    newSystem.setTotalNumberOfMoles(getPhase(phaseNumber).getNumberOfMolesInPhase());

    newSystem.init(0);
    newSystem.setNumberOfPhases(1);
    newSystem.setPhaseType(0, getPhase(phaseNumber).getType()); // phaseType[phaseNumber]);
    newSystem.init(1);
    return newSystem;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface phaseToSystem(int phaseNumber1, int phaseNumber2) {
    SystemInterface newSystem = this.clone();

    for (int j = 0; j < getMaxNumberOfPhases(); j++) {
      for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
        newSystem.getPhases()[j].getComponents()[i]
            .setNumberOfmoles(getPhase(phaseNumber1).getComponents()[i].getNumberOfMolesInPhase()
                + getPhase(phaseNumber2).getComponents()[i].getNumberOfMolesInPhase());
        newSystem.getPhases()[j].getComponents()[i].setNumberOfMolesInPhase(
            getPhase(phaseNumber1).getComponents()[i].getNumberOfMolesInPhase()
                + getPhase(phaseNumber2).getComponents()[i].getNumberOfMolesInPhase());
      }
    }

    newSystem.setTotalNumberOfMoles(getPhase(phaseNumber1).getNumberOfMolesInPhase()
        + getPhase(phaseNumber2).getNumberOfMolesInPhase());

    newSystem.init(0);

    newSystem.setNumberOfPhases(1);
    // newSystem.setPhaseType(0,
    // getPhase(phaseNumber1).getType()); //phaseType[phaseNumber]);
    newSystem.init(1);
    return newSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void setTotalFlowRate(double flowRate, String flowunit) {
    init(0);
    try {
      init(1);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    double density = 0.0;
    if (flowunit.equals("Am3/hr") || flowunit.equals("Am3/min") || flowunit.equals("Am3/sec")) {
      initPhysicalProperties("density");
    }

    density = getPhase(0).getDensity("kg/m3");
    if (flowunit.equals("idSm3/hr")) {
      density = getIdealLiquidDensity("kg/m3");
    }
    neqsim.util.unit.Unit unit =
        new neqsim.util.unit.RateUnit(flowRate, flowunit, getMolarMass(), density, 0);
    double SIval = unit.getSIvalue();
    double totalNumberOfMolesLocal = totalNumberOfMoles;
    for (int i = 0; i < numberOfComponents; i++) {
      if (flowRate < 1e-100) {
        setEmptyFluid();
      } else if (totalNumberOfMolesLocal > 1e-100) {
        // (SIval / totalNumberOfMolesLocal - 1) * ...
        double change =
            SIval / totalNumberOfMolesLocal * getPhase(0).getComponent(i).getNumberOfmoles()
                - getPhase(0).getComponent(i).getNumberOfmoles();
        if (Math.abs(change) > 1e-12) {
          addComponent(i, change);
        }
      } else {
        addComponent(i, SIval);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getFlowRate(String flowunit) {
    if (flowunit.equals("kg/sec")) {
      return totalNumberOfMoles * getMolarMass();
    } else if (flowunit.equals("kg/min")) {
      return totalNumberOfMoles * getMolarMass() * 60.0;
    } else if (flowunit.equals("kg/hr")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0;
    } else if (flowunit.equals("kg/day")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0 * 24.0;
    } else if (flowunit.equals("m3/sec")) {
      initPhysicalProperties("density");
      return totalNumberOfMoles * getMolarMass() / getDensity("kg/m3");
      // return getVolume() / 1.0e5;
    } else if (flowunit.equals("m3/min")) {
      initPhysicalProperties("density");
      return totalNumberOfMoles * getMolarMass() * 60.0 / getDensity("kg/m3");
      // return getVolume() / 1.0e5 * 60.0;
    } else if (flowunit.equals("m3/hr")) {
      // return getVolume() / 1.0e5 * 3600.0;
      initPhysicalProperties("density");
      return totalNumberOfMoles * getMolarMass() * 3600.0 / getDensity("kg/m3");
    } else if (flowunit.equals("idSm3/hr")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0 / getIdealLiquidDensity("kg/m3");
    } else if (flowunit.equals("Sm3/sec")) {
      return totalNumberOfMoles * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("Sm3/hr")) {
      return totalNumberOfMoles * 3600.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("Sm3/day")) {
      return totalNumberOfMoles * 3600.0 * 24.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("MSm3/day")) {
      return totalNumberOfMoles * 3600.0 * 24.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm / 1.0e6;
    } else if (flowunit.equals("MSm3/hr")) {
      return totalNumberOfMoles * 3600.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm / 1.0e6;
    } else if (flowunit.equals("mole/sec")) {
      return totalNumberOfMoles;
    } else if (flowunit.equals("mole/min")) {
      return totalNumberOfMoles * 60.0;
    } else if (flowunit.equals("mole/hr")) {
      return totalNumberOfMoles * 3600.0;
    } else {
      throw new RuntimeException("failed.. unit: " + flowunit + " not supported");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addSalt(String componentName, double value) {
    double val1 = 1e-20;
    double val2 = 1e-20;
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database
            .getResultSet("SELECT * FROM compsalt WHERE SaltName='" + componentName + "'")) {
      dataSet.next();
      String name1 = dataSet.getString("ion1").trim();
      val1 = Double.parseDouble(dataSet.getString("stoc1")) * value;
      this.addComponent(name1, val1);

      String name2 = dataSet.getString("ion2").trim();
      val2 = Double.parseDouble(dataSet.getString("stoc2")) * value;
      this.addComponent(name2, val2);
      logger.info("ok adding salts. Ions: " + name1 + ", " + name2);
    } catch (Exception ex) {
      logger.error("failed ", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addTBPfraction(String componentName, double numberOfMoles, double molarMass,
      double density) {
    if (density < 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "addTBPfraction", "density", "is negative."));
    }
    if (molarMass < 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "addTBPfraction", "molarMass", "is negative."));
    }

    SystemInterface refSystem = null;
    double TC = 0.0;
    double PC = 0.0;
    double m = 0.0;
    double TB = 0.0;
    double acs = 0.0;
    // double penelouxC = 0.0;
    double racketZ = 0.0;
    componentName = (componentName.split("_PC")[0]) + "_PC"; // + getFluidName());

    try {
      refSystem = this.getClass().getDeclaredConstructor().newInstance();
      refSystem.setTemperature(273.15 + 15.0);
      refSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
      refSystem.addComponent("default", 1.0, 273.15, 50.0, 0.1);
      refSystem.init(0);
      refSystem.setNumberOfPhases(1);
      refSystem.setPhaseType(0, PhaseType.LIQUID);
      molarMass = 1000 * molarMass;
      TC = characterization.getTBPModel().calcTC(molarMass, density);
      PC = characterization.getTBPModel().calcPC(molarMass, density);
      m = characterization.getTBPModel().calcm(molarMass, density);
      acs = characterization.getTBPModel().calcAcentricFactor(molarMass, density);
      // TBPfractionCoefs[2][0]+TBPfractionCoefs[2][1]*molarMass+TBPfractionCoefs[2][2]*density+TBPfractionCoefs[2][3]*Math.pow(molarMass,2.0);
      TB = characterization.getTBPModel().calcTB(molarMass, density);
      // Math.pow((molarMass/5.805e-5*Math.pow(density,0.9371)), 1.0/2.3776);
      // acs = TBPfractionModel.calcAcentricFactor(molarMass, density);
      // System.out.println("acentric " + acs);
      // 3.0/7.0*Math.log10(PC/ThermodynamicConstantsInterface.referencePressure)/(TC/TB-1.0)-1.0;
      molarMass /= 1000.0;

      for (int i = 0; i < refSystem.getNumberOfPhases(); i++) {
        refSystem.getPhase(i).getComponent(0).setComponentName(componentName);
        refSystem.getPhase(i).getComponent(0).setMolarMass(molarMass);
        refSystem.getPhase(i).getComponent(0).setAcentricFactor(acs);
        refSystem.getPhase(i).getComponent(0).setTC(TC);
        refSystem.getPhase(i).getComponent(0).setPC(PC);
        refSystem.getPhase(i).getComponent(0).setComponentType("TBPfraction");
        refSystem.getPhase(i).getComponent(0).setIsTBPfraction(true);
        if (characterization.getTBPModel().isCalcm()) {
          refSystem.getPhase(i).getComponent(0).getAttractiveTerm().setm(m);
          acs = refSystem.getPhase(i).getComponent(0).getAcentricFactor();
        }
      }

      refSystem.setTemperature(273.15 + 15.0);
      refSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
      refSystem.init(1);
      // refSystem.display();
      racketZ = characterization.getTBPModel().calcRacketZ(refSystem, molarMass * 1000.0, density);

      // System.out.println("vol ok");
      // System.out.println("racketZ " + racketZ);
      // penelouxC = (refSystem.getPhase(1).getMolarVolume() - molarMass/density*1e2);
      // System.out.println("vol err " +
      // penelouxC/refSystem.getPhase(1).getMolarVolume()*100);
      // racketZ = TPBracketcoefs[0] -
      // penelouxC/(TPBracketcoefs[1]*thermo.ThermodynamicConstantsInterface.R*refSystem.getPhase(1).getComponent(0).getTC()/(refSystem.getPhase(1).getComponent(0).getPC()));
      refSystem.getPhase(0).getComponent(0).setRacketZ(racketZ);
      // refSystem.init(1);
      // refSystem.display();
      // refSystem.getPhase(1).getComponent(0).setRacketZ(racketZ);

      // // refSystem.setTemperature(273.15+80.0);
      // // refSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
      // // refSystem.init(1);
      // //refSystem.initPhysicalProperties();
      // // APIdens - refSystem.getPhase(1).getPhysicalProperties().getDensity();
      // sammenligne med API-standard for tetthet - og sette Penloux dt
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    double critVol = characterization.getTBPModel().calcCriticalVolume(molarMass * 1000, density);
    // 0.2918-0.0928*acs)*ThermodynamicConstantsInterface.R*TC/PC*10.0;
    addComponent(componentName, numberOfMoles, TC, PC, acs);
    double Kwatson = Math.pow(TB * 1.8, 1.0 / 3.0) / density;
    // System.out.println("watson " + Kwatson);
    double CF = Math.pow((12.8 - Kwatson) * (10.0 - Kwatson) / (10.0 * acs), 2.0);
    double acsKeslerLee = acs; // characterization.getTBPModel().calcAcentricFactorKeslerLee(molarMass*1000.0,
                               // density);
    double cpa = (-0.33886 + 0.02827 * Kwatson - 0.26105 * CF + 0.59332 * acsKeslerLee * CF)
        * 4.18682 * molarMass * 1e3;
    double cpb = (-(0.9291 - 1.1543 * Kwatson + 0.0368 * Kwatson * Kwatson) * 1e-4
        + CF * (4.56 - 9.48 * acsKeslerLee) * 1e-4) * 4.18682 * molarMass * 1.8 * 1e3;
    double cpc = (-1.6658e-7 + CF * (0.536 - 0.6828 * acsKeslerLee) * 1.0e-7) * 4.18682 * molarMass
        * 1.8 * 1.8 * 1.0e3;
    double cpd = 0.0;

    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).setAttractiveTerm(attractiveTermNumber);
      getPhase(i).getComponent(componentName).setMolarMass(molarMass);
      getPhase(i).getComponent(componentName).setComponentType("TBPfraction");
      getPhase(i).getComponent(componentName).setNormalLiquidDensity(density);
      getPhase(i).getComponent(componentName).setNormalBoilingPoint(TB - 273.15);
      getPhase(i).getComponent(componentName)
          .setAcentricFactor(refSystem.getPhase(0).getComponent(0).getAcentricFactor());
      getPhase(i).getComponent(componentName).setCriticalVolume(critVol);
      getPhase(i).getComponent(componentName).setRacketZ(racketZ);
      getPhase(i).getComponent(componentName).setRacketZCPA(racketZ);
      getPhase(i).getComponent(componentName).setIsTBPfraction(true);
      getPhase(i).getComponent(componentName).setParachorParameter(
          characterization.getTBPModel().calcParachorParameter(molarMass, density)); // 59.3+2.34*molarMass*1000.0);
                                                                                     // //0.5003*thermo.ThermodynamicConstantsInterface.R*TC/PC*(0.25969-racketZ));
      getPhase(i).getComponent(componentName).setCriticalViscosity(
          characterization.getTBPModel().calcCriticalViscosity(molarMass * 1000.0, density)); // 7.94830*Math.sqrt(1e3*molarMass)*Math.pow(PC,2.0/3.0)/Math.pow(TC,
                                                                                              // 1.0/6.0)*1e-7);
      getPhase(i).getComponent(componentName).setTriplePointTemperature(
          374.5 + 0.02617 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
              - 20172.0 / (getPhase(i).getComponent(componentName).getMolarMass() * 1000.0));
      getPhase(i).getComponent(componentName).setHeatOfFusion(
          0.1426 / 0.238845 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
              * getPhase(i).getComponent(componentName).getTriplePointTemperature());
      getPhase(i).getComponent(componentName)
          .setIdealGasEnthalpyOfFormation(-1462600 * molarMass - 47566.0);
      // getPhase(i).getComponent(componentName).set

      // System.out.println(" plusTC " + TC + " plusPC " + PC + " plusm " + m + "
      // acslusm " + acs + " tb " + TB + " critvol " + critVol + " racketZ " + racketZ
      // + " parachor " +
      // getPhase(i).getComponent(componentName).getParachorParameter());
      getPhase(i).getComponent(componentName).setCpA(cpa);
      getPhase(i).getComponent(componentName).setCpB(cpb);
      getPhase(i).getComponent(componentName).setCpC(cpc);
      getPhase(i).getComponent(componentName).setCpD(cpd);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addTBPfraction(String componentName, double numberOfMoles, double molarMass,
      double density, double criticalTemperature, double criticalPressure, double acentricFactor) {
    if (density < 0.0 || molarMass < 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "addTBPfraction", "density", "is negative."));
    }

    if (density < 0.0 || molarMass < 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "addTBPfraction", "molarMass", "is negative."));
    }

    SystemInterface refSystem = null;
    double TC = 0.0;
    double PC = 0.0;
    double m = 0.0;
    double TB = 0.0;
    double acs = 0.0;
    // double penelouxC = 0.0;
    double racketZ = 0.0;
    componentName = (componentName.split("_PC")[0]) + "_PC"; // + getFluidName());

    try {
      refSystem = this.getClass().getDeclaredConstructor().newInstance();
      refSystem.setTemperature(273.15 + 15.0);
      refSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
      refSystem.addComponent("default", 1.0, 273.15, 50.0, 0.1);
      refSystem.init(0);
      refSystem.setNumberOfPhases(1);
      refSystem.setPhaseType(0, PhaseType.LIQUID);
      molarMass = 1000 * molarMass;
      // characterization.getTBPModel().calcTC(molarMass, density);
      TC = criticalTemperature;
      // characterization.getTBPModel().calcPC(molarMass, density);
      PC = criticalPressure;
      m = characterization.getTBPModel().calcm(molarMass, density);
      // acentracentrcharacterization.getTBPModel().calcAcentricFactor(molarMass,
      // density);
      acs = acentricFactor;
      TB = characterization.getTBPModel().calcTB(molarMass, density);
      molarMass /= 1000.0;

      for (int i = 0; i < refSystem.getNumberOfPhases(); i++) {
        refSystem.getPhase(i).getComponent(0).setComponentName(componentName);
        refSystem.getPhase(i).getComponent(0).setMolarMass(molarMass);
        refSystem.getPhase(i).getComponent(0).setAcentricFactor(acs);
        refSystem.getPhase(i).getComponent(0).setTC(TC);
        refSystem.getPhase(i).getComponent(0).setPC(PC);
        refSystem.getPhase(i).getComponent(0).setComponentType("TBPfraction");
        refSystem.getPhase(i).getComponent(0).setIsTBPfraction(true);
        if (characterization.getTBPModel().isCalcm()) {
          refSystem.getPhase(i).getComponent(0).getAttractiveTerm().setm(m);
          acs = refSystem.getPhase(i).getComponent(0).getAcentricFactor();
        }
      }

      refSystem.setTemperature(273.15 + 15.0);
      refSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
      refSystem.init(1);
      // refSystem.display();
      racketZ = characterization.getTBPModel().calcRacketZ(refSystem, molarMass * 1000.0, density);

      // System.out.println("vol ok");
      // System.out.println("racketZ " + racketZ);
      // penelouxC = (refSystem.getPhase(1).getMolarVolume() - molarMass/density*1e2);
      // System.out.println("vol err " +
      // penelouxC/refSystem.getPhase(1).getMolarVolume()*100);
      // racketZ = TPBracketcoefs[0] -
      // penelouxC/(TPBracketcoefs[1]*thermo.ThermodynamicConstantsInterface.R*refSystem.getPhase(1).getComponent(0).getTC()/(refSystem.getPhase(1).getComponent(0).getPC()));
      refSystem.getPhase(0).getComponent(0).setRacketZ(racketZ);
      // refSystem.init(1);
      // refSystem.display();
      // refSystem.getPhase(1).getComponent(0).setRacketZ(racketZ);

      // // refSystem.setTemperature(273.15+80.0);
      // // refSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
      // // refSystem.init(1);
      // // refSystem.initPhysicalProperties();
      // // APIdens - refSystem.getPhase(1).getPhysicalProperties().getDensity();
      // // sammenligne med API-standard for tetthet - og sette Penloux dt
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    double critVol = characterization.getTBPModel().calcCriticalVolume(molarMass * 1000, density); // 0.2918-0.0928*
                                                                                                   // acs)*ThermodynamicConstantsInterface.R*TC/PC*10.0;
    addComponent(componentName, numberOfMoles, TC, PC, acs);
    double Kwatson = Math.pow(TB * 1.8, 1.0 / 3.0) / density;
    // System.out.println("watson " + Kwatson);
    double CF = Math.pow((12.8 - Kwatson) * (10.0 - Kwatson) / (10.0 * acs), 2.0);
    // characterization.getTBPModel().calcAcentricFactorKeslerLee(molarMass*1000.0,
    // density);
    double acsKeslerLee = acs;
    double cpa = (-0.33886 + 0.02827 * Kwatson - 0.26105 * CF + 0.59332 * acsKeslerLee * CF)
        * 4.18682 * molarMass * 1e3;
    double cpb = (-(0.9291 - 1.1543 * Kwatson + 0.0368 * Kwatson * Kwatson) * 1e-4
        + CF * (4.56 - 9.48 * acsKeslerLee) * 1e-4) * 4.18682 * molarMass * 1.8 * 1e3;
    double cpc = (-1.6658e-7 + CF * (0.536 - 0.6828 * acsKeslerLee) * 1.0e-7) * 4.18682 * molarMass
        * 1.8 * 1.8 * 1.0e3;
    double cpd = 0.0;

    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).setAttractiveTerm(attractiveTermNumber);
      getPhase(i).getComponent(componentName).setMolarMass(molarMass);
      getPhase(i).getComponent(componentName).setComponentType("TBPfraction");
      getPhase(i).getComponent(componentName).setNormalLiquidDensity(density);
      getPhase(i).getComponent(componentName).setNormalBoilingPoint(TB - 273.15);
      getPhase(i).getComponent(componentName)
          .setAcentricFactor(refSystem.getPhase(0).getComponent(0).getAcentricFactor());
      getPhase(i).getComponent(componentName).setCriticalVolume(critVol);
      getPhase(i).getComponent(componentName).setRacketZ(racketZ);
      getPhase(i).getComponent(componentName).setRacketZCPA(racketZ);
      getPhase(i).getComponent(componentName).setIsTBPfraction(true);
      getPhase(i).getComponent(componentName).setParachorParameter(
          characterization.getTBPModel().calcParachorParameter(molarMass, density)); // 59.3+2.34*molarMass*1000.0);
                                                                                     // //0.5003*thermo.ThermodynamicConstantsInterface.R*TC/PC*(0.25969-racketZ));
      getPhase(i).getComponent(componentName).setCriticalViscosity(
          characterization.getTBPModel().calcCriticalViscosity(molarMass * 1000.0, density)); // 7.94830*Math.sqrt(1e3*molarMass)*Math.pow(PC,2.0/3.0)/Math.pow(TC,
                                                                                              // 1.0/6.0)*1e-7);
      getPhase(i).getComponent(componentName).setTriplePointTemperature(
          374.5 + 0.02617 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
              - 20172.0 / (getPhase(i).getComponent(componentName).getMolarMass() * 1000.0));
      getPhase(i).getComponent(componentName).setHeatOfFusion(
          0.1426 / 0.238845 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
              * getPhase(i).getComponent(componentName).getTriplePointTemperature());
      getPhase(i).getComponent(componentName)
          .setIdealGasEnthalpyOfFormation(-1462600 * molarMass - 47566.0);
      // getPhase(i).getComponent(componentName).set

      // System.out.println(" plusTC " + TC + " plusPC " + PC + " plusm " + m + "
      // acslusm " + acs + " tb " + TB + " critvol " + critVol + " racketZ " + racketZ
      // + " parachor " +
      // getPhase(i).getComponent(componentName).getParachorParameter());
      getPhase(i).getComponent(componentName).setCpA(cpa);
      getPhase(i).getComponent(componentName).setCpB(cpb);
      getPhase(i).getComponent(componentName).setCpC(cpc);
      getPhase(i).getComponent(componentName).setCpD(cpd);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addPlusFraction(String componentName, double numberOfMoles, double molarMass,
      double density) {
    addTBPfraction(componentName, numberOfMoles, molarMass, density);
    componentName = (componentName + "_" + "PC"); // getFluidName());
    for (int i = 0; i < numberOfPhases; i++) {
      // System.out.println("comp " + componentName);
      getPhase(i).getComponent(componentName).setIsPlusFraction(true);
      getPhase(i).getComponent(componentName).setCriticalViscosity(
          7.94830 * Math.sqrt(1e3 * getPhase(i).getComponent(componentName).getMolarMass())
              * Math.pow(getPhase(i).getComponent(componentName).getPC(), 2.0 / 3.0)
              / Math.pow(getPhase(i).getComponent(componentName).getTC(), 1.0 / 6.0) * 1e-7);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(ComponentInterface inComponent) {
    if (inComponent.isIsTBPfraction()) {
      addTBPfraction(inComponent.getComponentName(), inComponent.getNumberOfmoles(),
          inComponent.getMolarMass(), inComponent.getNormalLiquidDensity());
      String componentName = inComponent.getComponentName();
      changeComponentName(componentName + "_PC", componentName.replaceFirst("_PC", ""));
      for (int i = 0; i < numberOfPhases; i++) {
        getPhase(i).getComponent(componentName)
            .setAttractiveTerm(inComponent.getAttractiveTermNumber());
        getPhase(i).getComponent(componentName).setTC(inComponent.getTC());
        getPhase(i).getComponent(componentName).setPC(inComponent.getPC());
        getPhase(i).getComponent(componentName).setMolarMass(inComponent.getMolarMass());
        getPhase(i).getComponent(componentName).setComponentType("TBPfraction");
        getPhase(i).getComponent(componentName)
            .setNormalLiquidDensity(inComponent.getNormalLiquidDensity());
        getPhase(i).getComponent(componentName)
            .setNormalBoilingPoint(inComponent.getNormalBoilingPoint());
        getPhase(i).getComponent(componentName).setAcentricFactor(inComponent.getAcentricFactor());
        getPhase(i).getComponent(componentName).setCriticalVolume(inComponent.getCriticalVolume());
        getPhase(i).getComponent(componentName).setRacketZ(inComponent.getRacketZ());
        getPhase(i).getComponent(componentName).setRacketZCPA(inComponent.getRacketZCPA());
        getPhase(i).getComponent(componentName).setIsTBPfraction(true);
        getPhase(i).getComponent(componentName)
            .setParachorParameter(inComponent.getParachorParameter());
        getPhase(i).getComponent(componentName)
            .setTriplePointTemperature(inComponent.getTriplePointTemperature());
        getPhase(i).getComponent(componentName)
            .setIdealGasEnthalpyOfFormation(inComponent.getIdealGasEnthalpyOfFormation());
        getPhase(i).getComponent(componentName).setCpA(inComponent.getCpA());
        getPhase(i).getComponent(componentName).setCpB(inComponent.getCpB());
        getPhase(i).getComponent(componentName).setCpC(inComponent.getCpC());
        getPhase(i).getComponent(componentName).setCpD(inComponent.getCpD());
      }
    } else {
      addComponent(inComponent.getComponentName(), inComponent.getNumberOfmoles());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double moles) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);

    int index = 0;

    boolean addForFirstTime = true;
    for (int p = 0; p < componentNames.size(); p++) {
      if (componentNames.get(p).equals(componentName)) {
        addForFirstTime = false;
        index = p;
        break;
      }
    }

    if (addForFirstTime) {
      if (!neqsim.util.database.NeqSimDataBase.hasComponent(componentName)) {
        throw new RuntimeException(
            new neqsim.util.exception.InvalidInputException(this, "addComponent", "componentName",
                "with value " + componentName + " not found in database."));
      }
      if (moles < 0.0) {
        String msg = "is negative input for component: " + componentName;
        throw new RuntimeException(
            new neqsim.util.exception.InvalidInputException(this, "addComponent", "moles", msg));
      }
      // System.out.println("adding " + componentName);
      componentNames.add(componentName);
      for (int i = 0; i < getMaxNumberOfPhases(); i++) {
        getPhase(i).addComponent(componentName, moles, moles, numberOfComponents);
        getPhase(i).setAttractiveTerm(attractiveTermNumber);
      }
      numberOfComponents++;
    } else {
      for (PhaseInterface tmpPhase : phaseArray) {
        if (tmpPhase != null
            && (tmpPhase.getComponent(componentName).getNumberOfMolesInPhase() + moles) < 0.0) {
          init(0);
          break;
        }
      }

      // System.out.println("adding chem reac " + componentName);
      for (PhaseInterface tmpPhase : phaseArray) {
        // TODO: adding moles to all phases, not just the active ones.
        if (tmpPhase != null) {
          tmpPhase.addMolesChemReac(index, moles, moles);
        }
      }
    }
    setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double value, String unitName) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);

    if (!neqsim.util.database.NeqSimDataBase.hasComponent(componentName)) {
      throw new RuntimeException("No component with name: " + componentName + " in database");
    }

    double molarmass = 0.0;
    double stddens = 0.0;
    double boilp = 0.0;
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet =
            database.getResultSet(("SELECT * FROM comp WHERE name='" + componentName + "'"))) {
      dataSet.next();
      molarmass = Double.parseDouble(dataSet.getString("molarmass")) / 1000.0;
      stddens = Double.parseDouble(dataSet.getString("stddens"));
      boilp = Double.parseDouble(dataSet.getString("normboil"));
    } catch (Exception ex) {
      // todo: mole amount may be not set. should not be caught?
      logger.error("failed ", ex);
    }
    neqsim.util.unit.Unit unit =
        new neqsim.util.unit.RateUnit(value, unitName, molarmass, stddens, boilp);
    double SIval = unit.getSIvalue();
    // System.out.println("number of moles " + SIval);
    this.addComponent(componentName, SIval);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double moles, double TC, double PC, double acs) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);

    String comNam = componentName;
    if (getPhase(0).hasComponent(componentName)) {
      addComponent(componentName, moles);
    } else {
      addComponent("default", moles);
      comNam = "default";
      // componentNames.set(componentNames.indexOf("default"), componentName);
    }
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      getPhase(i).getComponent(comNam).setComponentName(componentName);
      getPhase(i).getComponent(componentName).setTC(TC);
      getPhase(i).getComponent(componentName).setPC(PC);
      getPhase(i).getComponent(componentName).setAcentricFactor(acs);
    }
    if (comNam.equals("default")) {
      componentNames.remove("default");
      componentNames.add(componentName);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double moles, int phaseNumber) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);

    if (!neqsim.util.database.NeqSimDataBase.hasComponent(componentName)) {
      throw new RuntimeException("No component with name: " + componentName + " in database");
    }

    for (int p = 0; p < componentNames.size(); p++) {
      if (componentNames.get(p).equals(componentName)) {
        addComponent(p, moles, phaseNumber);
        return;
      }
    }

    // Add new component
    if (moles < 0.0) {
      String msg = "Negative input number of moles.";
      neqsim.util.exception.InvalidInputException ex =
          new neqsim.util.exception.InvalidInputException(this, "addComponent", "moles", msg);
      throw new RuntimeException(ex);
    }

    componentNames.add(componentName);
    double k = 1.0;
    setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);

    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (phaseNumber == i) {
        k = 1.0;
      } else {
        k = 1.0e-30;
      }
      getPhase(i).addComponent(componentName, moles, moles * k, numberOfComponents);
      getPhase(i).setAttractiveTerm(attractiveTermNumber);
    }
    numberOfComponents++;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double value, String name, int phase) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);

    if (!neqsim.util.database.NeqSimDataBase.hasComponent(componentName)) {
      throw new RuntimeException("No component with name: " + componentName + " in database");
    }

    double molarmass = 0.0;
    double stddens = 0.0;
    double boilp = 0.0;
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet =
            database.getResultSet(("SELECT * FROM comp WHERE name='" + componentName + "'"))) {
      dataSet.next();
      molarmass = Double.parseDouble(dataSet.getString("molarmass")) / 1000.0;
      stddens = Double.parseDouble(dataSet.getString("stddens"));
      boilp = Double.parseDouble(dataSet.getString("normboil"));
    } catch (Exception ex) {
      logger.error("failed ", ex);
      throw new RuntimeException(ex);
    }
    neqsim.util.unit.Unit unit =
        new neqsim.util.unit.RateUnit(value, name, molarmass, stddens, boilp);
    double SIval = unit.getSIvalue();
    // System.out.println("number of moles " + SIval);
    this.addComponent(componentName, SIval, phase);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(int index, double moles) {
    if (index >= getPhase(0).getNumberOfComponents()) {
      logger.error("componentIndex higher than number of components in system");
      return;
    }

    for (PhaseInterface tmpPhase : phaseArray) {
      // TODO: adding moles to all phases, not just the active ones.
      if (tmpPhase != null) {
        tmpPhase.addMolesChemReac(index, moles, moles);
      }
    }
    setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(int index, double moles, int phaseNumber) {
    if (index >= getPhase(0).getNumberOfComponents()) {
      logger.error("componentIndex higher than number of components in system");
      return;
    }
    double k = 1.0;

    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (phaseNumber == i) {
        k = 1.0;
      } else {
        k = 0.0;
      }
      phaseArray[phaseIndex[i]].addMolesChemReac(index, moles * k, moles);
    }

    setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);
  }

  /** {@inheritDoc} */
  @Override
  public void changeComponentName(String name, String newName) {
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentNames.get(i).equals(name)) {
        componentNames.set(i, newName);
      }
    }

    for (int i = 0; i < maxNumberOfPhases; i++) {
      getPhase(i).getComponent(name).setComponentName(newName);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void removeComponent(String name) {
    name = ComponentInterface.getComponentNameFromAlias(name);

    setTotalNumberOfMoles(
        getTotalNumberOfMoles() - phaseArray[0].getComponent(name).getNumberOfmoles());
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      getPhase(i).removeComponent(name, getTotalNumberOfMoles(),
          phaseArray[phaseIndex[i]].getComponent(name).getNumberOfMolesInPhase(),
          phaseArray[phaseIndex[i]].getComponent(name).getComponentNumber());
    }

    componentNames.remove(name);
    // System.out.println("removing " + componentNames.toString());
    numberOfComponents--;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getComponentNames() {
    ArrayList<String> components = new ArrayList<String>();

    for (int j = 0; j < numberOfComponents; j++) {
      components.add(phaseArray[0].getComponents()[j].getName());
    }
    String[] componentList = new String[components.size()];
    for (int j = 0; j < numberOfComponents; j++) {
      componentList[j] = components.get(j);
    }
    return componentList;
  }

  /** {@inheritDoc} */
  @Override
  public void setComponentNames(String[] componentNames) {
    for (int i = 0; i < componentNames.length; i++) {
      this.componentNames.set(i, componentNames[i]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void renameComponent(String oldName, String newName) {
    componentNames.set(getPhase(0).getComponent(oldName).getComponentNumber(), newName);
    for (int i = 0; i < maxNumberOfPhases; i++) {
      getPhase(i).getComponent(oldName).setComponentName(newName);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addToComponentNames(String postfix) {
    for (int j = 0; j < componentNames.size(); j++) {
      componentNames.set(j, componentNames.get(j) + postfix);
    }
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      for (int j = 0; j < componentNames.size(); j++) {
        getPhase(i).getComponent(j)
            .setComponentName(getPhase(i).getComponent(j).getComponentName() + postfix);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getComponentNameTag() {
    return componentNameTag;
  }

  /** {@inheritDoc} */
  @Override
  public void setComponentNameTag(String nameTag) {
    componentNameTag = nameTag;
    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      renameComponent(componentNames.get(i), componentNames.get(i) + nameTag);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setComponentNameTagOnNormalComponents(String nameTag) {
    componentNameTag = nameTag;
    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      if (!getPhase(0).getComponent(i).isIsTBPfraction()
          && !getPhase(0).getComponent(i).isIsPlusFraction()) {
        renameComponent(componentNames.get(i), componentNames.get(i) + nameTag);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setEmptyFluid() {
    for (PhaseInterface tmpPhase : phaseArray) {
      if (tmpPhase != null) {
        tmpPhase.setEmptyFluid();
      }
    }
    totalNumberOfMoles = 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public final double calcBeta() throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    ComponentInterface[] compArray = getPhase(0).getComponents();

    int i;
    double tolerance = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    double deriv = 0.0;
    double gbeta = 0.0;
    double betal = 0;
    double nybeta = 0;

    double midler = 0;
    double minBeta = tolerance;
    double maxBeta = 1.0 - tolerance;
    double g0 = -1.0;
    double g1 = 1.0;
    nybeta = this.beta[0];
    betal = 1.0 - nybeta;

    for (i = 0; i < numberOfComponents; i++) {
      midler = (compArray[i].getK() * compArray[i].getz() - 1.0) / (compArray[i].getK() - 1.0);
      if ((midler > minBeta) && (compArray[i].getK() > 1.0)) {
        minBeta = midler;
      }
      midler = (1.0 - compArray[i].getz()) / (1.0 - compArray[i].getK());
      if ((midler < maxBeta) && (compArray[i].getK() < 1.0)) {
        maxBeta = midler;
      }
      g0 += compArray[i].getz() * compArray[i].getK();
      g1 += -compArray[i].getz() / compArray[i].getK();
    }

    if (g0 < 0) {
      this.beta[1] = 1.0 - tolerance;
      this.beta[0] = tolerance;
      return this.beta[0];
    }
    if (g1 > 0) {
      this.beta[1] = tolerance;
      this.beta[0] = 1.0 - tolerance;
      return this.beta[0];
    }

    nybeta = (minBeta + maxBeta) / 2.0;
    // System.out.println("guessed beta: " + nybeta + " maxbeta: " +maxBeta + "
    // minbeta: " +minBeta );
    betal = 1.0 - nybeta;

    // ' *l = 1.0-nybeta;
    double gtest = 0.0;
    for (i = 0; i < numberOfComponents; i++) {
      gtest += compArray[i].getz() * (compArray[i].getK() - 1.0)
          / (1.0 - nybeta + nybeta * compArray[i].getK()); // beta
                                                           // =
                                                           // nybeta
    }

    if (gtest >= 0) {
      minBeta = nybeta;
    } else {
      maxBeta = nybeta;
    }

    if (gtest < 0) {
      double minold = minBeta;
      minBeta = 1.0 - maxBeta;
      maxBeta = 1.0 - minold;
    }

    int iterations = 0;
    int maxIterations = 300;
    // System.out.println("gtest: " + gtest);
    double step = 1.0;
    do {
      iterations++;
      if (gtest >= 0) {
        // oldbeta = nybeta;
        deriv = 0.0;
        gbeta = 0.0;

        for (i = 0; i < numberOfComponents; i++) {
          double temp1 = (compArray[i].getK() - 1.0);
          double temp2 = 1.0 + temp1 * nybeta;
          deriv += -(compArray[i].getz() * temp1 * temp1) / (temp2 * temp2);
          gbeta += compArray[i].getz() * (compArray[i].getK() - 1.0)
              / (1.0 + (compArray[i].getK() - 1.0) * nybeta);
        }

        if (gbeta >= 0) {
          minBeta = nybeta;
        } else {
          maxBeta = nybeta;
        }
        nybeta -= (gbeta / deriv);

        // System.out.println("beta: " + maxBeta);
        if (nybeta > maxBeta) {
          nybeta = maxBeta;
        }
        if (nybeta < minBeta) {
          nybeta = minBeta;
        }

        /*
         * if ((nybeta > maxBeta) || (nybeta < minBeta)) { // nybeta = 0.5 * (maxBeta + minBeta);
         * gbeta = 1.0; }
         */
      } else {
        // oldbeta = betal;
        deriv = 0.0;
        gbeta = 0.0;

        for (i = 0; i < numberOfComponents; i++) {
          deriv -= (compArray[i].getz() * (compArray[i].getK() - 1.0) * (1.0 - compArray[i].getK()))
              / Math.pow((betal + (1 - betal) * compArray[i].getK()), 2);
          gbeta += compArray[i].getz() * (compArray[i].getK() - 1.0)
              / (betal + (-betal + 1.0) * compArray[i].getK());
        }

        if (gbeta < 0) {
          minBeta = betal;
        } else {
          maxBeta = betal;
        }

        betal -= (gbeta / deriv);

        if (betal > maxBeta) {
          betal = maxBeta;
        }
        if (betal < minBeta) {
          betal = minBeta;
        }

        /*
         * if ((betal > maxBeta) || (betal < minBeta)) { gbeta = 1.0; { betal = 0.5 * (maxBeta +
         * minBeta); } }
         */
        nybeta = 1.0 - betal;
      }
      step = gbeta / deriv;
      // System.out.println("step : " + step);
    } while (Math.abs(step) >= 1.0e-10 && iterations < maxIterations); // &&
    // (Math.abs(nybeta)-Math.abs(maxBeta))>0.1);

    // System.out.println("beta: " + nybeta + " iterations: " + iterations);
    if (nybeta <= tolerance) {
      // this.phase = 1;
      nybeta = tolerance;
    } else if (nybeta >= 1.0 - tolerance) {
      // this.phase = 0;
      nybeta = 1.0 - tolerance;
      // superheated vapour
    } else {
      // this.phase = 2;
    } // two-phase liquid-gas

    this.beta[0] = nybeta;
    this.beta[1] = 1.0 - nybeta;

    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "calcBeta", maxIterations);
    }
    if (Double.isNaN(beta[1])) {
      /*
       * for (i = 0; i < numberOfComponents; i++) { System.out.println("K " + compArray[i].getK());
       * System.out.println("z " + compArray[i].getz()); }
       */
      throw new neqsim.util.exception.IsNaNException(this, "calcBeta", "beta");
    }
    return this.beta[0];
  }

  /** {@inheritDoc} */
  @Override
  public final void initBeta() {
    for (int i = 0; i < numberOfPhases; i++) {
      this.beta[phaseIndex[i]] = getPhase(i).getNumberOfMolesInPhase() / getTotalNumberOfMoles();
    }
    if (this.getSumBeta() < 1.0 - ThermodynamicModelSettings.phaseFractionMinimumLimit
        || this.getSumBeta() > 1.0 + ThermodynamicModelSettings.phaseFractionMinimumLimit) {
      logger.warn("SystemThermo:initBeta - Sum of beta does not equal 1.0");
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient(String unit) {
    double JTcoef = getJouleThomsonCoefficient();
    double conversionFactor = 1.0;
    switch (unit) {
      case "K/bar":
        conversionFactor = 1.0;
        break;
      case "C/bar":
        conversionFactor = 1.0;
        break;
      default:
        break;
    }
    return JTcoef * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    double JTcoef = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      JTcoef += getBeta(i) * getPhase(i).getJouleThomsonCoefficient();
    }
    return JTcoef;
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed(String unit) {
    double refVel = getSoundSpeed();
    double conversionFactor = 1.0;
    switch (unit) {
      case "m/s":
        conversionFactor = 1.0;
        break;
      case "km/hr":
        conversionFactor = 3.6;
        break;
      default:
        break;
    }
    return refVel * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    double soundspeed = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      soundspeed += getBeta(i) * getPhase(i).getSoundSpeed();
    }
    return soundspeed;
  }

  /** {@inheritDoc} */
  @Override
  public final void initTotalNumberOfMoles(double change) {
    setTotalNumberOfMoles(getTotalNumberOfMoles() + change);
    // System.out.println("total moles: " + totalNumberOfMoles);
    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        getPhase(j).getComponents()[i]
            .setNumberOfmoles(phaseArray[phaseIndex[0]].getComponents()[i].getNumberOfmoles());
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void init_x_y() {
    // double x, z;
    for (int j = 0; j < numberOfPhases; j++) {
      // x = 0;
      // z = 0;
      for (int i = 0; i < numberOfComponents; i++) {
        getPhase(j).getComponents()[i]
            .setz(getPhase(j).getComponents()[i].getNumberOfmoles() / getTotalNumberOfMoles());
        getPhase(j).getComponents()[i].setx(getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
            / getPhase(j).getNumberOfMolesInPhase());
      }
      getPhase(j).normalize();
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void calc_x_y() {
    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        if (j == 0) {
          getPhase(j).getComponent(i)
              .setx(getPhase(0).getComponent(i).getK() * getPhase(j).getComponents()[i].getz()
                  / (1 - beta[phaseIndex[0]]
                      + beta[phaseIndex[0]] * getPhase(0).getComponent(i).getK()));
        } else if (j == 1) {
          getPhase(j).getComponent(i).setx(getPhase(0).getComponent(i).getz() / (1.0
              - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponent(i).getK()));
        }
        // phaseArray[j].getComponents()[i].setx(phaseArray[0].getComponents()[i].getx()
        // / phaseArray[0].getComponents()[i].getK());
        // System.out.println("comp: " + j + i + " " + c[j][i].getx());
      }
      getPhase(j).normalize();
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void calc_x_y_nonorm() {
    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        if (j == 0) {
          getPhase(j).getComponents()[i].setx(getPhase(j).getComponents()[i].getK()
              * getPhase(j).getComponents()[i].getz() / (1 - beta[phaseIndex[0]]
                  + beta[phaseIndex[0]] * getPhase(0).getComponents()[i].getK()));
        }
        if (j == 1) {
          getPhase(j).getComponents()[i].setx(getPhase(0).getComponents()[i].getz() / (1.0
              - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponents()[i].getK()));
        }
        // phaseArray[j].getComponents()[i].setx(phaseArray[0].getComponents()[i].getx()
        // / phaseArray[0].getComponents()[i].getK());
        // System.out.println("comp: " + j + i + " " + c[j][i].getx());
      }
      // getPhase(j).normalize();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void reset_x_y() {
    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        getPhase(j).getComponents()[i].setx(phaseArray[phaseIndex[0]].getComponents()[i].getz());
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void reset() {
    for (int i = 0; i < numberOfComponents; i++) {
      // TODO: numeric issue, nearly zero
      addComponent(getPhase(0).getComponent(i).getComponentName(),
          -getPhase(0).getComponent(i).getNumberOfmoles());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void init(int type) {
    if (!this.isInitialized) {
      initBeta();
      init_x_y();
    }
    if (this.numericDerivatives) {
      initNumeric(type);
    } else {
      initAnalytic(type);
    }
    this.isInitialized = true;
  }

  /** {@inheritDoc} */
  @Override
  public void init(int type, int phase) {
    if (this.numericDerivatives) {
      initNumeric(type, phase);
    } else {
      initAnalytic(type, phase);
    }
  }

  /**
   * <p>
   * initAnalytic.
   * </p>
   *
   * @param type a int. 0 to initialize and 1 to reset, 2 to calculate T and P derivatives, 3 to
   *        calculate all derivatives and 4 to calculate all derivatives numerically
   */
  public void initAnalytic(int type) {

    if (type == 0) {
      reInitPhaseInformation();

      for (int i = 0; i < getMaxNumberOfPhases(); i++) {
        if (isPhase(i)) {
          getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, type,
              phaseType[phaseIndex[i]], beta[phaseIndex[i]]);
        }
      }
      setNumberOfPhases(2);
    } else if (type == 1) {
      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, 1, phaseType[phaseIndex[i]],
              beta[phaseIndex[i]]);
        }
      }

      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          for (int j = 0; j < numberOfComponents; j++) {
            getPhase(i).getComponents()[j].fugcoef(getPhase(i));
          }
        }
      }
    } else if (type == 2) { // calculate T and P derivatives
      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, 2, phaseType[phaseIndex[i]],
              beta[phaseIndex[i]]);
        }
      }

      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          for (int j = 0; j < numberOfComponents; j++) {
            getPhase(i).getComponents()[j].fugcoef(getPhase(i));
            getPhase(i).getComponents()[j].logfugcoefdT(getPhase(i));
            getPhase(i).getComponents()[j].logfugcoefdP(getPhase(i));
          }
        }
      }
    } else if (type == 3) { // calculate all derivatives
      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, 3, phaseType[phaseIndex[i]],
              beta[phaseIndex[i]]);
        }
      }

      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          for (int j = 0; j < numberOfComponents; j++) {
            getPhase(i).getComponents()[j].fugcoef(getPhase(i));
            getPhase(i).getComponents()[j].logfugcoefdT(getPhase(i));
            getPhase(i).getComponents()[j].logfugcoefdP(getPhase(i));
            getPhase(i).getComponents()[j].logfugcoefdN(getPhase(i));
          }
        }
      }
    } else if (type == 4) { // calculate all derivatives numerically
      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, 3, phaseType[phaseIndex[i]],
              beta[phaseIndex[i]]);
        }
      }
      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          for (int j = 0; j < numberOfComponents; j++) {
            getPhase(i).getComponents()[j].fugcoef(getPhase(i));
            getPhase(i).getComponents()[j].fugcoefDiffTempNumeric(getPhase(i), numberOfComponents,
                getPhase(i).getTemperature(), getPhase(i).getPressure());
            getPhase(i).getComponents()[j].fugcoefDiffPresNumeric(getPhase(i), numberOfComponents,
                getPhase(i).getTemperature(), getPhase(i).getPressure());
          }
        }
      }
    }

    for (int i = 1; i < numberOfPhases; i++) {
      if (isPhase(i)) {
        if (getPhase(i).getType() == PhaseType.GAS) {
          getPhase(i).setType(PhaseType.OIL);
        }
      }
    }
    this.isInitialized = true;
  }

  /**
   * <p>
   * initAnalytic.
   * </p>
   *
   * @param type a int
   * @param phase a int
   */
  public void initAnalytic(int type, int phase) {
    if (type == 0) {
      beta[0] = 1.0;
      phaseIndex[phase] = phase;
    }

    if (isPhase(phase)) {
      getPhase(phase).init(getTotalNumberOfMoles(), numberOfComponents, type,
          phaseType[phaseIndex[phase]], beta[phaseIndex[phase]]);
      if (type > 0) {
        for (int j = 0; j < numberOfComponents; j++) {
          getPhase(phase).getComponents()[j].fugcoef(getPhase(phase));
        }
      }
      if (type > 1) {
        for (int j = 0; j < numberOfComponents; j++) {
          getPhase(phase).getComponents()[j].logfugcoefdT(getPhase(phase));
          getPhase(phase).getComponents()[j].logfugcoefdP(getPhase(phase));
        }
      }
      if (type > 2) {
        for (int j = 0; j < numberOfComponents; j++) {
          getPhase(phase).getComponents()[j].logfugcoefdT(getPhase(phase));
          getPhase(phase).getComponents()[j].logfugcoefdP(getPhase(phase));
          getPhase(phase).getComponents()[j].logfugcoefdN(getPhase(phase));
        }
      }
    }

    for (PhaseInterface tmpPhase : phaseArray) {
      if (tmpPhase != null && tmpPhase.getType() == PhaseType.GAS) {
        tmpPhase.setType(PhaseType.OIL);
      }
    }

    this.isInitialized = true;
  }

  /**
   * <p>
   * initNumeric.
   * </p>
   *
   * @param type a int
   */
  public void initNumeric(int type) {
    initNumeric(type, 1);
  }

  /**
   * <p>
   * initNumeric.
   * </p>
   *
   * @param type a int
   * @param phasen a int
   */
  public void initNumeric(int type, int phasen) {
    if (type < 2) {
      initAnalytic(type);
    } else if (type >= 2) {
      double[][] gasfug = new double[2][getPhases()[0].getNumberOfComponents()];
      double[][] liqfug = new double[2][getPhases()[0].getNumberOfComponents()];

      double dt = getTemperature() / 1.0e6;
      setTemperature(getTemperature() + dt);
      init(1);

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        gasfug[0][i] = Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
        liqfug[0][i] = Math.log(getPhases()[1].getComponents()[i].getFugacityCoefficient());
      }

      setTemperature(getTemperature() - 2 * dt);
      init(1);

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        gasfug[1][i] = Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
        liqfug[1][i] = Math.log(getPhases()[1].getComponents()[i].getFugacityCoefficient());
      }

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        getPhase(0).getComponent(i).setdfugdt((gasfug[0][i] - gasfug[1][i]) / (2 * dt));
        getPhase(1).getComponent(i).setdfugdt((liqfug[0][i] - liqfug[1][i]) / (2 * dt));
      }

      setTemperature(getTemperature() + dt);

      double dp = getPressure() / 1.0e6;
      setPressure(getPressure() + dp);
      init(1);

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        gasfug[0][i] = Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
        liqfug[0][i] = Math.log(getPhases()[1].getComponents()[i].getFugacityCoefficient());
      }

      setPressure(getPressure() - 2 * dp);
      init(1);

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        gasfug[1][i] = Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
        liqfug[1][i] = Math.log(getPhases()[1].getComponents()[i].getFugacityCoefficient());
      }

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        getPhase(0).getComponent(i).setdfugdp((gasfug[0][i] - gasfug[1][i]) / (2 * dp));
        getPhase(1).getComponent(i).setdfugdp((liqfug[0][i] - liqfug[1][i]) / (2 * dp));
      }

      setPressure(getPressure() + dp);
      init(1);

      if (type == 3) {
        for (int phase = 0; phase < 2; phase++) {
          for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
            double dn = getPhases()[phase].getComponents()[k].getNumberOfMolesInPhase() / 1.0e6;

            addComponent(k, dn, phase);
            // initBeta();
            init_x_y();
            init(1);

            for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
              liqfug[0][i] =
                  Math.log(getPhases()[phase].getComponents()[i].getFugacityCoefficient());
            }

            addComponent(k, -2.0 * dn, phase);
            // initBeta();
            init_x_y();
            init(1);

            for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
              // gasfug[1][i] =
              // Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
              liqfug[1][i] =
                  Math.log(getPhases()[phase].getComponents()[i].getFugacityCoefficient());
            }
            addComponent(k, dn, phase);
            init_x_y();
            init(1);

            for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
              getPhase(phase).getComponent(k).setdfugdn(i,
                  (liqfug[0][i] - liqfug[1][i]) / (2 * dn));
              getPhase(phase).getComponent(k).setdfugdx(i, (liqfug[0][i] - liqfug[1][i]) / (2 * dn)
                  * getPhase(phase).getNumberOfMolesInPhase());
            }
            // initBeta();
          }
        }
      }
    }
    this.isInitialized = true;
  }

  /** {@inheritDoc} */
  @Override
  public void initNumeric() {
    double[][] gasfug = new double[2][getPhases()[0].getNumberOfComponents()];
    double[][] liqfug = new double[2][getPhases()[0].getNumberOfComponents()];
    double[][] gasnumericDfugdt = new double[2][getPhases()[0].getNumberOfComponents()];
    double[][] liqnumericDfugdt = new double[2][getPhases()[0].getNumberOfComponents()];
    double[][] gasnumericDfugdp = new double[2][getPhases()[0].getNumberOfComponents()];
    double[][] liqnumericDfugdp = new double[2][getPhases()[0].getNumberOfComponents()];
    double[][][] gasnumericDfugdn = new double[2][getPhases()[0]
        .getNumberOfComponents()][getPhases()[0].getNumberOfComponents()];
    double[][][] liqnumericDfugdn = new double[2][getPhases()[0]
        .getNumberOfComponents()][getPhases()[0].getNumberOfComponents()];

    double dt = getTemperature() / 1e5;
    setTemperature(getTemperature() + dt);
    init(1);

    for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[0][i] = Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
      liqfug[0][i] = Math.log(getPhases()[1].getComponents()[i].getFugacityCoefficient());
    }

    setTemperature(getTemperature() - 2 * dt);
    init(1);

    for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[1][i] = Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
      liqfug[1][i] = Math.log(getPhases()[1].getComponents()[i].getFugacityCoefficient());
      gasnumericDfugdt[0][i] = (gasfug[0][i] - gasfug[1][i]) / (2 * dt);
      liqnumericDfugdt[0][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dt);
      phaseArray[0].getComponents()[i].setdfugdt(gasnumericDfugdt[0][i]);
      phaseArray[1].getComponents()[i].setdfugdt(liqnumericDfugdt[0][i]);
    }

    setTemperature(getTemperature() + dt);

    double dp = getPressure() / 1e5;
    setPressure(getPressure() + dp);
    init(1);

    for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[0][i] = Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
      liqfug[0][i] = Math.log(getPhases()[1].getComponents()[i].getFugacityCoefficient());
    }

    setPressure(getPressure() - 2 * dp);
    init(1);

    for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[1][i] = Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
      liqfug[1][i] = Math.log(getPhases()[1].getComponents()[i].getFugacityCoefficient());
      gasnumericDfugdp[0][i] = (gasfug[0][i] - gasfug[1][i]) / (2 * dp);
      liqnumericDfugdp[0][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dp);
      phaseArray[0].getComponents()[i].setdfugdp(gasnumericDfugdp[0][i]);
      phaseArray[1].getComponents()[i].setdfugdp(liqnumericDfugdp[0][i]);
    }

    setPressure(getPressure() + dp);
    init(1);

    for (int phase = 0; phase < 2; phase++) {
      for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
        double dn = getPhases()[phase].getComponents()[k].getNumberOfMolesInPhase() / 1.0e6;
        if (dn < 1e-12) {
          dn = 1e-12;
        }

        addComponent(k, dn, phase);
        // initBeta();
        init_x_y();
        init(1);

        for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
          liqfug[0][i] = Math.log(getPhases()[phase].getComponents()[i].getFugacityCoefficient());
        }

        addComponent(k, -2.0 * dn, phase);
        // initBeta();
        init_x_y();
        init(1);

        for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
          // gasfug[1][i] =
          // Math.log(getPhases()[0].getComponents()[i].getFugacityCoefficient());
          liqfug[1][i] = Math.log(getPhases()[phase].getComponents()[i].getFugacityCoefficient());
        }

        for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
          if (phase == 0) {
            gasnumericDfugdn[0][k][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dn);
            phaseArray[0].getComponents()[i].setdfugdn(k, gasnumericDfugdn[0][k][i]);
            phaseArray[0].getComponents()[i].setdfugdx(k,
                gasnumericDfugdn[0][k][i] * phaseArray[0].getNumberOfMolesInPhase());
          }

          if (phase == 1) {
            liqnumericDfugdn[0][k][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dn);
            phaseArray[1].getComponents()[i].setdfugdn(k, liqnumericDfugdn[0][k][i]);
            phaseArray[1].getComponents()[i].setdfugdx(k,
                liqnumericDfugdn[0][k][i] * phaseArray[1].getNumberOfMolesInPhase());
          }
        }

        addComponent(k, dn, phase);
        // initBeta();
        init_x_y();
        init(1);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initProperties() {
    initThermoProperties();
    initPhysicalProperties();
  }

  /** {@inheritDoc} */
  @Override
  public void initPhysicalProperties() {
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).initPhysicalProperties();
    }
    calcInterfaceProperties();
  }

  /** {@inheritDoc} */
  @Override
  public void initPhysicalProperties(String propertyName) {
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).initPhysicalProperties(propertyName);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void resetPhysicalProperties() {
    for (PhaseInterface tmpPhase : phaseArray) {
      if (tmpPhase != null) {
        tmpPhase.resetPhysicalProperties();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initRefPhases() {
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).initRefPhases(false);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPhysicalPropertyModel(int type) {
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).setPhysicalProperties(type);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void chemicalReactionInit() {
    chemicalReactionOperations = new ChemicalReactionOperations(this);
    chemicalSystem = chemicalReactionOperations.hasReactions();
  }

  /** {@inheritDoc} */
  @Override
  public ChemicalReactionOperations getChemicalReactionOperations() {
    return chemicalReactionOperations;
  }

  /**
   * Verify if system has a phase of a specific type.
   *
   * @param pt PhaseType to look for.
   * @return True if system contains a phase of requested type.
   */
  public boolean hasPhaseType(PhaseType pt) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (getPhase(i) == null) {
        continue;
      }
      if (getPhase(i).getType() == pt) {
        return true;
      }
      if (getPhase(i).getPhaseTypeName().equals(pt.getDesc())) {
        logger.error(
            "Bug in setting phasetype somewhere. Phasetype and phasetypename should be the same.");
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public final PhaseInterface getGasPhase() {
    for (int phase = 0; phase < numberOfPhases; phase++) {
      if (phaseArray[phaseIndex[phase]].getType() == PhaseType.GAS) {
        return phaseArray[phase];
      }
    }
    logger.info("No gas phase at current state.");
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public final PhaseInterface getLiquidPhase() {
    for (int phase = 0; phase < numberOfPhases; phase++) {
      if (phaseArray[phaseIndex[phase]].getType() == PhaseType.LIQUID) {
        return phaseArray[phase];
      }
    }
    logger.info("No liquid phase at current state.");
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isPhase(int i) {
    // TODO: what if i > numberofphases?
    if (i > phaseArray.length) {
      return false;
    }

    // getPhase(i) without try/catch
    return phaseArray[phaseIndex[i]] != null;
  }

  /** {@inheritDoc} */
  @Override
  public final PhaseInterface getPhase(int i) {
    if (i < 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getPhase",
          "i", i + " is not valid, must be in the range 0-" + this.getNumberOfPhases()));
    } else if (i >= getNumberOfPhases() && phaseArray[phaseIndex[i]] == null) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          this.getClass() + ":getPhase - Can not return phase number " + i
              + ". Current number of phases are " + getNumberOfPhases()));
    }
    return phaseArray[phaseIndex[i]];
  }

  /** {@inheritDoc} */
  @Override
  public PhaseInterface getPhase(PhaseType pt) {
    if (!this.hasPhaseType(pt)) {
      throw new RuntimeException("Phase with phase type " + pt + " not found.");
    }

    int phaseNum = getPhaseNumberOfPhase(pt);
    if (phaseNum >= 0) {
      return getPhase(phaseNum);
    }

    return null;
  }

  /** {@inheritDoc} */
  @Override
  public PhaseInterface getPhase(String phaseTypeName) {
    PhaseType pt = PhaseType.byDesc(phaseTypeName);
    return getPhase(pt);
  }

  /** {@inheritDoc} */
  @Override
  public PhaseInterface getPhaseOfType(String phaseTypeName) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (getPhase(i).getPhaseTypeName().equals(phaseTypeName)) {
        return getPhase(i);
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public int getPhaseNumberOfPhase(PhaseType pt) {
    // TODO: returning first if not found, not same as the others.
    for (int i = 0; i < numberOfPhases; i++) {
      if (getPhase(i).getType() == pt) {
        return i;
      }
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public final int getPhaseIndex(int index) {
    return phaseIndex[index];
  }

  /** {@inheritDoc} */
  @Override
  public int getPhaseIndex(String phaseTypeName) {
    // TODO: returning first if not found, not same as the others.
    for (int i = 0; i < numberOfPhases; i++) {
      if (getPhase(i).getPhaseTypeName().equals(phaseTypeName)) {
        return phaseIndex[i];
      }
    }
    return phaseIndex[0];
  }

  /** {@inheritDoc} */
  @Override
  public int getPhaseIndex(PhaseInterface phase) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (getPhase(i) == phase) {
        return phaseIndex[i];
      }
    }
    throw new RuntimeException(
        new InvalidInputException(this, "getPhaseIndex", "phase", "is not found in phaseArray."));
  }

  /** {@inheritDoc} */
  @Override
  public final void setPhaseIndex(int index, int phaseIndex) {
    this.phaseIndex[index] = phaseIndex;
  }

  /** {@inheritDoc} */
  @Override
  public final boolean isChemicalSystem() {
    return chemicalSystem;
  }

  /** {@inheritDoc} */
  @Override
  public final void isChemicalSystem(boolean temp) {
    chemicalSystem = temp;
  }

  /**
   * <p>
   * getAntoineVaporPressure.
   * </p>
   *
   * @param temp a double
   * @return a double
   */
  public double getAntoineVaporPressure(double temp) {
    return phaseArray[0].getAntoineVaporPressure(temp);
  }

  /** {@inheritDoc} */
  @Override
  public final double getTC() {
    return criticalTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public final double getPC() {
    return criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public final void setTC(double TC) {
    criticalTemperature = TC;
  }

  /** {@inheritDoc} */
  @Override
  public final void setPC(double PC) {
    criticalPressure = PC;
  }

  /**
   * <p>
   * setMixingRuleGEmodel.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setMixingRuleGEmodel(String name) {
    for (PhaseInterface tmpPhase : phaseArray) {
      if (tmpPhase != null) {
        tmpPhase.setMixingRuleGEModel(name);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void setMixingRule(int type) {
    mixingRule = type;
    if (numberOfPhases < 4) {
      resetPhysicalProperties(); // initPhysicalProperties();
    }
    for (int i = 0; i < maxNumberOfPhases; i++) {
      if (isPhase(i)) {
        getPhase(i).setMixingRule(type);
        getPhase(i).initPhysicalProperties();
        // getPhase(i).getPhysicalProperties().getMixingRule().initMixingRules(getPhase(i));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(String typename, String GEmodel) {
    setMixingRuleGEmodel(GEmodel);
    setMixingRule(typename);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(String typename) {
    int var = 0;
    if (typename.equals("no")) {
      var = 1;
    } else if (typename.equals("classic")) {
      var = 2;
    } else if (typename.equals("HV")) {
      var = 4;
    } else if (typename.equals("WS")) {
      var = 5;
    } else if (typename.equals("CPA-Mix")) {
      var = 7;
    } else if (typename.equals("classic-T")) {
      var = 8;
    } else if (typename.equals("classic-T-cpa")) {
      var = 9;
    } else if (typename.equals("classic-Tx-cpa")) {
      var = 10;
    } else {
      var = 1;
    }
    this.setMixingRule(var);
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfPhases(int number) {
    this.numberOfPhases = number;
    if (numberOfPhases > getMaxNumberOfPhases()) {
      setMaxNumberOfPhases(number);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void useVolumeCorrection(boolean volcor) {
    for (PhaseInterface tmpPhase : phaseArray) {
      if (tmpPhase != null) {
        tmpPhase.useVolumeCorrection(volcor);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public final PhaseInterface[] getPhases() {
    return phaseArray;
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    double gibbsEnergy = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      gibbsEnergy += getPhase(i).getGibbsEnergy();
    }
    return gibbsEnergy;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergy(double temperatureOfSurroundings, String exergyUnit) {
    double refExergy = getExergy(temperatureOfSurroundings); // exergy in J
    double conversionFactor = 1.0;
    switch (exergyUnit) {
      case "J":
        conversionFactor = 1.0;
        break;
      case "J/mol":
        conversionFactor = 1.0 / getTotalNumberOfMoles();
        break;
      case "J/kg":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
        break;
      case "kJ/kg":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    return refExergy * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergy(double temperatureOfSurroundings) {
    double getExergy = getEnthalpy() - temperatureOfSurroundings * getEntropy();
    return getExergy;
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    double enthalpy = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      enthalpy += getPhase(i).getEnthalpy();
    }
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy(String unit) {
    double refEnthalpy = getEnthalpy(); // enthalpy in J
    double conversionFactor = 1.0;
    switch (unit) {
      case "J":
        conversionFactor = 1.0;
        break;
      case "kJ/kmol":
      case "J/mol":
        conversionFactor = 1.0 / getTotalNumberOfMoles();
        break;
      case "J/kg":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
        break;
      case "kJ/kg":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refEnthalpy * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosity() {
    double visc = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      visc += beta[phaseIndex[i]] * getPhase(i).getPhysicalProperties().getViscosity();
    }
    return visc;
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosity(String unit) {
    double refViscosity = getViscosity(); // viscosity in kg/msec
    double conversionFactor = 1.0;
    switch (unit) {
      case "kg/msec":
        conversionFactor = 1.0;
        break;
      case "cP":
        conversionFactor = 1.0e3;
        break;
      case "Pas":
        conversionFactor = 1.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refViscosity * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getKinematicViscosity(String unit) {
    double refViscosity = getViscosity("kg/msec") / getDensity("kg/m3"); // viscosity in kg/msec
    double conversionFactor = 1.0;
    switch (unit) {
      case "m2/sec":
        conversionFactor = 1.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refViscosity * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getKinematicViscosity() {
    return getViscosity() / getDensity();
  }

  /** {@inheritDoc} */
  @Override
  public double getThermalConductivity() {
    double cond = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      cond += beta[phaseIndex[i]] * getPhase(i).getPhysicalProperties().getConductivity();
    }
    return cond;
  }

  /** {@inheritDoc} */
  @Override
  public double getThermalConductivity(String unit) {
    double refConductivity = getThermalConductivity(); // conductivity in W/m*K
    double conversionFactor = 1.0;
    switch (unit) {
      case "W/mK":
        conversionFactor = 1.0;
        break;
      case "W/cmK":
        conversionFactor = 0.01;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refConductivity * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy() {
    double internalEnergy = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      internalEnergy += getPhase(i).getInternalEnergy();
    }
    return internalEnergy;
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy(String unit) {
    double refEnthalpy = getInternalEnergy(); // enthalpy in J
    double conversionFactor = 1.0;
    switch (unit) {
      case "J":
        conversionFactor = 1.0;
        break;
      case "J/mole":
        conversionFactor = 1.0 / getTotalNumberOfMoles();
        break;
      case "J/kg":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
        break;
      case "kJ/kg":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refEnthalpy * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getHelmholtzEnergy() {
    double helmholtzEnergy = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      helmholtzEnergy += getPhase(i).getHelmholtzEnergy();
    }
    return helmholtzEnergy;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    double entropy = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      entropy += getPhase(i).getEntropy();
    }
    return entropy;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy(String unit) {
    double refEntropy = getEntropy(); // entropy in J/K
    double conversionFactor = 1.0;
    switch (unit) {
      case "J/K":
        conversionFactor = 1.0;
        break;
      case "J/molK":
        conversionFactor = 1.0 / getTotalNumberOfMoles();
        break;
      case "J/kgK":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refEntropy * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarVolume(String unit) {
    double volume = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      volume += beta[phaseIndex[i]] * getPhase(i).getMolarVolume(unit);
    }
    return volume;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarVolume() {
    double volume = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      volume += beta[phaseIndex[i]] * getPhase(i).getMolarVolume();
    }
    return volume;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    double density = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      density +=
          1.0e5 * (getPhase(i).getMolarMass() * beta[phaseIndex[i]] / getPhase(i).getMolarVolume());
    }
    return density;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity(String unit) {
    double density = 0;
    for (int i = 0; i < getNumberOfPhases(); i++) {
      density +=
          getPhase(i).getVolume() / getVolume() * getPhase(i).getPhysicalProperties().getDensity();
    }
    double refDensity = density; // density in kg/m3
    double conversionFactor = 1.0;
    switch (unit) {
      case "kg/m3":
        conversionFactor = 1.0;
        break;
      case "lb/ft3":
        conversionFactor = 0.0624279606;
        break;
      case "kg/Sm3":
        return getMolarMass() * ThermodynamicConstantsInterface.atm
            / ThermodynamicConstantsInterface.R
            / ThermodynamicConstantsInterface.standardStateTemperature;
      case "mol/m3":
        conversionFactor = 1.0 / getMolarMass();
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refDensity * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getZ() {
    double Z = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      Z += beta[phaseIndex[i]] * getPhase(i).getZ();
    }
    return Z;
  }

  /** {@inheritDoc} */
  @Override
  public double getMoleFractionsSum() {
    double sumz = 0.0;
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      sumz += phaseArray[0].getComponent(i).getz();
    }
    return sumz;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarMass() {
    double tempVar = 0;
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      tempVar +=
          phaseArray[0].getComponents()[i].getz() * phaseArray[0].getComponents()[i].getMolarMass();
    }
    return tempVar;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarMass(String unit) {
    double refMolarMass = getMolarMass();
    double conversionFactor = 1.0;
    switch (unit) {
      case "kg/mol":
        conversionFactor = 1.0;
        break;
      case "gr/mol":
        conversionFactor = 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refMolarMass * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double newTemperature) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      getPhases()[i].setTemperature(newTemperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void setTemperature(double newTemperature, int phase) {
    getPhase(phaseIndex[phase]).setTemperature(newTemperature);
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double newTemperature, String unit) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (unit.equals("K")) {
        getPhases()[i].setTemperature(newTemperature);
      } else if (unit.equals("C")) {
        getPhases()[i].setTemperature(newTemperature + 273.15);
      } else {
        throw new RuntimeException("unit not supported " + unit);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPhaseType(int phaseToChange, PhaseType pt) {
    // System.out.println("new phase type: cha " + pt);
    if (allowPhaseShift) {
      phaseType[phaseIndex[phaseToChange]] = pt;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setAllPhaseType(PhaseType pt) {
    if (allowPhaseShift) {
      for (int i = 0; i < getMaxNumberOfPhases(); i++) {
        setPhaseType(i, pt);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void invertPhaseTypes() {
    // Following code was from public void setPhaseType(int phaseToChange, String phaseTypeName) {

    /*
     * int newPhaseType = 0; if (phaseTypeName.equals("gas")) { newPhaseType = 1; } else if
     * (StateOfMatter.isLiquid(PhaseType.byDesc(phaseTypeName))) { newPhaseType = 0; } else {
     * newPhaseType = 0; }
     */

    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (phaseType[i] == PhaseType.byValue(0)) {
        phaseType[i] = PhaseType.byValue(1);
      } else {
        phaseType[i] = PhaseType.byValue(0);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhaseInterface phase, int index) {
    double temp = phaseArray[index].getTemperature();
    double pres = phaseArray[index].getPressure();
    this.phaseArray[index] = phase;
    this.phaseArray[index].setTemperature(temp);
    this.phaseArray[index].setPressure(pres);
  }

  /** {@inheritDoc} */
  @Override
  public void reInitPhaseType() {
    phaseType[0] = PhaseType.byValue(1);
    phaseType[1] = PhaseType.byValue(0);
    phaseType[2] = PhaseType.byValue(0);
    phaseType[3] = PhaseType.byValue(0);
    // TODO: why stop at 3 and not iterate through MAX_PHASES elements?
  }

  /**
   * Re-initialize phasetype, beta and phaseindex arrays, same initialization which is used in
   * constructor.
   */
  public void reInitPhaseInformation() {
    reInitPhaseType();
    phaseType[4] = phaseType[3];
    phaseType[5] = phaseType[3];

    for (int i = 0; i < MAX_PHASES; i++) {
      beta[i] = 1.0;
    }

    phaseIndex = new int[] {0, 1, 2, 3, 4, 5};
  }

  /** {@inheritDoc} */
  @Override
  public final boolean doSolidPhaseCheck() {
    return solidPhaseCheck;
  }

  /** {@inheritDoc} */
  @Override
  public final void setPressure(double newPressure) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      phaseArray[i].setPressure(newPressure);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void setPressure(double newPressure, String unit) {
    neqsim.util.unit.PressureUnit presConversion =
        new neqsim.util.unit.PressureUnit(newPressure, unit);
    setPressure(presConversion.getValue("bara"));
  }

  /** {@inheritDoc} */
  @Override
  public final double getTemperature() {
    return phaseArray[0].getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public final double getTemperature(String unit) {
    neqsim.util.unit.TemperatureUnit tempConversion =
        new neqsim.util.unit.TemperatureUnit(getTemperature(), "K");
    return tempConversion.getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature(int phaseNumber) {
    return getPhase(phaseIndex[phaseNumber]).getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public final double getPressure() {
    return phaseArray[0].getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public final double getPressure(String unit) {
    neqsim.util.unit.PressureUnit presConversion =
        new neqsim.util.unit.PressureUnit(getPressure(), "bara");
    return presConversion.getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public final double getPressure(int phaseNumber) {
    return getPhase(phaseIndex[phaseNumber]).getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public final double getBeta() {
    return beta[0];
  }

  /** {@inheritDoc} */
  @Override
  public final double getBeta(int phase) {
    return beta[phaseIndex[phase]];
  }

  public final double getSumBeta() {
    double sum = 0;
    for (int k = 0; k < numberOfPhases; k++) {
      sum += getBeta(k);
    }
    return sum;
  }

  /** {@inheritDoc} */
  @Override
  public void setAttractiveTerm(int i) {
    for (int k = 0; k < getMaxNumberOfPhases(); k++) {
      phaseArray[k].setAttractiveTerm(i);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final int getNumberOfPhases() {
    return numberOfPhases;
  }

  /** {@inheritDoc} */
  @Override
  public final void setBeta(double b) {
    if (b < 0) {
      b = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    }
    if (b > 1) {
      b = 1.0 - neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    }
    beta[0] = b;
    beta[1] = 1.0 - b;
  }

  /** {@inheritDoc} */
  @Override
  public final void setBeta(int phase, double b) {
    if (b < 0) {
      b = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    }
    if (b > 1) {
      b = 1.0 - neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    }
    beta[phaseIndex[phase]] = b;
  }

  /** {@inheritDoc} */
  @Override
  public final double getVolume() {
    double volume = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      volume += getPhase(i).getMolarVolume() * getPhase(i).getNumberOfMolesInPhase();
    }
    return volume;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolume(String unit) {
    double conversionFactor = 1.0;
    switch (unit) {
      case "m3":
        conversionFactor = 1.0;
        break;
      case "m3/kg":
        conversionFactor = 1.0 / getMass("kg");
        break;
      case "litre":
        conversionFactor = 1000.0;
        break;
      case "m3/mol":
        conversionFactor = 1.0 / getTotalNumberOfMoles();
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return conversionFactor * getVolume() / 1.0e5;
  }

  /** {@inheritDoc} */
  @Override
  public double getMass(String unit) {
    double conversionFactor = 1.0;
    switch (unit) {
      case "kg":
        conversionFactor = 1.0;
        break;

      case "gr":
        conversionFactor = 1000.0;
        break;
      case "tons":
        conversionFactor = 1.0e-3;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return conversionFactor * getTotalNumberOfMoles() * getMolarMass();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * need to call initPhysicalProperties() before this method is called
   * </p>
   */
  @Override
  public double getCorrectedVolume() {
    double volume = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      volume += getPhase(i).getMolarMass() / getPhase(i).getPhysicalProperties().getDensity()
          * getPhase(i).getNumberOfMolesInPhase();
    }
    return volume;
  }

  /**
   * getPdVtn. TODO: document
   *
   * @return dpdv
   */
  public double getdPdVtn() {
    double dPdV = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      if (isPhase(i)) {
        dPdV += getPhase(i).getdPdVTn() * getPhase(i).getVolume() / getVolume();
      }
    }
    return dPdV;
  }

  /** {@inheritDoc} */
  @Override
  public double getdVdPtn() {
    double dVdP = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      if (isPhase(i)) {
        dVdP += 1.0 / getPhase(i).getdPdVTn();
      }
    }
    return dVdP;
  }

  /** {@inheritDoc} */
  @Override
  public double getdVdTpn() {
    double dVdT = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      if (isPhase(i)) {
        dVdT += -getPhase(i).getdPdTVn() / getPhase(i).getdPdVTn();
      }
    }
    return dVdT;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    double cP = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      cP += getPhase(i).getCp();
    }
    return cP;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp(String unit) {
    double refCp = getCp(); // Cp in J/K
    double conversionFactor = 1.0;
    switch (unit) {
      case "J/K":
        conversionFactor = 1.0;
        break;
      case "J/molK":
        conversionFactor = 1.0 / getTotalNumberOfMoles();
        break;
      case "J/kgK":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refCp * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    double cv = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      cv += getPhase(i).getCv();
    }
    return cv;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv(String unit) {
    double refCv = getCv(); // enthalpy in J
    double conversionFactor = 1.0;
    switch (unit) {
      case "J/K":
        conversionFactor = 1.0;
        break;
      case "J/molK":
        conversionFactor = 1.0 / getTotalNumberOfMoles();
        break;
      case "J/kgK":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refCv * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getKappa() {
    return -getCp() / getCv() * getVolume() / getPressure() * getdPdVtn();
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma() {
    return getCp() / getCv();
  }

  /** {@inheritDoc} */
  @Override
  public void calcInterfaceProperties() {
    interfaceProp.init();
  }

  /** {@inheritDoc} */
  @Override
  public InterphasePropertiesInterface getInterphaseProperties() {
    return interfaceProp;
  }

  /** {@inheritDoc} */
  @Override
  public double getInterfacialTension(int phase1, int phase2) {
    return interfaceProp.getSurfaceTension(phase1, phase2);
  }

  /** {@inheritDoc} */
  @Override
  public double getInterfacialTension(int phase1, int phase2, String unit) {
    return interfaceProp.getSurfaceTension(phase1, phase2, unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getInterfacialTension(String phase1, String phase2) {
    if (hasPhaseType(phase1) && hasPhaseType(phase2)) {
      return interfaceProp.getSurfaceTension(getPhaseNumberOfPhase(phase1),
          getPhaseNumberOfPhase(phase2));
    } else {
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void normalizeBeta() {
    double tot = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      tot += beta[phaseIndex[i]];
    }
    for (int i = 0; i < numberOfPhases; i++) {
      beta[phaseIndex[i]] /= tot;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String[][] createTable(String name) {
    initProperties();

    java.text.DecimalFormat nf = new java.text.DecimalFormat();

    java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
    symbols.setDecimalSeparator('.');
    nf.setDecimalFormatSymbols(symbols);

    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    // String[][] table = new String[getPhases()[0].getNumberOfComponents() +
    // 30][7];
    // String[] names = {"", "Feed", "Phase 1", "Phase 2", "Phase 3", "Phase 4",
    // "Unit"};
    String[][] table = new String[getPhases()[0].getNumberOfComponents() + 30][7];
    table[0][0] = ""; // getPhases()[0].getType(); //"";

    for (int i = 0; i < getPhases()[0].getNumberOfComponents() + 30; i++) {
      for (int j = 0; j < 7; j++) {
        table[i][j] = "";
      }
    }
    table[0][1] = "total";
    for (int i = 0; i < numberOfPhases; i++) {
      table[0][i + 2] = getPhase(i).getType().toString();
    }

    StringBuffer buf = new StringBuffer();
    java.text.FieldPosition test = new java.text.FieldPosition(0);
    for (int j = 0; j < getPhases()[0].getNumberOfComponents(); j++) {
      buf = new StringBuffer();
      table[j + 1][1] = nf.format(getPhase(0).getComponents()[j].getz(), buf, test).toString();
    }
    buf = new StringBuffer();
    table[getPhases()[0].getNumberOfComponents() + 4][1] =
        nf.format(getMolarMass() * 1000, buf, test).toString();
    buf = new StringBuffer();
    table[getPhases()[0].getNumberOfComponents() + 9][1] =
        nf.format(getEnthalpy() / (getTotalNumberOfMoles() * getMolarMass() * 1000), buf, test)
            .toString();
    buf = new StringBuffer();
    table[getPhases()[0].getNumberOfComponents() + 10][1] =
        nf.format(getEntropy() / (getTotalNumberOfMoles() * getMolarMass() * 1000), buf, test)
            .toString();

    for (int i = 0; i < numberOfPhases; i++) {
      for (int j = 0; j < getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = getPhases()[0].getComponents()[j].getName();
        buf = new StringBuffer();
        table[j + 1][i + 2] =
            nf.format(getPhase(i).getComponents()[j].getx(), buf, test).toString();
        table[j + 1][6] = "[mole fraction]";
      }

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
      table[getPhases()[0].getNumberOfComponents() + 2][i + 2] =
          nf.format(getPhase(i).getPhysicalProperties().getDensity(), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 2][6] = "[kg/m^3]";

      // Double.longValue(system.getPhase(i).getBeta());
      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 3][0] = "PhaseFraction";
      table[getPhases()[0].getNumberOfComponents() + 3][i + 2] =
          nf.format(getPhase(i).getBeta(), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 3][6] = "[mole fraction]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
      table[getPhases()[0].getNumberOfComponents() + 4][i + 2] =
          nf.format(getPhase(i).getMolarMass() * 1000, buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 4][6] = "[kg/kmol]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 5][0] = "Z factor";
      table[getPhases()[0].getNumberOfComponents() + 5][i + 2] =
          nf.format(getPhase(i).getZ(), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 5][6] = "[-]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 6][0] = "Heat Capacity (Cp)";
      table[getPhases()[0].getNumberOfComponents() + 6][i + 2] = nf.format(
          (getPhase(i).getCp()
              / (getPhase(i).getNumberOfMolesInPhase() * getPhase(i).getMolarMass() * 1000)),
          buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 6][6] = "[kJ/kg*K]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 7][0] = "Heat Capacity (Cv)";
      table[getPhases()[0].getNumberOfComponents() + 7][i + 2] = nf.format(
          (getPhase(i).getCv()
              / (getPhase(i).getNumberOfMolesInPhase() * getPhase(i).getMolarMass() * 1000)),
          buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 7][6] = "[kJ/kg*K]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 8][0] = "Speed of Sound";
      table[getPhases()[0].getNumberOfComponents() + 8][i + 2] =
          nf.format((getPhase(i).getSoundSpeed()), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 8][6] = "[m/sec]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 9][0] = "Enthalpy";
      table[getPhases()[0].getNumberOfComponents() + 9][i + 2] = nf.format(
          (getPhase(i).getEnthalpy()
              / (getPhase(i).getNumberOfMolesInPhase() * getPhase(i).getMolarMass() * 1000)),
          buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 9][6] = "[kJ/kg]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 10][0] = "Entropy";
      table[getPhases()[0].getNumberOfComponents() + 10][i + 2] = nf.format(
          (getPhase(i).getEntropy()
              / (getPhase(i).getNumberOfMolesInPhase() * getPhase(i).getMolarMass() * 1000)),
          buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 10][6] = "[kJ/kg*K]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 11][0] = "JT coefficient";
      table[getPhases()[0].getNumberOfComponents() + 11][i + 2] =
          nf.format((getPhase(i).getJouleThomsonCoefficient()), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 11][6] = "[K/bar]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 13][0] = "Viscosity";
      table[getPhases()[0].getNumberOfComponents() + 13][i + 2] =
          nf.format((getPhase(i).getPhysicalProperties().getViscosity()), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 13][6] = "[kg/m*sec]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 14][0] = "Conductivity";
      table[getPhases()[0].getNumberOfComponents() + 14][i + 2] =
          nf.format(getPhase(i).getPhysicalProperties().getConductivity(), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 14][6] = "[W/m*K]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 15][0] = "SurfaceTension";
      try {
        if (i < numberOfPhases - 1) {
          table[getPhases()[0].getNumberOfComponents() + 15][2] =
              nf.format(getInterphaseProperties().getSurfaceTension(0, 1), buf, test).toString();
          buf = new StringBuffer();
          table[getPhases()[0].getNumberOfComponents() + 15][3] =
              nf.format(getInterphaseProperties().getSurfaceTension(0, 1), buf, test).toString();
          buf = new StringBuffer();
          if (i == 1) {
            table[getPhases()[0].getNumberOfComponents() + 17][2] =
                nf.format(getInterphaseProperties().getSurfaceTension(0, 2), buf, test).toString();
            buf = new StringBuffer();
            table[getPhases()[0].getNumberOfComponents() + 17][4] =
                nf.format(getInterphaseProperties().getSurfaceTension(0, 2), buf, test).toString();
            table[getPhases()[0].getNumberOfComponents() + 17][6] = "[N/m]";
          }
          if (i == 1) {
            buf = new StringBuffer();
            table[getPhases()[0].getNumberOfComponents() + 16][3] =
                nf.format(getInterphaseProperties().getSurfaceTension(1, 2), buf, test).toString();
            buf = new StringBuffer();
            table[getPhases()[0].getNumberOfComponents() + 16][4] =
                nf.format(getInterphaseProperties().getSurfaceTension(1, 2), buf, test).toString();
            table[getPhases()[0].getNumberOfComponents() + 16][6] = "[N/m]";
          }
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      table[getPhases()[0].getNumberOfComponents() + 15][6] = "[N/m]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 19][0] = "Pressure";
      table[getPhases()[0].getNumberOfComponents() + 19][i + 2] =
          Double.toString(getPhase(i).getPressure());
      table[getPhases()[0].getNumberOfComponents() + 19][6] = "[bar]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 20][0] = "Temperature";
      table[getPhases()[0].getNumberOfComponents() + 20][i + 2] =
          Double.toString(getPhase(i).getTemperature());
      table[getPhases()[0].getNumberOfComponents() + 20][6] = "[K]";
      Double.toString(getPhase(i).getTemperature());

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 22][0] = "Model";
      table[getPhases()[0].getNumberOfComponents() + 22][i + 2] = getModelName();
      table[getPhases()[0].getNumberOfComponents() + 22][6] = "-";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 23][0] = "Mixing Rule";
      try {
        table[getPhases()[0].getNumberOfComponents() + 23][i + 2] =
            ((PhaseEosInterface) getPhase(i)).getMixingRuleName();
      } catch (Exception ex) {
        table[getPhases()[0].getNumberOfComponents() + 23][i + 2] = "?";
        // logger.error(ex.getMessage(),e);
      }
      table[getPhases()[0].getNumberOfComponents() + 23][6] = "-";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 25][0] = "Stream";
      table[getPhases()[0].getNumberOfComponents() + 25][i + 2] = name;
      table[getPhases()[0].getNumberOfComponents() + 25][6] = "-";
    }

    resultTable = table;
    return table;
  }

  /** {@inheritDoc} */
  @Override
  public void display(String name) {
    if (this.getNumberOfComponents() == 0) {
      return;
    }
    javax.swing.JFrame dialog = new javax.swing.JFrame("System-Report");
    java.awt.Dimension screenDimension = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
    java.awt.Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new java.awt.BorderLayout());

    String[] names = {"", "Feed", "Phase 1", "Phase 2", "Phase 3", "Phase 4", "Unit"};
    String[][] table = createTable(name);
    javax.swing.JTable Jtab = new javax.swing.JTable(table, names);
    javax.swing.JScrollPane scrollpane = new javax.swing.JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);

    // setting the size of the frame and text size
    dialog.setSize(screenDimension.width / 2, screenDimension.height / 2); // pack();
    Jtab.setRowHeight(dialog.getHeight() / table.length);
    Jtab.setFont(new java.awt.Font("Serif", java.awt.Font.PLAIN,
        dialog.getHeight() / table.length - dialog.getHeight() / table.length / 10));

    // dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * <p>
   * write.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String write() {
    // create a String description of the system
    return "";
  }

  /** {@inheritDoc} */
  @Override
  public void write(String name, String filename, boolean newfile) {
    String[][] table = createTable(name);
    neqsim.dataPresentation.fileHandeling.createTextFile.TextFile file =
        new neqsim.dataPresentation.fileHandeling.createTextFile.TextFile();
    if (newfile) {
      file.newFile(filename);
    }
    file.setOutputFileName(filename);
    file.setValues(table);
    file.createFile();
  }

  /** {@inheritDoc} */
  @Override
  public void resetDatabase() {
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      if (NeqSimDataBase.createTemporaryTables()) {
        database.execute("delete FROM comptemp");
        database.execute("delete FROM intertemp");
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void createDatabase(boolean reset) {
    if (reset) {
      resetDatabase();
    }

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      String names = new String();

      for (int k = 0; k < getPhase(0).getNumberOfComponents() - 1; k++) {
        names += "'" + this.getComponentNames()[k] + "', ";
      }
      names += "'" + this.getComponentNames()[getPhase(0).getNumberOfComponents() - 1] + "'";

      if (NeqSimDataBase.createTemporaryTables()) {
        database.execute("insert into comptemp SELECT * FROM comp WHERE name IN (" + names + ")");
        database.execute("insert into intertemp SELECT DISTINCT * FROM inter WHERE comp1 IN ("
            + names + ") AND comp2 IN (" + names + ")");
        database.execute("delete FROM intertemp WHERE comp1=comp2");
      }
      // System.out.println("ok " + names);

      for (int phase = 0; phase < maxNumberOfPhases; phase++) {
        getPhase(phase).setMixingRuleDefined(false);
      }

      for (int i = 0; i < numberOfComponents; i++) {
        if (getPhase(0).getComponent(i).isIsTBPfraction()
            || getPhase(0).getComponent(i).isIsPlusFraction()) {
          getPhase(0).getComponent(i).insertComponentIntoDatabase("");
        }
      }
    } catch (Exception ex) {
      logger.error("error in SystemThermo Class...createDatabase() method", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setSolidPhaseCheck(boolean solidPhaseCheck) {
    this.solidPhaseCheck = solidPhaseCheck;

    final int oldphase = numberOfPhases;
    if (solidPhaseCheck && !this.hasSolidPhase()) {
      addSolidPhase();
    }
    // init(0);

    for (int phase = 0; phase < numberOfPhases; phase++) {
      for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
        getPhase(phase).getComponent(k).setSolidCheck(solidPhaseCheck);
        getPhase(3).getComponent(k).setSolidCheck(solidPhaseCheck);
      }
    }
    setNumberOfPhases(oldphase);
  }

  /** {@inheritDoc} */
  @Override
  public void setSolidPhaseCheck(String solidComponent) {
    init(0);
    final int oldphase = numberOfPhases;
    if (!solidPhaseCheck) {
      addSolidPhase();
    }
    this.solidPhaseCheck = true;
    init(0);

    for (int phase = 0; phase < getMaxNumberOfPhases(); phase++) {
      try {
        getPhase(phase).getComponent(solidComponent).setSolidCheck(true);
        getPhase(3).getComponent(solidComponent).setSolidCheck(true);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    setNumberOfPhases(oldphase);
  }

  /** {@inheritDoc} */
  @Override
  public void setHydrateCheck(boolean hydrateCheck) {
    init(0);
    if (hydrateCheck) {
      addHydratePhase();
    }
    this.hydrateCheck = hydrateCheck;
    init(0);
  }

  /** {@inheritDoc} */
  @Override
  public boolean doMultiPhaseCheck() {
    return multiPhaseCheck;
  }

  /** {@inheritDoc} */
  @Override
  public void setMultiPhaseCheck(boolean multiPhaseCheck) {
    if (getMaxNumberOfPhases() < 3) {
      if (multiPhaseCheck) {
        setMaxNumberOfPhases(3);
        if (phaseArray[1] != null) {
          phaseArray[2] = phaseArray[1].clone();
          phaseArray[2].resetMixingRule(phaseArray[0].getMixingRuleNumber());
          phaseArray[2].resetPhysicalProperties();
          phaseArray[2].initPhysicalProperties();
        }
      } else {
        setMaxNumberOfPhases(2);
      }
    }
    this.multiPhaseCheck = multiPhaseCheck;
  }

  /** {@inheritDoc} */
  @Override
  public int getInitType() {
    return initType;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitType(int initType) {
    this.initType = initType;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isNumericDerivatives() {
    return numericDerivatives;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumericDerivatives(boolean numericDerivatives) {
    this.numericDerivatives = numericDerivatives;
  }

  /** {@inheritDoc} */
  @Override
  public void checkStability(boolean val) {
    checkStability = val;
  }

  /** {@inheritDoc} */
  @Override
  public boolean checkStability() {
    return checkStability;
  }

  /** {@inheritDoc} */
  @Override
  public boolean getHydrateCheck() {
    return hydrateCheck;
  }

  /** {@inheritDoc} */
  @Override
  public void save(String name) {
    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(name))) {
      out.writeObject(this);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface readObject(int ID) {
    java.sql.ResultSet rs = null;
    SystemThermo tempSystem = null;
    neqsim.util.database.NeqSimBlobDatabase database =
        new neqsim.util.database.NeqSimBlobDatabase();
    try {
      java.sql.Connection con = database.openConnection();
      String sqlStr = "SELECT FLUID FROM fluid_blobdb WHERE ID=" + Integer.toString(ID);
      java.sql.PreparedStatement ps = con.prepareStatement(sqlStr);
      rs = ps.executeQuery();

      if (rs.next()) {
        try (ObjectInputStream ins =
            new ObjectInputStream(new ByteArrayInputStream(rs.getBytes("FLUID")))) {
          tempSystem = (SystemThermo) ins.readObject();
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    } finally {
      try {
        if (database.getStatement() != null) {
          database.getStatement().close();
        }
        if (database.getConnection() != null) {
          database.getConnection().close();
        }
      } catch (Exception ex) {
        logger.error("err closing database IN MIX...", ex);
      }
    }

    return tempSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void saveFluid(int ID) {
    saveObject(ID, "");
  }

  /** {@inheritDoc} */
  @Override
  public void saveFluid(int ID, String text) {
    saveObject(ID, text);
  }

  /** {@inheritDoc} */
  @Override
  public void saveObject(int ID, String text) {
    ByteArrayOutputStream fout = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(fout)) {
      out.writeObject(this);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    byte[] byteObject = fout.toByteArray();
    ByteArrayInputStream inpStream = new ByteArrayInputStream(byteObject);

    neqsim.util.database.NeqSimBlobDatabase database =
        new neqsim.util.database.NeqSimBlobDatabase();

    try {
      java.sql.Connection con = database.openConnection();

      java.sql.PreparedStatement ps =
          con.prepareStatement("REPLACE INTO fluid_blobdb (ID, FLUID) VALUES (?,?)");
      ps.setInt(1, ID);
      ps.setBlob(2, inpStream);

      ps.executeUpdate();
      /*
       * if (!text.isEmpty()) { ps = con.prepareStatement(
       * "REPLACE INTO fluidinfo (ID, TEXT) VALUES (?,?)"); ps.setInt(1, ID); ps.setString(2, text);
       * }
       * 
       * ps.executeUpdate();
       * 
       */
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    } finally {
      try {
        if (database.getStatement() != null) {
          database.getStatement().close();
        }
        if (database.getConnection() != null) {
          database.getConnection().close();
        }
      } catch (Exception ex) {
        logger.error("err closing database IN MIX...", ex);
      }
    }
    // database.execute("INSERT INTO fluid_blobdb VALUES ('1'," + sqlString + ")");
  }

  /** {@inheritDoc} */
  @Override
  public void saveObjectToFile(String filePath, String fluidName) {
    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath, false))) {
      out.writeObject(this);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface readObjectFromFile(String filePath, String fluidName) {
    SystemThermo tempSystem = null;
    try (ObjectInputStream objectinputstream =
        new ObjectInputStream(new FileInputStream(filePath))) {
      tempSystem = (SystemThermo) objectinputstream.readObject();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return tempSystem;
  }

  /** {@inheritDoc} */
  @Override
  public String getMixingRuleName() {
    return ((PhaseEosInterface) getPhase(0)).getMixingRule().getMixingRuleName();
  }

  /** {@inheritDoc} */
  @Override
  public String getFluidInfo() {
    return fluidInfo;
  }

  /** {@inheritDoc} */
  @Override
  public void setFluidInfo(String info) {
    this.fluidInfo = info;
  }

  /** {@inheritDoc} */
  @Override
  public String getFluidName() {
    return fluidName;
  }

  /** {@inheritDoc} */
  @Override
  public void setFluidName(String fluidName) {
    this.fluidName = fluidName;
  }

  /**
   * <p>
   * setLastTBPasPlus.
   * </p>
   *
   * @return a boolean
   */
  public boolean setLastTBPasPlus() {
    neqsim.thermo.characterization.PlusCharacterize temp =
        new neqsim.thermo.characterization.PlusCharacterize(this);
    if (temp.hasPlusFraction()) {
      return false;
    } else {
      temp.setHeavyTBPtoPlus();
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.thermo.characterization.Characterise getCharacterization() {
    return characterization;
  }

  /** {@inheritDoc} */
  @Override
  public void calcKIJ(boolean ok) {
    neqsim.thermo.mixingRule.EosMixingRules.calcEOSInteractionParameters = ok;
    for (int i = 0; i < numberOfPhases; i++) {
      ((PhaseEosInterface) getPhase(i)).getMixingRule().setCalcEOSInteractionParameters(ok);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getModelName() {
    return modelName;
  }

  /**
   * Setter for property modelName.
   *
   * @param modelName New value of property modelName.
   */
  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  /** {@inheritDoc} */
  @Override
  public boolean allowPhaseShift() {
    return allowPhaseShift;
  }

  /** {@inheritDoc} */
  @Override
  public void allowPhaseShift(boolean allowPhaseShift) {
    this.allowPhaseShift = allowPhaseShift;
  }

  /** {@inheritDoc} */
  @Override
  public double getProperty(String prop, String compName, int phase) {
    if (prop.equals("molefraction")) {
      return getPhase(phase).getComponent(compName).getx();
    } else if (prop.equals("fugacitycoefficient")) {
      return getPhase(phase).getComponent(compName).getFugacityCoefficient();
    } else if (prop.equals("logfugdT")) {
      return getPhase(phase).getComponent(compName).getdfugdt();
    } else if (prop.equals("logfugdP")) {
      return getPhase(phase).getComponent(compName).getdfugdp();
    } else {
      return 1.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getProperty(String prop, int phase) {
    initPhysicalProperties();
    if (prop.equals("temperature")) {
      return getPhase(phase).getTemperature();
    } else if (prop.equals("pressure")) {
      return getPhase(phase).getPressure();
    } else if (prop.equals("compressibility")) {
      return getPhase(phase).getZ();
    } else if (prop.equals("density")) {
      return getPhase(phase).getPhysicalProperties().getDensity();
    } else if (prop.equals("beta")) {
      return getPhase(phase).getBeta();
    } else if (prop.equals("enthalpy")) {
      return getPhase(phase).getEnthalpy();
    } else if (prop.equals("entropy")) {
      return getPhase(phase).getEntropy();
    } else if (prop.equals("viscosity")) {
      return getPhase(phase).getPhysicalProperties().getViscosity();
    } else if (prop.equals("conductivity")) {
      return getPhase(phase).getPhysicalProperties().getConductivity();
    } else {
      return 1.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getProperty(String prop) {
    if (prop.equals("numberOfPhases")) {
      return numberOfPhases;
    } else if (prop.equals("numberOfComponents")) {
      return numberOfComponents;
    } else if (prop.equals("enthalpy")) {
      return getEnthalpy();
    } else if (prop.equals("entropy")) {
      return getEntropy();
    } else if (prop.equals("temperature")) {
      return getTemperature();
    } else if (prop.equals("pressure")) {
      return getPressure();
    } else {
      return 1.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void saveToDataBase() {
    // java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM
    // SYSTEMREPORT"));
    // double molarmass = 0.0, stddens = 0.0, boilp = 0.0;
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      database.execute("delete FROM systemreport");
      int i = 0;
      for (; i < numberOfComponents; i++) {
        String sqlString =
            "'" + Integer.toString(i + 1) + "', '" + getPhase(0).getComponent(i).getName() + "', "
                + "'molfrac[-] ', '" + Double.toString(getPhase(0).getComponent(i).getz()) + "'";

        int j = 0;
        for (; j < numberOfPhases; j++) {
          sqlString += ", '" + Double.toString(getPhase(j).getComponent(i).getx()) + "'";
        }

        while (j < 3) {
          j++;
          sqlString += ", '0'";
        }

        logger.error(sqlString);

        database.execute("INSERT INTO systemreport VALUES (" + sqlString + ")");
      }

      // beta
      i++;

      String sqlString = "'" + Integer.toString(i + 1) + "', 'PhaseFraction', " + "'[-]', '1'";

      int j = 0;
      for (; j < numberOfPhases; j++) {
        sqlString += ", '" + Double.toString(getPhase(j).getBeta()) + "'";
      }

      while (j < 3) {
        j++;
        sqlString += ", '0'";
      }

      logger.error(sqlString);

      database.execute("INSERT INTO systemreport VALUES (" + sqlString + ")");

      // molarmass
      i++;

      sqlString = "'" + Integer.toString(i + 1) + "', 'MolarMass', " + "'kg/mol ', '"
          + Double.toString(getMolarMass()) + "'";

      j = 0;
      for (; j < numberOfPhases; j++) {
        sqlString += ", '" + Double.toString(getPhase(j).getMolarMass()) + "'";
      }
      while (j < 3) {
        j++;
        sqlString += ", '0'";
      }

      // System.out.println(sqlString);
      database.execute("INSERT INTO systemreport VALUES (" + sqlString + ")");

      // dataSet.next();
      // dataSet.updateString("SPECIFICATION", "dette");
      // double test = dataSet.getDouble("Phase1");
      // System.out.println(test);
      // dataSet.next();
      // dataSet.updateString(1,"tesst");
    } catch (Exception ex) {
      logger.error("failed ", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.standards.StandardInterface getStandard() {
    return standard;
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.standards.StandardInterface getStandard(String standardName) {
    this.setStandard(standardName);
    return standard;
  }

  /** {@inheritDoc} */
  @Override
  public void setStandard(String standardName) {
    if (standardName.equals("ISO1992")) {
      this.standard = new neqsim.standards.gasQuality.Standard_ISO6976(this);
    } else if (standardName.equals("Draft_ISO18453")) {
      this.standard = new neqsim.standards.gasQuality.Draft_ISO18453(this);
    } else {
      this.standard = new neqsim.standards.gasQuality.Standard_ISO6976(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasPlusFraction() {
    for (int i = 0; i < numberOfComponents; i++) {
      if (getPhase(0).getComponent(i).isIsPlusFraction()) {
        return true;
      }
    }
    return false;
  }

  /**
   * <p>
   * hasTBPFraction.
   * </p>
   *
   * @return a boolean
   */
  public boolean hasTBPFraction() {
    for (int i = 0; i < numberOfComponents; i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void tuneModel(String model, double val, int phase) {
    if (model.equals("viscosity")) {
      getPhase(phase).getPhysicalProperties().getViscosityModel().tuneModel(val,
          getPhase(phase).getTemperature(), getPhase(phase).getPressure());
      for (int i = 0; i < getMaxNumberOfPhases(); i++) {
        for (int j = 0; j < numberOfPhases; j++) {
          getPhase(i).getComponent(j)
              .setCriticalViscosity(getPhase(phase).getComponent(j).getCriticalViscosity());
        }
      }
    }
    initPhysicalProperties();
  }

  /** {@inheritDoc} */
  @Override
  public double getHeatOfVaporization() {
    if (numberOfPhases < 2) {
      return 0;
    } else {
      return getPhase(0).getEnthalpy() / getPhase(0).getNumberOfMolesInPhase()
          - getPhase(1).getEnthalpy() / getPhase(1).getNumberOfMolesInPhase();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void readFluid(String fluidName) {
    this.fluidName = fluidName;
    try {
      neqsim.util.database.NeqSimFluidDataBase database =
          new neqsim.util.database.NeqSimFluidDataBase();
      java.sql.ResultSet dataSet = null;
      dataSet = database.getResultSet("SELECT * FROM " + fluidName);

      while (dataSet.next()) {
        String componentType = dataSet.getString("ComponentType");

        if (componentType.equalsIgnoreCase("normal")) {
          addComponent(dataSet.getString("ComponentName"),
              Double.parseDouble(dataSet.getString("Rate")));
        } else if (componentType.equalsIgnoreCase("TBP")) {
          addTBPfraction(dataSet.getString("ComponentName"),
              Double.parseDouble(dataSet.getString("Rate")),
              Double.parseDouble(dataSet.getString("MolarMass")) / 1000.0,
              Double.parseDouble(dataSet.getString("Density")));
        } else if (componentType.equalsIgnoreCase("plus")) {
          addPlusFraction(dataSet.getString("ComponentName"),
              Double.parseDouble(dataSet.getString("Rate")),
              Double.parseDouble(dataSet.getString("MolarMass")) / 1000.0,
              Double.parseDouble(dataSet.getString("Density")));
        } else {
          logger.error(
              "component type need to be specified for ... " + dataSet.getString("ComponentName"));
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return resultTable;
  }

  // public String[] getResultArray1(){
  // ArrayList list = new ArrayList();
  // for(int i=0;i<resultTable[0].length;i++){
  // list.add(getResultTable()[0][i].toString());
  // }
  // String[] componentList = new String[list.size()];
  // for (int j=0; j<resultTable[0].length; j++){
  // componentList[j] = (String) list.get(j);
  // }
  // return componentList;
  // }

  /** {@inheritDoc} */
  @Override
  public SystemInterface setModel(String model) {
    SystemInterface tempModel = null;
    try {
      if (model.equals("SRK-EOS")) {
        tempModel = new SystemSrkEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("GERG2004-EOS")) {
        tempModel = new SystemGERG2004Eos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("PrEos") || model.equals("PR-EOS")) {
        tempModel = new SystemPrEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("ScRK-EOS") || model.equals("ScRK-EOS-HV")) {
        tempModel = new SystemSrkSchwartzentruberEos(getPhase(0).getTemperature(),
            getPhase(0).getPressure());
      } else if (model.equals("Electrolyte-ScRK-EOS")) {
        tempModel =
            new SystemFurstElectrolyteEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("GERG-water-EOS")) {
        tempModel = new SystemGERGwaterEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("CPAs-SRK-EOS")) {
        tempModel = new SystemSrkCPAs(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("CPAs-SRK-EOS-statoil")) {
        tempModel =
            new SystemSrkCPAstatoil(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("Electrolyte-CPA-EOS-statoil")
          || model.equals("Electrolyte-CPA-EOS")) {
        tempModel = new SystemElectrolyteCPAstatoil(getPhase(0).getTemperature(),
            getPhase(0).getPressure());
      } else if (model.equals("UMR-PRU-EoS")) {
        tempModel = new SystemUMRPRUMCEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("PC-SAFT")) {
        tempModel = new SystemPCSAFT(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("GERG-2008-EoS")) {
        tempModel = new SystemGERG2004Eos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("SRK-TwuCoon-Statoil-EOS") || model.equals("SRK-TwuCoon-EOS")) {
        tempModel =
            new SystemSrkTwuCoonStatoilEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("SRK-TwuCoon-Param-EOS")) {
        tempModel =
            new SystemSrkTwuCoonParamEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("Duan-Sun")) {
        tempModel = new SystemDuanSun(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else {
        logger.error("model : " + model + " not defined.....");
      }
      // tempModel.getCharacterization().setTBPModel("RiaziDaubert");
      tempModel.useVolumeCorrection(true);

      logger.info("created class " + tempModel);
      for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
        logger.info("adding " + getPhase(0).getComponent(i).getName() + " moles "
            + getPhase(0).getComponent(i).getNumberOfmoles() + " isPlus "
            + getPhase(0).getComponent(i).isIsPlusFraction() + " isTBP "
            + getPhase(0).getComponent(i).isIsTBPfraction());
        if (getPhase(0).getComponent(i).isIsTBPfraction()) {
          tempModel.addTBPfraction(getPhase(0).getComponent(i).getName(),
              getPhase(0).getComponent(i).getNumberOfmoles(),
              getPhase(0).getComponent(i).getMolarMass(),
              getPhase(0).getComponent(i).getNormalLiquidDensity());
        } else if (getPhase(0).getComponent(i).isIsPlusFraction()) {
          tempModel.addPlusFraction(getPhase(0).getComponent(i).getName(),
              getPhase(0).getComponent(i).getNumberOfmoles(),
              getPhase(0).getComponent(i).getMolarMass(),
              getPhase(0).getComponent(i).getNormalLiquidDensity());
        } else {
          tempModel.addComponent(getPhase(0).getComponent(i).getName(),
              getPhase(0).getComponent(i).getNumberOfmoles());
        }
      }

      // if (tempModel.getCharacterization().characterize()) {
      // tempModel.addPlusFraction(6, 100);
      // }
      if (NeqSimDataBase.createTemporaryTables()) {
        logger.info("done ... create database ......");
        tempModel.createDatabase(true);
      }
      logger.info("done ... set mixing rule ......");
      tempModel.autoSelectMixingRule();
      if (model.equals("Electrolyte-ScRK-EOS")) { // ||
                                                  // model.equals("Electrolyte-CPA-EOS-statoil"
        logger.info("chemical reaction init......");
        tempModel.setMultiPhaseCheck(false);
        tempModel.chemicalReactionInit();
      } else {
        tempModel.setMultiPhaseCheck(true);
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return tempModel;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface autoSelectModel() {
    if (this.getPhase(0).hasComponent("MDEA") && this.getPhase(0).hasComponent("water")
        && this.getPhase(0).hasComponent("CO2")) {
      return setModel("Electrolyte-ScRK-EOS");
    } else if (this.getPhase(0).hasComponent("water") || this.getPhase(0).hasComponent("methanol")
        || this.getPhase(0).hasComponent("MEG") || this.getPhase(0).hasComponent("TEG")
        || this.getPhase(0).hasComponent("ethanol") || this.getPhase(0).hasComponent("DEG")) {
      if (this.getPhase(0).hasComponent("Na+") || this.getPhase(0).hasComponent("K+")
          || this.getPhase(0).hasComponent("Br-") || this.getPhase(0).hasComponent("Mg++")
          || this.getPhase(0).hasComponent("Cl-") || this.getPhase(0).hasComponent("Ca++")
          || this.getPhase(0).hasComponent("Fe++") || this.getPhase(0).hasComponent("SO4--")) {
        logger.info("model elect");
        return setModel("Electrolyte-CPA-EOS-statoil");
      } else {
        return setModel("CPAs-SRK-EOS-statoil");
      }
    } else if (this.getPhase(0).hasComponent("water")) {
      return setModel("ScRK-EOS");
    } else if (this.getPhase(0).hasComponent("mercury")) {
      return setModel("SRK-TwuCoon-Statoil-EOS");
    } else {
      logger.info("no model");
      return setModel("SRK-EOS");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void autoSelectMixingRule() {
    logger.info("setting mixing rule");
    if (modelName.equals("CPAs-SRK-EOS") || modelName.equals("CPA-SRK-EOS")
        || modelName.equals("Electrolyte-CPA-EOS-statoil")
        || modelName.equals("CPAs-SRK-EOS-statoil") || modelName.equals("Electrolyte-CPA-EOS")) {
      this.setMixingRule(10);
      // System.out.println("mix rule 10");
    } else if ((modelName.equals("ScRK-EOS-HV") || modelName.equals("SRK-EOS")
        || modelName.equals("ScRK-EOS")) && this.getPhase(0).hasComponent("water")) {
      this.setMixingRule(4);
    } else if (modelName.equals("PR-EOS")) {
      this.setMixingRule(2);
    } else if (modelName.equals("Electrolyte-ScRK-EOS")) {
      this.setMixingRule(4);
    } else if (modelName.equals("UMR-PRU-EoS") || modelName.equals("UMR-PRU-MC-EoS")) {
      this.setMixingRule("HV", "UNIFAC_UMRPRU");
    } else if (modelName.equals("GERG-water-EOS")) {
      this.setMixingRule(8);
    } else if (modelName.equals("GERG-2008-EOS")) {
      this.setMixingRule(2);
    } else if (modelName.equals("PC-SAFT")) {
      this.setMixingRule(8);
    } else {
      this.setMixingRule(2);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getMixingRule() {
    return mixingRule;
  }

  /** {@inheritDoc} */
  @Override
  public void orderByDensity() {
    boolean change = false;
    // int count = 0;

    for (int i = 0; i < getNumberOfPhases(); i++) {
      if (getPhase(i).getPhysicalProperties() == null) {
        getPhase(i).initPhysicalProperties("density");
      }
      getPhase(i).getPhysicalProperties().setPhase(getPhase(i));
    }

    do {
      change = false;
      // count++;
      for (int i = 1; i < getNumberOfPhases(); i++) {
        if (i == 4) {
          break;
        }

        try {
          if (change || getPhase(i).getPhysicalProperties() == null) {
            getPhase(i).initPhysicalProperties("density");
          }
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
        if (getPhase(i).getPhysicalProperties().calcDensity() < getPhase(i - 1)
            .getPhysicalProperties().calcDensity()) {
          int tempIndex1 = getPhaseIndex(i - 1);
          int tempIndex2 = getPhaseIndex(i);
          setPhaseIndex(i, tempIndex1);
          setPhaseIndex(i - 1, tempIndex2);
          change = true;
        }
      }
    } while (change);
  }

  /** {@inheritDoc} */
  @Override
  public void addLiquidToGas(double fraction) {
    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      double change = getPhase(1).getComponent(i).getNumberOfMolesInPhase() * fraction;
      addComponent(i, change, 0);
      addComponent(i, -change, 1);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addPhaseFractionToPhase(double fraction, String specification, String fromPhaseName,
      String toPhaseName) {
    if (!(hasPhaseType(fromPhaseName) && hasPhaseType(toPhaseName) || fraction < 1e-30)) {
      return;
    }
    int phaseNumbFrom = getPhaseNumberOfPhase(fromPhaseName);
    int phaseNumbTo = getPhaseNumberOfPhase(toPhaseName);
    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      double change = getPhase(phaseNumbFrom).getComponent(i).getNumberOfMolesInPhase() * fraction;
      addComponent(i, change, phaseNumbTo);
      addComponent(i, -change, phaseNumbFrom);
    }
    init_x_y();
  }

  /** {@inheritDoc} */
  @Override
  public void addPhaseFractionToPhase(double fraction, String specification, String specifiedStream,
      String fromPhaseName, String toPhaseName) {
    double moleFraction = fraction;
    if (!hasPhaseType(fromPhaseName) || !hasPhaseType(toPhaseName) || fraction < 1e-30) {
      return;
    }
    int phaseNumbFrom = getPhaseNumberOfPhase(fromPhaseName);
    int phaseNumbTo = getPhaseNumberOfPhase(toPhaseName);

    if (specifiedStream.equals("feed")) {
      moleFraction = fraction;
    } else if (specifiedStream.equals("product")) {
      // double specFractionFrom = getPhaseFraction(specification, fromPhaseName);
      double specFractionTo = getPhaseFraction(specification, toPhaseName);

      double moleFractionFrom = getMoleFraction(phaseNumbFrom);
      double moleFractionTo = getMoleFraction(phaseNumbTo);

      if (specification.equals("volume") || specification.equals("mass")) {
        double test = fraction * specFractionTo / (fraction * specFractionTo + specFractionTo);
        moleFraction = test * moleFractionTo / specFractionTo;
      } else if (specification.equals("mole")) {
        double test = fraction * moleFractionTo / (fraction * moleFractionTo + moleFractionTo);
        moleFraction = test;
      }

      moleFraction = moleFraction * moleFractionTo / moleFractionFrom;
      if (moleFraction > moleFractionFrom) {
        logger.debug("error in addPhaseFractionToPhase()...to low fraction in from phase");
        moleFraction = moleFractionFrom;
      }
    }

    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      double change = 0.0;
      change = getPhase(phaseNumbFrom).getComponent(i).getNumberOfMolesInPhase() * moleFraction;
      addComponent(i, change, phaseNumbTo);
      addComponent(i, -change, phaseNumbFrom);
    }
    init_x_y();
  }

  /** {@inheritDoc} */
  @Override
  public void addGasToLiquid(double fraction) {
    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      double change = getPhase(0).getComponent(i).getNumberOfMolesInPhase() * fraction;
      addComponent(i, -change, 0);
      addComponent(i, change, 1);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalNumberOfMoles() {
    return this.totalNumberOfMoles;
  }

  /** {@inheritDoc} */
  @Override
  public void setTotalNumberOfMoles(double totalNumberOfMoles) {
    if (totalNumberOfMoles < 0) {
      /*
       * throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
       * "setTotalNumberOfMoles", "totalNumberOfMoles", "can not be less than 0."));
       */
      totalNumberOfMoles = 0;
    }
    this.totalNumberOfMoles = totalNumberOfMoles;
  }

  /** {@inheritDoc} */
  @Override
  public double calcHenrysConstant(String component) {
    if (numberOfPhases != 2) {
      logger.error("Can't calculate Henrys constant - two phases must be present.");
      return 0;
    } else {
      int compNumb = getPhase(getPhaseIndex(0)).getComponent(component).getComponentNumber();
      double hc = getPhase(getPhaseIndex(0)).getFugacity(compNumb)
          / getPhase(getPhaseIndex(1)).getComponent(component).getx();
      return hc;
    }
  }

  /**
   * <p>
   * useTVasIndependentVariables.
   * </p>
   *
   * @return a boolean
   */
  public boolean useTVasIndependentVariables() {
    return useTVasIndependentVariables;
  }

  /** {@inheritDoc} */
  @Override
  public void setUseTVasIndependentVariables(boolean useTVasIndependentVariables) {
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).setTotalVolume(getPhase(i).getVolume());
      getPhase(i).setConstantPhaseVolume(useTVasIndependentVariables);
      getPhase(i).calcMolarVolume(!useTVasIndependentVariables);
    }
    this.useTVasIndependentVariables = useTVasIndependentVariables;
  }

  /** {@inheritDoc} */
  @Override
  public void setBmixType(int bmixType) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      ((PhaseEosInterface) getPhase(i)).getMixingRule().setBmixType(bmixType);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isImplementedCompositionDeriativesofFugacity() {
    return implementedCompositionDeriativesofFugacity;
  }

  /** {@inheritDoc} */
  @Override
  public void isImplementedCompositionDeriativesofFugacity(boolean isImpl) {
    this.implementedCompositionDeriativesofFugacity = isImpl;
  }

  /** {@inheritDoc} */
  @Override
  public void setImplementedCompositionDeriativesofFugacity(
      boolean implementedCompositionDeriativesofFugacity) {
    this.implementedCompositionDeriativesofFugacity = implementedCompositionDeriativesofFugacity;
  }

  /** {@inheritDoc} */
  @Override
  public void setImplementedPressureDeriativesofFugacity(
      boolean implementedPressureDeriativesofFugacity) {
    this.implementedPressureDeriativesofFugacity = implementedPressureDeriativesofFugacity;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isImplementedPressureDeriativesofFugacity() {
    return implementedPressureDeriativesofFugacity;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isImplementedTemperatureDeriativesofFugacity() {
    return implementedTemperatureDeriativesofFugacity;
  }

  /** {@inheritDoc} */
  @Override
  public void setImplementedTemperatureDeriativesofFugacity(
      boolean implementedTemperatureDeriativesofFugacity) {
    this.implementedTemperatureDeriativesofFugacity = implementedTemperatureDeriativesofFugacity;
  }

  /** {@inheritDoc} */
  @Override
  public void deleteFluidPhase(int phase) {
    for (int i = phase; i < numberOfPhases; i++) {
      phaseIndex[i] = phaseIndex[i + 1];
    }
    numberOfPhases--;
  }

  /** {@inheritDoc} */
  @Override
  public int getMaxNumberOfPhases() {
    return maxNumberOfPhases;
  }

  /** {@inheritDoc} */
  @Override
  public void setMaxNumberOfPhases(int maxNumberOfPhases) {
    this.maxNumberOfPhases = maxNumberOfPhases;
  }

  /** {@inheritDoc} */
  @Override
  public void setMolarComposition(double[] molefractions) {
    setMolarFractions(molefractions, "");
  }

  /** {@inheritDoc} */
  @Override
  public void setMolarCompositionPlus(double[] molefractions) {
    setMolarFractions(molefractions, "Plus");
  }

  /** {@inheritDoc} */
  @Override
  public void setMolarCompositionOfPlusFluid(double[] molefractions) {
    setMolarFractions(molefractions, "PlusFluid");
  }

  /** {@inheritDoc} */
  @Override
  public void setMolarFlowRates(double[] moles) {
    setEmptyFluid();
    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      addComponent(compNumb, moles[compNumb]);
    }
    for (int i = 0; i < getNumberOfPhases(); i++) {
      init(0, i);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[] getMolarRate() {
    double[] comp = new double[getPhase(0).getNumberOfComponents()];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      comp[compNumb] = getPhase(0).getComponent(compNumb).getNumberOfmoles();
    }
    return comp;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getMolarComposition() {
    PhaseInterface phase = this.getPhase(0);
    double[] comp = new double[phase.getNumberOfComponents()];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      comp[compNumb] = phase.getComponent(compNumb).getz();
    }
    return comp;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMultiphaseWaxCheck() {
    return multiphaseWaxCheck;
  }

  /** {@inheritDoc} */
  @Override
  public void setMultiphaseWaxCheck(boolean multiphaseWaxCheck) {
    this.multiphaseWaxCheck = multiphaseWaxCheck;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getCompIDs() {
    String[] ids = new String[numberOfComponents];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      ids[compNumb] = Integer.toString(getPhase(0).getComponent(compNumb).getIndex());
    }
    return ids;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getCompFormulaes() {
    String[] formula = new String[numberOfComponents];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      formula[compNumb] = getPhase(0).getComponent(compNumb).getFormulae();
    }
    return formula;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getCompNames() {
    String[] names = new String[numberOfComponents];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      names[compNumb] = getPhase(0).getComponent(compNumb).getComponentName();
    }
    return names;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getNormalBoilingPointTemperatures() {
    double[] bt = new double[numberOfComponents];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      bt[compNumb] = getPhase(0).getComponent(compNumb).getNormalBoilingPoint() + 273.15;
    }
    return bt;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getCapeOpenProperties11() {
    return CapeOpenProperties11;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getCapeOpenProperties10() {
    return CapeOpenProperties10;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getMolecularWeights() {
    double[] mm = new double[numberOfComponents];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      mm[compNumb] = getPhase(0).getComponent(compNumb).getMolarMass() * 1e3;
    }
    return mm;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getCASNumbers() {
    String[] names = new String[numberOfComponents];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      names[compNumb] = getPhase(0).getComponent(compNumb).getCASnumber();
    }
    return names;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfOilFractionComponents() {
    int number = 0;
    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction()
          || getPhase(0).getComponent(i).isIsPlusFraction()) {
        number++;
      }
    }
    return number;
  }

  /** {@inheritDoc} */
  @Override
  public int[] getOilFractionIDs() {
    int numb = getNumberOfOilFractionComponents();
    int[] IDs = new int[numb];
    // int number = 0;
    for (int i = 0; i < numb; i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction()
          || getPhase(0).getComponent(i).isIsPlusFraction()) {
        IDs[i] = getPhase(0).getComponent(i).getIndex();
        // number++;
      }
    }
    return IDs;
  }

  /** {@inheritDoc} */
  @Override
  public boolean setHeavyTBPfractionAsPlusFraction() {
    int compNumber = 0;
    double molarMass = 0;
    boolean foundTBP = false;

    for (int i = 0; i < numberOfComponents; i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction()
          || getPhase(0).getComponent(i).isIsPlusFraction()) {
        if (getPhase(0).getComponent(i).getMolarMass() > molarMass) {
          molarMass = getPhase(0).getComponent(i).getMolarMass();
          compNumber = i;
          foundTBP = true;
        }
      }
    }
    if (foundTBP) {
      for (int i = 0; i < maxNumberOfPhases; i++) {
        getPhase(0).getComponent(compNumber).setIsPlusFraction(true);
      }
    }
    return foundTBP;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getOilFractionNormalBoilingPoints() {
    int numb = getNumberOfOilFractionComponents();
    int[] indexes = getOilFractionIDs();
    double[] temp = new double[numb];
    for (int i = 0; i < numb; i++) {
      temp[i] = getPhase(0).getComponentWithIndex(indexes[i]).getNormalBoilingPoint();
    }
    return temp;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getOilFractionLiquidDensityAt25C() {
    int numb = getNumberOfOilFractionComponents();
    int[] indexes = getOilFractionIDs();
    double[] temp = new double[numb];
    for (int i = 0; i < numb; i++) {
      temp[i] = getPhase(0).getComponentWithIndex(indexes[i]).getNormalLiquidDensity();
    }
    return temp;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getOilFractionMolecularMass() {
    int numb = getNumberOfOilFractionComponents();
    int[] indexes = getOilFractionIDs();
    double[] temp = new double[numb];
    for (int i = 0; i < numb; i++) {
      temp[i] = getPhase(0).getComponentWithIndex(indexes[i]).getMolarMass();
    }
    return temp;
  }

  /** {@inheritDoc} */
  @Override
  public PhaseInterface getLowestGibbsEnergyPhase() {
    if (getPhase(0).getGibbsEnergy() < getPhase(1).getGibbsEnergy()) {
      return getPhase(0);
    } else {
      return getPhase(1);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getWtFraction(int phaseNumber) {
    return getPhase(phaseNumber).getWtFraction(this);
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeFraction(int phaseNumber) {
    return getPhase(phaseNumber).getVolume() / getVolume();
  }

  /** {@inheritDoc} */
  @Override
  public final double getPhaseFraction(String phaseTypeName, String unit) {
    int phaseNumber = getPhaseNumberOfPhase(phaseTypeName);
    switch (unit) {
      case "mole":
        return getBeta(phaseNumber);
      case "volume":
        return getVolumeFraction(phaseNumber);
      case "mass":
        initPhysicalProperties("density");
        return getVolumeFraction(phaseNumber) * getPhase(phaseNumber).getDensity("kg/m3")
            / getDensity("kg/m3");
      default:
        return getBeta(phaseNumber);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getCorrectedVolumeFraction(int phaseNumber) {
    return getPhase(phaseNumber).getCorrectedVolume() / getCorrectedVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double getMoleFraction(int phaseNumber) {
    return getPhase(phaseNumber).getBeta();
  }

  /** {@inheritDoc} */
  @Override
  public void addCapeOpenProperty(String propertyName) {
    String[] tempString = new String[CapeOpenProperties11.length + 1];
    System.arraycopy(CapeOpenProperties11, 0, tempString, 0, CapeOpenProperties11.length);
    tempString[CapeOpenProperties11.length] = propertyName;
    CapeOpenProperties11 = tempString;

    tempString = new String[CapeOpenProperties10.length + 1];
    System.arraycopy(CapeOpenProperties10, 0, tempString, 0, CapeOpenProperties10.length);
    tempString[CapeOpenProperties10.length] = propertyName;
    CapeOpenProperties10 = tempString;
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.thermo.characterization.WaxCharacterise getWaxCharacterisation() {
    return waxCharacterisation;
  }

  /** {@inheritDoc} */
  @Override
  public WaxModelInterface getWaxModel() {
    if (waxCharacterisation == null) {
      waxCharacterisation = new WaxCharacterise(this);
    }
    return waxCharacterisation.getModel();
  }

  /** {@inheritDoc} */
  @Override
  public double getLiquidVolume() {
    double totFlow = 0;

    for (int kj = 0; kj < numberOfPhases; kj++) {
      if (getPhase(kj).getType() != PhaseType.GAS) {
        totFlow += getPhase(kj).getVolume();
      }
    }
    return totFlow;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isForcePhaseTypes() {
    return forcePhaseTypes;
  }

  /** {@inheritDoc} */
  @Override
  public void setForcePhaseTypes(boolean forcePhaseTypes) {
    this.forcePhaseTypes = forcePhaseTypes;
  }

  /** {@inheritDoc} */
  @Override
  public SystemProperties getProperties() {
    return new SystemProperties(this);
  }

  /**
   * Wrapper function for addComponent to set fluid type and specify mole fractions.
   *
   * @param molefractions Component mole fraction of each component.
   * @param type Type of fluid. Supports "PlusFluid", "Plus" and default.
   */
  private void setMolarFractions(double[] molefractions, String type) {
    double totalFlow = getTotalNumberOfMoles();
    if (totalFlow < 1e-100) {
      String msg = "must be larger than 0 (1e-100) when setting molar composition";
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "setMolarComposition", "totalFlow", msg));
    }
    double sum = 0;
    for (double value : molefractions) {
      sum += value;
    }
    setEmptyFluid();

    switch (type) {
      case "PlusFluid":
        // TODO: really skip last component of molefraction?
        for (int compNumb = 0; compNumb < molefractions.length - 1; compNumb++) {
          addComponent(compNumb, totalFlow * molefractions[compNumb] / sum);
        }
        for (int j = 0; j < getCharacterization().getLumpingModel().getNumberOfLumpedComponents()
            - 1; j++) {
          // addComponent(compNumb, totalFlow * molefractions[molefractions.length - 1]
          // * getCharacterization().getLumpingModel().getFractionOfHeavyEnd(j) / sum);
        }
        break;
      case "Plus":
        // TODO: compNumb can be negative
        for (int compNumb = 0; compNumb < this.numberOfComponents
            - getCharacterization().getLumpingModel().getNumberOfLumpedComponents(); compNumb++) {
          addComponent(compNumb, totalFlow * molefractions[compNumb] / sum);
        }
        int ii = 0;
        for (int compNumb = this.numberOfComponents - getCharacterization().getLumpingModel()
            .getNumberOfLumpedComponents(); compNumb < this.numberOfComponents; compNumb++) {
          addComponent(compNumb,
              totalFlow * getCharacterization().getLumpingModel().getFractionOfHeavyEnd(ii++)
                  * molefractions[this.numberOfComponents
                      - getCharacterization().getLumpingModel().getNumberOfLumpedComponents()]
                  / sum);
        }
        break;
      default:
        // NB! It will allow setting composition for only the first items.
        // for (int compNumb = 0; compNumb <= molefractions.length - 1; compNumb++) {
        // NB! Can fail because len(molefractions) < this.numberOfComponents
        for (int compNumb = 0; compNumb <= this.numberOfComponents - 1; compNumb++) {
          addComponent(compNumb, totalFlow * molefractions[compNumb] / sum);
        }
        break;
    }

    for (int i = 0; i < getNumberOfPhases(); i++) {
      init(0, i);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addCharacterized(String[] charNames, double[] charFlowrate, double[] molarMass,
      double[] relativedensity) {
    if (charNames.length != charFlowrate.length) {
      logger.error("component names and mole fractions need to be same length...");
    }
    for (int i = 0; i < charNames.length; i++) {
      addTBPfraction(charNames[i], charFlowrate[i], molarMass[i], relativedensity[i]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addOilFractions(String[] charNames, double[] charFlowrate, double[] molarMass,
      double[] relativedensity, boolean lastIsPlusFraction, boolean lumpComponents,
      int numberOfPseudoComponents) {
    if (charNames.length != charFlowrate.length) {
      logger.error("component names and mole fractions need to be same length...");
    }

    for (int i = 0; i < charNames.length - 1; i++) {
      addTBPfraction(charNames[i], charFlowrate[i], molarMass[i], relativedensity[i]);
    }
    int i = charNames.length - 1;
    if (lastIsPlusFraction) {
      addPlusFraction(charNames[i], charFlowrate[i], molarMass[i], relativedensity[i]);
    } else {
      addTBPfraction(charNames[i], charFlowrate[i], molarMass[i], relativedensity[i]);
    }
    createDatabase(true);
    if (lastIsPlusFraction) {
      getCharacterization().getLumpingModel().setNumberOfPseudoComponents(numberOfPseudoComponents);
      if (lumpComponents) {
        getCharacterization().setLumpingModel("PVTlumpingModel");
      } else {
        getCharacterization().setLumpingModel("no lumping");
      }
      getCharacterization().characterisePlusFraction();
    }
    setMixingRule(getMixingRule());
    setMultiPhaseCheck(true);
    init(0);
  }

  /** {@inheritDoc} */
  @Override
  public void addOilFractions(String[] charNames, double[] charFlowrate, double[] molarMass,
      double[] relativedensity, boolean lastIsPlusFraction) {
    addOilFractions(charNames, charFlowrate, molarMass, relativedensity, lastIsPlusFraction, true,
        12);
  }

  /** {@inheritDoc} */
  public double getIdealLiquidDensity(String unit) {
    double normalLiquidDensity = 0.0;
    double molarMass = getMolarMass();
    for (int i = 0; i < getNumberOfComponents(); i++) {
      normalLiquidDensity += getComponent(i).getNormalLiquidDensity() * getComponent(i).getz()
          * getComponent(i).getMolarMass() / molarMass;
    }
    if (unit.equals("gr/cm3")) {
      return normalLiquidDensity;
    } else if (unit.equals("kg/m3")) {
      return normalLiquidDensity * 1000.0;
    } else {
      logger.error("unit not supported: " + unit);
      return normalLiquidDensity;
    }
  }
}
