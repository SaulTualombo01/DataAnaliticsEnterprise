package org.dataanaliticsent.catalogo.internal.dominio;

import java.util.Objects;

final class Validacion {
    private Validacion() { }

    static String texto(String valor, String nombre, int maximo) {
        Objects.requireNonNull(valor, nombre);
        if (valor.isBlank() || valor.length() > maximo) {
            throw new IllegalArgumentException(nombre + " inválido");
        }
        return valor;
    }

    static void descripcion(String valor) {
        if (valor != null && valor.length() > 2000) {
            throw new IllegalArgumentException("Descripción demasiado larga");
        }
    }
}
