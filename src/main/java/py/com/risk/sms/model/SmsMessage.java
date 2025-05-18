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
}