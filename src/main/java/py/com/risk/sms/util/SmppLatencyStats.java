package py.com.risk.sms.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Clase utilitaria para el monitoreo de latencias de respuesta en envíos SMPP.
 * 
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

    /** Logger principal */
    private static final Logger logger = LogManager.getLogger(SmppLatencyStats.class);

    /** Cantidad total de mediciones registradas */
    private final AtomicInteger count = new AtomicInteger(0);

    /** Suma total de latencias acumuladas (en ms) */
    private final AtomicLong totalTime = new AtomicLong(0);

    /** Valor mínimo de latencia registrado */
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);

    /** Valor máximo de latencia registrado */
    private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);

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
        count.incrementAndGet();
        totalTime.addAndGet(latencyMs);
        min.updateAndGet(prev -> Math.min(prev, latencyMs));
        max.updateAndGet(prev -> Math.max(prev, latencyMs));

        if (count.get() % reportEvery == 0) {
            report();
        }
    }

    /**
     * Imprime un resumen estadístico en el log cuando se alcanza la cantidad
     * configurada de mediciones.
     */
    private void report() {
        int n = count.get();
        long total = totalTime.get();
        long minVal = min.get();
        long maxVal = max.get();
        double avg = n > 0 ? (double) total / n : 0.0;

        logger.info(String.format("[LATENCIA SMPP] total=%d  avg=%.2f ms  min=%d ms  max=%d ms",
                n, avg, minVal, maxVal));
    }
}