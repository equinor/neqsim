package neqsim.thermo.util.parameterFitting.Procede.WaterMDEA;

import neqsim.util.database.NeqSimDataBase;
import java.io.*;
import java.sql.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;



/*
 * Sleipneracetate.java
 *
 * Created on August 6, 2004, 11:41 AM
 */

/**
 *
 * @author  agrawalnj
 */
public class Water_MDEA {

    private static final long serialVersionUID = 1000;
    
    /** Creates a new instance of Sleipneracetate */
    public Water_MDEA() {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        FileOutputStream outfile;
        PrintStream p;
        try{
            outfile = new FileOutputStream("C:/java/NeqSimSource/water_MDEA.txt");
            p = new PrintStream(outfile);
            p.close();
        }catch(IOException e) {
            System.out.println("Could not find file");
        }
        
            
        double pressure = 1;
        double temperature = 25+273.16;
        double x1,x2;
               
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM WaterMDEA");
        
        
       try{
            
            while(dataSet.next()){
            
                double ID = Double.parseDouble(dataSet.getString("ID"));
                pressure = Double.parseDouble(dataSet.getString("Pressure"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
                x2 = Double.parseDouble(dataSet.getString("x2"));
               
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(298, pressure);
            
                testSystem.addComponent("water",x1);
                testSystem.addComponent("MDEA",x2);

                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
            
                 
            ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
            
            try{
                testOps.bubblePointPressureFlash(false);
                
            }
            catch(Exception e){
                System.out.println(e.toString());
            }
           
            double hm = testSystem.getPhase(1).getEnthalpy();
            System.out.println(hm);
            
            try{
                outfile = new FileOutputStream("C:/java/NeqSimSource/water_MDEA.txt",true);
                p = new PrintStream(outfile);
                p.println(ID+" "+pressure+" "+testSystem.getPressure());
                
            }catch(FileNotFoundException e) {
                System.out.println("Could not find file");
                e.printStackTrace();
            }
            
            }
       }catch(Exception e){
            System.out.println("database error" + e);
            }
            
            //  }
            //   }
            //     }
            //}
                        
            
        System.out.println("Finished");
        }

}