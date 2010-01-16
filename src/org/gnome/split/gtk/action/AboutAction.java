/*
 * AboutAction.java
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

import org.gnome.gtk.Stock;
import org.gnome.split.GnomeSplit;

/**
 * Action to popup the classic GTK+ About dialog.
 * 
 * @author Guillaume Mazoyer
 */
public final class AboutAction extends Action
{
    public AboutAction(final GnomeSplit app) {
        super(app, "about-action", Stock.ABOUT);
    }

    @Override
    public void onActivate(org.gnome.gtk.Action source) {
        this.getApplication().getMainWindow().getAboutDialog().present();
    }
}
