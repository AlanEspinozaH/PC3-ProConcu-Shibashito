/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package a.reniec.service.java;

/** @author alulo*/

import a.common.Config;
import a.common.Rabbit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ReniecServiceJava {
  static final ObjectMapper M = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    var cfgPath = System.getenv().getOrDefault("CFG", "config/reniec.json");
    var cfg = new Config(cfgPath);

    // Base de datos "falsa" en memoria para el MVP
    var padron = Map.of(
      "01234567", Map.of("dni","01234567","nombres","ALAN","apellidos","ESPINOZA","f_nac","2000-01-02"),
      "11112222", Map.of("dni","11112222","nombres","RATA","apellidos","TUI","f_nac","1999-05-01")
    );

    try (var conn = Rabbit.newConnection(cfg); var ch = conn.createChannel()) {
      ch.queueDeclare(cfg.reniecRpcQueue, true, false, false, null);
      ch.basicQos(50);
      System.out.println("[reniec] RPC listo en " + cfg.reniecRpcQueue);

      DeliverCallback cb = (tag, msg) -> {
        try {
          var props = msg.getProperties();
          var corrId = props.getCorrelationId();
          var replyTo = props.getReplyTo();

          var req = M.readValue(msg.getBody(), Map.class);
          var dni = String.valueOf(req.get("dni"));
          var found = padron.get(dni);

          byte[] resp;
          if (found != null) {
            resp = M.writeValueAsBytes(Map.of("ok", true, "dni", dni, "nombres", found.get("nombres"),
                    "apellidos", found.get("apellidos"), "f_nac", found.get("f_nac")));
          } else {
            resp = M.writeValueAsBytes(Map.of("ok", false, "error", "DNI_NO_ENCONTRADO"));
          }

          var replyProps = new AMQP.BasicProperties.Builder().correlationId(corrId).build();
          ch.basicPublish("", replyTo, replyProps, resp);
          ch.basicAck(msg.getEnvelope().getDeliveryTag(), false);
        } catch (Exception e) {
          e.printStackTrace();
          ch.basicNack(msg.getEnvelope().getDeliveryTag(), false, true);
        }
      };

      ch.basicConsume(cfg.reniecRpcQueue, false, cb, tag -> {});
      Thread.currentThread().join();
    }
  }
}

