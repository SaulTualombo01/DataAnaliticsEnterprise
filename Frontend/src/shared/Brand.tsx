type BrandProps = {
  variant?: 'color' | 'inverse';
  className?: string;
};

/** Identidad compartida de acceso, activación y navegación (real y demo). */
export function Brand({ variant = 'color', className = '' }: BrandProps) {
  const source = import.meta.env.BASE_URL + 'brand/ciclo-' + variant + '.svg';
  return (
    <div className={'brand brand-' + variant + (className ? ' ' + className : '')} role="img" aria-label="DataAnalitics Enterprise">
      <img className="brand-symbol" src={source} width="44" height="44" alt="" aria-hidden="true" />
      <div className="brand-wordmark" aria-hidden="true">
        <strong>DataAnalitics</strong>
        <span className="brand-enterprise">Enterprise</span>
      </div>
    </div>
  );
}
