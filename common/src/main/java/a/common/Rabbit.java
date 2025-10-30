/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package a.common;
/** @author alulo */

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;


public class Rabbit {
  public static Connection newConnection(Config c) throws Exception {
    ConnectionFactory f = new ConnectionFactory();
    f.setHost(c.mqHost);
    f.setUsername(c.mqUser);
    f.setPassword(c.mqPass);
    f.setVirtualHost(c.mqVhost);
    // f.setPort(5672); // si cambiaste el puerto por defecto
    f.setAutomaticRecoveryEnabled(true);
    f.setNetworkRecoveryInterval(5000);
    return f.newConnection("shibasito");
  }
}