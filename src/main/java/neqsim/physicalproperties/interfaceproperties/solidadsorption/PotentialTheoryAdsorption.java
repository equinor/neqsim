package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PotentialTheoryAdsorption class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PotentialTheoryAdsorption implements AdsorptionInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PotentialTheoryAdsorption.class);

  SystemInterface system;
  double[] eps0; // = 7.458; //7.630; // J/mol
  double[] z0; // = 3.284; // * 1e-3; // m^3/kg
  double[] beta; // = 2.0;
  int integrationSteps = 500;
  double totalSurfaceExcess;
  double[][] compositionSurface;
  double[][] fugacityField;
  double[][] zField;
  double[][] epsField;
  double[] pressureField;
  double[] surfaceExcess;
  double[] surfaceExcessMolFraction;
  double[] deltaz;
  String solidMaterial = "AC";

  /**
   * <p>
   * Constructor for PotentialTheoryAdsorption.
   * </p>
   */
  public PotentialTheoryAdsorption() {}

  /**
   * <p>
   * Constructor for PotentialTheoryAdsorption.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public PotentialTheoryAdsorption(SystemInterface system) {
    this.system = system;
    compositionSurface = new double[integrationSteps][system.getPhase(0).getNumberOfComponents()];
    pressureField = new double[integrationSteps];
    zField = new double[system.getPhase(0).getNumberOfComponents()][integrationSteps];
    epsField = new double[system.getPhase(0).getNumberOfComponents()][integrationSteps];
    fugacityField = new double[system.getPhase(0).getNumberOfComponents()][integrationSteps];
    deltaz = new double[system.getPhase(0).getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public void setSolidMaterial(String solidM) {
    solidMaterial = solidM;
  }

  /** {@inheritDoc} */
  @Override
  public void calcAdsorption(int phaseNum) {
    SystemInterface tempSystem = system.clone();
    tempSystem.init(3);
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();

    surfaceExcess = new double[numComp];
    surfaceExcessMolFraction = new double[numComp];

    eps0 = new double[numComp];
    z0 = new double[numComp];
    beta = new double[numComp];

    readDBParameters();

    double[] bulkFug = new double[numComp];
    double[] corrx = new double[numComp];
    for (int comp = 0; comp < numComp; comp++) {
      bulkFug[comp] = system.getPhase(phaseNum).getComponent(comp).getx()
          * system.getPhase(phaseNum).getComponent(comp).getFugacityCoefficient()
          * system.getPhase(phaseNum).getPressure();
      deltaz[comp] = z0[comp] / (integrationSteps * 1.0);
      zField[comp][0] = z0[comp];
      for (int i = 0; i < integrationSteps; i++) {
        zField[comp][i] = zField[comp][0] - deltaz[comp] * i;
        epsField[comp][i] =
            eps0[comp] * Math.pow(Math.log(z0[comp] / zField[comp][i]), 1.0 / beta[comp]);
      }
    }
    for (int i = 0; i < integrationSteps; i++) {
      int iter = 0;
      double sumx = 0;
      double pressure = 0;
      do {
        iter++;
        sumx = 0.0;
        pressure = 0.0;
        for (int comp = 0; comp < numComp; comp++) {
          double correction =
              Math.exp(epsField[comp][i] / R / system.getPhase(phaseNum).getTemperature());
          fugacityField[comp][i] = correction * bulkFug[comp];
          double fugComp = tempSystem.getPhase(phaseNum).getComponent(comp).getFugacityCoefficient()
              * tempSystem.getPhase(phaseNum).getPressure();
          corrx[comp] = fugacityField[comp][i] / fugComp;
          pressure += fugacityField[comp][i]
              / tempSystem.getPhase(phaseNum).getComponent(comp).getFugacityCoefficient();
        }
        for (int comp = 0; comp < numComp; comp++) {
          tempSystem.getPhase(phaseNum).getComponent(comp).setx(corrx[comp]);
          sumx += corrx[comp];
        }
        tempSystem.setPressure(pressure);
        // tempSystem.getPhase(phase).normalize();
        // tempSystem.calc_x_y();
        tempSystem.init(1);
        // System.out.println("pressure " + tempSystem.getPressure() + " error " +
        // Math.abs(sumx - 1.0));
      } while (Math.abs(sumx - 1.0) > 1e-12 && iter < 100);

      for (int comp = 0; comp < numComp; comp++) {
        surfaceExcess[comp] +=
            deltaz[comp] * (1.0e5 / tempSystem.getPhase(phaseNum).getMolarVolume()
                * tempSystem.getPhase(phaseNum).getComponent(comp).getx()
                - 1.0e5 / system.getPhase(phaseNum).getMolarVolume()
                    * system.getPhase(phaseNum).getComponent(comp).getx());
      }
    }

    totalSurfaceExcess = 0.0;
    for (int comp = 0; comp < numComp; comp++) {
      totalSurfaceExcess += surfaceExcess[comp];
    }
    for (int comp = 0; comp < numComp; comp++) {
      surfaceExcessMolFraction[comp] = surfaceExcess[comp] / totalSurfaceExcess;
      // logger.info("surface excess molfrac " + surfaceExcessMolFraction[comp] + "
      // mol/kg adsorbent " + surfaceExcess[comp]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getSurfaceExcess(int component) {
    throw new UnsupportedOperationException("Unimplemented method 'getSurfaceExcess'");
  }

  /** {@inheritDoc} */
  @Override
  public double getSurfaceExcess(String componentName) {
    int componentNumber = system.getPhase(0).getComponent(componentName).getComponentNumber();
    return surfaceExcess[componentNumber];
  }

  /**
   * <p>
   * readDBParameters.
   * </p>
   */
  public void readDBParameters() {
    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
          java.sql.ResultSet dataSet =
              database.getResultSet(("SELECT * FROM adsorptionparameters WHERE name='"
                  + system.getPhase(0).getComponent(comp).getComponentName() + "' AND Solid='"
                  + solidMaterial + "'"))) {
        dataSet.next();

        eps0[comp] = Double.parseDouble(dataSet.getString("eps"));
        beta[comp] = Double.parseDouble(dataSet.getString("z0"));
        z0[comp] = Double.parseDouble(dataSet.getString("beta"));

        logger.info("adsorption parameters read ok for "
            + system.getPhase(0).getComponent(comp).getComponentName() + " eps " + eps0[comp]);
      } catch (Exception ex) {
        logger.info("Component not found in adsorption DB "
            + system.getPhase(0).getComponent(comp).getComponentName() + " on solid "
            + solidMaterial);
        logger.info("using default parameters");
        eps0[comp] = 7.2;
        beta[comp] = 2.0;
        z0[comp] = 3.2;
        // logger.error(ex.getMessage(), ex);
      }
    }
  }
}
