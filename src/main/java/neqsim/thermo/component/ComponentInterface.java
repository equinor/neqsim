/*
 * ComponentInterface.java
 *
 * Created on 8. april 2000, 23:15
 */
package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.atomElement.Element;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * @author  Even Solbraa
 * @version
 */
public interface ComponentInterface extends ThermodynamicConstantsInterface, Cloneable {

    public boolean isInert();

    public void setIdealGasEnthalpyOfFormation(double idealGasEnthalpyOfFormation);

    public String getFormulae();

    public double getVolumeCorrectionT();

    public void setVolumeCorrectionT(double volumeCorrectionT);

    public String getCASnumber();

    public double getPureComponentCpLiquid(double temperature);

    public double getPureComponentCpSolid(double temperature);

    public double getdrhodN();

    public double getVolumeCorrectionT_CPA();

    /**
     * method to return flow rate of a component
     *
     * @param  flowunit The unit as a string. Supported units are kg/sec, kg/min,
     *                  m3/sec, m3/min, m3/hr, mole/sec, mole/min, mole/hr
     * @return          flow rate in specified unit
     */
    public double getFlowRate(String flowunit);
    
    /**
     * method to return total flow rate of a component
     *
     * @param  flowunit The unit as a string. Supported units are kg/sec, kg/min,
     *                  mole/sec, mole/min, mole/hr
     * @return          flow rate in specified unit
     */
    public double getTotalFlowRate(String flowunit);

    public void setVolumeCorrectionT_CPA(double volumeCorrectionT_CPA);

    public void setNumberOfAssociationSites(int numb);

    public void setCASnumber(String CASnumber);

    public void setFormulae(String formulae);

    public void insertComponentIntoDatabase(String databaseName);

    public int getOrginalNumberOfAssociationSites();

    public void createComponent(String component_name, double moles, double molesInPhase, int compnumber);

    public double getRacketZCPA();

    public void setRacketZCPA(double racketZCPA);

    public boolean isHydrocarbon();

    public void setComponentName(String componentName);

    public double getChemicalPotentialdP();

    public void setHeatOfFusion(double heatOfFusion);

    public double getChemicalPotentialIdealReference(PhaseInterface phase);

    public double getHeatOfFusion();

    public void setSurfTensInfluenceParam(int factNum, double val);

    public boolean isWaxFormer();

    public void setWaxFormer(boolean waxFormer);

    public double getSurfTensInfluenceParam(int factNum);

    public double getChemicalPotentialdN(int i, PhaseInterface phase);

    public double getChemicalPotential(PhaseInterface phase);

    public double getChemicalPotentialdT(PhaseInterface phase);

    public double getChemicalPotentialdNTV(int i, PhaseInterface phase);

    public void setTriplePointTemperature(double triplePointTemperature);

    public void setComponentType(String componentType);

    public void seta(double a);

    public double getSphericalCoreRadius();

    public void setb(double b);

    public int getNumberOfAssociationSites();

    public java.lang.String getComponentType();

    public double getRate(String unitName);

    public double fugcoef(PhaseInterface phase);

    public double logfugcoefdT(PhaseInterface phase);

    public double logfugcoefdNi(PhaseInterface phase, int k);

    public void setStokesCationicDiameter(double stokesCationicDiameter);

    public double logfugcoefdP(PhaseInterface phase);

    public double[] logfugcoefdN(PhaseInterface phase);

    public void setProperties(ComponentInterface component);

    public double getTripplePointDensity();

    public void setFugacityCoefficient(double val);

    public double getCriticalCompressibilityFactor();

    public void setCriticalCompressibilityFactor(double criticalCompressibilityFactor);

    public void setMolarMass(double molarMass);

    public boolean calcActivity();

    public double getMolality(PhaseInterface phase);

    public double getFugasityCoefficient();

    public void setLennardJonesMolecularDiameter(double lennardJonesMolecularDiameter);

    public void setLennardJonesEnergyParameter(double lennardJonesEnergyParameter);

    public void setSphericalCoreRadius(double sphericalCoreRadius);

    public double getTriplePointPressure();

    public double getTriplePointTemperature();

    public double getMeltingPointTemperature();

    public double getIdealGasEnthalpyOfFormation();

    public void addMolesChemReac(double dn, double totdn);

    public double getIdealGasGibsEnergyOfFormation();

    public void setTC(double val);

    public void setPC(double val);

    public double getDiElectricConstantdTdT(double temperature);

    public double getIdealGasAbsoluteEntropy();

    public double getDiElectricConstantdT(double temperature);

    public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int type);

    public void Finit(PhaseInterface phase, double temperature, double pressure, double totalNumberOfMoles, double beta,
            int numberOfComponents, int type);

    public double getx();

    public double getz();

    public double getK();

    public double getTC();

    public double getNormalBoilingPoint();

    public void setNormalBoilingPoint(double normalBoilingPoint);

    public double getPC();

    public void setComponentNumber(int numb);

    public void setViscosityAssociationFactor(double val);

    public int getIndex();

    public String getReferenceStateType();

    public void setLiquidConductivityParameter(double number, int i);

    public double getLiquidConductivityParameter(int i);

    public double getNormalLiquidDensity();

    public String getComponentName();

    public int getComponentNumber();

    public void addMolesChemReac(double dn);

    public double getHeatOfVapourization(double temp);

    public double getNumberOfmoles();

    public void addMoles(double dn);

    public double getGibbsEnergyOfFormation();

    public double getReferencePotential();

    public double getLogFugasityCoeffisient();

    public void setReferencePotential(double ref);

    public double getNumberOfMolesInPhase();

    public void setNumberOfMolesInPhase(double moles);

    public double getIdEntropy(double temperature);

    public void setx(double newx);

    public void setz(double newz);

    public void setK(double newK);

    public double getDiElectricConstant(double temperature);

    public double getIonicCharge();

    public double getdfugdt();

    public double getdfugdp();

    public double getSolidVaporPressure(double temperature);

    /**
     * @param  temperature
     * @return             ideal gas Cp for the component in the specific phase
     *                     [J/molK]
     */
    public double getCp0(double temperature);

    /**
     * @param  temperature
     * @return             ideal gas Cv for the component in the specific phase
     *                     [J/molK]
     */
    public double getCv0(double temperature);

    public double getHID(double T);

    public double getEnthalpy(double temperature);

    public double getMolarMass();

    public double getLennardJonesMolecularDiameter();

    public double getLennardJonesEnergyParameter();

    public double getEntropy(double temperature, double pressure);

    public double getdfugdx(int i);

    public double getdfugdn(int i);

    public double getHresTP(double temperature);

    public double getGresTP(double temperature);

    public double getSresTP(double temperature);

    public double getFugasityCoeffisient();

    public double getAcentricFactor();

    public void setAtractiveTerm(int i);

    public AtractiveTermInterface getAtractiveTerm();

    public void setNumberOfmoles(double newmoles);

    public double getAntoineVaporPressure(double temp);

    public double getAntoineVaporTemperature(double pres);

    public double getSolidVaporPressuredT(double temperature);

    public double getChemicalPotential(double temperature, double pressure);
    // public double fugcoef(PhaseInterface phase, int numberOfComponents, double
    // temperature, double pressure);
    // public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents,
    // double temperature, double pressure);
    // public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents,
    // double temperature, double pressure);
    // public double[] fugcoefDiffN(PhaseInterface phase, int numberOfComponents,
    // double temperature, double pressure);

    public double getGibbsEnergy(double temperature, double pressure);

    public Object clone();

    public double getDebyeDipoleMoment();

    public double getViscosityCorrectionFactor();

    public double getCriticalVolume();

    public double getRacketZ();

    public String getName();

    public double getLiquidViscosityParameter(int i);

    public int getLiquidViscosityModel();

    public void setAcentricFactor(double val);

    public double getVolumeCorrection();

    public void setRacketZ(double val);

    public void setLiquidViscosityModel(int modelNumber);

    public void setLiquidViscosityParameter(double number, int i);

    public Element getElements();

    public double[] getSchwartzentruberParams();

    public void setSchwartzentruberParams(int i, double param);

    public double[] getTwuCoonParams();

    public void setTwuCoonParams(int i, double param);

    public double getParachorParameter();

    public void setParachorParameter(double parachorParameter);

    public double getPureComponentSolidDensity(double temperature);

    public double getPureComponentLiquidDensity(double temperature);

    public double getChemicalPotentialdV(PhaseInterface phase);

    public double getPureComponentHeatOfVaporization(double temperature);

    public double fugcoefDiffPresNumeric(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure);

    public double fugcoefDiffTempNumeric(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure);

    public void setdfugdt(double val);

    public void setdfugdp(double val);

    public void setdfugdn(int i, double val);

    public void setdfugdx(int i, double val);

    public double getPaulingAnionicDiameter();

    public double getStokesCationicDiameter();

    public int getAtractiveTermNumber();

    public double getVoli();

    public double getAntoineVaporPressuredT(double temp);

    public double[] getMatiascopemanParams();

    public void setMatiascopemanParams(int index, double matiascopemanParams);

    public void setMatiascopemanParams(double[] matiascopemanParams);

    public double getAssociationVolume();

    public void setAssociationVolume(double associationVolume);

    public double getAssociationEnergy();

    public void setAssociationEnergy(double associationEnergy);

    public double getAntoineASolid();

    public void setAntoineASolid(double AntoineASolid);

    public double getAntoineBSolid();

    public void setAntoineBSolid(double AntoineBSolid);

    public boolean isIsTBPfraction();

    public void setIsTBPfraction(boolean isTBPfraction);

    public boolean isIsPlusFraction();

    public void setIsPlusFraction(boolean isPlusFraction);

    public boolean isIsNormalComponent();

    public void setIsNormalComponent(boolean isNormalComponent);

    public boolean isIsIon();

    public void setIsIon(boolean isIon);

    public void setNormalLiquidDensity(double normalLiquidDensity);

    public boolean doSolidCheck();

    public void setSolidCheck(boolean solidCheck);

    public java.lang.String getAssociationScheme();

    public void setAssociationScheme(java.lang.String associationScheme);

    public double getAntoineCSolid();

    public void setAntoineCSolid(double AntoineCSolid);

    public double getCCsolidVaporPressure(double temperature);

    public double getCCsolidVaporPressuredT(double temperature);

    public double getHsub();

    public double[] getHenryCoefParameter();

    public void setHenryCoefParameter(double[] henryCoefParameter);

    public double getHenryCoef(double temperature);

    public double getHenryCoefdT(double temperature);

    public double[] getMatiascopemanSolidParams();

    public void setCriticalVolume(double criticalVolume);

    public double getCriticalViscosity();

    public void setCriticalViscosity(double criticalViscosity);

    public double getMolarity(PhaseInterface phase);

    public boolean isHydrateFormer();

    public void setIsHydrateFormer(boolean isHydrateFormer);

    public double getmSAFTi();

    public void setmSAFTi(double mSAFTi);

    public double getSigmaSAFTi();

    public void setSigmaSAFTi(double sigmaSAFTi);

    public double getEpsikSAFT();

    public void setEpsikSAFT(double epsikSAFT);

    public double getAssociationVolumeSAFT();

    public void setAssociationVolumeSAFT(double associationVolumeSAFT);

    public double getAssociationEnergySAFT();

    public void setAssociationEnergySAFT(double associationEnergySAFT);

    public double getSurfaceTenisionInfluenceParameter(double temperature);

    public double getCpA();

    public void setCpA(double CpA);

    public double getCpB();

    public void setCpB(double CpB);

    public double getCpC();

    public void setCpC(double CpC);

    public double getCpD();

    public void setCpD(double CpD);

    public double getCpE();

    public void setCpE(double CpE);
}
