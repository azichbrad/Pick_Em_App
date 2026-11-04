package com.pickem.app.config;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        http.oauth2Login(oauth2 -> oauth2.loginPage("/oauth2/authorization/google").permitAll());
    }

    @Override
    protected void configure(WebSecurity web) throws Exception {
        super.configure(web);
        // Completely bypasses Spring Security and Vaadin UI routing for your API endpoints
        web.ignoring().requestMatchers("/api/sync/**");
    }
}