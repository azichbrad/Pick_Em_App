package com.pickem.app.controller;

import com.pickem.app.repository.LeagueRepository;
import com.pickem.app.repository.PlayerRepository;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private final LeagueRepository leagueRepo;
    private final PlayerRepository playerRepo;
    private final String webhookSecret;

    public StripeWebhookController(
            LeagueRepository leagueRepo,
            PlayerRepository playerRepo,
            @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.leagueRepo = leagueRepo;
        this.playerRepo = playerRepo;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (sigHeader == null || webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature or webhook secret");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            System.err.println("❌ Invalid Stripe webhook signature: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = extractSession(event);
            if (session != null && "paid".equalsIgnoreCase(session.getPaymentStatus())) {
                fulfillProUnlock(session);
            }
        }

        return ResponseEntity.ok("Received");
    }

    private Session extractSession(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            StripeObject obj = deserializer.getObject().get();
            return (obj instanceof Session s) ? s : null;
        }
        try {
            StripeObject obj = deserializer.deserializeUnsafe();
            return (obj instanceof Session s) ? s : null;
        } catch (EventDataObjectDeserializationException e) {
            System.err.println("❌ Failed to deserialize Stripe session: " + e.getMessage());
            return null;
        }
    }

    private void fulfillProUnlock(Session session) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null) return;

        String scope = metadata.get("scope");
        String targetIdStr = metadata.get("targetId");
        if (scope == null || targetIdStr == null) return;

        Long targetId = Long.parseLong(targetIdStr);

        if ("LEAGUE".equalsIgnoreCase(scope)) {
            leagueRepo.findById(targetId).ifPresent(league -> {
                if (!league.isPremium()) {
                    league.setPremium(true);
                    leagueRepo.save(league);
                    System.out.println("✅ Unlocked League PRO for league ID " + targetId);
                }
            });
        } else if ("PLAYER".equalsIgnoreCase(scope)) {
            playerRepo.findById(targetId).ifPresent(player -> {
                if (!player.isPremium()) {
                    player.setPremium(true);
                    playerRepo.save(player);
                    System.out.println("✅ Unlocked Player PRO for player ID " + targetId);
                }
            });
        }
    }
}