package py.com.risk.sms.config;

import java.util.List;

public class RiskSmsConfig {
    private DataSourceConfig datasource;
    private List<SmsConfig> sms;

	public DataSourceConfig getDatasource() {
		return datasource != null ? datasource : new DataSourceConfig();
	}

	public void setDatasource(DataSourceConfig datasource) {
		this.datasource = datasource;
	}

	public List<SmsConfig> getSms() {
		return sms;
	}

	public void setSms(List<SmsConfig> sms) {
		this.sms = sms;
	}
}