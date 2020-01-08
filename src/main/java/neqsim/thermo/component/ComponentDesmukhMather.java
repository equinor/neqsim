/*
 * ComponentGEUniquac.java
 *
 * Created on 10. juli 2000, 21:06
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseDesmukhMather;
import neqsim.thermo.phase.PhaseInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class ComponentDesmukhMather extends ComponentGE {

    private static final long serialVersionUID = 1000;

    private double deshMathIonicDiameter = 1.0;
    static Logger logger = LogManager.getLogger(ComponentDesmukhMather.class);
    /** Creates new ComponentGENRTLmodifiedHV */
    public ComponentDesmukhMather() {
    }

    public ComponentDesmukhMather(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;

        try {
            if (!component_name.equals("default")) {

                try {
                    dataSet = database.getResultSet(("SELECT * FROM comptemp WHERE name='" + component_name + "'"));
                    dataSet.next();
                    dataSet.getString("FORMULA");
                } catch (Exception e) {
                    dataSet.close();
                    logger.info("no parameters in tempcomp -- trying comp.. " + component_name);
                    dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + component_name + "'"));
                    dataSet.next();
                }
                deshMathIonicDiameter = Double.parseDouble(dataSet.getString("DeshMatIonicDiameter"));
            }
        } catch (Exception e) {
            logger.error("error in comp", e);
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
                logger.error("error closing database.....", e);
            }
        }
    }

    public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure, int phasetype, double[][] HValpha, double[][] HVgij, double[][] intparam, String[][] mixRule) {
        return getGamma(phase, numberOfComponents, temperature, pressure, phasetype);
    }

    public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure, int phasetype) {
        double A = 1.174;
        
        double B = 3.32384e9;
        double Iion = ((PhaseDesmukhMather) phase).getIonicStrength();
        double temp = 0.0;

        for (int i = 0; i < phase.getNumberOfComponents(); i++) {
            if(!phase.getComponent(i).getComponentName().equals("water")) {
                temp += 2.0 * ((PhaseDesmukhMather) phase).getBetaDesMatij(i, getComponentNumber()) * phase.getComponent(i).getMolality(phase);//phase.getComponent(i).getMolarity(phase);
            }

        }
        //System.out.println("molality MDEA "+ phase.getComponent("MDEA").getMolality(phase));

        lngamma = - A * Math.pow(getIonicCharge(), 2.0) * Math.sqrt(Iion) / (1.0 + B*deshMathIonicDiameter*1e-10* Math.sqrt(Iion)) + temp;
        // else lngamma = 0.0;
        //System.out.println("temp2 "+ -2.303*A*Math.pow(getIonicCharge(),2.0)*Math.sqrt(Iion)/(1.0+B*Math.sqrt(Iion)));
        gamma = getMolality(phase)*((PhaseDesmukhMather)phase).getSolventMolarMass()*Math.exp(lngamma)/getx();
        lngamma = Math.log(gamma);
        logger.info("gamma " + componentName + " " + gamma);
        return gamma;
    }

    public double fugcoef(PhaseInterface phase) {
        //System.out.println("fug coef " + gamma*getAntoineVaporPressure(phase.getTemperature())/phase.getPressure());
         if (componentName.equals("water")) {
             double watervol = 1.0 / 1000.0 * getMolarMass();
             double watervappres = getAntoineVaporPressure(phase.getTemperature());
            fugasityCoeffisient = gamma * watervappres*Math.exp(watervol / (R * phase.getTemperature()) * (phase.getPressure()- watervappres) * 1e5) / phase.getPressure();
         }
         else if (ionicCharge == 0 && referenceStateType.equals("solvent")) {
            fugasityCoeffisient = gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure();
        } else if (ionicCharge == 0 && referenceStateType.equals("solute")) {
            fugasityCoeffisient = gamma * getHenryCoef(phase.getTemperature()) / phase.getPressure(); // sjekke denne

        } else {
            fugasityCoeffisient = 1e-15;
        //System.out.println("fug " + fugasityCoeffisient);
        }
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        return fugasityCoeffisient;
    }

    /** Getter for property lngamma.
     * @return Value of property lngamma.
     *
     */
    public double getLngamma() {
        return lngamma;
    }
    
      public double getMolality(PhaseInterface phase) {
        return getNumberOfMolesInPhase()/((PhaseDesmukhMather) phase).getSolventWeight();
    }
}