"""JSON email gateway contract shared with FieldCRM."""

import json
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from flask import abort, current_app


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def deliver(recipient, code):
    url = current_app.config["EMAIL_SERVICE_URL"]
    parsed = urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.fragment:
        abort(503, description="Email service requires a valid HTTPS URL")
    payload = {
        "to": recipient,
        "subject": "Your Vent sign-in code",
        "text": f"Your Vent code is {code}. It expires in 10 minutes.",
        "html": f"<p>Your Vent sign-in code is <strong>{code}</strong>.</p><p>It expires in 10 minutes.</p>",
    }
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    req = Request(url, data=json.dumps(payload).encode(), headers=headers, method="POST")
    try:
        with build_opener(NoRedirect()).open(req, timeout=15) as response:
            if not 200 <= response.status < 300:
                abort(503, description="Email delivery failed; try again later")
    except (OSError, URLError):
        # Never log the OTP, recipient, gateway credential, or response body.
        abort(503, description="Email delivery failed; try again later")
