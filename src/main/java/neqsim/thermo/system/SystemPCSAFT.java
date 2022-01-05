/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */
package neqsim.thermo.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePCSAFTRahmat;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 *
 * @author Even Solbraa
 * @version
 */
/**
 * This class defines a thermodynamic system using the PC-SAFT EoS equation of state
 */
public class SystemPCSAFT extends SystemSrkEos {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(SystemPCSAFT.class);

    // SystemSrkEos clonedSystem;
    /**
     * <p>
     * Constructor for SystemPCSAFT.
     * </p>
     */
    public SystemPCSAFT() {
        super();
        modelName = "PCSAFT-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePCSAFTRahmat();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
        this.useVolumeCorrection(false);
        commonInitialization();
    }

    /**
     * <p>
     * Constructor for SystemPCSAFT.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemPCSAFT(double T, double P) {
        super(T, P);
        modelName = "PCSAFT-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePCSAFTRahmat();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        this.useVolumeCorrection(false);
        commonInitialization();
    }

    /**
     * <p>
     * Constructor for SystemPCSAFT.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemPCSAFT(double T, double P, boolean solidCheck) {
        this(T, P);
        modelName = "PCSAFT-EOS";
        attractiveTermNumber = 0;
        numberOfPhases = 5;
        maxNumberOfPhases = 5;
        solidPhaseCheck = solidCheck;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePCSAFTRahmat();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        commonInitialization();
        if (solidPhaseCheck) {
            // System.out.println("here first");
            phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }

        if (hydrateCheck) {
            // System.out.println("here first");
            phaseArray[numberOfPhases - 1] = new PhaseHydrate();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }
        this.useVolumeCorrection(false);
    }

    /** {@inheritDoc} */
    @Override
    public SystemPCSAFT clone() {
        SystemPCSAFT clonedSystem = null;
        try {
            clonedSystem = (SystemPCSAFT) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // clonedSystem.phaseArray = (PhaseInterface[]) phaseArray.clone();
        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }

    /** {@inheritDoc} */
    @Override
    public void addTBPfraction(String componentName2, double numberOfMoles, double molarMass,
            double density) {
        // componentName = (componentName + "_" + getFluidName());
        super.addTBPfraction(componentName2, numberOfMoles, molarMass, density);
        // addComponent(componentName2, numberOfMoles, 290.0, 30.0, 0.11);
        String componentName = getPhase(0).getComponent(getPhase(0).getNumberOfComponents() - 1)
                .getComponentName();
        for (int i = 0; i < numberOfPhases; i++) {
            // getPhase(phaseIndex[i]).getComponent(componentName).setMolarMass(molarMass);
            // getPhase(phaseIndex[i]).getComponent(componentName).setIsTBPfraction(true);

            double mSaft = 0.0249 * molarMass * 1e3 + 0.9711;
            double epskSaftm = 6.5446 * molarMass * 1e3 + 177.92;
            double msigm = 1.6947 * molarMass * 1e3 + 23.27;
            getPhase(phaseIndex[i]).getComponent(componentName).setmSAFTi(mSaft);
            getPhase(phaseIndex[i]).getComponent(componentName).setEpsikSAFT(epskSaftm / mSaft);
            getPhase(phaseIndex[i]).getComponent(componentName)
                    .setSigmaSAFTi(Math.pow(msigm / mSaft, 1.0 / 3.0) / 1.0e10);
            logger.info("Saft parameters: m " + mSaft + " epsk " + epskSaftm / mSaft + " sigma "
                    + Math.pow(msigm / mSaft, 1.0 / 3.0));
        }
    }

    /**
     * <p>
     * commonInitialization.
     * </p>
     */
    public void commonInitialization() {
        setImplementedCompositionDeriativesofFugacity(false);
        setImplementedPressureDeriativesofFugacity(false);
        setImplementedTemperatureDeriativesofFugacity(false);
    }
}
