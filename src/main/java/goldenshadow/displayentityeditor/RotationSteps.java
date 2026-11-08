package goldenshadow.displayentityeditor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Keeps exact degree settings independently of the quaternion representation. */
public final class RotationSteps {
    private RotationSteps() {}

    public static double change(Display display, Transformation transform, boolean left,
                                int axis, double step, boolean reset) {
        Quaternionf rotation = left ? transform.getLeftRotation() : transform.getRightRotation();
        NamespacedKey key = DisplayEntityEditor.legacyKey(left ? "left_angles" : "right_angles");
        byte[] stored = display.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        double[] angles = null;
        if (stored != null && stored.length == 24) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(stored);
            angles = new double[]{buffer.getDouble(), buffer.getDouble(), buffer.getDouble()};
        }
        if (angles == null || angles.length != 3 || !matches(rotation, angles)) {
            Vector3f euler = new Quaternionf(rotation).normalize().getEulerAnglesXYZ(new Vector3f());
            angles = new double[]{StepMath.round(Math.toDegrees(euler.x)),
                    StepMath.round(Math.toDegrees(euler.y)), StepMath.round(Math.toDegrees(euler.z))};
        }
        angles[axis] = reset ? 0 : StepMath.round(angles[axis] + step);
        rotation.set(fromDegrees(angles));
        byte[] data = java.nio.ByteBuffer.allocate(24).putDouble(angles[0]).putDouble(angles[1]).putDouble(angles[2]).array();
        display.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, data);
        return angles[axis];
    }

    private static Quaternionf fromDegrees(double[] angles) {
        return new Quaternionf().rotationXYZ((float) Math.toRadians(angles[0]),
                (float) Math.toRadians(angles[1]), (float) Math.toRadians(angles[2]));
    }

    private static boolean matches(Quaternionf rotation, double[] angles) {
        return Math.abs(new Quaternionf(rotation).normalize().dot(fromDegrees(angles))) > 0.999999f;
    }
}
