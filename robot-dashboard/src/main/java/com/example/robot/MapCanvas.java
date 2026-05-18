package com.example.robot;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renders the CityMap onto a JavaFX Canvas.
 * Call redraw() whenever the map or robot position changes.
 *
 * Tile images are loaded from /resources/tiles/ using the Tile.Id name
 * (e.g. BUILDING.png, ROAD_URD.png). Any tile without an image falls back
 * to the vector drawing automatically.
 */
public class MapCanvas extends Canvas {

    private static final Color COL_BUILDING   = Color.web("#fff0f0");
    private static final Color COL_BUILDING_R = Color.web("#e24b4a");
    private static final Color COL_ROAD       = Color.web("#f0f8fd");
    private static final Color COL_PICKUP     = Color.web("#f0f8e8");
    private static final Color COL_GRID       = Color.web("#dddddd");
    private static final Color COL_STREET_G   = Color.web("#0e6e56");   // green lane line
    private static final Color COL_STREET_B   = Color.web("#1a5fa5");   // blue lane line
    private static final Color COL_SQUARE     = Color.web("#222222");   // corner black square
    private static final Color COL_ROBOT      = Color.web("#FF6B00cc");
    private static final Color COL_ROBOT_TXT  = Color.WHITE;
    private static final Color COL_TILE_ID    = Color.web("#aaaaaa");

    /** Pre-loaded images keyed by Tile.Id. Only contains entries that loaded successfully. */
    private final Map<Tile.Id, Image> tileImages = new EnumMap<>(Tile.Id.class);

    private CityMap map;
    private int robotRow = -1;
    private int robotCol = -1;

    // Selected pickup/delivery tiles for map-click flow
    private int selectedPickupRow   = -1;
    private int selectedPickupCol   = -1;
    private int selectedDeliveryRow = -1;
    private int selectedDeliveryCol = -1;

    public MapCanvas(double width, double height) {
        super(width, height);
        loadTileImages();
    }

    /**
     * Tries to load a PNG for every Tile.Id from /tiles/<ID_NAME>.png.
     * Missing files are silently skipped — those tiles use vector drawing.
     */
    private void loadTileImages() {
        for (Tile.Id id : Tile.Id.values()) {
            String path = "/tiles/" + id.name() + ".png";
            var stream = getClass().getResourceAsStream(path);
            if (stream != null) {
                Image img = new Image(stream);
                if (!img.isError()) {
                    tileImages.put(id, img);
                }
            }
        }
    }

    public void setMap(CityMap map) {
        this.map = map;
        redraw();
    }

    public void setRobotPosition(int row, int col) {
        this.robotRow = row;
        this.robotCol = col;
        redraw();
    }

    public void setSelectedPickup(int row, int col) {
        this.selectedPickupRow = row;
        this.selectedPickupCol = col;
        redraw();
    }

    public void setSelectedDelivery(int row, int col) {
        this.selectedDeliveryRow = row;
        this.selectedDeliveryCol = col;
        redraw();
    }

    public void clearSelections() {
        selectedPickupRow = selectedPickupCol = -1;
        selectedDeliveryRow = selectedDeliveryCol = -1;
        redraw();
    }

    /** Pixel margin reserved for row/column index labels. */
    private static final double MARGIN = 22;

    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.clearRect(0, 0, w, h);

        // Background for label margin area
        gc.setFill(Color.web("#f0f0f0"));
        gc.fillRect(0, 0, w, h);

        if (map == null) {
            gc.setFill(Color.web("#888888"));
            gc.setFont(Font.font(14));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("Esperando mapa MQTT...", w / 2, h / 2);
            return;
        }

        double gridW = w - MARGIN;
        double gridH = h - MARGIN;
        double cellW = gridW / map.getCols();
        double cellH = gridH / map.getRows();

        // Draw tiles (offset by MARGIN to leave room for labels top+left)
        for (int row = 0; row < map.getRows(); row++) {
            for (int col = 0; col < map.getCols(); col++) {
                double x = MARGIN + col * cellW;
                double y = MARGIN + row * cellH;
                drawTile(gc, map.getTile(row, col), x, y, cellW, cellH, row, col);
            }
        }

        // Black grid lines drawn on top of tiles
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        for (int col = 0; col <= map.getCols(); col++) {
            double x = MARGIN + col * cellW;
            gc.strokeLine(x, MARGIN, x, MARGIN + gridH);
        }
        for (int row = 0; row <= map.getRows(); row++) {
            double y = MARGIN + row * cellH;
            gc.strokeLine(MARGIN, y, MARGIN + gridW, y);
        }

        // Yellow borders on pickup/delivery tiles — drawn AFTER black grid so they stay visible
        gc.setStroke(Color.web("#FFB300"));
        gc.setLineWidth(3.0);
        for (int row = 0; row < map.getRows(); row++) {
            for (int col = 0; col < map.getCols(); col++) {
                if (map.getTile(row, col).isPickupOrDelivery()) {
                    double x = MARGIN + col * cellW + 1;
                    double y = MARGIN + row * cellH + 1;
                    gc.strokeRect(x, y, cellW - 2, cellH - 2);
                }
            }
        }

        // Green overlay for selected pickup tile
        if (selectedPickupRow >= 0 && selectedPickupCol >= 0) {
            double x = MARGIN + selectedPickupCol * cellW + 1;
            double y = MARGIN + selectedPickupRow * cellH + 1;
            gc.setFill(Color.web("#00cc44", 0.40));
            gc.fillRect(x, y, cellW - 2, cellH - 2);
            gc.setStroke(Color.web("#00cc44"));
            gc.setLineWidth(3.0);
            gc.strokeRect(x, y, cellW - 2, cellH - 2);
        }

        // Blue overlay for selected delivery tile
        if (selectedDeliveryRow >= 0 && selectedDeliveryCol >= 0) {
            double x = MARGIN + selectedDeliveryCol * cellW + 1;
            double y = MARGIN + selectedDeliveryRow * cellH + 1;
            gc.setFill(Color.web("#1a5fa5", 0.40));
            gc.fillRect(x, y, cellW - 2, cellH - 2);
            gc.setStroke(Color.web("#1a5fa5"));
            gc.setLineWidth(3.0);
            gc.strokeRect(x, y, cellW - 2, cellH - 2);
        }

        // Column numbers along the top
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font("Arial", Math.min(MARGIN * 0.65, cellW * 0.5)));
        gc.setTextAlign(TextAlignment.CENTER);
        for (int col = 0; col < map.getCols(); col++) {
            double cx = MARGIN + col * cellW + cellW / 2;
            gc.fillText(String.valueOf(col), cx, MARGIN - 5);
        }

        // Row numbers along the left
        gc.setTextAlign(TextAlignment.RIGHT);
        for (int row = 0; row < map.getRows(); row++) {
            double cy = MARGIN + row * cellH + cellH / 2 + 4;
            gc.fillText(String.valueOf(row), MARGIN - 4, cy);
        }

        if (robotRow >= 0 && robotCol >= 0) {
            drawRobot(gc, robotRow, robotCol, cellW, cellH);
        }
    }

    private void drawTile(GraphicsContext gc, Tile tile, double x, double y, double w, double h,
                          int row, int col) {
        boolean pickup = tile.isPickupOrDelivery();

        // --- Image path: draw image and return (with pickup overlay if needed) ---
        Image img = tileImages.get(tile.getId());
        if (img != null) {
            gc.drawImage(img, x, y, w, h);
            if (pickup) {
                // Yellow semi-transparent overlay over image
                gc.setFill(Color.web("#FFE44D", 0.55));
                gc.fillRect(x, y, w, h);
                // Bold "P" label
                gc.setFill(Color.web("#996600"));
                gc.setFont(Font.font("Arial", Font.font("Arial").getSize() * 1.1));
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText("P", x + w - 3, y + 13);
                // Yellow border to make the tile pop even more
                gc.setStroke(Color.web("#FFB300"));
                gc.setLineWidth(2.5);
                gc.strokeRect(x + 1, y + 1, w - 2, h - 2);
            }
            return;
        }

        // --- Vector fallback ---
        if (tile.isBuilding()) {
            gc.setFill(COL_BUILDING);
            gc.fillRect(x, y, w, h);
            gc.setStroke(COL_GRID);
            gc.setLineWidth(0.5);
            gc.strokeRect(x, y, w, h);
            // Red circle
            gc.setStroke(COL_BUILDING_R);
            gc.setLineWidth(3);
            double r = Math.min(w, h) * 0.28;
            gc.strokeOval(x + w/2 - r, y + h/2 - r, r*2, r*2);
            return;
        }

        // Road cell background
        gc.setFill(pickup ? COL_PICKUP : COL_ROAD);
        gc.fillRect(x, y, w, h);
        gc.setStroke(COL_GRID);
        gc.setLineWidth(0.5);
        gc.strokeRect(x, y, w, h);

        // Road fill bands (light blue)
        double roadFrac = 0.30;
        double rw = w * roadFrac, rh = h * roadFrac;
        double cx = x + w/2, cy = y + h/2;

        gc.setFill(Color.web("#e0f0ff"));
        if (tile.connectsLeft())  gc.fillRect(x, cy - rh/2, cx - x, rh);
        if (tile.connectsRight()) gc.fillRect(cx, cy - rh/2, x + w - cx, rh);
        if (tile.connectsUp())    gc.fillRect(cx - rw/2, y, rw, cy - y);
        if (tile.connectsDown())  gc.fillRect(cx - rw/2, cy, rw, y + h - cy);
        // Center square
        double cs = Math.max(rw, rh);
        gc.fillRect(cx - cs/2, cy - cs/2, cs, cs);

        // Green lane lines
        drawLaneLines(gc, tile, x, y, w, h, cx, cy, rw, rh, COL_STREET_G, 0.18, 1.8);
        // Blue lane lines (outer)
        drawLaneLines(gc, tile, x, y, w, h, cx, cy, rw, rh, COL_STREET_B, 0.36, 1.4);

        // Black corner squares
        double sq = rh * 0.28;
        gc.setFill(COL_SQUARE);
        gc.fillRect(cx - sq/2, cy - sq/2, sq, sq);

        // Pickup marker
        if (pickup) {
            gc.setFill(Color.web("#FFE44D", 0.55));
            gc.fillRect(x, y, w, h);
            gc.setFill(Color.web("#996600"));
            gc.setFont(Font.font("Arial", Font.font("Arial").getSize() * 1.1));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("P", x + w - 3, y + 13);
            gc.setStroke(Color.web("#FFB300"));
            gc.setLineWidth(2.5);
            gc.strokeRect(x + 1, y + 1, w - 2, h - 2);
        }

        // Tile ID label (small, bottom-left)
        gc.setFill(COL_TILE_ID);
        gc.setFont(Font.font("Monospaced", 9));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(String.format("%02d", tile.getCode()), x + 2, y + h - 2);
    }

    private void drawLaneLines(GraphicsContext gc, Tile tile,
                               double x, double y, double w, double h,
                               double cx, double cy, double rw, double rh,
                               Color color, double offsetFrac, double lineWidth) {
        double off = rh * offsetFrac;
        gc.setStroke(color);
        gc.setLineWidth(lineWidth);

        if (tile.connectsLeft()) {
            gc.strokeLine(x, cy - off, cx - rw/2, cy - off);
            gc.strokeLine(x, cy + off, cx - rw/2, cy + off);
        }
        if (tile.connectsRight()) {
            gc.strokeLine(cx + rw/2, cy - off, x + w, cy - off);
            gc.strokeLine(cx + rw/2, cy + off, x + w, cy + off);
        }
        if (tile.connectsUp()) {
            gc.strokeLine(cx - off, y, cx - off, cy - rh/2);
            gc.strokeLine(cx + off, y, cx + off, cy - rh/2);
        }
        if (tile.connectsDown()) {
            gc.strokeLine(cx - off, cy + rh/2, cx - off, y + h);
            gc.strokeLine(cx + off, cy + rh/2, cx + off, y + h);
        }
    }

    private void drawRobot(GraphicsContext gc, int row, int col, double cellW, double cellH) {
        double cx = MARGIN + col * cellW + cellW / 2;
        double cy = MARGIN + row * cellH + cellH / 2;
        double r  = Math.min(cellW, cellH) * 0.20;

        gc.setFill(COL_ROBOT);
        gc.fillOval(cx - r, cy - r, r*2, r*2);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(cx - r, cy - r, r*2, r*2);

        gc.setFill(COL_ROBOT_TXT);
        gc.setFont(Font.font("Arial", Font.getDefault().getSize() * 0.7));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("R", cx, cy + 4);
    }
}