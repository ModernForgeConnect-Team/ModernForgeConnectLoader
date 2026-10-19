# Modern Forge Connect Loader

[English](../README.md) · [简体中文](README.zh-CN.md) · [日本語](README.ja.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [한국어](README.ko.md)

Modern Forge Connect Loader (MFCL) ist eine experimentelle Kompatibilitätslaufzeit, mit der originale Forge-1.7.10-Mods innerhalb eines Hosts mit Forge 1.20.1 und Java 17 ausgeführt werden sollen.

## Zweck und Entwurfsziele

MFCL führt das echte alte FML und den originalen Mod-Code in einem isolierten Klassenladebereich innerhalb derselben JVM aus. Brücken verbinden unterstützte Spielobjekte, Registrierungen, Darstellung und Interaktionen mit dem modernen Spiel. Die ursprünglichen Mod-JARs bleiben unverändert.

Der Kern stellt allgemeine Mechanismen bereit; modspezifisches Verhalten gehört in optionale Adapter. Die Wissensverwaltung von Thaumcraft und die dynamische Darstellung des Thaumometers werden in die TC4-Kompatibilitätsschicht ausgelagert. Mit der Developer API / MFCL Legacy Accessor (MLA) können moderne Erweiterungen alten Code aufrufen, ohne beim Kompilieren von alten Minecraft-Klassen abhängig zu sein.

**Das Projekt ist noch in Entwicklung. Eine erfolgreiche Initialisierung oder ein sichtbares Item bedeutet keine vollständige Unterstützung der Spielfunktionen.**

## Voraussetzungen und Installation

| Komponente | Aktuelles Ziel |
| --- | --- |
| Host | Minecraft 1.20.1, Forge 47.4.28 |
| Java | JDK 17 |
| Alte Laufzeit | Minecraft 1.7.10, Forge 10.13.4.1614; separat vorbereitete SRG-Laufzeit |
| Geprüfte Grafikumgebung | Windows 11; Client und integrierter Server |

1. MFCL mit Java 17 bauen oder eine passende Version verwenden. Die vollständige `mfcl-0.1.0.jar` kommt in das Verzeichnis `mods/` des modernen Spiels.
2. Originale alte Mods und ihre erforderlichen Abhängigkeiten beschaffen und direkt in `mods/1.7.10/` ablegen, nicht im Hauptverzeichnis der modernen Mods.
3. Die private alte Laufzeit mit einem Manifest geprüfter Eingabedateien vorbereiten. Siehe [Testumgebung und Vorbereitung](../docs/testing.md). Weder die MFCL-JAR noch der Dependency Resolver enthalten oder installieren automatisch eine vollständige alte Minecraft-Laufzeit.
4. Forge starten und `logs/mfcl/legacy-1.7.10.json` sowie `logs/mfcl/runtime-1.7.10.json` auf erkannte Dateien, tatsächliche Ladephasen und Fehler prüfen.

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

MFCL verwendet das Verzeichnis des jeweiligen Spiels und liest nur JARs direkt in `mods/1.7.10/`. Originaldateien von Minecraft, Forge und Drittanbieter-Mods werden nicht mit MFCL verteilt.

## Implementierte Kompatibilitätsfunktionen

- Echte Initialisierung des alten FML sowie unterstützte CoreMod- und Access-Transformer im isolierten alten Bereich.
- Registrierung moderner Gegenstücke für Items und Blöcke, Erhalt von Metadata und originalem NBT, Erzeugung von Texturen und Übersetzungen sowie unterstütztes Platzieren, Droppen und Speichern.
- Unterstützte Darstellung alter GUIs, Schriften, Items, Blöcke und Block-Entities; ausgewählte dynamische Item-Darstellung und alte Soundeffekte.
- Teilweise Brücken für Welt, Spielerzustand, Inventar, Redstone und Block-Entity-Logik sowie unterstützte normale Werkbankrezepte.
- Experimentelle Developer API / MLA, explizite Objektkonvertierung, Diagnose und ein unabhängiges Werkzeug für Abhängigkeiten.

| Referenz-Mod | Geprüfter Umfang |
| --- | --- |
| NotEnoughItems 1.0.5.120 mit CodeChicken-Abhängigkeiten | Echte Initialisierung, Inventar-Overlay, Suche, originale Rezept- und Verwendungsseiten |
| AcademyCraft 1.0.7 mit LambdaLib 1.2.3 | Echte Initialisierung, grundlegende Inhaltsregistrierung und ausgewählte Platzierungs- / Speichertests |
| Thaumcraft 4.2.3.5 mit Baubles 1.0.1.10 | Echte Initialisierung, ausgewählte Darstellungs- und Spiellogikbrücken, Thaumonomicon, Scannen bei gedrückter Taste, Belohnungen, Speicherung und Ton |

Der [Index der technischen Dokumentation](../docs/README.md) enthält den genauen Umfang, Hashes der Originaldateien und datierte Abnahmeprotokolle. Die Ergebnisse gelten für die geprüften Versionen und Funktionen.

## Grenzen der Kompatibilität

MFCL ist noch kein vollständiger Forge-1.7.10-Client, kein universeller OpenGL-Emulator und kein Ersatz für sämtliche alten Spielsysteme. Beliebige Mods, eigene GUIs und Renderer, Maschinen, Weltgenerierung, Entities, Spielerfähigkeiten und Netzwerkprotokolle sind nur teilweise oder noch nicht unterstützt. Tests mit dem integrierten Server belegen keine Kompatibilität mit eigenständigen dedizierten Servern.

Nicht unterstützte oder verlustbehaftete Konvertierungen werden ausdrücklich abgelehnt. Moderne Blöcke oder Items besitzen möglicherweise keine alte Entsprechung. Allgemeine Ereignisweiterleitung und Entity- / Spielerkonvertierung sind noch nicht fertig. Normale Werkbankrezepte bedeuten keine Unterstützung für arkane Werkbänke, Tiegel, Infusion oder andere modspezifische Herstellungssysteme.

Die TC4-Prüfung abgeschlossener Scans und das Abfangen von Scan-Paketen verwenden weiterhin besondere Kompatibilitätspfade. Der schrittweise Umzug ist unter [API und Adaptergrenzen](../docs/developer-api.md) beschrieben. Alte Mod-Container verbleiben im alten FML. Ihre gemeinsame Anzeige in der Mod-Liste ist keine Registrierung im modernen ModList oder Netzwerk-Handshake.

## Beispiel für Developer API / MLA

Die eigenständige API als `compileOnly` einbinden. Weder die API in die Erweiterung einbetten noch alte Minecraft-Klassen in den modernen Laufzeit-Klassenpfad aufnehmen.

```groovy
dependencies {
    compileOnly files('libs/mfcl-0.1.0-api.jar')
}
```

Einen öffentlichen Accessor innerhalb der Erweiterung deklarieren und bei der Initialisierung abonnieren:

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

MLA verwendet genaue alte SRG-Namen und JVM-Deskriptoren. Alte Referenzen bleiben undurchsichtig und gehören zu genau einer Laufzeit. Spiellogik muss auf dem passenden Spiel-Thread ausgeführt werden. Imports, Felder, Konstruktoren, Konvertierung, Lebenszyklus und Grenzen erläutern das [kompilierbare Beispiel](../examples/legacy-accessor/) und die [API-Anleitung](../docs/developer-api.md). `/mfclapi` bietet Diagnose für Operatoren.

## Legacy Dependency Resolver

Das [unabhängige Gradle-Plugin](../tools/legacy-dependency-resolver/) nutzt die Abhängigkeitsauflösung von Gradle und erlaubt ausdrücklich angegebene lokale JAR-Importe. Eingaben benötigen einen festen SHA-256-Hash, Herkunft und Hinweise zu Lizenz und Weitergabe. Es fügt keine entfernten Repositories hinzu, löst keine transitiven Abhängigkeiten auf und installiert alte JARs nicht im modernen Laufzeit-Klassenpfad.

Ausgegeben werden inhaltsadressierte JARs und ein Herkunftsmanifest. Konfiguration, Fehlerverhalten und Tests stehen in der [Werkzeug-README](../tools/legacy-dependency-resolver/README.md).

## Bauen, Testen und Beitragen

Im Stammverzeichnis des Repositorys mit JDK 17 ausführen:

```powershell
.\gradlew.bat test build
.\gradlew.bat apiJar apiSourcesJar compileApiExample
.\gradlew.bat -p tools/legacy-dependency-resolver test validatePlugins
.\gradlew.bat inspectLegacyMods "-PlegacyGameDir=C:/path/to/game"
```

Die vollständige Mod und die API-Artefakte liegen in `build/libs/`. Außerhalb von Windows `./gradlew` verwenden. Referenzprüfungen ohne die benötigten externen Dateien werden übersprungen; grafische Abnahmetests benötigen ein vorbereitetes Spielverzeichnis. Vorbereitung, Smoke-Test-Parameter und getrennte Build-Ausgaben beschreibt [Testen und Reproduzierbarkeit](../docs/testing.md).

Beiträge sollten Ziel-Mod und Version, Reproduktionsschritte, Logs und erwartetes Verhalten nennen. Allgemeine Mechanismen gehören in den Kern, modspezifische Abläufe in Adapter. Gezielte Regressionstests beifügen, Original-JARs unverändert lassen und die bestehenden Regeln für Threads, Objektbesitz und Registrierung beachten.

## MPL-2.0-Lizenz

MFCL steht unter der [Mozilla Public License 2.0 (MPL-2.0)](../LICENSE). Minecraft, Forge und Drittanbieter-Mods behalten ihre eigenen Lizenzen. Die MFCL-Lizenz gewährt keine Rechte zur Weitergabe ihrer Dateien.
