
import java.io.*;
import java.util.zip.GZIPInputStream;

    public class MRIReader {

        public static short[][][] loadMRI(String filename)
                throws IOException {

            FileInputStream file = new FileInputStream(filename);
            BufferedInputStream input = new BufferedInputStream(file);

            // Step 1: Skip the text header
            int previous = -1;
            int current;

            while ((current = input.read()) != -1) {
                if (previous == '\n' && current == '\n') {
                    break;
                }
                previous = current;
            }

            if (current == -1) {
                throw new IOException("Invalid NRRD header");
            }

            // Step 2: Decompress the image data
            GZIPInputStream gzip = new GZIPInputStream(input);

            // Dimensions from your MRI header
            int sizeX = 256;
            int sizeY = 256;
            int sizeZ = 130;

            short[][][] M = new short[sizeX][sizeY][sizeZ];

            // Step 3: Read the MRI voxel values
            // NRRD stores the first axis fastest
            for (int z = 0; z < sizeZ; z++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int x = 0; x < sizeX; x++) {

                        int low = gzip.read();
                        int high = gzip.read();

                        if (low == -1 || high == -1) {
                            throw new EOFException("Incomplete MRI data");
                        }

                        // Two bytes form one signed short
                        M[x][y][z] =
                                (short) ((high << 8) | low);
                    }
                }
            }

            gzip.close();
            return M;
        }
    }


