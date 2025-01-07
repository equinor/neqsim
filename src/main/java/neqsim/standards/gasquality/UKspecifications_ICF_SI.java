package neqsim.standards.gasquality;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * UKspecifications_ICF_SI class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class UKspecifications_ICF_SI extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(UKspecifications_ICF_SI.class);

  String componentName = "";
  String unit = "-";
  Standard_ISO6976 iso6976 = null;
  double propaneNumber = 0.0;

  /**
   * <p>
   * Constructor for UKspecifications_ICF_SI.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public UKspecifications_ICF_SI(SystemInterface thermoSystem) {
    super("UKspecifications_ICF_SI", "UKspecifications_ICF_SI", thermoSystem);
    iso6976 = new Standard_ISO6976(thermoSystem, 15, 15, "volume");
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    iso6976.calculate();
    propaneNumber = calcPropaneNumber();
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if (returnParameter.equals("PropaneNumber")) {
      return propaneNumber;
    }
    if (returnParameter.equals("IncompleteCombustionFactor")) {
      return (iso6976.getValue("SuperiorWobbeIndex") / 1000.0 - 50.73 + 0.03 * propaneNumber)
          / 1.56;
    }
    if (returnParameter.equals("SootIndex")) {
      return 0.896 * Math.atan(0.0255 * thermoSystem.getPhase(0).getComponent("propane").getz()
          - 0.0233 * thermoSystem.getPhase(0).getComponent("nitrogen").getz() + 0.617);
    } else {
      return thermoSystem.getPhase(0).getComponent(componentName).getz();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    return thermoSystem.getPhase(0).getComponent(componentName).getz();
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    return unit;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return true;
  }

  /**
   * <p>
   * calcPropaneNumber.
   * </p>
   *
   * @return a double
   */
  public double calcPropaneNumber() {
    double avgCarbon = iso6976.getAverageCarbonNumber();

    double[][] Amatrix = {{1.0, 1.0}, {1.0, 3.0}};
    // {thermoSystem.getNumberOfMoles(), //
    // thermoSystem.getNumberOfMoles()*iso6976.getAverageCarbonNumber()}};
    double[] bmatrix = {(thermoSystem.getTotalNumberOfMoles() - iso6976.getTotalMolesOfInerts()),
        avgCarbon * (thermoSystem.getTotalNumberOfMoles() - iso6976.getTotalMolesOfInerts())};

    RealMatrix fmatrixJama = new Array2DRowRealMatrix(Amatrix);
    DecompositionSolver solver1 =
        new org.apache.commons.math3.linear.LUDecomposition(fmatrixJama).getSolver();
    RealMatrix ans2 = solver1.solve(new Array2DRowRealMatrix(bmatrix));

    double nitrogenCalc = calcWithNitrogenAsInert();
    System.out.println("nitrogen content pn " + nitrogenCalc);
    System.out.println("methane content pn " + ans2.getEntry(0, 0));

    System.out.println("propane content pn " + ans2.getEntry(1, 0));
    try {
      System.out.println("propane number "
          + (nitrogenCalc + ans2.getEntry(1, 0)) / thermoSystem.getTotalNumberOfMoles() * 100.0);
      return nitrogenCalc + ans2.getEntry(1, 0);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return 1.0;
  }

  /**
   * <p>
   * calcWithNitrogenAsInert.
   * </p>
   *
   * @return a double
   */
  public double calcWithNitrogenAsInert() {
    SystemInterface tempThermo = thermoSystem.clone();
    Standard_ISO6976 localIso6976 = new Standard_ISO6976(tempThermo);

    localIso6976.calculate();
    double targetWI = localIso6976.getValue("SuperiorWobbeIndex");
    if (!localIso6976.getThermoSystem().getPhase(0).hasComponent("nitrogen")) {
      localIso6976.getThermoSystem().addComponent("nitrogen",
          localIso6976.getThermoSystem().getNumberOfMoles() * 1e-50);
      localIso6976 = new Standard_ISO6976(tempThermo);
    }
    localIso6976.removeInertsButNitrogen();

    double newWI = targetWI / 1.01;
    double oldWI = 0.0;
    double dn2 = 0.1;
    // double dWIdN2;
    int iter = 0;
    do {
      iter++;
      double olddn2 = dn2;
      if (iter > 1) {
        dn2 = -(newWI - targetWI) / ((newWI - oldWI) / olddn2);
      } else {
        dn2 = 0.1;
      }
      oldWI = newWI;

      localIso6976.getThermoSystem().addComponent("nitrogen", dn2);
      localIso6976.getThermoSystem().init_x_y();
      localIso6976.calculate();
      newWI = localIso6976.getValue("SuperiorWobbeIndex");
      // dWIdN2 = newWI - oldWI / (1.0);
      System.out.println("WI " + newWI);
      System.out.println("error " + Math.abs(targetWI - newWI));
    } while (Math.abs(targetWI - newWI) > 1e-6 && iter < 100);
    // tempThermo.display();
    return tempThermo.getPhase(0).getComponent("nitrogen").getNumberOfmoles();
  }
}
