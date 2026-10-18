/*
 * Modern UI.
 * Copyright (C) 2019-2025 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnifontFont extends BitmapFont {

    public static final int SPRITE_WIDTH = 32;
    public static final int SPRITE_HEIGHT = 16;
    public static final int GLYPH_HEIGHT = 16;
    public static final int ASCENT = 7;
    public static final int HEIGHT = 8;

    private static final int GLYPH_ROWS = 16;

    private final ResourceLocation mHexFile;
    private final Int2ObjectOpenHashMap<int[]> mHexGlyphs;

    private UnifontFont(@Nonnull ResourceLocation hexFile, int[][] grid,
                        @Nonnull Int2ObjectOpenHashMap<int[]> hexGlyphs) {
        super(hexFile, grid, HEIGHT, ASCENT, SPRITE_WIDTH, SPRITE_HEIGHT);
        mHexFile = hexFile;
        mHexGlyphs = hexGlyphs;

        var iterator = hexGlyphs.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int packed = entry.getValue()[0];
            int width = (packed & 0xFF) - (packed >>> 8 & 0xFF) + 1;
            putGlyph(entry.getIntKey(), new Glyph(width / 2 + 1, 0, 0, false,
                    getShadowOffset(), getBoldOffset()));
        }
    }

    @Nonnull
    public static UnifontFont create(@Nonnull ResourceLocation hexFile,
                                     @Nonnull JsonObject metadata,
                                     @Nonnull ResourceManager resources) throws IOException {
        List<int[]> overrides = parseSizeOverrides(metadata);
        Int2ObjectOpenHashMap<int[]> hexGlyphs = new Int2ObjectOpenHashMap<>();
        try (InputStream stream = resources.open(hexFile);
             ZipInputStream zip = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".hex")) {
                    continue;
                }
                var reader = new BufferedReader(
                        new InputStreamReader(zip, StandardCharsets.UTF_8));
                readHex(reader, overrides, hexGlyphs);
            }
        }
        if (hexGlyphs.isEmpty()) {
            throw new IOException("No glyph data found in " + hexFile);
        }
        int[] codepoints = hexGlyphs.keySet().toIntArray();
        Arrays.sort(codepoints);
        int[][] grid = new int[(codepoints.length + 255) >> 8][256];
        for (int i = 0; i < codepoints.length; i++) {
            grid[i >> 8][i & 0xFF] = codepoints[i];
        }
        return new UnifontFont(hexFile, grid, hexGlyphs);
    }

    private static void readHex(@Nonnull BufferedReader reader,
                                @Nonnull List<int[]> overrides,
                                @Nonnull Int2ObjectOpenHashMap<int[]> out) throws IOException {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 1) {
                throw new IOException("Invalid entry at line " + lineNumber +
                        ": expected hex codepoint followed by a colon");
            }
            int codepoint = Integer.parseInt(line.substring(0, colon), 16);
            String hex = line.substring(colon + 1).strip();
            int digits = hex.length();
            if (digits != 32 && digits != 64 && digits != 96 && digits != 128) {
                throw new IOException("Invalid entry at line " + lineNumber +
                        ": expected 32, 64, 96 or 128 hex digits, got " + digits);
            }
            int digitsPerRow = digits >> 4; // 2, 4, 6 or 8 digits, 8, 16, 24 or 32 bits
            int bitsPerRow = digitsPerRow << 2;
            int[] data = new int[GLYPH_ROWS + 1];
            int mask = 0;
            for (int r = 0; r < GLYPH_ROWS; r++) {
                String rowHex = hex.substring(r * digitsPerRow, (r + 1) * digitsPerRow);
                long bits = Long.parseLong(rowHex, 16);
                int row = (int) (bits << (32 - bitsPerRow));
                data[r + 1] = row;
                mask |= row;
            }
            if (mask == 0) {
                continue; // fully transparent, never requested
            }
            int left = Integer.numberOfLeadingZeros(mask);
            int right = 31 - Integer.numberOfTrailingZeros(mask);
            for (int[] range : overrides) {
                if (codepoint >= range[0] && codepoint <= range[1]) {
                    left = range[2];
                    right = range[3];
                    break;
                }
            }
            data[0] = (left & 0xFF) << 8 | (right & 0xFF);
            out.put(codepoint, data);
        }
    }

    @Nonnull
    private static List<int[]> parseSizeOverrides(@Nonnull JsonObject metadata) {
        JsonElement element = metadata.get("size_overrides");
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        List<int[]> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonObject()) {
                continue;
            }
            JsonObject object = item.getAsJsonObject();
            int from = readCodepoint(object.get("from"));
            int to = readCodepoint(object.get("to"));
            JsonElement left = object.get("left");
            JsonElement right = object.get("right");
            if (from >= 0 && to >= from && left != null && right != null) {
                result.add(new int[]{from, to, left.getAsInt(), right.getAsInt()});
            }
        }
        return result;
    }

    private static int readCodepoint(@Nullable JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return -1;
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        String text = element.getAsString();
        return text.isEmpty() ? -1 : text.codePointAt(0);
    }

    @Override
    public boolean getGlyphImage(int ch, long dst) {
        int[] data = mHexGlyphs.get(ch);
        if (data == null) {
            return false;
        }
        int left = (data[0] >>> 8) & 0xFF;
        int width = (data[0] & 0xFF) - left + 1;
        final long rowBytes = (long) SPRITE_WIDTH * 4;
        for (int y = 0; y < SPRITE_HEIGHT; y++) {
            int bits = data[y + 1];
            long row = dst + (long) y * rowBytes;
            for (int x = 0; x < SPRITE_WIDTH; x++) {
                boolean ink = x < width && ((bits >>> (31 - left - x)) & 1) != 0;
                MemoryUtil.memPutInt(row + (long) x * 4, ink ? 0xFFFFFFFF : 0x00000000);
            }
        }
        return true;
    }

    @Override
    public boolean nothingToDraw() {
        return mHexGlyphs.isEmpty();
    }

    @Override
    public float getShadowOffset() {
        return 0.5f;
    }

    @Override
    public float getBoldOffset() {
        return 0.5f;
    }

    @Override
    public int hashCode() {
        int result = mHexFile.hashCode();
        result = 31 * result + getAscent();
        result = 31 * result + mHexGlyphs.size();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }
}
