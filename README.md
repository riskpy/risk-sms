
# Risk SMS Gateway
**`risk-sms`** es una herramienta desarrollada en Java para el envío y recepción de mensajes SMS, diseñada como **parte del ecosistema [risk](https://github.com/jtsoya539/risk)** y pensada para ser integrada de forma nativa con su módulo de _Mensajería_.

Además, gracias a su arquitectura modular, puede **adaptarse fácilmente a otros entornos o sistemas que requieran funcionalidades similares**.

Utiliza la librería [Cloudhopper SMPP](https://github.com/fizzed/cloudhopper-smpp) para la comunicación con gateways de SMS mediante el protocolo SMPP, y [HikariCP](https://github.com/brettwooldridge/HikariCP) para la gestión eficiente de conexiones a base de datos Oracle.

---

## 🚀 Funcionalidades principales
* 📤Envío de mensajes SMS a través de SMPP.
* 📩Recepción de mensajes entrantes (MO - _Mobile Originated_).
* 📬Procesamiento de acuses de entrega (DLR - _Delivery Receipt_).
* 🗃️Lectura y escritura desde/hacia una base de datos Oracle.
* ⚙️Configuración externa y desacoplada para entornos productivos.

---

## 🧱 Requisitos
- Java 8 o superior
- Maven 3.6+
- Acceso a un gateway SMPP válido
- **Base de datos Oracle accesible**, con las siguientes tablas disponibles:
    - [`t_mensajes`](https://github.com/jtsoya539/risk/blob/master/source/database/modules/msj/tables/t_mensajes.tab): utilizada para almacenar y procesar los mensajes pendientes de envío.
    - [`t_mensajes_recibidos`](https://github.com/jtsoya539/risk/blob/master/source/database/modules/msj/tables/t_mensajes_recibidos.tab): utilizada para guardar los mensajes entrantes (MO).

---

## 📦 Estructura del proyecto

```
risk-sms/
├── config/
│   ├── risk-sms.yml.example     # Archivo de configuración ejemplo
│   └── risk-sms.yml             # Archivo real (no versionado)
├── src/                         # Código fuente
├── target/                      # Archivos compilados y .jar
├── pom.xml                      # Configuración Maven
├── LICENCE                      # Detalles de la licencia
├── .gitignore
└── README.md
```

---

## ⚙️ Configuración
El archivo de configuración está ubicado en `config/risk-sms.yml`, pero **no debe versionarse** porque puede contener credenciales. En su lugar, se incluye un archivo de ejemplo:
```bash
cp config/risk-sms.yml.example config/risk-sms.yml
```
Edita el archivo `config/risk-sms.yml` con los valores adecuados para tu entorno:
```yml
# Ejemplo: config/risk-sms.yml
# Conexion al Gateway para envio y recepcion de SMS
smpp:
  host: host.com.py
  port: 54321
  systemId: systemId
  password: password
  sourceAdress: 09751000000
  sendDelayMs: 50  # Tiempo de espera entre envíos consecutivos de SMS (en milisegundos). Util para cumplir con límites del proveedor SMPP o evitar sobrecarga. Por defecto 500

# Conexion a la base de datos
datasource:
  serverName: serverName.com.py
  port: 1521
  serviceName: serviceName
  user: user
  password: password
  maximumPoolSize: 50
  minimumIdle: 5
  idleTimeout: 30000
  connectionTimeout: 10000

# Configuraciones de negocio para el envio de SMS
sms:
  telefonia: TEL                        # Operadora telefonica. Opcional
  clasificacion: AVISO                  # Clasificacion: ALERTA, AVISO, PROMOCION (u otros). Opcional
  cantidadMaximaPorLote: 100            # Cantidad maxima de SMS a enviar por lote. Opcional. Por defecto 100
  modoEnvioLotes: secuencial_espaciado  # Modo de envío: paralelo, paralelo_espaciado, secuencial_espaciado, secuencial_espaciado_async
  intervaloEntreLotesMs: 10000          # Tiempo de espera entre lotes de SMS a enviar (en milisegundos)
```
> ⚠️ **Importante:** No compartas ni subas el archivo `risk-sms.yml` real al repositorio.


---

## 🛠️ Compilación
Para compilar y generar el JAR ejecutable con todas las dependencias (desde el directorio raíz del proyecto):
```bash
mvn clean install
```
El JAR final estará en:
```
target/risk-sms.jar
```

---

## ▶️ Ejecución
Para ejecutar el JAR:
```bash
java -Xms500M -Xmx500M -XX:MaxDirectMemorySize=250M -server -XX:+UseG1GC -XX:+ExplicitGCInvokesConcurrent -XX:MaxGCPauseMillis=500 -jar target/risk-sms.jar
```
Por defecto, busca el archivo `config/risk-sms.yml`.

También podés especificar otro archivo:

```bash
java -Xms500M -Xmx500M -XX:MaxDirectMemorySize=250M -server -XX:+UseG1GC -XX:+ExplicitGCInvokesConcurrent -XX:MaxGCPauseMillis=500 -jar target/risk-sms.jar path/a/otro-risk-sms.yml
```

---

## 🧪 Testing
Podés agregar mensajes de prueba en la tabla `t_mensajes` de tu base de datos y verificar que se procesen correctamente, tanto el envío como la recepción.

---

## 🪵 Logging
* Se utiliza **Log4j2** para los logs.
* La configuración puede personalizarse en el archivo `log4j2.xml`.

---

## 🙋‍♂️ Sugerencias
Si tenés dudas, encontraste un error o querés proponer una mejora en el código o la documentación, no dudes en crear un [issue](https://github.com/DamyGenius/risk-sms/issues).  
Estaremos atentos para ayudarte o recibir tus aportes.

---

## 🤝 Contribuciones
Las contribuciones son siempre bienvenidas.  
Si querés corregir un error, mejorar el rendimiento o incorporar una nueva funcionalidad:
1.  Creá una rama con tus cambios.
2.  Enviá un [pull request](https://github.com/DamyGenius/risk-sms/pulls) con una breve descripción.

¡Gracias por ayudar a mejorar **`risk-sms`**!

---

## 📄 Licencia
El Proyecto `risk-sms` está licenciado bajo la licencia MIT. Ver el archivo [LICENSE](/LICENSE) para más detalles.

MIT © 2025 – [DamyGenius](https://github.com/DamyGenius)

---
