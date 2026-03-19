package com.rubenverg.moldraw.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MolDrawConfigScreen extends Screen {

    private final Screen parent;

    public MolDrawConfigScreen(Screen parent) {
        super(Component.translatable("moldraw.gui.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int rowHeight = 30;

        // MOL Converter button
        Button molConverterButton = Button
                .builder(Component.translatable("moldraw.gui.mol_converter.title"), button -> {
                    this.minecraft.setScreen(new MOLConverterScreen());
                }).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(molConverterButton);

        // Back button
        Button backButton = Button.builder(Component.translatable("gui.back"), button -> {
            this.minecraft.setScreen(parent);
        }).bounds(centerX - buttonWidth / 2, startY + rowHeight * 2, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(backButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Render description
        guiGraphics.drawCenteredString(this.font, Component.translatable("moldraw.gui.config.description"),
                this.width / 2, 40, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
