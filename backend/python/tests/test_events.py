from datetime import datetime, timedelta, timezone

from conftest import login


def setup_event(client, organizer):
    now = datetime.now(timezone.utc)
    timing = {"startsAt": (now - timedelta(minutes=5)).isoformat(), "endsAt": (now + timedelta(hours=1)).isoformat()}
    event = client.post("/api/v1/events", headers=organizer, json=dict(timing, title="Summit", seedArgb="FF6750A4"))
    assert event.status_code == 201, event.json
    eid = event.json["id"]
    seat = {"id": "a1", "label": "A1", "x": 1, "y": 1, "rotation": 0, "type": "STANDARD", "state": "FREE"}
    hall = client.post(
        f"/api/v1/events/{eid}/halls",
        headers=organizer,
        json={"name": "Main", "width": 10, "height": 10, "seats": [seat], "elements": []},
    )
    assert hall.status_code == 201, hall.json
    session = client.post(
        "/api/v1/sessions",
        headers=organizer,
        json=dict(
            timing,
            eventId=eid,
            hallId=hall.json["id"],
            title="Opening",
            checkInOpensAt=timing["startsAt"],
            checkInClosesAt=timing["endsAt"],
        ),
    )
    assert session.status_code == 201, session.json
    return eid, hall.json["id"], session.json["id"]


def test_full_attendee_journey_and_isolation(client, app, organizer):
    eid, hid, sid = setup_event(client, organizer)
    imported = client.post(
        f"/api/v1/events/{eid}/attendees/import",
        headers=organizer,
        json={"attendees": [{"name": "Guest", "email": "guest@example.com"}]},
    )
    assert imported.status_code == 201, imported.json
    attendee_id = imported.json[0]["id"]
    pair = login(client, app, "guest@example.com")
    guest = {"Authorization": "Bearer " + pair["accessToken"]}
    preview = client.post(
        f"/api/v1/sessions/{sid}/assignments/preview", headers=organizer, json={"attendeeIds": [attendee_id]}
    )
    assert preview.status_code == 200, preview.json
    payload = {k: preview.json[k] for k in ("previewId", "baseVersion")}
    assert client.post(f"/api/v1/sessions/{sid}/assignments/commit", headers=organizer, json=payload).status_code == 204
    assert client.get(f"/api/v1/sessions/{sid}/seats", headers=guest).json["seats"][0]["isMine"]
    code = client.get(f"/api/v1/sessions/{sid}/checkin-codes/current", headers=organizer).json["token"]
    checkin = {"token": code, "deviceId": "test-device", "clientTimestamp": datetime.now(timezone.utc).isoformat()}
    assert client.post(f"/api/v1/sessions/{sid}/checkin", headers=guest, json=checkin).json["pointsAwarded"] == 10
    assert client.post(f"/api/v1/sessions/{sid}/checkin", headers=guest, json=checkin).json["pointsAwarded"] == 0
    poll = client.post(
        f"/api/v1/sessions/{sid}/polls", headers=organizer, json={"question": "Enjoying it?", "options": ["Yes", "No"]}
    ).json["id"]
    assert client.post(f"/api/v1/polls/{poll}/answer", headers=guest, json={"optionIndex": 2}).status_code == 400
    assert client.post(f"/api/v1/polls/{poll}/answer", headers=guest, json={"optionIndex": 0}).status_code == 204
    assert client.post(f"/api/v1/polls/{poll}/answer", headers=guest, json={"optionIndex": 0}).status_code == 409
    assert client.get(f"/api/v1/polls/{poll}/results", headers=guest).json["counts"] == [1, 0]
    quest = client.post(
        f"/api/v1/events/{eid}/quests",
        headers=organizer,
        json={"type": "SESSIONS_CHECKED_IN", "target": 1, "points": 30, "badgeId": "full-house"},
    )
    assert quest.status_code == 201, quest.json
    wrapped = client.get(f"/api/v1/events/{eid}/wrapped/me", headers=guest).json
    assert wrapped["points"] == 40 and wrapped["sessionsAttended"] == 1 and wrapped["pollsAnswered"] == 1
    assert client.get(f"/api/v1/events/{eid}/occupancy", headers=organizer).json[0]["checkedIn"] == 1
    token = client.get(f"/api/v1/events/{eid}/pass", headers=guest).json["token"]
    assert (
        client.post(f"/api/v1/events/{eid}/passes/verify", headers=organizer, json={"token": token}).json[
            "attendeeName"
        ]
        == "Guest"
    )
    outsider = login(client, app, "outsider@example.com")
    outsider = {"Authorization": "Bearer " + outsider["accessToken"]}
    for path in (
        f"events/{eid}",
        f"halls/{hid}",
        f"sessions/{sid}/seats",
        f"polls/{poll}/results",
        f"events/{eid}/wrapped/me",
    ):
        assert client.get("/api/v1/" + path, headers=outsider).status_code == 404
    assert (
        client.post(
            f"/api/v1/sessions/{sid}/assignments/preview", headers=guest, json={"attendeeIds": [attendee_id]}
        ).status_code
        == 403
    )


def test_stale_preview_and_layout_protection(client, app, organizer):
    eid, hid, sid = setup_event(client, organizer)
    attendees = client.post(
        f"/api/v1/events/{eid}/attendees/import",
        headers=organizer,
        json={"attendees": [{"name": "One", "email": "one@example.com"}, {"name": "Two", "email": "two@example.com"}]},
    ).json
    previews = [
        client.post(
            f"/api/v1/sessions/{sid}/assignments/preview", headers=organizer, json={"attendeeIds": [a["id"]]}
        ).json
        for a in attendees
    ]
    for index, p in enumerate(previews):
        response = client.post(
            f"/api/v1/sessions/{sid}/assignments/commit",
            headers=organizer,
            json={k: p[k] for k in ("previewId", "baseVersion")},
        )
        assert response.status_code == (204 if index == 0 else 409), response.json
    assert (
        client.post(
            f"/api/v1/halls/{hid}/layout",
            headers=organizer,
            json={"width": 10, "height": 10, "seats": [], "elements": []},
        ).status_code
        == 409
    )


def test_cross_event_session_hall_rejected(client, organizer):
    eid, hid, sid = setup_event(client, organizer)
    other, _, _ = setup_event(client, organizer)
    data = client.get(f"/api/v1/events/{eid}", headers=organizer).json["sessions"][0]
    data.pop("id")
    data["eventId"] = other
    assert client.post("/api/v1/sessions", headers=organizer, json=data).status_code == 400
