package neqsim.process.equipment.blackoil;

/**
 * Simple equilibrium Black-Oil separator at given (P_out, T_out).
 *
 * @author esol
 */
public class BlackOilSeparator {
  private final String name;
  private neqsim.blackoil.SystemBlackOil inlet;
  private final double P_out;
  private final double T_out;
  private neqsim.blackoil.SystemBlackOil oilOut;
  private neqsim.blackoil.SystemBlackOil gasOut;
  private neqsim.blackoil.SystemBlackOil waterOut;

  /**
   * <p>
   * Constructor for BlackOilSeparator.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inlet a {@link neqsim.blackoil.SystemBlackOil} object
   * @param P_out a double
   * @param T_out a double
   */
  public BlackOilSeparator(String name, neqsim.blackoil.SystemBlackOil inlet, double P_out,
      double T_out) {
    this.name = name;
    this.inlet = inlet;
    this.P_out = P_out;
    this.T_out = T_out;
  }

  /**
   * <p>
   * Getter for the field <code>name</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName() {
    return name;
  }

  /**
   * <p>
   * Setter for the field <code>inlet</code>.
   * </p>
   *
   * @param inlet a {@link neqsim.blackoil.SystemBlackOil} object
   */
  public void setInlet(neqsim.blackoil.SystemBlackOil inlet) {
    this.inlet = inlet;
  }

  /**
   * <p>
   * Getter for the field <code>inlet</code>.
   * </p>
   *
   * @return a {@link neqsim.blackoil.SystemBlackOil} object
   */
  public neqsim.blackoil.SystemBlackOil getInlet() {
    return inlet;
  }

  /**
   * <p>
   * Getter for the field <code>oilOut</code>.
   * </p>
   *
   * @return a {@link neqsim.blackoil.SystemBlackOil} object
   */
  public neqsim.blackoil.SystemBlackOil getOilOut() {
    return oilOut;
  }

  /**
   * <p>
   * Getter for the field <code>gasOut</code>.
   * </p>
   *
   * @return a {@link neqsim.blackoil.SystemBlackOil} object
   */
  public neqsim.blackoil.SystemBlackOil getGasOut() {
    return gasOut;
  }

  /**
   * <p>
   * Getter for the field <code>waterOut</code>.
   * </p>
   *
   * @return a {@link neqsim.blackoil.SystemBlackOil} object
   */
  public neqsim.blackoil.SystemBlackOil getWaterOut() {
    return waterOut;
  }

  /**
   * <p>
   * run.
   * </p>
   */
  public void run() {
    inlet.setPressure(P_out);
    inlet.setTemperature(T_out);
    neqsim.blackoil.BlackOilFlashResult r = inlet.flash();

    oilOut = inlet.copyShallow();
    gasOut = inlet.copyShallow();
    waterOut = inlet.copyShallow();

    double O_oil_std = r.O_std;
    double G_oil_std = r.Rs * r.O_std;
    oilOut.setStdTotals(O_oil_std, G_oil_std, 0.0);
    oilOut.setPressure(P_out);
    oilOut.setTemperature(T_out);

    double G_gas_std = r.Gf_std;
    double O_gas_std = r.Rv * r.Gf_std;
    gasOut.setStdTotals(O_gas_std, G_gas_std, 0.0);
    gasOut.setPressure(P_out);
    gasOut.setTemperature(T_out);

    waterOut.setStdTotals(0.0, 0.0, r.W_std);
    waterOut.setPressure(P_out);
    waterOut.setTemperature(T_out);
  }
}
