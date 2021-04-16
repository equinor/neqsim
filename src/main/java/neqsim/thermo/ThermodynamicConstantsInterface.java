/*
 * ThermodynamicConstantsInterface.java
 *
 * Created on 3. juni 2000, 19:07
 */

package neqsim.thermo;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface ThermodynamicConstantsInterface extends java.io.Serializable {
    double R = 8.3144621;
    double pi = 3.14159265;
    double gravity = 9.80665;
    double avagadroNumber = 6.023e23;
    static final int MAX_NUMBER_OF_COMPONENTS = 100;
    double referenceTemperature = 273.15;
    double referencePressure = 1.01325;
    double boltzmannConstant = 1.38066e-23;
    double electronCharge = 1.6021917e-19;
    double planckConstant = 6.626196e-34;
    double vacumPermittivity = 8.85419e-12;
    double faradayConstant = 96486.70;
    double standardStateTemperature = 288.15;
    double normalStateTemperature = 273.15;
    double molarMassAir = 28.96546;
}