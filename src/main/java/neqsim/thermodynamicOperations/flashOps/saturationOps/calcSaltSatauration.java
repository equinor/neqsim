/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */

package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import org.apache.logging.log4j.*;

public class calcSaltSatauration extends constantDutyTemperatureFlash{

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(calcSaltSatauration.class);
    String saltName;
    /** Creates new bubblePointFlash */
    public calcSaltSatauration() {
    }
    
    public calcSaltSatauration(SystemInterface system, String name) {
        super(system);
        this.saltName = name;
        logger.info("ok ");
    }
    
    
    public void run(){
        double ksp=0.0;
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet =  database.getResultSet("SELECT * FROM compsalt WHERE SaltName='"+saltName + "'");
        double stoc1=1e-20, stoc2=1e-20;
        String name1 = "";
        String name2 = "";
        try{
            dataSet.next();
            name1 = dataSet.getString("ion1").trim();
            name2 = dataSet.getString("ion2").trim();
            stoc1 = Double.parseDouble(dataSet.getString("stoc1"));
            stoc2 = Double.parseDouble(dataSet.getString("stoc2"));
            ksp = Double.parseDouble(dataSet.getString("Ksp-water"));
            system.addComponent(name1, ksp/100.0);
            system.addComponent(name2, ksp/100.0);
            system.createDatabase(true);
        }
        catch(Exception e){logger.error("failed " + e.toString());}
        
        system.init(0);
        system.init(1);
        system.initPhysicalProperties();
        double err = 1.0;
        int iter=1;
        double kspcalc=0.0, kspcalc1 = 0.0,kspcalc2 = 0.0;
        do{
            iter++;
            double addnumb = 0.0;
            //logger.info("testing");
            
            system.addComponent(name1, 0.001);//*stoc1*constant*system.getPhase(1).getComponent(name1).getNumberOfMolesInPhase());
            system.addComponent(name2, 0.001);
            //system.init(1);
            system.init(0);
            system.initPhysicalProperties();
            kspcalc1 = Math.pow(system.getPhase(1).getComponent(name1).getx()/system.getPhase(1).getMolarMass()*system.getPhase(1).getPhysicalProperties().getDensity()/1000.0,stoc1)*Math.pow(system.getPhase(1).getComponent(name2).getx()/system.getPhase(1).getMolarMass()*system.getPhase(1).getPhysicalProperties().getDensity()/1000.0,stoc2);
            
            system.addComponent(name1, -2*0.001);//*stoc1*constant*system.getPhase(1).getComponent(name1).getNumberOfMolesInPhase());
            system.addComponent(name2, -2*0.001);
            //system.init(1);
            system.init(0);
            system.initPhysicalProperties();
            kspcalc2 = Math.pow(system.getPhase(1).getComponent(name1).getx()/system.getPhase(1).getMolarMass()*system.getPhase(1).getPhysicalProperties().getDensity()/1000.0,stoc1)*Math.pow(system.getPhase(1).getComponent(name2).getx()/system.getPhase(1).getMolarMass()*system.getPhase(1).getPhysicalProperties().getDensity()/1000.0,stoc2);
            
            system.addComponent(name1, 0.001);//*stoc1*constant*system.getPhase(1).getComponent(name1).getNumberOfMolesInPhase());
            system.addComponent(name2, 0.001);
            //system.init(1);
            system.init(0);
            system.initPhysicalProperties();
            kspcalc = Math.pow(system.getPhase(1).getComponent(name1).getx()/system.getPhase(1).getMolarMass()*system.getPhase(1).getPhysicalProperties().getDensity()/1000.0,stoc1)*Math.pow(system.getPhase(1).getComponent(name2).getx()/system.getPhase(1).getMolarMass()*system.getPhase(1).getPhysicalProperties().getDensity()/1000.0,stoc2);
            
            double diff = (kspcalc1-kspcalc2)/0.002;
            //logger.info("diff " + diff);
            err = (kspcalc-ksp);
            addnumb = -(err/diff);
            logger.info("kspcalc " + kspcalc + " err " + err + " add " + addnumb);
            system.addComponent(name1, iter/100.0*addnumb);//*stoc1*constant*system.getPhase(1).getComponent(name1).getNumberOfMolesInPhase());
            system.addComponent(name2, iter/100.0*addnumb);
            //system.init(1);
            system.init(0);
            system.initPhysicalProperties();
            
        }
        while(Math.abs(err/ksp)>1e-5 && iter<1000);
        
        logger.info("solution found after " + iter + " iterations in calcSaltSatauration()");
        
    }
    
    public void printToFile(String name) {}
    
    
    
}
