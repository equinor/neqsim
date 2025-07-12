# Humid air mathematics

This page summarises the equations implemented in the `HumidAir` utility class for psychrometric calculations. The correlations are based on the ASHRAE Handbook Fundamentals (2017), CoolProp and the IAPWS formulation for the saturation pressure of water.

## Saturation pressure of water
For temperatures $T$ above the triple point the saturation vapour pressure $p_{ws}$ in pascal is given by the IAPWS equation of Wagner and Pruss (2002)

$$
\ln\left(\frac{p_{ws}}{p_c}\right) = \frac{T_c}{T}\left(a_1\theta + a_2\theta^{3/2} + a_3\theta^3 + a_4\theta^{7/2} + a_5\theta^4 + a_6\theta^{15/2}\right)
$$

where $\theta = 1 - T/T_c$, $T_c = 647.096\ \text{K}$ and $p_c = 22.064\ \text{MPa}$. Below the triple point a sublimation correlation is used.

## Humidity ratio
The humidity ratio $W$ relates the mass of water vapour to the mass of dry air

$$
W = \varepsilon \frac{p_w}{p - p_w}
$$

where $\varepsilon = M_w/M_{da} \approx 0.621945$, $p$ is the total pressure and $p_w$ the partial pressure of water.

For a given relative humidity $\phi$, the partial pressure is $p_w = \phi p_{ws}$.

## Dew point temperature
Given a humidity ratio, the dew point temperature $T_d$ is found by solving $p_{ws}(T_d) = p_w$. The `HumidAir` implementation uses a simple Newton iteration.

## Specific enthalpy
On a dry-air basis the specific enthalpy $h$ in kJ/kg dry air is approximated by

$$
h = 1.006\,t + W (2501 + 1.86\,t)
$$

where $t$ is the temperature in degrees Celsius and $W$ is the humidity ratio.

## Saturated specific heat
CoolProp provides a correlation for the saturated humid-air specific heat $c_{p,\text{sat}}$ at 1\,atm valid from 250\,K to 300\,K

$$
c_{p,\text{sat}} = 2.146\,27073 \times 10^{3} - 3.289\,17768 \times 10^{1}T + 1.894\,71075 \times 10^{-1}T^2 \\
 - 4.862\,90986 \times 10^{-4}T^3 + 4.695\,40143 \times 10^{-7}T^4.
$$

