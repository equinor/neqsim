"""EWMA and two-sided CUSUM drift monitor with a frozen warm-up baseline.

The first ``warmup`` values of a signal fix its baseline mean and standard deviation;
after that, each value updates an EWMA statistic (started at the baseline mean) and a
two-sided standardised CUSUM. An alarm needs ``confirm`` consecutive out-of-control
values, and ``min_sigma`` sets an engineering floor on the standard deviation so that
statistically real but practically irrelevant shifts do not alarm. Per-signal overrides
go under ``settings["signals"][name]``.
An alarm is a question, not a diagnosis: the caller classifies it.
"""

import json
import math
import os

DEFAULTS = {"lambda": 0.2, "L": 3.5, "k": 0.5, "h": 6.0, "warmup": 30, "confirm": 2,
            "min_sigma": 0.0}


class DriftMonitor(object):
    """Drift monitor for many named scalar signals; state is JSON-serialisable."""

    def __init__(self, settings=None, state=None):
        self.settings = dict(DEFAULTS)
        self.settings.update(settings or {})
        self.state = state or {}

    @classmethod
    def load(cls, path, settings=None):
        state = {}
        if os.path.exists(str(path)):
            with open(str(path), "r", encoding="utf-8") as f:
                state = json.load(f)
        return cls(settings, state)

    def save(self, path):
        directory = os.path.dirname(str(path))
        if directory:
            os.makedirs(directory, exist_ok=True)
        with open(str(path), "w", encoding="utf-8") as f:
            json.dump(self.state, f, indent=2, sort_keys=True)

    def settings_for(self, name):
        """Return the settings of one signal, with its overrides applied."""
        merged = {k: v for k, v in self.settings.items() if k != "signals"}
        merged.update((self.settings.get("signals") or {}).get(name, {}))
        return merged

    def update(self, name, value):
        """Feed one value; return a status dict with ``new_alarm`` on the first alarm."""
        s = self.state.setdefault(name, {"n": 0, "mean": 0.0, "m2": 0.0, "z": None,
                                        "cusum_pos": 0.0, "cusum_neg": 0.0, "alarm": False})
        s.setdefault("streak", 0)
        cfg = self.settings_for(name)
        value = float(value)
        warmup = int(cfg["warmup"])
        if s["n"] < warmup:
            s["n"] += 1
            delta = value - s["mean"]
            s["mean"] += delta / s["n"]
            s["m2"] += delta * (value - s["mean"])
            return {"name": name, "value": value, "phase": "warmup", "alarm": False,
                    "new_alarm": False}
        sigma = max(math.sqrt(s["m2"] / max(s["n"] - 1, 1)), float(cfg["min_sigma"])) or 1e-12
        lam = float(cfg["lambda"])
        previous = s["mean"] if s["z"] is None else s["z"]
        s["z"] = lam * value + (1 - lam) * previous
        limit = float(cfg["L"]) * sigma * math.sqrt(lam / (2 - lam))
        standard = (value - s["mean"]) / sigma
        k = float(cfg["k"])
        s["cusum_pos"] = max(0.0, s["cusum_pos"] + standard - k)
        s["cusum_neg"] = max(0.0, s["cusum_neg"] - standard - k)
        ewma_alarm = abs(s["z"] - s["mean"]) > limit
        cusum_alarm = max(s["cusum_pos"], s["cusum_neg"]) > float(cfg["h"])
        out_of_control = ewma_alarm or cusum_alarm
        s["streak"] = s["streak"] + 1 if out_of_control else 0
        alarm = s["streak"] >= max(int(cfg["confirm"]), 1)
        new_alarm = alarm and not s["alarm"]
        s["alarm"] = s["alarm"] or alarm
        return {"name": name, "value": value, "phase": "monitor", "baseline_mean": s["mean"],
                "baseline_std": sigma, "ewma": s["z"], "ewma_limit": limit,
                "cusum_pos": s["cusum_pos"], "cusum_neg": s["cusum_neg"],
                "ewma_alarm": ewma_alarm, "cusum_alarm": cusum_alarm,
                "direction": "up" if value > s["mean"] else "down",
                "alarm": alarm, "new_alarm": new_alarm}

    def reset(self, name):
        """Clear the alarm and statistics of a signal (keeps the baseline)."""
        s = self.state.get(name)
        if s:
            s.update({"z": None, "cusum_pos": 0.0, "cusum_neg": 0.0, "alarm": False, "streak": 0})
