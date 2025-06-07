package py.com.risk.sms.cloudhopper;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppClient;

import py.com.risk.sms.bd.DBService;
import py.com.risk.sms.config.SmppConfig;
import py.com.risk.sms.config.SmsConfig;

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
    
    private DefaultSmppClient defaultClient;
    private SmppSession session;

    /**
     * Constructor que inicializa el cliente SMPP.
     */
    public SmppSessionManager() {
        this.defaultClient = new DefaultSmppClient();
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
     * @return La sesión SMPP establecida.
     * @throws Exception Si la conexión o el bind falla.
     */
    public SmppSession connect(String service, DBService dbService, String host, int port, String systemId, String password, Integer windowSize) throws Exception {
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

        session = defaultClient.bind(config, new SmppMessageHandler(service, dbService));
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
     * @return La sesión SMPP establecida.
     * @throws Exception Si la conexión o el bind falla.
     */
    public SmppSession connect(String service, DBService dbService, SmsConfig smsConfig) throws Exception {
        SmppConfig smppConfig = smsConfig.getSmpp();
        return this.connect(service,
                dbService,
                smppConfig.getHost(),
                smppConfig.getPort(),
                smppConfig.getSystemId(),
                smppConfig.getPassword(),
                smsConfig.getCantidadMaximaPorLote());
    }

    /**
     * Desconecta y limpia recursos asociados a la sesión SMPP.
     * <p>
     * Deshace el bind de la sesión y destruye el cliente SMPP para liberar recursos.
     * </p>
     */
    public void shutdown() {
        if (session != null) session.unbind(5000);
        if (defaultClient != null) defaultClient.destroy();
    }
}