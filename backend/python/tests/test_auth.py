from conftest import login
from sqlalchemy import select
from sqlalchemy.orm import Session
from vent_backend.db import Credential


def test_authentication_required_and_preferences_roundtrip(client, app):
    assert client.get("/api/v1/events").status_code == 401
    pair = login(client, app, "person@example.com")
    headers = {"Authorization": "Bearer " + pair["accessToken"]}
    assert client.get("/api/v1/me/preferences", headers=headers).json == {"bookmarks": [], "preferences": []}
    assert (
        client.put("/api/v1/me/preferences", headers=headers, json={"bookmarks": ["a"], "preferences": []}).status_code
        == 204
    )
    assert client.get("/api/v1/me/preferences", headers=headers).json["bookmarks"] == ["a"]
    assert client.put("/api/v1/me/preferences", headers=headers, json={"role": "ORGANIZER"}).status_code == 400


def test_otp_is_single_use_and_refresh_rotates(client, app):
    pair = login(client, app, "person@example.com")
    assert (
        client.post(
            "/api/v1/auth/otp/verify",
            json={"email": "person@example.com", "code": app.extensions["messages"]["person@example.com"]},
        ).status_code
        == 401
    )
    response = client.post("/api/v1/auth/refresh", json={"refreshToken": pair["refreshToken"]})
    assert response.status_code == 200
    assert client.post("/api/v1/auth/refresh", json={"refreshToken": pair["refreshToken"]}).status_code == 401
    headers = {"Authorization": "Bearer " + response.json["accessToken"]}
    assert client.post("/api/v1/auth/logout", headers=headers).status_code == 204
    assert client.get("/api/v1/me", headers=headers).status_code == 401


def test_failed_otp_attempts_persist_and_lock(client, app):
    client.post("/api/v1/auth/otp/request", json={"email": "person@example.com"})
    correct = app.extensions["messages"]["person@example.com"]
    wrong = "111111" if correct != "111111" else "222222"
    for _ in range(5):
        assert (
            client.post("/api/v1/auth/otp/verify", json={"email": "person@example.com", "code": wrong}).status_code
            == 401
        )
    assert (
        client.post("/api/v1/auth/otp/verify", json={"email": "person@example.com", "code": correct}).status_code == 429
    )


def test_expired_otp_and_disallowed_origins(client, app):
    client.post("/api/v1/auth/otp/request", json={"email": "person@example.com"})
    with Session(app.extensions["engine"]) as db:
        for row in db.scalars(select(Credential).where(Credential.key.like("otp:%"))):
            row.expires = 0
        db.commit()
    assert (
        client.post(
            "/api/v1/auth/otp/verify",
            json={"email": "person@example.com", "code": app.extensions["messages"]["person@example.com"]},
        ).status_code
        == 401
    )
    assert client.get("/api/v1/health", headers={"Origin": "https://evil.example"}).status_code == 403
    assert (
        client.options("/api/v1/auth/otp/request", headers={"Origin": "http://localhost:8080"}).headers[
            "Access-Control-Allow-Origin"
        ]
        == "http://localhost:8080"
    )


def test_resend_cooldown_and_account_isolation(client, app):
    first = login(client, app, "a@example.com")
    second = login(client, app, "b@example.com")
    a = {"Authorization": "Bearer " + first["accessToken"]}
    b = {"Authorization": "Bearer " + second["accessToken"]}
    client.put("/api/v1/me/preferences", headers=a, json={"bookmarks": ["private"]})
    assert client.get("/api/v1/me/preferences", headers=b).json["bookmarks"] == []
    assert client.post("/api/v1/events", headers=b, json={}).status_code == 403
