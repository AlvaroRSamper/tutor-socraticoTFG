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

/* ==========================================================================
 *  Modo reto exclusivo: el profesor deja visibles solo sus retos propuestos
 * ========================================================================== */

function modoRetoExclusivoActivo() {
    return document.body.classList.contains('modo-reto-exclusivo');
}

const CLAVE_COMO_FUNCIONA = 'reto_como_funciona_oculto';

const retoTablero = {
    lista: [],
    filtro: 'todos',
    seleccionado: null
};

const RETO_FILTROS = [
    { id: 'todos', etiqueta: 'Todos' },
    { id: 'pendiente', etiqueta: 'Pendientes' },
    { id: 'en-curso', etiqueta: 'En curso' },
    { id: 'completado', etiqueta: 'Completados' }
];

function activarModoRetoExclusivo() {
    document.body.classList.add('modo-reto-exclusivo');
    retoComoFunciona(!retoComoFuncionaOculto());
    retoCargarTablero();
}

function retoComoFuncionaOculto() {
    try { return localStorage.getItem(CLAVE_COMO_FUNCIONA) === '1'; } catch (e) { return false; }
}

function retoComoFunciona(mostrar) {
    const tira = document.getElementById('reto-comofunciona');
    const enlace = document.getElementById('reto-comofunciona-abrir');
    if (tira) tira.style.display = mostrar ? 'flex' : 'none';
    if (enlace) enlace.style.display = mostrar ? 'none' : 'block';
    try { localStorage.setItem(CLAVE_COMO_FUNCIONA, mostrar ? '0' : '1'); } catch (e) { }
}

function retoEstadoDe(e) {
    if (e.completadoPorMi || (e.nMicrohitos > 0 && e.hitosCompletados >= e.nMicrohitos)) return 'completado';
    return e.hitosCompletados > 0 ? 'en-curso' : 'pendiente';
}

function retoNivelDificultad(dificultad) {
    const d = (dificultad || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
    if (d.startsWith('faci')) return 1;
    if (d.startsWith('dific')) return 3;
    return d ? 2 : 0;
}

async function retoCargarTablero() {
    try {
        const res = await fetch('/api/reto/propuestos');
        const lista = await res.json();
        retoTablero.lista = Array.isArray(lista) ? lista : [];
    } catch (e) {
        retoTablero.lista = null;
    }
    retoElegirSeleccionInicial();
    retoPintarSidebar();
    retoPintarTablero();
    if (panelRetoAbierto()) panelRetoPintar01();
}

function retoElegirSeleccionInicial() {
    const lista = retoTablero.lista || [];
    if (retoTablero.seleccionado != null && lista.some(e => e.ejercicioId === retoTablero.seleccionado)) return;
    const elegido = lista.find(e => retoEstadoDe(e) === 'en-curso')
        || lista.find(e => retoEstadoDe(e) === 'pendiente')
        || lista[0];
    retoTablero.seleccionado = elegido ? elegido.ejercicioId : null;
}

function retoSeleccionar(ejercicioId) {
    retoTablero.seleccionado = ejercicioId;
    retoPintarSidebar();
    retoPintarTablero();
    if (panelRetoAbierto()) panelRetoPintar01();
}

function retoFiltrar(filtro) {
    retoTablero.filtro = filtro;
    retoPintarTablero();
}

/* ---------------------- Lista de la barra lateral ---------------------- */

function retoIconoEstado(estado) {
    return '<span class="reto-icono-estado ' + estado + '">' + (estado === 'completado' ? '&#10003;' : '') + '</span>';
}

function retoPintarSidebar() {
    const cont = document.getElementById('lista-retos-sidebar');
    if (!cont) return;
    if (retoTablero.lista === null) {
        cont.innerHTML = '<p class="texto-vacio-mini">No se pudieron cargar los retos.</p>';
        return;
    }
    if (retoTablero.lista.length === 0) {
        cont.innerHTML = '<p class="texto-vacio-mini">Tu profesor/a aún no ha publicado ningún reto.</p>';
        return;
    }
    cont.innerHTML = '';
    retoTablero.lista.forEach(e => {
        const estado = retoEstadoDe(e);
        const div = document.createElement('div');
        div.className = 'reto-sidebar-item' + (e.ejercicioId === retoTablero.seleccionado ? ' activo' : '');
        div.onclick = () => {
            retoSeleccionar(e.ejercicioId);
            if (!modoRetoExclusivoActivo()) abrirPanelRetos('01');
        };
        const detalle = (estado === 'completado' ? 'Completado' : (e.hitosCompletados + '/' + e.nMicrohitos + ' hitos')) + ' · Profesor';
        div.innerHTML = retoIconoEstado(estado) +
            '<span class="reto-sidebar-textos">' +
            '<span class="reto-sidebar-titulo">' + escaparHtml(e.titulo) + '</span>' +
            '<span class="reto-sidebar-meta">' + detalle + '</span>' +
            '</span>';
        cont.appendChild(div);
    });
}

/* ---------------------- Tablero de retos ---------------------- */

function retoPintarTablero() {
    const rejilla = document.getElementById('reto-rejilla');
    const detalle = document.getElementById('reto-detalle');
    const filtros = document.getElementById('reto-filtros');
    if (!rejilla || !detalle || !filtros) return;

    const lista = retoTablero.lista;
    detalle.innerHTML = '';
    if (lista === null || lista.length === 0) {
        filtros.innerHTML = '';
        rejilla.innerHTML = '<p class="reto-vacio-caja">' + (lista === null
            ? 'No se pudieron cargar los retos. Recarga la página.'
            : 'Tu profesor/a aún no ha publicado ningún reto.') + '</p>';
        retoPintarProgreso([]);
        return;
    }

    retoPintarProgreso(lista);
    retoPintarFiltros(lista, filtros);

    const visibles = retoTablero.filtro === 'todos'
        ? lista
        : lista.filter(e => retoEstadoDe(e) === retoTablero.filtro);

    rejilla.innerHTML = '';
    if (visibles.length === 0) {
        rejilla.innerHTML = '<p class="reto-vacio-caja">No tienes retos en este estado.</p>';
    } else {
        visibles.forEach(e => rejilla.appendChild(retoTarjeta(e)));
    }

    const elegido = lista.find(e => e.ejercicioId === retoTablero.seleccionado);
    if (elegido) detalle.appendChild(retoPanelDetalle(elegido));
}

function retoPintarProgreso(lista) {
    const txt = document.getElementById('reto-tablero-progreso-txt');
    const barra = document.getElementById('reto-tablero-barra-relleno');
    const hechos = lista.filter(e => retoEstadoDe(e) === 'completado').length;
    const total = lista.length;
    if (txt) txt.innerText = total ? (hechos + ' de ' + total + ' retos completados') : '';
    if (barra) barra.style.width = total ? Math.round(100 * hechos / total) + '%' : '0%';
}

function retoPintarFiltros(lista, cont) {
    cont.innerHTML = '';
    RETO_FILTROS.forEach(f => {
        const n = f.id === 'todos' ? lista.length : lista.filter(e => retoEstadoDe(e) === f.id).length;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'reto-filtro' + (retoTablero.filtro === f.id ? ' activo' : '');
        btn.setAttribute('aria-pressed', retoTablero.filtro === f.id ? 'true' : 'false');
        btn.onclick = () => retoFiltrar(f.id);
        btn.innerHTML = f.etiqueta + '<span class="reto-filtro-num">' + n + '</span>';
        cont.appendChild(btn);
    });
}

function retoInsignia(estado) {
    const texto = estado === 'completado' ? 'Completado' : (estado === 'en-curso' ? 'En curso' : 'Nuevo');
    return '<span class="reto-insignia ' + estado + '">' + texto + '</span>';
}

function retoPuntosDificultad(dificultad) {
    const nivel = retoNivelDificultad(dificultad);
    if (!nivel) return '';
    let puntos = '';
    for (let i = 1; i <= 3; i++) {
        puntos += '<span class="reto-punto-dif' + (i <= nivel ? ' n' + nivel : '') + '"></span>';
    }
    return '<span class="reto-dificultad">' + puntos + escaparHtml(dificultad) + '</span>';
}

function retoBarraHitos(e, estado) {
    if (!e.nMicrohitos) return '';
    let segmentos = '';
    for (let i = 0; i < e.nMicrohitos; i++) {
        const hecho = estado === 'completado' || i < e.hitosCompletados;
        const clase = hecho ? (estado === 'completado' ? ' fin' : ' hecho') : '';
        segmentos += '<span class="reto-segmento' + clase + '"></span>';
    }
    return '<div class="reto-barra-hitos">' + segmentos + '</div>';
}

function retoTarjeta(e) {
    const estado = retoEstadoDe(e);
    const div = document.createElement('div');
    div.className = 'reto-tarjeta' + (e.ejercicioId === retoTablero.seleccionado ? ' activa' : '');
    div.tabIndex = 0;
    div.setAttribute('role', 'button');
    div.onclick = () => retoSeleccionar(e.ejercicioId);
    div.onkeydown = (ev) => {
        if (ev.key === 'Enter' || ev.key === ' ') {
            ev.preventDefault();
            retoSeleccionar(e.ejercicioId);
        }
    };
    const hechos = estado === 'completado' ? e.nMicrohitos : e.hitosCompletados;
    div.innerHTML =
        '<div class="reto-tarjeta-alto">' +
        (e.tema ? '<span class="reto-tema">' + escaparHtml(e.tema) + '</span>' : '<span></span>') +
        retoInsignia(estado) +
        '</div>' +
        '<h3 class="reto-tarjeta-titulo">' + escaparHtml(e.titulo) + '</h3>' +
        retoBarraHitos(e, estado) +
        '<div class="reto-tarjeta-bajo">' +
        (retoPuntosDificultad(e.dificultad) || '<span></span>') +
        (e.nMicrohitos ? '<span class="reto-cuenta-hitos">' + hechos + '/' + e.nMicrohitos + ' hitos</span>' : '<span></span>') +
        '</div>';
    return div;
}

function retoPanelDetalle(e) {
    const estado = retoEstadoDe(e);
    const caja = document.createElement('div');
    caja.className = 'reto-detalle-caja';

    const hechos = estado === 'completado' ? e.nMicrohitos : e.hitosCompletados;
    let hitos = '';
    for (let i = 1; i <= e.nMicrohitos; i++) {
        const hecho = i <= hechos;
        const actual = !hecho && estado === 'en-curso' && i === hechos + 1;
        const clase = hecho ? 'hecho' : (actual ? 'actual' : 'pendiente');
        hitos += '<li class="reto-hito-fila ' + clase + '">' +
            '<span class="reto-hito-circulo">' + (hecho ? '&#10003;' : i) + '</span>' +
            '<span class="reto-hito-texto">Hito ' + i + '</span>' +
            '</li>';
    }

    let boton;
    let pista;
    let reiniciar = '';
    if (estado === 'completado') {
        boton = '<button type="button" class="reto-btn-detalle secundario">Repasar el reto</button>';
        pista = 'Tu autonomía ya está guardada';
    } else if (estado === 'en-curso') {
        const siguiente = Math.min(e.hitosCompletados + 1, e.nMicrohitos);
        boton = '<button type="button" class="reto-btn-detalle">Continuar · hito ' + siguiente + ' de ' + e.nMicrohitos + '</button>';
        pista = 'Retomas donde lo dejaste';
        reiniciar = '<button type="button" class="reto-btn-reiniciar">Reiniciar desde cero</button>';
    } else {
        boton = '<button type="button" class="reto-btn-detalle">Empezar reto</button>';
        pista = 'Se abrirá el chat con el enunciado completo';
    }

    caja.innerHTML =
        '<div class="reto-detalle-alto">' +
        '<div class="reto-detalle-meta">' +
        (e.tema ? '<span class="reto-tema">' + escaparHtml(e.tema) + '</span>' : '<span></span>') +
        retoInsignia(estado) +
        '</div>' +
        '<h2 class="reto-detalle-titulo">' + escaparHtml(e.titulo) + '</h2>' +
        retoPuntosDificultad(e.dificultad) +
        '</div>' +
        '<div class="reto-detalle-centro">' +
        '<span class="reto-detalle-etiqueta">Microhitos</span>' +
        (e.nMicrohitos
            ? '<ul class="reto-hitos-lista">' + hitos + '</ul>'
            : '<p class="reto-detalle-vacio">Este reto no tiene microhitos desglosados.</p>') +
        '</div>' +
        '<div class="reto-detalle-bajo">' +
        boton +
        '<span class="reto-detalle-pista">' + pista + '</span>' +
        reiniciar +
        '</div>';

    caja.querySelector('.reto-btn-detalle').onclick = () => retoTableroAbrir(e);
    const btnReiniciar = caja.querySelector('.reto-btn-reiniciar');
    if (btnReiniciar) btnReiniciar.onclick = () => retoReiniciar(e);
    return caja;
}

function retoTableroAbrir(propuesto, reiniciar) {
    document.getElementById('modal-reto').style.display = 'flex';
    retoIniciarDesdeEjercicio(propuesto.ejercicioId, true, !!reiniciar);
}

function retoReiniciar(propuesto) {
    const aviso = '¿Reiniciar "' + propuesto.titulo + '" desde cero?\n\n' +
        'Llevas ' + propuesto.hitosCompletados + ' de ' + propuesto.nMicrohitos + ' microhitos. ' +
        'Empezarás de nuevo y tu intento anterior quedará como abandonado.';
    if (!confirm(aviso)) return;
    retoTableroAbrir(propuesto, true);
}

/* ---------------------- Overlay de carga / arranque ---------------------- */

function abrirModalReto() {
    abrirPanelRetos();
}

function cerrarModalReto() {
    document.getElementById('modal-reto').style.display = 'none';
}

function retoMostrarCargando(texto) {
    const c = document.getElementById('reto-cargando');
    document.getElementById('reto-cargando-texto').innerText = texto || 'Preparando tu reto…';
    if (c) c.style.display = 'block';
    document.getElementById('modal-reto').style.display = 'flex';
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

/* ---------------------- Inicio del reto ---------------------- */

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
    cerrarModalReto();
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
    cerrarPanelRetos();
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
    retoCargarTablero();
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
 *  Panel de retos: 4 apartados con pestañas y vista previa
 *  Reutiliza las tarjetas y el panel de microhitos del tablero de modo reto.
 * ========================================================================== */

const CLAVE_TAB_PANEL = 'panel_reto_tab';
const CLAVE_IA_PANEL = 'panel_reto_ia';
const MAX_BYTES_SUBIDA = 10 * 1024 * 1024;

const PANEL_RETO_TABS = [
    { id: '01', titulo: 'Ejercicios propuestos', sub: 'Los retos que ha publicado tu profesor' },
    { id: '02', titulo: 'Subir ejercicio', sub: 'Convierte un enunciado tuyo en un reto guiado' },
    { id: '03', titulo: 'Generar con IA', sub: 'Un ejercicio nuevo a tu medida' },
    { id: '04', titulo: 'Test de teoría', sub: 'Preguntas rápidas para repasar conceptos' }
];

const DIFICULTADES = ['Fácil', 'Media', 'Difícil'];

const panelReto = {
    tab: '01',
    subir: { archivo: null, tema: null, estado: 'vacio', detalle: null, mensaje: '' },
    ia: { tema: null, dificultad: 'Media', estado: 'vacio', detalle: null, mensaje: '' },
    test: { fase: 'config', tema: null, dificultad: 'Media', num: 5, preguntas: [], indice: 0, elegida: null, comprobada: false, aciertos: [] }
};

function panelRetoAbierto() {
    const p = document.getElementById('panel-retos');
    return !!p && p.classList.contains('activo');
}

function abrirPanelRetos(tab) {
    const p = document.getElementById('panel-retos');
    if (!p) return;
    let inicial = tab;
    if (!inicial) {
        try { inicial = localStorage.getItem(CLAVE_TAB_PANEL); } catch (e) { inicial = null; }
    }
    panelReto.tab = PANEL_RETO_TABS.some(t => t.id === inicial) ? inicial : '01';
    panelRetoRestaurarIa();
    p.classList.add('activo');
    retoActualizarEstadoCarpeta();
    panelRetoPintarTabs();
    panelRetoPintarTab();
    if (retoTablero.lista === null || retoTablero.lista.length === 0) retoCargarTablero();
}

function cerrarPanelRetos() {
    const p = document.getElementById('panel-retos');
    if (p) p.classList.remove('activo');
}

function panelRetoTab(id) {
    panelReto.tab = id;
    try { localStorage.setItem(CLAVE_TAB_PANEL, id); } catch (e) { }
    panelRetoPintarTabs();
    panelRetoPintarTab();
}

function panelRetoPintarTabs() {
    const cont = document.getElementById('panel-reto-tabs');
    if (!cont) return;
    cont.innerHTML = '';
    PANEL_RETO_TABS.forEach(t => {
        const activa = panelReto.tab === t.id;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'panel-reto-tab' + (activa ? ' activa' : '');
        btn.setAttribute('role', 'tab');
        btn.setAttribute('aria-selected', activa ? 'true' : 'false');
        btn.onclick = () => panelRetoTab(t.id);
        btn.innerHTML =
            '<span class="panel-reto-tab-num">' + t.id + '</span>' +
            '<span class="panel-reto-tab-titulo">' + t.titulo + '</span>' +
            '<span class="panel-reto-tab-sub">' + t.sub + '</span>';
        cont.appendChild(btn);
    });
}

function panelRetoPintarTab() {
    PANEL_RETO_TABS.forEach(t => {
        const sec = document.getElementById('panel-reto-sec-' + t.id);
        if (sec) sec.classList.toggle('activa', panelReto.tab === t.id);
    });
    if (panelReto.tab === '01') panelRetoPintar01();
    if (panelReto.tab === '02') panelRetoPintar02();
    if (panelReto.tab === '03') panelRetoPintar03();
    if (panelReto.tab === '04') panelRetoPintarTest();
}

function panelRetoTemas() {
    const salida = [];
    document.querySelectorAll('.topic-item').forEach(li => {
        salida.push({
            valor: li.getAttribute('data-file'),
            etiqueta: li.getAttribute('data-nombre') || li.innerText,
            general: li.classList.contains('general-topic')
        });
    });
    return salida;
}

function panelRetoPintarChips(cont, temas, activo, alElegir, permiteQuitar) {
    cont.innerHTML = '';
    temas.forEach(t => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'panel-reto-chip' + (activo === t.valor ? ' activo' : '');
        btn.innerText = t.general ? 'Todo el temario' : t.etiqueta;
        btn.onclick = () => alElegir(permiteQuitar && activo === t.valor ? null : t.valor);
        cont.appendChild(btn);
    });
}

function panelRetoPintarSegmentado(cont, opciones, activo, alElegir) {
    cont.innerHTML = '';
    opciones.forEach(o => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'panel-reto-seg' + (String(activo) === String(o.valor) ? ' activo' : '');
        btn.innerText = o.etiqueta;
        btn.onclick = () => alElegir(o.valor);
        cont.appendChild(btn);
    });
}

function panelRetoCajaError(mensaje, alReintentar) {
    const div = document.createElement('div');
    div.className = 'panel-reto-error';
    div.innerHTML = '<span>' + escaparHtml(mensaje) + '</span>';
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'panel-reto-btn-sec';
    btn.innerText = 'Reintentar';
    btn.onclick = alReintentar;
    div.appendChild(btn);
    return div;
}

function panelRetoFantasmas(texto) {
    return '<div class="panel-reto-fantasmas">' +
        '<span class="panel-reto-fantasma"></span>' +
        '<span class="panel-reto-fantasma"></span>' +
        '<span class="panel-reto-fantasma"></span>' +
        '</div>' +
        (texto ? '<p class="panel-reto-vista-txt">' + texto + '</p>' : '');
}

function panelRetoHitosHtml(microhitos) {
    let filas = '';
    (microhitos || []).forEach((h, i) => {
        filas += '<li class="reto-hito-fila pendiente">' +
            '<span class="reto-hito-circulo">' + (i + 1) + '</span>' +
            '<span class="reto-hito-texto">' + escaparHtml(h.titulo || ('Hito ' + (i + 1))) + '</span>' +
            '</li>';
    });
    return '<ul class="reto-hitos-lista">' + filas + '</ul>';
}

/* ---------------------- 01 · Ejercicios propuestos ---------------------- */

function panelRetoPintar01() {
    const rejilla = document.getElementById('panel-reto-propuestos');
    const detalle = document.getElementById('panel-reto-detalle-01');
    const progreso = document.getElementById('panel-reto-progreso');
    if (!rejilla || !detalle) return;

    const lista = retoTablero.lista;
    detalle.innerHTML = '';
    rejilla.innerHTML = '';

    if (lista === null) {
        rejilla.appendChild(panelRetoCajaError('No se pudieron cargar los retos.', retoCargarTablero));
        if (progreso) progreso.innerText = '';
        return;
    }
    if (progreso) {
        const hechos = lista.filter(e => retoEstadoDe(e) === 'completado').length;
        progreso.innerText = lista.length ? (hechos + ' de ' + lista.length + ' retos del profesor completados') : '';
    }
    if (lista.length === 0) {
        rejilla.innerHTML = '<p class="reto-vacio-caja">Tu profesor/a aún no ha publicado ningún reto.</p>';
        return;
    }
    lista.forEach(e => rejilla.appendChild(retoTarjeta(e)));
    const elegido = lista.find(e => e.ejercicioId === retoTablero.seleccionado);
    if (elegido) detalle.appendChild(retoPanelDetalle(elegido));
}

/* ---------------------- 02 · Subir ejercicio ---------------------- */

function panelRetoPintar02() {
    const chips = document.getElementById('panel-reto-chips-subir');
    if (chips) {
        panelRetoPintarChips(chips, panelRetoTemas().filter(t => !t.general), panelReto.subir.tema,
            v => { panelReto.subir.tema = v; panelRetoPintar02(); }, true);
    }
    panelRetoArchivoFila();
    panelRetoActualizarBotonSubir();
    panelRetoVista02();
}

function panelRetoArchivoFila() {
    const zona = document.getElementById('panel-reto-soltar');
    const fila = document.getElementById('panel-reto-archivo');
    const archivo = panelReto.subir.archivo;
    if (!zona || !fila) return;
    if (!archivo) {
        zona.style.display = 'flex';
        fila.style.display = 'none';
        fila.innerHTML = '';
        return;
    }
    zona.style.display = 'none';
    fila.style.display = 'flex';
    const ext = (archivo.name.split('.').pop() || '?').toUpperCase().slice(0, 4);
    const kb = archivo.size < 1024 * 1024
        ? Math.max(1, Math.round(archivo.size / 1024)) + ' KB'
        : (archivo.size / (1024 * 1024)).toFixed(1) + ' MB';
    fila.innerHTML =
        '<span class="panel-reto-archivo-icono">' + escaparHtml(ext) + '</span>' +
        '<span class="panel-reto-archivo-txt">' +
        '<span class="panel-reto-archivo-nombre">' + escaparHtml(archivo.name) + '</span>' +
        '<span class="panel-reto-archivo-meta">' + kb + '</span>' +
        '</span>' +
        '<button type="button" class="panel-reto-archivo-quitar" title="Quitar">&times;</button>';
    fila.querySelector('.panel-reto-archivo-quitar').onclick = () => {
        panelReto.subir.archivo = null;
        const input = document.getElementById('panel-reto-input-archivo');
        if (input) input.value = '';
        panelRetoPintar02();
    };
}

function panelRetoActualizarBotonSubir() {
    const btn = document.getElementById('panel-reto-btn-subir');
    const texto = document.getElementById('panel-reto-enunciado');
    if (!btn) return;
    const hay = !!panelReto.subir.archivo || (texto && texto.value.trim().length > 0);
    btn.disabled = !hay || panelReto.subir.estado === 'cargando';
}

function panelRetoElegirArchivo(archivo) {
    if (!archivo) return;
    if (archivo.size > MAX_BYTES_SUBIDA) {
        panelReto.subir.estado = 'error';
        panelReto.subir.mensaje = 'El archivo supera los 10 MB.';
        panelRetoVista02();
        return;
    }
    panelReto.subir.archivo = archivo;
    panelReto.subir.estado = 'vacio';
    panelRetoPintar02();
}

async function panelRetoConvertir() {
    const texto = document.getElementById('panel-reto-enunciado');
    const enunciado = texto ? texto.value.trim() : '';
    const archivo = panelReto.subir.archivo;
    if (!archivo && !enunciado) return;

    panelReto.subir.estado = 'cargando';
    panelReto.subir.detalle = null;
    panelRetoActualizarBotonSubir();
    panelRetoVista02();

    const tema = panelReto.subir.tema || 'General';
    try {
        let res;
        if (archivo) {
            const datos = new FormData();
            datos.append('archivo', archivo);
            datos.append('tema', tema);
            datos.append('lenguaje', 'java');
            res = await fetch('/api/reto/subir-archivo', { method: 'POST', headers: cabecerasConCsrf(), body: datos });
        } else {
            const titulo = enunciado.split('\n')[0].slice(0, 80) || 'Ejercicio propio';
            res = await fetch('/api/reto/subir', {
                method: 'POST',
                headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ titulo, enunciado, lenguaje: 'java', tema })
            });
        }
        const data = await res.json();
        if (!res.ok) {
            panelReto.subir.estado = 'error';
            panelReto.subir.mensaje = data.mensaje || 'No se pudo procesar el enunciado.';
        } else {
            panelReto.subir.estado = 'listo';
            panelReto.subir.detalle = data;
        }
    } catch (e) {
        panelReto.subir.estado = 'error';
        panelReto.subir.mensaje = 'Error de conexión al procesar el enunciado.';
    }
    panelRetoActualizarBotonSubir();
    panelRetoVista02();
}

function panelRetoVista02() {
    const panel = document.getElementById('panel-reto-vista-02');
    if (!panel) return;
    const est = panelReto.subir;
    panel.innerHTML = '';

    if (est.estado === 'error') {
        panel.appendChild(panelRetoCajaError(est.mensaje, panelRetoConvertir));
        return;
    }
    if (est.estado === 'cargando') {
        panel.innerHTML = '<div class="panel-reto-vista-caja cargando">' +
            '<span class="panel-reto-etiqueta-mono">Vista previa</span>' +
            panelRetoFantasmas('Leyendo tu enunciado…') + '</div>';
        return;
    }
    if (est.estado !== 'listo' || !est.detalle) {
        panel.innerHTML = '<div class="panel-reto-vista-caja">' +
            '<span class="panel-reto-etiqueta-mono">Vista previa</span>' +
            '<p class="panel-reto-vista-txt">El tutor leerá tu enunciado y lo dividirá en microhitos. Podrás revisarlos antes de empezar.</p>' +
            panelRetoFantasmas('') + '</div>';
        return;
    }

    const d = est.detalle;
    const caja = document.createElement('div');
    caja.className = 'panel-reto-vista-caja resultado';
    caja.innerHTML =
        '<div class="panel-reto-vista-alto">' +
        (est.tema ? '<span class="reto-tema">' + escaparHtml(est.tema) + '</span>' : '') +
        '<h2 class="panel-reto-vista-titulo">' + escaparHtml(d.titulo) + '</h2>' +
        '</div>' +
        '<div class="panel-reto-vista-centro">' +
        '<span class="panel-reto-etiqueta-mono">Microhitos propuestos</span>' +
        panelRetoHitosHtml(d.microhitos) +
        '<p class="panel-reto-nota">El tutor solo divide el ejercicio en pasos: no lo resuelve.</p>' +
        '</div>' +
        '<div class="panel-reto-vista-bajo">' +
        '<button type="button" class="panel-reto-btn-sec">Editar</button>' +
        '<button type="button" class="panel-reto-btn">Empezar reto</button>' +
        '</div>';
    caja.querySelector('.panel-reto-btn-sec').onclick = () => {
        panelReto.subir.estado = 'vacio';
        panelReto.subir.detalle = null;
        panelRetoVista02();
    };
    caja.querySelector('.panel-reto-btn').onclick = () => panelRetoEmpezar(d.ejercicioId);
    panel.appendChild(caja);
}

/* ---------------------- 03 · Generar con IA ---------------------- */

function panelRetoRestaurarIa() {
    if (panelReto.ia.tema) return;
    try {
        const guardado = JSON.parse(localStorage.getItem(CLAVE_IA_PANEL) || 'null');
        if (guardado && guardado.tema) {
            panelReto.ia.tema = guardado.tema;
            panelReto.ia.dificultad = guardado.dificultad || 'Media';
            return;
        }
    } catch (e) { }
    const temas = panelRetoTemas();
    panelReto.ia.tema = temas.length ? temas[0].valor : 'General';
}

function panelRetoGuardarIa() {
    try {
        localStorage.setItem(CLAVE_IA_PANEL, JSON.stringify({ tema: panelReto.ia.tema, dificultad: panelReto.ia.dificultad }));
    } catch (e) { }
}

function panelRetoPintar03() {
    panelRetoRestaurarIa();
    const chips = document.getElementById('panel-reto-chips-ia');
    if (chips) {
        panelRetoPintarChips(chips, panelRetoTemas(), panelReto.ia.tema,
            v => { if (v) { panelReto.ia.tema = v; panelRetoGuardarIa(); panelRetoPintar03(); } }, false);
    }
    const seg = document.getElementById('panel-reto-dif-ia');
    if (seg) {
        panelRetoPintarSegmentado(seg, DIFICULTADES.map(d => ({ valor: d, etiqueta: d })), panelReto.ia.dificultad,
            v => { panelReto.ia.dificultad = v; panelRetoGuardarIa(); panelRetoPintar03(); });
    }
    const btn = document.getElementById('panel-reto-btn-ia');
    if (btn) {
        btn.innerText = panelReto.ia.detalle ? 'Generar otro' : 'Generar ejercicio';
        btn.disabled = panelReto.ia.estado === 'cargando';
    }
    panelRetoVista03();
}

async function panelRetoGenerar() {
    panelReto.ia.estado = 'cargando';
    panelReto.ia.detalle = null;
    panelRetoPintar03();
    try {
        const res = await fetch('/api/reto/crear-ia', {
            method: 'POST',
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ tema: panelReto.ia.tema, dificultad: panelReto.ia.dificultad, lenguaje: 'java' })
        });
        const data = await res.json();
        if (!res.ok) {
            panelReto.ia.estado = 'error';
            panelReto.ia.mensaje = data.mensaje || 'No se pudo generar el ejercicio.';
        } else {
            panelReto.ia.estado = 'listo';
            panelReto.ia.detalle = data;
        }
    } catch (e) {
        panelReto.ia.estado = 'error';
        panelReto.ia.mensaje = 'Error de conexión al generar el ejercicio.';
    }
    panelRetoPintar03();
}

function panelRetoVista03() {
    const panel = document.getElementById('panel-reto-vista-03');
    if (!panel) return;
    const est = panelReto.ia;
    panel.innerHTML = '';

    if (est.estado === 'error') {
        panel.appendChild(panelRetoCajaError(est.mensaje, panelRetoGenerar));
        return;
    }
    if (est.estado === 'cargando') {
        panel.innerHTML = '<div class="panel-reto-vista-caja cargando">' +
            '<span class="panel-reto-etiqueta-mono">Tu ejercicio</span>' +
            panelRetoFantasmas('Creando tu ejercicio…') + '</div>';
        return;
    }
    if (est.estado !== 'listo' || !est.detalle) {
        panel.innerHTML = '<div class="panel-reto-vista-caja">' +
            '<span class="panel-reto-etiqueta-mono">Tu ejercicio</span>' +
            '<p class="panel-reto-vista-txt">Elige tema y dificultad. El tutor creará un ejercicio nuevo, ya dividido en microhitos.</p>' +
            panelRetoFantasmas('') + '</div>';
        return;
    }

    const d = est.detalle;
    const nHitos = (d.microhitos || []).length;
    const caja = document.createElement('div');
    caja.className = 'panel-reto-vista-caja resultado';
    caja.innerHTML =
        '<div class="panel-reto-vista-alto">' +
        '<div class="panel-reto-vista-meta">' +
        '<span class="reto-tema">' + escaparHtml(est.tema) + '</span>' +
        '<span class="panel-reto-insignia-ia">Generado con IA</span>' +
        '</div>' +
        '<h2 class="panel-reto-vista-titulo">' + escaparHtml(d.titulo) + '</h2>' +
        '<p class="panel-reto-vista-enunciado">' + escaparHtml(d.enunciado || '') + '</p>' +
        '<span class="panel-reto-vista-linea">' + escaparHtml(est.dificultad) + ' · ' + nHitos + ' microhitos</span>' +
        '</div>' +
        '<div class="panel-reto-vista-centro">' +
        '<span class="panel-reto-etiqueta-mono">Microhitos</span>' +
        panelRetoHitosHtml(d.microhitos) +
        '<p class="panel-reto-aviso">Los ejercicios generados pueden contener errores. Si algo no cuadra, díselo al tutor o a tu profesor.</p>' +
        '</div>' +
        '<div class="panel-reto-vista-bajo">' +
        '<button type="button" class="panel-reto-btn-sec">Otro</button>' +
        '<button type="button" class="panel-reto-btn">Empezar reto</button>' +
        '</div>';
    caja.querySelector('.panel-reto-btn-sec').onclick = panelRetoGenerar;
    caja.querySelector('.panel-reto-btn').onclick = () => panelRetoEmpezar(d.ejercicioId);
    panel.appendChild(caja);
}

function panelRetoEmpezar(ejercicioId) {
    retoIniciarDesdeEjercicio(ejercicioId, false, false);
}

/* ---------------------- 04 · Test de teoría ---------------------- */

function panelRetoPintarTest() {
    const cont = document.getElementById('panel-reto-test');
    if (!cont) return;
    if (panelReto.test.fase === 'pregunta') return panelRetoTestPregunta(cont);
    if (panelReto.test.fase === 'resultado') return panelRetoTestResultado(cont);
    panelRetoTestConfig(cont);
}

function panelRetoTestConfig(cont) {
    const t = panelReto.test;
    if (!t.tema) {
        const temas = panelRetoTemas();
        t.tema = temas.length ? temas[0].valor : 'General';
    }
    cont.innerHTML =
        '<div class="panel-reto-test-caja">' +
        '<h2 class="panel-reto-test-titulo">Test de teoría</h2>' +
        '<p class="panel-reto-test-sub">Preguntas cortas de tipo test. Tras cada respuesta, el tutor te explica el porqué.</p>' +
        '<div class="panel-reto-campo"><span class="panel-reto-etiqueta">Tema</span><div class="panel-reto-chips" id="panel-reto-chips-test"></div></div>' +
        '<div class="panel-reto-campo"><span class="panel-reto-etiqueta">Dificultad</span><div class="panel-reto-segmentado" id="panel-reto-dif-test"></div></div>' +
        '<div class="panel-reto-campo"><span class="panel-reto-etiqueta">Número de preguntas</span><div class="panel-reto-segmentado" id="panel-reto-num-test"></div></div>' +
        '<div class="panel-reto-test-pie">' +
        '<button type="button" class="panel-reto-btn" id="panel-reto-btn-test">Empezar test</button>' +
        '<span class="panel-reto-test-nota">No cuenta para tu autonomía</span>' +
        '</div>' +
        (t.mensaje ? '<p class="panel-reto-test-error">' + escaparHtml(t.mensaje) + '</p>' : '') +
        '</div>';

    panelRetoPintarChips(cont.querySelector('#panel-reto-chips-test'), panelRetoTemas(), t.tema,
        v => { if (v) { t.tema = v; panelRetoPintarTest(); } }, false);
    panelRetoPintarSegmentado(cont.querySelector('#panel-reto-dif-test'),
        DIFICULTADES.map(d => ({ valor: d, etiqueta: d })), t.dificultad,
        v => { t.dificultad = v; panelRetoPintarTest(); });
    panelRetoPintarSegmentado(cont.querySelector('#panel-reto-num-test'),
        [5, 10, 15].map(n => ({ valor: n, etiqueta: String(n) })), t.num,
        v => { t.num = v; panelRetoPintarTest(); });
    cont.querySelector('#panel-reto-btn-test').onclick = panelRetoTestEmpezar;
}

async function panelRetoTestEmpezar() {
    const t = panelReto.test;
    const btn = document.getElementById('panel-reto-btn-test');
    if (btn) { btn.disabled = true; btn.innerText = 'Preparando…'; }
    t.mensaje = '';
    try {
        const res = await fetch('/api/reto/test', {
            method: 'POST',
            headers: cabecerasConCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ tema: t.tema, dificultad: t.dificultad, numPreguntas: t.num })
        });
        const data = await res.json();
        if (!res.ok || !Array.isArray(data) || data.length === 0) {
            t.mensaje = (data && data.mensaje) || 'No se han podido generar preguntas. Inténtalo de nuevo.';
            return panelRetoPintarTest();
        }
        t.preguntas = data;
        t.indice = 0;
        t.elegida = null;
        t.comprobada = false;
        t.aciertos = [];
        t.fase = 'pregunta';
    } catch (e) {
        t.mensaje = 'Error de conexión al generar el test.';
    }
    panelRetoPintarTest();
}

function panelRetoTestPregunta(cont) {
    const t = panelReto.test;
    const p = t.preguntas[t.indice];
    const total = t.preguntas.length;

    let segmentos = '';
    for (let i = 0; i < total; i++) {
        let clase = 'pendiente';
        if (i < t.aciertos.length) clase = t.aciertos[i] ? 'acierto' : 'fallo';
        else if (i === t.indice) clase = 'actual';
        segmentos += '<span class="panel-reto-test-seg ' + clase + '"></span>';
    }

    let opciones = '';
    p.opciones.forEach((texto, i) => {
        let clase = '';
        if (t.comprobada) {
            if (i === p.correcta) clase = ' correcta';
            else if (i === t.elegida) clase = ' incorrecta';
            clase += ' bloqueada';
        } else if (i === t.elegida) {
            clase = ' elegida';
        }
        opciones += '<button type="button" class="panel-reto-opcion' + clase + '" data-i="' + i + '">' +
            '<span class="panel-reto-opcion-letra">' + String.fromCharCode(65 + i) + '</span>' +
            '<span>' + escaparHtml(texto) + '</span></button>';
    });

    let feedback = '';
    if (t.comprobada) {
        const bien = t.elegida === p.correcta;
        feedback = '<div class="panel-reto-feedback ' + (bien ? 'bien' : 'mal') + '">' +
            '<span class="panel-reto-feedback-titulo">' + (bien ? 'Correcto' : 'No exactamente') + '</span>' +
            (p.explicacion ? '<p>' + escaparHtml(p.explicacion) + '</p>' : '') +
            '</div>';
    }

    const ultima = t.indice + 1 >= total;
    const textoBoton = !t.comprobada ? 'Comprobar' : (ultima ? 'Ver resultado' : 'Siguiente pregunta');

    cont.innerHTML =
        '<div class="panel-reto-test-barra">' +
        '<span class="panel-reto-test-cuenta">' + (t.indice + 1) + ' / ' + total + '</span>' +
        '<span class="panel-reto-test-segmentos">' + segmentos + '</span>' +
        '<button type="button" class="panel-reto-test-salir">Salir</button>' +
        '</div>' +
        '<div class="panel-reto-test-caja">' +
        '<span class="reto-tema">' + escaparHtml(t.tema) + '</span>' +
        '<h2 class="panel-reto-test-enunciado">' + escaparHtml(p.enunciado) + '</h2>' +
        '<div class="panel-reto-opciones">' + opciones + '</div>' +
        feedback +
        '<div class="panel-reto-test-acciones">' +
        '<button type="button" class="panel-reto-btn" id="panel-reto-btn-avanzar"' +
        (!t.comprobada && t.elegida === null ? ' disabled' : '') + '>' + textoBoton + '</button>' +
        '</div>' +
        '</div>';

    cont.querySelectorAll('.panel-reto-opcion').forEach(btn => {
        btn.tabIndex = 0;
        btn.onclick = () => panelRetoTestElegir(parseInt(btn.dataset.i, 10));
    });
    cont.querySelector('.panel-reto-test-salir').onclick = panelRetoTestSalir;
    cont.querySelector('#panel-reto-btn-avanzar').onclick = panelRetoTestAvanzar;
}

function panelRetoTestElegir(i) {
    if (panelReto.test.comprobada) return;
    panelReto.test.elegida = i;
    panelRetoPintarTest();
}

function panelRetoTestAvanzar() {
    const t = panelReto.test;
    if (!t.comprobada) {
        if (t.elegida === null) return;
        t.comprobada = true;
        t.aciertos.push(t.elegida === t.preguntas[t.indice].correcta);
        return panelRetoPintarTest();
    }
    if (t.indice + 1 >= t.preguntas.length) {
        t.fase = 'resultado';
        return panelRetoPintarTest();
    }
    t.indice++;
    t.elegida = null;
    t.comprobada = false;
    panelRetoPintarTest();
}

function panelRetoTestSalir() {
    panelReto.test.fase = 'config';
    panelReto.test.mensaje = '';
    panelRetoPintarTest();
}

function panelRetoTestResultado(cont) {
    const t = panelReto.test;
    const total = t.preguntas.length;
    const aciertos = t.aciertos.filter(Boolean).length;

    let filas = '';
    t.preguntas.forEach((p, i) => {
        const bien = t.aciertos[i];
        filas += '<li class="panel-reto-res-fila">' +
            '<span class="panel-reto-res-circulo ' + (bien ? 'bien' : 'mal') + '">' + (bien ? '&#10003;' : '&times;') + '</span>' +
            '<span>' + escaparHtml(p.enunciado) + '</span></li>';
    });

    cont.innerHTML =
        '<div class="panel-reto-test-caja">' +
        '<div class="panel-reto-res-nota">' + aciertos + '/' + total + '</div>' +
        '<p class="panel-reto-test-sub">respuestas correctas</p>' +
        '<ul class="panel-reto-res-lista">' + filas + '</ul>' +
        '<div class="panel-reto-test-acciones">' +
        '<button type="button" class="panel-reto-btn-sec" id="panel-reto-btn-tema">Cambiar tema</button>' +
        '<button type="button" class="panel-reto-btn" id="panel-reto-btn-repetir">Repetir test</button>' +
        '</div>' +
        '</div>';
    cont.querySelector('#panel-reto-btn-tema').onclick = panelRetoTestSalir;
    cont.querySelector('#panel-reto-btn-repetir').onclick = panelRetoTestEmpezar;
}

document.addEventListener('keydown', (ev) => {
    if (!panelRetoAbierto() || panelReto.tab !== '04' || panelReto.test.fase !== 'pregunta') return;
    if (ev.target && /^(INPUT|TEXTAREA)$/.test(ev.target.tagName)) return;
    const p = panelReto.test.preguntas[panelReto.test.indice];
    if (!p) return;
    if (ev.key === 'Enter') { ev.preventDefault(); return panelRetoTestAvanzar(); }
    let i = -1;
    if (/^[1-9]$/.test(ev.key)) i = parseInt(ev.key, 10) - 1;
    else if (/^[a-dA-D]$/.test(ev.key)) i = ev.key.toUpperCase().charCodeAt(0) - 65;
    if (i >= 0 && i < p.opciones.length) { ev.preventDefault(); panelRetoTestElegir(i); }
});

/* ---------------------- Arrastrar y soltar (apartado 02) ---------------------- */

document.addEventListener('DOMContentLoaded', () => {
    const zona = document.getElementById('panel-reto-soltar');
    const input = document.getElementById('panel-reto-input-archivo');
    const texto = document.getElementById('panel-reto-enunciado');
    if (zona && input) {
        zona.onclick = () => input.click();
        zona.ondragover = (ev) => { ev.preventDefault(); zona.classList.add('encima'); };
        zona.ondragleave = () => zona.classList.remove('encima');
        zona.ondrop = (ev) => {
            ev.preventDefault();
            zona.classList.remove('encima');
            if (ev.dataTransfer && ev.dataTransfer.files.length) panelRetoElegirArchivo(ev.dataTransfer.files[0]);
        };
        input.onchange = () => panelRetoElegirArchivo(input.files[0]);
    }
    if (texto) texto.addEventListener('input', panelRetoActualizarBotonSubir);
});

/* ---------------------- Utilidades ---------------------- */

function escaparHtml(str) {
    if (str == null) return '';
    return String(str).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
