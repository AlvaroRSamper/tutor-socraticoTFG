const TEMAS_VISUALES = [
    { id: 'github-dark', nombre: 'GitHub Dark', muestra: '#7d9ec4', fondo: '#14171c' },
    { id: 'dracula', nombre: 'Dracula', muestra: '#b0a0d8', fondo: '#1c1a24' },
    { id: 'monokai', nombre: 'Monokai', muestra: '#a8bf7a', fondo: '#1e1f1a' },
    { id: 'one-dark', nombre: 'One Dark', muestra: '#8aa9c9', fondo: '#1b1e24' },
    { id: 'noche', nombre: 'Noche (cálido)', muestra: '#c3a077', fondo: '#191613' },
    { id: 'papel', nombre: 'Papel (claro)', muestra: '#4f6b86', fondo: '#f5f1e9' },
    { id: 'niebla', nombre: 'Niebla (claro frío)', muestra: '#55707f', fondo: '#eef1f3' }
];
const CLAVE_TEMA = 'tutor_tema_visual';

function temaGuardado() {
    try {
        const guardado = localStorage.getItem(CLAVE_TEMA);
        return TEMAS_VISUALES.some(t => t.id === guardado) ? guardado : 'github-dark';
    } catch (e) {
        return 'github-dark';
    }
}

function aplicarTema(id) {
    const tema = TEMAS_VISUALES.some(t => t.id === id) ? id : 'github-dark';
    document.body.classList.remove(...TEMAS_VISUALES.map(t => 'theme-' + t.id));
    document.body.classList.add('theme-' + tema);
    document.querySelectorAll('.tema-opcion').forEach(b => {
        const activo = b.dataset.tema === tema;
        b.classList.toggle('activo', activo);
        b.setAttribute('aria-checked', activo ? 'true' : 'false');
    });
}

function elegirTema(id) {
    try { localStorage.setItem(CLAVE_TEMA, id); } catch (e) { }
    aplicarTema(id);
}

function pintarSelectorTemas(contenedor) {
    contenedor.innerHTML = '';
    TEMAS_VISUALES.forEach(t => {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'dropdown-item tema-opcion';
        boton.dataset.tema = t.id;
        boton.setAttribute('role', 'menuitemradio');
        boton.innerHTML = `<span class="tema-muestra" style="background:${t.fondo}; border-color:${t.muestra};"><i style="background:${t.muestra};"></i></span>${t.nombre}`;
        boton.addEventListener('click', (e) => { e.stopPropagation(); elegirTema(t.id); });
        contenedor.appendChild(boton);
    });
    aplicarTema(temaGuardado());
}

aplicarTema(temaGuardado());
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-selector-temas]').forEach(pintarSelectorTemas);
});
