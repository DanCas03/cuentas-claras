# Diseño «Isla»: qué dijeron las skills y qué se aplicó

**Fecha:** 2026-10-17 · Skills invocadas: `high-end-visual-design` (soft-skill) y `ui-ux-pro-max` (`--design-system` para «personal finance mobile app premium dark light cards bottom navigation» y `--stack jetpack-compose`). Es el tercer diseño del spec 2026-10-17 §6.2 (id `isla`), para que Daniel compare con «Tinta», hecho sin skills.

## Lo que recomendaron

1. **Barra flotante («Fluid Island»)**: la navegación despegada del borde como una píldora con margen, no una franja pegada. (soft-skill §5A)
2. **Doble bisel / radios concéntricos**: contenedores anidados con radio interior = exterior − relleno; filos de 1 dp translúcidos (`white/10`), nunca bordes grises sólidos. (soft-skill §4A) Libreta ya lo hace; Isla usa solo el filo translúcido sin carcasa.
3. **Radios exagerados** (`rounded-[2rem]`) y **más aire** entre bloques. (soft-skill §3-4)
4. **Presión física**: escala al pulsar (0,95-0,98) con resorte; nada instantáneo; salida más corta que entrada. (soft-skill §5B, ui-ux-pro-max `scale-feedback`, `spring-physics`, `exit-faster-than-enter`)
5. **Barra inferior ≤ 5 ítems con ícono + etiqueta**; el estado activo con indicador, no solo color. (ui-ux-pro-max `bottom-nav-limit`, `nav-state-active`)
6. **Tokens semánticos y objetivos ≥ 48 dp**, contraste 4,5:1 en ambos modos. (ui-ux-pro-max §1-2)

## Lo que se aplicó en Isla

- Tarjeta `CONTORNO`: `superficieElevada` plana, radio 24 dp, contorno de 1 dp con degradado `texto` al 10 % → transparente, relleno 20 dp (16 + 4). Sin carcasa.
- Barra `FLOTANTE`: píldora en `superficieElevada` con margen horizontal 16 dp y 8 dp abajo, sin divisor; el «+» (`GOTA`, disco de 56 dp) va dentro de la píldora, no sobresale.
- Presión 0,96 con el resorte contenido; formas de control 14 dp; hoja modal 28 dp.

## Lo que se descartó y por qué

- Fuentes «premium» propuestas (Geist, Clash Display) y la prohibición de Inter: la fuente es independiente del diseño (spec §2.1) y el catálogo es solo OFL.
- Paleta azul/verde de ui-ux-pro-max y el patrón «App Store Landing»: la app usa el catálogo cerrado de paletas y no es una landing.
- Sombras difusas y `backdrop-blur`: DESIGN.md §6 prohíbe sombras; la profundidad sigue siendo tonal.
- Glow de texto (`text-shadow`): DESIGN.md §8 prohíbe brillos.
