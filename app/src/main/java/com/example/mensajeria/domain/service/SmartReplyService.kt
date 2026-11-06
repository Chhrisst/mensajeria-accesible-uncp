package com.example.mensajeria.domain.service

/**
 * Servicio de Inteligencia Artificial para la generación de respuestas rápidas contextuales (Smart Replies).
 *
 * Impacta directamente en el indicador de investigación: "Tiempo de respuesta comunicativa".
 * Permite que una persona con discapacidad visual severa responda en menos de 3 segundos
 * con un solo toque o comando sobre la sugerencia inteligente.
 */
class SmartReplyService {

    /**
     * Genera 3 sugerencias contextualmente relevantes basadas en el mensaje entrante.
     */
    fun generateReplies(incomingMessage: String): List<String> {
        val lower = incomingMessage.lowercase()

        return when {
            // Preguntas de confirmación o asistencia (como en la UNCP)
            lower.contains("confirma") || lower.contains("asistencia") || lower.contains("asistir") || lower.contains("vienes") -> {
                listOf(
                    "Sí, confirmo mi asistencia",
                    "No podré asistir hoy",
                    "Llegaré con 15 minutos de retraso"
                )
            }

            // Preguntas de ubicación o estado de llegada
            lower.contains("dónde") || lower.contains("donde") || lower.contains("llegaste") || lower.contains("camino") || lower.contains("ubicación") -> {
                listOf(
                    "Ya estoy en camino a la UNCP",
                    "Estoy en la entrada principal",
                    "Todavía estoy en casa"
                )
            }

            // Preguntas de tiempo u hora
            lower.contains("a qué hora") || lower.contains("que hora") || lower.contains("cuándo") || lower.contains("cuando") || Regex("""\bhora\b""").containsMatchIn(lower) -> {
                listOf(
                    "A las 3:00 de la tarde",
                    "En una hora estaré libre",
                    "Dime tú a qué hora prefieres"
                )
            }

            // Saludos o inicio de conversación
            lower.contains("hola") || lower.contains("buenos días") || lower.contains("buenas tardes") -> {
                listOf(
                    "Hola, ¿cómo estás?",
                    "Buenos días, un gusto saludarte",
                    "Hola, ¿en qué te puedo ayudar?"
                )
            }

            // Preguntas de acuerdo / confirmación general
            lower.contains("te parece") || lower.contains("estás de acuerdo") || lower.contains("puedes") -> {
                listOf(
                    "Sí, me parece perfecto",
                    "Prefiero que lo coordinemos luego",
                    "Por favor llámame por teléfono"
                )
            }

            // Respuestas generales contextuales
            else -> {
                listOf(
                    "Entendido, muchas gracias",
                    "Por favor envíame más detalles",
                    "Te respondo en unos minutos"
                )
            }
        }
    }
}
