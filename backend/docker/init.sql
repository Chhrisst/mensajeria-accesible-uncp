-- ========================================================
-- BASE DE DATOS: MENSAJERÍA INCLUSIVA UNCP
-- Base de datos relacional PostgreSQL con soporte de accesibilidad
-- ========================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. TABLA DE USUARIOS
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    pin_code VARCHAR(10) NOT NULL DEFAULT '123',
    side_pattern VARCHAR(20) NOT NULL DEFAULT 'LRL',
    phone VARCHAR(30),
    dni VARCHAR(20),
    role VARCHAR(50) DEFAULT 'Socio Activo UNCP',
    description TEXT DEFAULT 'Socio UNCP',
    has_disability BOOLEAN DEFAULT TRUE,
    visual_condition VARCHAR(100) DEFAULT 'Baja visión',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. TABLA DE CONTACTOS ENTRE USUARIOS
CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alias VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_contact UNIQUE (user_id, contact_id)
);

-- 3. TABLA DE MENSAJES (SOPORTE TEXTO Y NOTAS DE VOZ)
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT,
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT', -- 'TEXT', 'AUDIO', 'VOICE_TRANSCRIPTION'
    audio_url TEXT,
    audio_duration_seconds INT DEFAULT 0,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. ÍNDICES DE RENDIMIENTO PARA MENSAJERÍA RÁPIDA
CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver ON messages(receiver_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_pin ON users(pin_code);

-- 5. USUARIOS INICIALES (SEMILLA PRECARGADA)
INSERT INTO users (id, name, email, password_hash, pin_code, side_pattern, phone, dni, role, description, has_disability, visual_condition)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Christian Beteta', 'christian@gmail.com', crypt('123', gen_salt('bf')), '123', 'LRL', '912 345 678', '71234567', 'Socio UNCP', 'Taller de Tecnologías Adaptadas', TRUE, 'Baja visión'),
    ('22222222-2222-2222-2222-222222222222', 'Romell Rivera', 'romell@gmail.com', crypt('123', gen_salt('bf')), '123', 'LRR', '987 654 321', '72345678', 'Socio Activo UNCP', 'Socio Activo UNCP', TRUE, 'Baja visión'),
    ('33333333-3333-3333-3333-333333333333', 'Miguel Retuerto', 'miguel@gmail.com', crypt('456', gen_salt('bf')), '456', 'LLR', '998 877 665', '73456789', 'Socio UNCP', 'Comité de Comunicaciones', TRUE, 'Ceguera total'),
    ('44444444-4444-4444-4444-444444444444', 'Juan Perez García', 'juan@gmail.com', crypt('123', gen_salt('bf')), '123', 'RLR', '987 654 321', '74567890', 'Socio UNCP', 'Usuario Registrado', TRUE, 'Ceguera total'),
    ('55555555-5555-5555-5555-555555555555', 'Socio Invitado', 'invitado@uncp.pe', crypt('000', gen_salt('bf')), '000', 'LLL', '900 000 000', '00000000', 'Participante Experimental', 'Perfil de evaluación y pruebas', TRUE, 'Ceguera total')
ON CONFLICT (email) DO NOTHING;

-- 6. ASOCIAR CONTACTOS MUTUOS ENTRE LOS SOCIOS
INSERT INTO contacts (user_id, contact_id) VALUES
    ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222'),
    ('11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333'),
    ('11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444444'),
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111'),
    ('22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333')
ON CONFLICT (user_id, contact_id) DO NOTHING;

-- 7. MENSAJES DE EJEMPLO DE BIENVENIDA
INSERT INTO messages (sender_id, receiver_id, content, message_type, is_read) VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', '¡Hola Christian! ¿Cómo va el avance del sistema?', 'TEXT', TRUE),
    ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Hola Romell, ya tenemos la arquitectura MVVM y la base de datos lista en Docker.', 'TEXT', TRUE)
ON CONFLICT DO NOTHING;
