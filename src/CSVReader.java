import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Headerless CSV: one triangle or four-point region per nonempty line. */
public final class CSVReader {
    private CSVReader() {}

    /** One nonempty CSV row, containing either a valid plane or its validation error. */
    public static final class Row {
        public final int lineNumber;
        public final Plane plane;
        public final IOException error;

        private Row(int lineNumber, Plane plane, IOException error) {
            this.lineNumber = lineNumber;
            this.plane = plane;
            this.error = error;
        }
    }

    /** Strict API for callers that require every row to be valid. */
    public static List<Plane> readPlanes(String filename) throws IOException {
        List<Plane> planes = new ArrayList<>();
        for (Row row : readRows(filename)) {
            if (row.error != null) throw row.error;
            planes.add(row.plane);
        }
        return planes;
    }

    /** Demo API: catches row validation exceptions so later rows can still be rendered. */
    public static List<Row> readRows(String filename) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    String[] fields = line.split(",", -1);
                    if (fields.length != 9 && fields.length != 12) {
                        throw new IllegalArgumentException("Expected 9 or 12 coordinates");
                    }
                    Point3D[] points = new Point3D[fields.length / 3];
                    for (int i = 0; i < points.length; i++) {
                        double x = Double.parseDouble(fields[3 * i].trim());
                        double y = Double.parseDouble(fields[3 * i + 1].trim());
                        double z = Double.parseDouble(fields[3 * i + 2].trim());
                        points[i] = new Point3D(x, y, z);
                    }
                    rows.add(new Row(lineNumber, new Plane(points), null));
                } catch (IllegalArgumentException e) {
                    IOException error = new IOException(
                            "CSV line " + lineNumber + ": " + e.getMessage(), e);
                    rows.add(new Row(lineNumber, null, error));
                }
            }
        }
        if (rows.isEmpty()) {
            throw new IOException("CSV contains no planes");
        }
        return rows;
    }
}
