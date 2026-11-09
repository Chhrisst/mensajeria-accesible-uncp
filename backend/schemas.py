from typing import Optional, List
from pydantic import BaseModel

class LoginRequest(BaseModel):
    query: str # puede ser nombre, correo o pin
    password_or_pin: str

class RegisterRequest(BaseModel):
    full_name: str
    username: Optional[str] = None
    email: Optional[str] = None
    phone: Optional[str] = ""
    dni: Optional[str] = ""
    birth_date: Optional[str] = ""
    pin: Optional[str] = "123"
    side_pattern: Optional[str] = "LRL"
    has_disability: Optional[bool] = True
    visual_condition: Optional[str] = "Con discapacidad"

class AddContactRequest(BaseModel):
    user_id: str
    contact_username: str

class UserResponse(BaseModel):
    id: str
    name: str
    username: Optional[str] = None
    email: Optional[str] = None
    phone: Optional[str] = None
    dni: Optional[str] = None
    role: str = "Usuario"
    description: Optional[str] = "Con discapacidad"
    has_disability: bool = True
    visual_condition: str = "Con discapacidad"

    class Config:
        from_attributes = True

class ContactResponse(BaseModel):
    id: str
    name: str
    username: Optional[str] = None
    email: str
    phone: Optional[str] = None
    role: str
    visual_condition: str
    last_message: Optional[str] = None
    last_message_time: Optional[str] = None
    unread_count: int = 0

class MessageCreate(BaseModel):
    sender_id: str
    receiver_id: str
    content: Optional[str] = ""
    message_type: Optional[str] = "TEXT" # TEXT, AUDIO
    audio_url: Optional[str] = None
    audio_duration_seconds: Optional[int] = 0

class MessageResponse(BaseModel):
    id: str
    sender_id: str
    receiver_id: str
    content: Optional[str] = None
    message_type: str
    audio_url: Optional[str] = None
    audio_duration_seconds: int = 0
    is_read: bool
    created_at: str
    is_from_me: Optional[bool] = False

    class Config:
        from_attributes = True
