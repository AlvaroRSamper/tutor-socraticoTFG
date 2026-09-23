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

    function aplicarConfiguracionYTemas(data) {
        if (!data) return;
        if (data.modoRetoExclusivo) activarModoRetoExclusivo();
        else retoCargarTablero();
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
                        li.setAttribute('data-nombre', (indice + 1) + ". " + nom.replace(/\.[^/.]+$/, ""));
                        li.onclick = () => seleccionarTema(li);
                        li.innerText = li.getAttribute('data-nombre');
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
                const nombre = li.getAttribute('data-nombre') || li.innerText;
                const option = document.createElement('option');
                option.value = file;
                option.text = nombre;
                selectModal.appendChild(option);
            });
        }
        if (typeof cargarMisConocimientos === 'function') cargarMisConocimientos();
        cargarTendencias();
    }

    async function cargarTendencias() {
        const listaTemas = document.getElementById('lista-temas');
        if (!listaTemas) return;
        try {
            const res = await fetch('/api/tutor/tendencias');
            if (!res.ok) return;
            const datos = await res.json();
            const porTema = {};
            datos.forEach(t => { porTema[t.tema] = t; });

            listaTemas.querySelectorAll('.topic-item').forEach(li => {
                const previa = li.querySelector('.tendencia-tema');
                if (previa) previa.remove();

                const t = porTema[li.getAttribute('data-file')];
                if (!t || t.direccion === 0) return;

                const sube = t.direccion === 1;
                const marca = document.createElement('span');
                marca.className = 'tendencia-tema ' + (sube ? 'tendencia-sube' : 'tendencia-baja');
                marca.innerText = sube ? '▲' : '▼';
                marca.title = sube
                    ? 'Vas necesitando menos pista en este tema (' + t.mediaPrevia + ' → ' + t.mediaReciente + ' de media).'
                    : 'Últimamente necesitas más pista en este tema (' + t.mediaPrevia + ' → ' + t.mediaReciente + ' de media).';
                li.appendChild(marca);
            });
        } catch (e) {
            console.error("Error cargando tendencias", e);
        }
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

    window.onload = async () => {
        if (window.matchMedia('(max-width: 760px)').matches) {
            document.getElementById('sidebar').classList.add('sidebar-closed');
        }
        iniciarMascotaCafe();
        try {
            const res = await fetch('/api/tutor/temas');
            if (res.ok) {
                const data = await res.json();
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

        const guardado = modoRetoExclusivoActivo() ? null : localStorage.getItem(claveHistorial);
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
        const etiqueta = elemento.getAttribute('data-nombre') || elemento.innerText;
        const nombreVisible = etiqueta.split('. ').pop().trim();
        
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
        wrapper.className = 'aviso-contexto';
        wrapper.innerHTML = `<span>${texto}</span>`;
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
                document.getElementById(idUnico).innerHTML = `<span class="texto-error">${json.mensaje || "Error de conexión o del servidor."}</span>`;
                historial.pop();
                guardarHistorial();
                return;
            }

            const capas = partirEnCapas(json.mensaje);
            historial.push({ role: "assistant", content: capas[0] });
            const indiceHistorial = historial.length - 1;
            guardarHistorial();

            const burbujaBot = document.getElementById(idUnico);
            if (burbujaBot) {
                marked.setOptions({ breaks: true });

                if (json.mostrarOpciones) {
                    renderizarOpcionesSideBySide(burbujaBot, limpiarMensaje(json.mensaje));
                } else if (capas.length > 1) {
                    renderizarPorCapas(burbujaBot, capas, json.consultaId, indiceHistorial);
                } else {
                    burbujaBot.innerHTML = marked.parse(capas[0]);
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

    function partirEnCapas(texto) {
        const limpio = limpiarMensaje(texto);
        const partes = limpio.split(/^[ \t]*-{3}\s*CAPA\s*-{3}[ \t]*$/m)
            .map(p => p.trim())
            .filter(p => p.length > 0);
        return partes.length > 0 ? partes : [limpio];
    }

    function renderizarPorCapas(burbujaBot, capas, consultaId, indiceHistorial) {
        burbujaBot.innerHTML = "";

        const contenido = document.createElement('div');
        contenido.className = 'capas-contenido';
        contenido.innerHTML = marked.parse(capas[0]);
        burbujaBot.appendChild(contenido);

        const boton = document.createElement('button');
        boton.className = 'btn-mas-pista';
        boton.innerText = '🔎 Necesito más pista';
        burbujaBot.appendChild(boton);

        let reveladas = 1;
        boton.addEventListener('click', async () => {
            if (reveladas >= capas.length) return;

            const nueva = document.createElement('div');
            nueva.className = 'capa-revelada';
            nueva.innerHTML = marked.parse(capas[reveladas]);
            contenido.appendChild(nueva);

            if (historial[indiceHistorial]) {
                historial[indiceHistorial].content += "\n\n" + capas[reveladas];
                guardarHistorial();
            }

            reveladas++;
            if (reveladas >= capas.length) boton.remove();
            hacerScrollAbajo();

            await registrarRevelacion(consultaId, reveladas - 1);
        });
    }

    async function registrarRevelacion(consultaId, nivel) {
        if (!consultaId || consultaId === 0) return;
        try {
            await fetch('/api/tutor/revelacion', {
                method: 'POST',
                headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ consultaId: consultaId, nivel: nivel })
            });
            cargarTendencias();
        } catch (error) {
            console.error("Error registrando revelación", error);
        }
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
                document.getElementById(idUnico).innerHTML = `<span class="texto-error">${json.mensaje || "Error de conexión o del servidor."}</span>`;
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
            if (ren) ren.innerHTML = "<p class='texto-error'>Error de conexión al generar los apuntes.</p>";
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
            lista.innerHTML = '<li class="texto-vacio-mini">Aún no has guardado apuntes propios.</li>';
            return;
        }
        apuntes.forEach(a => {
            const li = document.createElement('li');
            li.className = 'topic-item';
            li.setAttribute('data-file', a.tema);
            li.setAttribute('data-nombre', a.tema);
            li.onclick = () => seleccionarTema(li);
            li.oncontextmenu = (e) => {
                e.preventDefault();
                borrarApunteAlumno(a.id, a.tema);
            };
            li.innerText = a.tema;
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
                <img src="mascota-feliz.png" alt="Mascota" class="racha-mascota">
                <h3>¡Racha diaria!</h3>
                <p>${msg}</p>
                <div class="racha-contador">🔥 ${estado.rachaActual} días</div>
                <p class="texto-pequeno">Racha máxima: ${estado.rachaMaxima} días</p>
                <button class="btn-confirm" onclick="document.getElementById('modal-racha').style.display='none'">Continuar</button>
            `;
            document.getElementById('modal-racha').style.display = 'flex';
        }
    } catch (e) {
        console.error("Error cargando racha", e);
    }
}

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
