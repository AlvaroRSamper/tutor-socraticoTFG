    const inputElement = document.getElementById('inputMensaje');
    const mensajesDiv = document.getElementById('mensajes');
    let temaActual = "General";

    let editor = null;

    inputElement.addEventListener('input', function() {
        this.style.height = 'auto';
        this.style.height = this.scrollHeight + 'px';
    });

    let historial = [];
    let contadorMensajes = 0;

    window.onload = () => {
        const selectModal = document.getElementById('select-tema-modal');
        document.querySelectorAll('.topic-item').forEach(li => {
            const file = li.getAttribute('data-file');
            const nombre = li.innerText;
            const option = document.createElement('option');
            option.value = file;
            option.text = nombre;
            selectModal.appendChild(option);
        });

        const guardado = localStorage.getItem('historial_tutor_poo');
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
        localStorage.setItem('historial_tutor_poo', JSON.stringify(historial));
    }

    function seleccionarTema(elemento) {
        document.querySelectorAll('.topic-item').forEach(el => el.classList.remove('active'));
        elemento.classList.add('active');
        
        temaActual = elemento.getAttribute('data-file');
        const nombreVisible = elemento.innerText.split('. ').pop().trim();
        
        document.getElementById('titulo-chat').innerText = "Tutor: " + nombreVisible;
        agregarAvisoContexto("Contexto RAG cambiado a: " + nombreVisible);
    }

    function reiniciarChat() {
        if(confirm("¿Estás seguro de que quieres reiniciar la conversación? Esto borrará el historial actual.")) {
            historial = [];
            guardarHistorial();
            mensajesDiv.innerHTML = `
                <div class="mensaje-wrapper bot-wrapper">
                    <div class="mensaje bot">
                        <p>¡Hola! Soy tu <strong>Tutor Socrático de POO</strong>.</p>
                        <p>He cargado todos tus apuntes oficiales. Selecciona un tema en la barra lateral para centrar nuestro estudio, o pídeme generar un ejercicio práctico.</p>
                    </div>
                </div>
            `;
            document.querySelector('.topic-item.general-topic').click();
            salirEjercicio();
        }
    }

    function agregarAvisoContexto(texto) {
        const wrapper = document.createElement('div');
        wrapper.style = "text-align: center; margin: 10px 0;";
        wrapper.innerHTML = `<span style="background: rgba(88,166,255,0.1); color: var(--primary); padding: 4px 12px; border-radius: 12px; font-size: 0.8rem;">${texto}</span>`;
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
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    historial: historial,
                    tema: temaActual
                })
            });

            const json = await respuesta.json();
            historial.push({ role: "assistant", content: json.mensaje });
            guardarHistorial();

            const burbujaBot = document.getElementById(idUnico);
            if (burbujaBot) {
                marked.setOptions({ breaks: true });
                burbujaBot.innerHTML = marked.parse(json.mensaje);
            }
        } catch (error) {
            document.getElementById(idUnico).innerText = "Error de conexión.";
            historial.pop();
        }
        hacerScrollAbajo();
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

        activarModoEjercicio();

        const peticiónFicticia = `Genera un ejercicio ${dificultad} sobre ${nombreTema}`;
        historial.push({ role: "user", content: peticiónFicticia });
        guardarHistorial();
        agregarBurbuja("Quiero hacer un ejercicio práctico de nivel " + dificultad + " sobre " + nombreTema, 'user');

        const idUnico = 'msg-' + (++contadorMensajes);
        agregarBurbujaCargando(idUnico);

        try {
            const respuesta = await fetch('/api/tutor/ejercicio', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    tema: temaSeleccionado,
                    dificultad: dificultad
                })
            });

            const json = await respuesta.json();
            historial.push({ role: "assistant", content: json.mensaje });
            guardarHistorial();

            const burbujaBot = document.getElementById(idUnico);
            if (burbujaBot) {
                marked.setOptions({ breaks: true });
                burbujaBot.innerHTML = marked.parse(json.mensaje);
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
            div.innerHTML = marked.parse(texto);
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
            div.innerHTML = marked.parse(texto);
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
