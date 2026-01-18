/**
 * Driver models for rotating equipment (compressors, pumps, etc.).
 *
 * <p>
 * This package provides driver curve models for simulating the performance of compressor and pump
 * drivers, including gas turbines, electric motors, and steam turbines.
 * </p>
 *
 * <h2>Key Components</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.compressor.driver.DriverCurve} - Interface defining the
 * contract for all driver curve implementations</li>
 * <li>{@link neqsim.process.equipment.compressor.driver.GasTurbineDriver} - Gas turbine performance
 * model with ambient derating</li>
 * <li>{@link neqsim.process.equipment.compressor.driver.ElectricMotorDriver} - Electric motor model
 * with VFD support</li>
 * <li>{@link neqsim.process.equipment.compressor.driver.SteamTurbineDriver} - Steam turbine model
 * with Willans line</li>
 * </ul>
 *
 * <h2>Gas Turbine Driver</h2>
 * <p>
 * Models ambient derating effects on gas turbine performance:
 * </p>
 * <ul>
 * <li>Temperature derating (~0.7% per °C above ISO)</li>
 * <li>Altitude derating (~3.5% per 1000m)</li>
 * <li>Heat rate curves for fuel consumption</li>
 * </ul>
 *
 * <pre>
 * GasTurbineDriver driver = new GasTurbineDriver();
 * driver.setRatedPower(15000.0); // 15 MW
 * driver.setRatedEfficiency(0.35); // 35%
 * driver.setAmbientTemperature(303.15); // 30°C
 * driver.setAltitude(500.0); // 500m
 *
 * double available = driver.getMaxAvailablePower(); // Derated power
 * double fuel = driver.getFuelConsumption(10000.0); // At 10 MW load
 * </pre>
 *
 * <h2>Electric Motor Driver</h2>
 * <p>
 * Models electric motor performance with optional VFD:
 * </p>
 * <ul>
 * <li>Efficiency curves vs load</li>
 * <li>Variable speed drive (VFD) support</li>
 * <li>Speed limits (min/max with VFD)</li>
 * </ul>
 *
 * <pre>
 * ElectricMotorDriver motor = new ElectricMotorDriver();
 * motor.setRatedPower(5000.0); // 5 MW
 * motor.setRatedEfficiency(0.96);
 * motor.setVariableSpeedDrive(true);
 * motor.setMinSpeed(600.0);
 * motor.setMaxSpeed(3600.0);
 *
 * double efficiency = motor.getEfficiency(0.75); // At 75% load
 * </pre>
 *
 * <h2>Steam Turbine Driver</h2>
 * <p>
 * Models steam turbine with Willans line for steam consumption:
 * </p>
 * <ul>
 * <li>Inlet/exhaust pressure and temperature</li>
 * <li>Isentropic efficiency</li>
 * <li>Steam consumption calculation</li>
 * </ul>
 *
 * <pre>
 * SteamTurbineDriver turbine = new SteamTurbineDriver();
 * turbine.setRatedPower(8000.0); // 8 MW
 * turbine.setInletPressure(40.0); // bara
 * turbine.setInletTemperature(673.15); // K
 * turbine.setExhaustPressure(4.0); // bara
 * turbine.setIsentropicEfficiency(0.78);
 *
 * double steam = turbine.getSteamConsumption(6000.0); // At 6 MW
 * </pre>
 *
 * <h2>Integration with Compressors</h2>
 * <p>
 * Drivers can be assigned to compressors for power constraint evaluation:
 * </p>
 *
 * <pre>
 * Compressor compressor = new Compressor("Export Compressor", feed);
 * GasTurbineDriver driver = new GasTurbineDriver();
 * driver.setRatedPower(15000.0);
 *
 * compressor.setDriver(driver);
 * compressor.run();
 *
 * // Check power utilization
 * double power = compressor.getPower(); // Shaft power required
 * double available = driver.getMaxAvailablePower(); // Available from driver
 * double utilization = power / available; // Utilization fraction
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see neqsim.process.equipment.compressor.Compressor
 * @see neqsim.process.equipment.compressor.OperatingEnvelope
 */
package neqsim.process.equipment.compressor.driver;
