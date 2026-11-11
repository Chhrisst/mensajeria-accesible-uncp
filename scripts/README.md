# Scripts de Automatización y Verificación de Entorno

Esta carpeta contiene utilidades en lote (`.bat`) para facilitar el trabajo en equipo, la compilación y el control de versiones en Windows sin necesidad de recordar comandos complejos de Git o Gradle.

---

### 0. `configurar_servidor_y_api.bat` (Configuración de Credenciales)
* **¿Qué hace?**
  1. Permite ingresar o actualizar la **IP del servidor VPS** y la **clave de Google Gemini** sin tocar código.
  2. Guarda la configuración en `local.properties` (y `backend/.env`).
  3. Si solo escribes la IP (ej. `192.168.1.100`), la formatea automáticamente a `http://192.168.1.100:8000/`.
  4. Muestra los valores actuales y permite reemplazarlos si se vuelve a abrir.

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
  5. **Eliminar ramas con doble confirmación:** Permite borrar ramas locales que ya no se usen con doble verificación de seguridad, impidiendo por regla borrar la rama `main` o la rama activa actual.

### 3. `3_subir_cambios.bat`
* **¿Qué hace?**
  1. Detecta qué archivos has modificado o agregado.
  2. **Primera confirmación:** Te pide confirmar si deseas crear un commit para la rama actual e ingresar un mensaje descriptivo.
  3. **Segunda confirmación crítica (Doble confirmación):** Antes de enviar el código a GitHub, te vuelve a pedir confirmación explícita para evitar subir cambios por error o sobreescribir código en la rama equivocada.

### 4. `4_sincronizar_main.bat` (Verificar y Descargar Actualizaciones)
* **¿Qué hace?**
  1. Consulta a GitHub (`git fetch origin main`) sin alterar tus archivos locales de trabajo.
  2. Compara tu copia local con `origin/main` y te dice cuántos commits nuevos existen.
  3. Si hay cambios en GitHub, te muestra la lista de commits nuevos y te pregunta si deseas descargarlos (`git pull origin main`).
  4. Si tu copia ya está actualizada, te confirma que todo está al día.

### 5. `iniciar_claude_code.bat` (Asistente de Desarrollo con Claude Code)
* **¿Qué hace?**
  1. Verifica que Claude Code (`@anthropic-ai/claude-code`) esté disponible en el sistema.
  2. Inicia la sesión interactiva de Claude Code en la raíz del proyecto.
  3. Proporciona el flujo para autenticarse con cuenta de Anthropic (Claude Pro / Team / API Key) en el navegador.
  4. Claude Code lee automáticamente `CLAUDE.md` con las instrucciones de arquitectura, comandos de Gradle y directrices de accesibilidad.
