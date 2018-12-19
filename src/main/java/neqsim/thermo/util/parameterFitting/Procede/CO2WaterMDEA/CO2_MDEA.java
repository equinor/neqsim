package neqsim.thermo.util.parameterFitting.Procede.CO2WaterMDEA;

import neqsim.util.database.NeqSimDataBase;
import java.io.*;
import java.sql.*;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;



/*
 * Sleipneracetate.java
 *
 * Created on August 6, 2004, 11:41 AM
 */

/**
 *
 * @author  agrawalnj
 */
public class CO2_MDEA {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(CO2_MDEA.class);
    
    /** Creates a new instance of Sleipneracetate */
    public CO2_MDEA() {
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
            //p.println("");
            p.close();
        }catch(IOException e) {
            logger.error("Could not find file");
        }
        
        
        
        int i=0,j,CO2Numb=0, WaterNumb=0, MDEANumb=0, HCO3Numb=0,MDEAHpNumb=0;
        double ID, pressure, temperature, x1,x2,x3, bias;
       
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM CO2WaterMDEA WHERE ID>196 AND ID<231");
 
        try{
            
            while(dataSet.next()){
                i += 1;
                logger.info("Adding.... "+i);
                
                ID = Double.parseDouble(dataSet.getString("ID"));
                pressure = Double.parseDouble(dataSet.getString("PressureCO2"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
                x2 = Double.parseDouble(dataSet.getString("x2"));
                x3 = Double.parseDouble(dataSet.getString("x3"));
                
         
                /*if((ID>56 && ID<64) || (ID>92 && ID<101) || (ID>123 && ID<131))  //75 wt% amine
                    continue;              
                */
                logger.info("................ID............ "+ID);
                SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);
                
                testSystem.addComponent("CO2",x1);
                testSystem.addComponent("MDEA",x3);
                testSystem.addComponent("water",x2);
                
           
                testSystem.chemicalReactionInit();
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                 
                j=0;
                do{
                    CO2Numb = j;
                    j++;
                }while(!testSystem.getPhases()[1].getComponents()[j-1].getComponentName().equals("CO2"));
          
                
                j=0;
                do{
                    MDEANumb = j;
                    j++;
                }while(!testSystem.getPhases()[1].getComponents()[j-1].getComponentName().equals("MDEA"));
                
                
                j=0;
                do{
                    WaterNumb = j;
                    j++;
                }while(!testSystem.getPhases()[1].getComponents()[j-1].getComponentName().equals("water"));
         
                              
                j=0;
                do{
                    HCO3Numb = j;
                    j++;
                }while(!testSystem.getPhases()[1].getComponents()[j-1].getComponentName().equals("HCO3-"));
                       
                
                j=0;
                do{
                    MDEAHpNumb = j;
                    j++;
                }while(!testSystem.getPhases()[1].getComponents()[j-1].getComponentName().equals("MDEA+"));
                
                      
                
                ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
                try{
                    testOps.bubblePointPressureFlash(false);
                }
                catch(Exception e){
                    logger.error(e.toString());
                }
                
                bias = (pressure-testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx())/pressure*100;
                //bias = (pressure-testSystem.getPressure())/pressure*100;
                
                //logger.info("Bias "+bias);
                //logger.info("Act "+testSystem.getPhase(1).getActivityCoefficient(MDEAHpNumb, WaterNumb));
                //logger.info("Pressure CO2 "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx());
                try{
                    outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt",true);
                    p = new PrintStream(outfile);
                    p.println(ID+" "+pressure+" "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx());
                    p.close();
                }catch(FileNotFoundException e) {
                    logger.error("Could not find file"+e.getMessage());
                }
                
                }       
                
            }
            catch(Exception e){
                logger.error("database error " + e);
            }
            
            logger.info("Finished");
            
        }
    }
