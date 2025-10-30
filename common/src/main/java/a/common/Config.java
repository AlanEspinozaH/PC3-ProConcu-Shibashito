/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package a.common;
/** @author alulo*/
import java.io.IOException; 
import java.nio.file.*; 
import com.fasterxml.jackson.databind.*;
import java.util.Map;

public class Config {
  public final String mqHost, mqVhost, mqUser, mqPass, bankCmdEx, bankEvtEx, reniecRpcQueue;
  public final String pgUrl, pgUser, pgPass;
  public final String clientReplyQueue;

  public Config(String path) {
    try {
      var m = new ObjectMapper().readValue(Files.readString(Path.of(path)), Map.class);
      mqHost = (String)m.getOrDefault("mqHost","127.0.0.1");
      mqVhost= (String)m.getOrDefault("mqVhost","aaa");
      mqUser = (String)m.getOrDefault("mqUser","admin");
      mqPass = (String)m.getOrDefault("mqPass","admin");
      bankCmdEx = (String)m.getOrDefault("bankCmdEx","bank.cmd");
      bankEvtEx = (String)m.getOrDefault("bankEvtEx","bank.evt");
      reniecRpcQueue = (String)m.getOrDefault("reniecRpcQueue","q.reniec.rpc");
      clientReplyQueue = (String)m.getOrDefault("clientReplyQueue","q.bank.reply");
      pgUrl  = (String)m.getOrDefault("pgUrl","jdbc:postgresql://127.0.0.1:5432/bankdb");
      pgUser = (String)m.getOrDefault("pgUser","bank"); 
      pgPass = (String)m.getOrDefault("pgPass","bank");
    } catch (IOException e) { throw new RuntimeException(e); }
  }
}
