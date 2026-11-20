import json
from unittest.mock import MagicMock, patch
from urllib.error import URLError

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
        opener.return_value.open.side_effect = URLError("unavailable")
        response = client.post("/api/v1/auth/otp/request", json={"email": "guest@example.com"})
        assert response.status_code == 503
        assert "unavailable" not in response.get_data(as_text=True)
