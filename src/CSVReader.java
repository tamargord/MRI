import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Headerless CSV: one triangle or four-point region per nonempty line. */
public final class CSVReader {
    private CSVReader() {}

    public static List<Plane> readPlanes(String filename) throws IOException {
        List<Plane> planes = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename))) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.trim().isEmpty()) continue;
                try {
                    String[] fields = line.split(",", -1);
                    if (fields.length != 9 && fields.length != 12)
                        throw new IllegalArgumentException("Expected 9 or 12 coordinates");
                    Point3D[] points = new Point3D[fields.length / 3];
                    for (int i = 0; i < points.length; i++) {
                        double x = Double.parseDouble(fields[3 * i].trim());
                        double y = Double.parseDouble(fields[3 * i + 1].trim());
                        double z = Double.parseDouble(fields[3 * i + 2].trim());
                        points[i] = new Point3D(x, y, z);
                    }
                    planes.add(new Plane(points));
                } catch (IllegalArgumentException e) {
                    throw new IOException("CSV line " + number + ": " + e.getMessage(), e);
                }
            }
        }
        if (planes.isEmpty()) throw new IOException("CSV contains no planes");
        return planes;
    }
}
