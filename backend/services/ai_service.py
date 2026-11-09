import os
import base64
import requests
from typing import Optional

# Modelos optimizados para alta velocidad y costo cero en Google AI Studio
PRIMARY_MODEL = "gemini-flash-lite-latest"
FALLBACK_MODELS = ["gemini-3.8-flash", "gemini-flash-latest"]

class GeminiService:
    @staticmethod
    def get_api_key() -> str:
        # Cargar directamente de variable de entorno o archivo .env si no está cargada
        api_key = os.getenv("GEMINI_API_KEY", "").strip()
        if not api_key:
            env_file = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".env")
            if os.path.exists(env_file):
                try:
                    with open(env_file, "r", encoding="utf-8") as f:
                        for line in f:
                            if line.startswith("GEMINI_API_KEY="):
                                api_key = line.split("=", 1)[1].strip()
                                os.environ["GEMINI_API_KEY"] = api_key
                                break
                except Exception:
                    pass
        return api_key

    @classmethod
    def call_gemini(cls, parts: list) -> str:
        api_key = cls.get_api_key()
        if not api_key or api_key == "PEGA_AQUI_TU_API_KEY":
            return "El servicio de Inteligencia Artificial no tiene configurada una clave válida de Gemini. Por favor configure GEMINI_API_KEY."

        candidate_models = [PRIMARY_MODEL] + FALLBACK_MODELS
        headers = {"Content-Type": "application/json"}
        payload = {
            "contents": [{
                "parts": parts
            }],
            "generationConfig": {
                "temperature": 0.2,
                "maxOutputTokens": 300
            }
        }

        last_error = ""
        for model in candidate_models:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
            try:
                response = requests.post(url, json=payload, headers=headers, timeout=12)
                if response.status_code == 200:
                    data = response.json()
                    candidates = data.get("candidates", [])
                    if candidates:
                        content = candidates[0].get("content", {})
                        resp_parts = content.get("parts", [])
                        if resp_parts:
                            return resp_parts[0].get("text", "").strip()
                elif response.status_code == 429:
                    last_error = "Se alcanzó el límite de solicitudes gratuitas por minuto. Intente de nuevo en un momento."
                    continue
                else:
                    last_error = f"Código {response.status_code} en modelo {model}."
                    continue
            except requests.exceptions.Timeout:
                last_error = "Tiempo de espera agotado al consultar a la IA."
                continue
            except Exception as e:
                last_error = f"Error de conexión: {str(e)}"
                continue

        return f"No se pudo completar la consulta de IA ({last_error})."

    @classmethod
    def summarize_text(cls, text: str) -> str:
        """
        Sintetiza un mensaje largo o transcripción en 1 o 2 oraciones para personas con discapacidad visual.
        """
        prompt = (
            "Eres un asistente de accesibilidad de la Unión Nacional de Ciegos del Perú (UNCP). "
            "Resume el siguiente mensaje en máximo 2 oraciones directas y claras para ser leídas por voz, "
            "priorizando fechas, horas, nombres o acciones clave:\n\n"
            f"\"{text}\""
        )
        return cls.call_gemini([{"text": prompt}])

    @classmethod
    def describe_image(cls, image_bytes_or_b64: str, mime_type: str = "image/jpeg") -> str:
        """
        Genera una descripción detallada y lectura de texto OCR de una imagen para personas ciegas.
        """
        # Si ya viene en base64 limpio
        clean_b64 = image_bytes_or_b64
        if clean_b64.startswith("data:image"):
            clean_b64 = clean_b64.split(",", 1)[1]

        prompt = (
            "Eres un asistente de visión accesible para personas con ceguera total de la UNCP. "
            "Describe de forma concisa pero completa qué hay en la imagen (personas, objetos, entorno) "
            "y lee textualmente cualquier letrero, afiche, documento o texto legible que contenga."
        )

        parts = [
            {"text": prompt},
            {
                "inline_data": {
                    "mime_type": mime_type,
                    "data": clean_b64
                }
            }
        ]
        return cls.call_gemini(parts)

    @classmethod
    def refine_speech_text(cls, raw_text: str) -> str:
        """
        Agrega puntuación, mayúsculas y formato correcto a textos dictados por voz.
        """
        prompt = (
            "El siguiente texto fue dictado por voz por una persona invidente y carece de puntuación y mayúsculas. "
            "Agrega signos de puntuación adecuados (comas, puntos, signos de interrogación o exclamación) y "
            "corrige errores tipográficos evidentes sin cambiar el significado ni agregar palabras innecesarias. "
            "Devuelve estrictamente el texto corregido:\n\n"
            f"\"{raw_text}\""
        )
        return cls.call_gemini([{"text": prompt}])
