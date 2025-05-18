package py.com.risk.sms.config;

public class SmppConfig {
    private String host;
    private Integer port;
    private String systemId;
    private String password;
    private String sourceAdress;
    // Tiempo de espera entre envíos consecutivos de SMS (en milisegundos).
    // Util para cumplir con límites del proveedor SMPP o evitar sobrecarga. Por defecto 500
    private Long sendDelayMs;

    public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public Integer getPort() {
		return port;
	}
	public void setPort(Integer port) {
		this.port = port;
	}
	public String getSystemId() {
		return systemId;
	}
	public void setSystemId(String systemId) {
		this.systemId = systemId;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getSourceAdress() {
		return sourceAdress;
	}
	public void setSourceAdress(String sourceAdress) {
		this.sourceAdress = sourceAdress;
	}
	public Long getSendDelayMs() {
		return sendDelayMs != null ? sendDelayMs : 500; // por defecto 500ms
	}
	public void setSendDelayMs(Long sendDelayMs) {
		this.sendDelayMs = sendDelayMs;
	}
}
