package py.com.risk.sms.log;

import org.apache.logging.log4j.Logger;

/**
 * Envoltorio (wrapper) para {@link Logger} que aplica sanitización automática
 * a los mensajes de log antes de registrarlos.
 *
 * <p>Este logger es útil para proteger datos sensibles como códigos OTP, montos,
 * documentos, tarjetas y correos electrónicos, evitando su exposición en los logs.
 * Utiliza la clase {@link LogSanitizer} para detectar y reemplazar dichos valores.
 *
 * <p>Debe obtenerse a través de {@link SafeLogManager} para garantizar la integración completa.
 *
 * <pre>{@code
 * private static final SafeLogger log = SafeLogManager.getLogger(MiClase.class);
 *
 * log.info("Enviando código OTP {}", otp);
 * }</pre>
 *
 * <p>Todos los métodos típicos de log (info, warn, error, debug, trace, fatal) están disponibles
 * y delegan al logger subyacente después de aplicar sanitización.
 *
 * @author Damián Meza
 * @version 1.0.0
 */
public class SafeLogger {
    private final Logger delegate;

    /**
     * Constructor del logger seguro.
     *
     * @param delegate instancia original de {@link Logger} a la que se delegará el registro
     */
    public SafeLogger(Logger delegate) {
        this.delegate = delegate;
    }

    /**
     * Aplica la lógica de sanitización a un mensaje.
     *
     * @param message el mensaje original
     * @return el mensaje ofuscado
     */
    private String sanitize(String message) {
        return LogSanitizer.sanitize(message);
    }

    // -------------------- INFO --------------------

    public void info(String msg) {
        delegate.info(sanitize(msg));
    }

    public void info(String msg, Object... params) {
        delegate.info(sanitize(msg), params);
    }

    // -------------------- WARN --------------------

    public void warn(String msg) {
        delegate.warn(sanitize(msg));
    }

    public void warn(String msg, Object... params) {
        delegate.warn(sanitize(msg), params);
    }

    // -------------------- ERROR --------------------

    public void error(String msg) {
        delegate.error(sanitize(msg));
    }

    public void error(String msg, Throwable t) {
        delegate.error(sanitize(msg), t);
    }

    public void error(String msg, Object... params) {
        delegate.error(sanitize(msg), params);
    }

    // -------------------- DEBUG --------------------

    public void debug(String msg) {
        delegate.debug(sanitize(msg));
    }

    public void debug(String msg, Object... params) {
        delegate.debug(sanitize(msg), params);
    }

    // -------------------- TRACE --------------------

    public void trace(String msg) {
        delegate.trace(sanitize(msg));
    }

    public void trace(String msg, Object... params) {
        delegate.trace(sanitize(msg), params);
    }

    // -------------------- FATAL --------------------

    public void fatal(String msg) {
        delegate.fatal(sanitize(msg));
    }

    public void fatal(String msg, Throwable t) {
        delegate.fatal(sanitize(msg), t);
    }

    public void fatal(String msg, Object... params) {
        delegate.fatal(sanitize(msg), params);
    }
}
