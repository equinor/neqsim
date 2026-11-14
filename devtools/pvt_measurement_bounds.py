"""Outward-rounded closed decimal bounds for laboratory PVT measurements."""

from decimal import Context, Decimal, InvalidOperation, ROUND_CEILING, ROUND_FLOOR

from pvt_data import PVTStudyError


UP = Context(prec=34, rounding=ROUND_CEILING)
DOWN = Context(prec=34, rounding=ROUND_FLOOR)
GAS_CONSTANT = Decimal("8.31446261815324")


def _required_text(record, names):
    """Require nonblank provenance entries."""
    for name in names:
        if not isinstance(record.get(name), str) or not record[name].strip():
            raise PVTStudyError("Laboratory evidence requires " + name)


def _decimal(value):
    """Read a finite decimal without introducing a binary floating-point conversion."""
    if not isinstance(value, str):
        raise PVTStudyError("Laboratory interval endpoints must be decimal strings")
    try:
        result = Decimal(value)
    except InvalidOperation as error:
        raise PVTStudyError("Invalid laboratory interval endpoint") from error
    if not result.is_finite() or result.adjusted() < -150 or result.adjusted() > 150:
        raise PVTStudyError("Laboratory interval endpoint must be finite with a supported decimal exponent")
    return result


def _interval(item, unit, allow_zero=False):
    """Read an optional, explicitly unit-bearing closed interval."""
    if item is None:
        return None
    if not isinstance(item, dict) or item.get("unit") != unit:
        raise PVTStudyError("Laboratory interval requires unit " + unit)
    lower, upper = _decimal(item.get("lower")), _decimal(item.get("upper"))
    if lower > upper or lower < 0 or (lower == 0 and not allow_zero):
        raise PVTStudyError("Invalid laboratory interval order or sign for " + unit)
    return lower, upper


def _subtract(left, right):
    """Enclose the signed difference of two intervals."""
    return DOWN.subtract(left[0], right[1]), UP.subtract(left[1], right[0])


def _product(left, right):
    """Enclose the product of nonnegative quantities."""
    return DOWN.multiply(left[0], right[0]), UP.multiply(left[1], right[1])


def _divide(top, bottom):
    """Enclose a nonnegative numerator divided by a positive interval."""
    return DOWN.divide(top[0], bottom[1]), UP.divide(top[1], bottom[0])


def _serialize(interval):
    """Return JSON-safe decimal interval endpoints."""
    return [str(endpoint) for endpoint in interval]
