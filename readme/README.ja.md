# Modern Forge Connect Loader

[English](../README.md) · [简体中文](README.zh-CN.md) · [日本語](README.ja.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [한국어](README.ko.md)

Modern Forge Connect Loader（MFCL）は、Forge 1.20.1 / Java 17 のホスト上で、オリジナルの Forge 1.7.10 MOD を動かすための実験的な互換ランタイムです。

## プロジェクトの目的と設計方針

ホストと同じ JVM 内の隔離されたクラスロード領域で、実際の旧 FML とオリジナルの MOD コードを実行します。対応済みのゲームオブジェクト、レジストリ、描画、操作をブリッジ経由で現代版に接続し、元の MOD JAR は変更しません。

コアは汎用機構を担当し、MOD 固有の処理は任意のアダプターに配置します。Thaumcraft の知識管理とソーモメーターの動的描画は TC4 互換層へ分離を進めています。Developer API / MFCL Legacy Accessor（MLA）を使うと、現代版のアドオンは旧 Minecraft のクラスにコンパイル時依存することなく、旧コードを呼び出せます。

**開発途上のプロジェクトです。初期化の成功やアイテムの表示は、ゲーム機能全体の互換性を意味しません。**

## 動作環境と導入

| 構成要素 | 現在の対象 |
| --- | --- |
| ホスト | Minecraft 1.20.1、Forge 47.4.28 |
| Java | JDK 17 |
| 旧ランタイム | Minecraft 1.7.10、Forge 10.13.4.1614。個別に準備した SRG ランタイム |
| 描画の検証環境 | Windows 11。クライアントと統合サーバー |

1. Java 17 でビルドするか、対応するビルドを用意し、完全版の `mfcl-0.1.0.jar` を現代版ゲームの `mods/` に配置します。
2. オリジナルの旧 MOD と必須の依存 MOD を入手し、現代版 MOD のルートではなく `mods/1.7.10/` に直接配置します。
3. 旧ランタイムと検証済み入力のマニフェストを準備します。[テスト環境と準備](../docs/testing.md)を参照してください。MFCL の JAR と依存関係リゾルバーには完全な旧 Minecraft ランタイムは含まれず、自動インストールも行いません。
4. Forge を起動し、`logs/mfcl/legacy-1.7.10.json` と `logs/mfcl/runtime-1.7.10.json` で検出結果、実際のロード段階、エラーを確認します。

```text
<game-directory>/
  mods/
    mfcl-0.1.0.jar
    1.7.10/
      original-mod.jar
      required-legacy-dependency.jar
  mfcl/runtime/1.7.10/
    runtime-manifest.json
    minecraft-forge-1.7.10-srg.jar
    ...
```

ゲーム自身のディレクトリを使用し、`mods/1.7.10/` 直下の JAR のみを調べます。Minecraft、Forge、第三者製 MOD の元ファイルは MFCL に同梱しません。

## 実装済みの互換機能

- 隔離された旧領域での実際の FML 初期化と、対応済みの CoreMod / Access Transformer。
- 現代版アイテム / ブロックの代理登録、metadata と元の NBT の保持、テクスチャ / 言語リソース生成、対応済みの設置・ドロップ・保存。
- 対応済みの旧 GUI、フォント、アイテム、ブロック、ブロックエンティティの描画。一部の動的アイテム描画と旧効果音。
- ワールド、プレイヤー状態、インベントリ、レッドストーン、ブロックエンティティ処理の一部と、対応済みの通常作業台レシピ。
- 実験的 Developer API / MLA、明示的なオブジェクト変換、診断、独立した依存関係ツール。

| 参照 MOD | 検証済みの範囲 |
| --- | --- |
| NotEnoughItems 1.0.5.120 と CodeChicken 依存ライブラリ | 実際の初期化、インベントリ周辺の UI、検索、元のレシピ / 用途画面 |
| AcademyCraft 1.0.7 と LambdaLib 1.2.3 | 実際の初期化、基本コンテンツの登録、一部の設置 / 保存テスト |
| Thaumcraft 4.2.3.5 と Baubles 1.0.1.10 | 実際の初期化、一部の描画とゲーム処理、ソーモノミコン、長押しスキャン・報酬・保存、効果音 |

詳細な対応範囲、元ファイルのハッシュ、日付付きの検証記録は[技術文書一覧](../docs/README.md)を参照してください。結果は検証したバージョンと機能に限られます。

## 互換性の制限

完全な Forge 1.7.10 クライアント、汎用 OpenGL エミュレーター、すべての旧ゲーム機構の代替ではありません。任意の MOD、独自 GUI / 描画、機械、ワールド生成、エンティティ、プレイヤー能力、ネットワークプロトコルには未対応の部分があります。統合サーバーでの検証だけでは専用サーバーの互換性は保証できません。

未対応または情報が失われる変換は明示的に拒否します。現代版専用のブロック / アイテムには旧版での表現がない場合があります。汎用イベント転送とエンティティ / プレイヤー変換は未完成です。通常作業台の対応は、魔法作業台、るつぼ、注入など MOD 独自のクラフトシステムへの対応を意味しません。

TC4 のスキャン完了検証とスキャンパケット捕捉には専用処理が残っています。段階的な移行は [API とアダプターの境界](../docs/developer-api.md)を参照してください。旧 MOD コンテナは旧 FML に属し、MOD 一覧での統合表示は現代版 ModList やネットワークへの登録とは異なります。

## Developer API / MLA の例

API は `compileOnly` で参照します。アドオンに同梱せず、旧 Minecraft クラスを現代版のランタイムクラスパスに追加しないでください。

```groovy
dependencies {
    compileOnly files('libs/mfcl-0.1.0-api.jar')
}
```

アドオン内に公開 accessor を宣言し、初期化時に登録します。

```java
@LegacyType("net.minecraft.item.ItemStack")
public interface OldStack {
    @LegacyMethod(name = "func_77946_l",
                  descriptor = "()Lnet/minecraft/item/ItemStack;")
    LegacyObject copy();
}

MfclApi.whenReady(runtime -> {
    LegacyObject stack = runtime.stack(
        new LegacyStack("minecraft:diamond", 0, 2, ""));
    LegacyObject copy = runtime.accessor(OldStack.class, stack).copy();
});
```

正確な旧 SRG 名と JVM ディスクリプターを使います。旧参照は不透明で、単一のランタイムに属します。ゲーム処理は対応するゲームスレッドで呼び出してください。インポート、フィールド / コンストラクター、変換、ライフサイクル、制限は[コンパイル可能な例](../examples/legacy-accessor/)と [API ガイド](../docs/developer-api.md)に記載しています。管理者用診断は `/mfclapi` で実行できます。

## Legacy Dependency Resolver

[独立した Gradle プラグイン](../tools/legacy-dependency-resolver/)は Gradle の依存関係解決を利用し、明示的なローカル JAR インポートにも対応します。固定 SHA-256、入手元、ライセンス / 再配布条件の記録が必要です。リモートリポジトリの追加、推移的依存関係の解決、旧 JAR の現代版クラスパスへのインストールは行いません。

内容のハッシュで識別される JAR と出所マニフェストを出力します。設定、失敗時の動作、テストは[ツールの説明](../tools/legacy-dependency-resolver/README.md)を参照してください。

## ビルド、テスト、貢献

リポジトリのルートから JDK 17 で実行します。

```powershell
.\gradlew.bat test build
.\gradlew.bat apiJar apiSourcesJar compileApiExample
.\gradlew.bat -p tools/legacy-dependency-resolver test validatePlugins
.\gradlew.bat inspectLegacyMods "-PlegacyGameDir=C:/path/to/game"
```

完全版 MOD と API の成果物は `build/libs/` に出力されます。Windows 以外では `./gradlew` を使います。外部入力がない参照テストはスキップされます。描画の検証には準備済みのゲームディレクトリが必要です。[テストと再現手順](../docs/testing.md)に準備、スモークテストのフラグ、分離ビルドの説明があります。

貢献時は対象 MOD / バージョン、再現手順、ログ、期待する動作を記載してください。汎用機構はコア、固有処理はアダプターに配置し、焦点を絞った回帰検証を添えてください。元の JAR と既存のスレッド、所有権、登録契約を維持します。

## MPL-2.0 ライセンス

MFCL は [Mozilla Public License 2.0（MPL-2.0）](../LICENSE) で提供します。Minecraft、Forge、第三者製 MOD にはそれぞれのライセンスが適用されます。MFCL のライセンスは、それらのファイルを再配布する権利を付与しません。
