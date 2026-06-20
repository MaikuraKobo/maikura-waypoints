package jp.exnakamura.maikurawaypoints;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WaypointListScreen extends Screen {
    private static final int ROW_HEIGHT = 29;
    private static final int MIN_PANEL_WIDTH = 360;
    private static final int MAX_PANEL_WIDTH = 520;
    private static final int VISIBLE_ROWS = 5;
    private static final int HEADER_HEIGHT = 74;
    private final Entry current;
    private final List<Entry> entries;
    private int scrollOffset = 0;
    private static SortMode lastSortMode = SortMode.MANUAL;
    private static FilterMode lastFilterMode = FilterMode.ALL;
    private static String lastSearchQuery = "";
    private SortMode sortMode = lastSortMode;
    private FilterMode filterMode = lastFilterMode;
    private TextFieldWidget searchField;
    private TextFieldWidget inlineNameField;
    private Entry editingEntry;
    private boolean editingCurrentName = false;

    private int getVisibleRows(int headerHeight) {
        return Math.min(VISIBLE_ROWS, Math.max(3, (this.height - headerHeight - 30) / ROW_HEIGHT));
    }

    public WaypointListScreen(Entry current, List<Entry> entries) {
        super(Text.literal("ウェイポイント一覧"));
        this.current = current;
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public WaypointListScreen(List<Entry> entries) {
        this(null, entries);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, this.width - 48));
        int x = (this.width - panelWidth) / 2;
        int panelHeight = HEADER_HEIGHT + getVisibleRows(HEADER_HEIGHT) * ROW_HEIGHT + 18;
        int y = Math.max(8, (this.height - panelHeight) / 2);
        int topY = y + 20;

        this.searchField = new TextFieldWidget(this.textRenderer, x + 14, topY + 36, Math.max(120, panelWidth - 176), 16, Text.literal("検索"));
        this.searchField.setMaxLength(32);
        this.searchField.setText(lastSearchQuery);
        this.searchField.setPlaceholder(Text.literal("検索"));
        this.searchField.setChangedListener(value -> {
            lastSearchQuery = value == null ? "" : value;
            scrollOffset = 0;
        });
        this.addDrawableChild(this.searchField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x66000000);

        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, this.width - 48));
        int x = (this.width - panelWidth) / 2;
        int headerHeight = HEADER_HEIGHT;
        int visibleRows = getVisibleRows(headerHeight);
        int panelHeight = headerHeight + visibleRows * ROW_HEIGHT + 18;
        int y = Math.max(8, (this.height - panelHeight) / 2);
        int topY = y + 20;
        int listTop = y + headerHeight;
        int rowWidth = panelWidth - 16;
        List<Entry> sorted = sortedEntries();
        clampScroll(sorted.size());
        GuiEditorBridge.begin("waypoints");
        WaypointDisplayConfig displayConfig = WaypointDisplayConfig.get();
        ensureSortModeEnabled(displayConfig);

        // GUI Editor selectable containers and top controls.
        // These IDs are intentionally stable so saved layouts can be reused.
        GuiEditorBridge.register("curGroup", x + 8, topY, panelWidth - 16, 32);
        int currentInfoX = GuiEditorBridge.getX("curGroup", x + 8);
        int currentInfoY = GuiEditorBridge.getY("curGroup", topY);
        int currentInfoW = GuiEditorBridge.getWidth("curGroup", panelWidth - 16);

        // Current info default layout is intentionally split into 2 rows:
        // row 1 = label + dimension, row 2 = icon/favorite/name + coordinates.
        int currentLabelDefaultX = currentInfoX + 8;
        int currentDimDefaultX = currentInfoX + 174;
        int currentIconDefaultX = currentInfoX + 6;
        int currentFavDefaultX = currentInfoX + 26;
        int currentNameDefaultX = currentInfoX + 42;
        int currentCoordDefaultX = currentInfoX + 174;
        GuiEditorBridge.register("curLbl", currentLabelDefaultX, currentInfoY + 4, 38, 12);
        GuiEditorBridge.register("curDim", currentDimDefaultX, currentInfoY + 4, 80, 12);
        GuiEditorBridge.register("curIcon", currentIconDefaultX, currentInfoY + 15, 16, 16);
        GuiEditorBridge.register("curFav", currentFavDefaultX, currentInfoY + 17, 14, 14);
        GuiEditorBridge.register("curName", currentNameDefaultX, currentInfoY + 16, 162, 12);
        GuiEditorBridge.register("curXYZ", currentCoordDefaultX, currentInfoY + 17, 184, 12);

        GuiEditorBridge.register("sBox", x + 11, topY + 35, 256, 16);
        int searchBoxX = GuiEditorBridge.getX("sBox", x + 11);
        int searchBoxY = GuiEditorBridge.getY("sBox", topY + 35);
        int searchBoxWidth = GuiEditorBridge.getWidth("sBox", 256);
        if (this.searchField != null) {
            this.searchField.setX(searchBoxX);
            this.searchField.setY(searchBoxY);
            this.searchField.setWidth(searchBoxWidth);
        }

        GuiEditorBridge.register("list", x + 8, listTop, rowWidth, visibleRows * ROW_HEIGHT);
        int listX = GuiEditorBridge.getX("list", x + 8);
        int listTopEdited = GuiEditorBridge.getY("list", listTop);
        int listWidth = GuiEditorBridge.getWidth("list", rowWidth);
        GuiEditorBridge.register("filter", x + panelWidth - 118, topY + 33, 62, 20);
        int filterX = GuiEditorBridge.getX("filter", x + panelWidth - 118);
        int filterY = GuiEditorBridge.getY("filter", topY + 33);
        int filterW = GuiEditorBridge.getWidth("filter", 62);
        GuiEditorBridge.register("sort", x + panelWidth - 52, topY + 33, 68, 20);
        int sortX = GuiEditorBridge.getX("sort", x + panelWidth - 52);
        int sortY = GuiEditorBridge.getY("sort", topY + 33);
        int sortW = GuiEditorBridge.getWidth("sort", 68);

        context.fill(x - 2, y - 2, x + panelWidth + 2, y + panelHeight + 2, 0xFF777777);
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xC8101418);
        context.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, 0xAA20262A);

        context.drawText(this.textRenderer, Text.literal("◎ ウェイポイント一覧"), x + 10, y + 8, 0xFFFFFFFF, true);
        String countText = getSearchQuery().isBlank() ? sorted.size() + "件" : sorted.size() + "/" + entries.size() + "件";
        context.drawText(this.textRenderer, Text.literal(countText), x + panelWidth - this.textRenderer.getWidth(countText) - 10, y + 8, 0xFFFFFFFF, true);

        context.fill(x + 8, topY, x + panelWidth - 8, topY + 50, 0x66302A42);
        // Frame around current info area.
        context.fill(currentInfoX, currentInfoY, currentInfoX + currentInfoW, currentInfoY + 1, 0xCC9B5AD6);
        context.fill(currentInfoX, currentInfoY + 31, currentInfoX + currentInfoW, currentInfoY + 32, 0xCC9B5AD6);
        context.fill(currentInfoX, currentInfoY, currentInfoX + 1, currentInfoY + 32, 0xCC9B5AD6);
        context.fill(currentInfoX + currentInfoW - 1, currentInfoY, currentInfoX + currentInfoW, currentInfoY + 32, 0xCC9B5AD6);
        int currentLabelX = GuiEditorBridge.getX("curLbl", currentLabelDefaultX);
        int currentLabelY = GuiEditorBridge.getY("curLbl", currentInfoY + 4);
        int currentIconX = GuiEditorBridge.getX("curIcon", currentIconDefaultX);
        int currentIconY = GuiEditorBridge.getY("curIcon", currentInfoY + 16);
        int currentFavX = GuiEditorBridge.getX("curFav", currentFavDefaultX);
        int currentFavY = GuiEditorBridge.getY("curFav", currentInfoY + 17);
        int currentNameX = GuiEditorBridge.getX("curName", currentNameDefaultX);
        int currentNameY = GuiEditorBridge.getY("curName", currentInfoY + 17);
        int currentNameW = Math.max(150, GuiEditorBridge.getWidth("curName", Math.max(150, currentCoordDefaultX - currentNameDefaultX - 10)));
        int currentDimX = GuiEditorBridge.getX("curDim", currentDimDefaultX);
        int currentDimY = GuiEditorBridge.getY("curDim", currentInfoY + 4);
        int currentDimW = Math.max(80, GuiEditorBridge.getWidth("curDim", Math.max(80, currentCoordDefaultX - currentDimDefaultX - 8)));
        int currentCoordX = GuiEditorBridge.getX("curXYZ", currentCoordDefaultX);
        int currentCoordY = GuiEditorBridge.getY("curXYZ", currentInfoY + 17);
        int currentCoordW = GuiEditorBridge.getWidth("curXYZ", 184);
        context.drawText(this.textRenderer, Text.literal("現在:"), currentLabelX, currentLabelY, 0xFFFFFFFF, true);
        if (current != null) {
            drawWaypointListIcon(context, currentIconX, currentIconY, current);
            int nameColor = current.home ? 0xFFFFD35A : current.favorite ? 0xFFFFC6FF : 0xFFFFFFFF;
            if (!(editingCurrentName && editingEntry != null && editingEntry.key().equals(current.key()))) {
                context.drawText(this.textRenderer, Text.literal(trimToWidth(current.cleanDisplayName(), Math.max(40, currentNameW))), currentNameX, currentNameY, nameColor, true);
            }
            if (displayConfig.showDimension) {
                context.drawText(this.textRenderer, Text.literal(trimToWidth(shortDimension(current.dimension), Math.max(40, currentDimW))), currentDimX, currentDimY, 0xFFD9D9D9, true);
            }
            if (displayConfig.showCoordinates) {
                String currentCoord = "X:" + current.x + " Y:" + current.y + " Z:" + current.z;
                context.drawText(this.textRenderer, Text.literal(trimToWidth(currentCoord, Math.max(60, currentCoordW))), currentCoordX, currentCoordY, 0xFFBFBFBF, false);
            }
        } else {
            context.drawText(this.textRenderer, Text.literal("不明"), currentNameX, currentNameY, 0xFFFFFFFF, true);
        }

        String query = getSearchQuery();
        drawButton(context, filterX, filterY, filterW, 20, "表示:" + filterMode.label(), isButtonHover(mouseX, mouseY, filterX, filterY, filterW, 20), 0xFF4A3A60);
        drawButton(context, sortX, sortY, sortW, 20, "ソート:" + sortMode.label(), isButtonHover(mouseX, mouseY, sortX, sortY, sortW, 20), 0xFF405060);

        int max = Math.min(sorted.size(), scrollOffset + visibleRows);
        for (int i = scrollOffset; i < max; i++) {
            int row = i - scrollOffset;
            Entry entry = sorted.get(i);
            int rowY = listTopEdited + row * ROW_HEIGHT;
            String guiEditorRowSuffix = "_row" + row;
            boolean hover = isInRow(mouseX, mouseY, listX, rowY, listWidth);

            int rowColor = entry.home ? (hover ? 0xCC6B4A18 : 0xAA533A16) : entry.favorite ? (hover ? 0xBB5C3F75 : 0x994A315F) : (hover ? 0xAA3B2A55 : 0x88202428);
            GuiEditorBridge.register("rowBg" + guiEditorRowSuffix, listX, rowY, listWidth, ROW_HEIGHT - 3);
            int rowBgX = GuiEditorBridge.getX("rowBg" + guiEditorRowSuffix, listX);
            int rowBgY = GuiEditorBridge.getY("rowBg" + guiEditorRowSuffix, rowY);
            int rowBgW = GuiEditorBridge.getWidth("rowBg" + guiEditorRowSuffix, listWidth);
            context.fill(rowBgX, rowBgY, rowBgX + rowBgW, rowBgY + ROW_HEIGHT - 3, rowColor);
            context.fill(rowBgX, rowBgY, rowBgX + rowBgW, rowBgY + 1, entry.home ? 0xFFFFD35A : entry.favorite ? 0xFFFF8CFF : 0xAA858585);
            context.fill(rowBgX, rowBgY + ROW_HEIGHT - 4, rowBgX + rowBgW, rowBgY + ROW_HEIGHT - 3, entry.home ? 0xCCB87928 : entry.favorite ? 0xCC9258B8 : 0xAA555555);
            if (entry.home) {
                context.fill(rowBgX, rowBgY, rowBgX + 4, rowBgY + ROW_HEIGHT - 3, 0xFFFFD35A);
            } else if (entry.favorite) {
                context.fill(rowBgX, rowBgY, rowBgX + 4, rowBgY + ROW_HEIGHT - 3, 0xFFFF8CFF);
            }

            int iconXDefault = rowBgX + 6;
            int iconYDefault = rowBgY + 7;
            GuiEditorBridge.register("type" + guiEditorRowSuffix, iconXDefault, iconYDefault, 16, 16);
            int iconX = GuiEditorBridge.getX("type" + guiEditorRowSuffix, iconXDefault);
            int iconY = GuiEditorBridge.getY("type" + guiEditorRowSuffix, iconYDefault);
            drawWaypointListIcon(context, iconX, iconY, entry);

            int favButtonXDefault = rowBgX + 26;
            int favButtonYDefault = rowBgY + 9;
            GuiEditorBridge.register("fav" + guiEditorRowSuffix, favButtonXDefault, favButtonYDefault, 14, 14);
            int favButtonX = GuiEditorBridge.getX("fav" + guiEditorRowSuffix, favButtonXDefault);
            int favButtonY = GuiEditorBridge.getY("fav" + guiEditorRowSuffix, favButtonYDefault);
            String favMark = entry.favorite ? "★" : "☆";
            int favColor = entry.favorite ? 0xFFFF8CFF : 0xFFBFBFBF;
            context.drawText(this.textRenderer, Text.literal(favMark), favButtonX, favButtonY, favColor, true);

            int nameXDefault = rowBgX + 42;
            GuiEditorBridge.register("name" + guiEditorRowSuffix, nameXDefault, rowBgY + 8, 120, 14);
            int nameX = GuiEditorBridge.getX("name" + guiEditorRowSuffix, nameXDefault);
            int nameY = GuiEditorBridge.getY("name" + guiEditorRowSuffix, rowBgY + 8);
            int nameWidth = GuiEditorBridge.getWidth("name" + guiEditorRowSuffix, 120);
            String cleanName = entry.cleanDisplayName();
            boolean manual = sortMode == SortMode.MANUAL;
            int rightButtonEdge = rowBgX + rowBgW - 10;
            int deleteButtonXDefault = rightButtonEdge - 16;
            int downX = manual ? deleteButtonXDefault - 24 : deleteButtonXDefault;
            int upX = manual ? downX - 24 : downX;
            int deleteButtonYDefault = rowBgY + 6;
            GuiEditorBridge.register("del" + guiEditorRowSuffix, deleteButtonXDefault, deleteButtonYDefault, 16, 16);
            int deleteButtonX = GuiEditorBridge.getX("del" + guiEditorRowSuffix, deleteButtonXDefault);
            int deleteButtonY = GuiEditorBridge.getY("del" + guiEditorRowSuffix, deleteButtonYDefault);
            int distRight = (manual ? upX : deleteButtonXDefault) - 10;
            int dimX = rowBgX + Math.max(134, panelWidth / 2 - 42);
            int nameMaxWidth = Math.max(40, nameWidth);
            int dimMaxWidth = Math.max(142, distRight - dimX - 8);

            int nameColor = entry.home ? 0xFFFFD35A : entry.favorite ? 0xFFFFC6FF : 0xFFFFFFFF;
            if (!(editingEntry != null && editingEntry.key().equals(entry.key()) && !editingCurrentName)) {
                context.drawText(this.textRenderer, Text.literal(trimToWidth(cleanName, nameMaxWidth)), nameX, nameY, nameColor, true);
            }

            String dimensionId = "dim" + guiEditorRowSuffix;
            GuiEditorBridge.register(dimensionId, dimX, rowBgY + 4, dimMaxWidth, 12);
            int dimensionX = GuiEditorBridge.getX(dimensionId, dimX);
            int dimensionY = GuiEditorBridge.getY(dimensionId, rowBgY + 4);
            int dimensionWidth = GuiEditorBridge.getWidth(dimensionId, dimMaxWidth);
            if (displayConfig.showDimension) {
                context.drawText(this.textRenderer, Text.literal(trimToWidth(shortDimension(entry.dimension), Math.max(40, dimensionWidth))), dimensionX, dimensionY, 0xFFD9D9D9, true);
            }

            String coordId = "xyz" + guiEditorRowSuffix;
            GuiEditorBridge.register(coordId, dimX, rowBgY + 17, dimMaxWidth + 8, 12);
            int coordX = GuiEditorBridge.getX(coordId, dimX);
            int coordY = GuiEditorBridge.getY(coordId, rowBgY + 17);
            int coordWidth = GuiEditorBridge.getWidth(coordId, dimMaxWidth + 8);
            if (displayConfig.showCoordinates) {
                context.drawText(this.textRenderer, Text.literal(trimToWidth("X:" + entry.x + " Y:" + entry.y + " Z:" + entry.z, Math.max(40, coordWidth))), coordX, coordY, 0xFFBFBFBF, false);
            }

            String distanceText = entry.distance + "m";
            int distanceColumnWidth = 48;
            int distanceXDefault = distRight - distanceColumnWidth;
            GuiEditorBridge.register("dist" + guiEditorRowSuffix, distanceXDefault, rowBgY + 4, distanceColumnWidth, 14);
            int distanceX = GuiEditorBridge.getX("dist" + guiEditorRowSuffix, distanceXDefault);
            int distanceY = GuiEditorBridge.getY("dist" + guiEditorRowSuffix, rowBgY + 4);
            int distanceWidth = GuiEditorBridge.getWidth("dist" + guiEditorRowSuffix, distanceColumnWidth);
            int distanceDrawX = distanceX + distanceWidth - this.textRenderer.getWidth(distanceText);
            if (displayConfig.showDistance) {
                context.drawText(this.textRenderer, Text.literal(distanceText), distanceDrawX, distanceY, 0xFFFFFFFF, true);
            }

            GuiEditorBridge.register("up" + guiEditorRowSuffix, upX, rowBgY + 5, 18, 18);
            GuiEditorBridge.register("down" + guiEditorRowSuffix, downX, rowBgY + 5, 18, 18);
            int upButtonX = GuiEditorBridge.getX("up" + guiEditorRowSuffix, upX);
            int upButtonY = GuiEditorBridge.getY("up" + guiEditorRowSuffix, rowBgY + 5);
            int downButtonX = GuiEditorBridge.getX("down" + guiEditorRowSuffix, downX);
            int downButtonY = GuiEditorBridge.getY("down" + guiEditorRowSuffix, rowBgY + 5);
            if (manual) {
                drawTinyButton(context, upButtonX, upButtonY, "↑", i > 0 && isButtonHover(mouseX, mouseY, upButtonX, upButtonY, 18, 18), i > 0, false);
                drawTinyButton(context, downButtonX, downButtonY, "↓", i < sorted.size() - 1 && isButtonHover(mouseX, mouseY, downButtonX, downButtonY, 18, 18), i < sorted.size() - 1, false);
            }
            drawTinyButton(context, deleteButtonX, deleteButtonY, "×", isButtonHover(mouseX, mouseY, deleteButtonX, deleteButtonY, 16, 16), true, true);
        }

        if (sorted.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("該当するウェイポイントがありません"), listX + listWidth / 2, listTopEdited + (visibleRows * ROW_HEIGHT) / 2, 0xFFFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);

        int helpY = y + panelHeight - 15;
        drawHelpBar(context, x + 8, helpY - 3, panelWidth - 16);

        // GUI Editor Core r9以降はScreenRenderMixin側で前面描画する。
        // ここではbegin/register/getのみ行い、二重描画を避ける。
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, this.width - 48));
        int x = (this.width - panelWidth) / 2;
        int headerHeight = HEADER_HEIGHT;
        int visibleRows = getVisibleRows(headerHeight);
        int panelHeight = headerHeight + visibleRows * ROW_HEIGHT + 18;
        int y = Math.max(8, (this.height - panelHeight) / 2);
        int topY = y + 20;
        int listTop = y + headerHeight;
        int rowWidth = panelWidth - 16;
        List<Entry> sorted = sortedEntries();
        clampScroll(sorted.size());
        GuiEditorBridge.begin("waypoints");
        WaypointDisplayConfig displayConfig = WaypointDisplayConfig.get();
        ensureSortModeEnabled(displayConfig);
        int currentInfoX = GuiEditorBridge.getX("curGroup", x + 8);
        int currentInfoY = GuiEditorBridge.getY("curGroup", topY);
        int currentInfoW = GuiEditorBridge.getWidth("curGroup", panelWidth - 16);
        int currentIconDefaultX = currentInfoX + 6;
        int currentFavDefaultX = currentInfoX + 26;
        int currentNameDefaultX = currentInfoX + 42;
        int currentCoordDefaultX = currentInfoX + 174;
        int currentIconX = GuiEditorBridge.getX("curIcon", currentIconDefaultX);
        int currentIconY = GuiEditorBridge.getY("curIcon", currentInfoY + 15);
        int currentFavX = GuiEditorBridge.getX("curFav", currentFavDefaultX);
        int currentFavY = GuiEditorBridge.getY("curFav", currentInfoY + 17);
        int currentNameX = GuiEditorBridge.getX("curName", currentNameDefaultX);
        int currentNameY = GuiEditorBridge.getY("curName", currentInfoY + 16);
        int currentNameW = Math.max(150, GuiEditorBridge.getWidth("curName", 162));
        int listX = GuiEditorBridge.getX("list", x + 8);
        int listTopEdited = GuiEditorBridge.getY("list", listTop);
        int listWidth = GuiEditorBridge.getWidth("list", rowWidth);
        int filterX = GuiEditorBridge.getX("filter", x + panelWidth - 118);
        int filterY = GuiEditorBridge.getY("filter", topY + 33);
        int filterW = GuiEditorBridge.getWidth("filter", 62);
        int sortX = GuiEditorBridge.getX("sort", x + panelWidth - 52);
        int sortY = GuiEditorBridge.getY("sort", topY + 33);
        int sortW = GuiEditorBridge.getWidth("sort", 68);
        if (GuiEditorBridge.isEditMode()) {
            if (this.searchField != null) {
                this.searchField.setFocused(false);
            }
            GuiEditorBridge.mouseClicked(mouseX, mouseY, click.button());
            return true;
        }

        if (inlineNameField != null && inlineNameField.isFocused()) {
            if (!isButtonHover(mouseX, mouseY, inlineNameField.getX(), inlineNameField.getY(), inlineNameField.getWidth(), 20)) {
                finishInlineRename(true);
            }
        }

        if (click.button() == 0 && current != null) {
            if (isButtonHover(mouseX, mouseY, currentFavX - 3, currentFavY - 3, 14, 14)) {
                ClientPlayNetworking.send(new WaypointFavoritePayload(current.key, current.key));
                return true;
            }
            if (isButtonHover(mouseX, mouseY, currentNameX, currentNameY - 3, Math.max(40, currentNameW), 16)) {
                startInlineRename(current, true, currentNameX, currentNameY - 4, Math.max(40, currentNameW));
                return true;
            }
        }
        if (click.button() == 0 && isButtonHover(mouseX, mouseY, filterX, filterY, filterW, 20)) {
            filterMode = filterMode.next();
            lastFilterMode = filterMode;
            scrollOffset = 0;
            return true;
        }
        if (click.button() == 0 && isButtonHover(mouseX, mouseY, sortX, sortY, sortW, 20)) {
            sortMode = sortMode.nextEnabled(displayConfig);
            lastSortMode = sortMode;
            scrollOffset = 0;
            return true;
        }

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listTopEdited && mouseY <= listTopEdited + visibleRows * ROW_HEIGHT) {
            int row = (int) ((mouseY - listTopEdited) / ROW_HEIGHT);
            int index = scrollOffset + row;
            if (index >= 0 && index < sorted.size()) {
                Entry entry = sorted.get(index);
                int rowY = listTopEdited + row * ROW_HEIGHT;
                boolean manual = sortMode == SortMode.MANUAL;
                String guiEditorRowSuffix = "_row" + row;
                int rowBgX = GuiEditorBridge.getX("rowBg" + guiEditorRowSuffix, listX);
                int rowBgY = GuiEditorBridge.getY("rowBg" + guiEditorRowSuffix, rowY);
                int rowBgW = GuiEditorBridge.getWidth("rowBg" + guiEditorRowSuffix, listWidth);
                int rightButtonEdge = rowBgX + rowBgW - 10;
                int deleteButtonXDefault = rightButtonEdge - 16;
                int downX = manual ? deleteButtonXDefault - 24 : deleteButtonXDefault;
                int upX = manual ? downX - 24 : downX;
                int favButtonX = GuiEditorBridge.getX("fav" + guiEditorRowSuffix, rowBgX + 26);
                int favButtonY = GuiEditorBridge.getY("fav" + guiEditorRowSuffix, rowBgY + 9);
                int nameXDefault = rowBgX + 42;
                int nameX = GuiEditorBridge.getX("name" + guiEditorRowSuffix, nameXDefault);
                int nameY = GuiEditorBridge.getY("name" + guiEditorRowSuffix, rowBgY + 8);
                int nameMaxWidth = Math.max(40, GuiEditorBridge.getWidth("name" + guiEditorRowSuffix, 120));
                int deleteButtonX = GuiEditorBridge.getX("del" + guiEditorRowSuffix, deleteButtonXDefault);
                int deleteButtonY = GuiEditorBridge.getY("del" + guiEditorRowSuffix, rowBgY + 6);
                int upButtonX = GuiEditorBridge.getX("up" + guiEditorRowSuffix, upX);
                int upButtonY = GuiEditorBridge.getY("up" + guiEditorRowSuffix, rowBgY + 5);
                int downButtonX = GuiEditorBridge.getX("down" + guiEditorRowSuffix, downX);
                int downButtonY = GuiEditorBridge.getY("down" + guiEditorRowSuffix, rowBgY + 5);
                boolean onFav = isButtonHover(mouseX, mouseY, favButtonX - 3, favButtonY - 3, 14, 14);
                boolean onName = isButtonHover(mouseX, mouseY, nameX, nameY - 3, nameMaxWidth, 16);
                boolean onDelete = isButtonHover(mouseX, mouseY, deleteButtonX, deleteButtonY, 16, 16);
                boolean onUp = manual && isButtonHover(mouseX, mouseY, upButtonX, upButtonY, 18, 18);
                boolean onDown = manual && isButtonHover(mouseX, mouseY, downButtonX, downButtonY, 18, 18);
                if (click.button() == 0 && onFav) {
                    ClientPlayNetworking.send(new WaypointFavoritePayload(entry.key, current == null ? "" : current.key));
                    return true;
                }
                if (click.button() == 0 && onName) {
                    startInlineRename(entry, false, nameX, nameY - 4, nameMaxWidth);
                    return true;
                }
                if (click.button() == 0 && onDelete) {
                    if (this.client != null) this.client.setScreen(new DeleteConfirmScreen(this, entry));
                    return true;
                }
                if (click.button() == 0 && onUp) {
                    if (index > 0) ClientPlayNetworking.send(new WaypointMovePayload(entry.key, "up", current == null ? "" : current.key));
                    return true;
                }
                if (click.button() == 0 && onDown) {
                    if (index < sorted.size() - 1) ClientPlayNetworking.send(new WaypointMovePayload(entry.key, "down", current == null ? "" : current.key));
                    return true;
                }
                if (click.button() == 0 && mouseX >= favButtonX - 5 && mouseX <= favButtonX + 14) {
                    return true;
                }
                if (click.button() == 0 && onDelete) {
                    return true;
                }
                if (click.button() == 0 && sortMode == SortMode.MANUAL && mouseX >= upButtonX - 4 && mouseX <= downButtonX + 22) {
                    return true;
                }
                if (click.button() == 1) {
                    ClientPlayNetworking.send(new WaypointHomePayload(entry.key, current == null ? "" : current.key));
                } else {
                    if (this.client != null) this.client.setScreen(new WarpConfirmScreen(this, entry));
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        GuiEditorBridge.begin("waypoints");

        // F12だけは検索欄フォーカス中でもGUI EditorのON/OFFを優先する。
        if (input.key() == GLFW.GLFW_KEY_F12) {
            if (GuiEditorBridge.handleKey(input.key())) {
                return true;
            }
        }

        if (inlineNameField != null && inlineNameField.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                finishInlineRename(true);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                finishInlineRename(false);
                return true;
            }
            return super.keyPressed(input);
        }

        // 検索欄入力中はテキスト入力を最優先する。
        // E/G/S/R/Delete/Tab/矢印などをGUI Editorや画面閉じ処理で消費しない。
        if (this.searchField != null && this.searchField.isFocused()) {
            return super.keyPressed(input);
        }

        // 検索欄にフォーカスしていない時だけ、EでWaypoints画面を閉じる。
        if (input.key() == GLFW.GLFW_KEY_E) {
            this.close();
            return true;
        }

        if (GuiEditorBridge.isEditMode()) {
            if (GuiEditorBridge.handleKey(input.key())) {
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        GuiEditorBridge.begin("waypoints");
        if (GuiEditorBridge.isEditMode() && GuiEditorBridge.mouseDragged(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        GuiEditorBridge.begin("waypoints");
        if (GuiEditorBridge.isEditMode() && GuiEditorBridge.mouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount < 0) scrollOffset++;
        if (verticalAmount > 0) scrollOffset--;
        clampScroll(sortedEntries().size());
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount < 0) scrollOffset++;
        if (amount > 0) scrollOffset--;
        clampScroll(sortedEntries().size());
        return true;
    }

    private void drawHelpBar(DrawContext context, int x, int y, int width) {
        String[] parts = {
                "左:ワープ確認",
                "右:HOME設定",
                "☆:お気に入り",
                "↑↓:手動並替"
        };
        int[] preferred = {96, 96, 104, 112};
        int gap = 2;
        int totalPreferred = -gap;
        for (int value : preferred) totalPreferred += value + gap;
        int scaleSpace = Math.max(0, width - gap * (parts.length - 1));
        int cursor = x;
        for (int i = 0; i < parts.length; i++) {
            int partWidth;
            if (totalPreferred > width && i < parts.length - 1) {
                partWidth = Math.max(52, preferred[i] * scaleSpace / Math.max(1, totalPreferred - gap * (parts.length - 1)));
            } else if (i == parts.length - 1) {
                partWidth = Math.max(80, x + width - cursor);
            } else {
                partWidth = preferred[i];
            }
            int right = Math.min(x + width, cursor + partWidth);
            context.fill(cursor, y, right, y + 14, 0x66101418);
            String text = trimToWidth(parts[i], Math.max(10, right - cursor - 6));
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(text), (cursor + right) / 2, y + 3, 0xFFFFE066);
            cursor = right + gap;
            if (cursor >= x + width) break;
        }
    }

    private void startInlineRename(Entry entry, boolean currentName, int x, int y, int width) {
        if (entry == null) return;
        finishInlineRename(false);
        this.editingEntry = entry;
        this.editingCurrentName = currentName;
        this.inlineNameField = new TextFieldWidget(this.textRenderer, x, y, Math.max(80, width), 18, Text.literal("名前"));
        this.inlineNameField.setMaxLength(24);
        this.inlineNameField.setText(entry.cleanDisplayName());
        this.inlineNameField.setFocused(true);
        this.addDrawableChild(this.inlineNameField);
        this.setFocused(this.inlineNameField);
    }

    private void finishInlineRename(boolean save) {
        if (inlineNameField == null) return;
        Entry target = editingEntry;
        String value = inlineNameField.getText() == null ? "" : inlineNameField.getText().trim();
        try {
            this.remove(inlineNameField);
        } catch (Exception ignored) {
        }
        inlineNameField = null;
        editingEntry = null;
        editingCurrentName = false;
        if (save && target != null && !value.isBlank() && !value.equals(target.cleanDisplayName())) {
            ClientPlayNetworking.send(new WaypointRenamePayload(target.key(), value));
        }
    }

    private void drawWaypointListIcon(DrawContext context, int x, int y, Entry entry) {
        if (entry != null && entry.home) {
            context.fill(x, y + 6, x + 14, y + 14, 0xFFFFD35A);
            context.fill(x + 2, y + 4, x + 12, y + 6, 0xFFFFE69A);
            context.fill(x + 4, y + 2, x + 10, y + 4, 0xFFFFE69A);
            context.fill(x + 5, y + 9, x + 9, y + 14, 0xFF6B4A18);
            context.fill(x + 2, y + 6, x + 12, y + 7, 0xFFB87928);
            return;
        }
        int iconColor = entry != null && entry.ancient ? 0xFF7D45D8 : 0xFF2B2B30;
        context.fill(x, y, x + 14, y + 14, iconColor);
        context.fill(x + 3, y + 3, x + 11, y + 11, 0xFF121216);
        context.fill(x + 5, y + 5, x + 9, y + 9, 0xFFB66CFF);
    }

    private void drawButton(DrawContext context, int x, int y, int w, int h, String text, boolean hover, int baseColor) {
        context.fill(x, y, x + w, y + h, hover ? 0xFF7D45D8 : baseColor);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(text), x + w / 2, y + 6, 0xFFFFFFFF);
    }

    private void drawTinyButton(DrawContext context, int x, int y, String text, boolean hover, boolean enabled, boolean deleteButton) {
        int color;
        if (!enabled) {
            color = 0x553A3A3A;
        } else if (deleteButton) {
            color = hover ? 0xFFB84040 : 0xFF3A4650;
        } else {
            color = hover ? 0xFF7D45D8 : 0xFF3A4650;
        }
        context.fill(x, y, x + 18, y + 18, color);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(text), x + 9, y + 5, enabled ? 0xFFFFFFFF : 0xFF888888);
    }

    private boolean isButtonHover(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private boolean isInRow(double mouseX, double mouseY, int x, int rowY, int rowWidth) {
        return mouseX >= x + 8 && mouseX <= x + 8 + rowWidth && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 3;
    }

    private void clampScroll(int size) {
        int visibleRows = getVisibleRows(HEADER_HEIGHT);
        int max = Math.max(0, size - visibleRows);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > max) scrollOffset = max;
    }

    private String getSearchQuery() {
        if (searchField != null) {
            String value = searchField.getText();
            return value == null ? "" : value.trim();
        }
        return lastSearchQuery == null ? "" : lastSearchQuery.trim();
    }


    private void ensureSortModeEnabled(WaypointDisplayConfig config) {
        if (config == null) return;
        config.ensureAtLeastOneSort();
        if (!sortMode.isEnabled(config)) {
            sortMode = SortMode.firstEnabled(config);
            lastSortMode = sortMode;
        }
    }

    private List<Entry> filteredEntries() {
        String query = getSearchQuery().toLowerCase();
        List<Entry> filtered = new ArrayList<>();
        for (Entry entry : entries) {
            if (!passesFilter(entry)) continue;
            if (!query.isBlank()) {
                String text = (entry.cleanDisplayName() + " " + shortDimension(entry.dimension) + " " + entry.dimension + " X:" + entry.x + " Y:" + entry.y + " Z:" + entry.z).toLowerCase();
                if (!text.contains(query)) continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    private boolean passesFilter(Entry entry) {
        return switch (filterMode) {
            case ALL -> true;
            case NORMAL -> !entry.ancient;
            case ANCIENT -> entry.ancient;
            case OVERWORLD -> isOverworld(entry.dimension);
            case NETHER -> isNether(entry.dimension);
            case END -> isEnd(entry.dimension);
            case OTHER_DIMENSION -> !isOverworld(entry.dimension) && !isNether(entry.dimension) && !isEnd(entry.dimension);
        };
    }

    private List<Entry> sortedEntries() {
        ensureSortModeEnabled(WaypointDisplayConfig.get());
        List<Entry> sorted = filteredEntries();
        switch (sortMode) {
            case MANUAL -> sorted.sort(Comparator.comparingInt((Entry e) -> e.order));
            case DISTANCE -> sorted.sort(Comparator.comparingInt((Entry e) -> e.distance).thenComparing(e -> e.cleanDisplayName()));
            case NAME -> sorted.sort(Comparator.comparing(Entry::cleanDisplayName).thenComparingInt(e -> e.distance));
            case REGISTERED -> sorted.sort(Comparator.comparingInt((Entry e) -> e.order));
        }
        return sorted;
    }

    private boolean isOverworld(String dimension) {
        return dimension != null && dimension.toLowerCase().contains("overworld");
    }

    private boolean isNether(String dimension) {
        String lower = dimension == null ? "" : dimension.toLowerCase();
        return lower.contains("the_nether") || lower.contains("nether");
    }

    private boolean isEnd(String dimension) {
        String lower = dimension == null ? "" : dimension.toLowerCase();
        return lower.contains("the_end") || lower.equals("end") || lower.endsWith(":end");
    }

    private String shortDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) return "不明";
        String lower = dimension.toLowerCase();
        if (lower.contains("overworld")) return "オーバーワールド";
        if (lower.contains("the_nether") || lower.contains("nether")) return "ネザー";
        if (lower.contains("the_end") || lower.equals("end")) return "エンド";
        String text = dimension.replace("minecraft:", "");
        return trimToWidth(text, 84);
    }

    private String trimToWidth(String value, int maxWidth) {
        if (value == null) return "";
        String result = value;
        while (result.length() > 1 && this.textRenderer.getWidth(result) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        if (!result.equals(value) && result.length() > 1) {
            result = result.substring(0, result.length() - 1) + "…";
        }
        return result;
    }

    private class DeleteConfirmScreen extends Screen {
        private final WaypointListScreen parent;
        private final Entry target;

        protected DeleteConfirmScreen(WaypointListScreen parent, Entry target) {
            super(Text.literal("削除確認"));
            this.parent = parent;
            this.target = target;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            parent.render(context, -9999, -9999, delta);
            context.fill(0, 0, this.width, this.height, 0x77000000);
            int w = 270;
            int h = 124;
            int x = (this.width - w) / 2;
            int y = (this.height - h) / 2;
            context.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF8A4444);
            context.fill(x, y, x + w, y + h, 0xEE241820);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("ウェイポイントを削除しますか？"), x + w / 2, y + 12, 0xFFFFE0E0);
            String name = (target.favorite ? "[★] " : "") + target.cleanDisplayName() + (target.home ? " [HOME]" : "");
            int nameColor = target.home ? 0xFFFFD35A : target.favorite ? 0xFFFFC6FF : 0xFFFFFFFF;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(trimToWidth(name, w - 24)), x + w / 2, y + 34, nameColor);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(shortDimension(target.dimension)), x + w / 2, y + 50, 0xFFD9D9D9);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("X:" + target.x + " Y:" + target.y + " Z:" + target.z), x + w / 2, y + 64, 0xFFBFBFBF);
            drawButton(context, x + 36, y + 94, 82, 20, "削除", isButtonHover(mouseX, mouseY, x + 36, y + 94, 82, 20), 0xFF9A3030);
            drawButton(context, x + 152, y + 94, 82, 20, "キャンセル", isButtonHover(mouseX, mouseY, x + 152, y + 94, 82, 20), 0xFF3A4650);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            double mouseX = click.x();
            double mouseY = click.y();
            int w = 270;
            int h = 124;
            int x = (this.width - w) / 2;
            int y = (this.height - h) / 2;
            if (click.button() == 0 && isButtonHover(mouseX, mouseY, x + 36, y + 94, 82, 20)) {
                ClientPlayNetworking.send(new WaypointDeletePayload(target.key, current == null ? "" : current.key));
                if (this.client != null) this.client.setScreen(parent);
                return true;
            }
            if (click.button() == 0 && isButtonHover(mouseX, mouseY, x + 152, y + 94, 82, 20)) {
                if (this.client != null) this.client.setScreen(parent);
                return true;
            }
            return true;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    private int getWarpCostLevels(Entry target) {
        if (target == null) return 0;
        if (this.current != null && !this.current.dimension.equals(target.dimension)) {
            return 10;
        }
        int distance = Math.max(0, target.distance);
        if (distance <= 1000) return 1;
        if (distance <= 5000) return 3;
        if (distance <= 10000) return 5;
        return 10;
    }

    private class WarpConfirmScreen extends Screen {
        private final WaypointListScreen parent;
        private final Entry target;

        protected WarpConfirmScreen(WaypointListScreen parent, Entry target) {
            super(Text.literal("ワープ確認"));
            this.parent = parent;
            this.target = target;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            parent.render(context, -9999, -9999, delta);
            context.fill(0, 0, this.width, this.height, 0x66000000);
            int w = 250;
            int h = 132;
            int x = (this.width - w) / 2;
            int y = (this.height - h) / 2;
            context.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF777777);
            context.fill(x, y, x + w, y + h, 0xEE20262A);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("ウェイポイントへワープしますか？"), x + w / 2, y + 12, 0xFFFFFFFF);
            String name = (target.favorite ? "[★] " : "") + target.cleanDisplayName() + (target.home ? " [HOME]" : "");
            int nameColor = target.home ? 0xFFFFD35A : target.favorite ? 0xFFFFC6FF : 0xFFFFFFFF;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(trimToWidth(name, w - 24)), x + w / 2, y + 32, nameColor);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(shortDimension(target.dimension)), x + w / 2, y + 48, 0xFFD9D9D9);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("X:" + target.x + " Y:" + target.y + " Z:" + target.z), x + w / 2, y + 62, 0xFFBFBFBF);
            int cost = getWarpCostLevels(target);
            String costText = !WaypointGameplayConfig.get().warpCostEnabled ? "必要経験値: なし"
                    : (current != null && !current.dimension.equals(target.dimension)) ? "必要経験値: Lv" + cost + "（別ディメンション）" : "必要経験値: Lv" + cost;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(costText), x + w / 2, y + 78, 0xFFFFD56A);
            drawButton(context, x + 36, y + 104, 70, 20, "はい", isButtonHover(mouseX, mouseY, x + 36, y + 104, 70, 20), 0xFF5A2E9A);
            drawButton(context, x + 144, y + 104, 70, 20, "いいえ", isButtonHover(mouseX, mouseY, x + 144, y + 104, 70, 20), 0xFF3A4650);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            double mouseX = click.x();
            double mouseY = click.y();
            int w = 250;
            int h = 132;
            int x = (this.width - w) / 2;
            int y = (this.height - h) / 2;
            if (click.button() == 0 && isButtonHover(mouseX, mouseY, x + 36, y + 104, 70, 20)) {
                ClientPlayNetworking.send(new WaypointWarpPayload(target.key));
                this.close();
                return true;
            }
            if (click.button() == 0 && isButtonHover(mouseX, mouseY, x + 144, y + 104, 70, 20)) {
                if (this.client != null) this.client.setScreen(parent);
                return true;
            }
            return true;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    private enum FilterMode {
        ALL("全て"), NORMAL("通常"), ANCIENT("古代"), OVERWORLD("OW"), NETHER("ネザー"), END("エンド"), OTHER_DIMENSION("その他");
        private final String label;
        FilterMode(String label) { this.label = label; }
        String label() { return label; }
        FilterMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private enum SortMode {
        MANUAL("手動"), DISTANCE("距離"), NAME("名前"), REGISTERED("登録");
        private final String label;
        SortMode(String label) { this.label = label; }
        String label() { return label; }

        boolean isEnabled(WaypointDisplayConfig config) {
            if (config == null) return true;
            return switch (this) {
                case MANUAL -> config.enableManualSort;
                case DISTANCE -> config.enableDistanceSort;
                case NAME -> config.enableNameSort;
                case REGISTERED -> config.enableRegisteredSort;
            };
        }

        SortMode nextEnabled(WaypointDisplayConfig config) {
            SortMode[] modes = values();
            for (int offset = 1; offset <= modes.length; offset++) {
                SortMode mode = modes[(ordinal() + offset) % modes.length];
                if (mode.isEnabled(config)) {
                    return mode;
                }
            }
            return DISTANCE;
        }

        static SortMode firstEnabled(WaypointDisplayConfig config) {
            for (SortMode mode : values()) {
                if (mode.isEnabled(config)) {
                    return mode;
                }
            }
            return DISTANCE;
        }
    }

    public record Entry(String key, String name, String dimension, int distance, boolean ancient, boolean home, boolean favorite, int x, int y, int z, int order) {
        public String cleanDisplayName() {
            if (name == null || name.isBlank()) return "ウェイポイント";
            return name.trim();
        }
    }
}
