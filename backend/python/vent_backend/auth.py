import hashlib
import hmac
import secrets
import time
from functools import wraps

import jwt
from flask import Blueprint, abort, current_app, g, request
from sqlalchemy import select

from .db import Credential, Membership, User, db
from .validation import body, email, text

auth = Blueprint("auth", __name__)


def digest(value):
    return hmac.new(current_app.config["SECRET_KEY"].encode(), value.encode(), hashlib.sha256).hexdigest()


def signed(payload, purpose, lifetime):
    now = int(time.time())
    return jwt.encode(dict(payload, aud=purpose, iss="vent-python", iat=now, exp=now + lifetime), current_app.config["SECRET_KEY"], algorithm="HS256")


def decode(token, purpose):
    try:
        return jwt.decode(token, current_app.config["SECRET_KEY"], algorithms=["HS256"], audience=purpose, issuer="vent-python", options={"require": ["exp", "iat", "aud", "iss"]})
    except jwt.InvalidTokenError:
        abort(401, description="Invalid or expired token")


def protected(function):
    @wraps(function)
    def wrapped(*args, **kwargs):
        token = request.headers.get("Authorization", "")
        if not token.startswith("Bearer "):
            abort(401)
        claims = decode(token[7:], "access")
        g.user = db().get(User, claims.get("sub"))
        credential = db().get(Credential, claims.get("sid"))
        if not g.user or not credential or credential.expires <= time.time():
            abort(401)
        return function(*args, **kwargs)
    return wrapped


def tokens(user):
    refresh = secrets.token_urlsafe(48)
    sid = "refresh:" + digest(refresh)
    db().add(Credential(key=sid, user_id=user.id, expires=time.time() + 30 * 86400, payload={}))
    db().flush()
    return dict(accessToken=signed({"sub": user.id, "sid": sid}, "access", 900), refreshToken=refresh, expiresInSeconds=900)


def send_code(address, code):
    sender = current_app.config.get("EMAIL_SENDER")
    if sender:
        sender(address, code)
        return
    from .email_service import deliver
    deliver(address, code)


def locked(key):
    return db().scalar(select(Credential).where(Credential.key == key).with_for_update())


@auth.post("/auth/otp/request")
def request_otp():
    address = email(body(["email"])["email"])
    now = time.time()
    # Database-backed limits work across WSGI workers. Do not trust forwarded IP headers.
    key = "rate:" + digest(request.remote_addr or "unknown")
    rate = locked(key)
    if rate and rate.expires > now and rate.attempts >= 10:
        abort(429)
    if not rate:
        rate = Credential(key=key, expires=now + 60, attempts=0, payload={})
        db().add(rate)
    if rate.expires <= now:
        rate.expires, rate.attempts = now + 60, 0
    rate.attempts += 1
    otp_key = "otp:" + digest(address)
    otp = locked(otp_key)
    if otp and otp.payload["sent"] + 30 > now:
        db().commit()
        abort(429)
    code = f"{secrets.randbelow(1000000):06}"
    if not otp:
        otp = Credential(key=otp_key, expires=now + 600)
        db().add(otp)
    otp.expires, otp.attempts = now + 600, 0
    otp.payload = {"hash": digest(address + ":" + code), "sent": now}
    send_code(address, code)
    return {"expiresInSeconds": 600, "resendAfterSeconds": 30}, 202


@auth.post("/auth/otp/verify")
def verify_otp():
    data = body(["email", "code"])
    address, code = email(data["email"]), text(data["code"], 6, 6)
    otp = locked("otp:" + digest(address))
    if not otp or otp.expires <= time.time():
        abort(401, description="Invalid or expired code")
    if otp.attempts >= 5:
        abort(429)
    otp.attempts += 1
    if not hmac.compare_digest(otp.payload["hash"], digest(address + ":" + code)):
        db().commit()  # Failed attempts must survive the error response.
        abort(401, description="Invalid or expired code")
    db().delete(otp)
    user = db().scalar(select(User).where(User.email == address))
    if not user:
        user = User(email=address, name=address.split("@")[0])
        db().add(user)
        db().flush()
    return tokens(user)


@auth.post("/auth/refresh")
def refresh():
    raw = text(body(["refreshToken"])["refreshToken"], 128, 43)
    credential = locked("refresh:" + digest(raw))
    if not credential or credential.expires <= time.time():
        abort(401)
    user = db().get(User, credential.user_id)
    db().delete(credential)
    return tokens(user)


@auth.post("/auth/logout")
@protected
def logout():
    claims = decode(request.headers["Authorization"][7:], "access")
    db().delete(db().get(Credential, claims["sid"]))
    return "", 204


@auth.get("/me")
@protected
def me():
    roles = db().scalars(select(Membership).where(Membership.user_id == g.user.id)).all()
    organizer = g.user.email in current_app.config["ORGANIZER_EMAILS"] or any(r.role == "ORGANIZER" for r in roles)
    return {"id": g.user.id, "role": "ORGANIZER" if organizer else "ATTENDEE"}


@auth.route("/me/preferences", methods=["GET", "PUT"])
@protected
def preferences():
    if request.method == "GET":
        return g.user.preferences or {"bookmarks": [], "preferences": []}
    data = body([], ["bookmarks", "preferences"])
    result = {}
    for key in ("bookmarks", "preferences"):
        values = data.get(key, [])
        if not isinstance(values, list) or len(values) > 200:
            abort(400)
        result[key] = sorted(set(text(v, 200) for v in values))
    g.user.preferences = result
    return "", 204
