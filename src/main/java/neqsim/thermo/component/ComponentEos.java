/*
 * ComponentEos.java
 *
 * Created on 14. mai 2000, 21:27
 */
package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermCPAstatoil;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermGERG;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermInterface;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermMatCop;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermMatCopPR;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermMatCopPRUMR;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermMollerup;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPr;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPr1978;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPrDanesh;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPrDelft1998;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPrGassem2001;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermRk;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermSchwartzentruber;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermSrk;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermTwu;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermTwuCoon;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermTwuCoonParam;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermUMRPRU;
import neqsim.thermo.component.atractiveEosTerm.AttractiveTermTwuCoonStatoil;
import neqsim.thermo.phase.PhaseInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
abstract class ComponentEos extends Component implements ComponentEosInterface, neqsim.thermo.ThermodynamicConstantsInterface {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new ComponentEos
     */
    public double a = 1, b = 1, m = 0, alpha = 0, aT = 1, aDiffT = 0, Bi = 0, Ai = 0, AiT = 0, aDiffDiffT = 0;
    public double[] Aij = new double[MAX_NUMBER_OF_COMPONENTS];
    public double[] Bij = new double[MAX_NUMBER_OF_COMPONENTS];
    protected double delta1 = 0, delta2 = 0;
    protected double aDern = 0, aDerT = 0, aDerTT = 0, aDerTn = 0, bDern = 0, bDerTn = 0;
    protected double dAdndn[] = new double[MAX_NUMBER_OF_COMPONENTS];
    protected double dBdndn[] = new double[MAX_NUMBER_OF_COMPONENTS];
    protected AtractiveTermInterface atractiveParameter;
    static Logger logger = LogManager.getLogger(ComponentEos.class);

    public ComponentEos() {
    }

    public ComponentEos(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    public ComponentEos(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    public Object clone() {

        ComponentEos clonedComponent = null;
        try {
            clonedComponent = (ComponentEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        clonedComponent.atractiveParameter = (AtractiveTermInterface) this.atractiveParameter.clone();

        return clonedComponent;
    }

    public void init(double temp, double pres, double totMoles, double beta, int type) {
        super.init(temp, pres, totMoles, beta, type);
        a = calca();
        b = calcb();
        reducedTemperature = reducedTemperature(temp);
        reducedPressure = reducedPressure(pres);
        aT = a * alpha(temp);
        if (type >= 2) {
            aDiffT = diffaT(temp);
            aDiffDiffT = diffdiffaT(temp);
        }
    }

    public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta, int numberOfComponents, int type) {
        Bi = phase.calcBi(componentNumber, phase, temp, pres, numberOfComponents);
        Ai = phase.calcAi(componentNumber, phase, temp, pres, numberOfComponents);
        if (type >= 2) {
            AiT = phase.calcAiT(componentNumber, phase, temp, pres, numberOfComponents);
        }
        double totVol = phase.getMolarVolume() * phase.getNumberOfMolesInPhase();
        voli = -(-R * temp * dFdNdV(phase, numberOfComponents, temp, pres) + R * temp / (phase.getMolarVolume() * phase.getNumberOfMolesInPhase())) / (-R * temp * phase.dFdVdV() - phase.getNumberOfMolesInPhase() * R * temp / (totVol*totVol));

        if (type >= 3) {
            for (int j = 0; j < numberOfComponents; j++) {
                Aij[j] = phase.calcAij(componentNumber, j, phase, temp, pres, numberOfComponents);
                Bij[j] = phase.calcBij(componentNumber, j, phase, temp, pres, numberOfComponents);
            }
        }
    }

    public void setAtractiveTerm(int i) {
        atractiveTermNumber = i;
        if (i == 0) {
            atractiveParameter = new AtractiveTermSrk(this);
        } else if (i == 1) {
            atractiveParameter = new AtractiveTermPr(this);
        } else if (i == 2) {
            atractiveParameter = new AtractiveTermSchwartzentruber(this, getSchwartzentruberParams());
        } else if (i == 3) {
            atractiveParameter = new AtractiveTermMollerup(this, getSchwartzentruberParams());
        } else if (i == 4) {
            atractiveParameter = new AtractiveTermMatCop(this, getMatiascopemanParams());
        } else if (i == 5) {
            atractiveParameter = new AtractiveTermRk(this);
        } else if (i == 6) {
            atractiveParameter = new AtractiveTermPr1978(this);
        } else if (i == 7) {
            atractiveParameter = new AtractiveTermPrDelft1998(this);
        } else if (i == 8) {
            atractiveParameter = new AtractiveTermPrGassem2001(this);
        } else if (i == 9) {
            atractiveParameter = new AtractiveTermPrDanesh(this);
        } else if (i == 10) {
            atractiveParameter = new AtractiveTermGERG(this);
        } else if (i == 11) {
            atractiveParameter = new AtractiveTermTwuCoon(this);
        } else if (i == 12) {
            atractiveParameter = new AtractiveTermTwuCoonParam(this, getTwuCoonParams());
        } else if (i == 13) {
            atractiveParameter = new AtractiveTermMatCopPR(this, getMatiascopemanParamsPR());
        } else if (i == 14) {
            atractiveParameter = new AtractiveTermTwu(this);
        } else if (i == 15) {
            atractiveParameter = new AtractiveTermCPAstatoil(this);
        } else if (i == 16) {
            atractiveParameter = new AtractiveTermUMRPRU(this);
        } else if (i == 17) {
            atractiveParameter = new AtractiveTermMatCopPRUMR(this);
        } else if (i == 18) {
            if (componentName.equals("mercury")){
                atractiveParameter = new AttractiveTermTwuCoonStatoil(this, getTwuCoonParams());
            } else {
                atractiveParameter = new AtractiveTermSrk(this);
            }
        }
        else {
            logger.error("error selecting an alpha formultaion term");
            logger.info("ok setting alpha function");
        }
    }

    public AtractiveTermInterface getAtractiveTerm() {
        return this.atractiveParameter;
    }

    double reducedTemperature(double temperature) {
        return temperature / criticalTemperature;
    }

    double reducedPressure(double pressure) {
        return pressure / criticalPressure;
    }

    public double geta() {
        return a;
    }

    public double getb() {
        return b;
    }

    public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return phase.Fn() + phase.FB() * getBi() + phase.FD() * getAi();
    }

    public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return (phase.FBT() + phase.FBD() * phase.getAT()) * getBi() + phase.FDT() * getAi() + phase.FD() * getAiT();
    }

    public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return phase.FnV() + phase.FBV() * getBi() + phase.FDV() * getAi();
    }

    public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
        return phase.FnB() * (getBi() + comp_Array[j].getBi()) + phase.FBD() * (getBi() * comp_Array[j].getAi() + comp_Array[j].getBi() * getAi()) + phase.FB() * getBij(j) + phase.FBB() * getBi() * comp_Array[j].getBi() + phase.FD() * getAij(j);
    }

    public double getAi() {
        return Ai;
    }

    public double getAiT() {
        return AiT;
    }

    public double getBi() {
        return Bi;
    }

    public double getBij(int j) {
        return Bij[j];
    }

    public double getAij(int j) {
        return Aij[j];
    }

    public double getaDiffT() {
        return aDiffT;
    }

    public double getaDiffDiffT() {
        return aDiffDiffT;
    }

    public double getaT() {
        return aT;
    }

    public double fugcoef(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        logFugasityCoeffisient = dFdN(phase, phase.getNumberOfComponents(), temperature, pressure) - Math.log(pressure * phase.getMolarVolume() / (R * temperature));
        fugasityCoeffisient = Math.exp(logFugasityCoeffisient);
        return fugasityCoeffisient;
    }

    public double logfugcoefdP(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        double vol, voli, b, a, yaij = 0, coef;
        vol = phase.getMolarVolume();
        voli = getVoli();
        dfugdp = voli / R / temperature - 1.0 / pressure;
        return dfugdp;
    }

    public double logfugcoefdT(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        double vol, voli, b, a, yaij = 0, coef;
        vol = phase.getMolarVolume();
        voli = getVoli();
        dfugdt = (this.dFdNdT(phase, numberOfComponents, temperature, pressure) + 1.0 / temperature - voli / R / temperature * (-R * temperature * phase.dFdTdV() + pressure / temperature));
        return dfugdt;
    }

    public double[] logfugcoefdN(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getComponents();

        for (int i = 0; i < numberOfComponents; i++) {
            //System.out.println("dfdndn " + dFdNdN(i, phase, numberOfComponents, temperature, pressure) + " n " + phase.getNumberOfMolesInPhase() + " voli " + getVoli()/R/temperature + " dFdNdV " + comp_Array[i].dFdNdV(phase, numberOfComponents, temperature, pressure));
            dfugdn[i] = (this.dFdNdN(i, phase, numberOfComponents, temperature, pressure) + 1.0 / phase.getNumberOfMolesInPhase() - getVoli() / R / temperature * (-R * temperature * comp_Array[i].dFdNdV(phase, numberOfComponents, temperature, pressure) + R * temperature / phase.getTotalVolume()));
            // System.out.println("dfugdn " + dfugdn[i]);
            // System.out.println("dFdndn " + dFdNdN(i, phase, numberOfComponents, temperature, pressure) + " voli " + voli + " dFdvdn " + comp_Array[i].dFdNdV(phase, numberOfComponents, temperature, pressure) + " dfugdn " + dfugdn[i]);
            dfugdx[i] = dfugdn[i] * phase.getNumberOfMolesInPhase();
        }
        // System.out.println("diffN: " + 1 +  dfugdn[0]);
        return dfugdn;
    }

    //Method added by Neeraj
    /*
     * public double getdfugdn(int i){ double[] dfugdnv =
     * this.logfugcoefdN(phase); //return 0.0001; return dfugdnv[i]; }
     */
    // Added By Neeraj
    public double logfugcoefdNi(PhaseInterface phase, int k) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        double vol, voli, b, a, yaij = 0, coef;
        ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
        vol = phase.getMolarVolume();
        voli = getVoli();

        dfugdn[k] = (this.dFdNdN(k, phase, numberOfComponents, temperature, pressure) + 1.0 / phase.getNumberOfMolesInPhase() - voli / R / temperature * (-R * temperature * comp_Array[k].dFdNdV(phase, numberOfComponents, temperature, pressure) + R * temperature / (vol * phase.getNumberOfMolesInPhase())));
        dfugdx[k] = dfugdn[k] * (phase.getNumberOfMolesInPhase());
        //System.out.println("Main dfugdn "+dfugdn[k]);
        return dfugdn[k];
    }

    public double getAder() {
        return aDern;
    }

    public void setAder(double val) {
        aDern = val;
    }

    public double getdAdndn(int j) {
        return dAdndn[j];
    }

    public void setdAdndn(int jComp, double val) {
        dAdndn[jComp] = val;
    }

    public void setdAdT(double val) {
        aDerT = val;
    }

    public double getdAdT() {
        return aDerT;
    }

    public void setdAdTdn(double val) {
        aDerTn = val;
    }

    public double getdAdTdn() {
        return aDerTn;
    }

    public double getdAdTdT() {
        return aDerT;
    }

    public void setdAdTdT(double val) {
        aDerTT = val;
    }

    public double getBder() {
        return bDern;
    }

    public void setBder(double val) {
        bDern = val;
    }

    public double getdBdndn(int j) {
        return dBdndn[j];
    }

    public void setdBdndn(int jComp, double val) {
        dBdndn[jComp] = val;
    }

    public double getdBdT() {
        return 1;
    }

    public void setdBdTdT(double val) {
    }

    public double getdBdndT() {
        return bDerTn;
    }

    public void setdBdndT(double val) {
        bDerTn = val;
    }

    public double alpha(double temperature) {
        return atractiveParameter.alpha(temperature);
    }

    public double aT(double temperature) {
        return atractiveParameter.aT(temperature);
    }

    public double diffalphaT(double temperature) {
        return atractiveParameter.diffalphaT(temperature);
    }

    public double diffdiffalphaT(double temperature) {
        return atractiveParameter.diffdiffalphaT(temperature);
    }

    public double diffaT(double temperature) {
        return atractiveParameter.diffaT(temperature);
    }

    public double diffdiffaT(double temperature) {
        return atractiveParameter.diffdiffaT(temperature);
    }

    public double[] getDeltaEosParameters() {
        double[] param = {delta1, delta2};
        return param;
    }

    /**
     * Setter for property a.
     *
     * @param a New value of property a.
     */
    public void seta(double a) {
        this.a = a;
    }

    /**
     * Setter for property b.
     *
     * @param b New value of property b.
     */
    public void setb(double b) {
        this.b = b;
    }

    public abstract double calca();

    public abstract double calcb();

    public double getSurfaceTenisionInfluenceParameter(double temperature) {
        double a_inf = -3.471 + 4.927 * getCriticalCompressibilityFactor() + 13.085 * Math.pow(getCriticalCompressibilityFactor(), 2.0) - 2.067 * getAcentricFactor() + 1.891 * Math.pow(getAcentricFactor(), 2.0);
        double b_inf = -1.690 + 2.311 * getCriticalCompressibilityFactor() + 5.644 * Math.pow(getCriticalCompressibilityFactor(), 2.0) - 1.027 * getAcentricFactor() + 1.424 * Math.pow(getAcentricFactor(), 2.0);
        double c_inf = -0.318 + 0.299 * getCriticalCompressibilityFactor() + 1.710 * Math.pow(getCriticalCompressibilityFactor(), 2.0) - 0.174 * getAcentricFactor() + 0.157 * Math.pow(getAcentricFactor(), 2.0);
        double TR = 1.0 - temperature / getTC();
        if (TR < 1) {
            TR = 0.5;
        }

        double scale1 = aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * Math.exp(a_inf + b_inf * Math.log(TR) + c_inf * (Math.pow(Math.log(TR), 2.0))) / Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 8.0 / 3.0);

        // System.out.println("scale1 " + scale1);
        //      return scale1;
        //getAtractiveTerm().alpha(temperature)*1e-5 * Math.pow(b*1e-5, 2.0 / 3.0) * Math.exp(a_inf + b_inf * Math.log(TR) + c_inf * (Math.pow(Math.log(TR), 2.0)))/ Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 8.0 / 3.0);


        double AA = -1.0e-16 / (1.2326 + 1.3757 * getAcentricFactor());
        double BB = 1.0e-16 / (0.9051 + 1.541 * getAcentricFactor());
        double scale2 = getAtractiveTerm().alpha(temperature) * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB);

        // System.out.println("scale2 " + scale2);
        return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB);/// Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 2.0 / 3.0);

    }

    public double getAresnTV(PhaseInterface phase) {
        return R * phase.getTemperature() * dFdN(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
    }

    public double getChemicalPotential(PhaseInterface phase) {
        double entalp = getHID(phase.getTemperature()) * numberOfMolesInPhase;
        double entrop = numberOfMolesInPhase * getIdEntropy(phase.getTemperature());
        double chempot = ((entalp - phase.getTemperature() * entrop) + numberOfMolesInPhase * R * phase.getTemperature() * Math.log(numberOfMolesInPhase * R * phase.getTemperature() / phase.getVolume() / referencePressure) + getAresnTV(phase) * numberOfMolesInPhase) / numberOfMolesInPhase;
        //double chempot2 = super.getChemicalPotential(phase);
        //System.out.println("d " + chempot + " " + chempot2);
        return ((entalp - phase.getTemperature() * entrop) + numberOfMolesInPhase * R * phase.getTemperature() * Math.log(numberOfMolesInPhase * R * phase.getTemperature() / phase.getVolume() / referencePressure) + getAresnTV(phase) * numberOfMolesInPhase) / numberOfMolesInPhase;
        //  return dF;
    }

    public double getdUdnSV(PhaseInterface phase) {
        return getChemicalPotential(phase);
    }

    public double getdUdSdnV(PhaseInterface phase) {
        return -1.0 / phase.FTT() * dFdNdT(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
    }

    public double getdUdVdnS(PhaseInterface phase) {
        return 1.0 / phase.FTT() * dFdNdV(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
    }

    public double getdUdndnSV(PhaseInterface phase, int compNumb1, int compNumb2) {
        return dFdNdN(compNumb2, phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure())
                - dFdNdT(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure()) * 1.0 / phase.FTT();//*
        //   phase.getComponent(compNumb2).getF;
    }
}
