/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */

package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import org.apache.log4j.Logger;

public class bubblePointTemperatureNoDer extends constantDutyTemperatureFlash{

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(bubblePointTemperatureNoDer.class);
    
    /** Creates new bubblePointFlash */
    public bubblePointTemperatureNoDer() {
    }
    
    public bubblePointTemperatureNoDer(SystemInterface system) {
        super(system);
    }
    
    
    public void run(){
        if(system.getPhase(0).getNumberOfComponents()==1 && system.getPressure()>system.getPhase(0).getComponent(0).getPC()) {
            setSuperCritical(true);
        }
        
        int iterations=0, maxNumberOfIterations = 1000;
        double yold=0, ytotal=1;
        double deriv=0, funk=0;
        //logger.info("starting");
        
        system.init(0);
        system.setNumberOfPhases(2);
        system.setBeta(1, 1.0-1e-10);
        system.setBeta(0, 1e-10);
        
        if(system.getPhase(0).getNumberOfComponents()==1) system.setTemperature(system.getPhase(0).getComponent(0).getAntoineVaporTemperature(system.getPressure()));
       
        double oldPres=0;
        if(system.isChemicalSystem()){
            system.getChemicalReactionOperations().solveChemEq(0);
            system.getChemicalReactionOperations().solveChemEq(1);
        }
        
        for (int i=0;i<system.getPhases()[1].getNumberOfComponents();i++){
            system.getPhases()[1].getComponents()[i].setx(system.getPhases()[0].getComponents()[i].getz());
            if(system.getPhases()[0].getComponents()[i].getIonicCharge()!=0) {
                system.getPhases()[0].getComponents()[i].setx(1e-40);
            } else {
                system.getPhases()[0].getComponents()[i].setx(system.getPhases()[0].getComponents()[i].getK()*system.getPhases()[1].getComponents()[i].getz());
            }
        }
        ytotal = 0.0;
        for (int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            ytotal += system.getPhases()[0].getComponents()[i].getx();
        }
        double oldTemp=10.0;
        
        double ktot=0.0;
        do {
            iterations++;
            for (int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
                system.getPhases()[0].getComponents()[i].setx(system.getPhases()[0].getComponents()[i].getx()/ytotal);
            }
            if(system.isChemicalSystem() && (iterations%2)==0){
                system.getChemicalReactionOperations().solveChemEq(1);
            }
            system.init(1);
            oldTemp =  system.getTemperature();
            ktot = 0.0;
            for (int i=0;i<system.getPhases()[1].getNumberOfComponents();i++){
                do{
                    yold = system.getPhases()[0].getComponents()[i].getx();
                    if(system.getPhase(0).getComponent(i).getIonicCharge()!=0) {
                        system.getPhases()[0].getComponents()[i].setK(1e-40);
                    } else {
                        system.getPhases()[0].getComponents()[i].setK(Math.exp(Math.log(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient()) - Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient())));
                    }
                    system.getPhases()[1].getComponents()[i].setK(system.getPhases()[0].getComponents()[i].getK());
                    system.getPhases()[0].getComponents()[i].setx(system.getPhases()[0].getComponents()[i].getK()*system.getPhases()[1].getComponents()[i].getz());
                    //logger.info("y err " + Math.abs(system.getPhases()[0].getComponents()[i].getx()-yold));
                }
                while(Math.abs(system.getPhases()[0].getComponents()[i].getx()-yold)>1e-4);
                ktot += Math.abs(system.getPhases()[1].getComponents()[i].getK()-1.0);
                
            }
            ytotal = 0.0;
            for (int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
                ytotal += system.getPhases()[0].getComponents()[i].getx();
            }
            if(ytotal>1.2) {
                ytotal=1.2;
            }
            if(ytotal<0.8) {
                ytotal=0.8;
            }
            // logger.info("y tot " + ytotal);
            
 //           system.setTemperature(system.getTemperature() + 0.05*(1.0/ytotal*system.getTemperature()-system.getTemperature()));
             system.setTemperature(system.getTemperature() + 0.1*system.getTemperature()*(1.0-ytotal));
            //            for (int i=0;i<system.getPhases()[1].getNumberOfComponents();i++){
            //                system.getPhases()[0].getComponents()[i].setx(system.getPhases()[0].getComponents()[i].getx()+0.5*(system.getPhases()[0].getComponents()[i].getK()*system.getPhases()[1].getComponents()[i].getx()*1.0/ytotal-system.getPhases()[0].getComponents()[i].getx()));
            //            }
         //   logger.info("temperature " + system.getTemperature());
        }
        while((((Math.abs(ytotal)-1.0) > 1e-9) || Math.abs(oldTemp-system.getTemperature())/oldTemp>1e-8) && (iterations < maxNumberOfIterations));
        //logger.info("iter " + iterations);
        if(Math.abs(ytotal-1.0)>=1e-5 || ktot<1e-3 &&system.getPhase(0).getNumberOfComponents()>1) {
            setSuperCritical(true);
        }
    }
    
    public void printToFile(String name) {}
    
    
    
}
