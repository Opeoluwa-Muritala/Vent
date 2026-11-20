import os

from flask import Flask, g, jsonify, request
from sqlalchemy import create_engine
from sqlalchemy.engine import make_url
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker
from werkzeug.exceptions import HTTPException


def create_app(config=None):
    app = Flask(__name__)
    app.config.from_mapping(
        SECRET_KEY=os.getenv("SECRET_KEY", ""),
        DATABASE_URL=os.getenv("DATABASE_URL", ""),
        ALLOWED_ORIGINS=os.getenv("ALLOWED_ORIGINS", "").split(","),
        MAX_CONTENT_LENGTH=1024 * 1024,
        ORGANIZER_EMAILS=os.getenv("ORGANIZER_EMAILS", "").lower().split(","),
        EMAIL_SERVICE_URL=os.getenv("EMAIL_SERVICE_URL", "https://emailope.vercel.app/"),
    )
    app.config.update(config or {})
    if len(app.config["SECRET_KEY"]) < 32:
        raise ValueError("SECRET_KEY must contain at least 32 characters")
    url = make_url(app.config["DATABASE_URL"])
    if url.drivername in ("postgres", "postgresql"):
        url = url.set(drivername="postgresql+psycopg")
    if not app.testing:
        if url.drivername != "postgresql+psycopg":
            raise ValueError("Production requires PostgreSQL")
        if url.query.get("sslmode") not in ("require", "verify-ca", "verify-full"):
            raise ValueError("DATABASE_URL must require TLS")
    engine = create_engine(url, pool_pre_ping=True)
    app.extensions["engine"] = engine
    factory = sessionmaker(engine, expire_on_commit=False)

    @app.before_request
    def begin():
        g.db = factory()
        origin = request.headers.get("Origin")
        if origin and origin not in app.config["ALLOWED_ORIGINS"]:
            from flask import abort
            abort(403, description="Origin is not allowed")

    @app.after_request
    def finish(response):
        if response.status_code < 400:
            g.db.commit()
        else:
            g.db.rollback()
        response.headers.update({
            "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
            "X-Frame-Options": "DENY", "Referrer-Policy": "no-referrer",
            "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
        })
        origin = request.headers.get("Origin")
        if origin and origin in app.config["ALLOWED_ORIGINS"]:
            response.headers["Access-Control-Allow-Origin"] = origin
            response.headers["Vary"] = "Origin"
            response.headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type"
            response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
        return response

    @app.teardown_request
    def close(error):
        if hasattr(g, "db"):
            g.db.close()

    @app.errorhandler(HTTPException)
    def http_error(error):
        return jsonify(code=error.name.lower().replace(" ", "_"), message=error.description), error.code

    @app.errorhandler(IntegrityError)
    def conflict(error):
        g.db.rollback()
        return jsonify(code="conflict", message="The operation conflicts with existing data"), 409

    @app.errorhandler(Exception)
    def internal_error(error):
        g.db.rollback()
        app.logger.error("Request failed (%s)", type(error).__name__)
        return jsonify(code="internal_error", message="The request could not be completed"), 500

    from .auth import auth
    app.register_blueprint(auth, url_prefix="/api/v1")
    from .events import api
    app.register_blueprint(api, url_prefix="/api/v1")

    @app.get("/api/v1/health")
    def health():
        from sqlalchemy import text
        g.db.execute(text("SELECT 1"))
        return {"status": "ok"}

    return app
