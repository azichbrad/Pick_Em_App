package com.pickem.app.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Route("") // This is now the main entry point after login
@PermitAll
public class DashboardView extends VerticalLayout {

    public DashboardView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle().set("background-color", "#0b0f19");
        getElement().setAttribute("theme", "dark");

        // Get logged-in user email for greeting
        String userEmail = "User";
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            userEmail = oauth2User.getAttribute("name");
        }

        H1 title = new H1("Welcome, " + userEmail + "!");
        title.getStyle().set("color", "#f8fafc").set("font-size", "1.8rem").set("margin-bottom", "0px");

        Span subtitle = new Span("Select your league to view picks and leaderboards.");
        subtitle.getStyle().set("color", "#94a3b8").set("font-size", "0.95rem");

        // League Card Container
        VerticalLayout leagueCard = new VerticalLayout();
        leagueCard.getStyle()
                .set("background-color", "#151d30")
                .set("border", "1px solid #22304d")
                .set("border-radius", "16px")
                .set("padding", "24px")
                .set("width", "400px")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.3)");
        leagueCard.setSpacing(true);

        H3 leagueTitle = new H3("Pick'Em 2026-27");
        leagueTitle.getStyle().set("color", "#f8fafc").set("margin", "0");

        Span leagueDetails = new Span("5 Players • CFB & NFL Weekly Pick Tracking");
        leagueDetails.getStyle().set("color", "#94a3b8").set("font-size", "0.85rem");

        Button enterButton = new Button("Enter League Board", e -> {
            getUI().ifPresent(ui -> ui.navigate("board"));
        });
        enterButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        enterButton.setWidthFull();
        enterButton.getStyle().set("background-color", "#2563eb");

        leagueCard.add(leagueTitle, leagueDetails, enterButton);

        // Future extension placeholder
        Button createLeagueBtn = new Button("+ Create New League");
        createLeagueBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        createLeagueBtn.getStyle().set("color", "#60a5fa").set("margin-top", "15px");
        createLeagueBtn.addClickListener(e -> {
            // Placeholder for future multi-group creation logic
            com.vaadin.flow.component.notification.Notification.show("Custom league creation coming soon!");
        });

        add(title, subtitle, new VerticalLayout(leagueCard, createLeagueBtn));
        setHorizontalComponentAlignment(Alignment.CENTER, leagueCard, createLeagueBtn);
    }
}