package neqsim.thermo.util.parameterFitting.Procede.CH4CO2WaterMDEA;

import neqsim.util.database.NeqSimDataBase;
import java.io.*;
import java.sql.*;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
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
public class CO2_MDEA_methane {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(CO2_MDEA_methane.class);
    
    /** Creates a new instance of Sleipneracetate */
    public CO2_MDEA_methane() {
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
        
        
        
        int i=0,j,CH4Numb=0,CO2Numb=0, WaterNumb=0, MDEANumb=0, HCO3Numb=0,MDEAHpNumb=0;
        int iter =0;
        double error, newValue, oldValue, guess,dx,dP,Pold,Pnew;
        /*double pressure, n1,n2,n3;
        double MDEAwt = 35;
        double loading = 0.4;
        double temperature = 313.0;*/
        
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM PatrickCO2");
        
              
        try{
            
            while(dataSet.next()){
                
                i += 1;
                logger.info("Adding.... "+i);
                
                double ID = Double.parseDouble(dataSet.getString("ID"));
                double pressureCO2 = Double.parseDouble(dataSet.getString("PressureCO2"));
                double pressure = Double.parseDouble(dataSet.getString("Pressure"));
                double temperature = Double.parseDouble(dataSet.getString("Temperature"));
                double n1 = Double.parseDouble(dataSet.getString("x1"));
                double n2 = Double.parseDouble(dataSet.getString("x2"));
                double n3 = Double.parseDouble(dataSet.getString("x3"));
                
                guess = n1/20;
                SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);
                
                testSystem.addComponent("CO2",n1);
                testSystem.addComponent("water",n2);
                testSystem.addComponent("methane",guess);
                testSystem.addComponent("MDEA",n3);
                
                
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
                    CH4Numb = j;
                    j++;
                }while(!testSystem.getPhases()[1].getComponents()[j-1].getComponentName().equals("methane"));
                
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
                                   
                error = 1e10;
                dx = 1e-6;
                iter = 0;
                newValue = guess;
                oldValue = guess;
                
               
               do{
                    //System.out.println("iteration..." + iter);
                    iter += 1;
                    oldValue = newValue;
                                        
                    try{
                        testOps.bubblePointPressureFlash(false);
                        
                    }
                    catch(Exception e){
                        logger.error(e.toString());
                    }
                    Pold = testSystem.getPressure();
                    //System.out.println("Pold "+Pold);
                    
                    
                    
                    testSystem.addComponent("methane",-testSystem.getPhase(1).getComponent(CH4Numb).getNumberOfmoles());
                    testSystem.addComponent("methane",oldValue+dx);
                    
                    try{
                        testOps.bubblePointPressureFlash(false);
                        
                    }
                    catch(Exception e){
                        logger.error(e.toString());
                    }
                    
                    Pnew = testSystem.getPressure();
                    dP = (Pnew-Pold)/dx;
                    newValue = oldValue - (Pold-pressure)/(dP);
                    error = newValue - oldValue;
                                    
                    testSystem.addComponent("methane",-testSystem.getPhase(1).getComponent(CH4Numb).getNumberOfmoles());
                    testSystem.addComponent("methane",newValue);
                
                }while(Math.abs(error)>1e-9 && iter <50);
                
                j=0;
                do{
                    CO2Numb = j;
                    j++;
                }while(!testSystem.getPhases()[1].getComponents()[j-1].getComponentName().equals("CO2"));
                
                double aad = (pressureCO2-testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx())/pressureCO2*100;
                logger.info(ID+" "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx() + " " + pressureCO2+" "+aad);
                /*//System.out.println(testSystem.getPhase(1).getComponent(CO2Numb).getx()/testSystem.getPhase(1).getComponent(MDEANumb).getx());
                System.out.println("HCO3 "+testSystem.getPhase(1).getComponent(HCO3Numb).getx()+" "+testSystem.getPhase(0).getComponent(HCO3Numb).getx());
                System.out.println("CO2 "+testSystem.getPhase(1).getComponent(CO2Numb).getx()+" "+testSystem.getPhase(0).getComponent(CO2Numb).getx());
                System.out.println("H2O "+testSystem.getPhase(1).getComponent(WaterNumb).getx()+" "+testSystem.getPhase(0).getComponent(WaterNumb).getx());
                System.out.println("MDEA "+testSystem.getPhase(1).getComponent(MDEANumb).getx()+" "+testSystem.getPhase(0).getComponent(MDEANumb).getx());
                //System.out.println("CH4 "+testSystem.getPhase(1).getComponent(CH4Numb).getx()+" "+testSystem.getPhase(0).getComponent(CH4Numb).getx());
                */
                //System.out.println(testSystem.getPressure()+" "+pressure+" "+testSystem.getTemperature());
                
                //System.out.println("Bias dev. "+aad);
                
                
                try{
                    outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt",true);
                    p = new PrintStream(outfile);
                    //p.println(ID+" "+pressure+" "+pressureCO2+" "+" "+testSystem.getPressure()+" "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx()+" "+testSystem.getPhase(1).getComponent(CH4Numb).getx()+" "+iter);
                    p.println(ID+" "+pressure+" "+pressureCO2+" "+testSystem.getPressure()+" "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx());
                    //p.println(ID+" "+pressure+" "+" "+testSystem.getPressure()+" "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx());
                    p.close();
                }catch(FileNotFoundException e) {
                    logger.error("Could not find file"+e.getMessage());
                    logger.error("Could not read from Patrick.txt"+e.getMessage());
                }
                
                
                }       
                
           // }
        }
            catch(Exception e){
                logger.error("database error " + e);
            }
            
            //  }
            //   }
            //     }
            //}
            logger.info("Finished");
            
        }
    }
