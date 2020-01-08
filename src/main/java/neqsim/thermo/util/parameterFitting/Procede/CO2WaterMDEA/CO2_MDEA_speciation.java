package neqsim.thermo.util.parameterFitting.Procede.CO2WaterMDEA;

import java.io.*;
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
 * @author agrawalnj
 */
public class CO2_MDEA_speciation {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(CO2_MDEA_speciation.class);

    /**
     * Creates a new instance of Sleipneracetate
     */
    public CO2_MDEA_speciation() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        FileOutputStream outfile;
        PrintStream p;
        try {
            outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt");
            p = new PrintStream(outfile);
            p.close();
        } catch (IOException e) {
            logger.error("Could not find file");
        }

        FileOutputStream outfile1;
        PrintStream p1;
        try {
            outfile1 = new FileOutputStream("C:/java/NeqSimSource/activity.txt");
            p1 = new PrintStream(outfile1);
            p1.close();
        } catch (IOException e) {
            logger.error("Could not find file");
        }

        int i = 0, j, CO2Numb = 0, WaterNumb = 0, MDEANumb = 0, HCO3Numb = 0, MDEAHpNumb = 0, CO3Numb = 0, OHNumb = 0;
        double nCO2, nMDEA, nHCO3, nCO3, nMDEAp, nOH;
        double aCO2, aMDEA, aHCO3, awater, aMDEAp, aOH, aCO3;
        double x1, x2, x3, total, n1, n2, n3, mass;

        double MDEAwt = 42.313781;
        double loading = 0.43194;
        double temperature = 273.16 + 65;
        double pressure = 0.01;

        //for (loading = 5e-4; loading<=0.1;) {
        n3 = MDEAwt / 119.16;
        n2 = (100 - MDEAwt) / 18.015;
        n1 = n3 * loading;
        total = n1 + n2 + n3;
        x1 = n1 / total;
        x2 = n2 / total;
        x3 = n3 / total;
        mass = x1 * 44.01 + x2 * 18.015 + x3 * 119.1632;

        SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);

        testSystem.addComponent("CO2", x1);
        testSystem.addComponent("MDEA", x3);
        testSystem.addComponent("water", x2);

        testSystem.chemicalReactionInit();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);

        j = 0;
        do {
            CO2Numb = j;
            j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName().equals("CO2"));

        j = 0;
        do {
            MDEANumb = j;
            j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName().equals("MDEA"));

        j = 0;
        do {
            WaterNumb = j;
            j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName().equals("water"));

        j = 0;
        do {
            HCO3Numb = j;
            j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName().equals("HCO3-"));

        j = 0;
        do {
            CO3Numb = j;
            j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName().equals("CO3--"));

        j = 0;
        do {
            OHNumb = j;
            j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName().equals("OH-"));

        j = 0;
        do {
            MDEAHpNumb = j;
            j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName().equals("MDEA+"));

        logger.info("CO2 number " + CO2Numb);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        logger.info("Pressure " + testSystem.getPressure());

        nCO2 = testSystem.getPhase(1).getComponent(CO2Numb).getx();
        nMDEA = testSystem.getPhase(1).getComponent(MDEANumb).getx();
        nHCO3 = testSystem.getPhase(1).getComponent(HCO3Numb).getx();
        nCO3 = testSystem.getPhase(1).getComponent(CO3Numb).getx();
        nMDEAp = testSystem.getPhase(1).getComponent(MDEAHpNumb).getx();
        nOH = testSystem.getPhase(1).getComponent(OHNumb).getx();

        aMDEA = testSystem.getPhase(1).getActivityCoefficient(MDEANumb, WaterNumb);
        awater = testSystem.getPhase(1).getActivityCoefficient(WaterNumb);
        aCO2 = testSystem.getPhase(1).getActivityCoefficient(CO2Numb, WaterNumb);
        aMDEAp = testSystem.getPhase(1).getActivityCoefficient(MDEAHpNumb, WaterNumb);
        aHCO3 = testSystem.getPhase(1).getActivityCoefficient(HCO3Numb, WaterNumb);
        aOH = testSystem.getPhase(1).getActivityCoefficient(OHNumb, WaterNumb);
        aCO3 = testSystem.getPhase(1).getActivityCoefficient(CO3Numb, WaterNumb);

        try {
            outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true);
            p = new PrintStream(outfile);
            p.println(loading + " " + testSystem.getPressure() + " " + testSystem.getPressure() * testSystem.getPhase(0).getComponent(CO2Numb).getx() + " " + nCO2 + " " + nMDEA + " " + nHCO3 + " " + nMDEAp + " " + nCO3 + " " + nOH);
            //p.println(loading+" "+testSystem.getPressure()+" "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx());
            p.close();
        } catch (FileNotFoundException e) {
            logger.error("Could not find file");

            logger.error("Could not read from Patrick.txt" + e.getMessage());
        }
        try {
            outfile1 = new FileOutputStream("C:/java/NeqSimSource/activity.txt", true);
            p1 = new PrintStream(outfile1);
            p1.println(loading + " " + awater + " " + aCO2 + " " + aMDEA + " " + aHCO3 + " " + aMDEAp + " " + aCO3 + " " + aOH);
            p1.close();
        } catch (FileNotFoundException e) {
            logger.error("Could not find file");
            logger.error("Could not read from Patrick.txt" + e.getMessage());
        }

        if (loading < 0.1) {
            loading *= 10;
        } else {
            loading += 0.1;
        }

        //}
        logger.info("Finished");
    }
}
