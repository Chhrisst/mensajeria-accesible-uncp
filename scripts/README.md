# Scripts de Automatización y Verificación de Entorno

Esta carpeta contiene utilidades en lote (`.bat`) para facilitar el trabajo en equipo, la compilación y el control de versiones en Windows sin necesidad de recordar comandos complejos de Git o Gradle.

---

### 1. `1_verificar_entorno.bat`
* **¿Qué hace?**
  1. Verifica que Git esté instalado.
  2. Verifica que Java/JDK (versión 17 o superior) esté disponible.
  3. Verifica la existencia de `local.properties`. Si no existe, detecta automáticamente la ruta del Android SDK en la PC y crea el archivo.
  4. Prueba la ejecución del Gradle Wrapper (8.13).
  5. Ejecuta una compilación limpia de prueba (`assembleDebug`) para certificar que la computadora está lista para trabajar.

### 2. `2_gestionar_ramas.bat`
* **¿Qué hace?**
  1. Muestra la rama activa actual y la lista de ramas locales.
  2. Permite volver a la rama principal (`main`) y sincronizar los últimos cambios con `git pull`.
  3. Permite crear una nueva rama de trabajo (por ejemplo `feature/nuevo-modulo`) partiendo de `main` actualizado.
  4. Permite cambiar de rama fácilmente.

### 3. `3_subir_cambios.bat`
* **¿Qué hace?**
  1. Detecta qué archivos has modificado o agregado.
  2. **Primera confirmación:** Te pide confirmar si deseas crear un commit para la rama actual e ingresar un mensaje descriptivo.
  3. **Segunda confirmación crítica (Doble confirmación):** Antes de enviar el código a GitHub, te vuelve a pedir confirmación explícita para evitar subir cambios por error o sobreescribir código en la rama equivocada.
