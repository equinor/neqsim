"""Host inventory, inlet shutdown and gas-space PSV for the synthetic subsea tie-back."""

import math


SEPARATOR_PSHH_BARA = 49.5
INLET_SEAT_TIME_S = 2.
PSV_SET_BARA = 50.5
FLARE_BARA = 3.


class HostResponse:
    """Advance the native separator on the pipe's accepted net discharge."""

    def __init__(self, n, plant, separator):
        self.separator = separator
        self.choke = plant.getUnit("CHV-301")
        self.initial_dp = self.choke.getInletStream().getPressure("bara") - separator.getPressure("bara")
        self.design_rate = self.choke.getOutletStream().getFlowRate("kg/sec")
        self.choke.getInletStream().getFluid().initProperties()
        self.design_density = self.choke.getInletStream().getFluid().getDensity("kg/m3")
        self.operations = n.thermodynamicoperations.ThermodynamicOperations
        self.peak_opening = 0.
        separator.setCalculateSteadyState(False)
        separator.initializeTransientCalculation()
        self.gas = separator.getGasOutStream()
        self.gas_rate = self.gas.getFlowRate("kg/sec")
        self.psv = n.process.safety.depressurization.PsvValveModel(
            PSV_SET_BARA * 1e5, .03, .95, 5.e-4, FLARE_BARA * 1e5)
        self.flared = self.max_residual = 0.
        self.max_pressure = self.pressure()
        self.peak_relief_rate = 0.

    def pressure(self):
        return self.separator.getThermoSystem().getPressure("bara")

    def advance(self, t, dt, step, outlet, received_kg, blocked):
        sep = self.separator
        initial = sep.getThermoSystem().getMass("kg")
        # Preserve outlet composition and flash the pressure letdown at constant enthalpy.
        # A negligible carrier keeps the inlet flash defined after complete isolation.
        feed = outlet.clone("Host delivered fluid")
        feed.setFlowRate(max(received_kg / dt, 1e-9), "kg/sec")
        if not blocked:
            self.operations(feed.getFluid()).TPflash()
            feed.getFluid().initProperties()
            head = outlet.getPressure("bara") - self.pressure()
            if head <= 0:
                raise ValueError("No forward pressure drop at the regulated riser boundary")
            opening = .4 * received_kg / (dt * self.design_rate) * math.sqrt(
                self.initial_dp * self.design_density / (head * feed.getFluid().getDensity("kg/m3")))
            if not 0 <= opening <= 1:
                raise ValueError("Required host choke opening exceeds the installed control range")
            self.peak_opening = max(self.peak_opening, opening)
        self.choke.setInletStream(feed)
        self.choke.setOutletPressure(self.pressure(), "bara")
        self.choke.run(step)
        fluid = self.gas.getFluid()
        fluid.init(3)
        p_pa = self.pressure() * 1e5
        self.psv.update(p_pa)
        relief = self.psv.massFlowKgPerS(p_pa, fluid.getTemperature("K"), fluid.getMolarMass(),
                                          fluid.getGamma(), fluid.getZ())
        gas_out = relief + (self.gas_rate if t < 2. else 0.)
        self.gas.setFlowRate(max(gas_out, 1e-9), "kg/sec")
        sep.setGasOutletFlowFraction(1. if gas_out > 0 else 0.)
        for phase in ("Oil", "Water"):
            getattr(sep, "set" + phase + "OutletFlowFraction")(0. if blocked else 1.)
        liquid_out = 0. if blocked else (sep.getOilOutStream().getFlowRate("kg/sec")
                                         + sep.getWaterOutStream().getFlowRate("kg/sec"))
        sep.runTransient(dt, step)
        error = sep.getThermoSystem().getMass("kg") - initial - received_kg + dt * (gas_out + liquid_out)
        if not math.isfinite(error) or abs(error) > 1e-5:
            raise ValueError(f"Host inventory closure failed at {t + dt} s: {error} kg")
        self.max_residual = max(self.max_residual, abs(error))
        self.peak_relief_rate = max(self.peak_relief_rate, relief)
        self.max_pressure = max(self.max_pressure, self.pressure())
        self.flared += dt * relief

    def result(self):
        return {"peak_bara": self.max_pressure, "peak_relief_kg_s": self.peak_relief_rate,
                "relief_to_3_bara_header_kg": self.flared, "psv_cycles": self.psv.getCycleCount(),
                "max_required_choke_fraction": self.peak_opening, "max_mass_residual_kg": self.max_residual}
