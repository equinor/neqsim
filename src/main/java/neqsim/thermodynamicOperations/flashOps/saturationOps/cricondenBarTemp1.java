package neqsim.thermodynamicOperations.flashOps.saturationOps;

import Jama.*;
import neqsim.physicalProperties.util.examples.TPflash;
import neqsim.thermo.system.SystemInterface;

public class cricondenBarTemp1 extends TPflash implements  java.io.Serializable {

    private static final long serialVersionUID = 1000;

    int neq = 0, iter = 0;
    int ic02p = -100, ic03p = -100, testcrit = 0, npCrit = 0;
    double beta = 0, ds = 0, dTmax = 1, dPmax = 1, avscp = 0.1, TC1 = 0, TC2 = 0, PC1 = 0, PC2 = 0;
    Matrix Jac;
    Matrix fvec;
    Matrix u;
    Matrix uold;
    Matrix Xgij;
    SystemInterface system;
    int numberOfComponents;
    int speceq = 0;
    Matrix a = new Matrix(4, 4);
    Matrix s = new Matrix(1, 4);
    Matrix xg;
    Matrix dx;
    Matrix xcoef;

    public cricondenBarTemp1() {
    }

    /** Creates new nonlin */
    public cricondenBarTemp1(SystemInterface system) {
        this.system = system;
        this.numberOfComponents = system.getPhase(0).getNumberOfComponents();
        neq = numberOfComponents;
        Jac = new Matrix(neq+2, neq+2);
        fvec = new Matrix(neq+2, 1);
        u = new Matrix(neq+2, 1);
        Xgij = new Matrix(neq+2, 4);
        setu();
        uold = u.copy();
        //   System.out.println("Spec : " +speceq);
    }

    public void setfvec() {
        double xtot = 0.0, dQdT=0;
        for (int i = 0; i < numberOfComponents; i++) {
            xtot += system.getPhase(1).getComponent(i).getx();
            dQdT -= system.getPhase(1).getComponent(i).getx()*(system.getPhase(0).getComponent(i).getdfugdt()-system.getPhase(1).getComponent(i).getdfugdt());
            fvec.set(i, 0,
                    Math.log(
                    system.getPhase(0).getComponents()[i].getFugasityCoeffisient() * system.getPhase(0).getComponents()[i].getz() * system.getPressure()) -
                    Math.log(
                    system.getPhases()[1].getComponents()[i].getFugasityCoeffisient() * system.getPhases()[1].getComponents()[i].getx() * system.getPressure()));
        }
        fvec.set(numberOfComponents,0,1.0-xtot);
        fvec.set(numberOfComponents+1,0,dQdT);
    }

    public void setJac() {
        Jac.timesEquals(0.0);
        double dij = 0.0;

        double tempJ = 0.0, sumdyidbeta = 0, sumdxidbeta = 0;
        int nofc = numberOfComponents;

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                dij = i == j ? 1.0 : 0.0;//Kroneckers delta
                tempJ =
                        1.0 / system.getBeta() * (dij / system.getPhases()[0].getComponents()[i].getx() - 1.0 + system.getPhases()[0].getComponents()[i].getdfugdx(j)) +
                        1.0 / (1.0 - system.getBeta()) * (dij / system.getPhases()[1].getComponents()[i].getx() - 1.0 + system.getPhases()[1].getComponents()[i].getdfugdx(j));
                Jac.set(i, j, tempJ);
            }
        }

    }

    public void setu() {
        for (int i = 0; i < numberOfComponents; i++) {
            u.set(i, 0, system.getBeta() * system.getPhases()[0].getComponents()[i].getx());
        }

    }

    public void init() {
        double temp = 0;

        for (int i = 0; i < numberOfComponents; i++) {
            temp += u.get(i, 0);
        }
        system.setBeta(temp);
        for (int i = 0; i < numberOfComponents; i++) {
            system.getPhases()[0].getComponents()[i].setx(u.get(i, 0) / system.getBeta());
            system.getPhases()[1].getComponents()[i].setx((system.getPhases()[0].getComponents()[i].getz() - u.get(i, 0)) / (1.0 - system.getBeta()));
            system.getPhases()[0].getComponents()[i].setK(system.getPhases()[0].getComponents()[i].getx() / system.getPhases()[1].getComponents()[i].getx());
            system.getPhases()[1].getComponents()[i].setK(system.getPhases()[0].getComponents()[i].getK());

        }

        system.init(3);

    }

    public double solve() {
        iter++;
        init();
        setfvec();
        setJac();
        dx = Jac.solve(fvec);
        dx.print(10,10);
        u.minusEquals(dx);
        return (dx.norm2() / u.norm2());
    }

    public void run() {
        solve();
    }

    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    public double[] get(String name) {
        return new double[0];
    }

    public void printToFile(String name) {
    }

    public void displayResult() {
    }

    public double[][] getPoints(int i) {
        return null;
    }

    public String[][] getResultTable() {
        return null;
    }

    public void createNetCdfFile(String name) {
    }

    public SystemInterface getThermoSystem(){
       return system;
   }
}
