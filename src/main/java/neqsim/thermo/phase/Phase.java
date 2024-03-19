/*
 * Phase.java
 *
 * Created on 8. april 2000, 23:38
 */

package neqsim.thermo.phase;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalProperties.PhysicalPropertyHandler;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.exception.InvalidInputException;

/**
 * Phase class.
 *
 * @author Even Solbraa
 */
public abstract class Phase implements PhaseInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Phase.class);

  public int numberOfComponents = 0;
  public ComponentInterface[] componentArray;
  public boolean mixingRuleDefined = false;

  public boolean calcMolarVolume = true;

  private boolean constantPhaseVolume = false;

  public int physicalPropertyType = 0;

  protected boolean useVolumeCorrection = true;
  public neqsim.physicalProperties.PhysicalPropertyHandler physicalPropertyHandler = null;
  protected double molarVolume = 1.0;
  protected double phaseVolume = 1.0;

  public boolean chemSyst = false;
  protected double diElectricConstant = 0;
  double Z = 1;
  public String thermoPropertyModelName = null;

  /**
   * Mole fraction of this phase of system.
   * <code>beta = numberOfMolesInPhase/numberOfMolesInSystem</code>. NB! numberOfMolesInSystem is
   * not known to the phase.
   */
  double beta = 1.0;
  /**
   * Number of moles in phase. <code>numberOfMolesInPhase = numberOfMolesInSystem*beta</code>. NB!
   * numberOfMolesInSystem is not known to the phase.
   */
  public double numberOfMolesInPhase = 0;

  private int initType = 0;
  int mixingRuleNumber = 0;
  double temperature = 0;
  double pressure = 0;

  protected PhaseInterface[] refPhase = null;
  protected PhaseType pt = PhaseType.GAS;

  /**
   * <p>
   * Constructor for Phase.
   * </p>
   */
  public Phase() {
    componentArray = new ComponentInterface[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  }

  /** {@inheritDoc} */
  @Override
  public Phase clone() {
    Phase clonedPhase = null;

    try {
      clonedPhase = (Phase) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    clonedPhase.componentArray = this.componentArray.clone();
    for (int i = 0; i < numberOfComponents; i++) {
      clonedPhase.componentArray[i] = this.componentArray[i].clone();
    }
    // System.out.println("cloned length: " + componentArray.length);
    if (physicalPropertyHandler != null) {
      clonedPhase.physicalPropertyHandler = this.physicalPropertyHandler.clone();
    }

    return clonedPhase;
  }

  /**
   * <p>
   * addcomponent. Increase number of components and add moles to phase.
   *
   * NB! Does not actually add component to componentarray.
   * </p>
   *
   * @param name Name of component to add.
   * @param moles Number of moles of component to add to phase.
   */
  public void addComponent(String name, double moles) {
    if (name == null) {
      // Will fail anyhow creating component with no name
      throw new RuntimeException(
          new InvalidInputException(this, "addcomponent", "name", "can not be null"));
    }

    if (this.hasComponent(name)) {
      // shall use addMoles/addMolesChemreac to adding/subtracting moles for component.
      throw new RuntimeException(
          "Component already exists in phase. Use addMoles or addMolesChemreac.");
    }

    if (moles < 0) {
      throw new RuntimeException(
          new InvalidInputException(this, "addComponent", "moles", "can not be negative"));
    }

    this.numberOfMolesInPhase += moles;
    this.numberOfComponents++;
  }

  /** {@inheritDoc} */
  @Override
  public void removeComponent(String name, double moles, double molesInPhase) {
    name = ComponentInterface.getComponentNameFromAlias(name);

    ArrayList<ComponentInterface> temp = new ArrayList<ComponentInterface>();

    try {
      for (int i = 0; i < numberOfComponents; i++) {
        if (!componentArray[i].getName().equals(name)) {
          temp.add(this.componentArray[i]);
        }
      }
      // logger.info("length " + temp.size());
      for (int i = 0; i < temp.size(); i++) {
        this.componentArray[i] = temp.get(i);
        this.getComponent(i).setComponentNumber(i);
      }
    } catch (Exception ex) {
      logger.error("not able to remove " + name, ex);
    }

    // componentArray = (ComponentInterface[])temp.toArray();
    componentArray[numberOfComponents - 1] = null;
    numberOfMolesInPhase -= molesInPhase;
    numberOfComponents--;
  }

  /** {@inheritDoc} */
  @Override
  public void setEmptyFluid() {
    numberOfMolesInPhase = 0.0;
    for (int i = 0; i < getNumberOfComponents(); i++) {
      this.getComponent(i).setNumberOfMolesInPhase(0.0);
      this.getComponent(i).setNumberOfmoles(0.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addMoles(int component, double dn) {
    numberOfMolesInPhase += dn;
    componentArray[component].addMoles(dn);
  }

  /** {@inheritDoc} */
  @Override
  public void addMolesChemReac(int component, double dn, double totdn) {
    if ((numberOfMolesInPhase + dn) / numberOfMolesInPhase < -1e-10) {
      String msg = "will lead to negative number of moles in phase." + (numberOfMolesInPhase + dn);
      neqsim.util.exception.InvalidInputException ex =
          new neqsim.util.exception.InvalidInputException(this, "addMolesChemReac", "dn", msg);
      throw new RuntimeException(ex);
    }
    numberOfMolesInPhase += dn;
    componentArray[component].addMolesChemReac(dn, totdn);
  }

  /** {@inheritDoc} */
  @Override
  public void setProperties(PhaseInterface phase) {
    setType(phase.getType());
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      this.getComponent(i).setProperties(phase.getComponent(i));
    }
    this.numberOfMolesInPhase = phase.getNumberOfMolesInPhase();
    this.numberOfComponents = phase.getNumberOfComponents();
    this.setBeta(phase.getBeta());
    this.setTemperature(phase.getTemperature());
    this.setPressure(phase.getPressure());
  }

  /** {@inheritDoc} */
  @Override
  public ComponentInterface[] getcomponentArray() {
    return componentArray;
  }

  /** {@inheritDoc} */
  @Override
  public double getAntoineVaporPressure(double temp) {
    double pres = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      pres += componentArray[i].getx() * componentArray[i].getAntoineVaporPressure(temp);
      // System.out.println(componentArray[i].getAntoineVaporPressure(temp));
    }
    return pres;
  }

  /** {@inheritDoc} */
  @Override
  public double getWtFrac(String componentName) {
    return getWtFrac(getComponent(componentName).getComponentNumber());
  }

  /** {@inheritDoc} */
  @Override
  public double getWtFrac(int component) {
    return getComponent(component).getMolarMass() * getComponent(component).getx()
        / this.getMolarMass();
  }

  /** {@inheritDoc} */
  @Override
  public double getPseudoCriticalTemperature() {
    double temp = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp += componentArray[i].getx() * componentArray[i].getTC();
    }
    return temp;
  }

  /** {@inheritDoc} */
  @Override
  public double getPseudoCriticalPressure() {
    double pres = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      pres += componentArray[i].getx() * componentArray[i].getPC();
    }
    return pres;
  }

  /** {@inheritDoc} */
  @Override
  public void normalize() {
    double sumx = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      sumx += componentArray[i].getx();
    }
    for (int i = 0; i < numberOfComponents; i++) {
      componentArray[i].setx(componentArray[i].getx() / sumx);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setMoleFractions(double[] x) {
    for (int i = 0; i < numberOfComponents; i++) {
      componentArray[i].setx(x[i]);
    }
    normalize();
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    return temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    return pressure;
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
  public int getInitType() {
    return initType;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getMolarComposition() {
    double[] comp = new double[getNumberOfComponents()];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      comp[compNumb] = getComponent(compNumb).getx();
    }
    return comp;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getComposition(String unit) {
    double[] comp = new double[getNumberOfComponents()];

    for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
      if (unit.equals("molefraction")) {
        comp[compNumb] = getComponent(compNumb).getx();
      }
      if (unit.equals("wtfraction")) {
        comp[compNumb] = getWtFrac(compNumb);
      }
      if (unit.equals("molespersec")) {
        comp[compNumb] = getWtFrac(compNumb);
      }
      if (unit.equals("volumefraction")) {
        comp[compNumb] = getComponent(compNumb).getVoli() / getVolume();
      }
    }
    return comp;
  }

  /** {@inheritDoc} */
  @Override
  public double getMixGibbsEnergy() {
    double gmix = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      gmix += getComponent(i).getx() * Math.log(getComponent(i).getx());
    }
    // todo: is this correct?
    return R * temperature * numberOfMolesInPhase * getExcessGibbsEnergy() * gmix;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy() {
    double GE = 0.0;
    if (refPhase == null) {
      initRefPhases(false);
    }
    for (int i = 0; i < numberOfComponents; i++) {
      GE += getComponent(i).getx() * Math.log(getActivityCoefficient(i));
    }
    return R * temperature * numberOfMolesInPhase * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergySymetric() {
    double GE = 0.0;
    if (refPhase == null) {
      initRefPhases(true);
    }
    for (int i = 0; i < numberOfComponents; i++) {
      GE += getComponent(i).getx() * Math.log(getActivityCoefficientSymetric(i));
    }
    return R * temperature * numberOfMolesInPhase * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getZ() {
    return Z;
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pres) {
    this.pressure = pres;
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temp) {
    this.temperature = temp;
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface getPhysicalProperties() {
    if (physicalPropertyHandler == null) {
      initPhysicalProperties();
      return physicalPropertyHandler.getPhysicalProperty(this);
    } else {
      return physicalPropertyHandler.getPhysicalProperty(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    if (totalNumberOfMoles <= 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "init",
          "totalNumberOfMoles", "must be larger than zero."));
    }

    this.beta = beta;
    numberOfMolesInPhase = beta * totalNumberOfMoles;
    if (this.pt != pt) {
      setType(pt);
      // setPhysicalProperties(physicalPropertyType);
    }
    this.setInitType(initType);
    this.numberOfComponents = numberOfComponents;
    for (int i = 0; i < numberOfComponents; i++) {
      componentArray[i].init(temperature, pressure, totalNumberOfMoles, beta, initType);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPhysicalProperties() {
    // System.out.println("Physical properties: Default model");
    setPhysicalProperties(physicalPropertyType);
    // physicalProperty = new
    // physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(this,0,0);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhysicalProperties(int type) {
    if (physicalPropertyHandler == null) {
      physicalPropertyHandler = new PhysicalPropertyHandler();
    }
    physicalPropertyHandler.setPhysicalProperties(this, type);
  }

  /** {@inheritDoc} */
  @Override
  public void resetPhysicalProperties() {
    physicalPropertyHandler = null;
  }

  /** {@inheritDoc} */
  @Override
  public void initPhysicalProperties() {
    if (physicalPropertyHandler == null) {
      physicalPropertyHandler = new PhysicalPropertyHandler();
    }

    if (physicalPropertyHandler.getPhysicalProperty(this) == null) {
      setPhysicalProperties(physicalPropertyType);
    }
    getPhysicalProperties().init(this);
  }

  /** {@inheritDoc} */
  @Override
  public void initPhysicalProperties(String type) {
    if (physicalPropertyHandler == null) {
      physicalPropertyHandler = new PhysicalPropertyHandler();
    }
    if (physicalPropertyHandler.getPhysicalProperty(this) == null) {
      setPhysicalProperties(physicalPropertyType);
    }
    getPhysicalProperties().setPhase(this);

    // if (physicalProperty == null || phaseTypeAtLastPhysPropUpdate != phaseType ||
    // !phaseTypeNameAtLastPhysPropUpdate.equals(phaseTypeName)) {
    // this.setPhysicalProperties();
    // }
    // physicalProperty.init(this, type);
    getPhysicalProperties().init(this, type);
  }

  /** {@inheritDoc} */
  @Override
  public double geta(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'geta'");
  }

  /** {@inheritDoc} */
  @Override
  public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcA'");
  }

  /**
   * <p>
   * calcA.
   * </p>
   *
   * @param comp a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcA(int comp, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcA'");
  }

  /** {@inheritDoc} */
  @Override
  public double calcAi(int comp, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcAi'");
  }

  /** {@inheritDoc} */
  @Override
  public double calcAiT(int comp, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcAiT'");
  }

  /** {@inheritDoc} */
  @Override
  public double calcAT(int comp, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcAT'");
  }

  /** {@inheritDoc} */
  @Override
  public double calcAij(int compNumb, int j, PhaseInterface phase, double temperature,
      double pressure, int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcAij'");

  }

  /** {@inheritDoc} */
  @Override
  public double getb(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'getb'");
  }

  /** {@inheritDoc} */
  @Override
  public double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcB'");
  }

  /** {@inheritDoc} */
  @Override
  public double getg() {
    throw new UnsupportedOperationException("Unimplemented method 'getg'");
  }

  /** {@inheritDoc} */
  @Override
  public double calcBij(int compNumb, int j, PhaseInterface phase, double temperature,
      double pressure, int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcBij'");
  }

  /** {@inheritDoc} */
  @Override
  public double calcBi(int comp, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    throw new UnsupportedOperationException("Unimplemented method 'calcBi'");
  }

  /** {@inheritDoc} */
  @Override
  public void setAttractiveTerm(int i) {
    for (int k = 0; k < numberOfComponents; k++) {
      componentArray[k].setAttractiveTerm(i);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarVolume() {
    return molarVolume;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarVolume(String unit) {
    double conversionFactor = 1.0;
    switch (unit) {
      case "m3/mol":
        conversionFactor = 1.0;
        break;
      case "litre/mol":
        conversionFactor = 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return getMolarMass() / getDensity("kg/m3") * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfComponents() {
    return numberOfComponents;
  }

  /** {@inheritDoc} */
  @Override
  public double getA() {
    throw new UnsupportedOperationException("Unimplemented method 'calcAij'");
  }

  /** {@inheritDoc} */
  @Override
  public double getB() {
    throw new UnsupportedOperationException("Unimplemented method 'getB'");
  }

  /**
   * <p>
   * getBi.
   * </p>
   *
   * @return a double
   */
  public double getBi() {
    throw new UnsupportedOperationException("Unimplemented method 'getBi'");
  }

  /** {@inheritDoc} */
  @Override
  public double getAT() {
    throw new UnsupportedOperationException("Unimplemented method 'getAT'");
  }

  /** {@inheritDoc} */
  @Override
  public double getATT() {
    throw new UnsupportedOperationException("Unimplemented method 'getATT'");
  }

  /**
   * <p>
   * getAiT.
   * </p>
   *
   * @return a double
   */
  public double getAiT() {
    throw new UnsupportedOperationException("Unimplemented method 'getAiT'");
  }

  /** {@inheritDoc} */
  @Override
  public PhaseInterface getPhase() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public double getNumberOfMolesInPhase() {
    return numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public ComponentInterface[] getComponents() {
    return componentArray;
  }

  /** {@inheritDoc} */
  @Override
  public void setComponentArray(ComponentInterface[] components) {
    this.componentArray = components;
  }

  /** {@inheritDoc} */
  @Override
  public double calcR() {
    double R = ThermodynamicConstantsInterface.R / getMolarMass();

    return R;
  }

  /** {@inheritDoc} */
  @Override
  public double Fn() {
    throw new UnsupportedOperationException("Unimplemented method 'Fn'");
  }

  /** {@inheritDoc} */
  @Override
  public double FT() {
    throw new UnsupportedOperationException("Unimplemented method 'FT'");
  }

  /** {@inheritDoc} */
  @Override
  public double FV() {
    throw new UnsupportedOperationException("Unimplemented method 'FV'");
  }

  /** {@inheritDoc} */
  @Override
  public double FD() {
    throw new UnsupportedOperationException("Unimplemented method 'FD'");
  }

  /** {@inheritDoc} */
  @Override
  public double FB() {
    throw new UnsupportedOperationException("Unimplemented method 'FB'");
  }

  /** {@inheritDoc} */
  @Override
  public double gb() {
    throw new UnsupportedOperationException("Unimplemented method 'gb'");
  }

  /** {@inheritDoc} */
  @Override
  public double fb() {
    throw new UnsupportedOperationException("Unimplemented method 'fb'");
  }

  /** {@inheritDoc} */
  @Override
  public double gV() {
    throw new UnsupportedOperationException("Unimplemented method 'gV'");
  }

  /** {@inheritDoc} */
  @Override
  public double fv() {
    throw new UnsupportedOperationException("Unimplemented method 'fv'");
  }

  /** {@inheritDoc} */
  @Override
  public double FnV() {
    throw new UnsupportedOperationException("Unimplemented method 'FnV'");
  }

  /** {@inheritDoc} */
  @Override
  public double FnB() {
    throw new UnsupportedOperationException("Unimplemented method 'FnB'");
  }

  /** {@inheritDoc} */
  @Override
  public double FTT() {
    throw new UnsupportedOperationException("Unimplemented method 'FTT'");
  }

  /** {@inheritDoc} */
  @Override
  public double FBT() {
    throw new UnsupportedOperationException("Unimplemented method 'FBT'");
  }

  /** {@inheritDoc} */
  @Override
  public double FDT() {
    throw new UnsupportedOperationException("Unimplemented method 'FDT'");
  }

  /** {@inheritDoc} */
  @Override
  public double FBV() {
    throw new UnsupportedOperationException("Unimplemented method 'FBV'");
  }

  /** {@inheritDoc} */
  @Override
  public double FBB() {
    throw new UnsupportedOperationException("Unimplemented method 'FBB'");
  }

  /** {@inheritDoc} */
  @Override
  public double FDV() {
    throw new UnsupportedOperationException("Unimplemented method 'FDV'");
  }

  /** {@inheritDoc} */
  @Override
  public double FBD() {
    throw new UnsupportedOperationException("Unimplemented method 'FBD'");
  }

  /** {@inheritDoc} */
  @Override
  public double FTV() {
    throw new UnsupportedOperationException("Unimplemented method 'FTV'");
  }

  /** {@inheritDoc} */
  @Override
  public double FVV() {
    throw new UnsupportedOperationException("Unimplemented method 'FVV'");
  }

  /** {@inheritDoc} */
  @Override
  public double gVV() {
    throw new UnsupportedOperationException("Unimplemented method 'gVV'");
  }

  /** {@inheritDoc} */
  @Override
  public double gBV() {
    throw new UnsupportedOperationException("Unimplemented method 'gBV'");
  }

  /** {@inheritDoc} */
  @Override
  public double gBB() {
    throw new UnsupportedOperationException("Unimplemented method 'gBB'");
  }

  /** {@inheritDoc} */
  @Override
  public double fVV() {
    throw new UnsupportedOperationException("Unimplemented method 'fVV'");
  }

  /** {@inheritDoc} */
  @Override
  public double fBV() {
    throw new UnsupportedOperationException("Unimplemented method 'fBV'");
  }

  /** {@inheritDoc} */
  @Override
  public double fBB() {
    throw new UnsupportedOperationException("Unimplemented method 'fBB'");
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    throw new UnsupportedOperationException("Unimplemented method 'dFdT'");
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    throw new UnsupportedOperationException("Unimplemented method 'dFdV'");
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    throw new UnsupportedOperationException("Unimplemented method 'dFdTdV'");
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    throw new UnsupportedOperationException("Unimplemented method 'dFdVdV'");
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    throw new UnsupportedOperationException("Unimplemented method 'dFdTdT'");
  }

  /** {@inheritDoc} */
  @Override
  public double getCpres() {
    throw new UnsupportedOperationException("Unimplemented method 'getCpres'");
  }

  /**
   * <p>
   * getCvres.
   * </p>
   *
   * @return a double
   */
  public double getCvres() {
    throw new UnsupportedOperationException("Unimplemented method 'getCvres'");
  }

  /** {@inheritDoc} */
  @Override
  public double getHresTP() {
    throw new UnsupportedOperationException("Unimplemented method 'getHresTP'");
  }

  /**
   * <p>
   * getHresdP.
   * </p>
   *
   * @return a double
   */
  public double getHresdP() {
    throw new UnsupportedOperationException("Unimplemented method 'getHresdP'");
  }

  /** {@inheritDoc} */
  @Override
  public double getGresTP() {
    throw new UnsupportedOperationException("Unimplemented method 'getGresTP'");
  }

  /**
   * <p>
   * getSresTV.
   * </p>
   *
   * @return a double
   */
  public double getSresTV() {
    throw new UnsupportedOperationException("Unimplemented method 'getSresTV'");
  }

  /** {@inheritDoc} */
  @Override
  public double getSresTP() {
    throw new UnsupportedOperationException("Unimplemented method 'getSresTP'");
  }

  /** {@inheritDoc} */
  @Override
  public double getCp0() {
    double tempVar = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      tempVar += componentArray[i].getx() * componentArray[i].getCp0(temperature);
    }
    return tempVar;
  }

  // Integral av Cp0 mhp T
  /**
   * <p>
   * getHID.
   * </p>
   *
   * @return a double
   */
  public double getHID() {
    double tempVar = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      tempVar += componentArray[i].getx() * componentArray[i].getHID(temperature);
    }
    return tempVar;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    // System.out.println("Cp res:" + this.getCpres() + " Cp0: " + getCp0());
    return getCp0() * numberOfMolesInPhase + this.getCpres();
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
        conversionFactor = 1.0 / getNumberOfMolesInPhase();
        break;
      case "J/kgK":
        conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    return refCp * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return getCp0() * numberOfMolesInPhase - R * numberOfMolesInPhase + getCvres();
  }

  /** {@inheritDoc} */
  @Override
  public double getCv(String unit) {
    double refCv = getCv(); // Cv in J/K
    double conversionFactor = 1.0;
    switch (unit) {
      case "J/K":
        conversionFactor = 1.0;
        break;
      case "J/molK":
        conversionFactor = 1.0 / getNumberOfMolesInPhase();
        break;
      case "J/kgK":
        conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    return refCv * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getKappa() {
    return getCp() / getCv();
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma() {
    return getCp() / getCv();
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    return getHID() * numberOfMolesInPhase + this.getHresTP();
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
        conversionFactor = 1.0 / getNumberOfMolesInPhase();
        break;
      case "J/kg":
        conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass();
        break;
      case "kJ/kg":
        conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass() / 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refEnthalpy * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpydP() {
    return this.getHresdP();
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpydT() {
    return getCp();
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfComponents(int numberOfComponents) {
    this.numberOfComponents = numberOfComponents;
  }

  /** {@inheritDoc} */
  @Override
  public final int getNumberOfMolecularComponents() {
    int mol = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        mol++;
      }
    }
    return mol;
  }

  /** {@inheritDoc} */
  @Override
  public final int getNumberOfIonicComponents() {
    int ion = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() != 0) {
        ion++;
      }
    }
    return ion;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    double tempVar = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      tempVar += componentArray[i].getx() * componentArray[i].getIdEntropy(temperature);
    }

    double tempVar2 = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getx() > 1e-100) {
        tempVar2 += -R * componentArray[i].getx() * Math.log(componentArray[i].getx());
      }
    }

    return tempVar * numberOfMolesInPhase
        - numberOfMolesInPhase * R * Math.log(pressure / referencePressure)
        + tempVar2 * numberOfMolesInPhase + this.getSresTP();
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
        conversionFactor = 1.0 / getNumberOfMolesInPhase();
        break;
      case "J/kgK":
        conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass() / 1000.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refEntropy * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropydP() {
    return getdPdTVn() / getdPdVTn();
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropydT() {
    return getCp() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosity() {
    return getPhysicalProperties().getViscosity();
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosity(String unit) {
    double refViscosity = getViscosity(); // viscosity in kg/msec
    double conversionFactor = 1.0;
    switch (unit) {
      case "Pas":
      case "kg/msec":
        conversionFactor = 1.0;
        break;
      case "cP":
        conversionFactor = 1.0e3;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refViscosity * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getThermalConductivity() {
    return getPhysicalProperties().getConductivity();
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
  public void initRefPhases(boolean onlyPure) {
    if (refPhase == null) {
      initRefPhases(onlyPure, "water");
    }
  }

  /**
   * <p>
   * initRefPhases.
   * </p>
   *
   * @param onlyPure a boolean
   * @param name a {@link String} object
   */
  public void initRefPhases(boolean onlyPure, String name) {
    refPhase = new PhaseInterface[numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      try {
        refPhase[i] = this.getClass().getDeclaredConstructor().newInstance();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      refPhase[i].setTemperature(temperature);
      refPhase[i].setPressure(pressure);
      if (getComponent(i).getReferenceStateType().equals("solvent") || onlyPure) {
        if (getComponent(i).isIsTBPfraction() || getComponent(i).isIsPlusFraction()) {
          refPhase[i].addComponent("default", 10.0, 10.0, 0);
          refPhase[i].getComponent(0).setMolarMass(this.getComponent(i).getMolarMass());
          refPhase[i].getComponent(0).setAcentricFactor(this.getComponent(i).getAcentricFactor());
          refPhase[i].getComponent(0).setTC(this.getComponent(i).getTC());
          refPhase[i].getComponent(0).setPC(this.getComponent(i).getPC());
          refPhase[i].getComponent(0).setComponentType("TBPfraction");
          refPhase[i].getComponent(0).setIsTBPfraction(true);
        } else {
          refPhase[i].addComponent(getComponent(i).getComponentName(), 10.0, 10.0, 0);
        }
        refPhase[i].setAttractiveTerm(this.getComponent(i).getAttractiveTermNumber());
        refPhase[i].setMixingRule(this.getMixingRuleNumber());
        refPhase[i].setType(this.getType());
        refPhase[i].init(refPhase[i].getNumberOfMolesInPhase(), 1, 0, this.getType(), 1.0);
      } else {
        // System.out.println("ref " + name);
        if (getComponent(i).isIsTBPfraction() || getComponent(i).isIsPlusFraction()) {
          refPhase[i].addComponent("default", 10.0, 10.0, 0);
          refPhase[i].getComponent(0).setMolarMass(this.getComponent(i).getMolarMass());
          refPhase[i].getComponent(0).setAcentricFactor(this.getComponent(i).getAcentricFactor());
          refPhase[i].getComponent(0).setTC(this.getComponent(i).getTC());
          refPhase[i].getComponent(0).setPC(this.getComponent(i).getPC());
          refPhase[i].getComponent(0).setComponentType("TBPfraction");
          refPhase[i].getComponent(0).setIsTBPfraction(true);
        } else {
          refPhase[i].addComponent(getComponent(i).getComponentName(), 1.0e-10, 1.0e-10, 0);
        }
        refPhase[i].addComponent(name, 10.0, 10.0, 1);
        refPhase[i].setAttractiveTerm(this.getComponent(i).getAttractiveTermNumber());
        refPhase[i].setMixingRule(this.getMixingRuleNumber());
        refPhase[i].init(refPhase[i].getNumberOfMolesInPhase(), 2, 0, this.getType(), 1.0);
      }
    }
  }

  /**
   * <p>
   * getLogPureComponentFugacity.
   * </p>
   *
   * @param k a int
   * @param pure a boolean
   * @return a double
   */
  public double getLogPureComponentFugacity(int k, boolean pure) {
    if (refPhase == null) {
      initRefPhases(pure);
    }
    refPhase[k].setTemperature(temperature);
    refPhase[k].setPressure(pressure);
    refPhase[k].init(refPhase[k].getNumberOfMolesInPhase(), 1, 1, this.getType(), 1.0);
    refPhase[k].getComponent(0).fugcoef(refPhase[k]);
    return refPhase[k].getComponent(0).getLogFugacityCoefficient();
  }

  /** {@inheritDoc} */
  @Override
  public double getLogPureComponentFugacity(int p) {
    return getLogPureComponentFugacity(p, false);
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentFugacity(int p) {
    return Math.exp(getLogPureComponentFugacity(p));
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentFugacity(int p, boolean pure) {
    return Math.exp(getLogPureComponentFugacity(p, pure));
  }

  /** {@inheritDoc} */
  @Override
  public double getLogInfiniteDiluteFugacity(int k, int p) {
    if (refPhase == null) {
      initRefPhases(false, getComponent(p).getName());
    }
    refPhase[k].setTemperature(temperature);
    refPhase[k].setPressure(pressure);
    refPhase[k].init(refPhase[k].getNumberOfMolesInPhase(), 2, 1, this.getType(), 1.0);
    refPhase[k].getComponent(0).fugcoef(refPhase[k]);
    return refPhase[k].getComponent(0).getLogFugacityCoefficient();
  }

  /** {@inheritDoc} */
  @Override
  public double getLogInfiniteDiluteFugacity(int k) {
    PhaseInterface dilphase = this.clone();
    dilphase.addMoles(k, -(1.0 - 1e-10) * dilphase.getComponent(k).getNumberOfMolesInPhase());
    dilphase.getComponent(k).setx(1e-10);
    dilphase.init(dilphase.getNumberOfMolesInPhase(), dilphase.getNumberOfComponents(), 1,
        dilphase.getType(), 1.0);
    dilphase.getComponent(k).fugcoef(dilphase);
    return dilphase.getComponent(k).getLogFugacityCoefficient();
  }

  /** {@inheritDoc} */
  @Override
  public double getInfiniteDiluteFugacity(int k, int p) {
    return Math.exp(getLogInfiniteDiluteFugacity(k, p));
  }

  /**
   * <p>
   * getInfiniteDiluteFugacity.
   * </p>
   *
   * @param k a int
   * @return a double
   */
  public double getInfiniteDiluteFugacity(int k) {
    return Math.exp(getLogInfiniteDiluteFugacity(k));
  }

  /** {@inheritDoc} */
  @Override
  public double getLogActivityCoefficient(int k, int p) {
    double fug = 0.0;
    double oldFug = getComponent(k).getLogFugacityCoefficient();
    if (getComponent(k).getReferenceStateType().equals("solvent")) {
      fug = getLogPureComponentFugacity(k);
    } else {
      fug = getLogInfiniteDiluteFugacity(k, p);
    }
    return oldFug - fug;
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficient(int k, int p) {
    double fug = 0.0;
    double oldFug = getComponent(k).getLogFugacityCoefficient();
    if (getComponent(k).getReferenceStateType().equals("solvent")) {
      fug = getLogPureComponentFugacity(k);
    } else {
      fug = getLogInfiniteDiluteFugacity(k, p);
    }
    return Math.exp(oldFug - fug);
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficient(int k) {
    double fug = 0.0;
    double oldFug = getComponent(k).getLogFugacityCoefficient();
    if (getComponent(k).getReferenceStateType().equals("solvent")) {
      fug = getLogPureComponentFugacity(k);
    } else {
      fug = getLogInfiniteDiluteFugacity(k);
    }
    return Math.exp(oldFug - fug);
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficientSymetric(int k) {
    if (refPhase == null) {
      initRefPhases(true);
    }
    double fug = 0.0;
    double oldFug = getComponent(k).getLogFugacityCoefficient();
    fug = getLogPureComponentFugacity(k);
    return Math.exp(oldFug - fug);
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficientUnSymetric(int k) {
    double fug = 0.0;
    double oldFug = getComponent(k).getLogFugacityCoefficient();
    fug = getLogInfiniteDiluteFugacity(k);
    return Math.exp(oldFug - fug);
  }

  /** {@inheritDoc} */
  @Override
  public double getMolalMeanIonicActivity(int comp1, int comp2) {
    int watNumb = 0;
    // double vminus = 0.0, vplus = 0.0;
    double ions = 0.0;
    for (int j = 0; j < this.numberOfComponents; j++) {
      if (getComponent(j).getIonicCharge() != 0) {
        ions += getComponent(j).getx();
      }
    }

    double val = ions / getComponent("water").getx();
    for (int j = 0; j < this.numberOfComponents; j++) {
      if (getComponent(j).getComponentName().equals("water")) {
        watNumb = j;
      }
    }

    double act1 = Math.pow(getActivityCoefficient(comp1, watNumb),
        Math.abs(getComponent(comp2).getIonicCharge()));
    double act2 = Math.pow(getActivityCoefficient(comp2, watNumb),
        Math.abs(getComponent(comp1).getIonicCharge()));

    return Math.pow(act1 * act2, 1.0 / (Math.abs(getComponent(comp1).getIonicCharge())
        + Math.abs(getComponent(comp2).getIonicCharge()))) * 1.0 / (1.0 + val);
  }

  /** {@inheritDoc} */
  @Override
  public double getOsmoticCoefficientOfWater() {
    int watNumb = 0;
    for (int j = 0; j < this.numberOfComponents; j++) {
      if (getComponent(j).getComponentName().equals("water")) {
        watNumb = j;
      }
    }
    return getOsmoticCoefficient(watNumb);
  }

  /** {@inheritDoc} */
  @Override
  public double getOsmoticCoefficient(int watNumb) {
    double oldFug = getComponent(watNumb).getFugacityCoefficient();
    double pureFug = getPureComponentFugacity(watNumb);
    double ions = 0.0;
    for (int j = 0; j < this.numberOfComponents; j++) {
      if (getComponent(j).getIonicCharge() != 0) {
        ions += getComponent(j).getx();
      }
    }
    return -Math.log(oldFug * getComponent(watNumb).getx() / pureFug) * getComponent(watNumb).getx()
        / ions;
  }

  // public double getOsmoticCoefficient(int watNumb, String refState){
  // if(refState.equals("molality")){
  // double oldFug = getComponent(watNumb).getFugacityCoefficient();
  // double pureFug = getPureComponentFugacity(watNumb);system.getPhase(i).
  // double ions=0.0;
  // for(int j=0;j<this.numberOfComponents;j++){
  // if(getComponent(j).getIonicCharge()!=0) ions +=
  // getComponent(j).getNumberOfMolesInPhase() /
  // getComponent(watNumb).getNumberOfMolesInPhase()/getComponent(watNumb).getMolarMass();
  // //*Math.abs(getComponent(j).getIonicCharge());
  // }
  // double val = - Math.log(oldFug*getComponent(watNumb).getx()/pureFug) *
  // 1.0/ions/getComponent(watNumb).getMolarMass();
  // return val;
  // }
  // else return getOsmoticCoefficient(watNumb);
  // }

  /** {@inheritDoc} */
  @Override
  public double getMeanIonicActivity(int comp1, int comp2) {
    double act1 = 0.0;
    double act2 = 0.0;
    int watNumb = 0;
    // double vminus = 0.0, vplus = 0.0;

    for (int j = 0; j < this.numberOfComponents; j++) {
      if (getComponent(j).getComponentName().equals("water")) {
        watNumb = j;
      }
    }

    act1 = Math.pow(getActivityCoefficient(comp1, watNumb),
        Math.abs(getComponent(comp2).getIonicCharge()));
    act2 = Math.pow(getActivityCoefficient(comp2, watNumb),
        Math.abs(getComponent(comp1).getIonicCharge()));
    return Math.pow(act1 * act2, 1.0 / (Math.abs(getComponent(comp1).getIonicCharge())
        + Math.abs(getComponent(comp2).getIonicCharge())));
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return getEnthalpy() - temperature * getEntropy();
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy() {
    return getEnthalpy() - pressure * getMolarVolume() * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getHelmholtzEnergy() {
    return getInternalEnergy() - temperature * getEntropy();
  }

  /** {@inheritDoc} */
  @Override
  public final double getMolarMass() {
    double tempVar = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      tempVar += componentArray[i].getx() * componentArray[i].getMolarMass();
    }
    return tempVar;
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
        throw new RuntimeException("unit not supported " + unit);
    }
    return JTcoef * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    throw new UnsupportedOperationException("Unimplemented method 'getJouleThomsonCoefficient'");
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    return 1.0 / getMolarVolume() * getMolarMass() * 1.0e5;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity(String unit) {
    double refDensity = getPhysicalProperties().getDensity(); // density in kg/m3
    double conversionFactor = 1.0;
    switch (unit) {
      case "kg/m3":
        conversionFactor = 1.0;
        break;
      case "mol/m3":
        conversionFactor = 1.0 / getMolarMass();
        break;
      case "lb/ft3":
        conversionFactor = 0.0624279606;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refDensity * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public final double getPhaseFraction() {
    return getBeta();
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdrho() {
    throw new UnsupportedOperationException("Unimplemented method 'getdPdrho'");
  }

  /** {@inheritDoc} */
  @Override
  public double getdrhodP() {
    throw new UnsupportedOperationException("Unimplemented method 'getdrhodP'");
  }

  /** {@inheritDoc} */
  @Override
  public double getdrhodT() {
    throw new UnsupportedOperationException("Unimplemented method 'getdrhodT'");
  }

  /** {@inheritDoc} */
  @Override
  public double getdrhodN() {
    throw new UnsupportedOperationException("Unimplemented method 'getdrhodN'");
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    mixingRuleNumber = type;
  }

  /**
   * <p>
   * calcDiElectricConstant.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcDiElectricConstant(double temperature) {
    double tempVar = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      tempVar += componentArray[i].getNumberOfMolesInPhase()
          * componentArray[i].getDiElectricConstant(temperature);
    }
    return tempVar / numberOfMolesInPhase;
  }

  /**
   * <p>
   * calcDiElectricConstantdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcDiElectricConstantdT(double temperature) {
    double tempVar = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      tempVar += componentArray[i].getNumberOfMolesInPhase()
          * componentArray[i].getDiElectricConstantdT(temperature);
    }
    return tempVar / numberOfMolesInPhase;
  }

  /**
   * <p>
   * calcDiElectricConstantdTdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double calcDiElectricConstantdTdT(double temperature) {
    double tempVar = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      tempVar += componentArray[i].getNumberOfMolesInPhase()
          * componentArray[i].getDiElectricConstantdTdT(temperature);
    }
    return tempVar / numberOfMolesInPhase;
  }

  /**
   * <p>
   * Getter for the field <code>diElectricConstant</code>.
   * </p>
   *
   * @return a double
   */
  public final double getDiElectricConstant() {
    return diElectricConstant;
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdTVn() {
    throw new UnsupportedOperationException("Unimplemented method 'getdPdTVn'");
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdVTn() {
    throw new UnsupportedOperationException("Unimplemented method 'getdPdVTn'");
  }

  /** {@inheritDoc} */
  @Override
  public double getpH() {
    return getpH_old();
    // System.out.println("ph - old " + getpH_old());
    // initPhysicalProperties();
    // for(int i = 0; i<numberOfComponents; i++) {
    // if(componentArray[i].getName().equals("H3O+")){
    // return
    // -Math.log10(componentArray[i].getNumberOfMolesInPhase()*getPhysicalProperties().getDensity()
    // / (numberOfMolesInPhase*getMolarMass())*1e-3);
    // }
    // }
    // System.out.println("no H3Oplus");
    // return 7.0;
  }

  /**
   * <p>
   * getpH_old.
   * </p>
   *
   * @return a double
   */
  public double getpH_old() {
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getName().equals("H3O+")) {
        // return -Math.log10(componentArray[i].getx()
        // * getActivityCoefficient(i));
        return -Math.log10(componentArray[i].getx() * getActivityCoefficient(i)
            / (0.01802 * neqsim.thermo.util.empiric.Water.waterDensity(temperature) / 1000.0));
      }
    }
    logger.info("no H3Oplus");
    return 7.0;
  }

  /** {@inheritDoc} */
  @Override
  public ComponentInterface getComponent(int i) {
    return componentArray[i];
  }

  /** {@inheritDoc} */
  @Override
  public ComponentInterface getComponent(String name) {
    try {
      for (int i = 0; i < numberOfComponents; i++) {
        if (componentArray[i].getName().equals(name)) {
          return componentArray[i];
        }
      }
      logger.error("could not find component " + name + ", returning null");
      throw new Exception("component not in fluid... " + name);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasComponent(String name) {
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public final int getMixingRuleNumber() {
    return mixingRuleNumber;
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.thermo.phase.PhaseInterface getRefPhase(int index) {
    if (refPhase == null) {
      initRefPhases(false);
    }
    return refPhase[index];
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.thermo.phase.PhaseInterface[] getRefPhase() {
    if (refPhase == null) {
      initRefPhases(false);
    }
    return refPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void setRefPhase(int index, neqsim.thermo.phase.PhaseInterface refPhase) {
    this.refPhase[index] = refPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void setRefPhase(neqsim.thermo.phase.PhaseInterface[] refPhase) {
    this.refPhase = refPhase;
  }

  /** {@inheritDoc} */
  @Override
  public final int getPhysicalPropertyType() {
    return physicalPropertyType;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhysicalPropertyType(int physicalPropertyType) {
    this.physicalPropertyType = physicalPropertyType;
  }

  /** {@inheritDoc} */
  @Override
  public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT,
      String[][] mixRule, double[][] intparam) {}

  /** {@inheritDoc} */
  @Override
  public final boolean useVolumeCorrection() {
    return useVolumeCorrection;
  }

  /** {@inheritDoc} */
  @Override
  public void useVolumeCorrection(boolean volcor) {
    useVolumeCorrection = volcor;
  }

  /** {@inheritDoc} */
  @Override
  public double getFugacity(int compNumb) {
    // System.out.println("fugcoef" +
    // this.getComponent(compNumb).getFugacityCoefficient());
    return this.getComponent(compNumb).getx() * this.getComponent(compNumb).getFugacityCoefficient()
        * pressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getFugacity(String compName) {
    return this.getComponent(compName).getx() * this.getComponent(compName).getFugacityCoefficient()
        * pressure;
  }

  /**
   * <p>
   * groupTBPfractions.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] groupTBPfractions() {
    double[] TPBfrac = new double[20];

    for (int i = 0; i < getNumberOfComponents(); i++) {
      double boilpoint = getComponent(i).getNormalBoilingPoint();

      if (boilpoint >= 331.0) {
        TPBfrac[19] += getComponent(i).getx();
      } else if (boilpoint >= 317.0) {
        TPBfrac[18] += getComponent(i).getx();
      } else if (boilpoint >= 303.0) {
        TPBfrac[17] += getComponent(i).getx();
      } else if (boilpoint >= 287.0) {
        TPBfrac[16] += getComponent(i).getx();
      } else if (boilpoint >= 271.1) {
        TPBfrac[15] += getComponent(i).getx();
      } else if (boilpoint >= 253.9) {
        TPBfrac[14] += getComponent(i).getx();
      } else if (boilpoint >= 235.9) {
        TPBfrac[13] += getComponent(i).getx();
      } else if (boilpoint >= 216.8) {
        TPBfrac[12] += getComponent(i).getx();
      } else if (boilpoint >= 196.4) {
        TPBfrac[11] += getComponent(i).getx();
      } else if (boilpoint >= 174.6) {
        TPBfrac[10] += getComponent(i).getx();
      } else if (boilpoint >= 151.3) {
        TPBfrac[9] += getComponent(i).getx();
      } else if (boilpoint >= 126.1) {
        TPBfrac[8] += getComponent(i).getx();
      } else if (boilpoint >= 98.9) {
        TPBfrac[7] += getComponent(i).getx();
      } else if (boilpoint >= 69.2) {
        TPBfrac[6] += getComponent(i).getx();
      }
    }
    return TPBfrac;
  }

  /** {@inheritDoc} */
  @Override
  public final double getBeta() {
    return this.beta;
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
    this.beta = b;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleGEModel(String name) {}

  /** {@inheritDoc} */
  @Override
  public boolean isMixingRuleDefined() {
    return mixingRuleDefined;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleDefined(boolean mixingRuleDefined) {
    this.mixingRuleDefined = mixingRuleDefined;
  }

  /** {@inheritDoc} */
  @Override
  public final PhaseType getType() {
    return this.pt;
  }

  /** {@inheritDoc} */
  @Override
  public final void setType(PhaseType pt) {
    this.pt = pt;
  }

  /** {@inheritDoc} */
  @Override
  public void setMolarVolume(double molarVolume) {
    this.molarVolume = molarVolume;
  }

  /** {@inheritDoc} */
  @Override
  public void calcMolarVolume(boolean test) {
    this.calcMolarVolume = test;
  }

  /** {@inheritDoc} */
  @Override
  public void setTotalVolume(double volume) {
    phaseVolume = volume;
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalVolume() {
    if (constantPhaseVolume) {
      return phaseVolume;
    }
    return getMolarVolume() * getNumberOfMolesInPhase();
  }

  /** {@inheritDoc} */
  @Override
  public double getVolume() {
    return getTotalVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double getVolume(String unit) {
    double conversionFactor = 1.0;
    switch (unit) {
      case "m3":
        conversionFactor = 1.0;
        break;
      case "litre":
        conversionFactor = 1000.0;
        break;
      default:
        break;
    }
    return conversionFactor * getVolume() / 1.0e5;
  }

  /** {@inheritDoc} */
  @Override
  public double getCorrectedVolume() {
    return getMolarMass() / getPhysicalProperties().getDensity() * getNumberOfMolesInPhase();
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasPlusFraction() {
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).isIsPlusFraction()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasTBPFraction() {
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).isIsTBPfraction()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConstantPhaseVolume() {
    return constantPhaseVolume;
  }

  /** {@inheritDoc} */
  @Override
  public void setConstantPhaseVolume(boolean constantPhaseVolume) {
    this.constantPhaseVolume = constantPhaseVolume;
  }

  /** {@inheritDoc} */
  @Override
  public double getMass() {
    return getMolarMass() * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    throw new UnsupportedOperationException("Unimplemented method 'getSoundSpeed'");
  }

  /** {@inheritDoc} */
  @Override
  public ComponentInterface getComponentWithIndex(int index) {
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIndex() == index) {
        return componentArray[i];
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public double getWtFraction(SystemInterface system) {
    return getBeta() * getMolarMass() / system.getMolarMass();
  }

  /** {@inheritDoc} */
  @Override
  public double getMoleFraction() {
    return beta;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitType(int initType) {
    this.initType = initType;
  }

  /** {@inheritDoc} */
  @Override
  public double getWtFractionOfWaxFormingComponents() {
    double wtFrac = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].isWaxFormer()) {
        wtFrac += componentArray[i].getx() * componentArray[i].getMolarMass() / getMolarMass();
      }
    }
    return wtFrac;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity_GERG2008() {
    neqsim.thermo.util.GERG.NeqSimGERG2008 test = new neqsim.thermo.util.GERG.NeqSimGERG2008(this);
    return test.getDensity();
  }

  /** {@inheritDoc} */
  @Override
  public double[] getProperties_GERG2008() {
    neqsim.thermo.util.GERG.NeqSimGERG2008 test = new neqsim.thermo.util.GERG.NeqSimGERG2008(this);
    return test.propertiesGERG();
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity_AGA8() {
    neqsim.thermo.util.GERG.NeqSimAGA8Detail test =
        new neqsim.thermo.util.GERG.NeqSimAGA8Detail(this);
    return test.getDensity();
  }

  /** {@inheritDoc} */
  @Override
  public double getFlowRate(String flowunit) {
    if (flowunit.equals("kg/sec")) {
      return numberOfMolesInPhase * getMolarMass();
    } else if (flowunit.equals("kg/min")) {
      return numberOfMolesInPhase * getMolarMass() * 60.0;
    } else if (flowunit.equals("kg/hr")) {
      return numberOfMolesInPhase * getMolarMass() * 3600.0;
    } else if (flowunit.equals("m3/sec")) {
      initPhysicalProperties("density");
      return numberOfMolesInPhase * getMolarMass() / getDensity("kg/m3");
    } else if (flowunit.equals("m3/min")) {
      initPhysicalProperties("density");
      return numberOfMolesInPhase * getMolarMass() / getDensity("kg/m3") * 60.0;
    } else if (flowunit.equals("m3/hr")) {
      initPhysicalProperties("density");
      return numberOfMolesInPhase * getMolarMass() / getDensity("kg/m3") * 3600.0;
    } else if (flowunit.equals("ft3/sec")) {
      initPhysicalProperties("density");
      return numberOfMolesInPhase * getMolarMass() / getDensity("kg/m3") * Math.pow(3.2808399, 3);
    } else if (flowunit.equals("mole/sec")) {
      return numberOfMolesInPhase;
    } else if (flowunit.equals("mole/min")) {
      return numberOfMolesInPhase * 60.0;
    } else if (flowunit.equals("mole/hr")) {
      return numberOfMolesInPhase * 3600.0;
    } else if (flowunit.equals("Sm3/sec")) {
      return numberOfMolesInPhase * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("Sm3/hr")) {
      return numberOfMolesInPhase * 3600.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("Sm3/day")) {
      return numberOfMolesInPhase * 3600.0 * 24.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm;
    } else if (flowunit.equals("MSm3/day")) {
      return numberOfMolesInPhase * 3600.0 * 24.0 * ThermodynamicConstantsInterface.R
          * ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm / 1.0e6;
    } else {
      throw new RuntimeException("failed.. unit: " + flowunit + " not supported");
    }
  }

  /**
   * <p>
   * Getter for the field <code>thermoPropertyModelName</code>.
   * </p>
   *
   * @return a {@link String} object
   */
  public String getThermoPropertyModelName() {
    return thermoPropertyModelName;
  }

  /** {@inheritDoc} */
  @Override
  public double getCompressibilityX() {
    return getTemperature() / getTotalVolume() * getdPdTVn() / getdPdVTn();
  }

  /** {@inheritDoc} */
  @Override
  public double getCompressibilityY() {
    return getPressure() / getTotalVolume() * 1.0 / getdPdVTn();
  }

  /** {@inheritDoc} */
  @Override
  public double getIsothermalCompressibility() {
    return -1.0 / getTotalVolume() * 1.0 / getdPdVTn();
  }

  /** {@inheritDoc} */
  @Override
  public double getIsobaricThermalExpansivity() {
    return getIsothermalCompressibility() * getdPdTVn();
  }
}
