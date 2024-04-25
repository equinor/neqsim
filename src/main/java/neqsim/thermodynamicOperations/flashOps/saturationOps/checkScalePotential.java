package neqsim.thermodynamicOperations.flashOps.saturationOps;



import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * checkScalePotential class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class checkScalePotential extends constantDutyTemperatureFlash {
  private static final long serialVersionUID = 1000;
  

  String saltName;
  int phaseNumber = 1;
  String[][] resultTable = null;

  /**
   * <p>
   * Constructor for checkScalePotential.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumber a int
   */
  public checkScalePotential(SystemInterface system, int phaseNumber) {
    super(system);
    this.phaseNumber = phaseNumber;
    
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    double ksp = 0.0;

    resultTable = new String[10][3];
    double stoc1 = 1e-20;
    double stoc2 = 1e-20;
    String saltName = "";
    String name1 = "";
    String name2 = "";
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
/*
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database.getResultSet("SELECT * FROM compsalt")) {
      if (system.getPhase(phaseNumber).hasComponent("MEG")) {
        numberOfMolesMEG =
            system.getPhase(phaseNumber).getComponent("MEG").getNumberOfMolesInPhase();
        system.addComponent("MEG", -numberOfMolesMEG * 0.9999, phaseNumber);
        system.addComponent("water", numberOfMolesMEG, phaseNumber);
        system.init(1);
        system.getChemicalReactionOperations().solveChemEq(phaseNumber, 1);
      }
      while (dataSet.next()) {
        saltName = dataSet.getString("SaltName").trim();
        name1 = dataSet.getString("ion1").trim();
        name2 = dataSet.getString("ion2").trim();

        stoc1 = Double.parseDouble(dataSet.getString("stoc1"));
        stoc2 = Double.parseDouble(dataSet.getString("stoc2"));
        double temperatureC = system.getPhase(phaseNumber).getTemperature();
        double lnKsp = Double.parseDouble(dataSet.getString("Kspwater")) / temperatureC
            + Double.parseDouble(dataSet.getString("Kspwater2"))
            + Math.log(temperatureC) * Double.parseDouble(dataSet.getString("Kspwater3"))
            + temperatureC * Double.parseDouble(dataSet.getString("Kspwater4"))
            + Double.parseDouble(dataSet.getString("Kspwater5")) / (temperatureC * temperatureC);
        ksp = Math.exp(lnKsp);

        if (saltName.equals("NaCl")) {
          ksp = -814.18 + 7.4685 * temperatureC - 2.3262e-2 * temperatureC * temperatureC
              + 3.0536e-5 * Math.pow(temperatureC, 3.0) - 1.4573e-8 * Math.pow(temperatureC, 4.0);
        }
        if (saltName.equals("FeS")) {
          int waterompNumb =
              system.getPhase(phaseNumber).getComponent("water").getComponentNumber();
          double h3ox = system.getPhase(phaseNumber).getComponent("H3O+").getx()
              / (system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                  * system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass());

          ksp *= h3ox;
        }

        if (system.getPhase(phaseNumber).hasComponent(name1)
            && system.getPhase(phaseNumber).hasComponent(name2)) {
          numb++;
          
          
          
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
          double kspReac = Math.pow(
              system.getPhase(phaseNumber).getActivityCoefficient(compNumb1, waterompNumb) * x1,
              stoc1)
              * Math.pow(
                  x2 * system.getPhase(phaseNumber).getActivityCoefficient(compNumb2, waterompNumb),
                  stoc2);
          // double kspReac =
          // Math.pow(system.getPhase(phaseNumber).getActivityCoefficient(compNumb1) * x1,
          // stoc1) * Math.pow(x2 *
          // system.getPhase(phaseNumber).getActivityCoefficient(compNumb2), stoc2);

          double stocKsp = Math.pow(x1, stoc1) * Math.pow(x2, stoc2);

          if (saltName.contains("hydromagnesite (3MgCO3-Mg(OH)2-3H2O)")) {
            x1 = system.getPhase(phaseNumber).getComponent(name1).getx()
                / (system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                    * system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass());
            x2 = system.getPhase(phaseNumber).getComponent(name2).getx()
                / (system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                    * system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass());
            double x3 = system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                / (system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                    * system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass());
            double x4 = system.getPhase(phaseNumber).getComponent("OH-").getx()
                / (system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                    * system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass());
            kspReac = Math
                .pow(system.getPhase(phaseNumber).getActivityCoefficient(compNumb1, waterompNumb)
                    * x1, stoc1)
                * Math.pow(x2
                    * system.getPhase(phaseNumber).getActivityCoefficient(compNumb2, waterompNumb),
                    stoc2)
                * Math
                    .pow(x3 * system.getPhase(phaseNumber).getActivityCoefficient(waterompNumb,
                        waterompNumb), 3.0)
                * Math.pow(x4 * system.getPhase(phaseNumber).getActivityCoefficient(
                    system.getPhase(phaseNumber).getComponent("OH-").getComponentNumber(),
                    waterompNumb), 2.0);
            stocKsp = Math.pow(x1, stoc1) * Math.pow(x2, stoc2) * Math.pow(x3, 3) * Math.pow(x4, 2);
          }

          
          
          
          

          double scalePotentialFactor = kspReac / ksp;
          

          resultTable[numb][0] = saltName; // name1+ " " +name2;
          resultTable[numb][1] = Double.toString(scalePotentialFactor);
          resultTable[numb][2] = "";
          // double maxn = scalePotentialFactor/(stoc1*stoc2);

          // double x1max =system.getPhase(phaseNumber).getComponent(name1).getx()/maxn;
          // double x2max =system.getPhase(phaseNumber).getComponent(name2).getx()/maxn;
        }
      }
    } catch (Exception ex) {
      

      if (system.getPhase(phaseNumber).hasComponent("MEG")) {
        system.addComponent("MEG", numberOfMolesMEG * 0.9999, phaseNumber);
        system.addComponent("water", -numberOfMolesMEG, phaseNumber);
        system.init(1);
        system.getChemicalReactionOperations().solveChemEq(phaseNumber, 1);
      }
    }
    */
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    
    
    
    
    
    
    return resultTable;
  }
}
