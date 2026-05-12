package com.example.robot;

/**
 * Represents a single tile in the city map.
 * IDs 0-11 match the project specification (Figuras 9-20).
 */
public class Tile {

    public enum Id {
        BUILDING(0),        // Figura 9  - Edificio
        ROAD_LR(1),         // Figura 10 - Calle izquierda-derecha
        ROAD_UD(2),         // Figura 11 - Calle arriba-abajo
        ROAD_UR(3),         // Figura 12 - Calle arriba-derecha
        ROAD_RD(4),         // Figura 13 - Calle derecha-abajo
        ROAD_DL(5),         // Figura 14 - Calle abajo-izquierda
        ROAD_LU(6),         // Figura 15 - Calle izquierda-arriba
        ROAD_LUR(7),        // Figura 16 - Calle izquierda-arriba-derecha  (T: no down)
        ROAD_URD(8),        // Figura 17 - Calle arriba-derecha-abajo      (T: no left)
        ROAD_RDL(9),        // Figura 18 - Calle derecha-abajo-izquierda   (T: no up)
        ROAD_DLU(10),       // Figura 19 - Calle abajo-izquierda-arriba    (T: no right)
        ROAD_ALL(11);       // Figura 20 - Calle arriba-derecha-abajo-izquierda (cross)

        public final int code;
        Id(int code) { this.code = code; }

        public static Id fromCode(int code) {
            for (Id id : values()) if (id.code == code) return id;
            throw new IllegalArgumentException("Unknown tile code: " + code);
        }
    }

    private final Id id;
    private final boolean connectsUp, connectsRight, connectsDown, connectsLeft;

    public Tile(int code) {
        this.id = Id.fromCode(code);
        switch (id) {
            case BUILDING  -> { connectsUp=false; connectsRight=false; connectsDown=false; connectsLeft=false; }
            case ROAD_LR   -> { connectsUp=false; connectsRight=true;  connectsDown=false; connectsLeft=true;  }
            case ROAD_UD   -> { connectsUp=true;  connectsRight=false; connectsDown=true;  connectsLeft=false; }
            case ROAD_UR   -> { connectsUp=true;  connectsRight=true;  connectsDown=false; connectsLeft=false; }
            case ROAD_RD   -> { connectsUp=false; connectsRight=true;  connectsDown=true;  connectsLeft=false; }
            case ROAD_DL   -> { connectsUp=false; connectsRight=false; connectsDown=true;  connectsLeft=true;  }
            case ROAD_LU   -> { connectsUp=true;  connectsRight=false; connectsDown=false; connectsLeft=true;  }
            case ROAD_LUR  -> { connectsUp=true; connectsRight=true;  connectsDown=false; connectsLeft=true;  }
            case ROAD_URD  -> { connectsUp=true;  connectsRight=true;  connectsDown=true;  connectsLeft=false; }
            case ROAD_RDL  -> { connectsUp=false; connectsRight=true;  connectsDown=true;  connectsLeft=true;  }
            case ROAD_DLU  -> { connectsUp=true;  connectsRight=false; connectsDown=true;  connectsLeft=true;  }
            case ROAD_ALL  -> { connectsUp=true;  connectsRight=true;  connectsDown=true;  connectsLeft=true;  }
            default        -> { connectsUp=false; connectsRight=false; connectsDown=false; connectsLeft=false; }
        }
    }

    public Id getId()               { return id; }
    public int getCode()            { return id.code; }
    public boolean isBuilding()     { return id == Id.BUILDING; }
    public boolean connectsUp()     { return connectsUp; }
    public boolean connectsRight()  { return connectsRight; }
    public boolean connectsDown()   { return connectsDown; }
    public boolean connectsLeft()   { return connectsLeft; }

    /** Count of road connections (1 = pickup/delivery point) */
    public int connectionCount() {
        return (connectsUp?1:0)+(connectsRight?1:0)+(connectsDown?1:0)+(connectsLeft?1:0);
    }

    public boolean isPickupOrDelivery() {
        return !isBuilding() && connectionCount() == 1;
    }
}
