/*
 * GnomeSplit.java
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
package org.gnome.split;

import static java.lang.System.exit;
import static org.freedesktop.bindings.Internationalization._;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.freedesktop.bindings.Internationalization;
import org.gnome.glib.ApplicationCommandLine;
import org.gnome.glib.ApplicationFlags;
import org.gnome.glib.Glib;
import org.gnome.gtk.Application;
import org.gnome.gtk.ApplicationInhibitFlags;
import org.gnome.gtk.Gtk;
import org.gnome.notify.Notify;
import org.gnome.split.config.Configuration;
import org.gnome.split.config.Constants;
import org.gnome.split.core.EngineListener;
import org.gnome.split.core.utils.ShutdownHandler;
import org.gnome.split.core.utils.UncaughtExceptionLogger;
import org.gnome.split.gtk.DefaultEngineListener;
import org.gnome.split.gtk.UserInterface;
import org.gnome.split.gtk.action.ActionManager;
import org.gnome.split.gtk.action.ActionManager.ActionId;
import org.gnome.split.gtk.dialog.QuestionDialog;

/**
 * This class contains the GNOME Split application entry point.
 * 
 * @author Guillaume Mazoyer
 */
public final class GnomeSplit
{
    /**
     * Inhibition flags (no logout, no idle mode, no suspend).
     */
    private static ApplicationInhibitFlags inhibitFlags;

    /**
     * Inhibit cookie set when the application asks for computer inhibition.
     */
    private static int inhibitCookie;

    /**
     * Application instance to run.
     */
    public static Application app;

    /**
     * Configuration for the application.
     */
    public static Configuration config;

    /**
     * Application actions manager.
     */
    public static ActionManager actions;

    /**
     * Application main window.
     */
    public static UserInterface ui;

    /**
     * Engine listener to update the view.
     */
    public static EngineListener engine;

    private GnomeSplit() {
        // No instantiation from outside
    }

    /**
     * Load the configuration and preferences.
     */
    private static void loadConfig() {
        try {
            // Load constants and preferences
            Constants.load();
            config = new Configuration();
        } catch (IOException e) {
            e.printStackTrace();
            exit(1);
        }
    }

    /**
     * Build the GTK+ user interface.
     */
    private static void buildUserInterface() {
        // Load actions manager
        actions = new ActionManager();

        // Start the user interface
        ui = new UserInterface();
        ui.selectDefaultView();

        // Add the window to the underlining application model
        app.addWindow(ui);
    }

    /**
     * Change the view of the window, and update it using a filename. If no
     * filename is given (filename == null), the view will not be updated. 0
     * means split view and other means merge view.
     */
    private static void selectView(byte view, String filename) {
        // Choose split
        if (view == 0) {
            if (filename != null) {
                // Update the merge widget
                ui.getSplitWidget().setFile(filename);
            }

            // Show the merge widget
            actions.activateRadioAction(ActionId.SPLIT);
        } else {
            if (filename != null) {
                // Load the file to split
                ui.getMergeWidget().setFile(filename);
            }

            // Show the split widget
            actions.activateRadioAction(ActionId.MERGE);
        }
    }

    private static int run(String[] args) {
        int status;

        // Initialize uncaught exception handler
        new UncaughtExceptionLogger();

        // Start kill signals handler
        Runtime.getRuntime().addShutdownHook(new ShutdownHandler());

        // Load program name
        Glib.setProgramName(Constants.PROGRAM_NAME);

        // Load GTK
        Gtk.init(args);

        // Load config
        loadConfig();

        // Load logo
        Gtk.setDefaultIcon(Constants.PROGRAM_LOGO);

        // Load translations
        Internationalization.init("gnome-split", "share/locale/");

        // Load libnotify
        if (config.USE_NOTIFICATION) {
            Notify.init(Constants.PROGRAM_NAME);
        }

        // Build the application object
        app = new Application("org.gnome.split.GnomeSplit", ApplicationFlags.HANDLES_COMMAND_LINE);

        // Build the GUI on startup
        app.connect(new Application.Startup() {
            @Override
            public void onStartup(Application source) {
                // Build the user interface
                buildUserInterface();
            }
        });

        // Display the GUI when activated
        app.connect(new Application.Activate() {
            @Override
            public void onActivate(Application source) {
                // Load engine listener
                engine = new DefaultEngineListener();

                // Show the user interface
                ui.show();

                // Set the state of the interface
                engine.engineReady();

                if (config.ASSISTANT_ON_START) {
                    // Show the assistant on start if requested
                    actions.activateAction(ActionId.ASSISTANT);
                }
            }
        });

        // Handle command line arguments
        app.connect(new Application.CommandLine() {
            @Override
            public int onCommandLine(Application source, ApplicationCommandLine remote) {
                String[] args = remote.getArguments();

                if (args.length > 1) {
                    // Change the view
                    selectView((byte) ((args[1].equals("-s") || args[1].equals("--split")) ? 0 : 1),
                            (args.length > 2) ? args[2] : null);
                }

                // Trigger the Application.Activate signal
                app.activate();

                // Indicate that the remote instance that sent us the command
                // line arguments should terminate as soon as possible
                remote.exit();

                return 0;
            }
        });

        // Initialize the inhibit cookie
        inhibitFlags = ApplicationInhibitFlags.or(ApplicationInhibitFlags.IDLE,
                ApplicationInhibitFlags.LOGOUT, ApplicationInhibitFlags.SUSPEND);
        inhibitCookie = -1;

        // Fire the main loop (blocker)
        status = app.run(args);

        return status;
    }

    /**
     * Open the the URI with the default program.
     */
    public static void openURI(String uri) {
        try {
            Gtk.showURI(new URI(uri));
        } catch (URISyntaxException e) {
            // Should *never* happen
            e.printStackTrace();
        }
    }

    /**
     * This will cause the program to be ended.
     */
    public static void quit() {
        boolean quit = true;

        // An action is running
        if (!config.DO_NOT_ASK_QUIT && (engine.getEngine() != null)) {
            // Show a question to the user
            QuestionDialog dialog = new QuestionDialog(ui, _("Quit GNOME Split."),
                    _("An action is currently being performed. Do you really want to quit GNOME Split?"));

            // Get his response and hide the dialog
            quit = dialog.response();
            dialog.hide();
        }

        // The user really wants to quit
        if (quit) {
            // Hide the window immediately
            ui.hide();

            // Uninitialize the libnotify
            if (Notify.isInitialized()) {
                Notify.uninit();
            }

            // Quit the GTK application.
            app.removeWindow(ui);
            app.quit();
        }
    }

    /**
     * Don't let the computer go to sleep or shutdown until the application
     * has finished its work.
     */
    public static void inhibit() {
        inhibitCookie = app.inhibit(ui, inhibitFlags, _("GNOME Split activity"));
    }

    /**
     * Let know to the operating system that the application has finished its
     * work.
     */
    public static void unInhibit() {
        app.uninhibit(inhibitCookie);

        inhibitCookie = -1;
    }

    /**
     * Tells if the application called the method to inhibit the computer.
     */
    public static boolean isInhibited() {
        return ((inhibitCookie != -1) && app.isInhibited(inhibitFlags));
    }

    /**
     * Application entry point.
     */
    public static void main(String[] args) {
        int status = run(args);

        exit(status);
    }
}
