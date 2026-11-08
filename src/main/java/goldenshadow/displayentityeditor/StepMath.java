package goldenshadow.displayentityeditor;

import org.bukkit.Location;
import org.joml.Vector3f;

public final class StepMath {
    private StepMath() {}

    public static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    public static void add(Vector3f vector, float x, float y, float z) {
        if (x != 0) vector.x = (float) round(vector.x + (double) x);
        if (y != 0) vector.y = (float) round(vector.y + (double) y);
        if (z != 0) vector.z = (float) round(vector.z + (double) z);
    }

    public static Location move(Location location, double x, double y, double z) {
        if (x != 0) location.setX(round(location.getX() + x));
        if (y != 0) location.setY(round(location.getY() + y));
        if (z != 0) location.setZ(round(location.getZ() + z));
        return location;
    }
}
