package py.com.risk.sms.util;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloudhopper.commons.util.windowing.Window;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.SmppSession;

/**
 * Clase utilitaria para inspeccionar y liberar slots de ventana SMPP que estén colgados.
 *
 * <p>
 * Esta clase puede ayudarte a detectar cuellos de botella en el envío, monitorear slots activos
 * en la ventana de envío y liberar manualmente aquellos que hayan superado un tiempo umbral.
 * </p>
 */
public class SmppWindowMonitor {

    private static final Logger logger = LogManager.getLogger(SmppWindowMonitor.class);

    /** Umbral en milisegundos para liberar slots que no recibieron respuesta */
    private final long timeoutThresholdMs;

    /** Contador para saber cuántos slots se liberaron en cada inspección */
    private final AtomicInteger slotsLiberados = new AtomicInteger(0);

    private final SmppLatencyStats latencyStats;

    public SmppWindowMonitor(long timeoutThresholdMs, SmppLatencyStats latencyStats) {
        this.timeoutThresholdMs = timeoutThresholdMs;
        this.latencyStats = latencyStats;
    }

    /**
     * Inspecciona la ventana de la sesión y libera los slots colgados.
     *
     * @param session Sesión SMPP activa
     */
    public void inspectAndClean(SmppSession session) {
        if (session == null || session.getSendWindow() == null) {
            logger.warn("Sesión o ventana nula. No se puede inspeccionar.");
            return;
        }

        Window<Integer, ?, ?> window = session.getSendWindow();
        //Map<Integer, WindowFuture<Integer, ?, ?>> snapshot = window.createSortedSnapshot();
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
                        latencyStats.recordTimeout(elapsed);
                    }
                }
            }
        }

        logger.info("[WINDOW MONITOR] Total slots ocupados={}, slots liberados={} (umbral={}ms)",
                size, slotsLiberados.get(), timeoutThresholdMs);
    }
}
