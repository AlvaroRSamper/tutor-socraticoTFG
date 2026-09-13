    const inputElement = document.getElementById('inputMensaje');
    const mensajesDiv = document.getElementById('mensajes');
    let temaActual = "General";
    let contadorMensajes = 0;

    function leerCookie(nombre) {
        const match = document.cookie.match('(^|;)\\s*' + nombre + '\\s*=\\s*([^;]+)');
        return match ? decodeURIComponent(match[2]) : null;
    }

    function cabecerasConCsrf(cabecerasExtra) {
        return Object.assign({ 'X-XSRF-TOKEN': leerCookie('XSRF-TOKEN') }, cabecerasExtra || {});
    }

    const TEMAS_IDE = ['github-dark', 'dracula', 'monokai', 'one-dark'];

    // Aplica un tema de IDE como clase del <body>, sin pisar otras clases (ej. body-profesor).
    function aplicarTemaIde(nombre) {
        const tema = TEMAS_IDE.includes(nombre) ? nombre : 'github-dark';
        document.body.classList.remove(...TEMAS_IDE.map(t => 'theme-' + t));
        document.body.classList.add('theme-' + tema);
    }

    function aplicarConfiguracionYTemas(data) {
        if (!data) return;
        // El historial se guarda por usuario, para que cada cuenta vea SOLO su conversación
        // (evita que en un mismo navegador se arrastre el historial de otra cuenta).
        if (data.username && data.username.trim() !== "") {
            claveHistorial = 'historial_tutor_' + data.username.trim();
        }
        if (data.titulo && data.titulo.trim() !== "") {
            document.title = data.titulo + ' · Tutor Socrático';
            const h1 = document.getElementById('titulo-chat');
            if (h1) h1.innerText = data.titulo;
        }
        if (data.colorTema && data.colorTema.trim() !== "") {
            aplicarTemaIde(data.colorTema);
        }
        const listaTemas = document.getElementById('lista-temas');
        if (listaTemas) {
            listaTemas.querySelectorAll('.topic-item:not(.general-topic)').forEach(el => el.remove());
            if (data.temas && data.temas.trim() !== "") {
                const temasArray = data.temas.split(',');
                temasArray.forEach((archivo, indice) => {
                    const nom = archivo.trim();
                    if (nom) {
                        const li = document.createElement('li');
                        li.className = 'topic-item';
                        li.setAttribute('data-file', nom);
                        li.onclick = () => seleccionarTema(li);
                        li.innerText = (indice + 1) + ". " + nom.replace(/\.[^/.]+$/, "");
                        listaTemas.appendChild(li);
                    }
                });
            }
        }
        const selectModal = document.getElementById('select-tema-modal');
        if (selectModal) {
            selectModal.innerHTML = '';
            document.querySelectorAll('.topic-item').forEach(li => {
                const file = li.getAttribute('data-file');
                const nombre = li.innerText;
                const option = document.createElement('option');
                option.value = file;
                option.text = nombre;
                selectModal.appendChild(option);
            });
        }
        if (typeof cargarMisConocimientos === 'function') cargarMisConocimientos();
    }

    async function hacerLogin() {
        const id = document.getElementById('login-id').value.trim();
        const pass = document.getElementById('login-pass').value.trim();
        const errorEl = document.getElementById('login-error');
        errorEl.style.display = 'none';

        if (!leerCookie('XSRF-TOKEN')) {
            try { await fetch('/', { cache: 'no-store' }); } catch (e) { }
        }

        try {
            const respuesta = await fetch('/login', {
                method: 'POST',
                headers: cabecerasConCsrf({ 'Content-Type': 'application/x-www-form-urlencoded' }),
                body: new URLSearchParams({ username: id, password: pass })
            });

            if (respuesta.ok) {
                const json = await respuesta.json().catch(() => ({}));
                if (json.rol === 'PROFESOR') {
                    window.location.href = '/profesor.html';
                    return;
                }
                const chk = await fetch('/api/tutor/temas');
                const dataChk = await chk.json().catch(() => ({}));
                if (dataChk.error === "ACCESO_NO_LTI") {
                    document.getElementById('pantalla-login').innerHTML = `
                        <div class="login-box" style="max-width: 500px; text-align: center;">
                            <img src="logo-uma.png" alt="Logo UMA" class="login-logo">
                            <h2 style="color: #f85149;">Acceso Restringido</h2>
                            <p style="color: #c9d1d9; font-size: 0.95rem; line-height: 1.5; margin: 20px 0;">
                                El Tutor Socrático está integrado exclusivamente con la plataforma universitaria.
                                <br><br>
                                <strong>Por favor, entra a la aplicación haciendo clic en el enlace oficial dentro de tu asignatura en el Campus Virtual (Moodle).</strong>
                            </p>
                            <div style="margin-top: 30px; border-top: 1px solid #30363d; padding-top: 20px;">
                                <a href="javascript:void(0)" onclick="location.reload()" style="color: #58a6ff; font-size: 0.85rem; text-decoration: none;">¿Ya estás en Moodle? Reintentar</a>
                                <br><br>
                                <span style="font-size: 0.75rem; color: #8b949e;">¿Eres docente o administrador? <a href="javascript:void(0)" onclick="mostrarLoginManual()" style="color: #8b949e; text-decoration: underline;">Acceso manual</a></span>
                            </div>
                        </div>
                    `;
                    document.getElementById('pantalla-login').style.display = 'flex';
                    return;
                }
                document.getElementById('pantalla-login').style.display = 'none';
                location.reload();
            } else {
                const json = await respuesta.json().catch(() => ({}));
                errorEl.innerText = json.mensaje || 'Usuario o contraseña incorrectos';
                errorEl.style.display = 'block';
            }
        } catch (error) {
            errorEl.innerText = 'Error de conexión con el servidor.';
            errorEl.style.display = 'block';
        }
    }

    async function cerrarSesion() {
        try {
            await fetch('/logout', { method: 'POST', headers: cabecerasConCsrf() });
        } catch (error) {
            console.error("Error cerrando sesión", error);
        }
        location.reload();
    }

    function mostrarPantallaLogin() {
        document.getElementById('pantalla-login').style.display = 'flex';
    }

    let editor = null;

    inputElement.addEventListener('input', function() {
        this.style.height = 'auto';
        this.style.height = this.scrollHeight + 'px';
    });

    let historial = [];
    // Clave de localStorage del historial; se refina a 'historial_tutor_<usuario>' al conocer el usuario.
    let claveHistorial = 'historial_tutor';
    window.mostrarLoginManual = () => {
        document.getElementById('pantalla-login').innerHTML = `
            <div class="login-box">
                <img src="logo-uma.png" alt="Logo UMA" class="login-logo">
                <h2>Tutor Socrático</h2>
                <p class="login-subtitulo">Introduce el usuario y la contraseña que te ha facilitado tu profesor/a.</p>
                <form id="form-login" onsubmit="event.preventDefault(); hacerLogin();">
                    <div class="form-group">
                        <label>Identificador:</label>
                        <input type="text" id="login-id" maxlength="5" inputmode="numeric" autocomplete="username" required>
                    </div>
                    <div class="form-group">
                        <label>Contraseña:</label>
                        <input type="password" id="login-pass" maxlength="64" autocomplete="current-password" required>
                    </div>
                    <p id="login-error" class="login-error" style="display:none;"></p>
                    <button type="submit" class="btn-confirm login-submit">Entrar</button>
                </form>
            </div>
        `;
    };

    window.onload = async () => {
        iniciarMascotaCafe();
        try {
            const res = await fetch('/api/tutor/temas');
            if (res.ok) {
                const data = await res.json();
                if (data.error === "ACCESO_NO_LTI") {
                    document.getElementById('pantalla-login').innerHTML = `
                        <div class="login-box" style="max-width: 500px; text-align: center;">
                            <img src="logo-uma.png" alt="Logo UMA" class="login-logo">
                            <h2 style="color: #f85149;">Acceso Restringido</h2>
                            <p style="color: #c9d1d9; font-size: 0.95rem; line-height: 1.5; margin: 20px 0;">
                                El Tutor Socrático está integrado exclusivamente con la plataforma universitaria.
                                <br><br>
                                <strong>Por favor, entra a la aplicación haciendo clic en el enlace oficial dentro de tu asignatura en el Campus Virtual (Moodle).</strong>
                            </p>
                            <div style="margin-top: 30px; border-top: 1px solid #30363d; padding-top: 20px;">
                                <a href="javascript:void(0)" onclick="location.reload()" style="color: #58a6ff; font-size: 0.85rem; text-decoration: none;">¿Ya estás en Moodle? Reintentar</a>
                                <br><br>
                                <span style="font-size: 0.75rem; color: #8b949e;">¿Eres docente o administrador? <a href="javascript:void(0)" onclick="mostrarLoginManual()" style="color: #8b949e; text-decoration: underline;">Acceso manual</a></span>
                            </div>
                        </div>
                    `;
                    document.getElementById('pantalla-login').style.display = 'flex';
                    return;
                }
                document.getElementById('pantalla-login').style.display = 'none';
                aplicarConfiguracionYTemas(data);
                if (await comprobarConsentimiento()) {
                    mostrarRachaDelDia(data.titulo);
                } else {
                    mostrarModalConsentimiento(data.titulo);
                }
            }
        } catch (e) {
            console.error("Error cargando temas", e);
        }

        const guardado = localStorage.getItem(claveHistorial);
        if (guardado) {
            try {
                const historialParseado = JSON.parse(guardado);
                if (historialParseado.length > 0) {
                    historial = historialParseado;
                    mensajesDiv.innerHTML = '';
                    historial.forEach(msg => {
                        agregarBurbujaInit(msg.content, msg.role);
                    });
                    hacerScrollAbajo();
                }
            } catch (e) {
                console.error("Error cargando historial", e);
            }
        }
    };

    function guardarHistorial() {
        localStorage.setItem(claveHistorial, JSON.stringify(historial));
    }

    function seleccionarTema(elemento) {
        document.querySelectorAll('.topic-item').forEach(el => el.classList.remove('active'));
        elemento.classList.add('active');
        
        temaActual = elemento.getAttribute('data-file');
        const nombreVisible = elemento.innerText.split('. ').pop().trim();
        
        // No actualizamos titulo-chat para que mantenga el título de la asignatura
        agregarAvisoContexto("Contexto RAG cambiado a: " + nombreVisible);
    }

    function reiniciarChat() {
        if(confirm("¿Estás seguro de que quieres reiniciar la conversación? Esto borrará el historial actual.")) {
            historial = [];
            guardarHistorial();
            mensajesDiv.innerHTML = `
                <div class="mensaje-wrapper bot-wrapper">
                    <div class="mensaje bot">
                        <p>¡Hola! Soy tu <strong>Tutor Socrático</strong>.</p>
                        <p>He cargado todos tus apuntes oficiales. Selecciona un tema en la barra lateral para centrar nuestro estudio, o pídeme generar un ejercicio práctico.</p>
                    </div>
                </div>
            `;
            document.querySelector('.topic-item.general-topic').click();
        }
    }

    function agregarAvisoContexto(texto) {
        const wrapper = document.createElement('div');
        wrapper.style = "text-align: center; margin: 15px 0;";
        wrapper.innerHTML = `<span style="background: color-mix(in srgb, var(--primary) 15%, transparent); border: 1px solid color-mix(in srgb, var(--primary) 30%, transparent); box-shadow: 0 2px 8px color-mix(in srgb, var(--primary) 15%, transparent); color: var(--primary); padding: 6px 16px; border-radius: 20px; font-size: 0.85rem; font-weight: 500;">${texto}</span>`;
        mensajesDiv.appendChild(wrapper);
        hacerScrollAbajo();
    }

    function manejarEnter(event) {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            enviarMensaje();
        }
    }

    async function enviarMensaje() {
        const textoUsuario = inputElement.value.trim();
        if (!textoUsuario) return;

        historial.push({ role: "user", content: textoUsuario });
        guardarHistorial();
        agregarBurbuja(textoUsuario, 'user');
        inputElement.value = '';
        inputElement.style.height = 'auto';

        const idUnico = 'msg-' + (++contadorMensajes);
        agregarBurbujaCargando(idUnico);

        try {
            const respuesta = await fetch('/api/tutor/chat', {
                method: 'POST',
                headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({
                    historial: historial,
                    tema: temaActual
                })
            });

            if (respuesta.status === 401) {
                mostrarPantallaLogin();
                historial.pop();
                document.getElementById(idUnico)?.closest('.mensaje-wrapper')?.remove();
                return;
            }

            const json = await respuesta.json();

            if (!respuesta.ok) {
                document.getElementById(idUnico).innerHTML = `<span style="color:#f85149;">${json.mensaje || "Error de conexión o del servidor."}</span>`;
                historial.pop();
                guardarHistorial();
                return;
            }

            historial.push({ role: "assistant", content: json.mensaje });
            guardarHistorial();

            const burbujaBot = document.getElementById(idUnico);
            if (burbujaBot) {
                marked.setOptions({ breaks: true });
                let mensajeLimpio = limpiarMensaje(json.mensaje);

                if (json.mostrarOpciones) {
                    renderizarOpcionesSideBySide(burbujaBot, mensajeLimpio);
                } else {
                    burbujaBot.innerHTML = marked.parse(mensajeLimpio);
                }
                renderizarValoracionBotones(burbujaBot, json.consultaId);
            }

            if (json.estancamiento && typeof mostrarToastEstancamiento === 'function') {
                mostrarToastEstancamiento(json.mensajeEstancamiento, json.avisoId);
            }
        } catch (error) {
            document.getElementById(idUnico).innerText = "Error de conexión.";
            historial.pop();
        }
        hacerScrollAbajo();
    }

    function limpiarMensaje(texto) {
        if (!texto) return "";
        let stateIdx = texto.indexOf("💾 Registro de Estado");
        if (stateIdx !== -1) {
            return texto.substring(0, stateIdx).trim();
        }
        return texto;
    }

    function renderizarOpcionesSideBySide(burbujaBot, mensaje) {
        let intro = "";
        let opcionA = "";
        let opcionB = "";

        // Usamos regex para capturar variaciones (Opción 1, Opción A, con o sin asteriscos)
        const regexA = /(?:^|\n)\s*(?:\*\*|###\s*)?Opci.n\s*[A1]/i;
        const regexB = /(?:^|\n)\s*(?:\*\*|###\s*)?Opci.n\s*[B2]/i;

        const matchA = mensaje.match(regexA);
        const matchB = mensaje.match(regexB);

        if (matchA && matchB && matchA.index < matchB.index) {
            intro = mensaje.substring(0, matchA.index).trim();
            opcionA = mensaje.substring(matchA.index, matchB.index).trim();
            opcionB = mensaje.substring(matchB.index).trim();
        } else {
            // Si la IA desobedece y no genera opciones A y B, simplemente mostramos el texto normal
            // SIN añadir los botones de preferencia, ya que no tendrían sentido.
            burbujaBot.innerHTML = marked.parse(mensaje);
            return;
        }

        // Modo comparación: la fila se ensancha para que las dos opciones
        // se lean cómodas a pantalla (casi) completa.
        const wrapperMensaje = burbujaBot.closest('.mensaje-wrapper');
        burbujaBot.classList.add('mensaje-split');
        if (wrapperMensaje) wrapperMensaje.classList.add('wrapper-split');

        burbujaBot.innerHTML = "";
        if (intro) {
            const introDiv = document.createElement('div');
            introDiv.innerHTML = marked.parse(intro);
            burbujaBot.appendChild(introDiv);
        }

        const splitContainer = document.createElement('div');
        splitContainer.className = 'opciones-split-container';

        const colA = document.createElement('div');
        colA.className = 'opcion-col';
        colA.id = 'col-A';
        colA.innerHTML = `
            <div class="opcion-content">${marked.parse(opcionA)}</div>
            <button class="btn-preferencia-col" data-opcion="A">Elegir Opción A</button>
        `;

        const colB = document.createElement('div');
        colB.className = 'opcion-col';
        colB.id = 'col-B';
        colB.innerHTML = `
            <div class="opcion-content">${marked.parse(opcionB)}</div>
            <button class="btn-preferencia-col" data-opcion="B">Elegir Opción B</button>
        `;

        splitContainer.appendChild(colA);
        splitContainer.appendChild(colB);
        burbujaBot.appendChild(splitContainer);

        splitContainer.querySelectorAll('.btn-preferencia-col').forEach(boton => {
            boton.addEventListener('click', async () => {
                const eleccion = boton.getAttribute('data-opcion');
                if (eleccion === 'A') {
                    colB.classList.add('hidden');
                    colA.classList.add('selected');
                    historial[historial.length-1].content = intro + "\\n\\n" + opcionA;
                } else {
                    colA.classList.add('hidden');
                    colB.classList.add('selected');
                    historial[historial.length-1].content = intro + "\\n\\n" + opcionB;
                }
                guardarHistorial();
                colA.querySelector('button').style.display = 'none';
                colB.querySelector('button').style.display = 'none';

                // Al quedarse una sola respuesta, la burbuja vuelve al ancho normal
                burbujaBot.classList.remove('mensaje-split');
                if (wrapperMensaje) wrapperMensaje.classList.remove('wrapper-split');

                await registrarPreferencia(eleccion);
            });
        });
        hacerScrollAbajo();
    }

    function agregarBotonesPreferencia(burbujaBot) {
        const contenedor = document.createElement('div');
        contenedor.className = 'opciones-preferencia';
        contenedor.innerHTML =
            '<button class="btn-preferencia" data-opcion="A">Me ayudó más A</button>' +
            '<button class="btn-preferencia" data-opcion="B">Me ayudó más B</button>';

        contenedor.querySelectorAll('.btn-preferencia').forEach(boton => {
            boton.addEventListener('click', async () => {
                contenedor.style.display = 'none'; // Ocultar botones tras elegir
                await registrarPreferencia(boton.getAttribute('data-opcion'));
            });
        });

        const wrapper = burbujaBot.closest('.mensaje-wrapper');
        (wrapper || burbujaBot).insertAdjacentElement('afterend', contenedor);
        hacerScrollAbajo();
    }

    async function registrarPreferencia(opcion) {
        try {
            await fetch('/api/tutor/preferencia', {
                method: 'POST',
                headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ opcion: opcion })
            });
        } catch (error) {
            console.error("Error registrando preferencia", error);
        }
    }

    function abrirModal() { document.getElementById('modal-overlay').style.display = 'flex'; }
    function cerrarModal() { document.getElementById('modal-overlay').style.display = 'none'; }


    function activarModoEjercicio() {
        document.getElementById('editor-panel').style.display = 'flex';
        if (!editor) {
            editor = ace.edit("editor");
            editor.setTheme("ace/theme/tomorrow_night_eighties");
            editor.session.setMode("ace/mode/java");
            editor.setValue("public class Main {\n    public static void main(String[] args) {\n        // Escribe tu solución aquí...\n        \n    }\n}");
            editor.clearSelection();
        }
    }

    function salirEjercicio() {
        document.getElementById('editor-panel').style.display = 'none';
    }

    function pedirPista() {
        inputElement.value = "Me rindo con este ejercicio, ¿puedes darme una pista conceptual o pseudocódigo guiado sin darme la solución final?";
        enviarMensaje();
    }

    function corregirEjercicio() {
        const codigo = editor.getValue().trim();
        if(!codigo || codigo === "public class Main {\n    public static void main(String[] args) {\n        // Escribe tu solución aquí...\n        \n    }\n}") {
            alert("¡No has escrito nada de código todavía!");
            return;
        }
        inputElement.value = "He terminado el ejercicio. Aquí está mi código:\n\n```java\n" + codigo + "\n```\n\nPor favor, revísalo como mi tutor socrático. Señala si hay errores, posibles mejoras de diseño (POO) o buenas prácticas que me falten, pero sin escribirme el código perfecto directamente.";
        enviarMensaje();
    }

    async function pedirEjercicio() {
        const temaSeleccionado = document.getElementById('select-tema-modal').value;
        const nombreTema = document.getElementById('select-tema-modal').options[document.getElementById('select-tema-modal').selectedIndex].text;
        const dificultad = document.getElementById('select-dificultad').value;
        
        cerrarModal();

        const peticiónFicticia = `Genera un ejercicio ${dificultad} sobre ${nombreTema}`;
        historial.push({ role: "user", content: peticiónFicticia });
        guardarHistorial();
        agregarBurbuja("Quiero hacer un ejercicio práctico de nivel " + dificultad + " sobre " + nombreTema, 'user');

        const idUnico = 'msg-' + (++contadorMensajes);
        agregarBurbujaCargando(idUnico);

        try {
            const respuesta = await fetch('/api/tutor/ejercicio', {
                method: 'POST',
                headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({
                    tema: temaSeleccionado,
                    dificultad: dificultad
                })
            });

            if (respuesta.status === 401) {
                mostrarPantallaLogin();
                historial.pop();
                document.getElementById(idUnico)?.closest('.mensaje-wrapper')?.remove();
                return;
            }

            const json = await respuesta.json();

            if (!respuesta.ok) {
                document.getElementById(idUnico).innerHTML = `<span style="color:#f85149;">${json.mensaje || "Error de conexión o del servidor."}</span>`;
                historial.pop();
                guardarHistorial();
                return;
            }

            historial.push({ role: "assistant", content: json.mensaje });
            guardarHistorial();

            const burbujaBot = document.getElementById(idUnico);
            if (burbujaBot) {
                marked.setOptions({ breaks: true });
                burbujaBot.innerHTML = marked.parse(limpiarMensaje(json.mensaje));
                renderizarValoracionBotones(burbujaBot, json.consultaId);
            }
        } catch (error) {
            document.getElementById(idUnico).innerText = "Error generando el ejercicio.";
        }
        hacerScrollAbajo();
    }

    function agregarBurbuja(texto, remitente) {
        const wrapper = document.createElement('div');
        wrapper.className = 'mensaje-wrapper ' + remitente + '-wrapper';
        const div = document.createElement('div');
        div.className = 'mensaje ' + remitente;
        
        if (remitente === 'bot' || remitente === 'assistant') {
            div.classList.replace('assistant', 'bot');
            marked.setOptions({ breaks: true });
            div.innerHTML = marked.parse(limpiarMensaje(texto));
        } else {
            div.innerText = texto;
        }
        
        wrapper.appendChild(div);
        mensajesDiv.appendChild(wrapper);
        hacerScrollAbajo();
    }
    
    function agregarBurbujaInit(texto, remitente) {
        const claseRemitente = (remitente === 'assistant') ? 'bot' : remitente;
        const wrapper = document.createElement('div');
        wrapper.className = 'mensaje-wrapper ' + claseRemitente + '-wrapper';
        const div = document.createElement('div');
        div.className = 'mensaje ' + claseRemitente;
        
        if (claseRemitente === 'bot') {
            marked.setOptions({ breaks: true });
            div.innerHTML = marked.parse(limpiarMensaje(texto));
        } else {
            div.innerText = texto;
        }
        
        wrapper.appendChild(div);
        mensajesDiv.appendChild(wrapper);
    }
    
    function agregarBurbujaCargando(id) {
        const wrapper = document.createElement('div');
        wrapper.className = 'mensaje-wrapper bot-wrapper';
        const div = document.createElement('div');
        div.className = 'mensaje bot';
        div.id = id;
        div.innerHTML = '<div class="loading"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>';
        wrapper.appendChild(div);
        mensajesDiv.appendChild(wrapper);
        hacerScrollAbajo();
    }

    function hacerScrollAbajo() {
        mensajesDiv.scrollTo({ top: mensajesDiv.scrollHeight, behavior: 'smooth' });
    }

    function renderizarValoracionBotones(burbujaBot, consultaId) {
        if (!consultaId || consultaId === 0) return;
        const divVal = document.createElement('div');
        divVal.className = 'valoracion-caja';
        divVal.innerHTML = `
            <button class="btn-val" onclick="valorarConsultaBot(${consultaId}, 1, this)" title="Respuesta útil y clara">👍 Útil</button>
            <button class="btn-val" onclick="valorarConsultaBot(${consultaId}, -1, this)" title="Falta claridad o tiene errores">👎 Mejorable</button>
        `;
        burbujaBot.appendChild(divVal);
    }

    let consultaIdValNegActual = null;

    window.valorarConsultaBot = async (consultaId, val, botonEl) => {
        const caja = botonEl.closest('.valoracion-caja');
        if (caja) {
            caja.querySelectorAll('.btn-val').forEach(b => {
                b.classList.remove('activo-pos', 'activo-neg');
            });
            if (val === 1) botonEl.classList.add('activo-pos');
            if (val === -1) botonEl.classList.add('activo-neg');
        }

        try {
            await fetch('/api/tutor/valoracion', {
                method: 'POST',
                headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ consultaId: consultaId, valoracion: val })
            });
        } catch (e) {
            console.error("Error al registrar valoración", e);
        }

        if (val === -1) {
            consultaIdValNegActual = consultaId;
            const tx = document.getElementById('texto-val-neg');
            if (tx) tx.value = '';
            const mod = document.getElementById('modal-val-neg');
            if (mod) mod.style.display = 'flex';
        }
    };

    window.cerrarModalValNeg = () => {
        const mod = document.getElementById('modal-val-neg');
        if (mod) mod.style.display = 'none';
    };

    window.enviarValoracionNegativaComentario = async () => {
        const tx = document.getElementById('texto-val-neg');
        const txt = tx ? tx.value.trim() : '';
        if (txt && consultaIdValNegActual) {
            try {
                await fetch('/api/tutor/valoracion', {
                    method: 'POST',
                    headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
                    body: JSON.stringify({ consultaId: consultaIdValNegActual, valoracion: -1, comentario: txt })
                });
            } catch (e) {
                console.error("Error al enviar comentario de valoración", e);
            }
        }
        cerrarModalValNeg();
    };

    let textoRepasoMdActual = "";

    window.generarApuntesRepaso = async () => {
        if (historial.length === 0) {
            alert("¡No hay suficiente conversación en esta sesión para generar apuntes de repaso! Haz algunas consultas al tutor primero.");
            return;
        }
        const mod = document.getElementById('modal-repaso');
        if (mod) mod.style.display = 'flex';
        const carga = document.getElementById('contenido-repaso-carga');
        if (carga) carga.style.display = 'block';
        const listo = document.getElementById('contenido-repaso-listo');
        if (listo) listo.style.display = 'none';

        iniciarMascotaCafe();
        try {
            const res = await fetch('/api/tutor/repaso', {
                method: 'POST',
                headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ historial: historial })
            });
            const json = await res.json();
            textoRepasoMdActual = json.apuntes || "No se pudieron generar los apuntes.";
            marked.setOptions({ breaks: true });
            const ren = document.getElementById('repaso-render');
            if (ren) ren.innerHTML = marked.parse(textoRepasoMdActual);
            if (carga) carga.style.display = 'none';
            if (listo) listo.style.display = 'block';
        } catch (e) {
            console.error("Error generando repaso", e);
            const ren = document.getElementById('repaso-render');
            if (ren) ren.innerHTML = "<p style='color:#f85149;'>Error de conexión al generar los apuntes.</p>";
            if (carga) carga.style.display = 'none';
            if (listo) listo.style.display = 'block';
        }
    };

    window.cerrarModalRepaso = () => {
        const mod = document.getElementById('modal-repaso');
        if (mod) mod.style.display = 'none';
    };

    window.descargarRepasoMd = () => {
        if (!textoRepasoMdActual) return;
        const blob = new Blob([textoRepasoMdActual], { type: "text/markdown;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "apuntes-repaso-socratico.md";
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    };

    window.imprimirRepasoPdf = () => {
        const ren = document.getElementById('repaso-render');
        if (!ren) return;
        const ventana = window.open('', '_blank');
        ventana.document.write(`
            <html>
            <head>
                <title>Apuntes de Repaso Socrático</title>
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap" rel="stylesheet">
                <style>
                    body { font-family: 'Inter', sans-serif; color: #222; line-height: 1.6; padding: 40px; max-width: 800px; margin: 0 auto; }
                    h1, h2, h3 { color: #0056d2; border-bottom: 1px solid #ddd; padding-bottom: 6px; }
                    code { background: #f4f4f4; padding: 2px 6px; border-radius: 4px; font-family: monospace; }
                    pre { background: #f4f4f4; padding: 15px; border-radius: 6px; overflow-x: auto; }
                    blockquote { border-left: 4px solid #0056d2; margin: 0; padding-left: 15px; color: #555; }
                </style>
            </head>
            <body>
                <h1 style="text-align: center; border: none;">📘 Apuntes de Repaso · Tutor Socrático</h1>
                <p style="text-align: center; color: #666; font-size: 0.9em; margin-bottom: 30px;">Generados con Inteligencia Artificial Pedagógica</p>
                <hr style="border: 0; border-top: 2px solid #0056d2; margin-bottom: 30px;">
                ${ren.innerHTML}
            </body>
            </html>
        `);
        ventana.document.close();
        ventana.focus();
        setTimeout(() => { ventana.print(); }, 500);
    };

// Funciones para el Modal de Ayuda
function abrirModalAyuda() {
    const modal = document.getElementById("modal-ayuda");
    if (modal) modal.style.display = "flex";
}

function cerrarModalAyuda() {
    const modal = document.getElementById("modal-ayuda");
    if (modal) modal.style.display = "none";
}

/* ==========================================================================
 *  Validador de Apuntes y Conocimientos Propios
 * ========================================================================== */

let apunteValidadoContenido = null;

function abrirModalValidador() {
    apunteValidadoContenido = null;
    const modal = document.getElementById('modal-validador');
    document.getElementById('validador-titulo').value = '';
    document.getElementById('validador-texto').value = '';
    document.getElementById('validador-archivo').value = '';
    document.getElementById('validador-archivo-nombre').innerText = '';
    document.getElementById('validador-resultado').style.display = 'none';
    document.getElementById('validador-cargando').style.display = 'none';
    if (modal) modal.style.display = 'flex';
}

function cerrarModalValidador() {
    const modal = document.getElementById('modal-validador');
    if (modal) modal.style.display = 'none';
}

function validadorArchivoElegido(input) {
    const nombre = (input.files && input.files.length > 0) ? input.files[0].name : '';
    document.getElementById('validador-archivo-nombre').innerText = nombre ? '📎 ' + nombre : '';
}

async function validarApunte() {
    const archivo = document.getElementById('validador-archivo').files[0];
    const texto = document.getElementById('validador-texto').value.trim();
    if (!archivo && !texto) {
        alert('Sube un archivo (PDF/TXT) o pega tus apuntes antes de validar.');
        return;
    }
    const datos = new FormData();
    if (texto) datos.append('texto', texto);
    if (archivo) datos.append('archivo', archivo);

    document.getElementById('validador-resultado').style.display = 'none';
    document.getElementById('validador-cargando').style.display = 'block';
    const btn = document.getElementById('btn-validar-apunte');
    btn.disabled = true;

    try {
        const res = await fetch('/api/alumno/apuntes/validar', {
            method: 'POST',
            headers: cabecerasConCsrf(),
            body: datos
        });
        const json = await res.json();
        if (!res.ok) {
            alert(json.mensaje || 'No se pudo validar los apuntes.');
            return;
        }
        apunteValidadoContenido = json.contenido || texto;
        const ren = document.getElementById('validador-feedback');
        marked.setOptions({ breaks: true });
        ren.innerHTML = marked.parse(json.feedback || 'Sin feedback.');
        document.getElementById('validador-resultado').style.display = 'block';
    } catch (e) {
        console.error('Error validando apuntes', e);
        alert('Error de conexión al validar los apuntes.');
    } finally {
        document.getElementById('validador-cargando').style.display = 'none';
        btn.disabled = false;
    }
}

async function guardarApunte() {
    const titulo = document.getElementById('validador-titulo').value.trim();
    if (!titulo) {
        alert('Ponle un título a tus apuntes antes de guardarlos.');
        return;
    }
    if (!apunteValidadoContenido) {
        alert('Primero valida tus apuntes.');
        return;
    }
    const datos = new FormData();
    datos.append('titulo', titulo);
    datos.append('texto', apunteValidadoContenido);

    try {
        const res = await fetch('/api/alumno/apuntes/guardar', {
            method: 'POST',
            headers: cabecerasConCsrf(),
            body: datos
        });
        const json = await res.json();
        if (!res.ok) {
            alert(json.mensaje || 'No se pudo guardar los apuntes.');
            return;
        }
        cerrarModalValidador();
        await cargarMisConocimientos();
        alert('✅ Apuntes guardados. El tutor ya los tendrá en cuenta solo para ti.');
    } catch (e) {
        console.error('Error guardando apuntes', e);
        alert('Error de conexión al guardar los apuntes.');
    }
}

async function cargarMisConocimientos() {
    const lista = document.getElementById('lista-mis-conocimientos');
    if (!lista) return;
    try {
        const res = await fetch('/api/alumno/apuntes/listar');
        if (!res.ok) return; // p. ej. aún sin sesión: dejamos el placeholder
        const apuntes = await res.json();
        lista.innerHTML = '';
        if (!Array.isArray(apuntes) || apuntes.length === 0) {
            lista.innerHTML = '<li class="texto-vacio-mini" style="font-size: 0.75rem; color: var(--text-muted); padding: 4px 8px; list-style: none;">Aún no has guardado apuntes propios.</li>';
            return;
        }
        apuntes.forEach(a => {
            const li = document.createElement('li');
            li.className = 'topic-item';
            li.setAttribute('data-file', a.tema);
            li.onclick = () => seleccionarTema(li);
            li.oncontextmenu = (e) => {
                e.preventDefault();
                borrarApunteAlumno(a.id, a.tema);
            };
            li.innerText = '🎒 ' + a.tema;
            li.title = "Clic derecho para eliminar";
            lista.appendChild(li);
        });
    } catch (e) {
        console.error('Error cargando Mis Conocimientos', e);
    }
}

window.addEventListener('load', () => { cargarMisConocimientos(); });

async function borrarApunteAlumno(id, tema) {
    if (!confirm(`¿Estás seguro de que quieres eliminar el apunte "${tema}"?\nEsto lo quitará de tu base de conocimientos.`)) return;
    try {
        const res = await fetch(`/api/alumno/apuntes/${id}`, {
            method: 'DELETE',
            headers: cabecerasConCsrf()
        });
        const json = await res.json();
        if (res.ok) {
            await cargarMisConocimientos();
            alert("Apunte eliminado.");
        } else {
            alert(json.mensaje || "Error al eliminar el apunte.");
        }
    } catch (e) {
        console.error('Error al borrar', e);
    }
}

/* --- Toggle Funciones Header --- */
window.toggleSidebar = () => { const sidebar = document.getElementById('sidebar'); if (sidebar) sidebar.classList.toggle('sidebar-closed'); }; window.toggleHeaderMenu = (e) => { if (e) e.stopPropagation(); const dropdown = document.getElementById('header-dropdown'); if (dropdown) dropdown.classList.toggle('show'); }; document.addEventListener('click', (e) => { const dropdown = document.getElementById('header-dropdown'); const btn = document.querySelector('.floating-right'); if (dropdown && btn && dropdown.classList.contains('show') && !dropdown.contains(e.target) && !btn.contains(e.target)) dropdown.classList.remove('show'); });




function iniciarMascotaCafe() { const v = document.getElementById('mascota-cafe'); if (!v || v.tagName !== 'IMG') return; const srcOriginal = v.src; const reproducir = () => { v.src = ''; setTimeout(() => { v.src = srcOriginal.split('?')[0]; }, 50); }; reproducir(); setInterval(reproducir, 20000); }




/* --- Consentimiento de datos (primera vez del alumno) --- */
let consentTituloPendiente = null;

async function comprobarConsentimiento() {
    try {
        const res = await fetch('/api/tutor/consentimiento');
        if (!res.ok) return true;
        const data = await res.json();
        return !!data.aceptado;
    } catch (e) {
        return true;
    }
}

function mostrarModalConsentimiento(titulo) {
    consentTituloPendiente = titulo;
    const m = document.getElementById('modal-consentimiento');
    if (m) m.style.display = 'flex';
}

window.aceptarConsentimiento = async () => {
    try {
        await fetch('/api/tutor/consentimiento', { method: 'POST', headers: cabecerasConCsrf() });
    } catch (e) {
        console.error('Error registrando el consentimiento', e);
    }
    const m = document.getElementById('modal-consentimiento');
    if (m) m.style.display = 'none';
    mostrarRachaDelDia(consentTituloPendiente);
};

/* --- Racha Diaria --- */
async function mostrarRachaDelDia(titulo) {
    try {
        const res = await fetch('/api/tutor/racha', { method: 'POST', headers: cabecerasConCsrf() });
        if (!res.ok) return;
        const estado = await res.json();
        if (estado.aumentada) {
            let msg = estado.mostrarMensajeBienvenida 
                ? "¡Hola de nuevo! Has estado unos días fuera, pero tu progreso sigue ahí."
                : "¡Buen trabajo! Un día más entrando a repasar.";
            
            document.getElementById('contenido-racha').innerHTML = `
                <div style="text-align:center;">
                    <img src="mascota-feliz.png" alt="Mascota" style="height:100px; margin-bottom:15px;">
                    <h3 style="margin:0 0 10px 0; color:var(--primary);">🔥 ¡Racha Diaria!</h3>
                    <p style="font-size:0.9rem; color:var(--text-main); margin-bottom:15px;">${msg}</p>
                    <div style="font-size:1.5rem; font-weight:800; color:#fff; background:var(--primary); padding:10px; border-radius:10px; display:inline-block;">
                        🔥 ${estado.rachaActual} días
                    </div>
                    <p style="font-size:0.8rem; color:var(--text-muted); margin-top:10px;">Racha máxima: ${estado.rachaMaxima} días</p>
                    <button onclick="document.getElementById('modal-racha').style.display='none'" style="margin-top:15px; padding:8px 20px; background:var(--bg-secondary); border:none; border-radius:6px; color:var(--text-main); cursor:pointer;">Continuar</button>
                </div>
            `;
            document.getElementById('modal-racha').style.display = 'flex';
        }
    } catch (e) {
        console.error("Error cargando racha", e);
    }
}

function iniciarMascotaCafe() { const v = document.getElementById('mascota-cafe'); if (!v || v.tagName !== 'IMG') return; const srcOriginal = v.src; const reproducir = () => { v.src = ''; setTimeout(() => { v.src = srcOriginal.split('?')[0]; }, 50); }; reproducir(); setInterval(reproducir, 20000); }


/* --- Frases Motivadoras Mascota --- */
(function() {
    const mascota = document.getElementById("mascota-cafe");
    const bocadillo = document.getElementById("bocadillo-mascota");
    if (!mascota || !bocadillo) return;

    const frases = [
        "Un error no es un paso atrás, es una pista para la solución. ¡Sigue buscando!",
        "Respira, lee el mensaje de la consola y vuelve a intentarlo. Tú puedes con ese bug.",
        "Todo código maestro empezó fallando al compilar. No te desanimes.",
        "Un punto y coma o un fallo de sintaxis no te van a ganar la batalla de hoy.",
        "Los bugs son solo rompecabezas esperando a que encuentres la pieza correcta.",
        "Programa, equivócate, aprende y repite. Esa es la verdadera sintaxis del éxito.",
        "No necesitas ser un genio, solo necesitas no rendirte frente a la pantalla.",
        "Estás aprendiendo a construir el futuro, línea por línea.",
        "La lógica se entrena. Cada reto que resuelves hoy te hace mejor desarrollador mañana.",
        "Nadie nace sabiendo programar. La constancia es tu mejor algoritmo."
    ];

    mascota.addEventListener("mouseenter", () => {
        const fraseAleatoria = frases[Math.floor(Math.random() * frases.length)];
        bocadillo.innerText = fraseAleatoria;
        bocadillo.classList.add("mostrar");
    });

    mascota.addEventListener("mouseleave", () => {
        bocadillo.classList.remove("mostrar");
    });
})();
