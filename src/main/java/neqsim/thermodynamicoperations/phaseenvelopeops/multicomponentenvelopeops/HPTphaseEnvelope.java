/*
 * HPTphaseEnvelope.java
 *
 * Created on 14. oktober 2000, 21:59
 */

package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import java.awt.FlowLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * HPTphaseEnvelope class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class HPTphaseEnvelope extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HPTphaseEnvelope.class);

  double[][] points = new double[10][10];
  SystemInterface system;
  ThermodynamicOperations testOps;
  JProgressBar monitor;
  JFrame mainFrame;
  JPanel mainPanel;
  double startPressure = 1;
  double endPressure = 0;
  double startTemperature = 160;
  double endTemperature = 0;

  /**
   * <p>
   * Constructor for HPTphaseEnvelope.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public HPTphaseEnvelope(SystemInterface system) {
    testOps = new ThermodynamicOperations(system);
    this.system = system;
    mainFrame = new JFrame("Progress Bar");
    mainPanel = new JPanel();
    mainPanel.setSize(200, 100);
    mainFrame.getContentPane().setLayout(new FlowLayout());
    mainPanel.setLayout(new FlowLayout());
    mainFrame.setSize(200, 100);
    monitor = new JProgressBar(0, 1000);
    monitor.setSize(200, 100);
    monitor.setStringPainted(true);
    mainPanel.add(monitor);
    mainFrame.getContentPane().add(mainPanel);
    mainFrame.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int np = 0;

    for (int i = 0; i < 10; i++) {
      system.setPressure(i * 0.5 + startPressure);
      for (int j = 0; j < 10; j++) {
        np++;
        if (np % 2 == 0) {
          monitor.setValue(np);
          monitor.setString("Calculated points: " + np);
        }

        system.setTemperature(startTemperature + j);
        testOps.TPflash();
        system.init(3);
        points[i][j] = system.getEnthalpy();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    /*
     * try { mainFrame.setVisible(false); visAd3DPlot plot = new visAd3DPlot("pressure[bar]",
     * "temperature[K]", "enthalpy[J/mol]"); plot.setXYvals(150, 160, 10, 10, 20, 10);
     * plot.setZvals(points); plot.init(); } catch (Exception ex) { logger.error("plotting failed");
     * }
     */
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return points;
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
