/** Flat, first-axis-fastest scalar volume with an invertible world transform. */
public final class Volume {
    public final int sx, sy, sz;
    public final int bits;
    public final boolean signed;
    private final Object data;
    private final Point3D origin;
    private final Point3D[] directions;
    private final Point3D[] inverse;

    public Volume(int sx, int sy, int sz, int bits, boolean signed,
                  Object data, Point3D origin, Point3D[] directions) {
        if (sx<1 || sy<1 || sz<1 || sx>=1024 || sy>=1024 || sz>=1024)
            throw new IllegalArgumentException("Each volume dimension must be in 1..1023");
        long count=(long)sx*sy*sz;
        boolean valid = switch(bits) {
            case 8 -> data instanceof byte[] a && a.length==count;
            case 16 -> data instanceof short[] a && a.length==count;
            case 32 -> data instanceof int[] a && a.length==count;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Scalar storage does not match volume dimensions/type");
        if (origin==null || directions==null || directions.length!=3)
            throw new IllegalArgumentException("Three spatial directions and an origin are required");
        this.sx=sx; this.sy=sy; this.sz=sz; this.bits=bits; this.signed=signed;
        this.data=data; this.origin=origin; this.directions=directions.clone();
        Point3D a=directions[0], b=directions[1], c=directions[2];
        double determinant=a.dot(b.cross(c));
        double scale=a.length()*b.length()*c.length();
        if (!Double.isFinite(scale) || scale==0 || Math.abs(determinant)/scale<1e-12)
            throw new IllegalArgumentException("Singular or ill-conditioned spatial transform");
        inverse=new Point3D[]{b.cross(c).times(1/determinant), c.cross(a).times(1/determinant), a.cross(b).times(1/determinant)};
    }
    public Point3D worldToVoxel(Point3D p) { return worldVectorToVoxel(p.minus(origin)); }
    public Point3D worldVectorToVoxel(Point3D p) {
        return new Point3D(inverse[0].dot(p),inverse[1].dot(p),inverse[2].dot(p));
    }
    public Point3D voxelToWorld(Point3D p) {
        return origin.plus(directions[0].times(p.x())).plus(directions[1].times(p.y())).plus(directions[2].times(p.z()));
    }
    public long sample(double x, double y, double z) {
        long ix=Math.round(x), iy=Math.round(y), iz=Math.round(z);
        if(ix<0 || iy<0 || iz<0 || ix>=sx || iy>=sy || iz>=sz) return 0;
        int i=(int)(ix+sx*(iy+sy*iz));
        return switch(bits) {
            case 8 -> signed ? ((byte[])data)[i] : Byte.toUnsignedInt(((byte[])data)[i]);
            case 16 -> signed ? ((short[])data)[i] : Short.toUnsignedInt(((short[])data)[i]);
            case 32 -> signed ? ((int[])data)[i] : Integer.toUnsignedLong(((int[])data)[i]);
            default -> throw new AssertionError();
        };
    }
    public long sample(Point3D p) { return sample(p.x(),p.y(),p.z()); }
}
