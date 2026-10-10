"""Regional hit rate and duration statistics from Sodir wellbore records.

Used by exploration strategy screening (APbo) tasks: the Sodir FactMaps wellbore layer gives, for
each exploration well, the formation at TD and up to three formations with hydrocarbons. From it
this module derives

* the regional hit rate of a unit (wells that penetrated the unit and have Sodir hydrocarbons in it)
  with a Beta posterior, and
* duration percentiles of exploration wells and of pilot (observation) holes in producers,
  reported in the P90 (low) / P50 / P10 (high) convention used in results.json.

Input records use the Sodir field names (wlbFormationAtTd, wlbFormationWithHc1..3, wlbWellType,
wlbPurpose, wlbEntryDate, wlbCompletionDate, wlbTotalDepth, wlbCompletionYear), dates in epoch
milliseconds as returned by the REST service.

Example:
    python devtools/sodir_wellbore_stats.py wellbores.json --unit TILJE --unit ARE
"""
import argparse
import json
import sys

MS_PER_DAY = 86400000.0


def normalise(text):
    """Upper-case a Sodir formation name and replace the Norwegian A-ring.

    Args:
        text: formation name or None

    Returns:
        upper-case ASCII-like string, empty for None
    """
    return (text or "").strip().upper().replace("\u00c5", "A")


def penetrated(well, keys):
    """Return True when the formation at TD of the well contains any of the keys.

    Args:
        well: Sodir wellbore record
        keys: iterable of normalised formation keywords

    Returns:
        True when the well reached the unit
    """
    td = normalise(well.get("wlbFormationAtTd"))
    return any(k in td for k in keys)


def has_hydrocarbons(well, name):
    """Return True when Sodir lists the formation name among the hydrocarbon-bearing formations.

    Args:
        well: Sodir wellbore record
        name: normalised formation name, for example "TILJE FM"

    Returns:
        True when listed in wlbFormationWithHc1..3
    """
    return any(normalise(well.get("wlbFormationWithHc%d" % i)) == name for i in (1, 2, 3))


def beta_posterior(k, n, prior_a=0.5, prior_b=0.5, level=0.8):
    """Beta posterior of a hit rate.

    Args:
        k: number of hits
        n: number of trials
        prior_a: prior alpha (Jeffreys 0.5)
        prior_b: prior beta (Jeffreys 0.5)
        level: central credible interval mass

    Returns:
        dict with mean, p_low, p_high (central interval; interval needs scipy and is None without it)
    """
    a = k + prior_a
    b = n - k + prior_b
    out = {"k": k, "n": n, "mean": a / (a + b), "p_low": None, "p_high": None}
    try:
        from scipy.stats import beta
        tail = (1.0 - level) / 2.0
        out["p_low"] = float(beta.ppf(tail, a, b))
        out["p_high"] = float(beta.ppf(1.0 - tail, a, b))
    except ImportError:
        pass
    return out


def hit_rate(wells, unit_keys, hc_name, min_year=None):
    """Regional hit rate of one unit among exploration wells that penetrated it.

    Args:
        wells: list of Sodir wellbore records
        unit_keys: keywords for the formation at TD that mean the unit was penetrated
        hc_name: normalised hydrocarbon formation name, for example "ARE FM"
        min_year: optional first completion year

    Returns:
        dict with the posterior and the lists of penetrating and hit wells
    """
    pen = []
    for w in wells:
        if w.get("wlbWellType") != "EXPLORATION" or w.get("wlbPurpose") not in ("WILDCAT", "APPRAISAL"):
            continue
        if min_year and (w.get("wlbCompletionYear") or 0) < min_year:
            continue
        if penetrated(w, unit_keys):
            pen.append(w)
    hits = [w for w in pen if has_hydrocarbons(w, hc_name)]
    res = beta_posterior(len(hits), len(pen))
    res["penetrations"] = [w.get("wlbWellboreName") for w in pen]
    res["hits"] = [w.get("wlbWellboreName") for w in hits]
    return res


def days(well):
    """Drilling duration of a wellbore in days from entry to completion date.

    Args:
        well: Sodir wellbore record

    Returns:
        days as float or None when a date is missing
    """
    a, b = well.get("wlbEntryDate"), well.get("wlbCompletionDate")
    if a is None or b is None:
        return None
    return (b - a) / MS_PER_DAY


def percentiles(values):
    """P90 (low), P50 and P10 (high) of a list; P90 is the 10th percentile of the sample.

    Args:
        values: list of numbers

    Returns:
        dict with P90, P50, P10 and n, or None for an empty list
    """
    vals = sorted(v for v in values if v is not None)
    if not vals:
        return None

    def q(p):
        x = (len(vals) - 1) * p
        lo = int(x)
        hi = min(lo + 1, len(vals) - 1)
        return vals[lo] + (vals[hi] - vals[lo]) * (x - lo)

    return {"P90": q(0.10), "P50": q(0.50), "P10": q(0.90), "n": len(vals)}


def pilot_hole_days(wells, field=None):
    """Durations of pilot (observation) holes drilled from development slots.

    Args:
        wells: list of Sodir wellbore records
        field: optional field name filter (wlbField)

    Returns:
        list of day durations
    """
    out = []
    for w in wells:
        if w.get("wlbPurpose") == "OBSERVATION" and w.get("wlbWellType") == "DEVELOPMENT":
            if field and w.get("wlbField") != field:
                continue
            d = days(w)
            if d is not None:
                out.append(d)
    return out


def main(argv=None):
    """Command line entry point.

    Args:
        argv: argument list or None for sys.argv

    Returns:
        process exit code
    """
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("wellbores", help="JSON list of Sodir wellbore records")
    ap.add_argument("--unit", action="append", default=[], help="unit keyword such as TILJE or ARE (hit name KEYWORD FM)")
    ap.add_argument("--field", default=None, help="field name for pilot-hole durations")
    args = ap.parse_args(argv)
    with open(args.wellbores, encoding="utf-8") as fh:
        wells = json.load(fh)
    report = {"hit_rates": {}, "exploration_days": percentiles([days(w) for w in wells if w.get("wlbWellType") == "EXPLORATION"]),
              "pilot_hole_days": percentiles(pilot_hole_days(wells, args.field))}
    for u in args.unit:
        key = normalise(u)
        report["hit_rates"][key] = hit_rate(wells, (key,), key + " FM")
    json.dump(report, sys.stdout, indent=1)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
