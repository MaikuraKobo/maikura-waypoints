# Maikura Waypoints v3.2.0

Minecraft Fabric 1.21.11 用のウェイポイントMODです。

## 主な機能
- 通常ウェイポイント
- Ancient Waypoint / Inactive Ancient Waypoint
- 停止したウェイポイントとネザライトインゴットによる再起動
- GUIからのワープ、HOME設定、名前変更、並び替え、削除
- ワープコスト ON/OFF
- 帰還クリスタル ON/OFF（OFF時は使用不可・クラフト取得不可）
- GUI Editor対応
- Ancient / Inactive Ancient Waypointの自然生成

## 設定

### Mod Menu
- ワープコスト ON/OFF
- 帰還クリスタル ON/OFF（OFF時は使用不可・クラフト取得不可）
- ソート項目 ON/OFF（手動順 / 距離順 / 名前順 / 登録順）

### config/maikura_waypoints_worldgen.json
自然生成系の設定はMod Menuではなくconfigファイルで管理します。

```json
{
  "generationChance": 1,
  "minimumDistance": 768,
  "ignoreDistanceChance": 0
}
```

## 対応環境
- Minecraft 1.21.11
- Fabric

## ライセンス
All Rights Reserved (ARR)
