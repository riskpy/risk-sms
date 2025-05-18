package py.com.risk.sms.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fábrica de loggers seguros para proteger la información sensible en los mensajes de log.
 * <p>
 * Esta clase devuelve instancias de {@link SafeLogger}, un envoltorio de {@link Logger}
 * que aplica sanitización automática mediante {@link LogSanitizer} antes de registrar
 * cualquier mensaje.
 *
 * <p>Uso recomendado en lugar de obtener directamente loggers con {@code LogManager.getLogger()}:
 * <pre>{@code
 * private static final SafeLogger log = SafeLogManager.getLogger(MiClase.class);
 * }</pre>
 *
 * <p>Esto asegura que todos los logs registrados estén ofuscados correctamente.
 *
 * @author Damián Meza
 * @version 1.0.0
 */
public class SafeLogManager {

    /**
     * Obtiene una instancia de {@link SafeLogger} asociada a la clase indicada.
     * El {@link SafeLogger} aplicará ofuscación automática a los datos sensibles.
     *
     * @param clazz la clase desde donde se solicita el logger
     * @return una instancia segura de {@link SafeLogger}
     */
    public static SafeLogger getLogger(Class<?> clazz) {
        Logger logger = LogManager.getLogger(clazz);
        return new SafeLogger(logger);
    }
}
