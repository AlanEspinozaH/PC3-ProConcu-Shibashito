/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

/** @author alulo */

package a.bank.service;

import a.common.Config;
import a.common.Rabbit;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;

public class BankService {
  public static void main(String[] args) throws Exception {
    var cfgPath = System.getenv().getOrDefault("CFG", "config/bank.json");
    var cfg = new Config(cfgPath);
    try (var conn = Rabbit.newConnection(cfg); var ch = conn.createChannel()) {
      ch.exchangeDeclare(cfg.bankCmdEx, BuiltinExchangeType.DIRECT, true);
      ch.queueDeclare("q.bank.cmd", true, false, false, null);
      ch.queueBind("q.bank.cmd", cfg.bankCmdEx, "cmd");
      ch.basicQos(50);

      DeliverCallback cb = (tag, msg) -> {
        var body = new String(msg.getBody(), StandardCharsets.UTF_8);
        System.out.println("[bank] recibido: " + body);
        ch.basicAck(msg.getEnvelope().getDeliveryTag(), false);
      };
      ch.basicConsume("q.bank.cmd", false, cb, tag -> {});
      System.out.println("BankService up. Waiting messages...");
      Thread.currentThread().join();
    }
  }
}
