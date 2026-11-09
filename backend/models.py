import uuid
from sqlalchemy import Column, String, Boolean, Text, DateTime, ForeignKey, Integer
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.sql import func
from app.database import Base

class User(Base):
    __tablename__ = "users"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(String(120), nullable=False)
    username = Column(String(60), unique=True, nullable=True, index=True)
    email = Column(String(150), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    pin_code = Column(String(10), nullable=False, default="123", index=True)
    side_pattern = Column(String(20), nullable=False, default="LRL")
    phone = Column(String(30), nullable=True)
    dni = Column(String(20), nullable=True)
    role = Column(String(50), default="Usuario")
    description = Column(Text, default="Socio UNCP")
    has_disability = Column(Boolean, default=True)
    visual_condition = Column(String(100), default="Baja visión")
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

class Contact(Base):
    __tablename__ = "contacts"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    contact_id = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    alias = Column(String(100), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class Message(Base):
    __tablename__ = "messages"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    sender_id = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    receiver_id = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    content = Column(Text, nullable=True)
    message_type = Column(String(20), default="TEXT") # TEXT, AUDIO, VOICE_TRANSCRIPTION
    audio_url = Column(Text, nullable=True)
    audio_duration_seconds = Column(Integer, default=0)
    is_read = Column(Boolean, default=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), index=True)
