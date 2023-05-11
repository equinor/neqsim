package neqsim.thermo.util.parameterFitting.Procede.CH4CO2WaterMDEA;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * CO2_MDEA_methane class.
 * </p>
 *
 * @author agrawalnj
 * @version $Id: $Id
 */
public class CO2_MDEA_methane {
  static Logger logger = LogManager.getLogger(CO2_MDEA_methane.class);

  /**
   * <p>
   * Constructor for CO2_MDEA_methane.
   * </p>
   */
  public CO2_MDEA_methane() {}

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
    int CH4Numb = 0;
    int CO2Numb = 0;
    int WaterNumb = 0;
    int MDEANumb = 0;
    int HCO3Numb = 0;
    int MDEAHpNumb = 0;
    int iter = 0;
    double error;
    /*
     * double pressure, n1,n2,n3; double MDEAwt = 35; double loading = 0.4; double temperature =
     * 313.0;
     */

    double newValue;
    double oldValue;
    double guess;
    double dx;
    double dP;
    double Pold;
    double Pnew;

    try (NeqSimDataBase database = new NeqSimDataBase();
    ResultSet dataSet = database.getResultSet("SELECT * FROM PatrickCO2");
    ) {
      while (dataSet.next()) {
        i += 1;
        logger.info("Adding.... " + i);

        double ID = Double.parseDouble(dataSet.getString("ID"));
        double pressureCO2 = Double.parseDouble(dataSet.getString("PressureCO2"));
        double pressure = Double.parseDouble(dataSet.getString("Pressure"));
        double temperature = Double.parseDouble(dataSet.getString("Temperature"));
        double n1 = Double.parseDouble(dataSet.getString("x1"));
        double n2 = Double.parseDouble(dataSet.getString("x2"));
        double n3 = Double.parseDouble(dataSet.getString("x3"));

        guess = n1 / 20;
        SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);

        testSystem.addComponent("CO2", n1);
        testSystem.addComponent("water", n2);
        testSystem.addComponent("methane", guess);
        testSystem.addComponent("MDEA", n3);

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
          CH4Numb = j;
          j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
            .equals("methane"));

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
          MDEAHpNumb = j;
          j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
            .equals("MDEA+"));

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        error = 1e10;
        dx = 1e-6;
        iter = 0;
        newValue = guess;
        oldValue = guess;

        do {
          // System.out.println("iteration..." + iter);
          iter += 1;
          oldValue = newValue;

          try {
            testOps.bubblePointPressureFlash(false);
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
          }
          Pold = testSystem.getPressure();
          // System.out.println("Pold "+Pold);

          testSystem.addComponent("methane",
              -testSystem.getPhase(1).getComponent(CH4Numb).getNumberOfmoles());
          testSystem.addComponent("methane", oldValue + dx);

          try {
            testOps.bubblePointPressureFlash(false);
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
          }

          Pnew = testSystem.getPressure();
          dP = (Pnew - Pold) / dx;
          newValue = oldValue - (Pold - pressure) / (dP);
          error = newValue - oldValue;

          testSystem.addComponent("methane",
              -testSystem.getPhase(1).getComponent(CH4Numb).getNumberOfmoles());
          testSystem.addComponent("methane", newValue);
        } while (Math.abs(error) > 1e-9 && iter < 50);

        j = 0;
        do {
          CO2Numb = j;
          j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
            .equals("CO2"));

        double aad = (pressureCO2
            - testSystem.getPressure() * testSystem.getPhase(0).getComponent(CO2Numb).getx())
            / pressureCO2 * 100;
        logger.info(ID + " "
            + testSystem.getPressure() * testSystem.getPhase(0).getComponent(CO2Numb).getx() + " "
            + pressureCO2 + " " + aad);
        /*
         * //System.out.println(testSystem.getPhase(1).getComponent(CO2Numb).getx()/
         * testSystem.getPhase(1).getComponent(MDEANumb).getx());
         * System.out.println("HCO3 "+testSystem.getPhase(1).getComponent(HCO3Numb).getx
         * ()+" "+testSystem.getPhase(0).getComponent(HCO3Numb).getx());
         * System.out.println("CO2 "+testSystem.getPhase(1).getComponent(CO2Numb).getx()
         * +" "+testSystem.getPhase(0).getComponent(CO2Numb).getx());
         * System.out.println("H2O "+testSystem.getPhase(1).getComponent(WaterNumb).getx
         * ()+" "+testSystem.getPhase(0).getComponent(WaterNumb).getx());
         * System.out.println("MDEA "+testSystem.getPhase(1).getComponent(MDEANumb).getx
         * ()+" "+testSystem.getPhase(0).getComponent(MDEANumb).getx());
         * //System.out.println("CH4 "+testSystem.getPhase(1).getComponent(CH4Numb).getx
         * ()+" "+testSystem.getPhase(0).getComponent(CH4Numb).getx());
         */
        // System.out.println(testSystem.getPressure()+" "+pressure+"
        // "+testSystem.getTemperature());

        // System.out.println("Bias dev. "+aad);

        try (PrintStream p =
            new PrintStream(new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
          // p.println(ID+" "+pressure+" "+pressureCO2+" "+" "+testSystem.getPressure()+"
          // "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx()+"
          // "+testSystem.getPhase(1).getComponent(CH4Numb).getx()+" "+iter);
          p.println(ID + " " + pressure + " " + pressureCO2 + " " + testSystem.getPressure() + " "
              + testSystem.getPressure() * testSystem.getPhase(0).getComponent(CO2Numb).getx());
          // p.println(ID+" "+pressure+" "+" "+testSystem.getPressure()+"
          // "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx());
        } catch (FileNotFoundException ex) {
          logger.error("Could not find file", ex);
          logger.error("Could not read from Patrick.txt", ex);
        }
      }
    } catch (Exception ex) {
      logger.error("database error ", ex);
    }
    logger.info("Finished");
  }
}
