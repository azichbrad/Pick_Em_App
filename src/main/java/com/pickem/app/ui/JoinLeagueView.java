package com.pickem.app.ui;

import com.pickem.app.model.League;
import com.pickem.app.model.LeagueMember;
import com.pickem.app.model.Player;
import com.pickem.app.repository.LeagueMemberRepository;
import com.pickem.app.repository.LeagueRepository;
import com.pickem.app.repository.PlayerRepository;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Route("join")
@PermitAll
public class JoinLeagueView extends VerticalLayout implements HasUrlParameter<String> {

    private final LeagueRepository leagueRepo;
    private final LeagueMemberRepository leagueMemberRepo;
    private final PlayerRepository playerRepo;

    private final VerticalLayout card = new VerticalLayout();

    public JoinLeagueView(LeagueRepository leagueRepo,
                          LeagueMemberRepository leagueMemberRepo,
                          PlayerRepository playerRepo) {
        this.leagueRepo = leagueRepo;
        this.leagueMemberRepo = leagueMemberRepo;
        this.playerRepo = playerRepo;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle().set("background-color", "#0b0f19");
        getElement().setAttribute("theme", "dark");

        card.setAlignItems(Alignment.CENTER);
        card.setSpacing(true);
        card.getStyle()
                .set("background-color", "#151d30")
                .set("border", "1px solid #22304d")
                .set("border-radius", "16px")
                .set("padding", "32px 24px")
                .set("width", "420px")
                .set("max-width", "92vw")
                .set("box-shadow", "0 8px 24px rgba(0,0,0,0.4)")
                .set("text-align", "center");

        add(card);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String rawCode) {
        card.removeAll();

        if (rawCode == null || rawCode.isBlank()) {
            showErrorState("Invalid Invite Link", "No invite code was included in this link.");
            return;
        }

        String code = rawCode.trim().toUpperCase();
        League league = leagueRepo.findByInviteCode(code).orElse(null);

        if (league == null) {
            showErrorState("League Not Found", "We couldn't find a league matching code \"" + code + "\".");
            return;
        }

        Player currentPlayer = resolveOrCreateCurrentPlayer();
        if (currentPlayer == null) {
            showErrorState("Sign-In Required", "Please sign in with Google to accept this league invite.");
            return;
        }

        boolean alreadyMember = leagueMemberRepo.existsByLeagueIdAndPlayerId(league.getId(), currentPlayer.getId());
        if (alreadyMember) {
            showAlreadyMemberState(league);
        } else {
            showJoinConfirmation(league, currentPlayer);
        }
    }

    private void showJoinConfirmation(League league, Player currentPlayer) {
        Span badge = new Span("LEAGUE INVITE");
        badge.getStyle()
                .set("background-color", "#1e3a8a")
                .set("color", "#93c5fd")
                .set("padding", "4px 10px")
                .set("border-radius", "999px")
                .set("font-size", "0.75rem")
                .set("font-weight", "700")
                .set("letter-spacing", "0.05em");

        H2 title = new H2("Join " + league.getName() + "?");
        title.getStyle().set("color", "#f8fafc").set("margin", "8px 0 0 0");

        int picksPerWeek = league.getPicksPerWeek() != null ? league.getPicksPerWeek() : 5;
        boolean groupParlays = Boolean.TRUE.equals(league.getEnableGroupParlay());
        Span details = new Span("Weekly Picks: " + picksPerWeek + "  •  Group Parlays: " + (groupParlays ? "On" : "Off"));
        details.getStyle().set("color", "#94a3b8").set("font-size", "0.9rem");

        Button joinBtn = new Button("Join " + league.getName(), e -> {
            if (!leagueMemberRepo.existsByLeagueIdAndPlayerId(league.getId(), currentPlayer.getId())) {
                leagueMemberRepo.save(new LeagueMember(league, currentPlayer));
            }
            Notification toast = Notification.show("Welcome to " + league.getName() + "!", 3500, Notification.Position.TOP_CENTER);
            toast.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            enterLeague(league);
        });
        joinBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        joinBtn.setWidthFull();
        joinBtn.getStyle().set("background-color", "#10b981").set("color", "#0b0f19").set("font-weight", "700");

        Button declineBtn = new Button("Not Now", e -> UI.getCurrent().navigate(DashboardView.class));
        declineBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        declineBtn.setWidthFull();
        declineBtn.getStyle().set("color", "#94a3b8");

        card.add(badge, title, details, joinBtn, declineBtn);
    }

    private void showAlreadyMemberState(League league) {
        H2 title = new H2("You're Already In!");
        title.getStyle().set("color", "#f8fafc").set("margin", "0");

        Span subtitle = new Span("You are already a member of " + league.getName() + ".");
        subtitle.getStyle().set("color", "#94a3b8").set("font-size", "0.95rem");

        Button openBtn = new Button("Enter League Board", e -> enterLeague(league));
        openBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        openBtn.getStyle().set("background-color", "#2563eb");

        Button dashboardBtn = new Button("Dashboard", e -> UI.getCurrent().navigate(DashboardView.class));
        dashboardBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dashboardBtn.getStyle().set("color", "#94a3b8");

        HorizontalLayout actions = new HorizontalLayout(openBtn, dashboardBtn);
        actions.setJustifyContentMode(JustifyContentMode.CENTER);
        card.add(title, subtitle, actions);
    }

    private void showErrorState(String heading, String message) {
        H2 title = new H2(heading);
        title.getStyle().set("color", "#f8fafc").set("margin", "0");

        Span subtitle = new Span(message);
        subtitle.getStyle().set("color", "#94a3b8").set("font-size", "0.95rem");

        Button backBtn = new Button("Back to Dashboard", e -> UI.getCurrent().navigate(DashboardView.class));
        backBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(title, subtitle, backBtn);
    }

    private void enterLeague(League league) {
        VaadinSession.getCurrent().setAttribute("league_id", league.getId());
        VaadinSession.getCurrent().setAttribute("league_name", league.getName());
        UI.getCurrent().navigate("board");
    }

    private Player resolveOrCreateCurrentPlayer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof OAuth2User oauth2User)) {
            return null;
        }
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        if (email == null) {
            return null;
        }

        return playerRepo.findByEmail(email).orElseGet(() -> {
            Player newPlayer = new Player();
            newPlayer.setEmail(email);
            newPlayer.setName(name != null ? name : "Player");
            return playerRepo.save(newPlayer);
        });
    }
}