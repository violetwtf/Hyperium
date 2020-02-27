/*
 *       Copyright (C) 2018-present Hyperium <https://hyperium.cc/>
 *
 *       This program is free software: you can redistribute it and/or modify
 *       it under the terms of the GNU Lesser General Public License as published
 *       by the Free Software Foundation, either version 3 of the License, or
 *       (at your option) any later version.
 *
 *       This program is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *       GNU Lesser General Public License for more details.
 *
 *       You should have received a copy of the GNU Lesser General Public License
 *       along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cc.hyperium.utils;

import cc.hyperium.utils.staff.StaffSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.HttpUtil;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class StaffUtils {

    private static final HashMap<UUID, StaffSettings> STAFF_CACHE = new HashMap<>();
    private static final HashMap<UUID, StaffSettings> BOOSTER_CACHE = new HashMap<>();

    private static final String POG_URL = "http://localhost:6969/v0/staff-settings?type=";
    private static final int POG_VERSION = 1;

    public static boolean isStaff(UUID uuid) {
        return STAFF_CACHE.containsKey(uuid);
    }

    public static boolean isBooster(UUID uuid) {
        return BOOSTER_CACHE.containsKey(uuid);
    }

    public static DotColour getColor(UUID uuid) {
        // prioritize staff color
        if (STAFF_CACHE.containsKey(uuid)) {
            return STAFF_CACHE.get(uuid).getDotColour();
        }

        return BOOSTER_CACHE.get(uuid).getDotColour();
    }

    private static HashMap<UUID, StaffSettings> getStaff() throws IOException {
        return getStaffSettings("staff");
    }

    private static HashMap<UUID, StaffSettings> getBoosters() throws IOException {
        return getStaffSettings("boosters");
    }

    private static HashMap<UUID, StaffSettings> getStaffSettings(String type) throws IOException {
        HashMap<UUID, StaffSettings> staff = new HashMap<>();
        String content = HttpUtil.get(new URL(POG_URL + type));

        JsonParser parser = new JsonParser();
        JsonArray array = parser.parse(content).getAsJsonArray();

        int bound = array.size();
        for (int i = 0; i < bound; i++) {
            JsonObject item = array.get(i).getAsJsonObject();
            UUID uuid = UUID.fromString(item.get("uuid").getAsString());
            String colourStr = item.get("color").getAsString().toUpperCase(Locale.ENGLISH);
            DotColour colour = colourStr.equals("CHROMA") ? new DotColour(true, ChatColor.WHITE) : new DotColour(false, ChatColor.valueOf(colourStr));
            staff.put(uuid, new StaffSettings(colour));
        }

        return staff;
    }

    public static void clearCache() throws IOException {
        STAFF_CACHE.clear();
        STAFF_CACHE.putAll(getStaff());
        BOOSTER_CACHE.clear();
        BOOSTER_CACHE.putAll(getBoosters());
    }

    public static class DotColour {
        public boolean isChroma;
        public ChatColor baseColour;

        public DotColour(boolean isChroma, ChatColor baseColour) {
            this.isChroma = isChroma;
            this.baseColour = baseColour;
        }
    }
}
