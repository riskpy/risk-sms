package py.com.risk.sms.cloudhopper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

import org.apache.logging.log4j.ThreadContext;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.type.Address;

import py.com.risk.sms.bd.DBService;
import py.com.risk.sms.log.SafeLogManager;
import py.com.risk.sms.log.SafeLogger;
import py.com.risk.sms.model.ModoEnvioLote;
import py.com.risk.sms.model.SmsMessage;
import py.com.risk.sms.util.ContextAwareThreadFactory;
import py.com.risk.sms.util.SmppLatencyStats;

/**
 * Clase para envío de mensajes SMS usando SMPP con Cloudhopper.
 * Provee métodos para enviar mensajes en paralelo o secuencialmente,
 * con o sin delay, y maneja el estado de cada mensaje en base de datos.
 * <p>
 * Usa hilos para concurrencia y ScheduledExecutor para delays asincrónicos.
 * </p>
 * 
 * @author Damián Meza
 * @version 1.0.0
 */
public class SmsSender {
    private static final SafeLogger logger = SafeLogManager.getLogger(SmsSender.class);

    private final ExecutorService executor = Executors.newFixedThreadPool(50, new ContextAwareThreadFactory()); // Para envíos paralelos
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ContextAwareThreadFactory()); // Para delay asincrónico

    private static final Duration DEFAULT_DELAY_MS = Duration.ofMillis(500);
    private static final Duration TIMEOUT_3_SEGUNDOS = Duration.ofSeconds(3);

    private final Supplier<SmppSession> sessionProvider;
    private final DBService dbservice;

    private final SmppLatencyStats latencyStats;

    /*
     * Códigos válidos para reintentos, según el protocolo SMPP estándar
     * Referencia: https://smpp.org/smpp-error-codes.html
     */
    private static final Set<Integer> CODIGOS_REINTENTOS = new HashSet<Integer>(Arrays.asList(-1, 8, 20, 88));

    /**
     * Constructor.
     * 
     * @param sessionProvider Proveedor de sesión SMPP activa para envío de mensajes.
     * @param dbservice Servicio para actualización de estados en base de datos.
     * @param latencyStats Objeto de estadísticas de latencia
     */
    public SmsSender(Supplier<SmppSession> sessionProvider, DBService dbservice, SmppLatencyStats latencyStats) {
        this.sessionProvider = sessionProvider;
        this.dbservice = dbservice;
        this.latencyStats = latencyStats;
    }

    /**
     * Enrutador principal para el envío de mensajes SMS según el modo especificado.
     * 
     * Este método actúa como un "dispatcher" y selecciona la estrategia de envío
     * adecuada basada en el valor de {@link ModoEnvioLote}, incluyendo:
     * <ul>
     *     <li>{@code paralelo}: Envío en paralelo sin delay (no bloqueante).</li>
     *     <li>{@code paralelo_espaciado}: Envío en paralelo con delay entre mensajes (no bloqueante).</li>
     *     <li>{@code secuencial_espaciado}: Envío secuencial con delay entre mensajes (bloqueante).</li>
     *     <li>{@code secuencial_espaciado_async}: Envío secuencial asincrónico con delay (no bloqueante).</li>
     * </ul>
     * 
     * Si se especifica un modo no reconocido, se utilizará {@code secuencial_espaciado} por defecto.
     * 
     * @param modoEnvio El modo de envío a aplicar.
     * @param messages Lista de mensajes a enviar.
     * @param delayMs Delay entre envíos, usado en los modos que lo permiten. Si es menor o igual a cero, se usará un valor por defecto.
     */
    public void sendMessages(ModoEnvioLote modoEnvio, List<SmsMessage> messages, long delayMs) {
        switch (modoEnvio) {
            case paralelo:
                this.sendMessagesInParallelNonBlocking(messages);
                break;
            case paralelo_espaciado:
                this.sendMessagesInParallelWithDelayNonBlocking(messages, delayMs);
                break;
            case secuencial_espaciado:
                this.sendMessagesSequentialWithDelayBlocking(messages, delayMs);
                break;
            case secuencial_espaciado_async:
                this.sendMessagesSequentialWithDelayAsync(messages, delayMs)
                      .whenComplete((res, ex) -> {
                          if (ex != null) {
                              logger.error(String.format("Error en envío secuencial async: [%s]", ex.getMessage()));
                          } else {
                              logger.info(String.format("Envío secuencial async completado"));
                          }
                      })
                      //.join() // Descomentar si se desea esperar a que termine antes de seguir
                      ; 
                break;
            default:
                logger.warn(String.format("Modo de envío no reconocido: [%s]. Usando '%s' por defecto.", modoEnvio, ModoEnvioLote.secuencial_espaciado));
                this.sendMessagesSequentialWithDelayBlocking(messages, delayMs);
        }
    }

    /**
     * Envía múltiples mensajes en paralelo sin esperar a que terminen.
     * No hay delay entre envíos.
     * 
     * Ejemplo de uso desde main:
     * <pre>
     * List<SmsMessage> mensajes = ...; // lista con mensajes
     * SmsSender sender = new SmsSender(session, dbservice);
     * sender.sendMessagesInParallelNonBlocking(mensajes);
     * // no se espera a que terminen los envíos, seguir otras tareas
     * </pre>
     */
    public void sendMessagesInParallelNonBlocking(List<SmsMessage> messages) {
    	String count = ThreadContext.get("contador");
        for (SmsMessage msg : messages) {
            executor.submit(() -> sendSingleMessage(msg, count));
        }
    }

    /**
     * Envía múltiples mensajes en paralelo con un delay entre cada envío.
     * El método retorna inmediatamente, sin esperar.
     * 
     * Ejemplo de uso desde main:
     * <pre>
     * List<SmsMessage> mensajes = ...; // lista con mensajes
     * SmsSender sender = new SmsSender(session, dbservice);
     * sender.sendMessagesInParallelWithDelayNonBlocking(mensajes, 1000);
     * // envía mensajes con 1s de delay entre ellos, pero retorna inmediatamente
     * </pre>
     * 
     * @param delayMs Delay en milisegundos entre cada envío.
     */
    public void sendMessagesInParallelWithDelayNonBlocking(List<SmsMessage> messages, long delayMs) {
        new ParallelWithDelaySender(
                new ArrayList<>(messages), delayMs > 0 ? delayMs : DEFAULT_DELAY_MS.toMillis()).start();
    }

    /**
     * Envía múltiples mensajes en secuencia con delay entre cada uno.
     * Este método bloquea hasta que todos los mensajes hayan sido enviados.
     * 
     * Ejemplo de uso desde main:
     * <pre>
     * List<SmsMessage> mensajes = ...; // lista con mensajes
     * SmsSender sender = new SmsSender(session, dbservice);
     * sender.sendMessagesSequentialWithDelayBlocking(mensajes, 500);
     * // espera bloqueante hasta que todos los mensajes se envíen
     * </pre>
     * 
     * @param delayMs Delay en milisegundos entre cada envío.
     */
    public void sendMessagesSequentialWithDelayBlocking(List<SmsMessage> messages, long delayMs) {
        try {
            sendMessagesSequentialWithDelayAsync(messages, delayMs).get(); // Espera bloqueante
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Envío secuencial con delay interrumpido", e);
        } catch (ExecutionException e) {
            logger.error("Error en envío secuencial con delay", e.getCause());
        }
    }

    /**
     * Envía múltiples mensajes en secuencia con delay entre cada uno.
     * Retorna un CompletableFuture para que el llamador decida si espera o no.
     * 
     * Ejemplo de uso desde main:
     * <pre>
     * List<SmsMessage> mensajes = ...; // lista con mensajes
     * SmsSender sender = new SmsSender(session, dbservice);
     * CompletableFuture<Void> future = sender.sendMessagesSequentialWithDelayAsync(mensajes, 500);
     * // puede hacer otras cosas aquí mientras se envían
     * future.thenRun(() -> System.out.println("Todos los mensajes enviados"));
     * // o esperar si desea bloquear:
     * // future.get();
     * </pre>
     * 
     * @param delayMs Delay en milisegundos entre cada envío.
     * @return CompletableFuture<Void> que representa la tarea asincrónica completa.
     */
    public CompletableFuture<Void> sendMessagesSequentialWithDelayAsync(List<SmsMessage> messages, long delayMs) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        long delay = delayMs > 0 ? delayMs : DEFAULT_DELAY_MS.toMillis();

    	String count = ThreadContext.get("contador");
        for (SmsMessage msg : messages) {
            future = future.thenRunAsync(() -> sendSingleMessage(msg, count), executor)
                           .thenCompose(v -> delayAsync(delay));
        }

        return future;
    }

    /**
     * Crea un CompletableFuture que completa después de un delay asincrónico.
     * Usa el ScheduledExecutorService para no bloquear el hilo.
     */
    private CompletableFuture<Void> delayAsync(long delayMs) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(null), delayMs, TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * Envía un solo mensaje vía SMPP, actualizando el estado en base de datos.
     * Maneja excepciones y loguea los eventos.
     */
    private void sendSingleMessage(SmsMessage msg, String count) {
        ThreadContext.put("contador", count);
    	ThreadContext.put("idMensaje", String.valueOf(msg.getIdMensaje()));

        try {
            SubmitSm submit = new SubmitSm();
            submit.setSourceAddress(new Address((byte) 0x01, (byte) 0x01, msg.getSource()));
            submit.setDestAddress(new Address((byte) 0x01, (byte) 0x01, msg.getDestination()));
            submit.setShortMessage(msg.getText().getBytes(StandardCharsets.UTF_8));

            logger.info(String.format("Enviar mensaje a [%s] con texto=[%s]", msg.getDestination(), msg.getText()));
            //dbservice.updateMessageStatus(msg.getIdMensaje(), SmsMessage.Status.EN_PROCESO_ENVIO, null, null, null);

            SmppSession session = sessionProvider.get();
            if (session == null || !session.isBound()) {
                logger.warn("Sesión SMPP no disponible o no está en estado BOUND. No se puede enviar el mensaje.");
                dbservice.updateMessageStatus(msg.getIdMensaje(), SmsMessage.Status.PENDIENTE_ENVIO, 999998, "Sesión no disponible", null);
                return;
            }

            // Medición de latencia de envío
            long inicio = System.currentTimeMillis();

            SubmitSmResp resp = session.submit(submit, TIMEOUT_3_SEGUNDOS.toMillis());

            long fin = System.currentTimeMillis();
            long duracionMs = fin - inicio;

            latencyStats.record(duracionMs); // Acumula la estadística

            logger.info(String.format("Mensaje enviado a [%s] con IdExterno=[%s] (latencia: %d ms)", msg.getDestination(), resp.getMessageId(), duracionMs));

            if (resp.getCommandStatus() == SmppConstants.STATUS_OK) {
                dbservice.updateMessageStatus(msg.getIdMensaje(), SmsMessage.Status.ENVIADO, resp.getCommandStatus(), resp.getResultMessage(), resp.getMessageId());
            } else {
                // Dependiendo del tipo de error, se marca como reintento, dejando en estado PENDIENTE_ENVIO
                if (CODIGOS_REINTENTOS.contains(resp.getCommandStatus()))
                    dbservice.updateMessageStatus(msg.getIdMensaje(), SmsMessage.Status.PENDIENTE_ENVIO, resp.getCommandStatus(), resp.getResultMessage(), null);
                else
                    dbservice.updateMessageStatus(msg.getIdMensaje(), SmsMessage.Status.PROCESADO_ERROR, resp.getCommandStatus(), resp.getResultMessage(), null);
            }

        } catch (Exception e) {
            logger.error(String.format("Error al enviar mensaje: [%s]", e.getMessage()));
            dbservice.updateMessageStatus(msg.getIdMensaje(), SmsMessage.Status.PENDIENTE_ENVIO, 999999, "Excepción: " + e.getMessage(), null);
        } finally {
        	ThreadContext.remove("idMensaje");
        	ThreadContext.remove("contador");
        }
    }

    /**
     * Detiene limpiamente los ejecutores.
     */
    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Clase interna para enviar mensajes en paralelo con delay entre envíos.
     * Usa ScheduledExecutorService para programar tareas repetidas.
     */
    private class ParallelWithDelaySender {
        private final Iterator<SmsMessage> iterator;
        private final long delayMs;
        private ScheduledFuture<?> future;

        public ParallelWithDelaySender(List<SmsMessage> messages, long delayMs) {
            this.iterator = messages.iterator();
            this.delayMs = delayMs;
        }

        public void start() {
        	String count = ThreadContext.get("contador");
            future = scheduler.scheduleWithFixedDelay(() -> {
                try {
                    if (iterator.hasNext()) {
                        SmsMessage msg = iterator.next();
                        sendSingleMessage(msg, count);
                    } else {
                        future.cancel(false);
                    }
                } catch (Exception e) {
                    logger.error(String.format("Error inesperado al enviar mensajes: [%s]", e.getMessage()));
                    future.cancel(false);
                }
            }, 0, delayMs, TimeUnit.MILLISECONDS);
        }
    }
}