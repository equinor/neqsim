package neqsim.thermo.system;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.physicalproperties.interfaceproperties.InterfaceProperties;
import neqsim.physicalproperties.interfaceproperties.InterphasePropertiesInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.characterization.Characterise;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.characterization.WaxCharacterise;
import neqsim.thermo.characterization.WaxModelInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSolid;
import neqsim.thermo.phase.PhaseSolidComplex;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseWax;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.database.NeqSimDataBase;
import neqsim.util.exception.InvalidInputException;
import neqsim.util.unit.Units;

/**
 * This is the base class of the System classes.
 *
 * @author Even Solbraa
 */
public abstract class SystemThermo implements SystemInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Restores the serialized fields and reconciles a stale scalar total with the duplicated overall-component inventory.
   *
   * <p>
   * A separated phase may be serialized after its component inventory has changed while the scalar total still
   * describes the upstream system. Overall mole fractions are derived by dividing component inventories by that scalar,
   * so the mismatch must be repaired before the first initialization or flash. The reconciliation is deliberately
   * limited to deserialization and to phase slots that agree on the overall inventory.
   * </p>
   *
   * @param input serialized object input
   * @throws IOException if the serialized object cannot be read
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    reconcileTotalMolesWithComponentInventory();
  }

  /**
   * Reconciles the scalar total with a finite, positive overall-component inventory.
   *
   * <p>
   * Every active phase slot normally carries the same overall component amounts while storing its phase-specific
   * amounts separately. Reconciliation is skipped when those duplicated inventories disagree, because that state cannot
   * safely identify one authoritative total.
   * </p>
   */
  private void reconcileTotalMolesWithComponentInventory() {
    if (phaseArray == null || numberOfComponents == 0 || phaseArray[phaseIndex[0]] == null) {
      return;
    }

    double inventory = 0.0;
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      double componentMoles = getPhase(0).getComponent(componentIndex).getNumberOfmoles();
      if (!Double.isFinite(componentMoles) || componentMoles < 0.0) {
        return;
      }
      inventory += componentMoles;
    }
    if (!Double.isFinite(inventory) || inventory <= 0.0) {
      return;
    }

    for (int phase = 1; phase < numberOfPhases; phase++) {
      if (getPhase(phase) == null || getPhase(phase).getNumberOfComponents() != numberOfComponents) {
        return;
      }
      for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
        double referenceMoles = getPhase(0).getComponent(componentIndex).getNumberOfmoles();
        double phaseMoles = getPhase(phase).getComponent(componentIndex).getNumberOfmoles();
        double tolerance = 1.0e-12 * Math.max(1.0, Math.abs(referenceMoles));
        if (!Double.isFinite(phaseMoles) || Math.abs(phaseMoles - referenceMoles) > tolerance) {
          return;
        }
      }
    }

    double tolerance = 1.0e-12 * Math.max(1.0, inventory);
    if (!Double.isFinite(totalNumberOfMoles) || Math.abs(totalNumberOfMoles - inventory) > tolerance) {
      setTotalNumberOfMolesRaw(inventory);
      isInitialized = false;
    }
  }

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SystemThermo.class);

  // Class variables
  private static final int MAX_PHASES = 6;

  protected int a;
  protected boolean allowPhaseShift = true;
  protected int attractiveTermNumber = 0;

  /** Fraction of moles_in_phase / moles_in_system. Cached. */
  protected double[] beta = new double[MAX_PHASES];
  protected String[] CapeOpenProperties10 = { "molecularWeight", "speedOfSound", "jouleThomsonCoefficient", "energy",
      "energy.Dtemperature", "gibbsFreeEnergy", "helmholtzFreeEnergy", "fugacityCoefficient", "logFugacityCoefficient",
      "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure", "logFugacityCoefficient.Dmoles",
      "enthalpy", "enthalpy.Dmoles", "enthalpy.Dtemperature", "enthalpy.Dpressure", "entropy", "entropy.Dtemperature",
      "entropy.Dpressure", "entropy.Dmoles", "heatCapacity", "heatCapacityCv", "density", "density.Dtemperature",
      "density.Dpressure", "density.Dmoles", "volume", "volume.Dpressure", "volume.Dtemperature",
      "molecularWeight.Dtemperature", "molecularWeight.Dpressure", "molecularWeight.Dmoles", "compressibilityFactor" };
  // protected ArrayList<String> resultArray1 = new ArrayList<String>();
  protected String[] CapeOpenProperties11 = { "molecularWeight", "speedOfSound", "jouleThomsonCoefficient",
      "internalEnergy", "internalEnergy.Dtemperature", "gibbsEnergy", "helmholtzEnergy", "fugacityCoefficient",
      "logFugacityCoefficient", "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
      "logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dmoles", "enthalpy.Dtemperature", "enthalpy.Dpressure",
      "entropy", "entropy.Dtemperature", "entropy.Dpressure", "entropy.Dmoles", "heatCapacityCp", "heatCapacityCv",
      "density", "density.Dtemperature", "density.Dpressure", "density.Dmoles", "volume", "volume.Dpressure",
      "volume.Dtemperature", "molecularWeight.Dtemperature", "molecularWeight.Dpressure", "molecularWeight.Dmoles",
      "compressibilityFactor" };

  public neqsim.thermo.characterization.Characterise characterization = null;
  protected boolean checkStability = true;
  protected ChemicalReactionOperations chemicalReactionOperations = null;
  protected boolean chemicalSystem = false;
  private boolean chemicalReactionStateStale = false;
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
  protected boolean enhancedMultiPhaseCheck = false;
  private boolean multiphaseWaxCheck = false;
  private String waxModelTypeName = "Pedersen";

  // todo: replace numberOfComponents with length of componentNames.
  protected int numberOfComponents = 0;
  /** Number of phases in use/existing. */
  protected int numberOfPhases = 2;
  protected boolean numericDerivatives = false;

  /**
   * Array containing all phases of System. NB! Phases are reorered according to density, use phaseIndex to keep track
   * of the creation order.
   */
  protected PhaseInterface[] phaseArray = new PhaseInterface[MAX_PHASES];
  /**
   * Array of indexes to phaseArray keeping track of the creation order of the phases where 0 is the first created phase
   * and the lowest number is the phase created last.
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
  protected transient OilAssayCharacterisation oilAssayCharacterisation = null;

  /**
   * Constructor for SystemThermo.
   */
  public SystemThermo() {
    characterization = new Characterise(this);
    interfaceProp = new InterfaceProperties(this);

    reInitPhaseInformation();
  }

  /**
   * Constructor for SystemThermo.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemThermo(double T, double P, boolean checkForSolids) {
    this();
    if (T < 0.0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "SystemThermo", "T", "is negative"));
    }

    if (P < 0.0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "SystemThermo", "P", "is negative"));
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
      addTBPfraction(inComponent.getComponentName(), inComponent.getNumberOfmoles(), inComponent.getMolarMass(),
          inComponent.getNormalLiquidDensity());
      String componentName = inComponent.getComponentName();
      changeComponentName(componentName + "_PC", componentName.replaceFirst("_PC", ""));
      for (int i = 0; i < numberOfPhases; i++) {
        getPhase(i).getComponent(componentName).setAttractiveTerm(inComponent.getAttractiveTermNumber());
        getPhase(i).getComponent(componentName).setTC(inComponent.getTC());
        getPhase(i).getComponent(componentName).setPC(inComponent.getPC());
        getPhase(i).getComponent(componentName).setMolarMass(inComponent.getMolarMass());
        getPhase(i).getComponent(componentName).setComponentType("TBPfraction");
        getPhase(i).getComponent(componentName).setNormalLiquidDensity(inComponent.getNormalLiquidDensity());
        getPhase(i).getComponent(componentName).setNormalBoilingPoint(inComponent.getNormalBoilingPoint());
        getPhase(i).getComponent(componentName).setAcentricFactor(inComponent.getAcentricFactor());
        getPhase(i).getComponent(componentName).setCriticalVolume(inComponent.getCriticalVolume());
        getPhase(i).getComponent(componentName).setRacketZ(inComponent.getRacketZ());
        getPhase(i).getComponent(componentName).setRacketZCPA(inComponent.getRacketZCPA());
        getPhase(i).getComponent(componentName).setIsTBPfraction(true);
        getPhase(i).getComponent(componentName).setParachorParameter(inComponent.getParachorParameter());
        getPhase(i).getComponent(componentName).setTriplePointTemperature(inComponent.getTriplePointTemperature());
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
    setTotalNumberOfMolesRaw(getTotalNumberOfMoles() + moles);
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

    setTotalNumberOfMolesRaw(getTotalNumberOfMoles() + moles);
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
        throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "addComponent",
            "componentName", "with value " + componentName + " not found in database."));
      }
      if (moles < 0.0) {
        String msg = "is negative input for component: " + componentName;
        throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "addComponent", "moles", msg));
      }
      // System.out.println("adding " + componentName);
      componentNames.add(componentName);
      for (int i = 0; i < getMaxNumberOfPhases(); i++) {
        getPhase(i).addComponent(componentName, moles, moles, numberOfComponents);
        getPhase(i).setAttractiveTerm(attractiveTermNumber);
      }
      numberOfComponents++;
      markChemicalReactionStateStale();
    } else {
      for (PhaseInterface tmpPhase : phaseArray) {
        if (tmpPhase != null && (tmpPhase.getComponent(componentName).getNumberOfMolesInPhase() + moles) < 0.0) {
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
    setTotalNumberOfMolesRaw(getTotalNumberOfMoles() + moles);
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
      // Set isPlusFraction for components with "_PC" suffix (plus fraction
      // components)
      if (componentName.endsWith("_PC")) {
        getPhase(i).getComponent(componentName).setIsPlusFraction(true);
      }
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
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "addComponent", "moles", msg));
    }

    componentNames.add(componentName);
    double k = 1.0;
    setTotalNumberOfMolesRaw(getTotalNumberOfMoles() + moles);

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
    markChemicalReactionStateStale();
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
        java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + componentName + "'"))) {
      dataSet.next();
      molarmass = Double.parseDouble(dataSet.getString("molarmass")) / 1000.0;
      stddens = Double.parseDouble(dataSet.getString("stddens"));
      boilp = Double.parseDouble(dataSet.getString("normboil"));
    } catch (Exception ex) {
      // todo: mole amount may be not set. should not be caught?
      logger.error("failed ", ex);
    }
    neqsim.util.unit.Unit unit = new neqsim.util.unit.RateUnit(value, unitName, molarmass, stddens, boilp);
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
        java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + componentName + "'"))) {
      dataSet.next();
      molarmass = Double.parseDouble(dataSet.getString("molarmass")) / 1000.0;
      stddens = Double.parseDouble(dataSet.getString("stddens"));
      boilp = Double.parseDouble(dataSet.getString("normboil"));
    } catch (Exception ex) {
      logger.error("failed ", ex);
      throw new RuntimeException(ex);
    }
    neqsim.util.unit.Unit unit = new neqsim.util.unit.RateUnit(value, name, molarmass, stddens, boilp);
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
        index = getPhase(0).getComponent(addSystem.getPhase(0).getComponent(i).getComponentName()).getComponentNumber();
      }

      if (index != -1) {
        addComponent(index, addSystem.getPhase(0).getComponent(i).getNumberOfmoles());
      } else if (addSystem.getPhase(0).getComponent(i).isIsTBPfraction()) {
        addTBPfraction(addSystem.getPhase(0).getComponent(i).getComponentName().replaceFirst("_PC", ""),
            addSystem.getPhase(0).getComponent(i).getNumberOfmoles(),
            addSystem.getPhase(0).getComponent(i).getMolarMass(),
            addSystem.getPhase(0).getComponent(i).getNormalLiquidDensity());
      } else {
        if (addSystem.getPhase(0).getComponent(i).isIsTBPfraction()) {
          addTBPfraction(addSystem.getPhase(0).getComponent(i).getComponentName().replaceFirst("_PC", ""),
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
   * addHydratePhase.
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
        double moles = Math.max(0.0, getPhase(0).getComponent(i).getNumberOfmoles());
        if (getPhase(0).getComponent(i).isIsTBPfraction()) {
          phaseArray[3].addComponent("default", moles, moles, i);
          phaseArray[3].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
          phaseArray[3].getComponent(i).setIsTBPfraction(true);
        } else {
          phaseArray[3].addComponent(getPhase(0).getComponent(i).getName(), moles, moles, i);
        }
      }
      ((PhaseSolid) phaseArray[3]).setSolidRefFluidPhase(phaseArray[0]);
    }

    phaseArray[4] = new PhaseHydrate(getModelName());
    phaseArray[4].setTemperature(phaseArray[0].getTemperature());
    phaseArray[4].setPressure(phaseArray[0].getPressure());
    phaseArray[4].setType(PhaseType.HYDRATE);
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      double moles = Math.max(0.0, getPhase(0).getComponent(i).getNumberOfmoles());
      if (getPhase(0).getComponent(i).isIsTBPfraction()) {
        phaseArray[4].addComponent("default", moles, moles, i);
        phaseArray[4].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
        phaseArray[4].getComponent(i).setIsTBPfraction(true);
      } else {
        phaseArray[4].addComponent(getPhase(0).getComponent(i).getName(), moles, moles, i);
      }
    }
    ((PhaseHydrate) phaseArray[4]).setSolidRefFluidPhase(phaseArray[0]);

    setNumberOfPhases(5);
  }

  /**
   * addHydratePhase2.
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
        phaseArray[3].getComponent("default").setComponentName(getPhase(0).getComponent(i).getName());
      } else {
        phaseArray[3].addComponent(getPhase(0).getComponent(i).getName(),
            getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getNumberOfmoles(), i);
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
  public void addOilFractions(String[] charNames, double[] charFlowrate, double[] molarMass, double[] relativedensity,
      boolean lastIsPlusFraction) {
    addOilFractions(charNames, charFlowrate, molarMass, relativedensity, lastIsPlusFraction, true, 12);
  }

  /** {@inheritDoc} */
  @Override
  public void addOilFractions(String[] charNames, double[] charFlowrate, double[] molarMass, double[] relativedensity,
      boolean lastIsPlusFraction, boolean lumpComponents, int numberOfPseudoComponents) {
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
     * if (maxNumberOfPhases < 6 && !hydrateCheck) { ArrayList phaseList = new ArrayList(0); for (int i = 0; i <
     * numberOfPhases; i++) { phaseList.add(phaseArray[i]); } // add the new phase phaseList.add(phaseArray[0].clone());
     * beta[phaseList.size() - 1] = 1.0e-8; // beta[1] -= beta[1]/1.0e5;
     *
     * PhaseInterface[] phaseArray2 = new PhaseInterface[numberOfPhases + 1];
     *
     * for (int i = 0; i < numberOfPhases + 1; i++) { phaseArray2[i] = (PhaseInterface) phaseList.get(i); }
     *
     * phaseArray = phaseArray2;
     *
     * System.out.println("number of phases " + numberOfPhases); if (maxNumberOfPhases < numberOfPhases) {
     * maxNumberOfPhases = numberOfPhases; } }
     */
    numberOfPhases++;
  }

  /** {@inheritDoc} */
  @Override
  public void addPhaseFractionToPhase(double fraction, String specification, String fromPhaseName, String toPhaseName) {
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
      double moleFractionFrom = getMoleFraction(phaseNumbFrom);
      if (fraction <= 0.0) {
        moleFraction = 0.0;
      } else if (fraction >= 1.0) {
        moleFraction = moleFractionFrom;
      } else {
        double molesToTransfer = 0.0;

        switch (specification) {
        case "mole": {
          double molesInToPhase = getPhase(phaseNumbTo).getNumberOfMolesInPhase();
          molesToTransfer = fraction / (1.0 - fraction) * molesInToPhase;
          break;
        }
        case "mass": {
          initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
          double massInToPhase = getPhase(phaseNumbTo).getMass();
          double massToTransfer = fraction / (1.0 - fraction) * massInToPhase;
          double maxTransfer = getPhase(phaseNumbFrom).getMass();
          massToTransfer = Math.min(massToTransfer, maxTransfer);
          molesToTransfer = massToTransfer / getPhase(phaseNumbFrom).getMolarMass();
          break;
        }
        case "volume": {
          initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
          double volumeInToPhase = getPhase(phaseNumbTo).getVolume("m3");
          double volumeToTransfer = fraction / (1.0 - fraction) * volumeInToPhase;
          double maxVolume = getPhase(phaseNumbFrom).getVolume("m3");
          volumeToTransfer = Math.min(volumeToTransfer, maxVolume);
          double densityFrom = getPhase(phaseNumbFrom).getDensity("kg/m3");
          double massToTransfer = volumeToTransfer * densityFrom;
          molesToTransfer = massToTransfer / getPhase(phaseNumbFrom).getMolarMass();
          break;
        }
        default:
          throw new RuntimeException("unit not supported " + specification);
        }

        double maxMoles = getPhase(phaseNumbFrom).getNumberOfMolesInPhase();
        molesToTransfer = Math.min(molesToTransfer, maxMoles);
        double moleFractionToTransfer = molesToTransfer / getTotalNumberOfMoles();
        moleFraction = moleFractionToTransfer / moleFractionFrom;

        if (moleFraction > moleFractionFrom) {
          logger.debug("error in addPhaseFractionToPhase()...to low fraction in from phase");
          moleFraction = moleFractionFrom;
        }
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
  public void addPlusFraction(String componentName, double numberOfMoles, double molarMass, double density) {
    addTBPfraction(componentName, numberOfMoles, molarMass, density);
    componentName = (componentName + "_" + "PC"); // getFluidName());
    for (int i = 0; i < numberOfPhases; i++) {
      // System.out.println("comp " + componentName);
      getPhase(i).getComponent(componentName).setIsPlusFraction(true);
      getPhase(i).getComponent(componentName)
          .setCriticalViscosity(7.94830 * Math.sqrt(1e3 * getPhase(i).getComponent(componentName).getMolarMass())
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
      PhaseWax waxPhase = new PhaseWax();
      waxPhase.setWaxComponentModel(waxModelTypeName);
      phaseArray[5] = waxPhase;
    } else {
      phaseArray[5] = new PhaseSolidComplex();
    }

    phaseArray[5].setTemperature(phaseArray[0].getTemperature());
    phaseArray[5].setPressure(phaseArray[0].getPressure());
    phaseArray[5].setType(PhaseType.WAX);
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      double moles = Math.max(0.0, getPhase(0).getComponent(i).getNumberOfmoles());
      if (getPhase(0).getComponent(i).isIsTBPfraction()) {
        phaseArray[5].addComponent(getPhase(0).getComponent(i).getName(), moles, moles, i);
        phaseArray[5].getComponent(i).setIsPlusFraction(true);
      } else {
        phaseArray[5].addComponent(getPhase(0).getComponent(i).getName(), moles, moles, i);
      }
    }
    ((PhaseSolid) phaseArray[5]).setSolidRefFluidPhase(phaseArray[0]);
    setNumberOfPhases(6);
  }

  /**
   * addSolidPhase.
   */
  public void addSolidPhase() {
    if (!multiPhaseCheck) {
      setMultiPhaseCheck(true);
    }
    phaseArray[3] = new PhasePureComponentSolid();
    phaseArray[3].setTemperature(phaseArray[0].getTemperature());
    phaseArray[3].setPressure(phaseArray[0].getPressure());
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      double moles = Math.max(0.0, getPhase(0).getComponent(i).getNumberOfmoles());
      if (getPhase(0).getComponent(i).isIsTBPfraction()) {
        phaseArray[3].addComponent("default", moles, moles, i);
        phaseArray[3].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
        phaseArray[3].getComponent(i).setIsPlusFraction(true);
      } else {
        phaseArray[3].addComponent(getPhase(0).getComponent(i).getName(), moles, moles, i);
      }
    }
    ((PhaseSolid) phaseArray[3]).setSolidRefFluidPhase(phaseArray[0]);

    if (getMaxNumberOfPhases() < 4) {
      setMaxNumberOfPhases(4);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addTBPfraction(String componentName, double numberOfMoles, double molarMass, double density) {
    if (density < 0.0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "addTBPfraction", "density", "is negative."));
    }
    if (molarMass < 0.0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "addTBPfraction", "molarMass", "is negative."));
    }
    // Auto-convert density from kg/m3 to g/cm3 (= specific gravity) if needed.
    // No petroleum fraction has SG > 10, so values above that threshold are
    // assumed to be in kg/m3 (common user mistake, see issue #1980).
    if (density > 10.0) {
      logger.warn(
          "addTBPfraction: density value {} for '{}' appears to be in kg/m3. "
              + "Converting to g/cm3 (specific gravity) by dividing by 1000. " + "Expected range: 0.65-1.1 g/cm3.",
          density, componentName);
      density = density / 1000.0;
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
      // Re-throw NotImplementedException instead of swallowing it
      if (ex instanceof UnsupportedOperationException || ex.getClass().getSimpleName().contains("NotImplemented")) {
        throw ex;
      }
      logger.error(ex.getMessage(), ex);
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
    double cpa = (-0.33886 + 0.02827 * Kwatson - 0.26105 * CF + 0.59332 * acsKeslerLee * CF) * 4.18682 * molarMass
        * 1e3;
    double cpb = (-(0.9291 - 1.1543 * Kwatson + 0.0368 * Kwatson * Kwatson) * 1e-4
        + CF * (4.56 - 9.48 * acsKeslerLee) * 1e-4) * 4.18682 * molarMass * 1.8 * 1e3;
    double cpc = (-1.6658e-7 + CF * (0.536 - 0.6828 * acsKeslerLee) * 1.0e-7) * 4.18682 * molarMass * 1.8 * 1.8 * 1.0e3;
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
      getPhase(i).getComponent(componentName)
          .setParachorParameter(characterization.getTBPModel().calcParachorParameter(molarMass, density)); // 59.3+2.34*molarMass*1000.0);
      // 0.5003*thermo.ThermodynamicConstantsInterface.R*TC/PC*(0.25969-racketZ));
      getPhase(i).getComponent(componentName)
          .setCriticalViscosity(characterization.getTBPModel().calcCriticalViscosity(molarMass * 1000.0, density)); // 7.94830*Math.sqrt(1e3*molarMass)*Math.pow(PC,2.0/3.0)/Math.pow(TC,
      // 1.0/6.0)*1e-7);
      getPhase(i).getComponent(componentName)
          .setTriplePointTemperature(374.5 + 0.02617 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
              - 20172.0 / (getPhase(i).getComponent(componentName).getMolarMass() * 1000.0));
      getPhase(i).getComponent(componentName)
          .setHeatOfFusion(0.1426 / 0.238845 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
              * getPhase(i).getComponent(componentName).getTriplePointTemperature());
      getPhase(i).getComponent(componentName).setIdealGasEnthalpyOfFormation(-1462600 * molarMass - 47566.0);
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
    // Estimate Lennard-Jones parameters from critical properties using
    // Tee-Gotoh-Stewart correlation (Poling et al., 2001, Eq. 9-4.2 and 9-4.3).
    // The temp DB insert hardcodes water's LJ params (sigma=1.8, eps/k=809.1)
    // for all TBP fractions, which gives incorrect gas diffusion coefficients.
    if (TC > 0 && PC > 0) {
      double pcAtm = PC / 1.01325;
      double ljSigma = (2.3551 - 0.087 * acs) * Math.pow(TC / pcAtm, 1.0 / 3.0);
      double ljEpsOverK = TC * (0.7915 + 0.1693 * acs * acs);
      for (int i = 0; i < numberOfPhases; i++) {
        getPhase(i).getComponent(componentName).setLennardJonesMolecularDiameter(ljSigma);
        getPhase(i).getComponent(componentName).setLennardJonesEnergyParameter(ljEpsOverK);
      }
    }
    // Override water's dipole moment (1.8 Debye) and viscosity association factor
    // (0.076) from the temp DB insert. Hydrocarbons are non-polar (dipole ~ 0)
    // and have no association correction. These affect Chung and FrictionTheory
    // viscosity models through the Fc correction factor.
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).getComponent(componentName).setDebyeDipoleMoment(0.0);
      getPhase(i).getComponent(componentName).setViscosityAssociationFactor(0.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addTBPfraction(String componentName, double numberOfMoles, double molarMass, double density,
      double criticalTemperature, double criticalPressure, double acentricFactor) {
    if (density < 0.0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "addTBPfraction", "density", "is negative."));
    }
    if (molarMass < 0.0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "addTBPfraction", "molarMass", "is negative."));
    }
    // Auto-convert density from kg/m3 to g/cm3 (= specific gravity) if needed.
    if (density > 10.0) {
      logger.warn(
          "addTBPfraction: density value {} for '{}' appears to be in kg/m3. "
              + "Converting to g/cm3 (specific gravity) by dividing by 1000. " + "Expected range: 0.65-1.1 g/cm3.",
          density, componentName);
      density = density / 1000.0;
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
    double cpa = (-0.33886 + 0.02827 * Kwatson - 0.26105 * CF + 0.59332 * acsKeslerLee * CF) * 4.18682 * molarMass
        * 1e3;
    double cpb = (-(0.9291 - 1.1543 * Kwatson + 0.0368 * Kwatson * Kwatson) * 1e-4
        + CF * (4.56 - 9.48 * acsKeslerLee) * 1e-4) * 4.18682 * molarMass * 1.8 * 1e3;
    double cpc = (-1.6658e-7 + CF * (0.536 - 0.6828 * acsKeslerLee) * 1.0e-7) * 4.18682 * molarMass * 1.8 * 1.8 * 1.0e3;
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
      getPhase(i).getComponent(componentName)
          .setParachorParameter(characterization.getTBPModel().calcParachorParameter(molarMass, density)); // 59.3+2.34*molarMass*1000.0);
      // //0.5003*thermo.ThermodynamicConstantsInterface.R*TC/PC*(0.25969-racketZ));
      getPhase(i).getComponent(componentName)
          .setCriticalViscosity(characterization.getTBPModel().calcCriticalViscosity(molarMass * 1000.0, density)); // 7.94830*Math.sqrt(1e3*molarMass)*Math.pow(PC,2.0/3.0)/Math.pow(TC,
      // 1.0/6.0)*1e-7);
      getPhase(i).getComponent(componentName)
          .setTriplePointTemperature(374.5 + 0.02617 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
              - 20172.0 / (getPhase(i).getComponent(componentName).getMolarMass() * 1000.0));
      getPhase(i).getComponent(componentName)
          .setHeatOfFusion(0.1426 / 0.238845 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
              * getPhase(i).getComponent(componentName).getTriplePointTemperature());
      getPhase(i).getComponent(componentName).setIdealGasEnthalpyOfFormation(-1462600 * molarMass - 47566.0);
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
    // Estimate Lennard-Jones parameters from critical properties using
    // Tee-Gotoh-Stewart correlation (Poling et al., 2001, Eq. 9-4.2 and 9-4.3).
    if (TC > 0 && PC > 0) {
      double pcAtm = PC / 1.01325;
      double ljSigma = (2.3551 - 0.087 * acs) * Math.pow(TC / pcAtm, 1.0 / 3.0);
      double ljEpsOverK = TC * (0.7915 + 0.1693 * acs * acs);
      for (int i = 0; i < numberOfPhases; i++) {
        getPhase(i).getComponent(componentName).setLennardJonesMolecularDiameter(ljSigma);
        getPhase(i).getComponent(componentName).setLennardJonesEnergyParameter(ljEpsOverK);
      }
    }
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).getComponent(componentName).setDebyeDipoleMoment(0.0);
      getPhase(i).getComponent(componentName).setViscosityAssociationFactor(0.0);
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
        getPhase(i).getComponent(j).setComponentName(getPhase(i).getComponent(j).getComponentName() + postfix);
      }
    }
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
  public void autoSelectMixingRule() {
    logger.info("setting mixing rule");
    if (modelName.equals("CPAs-SRK-EOS") || modelName.equals("CPA-SRK-EOS")
        || modelName.equals("Electrolyte-CPA-EOS-statoil") || modelName.equals("CPAs-SRK-EOS-statoil")
        || modelName.equals("Electrolyte-CPA-EOS")) {
      this.setMixingRule(10);
      // System.out.println("mix rule 10");
    } else if ((modelName.equals("ScRK-EOS-HV") || modelName.equals("SRK-EOS") || modelName.equals("ScRK-EOS"))
        && this.getPhase(0).hasComponent("water")) {
      this.setMixingRule(4);
    } else if (modelName.equals("PR-EOS")) {
      this.setMixingRule(2);
    } else if (modelName.equals("Electrolyte-ScRK-EOS")) {
      this.setMixingRule(4);
    } else if (modelName.equals("Kent Eisenberg-model")) {
      this.setMixingRule(4);
    } else if (modelName.equals("UMR-PRU-EoS") || modelName.equals("UMR-PRU-MC-EoS") || modelName.equals("UMR-CPA")) {
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
  public final void calc_x_y() {
    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        if (j == 0) {
          getPhase(j).getComponent(i).setx(getPhase(0).getComponent(i).getK() * getPhase(j).getComponent(i).getz()
              / (1 - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponent(i).getK()));
        } else if (j == 1) {
          getPhase(j).getComponent(i).setx(getPhase(0).getComponent(i).getz()
              / (1.0 - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponent(i).getK()));
        }
        // phaseArray[j].getComponent(i).setx(phaseArray[0].getComponent(i).getx()
        // / phaseArray[0].getComponent(i).getK());
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
          getPhase(j).getComponent(i).setx(getPhase(j).getComponent(i).getK() * getPhase(j).getComponent(i).getz()
              / (1 - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponent(i).getK()));
        }
        if (j == 1) {
          getPhase(j).getComponent(i).setx(getPhase(0).getComponent(i).getz()
              / (1.0 - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponent(i).getK()));
        }
        // phaseArray[j].getComponent(i).setx(phaseArray[0].getComponent(i).getx()
        // / phaseArray[0].getComponent(i).getK());
        // System.out.println("comp: " + j + i + " " + c[j][i].getx());
      }
      // getPhase(j).normalize();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcHenrysConstant(String component) {
    if (numberOfPhases != 2) {
      throw new IllegalStateException("calcHenrysConstant: Henry's constant cannot be calculated with " + numberOfPhases
          + " phase(s); exactly two phases must be present. "
          + "Run a flash that produces a gas and a liquid phase before calling this method.");
    }
    int compNumb = getPhase(getPhaseIndex(0)).getComponent(component).getComponentNumber();
    double hc = getPhase(getPhaseIndex(0)).getFugacity(compNumb)
        / getPhase(getPhaseIndex(1)).getComponent(component).getx();
    return hc;
  }

  /** {@inheritDoc} */
  @Override
  public double calcHenrysConstant(String component, String unit) {
    double henryBarPerMoleFraction = calcHenrysConstant(component);
    String key = unit == null ? "" : unit.trim().toLowerCase().replace(" ", "");
    if (key.length() == 0 || key.equals("bar")) {
      return henryBarPerMoleFraction;
    }

    PhaseInterface liquidPhase = getPhase(getPhaseIndex(1));
    // Liquid molar volume in m3/mol; getMolarMass returns kg/mol and getDensity kg/m3.
    double molarVolume = liquidPhase.getMolarMass() / liquidPhase.getDensity("kg/m3");
    double concentrationPerBar = 1.0 / (molarVolume * henryBarPerMoleFraction);

    if (key.equals("mol/m3/bar") || key.equals("mmol/l/bar")) {
      return concentrationPerBar;
    }
    if (key.equals("mol/l/bar")) {
      return concentrationPerBar / 1000.0;
    }
    if (key.equals("mg/l/bar")) {
      return concentrationPerBar * liquidPhase.getComponent(component).getMolarMass() * 1000.0;
    }
    throw new IllegalArgumentException("calcHenrysConstant: unit '" + unit
        + "' is not supported; use bar, mol/m3/bar, mmol/L/bar, " + "mol/L/bar or mg/L/bar");
  }

  /** {@inheritDoc} */
  @Override
  public void calcInterfaceProperties() {
    interfaceProp.init();
  }

  /** {@inheritDoc} */
  @Override
  public void calcKIJ(boolean ok) {
    neqsim.thermo.mixingrule.EosMixingRuleHandler.calcEOSInteractionParameters = ok;
    for (int i = 0; i < numberOfPhases; i++) {
      ((PhaseEosInterface) getPhase(i)).getEosMixingRule().setCalcEOSInteractionParameters(ok);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void changeComponentName(String name, String newName) {
    markChemicalReactionStateStale();
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
  public boolean checkStability() {
    return checkStability;
  }

  /** {@inheritDoc} */
  @Override
  public void checkStability(boolean val) {
    checkStability = val;
  }

  /** Mark initialized chemical-reaction topology stale after a component identity change. */
  private void markChemicalReactionStateStale() {
    if (chemicalReactionOperations != null) {
      chemicalReactionStateStale = true;
    }
  }

  /** Reject use of chemical-reaction state built for a different component set. */
  private void requireCurrentChemicalReactionState() {
    if (chemicalReactionStateStale) {
      throw new IllegalStateException(
          "Chemical-reaction state is stale because component identities changed after chemicalReactionInit(). "
              + "Re-run chemicalReactionInit(), createDatabase(true), and setMixingRule(...) before the next reactive calculation.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void chemicalReactionInit() {
    chemicalReactionOperations = null;
    chemicalSystem = false;
    chemicalReactionStateStale = false;
    chemicalReactionOperations = new ChemicalReactionOperations(this);
    chemicalSystem = chemicalReactionOperations.hasReactions();
  }

  /** {@inheritDoc} */
  @Override
  public void clearAll() {
    setTotalNumberOfMolesRaw(0);
    phaseType[0] = PhaseType.GAS;
    phaseType[1] = PhaseType.LIQUID;
    numberOfComponents = 0;
    setNumberOfPhases(2);
    beta[0] = 1.0;
    beta[1] = 1.0;
    beta[2] = 1.0;
    beta[3] = 1.0;
    beta[4] = 1.0;
    beta[5] = 1.0;
    chemicalSystem = false;
    chemicalReactionOperations = null;
    chemicalReactionStateStale = false;

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
  public SystemThermo clone() {
    SystemThermo clonedSystem = null;
    try {
      clonedSystem = (SystemThermo) super.clone();
    } catch (CloneNotSupportedException ex) {
      throw new AssertionError("Clone failed for SystemThermo", ex);
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
    clonedSystem.characterization.setThermoSystem(clonedSystem);
    if (waxCharacterisation != null) {
      clonedSystem.waxCharacterisation = waxCharacterisation.clone();
    }
    if (oilAssayCharacterisation != null) {
      clonedSystem.oilAssayCharacterisation = oilAssayCharacterisation.clone();
      clonedSystem.oilAssayCharacterisation.setThermoSystem(clonedSystem);
      clonedSystem.oilAssayCharacterisation.setTotalAssayMass(oilAssayCharacterisation.getTotalAssayMass());
    }

    System.arraycopy(this.beta, 0, clonedSystem.beta, 0, beta.length);
    System.arraycopy(this.phaseType, 0, clonedSystem.phaseType, 0, phaseType.length);
    System.arraycopy(this.phaseIndex, 0, clonedSystem.phaseIndex, 0, phaseIndex.length);

    clonedSystem.phaseArray = phaseArray.clone();
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      clonedSystem.phaseArray[i] = phaseArray[i].clone();
    }

    if (chemicalReactionOperations != null) {
      clonedSystem.chemicalReactionInit();
    }

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void createDatabase(boolean reset) {
    if (reset) {
      resetDatabase();
    }

    int numberOfComponentsInPhase = getPhase(0).getNumberOfComponents();
    if (numberOfComponentsInPhase == 0) {
      logger.debug("Skipping database population – no components available in phase 0");
      return;
    }

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      String names = new String();
      for (int k = 0; k < numberOfComponentsInPhase - 1; k++) {
        names += "'" + this.getComponentNames()[k] + "', ";
      }
      names += "'" + this.getComponentNames()[numberOfComponentsInPhase - 1] + "'";

      if (NeqSimDataBase.createTemporaryTables()) {
        database.execute("insert into comptemp SELECT * FROM comp WHERE name IN (" + names + ")");
        database.execute("insert into intertemp SELECT DISTINCT * FROM inter WHERE comp1 IN (" + names
            + ") AND comp2 IN (" + names + ")");
        database.execute("delete FROM intertemp WHERE comp1=comp2");
      }

      for (int phaseNum = 0; phaseNum < maxNumberOfPhases; phaseNum++) {
        getPhase(phaseNum).setMixingRule(null);
      }

      for (int i = 0; i < numberOfComponents; i++) {
        if (getPhase(0).getComponent(i).isIsTBPfraction() || getPhase(0).getComponent(i).isIsPlusFraction()) {
          getPhase(0).getComponent(i).insertComponentIntoDatabase("");
        }
      }
    } catch (Exception ex) {
      logger.error("error in SystemThermo Class...createDatabase() method", ex);
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
      table[0][i + 2] = getPhase(i).getType().getDesc().toUpperCase();
    }

    StringBuffer buf = new StringBuffer();
    java.text.FieldPosition test = new java.text.FieldPosition(0);
    for (int j = 0; j < getPhases()[0].getNumberOfComponents(); j++) {
      buf = new StringBuffer();
      table[j + 1][1] = nf.format(getPhase(0).getComponent(j).getz(), buf, test).toString();
    }
    buf = new StringBuffer();
    table[getPhases()[0].getNumberOfComponents() + 4][1] = nf
        .format(getMolarMass(Units.getSymbol("Molar Mass")), buf, test).toString();
    buf = new StringBuffer();
    table[getPhases()[0].getNumberOfComponents() + 9][1] = nf
        .format(getEnthalpy(Units.getSymbol("enthalpy")), buf, test).toString();
    buf = new StringBuffer();
    table[getPhases()[0].getNumberOfComponents() + 10][1] = nf.format(getEntropy(Units.getSymbol("entropy")), buf, test)
        .toString();

    for (int i = 0; i < numberOfPhases; i++) {
      for (int j = 0; j < getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 1][i + 2] = nf.format(getPhase(i).getComponent(j).getx(), buf, test).toString();
        table[j + 1][6] = "[mole fraction]";
      }

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
      table[getPhases()[0].getNumberOfComponents() + 2][i + 2] = nf
          .format(getPhase(i).getDensity(Units.activeUnits.get("density").symbol), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 2][6] = Units.activeUnits.get("density").symbol;

      // Double.longValue(system.getPhase(i).getBeta());
      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 3][0] = "Phase Fraction";
      table[getPhases()[0].getNumberOfComponents() + 3][i + 2] = nf.format(getPhase(i).getBeta(), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 3][6] = "[mole fraction]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 4][0] = "Molar Mass";
      table[getPhases()[0].getNumberOfComponents() + 4][i + 2] = nf
          .format(getPhase(i).getMolarMass(Units.activeUnits.get("Molar Mass").symbol), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 4][6] = Units.activeUnits.get("Molar Mass").symbol;

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 5][0] = "Z factor";
      table[getPhases()[0].getNumberOfComponents() + 5][i + 2] = nf.format(getPhase(i).getZvolcorr(), buf, test)
          .toString();
      table[getPhases()[0].getNumberOfComponents() + 5][6] = "[-]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 6][0] = "Heat Capacity (Cp)";
      table[getPhases()[0].getNumberOfComponents() + 6][i + 2] = nf
          .format((getPhase(i).getCp(Units.activeUnits.get("Heat Capacity (Cp)").symbol)), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 6][6] = Units.activeUnits.get("Heat Capacity (Cp)").symbol;

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 7][0] = "Heat Capacity (Cv)";
      table[getPhases()[0].getNumberOfComponents() + 7][i + 2] = nf
          .format((getPhase(i).getCv(Units.activeUnits.get("Heat Capacity (Cv)").symbol)), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 7][6] = Units.activeUnits.get("Heat Capacity (Cv)").symbol;

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 8][0] = "Speed of Sound";
      table[getPhases()[0].getNumberOfComponents() + 8][i + 2] = nf
          .format((getPhase(i).getSoundSpeed(Units.getSymbol("speed of sound"))), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 8][6] = Units.getSymbol("speed of sound");

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 9][0] = "Enthalpy";
      table[getPhases()[0].getNumberOfComponents() + 9][i + 2] = nf
          .format((getPhase(i).getEnthalpy(Units.getSymbol("enthalpy"))), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 9][6] = Units.getSymbol("enthalpy");

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 10][0] = "Entropy";
      table[getPhases()[0].getNumberOfComponents() + 10][i + 2] = nf
          .format((getPhase(i).getEntropy(Units.getSymbol("entropy"))), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 10][6] = Units.getSymbol("entropy");

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 11][0] = "JT coefficient";
      table[getPhases()[0].getNumberOfComponents() + 11][i + 2] = nf
          .format((getPhase(i).getJouleThomsonCoefficient(Units.getSymbol("JT coefficient"))), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 11][6] = Units.getSymbol("JT coefficient");

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 13][0] = "Viscosity";
      table[getPhases()[0].getNumberOfComponents() + 13][i + 2] = nf
          .format((getPhase(i).getViscosity(Units.getSymbol("viscosity"))), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 13][6] = Units.getSymbol("viscosity");

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 14][0] = "Thermal Conductivity";
      table[getPhases()[0].getNumberOfComponents() + 14][i + 2] = nf
          .format(getPhase(i).getThermalConductivity(Units.getSymbol("thermal conductivity")), buf, test).toString();
      table[getPhases()[0].getNumberOfComponents() + 14][6] = Units.getSymbol("thermal conductivity");

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 15][0] = "Surface Tension";
      try {
        if (i < numberOfPhases - 1) {
          table[getPhases()[0].getNumberOfComponents() + 15][2] = nf
              .format(getInterphaseProperties().getSurfaceTension(0, 1), buf, test).toString();
          buf = new StringBuffer();
          table[getPhases()[0].getNumberOfComponents() + 15][3] = nf
              .format(getInterphaseProperties().getSurfaceTension(0, 1), buf, test).toString();
          buf = new StringBuffer();
          if (i == 1) {
            table[getPhases()[0].getNumberOfComponents() + 17][2] = nf
                .format(getInterphaseProperties().getSurfaceTension(0, 2), buf, test).toString();
            buf = new StringBuffer();
            table[getPhases()[0].getNumberOfComponents() + 17][4] = nf
                .format(getInterphaseProperties().getSurfaceTension(0, 2), buf, test).toString();
            table[getPhases()[0].getNumberOfComponents() + 17][6] = "[N/m]";
          }
          if (i == 1) {
            buf = new StringBuffer();
            table[getPhases()[0].getNumberOfComponents() + 16][3] = nf
                .format(getInterphaseProperties().getSurfaceTension(1, 2), buf, test).toString();
            buf = new StringBuffer();
            table[getPhases()[0].getNumberOfComponents() + 16][4] = nf
                .format(getInterphaseProperties().getSurfaceTension(1, 2), buf, test).toString();
            table[getPhases()[0].getNumberOfComponents() + 16][6] = "[N/m]";
          }
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      table[getPhases()[0].getNumberOfComponents() + 15][6] = "[N/m]";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 19][0] = "Pressure";
      table[getPhases()[0].getNumberOfComponents() + 19][i + 2] = Double
          .toString(getPhase(i).getPressure(Units.getSymbol("pressure")));
      table[getPhases()[0].getNumberOfComponents() + 19][6] = Units.getSymbol("pressure");

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 20][0] = "Temperature";
      table[getPhases()[0].getNumberOfComponents() + 20][i + 2] = Double
          .toString(getPhase(i).getTemperature(Units.getSymbol("temperature")));
      table[getPhases()[0].getNumberOfComponents() + 20][6] = Units.getSymbol("temperature");
      Double.toString(getPhase(i).getTemperature());

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 22][0] = "Model";
      table[getPhases()[0].getNumberOfComponents() + 22][i + 2] = getModelName();
      table[getPhases()[0].getNumberOfComponents() + 22][6] = "-";

      buf = new StringBuffer();
      table[getPhases()[0].getNumberOfComponents() + 23][0] = "Mixing Rule";
      try {
        table[getPhases()[0].getNumberOfComponents() + 23][i + 2] = ((PhaseEosInterface) getPhase(i))
            .getMixingRuleName();
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

  /**
   * Riazi-Daubert (1980) coefficient a in M = a * Tb^b * SG^c, with Tb in degrees Rankine and M in g/mol.
   */
  private static final double RD1980_A = 4.5673e-5;
  /** Riazi-Daubert (1980) boiling point exponent b. */
  private static final double RD1980_B = 2.1962;
  /** Riazi-Daubert (1980) specific gravity exponent c. */
  private static final double RD1980_C = -1.0164;

  /** {@inheritDoc} */
  @Override
  public void addTBPfraction2(String componentName, double numberOfMoles, double molarMass, double boilingPoint) {
    if (boilingPoint <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "addTBPfraction2",
          "boilingPoint", "must be positive."));
    }
    if (molarMass <= 0.0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "addTBPfraction2", "molarMass", "must be positive."));
    }

    double density = calculateDensityFromBoilingPoint(molarMass, boilingPoint);

    // Pin the boiling point so Tb-based TBP models (Lee-Kesler, Twu, Cavett) use the
    // requested value for Tc/Pc rather than back-correlating it from molar mass.
    characterization.getTBPModel().setBoilingPoint(boilingPoint);
    try {
      addTBPfraction(componentName, numberOfMoles, molarMass, density);
    } finally {
      characterization.getTBPModel().setBoilingPoint(0.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calculateDensityFromBoilingPoint(double molarMass, double boilingPoint) {
    if (molarMass <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "calculateDensityFromBoilingPoint", "molarMass", "must be positive."));
    }
    if (boilingPoint <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "calculateDensityFromBoilingPoint", "boilingPoint", "must be positive."));
    }

    // Riazi-Daubert (1980) M = a*Tb^c*SG^c inverted analytically for SG. The TBP model's own
    // calcTB cannot be used here: for the Pedersen models it carries no density dependence below
    // 540 g/mol, so the inverse problem has no solution (see PedersenTBPModelSRK.calcTB).
    double molarMassGmol = molarMass * 1000.0;
    double boilingPointRankine = boilingPoint * 1.8;
    double density = Math.pow(RD1980_A * Math.pow(boilingPointRankine, RD1980_B) / molarMassGmol, -1.0 / RD1980_C);

    if (boilingPoint < 300.0 || boilingPoint > 620.0 || molarMassGmol < 70.0 || molarMassGmol > 300.0) {
      logger.warn("calculateDensityFromBoilingPoint: molar mass {} g/mol and boiling point {} K fall outside the "
          + "Riazi-Daubert (1980) validated range (70-300 g/mol, 300-620 K). Specific gravity {} is an "
          + "extrapolation.", molarMassGmol, boilingPoint, density);
    }
    if (density < 0.5 || density > 1.3) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "calculateDensityFromBoilingPoint", "boilingPoint",
              "molar mass " + molarMassGmol + " g/mol combined with boiling point " + boilingPoint
                  + " K gives a non-physical specific gravity of " + density
                  + ". Expected 0.5-1.3. Check that the boiling point is in K and the molar mass in kg/mol."));
    }
    return density;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateDensityFromBoilingPointAndWatsonK(double boilingPoint, double watsonK) {
    if (boilingPoint <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "calculateDensityFromBoilingPointAndWatsonK", "boilingPoint", "must be positive."));
    }
    if (watsonK <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "calculateDensityFromBoilingPointAndWatsonK", "watsonK", "must be positive."));
    }
    double density = Math.pow(1.8 * boilingPoint, 1.0 / 3.0) / watsonK;
    if (density < 0.5 || density > 1.3) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "calculateDensityFromBoilingPointAndWatsonK", "watsonK", "boiling point " + boilingPoint + " K with Watson K "
              + watsonK + " gives a non-physical specific gravity of " + density + ". Expected 0.5-1.3."));
    }
    return density;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Add TBP fraction using density and boiling point, calculating molar mass.
   */
  @Override
  public void addTBPfraction3(String componentName, double numberOfMoles, double density, double boilingPoint) {
    if (boilingPoint <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "addTBPfraction3",
          "boilingPoint", "must be positive."));
    }
    if (density <= 0.0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "addTBPfraction3", "density", "must be positive."));
    }
    double molarMass = calculateMolarMassFromDensityAndBoilingPoint(density, boilingPoint);
    characterization.getTBPModel().setBoilingPoint(boilingPoint);
    try {
      addTBPfraction(componentName, numberOfMoles, molarMass, density);
    } finally {
      characterization.getTBPModel().setBoilingPoint(0.0);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates molar mass from density and boiling point
   */
  @Override
  public double calculateMolarMassFromDensityAndBoilingPoint(double density, double boilingPoint) {
    if (density <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "calculateMolarMassFromDensityAndBoilingPoint", "density", "must be positive."));
    }
    if (boilingPoint <= 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "calculateMolarMassFromDensityAndBoilingPoint", "boilingPoint", "must be positive."));
    }

    double lower = 0.01;
    double upper = 0.5;
    double fLower = characterization.getTBPModel().calcTB(lower * 1000.0, density) - boilingPoint;
    double fUpper = characterization.getTBPModel().calcTB(upper * 1000.0, density) - boilingPoint;

    if (fLower * fUpper > 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "calculateMolarMassFromDensityAndBoilingPoint", "boilingPoint",
          "boiling point " + boilingPoint + " K is not attainable with TBP model '"
              + characterization.getTBPModel().getName() + "' at specific gravity " + density + ". Attainable range is "
              + (fLower + boilingPoint) + " to " + (fUpper + boilingPoint) + " K for molar mass 10-500 g/mol."));
    }

    double tolerance = 1e-8;
    double molarMass = 0.5 * (lower + upper);
    for (int i = 0; i < 200 && (upper - lower) > tolerance; i++) {
      molarMass = 0.5 * (lower + upper);
      double fMid = characterization.getTBPModel().calcTB(molarMass * 1000.0, density) - boilingPoint;
      if (fMid == 0.0) {
        return molarMass;
      }
      if (fLower * fMid < 0.0) {
        upper = molarMass;
        fUpper = fMid;
      } else {
        lower = molarMass;
        fLower = fMid;
      }
    }
    return 0.5 * (lower + upper);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Add TBP fraction using density and boiling point, calculating molar mass.
   */
  @Override
  public void addTBPfraction4(String componentName, double numberOfMoles, double molarMass, double density,
      double boilingPoint) {
    characterization.getTBPModel().setBoilingPoint(boilingPoint);
    try {
      addTBPfraction(componentName, numberOfMoles, molarMass, density);
    } finally {
      // Without this the boiling point stays on the shared TBP model and overrides calcTB for
      // every fraction added afterwards.
      characterization.getTBPModel().setBoilingPoint(0.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteFluidPhase(int phaseNum) {
    for (int i = phaseNum; i < numberOfPhases; i++) {
      phaseIndex[i] = phaseIndex[i + 1];
    }
    numberOfPhases--;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void display(String name) {
    if (this.getNumberOfComponents() == 0) {
      return;
    }
    javax.swing.JFrame dialog = new javax.swing.JFrame("System-Report");
    java.awt.Dimension screenDimension = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
    java.awt.Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new java.awt.BorderLayout());

    String[] names = { "", "Feed", "Phase 1", "Phase 2", "Phase 3", "Phase 4", "Unit" };
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

  /** {@inheritDoc} */
  @Override
  public boolean doMultiPhaseCheck() {
    return multiPhaseCheck;
  }

  /** {@inheritDoc} */
  @Override
  public final boolean doSolidPhaseCheck() {
    return solidPhaseCheck;
  }

  /**
   * getAntoineVaporPressure.
   *
   * @param temp a double
   * @return a double
   */
  public double getAntoineVaporPressure(double temp) {
    return phaseArray[0].getAntoineVaporPressure(temp);
  }

  /** {@inheritDoc} */
  @Override
  public final double getBeta() {
    // Returns beta of the first phase (lightest/gas by convention after phase
    // identification)
    return beta[0];
  }

  /** {@inheritDoc} */
  @Override
  public final double getBeta(int phaseNum) {
    return beta[phaseIndex[phaseNum]];
  }

  /** {@inheritDoc} */
  @Override
  public String[] getCapeOpenProperties10() {
    return CapeOpenProperties10;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getCapeOpenProperties11() {
    return CapeOpenProperties11;
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
  public neqsim.thermo.characterization.Characterise getCharacterization() {
    return characterization;
  }

  /** {@inheritDoc} */
  @Override
  public ChemicalReactionOperations getChemicalReactionOperations() {
    requireCurrentChemicalReactionState();
    return chemicalReactionOperations;
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
  public String[] getCompIDs() {
    String[] ids = new String[numberOfComponents];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      ids[compNumb] = Integer.toString(getPhase(0).getComponent(compNumb).getIndex());
    }
    return ids;
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
  public String getComponentNameTag() {
    return componentNameTag;
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

  /** {@inheritDoc} */
  @Override
  public double getCorrectedVolumeFraction(int phaseNumber) {
    return getPhase(phaseNumber).getCorrectedVolume() / getCorrectedVolume();
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

  /**
   * Calculates the heat capacity at constant pressure (Cp) in the specified units. {@inheritDoc}
   */
  @Override
  public double getCp(String unit) {
    // The reference heat capacity (refCp) is the total heat capacity in J/K.
    double refCp = getCp();

    switch (unit) {
    case "J/K":
      // No conversion needed as the base unit is J/K.
      return refCp;

    case "J/molK":
      // To get molar heat capacity, divide the total heat capacity by the total
      // number of moles.
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for J/molK conversion.");
      }
      return refCp / getTotalNumberOfMoles();

    case "kJ/molK":
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for kJ/molK conversion.");
      }
      return refCp / getTotalNumberOfMoles() / 1000.0;

    case "J/kgK": {
      // To get specific heat capacity, divide the total heat capacity by the total
      // mass.
      // Total mass = total moles * molar mass (in kg/mol).
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for J/kgK conversion.");
      }
      return refCp / totalMass;
    }

    case "kJ/kgK": {
      // Same as J/kgK, but with an additional conversion from J to kJ.
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for kJ/kgK conversion.");
      }
      // Divide by total mass for specific heat capacity, and by 1000 for kJ.
      return refCp / totalMass / 1000.0;
    }

    case "btu/lbmole-F": {
      // This conversion is performed from the molar heat capacity (J/molK).
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for btu/lbmole-F conversion.");
      }
      double molarCp = refCp / getTotalNumberOfMoles(); // Cp in J/molK

      // CORRECTION: The factor 2.39006E-4 is for mass-based units (J/kgK).
      // The correct factor for molar-based units (J/molK) is 0.239006.
      final double J_PER_MOLK_TO_BTU_PER_LBMOLEF = 0.239006;

      return molarCp * J_PER_MOLK_TO_BTU_PER_LBMOLEF;
    }

    default:
      // Throw an exception if the requested unit is not supported.
      throw new IllegalArgumentException("Unit not supported: " + unit);
    }
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

  /**
   * Calculates the heat capacity at constant volume (Cv) in the specified units. {@inheritDoc}
   */
  @Override
  public double getCv(String unit) {
    // The reference heat capacity (refCv) is the total heat capacity at constant
    // volume in J/K.
    double refCv = getCv();

    switch (unit) {
    case "J/K":
      // No conversion needed as the base unit is J/K.
      return refCv;

    case "J/molK":
      // To get molar heat capacity, divide the total heat capacity by the total
      // number of moles.
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for J/molK conversion.");
      }
      return refCv / getTotalNumberOfMoles();

    case "kJ/molK":
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for kJ/molK conversion.");
      }
      return refCv / getTotalNumberOfMoles() / 1000.0;

    case "J/kgK": {
      // To get specific heat capacity, divide the total heat capacity by the total
      // mass.
      // Total mass = total moles * molar mass (in kg/mol).
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for J/kgK conversion.");
      }
      return refCv / totalMass;
    }

    case "kJ/kgK": {
      // Same as J/kgK, but with an additional conversion from J to kJ.
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for kJ/kgK conversion.");
      }
      // Divide by total mass for specific heat capacity, and by 1000 for kJ.
      return refCv / totalMass / 1000.0;
    }

    case "btu/lbmole-F": {
      // This conversion is performed from the molar heat capacity (J/molK).
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for btu/lbmole-F conversion.");
      }
      double molarCv = refCv / getTotalNumberOfMoles(); // Cv in J/molK

      // CORRECTION: The factor 2.39006E-4 is for mass-based units (J/kgK).
      // The correct factor for molar-based units (J/molK) is 0.239006.
      final double J_PER_MOLK_TO_BTU_PER_LBMOLEF = 0.239006;

      return molarCv * J_PER_MOLK_TO_BTU_PER_LBMOLEF;
    }

    default:
      // Throw an exception if the requested unit is not supported.
      throw new IllegalArgumentException("Unit not supported: " + unit);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    double totalVolume = getVolume();
    if (totalVolume == 0.0) {
      return 0.0;
    }
    double density = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      density += getPhase(i).getVolume() / totalVolume * getPhase(i).getDensity();
    }
    return density;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity(String unit) {
    double density = 0;
    double totalVolume = getVolume();
    for (int i = 0; i < getNumberOfPhases(); i++) {
      double volFrac = getCorrectedVolumeFraction(i);
      if (Double.isNaN(volFrac) || Double.isInfinite(volFrac)) {
        volFrac = totalVolume > 0 ? getPhase(i).getVolume() / totalVolume : 0;
      }
      density += volFrac * getPhase(i).getPhysicalProperties().getDensity();
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
      return getMolarMass() * ThermodynamicConstantsInterface.atm / ThermodynamicConstantsInterface.R
          / ThermodynamicConstantsInterface.standardStateTemperature;
    case "mol/m3":
      conversionFactor = 1.0 / getMolarMass();
      break;
    default:
      throw new RuntimeException("unit not supported " + unit);
    }
    return refDensity * conversionFactor;
  }

  /**
   * getPdVtn.
   *
   * @return dpdv
   */
  public double getdPdVtn() {
    double dPdV = 0.0;
    for (int i = 0; i < numberOfPhases; i++) {
      if (isPhase(i)) {
        dPdV += getPhase(i).getdPdVTn(); // * getPhase(i).getVolume() / getVolume();
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
  public SystemInterface getEmptySystemClone() {
    int phaseNumber = 0;

    SystemInterface newSystem = this.clone();

    double totalMolesInSystem = 0.0;
    double scaleFactor = 1.0e30;

    for (int j = 0; j < getMaxNumberOfPhases(); j++) {
      phaseNumber = j;
      double phaseMoles = 0.0;
      for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
        double scaledMoles = getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase() / scaleFactor;
        newSystem.getPhase(j).getComponent(i).setNumberOfmoles(scaledMoles);
        newSystem.getPhase(j).getComponent(i).setNumberOfMolesInPhase(scaledMoles);
        phaseMoles += scaledMoles;
      }
      totalMolesInSystem += phaseMoles;
    }

    if (totalMolesInSystem <= 0.0) {
      totalMolesInSystem = 1.0e-50;
    }

    ((SystemThermo) newSystem).setTotalNumberOfMolesRaw(totalMolesInSystem);
    ((SystemThermo) newSystem).isInitialized = false;

    newSystem.init(0);
    // newSystem.init(1);
    return newSystem;
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

  /**
   * Calculates the enthalpy in the specified units. {@inheritDoc}
   */
  @Override
  public double getEnthalpy(String unit) {
    // The reference enthalpy (refEnthalpy) is the total enthalpy in Joules.
    double refEnthalpy = getEnthalpy();

    switch (unit) {
    case "J":
      // No conversion needed.
      return refEnthalpy;

    case "Btu":
      // 1 J = 0.000947817 Btu
      final double J_TO_BTU = 0.000947817;
      return refEnthalpy * J_TO_BTU;

    case "J/mol": {
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for J/mol conversion.");
      }
      return refEnthalpy / getTotalNumberOfMoles();
    }

    case "kJ/kmol": {
      // Note: The units J/mol and kJ/kmol are numerically equivalent.
      // 1 J/mol = (1/1000 kJ) / (1/1000 kmol) = 1 kJ/kmol.
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for kJ/kmol conversion.");
      }
      // The original code incorrectly divided by 1000.
      return refEnthalpy / getTotalNumberOfMoles();
    }

    case "J/kg": {
      // To get specific enthalpy, divide the total enthalpy by the total mass.
      // Total mass = total moles * molar mass (in kg/mol).
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for J/kg conversion.");
      }
      return refEnthalpy / totalMass;
    }

    case "kJ/kg": {
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for kJ/kg conversion.");
      }
      return refEnthalpy / totalMass / 1000.0;
    }

    case "Btu/lbmol": {
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for Btu/lbmol conversion.");
      }
      double molarEnthalpy = refEnthalpy / getTotalNumberOfMoles(); // Enthalpy in J/mol
      // 1 J/mol = 0.429923 Btu/lbmol
      final double J_PER_MOL_TO_BTU_PER_LBMOL = 0.429923;
      return molarEnthalpy * J_PER_MOL_TO_BTU_PER_LBMOL;
    }

    default:
      throw new IllegalArgumentException("Unit not supported: " + unit);
    }
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

  /**
   * Calculates the entropy in the specified units. {@inheritDoc}
   */
  @Override
  public double getEntropy(String unit) {
    // The reference entropy (refEntropy) is the total entropy in J/K.
    double refEntropy = getEntropy();

    switch (unit) {
    case "J/K":
      // No conversion needed.
      return refEntropy;

    case "J/molK": {
      // To get molar entropy, divide the total entropy by the total number of moles.
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for J/molK conversion.");
      }
      return refEntropy / getTotalNumberOfMoles();
    }

    case "kJ/molK": {
      if (getTotalNumberOfMoles() == 0) {
        throw new ArithmeticException("Total number of moles cannot be zero for kJ/molK conversion.");
      }
      return refEntropy / getTotalNumberOfMoles() / 1000.0;
    }

    case "J/kgK": {
      // To get specific entropy, divide the total entropy by the total mass.
      // Total mass = total moles * molar mass (in kg/mol).
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for J/kgK conversion.");
      }
      return refEntropy / totalMass;
    }

    case "kJ/kgK": {
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for kJ/kgK conversion.");
      }
      return refEntropy / totalMass / 1000.0;
    }

    case "btu/lb-F": { // Assuming "lb" is pound-mass and "F" interval is equivalent to Rankine.
      // This conversion is best performed from the specific entropy (J/kgK).
      double totalMass = getTotalNumberOfMoles() * getMolarMass();
      if (totalMass == 0) {
        throw new ArithmeticException("Total mass cannot be zero for btu/lb-F conversion.");
      }
      double specificEntropySI = refEntropy / totalMass; // Entropy in J/kgK

      // 1 J/kgK = 0.000238846 btu/lbm-R
      final double J_PER_KGK_TO_BTU_PER_LBF = 0.000238846;
      return specificEntropySI * J_PER_KGK_TO_BTU_PER_LBF;
    }

    default:
      // Throw an exception if the requested unit is not supported.
      throw new IllegalArgumentException("Unit not supported: " + unit);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getExergy(double temperatureOfSurroundings) {
    double getExergy = getEnthalpy() - temperatureOfSurroundings * getEntropy();
    return getExergy;
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
  public double getFlowRate(String flowunit) {
    if (flowunit.equals("kg/sec")) {
      return totalNumberOfMoles * getMolarMass();
    } else if (flowunit.equals("kg/min")) {
      return totalNumberOfMoles * getMolarMass() * 60.0;
    } else if (flowunit.equals("kg/hr")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0;
    } else if (flowunit.equals("kg/day")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0 * 24.0;
    } else if (flowunit.equals("m3/sec") || flowunit.equals("Am3/sec")) {
      initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return totalNumberOfMoles * getMolarMass() / getDensity("kg/m3");
      // return getVolume() / 1.0e5;
    } else if (flowunit.equals("m3/min") || flowunit.equals("Am3/min")) {
      initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return totalNumberOfMoles * getMolarMass() * 60.0 / getDensity("kg/m3");
      // return getVolume() / 1.0e5 * 60.0;
    } else if (flowunit.equals("m3/hr") || flowunit.equals("Am3/hr")) {
      // return getVolume() / 1.0e5 * 3600.0;
      initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return totalNumberOfMoles * getMolarMass() * 3600.0 / getDensity("kg/m3");
    } else if (flowunit.equals("m3/day") || flowunit.equals("Am3/day")) {
      initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return totalNumberOfMoles * getMolarMass() * 3600.0 * 24.0 / getDensity("kg/m3");
    } else if (flowunit.equals("idSm3/sec")) {
      return totalNumberOfMoles * getMolarMass() / getIdealLiquidDensity("kg/m3");
    } else if (flowunit.equals("idSm3/min")) {
      return totalNumberOfMoles * getMolarMass() * 60.0 / getIdealLiquidDensity("kg/m3");
    } else if (flowunit.equals("idSm3/hr")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0 / getIdealLiquidDensity("kg/m3");
    } else if (flowunit.equals("idSm3/day")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0 * 24.0 / getIdealLiquidDensity("kg/m3");
    } else if (flowunit.equals("gallons/min")) {
      initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return totalNumberOfMoles * getMolarMass() * 60.0 / getDensity("kg/m3") * 1000 / 3.78541178;
    } else if (flowunit.equals("Sm3/sec")) {
      return totalNumberOfMoles * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("Sm3/min")) {
      return totalNumberOfMoles * 60.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("Sm3/hr")) {
      return totalNumberOfMoles * 3600.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("Sm3/day")) {
      return totalNumberOfMoles * 3600.0 * 24.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("MSm3/day")) {
      return totalNumberOfMoles * 3600.0 * 24.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature / ThermodynamicConstantsInterface.atm / 1.0e6;
    } else if (flowunit.equals("MSm3/hr")) {
      return totalNumberOfMoles * 3600.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature / ThermodynamicConstantsInterface.atm / 1.0e6;
    } else if (flowunit.equals("mole/sec") || flowunit.equals("mol/sec")) {
      return totalNumberOfMoles;
    } else if (flowunit.equals("mole/min") || flowunit.equals("mol/min")) {
      return totalNumberOfMoles * 60.0;
    } else if (flowunit.equals("mole/hr") || flowunit.equals("mol/hr")) {
      return totalNumberOfMoles * 3600.0;
    } else if (flowunit.equals("kmole/sec") || flowunit.equals("kmol/sec")) {
      return totalNumberOfMoles / 1000.0;
    } else if (flowunit.equals("kmole/min") || flowunit.equals("kmol/min")) {
      return totalNumberOfMoles * 60.0 / 1000.0;
    } else if (flowunit.equals("kmole/hr") || flowunit.equals("kmol/hr")) {
      return totalNumberOfMoles * 3600.0 / 1000.0;
    } else if (flowunit.equals("kmole/day") || flowunit.equals("kmol/day")) {
      return totalNumberOfMoles * 3600.0 * 24.0 / 1000.0;
    } else if (flowunit.equals("lbmole/hr") || flowunit.equals("lbmol/hr")) {
      return totalNumberOfMoles * 3600.0 / 1000.0 * 2.20462262;
    } else if (flowunit.equals("lb/hr")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0 * 2.20462262;
    } else if (flowunit.equals("barrel/day") || flowunit.equals("bbl/day")) {
      return totalNumberOfMoles * getMolarMass() * 3600.0 * 24.0 * 2.20462262 * 0.068;
    } else {
      throw new RuntimeException("failed.. unit: " + flowunit + " not supported. Supported units: kg/sec, kg/min, "
          + "kg/hr, kg/day, m3/sec, Am3/sec, m3/min, Am3/min, m3/hr, Am3/hr, m3/day, Am3/day, "
          + "idSm3/sec, idSm3/min, idSm3/hr, idSm3/day, gallons/min, Sm3/sec, Sm3/min, Sm3/hr, "
          + "Sm3/day, MSm3/day, MSm3/hr, mole/sec, mol/sec, mole/min, mol/min, mole/hr, "
          + "mol/hr, kmole/sec, kmol/sec, kmole/min, kmol/min, kmole/hr, kmol/hr, "
          + "kmole/day, kmol/day, lbmole/hr, lbmol/hr, lb/hr, barrel/day, bbl/day");
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getFluidInfo() {
    return fluidInfo;
  }

  /** {@inheritDoc} */
  @Override
  public String getFluidName() {
    return fluidName;
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma() {
    return getCp() / getCv();
  }

  /** {@inheritDoc} */
  @Override
  public final PhaseInterface getGasPhase() {
    for (int phaseNum = 0; phaseNum < numberOfPhases; phaseNum++) {
      if (phaseArray[phaseIndex[phaseNum]].getType() == PhaseType.GAS) {
        return phaseArray[phaseNum];
      }
    }
    logger.info("No gas phase at current state.");
    return null;
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
  public double getHelmholtzEnergy() {
    double helmholtzEnergy = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      helmholtzEnergy += getPhase(i).getHelmholtzEnergy();
    }
    return helmholtzEnergy;
  }

  /** {@inheritDoc} */
  @Override
  public boolean getHydrateCheck() {
    return hydrateCheck;
  }

  /** {@inheritDoc} */
  @Override
  public double getIdealLiquidDensity(String unit) {
    double normalLiquidDensity = 0.0;
    double molarMass = getMolarMass();
    for (int i = 0; i < getNumberOfComponents(); i++) {
      normalLiquidDensity += getComponent(i).getNormalLiquidDensity() * getComponent(i).getz()
          * getComponent(i).getMolarMass() / molarMass;
    }
    switch (unit) {
    case "gr/cm3":
      return normalLiquidDensity;
    case "kg/m3":
      return normalLiquidDensity * 1000.0;
    default:
      throw new RuntimeException("unit not supported " + unit);
    }
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
      return interfaceProp.getSurfaceTension(getPhaseNumberOfPhase(phase1), getPhaseNumberOfPhase(phase2));
    } else {
      return Double.NaN;
    }
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
  public InterphasePropertiesInterface getInterphaseProperties() {
    return interfaceProp;
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
    case "K/Pa":
      conversionFactor = 1.0e-5;
      break;
    case "F/psi":
      conversionFactor = 1.8 * 1.0 / 14.503773773;
      break;
    default:
      throw new RuntimeException("unit not supported " + unit);
    }
    return JTcoef * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getKappa() {
    return -getCp() / getCv() * getVolume() / getPressure() * getdPdVtn();
  }

  /** {@inheritDoc} */
  @Override
  public double getKinematicViscosity() {
    return getViscosity() / getDensity();
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
  public final PhaseInterface getLiquidPhase() {
    for (int phaseNum = 0; phaseNum < numberOfPhases; phaseNum++) {
      if (phaseArray[phaseIndex[phaseNum]].getType() == PhaseType.LIQUID) {
        return phaseArray[phaseNum];
      }
    }
    logger.info("No liquid phase at current state.");
    return null;
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
  public PhaseInterface getLowestGibbsEnergyPhase() {
    if (getPhase(0).getGibbsEnergy() < getPhase(1).getGibbsEnergy()) {
      return getPhase(0);
    } else {
      return getPhase(1);
    }
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

  /** {@inheritDoc} */
  @Override
  public int getMaxNumberOfPhases() {
    return maxNumberOfPhases;
  }

  /** {@inheritDoc} */
  @Override
  public MixingRuleTypeInterface getMixingRule() {
    return mixingRuleType;
  }

  /** {@inheritDoc} */
  @Override
  public String getMixingRuleName() {
    return ((PhaseEosInterface) getPhase(0)).getEosMixingRule().getName();
  }

  /** {@inheritDoc} */
  @Override
  public String getModelName() {
    return modelName;
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
  public double[] getWeightBasedComposition() {
    PhaseInterface phase = this.getPhase(0);
    double[] comp = new double[phase.getNumberOfComponents()];

    double totalMass = 0.0;
    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      totalMass += phase.getComponent(compNumb).getz() * phase.getComponent(compNumb).getMolarMass();
    }

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      comp[compNumb] = phase.getComponent(compNumb).getz() * phase.getComponent(compNumb).getMolarMass() / totalMass;
    }
    return comp;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarMass() {
    double tempVar = 0;
    double sumz = 0;
    for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
      double zi = phaseArray[0].getComponent(i).getz();
      tempVar += zi * phaseArray[0].getComponent(i).getMolarMass();
      sumz += zi;
    }
    // Normalise by the sum of overall mole fractions so the (intensive) molar mass is
    // invariant to any scaling of the mole bookkeeping (e.g. after
    // setTotalNumberOfMoles). When z already sums to 1 this is a no-op.
    if (sumz > 0) {
      return tempVar / sumz;
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
    case "g/mol":
    case "gr/mol":
    case "kg/kmol":
      conversionFactor = 1000.0;
      break;
    case "lbm/lbmol":
      conversionFactor = 1000.0;
      break;
    default:
      throw new RuntimeException("unit not supported " + unit);
    }
    return refMolarMass * conversionFactor;
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
  public double getMolarVolume() {
    if (!this.isInitialized) {
      this.init(0);
    }
    if (!isBetaValid()) {
      logger.warn("getMolarVolume", "Calculation is wrong, as beta is not valid. Perform flash");
    }
    double volume = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      volume += beta[phaseIndex[i]] * getPhase(i).getMolarVolume();
    }
    return volume;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarVolume(String unit) {
    if (!this.isInitialized) {
      this.init(0);
    }
    if (!isBetaValid()) {
      logger.warn("getMolarVolume", "Calculation is wrong, as beta is not valid. Perform flash");
    }
    double volume = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      volume += beta[phaseIndex[i]] * getPhase(i).getMolarVolume(unit);
    }
    return volume;
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
  public double getMoleFraction(int phaseNumber) {
    return getPhase(phaseNumber).getBeta();
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
  public double[] getNormalBoilingPointTemperatures() {
    double[] bt = new double[numberOfComponents];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      bt[compNumb] = getPhase(0).getComponent(compNumb).getNormalBoilingPoint();
    }
    return bt;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfComponents() {
    return getComponentNames().length;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfOilFractionComponents() {
    int number = 0;
    for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction() || getPhase(0).getComponent(i).isIsPlusFraction()) {
        number++;
      }
    }
    return number;
  }

  /** {@inheritDoc} */
  @Override
  public final int getNumberOfPhases() {
    return numberOfPhases;
  }

  /** {@inheritDoc} */
  @Override
  public int[] getOilFractionIDs() {
    int numb = getNumberOfOilFractionComponents();
    int[] IDs = new int[numb];
    for (int i = 0; i < numb; i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction() || getPhase(0).getComponent(i).isIsPlusFraction()) {
        IDs[i] = getPhase(0).getComponent(i).getIndex();
      }
    }
    return IDs;
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
  public final double getPC() {
    return criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public final PhaseInterface getPhase(int i) {
    if (i < 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getPhase", "i",
          i + " is not valid, must be in the range 0-" + this.getNumberOfPhases()));
    } else if (i >= getNumberOfPhases() - 1 && phaseArray[phaseIndex[i]] == null) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getPhase", "i",
          " - Can not return phase number " + i + ". Current number of phases are " + getNumberOfPhases()));
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
  public final double getPhaseFraction(String phaseTypeName, String unit) {
    int phaseNumber = getPhaseNumberOfPhase(phaseTypeName);
    switch (unit) {
    case "mole":
      return getBeta(phaseNumber);
    case "volume":
      return getVolumeFraction(phaseNumber);
    case "mass":
      initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return getVolumeFraction(phaseNumber) * getPhase(phaseNumber).getDensity("kg/m3") / getDensity("kg/m3");
    default:
      throw new RuntimeException("unit not supported " + unit);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final int getPhaseIndex(int index) {
    return phaseIndex[index];
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
  public int getPhaseIndex(String phaseTypeName) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (getPhase(i).getPhaseTypeName().equals(phaseTypeName)) {
        return phaseIndex[i];
      }
    }
    logger.debug("Phase type '{}' not found, returning first phase index", phaseTypeName);
    return phaseIndex[0];
  }

  /** {@inheritDoc} */
  @Override
  public int getPhaseNumberOfPhase(PhaseType pt) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (getPhase(i).getType() == pt) {
        return i;
      }
      // ASPHALTENE and LIQUID_ASPHALTENE are asphaltene-related phases, match when
      // looking for
      // either
      if (pt == PhaseType.ASPHALTENE && getPhase(i).getType() == PhaseType.LIQUID_ASPHALTENE) {
        return i;
      }
      if (pt == PhaseType.LIQUID_ASPHALTENE && getPhase(i).getType() == PhaseType.ASPHALTENE) {
        return i;
      }
      // ASPHALTENE is a solid-like phase, so also match when looking for SOLID
      if (pt == PhaseType.SOLID && getPhase(i).getType() == PhaseType.ASPHALTENE) {
        return i;
      }
    }
    logger.debug("Phase type {} not found, returning phase 0", pt);
    return 0;
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
  public final PhaseInterface[] getPhases() {
    return phaseArray;
  }

  /** {@inheritDoc} */
  @Override
  public final double getPressure() {
    return phaseArray[0].getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public final double getPressure(int phaseNumber) {
    return getPhase(phaseIndex[phaseNumber]).getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public final double getPressure(String unit) {
    neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(getPressure(), "bara");
    return presConversion.getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public SystemProperties getProperties() {
    return new SystemProperties(this);
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
  public double getProperty(String prop, int phaseNum) {
    initPhysicalProperties();
    if (prop.equals("temperature")) {
      return getPhase(phaseNum).getTemperature();
    } else if (prop.equals("pressure")) {
      return getPhase(phaseNum).getPressure();
    } else if (prop.equals("compressibility")) {
      return getPhase(phaseNum).getZ();
    } else if (prop.equals("density")) {
      return getPhase(phaseNum).getPhysicalProperties().getDensity();
    } else if (prop.equals("beta")) {
      return getPhase(phaseNum).getBeta();
    } else if (prop.equals("enthalpy")) {
      return getPhase(phaseNum).getEnthalpy();
    } else if (prop.equals("entropy")) {
      return getPhase(phaseNum).getEntropy();
    } else if (prop.equals("viscosity")) {
      return getPhase(phaseNum).getPhysicalProperties().getViscosity();
    } else if (prop.equals("conductivity")) {
      return getPhase(phaseNum).getPhysicalProperties().getConductivity();
    } else {
      return 1.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getProperty(String prop, String compName, int phaseNum) {
    if (prop.equals("molefraction")) {
      return getPhase(phaseNum).getComponent(compName).getx();
    } else if (prop.equals("fugacitycoefficient")) {
      return getPhase(phaseNum).getComponent(compName).getFugacityCoefficient();
    } else if (prop.equals("logfugdT")) {
      return getPhase(phaseNum).getComponent(compName).getdfugdt();
    } else if (prop.equals("logfugdP")) {
      return getPhase(phaseNum).getComponent(compName).getdfugdp();
    } else {
      return 1.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return resultTable;
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
    case "ft/sec":
      conversionFactor = 3.280839895;
      break;
    default:
      throw new RuntimeException("unit not supported " + unit);
    }
    return refVel * conversionFactor;
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

  /**
   * Get sum of phase <code>beta</code> values.
   *
   * @return Sum of <code>beta</code> beta values
   */
  public final double getSumBeta() {
    double sum = 0;
    for (int k = 0; k < numberOfPhases; k++) {
      sum += getBeta(k);
    }
    return sum;
  }

  /**
   * Verify if sum of beta is 1. Used to check if System needs to be flashed.
   *
   * @return True if the sum of beta is close to 1.
   */
  public boolean isBetaValid() {
    return this.getSumBeta() > 1.0 - ThermodynamicModelSettings.phaseFractionMinimumLimit
        && this.getSumBeta() < 1.0 + ThermodynamicModelSettings.phaseFractionMinimumLimit;
  }

  /** {@inheritDoc} */
  @Override
  public final double getTC() {
    return criticalTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public final double getTemperature() {
    return phaseArray[0].getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature(int phaseNumber) {
    return getPhase(phaseIndex[phaseNumber]).getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public final double getTemperature(String unit) {
    neqsim.util.unit.TemperatureUnit tempConversion = new neqsim.util.unit.TemperatureUnit(getTemperature(), "K");
    return tempConversion.getValue(unit);
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
    case "J/sec-m-K":
      conversionFactor = 1.0;
      break;
    case "W/cmK":
      conversionFactor = 0.01;
      break;
    case "Btu/hr-ft-F":
      conversionFactor = 0.5781759824;
      break;
    default:
      throw new RuntimeException("unit not supported " + unit);
    }
    return refConductivity * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalNumberOfMoles() {
    return this.totalNumberOfMoles;
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
  public double getVolumeFraction(int phaseNumber) {
    return getPhase(phaseNumber).getVolume() / getVolume();
  }

  /** {@inheritDoc} */
  @Override
  public OilAssayCharacterisation getOilAssayCharacterisation() {
    if (oilAssayCharacterisation == null) {
      oilAssayCharacterisation = new OilAssayCharacterisation(this);
    }
    return oilAssayCharacterisation;
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
  public double getWtFraction(int phaseNumber) {
    return getPhase(phaseNumber).getWtFraction(this);
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
  public double getZvolcorr() {
    return getPressure("Pa") * getMolarMass() / neqsim.thermo.ThermodynamicConstantsInterface.R / getTemperature()
        / getDensity("kg/m3");
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasPhaseType(PhaseType pt) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (getPhase(i) == null) {
        continue;
      }
      if (getPhase(i).getType() == pt) {
        return true;
      }
      // ASPHALTENE and LIQUID_ASPHALTENE are asphaltene-related phases
      if (pt == PhaseType.ASPHALTENE && getPhase(i).getType() == PhaseType.LIQUID_ASPHALTENE) {
        return true;
      }
      if (pt == PhaseType.LIQUID_ASPHALTENE && getPhase(i).getType() == PhaseType.ASPHALTENE) {
        return true;
      }
      // ASPHALTENE is a solid-like phase, so also match when looking for SOLID
      if (pt == PhaseType.SOLID && getPhase(i).getType() == PhaseType.ASPHALTENE) {
        return true;
      }
      if (getPhase(i).getPhaseTypeName().equals(pt.getDesc())) {
        logger.error("Bug in setting phasetype somewhere. Phasetype and phasetypename should be the same.");
        return true;
      }
    }
    return false;
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
   * hasTBPFraction.
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
  public void init(int initType) {
    if (!this.isInitialized) {
      initBeta();
      init_x_y();
    }
    if (this.numericDerivatives) {
      initNumeric(initType);
    } else {
      initAnalytic(initType);
    }

    this.isInitialized = true;
  }

  /** {@inheritDoc} */
  @Override
  public void init(int type, int phaseNum) {
    if (this.numericDerivatives) {
      initNumeric(type, phaseNum);
    } else {
      initAnalytic(type, phaseNum);
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
        getPhase(j).getComponent(i).setz(getPhase(j).getComponent(i).getNumberOfmoles() / getTotalNumberOfMoles());
        getPhase(j).getComponent(i)
            .setx(getPhase(j).getComponent(i).getNumberOfMolesInPhase() / getPhase(j).getNumberOfMolesInPhase());
      }
      getPhase(j).normalize();
    }
  }

  /**
   * initAnalytic.
   *
   * @param type a int. 0 to initialize and 1 to reset, 2 to calculate T and P derivatives, 3 to calculate all
   * derivatives and 4 to calculate all derivatives numerically
   */
  public void initAnalytic(int type) {
    if (type == 0) {
      reInitPhaseInformation();
      for (int i = 0; i < getMaxNumberOfPhases(); i++) {
        if (isPhase(i)) {
          getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, type, phaseType[phaseIndex[i]],
              beta[phaseIndex[i]]);
        }
      }
      setNumberOfPhases(2);
    }

    if (type > 0) {
      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          // todo: possible bug here, some components check for initType >= 3
          getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, Math.min(3, type), phaseType[phaseIndex[i]],
              beta[phaseIndex[i]]);
        }
      }

      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          PhaseInterface ph = getPhase(i);
          for (int j = 0; j < numberOfComponents; j++) {
            ph.getComponent(j).fugcoef(ph);
          }
        }
      }
    }

    if (type == 4) { // special case, calculate all derivatives numerically
      for (int i = 0; i < numberOfPhases; i++) {
        if (isPhase(i)) {
          PhaseInterface ph = getPhase(i);
          double phTemp = ph.getTemperature();
          double phPres = ph.getPressure();
          for (int j = 0; j < numberOfComponents; j++) {
            ComponentInterface comp = ph.getComponent(j);
            // TODO: only runs two calculations init == 3 runs three
            comp.fugcoefDiffTempNumeric(ph, numberOfComponents, phTemp, phPres);
            comp.fugcoefDiffPresNumeric(ph, numberOfComponents, phTemp, phPres);
          }
        }
      }
    } else {
      if (type > 1) { // calculate T and P derivatives
        for (int i = 0; i < numberOfPhases; i++) {
          if (isPhase(i)) {
            PhaseInterface ph = getPhase(i);
            for (int j = 0; j < numberOfComponents; j++) {
              ComponentInterface comp = ph.getComponent(j);
              comp.logfugcoefdT(ph);
              comp.logfugcoefdP(ph);
            }
          }
        }
      }
      if (type == 3) { // calculate all derivatives
        for (int i = 0; i < numberOfPhases; i++) {
          if (isPhase(i)) {
            PhaseInterface ph = getPhase(i);
            for (int j = 0; j < numberOfComponents; j++) {
              ph.getComponent(j).logfugcoefdN(ph);
            }
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
   * initAnalytic.
   *
   * @param type a int
   * @param phaseNum a int
   */
  public void initAnalytic(int type, int phaseNum) {
    if (type == 0) {
      beta[0] = 1.0;
      phaseIndex[phaseNum] = phaseNum;
    }

    if (isPhase(phaseNum)) {
      PhaseInterface ph = getPhase(phaseNum);
      ph.init(getTotalNumberOfMoles(), numberOfComponents, type, phaseType[phaseIndex[phaseNum]],
          beta[phaseIndex[phaseNum]]);
      if (type > 0) {
        for (int j = 0; j < numberOfComponents; j++) {
          ph.getComponent(j).fugcoef(ph);
        }
      }
      if (type > 1) {
        for (int j = 0; j < numberOfComponents; j++) {
          ComponentInterface comp = ph.getComponent(j);
          comp.logfugcoefdT(ph);
          comp.logfugcoefdP(ph);
        }
      }
      if (type > 2) {
        for (int j = 0; j < numberOfComponents; j++) {
          // dT/dP already computed in type > 1 block above — only compute dN here
          ph.getComponent(j).logfugcoefdN(ph);
        }
      }
    }

    // Only reclassify the initialized phase (not all phases).
    // Phase 0 is the reference phase and is allowed to remain GAS.
    // This is consistent with initAnalytic(type) which only reclassifies phases >=
    // 1.
    if (phaseNum >= 1 && getPhase(phaseNum).getType() == PhaseType.GAS) {
      getPhase(phaseNum).setType(PhaseType.OIL);
    }

    this.isInitialized = true;
  }

  /** {@inheritDoc} */
  @Override
  public final void initBeta() {
    for (int i = 0; i < numberOfPhases; i++) {
      this.beta[phaseIndex[i]] = getPhase(i).getNumberOfMolesInPhase() / getTotalNumberOfMoles();
    }
    if (!isInitialized && this.getSumBeta() < 1.0 - phaseFractionMinimumLimit
        || this.getSumBeta() > 1.0 + phaseFractionMinimumLimit) {
      // logger.warn("SystemThermo:initBeta - Sum of beta does not equal 1.0. " +
      // beta);
    }
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
    double[][][] gasnumericDfugdn = new double[2][getPhases()[0].getNumberOfComponents()][getPhases()[0]
        .getNumberOfComponents()];
    double[][][] liqnumericDfugdn = new double[2][getPhases()[0].getNumberOfComponents()][getPhases()[0]
        .getNumberOfComponents()];

    double dt = getTemperature() / 1e5;
    setTemperature(getTemperature() + dt);
    init(1);

    for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[0][i] = Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
      liqfug[0][i] = Math.log(getPhases()[1].getComponent(i).getFugacityCoefficient());
    }

    setTemperature(getTemperature() - 2 * dt);
    init(1);

    for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[1][i] = Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
      liqfug[1][i] = Math.log(getPhases()[1].getComponent(i).getFugacityCoefficient());
      gasnumericDfugdt[0][i] = (gasfug[0][i] - gasfug[1][i]) / (2 * dt);
      liqnumericDfugdt[0][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dt);
      phaseArray[0].getComponent(i).setdfugdt(gasnumericDfugdt[0][i]);
      phaseArray[1].getComponent(i).setdfugdt(liqnumericDfugdt[0][i]);
    }

    setTemperature(getTemperature() + dt);

    double dp = getPressure() / 1e5;
    setPressure(getPressure() + dp);
    init(1);

    for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[0][i] = Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
      liqfug[0][i] = Math.log(getPhases()[1].getComponent(i).getFugacityCoefficient());
    }

    setPressure(getPressure() - 2 * dp);
    init(1);

    for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[1][i] = Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
      liqfug[1][i] = Math.log(getPhases()[1].getComponent(i).getFugacityCoefficient());
      gasnumericDfugdp[0][i] = (gasfug[0][i] - gasfug[1][i]) / (2 * dp);
      liqnumericDfugdp[0][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dp);
      phaseArray[0].getComponent(i).setdfugdp(gasnumericDfugdp[0][i]);
      phaseArray[1].getComponent(i).setdfugdp(liqnumericDfugdp[0][i]);
    }

    setPressure(getPressure() + dp);
    init(1);

    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
        double dn = getPhase(phaseNum).getComponent(k).getNumberOfMolesInPhase() / 1.0e6;
        if (dn < 1e-12) {
          dn = 1e-12;
        }

        addComponent(k, dn, phaseNum);
        // initBeta();
        init_x_y();
        init(1);

        for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
          liqfug[0][i] = Math.log(getPhase(phaseNum).getComponent(i).getFugacityCoefficient());
        }

        addComponent(k, -2.0 * dn, phaseNum);
        // initBeta();
        init_x_y();
        init(1);

        for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
          // gasfug[1][i] =
          // Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
          liqfug[1][i] = Math.log(getPhase(phaseNum).getComponent(i).getFugacityCoefficient());
        }

        for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
          if (phaseNum == 0) {
            gasnumericDfugdn[0][k][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dn);
            phaseArray[0].getComponent(i).setdfugdn(k, gasnumericDfugdn[0][k][i]);
            phaseArray[0].getComponent(i).setdfugdx(k,
                gasnumericDfugdn[0][k][i] * phaseArray[0].getNumberOfMolesInPhase());
          }

          if (phaseNum == 1) {
            liqnumericDfugdn[0][k][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dn);
            phaseArray[1].getComponent(i).setdfugdn(k, liqnumericDfugdn[0][k][i]);
            phaseArray[1].getComponent(i).setdfugdx(k,
                liqnumericDfugdn[0][k][i] * phaseArray[1].getNumberOfMolesInPhase());
          }
        }

        addComponent(k, dn, phaseNum);
        // initBeta();
        init_x_y();
        init(1);
      }
    }
  }

  /**
   * initNumeric.
   *
   * @param type a int
   */
  public void initNumeric(int type) {
    initNumeric(type, 1);
  }

  /**
   * initNumeric.
   *
   * @param initType a int
   * @param phasen a int
   */
  public void initNumeric(int initType, int phasen) {
    if (initType < 2) {
      initAnalytic(initType);
    } else {
      double[][] gasfug = new double[2][getPhases()[0].getNumberOfComponents()];
      double[][] liqfug = new double[2][getPhases()[0].getNumberOfComponents()];

      double dt = getTemperature() / 1.0e6;
      setTemperature(getTemperature() + dt);
      init(1);

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        gasfug[0][i] = Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
        liqfug[0][i] = Math.log(getPhases()[1].getComponent(i).getFugacityCoefficient());
      }

      setTemperature(getTemperature() - 2 * dt);
      init(1);

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        gasfug[1][i] = Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
        liqfug[1][i] = Math.log(getPhases()[1].getComponent(i).getFugacityCoefficient());
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
        gasfug[0][i] = Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
        liqfug[0][i] = Math.log(getPhases()[1].getComponent(i).getFugacityCoefficient());
      }

      setPressure(getPressure() - 2 * dp);
      init(1);

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        gasfug[1][i] = Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
        liqfug[1][i] = Math.log(getPhases()[1].getComponent(i).getFugacityCoefficient());
      }

      for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
        getPhase(0).getComponent(i).setdfugdp((gasfug[0][i] - gasfug[1][i]) / (2 * dp));
        getPhase(1).getComponent(i).setdfugdp((liqfug[0][i] - liqfug[1][i]) / (2 * dp));
      }

      setPressure(getPressure() + dp);
      init(1);

      if (initType == 3) {
        for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
          for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
            double dn = getPhase(phaseNum).getComponent(k).getNumberOfMolesInPhase() / 1.0e6;

            addComponent(k, dn, phaseNum);
            // initBeta();
            init_x_y();
            init(1);

            for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
              liqfug[0][i] = Math.log(getPhase(phaseNum).getComponent(i).getFugacityCoefficient());
            }

            addComponent(k, -2.0 * dn, phaseNum);
            // initBeta();
            init_x_y();
            init(1);

            for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
              // gasfug[1][i] =
              // Math.log(getPhases()[0].getComponent(i).getFugacityCoefficient());
              liqfug[1][i] = Math.log(getPhase(phaseNum).getComponent(i).getFugacityCoefficient());
            }
            addComponent(k, dn, phaseNum);
            init_x_y();
            init(1);

            for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
              getPhase(phaseNum).getComponent(k).setdfugdn(i, (liqfug[0][i] - liqfug[1][i]) / (2 * dn));
              getPhase(phaseNum).getComponent(k).setdfugdx(i,
                  (liqfug[0][i] - liqfug[1][i]) / (2 * dn) * getPhase(phaseNum).getNumberOfMolesInPhase());
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
  public void initPhysicalProperties() {
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).initPhysicalProperties();
    }
    calcInterfaceProperties();
  }

  /** {@inheritDoc} */
  @Override
  public void initPhysicalProperties(PhysicalPropertyType ppt) {
    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(i).initPhysicalProperties(ppt);
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
  public final void initTotalNumberOfMoles(double change) {
    setTotalNumberOfMolesRaw(getTotalNumberOfMoles() + change);
    // System.out.println("total moles: " + totalNumberOfMoles);
    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        getPhase(j).getComponent(i).setNumberOfmoles(phaseArray[phaseIndex[0]].getComponent(i).getNumberOfmoles());
      }
    }
  }

  /**
   * Synchronize reaction-adjusted overall component amounts from a converged single-phase inventory.
   *
   * <p>
   * Chemical reactions conserve elements but can change the total number of species moles. This method updates the
   * scalar total and every phase object's overall composition without rescaling the converged reactive inventory.
   * Components outside the reaction set retain their exact overall amount; any single-phase round-off in their phase
   * amount is corrected before synchronization. Multiphase callers must instead use a coupled
   * reaction/phase-equilibrium algorithm that preserves feed elements.
   * </p>
   *
   * @param reactiveComponents components whose overall amounts are replaced by the converged reactive-phase inventory
   */
  public final void synchronizeSinglePhaseReactionComposition(ComponentInterface[] reactiveComponents) {
    if (numberOfPhases != 1) {
      throw new IllegalStateException("Single-phase reaction synchronization requires exactly one active phase");
    }
    if (reactiveComponents == null) {
      throw new IllegalArgumentException("Reactive components cannot be null");
    }
    boolean[] reactiveComponentNumbers = new boolean[numberOfComponents];
    for (ComponentInterface component : reactiveComponents) {
      if (component == null || component.getComponentNumber() < 0
          || component.getComponentNumber() >= numberOfComponents) {
        throw new IllegalArgumentException("Reactive component numbers must identify current system components");
      }
      reactiveComponentNumbers[component.getComponentNumber()] = true;
    }
    double[] componentMoles = new double[numberOfComponents];
    double totalMoles = 0.0;
    PhaseInterface reactivePhase = getPhase(0);
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      ComponentInterface component = reactivePhase.getComponent(componentIndex);
      if (reactiveComponentNumbers[componentIndex]) {
        componentMoles[componentIndex] = component.getNumberOfMolesInPhase();
      } else {
        componentMoles[componentIndex] = component.getNumberOfmoles();
        double phaseCorrection = componentMoles[componentIndex] - component.getNumberOfMolesInPhase();
        reactivePhase.addMolesChemReac(componentIndex, phaseCorrection, 0.0);
      }
      totalMoles += componentMoles[componentIndex];
    }
    if (!(totalMoles > 0.0) || !Double.isFinite(totalMoles)) {
      throw new IllegalStateException("Reaction-adjusted total moles must be finite and positive");
    }

    setTotalNumberOfMolesRaw(totalMoles);
    for (PhaseInterface phase : phaseArray) {
      if (phase == null) {
        continue;
      }
      for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
        phase.getComponent(componentIndex).setNumberOfmoles(componentMoles[componentIndex]);
        phase.getComponent(componentIndex).setz(componentMoles[componentIndex] / totalMoles);
      }
    }
    initBeta();
    init_x_y();
  }

  /** {@inheritDoc} */
  @Override
  public final boolean isChemicalSystem() {
    requireCurrentChemicalReactionState();
    return chemicalSystem;
  }

  /** {@inheritDoc} */
  @Override
  public final void isChemicalSystem(boolean temp) {
    chemicalSystem = temp;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isForcePhaseTypes() {
    return forcePhaseTypes;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInitialized() {
    return isInitialized;
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
  public boolean isMultiphaseWaxCheck() {
    return multiphaseWaxCheck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isNumericDerivatives() {
    return numericDerivatives;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isPhase(int i) {
    if (i < 0 || i > phaseArray.length) {
      return false;
    }
    return phaseArray[phaseIndex[i]] != null;
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
  public void orderByDensity() {
    boolean change = false;

    for (int i = 0; i < getNumberOfPhases(); i++) {
      if (getPhase(i).getPhysicalProperties() == null) {
        getPhase(i).initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      }
      getPhase(i).getPhysicalProperties().setPhase(getPhase(i));
    }

    do {
      change = false;
      for (int i = 1; i < getNumberOfPhases(); i++) {
        if (i == 4) {
          // Do not sort phase 5 and 6
          break;
        }

        try {
          if (change || getPhase(i).getPhysicalProperties() == null) {
            getPhase(i).initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
          }
        } catch (Exception ex) {
          logger.error(ex.getMessage());
        }

        // Get effective density for comparison
        // Solid phases (SOLID, ASPHALTENE, WAX, HYDRATE) should always be last
        // (densest)
        double density1 = getEffectiveDensityForOrdering(i - 1);
        double density2 = getEffectiveDensityForOrdering(i);

        if (density2 < density1) {
          int tempIndex1 = getPhaseIndex(i - 1);
          int tempIndex2 = getPhaseIndex(i);
          setPhaseIndex(i, tempIndex1);
          setPhaseIndex(i - 1, tempIndex2);
          change = true;
        }
      }
    } while (change);
  }

  /**
   * Get effective density for phase ordering. Solid-like phases (SOLID, ASPHALTENE, WAX, HYDRATE) return a very high
   * density to ensure they sort to the end.
   *
   * @param phaseNum phase number
   * @return effective density for ordering purposes
   */
  private double getEffectiveDensityForOrdering(int phaseNum) {
    PhaseType pt = getPhase(phaseNum).getType();
    // Solid-like phases should always be ordered last (highest density)
    if (pt == PhaseType.SOLID || pt == PhaseType.ASPHALTENE || pt == PhaseType.WAX || pt == PhaseType.HYDRATE
        || pt == PhaseType.SOLIDCOMPLEX) {
      // Return very high density to ensure solid phases sort to end
      // Use actual density if positive, otherwise use 2000 kg/m3 as fallback
      double actualDensity = getPhase(phaseNum).getPhysicalProperties().calcDensity();
      return actualDensity > 0 ? actualDensity : 2000.0;
    }
    return getPhase(phaseNum).getPhysicalProperties().calcDensity();
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface phaseToSystem(int phaseNumber) {
    SystemInterface newSystem = this.clone();

    for (int j = 0; j < getMaxNumberOfPhases(); j++) {
      for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
        newSystem.getPhase(j).getComponent(i)
            .setNumberOfmoles(getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase());
        newSystem.getPhase(j).getComponent(i)
            .setNumberOfMolesInPhase(getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase());
      }
    }

    ((SystemThermo) newSystem).setTotalNumberOfMolesRaw(getPhase(phaseNumber).getNumberOfMolesInPhase());

    newSystem.init(0);
    newSystem.setNumberOfPhases(1);
    newSystem.setPhaseType(0, getPhase(phaseNumber).getType()); // phaseType[phaseNumber]);
    newSystem.init(3);
    return newSystem;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface phaseToSystem(int phaseNumber1, int phaseNumber2) {
    SystemInterface newSystem = this.clone();

    for (int j = 0; j < getMaxNumberOfPhases(); j++) {
      for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
        newSystem.getPhases()[j].getComponent(i)
            .setNumberOfmoles(getPhase(phaseNumber1).getComponent(i).getNumberOfMolesInPhase()
                + getPhase(phaseNumber2).getComponent(i).getNumberOfMolesInPhase());
        newSystem.getPhases()[j].getComponent(i)
            .setNumberOfMolesInPhase(getPhase(phaseNumber1).getComponent(i).getNumberOfMolesInPhase()
                + getPhase(phaseNumber2).getComponent(i).getNumberOfMolesInPhase());
      }
    }

    ((SystemThermo) newSystem).setTotalNumberOfMolesRaw(
        getPhase(phaseNumber1).getNumberOfMolesInPhase() + getPhase(phaseNumber2).getNumberOfMolesInPhase());

    newSystem.init(0);

    newSystem.setNumberOfPhases(1);
    // newSystem.setPhaseType(0,
    // getPhase(phaseNumber1).getType()); //phaseType[phaseNumber]);
    newSystem.init(3);
    return newSystem;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface phaseToSystem(PhaseInterface newPhase) {
    // TODO: other phaseToSystem functions returns clones.
    for (int i = 0; i < newPhase.getNumberOfComponents(); i++) {
      newPhase.getComponent(i).setNumberOfmoles(newPhase.getComponent(i).getNumberOfMolesInPhase());
    }

    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      phaseArray[i] = newPhase.clone();
    }

    setTotalNumberOfMolesRaw(newPhase.getNumberOfMolesInPhase());
    this.init(0);
    setNumberOfPhases(1);
    setPhaseType(0, newPhase.getType());
    initBeta();
    init_x_y();
    this.init(3);
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
      logger.error("error....." + fluidName + " has no phase .... " + phaseName + " ..... returning phase number 0");
    }
    return phaseToSystem(0);
  }

  /** {@inheritDoc} */
  @Override
  public void readFluid(String fluidName) {
    this.fluidName = fluidName;
    try {
      neqsim.util.database.NeqSimFluidDataBase database = new neqsim.util.database.NeqSimFluidDataBase();
      java.sql.ResultSet dataSet = null;
      dataSet = database.getResultSet("SELECT * FROM " + fluidName);

      while (dataSet.next()) {
        String componentType = dataSet.getString("ComponentType");

        if (componentType.equalsIgnoreCase("normal")) {
          addComponent(dataSet.getString("ComponentName"), Double.parseDouble(dataSet.getString("Rate")));
        } else if (componentType.equalsIgnoreCase("TBP")) {
          addTBPfraction(dataSet.getString("ComponentName"), Double.parseDouble(dataSet.getString("Rate")),
              Double.parseDouble(dataSet.getString("MolarMass")) / 1000.0,
              Double.parseDouble(dataSet.getString("Density")));
        } else if (componentType.equalsIgnoreCase("plus")) {
          addPlusFraction(dataSet.getString("ComponentName"), Double.parseDouble(dataSet.getString("Rate")),
              Double.parseDouble(dataSet.getString("MolarMass")) / 1000.0,
              Double.parseDouble(dataSet.getString("Density")));
        } else {
          logger.error("component type need to be specified for ... " + dataSet.getString("ComponentName"));
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface readObject(int ID) {
    SystemThermo tempSystem = null;
    neqsim.util.database.NeqSimBlobDatabase database = new neqsim.util.database.NeqSimBlobDatabase();
    java.sql.Connection con = null;
    java.sql.PreparedStatement ps = null;
    java.sql.ResultSet rs = null;
    try {
      con = database.openConnection();
      String sqlStr = "SELECT FLUID FROM fluid_blobdb WHERE ID=" + Integer.toString(ID);
      ps = con.prepareStatement(sqlStr);
      rs = ps.executeQuery();

      if (rs.next()) {
        try (ObjectInputStream ins = new ObjectInputStream(new ByteArrayInputStream(rs.getBytes("FLUID")))) {
          tempSystem = (SystemThermo) ins.readObject();
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    } finally {
      try {
        if (rs != null) {
          rs.close();
        }
        if (ps != null) {
          ps.close();
        }
        if (con != null) {
          con.close();
        }
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
  public SystemInterface readObjectFromFile(String filePath, String fluidName) {
    SystemThermo tempSystem = null;
    try (ObjectInputStream objectinputstream = new ObjectInputStream(new FileInputStream(filePath))) {
      tempSystem = (SystemThermo) objectinputstream.readObject();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return tempSystem;
  }

  /**
   * Re-initialize phasetype, beta and phaseindex arrays, same initialization which is used in constructor.
   */
  public void reInitPhaseInformation() {
    reInitPhaseType();
    phaseType[4] = phaseType[3];
    phaseType[5] = phaseType[3];

    for (int i = 0; i < MAX_PHASES; i++) {
      beta[i] = 1.0;
    }

    phaseIndex = new int[] { 0, 1, 2, 3, 4, 5 };
  }

  /** {@inheritDoc} */
  @Override
  public void reInitPhaseType() {
    phaseType[0] = PhaseType.GAS;
    for (int i = 1; i < phaseType.length; i++) {
      phaseType[i] = PhaseType.LIQUID;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void removeComponent(String name) {
    name = ComponentInterface.getComponentNameFromAlias(name);
    markChemicalReactionStateStale();

    setTotalNumberOfMolesRaw(getTotalNumberOfMoles() - phaseArray[0].getComponent(name).getNumberOfmoles());
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      getPhase(i).removeComponent(name, getTotalNumberOfMoles(),
          getPhase(i).getComponent(name).getNumberOfMolesInPhase());
    }

    componentNames.remove(name);
    numberOfComponents--;
  }

  /** {@inheritDoc} */
  @Override
  public void removePhase(int specPhase) {
    setTotalNumberOfMolesRaw(getTotalNumberOfMoles() - getPhase(specPhase).getNumberOfMolesInPhase());

    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        getPhase(j).getComponent(i).setNumberOfmoles(getPhase(j).getComponent(i).getNumberOfmoles()
            - getPhase(specPhase).getComponent(i).getNumberOfMolesInPhase());
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
  public void renameComponent(String oldName, String newName) {
    componentNames.set(getPhase(0).getComponent(oldName).getComponentNumber(), newName);
    for (int i = 0; i < maxNumberOfPhases; i++) {
      getPhase(i).getComponent(oldName).setComponentName(newName);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void replacePhase(int repPhase, PhaseInterface newPhase) {
    for (int i = 0; i < 2; i++) {
      phaseArray[i] = newPhase.clone();
    }
    setTotalNumberOfMolesRaw(newPhase.getNumberOfMolesInPhase());
  }

  /** {@inheritDoc} */
  @Override
  public void reset() {
    init(0);
    for (int i = 0; i < numberOfComponents; i++) {
      double moles = getPhase(0).getComponent(i).getNumberOfMolesInPhase();
      if (Math.abs(moles) > 1e-100) {
        addComponent(getPhase(0).getComponent(i).getComponentName(), -moles);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void reset_x_y() {
    for (int j = 0; j < numberOfPhases; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        getPhase(j).getComponent(i).setx(phaseArray[phaseIndex[0]].getComponent(i).getz());
      }
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
  public void resetPhysicalProperties() {
    for (PhaseInterface tmpPhase : phaseArray) {
      if (tmpPhase != null) {
        tmpPhase.resetPhysicalProperties();
      }
    }
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

    neqsim.util.database.NeqSimBlobDatabase database = new neqsim.util.database.NeqSimBlobDatabase();
    java.sql.Connection con = null;
    java.sql.PreparedStatement ps = null;
    try {
      con = database.openConnection();

      ps = con.prepareStatement("REPLACE INTO fluid_blobdb (ID, FLUID) VALUES (?,?)");
      ps.setInt(1, ID);
      ps.setBlob(2, inpStream);

      ps.executeUpdate();
      /*
       * if (!text.isEmpty()) { ps = con.prepareStatement( "REPLACE INTO fluidinfo (ID, TEXT) VALUES (?,?)");
       * ps.setInt(1, ID); ps.setString(2, text); }
       *
       * ps.executeUpdate();
       */
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    } finally {
      try {
        if (ps != null) {
          ps.close();
        }
        if (con != null) {
          con.close();
        }
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
  public void saveToDataBase() {
    // java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM
    // SYSTEMREPORT"));
    // double molarmass = 0.0, stddens = 0.0, boilp = 0.0;
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      database.execute("delete FROM systemreport");
      int i = 0;
      for (; i < numberOfComponents; i++) {
        String sqlString = "'" + Integer.toString(i + 1) + "', '" + getPhase(0).getComponent(i).getName() + "', "
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

      sqlString = "'" + Integer.toString(i + 1) + "', 'MolarMass', " + "'kg/mol ', '" + Double.toString(getMolarMass())
          + "'";

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
  public void setAllComponentsInPhase(int phaseNum) {
    for (int k = 0; k < numberOfPhases; k++) {
      for (int i = 0; i < numberOfComponents; i++) {
        if (phaseNum != k) {
          // System.out.println("moles of comp: " + i + " " +
          // phaseArray[k].getComponent(i).getNumberOfMolesInPhase());
          phaseArray[phaseNum].addMoles(i, (phaseArray[k].getComponent(i).getNumberOfMolesInPhase() * (1.0 - 0.01)));
          phaseArray[k].addMoles(i, -(phaseArray[k].getComponent(i).getNumberOfMolesInPhase() * (1.0 - 0.01)));
          phaseArray[k].getComponent(i)
              .setx(phaseArray[k].getComponent(i).getNumberOfMolesInPhase() / phaseArray[k].getNumberOfMolesInPhase());
          // System.out.println("moles of comp after: " + i + " " +
          // phaseArray[k].getComponent(i).getNumberOfMolesInPhase());
        }
      }
    }
    initBeta();
    init(1);
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
  public final void setBeta(double b) {
    if (numberOfPhases > 2) {
      logger.warn("setBeta(double) called with {} phases - use setBeta(int, double) for multi-phase systems",
          numberOfPhases);
    }
    if (b < 0) {
      logger.warn("setBeta - Tried to set beta < 0: " + beta);
      b = phaseFractionMinimumLimit;
    }
    if (b > 1) {
      logger.warn("setBeta - Tried to set beta > 1: " + beta);
      b = 1.0 - phaseFractionMinimumLimit;
    }
    beta[0] = b;
    beta[1] = 1.0 - b;
  }

  /** {@inheritDoc} */
  @Override
  public final void setBeta(int phaseNum, double b) {
    if (b < 0) {
      logger.warn("setBeta - Tried to set beta < 0: " + beta);
      b = phaseFractionMinimumLimit;
    }
    if (b > 1) {
      logger.warn("setBeta - Tried to set beta > 1: " + beta);
      b = 1.0 - phaseFractionMinimumLimit;
    }
    beta[phaseIndex[phaseNum]] = b;
  }

  /** {@inheritDoc} */
  @Override
  public void setBmixType(int bmixType) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      ((PhaseEosInterface) getPhase(i)).getEosMixingRule().setBmixType(bmixType);
    }
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
      if (!getPhase(0).getComponent(i).isIsTBPfraction() && !getPhase(0).getComponent(i).isIsPlusFraction()) {
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
  public void setFluidInfo(String info) {
    this.fluidInfo = info;
  }

  /** {@inheritDoc} */
  @Override
  public void setFluidName(String fluidName) {
    this.fluidName = fluidName;
  }

  /** {@inheritDoc} */
  @Override
  public void setForcePhaseTypes(boolean forcePhaseTypes) {
    this.forcePhaseTypes = forcePhaseTypes;
  }

  /** {@inheritDoc} */
  @Override
  public boolean setHeavyTBPfractionAsPlusFraction() {
    int compNumber = 0;
    double molarMass = 0;
    boolean foundTBP = false;

    for (int i = 0; i < numberOfComponents; i++) {
      if (getPhase(0).getComponent(i).isIsTBPfraction() || getPhase(0).getComponent(i).isIsPlusFraction()) {
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
  public void setImplementedCompositionDeriativesofFugacity(boolean implementedCompositionDeriativesofFugacity) {
    this.implementedCompositionDeriativesofFugacity = implementedCompositionDeriativesofFugacity;
  }

  /** {@inheritDoc} */
  @Override
  public void setImplementedPressureDeriativesofFugacity(boolean implementedPressureDeriativesofFugacity) {
    this.implementedPressureDeriativesofFugacity = implementedPressureDeriativesofFugacity;
  }

  /** {@inheritDoc} */
  @Override
  public void setImplementedTemperatureDeriativesofFugacity(boolean implementedTemperatureDeriativesofFugacity) {
    this.implementedTemperatureDeriativesofFugacity = implementedTemperatureDeriativesofFugacity;
  }

  /**
   * setLastTBPasPlus.
   *
   * @return a boolean
   */
  public boolean setLastTBPasPlus() {
    neqsim.thermo.characterization.PlusCharacterize temp = new neqsim.thermo.characterization.PlusCharacterize(this);
    if (temp.hasPlusFraction()) {
      return false;
    } else {
      temp.setHeavyTBPtoPlus();
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void setMaxNumberOfPhases(int maxNumberOfPhases) {
    this.maxNumberOfPhases = maxNumberOfPhases;
  }

  /** {@inheritDoc} */
  @Override
  public final void setMixingRule(MixingRuleTypeInterface mr) {
    mixingRuleType = mr;
    if (numberOfPhases < 4) {
      resetPhysicalProperties();
    }
    for (int i = 0; i < maxNumberOfPhases; i++) {
      if (isPhase(i)) {
        getPhase(i).setMixingRule(mr);
        getPhase(i).initPhysicalProperties();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(String typename, String GEmodel) {
    setMixingRuleGEmodel(GEmodel);
    setMixingRule(typename);
  }

  /**
   * setMixingRuleGEmodel.
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
        tempModel = new SystemSrkSchwartzentruberEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("Electrolyte-ScRK-EOS")) {
        tempModel = new SystemFurstElectrolyteEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("GERG-water-EOS")) {
        tempModel = new SystemGERGwaterEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("CPAs-SRK-EOS")) {
        tempModel = new SystemSrkCPAs(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("CPAs-SRK-EOS-statoil")) {
        tempModel = new SystemSrkCPAstatoil(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("Electrolyte-CPA-EOS-statoil") || model.equals("Electrolyte-CPA-EOS")) {
        tempModel = new SystemElectrolyteCPAstatoil(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("UMR-PRU-EoS")) {
        tempModel = new SystemUMRPRUMCEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("PC-SAFT")) {
        tempModel = new SystemPCSAFT(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("GERG-2008-EoS")) {
        tempModel = new SystemGERG2004Eos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("SRK-TwuCoon-Statoil-EOS") || model.equals("SRK-TwuCoon-EOS")) {
        tempModel = new SystemSrkTwuCoonStatoilEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
      } else if (model.equals("SRK-TwuCoon-Param-EOS")) {
        tempModel = new SystemSrkTwuCoonParamEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
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
              getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getMolarMass(),
              getPhase(0).getComponent(i).getNormalLiquidDensity());
        } else if (getPhase(0).getComponent(i).isIsPlusFraction()) {
          tempModel.addPlusFraction(getPhase(0).getComponent(i).getName(),
              getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getMolarMass(),
              getPhase(0).getComponent(i).getNormalLiquidDensity());
        } else {
          tempModel.addComponent(getPhase(0).getComponent(i).getName(), getPhase(0).getComponent(i).getNumberOfmoles());
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
  public void setMolarComposition(double[] molefractions) {
    setMolarFractions(molefractions, "");
  }

  /** {@inheritDoc} */
  @Override
  public void setMolarCompositionOfPlusFluid(double[] molefractions) {
    setMolarFractions(molefractions, "PlusFluid");
  }

  /** {@inheritDoc} */
  @Override
  public void setMolarCompositionPlus(double[] molefractions) {
    setMolarFractions(molefractions, "Plus");
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
  public void setComponentFlowRates(double[] componentFlowRates, String unit) {
    if (componentFlowRates == null || componentFlowRates.length != getNumberOfComponents()) {
      throw new IllegalArgumentException("Component flow rates array must have length equal to number of components.");
    }

    double[] molarFlows = new double[componentFlowRates.length];
    String u = unit == null ? "" : unit.trim().toLowerCase();

    switch (u) {
    case "mol/sec":
    case "mole/sec":
      System.arraycopy(componentFlowRates, 0, molarFlows, 0, componentFlowRates.length);
      break;
    case "kmol/sec":
    case "kmole/sec":
      for (int i = 0; i < componentFlowRates.length; i++) {
        molarFlows[i] = componentFlowRates[i] * 1_000.0;
      }
      break;
    case "kmol/hr":
    case "kmole/hr":
      for (int i = 0; i < componentFlowRates.length; i++) {
        molarFlows[i] = componentFlowRates[i] * 1_000.0 / 3600.0;
      }
      break;
    case "mol/hr":
    case "mole/hr":
      for (int i = 0; i < componentFlowRates.length; i++) {
        molarFlows[i] = componentFlowRates[i] / 3600.0;
      }
      break;
    case "mol/min":
    case "mole/min":
      for (int i = 0; i < componentFlowRates.length; i++) {
        molarFlows[i] = componentFlowRates[i] / 60.0;
      }
      break;
    case "kg/hr":
      for (int i = 0; i < componentFlowRates.length; i++) {
        molarFlows[i] = componentFlowRates[i] / getComponent(i).getMolarMass() / 3600.0;
      }
      break;
    case "kg/sec":
      for (int i = 0; i < componentFlowRates.length; i++) {
        molarFlows[i] = componentFlowRates[i] / getComponent(i).getMolarMass();
      }
      break;
    case "kmol/day":
    case "kmole/day":
      for (int i = 0; i < componentFlowRates.length; i++) {
        molarFlows[i] = componentFlowRates[i] * 1_000.0 / 86400.0;
      }
      break;
    default:
      throw new IllegalArgumentException(
          "Unsupported unit: " + unit + ". Supported units: mol/sec, mole/sec, kmol/sec, "
              + "kmole/sec, kmol/hr, kmole/hr, mol/hr, mole/hr, mol/min, mole/min, "
              + "kg/hr, kg/sec, kmol/day, kmole/day");
    }

    setEmptyFluid();
    for (int compNumb = 0; compNumb < getNumberOfComponents(); compNumb++) {
      addComponent(compNumb, molarFlows[compNumb]);
    }
    for (int i = 0; i < getNumberOfPhases(); i++) {
      init(0, i);
    }
  }

  /**
   * Wrapper function for addComponent to set fluid type and specify mole fractions.
   *
   * @param molefractions Component mole fraction of each component.
   * @param type Type of fluid. Supports "PlusFluid", "Plus" and default.
   */
  private void setMolarFractions(double[] molefractions, String type) {
    double sum = 0;
    for (double value : molefractions) {
      sum += value;
    }
    if (sum <= 0) {
      throw new IllegalArgumentException("Mole fractions must sum to a positive value.");
    }

    double totalFlow = getTotalNumberOfMoles();
    if (totalFlow < 1e-100) {
      throw new RuntimeException(new InvalidInputException(this, "setMolarComposition", "totalFlow",
          "must be larger than 0 (1e-100) when setting molar composition"));
    }
    setEmptyFluid();

    switch (type) {
    case "PlusFluid":
      // TODO: really skip last component of molefraction?
      for (int compNumb = 0; compNumb < molefractions.length - 1; compNumb++) {
        addComponent(compNumb, totalFlow * molefractions[compNumb] / sum);
      }
      for (int j = 0; j < getCharacterization().getLumpingModel().getNumberOfLumpedComponents() - 1; j++) {
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
  public void setMultiPhaseCheck(boolean multiPhaseCheck) {
    if (getMaxNumberOfPhases() < 3) {
      if (multiPhaseCheck) {
        setMaxNumberOfPhases(3);
        if (phaseArray[1] != null) {
          phaseArray[2] = phaseArray[1].clone();
          phaseArray[2].resetMixingRule(phaseArray[0].getMixingRuleType());
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
  public boolean doEnhancedMultiPhaseCheck() {
    return enhancedMultiPhaseCheck;
  }

  /** {@inheritDoc} */
  @Override
  public void setEnhancedMultiPhaseCheck(boolean enhancedMultiPhaseCheck) {
    this.enhancedMultiPhaseCheck = enhancedMultiPhaseCheck;
    if (enhancedMultiPhaseCheck) {
      setMultiPhaseCheck(true);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setMultiphaseWaxCheck(boolean multiphaseWaxCheck) {
    this.multiphaseWaxCheck = multiphaseWaxCheck;
  }

  /** {@inheritDoc} */
  @Override
  public void setWaxModelType(String modelName) {
    this.waxModelTypeName = modelName;
    // If wax phase already exists, update it
    if (phaseArray[5] instanceof PhaseWax) {
      ((PhaseWax) phaseArray[5]).setWaxComponentModel(modelName);
    }
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
  public void setNumericDerivatives(boolean numericDerivatives) {
    this.numericDerivatives = numericDerivatives;
  }

  /** {@inheritDoc} */
  @Override
  public void setComponentCriticalParameters(int componentIndex, double tc, double pc, double acentricFactor) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      PhaseInterface phase = getPhase(i);
      if (phase != null && componentIndex < phase.getNumberOfComponents()) {
        ComponentInterface component = phase.getComponent(componentIndex);
        component.setTC(tc);
        component.setPC(pc);
        component.setAcentricFactor(acentricFactor);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setComponentCriticalParameters(String componentName, double tc, double pc, double acentricFactor) {
    setComponentCriticalParameters(getComponentIndex(componentName), tc, pc, acentricFactor);
  }

  /** {@inheritDoc} */
  @Override
  public void setComponentVolumeCorrection(int componentIndex, double volumeCorrection) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      PhaseInterface phase = getPhase(i);
      if (phase != null && componentIndex < phase.getNumberOfComponents()) {
        phase.getComponent(componentIndex).setVolumeCorrection(volumeCorrection);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setComponentVolumeCorrection(String componentName, double volumeCorrection) {
    setComponentVolumeCorrection(getComponentIndex(componentName), volumeCorrection);
  }

  /** {@inheritDoc} */
  @Override
  public void setBinaryInteractionParameter(int component1, int component2, double value) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      PhaseInterface phase = getPhase(i);
      if (phase != null && phase.getMixingRule() instanceof EosMixingRulesInterface) {
        ((EosMixingRulesInterface) phase.getMixingRule()).setBinaryInteractionParameter(component1, component2, value);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setBinaryInteractionParameter(String component1, String component2, double value) {
    setBinaryInteractionParameter(getComponentIndex(component1), getComponentIndex(component2), value);
  }

  /** {@inheritDoc} */
  @Override
  public final void setPC(double PC) {
    criticalPressure = PC;
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
  public final void setPhaseIndex(int index, int phaseIndex) {
    this.phaseIndex[index] = phaseIndex;
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
  public void setPhaseType(int phaseToChange, String phaseName) {
    // System.out.println("new phase type: cha " + pt);
    if (allowPhaseShift) {
      phaseType[phaseIndex[phaseToChange]] = PhaseType.byName(phaseName);
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
    // Following code was from public void setPhaseType(int phaseToChange, String
    // phaseTypeName) {
    /*
     * int newPhaseType = 0; if (phaseTypeName.equals("gas")) { newPhaseType = 1; } else if
     * (StateOfMatter.isLiquid(PhaseType.byDesc(phaseTypeName))) { newPhaseType = 0; } else { newPhaseType = 0; }
     */

    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (phaseType[i] == PhaseType.LIQUID) {
        phaseType[i] = PhaseType.GAS;
      } else {
        phaseType[i] = PhaseType.LIQUID;
      }
    }
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
    neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(newPressure, unit);
    setPressure(presConversion.getValue("bara"));
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

    for (int phaseNum = 0; phaseNum < numberOfPhases; phaseNum++) {
      for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
        getPhase(phaseNum).getComponent(k).setSolidCheck(solidPhaseCheck);
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

    for (int phaseNum = 0; phaseNum < numberOfPhases; phaseNum++) {
      try {
        if (getPhase(phaseNum) != null && getPhase(phaseNum).hasComponent(solidComponent)) {
          getPhase(phaseNum).getComponent(solidComponent).setSolidCheck(true);
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    setNumberOfPhases(oldphase);
  }

  /** {@inheritDoc} */
  @Override
  public void setStandard(String standardName) {
    if (standardName.equals("ISO1992")) {
      this.standard = new neqsim.standards.gasquality.Standard_ISO6976(this);
    } else if (standardName.equals("Draft_ISO18453")) {
      this.standard = new neqsim.standards.gasquality.Draft_ISO18453(this);
    } else {
      this.standard = new neqsim.standards.gasquality.Standard_ISO6976(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void setTC(double TC) {
    criticalTemperature = TC;
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
  public final void setTemperature(double newTemperature, int phaseNum) {
    getPhase(phaseIndex[phaseNum]).setTemperature(newTemperature);
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double newTemperature, String unit) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (unit.equals("K")) {
        // Direct setting as Kelvin
        getPhases()[i].setTemperature(newTemperature);
      } else if (unit.equals("C")) {
        // Convert Celsius to Kelvin
        getPhases()[i].setTemperature(newTemperature + 273.15);
      } else if (unit.equals("F")) {
        // Convert Fahrenheit to Kelvin
        getPhases()[i].setTemperature((newTemperature - 32) * 5.0 / 9.0 + 273.15);
      } else if (unit.equals("R")) {
        // Convert Rankine to Kelvin
        getPhases()[i].setTemperature(newTemperature * 5.0 / 9.0);
      } else {
        // Exception for unsupported units
        throw new RuntimeException("Unit not supported: " + unit);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setTotalFlowRate(double flowRate, String flowunit) {
    init(0);
    try {
      init(1);
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    double density = 0.0;
    if (flowunit.equals("Am3/hr") || flowunit.equals("Am3/min") || flowunit.equals("gallons/min")
        || flowunit.equals("Am3/sec") || flowunit.equals("Am3/day") || flowunit.equals("m3/hr")
        || flowunit.equals("m3/min") || flowunit.equals("m3/sec") || flowunit.equals("m3/day")) {
      initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    }

    density = getPhase(0).getDensity("kg/m3");
    if (flowunit.equals("idSm3/hr") || flowunit.equals("idSm3/min") || flowunit.equals("idSm3/sec")
        || flowunit.equals("idSm3/day") || flowunit.equals("gallons/min")) {
      density = getIdealLiquidDensity("kg/m3");
    }
    neqsim.util.unit.Unit unit = new neqsim.util.unit.RateUnit(flowRate, flowunit, getMolarMass(), density, 0);
    double SIval = unit.getSIvalue();
    double totalNumberOfMolesLocal = totalNumberOfMoles;
    for (int i = 0; i < numberOfComponents; i++) {
      if (flowRate < 1e-100) {
        setEmptyFluid();
      } else if (totalNumberOfMolesLocal > 1e-100) {
        // (SIval / totalNumberOfMolesLocal - 1) * ...
        double change = SIval / totalNumberOfMolesLocal * getPhase(0).getComponent(i).getNumberOfmoles()
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
  public void setTotalNumberOfMoles(double totalNumberOfMoles) {
    if (totalNumberOfMoles < 0) {
      /*
       * throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "setTotalNumberOfMoles",
       * "totalNumberOfMoles", "can not be less than 0."));
       */
      totalNumberOfMoles = 0;
    }
    rescaleComponentMoles(totalNumberOfMoles);
    this.totalNumberOfMoles = totalNumberOfMoles;
  }

  /**
   * Sets the scalar total-moles field only, leaving the per-component mole numbers untouched. For internal bookkeeping
   * where the caller has already updated the component moles.
   *
   * @param totalNumberOfMoles new total number of moles, negative values are clipped to zero
   */
  protected final void setTotalNumberOfMolesRaw(double totalNumberOfMoles) {
    this.totalNumberOfMoles = totalNumberOfMoles < 0 ? 0.0 : totalNumberOfMoles;
  }

  /**
   * Scales every component's mole numbers so they sum to {@code target}, keeping the composition unchanged. Component
   * moles are what {@code init(0)} divides by the total to get the overall mole fractions, so a total that disagrees
   * with the component moles makes z sum to something other than one and corrupts the next flash.
   *
   * @param target the new total number of moles
   */
  private void rescaleComponentMoles(double target) {
    if (phaseArray == null || numberOfComponents == 0 || phaseArray[phaseIndex[0]] == null) {
      return;
    }
    double current = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      current += getPhase(0).getComponent(i).getNumberOfmoles();
    }
    if (Math.abs(target - current) <= 1.0e-12 * Math.max(1.0, Math.abs(current))) {
      return;
    }

    double[] change = new double[numberOfComponents];
    if (current > 1.0e-100) {
      double factor = target / current;
      for (int i = 0; i < numberOfComponents; i++) {
        change[i] = factor - 1.0;
      }
      for (PhaseInterface tmpPhase : phaseArray) {
        if (tmpPhase == null) {
          continue;
        }
        for (int i = 0; i < numberOfComponents && i < tmpPhase.getNumberOfComponents(); i++) {
          ComponentInterface comp = tmpPhase.getComponent(i);
          tmpPhase.addMolesChemReac(i, comp.getNumberOfMolesInPhase() * change[i], comp.getNumberOfmoles() * change[i]);
        }
      }
      return;
    }

    // Empty fluid: distribute on the stored overall mole fractions, as init(initType > 0) does.
    double sumz = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      sumz += getPhase(0).getComponent(i).getz();
    }
    if (sumz <= 0.0) {
      return;
    }
    for (int i = 0; i < numberOfComponents; i++) {
      change[i] = target * getPhase(0).getComponent(i).getz() / sumz;
    }
    for (PhaseInterface tmpPhase : phaseArray) {
      if (tmpPhase == null) {
        continue;
      }
      for (int i = 0; i < numberOfComponents && i < tmpPhase.getNumberOfComponents(); i++) {
        tmpPhase.addMolesChemReac(i, change[i], change[i]);
      }
    }
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
  public void tuneModel(String model, double val, int phaseNum) {
    if (model.equals("viscosity")) {
      getPhase(phaseNum).getPhysicalProperties().getViscosityModel().tuneModel(val, getPhase(phaseNum).getTemperature(),
          getPhase(phaseNum).getPressure());
      for (int i = 0; i < getMaxNumberOfPhases(); i++) {
        for (int j = 0; j < numberOfPhases; j++) {
          getPhase(i).getComponent(j).setCriticalViscosity(getPhase(phaseNum).getComponent(j).getCriticalViscosity());
        }
      }
    }
    initPhysicalProperties();
  }

  /**
   * useTVasIndependentVariables.
   *
   * @return a boolean
   */
  public boolean useTVasIndependentVariables() {
    return useTVasIndependentVariables;
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

  /**
   * write.
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
    neqsim.datapresentation.filehandling.TextFile file = new neqsim.datapresentation.filehandling.TextFile();
    if (newfile) {
      file.newFile(filename);
    }
    file.setOutputFileName(filename);
    file.setValues(table);
    file.createFile();
  }

  /** {@inheritDoc} */
  @Override
  public double[] getKvector() {
    double[] K = new double[this.getNumberOfComponents()];
    for (int i = 0; i < this.getNumberOfComponents(); i++) {
      K[i] = this.getPhase(0).getComponent(i).getK();
    }
    return K;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getzvector() {
    double[] z = new double[this.getNumberOfComponents()];
    for (int i = 0; i < this.getNumberOfComponents(); i++) {
      z[i] = this.getPhase(0).getComponent(i).getz();
    }
    return z;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new neqsim.process.util.monitor.FluidResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toCompJson() {
    return new GsonBuilder().create().toJson(new neqsim.process.util.monitor.FluidComponentResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public void setForceSinglePhase(PhaseType phasetype) {
    this.init(0);
    this.setNumberOfPhases(1);
    this.setMaxNumberOfPhases(1);
    this.setForcePhaseTypes(true);
    this.setPhaseType(0, phasetype);
  }

  /** {@inheritDoc} */
  @Override
  public void setForceSinglePhase(String phasetype) {
    setForceSinglePhase(PhaseType.byName(phasetype));
  }

  private int getComponentIndex(String componentName) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);
    if (!getPhase(0).hasComponent(componentName)) {
      throw new RuntimeException("No component with name: " + componentName + " in system");
    }
    return getPhase(0).getComponent(componentName).getComponentNumber();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Sets the molar composition of components whose names contain the specified definition.
   */
  @Override
  public void setMolarCompositionOfNamedComponents(String nameDef, double[] molarComposition) {
    int place = 0;
    double[] comp = new double[getNumberOfComponents()];
    for (int i = 0; i < getNumberOfComponents(); i++) {
      comp[i] = 0.0;
      if (getPhase(0).getComponent(i).getName().contains(nameDef)) {
        comp[i] = molarComposition[place];
        place++;
      }
    }
    setMolarComposition(comp);
  }

  /**
   * setMixingRuleParametersForComponent.
   *
   * @param compName a {@link java.lang.String} object
   */
  public void setMixingRuleParametersForComponent(String compName) {
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      // getPhase(i).getMixingRule().setMixingRuleParametersForComponent(compName);
    }
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
    SystemThermo other = (SystemThermo) obj;
    if (numberOfComponents != other.numberOfComponents) {
      return false;
    }
    if (numberOfPhases != other.numberOfPhases) {
      return false;
    }
    if (Double.compare(totalNumberOfMoles, other.totalNumberOfMoles) != 0) {
      return false;
    }
    if (!fluidName.equals(other.fluidName)) {
      return false;
    }

    for (int i = 0; i < numberOfPhases; i++) {
      if (!getPhase(i).equals(other.getPhase(i))) {
        return false;
      }
    }
    return true;
  }
}
