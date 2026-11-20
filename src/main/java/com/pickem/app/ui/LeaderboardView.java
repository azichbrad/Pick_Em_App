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
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
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
    private final RadioButtonGroup<Double> lossRateGroup = new RadioButtonGroup<>();

    public LeaderboardView(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;

        // --- FORCE DARK MODE & APP BACKGROUND ---
        getElement().getThemeList().add(Lumo.DARK);
        getStyle().set("background-color", "#0b1120");
        getStyle().set("color", "#f8fafc");
        setSizeFull();
        setAlignItems(Alignment.CENTER);

        // --- Header & Back Button ---
        Button backBtn = new Button("Back to Dashboard", new Icon(VaadinIcon.ARROW_LEFT));
        backBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST);
        backBtn.getStyle().set("align-self", "flex-start").set("margin-top", "10px");
        backBtn.addClickListener(e -> UI.getCurrent().navigate(""));

        H2 title = new H2("Live Standings & Vegas Analytics");
        title.getStyle().set("margin-top", "0").set("color", "#38bdf8");

        // --- Sport Toggle Tabs ---
        Tab tabAll = new Tab("Overall");
        Tab tabNfl = new Tab("NFL");
        Tab tabCfb = new Tab("CFB");
        Tabs tabs = new Tabs(tabAll, tabNfl, tabCfb);
        tabs.addThemeVariants(com.vaadin.flow.component.tabs.TabsVariant.LUMO_EQUAL_WIDTH_TABS);
        tabs.getStyle()
                .set("flex", "1 1 260px")
                .set("min-width", "0")
                .set("max-width", "100%");
        tabs.addSelectedChangeListener(event -> {
            if (event.getSelectedTab().equals(tabAll)) currentSport = "ALL";
            else if (event.getSelectedTab().equals(tabNfl)) currentSport = "NFL";
            else currentSport = "NCAAF";
            refreshGrid();
        });

        // --- Interactive Bankroll Calculator ---
        unitStakeField.setMin(10.0);
        unitStakeField.setStep(10.0);
        unitStakeField.setValue(100.0);
        unitStakeField.setStepButtonsVisible(true);
        unitStakeField.setWidth("150px");
        unitStakeField.getStyle()
                .set("flex", "0 0 auto")
                .set("max-width", "100%");
        unitStakeField.addValueChangeListener(e -> refreshGrid());

        // --- Loss Penalty Rate Toggle (25¢ / 50¢ / $1) ---
        lossRateGroup.setLabel("League Penance");
        lossRateGroup.setItems(0.25, 0.50, 1.00);
        lossRateGroup.setValue(1.00);
        lossRateGroup.setItemLabelGenerator(rate -> {
            if (rate == 0.25) return "25¢";
            if (rate == 0.50) return "50¢";
            return "$1";
        });
        lossRateGroup.addThemeVariants(RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD);
        lossRateGroup.getStyle()
                .set("flex", "0 0 auto")
                .set("color", "#f8fafc");
        lossRateGroup.addValueChangeListener(e -> refreshGrid());

        HorizontalLayout calculatorsRow = new HorizontalLayout(unitStakeField, lossRateGroup);
        calculatorsRow.setAlignItems(Alignment.END);
        calculatorsRow.getStyle()
                .set("flex-wrap", "wrap")
                .set("gap", "16px")
                .set("max-width", "100%");

        HorizontalLayout controlsLayout = new HorizontalLayout(tabs, calculatorsRow);
        controlsLayout.setAlignItems(Alignment.END);
        controlsLayout.setWidthFull();
        controlsLayout.setMaxWidth("1000px");
        controlsLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        controlsLayout.getStyle()
                .set("background-color", "#151d30")
                .set("padding", "15px")
                .set("border-radius", "12px")
                .set("flex-wrap", "wrap")
                .set("gap", "12px")
                .set("box-sizing", "border-box");

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
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.getStyle().set("border-radius", "12px").set("box-shadow", "0 4px 12px rgba(0,0,0,0.5)");

        grid.addColumn(new ComponentRenderer<>(entry -> {
            Span nameSpan = new Span(entry.playerName());
            nameSpan.getStyle().set("font-weight", "bold").set("font-size", "1.1rem");

            if (entry.wins() > 0 && entry.equals(getTopPlayer())) {
                nameSpan.setText("👑 " + entry.playerName());
                nameSpan.getStyle().set("color", "#fbbf24");
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
                profitSpan.getStyle().set("color", "#4ade80").set("font-weight", "bold");
            } else if (entry.hypotheticalProfit() < 0) {
                profitSpan.getStyle().set("color", "#f87171");
            }
            return profitSpan;
        })).setHeader("Vegas P/L").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(entry -> {
            double rate = lossRateGroup.getValue() != null ? lossRateGroup.getValue() : 1.00;
            double owed = entry.losses() * rate;

            Span owedSpan = new Span(String.format("$%.2f", owed));
            owedSpan.getStyle().set("font-weight", "600");
            owedSpan.getStyle().set("color", owed > 0 ? "#fbbf24" : "#94a3b8");
            return owedSpan;
        })).setHeader("Owed").setAutoWidth(true);

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