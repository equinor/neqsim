package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * addIonToScaleSaturation class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class AddIonToScaleSaturation extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AddIonToScaleSaturation.class);

  String saltName = "";
  String scaleSaltName = "";
  String nameOfIonToBeAdded = "";
  int phaseNumber = 1;
  String[][] resultTable = null;

  /**
   * <p>
   * Constructor for addIonToScaleSaturation.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumber a int
   * @param scaleSaltName a {@link java.lang.String} object
   * @param nameOfIonToBeAdded a {@link java.lang.String} object
   */
  public AddIonToScaleSaturation(SystemInterface system, int phaseNumber, String scaleSaltName,
      String nameOfIonToBeAdded) {
    super(system);
    this.phaseNumber = phaseNumber;
    this.scaleSaltName = scaleSaltName;
    this.nameOfIonToBeAdded = nameOfIonToBeAdded;
    logger.info("ok ");
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    double ksp = 0.0;
    resultTable = new String[10][3];
    double stoc1 = 1e-20;
    double stoc2 = 1e-20;
    String saltName = "";
    String name1 = "";
    String name2 = "";
    system.init(1);
    int numb = 0;

    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 3; j++) {
        resultTable[i][j] = "";
      }
    }

    resultTable[0][0] = "Salt";
    resultTable[0][1] = "relative solubility";
    resultTable[0][2] = "";

    double numberOfMolesMEG = 0.0;
    if (system.getPhase(phaseNumber).hasComponent("MEG")) {
      numberOfMolesMEG = system.getPhase(phaseNumber).getComponent("MEG").getNumberOfMolesInPhase();
      system.addComponent("MEG", -numberOfMolesMEG * 0.9999, phaseNumber);
      system.addComponent("water", numberOfMolesMEG, phaseNumber);
      system.init(1);
      system.getChemicalReactionOperations().solveChemEq(phaseNumber, 1);
    }

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database.getResultSet("SELECT * FROM compsalt")) {
      while (dataSet.next()) {
        saltName = dataSet.getString("SaltName").trim();
        name1 = dataSet.getString("ion1").trim();
        name2 = dataSet.getString("ion2").trim();
        iterations++;
        stoc1 = Double.parseDouble(dataSet.getString("stoc1"));
        stoc2 = Double.parseDouble(dataSet.getString("stoc2"));
        double temperatureC = system.getPhase(phaseNumber).getTemperature();
        ksp = Math.exp(Double.parseDouble(dataSet.getString("Ksp-water")) / temperatureC
            + Double.parseDouble(dataSet.getString("Ksp-water2"))
            + Math.log(temperatureC) * Double.parseDouble(dataSet.getString("Ksp-water3"))
            + temperatureC * Double.parseDouble(dataSet.getString("Ksp-water4"))
            + Double.parseDouble(dataSet.getString("Ksp-water5")) / (temperatureC * temperatureC));
        if (saltName.equals("NaCl")) {
          ksp = -814.18 + 7.4685 * temperatureC - 2.3262e-2 * temperatureC * temperatureC
              + 3.0536e-5 * Math.pow(temperatureC, 3.0) - 1.4573e-8 * Math.pow(temperatureC, 4.0);
        }

        if (system.getPhase(phaseNumber).hasComponent(name1)
            && system.getPhase(phaseNumber).hasComponent(name2)) {
          numb++;
          logger.info("reaction added: " + name1 + " " + name2);
          logger.info("theoretic Ksp = " + ksp);
          double oldScalePotentialFactor = 1.0;
          double error = 1.0;
          int iterations = 0;
          do {
            iterations++;
            logger.info("theoretic lnKsp = " + Math.log(ksp));
            int compNumb1 = system.getPhase(phaseNumber).getComponent(name1).getComponentNumber();
            int compNumb2 = system.getPhase(phaseNumber).getComponent(name2).getComponentNumber();
            int waterompNumb =
                system.getPhase(phaseNumber).getComponent("water").getComponentNumber();

            double x1 = system.getPhase(phaseNumber).getComponent(name1).getx()
                / (system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                    * system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass());
            double x2 = system.getPhase(phaseNumber).getComponent(name2).getx()
                / (system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                    * system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass());
            double kspReac = Math
                .pow(system.getPhase(phaseNumber).getActivityCoefficient(compNumb1, waterompNumb)
                    * x1, stoc1)
                * Math.pow(x2
                    * system.getPhase(phaseNumber).getActivityCoefficient(compNumb2, waterompNumb),
                    stoc2);
            double stocKsp = Math.pow(x1, stoc1) * Math.pow(x2, stoc2);
            logger.info("calc Ksp " + kspReac);
            logger.info("stoc Ksp " + stocKsp);
            logger.info("activity " + kspReac / stocKsp);
            logger.info("mol/kg " + x1);

            double scalePotentialFactor = kspReac / ksp;

            error = (scalePotentialFactor - oldScalePotentialFactor) / scalePotentialFactor * 100;
            oldScalePotentialFactor = scalePotentialFactor;
            logger.info("Scale potential factor " + scalePotentialFactor);
            resultTable[numb][0] = name1 + " " + name2;
            resultTable[numb][1] = Double.toString(scalePotentialFactor);
            resultTable[numb][2] = "";

            if (saltName.equals(scaleSaltName)) {
              logger.info("error " + error);
              logger.info("pH : " + system.getPhase(phaseNumber).getpH());
              system.addComponent(nameOfIonToBeAdded, (1.0 - scalePotentialFactor) / 100000.0,
                  phaseNumber);
              ops.TPflash();
              logger.info("x1 " + system.getPhase(phaseNumber).getComponent(name1).getx());
              logger.info("x2 " + system.getPhase(phaseNumber).getComponent(name2).getx());
            }
          } while (saltName.equals(scaleSaltName) && Math.abs(error) > 1e-6 && iterations < 200);
        }
      }
    } catch (Exception ex) {
      logger.info("failed ", ex);
    }

    if (system.getPhase(phaseNumber).hasComponent("MEG")) {
      system.addComponent("MEG", numberOfMolesMEG * 0.9999, phaseNumber);
      system.addComponent("water", -numberOfMolesMEG, phaseNumber);
      system.init(1);
      system.getChemicalReactionOperations().solveChemEq(phaseNumber, 1);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    logger.info("checking table...scale " + resultTable[0][0]);
    logger.info("checking table...scale " + resultTable[0][1]);
    logger.info("checking table...scale " + resultTable[0][2]);
    logger.info("checking table...scale " + resultTable[1][0]);
    logger.info("checking table...scale " + resultTable[1][1]);
    logger.info("checking table...scale " + resultTable[1][2]);
    return resultTable;
  }
}
