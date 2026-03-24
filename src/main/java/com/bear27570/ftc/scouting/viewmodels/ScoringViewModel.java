// File: src/main/java/com/bear27570/ftc/scouting/viewmodels/ScoringViewModel.java
package com.bear27570.ftc.scouting.viewmodels;

import com.bear27570.ftc.scouting.utils.FxThread;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ScoringViewModel {
    private final StringProperty statusText = new SimpleStringProperty("");
    private final StringProperty statusColor = new SimpleStringProperty("#FFFFFF");
    private final StringProperty errorText = new SimpleStringProperty("");

    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty statusColorProperty() { return statusColor; }
    public StringProperty errorTextProperty() { return errorText; }

    public void setStatus(String text, String colorHex) {
        FxThread.run(() -> {
            statusText.set(text);
            statusColor.set(colorHex);
            errorText.set("");
        });
    }

    public void setError(String text) {
        FxThread.run(() -> {
            errorText.set(text.startsWith("❌") ? text : "❌ " + text);
            statusText.set("");
        });
    }

    public void clear() {
        FxThread.run(() -> {
            statusText.set("");
            errorText.set("");
        });
    }
}