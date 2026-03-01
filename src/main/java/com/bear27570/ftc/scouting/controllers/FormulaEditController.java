package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
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
    private CompetitionRepository competitionRepository;

    // --- 核心注入方法 ---
    public void setDependencies(Stage dialogStage, Competition competition, CompetitionRepository competitionRepository) {
        this.dialogStage = dialogStage;
        this.competition = competition;
        this.competitionRepository = competitionRepository;
        formulaField.setText(competition.getRatingFormula());
    }

    @FXML
    private void handleSave() {
        String formula = formulaField.getText().trim();
        if (formula.isEmpty()) {
            errorLabel.setText("Formula cannot be empty.");
            return;
        }

        try {
            new ExpressionBuilder(formula)
                    .variables("auto", "teleop", "total", "seq", "climb")
                    .build();

            // --- 使用注入的 Repo ---
            competitionRepository.updateFormula(competition.getName(), formula);
            competition.setRatingFormula(formula);
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