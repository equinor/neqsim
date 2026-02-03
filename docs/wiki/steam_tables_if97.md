---
title: "IF97 Steam Tables"
description: "This page documents the basic equations implemented in `Iapws_if97`."
---

# IF97 Steam Tables

This page documents the basic equations implemented in `Iapws_if97`.

## Saturation equations

For the saturation line (Region 4) the following equations are used:

Pressure as function of temperature:

$
\ln(p) = 4 \cdot \ln\left(\frac{2 C}{-B + \sqrt{B^2-4 A C}}\right)
$

where

$
A = \theta^2 + 1167.0521452767\,\theta - 724213.16703206\\
B = -17.073846940092\,\theta^2 + 12020.82470247\,\theta - 3232555.0322333\\
C = 14.91510861353\,\theta^2 - 4823.2657361591\,\theta + 405113.40542057\\
\theta = T - \frac{0.23855557567849}{T-650.17534844798}
$

Temperature as function of pressure is obtained by solving the inverse relation.

## Region 1 and 2

The specific Gibbs free energy is expressed with dimensionless variables
$\pi$ and $\tau$. For region 1

$
\gamma(\pi,\tau)=\sum n_i (7.1-\pi)^{I_i} (\tau-1.222)^{J_i}
$

while region 2 uses an ideal and residual part

$
\gamma(\pi,\tau)=\ln\pi + \sum n_i^0\tau^{J_i^0} + \sum n_i^r\pi^{I_i^r}(\tau-0.5)^{J_i^r}
$

Thermodynamic properties follow from derivatives of $\gamma$:

$
 v = \frac{R T}{p}\,\pi\, \gamma_{\pi} \quad\quad
 h = R T\tau\, \gamma_{\tau} \\
 s = R(\tau\gamma_{\tau}-\gamma)
$

where $R=0.461526\,\mathrm{kJ\,kg^{-1}\,K^{-1}}$.
