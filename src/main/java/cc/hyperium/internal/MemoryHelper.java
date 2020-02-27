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

package cc.hyperium.internal;

import cc.hyperium.Hyperium;
import cc.hyperium.event.InvokeEvent;
import cc.hyperium.event.client.TickEvent;
import cc.hyperium.event.world.WorldUnloadEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MemoryHelper {

    private List<ResourceLocation> locations = new ArrayList<>();
    private int tickCounter;

    private Field resourceCache;
    private Field packageManifests;

    public static MemoryHelper INSTANCE;

    public MemoryHelper() {
        INSTANCE = this;

        try {
            resourceCache = LaunchClassLoader.class.getDeclaredField("resourceCache");
            packageManifests = LaunchClassLoader.class.getDeclaredField("packageManifests");
        } catch (Exception e) {
            Hyperium.LOGGER.error("Failed to assign resourceCache/packageManifests", e);
        }

        try {
            resourceCache.setAccessible(true);
            packageManifests.setAccessible(true);
        } catch (Exception e) {
            Hyperium.LOGGER.error("Failed to set resourceCache/packageManifests to accessible", e);
        }
    }

    @InvokeEvent
    public void worldEvent(WorldUnloadEvent event) {
        try {
            TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
            Map<ResourceLocation, ITextureObject> mapTextureObjects = textureManager.getMapTextureObjects();
            List<ResourceLocation> removes = new ArrayList<>();

            for (Map.Entry<ResourceLocation, ITextureObject> entry : mapTextureObjects.entrySet()) {
                ResourceLocation key = entry.getKey();
                ITextureObject iTextureObject = entry.getValue();
                if (iTextureObject instanceof ThreadDownloadImageData) {
                    IImageBuffer imageBuffer = ((ThreadDownloadImageData) iTextureObject).getImageBuffer();

                    if (imageBuffer == null) continue;

                    Class<? extends IImageBuffer> aClass = imageBuffer.getClass();
                    // Optifine
                    if (aClass.getName().equalsIgnoreCase("CapeImageBuffer")) {
                        removes.add(key);
                    }
                }
            }

            for (ResourceLocation remove : removes) {
                deleteSkin(remove);
            }

            for (ResourceLocation location : locations) {
                deleteSkin(location);
            }

            int size = locations.size();
            locations.clear();

            int del = 0;

            RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
            try {
                Method getSkinMap = renderManager.getClass().getMethod("getSkinMap");
                Object invoke = getSkinMap.invoke(renderManager);
                Map<String, RenderPlayer> skinMap = (Map<String, RenderPlayer>) invoke;

                for (RenderPlayer value : skinMap.values()) {
                    ModelPlayer mainModel = value.getMainModel();

                    Class<?> superClass = mainModel.getClass().getSuperclass();

                    for (Field field : superClass.getDeclaredFields()) {
                        field.setAccessible(true);

                        try {
                            Object o = field.get(mainModel);
                            if (o != null) {
                                try {
                                    Field entityIn = o.getClass().getSuperclass().getDeclaredField("entityIn");
                                    entityIn.setAccessible(true);
                                    Object o1 = entityIn.get(o);
                                    if (o1 != null) {
                                        entityIn.set(o, null);
                                        del++;
                                    }
                                } catch (IllegalAccessException | NoSuchFieldException ignored) {
                                }

                                try {
                                    Field clientPlayer = o.getClass().getSuperclass().getDeclaredField("clientPlayer");
                                    clientPlayer.setAccessible(true);
                                    Object o1 = clientPlayer.get(o);
                                    if (o1 != null) {
                                        clientPlayer.set(o, null);
                                        del++;
                                    }
                                } catch (IllegalAccessException | NoSuchFieldException ignored) {
                                }
                            }
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }

            Hyperium.LOGGER.info("Deleted " + (removes.size() + size + del) + " cosmetic items / skins");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @InvokeEvent
    public void tickEvent(TickEvent event) {
        if (tickCounter % 20 * 60 == 0) {
            Minecraft.memoryReserve = new byte[0];
            try {
                ((Map) resourceCache.get(Launch.classLoader)).clear();
                ((Map) packageManifests.get(Launch.classLoader)).clear();
            } catch (IllegalAccessException e) {
                Hyperium.LOGGER.error("Failed to clear resourceCache/packageManifests", e);
            }
        }

        tickCounter++;
    }

    public void queueDelete(ResourceLocation location) {
        if (location == null) return;
        locations.add(location);
    }

    private void deleteSkin(ResourceLocation skinLocation) {
        if (skinLocation == null) return;
        TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
        Map<ResourceLocation, ITextureObject> mapTextureObjects = textureManager.getMapTextureObjects();
        textureManager.deleteTexture(skinLocation);
        mapTextureObjects.remove(skinLocation); // not needed with optifine but needed without it
    }
}
