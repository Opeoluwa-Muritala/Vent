import uuid

from flask import g
from sqlalchemy import JSON, Column, Float, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, relationship


def uid():
    return str(uuid.uuid4())


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "vent_py_users"
    id = Column(String(36), primary_key=True, default=uid)
    email = Column(String(254), unique=True, nullable=False)
    name = Column(String(120), nullable=False)
    preferences = Column(JSON, nullable=False, default=dict)


class Credential(Base):
    __tablename__ = "vent_py_credentials"
    key = Column(String(128), primary_key=True)
    user_id = Column(ForeignKey(User.id))
    expires = Column(Float, nullable=False)
    payload = Column(JSON, nullable=False, default=dict)
    attempts = Column(Integer, nullable=False, default=0)


class Event(Base):
    __tablename__ = "vent_py_events"
    id = Column(String(36), primary_key=True, default=uid)
    payload = Column(JSON, nullable=False)


class Membership(Base):
    __tablename__ = "vent_py_memberships"
    event_id = Column(ForeignKey(Event.id), primary_key=True)
    user_id = Column(ForeignKey(User.id), primary_key=True)
    role = Column(String(20), nullable=False)


class Hall(Base):
    __tablename__ = "vent_py_halls"
    id = Column(String(36), primary_key=True, default=uid)
    event_id = Column(ForeignKey(Event.id), nullable=False, index=True)
    payload = Column(JSON, nullable=False)


class Session(Base):
    __tablename__ = "vent_py_sessions"
    id = Column(String(36), primary_key=True, default=uid)
    event_id = Column(ForeignKey(Event.id), nullable=False, index=True)
    hall_id = Column(ForeignKey(Hall.id), nullable=False)
    version = Column(Integer, nullable=False, default=1)
    printed_epoch = Column(Integer, nullable=False, default=1)
    payload = Column(JSON, nullable=False)
    hall = relationship(Hall)


class Booking(Base):
    __tablename__ = "vent_py_bookings"
    __table_args__ = (UniqueConstraint("session_id", "seat_id"), UniqueConstraint("session_id", "user_id"))
    id = Column(String(36), primary_key=True, default=uid)
    session_id = Column(ForeignKey(Session.id), nullable=False, index=True)
    user_id = Column(ForeignKey(User.id), nullable=False)
    seat_id = Column(String(100), nullable=False)
    checked_in = Column(Float)


class Poll(Base):
    __tablename__ = "vent_py_polls"
    id = Column(String(36), primary_key=True, default=uid)
    session_id = Column(ForeignKey(Session.id), nullable=False, index=True)
    payload = Column(JSON, nullable=False)


class Answer(Base):
    __tablename__ = "vent_py_answers"
    poll_id = Column(ForeignKey(Poll.id), primary_key=True)
    user_id = Column(ForeignKey(User.id), primary_key=True)
    option = Column(Integer, nullable=False)


class Quest(Base):
    __tablename__ = "vent_py_quests"
    id = Column(String(36), primary_key=True, default=uid)
    event_id = Column(ForeignKey(Event.id), nullable=False, index=True)
    payload = Column(JSON, nullable=False)


class Award(Base):
    __tablename__ = "vent_py_awards"
    quest_id = Column(ForeignKey(Quest.id), primary_key=True)
    user_id = Column(ForeignKey(User.id), primary_key=True)


def db():
    return g.db
