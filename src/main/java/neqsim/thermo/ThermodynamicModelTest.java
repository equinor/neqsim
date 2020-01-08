/*
 * ThermodynamicModelTest.java
 *
 * Created on 7. mai 2001, 19:20
 */

package neqsim.thermo;

import neqsim.thermo.system.SystemInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class ThermodynamicModelTest implements ThermodynamicConstantsInterface{
    private static final long serialVersionUID = 1000;
    SystemInterface system;
    static Logger logger = LogManager.getLogger(ThermodynamicModelTest.class);

    
    
    public ThermodynamicModelTest() {
    }
    
    public ThermodynamicModelTest(SystemInterface system) {
        this.system = system;
    }
    
   
    public void runTest(){
        // system.init(0);
        system.init(3);
        logger.info("testing fugasitycoefs..." + checkFugasityCoeffisients());
        logger.info("testing fugasity der composition..." + checkFugasityCoeffisientsDn());
        logger.info("testing fugasity der composition2..." + checkFugasityCoeffisientsDn2());
        logger.info("testing fugasity der pressure..." + checkFugasityCoeffisientsDP());
        logger.info("testing fugasity der temperature..." + checkFugasityCoeffisientsDT());
        logger.info("comparing to numerical derivatives..." + checkNumerically());
      //  System.out.println("testing fugasitycoefs..." + checkFugasityCoeffisients());
      //  System.out.println("testing fugasity der composition..." + checkFugasityCoeffisientsDn());
      //  System.out.println("testing fugasity der composition2..." + checkFugasityCoeffisientsDn2());
      //  System.out.println("testing fugasity der pressure..." + checkFugasityCoeffisientsDP());
      //  System.out.println("testing fugasity der temperature..." + checkFugasityCoeffisientsDT());
      //  System.out.println("comparing to numerical derivatives..." + checkNumerically());
    }
    
    public boolean checkFugasityCoeffisients(){
        double temp1=0, temp2=0;
        boolean test1=false, test2=false;
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            temp1 += system.getPhases()[0].getComponents()[i].getNumberOfMolesInPhase()*Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());
            temp2 += system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase()*Math.log(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient());
        }
        logger.info("Testing fugasity coefficients...................");
        logger.info("Total fug gas : " + temp1);
        logger.info("Total fug liq : " + temp2);
       // System.out.println("Testing fugasity coefficients...................");
       // System.out.println("Total fug gas : " + temp1);
       // System.out.println("Total fug liq : " + temp2);
        temp1 -= system.getPhases()[0].getGresTP()/(R*system.getTemperature());
        temp2 -= system.getPhases()[1].getGresTP()/(R*system.getTemperature());
        double sum = Math.abs(temp1) +  Math.abs(temp2);
        logger.info("Diffference fug gas : " + temp1);
        logger.info("Difference fug liq : " + temp2);
       // System.out.println("Diffference fug gas : " + temp1);
       // System.out.println("Difference fug liq : " + temp2);
        return Math.abs(sum)<1e-10;
    }
    
    public boolean checkFugasityCoeffisientsDn(){
        boolean test1=false, test2=false;
        double temp1=0, temp2=0;
        double sum=0;
        
        for(int j=0;j<system.getPhases()[0].getNumberOfComponents();j++){
            temp1=0; temp2=0;
            //temp1 += Math.log(system.getPhases()[0].getComponents()[j].getFugasityCoeffisient());
            //temp2 += Math.log(system.getPhases()[1].getComponents()[j].getFugasityCoeffisient());
            for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
                temp1 += system.getPhases()[0].getComponents()[i].getNumberOfMolesInPhase()*system.getPhases()[0].getComponents()[i].getdfugdn(j);
                temp2 += system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase()*system.getPhases()[1].getComponents()[i].getdfugdn(j);
                //System.out.println("fug " + system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase()*system.getPhases()[1].getComponents()[i].getdfugdn(j));
            }
            
            sum += Math.abs(temp1) +  Math.abs(temp2);
            //            System.out.println("test fugdn gas : " + j + "  " + temp1 + " name " + system.getPhases()[0].getComponents()[j].getComponentName());
            //            System.out.println("test fugdn liq : " + j + "  " + temp2);
        }
        logger.info("Testing composition derivatives of fugasity coefficients...................");
        logger.info("Diffference : " + sum);
       // System.out.println("Testing composition derivatives of fugasity coefficients...................");
       // System.out.println("Diffference : " + sum);
        return Math.abs(sum)<1e-10;
    }
    
    public boolean checkFugasityCoeffisientsDn2(){
        boolean test1=false, test2=false;
        double temp1=0, temp2=0;
        double sum=0;
        
        for(int j=0;j<system.getPhases()[0].getNumberOfComponents();j++){
            temp1=0; temp2=0;
            //temp1 += Math.log(system.getPhases()[0].getComponents()[j].getFugasityCoeffisient());
            //temp2 += Math.log(system.getPhases()[1].getComponents()[j].getFugasityCoeffisient());
            for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
                temp1 += system.getPhases()[0].getComponents()[i].getdfugdn(j) - system.getPhases()[0].getComponents()[j].getdfugdn(i);
                temp2 += system.getPhases()[1].getComponents()[i].getdfugdn(j) - system.getPhases()[1].getComponents()[j].getdfugdn(i);
            }
            sum += Math.abs(temp1) +  Math.abs(temp2);
            //            System.out.println("test fugdn gas : " + j + "  " + temp1);
            //            System.out.println("test fugdn liq : " + j + "  " + temp2);
        }
        logger.info("Testing composition derivatives2 of fugasity coefficients...................");
        logger.info("Diffference : " + sum);
       // System.out.println("Testing composition derivatives2 of fugasity coefficients...................");
       // System.out.println("Diffference : " + sum);
        
        return Math.abs(sum)<1e-10;
    }
    
    public boolean checkFugasityCoeffisientsDP(){
        boolean test1=false, test2=false;
        double temp1=0, temp2=0;
        double sum=0;
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            temp1 += system.getPhases()[0].getComponents()[i].getNumberOfMolesInPhase() * system.getPhases()[0].getComponents()[i].getdfugdp();
            temp2 += system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase() * system.getPhases()[1].getComponents()[i].getdfugdp();
            
        }
        temp1 -= (system.getPhases()[0].getZ()-1.0)*system.getPhases()[0].getNumberOfMolesInPhase()/system.getPhases()[0].getPressure();
        temp2 -= (system.getPhases()[1].getZ()-1.0)*system.getPhases()[1].getNumberOfMolesInPhase()/system.getPhases()[1].getPressure();
        sum = Math.abs(temp1) +  Math.abs(temp2);
        // System.out.println("test fugdp gas : " + temp1);
        // System.out.println("test fugdp liq : " + temp2);
        logger.info("Testing pressure derivatives of fugasity coefficients...................");
        logger.info("Error : " + sum);
      //  System.out.println("Testing pressure derivatives of fugasity coefficients...................");
      //  System.out.println("Error : " + sum);
        
        return Math.abs(sum)<1e-10;
    }
    
    public boolean checkFugasityCoeffisientsDT(){
        boolean test1=false, test2=false;
        double temp1=0, temp2=0;
        double sum=0;
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            temp1 += system.getPhases()[0].getComponents()[i].getNumberOfMolesInPhase() * system.getPhases()[0].getComponents()[i].getdfugdt();
            temp2 += system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase() * system.getPhases()[1].getComponents()[i].getdfugdt();
            
        }
        temp1 += system.getPhases()[0].getHresTP()/(R*Math.pow(system.getTemperature(),2.0));
        temp2 += system.getPhases()[1].getHresTP()/(R*Math.pow(system.getTemperature(),2.0));
        sum = Math.abs(temp1) +  Math.abs(temp2);
        // System.out.println("test fugdp gas : " + system.getPhases()[0].getHresTP());
        // System.out.println("test fugdp liq : " + temp2);
        logger.info("Testing temperature derivatives of fugasity coefficients...................");
        logger.info("Error : " + sum);
        //System.out.println("Testing temperature derivatives of fugasity coefficients...................");
        //System.out.println("Error : " + sum);
        return Math.abs(sum)<1e-10;
    }
    
    
    public boolean checkNumerically(){
        
        double[][] gasfug=new double[2][system.getPhases()[0].getNumberOfComponents()];
        double[][] liqfug=new double[2][system.getPhases()[0].getNumberOfComponents()];
        double[][] gasnumericDfugdt=new double[2][system.getPhases()[0].getNumberOfComponents()];
        double[][] liqnumericDfugdt=new double[2][system.getPhases()[0].getNumberOfComponents()];
        double[][] gasnumericDfugdp=new double[2][system.getPhases()[0].getNumberOfComponents()];
        double[][] liqnumericDfugdp=new double[2][system.getPhases()[0].getNumberOfComponents()];
        double[][][] gasnumericDfugdn=new double[2][system.getPhases()[0].getNumberOfComponents()][system.getPhases()[0].getNumberOfComponents()];
        double[][][] liqnumericDfugdn=new double[2][system.getPhases()[0].getNumberOfComponents()][system.getPhases()[0].getNumberOfComponents()];
        system.init(3);
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            gasnumericDfugdt[0][i] = system.getPhases()[0].getComponents()[i].getdfugdt();
            gasnumericDfugdp[0][i] = system.getPhases()[0].getComponents()[i].getdfugdp();
            liqnumericDfugdt[0][i] = system.getPhases()[1].getComponents()[i].getdfugdt();
            liqnumericDfugdp[0][i] = system.getPhases()[1].getComponents()[i].getdfugdp();
            for(int k=0;k<system.getPhases()[0].getNumberOfComponents();k++){
                gasnumericDfugdn[0][i][k] = system.getPhases()[0].getComponents()[i].getdfugdn(k);
                liqnumericDfugdn[0][i][k] = system.getPhases()[1].getComponents()[i].getdfugdn(k);
            }
        }
        
        double dt = system.getTemperature()/1e5;
        system.setTemperature(system.getTemperature()+dt);
        system.init(3);
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            gasfug[0][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());
            liqfug[0][i] = Math.log(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient());
        }
        
        system.setTemperature(system.getTemperature()-2*dt);
        system.init(3);
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            gasfug[1][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());
            liqfug[1][i] = Math.log(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient());
        }
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            logger.info("dt: gas phase comp " + i + "  % error " + ((gasfug[0][i] - gasfug[1][i])/(2*dt) - gasnumericDfugdt[0][i])/gasnumericDfugdt[0][i]*100.0);
            logger.info("dt: liq phase comp " + i + "  % error " + ((liqfug[0][i] - liqfug[1][i])/(2*dt) - liqnumericDfugdt[0][i])/liqnumericDfugdt[0][i]*100.0);
          //  System.out.println("dt: gas phase comp " + i + "  % error " + ((gasfug[0][i] - gasfug[1][i])/(2*dt) - gasnumericDfugdt[0][i])/gasnumericDfugdt[0][i]*100.0);
          //  System.out.println("dt: liq phase comp " + i + "  % error " + ((liqfug[0][i] - liqfug[1][i])/(2*dt) - liqnumericDfugdt[0][i])/liqnumericDfugdt[0][i]*100.0);
        }
        
        system.setTemperature(system.getTemperature()+dt);
        system.init(3);
        
        double dp = system.getPressure()/1e5;
        system.setPressure(system.getPressure()+dp);
        system.init(3);
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            gasfug[0][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());
            liqfug[0][i] = Math.log(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient());
        }
        
        
        system.setPressure(system.getPressure()-2*dp);
        system.init(3);
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            gasfug[1][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());
            liqfug[1][i] = Math.log(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient());
        }
        
        for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
            logger.info("dp: gas phase comp " + i + "  % error " + ((gasfug[0][i] - gasfug[1][i])/(2*dp) - gasnumericDfugdp[0][i])/gasnumericDfugdp[0][i]*100.0);
            logger.info("dp: liq phase comp " + i + "  % error " + ((liqfug[0][i] - liqfug[1][i])/(2*dp) - liqnumericDfugdp[0][i])/liqnumericDfugdp[0][i]*100.0);
           // System.out.println("dp: gas phase comp " + i + "  % error " + ((gasfug[0][i] - gasfug[1][i])/(2*dp) - gasnumericDfugdp[0][i])/gasnumericDfugdp[0][i]*100.0);
           // System.out.println("dp: liq phase comp " + i + "  % error " + ((liqfug[0][i] - liqfug[1][i])/(2*dp) - liqnumericDfugdp[0][i])/liqnumericDfugdp[0][i]*100.0);
        }
        
        system.setPressure(system.getPressure()+dp);
        system.init(3);
        
        for(int phase = 0;phase<2;phase++){
            for(int k=0;k<system.getPhases()[0].getNumberOfComponents();k++){
                double dn = system.getPhases()[phase].getComponents()[k].getNumberOfMolesInPhase()/1.0e5;
               logger.info("component name " + system.getPhases()[phase].getComponents()[k].getComponentName());
               logger.info("dn " + dn);
              //  System.out.println("component name " + system.getPhases()[phase].getComponents()[k].getComponentName());
              //  System.out.println("dn " + dn);
                if(dn<1e-12) {
                    dn = 1e-12;
                }
                system.addComponent(k,dn,phase);
                //  system.initBeta();
                system.init_x_y();
                system.init(3);
                
                for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
                    //   gasfug[0][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());
                    liqfug[0][i] = Math.log(system.getPhases()[phase].getComponents()[i].getFugasityCoeffisient());
                }
                
                system.addComponent(k, -2.0*dn, phase);
                //  system.initBeta();
                system.init_x_y();
                system.init(3);
                
                for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
                    //   gasfug[1][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient());
                    liqfug[1][i] = Math.log(system.getPhases()[phase].getComponents()[i].getFugasityCoeffisient());
                }
                
                for(int i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
                    if(phase==0) {
                        logger.info("dn: gas phase comp " + i + "  % error " + ((liqfug[0][i] - liqfug[1][i])/(2*dn) - gasnumericDfugdn[0][i][k])/gasnumericDfugdn[0][i][k]*100.0);
                        // System.out.println("dn: gas phase comp " + i + "  % error " + ((liqfug[0][i] - liqfug[1][i])/(2*dn) - gasnumericDfugdn[0][i][k])/gasnumericDfugdn[0][i][k]*100.0);
                    }
                    if(phase==1) {
                        logger.info("dn: liq phase comp " + i + "  % error " + ((liqfug[0][i] - liqfug[1][i])/(2*dn) - liqnumericDfugdn[0][i][k])/liqnumericDfugdn[0][i][k]*100.0);
                        // System.out.println("dn: liq phase comp " + i + "  % error " + ((liqfug[0][i] - liqfug[1][i])/(2*dn) - liqnumericDfugdn[0][i][k])/liqnumericDfugdn[0][i][k]*100.0);
                    }
                }
                
                system.addComponent(k, dn, phase);
                //system.initBeta();
                system.init_x_y();
                system.init(3);
            }
        }
        return true;
    }
    
}
