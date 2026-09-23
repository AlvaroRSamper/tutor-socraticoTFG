/* ==========================================================================
 *  Modo Reto / Microhitos  (Modo Profesor): creador de propuestos + Radar Docente
 * ========================================================================== */

function retoLeerCookie(nombre) {
    const match = document.cookie.match('(^|;)\\s*' + nombre + '\\s*=\\s*([^;]+)');
    return match ? decodeURIComponent(match[2]) : null;
}

function retoCabecerasCsrf(extra) {
    return Object.assign({ 'X-XSRF-TOKEN': retoLeerCookie('XSRF-TOKEN') }, extra || {});
}

function escaparHtmlProf(str) {
    if (str == null) return '';
    return String(str).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/* ---------------------- Modo reto exclusivo ---------------------- */

function pintarEstadoModoReto(activo) {
    const check = document.getElementById('check-modo-reto');
    const bloque = document.getElementById('bloque-modo-reto');
    const estado = document.getElementById('estado-modo-reto');
    if (check) check.checked = activo;
    if (bloque) bloque.classList.toggle('activo', activo);
    if (estado) {
        estado.className = activo ? 'estado-msg ok' : 'estado-msg';
        estado.innerText = activo
            ? '✓ Activo: tus alumnos solo ven los retos propuestos.'
            : 'Desactivado: tus alumnos tienen disponibles todas las funcionalidades.';
    }
}

async function cargarModoReto() {
    try {
        const res = await fetch('/api/profesor/modo-reto');
        if (!res.ok) return;
        const data = await res.json();
        pintarEstadoModoReto(!!data.activo);
    } catch (e) {
        const estado = document.getElementById('estado-modo-reto');
        if (estado) {
            estado.className = 'estado-msg error';
            estado.innerText = 'No se pudo consultar el estado del modo reto.';
        }
    }
}

async function guardarModoReto(activo) {
    const estado = document.getElementById('estado-modo-reto');
    if (estado) {
        estado.className = 'estado-msg';
        estado.innerText = 'Guardando…';
    }
    try {
        const res = await fetch('/api/profesor/modo-reto?activo=' + (activo ? 'true' : 'false'), {
            method: 'POST',
            headers: retoCabecerasCsrf()
        });
        if (!res.ok) throw new Error('fallo');
        const data = await res.json();
        pintarEstadoModoReto(!!data.activo);
    } catch (e) {
        pintarEstadoModoReto(!activo);
        if (estado) {
            estado.className = 'estado-msg error';
            estado.innerText = 'No se pudo guardar el cambio. Inténtalo de nuevo.';
        }
    }
}

/* ---------------------- Creador de ejercicios propuestos ---------------------- */

let contadorHitos = 0;

function agregarFilaHito(titulo, descripcion, criterio) {
    contadorHitos++;
    const cont = document.getElementById('prop-hitos');
    const fila = document.createElement('div');
    fila.className = 'hito-editor-fila';
    fila.dataset.hito = contadorHitos;
    fila.innerHTML = `
        <div class="orden-badge">${cont.children.length + 1}</div>
        <div class="hito-editor-campos">
            <input type="text" class="hito-titulo" placeholder="Título del hito (ej: Crear struct)" value="${escaparHtmlProf(titulo || '')}">
            <input type="text" class="hito-desc" placeholder="Descripción (qué debe lograr el alumno)" value="${escaparHtmlProf(descripcion || '')}">
            <input type="text" class="hito-criterio" placeholder="Criterio de validación (cómo saber si el código lo cumple)" value="${escaparHtmlProf(criterio || '')}">
        </div>
        <button type="button" class="btn-reiniciar" onclick="eliminarFilaHito(this)" title="Eliminar">✕</button>`;
    cont.appendChild(fila);
}

function eliminarFilaHito(boton) {
    boton.closest('.hito-editor-fila').remove();
    renumerarHitos();
}

function renumerarHitos() {
    document.querySelectorAll('#prop-hitos .orden-badge').forEach((b, i) => b.innerText = i + 1);
}

async function cargarEnunciadoDesdeArchivo() {
    const input = document.getElementById('prop-archivo');
    const archivo = input && input.files ? input.files[0] : null;
    const estado = document.getElementById('estado-prop');
    if (!archivo) {
        estado.className = 'estado-msg error';
        estado.innerText = 'Elige un archivo PDF, Markdown o TXT.';
        return;
    }
    estado.className = 'estado-msg';
    estado.innerText = 'Extrayendo el enunciado del archivo…';
    const datos = new FormData();
    datos.append('archivo', archivo);
    try {
        const res = await fetch('/api/reto/profesor/extraer-texto', {
            method: 'POST',
            headers: retoCabecerasCsrf(),
            body: datos
        });
        const data = await res.json();
        if (!res.ok) {
            estado.className = 'estado-msg error';
            estado.innerText = data.mensaje || 'No se pudo procesar el archivo.';
            return;
        }
        document.getElementById('prop-enunciado').value = data.texto || '';
        estado.className = 'estado-msg ok';
        estado.innerText = '✓ Enunciado cargado. Revísalo y genera los microhitos.';
    } catch (e) {
        estado.className = 'estado-msg error';
        estado.innerText = 'Error de conexión al procesar el archivo.';
    }
}

async function generarMicrohitosIa() {
    const enunciado = document.getElementById('prop-enunciado').value.trim();
    const tema = document.getElementById('prop-tema').value.trim();
    const estado = document.getElementById('estado-prop');
    if (!enunciado) {
        estado.className = 'estado-msg error';
        estado.innerText = 'Escribe primero el enunciado para generar los microhitos.';
        return;
    }
    const boton = document.getElementById('btn-generar-hitos');
    boton.disabled = true;
    estado.className = 'estado-msg';
    estado.innerText = 'Generando microhitos con IA…';
    try {
        const res = await fetch('/api/reto/profesor/microhitos', {
            method: 'POST',
            headers: retoCabecerasCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ enunciado, lenguaje: 'java', tema })
        });
        if (!res.ok) {
            estado.className = 'estado-msg error';
            estado.innerText = 'No se pudieron generar los microhitos.';
            return;
        }
        const hitos = await res.json();
        if (!Array.isArray(hitos) || hitos.length === 0) {
            estado.className = 'estado-msg error';
            estado.innerText = 'La IA no devolvió microhitos. Prueba a detallar más el enunciado.';
            return;
        }
        document.getElementById('prop-hitos').innerHTML = '';
        hitos.forEach(h => agregarFilaHito(h.titulo, h.descripcion, h.criterioValidacion));
        estado.className = 'estado-msg ok';
        estado.innerText = '✓ ' + hitos.length + ' microhitos generados. Revísalos y edítalos antes de publicar.';
    } catch (e) {
        estado.className = 'estado-msg error';
        estado.innerText = 'Error de conexión al generar los microhitos.';
    } finally {
        boton.disabled = false;
    }
}

async function publicarPropuesto(event) {
    event.preventDefault();
    const titulo = document.getElementById('prop-titulo').value.trim();
    const enunciado = document.getElementById('prop-enunciado').value.trim();
    const dificultad = document.getElementById('prop-dificultad').value;
    const tema = document.getElementById('prop-tema').value.trim();

    const microhitos = [];
    document.querySelectorAll('#prop-hitos .hito-editor-fila').forEach((fila, i) => {
        const t = fila.querySelector('.hito-titulo').value.trim();
        if (!t) return;
        microhitos.push({
            orden: i + 1,
            titulo: t,
            descripcion: fila.querySelector('.hito-desc').value.trim(),
            criterioValidacion: fila.querySelector('.hito-criterio').value.trim()
        });
    });

    const estado = document.getElementById('estado-prop');
    if (microhitos.length === 0) { estado.className = 'estado-msg error'; estado.innerText = 'Añade al menos un microhito.'; return; }

    const boton = document.getElementById('btn-publicar-prop');
    boton.disabled = true;
    estado.className = 'estado-msg';
    estado.innerText = 'Publicando…';
    try {
        const res = await fetch('/api/reto/profesor/publicar', {
            method: 'POST',
            headers: retoCabecerasCsrf({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ titulo, enunciado, dificultad, tema, lenguaje: 'java', microhitos })
        });
        const data = await res.json();
        if (!res.ok) { estado.className = 'estado-msg error'; estado.innerText = data.mensaje || 'Error al publicar.'; return; }
        estado.className = 'estado-msg ok';
        estado.innerText = '✓ ' + (data.mensaje || 'Publicado.');
        document.getElementById('form-propuesto').reset();
        document.getElementById('prop-hitos').innerHTML = '';
        agregarFilaHito();
        cargarRadarDocente();
    } catch (e) {
        estado.className = 'estado-msg error';
        estado.innerText = 'Error de conexión al publicar.';
    } finally {
        boton.disabled = false;
    }
}

/* ---------------------- Radar Docente ---------------------- */

function formatearTiempoProf(seg) {
    if (seg == null) return '—';
    const h = Math.floor(seg / 3600), m = Math.floor((seg % 3600) / 60), s = seg % 60;
    const mm = String(m).padStart(2, '0'), ss = String(s).padStart(2, '0');
    return h > 0 ? `${h}h ${mm}m` : `${mm}:${ss}`;
}

async function borrarPropuesto(ejercicioId, titulo, nAlumnos) {
    const aviso = nAlumnos > 0
        ? `

OJO: ${nAlumnos} alumno(s) ya lo han empezado. Se borrará también su progreso y su seguimiento.`
        : '';
    if (!confirm(`¿Borrar el reto "${titulo}"?${aviso}

Dejará de verse en el panel de tus alumnos. No se puede deshacer.`)) return;
    const estado = document.getElementById('estado-radar');
    if (estado) {
        estado.className = 'estado-msg';
        estado.innerText = 'Borrando…';
    }
    try {
        const res = await fetch('/api/reto/profesor/ejercicio/' + ejercicioId, {
            method: 'DELETE',
            headers: retoCabecerasCsrf()
        });
        const data = await res.json();
        if (!res.ok) {
            if (estado) {
                estado.className = 'estado-msg error';
                estado.innerText = data.mensaje || 'No se pudo borrar el reto.';
            }
            return;
        }
        if (estado) {
            estado.className = 'estado-msg ok';
            estado.innerText = data.mensaje;
        }
        cargarRadarDocente();
    } catch (e) {
        if (estado) {
            estado.className = 'estado-msg error';
            estado.innerText = 'Error de conexión al borrar el reto.';
        }
    }
}

async function cargarRadarDocente() {
    const cont = document.getElementById('radar-docente');
    cont.innerHTML = '<p class="texto-vacio">Cargando…</p>';
    try {
        const res = await fetch('/api/reto/profesor/radar');
        const lista = await res.json();
        if (!Array.isArray(lista) || lista.length === 0) {
            cont.innerHTML = '<p class="texto-vacio">Aún no has publicado ejercicios propuestos.</p>';
            return;
        }
        cont.innerHTML = '';
        lista.forEach(e => {
            const div = document.createElement('div');
            div.className = 'radar-ejercicio';
            const autonomiaMedia = (e.autonomiaMedia != null ? e.autonomiaMedia : e.independenciaMedia);
            const indepMedia = autonomiaMedia != null ? autonomiaMedia + '%' : '—';
            const autoriaMedia = e.autoriaMedia != null ? e.autoriaMedia + '%' : '—';
            let filas = '';
            if (e.alumnos && e.alumnos.length > 0) {
                filas = '<div class="tabla-scroll" style="margin-top:10px;"><table><thead><tr>' +
                    '<th>Alumno</th><th>Estado</th>' +
                    '<th title="Cuánto lo resolvió sin depender del tutor (orientativo)">Autonomía</th>' +
                    '<th title="Código propio vs copiado del tutor (orientativo, no acusación de copia)">Autoría</th>' +
                    '<th>Tiempo</th></tr></thead><tbody>';
                e.alumnos.forEach(a => {
                    const badge = a.estado === 'COMPLETADO' ? '✅ Completado' : (a.estado === 'ABANDONADO' ? '⏹ Abandonado' : '⏳ En progreso');
                    const autonomia = (a.autonomia != null ? a.autonomia : a.independencia);
                    filas += `<tr>
                        <td>${escaparHtmlProf(a.alumno)}</td>
                        <td>${badge}</td>
                        <td>${autonomia != null ? autonomia + '%' : '—'}</td>
                        <td>${a.autoria != null ? a.autoria + '%' : '—'}</td>
                        <td>${formatearTiempoProf(a.tiempoSegundos)}</td>
                    </tr>`;
                });
                filas += '</tbody></table></div>';
            } else {
                filas = '<p class="texto-vacio" style="margin-top:8px;">Ningún alumno ha empezado este reto todavía.</p>';
            }
            div.innerHTML = `
                <div class="cab">
                    <span class="titulo">${escaparHtmlProf(e.titulo)}</span>
                    <span class="radar-chips">
                        <span class="radar-chip">${e.nCompletados}/${e.nAlumnos} completados</span>
                        <span class="radar-chip indep">Autonomía media: ${indepMedia}</span>
                        <span class="radar-chip indep">Autoría media: ${autoriaMedia}</span>
                        <button type="button" class="btn-borrar-reto" title="Borrar este reto">Borrar</button>
                    </span>
                </div>
                ${filas}`;
            div.querySelector('.btn-borrar-reto').onclick = () => borrarPropuesto(e.ejercicioId, e.titulo, e.nAlumnos);
            cont.appendChild(div);
        });
    } catch (e) {
        cont.innerHTML = '<p class="texto-error">Error de conexión al cargar el Radar Docente.</p>';
    }
}

/* ---------------------- Init ---------------------- */

document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('prop-hitos')) agregarFilaHito();
    if (document.getElementById('radar-docente')) cargarRadarDocente();
    if (document.getElementById('check-modo-reto')) cargarModoReto();
});
