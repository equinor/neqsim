package neqsim.thermodynamicOperations;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.flashOps.CriticalPointFlash;
import neqsim.thermodynamicOperations.flashOps.PHflash;
import neqsim.thermodynamicOperations.flashOps.PHflashSingleComp;
import neqsim.thermodynamicOperations.flashOps.PHsolidFlash;
import neqsim.thermodynamicOperations.flashOps.PSFlash;
import neqsim.thermodynamicOperations.flashOps.PSflashSingleComp;
import neqsim.thermodynamicOperations.flashOps.PVrefluxflash;
import neqsim.thermodynamicOperations.flashOps.SaturateWithWater;
import neqsim.thermodynamicOperations.flashOps.SolidFlash1;
import neqsim.thermodynamicOperations.flashOps.TPgradientFlash;
import neqsim.thermodynamicOperations.flashOps.TSFlash;
import neqsim.thermodynamicOperations.flashOps.TVflash;
import neqsim.thermodynamicOperations.flashOps.VHflashQfunc;
import neqsim.thermodynamicOperations.flashOps.VUflashQfunc;
import neqsim.thermodynamicOperations.flashOps.calcIonicComposition;
import neqsim.thermodynamicOperations.flashOps.dTPflash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.HCdewPointPressureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.HydrateEquilibriumLine;
import neqsim.thermodynamicOperations.flashOps.saturationOps.HydrateFormationPressureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.HydrateFormationTemperatureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.HydrateInhibitorConcentrationFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.HydrateInhibitorwtFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.SolidComplexTemperatureCalc;
import neqsim.thermodynamicOperations.flashOps.saturationOps.WATcalc;
import neqsim.thermodynamicOperations.flashOps.saturationOps.WaterDewPointEquilibriumLine;
import neqsim.thermodynamicOperations.flashOps.saturationOps.addIonToScaleSaturation;
import neqsim.thermodynamicOperations.flashOps.saturationOps.bubblePointPressureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.bubblePointPressureFlashDer;
import neqsim.thermodynamicOperations.flashOps.saturationOps.bubblePointTemperatureNoDer;
import neqsim.thermodynamicOperations.flashOps.saturationOps.calcSaltSatauration;
import neqsim.thermodynamicOperations.flashOps.saturationOps.checkScalePotential;
import neqsim.thermodynamicOperations.flashOps.saturationOps.constantDutyFlashInterface;
import neqsim.thermodynamicOperations.flashOps.saturationOps.constantDutyPressureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.constantDutyTemperatureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.cricondebarFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.dewPointPressureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.dewPointTemperatureFlashDer;
import neqsim.thermodynamicOperations.flashOps.saturationOps.freezingPointTemperatureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.waterDewPointTemperatureFlash;
import neqsim.thermodynamicOperations.flashOps.saturationOps.waterDewPointTemperatureMultiphaseFlash;
import neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps.CricondenBarFlash;
import neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps.CricondenThermFlash;
import neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps.HPTphaseEnvelope;
import neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps.pTphaseEnvelope;
import neqsim.thermodynamicOperations.phaseEnvelopeOps.reactiveCurves.pLoadingCurve2;
import neqsim.thermodynamicOperations.propertyGenerator.OLGApropertyTableGeneratorWaterStudents;
import neqsim.thermodynamicOperations.propertyGenerator.OLGApropertyTableGeneratorWaterStudentsPH;

/**
 * @author Even Solbraa
 * @version
 */
public class ThermodynamicOperations implements java.io.Serializable, Cloneable {
    private static final long serialVersionUID = 1000;

    private Thread thermoOperationThread = new Thread();
    private OperationInterface operation = null;
    SystemInterface system = null;
    boolean writeFile = false;
    String fileName = null;
    private boolean runAsThread = false;
    protected String[][] resultTable = null;
    static Logger logger = LogManager.getLogger(ThermodynamicOperations.class);

    /**
     * Creates new thermoOps
     */
    public ThermodynamicOperations() {}

    public ThermodynamicOperations(SystemInterface system) {
        this.system = system;
    }

    public void setSystem(SystemInterface system) {
        this.system = system;
    }

    public void TPSolidflash() {
        operation = new SolidFlash1(system);
        getOperation().run();
    }

    /**
     * Method to perform a flash at given temperature, pressure and specified volume The number of
     * moles in the system are changed to match the specified volume.
     *
     * @param volumeSpec is the specified volume
     * @param unit The unit as a string. units supported are m3, litre,
     */
    public void TPVflash(double volumeSpec, String unit) {
        unit = "m3";
        TPflash();
        double startVolume = system.getVolume(unit);
        system.setTotalNumberOfMoles(system.getNumberOfMoles() * volumeSpec / startVolume);
        system.init(3);
    }

    public void TPflash() {
        double flowRate = system.getTotalNumberOfMoles();
        double minimumFlowRate = 1e-50;
        if (flowRate < 1e-3) {
            system.setTotalFlowRate(1.0, "mol/sec");
        }
        operation = new neqsim.thermodynamicOperations.flashOps.TPflash(system,
                system.doSolidPhaseCheck());
        if (!isRunAsThread()) {
            getOperation().run();
        } else {
            run();
        }
        if (flowRate < 1e-3) {
            if (flowRate < minimumFlowRate) {
                system.setTotalNumberOfMoles(minimumFlowRate);
            } else {
                system.setTotalNumberOfMoles(flowRate);
            }
            system.init(2);
        }
    }

    public void saturateWithWater() {
        operation = new SaturateWithWater(system);
        getOperation().run();
    }

    public void TPflash(boolean checkSolids) {
        operation = new neqsim.thermodynamicOperations.flashOps.TPflash(system, checkSolids);
        getOperation().run();
    }

    public SystemInterface TPgradientFlash(double height, double temperature) {
        operation = new TPgradientFlash(system, height, temperature);
        getOperation().run();
        return operation.getThermoSystem();
    }

    public void dTPflash(String[] comps) {
        operation = new dTPflash(system, comps);
        getOperation().run();
    }

    public void chemicalEquilibrium() {
        if (system.isChemicalSystem()) {
            operation = new neqsim.thermodynamicOperations.chemicalEquilibrium.ChemicalEquilibrium(
                    system);
            getOperation().run();
        }
    }

    public void PHflash(double Hspec, int type) {
        if (system.getPhase(0).getNumberOfComponents() == 1) {
            operation = new PHflashSingleComp(system, Hspec, type);
        } else {
            operation = new PHflash(system, Hspec, type);
        }
        getOperation().run();
    }

    /**
     * Method to perform a PH flash calculation
     *
     * @param Hspec is the enthalpy in the specified unit
     * @param enthalpyUnit The unit as a string. units supported are J, J/mol, J/kg and kJ/kg
     */
    public void PHflash(double Hspec, String enthalpyUnit) {
        double conversionFactor = 1.0;
        switch (enthalpyUnit) {
            case "J":
                conversionFactor = 1.0;
                break;
            case "J/mol":
                conversionFactor = 1.0 / system.getTotalNumberOfMoles();
                break;
            case "J/kg":
                conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
                break;
            case "kJ/kg":
                conversionFactor =
                        1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
                break;
        }
        PHflash(Hspec / conversionFactor);
    }

    /**
     * Method to perform a PH flash calculation
     *
     * @param Hspec is the enthalpy in unit Joule to be held constant
     */
    public void PHflash(double Hspec) {
        this.PHflash(Hspec, 0);
    }

    public void PUflash(double Uspec) {
        operation = new neqsim.thermodynamicOperations.flashOps.PUflash(system, Uspec);
        getOperation().run();
    }

    public void PHflash2(double Hspec, int type) {
        operation = new PHflash(system, Hspec, type);
        getOperation().run();
    }

    public void criticalPointFlash() {
        operation = new CriticalPointFlash(system);
        getOperation().run();
    }

    public void PHsolidFlash(double Hspec) {
        operation = new PHsolidFlash(system, Hspec);
        getOperation().run();
    }

    /**
     * Method to perform a PS flash calculation for a specified entropy and pressure
     *
     * @param Sspec is the entropy in the specified unit
     * @param unit The unit as a string. units supported are J/K, J/molK, J/kgK and kJ/kgK
     */
    public void PSflash(double Sspec, String unit) {
        double conversionFactor = 1.0;
        switch (unit) {
            case "J/K":
                conversionFactor = 1.0;
                break;
            case "J/molK":
                conversionFactor = 1.0 / system.getTotalNumberOfMoles();
                break;
            case "J/kgK":
                conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
                break;
            case "kJ/kgK":
                conversionFactor =
                        1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
                break;
        }
        PSflash(Sspec / conversionFactor);
    }

    /**
     * Method to perform a TS flash calculation for a specified entropy and pressure
     *
     * @param Sspec is the entropy in the specified unit
     * @param unit The unit as a string. units supported are J/K, J/molK, J/kgK and kJ/kgK
     */
    public void TSflash(double Sspec, String unit) {
        double conversionFactor = 1.0;
        switch (unit) {
            case "J/K":
                conversionFactor = 1.0;
                break;
            case "J/molK":
                conversionFactor = 1.0 / system.getTotalNumberOfMoles();
                break;
            case "J/kgK":
                conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
                break;
            case "kJ/kgK":
                conversionFactor =
                        1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
                break;
        }
        TSflash(Sspec / conversionFactor);
    }

    public void PSflash(double Sspec) {
        if (system.getPhase(0).getNumberOfComponents() == 1) {
            operation = new PSflashSingleComp(system, Sspec, 0);
        } else {
            operation = new PSFlash(system, Sspec, 0);
        }
        getOperation().run();
    }

    public void TSflash(double Sspec) {
        operation = new TSFlash(system, Sspec);
        getOperation().run();
    }

    public void PSflash2(double Sspec) {
        operation = new PSFlash(system, Sspec, 0);
        getOperation().run();
    }

    public void VSflash(double volume, double entropy, String unitVol, String unitEntropy) {
        double conversionFactorV = 1.0;
        double conversionFactorEntr = 1.0;

        switch (unitVol) {
            case "m3":
                conversionFactorV = 1.0e5;
                break;
        }

        switch (unitEntropy) {
            case "J/K":
                conversionFactorEntr = 1.0;
                break;
            case "J/molK":
                conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles();
                break;
            case "J/kgK":
                conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
                break;
            case "kJ/kgK":
                conversionFactorEntr =
                        1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
                break;
        }
        VSflash(volume * conversionFactorV, entropy / conversionFactorEntr);
    }

    public void VSflash(double volume, double entropy) {
        operation = new neqsim.thermodynamicOperations.flashOps.VSflash(system, volume, entropy);
        getOperation().run();
    }

    public void TVflash(double Vspec, String unit) {
        double conversionFactor = 1.0;
        switch (unit) {
            case "m3":
                conversionFactor = 1.0e5;
                break;
        }
        TVflash(Vspec * conversionFactor);
    }

    public void TVflash(double Vspec) {
        operation = new TVflash(system, Vspec);
        getOperation().run();
    }

    public void PVrefluxFlash(double refluxspec, int refluxPhase) {
        operation = new PVrefluxflash(system, refluxspec, refluxPhase);
        getOperation().run();
    }

    public void VHflash(double Vspec, double Hspec) {
        operation = new VHflashQfunc(system, Vspec, Hspec);
        getOperation().run();
    }

    public void VHflash(double volume, double enthalpy, String unitVol, String unitEnthalpy) {
        double conversionFactorV = 1.0;
        double conversionFactorEntr = 1.0;

        switch (unitVol) {
            case "m3":
                conversionFactorV = 1.0e5;
                break;
        }

        switch (unitEnthalpy) {
            case "J/K":
                conversionFactorEntr = 1.0;
                break;
            case "J/mol":
                conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles();
                break;
            case "J/kg":
                conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
                break;
            case "kJ/kg":
                conversionFactorEntr =
                        1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
                break;
        }
        VHflash(volume * conversionFactorV, enthalpy / conversionFactorEntr);
    }

    public void VUflash(double volume, double energy, String unitVol, String unitEnergy) {
        double conversionFactorV = 1.0;
        double conversionFactorEntr = 1.0;

        switch (unitVol) {
            case "m3":
                conversionFactorV = 1.0e5;
                break;
        }

        switch (unitEnergy) {
            case "J/K":
                conversionFactorEntr = 1.0;
                break;
            case "J/mol":
                conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles();
                break;
            case "J/kg":
                conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
                break;
            case "kJ/kg":
                conversionFactorEntr =
                        1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
                break;
        }
        VUflash(volume * conversionFactorV, energy / conversionFactorEntr);
    }

    public void VUflash(double Vspec, double Uspec) {
        operation = new VUflashQfunc(system, Vspec, Uspec);
        getOperation().run();
    }

    public void bubblePointTemperatureFlash() throws Exception {
        constantDutyFlashInterface operation = new bubblePointTemperatureNoDer(system);
        operation.run();
        if (Double.isNaN(system.getTemperature()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in bubblePointTemperatureFlash() - could not find solution - possible no bubble point exists");
        }
    }

    public void freezingPointTemperatureFlash() throws Exception {
        operation = new freezingPointTemperatureFlash(system);
        getOperation().run();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in freezingPointTemperatureFlash() - could not find solution - possible no freezing point exists");
        }
    }

    public void freezingPointTemperatureFlash(String phaseName) throws Exception {
        operation = new freezingPointTemperatureFlash(system);
        getOperation().run();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in freezingPointTemperatureFlash() - could not find solution - possible no freezing point exists");
        }
    }

    public void waterDewPointTemperatureFlash() throws Exception {
        operation = new waterDewPointTemperatureFlash(system);
        getOperation().run();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in freezingPointTemperatureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void waterDewPointTemperatureMultiphaseFlash() throws Exception {
        operation = new waterDewPointTemperatureMultiphaseFlash(system);
        getOperation().run();
    }

    public void waterPrecipitationTemperature() throws Exception {
        double lowTemperature = 0.0;
        dewPointTemperatureFlash();

        if (system.getTemperature() > lowTemperature) {
            lowTemperature = system.getTemperature();
        }

        // if(lowTemperature<273.15 && system.doSolidPhaseCheck()){
        // hydrateFormationTemperature(0);
        // if(system.getTemperature()>lowTemperature) lowTemperature =
        // system.getTemperature();
        // }
        //
        // if(system.doHydrateCheck()){
        // hydrateFormationTemperature(1);
        // if(system.getTemperature()>lowTemperature) lowTemperature =
        // system.getTemperature();
        // hydrateFormationTemperature(2);
        // if(system.getTemperature()>lowTemperature) lowTemperature =
        // system.getTemperature();
        // }
        ////
        system.setTemperature(lowTemperature);
        // TPflash();

        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in freezingPointTemperatureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void calcSaltSaturation(String saltName) throws Exception {
        operation = new calcSaltSatauration(system, saltName);
        getOperation().run();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in calcSaltSaturation() - could not find solution - possible no dew point exists");
        }
    }

    public void checkScalePotential(int phaseNumber) throws Exception {
        operation = new checkScalePotential(system, phaseNumber);
        getOperation().run();
        resultTable = getOperation().getResultTable();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in checkScalePotential() - could not find solution - possible no dew point exists");
        }
    }

    public void addIonToScaleSaturation(int phaseNumber, String scaleSaltName,
            String nameOfIonToBeAdded) throws Exception {
        operation =
                new addIonToScaleSaturation(system, phaseNumber, scaleSaltName, nameOfIonToBeAdded);
        getOperation().run();
        resultTable = getOperation().getResultTable();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in checkScalePotential() - could not find solution - possible no dew point exists");
        }
    }

    public void hydrateFormationPressure() throws Exception {
        operation = new HydrateFormationPressureFlash(system);
        getOperation().run();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in hydrateFormationPressure() - could not find solution - possible no dew point exists");
        }
    }

    public void calcWAT() throws Exception {
        operation = new WATcalc(system);
        getOperation().run();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in WAT() - could not find solution - possible no dew point exists");
        }
    }

    public void run() {
        setThermoOperationThread(new Thread(operation));
        getThermoOperationThread().start();
    }

    public boolean waitAndCheckForFinishedCalculation(int maxTime) {
        try {
            getThermoOperationThread().join(maxTime);
            getThermoOperationThread().interrupt();
        } catch (Exception e) {
            logger.error("error", e);
        }
        boolean didFinish = !getThermoOperationThread().isInterrupted();
        // getThermoOperationThread().stop();
        return didFinish;
    }

    public void waitToFinishCalculation() {
        try {
            getThermoOperationThread().join();
        } catch (Exception e) {
            logger.error("error", e);
        }
    }

    public void calcSolidComlexTemperature() throws Exception {
        operation = new SolidComplexTemperatureCalc(system);
        getOperation().run();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in WAT() - could not find solution - possible no dew point exists");
        }
    }

    public void calcSolidComlexTemperature(String comp1, String comp2) throws Exception {
        if (operation == null) {
            operation = new SolidComplexTemperatureCalc(system, comp1, comp2);
        }
        getOperation().run();
        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in WAT() - could not find solution - possible no dew point exists");
        }
    }

    public double[] calcImobilePhaseHydrateTemperature(double[] temperature, double[] pressure) {
        double[] hydTemps = new double[temperature.length];
        SystemInterface systemTemp;
        ThermodynamicOperations opsTemp;
        systemTemp = (SystemInterface) system.clone();

        for (int i = 0; i < temperature.length; i++) {
            /*
             * opsTemp = new ThermodynamicOperations(systemTemp);
             * systemTemp.setTemperature(temperature[i]); systemTemp.setPressure(pressure[i]);
             * systemTemp.init(0); systemTemp.display(); try {
             * opsTemp.hydrateFormationTemperature(); } catch (Exception e) {
             * logger.error("error",e); } systemTemp.display(); hydTemps[i] =
             * systemTemp.getTemperature();
             *
             */
            opsTemp = new ThermodynamicOperations(systemTemp);
            systemTemp.setTemperature(temperature[i]);
            systemTemp.setPressure(pressure[i]);

            opsTemp.TPflash();
            systemTemp.display();
            systemTemp = systemTemp.phaseToSystem(0);
        }

        opsTemp = new ThermodynamicOperations(systemTemp);
        systemTemp.setHydrateCheck(true);
        systemTemp.setMixingRule(9);
        try {
            opsTemp.hydrateFormationTemperature();
        } catch (Exception e) {
            logger.error("error", e);
        }
        systemTemp.display();
        return hydTemps;
    }

    public double calcTOLHydrateFormationTemperature() {
        TPflash();

        SystemInterface systemTemp = system.phaseToSystem(0);
        ThermodynamicOperations opsTemp = new ThermodynamicOperations(systemTemp);
        try {
            opsTemp.hydrateFormationTemperature();
        } catch (Exception e) {
            logger.error("error", e);
        }
        systemTemp.display();
        system.setTemperature(systemTemp.getTemperature());
        TPflash();
        return system.getTemperature();
    }

    public void hydrateInhibitorConcentration(String inhibitorName, double hydEqTemperature)
            throws Exception {
        operation = new HydrateInhibitorConcentrationFlash(system, inhibitorName, hydEqTemperature);
        operation.run();
    }

    public void hydrateInhibitorConcentrationSet(String inhibitorName, double wtfrac)
            throws Exception {
        operation = new HydrateInhibitorwtFlash(system, inhibitorName, wtfrac);
        operation.run();
    }

    public void hydrateFormationTemperature(double initialTemperatureGuess) throws Exception {
        system.setTemperature(initialTemperatureGuess);
        operation = new HydrateFormationTemperatureFlash(system);
        for (int i = 0; i < system.getPhase(4).getNumberOfComponents(); i++) {
            ((ComponentHydrate) system.getPhase(4).getComponent(i)).getHydrateStructure();
        }
        if (!isRunAsThread()) {
            getOperation().run();
        } else {
            run();
        }
    }

    public void hydrateFormationTemperature() throws Exception {
        // guessing temperature
        double factor = 1.0;
        if (system.getPhase(0).hasComponent("methanol")) {
            factor -= 2 * system.getPhase(0).getComponent("methanol").getz()
                    / system.getPhase(0).getComponent("water").getz();
        }
        if (system.getPhase(0).hasComponent("MEG")) {
            factor -= 2 * system.getPhase(0).getComponent("MEG").getz()
                    / system.getPhase(0).getComponent("water").getz();
        }
        if (factor < 2) {
            factor = 2;
        }

        system.setTemperature(273.0 + system.getPressure() / 100.0 * 20.0 * factor - 20.0);
        if (system.getTemperature() > 298.15) {
            system.setTemperature(273.0 + 25.0);
        }
        // logger.info("guess hydrate temperature " + system.getTemperature());
        operation = new HydrateFormationTemperatureFlash(system);

        for (int i = 0; i < system.getPhase(4).getNumberOfComponents(); i++) {
            ((ComponentHydrate) system.getPhase(4).getComponent(i)).getHydrateStructure();
        }
        if (!isRunAsThread()) {
            getOperation().run();
        } else {
            run();
        }
        // logger.info("Hydrate structure " + (((ComponentHydrate)
        // system.getPhase(4).getComponent("water")).getHydrateStructure() + 1));
    }

    public void hydrateEquilibriumLine(double minimumPressure, double maximumPressure)
            throws Exception {
        operation = new HydrateEquilibriumLine(system, minimumPressure, maximumPressure);
        if (!isRunAsThread()) {
            getOperation().run();
        } else {
            run();
        }
    }

    public void calcCricoP(double[] cricondenBar, double[] cricondenBarX, double[] cricondenBarY) {
        double phasefraction = 1.0 - 1e-10;

        operation = new CricondenBarFlash(system, fileName, phasefraction, cricondenBar,
                cricondenBarX, cricondenBarY);

        getOperation().run();
    }

    public void calcCricoT(double[] cricondenTherm, double[] cricondenThermX,
            double[] cricondenThermY) {
        double phasefraction = 1.0 - 1e-10;

        operation = new CricondenThermFlash(system, fileName, phasefraction, cricondenTherm,
                cricondenThermX, cricondenThermY);

        getOperation().run();
    }

    public void waterDewPointLine(double minimumPressure, double maximumPressure) throws Exception {
        operation = new WaterDewPointEquilibriumLine(system, minimumPressure, maximumPressure);
        if (!isRunAsThread()) {
            getOperation().run();
        } else {
            run();
        }
    }

    public void hydrateFormationTemperature(int structure) throws Exception {
        system.setTemperature(273.0 + 1.0);
        if (structure == 0) {
            system.setSolidPhaseCheck("water");
            system.setHydrateCheck(true);
            operation = new freezingPointTemperatureFlash(system);
        } else {
            operation = new HydrateFormationTemperatureFlash(system);
        }

        for (int i = 0; i < system.getPhase(4).getNumberOfComponents(); i++) {
            ((ComponentHydrate) system.getPhases()[4].getComponent(i))
                    .setHydrateStructure(structure - 1);
        }
        if (!isRunAsThread()) {
            getOperation().run();
        } else {
            run();
        }

        if (Double.isNaN(system.getTemperature())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in freezingPointTemperatureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public double calcCricondenBar() {
        system.init(0);
        operation = new cricondebarFlash(system);
        // operation = new CricondenBarFlash(system);

        // operation = new cricondenBarTemp1(system);
        operation.run();
        return system.getPressure();
    }

    public void bubblePointPressureFlash() throws Exception {
        system.init(0);
        constantDutyFlashInterface operation = new constantDutyPressureFlash(system);
        system.setBeta(1, 1.0 - 1e-10);
        system.setBeta(0, 1e-10);
        operation.run();
        if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in bubblePointPressureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void constantPhaseFractionPressureFlash(double fraction) throws Exception {
        system.init(0);
        if (fraction < 1e-10) {
            fraction = 1e-10;
        }
        if (fraction > 1.0 - 1e-10) {
            fraction = 1.0 - 1.0e-10;
        }
        constantDutyFlashInterface operation = new constantDutyPressureFlash(system);
        system.setBeta(1, 1.0 - fraction);
        system.setBeta(0, fraction);
        operation.run();
        if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in bubblePointPressureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void constantPhaseFractionTemperatureFlash(double fraction) throws Exception {
        system.init(0);
        if (fraction < 1e-10) {
            fraction = 1e-10;
        }
        if (fraction > 1.0 - 1e-10) {
            fraction = 1.0 - 1.0e-10;
        }
        constantDutyFlashInterface operation = new constantDutyTemperatureFlash(system);
        system.setBeta(1, fraction);
        system.setBeta(0, fraction);
        operation.run();
        if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in bubblePointPressureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void bubblePointPressureFlash(boolean derivatives) throws Exception {
        constantDutyFlashInterface operation = null;
        if (derivatives == true) {
            operation = new bubblePointPressureFlashDer(system);
        } else {
            operation = new bubblePointPressureFlash(system);
        }
        operation.run();
        if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in bubblePointPressureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void dewPointMach(String componentName, String specification, double spec)
            throws Exception {
        int componentNumber = system.getPhase(0).getComponent(componentName).getComponentNumber();

        double dn = 0;
        if (system.getPhase(0).hasComponent(componentName)) {
            dn = system.getNumberOfMoles() / 1.0e6;
            system.addComponent(componentName, dn);
        } else {
            throw new neqsim.util.exception.IsNaNException(
                    "error in dewPointMach(String componentName) - specified component is not precent in mixture: "
                            + componentName);
        }
        double newTemperature = system.getTemperature(), oldTemperature = newTemperature;
        int iterations = 0;
        if (specification.equals("dewPointTemperature")) {
            // logger.info("new temperature " + newTemperature);
            do {
                iterations++;
                system.init(0);
                dewPointTemperatureFlash();
                newTemperature = system.getTemperature();
                // logger.info("new temperature " + newTemperature);
                double oldMoles = system.getPhase(0).getComponent(componentName).getNumberOfmoles();
                if (iterations > 1) {
                    system.addComponent(componentName, -(iterations / (30.0 + iterations))
                            * (newTemperature - spec) / ((newTemperature - oldTemperature) / dn));
                } else {
                    system.addComponent(componentName, system.getNumberOfMoles() / 1.0e6);
                }
                dn = system.getPhase(0).getComponent(componentName).getNumberOfmoles() - oldMoles;
                oldTemperature = newTemperature;
            } while (Math.abs(
                    dn / system.getPhase(0).getComponent(componentName).getNumberOfmoles()) > 1e-9
                    || iterations < 5 || iterations > 105);

            dewPointTemperatureFlash();
        }

        if (Double.isNaN(system.getPressure())) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in bubblePointPressureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void dewPointTemperatureFlash() throws Exception {
        constantDutyFlashInterface operation =
                new neqsim.thermodynamicOperations.flashOps.saturationOps.dewPointTemperatureFlash(
                        system);
        operation.run();
        if (Double.isNaN(system.getTemperature()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in dewPointTemperatureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void dewPointTemperatureFlash(boolean derivatives) throws Exception {
        constantDutyFlashInterface operation =
                new neqsim.thermodynamicOperations.flashOps.saturationOps.dewPointTemperatureFlash(
                        system);
        if (derivatives) {
            operation = new dewPointTemperatureFlashDer(system);
        }
        operation.run();
        if (Double.isNaN(system.getTemperature()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in dewPointTemperatureFlash() - could not find solution - possible no dew point exists");
        }
    }

    public void dewPointPressureFlashHC() throws Exception {
        // try{
        system.init(0);
        constantDutyFlashInterface operation = new HCdewPointPressureFlash(system);
        operation.run();
        if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in dewPointPressureFlash() - could not find solution - possible no dew point exists");
        }
        // }
    }

    public void dewPointPressureFlash() throws Exception {
        // try{
        system.init(0);
        constantDutyFlashInterface operation = new dewPointPressureFlash(system);
        operation.run();
        if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
            throw new neqsim.util.exception.IsNaNException(
                    "error in dewPointPressureFlash() - could not find solution - possible no dew point exists");
        }
        // }
    }

    // public void dewPointPressureFlash(){
    // constantDutyFlashInterface operation = new constantDutyPressureFlash(system);
    // operation.setBeta((1-1e-7));
    // operation.run();
    // }
    public void calcPTphaseEnvelope() {
        operation = new pTphaseEnvelope(system, fileName, (1.0 - 1e-10), 1.0, false);
        // thisThread = new Thread(operation);
        // thisThread.start();
        getOperation().run();
    }

    public void calcPTphaseEnvelope(boolean bubfirst, double lowPres) {
        double phasefraction = 1.0 - 1e-10;
        if (bubfirst) {
            phasefraction = 1.0e-10;
        }
        operation = new pTphaseEnvelope(system, fileName, phasefraction, lowPres, bubfirst);

        // thisThread = new Thread(operation);
        // thisThread.start();
        getOperation().run();
    }

    public void calcPTphaseEnvelope(double lowPres) {
        operation = new pTphaseEnvelope(system, fileName, 1e-10, lowPres, true);
        // thisThread = new Thread(operation);
        // thisThread.start();
        getOperation().run();
    }

    public void calcPTphaseEnvelope(boolean bubfirst) {
        double phasefraction = 1.0 - 1e-10;
        if (bubfirst) {
            phasefraction = 1.0e-10;
        }
        operation = new pTphaseEnvelope(system, fileName, phasefraction, 1.0, bubfirst);

        // thisThread = new Thread(operation);
        // thisThread.start();
        if (!isRunAsThread()) {
            getOperation().run();
        } else {
            run();
        }
    }

    public org.jfree.chart.JFreeChart getJfreeChart() {
        return getOperation().getJFreeChart("");
    }

    public void calcPTphaseEnvelopeNew() {
        double phasefraction = 1.0 - 1e-10;
        // operation = new pTphaseEnvelope(system, fileName, phasefraction, 1.0);
        getOperation().run();
    }

    public void calcPTphaseEnvelope(double lowPres, double phasefraction) {
        operation = new pTphaseEnvelope(system, fileName, phasefraction, lowPres, true);

        // thisThread = new Thread(operation);
        // thisThread.start();
        getOperation().run();
    }

    public void OLGApropTable(double minTemp, double maxTemp, int temperatureSteps, double minPres,
            double maxPres, int pressureSteps, String filename, int TABtype) {
        operation = new OLGApropertyTableGeneratorWaterStudents(system);
        ((OLGApropertyTableGeneratorWaterStudents) operation).setFileName(filename);
        ((OLGApropertyTableGeneratorWaterStudents) operation).setPressureRange(minPres, maxPres,
                pressureSteps);
        ((OLGApropertyTableGeneratorWaterStudents) operation).setTemperatureRange(minTemp, maxTemp,
                temperatureSteps);
        getOperation().run();
    }

    public void OLGApropTablePH(double minEnthalpy, double maxEnthalpy, int enthalpySteps,
            double minPres, double maxPres, int pressureSteps, String filename, int TABtype) {
        operation = new OLGApropertyTableGeneratorWaterStudentsPH(system);
        ((OLGApropertyTableGeneratorWaterStudentsPH) operation).setFileName(filename);
        ((OLGApropertyTableGeneratorWaterStudentsPH) operation).setPressureRange(minPres, maxPres,
                pressureSteps);
        ((OLGApropertyTableGeneratorWaterStudentsPH) operation).setEnthalpyRange(minEnthalpy,
                maxEnthalpy, enthalpySteps);
        getOperation().run();
    }

    public void calcPloadingCurve() {
        operation = new pLoadingCurve2(system);
        // thisThread = new Thread(operation);
        // thisThread.start();
        getOperation().run();
    }

    public void calcHPTphaseEnvelope() {
        operation = new HPTphaseEnvelope(system);
        // thisThread = new Thread(getOperation());
        // thisThread.start();
        operation.run();
    }

    public void printToFile(String name) {
        getOperation().printToFile(name);
    }

    // public double[] get(String name){
    // return operation.get(name);
    // }
    public double[][] getData() {
        return getOperation().getPoints(0);
    }

    public String[][] getDataPoints() {
        String[][] str = new String[getOperation()
                .getPoints(0).length][getOperation().getPoints(0)[0].length];
        for (int i = 0; i < getOperation().getPoints(0).length; i++) {
            for (int j = 0; j < getOperation().getPoints(0)[0].length; j++) {
                str[i][j] = Double.toString(getOperation().getPoints(0)[i][j]);
            }
        }
        return str;
    }

    public String[][] getResultTable() {
        return resultTable;
    }

    public double dewPointTemperatureCondensationRate() {
        double dT = 1.1;
        try {
            dewPointTemperatureFlash();
        } catch (Exception e) {
            logger.error("error", e);
        }
        system.setTemperature(system.getTemperature() - dT);
        TPflash();
        double condensationRate = system.getPhase(1).getMass() / (system.getVolume() * 1.0e-5);
        try {
            dewPointTemperatureFlash();
        } catch (Exception e) {
            logger.error("error", e);
        }
        return condensationRate / dT;
    }

    public void displayResult() {
        try {
            getThermoOperationThread().join();
        } catch (Exception e) {
            logger.error("Thread did not finish");
        }
        getOperation().displayResult();
    }

    public void writeNetCDF(String name) {
        fileName = name;
        getOperation().createNetCdfFile(name);
    }

    public void setResultTable(String[][] resultTable) {
        this.resultTable = resultTable;
    }

    public void display() {
        JFrame dialog = new JFrame("System-Report");
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new BorderLayout());

        String[] names = new String[resultTable[0].length];// {"", "", ""};
        for (int i = 0; i < names.length; i++) {
            names[i] = "";
        }
        JTable Jtab = new JTable(resultTable, names);
        JScrollPane scrollpane = new JScrollPane(Jtab);
        dialogContentPane.add(scrollpane);
        dialog.pack();
        dialog.setVisible(true);
    }

    public double[] get(String name) {
        return getOperation().get(name);
    }

    /**
     * @return the operation
     */
    public OperationInterface getOperation() {
        return operation;
    }

    /**
     * @return the runAsThread
     */
    public boolean isRunAsThread() {
        return runAsThread;
    }

    /**
     * @param runAsThread the runAsThread to set
     */
    public void setRunAsThread(boolean runAsThread) {
        this.runAsThread = runAsThread;
    }

    /**
     * @return the thermoOperationThread
     */
    public Thread getThermoOperationThread() {
        return thermoOperationThread;
    }

    /**
     * @param thermoOperationThread the thermoOperationThread to set
     */
    public void setThermoOperationThread(Thread thermoOperationThread) {
        this.thermoOperationThread = thermoOperationThread;
    }

    public void addData(String name, double[][] data) {
        operation.addData(name, data);
    }

    public void calcIonComposition(int phaseNumber) {
        operation = new calcIonicComposition(system, phaseNumber);
        getOperation().run();
        resultTable = getOperation().getResultTable();
    }

    public void flash(String flashType, double spec1, double spec2, String unitSpec1,
            String unitSpec2) {
        if (flashType.equals("TP")) {
            system.setTemperature(spec1, unitSpec1);
            system.setPressure(spec2, unitSpec2);
        } else if (flashType.equals("TV")) {
            system.setTemperature(spec1, unitSpec1);
            TVflash(spec2, unitSpec2);
        } else if (flashType.equals("PH")) {
            system.setPressure(spec1, unitSpec1);
            PHflash(spec2, unitSpec2);
        } else if (flashType.equals("TS")) {
            system.setTemperature(spec1, unitSpec1);
            TSflash(spec2, unitSpec2);
        }
    }
}
