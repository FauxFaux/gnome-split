/*
 * MergeViewAction.java
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
package org.gnome.split.gtk.action;

import static org.freedesktop.bindings.Internationalization._;
import static org.gnome.split.GnomeSplit.ui;

import org.gnome.gtk.RadioGroup;
import org.gnome.gtk.ToggleAction;
import org.gnome.split.gtk.widget.ActionWidget;
import org.gnome.split.gtk.widget.SplitWidget;

/**
 * Action to hide and show the merge widget.
 * 
 * @author Guillaume Mazoyer
 */
final class MergeViewAction extends RadioAction
{
    protected MergeViewAction(RadioGroup group) {
        super(group, "merge-view-action", _("_Merge"), false);
    }

    @Override
    public void onToggled(ToggleAction source) {
        // Get the current widget
        ActionWidget widget = ui.getActionWidget();

        // Show the merge widget
        if (widget instanceof SplitWidget) {
            ui.switchToMergeView();
        }
    }
}
