package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * SuperTrappViscosity class implements the viscosity model based on NIST IR 6650.
 * 
 * Reference: NIST IR 6650 - SUPERTRAPP
 * 
 * @version 1.0
 */
public class SuperTrappViscosity extends Viscosity {
  private static final long serialVersionUID = 1001L;

  // Reference system (SRK EOS for example)
  SystemInterface referenceSystem =
      new SystemSrkEos(273.15, ThermodynamicConstantsInterface.referencePressure);

  // Add parameters specific to the SuperTrapp model
  double[] SuperTrappCoefficients = new double[] {0.1, 0.2};// ...}; // Populate with coefficients
                                                            // from NIST IR 6650
  // Add other necessary parameters here

  // Constructors
  public SuperTrappViscosity() {}

  public SuperTrappViscosity(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
    super(phase);
    if (referenceSystem.getNumberOfMoles() < 1e-10) {
      referenceSystem.addComponent("methane", 10.0);
      referenceSystem.init(0);
    }
  }

  @Override
  public double calcViscosity() {
    // Step 1: Calculate mixture properties using SuperTrapp equations
    double TcMix = calculateCriticalTemperature(); // Example method
    double PcMix = calculateCriticalPressure(); // Example method
    double Mmix = calculateMolarMass(); // Example method

    // Step 2: Reference system adjustment
    referenceSystem.setTemperature(phase.getPhase().getTemperature() * TcMix);
    referenceSystem.setPressure(phase.getPhase().getPressure() * PcMix);
    referenceSystem.init(1);

    // Step 3: Calculate viscosity based on the SuperTrapp model
    double viscosity = calculateViscosityUsingSuperTrapp(TcMix, PcMix, Mmix);
    return viscosity;
  }

  private double calculateCriticalTemperature() {
    // Implement critical temperature calculation
    return 0.0;
  }

  private double calculateCriticalPressure() {
    // Implement critical pressure calculation
    return 0.0;
  }

  private double calculateMolarMass() {
    // Implement molar mass calculation
    return 0.0;
  }

  private double calculateViscosityUsingSuperTrapp(double TcMix, double PcMix, double Mmix) {
    // Implement the core SuperTrapp viscosity calculation
    // Utilize coefficients and equations from NIST IR 6650
    return 0.0;
  }
}

