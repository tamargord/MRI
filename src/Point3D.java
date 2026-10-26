/** A point or a vector with three coordinates. */
public class Point3D {
    public final double x;
    public final double y;
    public final double z;

    public Point3D(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Coordinates must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point3D plus(Point3D other) {
        return new Point3D(x + other.x, y + other.y, z + other.z);
    }

    public Point3D minus(Point3D other) {
        return new Point3D(x - other.x, y - other.y, z - other.z);
    }

    public Point3D times(double number) {
        return new Point3D(x * number, y * number, z * number);
    }

    public double dot(Point3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Point3D cross(Point3D other) {
        return new Point3D(
                y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
