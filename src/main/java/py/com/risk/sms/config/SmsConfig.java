package py.com.risk.sms.config;

import py.com.risk.sms.model.ModoEnvioLote;

public class SmsConfig {
	// Nombre del preveedor de SMS
    private String nombre;
    // Operadora telefonica. Opcional
    private String telefonia;
    // Clasificacion: ALERTA, AVISO, PROMOCION (u otros). Opcional
    private String clasificacion;
    // Cantidad maxima de mensajes a enviar por lote. Opcional. Por defecto 100
    private Integer cantidadMaximaPorLote;
    // Modo de envío: paralelo, paralelo_espaciado, secuencial_espaciado, secuencial_espaciado_async. Por defecto secuencial_espaciado
    private ModoEnvioLote modoEnvioLote;
    // Tiempo de espera entre lotes de SMS a enviar (en milisegundos). Por defecto 10000
    private Long intervaloEntreLotesMs;
    // Número máximo de intentos de envío permitidos de SMS. Opcional. Por defecto 5
    private Integer maximoIntentos;
    // Configuracion de conexion al gateway
    private SmppConfig smpp;

    public String getNombre() {
		return nombre;
	}
	public void setNombre(String nombre) {
		this.nombre = nombre;
	}
	public String getTelefonia() {
		return telefonia;
	}
	public void setTelefonia(String telefonia) {
		this.telefonia = telefonia;
	}
	public String getClasificacion() {
		return clasificacion;
	}
	public void setClasificacion(String clasificacion) {
		this.clasificacion = clasificacion;
	}
	public Integer getCantidadMaximaPorLote() {
		return cantidadMaximaPorLote != null ? cantidadMaximaPorLote : 100; // por defecto 100
	}
	public void setCantidadMaximaPorLote(Integer cantidadMaximaPorLote) {
		this.cantidadMaximaPorLote = cantidadMaximaPorLote;
	}
	public ModoEnvioLote getModoEnvioLote() {
		return modoEnvioLote != null ? modoEnvioLote : ModoEnvioLote.secuencial_espaciado;
	}
	public void setModoEnvioLote(ModoEnvioLote modoEnvioLote) {
		this.modoEnvioLote = modoEnvioLote;
	}
	public Long getIntervaloEntreLotesMs() {
		return intervaloEntreLotesMs != null ? intervaloEntreLotesMs : 10000; // por defecto 10000ms
	}
	public void setIntervaloEntreLotesMs(Long intervaloEntreLotesMs) {
		this.intervaloEntreLotesMs = intervaloEntreLotesMs;
	}
	public Integer getMaximoIntentos() {
		return maximoIntentos != null ? maximoIntentos : 5; // por defecto 5
	}
	public void setMaximoIntentos(Integer maximoIntentos) {
		this.maximoIntentos = maximoIntentos;
	}
	public SmppConfig getSmpp() {
		return smpp != null ? smpp : new SmppConfig();
	}
	public void setSmpp(SmppConfig smpp) {
		this.smpp = smpp;
	}
}