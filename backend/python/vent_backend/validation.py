import math
import re
from datetime import datetime, timezone

from flask import abort, request


def body(required=(), optional=()):
    data = request.get_json()
    if not isinstance(data, dict) or set(data) - set(required) - set(optional) or set(required) - set(data):
        abort(400, description="Invalid request fields")
    return data


def text(value, maximum=160, minimum=1):
    if not isinstance(value, str) or not minimum <= len(value.strip()) <= maximum:
        abort(400, description="Invalid text")
    return value.strip()


def number(value, minimum=0, maximum=10000, integer=False):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not minimum <= value <= maximum or (integer and not isinstance(value, int)):
        abort(400, description="Invalid number")
    return value


def timestamp(value):
    try:
        result = datetime.fromisoformat(text(value, 40).replace("Z", "+00:00"))
        if result.tzinfo is None:
            raise ValueError()
        return result.astimezone(timezone.utc)
    except ValueError:
        abort(400, description="An ISO-8601 timestamp with timezone is required")


def email(value):
    value = text(value, 254).lower()
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", value):
        abort(400, description="Invalid email")
    return value
