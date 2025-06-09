package py.com.risk.sms.util;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloudhopper.commons.util.windowing.Window;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.SmppSession;

/**
 * Clase utilitaria para inspeccionar y liberar manualmente slots ocupados
 * en la ventana de envío de una sesión SMPP que hayan excedido un umbral
 * de tiempo sin recibir respuesta.
 * 
 * <p>
 * El objetivo principal de esta clase es evitar que se saturen las ventanas
 * de envío (cuando todos los slots están ocupados) debido a respuestas que
 * nunca llegaron, lo cual puede provocar errores del tipo:
 * <i>"Unable to accept offer within [n ms] (window full)"</i>.
 * </p>
 * 
 * <p>
 * Se recomienda ejecutar esta clase de forma periódica usando un scheduler
 * externo (por ejemplo, {@link java.util.concurrent.ScheduledExecutorService}),
 * idealmente con un intervalo de algunos segundos.
 * </p>
 * 
 * <p>
 * Este monitor también puede integrarse con {@link SmppLatencyStats} para
 * registrar como timeout las latencias de los slots que fueron cancelados.
 * </p>
 * 
 * @author Damián Meza
 * @version 2.0.0
 */
public class SmppWindowMonitor {

    private static final Logger logger = LogManager.getLogger(SmppWindowMonitor.class);

    /**
     * Umbral de tiempo en milisegundos. Si un slot supera este tiempo
     * sin recibir respuesta, se considera colgado y será liberado.
     */
    private final long timeoutThresholdMs;

    /**
     * Contador de slots liberados manualmente durante la última inspección.
     * Utilizado únicamente para logging informativo.
     */
    private final AtomicInteger slotsLiberados = new AtomicInteger(0);

    /**
     * Referencia opcional a un recolector de estadísticas de latencia,
     * que se usará para registrar los timeouts detectados.
     */
    private final SmppLatencyStats latencyStats;

    /**
     * Constructor principal.
     * 
     * @param timeoutThresholdMs Umbral de tiempo (en milisegundos) para cancelar un slot colgado
     * @param latencyStats Objeto opcional de estadísticas de latencia para registrar los timeouts
     */
    public SmppWindowMonitor(long timeoutThresholdMs, SmppLatencyStats latencyStats) {
        this.timeoutThresholdMs = timeoutThresholdMs;
        this.latencyStats = latencyStats;
    }

    /**
     * Inspecciona la ventana de envío de la sesión SMPP, detectando slots
     * que estén pendientes (no finalizados) y hayan superado el tiempo límite.
     * 
     * <p>
     * Por cada slot colgado, se invoca {@code window.cancel(seq)} para
     * liberar manualmente el espacio, permitiendo que nuevos mensajes
     * puedan ser enviados.
     * </p>
     * 
     * @param session Instancia activa de la sesión SMPP
     */
    public void inspectAndClean(SmppSession session) {
        if (session == null || session.getSendWindow() == null) {
            logger.warn("Sesión o ventana nula. No se puede inspeccionar.");
            return;
        }

        Window<Integer, ?, ?> window = session.getSendWindow();

        @SuppressWarnings("unchecked")
        Map<Integer, WindowFuture<Integer, Object, Object>> snapshot =
                (Map<Integer, WindowFuture<Integer, Object, Object>>) (Map<?, ?>) window.createSortedSnapshot();

        int size = snapshot.size();
        slotsLiberados.set(0);

        long now = System.currentTimeMillis();

        for (Map.Entry<Integer, WindowFuture<Integer, Object, Object>> entry : snapshot.entrySet()) {
            Integer seqNum = entry.getKey();
            WindowFuture<Integer, Object, Object> wf = entry.getValue();

            if (!wf.isDone()) {
                long elapsed = now - wf.getOfferTimestamp();

                if (elapsed > timeoutThresholdMs) {
                    try {
                        window.cancel(seqNum);
                        slotsLiberados.incrementAndGet();
                        logger.warn("[VENTANA LIBERADA] Seq={} sin respuesta desde {} ms. Slot liberado manualmente.",
                                seqNum, elapsed);
                    } catch (Exception ex) {
                        logger.warn("[VENTANA RETENIDA] Seq={} con {} ms no pudo ser liberado: {}", wf.getKey(), elapsed, ex.getMessage());
                    } finally {
                        if (latencyStats != null) {
                            latencyStats.recordTimeout(elapsed);
                        }
                    }
                }
            }
        }

        logger.info("[WINDOW MONITOR] Total slots ocupados={}, slots liberados={} (umbral={}ms)",
                size, slotsLiberados.get(), timeoutThresholdMs);
    }
}