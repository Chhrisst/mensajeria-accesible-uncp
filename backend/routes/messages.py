from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import or_, asc
from typing import List
import uuid

from app.database import get_db
from app.models import Message, Contact, User
from app.schemas import MessageCreate, MessageResponse

router = APIRouter()

@router.get("/{user_id}/{contact_id}", response_model=List[MessageResponse])
def get_chat_messages(user_id: str, contact_id: str, db: Session = Depends(get_db)):
    try:
        u_uuid = uuid.UUID(user_id)
        c_uuid = uuid.UUID(contact_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="IDs de usuario inválidos.")

    msgs = db.query(Message).filter(
        or_(
            (Message.sender_id == u_uuid) & (Message.receiver_id == c_uuid),
            (Message.sender_id == c_uuid) & (Message.receiver_id == u_uuid)
        )
    ).order_by(asc(Message.created_at)).all()

    response = []
    for m in msgs:
        response.append(
            MessageResponse(
                id=str(m.id),
                sender_id=str(m.sender_id),
                receiver_id=str(m.receiver_id),
                content=m.content,
                message_type=m.message_type,
                audio_url=m.audio_url,
                audio_duration_seconds=m.audio_duration_seconds,
                is_read=m.is_read,
                created_at=m.created_at.strftime("%H:%M") if m.created_at else "Ahora",
                is_from_me=(m.sender_id == u_uuid)
            )
        )
    return response

@router.post("", response_model=MessageResponse, status_code=status.HTTP_201_CREATED)
def send_message(payload: MessageCreate, db: Session = Depends(get_db)):
    try:
        s_uuid = uuid.UUID(payload.sender_id)
        r_uuid = uuid.UUID(payload.receiver_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="IDs de remitente o destinatario inválidos.")

    new_msg = Message(
        sender_id=s_uuid,
        receiver_id=r_uuid,
        content=payload.content,
        message_type=payload.message_type or "TEXT",
        audio_url=payload.audio_url,
        audio_duration_seconds=payload.audio_duration_seconds or 0,
        is_read=False
    )
    db.add(new_msg)

    # Asegurar que ambos usuarios tengan el contacto en su agenda bilateral
    c1 = db.query(Contact).filter(Contact.user_id == s_uuid, Contact.contact_id == r_uuid).first()
    if not c1:
        r_user = db.query(User).filter(User.id == r_uuid).first()
        db.add(Contact(user_id=s_uuid, contact_id=r_uuid, alias=r_user.name if r_user else None))

    c2 = db.query(Contact).filter(Contact.user_id == r_uuid, Contact.contact_id == s_uuid).first()
    if not c2:
        s_user = db.query(User).filter(User.id == s_uuid).first()
        db.add(Contact(user_id=r_uuid, contact_id=s_uuid, alias=s_user.name if s_user else None))

    db.commit()
    db.refresh(new_msg)

    return MessageResponse(
        id=str(new_msg.id),
        sender_id=str(new_msg.sender_id),
        receiver_id=str(new_msg.receiver_id),
        content=new_msg.content,
        message_type=new_msg.message_type,
        audio_url=new_msg.audio_url,
        audio_duration_seconds=new_msg.audio_duration_seconds,
        is_read=new_msg.is_read,
        created_at=new_msg.created_at.strftime("%H:%M") if new_msg.created_at else "Ahora",
        is_from_me=True
    )
