import re
import time
from datetime import datetime, timezone

from flask import Blueprint, abort, current_app, g, request
from sqlalchemy import select

from .auth import decode, protected, signed
from .db import Answer, Award, Booking, Credential, Event, Hall, Membership, Poll, Quest, Session, User, db, uid
from .validation import body, email, number, text, timestamp

api = Blueprint("events", __name__)


def get(model, identifier, lock=False):
    query = select(model).where(model.id == identifier)
    row = db().scalar(query.with_for_update() if lock else query)
    if row is None:
        abort(404)
    return row


def access(event_id, roles=None):
    member = db().get(Membership, (event_id, g.user.id))
    if not member:
        abort(404)
    if roles and member.role not in roles:
        abort(403)
    return member


def session(identifier, roles=None, lock=False):
    row = get(Session, identifier, lock)
    access(row.event_id, roles)
    return row


def color(value):
    value = text(value, 8, 8)
    if not re.fullmatch("[0-9a-fA-F]{8}", value):
        abort(400, description="seedArgb must be eight hexadecimal digits")
    return value.upper()


def schedule(data, checkin=False):
    start, end = timestamp(data["startsAt"]), timestamp(data["endsAt"])
    if end <= start:
        abort(400, description="endsAt must follow startsAt")
    if checkin and timestamp(data["checkInClosesAt"]) <= timestamp(data["checkInOpensAt"]):
        abort(400, description="Invalid check-in window")
    return dict(data, title=text(data["title"]))


def event_dto(event):
    sessions = db().scalars(select(Session).where(Session.event_id == event.id)).all()
    return dict(event.payload, id=event.id, sessions=[s.payload for s in sessions])


@api.get("/events")
@protected
def events():
    rows = db().scalars(select(Event).join(Membership).where(Membership.user_id == g.user.id)).all()
    return [event_dto(e) for e in rows]


@api.get("/events/<identifier>")
@protected
def event_detail(identifier):
    access(identifier)
    return event_dto(get(Event, identifier))


@api.post("/events")
@protected
def create_event():
    if g.user.email not in current_app.config["ORGANIZER_EMAILS"] and not db().scalar(select(Membership).where(Membership.user_id == g.user.id, Membership.role == "ORGANIZER")):
        abort(403)
    data = schedule(body(["title", "startsAt", "endsAt", "seedArgb"], ["coverImageUrl"]))
    data["seedArgb"] = color(data["seedArgb"])
    # Cover uploads belong to a separate storage service; never fetch supplied URLs.
    if data.get("coverImageUrl") is not None:
        from urllib.parse import urlsplit
        cover = urlsplit(text(data.pop("coverImageUrl"), 2048))
        if cover.scheme != "https" or not cover.hostname or cover.username:
            abort(400)
    else:
        data.pop("coverImageUrl", None)
    event = Event(id=uid(), payload=data)
    db().add(event)
    db().flush()
    db().add(Membership(event_id=event.id, user_id=g.user.id, role="ORGANIZER"))
    return event_dto(event), 201


@api.put("/events/<identifier>/seed")
@protected
def update_seed(identifier):
    access(identifier, ["ORGANIZER"])
    event = get(Event, identifier, True)
    event.payload = dict(event.payload, seedArgb=color(body(["seedArgb"])["seedArgb"]))
    return "", 204


@api.post("/events/<identifier>/attendees/import")
@protected
def import_attendees(identifier):
    access(identifier, ["ORGANIZER"])
    data = body(["attendees"])
    if not isinstance(data["attendees"], list) or not 1 <= len(data["attendees"]) <= 500:
        abort(400)
    result = []
    for item in data["attendees"]:
        if not isinstance(item, dict) or set(item) != {"name", "email"}:
            abort(400)
        address, name = email(item["email"]), text(item["name"], 120)
        user = db().scalar(select(User).where(User.email == address))
        if not user:
            user = User(id=uid(), email=address, name=name)
            db().add(user)
            db().flush()
        if not db().get(Membership, (identifier, user.id)):
            db().add(Membership(event_id=identifier, user_id=user.id, role="ATTENDEE"))
        result.append({"id": user.id, "name": user.name, "email": user.email})
    return result, 201


@api.get("/events/<identifier>/attendees")
@protected
def attendees(identifier):
    access(identifier, ["ORGANIZER", "STAFF"])
    users = db().scalars(select(User).join(Membership).where(Membership.event_id == identifier)).all()
    return [{"id": u.id, "name": u.name, "email": u.email} for u in users]


def layout(data):
    number(data["width"], 0.1, 10000)
    number(data["height"], 0.1, 10000)
    if not isinstance(data["seats"], list) or len(data["seats"]) > 500 or not isinstance(data["elements"], list) or len(data["elements"]) > 200:
        abort(400)
    seen, labels = set(), set()
    for seat in data["seats"]:
        if not isinstance(seat, dict) or set(seat) - {"id", "label", "x", "y", "rotation", "type", "state", "isMine"} or not {"id", "label", "x", "y", "rotation", "type", "state"} <= seat.keys():
            abort(400)
        sid, label = text(seat["id"], 100), text(seat["label"], 40)
        if sid in seen or label in labels or seat["type"] not in ("STANDARD", "ACCESSIBLE", "VIP", "BLOCKED") or seat["state"] not in ("FREE", "BLOCKED"):
            abort(400)
        seen.add(sid)
        labels.add(label)
        number(seat["x"], 0, data["width"])
        number(seat["y"], 0, data["height"])
        number(seat["rotation"], -360, 360)
        seat["isMine"] = False
    for element in data["elements"]:
        if not isinstance(element, dict) or set(element) != {"kind", "label", "x", "y", "width", "height"}:
            abort(400)
        text(element["kind"], 30)
        text(element["label"], 120)
        for key in ("x", "y", "width", "height"):
            number(element[key])
    return data


@api.post("/events/<identifier>/halls")
@protected
def create_hall(identifier):
    access(identifier, ["ORGANIZER"])
    data = body(["name", "width", "height", "seats", "elements"])
    data["name"] = text(data["name"], 120)
    hall = Hall(id=uid(), event_id=identifier, payload=layout(data))
    db().add(hall)
    return dict(hall.payload, id=hall.id), 201


@api.get("/halls/<identifier>")
@protected
def hall_detail(identifier):
    hall = get(Hall, identifier)
    access(hall.event_id)
    return dict(hall.payload, id=hall.id)


@api.post("/halls/<identifier>/layout")
@protected
def save_layout(identifier):
    hall = get(Hall, identifier, True)
    access(hall.event_id, ["ORGANIZER"])
    sessions = db().scalars(select(Session).where(Session.hall_id == identifier).with_for_update()).all()
    for s in sessions:
        if db().scalar(select(Booking).where(Booking.session_id == s.id)):
            abort(409, description="Unassign bookings before changing the hall layout")
        s.version += 1
    hall.payload = dict(layout(body(["width", "height", "seats", "elements"])), name=hall.payload["name"])
    return "", 204


@api.post("/sessions")
@protected
def create_session():
    data = schedule(body(["eventId", "hallId", "title", "startsAt", "endsAt", "checkInOpensAt", "checkInClosesAt"]), True)
    access(data["eventId"], ["ORGANIZER"])
    hall = get(Hall, data["hallId"], True)
    if hall.event_id != data["eventId"]:
        abort(400)
    row = Session(id=uid(), event_id=hall.event_id, hall_id=hall.id, payload=dict(data, id=uid()))
    row.payload["id"] = row.id
    db().add(row)
    return row.payload, 201


def seats(row):
    bookings = db().scalars(select(Booking).where(Booking.session_id == row.id)).all()
    occupied = {b.seat_id: b for b in bookings}
    result = []
    for seat in row.hall.payload["seats"]:
        booking = occupied.get(seat["id"])
        result.append(dict(seat, state=("CHECKED_IN" if booking.checked_in else "ASSIGNED") if booking else seat["state"], isMine=bool(booking and booking.user_id == g.user.id)))
    return result


@api.get("/sessions/<identifier>/seats")
@protected
def seat_snapshot(identifier):
    row = session(identifier)
    since = request.args.get("since")
    if since is not None and (not since.isdigit() or len(since) > 18):
        abort(400)
    return {"version": row.version, "full": True, "seats": [] if since == str(row.version) else seats(row)}


@api.post("/sessions/<identifier>/assignments/preview")
@protected
def preview(identifier):
    row = session(identifier, ["ORGANIZER"], True)
    ids = body(["attendeeIds"])["attendeeIds"]
    if not isinstance(ids, list) or not 1 <= len(ids) <= 500 or any(not isinstance(v, str) for v in ids) or len(set(ids)) != len(ids):
        abort(400)
    for user_id in ids:
        if not db().get(Membership, (row.event_id, user_id)):
            abort(400, description="Attendees must belong to the event")
    existing = db().scalars(select(Booking).where(Booking.session_id == identifier)).all()
    assigned = {b.user_id for b in existing}
    pending = [i for i in ids if i not in assigned]
    free = [s["id"] for s in seats(row) if s["state"] == "FREE" and s["type"] != "BLOCKED"]
    assignments = dict(zip(pending, free))
    assignments.update({b.user_id: b.seat_id for b in existing if b.user_id in ids})
    preview_id = uid()
    db().add(Credential(key="preview:" + preview_id, user_id=g.user.id, expires=time.time() + 300, payload={"session": identifier, "version": row.version, "assignments": assignments}))
    return {"previewId": preview_id, "baseVersion": row.version, "assignments": assignments, "unseated": [i for i in pending if i not in assignments]}


@api.post("/sessions/<identifier>/assignments/commit")
@protected
def commit(identifier):
    row = session(identifier, ["ORGANIZER"], True)
    data = body(["previewId", "baseVersion"])
    value = db().scalar(select(Credential).where(Credential.key == "preview:" + text(data["previewId"], 36)).with_for_update())
    if not value or value.user_id != g.user.id or value.expires <= time.time() or value.payload["session"] != identifier:
        abort(404)
    if value.payload["version"] != row.version or data["baseVersion"] != row.version:
        abort(409, description="Seat data changed; preview again")
    for user_id, seat_id in value.payload["assignments"].items():
        if not db().scalar(select(Booking).where(Booking.session_id == identifier, Booking.user_id == user_id)):
            db().add(Booking(session_id=identifier, user_id=user_id, seat_id=seat_id))
    row.version += 1
    db().delete(value)
    return "", 204


@api.post("/sessions/<identifier>/seats/<seat_id>/unassign")
@protected
def unassign(identifier, seat_id):
    row = session(identifier, ["ORGANIZER"], True)
    booking = db().scalar(select(Booking).where(Booking.session_id == identifier, Booking.seat_id == seat_id))
    if not booking:
        abort(404)
    if booking.checked_in:
        abort(409, description="Checked-in bookings cannot be unassigned")
    db().delete(booking)
    row.version += 1
    return "", 204


@api.get("/sessions/<identifier>/checkin-codes/current")
@protected
def screen_code(identifier):
    session(identifier, ["ORGANIZER", "STAFF"])
    return {"token": signed({"session": identifier, "kind": "SCREEN"}, "checkin", 30), "type": "SCREEN", "expiresAt": datetime.fromtimestamp(time.time() + 30, timezone.utc).isoformat()}


@api.post("/sessions/<identifier>/printed-code/rotate")
@protected
def printed_code(identifier):
    row = session(identifier, ["ORGANIZER"], True)
    row.printed_epoch += 1
    lifetime = max(1, int(timestamp(row.payload["checkInClosesAt"]).timestamp() - time.time()))
    return {"token": signed({"session": identifier, "kind": "PRINTED", "epoch": row.printed_epoch}, "checkin", lifetime), "type": "PRINTED", "expiresAt": None}


def record_checkin(row, user_id):
    now = time.time()
    if not timestamp(row.payload["checkInOpensAt"]).timestamp() <= now <= timestamp(row.payload["checkInClosesAt"]).timestamp():
        abort(409, description="Check-in is closed")
    booking = db().scalar(select(Booking).where(Booking.session_id == row.id, Booking.user_id == user_id).with_for_update())
    if not booking:
        abort(409, description="A seat assignment is required")
    points = 0 if booking.checked_in else 10
    booking.checked_in = booking.checked_in or now
    row.version += bool(points)
    label = next(s["label"] for s in row.hall.payload["seats"] if s["id"] == booking.seat_id)
    return {"status": "checked_in", "seatLabel": label, "pointsAwarded": points}


@api.post("/sessions/<identifier>/checkin")
@protected
def checkin(identifier):
    row = session(identifier, lock=True)
    data = body(["token", "deviceId", "clientTimestamp"])
    text(data["deviceId"], 128, 8)
    timestamp(data["clientTimestamp"])
    claims = decode(text(data["token"], 4096), "checkin")
    if claims.get("session") != identifier or (claims.get("kind") == "PRINTED" and claims.get("epoch") != row.printed_epoch):
        abort(400, description="Invalid check-in code")
    return record_checkin(row, g.user.id)


@api.post("/sessions/<identifier>/checkin/manual")
@protected
def manual_checkin(identifier):
    row = session(identifier, ["ORGANIZER", "STAFF"], True)
    code = text(body(["bookingCode"])["bookingCode"], 128, 6)
    booking = db().scalar(select(Booking).where(Booking.id == code, Booking.session_id == identifier))
    if not booking:
        abort(404)
    return record_checkin(row, booking.user_id)


@api.get("/events/<identifier>/pass")
@protected
def attendee_pass(identifier):
    access(identifier)
    event = get(Event, identifier)
    return {"token": signed({"event": identifier, "sub": g.user.id}, "pass", 300), "expiresAt": datetime.fromtimestamp(time.time() + 300, timezone.utc).isoformat(), "attendeeName": g.user.name, "eventTitle": event.payload["title"]}


@api.post("/events/<identifier>/passes/verify")
@protected
def verify_pass(identifier):
    access(identifier, ["ORGANIZER", "STAFF"])
    claims = decode(text(body(["token"])["token"], 4096), "pass")
    if claims.get("event") != identifier or not db().get(Membership, (identifier, claims.get("sub"))):
        abort(400)
    user = get(User, claims["sub"])
    return {"attendeeName": user.name, "eventTitle": get(Event, identifier).payload["title"]}


@api.route("/sessions/<identifier>/polls", methods=["GET", "POST"])
@protected
def polls(identifier):
    session(identifier, ["ORGANIZER"] if request.method == "POST" else None)
    if request.method == "GET":
        return [dict(p.payload, id=p.id, sessionId=identifier) for p in db().scalars(select(Poll).where(Poll.session_id == identifier))]
    data = body(["question", "options"])
    if not isinstance(data["options"], list) or not 2 <= len(data["options"]) <= 8:
        abort(400)
    payload = {"question": text(data["question"], 300, 3), "options": [text(o, 160) for o in data["options"]], "status": "OPEN"}
    poll = Poll(id=uid(), session_id=identifier, payload=payload)
    db().add(poll)
    return dict(payload, id=poll.id, sessionId=identifier), 201


@api.post("/polls/<identifier>/answer")
@protected
def answer(identifier):
    poll = get(Poll, identifier, True)
    session(poll.session_id)
    option = number(body(["optionIndex"])["optionIndex"], 0, len(poll.payload["options"]) - 1, True)
    if poll.payload["status"] != "OPEN" or db().get(Answer, (identifier, g.user.id)):
        abort(409)
    db().add(Answer(poll_id=identifier, user_id=g.user.id, option=option))
    return "", 204


@api.post("/polls/<identifier>/close")
@protected
def close_poll(identifier):
    poll = get(Poll, identifier, True)
    session(poll.session_id, ["ORGANIZER"])
    poll.payload = dict(poll.payload, status="CLOSED")
    return "", 204


@api.get("/polls/<identifier>/results")
@protected
def results(identifier):
    poll = get(Poll, identifier)
    session(poll.session_id)
    answers = db().scalars(select(Answer).where(Answer.poll_id == identifier)).all()
    counts = [sum(a.option == i for a in answers) for i in range(len(poll.payload["options"]))]
    return {"pollId": identifier, "counts": counts, "total": sum(counts)}


def progress(event_id, user_id):
    attendance = len(db().scalars(select(Booking).join(Session).where(Session.event_id == event_id, Booking.user_id == user_id, Booking.checked_in.is_not(None))).all())
    answers = len(db().scalars(select(Answer).join(Poll).join(Session).where(Session.event_id == event_id, Answer.user_id == user_id)).all())
    return {"SESSIONS_CHECKED_IN": attendance, "POLLS_ANSWERED": answers}


def quest_dtos(event_id, user_id, award=False):
    current = progress(event_id, user_id)
    result = []
    for q in db().scalars(select(Quest).where(Quest.event_id == event_id)):
        value = current[q.payload["type"]]
        completed = value >= q.payload["target"]
        if award and completed and not db().get(Award, (q.id, user_id)):
            db().add(Award(quest_id=q.id, user_id=user_id))
        result.append(dict(q.payload, id=q.id, current=value, completed=completed))
    return result


@api.route("/events/<identifier>/quests", methods=["GET", "POST"])
@protected
def quests(identifier):
    access(identifier, ["ORGANIZER"] if request.method == "POST" else None)
    if request.method == "GET":
        return quest_dtos(identifier, g.user.id)
    data = body(["type", "target", "points", "badgeId"])
    if data["type"] not in ("SESSIONS_CHECKED_IN", "POLLS_ANSWERED"):
        abort(400)
    number(data["target"], 1, 100, True)
    number(data["points"], 0, 1000, True)
    badge_id = text(data.pop("badgeId"), 100)
    data["badge"] = {"id": badge_id, "name": "Full House" if data["type"] == "SESSIONS_CHECKED_IN" else "Voice", "description": f"Complete {data['target']} {data['type'].lower().replace('_', ' ')}"}
    quest = Quest(id=uid(), event_id=identifier, payload=data)
    db().add(quest)
    return dict(data, id=quest.id, current=0, completed=False), 201


def leaderboard(event_id):
    users = db().scalars(select(User).join(Membership).where(Membership.event_id == event_id)).all()
    result = []
    for user in users:
        current = progress(event_id, user.id)
        points = current["SESSIONS_CHECKED_IN"] * 10 + sum(q["points"] for q in quest_dtos(event_id, user.id) if q["completed"])
        result.append({"displayName": user.name, "points": points, "isCurrentUser": user.id == g.user.id, "_id": user.id})
    result.sort(key=lambda v: (-v["points"], v["_id"]))
    for rank, value in enumerate(result, 1):
        value.pop("_id")
        value["rank"] = rank
    return result


@api.get("/events/<identifier>/leaderboard")
@protected
def rankings(identifier):
    access(identifier)
    return leaderboard(identifier)


@api.get("/events/<identifier>/wrapped/me")
@protected
def wrapped(identifier):
    access(identifier)
    values = progress(identifier, g.user.id)
    quests = quest_dtos(identifier, g.user.id)
    rank = next(v for v in leaderboard(identifier) if v["isCurrentUser"])
    return {"sessionsAttended": values["SESSIONS_CHECKED_IN"], "pollsAnswered": values["POLLS_ANSWERED"], "points": rank["points"], "rank": rank["rank"], "badges": [q["badge"] for q in quests if q["completed"]]}


@api.post("/events/<identifier>/wrapped/generate")
@protected
def generate_wrapped(identifier):
    access(identifier, ["ORGANIZER"])
    for member in db().scalars(select(Membership).where(Membership.event_id == identifier).with_for_update()):
        quest_dtos(identifier, member.user_id, True)
    return "", 202


@api.get("/events/<identifier>/occupancy")
@protected
def occupancy(identifier):
    access(identifier, ["ORGANIZER", "STAFF"])
    result = []
    now = time.time()
    for row in db().scalars(select(Session).where(Session.event_id == identifier)):
        bookings = db().scalars(select(Booking).where(Booking.session_id == row.id)).all()
        checked = sum(bool(b.checked_in) for b in bookings)
        capacity = sum(s["type"] != "BLOCKED" and s["state"] != "BLOCKED" for s in row.hall.payload["seats"])
        result.append({"sessionId": row.id, "capacity": capacity, "assigned": len(bookings), "checkedIn": checked, "noShows": len(bookings) - checked if now > timestamp(row.payload["checkInClosesAt"]).timestamp() else 0})
    return result
