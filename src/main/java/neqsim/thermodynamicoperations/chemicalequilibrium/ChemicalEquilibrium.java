/*
 * ChemicalEquilibrium.java
 *
 * Created on 5. mai 2002, 20:53
 */

package neqsim.thermodynamicoperations.chemicalequilibrium;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ChemicalEquilibrium class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class ChemicalEquilibrium extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ChemicalEquilibrium.class);

  SystemInterface system;

  /**
   * <p>
   * Constructor for ChemicalEquilibrium.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ChemicalEquilibrium(SystemInterface system) {
    this.system = system;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    double chemdev = 0;
    int iter = 1;
    if (system.isChemicalSystem()) {
      double oldHeat = system.getChemicalReactionOperations().getReactionList()
          .reacHeat(system.getPhase(1), "HCO3-");
      do {
        iter++;
        for (int phaseNum = 1; phaseNum < system.getNumberOfPhases(); phaseNum++) {
          chemdev = 0.0;
          double xchem[] = new double[system.getPhase(phaseNum).getNumberOfComponents()];

          for (int i = 0; i < system.getPhase(phaseNum).getNumberOfComponents(); i++) {
            xchem[i] = system.getPhase(phaseNum).getComponent(i).getx();
          }

          system.init(1);
          system.getChemicalReactionOperations().solveChemEq(phaseNum);

          for (int i = 0; i < system.getPhase(phaseNum).getNumberOfComponents(); i++) {
            chemdev += Math.abs(xchem[i] - system.getPhase(phaseNum).getComponent(i).getx());
          }
        }
      } while (Math.abs(chemdev) > 1e-4 && iter < 100);
      double newHeat = system.getChemicalReactionOperations().getReactionList()
          .reacHeat(system.getPhase(1), "HCO3-");
      system.getChemicalReactionOperations().setDeltaReactionHeat(newHeat - oldHeat);
    }
    if (iter > 50) {
      logger.info("iter : " + iter + " in chemicalequilibrium");
    }
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    system.display();
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }
}
