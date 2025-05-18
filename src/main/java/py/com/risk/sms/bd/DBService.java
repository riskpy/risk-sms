package py.com.risk.sms.bd;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import py.com.risk.sms.model.SmsMessage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DBService {

	private static final Logger logger = LogManager.getLogger(DBService.class);

	private final DataSource dataSource;

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
        //
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setIdleTimeout(idleTimeout);
        config.setConnectionTimeout(connectionTimeout);

        this.dataSource = new HikariDataSource(config);
    }

    public List<SmsMessage> loadPendingMessages(String sourceAddr, String telephony, String clasification, int maxSize) throws SQLException {
        List<SmsMessage> list = new ArrayList<>();
        //String query = "SELECT id_mensaje, '0972109201' origen, '0972244176' destino, contenido mensaje FROM t_mensajes WHERE estado = 'P'";
        String query = String.format("select a.mensaje_iid id_mensaje, '0972109201' origen, telefono_destino destino, mensaje mensaje from cp_men_enviados a where a.mensaje_iid in (258533879, 258533805)");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(new SmsMessage(
                        rs.getBigDecimal("id_mensaje"),
                        rs.getString("origen"),
                        rs.getString("destino"),
                        rs.getString("mensaje")
                ));
            }
        } catch (SQLException e) {
            logger.error(String.format("Error al recuperar mensajes pendientes a enviar: [%s]", e.getMessage()));
        }
        
        return list;
    }
    
    public void actualizarEstadoMensaje(BigDecimal mensajeId, String estado, String descripcion) {
        String query = "UPDATE cp_men_enviados SET estado = ? WHERE mensaje_iid = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, estado); // 'E' o 'X'
            //stmt.setString(2, descripcion); // opcional: 'Enviado correctamente', 'Error: ...'
            stmt.setBigDecimal(2, mensajeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error(String.format("Error al actualizar mensaje enviado con id=[%s]: [%s]", mensajeId, e.getMessage()));
        }
    }

    public void guardarMensajeRecibido(String origen, String destino, String mensaje) {
        String query = "INSERT INTO t_mensajes_recibidos (origen, destino, mensaje) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, origen);
        	stmt.setString(2, destino);
        	stmt.setString(3, mensaje);
        	//stmt.setTimestamp(4, new Timestamp(fechaRecepcion.getTime()));
        	stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error(String.format("Error al insertar mensaje recibido=[%s] de [%s]: [%s]", mensaje, origen, e.getMessage()));
        }
    }

}
