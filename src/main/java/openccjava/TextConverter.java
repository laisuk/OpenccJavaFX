package openccjava;

/**
 * Represents a caller-supplied transformation for text-bearing content inside
 * an Office or EPUB package.
 *
 * <p>{@link OfficeHelper} owns package parsing, ZIP reconstruction, entry
 * selection, XLSX inline-string handling, EPUB packaging rules, and optional
 * font protection. A {@code TextConverter} owns only the transformation
 * applied to selected text.</p>
 *
 * <p>This separation allows callers to compose OpenCC conversion with other
 * processing steps, such as compatibility normalization or DeToFu, without
 * coupling those policies to the Office/EPUB package layer.</p>
 *
 * <p>The interface is deliberately compatible with Java 8 lambdas and method
 * references.</p>
 *
 * <p>Input is UTF-8-decoded package content, not necessarily plain text.
 * Most selected parts are passed as whole XML/XHTML strings, including markup
 * and entity references. XLSX worksheet inline strings are passed as the raw
 * contents of individual {@code <t>} elements. Implementations must preserve
 * markup and escaping; OfficeHelper does not XML-escape the returned string.</p>
 *
 * <p>OfficeHelper invokes the callback synchronously. A null return or a runtime
 * exception causes conversion to return a failed result. Callers sharing a
 * callback across concurrent conversions must provide any synchronization it needs.</p>
 *
 * <pre>{@code
 * OpenCC converter = new OpenCC(OpenccConfig.S2T);
 * TextConverter textConverter = text -> text.isEmpty() ? text : converter.convert(text);
 * }</pre>
 *
 * @since 1.5.0
 */
@FunctionalInterface
public interface TextConverter {

    /**
     * Transforms one text fragment selected by the Office/EPUB package layer.
     *
     * @param text package content including any markup or entity references;
     *             never {@code null}, but may be empty
     * @return transformed text; must not be {@code null}
     */
    String convert(String text);
}
