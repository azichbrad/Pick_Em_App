package com.pickem.app.ui;

import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRepository;
import com.pickem.app.service.ConferenceService;
import com.pickem.app.service.GradingService;
import com.pickem.app.service.OddsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Route("")
@CssImport("./styles.css")
public class BoardView extends VerticalLayout {

    private final PlayerRepository playerRepo;
    private final PickRepository pickRepo;
    private final OddsService oddsService;
    private final ConferenceService conferenceService;
    private final GradingService gradingService; // 1. Add this

    // 2. Update the constructor to accept the new service
    public BoardView(PlayerRepository playerRepo, PickRepository pickRepo, OddsService oddsService, ConferenceService conferenceService, GradingService gradingService) {
        this.playerRepo = playerRepo;
        this.pickRepo = pickRepo;
        this.oddsService = oddsService;
        this.conferenceService = conferenceService;
        this.gradingService = gradingService; // 3. Assign it

        getElement().setAttribute("theme", Lumo.DARK);
        setSizeFull();
        setPadding(true);
        getStyle().set("background-color", "#0b0f19");

        renderTabs();
    }

    private void renderTabs() {
        removeAll();

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.addThemeVariants(TabSheetVariant.LUMO_TABS_CENTERED, TabSheetVariant.LUMO_BORDERED);

        tabSheet.add("🏈 College Football", createTabContent("NCAAF"));
        tabSheet.add("⚡ NFL", createTabContent("NFL"));

        add(tabSheet);
    }

    private VerticalLayout createTabContent(String sport) {
        VerticalLayout container = new VerticalLayout();
        container.setSizeFull();
        container.setPadding(false);

        HorizontalLayout controls = new HorizontalLayout();
        controls.setWidthFull();
        controls.setJustifyContentMode(JustifyContentMode.BETWEEN);
        controls.setAlignItems(Alignment.CENTER);

        ComboBox<Integer> weekSelector = new ComboBox<>("Week");
        if (sport.equals("NCAAF")) {
            weekSelector.setItems(IntStream.rangeClosed(0, 19).boxed().toList());
        } else {
            weekSelector.setItems(IntStream.rangeClosed(1, 18).boxed().toList());
        }
        weekSelector.setItemLabelGenerator(week -> formatWeekLabel(sport, week));
        weekSelector.setValue(sport.equals("NCAAF") ? 0 : 1);
        weekSelector.setWidth("220px");

        // 1. MOVE THIS UP: Create the grid FIRST so the button can see it
        VerticalLayout boardGrid = new VerticalLayout();
        boardGrid.setSizeFull();
        boardGrid.setPadding(false);

        // 2. NOW build the button
        Button testGradeBtn = new Button("Force Grade (Test)");
        testGradeBtn.getStyle().set("background-color", "#334155").set("color", "white");
        testGradeBtn.addClickListener(e -> {
            gradingService.gradePendingPicks(); // Fires the logic
            renderPlayerColumns(boardGrid, sport, weekSelector.getValue()); // Redraws the board
        });

        HorizontalLayout leftControls = new HorizontalLayout(weekSelector, testGradeBtn);
        leftControls.setAlignItems(Alignment.BASELINE);

        controls.add(leftControls);

        // 3. Initial render and listeners
        renderPlayerColumns(boardGrid, sport, weekSelector.getValue());

        weekSelector.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                renderPlayerColumns(boardGrid, sport, event.getValue());
            }
        });

        container.add(controls, boardGrid);
        return container;
    }

    private void renderPlayerColumns(VerticalLayout boardGrid, String sport, Integer selectedWeek) {
        boardGrid.removeAll();

        FormLayout board = new FormLayout();
        board.setWidthFull();
        board.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("1000px", 4)
        );

        List<Player> players = playerRepo.findAll();
        List<Pick> weeklyPicks = pickRepo.findByWeekNumberAndSport(selectedWeek, sport);

        for (Player player : players) {
            VerticalLayout playerCard = new VerticalLayout();
            playerCard.setPadding(true);
            playerCard.setSpacing(true);

            // Modern Slate Card Styling
            playerCard.getStyle()
                    .set("background-color", "#151d30")
                    .set("border", "1px solid #22304d")
                    .set("border-radius", "16px")
                    .set("box-shadow", "0 10px 25px -5px rgba(0, 0, 0, 0.3)")
                    .set("margin-bottom", "15px");

            // Player Header + Record Pill Badge
            HorizontalLayout playerHeader = new HorizontalLayout();
            playerHeader.setWidthFull();
            playerHeader.setJustifyContentMode(JustifyContentMode.BETWEEN);
            playerHeader.setAlignItems(Alignment.CENTER);

            H2 name = new H2(player.getName());
            name.getStyle().set("margin", "0").set("font-size", "1.25rem").set("font-weight", "700");

            Span recordBadge = new Span(player.getWins() + "W - " + player.getLosses() + "L - " + player.getPushes() + "P");
            recordBadge.getStyle()
                    .set("background", "rgba(59, 130, 246, 0.15)")
                    .set("color", "#60a5fa")
                    .set("font-size", "0.75rem")
                    .set("font-weight", "600")
                    .set("padding", "4px 8px")
                    .set("border-radius", "9999px")
                    .set("border", "1px solid rgba(59, 130, 246, 0.3)");

            playerHeader.add(name, recordBadge);
            playerCard.add(playerHeader);

            // Render 5 Pick Slots
            for (int slot = 1; slot <= 5; slot++) {
                int currentSlot = slot;

                Optional<Pick> existingPick = weeklyPicks.stream()
                        .filter(p -> p.getPlayer().getId().equals(player.getId()) && p.getSlotNumber() == currentSlot)
                        .findFirst();

                Button slotButton = new Button();
                slotButton.setWidthFull();
                slotButton.setHeight("50px");

                if (existingPick.isPresent()) {
                    Pick pick = existingPick.get();

                    // We use standard Divs here because they map perfectly to pure CSS flexbox
                    com.vaadin.flow.component.html.Div slotContent = new com.vaadin.flow.component.html.Div();
                    slotContent.addClassName("pick-card-wrapper");

                    com.vaadin.flow.component.html.Div topRow = new com.vaadin.flow.component.html.Div();
                    topRow.addClassName("pick-card-top");

                    if (pick.getLogoUrl() != null && !pick.getLogoUrl().isEmpty()) {
                        Image logo = new Image(pick.getLogoUrl(), "icon");
                        logo.setWidth("24px");
                        logo.setHeight("24px");
                        logo.getStyle().set("flex-shrink", "0");
                        topRow.add(logo);
                    }

                    String rawSelection = pick.getSelection();
                    String matchName;
                    String lineBadgeText;

                    if (rawSelection.contains(" O ")) {
                        String[] parts = rawSelection.split(" O ", 2);
                        matchName = parts[0];
                        lineBadgeText = "O " + (parts.length > 1 ? parts[1] : "");
                    } else if (rawSelection.contains(" U ")) {
                        String[] parts = rawSelection.split(" U ", 2);
                        matchName = parts[0];
                        lineBadgeText = "U " + (parts.length > 1 ? parts[1] : "");
                    } else {
                        int lastSpace = rawSelection.lastIndexOf(" ");
                        if (lastSpace != -1) {
                            matchName = rawSelection.substring(0, lastSpace);
                            lineBadgeText = rawSelection.substring(lastSpace + 1);
                        } else {
                            matchName = rawSelection;
                            lineBadgeText = "";
                        }
                    }

                    Span nameSpan = new Span(matchName);
                    nameSpan.addClassName("pick-card-text"); // Hands the wrapping logic over to CSS
                    topRow.add(nameSpan);

                    com.vaadin.flow.component.html.Div bottomRow = new com.vaadin.flow.component.html.Div();
                    bottomRow.addClassName("pick-card-bottom");

                    Span lineBadge = new Span(lineBadgeText);
                    lineBadge.addClassName("pick-line-badge");
                    bottomRow.add(lineBadge);

                    slotContent.add(topRow, bottomRow);
                    slotButton.setIcon(slotContent);

                    String status = pick.getStatus();
                    if ("WIN".equals(status)) {
                        slotButton.addClassName("pick-win");
                    } else if ("LOSS".equals(status)) {
                        slotButton.addClassName("pick-loss");
                    } else if ("PUSH".equals(status)) {
                        slotButton.addClassName("pick-push");
                    } else {
                        slotButton.addClassName("filled-slot-btn");
                    }
                }

                slotButton.addClickListener(event -> {
                    PickSelectionDialog dialog = new PickSelectionDialog(
                            player, currentSlot, sport, selectedWeek, oddsService, pickRepo, conferenceService,
                            () -> renderPlayerColumns(boardGrid, sport, selectedWeek)
                    );
                    dialog.open();
                });

                playerCard.add(slotButton);
            }

            board.add(playerCard);
        }

        boardGrid.add(board);
    }

    private String formatWeekLabel(String sport, Integer week) {
        if (sport.equals("NFL")) return "Week " + week;
        if (sport.equals("NCAAF")) {
            if (week <= 14) return "Week " + week;
            if (week == 15) return "Conf. Championships";
            if (week == 16) return "CFP Round 1";
            if (week == 17) return "CFP Quarterfinals";
            if (week == 18) return "CFP Semifinals";
            if (week == 19) return "National Championship";
        }
        return "Week " + week;
    }
}