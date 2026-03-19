package com.rubenverg.moldraw.mol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MOLConverter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ConversionResult convert(String molFilePath, String outputDirectory) throws IOException {
        // 解析MOL文件
        var jsonObject = MOLParser.parse(molFilePath);

        // 创建输出文件路径
        File molFile = new File(molFilePath);
        String fileName = molFile.getName().replace(".mol", ".json");
        File outputFile = new File(outputDirectory, fileName);

        // 确保输出目录存在
        outputFile.getParentFile().mkdirs();

        // 写入JSON文件
        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(jsonObject, writer);
        }

        return new ConversionResult(molFile.getName(), outputFile.getAbsolutePath(), true, null);
    }

    public interface ProgressCallback {

        void onProgress(int progress);
    }

    public static List<ConversionResult> batchConvert(List<String> molFilePaths, String outputDirectory,
                                                      ProgressCallback callback) {
        List<ConversionResult> results = new ArrayList<>();
        int totalFiles = molFilePaths.size();

        for (int i = 0; i < totalFiles; i++) {
            String filePath = molFilePaths.get(i);
            try {
                var result = convert(filePath, outputDirectory);
                results.add(result);
            } catch (Exception e) {
                results.add(new ConversionResult(new File(filePath).getName(), null, false, e.getMessage()));
            }

            // 更新进度
            if (callback != null) {
                int progress = (i + 1) * 100 / totalFiles;
                callback.onProgress(progress);
            }
        }

        return results;
    }

    public static List<ConversionResult> batchConvert(List<String> molFilePaths, String outputDirectory) {
        return batchConvert(molFilePaths, outputDirectory, null);
    }

    public static class ConversionResult {

        private final String fileName;
        private final String outputPath;
        private final boolean success;
        private final String errorMessage;

        public ConversionResult(String fileName, String outputPath, boolean success, String errorMessage) {
            this.fileName = fileName;
            this.outputPath = outputPath;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public String getFileName() {
            return fileName;
        }

        public String getOutputPath() {
            return outputPath;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
