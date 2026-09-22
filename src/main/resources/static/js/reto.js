/* ==========================================================================
 *  Modo Reto / Microhitos  (Modo Alumno)
 *  Reutiliza cabecerasConCsrf(), leerCookie() y marked de app.js (cargado antes).
 * ========================================================================== */

const retoEstado = {
    resolucionId: null,
    ejercicioId: null,
    titulo: '',
    enunciado: '',
    lenguaje: 'java',
    esPropuesto: false,
    historial: [],
    hitos: [],
    dirHandle: null,          // File System Access API
    archivosFallback: null,   // fallback webkitdirectory (FileList)
    tiempoBase: 0,
    segundosSesion: 0,
    segundosPendientes: 0,
    cronometro: null
};

const SEGUNDOS_ENTRE_LATIDOS = 60;

const CIRCUNFERENCIA_ANILLO = 2 * Math.PI * 78; // r=78 en el SVG

/* ---------------------- Modal de configuración ---------------------- */

function abrirModalReto() {
    retoVolverOpciones();
    retoPoblarTemas();
    retoActualizarEstadoCarpeta();
    if (estaEnIframe()) {
        const est = document.getElementById('reto-carpeta-estado');
        if (est) est.innerHTML = '⚠️ Estás dentro de Moodle: la vinculación de carpeta puede requerir abrir la app en una pestaña nueva.';
    }
    document.getElementById('modal-reto').style.display = 'flex';
}

function cerrarModalReto() {
    document.getElementById('modal-reto').style.display = 'none';
}

function retoMostrarCarpeta(mostrar) {
    const carpeta = document.querySelector('#modal-reto .reto-carpeta');
    if (carpeta) carpeta.style.display = mostrar ? '' : 'none';
}

function retoVolverOpciones() {
    ['reto-form-crear', 'reto-form-subir', 'reto-form-propuestos', 'reto-form-retomar', 'reto-form-test', 'reto-cargando']
        .forEach(id => { const el = document.getElementById(id); if (el) el.style.display = 'none'; });
    document.querySelector('#modal-reto .reto-opciones').style.display = 'grid';
    retoMostrarCarpeta(false);
}

function retoMostrarSeccion(id) {
    document.querySelector('#modal-reto .reto-opciones').style.display = 'none';
    ['reto-form-crear', 'reto-form-subir', 'reto-form-propuestos', 'reto-form-retomar', 'reto-form-test']
        .forEach(s => { const el = document.getElementById(s); if (el) el.style.display = (s === id ? 'block' : 'none'); });
    retoMostrarCarpeta(id !== 'reto-form-test');
}

function retoElegirCrear() { retoMostrarSeccion('reto-form-crear'); }
function retoElegirSubir() { retoMostrarSeccion('reto-form-subir'); }
function retoElegirPropuestos() { retoMostrarSeccion('reto-form-propuestos'); retoCargarPropuestos(); }
function retoElegirTest() { retoMostrarSeccion('reto-form-test'); testPoblarTemas(); }

function retoPoblarTemas() {
    const sel = document.getElementById('reto-select-tema');
    if (!sel) return;
    sel.innerHTML = '';
    document.querySelectorAll('.topic-item').forEach(li => {
        const opt = document.createElement('option');
        opt.value = li.getAttribute('data-file');
        opt.text = li.innerText;
        sel.appendChild(opt);
    });
}

function retoMostrarCargando(texto) {
    retoVolverOpciones();
    document.querySelector('#modal-reto .reto-opciones').style.display = 'none';
    retoMostrarCarpeta(false);
    const c = document.getElementById('reto-cargando');
    document.getElementById('reto-cargando-texto').innerText = texto || 'Preparando tu reto…';
    if (c) c.style.display = 'block';
}

/* ---------------------- Vinculación con el IDE (File System Access API) ---- */

function soportaFsa() { return 'showDirectoryPicker' in window; }
function estaEnIframe() { try { return window.self !== window.top; } catch (e) { return true; } }

async function retoVincularCarpeta() {
    try {
        if (soportaFsa()) {
            retoEstado.dirHandle = await window.showDirectoryPicker({ mode: 'read' });
            retoEstado.archivosFallback = null;
            await guardarHandleIdb(retoEstado.dirHandle);
        } else {
            // Fallback para Firefox/Safari: input webkitdirectory
            const input = document.createElement('input');
            input.type = 'file';
            input.webkitdirectory = true;
            input.multiple = true;
            input.style.display = 'none';
            document.body.appendChild(input);
            await new Promise(resolve => {
                input.onchange = () => {
                    retoEstado.archivosFallback = input.files;
                    retoEstado.dirHandle = null;
                    resolve();
                };
                input.click();
            });
            document.body.removeChild(input);
        }
        retoActualizarEstadoCarpeta();
    } catch (e) {
        if (e && e.name !== 'AbortError') console.error('Error vinculando carpeta', e);
    }
}

function retoNombreCarpeta() {
    if (retoEstado.dirHandle) return retoEstado.dirHandle.name;
    if (retoEstado.archivosFallback && retoEstado.archivosFallback.length > 0) {
        const rel = retoEstado.archivosFallback[0].webkitRelativePath || '';
        return rel.split('/')[0] || 'carpeta seleccionada';
    }
    return null;
}

function retoActualizarEstadoCarpeta() {
    const nombre = retoNombreCarpeta();
    const est = document.getElementById('reto-carpeta-estado');
    if (est) {
        if (nombre) { est.innerText = '✓ Vinculada: ' + nombre; est.classList.add('vinculada'); }
        else { est.innerText = 'Sin vincular. Necesaria para "Recargar porcentaje".'; est.classList.remove('vinculada'); }
    }
    const footer = document.getElementById('reto-footer');
    const ftxt = document.getElementById('reto-footer-texto');
    if (footer && ftxt) {
        if (nombre) { footer.classList.add('vinculado'); ftxt.innerText = 'Carpeta vinculada: ' + nombre; }
        else { footer.classList.remove('vinculado'); ftxt.innerText = 'Carpeta del IDE sin vincular'; }
    }
}

const EXT_CODIGO = ['.java', '.c', '.h', '.cpp', '.hpp', '.cc', '.py', '.js', '.ts', '.cs', '.kt'];
const DIRS_EXCLUIR = ['node_modules', 'target', '.git', 'bin', 'build', 'out', '.idea', '.vscode', 'dist'];
const MAX_BYTES_ARCHIVO = 200000;

async function retoLeerCodigo() {
    // Vía File System Access API
    if (retoEstado.dirHandle) {
        if (retoEstado.dirHandle.queryPermission) {
            const q = await retoEstado.dirHandle.queryPermission({ mode: 'read' });
            if (q !== 'granted') {
                const r = await retoEstado.dirHandle.requestPermission({ mode: 'read' });
                if (r !== 'granted') return null;
            }
        }
        const acc = [];
        await recorrerDir(retoEstado.dirHandle, '', acc);
        return acc;
    }
    // Vía fallback webkitdirectory
    if (retoEstado.archivosFallback) {
        const acc = [];
        for (const f of retoEstado.archivosFallback) {
            const ruta = f.webkitRelativePath || f.name;
            if (DIRS_EXCLUIR.some(d => ruta.includes('/' + d + '/'))) continue;
            if (!EXT_CODIGO.some(e => ruta.endsWith(e))) continue;
            if (f.size > MAX_BYTES_ARCHIVO) continue;
            acc.push({ ruta: ruta, contenido: await f.text() });
        }
        return acc;
    }
    return null;
}

async function recorrerDir(handle, ruta, acc) {
    for await (const [nombre, h] of handle.entries()) {
        if (h.kind === 'directory') {
            if (!DIRS_EXCLUIR.includes(nombre)) await recorrerDir(h, ruta + nombre + '/', acc);
        } else if (EXT_CODIGO.some(e => nombre.endsWith(e))) {
            try {
                const f = await h.getFile();
                if (f.size <= MAX_BYTES_ARCHIVO) acc.push({ ruta: ruta + nombre, contenido: await f.text() });
            } catch (e) { /* archivo ilegible: se ignora */ }
        }
    }
}

/* IndexedDB mínima para recordar el handle de carpeta entre recargas de página */
function abrirIdb() {
    return new Promise((resolve, reject) => {
        const req = indexedDB.open('tutorReto', 1);
        req.onupgradeneeded = () => req.result.createObjectStore('handles');
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
    });
}
async function guardarHandleIdb(handle) {
    try {
        const db = await abrirIdb();
        db.transaction('handles', 'readwrite').objectStore('handles').put(handle, 'dir');
    } catch (e) { /* opcional */ }
}

/* ---------------------- Creación / inicio del reto ---------------------- */

async function retoCrearConIa() {
    const tema = document.getElementById('reto-select-tema').value;
    const dificultad = document.getElementById('reto-select-dificultad').value;
    retoMostrarCargando('La IA está diseñando tu ejercicio y sus microhitos…');
    try {
        const res = await fetch('/api/reto/crear-ia', {
            method: 'POST',
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ tema, dificultad, lenguaje: 'java' })
        });
        const data = await res.json();
        if (!res.ok) return retoErrorModal(data.mensaje);
        await retoIniciarDesdeEjercicio(data.ejercicioId, false);
    } catch (e) { retoErrorModal('Error de conexión al crear el ejercicio.'); }
}

async function retoSubirPropio() {
    const titulo = document.getElementById('reto-subir-titulo').value.trim();
    const enunciado = document.getElementById('reto-subir-enunciado').value.trim();
    if (!titulo || !enunciado) { alert('Rellena el título y el enunciado.'); return; }
    retoMostrarCargando('La IA está dividiendo tu ejercicio en microhitos…');
    try {
        const res = await fetch('/api/reto/subir', {
            method: 'POST',
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ titulo, enunciado, lenguaje: 'java', tema: 'General' })
        });
        const data = await res.json();
        if (!res.ok) return retoErrorModal(data.mensaje);
        await retoIniciarDesdeEjercicio(data.ejercicioId, false);
    } catch (e) { retoErrorModal('Error de conexión al subir el ejercicio.'); }
}

async function retoSubirArchivo() {
    const input = document.getElementById('reto-subir-archivo');
    const archivo = input && input.files ? input.files[0] : null;
    if (!archivo) { alert('Elige un archivo PDF, Markdown o TXT.'); return; }
    const titulo = document.getElementById('reto-subir-titulo').value.trim();
    const datos = new FormData();
    datos.append('archivo', archivo);
    if (titulo) datos.append('titulo', titulo);
    datos.append('tema', 'General');
    datos.append('lenguaje', 'java');
    retoMostrarCargando('Extrayendo el enunciado del archivo y dividiéndolo en microhitos…');
    try {
        const res = await fetch('/api/reto/subir-archivo', {
            method: 'POST',
            headers: cabecerasConCsrf(),
            body: datos
        });
        const data = await res.json();
        if (!res.ok) return retoErrorModal(data.mensaje || 'No se pudo procesar el archivo.');
        await retoIniciarDesdeEjercicio(data.ejercicioId, false);
    } catch (e) { retoErrorModal('Error de conexión al subir el archivo.'); }
}

async function retoCargarPropuestos() {
    const cont = document.getElementById('reto-lista-propuestos');
    cont.innerHTML = '<p class="texto-vacio">Cargando…</p>';
    try {
        const res = await fetch('/api/reto/propuestos');
        const lista = await res.json();
        if (!Array.isArray(lista) || lista.length === 0) {
            cont.innerHTML = '<p class="texto-vacio">Todavía no hay ejercicios propuestos por el profesor.</p>';
            return;
        }
        cont.innerHTML = '';
        lista.forEach(e => {
            const enCurso = e.estadoMio === 'EN_PROGRESO';
            const div = document.createElement('div');
            div.className = 'radar-ejercicio';
            div.classList.add('clicable');
            div.onclick = () => enCurso ? retoPreguntarRetomar(e) : retoIniciarDesdeEjercicio(e.ejercicioId, true, false);
            let chips = '';
            if (e.completadoPorMi) chips += `<span class="radar-chip hecho">✅ Completado</span>`;
            if (enCurso) chips += `<span class="radar-chip en-curso">⏳ En curso · ${e.hitosCompletados}/${e.nMicrohitos} hitos</span>`;
            if (!chips) chips = `<span class="radar-chip">${e.nMicrohitos} hitos</span>`;
            div.innerHTML = `
                <div class="cab">
                    <span class="titulo">${escaparHtml(e.titulo)}</span>
                    <span class="radar-chips">${chips}</span>
                </div>
                <div class="texto-muted texto-pequeno">
                    ${e.dificultad ? 'Dificultad: ' + escaparHtml(e.dificultad) : ''} ${e.tema ? '· ' + escaparHtml(e.tema) : ''}
                </div>`;
            cont.appendChild(div);
        });
    } catch (e) {
        cont.innerHTML = '<p class="texto-error">Error de conexión al cargar los ejercicios propuestos.</p>';
    }
}

function retoPreguntarRetomar(propuesto) {
    retoMostrarSeccion('reto-form-retomar');
    document.getElementById('reto-retomar-texto').innerHTML =
        `<strong>${escaparHtml(propuesto.titulo)}</strong>: llevas ${propuesto.hitosCompletados} de ${propuesto.nMicrohitos} microhitos completados. ¿Quieres seguir por donde ibas o reiniciarlo?`;
    document.getElementById('reto-btn-continuar').onclick = () => retoIniciarDesdeEjercicio(propuesto.ejercicioId, true, false);
    document.getElementById('reto-btn-reiniciar').onclick = () => retoIniciarDesdeEjercicio(propuesto.ejercicioId, true, true);
}

async function retoIniciarDesdeEjercicio(ejercicioId, esPropuesto, reiniciar) {
    retoMostrarCargando(reiniciar ? 'Reiniciando el reto…' : 'Arrancando el reto…');
    try {
        const res = await fetch('/api/reto/iniciar', {
            method: 'POST',
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ ejercicioId, reiniciar: !!reiniciar })
        });
        const data = await res.json();
        if (!res.ok) return retoErrorModal(data.mensaje);
        data.esPropuesto = esPropuesto;
        arrancarReto(data);
    } catch (e) { retoErrorModal('Error de conexión al iniciar el reto.'); }
}

function retoErrorModal(mensaje) {
    retoVolverOpciones();
    alert(mensaje || 'Ha ocurrido un error. Inténtalo de nuevo.');
}

/* ---------------------- Pantalla de ejecución (Modo Enfoque) ---------------------- */

function arrancarReto(data) {
    retoEstado.resolucionId = data.resolucionId;
    retoEstado.ejercicioId = data.ejercicioId;
    retoEstado.titulo = data.titulo || 'Reto';
    retoEstado.enunciado = data.enunciado || '';
    retoEstado.lenguaje = data.lenguaje || 'java';
    retoEstado.esPropuesto = !!data.esPropuesto;
    retoEstado.hitos = data.microhitos || [];
    retoEstado.historial = data.retomado ? retoCargarChat() : [];
    if (!data.retomado) retoBorrarChatsDelEjercicio();

    cerrarModalReto();
    document.getElementById('reto-titulo-activo').innerText = retoEstado.titulo;
    document.getElementById('pantalla-reto').classList.add('activo');
    document.getElementById('reto-comentario-eval').style.display = 'none';

    const mensajes = document.getElementById('reto-mensajes');
    mensajes.innerHTML = '';
    if (data.retomado) {
        const hechos = retoEstado.hitos.filter(h => h.estado === 'COMPLETADO').length;
        retoAgregarBurbuja('### 📝 Enunciado\n\n' + retoEstado.enunciado +
            `\n\n---\n\n**Retomas el reto donde lo dejaste:** llevas ${hechos} de ${retoEstado.hitos.length} microhitos completados. Pulsa **Recargar porcentaje** cuando quieras que vuelva a evaluar tu código.`, 'bot');
        retoEstado.historial.forEach(m => retoAgregarBurbuja(m.content, m.role));
    } else {
        retoAgregarBurbuja('### 📝 Enunciado\n\n' + retoEstado.enunciado +
            '\n\n---\n\n¡Empecemos! Trabaja en el primer microhito. Escribe tu código en la carpeta vinculada y pulsa **Recargar porcentaje** cuando quieras que evalúe tu avance. Pregúntame lo que necesites.', 'bot');
    }

    renderHitos();
    const hayProgreso = data.retomado && retoEstado.hitos.some(h => h.estado !== 'PENDIENTE');
    if (hayProgreso) {
        const autonomia = (data.porcentajeAutonomia != null ? data.porcentajeAutonomia : data.porcentajeIndependencia) || 0;
        actualizarMedidor(autonomia);
        actualizarDesglose(autonomia);
        actualizarAutoria(data.porcentajeAutoria);
    } else {
        actualizarMedidor(0);
        actualizarAutoria(null);
    }
    retoActualizarEstadoCarpeta();
    iniciarCronometro(data.tiempoSegundos || 0);
}

function retoPrefijoChat() {
    const usuario = typeof claveHistorial !== 'undefined' ? claveHistorial : '';
    return 'reto_chat_' + usuario + '_e' + retoEstado.ejercicioId + '_';
}

function retoClaveChat() {
    return retoPrefijoChat() + 'r' + retoEstado.resolucionId;
}

function retoGuardarChat() {
    try { localStorage.setItem(retoClaveChat(), JSON.stringify(retoEstado.historial)); } catch (e) { }
}

function retoCargarChat() {
    try {
        const guardado = JSON.parse(localStorage.getItem(retoClaveChat()) || '[]');
        return Array.isArray(guardado) ? guardado : [];
    } catch (e) {
        return [];
    }
}

function retoBorrarChatsDelEjercicio() {
    try {
        const prefijo = retoPrefijoChat();
        Object.keys(localStorage).filter(k => k.startsWith(prefijo)).forEach(k => localStorage.removeItem(k));
    } catch (e) { }
}

function renderHitos() {
    const cont = document.getElementById('reto-hitos-lista');
    cont.innerHTML = '';
    retoEstado.hitos.forEach(h => {
        const estado = (h.estado || 'PENDIENTE').toLowerCase();
        const div = document.createElement('div');
        div.className = 'hito-item ' + estado;
        let simbolo = h.orden;
        if (estado === 'completado') simbolo = '✓';
        else if (estado === 'en_progreso') simbolo = '◐';
        div.innerHTML = `
            <div class="hito-nodo">${simbolo}</div>
            <div class="hito-texto">
                <div class="t">${escaparHtml(h.titulo)}</div>
                ${h.independencia != null && estado !== 'pendiente' ? `<div class="e">Autonomía: ${h.independencia}%</div>` : ''}
            </div>`;
        cont.appendChild(div);
    });
}

function actualizarMedidor(pct) {
    pct = Math.max(0, Math.min(100, Math.round(pct)));
    const anillo = document.getElementById('reto-anillo-progreso');
    const num = document.getElementById('reto-indep-num');
    anillo.style.strokeDashoffset = CIRCUNFERENCIA_ANILLO * (1 - pct / 100);
    let color = 'var(--warning)';
    if (pct >= 70) color = 'var(--success)';
    else if (pct >= 40) color = 'var(--primary)';
    anillo.style.stroke = color;
    num.innerText = pct + '%';
}

function iniciarCronometro(segundosPrevios) {
    retoEstado.tiempoBase = segundosPrevios;
    retoEstado.segundosSesion = 0;
    retoEstado.segundosPendientes = 0;
    const el = document.getElementById('reto-cronometro');
    el.innerText = formatearTiempo(segundosPrevios);
    clearInterval(retoEstado.cronometro);
    retoEstado.cronometro = setInterval(() => {
        if (document.hidden) return;
        retoEstado.segundosSesion++;
        retoEstado.segundosPendientes++;
        el.innerText = formatearTiempo(retoTiempoTotal());
        if (retoEstado.segundosPendientes >= SEGUNDOS_ENTRE_LATIDOS) retoSincronizarTiempo(false);
    }, 1000);
}

function retoTiempoTotal() {
    return retoEstado.tiempoBase + retoEstado.segundosSesion;
}

async function retoSincronizarTiempo(alSalir) {
    const segundos = retoEstado.segundosPendientes;
    if (!retoEstado.resolucionId || segundos <= 0) return;
    retoEstado.segundosPendientes = 0;
    try {
        const res = await fetch('/api/reto/tiempo', {
            method: 'POST',
            keepalive: alSalir,
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ resolucionId: retoEstado.resolucionId, segundos })
        });
        if (!res.ok && res.status !== 404) retoEstado.segundosPendientes += segundos;
    } catch (e) {
        retoEstado.segundosPendientes += segundos;
    }
}

window.addEventListener('pagehide', () => {
    if (document.getElementById('pantalla-reto').classList.contains('activo')) retoSincronizarTiempo(true);
});

function formatearTiempo(seg) {
    const h = Math.floor(seg / 3600), m = Math.floor((seg % 3600) / 60), s = seg % 60;
    const mm = String(m).padStart(2, '0'), ss = String(s).padStart(2, '0');
    return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

function salirReto() {
    clearInterval(retoEstado.cronometro);
    retoSincronizarTiempo(true);
    document.getElementById('pantalla-reto').classList.remove('activo');
}

/* ---------------------- Chat del reto ---------------------- */

function retoManejarEnter(event) {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); retoEnviarMensaje(); }
}

async function retoEnviarMensaje() {
    const input = document.getElementById('reto-input');
    const texto = input.value.trim();
    if (!texto) return;
    retoEstado.historial.push({ role: 'user', content: texto });
    retoGuardarChat();
    retoAgregarBurbuja(texto, 'user');
    input.value = '';

    const id = 'reto-msg-' + Date.now();
    retoAgregarBurbujaCargando(id);
    try {
        const res = await fetch('/api/reto/chat', {
            method: 'POST',
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ resolucionId: retoEstado.resolucionId, historial: retoEstado.historial })
        });
        const data = await res.json();
        const burbuja = document.getElementById(id);
        if (!res.ok) { burbuja.innerHTML = `<span class="texto-error">${data.mensaje || 'Error del servidor.'}</span>`; return; }
        retoEstado.historial.push({ role: 'assistant', content: data.mensaje });
        retoGuardarChat();
        marked.setOptions({ breaks: true });
        burbuja.innerHTML = marked.parse(data.mensaje || '');
    } catch (e) {
        const burbuja = document.getElementById(id);
        if (burbuja) burbuja.innerText = 'Error de conexión.';
    }
    retoScrollAbajo();
}

function retoAgregarBurbuja(texto, remitente) {
    const clase = (remitente === 'assistant') ? 'bot' : remitente;
    const wrapper = document.createElement('div');
    wrapper.className = 'mensaje-wrapper ' + clase + '-wrapper';
    const div = document.createElement('div');
    div.className = 'mensaje ' + clase;
    if (clase === 'bot') { marked.setOptions({ breaks: true }); div.innerHTML = marked.parse(texto); }
    else div.innerText = texto;
    wrapper.appendChild(div);
    document.getElementById('reto-mensajes').appendChild(wrapper);
    retoScrollAbajo();
}

function retoAgregarBurbujaCargando(id) {
    const wrapper = document.createElement('div');
    wrapper.className = 'mensaje-wrapper bot-wrapper';
    const div = document.createElement('div');
    div.className = 'mensaje bot';
    div.id = id;
    div.innerHTML = '<div class="loading"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>';
    wrapper.appendChild(div);
    document.getElementById('reto-mensajes').appendChild(wrapper);
    retoScrollAbajo();
}

function retoScrollAbajo() {
    const m = document.getElementById('reto-mensajes');
    m.scrollTo({ top: m.scrollHeight, behavior: 'smooth' });
}

/* ---------------------- Recargar porcentaje ---------------------- */

async function retoRecargarPorcentaje() {
    const boton = document.getElementById('reto-btn-recargar');
    const nombre = retoNombreCarpeta();
    if (!nombre) {
        alert('Primero vincula la carpeta de trabajo del IDE. Sal del reto, ábrelo de nuevo y pulsa "Seleccionar carpeta".');
        return;
    }
    boton.disabled = true;
    boton.innerHTML = '⏳ Analizando tu código…';
    try {
        const archivos = await retoLeerCodigo();
        if (archivos === null) { alert('No se ha podido leer la carpeta (permiso denegado).'); return; }
        await retoSincronizarTiempo(false);

        const res = await fetch('/api/reto/recargar', {
            method: 'POST',
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ resolucionId: retoEstado.resolucionId, archivos })
        });
        const data = await res.json();
        if (!res.ok) { alert(data.mensaje || 'Error al evaluar el código.'); return; }

        retoEstado.hitos = data.microhitos || retoEstado.hitos;
        renderHitos();
        const autonomia = (data.porcentajeAutonomia != null ? data.porcentajeAutonomia : data.porcentajeIndependencia) || 0;
        actualizarMedidor(autonomia);
        actualizarDesglose(autonomia);
        actualizarAutoria(data.porcentajeAutoria);

        const comEl = document.getElementById('reto-comentario-eval');
        if (data.comentarioDocente) { comEl.style.display = 'block'; comEl.innerText = '🧭 ' + data.comentarioDocente; }

        if (data.completado) mostrarPantallaExito(autonomia);
    } catch (e) {
        console.error('Error en recargar', e);
        alert('Error de conexión al recargar el porcentaje.');
    } finally {
        boton.disabled = false;
        boton.innerHTML = '🔄 Recargar porcentaje';
    }
}

function actualizarDesglose(pct) {
    const el = document.getElementById('reto-indep-desglose');
    if (!el) return;
    let msg;
    if (pct >= 70) msg = 'Lo estás resolviendo con poca ayuda. ¡Buen dominio!';
    else if (pct >= 40) msg = 'Vas necesitando algo de apoyo del tutor. Repasa los conceptos.';
    else msg = 'Estás apoyándote mucho en el tutor. Intenta el siguiente paso por tu cuenta.';
    el.innerHTML = `<b>${pct}%</b> de autonomía · ${msg}`;
}

/** Autoría: métrica separada de la autonomía. Verde = código propio; ámbar/rojo = mucho solapamiento. */
function actualizarAutoria(pct) {
    const bloque = document.getElementById('reto-autoria-bloque');
    if (!bloque) return;
    if (pct == null) { bloque.style.display = 'none'; return; }
    pct = Math.max(0, Math.min(100, Math.round(pct)));
    bloque.style.display = 'block';
    const num = document.getElementById('reto-autoria-num');
    const barra = document.getElementById('reto-autoria-barra');
    if (num) num.innerText = pct + '%';
    if (barra) {
        barra.style.width = pct + '%';
        barra.style.background = pct >= 70 ? 'var(--success)' : (pct >= 40 ? 'var(--primary)' : 'var(--danger)');
    }
}

/* ---------------------- Pantalla de éxito ---------------------- */

function mostrarPantallaExito(pct) {
    clearInterval(retoEstado.cronometro);
    retoBorrarChatsDelEjercicio();
    document.getElementById('exito-indep').innerText = pct + '%';
    document.getElementById('exito-tiempo').innerText = 'Tiempo total: ' + formatearTiempo(retoTiempoTotal());
    document.getElementById('pantalla-exito').classList.add('activo');
}

function cerrarPantallaExito() {
    document.getElementById('pantalla-exito').classList.remove('activo');
    salirReto();
}

/* ==========================================================================
 *  Test de teoría (opción múltiple, práctica efímera)
 * ========================================================================== */

const testEstado = {
    preguntas: [],
    indice: 0,
    aciertos: 0,
    respondida: false
};

function testPoblarTemas() {
    const sel = document.getElementById('test-select-tema');
    if (!sel) return;
    sel.innerHTML = '';
    document.querySelectorAll('.topic-item').forEach(li => {
        const opt = document.createElement('option');
        opt.value = li.getAttribute('data-file');
        opt.text = li.innerText;
        sel.appendChild(opt);
    });
}

async function testGenerar() {
    const tema = document.getElementById('test-select-tema').value;
    const dificultad = document.getElementById('test-select-dificultad').value;
    const numPreguntas = parseInt(document.getElementById('test-select-num').value, 10) || 10;
    retoMostrarCargando('La IA está preparando tu test de teoría…');
    try {
        const res = await fetch('/api/reto/test', {
            method: 'POST',
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ tema, dificultad, numPreguntas })
        });
        const data = await res.json();
        if (!res.ok) return retoErrorModal(data.mensaje);
        if (!Array.isArray(data) || data.length === 0) return retoErrorModal('No se han podido generar preguntas. Inténtalo de nuevo.');
        arrancarTest(data);
    } catch (e) { retoErrorModal('Error de conexión al generar el test.'); }
}

function arrancarTest(preguntas) {
    testEstado.preguntas = preguntas;
    testEstado.indice = 0;
    testEstado.aciertos = 0;
    testEstado.respondida = false;

    cerrarModalReto();
    document.getElementById('pantalla-test').classList.add('activo');
    testRenderPregunta();
}

function testRenderPregunta() {
    const p = testEstado.preguntas[testEstado.indice];
    const total = testEstado.preguntas.length;
    testEstado.respondida = false;

    document.getElementById('test-progreso').innerText = (testEstado.indice + 1) + ' / ' + total;
    document.getElementById('test-barra').style.width = Math.round((testEstado.indice / total) * 100) + '%';
    document.getElementById('test-enunciado').innerHTML = escaparHtml(p.enunciado);

    const cont = document.getElementById('test-opciones');
    cont.innerHTML = '';
    p.opciones.forEach((texto, i) => {
        const btn = document.createElement('button');
        btn.className = 'test-opcion';
        btn.innerHTML = '<span class="letra">' + String.fromCharCode(65 + i) + '</span>' + escaparHtml(texto);
        btn.onclick = () => testResponder(i, btn);
        cont.appendChild(btn);
    });

    const exp = document.getElementById('test-explicacion');
    exp.style.display = 'none';
    exp.innerHTML = '';
    document.getElementById('test-btn-siguiente').style.display = 'none';
}

function testResponder(elegida, btn) {
    if (testEstado.respondida) return;
    testEstado.respondida = true;
    const p = testEstado.preguntas[testEstado.indice];
    const botones = document.querySelectorAll('#test-opciones .test-opcion');
    botones.forEach(b => b.classList.add('bloqueada'));

    if (elegida === p.correcta) {
        testEstado.aciertos++;
        btn.classList.add('correcta');
    } else {
        btn.classList.add('incorrecta');
        if (botones[p.correcta]) botones[p.correcta].classList.add('correcta');
        const exp = document.getElementById('test-explicacion');
        exp.innerHTML = '<strong>❌ Repasa esto:</strong> ' + escaparHtml(p.explicacion || 'La opción correcta está marcada en verde.');
        exp.style.display = 'block';
    }

    const btnSig = document.getElementById('test-btn-siguiente');
    btnSig.innerText = (testEstado.indice + 1 >= testEstado.preguntas.length) ? 'Ver resultado →' : 'Siguiente →';
    btnSig.style.display = 'inline-block';
}

function testSiguiente() {
    if (testEstado.indice + 1 >= testEstado.preguntas.length) {
        testFinalizar();
        return;
    }
    testEstado.indice++;
    testRenderPregunta();
}

function testFinalizar() {
    const total = testEstado.preguntas.length;
    const aciertos = testEstado.aciertos;
    const pct = Math.round((aciertos / total) * 100);

    document.getElementById('test-fin-nota').innerText = aciertos + ' / ' + total;
    let mensaje;
    if (pct >= 80) mensaje = '¡Dominas la teoría! Sigue así.';
    else if (pct >= 50) mensaje = 'Vas bien, pero repasa los fallos.';
    else mensaje = 'Toca repasar la teoría con calma. ¡Ánimo!';
    document.getElementById('test-fin-mensaje').innerText = mensaje + ' (' + pct + '% de aciertos)';

    const aprobado = pct >= 50;
    const mascota = document.getElementById('test-fin-mascota');
    mascota.src = aprobado ? 'mascota-feliz.png' : 'mascota-enfadado.png';
    mascota.classList.remove('animar-feliz', 'animar-enfadado');
    void mascota.offsetWidth;
    mascota.classList.add(aprobado ? 'animar-feliz' : 'animar-enfadado');

    document.getElementById('pantalla-test').classList.remove('activo');
    document.getElementById('pantalla-test-fin').classList.add('activo');
}

function testSalir() {
    document.getElementById('pantalla-test').classList.remove('activo');
}

function testCerrarFin() {
    document.getElementById('pantalla-test-fin').classList.remove('activo');
}

/* ---------------------- Utilidades ---------------------- */

function escaparHtml(str) {
    if (str == null) return '';
    return String(str).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
