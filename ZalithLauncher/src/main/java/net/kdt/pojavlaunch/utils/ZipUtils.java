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
     * TurtleLauncher SECURITY FIX (zip-slip): resolves a zip entry's relative path against a
     * destination directory, throwing if the result would land outside that directory - a
     * malicious modpack zip (MCBBS, MultiMC, CurseForge/Modrinth overrides, anything going
     * through this helper) could otherwise use an entry name like "../../../../data/data/
     * <pkg>/shared_prefs/evil.xml" to write outside the intended install location entirely.
     * Canonical paths are compared (not just string-prefixed raw paths) so "../" segments and
     * symlink components are actually resolved, not just pattern-matched.
     * @throws IOException if the entry would escape destination, or if either path can't be
     *                      canonicalized
     */
    public static File resolveSafeEntryPath(File destination, String relativePath) throws IOException {
        File target = new File(destination, relativePath);
        String destCanonical = destination.getCanonicalPath();
        String targetCanonical = target.getCanonicalPath();
        if (!targetCanonical.equals(destCanonical) && !targetCanonical.startsWith(destCanonical + File.separator)) {
            throw new IOException("Zip entry is outside of the target directory (zip-slip): " + relativePath);
        }
        return target;
    }

    /**
     * Extracts all files in a ZipFile inside of a given directory to a given destination directory
     * How to specify dirName:
     * If you want to extract all files in the ZipFile, specify ""
     * If you want to extract a single directory, specify its full path followed by a trailing /
     * @param zipFile The ZipFile to extract files from
     * @param dirName The directory to extract the files from
     * @param destination The destination directory to extract the files into
     * @throws IOException if it was not possible to create a directory or file extraction failed,
     *                      or if a zip entry would extract outside of destination (zip-slip)
     */
    public static void zipExtract(ZipFile zipFile, String dirName, File destination) throws IOException {
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

        int dirNameLen = dirName.length();
        while(zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            String entryName = zipEntry.getName();
            if(!entryName.startsWith(dirName) || zipEntry.isDirectory()) continue;
            File zipDestination = resolveSafeEntryPath(destination, entryName.substring(dirNameLen));
            FileUtils.ensureParentDirectory(zipDestination);
            try (InputStream inputStream = zipFile.getInputStream(zipEntry);
                 OutputStream outputStream = new FileOutputStream(zipDestination)) {
                IOUtils.copy(inputStream, outputStream);
            }
        }
    }
}
