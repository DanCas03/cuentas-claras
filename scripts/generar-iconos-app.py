"""Genera los íconos de la app por forma × paleta oscura (spec 2026-10-17 §7).

Uso, desde la raíz del repo:  python -I scripts/generar-iconos-app.py

Escribe (y sobrescribe) en android/app/src/main/res/:
- drawable/ic_launcher_<forma>_<paleta>.xml   primer plano con el trazo en `primario` de la paleta
- drawable/ic_launcher_mono_<forma>.xml        capa monocroma (blanco) de cada forma
- mipmap-anydpi-v26/ic_launcher_<forma>_<paleta>.xml   ícono adaptable (fondo = `fondo` de la paleta)
- values/colores_lanzador.xml                  los colores de fondo por paleta
Y imprime el bloque de <activity-alias> para pegar en AndroidManifest.xml.

Los ids de paleta y los hex salen de android/DESIGN.md §2.1 (oscuras). Las formas viven aquí; es la fuente de verdad.
"""
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent / "android" / "app" / "src" / "main"
RES = RAIZ / "res"

# id → (fondo, primario)
PALETAS_OSCURAS = {
    "grafito_menta": ("#0F110F", "#2DD4BF"),
    "grafito_lima": ("#0F110F", "#C5F05A"),
    "noche_lima": ("#0A0F1C", "#C5F05A"),
    "como_antes": ("#0A0F1C", "#2DD4BF"),
    "cacao": ("#1B1411", "#FFA24C"),
    "pizarra_cobalto": ("#0E1117", "#8AB4FF"),
}

# Cada forma: lista de (pathData, grosor, alfa 0-1). Zona segura: círculo de 66 dp centrado en (54, 54).
FORMAS = {
    "check": [
        ("M67,37 A21,21 0 1,0 67,71", 7, 1.0),
        ("M45,54 L52,61 L65,47", 6, 1.0),
    ],
    "moneda": [
        # Moneda trasera (arco visible por detrás), atenuada.
        ("M62,36 A17,17 0 0,1 76,62", 5, 0.6),
        # Moneda delantera.
        ("M69,54 A17,17 0 1,1 35,54 A17,17 0 1,1 69,54", 6, 1.0),
        # «$» sugerido: barra vertical y dos trazos.
        ("M52,43 L52,65 M46,48 L58,48 M46,60 L58,60", 5, 1.0),
    ],
    "libreta": [
        ("M36,32 H72 A4,4 0 0,1 76,36 V72 A4,4 0 0,1 72,76 H36 Z", 6, 1.0),
        ("M42,32 V76", 4, 1.0),
        ("M50,46 H68 M50,54 H68 M50,62 H60", 4, 1.0),
    ],
}

CABECERA = "<!-- Generado por scripts/generar-iconos-app.py; no editar a mano. -->\n"


def argb(hex_rgb: str, alfa: float) -> str:
    a = round(alfa * 255)
    return f"#{a:02X}{hex_rgb[1:]}"


def vector(trazos, color_rgb: str) -> str:
    paths = "".join(
        f"""    <path
        android:fillColor="#00000000"
        android:pathData="{d}"
        android:strokeColor="{argb(color_rgb, alfa)}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="{grosor}" />
"""
        for d, grosor, alfa in trazos
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        + CABECERA
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        + paths
        + "</vector>\n"
    )


def adaptable(forma: str, paleta: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        + CABECERA
        + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@color/lanzador_fondo_{paleta}" />\n'
        f'    <foreground android:drawable="@drawable/ic_launcher_{forma}_{paleta}" />\n'
        f'    <monochrome android:drawable="@drawable/ic_launcher_mono_{forma}" />\n'
        "</adaptive-icon>\n"
    )


def alias(forma: str, paleta: str, habilitado: bool) -> str:
    return f"""        <activity-alias
            android:name=".Lanzador_{forma}_{paleta}"
            android:targetActivity=".MainActivity"
            android:enabled="{'true' if habilitado else 'false'}"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_{forma}_{paleta}"
            android:roundIcon="@mipmap/ic_launcher_{forma}_{paleta}"
            android:label="${{etiquetaApp}}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>
"""


def main() -> None:
    escritos = 0
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    (RES / "values").mkdir(parents=True, exist_ok=True)
    for forma, trazos in FORMAS.items():
        (RES / "drawable" / f"ic_launcher_mono_{forma}.xml").write_text(vector(trazos, "#FFFFFF"), encoding="utf-8", newline="\n")
        escritos += 1
        for paleta, (_, primario) in PALETAS_OSCURAS.items():
            (RES / "drawable" / f"ic_launcher_{forma}_{paleta}.xml").write_text(vector(trazos, primario), encoding="utf-8", newline="\n")
            (RES / "mipmap-anydpi-v26" / f"ic_launcher_{forma}_{paleta}.xml").write_text(adaptable(forma, paleta), encoding="utf-8", newline="\n")
            escritos += 2
    colores = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        + CABECERA
        + "<resources>\n"
        + "".join(f'    <color name="lanzador_fondo_{p}">{argb(fondo, 1.0)}</color>\n' for p, (fondo, _) in PALETAS_OSCURAS.items())
        + "</resources>\n"
    )
    (RES / "values" / "colores_lanzador.xml").write_text(colores, encoding="utf-8", newline="\n")
    escritos += 1
    print(f"{escritos} archivos escritos en {RES}")
    print("\nBloque para AndroidManifest.xml (solo check × noche_lima habilitado):\n")
    for forma in FORMAS:
        for paleta in PALETAS_OSCURAS:
            print(alias(forma, paleta, forma == "check" and paleta == "noche_lima"), end="")


if __name__ == "__main__":
    main()
