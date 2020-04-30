/*
 * ComponentGE.java
 *
 * Created on 10. juli 2000, 21:05
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseGE;
import neqsim.thermo.phase.PhaseInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author  Even Solbraa
 * @version
 */
abstract class ComponentGE extends Component implements ComponentGEInterface {

    private static final long serialVersionUID = 1000;

    protected double gamma = 0, gammaRefCor = 0;
    protected double lngamma = 0, dlngammadt = 0, dlngammadp = 0,dlngammadtdt=0.0;
    protected double[] dlngammadn;
    static Logger logger = LogManager.getLogger(ComponentGE.class);

    /** Creates new ComponentGE */
    public ComponentGE() {
    }

    public ComponentGE(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    public double fugcoef(PhaseInterface phase) {
        logger.info("fug coef " + gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure());
        if (referenceStateType.equals("solvent")) {
            fugasityCoeffisient = gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure();
            gammaRefCor = gamma;
        } else {
            double activinf = 1.0;
            if (phase.hasComponent("water")) {
                int waternumb = phase.getComponent("water").getComponentNumber();
                activinf = gamma / ((PhaseGE) phase).getActivityCoefficientInfDilWater(componentNumber, waternumb);
            } else {
                activinf = gamma / ((PhaseGE) phase).getActivityCoefficientInfDil(componentNumber);
            }
            fugasityCoeffisient = activinf * getHenryCoef(phase.getTemperature()) / phase.getPressure();//gamma* benyttes ikke
            gammaRefCor = activinf;
        }
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);

        return fugasityCoeffisient;
    }

    public double fugcoefDiffPres(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        if (referenceStateType.equals("solvent")) {
            dfugdp = 0.0; // forelopig uten pointing
        } else {
            dfugdp = 0.0; // forelopig uten pointing
        }
        return dfugdp;
    }

    public double fugcoefDiffTemp(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();

        if (referenceStateType.equals("solvent")) {
            dfugdt = dlngammadt + 1.0/getAntoineVaporPressure(temperature)*getAntoineVaporPressuredT(temperature);
            logger.info("check this dfug dt - antoine");
        } else {
            dfugdt = dlngammadt + getHenryCoefdT(temperature);
        }
        return dfugdt;
    }

    public double getGamma() {
        return gamma;
    }
    
     public double getlnGamma() {
        return lngamma;
    }

    public double getlnGammadt() {
        return dlngammadt;
    }
    
      public double getlnGammadtdt() {
        return dlngammadtdt;
    }

    public double getlnGammadn(int k) {
        return dlngammadn[k];
    }

    public void setlnGammadn(int k, double val) {
        dlngammadn[k] = val;
    }

    /** Getter for property gammaRefCor.
     * @return Value of property gammaRefCor.
     *
     */
    public double getGammaRefCor() {
        return gammaRefCor;
    }
    
 
}