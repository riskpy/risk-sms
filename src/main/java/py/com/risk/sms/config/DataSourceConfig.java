package py.com.risk.sms.config;

public class DataSourceConfig {
    private String serverName;
    private Integer port;
    private String sid;
    private String serviceName;
    private String user;
    private String password;
    private Integer maximumPoolSize;
    private Integer minimumIdle;
    private Long idleTimeout;
    private Long connectionTimeout;

    public String getJdbcUrl() {
    	return String.format("jdbc:oracle:thin:@//%s:%s/%s", serverName, port, serviceName);
    }
    public String getServerName() {
		return serverName;
	}
	public void setServerName(String serverName) {
		this.serverName = serverName;
	}
	public Integer getPort() {
		return port;
	}
	public void setPort(Integer port) {
		this.port = port;
	}
	public String getSid() {
		return sid;
	}
	public void setSid(String sid) {
		this.sid = sid;
	}
	public String getServiceName() {
		return serviceName;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public Integer getMaximumPoolSize() {
		return maximumPoolSize != null ? maximumPoolSize : 50; // por defecto 50 o el número adecuado a tu carga concurrente
	}
	public void setMaximumPoolSize(Integer maximumPoolSize) {
		this.maximumPoolSize = maximumPoolSize;
	}
	public Integer getMinimumIdle() {
		return minimumIdle != null ? minimumIdle : 5; // por defecto 5
	}
	public void setMinimumIdle(Integer minimumIdle) {
		this.minimumIdle = minimumIdle;
	}
	public Long getIdleTimeout() {
		return idleTimeout != null ? idleTimeout : 30000; // por defecto 30000ms
	}
	public void setIdleTimeout(Long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}
	public Long getConnectionTimeout() {
		return connectionTimeout != null ? connectionTimeout : 10000; // por defecto 10000ms
	}
	public void setConnectionTimeout(Long connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

}
