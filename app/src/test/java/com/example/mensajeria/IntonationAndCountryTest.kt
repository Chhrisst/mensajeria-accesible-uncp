package com.example.mensajeria

import com.example.mensajeria.data.model.CountryPhoneCatalog
import com.example.mensajeria.domain.service.IntonationPunctuationPolisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntonationAndCountryTest {

    @Test
    fun polisher_formatsGreetingAndQuestionWithIntonation() {
        val formatted = IntonationPunctuationPolisher.polish("Hola Que Tal como estas")
        assertEquals("Hola, qué tal. ¿Cómo estás?", formatted)
    }

    @Test
    fun polisher_formatsStandaloneQuestionWithSignsAndAccent() {
        val formatted = IntonationPunctuationPolisher.polish("a que hora vienes hoy")
        assertEquals("¿A qué hora vienes hoy?", formatted)
    }

    @Test
    fun polisher_formatsDeclarativeSentenceWithPeriod() {
        val formatted = IntonationPunctuationPolisher.polish("estoy llegando a la universidad")
        assertEquals("Estoy llegando a la universidad.", formatted)
    }

    @Test
    fun countryCatalog_hasWorldCountriesAndPeru9Digits() {
        assertTrue(CountryPhoneCatalog.allCountries.size >= 150)
        val peru = CountryPhoneCatalog.findByIso("PE")
        assertEquals("+51", peru.dialCode)
        assertEquals(9, peru.maxDigits)
    }
}
