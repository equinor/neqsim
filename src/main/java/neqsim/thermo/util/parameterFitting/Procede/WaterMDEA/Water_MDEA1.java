package neqsim.thermo.util.parameterFitting.Procede.WaterMDEA;

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
public class Water_MDEA1 {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Water_MDEA1.class);
    
    /** Creates a new instance of Sleipneracetate */
    public Water_MDEA1() {
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
        double temperature=40+273.16;
        double pressure = 1.0;
        double x=0;
        
        for(x = 0.85; x<=1; x+=0.010){
        
        SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, pressure);
        testSystem.addComponent("water",x);
        testSystem.addComponent("MDEA",1-x);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);
        testSystem.init(1);
        
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try{
                testOps.bubblePointPressureFlash(false);
                }
        catch(Exception e){
            logger.error(e.toString());
        }
        
        //double aMDEA = testSystem.getPhase(1).getActivityCoefficient(1);
        //double awater = testSystem.getPhase(1).getActivityCoefficient(0);
        //double yMDEA = testSystem.getPhase(0).getComponent(1).getx();
        //double Hm = testSystem.getPhase(1).getHresTP();
        //logger.info("Activity MDEA "+aMDEA+" "+yMDEA);
        logger.info("pressure "+testSystem.getPressure());
        
        /*logger.info("Excess Heat kJ "+Hm/1000);
        logger.info("Excess Heat kJ "+testSystem.getPhase(1).getComponent(0).getHresTP(temperature)/1000);
        logger.info("Excess Heat kJ "+testSystem.getPhase(1).getComponent(1).getHresTP(temperature)/1000);
        */
        try{
                outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt",true);
                p = new PrintStream(outfile);
                //p.println(x+" "+testSystem.getPhase(0).getComponent(0).getx()+" "+testSystem.getPhase(0).getComponent(1).getx());
                p.println(x+" "+testSystem.getPhase(0).getComponent(1).getx()+" "+testSystem.getPressure()+" "+testSystem.getPhase(0).getComponent(1).getFugasityCoeffisient());
                //p.println(x+" "+aMDEA+" "+awater);
                p.close();
            }catch(FileNotFoundException e) {
                logger.error("Could not find file" + e.getMessage());
            }
            

        }
        logger.info("Finished");
            
        }
    }
