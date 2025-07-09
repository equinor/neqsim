package neqsim.thermo.system;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.physicalproperties.interfaceproperties.InterfaceProperties;
import neqsim.physicalproperties.interfaceproperties.InterphasePropertiesInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.characterization.Characterise;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSolid;
import neqsim.thermo.phase.PhaseSolidComplex;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseWax;

/**
 * This is the base class of the System classes.
 *
 * @author Even Solbraa
 */
public abstract class SystemThermo implements SystemInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SystemThermo.class);

  // Class variables
  private static final int MAX_PHASES = 6;

  protected int a;
  protected boolean allowPhaseShift = true;
  protected int attractiveTermNumber = 0;

  /** Fraction of moles_in_phase / moles_in_system. Cached. */
  protected double[] beta = new double[MAX_PHASES];
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

  public neqsim.thermo.characterization.Characterise characterization = null;
  protected boolean checkStability = true;
  protected ChemicalReactionOperations chemicalReactionOperations = null;
  protected boolean chemicalSystem = false;
  private ArrayList<String> componentNames = new ArrayList<String>();

  // TODO: componentNameTag is not working yet, a kind of alias-postfix for
  // Components from this
  // system that will be passed on to other systems. used to find originator of
  // specific components
  // or
  public String componentNameTag = "";

  /** Critical pressure in bara. */
  protected double criticalPressure = 0;
  /** Critical temperature in Kelvin. */
  protected double criticalTemperature = 0;
  // Object metadata
  protected String fluidInfo = "No Information Available";

  protected String fluidName = "DefaultName";
  private boolean forcePhaseTypes = false;
  protected boolean hydrateCheck = false;

  private boolean implementedCompositionDeriativesofFugacity = true;
  private boolean implementedPressureDeriativesofFugacity = true;

  private boolean implementedTemperatureDeriativesofFugacity = true;
  protected InterphasePropertiesInterface interfaceProp = null;

  // Initialization
  boolean isInitialized = false;

  /** Maximum allowed number of phases. */
  public int maxNumberOfPhases = 2;

  private MixingRuleTypeInterface mixingRuleType;
  protected String modelName = "Default";

  protected boolean multiPhaseCheck = false;
  private boolean multiphaseWaxCheck = false;

  // todo: replace numberOfComponents with length of componentNames.
  protected int numberOfComponents = 0;
  /** Number of phases in use/existing. */
  protected int numberOfPhases = 2;
  protected boolean numericDerivatives = false;

  /**
   * Array containing all phases of System. NB! Phases are reorered according to density, use
   * phaseIndex to keep track of the creation order.
   */
  protected PhaseInterface[] phaseArray = new PhaseInterface[MAX_PHASES];
  /**
   * Array of indexes to phaseArray keeping track of the creation order of the phases where 0 is the
   * first created phase and the lowest number is the phase created last.
   */
  protected int[] phaseIndex;
  // PhaseType of phases belonging to system.
  protected PhaseType[] phaseType = new PhaseType[MAX_PHASES];

  protected String[][] resultTable = null;
  protected boolean solidPhaseCheck = false;
  protected neqsim.standards.StandardInterface standard = null;
  private double totalNumberOfMoles = 0;
  private boolean useTVasIndependentVariables = false;
  protected neqsim.thermo.characterization.WaxCharacterise waxCharacterisation = null;

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
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemThermo(double T, double P, boolean checkForSolids) {
    this();
    if (T < 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "SystemThermo", "T", "is negative"));
    }

    if (P < 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "SystemThermo", "P", "is negative"));
    }

    this.solidPhaseCheck = checkForSolids;
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
    // TODO: isInitialized = false;
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
    // TODO: isInitialized = false;
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
    // TODO: isInitialized = false;
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
    // TODO: isInitialized = false;
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
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "addComponent", "moles", msg));
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
    // TODO: isInitialized = false;
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
    // TODO: isInitialized = false;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double value, String name, int phaseNum) {
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
    this.addComponent(componentName, SIval, phaseNum);
    // TODO: isInitialized = false;
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
  public SystemInterface addFluid(SystemInterface addSystem, int phaseNum) {
    for (int i = 0; i < addSystem.getPhase(0).getNumberOfComponents(); i++) {
      addComponent(addSystem.getPhase(0).getComponent(i).getComponentName(),
          addSystem.getPhase(0).getComponent(i).getNumberOfmoles(), phaseNum);
    }
    return this;
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
  public void addLiquidToGas(double fraction) {
    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      double change = getPhase(1).getComponent(i).getNumberOfMolesInPhase() * fraction;
      addComponent(i, change, 0);
      addComponent(i, -change, 1);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addOilFractions(String[] charNames, double[] charFlowrate, double[] molarMass,
      double[] relativedensity, boolean lastIsPlusFraction) {
    addOilFractions(charNames, charFlowrate, molarMass, relativedensity, lastIsPlusFraction, true,
        12);
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
      // double specFractionFrom = getPhaseFraction(fromPhaseName, specification);
      double specFractionTo = getPhaseFraction(toPhaseName, specification);

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
    componentName = (componentName.split("_PC")[0]) + "_PC"; // + getFluidName();

    try {
      refSystem = this.getClass().getDeclaredConstructor().newInstance();
      refSystem.setTemperature(273.15 + 15.0);
      refSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
      refSystem.addComponent("default", 1.0, 273.15, 50.0, 0.1);
      refSystem.setMixingRule(1);
      refSystem.init(0);
      refSystem.setNumberOfPhases(1);
      refSystem.setPhaseType(0, PhaseType.LIQUID);
      molarMass = 1000 * molarMass;
      TC = characterization.getTBPModel().calcTC(molarMass, density);
      PC = characterization.getTBPModel().calcPC(molarMass, density);
      if (characterization.getTBPModel().isCalcm()) {
        m = characterization.getTBPModel().calcm(molarMass, density);
      }
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
    } catch (RuntimeException ex) {
      // todo: Should not swallow notimplementedexception
      /*
       * if (ex.getCause().getClass().equals(NotImplementedException.class)) { throw ex; }
       */
      logger.error(ex.getMessage());
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
      getPhase(i).getComponent(componentName).setNormalBoilingPoint(TB);
      getPhase(i).getComponent(componentName)
          .setAcentricFactor(refSystem.getPhase(0).getComponent(0).getAcentricFactor());
      getPhase(i).getComponent(componentName).setCriticalVolume(critVol);
      getPhase(i).getComponent(componentName).setRacketZ(racketZ);
      getPhase(i).getComponent(componentName).setRacketZCPA(racketZ);
      getPhase(i).getComponent(componentName).setIsTBPfraction(true);
      getPhase(i).getComponent(componentName).setParachorParameter(
          characterization.getTBPModel().calcParachorParameter(molarMass, density)); // 59.3+2.34*molarMass*1000.0);
                                                                                     // 0.5003*thermo.ThermodynamicConstantsInterface.R*TC/PC*(0.25969-racketZ));
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
  public void addTBPfractionFromBoilingPoint(String componentName, double numberOfMoles, 
      double boilingPoint, double molarMass) {
    if (boilingPoint <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "addTBPfractionFromBoilingPoint", "boilingPoint", "must be positive."));
    }
    if (molarMass <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "addTBPfractionFromBoilingPoint", "molarMass", "must be positive."));
    }

    // Calculate density from boiling point and molar mass using TBP model
    double density = characterization.getTBPModel().calcDensityFromTBAndMW(boilingPoint, molarMass);
    
    // Call the standard addTBPfraction method with calculated density
    addTBPfraction(componentName, numberOfMoles, molarMass, density);
  }

  // ...existing code...
