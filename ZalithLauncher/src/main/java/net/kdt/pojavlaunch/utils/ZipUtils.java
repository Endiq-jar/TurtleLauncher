package net.kdt.pojavlaunch.utils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipUtils {
    /**
     * Gets an InputStream for a given ZIP entry, throwing an IOException if the ZIP entry does not
     * exist.
     * @param zipFile The ZipFile to get the entry from
     * @param entryPath The full path inside of the ZipFile
     * @return The InputStream provided by the ZipFile
     * @throws IOException if the entry was not found
     */
    public static InputStream getEntryStream(ZipFile zipFile, String entryPath) throws IOException{
        ZipEntry entry = zipFile.getEntry(entryPath);
        if(entry == null) throw new IOException("No entry in ZIP file: "+entryPath);
        return zipFile.getInputStream(entry);
    }

    /**
     * Extracts all files in a ZipFile inside of a given directory to a given destination directory
     * How to specify dirName:
     * If you want to extract all files in the ZipFile, specify ""
     * If you want to extract a single directory, specify its full path followed by a trailing /
     * @param zipFile The ZipFile to extract files from
     * @param dirName The directory to extract the files from
     * @param destination The destination directory to extract the files into
     * @throws IOException if it was not possible to create a directory or file extraction failed
     */
    public static void zipExtract(ZipFile zipFile, String dirName, File destination) throws IOException {
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

        // Resolved once up front - the zip-slip guard below compares every entry against it.
        // TurtleLauncher: this used to be `new File(destination, entryName.substring(dirNameLen))`
        // with no validation, so a zip entry named e.g. "../../../../data/..." could escape the
        // destination directory and write files anywhere the app can reach (the classic
        // "zip-slip" path-traversal). Every modpack/world/plugin import goes through here, so a
        // malicious archive was a real, reachable arbitrary-file-write. Entry paths that don't
        // stay inside the destination are now skipped.
        String canonicalDestinationPath = destination.getCanonicalFile().getPath();

        int dirNameLen = dirName.length();
        while(zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            String entryName = zipEntry.getName();
            if(!entryName.startsWith(dirName) || zipEntry.isDirectory()) continue;
            File zipDestination = new File(destination, entryName.substring(dirNameLen)).getCanonicalFile();
            // java.io.File has no startsWith() - compare the canonical paths as strings instead,
            // making sure we match on a full path segment ("/dest/evil" must not pass for "/dest").
            String zipDestinationPath = zipDestination.getPath();
            if(!zipDestinationPath.equals(canonicalDestinationPath)
                    && !zipDestinationPath.startsWith(canonicalDestinationPath + File.separator)) {
                continue; // path traversal attempt - never write outside the destination
            }
            FileUtils.ensureParentDirectory(zipDestination);
            try (InputStream inputStream = zipFile.getInputStream(zipEntry);
                 OutputStream outputStream = new FileOutputStream(zipDestination)) {
                IOUtils.copy(inputStream, outputStream);
            }
        }
    }
}
