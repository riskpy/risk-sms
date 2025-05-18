package py.com.risk.sms.model;

/**
 * Enum que define los diferentes modos disponibles para el envío de SMS en lote.
 * 
 * Este enum es utilizado para controlar la estrategia de ejecución de los envíos
 * de mensajes desde el sistema, permitiendo seleccionar entre ejecución paralela 
 * o secuencial, con o sin espacios de tiempo entre cada mensaje.
 * 
 * Los valores permiten configurar distintos comportamientos según la capacidad del 
 * gateway SMPP, los requerimientos de regulación de tráfico (throttling), o la 
 * necesidad de evitar sobrecarga en la red o sistema.
 * 
 * <p><b>Modos disponibles:</b></p>
 * <ul>
 *   <li><b>paralelo:</b> Todos los mensajes se envían de forma paralela sin ningún retardo entre ellos.</li>
 *   <li><b>paralelo_espaciado:</b> Los mensajes se envían de forma paralela, pero se introduce un retardo fijo configurable entre cada uno.</li>
 *   <li><b>secuencial_espaciado:</b> Los mensajes se envían uno tras otro de forma estrictamente secuencial, con un retardo configurable entre cada uno.</li>
 *   <li><b>secuencial_espaciado_async:</b> Similar a secuencial_espaciado, pero los envíos se manejan de forma asincrónica (por ejemplo, en un `ScheduledExecutorService`).</li>
 * </ul>
 * 
 * @author Damián Meza
 * @version 1.0.0
 */
public enum ModoEnvioLote {
    /**
     * Envía todos los mensajes en paralelo sin ningún tipo de retardo.
     */
    paralelo,

    /**
     * Envía mensajes en paralelo, pero introduciendo un retardo fijo entre cada uno 
     * para controlar la tasa de envío.
     */
    paralelo_espaciado,

    /**
     * Envía los mensajes uno por uno, de forma secuencial y sin concurrencia, 
     * introduciendo un retardo fijo entre cada envío.
     */
    secuencial_espaciado,

    /**
     * Igual que secuencial_espaciado, pero ejecutado de forma asincrónica
     * con ayuda de un programador (ScheduledExecutorService).
     */
    secuencial_espaciado_async
}
