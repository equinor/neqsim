/*
 * ThermodynamicConstantsInterface.java
 *
 * Created on 3. juni 2000, 19:07
 */

package neqsim.thermo;

/**
 * <p>ThermodynamicConstantsInterface interface.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ThermodynamicConstantsInterface extends java.io.Serializable {
    /** Constant <code>R=8.3144621</code> */
    double R = 8.3144621;
    /** Constant <code>pi=3.14159265</code> */
    double pi = 3.14159265;
    /** Constant <code>gravity=9.80665</code> */
    double gravity = 9.80665;
    /** Constant <code>avagadroNumber=6.023e23</code> */
    double avagadroNumber = 6.023e23;
    /** Constant <code>MAX_NUMBER_OF_COMPONENTS=100</code> */
    static final int MAX_NUMBER_OF_COMPONENTS = 100;
    /** Constant <code>referenceTemperature=273.15</code> */
    double referenceTemperature = 273.15;
    /** Constant <code>referencePressure=1.01325</code> */
    double referencePressure = 1.01325;
    /** Constant <code>boltzmannConstant=1.38066e-23</code> */
    double boltzmannConstant = 1.38066e-23;
    /** Constant <code>electronCharge=1.6021917e-19</code> */
    double electronCharge = 1.6021917e-19;
    /** Constant <code>planckConstant=6.626196e-34</code> */
    double planckConstant = 6.626196e-34;
    /** Constant <code>vacumPermittivity=8.85419e-12</code> */
    double vacumPermittivity = 8.85419e-12;
    /** Constant <code>faradayConstant=96486.70</code> */
    double faradayConstant = 96486.70;
    /** Constant <code>standardStateTemperature=288.15</code> */
    double standardStateTemperature = 288.15;
    /** Constant <code>normalStateTemperature=273.15</code> */
    double normalStateTemperature = 273.15;
    /** Constant <code>molarMassAir=28.96546</code> */
    double molarMassAir = 28.96546;
}
