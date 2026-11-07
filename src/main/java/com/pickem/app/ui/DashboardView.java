package com.pickem.app.ui;

import com.pickem.app.model.League;
import com.pickem.app.model.LeagueMember;
import com.pickem.app.model.Player;
import com.pickem.app.repository.LeagueMemberRepository;
import com.pickem.app.repository.PlayerRepository;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;

@Route("")
@PermitAll
public class DashboardView extends VerticalLayout {

    private final PlayerRepository playerRepo;
    private final LeagueMemberRepository leagueMemberRepo;

    public DashboardView(PlayerRepository playerRepo, LeagueMemberRepository leagueMemberRepo) {
        this.playerRepo = playerRepo;
        this.leagueMemberRepo = leagueMemberRepo;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("background-color", "#0b0f19");
        getElement().setAttribute("theme", "dark");

        // 1. Top Header with Logout Button
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.END);
        header.setPadding(true);

        Button logoutBtn = new Button("Sign Out", e -> {
            UI.getCurrent().getPage().setLocation("/logout");
        });
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logoutBtn.getStyle().set("color", "#ef4444");
        header.add(logoutBtn);

        // 2. Fetch logged-in user
        String userEmail = "User";
        String userName = "Player";
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            userEmail = oauth2User.getAttribute("email");
            userName = oauth2User.getAttribute("name");
        }

        // 3. Welcome Titles
        VerticalLayout titleLayout = new VerticalLayout();
        titleLayout.setAlignItems(Alignment.CENTER);

        H1 title = new H1("Welcome, " + userName + "!");
        title.getStyle().set("color", "#f8fafc").set("font-size", "1.8rem").set("margin-bottom", "0px");

        Span subtitle = new Span("Select your league to view picks and leaderboards.");
        subtitle.getStyle().set("color", "#94a3b8").set("font-size", "0.95rem");
        titleLayout.add(title, subtitle);

        // 4. Main Container for League Cards
        VerticalLayout leaguesContainer = new VerticalLayout();
        leaguesContainer.setAlignItems(Alignment.CENTER);
        leaguesContainer.setWidthFull();

        // 5. Query Database for User's Leagues
        Player currentPlayer = playerRepo.findByEmail(userEmail).orElse(null);

        if (currentPlayer != null) {
            List<LeagueMember> memberships = leagueMemberRepo.findByPlayerId(currentPlayer.getId());

            if (memberships.isEmpty()) {
                Span noLeagues = new Span("You are not in any leagues yet.");
                noLeagues.getStyle().set("color", "#94a3b8").set("margin-top", "20px");
                leaguesContainer.add(noLeagues);
            } else {
                for (LeagueMember membership : memberships) {
                    leaguesContainer.add(createLeagueCard(membership.getLeague()));
                }
            }
        } else {
            // Guardrail if the OAuth user isn't in your players table yet
            Span errorSpan = new Span("Account not found in database. Please contact admin.");
            errorSpan.getStyle().set("color", "#ef4444");
            leaguesContainer.add(errorSpan);
        }

        // 6. Create/Join League Buttons
        HorizontalLayout actionButtons = new HorizontalLayout();

        Button joinLeagueBtn = new Button("Join League");
        joinLeagueBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        joinLeagueBtn.getStyle().set("color", "#10b981"); // Emerald green
        joinLeagueBtn.addClickListener(e -> {
            com.vaadin.flow.component.notification.Notification.show("Join League prompt coming soon!");
        });

        Button createLeagueBtn = new Button("+ Create New League");
        createLeagueBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        createLeagueBtn.getStyle().set("color", "#60a5fa"); // Blue
        createLeagueBtn.addClickListener(e -> {
            com.vaadin.flow.component.notification.Notification.show("Custom league creation coming soon!");
        });

        actionButtons.add(joinLeagueBtn, createLeagueBtn);

        add(header, titleLayout, leaguesContainer, actionButtons);
    }

    private VerticalLayout createLeagueCard(League league) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("background-color", "#151d30")
                .set("border", "1px solid #22304d")
                .set("border-radius", "16px")
                .set("padding", "24px")
                .set("width", "400px")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.3)")
                .set("margin-bottom", "15px");
        card.setSpacing(true);

        H3 leagueTitle = new H3(league.getName());
        leagueTitle.getStyle().set("color", "#f8fafc").set("margin", "0");

        Span leagueDetails = new Span("CFB & NFL Weekly Pick Tracking");
        leagueDetails.getStyle().set("color", "#94a3b8").set("font-size", "0.85rem");

        Button enterButton = new Button("Enter League Board", e -> {
            // THE MOST IMPORTANT STEP: Save the selected league ID to the Vaadin session
            VaadinSession.getCurrent().setAttribute("league_id", league.getId());
            VaadinSession.getCurrent().setAttribute("league_name", league.getName());

            // Navigate to the board
            UI.getCurrent().navigate("board");
        });
        enterButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        enterButton.setWidthFull();
        enterButton.getStyle().set("background-color", "#2563eb");

        card.add(leagueTitle, leagueDetails, enterButton);
        return card;
    }
}