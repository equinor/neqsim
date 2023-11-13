package neqsim.thermo.util.empiric;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * BukacekWaterInGas class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class BukacekWaterInGas {
  /*
   * Calculates the ppm(mol) water content of a gas at its water dew point
   */
  /**
   * <p>
   * getWaterInGas.
   * </p>
   *
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public static double getWaterInGas(double temperature, double pressure) {
    double TCwater = 393.99 + 273.15, PCwater = 220.64;
    double tau = (TCwater - temperature) / TCwater;

    double temp = (-7.85823 * tau + 1.83991 * Math.pow(tau, 1.5) - 11.7811 * Math.pow(tau, 3.0)
        + 22.67 * Math.pow(tau, 3.5) - 15.9393 * Math.pow(tau, 4.0) + 1.77516 * Math.pow(tau, 7.5))
        / (1.0 - tau);
    double psw = PCwater * Math.exp(temp);

    double mgwaterSm3 =
        761900.42 * psw / pressure + 16.016 * Math.pow(10.0, -1716.26 / (temperature) + 6.69449);

    double molarMassGas = 0.6 * 28.0 * 1000.0; // mgr/mol

    double ans = mgwaterSm3 / molarMassGas; // mol water /Sm3 gas

    double molgasSm3 =
        ThermodynamicConstantsInterface.atm / (ThermodynamicConstantsInterface.R * 288.15); // mol
                                                                                            // gas/
                                                                                            // Sm3

    return ans / molgasSm3;
  }

  /**
   * <p>
   * waterDewPointTemperature.
   * </p>
   *
   * @param moleFractionWaterInGas a double
   * @param pressure a double
   * @return a double
   */
  public static double waterDewPointTemperature(double moleFractionWaterInGas, double pressure) {
    int iter = 0;
    double newppm, newTemp = 273.15;
    do {
      iter++;

      newppm = getWaterInGas(newTemp, pressure);
      newTemp -= (newppm - moleFractionWaterInGas) * 1e5;
    } while (Math.abs((newppm - moleFractionWaterInGas) / moleFractionWaterInGas) > 1e-8
        && iter < 1000);
    return newTemp;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    System.out.println("water in gas " + BukacekWaterInGas.getWaterInGas(273.15 - 18.0, 70.0));

    System.out.println("water dew point temperature "
        + (BukacekWaterInGas.waterDewPointTemperature(20.0e-6, 70.0) - 273.15));
  }
}
