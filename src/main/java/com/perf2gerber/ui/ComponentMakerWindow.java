package com.perf2gerber.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.perf2gerber.model.CustomComponent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class ComponentMakerWindow extends Stage {
    
    private final int GRID_COLS = 15;
    private final int GRID_ROWS = 15;
    private final double SPACING_MM = 2.54;
    private final double ZOOM = 15.0;
    private final double PADDING = 20.0;
    
    private Canvas canvas;
    private List<CustomComponent.ShapeDef> shapes = new ArrayList<>();
    
    private Double dragStartX = null;
    private Double dragStartY = null;
    private CustomComponent.ShapeDef currentShape = null;

    private ToggleButton btnRect;
    private MenuButton btnCircle;
    private String currentShapeType = "RECTANGLE";
    private TextField txtName;
    private ColorPicker colorPicker;
    
    private java.util.Stack<List<CustomComponent.ShapeDef>> undoStack = new java.util.Stack<>();
    
    private Runnable onComponentSaved;

    public ComponentMakerWindow(Runnable onComponentSaved) {
        this.onComponentSaved = onComponentSaved;
        setTitle("Component Maker");
        initModality(Modality.APPLICATION_MODAL);
        
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #2B2B2B;");
        
        // Toolbar
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        
        btnRect = new ToggleButton("Box");
        btnRect.setSelected(true);
        btnRect.setOnAction(e -> {
            btnRect.setSelected(true);
            currentShapeType = "RECTANGLE";
        });
        
        btnCircle = new MenuButton("Circle");
        String[] circleTypes = {"Circle", "Semicircle (Top)", "Semicircle (Bottom)", "Semicircle (Left)", "Semicircle (Right)"};
        String[] typeKeys = {"CIRCLE", "SEMICIRCLE_TOP", "SEMICIRCLE_BOTTOM", "SEMICIRCLE_LEFT", "SEMICIRCLE_RIGHT"};
        for (int i = 0; i < circleTypes.length; i++) {
            String lbl = circleTypes[i];
            String t = typeKeys[i];
            MenuItem item = new MenuItem(lbl);
            item.setOnAction(e -> {
                btnRect.setSelected(false);
                btnCircle.setText(lbl);
                currentShapeType = t;
            });
            btnCircle.getItems().add(item);
        }
        
        colorPicker = new ColorPicker(Color.web("#5C8A5C"));
        colorPicker.setOnAction(e -> draw());
        
        Button btnClear = new Button("Clear");
        btnClear.setOnAction(e -> {
            saveState();
            shapes.clear();
            draw();
        });
        
        ComboBox<String> cbEdit = new ComboBox<>();
        cbEdit.setPromptText("Edit Part...");
        cbEdit.setStyle("-fx-background-color: #3C3F41; -fx-text-fill: white;");
        cbEdit.setOnAction(e -> {
            String toEdit = cbEdit.getValue();
            if (toEdit != null) {
                loadComponentForEdit(toEdit);
            }
        });
        
        Button btnDelete = new Button("Delete");
        btnDelete.setStyle("-fx-background-color: #E74C3C; -fx-text-fill: white;");
        btnDelete.setOnAction(e -> {
            String toDelete = cbEdit.getValue();
            if (toDelete != null) {
                deleteComponent(toDelete);
                cbEdit.getItems().remove(toDelete);
                cbEdit.setValue(null);
                shapes.clear();
                txtName.setText("");
                draw();
            }
        });
        
        refreshDeleteCombo(cbEdit);
        
        Label lblSep = new Label(" | ");
        lblSep.setTextFill(Color.WHITE);
        toolbar.getChildren().addAll(btnRect, btnCircle, colorPicker, btnClear, lblSep, cbEdit, btnDelete);
        
        // Bottom bar
        HBox bottom = new HBox(10);
        bottom.setPadding(new Insets(10));
        bottom.setStyle("-fx-background-color: #1E1E1E;");
        
        Label lblName = new Label("Name:");
        lblName.setTextFill(Color.WHITE);
        txtName = new TextField();
        txtName.setPromptText("Ex: Trimpot 3-pin");
        
        Button btnSave = new Button("Save to Library");
        btnSave.setStyle("-fx-background-color: #5C8A5C; -fx-text-fill: white;");
        btnSave.setOnAction(e -> saveComponent());
        
        bottom.getChildren().addAll(lblName, txtName, btnSave);
        
        // Canvas
        double w = PADDING * 2 + (GRID_COLS - 1) * SPACING_MM * ZOOM;
        double h = PADDING * 2 + (GRID_ROWS - 1) * SPACING_MM * ZOOM;
        canvas = new Canvas(w, h);
        
        setupMouseEvents();
        
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setStyle("-fx-background: #1E1E1E;");
        
        root.setTop(toolbar);
        root.setCenter(scrollPane);
        root.setBottom(bottom);
        
        setScene(new Scene(root, 600, 600));
        getScene().setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == javafx.scene.input.KeyCode.Z) {
                undo();
            }
        });
        draw();
    }
    
    private void saveState() {
        List<CustomComponent.ShapeDef> clone = new java.util.ArrayList<>();
        for (CustomComponent.ShapeDef s : shapes) {
            clone.add(new CustomComponent.ShapeDef(s.offsetX, s.offsetY, s.width, s.height, s.type, s.color));
        }
        undoStack.push(clone);
    }
    
    private void undo() {
        if (!undoStack.isEmpty()) {
            shapes = undoStack.pop();
            draw();
        }
    }
    
    private void setupMouseEvents() {
        canvas.setOnMousePressed(e -> {
            double gridX = getGridX(e.getX());
            double gridY = getGridY(e.getY());
            
            saveState();
            
            dragStartX = gridX;
            dragStartY = gridY;
            
            String hex = String.format("#%02X%02X%02X", 
                (int)(colorPicker.getValue().getRed()*255),
                (int)(colorPicker.getValue().getGreen()*255),
                (int)(colorPicker.getValue().getBlue()*255));
            currentShape = new CustomComponent.ShapeDef(gridX, gridY, 0, 0, currentShapeType, hex);
            shapes.add(currentShape);
            draw();
        });
        
        canvas.setOnMouseDragged(e -> {
            if (currentShape != null) {
                double gridX = getGridX(e.getX());
                double gridY = getGridY(e.getY());
                
                double minX = Math.min(dragStartX, gridX);
                double minY = Math.min(dragStartY, gridY);
                double maxX = Math.max(dragStartX, gridX);
                double maxY = Math.max(dragStartY, gridY);
                
                currentShape.offsetX = minX;
                currentShape.offsetY = minY;
                currentShape.width = maxX - minX;
                currentShape.height = maxY - minY;
                draw();
            }
        });
        
        canvas.setOnMouseReleased(e -> {
            if (currentShape != null && currentShape.width == 0 && currentShape.height == 0) {
                shapes.remove(currentShape);
                if (!undoStack.isEmpty()) undoStack.pop();
                draw();
            }
            currentShape = null;
            dragStartX = null;
            dragStartY = null;
        });
    }
    
    private double getGridX(double screenX) {
        double phys = (screenX - PADDING) / ZOOM;
        double grid = phys / SPACING_MM;
        return Math.round(grid * 2.0) / 2.0;
    }
    
    private double getGridY(double screenY) {
        double phys = (screenY - PADDING) / ZOOM;
        double grid = ((canvas.getHeight() - PADDING - screenY) / ZOOM) / SPACING_MM;
        return Math.round(grid * 2.0) / 2.0;
    }
    
    private double getScreenX(double gridX) {
        return PADDING + gridX * SPACING_MM * ZOOM;
    }
    
    private double getScreenY(double gridY) {
        return canvas.getHeight() - PADDING - gridY * SPACING_MM * ZOOM;
    }
    
    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#1E1E1E"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        // Draw grid as inactive pads
        gc.setFill(Color.web("#4A3C13"));
        double copperRadius = 2.0 * ZOOM / 2.0;
        double holeRadius = 1.0 * ZOOM / 2.0;
        for (int x = 0; x < GRID_COLS; x++) {
            for (int y = 0; y < GRID_ROWS; y++) {
                double sx = getScreenX(x);
                double sy = getScreenY(y);
                gc.fillOval(sx - copperRadius, sy - copperRadius, copperRadius*2, copperRadius*2);
                
                // inner hole
                gc.setFill(Color.web("#222222"));
                gc.fillOval(sx - holeRadius, sy - holeRadius, holeRadius*2, holeRadius*2);
                gc.setFill(Color.web("#4A3C13"));
            }
        }
        
        // Draw origin (0,0)
        double ox = getScreenX(0);
        double oy = getScreenY(0);
        gc.setStroke(Color.RED);
        gc.strokeLine(ox - 10, oy, ox + 10, oy);
        gc.strokeLine(ox, oy - 10, ox, oy + 10);
        
        // Draw shapes
        for (CustomComponent.ShapeDef shape : shapes) {
            double sx = getScreenX(shape.offsetX);
            double sy = getScreenY(shape.offsetY);
            double sw = shape.width * SPACING_MM * ZOOM;
            double sh = shape.height * SPACING_MM * ZOOM;
            
            Color shapeColor = Color.web(shape.color != null ? shape.color : "#5C8A5C");
            gc.setFill(new Color(shapeColor.getRed(), shapeColor.getGreen(), shapeColor.getBlue(), 0.4));
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2.0);
            
            String t = shape.type != null ? shape.type : "RECTANGLE";
            if (t.equals("RECTANGLE")) {
                gc.fillRect(sx, sy - sh, sw, sh);
                gc.strokeRect(sx, sy - sh, sw, sh);
            } else if (t.equals("CIRCLE")) {
                gc.fillOval(sx, sy - sh, sw, sh);
                gc.strokeOval(sx, sy - sh, sw, sh);
            } else if (t.startsWith("SEMICIRCLE")) {
                double angle = 0;
                if (t.equals("SEMICIRCLE_TOP")) angle = 0;
                else if (t.equals("SEMICIRCLE_BOTTOM")) angle = 180;
                else if (t.equals("SEMICIRCLE_LEFT")) angle = 90;
                else if (t.equals("SEMICIRCLE_RIGHT")) angle = 270;
                
                gc.fillArc(sx, sy - sh, sw, sh, angle, 180, javafx.scene.shape.ArcType.CHORD);
                gc.strokeArc(sx, sy - sh, sw, sh, angle, 180, javafx.scene.shape.ArcType.CHORD);
            }
        }
    }
    
    private void saveComponent() {
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Please enter a name for the component.");
            alert.show();
            return;
        }
        
        CustomComponent cc = new CustomComponent();
        cc.setType("Custom");
        cc.setName(name);
        
        Color c = colorPicker.getValue();
        String hex = String.format("#%02X%02X%02X",
            (int)(c.getRed() * 255),
            (int)(c.getGreen() * 255),
            (int)(c.getBlue() * 255));
        cc.setColor(hex);
        
        // Calculate bounding box center to align component on cursor when placing
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (CustomComponent.ShapeDef s : shapes) {
            minX = Math.min(minX, s.offsetX);
            minY = Math.min(minY, s.offsetY);
            maxX = Math.max(maxX, s.offsetX + s.width);
            maxY = Math.max(maxY, s.offsetY + s.height);
        }
        
        double cx = 0, cy = 0;
        if (!shapes.isEmpty()) {
            cx = (minX + maxX) / 2.0;
            cy = (minY + maxY) / 2.0;
        }
        
        // Copy lists with center offset applied
        for(CustomComponent.ShapeDef s : shapes) {
            cc.getShapes().add(new CustomComponent.ShapeDef(s.offsetX - cx, s.offsetY - cy, s.width, s.height, s.type, s.color));
        }
        
        try {
            File dir = new File(System.getProperty("user.home"), "Documents/Perf2Gerber_Projects");
            if (!dir.exists()) dir.mkdirs();
            File libFile = new File(dir, "custom_parts.json");
            
            List<CustomComponent> library = loadLibrary();
            
            // Remove old with same name if exists
            library.removeIf(comp -> name.equals(comp.getName()));
            library.add(cc);
            Gson gson = new Gson();
            try (FileWriter fw = new FileWriter(libFile)) {
                gson.toJson(library, fw);
            }
            
            if (onComponentSaved != null) onComponentSaved.run();
            close();
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to save: " + e.getMessage());
            alert.show();
        }
    }
    
    private List<CustomComponent> loadLibrary() {
        try {
            File dir = new File(System.getProperty("user.home"), "Documents/Perf2Gerber_Projects");
            File libFile = new File(dir, "custom_parts.json");
            if (libFile.exists()) {
                Gson gson = new Gson();
                List<CustomComponent> library = gson.fromJson(new FileReader(libFile), new TypeToken<List<CustomComponent>>(){}.getType());
                if (library != null) return library;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    private void refreshDeleteCombo(ComboBox<String> cb) {
        cb.getItems().clear();
        for (CustomComponent cc : loadLibrary()) {
            cb.getItems().add(cc.getName());
        }
    }
    
    private void loadComponentForEdit(String name) {
        List<CustomComponent> library = loadLibrary();
        for (CustomComponent cc : library) {
            if (name.equals(cc.getName())) {
                txtName.setText(cc.getName());
                try {
                    colorPicker.setValue(Color.web(cc.getColor()));
                } catch (Exception ex) {
                    colorPicker.setValue(Color.web("#5C8A5C"));
                }
                
                shapes.clear();
                undoStack.clear();
                for (CustomComponent.ShapeDef s : cc.getShapes()) {
                    // Re-center around 7.0, 7.0 for editing
                    shapes.add(new CustomComponent.ShapeDef(s.offsetX + 7.0, s.offsetY + 7.0, s.width, s.height, s.type, s.color));
                }
                draw();
                break;
            }
        }
    }
    
    private void deleteComponent(String name) {
        List<CustomComponent> library = loadLibrary();
        if (library.removeIf(c -> name.equals(c.getName()))) {
            try {
                File dir = new File(System.getProperty("user.home"), "Documents/Perf2Gerber_Projects");
                File libFile = new File(dir, "custom_parts.json");
                Gson gson = new Gson();
                try (FileWriter fw = new FileWriter(libFile)) {
                    gson.toJson(library, fw);
                }
                if (onComponentSaved != null) onComponentSaved.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
