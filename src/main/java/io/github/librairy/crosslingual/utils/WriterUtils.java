package io.github.librairy.crosslingual.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class WriterUtils {

    private static final Logger LOG = LoggerFactory.getLogger(WriterUtils.class);

    public static BufferedWriter to(String path) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(path))));
    }

}
