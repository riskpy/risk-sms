package py.com.risk.sms.cloudhopper;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppClient;

import py.com.risk.sms.bd.DBService;
import py.com.risk.sms.config.SmppConfig;
import py.com.risk.sms.config.SmsConfig;
import py.com.risk.sms.util.SmppLatencyStats;
import py.com.risk.sms.util.SmppWindowMonitor;

/**
 * Manager para la sesión SMPP.
 * <p>
 * Proporciona métodos para conectar y desconectar sesiones SMPP usando la librería Cloudhopper.
 * </p>
 * 
 * <p>Funcionalidades principales:
 * <ul>
 *   <li>Crear y configurar sesión SMPP con parámetros de conexión.</li>
 *   <li>Gestionar el ciclo de vida de la sesión (conectar y desconectar).</li>
 *   <li>Asociar un {@link SmppMessageHandler} para el manejo de mensajes recibidos.</li>
 * </ul>
 * </p>
 * 
 * @see com.cloudhopper.smpp.impl.DefaultSmppClient
 * @see com.cloudhopper.smpp.SmppSession
 * @see py.com.risk.sms.cloudhopper.SmppMessageHandler
 * 
 * @author Damián Meza
 * @version 1.0.0
 */
public class SmppSessionManager {

    private static final Logger logger = LogManager.getLogger(SmppSessionManager.class);

    private static final Duration ESPERA_2_SEGUNDOS = Duration.ofSeconds(2);
    private static final Duration INICIO_15_SEGUNDOS = Duration.ofSeconds(15);
    private static final Duration TIMEOUT_30_SEGUNDOS = Duration.ofSeconds(30);

    private DefaultSmppClient defaultClient;
    private SmppSession session;

    private ScheduledExecutorService scheduler;
    private SmppWindowMonitor windowMonitor;

    private String serviceName;
    private DBService dbService;
    private SmsConfig smsConfig;
    private SmppLatencyStats latencyStats;

    /**
     * Constructor por defecto
     */
    public SmppSessionManager() {
    }

    public SmppSession getSession() {
        return session;
    }

    /**
     * Establece una conexión SMPP tipo TRANSCEIVER.
     * <p>
     * Configura los parámetros de conexión (host, puerto, credenciales) y
     * crea la sesión SMPP, asociando un {@link SmppMessageHandler} para manejo
     * de mensajes recibidos.
     * </p>
     * 
     * @param service Nombre del servicio levantado para enviar/recibir SMS.
     * @param dbService Servicio para acceso a base de datos, que será usado por el handler.
     * @param host Dirección del servidor SMPP.
     * @param port Puerto del servidor SMPP.
     * @param systemId Identificador del sistema para autenticación SMPP.
     * @param password Contraseña para autenticación SMPP.
     * @param windowSize Cantidad de envíos simultáneos permitidos en una ventana de conexión SMPP.
     * @param latencyStats Objeto opcional de estadísticas de latencia para registrar los timeouts
     * @return La sesión SMPP establecida.
     * @throws Exception Si la conexión o el bind falla.
     */
    public synchronized SmppSession bind(
            String service, DBService dbService, String host, int port, String systemId, String password, Integer windowSize, SmppLatencyStats latencyStats) throws Exception {
        SmppSessionConfiguration config = new SmppSessionConfiguration();
        config.setName(String.format("SMPP-RiskSession-%s", systemId));
        config.setType(SmppBindType.TRANSCEIVER);
        config.setHost(host);
        config.setPort(port);
        config.setSystemId(systemId);
        config.setPassword(password);
        config.setInterfaceVersion((byte) 0x34);
        config.getLoggingOptions().setLogBytes(true);

        config.setWindowSize(windowSize);
        //config.setRequestExpiryTimeout(10000);      // Tiempo que espera antes de considerar que el proveedor no respondió
        //config.setWindowMonitorInterval(15000);     // Intervalo para revisar expiraciones
        //config.setConnectTimeout(5000);             // Tiempo para conectar
        //config.setBindTimeout(5000);                // Tiempo para esperar el bind
        //config.setWriteTimeout(2000);               // Tiempo para escribir en socket

        // Inicializar el cliente SMPP
        this.defaultClient = new DefaultSmppClient();

        session = defaultClient.bind(config, new SmppMessageHandler(service, dbService));

        this.windowMonitor = new SmppWindowMonitor(TIMEOUT_30_SEGUNDOS.toMillis(), latencyStats, this::rebind); // 30s de umbral
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            ThreadContext.put("servicio", service);
            try {
                windowMonitor.inspectAndClean(session);
            } catch (Exception e) {
                logger.warn("Error en inspección de ventana SMPP", e);
            }
        }, INICIO_15_SEGUNDOS.getSeconds(), TIMEOUT_30_SEGUNDOS.getSeconds(), TimeUnit.SECONDS);

        return session;
    }

    /**
     * Establece una conexión SMPP tipo TRANSCEIVER.
     * <p>
     * Configura los parámetros de conexión (host, puerto, credenciales) y
     * crea la sesión SMPP, asociando un {@link SmppMessageHandler} para manejo
     * de mensajes recibidos.
     * </p>
     * 
     * @param service Nombre del servicio levantado para enviar/recibir SMS.
     * @param dbService Servicio para acceso a base de datos, que será usado por el handler.
     * @param smppConfig Contiene la configuración de conexión al servidor SMPP.
     * @param latencyStats Objeto opcional de estadísticas de latencia para registrar los timeouts
     * @return La sesión SMPP establecida.
     * @throws Exception Si la conexión o el bind falla.
     */
    public synchronized SmppSession bind(String service, DBService dbService, SmsConfig smsConfig, SmppLatencyStats latencyStats) throws Exception {
        this.serviceName = service;
        this.dbService = dbService;
        this.smsConfig = smsConfig;
        this.latencyStats = latencyStats;

        SmppConfig smppConfig = smsConfig.getSmpp();
        return this.bind(service,
                dbService,
                smppConfig.getHost(),
                smppConfig.getPort(),
                smppConfig.getSystemId(),
                smppConfig.getPassword(),
                smsConfig.getCantidadMaximaPorLote(),
                latencyStats);
    }

    /**
     * Desconecta y limpia recursos asociados a la sesión SMPP.
     * <p>
     * Deshace el bind de la sesión y destruye el cliente SMPP para liberar recursos.
     * </p>
     * 
     * @param force Necesita un cierre forzado cuando el programa termina, caso contrario hace un cierre controlado.
     */
    public synchronized void shutdown(boolean force) {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                if (force) scheduler.shutdownNow();
                else scheduler.shutdown();
            }
        } catch (Exception e) {
            logger.warn("Error al apagar scheduler: {}", e.getMessage());
        }

        try {
            if (session != null && session.isBound()) session.unbind(5000);
        } catch (Exception e) {
            logger.warn("Error al hacer unbind de la sesión: {}", e.getMessage());
        }

        try {
            if (defaultClient != null) defaultClient.destroy();
        } catch (Exception e) {
            logger.warn("Error al destruir DefaultSmppClient: {}", e.getMessage());
        }

        // Limpiar referencias para evitar reutilización incorrecta en reintentos
        session = null;
        defaultClient = null;
        scheduler = null;
    }

    /**
     * Desconecta y limpia recursos asociados a la sesión SMPP.
     * <p>
     * Deshace el bind de la sesión y destruye el cliente SMPP para liberar recursos.
     * </p>
     */
    public synchronized void shutdown() {
        this.shutdown(true);
    }

    /**
     * Reinicia la sesión SMPP de forma segura y controlada.
     * 
     * <p>
     * Este método encapsula la lógica de desconexión y reconexión (bind) de la sesión SMPP.
     * Es útil en escenarios donde se detecta una degradación en la comunicación con el proveedor
     * (por ejemplo, saturación de la ventana de envío o timeouts repetidos).
     * </p>
     * 
     * <p>
     * Internamente realiza los siguientes pasos:
     * <ul>
     *   <li>Invoca {@link #shutdown()} para liberar recursos, cerrar sesión y detener el monitor.</li>
     *   <li>Espera un breve período (15 segundos) para permitir que el proveedor se estabilice.</li>
     *   <li>Invoca {@link #connect(String, DBService, SmsConfig, SmppLatencyStats)} reutilizando los parámetros almacenados previamente.</li>
     * </ul>
     * </p>
     * 
     * <p>
     * El método está sincronizado para evitar que múltiples hilos realicen rebinds simultáneamente,
     * lo cual podría producir condiciones de carrera o sesiones inconsistentes.
     * </p>
     * 
     * <p>
     * Si la reconexión falla, se registra un log de error pero no se lanza excepción, permitiendo
     * que el sistema continúe y el monitor vuelva a intentar más adelante si corresponde.
     * </p>
     */
    public synchronized void rebind() {
        ThreadContext.put("servicio", this.serviceName);
        logger.warn("[AUTO-REBIND] Reiniciando sesión SMPP para servicio [{}]...", serviceName);
        final int MAX_REINTENTOS = 5;
        int intento = 0;
        boolean exito = false;

        while (intento < MAX_REINTENTOS && !exito) {
            intento++;
            try {
                this.shutdown(false);

                try {
                    Thread.sleep(INICIO_15_SEGUNDOS.toMillis());
                } catch (InterruptedException ie) {
                    logger.warn("[AUTO-REBIND] Sleep interrumpido en intento #{}: {}", intento, ie.getMessage());
                    Thread.currentThread().interrupt(); // mantener el estado de interrupción
                    if (intento < MAX_REINTENTOS)
                        continue; // salta al siguiente intento de rebind si fue interrumpido desde fuera (solo si no es el último intento)
                    else
                        logger.warn("[AUTO-REBIND] Continuando con el último intento a pesar de la interrupción...");
                }

                this.bind(serviceName, dbService, smsConfig, latencyStats);
                logger.info("[AUTO-REBIND] Rebind SMPP exitoso para [{}] en intento #{}", serviceName, intento);
                exito = true;
            } catch (Exception ex) {
                logger.error("[AUTO-REBIND] Fallo intento #{} para [{}]: {}", intento, serviceName, ex.getMessage(), ex);
                if (intento < MAX_REINTENTOS) {
                    try {
                        Thread.sleep(ESPERA_2_SEGUNDOS.toMillis()); // espera breve antes de intentar de nuevo
                    } catch (InterruptedException ie) {
                        logger.warn("[AUTO-REBIND] Sleep interrumpido durante espera entre reintentos: {}", ie.getMessage());
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        if (!exito) {
            logger.error("[AUTO-REBIND] Rebind fallido tras {} intentos para [{}].", MAX_REINTENTOS, serviceName);
        }
    }

}