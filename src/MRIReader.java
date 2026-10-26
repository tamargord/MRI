import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

/** Reader for attached, scalar 3D integer NRRDs (raw, gzip, or ASCII). */
public final class MRIReader {
    private MRIReader() { }
    public static Volume loadMRI(String filename) throws IOException { return loadMRI(Path.of(filename)); }
    public static Volume loadMRI(Path path) throws IOException {
        try (InputStream input=new BufferedInputStream(Files.newInputStream(path))) {
            Map<String,String> fields=readHeader(input);
            if (!required(fields,"dimension").equals("3")) throw new IOException("Only scalar 3D NRRD files are supported");
            for(String unsupported: List.of("data file","datafile","block size"))
                if(fields.containsKey(unsupported)) throw new IOException("Unsupported NRRD field: "+unsupported);
            for(String skip: List.of("byte skip","byteskip","line skip","lineskip"))
                if(fields.containsKey(skip) && !fields.get(skip).equals("0")) throw new IOException("Nonzero NRRD skips are unsupported");
            if(fields.containsKey("space dimension") && !fields.get("space dimension").equals("3"))
                throw new IOException("Only three-dimensional coordinate spaces are supported");
            if(fields.containsKey("kinds")) {
                String[] kinds=fields.get("kinds").split("\\s+");
                if(kinds.length!=3 || Arrays.stream(kinds).anyMatch(k->!k.equals("domain") && !k.equals("space")))
                    throw new IOException("Only three spatial/domain axes are supported");
            }
            String[] dimensions=required(fields,"sizes").split("\\s+");
            if(dimensions.length!=3) throw new IOException("Expected three NRRD sizes");
            int[] sizes=Arrays.stream(dimensions).mapToInt(Integer::parseInt).toArray();
            for(int size:sizes) if(size<1 || size>=1024) throw new IOException("NRRD dimensions must be in 1..1023");
            int count=Math.multiplyExact(Math.multiplyExact(sizes[0],sizes[1]),sizes[2]);
            String type=required(fields,"type").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
            int bits; boolean signed;
            switch(type) {
                case "uchar","unsigned char","uint8","uint8_t" -> {bits=8;signed=false;}
                case "signed char","int8","int8_t" -> {bits=8;signed=true;}
                case "ushort","unsigned short","unsigned short int","uint16","uint16_t" -> {bits=16;signed=false;}
                case "short","short int","signed short","signed short int","int16","int16_t" -> {bits=16;signed=true;}
                case "uint","unsigned int","uint32","uint32_t" -> {bits=32;signed=false;}
                case "int","signed int","int32","int32_t" -> {bits=32;signed=true;}
                default -> throw new IOException("Unsupported NRRD scalar type: "+type);
            }
            Point3D origin=fields.containsKey("space origin") ? parseVector(fields.get("space origin")) : new Point3D(0,0,0);
            Point3D[] directions={new Point3D(1,0,0),new Point3D(0,1,0),new Point3D(0,0,1)};
            if(fields.containsKey("space directions")) {
                Matcher m=Pattern.compile("\\([^()]+\\)").matcher(fields.get("space directions"));
                int n=0; StringBuilder matches=new StringBuilder();
                while(m.find()) { if(n==3) throw new IOException("Expected three space directions"); directions[n++]=parseVector(m.group()); matches.append(m.group()); }
                if(n!=3 || !matches.toString().replaceAll("\\s+","").equals(fields.get("space directions").replaceAll("\\s+","")))
                    throw new IOException("Expected three spatial direction vectors");
            } else if(fields.containsKey("spacings")) {
                String[] spacing=fields.get("spacings").split("\\s+");
                if(spacing.length!=3) throw new IOException("Expected three spacings");
                for(int i=0;i<3;i++) directions[i]=directions[i].times(Double.parseDouble(spacing[i]));
            }
            String encoding=required(fields,"encoding").toLowerCase(Locale.ROOT);
            boolean ascii=Set.of("ascii","text","txt").contains(encoding);
            boolean little=true;
            if(!ascii && bits>8) {
                String endian=required(fields,"endian").toLowerCase(Locale.ROOT);
                if(!endian.equals("little") && !endian.equals("big")) throw new IOException("Invalid NRRD endian: "+endian);
                little=endian.equals("little");
            }
            InputStream payload=switch(encoding) {
                case "raw","ascii","text","txt" -> input;
                case "gzip","gz" -> new GZIPInputStream(input);
                default -> throw new IOException("Unsupported NRRD encoding: "+encoding);
            };
            Object data=switch(bits) {case 8 -> new byte[count]; case 16 -> new short[count]; default -> new int[count];};
            try(payload) {
                if(ascii) {
                    TokenReader tokens=new TokenReader(payload);
                    long min=signed ? -(1L<<(bits-1)) : 0;
                    long max=signed ? (1L<<(bits-1))-1 : (1L<<bits)-1;
                    for(int i=0;i<count;i++) {
                        String token=tokens.next();
                        if(token==null) throw new EOFException("Incomplete NRRD ASCII data");
                        long value=Long.parseLong(token);
                        if(value<min || value>max) throw new IOException("NRRD value outside declared scalar range");
                        store(data,bits,i,value);
                    }
                    if(tokens.next()!=null) throw new IOException("Extra NRRD ASCII samples");
                } else {
                    // Decode in chunks: no extra volume-sized byte buffer for 16/32-bit data.
                    byte[] chunk=new byte[65536]; int bytes=bits/8;
                    for(int offset=0;offset<count;) {
                        int samples=Math.min(chunk.length/bytes,count-offset), length=samples*bytes;
                        if(payload.readNBytes(chunk,0,length)!=length) throw new EOFException("Incomplete NRRD binary data");
                        for(int j=0;j<samples;j++) {
                            long value=0;
                            for(int k=0;k<bytes;k++) value |= (long)(chunk[j*bytes+k]&255) << (8*(little?k:bytes-1-k));
                            store(data,bits,offset+j,value);
                        }
                        offset+=samples;
                    }
                    if(payload.read()!=-1) throw new IOException("Extra NRRD binary data");
                }
            }
            return new Volume(sizes[0],sizes[1],sizes[2],bits,signed,data,origin,directions);
        } catch(IllegalArgumentException | ArithmeticException e) { throw new IOException("Invalid NRRD: "+e.getMessage(),e); }
    }
    private static void store(Object data,int bits,int i,long value) {
        switch(bits) {case 8 -> ((byte[])data)[i]=(byte)value; case 16 -> ((short[])data)[i]=(short)value; case 32 -> ((int[])data)[i]=(int)value; default -> throw new AssertionError();}
    }
    private static String required(Map<String,String> fields,String name) throws IOException {
        String value=fields.get(name); if(value==null || value.isBlank()) throw new IOException("Missing NRRD field: "+name); return value;
    }
    private static Map<String,String> readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream line=new ByteArrayOutputStream(); List<String> lines=new ArrayList<>();
        boolean ended=false;
        for(int n=0;n<1_048_576;n++) {
            int ch=input.read(); if(ch==-1) break;
            if(ch=='\n') {
                String s=line.toString(StandardCharsets.US_ASCII).replaceFirst("\\r$",""); line.reset();
                if(s.isEmpty()) {ended=true;break;} lines.add(s);
            } else line.write(ch);
        }
        if(!ended) throw new IOException("Missing NRRD header terminator or header exceeds 1 MiB");
        if(lines.isEmpty() || !lines.get(0).matches("NRRD000[1-5]")) throw new IOException("Invalid NRRD magic/version");
        Map<String,String> fields=new HashMap<>();
        for(String s:lines.subList(1,lines.size())) {
            if(s.startsWith("#") || s.contains(":=")) continue;
            int colon=s.indexOf(':'); if(colon<1) throw new IOException("Invalid NRRD header line");
            String name=s.substring(0,colon).trim().toLowerCase(Locale.ROOT);
            if(fields.putIfAbsent(name,s.substring(colon+1).trim())!=null) throw new IOException("Duplicate NRRD field: "+name);
        }
        return fields;
    }
    private static Point3D parseVector(String s) throws IOException {
        if(!s.startsWith("(") || !s.endsWith(")")) throw new IOException("Invalid spatial vector");
        String[] v=s.substring(1,s.length()-1).split(",",-1);
        if(v.length!=3) throw new IOException("Expected three vector components");
        return new Point3D(Double.parseDouble(v[0].trim()),Double.parseDouble(v[1].trim()),Double.parseDouble(v[2].trim()));
    }
    private static final class TokenReader {
        private final InputStream input;
        TokenReader(InputStream input) {this.input=input;}
        String next() throws IOException {
            int c; do {c=input.read();} while(c>=0 && Character.isWhitespace(c));
            if(c<0) return null;
            StringBuilder s=new StringBuilder();
            do {if(s.length()>64) throw new IOException("Invalid ASCII sample"); s.append((char)c); c=input.read();} while(c>=0 && !Character.isWhitespace(c));
            return s.toString();
        }
    }
}
