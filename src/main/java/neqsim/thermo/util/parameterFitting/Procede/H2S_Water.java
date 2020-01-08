package neqsim.thermo.util.parameterFitting.Procede;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;



/*
 * Sleipneracetate.java
 *
 * Created on August 6, 2004, 11:41 AM
 */

/**
 *
 * @author  agrawalnj
 */
public class H2S_Water {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(H2S_Water.class);
    
    /** Creates a new instance of Sleipneracetate */
    public H2S_Water() {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        int i=0;
        double aad;
        
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM H2SWater");
        
       
         try{
            
              while(dataSet.next()){
                i++;
                logger.info("Adding.... "+i);
                                
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(300, 1);
                
                double ID = Double.parseDouble(dataSet.getString("ID"));
                double temperature = Double.parseDouble(dataSet.getString("Temperature"));
                double pressure = Double.parseDouble(dataSet.getString("Pressure"));
                double x = Double.parseDouble(dataSet.getString("x"));
                double y = Double.parseDouble(dataSet.getString("y"));
                
                testSystem.setTemperature(temperature);
                testSystem.setPressure(pressure);
                testSystem.addComponent("H2S", x);
                testSystem.addComponent("water", 1.0-x);


                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                
                
                
                ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
                
                
                try{
                    testOps.bubblePointPressureFlash(false);
                }
                catch(Exception e){
                    logger.error(e.toString());
                }
                
                aad = (pressure-testSystem.getPressure())/pressure*100;
                logger.info(ID+" "+pressure+" "+testSystem.getPressure()+" "+aad);
                
                
                 }
        }
        catch(Exception e){
            logger.error("database error" + e);
        }

        logger.info("Finished");
            
        }
    }
