"""Debug density solver for binary CH4/C2H6."""
import teqp
import numpy as np

coeffs = [
    {"name": "Methane", "m": 1.0, "sigma_Angstrom": 3.7412, "epsilon_over_k": 153.36,
     "lambda_r": 12.65, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
    {"name": "Ethane", "m": 1.4373, "sigma_Angstrom": 3.7257, "epsilon_over_k": 206.12,
     "lambda_r": 12.4, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
]
model = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs}})
R = 8.314462618
T = 250.0

# Scan P vs rho for pure ethane and x_CH4=0.15
for x_CH4 in [0.001, 0.15]:
    z = np.array([x_CH4, 1-x_CH4])
    print(f"\nx_CH4 = {x_CH4}")
    print(f"{'rho':>10} {'P(bar)':>10} {'Z':>10}")
    for rho in np.concatenate([np.linspace(100, 5000, 20), np.linspace(5000, 20000, 20)]):
        try:
            Ar01 = model.get_Ar01(T, rho, z)
            P = rho * R * T * (1.0 + Ar01)
            Z = 1.0 + Ar01
            print(f"{rho:10.0f} {P/1e5:10.3f} {Z:10.6f}")
        except:
            print(f"{rho:10.0f} ERROR")
