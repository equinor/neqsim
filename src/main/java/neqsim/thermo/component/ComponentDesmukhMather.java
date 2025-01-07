package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseDesmukhMather;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentDesmukhMather class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentDesmukhMather extends ComponentGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double deshMathIonicDiameter = 1.0;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentDesmukhMather.class);

  /**
   * <p>
   * Constructor for ComponentDesmukhMather.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentDesmukhMather(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    java.sql.ResultSet dataSet = null;

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      if (!name.equals("default")) {
        try {
          dataSet = database.getResultSet(("SELECT * FROM comptemp WHERE name='" + name + "'"));
          dataSet.next();
          dataSet.getString("FORMULA");
        } catch (Exception ex) {
          dataSet.close();
          logger.info("no parameters in tempcomp -- trying comp.. " + name);
          dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + name + "'"));
          dataSet.next();
        }
        deshMathIonicDiameter = Double.parseDouble(dataSet.getString("DeshMatIonicDiameter"));
      }
    } catch (Exception ex) {
      logger.error("error in comp");
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam,
      String[][] mixRule) {
    // todo: not actually implemented
    return getGamma(phase, numberOfComponents, temperature, pressure, pt);
  }

  /**
   * <p>
   * getGamma.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @return a double
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    double A = 1.174;

    double B = 3.32384e9;
    double Iion = ((PhaseDesmukhMather) phase).getIonicStrength();
    double temp = 0.0;

    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (!phase.getComponent(i).getComponentName().equals("water")) {
        temp += 2.0 * ((PhaseDesmukhMather) phase).getBetaDesMatij(i, getComponentNumber())
            * phase.getComponent(i).getMolality(phase); // phase.getComponent(i).getMolarity(phase);
      }
    }
    // System.out.println("molality MDEA "+
    // phase.getComponent("MDEA").getMolality(phase));

    lngamma = -A * Math.pow(getIonicCharge(), 2.0) * Math.sqrt(Iion)
        / (1.0 + B * deshMathIonicDiameter * 1e-10 * Math.sqrt(Iion)) + temp;
    // else lngamma = 0.0;
    // System.out.println("temp2 "+
    // -2.303*A*Math.pow(getIonicCharge(),2.0)*Math.sqrt(Iion)/(1.0+B*Math.sqrt(Iion)));
    gamma = getMolality(phase) * ((PhaseDesmukhMather) phase).getSolventMolarMass()
        * Math.exp(lngamma) / getx();
    lngamma = Math.log(gamma);
    logger.info("gamma " + componentName + " " + gamma);
    return gamma;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    // System.out.println("fug coef " +
    // gamma*getAntoineVaporPressure(phase.getTemperature())/phase.getPressure());
    if (componentName.equals("water")) {
      double watervol = 1.0 / 1000.0 * getMolarMass();
      double watervappres = getAntoineVaporPressure(phase.getTemperature());
      fugacityCoefficient = gamma * watervappres
          * Math.exp(
              watervol / (R * phase.getTemperature()) * (phase.getPressure() - watervappres) * 1e5)
          / phase.getPressure();
    } else if (ionicCharge == 0 && referenceStateType.equals("solvent")) {
      fugacityCoefficient =
          gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure();
    } else if (ionicCharge == 0 && referenceStateType.equals("solute")) {
      // TODO: sjekk denne
      fugacityCoefficient = gamma * getHenryCoef(phase.getTemperature()) / phase.getPressure();
    } else {
      fugacityCoefficient = 1e-15;
      // System.out.println("fug " + fugacityCoefficient);
    }

    return fugacityCoefficient;
  }

  /**
   * Getter for property lngamma.
   *
   * @return Value of property lngamma.
   */
  public double getLngamma() {
    return lngamma;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolality(PhaseInterface phase) {
    return getNumberOfMolesInPhase() / ((PhaseDesmukhMather) phase).getSolventWeight();
  }
}
