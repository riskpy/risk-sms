package py.com.risk.sms.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clase utilitaria para sanitizar y ofuscar información sensible en mensajes de log.
 * <p>
 * Ofusca datos como números de tarjetas, correos electrónicos, montos, documentos,
 * y códigos temporales (OTP, PIN, códigos de verificación, etc.) en base a reglas predefinidas.
 * <p>
 * Esta clase es segura para uso concurrente y puede utilizarse en entornos de alta concurrencia.
 *
 * <p>Ejemplo de uso:
 * <pre>{@code
 *   String logOriginal = "Tu codigo es 654321 y el monto es 1.000.000, tarjeta 4111-1111-1111-1111";
 *   String logSeguro = LogSanitizer.sanitize(logOriginal);
 * }</pre>
 *
 * @author Damián Meza
 * @version 1.0.0
 */
public class LogSanitizer {

    /**
     * Patrón para detectar códigos temporales (OTP) con contexto semántico cercano.
     * Busca palabras clave como "codigo", "otp", "code", "clave", "password", "pin"
     * seguidas (hasta 100 caracteres) de un número de 4 a 8 dígitos.
     */
    private static final Pattern CONTEXTUAL_OTP_PATTERN = Pattern.compile(
        "(?i)\\b(?:codigo|otp|code|clave|password|pin)\\b[^\\d]{0,100}?(\\d{4,8})"
    );

    /**
     * Patrón para detectar números de tarjeta (16 dígitos) con o sin separadores
     * "-" o espacio. Ejemplo: 4111-1111-1111-1111 o 4111111111111111.
     */
    private static final Pattern CARD_PATTERN = Pattern.compile("\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b");

    /**
     * Patrón para detectar montos con separadores de miles y opcionalmente decimales.
     * Soporta puntos o comas como separadores. Ejemplos: 1.000,00 o 1,000.00.
     */
    private static final Pattern MONTO_PATTERN = Pattern.compile("\\b\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{2})?\\b");

    /**
     * Patrón para detectar números de documentos con separadores de miles.
     * Ejemplo: 1.234.567 o 1,234,567.
     */
    private static final Pattern DOCUMENTO_PATTERN = Pattern.compile("\\b\\d{1,3}(?:[.,]\\d{3})+\\b");

    /**
     * Patrón para detectar direcciones de correo electrónico estándar.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+");

    /**
     * Sanitiza un mensaje reemplazando información sensible por valores genéricos.
     *
     * <ul>
     *   <li>Tarjetas: "**** **** **** ****"</li>
     *   <li>Emails: "****@****"</li>
     *   <li>Montos: "****"</li>
     *   <li>Documentos: "****"</li>
     *   <li>OTP/Códigos/PIN (con contexto): "****"</li>
     * </ul>
     *
     * @param message Mensaje original
     * @return Mensaje con los datos sensibles ofuscados
     */
    public static String sanitize(String message) {
        if (message == null) return null;

        String sanitized = message;

        // Reemplazo directo
        sanitized = CARD_PATTERN.matcher(sanitized).replaceAll("**** **** **** ****");
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("****@****");
        sanitized = MONTO_PATTERN.matcher(sanitized).replaceAll("****");
        sanitized = DOCUMENTO_PATTERN.matcher(sanitized).replaceAll("****");

        // Reemplazo contextual para OTP con Java 9 o superior
        /*sanitized = CONTEXTUAL_OTP_PATTERN.matcher(sanitized).replaceAll(m -> {
            String match = m.group();
            return match.replaceAll("\\d{4,8}", "****");
        });*/

        // Reemplazo contextual para OTP con Java 8
        Matcher matcher = CONTEXTUAL_OTP_PATTERN.matcher(sanitized);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            matcher.appendReplacement(buffer, match.replaceAll("\\d{4,8}", "****"));
        }
        matcher.appendTail(buffer);
        sanitized = buffer.toString();

        return sanitized;
    }
}