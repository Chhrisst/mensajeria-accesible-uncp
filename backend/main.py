from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.database import engine, Base
from app.routes import auth, contacts, messages, ai

# Asegurar creación de tablas si no existen
Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="Mensajería UNCP - API Inclusiva",
    description="Backend en FastAPI para autenticación y mensajería accesible de la UNCP",
    version="1.0.0"
)

# CORS permitido para que la app Android y herramientas web puedan comunicarse
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Incluir routers
app.include_router(auth.router, prefix="/api/auth", tags=["Autenticación"])
app.include_router(contacts.router, prefix="/api/contacts", tags=["Contactos"])
app.include_router(messages.router, prefix="/api/messages", tags=["Mensajes"])
app.include_router(ai.router, prefix="/api/ai", tags=["Inteligencia Artificial"])

@app.get("/")
def health_check():
    return {
        "status": "online",
        "app": "Mensajería UNCP",
        "database": "PostgreSQL 16",
        "version": "1.0.0"
    }
