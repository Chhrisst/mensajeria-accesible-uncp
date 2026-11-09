# 🚀 Guía de Despliegue en VPS Oracle Cloud (PostgreSQL + FastAPI + Adminer)

Esta guía explica cómo desplegar la arquitectura completa de la **Mensajería Inclusiva UNCP** en tu VPS de Oracle Cloud:
- **🐘 Base de Datos:** PostgreSQL 16
- **⚡ Backend API:** Python FastAPI (puerto 8000)
- **🖥️ Gestor Web:** Adminer (puerto 8086)

---

## 1. Abrir el puerto 8000 para la API REST

Para que la app Android se comunique con la API de tu VPS, habilitamos el puerto **8000**:

### A) En la Consola Web de Oracle Cloud (Security List)
1. Ve a **Compute** > **Instances** > Clic en tu instancia > Clic en tu **VCN** > **Security Lists**.
2. Clic en **Agregar reglas de entrada**:
   - **CIDR de origen:** `0.0.0.0/0`
   - **Protocolo IP:** `TCP`
   - **Rango de puertos de destino:** `8000`
   - **Descripción:** `FastAPI Backend Mensajeria UNCP`
3. Clic en **Agregar reglas de entrada**.

### B) En la Terminal de tu VPS (Firewall de Ubuntu)
Ejecuta:
```bash
sudo iptables -I INPUT -p tcp --dport 8000 -j ACCEPT
sudo netfilter-persistent save
```

---

## 2. Subir los archivos del Backend a tu VPS

Desde PowerShell en tu computadora:
```powershell
scp -i "tu_clave.key" -r ".\backend\*" ubuntu@TU_IP_VPS_ORACLE:~/mensajeria-db/
```

O si prefieres, conéctate por SSH y clona/actualiza los archivos en `~/mensajeria-db`.

---

## 3. Construir y Levantar el Backend y la Base de Datos

En la terminal de tu VPS:
```bash
cd ~/mensajeria-db
docker compose up -d --build
```

Docker descargará las imágenes, compilará el contenedor de FastAPI y levantará los 3 servicios:
```bash
docker compose ps
```
Verás:
- `uncp_postgres_db` (Up - healthy)
- `uncp_backend_api` (Up)
- `uncp_db_adminer` (Up)

---

## 4. Probar la API y Documentación Interactiva (Swagger UI)

Abre en tu navegador web:
👉 **`http://TU_IP_VPS_ORACLE:8000/docs`**

FastAPI genera automáticamente una interfaz web interactiva donde tú y tus compañeros pueden:
- Probar el inicio de sesión (`POST /api/auth/login`)
- Registrar nuevos usuarios (`POST /api/auth/register`)
- Consultar los usuarios y mensajes en vivo

---

## 5. Conexión de la App Android

La app Android incluye la biblioteca de red **Retrofit** configurada dinámicamente mediante `local.properties`:
```properties
backend.base.url=http://TU_IP_VPS_ORACLE:8000/
```

Cuando un usuario inicia sesión o se registra desde la app en el celular:
1. La app envía la petición a la API FastAPI en tu VPS.
2. FastAPI valida y almacena los datos en **PostgreSQL**.
3. Si el celular se queda sin señal o internet, la app cuenta con **soporte offline** para no bloquear la experiencia del usuario con discapacidad.
