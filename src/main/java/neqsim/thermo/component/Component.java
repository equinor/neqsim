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
 * Component.java
 *
 * Created on 8. april 2000, 23:28
 */
package neqsim.thermo.component;

//import thermo.component.*;
import neqsim.thermo.ThermodynamicConstantsInterface;
import static neqsim.thermo.ThermodynamicConstantsInterface.MAX_NUMBER_OF_COMPONENTS;
import static neqsim.thermo.ThermodynamicConstantsInterface.R;
import static neqsim.thermo.ThermodynamicConstantsInterface.referencePressure;
import static neqsim.thermo.ThermodynamicConstantsInterface.referenceTemperature;
import neqsim.thermo.atomElement.Element;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermInterface;
import neqsim.thermo.phase.PhaseInterface;

abstract class Component extends Object implements ComponentInterface, ThermodynamicConstantsInterface, Cloneable, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    double[] surfTensInfluenceParam = {0.28367, -0.05164, -0.81594, 1.06810, -1.1147};
    protected int index, componentNumber, atractiveTermNumber = 0, numberOfAssociationSites = 0;
    protected double logFugasityCoeffisient = 0.0, associationVolume = 0.0, associationEnergy = 0.0, aCPA = 0.0, bCPA = 0.0, mCPA = 0.0, srkacentricFactor = 0.0;
    protected String componentName = "default", referenceStateType = "solvent", associationScheme = "0", antoineLiqVapPresType = null;
    private String formulae = "", CASnumber = "";
    protected Element elements;
    protected boolean isTBPfraction = false, isPlusFraction = false, isNormalComponent = true, isIon = false;
    private boolean isHydrateFormer = false;
    private boolean waxFormer = false;
    private String componentType = "Component";
    protected double qPure = 0, voli = 1.0;
    protected int calcActivity = 1;
    protected boolean solidCheck = false;
    protected double dqPuredT = 0, dqPuredTdT = 0;
    private double racketZCPA = 0;
    private double criticalCompressibilityFactor = 0.0;
    private double volumeCorrectionT = 0.0, volumeCorrectionT_CPA = 0.0;
    protected double criticalPressure, criticalTemperature, molarMass, acentricFactor, numberOfMoles = 0.0, numberOfMolesInPhase = 0.0, normalLiquidDensity = 0;
    protected double reducedPressure, reducedTemperature, fugasityCoeffisient, debyeDipoleMoment = 0, viscosityCorrectionFactor = 0, criticalVolume = 0, racketZ = 0;
    protected double gibbsEnergyOfFormation = 0, criticalViscosity = 0.0;
    protected double referencePotential = 0, viscosityFrictionK = 1.0;
    protected int liquidViscosityModel = 0;
    protected int ionicCharge = 0;
    private double referenceEnthalpy = 0.0;
    protected double parachorParameter = 0.0, normalBoilingPoint = 0, sphericalCoreRadius = 0.384, standardDensity = 0, AntoineASolid = 0.0, AntoineBSolid = 0.0, AntoineCSolid = 0.0;
    protected double[] liquidViscosityParameter = new double[4];
    protected double[] liquidConductivityParameter = new double[3];
    protected double[] henryCoefParameter = new double[4];
    protected double[] dielectricParameter = new double[5];
    protected double[] schwartzentruberParams = new double[3], matiascopemanParams = new double[3], matiascopemanParamsPR = new double[3], TwuCoonParams = new double[3], matiascopemanSolidParams = new double[3];
    protected double lennardJonesMolecularDiameter = 0, lennardJonesEnergyParameter = 0, stokesCationicDiameter = 0, paulingAnionicDiameter = 0;
    protected double K, z;
    protected double x = 0;
    private int orginalNumberOfAssociationSites = 0;
    protected double dfugdt = 0.1, dfugdp = 0.1;
    protected double[] dfugdn = new double[MAX_NUMBER_OF_COMPONENTS];
    public double[] dfugdx = new double[MAX_NUMBER_OF_COMPONENTS];
    double AntoineA = 0, AntoineB = 0, AntoineC = 0, AntoineD = 0, AntoineE = 0;
    private double CpA = 0;
    private double CpB = 0;
    private double CpC = 0;
    private double CpD = 0;
    private double CpE = 0;
    private double[] CpSolid = new double[5];
    private double[] CpLiquid = new double[5];
    private double heatOfFusion = 0.0;
    double triplePointDensity = 0.0, triplePointPressure = 0.0;
    private double triplePointTemperature = 0.0;
    double meltingPointTemperature = 0.0;
    private double idealGasEnthalpyOfFormation = 0.0;
    double idealGasGibsEnergyOfFormation = 0.0, idealGasAbsoluteEntropy = 0.0;
    double Hsub = 0.0;
    double[] solidDensityCoefs = new double[5];
    double[] liquidDensityCoefs = new double[5];
    double[] heatOfVaporizationCoefs = new double[5];
    protected double mSAFTi = 0;
    protected double sigmaSAFTi = 0;
    protected double epsikSAFT = 0;
    private double associationVolumeSAFT;
    private double associationEnergySAFT = 0;

    /**
     * Creates new Component
     */
    // Class methods
    public Component() {
    }

    public Component(int number, double moles) {

        numberOfMoles = moles;

    }

    public Component(double moles) {
        numberOfMoles = moles;
    }

    public Component(int number, double TC, double PC, double M, double a, double moles) {
        criticalPressure = PC;
        criticalTemperature = TC;
        molarMass = M;
        acentricFactor = a;
        numberOfMoles = moles;
    }

    public Component(String component_name, double moles, double molesInPhase, int compnumber) {
        createComponent(component_name, moles, molesInPhase, compnumber);
    }

    public void insertComponentIntoDatabase(String databaseName) {
        databaseName = "COMPTEMP";
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        try {
            int isW = 0;
            if (isWaxFormer()) {
                isW = 1;
            }
            database.execute("insert into COMPTEMP VALUES (" + (1000 + componentNumber) + ", '" + componentName + "', '00-00-0','" + getComponentType() + "', " + (1000 + componentNumber) + ", 'HC', " + (molarMass * 1000.0) + ", " + normalLiquidDensity + ", " + (getTC() - 273.15) + ", " + getPC() + ", " + getAcentricFactor() + "," + (getNormalBoilingPoint() - 273.15) + ", 39.948, 74.9, 'Classic', 0, " + getCpA() + ", " + getCpB() + ", " + getCpC() + ", " + getCpD() + ", " + getCpE() + ", 'log', 5.2012, 1936.281, -20.143, -1.23303, 1000, 1.8, 0.076, 0.0, 0.0, 2.52, 809.1, 0, 3, -24.71, 4210, 0.0453, -3.38e-005, -229000, -19.2905, 29814.5, -0.019678, 0.000132, -3.11e-007, 0, 'solvent', 0, 0, 0, 0, 0.0789, -1.16, 0, -0.384, 0.00525, -6.37e-006, 207, " + getHeatOfFusion() + ", 1000, 0.00611, " + getTriplePointTemperature() + ", " + getMeltingPointTemperature() + ", -242000, 189, 53, -0.00784, 0, 0, 0, 5.46, 0.305, 647, 0.081, 0, 52100000, 0.32, -0.212, 0.258, 0, 0.999, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, '0', 0, 0, 0, 0,0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 'no', " + getmSAFTi() + ", " + (getSigmaSAFTi() * 1e10) + ", " + getEpsikSAFT() + ", 0, 0,0,0,0,0," + isW + ",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)");
            index = 1000 + componentNumber;
            CASnumber = "00-00-0";
        } catch (Exception e) {
            System.out.println("error in inserting to database");
            e.printStackTrace();
        } finally {
            try {
                if (database.getStatement() != null) {
                    database.getStatement().close();
                }
                if (database.getConnection() != null) {
                    database.getConnection().close();
                }
            } catch (Exception e) {
                System.out.println("error closing database.....");
                e.printStackTrace();
            }
        }
    }

    public void createComponent(String component_name, double moles, double molesInPhase, int compnumber) {
        componentName = component_name;
        numberOfMoles = moles;
        numberOfMolesInPhase = molesInPhase;
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        try {
            if (!component_name.equals("default")) {
                try {
                    dataSet = database.getResultSet(("SELECT * FROM COMPTEMP WHERE name='" + component_name + "'"));
                    dataSet.next();
                    dataSet.getString("ID");
                    //   if(dataSet.isAfterLast()) dataSet.next(); 
                } catch (Exception e) {
                    dataSet.close();
                    System.out.println("no parameters in tempcomp -- trying comp.. " + component_name);
                    dataSet = database.getResultSet(("SELECT * FROM COMP WHERE name='" + component_name + "'"));
                    dataSet.next();
                }
                setComponentType(dataSet.getString("comptype"));
                setCASnumber(dataSet.getString("CASnumber"));
                index = Integer.parseInt(dataSet.getString("compindex"));
                setFormulae(dataSet.getString("FORMULA").trim());                                                   //C
                molarMass = Double.parseDouble(dataSet.getString("molarmass")) / 1000.0;                         //D
                normalLiquidDensity = Double.parseDouble(dataSet.getString("liqdens"));                //E
                criticalTemperature = (Double.parseDouble(dataSet.getString("TC")) + 273.15);      //F
                criticalPressure = Double.parseDouble(dataSet.getString("PC"));                   //G
                acentricFactor = Double.parseDouble(dataSet.getString("acsfact"));                                                            //J
                criticalVolume = Double.parseDouble(dataSet.getString("critvol"));
                criticalCompressibilityFactor = criticalPressure * criticalVolume / ThermodynamicConstantsInterface.R / criticalTemperature / 10.0;
                referenceEnthalpy = Double.parseDouble(dataSet.getString("Href"));
                setCpA(dataSet.getDouble("CPA"));                                //R                                       //S
                setCpB(dataSet.getDouble("CPB"));                                //S
                setCpC(dataSet.getDouble("CPC"));                                //T
                setCpD(dataSet.getDouble("CPD"));
                setCpE(dataSet.getDouble("CPE"));

                CpSolid[0] = Double.parseDouble(dataSet.getString("CPsolid1"));
                CpSolid[1] = Double.parseDouble(dataSet.getString("CPsolid2"));
                CpSolid[2] = Double.parseDouble(dataSet.getString("CPsolid3"));
                CpSolid[3] = Double.parseDouble(dataSet.getString("CPsolid4"));
                CpSolid[4] = Double.parseDouble(dataSet.getString("CPsolid5"));

                CpLiquid[0] = Double.parseDouble(dataSet.getString("CPliquid1"));
                CpLiquid[1] = Double.parseDouble(dataSet.getString("CPliquid2"));
                CpLiquid[2] = Double.parseDouble(dataSet.getString("CPliquid3"));
                CpLiquid[3] = Double.parseDouble(dataSet.getString("CPliquid4"));
                CpLiquid[4] = Double.parseDouble(dataSet.getString("CPliquid5"));

                antoineLiqVapPresType = dataSet.getString("AntoineVapPresLiqType");
                AntoineA = Double.parseDouble(dataSet.getString("ANTOINEA"));                           //AY
                AntoineB = Double.parseDouble(dataSet.getString("ANTOINEB"));                           //AZ
                AntoineC = Double.parseDouble(dataSet.getString("ANTOINEC"));                           //AX
                AntoineD = Double.parseDouble(dataSet.getString("ANTOINED"));
                AntoineE = Double.parseDouble(dataSet.getString("ANTOINEE"));

                if (AntoineA == 0) {
                    AntoineA = 1.0;
                    AntoineB = getNormalBoilingPoint();
                }

                AntoineASolid = Double.parseDouble(dataSet.getString("ANTOINESolidA"));
                AntoineBSolid = Double.parseDouble(dataSet.getString("ANTOINESolidB"));
                AntoineCSolid = Double.parseDouble(dataSet.getString("ANTOINESolidC"));

                debyeDipoleMoment = Double.parseDouble(dataSet.getString("dipolemoment"));
                normalBoilingPoint = Double.parseDouble(dataSet.getString("normboil"));
                standardDensity = Double.parseDouble(dataSet.getString("stddens"));
                viscosityCorrectionFactor = Double.parseDouble(dataSet.getString("viscfact"));            //BC
                racketZ = Double.parseDouble(dataSet.getString("racketZ"));                              //BE
                lennardJonesMolecularDiameter = Double.parseDouble(dataSet.getString("LJdiameter"));        //BF
                lennardJonesEnergyParameter = Double.parseDouble(dataSet.getString("LJeps"));
                sphericalCoreRadius = Double.parseDouble(dataSet.getString("SphericalCoreRadius"));
                liquidViscosityModel = Integer.parseInt(dataSet.getString("liqviscmodel"));
                liquidViscosityParameter[0] = Double.parseDouble(dataSet.getString("liqvisc1"));
                liquidViscosityParameter[1] = Double.parseDouble(dataSet.getString("liqvisc2"));
                liquidViscosityParameter[2] = Double.parseDouble(dataSet.getString("liqvisc3"));
                liquidViscosityParameter[3] = Double.parseDouble(dataSet.getString("liqvisc4"));

                gibbsEnergyOfFormation = Double.parseDouble(dataSet.getString("gibbsEnergyOfFormation"));
                dielectricParameter[0] = Double.parseDouble(dataSet.getString("dielectricParameter1"));
                dielectricParameter[1] = Double.parseDouble(dataSet.getString("dielectricParameter2"));
                dielectricParameter[2] = Double.parseDouble(dataSet.getString("dielectricParameter3"));
                dielectricParameter[3] = Double.parseDouble(dataSet.getString("dielectricParameter4"));
                dielectricParameter[4] = Double.parseDouble(dataSet.getString("dielectricParameter5"));

                ionicCharge = Integer.parseInt(dataSet.getString("ionicCharge"));

                referenceStateType = dataSet.getString("referenceStateType").trim();
                henryCoefParameter[0] = Double.parseDouble(dataSet.getString("HenryCoef1"));
                henryCoefParameter[1] = Double.parseDouble(dataSet.getString("HenryCoef2"));
                henryCoefParameter[2] = Double.parseDouble(dataSet.getString("HenryCoef3"));
                henryCoefParameter[3] = Double.parseDouble(dataSet.getString("HenryCoef4"));

                schwartzentruberParams[0] = Double.parseDouble(dataSet.getString("schwartzentruber1"));
                schwartzentruberParams[1] = Double.parseDouble(dataSet.getString("schwartzentruber2"));
                schwartzentruberParams[2] = Double.parseDouble(dataSet.getString("schwartzentruber3"));

                matiascopemanParams[0] = Double.parseDouble(dataSet.getString("MC1"));
                matiascopemanParams[1] = Double.parseDouble(dataSet.getString("MC2"));
                matiascopemanParams[2] = Double.parseDouble(dataSet.getString("MC3"));

                matiascopemanParamsPR[0] = Double.parseDouble(dataSet.getString("MCPR1"));
                matiascopemanParamsPR[1] = Double.parseDouble(dataSet.getString("MCPR2"));
                matiascopemanParamsPR[2] = Double.parseDouble(dataSet.getString("MCPR3"));

                matiascopemanSolidParams[0] = Double.parseDouble(dataSet.getString("MC1Solid"));
                matiascopemanSolidParams[1] = Double.parseDouble(dataSet.getString("MC2Solid"));
                matiascopemanSolidParams[2] = Double.parseDouble(dataSet.getString("MC3Solid"));

                TwuCoonParams[0] = Double.parseDouble(dataSet.getString("TwuCoon1"));
                TwuCoonParams[1] = Double.parseDouble(dataSet.getString("TwuCoon2"));
                TwuCoonParams[2] = Double.parseDouble(dataSet.getString("TwuCoon3"));

                liquidConductivityParameter[0] = Double.parseDouble(dataSet.getString("liquidConductivity1"));
                liquidConductivityParameter[1] = Double.parseDouble(dataSet.getString("liquidConductivity2"));
                liquidConductivityParameter[2] = Double.parseDouble(dataSet.getString("liquidConductivity3"));

                if (this.getClass().getName().equals("neqsim.thermo.component.ComponentSrkCPA") || this.getClass().getName().equals("neqsim.thermo.component.ComponentSrkCPAs")) {
                    parachorParameter = Double.parseDouble(dataSet.getString("PARACHOR_CPA"));
                } else {
                    parachorParameter = Double.parseDouble(dataSet.getString("parachor"));
                }

                setHeatOfFusion(Double.parseDouble(dataSet.getString("heatOfFusion")));

                triplePointDensity = Double.parseDouble(dataSet.getString("triplePointDensity"));
                triplePointPressure = Double.parseDouble(dataSet.getString("triplePointPressure"));
                setTriplePointTemperature(Double.parseDouble(dataSet.getString("triplePointTemperature")));
                meltingPointTemperature = Double.parseDouble(dataSet.getString("meltingPointTemperature"));

                Hsub = Double.parseDouble(dataSet.getString("Hsub"));

                setIdealGasEnthalpyOfFormation(Double.parseDouble(dataSet.getString("EnthalpyOfFormation")));
                idealGasGibsEnergyOfFormation = gibbsEnergyOfFormation;
                idealGasAbsoluteEntropy = Double.parseDouble(dataSet.getString("AbsoluteEntropy"));

                for (int i = 0; i < 5; i++) {
                    solidDensityCoefs[i] = Double.parseDouble((dataSet.getString("solidDensityCoefs" + (i + 1))));
                }
                for (int i = 0; i < 5; i++) {
                    liquidDensityCoefs[i] = Double.parseDouble((dataSet.getString("liquidDensityCoefs" + (i + 1))));
                }
                for (int i = 0; i < 5; i++) {
                    heatOfVaporizationCoefs[i] = Double.parseDouble((dataSet.getString("heatOfVaporizationCoefs" + (i + 1))));
                }
                // disse må settes inn fra database ssociationsites
                numberOfAssociationSites = Integer.parseInt(dataSet.getString("associationsites"));
                orginalNumberOfAssociationSites = numberOfAssociationSites;
                associationScheme = dataSet.getString("associationscheme");
                associationEnergy = Double.parseDouble(dataSet.getString("associationenergy"));

                calcActivity = Integer.parseInt(dataSet.getString("calcActivity"));
                setRacketZCPA(Double.parseDouble(dataSet.getString("racketZCPA")));

                setVolumeCorrectionT_CPA(Double.parseDouble(dataSet.getString("volcorrCPA_T")));
                volumeCorrectionT = Double.parseDouble(dataSet.getString("volcorrSRK_T"));

                if (this.getClass().getName().equals("neqsim.thermo.component.ComponentPrCPA")) {
                    //System.out.println("pr-cpa");
                    associationVolume = Double.parseDouble(dataSet.getString("associationboundingvolume_PR"));
                    aCPA = Double.parseDouble(dataSet.getString("aCPA_PR"));
                    bCPA = Double.parseDouble(dataSet.getString("bCPA_PR"));
                    mCPA = Double.parseDouble(dataSet.getString("mCPA_PR"));
                } else {
                    //System.out.println("srk-cpa");
                    associationVolume = Double.parseDouble(dataSet.getString("associationboundingvolume_SRK"));
                    aCPA = Double.parseDouble(dataSet.getString("aCPA_SRK"));
                    bCPA = Double.parseDouble(dataSet.getString("bCPA_SRK"));
                    mCPA = Double.parseDouble(dataSet.getString("mCPA_SRK"));
                }

                criticalViscosity = Double.parseDouble(dataSet.getString("criticalViscosity"));
                if (criticalViscosity < 1e-20) {
                    criticalViscosity = 7.94830 * Math.sqrt(1e3 * molarMass) * Math.pow(criticalPressure, 2.0 / 3.0) / Math.pow(criticalTemperature, 1.0 / 6.0) * 1e-7;
                }
                mSAFTi = Double.parseDouble(dataSet.getString("mSAFT"));
                sigmaSAFTi = Double.parseDouble(dataSet.getString("sigmaSAFT")) / 1.0e10;
                epsikSAFT = Double.parseDouble(dataSet.getString("epsikSAFT"));
                setAssociationVolumeSAFT(Double.parseDouble(dataSet.getString("associationboundingvolume_PCSAFT")));
                setAssociationEnergySAFT(Double.parseDouble(dataSet.getString("associationenergy_PCSAFT")));
                if (Math.abs(criticalViscosity) < 1e-12) {
                    criticalViscosity = 7.94830 * Math.sqrt(molarMass * 1e3) * Math.pow(criticalPressure, 2.0 / 3.0) / Math.pow(criticalTemperature, 1.0 / 6.0) * 1e-7;
                }
                //System.out.println("crit visc " + criticalViscosity);
                if (normalLiquidDensity == 0) {
                    normalLiquidDensity = molarMass / (0.285 * Math.pow(criticalVolume, 1.048));
                }
                if (dataSet.getString("HydrateFormer").equals("yes")) {
                    setIsHydrateFormer(true);
                } else {
                    setIsHydrateFormer(false);
                }

                waxFormer = Integer.parseInt(dataSet.getString("waxformer")) == 1;
                //System.out.println(componentName + " pure component parameters: ok...");
            }
            componentNumber = compnumber;
        } catch (Exception e) {
            System.out.println("error in comp");
            e.printStackTrace();
        } finally {
            try {
                if (dataSet != null) {
                    dataSet.close();
                }
                if (database.getStatement() != null) {
                    database.getStatement().close();
                }
                if (database.getConnection() != null) {
                    database.getConnection().close();
                }
            } catch (Exception e) {
                System.out.println("error closing database.....");
                e.printStackTrace();
            }
        }

        srkacentricFactor = acentricFactor;
        stokesCationicDiameter = lennardJonesMolecularDiameter;
        paulingAnionicDiameter = lennardJonesMolecularDiameter;
    }

    public Object clone() {

        Component clonedComponent = null;
        try {
            clonedComponent = (Component) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedComponent;
    }

    public void addMolesChemReac(double dn) {
        numberOfMoles += dn;
        numberOfMolesInPhase += dn;
    }

    public void addMolesChemReac(double dn, double totdn) {
        numberOfMoles += totdn;
        numberOfMolesInPhase += dn;
    }

    public void addMoles(double dn) {
        numberOfMolesInPhase += dn;
    }

    public void setProperties(ComponentInterface component) {
        x = component.getx();
        z = component.getz();
        numberOfMolesInPhase = component.getNumberOfMolesInPhase();
        numberOfMoles = component.getNumberOfmoles();
        K = component.getK();
    }

    public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int type) {

        if (type == 0) {
            z = numberOfMoles / totalNumberOfMoles;
            K = Math.exp(Math.log(criticalPressure / pressure) + 5.373 * (1.0 + srkacentricFactor) * (1.0 - criticalTemperature / temperature));
            x = z;
            //System.out.println("K " + K);
        }
        numberOfMolesInPhase = totalNumberOfMoles * x * beta;
        numberOfMoles = totalNumberOfMoles * z; // added late by Even 22/10-06
        z = numberOfMoles / totalNumberOfMoles;
    }

    public Element getElements() {
        if (elements == null) {
            elements = new Element(componentName);
        }
        return elements;
    }

    public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta, int numberOfComponents, int type) {
    }

    public final double getx() {
        return x;
    }

    public final double getz() {
        return z;
    }

    public final void setz(double z) {
        this.z = z;
    }

    public final double getReferencePotential() {
        return referencePotential;
    }

    public final void setReferencePotential(double ref) {
        this.referencePotential = ref;
    }

    public final double getK() {
        return K;
    }

    public final double getHeatOfFusion() {
        return heatOfFusion;
    }

    public double getHeatOfVapourization(double temp) {
        return heatOfVaporizationCoefs[0] * 0.0;  // må settes på rett form
    }

    public final double getTripplePointDensity() {
        return triplePointDensity;
    }

    public final double getTriplePointPressure() {
        return triplePointPressure;
    }

    public final double getTriplePointTemperature() {
        return triplePointTemperature;
    }

    public final double getMeltingPointTemperature() {
        return meltingPointTemperature;
    }

    public final double getIdealGasEnthalpyOfFormation() {
        return idealGasEnthalpyOfFormation;
    }

    public final double getIdealGasGibsEnergyOfFormation() {
        return idealGasGibsEnergyOfFormation;
    }

    public final double getIdealGasAbsoluteEntropy() {
        return idealGasAbsoluteEntropy;
    }

    public final double getTC() {
        return criticalTemperature;
    }

    public final void setTC(double val) {
        criticalTemperature = val;
    }

    public final void setPC(double val) {
        criticalPressure = val;
    }

    public final String getComponentName() {
        return componentName;
    }

    public final String getReferenceStateType() {
        return referenceStateType;
    }

    public final double getPC() {
        return criticalPressure;
    }

    public double getGibbsEnergyOfFormation() {
        return gibbsEnergyOfFormation;
    }

    public double getDiElectricConstant(double temperature) {
        return dielectricParameter[0] + dielectricParameter[1] / temperature + dielectricParameter[2] * temperature + dielectricParameter[3] * temperature * temperature + dielectricParameter[4] * Math.pow(temperature, 3.0);
    }

    public double getDiElectricConstantdT(double temperature) {
        return -dielectricParameter[1] / Math.pow(temperature, 2.0) + dielectricParameter[2] + 2.0 * dielectricParameter[3] * temperature + 3.0 * dielectricParameter[4] * Math.pow(temperature, 2.0);
    }

    public double getDiElectricConstantdTdT(double temperature) {
        return 2.0 * dielectricParameter[1] / Math.pow(temperature, 3.0) + 2.0 * dielectricParameter[3] + 6.0 * dielectricParameter[4] * Math.pow(temperature, 1.0);
    }

    public double getDebyeDipoleMoment() {
        return debyeDipoleMoment;
    }

    public final double getIonicCharge() {
        return ionicCharge;
    }

    public final void setViscosityAssociationFactor(double val) {
        viscosityCorrectionFactor = val;
    }

    public final double getRacketZ() {
        return racketZ;
    }

    public double getVolumeCorrection() {
        return 0;
    }

    public double getNormalLiquidDensity() {
        return normalLiquidDensity;
    }

    public double getViscosityCorrectionFactor() {
        return viscosityCorrectionFactor;
    }

    public double getCriticalVolume() {
        return criticalVolume;
    }

    public final int getLiquidViscosityModel() {
        return liquidViscosityModel;
    }

    public final double getParachorParameter() {
        return parachorParameter;
    }

    public final void setParachorParameter(double parachorParameter) {
        this.parachorParameter = parachorParameter;
    }

    public final void setLiquidViscosityModel(int modelNumber) {
        liquidViscosityModel = modelNumber;
    }

    public final void setLiquidViscosityParameter(double number, int i) {
        liquidViscosityParameter[i] = number;
    }

    public final double getLiquidViscosityParameter(int i) {
        return liquidViscosityParameter[i];
    }

    public void setLiquidConductivityParameter(double number, int i) {
        liquidConductivityParameter[i] = number;
    }

    public double getLiquidConductivityParameter(int i) {
        return liquidConductivityParameter[i];
    }

    /**
     * Units in m*e10
     */
    public double getLennardJonesMolecularDiameter() {
        return lennardJonesMolecularDiameter;
    }

    public double getLennardJonesEnergyParameter() {
        return lennardJonesEnergyParameter;
    }

    public double getHsub() {
        return Hsub;
    }

    /**
     * Calculates the pure comonent solid vapor pressure (bar) with the C-C
     * equation, based on Hsub Should only be used in the valid temperature
     * range below the triple point (specified in component database).
     */
    public double getCCsolidVaporPressure(double temperature) {
        return triplePointPressure * (Math.exp(Hsub / R * (1.0 / getTriplePointTemperature() - 1.0 / temperature)));
    }

    /**
     * Calculates the DT of pure comonent solid vapor pressure (bar) with the
     * C-C equation, based on Hsub Should only be used in the valid temperature
     * range below the triple point (specified in component database).
     */
    public double getCCsolidVaporPressuredT(double temperature) {
        return triplePointPressure * Hsub / R * (1.0 / (temperature * temperature)) * (Math.exp(Hsub / R * (1.0 / getTriplePointTemperature() - 1.0 / temperature)));
    }

    /**
     * Calculates the pure component solid density in kg/liter Should only be
     * used in the valid temperature range (specified in component database).
     */
    public double getPureComponentSolidDensity(double temperature) {
        return molarMass * 1000.0 * (solidDensityCoefs[0] + solidDensityCoefs[1] * Math.pow(temperature, 1.0) + solidDensityCoefs[2] * Math.pow(temperature, 2.0) + solidDensityCoefs[3] * Math.pow(temperature, 3.0) + solidDensityCoefs[4] * Math.pow(temperature, 4.0));
    }

    /**
     * Calculates the pure component liquid density in kg/liter Should only be
     * used in the valid temperature range (specified in component database).
     * This method seems to give bad results at the moment
     */
    public double getPureComponentLiquidDensity(double temperature) {
        return molarMass * 1000.0 * (liquidDensityCoefs[0] + liquidDensityCoefs[1] * Math.pow(temperature, 1.0) + liquidDensityCoefs[2] * Math.pow(temperature, 2.0) + liquidDensityCoefs[3] * Math.pow(temperature, 3.0) + liquidDensityCoefs[4] * Math.pow(temperature, 4.0));
        //return Math.pow(liquidDensityCoefs[0] / liquidDensityCoefs[1], 1.0 + Math.pow(1.0 - temperature / liquidDensityCoefs[2], liquidDensityCoefs[3]));
    }

    /**
     * Calculates the pure comonent heat of vaporization in J/mol
     */
    public double getPureComponentHeatOfVaporization(double temperature) {
        return 1.0e-3 * heatOfVaporizationCoefs[0] * Math.pow((1.0 - temperature / criticalTemperature), heatOfVaporizationCoefs[1] + heatOfVaporizationCoefs[2] * temperature / criticalTemperature + heatOfVaporizationCoefs[3] * Math.pow(temperature / criticalTemperature, 2.0));
    }

    public final void setx(double newx) {
        if (Double.isNaN(newx) || Double.isInfinite(newx)) {
            return;
        }
        if (newx < 0) {
            x = 1.0e-50;
        }
        if (newx > 0) {
            x = newx;
        }
        if (newx > 5) {
            x = 5;
        }
    }

    public final void setNumberOfmoles(double newmoles) {
        numberOfMoles = newmoles;
    }

    public final void setNumberOfMolesInPhase(double totmoles) {
        numberOfMolesInPhase = totmoles * x;

    }

    public final double getNumberOfmoles() {
        return this.numberOfMoles;
    }

    public final double getMolarMass() {
        return this.molarMass;
    }

    public final double getNumberOfMolesInPhase() {
        return this.numberOfMolesInPhase;
    }

    public double getRate(String unitName) {
        neqsim.util.unit.Unit unit = new neqsim.util.unit.RateUnit(numberOfMolesInPhase, "mol/sec", molarMass, normalLiquidDensity, normalBoilingPoint);
        double val = unit.getValue(unitName);
        return val;
    }

    public final void setK(double newK) {
        K = newK;
    }

    public final double getFugasityCoeffisient() {
        return fugasityCoeffisient;
    }

    public final double getFugasityCoefficient() {
        return getFugasityCoeffisient();
    }

    public double fugcoef(PhaseInterface phase) {
        fugasityCoeffisient = 1.0;//this.fugcoef(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        return fugasityCoeffisient;
    }

    public double logfugcoefdT(PhaseInterface phase) {
        dfugdt = 0.0;//this.fugcoefDiffTemp(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
        return dfugdt;
    }

    public double logfugcoefdP(PhaseInterface phase) {
        dfugdp = 0.0;//this.fugcoefDiffPres(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
        return dfugdp;
    }

    public double[] logfugcoefdN(PhaseInterface phase) {
        //dfugdn = this.fugcoefDiffN(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
        return new double[2];
    }

    public double logfugcoefdNi(PhaseInterface phase, int k) {
        return 0.0;
    }

    public double getdfugdt() {
        return dfugdt;
    }

    public double getdfugdp() {
        return dfugdp;
    }

    public void setdfugdt(double val) {
        dfugdt = val;
    }

    public void setdfugdp(double val) {
        dfugdp = val;
    }

    public void setdfugdn(int i, double val) {
        dfugdn[i] = val;
    }

    public void setdfugdx(int i, double val) {
        dfugdx[i] = val;
    }

    public double getAcentricFactor() {
        return acentricFactor;
    }

    public double getdfugdx(int i) {
        return dfugdx[i];
    }

    public double getdfugdn(int i) {
        return dfugdn[i];
    }

    public int getIndex() {
        return index;
    }

    public int getComponentNumber() {
        return componentNumber;
    }

    public final double getGibbsEnergy(double temperature, double pressure) {
        return getEnthalpy(temperature) - temperature * getEntropy(temperature, pressure);
    }

    public final double getChemicalPotentialIdealReference(PhaseInterface phase) {
        return (getHID(phase.getTemperature()) - phase.getTemperature() * getIdEntropy(phase.getTemperature()));
    }

    public final double getChemicalPotential(double temperature, double pressure) {
        return getGibbsEnergy(temperature, pressure) / numberOfMolesInPhase;
    }

    public double getChemicalPotential(PhaseInterface phase) {
        return getGibbsEnergy(phase.getTemperature(), phase.getPressure()) / numberOfMolesInPhase;
        // return getGresTV;

    }

    public final double getFugacitydN(int i, PhaseInterface phase) {
        double tempFug = 0.0;
        if (i == componentNumber) {
            tempFug = 1.0 / getNumberOfMolesInPhase();
        }
        return getdfugdn(i) + tempFug - 1.0 / phase.getNumberOfMolesInPhase();
    }

    public final double getChemicalPotentialdNTV(int i, PhaseInterface phase) {
        return getChemicalPotentialdN(i, phase) - getVoli() * phase.getComponent(i).getVoli() * phase.getdPdVTn();
    }

    public final double getChemicalPotentialdN(int i, PhaseInterface phase) {
        return R * phase.getTemperature() * getFugacitydN(i, phase);
    }

    public final double getChemicalPotentialdP() {
        return voli;
    }

    public final double getChemicalPotentialdT(PhaseInterface phase) {
        return -getEntropy(phase.getTemperature(), phase.getPressure()) / numberOfMolesInPhase;
    }

    public final double getChemicalPotentialdV(PhaseInterface phase) {
        return getChemicalPotentialdP() * phase.getdPdVTn();
    }

    public final double getChemicalPotentialdP(int i, PhaseInterface phase) {
        return R * phase.getTemperature() * getFugacitydN(i, phase);
    }

    public void setComponentNumber(int numb) {
        componentNumber = numb;
    }

    public double getAntoineVaporPressure(double temp) {
        if (Math.abs(AntoineE) > 1e-12) {
            return Math.exp(AntoineA + AntoineB / temp + AntoineC * Math.log(temp) + AntoineD * Math.pow(temp, AntoineE)) / 100000;
        } else if (Math.abs(AntoineD) <= 0.000000001) {
            return (Math.exp(AntoineA - (AntoineB / (temp + AntoineC))));
        } else if ((AntoineD - 1000) < 1e-10) {
            return (Math.pow(10, AntoineA - (AntoineB / (temp + AntoineC))));
        } else {
            double x = 1 - (temp / criticalTemperature);
            return (Math.exp(Math.pow((1 - x), -1) * (AntoineA * x + AntoineB * Math.pow(x, 1.5) + AntoineC * Math.pow(x, 3) + AntoineD * Math.pow(x, 6))) * criticalPressure);
        }
    }

    public double getLogAntoineVaporPressuredT(double temp) {
        if (AntoineD == 0) {
            return AntoineB / Math.pow(temp + AntoineC, 2.0);
        } else {
            double x = 1 - (temp / criticalTemperature);
            return 0.0;
        }
    }

    public double getAntoineVaporTemperature(double pres) {
        double nyPres = 0, nyTemp = criticalTemperature * 0.7;
        nyPres = getAntoineVaporPressure(nyTemp);
        // if(Math.abs(AntoineE)>0){
        //     System.out.println("Can not calculate Antoine vapor temperature");
        //    return 0;
        // }
        int iter = 0;
        do {
            iter++;
            nyTemp -= (nyPres - pres);

            nyPres = getAntoineVaporPressure(nyTemp);
            //System.out.println("temp Antoine " +nyTemp);
        } while (Math.abs((nyPres - pres) / pres) > 0.001 && iter < 1000);
        return nyTemp;
    }

    public final double getHresTP(double temperature) {
        return R * temperature * (-temperature * getdfugdt());
    }

    public final double getGresTP(double temperature) {
        return R * temperature * (Math.log(getFugasityCoeffisient()));
    }

    public final double getSresTP(double temperature) {
        return (getHresTP(temperature) - getGresTP(temperature)) / temperature;
    }

    public final double getCp0(double temperature) {
        return getCpA() + getCpB() * temperature + getCpC() * Math.pow(temperature, 2) + getCpD() * Math.pow(temperature, 3) + getCpE() * Math.pow(temperature, 4);
    }

    public final double getCv0(double temperature) {
        return getCpA() + getCpB() * temperature + getCpC() * Math.pow(temperature, 2) + getCpD() * Math.pow(temperature, 3) + getCpE() * Math.pow(temperature, 4) - R;
    }

    //integralet av Cp0 mhp T
    public final double getHID(double T) {
        return 0 * getIdealGasEnthalpyOfFormation() + (getCpA() * T + 1.0 / 2.0 * getCpB() * T * T + 1.0 / 3.0 * getCpC() * T * T * T + 1.0 / 4.0 * getCpD() * T * T * T * T) + 1.0 / 5.0 * getCpE() * T * T * T * T * T
                - (getCpA() * referenceTemperature + 1.0 / 2.0 * getCpB() * referenceTemperature * referenceTemperature + 1.0 / 3.0 * getCpC() * referenceTemperature * referenceTemperature * referenceTemperature + 1.0 / 4.0 * getCpD() * referenceTemperature * referenceTemperature * referenceTemperature * referenceTemperature + 1.0 / 5.0 * getCpE() * referenceTemperature * referenceTemperature * referenceTemperature * referenceTemperature * referenceTemperature);
    }

    /**
     * @param temperature
     * @return enthalpy The function gives the total enthalpy for the component
     * in the specific phase [J]
     */
    public final double getEnthalpy(double temperature) {
        return getHID(temperature) * numberOfMolesInPhase + getHresTP(temperature) * numberOfMolesInPhase;
    }

    public double getIdEntropy(double temperature) {
        return (getCpE() * temperature * temperature * temperature * temperature / 4.0 + getCpD() * temperature * temperature * temperature / 3.0 + getCpC() * temperature * temperature / 2.0 + getCpB() * temperature + getCpA() * Math.log(temperature) - getCpE() * referenceTemperature * referenceTemperature * referenceTemperature * referenceTemperature / 4.0 - getCpD() * referenceTemperature * referenceTemperature * referenceTemperature / 3.0 - getCpC() * referenceTemperature
                * referenceTemperature / 2.0 - getCpB() * referenceTemperature - getCpA() * Math.log(referenceTemperature));
    }

    public double getEntropy(double temperature, double pressure) {
        if (x < 1e-100) {
            return 0.0;
        }
        return numberOfMolesInPhase * (getIdEntropy(temperature) - (R * Math.log(pressure / referencePressure)) - R * Math.log(x)) + getSresTP(temperature) * numberOfMolesInPhase; // 1 bør være Z
    }

    public final String getName() {
        return componentName;
    }

    public void setAcentricFactor(double val) {
        acentricFactor = val;
        getAtractiveTerm().init();
    }

    public void setRacketZ(double val) {
        racketZ = val;
    }

    public void setAtractiveTerm(int i) {
        atractiveTermNumber = i;
    }

    public AtractiveTermInterface getAtractiveTerm() {
        return null;
    }

    public final double[] getSchwartzentruberParams() {
        return schwartzentruberParams;
    }

    public final void setSchwartzentruberParams(int i, double param) {
        schwartzentruberParams[i] = param;
    }

    public final double[] getTwuCoonParams() {
        return TwuCoonParams;
    }

    public final void setTwuCoonParams(int i, double param) {
        TwuCoonParams[i] = param;
    }

    public double fugcoefDiffPresNumeric(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        double temp1 = 0.0, temp2 = 0.0;
        double dp = phase.getPressure() / 1.0e5;
        temp1 = phase.getComponents()[componentNumber].getFugasityCoeffisient();
        phase.setPressure(phase.getPressure() - dp);
        phase.init(numberOfMolesInPhase, numberOfComponents, 1, phase.getPhaseType(), phase.getBeta());
        phase.getComponents()[componentNumber].fugcoef(phase);
        temp2 = phase.getComponents()[componentNumber].getFugasityCoeffisient();
        phase.setPressure(phase.getPressure() + dp);
        phase.init(numberOfMolesInPhase, numberOfComponents, 1, phase.getPhaseType(), phase.getBeta());
        phase.getComponents()[componentNumber].fugcoef(phase);
        dfugdp = (Math.log(temp1) - Math.log(temp2)) / dp;
        return dfugdp;
    }

    public double fugcoefDiffTempNumeric(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        double temp1 = 0.0, temp2 = 0.0;
        double dt = phase.getTemperature() / 1.0e6;
        temp1 = phase.getComponents()[componentNumber].getFugasityCoeffisient();
        phase.setTemperature(phase.getTemperature() - dt);
        phase.init(numberOfMolesInPhase, numberOfComponents, 1, phase.getPhaseType(), phase.getBeta());
        phase.getComponents()[componentNumber].fugcoef(phase);
        temp2 = phase.getComponents()[componentNumber].getFugasityCoeffisient();
        //        phase.setTemperature(phase.getTemperature()+dt);
        //        System.out.println("temp " + phase.getTemperature());
        //        phase.init(numberOfMolesInPhase, numberOfComponents, 1,phase.getPhaseType(), phase.getBeta());
        //        phase.getComponents()[componentNumber].fugcoef(phase, numberOfComponents, phase.getTemperature(), phase.getPressure());
        dfugdt = (Math.log(temp1) - Math.log(temp2)) / dt;
        return dfugdt;
    }

    public final double getIonicDiameter() {
        if (ionicCharge < 0) {
            return paulingAnionicDiameter;
        } else if (ionicCharge > 0) {
            return stokesCationicDiameter;
        } else {
            return lennardJonesMolecularDiameter;
        }
    }

    /**
     * Getter for property stokesCationicDiameter.
     *
     * @return Value of property stokesCationicDiameter.
     */
    public double getStokesCationicDiameter() {
        return stokesCationicDiameter;
    }

    /**
     * Setter for property stokesCationicDiameter.
     *
     * @param stokesCationicDiameter New value of property
     * stokesCationicDiameter.
     */
    public void setStokesCationicDiameter(double stokesCationicDiameter) {
        this.stokesCationicDiameter = stokesCationicDiameter;
    }

    /**
     * Getter for property paulingAnionicDiameter.
     *
     * @return Value of property paulingAnionicDiameter.
     */
    public final double getPaulingAnionicDiameter() {
        return paulingAnionicDiameter;
    }

    /**
     * Setter for property paulingAnionicDiameter.
     *
     * @param paulingAnionicDiameter New value of property
     * paulingAnionicDiameter.
     */
    public void setPaulingAnionicDiameter(double paulingAnionicDiameter) {
        this.paulingAnionicDiameter = paulingAnionicDiameter;
    }

    /**
     * Getter for property logFugasityCoeffisient.
     *
     * @return Value of property logFugasityCoeffisient.
     */
    public final double getLogFugasityCoeffisient() {
        return logFugasityCoeffisient;
    }

    /**
     * Getter for property atractiveTermNumber.
     *
     * @return Value of property atractiveTermNumber.
     */
    public final int getAtractiveTermNumber() {
        return atractiveTermNumber;
    }

    public double getVoli() {
        return voli;
    }

    public void setVoli(double molarVol) {
        voli = molarVol;
    }

    /**
     * Indexed getter for property matiascopemanParams.
     *
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
     */
    public final double getMatiascopemanParams(int index) {
        return matiascopemanParams[index];
    }

    /**
     * Getter for property matiascopemanParams.
     *
     * @return Value of property matiascopemanParams.
     */
    public final double[] getMatiascopemanParams() {
        return matiascopemanParams;
    }

    public final double[] getMatiascopemanParamsPR() {
        return matiascopemanParamsPR;
    }

    public void setMatiascopemanParamsPR(int index, double matiascopemanParams) {
        this.matiascopemanParamsPR[index] = matiascopemanParams;
    }

    /**
     * Indexed setter for property matiascopemanParams.
     *
     * @param index Index of the property.
     * @param matiascopemanParams New value of the property at
     * <CODE>index</CODE>.
     */
    public void setMatiascopemanParams(int index, double matiascopemanParams) {
        this.matiascopemanParams[index] = matiascopemanParams;
    }

    /**
     * Setter for property matiascopemanParams.
     *
     * @param matiascopemanParams New value of property matiascopemanParams.
     */
    public void setMatiascopemanParams(double[] matiascopemanParams) {
        this.matiascopemanParams = matiascopemanParams;
    }

    public void setFugacityCoefficient(double val) {
        fugasityCoeffisient = val;
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
    }

    /**
     * Getter for property numberOfAssociationSites.
     *
     * @return Value of property numberOfAssociationSites.
     */
    public final int getNumberOfAssociationSites() {
        return numberOfAssociationSites;
    }

    /**
     * Setter for property numberOfAssociationSites.
     *
     * @param numberOfAssociationSites New value of property
     * numberOfAssociationSites.
     */
    public void setNumberOfAssociationSites(int numberOfAssociationSites) {
        this.numberOfAssociationSites = numberOfAssociationSites;
    }

    public void seta(double a) {
        System.out.println("no method set a");
    }

    public void setb(double b) {
        System.out.println("no method set b");
    }

    /**
     * Getter for property associationVolume.
     *
     * @return Value of property associationVolume.
     */
    public final double getAssociationVolume() {
        return associationVolume;
    }

    /**
     * Setter for property associationVolume.
     *
     * @param associationVolume New value of property associationVolume.
     */
    public void setAssociationVolume(double associationVolume) {
        this.associationVolume = associationVolume;
    }

    /**
     * Getter for property associationEnergy.
     *
     * @return Value of property associationEnergy.
     */
    public final double getAssociationEnergy() {
        return associationEnergy;
    }

    /**
     * Setter for property associationEnergy.
     *
     * @param associationEnergy New value of property associationEnergy.
     */
    public void setAssociationEnergy(double associationEnergy) {
        this.associationEnergy = associationEnergy;
    }

    /**
     * Getter for property normalBoilingPoint.
     *
     * @return Value of property normalBoilingPoint.
     */
    public double getNormalBoilingPoint() {
        return normalBoilingPoint;
    }

    /**
     * Setter for property normalBoilingPoint.
     *
     * @param normalBoilingPoint New value of property normalBoilingPoint.
     */
    public void setNormalBoilingPoint(double normalBoilingPoint) {
        this.normalBoilingPoint = normalBoilingPoint;
    }

    /**
     * Getter for property standardDensity.
     *
     * @return Value of property standardDensity.
     */
    public double getStandardDensity() {
        return standardDensity;
    }

    /**
     * Setter for property standardDensity.
     *
     * @param standardDensity New value of property standardDensity.
     */
    public void setStandardDensity(double standardDensity) {
        this.standardDensity = standardDensity;
    }

    /**
     * Getter for property AntoineASolid.
     *
     * @return Value of property AntoineASolid.
     */
    public double getAntoineASolid() {
        return AntoineASolid;
    }

    /**
     * Setter for property AntoineASolid.
     *
     * @param AntoineASolid New value of property AntoineASolid.
     */
    public void setAntoineASolid(double AntoineASolid) {
        this.AntoineASolid = AntoineASolid;
    }

    /**
     * Getter for property AntoineBSolid.
     *
     * @return Value of property AntoineBSolid.
     */
    public double getAntoineBSolid() {
        return AntoineBSolid;
    }

    /**
     * Setter for property AntoineBSolid.
     *
     * @param AntoineBSolid New value of property AntoineBSolid.
     */
    public void setAntoineBSolid(double AntoineBSolid) {
        this.AntoineBSolid = AntoineBSolid;
    }

    /**
     * Getter for property AntoineBSolid.
     *
     * @return Value of property AntoineBSolid.
     */
    public double getAntoineCSolid() {
        return AntoineBSolid;
    }

    /**
     * Setter for property AntoineBSolid.
     *
     * @param AntoineBSolid New value of property AntoineBSolid.
     */
    public void setAntoineCSolid(double AntoineCSolid) {
        this.AntoineCSolid = AntoineCSolid;
    }

    public final double getSolidVaporPressure(double temperature) {
        if (Math.abs(AntoineCSolid) < 1e-10) {
            return Math.exp(AntoineASolid + AntoineBSolid / temperature);
        } else {
            return Math.pow(10.0, AntoineASolid - AntoineBSolid / (temperature + AntoineCSolid));
        }
    }

    public final double getSolidVaporPressuredT(double temperature) {
        if (Math.abs(AntoineCSolid) < 1e-10) {
            return -AntoineBSolid / (temperature * temperature) * Math.exp(AntoineASolid + AntoineBSolid / temperature);
        } else {
            return AntoineBSolid * Math.log(10) * Math.pow((1 / 10), (AntoineASolid - AntoineBSolid / (temperature + AntoineCSolid))) * Math.pow(10, AntoineASolid) / Math.pow((temperature + AntoineCSolid), 2);
        }

    }

    public final double getSphericalCoreRadius() {
        return sphericalCoreRadius;
    }

    /**
     * Setter for property componentName.
     *
     * @param componentName New value of property componentName.
     */
    public void setComponentName(java.lang.String componentName) {
        this.componentName = componentName;
    }

    /**
     * Setter for property lennardJonesEnergyParameter.
     *
     * @param lennardJonesEnergyParameter New value of property
     * lennardJonesEnergyParameter.
     *
     */
    public void setLennardJonesEnergyParameter(double lennardJonesEnergyParameter) {
        this.lennardJonesEnergyParameter = lennardJonesEnergyParameter;
    }

    /**
     * Setter for property lennardJonesMolecularDiameter.
     *
     * @param lennardJonesMolecularDiameter New value of property
     * lennardJonesMolecularDiameter.
     *
     */
    public void setLennardJonesMolecularDiameter(double lennardJonesMolecularDiameter) {
        this.lennardJonesMolecularDiameter = lennardJonesMolecularDiameter;
    }

    /**
     * Setter for property sphericalCoreRadius.
     *
     * @param sphericalCoreRadius New value of property sphericalCoreRadius.
     *
     */
    public void setSphericalCoreRadius(double sphericalCoreRadius) {
        this.sphericalCoreRadius = sphericalCoreRadius;
    }

    /**
     * Getter for property calcActivity.
     *
     * @return Value of property calcActivity.
     *
     */
    public boolean calcActivity() {
        return calcActivity != 0;
    }

    /**
     * Getter for property isTBPfraction.
     *
     * @return Value of property isTBPfraction.
     *
     */
    public boolean isIsTBPfraction() {
        return isTBPfraction;
    }

    public boolean isHydrocarbon() {
        return isIsTBPfraction() || isPlusFraction || componentType.equals("HC");
    }

    /**
     * Setter for property isTBPfraction.
     *
     * @param isTBPfraction New value of property isTBPfraction.
     *
     */
    public void setIsTBPfraction(boolean isTBPfraction) {
        setIsAllTypesFalse();
        this.isTBPfraction = isTBPfraction;
    }

    protected void setIsAllTypesFalse() {
        this.isTBPfraction = false;
        this.isPlusFraction = false;
        this.isNormalComponent = false;
        this.isIon = false;
    }

    /**
     * Getter for property isPlusFraction.
     *
     * @return Value of property isPlusFraction.
     *
     */
    public boolean isIsPlusFraction() {
        return isPlusFraction;
    }

    /**
     * Setter for property isPlusFraction.
     *
     * @param isPlusFraction New value of property isPlusFraction.
     *
     */
    public void setIsPlusFraction(boolean isPlusFraction) {
        setIsAllTypesFalse();
        this.isPlusFraction = isPlusFraction;
    }

    /**
     * Getter for property isNormalComponent.
     *
     * @return Value of property isNormalComponent.
     *
     */
    public boolean isIsNormalComponent() {
        return isNormalComponent;
    }

    public boolean isInert() {
        return componentType.equals("inert");
    }

    /**
     * Setter for property isNormalComponent.
     *
     * @param isNormalComponent New value of property isNormalComponent.
     *
     */
    public void setIsNormalComponent(boolean isNormalComponent) {
        setIsAllTypesFalse();
        this.isNormalComponent = isNormalComponent;
    }

    /**
     * Getter for property isIon.
     *
     * @return Value of property isIon.
     *
     */
    public boolean isIsIon() {
        return isIon;
    }

    /**
     * Setter for property isIon.
     *
     * @param isIon New value of property isIon.
     *
     */
    public void setIsIon(boolean isIon) {
        setIsAllTypesFalse();
        this.isIon = isIon;
    }

    /**
     * Setter for property normalLiquidDensity.
     *
     * @param normalLiquidDensity New value of property normalLiquidDensity.
     *
     */
    public void setNormalLiquidDensity(double normalLiquidDensity) {
        this.normalLiquidDensity = normalLiquidDensity;
    }

    /**
     * Setter for property molarMass.
     *
     * @param molarMass New value of property molarMass.
     *
     */
    public void setMolarMass(double molarMass) {
        this.molarMass = molarMass;
    }

    /**
     * Getter for property solidCheck.
     *
     * @return Value of property solidCheck.
     *
     */
    public final boolean doSolidCheck() {
        return solidCheck;
    }

    /**
     * Setter for property solidCheck.
     *
     * @param solidCheck New value of property solidCheck.
     *
     */
    public void setSolidCheck(boolean solidCheck) {
        this.solidCheck = solidCheck;
    }

    /**
     * Getter for property associationScheme.
     *
     * @return Value of property associationScheme.
     *
     */
    public java.lang.String getAssociationScheme() {
        return associationScheme;
    }

    /**
     * Setter for property associationScheme.
     *
     * @param associationScheme New value of property associationScheme.
     *
     */
    public void setAssociationScheme(java.lang.String associationScheme) {
        this.associationScheme = associationScheme;
    }

    /**
     * Getter for property componentType.
     *
     * @return Value of property componentType.
     *
     */
    public java.lang.String getComponentType() {
        if (isTBPfraction) {
            componentType = "TBP";
        } else if (isPlusFraction) {
            componentType = "plus";
        } else if (isNormalComponent) {
            componentType = "normal";
        } else if (isIon) {
            componentType = "ion";
        }
        return componentType;
    }

    /**
     * Getter for property Henrys Coeffisient. Unit is bar. ln H = C1 + C2/T +
     * C3lnT + C4*T
     *
     * @return Value of property componentType.
     *
     */
    public double getHenryCoef(double temperature) {
        //System.out.println("henry " + Math.exp(henryCoefParameter[0]+henryCoefParameter[1]/temperature+henryCoefParameter[2]*Math.log(temperature)+henryCoefParameter[3]*temperature)*100*0.01802);
        return Math.exp(henryCoefParameter[0] + henryCoefParameter[1] / temperature + henryCoefParameter[2] * Math.log(temperature) + henryCoefParameter[3] * temperature) * 0.01802 * 100;
    }

    public double getHenryCoefdT(double temperature) {
        return getHenryCoef(temperature) * (-henryCoefParameter[1] / (temperature * temperature) + henryCoefParameter[2] / temperature + henryCoefParameter[3]);
    }

    /**
     * Getter for property henryCoefParameter.
     *
     * @return Value of property henryCoefParameter.
     *
     */
    public double[] getHenryCoefParameter() {
        return this.henryCoefParameter;
    }

    /**
     * Setter for property henryCoefParameter.
     *
     * @param henryCoefParameter New value of property henryCoefParameter.
     *
     */
    public void setHenryCoefParameter(double[] henryCoefParameter) {
        this.henryCoefParameter = henryCoefParameter;
    }

    /**
     * Getter for property matiascopemanSolidParams.
     *
     * @return Value of property matiascopemanSolidParams.
     *
     */
    public double[] getMatiascopemanSolidParams() {
        return this.matiascopemanSolidParams;
    }

    /**
     * Setter for property matiascopemanSolidParams.
     *
     * @param matiascopemanSolidParams New value of property
     * matiascopemanSolidParams.
     *
     */
    public void setMatiascopemanSolidParams(double[] matiascopemanSolidParams) {
        this.matiascopemanSolidParams = matiascopemanSolidParams;
    }

    public double getPureComponentCpSolid(double temperature) {
        // unit J/mol*K DIPPR function
        return 1. / 1000.0 * (CpSolid[0] + CpSolid[1] * temperature + CpSolid[2] * Math.pow(temperature, 2.0) + CpSolid[3] * Math.pow(temperature, 3.0) + CpSolid[4] * Math.pow(temperature, 4.0));
    }

    // A^2/(1-Tr)+B-2*A*C*(1-Tr)-A*D*(1-Tr)^2-C^2*(1-Tr)^3/3-C*D*(1-Tr)^4/2-D^2*(1-Tr)^5/5
    public double getPureComponentCpLiquid(double temperature) {
        // unit J/mol*K DIPPR function
        return 1. / 1000.0 * (CpLiquid[0] + CpLiquid[1] * temperature + CpLiquid[2] * Math.pow(temperature, 2.0) + CpLiquid[3] * Math.pow(temperature, 3.0) + CpLiquid[4] * Math.pow(temperature, 4.0));

    }

    /**
     * Setter for property criticalVolume.
     *
     * @param criticalVolume New value of property criticalVolume.
     *
     */
    public void setCriticalVolume(double criticalVolume) {
        this.criticalVolume = criticalVolume;
    }

    public double getCriticalViscosity() {
        return criticalViscosity;
    }

    /**
     * Setter for property criticalViscosity.
     *
     * @param criticalViscosity New value of property criticalViscosity.
     *
     */
    public void setCriticalViscosity(double criticalViscosity) {
        this.criticalViscosity = criticalViscosity;
    }

    // return mol/litre
    public double getMolarity(PhaseInterface phase) {
        return x * 1.0 / (phase.getMolarVolume() * 1e-5) / 1e3;
    }

    // return mol/kg
    public double getMolality(PhaseInterface phase) {
        return getMolarity(phase) / (phase.getDensity() / 1.0e3);
    }

    public boolean isHydrateFormer() {
        return isIsHydrateFormer();
    }

    public void setIsHydrateFormer(boolean isHydrateFormer) {
        this.isHydrateFormer = isHydrateFormer;
    }

    public double getmSAFTi() {
        return mSAFTi;
    }

    public void setmSAFTi(double mSAFTi) {
        this.mSAFTi = mSAFTi;
    }

    public double getSigmaSAFTi() {
        return sigmaSAFTi;
    }

    public void setSigmaSAFTi(double sigmaSAFTi) {
        this.sigmaSAFTi = sigmaSAFTi;
    }

    public double getEpsikSAFT() {
        return epsikSAFT;
    }

    public void setEpsikSAFT(double epsikSAFT) {
        this.epsikSAFT = epsikSAFT;
    }

    public double getAssociationVolumeSAFT() {
        return associationVolumeSAFT;
    }

    public void setAssociationVolumeSAFT(double associationVolumeSAFT) {
        this.associationVolumeSAFT = associationVolumeSAFT;
    }

    public double getAssociationEnergySAFT() {
        return associationEnergySAFT;
    }

    public void setAssociationEnergySAFT(double associationEnergySAFT) {
        this.associationEnergySAFT = associationEnergySAFT;
    }

    public double getCriticalCompressibilityFactor() {
        return criticalCompressibilityFactor;
    }

    public void setCriticalCompressibilityFactor(double criticalCompressibilityFactor) {
        this.criticalCompressibilityFactor = criticalCompressibilityFactor;
    }

    public double getSurfaceTenisionInfluenceParameter(double temperature) {
        return 1.0;
    }

    public void setSurfTensInfluenceParam(int factNum, double val) {
        surfTensInfluenceParam[factNum] = val;
    }

    public double getSurfTensInfluenceParam(int factNum) {
        return surfTensInfluenceParam[factNum];
    }

    /**
     * @return the waxFormer
     */
    public boolean isWaxFormer() {
        return waxFormer;
    }

    /**
     * @param waxFormer the waxFormer to set
     */
    public void setWaxFormer(boolean waxFormer) {
        this.waxFormer = waxFormer;
    }

    /**
     * @param heatOfFusion the heatOfFusion to set
     */
    public void setHeatOfFusion(double heatOfFusion) {
        this.heatOfFusion = heatOfFusion;
    }

    /**
     * @param triplePointTemperature the triplePointTemperature to set
     */
    public void setTriplePointTemperature(double triplePointTemperature) {
        this.triplePointTemperature = triplePointTemperature;
    }

    /**
     * @param componentType the componentType to set
     */
    public void setComponentType(String componentType) {
        this.componentType = componentType;
        if (componentType.equals("TBP")) {
            setIsTBPfraction(true);
        }
    }

    /**
     * @return the isHydrateFormer
     */
    public boolean isIsHydrateFormer() {
        return isHydrateFormer;
    }

    /**
     * @return the referenceEnthalpy
     */
    public double getReferenceEnthalpy() {
        return referenceEnthalpy;
    }

    /**
     * @param referenceEnthalpy the referenceEnthalpy to set
     */
    public void setReferenceEnthalpy(double referenceEnthalpy) {
        this.referenceEnthalpy = referenceEnthalpy;
    }

    /**
     * @return the CpA
     */
    public double getCpA() {
        return CpA;
    }

    /**
     * @param CpA the CpA to set
     */
    public void setCpA(double CpA) {
        this.CpA = CpA;
    }

    /**
     * @return the CpB
     */
    public double getCpB() {
        return CpB;
    }

    /**
     * @param CpB the CpB to set
     */
    public void setCpB(double CpB) {
        this.CpB = CpB;
    }

    /**
     * @return the CpC
     */
    public double getCpC() {
        return CpC;
    }

    /**
     * @param CpC the CpC to set
     */
    public void setCpC(double CpC) {
        this.CpC = CpC;
    }

    /**
     * @return the CpD
     */
    public double getCpD() {
        return CpD + 1e-10;
    }

    /**
     * @param CpD the CpD to set
     */
    public void setCpD(double CpD) {
        this.CpD = CpD;
    }

    /**
     * @return the formulae
     */
    public String getFormulae() {
        return formulae;
    }

    /**
     * @param formulae the formulae to set
     */
    public void setFormulae(String formulae) {
        this.formulae = formulae;
    }

    /**
     * @return the CASnumber
     */
    public String getCASnumber() {
        return CASnumber;
    }

    /**
     * @param CASnumber the CASnumber to set
     */
    public void setCASnumber(String CASnumber) {
        this.CASnumber = CASnumber;
    }

    /**
     * @return the orginalNumberOfAssociationSites
     */
    public int getOrginalNumberOfAssociationSites() {
        return orginalNumberOfAssociationSites;
    }

    public double getdrhodN() {
        return molarMass;
    }

    /**
     * @return the CpE
     */
    public double getCpE() {
        return CpE;
    }

    /**
     * @param CpE the CpE to set
     */
    public void setCpE(double CpE) {
        this.CpE = CpE;
    }

    /**
     * @return the racketZCPA
     */
    public double getRacketZCPA() {
        return racketZCPA;
    }

    /**
     * @param racketZCPA the racketZCPA to set
     */
    public void setRacketZCPA(double racketZCPA) {
        this.racketZCPA = racketZCPA;
    }

    /**
     * @return the volumeCorrectionT
     */
    public double getVolumeCorrectionT() {
        return volumeCorrectionT;
    }

    /**
     * @param volumeCorrectionT the volumeCorrectionT to set
     */
    public void setVolumeCorrectionT(double volumeCorrectionT) {
        this.volumeCorrectionT = volumeCorrectionT;
    }

    /**
     * @return the volumeCorrectionT_CPA
     */
    public double getVolumeCorrectionT_CPA() {
        return volumeCorrectionT_CPA;
    }

    /**
     * @param volumeCorrectionT_CPA the volumeCorrectionT_CPA to set
     */
    public void setVolumeCorrectionT_CPA(double volumeCorrectionT_CPA) {
        this.volumeCorrectionT_CPA = volumeCorrectionT_CPA;
    }

    /**
     * @param idealGasEnthalpyOfFormation the idealGasEnthalpyOfFormation to set
     */
    public void setIdealGasEnthalpyOfFormation(double idealGasEnthalpyOfFormation) {
        this.idealGasEnthalpyOfFormation = idealGasEnthalpyOfFormation;
    }
}
