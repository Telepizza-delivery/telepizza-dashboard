package com.example.robot;

import java.util.ArrayList;
import java.util.List;

public class CityMap {

    private final int cols;
    private final int rows;
    private final Tile[][] grid;
    private final List<int[]> pickupPoints = new ArrayList<>();

    public CityMap(String mqttPayload, int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.grid = new Tile[rows][cols];

        String s = mqttPayload.trim().replaceAll("\\s+", "");
        if (s.length() != rows * cols * 2) {
            throw new IllegalArgumentException(
                    "Expected " + (rows * cols * 2) + " chars, got " + s.length());
        }

        // First pass: build the grid
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = (row * cols + col) * 2;
                int code = Integer.parseInt(s.substring(idx, idx + 2));
                grid[row][col] = new Tile(code);
            }
        }

        // Second pass: find pickup/delivery points
        // A tile is a pickup/delivery point if it is a road tile
        // that has exactly 1 neighbour that is also a road tile
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (!grid[row][col].isBuilding() && isPickupPoint(row, col)) {
                    pickupPoints.add(new int[]{row, col});
                }
            }
        }
    }

    private boolean isPickupPoint(int row, int col) {
        Tile tile = grid[row][col];
        int validConnections = 0;

        // UP: this tile connects up AND neighbour exists AND neighbour connects DOWN back
        if (tile.connectsUp() && row > 0
                && !grid[row-1][col].isBuilding()
                && grid[row-1][col].connectsDown())
            validConnections++;

        // DOWN: this tile connects down AND neighbour exists AND neighbour connects UP back
        if (tile.connectsDown() && row < rows-1
                && !grid[row+1][col].isBuilding()
                && grid[row+1][col].connectsUp())
            validConnections++;

        // LEFT: this tile connects left AND neighbour exists AND neighbour connects RIGHT back
        if (tile.connectsLeft() && col > 0
                && !grid[row][col-1].isBuilding()
                && grid[row][col-1].connectsRight())
            validConnections++;

        // RIGHT: this tile connects right AND neighbour exists AND neighbour connects LEFT back
        if (tile.connectsRight() && col < cols-1
                && !grid[row][col+1].isBuilding()
                && grid[row][col+1].connectsLeft())
            validConnections++;

        return validConnections == 1;
    }

    public Tile getTile(int row, int col) { return grid[row][col]; }
    public int getCols()                  { return cols; }
    public int getRows()                  { return rows; }
    public List<int[]> getPickupPoints()  { return pickupPoints; }
}