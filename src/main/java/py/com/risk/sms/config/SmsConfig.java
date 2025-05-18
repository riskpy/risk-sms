package py.com.risk.sms.config;

import py.com.risk.sms.model.ModoEnvioLotes;

public class SmsConfig {
	// Operadora telefonica. Opcional
    private String telefonia;
    // Clasificacion: ALERTA, AVISO, PROMOCION (u otros). Opcional
    private String clasificacion;
    // Cantidad maxima de mensajes a enviar por lote. Opcional. Por defecto 100
    private Integer cantidadMaximaPorLote;
    // Modo de envío: paralelo, paralelo_espaciado, secuencial_espaciado, secuencial_espaciado_async. Por defecto secuencial_espaciado
    private ModoEnvioLotes modoEnvioLotes;
    // Tiempo de espera entre lotes de SMS a enviar (en milisegundos). Por defecto 10000
    private Long intervaloEntreLotesMs;

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
	public ModoEnvioLotes getModoEnvioLotes() {
		return modoEnvioLotes != null ? modoEnvioLotes : ModoEnvioLotes.secuencial_espaciado;
	}
	public void setModoEnvioLotes(ModoEnvioLotes modoEnvioLotes) {
		this.modoEnvioLotes = modoEnvioLotes;
	}
	public Long getIntervaloEntreLotesMs() {
		return intervaloEntreLotesMs != null ? intervaloEntreLotesMs : 10000; // por defecto 10000ms
	}
	public void setIntervaloEntreLotesMs(Long intervaloEntreLotesMs) {
		this.intervaloEntreLotesMs = intervaloEntreLotesMs;
	}

}
