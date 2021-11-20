package neqsim.thermo.util.parameterFitting.Statoil.Acids;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 * Sleipneracetate.java
 *
 * Created on August 6, 2004, 11:41 AM
 */
/**
 *
 * @author agrawalnj
 */
public class Acids {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Acids.class);

    /**
     * Creates a new instance of Sleipneracetate
     */
    public Acids() {}

    /**
     * @param args the command line arguments
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        FileOutputStream outfile;
        PrintStream p;
        try {
            outfile = new FileOutputStream(
                    "C:/Documents and Settings/agrawalnj/Desktop/Statoil/Statoil.txt");
            p = new PrintStream(outfile);
            p.close();
        } catch (IOException e) {
            logger.error("Could not find file");
        }

        FileOutputStream outfile1;
        PrintStream p1;
        try {
            outfile1 = new FileOutputStream(
                    "C:/Documents and Settings/agrawalnj/Desktop/Statoil/activity.txt");
            p1 = new PrintStream(outfile1);
            p1.close();
        } catch (IOException e) {
            logger.error("Could not find file");
        }

        int j;
        int AcidNumb = 0, AcnNumb = 0, CO2Numb = 0, WaterNumb = 0, MDEANumb = 0, HCO3Numb = 0,
                MDEAHpNumb = 0, CO3Numb = 0, OHNumb;
        double nCO2, nMDEA, nHCO3, nCO3, nMDEAp, nOH, nHAc, nAcn;
        double aCO2, aMDEA, aHCO3, awater, aMDEAp, aOH, aCO3, aHAc, aAcn;
        double error, newValue, oldValue, guess, dx, dP, Pold, Pnew;
        double PressureCO2, n1, n2, n3, n4;
        double MDEAwt = 39.43955905;
        double Acidwt = 2.0;
        double loading = 0.2;
        double temperature = 273.16 + 65;
        PressureCO2 = 1;

        for (loading = 1e-5; loading <= 1.0;) {
            // For acid systems
            n4 = MDEAwt / 119.16;
            n3 = Acidwt / 60.05;
            n2 = (100 - MDEAwt - Acidwt) / 18.015;
            n1 = loading * n4;

            SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, PressureCO2);

            testSystem.addComponent("CO2", n1);
            testSystem.addComponent("AceticAcid", n3);
            testSystem.addComponent("MDEA", n4);
            testSystem.addComponent("water", n2);

            testSystem.chemicalReactionInit();
            testSystem.createDatabase(true);
            testSystem.setMixingRule(4);
            testSystem.init(0);

            j = 0;
            do {
                CO2Numb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("CO2"));

            j = 0;
            do {
                MDEANumb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("MDEA"));

            j = 0;
            do {
                WaterNumb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("water"));

            j = 0;
            do {
                HCO3Numb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("HCO3-"));

            j = 0;
            do {
                CO3Numb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("CO3--"));

            j = 0;
            do {
                OHNumb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("OH-"));

            j = 0;
            do {
                MDEAHpNumb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("MDEA+"));

            j = 0;
            do {
                AcidNumb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("AceticAcid"));

            j = 0;
            do {
                AcnNumb = j;
                j++;
            } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
                    .equals("Ac-"));

            logger.info("CO2 number " + CO2Numb);

            ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
            try {
                testOps.bubblePointPressureFlash(false);
            } catch (Exception e) {
                logger.error(e.toString());
            }

            nCO2 = testSystem.getPhase(1).getComponent(CO2Numb).getx();
            nMDEA = testSystem.getPhase(1).getComponent(MDEANumb).getx();
            nHCO3 = testSystem.getPhase(1).getComponent(HCO3Numb).getx();
            nCO3 = testSystem.getPhase(1).getComponent(CO3Numb).getx();
            nMDEAp = testSystem.getPhase(1).getComponent(MDEAHpNumb).getx();
            nOH = testSystem.getPhase(1).getComponent(OHNumb).getx();
            nHAc = testSystem.getPhase(1).getComponent(AcidNumb).getx();
            nAcn = testSystem.getPhase(1).getComponent(AcnNumb).getx();

            aMDEA = testSystem.getPhase(1).getActivityCoefficient(MDEANumb, WaterNumb);
            awater = testSystem.getPhase(1).getActivityCoefficient(WaterNumb);
            aCO2 = testSystem.getPhase(1).getActivityCoefficient(CO2Numb, WaterNumb);
            aMDEAp = testSystem.getPhase(1).getActivityCoefficient(MDEAHpNumb, WaterNumb);
            aHCO3 = testSystem.getPhase(1).getActivityCoefficient(HCO3Numb, WaterNumb);
            aOH = testSystem.getPhase(1).getActivityCoefficient(OHNumb, WaterNumb);
            aCO3 = testSystem.getPhase(1).getActivityCoefficient(CO3Numb, WaterNumb);
            aHAc = testSystem.getPhase(1).getActivityCoefficient(AcidNumb, WaterNumb);
            aAcn = testSystem.getPhase(1).getActivityCoefficient(AcnNumb, WaterNumb);

            try {
                outfile = new FileOutputStream(
                        "C:/Documents and Settings/agrawalnj/Desktop/Statoil/Statoil.txt", true);
                p = new PrintStream(outfile);
                p.println(loading + " "
                        + testSystem.getPressure()
                                * testSystem.getPhase(0).getComponent(CO2Numb).getx()
                        + " " + nCO2 + " " + nMDEA + " " + nHCO3 + " " + nMDEAp + " " + nCO3 + " "
                        + nOH + " " + nHAc + " " + nAcn);
                p.close();
            } catch (FileNotFoundException e) {
                logger.error("Could not find file " + e.getMessage());
            }

            try {
                outfile1 = new FileOutputStream(
                        "C:/Documents and Settings/agrawalnj/Desktop/Statoil/activity.txt", true);
                p1 = new PrintStream(outfile1);
                p1.println(loading + " " + awater + " " + aCO2 + " " + aMDEA + " " + aHCO3 + " "
                        + aMDEAp + " " + aCO3 + " " + aOH + " " + aHAc + " " + aAcn);
                p1.close();
            } catch (FileNotFoundException e) {
                logger.error("Could not find file" + e.getMessage());
            }

            if (loading < 0.1) {
                loading *= 10;
            } else {
                loading += 0.1;
            }
        }

        logger.info("Finished");
    }
}
