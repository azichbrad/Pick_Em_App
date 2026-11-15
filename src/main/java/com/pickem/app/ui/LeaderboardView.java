package com.pickem.app.ui;

import com.pickem.app.dto.LeaderboardEntryDTO;
import com.pickem.app.service.player.LeaderboardService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import jakarta.annotation.security.PermitAll;

import java.util.List;

@Route("leaderboard")
@PageTitle("League Standings")
@PermitAll
public class LeaderboardView extends VerticalLayout implements HasUrlParameter<Long> {

    private final LeaderboardService leaderboardService;
    private Long currentLeagueId;
    private String currentSport = "ALL";

    private final Grid<LeaderboardEntryDTO> grid = new Grid<>(LeaderboardEntryDTO.class, false);
    private final NumberField unitStakeField = new NumberField("Unit Stake ($)");

    public LeaderboardView(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;

        // --- FORCE DARK MODE & APP BACKGROUND ---
        getElement().getThemeList().add(Lumo.DARK);
        getStyle().set("background-color", "#0b1120"); // Matches standard sleek dark apps
        getStyle().set("color", "#f8fafc");
        setSizeFull();
        setAlignItems(Alignment.CENTER);

        // --- Header & Back Button ---
        Button backBtn = new Button("Back to Dashboard", new Icon(VaadinIcon.ARROW_LEFT));
        backBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST);
        backBtn.getStyle().set("align-self", "flex-start").set("margin-top", "10px");
        backBtn.addClickListener(e -> UI.getCurrent().navigate(""));

        H2 title = new H2("Live Standings & Vegas Analytics");
        title.getStyle().set("margin-top", "0").set("color", "#38bdf8"); // Slick light blue title

        // --- Sport Toggle Tabs ---
        Tab tabAll = new Tab("Overall Combined");
        Tab tabNfl = new Tab("NFL Only");
        Tab tabCfb = new Tab("CFB Only");
        Tabs tabs = new Tabs(tabAll, tabNfl, tabCfb);
        tabs.addThemeVariants(com.vaadin.flow.component.tabs.TabsVariant.LUMO_EQUAL_WIDTH_TABS);
        tabs.addSelectedChangeListener(event -> {
            if (event.getSelectedTab().equals(tabAll)) currentSport = "ALL";
            else if (event.getSelectedTab().equals(tabNfl)) currentSport = "NFL";
            else currentSport = "NCAAF";
            refreshGrid();
        });

        // --- Interactive Bankroll Calculator ---
        unitStakeField.setValue(100.0);
        unitStakeField.setStep(10.0);
        unitStakeField.setMin(1.0);
        unitStakeField.setStepButtonsVisible(true);
        unitStakeField.addValueChangeListener(e -> refreshGrid());

        HorizontalLayout controlsLayout = new HorizontalLayout(tabs, unitStakeField);
        controlsLayout.setAlignItems(Alignment.BASELINE);
        controlsLayout.setWidthFull();
        controlsLayout.setMaxWidth("1000px"); // Keep it contained on large screens
        controlsLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        controlsLayout.getStyle().set("background-color", "#151d30").set("padding", "15px").set("border-radius", "12px");

        // --- Configure the Grid ---
        configureGrid();

        VerticalLayout contentContainer = new VerticalLayout(backBtn, title, controlsLayout, grid);
        contentContainer.setMaxWidth("1000px");
        contentContainer.setWidthFull();
        contentContainer.setPadding(false);

        add(contentContainer);
    }

    @Override
    public void setParameter(BeforeEvent event, Long leagueId) {
        this.currentLeagueId = leagueId;
        refreshGrid();
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        // Clean up the grid styling to make it look like a modern sports app
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.getStyle().set("border-radius", "12px").set("box-shadow", "0 4px 12px rgba(0,0,0,0.5)");

        grid.addColumn(new ComponentRenderer<>(entry -> {
            Span nameSpan = new Span(entry.playerName());
            nameSpan.getStyle().set("font-weight", "bold").set("font-size", "1.1rem");

            if (entry.wins() > 0 && entry.equals(getTopPlayer())) {
                nameSpan.setText("👑 " + entry.playerName());
                nameSpan.getStyle().set("color", "#fbbf24"); // Glowing Amber for 1st Place
            }
            return nameSpan;
        })).setHeader("Player").setAutoWidth(true).setFlexGrow(1);

        grid.addColumn(entry -> entry.wins() + "-" + entry.losses() + "-" + entry.pushes())
                .setHeader("Record").setAutoWidth(true);

        grid.addColumn(entry -> String.format("%.1f%%", entry.winPercentage()))
                .setHeader("Win %").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(entry -> {
            Span profitSpan = new Span(String.format(entry.hypotheticalProfit() >= 0 ? "+$%.2f" : "-$%.2f", Math.abs(entry.hypotheticalProfit())));
            if (entry.hypotheticalProfit() > 0) {
                profitSpan.getStyle().set("color", "#4ade80"); // Tailwind Green-400
                profitSpan.getStyle().set("font-weight", "bold");
            } else if (entry.hypotheticalProfit() < 0) {
                profitSpan.getStyle().set("color", "#f87171"); // Tailwind Red-400
            }
            return profitSpan;
        })).setHeader("Vegas P/L").setAutoWidth(true);

        grid.addColumn(LeaderboardEntryDTO::aiStatusTag)
                .setHeader("Bettor Status").setAutoWidth(true).setFlexGrow(2);
    }

    private void refreshGrid() {
        if (currentLeagueId != null) {
            double stake = unitStakeField.getValue() != null ? unitStakeField.getValue() : 100.0;
            List<LeaderboardEntryDTO> data = leaderboardService.generateLeaderboard(currentLeagueId, currentSport, stake);
            grid.setItems(data);
        }
    }

    private LeaderboardEntryDTO getTopPlayer() {
        double stake = unitStakeField.getValue() != null ? unitStakeField.getValue() : 100.0;
        List<LeaderboardEntryDTO> currentData = leaderboardService.generateLeaderboard(currentLeagueId, currentSport, stake);
        return currentData.isEmpty() ? null : currentData.get(0);
    }
}