package py.com.risk.sms.config;

public class RiskSmsConfig {
    private DataSourceConfig datasource;
    private SmppConfig smpp;
    private SmsConfig sms;

	public DataSourceConfig getDatasource() {
		return datasource != null ? datasource : new DataSourceConfig();
	}

	public void setDatasource(DataSourceConfig datasource) {
		this.datasource = datasource;
	}

	public SmppConfig getSmpp() {
		return smpp != null ? smpp : new SmppConfig();
	}

	public void setSmpp(SmppConfig smpp) {
		this.smpp = smpp;
	}

	public SmsConfig getSms() {
		return sms != null ? sms : new SmsConfig();
	}

	public void setSms(SmsConfig sms) {
		this.sms = sms;
	}

}

