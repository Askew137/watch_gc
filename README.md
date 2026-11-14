# GC Watch 🧭

Offline geocaching navigátor pro **OnePlus Watch 2** (Wear OS 4) s companion aplikací pro Android.

## Funkce
- 🗺️ Offline vektorové mapy (Mapsforge `.map` soubory)
- 📍 Přímý přístup k L1+L5 GPS hardware hodinek (bypass Bluetooth tellhering)
- 🧭 Duální rozhraní: **Mapa** ↔ **Kompas/Radar**
- 🎯 Cíl z odkazu `geo:` (c:geo apod.): na mapě značka a vzdušná čára; jedním tlačítkem přepínáš vzdušná čára → trasa → jen cíl (trasu pošle telefon, jakmile bude routovací zdroj – zatím není)
- 🌙 Night Red mód pro zachování tmavého vidění
- 🔄 Synchronizace nastavení mezi hodinkami a telefonem

## Požadavky

| | Verze |
|---|---|
| Android Studio | Ladybug nebo novější |
| JDK | 17 (Temurin doporučen) |
| Android SDK | API 35 |
| Wear OS | 3+ (API 30+) |
| Kotlin | 2.0.21 |

## Rychlý start

```bash
# 1. Klonuj repozitář
git clone https://github.com/Askew137/watch_gc.git
cd watch_gc

# 2. Otevři v Android Studio (automaticky nastaví Gradle wrapper)
# NEBO inicializuj wrapper ručně (vyžaduje Gradle 8.9+ na PATH):
gradle wrapper --gradle-version=8.9

# 3. Build
./gradlew :mobile:assembleDebug :wear:assembleDebug
```

## Stažení map

Aplikace na telefonu obsahuje průvodce. Potřebuješ soubor s mapou:

| Soubor | Použití | Zdroj |
|--------|---------|-------|
| `*.map` (Mapsforge) | Offline mapa na hodinkách | [OpenAndroMaps.org](https://www.openandromaps.org) nebo [BBBike.org](https://extract.bbbike.org) |

## Struktura projektu

```
├── core/
│   ├── model/          – Sdílené datové třídy (Waypoint, AppConfig, ...)
│   └── communication/  – Protobuf serializace, MessagePaths konstanty
├── mobile/             – Android companion app (geo: intent, mapy, routing)
└── wear/               – Standalone Wear OS aplikace
```

## Licence
MIT
