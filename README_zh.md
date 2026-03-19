# GregTech 分子结构图

[English](README.md) | 中文

这款仅限客户端的模组为来自 GregTech CEu 及其附属和模组包中的有机分子在物品提示（Tooltip）中添加了分子结构图。
GregTechModern 1.x 特供版，去除了对高版本(gtm7+)的一些依赖，计划更新gtl/gto的化学品分子结构图，由gtl/gto群u赞助构建！

附言：“此mod的目的在于通过对许多不同产线造出的材料的分子结构式的显示，以此体会到你建造的那些生产聚苯并咪唑，凯夫拉，CL-20(66粉)之类的产线的产物在现实中的样子，给玩家一种”原来我造的这个产线能搞出来这么NB的东西“的成就感”

## 示例

| 颜色模式        | 聚乙烯 (Polyethylene)                                                                                                                            | 聚苯并咪唑 (Polybenzimidazole)                                                                                                                                      | 氯仿 (Chloroform)                                                                                                                                     |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| 无 (None)       | ![polyethylene tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/polyethylene.png) | ![polybenzimidazole tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/polybenzimidazole.png)          | ![chloroform tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/chloroform.png)          |
| CPK             | ![polyethylene tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/polyethylene.png) | ![polybenzimidazole tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/polybenzimidazole_color.png)    | ![chloroform tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/chloroform_color.png)    |
| 材质 (Material) | ![polyethylene tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/polyethylene.png) | ![polybenzimidazole tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/polybenzimidazole_matcolor.png) | ![chloroform tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/chloroform_matcolor.png) |

### 合金

| 模式                   | HSS-E                                                                                                                                       |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| 按质量 (By mass)       | ![hss-e tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/hsse.png)           |
| 非递归 (Not recursive) | ![hss-e tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/hsse_norecurse.png) |
| 按数量 (By count)      | ![hss-e tooltip screenshot](https://raw.githubusercontent.com/RubenVerg/gregtech-molecule-drawings/refs/heads/main/images/hsse_bycount.png)   |

## 额外内容支持

MolDraw 开箱即用，不仅支持基础 GTCEu 中的大多数有机材料和合金，还支持许多附属模组和模组包。目前支持的扩展包括：

* Monifactory
* Star Technology
* Cosmic Frontiers
* Phoenix Forge Technologies
* TerraFirmaGreg
* GT--
* GregTech Community Additions
* Gregicality Rocketry
* GregTech Leisure (仅兼容性修复)
* GregTech Odyssey (长期支持)

## 玩家评价

> 这可能是我见过的有人在物品提示上做的最酷的事情。

> 我每天都在提醒自己，基本上所有的有机化学都是苯环加上一些突出来的部分。

> 我唯一想说的是，这简直是巅峰之作。

> 现在我可以欣赏我建造的聚合物有多复杂了。

> 这已经很酷了，颜色让它变得更棒！

> 我应该被允许给 Gregtech 附属模组颁发米其林星，因为 Madeline 做得太出色了。

> 极其没用，但又极其酷！

## 添加你自己的分子

分子存储在资源包的 `assets/<namespace>/molecules/<compound>.json` 下，对应 ID 为 `<namespace>:<compound>` 的 GT 材料。

分子遵循以下模式：

```typescript
// ARGB 颜色或 RGB 十六进制字符串，将始终使用，或具有相同属性的对象以及是否可通过配置禁用的规范
type ElementColor = number | string | {
  color: number | string;
  optional: boolean;
};

// 仅一个符号，可用于普通原子，或指定符号、是否不可见以及颜色的对象
type Element = string | {
  symbol: string;
  invisible?: boolean; // 旨在具有不同颜色的新不可见元素仍需要与空字符串不同的符号；无论哪种方式它都不会显示
  color?: ElementColor;
  material?: string; // 与此元素关联的 GregTech 材料的 ID，用于按材质设置颜色。
}; 

// 原子数量默认为 1
type CountedElement = string | [string, number];

interface AtomCommon {
  type: 'atom';
  index: number; // 用于引用此原子的标识符
  element?: CountedElement; // 原子的主要元素，如果原子应该是不可见的碳，则不存在
  above?: CountedElement; // 在主要元素上方显示的元素
  right?: CountedElement; // 等等
  below?: CountedElement;
  left?: CountedElement;
  spin_group?: number; // 具有相同组的原子以相同的速率旋转，如果它是分子 spin 属性中的有效索引
}

// 原子可以指定 X 和 Y 坐标...
interface AtomXY extends AtomCommon {
  x: number;
  y: number;
}

// 或 U 和 V 坐标，它们是分别从正 X 半轴逆时针和顺时针倾斜 30 度的单位向量...
interface AtomUV extends AtomCommon {
  u: number;
  v: number;
}

// 或完整的 3D X、Y 和 Z 坐标。
interface AtomXYZ extends AtomCommon {
  x: number;
  y: number;
  z: number;
}

// 向内和向外的键在向第二个原子延伸时尺寸增大。
type Line = 'solid' | 'dotted' | 'inward' | 'outward' | 'thick';

interface Bond {
  type: 'bond';
  a: number; // 键的第一个原子
  b: number; // 键的第二个原子
  centered?: boolean // 如果线条可以偏移半个单位以居中，则为 True
  lines: Line[];
}

// 括号是围绕某些原子的方括号，显示下标和/或上标。
interface Parens {
  type: 'parens';
  sup?: string; // 上标文本
  sub?: string; // 下标文本
  atoms: number[]; // 要被包围的原子的列表
}

// 在原子循环内绘制的圆；自动居中到它们的质心。
interface CircleCommon {
  type: 'circle';
  atoms: number[];
}

// 按 x 和 y 缩放的轴对齐圆。
interface CircleXY extends CircleCommon {
  x: number;
  y: number;
}

// 应用于单位圆的线性变换。
interface CircleMatrix extends CircleCommon {
  a00: number;
  a01: number;
  a10: number;
  a11: number;
}

type SpinSpec
  = boolean // 使用默认速度启用旋转
  | number // 启用旋转并设置速度
  | number[]; // 为多个组设置速度

type MoleculeElement = AtomXY | AtomUV | AtomXYZ | Bond | Parens | CircleXY | CircleMatrix;

// 分子 JSON 文件属于 Molecule 类型。
interface Molecule {
  contents: MoleculeElement[];
  spin?: SpinSpec;
}
```

原子的位置既可以存储为一对 `x` 和 `y` 坐标，也可以存储为一对 `u` and `v` 坐标，它们是分别从正水平半轴逆时针和顺时针倾斜 30 度的单位向量。通常，使用 `u` 和 `v` 编码位置更容易，因为有机化合物的骨架图尽可能地遵循六边形网格。

例如，这是苯的编码：

```json
{
  "contents": [
    {
      "type": "atom",
      "index": 0,
      "u": 0.0,
      "v": 0.0
    },
    {
      "type": "atom",
      "index": 1,
      "u": -1.0,
      "v": 1.0
    },
    {
      "type": "atom",
      "index": 2,
      "u": -1.0,
      "v": 2.0
    },
    {
      "type": "atom",
      "index": 3,
      "u": 0.0,
      "v": 2.0
    },
    {
      "type": "atom",
      "index": 4,
      "u": 1.0,
      "v": 1.0
    },
    {
      "type": "atom",
      "index": 5,
      "u": 1.0,
      "v": 0.0
    },
    {
      "type": "bond",
      "a": 0,
      "b": 1
    },
    {
      "type": "bond",
      "a": 1,
      "b": 2,
      "lines": ["solid", "solid"]
    },
    {
      "type": "bond",
      "a": 2,
      "b": 3
    },
    {
      "type": "bond",
      "a": 3,
      "b": 4,
      "lines": ["solid", "solid"]
    },
    {
      "type": "bond",
      "a": 4,
      "b": 5
    },
    {
      "type": "bond",
      "a": 5,
      "b": 0,
      "lines": ["solid", "solid"]
    }
  ]
}
```

## 添加你自己的合金

类似地，合金存储在资源包的 `assets/<namespace>/alloys/<compound>.json` 下，对应 ID 为 `<namespace>:<compound>` 的 GT 材料。

在最简单的情况下，材料只需要被标记为合金，然后就可以通过 GregTech 的材料成分系统自动计算其成分。在这种情况下，只需将以下文件放在正确的位置：

```json
{
  "derive": true
}
```

如果你想手动指定组件，例如指示材料成分中不存在的杂质，合金遵循这个简单的模式：

```ts
// 材料 ID 和整数数量，默认为 1
type Datum = string | [string, number];

// 合金 JSON 文件属于 Alloy 类型
interface Alloy {
  components: Datum[];
}
```
