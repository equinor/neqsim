/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 /*
 * Phase.java
 *
 * Created on 8. april 2000, 23:38
 */
package neqsim.thermo.phase;

import neqsim.thermo.ThermodynamicConstantsInterface;
import static neqsim.thermo.ThermodynamicConstantsInterface.MAX_NUMBER_OF_COMPONENTS;
import static neqsim.thermo.ThermodynamicConstantsInterface.R;
import static neqsim.thermo.ThermodynamicConstantsInterface.referencePressure;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version
 */
abstract class Phase extends Object implements PhaseInterface, ThermodynamicConstantsInterface, Cloneable, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    public ComponentInterface[] componentArray;
    public boolean mixingRuleDefined = false, calcMolarVolume = true;
    private boolean constantPhaseVolume = false;
    public int numberOfComponents = 0, physicalPropertyType = 0;
    protected boolean useVolumeCorrection = true;
    public neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface physicalProperty = null;
    public double numberOfMolesInPhase = 0;
    protected double molarVolume = 1.0, phaseVolume = 1.0;
    public boolean chemSyst = false;
    protected double diElectricConstant = 0;
    double Z = 1;
    double beta = 1.0;
    private int initType = 0;
    int mixingRuleNumber = 0;
    double temperature = 0, pressure = 0;
    transient PhaseInterface[] refPhase = null;
    int phaseType = 0, phaseTypeAtLastPhysPropUpdate = 0;
    protected String phaseTypeName = "gas";
    String phaseTypeNameAtLastPhysPropUpdate = "";
    static Logger logger = Logger.getLogger(Phase.class);

    // Class methods
    /**
     * Creates new Phase
     */
    public Phase() {
        componentArray = new ComponentInterface[MAX_NUMBER_OF_COMPONENTS];
    }

    public Phase(Phase phase) {
    }

    public Object clone() {

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
        //System.out.println("cloed length: " + componentArray.length);
        if (this.physicalProperty != null) {
            clonedPhase.physicalProperty = (neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface) this.physicalProperty.clone();
        }
        return clonedPhase;
    }

    public void addcomponent(double moles) {
        numberOfMolesInPhase += moles;
        numberOfComponents++;
    }

    public void removeComponent(String componentName, double moles, double molesInPhase, int compNumber) {
        java.util.ArrayList temp = new java.util.ArrayList();

        try {
            for (int i = 0; i < numberOfComponents; i++) {
                if (!componentArray[i].getName().equals(componentName)) {
                    temp.add(this.componentArray[i]);
                }
            }
            logger.info("length " + temp.size());
            for (int i = 0; i < temp.size(); i++) {
                this.componentArray[i] = (ComponentInterface) temp.get(i);
                this.getComponent(i).setComponentNumber(i);
            }
        } catch (Exception e) {
            logger.error("not able to remove " + componentName);
        }

        //        componentArray = (ComponentInterface[])temp.toArray();
        componentArray[numberOfComponents - 1] = null;
        numberOfMolesInPhase -= molesInPhase;
        numberOfComponents--;
    }

    public void setEmptyFluid() {
        numberOfMolesInPhase = 0.0;
        for (int i = 0; i < getNumberOfComponents(); i++) {
            this.getComponent(i).setNumberOfMolesInPhase(0.0);
             this.getComponent(i).setNumberOfmoles(0.0);
        }
    }

    public void addMoles(int component, double dn) {
        numberOfMolesInPhase += dn;
        componentArray[component].addMoles(dn);
    }

    public void addMolesChemReac(int component, double dn) {
        numberOfMolesInPhase += dn;
        componentArray[component].addMolesChemReac(dn);
    }

    public void addMolesChemReac(int component, double dn, double totdn) {
        numberOfMolesInPhase += dn;
        componentArray[component].addMolesChemReac(dn, totdn);
    }

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

    public ComponentInterface[] getcomponentArray() {
        return componentArray;
    }

    public double getAntoineVaporPressure(double temp) {
        double pres = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            pres += componentArray[i].getx() * componentArray[i].getAntoineVaporPressure(temp);
            //            System.out.println(componentArray[i].getAntoineVaporPressure(temp));
        }
        return pres;
    }

    public double getWtFrac(int component) {
        return getComponent(component).getMolarMass() * getComponent(component).getx() / this.getMolarMass();
    }

    public double getPseudoCriticalTemperature() {
        double temp = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            temp += componentArray[i].getx() * componentArray[i].getTC();
        }
        return temp;
    }

    public double getPseudoCriticalPressure() {
        double pres = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            pres += componentArray[i].getx() * componentArray[i].getPC();
        }
        return pres;
    }

    public void normalize() {
        double sumx = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            sumx += componentArray[i].getx();
        }
        for (int i = 0; i < numberOfComponents; i++) {
            componentArray[i].setx(componentArray[i].getx() / sumx);
        }
    }

    public void setMoleFractions(double[] x) {
        for (int i = 0; i < numberOfComponents; i++) {
            componentArray[i].setx(x[i]);
        }
        normalize();
    }

    public double getTemperature() {
        return temperature;
    }
    
    /**
     * method to return pressure of a phase
     *
     * @return pressure in unit bara
     */
    public double getPressure() {
        return pressure;
    }
    
     /**
     * method to return pressure in a given unit
     *
     * @param unit The unit as a string. Supported units are bara, barg, Pa and
     * MPa
     * @return pressure in specified unit
     */
    public final double getPressure(String unit) {
        neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(getPressure(), "bara");
        return presConversion.getValue(unit);
    }

    public int getInitType() {
        return initType;
    }

    public double getMixGibbsEnergy() {
        double gmix = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            gmix += getComponent(i).getx() * Math.log(getComponent(i).getx());
        }
        return getExessGibbsEnergy() + R * temperature * gmix * numberOfMolesInPhase;
    }

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

    public double getZ() {
        return Z;
    }

    public void setPressure(double pres) {
        this.pressure = pres;
    }

     /**
     * method to set the temperature of a phase
     *
     * @param temp in unit Kelvin
     */
    public void setTemperature(double temp) {
        this.temperature = temp;
    }

    public neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface getPhysicalProperties() {
        return physicalProperty;
    }

    public void init() {
        init(numberOfMolesInPhase / beta, numberOfComponents, initType, phaseType, beta);
    }

    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0 start init type =1 gi nye betingelser

        this.beta = beta;
        numberOfMolesInPhase = beta * totalNumberOfMoles;
        if (this.phaseType != phase) {
            this.phaseType = phase;
            //setPhysicalProperties(physicalPropertyType);
        }
        this.setInitType(type);
        this.numberOfComponents = numberOfComponents;
        for (int i = 0; i < numberOfComponents; i++) {
            componentArray[i].init(temperature, pressure, totalNumberOfMoles, beta, type);
        }
    }

    public void setPhysicalProperties() {
        // System.out.println("Physical properties:    Default model");
        setPhysicalProperties(physicalPropertyType);
        //physicalProperty = new physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(this,0,0);
    }

    /**
     * specify the type model for the physical properties you want to use. Type:
     * Model 0 Orginal/default 1 Water 2 Glycol 3 Amine
     */
    public void setPhysicalProperties(int type) {
        physicalPropertyType = type;
        //System.out.println("phase type: " + phaseType);
        if (phaseTypeName.equals("aqueous")) {
            physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.WaterPhysicalProperties(this, 0, 0);
            // return;
        } else if (type == 0) {
            //System.out.println("Physical properties:    Default model..." + phaseType);
            if (phaseTypeName.equals("gas")) {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(this, 0, 0);
            } else {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(this, 0, 0);
            }
        } else if (type == 1) {
            //System.out.println("Physical properties:    Water model");
            if (phaseTypeName.equals("gas")) {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(this, 0, 0);
            } else {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.WaterPhysicalProperties(this, 0, 0);
            }
        } else if (type == 2) {
            //System.out.println("Physical properties:    Glycol model");
            if (phaseTypeName.equals("gas")) {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(this, 0, 0);
            } else {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.GlycolPhysicalProperties(this, 0, 0);
            }
        } else if (type == 3) {
            //System.out.println("Physical properties:    Amine model");
            if (phaseTypeName.equals("gas")) {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(this, 0, 0);
            } else {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.AminePhysicalProperties(this, 0, 0);
            }
        } else if (type == 4) {
            //System.out.println("Physical properties:    Amine model");
            if (phaseTypeName.equals("gas")) {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(this, 0, 0);
            } else {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.CO2waterPhysicalProperties(this, 0, 0);
            }
        } else if (type == 5) {
            logger.info("Physical properties:    Amine model");
            if (phaseTypeName.equals("gas")) {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(this, 0, 0);
            } else {
                physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(this, 0, 0);
            }
        } else if (type == 6) {
            //System.out.println("Physical properties:    common HC model");
            physicalProperty = new neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(this, 0, 0);
        } else {
            logger.error("error selecting physical properties model.\n Continue using default model...");
            setPhysicalProperties();
        }
        phaseTypeAtLastPhysPropUpdate = phaseType;
        phaseTypeNameAtLastPhysPropUpdate = phaseTypeName;
        //initPhysicalProperties();
    }

    public void resetPhysicalProperties() {
        neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties.mixingRule = null;
        physicalProperty = null;
    }

    public void initPhysicalProperties() {
        if (physicalProperty == null || phaseTypeAtLastPhysPropUpdate != phaseType || !phaseTypeNameAtLastPhysPropUpdate.equals(phaseTypeName)) {
            this.setPhysicalProperties();
        }
        physicalProperty.init(this);
    }

    public void initPhysicalProperties(String type) {
        if (physicalProperty == null || phaseTypeAtLastPhysPropUpdate != phaseType || !phaseTypeNameAtLastPhysPropUpdate.equals(phaseTypeName)) {
            this.setPhysicalProperties();
        }
        physicalProperty.init(this, type);
    }

    public double geta(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public double getb(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public double getf() {
        return 1;
    }

    public double getg() {
        return 1;
    }

    public double calcA(int comp, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public double calcAi(int comp, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public double calcAiT(int comp, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public double calcAT(int comp, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public double calcAij(int compNumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 0;
    }

    public double calcBij(int compNumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 0;
    }

    public double calcBi(int comp, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return 1;
    }

    public void setAtractiveTerm(int i) {
        for (int k = 0; k < numberOfComponents; k++) {
            componentArray[k].setAtractiveTerm(i);
        }
    }
    
     /**
     * method to return molar volume of the phase
     * note: without Peneloux volume correction
     * @return volume in unit m3*1e5
     */
    public double getMolarVolume() {
        return molarVolume;
    }
    
    public int getNumberOfComponents() {
        return numberOfComponents;
    }

    public double geta() {
        return 0;
    }

    public double getb() {
        return 0;
    }

    public double getA() {
        return 0;
    }

    public double getB() {
        return 0;
    }

    public double getBi() {
        return 0;
    }

    public double getAT() {
        return 0;
    }

    public double getATT() {
        return 0;
    }

    public double getAiT() {
        return 0;
    }

    public PhaseInterface getPhase() {
        return this;
    }

    public double getNumberOfMolesInPhase() {
        return numberOfMolesInPhase;
    }

    public ComponentInterface[] getComponents() {
        return componentArray;
    }

    public void setComponentArray(ComponentInterface[] components) {
        this.componentArray = components;
    }

    public double calcR() {

        double R = 8.314 / getMolarMass();

        return R;
    }

    public double Fn() {
        return 1;
    }

    public double FT() {
        return 1;
    }

    public double FV() {
        return 1;
    }

    public double FD() {
        return 1;
    }

    public double FB() {
        return 1;
    }

    public double gb() {
        return 1;
    }

    public double fb() {
        return 1;
    }

    public double gV() {
        return 1;
    }

    public double fv() {
        return 1;
    }

    public double FnV() {
        return 1;
    }

    public double FnB() {
        return 1;
    }

    public double FTT() {
        return 1;
    }

    public double FBT() {
        return 1;
    }

    public double FDT() {
        return 1;
    }

    public double FBV() {
        return 1;
    }

    public double FBB() {
        return 1;
    }

    public double FDV() {
        return 1;
    }

    public double FBD() {
        return 1;
    }

    public double FTV() {
        return 1;
    }

    public double FVV() {
        return 1;
    }

    public double gVV() {
        return 1;
    }

    public double gBV() {
        return 1;
    }

    public double gBB() {
        return 1;
    }

    public double fVV() {
        return 1;
    }

    public double fBV() {
        return 1;
    }

    public double fBB() {
        return 1;
    }

    public double dFdT() {
        return 1;
    }

    public double dFdV() {
        return 1;
    }

    public double dFdTdV() {
        return 1;
    }

    public double dFdVdV() {
        return 1;
    }

    public double dFdTdT() {
        return 1;
    }

    public double getCpres() {
        return 1;
    }

    public double getCvres() {
        return 1;
    }

    public double getHresTP() {
        logger.error("error Hres");
        return 0;
    }

    public double getHresdP() {
        logger.error(" getHresdP error Hres - not implemented?");
        return 0;
    }

    public double getGresTP() {
        logger.error("error Gres");
        return 0;
    }

    public double getSresTV() {
        logger.error("error Hres");
        return 0;
    }

    public double getSresTP() {
        return 0;
    }

    public double getCp0() {
        double tempVar = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getx() * componentArray[i].getCp0(temperature);
        }
        return tempVar;
    }

//Integral av Cp0 mhp T
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
    public double getCp() {
        // System.out.println("Cp res:" + this.getCpres() + " Cp0: " + getCp0());
        return getCp0() * numberOfMolesInPhase + this.getCpres();
    }
    
    /**
     * method to return specific heat capacity (Cp) in a given unit
     *
     * @param unit The unit as a string. Supported units are J/K, J/molK, J/kgK
     * and kJ/kgK
     * @return Cp in specified unit
     */
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
    public double getCv() {
        return getCp0() * numberOfMolesInPhase - R * numberOfMolesInPhase + getCvres();
    }
    
     /**
     * method to return specific heat capacity (Cv) in a given unit
     *
     * @param unit The unit as a string. Supported units are J/K, J/molK, J/kgK
     * and kJ/kgK
     * @return Cv in specified unit
     */
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
     * @return kappa
     */
    public double getKappa(){
        return getCp()/getCv();
    }
    
     /**
     * method to return heat capacity ratio/adiabatic index/Poisson constant.
     * The method calculates it as Cp (real) /Cv (real)
     * @return gamma
     */
    public double getGamma(){
        return getCp()/getCv();
    }
    
    /**
     * method to return enthalpy of a phase in unit Joule
     */
    public double getEnthalpy() {
        return getHID() * numberOfMolesInPhase + this.getHresTP();
    }

     /**
     * method to return phase enthalpy in a given unit
     *
     * @param unit The unit as a string. Supported units are J, J/mol, J/kg and
     * kJ/kg
     * @return enthalpy in specified unit
     */
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

    
    public double getEnthalpydP() {
        return this.getHresdP();
    }

    public double getEnthalpydT() {
        return getCp();
    }

    public void setNumberOfComponents(int numberOfComponents) {
        this.numberOfComponents = numberOfComponents;
    }

    public final int getNumberOfMolecularComponents() {
        int mol = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getIonicCharge() == 0) {
                mol++;
            }
        }
        return mol;
    }

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

        return tempVar * numberOfMolesInPhase - numberOfMolesInPhase * R * Math.log(pressure / referencePressure) + tempVar2 * numberOfMolesInPhase + this.getSresTP();
    }
    
     /**
     * method to return entropy of the phase
     *
     * @param unit The unit as a string. Supported units are J/K, J/moleK, J/kgK
     * and kJ/kgK
     * @return entropy in specified unit
     */
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
    

    public double getEntropydP() {
        return getdPdTVn() / getdPdVTn();
    }

    public double getEntropydT() {
        return getCp() / temperature;
    }

     /**
     * method to return viscosity of the phase
     *
     * @return viscosity in unit kg/msec
     */
    public double getViscosity() {
        return getPhysicalProperties().getViscosity();
    }

    /**
     * method to return viscosity og the phase in a given unit
     *
     * @param unit The unit as a string. Supported units are kg/msec, cP
     * (centipoise)
     * @return viscosity in specified unit
     */
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
        }
        return refViscosity * conversionFactor;
    }
    
     /**
     * method to return conductivity of a phase
     *
     * @return conductivity in unit W/m*K
     */
    public double getConductivity() {
        return getPhysicalProperties().getConductivity();
    }

    /**
     * method to return conductivity in a given unit
     *
     * @param unit The unit as a string. Supported units are W/mK, W/cmK
     *
     * @return conductivity in specified unit
     */
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
        }
        return refConductivity * conversionFactor;
    }
    
    public void initRefPhases(boolean onlyPure) {
        if (refPhase == null) {
            initRefPhases(onlyPure, "water");
        }
    }

    public void initRefPhases(boolean onlyPure, String name) {
        refPhase = new PhaseInterface[numberOfComponents];
        for (int i = 0; i < numberOfComponents; i++) {
            try {
                refPhase[i] = this.getClass().newInstance();
            } catch (Exception e) {
                logger.error("err " + e.toString());
            }
            refPhase[i].setTemperature(temperature);
            refPhase[i].setPressure(pressure);
            if (getComponent(i).getReferenceStateType().equals("solvent") || onlyPure) {
                refPhase[i].addcomponent(getComponent(i).getComponentName(), 10.0, 10.0, 0);
                refPhase[i].setAtractiveTerm(this.getComponent(i).getAtractiveTermNumber());
                refPhase[i].setMixingRule(this.getMixingRuleNumber());
                refPhase[i].setPhaseType(this.getPhaseType());
                refPhase[i].init(refPhase[i].getNumberOfMolesInPhase(), 1, 0, this.getPhaseType(), 1.0);
            } else {
                //System.out.println("ref " + name);
                refPhase[i].addcomponent(getComponent(i).getComponentName(), 1.0e-10, 1.0e-10, 0);
                refPhase[i].addcomponent(name, 10.0, 10.0, 1);
                refPhase[i].setAtractiveTerm(this.getComponent(i).getAtractiveTermNumber());
                refPhase[i].setMixingRule(this.getMixingRuleNumber());
                refPhase[i].init(refPhase[i].getNumberOfMolesInPhase(), 2, 0, this.getPhaseType(), 1.0);
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

    public double getLogPureComponentFugacity(int p) {
        return getLogPureComponentFugacity(p, false);
    }

    public double getPureComponentFugacity(int p) {
        return Math.exp(getLogPureComponentFugacity(p));
    }

    public double getPureComponentFugacity(int p, boolean pure) {
        return Math.exp(getLogPureComponentFugacity(p, pure));
    }

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

    public double getLogInfiniteDiluteFugacity(int k) {
        PhaseInterface dilphase = (PhaseInterface) this.clone();
        dilphase.addMoles(k, -(1.0 - 1e-10) * dilphase.getComponent(k).getNumberOfMolesInPhase());
        dilphase.getComponent(k).setx(1e-10);
        dilphase.init(dilphase.getNumberOfMolesInPhase(), dilphase.getNumberOfComponents(), 1, dilphase.getPhaseType(), 1.0);
        dilphase.getComponent(k).fugcoef(dilphase);
        return dilphase.getComponent(k).getLogFugasityCoeffisient();
    }

    public double getInfiniteDiluteFugacity(int k, int p) {
        return Math.exp(getLogInfiniteDiluteFugacity(k, p));
    }

    public double getInfiniteDiluteFugacity(int k) {
        return Math.exp(getLogInfiniteDiluteFugacity(k));
    }

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

    public double getActivityCoefficientSymetric(int k) {
        if (refPhase == null) {
            initRefPhases(true);
        }
        double fug = 0.0;
        double oldFug = getComponent(k).getLogFugasityCoeffisient();
        fug = getLogPureComponentFugacity(k);
        return Math.exp(oldFug - fug);
    }

    public double getActivityCoefficientUnSymetric(int k) {
        double fug = 0.0;
        double oldFug = getComponent(k).getLogFugasityCoeffisient();
        fug = getLogInfiniteDiluteFugacity(k);
        return Math.exp(oldFug - fug);
    }

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

        act1 = Math.pow(getActivityCoefficient(comp1, watNumb), Math.abs(getComponent(comp2).getIonicCharge()));
        act2 = Math.pow(getActivityCoefficient(comp2, watNumb), Math.abs(getComponent(comp1).getIonicCharge()));

        return Math.pow(act1 * act2, 1.0 / (Math.abs(getComponent(comp1).getIonicCharge()) + Math.abs(getComponent(comp2).getIonicCharge()))) * 1.0 / (1.0 + val);
    }

    public double getOsmoticCoefficientOfWater() {
        int watNumb = 0;
        for (int j = 0; j < this.numberOfComponents; j++) {
            if (getComponent(j).getComponentName().equals("water")) {
                watNumb = j;
            }
        }
        return getOsmoticCoefficient(watNumb);
    }

    public double getOsmoticCoefficient(int watNumb) {
        double oldFug = getComponent(watNumb).getFugasityCoeffisient();
        double pureFug = getPureComponentFugacity(watNumb);
        double ions = 0.0;
        for (int j = 0; j < this.numberOfComponents; j++) {
            if (getComponent(j).getIonicCharge() != 0) {
                ions += getComponent(j).getx();
            }
        }
        double val = -Math.log(oldFug * getComponent(watNumb).getx() / pureFug) * getComponent(watNumb).getx() / ions;
        return val;
    }

//    public double getOsmoticCoefficient(int watNumb, String refState){
//        if(refState.equals("molality")){
//            double oldFug = getComponent(watNumb).getFugasityCoeffisient();
//            double pureFug = getPureComponentFugacity(watNumb);system.getPhase(i).
//            double ions=0.0;
//            for(int j=0;j<this.numberOfComponents;j++){
//                if(getComponent(j).getIonicCharge()!=0) ions += getComponent(j).getNumberOfMolesInPhase()/getComponent(watNumb).getNumberOfMolesInPhase()/getComponent(watNumb).getMolarMass();//*Math.abs(getComponent(j).getIonicCharge());
//            }
//            double val = - Math.log(oldFug*getComponent(watNumb).getx()/pureFug) * 1.0/ions/getComponent(watNumb).getMolarMass();
//            return val;
//        }
//        else return getOsmoticCoefficient(watNumb);
//    }
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

        act1 = Math.pow(getActivityCoefficient(comp1, watNumb), Math.abs(getComponent(comp2).getIonicCharge()));
        act2 = Math.pow(getActivityCoefficient(comp2, watNumb), Math.abs(getComponent(comp1).getIonicCharge()));
        return Math.pow(act1 * act2, 1.0 / (Math.abs(getComponent(comp1).getIonicCharge()) + Math.abs(getComponent(comp2).getIonicCharge())));
    }

    public final int getPhaseType() {
        return phaseType;
    }

    public double getGibbsEnergy() {
        return getEnthalpy() - temperature * getEntropy();
    }

    public double getInternalEnergy() {
        return getEnthalpy() - pressure * getMolarVolume() * numberOfMolesInPhase;
    }

    public double getHelmholtzEnergy() {
        return getInternalEnergy() - temperature * getEntropy();
    }

    /**
     * Returns the molar mass of the phase. Unit: kg/mol
     */
    public final double getMolarMass() {
        double tempVar = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getx() * componentArray[i].getMolarMass();
        }
        return tempVar;
    }

    /**
     * method to get the Joule Thomson Coefficient of a phase
     * note: implemented in phaseEos
     * 
     * @return Joule Thomson coefficient in K/bar
     */
    public double getJouleThomsonCoefficient() {
        return 0;
    }

    /**
     * method to get density of a phase
     * note: does not use Peneloux volume correction
     * 
     * @return density with unit kg/m3
     */
    public double getDensity() {
        return 1.0 / getMolarVolume() * getMolarMass() * 1.0e5;
    }
    
     /**
     * method to get density of a fluid
     * note: with Peneloux volume correction
     * 
     * @param unit The unit as a string. Supported units are kg/m3, mol/m3
     * @return density in specified unit
     */
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
        }
        return refDensity * conversionFactor;
    }

    public final double getBeta() {
        return this.beta;
    }

    public double getdPdrho() {
        return 0;
    }

    public double getdrhodP() {
        return 0.0;
    }

    public double getdrhodT() {
        return 0;
    }

    public double getdrhodN() {
        return 0;
    }

    public void setMixingRule(int type) {
        mixingRuleNumber = type;
    }

    public double calcDiElectricConstant(double temperature) {
        double tempVar = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getNumberOfMolesInPhase() * componentArray[i].getDiElectricConstant(temperature);
        }
        return tempVar / numberOfMolesInPhase;
    }

    public double calcDiElectricConstantdT(double temperature) {
        double tempVar = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getNumberOfMolesInPhase() * componentArray[i].getDiElectricConstantdT(temperature);
        }
        return tempVar / numberOfMolesInPhase;
    }

    public double calcDiElectricConstantdTdT(double temperature) {
        double tempVar = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            tempVar += componentArray[i].getNumberOfMolesInPhase() * componentArray[i].getDiElectricConstantdTdT(temperature);
        }
        return tempVar / numberOfMolesInPhase;
    }

    public final double getDiElectricConstant() {
        return diElectricConstant;
    }

    public double getdPdTVn() {
        return 0;
    }

    public double getdPdVTn() {
        return 0;
    }

    public double getpH() {
        return getpH_old();
//        System.out.println("ph - old " + getpH_old());
//        initPhysicalProperties();
//        for(int i = 0; i<numberOfComponents; i++) {
//            if(componentArray[i].getName().equals("H3O+")){
//                return - MathLib.generalMath.GeneralMath.log10(componentArray[i].getNumberOfMolesInPhase()*getPhysicalProperties().getDensity()/(numberOfMolesInPhase*getMolarMass())*1e-3);
//            }
//        }
//        System.out.println("no H3Oplus");
//        return 7.0;
    }

    public double getpH_old() {
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getName().equals("H3O+")) {
                return -neqsim.MathLib.generalMath.GeneralMath.log10(componentArray[i].getx() * getActivityCoefficient(i));
            }
        }
        logger.info("no H3Oplus");
        return 7.0;
    }

    public ComponentInterface getComponent(int i) {
        return componentArray[i];
    }

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
    public final int getMixingRuleNumber() {
        return mixingRuleNumber;
    }

    /**
     * Indexed getter for property refPhase.
     *
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
     */
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
    public void setRefPhase(int index, neqsim.thermo.phase.PhaseInterface refPhase) {
        this.refPhase[index] = refPhase;
    }

    //   public double getTotalVolume() {
    //      return numberOfMolesInPhase * getMolarVolume();
    //  }
    /**
     * Setter for property refPhase.
     *
     * @param refPhase New value of property refPhase.
     */
    public void setRefPhase(neqsim.thermo.phase.PhaseInterface[] refPhase) {
        this.refPhase = refPhase;
    }

    /**
     * Getter for property physicalPropertyType.
     *
     * @return Value of property physicalPropertyType.
     */
    public final int getPhysicalPropertyType() {
        return physicalPropertyType;
    }

    /**
     * Setter for property physicalPropertyType.
     *
     * @param physicalPropertyType New value of property physicalPropertyType.
     */
    public void setPhysicalPropertyType(int physicalPropertyType) {
        this.physicalPropertyType = physicalPropertyType;
    }

    public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT, String[][] mixRule, double[][] intparam) {
    }

    public final boolean useVolumeCorrection() {
        return useVolumeCorrection;
    }

    public void useVolumeCorrection(boolean volcor) {
        useVolumeCorrection = volcor;
    }

    public double getFugacity(int compNumb) {
        //System.out.println("fugcoef" + this.getComponent(compNumb).getFugasityCoefficient());
        return this.getComponent(compNumb).getx() * this.getComponent(compNumb).getFugasityCoefficient() * pressure;
    }

    public double getFugacity(String compName) {
        return this.getComponent(compName).getx() * this.getComponent(compName).getFugasityCoefficient() * pressure;
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
     *
     */
    public final void setBeta(double beta) {
        this.beta = beta;
    }

    public void setMixingRuleGEModel(String name) {
    }

    /**
     * Getter for property phaseTypeName.
     *
     * @return Value of property phaseTypeName.
     *
     */
    public java.lang.String getPhaseTypeName() {
        return phaseTypeName;
    }

    /**
     * Setter for property phaseTypeName.
     *
     * @param phaseTypeName New value of property phaseTypeName.
     *
     */
    public void setPhaseTypeName(java.lang.String phaseTypeName) {
        this.phaseTypeName = phaseTypeName;
    }

    /**
     * Getter for property mixingRuleDefined.
     *
     * @return Value of property mixingRuleDefined.
     *
     */
    public boolean isMixingRuleDefined() {
        return mixingRuleDefined;
    }

    /**
     * Setter for property mixingRuleDefined.
     *
     * @param mixingRuleDefined New value of property mixingRuleDefined.
     *
     */
    public void setMixingRuleDefined(boolean mixingRuleDefined) {
        this.mixingRuleDefined = mixingRuleDefined;
    }

    /**
     * Setter for property phaseType.
     *
     * @param phaseType New value of property phaseType.
     *
     */
    public final void setPhaseType(int phaseType) {
        this.phaseType = phaseType;
    }
    

    public void setMolarVolume(double molarVolume) {
        this.molarVolume = molarVolume;
    }

    public void calcMolarVolume(boolean test) {
        this.calcMolarVolume = test;
    }

    public void setTotalVolume(double volume) {
        phaseVolume = volume;
    }

     /**
     * method to return phase volume
     * note: without Peneloux volume correction
     * @return volume in unit m3*1e5
     */
    public double getTotalVolume() {
        if (constantPhaseVolume) {
            return phaseVolume;
        }
        return getMolarVolume() * getNumberOfMolesInPhase();
    }

     /**
     * method to return phase volume
     * note: without Peneloux volume correction
     * @return volume in unit m3*1e5
     */
    public double getVolume() {
        return getTotalVolume();
    }
    
     /**
     * method to return phase volume with Peneloux volume correction
     * need to call initPhysicalProperties() before this method is called
     * @return volume in unit m3
     */
    public double getCorrectedVolume() {
        return getMolarMass() / getPhysicalProperties().getDensity() * getNumberOfMolesInPhase();
    }


    public boolean hasPlusFraction() {
        for (int i = 0; i < numberOfComponents; i++) {
            if (getComponent(i).isIsPlusFraction()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTBPFraction() {
        for (int i = 0; i < numberOfComponents; i++) {
            if (getComponent(i).isIsTBPfraction()) {
                return true;
            }
        }
        return false;
    }

    public boolean isConstantPhaseVolume() {
        return constantPhaseVolume;
    }

    public void setConstantPhaseVolume(boolean constantPhaseVolume) {
        this.constantPhaseVolume = constantPhaseVolume;
    }

    public double getMass() {
        return getMolarMass() * numberOfMolesInPhase;
    }

    /**
     * method to get the speed of sound of a phase
     * note: implemented in phaseEos
     * 
     * @return speed of sound in m/s
     */
    public double getSoundSpeed() {
        return 0.0;
    }

    public ComponentInterface getComponentWithIndex(int index) {
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getIndex() == index) {
                return componentArray[i];
            }
        }
        return null;
    }

    public double getWtFraction(SystemInterface system) {
        return getBeta() * getMolarMass() / system.getMolarMass();
    }

    public double getMoleFraction() {
        return beta;
    }

    /**
     * @param initType the initType to set
     */
    public void setInitType(int initType) {
        this.initType = initType;
    }

    public double getWtFractionOfWaxFormingComponents() {
        double wtFrac = 0.0;

        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].isWaxFormer()) {
                wtFrac += componentArray[i].getx() * componentArray[i].getMolarMass() / getMolarMass();
            }
        }
        return wtFrac;
    }
}
