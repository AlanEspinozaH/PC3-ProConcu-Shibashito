/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

/** @author alulo */

package a.desktop.client;

import a.common.Config;
import a.common.Rabbit;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class DesktopClient {
  public static void main(String[] args) throws Exception {
    var cfgPath = System.getenv().getOrDefault("CFG", "config/desktop.json");
    var cfg = new Config(cfgPath);
    try (var conn = Rabbit.newConnection(cfg); var ch = conn.createChannel()) {
      ch.exchangeDeclare(cfg.bankCmdEx, BuiltinExchangeType.DIRECT, true);
      var corr = UUID.randomUUID().toString();
      var props = new AMQP.BasicProperties.Builder().correlationId(corr).build();
      var json = """
        {"messageId":"%s","type":"deposit","actorDni":"01234567","payload":{"accountId":"A-001","amount":150.0}}
        """.formatted(UUID.randomUUID().toString());
      ch.basicPublish(cfg.bankCmdEx, "cmd", props, json.getBytes(StandardCharsets.UTF_8));
      System.out.println("[desktop] enviado deposit, corrId=" + corr);
    }
  }
}

