package com.rubenverg.moldraw.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import com.rubenverg.moldraw.mol.MOLConverter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MOLConverterScreen extends Screen {

    private EditBox inputPathField;
    private EditBox outputPathField;
    private Button selectInputButton;
    private Button selectOutputButton;
    private Button convertButton;
    private Button refreshButton;
    private List<String> selectedFiles;
    private String statusMessage;
    private boolean isConverting;
    private int conversionProgress;

    public MOLConverterScreen() {
        super(Component.translatable("moldraw.gui.mol_converter.title"));
        this.selectedFiles = new ArrayList<>();
        this.statusMessage = "";
        this.isConverting = false;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;
        int fieldWidth = 200;
        int buttonWidth = 80;
        int rowHeight = 30;

        // 输入路径
        inputPathField = new EditBox(this.font, centerX - fieldWidth / 2, startY, fieldWidth, 20,
                Component.translatable("moldraw.gui.mol_converter.input_path"));
        inputPathField.setMaxLength(512);
        this.addRenderableWidget(inputPathField);

        selectInputButton = Button.builder(Component.translatable("moldraw.gui.mol_converter.select"), button -> {
            try {
                // 使用输入的路径作为文件路径
                String inputPath = inputPathField.getValue();
                if (!inputPath.isEmpty()) {
                    File file = new File(inputPath);
                    if (file.exists() && file.isFile() && file.getName().endsWith(".mol")) {
                        selectedFiles.clear();
                        selectedFiles.add(inputPath);
                        statusMessage = "File selected: " + file.getName();
                    } else {
                        statusMessage = "Invalid MOL file: " + inputPath;
                    }
                } else {
                    statusMessage = "Please enter a MOL file path";
                }
            } catch (Exception e) {
                statusMessage = "Error selecting file: " + e.getMessage();
            }
        }).bounds(centerX + fieldWidth / 2 + 5, startY, buttonWidth, 20).build();
        this.addRenderableWidget(selectInputButton);

        // 输出路径
        outputPathField = new EditBox(this.font, centerX - fieldWidth / 2, startY + rowHeight, fieldWidth, 20,
                Component.translatable("moldraw.gui.mol_converter.output_path"));
        outputPathField.setMaxLength(512);
        // 设置默认输出目录为mod的资源包目录
        outputPathField.setValue(FMLPaths.GAMEDIR.get().resolve("resourcepacks").resolve("moldraw_molecules")
                .resolve("assets").resolve("gtceu").resolve("molecules").toString());
        this.addRenderableWidget(outputPathField);

        selectOutputButton = Button.builder(Component.translatable("moldraw.gui.mol_converter.select"), button -> {
            try {
                // 使用输入的路径作为目录路径
                String outputPath = outputPathField.getValue();
                if (!outputPath.isEmpty()) {
                    File directory = new File(outputPath);
                    // 尝试创建目录（如果不存在）
                    if (!directory.exists()) {
                        boolean created = directory.mkdirs();
                        if (created) {
                            statusMessage = "Directory created: " + outputPath;
                        } else {
                            statusMessage = "Failed to create directory: " + outputPath;
                        }
                    } else if (directory.isDirectory()) {
                        statusMessage = "Directory selected: " + outputPath;
                    } else {
                        statusMessage = "Invalid directory: " + outputPath;
                    }
                } else {
                    statusMessage = "Please enter an output directory path";
                }
            } catch (Exception e) {
                statusMessage = "Error selecting directory: " + e.getMessage();
            }
        }).bounds(centerX + fieldWidth / 2 + 5, startY + rowHeight, buttonWidth, 20).build();
        this.addRenderableWidget(selectOutputButton);

        // 转换按钮
        convertButton = Button.builder(Component.translatable("moldraw.gui.mol_converter.convert"), button -> {
            if (!isConverting && !selectedFiles.isEmpty()) {
                startConversion();
            }
        }).bounds(centerX - 100, startY + rowHeight * 2, 200, 20).build();
        this.addRenderableWidget(convertButton);

        // 刷新按钮
        refreshButton = Button.builder(Component.translatable("moldraw.gui.mol_converter.refresh"), button -> {
            // 触发资源重载
            Minecraft.getInstance().reloadResourcePacks();
            statusMessage = "Resources reloaded!";
        }).bounds(centerX - 100, startY + rowHeight * 3, 200, 20).build();
        this.addRenderableWidget(refreshButton);
    }

    private void startConversion() {
        isConverting = true;
        convertButton.active = false;
        conversionProgress = 0;
        statusMessage = "Converting...";

        CompletableFuture.runAsync(() -> {
            try {
                List<String> files = selectedFiles;
                String outputDir = outputPathField.getValue();

                var results = MOLConverter.batchConvert(files, outputDir, progress -> {
                    // 在主线程中更新进度
                    Minecraft.getInstance().execute(() -> {
                        conversionProgress = progress;
                    });
                });

                int successCount = 0;
                int errorCount = 0;
                for (var result : results) {
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        errorCount++;
                    }
                }

                statusMessage = String.format("Conversion complete: %d success, %d error", successCount, errorCount);
            } catch (Exception e) {
                statusMessage = "Error: " + e.getMessage();
            } finally {
                isConverting = false;
                convertButton.active = true;
            }
        });
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int startY = 60;
        int fieldWidth = 200;

        // 渲染标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // 渲染输入路径标签
        guiGraphics.drawString(this.font, Component.literal("Input MOL Files:"), centerX - fieldWidth / 2 - 100,
                startY - 10, 0xFFFFFF);

        // 渲染输出路径标签
        guiGraphics.drawString(this.font, Component.literal("Output Directory:"), centerX - fieldWidth / 2 - 100,
                startY + 20, 0xFFFFFF);

        // 渲染状态消息
        if (!statusMessage.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.literal(statusMessage), this.width / 2,
                    this.height - 40, 0xFFFFFF);
        }

        // 渲染转换进度
        if (isConverting) {
            int progressBarWidth = 200;
            int progressBarHeight = 20;
            int progressX = centerX - progressBarWidth / 2;
            int progressY = this.height - 70;

            // 绘制进度条背景
            guiGraphics.fill(progressX, progressY, progressX + progressBarWidth, progressY + progressBarHeight,
                    0xFF444444);
            // 绘制进度条
            int progress = (int) (conversionProgress * progressBarWidth / 100);
            guiGraphics.fill(progressX, progressY, progressX + progress, progressY + progressBarHeight, 0xFF00FF00);
            // 绘制进度文本
            guiGraphics.drawCenteredString(this.font, Component.literal("Progress: " + conversionProgress + "%"),
                    centerX, progressY + 5, 0xFFFFFF);
        }

        // 渲染选中文件数量
        if (!selectedFiles.isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("Selected files: " + selectedFiles.size()), 20, 20,
                    0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
