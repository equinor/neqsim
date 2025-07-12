# Flow meter models

This page documents the equations implemented in the `Orifice` equipment for
computing flow through differential pressure meters. All variables are in SI
units.

## Orifice plate

The discharge coefficient $C$ is calculated with the Reader&ndash;Harris/Gallagher
correlation as implemented in ISO&nbsp;5167:

$$
C = 0.5961 + 0.0261\beta^2 - 0.216\beta^8 + 0.000521\left(\frac{10^6\beta}{Re_D}\right)^{0.7}
    +(0.0188 + 0.0063A)\beta^{3.5}\left(\frac{10^6}{Re_D}\right)^{0.3}
    +(0.043 + 0.080e^{-10L_1}-0.123e^{-7L_1})(1-0.11A)\frac{\beta^4}{1-\beta^4}
    -0.031(M_2' -0.8M_2'^{1.1})\beta^{1.3}
$$

The expansibility factor is
$$
\epsilon = 1 - (0.351 +0.256\beta^4 +0.93\beta^8)\left[1-\left(\frac{P_2}{P_1}\right)^{1/\kappa}\right]
$$

The mass flow rate is obtained iteratively from
$$
 m = \left(\tfrac{\pi D^2\beta^2}{4}\right) C \epsilon \frac{\sqrt{2\rho(P_1-P_2)}}{\sqrt{1-\beta^4}}.
$$
