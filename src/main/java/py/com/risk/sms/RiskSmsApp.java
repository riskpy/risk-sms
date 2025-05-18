package py.com.risk.sms;

import com.cloudhopper.smpp.SmppSession;

import py.com.risk.sms.bd.DBService;
import py.com.risk.sms.cloudhopper.SmppSessionManager;
import py.com.risk.sms.cloudhopper.SmsSender;
import py.com.risk.sms.config.*;
import py.com.risk.sms.model.*;

import java.io.FileInputStream;
import java.io.IOException;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Aplicación principal para el envío de SMS mediante SMPP usando Cloudhopper.
 * <p>
 * Esta clase:
 * <ul>
 *   <li>Carga configuración YAML desde archivo.</li>
 *   <li>Inicia conexión SMPP y DBService.</li>
 *   <li>Envía lotes de mensajes pendientes desde base de datos con control de intervalos y delays.</li>
 *   <li>Permanece corriendo para recibir mensajes MO y soporta apagado limpio con shutdown hooks.</li>
 * </ul>
 * </p>
 * 
 * <p>Configuración por defecto en <code>config/risk-sms.yml</code> o ruta pasada como argumento.</p>
 * 
 * <p>Ejemplo de ejecución:</p>
 * <pre>
 * java -jar risk-sms-app.jar config/mi-config.yml
 * </pre>
 * 
 * @author Damián Meza
 * @version 1.0.0
 */
public class RiskSmsApp {
    private static final Logger logger = LogManager.getLogger(RiskSmsApp.class);

    private static volatile boolean running = true;

    private static String propsFilePath = "config/risk-sms.yml";

    /**
     * Método principal que arranca la aplicación.
     * 
     * @param args Primer argumento opcional con ruta al archivo YAML de configuración.
     * @throws Exception Propaga excepciones críticas durante la inicialización o envío.
     */
    public static void main(String[] args) throws Exception {
        logger.info("Prendiendo RiskSMSApp...");
        propsFilePath = args.length > 0 ? args[0] : propsFilePath;

        final RiskSmsConfig config = loadConfig(propsFilePath);
        final DataSourceConfig ds = config.getDatasource();

        final DBService dbService = new DBService(ds.getJdbcUrl(),
                ds.getUser(),
                ds.getPassword(),
                ds.getMaximumPoolSize(),
                ds.getMinimumIdle(),
                ds.getIdleTimeout(),
                ds.getConnectionTimeout());

        final SmppConfig smppConfig = config.getSmpp();
        final SmppSessionManager manager = new SmppSessionManager();
        final SmppSession session = manager.connect(dbService,
                smppConfig.getHost(),
                smppConfig.getPort(),
                smppConfig.getSystemId(),
                smppConfig.getPassword());

        SmsConfig smsConfig = config.getSms();

        final SmsSender sender = new SmsSender(session, dbService);

        // Agregar hook para apagar limpiamente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Apagando RiskSMSApp...");
            running = false;
            sender.shutdown();
            manager.shutdown();
        }));

        ModoEnvioLotes modoEnvio = smsConfig.getModoEnvioLotes();
        final Long intervaloLoteMs = smsConfig.getIntervaloEntreLotesMs();
        int count = 1;
        while (running) {
            try {
                List<SmsMessage> messages = dbService.loadPendingMessages(
                        smppConfig.getSourceAdress(),
                        smsConfig.getTelefonia(),
                        smsConfig.getClasificacion(),
                        smsConfig.getCantidadMaximaPorLote());

                if (!messages.isEmpty()) {
                    logger.info(String.format("[%s] - Modo de envío: [%s].", count, modoEnvio));
                    switch (modoEnvio) {
                        case paralelo:
                            sender.sendMessagesInParallelNonBlocking(messages);
                            break;
                        case paralelo_espaciado:
                            sender.sendMessagesInParallelWithDelayNonBlocking(messages, smppConfig.getSendDelayMs());
                            break;
                        case secuencial_espaciado:
                            sender.sendMessagesSequentialWithDelayBlocking(messages, smppConfig.getSendDelayMs());
                            break;
                        case secuencial_espaciado_async:
                            sender.sendMessagesSequentialWithDelayAsync(messages, smppConfig.getSendDelayMs())
                                  .whenComplete((res, ex) -> {
                                      if (ex != null) {
                                          logger.error(String.format("Error en envío secuencial async: [%s]", ex.getMessage()));
                                      } else {
                                          logger.info(String.format("Envío secuencial async completado"));
                                      }
                                  })
                                  //.join() // Descomentar si se desea esperar a que termine antes de seguir
                                  ; 
                            break;
                        default:
                            logger.warn(String.format("[%s] - Modo de envío no reconocido: [%s]. Usando '%s' por defecto.", count, modoEnvio, ModoEnvioLotes.secuencial_espaciado));
                            sender.sendMessagesSequentialWithDelayBlocking(messages, smppConfig.getSendDelayMs());
                    }
                } else {
                	logger.info(String.format("[%s] - No se encontraron mensajes pendientes para enviar", count));
                }

                logger.info(String.format("[%s] - Tomando un descanso de [%s] ms...", count, intervaloLoteMs));
                Thread.sleep(intervaloLoteMs);
                logger.info(String.format("[%s] - Descanso terminado. Reintentando lectura de mensajes...", count));

            } catch (Exception e) {
                logger.error(String.format("[%s] - Error al procesar lote de mensajes: [%s]", count, e.getMessage()));

                logger.info(String.format("[%s] - Tomando un descanso de [%s] ms...", count, intervaloLoteMs));
                Thread.sleep(intervaloLoteMs);
                logger.info(String.format("[%s] - Descanso terminado. Reintentando lectura de mensajes...", count));
            }
            count++;
            if (count >= 100) {
                count = 1;
            }
        }

        logger.info("RiskSMSApp finalizado.");
    }

    /**
     * Carga la configuración desde un archivo YAML.
     * 
     * @param filePath Ruta al archivo YAML.
     * @return Objeto {@link RiskSmsConfig} con la configuración cargada.
     * @throws IOException Si no se puede leer o parsear el archivo.
     * 
     * <p>Ejemplo:</p>
     * <pre>
     * RiskSMSConfig config = RiskSMSApp.loadConfig("config/risk-sms.yml");
     * </pre>
     */
    public static RiskSmsConfig loadConfig(String filePath) throws IOException {
        Yaml yaml = new Yaml(new Constructor(RiskSmsConfig.class));
        try (FileInputStream input = new FileInputStream(filePath)) {
            return yaml.load(input);
        }
    }

}