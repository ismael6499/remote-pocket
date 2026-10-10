# Project Rules & Customizations - Remote Pocket

1. **Identidad de Usuario e Interacción**:
   - El usuario es **Agustín Acosta** (`acostaagustin6499@gmail.com`).
   - Dirigirse siempre a él de forma directa y amigable en español rioplatense (*vos*, *tu*).
   - NUNCA dirigirse como formal (*usted*) y NUNCA llamarlo Breto (ex-jefe).

2. **Commitear cada cambio exitoso**:
   - Realizar un commit de Git local de manera secuencial e independiente por cada funcionalidad, corrección o endpoint nuevo implementado y verificado.
   - Mantener el historial limpio y granular.
   - Configuración de identidad local:
     ```powershell
     git config user.name "Agustin"
     git config user.email "acostaagustin6499@gmail.com"
     ```
   - Usar backdating con segundos/minutos no redondos cuando se creen commits retrospectivos:
     ```powershell
     $env:GIT_AUTHOR_DATE="2026-10-10T13:20:14-03:00"; $env:GIT_COMMITTER_DATE="2026-10-10T13:20:14-03:00"; git commit -m "feat: message"
     Remove-Item Env:\GIT_AUTHOR_DATE
     Remove-Item Env:\GIT_COMMITTER_DATE
     ```

3. **Restricción Estricta de Git Push en Horario Laboral**:
   - **NUNCA realizar `git push` a repositorios remotos/públicos durante el horario laboral** (Lunes a Viernes de 09:00 a 18:00 ART / UTC-3).
   - Se permite trabajar y realizar commits locales con total normalidad, pero cualquier comando `git push` hacia GitHub o remotos debe esperar a estar fuera del horario laboral o en fin de semana.

4. **Flujo de Ejecución y Tareas de Fondo en Antigravity**:
   - El agente de IA **NO** debe dejar el servidor corriendo como una tarea daemon en segundo plano dentro de Antigravity (`IsDaemon: true`).
   - El servidor de la PC se ejecuta externamente mediante sus scripts de arranque (`start.bat` o `install.bat`) o la consola del usuario.
   - Si se genera cualquier tarea accidental en segundo plano, cancelarla inmediatamente con `manage_task (kill)`.

5. **Idioma por Defecto para UI e Internacionalización (i18n)**:
   - Todo texto de interfaz de usuario, etiquetas, botones, diálogos, toasts y descripciones debe estar en **inglés por defecto**.
   - La arquitectura debe tener internacionalización centralizada:
     - Android: `res/values/strings.xml` (inglés por defecto) y `res/values-es/strings.xml` (español como traducción primaria).
     - PC Agent: Diccionarios o constantes centralizadas en inglés con traducción.
   - NUNCA hardcodear textos visibles en la UI directamente en archivos de layout XML ni en clases Java/JS.

6. **Protocolo de Handoff & Estado**:
   - Mantener actualizado el archivo `HANDOFF.md` en la raíz del proyecto para registrar:
     - Estado actual del desarrollo.
     - Funcionalidades implementadas y verificadas.
     - Tareas pendientes inmediatas y próximos pasos.
     - Instrucciones necesarias para que cualquier nuevo agente o sesión continúe fluidamente sin perder contexto.

7. **Distribución Automática de APKs a Mobile Drop**:
   - Cada vez que se compile o genere el APK de Android (`app-debug.apk` o release), DEBE copiarse automáticamente tanto a `pc-agent/updates/app-release.apk` (para actualizaciones OTA) como a la carpeta de descargas de Express Gateway: `C:\Users\agust\Documents\Mobile Drop\RemotePocket-v<version>.apk` y `RemotePocket.apk`.
   - Está automatizado en Gradle (`copyToMobileDrop` en `android-app/app/build.gradle`) para que ocurra en cada `assemble`.
