/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

/** @author alulo */

package a.desktop.client;

import a.common.Config;
import a.common.Rabbit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DesktopClient {
  static final ObjectMapper M = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    var cfgPath = System.getenv().getOrDefault("CFG", "config/desktop.json");
    var cfg = new Config(cfgPath);

    try (var conn = Rabbit.newConnection(cfg); var ch = conn.createChannel()) {
      // ---------- 1) RPC a RENIEC (validar DNI) ----------
      var corrRpc = UUID.randomUUID().toString();
      var replyQueue = ch.queueDeclare("", false, true, true, null).getQueue(); // exclusiva, auto-delete
      var latch = new ArrayBlockingQueue<Map>(1);

      var ctag = ch.basicConsume(replyQueue, true, (tag, msg) -> {
        if (corrRpc.equals(msg.getProperties().getCorrelationId())) {
          try {
            var map = M.readValue(msg.getBody(), Map.class);
            latch.offer(map);
          } catch (Exception e) { e.printStackTrace(); }
        }
      }, tag -> {});

      var req = Map.of("dni","01234567");
      ch.basicPublish("", cfg.reniecRpcQueue,
          new AMQP.BasicProperties.Builder().correlationId(corrRpc).replyTo(replyQueue).build(),
          M.writeValueAsBytes(req));

      var rpcResp = latch.poll(5, TimeUnit.SECONDS);
      ch.basicCancel(ctag);
      if (rpcResp == null || !(Boolean)rpcResp.getOrDefault("ok", false)) {
        System.out.println("[desktop] RENIEC no validó el DNI, abortando.");
        return;
      }

      System.out.println("[desktop] RENIEC OK: " + rpcResp);

      // ---------- 2) Consumidor de bank.reply ----------
      ch.exchangeDeclare(cfg.bankEvtEx, BuiltinExchangeType.FANOUT, true);
      ch.queueDeclare(cfg.clientReplyQueue, true, false, false, null);
      ch.queueBind(cfg.clientReplyQueue, cfg.bankEvtEx, "evt");

      var corr = UUID.randomUUID().toString();
      ch.basicConsume(cfg.clientReplyQueue, true, (tag, msg) -> {
        if (corr.equals(msg.getProperties().getCorrelationId())) {
          System.out.println("[desktop] Respuesta banco: " + new String(msg.getBody(), StandardCharsets.UTF_8));
        }
      }, tag -> {});

      // ---------- 3) Enviar comando (e.g., deposit) ----------
      ch.exchangeDeclare(cfg.bankCmdEx, BuiltinExchangeType.DIRECT, true);
      var cmd = Map.of(
        "messageId", UUID.randomUUID().toString(),
        "type", "deposit",
        "actorDni", "01234567",
        "payload", Map.of("accountId","A-001","amount",150.0)
      );

      ch.basicPublish(cfg.bankCmdEx, "cmd",
        new AMQP.BasicProperties.Builder().correlationId(corr).deliveryMode(2).build(),
        M.writeValueAsBytes(cmd));

      System.out.println("[desktop] Enviado deposit corrId=" + corr + ", esperando confirmación...");
      Thread.sleep(2000); // pequeña espera para ver el print; en GUI usaremos eventos/handlers
    }
  }
}


