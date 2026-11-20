package com.pickem.app.service.billing;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeCheckoutService {

    private final String secretKey;
    private final String leagueProPriceId;
    private final String playerProPriceId;
    private final String appBaseUrl;
    private StripeClient stripeClient;

    public StripeCheckoutService(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.league-pro-price-id:}") String leagueProPriceId,
            @Value("${stripe.player-pro-price-id:}") String playerProPriceId,
            @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.secretKey = secretKey;
        this.leagueProPriceId = leagueProPriceId;
        this.playerProPriceId = playerProPriceId;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    private synchronized StripeClient getClient() {
        if (stripeClient == null) {
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("STRIPE_SECRET_KEY is not configured.");
            }
            stripeClient = new StripeClient(secretKey);
        }
        return stripeClient;
    }

    public boolean isPlayerProEnabled() {
        return playerProPriceId != null && !playerProPriceId.isBlank();
    }

    public String createLeagueProCheckoutUrl(Long leagueId, String customerEmail) throws StripeException {
        if (leagueProPriceId == null || leagueProPriceId.isBlank()) {
            throw new IllegalStateException("STRIPE_LEAGUE_PRO_PRICE_ID is not configured.");
        }
        return createOneTimeCheckoutUrl(leagueProPriceId, "LEAGUE", leagueId, customerEmail);
    }

    public String createPlayerProCheckoutUrl(Long playerId, String customerEmail) throws StripeException {
        if (playerProPriceId == null || playerProPriceId.isBlank()) {
            throw new IllegalStateException("STRIPE_PLAYER_PRO_PRICE_ID is not configured.");
        }
        return createOneTimeCheckoutUrl(playerProPriceId, "PLAYER", playerId, customerEmail);
    }

    private String createOneTimeCheckoutUrl(String priceId, String scope, Long targetId, String customerEmail)
            throws StripeException {

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(appBaseUrl + "/board?pro=success")
                .setCancelUrl(appBaseUrl + "/board?pro=canceled")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("scope", scope)
                .putMetadata("targetId", String.valueOf(targetId));

        if (customerEmail != null && !customerEmail.isBlank()) {
            paramsBuilder.setCustomerEmail(customerEmail);
        }

        Session session = getClient().v1().checkout().sessions().create(paramsBuilder.build());
        return session.getUrl();
    }
}