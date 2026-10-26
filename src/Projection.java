import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Assignment scan-line projection and three-argument command-line demo. */
public final class Projection {
    private Projection() { }
    public static long[][] project(Volume volume, Point3D... points) { return project(volume,new Plane(points)); }
    public static long[][] project(Volume volume, Plane plane) {
        long[][] image=new long[plane.height][plane.width];
        Point3D edge1=plane.b.minus(plane.a1).times(1/plane.length);
        Point3D edge2=plane.c.minus(plane.a2).times(1/plane.otherLength);
        for(int y=0;y<plane.height;y++) {
            Point3D left=plane.a1.plus(edge1.times(Math.min(y+1.0,plane.length)));
            Point3D right=plane.a2.plus(edge2.times(Math.min(y+1.0,plane.otherLength)));
            Point3D across=right.minus(left); double span=across.length();
            Point3D voxel=volume.worldToVoxel(left);
            if(span<1e-9) {image[y][plane.width/2]=volume.sample(voxel);continue;}
            Point3D step=volume.worldVectorToVoxel(across.times(1/span));
            // k <= span, as in the assignment; tolerance repairs numerical error at integer endpoints.
            int last=(int)Math.floor(span+1e-9);
            double start=(plane.base-span)/2;
            for(int k=0;k<=last;k++) {
                int x=(int)Math.round(start+k);
                if(x>=0 && x<plane.width) image[y][x]=volume.sample(voxel.x()+k*step.x(),voxel.y()+k*step.y(),voxel.z()+k*step.z());
            }
        }
        return image;
    }
    public static BufferedImage grayscale(long[][] values,int width,int height) {
        if(values.length==0 || values[0].length==0 || width<values[0].length || height<values.length)
            throw new IllegalArgumentException("Canvas must contain the projection");
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_BYTE_GRAY);
        long maximum=0;
        for(long[] row:values) for(long value:row) maximum=Math.max(maximum,value);
        int xOffset=(width-values[0].length)/2,yOffset=(height-values.length)/2;
        for(int y=0;y<values.length;y++) for(int x=0;x<values[y].length;x++) {
            int gray=maximum==0 ? 0 : (int)Math.round(Math.max(0,values[y][x])*255.0/maximum);
            image.getRaster().setSample(x+xOffset,y+yOffset,0,gray);
        }
        return image;
    }
    public static void saveImage(long[][] values,String filename) throws IOException {
        ImageIO.write(grayscale(values,values[0].length,values.length),"png",Path.of(filename).toFile());
    }
    public static void run(String[] args) throws IOException,InterruptedException {
        if(args.length!=3) throw new IllegalArgumentException("Usage: java -cp out Projection input.nrrd planes.csv output.mp4");
        Path output=Path.of(args[2]).toAbsolutePath();
        if(!output.toString().toLowerCase(Locale.ROOT).endsWith(".mp4")) throw new IllegalArgumentException("Output must have an .mp4 extension");
        Path mri=Path.of(args[0]).toAbsolutePath(),csv=Path.of(args[1]).toAbsolutePath();
        if(output.normalize().equals(mri.normalize()) || output.normalize().equals(csv.normalize())) throw new IllegalArgumentException("Output must not overwrite an input");
        if(!Files.isDirectory(output.getParent())) throw new IOException("Output directory does not exist: "+output.getParent());
        List<Plane> planes=CSVReader.readPlanes(args[1]);
        Volume volume=MRIReader.loadMRI(args[0]);
        int width=0,height=0;
        for(Plane plane:planes) {width=Math.max(width,plane.width);height=Math.max(height,plane.height);}
        width+=(width%2);height+=(height%2);
        if((long)width*height>16_000_000) throw new IOException("Common video canvas exceeds 16 million pixels");
        // Unique directory prevents old numbered PNGs from becoming frames in a later run.
        Path directory=Files.createTempDirectory("mri-projection-");
        Path encoded=null;
        try {
            long projectionNanos=0;
            for(int i=0;i<planes.size();i++) {
                long start=System.nanoTime(); long[][] image=project(volume,planes.get(i)); projectionNanos+=System.nanoTime()-start;
                Path frame=directory.resolve(String.format(Locale.ROOT,"frame_%06d.png",i));
                if(!ImageIO.write(grayscale(image,width,height),"png",frame.toFile())) throw new IOException("PNG writer unavailable");
            }
            encoded=Files.createTempFile(output.getParent(),"mri-video-",".mp4");
            String ffmpeg=System.getenv("FFMPEG"); if(ffmpeg==null || ffmpeg.isBlank()) ffmpeg="ffmpeg";
            Path log=directory.resolve("ffmpeg.log");
            Process process;
            try {
                process=new ProcessBuilder(ffmpeg,"-hide_banner","-loglevel","error","-y","-framerate","30","-start_number","0","-i",directory.resolve("frame_%06d.png").toString(),"-frames:v",Integer.toString(planes.size()),"-c:v","libx264","-pix_fmt","yuv420p","-movflags","+faststart",encoded.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            } catch(IOException e) {throw new IOException("Cannot start FFmpeg; install it on PATH or set FFMPEG to its executable path",e);}
            int status;
            try { status=process.waitFor(); } catch(InterruptedException e) {process.destroyForcibly(); process.waitFor();throw e;}
            if(status!=0) throw new IOException("FFmpeg failed (exit "+status+"): "+Files.readString(log));
            Files.move(encoded,output,StandardCopyOption.REPLACE_EXISTING);
            System.out.printf(Locale.ROOT,"Created %s: %d frames, %dx%d, 30 playback FPS%n",output,planes.size(),width,height);
            System.out.printf(Locale.ROOT,"Projection-only total: %.3f ms (unwarmed demo timing; use Benchmark for throughput)%n",projectionNanos/1e6);
        } finally {
            if(encoded!=null) Files.deleteIfExists(encoded);
            try(var files=Files.list(directory)) {for(Path file:files.toList()) Files.deleteIfExists(file);}
            Files.deleteIfExists(directory);
        }
    }
    public static void main(String[] args) {
        try {run(args);} catch(Exception e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("Error: "+e.getMessage()); System.exit(1);
        }
    }
}
