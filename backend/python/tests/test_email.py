import json
from unittest.mock import MagicMock, patch
from urllib.error import URLError

from sqlalchemy import select
from sqlalchemy.orm import Session
from vent_backend.db import Credential
from vent_backend.email_service import deliver


def test_gateway_uses_fieldcrm_payload_without_token(app):
    response = MagicMock()
    response.__enter__.return_value.status = 202
    with app.app_context(), patch("vent_backend.email_service.build_opener") as opener:
        opener.return_value.open.return_value = response
        deliver("guest@example.com", "123456")
        req = opener.return_value.open.call_args.args[0]
        assert req.method == "POST"
        assert not req.has_header("Authorization")
        data = json.loads(req.data)
        assert set(data) == {"to", "subject", "text", "html"}
        assert data["to"] == "guest@example.com" and "123456" in data["text"]


def test_gateway_failure_does_not_leave_valid_otp(client, app):
    app.config.pop("EMAIL_SENDER")
    with patch("vent_backend.email_service.build_opener") as opener:
        opener.return_value.open.side_effect = URLError("private-gateway-diagnostic")
        response = client.post("/api/v1/auth/otp/request", json={"email": "guest@example.com"})
        assert response.status_code == 503
        assert "private-gateway-diagnostic" not in response.get_data(as_text=True)
    with Session(app.extensions["engine"]) as db:
        assert db.scalar(select(Credential).where(Credential.key.like("otp:%"))) is None
