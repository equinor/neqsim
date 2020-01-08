package neqsim.thermo.util.parameterFitting.Procede.CO2Water;

import java.io.*;
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
public class Jamal {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Jamal.class);
    
    /** Creates a new instance of Sleipneracetate */
    public Jamal() {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        FileOutputStream outfile;
        PrintStream p;
        try{
            outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt");
            p = new PrintStream(outfile);
            p.close();
        }catch(IOException e) {
            logger.error("Could not find file");
        }
        double temperature, x,pressure;
        
       for (temperature = 278; temperature <=500; temperature+=5){
                x = 1e-4;
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1);
                testSystem.addComponent("CO2",x);
                testSystem.addComponent("water",1-x);
                
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                testSystem.init(1);
                
              
                ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
                try{
                    testOps.bubblePointPressureFlash(false);
                } catch(Exception e){
                    logger.error(e.toString());
                }
                
                
                //System.out.println(testSystem.getPressure()*testSystem.getPhase(0).getComponent(0).getx());
                
                try{
                    outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt",true);
                    p = new PrintStream(outfile);
                    p.println(temperature + " "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(0).getx()/x);
                    p.close();
                }catch(FileNotFoundException e) {
                    logger.error("Could not find file");
                    
                    logger.error("Could not read from Patrick.txt"+ e.getMessage());
                }
           
        logger.info("Finished");
        
    }
    }
}
