package com.pickem.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class KeepAliveService {

    // Your exact Render production URL
    private static final String APP_URL = "https://pick-em-app.onrender.com";

    // Runs every 14 minutes (840,000 milliseconds)
    @Scheduled(fixedRate = 840000)
    public void keepAppAwake() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(APP_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Keep-alive ping sent to " + APP_URL + " - Status: " + response.statusCode());

        } catch (Exception e) {
            System.err.println("Keep-alive ping failed: " + e.getMessage());
        }
    }
}