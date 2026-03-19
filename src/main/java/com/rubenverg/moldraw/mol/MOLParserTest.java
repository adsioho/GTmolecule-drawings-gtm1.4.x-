package com.rubenverg.moldraw.mol;

import com.google.gson.JsonObject;

import java.io.IOException;

public class MOLParserTest {

    public static void main(String[] args) {
        try {
            // 测试解析MOL文件
            JsonObject json = MOLParser.parse("test.mol");
            System.out.println("MOL file parsed successfully!");
            System.out.println(json.toString());

            // 测试转换功能
            var result = MOLConverter.convert("test.mol", "output");
            if (result.isSuccess()) {
                System.out.println("Conversion successful! Output file: " + result.getOutputPath());
            } else {
                System.out.println("Conversion failed: " + result.getErrorMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
