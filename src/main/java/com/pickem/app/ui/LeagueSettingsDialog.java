package com.pickem.app.ui;

import com.pickem.app.model.League;
import com.pickem.app.repository.LeagueRepository;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

public class LeagueSettingsDialog extends Dialog {

    public LeagueSettingsDialog(League league, LeagueRepository leagueRepo) {
        setHeaderTitle("⚙️ League Settings");

        // 1. Invite Code (Read-Only)
        TextField inviteCodeField = new TextField("Invite Code");
        inviteCodeField.setValue(league.getInviteCode() != null ? league.getInviteCode() : "N/A");
        inviteCodeField.setReadOnly(true);
        inviteCodeField.setWidthFull();

        // 2. Weekly Pick Limit
        IntegerField pickLimitField = new IntegerField("Weekly Pick Limit");
        pickLimitField.setMin(1);
        pickLimitField.setMax(10);
        pickLimitField.setStepButtonsVisible(true);
        // Safely check for null and default to 5 if empty
        pickLimitField.setValue(league.getPicksPerWeek() != null ? league.getPicksPerWeek() : 5);
        pickLimitField.setWidthFull();

        // 3. Group Parlay Toggle
        Checkbox parlayCheckbox = new Checkbox("Enable Group Parlays");
        // Safely check for null and default to false if empty
        parlayCheckbox.setValue(league.getEnableGroupParlay() != null ? league.getEnableGroupParlay() : false);

        // 4. Save Button
        Button saveBtn = new Button("Save Settings", e -> {
            league.setPicksPerWeek(pickLimitField.getValue());
            league.setEnableGroupParlay(parlayCheckbox.getValue());

            leagueRepo.save(league);

            Notification.show("League settings updated successfully!", 3000, Notification.Position.TOP_CENTER);
            close();
            UI.getCurrent().getPage().reload(); // Refresh the board to apply new rules
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> close());

        VerticalLayout layout = new VerticalLayout(inviteCodeField, pickLimitField, parlayCheckbox);
        layout.setPadding(false);
        add(layout);

        getFooter().add(cancelBtn, saveBtn);
    }
}