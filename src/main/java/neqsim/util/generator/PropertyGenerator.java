package neqsim.util.generator;

import java.util.HashMap;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * PropertyGenerator class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PropertyGenerator {
  double[] temperatures, pressures;
  SystemInterface fluid = null;
  HashMap<String, double[]> properties = new HashMap<String, double[]>();

  /**
   * <p>
   * Constructor for PropertyGenerator.
   * </p>
   *
   * @param fluid a {@link neqsim.thermo.system.SystemInterface} object
   * @param temperatures an array of type double
   * @param pressures an array of type double
   */
  public PropertyGenerator(SystemInterface fluid, double[] temperatures, double[] pressures) {
    this.fluid = fluid;
    this.temperatures = temperatures;
    this.pressures = pressures;
  }

  /**
   * <p>
   * calculate.
   * </p>
   *
   * @return a {@link java.util.HashMap} object
   */
  public HashMap<String, double[]> calculate() {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    int length = temperatures.length;
    double[] molarmass = new double[length];
    double[] molarmassGas = new double[length];
    double[] molarmassOil = new double[length];
    double[] molarmassAqueous = new double[length];
    double[] numberOfPhases = new double[length];
    double[] locpressure = new double[length];
    double[] loctemperature = new double[length];
    double[] molefraction = new double[length];
    double[] molarVolume = new double[length];
    double[] gamma = new double[length];
    double[] thermalconductivity = new double[length];

    double[] soundspeedGas = new double[length];
    double[] wtfractionGas = new double[length];
    double[] molefractionGas = new double[length];
    double[] molarvolumeGas = new double[length];
    double[] volumefractionGas = new double[length];
    double[] gammaGas = new double[length];
    double[] joulethomsoncoefficientGas = new double[length];
    double[] thermalconductivitygas = new double[length];

    double[] soundspeedOil = new double[length];
    double[] wtfractionOil = new double[length];
    double[] molefractionOil = new double[length];
    double[] molarvolumeOil = new double[length];
    double[] volumefractionOil = new double[length];
    double[] gammaOil = new double[length];
    double[] joulethomsoncoefficientOil = new double[length];
    double[] thermalconductivityOil = new double[length];

    double[] soundspeedAqueous = new double[length];
    double[] wtfractionAqueous = new double[length];
    double[] molefractionAqueous = new double[length];
    double[] molarvolumeAqueous = new double[length];
    double[] volumefractionAqueous = new double[length];
    double[] gammaAqueous = new double[length];
    double[] joulethomsoncoefficientAqueous = new double[length];
    double[] thermalconductivityAqueous = new double[length];

    double[] Z = new double[length];
    double[] ZGas = new double[length];
    double[] ZOil = new double[length];
    double[] ZAqueous = new double[length];

    double[] viscosity = new double[length];
    double[] viscosityGas = new double[length];
    double[] viscosityOil = new double[length];
    double[] viscosityAqueous = new double[length];

    double[] enthalpy = new double[length];
    double[] enthalpyGas = new double[length];
    double[] enthalpyOil = new double[length];
    double[] enthalpyAqueous = new double[length];

    double[] entropy = new double[length];
    double[] entropyGas = new double[length];
    double[] entropyOil = new double[length];
    double[] entropyAqueous = new double[length];

    double[] Cp = new double[length];
    double[] CpGas = new double[length];
    double[] CpOil = new double[length];
    double[] CpAqueous = new double[length];

    double[] Cv = new double[length];
    double[] CvGas = new double[length];
    double[] CvOil = new double[length];
    double[] CvAqueous = new double[length];

    double[] density = new double[length];
    double[] densityGas = new double[length];
    double[] densityOil = new double[length];
    double[] densityAqueous = new double[length];

    for (int i = 0; i < length; i++) {
      fluid.setTemperature(temperatures[i]);
      fluid.setPressure(pressures[i]);
      try {
        ops.TPflash();
        fluid.initProperties();
      } catch (Exception ex) {
        continue;
      }
      molarmass[i] = fluid.getMolarMass();
      Z[i] = fluid.getZ();
      viscosity[i] = fluid.getViscosity("cP");
      enthalpy[i] = fluid.getEnthalpy("J/mol");
      entropy[i] = fluid.getEntropy("J/molK");
      Cp[i] = fluid.getCp("kJ/kgK");
      Cv[i] = fluid.getCv("kJ/kgK");
      density[i] = fluid.getDensity("kg/m3");
      numberOfPhases[i] = fluid.getNumberOfPhases();
      locpressure[i] = fluid.getPressure("Pa");
      loctemperature[i] = fluid.getTemperature("K");
      molefraction[i] = fluid.getMoleFractionsSum();
      molarVolume[i] = 1.0 / fluid.getDensity("mol/m3");
      gamma[i] = fluid.getGamma();
      thermalconductivity[i] = fluid.getThermalConductivity("W/mK");

      if (fluid.hasPhaseType("gas")) {
        int phasenumb = fluid.getPhaseNumberOfPhase("gas");
        molarmassGas[i] = fluid.getPhase(phasenumb).getMolarMass();
        ZGas[i] = fluid.getPhase(phasenumb).getZ();
        viscosityGas[i] = fluid.getPhase(phasenumb).getViscosity("kg/msec");
        enthalpyGas[i] = fluid.getPhase(phasenumb).getEnthalpy("J/mol");
        entropyGas[i] = fluid.getPhase(phasenumb).getEntropy("J/molK");
        CpGas[i] = fluid.getPhase(phasenumb).getCp("kJ/kgK");
        CvGas[i] = fluid.getPhase(phasenumb).getCv("kJ/kgK");
        densityGas[i] = fluid.getPhase(phasenumb).getDensity("kg/m3");

        soundspeedGas[i] = fluid.getPhase(phasenumb).getSoundSpeed();
        wtfractionGas[i] = fluid.getWtFraction(phasenumb);
        molefractionGas[i] = fluid.getMoleFraction(phasenumb);
        molarvolumeGas[i] = 1.0 / fluid.getPhase(phasenumb).getDensity("mol/m3");
        volumefractionGas[i] = fluid.getCorrectedVolumeFraction(phasenumb);
        gammaGas[i] = fluid.getPhase(phasenumb).getKappa();
        joulethomsoncoefficientGas[i] =
            fluid.getPhase(phasenumb).getJouleThomsonCoefficient() / 1e5;
        thermalconductivitygas[i] = fluid.getPhase(phasenumb).getThermalConductivity("W/mK");
      } else {
        molarmassGas[i] = Double.NaN;
        ZGas[i] = Double.NaN;
        viscosityGas[i] = Double.NaN;
        enthalpyGas[i] = Double.NaN;
        entropyGas[i] = Double.NaN;
        CpGas[i] = Double.NaN;
        CvGas[i] = Double.NaN;
        densityGas[i] = Double.NaN;

        soundspeedGas[i] = Double.NaN;
        wtfractionGas[i] = Double.NaN;
        molefractionGas[i] = Double.NaN;
        molarvolumeGas[i] = Double.NaN;
        volumefractionGas[i] = Double.NaN;
        gammaGas[i] = Double.NaN;
        joulethomsoncoefficientGas[i] = Double.NaN;
        thermalconductivitygas[i] = Double.NaN;
      }
      if (fluid.hasPhaseType("oil")) {
        int phasenumb = fluid.getPhaseNumberOfPhase("oil");
        molarmassOil[i] = fluid.getPhase(phasenumb).getMolarMass();
        ZOil[i] = fluid.getPhase(phasenumb).getZ();
        viscosityOil[i] = fluid.getPhase(phasenumb).getViscosity("kg/msec");
        enthalpyOil[i] = fluid.getPhase(phasenumb).getEnthalpy("J/mol");
        entropyOil[i] = fluid.getPhase(phasenumb).getEntropy("J/molK");
        CpOil[i] = fluid.getPhase(phasenumb).getCp("kJ/kgK");
        CvOil[i] = fluid.getPhase(phasenumb).getCv("kJ/kgK");
        densityOil[i] = fluid.getPhase(phasenumb).getDensity("kg/m3");

        soundspeedOil[i] = fluid.getPhase(phasenumb).getSoundSpeed();
        wtfractionOil[i] = fluid.getWtFraction(phasenumb);
        molefractionOil[i] = fluid.getMoleFraction(phasenumb);
        molarvolumeOil[i] = 1.0 / fluid.getPhase(phasenumb).getDensity("mol/m3");
        volumefractionOil[i] = fluid.getCorrectedVolumeFraction(phasenumb);
        gammaOil[i] = fluid.getPhase(phasenumb).getKappa();
        joulethomsoncoefficientOil[i] =
            fluid.getPhase(phasenumb).getJouleThomsonCoefficient() / 1e5;
        thermalconductivityOil[i] = fluid.getPhase(phasenumb).getThermalConductivity("W/mK");
      } else {
        molarmassOil[i] = Double.NaN;
        ZOil[i] = Double.NaN;
        viscosityOil[i] = Double.NaN;
        enthalpyOil[i] = Double.NaN;
        entropyOil[i] = Double.NaN;
        CpOil[i] = Double.NaN;
        CvOil[i] = Double.NaN;
        densityOil[i] = Double.NaN;

        soundspeedOil[i] = Double.NaN;
        wtfractionOil[i] = Double.NaN;
        molefractionOil[i] = Double.NaN;
        molarvolumeOil[i] = Double.NaN;
        volumefractionOil[i] = Double.NaN;
        gammaOil[i] = Double.NaN;
        joulethomsoncoefficientOil[i] = Double.NaN;
        thermalconductivityOil[i] = Double.NaN;
      }
      if (fluid.hasPhaseType("aqueous")) {
        int phasenumb = fluid.getPhaseNumberOfPhase("aqueous");
        molarmassAqueous[i] = fluid.getPhase(phasenumb).getMolarMass();
        ZAqueous[i] = fluid.getPhase(phasenumb).getZ();
        viscosityAqueous[i] = fluid.getPhase(phasenumb).getViscosity("kg/msec");
        enthalpyAqueous[i] = fluid.getPhase(phasenumb).getEnthalpy("J/mol");
        entropyAqueous[i] = fluid.getPhase(phasenumb).getEntropy("J/molK");
        CpAqueous[i] = fluid.getPhase(phasenumb).getCp("kJ/kgK");
        CvAqueous[i] = fluid.getPhase(phasenumb).getCv("kJ/kgK");
        densityAqueous[i] = fluid.getPhase(phasenumb).getDensity("kg/m3");

        soundspeedAqueous[i] = fluid.getPhase(phasenumb).getSoundSpeed();
        wtfractionAqueous[i] = fluid.getWtFraction(phasenumb);
        molefractionAqueous[i] = fluid.getMoleFraction(phasenumb);
        molarvolumeAqueous[i] = 1.0 / fluid.getPhase(phasenumb).getDensity("mol/m3");
        volumefractionAqueous[i] = fluid.getCorrectedVolumeFraction(phasenumb);
        gammaAqueous[i] = fluid.getPhase(phasenumb).getKappa();
        joulethomsoncoefficientAqueous[i] =
            fluid.getPhase(phasenumb).getJouleThomsonCoefficient() / 1e5;
        thermalconductivityAqueous[i] = fluid.getPhase(phasenumb).getThermalConductivity("W/mK");
      } else {
        molarmassAqueous[i] = Double.NaN;
        ZAqueous[i] = Double.NaN;
        viscosityAqueous[i] = Double.NaN;
        enthalpyAqueous[i] = Double.NaN;
        entropyAqueous[i] = Double.NaN;
        CpAqueous[i] = Double.NaN;
        CvAqueous[i] = Double.NaN;
        densityAqueous[i] = Double.NaN;
        soundspeedAqueous[i] = Double.NaN;
        wtfractionAqueous[i] = Double.NaN;
        molefractionAqueous[i] = Double.NaN;
        molarvolumeAqueous[i] = Double.NaN;
        volumefractionAqueous[i] = Double.NaN;
        gammaAqueous[i] = Double.NaN;
        joulethomsoncoefficientAqueous[i] = Double.NaN;
        thermalconductivityAqueous[i] = Double.NaN;
      }
    }
    properties.put("molarmass[kg/mol]", molarmass);
    properties.put("molarmass_gas[kg/mol]", molarmassGas);
    properties.put("molarmass_oil[kg/mol]", molarmassOil);
    properties.put("molarmass_aqueous[kg/mol]", molarmassAqueous);
    properties.put("density[kg/m3]", density);
    properties.put("Z[-]", Z);
    properties.put("numberofphases[-]", numberOfPhases);
    properties.put("pressure[Pa]", locpressure);
    properties.put("temperature[K]", loctemperature);
    properties.put("molefraction[-]", molefraction);
    properties.put("molarvolume[m3/mol]", molarVolume);
    properties.put("gamma[-]", gamma);
    properties.put("enthalpy[J/mol]", enthalpy);
    properties.put("entropy[J/molK]", entropy);
    properties.put("Cp[J/molK]", Cp);
    properties.put("Cv[J/molK]", Cv);
    properties.put("thermalconductivity[W/mK]", thermalconductivity);
    properties.put("viscosity[kg/msec]", viscosity);

    properties.put("density_gas[kg/m3]", densityGas);
    properties.put("Z_gas[-]", ZGas);
    properties.put("soundspeed_gas[m/sec]", soundspeedGas);
    properties.put("wtfraction_gas[-]", wtfractionGas);
    properties.put("molefraction_gas[-]", molefractionGas);
    properties.put("molarvolume_gas[m3/mol]", molarvolumeGas);
    properties.put("volumefraction_gas[-]", volumefractionGas);
    properties.put("gamma_gas[-]", gammaGas);
    properties.put("joulethomsoncoefficient_gas[K/Pa]", joulethomsoncoefficientGas);
    properties.put("enthalpy_gas[J/mol]", enthalpyGas);
    properties.put("entropy_gas[J/molK]", entropyGas);
    properties.put("Cp_gas[J/molK]", CpGas);
    properties.put("Cv_gas[J/molK]", CvGas);
    properties.put("thermalconductivity_gas[W/mK]", thermalconductivitygas);
    properties.put("viscosity_gas[kg/msec]", viscosityGas);

    properties.put("density_oil[kg/m3]", densityOil);
    properties.put("Z_oil[-]", ZOil);
    properties.put("soundspeed_oil[m/sec]", soundspeedOil);
    properties.put("wtfraction_oil[-]", wtfractionOil);
    properties.put("molefraction_oil[-]", molefractionOil);
    properties.put("molarvolume_oil[m3/mol]", molarvolumeOil);
    properties.put("volumefraction_oil[-]", volumefractionOil);
    properties.put("gamma_oil[-]", gammaOil);
    properties.put("joulethomsoncoefficient_oil[K/Pa]", joulethomsoncoefficientOil);
    properties.put("enthalpy_oil[J/mol]", enthalpyOil);
    properties.put("entropy_oil[J/molK]", entropyOil);
    properties.put("Cp_oil[J/molK]", CpOil);
    properties.put("Cv_oil[J/molK]", CvOil);
    properties.put("thermalconductivity_oil[W/mK]", thermalconductivityOil);
    properties.put("viscosity_oil[kg/msec]", viscosityOil);

    properties.put("density_aqueous[kg/m3]", densityAqueous);
    properties.put("Z_aqueous[-]", ZAqueous);
    properties.put("soundspeed_aqueous[m/sec]", soundspeedAqueous);
    properties.put("wtfraction_aqueous[-]", wtfractionAqueous);
    properties.put("molefraction_aqueous[-]", molefractionAqueous);
    properties.put("molarvolume_aqueous[m3/mol]", molarvolumeAqueous);
    properties.put("volumefraction_aqueous[-]", volumefractionAqueous);
    properties.put("gamma_aqueous[-]", gammaAqueous);
    properties.put("enthalpy_aqueous[J/mol]", enthalpyAqueous);
    properties.put("entropy_aqueous[J/molK]", entropyAqueous);
    properties.put("Cp_aqueous[J/molK]", CpAqueous);
    properties.put("Cv_aqueous[J/molK]", CvAqueous);
    properties.put("joulethomsoncoefficient_aqueous[K/Pa]", joulethomsoncoefficientAqueous);
    properties.put("thermalconductivity_aqueous[W/mK]", thermalconductivityAqueous);
    properties.put("viscosity_aqueous[kg/msec]", viscosityAqueous);

    return properties;
  }

  /*
   * public void test(Dataset<Row> teenagersDF){ SparkSession spark = SparkSession .builder()
   * .appName("Java Spark SQL basic example") .config("spark.some.config.option", "some-value")
   * .getOrCreate(); // Dataset<Row> df =
   * spark.read().json("examples/src/main/resources/people.json"); Dataset<Row> df = teenagersDF; //
   * Displays the content of the DataFrame to stdout df.show(); }
   */

  /**
   * <p>
   * getValue.
   * </p>
   *
   * @param propertyName a {@link java.lang.String} object
   * @return a double
   */
  public double getValue(String propertyName) {
    return 0.0;
  }
}
