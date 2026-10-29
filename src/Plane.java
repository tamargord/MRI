/** Checks the section points and calculates the output image size. */
public final class Plane {
    public static final double GEOMETRY_TOLERANCE = 0.001;
    // Keep the point names from the assignment: a1/a2 at the top, b/c at the bottom.
    public final Point3D a1, a2, b, c;
    public final double leftSideLength, rightSideLength, bottomLength;
    public final int width, height;

    public Plane(Point3D... points) {
        if (points.length != 3 && points.length != 4) {
            throw new IllegalArgumentException("A plane needs three or four points");
        }
        a1 = points[0];
        a2 = points.length == 3 ? points[0] : points[1];
        b = points[points.length - 2];
        c = points[points.length - 1];
        leftSideLength = b.minus(a1).length();
        rightSideLength = c.minus(a2).length();
        bottomLength = c.minus(b).length();
        if (leftSideLength < 1e-9 || rightSideLength < 1e-9 || bottomLength < 1e-9) {
            throw new IllegalArgumentException("Plane edges must have positive length");
        }
        if (Math.abs(leftSideLength - rightSideLength) >= GEOMETRY_TOLERANCE) {
            throw new IllegalArgumentException("Plane side lengths must agree within 0.001");
        }
        if (a2.minus(a1).length() > bottomLength) {
            throw new IllegalArgumentException("Top edge cannot exceed bottom edge");
        }
        Point3D normal = b.minus(a1).cross(c.minus(a1));
        double normalLength = normal.length();
        if (normalLength < 1e-10 * leftSideLength * bottomLength) {
            throw new IllegalArgumentException("Plane points must not be collinear");
        }
        if (Math.abs(a2.minus(a1).dot(normal)) / normalLength >= GEOMETRY_TOLERANCE) {
            throw new IllegalArgumentException("Plane points must be coplanar");
        }
        // Test whether each side has endpoints on opposite sides of the other side.
        if (points.length == 4 && a2.minus(a1).length() > 1e-9) {
            double leftToTop = b.minus(a1).cross(a2.minus(a1)).dot(normal);
            double leftToBottom = b.minus(a1).cross(c.minus(a1)).dot(normal);
            double rightToTop = c.minus(a2).cross(a1.minus(a2)).dot(normal);
            double rightToBottom = c.minus(a2).cross(b.minus(a2)).dot(normal);
            if (leftToTop * leftToBottom < 0 && rightToTop * rightToBottom < 0) {
                throw new IllegalArgumentException("Plane side edges must not cross");
            }
        }
        double imageHeight = Math.ceil(leftSideLength);
        double imageWidth = Math.ceil(bottomLength);
        if (!Double.isFinite(imageHeight * imageWidth) || imageHeight * imageWidth > 16_000_000) {
            throw new IllegalArgumentException("Projection exceeds the 16 million pixel limit");
        }
        height = (int) imageHeight;
        width = (int) imageWidth;
    }
}
