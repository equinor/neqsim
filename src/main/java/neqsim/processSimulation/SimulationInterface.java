package neqsim.processSimulation;

public interface SimulationInterface extends Runnable {

    /**
     * <p>
     * getName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName();

    /**
     * <p>
     * setName.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name);

    /**
     * <p>
     * run
     * </p>
     * In this method all thermodynamic and unit the operation will be calculated in
     * a steady state
     * calculation.
     *
     * @return void
     */
    @Override
    public void run();

    /**
     * <p>
     * runTransient
     * </p>
     * In this method all thermodynamic and unit the operation will be calculated in
     * a dynamic
     * calculation. dt is the delta time step (seconds)
     *
     * @return void
     */
    public void runTransient(double dt);

    /**
     * <p>
     * solved.
     * </p>
     *
     * @return a boolean
     */
    public boolean solved();
}
