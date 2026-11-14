# Sealed radial annulus reference

The dimensionless radius is $1<r<R$. Pressure $P$ satisfies

$$
P_t=(rP_r)_r/r,\qquad P(r,0)=0,\qquad -P_r(1,t)=1,\qquad P_r(R,t)=0.
$$

The radial storage is $C=(R^2-1)/2$. Integrating the equation over the
annulus gives $\int_1^R rP\,dr=t$. This fixes the constant mode and the
late pressure slope $1/C$.

For a positive Neumann eigenvalue $\lambda_n$, take

$$
\phi_n(r)=Y_1(\lambda_n)J_0(\lambda_n r)-J_1(\lambda_n)Y_0(\lambda_n r).
$$

The outer Neumann condition sets
$J_1(\lambda_n)Y_1(R\lambda_n)-Y_1(\lambda_n)J_1(R\lambda_n)=0$.
Integration by parts gives
$\partial_t\langle P,\phi_n\rangle+\lambda_n^2\langle P,\phi_n\rangle=\phi_n(1)$
in the inner product weighted by $r$. The norm identity follows by
differentiating $r^2[\phi_n(r)^2+\phi_n'(r)^2/\lambda_n^2]/2$.

$$
N_n=\int_1^R r\phi_n^2\,dr=\frac{R^2\phi_n(R)^2-\phi_n(1)^2}{2},
\qquad A_n=\frac{\phi_n(1)^2}{N_n}.
$$

The inner Wronskian gives $\phi_n(1)=-2/(\pi\lambda_n)$. At the outer
root the two order-one Bessel vectors are parallel, so the weight reduces to

$$
A_n=\frac{2}{M(\lambda_n)/M(R\lambda_n)-1},\qquad M(x)=J_1(x)^2+Y_1(x)^2.
$$

Writing the steady spatial shape as $P=t/C+F(r)$ gives
$F'(r)=(r-R^2/r)/(R^2-1)$. Its weighted mean is zero. Thus the wall intercept is

$$
B=F(1)=\frac{R^4\log R}{(R^2-1)^2}-\frac{3R^2-1}{4(R^2-1)}.
$$

The wall series are

$$
P'(1,t)=C^{-1}+\sum_{n\geq1} A_n e^{-\lambda_n^2t},\qquad
P(1,t)=t/C+B-\sum_{n\geq1}\frac{A_n}{\lambda_n^2}e^{-\lambda_n^2t}.
$$

## Preparation and truncation bounds

Using Nicholson's integral and substituting $u=2x\sinh v$ gives

$$
xM(x)=\frac{4}{\pi^2}\int_0^\infty K_0(u)
\frac{1+2y^2}{\sqrt{1+y^2}}\,du,\qquad y=\frac{u}{2x}.
$$

The factor increases with $y>0$. Hence $xM(x)$ decreases towards
$2/\pi$, and $A_n<2/(R-1)$. For continuous Bessel phase
$\theta=\arg(J_1+iY_1)$, the Wronskian gives
$\theta'=2/[\pi xM(x)]$, increasing and bounded by 1. Thus
$\theta(R\lambda)-\theta(\lambda)$ is strictly increasing. The phase
defect $\theta(x)-x$ decreases from $-\pi/2$ to $-3\pi/4$, so

$$
\frac{n\pi}{R-1}<\lambda_n<\frac{n\pi+\pi/4}{R-1}.
$$

For $t\geq(R-1)^2/64$, let $c=\pi^2/64$. The omitted derivative
after $N$ terms is bounded by

$$
\frac{2}{R-1}\frac{e^{-c(N+1)^2}}{1-e^{-c(2N+3)}}.
$$

Multiply this by $[(R-1)/(\pi(N+1))]^2$ for the omitted pressure
transient. These bounds address truncation of exact modes. The implementation
also needs to control root search, Bessel approximation and summation errors.

Sources for the Bessel identity, continuous phase branch and phase asymptote
are NIST DLMF [10.9.30](https://dlmf.nist.gov/10.9.E30),
[10.18.3](https://dlmf.nist.gov/10.18.E3) and
[10.18.18](https://dlmf.nist.gov/10.18.E18).

## Early outer reflection

Let $L=R-1$ and $t_s=L^2/64$. For the infinite response define
$F=-\partial_r P_\infty$ and $g=\sqrt{r}F$. Then
$g_t=g_{rr}-3g/(4r^2)$, with a unit Dirichlet step at $r=1$ and zero initial
data. Compare nonnegative Dirichlet pulses with the half-line heat equation
without the killing term. Passing to the impulse limit bounds the derivative
of the wall-driven flux at the outer radius:

$$
0\le F_t(R,t) \le \frac{L}{2\sqrt{\pi R}\,t^{3/2}}
\exp[-L^2/(4t)].
$$

Set $\epsilon=1/8$ and $\beta=60/L$. Maximizing
$t^{-3/2}\exp[-\epsilon L^2/(4t)]$ and completing a square gives
$F_t(R,t)\le M\exp[\beta^2t-\beta L\sqrt{1-\epsilon}]$, where

$$
M=\frac{L}{2\sqrt{\pi R}}\left(\frac{6}{\epsilon L^2}\right)^{3/2}
\exp(-3/2).
$$

The derivative difference $W=\partial_t(P-P_\infty)$ solves the radial heat
equation on the annulus with zero initial data, $W_r(1,t)=0$ and
$W_r(R,t)=F_t(R,t)$. Use the supersolution

$$
H(r,t)=\frac{M\exp[(\beta^2+\beta)t-\beta L\sqrt{1-\epsilon}]
\cosh[\beta(r-1)]}{\beta\sinh(\beta L)}.
$$

Its derivative is zero at the inner wall and bounds $W_r$ at the outer wall.
Its radial diffusion operator is at most $(\beta^2+\beta)H$ since $r\ge1$.
Comparison gives $0\le W(1,t)\le H(1,t)$. On $1\le L\le9$, the exponential
factor at $t_s$, after factoring the denominator's hyperbolic sine, has
exponent $56.25+0.9375L-60\sqrt{7/8}-60<-51.4$. The prefactor
$2M/[\beta(1-e^{-120})]$ is at most 0.5. Thus the derivative difference is
below $3\times10^{-23}$ and the pressure difference is below
$4\times10^{-23}$ for $0<t\le t_s$.

This comparison bounds the outer boundary's effect for exact responses.
The evaluation error of the infinite integral and finite modes governs the
remaining junction error in binary64 arithmetic.

## Independent numerical reference

The script solves the Laplace-transformed boundary conditions directly with
modified Bessel functions and inverts the response and derivative transforms
at two working precisions. It uses neither modal roots nor the proposed
early-time approximation. Inputs use exact decimal switch multipliers, with
the corresponding binary64 time retained in the output.

The 36 pressure/derivative pairs pass 32/44-digit agreement at the declared
$10^{-20}$ absolute plus $10^{-20}$ relative bound. The largest differences
are $4.787\times 10^{-29}$ for pressure and $7.877\times 10^{-31}$ for the
derivative. `independent-annulus.json` retains both precision results, errors,
analytic late-time limits and the generator hash.

Reproduction command, observed exit 0:

```sh
PYTHONDONTWRITEBYTECODE=1 /opt/codex/runtimes/codex-primary-runtime/dependencies/python/bin/python3 -O independent-annulus.py
```

Agreement at 0.999 and 1.001 times the switch leaves strict floating-point
monotonicity between adjacent doubles to be checked on the candidate.
