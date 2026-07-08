/*
 * esochan (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.esoc.esochan.common;

import android.content.Context;
import android.content.Intent;

public final class InternalBroadcasts {
    private InternalBroadcasts() {}

    public static Intent intent(Context context, String action) {
        return new Intent(action).setPackage(context.getPackageName());
    }

    public static void send(Context context, String action) {
        context.sendBroadcast(intent(context, action));
    }

    public static void send(Context context, Intent intent) {
        if (intent.getPackage() == null && intent.getComponent() == null) {
            intent.setPackage(context.getPackageName());
        }
        context.sendBroadcast(intent);
    }
}
