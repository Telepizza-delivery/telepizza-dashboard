package com.example.robot;

import java.util.List;

/**
 * Calcula la posicion y orientacion actual del robot a partir de:
 *   - la casilla inicial (siempre (6,0) segun el enunciado),
 *   - la orientacion inicial (derivada del tipo de bloque de la casilla
 *     inicial: vertical -> N, horizontal -> E),
 *   - el subconjunto de instrucciones completadas que el robot publica
 *     en Equipo E/odometry.
 *
 * Como Pablo resetea el contador de instrucciones completadas al recibir
 * un nuevo pedido, el tracker mantiene un "snapshot" de la posicion al
 * recibir LISTO; el siguiente pedido se aplica desde ese snapshot.
 */
public class RobotTracker {

    public enum Heading { N, E, S, W }

    private int row;
    private int col;
    private Heading heading;

    private int snapshotRow;
    private int snapshotCol;
    private Heading snapshotHeading;

    public RobotTracker(int row, int col, Heading heading) {
        this.row = this.snapshotRow = row;
        this.col = this.snapshotCol = col;
        this.heading = this.snapshotHeading = heading;
    }

    /** Heading inicial derivada del tipo de bloque (regla del enunciado). */
    public static Heading initialHeading(Tile tile) {
        if (tile.connectsUp())    return Heading.N;
        if (tile.connectsRight()) return Heading.E;
        if (tile.connectsDown())  return Heading.S;
        if (tile.connectsLeft())  return Heading.W;
        return Heading.N;
    }

    /**
     * Reaplica la lista completa de instrucciones completadas desde el
     * ultimo snapshot. Es idempotente: aunque el robot reenvie el mismo
     * subconjunto varias veces el resultado es el mismo.
     */
    public void applyCompleted(List<String> instructions) {
        row = snapshotRow;
        col = snapshotCol;
        heading = snapshotHeading;
        for (String instr : instructions) {
            applyOne(instr);
        }
    }

    /** Fija el snapshot al estado actual. Llamar al recibir LISTO. */
    public void commitSnapshot() {
        snapshotRow = row;
        snapshotCol = col;
        snapshotHeading = heading;
    }

    private void applyOne(String instr) {
        switch (instr) {
            case "MOVE" -> {
                switch (heading) {
                    case N -> row--;
                    case S -> row++;
                    case E -> col++;
                    case W -> col--;
                }
            }
            case "TURN_LEFT"  -> heading = turnLeft(heading);
            case "TURN_RIGHT" -> heading = turnRight(heading);
            case "TURN_BACK"  -> { heading = turnLeft(heading); heading = turnLeft(heading); }
            default -> { /* PICK_UP, DELIVER, STRAIGHT: no afectan posicion */ }
        }
    }

    private static Heading turnLeft(Heading h) {
        return switch (h) {
            case N -> Heading.W;
            case W -> Heading.S;
            case S -> Heading.E;
            case E -> Heading.N;
        };
    }

    private static Heading turnRight(Heading h) {
        return switch (h) {
            case N -> Heading.E;
            case E -> Heading.S;
            case S -> Heading.W;
            case W -> Heading.N;
        };
    }

    public int getRow()         { return row; }
    public int getCol()         { return col; }
    public Heading getHeading() { return heading; }
}
