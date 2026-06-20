package jp.exnakamura.maikurawaypoints;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class WaypointRenameScreen extends Screen {
    private final WaypointListScreen.Entry target;
    private final List<WaypointListScreen.Entry> entries;
    private TextFieldWidget nameField;

    public WaypointRenameScreen(WaypointListScreen.Entry target, List<WaypointListScreen.Entry> entries) {
        super(Text.literal("ウェイポイント名変更"));
        this.target = target;
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, Math.max(300, this.width - 60));
        int x = (this.width - panelWidth) / 2;
        int y = Math.max(20, (this.height - 120) / 2);

        this.nameField = new TextFieldWidget(this.textRenderer, x + 16, y + 42, panelWidth - 32, 20, Text.literal("名前"));
        this.nameField.setMaxLength(24);
        this.nameField.setText(target.cleanDisplayName());
        this.nameField.setFocused(true);
        this.addDrawableChild(this.nameField);
        this.setFocused(this.nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> saveAndClose())
                .dimensions(x + 16, y + 78, 80, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("キャンセル"), button -> this.client.setScreen(new WaypointListScreen(target, entries)))
                .dimensions(x + panelWidth - 96, y + 78, 80, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        int panelWidth = Math.min(360, Math.max(300, this.width - 60));
        int x = (this.width - panelWidth) / 2;
        int y = Math.max(20, (this.height - 120) / 2);
        context.fill(x - 2, y - 2, x + panelWidth + 2, y + 118, 0xFF777777);
        context.fill(x, y, x + panelWidth, y + 116, 0xDD101418);
        context.drawText(this.textRenderer, Text.literal("現在のウェイポイント名変更"), x + 16, y + 12, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.literal("開いているウェイポイントだけ変更します"), x + 16, y + 28, 0xFFD9D9D9, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private void saveAndClose() {
        String value = nameField == null ? "" : nameField.getText().trim();
        if (value.isBlank()) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal("名前を入力してください。"), true);
            }
            return;
        }

        ClientPlayNetworking.send(new WaypointRenamePayload(target.key(), value));
        WaypointListScreen.Entry updatedTarget = new WaypointListScreen.Entry(target.key(), value, target.dimension(), target.distance(), target.ancient(), target.home(), target.favorite(), target.x(), target.y(), target.z(), target.order());
        if (this.client != null) {
            this.client.setScreen(new WaypointListScreen(updatedTarget, entries));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
