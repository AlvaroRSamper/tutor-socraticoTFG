/*
 * seguridad.js — Endurecimiento anti-XSS del renderizado de Markdown.
 *
 * El tutor renderiza como HTML (innerHTML) el Markdown que devuelve el LLM. Como la salida del
 * modelo puede contener HTML activo (p. ej. inducido por prompt injection en las preguntas de otros
 * alumnos), TODO el resultado de marked.parse() debe pasar por DOMPurify antes de inyectarse.
 *
 * En lugar de modificar las decenas de llamadas existentes, se intercepta marked.parse una sola vez:
 * cualquier código que ya use marked.parse(...) queda saneado de forma transparente.
 *
 * Debe cargarse DESPUÉS de marked y DOMPurify, y ANTES del resto de scripts de la página.
 */
(function () {
    if (typeof marked === 'undefined' || typeof DOMPurify === 'undefined') {
        console.error('seguridad.js: marked o DOMPurify no están cargados; NO se aplicará el saneado anti-XSS.');
        return;
    }
    var parseOriginal = marked.parse.bind(marked);
    marked.parse = function (src, opciones) {
        return DOMPurify.sanitize(parseOriginal(src == null ? '' : src, opciones));
    };
})();
