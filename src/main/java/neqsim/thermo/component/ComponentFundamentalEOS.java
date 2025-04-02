package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseFundamentalEOS;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Component class for use with fundamental Helmholtz equations of state.
 */
public abstract class ComponentFundamentalEOS extends Component implements ComponentFundamentalEOSInterface {
  private static final long serialVersionUID = 1L;

  /**
   * <p>
   * Constructor for ComponentFundamentalEos.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentFundamentalEOS(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }


  /** {@inheritDoc} */
  @Override
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int initType) {
    super.init(temperature, pressure, totalNumberOfMoles, beta, initType);
    //For gerg get pure component contributions
   
  }

  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta,
    int numberOfComponents, int initType) {
      //Multicomponent EOS
      //double dPdN = phase.getDensity() * R * temp * (1.0 + phase.getalpharesMatrix()[0][1].val * (2 - 1 / rho_r * numberOfMolesInPhase * drhordn) + ) 
      //voli = - dPdN / phase.getdPdVTn();
      //Single component EOS
      voli = phase.getVolume() / getNumberOfMolesInPhase();
  }


  /**
   * Get reduced temperature.
   *
   * @param temperature temperature of fluid
   * @return double reduced temperature T/TC
   */
  double reducedTemperature(double temperature) {
    return temperature / criticalTemperature;
  }

  /**
   * <p>
   * Get reduced pressure.
   * </p>
   *
   * @param pressure pressure in unit bara
   * @return double
   */
  double reducedPressure(double pressure) {
    return pressure / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseFundamentalEOS fundPhase = (PhaseFundamentalEOS) phase;
    return fundPhase.getAlpharesMatrix()[0][0].val + ndAlphaResdN(phase, numberOfComponents, temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double ndAlphaResdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
        //MulticomponentEOS
        //double rho_r = reducedDensity(phase.getDensity());
        //return 
        //single component EOS
    PhaseFundamentalEOS fundPhase = (PhaseFundamentalEOS) phase;
    return fundPhase.getAlpharesMatrix()[0][1].val;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseFundamentalEOS fundPhase = (PhaseFundamentalEOS) phase;
    return -(fundPhase.getAlpharesMatrix()[1][0].val + fundPhase.getAlpharesMatrix()[1][1].val)
        / phase.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseFundamentalEOS fundPhase = (PhaseFundamentalEOS) phase;

    return -(2 * fundPhase.getAlpharesMatrix()[0][1].val + fundPhase.getAlpharesMatrix()[0][2].val)
        / phase.getVolume();
  }


  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseFundamentalEOS fundPhase = (PhaseFundamentalEOS) phase;
    return (2 * fundPhase.getAlpharesMatrix()[0][1].val + fundPhase.getAlpharesMatrix()[0][2].val)
        / phase.getNumberOfMolesInPhase(); //single component EOS
  }






  @Override
  public double fugcoef(PhaseInterface phase) {
    if (!(phase instanceof PhaseFundamentalEOS)) {
      throw new IllegalArgumentException("Phase must be of type PhaseFundamentalEOS");
    }
    PhaseFundamentalEOS fundPhase = (PhaseFundamentalEOS) phase;
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    double Z = phase.getZ();
    double logFugacityCoefficient = dFdN(fundPhase, phase.getNumberOfComponents(), temperature, pressure)
      - Math.log(Z);

    fugacityCoefficient = Math.exp(logFugacityCoefficient);

    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdP(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    dfugdp = getVoli() / R / temperature - 1.0 / pressure;
    return dfugdp;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdT(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    int numberOfComponents = phase.getNumberOfComponents();
    dfugdt = (this.dFdNdT(phase, numberOfComponents, temperature, pressure) + 1.0 / temperature
        - getVoli() / R / temperature
            * (-R * temperature * phase.dFdTdV() + pressure / temperature));
    return dfugdt;
  }

  /** {@inheritDoc} */
  @Override
  public double[] logfugcoefdN(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    int numberOfComponents = phase.getNumberOfComponents();
    ComponentInterface[] compArray = phase.getComponents();
    for (int i = 0; i < numberOfComponents; i++) {
        ComponentFundamentalEOSInterface comp = (ComponentFundamentalEOSInterface) compArray[i];
        dfugdn[i] = (this.dFdNdN(i, phase, numberOfComponents, temperature, pressure)
            + 1.0 / phase.getNumberOfMolesInPhase()
            - getVoli() / R / temperature
                * (-R * temperature
                    * comp.dFdNdV(phase, numberOfComponents, temperature, pressure)
                    + R * temperature / phase.getTotalVolume()));
        dfugdx[i] = dfugdn[i] * phase.getNumberOfMolesInPhase();
    }
    // System.out.println("diffN: " + 1 + dfugdn[0]);
    return dfugdn;
  }

  // Method added by Neeraj
  /*
   * public double getdfugdn(int i){ double[] dfugdnv = this.logfugcoefdN(phase); //return 0.0001;
   * return dfugdnv[i]; }
   */
  // Added By Neeraj
  /** {@inheritDoc} */
  @Override
  public double logfugcoefdNi(PhaseInterface phase, int k) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    int numberOfComponents = phase.getNumberOfComponents();
    double vol;
    double voli;
    ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
    vol = phase.getMolarVolume();
    voli = getVoli();

    dfugdn[k] = (this.dFdNdN(k, phase, numberOfComponents, temperature, pressure)
        + 1.0 / phase.getNumberOfMolesInPhase()
        - voli / R / temperature
            * (-R * temperature
                * comp_Array[k].dFdNdV(phase, numberOfComponents, temperature, pressure)
                + R * temperature / (vol * phase.getNumberOfMolesInPhase())));
    dfugdx[k] = dfugdn[k] * (phase.getNumberOfMolesInPhase());
    // System.out.println("Main dfugdn "+dfugdn[k]);
    return dfugdn[k];
  }


  @Override
  public ComponentFundamentalEOS clone() {
    return (ComponentFundamentalEOS) super.clone();
  }
}
