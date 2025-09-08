# Process control framework

NeqSim contains a flexible process control framework for dynamic simulations.  
The framework provides:

- **PID controllers** through `ControllerDeviceBaseClass` implementing proportional,
  integral and derivative actions with anti-windup, derivative filtering and
  configurable output limits.
- **Auto‑tuning and gain scheduling** for adapting controller parameters to
  different operating conditions.
- **Event logging and performance metrics** such as integral absolute error and
  settling time for evaluating controller behaviour.
- **Advanced control structures** – cascade, ratio and feed‑forward – built on the
  common `ControlStructureInterface` for multi‑loop coordination.
- **Measurement devices** with explicit unit handling plus optional Gaussian noise
  and sample delay to emulate realistic transmitters.

See the unit tests in `src/test/java/neqsim/process/controllerdevice` for examples
of how the controllers and control structures are used in simulations.
