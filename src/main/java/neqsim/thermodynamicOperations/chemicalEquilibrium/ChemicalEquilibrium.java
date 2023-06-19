/*
 * ChemicalEquilibrium.java
 *
 * Created on 5. mai 2002, 20:53
 */

package neqsim.thermodynamicOperations.chemicalEquilibrium;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.BaseOperation;

/**
 * <p>
 * ChemicalEquilibrium class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class ChemicalEquilibrium extends BaseOperation {
  private static final long serialVersionUID = 1000;
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
        for (int phase = 1; phase < system.getNumberOfPhases(); phase++) {
          chemdev = 0.0;
          double xchem[] = new double[system.getPhases()[phase].getNumberOfComponents()];

          for (int i = 0; i < system.getPhases()[phase].getNumberOfComponents(); i++) {
            xchem[i] = system.getPhases()[phase].getComponents()[i].getx();
          }

          system.init(1);
          system.getChemicalReactionOperations().solveChemEq(phase);

          for (int i = 0; i < system.getPhases()[phase].getNumberOfComponents(); i++) {
            chemdev += Math.abs(xchem[i] - system.getPhases()[phase].getComponents()[i].getx());
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
