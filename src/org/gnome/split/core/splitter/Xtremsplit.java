/*
 * Xtremsplit.java
 * 
 * Copyright (c) 2009-2013 Guillaume Mazoyer
 * 
 * This file is part of GNOME Split.
 * 
 * GNOME Split is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * GNOME Split is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNOME Split.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gnome.split.core.splitter;

import static org.gnome.split.GnomeSplit.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.gnome.split.config.Constants;
import org.gnome.split.core.io.GRandomAccessFile;
import org.gnome.split.core.utils.ByteUtils;
import org.gnome.split.core.utils.MD5Hasher;
import org.gnome.split.core.utils.Utils;

/**
 * Algorithm to split a file with the Xtremsplit algorithm.
 * 
 * @author Guillaume Mazoyer
 */
public final class Xtremsplit extends DefaultSplitEngine
{
    private int parts;

    public Xtremsplit(File file, long size, String destination) {
        super(file, size, destination);
        parts = (int) Math.ceil((float) file.length() / (float) size);
    }

    /**
     * Write the xtm header at the beginning of a file.
     */
    private void writeHeaders(GRandomAccessFile access) throws IOException {
        byte[] toWrite;

        // Write program name
        toWrite = Constants.PROGRAM_NAME.getBytes();
        access.writeByte(toWrite.length);
        access.write(toWrite);
        for (int i = toWrite.length; i < 20; i++) {
            access.write(0);
        }

        // Write program version
        toWrite = Constants.PROGRAM_VERSION.getBytes();
        access.writeByte(toWrite.length);
        access.write(toWrite);
        for (int i = toWrite.length; i < 14; i++) {
            access.write(0);
        }

        // Write date
        double date = Utils.datetimeFromNow();
        access.write(ByteUtils.toLittleEndian(date));

        // Write original filename and truncate it if needed
        toWrite = (file.getName().length() > 50) ? file.getName().substring(0, 50).getBytes()
                : file.getName().getBytes();
        access.writeByte(toWrite.length);
        access.write(toWrite);

        // Fill the left space of the field if needed
        if (toWrite.length < 50) {
            for (int i = toWrite.length; i < 50; i++) {
                access.write(0);
            }
        }

        // Write if using MD5
        access.writeBoolean(config.SAVE_FILE_HASH);

        // Write number of files
        access.write(ByteUtils.toLittleEndian(parts));

        // Write file size
        access.write(ByteUtils.toLittleEndian(file.length()));
    }

    @Override
    protected String getChunkName(String destination, int number) {
        // Get the current extension
        String current;
        if (number >= 100) {
            current = String.valueOf(number);
        } else if (number >= 10) {
            current = "0" + number;
        } else {
            current = "00" + number;
        }

        // Finally
        return (destination + "." + current + ".xtm");
    }

    @Override
    public void split() throws IOException, FileNotFoundException {
        GRandomAccessFile toSplit = null;
        boolean run = true;
        try {
            // Open a new file
            toSplit = new GRandomAccessFile(file, "r");

            // Used for the MD5 calculation
            StringBuilder md5sum = null;
            MD5Hasher md5hasher = null;

            // Use it only if the MD5 should be calculated
            if (config.SAVE_FILE_HASH) {
                md5sum = new StringBuilder();
                md5hasher = new MD5Hasher();
            }

            for (int i = 1; i <= parts; i++) {
                GRandomAccessFile access = null;
                File chunk = null;
                try {
                    // Open the part
                    chunk = new File(this.getChunkName(destination, i));
                    access = new GRandomAccessFile(chunk, "rw");

                    // Notify the view from a new part
                    chunks.add(chunk.getAbsolutePath());
                    this.fireEnginePartCreated(chunk.getName());

                    if (i == 1) {
                        // Write header on the first part
                        this.writeHeaders(access);
                    } else if (i == parts) {
                        // Update size to stop the split correctly
                        size = file.length() - total;
                    }

                    // Write the chunk
                    run = this.writeChunk(toSplit, access);

                    // Writing stopped
                    if (!run) {
                        return;
                    }

                    // Should we save MD5 sum?
                    if (config.SAVE_FILE_HASH) {
                        // Calculate the MD5 sum
                        this.fireMD5SumStarted();
                        String md5 = md5hasher.hashToString(chunk);
                        this.fireMD5SumEnded();

                        // Append it to the other
                        md5sum.append(md5);

                        // Write all the MD5 sums at the end of the last file
                        if (i == parts) {
                            access.write(md5sum.toString().getBytes());
                        }
                    }

                    // Notify the view from a written part
                    this.fireEnginePartWritten(chunk.getName());
                } finally {
                    try {
                        // Close the part file
                        access.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Notify the end of the split
            this.fireEngineEnded();
        } finally {
            try {
                // Close the part file
                toSplit.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
