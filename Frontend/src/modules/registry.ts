// Each module registers its label and view here; business state stays inside its module.
export const modules = [
  { id: 'catalogo', code: 'M1', label: 'Catálogo', title: 'Catálogo de datos', description: 'Estructura, procedencia y diccionario de tus activos.' },
  { id: 'calidad', code: 'M2', label: 'Calidad', title: 'Calidad de datos', description: 'Reglas y resultados de evaluación.' },
  { id: 'gobierno', code: 'M3', label: 'Gobierno', title: 'Gobierno de datos', description: 'Políticas e historial de gobierno.' },
  { id: 'analitica', code: 'M4', label: 'Analítica', title: 'Analítica empresarial', description: 'Indicadores y reportes autorizados.' },
] as const;
export type ModuleId = typeof modules[number]['id'];
