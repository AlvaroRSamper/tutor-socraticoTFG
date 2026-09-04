    function leerCookie(nombre) {
        const match = document.cookie.match('(^|;)\\s*' + nombre + '\\s*=\\s*([^;]+)');
        return match ? decodeURIComponent(match[2]) : null;
    }

    function comprobarSesion(respuesta) {
        if (respuesta.status === 401 || respuesta.status === 403) {
            window.location.href = '/';
            return false;
        }
        return true;
    }

    const TEMAS_IDE = ['github-dark', 'dracula', 'monokai', 'one-dark'];

    // Aplica un tema de IDE como clase del <body>, preservando la clase body-profesor.
    function aplicarTemaIde(nombre) {
        const tema = TEMAS_IDE.includes(nombre) ? nombre : 'github-dark';
        document.body.classList.remove(...TEMAS_IDE.map(t => 'theme-' + t));
        document.body.classList.add('theme-' + tema);
    }

    window.previsualizarTemaIde = (nombre) => aplicarTemaIde(nombre);

    async function cargarResumen() {
        const respuesta = await fetch('/api/profesor/resumen');
        if (!comprobarSesion(respuesta)) return;
        const datos = await respuesta.json();

        document.getElementById('metrica-total').innerText = datos.totalConsultas;
        document.getElementById('metrica-alumnos').innerText = datos.alumnosActivos;

        pintarGraficoTemas(datos.porTema || []);
        rellenarSelectTemas(datos.porTema || []);
    }

    function pintarGraficoTemas(porTema) {
        const contenedor = document.getElementById('grafico-temas');
        contenedor.innerHTML = '';

        if (porTema.length === 0) {
            const vacio = document.createElement('p');
            vacio.className = 'texto-vacio';
            vacio.innerText = 'Todavía no hay consultas registradas.';
            contenedor.appendChild(vacio);
            return;
        }

        const maximo = Math.max(...porTema.map(t => t.total));
        porTema.forEach(t => {
            const fila = document.createElement('div');
            fila.className = 'barra-fila';

            const etiqueta = document.createElement('div');
            etiqueta.className = 'barra-etiqueta';
            etiqueta.innerText = t.clave || '(sin tema)';

            const pista = document.createElement('div');
            pista.className = 'barra-pista';
            const relleno = document.createElement('div');
            relleno.className = 'barra-relleno';
            relleno.style.width = Math.round(100 * t.total / maximo) + '%';
            relleno.innerText = t.total;
            pista.appendChild(relleno);

            fila.appendChild(etiqueta);
            fila.appendChild(pista);
            contenedor.appendChild(fila);
        });
    }

    async function cargarPerfiles() {
        const respuesta = await fetch('/api/profesor/perfiles');
        if (!comprobarSesion(respuesta)) return;
        const perfiles = await respuesta.json();

        const contenedor = document.getElementById('grafico-perfiles');
        contenedor.innerHTML = '';

        if (perfiles.length === 0) {
            const vacio = document.createElement('p');
            vacio.className = 'texto-vacio';
            vacio.innerText = 'Ningún alumno ha registrado preferencias todavía.';
            contenedor.appendChild(vacio);
            return;
        }

        perfiles.forEach(p => {
            const fila = document.createElement('div');
            fila.className = 'barra-fila';

            const etiqueta = document.createElement('div');
            etiqueta.className = 'barra-etiqueta';
            const racha = p.racha > 0
                ? ` <span title="Racha actual de días seguidos" style="color:#f0883e;font-weight:600;font-size:.85rem;margin-left:6px;white-space:nowrap;">🔥${p.racha}</span>`
                : '';
            etiqueta.innerHTML = escaparHtmlP(p.alumno) + racha;

            const barra = document.createElement('div');
            barra.className = 'barra-dual';

            const teorico = document.createElement('div');
            teorico.className = 'segmento teorico';
            teorico.style.width = p.porcentajeTeorico + '%';
            // Si el segmento es muy estrecho, el rótulo no cabe: se omite
            if (p.porcentajeTeorico >= 14) teorico.textContent = p.porcentajeTeorico + '% T';
            teorico.title = 'Teórico: ' + p.porcentajeTeorico + '%';

            const practico = document.createElement('div');
            practico.className = 'segmento practico';
            practico.style.width = p.porcentajePractico + '%';
            if (p.porcentajePractico >= 14) practico.textContent = p.porcentajePractico + '% P';
            practico.title = 'Práctico: ' + p.porcentajePractico + '%';

            barra.appendChild(teorico);
            barra.appendChild(practico);

            const muestras = document.createElement('div');
            muestras.className = 'barra-muestras';
            muestras.textContent = p.muestras + (p.muestras === 1 ? ' respuesta' : ' respuestas');

            const utilidad = document.createElement('div');
            utilidad.className = 'barra-utilidad';
            utilidad.style.marginLeft = '20px';
            utilidad.style.color = '#10b981';
            utilidad.style.fontWeight = '500';
            utilidad.style.width = '75px';
            utilidad.style.textAlign = 'right';
            utilidad.textContent = p.porcentajeUtil + '% útil';

            fila.appendChild(etiqueta);
            fila.appendChild(barra);
            fila.appendChild(muestras);
            fila.appendChild(utilidad);
            contenedor.appendChild(fila);
        });
    }

    function rellenarSelectTemas(porTema) {
        const select = document.getElementById('filtro-tema');
        const seleccionado = select.value;
        // Conservar la opción "Todos" y regenerar el resto.
        select.innerHTML = '<option value="">Todos los temas</option>';
        porTema.forEach(t => {
            if (!t.clave) return;
            const opcion = document.createElement('option');
            opcion.value = t.clave;
            opcion.text = t.clave;
            select.appendChild(opcion);
        });
        select.value = seleccionado;
    }

    function construirParametros() {
        const params = new URLSearchParams();
        const alumno = document.getElementById('filtro-alumno').value.trim();
        const tema = document.getElementById('filtro-tema').value;
        const desde = document.getElementById('filtro-desde').value;
        const hasta = document.getElementById('filtro-hasta').value;
        if (alumno) params.set('alumno', alumno);
        if (tema) params.set('tema', tema);
        if (desde) params.set('desde', desde);
        if (hasta) params.set('hasta', hasta);
        return params;
    }

    async function cargarConsultas() {
        const params = construirParametros();
        const respuesta = await fetch('/api/profesor/consultas?' + params.toString());
        if (!comprobarSesion(respuesta)) return;
        const consultas = await respuesta.json();

        const cuerpo = document.getElementById('cuerpo-tabla');
        cuerpo.innerHTML = '';

        if (consultas.length === 0) {
            const fila = document.createElement('tr');
            const celda = document.createElement('td');
            celda.colSpan = 6;
            celda.className = 'texto-vacio';
            celda.innerText = 'No hay consultas con estos filtros.';
            fila.appendChild(celda);
            cuerpo.appendChild(fila);
            return;
        }

        consultas.forEach(c => cuerpo.appendChild(construirFila(c)));
    }

    function construirFila(c) {
        const fila = document.createElement('tr');
        fila.className = 'fila-consulta';

        fila.appendChild(celda(formatearFecha(c.fechaHora)));
        fila.appendChild(celda(c.username));
        fila.appendChild(celda(c.tema || '—'));
        fila.appendChild(celda(c.tipo));
        fila.appendChild(celdaTexto(c.pregunta));
        fila.appendChild(celdaTexto(c.respuesta));
        let valStr = '—';
        if (c.valoracion === 1) valStr = '👍';
        if (c.valoracion === -1) valStr = '👎';
        fila.appendChild(celda(valStr));
        fila.appendChild(celdaTexto(c.comentarioValoracion || ''));

        fila.addEventListener('click', () => fila.classList.toggle('expandida'));
        return fila;
    }

    function celda(texto) {
        const td = document.createElement('td');
        td.textContent = texto;
        return td;
    }

    function celdaTexto(texto) {
        const td = document.createElement('td');
        td.className = 'celda-texto';
        td.textContent = texto || '';
        return td;
    }

    function formatearFecha(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        if (isNaN(d)) return iso;
        return d.toLocaleString('es-ES');
    }

    function aplicarFiltros() {
        cargarConsultas();
    }

    function descargarInforme() {
        window.location.href = '/api/profesor/informe?' + construirParametros().toString();
    }

    async function cargarInfoAsignatura() {
        try {
            const res = await fetch('/api/profesor/asignatura/info');
            if (!comprobarSesion(res)) return;
            if (res.ok) {
                const info = await res.json();
                if (info.titulo) {
                    document.title = info.titulo + ' · Panel del Profesor';
                    const h1 = document.getElementById('titulo-profesor');
                    if (h1) h1.innerText = info.titulo;
                }
                if (info.colorTema) {
                    aplicarTemaIde(info.colorTema);
                }
            }
        } catch (e) {
            console.error('Error cargando info asignatura', e);
        }
    }

    window.abrirModalConfiguracion = async () => {
        const modal = document.getElementById('modal-configuracion');
        const estado = document.getElementById('estado-asignatura-modal');
        if (estado) estado.textContent = '';
        try {
            const res = await fetch('/api/profesor/asignatura/info');
            if (res.ok) {
                const info = await res.json();
                const inpTit = document.getElementById('input-titulo-modal');
                const inpPrm = document.getElementById('input-prompt-modal');
                if (inpTit) inpTit.value = info.titulo || '';
                if (inpPrm) inpPrm.value = info.systemPrompt || '';
                const tema = info.colorTema || 'github-dark';
                const selTema = document.getElementById('select-tema-ide');
                if (selTema) selTema.value = tema;
                aplicarTemaIde(tema);
                const inpSens = document.getElementById('input-sensibilidad-modal');
                if (inpSens) { inpSens.value = info.sensibilidad || 5; actualizarValorSensibilidad(inpSens.value); }
                const inpEmail = document.getElementById('input-email-modal');
                if (inpEmail) inpEmail.value = info.emailProfesor || '';
                const selDia = document.getElementById('select-dia-informe-modal');
                if (selDia) selDia.value = info.diaInformeSemanal != null ? String(info.diaInformeSemanal) : '';

                const listaCont = document.getElementById('lista-archivos-subidos');
                if (listaCont) {
                    listaCont.innerHTML = '';
                    if (info.temas && info.temas.trim() !== "") {
                        const archivos = info.temas.split(',');
                        archivos.forEach(arc => {
                            const nom = arc.trim();
                            if (!nom) return;
                            const item = document.createElement('div');
                            item.style = "display: flex; justify-content: space-between; align-items: center; background: #21262d; padding: 6px 10px; border-radius: 6px; margin-bottom: 6px; font-size: 0.85rem; color: #c9d1d9; border: 1px solid #30363d;";
                            item.innerHTML = `
                                <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 80%;" title="${nom}">📄 ${nom}</span>
                                <button type="button" onclick="borrarArchivoSubido('${nom}')" style="background: none; border: none; color: #f85149; cursor: pointer; font-weight: bold; font-size: 1.1rem; padding: 0 4px;" title="Borrar este PDF">&times;</button>
                            `;
                            listaCont.appendChild(item);
                        });
                    } else {
                        listaCont.innerHTML = '<p style="margin: 0; font-size: 0.8rem; color: #8b949e; text-align: center;">No hay archivos subidos previamente</p>';
                    }
                }
            }
        } catch (e) {
            console.error('Error al abrir modal', e);
        }
        if (modal) modal.style.display = 'flex';
    };

    window.borrarArchivoSubido = async (nom) => {
        if (!confirm(`¿Estás seguro de que quieres eliminar el archivo "${nom}" y sus conocimientos del tutor?`)) return;
        const estado = document.getElementById('estado-asignatura-modal');
        if (estado) estado.textContent = 'Borrando archivo…';
        try {
            const res = await fetch(`/api/profesor/asignatura/borrar-archivo?nombreArchivo=${encodeURIComponent(nom)}`, {
                method: 'POST',
                headers: { 'X-XSRF-TOKEN': leerCookie('XSRF-TOKEN') }
            });
            if (res.ok) {
                if (estado) estado.textContent = 'Archivo borrado correctamente.';
                abrirModalConfiguracion();
            } else {
                if (estado) estado.textContent = 'Error al borrar el archivo.';
            }
        } catch (e) {
            console.error('Error borrando archivo', e);
            if (estado) estado.textContent = 'Error de red al borrar.';
        }
    };

    window.cerrarModalConfiguracion = () => {
        const modal = document.getElementById('modal-configuracion');
        if (modal) modal.style.display = 'none';
    };

    window.actualizarNombreArchivos = (input) => {
        const texto = document.getElementById('texto-archivos-seleccionados');
        if (!texto) return;
        if (!input.files || input.files.length === 0) {
            texto.textContent = 'No hay archivos nuevos seleccionados';
        } else if (input.files.length === 1) {
            texto.textContent = `1 archivo seleccionado: ${input.files[0].name}`;
        } else {
            texto.textContent = `${input.files.length} archivos seleccionados`;
        }
    };

    window.guardarConfiguracionModal = async (evento) => {
        evento.preventDefault();
        const estado = document.getElementById('estado-asignatura-modal');
        const titulo = document.getElementById('input-titulo-modal').value;
        const prompt = document.getElementById('input-prompt-modal').value;
        const colorTema = document.getElementById('select-tema-ide').value;
        const archivos = document.getElementById('input-apuntes-modal').files;

        const inpSens = document.getElementById('input-sensibilidad-modal');
        const inpEmail = document.getElementById('input-email-modal');
        const selDia = document.getElementById('select-dia-informe-modal');
        const datos = new FormData();
        datos.append('titulo', titulo);
        datos.append('systemPrompt', prompt);
        datos.append('colorTema', colorTema);
        if (inpSens) datos.append('sensibilidad', inpSens.value);
        if (inpEmail) datos.append('emailProfesor', inpEmail.value.trim());
        if (selDia) datos.append('diaInformeSemanal', selDia.value === '' ? '0' : selDia.value);
        for (let i = 0; i < archivos.length; i++) {
            datos.append('archivos', archivos[i]);
        }

        if (estado) estado.textContent = 'Guardando configuración e indexando PDF…';
        const respuesta = await fetch('/api/profesor/asignatura/configurar', {
            method: 'POST',
            headers: { 'X-XSRF-TOKEN': leerCookie('XSRF-TOKEN') },
            body: datos
        });
        if (!comprobarSesion(respuesta)) return;

        if (respuesta.ok) {
            const resultado = await respuesta.json();
            if (titulo) {
                document.title = titulo + ' · Panel del Profesor';
                const h1 = document.getElementById('titulo-profesor');
                if (h1) h1.innerText = titulo;
            }
            if (colorTema) {
                aplicarTemaIde(colorTema);
            }
            if (estado) estado.textContent = '¡Guardado correctamente! Cerrando panel…';
            setTimeout(() => {
                cerrarModalConfiguracion();
                const inpApu = document.getElementById('input-apuntes-modal');
                if (inpApu) {
                    inpApu.value = '';
                    actualizarNombreArchivos(inpApu);
                }
            }, 1000);
        } else {
            const error = await respuesta.json().catch(() => ({}));
            if (estado) estado.textContent = error.error || 'Error al guardar la configuración.';
        }
    };

    async function cerrarSesion() {
        try {
            await fetch('/logout', {
                method: 'POST',
                headers: { 'X-XSRF-TOKEN': leerCookie('XSRF-TOKEN') }
            });
        } catch (e) {
            console.error('Error cerrando sesión', e);
        }
        window.location.href = '/';
    }

    let textoRadarMdActual = "";

    function ocultarVistasRadar() {
        ['radar-chooser', 'contenido-radar-carga', 'contenido-radar-listo', 'contenido-problemas']
            .forEach(id => { const el = document.getElementById(id); if (el) el.style.display = 'none'; });
    }

    window.abrirRadarDocente = () => {
        const modal = document.getElementById('modal-radar');
        ocultarVistasRadar();
        const chooser = document.getElementById('radar-chooser');
        if (chooser) chooser.style.display = 'block';
        if (modal) modal.style.display = 'flex';
    };

    window.volverChooserRadar = () => {
        ocultarVistasRadar();
        const chooser = document.getElementById('radar-chooser');
        if (chooser) chooser.style.display = 'block';
    };

    // Opción 1: Generar Informe (visión general de la clase)
    window.radarGenerarInforme = async () => {
        ocultarVistasRadar();
        const carga = document.getElementById('contenido-radar-carga');
        const listo = document.getElementById('contenido-radar-listo');
        if (carga) carga.style.display = 'block';
        try {
            const sel = document.getElementById('radar-semanas');
            const semanas = sel ? sel.value : '2';
            const res = await fetch('/api/profesor/asignatura/radar-confusion?semanas=' + encodeURIComponent(semanas));
            if (!comprobarSesion(res)) return;
            const json = await res.json();
            textoRadarMdActual = json.analisis || "No se pudo generar el análisis del radar.";
            const ren = document.getElementById('radar-render');
            if (ren && typeof marked !== 'undefined') {
                marked.setOptions({ breaks: true });
                ren.innerHTML = marked.parse(textoRadarMdActual);
            } else if (ren) {
                ren.innerText = textoRadarMdActual;
            }
            if (carga) carga.style.display = 'none';
            if (listo) listo.style.display = 'block';
        } catch (e) {
            console.error("Error cargando Informe IA", e);
            const ren = document.getElementById('radar-render');
            if (ren) ren.innerHTML = "<p style='color:#f85149;'>Error de conexión al obtener el informe.</p>";
            if (carga) carga.style.display = 'none';
            if (listo) listo.style.display = 'block';
        }
    };

    // Opción 2: Listado de alumnos con problemas (algoritmo de estancamiento)
    window.radarAlumnosProblemas = async () => {
        ocultarVistasRadar();
        const cont = document.getElementById('contenido-problemas');
        const wrap = document.getElementById('tabla-problemas-wrap');
        if (cont) cont.style.display = 'block';
        if (wrap) wrap.innerHTML = '<p class="texto-vacio">Cargando…</p>';
        try {
            const res = await fetch('/api/profesor/estancamiento/alumnos');
            if (!comprobarSesion(res)) return;
            const lista = await res.json();
            if (!Array.isArray(lista) || lista.length === 0) {
                wrap.innerHTML = '<p class="texto-vacio">🎉 Ningún alumno con abandono ahora mismo.</p>';
                return;
            }
            let html = '<div class="tabla-scroll"><table><thead><tr>' +
                '<th>Alumno</th><th>Asignatura</th><th>Ámbito</th><th>Último tema</th><th>Consultas previas</th><th>Acción</th>' +
                '</tr></thead><tbody>';
            lista.forEach(a => {
                const ambito = a.ambito === 'RETO' ? 'Reto' : 'Chat';
                html += `<tr>
                    <td>${escaparHtmlP(a.alumno)}</td>
                    <td>${escaparHtmlP(a.asignaturaId)}</td>
                    <td>${ambito}</td>
                    <td title="${escaparHtmlP(a.preguntaEjemplo)}"><span class="badge-estancado">${escaparHtmlP(a.tema)}</span></td>
                    <td>${a.iteraciones}</td>
                    <td><button class="btn-ejercicio" style="padding:4px 10px;" onclick="resolverEstancamiento(${a.avisoId})">✓ Resolver</button></td>
                </tr>`;
            });
            html += '</tbody></table></div>';
            wrap.innerHTML = html;
        } catch (e) {
            console.error('Error cargando alumnos con problemas', e);
            if (wrap) wrap.innerHTML = "<p style='color:#f85149;'>Error de conexión al obtener el listado.</p>";
        }
    };

    window.resolverEstancamiento = async (avisoId) => {
        try {
            const res = await fetch('/api/profesor/estancamiento/resolver?avisoId=' + encodeURIComponent(avisoId), {
                method: 'POST',
                headers: { 'X-XSRF-TOKEN': leerCookie('XSRF-TOKEN') }
            });
            if (!comprobarSesion(res)) return;
            radarAlumnosProblemas();
        } catch (e) {
            console.error('Error resolviendo aviso', e);
        }
    };

    window.probarInformeSemanal = async () => {
        const estado = document.getElementById('estado-asignatura-modal');
        const email = document.getElementById('input-email-modal');
        if (email && !email.value.trim()) {
            if (estado) estado.textContent = 'Introduce y guarda tu correo antes de enviar el informe de prueba.';
            return;
        }
        if (estado) estado.textContent = 'Enviando informe de prueba…';
        try {
            const res = await fetch('/api/profesor/asignatura/informe-semanal/probar', {
                method: 'POST',
                headers: { 'X-XSRF-TOKEN': leerCookie('XSRF-TOKEN') }
            });
            if (!comprobarSesion(res)) return;
            const data = await res.json();
            if (estado) estado.textContent = data.exito
                ? 'Informe de prueba enviado. Revisa tu bandeja de entrada.'
                : 'No se pudo enviar. Guarda primero el correo y revisa la configuración SMTP.';
        } catch (e) {
            if (estado) estado.textContent = 'Error de conexión al enviar el informe.';
        }
    };

    window.actualizarValorSensibilidad = (v) => {
        const el = document.getElementById('valor-sensibilidad-modal');
        if (el) el.textContent = v + (v === '1' ? ' pregunta' : ' preguntas');
    };

    function escaparHtmlP(str) {
        if (str == null) return '';
        return String(str).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    window.cerrarModalRadar = () => {
        const modal = document.getElementById('modal-radar');
        if (modal) modal.style.display = 'none';
    };

    window.imprimirRadarPdf = () => {
        const ren = document.getElementById('radar-render');
        if (!ren) return;
        const ventana = window.open('', '_blank');
        ventana.document.write(`
            <html>
            <head>
                <title>Radar de Confusión IA · Tutor Socrático</title>
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap" rel="stylesheet">
                <style>
                    body { font-family: 'Inter', sans-serif; color: #222; line-height: 1.6; padding: 40px; max-width: 850px; margin: 0 auto; }
                    h1, h2, h3 { color: #d29922; border-bottom: 1px solid #ddd; padding-bottom: 6px; }
                    code { background: #f4f4f4; padding: 2px 6px; border-radius: 4px; font-family: monospace; }
                    pre { background: #f4f4f4; padding: 15px; border-radius: 6px; overflow-x: auto; }
                    blockquote { border-left: 4px solid #d29922; margin: 0; padding-left: 15px; color: #555; }
                </style>
            </head>
            <body>
                <h1 style="text-align: center; border: none;">📡 Radar de Confusión y Puntos Ciegos</h1>
                <p style="text-align: center; color: #666; font-size: 0.9em; margin-bottom: 30px;">Informe Pedagógico IA · Asignatura</p>
                <hr style="border: 0; border-top: 2px solid #d29922; margin-bottom: 30px;">
                ${ren.innerHTML}
            </body>
            </html>
        `);
        ventana.document.close();
        ventana.focus();
        setTimeout(() => { ventana.print(); }, 500);
    };

    window.onload = () => {
        cargarInfoAsignatura();
        cargarResumen();
        cargarPerfiles();
        cargarConsultas();
    };
