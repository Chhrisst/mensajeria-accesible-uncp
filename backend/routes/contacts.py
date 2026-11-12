from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import or_, desc, func
from typing import List
import uuid

from app.database import get_db
from app.models import User, Contact, Message
from app.schemas import ContactResponse, AddContactRequest

router = APIRouter()

@router.get("/{user_id}", response_model=List[ContactResponse])
def get_user_contacts(user_id: str, db: Session = Depends(get_db)):
    try:
        user_uuid = uuid.UUID(user_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="ID de usuario inválido.")

    # Tríada CIA (Confidencialidad): Obtener ÚNICAMENTE los contactos autorizados del usuario
    contact_entries = db.query(Contact).filter(Contact.user_id == user_uuid).all()
    if not contact_entries:
        return []

    contact_ids = [c.contact_id for c in contact_entries]
    contacts_users = db.query(User).filter(User.id.in_(contact_ids)).all()
    user_map = {u.id: u for u in contacts_users}

    contacts_list = []
    for c_entry in contact_entries:
        other = user_map.get(c_entry.contact_id)
        if not other:
            continue

        # Buscar último mensaje entre ambos
        last_msg = db.query(Message).filter(
            or_(
                (Message.sender_id == user_uuid) & (Message.receiver_id == other.id),
                (Message.sender_id == other.id) & (Message.receiver_id == user_uuid)
            )
        ).order_by(desc(Message.created_at)).first()

        content = "Toca para iniciar conversación"
        time_str = "Hoy"
        if last_msg:
            content = last_msg.content if last_msg.content else "Mensaje de voz"
            time_str = last_msg.created_at.strftime("%H:%M") if last_msg.created_at else "Hoy"

        contacts_list.append(
            ContactResponse(
                id=str(other.id),
                name=c_entry.alias if c_entry.alias else other.name,
                username=other.username or "",
                email=other.email,
                phone=other.phone,
                role=other.role,
                visual_condition=other.visual_condition,
                last_message=content,
                last_message_time=time_str,
                unread_count=0
            )
        )

    return contacts_list

@router.post("/add", response_model=ContactResponse, status_code=status.HTTP_201_CREATED)
def add_contact(payload: AddContactRequest, db: Session = Depends(get_db)):
    try:
        user_uuid = uuid.UUID(payload.user_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="ID de usuario inválido.")

    clean_username = payload.contact_username.strip().lstrip('@').lower()
    if not clean_username:
        raise HTTPException(status_code=400, detail="Debe especificar un nombre de usuario.")

    # Buscar usuario destino por nombre de usuario único (@handle), correo o nombre completo
    target_user = db.query(User).filter(
        or_(
            func.lower(User.username) == clean_username,
            func.lower(User.email) == clean_username,
            func.lower(User.name) == clean_username,
            func.lower(User.name).ilike(f"%{clean_username}%")
        )
    ).first()

    if not target_user:
        raise HTTPException(
            status_code=404,
            detail=f"No se encontró ningún usuario con el identificador @{clean_username}."
        )

    if target_user.id == user_uuid:
        raise HTTPException(
            status_code=400,
            detail="No puedes agregarte a ti mismo como contacto."
        )

    # Verificar si ya existe en la lista de contactos del usuario
    existing_contact = db.query(Contact).filter(
        Contact.user_id == user_uuid,
        Contact.contact_id == target_user.id
    ).first()

    if not existing_contact:
        # Agregar contacto a la agenda del usuario
        new_contact = Contact(
            user_id=user_uuid,
            contact_id=target_user.id,
            alias=target_user.name
        )
        db.add(new_contact)

        # Relación bilateral para permitir mensajería recíproca inmediata
        reciprocal_contact = db.query(Contact).filter(
            Contact.user_id == target_user.id,
            Contact.contact_id == user_uuid
        ).first()
        if not reciprocal_contact:
            me_user = db.query(User).filter(User.id == user_uuid).first()
            alias_me = me_user.name if me_user else None
            db.add(Contact(
                user_id=target_user.id,
                contact_id=user_uuid,
                alias=alias_me
            ))

        db.commit()

    # Buscar último mensaje si existiese
    last_msg = db.query(Message).filter(
        or_(
            (Message.sender_id == user_uuid) & (Message.receiver_id == target_user.id),
            (Message.sender_id == target_user.id) & (Message.receiver_id == user_uuid)
        )
    ).order_by(desc(Message.created_at)).first()

    content = "Toca para iniciar conversación"
    time_str = "Hoy"
    if last_msg:
        content = last_msg.content if last_msg.content else "Mensaje de voz"
        time_str = last_msg.created_at.strftime("%H:%M") if last_msg.created_at else "Hoy"

    return ContactResponse(
        id=str(target_user.id),
        name=target_user.name,
        username=target_user.username or "",
        email=target_user.email,
        phone=target_user.phone,
        role=target_user.role,
        visual_condition=target_user.visual_condition,
        last_message=content,
        last_message_time=time_str,
        unread_count=0
    )

@router.delete("/{user_id}/{contact_id}")
def delete_contact(user_id: str, contact_id: str, db: Session = Depends(get_db)):
    try:
        u_uuid = uuid.UUID(user_id)
        c_uuid = uuid.UUID(contact_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="IDs de usuario inválidos.")

    deleted = db.query(Contact).filter(
        Contact.user_id == u_uuid,
        Contact.contact_id == c_uuid
    ).delete()
    db.commit()

    return {"status": "success", "deleted": deleted > 0}
