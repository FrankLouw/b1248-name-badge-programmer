package com.franklouw.b1248.ui;

import com.franklouw.b1248.Badge;
import com.franklouw.b1248.BadgeMessage;
import com.franklouw.b1248.BadgeWriter;
import com.franklouw.b1248.render.BadgeRenderer;
import com.franklouw.b1248.render.Bitmap;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

/**
 * Main JavaFX application: edit up to 8 messages (text/image + effect) and write
 * them to the B1248 badge.
 */
public class BadgeApp extends Application {

    private static final String[] EFFECT_NAMES =
            {"left", "right", "up", "down", "freeze", "animation", "snow", "volume"};
    private static final int SLOT_COUNT = 8;
    private static final int DISPLAY_WIDTH = 44;

    private final BadgeRenderer renderer = new BadgeRenderer();
    private final BadgeWriter writer = new BadgeWriter();
    private final MessageDraft[] drafts = new MessageDraft[SLOT_COUNT];

    private final BadgePreview preview = new BadgePreview();
    private final ListView<String> slotList = new ListView<>();
    private final TextField textField = new TextField();
    private final ComboBox<String> effectBox = new ComboBox<>();
    private final Spinner<Integer> speedSpinner = new Spinner<>(1, 8, 4);
    private final CheckBox blinkBox = new CheckBox("Blink");
    private final CheckBox frameBox = new CheckBox("Frame");
    private final Label statusLabel = new Label("Device not connected");
    private final Label overflowLabel = new Label();

    private int selectedSlot;
    private boolean loaded;

    @Override
    public void start(Stage stage) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            drafts[i] = new MessageDraft();
        }

        buildUi(stage);
        loaded = true;
        selectSlot(0);
    }

    private void buildUi(Stage stage) {
        Button writeButton = new Button("Write to badge");
        writeButton.setOnAction(e -> write());
        Button clearButton = new Button("Clear all");
        clearButton.setOnAction(e -> clearAll());
        HBox topBar = new HBox(8, writeButton, clearButton, statusLabel);
        topBar.setPadding(new Insets(8));

        for (int i = 0; i < SLOT_COUNT; i++) {
            slotList.getItems().add("Slot " + (i + 1));
        }
        slotList.setPrefWidth(110);
        slotList.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            if (n != null && n.intValue() >= 0) {
                selectSlot(n.intValue());
            }
        });

        Button imageButton = new Button("Load image...");
        imageButton.setOnAction(e -> loadImage(stage));
        HBox textRow = new HBox(8, textField, imageButton);
        HBox.setHgrow(textField, Priority.ALWAYS);

        effectBox.getItems().addAll(EFFECT_NAMES);
        effectBox.getSelectionModel().select(4); // freeze

        HBox controls = new HBox(8,
                new Label("Effect"), effectBox,
                new Label("Speed"), speedSpinner,
                blinkBox, frameBox);

        VBox editor = new VBox(8,
                new Label("Text (or load an image):"),
                textRow,
                controls,
                preview,
                overflowLabel);
        editor.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(slotList);
        root.setCenter(editor);

        wireListeners();

        Scene scene = new Scene(root, 900, 420);
        stage.setTitle("B1248 Name Badge Programmer");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> writer.shutdown());
        stage.show();
    }

    private void wireListeners() {
        textField.textProperty().addListener((obs, o, n) -> {
            if (!loaded) {
                return;
            }
            drafts[selectedSlot].text = n;
            drafts[selectedSlot].image = null;
            refreshPreview();
        });
        effectBox.valueProperty().addListener((obs, o, n) -> {
            if (!loaded) {
                return;
            }
            drafts[selectedSlot].effect = effectBox.getSelectionModel().getSelectedIndex();
            refreshPreview();
        });
        speedSpinner.valueProperty().addListener((obs, o, n) -> {
            if (loaded) {
                drafts[selectedSlot].speed = n;
            }
        });
        blinkBox.selectedProperty().addListener((obs, o, n) -> {
            if (loaded) {
                drafts[selectedSlot].blink = n;
            }
        });
        frameBox.selectedProperty().addListener((obs, o, n) -> {
            if (loaded) {
                drafts[selectedSlot].frame = n;
            }
        });
    }

    private void selectSlot(int slot) {
        selectedSlot = slot;
        MessageDraft d = drafts[slot];
        loaded = false;
        textField.setText(d.text);
        effectBox.getSelectionModel().select(d.effect);
        speedSpinner.getValueFactory().setValue(d.speed);
        blinkBox.setSelected(d.blink);
        frameBox.setSelected(d.frame);
        loaded = true;
        refreshPreview();
    }

    private void refreshPreview() {
        MessageDraft d = drafts[selectedSlot];
        Bitmap b = null;
        if (d.image != null) {
            b = renderer.renderImage(d.image);
        } else if (d.text != null && !d.text.isBlank()) {
            b = renderer.renderText(d.text);
        }
        preview.setBitmap(b);
        if (b != null && b.widthPx() > DISPLAY_WIDTH) {
            String behavior = d.effect == 4 ? "be clipped" : "scroll";
            overflowLabel.setText("Message is " + b.widthPx() + " px wide and will " + behavior + ".");
        } else {
            overflowLabel.setText("");
        }
    }

    private void loadImage(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load image");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            drafts[selectedSlot].image = ImageIO.read(file);
            drafts[selectedSlot].text = "";
            textField.setText("");
            refreshPreview();
        } catch (IOException ex) {
            statusLabel.setText("Failed to load image: " + ex.getMessage());
        }
    }

    private void clearAll() {
        for (MessageDraft d : drafts) {
            d.text = "";
            d.image = null;
        }
        loaded = false;
        textField.setText("");
        loaded = true;
        refreshPreview();
        statusLabel.setText("Cleared");
    }

    private void write() {
        Badge badge = buildBadge();
        if (badge.isEmpty()) {
            statusLabel.setText("Nothing to write");
            return;
        }
        if (!badge.isWithinLimit()) {
            statusLabel.setText("Too much content: " + badge.totalColumns() + " columns (max "
                    + BadgeWriter.MAX_TOTAL_COLUMNS + ")");
            return;
        }
        try {
            if (!writer.open()) {
                statusLabel.setText("Badge (0483:5750) not found");
                return;
            }
            writer.write(badge);
            statusLabel.setText("Wrote " + badge.nonEmptyMessages().size() + " message(s)");
        } catch (Exception ex) {
            statusLabel.setText("Write failed: " + ex.getMessage());
        }
    }

    private Badge buildBadge() {
        Badge badge = new Badge();
        for (int i = 0; i < SLOT_COUNT; i++) {
            MessageDraft d = drafts[i];
            if (!d.hasContent()) {
                continue;
            }
            Bitmap b = d.image != null ? renderer.renderImage(d.image) : renderer.renderText(d.text);
            badge.set(i, new BadgeMessage(b.data(), b.cols(), d.effect, d.speed, d.blink, d.frame));
        }
        return badge;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
