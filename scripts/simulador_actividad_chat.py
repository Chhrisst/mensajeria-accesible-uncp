# -*- coding: utf-8 -*-
"""
=============================================================================
SIMULADOR DE ACTIVIDAD Y AUTO-RESPONDEDOR EN TIEMPO REAL (MESSENGER ACCESIBLE)
=============================================================================
Herramienta para demostraciones y sustentación de tesis:
1. Auto-Respondedor Inteligente E2EE (Demo en vivo con el celular).
2. Envío manual de mensajes desde la laptop hacia el celular.
3. Generador de tráfico e historial de chat en vivo.
"""

import sys
import time
import os
import random
import hashlib
import base64
import requests
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

BACKEND_URL = "http://150.136.172.213:8000"
KDF_SALT = "MESSENGER_UNCP_E2EE_V1_2026_SHARED_KEY_SALT_"
E2E_PREFIX = "E2E:v1:"

# Respuestas automáticas contextuales para la presentación
RESPUESTAS_DEMO = [
    "Hola Christian, todo listo para la exposición.",
    "El asistente de voz se escucha muy claro y fluido.",
    "Excelente, acabo de ver tu mensaje en Messenger.",
    "Ya preparé las diapositivas de la sustentación, nos vemos en 5 minutos.",
    "Confirmado, la comunicación en tiempo real y accesible funciona perfecto.",
    "Hola, estoy conectado desde la aplicación.",
    "Recibí tu mensaje cifrado de extremo a extremo sin problemas."
]

def derive_shared_key(user_a: str, user_b: str) -> bytes:
    clean_a = user_a.strip().lower()
    clean_b = user_b.strip().lower()
    canonical_pair = f"{clean_a}:{clean_b}" if clean_a <= clean_b else f"{clean_b}:{clean_a}"
    seed = (KDF_SALT + canonical_pair).encode("utf-8")
    return hashlib.sha256(seed).digest()

def encrypt_message(plain_text: str, sender_id: str, receiver_id: str) -> str:
    key = derive_shared_key(sender_id, receiver_id)
    aesgcm = AESGCM(key)
    iv = os.urandom(12)
    cipher_bytes = aesgcm.encrypt(iv, plain_text.encode("utf-8"), None)
    combined = iv + cipher_bytes
    encoded = base64.b64encode(combined).decode("ascii")
    return f"{E2E_PREFIX}{encoded}"

def decrypt_message(cipher_text: str, user_a: str, user_b: str) -> str:
    if not cipher_text or not cipher_text.startswith(E2E_PREFIX):
        return cipher_text
    try:
        base64_payload = cipher_text[len(E2E_PREFIX):]
        combined = base64.b64decode(base64_payload)
        if len(combined) <= 12:
            return cipher_text
        iv = combined[:12]
        cipher_bytes = combined[12:]
        key = derive_shared_key(user_a, user_b)
        aesgcm = AESGCM(key)
        plain_bytes = aesgcm.decrypt(iv, cipher_bytes, None)
        return plain_bytes.decode("utf-8")
    except Exception:
        return cipher_text

def get_registered_users():
    try:
        resp = requests.get(f"{BACKEND_URL}/api/auth/users", timeout=5)
        if resp.status_code == 200:
            return resp.json()
    except Exception as e:
        print(f"[!] Error al conectar con el servidor: {e}")
    return []

def send_remote_message(sender_id: str, receiver_id: str, text: str):
    encrypted_payload = encrypt_message(text, sender_id, receiver_id)
    payload = {
        "sender_id": sender_id,
        "receiver_id": receiver_id,
        "content": encrypted_payload,
        "message_type": "TEXT",
        "audio_duration_seconds": 0
    }
    resp = requests.post(f"{BACKEND_URL}/api/messages", json=payload, timeout=5)
    return resp.status_code == 201

def fetch_messages(user_a: str, user_b: str):
    try:
        resp = requests.get(f"{BACKEND_URL}/api/messages/{user_a}/{user_b}", timeout=5)
        if resp.status_code == 200:
            return resp.json()
    except Exception:
        pass
    return []

def run_auto_responder(bot_user, target_user):
    print("\n" + "="*65)
    print(f"[*] AUTO-RESPONDEDOR ACTIVO")
    print(f"[*] El bot '{bot_user['name']}' (@{bot_user.get('username')}) responderá a:")
    print(f"[*] Tu usuario '{target_user['name']}' (@{target_user.get('username')})")
    print(f"[*] Protocolo de cifrado: AES-256-GCM End-to-End (E2EE)")
    print("="*65)
    print("[+] Esperando mensajes entrantes desde tu celular... (Presiona Ctrl+C para salir)\n")

    seen_ids = set()
    initial_msgs = fetch_messages(bot_user["id"], target_user["id"])
    for m in initial_msgs:
        seen_ids.add(m["id"])

    response_index = 0
    try:
        while True:
            time.sleep(3)
            current_msgs = fetch_messages(bot_user["id"], target_user["id"])
            for m in current_msgs:
                msg_id = m["id"]
                if msg_id not in seen_ids:
                    seen_ids.add(msg_id)
                    # Verificar si el mensaje viene del celular hacia el bot
                    if m.get("sender_id") == target_user["id"]:
                        raw_content = m.get("content", "")
                        decrypted_text = decrypt_message(raw_content, bot_user["id"], target_user["id"])
                        print(f"\n[📱 CELULAR DICE]: \"{decrypted_text}\"")
                        print("[⏳ BOT]: Pensando respuesta (simulando escritura de 3 seg)...")
                        time.sleep(3)

                        reply_text = RESPUESTAS_DEMO[response_index % len(RESPUESTAS_DEMO)]
                        response_index += 1

                        # Si preguntan por reunión o sustentación, dar respuesta acorde
                        lower_q = decrypted_text.lower()
                        if "hora" in lower_q or "reunion" in lower_q or "reunión" in lower_q:
                            reply_text = "La sustentación es a las 10:00 AM en el aula principal. ¡Muchos éxitos!"
                        elif "hola" in lower_q or "buenas" in lower_q:
                            reply_text = f"¡Hola {target_user['name'].split()[0]}! Todo listo para la demostración en vivo."

                        ok = send_remote_message(bot_user["id"], target_user["id"], reply_text)
                        if ok:
                            print(f"[🤖 BOT RESPONDE]: \"{reply_text}\" [Cifrado y enviado ✓]")
                        else:
                            print("[!] Error al enviar respuesta")
    except KeyboardInterrupt:
        print("\n[!] Auto-respondedor detenido.")

def run_manual_sender(bot_user, target_user):
    print("\n" + "="*65)
    print(f"[*] MODO CONSOLA EN VIVO: Envía mensajes a tu celular como {bot_user['name']}")
    print("="*65)
    print("Escribe un mensaje y presiona Enter para enviarlo a tu celular.")
    print("Escribe 'salir' para volver al menú.\n")

    while True:
        try:
            msg = input(f"{bot_user['name']} > ").strip()
            if not msg:
                continue
            if msg.lower() == "salir":
                break
            ok = send_remote_message(bot_user["id"], target_user["id"], msg)
            if ok:
                print(f"[✓] Mensaje cifrado y entregado al celular: \"{msg}\"")
            else:
                print("[!] Error al enviar mensaje")
        except KeyboardInterrupt:
            break

def run_traffic_generator(users):
    print("\n" + "="*65)
    print("[*] GENERADOR DE TRÁFICO ENTRE USUARIOS")
    print("="*65)
    if len(users) < 2:
        print("[!] Se necesitan al menos 2 usuarios en la base de datos.")
        return

    conversaciones_ejemplo = [
        ("¿Ya revisaron los lineamientos de accesibilidad WCAG 2.1?", "Sí, el contraste de color y los tamaños táctiles están aprobados."),
        ("¿A qué hora nos reunimos con el asesor de tesis?", "Quedamos a las 3:30 pm en la sala de docentes."),
        ("El asistente de voz en Messenger responde muy rápido.", "Sí, la latencia con la IA se redujo notablemente."),
        ("¿Quién tiene las diapositivas finales?", "Ya las subí a la carpeta compartida, revísalas por favor.")
    ]

    print("[+] Generando 4 intercambios de mensajes...")
    u1 = users[0]
    u2 = users[1]
    for q, a in conversaciones_ejemplo:
        send_remote_message(u1["id"], u2["id"], q)
        time.sleep(1)
        send_remote_message(u2["id"], u1["id"], a)
        time.sleep(1)
        print(f"  [✓] {u1['name']} ↔ {u2['name']}: Intercambio registrado.")

    print("\n[✓] Tráfico generado con éxito. En la app los chats ahora mostrarán actividad reciente.")

def main():
    print("="*65)
    print("   MESSENGER ACCESIBLE - SIMULADOR DE INTERACCIÓN Y DEMO EN VIVO")
    print("="*65)

    users = get_registered_users()
    if not users:
        print("[!] No se pudo obtener la lista de usuarios del servidor.")
        input("Presiona Enter para salir...")
        return

    print("\nUsuarios encontrados en la base de datos:")
    for idx, u in enumerate(users):
        uname = u.get("username", "sin_user")
        disc = " [Con discapacidad]" if u.get("has_disability") else " [Sin discapacidad]"
        print(f"  [{idx + 1}] {u['name']} (@{uname}){disc}")

    print("\nSelecciona tu usuario (el que usarás en tu teléfono Android):")
    try:
        target_idx = int(input("Número de tu usuario: ")) - 1
        target_user = users[target_idx]
    except Exception:
        target_user = users[0]

    print(f"\nHas seleccionado como tu usuario a: {target_user['name']} (@{target_user.get('username')})")

    # Escoger usuario bot (diferente al tuyo)
    bot_candidates = [u for u in users if u["id"] != target_user["id"]]
    if not bot_candidates:
        print("[!] No hay otros usuarios para interactuar.")
        return
    bot_user = bot_candidates[0]
    for b in bot_candidates:
        if "romell" in b["name"].lower() or "juan" in b["name"].lower() or "miguel" in b["name"].lower():
            bot_user = b
            break

    while True:
        print("\n" + "-"*50)
        print("¿Qué deseas simular para la presentación?")
        print(f"  [1] AUTO-RESPONDEDOR EN VIVO (Tu celular le habla a {bot_user['name']} y él te responde solo)")
        print(f"  [2] ENVIAR MENSAJES MANUALES desde esta PC a tu teléfono como {bot_user['name']}")
        print(f"  [3] GENERAR HISTORIAL Y TRÁFICO de fondo entre varios contactos")
        print("  [4] Cambiar contacto bot")
        print("  [5] Salir")
        print("-"*50)
        opc = input("Opción (1-5): ").strip()

        if opc == "1":
            run_auto_responder(bot_user, target_user)
        elif opc == "2":
            run_manual_sender(bot_user, target_user)
        elif opc == "3":
            run_traffic_generator(users)
        elif opc == "4":
            print("\nSelecciona quién será el bot:")
            for idx, u in enumerate(bot_candidates):
                print(f"  [{idx + 1}] {u['name']} (@{u.get('username')})")
            try:
                b_idx = int(input("Número: ")) - 1
                bot_user = bot_candidates[b_idx]
            except Exception:
                pass
        elif opc == "5":
            print("\n¡Éxitos en tu presentación de mañana!")
            break

if __name__ == "__main__":
    main()
