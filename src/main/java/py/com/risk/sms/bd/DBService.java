package py.com.risk.sms.bd;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import oracle.jdbc.OracleTypes;
import oracle.sql.ARRAY;
import oracle.sql.ArrayDescriptor;
import py.com.risk.sms.config.DataSourceConfig;
import py.com.risk.sms.model.SmsMessage;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Servicio de acceso a base de datos para operaciones relacionadas con mensajes SMS.
 * Utiliza un pool de conexiones basado en HikariCP.
 * 
 * @author Damián Meza
 * @version 1.0.0
 */
public class DBService {

	private static final Logger logger = LogManager.getLogger(DBService.class);

	private final DataSource dataSource;

    // Número máximo de intentos de envío permitidos
    private final Integer MAXIMO_INTENTOS = 5;

    private Integer maximoIntentos = MAXIMO_INTENTOS;

    // Consultas SQL utilizadas internamente
    private static final String QUERY_OBTENER_MENSAJES_PENDIENTES = "SELECT b.id_mensaje,\r\n"
            + "       b.numero_telefono destino,\r\n"
            + "       nvl(b.contenido, 160) mensaje\r\n"
            + "  FROM t_mensajes b\r\n"
            + "  JOIN t_mensajeria_categorias c\r\n"
            + "    ON b.id_categoria = c.id_categoria\r\n"
            + " WHERE b.estado = ?\r\n"
            + "   AND b.telefonia = nvl(?, b.telefonia)\r\n"
            + "   AND c.clasificacion = nvl(?, c.clasificacion)\r\n"
            + " ORDER BY nvl(c.prioridad, 997), b.id_mensaje\r\n"
            + " FETCH FIRST nvl(?, 100) rows ONLY";

    /**
     * Consulta SQL para actualizar el estado de envío de mensajes masivamente.
     * Maneja lógica para verificar si puede actualizar los registros o están siendo usados por otro proceso.
     */
    private static final String QUERY_ACTUALIZAR_ESTADO_MENSAJES =
        "DECLARE\n" + 
        "  v_ids SYS.ODCINUMBERLIST := ?;\n" +
        "  v_estado VARCHAR2(1) := ?;\n" +
        "  v_no_bloqueados SYS.ODCINUMBERLIST := SYS.ODCINUMBERLIST();\n" +
        "  v_bloqueados SYS.ODCINUMBERLIST := SYS.ODCINUMBERLIST();\n" +
        "  v_bloqueado number;\n" +
        "BEGIN\n" +
        "  -- Intentar bloquear los registros\n" +
        "  FOR i IN 1 .. v_ids.COUNT LOOP\n" +
        "    BEGIN\n" +
        "      SELECT id_mensaje\n" +
        "        INTO v_bloqueado\n" +
        "        FROM t_mensajes\n" +
        "       WHERE id_mensaje = v_ids(i)\n" +
        "         FOR UPDATE NOWAIT;\n" +
        "\n" +
        "      v_bloqueados.EXTEND;\n" +
        "      v_bloqueados(v_bloqueados.COUNT) := v_bloqueado;\n" +
        "    EXCEPTION\n" +
        "      WHEN OTHERS THEN\n" +
        "        IF SQLCODE = -54 THEN -- ORA-00054: recurso ocupado\n" +
        "          v_no_bloqueados.EXTEND;\n" +
        "          v_no_bloqueados(v_no_bloqueados.COUNT) := v_ids(i);\n" +
        "        ELSE\n" +
        "          RAISE;\n" +
        "        END IF;\n" +
        "    END;\n" +
        "  END LOOP;\n" +
        "\n" +
        "  -- Actualizar estado si el bloqueo fue exitoso\n" +
        "  UPDATE t_mensajes\n" +
        "     SET estado = v_estado\n" +
        "   WHERE id_mensaje IN (SELECT * FROM TABLE(v_bloqueados));\n" +
        "\n" +
        "  -- Devolver los no bloqueados\n" +
        "  ? := v_no_bloqueados;\n" +
        "END;";

    private static final String QUERY_ACTUALIZAR_MENSAJE_ENVIADO = "UPDATE t_mensajes\r\n"
            + "   SET estado = CASE\r\n"
            + "                  WHEN ? = 'P' AND cantidad_intentos_envio >= ? THEN\r\n"
            + "                   'R'\r\n"
            + "                  ELSE\r\n"
            + "                   nvl(?, estado)\r\n"
            + "                END,\r\n"
            + "       codigo_respuesta_envio  = nvl(?, codigo_respuesta_envio),\r\n"
            + "       respuesta_envio         = nvl(substr(?, 1, 1000), respuesta_envio),\r\n"
            + "       id_externo_envio        = nvl(substr(?, 1, 100), id_externo_envio),\r\n"
            + "       cantidad_intentos_envio = CASE\r\n"
            + "                                   WHEN ? = 'N' THEN\r\n"
            + "                                    nvl(cantidad_intentos_envio, 0)\r\n"
            + "                                   ELSE\r\n"
            + "                                    nvl(cantidad_intentos_envio, 0) + 1\r\n"
            + "                                 END,\r\n"
            + "       fecha_envio = CASE\r\n"
            + "                       WHEN ? = 'E' THEN\r\n"
            + "                        current_timestamp\r\n"
            + "                       ELSE\r\n"
            + "                        fecha_envio\r\n"
            + "                     END\r\n"
            + " WHERE id_mensaje = ?";

    private static final String QUERY_INSERTAR_MENSAJE_RECIBIDO = "BEGIN\n"
            + "INSERT INTO t_mensajes_recibidos\r\n"
            + "  (numero_telefono_origen, numero_telefono_destino, contenido)\r\n"
            + "VALUES\r\n"
            + "  (?, ?, ?)\r\n"
            + "RETURNING id_mensaje INTO ?;\n"
            + "END;";

    /**
     * Inicializa el servicio con configuración personalizada del pool de conexiones.
     *
     * @param jdbcUrl             URL JDBC de conexión a la base de datos
     * @param user                Usuario de la base de datos
     * @param password            Contraseña de la base de datos
     * @param maximumPoolSize     Tamaño máximo del pool
     * @param minimumIdle         Número mínimo de conexiones inactivas
     * @param idleTimeout         Tiempo máximo de inactividad (ms) antes de cerrar una conexión
     * @param connectionTimeout   Tiempo máximo de espera para obtener una conexión (ms)
     */
    public DBService(String jdbcUrl,
            String user,
            String password,
            int maximumPoolSize,
            int minimumIdle,
            long idleTimeout,
            long connectionTimeout) {

    	HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setIdleTimeout(idleTimeout);
        config.setConnectionTimeout(connectionTimeout);

        this.dataSource = new HikariDataSource(config);
    }

    /**
     * Inicializa el servicio con configuración personalizada del pool de conexiones.
     *
     * @param dataSourceConfig    Contiene la configuración de conexión a la base de datos
     */
    public DBService(DataSourceConfig dataSourceConfig) {
        this(dataSourceConfig.getJdbcUrl(),
        		dataSourceConfig.getUser(),
        		dataSourceConfig.getPassword(),
        		dataSourceConfig.getMaximumPoolSize(),
        		dataSourceConfig.getMinimumIdle(),
        		dataSourceConfig.getIdleTimeout(),
        		dataSourceConfig.getConnectionTimeout());    	
    }

    /**
     * Obtiene el número máximo de intentos de envío permitidos.
     *
     * @return Número máximo de intentos de envío
     */
    public Integer getMaximoIntentos() {
        return maximoIntentos;
    }

    /**
     * Modifica el número máximo de intentos de envío permitidos.
     *
     * @param maximoIntentos Número máximo de intentos de envío
     */
    public void setMaximoIntentos(Integer maximoIntentos) {
        this.maximoIntentos = maximoIntentos;
    }

	/**
     * Recupera los mensajes pendientes de envío desde la base de datos, aplicando filtros opcionales.
     *
     * @param sourceAddr    Nombre del campo que representa el origen del mensaje (usado como alias de columna)
     * @param telephony     Filtro por tipo de telefonía (nullable)
     * @param clasification Filtro por clasificación de mensaje (nullable)
     * @param maxSize       Número máximo de mensajes a recuperar (nullable)
     * @return Lista de mensajes pendientes
     * @throws SQLException si ocurre un error durante la consulta
     */
    public List<SmsMessage> loadPendingMessages(String sourceAddr, String telephony, String clasification, Integer maxSize) throws SQLException {
        logger.debug(String.format("vamos a recuperar mensajes pendientes a enviar con telepnony=[%s], clasification=[%s], maxSize=[%s]", telephony, clasification, maxSize));
        List<SmsMessage> list = new ArrayList<>();
        String query = QUERY_OBTENER_MENSAJES_PENDIENTES;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
        	stmt.setObject(1, SmsMessage.Status.PENDIENTE_ENVIO.getCode(), Types.VARCHAR);
            stmt.setObject(2, telephony, Types.VARCHAR);
            stmt.setObject(3, clasification, Types.VARCHAR);
            stmt.setObject(4, maxSize, Types.INTEGER);

        	try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    list.add(new SmsMessage(
                            rs.getBigDecimal("id_mensaje"),
                            sourceAddr,
                            rs.getString("destino"),
                            rs.getString("mensaje")
                    ));
                }
        	}
        } catch (SQLException e) {
            logger.error(String.format("Error al recuperar mensajes pendientes a enviar"), e);
        }
        
        return list;
    }

    /**
     * Actualiza el estado de los mensajes de forma masiva.
     *
     * @param mensajes lista de notificaciones
     * @param estado nuevo estado (P: pendiente, E: enviado, R: rechazado)
     */
    public void updateMessagesStatus(List<SmsMessage> mensajes, SmsMessage.Status estado) {
        if (mensajes == null || mensajes.isEmpty())
            return;
        logger.debug("Marcando [{}] mensajes como [{}]", mensajes.size(), estado);

        try (Connection conn = dataSource.getConnection()) {
            // Convertir a arreglo de BigDecimal
            BigDecimal[] ids = mensajes.stream()
                    .map(SmsMessage::getIdMensaje)
                    .toArray(BigDecimal[]::new);

            // Desempaquetar la conexión real de Oracle
            oracle.jdbc.OracleConnection oraConn = conn.unwrap(oracle.jdbc.OracleConnection.class);

            // Crear el descriptor y el ARRAY
            ArrayDescriptor descriptor = ArrayDescriptor.createDescriptor("SYS.ODCINUMBERLIST", oraConn);
            ARRAY array = new ARRAY(descriptor, oraConn, ids);

            // Ejecutar el bloque anónimo PL/SQL
            try (CallableStatement stmt = conn.prepareCall(QUERY_ACTUALIZAR_ESTADO_MENSAJES)) {
                stmt.setArray(1, array); // v_ids
                stmt.setString(2, estado.getCode()); // v_estado
                stmt.registerOutParameter(3, OracleTypes.ARRAY, "SYS.ODCINUMBERLIST");
                stmt.execute();

                ARRAY resultArray = (ARRAY) stmt.getArray(3);
                if (resultArray != null) {
                    BigDecimal[] notUpdatedIds = (BigDecimal[]) resultArray.getArray();
                    Set<BigDecimal> noBloqueadosSet = new HashSet<>(Arrays.asList(notUpdatedIds));
                    logger.debug("Mensajes no bloqueados: [{}]", noBloqueadosSet);

                    // Excluir los no bloqueados de la lista original
                    mensajes.removeIf(m -> noBloqueadosSet.contains(m.getIdMensaje()));
                }
            }

        } catch (SQLException e) {
            if (e.getErrorCode() == 54) { // ORA-00054: recurso ocupado
                logger.warn("No se pudo bloquear los registros para actualizar estado de mensajes: están siendo usados por otro proceso.");
            } else {
                logger.error("Error al actualizar estado de mensajes", e);
            }
        } catch (Exception e) {
            logger.error("Fallo inesperado en actualización de estado de mensajes", e);
        }
    }

    /**
     * Actualiza el estado de un mensaje después de intentar el envío.
     *
     * @param mensajeId       ID del mensaje
     * @param estado          Nuevo estado del mensaje (nullable)
     * @param codigoRespuesta Código de respuesta del proveedor (nullable)
     * @param respuesta       Texto de respuesta del proveedor (nullable)
     * @param idExterno       Identificador externo del envío (nullable)
     */
    public void updateMessageStatus(BigDecimal mensajeId, SmsMessage.Status estado, Integer codigoRespuesta, String respuesta, String idExterno) {
        logger.debug(String.format("vamos a actualizar mensaje enviado con id=[%s], estado=[%s], codigoRespuesta=[%s], respuesta=[%s], idExterno=[%s]", mensajeId, estado, codigoRespuesta, respuesta, idExterno));
        String query = QUERY_ACTUALIZAR_MENSAJE_ENVIADO;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, estado.getCode(), Types.VARCHAR);
            stmt.setObject(2, (maximoIntentos - 1), Types.INTEGER);
        	stmt.setObject(3, estado.getCode(), Types.VARCHAR);
            stmt.setObject(4, codigoRespuesta, Types.INTEGER);
            stmt.setObject(5, respuesta, Types.VARCHAR);
            stmt.setObject(6, idExterno, Types.VARCHAR);
            stmt.setObject(7, estado.getCode(), Types.VARCHAR);
            stmt.setObject(8, estado.getCode(), Types.VARCHAR);
            stmt.setObject(9, mensajeId, Types.DECIMAL);

            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error(String.format("Error al actualizar mensaje enviado con id=[%s]", mensajeId), e);
        }
    }

    /**
     * Inserta un nuevo mensaje recibido en la base de datos.
     *
     * @param origen   Número de teléfono del emisor
     * @param destino  Número de teléfono del receptor
     * @param mensaje  Contenido del mensaje recibido
     * @return ID del mensaje insertado, o {@code null} si ocurrió un error
     */
    public BigDecimal saveReceivedMessage(String origen, String destino, String mensaje) {
        logger.debug(String.format("vamos a insertar mensaje recibido=[%s] de [%s]", mensaje, origen));
        String query = QUERY_INSERTAR_MENSAJE_RECIBIDO;

        try (Connection conn = dataSource.getConnection();
        		CallableStatement stmt = conn.prepareCall(query)) {

            stmt.setObject(1, origen, Types.VARCHAR);
            stmt.setObject(2, destino, Types.VARCHAR);
            stmt.setObject(3, mensaje, Types.VARCHAR);
            stmt.registerOutParameter(4, Types.DECIMAL);

        	stmt.execute();

        	return stmt.getBigDecimal(4);
        } catch (SQLException e) {
            logger.error(String.format("Error al insertar mensaje recibido=[%s] de [%s]", mensaje, origen), e);
            return null;
        }
    }

}
