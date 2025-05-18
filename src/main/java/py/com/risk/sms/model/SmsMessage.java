package py.com.risk.sms.model;

import java.math.BigDecimal;

/**
 * Representa un mensaje SMS.
 * <p>
 * Esta clase contiene los datos esenciales de un mensaje SMS que se va a enviar o ha sido recibido.
 * </p>
 * 
 * <p>Campos principales:
 * <ul>
 *   <li><b>idMensaje:</b> Identificador único del mensaje.</li>
 *   <li><b>source:</b> Número o identificador del remitente.</li>
 *   <li><b>destination:</b> Número o identificador del destinatario.</li>
 *   <li><b>text:</b> Contenido textual del mensaje SMS.</li>
 * </ul>
 * </p>
 * 
 * @author Damián Meza
 * @version 1.0.0
 */
public class SmsMessage {
    /** Identificador único del mensaje SMS. */
    private final BigDecimal idMensaje;

    /** Remitente del mensaje SMS. */
    private final String source;

    /** Destinatario del mensaje SMS. */
    private final String destination;

    /** Contenido del mensaje SMS. */
    private final String text;

    /**
     * Constructor para crear una instancia de SmsMessage.
     * 
     * @param idMensaje Identificador único del mensaje.
     * @param source Número o identificador del remitente.
     * @param destination Número o identificador del destinatario.
     * @param text Texto o contenido del mensaje SMS.
     */
    public SmsMessage(BigDecimal idMensaje, String source, String destination, String text) {
        this.idMensaje = idMensaje;
        this.source = source;
        this.destination = destination;
        this.text = text;
    }

    /**
     * Obtiene el identificador único del mensaje.
     * 
     * @return El ID del mensaje.
     */
    public BigDecimal getIdMensaje() { return idMensaje; }

    /**
     * Obtiene el remitente del mensaje.
     * 
     * @return El identificador o número del remitente.
     */
    public String getSource() { return source; }

    /**
     * Obtiene el destinatario del mensaje.
     * 
     * @return El identificador o número del destinatario.
     */
    public String getDestination() { return destination; }

    /**
     * Obtiene el contenido del mensaje SMS.
     * 
     * @return El texto del mensaje.
     */
    public String getText() { return text; }

    /**
     * Representa los posibles estados de un mensaje SMS
     * Cada estado está asociado a un código y una descripción legible.
     *
     * <p>Los estados posibles son:
     * <ul>
     *     <li>{@code PENDIENTE_ENVIO} ("P"): El mensaje está pendiente de ser enviado.</li>
     *     <li>{@code EN_PROCESO_ENVIO} ("N"): El mensaje está en proceso de envío.</li>
     *     <li>{@code ENVIADO} ("E"): El mensaje fue enviado exitosamente.</li>
     *     <li>{@code PROCESADO_ERROR} ("R"): Hubo un error al procesar el envío del mensaje.</li>
     *     <li>{@code ANULADO} ("A"): El mensaje fue anulado y no será enviado.</li>
     * </ul>
     */
    public enum Status {
        /** El mensaje está pendiente de ser enviado. */
        PENDIENTE_ENVIO("P", "Pendiente de envío"),
        /** El mensaje está en proceso de envío. */
        EN_PROCESO_ENVIO("N", "En proceso de envío"),
        /** El mensaje fue enviado exitosamente. */
        ENVIADO("E", "Enviado"),
        /** Hubo un error al procesar el mensaje. */
        PROCESADO_ERROR("R", "Procesado con error"),
        /** El mensaje fue anulado y no será enviado. */
        ANULADO("A", "Anulado");

    	/** Código corto asociado al estado. */
        private String code;

        /** Descripción legible del estado. */
        private String description;

        /**
         * Constructor privado del enum.
         *
         * @param code Código corto del estado.
         * @param description Descripción legible del estado.
         */
        private Status(String code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * Obtiene el código asociado al estado.
         *
         * @return Código del estado (ej. "P", "E", etc.).
         */
        public String getCode() {
            return code;
        }

        /**
         * Obtiene la descripción del estado.
         *
         * @return Descripción legible del estado.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Obtiene una instancia de {@code SmsMessageStatus} a partir del código.
         *
         * @param value Código del estado buscado.
         * @return Instancia del enum correspondiente, o {@code null} si no se encuentra.
         */
        public static Status fromCode(String value) {
            for (Status status : Status.values()) {
                if (status.code.equals(value)) {
                    return status;
                }
            }
            return null;
        }
    }
}