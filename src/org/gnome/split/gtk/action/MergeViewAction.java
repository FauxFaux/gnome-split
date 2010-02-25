/*
 * MergeViewAction.java
 * 
 * Copyright (c) 2009-2010 Guillaume Mazoyer
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
package org.gnome.split.gtk.action;

import org.gnome.gtk.RadioGroup;
import org.gnome.gtk.ToggleAction;
import org.gnome.split.GnomeSplit;
import org.gnome.split.gtk.MainWindow;
import org.gnome.split.gtk.widget.ActionWidget;
import org.gnome.split.gtk.widget.SplitWidget;

import static org.freedesktop.bindings.Internationalization._;

/**
 * Action to hide and show the merge widget.
 * 
 * @author Guillaume Mazoyer
 */
public final class MergeViewAction extends RadioAction
{
    public MergeViewAction(final GnomeSplit app, RadioGroup group) {
        super(app, group, "merge-view-action", _("_Merge"), false);
    }

    @Override
    public void onToggled(ToggleAction source) {
        // Get the current widget
        MainWindow window = this.getApplication().getMainWindow();
        ActionWidget widget = window.getActionWidget();

        // Show the merge widget
        if (widget instanceof SplitWidget) {
            window.switchToMergeView();
        }
    }
}