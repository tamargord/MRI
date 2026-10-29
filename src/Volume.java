/** Stores MRI values and converts world coordinates to array positions. */
public final class Volume {
    // Number of voxels along the x, y and z axes.
    public final int sx, sy, sz;
    public final int bits;
    public final boolean signed;
    private final byte[] byteData;
    private final short[] shortData;
    private final int[] intData;
    private final Point3D origin;
    private final Point3D[] directions;
    private final Point3D[] inverse;

    public Volume(
            int sx,
            int sy,
            int sz,
            int bits,
            boolean signed,
            Object data,
            Point3D origin,
            Point3D[] directions) {
        if (sx < 1 || sy < 1 || sz < 1 || sx >= 1024 || sy >= 1024 || sz >= 1024) {
            throw new IllegalArgumentException("Each volume dimension must be in 1..1023");
        }
        long count = (long) sx * sy * sz;
        // The file type decides which array holds the voxels.
        boolean valid = false;
        if (bits == 8 && data instanceof byte[]) {
            valid = ((byte[]) data).length == count;
        } else if (bits == 16 && data instanceof short[]) {
            valid = ((short[]) data).length == count;
        } else if (bits == 32 && data instanceof int[]) {
            valid = ((int[]) data).length == count;
        }
        if (!valid) {
            throw new IllegalArgumentException(
                    "Scalar storage does not match volume dimensions/type");
        }
        if (origin == null || directions == null || directions.length != 3) {
            throw new IllegalArgumentException(
                    "Three spatial directions and an origin are required");
        }
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.bits = bits;
        this.signed = signed;
        byteData = bits == 8 ? (byte[]) data : null;
        shortData = bits == 16 ? (short[]) data : null;
        intData = bits == 32 ? (int[]) data : null;
        this.origin = origin;
        this.directions = directions.clone();
        Point3D a = directions[0], b = directions[1], c = directions[2];
        double determinant = a.dot(b.cross(c));
        double scale = a.length() * b.length() * c.length();
        if (!Double.isFinite(scale) || scale == 0 || Math.abs(determinant) / scale < 1e-12) {
            throw new IllegalArgumentException("Singular or ill-conditioned spatial transform");
        }
        inverse =
                new Point3D[] {
                    b.cross(c).times(1 / determinant),
                    c.cross(a).times(1 / determinant),
                    a.cross(b).times(1 / determinant)
                };
    }

    public Point3D worldToVoxel(Point3D worldPoint) {
        return worldVectorToVoxel(worldPoint.minus(origin));
    }

    // A direction has no position, so the origin is not subtracted here.
    public Point3D worldVectorToVoxel(Point3D worldVector) {
        return new Point3D(
                inverse[0].dot(worldVector),
                inverse[1].dot(worldVector),
                inverse[2].dot(worldVector));
    }

    public Point3D voxelToWorld(Point3D voxelPoint) {
        return origin.plus(directions[0].times(voxelPoint.x))
                .plus(directions[1].times(voxelPoint.y))
                .plus(directions[2].times(voxelPoint.z));
    }

    /** Reads the nearest voxel in voxel coordinates, or zero outside the volume. */
    public long sample(double x, double y, double z) {
        long ix = Math.round(x), iy = Math.round(y), iz = Math.round(z);
        if (ix < 0 || iy < 0 || iz < 0 || ix >= sx || iy >= sy || iz >= sz) {
            return 0;
        }
        // NRRD puts adjacent x values next to each other in the file.
        int index = (int) (ix + sx * (iy + sy * iz));
        if (bits == 8) {
            if (signed) {
                return byteData[index];
            }
            return Byte.toUnsignedInt(byteData[index]);
        }
        if (bits == 16) {
            if (signed) {
                return shortData[index];
            }
            return Short.toUnsignedInt(shortData[index]);
        }
        if (signed) {
            return intData[index];
        }
        return Integer.toUnsignedLong(intData[index]);
    }

    public long sample(Point3D voxelPoint) {
        return sample(voxelPoint.x, voxelPoint.y, voxelPoint.z);
    }
}
