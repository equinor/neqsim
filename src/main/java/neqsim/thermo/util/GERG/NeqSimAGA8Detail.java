package neqsim.thermo.util.GERG;

import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * NeqSimAGA8Detail class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class NeqSimAGA8Detail {
  double[] normalizedGERGComposition = new double[21 + 1];
  double[] notNormalizedGERGComposition = new double[21 + 1];
  PhaseInterface phase = null;
  DETAIL DETAIL = new DETAIL();

  /**
   * <p>
   * Constructor for NeqSimAGA8Detail.
   * </p>
   */
  public NeqSimAGA8Detail() {}

  /**
   * <p>
   * Constructor for NeqSimAGA8Detail.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public NeqSimAGA8Detail(PhaseInterface phase) {
    this.setPhase(phase);
    if (Double.isNaN(DETAIL.RDetail) || DETAIL.RDetail == 0) {
      DETAIL.SetupDetail();
    }
  }

  /**
   * <p>
   * getMolarDensity.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getMolarDensity(PhaseInterface phase) {
    this.setPhase(phase);
    return getMolarDensity();
  }

  /**
   * <p>
   * getDensity.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getDensity(PhaseInterface phase) {
    this.setPhase(phase);
    // return getMolarDensity() * getMolarMass() * 1000.0;
    return getMolarDensity() * phase.getMolarMass() * 1000.0;
  }

  /**
   * <p>
   * getDensity.
   * </p>
   *
   * @return a double
   */
  public double getDensity() {
    return getMolarDensity() * phase.getMolarMass() * 1000.0;
    // return getMolarDensity() * getMolarMass()* 1000.0;
  }

  /**
   * <p>
   * getPressure.
   * </p>
   *
   * @return a double
   */
  public double getPressure() {
    double moldens = getMolarDensity();
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    DETAIL.PressureDetail(phase.getTemperature(), moldens, normalizedGERGComposition, P, Z);
    return P.val;
  }

  /**
   * <p>
   * getMolarMass.
   * </p>
   *
   * @return a double
   */
  public double getMolarMass() {
    doubleW mm = new doubleW(0.0);
    DETAIL.MolarMassDetail(normalizedGERGComposition, mm);
    return mm.val / 1.0e3;
  }

  /**
   * <p>
   * getMolarDensity.
   * </p>
   *
   * @return a double
   */
  public double getMolarDensity() {
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    doubleW D = new doubleW(0.0);
    double pressure = phase.getPressure() * 100.0;
    DETAIL.DensityDetail(phase.getTemperature(), pressure,
    normalizedGERGComposition, D, ierr, herr);
    return D.val;
  }

  /**
   * <p>
   * propertiesDetail.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return an array of {@link double} objects
   */
  public double[] propertiesDetail(PhaseInterface phase) {
    this.setPhase(phase);
    return propertiesDetail();
  }

  /**
   * <p>
   * getProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param properties an array of {@link java.lang.String} objects
   * @return an array of {@link double} objects
   */
  public double[] getProperties(PhaseInterface phase, String[] properties) {
    // double molarDens = getMolarDensity(phase);
    double[] allProperties = propertiesDetail();
    double[] returnProperties = new double[properties.length];

    for (int i = 0; i < properties.length; i++) {
      switch (properties[i]) {
        case "density":
          returnProperties[i] = allProperties[0];
          break;
        case "Cp":
          returnProperties[i] = allProperties[1];
          break;
        case "Cv":
          returnProperties[i] = allProperties[2];
          break;
        case "soundSpeed":
          returnProperties[i] = allProperties[3];
          break;
      }
    }
    return returnProperties;
  }

  /**
   * <p>
   * propertiesDetail.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] propertiesDetail() {
    // int _x_offset = 0;
    doubleW p = new doubleW(0.0);
    doubleW z = new doubleW(0.0);
    doubleW dpdd = new doubleW(0.0);
    doubleW d2pdd2 = new doubleW(0.0);
    doubleW d2pdtd = new doubleW(0.0);
    doubleW dpdt = new doubleW(0.0);
    doubleW u = new doubleW(0.0);
    doubleW h = new doubleW(0.0);
    doubleW s = new doubleW(0.0);
    doubleW cv = new doubleW(0.0);
    doubleW cp = new doubleW(0.0);
    doubleW w = new doubleW(0.0);
    doubleW g = new doubleW(0.0);
    doubleW jt = new doubleW(0.0);
    doubleW kappa = new doubleW(0.0);
    // doubleW A = new doubleW(0.0);
    double dens = getMolarDensity();
    // neqsim.thermo.GERG.Densitygerg.densitygerg(0, 0, 0, arg3, 0, arg5, arg6,
    // arg7);
    DETAIL.PropertiesDetail(phase.getTemperature(), dens, normalizedGERGComposition, p, z, dpdd,
        d2pdd2, d2pdtd, dpdt, u, h, s, cv, cp, w, g, jt, kappa);
    double[] properties = new double[] {p.val, z.val, dpdd.val, d2pdd2.val, d2pdtd.val, dpdt.val,
        u.val, h.val, s.val, cv.val, cp.val, w.val, g.val, jt.val, kappa.val};
    return properties;
  }

  /**
   * <p>
   * Setter for the field <code>phase</code>.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setPhase(PhaseInterface phase) {
    this.phase = phase;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      String componentName = phase.getComponent(i).getComponentName();

      switch (componentName) {
        case "methane":
          notNormalizedGERGComposition[1] = phase.getComponent(i).getx();
          break;
        case "nitrogen":
          notNormalizedGERGComposition[2] = phase.getComponent(i).getx();
          break;
        case "CO2":
          notNormalizedGERGComposition[3] = phase.getComponent(i).getx();
          break;
        case "ethane":
          notNormalizedGERGComposition[4] = phase.getComponent(i).getx();
          break;
        case "propane":
          notNormalizedGERGComposition[5] = phase.getComponent(i).getx();
          break;
        case "i-butane":
          notNormalizedGERGComposition[6] = phase.getComponent(i).getx();
          break;
        case "n-butane":
          notNormalizedGERGComposition[7] = phase.getComponent(i).getx();
          break;
        case "i-pentane":
          notNormalizedGERGComposition[8] = phase.getComponent(i).getx();
          break;
        case "n-pentane":
          notNormalizedGERGComposition[9] = phase.getComponent(i).getx();
          break;
        case "n-hexane":
          notNormalizedGERGComposition[10] = phase.getComponent(i).getx();
          break;
        case "n-heptane":
          notNormalizedGERGComposition[11] = phase.getComponent(i).getx();
          break;
        case "n-octane":
          notNormalizedGERGComposition[12] = phase.getComponent(i).getx();
          break;
        case "n-nonane":
          notNormalizedGERGComposition[13] = phase.getComponent(i).getx();
          break;
        case "nC10":
          notNormalizedGERGComposition[14] = phase.getComponent(i).getx();
          break;
        case "hydrogen":
          notNormalizedGERGComposition[15] = phase.getComponent(i).getx();
          break;
        case "oxygen":
          notNormalizedGERGComposition[16] = phase.getComponent(i).getx();
          break;
        case "CO":
          notNormalizedGERGComposition[17] = phase.getComponent(i).getx();
          break;
        case "water":
          notNormalizedGERGComposition[18] = phase.getComponent(i).getx();
          break;
        case "H2S":
          notNormalizedGERGComposition[19] = phase.getComponent(i).getx();
          break;
        case "helium":
          notNormalizedGERGComposition[20] = phase.getComponent(i).getx();
          break;
        case "argon":
          notNormalizedGERGComposition[21] = phase.getComponent(i).getx();
          break;

        default:
          double molarMass = phase.getComponent(i).getMolarMass();
          if (molarMass > 44.096759796142 / 1000.0 && molarMass < 58.1236991882324 / 1000.0)
            notNormalizedGERGComposition[7] += phase.getComponent(i).getx();
          if (molarMass > 58.1236991882324 / 1000.0 && molarMass < 72.15064 / 1000.0)
            notNormalizedGERGComposition[8] += phase.getComponent(i).getx();
          if (molarMass > 72.15064 / 1000.0 && molarMass < 86.2 / 1000.0)
            notNormalizedGERGComposition[10] += phase.getComponent(i).getx();
          if (molarMass > 86.2 / 1000.0 && molarMass < 100.204498291016 / 1000.0)
            notNormalizedGERGComposition[11] += phase.getComponent(i).getx();
          if (molarMass > 100.204498291016 / 1000.0 && molarMass < 107.0 / 1000.0)
            notNormalizedGERGComposition[12] += phase.getComponent(i).getx();
          if (molarMass > 107.0 / 1000.0 && molarMass < 121.0 / 1000.0)
            notNormalizedGERGComposition[13] += phase.getComponent(i).getx();
          if (molarMass > 121.0 / 1000.0)
            notNormalizedGERGComposition[14] += phase.getComponent(i).getx();
          break;
      }
    }
    normalizeComposition();
  }

  /**
   * <p>
   * normalizeComposition.
   * </p>
   */
  public void normalizeComposition() {
    double result = 0;
    for (double value : notNormalizedGERGComposition) {
      result += value;
    }
    for (int k = 0; k < normalizedGERGComposition.length; k++) {
      normalizedGERGComposition[k] = notNormalizedGERGComposition[k] / result;
    }
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    // test HitHub
    SystemInterface fluid1 = new SystemSrkEos();
    fluid1.addComponent("methane", 90.0);
    fluid1.addComponent("hydrogen", 10.0);
    // fluid1.addComponent("CO2", 1.0);
    // fluid1.addComponent("ethane", 10.0);
    // fluid1.addComponent("propane", 3.0);
    // fluid1.addComponent("n-butane", 1.0);
    // fluid1.addComponent("oxygen", 1.0);
    /*
     * fluid1.addComponent("n-butane", 0.006304); fluid1.addComponent("i-butane", 0.003364);
     * fluid1.addComponent("n-pentane", 0.001005); fluid1.addComponent("i-pentane", 0.000994);
     * fluid1.addComponent("n-hexane", 0.000369); fluid1.addComponent("n-heptane", 0.000068);
     * fluid1.addComponent("n-octane", 0.000008);
     */
    // fluid//1.addComponent("ethane", 5.0);
    // fluid1.addComponent("propane", 5.0);
    // fluid1.addTBPfraction("C8", 0.01, 211.0/1000.0, 0.82);
    fluid1.setTemperature(273.15 + 20);
    fluid1.setPressure(50.0);
    fluid1.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid1);
    ops.TPflash();
    // fluid1.display();
    System.out.println("density GERG2008 Detail " + fluid1.getPhase(0).getDensity_GERG2008());
    System.out.println("density AGA8 Detail " + fluid1.getPhase(0).getDensity_AGA8());

    NeqSimAGA8Detail test = new NeqSimAGA8Detail(fluid1.getPhase("gas"));
    System.out.println("density " + test.getDensity());
    System.out.println("pressure " + test.getPressure());
    // System.out.println("properties " + test.propertiesGERG());
    double[] properties = test.propertiesDetail();
    System.out.println("Pressure [kPa]:            " + properties[0]);
    System.out.println("Compressibility factor:            " + properties[1]);
    System.out.println("d(P)/d(rho) [kPa/(mol/l)]            " + properties[2]);
    System.out.println("d^2(P)/d(rho)^2 [kPa/(mol/l)^2]:            " + properties[3]);
    System.out.println("d2(P)/d2(T) [kPa/K]:             " + properties[4]);
    System.out.println("d(P)/d(T) [kPa/K]:             " + properties[5]);
    System.out.println("Energy [J/mol]:             " + properties[6]);
    System.out.println("Enthalpy [J/mol]:             " + properties[7]);
    System.out.println("Entropy [J/mol-K]:             " + properties[8]);
    System.out.println("Isochoric heat capacity [J/mol-K]:             " + properties[9]);
    System.out.println("Isobaric heat capacity [J/mol-K]:            " + properties[10]);
    System.out.println("Speed of sound [m/s]:            " + properties[11]);
    System.out.println("Gibbs energy [J/mol]:            " + properties[12]);
    System.out.println("Joule-Thomson coefficient [K/kPa]:            " + properties[13]);
    System.out.println("Isentropic exponent:           " + properties[14]);
  }
}
