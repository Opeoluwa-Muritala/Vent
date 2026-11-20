from alembic import context
from dotenv import load_dotenv
from vent_backend import create_app
from vent_backend.db import Base

load_dotenv()

app = create_app()
engine = app.extensions["engine"]
if context.is_offline_mode():
    context.configure(url=engine.url, target_metadata=Base.metadata, literal_binds=True)
    with context.begin_transaction():
        context.run_migrations()
else:
    with engine.connect() as connection:
        context.configure(connection=connection, target_metadata=Base.metadata)
        with context.begin_transaction():
            context.run_migrations()
