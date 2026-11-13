from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import or_, func
import bcrypt
from typing import List
import uuid

from app.database import get_db
from app.models import User, Contact
from app.schemas import LoginRequest, RegisterRequest, UserResponse

router = APIRouter()

def hash_pin_or_password(pwd: str) -> str:
    pwd_bytes = pwd.encode('utf-8')[:72]
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(pwd_bytes, salt).decode('utf-8')

def check_pin_or_password(plain: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(plain.encode('utf-8')[:72], hashed.encode('utf-8'))
    except Exception:
        return False

def verify_credential(plain: str, stored: str) -> bool:
    if not plain or not stored:
        return False
    # Si está guardado como hash bcrypt
    if stored.startswith("$2b$") or stored.startswith("$2a$"):
        return check_pin_or_password(plain, stored)
    # Respaldo de compatibilidad para cuentas preexistentes en texto plano
    return plain.strip().upper() == stored.strip().upper()

def user_to_response(u: User) -> UserResponse:
    return UserResponse(
        id=str(u.id),
        name=u.name,
        username=u.username or "",
        email=u.email,
        phone=u.phone,
        dni=u.dni,
        role="Usuario",
        description="Con discapacidad" if u.has_disability else "Sin discapacidad",
        has_disability=u.has_disability,
        visual_condition="Con discapacidad" if u.has_disability else "Sin discapacidad"
    )

@router.post("/login", response_model=UserResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    clean_query = request.query.strip().lower()
    clean_username = clean_query.lstrip('@')
    
    # Buscar por coincidencia en username, nombre, correo, teléfono o código PIN
    user = db.query(User).filter(
        or_(
            func.lower(User.username) == clean_username,
            func.lower(User.email) == clean_query,
            func.lower(User.name).contains(clean_query),
            func.lower(User.pin_code) == clean_query,
            User.phone.contains(clean_query)
        )
    ).first()

    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Usuario o correo no encontrado."
        )

    clean_pass = request.password_or_pin.strip()
    if not clean_pass:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Debe ingresar su contraseña o PIN."
        )

    # Verificación criptográfica segura de contraseña, PIN o patrón L-R
    is_valid = (
        verify_credential(clean_pass, user.pin_code) or
        verify_credential(clean_pass.upper(), user.side_pattern) or
        verify_credential(clean_pass, user.password_hash)
    )

    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Contraseña o PIN incorrecto."
        )

    return user_to_response(user)

@router.post("/register", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
def register(request: RegisterRequest, db: Session = Depends(get_db)):
    clean_name = re.sub(r'[^a-zA-Z0-9]', '', request.full_name).lower() if request.full_name else "usuario"
    if not clean_name:
        clean_name = "usuario"

    # Determinar username único
    if request.username and request.username.strip():
        chosen_username = re.sub(r'[^a-zA-Z0-9_]', '', request.username.strip().lstrip('@')).lower()
        if not chosen_username:
            chosen_username = f"{clean_name}_{str(uuid.uuid4())[:4]}"
    else:
        chosen_username = f"{clean_name}_{str(uuid.uuid4())[:4]}"

    # Asegurar unicidad de username
    candidate_username = chosen_username
    counter = 1
    while db.query(User).filter(func.lower(User.username) == candidate_username).first():
        candidate_username = f"{chosen_username}_{counter}"
        counter += 1

    # Si no tiene correo (registro por voz de persona con discapacidad visual), autogenerar uno
    if request.email and request.email.strip():
        user_email = request.email.strip().lower()
    else:
        user_email = f"{candidate_username}@messenger.pe"

    existing = db.query(User).filter(func.lower(User.email) == user_email).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="El correo electrónico ya se encuentra registrado."
        )

    raw_pin = request.pin.strip() if request.pin else "123"
    raw_pattern = request.side_pattern.strip().upper() if request.side_pattern else "LRL"

    hashed_pw = hash_pin_or_password(raw_pin)
    hashed_pin = hash_pin_or_password(raw_pin)
    hashed_pattern = hash_pin_or_password(raw_pattern)
    desc = "Con discapacidad" if request.has_disability else "Sin discapacidad"

    new_user = User(
        name=request.full_name.strip() if request.full_name else "Nuevo Usuario",
        username=candidate_username,
        email=user_email,
        password_hash=hashed_pw,
        pin_code=hashed_pin,
        side_pattern=hashed_pattern,
        phone=request.phone.strip() if request.phone else None,
        dni=request.dni.strip() if request.dni else None,
        role="Usuario",
        description=desc,
        has_disability=request.has_disability if request.has_disability is not None else True,
        visual_condition=desc
    )

    db.add(new_user)
    db.commit()
    db.refresh(new_user)

    # Cada usuario administra su propia lista de contactos estrictamente (Tríada CIA)
    return user_to_response(new_user)

@router.get("/users", response_model=List[UserResponse])
def get_all_users(db: Session = Depends(get_db)):
    users = db.query(User).all()
    return [user_to_response(u) for u in users]
