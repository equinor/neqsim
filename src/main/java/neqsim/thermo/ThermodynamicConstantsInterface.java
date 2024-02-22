package neqsim.thermo;

/**
 * <p>
 * ThermodynamicConstantsInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ThermodynamicConstantsInterface extends java.io.Serializable {
  /** Constant <code>R=8.3144621</code>. */
  double R = 8.3144621;
  /** Constant <code>pi=3.14159265</code>. */
  double pi = 3.14159265;
  /** Constant <code>gravity=9.80665</code> [kg/ms^2]. */
  double gravity = 9.80665;
  /** Constant <code>avagadroNumber=6.023e23</code>. */
  double avagadroNumber = 6.023e23;
  /** Constant <code>referenceTemperature=273.15</code> [K]. */
  double referenceTemperature = 273.15;
  /** Constant <code>referencePressure=1.01325</code> [bar]. */
  double referencePressure = 1.01325;
  /** Constant <code>atm=101325</code> [Pa]. */
  double atm = 101325;
  /** Constant <code>boltzmannConstant=1.38066e-23</code>. */
  double boltzmannConstant = 1.38066e-23;
  /** Constant <code>electronCharge=1.6021917e-19</code>. */
  double electronCharge = 1.6021917e-19;
  /** Constant <code>planckConstant=6.626196e-34</code>. */
  double planckConstant = 6.626196e-34;
  /** Constant <code>vacumPermittivity=8.85419e-12</code>. */
  double vacumPermittivity = 8.85419e-12;
  /** Constant <code>faradayConstant=96486.70</code>. */
  double faradayConstant = 96486.70;
  /** Constant <code>standardStateTemperature=288.15</code> [K]. */
  double standardStateTemperature = 288.15;
  /** Constant <code>normalStateTemperature=273.15</code> [K]. */
  double normalStateTemperature = 273.15;
  /** Constant <code>molarMassAir=28.96546</code> [g/mol]. */
  double molarMassAir = 28.96546;
}
