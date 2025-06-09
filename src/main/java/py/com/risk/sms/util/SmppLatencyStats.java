package py.com.risk.sms.util;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Clase utilitaria para el monitoreo y análisis estadístico de latencias
 * en las respuestas de envíos SMPP (submit_sm -> submit_sm_resp).
 *
 * <p>
 * Esta clase acumula tanto estadísticas históricas como estadísticas
 * temporales (por ventana de observación), permitiendo detectar
 * cuellos de botella, degradaciones de servicio o picos de latencia.
 * </p>
 *
 * <p>
 * A partir de la versión 2.0, también registra estadísticas de
 * <b>timeouts</b>, es decir, mensajes que nunca recibieron respuesta
 * y fueron liberados manualmente por el monitor de ventana SMPP.
 * </p>
 *
 * <p>
 * Las estadísticas registradas incluyen:
 * <ul>
 *   <li>Total de mensajes enviados con respuesta</li>
 *   <li>Latencia mínima, máxima y promedio (total y por ventana)</li>
 *   <li>Total de timeouts y su tiempo promedio</li>
 * </ul>
 * </p>
 *
 * <p>
 * Esta clase es thread-safe y puede ser compartida entre múltiples hilos concurrentes.
 * Se recomienda instanciar una por proveedor/SMPP session.
 * </p>
 *
 * <pre>
 * Ejemplo de uso:
 * 
 *   SmppLatencyStats stats = new SmppLatencyStats(100);
 * 
 *   long t0 = System.currentTimeMillis();
 *   SubmitSmResp resp = session.submit(request, 3000);
 *   long t1 = System.currentTimeMillis();
 *   stats.record(t1 - t0);
 * </pre>
 *
 * <p>Para timeouts manuales detectados por un {@link SmppWindowMonitor}:</p>
 * <pre>
 *   stats.recordTimeout(elapsedMs);
 * </pre>
 *
 * @author Damián Meza
 * @version 2.0.0
 */
public class SmppLatencyStats {

    private static final Logger logger = LogManager.getLogger(SmppLatencyStats.class);

    // Estadísticas acumuladas de respuestas exitosas
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong totalTime = new AtomicLong(0);
    private final AtomicLong totalMin = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong totalMax = new AtomicLong(Long.MIN_VALUE);

    // Estadísticas acumuladas de timeouts
    private final AtomicLong totalTimeoutCount = new AtomicLong(0);
    private final AtomicLong totalTimeoutTotalElapsed = new AtomicLong(0);

    // Estadísticas por ventana
    private final AtomicLong windowCount = new AtomicLong(0);
    private final AtomicLong windowTime = new AtomicLong(0);
    private final AtomicLong windowMin = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong windowMax = new AtomicLong(Long.MIN_VALUE);

    /** Umbral de cantidad de registros necesarios para emitir un resumen por log */
    private final int reportEvery;

    /**
     * Constructor principal.
     *
     * @param reportEvery Cantidad de registros tras la cual se imprimirá un resumen de latencia
     */
    public SmppLatencyStats(int reportEvery) {
        this.reportEvery = reportEvery;
    }

    /**
     * Registra una nueva latencia de respuesta exitosa submit_sm -> submit_sm_resp.
     *
     * @param latencyMs Tiempo en milisegundos que tardó en llegar la respuesta
     */
    public void record(long latencyMs) {
        // Estadísticas totales
        totalCount.incrementAndGet();
        totalTime.addAndGet(latencyMs);
        totalMin.updateAndGet(prev -> Math.min(prev, latencyMs));
        totalMax.updateAndGet(prev -> Math.max(prev, latencyMs));

        // Estadísticas por ventana
        long currentWindow = windowCount.incrementAndGet();
        windowTime.addAndGet(latencyMs);
        windowMin.updateAndGet(prev -> Math.min(prev, latencyMs));
        windowMax.updateAndGet(prev -> Math.max(prev, latencyMs));

        if (currentWindow % reportEvery == 0) {
            report();
            resetWindow();
        }
    }

    /**
     * Registra un nuevo timeout detectado (es decir, slot que fue liberado sin respuesta).
     *
     * @param elapsedMs Tiempo que el slot estuvo esperando antes de ser cancelado
     */
    public void recordTimeout(long elapsedMs) {
        totalTimeoutCount.incrementAndGet();
        totalTimeoutTotalElapsed.addAndGet(elapsedMs);
    }

    /**
     * Reinicia las estadísticas de la ventana actual.
     *
     * <p>
     * Este método se ejecuta automáticamente tras {@code reportEvery}
     * llamadas al método {@link #record}, para iniciar una nueva ventana de análisis.
     * </p>
     */
    private void resetWindow() {
        windowCount.set(0);
        windowTime.set(0);
        windowMin.set(Long.MAX_VALUE);
        windowMax.set(Long.MIN_VALUE);
    }

    /**
     * Imprime un resumen estadístico tanto total como por ventana
     * en el archivo de log configurado.
     */
    private void report() {
        long tCount = totalCount.get();
        long tTotal = totalTime.get();
        double tAvg = tCount > 0 ? (double) tTotal / tCount : 0.0;

        long tMin = totalMin.get();
        long tMax = totalMax.get();

        long tc = totalTimeoutCount.get();
        double avgTimeout = tc > 0 ? (double) totalTimeoutTotalElapsed.get() / tc : 0.0;

        long wCount = windowCount.get();
        long wTotal = windowTime.get();
        double wAvg = wCount > 0 ? (double) wTotal / wCount : 0.0;

        long wMin = windowMin.get();
        long wMax = windowMax.get();

        logger.info(String.format("[LATENCIA SMPP TOTAL]    total=%d  avg=%.2fms  min=%dms  max=%dms  timeouts=%d  avgTimeout=%.2fms",
                tCount, tAvg, tMin, tMax, tc, avgTimeout));
        logger.info(String.format("[LATENCIA SMPP VENTANA]  total=%d  avg=%.2fms  min=%dms  max=%dms",
                wCount, wAvg, wMin, wMax));
    }
}