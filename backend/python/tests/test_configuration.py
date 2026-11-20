import pytest
import vent_backend
from alembic import command
from alembic.config import Config
from sqlalchemy import inspect
from vent_backend import create_app
from vent_backend.db import Base


def test_requires_secret_and_postgres_tls():
    with pytest.raises(ValueError, match="SECRET_KEY"):
        create_app({"SECRET_KEY": "short"})
    with pytest.raises(ValueError, match="TLS"):
        create_app({"SECRET_KEY": "x" * 64, "DATABASE_URL": "postgresql://user:pass@localhost/test"})
    with pytest.raises(ValueError, match="PostgreSQL"):
        create_app({"SECRET_KEY": "x" * 64, "DATABASE_URL": "sqlite://"})


def test_migrations_upgrade_and_downgrade(app, monkeypatch):
    from pathlib import Path

    engine = app.extensions["engine"]
    Base.metadata.drop_all(engine)
    monkeypatch.setattr(vent_backend, "create_app", lambda: app)
    config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
    command.upgrade(config, "head")
    assert set(Base.metadata.tables) <= set(inspect(engine).get_table_names())
    for table in Base.metadata.sorted_tables:
        assert {c.name for c in table.columns} == {c["name"] for c in inspect(engine).get_columns(table.name)}
    command.downgrade(config, "base")
    assert not set(Base.metadata.tables) & set(inspect(engine).get_table_names())
