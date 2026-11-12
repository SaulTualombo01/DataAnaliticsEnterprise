# Componentes del frontend

Los componentes de Frontend/ui.tsx adaptan los patrones de Button, Card y controles de shadcn/ui al módulo. La integración es manual: se conservan las carpetas y se usan controles HTML nativos. No se instaló un generador ni una dependencia de ejecución llamada shadcn/ui. Tailwind CSS aporta las utilidades de presentación; React y TypeScript conservan sus responsabilidades.

Fuentes: https://ui.shadcn.com/docs/installation/manual, https://ui.shadcn.com/r/styles/new-york-v4/button.json y https://ui.shadcn.com/r/styles/new-york-v4/card.json.

Aviso original de https://github.com/shadcn-ui/ui/blob/main/LICENSE.md:

MIT License

Copyright (c) 2023 shadcn

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

Las licencias de Tailwind CSS, class-variance-authority, clsx y tailwind-merge se distribuyen con sus paquetes. package-lock.json fija las versiones utilizadas. Los iconos SVG existentes se reutilizaron y ampliaron, con una paleta azul petróleo y verde de acción; no se copiaron marcas, capturas ni activos gráficos de las referencias de diseño.

## Animaciones y referencias del rediseño

Motion 14.11.1 se incorpora como dependencia React; su licencia MIT se distribuye con el paquete. Se usa su API pública, sin copiar ejemplos de pago. Fuentes: https://motion.dev/docs/react y https://github.com/motiondivision/motion.

Kokonut UI se consultó como referencia de tarjetas métricas y feedback de carga: https://kokonutui.com/docs/cards/metric-card y https://kokonutui.com/docs/inputs/loader. No se copió su código ni sus datos de ingresos. Reveal, MetricGauge, BrandMark, el favicon y los patrones de fondo se implementaron en los archivos del proyecto. No se descargaron imágenes, fuentes o recursos de pago, ni se incorporaron SDK de seguimiento.
