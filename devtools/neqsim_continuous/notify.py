"""Digest notifiers: file (always), SMTP email and generic webhook (Teams, Slack, ...).

Secrets never live in the task: the SMTP password and the webhook URL are read from
environment variables named in the plan (``password_env``, ``url_env``). Sending is
opt-in per channel and suppressed in dry runs and backtests.
"""

import json
import os
import smtplib
import urllib.request
from email.message import EmailMessage

from .contracts import register

WHEN = ("every", "needs_decision", "trigger", "stop_state")


def wanted(channel_spec, summary):
    """Return True when the cycle summary matches the channel's ``when`` filter."""
    when = channel_spec.get("when", ["every"])
    if "every" in when:
        return True
    if "needs_decision" in when and summary.get("needs_decision"):
        return True
    if "trigger" in when and summary.get("triggers"):
        return True
    if "stop_state" in when and summary.get("stop_state"):
        return True
    return False


def notify_file(spec, subject, body, summary, cycle_dir):
    path = os.path.join(cycle_dir, "digest.md")
    return {"channel": "file", "status": "ok", "path": path}


def notify_smtp(spec, subject, body, summary, cycle_dir):
    host = spec.get("host")
    recipients = spec.get("to", [])
    if not host or not recipients:
        return {"channel": "email", "status": "skipped", "message": "host and to are required"}
    message = EmailMessage()
    message["Subject"] = subject
    message["From"] = spec.get("from", "neqsim-continuous@localhost")
    message["To"] = ", ".join(recipients)
    message.set_content(body)
    port = int(spec.get("port", 587))
    with smtplib.SMTP(host, port, timeout=30) as server:
        if spec.get("starttls", True):
            server.starttls()
        user = spec.get("user")
        password = os.environ.get(spec.get("password_env", ""), "")
        if user and password:
            server.login(user, password)
        server.send_message(message)
    return {"channel": "email", "status": "ok", "to": len(recipients)}


def notify_webhook(spec, subject, body, summary, cycle_dir):
    url = os.environ.get(spec.get("url_env", ""), "")
    if not url:
        return {"channel": "webhook", "status": "skipped",
                "message": "environment variable {} is not set".format(spec.get("url_env"))}
    payload = {"title": subject, "text": body}
    request = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return {"channel": "webhook", "status": "ok", "http_status": response.status}


register("notifiers", "file", notify_file)
register("notifiers", "email", notify_smtp)
register("notifiers", "smtp", notify_smtp)
register("notifiers", "webhook", notify_webhook)
register("notifiers", "teams", notify_webhook)
