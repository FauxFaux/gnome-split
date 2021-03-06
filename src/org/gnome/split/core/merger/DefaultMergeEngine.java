/*
 * DefaultMergeEngine.java
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
package org.gnome.split.core.merger;

import static org.freedesktop.bindings.Internationalization._;
import static org.gnome.split.GnomeSplit.config;
import static org.gnome.split.GnomeSplit.engine;
import static org.gnome.split.GnomeSplit.openURI;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.gnome.split.core.DefaultEngine;
import org.gnome.split.core.Engine;
import org.gnome.split.core.exception.EngineException;
import org.gnome.split.core.io.GRandomAccessFile;
import org.gnome.split.core.utils.Algorithm;

/**
 * Define the model that all merge engines should use.
 * 
 * @author Guillaume Mazoyer
 */
public abstract class DefaultMergeEngine extends DefaultEngine
{
    /**
     * The first part to merge.
     */
    protected File file;

    /**
     * The name of the file to create.
     */
    protected String filename;

    /**
     * The total length of the file.
     */
    protected long fileLength;

    /**
     * The number of parts to merge.
     */
    protected int parts;

    /**
     * If the merge should use an MD5 sum or not.
     */
    protected boolean md5;

    /**
     * The MD5 sum if it used.
     */
    protected String md5sum;

    /**
     * A timer to update the progress of the view.
     */
    private Timer progress;

    /**
     * Create a new merge {@link Engine engine} using a first
     * <code>file</code> to merge.
     */
    protected DefaultMergeEngine(File file, String filename) {
        super();
        this.file = file;
        this.filename = filename;
        this.progress = null;

        if (filename != null) {
            this.directory = filename.substring(0, filename.lastIndexOf(File.separator));
        } else {
            this.directory = config.MERGE_DIRECTORY;
        }

        try {
            // Load headers
            this.loadHeaders();
        } catch (Exception e) {
            // Handle the error
            this.fireEngineError(e);
        }
    }

    /**
     * Return the right merger to merge files with right algorithm.
     */
    public static final DefaultMergeEngine getInstance(File file, String filename) {
        String name = file.getName();
        String[] extensions = Algorithm.getExtensions();

        if (name.endsWith(extensions[0]) || name.endsWith(extensions[1])) {
            // Use Generic algorithm
            return new Generic(file, filename);
        }

        if (name.endsWith(extensions[2])) {
            // Use GNOME Split algorithm
            return new GnomeSplit(file, filename);
        }

        if (name.endsWith(extensions[3]) || name.endsWith(extensions[4])) {
            // Use Xtremsplit algorithm
            return new Xtremsplit(file, filename);
        }

        if (name.endsWith(extensions[5])) {
            // Use KFK algorithm
            return new KFK(file, filename);
        }

        if (name.endsWith(extensions[6])) {
            // Use YoyoCut algorithm
            return new YoyoCut(file, filename);
        }

        // Can't find the right algorithm
        return null;
    }

    @Override
    public void run() {
        synchronized (mutex) {
            try {
                // Start the indicators
                this.startProgressUpdater();
                this.startSpeedCalculator();

                // Merge files
                this.merge();

                // Execute action after the merge
                this.onFinish();
            } catch (Exception e) {
                // Handle the error
                this.fireEngineError(e);
            } finally {
                // Stop the indicators
                this.stopProgressUpdater();
                this.stopSpeedCalculator();
            }
        }
    }

    @Override
    public String toString() {
        String name = filename.substring((filename.lastIndexOf('/') + 1), filename.length());
        return _("Merging {0}", name);
    }

    @Override
    public void stop(boolean clean) {
        super.stop(clean);

        if (clean) {
            // Remove the created file
            new File(filename).delete();
        }
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getFileLength() {
        return fileLength;
    }

    /**
     * Load the headers of the files to merge.
     */
    protected abstract void loadHeaders() throws IOException;

    /**
     * Get the next name of the file to merge.
     */
    protected abstract String getNextChunk(String part, int number);

    /**
     * Merge files to get a new one.
     */
    public abstract void merge() throws IOException, EngineException;

    /**
     * Executed at the end of a successful merge.
     */
    private void onFinish() {
        if (config.OPEN_FILE_AT_END) {
            // Open the created file if requested
            openURI("file://" + filename);
        }
    }

    /**
     * Start the progress updater which should notify the view from the
     * progress of the action.
     */
    private void startProgressUpdater() {
        // Create a new timer and start its task
        progress = new Timer("Progress updater");
        progress.scheduleAtFixedRate(new ProgressUpdaterTask(), 1, 250);
    }

    /**
     * Stop the progress updater.
     */
    private void stopProgressUpdater() {
        // Stop the timer
        if (progress != null) {
            progress.cancel();
            progress = null;
        }
    }

    /**
     * Notify the view that a part is being read.
     */
    protected void fireEnginePartRead(String filename) {
        engine.enginePartRead(filename);
    }

    /**
     * Notify the view that the MD5 sum calculation has started.
     */
    protected void fireMD5SumStarted() {
        this.stopProgressUpdater();
        engine.engineMD5SumStarted();
    }

    /**
     * Notify the view that the MD5 sum calculation has ended.
     */
    protected void fireMD5SumEnded() {
        this.startProgressUpdater();
        engine.engineMD5SumEnded();
    }

    /**
     * Notify the view that the engine has finish its work.
     */
    protected void fireEngineEnded() {
        engine.engineEnded();

        List<String> list = new ArrayList<String>();
        list.add(filename);
        engine.engineFilesList(list);
    }

    /**
     * Notify the view that the engine has been stopped.
     */
    protected void fireEngineStopped() {
        engine.engineStopped();
    }

    /**
     * Notify the view that an error has occurred.
     */
    protected void fireEngineError(Exception exception) {
        engine.engineError(exception);
    }

    /**
     * Notify the view that a part of the file has been read.
     */
    protected void fireEngineDone(long done, long total) {
        engine.engineDone(done, total);
    }

    /**
     * Merge a chunk into another file by copying its content. The
     * <code>read</code> parameter is used to know how many bytes have been
     * already read. The <code>length</code> parameter is used to know the
     * maximum number of bytes that can be read. It returns <code>true</code>
     * if the reading was fully performed, else it returns <code>false</code>.
     */
    protected boolean mergeChunk(GRandomAccessFile merge, GRandomAccessFile chunk, long read, long length)
            throws IOException {
        // Setup the buffer
        int bufferSize = config.BUFFER_SIZE;
        byte[] buffer = null;

        // Merge the file
        while (read < length) {
            if (paused) {
                try {
                    // Pause the current thread
                    mutex.wait();
                } catch (InterruptedException e) {
                    // Drop the exception
                }
            }

            if (stopped) {
                // Stop the current thread
                this.fireEngineStopped();
                return false;
            }

            // Define a new buffer size
            buffer = new byte[(bufferSize > (length - read) ? (int) (length - read) : bufferSize)];

            // Read and write data
            chunk.read(buffer);
            merge.write(buffer);

            // Update read and write status
            read += buffer.length;
            total += buffer.length;
        }

        // Success
        return true;
    }

    /**
     * Get the number of parts to merge.
     */
    public int getChunksNumber() {
        return parts;
    }

    /**
     * Tell if whether or not the merge will use a MD5 sum to control the file
     * integrity.
     */
    public boolean useMD5() {
        return md5;
    }

    /**
     * A class that notify the view from the progress of the action.
     * 
     * @author Guillaume Mazoyer
     */
    private class ProgressUpdaterTask extends TimerTask
    {
        private ProgressUpdaterTask() {

        }

        @Override
        public void run() {
            fireEngineDone(total, fileLength);
        }
    }
}
