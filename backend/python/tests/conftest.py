import pytest
from vent_backend import create_app
from vent_backend.db import Base


@pytest.fixture
def app(tmp_path):
    messages = {}
    app = create_app(
        {
            "TESTING": True,
            "SECRET_KEY": "test-secret-" * 6,
            "DATABASE_URL": "sqlite:///" + str(tmp_path / "test.db"),
            "EMAIL_SENDER": lambda email, code: messages.update({email: code}),
            "ORGANIZER_EMAILS": ["organizer@example.com"],
            "ALLOWED_ORIGINS": ["http://localhost:8080"],
        }
    )
    Base.metadata.create_all(app.extensions["engine"])
    app.extensions["messages"] = messages
    yield app
    app.extensions["engine"].dispose()


@pytest.fixture
def client(app):
    return app.test_client()


def login(client, app, email):
    response = client.post("/api/v1/auth/otp/request", json={"email": email})
    assert response.status_code == 202, response.json
    response = client.post("/api/v1/auth/otp/verify", json={"email": email, "code": app.extensions["messages"][email]})
    assert response.status_code == 200, response.json
    return response.json


@pytest.fixture
def organizer(client, app):
    pair = login(client, app, "organizer@example.com")
    return {"Authorization": "Bearer " + pair["accessToken"]}
