from dotenv import load_dotenv

load_dotenv()

from vent_backend import create_app  # noqa: E402

app = create_app()
