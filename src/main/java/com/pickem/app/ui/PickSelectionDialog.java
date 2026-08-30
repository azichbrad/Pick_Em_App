package com.pickem.app.ui;

import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.dto.TeamDTO;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.service.ConferenceService;
import com.pickem.app.service.OddsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PickSelectionDialog extends Dialog {

    private final VerticalLayout gameListContainer = new VerticalLayout();
    private final List<GameOddsDTO> games;
    private final Map<String, TeamDTO> teamDataMap;
    private static final String TOTALS_ICON = "https://cdn-icons-png.flaticon.com/512/1199/1199155.png";

    // Formatter for the game times (Pacific Time)
    private final DateTimeFormatter timeFormatter = DateTimeFormatter
            .ofPattern("EEE, MMM d • h:mm a")
            .withZone(ZoneId.of("America/Los_Angeles"));

    public PickSelectionDialog(
            Player player, int slotNumber, String sport, int weekNumber,
            OddsService oddsService, PickRepository pickRepo, ConferenceService conferenceService, Runnable onPickSaved
    ) {
        setHeaderTitle("Select Pick for " + player.getName() + " (Slot " + slotNumber + ")");
        setWidth("550px");
        setHeight("700px");

        games = sport.equals("NCAAF") ? oddsService.getCollegeFootballOdds() : oddsService.getNflOdds();
        this.teamDataMap = sport.equals("NCAAF") ? conferenceService.getTeamDataMap() : Map.of();

        ComboBox<String> conferenceFilter = new ComboBox<>("Filter by Conference");
        conferenceFilter.setWidthFull();

        if (sport.equals("NCAAF")) {
            List<String> dynamicConferences = new ArrayList<>();
            dynamicConferences.add("All");
            dynamicConferences.addAll(teamDataMap.values().stream().map(TeamDTO::conference).distinct().sorted().toList());
            conferenceFilter.setItems(dynamicConferences);
        } else {
            conferenceFilter.setItems("All", "AFC", "NFC");
        }

        conferenceFilter.setValue("All");

        conferenceFilter.addValueChangeListener(event -> {
            renderGames(event.getValue(), player, slotNumber, sport, weekNumber, pickRepo, onPickSaved);
        });

        gameListContainer.setSizeFull();
        gameListContainer.getStyle().set("overflow-y", "auto");

        renderGames("All", player, slotNumber, sport, weekNumber, pickRepo, onPickSaved);

        add(conferenceFilter, gameListContainer);
    }

    private void renderGames(
            String selectedConference, Player player, int slotNumber, String sport,
            int weekNumber, PickRepository pickRepo, Runnable onPickSaved
    ) {
        gameListContainer.removeAll();

        if (games == null || games.isEmpty()) {
            gameListContainer.add(new Span("No upcoming games found."));
            return;
        }

        for (GameOddsDTO game : games) {
            if (!selectedConference.equals("All") && sport.equals("NCAAF")) {
                String homeConf = getConference(game.homeTeam());
                String awayConf = getConference(game.awayTeam());

                if (!selectedConference.equals(homeConf) && !selectedConference.equals(awayConf)) {
                    continue;
                }
            }

            Optional<GameOddsDTO.MarketDTO> spreadMarket = game.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .filter(m -> "spreads".equalsIgnoreCase(m.key()))
                    .findFirst();

            Optional<GameOddsDTO.MarketDTO> totalsMarket = game.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .filter(m -> "totals".equalsIgnoreCase(m.key()))
                    .findFirst();

            if (spreadMarket.isPresent() || totalsMarket.isPresent()) {

                GameOddsDTO.OutcomeDTO awayOutcome;
                GameOddsDTO.OutcomeDTO homeOutcome;

                if (spreadMarket.isPresent()) {
                    List<GameOddsDTO.OutcomeDTO> outcomes = spreadMarket.get().outcomes();
                    awayOutcome = outcomes.stream().filter(o -> o.name().equals(game.awayTeam())).findFirst().orElse(null);
                    homeOutcome = outcomes.stream().filter(o -> o.name().equals(game.homeTeam())).findFirst().orElse(null);
                } else {
                    homeOutcome = null;
                    awayOutcome = null;
                }

                GameOddsDTO.OutcomeDTO overOutcome;
                GameOddsDTO.OutcomeDTO underOutcome;

                if (totalsMarket.isPresent()) {
                    List<GameOddsDTO.OutcomeDTO> totalOutcomes = totalsMarket.get().outcomes();
                    overOutcome = totalOutcomes.stream().filter(o -> "Over".equalsIgnoreCase(o.name())).findFirst().orElse(null);
                    underOutcome = totalOutcomes.stream().filter(o -> "Under".equalsIgnoreCase(o.name())).findFirst().orElse(null);
                } else {
                    underOutcome = null;
                    overOutcome = null;
                }

                VerticalLayout gameCard = new VerticalLayout();
                gameCard.getStyle().set("background-color", "#151d30");
                gameCard.getStyle().set("border", "1px solid #22304d");
                gameCard.getStyle().set("border-radius", "12px");
                gameCard.getStyle().set("padding", "14px");
                gameCard.getStyle().set("margin-bottom", "12px");
                gameCard.setSpacing(false);

                String matchNameStr = game.awayTeam() + " @ " + game.homeTeam();

                // --- START TIME HEADER ---
                HorizontalLayout timeRow = new HorizontalLayout();
                timeRow.setWidthFull();
                timeRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
                timeRow.getStyle().set("margin-bottom", "8px");

                String timeString = game.commenceTime() != null ? timeFormatter.format(game.commenceTime()) + " PT" : "TBD";
                Span timeSpan = new Span(timeString);

                // FIX: Force Light Gray Text
                timeSpan.getStyle().set("font-size", "0.85em").set("color", "#94a3b8");
                timeRow.add(timeSpan);

                // --- AWAY TEAM ROW ---
                HorizontalLayout awayRow = new HorizontalLayout();
                awayRow.setWidthFull();
                awayRow.setAlignItems(Alignment.CENTER);

                Image awayLogo = new Image(getLogoUrl(game.awayTeam(), sport), "logo");
                awayLogo.setWidth("30px");
                awayLogo.setHeight("30px");

                Span awayName = new Span(game.awayTeam());
                // FIX: Force White Text
                awayName.getStyle().set("font-weight", "500").set("color", "#f8fafc");

                Button awayBtn = new Button();
                if (awayOutcome != null && awayOutcome.point() != null) {
                    String pointStr = awayOutcome.point() > 0 ? "+" + awayOutcome.point() : String.valueOf(awayOutcome.point());
                    awayBtn.setText(pointStr);
                    String selectionStr = game.awayTeam() + " " + pointStr;

                    // NEW: Pass Game ID and Locked Point
                    awayBtn.addClickListener(e -> savePick(player, slotNumber, sport, weekNumber, selectionStr, getLogoUrl(game.awayTeam(), sport), game.id(), awayOutcome.point(), matchNameStr, pickRepo, onPickSaved));
                } else {
                    awayBtn.setText("N/A"); awayBtn.setEnabled(false);
                }
                awayBtn.setWidth("80px");
                awayBtn.addClassName("odds-btn");

                Button overBtn = new Button();
                if (overOutcome != null && overOutcome.point() != null) {
                    String pointStr = "O " + overOutcome.point();
                    overBtn.setText(pointStr);
                    String selectionStr = matchNameStr + " " + pointStr;

                    // NEW: Pass Game ID and Locked Point
                    overBtn.addClickListener(e -> savePick(
                            player, slotNumber, sport, weekNumber, selectionStr,
                            TOTALS_ICON, // Use neutral icon for totals
                            game.id(), overOutcome.point(), matchNameStr, pickRepo, onPickSaved
                    ));
                } else {
                    overBtn.setText("N/A"); overBtn.setEnabled(false);
                }
                overBtn.setWidth("80px");
                overBtn.addClassName("odds-btn");

                awayRow.add(awayLogo, awayName, awayBtn, overBtn);
                awayRow.expand(awayName);

                // --- HOME TEAM ROW ---
                HorizontalLayout homeRow = new HorizontalLayout();
                homeRow.setWidthFull();
                homeRow.setAlignItems(Alignment.CENTER);
                homeRow.getStyle().set("margin-top", "8px");

                Image homeLogo = new Image(getLogoUrl(game.homeTeam(), sport), "logo");
                homeLogo.setWidth("30px");
                homeLogo.setHeight("30px");

                Span homeName = new Span(game.homeTeam());
                // FIX: Force White Text
                homeName.getStyle().set("font-weight", "500").set("color", "#f8fafc");

                Button homeBtn = new Button();
                if (homeOutcome != null && homeOutcome.point() != null) {
                    String pointStr = homeOutcome.point() > 0 ? "+" + homeOutcome.point() : String.valueOf(homeOutcome.point());
                    homeBtn.setText(pointStr);
                    String selectionStr = game.homeTeam() + " " + pointStr;

                    // NEW: Pass Game ID and Locked Point
                    homeBtn.addClickListener(e -> savePick(player, slotNumber, sport, weekNumber, selectionStr, getLogoUrl(game.homeTeam(), sport), game.id(), homeOutcome.point(), matchNameStr, pickRepo, onPickSaved));
                } else {
                    homeBtn.setText("N/A"); homeBtn.setEnabled(false);
                }
                homeBtn.setWidth("80px");
                homeBtn.addClassName("odds-btn");

                Button underBtn = new Button();
                if (underOutcome != null && underOutcome.point() != null) {
                    String pointStr = "U " + underOutcome.point();
                    underBtn.setText(pointStr);
                    String selectionStr = matchNameStr + " " + pointStr;

                    // NEW: Pass Game ID and Locked Point
                    underBtn.addClickListener(e -> savePick(
                            player, slotNumber, sport, weekNumber, selectionStr,
                            TOTALS_ICON, // Use neutral icon for totals
                            game.id(), underOutcome.point(), matchNameStr, pickRepo, onPickSaved
                    ));
                } else {
                    underBtn.setText("N/A"); underBtn.setEnabled(false);
                }
                underBtn.setWidth("80px");
                underBtn.addClassName("odds-btn");

                homeRow.add(homeLogo, homeName, homeBtn, underBtn);
                homeRow.expand(homeName);

                gameCard.add(timeRow, awayRow, homeRow);
                gameListContainer.add(gameCard);
            }
        }
    }

    private void savePick(Player player, int slotNumber, String sport, int weekNumber, String selection, String logoUrl, String gameId, Double lockedPoint, String matchName, PickRepository pickRepo, Runnable onPickSaved) {
        Pick pick = pickRepo.findByPlayerIdAndWeekNumberAndSlotNumber(player.getId(), weekNumber, slotNumber).orElse(new Pick());
        pick.setPlayer(player);
        pick.setSlotNumber(slotNumber);
        pick.setWeekNumber(weekNumber);
        pick.setSport(sport);
        pick.setSelection(selection);
        pick.setLogoUrl(logoUrl);

        // --- NEW LOCKED FIELDS ---
        pick.setGameId(gameId);
        pick.setLockedPoint(lockedPoint);
        pick.setMatchName(matchName);

        pick.setStatus("PENDING");
        pickRepo.save(pick);
        close();
        onPickSaved.run();
    }

    private TeamDTO getTeamData(String teamName) {
        if (teamDataMap.isEmpty()) return null;

        TeamDTO bestMatch = null;
        int maxMatchLength = 0;

        for (Map.Entry<String, TeamDTO> entry : teamDataMap.entrySet()) {
            String cfbdTeamName = entry.getKey();

            // Check if the Odds API team name contains the CFBD school name
            if (teamName.contains(cfbdTeamName)) {
                // If it's a match, make sure it's the LONGEST match we've found so far
                if (cfbdTeamName.length() > maxMatchLength) {
                    maxMatchLength = cfbdTeamName.length();
                    bestMatch = entry.getValue();
                }
            }
        }

        return bestMatch;
    }

    private String getConference(String teamName) {
        TeamDTO teamData = getTeamData(teamName);
        return teamData != null ? teamData.conference() : "Other";
    }

    // UPDATED to accept sport and pull from NFL map if necessary
    private String getLogoUrl(String teamName, String sport) {
        if (sport.equals("NFL")) {
            return getNflLogo(teamName);
        }

        TeamDTO teamData = getTeamData(teamName);
        if (teamData != null && teamData.logos() != null && !teamData.logos().isEmpty()) {
            return teamData.logos().get(0);
        }
        return "[https://cdn-icons-png.flaticon.com/512/1199/1199155.png](https://cdn-icons-png.flaticon.com/512/1199/1199155.png)";
    }

    // --- NFL LOGO MAP ---
    private String getNflLogo(String teamName) {
        Map<String, String> nflLogos = new HashMap<>();

        // You can expand this by searching for the team's 3-letter abbreviation on ESPN
        // Format: [https://a.espncdn.com/i/teamlogos/nfl/500/](https://a.espncdn.com/i/teamlogos/nfl/500/)[ABBREVIATION].png
        nflLogos.put("Pittsburgh Steelers", "[https://a.espncdn.com/i/teamlogos/nfl/500/pit.png](https://a.espncdn.com/i/teamlogos/nfl/500/pit.png)");
        nflLogos.put("Kansas City Chiefs", "[https://a.espncdn.com/i/teamlogos/nfl/500/kc.png](https://a.espncdn.com/i/teamlogos/nfl/500/kc.png)");
        nflLogos.put("San Francisco 49ers", "[https://a.espncdn.com/i/teamlogos/nfl/500/sf.png](https://a.espncdn.com/i/teamlogos/nfl/500/sf.png)");
        nflLogos.put("Baltimore Ravens", "[https://a.espncdn.com/i/teamlogos/nfl/500/bal.png](https://a.espncdn.com/i/teamlogos/nfl/500/bal.png)");
        nflLogos.put("Los Angeles Rams", "[https://a.espncdn.com/i/teamlogos/nfl/500/lar.png](https://a.espncdn.com/i/teamlogos/nfl/500/lar.png)");
        nflLogos.put("Los Angeles Chargers", "[https://a.espncdn.com/i/teamlogos/nfl/500/lac.png](https://a.espncdn.com/i/teamlogos/nfl/500/lac.png)");

        return nflLogos.getOrDefault(teamName, "[https://cdn-icons-png.flaticon.com/512/1199/1199155.png](https://cdn-icons-png.flaticon.com/512/1199/1199155.png)");
    }
}