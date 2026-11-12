package org.dataanaliticsent.Calidad.Dominio;

import java.nio.*;
import java.nio.charset.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Lectura acotada de CSV UTF-8. Conserva celdas vacías, comillas y líneas físicas originales.
 */
public final class CsvParser {
    public enum ColumnType {TEXT, NUMBER}

    public record Parsed(List<String> headers, List<List<String>> records, List<Long> lineNumbers, char delimiter) {
        public QualityEngine.Input input(Map<String, ColumnType> types) {
            if (!headers.containsAll(types.keySet()))
                throw new IllegalArgumentException("Los tipos contienen columnas inexistentes.");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (var record : records) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String raw = record.get(i);
                    Object value = raw;
                    // Solo convertir cuando el usuario declara NUMBER. Los errores permanecen para medir validez.
                    if (types.getOrDefault(headers.get(i), ColumnType.TEXT) == ColumnType.NUMBER && raw.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,3})?")) {
                        try {
                            value = new BigDecimal(raw);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    row.put(headers.get(i), value);
                }
                rows.add(row);
            }
            return new QualityEngine.Input(rows, new LinkedHashSet<>(headers), true, Map.of(), lineNumbers);
        }
    }

    public Parsed parse(byte[] bytes, String separator, int maxRows, long maxBytes) {
        if (bytes.length > maxBytes) throw new LimitException("El CSV supera el límite de bytes permitido.");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("Guarda el archivo como CSV UTF-8 antes de subirlo.");
        }
        if (text.startsWith("\ufeff")) text = text.substring(1);
        if (text.isEmpty()) throw new IllegalArgumentException("El CSV debe contener una fila de encabezados.");
        if (text.indexOf('\0') >= 0)
            throw new IllegalArgumentException("El archivo contiene caracteres nulos y no es un CSV válido.");
        char delimiter = switch (Objects.toString(separator, "AUTO")) {
            case "COMMA" -> ',';
            case "SEMICOLON" -> ';';
            case "TAB" -> '\t';
            case "AUTO" -> detect(text);
            default -> throw new IllegalArgumentException("Selecciona un separador permitido.");
        };
        List<List<String>> all = new ArrayList<>();
        List<Long> lines = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, closed = false, touched = false;
        long line = 1, start = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String originalNewline = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? "\r\n" : String.valueOf(c);
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                c = '\n';
                i++;
            } else if (c == '\r') c = '\n';
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closed = true;
                    }
                } else {
                    cell.append(c == '\n' ? originalNewline : String.valueOf(c));
                    if (c == '\n') line++;
                }
            } else if (c == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
                closed = false;
                touched = true;
            } else if (c == '\n') {
                row.add(cell.toString());
                append(all, lines, row, start, maxRows);
                row = new ArrayList<>();
                cell.setLength(0);
                closed = false;
                touched = false;
                line++;
                start = line;
            } else if (c == '"') {
                if (cell.length() != 0 || closed) throw format(line);
                quoted = true;
                touched = true;
            } else {
                if (closed) throw format(line);
                cell.append(c);
                touched = true;
            }
            if (cell.length() > 16384) throw new LimitException("Una celda supera los 16.384 caracteres admitidos.");
            if (row.size() > 128) throw new LimitException("El CSV admite como máximo 128 columnas.");
        }
        if (quoted)
            throw new IllegalArgumentException("Hay una celda entre comillas sin cerrar en la fila " + start + ".");
        if (touched || closed || cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            append(all, lines, row, start, maxRows);
        }
        List<String> headers = all.removeFirst();
        lines.removeFirst();
        if (headers.size() > 128 || headers.stream().anyMatch(h -> h.isBlank() || h.length() > 128 || h.codePoints().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Los encabezados deben ser nombres no vacíos de hasta 128 caracteres, sin saltos de línea.");
        if (new HashSet<>(headers).size() != headers.size())
            throw new IllegalArgumentException("Hay nombres de columna repetidos en el encabezado.");
        for (int i = 0; i < all.size(); i++)
            if (all.get(i).size() != headers.size())
                throw new IllegalArgumentException("La fila " + lines.get(i) + " no tiene el mismo número de columnas que el encabezado.");
        return new Parsed(List.copyOf(headers), List.copyOf(all), List.copyOf(lines), delimiter);
    }

    private void append(List<List<String>> rows, List<Long> lines, List<String> row, long line, int maxRows) {
        if (rows.size() > maxRows)
            throw new LimitException("El CSV supera el máximo de " + maxRows + " registros permitido.");
        rows.add(List.copyOf(row));
        lines.add(line);
    }

    private char detect(String text) {
        boolean quote = false;
        int comma = 0, semicolon = 0, tab = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quote && i + 1 < text.length() && text.charAt(i + 1) == '"') i++;
                else quote = !quote;
            } else if (!quote) {
                if (c == '\r' || c == '\n') break;
                if (c == ',') comma++;
                if (c == ';') semicolon++;
                if (c == '\t') tab++;
            }
        }
        int max = Math.max(comma, Math.max(semicolon, tab));
        if (max == 0) return ',';
        if ((comma == max ? 1 : 0) + (semicolon == max ? 1 : 0) + (tab == max ? 1 : 0) > 1)
            throw new IllegalArgumentException("No se puede detectar el separador. Selecciona coma, punto y coma o tabulación.");
        return comma == max ? ',' : semicolon == max ? ';' : '\t';
    }

    private IllegalArgumentException format(long line) {
        return new IllegalArgumentException("El uso de comillas no es válido en la fila " + line + ".");
    }

    public static final class LimitException extends IllegalArgumentException {
        public LimitException(String message) {
            super(message);
        }
    }
}
