/*
 * DefaultSplitEngine.java
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

import static org.freedesktop.bindings.Internationalization._;
import static org.gnome.split.GnomeSplit.config;
import static org.gnome.split.GnomeSplit.engine;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.gnome.split.core.DefaultEngine;
import org.gnome.split.core.Engine;
import org.gnome.split.core.exception.EngineException;
import org.gnome.split.core.exception.InvalidSizeException;
import org.gnome.split.core.io.GRandomAccessFile;

/**
 * Define the model that all split engines should use.
 * 
 * @author Guillaume Mazoyer
 */
public abstract class DefaultSplitEngine extends DefaultEngine
{
    /**
     * The {@link File file} to split.
     */
    protected File file;

    /**
     * The maximum size of parts which will be created.
     */
    protected long size;

    /**
     * A part of the name of the files which will be created.
     */
    protected String destination;

    /**
     * A timer to update the progress of the view.
     */
    private Timer progress;

    /**
     * Create a new split {@link Engine engine} using a <code>file</code> to
     * split and a maximum <code>size</code> for each chunk.
     */
    protected DefaultSplitEngine(File file, long size, String destination) {
        super();
        this.directory = destination.substring(0, destination.lastIndexOf(File.separator));
        this.file = file;
        this.size = size;
        this.destination = destination;
        this.progress = null;
    }

    @Override
    public void run() {
        synchronized (mutex) {
            try {
                // Invalid size
                if (size == -1) {
                    throw new InvalidSizeException();
                }

                // Start the indicators
                this.startProgressUpdater();
                this.startSpeedCalculator();

                // Split the file
                this.split();
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
        return _("Splitting {0}", file.getName());
    }

    @Override
    public void stop(boolean clean) {
        super.stop(clean);

        if (clean) {
            // Remove all created parts
            for (String chunk : chunks) {
                new File(chunk).delete();
            }
        }
    }

    @Override
    public String getFilename() {
        return file.getAbsolutePath();
    }

    @Override
    public long getFileLength() {
        return file.length();
    }

    /**
     * Get a filename for the current chunk using the file number.
     */
    protected abstract String getChunkName(String destination, int number);

    /**
     * Split a file into smaller parts.
     */
    public abstract void split() throws IOException, EngineException;

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
     * Notify the view that a part has been created.
     */
    protected void fireEnginePartCreated(String filename) {
        engine.enginePartCreated(filename);
    }

    /**
     * Notify the view that a part has been written.
     */
    protected void fireEnginePartWritten(String filename) {
        engine.enginePartWritten(filename);
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
        engine.engineFilesList(chunks);
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
     * Write a chunk by reading the file to split and copying its content. It
     * returns <code>true</code> if the writing was fully performed, else it
     * returns <code>false</code>.
     */
    protected boolean writeChunk(GRandomAccessFile split, GRandomAccessFile chunk) throws IOException {
        // Needed variables to know when the chunk writing must be stopped
        int bufferSize = config.BUFFER_SIZE;
        long read = 0;
        byte[] buffer = null;

        while (read < size) {
            if (paused) {
                try {
                    // Pause the current thread
                    mutex.wait();
                } catch (InterruptedException e) {
                    // Drop this exception
                }
            }

            if (stopped) {
                // Stop the current thread
                this.fireEngineStopped();
                return false;
            }

            // Define a new buffer size
            buffer = new byte[(bufferSize > (size - read) ? (int) (size - read) : bufferSize)];

            // Read and write data
            split.read(buffer);
            chunk.write(buffer);

            // Update read and write status
            read += buffer.length;
            total += buffer.length;
        }

        // Success
        return true;
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
            fireEngineDone(total, file.length());
        }
    }
}
