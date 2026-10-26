import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Dependency-free regression suite. Run with java -cp out RegressionTests. */
public final class RegressionTests {
    private static int checks;
    private static final Point3D[] ID={p(1,0,0),p(0,1,0),p(0,0,1)};
    private static Point3D p(double x,double y,double z) {return new Point3D(x,y,z);}
    private static void check(boolean ok,String label) {checks++;if(!ok) throw new AssertionError(label);}
    @FunctionalInterface interface Action {void run() throws Exception;}
    private static void reject(Action action,String label) throws Exception {
        try {action.run();} catch(IOException | IllegalArgumentException e) {checks++;return;}
        throw new AssertionError("Accepted invalid input: "+label);
    }
    private static Volume coded() {
        int[] data=new int[12*12*12];
        for(int z=0;z<12;z++) for(int y=0;y<12;y++) for(int x=0;x<12;x++) data[x+12*(y+12*z)]=x+10*y+100*z;
        return new Volume(12,12,12,32,false,data,p(0,0,0),ID);
    }
    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("mri-regression-");
        try {
            DatasetGenerator.small(temp);
            Volume rotated=MRIReader.loadMRI(temp.resolve("coded-u16-rotated.nrrd"));
            check(rotated.sx==8 && rotated.sy==7 && rotated.sz==6,"header sizes");
            for(int z=0;z<6;z++) for(int y=0;y<7;y++) for(int x=0;x<8;x++) {
                Point3D world=rotated.voxelToWorld(p(x,y,z));
                check(Math.abs(world.x()-(10-y))<1e-9 && Math.abs(world.y()-(-20+2*x))<1e-9 && Math.abs(world.z()-(30+1.5*z))<1e-9,"header affine");
                check(rotated.sample(rotated.worldToVoxel(world))==x+10*y+100*z,"rotated value and roundtrip");
            }
            Volume u32=MRIReader.loadMRI(temp.resolve("unsigned-u32-big.nrrd"));
            check(u32.sample(0,0,1)==4294967295L && u32.sample(1,1,0)==2147483648L,"u32 high bit and big endian");
            Volume signed=MRIReader.loadMRI(temp.resolve("signed-i16.nrrd"));
            check(signed.sample(0,0,0)==-32768 && signed.sample(1,0,0)==-1,"signed ASCII preserved");
            // Integer limits across encodings and byte orders, including unsigned high bits.
            for(int bits:new int[]{8,16,32}) for(boolean isSigned:new boolean[]{false,true}) {
                long min=isSigned ? -(1L<<(bits-1)) : 0;
                long max=isSigned ? (1L<<(bits-1))-1 : (1L<<bits)-1;
                long[] values={min,max,0,1};
                for(String encoding:List.of("raw","gzip","ascii")) for(String endian:List.of("little","big")) {
                    Path fixture=temp.resolve("limits.nrrd");
                    DatasetGenerator.write(fixture,(isSigned?"int":"uint")+bits,"2 2 1",encoding,endian,"","\n",values,bits);
                    Volume limits=MRIReader.loadMRI(fixture);
                    for(int i=0;i<4;i++) check(limits.sample(i%2,i/2,0)==values[i],"scalar boundary "+bits+" "+encoding+" "+endian);
                }
            }
            Volume zero=MRIReader.loadMRI(temp.resolve("zero-u8.nrrd"));
            check(zero.sample(2,2,2)==0,"raw byte");
            check(coded().sample(-0.5,0,0)==0 && coded().sample(1.5,0,0)==2,"nearest neighbor ties");
            check(coded().sample(-1,0,0)==0 && coded().sample(12,0,0)==0 && coded().sample(0,0,99)==0,"outside volume");
            // Analytic rectangle: row y samples source y+1; column x samples source x.
            long[][] rect=Projection.project(coded(),p(0,0,2),p(6,0,2),p(0,4,2),p(6,4,2));
            check(rect.length==4 && rect[0].length==6,"rectangle dimensions");
            for(int y=0;y<4;y++) for(int x=0;x<6;x++) check(rect[y][x]==x+10*(y+1)+200,"analytic rectangle pixel");
            // Right-triangle cross-section through (x,z), equal five-unit sides.
            long[][] oblique=Projection.project(coded(),p(0,0,0),p(6,0,0),p(0,3,4),p(6,3,4));
            for(int y=0;y<5;y++) for(int x=0;x<6;x++) check(oblique[y][x]==x+10*Math.round((y+1)*0.6)+100*Math.round((y+1)*0.8),"analytic oblique pixel");
            // 3-4-5 triangle: first row endpoints (2.4,1.8,2), (3.6,1.8,2).
            long[][] triangle=Projection.project(coded(),p(3,1,2),p(0,5,2),p(6,5,2));
            check(triangle.length==5 && triangle[0].length==6,"triangle dimensions");
            check(triangle[0][2]==222 && triangle[0][3]==223 && triangle[0][4]==0,"triangle first row and floor span");
            check(triangle[4][0]==250 && triangle[4][5]==255,"triangle final row");
            long[][] collapsed=Projection.project(coded(),p(3,1,2),p(3,1,2),p(0,5,2),p(6,5,2));
            check(Arrays.deepEquals(triangle,collapsed),"zero-width top equivalent to triangle");
            // Length 1.4: final row must stop at y=1.4 rather than advance to y=2.
            long[][] fractional=Projection.project(coded(),p(0,0,0),p(2,0,0),p(0,1.4,0),p(2,1.4,0));
            check(fractional.length==2 && fractional[1][0]==10,"fractional side clamped");
            // Narrow span 1.6: k=2 is outside; round(span) previously sampled it.
            long[][] narrow=Projection.project(coded(),p(2,0,0),p(0,1.5,0),p(4,1.5,0));
            check(narrow[0][1]==11 && narrow[0][2]==12 && narrow[0][3]==0,"fractional span policy");
            long[][] outside=Projection.project(coded(),p(30,30,30),p(32,30,30),p(30,32,30),p(32,32,30));
            check(Arrays.stream(outside).flatMapToLong(Arrays::stream).sum()==0,"external plane zero");
            check(Projection.grayscale(new long[][]{{0,0}},2,2).getRaster().getSample(0,0,0)==0,"black normalization");
            var gray=Projection.grayscale(new long[][]{{-1,0,2147483648L,4294967295L}},4,2);
            check(gray.getRaster().getSample(0,0,0)==0 && gray.getRaster().getSample(2,0,0)==128 && gray.getRaster().getSample(3,0,0)==255,"signed clamp and full u32 display");
            reject(()->new Point3D(Double.NaN,0,0),"NaN");
            reject(()->new Point3D(Double.POSITIVE_INFINITY,0,0),"infinity");
            reject(()->new Plane(p(0,0,0),p(0,0,0),p(1,0,0)),"zero edge");
            reject(()->new Plane(p(0,0,0),p(-1,0,0),p(1,0,0)),"collinear");
            reject(()->new Plane(p(0,0,0),p(1,0,0),p(0,2,0)),"unequal legs");
            reject(()->new Plane(p(0,0,0),p(2,0,0),p(0,2,0),p(2,0,2)),"noncoplanar or invalid shape");
            reject(()->new Plane(p(0,0,0),p(6,0,0),p(1,3,0),p(5,3,0)),"top wider than base");
            reject(()->new Plane(p(0,0,0),p(2,0,0),p(2,2,0),p(0,2,0)),"crossing sides");
            reject(()->new Volume(1,1,1,8,false,new byte[1],p(0,0,0),new Point3D[]{p(1,0,0),p(2,0,0),p(0,0,1)}),"singular transform");
            List<Plane> mixed=CSVReader.readPlanes(temp.resolve("mixed-planes.csv").toString());
            check(mixed.size()==2 && mixed.get(0).height!=mixed.get(1).height,"mixed CSV order");
            Path csv=temp.resolve("bad.csv");
            for(String line:List.of("", "1,2,3", "0,0,0,1,0,0,0,1,0,", "NaN,0,0,1,0,0,0,1,0", "a,0,0,1,0,0,0,1,0")) {
                Files.writeString(csv,line); reject(()->CSVReader.readPlanes(csv.toString()),"bad CSV");
            }
            Path bad=temp.resolve("bad.nrrd");
            String base="NRRD0005\ntype: uint16\ndimension: 3\nsizes: 1 1 1\nencoding: raw\nendian: little\n\n";
            Files.write(bad,base.getBytes()); reject(()->MRIReader.loadMRI(bad),"truncated binary");
            for(String header:List.of(base.replace("NRRD0005","INVALID"),base.replace("1 1 1","0 1 1"),base.replace("uint16","float"),base.replace("raw","bzip2"),base.replace("endian: little\n",""),base.replace("\n\n","\ndata file: missing.raw\n\n"),base.replace("\n\n","\nbyte skip: 2\n\n"),base.replace("\n\n","\nspace directions: (1,0,0) (2,0,0) (0,0,1)\n\n"))) {
                Files.write(bad,concat(header.getBytes(),new byte[2])); reject(()->MRIReader.loadMRI(bad),"invalid NRRD header");
            }
            Files.writeString(bad,"NRRD0005\ntype: uint8"); reject(()->MRIReader.loadMRI(bad),"unterminated header");
            DatasetGenerator.write(bad,"uint8","1 1 1","ascii","little","","\n",new long[]{256},8);
            reject(()->MRIReader.loadMRI(bad),"ASCII range");
            reject(()->Projection.run(new String[0]),"missing arguments");
            reject(()->Projection.run(new String[]{"a","b","out.png"}),"wrong extension");
            System.out.println("PASS: "+checks+" checks (loading, transforms, analytic projections, malformed inputs, display)");
        } finally {try(var paths=Files.walk(temp)) {for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);}}
    }
    private static byte[] concat(byte[] a,byte[] b) {byte[] c=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,c,a.length,b.length);return c;}
}
