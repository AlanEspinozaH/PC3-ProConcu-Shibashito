/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

/** @author alulo */

package a.bank.service;

import a.common.Config;
import a.common.Rabbit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import java.util.LinkedHashMap;
import java.util.Objects;

public class BankService {
  static final ObjectMapper M = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    var cfgPath = System.getenv().getOrDefault("CFG", "config/bank.json");
    var cfg = new Config(cfgPath);

    // Pool JDBC
    var hk = new HikariConfig();
    hk.setJdbcUrl(cfg.pgUrl);
    hk.setUsername(cfg.pgUser);
    hk.setPassword(cfg.pgPass);
    hk.setMaximumPoolSize(10);
    try (var ds = new HikariDataSource(hk);
         var conn = Rabbit.newConnection(cfg);
         var ch = conn.createChannel()) {

      // Infra AMQP
      ch.exchangeDeclare(cfg.bankCmdEx, BuiltinExchangeType.DIRECT, true);
      ch.exchangeDeclare(cfg.bankEvtEx, BuiltinExchangeType.FANOUT, true);
      ch.queueDeclare("q.bank.cmd", true, false, false, null);
      ch.queueBind("q.bank.cmd", cfg.bankCmdEx, "cmd");
      ch.queueDeclare(cfg.clientReplyQueue, true, false, false, null); // cliente se puede suscribir aquí
      ch.basicQos(50);

      System.out.println("BankService up. Waiting messages...");

      DeliverCallback cb = (tag, msg) -> {
        var corrId = msg.getProperties().getCorrelationId();
        try {
          var body = new String(msg.getBody(), StandardCharsets.UTF_8);
          var root = M.readTree(body);
          var messageId = root.path("messageId").asText();
          var type = root.path("type").asText();
          var payload = root.path("payload");

          var ok = false;
          String error = null;
          Map<String,Object> result = null;

          try (var cx = ds.getConnection()) {
            cx.setAutoCommit(false);

            if (!registerInbox(cx, messageId)) {
              // ya procesado anteriormente → idempotencia
              ok = true; error = null;
            } else {
              switch (type) {
                case "deposit" -> {
                  var acc = payload.path("accountId").asText();
                  var amount = payload.path("amount").decimalValue();
                  applyDeposit(cx, acc, amount);
                  ok = true; result = Map.of("accountId", acc, "applied", amount);
                }
                case "withdraw" -> {
                  var acc = payload.path("accountId").asText();
                  var amount = payload.path("amount").decimalValue();
                  if (!applyWithdraw(cx, acc, amount)) {
                    throw new IllegalStateException("INSUFFICIENT_FUNDS");
                  }
                  ok = true; result = Map.of("accountId", acc, "applied", amount);
                }
                case "transfer" -> {
                  var from = payload.path("fromAccountId").asText();
                  var to   = payload.path("toAccountId").asText();
                  var amount = payload.path("amount").decimalValue();
                  applyTransfer(cx, from, to, amount);
                  ok = true; result = Map.of("from", from, "to", to, "applied", amount);
                }
                default -> throw new IllegalArgumentException("UNKNOWN_TYPE");
              }
            }

            cx.commit();
          } catch (Exception ex) {
            error = ex.getMessage();
          }

          // publicar evento (sin nulls)
var evtMap = new LinkedHashMap<String, Object>();
evtMap.put("correlationId", Objects.toString(corrId, "")); // evita null
evtMap.put("ok", ok);
evtMap.put("type", type + (ok ? "_ok" : "_err"));
if (result != null) evtMap.put("result", result);
if (error  != null) evtMap.put("error",  error);

var evt = M.writeValueAsBytes(evtMap);

ch.basicPublish(
    cfg.bankEvtEx, "evt",
    new AMQP.BasicProperties.Builder()
        .correlationId(corrId)
        .contentType("application/json")
        .contentEncoding("utf-8")
        .build(),
    evt
);

ch.basicAck(msg.getEnvelope().getDeliveryTag(), false);
} catch (Exception e) {
  e.printStackTrace();
  // evita el loop infinito de reentrega
  ch.basicNack(msg.getEnvelope().getDeliveryTag(), false, false);
}
}; // el ; cierra el lambda y la asignación de 'cb'

ch.basicConsume("q.bank.cmd", false, cb, tag -> {});
Thread.currentThread().join();
    } //cierra el try-with-resources (ds, conn, ch)
  }

  // =============== Helpers JDBC ================

  static boolean registerInbox(Connection cx, String messageId) throws SQLException {
    try (var ps = cx.prepareStatement("insert into public.inbox(message_id) values (?) on conflict do nothing")) {
      ps.setString(1, messageId);
      return ps.executeUpdate() == 1; // true si es la 1ra vez; false si duplicado
    }
  }

  static void applyDeposit(Connection cx, String accountId, BigDecimal amount) throws SQLException {
    try (var ps = cx.prepareStatement("update cuentas set saldo = saldo + ? where id_cuenta=?")) {
      ps.setBigDecimal(1, amount);
      ps.setString(2, accountId);
      if (ps.executeUpdate() != 1) throw new SQLException("ACCOUNT_NOT_FOUND");
    }
  }

  static boolean applyWithdraw(Connection cx, String accountId, BigDecimal amount) throws SQLException {
    // Garantiza fondos: condicional en WHERE
    try (var ps = cx.prepareStatement("update cuentas set saldo = saldo - ? where id_cuenta=? and saldo >= ?")) {
      ps.setBigDecimal(1, amount);
      ps.setString(2, accountId);
      ps.setBigDecimal(3, amount);
      return ps.executeUpdate() == 1;
    }
  }

  static void applyTransfer(Connection cx, String from, String to, BigDecimal amount) throws SQLException {
    if (!applyWithdraw(cx, from, amount)) throw new SQLException("INSUFFICIENT_FUNDS");
    try (var ps = cx.prepareStatement("update cuentas set saldo = saldo + ? where id_cuenta=?")) {
      ps.setBigDecimal(1, amount);
      ps.setString(2, to);
      if (ps.executeUpdate() != 1) throw new SQLException("DEST_NOT_FOUND");
    }
  }
}

