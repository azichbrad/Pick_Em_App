package com.pickem.app.ui;

import com.pickem.app.model.Pick;
import com.pickem.app.repository.PickRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;

public class AdminPickEditDialog extends Dialog {

    public AdminPickEditDialog(Pick pick, PickRepository pickRepo, Runnable onSave, Runnable onSwapPick) {
        getElement().setAttribute("theme", "dark");
        setHeaderTitle("Admin Override: " + pick.getPlayer().getName() + " (Slot " + pick.getSlotNumber() + ")");
        setWidth("400px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        // 1. Edit the Display String (What players see on the board)
        TextField selectionField = new TextField("Display Text");
        selectionField.setValue(pick.getSelection() != null ? pick.getSelection() : "");
        selectionField.setWidthFull();
        selectionField.setHelperText("e.g. 'Oklahoma State -2.5'");

        // 2. Edit the raw math line (What the grading engine uses)
        NumberField lockedPointField = new NumberField("Backend Grading Line");
        lockedPointField.setValue(pick.getLockedPoint());
        lockedPointField.setStep(0.5);
        lockedPointField.setWidthFull();

        // 3. Edit Status (Manual grading override)
        ComboBox<String> statusBox = new ComboBox<>("Game Status");
        statusBox.setItems("PENDING", "WIN", "LOSS", "PUSH");
        statusBox.setValue(pick.getStatus() != null ? pick.getStatus() : "PENDING");
        statusBox.setWidthFull();

        layout.add(selectionField, lockedPointField, statusBox);
        add(layout);

        Button saveBtn = new Button("Force Override", e -> {
            String newDisplayText = selectionField.getValue();
            Double newLockedPoint = lockedPointField.getValue();

            // Extract the team name by removing the old odds from the end of the string
            String teamName = pick.getSelection();
            int lastSpace = teamName.lastIndexOf(" ");
            if (lastSpace != -1) {
                teamName = teamName.substring(0, lastSpace).trim();
            }

            // Reconstruct the selection string with the updated odds
            String updatedSelection = teamName + " " + (newLockedPoint > 0 ? "+" : "") + newLockedPoint;

            // Only update if the user modified the selection field manually, otherwise use the reconstructed string
            if (!newDisplayText.equals(pick.getSelection())) {
                pick.setSelection(newDisplayText);
            } else {
                pick.setSelection(updatedSelection);
            }

            pick.setLockedPoint(newLockedPoint);
            pick.setStatus(statusBox.getValue());

            pickRepo.save(pick);
            onSave.run();
            close();
        });

        // Style it red so you know you are wielding commissioner powers
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancelBtn = new Button("Cancel", e -> close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        getFooter().add(cancelBtn, saveBtn);

        Button swapBtn = new Button("Swap Matchup", e -> {
            close(); // Close the admin modal
            onSwapPick.run(); // Launch the standard odds board
        });
        swapBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        getFooter().add(cancelBtn, swapBtn, saveBtn);
    }
}
