package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;

/**
 * Electrical screening calculations for umbilical power, signal and fibre-optic elements.
 *
 * <p>
 * Three-phase power cables are solved as a distributed-parameter line (exact hyperbolic two-port), which includes the
 * cable charging current that dominates long step-out distances, with skin effect on the conductor resistance (IEC
 * 60287-1-1) and a coaxial capacitance model of the screened core. Conductor resistance is copper with a linear
 * temperature coefficient. Proximity effect, armour/screen losses and dielectric loss are not included; results are
 * screening values.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class UmbilicalElectricalCalculator {

  /** Copper resistivity at 20 C in ohm m. */
  private static final double RHO_CU_20 = 1.7241e-8;
  /** Copper temperature coefficient of resistance per K. */
  private static final double ALPHA_CU = 0.00393;
  /** Permeability of free space in H/m. */
  private static final double MU0 = 4.0e-7 * Math.PI;
  /** Permittivity of free space in F/m. */
  private static final double EPS0 = 8.8541878128e-12;
  /** Compaction factor of a stranded conductor (copper area to circle area). */
  private static final double STRANDING_FILL = 0.9;

  /**
   * Result of a three-phase cable calculation.
   */
  public static class PowerResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** AC resistance in ohm per km. */
    private final double acResistanceOhmPerKm;
    /** Series reactance in ohm per km. */
    private final double reactanceOhmPerKm;
    /** Capacitance in uF per km. */
    private final double capacitanceUfPerKm;
    /** Charging current at nominal voltage in A. */
    private final double chargingCurrentA;
    /** Load current in A. */
    private final double loadCurrentA;
    /** Sending end line-to-line voltage in V. */
    private final double sendingVoltageV;
    /** Sending end current in A. */
    private final double sendingCurrentA;
    /** Sending end active power in MW. */
    private final double sendingPowerMW;
    /** Active power loss in MW. */
    private final double lossMW;
    /** Voltage drop in percent of receiving voltage. */
    private final double voltageDropPercent;
    /** No-load voltage rise in percent (Ferranti effect). */
    private final double noLoadRisePercent;

    /**
     * Constructor.
     *
     * @param acResistanceOhmPerKm AC resistance in ohm/km
     * @param reactanceOhmPerKm reactance in ohm/km
     * @param capacitanceUfPerKm capacitance in uF/km
     * @param chargingCurrentA charging current in A
     * @param loadCurrentA load current in A
     * @param sendingVoltageV sending line-to-line voltage in V
     * @param sendingCurrentA sending current in A
     * @param sendingPowerMW sending active power in MW
     * @param lossMW power loss in MW
     * @param voltageDropPercent voltage drop in percent
     * @param noLoadRisePercent no-load voltage rise in percent
     */
    public PowerResult(double acResistanceOhmPerKm, double reactanceOhmPerKm, double capacitanceUfPerKm,
        double chargingCurrentA, double loadCurrentA, double sendingVoltageV, double sendingCurrentA,
        double sendingPowerMW, double lossMW, double voltageDropPercent, double noLoadRisePercent) {
      this.acResistanceOhmPerKm = acResistanceOhmPerKm;
      this.reactanceOhmPerKm = reactanceOhmPerKm;
      this.capacitanceUfPerKm = capacitanceUfPerKm;
      this.chargingCurrentA = chargingCurrentA;
      this.loadCurrentA = loadCurrentA;
      this.sendingVoltageV = sendingVoltageV;
      this.sendingCurrentA = sendingCurrentA;
      this.sendingPowerMW = sendingPowerMW;
      this.lossMW = lossMW;
      this.voltageDropPercent = voltageDropPercent;
      this.noLoadRisePercent = noLoadRisePercent;
    }

    /**
     * Get AC resistance.
     *
     * @return resistance in ohm/km
     */
    public double getAcResistanceOhmPerKm() {
      return acResistanceOhmPerKm;
    }

    /**
     * Get series reactance.
     *
     * @return reactance in ohm/km
     */
    public double getReactanceOhmPerKm() {
      return reactanceOhmPerKm;
    }

    /**
     * Get capacitance.
     *
     * @return capacitance in uF/km
     */
    public double getCapacitanceUfPerKm() {
      return capacitanceUfPerKm;
    }

    /**
     * Get the total cable charging current at nominal voltage.
     *
     * @return charging current in A
     */
    public double getChargingCurrentA() {
      return chargingCurrentA;
    }

    /**
     * Get the load current.
     *
     * @return load current in A
     */
    public double getLoadCurrentA() {
      return loadCurrentA;
    }

    /**
     * Get the required sending end line-to-line voltage.
     *
     * @return voltage in V
     */
    public double getSendingVoltageV() {
      return sendingVoltageV;
    }

    /**
     * Get the sending end current.
     *
     * @return current in A
     */
    public double getSendingCurrentA() {
      return sendingCurrentA;
    }

    /**
     * Get the sending end active power.
     *
     * @return power in MW
     */
    public double getSendingPowerMW() {
      return sendingPowerMW;
    }

    /**
     * Get the active power loss.
     *
     * @return loss in MW
     */
    public double getLossMW() {
      return lossMW;
    }

    /**
     * Get the voltage drop, (sending - receiving) / receiving; negative when the charging current raises the receiving
     * voltage.
     *
     * @return voltage drop in percent
     */
    public double getVoltageDropPercent() {
      return voltageDropPercent;
    }

    /**
     * Get the open-circuit voltage rise at the receiving end for a fixed sending voltage.
     *
     * @return rise in percent
     */
    public double getNoLoadRisePercent() {
      return noLoadRisePercent;
    }
  }

  /**
   * Minimal immutable complex number.
   */
  private static final class Cx {
    /** Real part. */
    private final double re;
    /** Imaginary part. */
    private final double im;

    /**
     * Constructor.
     *
     * @param re real part
     * @param im imaginary part
     */
    private Cx(double re, double im) {
      this.re = re;
      this.im = im;
    }

    /**
     * Sum.
     *
     * @param o other
     * @return this + o
     */
    private Cx plus(Cx o) {
      return new Cx(re + o.re, im + o.im);
    }

    /**
     * Product.
     *
     * @param o other
     * @return this * o
     */
    private Cx times(Cx o) {
      return new Cx(re * o.re - im * o.im, re * o.im + im * o.re);
    }

    /**
     * Quotient.
     *
     * @param o other
     * @return this / o
     */
    private Cx div(Cx o) {
      double den = o.re * o.re + o.im * o.im;
      return new Cx((re * o.re + im * o.im) / den, (im * o.re - re * o.im) / den);
    }

    /**
     * Principal square root.
     *
     * @return square root
     */
    private Cx sqrt() {
      double mod = Math.hypot(re, im);
      double sr = Math.sqrt((mod + re) / 2.0);
      double si = Math.sqrt(Math.max(0.0, (mod - re) / 2.0));
      return new Cx(sr, im < 0.0 ? -si : si);
    }

    /**
     * Hyperbolic cosine.
     *
     * @return cosh
     */
    private Cx cosh() {
      return new Cx(Math.cosh(re) * Math.cos(im), Math.sinh(re) * Math.sin(im));
    }

    /**
     * Hyperbolic sine.
     *
     * @return sinh
     */
    private Cx sinh() {
      return new Cx(Math.sinh(re) * Math.cos(im), Math.cosh(re) * Math.sin(im));
    }

    /**
     * Complex conjugate.
     *
     * @return conjugate
     */
    private Cx conj() {
      return new Cx(re, -im);
    }

    /**
     * Modulus.
     *
     * @return magnitude
     */
    private double abs() {
      return Math.hypot(re, im);
    }
  }

  /**
   * Private constructor for utility class.
   */
  private UmbilicalElectricalCalculator() {
  }

  /**
   * DC resistance of a copper conductor.
   *
   * @param areaMm2 conductor cross-section in mm2
   * @param temperatureC conductor temperature in C
   * @return resistance in ohm per m
   */
  public static double conductorResistancePerMeter(double areaMm2, double temperatureC) {
    return RHO_CU_20 * (1.0 + ALPHA_CU * (temperatureC - 20.0)) / (areaMm2 * 1.0e-6);
  }

  /**
   * Skin effect increase of the AC resistance, Ys of IEC 60287-1-1 for a round stranded conductor.
   *
   * @param dcResistanceOhmPerM DC resistance in ohm per m
   * @param frequencyHz frequency in Hz
   * @return Ys, so that R_ac = R_dc (1 + Ys)
   */
  public static double skinEffectFactor(double dcResistanceOhmPerM, double frequencyHz) {
    double xs2 = 8.0 * Math.PI * frequencyHz / dcResistanceOhmPerM * 1.0e-7;
    double xs4 = xs2 * xs2;
    return xs4 / (192.0 + 0.8 * xs4);
  }

  /**
   * Overall diameter of a stranded circular conductor.
   *
   * @param areaMm2 copper area in mm2
   * @return conductor diameter in mm
   */
  public static double conductorDiameterMm(double areaMm2) {
    return Math.sqrt(4.0 * areaMm2 / (Math.PI * STRANDING_FILL));
  }

  /**
   * Screening default for the XLPE/EPR insulation thickness from the rated line-to-line voltage.
   *
   * @param ratedVoltageV rated line-to-line voltage in V
   * @return insulation thickness in mm
   */
  public static double defaultInsulationThicknessMm(double ratedVoltageV) {
    if (ratedVoltageV <= 1000.0) {
      return 1.2;
    } else if (ratedVoltageV <= 12000.0) {
      return 3.4;
    } else if (ratedVoltageV <= 24000.0) {
      return 5.5;
    } else if (ratedVoltageV <= 36000.0) {
      return 8.0;
    }
    return 12.0;
  }

  /**
   * Screening default for the conductor area from the rated voltage.
   *
   * @param ratedVoltageV rated voltage in V
   * @return conductor area in mm2
   */
  public static double defaultConductorAreaMm2(double ratedVoltageV) {
    if (ratedVoltageV <= 100.0) {
      return 2.5;
    } else if (ratedVoltageV <= 1000.0) {
      return 10.0;
    } else if (ratedVoltageV <= 12000.0) {
      return 35.0;
    }
    return 95.0;
  }

  /**
   * Capacitance of a screened insulated core.
   *
   * @param conductorDiameterMm conductor diameter in mm
   * @param insulationThicknessMm insulation thickness in mm
   * @param relativePermittivity relative permittivity of the insulation (about 2.5 for XLPE)
   * @return capacitance in F per m
   */
  public static double capacitancePerMeter(double conductorDiameterMm, double insulationThicknessMm,
      double relativePermittivity) {
    double ratio = (conductorDiameterMm + 2.0 * insulationThicknessMm) / conductorDiameterMm;
    return 2.0 * Math.PI * EPS0 * relativePermittivity / Math.log(ratio);
  }

  /**
   * Series reactance of a three-core cable with touching cores (trefoil spacing equal to the core diameter), using the
   * conductor geometric mean radius 0.7788 r.
   *
   * @param conductorDiameterMm conductor diameter in mm
   * @param insulationThicknessMm insulation thickness in mm
   * @param frequencyHz frequency in Hz
   * @return reactance in ohm per m
   */
  public static double reactancePerMeter(double conductorDiameterMm, double insulationThicknessMm, double frequencyHz) {
    double spacing = conductorDiameterMm + 2.0 * insulationThicknessMm;
    double gmr = 0.7788 * conductorDiameterMm / 2.0;
    return 2.0 * Math.PI * frequencyHz * MU0 / (2.0 * Math.PI) * Math.log(spacing / gmr);
  }

  /**
   * Solve a three-phase power cable delivering a load at the receiving end.
   *
   * @param conductorAreaMm2 conductor area per core in mm2
   * @param insulationThicknessMm insulation thickness in mm
   * @param relativePermittivity insulation relative permittivity
   * @param ratedVoltageV receiving end line-to-line voltage in V
   * @param lengthM cable length in m
   * @param frequencyHz frequency in Hz
   * @param conductorTemperatureC conductor temperature in C
   * @param loadMW receiving end active power in MW
   * @param powerFactor lagging load power factor (0 to 1]
   * @return power transmission result
   */
  public static PowerResult threePhase(double conductorAreaMm2, double insulationThicknessMm,
      double relativePermittivity, double ratedVoltageV, double lengthM, double frequencyHz,
      double conductorTemperatureC, double loadMW, double powerFactor) {
    double dCond = conductorDiameterMm(conductorAreaMm2);
    double rdc = conductorResistancePerMeter(conductorAreaMm2, conductorTemperatureC);
    double rac = rdc * (1.0 + skinEffectFactor(rdc, frequencyHz));
    double x = reactancePerMeter(dCond, insulationThicknessMm, frequencyHz);
    double cap = capacitancePerMeter(dCond, insulationThicknessMm, relativePermittivity);
    double omega = 2.0 * Math.PI * frequencyHz;

    Cx z = new Cx(rac, x);
    Cx y = new Cx(0.0, omega * cap);
    Cx gamma = z.times(y).sqrt().times(new Cx(lengthM, 0.0));
    Cx zc = z.div(y).sqrt();

    double vPhase = ratedVoltageV / Math.sqrt(3.0);
    double iLoad = loadMW * 1.0e6 / (Math.sqrt(3.0) * ratedVoltageV * powerFactor);
    double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - powerFactor * powerFactor));
    Cx vr = new Cx(vPhase, 0.0);
    Cx ir = new Cx(iLoad * powerFactor, -iLoad * sinPhi);

    Cx ch = gamma.cosh();
    Cx sh = gamma.sinh();
    Cx vs = vr.times(ch).plus(zc.times(ir).times(sh));
    Cx is = ir.times(ch).plus(vr.div(zc).times(sh));

    double psW = 3.0 * vs.times(is.conj()).re;
    double lossMW = psW / 1.0e6 - loadMW;
    double chargingA = omega * cap * lengthM * vPhase;

    return new PowerResult(rac * 1000.0, x * 1000.0, cap * 1.0e9, chargingA, iLoad, vs.abs() * Math.sqrt(3.0), is.abs(),
        psW / 1.0e6, lossMW, (vs.abs() - vPhase) / vPhase * 100.0, (1.0 / ch.abs() - 1.0) * 100.0);
  }

  /**
   * Voltage drop of a two-wire DC or low-frequency control circuit (out and return conductor).
   *
   * @param conductorAreaMm2 conductor area in mm2
   * @param lengthM one-way length in m
   * @param currentA current in A
   * @param conductorTemperatureC conductor temperature in C
   * @return voltage drop in V
   */
  public static double dcLoopVoltageDrop(double conductorAreaMm2, double lengthM, double currentA,
      double conductorTemperatureC) {
    return 2.0 * currentA * lengthM * conductorResistancePerMeter(conductorAreaMm2, conductorTemperatureC);
  }

  /**
   * Optical link loss budget of a fibre in an umbilical.
   *
   * @param lengthKm length in km
   * @param attenuationDbPerKm fibre attenuation in dB/km (about 0.35 at 1310 nm, 0.22 at 1550 nm for single mode)
   * @param spliceCount number of splices
   * @param spliceLossDb loss per splice in dB
   * @param connectorLossDb total connector loss in dB
   * @return total link loss in dB
   */
  public static double fiberLinkLossDb(double lengthKm, double attenuationDbPerKm, int spliceCount, double spliceLossDb,
      double connectorLossDb) {
    return lengthKm * attenuationDbPerKm + spliceCount * spliceLossDb + connectorLossDb;
  }
}
