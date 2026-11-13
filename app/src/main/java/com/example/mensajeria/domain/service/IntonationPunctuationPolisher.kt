package com.example.mensajeria.domain.service

/**
 * Formateador inteligente de entonación, intención y puntuación para dictado por voz en español.
 * Convierte transcripciones crudas del STT (como "Hola Que Tal como estas") en texto expresivo
 * y bien puntuado ("Hola, qué tal. ¿Cómo estás?").
 */
object IntonationPunctuationPolisher {

    private val accentMap = mapOf(
        "que" to "qué",
        "como" to "cómo",
        "cuando" to "cuándo",
        "donde" to "dónde",
        "cuanto" to "cuánto",
        "cuantos" to "cuántos",
        "cuanta" to "cuánta",
        "cuantas" to "cuántas",
        "cual" to "cuál",
        "quien" to "quién",
        "quienes" to "quiénes",
        "estas" to "estás",
        "esta" to "está",
        "estara" to "estará",
        "estaras" to "estarás",
        "dias" to "días",
        "dia" to "día",
        "adios" to "adiós",
        "tambien" to "también",
        "despues" to "después",
        "asi" to "así",
        "mas" to "más",
        "telefonico" to "telefónico",
        "telefono" to "teléfono",
        "numero" to "número",
        "rapido" to "rápido",
        "academico" to "académico",
        "reunion" to "reunión",
        "informacion" to "información",
        "cancion" to "canción",
        "atencion" to "atención",
        "perdon" to "perdón",
        "ira" to "irá",
        "iras" to "irás",
        "sera" to "será",
        "seras" to "serás",
        "podras" to "podrás",
        "tendra" to "tendrá",
        "tendras" to "tendrás"
    )

    private val greetingPrefixes = listOf(
        "hola que tal",
        "hola qué tal",
        "buenos dias",
        "buenos días",
        "buenas tardes",
        "buenas noches",
        "hola amigo",
        "hola amiga",
        "hola"
    )

    private val questionStarters = listOf(
        "como estas", "cómo estás", "como te va", "cómo te va", "como has estado", "cómo has estado",
        "que tal", "qué tal", "que haces", "qué haces", "que paso", "qué pasó",
        "que hora es", "qué hora es", "a que hora", "a qué hora", "en cuantos grados", "en cuántos grados",
        "donde estas", "dónde estás", "cuando vienes", "cuándo vienes", "cuando vas", "cuándo vas",
        "cuantos grados", "cuántos grados", "cual es", "cuál es", "quien es", "quién es",
        "por que", "por qué", "para que", "para qué",
        "puedes", "puedo", "tienes", "sabes", "quieres", "vas a", "vienes", "estas ahi", "estás ahí",
        "me escuchas", "todo bien", "me entiendes"
    )

    private val exclamativePhrases = listOf(
        "muchas gracias", "te quiero mucho", "feliz cumpleanos", "feliz cumpleaños",
        "felicidades", "que bueno", "qué bueno", "que bien", "qué bien",
        "excelente", "genial", "cuidado", "urgente", "auxilio", "ayuda"
    )

    fun polish(rawText: String): String {
        val collapsed = rawText.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isBlank()) return ""

        // Si ya contiene signos explícitos de interrogación/exclamación puestos por Gemini, solo limpiar espacios y mayúscula inicial
        if (collapsed.contains("¿") || collapsed.contains("¡")) {
            return normalizeSentenceCase(collapsed)
        }

        // Pasar a minúsculas base si el STT metió mayúsculas en cada palabra ("Hola Que Tal")
        var working = collapsed.lowercase()

        // Aplicar tildes clave cuando aparecen como interrogativos o palabras agudas comunes
        working = applyContextualAccents(working)

        // Separar saludo inicial + pregunta/oración ("hola, qué tal. ¿cómo estás?")
        var prefixSegment = ""
        for (greeting in greetingPrefixes) {
            if (working == greeting) {
                prefixSegment = capitalizeFirst(greeting)
                working = ""
                break
            } else if (working.startsWith("$greeting ")) {
                val remainder = working.removePrefix(greeting).trim()
                if (greeting.contains("que tal") || greeting.contains("qué tal")) {
                    prefixSegment = "Hola, qué tal."
                } else {
                    val punct = if (remainder.startsWith("¿")) "." else ","
                    prefixSegment = capitalizeFirst(greeting) + punct
                }
                working = remainder
                break
            }
        }

        val bodyFormatted = if (working.isNotBlank()) {
            formatBodyClause(working)
        } else {
            ""
        }

        val combined = when {
            prefixSegment.isNotBlank() && bodyFormatted.isNotBlank() -> {
                if (prefixSegment.endsWith(",")) {
                    val loweredOrQuestion = if (bodyFormatted.startsWith("¿") || bodyFormatted.startsWith("¡")) {
                        bodyFormatted.replaceFirstChar { it.lowercase() }.let {
                            if (bodyFormatted.startsWith("¿")) "¿" + bodyFormatted.drop(1).replaceFirstChar { c -> c.lowercase() }
                            else bodyFormatted
                        }
                    } else {
                        bodyFormatted.replaceFirstChar { it.lowercase() }
                    }
                    "$prefixSegment $loweredOrQuestion"
                } else {
                    "$prefixSegment $bodyFormatted"
                }
            }
            prefixSegment.isNotBlank() -> {
                if (prefixSegment.endsWith(".") || prefixSegment.endsWith("?") || prefixSegment.endsWith("!")) prefixSegment
                else "$prefixSegment."
            }
            else -> bodyFormatted
        }

        return normalizeSentenceCase(combined)
    }

    private fun applyContextualAccents(text: String): String {
        val tokens = text.split(" ").toMutableList()
        for (i in tokens.indices) {
            val clean = tokens[i].trim(',', '.', '?', '!', '¿', '¡')
            val suffix = tokens[i].substring(clean.length)
            val prev = if (i > 0) tokens[i - 1].trim(',', '.', '?', '!') else ""
            val next = if (i + 1 < tokens.size) tokens[i + 1].trim(',', '.', '?', '!') else ""

            val replacement = when (clean) {
                "que" -> if (i == 0 || prev in listOf("hola", "y", "pero", "dime", "o", "para", "por") || next in listOf("tal", "hora", "haces", "paso", "pasó", "es", "bueno", "bien")) "qué" else "que"
                "como" -> if (i == 0 || prev in listOf("hola", "y", "dime", "o") || next in listOf("estas", "estás", "te", "esta", "está", "has", "se", "va")) "cómo" else "como"
                "cuando" -> if (i == 0 || prev in listOf("dime", "sabes", "y") || next in listOf("vienes", "vas", "sera", "será", "es")) "cuándo" else "cuando"
                "donde" -> if (i == 0 || prev in listOf("dime", "sabes", "a", "de", "en") || next in listOf("estas", "estás", "esta", "está", "queda", "es")) "dónde" else "donde"
                "esta" -> if (next in listOf("bien", "mal", "aqui", "aquí", "alli", "allí", "listo", "lista", "ocupado") || i == tokens.lastIndex) "está" else "esta"
                else -> accentMap[clean] ?: clean
            }
            tokens[i] = replacement + suffix
        }
        return tokens.joinToString(" ")
    }

    private fun formatBodyClause(clause: String): String {
        val trimmed = clause.trim()
        if (trimmed.isBlank()) return ""

        val isQuestion = questionStarters.any { trimmed.startsWith(it) || trimmed == it } ||
            trimmed.startsWith("¿") || trimmed.endsWith("?") ||
            Regex("^(?:(?:a|en|de|con|para|por)\\s+)?(qué|cómo|cuándo|dónde|cuánto|cuántos|cuál|quién)\\b").containsMatchIn(trimmed)

        if (isQuestion) {
            val inner = trimmed.trim('¿', '?', '.', ' ').let { capitalizeFirst(it) }
            return "¿$inner?"
        }

        // Si la frase no inicia ya como pregunta, buscar si termina con una pregunta (ej: "estoy llegando, como estas")
        for (starter in questionStarters) {
            val idx = trimmed.indexOf(starter)
            if (idx > 0 && trimmed[idx - 1] == ' ') {
                val firstPart = trimmed.substring(0, idx).trim().trimEnd(',', '.')
                val secondPart = trimmed.substring(idx).trim().trimEnd('?', '.')
                if (firstPart.isNotBlank() && secondPart.isNotBlank()) {
                    return "${capitalizeFirst(firstPart)}. ¿${capitalizeFirst(secondPart)}?"
                }
            }
        }

        val isExclamation = exclamativePhrases.any { trimmed == it || trimmed.startsWith("$it ") } ||
            trimmed.startsWith("¡") || trimmed.endsWith("!")
        if (isExclamation) {
            val inner = trimmed.trim('¡', '!', '.', ' ').let { capitalizeFirst(it) }
            return "¡$inner!"
        }

        // Insertar coma antes de conectores adversativos o vocativos comunes ("pero", "por favor", "gracias")
        var withCommas = trimmed
            .replace(Regex("\\s+pero\\s+"), ", pero ")
            .replace(Regex("\\s+por favor$"), ", por favor")
            .replace(Regex("\\s+gracias$"), ", gracias")

        withCommas = capitalizeFirst(withCommas.trimEnd('.'))
        return if (withCommas.endsWith(".") || withCommas.endsWith("?") || withCommas.endsWith("!")) {
            withCommas
        } else {
            "$withCommas."
        }
    }

    private fun normalizeSentenceCase(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return ""
        val result = StringBuilder(clean.length)
        var capitalizeNext = true
        for (ch in clean) {
            if (capitalizeNext && ch.isLetter()) {
                result.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                result.append(ch)
                if (ch == '.' || ch == '?' || ch == '!') {
                    capitalizeNext = true
                } else if (ch != ' ' && ch != '¿' && ch != '¡' && !ch.isLetter()) {
                    // otros símbolos mantienen el estado actual
                } else if (ch.isLetter()) {
                    capitalizeNext = false
                }
            }
        }
        return result.toString()
    }

    private fun capitalizeFirst(s: String): String {
        if (s.isEmpty()) return s
        val idx = s.indexOfFirst { it.isLetter() }
        if (idx == -1) return s
        return s.substring(0, idx) + s[idx].uppercaseChar() + s.substring(idx + 1)
    }
}
