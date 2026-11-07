package com.pickem.app.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;

public class MainLayout extends Div implements RouterLayout {

    private final Div content = new Div();

    public MainLayout() {
        setSizeFull();
        getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("background-color", "#0b0f19")
                .set("color", "#f8fafc");

        // 1. Sleek, compact top header
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle()
                .set("background-color", "#111827")
                .set("border-bottom", "1px solid #1f2937")
                .set("flex-shrink", "0");

        // Left side: App Name + Active League Badge
        HorizontalLayout branding = new HorizontalLayout();
        branding.setAlignItems(FlexComponent.Alignment.CENTER);
        branding.setSpacing(true);

        H3 logo = new H3("Pick'Em");
        logo.getStyle().set("margin", "0").set("font-size", "1.2rem").set("color", "#f8fafc");

        String leagueName = (String) VaadinSession.getCurrent().getAttribute("league_name");
        Span leagueBadge = new Span(leagueName != null ? leagueName : "League");
        leagueBadge.getStyle()
                .set("background-color", "#1e3a8a")
                .set("color", "#93c5fd")
                .set("padding", "2px 8px")
                .set("border-radius", "6px")
                .set("font-size", "0.75rem")
                .set("font-weight", "600");

        branding.add(logo, leagueBadge);

        // Right side: Quick Links (Dashboard Button + Sign Out)
        HorizontalLayout actions = new HorizontalLayout();
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.setSpacing(true);

        RouterLink dashboardLink = new RouterLink("Dashboard", DashboardView.class);
        dashboardLink.getStyle()
                .set("color", "#94a3b8")
                .set("font-size", "0.85rem")
                .set("text-decoration", "none");

        Button logoutBtn = new Button("Sign Out", e -> {
            VaadinSession.getCurrent().getSession().invalidate();
            UI.getCurrent().getPage().setLocation("/logout");
        });
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        logoutBtn.getStyle().set("color", "#ef4444").set("font-size", "0.85rem");

        actions.add(dashboardLink, logoutBtn);
        header.add(branding, actions);

        // 2. Main content area wrapper
        content.setSizeFull();
        content.getStyle()
                .set("flex-grow", "1")
                .set("overflow", "auto");

        add(header, content);
    }

    @Override
    public void showRouterLayoutContent(com.vaadin.flow.component.HasElement contentComponent) {
        content.removeAll();
        if (contentComponent != null) {
            content.getElement().appendChild(contentComponent.getElement());
        }
    }
}