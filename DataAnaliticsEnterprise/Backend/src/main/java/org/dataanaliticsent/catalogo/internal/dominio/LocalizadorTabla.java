package org.dataanaliticsent.catalogo.internal.dominio;

public record LocalizadorTabla(String namespace, String name) {
    public LocalizadorTabla {
        Validacion.texto(namespace, "Esquema", 500);
        Validacion.texto(name, "Tabla", 200);
    }
}
