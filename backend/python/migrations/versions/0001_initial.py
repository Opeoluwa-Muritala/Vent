"""Initial independent Flask schema; leaves the Ktor schema untouched."""

from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade():
    # Freeze the initial metadata for this revision; future changes use new revisions.
    from migrations.initial_schema import metadata

    metadata.create_all(op.get_bind())


def downgrade():
    from migrations.initial_schema import metadata

    metadata.drop_all(op.get_bind())
