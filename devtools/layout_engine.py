"""Bounded orthogonal placement and independent plant-layout geometry checks.

The deterministic backend finds feasible candidates on a declared grid. It does
not prove optimality or global infeasibility, and its pipe-length metric is a
connection-distance proxy rather than a routed or stress-qualified piping model.
"""

from __future__ import annotations

import copy
import math
import time

from layout_model import (TOLERANCE_M, box_distance, boxes_intersect, equipment_bounds,
                          normalize_study, rule_applies, source_is_verified, study_fingerprint)


MODEL_SOURCE = {"kind": "software_model", "reference": "plant_layout_study.v1 orthogonal screening geometry"}


def _check(identifier, kind, status, message, equipment=None, required=True, source=None, **values):
    """Build a consistent, JSON-compatible finding."""
    result = {"id": identifier, "kind": kind, "status": status, "required": required,
              "equipment": equipment or [], "message": message, "source": source or dict(MODEL_SOURCE)}
    result.update(values)
    return result


def _inside(bounds, deck):
    """Check containment in the allocated rectangular deck volume.

    Below-deck equipment or reserved volumes are outside this screening profile.
    Open-air plots without a supplied clear-height have no upper z boundary.
    """
    return (bounds[0] >= -TOLERANCE_M and bounds[1] >= -TOLERANCE_M
            and bounds[3] <= deck["width_m"] + TOLERANCE_M
            and bounds[4] <= deck["depth_m"] + TOLERANCE_M
            and bounds[2] >= deck["elevation_m"] - TOLERANCE_M
            and ("height_limit_m" not in deck
                 or bounds[5] <= deck["elevation_m"] + deck["height_limit_m"] + TOLERANCE_M))


def _obstacle_bounds(obstacle, decks):
    """Interpret obstacle z coordinates as local heights above the owning deck."""
    bounds = list(obstacle["bounds_m"])
    elevation = decks[obstacle["deck"]]["elevation_m"]
    bounds[2] += elevation
    bounds[5] += elevation
    return bounds


def _corridor_box(corridor, deck):
    """Construct the reserved horizontal corridor over the deck clear height."""
    bounds = corridor["bounds_m"]
    z = deck["elevation_m"]
    return [bounds[0], bounds[1], z, bounds[2], bounds[3], z + deck.get("height_limit_m", 1000000)]


def _touches(first, second):
    """Test corridor intersection, rejecting an unusable corner-only contact."""
    dx = min(first[2], second[2]) - max(first[0], second[0])
    dy = min(first[3], second[3]) - max(first[1], second[1])
    return dx >= -TOLERANCE_M and dy >= -TOLERANCE_M and max(dx, dy) > TOLERANCE_M


def _point_in(point, bounds):
    """Test a point against a closed planar rectangle."""
    return (bounds[0] - TOLERANCE_M <= point[0] <= bounds[2] + TOLERANCE_M
            and bounds[1] - TOLERANCE_M <= point[1] <= bounds[3] + TOLERANCE_M)


def _corridor_inside(corridor, deck):
    """Return whether a corridor has a valid footprint on its owning deck."""
    bounds = corridor["bounds_m"]
    return (bounds[0] >= -TOLERANCE_M and bounds[1] >= -TOLERANCE_M
            and bounds[2] <= deck["width_m"] + TOLERANCE_M
            and bounds[3] <= deck["depth_m"] + TOLERANCE_M)


def _escape_checks(study, decks):
    """Check corridor-to-exit continuity, without claiming safe evacuation."""
    findings = []
    for deck_id, deck in decks.items():
        corridors = [c for c in study["corridors"] if c["deck"] == deck_id and c.get("kind") == "escape"]
        exits = [e for e in study["exits"] if e["deck"] == deck_id]
        invalid = [c["id"] for c in corridors if not _corridor_inside(c, deck)]
        if invalid:
            findings.append(_check("escape-network-" + deck_id, "escape_connectivity", "FAIL",
                                   "Off-deck corridor geometry cannot establish escape connectivity",
                                   evidence={"invalid_corridors": invalid}))
            continue
        if not corridors or not exits:
            findings.append(_check("escape-network-" + deck_id, "escape_connectivity", "NOT_EVALUATED",
                                   "Escape corridor geometry and exits are required for connectivity screening."))
            continue
        remaining = set(range(len(corridors)))
        components = []
        while remaining:
            component = {min(remaining)}
            remaining -= component
            pending = list(component)
            while pending:
                index = pending.pop()
                linked = {i for i in remaining if _touches(corridors[index]["bounds_m"], corridors[i]["bounds_m"])}
                remaining -= linked
                component |= linked
                pending.extend(sorted(linked))
            components.append(component)
        valid_exits = [e for e in exits if 0 <= e["x_m"] <= deck["width_m"] and 0 <= e["y_m"] <= deck["depth_m"]]
        orphan = [i for i, component in enumerate(components) if not any(
            _point_in((exit_["x_m"], exit_["y_m"]), corridors[index]["bounds_m"])
            for index in component for exit_ in valid_exits)]
        unattached = [e["id"] for e in exits if not any(_point_in((e["x_m"], e["y_m"]), c["bounds_m"]) for c in corridors)]
        status = "FAIL" if orphan or unattached or len(valid_exits) != len(exits) else "PASS"
        findings.append(_check("escape-network-" + deck_id, "escape_connectivity", status,
                               "Corridor-to-exit connectivity only; equipment access, travel capacity and common-hazard independence are not established.",
                               evidence={"components": len(components), "components_without_exit": orphan,
                                         "unattached_exits": unattached, "exit_count": len(exits)}))
    return findings


def _corridor_checks(study, decks):
    """Check reserved geometry itself before trusting corridor connectivity."""
    findings = []
    for corridor in study["corridors"]:
        deck = decks[corridor["deck"]]
        bounds = corridor["bounds_m"]
        inside = _corridor_inside(corridor, deck)
        findings.append(_check("corridor-boundary-" + corridor["id"], "corridor_boundary",
                               "PASS" if inside else "FAIL", "Reserved corridor footprint within allocated plot/deck",
                               measured=bounds, unit="m"))
        prism = _corridor_box(corridor, deck)
        for obstacle in study["obstacles"]:
            if obstacle["deck"] == deck["id"] and boxes_intersect(prism, _obstacle_bounds(obstacle, decks)):
                findings.append(_check("corridor-obstacle-%s-%s" % (corridor["id"], obstacle["id"]),
                                       "corridor_obstruction", "FAIL",
                                       "Fixed obstacle %s obstructs reserved corridor %s" % (obstacle["id"], corridor["id"])))
    return findings


def check_layout(study, alternative):
    """Independently assess a candidate using exact orthogonal box geometry.

    Findings preserve unverified requirements as NOT_EVALUATED. Physical contact
    at a common boundary is permitted unless an explicit separation rule applies.
    The function tolerates malformed candidate placements and emits FAIL findings
    rather than converting missing equipment into a successful sparse layout.
    """
    study = normalize_study(study)
    decks = {d["id"]: d for d in study["decks"]}
    equipment = {e["tag"]: e for e in study["equipment"]}
    checks = []
    placements = alternative.get("placements", [])
    if not isinstance(placements, list):
        return [_check("placement-register", "placement", "FAIL", "placements must be a list")]
    by_tag = {}
    for placement in placements:
        if not isinstance(placement, dict) or placement.get("tag") not in equipment:
            checks.append(_check("placement-unknown-%d" % len(checks), "placement", "FAIL", "Unknown or malformed equipment placement"))
            continue
        tag = placement["tag"]
        if tag in by_tag:
            checks.append(_check("placement-duplicate-" + tag, "placement", "FAIL", "Duplicate equipment placement", [tag]))
        by_tag[tag] = placement
    valid = {}
    for tag, item in equipment.items():
        placement = by_tag.get(tag)
        if placement is None:
            checks.append(_check("placement-" + tag, "placement", "FAIL", "Equipment is not placed", [tag]))
            continue
        deck = decks.get(placement.get("deck"))
        rotation = placement.get("rotation_deg", 0)
        if deck is None or deck["id"] not in item["allowed_decks"] or rotation not in item["rotations_deg"]:
            checks.append(_check("placement-" + tag, "placement", "FAIL", "Unknown/disallowed deck or orientation", [tag]))
            continue
        try:
            bounds = equipment_bounds(item, placement, deck)
        except (ValueError, KeyError, TypeError):
            checks.append(_check("placement-" + tag, "placement", "FAIL", "Invalid or nonfinite placement coordinates", [tag]))
            continue
        valid[tag] = (item, placement, deck, bounds)
        checks.append(_check("placement-" + tag, "placement", "PASS", "Exactly one valid equipment placement", [tag]))
        checks.append(_check("boundary-" + tag, "deck_boundary", "PASS" if _inside(bounds, deck) else "FAIL",
                             "Physical box within allocated plot/deck volume", [tag], measured=bounds, unit="m"))
        if "height_limit_m" in deck:
            height = bounds[5] - deck["elevation_m"]
            checks.append(_check("height-" + tag, "height_limit", "PASS" if height <= deck["height_limit_m"] + TOLERANCE_M else "FAIL",
                                 "Equipment height against supplied deck clearance", [tag], measured=height,
                                 limit=deck["height_limit_m"], unit="m"))
        if item.get("fixed") is not None:
            fixed = item["fixed"]
            same = (placement["deck"] == fixed["deck"] and rotation == fixed["rotation_deg"]
                    and abs(placement["x_m"] - fixed["x_m"]) <= TOLERANCE_M
                    and abs(placement["y_m"] - fixed["y_m"]) <= TOLERANCE_M)
            checks.append(_check("fixed-" + tag, "fixed_placement", "PASS" if same else "FAIL", "Preserve locked equipment", [tag]))
        for envelope in item["envelopes"]:
            eb = equipment_bounds(item, placement, deck, envelope)
            checks.append(_check("envelope-boundary-" + envelope["id"], "envelope_boundary",
                                 "PASS" if _inside(eb, deck) else "FAIL", envelope["kind"] + " envelope within allocated plot/deck volume", [tag],
                                 measured=eb, unit="m"))
            for other_tag, other in equipment.items():
                if other_tag == tag or other_tag not in by_tag:
                    continue
                op = by_tag[other_tag]
                od = decks.get(op.get("deck"))
                if od is None:
                    continue
                try:
                    ob = equipment_bounds(other, op, od)
                except (ValueError, TypeError, KeyError):
                    continue
                if boxes_intersect(eb, ob):
                    checks.append(_check("envelope-clash-%s-%s" % (envelope["id"], other_tag), "envelope_clash", "FAIL",
                                         "%s envelope obstructed by physical equipment" % envelope["kind"], [tag, other_tag]))
            for obstacle in study["obstacles"]:
                if boxes_intersect(eb, _obstacle_bounds(obstacle, decks)):
                    checks.append(_check("envelope-obstacle-%s-%s" % (envelope["id"], obstacle["id"]), "envelope_clash", "FAIL",
                                         "%s envelope obstructed by %s" % (envelope["kind"], obstacle["id"]), [tag]))
    tags = sorted(valid)
    for i, tag in enumerate(tags):
        bounds = valid[tag][3]
        for other in tags[i + 1:]:
            if boxes_intersect(bounds, valid[other][3]):
                checks.append(_check("physical-clash-%s-%s" % (tag, other), "physical_clash", "FAIL", "Physical boxes overlap in three dimensions", [tag, other]))
        for obstacle in study["obstacles"]:
            if boxes_intersect(bounds, _obstacle_bounds(obstacle, decks)):
                checks.append(_check("obstacle-clash-%s-%s" % (tag, obstacle["id"]), "obstacle_clash", "FAIL", "Equipment intersects obstacle " + obstacle["id"], [tag]))
        item, placement, deck, _ = valid[tag]
        envelopes = [bounds] + [equipment_bounds(item, placement, deck, envelope) for envelope in item["envelopes"]]
        for corridor in study["corridors"]:
            if corridor["deck"] == deck["id"] and any(boxes_intersect(b, _corridor_box(corridor, deck)) for b in envelopes):
                checks.append(_check("corridor-%s-%s" % (tag, corridor["id"]), "corridor_obstruction", "FAIL",
                                     "Physical or reserved equipment envelope obstructs %s corridor %s" % (corridor.get("kind", "reserved"), corridor["id"]), [tag]))
    checks.extend(_corridor_checks(study, decks))
    if not any(c["kind"] == "physical_clash" for c in checks):
        checks.append(_check("physical-clash-register", "physical_clash", "PASS", "No positive-volume physical equipment clashes among valid placements"))
    if not any(c["kind"] == "obstacle_clash" for c in checks):
        checks.append(_check("obstacle-clash-register", "obstacle_clash", "PASS", "No physical equipment/obstacle clashes among valid placements"))
    if not any(c["kind"] == "envelope_clash" for c in checks):
        checks.append(_check("envelope-clash-register", "envelope_clash", "PASS", "No reserved envelope obstructed by other physical equipment or obstacles"))
    if not any(c["kind"] == "corridor_obstruction" for c in checks):
        checks.append(_check("corridor-register", "corridor_obstruction", "PASS" if study["corridors"] else "NOT_EVALUATED",
                             "Reserved corridor obstruction screening"))
    checks.extend(_escape_checks(study, decks))
    for deck_id, deck in decks.items():
        assigned = [valid[t][0] for t in tags if valid[t][1]["deck"] == deck_id]
        if "mass_limit_kg" not in deck:
            checks.append(_check("mass-" + deck_id, "deck_mass", "NOT_EVALUATED", "No supplied deck mass limit; structural adequacy is unassessed"))
        elif any(e.get("mass_kg") is None for e in assigned):
            checks.append(_check("mass-" + deck_id, "deck_mass", "NOT_EVALUATED", "Equipment mass data are incomplete"))
        else:
            total = sum(e["mass_kg"] for e in assigned)
            checks.append(_check("mass-" + deck_id, "deck_mass", "PASS" if total <= deck["mass_limit_kg"] + TOLERANCE_M else "FAIL",
                                 "Sum of supplied equipment masses only; structural loads and mass cases require specialist verification",
                                 [e["tag"] for e in assigned], measured=total, limit=deck["mass_limit_kg"], unit="kg"))
    for rule in study["rules"]:
        base = {"source": copy.deepcopy(rule.get("source", {})), "required": rule["required"]}
        identifier = "rule-" + rule["id"]
        if not rule_applies(study, rule):
            checks.append(_check(identifier, rule["kind"], "NOT_APPLICABLE", "Rule does not apply to the declared facility type", **base))
        elif rule["kind"] == "required_evidence":
            evidence = rule.get("evidence", [])
            verified = (rule.get("evidence_status") == "verified" and isinstance(evidence, list)
                        and bool(evidence) and all(isinstance(e, str) and e.strip() for e in evidence))
            checks.append(_check(identifier, rule["kind"], "PASS" if verified else "NOT_EVALUATED",
                                 "Externally verified evidence references supplied" if verified else "Required specialist/project evidence has not been verified",
                                 evidence=evidence, **base))
        elif not source_is_verified(rule.get("source")):
            checks.append(_check(identifier, rule["kind"], "NOT_EVALUATED", "Numeric rule lacks a verified source, adopted edition/clause where applicable, or source access", **base))
        else:
            first, second = rule["from_tag"], rule["to_tag"]
            if first not in valid or second not in valid:
                checks.append(_check(identifier, rule["kind"], "NOT_EVALUATED", "Both valid equipment placements are required", [first, second], **base))
            else:
                distance = box_distance(valid[first][3], valid[second][3])
                checks.append(_check(identifier, rule["kind"], "PASS" if distance + TOLERANCE_M >= rule["minimum_m"] else "FAIL",
                                     "Shortest three-dimensional box-surface separation", [first, second],
                                     measured=distance, limit=rule["minimum_m"], unit="m", **base))
    return checks


def _candidate_valid(study, item, placement, placed, equipment, decks):
    """Prune only source-qualified hard constraints and physical invariants."""
    deck = decks[placement["deck"]]
    bounds = equipment_bounds(item, placement, deck)
    envelopes = [equipment_bounds(item, placement, deck, e) for e in item["envelopes"]]
    if not all(_inside(b, deck) for b in [bounds] + envelopes):
        return False
    if "height_limit_m" in deck and bounds[5] > deck["elevation_m"] + deck["height_limit_m"] + TOLERANCE_M:
        return False
    for obstacle in study["obstacles"]:
        if any(boxes_intersect(b, _obstacle_bounds(obstacle, decks)) for b in [bounds] + envelopes):
            return False
    for corridor in study["corridors"]:
        if corridor["deck"] == deck["id"] and any(boxes_intersect(b, _corridor_box(corridor, deck)) for b in [bounds] + envelopes):
            return False
    for other_tag, op in placed.items():
        other = equipment[other_tag]
        od = decks[op["deck"]]
        ob = equipment_bounds(other, op, od)
        if any(boxes_intersect(b, ob) for b in [bounds] + envelopes):
            return False
        if any(boxes_intersect(bounds, equipment_bounds(other, op, od, e)) for e in other["envelopes"]):
            return False
    for rule in study["rules"]:
        if rule["kind"] != "minimum_separation" or not rule["required"] or not rule_applies(study, rule) or not source_is_verified(rule.get("source")):
            continue
        if item["tag"] not in (rule["from_tag"], rule["to_tag"]):
            continue
        other_tag = rule["to_tag"] if item["tag"] == rule["from_tag"] else rule["from_tag"]
        if other_tag in placed:
            op = placed[other_tag]
            if box_distance(bounds, equipment_bounds(equipment[other_tag], op, decks[op["deck"]])) + TOLERANCE_M < rule["minimum_m"]:
                return False
    if "mass_limit_kg" in deck and item.get("mass_kg") is not None:
        total = item["mass_kg"] + sum(equipment[t].get("mass_kg") or 0 for t, p in placed.items() if p["deck"] == deck["id"])
        if total > deck["mass_limit_kg"] + TOLERANCE_M:
            return False
    return True


def _candidates(study, item, decks, variant):
    """Yield finite grid positions with distinct reproducible scan strategies."""
    if item.get("fixed") is not None:
        yield dict(item["fixed"], tag=item["tag"])
        return
    grid = study["options"]["grid_m"]
    allowed = list(item["allowed_decks"])
    rotations = list(item["rotations_deg"])
    if variant == 2:
        allowed.reverse()
        rotations.reverse()
    for deck_id in allowed:
        deck = decks[deck_id]
        for rotation in rotations:
            length, width = item["size_m"][:2]
            if rotation in (90, 270):
                length, width = width, length
            nx = math.floor((deck["width_m"] - length + TOLERANCE_M) / grid)
            ny = math.floor((deck["depth_m"] - width + TOLERANCE_M) / grid)
            if nx < 0 or ny < 0:
                continue
            xs, ys = range(nx + 1), range(ny + 1)
            if variant == 1:
                xs = range(nx, -1, -1)
            elif variant == 2:
                ys = range(ny, -1, -1)
            outer, inner = (xs, ys) if variant == 1 else (ys, xs)
            for a in outer:
                for b in inner:
                    x, y = (a, b) if variant == 1 else (b, a)
                    yield {"tag": item["tag"], "deck": deck_id, "x_m": round(x * grid, 10),
                           "y_m": round(y * grid, 10), "rotation_deg": rotation}


def _metrics(study, placements, decks):
    """Calculate transparent placement proxies, without routing/cost claims."""
    equipment = {e["tag"]: e for e in study["equipment"]}
    by_tag = {p["tag"]: p for p in placements}
    centres = {}
    for tag, placement in by_tag.items():
        bounds = equipment_bounds(equipment[tag], placement, decks[placement["deck"]])
        centres[tag] = [(bounds[i] + bounds[i + 3]) / 2 for i in range(3)]
    proxy = sum(sum(abs(centres[c["from_tag"]][i] - centres[c["to_tag"]][i]) for i in range(3))
                for c in study["connections"] if c["from_tag"] in centres and c["to_tag"] in centres)
    known_mass = sum(equipment[tag].get("mass_kg") or 0 for tag in by_tag)
    return {"connection_manhattan_proxy_m": round(proxy, 6), "piping_proxy_m": round(proxy, 6),
            "occupied_area_m2": round(sum(equipment[t]["size_m"][0] * equipment[t]["size_m"][1] for t in by_tag), 6),
            "total_mass_kg": known_mass, "mass_complete": all(equipment[t].get("mass_kg") is not None for t in by_tag),
            "placed_equipment": len(by_tag), "required_equipment": len(equipment)}


def generate_layouts(study):
    """Generate up to three distinct candidates with bounded backtracking.

    A stopped search reports SEARCH_LIMIT or NO_SOLUTION_FOUND.  Neither means
    the engineering problem has been proven infeasible.  Solver timing is omitted
    from the candidate fingerprint so equal input geometry regenerates identically.
    """
    study = normalize_study(study)
    backend = study["options"]["backend"]
    if backend == "ortools":
        raise ValueError("The ortools backend is not implemented in this screening release; use deterministic")
    decks = {d["id"]: d for d in study["decks"]}
    equipment = {e["tag"]: e for e in study["equipment"]}
    alternatives = []
    seen = set()
    for variant in range(study["options"]["alternatives"]):
        start = time.perf_counter()
        order = sorted(equipment, key=lambda t: (equipment[t].get("fixed") is None,
                                                len(equipment[t]["allowed_decks"]),
                                                -equipment[t]["size_m"][0] * equipment[t]["size_m"][1], t))
        if variant == 1:
            fixed = [t for t in order if equipment[t].get("fixed") is not None]
            order = fixed + list(reversed([t for t in order if t not in fixed]))
        placed = {}
        best = {}
        counter = 0
        stopped = False

        def search(index):
            nonlocal best, counter, stopped
            if index == len(order):
                return True
            tag = order[index]
            for candidate in _candidates(study, equipment[tag], decks, variant):
                if counter >= study["options"]["max_candidates"]:
                    stopped = True
                    return False
                counter += 1
                if not _candidate_valid(study, equipment[tag], candidate, placed, equipment, decks):
                    continue
                placed[tag] = candidate
                if len(placed) > len(best):
                    best = copy.deepcopy(placed)
                if search(index + 1):
                    return True
                del placed[tag]
                if stopped:
                    return False
            return False

        complete = search(0)
        selected = placed if complete else best
        placements = [selected[tag] for tag in sorted(selected)]
        fingerprint = study_fingerprint({"study": study_fingerprint(study), "placements": placements})
        if fingerprint in seen:
            continue
        seen.add(fingerprint)
        alternative = {"id": "ALT-%02d" % (variant + 1), "label": ("South-west scan", "East-side scan", "North-side scan")[variant],
                       "placements": placements, "metrics": _metrics(study, placements, decks),
                       "solver": {"backend": "deterministic", "status": "FEASIBLE" if complete else "SEARCH_LIMIT" if stopped else "NO_SOLUTION_FOUND",
                                  "seed": 0, "settings": {"grid_m": study["options"]["grid_m"], "max_candidates": study["options"]["max_candidates"], "scan_variant": variant},
                                  "candidates_evaluated": counter, "elapsed_s": round(time.perf_counter() - start, 6),
                                  "optimality_proven": False, "infeasibility_proven": False},
                       "fingerprint": fingerprint}
        alternative["checks"] = check_layout(study, alternative)
        alternative["status"] = ("INFEASIBLE" if any(c["required"] and c["status"] == "FAIL" for c in alternative["checks"])
                                 else "INCOMPLETE" if any(c["required"] and c["status"] == "NOT_EVALUATED" for c in alternative["checks"])
                                 else "FEASIBLE")
        alternatives.append(alternative)
    return alternatives
