# Stripe PRO Checkout Implementation TODO

Scope: Stripe Hosted Checkout strictly for unlocking **PRO** analytics (individual player or league-wide). End-of-season league loss penalties remain settled outside the app.

## 1. Stripe Dashboard & Environment Setup
- [ ] Create a Stripe account (or open your existing dashboard) and switch to **Test Mode** for initial development.
- [ ] Create your PRO product(s) and price(s) in Stripe (e.g., Individual PRO and/or League-Wide PRO) and copy the `price_...` IDs.
- [ ] Copy your test **Secret Key** (`sk_test_...`) and **Publishable Key** (if needed).
- [ ] Configure environment variables locally (IntelliJ run configuration / `.env`) and in production on **Render**:
  - `STRIPE_SECRET_KEY`
  - `STRIPE_WEBHOOK_SECRET`
  - `STRIPE_PLAYER_PRO_PRICE_ID` (and/or `STRIPE_LEAGUE_PRO_PRICE_ID`)
  - `APP_BASE_URL` (e.g., `http://localhost:8080` locally and your Render URL in production, used for `success_url` and `cancel_url`)

## 2. Maven Dependencies & Security Configuration
- [ ] Add `com.stripe:stripe-java` to `pom.xml` and reload Maven in IntelliJ.
- [ ] Bind your Stripe properties (`stripe.secret-key`, `stripe.webhook-secret`, `stripe.player-pro-price-id`, `stripe.league-pro-price-id`, `app.base-url`) in `application.yml` or `application.properties`.
- [ ] Update `SecurityConfiguration.java` so `POST /api/webhooks/stripe` is accessible without OAuth2 login redirects.

## 3. Database & Idempotency Guardrails
- [ ] Confirm `League.isPremium()` and `Player.isPremium()` map cleanly to boolean columns in your Supabase PostgreSQL database.
- [ ] Add an idempotency check or table tracking processed Stripe event IDs (`evt_...`) / session IDs (`cs_...`) so retried webhook deliveries do not cause duplicate side effects or errors.

## 4. Backend Checkout Service & Webhook Controller
- [ ] Create `StripeCheckoutService` (`com.pickem.app.service`) to initialize `Stripe.apiKey` and create a Stripe Checkout `Session` (`PAYMENT` or `SUBSCRIPTION` mode) with:
  - `line_items` referencing your PRO price ID
  - `metadata` containing `scope` (`"PLAYER"` or `"LEAGUE"`), `playerId` (when applicable), and `leagueId` (when applicable)
  - `success_url` and `cancel_url` returning to `/board` or `/dashboard` (e.g., `?pro=success` or `?pro=canceled`)
- [ ] Create `StripeWebhookController` (`com.pickem.app.controller`) for `POST /api/webhooks/stripe`:
  - Read the raw request body (`@RequestBody String payload`) and `@RequestHeader("Stripe-Signature") String sigHeader` without modifying the JSON string prior to verification.
  - Validate the signature using `Webhook.constructEvent(payload, sigHeader, webhookSecret)` and return `400` on invalid signatures.
  - Handle `checkout.session.completed` (plus subscription lifecycle events if using recurring billing): read `scope`, `playerId`, and `leagueId` from `session.getMetadata()`, set `premium = true` on the target `Player` or `League`, and persist via your repository.
  - Return `200 OK` promptly once processed.

## 5. Vaadin UI Hookup (`AnalyticsDialog.java` & Return Flow)
- [ ] Inject `StripeCheckoutService` where `AnalyticsDialog` is opened (such as `BoardView.java`) and pass it into `AnalyticsDialog`.
- [ ] Update `openUnlockModal()` in `AnalyticsDialog.java` (and offer scope selection if unlocking both individual and league-wide PRO) to create a Checkout Session and redirect via `UI.getCurrent().getPage().open(session.getUrl(), "_self")`.
- [ ] Handle `?pro=success` and `?pro=canceled` on return so users receive clear feedback and see updated PRO status once the webhook completes.

## 6. Local Testing, Render Deploy & Live Cutover
- [ ] Run Stripe CLI locally to forward events to your Spring Boot server:
  ```bash
  stripe listen --forward-to localhost:8080/api/webhooks/stripe
  ```
- [ ] Complete a test checkout with a Stripe test card, verify `checkout.session.completed` reaches your webhook handler, and confirm `premium` updates to `true` in Supabase and unlocks `AnalyticsDialog`.
- [ ] Test canceled checkouts and invalid webhook signatures (`400`) to verify error handling.
- [ ] Add live Stripe environment variables on **Render**, push to `main`, and once live, configure a **Live** webhook endpoint in the Stripe Dashboard pointing to `https://<your-render-domain>/api/webhooks/stripe` for `checkout.session.completed` using the live `whsec_...` signing secret.