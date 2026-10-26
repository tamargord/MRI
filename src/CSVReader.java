
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

    public class CSVReader {

        public static ArrayList<Point3D[]> readPlanes(
                String filename) throws Exception {

            ArrayList<Point3D[]> planes = new ArrayList<>();

            BufferedReader reader =
                    new BufferedReader(new FileReader(filename));

            String line;

            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                String[] values = line.split(",");

                if (values.length != 9 && values.length != 12) {
                    reader.close();
                    throw new IllegalArgumentException(
                            "Each row must have 9 or 12 numbers"
                    );
                }

                int numberOfPoints = values.length / 3;

                Point3D[] points = new Point3D[numberOfPoints];

                for (int i = 0; i < numberOfPoints; i++) {

                    double x = Double.parseDouble(
                            values[i * 3].trim());

                    double y = Double.parseDouble(
                            values[i * 3 + 1].trim());

                    double z = Double.parseDouble(
                            values[i * 3 + 2].trim());

                    points[i] = new Point3D(x, y, z);
                }

                planes.add(points);
            }

            reader.close();

            return planes;
        }
    }


