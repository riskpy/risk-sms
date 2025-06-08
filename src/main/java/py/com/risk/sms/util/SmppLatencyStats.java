package py.com.risk.sms.util;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Clase utilitaria para el monitoreo de latencias de respuesta en envíos SMPP.
 * 
 * <p>Mantiene estadísticas acumuladas totales y estadísticas por ventana.</p>
 
 * <p>
 * Esta clase permite registrar el tiempo de latencia (en milisegundos) entre
 * el envío de un mensaje SMS y la recepción del `submit_sm_resp` correspondiente,
 * acumulando estadísticas clave como:
 * </p>
 * <ul>
 *   <li>Total de mensajes registrados</li>
 *   <li>Tiempo mínimo, máximo y promedio de respuesta</li>
 * </ul>
 * 
 * <p>
 * El objetivo principal es detectar cuellos de botella o degradación
 * del proveedor SMPP a través de mediciones no invasivas que se realizan
 * en tiempo de ejecución.
 * </p>
 * 
 * <p>
 * Cada vez que se alcanza un múltiplo del parámetro {@code reportEvery},
 * se emite un log con el resumen estadístico acumulado hasta ese momento.
 * </p>
 * 
 * <p>
 * Esta clase es thread-safe gracias al uso de variables atómicas,
 * y puede ser compartida entre múltiples hilos concurrentes.
 * </p>
 * 
 * <pre>
 * Ejemplo de uso:
 * 
 *   private static final SmppLatencyStats stats = new SmppLatencyStats(100);
 * 
 *   long inicio = System.currentTimeMillis();
 *   SubmitSmResp resp = session.submit(request, 3000);
 *   long fin = System.currentTimeMillis();
 * 
 *   stats.record(fin - inicio);
 * </pre>
 * 
 * @author Damian
 */
public class SmppLatencyStats {

    private static final Logger logger = LogManager.getLogger(SmppLatencyStats.class);

    // Estadísticas acumuladas (históricas)
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong totalTime = new AtomicLong(0);
    private final AtomicLong totalMin = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong totalMax = new AtomicLong(Long.MIN_VALUE);

    // Estadísticas por ventana
    private final AtomicLong windowCount = new AtomicLong(0);
    private final AtomicLong windowTime = new AtomicLong(0);
    private final AtomicLong windowMin = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong windowMax = new AtomicLong(Long.MIN_VALUE);

    /** Frecuencia con la que se reportan las estadísticas */
    private final int reportEvery;

    /**
     * Constructor principal.
     * 
     * @param reportEvery cantidad de registros necesarios para emitir un log de resumen
     */
    public SmppLatencyStats(int reportEvery) {
        this.reportEvery = reportEvery;
    }

    /**
     * Registra una nueva medición de latencia.
     * 
     * @param latencyMs latencia en milisegundos del envío-response SMPP
     */
    public void record(long latencyMs) {
        // Acumulativo
        totalCount.incrementAndGet();
        totalTime.addAndGet(latencyMs);
        totalMin.updateAndGet(prev -> Math.min(prev, latencyMs));
        totalMax.updateAndGet(prev -> Math.max(prev, latencyMs));

        // Por ventana
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
     * Reinicia las estadísticas de la ventana actual de mediciones.
     * 
     * <p>
     * Este método borra el contador de mensajes y restablece los valores
     * de latencia mínima, máxima y total correspondientes al ciclo reciente,
     * permitiendo que se acumulen nuevos datos para una nueva ventana de análisis.
     * </p>
     * 
     * <p>
     * Es utilizado internamente al alcanzar el umbral de {@code reportEvery},
     * para proporcionar estadísticas periódicas independientes entre sí.
     * </p>
     */
    private void resetWindow() {
        windowCount.set(0);
        windowTime.set(0);
        windowMin.set(Long.MAX_VALUE);
        windowMax.set(Long.MIN_VALUE);
    }

    /**
     * Imprime un resumen estadístico en el log cuando se alcanza la cantidad
     * configurada de mediciones.
     */
    private void report() {
        long tCount = totalCount.get();
        long tTotal = totalTime.get();
        double tAvg = tCount > 0 ? (double) tTotal / tCount : 0.0;

        long tMin = totalMin.get();
        long tMax = totalMax.get();

        long wCount = windowCount.get();
        long wTotal = windowTime.get();
        double wAvg = wCount > 0 ? (double) wTotal / wCount : 0.0;

        long wMin = windowMin.get();
        long wMax = windowMax.get();

        logger.info(String.format("[LATENCIA SMPP TOTAL]    total=%d  avg=%.2f ms  min=%d ms  max=%d ms",
                tCount, tAvg, tMin, tMax));
        logger.info(String.format("[LATENCIA SMPP VENTANA]  total=%d  avg=%.2f ms  min=%d ms  max=%d ms",
                wCount, wAvg, wMin, wMax));
    }
}