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
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * @author Even Solbraa
 * @version
 */

abstract class Phase implements PhaseInterface {

    private static final long serialVersionUID = 1000;

    public ComponentInterface[] componentArray;
    public boolean mixingRuleDefined = false, calcMolarVolume = true;
    private boolean constantPhaseVolume = false;
    public int numberOfComponents = 0, physicalPropertyType = 0;
    protected boolean useVolumeCorrection = true;
    public neqsim.physicalProperties.PhysicalPropertyHandler physicalPropertyHandler = null;
    public double numberOfMolesInPhase = 0;
    protected double molarVolume = 1.0, phaseVolume = 1.0;
    public boolean chemSyst = false;
    protected double diElectricConstant = 0;
    double Z = 1;
    public String thermoPropertyModelName = null;
    double beta = 1.0;
    private int initType = 0;
    int mixingRuleNumber = 0;
    double temperature = 0, pressure = 0;
    protected PhaseInterface[] refPhase = null;
    int phaseType = 0;
    protected String phaseTypeName = "gas";
    static Logger logger = LogManager.getLogger(Phase.class);

    /**
     * Creates new Phase
     */
    public Phase() {
        componentArray = new ComponentInterface[MAX_NUMBER_OF_COMPONENTS];
    }

    public Phase(Phase phase) {}

    @Override
    public Phase clone() {
        Phase clonedPhase = null;

        try {
            clonedPhase = (Phase) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        clonedPhase.componentArray = this.componentArray.clone();
        for (int i = 0; i < numberOfComponents; i++) {
            clonedPhase.componentArray[i] = (ComponentInterface) this.componentArray[i].clone();
        }
        // System.out.println("cloed length: " + componentArray.length);
        if (physicalPropertyHandler != null) {
            clonedPhase.physicalPropertyHandler =
                    ((neqsim.physicalProperties.PhysicalPropertyHandler) this.physicalPropertyHandler
                            .clone());
        }

        return clonedPhase;
    }

    public void addcomponent(double moles) {
        numberOfMolesInPhase += moles;
        numberOfComponents++;
    }

    @Override
    public void removeComponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        ArrayList<ComponentInterface> temp = new ArrayList<ComponentInterface>();

        try {
            for (int i = 0; i < numberOfComponents; i++) {
                if (!componentArray[i].getName().equals(componentName)) {
                    temp.add(this.componentArray[i]);
                }
            }
            // logger.info("length " + temp.size());
            for (int i = 0; i < temp.size(); i++) {
                this.componentArray[i] = (ComponentInterface) temp.get(i);
                this.getComponent(i).setComponentNumber(i);
            }
        } catch (Exception e) {
            logger.error("not able to remove " + componentName);
        }

        // componentArray = (ComponentInterface[])temp.toArray();
        componentArray[numberOfComponents - 1] = null;
        numberOfMolesInPhase -= molesInPhase;
        numberOfComponents--;
    }

    @Override
    public void setEmptyFluid() {
        numberOfMolesInPhase = 0.0;
        for (int i = 0; i < getNumberOfComponents(); i++) {
            this.getComponent(i).setNumberOfMolesInPhase(0.0);
            this.getComponent(i).setNumberOfmoles(0.0);
        }
    }

    @Override
    public void addMoles(int component, double dn) {
        numberOfMolesInPhase += dn;
        componentArray[component].addMoles(dn);
    }

    @Override
    public void addMolesChemReac(int component, double dn) {
        numberOfMolesInPhase += dn;
        componentArray[component].addMolesChemReac(dn);
    }

    @Override
    public void addMolesChemReac(int component, double dn, double totdn) {
        numberOfMolesInPhase += dn;
        componentArray[component].addMolesChemReac(dn, totdn);
        if (numberOfMolesInPhase < 0.0 || getComponent(component).getNumberOfMolesInPhase() < 0.0) {
            logger.error("Negative number of moles in phase.");
            neqsim.util.exception.InvalidInputException e =
                    new neqsim.util.exception.InvalidInputException();
            throw new RuntimeException(e);
        }
        if (getComponent(component).getNumberOfMolesInPhase() < 0.0) {
            logger.error("Negative number of moles of component " + component);
            neqsim.util.exception.InvalidInputException e =
                    new neqsim.util.exception.InvalidInputException();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setProperties(PhaseInterface phase) {
        this.phaseType = phase.getPhaseType();
        for (int i = 0; i < phase.getNumberOfComponents(); i++) {
            this.getComponent(i).setProperties(phase.getComponent(i));
        }
        this.numberOfMolesInPhase = phase.getNumberOfMolesInPhase();
        this.numberOfComponents = phase.getNumberOfComponents();
        this.setBeta(phase.getBeta());
        this.setTemperature(phase.getTemperature());
        this.setPressure(phase.getPressure());
    }

    @Override
    public ComponentInterface[] getcomponentArray() {
        return componentArray;
    }

    @Override
    public double getAntoineVaporPressure(double temp) {
        double pres = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            pres += componentArray[i].getx() * componentArray[i].getAntoineVaporPressure(temp);
            // System.out.println(componentArray[i].getAntoineVaporPressure(temp));
        }
        return pres;
    }

    @Override
    public double getWtFrac(String componentName) {
        return getWtFrac(getComponent(componentName).getComponentNumber());
    }

    @Override
    public double getWtFrac(int component) {
        return getComponent(component).getMolarMass() * getComponent(component).getx()
                / this.getMolarMass();
    }

    @Override
    public double getPseudoCriticalTemperature() {
        double temp = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            temp += componentArray[i].getx() * componentArray[i].getTC();
        }
        return temp;
    }

    @Override
    public double getPseudoCriticalPressure() {
        double pres = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            pres += componentArray[i].getx() * componentArray[i].getPC();
        }
        return pres;
    }

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

    @Override
    public void setMoleFractions(double[] x) {
        for (int i = 0; i < numberOfComponents; i++) {
            componentArray[i].setx(x[i]);
        }
        normalize();
    }

    @Override
    public double getTemperature() {
        return temperature;
    }

    /**
     * method to return pressure of a phase
     *
     * @return pressure in unit bara
     */
    @Override
    public double getPressure() {
        return pressure;
    }

    /**
     * method to return pressure in a given unit
     *
     * @param unit The unit as a string. Supported units are bara, barg, Pa and MPa
     * @return pressure in specified unit
     */
    @Override
    public final double getPressure(String unit) {
        neqsim.util.unit.PressureUnit presConversion =
                new neqsim.util.unit.PressureUnit(getPressure(), "bara");
        return presConversion.getValue(unit);
    }

    @Override
    public int getInitType() {
        return initType;
    }

    /**
     * Returns the mole composition vector in unit mole fraction
     */
    @Override
    public double[] getMolarComposition() {
        double[] comp = new double[getNumberOfComponents()];

        for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
            comp[compNumb] = getComponent(compNumb).getx();
        }
        return comp;
    }

    /**
     * Returns the composition vector in unit molefraction/wtfraction/molespersec/volumefraction
     */
    @Override
    public double[] getComposition(String unit) {
        double[] comp = new double[getNumberOfComponents()];

        for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
            if (unit.equals("molefraction"))
                comp[compNumb] = getComponent(compNumb).getx();
            if (unit.equals("wtfraction"))
                comp[compNumb] = getWtFrac(compNumb);
            if (unit.equals("molespersec"))
                comp[compNumb] = getWtFrac(compNumb);
            if (unit.equals("volumefraction"))
                comp[compNumb] = getComponent(compNumb).getVoli() / getVolume();
        }
        return comp;
    }

    @Override
    public double getMixGibbsEnergy() {
        double gmix = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            gmix += getComponent(i).getx() * Math.log(getComponent(i).getx());
        }
        return getExessGibbsEnergy() + R * temperature * gmix * numberOfMolesInPhase;
    }

    @Override
    public double getExessGibbsEnergy() {
        double GE = 0.0;
        if (refPhase == null) {
            initRefPhases(false);
        }
        for (int i = 0; i < numberOfComponents; i++) {
            GE += getComponent(i).getx() * Math.log(getActivityCoefficient(i));
        }
        return R * temperature * numberOfMolesInPhase * GE;
    }

    @Override
    public double getExessGibbsEnergySymetric() {
        double GE = 0.0;
        if (refPhase == null) {
            initRefPhases(true);
        }
        for (int i = 0; i < numberOfComponents; i++) {
            GE += getComponent(i).getx() * Math.log(getActivityCoefficientSymetric(i));
        }
        return R * temperature * numberOfMolesInPhase * GE;
    }

    @Override
    public double getZ() {
        return Z;
    }

    @Override
    public void setPressure(double pres) {
        this.pressure = pres;
    }

    /**
     * method to set the temperature of a phase
     *
     * @param temp in unit Kelvin
     */
    @Override
    public void setTemperature(double temp) {
        this.temperature = temp;
    }

    @Override
    public neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface getPhysicalProperties() {
        if (physicalPropertyHandler == null) {
            initPhysicalProperties();
            return physicalPropertyHandler.getPhysicalProperty(this);
        } else {
            return physicalPropertyHandler.getPhysicalProperty(this);
        }
    }

    @Override
    public void init() {
        init(numberOfMolesInPhase / beta, numberOfComponents, initType, phaseType, beta);
    }

    @Override
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
            double beta) {

        this.beta = beta;
        numberOfMolesInPhase = beta * totalNumberOfMoles;
        if (this.phaseType != phase) {
            this.phaseType = phase;
            // setPhysicalProperties(physicalPropertyType);
        }
        this.setInitType(type);
        this.numberOfComponents = numberOfComponents;
        for (int i = 0; i < numberOfComponents; i++) {
            componentArray[i].init(temperature, pressure, totalNumberOfMoles, beta, type);
        }
    }

    @Override
    public void setPhysicalProperties() {
        // System.out.println("Physical properties: Default model");
        setPhysicalProperties(physicalPropertyType);
        // physicalProperty = new
        // physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(this,0,0);
    }

    /**
     * specify the type model for the physical properties you want to use. Type: Model 0
     * Orginal/default 1 Water 2 Glycol 3 Amine
     */
    @Override
    public void setPhysicalProperties(int type) {
        if (physicalPropertyHandler == null) {
            physicalPropertyHandler = new PhysicalPropertyHandler();
        }
        physicalPropertyHandler.setPhysicalProperties(this, type);

    }

    @Override
    public void resetPhysicalProperties() {
        physicalPropertyHandler = null;
    }

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
        //// }
        // physicalProperty.init(this, type);
        getPhysicalProperties().init(this, type);
    }

    @Override
    public double geta(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    @Override
    public double getb(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    @Override
    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    @Override
    public double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    @Override
    public double getg() {
        return 1;
    }

    public double calcA(int comp, PhaseInterface phase, double temperature, double pressure,
            int numbcomp) {
        return 1;
    }

    @Override
    public double calcAi(int comp, PhaseInterface phase, double temperature, double pressure,
            int numbcomp) {
        return 1;
    }

    @Override
    public double calcAiT(int comp, PhaseInterface phase, double temperature, double pressure,
            int numbcomp) {
        return 1;
    }

    @Override
    public double calcAT(int comp, PhaseInterface phase, double temperature, double pressure,
            int numbcomp) {

        return 1;
    }

    @Override
    public double calcAij(int compNumb, int j, PhaseInterface phase, double temperature,
            double pressure, int numbcomp) {
        return 0;
    }

    @Override
    public double calcBij(int compNumb, int j, PhaseInterface phase, double temperature,
            double pressure, int numbcomp) {
        return 0;
    }

    @Override
    public double calcBi(int comp, PhaseInterface phase, double temperature, double pressure,
            int numbcomp) {
        return 1;
    }

    @Override
    public void setAtractiveTerm(int i) {
        for (int k = 0; k < numberOfComponents; k++) {
            componentArray[k].setAtractiveTerm(i);
        }
    }

    /**
     * method to return molar volume of the phase note: without Peneloux volume correction
     *
     * @return molar volume volume in unit m3/mol*1e5
     */
    @Override
    public double getMolarVolume() {
        return molarVolume;
    }

    @Override
    public int getNumberOfComponents() {
        return numberOfComponents;
    }

    @Override
    public double getA() {
        return 0;
    }

    @Override
    public double getB() {
        return 0;
    }

    public double getBi() {
        return 0;
    }

    @Override
    public double getAT() {
        return 0;
    }

    @Override
    public double getATT() {
        return 0;
    }

    public double getAiT() {
        return 0;
    }

    @Override
    public PhaseInterface getPhase() {
        return this;
    }

    @Override
    public double getNumberOfMolesInPhase() {
        return numberOfMolesInPhase;
    }

    @Override
    public ComponentInterface[] getComponents() {
        return componentArray;
    }

    @Override
    public void setComponentArray(ComponentInterface[] components) {
        this.componentArray = components;
    }

    @Override
    public double calcR() {

        double R = 8.314 / getMolarMass();

        return R;
    }

    @Override
    public double Fn() {
        return 1;
    }

    @Override
    public double FT() {
        return 1;
    }

    @Override
    public double FV() {
        return 1;
    }

    @Override
    public double FD() {
        return 1;
    }

    @Override
    public double FB() {
        return 1;
    }

    @Override
    public double gb() {
        return 1;
    }

    @Override
    public double fb() {
        return 1;
    }

    @Override
    public double gV() {
        return 1;
    }

    @Override
    public double fv() {
        return 1;
    }

    @Override
    public double FnV() {
        return 1;
    }

    @Override
    public double FnB() {
        return 1;
    }

    @Override
    public double FTT() {
        return 1;
    }

    @Override
    public double FBT() {
        return 1;
    }

    @Override
    public double FDT() {
        return 1;
    }

    @Override
    public double FBV() {
        return 1;
    }

    @Override
    public double FBB() {
        return 1;
    }

    @Override
    public double FDV() {
        return 1;
    }

    @Override
    public double FBD() {
        return 1;
    }

    @Override
    public double FTV() {
        return 1;
    }

    @Override
    public double FVV() {
        return 1;
    }

    @Override
    public double gVV() {
        return 1;
    }

    @Override
    public double gBV() {
        return 1;
    }

    @Override
    public double gBB() {
        return 1;
    }

    @Override
    public double fVV() {
        return 1;
    }

    @Override
    public double fBV() {
        return 1;
    }

    @Override
    public double fBB() {
        return 1;
    }

    @Override
    public double dFdT() {
        return 1;
    }

    @Override
    public double dFdV() {
        return 1;
    }

    @Override
    public double dFdTdV() {
        return 1;
    }

    @Override
    public double dFdVdV() {
        return 1;
    }

    @Override
    public double dFdTdT() {
        return 1;
    }

    @Override
    public double getCpres() {
        return 1;
    }

    public double getCvres() {
        return 1;
    }

    @Override
    public double getHresTP() {
        logger.error("error Hres");
        return 0;
    }

    public double getHresdP() {
        logger.error(" getHresdP error Hres - not implemented?");
        return 0;
    }

    @Override
    public double getGresTP() {
        logger.error("error Gres");
        return 0;
    }

    public double getSresTV() {
        logger.error("error Hres");
        return 0;
    }

    @Override
    public double getSresTP() {
        return 0;
    }

    @Override
    public double getCp0() {
        double tempVar = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getx() * componentArray[i].getCp0(temperature);
        }
        return tempVar;
    }

    // Integral av Cp0 mhp T
    public double getHID() {
        double tempVar = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getx() * componentArray[i].getHID(temperature);
        }
        return tempVar;
    }

    /**
     * method to return specific heat capacity (Cp)
     *
     * @return Cp in unit J/K
     */
    @Override
    public double getCp() {
        // System.out.println("Cp res:" + this.getCpres() + " Cp0: " + getCp0());
        return getCp0() * numberOfMolesInPhase + this.getCpres();
    }

    /**
     * method to return specific heat capacity (Cp) in a given unit
     *
     * @param unit The unit as a string. Supported units are J/K, J/molK, J/kgK and kJ/kgK
     * @return Cp in specified unit
     */
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
        }
        return refCp * conversionFactor;
    }

    /**
     * method to return specific heat capacity (Cv)
     *
     * @return Cv in unit J/K
     */
    @Override
    public double getCv() {
        return getCp0() * numberOfMolesInPhase - R * numberOfMolesInPhase + getCvres();
    }

    /**
     * method to return specific heat capacity (Cv) in a given unit
     *
     * @param unit The unit as a string. Supported units are J/K, J/molK, J/kgK and kJ/kgK
     * @return Cv in specified unit
     */
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
        }
        return refCv * conversionFactor;
    }

    /**
     * method to return heat capacity ratio/adiabatic index/Poisson constant
     *
     * @return kappa
     */
    @Override
    public double getKappa() {
        return getCp() / getCv();
    }

    /**
     * method to return heat capacity ratio/adiabatic index/Poisson constant. The method calculates
     * it as Cp (real) /Cv (real)
     *
     * @return gamma
     */
    @Override
    public double getGamma() {
        return getCp() / getCv();
    }

    /**
     * method to return heat capacity ratio calculated as Cp/(Cp-R)
     *
     * @return kappa
     */
    @Override
    public double getGamma2() {
        double cp0 = getCp();
        return cp0 / (cp0 - ThermodynamicConstantsInterface.R * numberOfMolesInPhase);
    }

    /**
     * method to return enthalpy of a phase in unit Joule
     */
    @Override
    public double getEnthalpy() {
        return getHID() * numberOfMolesInPhase + this.getHresTP();
    }

    /**
     * method to return phase enthalpy in a given unit
     *
     * @param unit The unit as a string. Supported units are J, J/mol, J/kg and kJ/kg
     * @return enthalpy in specified unit
     */
    @Override
    public double getEnthalpy(String unit) {
        double refEnthalpy = getEnthalpy(); // enthalpy in J
        double conversionFactor = 1.0;
        switch (unit) {
            case "J":
                conversionFactor = 1.0;
                break;
            case "J/mol":
                conversionFactor = 1.0 / getNumberOfMolesInPhase();
                break;
            case "J/kg":
                conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass();
                break;
            case "kJ/kg":
                conversionFactor = 1.0 / getNumberOfMolesInPhase() / getMolarMass() / 1000.0;
                break;
        }
        return refEnthalpy * conversionFactor;
    }

    @Override
    public double getEnthalpydP() {
        return this.getHresdP();
    }

    @Override
    public double getEnthalpydT() {
        return getCp();
    }

    @Override
    public void setNumberOfComponents(int numberOfComponents) {
        this.numberOfComponents = numberOfComponents;
    }

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

    /**
     * method to return entropy of the phase
     */
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

    /**
     * method to return entropy of the phase
     *
     * @param unit The unit as a string. Supported units are J/K, J/moleK, J/kgK and kJ/kgK
     * @return entropy in specified unit
     * 
     */
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
        }
        return refEntropy * conversionFactor;
    }

    @Override
    public double getEntropydP() {
        return getdPdTVn() / getdPdVTn();
    }

    @Override
    public double getEntropydT() {
        return getCp() / temperature;
    }

    /**
     * method to return viscosity of the phase
     *
     * @return viscosity in unit kg/msec
     */
    @Override
    public double getViscosity() {
        return getPhysicalProperties().getViscosity();
    }

    /**
     * method to return viscosity og the phase in a given unit
     *
     * @param unit The unit as a string. Supported units are kg/msec, cP (centipoise)
     * @return viscosity in specified unit
     * 
     */
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
            default:
                throw new RuntimeException();
        }
        return refViscosity * conversionFactor;
    }

    /**
     * method to return conductivity of a phase
     *
     * @return conductivity in unit W/m*K
     */
    @Override
    public double getThermalConductivity() {
        return getPhysicalProperties().getConductivity();
    }

    /**
     * method to return conductivity of a phase
     *
     * @return conductivity in unit W/m*K
     * @deprecated use {@link #getThermalConductivity()} instead.
     */
    @Override
    @Deprecated
    public double getConductivity() {
        return getPhysicalProperties().getConductivity();
    }

    /**
     * method to return conductivity in a given unit
     *
     * @param unit The unit as a string. Supported units are W/mK, W/cmK
     * @return conductivity in specified unit
     */
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
                throw new RuntimeException();
        }
        return refConductivity * conversionFactor;
    }

    /**
     * method to return conductivity in a given unit
     *
     * @param unit The unit as a string. Supported units are W/mK, W/cmK
     * @return conductivity in specified unit
     * @deprecated use {@link #getThermalConductivity(String unit)} instead.
     */
    @Override
    @Deprecated
    public double getConductivity(String unit) {
        double refConductivity = getConductivity(); // conductivity in W/m*K
        double conversionFactor = 1.0;
        switch (unit) {
            case "W/mK":
                conversionFactor = 1.0;
                break;
            case "W/cmK":
                conversionFactor = 0.01;
                break;
            default:
                throw new RuntimeException();
        }
        return refConductivity * conversionFactor;
    }

    @Override
    public void initRefPhases(boolean onlyPure) {
        if (refPhase == null) {
            initRefPhases(onlyPure, "water");
        }
    }

    public void initRefPhases(boolean onlyPure, String name) {
        refPhase = new PhaseInterface[numberOfComponents];
        for (int i = 0; i < numberOfComponents; i++) {
            try {
                refPhase[i] = this.getClass().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error("err " + e.toString());
            }
            refPhase[i].setTemperature(temperature);
            refPhase[i].setPressure(pressure);
            if (getComponent(i).getReferenceStateType().equals("solvent") || onlyPure) {
                if (getComponent(i).isIsTBPfraction() || getComponent(i).isIsPlusFraction()) {
                    refPhase[i].addcomponent("default", 10.0, 10.0, 0);
                    refPhase[i].getComponent(0).setMolarMass(this.getComponent(i).getMolarMass());
                    refPhase[i].getComponent(0)
                            .setAcentricFactor(this.getComponent(i).getAcentricFactor());
                    refPhase[i].getComponent(0).setTC(this.getComponent(i).getTC());
                    refPhase[i].getComponent(0).setPC(this.getComponent(i).getPC());
                    refPhase[i].getComponent(0).setComponentType("TBPfraction");
                    refPhase[i].getComponent(0).setIsTBPfraction(true);
                } else {
                    refPhase[i].addcomponent(getComponent(i).getComponentName(), 10.0, 10.0, 0);
                }
                refPhase[i].setAtractiveTerm(this.getComponent(i).getAtractiveTermNumber());
                refPhase[i].setMixingRule(this.getMixingRuleNumber());
                refPhase[i].setPhaseType(this.getPhaseType());
                refPhase[i].init(refPhase[i].getNumberOfMolesInPhase(), 1, 0, this.getPhaseType(),
                        1.0);

            } else {
                // System.out.println("ref " + name);
                if (getComponent(i).isIsTBPfraction() || getComponent(i).isIsPlusFraction()) {
                    refPhase[i].addcomponent("default", 10.0, 10.0, 0);
                    refPhase[i].getComponent(0).setMolarMass(this.getComponent(i).getMolarMass());
                    refPhase[i].getComponent(0)
                            .setAcentricFactor(this.getComponent(i).getAcentricFactor());
                    refPhase[i].getComponent(0).setTC(this.getComponent(i).getTC());
                    refPhase[i].getComponent(0).setPC(this.getComponent(i).getPC());
                    refPhase[i].getComponent(0).setComponentType("TBPfraction");
                    refPhase[i].getComponent(0).setIsTBPfraction(true);
                } else {
                    refPhase[i].addcomponent(getComponent(i).getComponentName(), 1.0e-10, 1.0e-10,
                            0);
                }
                refPhase[i].addcomponent(name, 10.0, 10.0, 1);
                refPhase[i].setAtractiveTerm(this.getComponent(i).getAtractiveTermNumber());
                refPhase[i].setMixingRule(this.getMixingRuleNumber());
                refPhase[i].init(refPhase[i].getNumberOfMolesInPhase(), 2, 0, this.getPhaseType(),
                        1.0);
            }
        }
    }

    public double getLogPureComponentFugacity(int k, boolean pure) {
        if (refPhase == null) {
            initRefPhases(pure);
        }
        refPhase[k].setTemperature(temperature);
        refPhase[k].setPressure(pressure);
        refPhase[k].init(refPhase[k].getNumberOfMolesInPhase(), 1, 1, this.getPhaseType(), 1.0);
        refPhase[k].getComponent(0).fugcoef(refPhase[k]);
        return refPhase[k].getComponent(0).getLogFugasityCoeffisient();
    }

    @Override
    public double getLogPureComponentFugacity(int p) {
        return getLogPureComponentFugacity(p, false);
    }

    @Override
    public double getPureComponentFugacity(int p) {
        return Math.exp(getLogPureComponentFugacity(p));
    }

    @Override
    public double getPureComponentFugacity(int p, boolean pure) {
        return Math.exp(getLogPureComponentFugacity(p, pure));
    }

    @Override
    public double getLogInfiniteDiluteFugacity(int k, int p) {
        if (refPhase == null) {
            initRefPhases(false, getComponent(p).getName());
        }
        refPhase[k].setTemperature(temperature);
        refPhase[k].setPressure(pressure);
        refPhase[k].init(refPhase[k].getNumberOfMolesInPhase(), 2, 1, this.getPhaseType(), 1.0);
        refPhase[k].getComponent(0).fugcoef(refPhase[k]);
        return refPhase[k].getComponent(0).getLogFugasityCoeffisient();
    }

    @Override
    public double getLogInfiniteDiluteFugacity(int k) {
        PhaseInterface dilphase = (PhaseInterface) this.clone();
        dilphase.addMoles(k, -(1.0 - 1e-10) * dilphase.getComponent(k).getNumberOfMolesInPhase());
        dilphase.getComponent(k).setx(1e-10);
        dilphase.init(dilphase.getNumberOfMolesInPhase(), dilphase.getNumberOfComponents(), 1,
                dilphase.getPhaseType(), 1.0);
        dilphase.getComponent(k).fugcoef(dilphase);
        return dilphase.getComponent(k).getLogFugasityCoeffisient();
    }

    @Override
    public double getInfiniteDiluteFugacity(int k, int p) {
        return Math.exp(getLogInfiniteDiluteFugacity(k, p));
    }

    public double getInfiniteDiluteFugacity(int k) {
        return Math.exp(getLogInfiniteDiluteFugacity(k));
    }

    @Override
    public double getLogActivityCoefficient(int k, int p) {
        double fug = 0.0;
        double oldFug = getComponent(k).getLogFugasityCoeffisient();
        if (getComponent(k).getReferenceStateType().equals("solvent")) {
            fug = getLogPureComponentFugacity(k);
        } else {
            fug = getLogInfiniteDiluteFugacity(k, p);
        }
        return oldFug - fug;
    }

    @Override
    public double getActivityCoefficient(int k, int p) {
        double fug = 0.0;
        double oldFug = getComponent(k).getLogFugasityCoeffisient();
        if (getComponent(k).getReferenceStateType().equals("solvent")) {
            fug = getLogPureComponentFugacity(k);
        } else {
            fug = getLogInfiniteDiluteFugacity(k, p);
        }
        return Math.exp(oldFug - fug);
    }

    @Override
    public double getActivityCoefficient(int k) {
        double fug = 0.0;

        double oldFug = getComponent(k).getLogFugasityCoeffisient();
        if (getComponent(k).getReferenceStateType().equals("solvent")) {
            fug = getLogPureComponentFugacity(k);
        } else {
            fug = getLogInfiniteDiluteFugacity(k);
        }
        return Math.exp(oldFug - fug);
    }

    @Override
    public double getActivityCoefficientSymetric(int k) {
        if (refPhase == null) {
            initRefPhases(true);
        }
        double fug = 0.0;
        double oldFug = getComponent(k).getLogFugasityCoeffisient();
        fug = getLogPureComponentFugacity(k);
        return Math.exp(oldFug - fug);
    }

    @Override
    public double getActivityCoefficientUnSymetric(int k) {
        double fug = 0.0;
        double oldFug = getComponent(k).getLogFugasityCoeffisient();
        fug = getLogInfiniteDiluteFugacity(k);
        return Math.exp(oldFug - fug);
    }

    @Override
    public double getMolalMeanIonicActivity(int comp1, int comp2) {
        double act1 = 0.0;
        double act2 = 0.0;
        int watNumb = 0;
        double vminus = 0.0, vplus = 0.0;
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

        act1 = Math.pow(getActivityCoefficient(comp1, watNumb),
                Math.abs(getComponent(comp2).getIonicCharge()));
        act2 = Math.pow(getActivityCoefficient(comp2, watNumb),
                Math.abs(getComponent(comp1).getIonicCharge()));

        return Math
                .pow(act1 * act2,
                        1.0 / (Math.abs(getComponent(comp1).getIonicCharge())
                                + Math.abs(getComponent(comp2).getIonicCharge())))
                * 1.0 / (1.0 + val);
    }

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

    @Override
    public double getOsmoticCoefficient(int watNumb) {
        double oldFug = getComponent(watNumb).getFugasityCoeffisient();
        double pureFug = getPureComponentFugacity(watNumb);
        double ions = 0.0;
        for (int j = 0; j < this.numberOfComponents; j++) {
            if (getComponent(j).getIonicCharge() != 0) {
                ions += getComponent(j).getx();
            }
        }
        double val = -Math.log(oldFug * getComponent(watNumb).getx() / pureFug)
                * getComponent(watNumb).getx() / ions;
        return val;
    }

    // public double getOsmoticCoefficient(int watNumb, String refState){
    // if(refState.equals("molality")){
    // double oldFug = getComponent(watNumb).getFugasityCoeffisient();
    // double pureFug = getPureComponentFugacity(watNumb);system.getPhase(i).
    // double ions=0.0;
    // for(int j=0;j<this.numberOfComponents;j++){
    // if(getComponent(j).getIonicCharge()!=0) ions +=
    // getComponent(j).getNumberOfMolesInPhase()/getComponent(watNumb).getNumberOfMolesInPhase()/getComponent(watNumb).getMolarMass();//*Math.abs(getComponent(j).getIonicCharge());
    // }
    // double val = - Math.log(oldFug*getComponent(watNumb).getx()/pureFug) *
    // 1.0/ions/getComponent(watNumb).getMolarMass();
    // return val;
    // }
    // else return getOsmoticCoefficient(watNumb);
    // }
    @Override
    public double getMeanIonicActivity(int comp1, int comp2) {
        double act1 = 0.0;
        double act2 = 0.0;
        int watNumb = 0;
        double vminus = 0.0, vplus = 0.0;

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

    @Override
    public final int getPhaseType() {
        return phaseType;
    }

    @Override
    public double getGibbsEnergy() {
        return getEnthalpy() - temperature * getEntropy();
    }

    @Override
    public double getInternalEnergy() {
        return getEnthalpy() - pressure * getMolarVolume() * numberOfMolesInPhase;
    }

    @Override
    public double getHelmholtzEnergy() {
        return getInternalEnergy() - temperature * getEntropy();
    }

    /**
     * Returns the molar mass of the phase. Unit: kg/mol
     */
    @Override
    public final double getMolarMass() {
        double tempVar = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getx() * componentArray[i].getMolarMass();
        }
        return tempVar;
    }

    /**
     * method to get the Joule Thomson Coefficient of a phase note: implemented in phaseEos
     * 
     * @param unit The unit as a string. Supported units are K/bar, C/bar
     * @return Joule Thomson coefficient in given unit
     */
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
        }
        return JTcoef * conversionFactor;
    }

    /**
     * method to get the Joule Thomson Coefficient of a phase note: implemented in phaseEos
     *
     * @return Joule Thomson coefficient in K/bar
     */
    @Override
    public double getJouleThomsonCoefficient() {
        return 0;
    }

    /**
     * method to get density of a phase note: does not use Peneloux volume correction
     *
     * @return density with unit kg/m3
     */
    @Override
    public double getDensity() {
        return 1.0 / getMolarVolume() * getMolarMass() * 1.0e5;
    }

    /**
     * method to get density of a fluid note: with Peneloux volume correction
     *
     * @param unit The unit as a string. Supported units are kg/m3, mol/m3
     * @return density in specified unit
     */
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
            default:
                throw new RuntimeException(
                        "Could not create conversion factor because molar mass is NULL or 0");
        }
        return refDensity * conversionFactor;
    }

    @Override
    public final double getPhaseFraction() {
        return getBeta();
    }

    @Override
    public final double getBeta() {
        return this.beta;
    }

    @Override
    public double getdPdrho() {
        return 0;
    }

    @Override
    public double getdrhodP() {
        return 0.0;
    }

    @Override
    public double getdrhodT() {
        return 0;
    }

    @Override
    public double getdrhodN() {
        return 0;
    }

    @Override
    public void setMixingRule(int type) {
        mixingRuleNumber = type;
    }

    public double calcDiElectricConstant(double temperature) {
        double tempVar = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getNumberOfMolesInPhase()
                    * componentArray[i].getDiElectricConstant(temperature);
        }
        return tempVar / numberOfMolesInPhase;
    }

    public double calcDiElectricConstantdT(double temperature) {
        double tempVar = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getNumberOfMolesInPhase()
                    * componentArray[i].getDiElectricConstantdT(temperature);
        }
        return tempVar / numberOfMolesInPhase;
    }

    public double calcDiElectricConstantdTdT(double temperature) {
        double tempVar = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getNumberOfMolesInPhase()
                    * componentArray[i].getDiElectricConstantdTdT(temperature);
        }
        return tempVar / numberOfMolesInPhase;
    }

    public final double getDiElectricConstant() {
        return diElectricConstant;
    }

    @Override
    public double getdPdTVn() {
        return 0;
    }

    @Override
    public double getdPdVTn() {
        return 0;
    }

    @Override
    public double getpH() {
        return getpH_old();
        // System.out.println("ph - old " + getpH_old());
        // initPhysicalProperties();
        // for(int i = 0; i<numberOfComponents; i++) {
        // if(componentArray[i].getName().equals("H3O+")){
        // return -
        // MathLib.generalMath.GeneralMath.log10(componentArray[i].getNumberOfMolesInPhase()*getPhysicalProperties().getDensity()/(numberOfMolesInPhase*getMolarMass())*1e-3);
        // }
        // }
        // System.out.println("no H3Oplus");
        // return 7.0;
    }

    public double getpH_old() {
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getName().equals("H3O+")) {
                // return -neqsim.MathLib.generalMath.GeneralMath.log10(componentArray[i].getx()
                // * getActivityCoefficient(i));
                return -java.lang.Math.log10(componentArray[i].getx() * getActivityCoefficient(i)
                        / (0.01802 * neqsim.thermo.util.empiric.Water.waterDensity(temperature)
                                / 1000.0));
            }
        }
        logger.info("no H3Oplus");
        return 7.0;
    }

    @Override
    public ComponentInterface getComponent(int i) {
        return componentArray[i];
    }

    @Override
    public ComponentInterface getComponent(String name) {
        try {
            for (int i = 0; i < numberOfComponents; i++) {
                if (componentArray[i].getName().equals(name)) {
                    return componentArray[i];
                }
            }
            logger.error("could not find component... " + name + " ..returning null");
        } catch (Exception e) {
            logger.error("component not found.... " + name);
            logger.error("returning first component..." + componentArray[0].getName(), e);
        }
        return componentArray[0];
    }

    @Override
    public boolean hasComponent(String name) {
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Getter for property mixingRuleNumber.
     *
     * @return Value of property mixingRuleNumber.
     */
    @Override
    public final int getMixingRuleNumber() {
        return mixingRuleNumber;
    }

    /**
     * Indexed getter for property refPhase.
     *
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
     */
    @Override
    public neqsim.thermo.phase.PhaseInterface getRefPhase(int index) {
        if (refPhase == null) {
            initRefPhases(false);
        }
        return refPhase[index];
    }

    /**
     * Getter for property refPhase.
     *
     * @return Value of property refPhase.
     */
    @Override
    public neqsim.thermo.phase.PhaseInterface[] getRefPhase() {
        if (refPhase == null) {
            initRefPhases(false);
        }
        return refPhase;
    }

    /**
     * Indexed setter for property refPhase.
     *
     * @param index Index of the property.
     * @param refPhase New value of the property at <CODE>index</CODE>.
     */
    @Override
    public void setRefPhase(int index, neqsim.thermo.phase.PhaseInterface refPhase) {
        this.refPhase[index] = refPhase;
    }

    // public double getTotalVolume() {
    // return numberOfMolesInPhase * getMolarVolume();
    // }
    /**
     * Setter for property refPhase.
     *
     * @param refPhase New value of property refPhase.
     */
    @Override
    public void setRefPhase(neqsim.thermo.phase.PhaseInterface[] refPhase) {
        this.refPhase = refPhase;
    }

    /**
     * Getter for property physicalPropertyType.
     *
     * @return Value of property physicalPropertyType.
     */
    @Override
    public final int getPhysicalPropertyType() {
        return physicalPropertyType;
    }

    /**
     * Setter for property physicalPropertyType.
     *
     * @param physicalPropertyType New value of property physicalPropertyType.
     */
    @Override
    public void setPhysicalPropertyType(int physicalPropertyType) {
        this.physicalPropertyType = physicalPropertyType;
    }

    @Override
    public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT,
            String[][] mixRule, double[][] intparam) {}

    @Override
    public final boolean useVolumeCorrection() {
        return useVolumeCorrection;
    }

    @Override
    public void useVolumeCorrection(boolean volcor) {
        useVolumeCorrection = volcor;
    }

    @Override
    public double getFugacity(int compNumb) {
        // System.out.println("fugcoef" +
        // this.getComponent(compNumb).getFugasityCoefficient());
        return this.getComponent(compNumb).getx()
                * this.getComponent(compNumb).getFugasityCoefficient() * pressure;
    }

    @Override
    public double getFugacity(String compName) {
        return this.getComponent(compName).getx()
                * this.getComponent(compName).getFugasityCoefficient() * pressure;
    }

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
            } else {
            }
        }
        return TPBfrac;
    }

    /**
     * Setter for property beta.
     *
     * @param beta New value of property beta.
     */
    @Override
    public final void setBeta(double beta) {
        this.beta = beta;
    }

    @Override
    public void setMixingRuleGEModel(String name) {}


    /**
     * Getter for property phaseTypeName.
     *
     * @return Value of property phaseTypeName.
     */
    @Override
    public java.lang.String getPhaseTypeName() {
        return phaseTypeName;
    }

    /**
     * Setter for property phaseTypeName.
     *
     * @param phaseTypeName New value of property phaseTypeName.
     */
    @Override
    public void setPhaseTypeName(java.lang.String phaseTypeName) {
        this.phaseTypeName = phaseTypeName;
    }

    /**
     * Getter for property mixingRuleDefined.
     *
     * @return Value of property mixingRuleDefined.
     */
    @Override
    public boolean isMixingRuleDefined() {
        return mixingRuleDefined;
    }

    /**
     * Setter for property mixingRuleDefined.
     *
     * @param mixingRuleDefined New value of property mixingRuleDefined.
     */
    @Override
    public void setMixingRuleDefined(boolean mixingRuleDefined) {
        this.mixingRuleDefined = mixingRuleDefined;
    }

    /**
     * Setter for property phaseType.
     *
     * @param phaseType New value of property phaseType.
     */
    @Override
    public final void setPhaseType(int phaseType) {
        this.phaseType = phaseType;
    }

    @Override
    public void setMolarVolume(double molarVolume) {
        this.molarVolume = molarVolume;
    }

    @Override
    public void calcMolarVolume(boolean test) {
        this.calcMolarVolume = test;
    }

    @Override
    public void setTotalVolume(double volume) {
        phaseVolume = volume;
    }

    /**
     * method to return phase volume note: without Peneloux volume correction
     *
     * @return volume in unit m3*1e5
     */
    @Override
    public double getTotalVolume() {
        if (constantPhaseVolume) {
            return phaseVolume;
        }
        return getMolarVolume() * getNumberOfMolesInPhase();
    }

    /**
     * method to return phase volume note: without Peneloux volume correction
     *
     * @return volume in unit m3*1e5
     */
    @Override
    public double getVolume() {
        return getTotalVolume();
    }

    /**
     * method to return fluid volume
     *
     * @param unit The unit as a string. Supported units are m3, litre
     * @return volume in specified unit
     */
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
        }
        return conversionFactor * getVolume() / 1.0e5;
    }

    /**
     * method to return phase volume with Peneloux volume correction need to call
     * initPhysicalProperties() before this method is called
     *
     * @return volume in unit m3
     */
    @Override
    public double getCorrectedVolume() {
        return getMolarMass() / getPhysicalProperties().getDensity() * getNumberOfMolesInPhase();
    }

    @Override
    public boolean hasPlusFraction() {
        for (int i = 0; i < numberOfComponents; i++) {
            if (getComponent(i).isIsPlusFraction()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasTBPFraction() {
        for (int i = 0; i < numberOfComponents; i++) {
            if (getComponent(i).isIsTBPfraction()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConstantPhaseVolume() {
        return constantPhaseVolume;
    }

    @Override
    public void setConstantPhaseVolume(boolean constantPhaseVolume) {
        this.constantPhaseVolume = constantPhaseVolume;
    }

    @Override
    public double getMass() {
        return getMolarMass() * numberOfMolesInPhase;
    }

    /**
     * method to get the speed of sound of a phase note: implemented in phaseEos
     *
     * @return speed of sound in m/s
     */
    @Override
    public double getSoundSpeed() {
        return 0.0;
    }

    @Override
    public ComponentInterface getComponentWithIndex(int index) {
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getIndex() == index) {
                return componentArray[i];
            }
        }
        return null;
    }

    @Override
    public double getWtFraction(SystemInterface system) {
        return getBeta() * getMolarMass() / system.getMolarMass();
    }

    @Override
    public double getMoleFraction() {
        return beta;
    }

    /**
     * @param initType the initType to set
     */
    @Override
    public void setInitType(int initType) {
        this.initType = initType;
    }

    @Override
    public double getWtFractionOfWaxFormingComponents() {
        double wtFrac = 0.0;

        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].isWaxFormer()) {
                wtFrac += componentArray[i].getx() * componentArray[i].getMolarMass()
                        / getMolarMass();
            }
        }
        return wtFrac;
    }

    /**
     * method to get density of a phase using the GERG-2008 EoS
     *
     * @return density with unit kg/m3
     */
    @Override
    public double getDensity_GERG2008() {
        neqsim.thermo.util.GERG.NeqSimGERG2008 test =
                new neqsim.thermo.util.GERG.NeqSimGERG2008(this);
        return test.getDensity();
    }

    /**
     * method to get GERG properties of a phase using the GERG-2008 EoS
     *
     * @return double array [Pressure [kPa], Compressibility factor, d(P)/d(rho) [kPa/(mol/l), ..]
     */
    @Override
    public double[] getProperties_GERG2008() {
        neqsim.thermo.util.GERG.NeqSimGERG2008 test =
                new neqsim.thermo.util.GERG.NeqSimGERG2008(this);
        return test.propertiesGERG();
    }

    /**
     * method to get density of a phase using the AGA8-Detail EoS
     *
     * @return density with unit kg/m3
     */
    @Override
    public double getDensity_AGA8() {
        neqsim.thermo.util.GERG.NeqSimAGA8Detail test =
                new neqsim.thermo.util.GERG.NeqSimAGA8Detail(this);
        return test.getDensity();
    }

    /**
     * method to return flow rate of phase
     *
     * @param flowunit The unit as a string. Supported units are kg/sec, kg/min, m3/sec, m3/min,
     *        m3/hr, mole/sec, mole/min, mole/hr
     *
     * @return flow rate in specified unit
     */
    @Override
    public double getFlowRate(String flowunit) {
        if (flowunit.equals("kg/sec")) {
            return numberOfMolesInPhase * getMolarMass();
        } else if (flowunit.equals("kg/min")) {
            return numberOfMolesInPhase * getMolarMass() * 60.0;
        } else if (flowunit.equals("kg/hr")) {
            return numberOfMolesInPhase * getMolarMass() * 3600.0;
        } else if (flowunit.equals("m3/hr")) {
            return getVolume() / 1.0e5 * 3600.0;
        } else if (flowunit.equals("m3/min")) {
            return getVolume() / 1.0e5 * 60.0;
        } else if (flowunit.equals("m3/sec")) {
            return getVolume() / 1.0e5;
        } else if (flowunit.equals("mole/sec")) {
            return numberOfMolesInPhase;
        } else if (flowunit.equals("mole/min")) {
            return numberOfMolesInPhase * 60.0;
        } else if (flowunit.equals("mole/hr")) {
            return numberOfMolesInPhase * 3600.0;
        } else {
            throw new RuntimeException("failed.. unit: " + flowunit + " not suported");
        }
    }

    public String getThermoPropertyModelName() {
        return thermoPropertyModelName;
    }
}
