package py.com.risk.sms.cloudhopper;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

import py.com.risk.sms.bd.DBService;

/**
 * Handler para eventos SMPP que procesa mensajes recibidos (MO) y acuses de entrega (DLR).
 * <p>
 * Extiende {@link DefaultSmppSessionHandler} para implementar lógica personalizada 
 * al recibir PDU de tipo {@link DeliverSm}.
 * </p>
 * 
 * <p>Funciones principales:
 * <ul>
 *   <li>Detectar y procesar mensajes Mobile Originated (MO) y almacenarlos en base de datos.</li>
 *   <li>Detectar y procesar acuses de entrega (Delivery Receipts o DLR) para actualizar estado.</li>
 * </ul>
 * </p>
 * 
 * @see com.cloudhopper.smpp.impl.DefaultSmppSessionHandler
 * @see com.cloudhopper.smpp.pdu.DeliverSm
 * 
 * @author Damián Meza
 * @version 1.0.0
 */
public class SmppMessageHandler extends DefaultSmppSessionHandler {
    private static final Logger logger = LogManager.getLogger(SmppMessageHandler.class);

    private final DBService dbservice;

    /**
     * Constructor que recibe el servicio para acceso a base de datos.
     * 
     * @param dbservice Instancia de {@link DBService} para operaciones sobre la base de datos.
     */
    public SmppMessageHandler(DBService dbservice) {
        super();
        this.dbservice = dbservice;
    }

    /**
     * Maneja la recepción de un PDU SMPP.
     * <p>
     * Si el PDU es un {@link DeliverSm}, determina si es un mensaje MO o un DLR
     * y lo procesa adecuadamente.
     * </p>
     * 
     * @param pduRequest PDU SMPP recibido.
     * @return Respuesta al PDU recibido, creada con {@code pduRequest.createResponse()}.
     */
    @Override
    public PduResponse firePduRequestReceived(PduRequest pduRequest) {
        if (pduRequest instanceof DeliverSm) {
            DeliverSm deliverSm = (DeliverSm) pduRequest;

            byte esmClass = deliverSm.getEsmClass();
            boolean isDeliveryReceipt = (esmClass & 0x04) == 0x04;

            if (isDeliveryReceipt) {
                handleDeliveryReceipt(deliverSm);
            } else {
                handleMobileOriginated(deliverSm);
            }
        }

        return pduRequest.createResponse();
    }

    /**
     * Procesa un mensaje Mobile Originated (MO).
     * <p>
     * Extrae el texto, origen y destino, lo registra en log y guarda en base de datos.
     * </p>
     * 
     * @param deliverSm PDU {@link DeliverSm} con el mensaje MO.
     */
    private void handleMobileOriginated(DeliverSm deliverSm) {
        String msg = new String(deliverSm.getShortMessage(), StandardCharsets.UTF_8);
        String from = deliverSm.getSourceAddress().getAddress();
        String to = deliverSm.getDestAddress().getAddress();

        logger.info(String.format("MO recibido de [%s] a [%s]: [%s]", from, to, msg));
        dbservice.guardarMensajeRecibido(from, to, msg);
    }

    /**
     * Procesa un acuse de entrega (Delivery Receipt - DLR).
     * <p>
     * Extrae información relevante (ID del mensaje y estado de entrega) 
     * y la registra en logs para su seguimiento.
     * </p>
     * 
     * <p>Posibles valores comunes del estado ("stat") en el DLR:
     * <ul>
     *   <li>DELIVRD: Entregado exitosamente.</li>
     *   <li>EXPIRED: Expirado sin entrega.</li>
     *   <li>UNDELIV: No entregado.</li>
     *   <li>REJECTD: Rechazado.</li>
     *   <li>ACCEPTD: Aceptado pero aún no entregado.</li>
     *   <li>UNKNOWN: Estado desconocido.</li>
     *   <li>ENROUTE: En tránsito.</li>
     * </ul>
     * </p>
     * 
     * @param deliverSm PDU {@link DeliverSm} con el DLR.
     */
    private void handleDeliveryReceipt(DeliverSm deliverSm) {
        String receipt = new String(deliverSm.getShortMessage(), StandardCharsets.UTF_8);
        logger.debug(String.format("DLR recibido: [%s]", receipt));

        String messageId = extractValue(receipt, "id");
        String status = extractValue(receipt, "stat");

        logger.info(String.format("ID mensaje: [%s], Estado entrega: [%s]", messageId, status));
        // Aquí se podría agregar la actualización del estado en base de datos
    }

    /**
     * Extrae el valor asociado a una clave específica en el texto del DLR.
     * <p>
     * Busca una cadena en formato "key:value" separada por espacios y retorna el valor.
     * </p>
     * 
     * @param text Texto completo del DLR.
     * @param key Clave cuyo valor se desea extraer.
     * @return Valor asociado a la clave, o cadena vacía si no se encuentra.
     */
    private String extractValue(String text, String key) {
        for (String part : text.split(" ")) {
            if (part.startsWith(key + ":")) {
                return part.substring((key + ":").length());
            }
        }
        return "";
    }

    /**
     * Ejemplo de parsing avanzado usando expresiones regulares para extraer
     * ID y estado del DLR.
     * <p>
     * Este método no se usa actualmente, pero puede ser útil para implementaciones futuras.
     * </p>
     * 
     * @param receipt Texto completo del DLR.
     */
    @SuppressWarnings("unused")
    private static void parseDeliveryReceipt(String receipt) {
        Pattern pattern = Pattern.compile("id:(\\S+) .* stat:(\\S+)");
        Matcher matcher = pattern.matcher(receipt);

        if (matcher.find()) {
            String messageId = matcher.group(1);
            String status = matcher.group(2);
            logger.debug(String.format("ID del mensaje: [%s], Estado: [%s]", messageId, status));
        } else {
            logger.warn(String.format("No se pudo parsear el DLR: [%s]", receipt));
        }
    }
}