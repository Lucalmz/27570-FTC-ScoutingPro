package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.services.DatabaseService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import net.objecthunter.exp4j.ExpressionBuilder;

public class FormulaEditController {
    @FXML private TextField formulaField;
    @FXML private Label errorLabel;

    private Stage dialogStage;
    private Competition competition;

    public void setDialogStage(Stage dialogStage, Competition competition) {
        this.dialogStage = dialogStage;
        this.competition = competition;
        formulaField.setText(competition.getRatingFormula());
    }

    @FXML
    private void handleSave() {
        String formula = formulaField.getText().trim();
        if (formula.isEmpty()) {
            errorLabel.setText("Formula cannot be empty.");
            return;
        }

        // 校验公式语法
        try {
            new ExpressionBuilder(formula)
                    .variables("auto", "teleop", "total", "seq", "climb")
                    .build();

            DatabaseService.updateCompetitionFormula(competition.getName(), formula);
            competition.setRatingFormula(formula); // 更新内存对象
            dialogStage.close();
        } catch (Exception e) {
            errorLabel.setText("Invalid syntax: " + e.getMessage());
        }
    }

    @FXML
    private void handleReset() {
        formulaField.setText("total");
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}