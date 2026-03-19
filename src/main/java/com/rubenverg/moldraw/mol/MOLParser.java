package com.rubenverg.moldraw.mol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rubenverg.moldraw.MolDraw;
import com.rubenverg.moldraw.molecule.Molecule;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class MOLParser {

    public static JsonObject parse(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            return parseReader(reader);
        }
    }

    public static Molecule parseToMolecule(String molContent) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(molContent))) {
            JsonObject json = parseReader(reader);
            return MolDraw.gson.fromJson(json, Molecule.class);
        }
    }

    private static JsonObject parseReader(BufferedReader reader) throws IOException {
        // 跳过前3行（标题、程序、注释）
        reader.readLine();
        reader.readLine();
        reader.readLine();

        // 读取计数行
        String countsLine = reader.readLine().trim();
        int atomCount = Integer.parseInt(countsLine.substring(0, 3).trim());
        int bondCount = Integer.parseInt(countsLine.substring(3, 6).trim());

        // 读取原子块
        List<Atom> atoms = new ArrayList<>();
        for (int i = 0; i < atomCount; i++) {
            String line = reader.readLine();
            if (line == null) break;

            double x = Double.parseDouble(line.substring(0, 10).trim());
            double y = Double.parseDouble(line.substring(10, 20).trim());
            // 忽略z坐标
            String element = line.substring(31, 34).trim();
            if (element.isEmpty()) element = "C"; // 默认碳

            atoms.add(new Atom(i, element, x, y));
        }

        // 读取键块
        List<Bond> bonds = new ArrayList<>();
        for (int i = 0; i < bondCount; i++) {
            String line = reader.readLine();
            if (line == null) break;

            int atom1 = Integer.parseInt(line.substring(0, 3).trim()) - 1; // MOL使用1-based索引
            int atom2 = Integer.parseInt(line.substring(3, 6).trim()) - 1;
            int bondType = Integer.parseInt(line.substring(6, 9).trim());

            bonds.add(new Bond(atom1, atom2, bondType));
        }

        // 构建JSON对象
        return buildJson(atoms, bonds);
    }

    private static JsonObject buildJson(List<Atom> atoms, List<Bond> bonds) {
        JsonObject result = new JsonObject();
        JsonArray contents = new JsonArray();

        // 添加原子
        for (Atom atom : atoms) {
            JsonObject atomObj = new JsonObject();
            atomObj.addProperty("index", atom.index);

            // 创建element对象
            JsonObject elementObj = new JsonObject();
            elementObj.addProperty("element", atom.element);
            elementObj.addProperty("count", 1);
            atomObj.add("element", elementObj);

            atomObj.addProperty("x", atom.x);
            atomObj.addProperty("y", atom.y);
            atomObj.addProperty("type", "atom");
            contents.add(atomObj);
        }

        // 添加键
        for (Bond bond : bonds) {
            JsonObject bondObj = new JsonObject();
            bondObj.addProperty("a", bond.atom1);
            bondObj.addProperty("b", bond.atom2);

            JsonArray lines = new JsonArray();
            if (bond.bondType == 1) {
                lines.add("solid");
            } else if (bond.bondType == 2) {
                lines.add("solid");
                lines.add("solid");
                bondObj.addProperty("centered", true);
            } else if (bond.bondType == 3) {
                lines.add("solid");
                lines.add("solid");
                lines.add("solid");
                bondObj.addProperty("centered", true);
            } else if (bond.bondType == 4) {
                lines.add("solid");
                lines.add("dotted");
            }

            bondObj.add("lines", lines);
            bondObj.addProperty("type", "bond");
            contents.add(bondObj);
        }

        result.add("contents", contents);
        return result;
    }

    private static class Atom {

        int index;
        String element;
        double x;
        double y;

        Atom(int index, String element, double x, double y) {
            this.index = index;
            this.element = element;
            this.x = x;
            this.y = y;
        }
    }

    private static class Bond {

        int atom1;
        int atom2;
        int bondType;

        Bond(int atom1, int atom2, int bondType) {
            this.atom1 = atom1;
            this.atom2 = atom2;
            this.bondType = bondType;
        }
    }
}
