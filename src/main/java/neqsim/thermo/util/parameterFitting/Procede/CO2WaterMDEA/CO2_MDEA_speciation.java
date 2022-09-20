package neqsim.thermo.util.parameterFitting.Procede.CO2WaterMDEA;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * CO2_MDEA_speciation class.
 * </p>
 *
 * @author agrawalnj
 * @version $Id: $Id
 */
public class CO2_MDEA_speciation {
  static Logger logger = LogManager.getLogger(CO2_MDEA_speciation.class);

  /**
   * <p>
   * Constructor for CO2_MDEA_speciation.
   * </p>
   */
  public CO2_MDEA_speciation() {}

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args the command line arguments
   */
  @SuppressWarnings("unused")
  public static void main(String[] args) {
    int i = 0;
    int j;
    int CO2Numb = 0;
    int WaterNumb = 0;
    int MDEANumb = 0;
    int HCO3Numb = 0;
    int MDEAHpNumb = 0;
    int CO3Numb = 0;
    int OHNumb = 0;
    double nCO2;
    double nMDEA;
    double nHCO3;
    double nCO3;
    double nMDEAp;
    double nOH;
    double aCO2;
    double aMDEA;
    double aHCO3;
    double awater;
    double aMDEAp;
    double aOH;
    double aCO3;
    double x1;

    double x2;
    double x3;
    double total;
    double n1;
    double n2;
    double n3;
    double mass;
    double MDEAwt = 42.313781;
    double loading = 0.43194;
    double temperature = 273.16 + 65;
    double pressure = 0.01;

    // for (loading = 5e-4; loading<=0.1;) {
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
    } catch (Exception ex) {
      logger.error(ex.toString());
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

    try (PrintStream p =
        new PrintStream(new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
      p.println(loading + " " + testSystem.getPressure() + " "
          + testSystem.getPressure() * testSystem.getPhase(0).getComponent(CO2Numb).getx() + " "
          + nCO2 + " " + nMDEA + " " + nHCO3 + " " + nMDEAp + " " + nCO3 + " " + nOH);
      // p.println(loading+" "+testSystem.getPressure()+"
      // "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx());
    } catch (FileNotFoundException ex) {
      logger.error("Could not find file");
      logger.error("Could not read from Patrick.txt" + ex.getMessage());
    }
    try (PrintStream p =
        new PrintStream(new FileOutputStream("C:/java/NeqSimSource/activity.txt", true))) {
      p.println(loading + " " + awater + " " + aCO2 + " " + aMDEA + " " + aHCO3 + " " + aMDEAp + " "
          + aCO3 + " " + aOH);
    } catch (FileNotFoundException ex) {
      logger.error("Could not find file");
      logger.error("Could not read from Patrick.txt" + ex.getMessage());
    }

    if (loading < 0.1) {
      loading *= 10;
    } else {
      loading += 0.1;
    }
    logger.info("Finished");
  }
}
