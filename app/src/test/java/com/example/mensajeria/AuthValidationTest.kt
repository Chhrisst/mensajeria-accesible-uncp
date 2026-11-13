package com.example.mensajeria

import com.example.mensajeria.ui.viewmodel.AuthViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {

    @Test
    fun emailValidation() {
        assertNull(AuthViewModel.validateEmail("ana@dominio.com"))
        assertNotNull(AuthViewModel.validateEmail(""))
        assertNotNull(AuthViewModel.validateEmail("ana@dominio"))
        assertNotNull(AuthViewModel.validateEmail("ana dominio.com"))
    }

    @Test
    fun passwordValidation() {
        assertNull(AuthViewModel.validatePassword("secreto1"))
        assertNotNull(AuthViewModel.validatePassword("12345"))
        assertNotNull(AuthViewModel.validatePassword("       "))
    }

    @Test
    fun userOrEmailValidation() {
        assertNull(AuthViewModel.validateUserOrEmail("christian"))
        assertNull(AuthViewModel.validateUserOrEmail("ana@dominio.com"))
        assertNotNull(AuthViewModel.validateUserOrEmail("ana@"))
        assertNotNull(AuthViewModel.validateUserOrEmail("   "))
    }

    @Test
    fun usernameValidation() {
        assertNull(AuthViewModel.validateUsername("carlos20"))
        assertNull(AuthViewModel.validateUsername("@maria_lopez"))
        assertNull(AuthViewModel.validateUsername("ana_123"))
        assertNotNull(AuthViewModel.validateUsername(""))
        assertNotNull(AuthViewModel.validateUsername("ab")) // menor a 3 caracteres
        assertNotNull(AuthViewModel.validateUsername("user name")) // espacios no permitidos
        assertNotNull(AuthViewModel.validateUsername("carlos!@#")) // caracteres especiales no permitidos
    }
}
