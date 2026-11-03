"""Canonical, SI-unit input contract for physical plant-layout screening.

The model uses orthogonal equipment boxes on rectangular plots/decks.  These
coordinates are physical metres and must never be populated from PFD glyphs.
No standard-derived spacing defaults are supplied by this module.
"""

from __future__ import annotations

import copy
import hashlib
import json
import math


SCHEMA_VERSION = "plant_layout_study.v1"
FACILITY_TYPES = {"onshore", "offshore_fixed", "offshore_floating"}
TOLERANCE_M = 1.0e-8


def _number(value, name, positive=False, nonnegative=False):
    """Return a finite real number, rejecting Booleans and invalid ranges."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("%s must be a finite number" % name)
    result = float(value)
    if not math.isfinite(result):
        raise ValueError("%s must be finite" % name)
    if positive and result <= 0:
        raise ValueError("%s must be positive" % name)
    if nonnegative and result < 0:
        raise ValueError("%s must be nonnegative" % name)
    return result


def _identifier(value, name):
    """Validate a nonempty human-readable stable identifier."""
    if not isinstance(value, str) or not value.strip():
        raise ValueError("%s must be a nonempty string" % name)
    return value


def _list(value, name):
    """Require a list rather than silently iterating strings or mappings."""
    if not isinstance(value, list):
        raise ValueError("%s must be a list" % name)
    return value


def _bounds(value, name, dimensions=3):
    """Validate finite lower/upper Cartesian bounds with positive extents."""
    _list(value, name)
    if len(value) != dimensions * 2:
        raise ValueError("%s needs %d coordinates" % (name, dimensions * 2))
    result = [_number(v, "%s[%d]" % (name, i)) for i, v in enumerate(value)]
    if any(result[i] >= result[i + dimensions] for i in range(dimensions)):
        raise ValueError("%s must have positive extents" % name)
    return result


def _unique(items, field, name):
    """Validate identity uniqueness and mapping item types."""
    seen = set()
    for item in items:
        if not isinstance(item, dict):
            raise ValueError("%s entries must be objects" % name)
        identifier = _identifier(item.get(field), "%s.%s" % (name, field))
        if identifier in seen:
            raise ValueError("Duplicate %s identifier: %s" % (name, identifier))
        seen.add(identifier)
    return seen


def normalize_study(study):
    """Validate and deep-copy a versioned layout study with explicit SI units.

    Missing physical dimensions are rejected instead of fabricated.  An importer
    may retain such objects in its data-gap register until dimensions are supplied.
    Optional vendor, process and standards metadata are preserved verbatim.
    """
    if not isinstance(study, dict):
        raise ValueError("Study must be an object")
    result = copy.deepcopy(study)
    if result.get("schema_version") != SCHEMA_VERSION:
        raise ValueError("schema_version must be %s" % SCHEMA_VERSION)
    project = result.get("project")
    if not isinstance(project, dict):
        raise ValueError("project must be an object")
    _identifier(project.get("id"), "project.id")
    _identifier(project.get("title"), "project.title")
    if project.get("facility_type") not in FACILITY_TYPES:
        raise ValueError("Unsupported project.facility_type")
    project.setdefault("revision", "A")
    project.setdefault("maturity", "SCREENING")
    project.setdefault("jurisdiction", "unspecified")
    if project["maturity"] != "SCREENING":
        raise ValueError("This layout implementation supports SCREENING maturity only")
    coordinates = result.get("coordinate_system")
    if not isinstance(coordinates, dict):
        raise ValueError("coordinate_system with explicit units is required")
    if coordinates.get("length_unit") != "m" or coordinates.get("mass_unit") != "kg":
        raise ValueError("Canonical layout units must be m and kg")
    coordinates.setdefault("origin", [0.0, 0.0, 0.0])
    if not isinstance(coordinates["origin"], list) or len(coordinates["origin"]) != 3:
        raise ValueError("coordinate_system.origin needs three coordinates")
    coordinates["origin"] = [_number(v, "coordinate_system.origin") for v in coordinates["origin"]]
    coordinates["north_deg"] = _number(coordinates.get("north_deg", 0), "north_deg")
    coordinates.setdefault("elevation_datum", "project")

    decks = _list(result.get("decks"), "decks")
    if not decks:
        raise ValueError("At least one rectangular plot/deck is required")
    deck_ids = _unique(decks, "id", "decks")
    for deck in decks:
        for field in ("width_m", "depth_m"):
            deck[field] = _number(deck.get(field), "deck." + field, positive=True)
        deck["elevation_m"] = _number(deck.get("elevation_m"), "deck.elevation_m")
        if "height_limit_m" in deck:
            deck["height_limit_m"] = _number(deck["height_limit_m"], "deck.height_limit_m", positive=True)
        if "mass_limit_kg" in deck:
            deck["mass_limit_kg"] = _number(deck["mass_limit_kg"], "deck.mass_limit_kg", nonnegative=True)

    equipment = _list(result.get("equipment"), "equipment")
    if not equipment:
        raise ValueError("At least one dimensioned equipment item is required")
    tags = _unique(equipment, "tag", "equipment")
    envelope_ids = set()
    for item in equipment:
        item.setdefault("kind", "package")
        size = _list(item.get("size_m"), "%s.size_m" % item["tag"])
        if len(size) != 3:
            raise ValueError("%s.size_m needs length, width and height" % item["tag"])
        item["size_m"] = [_number(v, "%s.size_m" % item["tag"], positive=True) for v in size]
        source = item.get("dimension_source")
        if not isinstance(source, dict) or source.get("kind") not in {
            "synthetic_assumption", "vendor", "calculated", "measured", "project_assumption"
        }:
            raise ValueError("%s needs an explicit dimension_source kind" % item["tag"])
        _identifier(source.get("reference"), "%s.dimension_source.reference" % item["tag"])
        allowed = _list(item.get("allowed_decks"), "%s.allowed_decks" % item["tag"])
        if not allowed or len(set(allowed)) != len(allowed) or not set(allowed) <= deck_ids:
            raise ValueError("%s.allowed_decks must reference distinct known decks" % item["tag"])
        rotations = _list(item.get("rotations_deg", [0, 90]), "%s.rotations_deg" % item["tag"])
        if not rotations or any(isinstance(v, bool) or v not in (0, 90, 180, 270) for v in rotations):
            raise ValueError("%s supports only orthogonal rotations" % item["tag"])
        item["rotations_deg"] = list(dict.fromkeys(rotations))
        if item.get("mass_kg") is not None:
            item["mass_kg"] = _number(item["mass_kg"], "%s.mass_kg" % item["tag"], nonnegative=True)
        if "mass_cases_kg" in item:
            if not isinstance(item["mass_cases_kg"], dict):
                raise ValueError("mass_cases_kg must be an object")
            item["mass_cases_kg"] = {
                k: _number(v, "%s.mass_cases_kg.%s" % (item["tag"], k), nonnegative=True)
                for k, v in item["mass_cases_kg"].items()
            }
        item.setdefault("envelopes", [])
        for index, envelope in enumerate(_list(item["envelopes"], "equipment.envelopes")):
            if not isinstance(envelope, dict):
                raise ValueError("Equipment envelopes must be objects")
            _identifier(envelope.get("kind"), "envelope.kind")
            envelope["bounds_m"] = _bounds(envelope.get("bounds_m"), "envelope.bounds_m")
            envelope.setdefault("id", "%s-%s-%d" % (item["tag"], envelope["kind"], index + 1))
            _identifier(envelope["id"], "envelope.id")
            if envelope["id"] in envelope_ids:
                raise ValueError("Duplicate equipment envelope identifier: %s" % envelope["id"])
            envelope_ids.add(envelope["id"])
        fixed = item.get("fixed")
        if fixed is not None:
            if not isinstance(fixed, dict) or fixed.get("deck") not in allowed:
                raise ValueError("%s.fixed must reference an allowed deck" % item["tag"])
            for field in ("x_m", "y_m"):
                fixed[field] = _number(fixed.get(field), "fixed." + field)
            fixed.setdefault("rotation_deg", 0)
            if fixed["rotation_deg"] not in item["rotations_deg"]:
                raise ValueError("%s.fixed rotation is not allowed" % item["tag"])

    for collection in ("connections", "corridors", "exits", "obstacles", "rules", "standards"):
        result.setdefault(collection, [])
        _list(result[collection], collection)
    _unique(result["connections"], "id", "connections")
    for connection in result["connections"]:
        if connection.get("from_tag") not in tags or connection.get("to_tag") not in tags:
            raise ValueError("Connection %s references unknown equipment" % connection["id"])
    from layout_inventory import normalize_inventory_fields
    normalize_inventory_fields(
        result,
        {connection["id"] for connection in result["connections"]},
        tags,
    )
    for collection in ("corridors", "obstacles", "exits"):
        _unique(result[collection], "id", collection)
        for entry in result[collection]:
            if entry.get("deck") not in deck_ids:
                raise ValueError("%s %s references unknown deck" % (collection, entry["id"]))
            if collection == "exits":
                entry["x_m"] = _number(entry.get("x_m"), "exit.x_m")
                entry["y_m"] = _number(entry.get("y_m"), "exit.y_m")
            else:
                entry["bounds_m"] = _bounds(entry.get("bounds_m"), collection + ".bounds_m", 2 if collection == "corridors" else 3)
    _unique(result["rules"], "id", "rules")
    for rule in result["rules"]:
        if rule.get("kind") not in {"minimum_separation", "required_evidence"}:
            raise ValueError("Unsupported rule kind: %s" % rule.get("kind"))
        rule.setdefault("required", True)
        if not isinstance(rule["required"], bool):
            raise ValueError("rule.required must be Boolean")
        if not isinstance(rule.get("source", {}), dict):
            raise ValueError("rule.source must be an object")
        if rule["kind"] == "minimum_separation":
            if rule.get("from_tag") not in tags or rule.get("to_tag") not in tags:
                raise ValueError("Rule %s references unknown equipment" % rule["id"])
            if rule["from_tag"] == rule["to_tag"]:
                raise ValueError("Separation rule needs two distinct equipment tags")
            rule["minimum_m"] = _number(rule.get("minimum_m"), "rule.minimum_m", nonnegative=True)
        applicability = rule.get("applicability", {})
        if not isinstance(applicability, dict):
            raise ValueError("rule.applicability must be an object")
        facility_types = applicability.get("facility_types", [])
        if not isinstance(facility_types, list) or not set(facility_types) <= FACILITY_TYPES:
            raise ValueError("rule applicability has unsupported facility types")
    for field in ("assumptions", "data_gaps"):
        result.setdefault(field, [])
        if any(not isinstance(v, str) for v in _list(result[field], field)):
            raise ValueError("%s entries must be strings" % field)
    options = result.setdefault("options", {})
    if not isinstance(options, dict):
        raise ValueError("options must be an object")
    options["grid_m"] = _number(options.get("grid_m", 1), "options.grid_m", positive=True)
    for field, default, maximum in (("alternatives", 3, 3), ("max_candidates", 100000, 10000000)):
        value = options.get(field, default)
        if isinstance(value, bool) or not isinstance(value, int) or not 1 <= value <= maximum:
            raise ValueError("options.%s must be an integer in 1..%d" % (field, maximum))
        options[field] = value
    options.setdefault("backend", "deterministic")
    if options["backend"] not in {"deterministic", "auto", "ortools"}:
        raise ValueError("Unsupported placement backend")
    try:
        json.dumps(result, allow_nan=False)
    except (ValueError, TypeError) as exc:
        raise ValueError("Study must contain finite JSON-serializable values") from exc
    return result


def study_fingerprint(study):
    """Return the content hash of the supplied canonical JSON-compatible model."""
    encoded = json.dumps(study, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def equipment_bounds(equipment, placement, deck, envelope=None):
    """Transform a physical or local envelope box to the plant coordinate frame.

    ``placement.x_m/y_m`` is the lower-left corner of the *rotated physical*
    footprint.  Envelope coordinates remain attached to the local equipment
    origin.  The deck supplies the base elevation; the project CRS origin is
    metadata and is applied only by an explicit external frame transform.
    """
    length, width, height = equipment["size_m"]
    local = envelope["bounds_m"] if envelope is not None else [0, 0, 0, length, width, height]
    rotation = placement.get("rotation_deg", 0)
    if rotation not in (0, 90, 180, 270):
        raise ValueError("Only orthogonal equipment rotations are supported")

    def rotate(x, y):
        if rotation == 90:
            return width - y, x
        if rotation == 180:
            return length - x, width - y
        if rotation == 270:
            return y, length - x
        return x, y

    corners = [rotate(x, y) for x in (local[0], local[3]) for y in (local[1], local[4])]
    x0 = _number(placement.get("x_m"), "placement.x_m")
    y0 = _number(placement.get("y_m"), "placement.y_m")
    z0 = deck["elevation_m"]
    return [min(p[0] for p in corners) + x0, min(p[1] for p in corners) + y0, local[2] + z0,
            max(p[0] for p in corners) + x0, max(p[1] for p in corners) + y0, local[5] + z0]


def boxes_intersect(first, second, tolerance=TOLERANCE_M):
    """Return true only for positive-volume box overlap, not boundary contact."""
    return all(min(first[i + 3], second[i + 3]) - max(first[i], second[i]) > tolerance for i in range(3))


def box_distance(first, second):
    """Return the exact shortest Euclidean distance between axis-aligned boxes."""
    return math.sqrt(sum(max(first[i] - second[i + 3], second[i] - first[i + 3], 0.0) ** 2 for i in range(3)))


def source_is_verified(source):
    """Assess whether a numeric rule has the minimum controlled-source fields.

    This checks evidence completeness, not authenticity or engineering approval.
    Standard clauses must be reviewed externally before their fields are supplied.
    """
    if not isinstance(source, dict) or source.get("access_status") != "verified_full_text":
        return False
    if not isinstance(source.get("reference"), str) or not source["reference"].strip():
        return False
    if source.get("kind") == "standard":
        return all(isinstance(source.get(key), str) and source[key].strip() for key in ("edition", "clause"))
    return source.get("kind") == "project_requirement"


def rule_applies(study, rule):
    """Return whether a rule applies to the declared facility type."""
    scope = rule.get("applicability", {}).get("facility_types", [])
    return not scope or study["project"]["facility_type"] in scope
