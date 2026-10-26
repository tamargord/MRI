/** Validated bounded plane following the assignment's explicit geometry rules. */
public final class Plane {
    public static final double EPS=0.001;
    public final Point3D a1,a2,b,c;
    public final double length, otherLength, base;
    public final int width,height;
    public Plane(Point3D... points) {
        if(points.length!=3 && points.length!=4) throw new IllegalArgumentException("A plane needs three or four points");
        a1=points[0]; a2=points.length==3 ? points[0] : points[1];
        b=points[points.length-2]; c=points[points.length-1];
        length=b.minus(a1).length(); otherLength=c.minus(a2).length(); base=c.minus(b).length();
        if(length<1e-9 || otherLength<1e-9 || base<1e-9)
            throw new IllegalArgumentException("Plane edges must have positive length");
        if(Math.abs(length-otherLength)>=EPS) throw new IllegalArgumentException("Plane side lengths must agree within 0.001");
        if(a2.minus(a1).length()>base) throw new IllegalArgumentException("Top edge cannot exceed bottom edge");
        Point3D normal=b.minus(a1).cross(c.minus(a1));
        double normalLength=normal.length();
        if(normalLength<1e-10*length*base) throw new IllegalArgumentException("Plane points must not be collinear");
        if(Math.abs(a2.minus(a1).dot(normal))/normalLength>=EPS) throw new IllegalArgumentException("Plane points must be coplanar");
        // Opposite traversing edges must not cross: a section must be a simple region.
        if(points.length==4 && a2.minus(a1).length()>1e-9) {
            double s1=b.minus(a1).cross(a2.minus(a1)).dot(normal);
            double s2=b.minus(a1).cross(c.minus(a1)).dot(normal);
            double t1=c.minus(a2).cross(a1.minus(a2)).dot(normal);
            double t2=c.minus(a2).cross(b.minus(a2)).dot(normal);
            if(s1*s2<0 && t1*t2<0) throw new IllegalArgumentException("Plane side edges must not cross");
        }
        double h=Math.ceil(length),w=Math.ceil(base);
        if(!Double.isFinite(h*w) || h*w>16_000_000) throw new IllegalArgumentException("Projection exceeds the 16 million pixel limit");
        height=(int)h; width=(int)w;
    }
}
