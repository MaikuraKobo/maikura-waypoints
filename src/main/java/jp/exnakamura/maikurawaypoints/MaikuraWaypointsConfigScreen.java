package jp.exnakamura.maikurawaypoints;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MaikuraWaypointsConfigScreen extends Screen {
    private final Screen parent;
    private final WaypointGameplayConfig gameplayConfig;
    private final WaypointDisplayConfig displayConfig;

    private static final int BUTTON_W = 190;
    private static final int BUTTON_H = 22;
    private static final int GAP = 8;
    private static final int LEFT_PAD = 14;

    protected MaikuraWaypointsConfigScreen(Screen parent) {
        super(Text.literal("Maikura Waypoints 設定"));
        this.parent = parent;
        this.gameplayConfig = WaypointGameplayConfig.get();
        this.displayConfig = WaypointDisplayConfig.get();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC101418);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Maikura Waypoints 設定"), this.width / 2, 24, 0xFFFFFFFF);

        int leftX = this.width / 2 - BUTTON_W - LEFT_PAD;
        int rightX = this.width / 2 + LEFT_PAD;
        int y = 56;

        context.drawText(this.textRenderer, Text.literal("基本設定"), leftX, y, 0xFFFFD56A, true);
        y += 16;
        drawToggle(context, leftX, y, "ワープコスト", gameplayConfig.warpCostEnabled, mouseX, mouseY);
        y += BUTTON_H + GAP;
        drawToggle(context, leftX, y, "帰還クリスタル", gameplayConfig.returnCrystalEnabled, mouseX, mouseY);

        y = 56;
        context.drawText(this.textRenderer, Text.literal("有効ソート"), rightX, y, 0xFFFFD56A, true);
        y += 16;
        drawToggle(context, rightX, y, "手動順", displayConfig.enableManualSort, mouseX, mouseY);
        y += BUTTON_H + GAP;
        drawToggle(context, rightX, y, "距離順", displayConfig.enableDistanceSort, mouseX, mouseY);
        y += BUTTON_H + GAP;
        drawToggle(context, rightX, y, "名前順", displayConfig.enableNameSort, mouseX, mouseY);
        y += BUTTON_H + GAP;
        drawToggle(context, rightX, y, "登録順", displayConfig.enableRegisteredSort, mouseX, mouseY);

        int bottomY = this.height - 32;
        context.fill(0, bottomY - 24, this.width, this.height, 0xAA101418);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("ソートは最低1個必要"), this.width / 2, bottomY - 16, 0xFFBFBFBF);
        drawButton(context, this.width / 2 - 45, bottomY, 90, 22, "戻る", isHover(mouseX, mouseY, this.width / 2 - 45, bottomY, 90, 22), 0xFF3A4650);
    }

    private void drawToggle(DrawContext context, int x, int y, String label, boolean on, int mouseX, int mouseY) {
        boolean hover = isHover(mouseX, mouseY, x, y, BUTTON_W, BUTTON_H);
        int base = on ? 0xFF2E6F4A : 0xFF53333A;
        int color = hover ? 0xFF7D45D8 : base;
        drawButton(context, x, y, BUTTON_W, BUTTON_H, (on ? "ON  " : "OFF ") + label, hover, color);
    }

    private void drawButton(DrawContext context, int x, int y, int w, int h, String text, boolean hover, int baseColor) {
        context.fill(x, y, x + w, y + h, hover ? 0xFF7D45D8 : baseColor);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(text), x + w / 2, y + 7, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int leftX = this.width / 2 - BUTTON_W - LEFT_PAD;
        int rightX = this.width / 2 + LEFT_PAD;
        int y = 72;

        if (hit(mouseX, mouseY, leftX, y)) {
            gameplayConfig.warpCostEnabled = !gameplayConfig.warpCostEnabled;
            gameplayConfig.save();
            return true;
        }
        y += BUTTON_H + GAP;
        if (hit(mouseX, mouseY, leftX, y)) {
            gameplayConfig.returnCrystalEnabled = !gameplayConfig.returnCrystalEnabled;
            gameplayConfig.save();
            return true;
        }

        y = 72;
        if (hit(mouseX, mouseY, rightX, y)) { displayConfig.toggleSort("manual"); return true; }
        y += BUTTON_H + GAP;
        if (hit(mouseX, mouseY, rightX, y)) { displayConfig.toggleSort("distance"); return true; }
        y += BUTTON_H + GAP;
        if (hit(mouseX, mouseY, rightX, y)) { displayConfig.toggleSort("name"); return true; }
        y += BUTTON_H + GAP;
        if (hit(mouseX, mouseY, rightX, y)) { displayConfig.toggleSort("registered"); return true; }

        int bottomY = this.height - 32;
        if (isHover(mouseX, mouseY, this.width / 2 - 45, bottomY, 90, 22)) {
            this.close();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_E) {
            this.close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private boolean hit(double mouseX, double mouseY, int x, int y) {
        return isHover(mouseX, mouseY, x, y, BUTTON_W, BUTTON_H);
    }

    private boolean isHover(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
