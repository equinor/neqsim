package neqsim.process.equipment.expander;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class TurboExpanderCompressor extends Expander {

  private static final long serialVersionUID = 1001;
  private double expanderOutPressure = 40.0; // bar
  private double IGVposition = 1.0; // 1.0 = 100% open
  private double bearingLossPower = 1000.0; // W
  private double compressorRequiredPower = 0.0; // W
  private double turboSpeed = 10000.0; // rpm, initial guess
  private double expanderEfficiencyDesign = 0.85;

  public TurboExpanderCompressor(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  public void setIGVposition(double IGVposition) {
    this.IGVposition = IGVposition;
  }

  public void setCompressorRequiredPower(double power) {
    this.compressorRequiredPower = power;
  }

  public void setExpanderOutPressure(double pressure) {
    this.expanderOutPressure = pressure;
  }

  @Override
  public void run(UUID id) {
    SystemInterface thermoSystem = inStream.getThermoSystem().clone();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);

    double P_in = thermoSystem.getPressure();
    double H_in = thermoSystem.getEnthalpy();
    double S_in = thermoSystem.getEntropy();

    // Step 1: Apply IGV pressure drop
    double P_afterIGV = applyIGVpressureDrop(P_in);
    thermoSystem.setPressure(P_afterIGV);
    thermoOps.TPflash();
    thermoSystem.initThermoProperties();
    H_in = thermoSystem.getEnthalpy(); // update enthalpy after IGV

    // Step 2: Iterate on speed to match power
    double speed = turboSpeed;
    double dSpeed = 500.0;
    int iter = 0;
    int maxIter = 20;

    while (iter < maxIter) {
      thermoSystem.setPressure(expanderOutPressure);
      thermoOps.PSflash(S_in); // isentropic expansion
      double H_isentropicOut = thermoSystem.getEnthalpy();

      // Correct efficiency based on speed
      double efficiency = correctEfficiency(speed);

      double W_isentropic = H_isentropicOut - H_in;
      double W_expander = W_isentropic * efficiency;
      double W_expander_total = W_expander; // W

      double W_corrected = W_expander_total - bearingLossPower;

      double error = W_corrected - compressorRequiredPower;
      if (Math.abs(error) < 50.0)
        break; // convergence criteria 50 W

      speed -= error / 500.0; // crude proportional adjustment
      iter++;
    }

    // Final PH-flash to determine outlet state
    double H_out = H_in + (compressorRequiredPower + bearingLossPower)
        / (thermoSystem.getFlowRate("kg/sec") * 1000.0);
    thermoSystem.setPressure(expanderOutPressure);
    thermoOps.PHflash(H_out);

    outStream.setThermoSystem(thermoSystem);
    setCalculationIdentifier(id);
  }

  private double applyIGVpressureDrop(double Pin) {
    // Simple Cv-like pressure drop: linear loss based on opening
    double k = 0.05; // tuning parameter
    return Pin * (1.0 - k * (1.0 - IGVposition));
  }

  private double correctEfficiency(double speed) {
    // Placeholder correction: simple linear relation
    double baseSpeed = 10000.0;
    double corr = 1.0 - 0.00001 * Math.abs(speed - baseSpeed);
    return expanderEfficiencyDesign * corr;
  }

  public double getTurboSpeed() {
    return turboSpeed;
  }
}
