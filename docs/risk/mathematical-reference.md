# Mathematical Reference

This document provides the mathematical foundations for the Risk Simulation Framework, including reliability theory, Monte Carlo methods, and risk calculations.

---

## 1. Reliability Theory

### 1.1 Failure Rate (Hazard Function)

The failure rate $\lambda(t)$ is the conditional probability of failure per unit time:

$$\lambda(t) = \lim_{\Delta t \to 0} \frac{P(t < T \leq t + \Delta t | T > t)}{\Delta t} = \frac{f(t)}{R(t)}$$

For **constant failure rate** (exponential distribution):

$$\lambda(t) = \lambda \quad \text{(constant)}$$

### 1.2 Reliability Function

The reliability function $R(t)$ is the probability of survival to time $t$:

$$R(t) = P(T > t) = e^{-\int_0^t \lambda(u) du}$$

For constant failure rate:

$$R(t) = e^{-\lambda t}$$

### 1.3 Mean Time To Failure (MTTF)

$$\text{MTTF} = E[T] = \int_0^\infty R(t) dt = \int_0^\infty e^{-\lambda t} dt = \frac{1}{\lambda}$$

### 1.4 Mean Time Between Failures (MTBF)

$$\text{MTBF} = \text{MTTF} + \text{MTTR}$$

### 1.5 Availability

**Steady-state availability:**

$$A = \frac{\text{MTTF}}{\text{MTTF} + \text{MTTR}} = \frac{\text{Uptime}}{\text{Total Time}}$$

**Instantaneous availability** (for repairable systems):

$$A(t) = \frac{\mu}{\lambda + \mu} + \frac{\lambda}{\lambda + \mu} e^{-(\lambda + \mu)t}$$

Where $\mu = 1/\text{MTTR}$ is the repair rate.

---

## 2. System Reliability

### 2.1 Series System

All components must function (AND logic):

```
┌───┐   ┌───┐   ┌───┐
│ A │───│ B │───│ C │
└───┘   └───┘   └───┘
```

$$R_s(t) = \prod_{i=1}^n R_i(t)$$

$$A_s = \prod_{i=1}^n A_i$$

**Example:** Three components with 99% availability each:
$$A_s = 0.99^3 = 0.970$$

### 2.2 Parallel System (Active Redundancy)

System works if any component functions (OR logic):

```
    ┌───┐
┌───│ A │───┐
│   └───┘   │
│   ┌───┐   │
├───│ B │───┤
│   └───┘   │
└───────────┘
```

$$R_p(t) = 1 - \prod_{i=1}^n (1 - R_i(t))$$

$$A_p = 1 - \prod_{i=1}^n (1 - A_i)$$

**Example:** Two components with 99% availability each:
$$A_p = 1 - (1-0.99)^2 = 0.9999$$

### 2.3 k-out-of-n System

System works if at least $k$ of $n$ components function:

$$R_{k/n}(t) = \sum_{i=k}^n \binom{n}{i} R(t)^i (1-R(t))^{n-i}$$

**2-out-of-3 system:**
$$R_{2/3} = 3R^2(1-R) + R^3 = 3R^2 - 2R^3$$

---

## 3. Monte Carlo Simulation

### 3.1 Random Variate Generation

**Exponential distribution** (for failure/repair times):

$$T = -\frac{1}{\lambda} \ln(U), \quad U \sim \text{Uniform}(0,1)$$

**Weibull distribution** (for wear-out failures):

$$T = \eta \cdot (-\ln(U))^{1/\beta}$$

Where $\eta$ is scale parameter, $\beta$ is shape parameter.

### 3.2 Simulation Algorithm

```
For each iteration i = 1 to N:
    t = 0
    Initialize all equipment to OPERATING
    production[i] = 0
    
    While t < T_horizon:
        # Generate next event
        For each equipment j:
            If operating: t_fail[j] = t + Exp(λ_j)
            If failed: t_repair[j] = t + Exp(μ_j)
        
        t_next = min(all event times)
        
        # Advance time and update state
        production[i] += P(state) × (t_next - t)
        t = t_next
        Update equipment states
    
    Store production[i]

Calculate statistics from production[]
```

### 3.3 Confidence Intervals

**For mean:**

$$\bar{X} \pm z_{\alpha/2} \frac{s}{\sqrt{n}}$$

**95% confidence interval** ($z_{0.025} = 1.96$):

$$CI_{95\%} = \bar{X} \pm 1.96 \frac{s}{\sqrt{n}}$$

### 3.4 Percentile Estimation

**Order statistics method:**

Sort $n$ samples: $X_{(1)} \leq X_{(2)} \leq ... \leq X_{(n)}$

For percentile $p$:
$$\hat{X}_p = X_{(\lceil np \rceil)}$$

**P10, P50, P90:**
- P10: 10th percentile (optimistic)
- P50: 50th percentile (median)
- P90: 90th percentile (conservative)

### 3.5 Convergence

**Standard error of mean:**

$$SE = \frac{\sigma}{\sqrt{n}}$$

**Required sample size for precision $\epsilon$:**

$$n = \left(\frac{z_{\alpha/2} \cdot \sigma}{\epsilon}\right)^2$$

---

## 4. Risk Calculations

### 4.1 Risk Score

$$\text{Risk Score} = P \times C$$

Where:
- $P$ = Probability level (1-5)
- $C$ = Consequence level (1-5)

### 4.2 Annual Risk Cost

$$C_{\text{annual}} = \lambda \times C_{\text{event}}$$

Where $C_{\text{event}}$ is the cost per failure event.

### 4.3 Event Cost Components

$$C_{\text{event}} = C_{\text{production}} + C_{\text{downtime}} + C_{\text{repair}}$$

**Production loss cost:**
$$C_{\text{production}} = \text{MTTR} \times \dot{m} \times \text{Loss\%} \times P_{\text{product}}$$

Where:
- $\dot{m}$ = mass flow rate (kg/hr)
- $P_{\text{product}}$ = product price ($/kg)

**Downtime cost:**
$$C_{\text{downtime}} = \text{MTTR} \times C_{\text{fixed}}$$

### 4.4 Expected Annual Production Loss

$$E[\text{Loss}] = \sum_{i=1}^n \lambda_i \times \text{MTTR}_i \times L_i$$

Where $L_i$ is the production loss fraction for equipment $i$.

### 4.5 Production Availability

$$A_{\text{production}} = 1 - \sum_{i=1}^n \frac{\lambda_i \times \text{MTTR}_i \times L_i}{8760}$$

---

## 5. Production Impact

### 5.1 Production Loss Percentage

$$L\% = \frac{P_{\text{normal}} - P_{\text{degraded}}}{P_{\text{normal}}} \times 100\%$$

### 5.2 Capacity Factor Effect

When equipment operates at reduced capacity $C_f$:

$$P_{\text{degraded}} = P_{\text{normal}} \times f(C_f)$$

For simple proportional relationship:
$$f(C_f) = C_f$$

For non-linear (e.g., compressor at reduced speed):
$$f(C_f) = C_f^\alpha, \quad \alpha > 1$$

### 5.3 Criticality Index

$$CI_i = \frac{L_i}{\max_j(L_j)}$$

Equipment with $CI > 0.8$ is "critical".

---

## 6. Dependency Analysis

### 6.1 Cascade Effect Propagation

For equipment $j$ downstream of failed equipment $i$:

$$L_j = L_i \times T_{ij}$$

Where $T_{ij}$ is the transmission factor (0-1).

### 6.2 Criticality Increase

When equipment $i$ fails, criticality of parallel equipment $j$ increases:

$$CI_j^{\text{new}} = CI_j^{\text{base}} \times \frac{n}{n - 1}$$

Where $n$ is the number of parallel trains.

### 6.3 Cross-Installation Impact

$$L_{\text{target}} = L_{\text{source}} \times \text{Impact Factor}$$

---

## 7. Probability Distributions

### 7.1 Exponential Distribution

**PDF:** $f(t) = \lambda e^{-\lambda t}$

**CDF:** $F(t) = 1 - e^{-\lambda t}$

**Mean:** $E[T] = 1/\lambda$

**Variance:** $\text{Var}[T] = 1/\lambda^2$

### 7.2 Weibull Distribution

**PDF:** $f(t) = \frac{\beta}{\eta}\left(\frac{t}{\eta}\right)^{\beta-1} e^{-(t/\eta)^\beta}$

**Mean:** $E[T] = \eta \cdot \Gamma(1 + 1/\beta)$

**Special cases:**
- $\beta = 1$: Exponential (constant failure rate)
- $\beta < 1$: Decreasing failure rate (infant mortality)
- $\beta > 1$: Increasing failure rate (wear-out)

### 7.3 Log-Normal Distribution

For repair times:

**PDF:** $f(t) = \frac{1}{t\sigma\sqrt{2\pi}} e^{-\frac{(\ln t - \mu)^2}{2\sigma^2}}$

**Mean:** $E[T] = e^{\mu + \sigma^2/2}$

---

## 8. Statistical Formulas

### 8.1 Sample Mean

$$\bar{X} = \frac{1}{n}\sum_{i=1}^n X_i$$

### 8.2 Sample Variance

$$s^2 = \frac{1}{n-1}\sum_{i=1}^n (X_i - \bar{X})^2$$

### 8.3 Coefficient of Variation

$$CV = \frac{s}{\bar{X}} \times 100\%$$

### 8.4 Binomial Coefficient

$$\binom{n}{k} = \frac{n!}{k!(n-k)!}$$

---

## 9. Numerical Methods

### 9.1 Newton-Raphson (for optimization)

$$x_{n+1} = x_n - \frac{f(x_n)}{f'(x_n)}$$

### 9.2 Trapezoidal Integration

$$\int_a^b f(x)dx \approx \frac{h}{2}\sum_{i=1}^{n-1}(f(x_i) + f(x_{i+1}))$$

### 9.3 Linear Interpolation (for percentiles)

$$X_p = X_k + (p \cdot n - k)(X_{k+1} - X_k)$$

---

## 10. Unit Conversions

| From | To | Factor |
|------|-----|--------|
| hours | years | ÷ 8760 |
| failures/year | failures/hour | ÷ 8760 |
| kg/hr | tonnes/day | × 0.024 |
| bara | psia | × 14.5038 |
| °C | K | + 273.15 |

---

## See Also

- [Monte Carlo Simulation](monte-carlo.md)
- [Risk Matrix](risk-matrix.md)
- [Equipment Failure Modeling](equipment-failure.md)
- [API Reference](api-reference.md)
