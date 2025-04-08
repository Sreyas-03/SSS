package com.sismics.util.mime;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * A collection of MIME types.
 *
 * @author jtremeaux 
 */
public class MimeType {

    public static final String IMAGE_X_ICON = "image/x-icon";
    
    public static final String IMAGE_PNG = "image/png";
    
    public static final String IMAGE_JPEG = "image/jpeg";
    
    public static final String IMAGE_GIF = "image/gif";
    
    public static final String APPLICATION_ZIP = "application/zip";

    public static String guessMimeType(File file) throws Exception {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            byte[] headerBytes = new byte[64];
            int readCount = is.read(headerBytes, 0, headerBytes.length);
            if (readCount <= 0) {
                throw new Exception("Cannot read input file");
            }
            String header = new String(headerBytes, "US-ASCII");
            
            if (header.startsWith("PK")) {
                return MimeType.APPLICATION_ZIP;
            } else if (header.startsWith("GIF87a") || header.startsWith("GIF89a")) {
                return MimeType.IMAGE_GIF;
            } else if (headerBytes[0] == ((byte) 0xff) && headerBytes[1] == ((byte) 0xd8)) {
                return MimeType.IMAGE_JPEG;
            } else if (headerBytes[0] == ((byte) 0x89) && headerBytes[1] == ((byte) 0x50) && headerBytes[2] == ((byte) 0x4e) && headerBytes[3] == ((byte) 0x47) &&
                    headerBytes[4] == ((byte) 0x0d) && headerBytes[5] == ((byte) 0x0a) && headerBytes[6] == ((byte) 0x1a) && headerBytes[7] == ((byte) 0x0a)) {
                return MimeType.IMAGE_PNG;
            } else if (headerBytes[0] == ((byte) 0x00) && headerBytes[1] == ((byte) 0x00) && headerBytes[2] == ((byte) 0x01) && headerBytes[3] == ((byte) 0x00)) {
                return MimeType.IMAGE_X_ICON;
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return null;
    }
}
