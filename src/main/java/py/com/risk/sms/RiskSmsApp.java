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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Aplicación principal para el envío de SMS mediante SMPP usando Cloudhopper.
 * 
 * <p>Esta clase realiza las siguientes acciones:</p>
 * <ul>
 *   <li>Carga la configuración desde un archivo YAML único.</li>
 *   <li>Por cada configuración de servicio definida, crea un hilo independiente.</li>
 *   <li>Cada hilo inicializa sus propias instancias de conexión SMPP, base de datos y lógica de envío.</li>
 *   <li>Registra logs de forma independiente por servicio utilizando ThreadContext y RoutingAppender de Log4j2.</li>
 *   <li>Mantiene el proceso activo en modo polling, con control de intervalos, reintentos y apagado controlado.</li>
 * </ul>
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

    private static final List<SmsSender> senderList = new CopyOnWriteArrayList<>();
    private static final List<SmppSessionManager> sessionManagerList = new CopyOnWriteArrayList<>();

    private static volatile boolean running = true;

    private static String propsFilePath = "config/risk-sms.yml";

    /**
     * Método principal que arranca la aplicación.
     * 
     * @param args Primer argumento opcional con ruta al archivo YAML de configuración.
     * @throws Exception Propaga excepciones críticas durante la inicialización o ejecución.
     */
    public static void main(String[] args) throws Exception {
        ThreadContext.put("servicio", "default");
        logger.info("Prendiendo RiskSMSApp...");
        propsFilePath = args.length > 0 ? args[0] : propsFilePath;

        final RiskSmsConfig config = loadConfig(propsFilePath);
        final DataSourceConfig ds = config.getDatasource();

        List<SmsConfig> smsConfigs = config.getSms();
        ExecutorService executor = Executors.newFixedThreadPool(smsConfigs.size());

        // Agregar hook para apagar limpiamente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ThreadContext.put("servicio", "default");
            logger.info("Apagando RiskSMSApp...");
            running = false;
            senderList.forEach(SmsSender::shutdown);
            sessionManagerList.forEach(SmppSessionManager::shutdown);
            executor.shutdown();
            logger.info("RiskSMSApp finalizado.");
        }));

        smsConfigs.forEach(smsConfig -> {
            executor.submit(() -> {
                ThreadContext.put("servicio", smsConfig.getNombre());
                try {
                    runExecution(ds, smsConfig);
                } catch (Exception e) {
                    logger.error(String.format("Error inesperado al ejecutar el envío de: [%s]", smsConfig.getNombre()), e);
                } finally {
                    ThreadContext.clearAll();
                }
            });
        });
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

    /**
     * Ejecuta el proceso de envío de mensajes para un servicio específico.
     * 
     * @param dsConfig Configuración de base de datos compartida.
     * @param smsConfig Configuración del servicio.
     * @throws Exception Si ocurre un error en la conexión o en el envío.
     */
    public static void runExecution(DataSourceConfig dsConfig, SmsConfig smsConfig) throws Exception {
        logger.info(String.format("Nombre del servicio: [%s]", smsConfig.getNombre()));

        final DBService dbService = new DBService(dsConfig);
        dbService.setMaximoIntentos(smsConfig.getMaximoIntentos());

        final SmppConfig smppConfig = smsConfig.getSmpp();
        final SmppSessionManager sessionManager = new SmppSessionManager();
        final SmppSession session = sessionManager.connect(smsConfig.getNombre(), dbService, smppConfig);

        final SmsSender sender = new SmsSender(session, dbService);

        senderList.add(sender);
        sessionManagerList.add(sessionManager);

        ModoEnvioLote modoEnvio = smsConfig.getModoEnvioLote();
        Long sendDelayMs = smppConfig.getSendDelayMs();
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
                	logger.info(String.format("[%s] Mensajes pendientes para enviar: [%s], Modo de envio: [%s].", count, messages.size(), modoEnvio));
                    sender.sendMessages(modoEnvio, messages, sendDelayMs);
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
            count = (count >= 100) ? 1 : count + 1;
        }
    }

}