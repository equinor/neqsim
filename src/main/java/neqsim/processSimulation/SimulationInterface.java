package neqsim.processSimulation;

import neqsim.util.NamedInterface;

public interface SimulationInterface extends NamedInterface, Runnable {
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
