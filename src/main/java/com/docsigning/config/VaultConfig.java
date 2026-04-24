package com.docsigning.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

@Configuration
public class VaultConfig {

    @Value("${vault.host:openbao}")
    private String vaultHost;

    @Value("${vault.port:8200}")
    private int vaultPort;

    @Value("${vault.scheme:http}")
    private String vaultScheme;

    @Value("${vault.token:root}")
    private String vaultToken;

    @Bean
    public VaultEndpoint vaultEndpoint() {
        VaultEndpoint endpoint = new VaultEndpoint();
        endpoint.setHost(vaultHost);
        endpoint.setPort(vaultPort);
        endpoint.setScheme(vaultScheme);
        return endpoint;
    }

    @Bean
    public ClientAuthentication clientAuthentication() {
        return new TokenAuthentication(vaultToken);
    }

    @Bean
    public VaultTemplate vaultTemplate(VaultEndpoint vaultEndpoint, ClientAuthentication clientAuthentication) {
        return new VaultTemplate(vaultEndpoint, clientAuthentication);
    }
}
