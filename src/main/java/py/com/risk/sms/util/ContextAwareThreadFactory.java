package py.com.risk.sms.util;

import org.apache.logging.log4j.ThreadContext;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * ThreadFactory que propaga el contexto de Log4j2 (ThreadContext) desde el hilo principal
 * a los hilos que se crean a través de un ExecutorService o ScheduledExecutorService.
 * 
 * <p>Esta clase permite que todos los hilos creados mantengan automáticamente
 * las claves de contexto como "servicio", evitando así problemas con el RoutingAppender
 * y garantizando que los logs se roten correctamente por proveedor/servicio.</p>
 *
 * <p>Ejemplo de uso:</p>
 * <pre>{@code
 * ExecutorService executor = Executors.newFixedThreadPool(10, new ContextAwareThreadFactory());
 * ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new ContextAwareThreadFactory());
 * }</pre>
 *
 * <p>Es especialmente útil cuando se usa RoutingAppender por proveedor en aplicaciones multihilo.</p>
 * 
 * @author Damián Meza
 * @version 1.0.0
 */
public class ContextAwareThreadFactory implements ThreadFactory {

    private final ThreadFactory delegate;
    private final Map<String, String> parentContext;

    /**
     * Crea una nueva instancia que captura el contexto actual del hilo que construye esta fábrica.
     */
    public ContextAwareThreadFactory() {
        this.delegate = Executors.defaultThreadFactory();
        this.parentContext = ThreadContext.getImmutableContext(); // snapshot del contexto actual
    }

    /**
     * Crea un nuevo hilo que hereda el contexto del hilo padre (capturado en el constructor).
     * 
     * @param r La tarea a ejecutar en el nuevo hilo.
     * @return Un hilo decorado que copia el ThreadContext antes de ejecutar la tarea.
     */
    @Override
    public Thread newThread(Runnable r) {
        return delegate.newThread(() -> {
            if (parentContext != null) {
                ThreadContext.putAll(parentContext);
            }
            try {
                r.run();
            } finally {
                ThreadContext.clearAll(); // evitar contaminación cruzada
            }
        });
    }
}