package io.akka.vanna;

import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.typesafe.config.Config;

@Setup
public class Bootstrap implements ServiceSetup {

  public Bootstrap(Config config) {
    String provider = config.getString("akka.javasdk.agent.model-provider");
    String keyPath = "akka.javasdk.agent." + provider + ".api-key";
    if (config.hasPath(keyPath) && config.getString(keyPath).isBlank()) {
      throw new IllegalStateException(
          "No API key found for model provider '"
              + provider
              + "'. Set the matching environment variable, or change "
              + "akka.javasdk.agent.model-provider in application.conf.");
    }
  }
}
