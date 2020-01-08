/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package neqsim.thermodynamicOperations.flashOps;

import Jama.*;
import neqsim.MathLib.nonLinearSolver.newtonRhapson;
import neqsim.thermo.ThermodynamicConstantsInterface;
import static neqsim.thermo.ThermodynamicConstantsInterface.R;
import neqsim.thermo.system.SystemInterface;
import org.apache.logging.log4j.*;

public class sysNewtonRhapsonPHflash extends Object implements java.io.Serializable, ThermodynamicConstantsInterface{

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(sysNewtonRhapsonPHflash.class);
    int neq=0,iter=0;
    int ic02p=-100, ic03p=-100, testcrit=0, npCrit=0;
    double beta = 0, ds=0,dTmax=1,dPmax=1, avscp = 0.1, TC1=0, TC2=0,PC1=0,PC2=0;
    double specVar=0;
    Matrix Jac;
    Matrix fvec;
    Matrix gTvec;
    Matrix gPvec;
    Matrix u;
    Matrix uold;
    Matrix Xgij;
    SystemInterface system;
    int numberOfComponents;
    int speceq=0;
    Matrix a=new Matrix(4,4);
    Matrix s=new Matrix(1,4);
    Matrix xg;
    Matrix xcoef;
    newtonRhapson solver;
    boolean etterCP = false;
    boolean etterCP2 = false;
    double dVdT=0;
    int type=0;
    double dPdT=0;
    
    public sysNewtonRhapsonPHflash() {
    }
    
    /** Creates new nonlin */
    public sysNewtonRhapsonPHflash(SystemInterface system,int numberOfPhases,int numberOfComponents) {
        this.system = system;
        this.numberOfComponents=numberOfComponents;
        neq=numberOfComponents;
        Jac=new Matrix(neq+2,neq+2);
        fvec=new Matrix(neq+2,1);
        gTvec=new Matrix(neq,1);
        gPvec=new Matrix(neq,1);
        u=new Matrix(neq+2,1);
        Xgij=new Matrix(neq+2,4);
        setu();
        uold = u.copy();
        //   logger.info("Spec : " +speceq);
        solver = new  newtonRhapson();
        solver.setOrder(3);
    }
    
    public sysNewtonRhapsonPHflash(SystemInterface system,int numberOfPhases,int numberOfComponents, int type) {
        this(system, numberOfPhases, numberOfComponents);
        type = type;
    }
    public void setSpec(double spec){
        this.specVar = spec;
    }
    
    public void setfvec(){
        for (int i=0;i<numberOfComponents;i++) {
            fvec.set(i,0,
            Math.log(
            system.getPhases()[0].getComponents()[i].getFugasityCoeffisient()) + Math.log(system.getPhases()[0].getComponents()[i].getx())
            -
            Math.log(
            system.getPhases()[1].getComponents()[i].getFugasityCoeffisient()) - Math.log(system.getPhases()[1].getComponents()[i].getx()));
        }
        double rP = 0.0, rT=0.0;
        if(type==0){
            rT = 1.0/(R*system.getTemperature())*(specVar-system.getEnthalpy());
            rP = 0.0;
        }
        if(type==1){
            rT = 1.0/R*(specVar-system.getEntropy());
            rP = 0.0;
        }
        
        fvec.set(numberOfComponents,0, rT);
        fvec.set(numberOfComponents+1,0,rP);
    }
    
    
    public void setJac(){
        Jac.timesEquals(0.0);
        double dij=0.0;
        
        double tempJ=0.0, sumdyidbeta=0, sumdxidbeta=0;
        int nofc=numberOfComponents;
        
        for (int i=0;i<numberOfComponents;i++) {
            for (int j=0;j<numberOfComponents;j++) {
                dij= i==j ? 1.0 : 0.0;//Kroneckers delta
                tempJ=
                1.0/system.getBeta()*(dij/system.getPhases()[0].getComponents()[i].getx()-1.0+system.getPhases()[0].getComponents()[i].getdfugdx(j))
                +
                1.0/(1.0-system.getBeta())*(dij/system.getPhases()[1].getComponents()[i].getx()-1.0+system.getPhases()[1].getComponents()[i].getdfugdx(j));
                Jac.set(i,j,tempJ);
            }
        }
        
        double[] gT = new double[numberOfComponents];
        double[] gP  = new double[numberOfComponents];
        
        for (int i=0;i<numberOfComponents;i++) {
            gT[i] = system.getTemperature() * (system.getPhases()[0].getComponents()[i].getdfugdt()-system.getPhases()[1].getComponents()[i].getdfugdt());
            gP[i] = system.getPressure() * (system.getPhases()[0].getComponents()[i].getdfugdp()-system.getPhases()[1].getComponents()[i].getdfugdp());
            
            Jac.set(numberOfComponents,i,gT[i]);
            Jac.set(i,numberOfComponents,gT[i]);
            
            Jac.set(numberOfComponents+1,i,gP[i]);
            Jac.set(i,numberOfComponents+1,gP[i]);
        }
        
        double Ett = -system.getCp()/R;
        Jac.set(numberOfComponents,numberOfComponents,Ett);
        double Etp = system.getPressure()/R*system.getdVdTpn();
        Jac.set(numberOfComponents,numberOfComponents+1,Etp);
        Jac.set(numberOfComponents+1,numberOfComponents,Etp);
        double Epp = Math.pow(system.getPressure(),2.0)/(R*system.getTemperature())*system.getdVdPtn();
        Jac.set(numberOfComponents+1,numberOfComponents+1,Epp);
    }
    
    public void setu() {
        for (int i=0;i<numberOfComponents;i++){
            u.set(i,0,0.0);
        }
        u.set(numberOfComponents,0,0.0);
        u.set(numberOfComponents+1,0,0.0);
    }
    
    
    public void init() {
        
        double temp = system.getBeta();
        
        for (int i=0;i<numberOfComponents;i++) {
            temp += u.get(i,0);
        }
        system.setBeta(temp);
        
        for (int i=0;i<numberOfComponents;i++) {
            double v=0.0, l=0.0;
            v = system.getPhases()[0].getComponents()[i].getx() * system.getBeta() + u.get(i,0);
            l = system.getPhases()[0].getComponents()[i].getz() - v;
            system.getPhases()[0].getComponents()[i].setx(v/system.getBeta());
            system.getPhases()[1].getComponents()[i].setx(l/(1.0-system.getBeta()));
            system.getPhases()[1].getComponents()[i].setK(system.getPhases()[0].getComponents()[i].getx()/system.getPhases()[1].getComponents()[i].getx());
            system.getPhases()[0].getComponents()[i].setK(system.getPhases()[1].getComponents()[i].getK());
        }
        
        //    dt = Math.exp(u.get(numberOfComponents+1,0)) - system.getTemperature();
        //     logger.info("temperature: " + system.getTemperature());
        //    logger.info("pressure: " + system.getPressure());
        //     system.init(1);
        //      v1 = system.getVolume();
        //  system.setPressure(Math.exp(u.get(numberOfComponents+1,0)));
        logger.info("temperature: " + system.getTemperature());
        system.setTemperature(Math.exp(u.get(numberOfComponents,0) + Math.log(system.getTemperature())));
        system.setPressure(Math.exp(u.get(numberOfComponents+1,0) + Math.log(system.getPressure())));
        logger.info("etter temperature: " + system.getTemperature());
        
        system.init(3);
    }
    
    
    public int solve(int np) {
        Matrix dx;
        iter=1;
        do {
            iter++;
            init();
            setfvec();
            setJac();
            fvec.print(10,10);
            Jac.print(10,10);
            u = Jac.solve(fvec.times(-1.0));
            //u.equals(dx.timesEquals(1.0));
            fvec.print(10,10);
            logger.info("iter: "+iter);
        }
        while (fvec.norm2()>1.e-10 && iter<1000);// && Double.isNaN(dx.norm2()));
        logger.info("iter: "+iter);
        logger.info("temperature: " + system.getTemperature());
        logger.info("pressure: " + system.getPressure());
        init();
        return iter;
    }
    
}