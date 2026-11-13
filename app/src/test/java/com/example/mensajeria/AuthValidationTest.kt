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
}
